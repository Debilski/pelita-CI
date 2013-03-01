package de.debilski.pelita.pelitaci.frontend
package lib

import scala.concurrent.ExecutionContext.Implicits.global
import net.liftweb.http.CometActor
import de.debilski.pelita.pelitaci.backend.SubscribeGlobal

object CI {
  case class TeamsList(teams: Seq[de.debilski.pelita.pelitaci.backend.Team])

  def requestTeams(actor: CometActor) = {
    db.getTeams map { teams =>
      actor send TeamsList(teams.map(team => de.debilski.pelita.pelitaci.backend.Team(team.uri, team.factory)))
    }
  }

  val actorSystem = akka.actor.ActorSystem("Pelita-CI")
  val controller = actorSystem.actorOf(akka.actor.Props[de.debilski.pelita.pelitaci.backend.Controller])
  val db = de.debilski.pelita.pelitaci.backend.database.DBController.createActor(actorSystem)("jdbc:h2:pelita.db")
  db.createDB()

  val listener = actorSystem.actorOf(akka.actor.Props(new akka.actor.Actor {
    def receive = {
      case msg => println(s"---> $msg")
    }
  }))
  controller ! SubscribeGlobal(listener)
}
