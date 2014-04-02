package so.modernized.dos

import akka.actor.{ActorSelection, Props}

/**
 * @author John Sullivan
 */
class FrontendServer(val teamPath:ActorSelection, val eventPath:ActorSelection) extends SubclassableActor {

  addReceiver {
    case teamMessage:TeamMessage => {
      println("cacofonix listener received a team message: %s" format teamMessage)
      teamPath ! teamMessage
    }
    case eventMessage:EventMessage => {
      println("cacofonix listener received an event message: %s" format eventMessage)
      eventPath ! eventMessage
    }
  }
}

object FrontendServer{
  def apply(teamPath:ActorSelection, eventPath:ActorSelection):Props = Props(new FrontendServer(teamPath, eventPath))
}

class FrontendManager(numServers:Int) extends SubclassableActor {
  val teamPath = context.system.actorSelection(context.system./("teams"))
  val eventPath = context.system.actorSelection(context.system./("events"))


  val servers = Vector.fill(numServers){
    val r = context.actorOf(FrontendServer(teamPath, eventPath))
    context watch r
    r
  }
}

object FrontendManager {
  def apply(numServers:Int):Props = Props(new FrontendManager(numServers))
}
