package de.debilski.pelita.pelitaci.backend
package PelitaInterface

import org.specs2.mutable.Specification

class RunnerSpecs extends Specification{
  
  object TestRunner extends Runner {
    type GameType = DummyGame
    val game = new DummyGame{}
  }
  
  val team1 = Team(url="/Volumes/Data/Projects/Python-School/players#master", "rike:factory")
  val team2 = Team(url="/Volumes/Data/Projects/Python-School/players#master", "rike:factory")

  "Pelita CI" should {
    "play a game" in {
      TestRunner.playGame(Pairing(team1, team2)).unsafePerformIO must_!= None
    }
    
    "play many games" in {
      val pairings = Iterator.continually(Pairing(team1, team2)).take(10).toList
      
      import scala.concurrent.Future
      import scala.concurrent.ExecutionContext.Implicits.global
      val runFuture = Future.traverse(pairings)(p => Future(TestRunner.playGame(p).unsafePerformIO))
      
      
    }
  }

}
