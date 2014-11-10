package de.debilski.pelita.pelitaci.backend.database

import scala.slick.driver.H2Driver.simple._
import Database.dynamicSession
import java.util.UUID

case class Team(id: Option[Int], url: String, factory: String, name: Option[String])
case class Match(uuid: Option[UUID], teamA: Int, teamB: Int, result: Int, timestamp: Option[java.sql.Timestamp])

class Tables { 
  class Teams(tag: Tag) extends Table[Team](tag, "TEAMS") {
    def id = column[Int]("TEAM_ID", O.PrimaryKey, O.AutoInc)
    def url = column[String]("TEAM_URL")
    def factory = column[String]("TEAM_FACTORY")
    def name = column[String]("TEAM_NAME", O.Nullable)
    def * = (id.?, url, factory, name.?) <> (Team.tupled, Team.unapply)
  
    def idx = index("idx_a", (url, factory), unique = true)
  }
  val teams = TableQuery[Teams]
  
  class Matches(tag: Tag) extends Table[Match](tag, "MATCHES") {
    def uuid = column[UUID]("MATCH_ID", O.PrimaryKey)
    def teamA_id = column[Int]("TEAMA_ID")
    def teamB_id = column[Int]("TEAMB_ID")
    def result = column[Int]("RESULT")
    def timestamp = column[java.sql.Timestamp]("TIMESTAMP")
    def * = (uuid.?, teamA_id, teamB_id, result, timestamp.?) <> (Match.tupled, Match.unapply)
    
    def teamA = foreignKey("TEAMA_ID_FK", teamA_id, teams)(_.id)
    def teamB = foreignKey("TEAMB_ID_FK", teamB_id, teams)(_.id)
  
    def forInsert = matches.map(m => (m.teamA_id, m.teamB_id, m.result))
  }
  val matches = TableQuery[Matches]
}

import scala.concurrent.{ Promise, Future, Await }
import akka.actor.{ TypedActor, ActorSystem, TypedProps }
import akka.event.Logging

trait DBController {
  def createDB(): Unit

  def addTeam(url: String, factory: String, name: Option[String]): Future[Int]
  def getTeam(id: Int): Future[Team]
  def getTeams(): Future[Seq[Team]]

  def storeMatch(uuid: UUID, teamA: Int, teamB: Int, result: Int, timestamp: Option[java.sql.Timestamp]): Unit
  def getMatches(): Future[Seq[Match]]
}

class DBControllerImpl(dbURL: String) extends DBController with TypedActor.PreRestart {
  val log = Logging(TypedActor.context.system, TypedActor.context.self)

  def preRestart(reason: Throwable,message: Option[Any]) = log.info(s"DBController restarted: reason: $reason, msg: $message")
  
  val db = Database.forURL(dbURL, driver = "org.h2.Driver")
  val tables = new Tables

  import TypedActor.dispatcher
  
  def createDB(): Unit = {
    db withDynSession {
      log.info("Creating database")
      try {
        (tables.teams.ddl ++ tables.matches.ddl).create
      } catch {
        case e: org.h2.jdbc.JdbcSQLException => log.info(s"Could not create database for URL $dbURL. ($e)")
      }
    }
  }
  
  def addTeam(url: String, factory: String, name: Option[String]): Future[Int] =  db withDynSession {
    val id: Int = try {
        tables.teams.map(t => (t.url, t.factory, t.name.?)).returning(tables.teams.map(_.id)) += (url, factory, name)
      } catch {
        case e: org.h2.jdbc.JdbcSQLException =>
          name.foreach(newName => (for { team <- tables.teams if team.url === url && team.factory === factory} yield team.name).update(newName))
          (for { team <- tables.teams if team.url === url && team.factory === factory} yield team.id).first
      }
    (Promise successful id).future
  }

  def getTeam(id: Int): Future[Team] = db withDynSession {
    (Promise() complete scala.util.Try(tables.teams.filter(_.id === id).first)).future
  }
  
  def getTeams(): Future[Seq[Team]] = db withDynSession {
    (Promise() complete scala.util.Try((for (t <- tables.teams) yield t).list.toSeq)).future
  }

  def storeMatch(uuid: UUID, teamA: Int, teamB: Int, result: Int, timestamp: Option[java.sql.Timestamp]): Unit = db withDynSession {
    val tstamp = timestamp orElse Option(new java.sql.Timestamp(java.util.Calendar.getInstance.getTime.getTime))
    tables.matches += Match(Some(uuid), teamA, teamB, result, tstamp)
  }

  def getMatches(): Future[Seq[Match]] = db withDynSession {
    (Promise() complete scala.util.Try((for (m <- tables.matches.sortBy(_.timestamp)) yield m).list.toSeq)).future
  }
}

object DBController {
  def createActor(system: ActorSystem)(dbURL: String): DBController = TypedActor(system).typedActorOf(TypedProps(classOf[DBController], new DBControllerImpl(dbURL)))
}

