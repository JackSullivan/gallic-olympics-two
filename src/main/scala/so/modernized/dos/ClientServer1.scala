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
import so.modernized.dos.CausalOrderTester.ActorWithVectorClock

/**
 * Created by akobren on 4/4/14.
 */
/*
object ClientServer1Test {

  class ClientServer1 {
    val dbaddress = Address("akka.tcp", "testSystem", "127.0.0.1", 2552)
    val clientServer2Address = Address("akka.tcp", "clientserver", "127.0.0.1", 2555)

    val system = ActorSystem("clientserver", ConfigFactory.load("clientserver1.conf"))



    val clientServer = system.actorOf(ActorWithVectorClock(id, ), "clientserver")

    val dbserver = system.actorSelection(dbaddress.toString + "/user/dbserver")
    val clientServer2 = system.actorSelection(clientServer2Address.toString + "/user/clientserver")


    def broadcastMessage(m: String) = {
      clientServer ! new SendInOrder(m, dbserver)
      clientServer ! new SendInOrder(m, clientServer2)
    }

  }

  def main(args: Array[String]) = {
    val client1 = new ClientServer1

    Thread.sleep(10000)
    client1.broadcastMessage("I can't believe it's not butter")
    Thread.sleep(1000)
    client1.broadcastMessage("this is the true second message")
    println("Client 1 done sending messages fools")

    //    client2.shutdown()
  }

}
                                         */