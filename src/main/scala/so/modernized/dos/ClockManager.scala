package so.modernized.dos

import scala.collection.immutable
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.Random

/**
 * Created by akobren on 4/3/14.
 */

case class VectorClock(val hostId: String, val clock: immutable.Map[String, Int])

/**
 * This clock manager helps decide when to send and receive message according to the causal ordering scheme
 * @param hostId
 */
class ClockManager(val hostId: String) {
  private val myClock = new mutable.HashMap[String, Int].withDefaultValue(0)

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
    if (idxToCheck < clockQueue.length && isNextLogicalClock(clockQueue(idxToCheck))) {
      addClock(clockQueue(idxToCheck))
      clockQueue.remove(idxToCheck)
      processQueue(0)
    }

    else if (idxToCheck < clockQueue.length && !isNextLogicalClock(clockQueue(idxToCheck)))
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
      assert(if (id != vectorClock.hostId) myClock(id) >= count else count == myClock(id) + 1)

      myClock.update(id, math.max(myClock(id), count) )
    }})
  }

  /**
   * Since we are causally ordering the messages, they should be added to an internal queue and then processed
   * @param vectorClock
   */
  def receiveClock(vectorClock: VectorClock) = {
    clockQueue += vectorClock
    processQueue(clockQueue.length - 1)   // start checking the queue from the most recennt message
  }

  def getClock: VectorClock = new VectorClock(hostId, myClock.toMap)

  def recordLocalEvent: VectorClock = {
    myClock.update(hostId, myClock(hostId) + 1)
    getClock
  }

}

object LocalTester {

  def testClockEquivalence(c1: VectorClock, c2: VectorClock): Unit = {
    assert(c1.clock.forall({ case (id, count) => c2.clock(id) == count}) && c2.clock.forall({ case (id, count) => c1.clock(id) == count }))
  }

  def main(args: Array[String]) = {

    val rand = new Random

    val n1 = new ClockManager("1")
    val n2 = new ClockManager("2")

    (0 until 100).foreach({ _ =>
      if (rand.nextBoolean){
        n2.receiveClock(n1.recordLocalEvent)
      }

      if (rand.nextBoolean){
        n1.receiveClock(n2.recordLocalEvent)
      }

      testClockEquivalence(n1.getClock, n2.getClock)
    })

    println("N1 CLOCK: " + n1.getClock.clock.toString())
    println("N2 CLOCK: " + n2.getClock.clock.toString())

    // Now test with 3 people and late messages

    val p1 = new ClockManager("P1")
    val p2 = new ClockManager("P2")
    val p3 = new ClockManager("P3")

    testClockEquivalence(p1.getClock, p2.getClock)
    testClockEquivalence(p1.getClock, p3.getClock)

    val e1 = p1.recordLocalEvent                  // e1: (1, 0, 0)
    p2.receiveClock(e1)                           // p2: (1, 0, 0)

    val e2 = p2.recordLocalEvent                  // e2: (1, 1, 0)
    p1.receiveClock(e2)                           // p1: (1, 1, 0)
    p3.receiveClock(e2)                           // p3: (0, 0, 0)
    p3.receiveClock(e1)                           // p3: (1, 1, 0)

    testClockEquivalence(p1.getClock, p2.getClock)
    testClockEquivalence(p1.getClock, p3.getClock)




  }

}