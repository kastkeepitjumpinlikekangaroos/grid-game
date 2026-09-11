package com.gridgame.common.protocol

import com.gridgame.common.Constants
import org.junit.Assert._
import org.junit.Test

import java.util.UUID

/** Every packet after login is signed with the session's token; the far end drops any that
  * don't verify. */
class PacketSignerTest {
  private def token(seed: Int): Array[Byte] = Array.tabulate[Byte](32)(i => (i * 31 + seed).toByte)
  private val payload = new HeartbeatPacket(42, UUID.randomUUID()).serialize()

  @Test def aSignedPacketVerifiesToItsPayload(): Unit = {
    val signed = PacketSigner.sign(payload, token(1))
    assertEquals(Constants.PACKET_SIZE, signed.length)
    assertArrayEquals(payload, PacketSigner.verify(signed, token(1)))
  }

  @Test def anyChangedByteFailsVerification(): Unit = {
    val signed = PacketSigner.sign(payload, token(1))
    for (i <- signed.indices) {
      val tampered = signed.clone()
      tampered(i) = (tampered(i) ^ 0x01).toByte
      assertNull(s"byte $i flipped", PacketSigner.verify(tampered, token(1)))
    }
  }

  @Test def anotherSessionsTokenFailsVerification(): Unit = {
    // A re-login issues a new token: the old session's packets must not verify under it
    assertNull(PacketSigner.verify(PacketSigner.sign(payload, token(1)), token(2)))
  }

  @Test def switchingTokensOnOneThreadSignsWithTheRightOne(): Unit = {
    // The signer caches its key per thread; the server signs for many players on one thread
    val a = PacketSigner.sign(payload, token(1))
    val b = PacketSigner.sign(payload, token(2))
    assertNotNull(PacketSigner.verify(a, token(1)))
    assertNotNull(PacketSigner.verify(b, token(2)))
    assertNull(PacketSigner.verify(a, token(2)))
    assertFalse(java.util.Arrays.equals(a, b))
  }

  @Test def aPacketOfTheWrongLengthDoesNotVerify(): Unit = {
    assertNull(PacketSigner.verify(new Array[Byte](Constants.PACKET_SIZE - 1), token(1)))
    assertNull(PacketSigner.verify(null, token(1)))
  }
}
