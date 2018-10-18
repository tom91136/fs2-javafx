package net.kurobako

import cats.arrow.FunctionK
import cats.effect.concurrent.{Deferred, Ref}
import cats.effect.{Concurrent, ContextShift, IO}
import cats.implicits._
import cats.~>
import fs2.concurrent.{Queue, SignallingRef}
import fs2.{Pipe, Stream}
import javafx.beans.property.ObjectProperty
import javafx.beans.value.{ChangeListener, ObservableValue}
import javafx.collections._
import javafx.event.{Event, EventHandler, EventType}
import javafx.scene.Node
import javafx.scene.control.Cell
import javafx.util.Callback
import net.kurobako.jfx.FXApp.FXContextShift

package object jfx {


	val IoToStream: FunctionK[IO, Stream[IO, ?]] = λ[IO ~> Stream[IO, ?]](Stream.eval(_))

	def FXIO[A](a: => A)(implicit fxcs: FXContextShift, cs: ContextShift[IO]): IO[A] =
		IO.shift(fxcs.underlying) *> IO(a) <* IO.shift(cs)


	def joinAndDrain(xs: Stream[IO, Any]*)(implicit ev: Concurrent[IO]): Stream[IO, Nothing] =
		Stream(xs: _*).parJoinUnbounded.drain

	private def unsafeRunAsync[A](f: IO[A])(implicit fxcs: FXContextShift): Unit =
		f.start(fxcs.underlying).flatMap(_.join).runAsync(_ => IO.unit).unsafeRunSync()


	object Event {


		def indexed[A](prop: ObservableList[A],
					   consInit: Boolean = true,
					   maxEvent: Int = 1)
					  (implicit fxcs: FXContextShift, cs: ContextShift[IO]): Stream[IO, IndexedSeq[A]] = {
			import scala.collection.JavaConverters._
			for {
				q <- Stream.eval(Queue.bounded[IO, IndexedSeq[A]](maxEvent))
				_ <- Stream.eval[IO, Unit] {if (consInit) q.enqueue1(prop.asScala.toVector) else IO.unit}
				_ <- Stream.bracket(IO {
					val listener: ListChangeListener[A] = { _ => unsafeRunAsync(q.enqueue1(prop.asScala.toIndexedSeq)) }
					prop.addListener(listener)
					listener
				}) { x => IO {prop.removeListener(x)} }
				a <- q.dequeue
			} yield a
		}

		def property[A](prop: ObservableValue[A],
						consInit: Boolean = true,
						maxEvent: Int = 1)
					   (implicit fxcs: FXContextShift, cs: ContextShift[IO]): Stream[IO, Option[A]] = {
			for {
				q <- Stream.eval(Queue.bounded[IO, Option[A]](maxEvent))
				_ <- Stream.eval[IO, Unit] {if (consInit) q.enqueue1(Option(prop.getValue)) else IO.unit}
				_ <- Stream.bracket(IO {
					val listener: ChangeListener[A] = (_, _, n) => unsafeRunAsync(q.enqueue1(Option(n)))
					prop.addListener(listener)
					listener
				}) { x => IO {prop.removeListener(x)} }
				a <- q.dequeue
			} yield a
		}


		def eventProp[A <: Event](prop: ObjectProperty[_ >: EventHandler[A]] , maxEvent: Int = 1)
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

		def event[A <: Event](prop: Node)
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
						val cell = mkCell(a)
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
							val k = kf(o)
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


	class EventSink[A] private(private val as: SignallingRef[IO, A],
							   private val refs: SignallingRef[IO, List[IO[Unit]]])
							  (implicit val fxcs: FXContextShift) {

		val discrete: Stream[IO, A] = as.discrete

		def unbindAll: IO[Unit] = refs.getAndSet(Nil).flatMap(_.sequence).void

		def bind[B](prop: ObservableValue[B])(f: (A, Option[B]) => A): IO[Unit] =
			IO {
				val listener: ChangeListener[B] = { (_, _, n) => unsafeRunAsync(as.update(f(_, Option(n)))) }
				prop.addListener(listener)
				listener
			}.flatMap(s => refs.update(IO(prop.removeListener(s)) :: _))

		def bind[B <: Event](prop: ObjectProperty[EventHandler[B]])(f: (A, B) => A): IO[Unit] =
			IO {
				val prev = prop.get
				prop.set({ e => unsafeRunAsync(as.update(f(_, e))) })
				prev
			}.flatMap { p => refs.update(IO(prop.set(p)) :: _) }

	}

	object EventSink {
		def apply[A](a: A)(implicit fxcs: FXContextShift, cs: ContextShift[IO]): IO[EventSink[A]] = for {
			as <- SignallingRef[IO, A](a)
			refs <- SignallingRef[IO, List[IO[Unit]]](Nil)
		} yield new EventSink(as, refs)
	}


}
