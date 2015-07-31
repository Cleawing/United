import sbt._
import Keys._

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
    aggregate(consul)

  lazy val consul = (project in file("consul")).
    settings(commonSettings: _*).
    settings(
      libraryDependencies += Dependencies.json4s,
      initialCommands in console :=
        """
          |import akka.actor.ActorSystem
          |import com.cleawing.consul.Consul
          |val system = ActorSystem()
          |val consul = Consul(system)
        """.stripMargin
    ).
    dependsOn(akka)

  lazy val akka = (project in file("akka")).
    settings(commonSettings: _*).
    settings(
      libraryDependencies ++= Seq(Dependencies.typesafeConfig)
        ++ Dependencies.akka ++ Dependencies.akkaStreamHttp
    )
}
