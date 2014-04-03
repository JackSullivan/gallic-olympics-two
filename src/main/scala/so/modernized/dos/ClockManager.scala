package so.modernized.dos

import scala.collection.immutable
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
 * Created by akobren on 4/3/14.
 */

case class VectorClock(val hostId: String, val clock: immutable.HashMap[String, Int])

class ClockManager(hostId: String) {
  private val myClock = new mutable.HashMap[String, Int].withDefault(0)

  private val clockQueue = new ArrayBuffer[VectorClock]

  /**
   * Test whether or not a clock is ready to be processed -- a clock is only ready to be process when
   * the local clock has a greater count in all cells except for that corresponding to the
   * sender's id
   * @param vectorClock
   * @return
   */
  private def isNextLogicalClock(vectorClock: VectorClock) = {
    vectorClock.clock.forall({ case (id, count) => {
      if (id != vectorClock.hostId) myClock(id) >= count
      else count == myClock(id) + 1
    }})
  }

  private def processQueue(idxToCheck: Int): Unit = {
    if (idxToCheck <= clockQueue.length && isNextLogicalClock(clockQueue(idxToCheck))) {
      addClock(clockQueue(idxToCheck))
      clockQueue.remove(idxToCheck)
      processQueue(0)
    }

    else if (idxToCheck <= clockQueue.length && !isNextLogicalClock(clockQueue(idxToCheck)))
      processQueue(idxToCheck + 1)
  }

  /**
   * Update the local clock by taking the max count between the incoming clock and the local clock
   * in the case of causally ordered multicasting, this should only update 1 count (the count of the sender)
   * @param vectorClock
   */
  private def addClock(vectorClock: VectorClock): Unit = {
    vectorClock.clock.foreach({case (id, count) => {

      /* the only count that should be greater than the local clock is that of the sender*/
      assert(if (id != vectorClock.hostId) myClock(id) >= count)

      myClock.update(id, math.max(myClock(id), count) )
    }})
  }

  def receiveClock(vectorClock: VectorClock) = {
    clockQueue += vectorClock
    processQueue(clockQueue.length - 1)   // start checking the queue from the most recennt message
  }

  def getClock: VectorClock = new VectorClock(hostId, myClock.toMap)

}
