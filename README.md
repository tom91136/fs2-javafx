# fs2-javafx

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)


FS2 bindings for JavaFX 


## Features

 * Supports JavaFX bundled in Oracle JDK8 or the standalone JavaFX11 library
 * Compatible with ScalaFX

## Getting started

Here is a minimal HelloWorld example:

```scala
import cats.effect.IO
import fs2.Stream
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.layout.StackPane
import javafx.scene.paint.{Color, CycleMethod, LinearGradient, Stop}
import javafx.scene.shape.Rectangle
import javafx.stage.Stage
import net.kurobako.jfx.FXApp
import net.kurobako.jfx.FXIO

import scala.concurrent.duration._

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
```


## How to build

Prerequisites:

 * JDK 8
 * sbt 1.x

The project uses sbt for build so you will need to install the latest 1.x branch of sbt.

Clone the project and then in project root

    sbt assemble
    

## Licence

    Copyright 2018 WEI CHEN LIN
    
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    
       http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.