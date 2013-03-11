package de.debilski.pelita.pelitaci.frontend
package rest

import net.liftweb.http._
import net.liftweb.http.rest._
import net.liftweb.util.Helpers._
import net.liftweb.http.LiftRules
import net.liftweb.json.JsonAST._
import scala.concurrent.ExecutionContext.Implicits.global
import concurrent.{Promise, Future}

import akka.pattern.ask

import de.debilski.pelita.pelitaci.backend.{QueuedMatch, PlayGame, AddTeam}
import net.liftweb.util.Schedule


object Rest extends RestHelper {
  implicit class Team2Json(t: de.debilski.pelita.pelitaci.backend.database.Team) {
    def toTeam = de.debilski.pelita.pelitaci.backend.Team(t.uri, t.factory)

    import net.liftweb.json.JsonDSL._
    def toJson: net.liftweb.json.JValue = ("id" -> t.id) ~ ("uri" -> t.uri) ~ ("factory" -> t.factory) ~ ("name" -> t.name)
  }

  def init() : Unit = {
    LiftRules.statelessDispatch.append(Rest)
  }

  def addTeam(json: net.liftweb.json.JsonAST.JValue): Future[JValue] = {
    Future {
      val JString(uri) = json \ "uri"
      val JString(factory) = json \ "factory"
      (uri, factory)
    } flatMap {
      case (uri, factory) => {
        import de.debilski.pelita.pelitaci.backend.PelitaInterface._
        val runner = new Runner { type GameType = ShortGame; val game = new ShortGame{} }
        val team = de.debilski.pelita.pelitaci.backend.Team(uri, factory)

        val res = runner.checkTeamName(team).unsafePerformIO() match {
          case Some(teamName) => Future successful teamName
          case None => Future failed (new Throwable)
        }
        res map (teamName => (uri, factory, teamName))
      }
    } flatMap {
      case (uri, factory, teamName) => {
        lib.CI.db.addTeam(uri, factory, Some(teamName)).map{ id =>
          de.debilski.pelita.pelitaci.backend.database.Team(Some(id), uri, factory, Some(teamName))
        }
      }
    } map {
      case team: de.debilski.pelita.pelitaci.backend.database.Team =>
        team.toJson
    }
  }

  def getTeam(teamId: Int): Future[JValue] = {
    val force = S.param("force").map(_ == "true").getOrElse(false)
    if (force) {
      for {
        team <- lib.CI.db.getTeam(teamId)
        teamJson <- addTeam(team.toJson)
      } yield teamJson
    } else {
      for {
        team <- lib.CI.db.getTeam(teamId)
      } yield team.toJson
    }
  }

  def scheduleMatch(json: net.liftweb.json.JsonAST.JValue): Future[JValue] = {
    val teamIds = Future {
      val JArray(ids) = json
      ids collect {
        case JInt(id) => id.toInt
      } match {
        case (id1: Int) :: (id2: Int) :: Nil => Some((id1, id2))
        case _ => None
      }
    }

    import akka.util.Timeout
    import scala.concurrent.duration.Duration
    import net.liftweb.json.JsonDSL._

    implicit val timeout = Timeout(5000L)

    for {
      Some((id1, id2)) <- teamIds
      team1 <- lib.CI.db.getTeam(id1)
      team2 <- lib.CI.db.getTeam(id2)
      res <- ask(lib.CI.controller, PlayGame(team1.toTeam, team2.toTeam)).mapTo[QueuedMatch]
    } yield ("uuid" -> res.uuid.map(_.toString)) ~ ("queueTime" -> res.queueTime.map(_.toString)) ~ ("teamA" -> team1.toJson) ~ ("teamB" -> team2.toJson)
  }


  def asyncFuture[T <% net.liftweb.http.LiftResponse](timeout: Int)(fut: Future[T]) = RestContinuation.async { f => {
    val timedOut = Schedule.schedule(() => f(JNull), timeout.seconds)
    def finish(v: net.liftweb.http.LiftResponse) { timedOut.cancel(false); f(v) }

    fut onComplete {
      case scala.util.Success(v) => finish(v)
      case scala.util.Failure(e) => finish(JString(e.toString))
    }
  }
  }

  serve("rest" :: Nil prefix {
    case "team" :: "add" :: Nil JsonPost json -> _ => asyncFuture(60)(addTeam(json))
    case "team" :: AsInt(teamId) :: Nil JsonGet _ => asyncFuture(25)(getTeam(teamId))
    case "match" :: "schedule" :: Nil JsonPost json -> _ => asyncFuture(25)(scheduleMatch(json))
  })
}
