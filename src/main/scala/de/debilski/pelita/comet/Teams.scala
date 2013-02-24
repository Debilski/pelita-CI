package de.debilski.pelita
package comet


import java.util.Date
import scala.xml._
import net.liftweb._
import http._
import net.liftweb.actor._
import net.liftweb.common.{Box, Full}
import net.liftweb.util._
import net.liftweb.util.Helpers._
import net.liftweb.http.js.JsCmds.{SetHtml}

class Teams extends CometActor {
  override def defaultPrefix = Full("tms")

  def render = bind("teams" -> loadingMsg)

  def loadingMsg = (<span id="allteamslist">Loading teams…</span>)

  Schedule.schedule(this, "requestNew", 1000L)

  override def lowPriority : PartialFunction[Any, Unit] = {
    case "requestNew" => lib.CI.requestTeams(this)
    case lib.CI.TeamsList(teams) => {
      val html = <ul>{ for (t <- teams) yield <li>{t.toString}</li> }</ul>

      partialUpdate(SetHtml("allteamslist", html))
      // schedule an update in 10 seconds
      Schedule.schedule(this, "requestNew", 10000L)
    }
  }
}
