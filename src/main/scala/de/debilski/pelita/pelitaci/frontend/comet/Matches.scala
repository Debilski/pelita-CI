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
import de.debilski.pelita.pelitaci.backend.PelitaInterface.{PelitaMaze, PelitaMatchMinimal}
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
  private[this] val _layouts = scala.collection.mutable.LinkedHashMap.empty[UUID, PelitaMaze]

  // Inserts a match into our match list and returns a positive result when it was a new entry
  def updateMatchList(uuid: UUID, theMatch: PelitaMatchMinimal): Boolean = {
    val isNew = !(_matches contains uuid)
    _matches(uuid) = theMatch
    isNew
  }

  // Inserts a match into our layout list and returns a positive result when it was a new entry
  def updateLayoutList(uuid: UUID, theLayout: PelitaMaze): Boolean = {
    val isNew = !(_layouts contains uuid)
    _layouts(uuid) = theLayout
    isNew
  }

  def render = {
  //             "#num-workers *" #> lib.CI.numWorkersAgent() &
  //             "#queue-size *" #> lib.CI.queueSizeAgent() &
  Schedule.schedule(this, NumWorkersChanged(lib.CI.numWorkersAgent()), 100L)
  Schedule.schedule(this, QueueSizeChanged(lib.CI.queueSizeAgent()), 100L)

  "#allmatches *" #> renderMatches(_matches)
  }

  def renderMatchHeader(uuid: UUID, aMatch: PelitaMatchMinimal) = {
    ".teamA *" #> aMatch.teamA.name &
    ".teamB *" #> aMatch.teamB.name &
    ".scoreA *" #> aMatch.teamA.score &
    ".scoreB *" #> aMatch.teamB.score
  }

  def renderMatch(uuid: UUID, aMatch: PelitaMatchMinimal) = {
    import net.liftweb.json._

    ".match [id]" #> uuid.toString &
    "header [id]" #> ("header-" + uuid.toString) &
    "canvas [id]" #> ("canvas-" + uuid.toString) &
    "canvas [data-maze-bot-positions]" #> _layouts.get(uuid).map(maze => compact(net.liftweb.json.render(maze.bot_positions))) &
    "canvas [data-maze-walls]" #> _layouts.get(uuid).map(maze => compact(net.liftweb.json.render(maze.walls))) &
    "canvas [data-maze-width]" #> _layouts.get(uuid).map(maze => compact(net.liftweb.json.render(maze.width))) &
    "canvas [data-maze-height]" #> _layouts.get(uuid).map(maze => compact(net.liftweb.json.render(maze.height))) &
    renderMatchHeader(uuid, aMatch)
  }

  def headerHtml(uuid: UUID, aMatch: PelitaMatchMinimal): (String, NodeSeq) = {
    (s"header-${uuid.toString}", renderMatchHeader(uuid, aMatch).apply(defaultHtml \\ "header"))
  }

  def renderMatches(in: _matches.type) = {
    "article" #> in.toSeq.reverse.map {
      case (uuid, aMatch) => renderMatch(uuid, aMatch)
    }
  }

  def loadingMsg = <span id="allmatcheslist">{
    <b>Nothing yet.</b>
    }</span>

  import net.liftweb.http.js.JE
  import net.liftweb.http.js.jquery.JqJsCmds

  override def highPriority: PartialFunction[Any, Unit] = {
    case NumWorkersChanged(numWorkers) =>
      partialUpdate(JE.Call("updateNumWorkers", numWorkers).cmd)
    case QueueSizeChanged(queueSize) =>
      partialUpdate(JE.Call("updateQueueSize", queueSize).cmd)
  }

  override def lowPriority: PartialFunction[Any, Unit] = {
    case (uuid: UUID, Some(m @ PelitaMatchMinimal(ta, tb, winner))) => {
      val isNew = updateMatchList(uuid, m)
      if (isNew) {
        partialUpdate(JqJsCmds.PrependHtml("allmatches", renderMatch(uuid, m).apply(defaultHtml \\ "article")))
      }
      else {
        partialUpdate(SetHtml.tupled(headerHtml(uuid, m)))
      }
    }
    case (uuid: UUID, maze: PelitaMaze) =>
      val isNew = updateLayoutList(uuid, maze)
      if (isNew) {
        partialUpdate(JE.Call("createMaze", JE.JsRaw("$('#"+uuid.toString+" canvas')"), maze.width, maze.height, maze.walls, maze.bot_positions).cmd)
      } else {
        partialUpdate(JE.Call("createMaze", JE.JsRaw("$('#"+uuid.toString+" canvas')"), maze.width, maze.height, maze.walls, maze.bot_positions).cmd)
      }
  }
}
