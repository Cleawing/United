import sbt._

object Dependencies {
  object Versions {
    val typesafeConfig  = "1.3.0"
    val finagle         = "6.27.0"
    val curator         = "2.8.0"
    val etcd4j          = "2.7.0"
    val akka            = "2.4-M2"
    val akkaStreams     = "1.0"
    val json4sJackson   = "3.3.0.RC3"
    val ramlParser      = "0.8.11"
    val scalaTest       = "2.2.5"
    val scalaReflect    = "2.11.7"
  }

  lazy val typesafeConfig = "com.typesafe" % "config" % Versions.typesafeConfig

  lazy val finagle = Seq(
    "com.twitter" %% "finagle-httpx" % Versions.finagle
  )

  lazy val curator = Seq(
    "org.apache.curator" % "curator-recipes"      % Versions.curator,
    "org.apache.curator" % "curator-x-discovery"  % Versions.curator
  )

  lazy val etcd4j = "org.mousio" % "etcd4j" % Versions.etcd4j

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

  lazy val json4s = Seq(
    "org.json4s" %% "json4s-jackson" % Versions.json4sJackson,
    "org.json4s" %% "json4s-ext" % Versions.json4sJackson
  )

  lazy val ramlParser = "org.raml" % "raml-parser" % Versions.ramlParser

  lazy val scalaTest = "org.scalatest" %% "scalatest" % Versions.scalaTest % "test"

  lazy val scalaReflect =  "org.scala-lang" % "scala-reflect" % Versions.scalaReflect
}
