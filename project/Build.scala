import sbt._
import Keys._

object Build extends Build {
  lazy val commonSettings = Seq(
    organizationName := "Cleawing Inc",
    organization := "com.cleawing",
    version := "0.1",
    scalaVersion := "2.11.7",
    libraryDependencies += Dependencies.scalaTest
  )

  lazy val united = (project in file(".")).
    aggregate(consul)

  lazy val consul = (project in file("consul")).
    settings(commonSettings: _*).
    dependsOn(akka)

  lazy val akka = (project in file("akka")).
    settings(commonSettings: _*).
    settings(
      libraryDependencies ++= Seq(Dependencies.typesafeConfig)
        ++ Dependencies.akka ++ Dependencies.akkaStreamHttp
    )
}
