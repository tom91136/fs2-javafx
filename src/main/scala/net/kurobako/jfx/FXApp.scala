package net.kurobako.jfx

import cats.effect.{Blocker, ContextShift, ExitCode, IO, IOApp}
import cats.implicits._
import fs2.Stream
import fs2.concurrent.{Signal, SignallingRef}
import javafx.application.{Application, ConditionalFeature, HostServices, Platform}
import javafx.stage.Stage
import net.kurobako.jfx.FXApp.{FXAppHelper, FXContext, FXContextShift}

import scala.concurrent.ExecutionContext

trait FXApp extends IOApp {


	private def time[R](name: String)(block: => R): R = {
		val t0     = System.nanoTime()
		val result = block
		val t1     = System.nanoTime()
		println(s"$name =>  ${((t1 - t0).toFloat / 1000000)}ms")
		result
	}

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

	protected def fxBlocker: Blocker

	def runFX(args: List[String], ctx: FXContext, mainStage: Stage): Stream[IO, Unit]

	override final def run(args: List[String]): IO[ExitCode] = (for {
		halt <- Stream.eval(SignallingRef[IO, Boolean](false))
		c <- Stream.eval(IO.async[(SignallingRef[IO, Boolean], FXContextShift) => (FXContext, Stage)] { cb => FXApp.ctx = cb }) concurrently
			 Stream.eval(IO.async[Unit] { cb => FXApp.stopFn = cb } *> halt.set(true)) concurrently
			 Stream.eval(fxBlocker.blockOn(IO(Application.launch(classOf[FXAppHelper], args: _*))))
		_ <- Stream.eval(IO(c(halt, fxContextShift))
			.flatMap { case (ctx, stage) => runFX(args, ctx, stage).compile.drain })
			.onFinalize(IO(Platform.exit()))
		_ <- Stream.eval(IO(println("Stream ended")))
	} yield ()).compile.drain.as(ExitCode.Success)
}

object FXApp {

	class FXContextShift(val underlying: ContextShift[IO]) extends AnyVal

	case class FXContext(hostServices: HostServices, halt: Signal[IO, Boolean])
						(implicit val fxcs: FXContextShift) {
		def exit = IO(Platform.exit())
		def implicitExit(exit: Boolean) = IO(Platform.setImplicitExit(exit))
		def supported(feature: ConditionalFeature) = IO(Platform.isSupported(feature))
	}

	implicit val FXContextFXContextShiftInstance: FXContext => FXContextShift = _.fxcs

	private var ctx   : Either[Throwable,
		(SignallingRef[IO, Boolean], FXContextShift) => (FXContext, Stage)] => Unit = _
	private var stopFn: Either[Throwable, Unit] => Unit                             = _

	private class FXAppHelper extends Application {

		override def start(primaryStage: Stage): Unit = {
			println(s"FX start, ctx=${FXApp.ctx}")
			FXApp.ctx(Right(FXContext(getHostServices, _)(_) -> primaryStage))
		}
		override def stop(): Unit = {
			println(s"FX stop, stopFn=${stopFn}")
			stopFn(Right(()))
		}
	}

}
