package net.kurobako.jfx

import cats.effect.concurrent.{Deferred, Ref}
import cats.effect.{Concurrent, ContextShift, IO}
import cats.implicits._
import fs2.concurrent.Queue
import fs2.{Pipe, Stream}
import javafx.beans.property.ObjectProperty
import javafx.beans.value.{ChangeListener, ObservableBooleanValue, ObservableDoubleValue, ObservableFloatValue, ObservableIntegerValue, ObservableLongValue, ObservableValue}
import javafx.collections.{ListChangeListener, ObservableList}
import javafx.event.{EventHandler, EventType, Event => FXEvent}
import javafx.scene.Node
import javafx.scene.control.{Cell, ListCell, ListView}
import javafx.util.Callback
import net.kurobako.jfx.FXApp.FXContextShift

object Event {


	def indexed[A](prop: ObservableList[A],
				   consInit: Boolean = true,
				   maxEvent: Int = 1)
				  (implicit fxcs: FXContextShift, cs: ContextShift[IO]): Stream[IO, Vector[A]] = {
		import scala.jdk.CollectionConverters._
		for {
			q <- Stream.eval(Queue.bounded[IO, Vector[A]](maxEvent))
			_ <- Stream.eval[IO, Unit] {if (consInit) q.enqueue1(prop.asScala.toVector) else IO.unit}
			_ <- Stream.bracket(IO {
				val listener: ListChangeListener[A] = { _ => unsafeRunAsync(q.enqueue1(prop.asScala.toVector)) }
				prop.addListener(listener)
				listener
			}) { x => IO {prop.removeListener(x)} }
			a <- q.dequeue
		} yield a
	}


	@inline private def lift0[B, A](prop: ObservableValue[B], consInit: Boolean)
								   (implicit fxcs: FXContextShift, cs: ContextShift[IO], ev: B => A): Stream[IO, Option[A]] = {
		for {
			q <- Stream.eval(Queue.bounded[IO, Option[A]](1))
			_ <- Stream.eval[IO, Unit] {if (consInit) q.enqueue1(Option(ev(prop.getValue))) else IO.unit}
			_ <- Stream.bracket(IO {
				val listener: ChangeListener[B] = (_, _, n) => unsafeRunAsync(q.enqueue1(Option(ev(n))))
				prop.addListener(listener)
				listener
			}) { x => IO {prop.removeListener(x)} }
			a <- q.dequeue
		} yield a
	}

	@inline private def lift1[O <: ObservableValue[P], P, B, A](prop: O, getter: O => B, consInit: Boolean)
															   (implicit fxcs: FXContextShift, cs: ContextShift[IO], ev: B => A): Stream[IO, Option[A]] = {
		for {
			q <- Stream.eval(Queue.bounded[IO, Option[A]](1))
			_ <- Stream.eval[IO, Unit] {if (consInit) q.enqueue1(Option(ev(getter(prop)))) else IO.unit}
			_ <- Stream.bracket(IO {
				val listener: ChangeListener[P] = (_, _, _) => unsafeRunAsync(q.enqueue1(Option(ev(getter(prop)))))
				prop.addListener(listener)
				listener
			}) { x => IO {prop.removeListener(x)} }
			a <- q.dequeue
		} yield a
	}

	def lift[A <: AnyRef](prop: ObservableValue[A], consInit: Boolean)
						 (implicit fxcs: FXContextShift, cs: ContextShift[IO]): Stream[IO, Option[A]] = lift0[A, A](prop, consInit)

	def lift(prop: ObservableBooleanValue, consInit: Boolean)
			(implicit fxcs: FXContextShift, cs: ContextShift[IO]): Stream[IO, Option[Boolean]] = lift1[ObservableBooleanValue, java.lang.Boolean, java.lang.Boolean, Boolean](prop, _.get, consInit)

	def lift(prop: ObservableDoubleValue, consInit: Boolean)
			(implicit fxcs: FXContextShift, cs: ContextShift[IO]): Stream[IO, Option[Double]] = lift1[ObservableDoubleValue, Number, java.lang.Double, Double](prop, _.get, consInit)

	def lift(prop: ObservableFloatValue, consInit: Boolean)
			(implicit fxcs: FXContextShift, cs: ContextShift[IO]): Stream[IO, Option[Float]] = lift1[ObservableFloatValue, Number, java.lang.Float, Float](prop, _.get, consInit)

	def lift(prop: ObservableIntegerValue, consInit: Boolean)
			(implicit fxcs: FXContextShift, cs: ContextShift[IO]): Stream[IO, Option[Int]] = lift1[ObservableIntegerValue, Number, java.lang.Integer, Int](prop, _.get, consInit)

	def lift(prop: ObservableLongValue, consInit: Boolean)
			(implicit fxcs: FXContextShift, cs: ContextShift[IO]): Stream[IO, Option[Long]] = lift1[ObservableLongValue, Number, java.lang.Long, Long](prop, _.get, consInit)


	def handleEvent[A <: FXEvent](prop: ObjectProperty[_ >: EventHandler[A]])(f: A => Unit = (e: A) => e.consume()): Stream[IO, Unit] =
		Stream.bracket(IO {
			prop.set(new EventHandler[A] {
				override def handle(event: A): Unit = f(event)
			})
		}) { _ => IO {prop.set(null)} } >> Stream.never[IO]


	def handleEventF[A <: FXEvent, B](prop: ObjectProperty[_ >: EventHandler[A]])(f: A => Option[B])
									 (implicit fxcs: FXContextShift, cs: ContextShift[IO]): Stream[IO, B] = {
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

	def handleEventF[A <: FXEvent, B](prop: Node)
									 (eventType: EventType[A], filter: Boolean = false)(f: A => Option[B])
									 (implicit fxcs: FXContextShift, cs: ContextShift[IO]): Stream[IO, B] = {
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