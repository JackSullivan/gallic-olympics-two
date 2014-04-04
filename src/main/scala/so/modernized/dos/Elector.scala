package so.modernized.dos

import akka.actor._
import akka.pattern.ask
import scala.concurrent.duration._
import scala.concurrent.{Future, Await}
import scala.collection.mutable
import com.typesafe.config.ConfigFactory
import akka.util.Timeout


case object OkElection
case class LeaderElection(id:Int)
case class IWonElection(id:Int)

case class AddElector(id:Int)
case class GetHigherIds(id:Int)
case class GetLowerIds(id:Int)
case object Enfranchisement
case object GetLeader
case class TheLeader(id:Int)

/**
 * @author John Sullivan
 */
class Franchise extends Actor {

  private var idMap = mutable.ArrayBuffer[(Int, ActorRef)]()

  private def addElector(id:Int, elector:ActorRef) {
    idMap += id -> elector
    idMap = idMap.sortBy(_._1) // sortBy produces a new sequence
  }

  private def getHigherIds(id:Int) = {
    val highSplit = idMap.splitAt(id)._2.map(_._2)
    if(highSplit.isEmpty) {
      highSplit
    } else {
      highSplit.tail
    }
  }
  private def getLowerIds(id:Int) = idMap.splitAt(id)._1.map(_._2)


  def receive = {
    case AddElector(id) => {
      println("added %s to the electorate".format(sender()))
      addElector(id, sender())
      sender() ! Enfranchisement
    }
    case GetHigherIds(id) => sender() ! getHigherIds(id)
    case GetLowerIds(id) => sender() ! getLowerIds(id)
  }

}

trait Elector extends SubclassableActor {
  def franchise:ActorRef
  def id:Int
  def higherIds:Iterable[ActorRef] = {
    implicit val timeout = new Timeout(600.seconds)
    Await.result(franchise ? GetHigherIds(id), 600.seconds).asInstanceOf[Iterable[ActorRef]]
  }
  def lowerIds:Iterable[ActorRef] = {
    implicit val timeout = new Timeout(600.seconds)
    Await.result(franchise ? GetLowerIds(id), 600.seconds).asInstanceOf[Iterable[ActorRef]]
  }
  protected var winnerId:Option[Int] = None
  private var requesterOpt:Option[ActorRef] = None

  franchise ! AddElector(id)

  addReceiver {
    case GetLeader => {
      println("%s received GetLeader from %s".format(context.self, sender()))
      winnerId match {
        case Some(wId) => sender() ! TheLeader(wId)
        case None => {
          println("%s sending LeaderElection to %s".format(context.self, higherIds))
          requesterOpt = Some(sender())
          higherIds.foreach(_ ! LeaderElection(id))
        }
      }
    }
    case LeaderElection(callerId) => {
      println("%s received LeaderElection from %s".format(context.self, sender()))
      if(callerId < id) {
        println("%s sending OkElection to %s".format(context.self, sender()))
        sender ! OkElection
      }
      println("%s sending LeaderElection to %d higher ids".format(context.self, higherIds.size))
      if(higherIds.size == 0) {
        context.self ! TheLeader(id)
        lowerIds.foreach { elector =>
          elector ! IWonElection(id)
        }
      } else {

        val futures = higherIds.map{ ref =>
          Await.ready(ask(ref, LeaderElection(id))(5.seconds), 5.seconds).asInstanceOf[Future[OkElection.type]]
        }
        val higherUpExists = futures.flatMap{_.value.map(_.isSuccess)}.foldLeft(false)(_ || _)
        if (!higherUpExists) {
          println("%s is the winner!".format(context.self))
          context.self ! TheLeader(id)
          lowerIds.foreach { elector =>
            elector ! IWonElection(id)
          }
        }
      }
    }
    case IWonElection(wId) => {
      println("%s received IWonElection from %s".format(context.self, sender()))
      requesterOpt match {
        case Some(requester) => {
          requester ! TheLeader(wId)
          requesterOpt = None
        }
        case None => Unit
      }
      winnerId = Some(wId)
    }
    case OkElection => {
      println("%s received OkElection".format(context.self))
      Unit
    }
  }
}

object ElectorTest {

  object TestFranchise extends Franchise
  class TestElector(val id:Int, val franchise:ActorRef) extends Elector
  object TestElector {
    def apply(id:Int, franchise:ActorRef) = Props(new TestElector(id, franchise))
  }

  class ResultPrinter extends Actor{
    def receive = {
      case TheLeader(id) => println("Elected %d as leader".format(id))
    }
  }

  def main(args:Array[String]) {
    val system = ActorSystem("elector-test", ConfigFactory.load("server"))
    implicit val timeout = new Timeout(5.seconds)


    val franchise = system.actorOf(Props[Franchise], "franchise")
    val e1 = system.actorOf(TestElector(0, franchise), "test0")
    val e2 = system.actorOf(TestElector(1, franchise), "test1")
    val e3 = system.actorOf(TestElector(2, franchise), "test2")

    Thread.sleep(2000)

    val inbox = Inbox.create(system)

    inbox.send(e1, GetLeader)

    val TheLeader(leaderId) = inbox.receive(600.seconds).asInstanceOf[TheLeader]


    println("Elected %d as leader".format(leaderId))
    Thread.sleep(1000)


    system.shutdown()
  }
}