package com.gridgame.common.protocol

import com.gridgame.common.Constants
import org.junit.Assert._
import org.junit.Test

/**
 * The wire against whatever arrives on it. Both ends read every packet with PacketSerializer, and
 * what a broken or hostile client sends the server, or a server the client, is whatever bytes it
 * likes: each must either be read as a packet or refused as malformed (an IllegalArgumentException,
 * which the handlers count and drop), never anything else, which escapes to whatever called. And a
 * packet once read is what it says it is: written back out and read again, it is the same packet.
 * PacketRoundTripTest holds every field of every type; this throws everything else at them.
 */
class PacketFuzzTest {
  private val rnd = new scala.util.Random(64)
  private val types: Array[Byte] = Array.tabulate(0x14)(i => (i + 1).toByte)

  /** 64 bytes as anyone might send them: a real packet type mostly, and the rest at random, some of
    * it zeros, some of it all ones, and some of it the bytes a packet's fields expect. */
  private def payload(): Array[Byte] = {
    val b = new Array[Byte](Constants.PACKET_PAYLOAD_SIZE)
    rnd.nextInt(4) match {
      case 0 => rnd.nextBytes(b)
      case 1 => java.util.Arrays.fill(b, 0xFF.toByte); for (i <- 0 until rnd.nextInt(8)) b(rnd.nextInt(b.length)) = rnd.nextInt(256).toByte
      case 2 => for (i <- 0 until rnd.nextInt(16)) b(rnd.nextInt(b.length)) = rnd.nextInt(256).toByte
      case _ => rnd.nextBytes(b); for (i <- 21 until b.length if rnd.nextBoolean()) b(i) = rnd.nextInt(8).toByte
    }
    b(0) = if (rnd.nextDouble() < 0.85) types(rnd.nextInt(types.length)) else rnd.nextInt(256).toByte
    b
  }

  /** The packet in these bytes; None if they are refused as malformed. Anything else is a failure. */
  private def read(bytes: Array[Byte]): Option[Packet] =
    try Some(PacketSerializer.deserialize(bytes))
    catch {
      case _: IllegalArgumentException => None
      case e: Throwable =>
        throw new AssertionError(s"${e.getClass.getSimpleName} reading ${hex(bytes)}", e)
    }

  private def hex(b: Array[Byte]): String = b.map(x => f"${x & 0xFF}%02x").mkString

  @Test def anyBytesAreReadAsAPacketOrRefusedAsMalformed(): Unit = {
    var read_ = 0
    for (_ <- 0 until 200000) if (read(payload()).isDefined) read_ += 1
    assertTrue(s"only $read_ read as packets", read_ > 100000)
  }

  @Test def aPacketReadFromAnyBytesSaysWhatItIs(): Unit = {
    for (_ <- 0 until 50000) {
      val bytes = payload()
      read(bytes).foreach { p =>
        if (p.getType.id != bytes(0) || p.serialize().length != Constants.PACKET_PAYLOAD_SIZE || p.getPlayerId == null)
          fail(s"${hex(bytes)} read as $p")
      }
    }
  }

  @Test def aPacketOnceReadIsTheSamePacketWrittenAndReadAgain(): Unit = {
    // Reading may tidy a field (a name cut at its first zero, a position off the grid put at the
    // corner, an action's unused bytes dropped, bytes that are no UTF-8 read as "�"); after that,
    // writing and reading change nothing. Text used to be cut at a byte count, so a character of
    // two or three bytes at the end of a field came back as half of one, and then as "�"
    // (SerializeUtil.putText). The login's name and password still are, as the account database
    // holds passwords by that cut (and a name is ASCII): they settle a write later.
    var checked = 0
    for (_ <- 0 until 100000) {
      val bytes = payload()
      read(bytes).foreach { p =>
        def rewritten(b: Array[Byte]): Array[Byte] =
          read(b).getOrElse(throw new AssertionError(s"${hex(bytes)} was read, but not what it wrote: ${hex(b)}")).serialize()
        val login = p.getType == PacketType.AUTH_REQUEST
        val once = if (login) rewritten(p.serialize()) else p.serialize()
        val twice = rewritten(once)
        if (!java.util.Arrays.equals(once, twice))
          fail(s"read from ${hex(bytes)}: written ${hex(once)}, then ${hex(twice)}")
        checked += 1
      }
    }
    assertTrue(checked > 50000)
  }

  @Test def textIsCutAtTheLastWholeCharacterThatFits(): Unit = {
    // A lobby's name a byte too long for its 24 in Korean: cut at a byte count it ended in half
    // a character, which the far end read as "�"
    val name = "a" + "가나다라마바사아" // 1 + 8 * 3 = 25 bytes
    val read = PacketSerializer.deserialize(new LobbyActionPacket(1, java.util.UUID.randomUUID(), LobbyAction.CREATE,
      7.toShort, 0.toByte, 5.toByte, 1.toByte, 8.toByte, 0.toByte, name).serialize()).asInstanceOf[LobbyActionPacket]
    assertEquals("a가나다라마바사", read.getLobbyName)
    // and a chat line of 15 three-byte characters in its 42
    val line = "가" * 15
    val chat = PacketSerializer.deserialize(new ChatMessagePacket(1, java.util.UUID.randomUUID(),
      Packet.getCurrentTimestamp, 0.toByte, line).serialize()).asInstanceOf[ChatMessagePacket]
    assertEquals("가" * 14, chat.getMessage)
  }

  @Test def everyTypeTakesItsOwnBytes(): Unit = {
    // Of every type, with the rest of its 64 bytes anything at all
    for (t <- types; _ <- 0 until 5000) {
      val bytes = new Array[Byte](Constants.PACKET_PAYLOAD_SIZE)
      rnd.nextBytes(bytes)
      bytes(0) = t
      read(bytes)
    }
  }

  @Test def onlySixtyFourBytesAreAPacket(): Unit = {
    for (n <- Seq(0, 1, 21, 63, 65, 80, 200)) {
      val bytes = new Array[Byte](n)
      rnd.nextBytes(bytes)
      if (n > 0) bytes(0) = PacketType.PLAYER_UPDATE.id
      assertEquals(s"$n bytes", None, read(bytes))
    }
    assertEquals(None, read(null))
  }

  // --- Signing ---

  private def token(): Array[Byte] = { val t = new Array[Byte](32); rnd.nextBytes(t); t }

  @Test def aSignatureIsCheckedWhateverArrivesAndOnlyTheRightOnePasses(): Unit = {
    val key = token()
    for (_ <- 0 until 20000) {
      val n = rnd.nextInt(5) match {
        case 0 => rnd.nextInt(200)
        case _ => Constants.PACKET_SIZE
      }
      val bytes = new Array[Byte](n)
      rnd.nextBytes(bytes)
      // Random bytes carry a valid signature once in 2^128
      assertNull(PacketSigner.verify(bytes, key))
    }
  }

  @Test def aSignedPacketChangedAnywhereIsRefused(): Unit = {
    for (_ <- 0 until 5000) {
      val key = token()
      val body = payload()
      val signed = PacketSigner.sign(body, key)
      assertArrayEquals(body, PacketSigner.verify(signed, key))
      val tampered = signed.clone()
      val flips = 1 + rnd.nextInt(4)
      for (_ <- 0 until flips) {
        val i = rnd.nextInt(tampered.length)
        tampered(i) = (tampered(i) ^ (1 + rnd.nextInt(255))).toByte
      }
      if (!java.util.Arrays.equals(tampered, signed)) assertNull(PacketSigner.verify(tampered, key))
      assertNull("signed by another session", PacketSigner.verify(signed, token()))
    }
  }
}
