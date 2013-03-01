package de.debilski.pelita.pelitaci.backend

import akka.actor.{ Actor, ActorLogging, ActorRef }
import akka.actor.Props
import java.util.UUID

sealed trait ControllerMessage
case class AddTeam(uri: String, fragment: String) extends ControllerMessage
case class PlayGame(team1: Team, team2: Team) extends ControllerMessage
case class PlayMatch(queuedMatch: QueuedMatch) extends ControllerMessage
case class QueuedMatch(uuid: Option[UUID], teamA: Team, teamB: Team, queueTime: Option[java.util.Date], resultTime: Option[java.util.Date])

class GameBalancer extends utils.workbalancer.Master {
  def receiveWork = {
    case c @ (queuedMatch: QueuedMatch, logger: MessageBus) ⇒ doWork(c)
  }
}

class Controller extends Actor with ActorLogging {
  val balancer = context.actorOf(Props[GameBalancer], name = "gamebalancer")
  def worker(controller: String, subscriber: String) = context.actorOf(Props(new PelitaInterface.Worker(balancer.path)(controller, subscriber)))

  def basePort = 51100
  def numWorkers = 2

  val eventBus = new MessageBus

  val workers = (0 until numWorkers).map { i ⇒
    worker(s"tcp://127.0.0.1:${basePort + 2 * i}", s"tcp://127.0.0.1:${basePort + 2 * i + 1}")
  }

  def receive = {
    case msg: SubscriptionChangeMsg => eventBus.receiveSubscriptionChange(msg)

    case c @ PlayGame(a, b) ⇒ {
      val queuedMatch = QueuedMatch(Option(java.util.UUID.randomUUID), a, b, Option(new java.util.Date), None)
      balancer.!((queuedMatch, eventBus))(sender)
      sender ! queuedMatch
    }
    case other ⇒ log.info(s"received unknown message: $other")
  }
}
