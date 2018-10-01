
lazy val `fs2-javafx` = project.in(file(".")).settings(
	organization := "net.kurobako",
	name := "fs2-javafx",
	version := "0.1.0-SNAPSHOT",
	scalaVersion := "2.12.7",
	scalacOptions ++= Seq(
		"-target:jvm-1.8",
		"-encoding", "UTF-8",
		"-unchecked",
		"-deprecation",
		"-explaintypes",
		"-feature",
		"-Xfuture",

		"-language:existentials",
		"-language:experimental.macros",
		"-language:higherKinds",
		"-language:postfixOps",
		"-language:implicitConversions",

		"-Xlint:adapted-args",
		"-Xlint:by-name-right-associative",
		"-Xlint:constant",
		"-Xlint:delayedinit-select",
		"-Xlint:doc-detached",
		"-Xlint:inaccessible",
		"-Xlint:infer-any",
		"-Xlint:missing-interpolator",
		"-Xlint:nullary-override",
		"-Xlint:nullary-unit",
		"-Xlint:option-implicit",
		"-Xlint:package-object-classes",
		"-Xlint:poly-implicit-overload",
		"-Xlint:private-shadow",
		"-Xlint:stars-align",
		"-Xlint:type-parameter-shadow",
		"-Xlint:unsound-match",

		"-Yno-adapted-args",
		"-Ywarn-dead-code",
		"-Ywarn-extra-implicit",
		"-Ywarn-inaccessible",
		"-Ywarn-infer-any",
		"-Ywarn-nullary-override",
		"-Ywarn-nullary-unit",
		"-Ywarn-numeric-widen",
		"-Ywarn-unused:implicits",
		//		"-Ywarn-unused:imports",
		"-Ywarn-unused:locals",
		"-Ywarn-unused:params",
		"-Ywarn-unused:patvars",
		"-Ywarn-unused:privates",
		"-Ywarn-value-discard",
		"-Ypartial-unification",

		// TODO enable to Scala 2.12.5
		//		"-Ybackend-parallelism", "4",
		//		"-Ycache-plugin-class-loader:last-modified",
		//		"-Ycache-macro-class-loader:last-modified",

		// XXX enable for macro debug
		//		"-Ymacro-debug-lite",
		//			"-Xlog-implicits",
		"-P:bm4:no-map-id:y",
	),
	javacOptions ++= Seq(
		"-target", "1.8",
		"-source", "1.8",
		"-Xlint:all"),
	addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.2.4"),
	addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.6"),

	libraryDependencies ++= Seq(
		"co.fs2" %% "fs2-core" % "1.0.0-RC2",
		"org.scalatest" %% "scalatest" % "3.0.1" % Test
	)
)
