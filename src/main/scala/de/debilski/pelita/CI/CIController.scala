package de.debilski.pelita.CI

import akka.actor.{ Actor, ActorSystem }
import akka.actor.Props
import akka.event.Logging
import akka.actor.PoisonPill

import PelitaInterface.GameController

sealed trait ControllerMessage
case class AddTeam(team: Team) extends ControllerMessage
case class PlayGame(team1: Team, team2: Team) extends ControllerMessage

class Controller extends Actor {
  val basePort = 51100
  val gameQueue = context.actorOf(Props(new GameController(s"tcp://127.0.0.1:$basePort", s"tcp://127.0.0.1:${basePort+1}")).withDispatcher("akka.actor.game-controller"))
  val gameQueueAdditional = (1 to 5).map(i => context.actorOf(Props(new GameController(s"tcp://127.0.0.1:${basePort + 2*i}", s"tcp://127.0.0.1:${basePort + 2*i + 1}")).withDispatcher("akka.actor.game-controller")))
  
  val log = Logging(context.system, this)
  def receive = {
    case c@PlayGame(a, b) => gameQueue.!(c)(sender)
    case _      ⇒ log.info("received unknown message")
  }
}


object Main extends App {
  import akka.actor.ActorSystem
  val system = ActorSystem("MySystem")
  val myActor = system.actorOf(Props[Controller], name="controller")
  
  
  val team1 = Team(uri=new java.net.URI("/Volumes/Data/Projects/Python-School/players#master"), "rike:factory")
  val team2 = Team(uri=new java.net.URI("/Volumes/Data/Projects/Python-School/players#master"), "rike:factory")
  
  import akka.pattern.ask
  import scala.concurrent.duration._
  1 to 2 foreach { _ =>
    val fut = ask(myActor, PlayGame(team1, team2))(20.seconds)
    import scala.concurrent.ExecutionContext.Implicits.global
    fut foreach(j => println(j))
  }
  myActor ! "..."
  //myActor ! akka.actor.PoisonPill
  
  
  import akka.pattern.gracefulStop
  import scala.concurrent.Await
  import scala.concurrent.Future
  import scala.concurrent.duration._
 
  try {
    val stopped: Future[Boolean] = gracefulStop(myActor, 15.seconds)(system)
    Await.result(stopped, 16.seconds)
    // the actor has been stopped
  } catch {
    // the actor wasn't stopped within 5 seconds
    case e: akka.pattern.AskTimeoutException ⇒
  }
  system.shutdown
  println("...")
}
