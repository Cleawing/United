import sbt._
import sbt.Keys._

object Build extends Build {
  lazy val commonSettings = Seq(
    organizationName := "Cleawing Inc",
    organization := "com.cleawing",
    version := "0.1",
    scalaVersion := "2.11.7",
    resolvers ++= Seq(
      DefaultMavenRepository,
      "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
      "Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/",
      Classpaths.typesafeReleases,
      Classpaths.sbtPluginReleases
    ),
    libraryDependencies += Dependencies.scalaTest
  )

  lazy val united = (project in file(".")).
    aggregate(`akka-extensions`)

  lazy val `akka-extensions` = (project in file("akka-extensions")).
    settings(commonSettings: _*).
    settings(
      libraryDependencies ++= Seq(Dependencies.typesafeConfig, Dependencies.json4s)
        ++ Dependencies.akka ++ Dependencies.akkaStreamHttp,
        initialCommands in console :=
        """
          |import akka.actor.ActorSystem
          |import com.cleawing.akka.consul.Consul
          |val system = ActorSystem()
          |val consul = Consul(system)
        """.stripMargin
    )
}
