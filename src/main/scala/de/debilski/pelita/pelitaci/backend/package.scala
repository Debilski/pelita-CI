package de.debilski.pelita.pelitaci

package backend {
  case class Team(url: String, factory: String, name: Option[String]) {
    def this(url: String, factory: String) = this(url, factory, None)
  }
  case class TeamPath(team: Team, path: java.io.File)
  case class Pairing(team1: Team, team2: Team)

  sealed trait MatchResultCode
  case object MatchWinnerLeft extends MatchResultCode
  case object MatchWinnerRight extends MatchResultCode
  case object MatchDraw extends MatchResultCode

  case class MatchResult(pairing: Pairing, result: MatchResultCode)
}

package object backend {
}
