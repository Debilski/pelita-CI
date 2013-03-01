package de.debilski.pelita.pelitaci.backend

import akka.actor.{ Actor, ActorLogging, ActorRef }
import akka.actor.Props
import akka.event.{ActorEventBus, LookupClassification}

sealed trait ControllerMessage
case class AddTeam(uri: String, fragment: String) extends ControllerMessage
case class PlayGame(team1: Team, team2: Team) extends ControllerMessage

class GameBalancer extends utils.workbalancer.Master {
  def receiveWork = {
    case c @ (PlayGame(a, b), logger: MessageBus) ⇒ doWork(c)
  }
}

class Controller extends Actor with ActorLogging {
  val balancer = context.actorOf(Props[GameBalancer], name = "gamebalancer")
  def worker(controller: String, subscriber: String) = context.actorOf(Props(new PelitaInterface.Worker(balancer.path)(controller, subscriber)))

  val basePort = 51100
  val numWorkers = 2

  val eventBus = new MessageBus

  val workers = (0 until numWorkers).map { i ⇒
    worker(s"tcp://127.0.0.1:${basePort + 2 * i}", s"tcp://127.0.0.1:${basePort + 2 * i + 1}")
  }

  def receive = {
    case SubscribeMatch(subscriber: ActorRef, to: Int) => eventBus.subscribe(subscriber, Some(to))
    case SubscribeGlobal(subscriber: ActorRef) => eventBus.subscribe(subscriber, None)
    case UnsubscribeMatch(subscriber: ActorRef, from: Int) => eventBus.unsubscribe(subscriber, Some(from))
    case UnsubscribeGlobal(subscriber: ActorRef) => eventBus.unsubscribe(subscriber, None)
    case UnsubscribeAll(subscriber: ActorRef) => eventBus.unsubscribe(subscriber)

    case c @ PlayGame(a, b) ⇒ {
      balancer.!((c, eventBus))(sender)
    }
    case other ⇒ log.info(s"received unknown message: $other")
  }
}

sealed trait MessageEvent { def msg: Any }
case class GlobalChange(msg: Any) extends MessageEvent
case class MatchDetailChange(matchId: Int, msg: Any) extends MessageEvent

sealed trait SubscriptionMsg
case class SubscribeMatch(subscriber: ActorRef, to: Int)
case class SubscribeGlobal(subscriber: ActorRef)
case class UnsubscribeMatch(subscriber: ActorRef, from: Int)
case class UnsubscribeGlobal(subscriber: ActorRef)
case class UnsubscribeAll(subscriber: ActorRef)

class MessageBus extends ActorEventBus with LookupClassification {
  type Event = MessageEvent
  type Classifier = Option[Int]

  protected def mapSize = 10

  protected def classify(event: Event): Classifier = {
    event match {
      case e: GlobalChange => None
      case MatchDetailChange(id, msg) => Some(id)
    }
  }

  protected def publish(event: Event, subscriber: Subscriber): Unit = {
    subscriber ! event.msg
  }

  def publishGlobal(msg: Any) = this publish GlobalChange(msg)
  def publishMatch(matchId: Int, msg: Any) = this publish MatchDetailChange(matchId, msg)
}
