scalacOptions ++= Seq("-deprecation", "-feature")
lazy val scala213 = "2.13.4"
lazy val scala212 = "2.12.13"

lazy val commonSettings = Seq(
  name := "server",
  version := "1.0.0",
  scalaVersion := scala212
)

enablePlugins(DockerPlugin)

lazy val commonDependencies = Seq(
  "org.slf4j" % "slf4j-api" % "1.7.30",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.2",
  dependencies.specs2Core,
  dependencies.specs2jUnit
)

logLevel := Level.Warn
lazy val dependencies =
  new {
    val akkaVersion = "2.6.12"
    val akkaHttpVersion = "10.2.3"
    val specs2Version = "4.10.5"
    val akkaHttpJsonV = "1.35.3"

    val redis = "net.debasishg" %% "redisclient" % "3.30"
    val jodaTime = "com.github.nscala-time" %% "nscala-time" % "2.26.0"
    val akkaStreamTyped = "com.typesafe.akka" %% "akka-stream-typed" % akkaVersion
    val akkaStream = "com.typesafe.akka" %% "akka-stream" % akkaVersion
    val akkaActorTyped = "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion
    val akkaActor = "com.typesafe.akka" %% "akka-actor" % akkaVersion
    val akkaHttp = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion
    val akkaTestKitTyped = "com.typesafe.akka" %% "akka-testkit-typed" % "2.5.12"
    val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
    val akkaJson4s = "de.heikoseeberger" %% "akka-http-json4s" % akkaHttpJsonV
    val json4sJackson = "org.json4s" %% "json4s-jackson" % "3.6.10"
    val specs2Core = "org.specs2" %% "specs2-core" % specs2Version % Test
    val specs2jUnit = "org.specs2" %% "specs2-junit" % specs2Version % Test
    val akkaStreamTst = "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test
  }

lazy val root = (project in file("."))
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= commonDependencies ++ Seq(
      dependencies.redis,
      dependencies.akkaStreamTyped,
      dependencies.akkaStream,
//      dependencies.akkaActorTyped,
      dependencies.akkaActor,
      dependencies.akkaHttp,
      dependencies.akkaSlf4j,
      dependencies.jodaTime,
      dependencies.json4sJackson,
      dependencies.akkaJson4s
    )
  )