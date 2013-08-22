organization := "de.debilski.pelita"

name := "Pelita CI"

version := "0.2-SNAPSHOT"

scalaVersion := "2.10.1"

seq(com.github.siasia.WebPlugin.webSettings :_*)

libraryDependencies ++= {
  val liftVersion = "2.5"
  Seq(
    "net.liftweb" %% "lift-webkit" % liftVersion % "compile",
    "org.eclipse.jetty" % "jetty-webapp" % "8.1.9.v20130131" % "container,test",
    "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "container,compile" artifacts Artifact("javax.servlet", "jar", "jar")
  )
}

resolvers += "Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/"

libraryDependencies ++= {
  val scalazVersion = "7.0.3"
  Seq(
    "org.scalaz" %% "scalaz-core" % scalazVersion,
    "org.scalaz" %% "scalaz-effect" % scalazVersion
  )
}

libraryDependencies ++= {
  val akkaVersion = "2.2.0"
  Seq(
  	"com.typesafe.akka" %% "akka-actor" % akkaVersion,
  	"com.typesafe.akka" %% "akka-agent" % akkaVersion,
  	"com.typesafe.akka" %% "akka-zeromq" % akkaVersion,
  	"com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  	"com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  	"com.typesafe.akka" %% "akka-dataflow" % akkaVersion
  )
}

libraryDependencies += "org.scalatest" %% "scalatest" % "1.9.1" % "test"

libraryDependencies += "org.specs2" %% "specs2" % "1.14" % "test"

autoCompilerPlugins := true

libraryDependencies <+= scalaVersion {
  v => compilerPlugin("org.scala-lang.plugins" % "continuations" % "2.10.1")
}

scalacOptions += "-P:continuations:enable"

scalacOptions += "-deprecation"

scalacOptions += "-feature"

scalacOptions += "-Xlint"

libraryDependencies ++= List(
  // use the right Slick version here:
  "com.typesafe.slick" %% "slick" % "1.0.0",
  "com.h2database" % "h2" % "1.3.171"
)

libraryDependencies += "com.typesafe" % "config" % "1.0.0"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.0.11" % "runtime"

libraryDependencies += "org.codehaus.janino" % "janino" % "2.6.1"

seq(coffeeSettings: _*)

(resourceManaged in (Compile, CoffeeKeys.coffee)) <<= (webappResources in Compile)(_.get.head / "js")

Compass.settings



javaOptions +=  "-verbosegc"

javaOptions +=  "-XX:+PrintGCDetails"

javaOptions += "-verbosegc"

javaOptions += "-Xloggc:gc.log"


