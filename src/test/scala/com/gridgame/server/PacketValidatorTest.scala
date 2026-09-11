package com.gridgame.server

import com.gridgame.common.Constants
import org.junit.Assert._
import org.junit.Test

import java.util.UUID

/** Replay protection: each session's packets carry increasing sequence numbers. */
class PacketValidatorTest {
  private val v = new PacketValidator()
  private val id = UUID.randomUUID()

  private def udp(seq: Int): Boolean = v.validateSequence(id, seq, isUdp = true)
  private def tcp(seq: Int): Boolean = v.validateSequence(id, seq, isUdp = false)

  @Test def tcpMustAlwaysMoveForward(): Unit = {
    assertTrue(tcp(1))
    assertTrue(tcp(5))
    assertFalse("a replay", tcp(5))
    assertFalse("from before", tcp(3))
    assertTrue(tcp(6))
  }

  @Test def udpMayArriveOutOfOrderButNotTwice(): Unit = {
    assertTrue(udp(10))
    assertTrue(udp(12))
    assertTrue("late, but new", udp(11))
    assertFalse("a replay", udp(11))
    assertFalse(udp(12))
  }

  @Test def udpFromTooFarBehindIsDropped(): Unit = {
    assertTrue(udp(5000))
    assertFalse(udp(5000 - Constants.SEQUENCE_WINDOW_SIZE - 10))
    assertTrue("within the window", udp(5000 - 10))
  }

  @Test def udpAndTcpAreCountedApart(): Unit = {
    // One counter on the client, two transports: UDP can run ahead of TCP
    assertTrue(udp(50))
    assertTrue(tcp(20))
    assertTrue(udp(21))
  }

  @Test def sequencesWrapAround(): Unit = {
    assertTrue(tcp(Int.MaxValue - 1))
    assertTrue(tcp(0x7FFFFFFF))
    assertTrue("past the wrap", tcp(0x80000000 & 0x7FFFFFFF))
    // A session's UDP count starts from zero and walks up to the wrap
    for (s <- Seq(0x2AAAAAAA, 0x55555554, 0x7FFFFFFE)) assertTrue(f"0x$s%x", udp(s))
    assertTrue("past the wrap", udp(5))
    assertFalse("and the far side of it is behind", udp(0x7FFFFFFE))
  }

  @Test def aNewSessionCountsAfresh(): Unit = {
    assertTrue(tcp(300))
    assertTrue(udp(300))
    v.resetSequences(id)
    assertTrue(tcp(1))
    assertTrue(udp(1))
  }

  @Test def playersAreCountedApart(): Unit = {
    val other = UUID.randomUUID()
    assertTrue(tcp(100))
    assertTrue(v.validateSequence(other, 1, isUdp = false))
  }
}
