import sbt._
import Keys._
import sbt.Process._

object Compass extends Plugin {
  override lazy val settings = Seq(commands += compassCompile)

  lazy val compassCompile =
    Command.command("compass-compile") { (state: State) =>
      val cmd = "bundle" :: "exec" :: "compass" :: "compile" :: Nil
      cmd.!!
      state
    }

}
