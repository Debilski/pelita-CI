package de.debilski.pelita.pelitaci.frontend
package comet

import net.liftweb._
import http._
import net.liftweb.actor._
import net.liftweb.common.{Box, Full}

class Matches extends CometActor {
  override def defaultPrefix = Full("mtchs")

  def render = {
    bind("matches" -> <b>Nothing yet.</b>)
  }
}
