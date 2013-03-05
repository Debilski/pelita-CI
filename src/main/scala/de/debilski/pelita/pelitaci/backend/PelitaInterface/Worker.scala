package de.debilski.pelita.pelitaci.backend
package PelitaInterface

import scala.concurrent.Future
import akka.actor._
import akka.pattern.pipe
import akka.event.Logging
import java.util.UUID

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

case class PelitaTeam(
    name: String,
    score: Int,
    timeout_team: Int,
    team_time: Double,
    food_count: Int,
    food_to_eat: Int)

case class PelitaGameState(
    teams: (PelitaTeam, PelitaTeam),
    bot_id: Option[Int],
    round_index: Option[Int],
    running_time: Double,
    finished: Boolean,
    team_wins: Option[Int],
    game_draw: Option[Boolean],
    game_time: Int,
    timeout_length: Double,
    max_timeouts: Int,
    layout_name: String)

case class PelitaTeamMinimal(
    name: String,
    score: Int)

case class PelitaMatchMinimal(
    teamA: PelitaTeamMinimal,
    teamB: PelitaTeamMinimal,
    winner: Option[MatchResultCode])

object SimpleSubscriber extends SimpleSubscriberInterface {
  def set_initial = {}
  def observe = {}
  def exit = {}
  
  import net.liftweb.json._

  def set_initial(data: JObject) = extractMinimal(data)
  def observe(data: JObject) = extractMinimal(data)

  def extractMinimal(data: JObject): Option[PelitaMatchMinimal] = {
    val JArray(teams) = data \ "universe" \ "__value__" \ "teams"
    val names = for {
      JString(name) <- teams.map(_ \ "__value__" \ "name")
    } yield name
    val score = for {
      JInt(score) <- teams.map(_ \ "__value__" \ "score")
    } yield score.toInt

    val theTeams = names zip score map {
      case (n, s) => PelitaTeamMinimal(n, s)
    }

    if ((data \ "game_state").isInstanceOf[JObject]) {
      val JBool(finished) = data \ "game_state" \ "finished"
      val matchResult: Option[MatchResultCode] =
          if (finished) {
            val team_wins = scala.util.Try { val JInt(team_wins) = data \ "game_state" \ "team_wins"; team_wins.toInt }.toOption
            val game_draw = scala.util.Try { val JBool(game_draw) = data \ "game_state" \ "game_draw"; game_draw }.toOption
            (team_wins, game_draw) match {
              case (None, Some(true)) => Some(MatchDraw)
              case (Some(0), None) => Some(MatchWinnerLeft)
              case (Some(1), None) => Some(MatchWinnerRight)
              case _ => None
            }
        } else None

      Some(PelitaMatchMinimal(theTeams(0), theTeams(1), matchResult))
    } else {
      Some(PelitaMatchMinimal(theTeams(0), theTeams(1), None))
    }
  }
  
  def receive(uuid: UUID, s: String, logger: MessageBus) = {
    parseOpt(s) map { json =>
      (json \ "__action__", json \ "__data__") match {
        case (JString("set_initial"), data: JObject) => {
          val d = set_initial(data)
          logger publishGlobal (uuid, d)
        }
        case (JString("observe"), data: JObject) => {
          val d = observe(data)
          logger publishGlobal (uuid, d)
        }
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

class ZMQPelitaController(val uuid: UUID, val controller: String, val subscriber: String, val logger: MessageBus) extends Actor with ActorLogging {
  override def postStop() = log.info(s"Stopped controller for $controller/$subscriber.")

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
      case m: ZMQMessage ⇒ SimpleSubscriber.receive(uuid, akka.util.ByteString(m.frames(0).payload.toArray).utf8String.toString, logger)
      case _             ⇒ //...
    }
  }))
  println(s"using ports $controller, $subscriber")
  private[this] val ctrlSocket = ZeroMQExtension(context.system).newSocket(SocketType.Dealer, Listener(ctrlListener), Connect(controller))
  private[this] val subSocket = ZeroMQExtension(context.system).newSocket(SocketType.Sub, Listener(subListener), Connect(subscriber), SubscribeAll)
  
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

  override def preRestart(reason: Throwable, message: Option[Any]) = log.info(s"Restarting due to $reason. ($message)")

  object TestRunner extends Runner {
    type GameType = PelitaGame
    val game = new PelitaGame{}
  }
 
  def doWork(workSender: ActorRef, msg: Any): Unit = {
    log.info(s"Trying to work on $msg")
    
    Future {
      msg match {
        case (QueuedMatch(uuid, a, b, qT, rT), logger: MessageBus) =>
          val c = context.actorOf(Props(new ZMQPelitaController(uuid getOrElse java.util.UUID.fromString("00000000-0000-0000-0000-000000000000"),
                                                                controller, subscriber, logger)))
          try {
            c ! "play"
            c ! "exit"
            val result = TestRunner.playGame(Pairing(a, b), controller=Some(controller), subscriber=Some(subscriber)).unsafePerformIO
            workSender ! result
          } finally {
            // ensure that we kill the controller again
            context.system.stop(c)
            // also, sleep for, say 10 seconds, to ensure the zmq socket is really closed
            Thread.sleep(10000)
          }
          WorkComplete("done")
        case msg =>
          WorkCouldNotRun(s"Did not understand message: $msg")
      }
    } recover {
      case e =>
        WorkFailed(s"Work Failed: $e")
    } pipeTo self
  }
}

