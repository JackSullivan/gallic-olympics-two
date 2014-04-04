package so.modernized.dos

import akka.actor.{ActorRef, ActorSystem, Props}
import com.typesafe.config.ConfigFactory

/**
 * Created by akobren on 4/4/14.
 */
object VectorClockTest {
  case class SpecialMessage(s: String)

  object ActorWithVectorClock {
    def apply(id: Int, vcManager: ActorRef) = Props(new ActorWithVectorClock(id, vcManager))
  }

  class ActorWithVectorClock(val id: Int, val vcManager: ActorRef) extends VectorClockableActor {
    addReceiver{ case SpecialMessage(x) => Unit}
  }

  object ActorWithVectorClockPlus {
    def apply(id: Int, vcManager: ActorRef) = Props(new ActorWithVectorClockPlus(id, vcManager))
  }

  class ActorWithVectorClockPlus(val id: Int, val vcManager: ActorRef) extends VectorClockableActor {
    var received = 0
    addReceiver{case SpecialMessage(x) => received += 1; println("THIS IS MY: " + received + " MESSAGE: " + x)}
  }

  class DBServer {
    val system = ActorSystem("testSystem", ConfigFactory.load("server.conf"))
    val vcManager = system.actorOf(Props[VectorClockManager], "vc-manager")
    val client1 = system.actorOf(ActorWithVectorClock(1, vcManager), "client1")
    val client2 = system.actorOf(ActorWithVectorClock(2, vcManager), "client2")
    val client3 = system.actorOf(ActorWithVectorClockPlus(3, vcManager), "client3")

    def shutdown = system.shutdown()

  }

  def main(args: Array[String]) = {
    val dbServer = new DBServer

    dbServer.client1 ! SendInOrder(new SpecialMessage("MESSAGE 1 FROM 1"), null)
    dbServer.client1 ! SendInOrder(new SpecialMessage("MESSAGE 2 FROM 1"), null)
    dbServer.client2 ! SendInOrder(new SpecialMessage("MESSAGE 1 FROM 2"), null)
    dbServer.client1 ! SendInOrder(new SpecialMessage("MESSAGE 3 FROM 1"), null)
    dbServer.client2 ! SendInOrder(new SpecialMessage("MESSAGE 2 FROM 2"), null)
    dbServer.client2 ! SendInOrder(new SpecialMessage("MESSAGE 3 FROM 2"), null)
    dbServer.client1 ! SendInOrder(new SpecialMessage("MESSAGE 4 FROM 1"), null)
    dbServer.client1 ! SendInOrder(new SpecialMessage("MESSAGE 5 FROM 1"), null)
    dbServer.client2 ! SendInOrder(new SpecialMessage("MESSAGE 4 FROM 2"), null)
    dbServer.client1 ! SendInOrder(new SpecialMessage("MESSAGE 6 FROM 1"), null)
    dbServer.client2 ! SendInOrder(new SpecialMessage("MESSAGE 5 FROM 2"), null)
    Thread.sleep(5000)
    dbServer.shutdown

  }

}
