
package net.kurobako.jfx

import cats.effect.{Blocker, IO}
import cats.implicits._
import fs2.Stream
import javafx.beans.property._
import javafx.collections.FXCollections
import javafx.scene.Scene
import javafx.scene.control._
import javafx.scene.layout._
import javafx.scene.paint.{Color, CycleMethod, LinearGradient, Stop}
import javafx.scene.shape.Rectangle
import javafx.stage.Stage
import net.kurobako.jfx.Event._
import org.scalatest.{FlatSpec, Matchers}

import scala.collection.immutable.ArraySeq
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

class FXIOSpec extends FlatSpec with Matchers {

	behavior of "FXIOApp"
	object IOContext {
		def apply[A](io: IO[A]): A = io.unsafeRunSync()
	}

	trait GlobalBlockerFXApp extends FXApp {
		override protected def fxBlocker: Blocker = Blocker.liftExecutionContext(ExecutionContext.global)
	}

	object MyApp1 extends GlobalBlockerFXApp {
		override def runFX(args: List[String], ctx: FXApp.FXContext, stage: Stage): Stream[IO, Unit] = {


			indexed(FXCollections.observableArrayList(1, 2))(ArraySeq)
			indexed(FXCollections.observableArrayList(1, 2))(List)
			indexedRaw(FXCollections.observableArrayList(1, 2))(_.get(0))

			lift(new SimpleStringProperty(), consInit = true): Stream[IO, Option[String]]
			lift(new SimpleBooleanProperty(), consInit = true): Stream[IO, Option[Boolean]]
			lift(new SimpleDoubleProperty(), consInit = true): Stream[IO, Option[Double]]
			lift(new SimpleFloatProperty(), consInit = true): Stream[IO, Option[Float]]
			lift(new SimpleLongProperty(), consInit = true): Stream[IO, Option[Long]]
			lift(new SimpleIntegerProperty(), consInit = true): Stream[IO, Option[Int]]
			Stream.eval(IO.unit)
		}
	}

	//	it should "typecheck for primitives" in IOContext(MyApp1.run(Nil))

	object MyApp2 extends GlobalBlockerFXApp {
		override def runFX(args: List[String], ctx: FXApp.FXContext, stage: Stage): Stream[IO, Unit] =
			Stream.eval_(FXIO {


				stage.setScene(new Scene(new StackPane(
					new Rectangle(100, 100,
						new LinearGradient(0f, 1f, 1f, 0f, true, CycleMethod.NO_CYCLE,
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
			}) ++ Stream.never[IO]
//		++
			//			Stream.eval_ {
			//				ctx.hostServices.flatMap { h => IO {
			//					println(h.showDocument("a"))}
			//				}
			//			}
			//		++
//			Stream.sleep_(300 milliseconds) ++
//			Stream.eval(ctx.exit)
	}
		it should "start the app" in IOContext(MyApp2.run(Nil))

	object MyApp3 extends GlobalBlockerFXApp {

		override def runFX(args: List[String], ctx: FXApp.FXContext, stage: Stage): Stream[IO, Unit] =
			Stream.force(for {
				_ <- ctx.implicitExit(true)
				ls <- FXIO {
					val ls = new ListView[Long](FXCollections.observableArrayList(0L to 100L: _*))
					VBox.setVgrow(ls, Priority.ALWAYS)
					stage.setScene(new Scene(new VBox(ls)))
					stage.show()
					ls.scrollTo(50)
					ls
				}
			} yield simpleListCellFactory(ls.cellFactoryProperty())({
				case (Some(x), cell) =>
					FXIO {
						cell.setText(x.toString)
						val color = if (x % 2 == 0) Color.GREEN else Color.WHITE
						cell.setBackground(new Background(new BackgroundFill(color, null, null)))
					}
				case (None, cell)    => FXIO {cell.setText("")}
			}).through(switchMapKeyed(x => x._2, {
				case (Some(x), cell) => Stream.force(
					FXIO {
						val a = new MenuItem(s"Action a ($x)")
						val b = new MenuItem(s"Action b ($x)")
						cell.setContextMenu(new ContextMenu(a, b))
						joinAndDrain(
							handleEvent(new VBox().onMouseClickedProperty())().evalMap(e => IO(println(s"$x -> a $e"))),
							handleEvent(a.onActionProperty)().evalMap(e => IO(println(s"$x -> a $e"))),
							handleEvent(b.onActionProperty)().evalMap(e => IO(println(s"$x -> b $e"))))
					})
				case (None, cell)    => Stream.eval_(FXIO {cell.setContextMenu(null)})
			})))

	}

//	it should "flow" in IOContext(MyApp3.run(Nil))

}
