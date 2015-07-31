import sbt._

object Dependencies {
  object Versions {
    val typesafeConfig  = "1.3.0"
    val akka            = "2.4-M2"
    val akkaStreams     = "1.0"
    val json4sJackson   = "3.3.0.RC3"
    val scalaTest       = "2.2.5"
  }

  lazy val typesafeConfig = "com.typesafe" % "config" % Versions.typesafeConfig

  lazy val json4s = "org.json4s" %% "json4s-jackson" % Versions.json4sJackson

  lazy val akka = Seq(
    "com.typesafe.akka" %% "akka-actor"   % Versions.akka,
    "com.typesafe.akka" %% "akka-testkit" % Versions.akka % "test"
  )

  lazy val akkaStream = Seq(
    "com.typesafe.akka" %% "akka-stream-experimental"         % Versions.akkaStreams,
    "com.typesafe.akka" %% "akka-stream-testkit-experimental" % Versions.akkaStreams % "test"
  )

  lazy val akkaStreamHttp = Seq(
    "com.typesafe.akka" %% "akka-http-core-experimental"      % Versions.akkaStreams,
    "com.typesafe.akka" %% "akka-http-experimental"           % Versions.akkaStreams,
    "com.typesafe.akka" %% "akka-http-testkit-experimental"   % Versions.akkaStreams % "test"
  )

  lazy val scalaTest = "org.scalatest" %% "scalatest" % Versions.scalaTest % "test"
}
