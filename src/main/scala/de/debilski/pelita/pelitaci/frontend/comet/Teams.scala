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
import de.debilski.pelita.pelitaci.backend.Team

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

  def renderTeam(team: Team, score: Option[Double]) = {
    val location = if (!team.url.isEmpty) s"${team.factory} at ${team.url}" else team.factory
    val scoreFormatted = score.map(s => f"$s%.1f pts") getOrElse "–"
    <li><b class="score">{scoreFormatted}</b> <b class="teamname" title={location}>{team.name getOrElse <i>unknown name</i>}</b></li>
  }

  ping(10000L)

  def loadingMsg = <span id="allteamslist">{
      _teams.getOrElse(Text("Loading teams…"))
    }</span>

  override def lowPriority : PartialFunction[Any, Unit] = {
    case Ping => ping(10000L)
    case RequestNew => lib.CI.requestTeams(this)
    case lib.CI.TeamsList(teams) => {
      _teams = Some(<ul>{ for ((team, score) <- teams) yield renderTeam(team, score) }</ul>)

      _teams.map(html => partialUpdate(SetHtml("allteamslist", html)))
    }
  }
}
