package so.modernized.dos

import akka.actor._
import scala.collection.{mutable}
import com.typesafe.config.ConfigFactory
import akka.routing.Broadcast
import akka.remote.RemoteScope
import akka.routing.{RoundRobinRoutingLogic, Router, ActorRefRoutee}
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.collection.mutable.ArrayBuffer

/**
 * @author John Sullivan
 */

case class TimedMessage(val clock: VectorClock, val message: Any)

case class Send(id: String, m: Any, ref: ActorSelection)

trait ClockableActor extends Actor with ClockManager {

  private var timedMessageQueue: mutable.ArrayBuffer[TimedMessage] = new mutable.ArrayBuffer[TimedMessage]

  final def receive: Actor.Receive = {
    case Send(id, m, ref) => {
      println("SENDING: " + m + " MY CLOCK IS CURRENTLY " + getClock.clock.toString())
      val t = new TimedMessage(getClock, m)
      ref ! t
    }

    case TimedMessage(clock, message) => {
      println("RECEIVING: " + message + " UPDATING " + getClock.clock.toString() + " WITH " + clock.clock.toString())
      updateClock(clock.hostId)
      timedMessageQueue += TimedMessage(clock, message)
      processMessageQueue(timedMessageQueue.length - 1)
    }

    case x => println("RECIEVED: " + x)
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

class ActorWithVectorClock extends ClockableActor

object CausalOrderTester {

  class TestActorSystem {
    val system = ActorSystem("testSystem", ConfigFactory.load("server.conf"))
    system.actorOf(Props[ActorWithVectorClock], "dbserver")

    def shutdown = system.shutdown()

  }

  def main(args: Array[String]) {
    println("CAUSE ORDER TESTER")
    val testSystem = new TestActorSystem
    Thread.sleep(20000)
  }
}
