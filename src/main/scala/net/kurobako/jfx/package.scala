package net.kurobako

import cats.arrow.FunctionK
import cats.effect.{Concurrent, ContextShift, IO}
import cats.implicits._
import cats.~>
import fs2.Stream
import fs2.concurrent.{Queue, SignallingRef}
import javafx.beans.property.ObjectProperty
import javafx.beans.value.{ChangeListener, ObservableValue}
import javafx.collections.ListChangeListener
import javafx.event.{Event, EventHandler}
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


		def indexed[A](prop: javafx.collections.ObservableList[A],
					   consInit: Boolean = true,
					   maxEvent: Int = 1)
					  (implicit fxcs: FXContextShift): Stream[IO, IndexedSeq[A]] = {
			import fxcs._

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
					   (implicit fxcs: FXContextShift): Stream[IO, Option[A]] = {
			import fxcs._
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
		}.onFinalize(IO {println(s"Kill prop $prop")})


		def event[A <: Event](prop: ObjectProperty[EventHandler[A]], maxEvent: Int = 1)
							 (implicit fxcs: FXContextShift): Stream[IO, A] = {
			import fxcs._
			for {
				q <- Stream.eval(Queue.bounded[IO, A](maxEvent))
				_ <- Stream.bracket(IO {
					val prev = prop.get
					prop.set({ e => unsafeRunAsync(q.enqueue1(e)) })
					prev
				}) { x => IO {prop.set(x)} }
				a <- q.dequeue
			} yield a
		}.onFinalize(IO {println(s"Kill event $prop")})
	}


	class EventSink[A] private(private val as: SignallingRef[IO, A],
							   private val refs: SignallingRef[IO, List[IO[Unit]]])
							  (implicit val fxcs: FXContextShift) {

		import fxcs._

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
