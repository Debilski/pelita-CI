organization := "de.debilski.pelita"

name := "Pelita CI"

version := "0.2-SNAPSHOT"

scalaVersion := "2.11.4"

jetty()

libraryDependencies ++= {
  val liftVersion = "2.6-RC1"
  Seq(
    "net.liftweb" %% "lift-webkit" % liftVersion % "compile",
    "org.eclipse.jetty" % "jetty-webapp" % "9.2.3.v20140905" % "container,test",
    "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "container,compile" artifacts Artifact("javax.servlet", "jar", "jar")
  )
}

resolvers += "Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/"

libraryDependencies ++= {
  val scalazVersion = "7.1.0"
  Seq(
    "org.scalaz" %% "scalaz-core" % scalazVersion,
    "org.scalaz" %% "scalaz-effect" % scalazVersion
  )
}

resolvers += "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"

libraryDependencies += "org.scalaz.stream" %% "scalaz-stream" % "0.5a"

libraryDependencies ++= {
  val akkaVersion = "2.3.6"
  Seq(
  	"com.typesafe.akka" %% "akka-actor" % akkaVersion,
  	"com.typesafe.akka" %% "akka-agent" % akkaVersion,
//  	"com.typesafe.akka" %% "akka-zeromq" % akkaVersion,
  	"com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  	"com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  	"com.typesafe.akka" %% "akka-dataflow" % akkaVersion
  )
}

libraryDependencies += "org.zeromq" % "jzmq" % "3.1.0"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.1" % "test"

libraryDependencies += "org.specs2" %% "specs2" % "2.4.9" % "test"

scalacOptions in Test ++= Seq("-Yrangepos")

autoCompilerPlugins := true

// libraryDependencies <+= scalaVersion {
//  v => compilerPlugin("org.scala-lang.plugins" % "continuations" % v)
// }

// scalacOptions += "-P:continuations:enable"

scalacOptions += "-deprecation"

scalacOptions += "-feature"

scalacOptions += "-Xlint"

libraryDependencies ++= List(
  // use the right Slick version here:
  "com.typesafe.slick" %% "slick" % "2.1.0",
  "com.h2database" % "h2" % "1.4.182"
)

libraryDependencies += "com.typesafe" % "config" % "1.0.0"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.0.11" % "runtime"

libraryDependencies += "org.codehaus.janino" % "janino" % "2.6.1"

seq(coffeeSettings: _*)

// (resourceManaged in (Compile, CoffeeKeys.coffee)) <<= (webappSrc in Compile)(_.get.head / "js")

Compass.settings

javaOptions +=  "-verbosegc"

javaOptions +=  "-XX:+PrintGCDetails"

javaOptions += "-verbosegc"

javaOptions += "-Xloggc:gc.log"


