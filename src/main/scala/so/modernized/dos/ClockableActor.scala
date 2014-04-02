package so.modernized.dos

import akka.actor.Actor
import scala.collection.mutable

/**
 * @author John Sullivan
 */

case class TimedMessage(timestamp:Vector[Int], message:Any)

trait ClockableActor extends Actor {

  private var timedReceivers:mutable.ArrayBuffer[Actor.Receive] = new mutable.ArrayBuffer[Actor.Receive]()

  def addTimedReceiver(rec:Actor.Receive) {
    timedReceivers += rec
  }

  final def receive:Actor.Receive = {
    case TimedMessage(timestamp, message) =>
    // do time processing

  }
}