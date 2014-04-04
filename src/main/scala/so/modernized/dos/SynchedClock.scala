package so.modernized.dos

import akka.actor._
import scala.collection.mutable
import scala.concurrent.duration._
import akka.util.Timeout
import scala.concurrent.Await
import akka.pattern.ask

/**
 * @author John Sullivan
 */

case object AddSynchMember
case object WelcomeToSynch

case object StartSynch
case object Synched
case object GetMembers
case class Synchronize(startTime:Long)
case class TimeSubmission(startTime:Long, myTime:Long)
case class TimeOffset(offset:Long)

class SynchManager extends Actor {

  private val members = mutable.ArrayBuffer[ActorRef]()

  def receive = {
    case AddSynchMember => {
      members += sender()
      sender() ! WelcomeToSynch
      sender() ! GetLeader
      println("added %s to sync management".format(sender()))
    }
      /*
    case StartSynch => {
      implicit val timeout = new Timeout(600.seconds)
      Await.result(leader ? StartSynch, 600.seconds)
    }
    */
    case GetMembers => sender() ! members
  }

}

trait SynchedClock extends SubclassableActor {
  implicit val timeout = new Timeout(600.seconds)
  def id:Int
  def manager:ActorRef
  def members = Await.result(manager ? GetMembers, 600.seconds).asInstanceOf[Iterable[ActorRef]]

  private var offset:Long = 0L // The different between my clock and the agreed clock time
  private var submittedTimes = mutable.ArrayBuffer[(ActorRef, Long)]()
  private var currentstartSync:Option[Long] = None

  def getSynchedTime:Long = {
    if(offset==0L) {
      println("WARNING! the time offset is 0, clocks may not have been synched")
    }
    System.currentTimeMillis() + offset
  }

  manager ! AddSynchMember

  addReceiver {
    case StartSynch => context.self ! GetLeader // to start syncing, you need to know your leader. Calling this will eventually result in all actors receiving a TheLeader message which will trigger the actual sync
    case TheLeader(leaderId) => if(id == leaderId) {
      println("%s is the leader is and is ready to start synching!".format(context.self))
      val synchStart = System.currentTimeMillis()
      members.filter(_ != context.self).foreach{_ ! Synchronize(synchStart)}
      currentstartSync = Some(synchStart)
    } else {
      println("%s know's who it's leader is, sadly its %d".format(context.self, leaderId))
    }
    case Synchronize(synchStart) => {
      val localTime = System.currentTimeMillis()
      println("%s received %s from %s at local time: %d".format(context.self, Synchronize(synchStart), sender(), localTime))
      sender ! TimeSubmission(synchStart, localTime)
    }
    case TimeSubmission(synchStart, slaveTime) => if(currentstartSync.isDefined && synchStart == currentstartSync.get) {
      val localTime = System.currentTimeMillis()
      println("%s received %s from %s at local time: %d".format(context.self, TimeSubmission(synchStart, slaveTime), sender(), localTime))
      val travelTime = (synchStart - localTime)/2
      submittedTimes += sender -> (slaveTime - travelTime)
      if(submittedTimes.size + 1 == members.size) {
        println("%s received all submissions".format(context.self))
        val unifiedTime = (submittedTimes.foldLeft(0L)(_ + _._2) + synchStart) / (submittedTimes.size + 1)
        submittedTimes.foreach { case(slave, adjSlaveTime) =>
          slave ! TimeOffset(unifiedTime - adjSlaveTime)
        }
        offset = unifiedTime - synchStart
        submittedTimes.clear()
        currentstartSync = None
      }
    }
    case TimeOffset(remOffset) => {
      val localTime = System.currentTimeMillis()
      println("%s received %s from %s at local time: %d".format(context.self, TimeOffset(remOffset), sender(), localTime))
      offset = remOffset
    }
  }
}


object SynchTest {
  object TestSynch{
    def apply(id:Int, franchise:ActorRef, manager:ActorRef) = Props(new TestSynch(id, franchise, manager))
  }

  class TestSynch(val id:Int, val franchise:ActorRef, val manager:ActorRef) extends Elector with SynchedClock

  def main(args:Array[String]) {
    val system = ActorSystem.apply("synchtest")


    val syncManager = system.actorOf(Props[SynchManager], "sync-manager")
    val franchise = system.actorOf(Props[Franchise], "franchise")

    val e1 = system.actorOf(TestSynch(0, franchise, syncManager), "test0")
    val e2 = system.actorOf(TestSynch(1, franchise, syncManager), "test1")
    val e3 = system.actorOf(TestSynch(2, franchise, syncManager), "test2")

    Thread.sleep(2000)

    val inbox = Inbox.create(system)

    inbox.send(e1, StartSynch)

    Thread.sleep(10000)

    system.shutdown()
  }
}