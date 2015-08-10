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
    aggregate(`akka-extensions`, `finagle-services`, inception)

  lazy val `akka-extensions` = (project in file("akka-extensions")).
    settings(commonSettings: _*).
    settings(
      resolvers += "Confluent repository" at "http://packages.confluent.io/maven/",
      libraryDependencies ++= Dependencies.json4s ++ Dependencies.akka
        ++ Dependencies.akkaStreamHttp ++ Dependencies.curator ++ Seq(Dependencies.etcd4j, Dependencies.reactiveKafka)
        ++ Dependencies.confluent,
        initialCommands in console :=
        """
          |import akka.actor.ActorSystem
          |import com.cleawing.akka.EmptyActor
          |import com.cleawing.akka.curator.Curator
          |implicit val system = ActorSystem()
          |val curator = Curator(system)
          |val emptyActor = system.actorOf(EmptyActor.props())
        """.stripMargin
    )

  lazy val `finagle-services` = (project in file("finagle-services")).
    settings(commonSettings: _*).
    settings(
      libraryDependencies ++= Dependencies.finagle ++ Dependencies.json4s ++ Seq(Dependencies.ramlParser),
      initialCommands in console :=
      """
        |import scala.concurrent.ExecutionContext.Implicits.global
        |import com.cleawing.finagle.consul.Consul
        |val consul = Consul("192.168.99.100").v1
      """.stripMargin
    )

  lazy val inception = (project in file("inception")).
    settings(commonSettings: _*).dependsOn(`akka-extensions`, `finagle-services`)
}