package so.modernized.dos

import akka.actor.{ActorSystem, Props, ActorRef}
import scala.collection.mutable

/**
 * @author John Sullivan
 */

case object StartSynch
case class Synchronize(startTime:Long)
case class TimeSubmission(startTime:Long, myTime:Long)

case class TimeOffset(offset:Long)
trait SynchManager {
  def numSlaves = slaves.size
  def leader:ActorRef
  def slaves:Iterable[ActorRef]

  def synchronize() {
    leader ! StartSynch
  }
}

trait SynchedClock extends SubclassableActor {
  def manager:SynchManager

  private var offset:Long = 0L // The different between my clock and the agreed clock time
  private var submittedTimes = mutable.ArrayBuffer[(ActorRef, Long)]()

  def getSynchedTime:Long = {
    if(offset==0L) {
      println("WARNING! the time offset is 0, clocks may not have been synched")
    }
    System.currentTimeMillis() + offset
  }

  addReceiver {
    case StartSynch => {
      assert(context.self == manager.leader)
      println("%s received StartSynch".format(context.self))
      val synchStart = System.currentTimeMillis()
      manager.slaves.foreach(_ ! Synchronize(synchStart))
    }
    case Synchronize(synchStart) => {
      val localTime = System.currentTimeMillis()
      println("%s received %s from %s at local time: %d".format(context.self, Synchronize(synchStart), sender, localTime))
      sender ! TimeSubmission(synchStart, localTime)
    }
    case TimeSubmission(synchStart, slaveTime) => {
      val localTime = System.currentTimeMillis()
      println("%s received %s from %s at local time: %d".format(context.self, TimeSubmission(synchStart, slaveTime), sender, localTime))
      val travelTime = (synchStart - localTime)/2
      submittedTimes += sender -> (slaveTime - travelTime)
      if(submittedTimes.size == manager.numSlaves) {
        println("%s received all submissions".format(context.self))
        val unifiedTime = (submittedTimes.foldLeft(0L)(_ + _._2) + synchStart) / (submittedTimes.size + 1)
        submittedTimes.foreach { case(slave, adjSlaveTime) =>
          slave ! TimeOffset(unifiedTime - adjSlaveTime)
        }
        offset = unifiedTime - synchStart
      }
    }
    case TimeOffset(remOffset) => {
      val localTime = System.currentTimeMillis()
      println("%s received %s from %s at local time: %d".format(context.self, TimeOffset(remOffset), sender, localTime))
      offset = remOffset
    }
  }
}


object SynchTest {
  object TestSynchManager extends SynchManager {
    var leader:ActorRef = null
    val slaves = mutable.ArrayBuffer[ActorRef]()
  }
  class TestSynch extends SynchedClock {
    val manager = TestSynchManager
  }

  def main(args:Array[String]) {
    val system = ActorSystem.apply("synchtest")


    val leader = system.actorOf(Props[TestSynch], "leader")
    val s1 = system.actorOf(Props[TestSynch], "s1")
    val s2 = system.actorOf(Props[TestSynch], "s2")

    TestSynchManager.leader = leader
    TestSynchManager.slaves += s1
    TestSynchManager.slaves += s2

    TestSynchManager.synchronize()


    Thread.sleep(10000)

    system.shutdown()
  }
}
