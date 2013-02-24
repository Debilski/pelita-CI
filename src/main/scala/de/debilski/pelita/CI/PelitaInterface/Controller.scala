package de.debilski.pelita.CI
package PelitaInterface

import scala.concurrent.Future
import akka.actor.{ Actor, ActorRef, ActorSystem, Props, ActorLogging, ActorPath }
import akka.pattern.pipe
import akka.event.Logging

trait SimpleControllerInterface {
  def set_initial
  def play
  def play_round
  def play_step
  def update_viewers
  def exit
}

trait SimpleSubscriberInterface {
  def set_initial
  def observe
  def exit
}

object SimpleSubscriber extends SimpleSubscriberInterface {
  def set_initial = {}
  def observe = {}
  def exit = {}
  
  import net.liftweb.json._

  def set_initial(data: JObject) = () // println(data)
  def observe(data: JObject) = () // println(data)
  
  def receive(s: String) = {
    parseOpt(s) map { json =>
      (json \ "__action__", json \ "__data__") match {
        case (JString("set_initial"), data: JObject) => set_initial(data)
        case (JString("observe"), data: JObject) => observe(data)
        case (JString("exit"), other) => exit
        case _ => // log.info("No match for json string.")
      }
    } // getOrElse log.info("Could not parse json string.")
  }
}

class SimpleController(ctrlSocket: akka.actor.ActorRef) extends SimpleControllerInterface {
  import akka.zeromq._
  private[this] def ship(msg: String) = {
    println(s"shipping $msg")
    ctrlSocket ! ZMQMessage(akka.util.ByteString(msg).toArray)
  }
  private[this] def shipAction(action: String) = ship(s"""{"__action__": "$action"}""")
  
  def set_initial = shipAction("set_initial")
  def play = shipAction("play")
  def play_round = shipAction("play_round")
  def play_step = shipAction("play_step")
  def update_viewers = shipAction("update_viewers")
  def exit = shipAction("exit")
}

class ZMQPelitaController(val controller: String, val subscriber: String) extends Actor {
  import akka.zeromq._
  // ctrlListener should not receive anything but we’ll have it anyway
  val ctrlListener = context.actorOf(Props(new Actor {
    def receive: Receive = {
      case c@Connecting  ⇒ Logging(context.system, this).info(c.toString)
      case m: ZMQMessage ⇒ Logging(context.system, this).info(akka.util.ByteString(m.frames(0).payload.toArray).utf8String.toString)
      case _             ⇒ //...
    } 
  }))
  
  val subListener = context.actorOf(Props(new Actor {
    def receive: Receive = {
      case c@Connecting  ⇒ Logging(context.system, this).info(c.toString)
      case m: ZMQMessage ⇒ SimpleSubscriber.receive(akka.util.ByteString(m.frames(0).payload.toArray).utf8String.toString)
      case _             ⇒ //...
    }
  }))
  println(s"using ports $controller, $subscriber")
  val ctrlSocket = ZeroMQExtension(context.system).newSocket(SocketType.Dealer, Listener(ctrlListener), Connect(controller))
  val subSocket = ZeroMQExtension(context.system).newSocket(SocketType.Sub, Listener(subListener), Connect(subscriber), SubscribeAll)
  
  val controllerSender = new SimpleController(ctrlSocket)
  
  def receive = {
    case "play" => { Thread.sleep(1000); controllerSender.play }
    case "exit" => { controllerSender.exit }
  }
}


class Worker(masterLocation: ActorPath)(val controller: String, val subscriber: String) extends utils.workbalancer.Worker(masterLocation) {
  // We'll use the current dispatcher for the execution context.
  // You can use whatever you want.
  implicit val ec = context.dispatcher

  object TestRunner extends Runner {
    type GameType = ShortGame
    val game = new ShortGame{}
  }
 
  def doWork(workSender: ActorRef, msg: Any): Unit = {
    log.info(s"Trying to work on $msg")
    
    Future {
      msg match {
        case PlayGame(a, b) =>
          val c = context.actorOf(Props(new ZMQPelitaController(controller, subscriber)))
          c ! "play"
          c ! "exit"
          val result = TestRunner.playGame(Pairing(a, b), controller=Some(controller), subscriber=Some(subscriber)).unsafePerformIO
          workSender ! result
      }
      WorkComplete("done")
    } pipeTo self
  }
}

