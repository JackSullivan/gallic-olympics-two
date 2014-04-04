package so.modernized.dos

import akka.actor._
import scala.collection.{mutable}
import com.typesafe.config.ConfigFactory

/**
 * Created by akobren on 4/4/14.
 */
object ClientServer2Test {

  class ClientServer2 {
    val id = "cs2"
    val dbaddress = Address("akka.tcp", "testSystem", "127.0.0.1", 2552)
    val clientServer1Address = Address("akka.tcp", "clientserver", "127.0.0.1", 2557)

    val system = ActorSystem("clientserver", ConfigFactory.load("clientserver2.conf"))
    val clientserver = system.actorOf(Props[ActorWithVectorClock], "clientserver")

    val dbserver = system.actorSelection(dbaddress.toString + "/user/dbserver")
    val clientServer1 = system.actorSelection(clientServer1Address.toString + "/user/clientserver")

    Thread.sleep(2000)

    def broadcastMessage(m: String) = {
      clientserver ! new Send(id, m, dbserver)
      clientserver ! new Send(id, m, clientServer1)
    }

  }

  def main(args: Array[String]) = {
    val client2 = new ClientServer2
    Thread.sleep(10000)
    client2.broadcastMessage("this is the first message")
    Thread.sleep(5000)
    client2.broadcastMessage("this is the second message")
    Thread.sleep(5000)

    println("Client 2 finished sending messages fools")
    //    client2.shutdown()
  }

}