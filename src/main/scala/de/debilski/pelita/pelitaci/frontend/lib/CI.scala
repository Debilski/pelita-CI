package de.debilski.pelita.pelitaci.frontend
package lib

import de.debilski.pelita.pelitaci.DefaultSettings
import scala.concurrent.ExecutionContext.Implicits.global
import net.liftweb.http.CometActor
import de.debilski.pelita.pelitaci.backend._
import database.Match
import java.util.UUID
import de.debilski.pelita.pelitaci.backend.PelitaInterface.{PelitaTeamMinimal, PelitaMatchMinimal}
import de.debilski.pelita.pelitaci.Ranking
import de.debilski.pelita.pelitaci.backend.database.Match
import PelitaInterface.PelitaMatchMinimal
import de.debilski.pelita.pelitaci.backend.QueuedMatch
import de.debilski.pelita.pelitaci.backend.SubscribeGlobal
import de.debilski.pelita.pelitaci.backend.utils.workbalancer.InfoMessages.{QueueSizeChanged, NumWorkersChanged}

class BridgeActor(cometActor: CometActor) extends akka.actor.Actor with akka.actor.ActorLogging {
  override def preStart() = log.info(s"Created BridgeActor for $cometActor")

  override def postStop() = log.info(s"Stopped BridgeActor for $cometActor")

  def receive = {
    case msg => cometActor ! msg
  }
}

case class ScheduleRandom(number: Int)
case class AutoMode(maxQueue: Int, intervalSecs: Int)
case object AutoModePing
case object AutoModeStop

class AutoScheduler extends akka.actor.Actor with akka.actor.ActorLogging {
  def distribute(a: Int, b: Int): (Int, Int) = {
    if (a > b) { (a + 1, b) }
    else if (a < b) { (a, b + 1) }
    else {
      val choice = scala.util.Random.nextInt(2)
      if (choice == 0) {
        (a + 1, b)
      } else {
        (a, b + 1)
      }
    }
  }

  implicit class Team2Json(t: de.debilski.pelita.pelitaci.backend.database.Team) {
    def toTeam = de.debilski.pelita.pelitaci.backend.Team(t.url, t.factory, t.name)
  }

  var _autoMode = false
  var _scheduler: Option[akka.actor.Cancellable] = None

  def receive = {
    case ScheduleRandom(number: Int) => {

      CI.db.getTeams() map { teams =>
        val numTeams = teams.length
        val nums = Vector.fill(number * 2)(scala.util.Random.nextInt(numTeams - 1)).grouped(2)
        val matchIdxs: Vector[(Int, Int)] = for {
          Vector(teamAId_, teamBId_) <- nums.toVector
        } yield distribute(teamAId_, teamBId_)

        matchIdxs foreach {
          case (a, b) =>
            CI.controller ! PlayGame(teams(a).toTeam, teams(b).toTeam)
        }
      }
    }
    case AutoModePing => self ! ScheduleRandom(1)
    case AutoMode(maxQueue: Int, intervalSecs: Int) =>
      _scheduler.foreach(_.cancel())

      val delay = scala.concurrent.duration.Duration(2, scala.concurrent.duration.SECONDS)
      val frequency = scala.concurrent.duration.Duration(intervalSecs, scala.concurrent.duration.SECONDS)
      _scheduler = Some(context.system.scheduler.schedule(delay, frequency, self, AutoModePing))

    case AutoModeStop => _scheduler.foreach(_.cancel())
  }
}


object CI {
  case class TeamsList(teams: Seq[(Int, de.debilski.pelita.pelitaci.backend.Team, Option[Double])])

  private var _ranking = new Ranking {
    type Team = Int
    val teamScores = Map[Team, Score]() withDefaultValue 800.0
  }

  case class WDL(wins: Int, draws: Int, losses: Int)

  private val _statisticTotal = scala.collection.mutable.LinkedHashMap.empty[Int, WDL] withDefaultValue WDL(0, 0, 0)
  private val _statisticComplete = scala.collection.mutable.LinkedHashMap.empty[Int, Vector[Int]] withDefaultValue Vector.empty

