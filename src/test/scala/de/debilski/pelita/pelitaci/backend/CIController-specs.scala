package de.debilski.pelita.pelitaci.backend

import akka.actor.ActorSystem
import akka.actor.Props
import akka.testkit.TestKit
import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers
import org.scalatest.BeforeAndAfterAll
import akka.testkit.ImplicitSender
import scala.concurrent.duration._

class CIControllerSpec extends TestKit(ActorSystem("CIControllerSpec"))
       with ImplicitSender
       with WordSpec
       with BeforeAndAfterAll
       with MustMatchers {

  override def afterAll() {
    system.shutdown()
  }
  
  "Controller" should {
    "run with workers" in {
      val master = system.actorOf(Props[Controller], name="controller1")

      def basePort = 51100
      def numWorkers = 2

      val workerFactory = new DefaultWorkerFactory(system, master.path.child("gamebalancer"))
      val workers = (0 until numWorkers).map { i ⇒
        workerFactory.worker(s"tcp://127.0.0.1:${basePort + 2 * i}", s"tcp://127.0.0.1:${basePort + 2 * i + 1}")
      }
      
      val team1 = Team(uri="/Volumes/Data/Projects/Python-School/players#master", "rike:factory")
      val team2 = Team(uri="/Volumes/Data/Projects/Python-School/players#master", "rike:factory")
      
      master ! PlayGame(team1, team2)
      master ! PlayGame(team1, team2)
      master ! PlayGame(team1, team2)

      // We expect 3 QueuedMatch and 3 Some(MatchResult) messages
      val msgs = receiveN(6, 30.seconds)
      (msgs filter (_.isInstanceOf[QueuedMatch])).length must be(3)
      (msgs filter { case Some(MatchResult(pairing, winner)) => true; case _ => false }).length must be(3)
    }
  }
}
