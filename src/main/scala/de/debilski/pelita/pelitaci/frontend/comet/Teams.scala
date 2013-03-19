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

  def renderTeam(id: Int, team: Team, score: Option[Double]) = {
    val location = if (!team.url.isEmpty) s"${team.factory} at ${team.url}" else team.factory
    val scoreFormatted = score.map(s => f"$s%.1f pts") getOrElse "–"
    <li><b class="score">{scoreFormatted}</b> <b class="teamname" title={location + " " + statistics(id)}>{team.name getOrElse <i>unknown name</i>}</b></li>
  }

  def statistics(teamId: Int) = {
    val (totalStats, completeStats) = lib.CI.statisticsAgent()
    val last20Stats = (completeStats(teamId) takeRight 20).reverse

    def toSmiley(i: Int) = i match {
      case -1 => "●"
      case 0 => "◐"
      case 1 => "○"
      case _ => ""
    }

    totalStats(teamId).toString + last20Stats.map(toSmiley).mkString("[", "", "]")
  }

  ping(10000L)

  def loadingMsg = <span id="allteamslist">{
      _teams.getOrElse(Text("Loading teams…"))
    }</span>

  override def lowPriority : PartialFunction[Any, Unit] = {
    case Ping => ping(10000L)
    case RequestNew => lib.CI.requestTeams(this)
    case lib.CI.TeamsList(teams) => {
      _teams = Some(<ul>{ for ((id, team, score) <- teams) yield renderTeam(id, team, score) }</ul>)

      _teams.map(html => partialUpdate(SetHtml("allteamslist", html)))
    }
  }
}
