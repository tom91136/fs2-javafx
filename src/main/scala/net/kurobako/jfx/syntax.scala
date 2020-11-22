package net.kurobako.jfx

import cats.arrow.FunctionK
import cats.data.Chain
import cats.effect.{Async, Concurrent, IO}
import cats.implicits._
import cats.~>
import fs2.Stream
import javafx.beans.binding.{Bindings, ObjectBinding}
import javafx.beans.property.ObjectProperty
import javafx.beans.value._
import javafx.collections.{ObservableList, ObservableSet}
import javafx.event.{EventHandler, EventType, Event => FXEvent}
import javafx.scene.{Node, Parent}

import scala.collection.Factory

object syntax {

	private[jfx] def unsafeRunAsync[A](f: IO[A]): Unit = {
		import cats.effect.unsafe.implicits.global
		f.unsafeRunAndForget()

		//		f.runAsync(_ => IO.unit).unsafeRunSync()
		//		f.start(fxcs.underlying).flatMap(_.join).runAsync(_ => IO.unit).unsafeRunSync()
	}

	val IoToStream: FunctionK[IO, Stream[IO, *]] = λ[IO ~> Stream[IO, *]](Stream.eval(_))

	@inline def FXIO[A](a: => A): IO[A] = {
		Async[IO].evalOn(IO(a), FXExecutionContext)
		//		IO.shift(fxcs.underlying) *> IO(a) <* IO.shift(cs)
	}

	@inline def joinAndDrain(xs: Stream[IO, Any]*)(implicit ev: Concurrent[IO]): Stream[IO, Nothing] =
		Stream(xs: _*).parJoinUnbounded.drain

	@inline def joinAndDrain(xs: Chain[Stream[IO, Any]])(implicit ev: Concurrent[IO]): Stream[IO, Nothing] =
		joinAndDrain(xs.toList: _*)

	implicit class ObservableInstances[A <: Any](private val x: ObservableValue[A]) extends AnyVal {
		def observe(consInit: Boolean): Stream[IO, Option[A]] = lift(x, consInit)
		def map[B](f: A => B): ObjectBinding[B] = Bindings.createObjectBinding(() => f(x.getValue), x)
	}

	implicit class ObservableBooleanInstances(private val x: ObservableBooleanValue) extends AnyVal {
		def observe(consInit: Boolean): Stream[IO, Option[Boolean]] = lift(x, consInit)
	}

	implicit class ObservableDoubleInstances(private val x: ObservableDoubleValue) extends AnyVal {
		def observe(consInit: Boolean): Stream[IO, Option[Double]] = lift(x, consInit)
	}

	implicit class ObservableFloatInstances(private val x: ObservableFloatValue) extends AnyVal {
		def observe(consInit: Boolean): Stream[IO, Option[Float]] = lift(x, consInit)
	}

	implicit class ObservableIntegerInstances(private val x: ObservableIntegerValue) extends AnyVal {
		def observe(consInit: Boolean): Stream[IO, Option[Int]] = lift(x, consInit)
	}

	implicit class ObservableLongInstances(private val x: ObservableLongValue) extends AnyVal {
		def observe(consInit: Boolean): Stream[IO, Option[Long]] = lift(x, consInit)
	}

	implicit class ParentInstances(private val x: Parent) extends AnyVal {
		def afterLayout[A](f: => IO[A]): IO[A] = deferUntilLayout[A](x)(f)
	}

	implicit class NodeInstances(private val x: Node) extends AnyVal {
		def event[A <: FXEvent, B](eventType: EventType[A], filter: Boolean = false)(f: A => Option[B])
		: Stream[IO, B] = handleEvent(x)(eventType, filter)(f)
	}

	implicit class EventHandlerInstances[A <: FXEvent](private val x: ObjectProperty[_ >: EventHandler[A]]) extends AnyVal {
		def event[B](f: A => Option[B]): Stream[IO, B] = handleEvent(x)(f)
		def event: Stream[IO, Unit] = handleEvent(x)(_ => Some(()))
	}

	implicit class ObservableListInstances[A](private val xs: ObservableList[A]) extends AnyVal {
		def observeRaw[C](consInit: Boolean)(f: ObservableList[A] => C): Stream[IO, C] = liftListRaw(xs, consInit)(f)
		def observe[C](consInit: Boolean)(factory: Factory[A, C]): Stream[IO, C] = liftList(xs, consInit)(factory)
	}

	implicit class ObservableSetInstances[A](private val xs: ObservableSet[A]) extends AnyVal {
		def observeRaw[C](consInit: Boolean)(f: ObservableSet[A] => C): Stream[IO, C] = liftSetRaw(xs, consInit)(f)
		def observe[C](consInit: Boolean)(factory: Factory[A, C]): Stream[IO, C] = liftSet(xs, consInit)(factory)
	}


}
