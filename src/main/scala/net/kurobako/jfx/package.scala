package net.kurobako

import cats.arrow.FunctionK
import cats.effect.{Concurrent, ContextShift, IO}
import cats.implicits._
import cats.~>
import fs2.Stream
import net.kurobako.jfx.FXApp.FXContextShift

package object jfx {


	val IoToStream: FunctionK[IO, Stream[IO, ?]] = λ[IO ~> Stream[IO, ?]](Stream.eval(_))

	@inline def FXIO[A](a: => A)(implicit fxcs: FXContextShift, cs: ContextShift[IO]): IO[A] =
		IO.shift(fxcs.underlying) *> IO(a) <* IO.shift(cs)


	def joinAndDrain(xs: Stream[IO, Any]*)(implicit ev: Concurrent[IO]): Stream[IO, Nothing] =
		Stream(xs: _*).parJoinUnbounded.drain

	private[jfx] def unsafeRunAsync[A](f: IO[A])(implicit fxcs: FXContextShift): Unit =
		f.start(fxcs.underlying).flatMap(_.join).runAsync(_ => IO.unit).unsafeRunSync()


}
