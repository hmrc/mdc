import sbt.Keys._
import sbt._

val scala2_13 = "2.13.16"
val scala3    = "3.3.6"

lazy val library = Project("mdc", file("."))
  .disablePlugins(sbt.plugins.JUnitXmlReportPlugin)
  .settings(
    scalaVersion       := scala2_13,
    crossScalaVersions := Seq(scala2_13, scala3),
    majorVersion       := 0,
    isPublicArtefact   := true,
    libraryDependencies ++= LibDependencies.compileDependencies ++ LibDependencies.testDependencies
  )
