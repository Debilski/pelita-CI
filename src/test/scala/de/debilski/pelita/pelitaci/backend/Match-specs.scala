package de.debilski.pelita.pelitaci

import org.specs2.mutable.Specification

class MatchSpecs extends Specification{

  val ranking = new Ranking {
    type Team = de.debilski.pelita.pelitaci.backend.Team
    val teamScores = Map[Team, Score]() withDefaultValue 0.0
  }

  import de.debilski.pelita.pelitaci.backend.Team
  val team1 = Team(url="/Volumes/Data/Projects/Python-School/players#master", "rike:factory1")
  val team2 = Team(url="/Volumes/Data/Projects/Python-School/players#master", "rike:factory2")
  
  "Elo expectation" should {
    "be 0.5 when both are equally strong" in {
      ranking.eloExpectation(0.0, 0.0) must beCloseTo (0.5, 0.001)
      ranking.eloExpectation(1.0, 1.0) must beCloseTo (0.5, 0.001)
      ranking.eloExpectation(100.0, 100.0) must beCloseTo (0.5, 0.001)
    }
  }

  "Pelita CI" should {
    "not change after draw" in {
      val r = ranking.addDraw(team1, team2)
      r.teamScores(team1) must beCloseTo (0, 0.001)
      r.teamScores(team2) must beCloseTo (0, 0.001)
    }
    
    "calculate results" in {
      val r = ranking.addDraw(team1, team2).addWinning(team1, team2).addWinning(team1, team2)
      r.teamScores(team1) must beCloseTo (15.323, 0.001)
      r.teamScores(team2) must beCloseTo (-14.676, 0.001)
    }
    
    /*"play many games" in {
      val pairings = Iterator.continually(Pairing(team1, team2)).take(10).toList
      
      import scala.concurrent.Future
      import scala.concurrent.ExecutionContext.Implicits.global
      val runFuture = Future.traverse(pairings)(p => Future(Runner.playGame(p).unsafePerformIO))
      
      
    }*/
  }

}
