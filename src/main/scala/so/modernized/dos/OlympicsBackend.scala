package so.modernized.dos

import akka.actor.{Props, ActorRef, ActorSystem}
import com.typesafe.config.ConfigFactory
import scala.collection.mutable

/**
 * @author John Sullivan
 */
object OlympicsFranchise extends Franchise
object OlympicSynchManager extends SynchManager {
  var leader:ActorRef = null
  val slaves = mutable.ArrayBuffer[ActorRef]()
}

class OlympicsBackend(teams:Iterable[String], events:Iterable[String]) {

  private var nextId = 0
  private def getId = {
    val id = nextId
    nextId += 1
    id
  }

  val system = ActorSystem("olympics-backend",ConfigFactory.load("server"))


  val db = system.actorOf(ConcreteDB(teams, events, getId))
  val frontend1 = system.actorOf(ConcreteFrontend(db, getId))
  val frontend2 = system.actorOf(ConcreteFrontend(db, getId))

  val (leader, losers) = OlympicsFranchise.election

  OlympicSynchManager.leader = leader
  OlympicSynchManager.slaves ++= losers

  OlympicSynchManager.synchronize() //todo ensure synch is complete

}

object ConcreteDB {
  def apply(teamNames:Iterable[String], eventNames:Iterable[String], id:Int) = Props(new ConcreteDB(teamNames, eventNames, id))
}

class ConcreteDB(teamNames:Iterable[String], eventNames:Iterable[String], val id:Int) extends DBServer with Elector with SynchedClock {

  val teams = context.actorOf(TeamRoster(teamNames))
  val events = context.actorOf(EventRoster(eventNames))

  val franchise = OlympicsFranchise

  franchise.addElector(id, context.self)

  val manager = OlympicSynchManager
}

object ConcreteFrontend{
  def apply(dbPath:ActorRef, id:Int) = Props(new ConcreteFrontend(dbPath, id))
}

class ConcreteFrontend(val dbPath:ActorRef, val id:Int) extends FrontEndServer with Elector with SynchedClock {
  val franchise = OlympicsFranchise

  franchise.addElector(id, context.self)

  val manager = OlympicSynchManager

  def getTimestamp = getSynchedTime
}
