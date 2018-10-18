
package net.kurobako.jfx

import cats.effect.IO
import cats.implicits._
import fs2.Stream
import javafx.collections.FXCollections
import javafx.scene.Scene
import javafx.scene.control._
import javafx.scene.layout._
import javafx.scene.paint.{Color, CycleMethod, LinearGradient, Stop}
import javafx.scene.shape.Rectangle
import javafx.stage.Stage
import net.kurobako.jfx.Event._
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration._

class FXIOSpec extends FlatSpec with Matchers {

	behavior of "FXIOApp"
	object IOContext {
		def apply[A](io: IO[A]): A = io.unsafeRunSync()
	}

	ignore should "start the app" in IOContext {

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


	it should "flow" in IOContext {


		object MyApp extends FXApp {


			override def streamFX(args: List[String], ctx: FXApp.FXContext, stage: Stage): Stream[IO, Unit] =
				Stream.force(for {
					ls <- FXIO {
						val ls = new ListView[Long](FXCollections.observableArrayList(0L to 100L: _*))
						VBox.setVgrow(ls, Priority.ALWAYS)
						stage.setScene(new Scene(new VBox(ls)))
						stage.show()
						ls.scrollTo(50)
						ls
					}
				} yield cellFactory(ls.cellFactoryProperty())(_ => new ListCell[Long], {
					case (Some(x), cell) =>
						FXIO {
							cell.setText(x.toString)
							val color = if (x > 50) Color.GREEN else Color.WHITE
							cell.setBackground(new Background(new BackgroundFill(color, null, null)))
						}
					case (None, cell)    => FXIO {cell.setText("")}
				}).through(switchMapKeyed(x => x._2, {
					case (Some(x), cell) => Stream.force(
						FXIO {
							val a = new MenuItem("a")
							val b = new MenuItem("b")
							cell.setContextMenu(new ContextMenu(a, b))
							joinAndDrain(
								eventProp(new VBox().onMouseClickedProperty()).evalMap(e => IO(println(s"$x -> a $e"))),

								eventProp(a.onActionProperty).evalMap(e => IO(println(s"$x -> a $e"))),
								eventProp(b.onActionProperty).evalMap(e => IO(println(s"$x -> b $e"))))
						})
					case (None, cell)    => Stream.eval_(FXIO {cell.setContextMenu(null)})
				})).interruptWhen(ctx.halt)) concurrently Stream.sleep_(1 seconds) ++ Stream.eval(ctx.exit)
 

		}

		MyApp.run(Nil)

	}

}
