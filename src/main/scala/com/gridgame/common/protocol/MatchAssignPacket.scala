package com.gridgame.common.protocol

import java.net.InetAddress
import java.util.{Base64, UUID}

/** The two parts of a MatchAssignPacket. */
object MatchAssignPart {
  /** Where the match is played: the match server's address and port, and the player's ticket. */
  val ADDRESS: Byte = 0
  /** Who the match server is: the fingerprint of its TLS key, which the client holds it to. */
  val IDENTITY: Byte = 1
}

/**
 * Where to play the match that is starting (server -> client only), when it runs on a match server of
 * its own rather than in the server the player is logged in to (design/networking.md, *Match
 * servers*). Sent on the session, so it is signed and inside TLS, in two parts, as the address, the
 * ticket and the key's fingerprint don't fit in one packet: ADDRESS, then IDENTITY. The client
 * connects once it has both: to that address, holding the server there to that key and no other, and
 * logs in with the ticket (AuthAction.MATCH_JOIN).
 *
 * {{{
 * [21]     part (MatchAssignPart)
 * ADDRESS:   [22] address length (4 or 16)  [23-38] the address  [39-40] port  [41-56] ticket  [57-63] zero
 * IDENTITY:  [22-53] the SHA-256 of the match server's TLS key (ServerIdentity.fingerprint's)   [54-63] zero
 * }}}
 */
class MatchAssignPacket(
    sequenceNumber: Int,
    playerId: UUID,
    timestamp: Int,
    val part: Byte,
    val address: InetAddress = null,
    val port: Int = 0,
    val ticket: Array[Byte] = null,
    val keyHash: Array[Byte] = null
) extends Packet(PacketType.MATCH_ASSIGN, sequenceNumber, playerId, timestamp) {

  def getPart: Byte = part
  def getAddress: InetAddress = address
  def getPort: Int = port
  def getTicket: Array[Byte] = ticket
  def getKeyHash: Array[Byte] = keyHash

  /** The match server's identity as a client pins one ("SHA256:..."). */
  def fingerprint: String = MatchAssignPacket.fingerprintOf(keyHash)

  override def serialize(): Array[Byte] = {
    val buffer = SerializeUtil.acquireBuffer()
    buffer.put(packetType.id)
    buffer.putInt(sequenceNumber)
    buffer.putLong(playerId.getMostSignificantBits)
    buffer.putLong(playerId.getLeastSignificantBits)
    buffer.put(part)
    if (part == MatchAssignPart.ADDRESS) {
      val raw = if (address != null) address.getAddress else new Array[Byte](4)
      buffer.put(raw.length.toByte)
      buffer.put(raw)
      buffer.put(new Array[Byte](16 - raw.length))
      buffer.putShort(port.toShort)
      MatchAssignPacket.putFixed(buffer, ticket, MatchAssignPacket.TicketSize)
      buffer.put(new Array[Byte](7))
    } else {
      MatchAssignPacket.putFixed(buffer, keyHash, MatchAssignPacket.KeyHashSize)
      buffer.put(new Array[Byte](10))
    }
    buffer.array().clone()
  }

  // Never the ticket: this is what a log line would print
  override def toString: String = s"MatchAssignPacket{seq=$sequenceNumber, part=$part}"
}

object MatchAssignPacket {
  val TicketSize = 16
  val KeyHashSize = 32
  private val Prefix = "SHA256:"

  def address(seq: Int, playerId: UUID, address: InetAddress, port: Int, ticket: Array[Byte]): MatchAssignPacket =
    new MatchAssignPacket(seq, playerId, Packet.getCurrentTimestamp, MatchAssignPart.ADDRESS, address, port, ticket)

  def identity(seq: Int, playerId: UUID, keyHash: Array[Byte]): MatchAssignPacket =
    new MatchAssignPacket(seq, playerId, Packet.getCurrentTimestamp, MatchAssignPart.IDENTITY, keyHash = keyHash)

  /** The key's hash a fingerprint ("SHA256:" and its base64) is of, or null if it isn't one. */
  def keyHashOf(fingerprint: String): Array[Byte] =
    if (fingerprint == null || !fingerprint.startsWith(Prefix)) null
    else try {
      val hash = Base64.getDecoder.decode(fingerprint.substring(Prefix.length))
      if (hash.length == KeyHashSize) hash else null
    } catch { case _: IllegalArgumentException => null }

  def fingerprintOf(keyHash: Array[Byte]): String =
    if (keyHash == null) null else Prefix + Base64.getEncoder.withoutPadding.encodeToString(keyHash)

  private def putFixed(buffer: java.nio.ByteBuffer, bytes: Array[Byte], size: Int): Unit = {
    val b = if (bytes != null) bytes else new Array[Byte](0)
    val n = Math.min(b.length, size)
    buffer.put(b, 0, n)
    buffer.put(new Array[Byte](size - n))
  }

  /** Read from the buffer at byte 21 (PacketSerializer). An address of any other length than 4 or 16
    * is malformed. */
  private[protocol] def read(seq: Int, playerId: UUID, buffer: java.nio.ByteBuffer): MatchAssignPacket = {
    val part = buffer.get()
    if (part == MatchAssignPart.ADDRESS) {
      val len = buffer.get() & 0xFF
      if (len != 4 && len != 16) throw new IllegalArgumentException(s"MATCH_ASSIGN: an address of $len bytes")
      val raw = new Array[Byte](16)
      buffer.get(raw)
      val address = InetAddress.getByAddress(java.util.Arrays.copyOf(raw, len))
      val port = buffer.getShort() & 0xFFFF
      val ticket = new Array[Byte](TicketSize)
      buffer.get(ticket)
      new MatchAssignPacket(seq, playerId, Packet.getCurrentTimestamp, part, address, port, ticket)
    } else {
      val hash = new Array[Byte](KeyHashSize)
      buffer.get(hash)
      new MatchAssignPacket(seq, playerId, Packet.getCurrentTimestamp, part, keyHash = hash)
    }
  }
}
