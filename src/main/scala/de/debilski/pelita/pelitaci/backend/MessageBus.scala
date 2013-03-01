package de.debilski.pelita.pelitaci.backend

import akka.actor.ActorRef
import akka.event.{LookupClassification, ActorEventBus}

sealed trait MessageEvent { def msg: Any }
case class GlobalChange(msg: Any) extends MessageEvent
case class MatchDetailChange(matchId: Int, msg: Any) extends MessageEvent

sealed trait SubscriptionChangeMsg
case class SubscribeMatch(subscriber: ActorRef, to: Int) extends SubscriptionChangeMsg
case class SubscribeGlobal(subscriber: ActorRef) extends SubscriptionChangeMsg
case class UnsubscribeMatch(subscriber: ActorRef, from: Int) extends SubscriptionChangeMsg
case class UnsubscribeGlobal(subscriber: ActorRef) extends SubscriptionChangeMsg
case class UnsubscribeAll(subscriber: ActorRef) extends SubscriptionChangeMsg

class MessageBus extends ActorEventBus with LookupClassification {
  type Event = MessageEvent
  type Classifier = Option[Int]

  protected def mapSize = 10

  def receiveSubscriptionChange(msg: SubscriptionChangeMsg) = msg match {
    case SubscribeMatch(subscriber: ActorRef, to: Int) => this.subscribe(subscriber, Some(to))
    case SubscribeGlobal(subscriber: ActorRef) => this.subscribe(subscriber, None)
    case UnsubscribeMatch(subscriber: ActorRef, from: Int) => this.unsubscribe(subscriber, Some(from))
    case UnsubscribeGlobal(subscriber: ActorRef) => this.unsubscribe(subscriber, None)
    case UnsubscribeAll(subscriber: ActorRef) => this.unsubscribe(subscriber)
  }

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
