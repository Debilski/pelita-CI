package de.debilski.pelita.pelitaci.backend.utils
package workbalancer

import akka.actor.{ Actor, ActorRef, ActorPath, ActorLogging, Terminated }

 /* This is pretty much copied from http://letitcrash.com/post/29044669086/balancing-workload-across-nodes-with-akka-2
  * We may make this more robust and reusable later.
  *
  * For balancing work on a single machine, it might be easier to use a simple BalancingDispatcher.
  * However, it is more difficult (if at all possible) to keep the working actor responsive in this case
  * (which means that work must be done in the background) without signalling the BalancingDispatcher
  * that we have finished working on our task.
  *
  * As a plus, the master–worker protocol also gives the easy opportunity to scale across multiple machines.
  *
  * */

object MasterWorkerProtocol {
  // Messages from Workers
  case class WorkerCreated(worker: ActorRef)
  case class WorkerRequestsWork(worker: ActorRef)
  case class WorkIsDone(worker: ActorRef)
 
  // Messages to Workers
  case class WorkToBeDone(work: Any)
  case object WorkIsReady
  case object NoWorkToBeDone
}

object InfoMessages {
  case class QueueSizeChanged(size: Int)
  case class NumWorkersChanged(size: Int)
}

abstract class Worker(masterLocation: ActorPath)
  extends Actor with ActorLogging {
  import MasterWorkerProtocol._
 
  // We need to know where the master is
  val master = context.actorFor(masterLocation)
 
  // This is how our derivations will interact with us.  It
  // allows derivations to complete work asynchronously
  case class WorkComplete(result: Any)
  case class WorkCouldNotRun(result: Any)
  case class WorkFailed(result: Any)
 
  // Required to be implemented
  def doWork(workSender: ActorRef, work: Any): Unit
 
  // Notify the Master that we’re alive
  override def preStart() = master ! WorkerCreated(self)
 
  // This is the state we’re in when we’re working on something.
  // In this state we can deal with messages in a much more
  // reasonable manner
  def working(work: Any): Receive = {
    // Pass... we’re already working
    case WorkIsReady =>
    // Pass... we’re already working
    case NoWorkToBeDone =>
    // Pass... we shouldn’t even get this
    case WorkToBeDone(_) =>
      log.error("Yikes. Master told me to do work, while I’m working.")
    // Our derivation has completed its task
    case m@(WorkComplete(_)|WorkCouldNotRun(_)|WorkFailed(_)) =>
      m match {
        case WorkComplete(result) => log.info("Work is complete. Result {}.", result)
        case WorkCouldNotRun(result) => log.warning("Work could not be run: {}", result)
        case WorkFailed(result) => log.error("Work failed: {}", result)
      }
      master ! WorkIsDone(self)
      master ! WorkerRequestsWork(self)
      // We’re idle now
      context.become(idle)
  }
 
  // In this state we have no work to do. There really are only
  // two messages that make sense while we’re in this state, and
  // we deal with them specially here
  def idle: Receive = {
    // Master says there’s work to be done, let’s ask for it
    case WorkIsReady =>
      log.info("Requesting work")
      master ! WorkerRequestsWork(self)
    // Send the work off to the implementation
    case WorkToBeDone(work) =>
      log.info("Got work {}", work)
      doWork(sender, work)
      context.become(working(work))
    // We asked for it, but either someone else got it first, or
    // there’s literally no work to be done
    case NoWorkToBeDone =>
  }
 
  def receive = idle
}

abstract class Master extends Actor with ActorLogging {
  import MasterWorkerProtocol._
  import InfoMessages._
  import scala.collection.mutable.{Map, Queue}
 
  // Holds known workers and what they may be working on
  val workers = Map.empty[ActorRef, Option[Tuple2[ActorRef, Any]]]
  // Holds the incoming list of work to be done as well
  // as the memory of who asked for it
  val workQ = Queue.empty[Tuple2[ActorRef, Any]]
 
  // Notifies workers that there’s work available, provided they’re
  // not already working on something
  def notifyWorkers(): Unit = {
    if (!workQ.isEmpty) {
      workers.foreach { 
        case (worker, m) if (m.isEmpty) => worker ! WorkIsReady
        case _ =>
      }
    }
  }

  // Notifies the parent that the queue size changed
  def notifyQueueSize(): Unit = {
    context.parent ! QueueSizeChanged(workQ.size)
  }

  // Notifies the parent that the number of workers changed
  def notifyNumWorkers(): Unit = {
    context.parent ! NumWorkersChanged(workers.size)
  }
 
  private[this] def receiveProtocol: Receive = {
    // Worker is alive. Add it to the list, watch it for
    // death, and let it know if there’s work to be done
    case WorkerCreated(worker) =>
      log.info("Worker created: {}", worker)
      context.watch(worker)
      workers += (worker -> None)
      notifyNumWorkers()
      notifyWorkers()
 
    // A worker wants more work. If we know about it, it’s not
    // currently doing anything and we’ve got something to do,
    // give it to the worker.
    case WorkerRequestsWork(worker) =>
      log.info("Worker requests work: {}", worker)
      if (workers.contains(worker)) {
        if (workQ.isEmpty)
          worker ! NoWorkToBeDone
        else if (workers(worker) == None) {
          val (workSender, work) = workQ.dequeue()
          notifyQueueSize()
          workers += (worker -> Some(workSender -> work))
          notifyNumWorkers()
          // Use the special form of ‘tell’ that lets us supply
          // the sender
          worker.tell(WorkToBeDone(work), workSender)
        }
      }
 
    // Worker has completed its work and we can clear it out
    case WorkIsDone(worker) =>
      if (!workers.contains(worker))
        log.error("Blurgh! {} said it's done work but we didn't know about it", worker)
      else {
        workers += (worker -> None)
        notifyNumWorkers()
      }
 
    // A worker died. If it was doing anything then we need
    // to give it to someone else so we just add it back to the
    // master and let things progress as usual
    case Terminated(worker) =>
      if (workers.contains(worker) && workers(worker) != None) {
        log.error("Blurgh! {} died while processing {}", worker, workers(worker))
        // Send the work that it was doing back to ourselves for processing
        val (workSender, work) = workers(worker).get
        self.tell(work, workSender)
      }
      workers -= worker
      notifyNumWorkers()
  }
  
  def receiveWork: Receive
  
  def receive = receiveProtocol orElse receiveWork
  
  def doWork(work: Any) = {
    log.info("Queueing {}", work)
    workQ.enqueue(sender -> work)
    notifyQueueSize()
    notifyWorkers()
  }
}
