package so.modernized.dos

import akka.actor.{ActorSystem, Props, ActorRef, Actor}
import akka.pattern.ask
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import akka.routing.Broadcast
import scala.collection.mutable
import com.typesafe.config.ConfigFactory
import akka.util.Timeout


case object CallElection
case object OkElection
case class LeaderElection(id:Int)
case class IWonElection(id:Int)

/**
 * @author John Sullivan
 */
trait Franchise {

  private var idMap = mutable.ArrayBuffer[(Int, ActorRef)]()

  def addElector(id:Int, elector:ActorRef) {
    idMap += id -> elector
    idMap = idMap.sortBy(_._1) // sortBy produces a new sequence
  }

  def getHigherIds(id:Int) = {
    val highSplit = idMap.splitAt(id)._2.map(_._2)
    if(highSplit.isEmpty) {
      highSplit
    } else {
      highSplit.tail
    }
  }

  def everyone:Iterable[ActorRef] = idMap.map(_._2)

  def election:(ActorRef, Iterable[ActorRef]) = {
    implicit val timeout = new Timeout(600.seconds)
    Await.result(idMap.head._2 ? CallElection, 600.seconds).asInstanceOf[(ActorRef, Iterable[ActorRef])]
  }
}

trait Elector extends SubclassableActor {
  def franchise:Franchise
  def id:Int
  def higherIds:Iterable[ActorRef] = franchise.getHigherIds(id)
  protected var winner:Option[ActorRef] = None

  def getLeader:ActorRef = winner match {
    case Some(w) => w
    case None => throw new Exception("No winner set")
  }
  private var electionCallerOpt:Option[ActorRef] = None

  addReceiver {
    case CallElection => {
      electionCallerOpt = Some(sender())
      higherIds.foreach {_ ! LeaderElection(id)}
    }
    case LeaderElection(callerId) => {
      println("%s received LeaderElection from %s".format(context.self, sender))
      if(callerId < id) {
        println("%s sending OkElection to %s".format(context.self, sender))
        sender ! OkElection
      }
      println("%d sending LeaderElection to %d higher ids".format(context.self, higherIds.size))
      if(higherIds.size == 0) {
        franchise.everyone.foreach { elector =>
          elector ! IWonElection(id)
        }
      } else {
        val futures = higherIds.map{ ref =>
          ask(ref, LeaderElection(id))(5.seconds)
        }
        Thread.sleep(5005)
        val higherUpExists = futures.flatMap{_.value.map(_.isSuccess)}.foldLeft(false)(_ || _)
        if (!higherUpExists) {
          println("%s is the winner!".format(context.self))
          franchise.everyone.foreach { elector =>
            elector ! IWonElection(id)
          }
        }
      }
    }
    case IWonElection(winnerId) => {
      println("%s received IWonElection from %s".format(context.self, sender))
      winner = Some(sender())
      electionCallerOpt match {
        case Some(electionCaller) => electionCaller ! (winner.get, franchise.everyone.filter(_ != winner.get))
        case None => Unit
      }
    }
    case OkElection => {
      println("%s received OkElection".format(context.self))
      Unit
    }
  }
}

object ElectorTest {

  object TestFranchise extends Franchise
  class TestElector(val id:Int) extends Elector {
    val electorType = "test"
    val franchise = TestFranchise

    franchise.addElector(id, this.self)

    addReceiver{
      case "winner set?" => sender ! winner.isDefined
      case "show winner" => sender ! winner.get
      case "call election" => sender ! getLeader
      case "get id" => sender ! id
    }
  }
  object TestElector {
    def apply(id:Int) = Props(new TestElector(id))
  }

  def main(args:Array[String]) {
    val system = ActorSystem("elector-test", ConfigFactory.load("server"))
    implicit val timeout = new Timeout(5.seconds)

    val e1 = system.actorOf(TestElector(0), "test0")
    val e2 = system.actorOf(TestElector(1), "test1")
    val e3 = system.actorOf(TestElector(2), "test2")

    val winnerSet1 = Await.result(e1 ? "winner set?", 5.seconds)
    val winnerSet2 = Await.result(e2 ? "winner set?", 5.seconds)
    val winnerSet3 = Await.result(e3 ? "winner set?", 5.seconds)

    println("winner is set e1: %s" format winnerSet1)
    println("winner is set e2: %s" format winnerSet2)
    println("winner is set e3: %s" format winnerSet3)

    println("0 higherIds: %s".format(TestFranchise.getHigherIds(0)))
    println("1 higherIds: %s".format(TestFranchise.getHigherIds(1)))
    println("2 higherIds: %s".format(TestFranchise.getHigherIds(2)))

    TestFranchise.election

    Thread.sleep(10000)
    println("Ok well I'm going anyway")

    val elected1 = Await.result(e1 ? "call election", 5.seconds).asInstanceOf[ActorRef]
    println("%s has %s as it's leader".format(e1, elected1))
    val elected2 = Await.result(e2 ? "call election", 5.seconds).asInstanceOf[ActorRef]
    println("%s has %s as it's leader".format(e2, elected2))
    val elected3 = Await.result(e3 ? "call election", 5.seconds).asInstanceOf[ActorRef]
    println("%s has %s as it's leader".format(e3, elected3))


    system.shutdown()
  }
}