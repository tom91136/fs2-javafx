import ReleaseTransformations._

lazy val `fs2-javafx` = project.in(file(".")).settings(
	organization := "net.kurobako",
	name := "fs2-javafx",
	scalaVersion := "2.13.6",
	scalacOptions ++= Seq(
		"-P:bm4:no-map-id:y",
	),
	scalacOptions ~= filterConsoleScalacOptions,
	Test / fork := true,
	Test / testForkedParallel := false,
	javacOptions ++= Seq(
		"-target", "1.8",
		"-source", "1.8",
		"-Xlint:all"),
	addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
	addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),

	libraryDependencies ++= Seq(
		"co.fs2" %% "fs2-core" % "3.0.6",
		"org.scalatest" %% "scalatest" % "3.2.9" % Test
	),


	developers := List(Developer(
		id = "tom91136",
		name = "Tom Lin",
		email = "tom91136@gmail.com",
		url("https://github.com/tom91136")
	)),
	homepage := Some(url(s"https://github.com/tom91136/${name.value}")),
	licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),


	// publish settings
	bintrayPackage := name.value,
	bintrayReleaseOnPublish := false,

	releaseProcess := Seq[ReleaseStep](
		checkSnapshotDependencies,
		inquireVersions,
		runClean,
		releaseStepCommandAndRemaining("^ compile"), // still no tests =(
		setReleaseVersion,
		commitReleaseVersion,
		tagRelease,
		releaseStepCommandAndRemaining("^ publish"),
		releaseStepTask(bintrayRelease),
		setNextVersion,
		commitNextVersion,
		pushChanges
	)
)
