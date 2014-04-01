package so.modernized.dos

import akka.actor.{ActorSystem, ActorRef, Actor}
import akka.pattern.ask
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global


case object OkElection
case class LeaderElection(id:Long)
case class IWonElection(id:Long)

/**
 * @author John Sullivan
 */
trait Elector extends Actor{
  def electorType:String
  def id:Long
  def higherIds:Iterable[ActorRef]
  def electorate:Iterable[ActorRef]
  var winner:Option[ActorRef] = None

  def receive: Actor.Receive = {
    case LeaderElection(callerId) => {
      if(callerId < id) {
        sender ! OkElection
      } else {
        /*
        Future.sequence(higherIds.map{ ref =>
          ask(ref, LeaderElection(id))(5.seconds)
        })
        */
      }
    }
    case IWonElection(winnerId) => {
      winner = Some(sender())
    }
    case OkElection => Unit
  }
}


object AElector {

  def main(args:Array[String]) {

    val f1 = Future.successful("I won")
    val f2 = Future.successful("I also won")
    val f3 = Future.failed[String](new Exception())

    val futureSeq = Seq(f1, f2, f3)


    def isSuccess(f:Future[_]):Boolean = f.value.get.isInstanceOf[scala.util.Success[_]]

    val fSeq = Await.ready(Future.sequence(futureSeq), 5.seconds)



    //println(f3.value.get)

    val r1 = futureSeq.exists(isSuccess)
    val r2 = futureSeq.forall(isSuccess)


    //println("there exists a success: %s" format r1)
    //println("they are all successful: %s" format r2)


  }
}