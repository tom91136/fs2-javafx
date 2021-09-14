package net.kurobako.jfx

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import fs2.Stream
import fs2.concurrent.{Signal, SignallingRef}
import javafx.application.{Application, ConditionalFeature, HostServices, Platform}
import javafx.stage.Stage
import net.kurobako.jfx.FXApp.{FXAppHelper, FXContext}
import net.kurobako.jfx.syntax.FXIO

import scala.concurrent.ExecutionContext

/**
 * A variant of the [[cats.effect.IOApp]] that includes JavaFX related enhancements.
 */
trait FXApp extends IOApp {


	/**
	 * The entry point of your JavaFX application, similar to [[cats.effect.IOApp.run]]
	 */
	def runFX(args: List[String], ctx: FXContext, mainStage: Stage): Stream[IO, Unit]

	/**
	 * Delegates to [[runFX]], do not use directly.
	 */
	override final def run(args: List[String]): IO[ExitCode] = {

		for{
			halt <- SignallingRef[IO, Boolean](false)
			fx <- (for {
				c <- Stream.eval(IO.async_[(SignallingRef[IO, Boolean]) => (FXContext, Stage)] { cb => FXApp.ctx = cb }) concurrently
				     Stream.eval(IO.async_[Unit] { cb => FXApp.stopFn = cb } *> halt.set(true)) concurrently
				     Stream.eval(IO.blocking(Application.launch(classOf[FXAppHelper], args: _*)))
				_ <- Stream.eval(IO(c(halt))
					.flatMap { case (ctx, stage) =>
						FXIO { Platform.setImplicitExit(false) } *>
						runFX(args, ctx, stage).interruptWhen(halt).compile.drain })
					.onFinalize(IO(Platform.exit()))
			} yield ()).compile.drain.start
			_ <- fx.joinWith(IO.println("Cancelled, sending term to FX...") *> halt.set(true))
		} yield ExitCode.Success
	}
}

object FXApp {


	/**
	 * JavaFX context providing [[javafx.application.HostServices]] and methods in [[javafx.application.Platform]]
	 */
	class FXContext(private val app: Application,
	                private val halt: Signal[IO, Boolean]) {
		def hostServices: IO[HostServices] = IO(app.getHostServices)
		def exit: IO[Unit] = IO(Platform.exit())
		def implicitExit(exit: Boolean): IO[Unit] = IO(Platform.setImplicitExit(exit))
		def supported(feature: ConditionalFeature): IO[Boolean] = IO(Platform.isSupported(feature))
	}


	private var ctx   : Either[Throwable,
		(SignallingRef[IO, Boolean]) => (FXContext, Stage)] => Unit = _
	private var stopFn: Either[Throwable, Unit] => Unit             = _

	private class FXAppHelper extends Application {
		override def start(primaryStage: Stage): Unit = FXApp.ctx(Right(new FXContext(this, _) -> primaryStage))
		override def stop(): Unit = {
			println("FX requested termination...")
			stopFn(Right(()))
		}
	}

}
