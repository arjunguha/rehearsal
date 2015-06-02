version in ThisBuild := "0.1"

scalaVersion in ThisBuild := "2.11.5"

scalacOptions in ThisBuild ++= Seq(
  "-deprecation",
  "-unchecked",
  "-feature",
  "-Xfatal-warnings"
)

resolvers in ThisBuild ++= Seq(
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "Typesafe Repository" at "http://repo.akka.io/snapshots/",
  "PLASMA" at "https://dl.bintray.com/plasma-umass/maven"
)

libraryDependencies in ThisBuild ++= {
  val akkaV = "2.3.4"
  val graphV = "1.9.0"
  Seq(
    "org.scalatest" %% "scalatest" % "2.2.1" % "test",
    "org.scalacheck" %% "scalacheck" % "1.10.1" % "test",
    "com.assembla.scala-incubator" %% "graph-core" % graphV,
    "edu.umass.cs" %% "scala-puppet" % "0.2.3",
    "com.typesafe.akka" %% "akka-actor"  % akkaV,
    "com.typesafe.akka" %% "akka-kernel" % akkaV,
    "com.typesafe.akka" %% "akka-remote" % akkaV,
    "org.scala-lang" % "scala-compiler" % "2.11.5",
    "jline" % "jline" % "2.11"
  )
}


lazy val installer = project.dependsOn(common)

lazy val common = project

lazy val bdd = project

lazy val rehearsal = project.dependsOn(common, bdd)

lazy val  root = project.in(file("."))
  .aggregate(rehearsal)

parallelExecution in Test := false

libraryDependencies ++= Seq(
  "edu.umass.cs" %% "scala-puppet" % "0.2.3")

/*
 * D - Show durations for each test
 * F - Show full stack traces on exception
 */
testOptions in Test += Tests.Argument("-oD")
