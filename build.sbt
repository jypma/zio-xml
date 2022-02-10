val ZIOVersion = "2.0.0-RC1+171-08242aee-SNAPSHOT"

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
