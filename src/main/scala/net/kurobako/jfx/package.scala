package net.kurobako

import cats.effect.concurrent.{Deferred, Ref}
import cats.effect.{Concurrent, ContextShift, IO}
import cats.implicits._
import fs2.concurrent.Queue
import fs2.{Pipe, Stream}
import javafx.beans.InvalidationListener
import javafx.beans.property.ObjectProperty
import javafx.beans.value._
import javafx.collections.{ListChangeListener, ObservableList, ObservableSet, SetChangeListener}
import javafx.event.{EventHandler, EventType, Event => FXEvent}
import javafx.scene.control.{Cell, ListCell, ListView}
import javafx.scene.{Node, Parent}
import javafx.util.Callback
import net.kurobako.jfx.FXApp.FXContextShift
import net.kurobako.jfx.syntax._

import scala.collection.Factory

package object jfx {


	@inline private def liftGen[A, C1, C2, S](c1: C1, consInit: Boolean)
	                                         (bind: (C1 => Unit) => S, unbind: (C1, S) => Unit)
	                                         (f: C1 => C2)
	                                         (implicit cs: ContextShift[IO]): Stream[IO, C2] = for {
		c2s <- Stream.eval(Queue.bounded[IO, C2](1))
		_ <- Stream.eval[IO, Unit] {if (consInit) c2s.enqueue1(f(c1)) else IO.unit}
		_ <- Stream.bracket(IO {
			bind(c1 => unsafeRunAsync(c2s.enqueue1(f(c1))))
		}) { s => IO {unbind(c1, s)} }
		a <- c2s.dequeue
	} yield a


	def liftSetRaw[A, C](prop: ObservableSet[A], consInit: Boolean)(f: ObservableSet[A] => C)
	                    (implicit cs: ContextShift[IO]): Stream[IO, C] =
		liftGen[A, ObservableSet[A], C, SetChangeListener[A]](prop, consInit)(f => {
			val listener: SetChangeListener[A] = _ => f(prop)
			prop.addListener(listener)
			listener
		}, _.removeListener(_))(f)


	def liftSet[A, C](prop: ObservableSet[A], consInit: Boolean)(factory: Factory[A, C])
	                 (implicit cs: ContextShift[IO]): Stream[IO, C] =
		liftSetRaw[A, C](prop, consInit) { xs =>
			import scala.jdk.CollectionConverters._
			xs.asScala.to(factory)
		}

	def liftListRaw[A, C](prop: ObservableList[A], consInit: Boolean)(f: ObservableList[A] => C)
	                     (implicit cs: ContextShift[IO]): Stream[IO, C] =
		liftGen[A, ObservableList[A], C, ListChangeListener[A]](prop, consInit)(f => {
			val listener: ListChangeListener[A] = _ => f(prop)
			prop.addListener(listener)
			listener
		}, _.removeListener(_))(f)

	def liftList[A, C](prop: ObservableList[A], consInit: Boolean)(factory: Factory[A, C])
	                  (implicit cs: ContextShift[IO]): Stream[IO, C] =
		liftListRaw[A, C](prop, consInit) { xs =>
			import scala.jdk.CollectionConverters._
			xs.asScala.to(factory)
		}

	private def bindObservableValue[C1 <: ObservableValue[_]](prop: C1): (C1 => Unit) => InvalidationListener = f => {
		val listener: InvalidationListener = _ => f(prop)
		prop.addListener(listener)
		listener
	}

	private def unbindObservableValue[C1 <: ObservableValue[_]]: (C1, InvalidationListener) => Unit = _.removeListener(_)

	@inline private def lift0[B, A](prop: ObservableValue[B], consInit: Boolean)
	                               (implicit cs: ContextShift[IO], ev: B => A): Stream[IO, Option[A]] =
		liftGen[B, ObservableValue[B], Option[A], InvalidationListener](prop, consInit)(bindObservableValue(prop), unbindObservableValue)(v => Option(v.getValue).map(ev))

	@inline private def lift1[O <: ObservableValue[P], P, B, A](prop: O, getter: O => B, consInit: Boolean)
	                                                           (implicit cs: ContextShift[IO], ev: B => A): Stream[IO, Option[A]] =
		liftGen[B, O, Option[A], InvalidationListener](prop, consInit)(bindObservableValue(prop), unbindObservableValue)(v => Option(getter(v)).map(ev))

	def lift[A <: Any](prop: ObservableValue[A], consInit: Boolean)
	                  (implicit cs: ContextShift[IO]): Stream[IO, Option[A]] = lift0[A, A](prop, consInit)

	def lift(prop: ObservableBooleanValue, consInit: Boolean)
	        (implicit cs: ContextShift[IO]): Stream[IO, Option[Boolean]] = lift1[ObservableBooleanValue, java.lang.Boolean, java.lang.Boolean, Boolean](prop, _.get, consInit)

	def lift(prop: ObservableDoubleValue, consInit: Boolean)
	        (implicit cs: ContextShift[IO]): Stream[IO, Option[Double]] = lift1[ObservableDoubleValue, Number, java.lang.Double, Double](prop, _.get, consInit)

	def lift(prop: ObservableFloatValue, consInit: Boolean)
	        (implicit cs: ContextShift[IO]): Stream[IO, Option[Float]] = lift1[ObservableFloatValue, Number, java.lang.Float, Float](prop, _.get, consInit)

	def lift(prop: ObservableIntegerValue, consInit: Boolean)
	        (implicit cs: ContextShift[IO]): Stream[IO, Option[Int]] = lift1[ObservableIntegerValue, Number, java.lang.Integer, Int](prop, _.get, consInit)

	def lift(prop: ObservableLongValue, consInit: Boolean)
	        (implicit cs: ContextShift[IO]): Stream[IO, Option[Long]] = lift1[ObservableLongValue, Number, java.lang.Long, Long](prop, _.get, consInit)


	def handleEvent[A <: FXEvent, B](prop: ObjectProperty[_ >: EventHandler[A]])(f: A => Option[B])
	                                (implicit cs: ContextShift[IO]): Stream[IO, B] = {
		for {
			q <- Stream.eval(Queue.bounded[IO, B](maxSize = 1))
			_ <- Stream.bracket(IO {
				prop.set(new EventHandler[A] {
					override def handle(event: A): Unit = f(event).foreach(x => unsafeRunAsync(q.enqueue1(x)))
				})
			}) { _ => IO {prop.set(null)} }
			a <- q.dequeue
		} yield a
	}

	def handleEvent[A <: FXEvent, B](prop: Node)
	                                (eventType: EventType[A], filter: Boolean = false)(f: A => Option[B])
	                                (implicit cs: ContextShift[IO]): Stream[IO, B] = {
		for {
			q <- Stream.eval(Queue.bounded[IO, B](maxSize = 1))
			_ <- Stream.bracket(IO {
				val handler: EventHandler[A] = { e => f(e).foreach(x => unsafeRunAsync(q.enqueue1(x))) }
				if (filter) prop.addEventFilter[A](eventType, handler)
				else prop.addEventHandler[A](eventType, handler)
				handler
			}) { x =>
				IO {
					if (filter) prop.removeEventFilter(eventType, x)
					else prop.removeEventHandler(eventType, x)
				}
			}
			a <- q.dequeue
		} yield a
	}

	def nodeCellFactory[A, N](prop: ObjectProperty[Callback[ListView[A], ListCell[A]]])
	                         (mkNode: () => N)(toNode: N => Node)
	                         (unsafeUpdate: (Option[A], N) => IO[Unit])
	                         (implicit
	                          fxcs: FXContextShift,
	                          cs: ContextShift[IO]): Stream[IO, (Option[A], N)] = for {
		q <- Stream.eval(Queue.unbounded[IO, (Option[A], N)])
		_ <- Stream.bracket(FXIO {
			val prev = prop.get
			prop.set { _ =>
				new ListCell[A] {
					val node = mkNode()
					setGraphic(toNode(node))
					setText(null)
					override def updateItem(item: A, empty: Boolean): Unit = {
						super.updateItem(item, empty)
						val a = if (empty || item == null) None else Some(item)
						unsafeRunAsync(unsafeUpdate(a, node) *> q.enqueue1(a -> node))
					}
				}
			}
			prev
		}) { prev => IO {prop.set(prev)} }
		a <- q.dequeue
	} yield a


	def cellFactory[P, C[x] <: Cell[x], A](prop: ObjectProperty[Callback[P, C[A]]])
	                                      (mkCell: (P, (Option[A], C[A]) => Unit) => C[A],
	                                       tickUnsafe: (Option[A], C[A]) => IO[Unit] = { (_: Option[A], _: C[A]) => IO.unit })
	                                      (implicit fxcs: FXContextShift, cs: ContextShift[IO]): Stream[IO, (Option[A], C[A])] = {
		for {
			q <- Stream.eval(Queue.unbounded[IO, (Option[A], C[A])])
			_ <- Stream.bracket(FXIO {
				val prev = prop.get
				prop.set { a =>
					mkCell(a, { (a, c) => unsafeRunAsync(tickUnsafe(a, c) *> q.enqueue1(a -> c)) })
				}
				prev
			}) { prev => IO {prop.set(prev)} }
			a <- q.dequeue
		} yield a
	}

	def simpleListCellFactory[A](prop: ObjectProperty[Callback[ListView[A], ListCell[A]]])
	                            (tickUnsafe: (Option[A], ListCell[A]) => IO[Unit] = { (_: Option[A], _: ListCell[A]) => IO.unit })
	                            (implicit fxcs: FXContextShift, cs: ContextShift[IO]): Stream[IO, (Option[A], ListCell[A])] = {
		cellFactory(prop)((_, cb) => {
			new ListCell[A] {
				override def updateItem(item: A, empty: Boolean): Unit = {
					super.updateItem(item, empty)
					cb(if (empty || item == null) None else Some(item), this)
				}
			}
		}, tickUnsafe)
	}

	def deferUntilLayout[A](parent: Parent)(f: => IO[A])(implicit fxcs: FXContextShift, cs: ContextShift[IO]): IO[A] = FXIO {
		if (parent.isNeedsLayout && parent.getParent != null) {
			deferUntilLayout(parent)(f)
		} else IO.shift(cs) *> f
	}.flatten

	def switchMapKeyed[F[_], F2[x] >: F[x], O, O2, K](kf: O => K, f: O => Stream[F2, O2], maxOpen: Int = Int.MaxValue)
	                                                 (implicit F2: Concurrent[F2]): Pipe[F2, O, O2] = self =>
		Stream.force(Ref.of[F2, Map[K, Deferred[F2, Unit]]](Map()).map { haltRef =>
			self.evalMap { o =>
				Deferred[F2, Unit].flatMap { halt =>
					haltRef.modify { m =>
						val k       = kf(o)
						val updated = m + (k -> halt)
						m.get(k) match {
							case None       => updated -> F2.unit
							case Some(last) => updated -> last.complete(())
						}
					}.flatten.as(f(o).interruptWhen(halt.get.attempt))
				}
			}.parJoin(maxOpen)
		})

}