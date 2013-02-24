package de.debilski.pelita.CI.utils
package workbalancer

import akka.actor.ActorSystem
import akka.actor.Actor
import akka.actor.ActorPath
import akka.actor.ActorRef
import akka.actor.Props
import akka.testkit.TestKit
import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers
import org.scalatest.BeforeAndAfterAll
import akka.testkit.ImplicitSender
import scala.concurrent.duration._

class TestWorker(masterLocation: ActorPath) extends Worker(masterLocation) {
  def doWork(workSender: ActorRef, msg: Any): Unit = {
    msg match {
      case i: Int => workSender ! i*2 
      case s: String => workSender ! s ++ s
      case _ => workSender ! 'Nothing
    }
    self ! WorkComplete("done")
  }
}

class TestMaster extends Master {
  def receiveWork = {
    case everything => doWork(everything)
  }
}

class WorkBalancerSpec extends TestKit(ActorSystem("WorkBalancerSpec"))
       with ImplicitSender
       with WordSpec
       with BeforeAndAfterAll
       with MustMatchers {

  override def afterAll() {
    system.shutdown()
  }
  
  "Work balancer" should {
    "balance work among workers" in {
      def worker(name: String) = system.actorOf(Props(new TestWorker(ActorPath.fromString("akka://%s/user/%s".format(system.name, name)))))
      
      val master = system.actorOf(Props[TestMaster], name="testmaster")
      val w1 = worker("testmaster")
      val w2 = worker("testmaster")
      val w3 = worker("testmaster")
      
      master ! 1
      master ! 2
      master ! "a"
      master ! "b"
      master ! "c"
      master ! 'symbol
      
      expectMsgAllOf(10.seconds, 2, 4, "aa", "bb", "cc", 'Nothing)
    }
  }
}
