package de.debilski.pelita.pelitaci.frontend
package comet

import scala.xml._
import net.liftweb._
import http._
import net.liftweb.actor._
import net.liftweb.common.{Full}
import net.liftweb.util._
import net.liftweb.util.Helpers._
import net.liftweb.http.js.JsCmds.{SetHtml}
import net.liftweb.http.js.JE
import lib.CI
import de.debilski.pelita.pelitaci.backend.{UnsubscribeAll, SubscribeGlobal}
import akka.actor.{PoisonPill, ActorRef}
import de.debilski.pelita.pelitaci.backend.PelitaInterface.PelitaMatchMinimal
import java.util.UUID
import de.debilski.pelita.pelitaci.backend.utils.workbalancer.InfoMessages.{QueueSizeChanged, NumWorkersChanged}

class Matches extends CometActor {
  override def defaultPrefix = Full("mtchs")

  private[this] var listener: Option[ActorRef] = None

  override protected def localSetup() {
    listener = Some(CI.createBridgeActor(this))
    listener.foreach { l =>
      CI.controller ! SubscribeGlobal(l)
    }
    super.localSetup()
  }

  override protected def localShutdown() {
    listener.foreach { l =>
      CI.controller ! UnsubscribeAll(l)
      CI.actorSystem.stop(l)
    }
    super.localShutdown()
  }

  private[this] val _matches = scala.collection.mutable.LinkedHashMap.empty[UUID, PelitaMatchMinimal]

  // Inserts a match into our matchlist and returns a positive result when it was a new entry
  def updateMatchList(uuid: UUID, theMatch: PelitaMatchMinimal): Boolean = {
    val isNew = !(_matches contains uuid)
    _matches(uuid) = theMatch
    isNew
  }

  def render = "#allmatches *" #> renderMatches(_matches) &
               "#num-workers *" #> _numWorkers &
               "#queue-size *" #> _queueSize

  def renderMatches(in: _matches.type) = {
    "article" #> in.toSeq.reverse.map {
      case (uuid, aMatch) =>
        ".match [id]" #> uuid.toString &
        ".teamA *" #> aMatch.teamA.name &
        ".teamB *" #> aMatch.teamB.name &
        ".scoreA *" #> aMatch.teamA.score &
        ".scoreB *" #> aMatch.teamB.score
    }
  }

  def loadingMsg = <span id="allmatcheslist">{
    <b>Nothing yet.</b>
    }</span>

  import net.liftweb.http.js.JE

  var _numWorkers: Option[Int] = None
  var _queueSize: Option[Int] = None

  override def highPriority: PartialFunction[Any, Unit] = {
    case NumWorkersChanged(numWorkers) =>
      _numWorkers = Some(numWorkers)
      partialUpdate(JE.Call("updateNumWorkers", numWorkers).cmd)
    case QueueSizeChanged(queueSize) =>
      _queueSize = Some(queueSize)
      partialUpdate(JE.Call("updateQueueSize", queueSize).cmd)
  }

  override def lowPriority: PartialFunction[Any, Unit] = {
    case (uuid: UUID, Some(m @ PelitaMatchMinimal(ta, tb, winner))) => {
      updateMatchList(uuid, m)
      reRender(true)
    }
  }
}
