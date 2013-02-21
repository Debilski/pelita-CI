package de.debilski.pelita.CI

case class Team(uri: java.net.URI, factory: String)
case class TeamPath(team: Team, path: java.io.File)
case class Pairing(team1: Team, team2: Team)

sealed trait MatchResultCode
case object MatchWinnerLeft extends MatchResultCode
case object MatchWinnerRight extends MatchResultCode
case object MatchDraw extends MatchResultCode

case class MatchResult(pairing: Pairing, result: MatchResultCode)

object `package` {
  type GitURI = java.net.URI
}
