package so.modernized.dos

import akka.actor._
import akka.routing.RoundRobinRoutingLogic
import akka.routing.Router
import akka.actor.Terminated
import akka.routing.ActorRefRoutee
import scala.util.Random

/**
 * The TabletRequestRouter uses a round-robin system
 * to assign routing requests to workers that forward those requests
 * to one of the two Frontend servers. It also manages the creation 
 * and restarting of workers.
 */
class RequestRouter(val servers:IndexedSeq[ActorRef]) extends Actor {
  var router = {
    val routees = Vector.fill(5) {
      val r = context.actorOf(RequestWorker(servers))
      context watch r
      ActorRefRoutee(r)
    }
    Router(RoundRobinRoutingLogic(), routees)
  }

  def receive: Actor.Receive = {
    case Terminated(a) =>
      router = router.removeRoutee(a)
      val r = context.actorOf(RequestWorker(servers))
      context watch r
      router = router.addRoutee(r)
    case message => router.route(message, sender())
  }
}

trait WriteMessage

/**
 * A TabletRequestWorker takes requests from tablets and routes them to the
 * team or event roster as needed.
 */
object RequestWorker {
  def apply(servers:IndexedSeq[ActorRef]) = Props(new RequestWorker(servers))
}

class RequestWorker(val servers:IndexedSeq[ActorRef]) extends Actor {
  val rand = new Random()
  def routee = servers(rand.nextInt(servers.size))
  
  def receive: Actor.Receive = {
    case wm:WriteMessage => servers.head.tell(wm, sender())
    case message => routee.tell(message, sender())    
  }
}