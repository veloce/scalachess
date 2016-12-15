name := "scalachess"

organization := "org.lichess"

version := "5.3"

scalaVersion := "2.11.8"

libraryDependencies ++= List(
  "org.specs2" %% "specs2-core" % "3.6" % "test",
  "joda-time" % "joda-time" % "2.9.4",
  "org.joda" % "joda-convert" % "1.8",
  "org.scala-lang.modules" %% "scala-java8-compat" % "0.8.0",
  "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4"
)

// updateOptions := updateOptions.value.withCachedResolution(true)

resolvers ++= Seq(
  "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases")

scalacOptions ++= Seq(
  "-deprecation",
  "-unchecked",
  "-feature",
  "-language:_",
  "-Xfatal-warnings",
  "-Ybackend:GenBCode", "-Ydelambdafy:method", "-target:jvm-1.8")
