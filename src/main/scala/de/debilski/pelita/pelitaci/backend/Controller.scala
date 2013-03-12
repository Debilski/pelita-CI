package de.debilski.pelita.pelitaci.backend

import akka.actor._
import java.util.UUID

sealed trait ControllerMessage
case class AddTeam(url: String, fragment: String) extends ControllerMessage
case class PlayGame(team1: Team, team2: Team) extends ControllerMessage
case class PlayMatch(queuedMatch: QueuedMatch) extends ControllerMessage
case class QueuedMatch(uuid: Option[UUID], teamA: Team, teamB: Team, queueTime: Option[java.util.Date], resultTime: Option[java.util.Date])

class GameBalancer extends utils.workbalancer.Master {
  def receiveWork = {
    case c @ (queuedMatch: QueuedMatch, logger: MessageBus) ⇒ doWork(c)
  }
}

class DefaultWorkerFactory(context: ActorRefFactory, balancer: ActorPath) {
  def worker(controller: String, subscriber: String) = context.actorOf(Props(new PelitaInterface.Worker(balancer)(controller, subscriber)))
}

class Controller extends Actor with ActorLogging {
  val balancer = context.actorOf(Props[GameBalancer], name = "gamebalancer")

  val eventBus = new MessageBus

  def receive = {
    case msg: SubscriptionChangeMsg => eventBus.receiveSubscriptionChange(msg)

    case c @ PlayGame(a, b) ⇒ {
      val queuedMatch = QueuedMatch(Option(java.util.UUID.randomUUID), a, b, Option(new java.util.Date), None)
      balancer.!((queuedMatch, eventBus))(sender)
      sender ! queuedMatch
    }
    case nwc :utils.workbalancer.InfoMessages.NumWorkersChanged => eventBus.publishGlobal(nwc)
    case qsc :utils.workbalancer.InfoMessages.QueueSizeChanged => eventBus.publishGlobal(qsc)
    case other ⇒ log.info(s"received unknown message: $other")
  }
}
