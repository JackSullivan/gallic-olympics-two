package so.modernized.dos

import akka.actor.{Props, Actor}

trait TeamMessageType {
  def initTime:Long
}
case class IncrementMedals(medalType:MedalType, initTime:Long) extends TeamMessageType
case class GetMedalTally(initTime:Long) extends TeamMessageType

case class TeamMessage(teamName:String, message:TeamMessageType)
case class UnknownTeam(teamName:String, initTime:Long)

case class MedalTally(team:String, gold:Int, silver:Int, bronze:Int, initTime:Long)

/**
 * The team object stores medal counts and responds to requests to
 * read and write to them. It relies on TeamRoster to ensure that
 * it receive the appropriate messages.
 */
object Team {
  def props(name:String): Props = Props(new Team(name))
}

class Team(val name:String) extends Actor {
  var gold:Int = 0
  var silver:Int = 0
  var bronze:Int = 0

  override def receive: Actor.Receive = {
    case IncrementMedals(medalType, time) => medalType match {
      case Gold => {
        gold += 1
        println ("one more gold for %s! That makes %d".format(name, gold))
      }
      case Silver => {
        silver += 1
        println ("one more silver for %s! That makes %d".format(name, silver))
      }
      case Bronze => {
        bronze += 1
        println ("one more bronze for %s! That makes %d".format(name, bronze))
      }
    }
    case GetMedalTally(time) => sender ! MedalTally(name, gold, silver, bronze, time)
  }
}

/**
 * The team roster serves as parents to all of the teams at the olympics
 * and routes incoming requests to read and write to the appropriate team.
 * If no team is found for a given request a message is sent back to the
 * requester to that effect.
 */
object TeamRoster{
  def apply(teams:Iterable[String]):Props = Props(new TeamRoster(teams))
}

class TeamRoster(teams:Iterable[String]) extends Actor {

  teams.foreach{ teamName =>
    context.actorOf(Team.props(teamName), teamName)
  }

  override def receive: Actor.Receive = {
    case TeamMessage(teamName, message) => context.child(teamName) match {
        case Some(team) => team.tell(message, sender())
        case None => sender() ! UnknownTeam(teamName, message.initTime)
    }
  }
}