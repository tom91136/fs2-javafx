package net.kurobako.jfx

import cats.effect.concurrent.{Deferred, Ref}
import cats.effect.{Concurrent, ContextShift, IO}
import cats.implicits._
import fs2.concurrent.Queue
import fs2.{Pipe, Stream}
import javafx.beans.property.ObjectProperty
import javafx.beans.value.{ChangeListener, ObservableValue}
import javafx.collections.{ListChangeListener, ObservableList}
import javafx.event.{Event => FXEvent, EventHandler, EventType}
import javafx.scene.Node
import javafx.scene.control.Cell
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


	private def lift0[B, A](prop: ObservableValue[B], consInit: Boolean, maxEvent: Int)
						   (implicit fxcs: FXContextShift, cs: ContextShift[IO], ev: B => A): Stream[IO, Option[A]] = {
		for {
			q <- Stream.eval(Queue.bounded[IO, Option[A]](maxEvent))
			_ <- Stream.eval[IO, Unit] {if (consInit) q.enqueue1(Option(ev(prop.getValue))) else IO.unit}
			_ <- Stream.bracket(IO {
				val listener: ChangeListener[B] = (_, _, n) => unsafeRunAsync(q.enqueue1(Option(ev(n))))
				prop.addListener(listener)
				listener
			}) { x => IO {prop.removeListener(x)} }
			a <- q.dequeue
		} yield a
	}

	def liftBool(prop: ObservableValue[java.lang.Boolean], consInit: Boolean = true, maxEvent: Int = 1)
				(implicit fxcs: FXContextShift, cs: ContextShift[IO]): Stream[IO, Option[Boolean]] = lift0[java.lang.Boolean, Boolean](prop, consInit, maxEvent)

	def liftInt(prop: ObservableValue[java.lang.Integer], consInit: Boolean = true, maxEvent: Int = 1)
			   (implicit fxcs: FXContextShift, cs: ContextShift[IO]): Stream[IO, Option[Int]] = lift0[java.lang.Integer, Int](prop, consInit, maxEvent)

	def liftLong(prop: ObservableValue[java.lang.Long], consInit: Boolean = true, maxEvent: Int = 1)
				(implicit fxcs: FXContextShift, cs: ContextShift[IO]): Stream[IO, Option[Long]] = lift0[java.lang.Long, Long](prop, consInit, maxEvent)

	def liftDouble(prop: ObservableValue[java.lang.Double], consInit: Boolean = true, maxEvent: Int = 1)
				  (implicit fxcs: FXContextShift, cs: ContextShift[IO]): Stream[IO, Option[Double]] = lift0[java.lang.Double, Double](prop, consInit, maxEvent)

	def liftFloat(prop: ObservableValue[java.lang.Float], consInit: Boolean = true, maxEvent: Int = 1)
				 (implicit fxcs: FXContextShift, cs: ContextShift[IO]): Stream[IO, Option[Float]] = lift0[java.lang.Float, Float](prop, consInit, maxEvent)

	def lift[A](prop: ObservableValue[A], consInit: Boolean = true, maxEvent: Int = 1)
			   (implicit fxcs: FXContextShift, cs: ContextShift[IO]): Stream[IO, Option[A]] = lift0[A, A](prop, consInit, maxEvent)


	def eventProp[A <: FXEvent](prop: ObjectProperty[_ >: EventHandler[A]], maxEvent: Int = 1)
							   (implicit fxcs: FXContextShift, cs: ContextShift[IO]): Stream[IO, A] = {
		for {
			q <- Stream.eval(Queue.bounded[IO, A](maxEvent))
			_ <- Stream.bracket(IO {
				prop.set(new EventHandler[A] {
					override def handle(event: A): Unit = unsafeRunAsync(q.enqueue1(event))
				})
			}) { _ => IO {prop.set(null)} }
			a <- q.dequeue
		} yield a
	}

	def event[A <: FXEvent](prop: Node)
						   (eventType: EventType[A], filter: Boolean = false, maxEvent: Int = 1)
						   (implicit fxcs: FXContextShift, cs: ContextShift[IO]): Stream[IO, A] = {
		for {
			q <- Stream.eval(Queue.bounded[IO, A](maxEvent))
			_ <- Stream.bracket(IO {
				val f: EventHandler[A] = { e => unsafeRunAsync(q.enqueue1(e)) }
				if (filter) prop.addEventFilter[A](eventType, f)
				else prop.addEventHandler[A](eventType, f)
				f
			}) { x =>
				IO {
					if (filter) prop.removeEventFilter(eventType, x)
					else prop.removeEventHandler(eventType, x)
				}
			}
			a <- q.dequeue
		} yield a
	}


	def cellFactory[N, C[x] <: Cell[x], A](prop: ObjectProperty[Callback[N, C[A]]])
										  (mkCell: N => C[A],
										   tickUnsafe: (Option[A], C[A]) => IO[Unit] = { (_: Option[A], _: C[A]) => IO.unit })
										  (implicit fxcs: FXContextShift, cs: ContextShift[IO]): Stream[IO, (Option[A], C[A])] = {
		//			implicit val cs: ContextShift[IO] = fxcs.underlying
		for {
			q <- Stream.eval(Queue.unbounded[IO, (Option[A], C[A])])
			_ <- Stream.bracket(FXIO {
				val prev = prop.get
				prop.set { a =>
					val cell                        = mkCell(a)
					val listener: ChangeListener[A] = { (_, _, n) =>
						val option = Option(n)
						unsafeRunAsync(tickUnsafe(option, cell) *> q.enqueue1(option -> cell))
					}
					cell.itemProperty().addListener(listener)
					cell
				}
				prev
			}) { x => IO {prop.set(x)} }
			a <- q.dequeue
		} yield a
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