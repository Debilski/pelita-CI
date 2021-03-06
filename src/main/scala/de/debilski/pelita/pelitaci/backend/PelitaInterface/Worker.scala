package de.debilski.pelita.pelitaci.backend
package PelitaInterface

import scala.concurrent.Future
import akka.actor._
import akka.pattern.pipe
import akka.event.Logging
import java.util.UUID
import net.liftweb.json._

trait SimpleControllerInterface {
  def set_initial()
  def play()
  def play_round()
  def play_step()
  def update_viewers()
  def exit()
}

trait SimpleSubscriberInterface {
  def set_initial(data: JObject)
  def observe(data: JObject)
  def exit()
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

case class PelitaMaze(
    width: net.liftweb.json.JInt,
    height: net.liftweb.json.JInt,
    walls: net.liftweb.json.JArray,
    bot_positions: net.liftweb.json.JArray)

class SimpleSubscriber(uuid: UUID, logger: MessageBus) extends SimpleSubscriberInterface {

  def set_initial(data: JObject) = {
    val minimalMatch = extractMinimal(data)
    logger publishGlobal (uuid, minimalMatch)

    val maze = extractMaze(data)
    maze foreach (mz => logger publishGlobal (uuid, mz))
  }

  def observe(data: JObject) = {
    val minimalMatch = extractMinimal(data)
    logger publishGlobal (uuid, minimalMatch)

    val maze = extractMaze(data)
    maze foreach (mz => logger publishGlobal (uuid, mz))
  }
  def exit() = {}

  def extractMaze(data: JObject): Option[PelitaMaze] = {
    val width = data \ "universe" \ "__value__" \ "maze" \ "__value__" \ "width"
    val height = data \ "universe" \ "__value__" \ "maze" \ "__value__" \ "height"
    val maze = data \ "universe" \ "__value__" \ "maze" \ "__value__" \ "data"
    val JArray(bots) = data \ "universe" \ "__value__" \ "bots"
    val bot_positions = JArray(bots.map(_ \ "__value__" \ "current_pos"))

    (width, height, maze, bot_positions) match {
      case (w: JInt, h: JInt, m: JArray, b: JArray) => Some(PelitaMaze(width=w, height=h, walls=m, bot_positions = b))
      case _ => None
    }
  }

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
  
  def receive(rawJson: String) = {
    parseOpt(rawJson) map { json =>
      (json \ "__action__", json \ "__data__") match {
        case (JString("set_initial"), data: JObject) => set_initial(data)
        case (JString("observe"), data: JObject) => observe(data)
        case (JString("exit"), other) => exit()
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
  
  def set_initial() = shipAction("set_initial")
  def play() = shipAction("play")
  def play_round() = shipAction("play_round")
  def play_step() = shipAction("play_step")
  def update_viewers() = shipAction("update_viewers")
  def exit() = shipAction("exit")
}

class ZMQPelitaController(val uuid: UUID, val controller: String, val subscriber: String, val logger: MessageBus) extends Actor with ActorLogging {
  override def preStart() = log.info(s"ZMQPelitaController using ports $controller, $subscriber.")

  override def postStop() = {
    context.system.stop(ctrlSocket)
    context.system.stop(subSocket)
    log.info(s"Stopped controller for $controller/$subscriber.")
  }

  import akka.zeromq._

  private[this] val ctrlSocket = ZeroMQExtension(context.system).newSocket(SocketType.Dealer, Connect(controller))
  val controllerSender = new SimpleController(ctrlSocket)

  val subListener = context.actorOf(Props(new Actor {
    val subscriptionReceiver = new SimpleSubscriber(uuid, logger)

    def receive: Receive = {
      case c@Connecting  ⇒ Logging(context.system, this).info(c.toString)
      case m: ZMQMessage ⇒ subscriptionReceiver.receive(akka.util.ByteString(m.frames(0).payload.toArray).utf8String.toString)
      case _             ⇒ //...
    }
  }))
  private[this] val subSocket = ZeroMQExtension(context.system).newSocket(SocketType.Sub, Listener(subListener), Connect(subscriber), SubscribeAll)
  
  def receive = {
    case "set_initial" => { controllerSender.set_initial }
    case "play" => { controllerSender.play }
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
          val matchUuid = uuid getOrElse java.util.UUID.fromString("00000000-0000-0000-0000-000000000000")
          val c = context.actorOf(Props(new ZMQPelitaController(matchUuid,
                                                                controller, subscriber, logger)))
          try {
            val resultIO = TestRunner.withPreparedGame(Pairing(a, b)) { preparedGame =>
              val (team1, team2) = preparedGame.teams

              for {
                name1 <- TestRunner.checkPreparedTeamName()(team1)
                name2 <- TestRunner.checkPreparedTeamName()(team2)
                _ <- scalaz.effect.IO {
                  c ! "set_initial"
                  c ! "play"
                  c ! "exit"
                }
                game <- TestRunner.playPreparedGame(controller=Some(controller), subscriber=Some(subscriber))(preparedGame)
              } yield game
            }

            val result = resultIO.unsafePerformIO

            workSender ! result
          } finally {
            // ensure that we kill the controller again
            context.system.stop(c)
            // also, sleep for, say 10 seconds, to ensure the zmq socket is really closed
            // Thread.sleep(10000)
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

