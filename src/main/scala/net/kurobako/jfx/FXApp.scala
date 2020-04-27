package net.kurobako.jfx

import cats.effect.{Blocker, ContextShift, ExitCode, IO, IOApp}
import cats.implicits._
import fs2.Stream
import fs2.concurrent.{Signal, SignallingRef}
import javafx.application.{Application, ConditionalFeature, HostServices, Platform}
import javafx.stage.Stage
import net.kurobako.jfx.FXApp.{FXAppHelper, FXContext, FXContextShift}

import scala.concurrent.ExecutionContext

/**
 * A variant of the [[cats.effect.IOApp]] that includes JavaFX related enhancements.
 */
trait FXApp extends IOApp {

	protected implicit def fxContextShift: FXContextShift = new FXContextShift(IO.contextShift(new ExecutionContext {
		override def execute(runnable: Runnable): Unit = {
			if (Platform.isFxApplicationThread) {
				runnable.run()
			}
			else Platform.runLater(runnable)
		}
		override def reportFailure(t: Throwable): Unit = {
			t.printStackTrace(System.err)
		}
	}))

	/**
	 * Blocker used for the allowing the `JavaFX-Launcher` thread to block.
	 * For example: {{{Blocker.liftExecutionContext(ExecutionContext.global)}}}
	 */
	protected def fxBlocker: Blocker

	/**
	 * The entry point of your JavaFX application, similar to [[cats.effect.IOApp.run]]
	 */
	def runFX(args: List[String], ctx: FXContext, mainStage: Stage): Stream[IO, Unit]

	/**
	 * Delegates to [[runFX]], do not use directly.
	 */
	override final def run(args: List[String]): IO[ExitCode] = (for {
		halt <- Stream.eval(SignallingRef[IO, Boolean](false))
		c <- Stream.eval(IO.async[(SignallingRef[IO, Boolean], FXContextShift) => (FXContext, Stage)] { cb => FXApp.ctx = cb }) concurrently
			 Stream.eval(IO.async[Unit] { cb => FXApp.stopFn = cb } *> halt.set(true)) concurrently
			 Stream.eval(fxBlocker.blockOn(IO(Application.launch(classOf[FXAppHelper], args: _*))))
		_ <- Stream.eval(IO(c(halt, fxContextShift))
			.flatMap { case (ctx, stage) => runFX(args, ctx, stage).interruptWhen(halt).compile.drain })
		//			.onFinalize(IO(Platform.exit()))
	} yield ()).compile.drain.as(ExitCode.Success)
}

object FXApp {

	/**
	 * Tagged [[ContextShift]] type specific to JavaFX
	 */
	class FXContextShift(val underlying: ContextShift[IO]) extends AnyVal

	/**
	 * JavaFX context providing [[javafx.application.HostServices]] and methods in [[javafx.application.Platform]]
	 */
	class FXContext(private val app: Application,
					private val halt: Signal[IO, Boolean])(implicit val fxcs: FXContextShift) {
		def hostServices: IO[HostServices] = IO(app.getHostServices)
		def exit: IO[Unit] = IO(Platform.exit())
		def implicitExit(exit: Boolean): IO[Unit] = IO(Platform.setImplicitExit(exit))
		def supported(feature: ConditionalFeature): IO[Boolean] = IO(Platform.isSupported(feature))
	}

	implicit val FXContextFXContextShiftInstance: FXContext => FXContextShift = _.fxcs

	private var ctx   : Either[Throwable,
		(SignallingRef[IO, Boolean], FXContextShift) => (FXContext, Stage)] => Unit = _
	private var stopFn: Either[Throwable, Unit] => Unit                             = _

	private class FXAppHelper extends Application {
		override def start(primaryStage: Stage): Unit = FXApp.ctx(Right(new FXContext(this, _)(_) -> primaryStage))
		override def stop(): Unit = stopFn(Right(()))
	}

}
