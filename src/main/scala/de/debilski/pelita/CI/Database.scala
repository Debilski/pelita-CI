package de.debilski.pelita.CI.database

import scala.slick.driver.H2Driver.simple._
import Database.threadLocalSession

case class Team(id: Option[Int], uri: String, factory: String)
case class Match(id: Option[Int], teamA: Int, teamB: Int, result: Int, timestamp: Option[java.sql.Timestamp])

class Tables { 
  object Teams extends Table[Team]("TEAMS") {
    def id = column[Int]("TEAM_ID", O.PrimaryKey, O.AutoInc)
    def uri = column[String]("TEAM_URI")
    def factory = column[String]("TEAM_FACTORY")
    def * = id.? ~ uri ~ factory <> (Team, Team.unapply _)
  
    def forInsert = uri ~ factory
    def idx = index("idx_a", (uri, factory), unique = true)
  }
  
  object Matches extends Table[Match]("MATCHES") {
    def id = column[Int]("MATCH_ID", O.PrimaryKey, O.AutoInc)
    def teamA_id = column[Int]("TEAMA_ID")
    def teamB_id = column[Int]("TEAMB_ID")
    def result = column[Int]("RESULT")
    def timestamp = column[java.sql.Timestamp]("TIMESTAMP")
    def * = id.? ~ teamA_id ~ teamB_id ~ result ~ timestamp.? <> (Match, Match.unapply _)
    
    def teamA = foreignKey("TEAMA_ID_FK", teamA_id, Teams)(_.id)
    def teamB = foreignKey("TEAMB_ID_FK", teamB_id, Teams)(_.id)
  
    def forInsert = teamA_id ~ teamB_id ~ result
  }
}

import scala.concurrent.{ Promise, Future, Await }
import akka.actor.{ TypedActor, ActorSystem, TypedProps }
import akka.event.Logging

trait DBController {
  def createDB: Unit
  def addTeam(uri: String, factory: String): Unit
  def getTeams: Future[Seq[Team]]
}

class DBControllerImpl(dbURI: String) extends DBController with TypedActor.PreStart with TypedActor.PreRestart {
  def preRestart(reason: Throwable,message: Option[Any]) = log.info(s"DBController restarted: reason: $reason, msg: $message")
  def preStart() = println("PRESTART")
  
  val db = Database.forURL(dbURI, driver = "org.h2.Driver")
  val tables = new Tables
  
  val log = Logging(TypedActor.context.system, TypedActor.context.self)

  import TypedActor.dispatcher
  
  def createDB: Unit = {
    db withSession {
      log.info("DB created")
      (tables.Teams.ddl ++ tables.Matches.ddl).create
    }
  }
  
  def addTeam(uri: String, factory: String): Unit =  db withSession {
    tables.Teams.insert(Team(None, uri, factory))
  }
  
  def getTeams: Future[Seq[Team]] = db withSession {
    // Future successful (for (t <- tables.Teams) yield t).list.toSeq
    (Promise() complete scala.util.Try((for (t <- tables.Teams) yield t).list.toSeq)).future
  }
}

object DBController {
  def createActor(system: ActorSystem)(dbURI: String): DBController = TypedActor(system).typedActorOf(TypedProps(classOf[DBController], new DBControllerImpl(dbURI)))
}

