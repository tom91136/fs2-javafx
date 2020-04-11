# fs2-javafx


[![Build Status](https://travis-ci.org/tom91136/fs2-javafx.svg?branch=master)](https://travis-ci.org/tom91136/fs2-javafx)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Download](https://api.bintray.com/packages/tom91136/maven/fs2-javafx/images/download.svg)](https://bintray.com/tom91136/maven/fs2-javafx/_latestVersion)


[fs2](https://fs2.io/) support for JavaFX applications.


## Features

 * Supports JavaFX bundled in JDK8 or OpenJFX11+
 * ScalaFX compatible


## Dependencies

Add this to your build.sbt:

```scala
libraryDependencies += "net.kurobako" %% "fs2-javafx" % "0.1.0"    
```
And also jcenter:
```scala
resolvers ++= Seq(Resolver.jcenterRepo)
```


## Getting started

Here is a minimal HelloWorld example:

```scala
import cats.effect.{Blocker, IO}
import fs2.Stream
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.layout.StackPane
import javafx.scene.paint.{Color, CycleMethod, LinearGradient, Stop}
import javafx.scene.shape.Rectangle
import javafx.stage.Stage
import net.kurobako.jfx.FXApp
import net.kurobako.jfx.FXIO
import scala.concurrent.ExecutionContext

import scala.concurrent.duration._

object MyApp extends FXApp {
  override protected def fxBlocker: Blocker = Blocker.liftExecutionContext(ExecutionContext.global)
  override def runFX(args: List[String], ctx: FXApp.FXContext, stage: Stage): Stream[IO, Unit] =
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
    }) ++ Stream.never[IO] // keep the window open
}
```


## How to build

To ensure the project is usable with Java 8 and [OpenJFX](https://openjfx.io/), you must build against Java 8. 

Prerequisites:

 * JDK 8 with JavaFX
 
Be aware  some OpenJDK distributions does not include JavaFX or have missing webkit libraries which is required for the sample to build. 
This project integrates the sbt launch script, so you do not need to install sbt beforehand.

    sbt clean; publishLocal

## Release process

1. Commit all changes before release
2. Make sure `~/.bintray/.credentials` exist or generate one wit `sbt bintrayChangeCredentials`
3. Run `sbt release`


## Licence

    Copyright 2020 WEI CHEN LIN
    
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    
       http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.