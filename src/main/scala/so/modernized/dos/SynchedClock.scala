package so.modernized.dos

import akka.actor.{ActorSystem, Props, ActorRef}
import scala.collection.mutable

/**
 * @author John Sullivan
 */
case object Synchronize
case class TimeSubmission(myTime:Long)
case class TimeOffset(sentTime:Long, trueTime:Long)

trait SynchManager {
  def numOfClocks:Int
}

trait SynchedClock extends SubclassableActor {
  def manager:SynchManager
  def leader:ActorRef

  private var offset:Long = 0L // The different between my clock and the agreed clock time
  private var submittedTimes = mutable.ArrayBuffer[(ActorRef, Long)]()

  def getSynchedTime:Long = {
    if(offset==0L) {
      println("WARNING! the time offset is 0, clocks may not have been synched")
    }
    System.currentTimeMillis() + offset
  }

  addReceiver{
    case Synchronize => {
      val timeStamp = System.currentTimeMillis()
      println("%s received Synchronize at %d".format(context.self, timeStamp))
      leader ! TimeSubmission(timeStamp)
    }
    case TimeOffset(sentTime, trueTime) =>
      val localTime = System.currentTimeMillis()
      val travelTime = (localTime - sentTime)/2 // this is half of the round trip time to respond, taken as the transit time that the original message took to arrive
      offset = (sentTime - trueTime) + travelTime
      println("%s received %s at local time %d. Calculated travel time at %d. Calculated offset at %d".format(context.self, TimeOffset(sentTime, trueTime), localTime, travelTime, offset))
    case TimeSubmission(submittedTime) => {
      println("%s received a %s from %s at local time %d".format(context.self, TimeSubmission(submittedTime), sender, System.currentTimeMillis()))
      submittedTimes += sender -> submittedTime
      if(submittedTimes.size == manager.numOfClocks) {
        println("%s has all relevant time submissions at %d".format(context.self, System.currentTimeMillis()))
        val trueTime = submittedTimes.map(_._2).sum / submittedTimes.size
        submittedTimes.foreach{ case(synchee, syncheeTime) =>
          synchee ! TimeOffset(syncheeTime, trueTime)
        }
      }
    }
  }
}


object SynchTest {
  object TestSynchManager extends SynchManager {
    val numOfClocks = 3
  }
  class TestSynch(var leader:ActorRef = null) extends SynchedClock {
    val manager = TestSynchManager

    if(leader == null) {
      leader = context.self
    }

  }

  object TestSynch{
    def apply(leader:ActorRef) = Props(new TestSynch(leader))
  }

  def main(args:Array[String]) {
    val system = ActorSystem.apply("synchtest")


    val leader = system.actorOf(TestSynch(null), "leader")
    val s1 = system.actorOf(TestSynch(leader), "s1")
    val s2 = system.actorOf(TestSynch(leader), "s2")

    leader ! Synchronize
    s1 ! Synchronize
    s2 ! Synchronize
  }
}
