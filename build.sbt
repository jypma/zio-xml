val ZIOVersion = "2.0.1"

scalaVersion := "2.13.7"

libraryDependencies := Seq(
  "org.scala-lang.modules" %% "scala-xml" % "2.0.1",
  "com.fasterxml" % "aalto-xml" % "1.2.2",
  "dev.zio" %% "zio" % ZIOVersion,
  "dev.zio" %% "zio-streams" % ZIOVersion,
  "dev.zio" %% "zio-interop-reactivestreams" % "1.3.5",
  "dev.zio" %% "zio-test" % ZIOVersion % "test",
  "dev.zio" %% "zio-test-sbt" % ZIOVersion % "test",
)

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

lazy val `zio-xml` = (project in file("."))

ThisBuild / version := "0.1.0"
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / organization := "net.ypmania.zioxml"
ThisBuild / organizationName := "Ypmania"
ThisBuild / organizationHomepage := Some(url("https://github.com/jypma/"))

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/jypma/zio-xml"),
    "scm:git@github.com:jypma/zio-xml.git"
  )
)
ThisBuild / developers := List(
  Developer(
    id    = "jypma",
    name  = "Jan Ypma",
    email = "jan@ypmania.net",
    url   = url("https://github.com/jypma/")
  )
)

ThisBuild / description := "XML streaming directives for ZIO."
ThisBuild / licenses := List("MIT" -> new URL("https://mit-license.org/"))
ThisBuild / homepage := Some(url("https://github.com/jypma/zio-xml"))

// Remove all additional repository other than Maven Central from POM
ThisBuild / pomIncludeRepository := { _ => false }
ThisBuild / publishTo := {
  val nexus = "https://s01.oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}
ThisBuild / publishMavenStyle := true
