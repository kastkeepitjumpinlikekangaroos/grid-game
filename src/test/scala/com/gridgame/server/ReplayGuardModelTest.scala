package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.server.net.ReplayGuard
import org.junit.Assert._
import org.junit.Test

import java.util.UUID
import scala.collection.mutable

/**
 * Replay protection over long runs of packet numbers, against a model of what it promises:
 *
 *  - over TCP, a packet is taken only if it is numbered past the last one taken;
 *  - over UDP, a packet is taken once, whenever it arrives, if it is no more than the window
 *    (SEQUENCE_WINDOW_SIZE) behind the newest taken, or anywhere ahead of it; the window slides up
 *    to whatever is taken ahead of it.
 *
 * The streams are a client's datagrams as a network delivers them: mostly in order, some late, some
 * twice, some lost, now and then a jump of thousands (packets sent over TCP in between, or a burst
 * lost), and a run across the wrap of the 31-bit count. PacketValidatorTest has the cases by hand;
 * this is the bitmap's shifting, which they barely touch.
 */
class ReplayGuardModelTest {
  private val W = Constants.SEQUENCE_WINDOW_SIZE

  /** What a sequence number means: a count that keeps going up, sent as the Int it wraps to. */
  private def wire(count: Long): Int = count.toInt

  /** The UDP promise, on the unwrapped counts. */
  private final class UdpModel {
    private val seen = mutable.HashSet[Long]()
    private var high = -1L
    def offer(count: Long): Boolean = {
      val base = Math.max(0L, high - (W - 1))
      val take = count >= base && !seen.contains(count)
      if (take) {
        seen += count
        high = Math.max(high, count)
        // Nothing below the window can be taken again: forget it, so the model stays small
        if (seen.size > 4 * W) seen.filterInPlace(_ >= Math.max(0L, high - (W - 1)))
      }
      take
    }
    def newest: Long = high
  }

  /** A client's datagrams as they arrive: a counter going up, with the network's disorder. */
  private def stream(rnd: scala.util.Random, start: Long, n: Int): Seq[Long] = {
    val out = mutable.ArrayBuffer[Long]()
    var next = start
    val sentRecently = mutable.ArrayBuffer[Long]()
    while (out.size < n) {
      rnd.nextInt(100) match {
        case r if r < 70 => // in order (some of what goes out is lost: never arrives)
          if (rnd.nextInt(10) != 0) out += next
          sentRecently += next
          next += 1
        case r if r < 82 && sentRecently.nonEmpty => // late: overtaken on the way
          out += sentRecently(sentRecently.size - 1 - rnd.nextInt(Math.min(sentRecently.size, 40)))
        case r if r < 88 && sentRecently.nonEmpty => // twice
          out += sentRecently(rnd.nextInt(sentRecently.size))
        case r if r < 93 => // very late: from before the window, or about its edge
          out += Math.max(start, next - W + rnd.nextInt(80) - 40)
        case r if r < 97 => // a gap: the counter ran on over TCP, or a run was lost
          next += 1 + rnd.nextInt(3 * W)
        case _ => // a long silence over UDP
          next += W * (1 + rnd.nextInt(50))
      }
      if (sentRecently.size > 4000) sentRecently.remove(0, 2000)
    }
    out.toSeq
  }

  private def checkUdp(seed: Long, start: Long, n: Int): Unit = {
    val rnd = new scala.util.Random(seed)
    val guard = new ReplayGuard
    val id = UUID.randomUUID()
    val model = new UdpModel
    var taken = 0
    for ((count, i) <- stream(rnd, start, n).zipWithIndex) {
      val expected = model.offer(count)
      val actual = guard.validateSequence(id, wire(count), isUdp = true)
      if (actual != expected)
        fail(s"seed $seed, datagram $i numbered $count (${wire(count)}): ${if (actual) "taken" else "dropped"}, " +
          s"where it should have been ${if (expected) "taken" else "dropped"} (the newest taken was ${model.newest})")
      if (actual) taken += 1
    }
    assertTrue(s"seed $seed took $taken of $n", taken > n / 3)
  }

  @Test def udpTakesEachDatagramOnceWithinItsWindowAndNoOtherwise(): Unit =
    for (seed <- 1L to 40L) checkUdp(seed, start = 0, n = 20000)

  @Test def udpStillDoesAcrossTheWrapOfTheCount(): Unit = {
    // The count is 31 bits: a session that has sent two billion datagrams wraps round to 0. Walked
    // up to the edge in strides the window takes as ahead, then across it with the network's disorder
    for (seed <- 100L to 110L) {
      val rnd = new scala.util.Random(seed)
      val guard = new ReplayGuard
      val id = UUID.randomUUID()
      val model = new UdpModel
      val edge = 0x80000000L - 3000
      var c = 0L
      while (c < edge) {
        assertTrue(s"stride to $c", guard.validateSequence(id, wire(c), isUdp = true))
        assertTrue(model.offer(c))
        c = Math.min(edge, c + (1L << 29) - rnd.nextInt(1000))
      }
      for ((count, i) <- stream(rnd, edge, 12000).zipWithIndex) {
        val expected = model.offer(count)
        val actual = guard.validateSequence(id, wire(count), isUdp = true)
        if (actual != expected)
          fail(s"seed $seed, datagram $i numbered $count (${wire(count)}) across the wrap: " +
            s"${if (actual) "taken" else "dropped"}, expected ${if (expected) "taken" else "dropped"}")
      }
    }
  }

  @Test def tcpTakesOnlyWhatIsNumberedPastTheLast(): Unit = {
    for (seed <- 200L to 220L) {
      val rnd = new scala.util.Random(seed)
      val guard = new ReplayGuard
      val id = UUID.randomUUID()
      var last = -1L
      var count = rnd.nextInt(100).toLong
      for (i <- 0 until 5000) {
        // Mostly the next, sometimes a resend of one already taken, sometimes a gap (UDP between)
        val sent = rnd.nextInt(10) match {
          case 0 if last >= 0 => last - rnd.nextInt(5)
          case 1 => count + rnd.nextInt(500)
          case _ => count
        }
        val expected = sent > last
        assertEquals(s"seed $seed, packet $i numbered $sent after $last", expected,
          guard.validateSequence(id, wire(sent), isUdp = false))
        if (expected) { last = sent; count = sent + 1 }
      }
    }
  }

  @Test def eachPlayerAndEachTransportIsCountedApart(): Unit = {
    // Interleaved, two players' streams and a player's TCP and UDP don't see each other's numbers
    val rnd = new scala.util.Random(7)
    val guard = new ReplayGuard
    val (a, b) = (UUID.randomUUID(), UUID.randomUUID())
    val models = Map(a -> new UdpModel, b -> new UdpModel)
    val streams = Map(a -> stream(rnd, 0, 8000).iterator, b -> stream(rnd, 0, 8000).iterator)
    val tcpLast = mutable.Map(a -> -1L, b -> -1L)
    var tcpNext = mutable.Map(a -> 0L, b -> 0L)
    for (_ <- 0 until 12000) {
      val who = if (rnd.nextBoolean()) a else b
      if (rnd.nextInt(5) == 0) {
        val sent = tcpNext(who)
        assertEquals(sent > tcpLast(who), guard.validateSequence(who, wire(sent), isUdp = false))
        tcpLast(who) = sent
        tcpNext(who) = sent + 1 + rnd.nextInt(3)
      } else if (streams(who).hasNext) {
        val count = streams(who).next()
        assertEquals(s"$count", models(who).offer(count), guard.validateSequence(who, wire(count), isUdp = true))
      }
    }
  }
}
