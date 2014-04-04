package so.modernized.dos

import akka.actor._
import scala.collection.mutable
import com.typesafe.config.ConfigFactory
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Await
import akka.pattern.ask

/**
 * @author John Sullivan
 */

case class VectorClock(hostId: Int, clock: Map[Int, Int])
case class TimedMessage(clock: VectorClock, message: Any)

case class SendInOrder(m: Any, ref: ActorSelection)
case object AddToVectorClocking
case object GetClocked

class VectorClockManager extends Actor {
  private val clockees = mutable.ArrayBuffer[ActorRef]()
  
  
  def receive = {
    case AddToVectorClocking => {
      println("Added %s to Vector Clocking".format(sender()))
      clockees += sender()
    }
    case GetClocked => sender() ! clockees
  }
}

trait VectorClockableActor extends SubclassableActor {
  def vcManager:ActorRef
  def getClockees = {
    implicit val timeout = Timeout(600.seconds)
    Await.result(vcManager ? GetClocked, 600.seconds).asInstanceOf[Iterable[ActorRef]]
  }

  vcManager ! AddToVectorClocking

  def id:Int

  private val myClock = new mutable.HashMap[Int, Int].withDefaultValue(0)

  /**
   * Test whether or not a clock is ready to be processed -- a clock is only ready to be process when
   * the local clock has a greater count in all cells except for that corresponding to the
   * sender's id
   * @param vectorClock
   * @return
   */
  def isNextLogicalClock(vectorClock: VectorClock) = {
    vectorClock.clock.forall({ case (id, count) => {
      if (id != vectorClock.hostId) myClock(id) >= count
      else count == myClock(id) + 1
    }})
  }

  /**
   * Update the local clock by taking the max count between the incoming clock and the local clock
   * in the case of causally ordered multicasting, this should only update 1 count (the count of the sender)
   * @param vectorClock
   */
  def addToClock(vectorClock: VectorClock): Unit = {
    vectorClock.clock.foreach({case (id, count) => {

      /* the only count that should be greater than the local clock is that of the sender*/
      assert(if (id != vectorClock.hostId) myClock(id) >= count else count == myClock(id) + 1)
      myClock.update(id, math.max(myClock(id), count) )
    }})
  }

  def getClock: VectorClock = new VectorClock(id, myClock.toMap)

  def updateClock(senderId: Int = id): VectorClock = {
    myClock.update(senderId, myClock(senderId) + 1)
    getClock
  }


  private var timedMessageQueue: mutable.ArrayBuffer[TimedMessage] = new mutable.ArrayBuffer[TimedMessage]

  addReceiver {
    case SendInOrder(m, ref) => {
      println("SENDING: " + m + " MY CLOCK IS CURRENTLY " + getClock.clock.toString())
      updateClock(id)
      val t = new TimedMessage(getClock, m)
      getClockees.map(_ ! t)
    }

    case TimedMessage(clock, message) => {
      println("RECEIVING: " + message + " UPDATING " + getClock.clock.toString() + " WITH " + clock.clock.toString())
      updateClock(clock.hostId)
      timedMessageQueue += TimedMessage(clock, message)
      processMessageQueue(timedMessageQueue.length - 1)
    }
 
  }

  private def processMessageQueue(idxToCheck: Int): Unit = {
    if (idxToCheck < timedMessageQueue.length && isNextLogicalClock(timedMessageQueue(idxToCheck).clock)) {
      addToClock(timedMessageQueue(idxToCheck).clock)
      receive(timedMessageQueue(idxToCheck).message)
      timedMessageQueue.remove(idxToCheck)
      processMessageQueue(0)
    }

    else if (idxToCheck < timedMessageQueue.length && !isNextLogicalClock(timedMessageQueue(idxToCheck).clock))
      processMessageQueue(idxToCheck + 1)
  }

}

object CausalOrderTester {

  object ActorWithVectorClock {
    def apply(id:Int, vcManager:ActorRef) = Props(new ActorWithVectorClock(id, vcManager))
  }
  class ActorWithVectorClock(val id:Int, val vcManager:ActorRef) extends VectorClockableActor

  class TestActorSystem {
    val system = ActorSystem("testSystem", ConfigFactory.load("server.conf"))
    val vcManager = system.actorOf(Props[VectorClockManager], "vc-manager")
    val e1 = system.actorOf(ActorWithVectorClock(0, vcManager), "clockable-0")

    def shutdown = system.shutdown()

  }

  def main(args: Array[String]) {
    println("CAUSE ORDER TESTER")
    val testSystem = new TestActorSystem
    Thread.sleep(20000)
  }
}
