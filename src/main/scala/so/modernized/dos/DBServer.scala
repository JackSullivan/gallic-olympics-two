package so.modernized.dos

import akka.actor.{Inbox, ActorSystem, Props, ActorRef}
import com.typesafe.config.ConfigFactory

/**
 * @author John Sullivan
 */

trait DBServer extends SubclassableActor {
  def teams:ActorRef
  def events:ActorRef

  addReceiver{
    case DBWrite(write) => write match {
      case tm:TeamMessage => teams ! DBWrite(tm)
      case em:EventMessage => events ! DBWrite(em)
    }
    case DBRequest(request, routee, server) => request match {
      case tm:TeamMessage => teams ! DBRequest(tm, routee, server)
      case em:EventMessage => events ! DBRequest(em, routee, server)
    }
    case DBResponse(response, routee, serverRoutee) => serverRoutee ! DBResponse(response, routee, serverRoutee)
  }
}

object ConcreteDB {
  def apply(teamNames:Iterable[String], eventNames:Iterable[String], id:Int, franchise:ActorRef, manager:ActorRef, vcManager:ActorRef) = Props(new ConcreteDB(teamNames, eventNames, id, franchise, manager, vcManager))
}

class ConcreteDB(teamNames:Iterable[String], eventNames:Iterable[String], val id:Int, val franchise:ActorRef, val manager:ActorRef, val vcManager:ActorRef) extends DBServer with Elector with SynchedClock with VectorClockableActor {
  val teams = context.actorOf(TeamRoster(teamNames))
  val events = context.actorOf(EventRoster(eventNames))
}

object DBProcess {

  def main(args:Array[String]) {
    val teams = args(0).split('|')
    val events = args(1).split('|')
    val id = args(2).toInt

    val system = ActorSystem("db", ConfigFactory.load("db"))

    val franchise = system.actorOf(Props[Franchise], "franchise")
    val manager = system.actorOf(Props[SynchManager], "sync-manager")
    val vcManager = system.actorOf(Props[VectorClockManager], "vc-manager")

    val db = system.actorOf(ConcreteDB(teams, events, id, franchise, manager, vcManager), "db")

    val syncInbox = Inbox.create(system)

    while(true) {
      syncInbox.send(db, StartSynch)
      Thread.sleep(60000)
    }
  }
}