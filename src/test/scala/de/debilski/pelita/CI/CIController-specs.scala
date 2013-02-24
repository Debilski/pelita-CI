package de.debilski.pelita.CI

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
      
      val team1 = Team(uri=new java.net.URI("/Volumes/Data/Projects/Python-School/players#master"), "rike:factory")
      val team2 = Team(uri=new java.net.URI("/Volumes/Data/Projects/Python-School/players#master"), "rike:factory")
      
      master ! PlayGame(team1, team2)
      master ! PlayGame(team1, team2)
      master ! PlayGame(team1, team2)

      val res = Some(MatchResult(Pairing(team1, team2), MatchDraw))
      expectMsgAllOf(20.seconds, res, res, res)
    }
  }
}
