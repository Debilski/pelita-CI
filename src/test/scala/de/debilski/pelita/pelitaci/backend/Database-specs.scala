package de.debilski.pelita.pelitaci.backend

import org.specs2.mutable.Specification
import de.debilski.pelita.pelitaci.backend.database.DBController

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
  
  val team1 = Team(url="/Volumes/Data/Projects/Python-School/players#master", "rike:factory")
  val team1B = Team(url="/Volumes/Data/Projects/Python-School/players#master", "rike:factory")
  val team2 = Team(url="/Volumes/Data/Projects/Python-School/players#master", "rike:factory2")

  "Database" should {
    "add teams" in {
      val myActor = DBController.createActor(system)("jdbc:h2:mem:test1;DB_CLOSE_DELAY=-1")
      myActor.createDB()
      
      myActor addTeam (team1.url.toString, team1.factory)
      myActor addTeam (team2.url.toString, team2.factory)
      
      val fut = myActor.getTeams.map(_.length)
      
      Await.result(fut, Duration("5s"))  must_== 2
    }
    
    "not add duplicate teams" in {
      val myActor = DBController.createActor(system)("jdbc:h2:mem:test2;DB_CLOSE_DELAY=-1")
      myActor.createDB()
      
      myActor addTeam (team1.url.toString, team1.factory)
      myActor addTeam (team2.url.toString, team2.factory)
      myActor addTeam (team1B.url.toString, team1B.factory)
      
      val fut = myActor.getTeams.map(_.length)
      
      Await.result(fut, Duration("5s"))  must_== 2
    }

    "duplicate teams should have the same id" in {
      val myActor = DBController.createActor(system)("jdbc:h2:mem:test3;DB_CLOSE_DELAY=-1")
      myActor.createDB()

      val id1A_F: Future[Int] = myActor addTeam (team1.url.toString, team1.factory)
      val id2A_F: Future[Int] = myActor addTeam (team2.url.toString, team2.factory)
      val id1B_F: Future[Int] = myActor addTeam (team1B.url.toString, team1B.factory)
      val id2B_F: Future[Int] = myActor addTeam (team2.url.toString, team2.factory)

      val success = for {
        id1A <- id1A_F
        id2A <- id2A_F
        id1B <- id1B_F
        id2B <- id2B_F
      } yield (id1A == id1B) && (id2A == id2B)

      Await.result(success, Duration("5s")) must_== true
    }
    
    "add teams coming from separate threads" in {
      val myActor = DBController.createActor(system)("jdbc:h2:mem:test4;DB_CLOSE_DELAY=-1")
      myActor.createDB()
      
      val numTeams = 120
      val teams = Future.traverse(1 to numTeams){ i =>
          Future(myActor addTeam ("abc", i.toString))
        }
      
      val count = for (t <- teams; c <- myActor.getTeams.map(_.length)) yield c
      
      Await.result(count, Duration("8s")) must_== numTeams
      
    }
  }

}
