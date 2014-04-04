package so.modernized.dos

import akka.actor._
import akka.routing.RoundRobinRoutingLogic
import akka.routing.Router
import akka.actor.Terminated
import akka.routing.ActorRefRoutee

/**
 * The TabletRequestRouter uses a round-robin system
 * to assign routing requests to workers that process those requests.
 * It also manages the creation and restarting of workers.
 */
class TabletRequestRouter extends Actor {
  var router = {
    val routees = Vector.fill(5) {
      val r = context.actorOf(Props[TabletRequestWorker])
      context watch r
      ActorRefRoutee(r)
    }
    Router(RoundRobinRoutingLogic(), routees)
  }

  def receive: Actor.Receive = {
    case Terminated(a) =>
      router = router.removeRoutee(a)
      val r = context.actorOf(Props[TabletRequestWorker])
      context watch r
      router = router.addRoutee(r)
    case message => router.route(message, sender())
  }
}

/**
 * A TabletRequestWorker takes requests from tablets and routes them to the
 * team or event roster as needed.
 */
class TabletRequestWorker extends Actor {
  val teamPath = context.system.actorSelection(context.system./("teams"))
  val eventPath = context.system.actorSelection(context.system./("events"))

  def receive: Actor.Receive = {
    case teamMessage:TeamMessage => teamPath.tell(teamMessage, sender())
    case eventMessage:EventMessage => eventPath.tell(eventMessage, sender())
  }
}

case class ClientRequest(request:AnyRef)
case class DBRequest(message:AnyRef, finalRoutee:ActorRef, serverRoutee:ActorRef)
case class DBResponse(response:AnyRef, finalRoutee:ActorRef, serverRoutee:ActorRef)

/**
 * The FrontEndServer trait routes read requests from
 */
trait FrontEndServer extends SubclassableActor {
  def dbPath:ActorRef

  addReceiver {
    case ClientRequest(message) => dbPath ! DBRequest(message, sender(), context.self)
    case DBResponse(response, routee, _) => routee ! response
  }
}

trait DBServer extends SubclassableActor {
  def teams:ActorRef
  def events:ActorRef

  addReceiver{
    case DBRequest(request, routee, server) => request match {
      case tm:TeamMessage => teams ! DBRequest(tm, routee, server)
      case em:EventMessage => events ! DBRequest(em, routee, server)
    }
    case DBResponse(response, routee, serverRoutee) => serverRoutee ! DBResponse(response, routee, serverRoutee)
  }
}