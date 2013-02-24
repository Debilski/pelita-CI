package de.debilski.pelita
package lib

import net.liftweb.http.CometActor
import java.net.URI

object CI {
  case class TeamsList(teams: Seq[de.debilski.pelita.CI.Team])

  def requestTeams(actor: CometActor) = {
    actor send TeamsList(de.debilski.pelita.CI.Team(new URI("Team1#master"), "my:team") :: Nil)
  }

}