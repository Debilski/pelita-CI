package de.debilski.pelita.pelitaci.frontend
package rest

import net.liftweb.http._
import net.liftweb.http.rest._
import net.liftweb.util.Helpers._
import net.liftweb.http.LiftRules
import net.liftweb.json.JsonAST._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import akka.pattern.ask

import de.debilski.pelita.pelitaci.backend.{QueuedMatch, PlayGame, AddTeam}
import net.liftweb.util.Schedule


object Rest extends RestHelper {

  implicit def t2t(t: de.debilski.pelita.pelitaci.backend.database.Team): de.debilski.pelita.pelitaci.backend.Team = {
    de.debilski.pelita.pelitaci.backend.Team(t.uri, t.factory)
  }

  def init() : Unit = {
    LiftRules.statelessDispatch.append(Rest)
  }

  def addTeam(json: net.liftweb.json.JsonAST.JValue): Future[JValue] = {
    Future {
      val JString(uri) = json \ "uri"
      val JString(fragment) = json \ "fragment"
      (uri, fragment)
    } flatMap {
      case (uri, fragment) => lib.CI.db.addTeam(uri, fragment)
    } map {
      id => JField("id", JInt(id))
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
      res <- ask(lib.CI.controller, PlayGame(team1, team2)).mapTo[QueuedMatch]
    } yield ("uuid" -> res.uuid.map(_.toString)) ~ ("queueTime" -> res.queueTime.map(_.toString))
  }


  def asyncFuture[T <% net.liftweb.http.LiftResponse](timeout: Int)(fut: Future[T]) = RestContinuation.async { f => {
    Schedule.schedule(() => f(JNull), timeout.seconds)

    fut andThen {
      case scala.util.Success(v) => f(v)
      case scala.util.Failure(e) => f(JString(e.toString))
    }
  }
  }

  serve("rest" :: Nil prefix {
    case "team" :: "add" :: Nil JsonPost json -> _ => asyncFuture(25)(addTeam(json))
    case "match" :: "schedule" :: Nil JsonPost json -> _ => asyncFuture(25)(scheduleMatch(json))
  })
}
