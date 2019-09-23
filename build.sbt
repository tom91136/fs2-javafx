import sbt.Tests.{Group, SubProcess}

lazy val `fs2-javafx` = project.in(file(".")).settings(
	organization := "net.kurobako",
	name := "fs2-javafx",
	version := "0.1.0-SNAPSHOT",
	scalaVersion := "2.13.1",
	scalacOptions ++= Seq(
		"-P:bm4:no-map-id:y",
	),
	Test / fork := true,
	Test / testForkedParallel := false,
	javacOptions ++= Seq(
		"-target", "1.8",
		"-source", "1.8",
		"-Xlint:all"),
	addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
	addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),

	libraryDependencies ++= Seq(
		"co.fs2" %% "fs2-core" % "2.0.1",
		"org.scalatest" %% "scalatest" % "3.0.8" % Test
	)
	
)
