package de.debilski.pelita.pelitaci.frontend
package comet

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

  private[this] var _teams: Option[scala.xml.NodeSeq] = None
  private case object RequestNew
  private case object Ping
  private[this] def ping(time: Long) {
    this send RequestNew
    Schedule.schedule(this, Ping, time)
  }

  def render = {
    bind("teams" -> loadingMsg)
  }

  ping(10000L)

  def loadingMsg = <span id="allteamslist">{
      _teams.getOrElse(Text("Loading teams…"))
    }</span>

  override def lowPriority : PartialFunction[Any, Unit] = {
    case Ping => ping(10000L)
    case RequestNew => lib.CI.requestTeams(this)
    case lib.CI.TeamsList(teams) => {
      _teams = Some(<ul>{ for (t <- teams) yield <li>{t.toString}</li> }</ul>)

      _teams.map(html => partialUpdate(SetHtml("allteamslist", html)))
    }
  }
}
