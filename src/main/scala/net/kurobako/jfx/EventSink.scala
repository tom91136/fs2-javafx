package net.kurobako.jfx

import cats.effect.{IO}
import cats.implicits._
import fs2.Stream
import fs2.concurrent.SignallingRef
import javafx.beans.property.ObjectProperty
import javafx.beans.value.{ChangeListener, ObservableValue}
import javafx.event.{Event, EventHandler}
import net.kurobako.jfx.syntax._

class EventSink[A] private(private val as: SignallingRef[IO, A],
                           private val refs: SignallingRef[IO, List[IO[Unit]]]) {

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
	def apply[A](a: A): IO[EventSink[A]] = for {
		as <- SignallingRef[IO, A](a)
		refs <- SignallingRef[IO, List[IO[Unit]]](Nil)
	} yield new EventSink(as, refs)
}