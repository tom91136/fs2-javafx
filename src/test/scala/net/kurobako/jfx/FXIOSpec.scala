
package net.kurobako.jfx

import cats.effect.IO
import fs2.Stream
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.layout.StackPane
import javafx.scene.paint.{Color, CycleMethod, LinearGradient, Stop}
import javafx.scene.shape.Rectangle
import javafx.stage.Stage
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration._

class FXIOSpec extends FlatSpec with Matchers {

	behavior of "FXIOApp"
	object IOContext {
		def apply[A](io: IO[A]): A = io.unsafeRunSync()
	}

	it should "start the app" in IOContext {

		object MyApp extends FXApp {
			override def streamFX(args: List[String], ctx: FXApp.FXContext, stage: Stage): Stream[IO, Unit] =
				Stream.eval_(FXIO {
					stage.setScene(new Scene(new StackPane(
						new Rectangle(100, 100, new LinearGradient(0f, 1f, 1f, 0f, true, CycleMethod.NO_CYCLE,
							new Stop(0, Color.web("#f8bd55")),
							new Stop(0.14, Color.web("#c0fe56")),
							new Stop(0.28, Color.web("#5dfbc1")),
							new Stop(0.43, Color.web("#64c2f8")),
							new Stop(0.57, Color.web("#be4af7")),
							new Stop(0.71, Color.web("#ed5fc2")),
							new Stop(0.85, Color.web("#ef504c")),
							new Stop(1, Color.web("#f2660f")))),
						new Label("Hello world!") {
							setTextFill(Color.WHITE)
						})))
					stage.show()
				}) ++ Stream.sleep_(300 milliseconds) ++ Stream.eval(ctx.exit)
		}

		MyApp.run(Nil)
	}
	
}
