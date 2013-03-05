organization := "de.debilski.pelita"

name := "Pelita CI"

version := "0.1-SNAPSHOT"

scalaVersion := "2.10.0"

seq(com.github.siasia.WebPlugin.webSettings :_*)

libraryDependencies ++= {
  val liftVersion = "2.5-RC1"
  Seq(
    "net.liftweb" %% "lift-webkit" % liftVersion % "compile",
    "org.eclipse.jetty" % "jetty-webapp" % "8.1.9.v20130131" % "container,test"
  )
}

resolvers += "Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/"

libraryDependencies ++= {
  val scalazVersion = "7.0.0-M7"
  Seq(
    "org.scalaz" %% "scalaz-core" % scalazVersion,
    "org.scalaz" %% "scalaz-effect" % scalazVersion
  )
}

libraryDependencies ++= {
  val akkaVersion = "2.1.0"
  Seq(
  	"com.typesafe.akka" %% "akka-actor" % akkaVersion,
  	"com.typesafe.akka" %% "akka-zeromq" % akkaVersion,
  	"com.typesafe.akka" %% "akka-testkit" % akkaVersion
  )
}

libraryDependencies += "org.scalatest" %% "scalatest" % "1.9.1" % "test"

libraryDependencies += "org.specs2" %% "specs2" % "1.14" % "test"

autoCompilerPlugins := true

libraryDependencies <+= scalaVersion {
  v => compilerPlugin("org.scala-lang.plugins" % "continuations" % "2.10.0")
}

scalacOptions += "-P:continuations:enable"

scalacOptions += "-deprecation"

scalacOptions += "-feature"

scalacOptions += "-Xlint"

libraryDependencies += "com.typesafe.akka" %% "akka-dataflow" % "2.1.0"

libraryDependencies ++= List(
  // use the right Slick version here:
  "com.typesafe.slick" %% "slick" % "1.0.0",
  "com.h2database" % "h2" % "1.3.166"
)

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.0.9" % "runtime"

seq(coffeeSettings: _*)

(resourceManaged in (Compile, CoffeeKeys.coffee)) <<= (webappResources in Compile)(_.get.head / "js")

Compass.settings
