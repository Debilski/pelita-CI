package de.debilski.pelita.pelitaci.frontend
package lib

import scala.concurrent.ExecutionContext.Implicits.global
import net.liftweb.http.CometActor
import de.debilski.pelita.pelitaci.backend.{Controller, DefaultWorkerFactory}

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
    db.getTeams() map { teams =>
      actor send TeamsList(teams.map(team => de.debilski.pelita.pelitaci.backend.Team(team.url, team.factory, team.name)))
    }
  }

  def createBridgeActor(actor: CometActor) = actorSystem.actorOf(akka.actor.Props(new BridgeActor(actor)), name=s"bridge-actor-${actor.##}")

  val actorSystem = akka.actor.ActorSystem("Pelita-CI")
  val controller = actorSystem.actorOf(akka.actor.Props[Controller])


  def basePort = 51100
  def numWorkers = 2

  val workerFactory = new DefaultWorkerFactory(actorSystem, controller.path.child("gamebalancer"))
  val workers = (0 until numWorkers).map { i ⇒
    workerFactory.worker(s"tcp://127.0.0.1:${basePort + 2 * i}", s"tcp://127.0.0.1:${basePort + 2 * i + 1}")
  }

  val db = de.debilski.pelita.pelitaci.backend.database.DBController.createActor(actorSystem)("jdbc:h2:pelita.db")
  db.createDB()
}
