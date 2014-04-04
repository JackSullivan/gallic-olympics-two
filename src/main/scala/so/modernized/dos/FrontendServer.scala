package so.modernized.dos

import akka.actor.{ActorSystem, Props, ActorRef}
import com.typesafe.config.ConfigFactory
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Await

/**
 * @author John Sullivan
 */
case class ClientRequest(request:AnyRef)
case class DBWrite(message:AnyRef)
case class DBRequest(message:AnyRef, finalRoutee:ActorRef, serverRoutee:ActorRef)
case class DBResponse(response:AnyRef, finalRoutee:ActorRef, serverRoutee:ActorRef)
case class TimestampedResponse(timestamp:Long, response:AnyRef)

/**
 * The FrontEndServer trait routes read requests from table clients (and from Cacofonix
 * to the backend DBServer process, wrapping in such a way as to preserve information about
 * both the server through which it came and the original client to route it to.
 */
trait FrontendServer extends SubclassableActor {
  def dbPath:ActorRef

  def getSynchedTime:Long

  addReceiver {
    case m:DBWrite => dbPath ! m
    case ClientRequest(message) => {
      println("%s received ClientRequest(%s) from %s".format(context.self, message, sender()))
      dbPath ! DBRequest(message, sender(), context.self)
    }
    case DBResponse(response, routee, _) => {
      println("%s received %s from %s, routing to %s".format(context.self, response, sender(), routee))
      routee ! TimestampedResponse(getSynchedTime, response)
    }
  }
}

object ConcreteFrontend{
  def apply(dbPath:ActorRef, id:Int, franchise:ActorRef, manager:ActorRef, vcManager:ActorRef) = Props(new ConcreteFrontend(dbPath, id, franchise, manager, vcManager))
}

class ConcreteFrontend(val dbPath:ActorRef, val id:Int, val franchise:ActorRef, val manager:ActorRef, val vcManager:ActorRef) extends FrontendServer with Elector with SynchedClock with VectorClockableActor

object FrontendProcess {
  def main(args:Array[String]) {
    val remote = args(0)
    val id = args(1).toInt

    implicit val timeout = Timeout(600.seconds)

    val system = ActorSystem(s"frontend-$id", ConfigFactory.load(s"clientserver$id"))

    val db = Await.result(system.actorSelection(remote + "/user/db").resolveOne(), 600.seconds)
    val franchise = Await.result(system.actorSelection(remote + "/user/franchise").resolveOne(), 600.seconds)
    val manager = Await.result(system.actorSelection(remote + "/user/sync-manager").resolveOne(), 600.seconds)
    val vcManager = Await.result(system.actorSelection(remote + "/user/vc-manager").resolveOne(), 600.seconds)

    val frontend = system.actorOf(ConcreteFrontend(db, id, franchise, manager, vcManager), "frontend")
  }
}

