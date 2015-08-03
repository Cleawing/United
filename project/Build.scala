import sbt._
import sbt.Keys._

object Build extends Build {
  lazy val commonSettings = Seq(
    organizationName := "Cleawing Inc",
    organization := "com.cleawing",
    version := "0.1",
    scalaVersion := "2.11.7",
    scalacOptions ++= Seq("-feature", "-deprecation", "-unchecked"),
    javacOptions ++= Seq("-source", "1.7", "-target", "1.7"),
    resolvers ++= Seq(
      DefaultMavenRepository,
      "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
      "Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/",
      Classpaths.typesafeReleases,
      Classpaths.sbtPluginReleases
    ),
    libraryDependencies ++= Seq(Dependencies.typesafeConfig, Dependencies.scalaReflect, Dependencies.scalaTest)
  )

  lazy val united = (project in file(".")).
    aggregate(`akka-extensions`, `finagle-services`)

  lazy val `akka-extensions` = (project in file("akka-extensions")).
    settings(commonSettings: _*).
    settings(
      libraryDependencies ++= Seq(Dependencies.json4s)
        ++ Dependencies.akka ++ Dependencies.akkaStreamHttp,
        initialCommands in console :=
        """
          |import akka.actor.ActorSystem
          |import com.cleawing.akka.consul.Consul
          |val system = ActorSystem()
          |val consul = Consul(system)
        """.stripMargin
    )

  lazy val `finagle-services` = (project in file("finagle-services")).
    settings(commonSettings: _*).
    settings(
      libraryDependencies ++= Dependencies.finagle ++ Seq(Dependencies.json4s, Dependencies.ramlParser),
      initialCommands in console :=
      """
        |import scala.concurrent.ExecutionContext.Implicits.global
        |import com.cleawing.finagle.http.RamlClient
        |import com.cleawing.finagle.http.RamlHelper
        |import com.cleawing.finagle.consul.Consul
        |implicit val raml = RamlHelper("consul/v1.raml")
        |val consul = Consul("192.168.99.100").v1
      """.stripMargin
    )
}