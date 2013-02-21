package de.debilski.pelita.CI

trait Ranking { self =>
  type Score = Double
  type Team
  
  val teamScores: Map[Team, Score]
  
  def eloNormalisation = 400
  
  def eloDifference(player: Score, other: Score) = math.min(math.abs(player - other), eloNormalisation) / eloNormalisation
  
  def eloExpectation(player: Score, other: Score) = 1.0 / (1.0 + math.pow(10, eloDifference(player, other)))
  
  def calculateScore(player: Team, other: Team, outcome: Double) = {
    teamScores(player) + 15 * (outcome - eloExpectation(teamScores(player), teamScores(other)))
  }
  
  def winningScore(player: Team, other: Team): Score = calculateScore(player, other, 1.0)
  def losingScore(player: Team, other: Team): Score = calculateScore(player, other, 0.0)
  def drawScore(player: Team, other: Team): Score = calculateScore(player, other, 0.5)

  def addWinning(winner: Team, loser: Team): Ranking{type Team=self.Team} = {
    val winnerScore = winningScore(winner, loser)
    val loserScore = losingScore(loser, winner)
    
    new Ranking {
      type Team = self.Team
      val teamScores = self.teamScores.updated(winner, winnerScore).updated(loser, loserScore)
    }
  }
  
  def addDraw(team1: Team, team2: Team): Ranking{type Team=self.Team} = {
    val newScore1 = drawScore(team1, team2)
    val newScore2 = drawScore(team2, team1)
    
    new Ranking {
      type Team = self.Team
      val teamScores = self.teamScores.updated(team1, newScore1).updated(team2, newScore2)
    }
  }  
}

