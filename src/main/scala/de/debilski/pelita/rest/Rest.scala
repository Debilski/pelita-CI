package de.debilski.pelita
package rest

import net.liftweb.http.rest.RestHelper
import net.liftweb.http.LiftRules
import net.liftweb.json.JsonAST.JString

object Rest extends RestHelper {
  def init() : Unit = {
    LiftRules.statelessDispatch.append(Rest)
  }

  serve("rest" :: Nil prefix {
    case "team" :: teamURI :: "add" :: Nil JsonGet _ => JString(teamURI)
  })
}