  private def _updateRanking(teamA: Int, teamB: Int, result: Int) {
    println(s"Updating ranking between teams $teamA and $teamB.")
    result match {
      case 0 =>
        _ranking = _ranking.addDraw(teamA, teamB)
        _statisticTotal(teamA) = _statisticTotal(teamA).copy(draws = _statisticTotal(teamA).draws + 1)
        _statisticTotal(teamB) = _statisticTotal(teamB).copy(draws = _statisticTotal(teamB).draws + 1)
        _statisticComplete(teamA) = _statisticComplete(teamA) :+ 0
        _statisticComplete(teamB) = _statisticComplete(teamB) :+ 0
      case 1 =>
        _ranking = _ranking.addWinning(teamA, teamB)
        _statisticTotal(teamA) = _statisticTotal(teamA).copy(wins = _statisticTotal(teamA).wins + 1)
        _statisticTotal(teamB) = _statisticTotal(teamB).copy(losses = _statisticTotal(teamB).losses + 1)
        _statisticComplete(teamA) = _statisticComplete(teamA) :+ 1
        _statisticComplete(teamB) = _statisticComplete(teamB) :+ -1
      case 2 =>
        _ranking = _ranking.addWinning(teamB, teamA)
        _statisticTotal(teamA) = _statisticTotal(teamA).copy(losses = _statisticTotal(teamA).losses + 1)
        _statisticTotal(teamB) = _statisticTotal(teamB).copy(wins = _statisticTotal(teamB).wins + 1)
        _statisticComplete(teamA) = _statisticComplete(teamA) :+ -1
        _statisticComplete(teamB) = _statisticComplete(teamB) :+ 1
      case _ =>
    }
    statisticsAgent send (_statisticTotal, _statisticComplete)
  }

  def requestTeams(actor: CometActor) = {
    db.getTeams() map { teams =>
      val teamsScore = teams.flatMap { team =>
        val score = team.id map _ranking.teamScores
        team.id map (id => (id, de.debilski.pelita.pelitaci.backend.Team(team.url, team.factory, team.name), score))
      } sortBy (t => t._3.map(-_))

      actor send TeamsList(teamsScore)
    }
  }

  def createBridgeActor(actor: CometActor) = actorSystem.actorOf(akka.actor.Props(new BridgeActor(actor)), name=s"bridge-actor-${actor.##}")

  val actorSystem = akka.actor.ActorSystem("Pelita-CI")
  val controller = actorSystem.actorOf(akka.actor.Props[Controller], name = "controller")
  val autoScheduler = actorSystem.actorOf(akka.actor.Props[AutoScheduler], name = "autoScheduler")


  def basePort = DefaultSettings.initialZmqPort // 51100
  def numWorkers = DefaultSettings.numWorkers // 2

  val workerFactory = new DefaultWorkerFactory(actorSystem, controller.path.child("gamebalancer"))
  val workers = (0 until numWorkers).map { i ⇒
    workerFactory.worker(s"tcp://127.0.0.1:${basePort + 2 * i}", s"tcp://127.0.0.1:${basePort + 2 * i + 1}")
  }

  val numWorkersAgent = akka.agent.Agent(0)
  val queueSizeAgent = akka.agent.Agent(0)

  val statisticsAgent = akka.agent.Agent((_statisticTotal, _statisticComplete))

  val db = de.debilski.pelita.pelitaci.backend.database.DBController.createActor(actorSystem)("jdbc:h2:pelita.db")
  db.createDB()

  for {
    oldMatches <- db.getMatches()
    Match(_, teamA, teamB, result, _) <- oldMatches
  } _updateRanking(teamA, teamB, result)

  val matchResultListener = actorSystem.actorOf(akka.actor.Props(new akka.actor.Actor with akka.actor.ActorLogging {
    val _queuedMatches = scala.collection.mutable.LinkedHashMap.empty[UUID, (Int, Int)]

    def receive = {
      case NumWorkersChanged(numWorkers) => numWorkersAgent send numWorkers
      case QueueSizeChanged(queueSize) => queueSizeAgent send queueSize
      case QueuedMatch(Some(uuid), teamA, teamB, _, _) =>
        log.info(s"Queuing match $uuid between $teamA and $teamB")
        val updateFuture = for {
          teamAId <- db.addTeam(teamA.url, teamA.factory, teamA.name)
          teamBId <- db.addTeam(teamB.url, teamB.factory, teamB.name)
        } yield {
          _queuedMatches(uuid) = (teamAId, teamBId)
        }
        // TODO: Somehow remove this Await
        scala.concurrent.Await.ready(updateFuture, scala.concurrent.duration.Duration("3 hours"))

      case (uuid: UUID, Some(m @ PelitaMatchMinimal(ta, tb, Some(matchWinner)))) => {
        log.info(s"Received a result in match $uuid: $m")
        val storedMatch = _queuedMatches.get(uuid)

        storedMatch foreach { m =>
          db.storeMatch(uuid, m._1, m._2, matchWinner.code, None)
          _updateRanking(m._1, m._2, matchWinner.code)
        }
      }
    }
  }))
  controller ! SubscribeGlobal(matchResultListener)
}
