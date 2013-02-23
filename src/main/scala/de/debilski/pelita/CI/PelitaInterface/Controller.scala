package de.debilski.pelita.CI
package PelitaInterface

import akka.actor.{ Actor, ActorRef, ActorSystem, Props }
import akka.event.Logging
import akka.zeromq.ZMQMessage
import com.google.protobuf.ByteString

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
  def set_initial = ???
  def observe = ???
  def exit = ???
  
  import net.liftweb.json._
  
  def receive(s: String) = {
    val json = parse(s)
    println(json \ "__action__")
  }
}

class SimpleController(ctrlSocket: akka.actor.ActorRef) extends SimpleControllerInterface {
  import akka.zeromq._
  private[this] def ship(msg: String) = {
    println(s"shipping $msg")
    ctrlSocket ! ZMQMessage(akka.util.ByteString(msg).toArray)
  }
  private[this] def shipAction(action: String) = ship(s""" {"__action__": "$action"} """)
  
  def set_initial = shipAction("set_initial")
  def play = shipAction("play")
  def play_round = shipAction("play_round")
  def play_step = shipAction("play_step")
  def update_viewers = shipAction("update_viewers")
  def exit = shipAction("exit")
}

class ZMQPelitaController(val controller: String, val subscriber: String) extends Actor {
  import akka.zeromq._
  // ctrlListener does not receive anything but we’ll have it anyway
  val ctrlListener = context.actorOf(Props(new Actor {
    def receive: Receive = {
      case c@Connecting    ⇒ Logging(context.system, this).info(c.toString)
      case m: ZMQMessage ⇒ SimpleSubscriber.receive(akka.util.ByteString(m.frames(0).payload.toArray).utf8String.toString)// Logging(context.system, this).info(akka.util.ByteString(m.frames(0).payload.toArray).utf8String.toString)
      case _             ⇒ //...
    } 
  }))
  
  val subListener = context.actorOf(Props(new Actor {
    def receive: Receive = {
      case c@Connecting    ⇒ Logging(context.system, this).info(c.toString)
      case m: ZMQMessage ⇒ SimpleSubscriber.receive(akka.util.ByteString(m.frames(0).payload.toArray).utf8String.toString)// Logging(context.system, this).info(akka.util.ByteString(m.frames(0).payload.toArray).utf8String.toString)
      case _             ⇒ //...
    }
  }))
  println(s"using ports $controller, $subscriber")
  val ctrlSocket = ZeroMQExtension(context.system).newSocket(SocketType.Dealer, Listener(ctrlListener), Connect(controller))
  val subSocket = ZeroMQExtension(context.system).newSocket(SocketType.Sub, Listener(subListener), Connect(subscriber), SubscribeAll)
  
  val controllerSender = new SimpleController(ctrlSocket)
  
  def receive = {
    case "play" => { Thread.sleep(1000); controllerSender.play }
  }
}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.future

class GameController(controller: String, subscriber: String) extends Actor {
  val log = Logging(context.system, this)
  
  object TestRunner extends Runner {
    type GameType = ShortGame
    val game = new ShortGame{}
  }
  
  def receive = {
    case PlayGame(a, b) => {
      val c = context.actorOf(Props(new ZMQPelitaController(controller, subscriber)))
      c ! "play"
      val result = TestRunner.playGame(Pairing(a, b), controller=Some(controller), subscriber=Some(subscriber)).unsafePerformIO
      sender ! result
    }
  }
}

