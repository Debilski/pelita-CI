package de.debilski.pelita.pelitaci.frontend
package lib

import scala.concurrent.ExecutionContext.Implicits.global
import net.liftweb.http.CometActor

class BridgeActor(cometActor: CometActor) extends akka.actor.Actor with akka.actor.ActorLogging {
  override def preStart() = log.info(s"Created BridgeActor for $cometActor")

  override def postStop() = log.info(s"Stopped BridgeActor for $cometActor")

  def receive = {
    case msg => cometActor ! msg
  }
}

object CI {
  case class TeamsList(teams: Seq[de.debilski.pelita.pelitaci.backend.Team])

  def requestTeams(actor: CometActor) = {
    db.getTeams map { teams =>
      actor send TeamsList(teams.map(team => de.debilski.pelita.pelitaci.backend.Team(team.uri, team.factory)))
    }
  }

  def createBridgeActor(actor: CometActor) = actorSystem.actorOf(akka.actor.Props(new BridgeActor(actor)), name=s"bridge-actor-${actor.##}")

  val actorSystem = akka.actor.ActorSystem("Pelita-CI")
  val controller = actorSystem.actorOf(akka.actor.Props[de.debilski.pelita.pelitaci.backend.Controller])
  val db = de.debilski.pelita.pelitaci.backend.database.DBController.createActor(actorSystem)("jdbc:h2:pelita.db")
  db.createDB()
}
