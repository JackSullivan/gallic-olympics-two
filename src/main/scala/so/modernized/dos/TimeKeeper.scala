package so.modernized.dos

import scala.collection.immutable
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.Random
import java.util.UUID

/**
 * Created by akobren on 4/3/14.
 */

case class VectorClock(val hostId: String, val clock: immutable.Map[String, Int])

/**
 * This clock manager helps decide when to send and receive message according to the causal ordering scheme
 */
trait ClockManager {
  private val id = UUID.randomUUID().toString

  private val myClock = new mutable.HashMap[String, Int].withDefaultValue(0)

  /**
   * Test whether or not a clock is ready to be processed -- a clock is only ready to be process when
   * the local clock has a greater count in all cells except for that corresponding to the
   * sender's id
   * @param vectorClock
   * @return
   */
  def isNextLogicalClock(vectorClock: VectorClock) = {
    vectorClock.clock.forall({ case (id, count) => {
      if (id != vectorClock.hostId) myClock(id) >= count
      else count == myClock(id) + 1
    }})
  }

  /**
   * Update the local clock by taking the max count between the incoming clock and the local clock
   * in the case of causally ordered multicasting, this should only update 1 count (the count of the sender)
   * @param vectorClock
   */
  def addToClock(vectorClock: VectorClock): Unit = {
    vectorClock.clock.foreach({case (id, count) => {

      /* the only count that should be greater than the local clock is that of the sender*/
      assert(if (id != vectorClock.hostId) myClock(id) >= count else count == myClock(id) + 1)
      myClock.update(id, math.max(myClock(id), count) )
    }})
  }

  def getClock: VectorClock = new VectorClock(id, myClock.toMap)

  def updateClock(senderId: String): VectorClock = {
    myClock.update(senderId, myClock(senderId) + 1)
    getClock
  }
}