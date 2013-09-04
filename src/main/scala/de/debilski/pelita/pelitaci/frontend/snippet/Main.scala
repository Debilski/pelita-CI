package de.debilski.pelita.pelitaci.frontend
package snippet


import scala.xml.NodeSeq

import net.liftweb.util.Helpers._
import net.liftweb.http.SHtml
import net.liftweb.http.js.{JsCmd, JsCmds}
import net.liftweb.common.Loggable


class GitRevision {
  def currentRevision = try {
    sys.process.Process( Seq( "git", "describe" ), new java.io.File( "/opt/player" )).!!
  } catch {
    case e: java.io.IOException => ""
  }
  def gitPull = {
    sys.process.Process( Seq( "git", "describe" ), new java.io.File( "/opt/player" )).!!
    JsCmds.Noop
  }

  def button = "button [onclick]" #> SHtml.ajaxInvoke(gitPull _)
}

