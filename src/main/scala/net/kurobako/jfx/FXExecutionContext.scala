package net.kurobako.jfx

import javafx.application.Platform

import scala.concurrent.ExecutionContext

object FXExecutionContext extends   ExecutionContext {
	override def execute(runnable: Runnable): Unit = {
		if (Platform.isFxApplicationThread) {
			runnable.run()
		}
		else Platform.runLater(runnable)
	}
	override def reportFailure(t: Throwable): Unit = {
		t.printStackTrace(System.err)
	}
}