package de.debilski.pelita.CI

import akka.actor.{ Actor, ActorLogging }
import akka.actor.Props

sealed trait ControllerMessage
case class AddTeam(team: Team) extends ControllerMessage
case class PlayGame(team1: Team, team2: Team) extends ControllerMessage

class GameBalancer extends utils.workbalancer.Master {
  def receiveWork = {
    case c @ PlayGame(a, b) ⇒ doWork(c)
  }
}

class Controller extends Actor with ActorLogging {
  val balancer = context.actorOf(Props[GameBalancer], name = "gamebalancer")
  def worker(controller: String, subscriber: String) = context.actorOf(Props(new PelitaInterface.Worker(balancer.path)(controller, subscriber)))

  val basePort = 51100
  val numWorkers = 2

  val workers = (0 until numWorkers).map { i ⇒
    worker(s"tcp://127.0.0.1:${basePort + 2 * i}", s"tcp://127.0.0.1:${basePort + 2 * i + 1}")
  }

  def receive = {
    case c @ PlayGame(a, b) ⇒ balancer.!(c)(sender)
    case other ⇒ log.info(s"received unknown message: $other")
  }
}
