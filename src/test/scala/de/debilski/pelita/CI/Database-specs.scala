package de.debilski.pelita.CI

import org.specs2.mutable.Specification
import de.debilski.pelita.CI.database.DBController

class DatabaseSpecs extends Specification{
  
  import akka.actor.{ TypedActor, TypedProps }
  import akka.actor.Props
  import akka.event.Logging
  import akka.actor.ActorSystem
  import scala.concurrent.Future
  import scala.concurrent.duration.Duration
  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.Await
  
  val system = ActorSystem("MySystem")
  
  import database.DBController
  
  val team1 = Team(uri=new java.net.URI("/Volumes/Data/Projects/Python-School/players#master"), "rike:factory")
  val team1B = Team(uri=new java.net.URI("/Volumes/Data/Projects/Python-School/players#master"), "rike:factory")
  val team2 = Team(uri=new java.net.URI("/Volumes/Data/Projects/Python-School/players#master"), "rike:factory2")

  "Database" should {
    "add teams" in {
      val myActor = DBController.createActor(system)("jdbc:h2:mem:test1;DB_CLOSE_DELAY=-1")
      myActor.createDB
      
      myActor addTeam (team1.uri.toString, team1.factory)
      myActor addTeam (team2.uri.toString, team2.factory)
      
      val fut = myActor.getTeams.map(_.length)
      
      Await.result(fut, Duration("5s"))  must_== 2
    }
    
    "not add duplicate teams" in {
      val myActor = DBController.createActor(system)("jdbc:h2:mem:test2;DB_CLOSE_DELAY=-1")
      myActor.createDB
      
      myActor addTeam (team1.uri.toString, team1.factory)
      myActor addTeam (team2.uri.toString, team2.factory)
      myActor addTeam (team1B.uri.toString, team1B.factory)
      
      val fut = myActor.getTeams.map(_.length)
      
      Await.result(fut, Duration("5s"))  must_== 2
    }
    
    "add teams coming from separate threads" in {
      val myActor = DBController.createActor(system)("jdbc:h2:mem:test3;DB_CLOSE_DELAY=-1")
      myActor.createDB
      
      val numTeams = 120
      val teams = Future.traverse(1 to numTeams){ i =>
          Future(myActor addTeam ("abc", i.toString))
        }
      
      val count = for (t <- teams; c <- myActor.getTeams.map(_.length)) yield c
      
      Await.result(count, Duration("8s")) must_== numTeams
      
    }
  }

}
