package com.gridgame.common.protocol

import java.nio.ByteBuffer
import java.util.UUID

object ProblemReportAction {
  /** Client -> server, on its session: a report of `size` bytes is ready to send (design/diagnostics.md). */
  val BEGIN: Byte = 0
  /** Server -> client, on the session: send it, on an upload connection of its own, with this ticket. On
    * that connection: the ticket is good, and the report's bytes may come. */
  val READY: Byte = 1
  /** Server -> client: not now, and why (ProblemReportRefusal); on either connection. */
  val REFUSED: Byte = 2
  /** Server -> client: the report is kept, under its code. On the upload connection once all of it has
    * come, or on the session for a report the server has already. */
  val ACCEPTED: Byte = 3
  /** Server -> client, on the upload connection: what came wasn't the report BEGIN described (too few
    * bytes, too many, or not the ones its CRC says). Sending it again may work. */
  val FAILED: Byte = 4
}

/** What a report is about. */
object ProblemReportKind {
  /** A match the player has just played (or left, or had closed by an error). */
  val MATCH: Byte = 0
  /** The run before this one, which ended without closing properly. */
  val CRASH: Byte = 1
  /** Anything else: the menus, logging in, the game in general. */
  val GENERAL: Byte = 2
}

/** Why the server refused a report, or failed to take one in (REFUSED, FAILED). */
object ProblemReportRefusal {
  /** The account has sent as many as the server takes for now: try again after the wait it says. */
  val TOO_MANY: Byte = 1
  /** Bigger than the server takes. */
  val TOO_BIG: Byte = 2
  /** The server has no room left for reports. */
  val STORAGE_FULL: Byte = 3
  /** As many uploads under way as the server takes at once, or one of this player's already: try again
    * after the wait it says. */
  val BUSY: Byte = 4
  /** Upload connection: no such ticket (used, run out, or never given). */
  val BAD_TICKET: Byte = 5
  /** FAILED: the upload ended, or went quiet, before all of it came. */
  val TRUNCATED: Byte = 6
  /** FAILED: the bytes that came aren't the ones BEGIN's CRC-32 described, or more came than it said. */
  val CORRUPT: Byte = 7
  /** The server couldn't keep it (its disk), or doesn't take reports at all. */
  val UNAVAILABLE: Byte = 8
}

/**
 * A player's problem report, as far as the session goes: the client says one is ready (BEGIN), the
 * server gives it a ticket (READY) for an upload connection of its own, or refuses it (REFUSED). The
 * report itself never travels in packets: the client opens a second TLS connection to the server, logs
 * it in with the ticket (AUTH_REQUEST, AuthAction.UPLOAD), and once that connection hears READY, streams
 * the report's bytes down it, raw; the server answers ACCEPTED with the report's code, or FAILED, and
 * closes it (design/diagnostics.md, *Sending a report*).
 *
 * {{{
 * [21]     action (ProblemReportAction)
 * [22-37]  the report's id (the client's UUID for it: a report sent twice is kept once)
 * BEGIN:    [38-41] its size in bytes  [42-45] its CRC-32  [46-61] the match it is about (zero: none)  [62] kind  [63] zero
 * READY:    [38-53] the upload ticket (16 bytes; zero in the upload connection's go-ahead)               [54-63] zero
 * REFUSED:  [38] why (ProblemReportRefusal)  [39-42] seconds before it is worth trying again (0: no point) [43-63] zero
 * ACCEPTED: [38-49] the report's code (text, "GG-" and eight letters and digits)                          [50-63] zero
 * FAILED:   [38] why (ProblemReportRefusal)                                                               [39-63] zero
 * }}}
 */
class ProblemReportPacket(
    sequenceNumber: Int,
    playerId: UUID,
    timestamp: Int,
    val action: Byte,
    val reportId: UUID,
    val size: Int = 0,
    val crc: Int = 0,
    val matchId: UUID = null,
    val kind: Byte = 0,
    val ticket: Array[Byte] = null,
    val reason: Byte = 0,
    val retryAfterSeconds: Int = 0,
    val code: String = ""
) extends Packet(PacketType.PROBLEM_REPORT, sequenceNumber, playerId, timestamp) {

  def getAction: Byte = action
  def getReportId: UUID = reportId
  /** BEGIN: how many bytes the report is. */
  def getSize: Int = size
  /** BEGIN: the CRC-32 of the report's bytes. */
  def getCrc: Int = crc
  /** BEGIN: the match the report is about, or null. */
  def getMatchId: UUID = matchId
  /** BEGIN: what it is about (ProblemReportKind). */
  def getKind: Byte = kind
  /** READY: the ticket the upload connection logs in with (16 bytes; zero in the connection's go-ahead). */
  def getTicket: Array[Byte] = if (ticket != null) ticket else new Array[Byte](ProblemReportPacket.TicketSize)
  /** REFUSED, FAILED: why (ProblemReportRefusal). */
  def getReason: Byte = reason
  /** REFUSED: how long before trying again is worth it, in seconds; 0 when it isn't. */
  def getRetryAfterSeconds: Int = retryAfterSeconds
  /** ACCEPTED: the code the server keeps the report under. */
  def getCode: String = code

  override def serialize(): Array[Byte] = {
    val buffer = SerializeUtil.acquireBuffer()

    // [0] Packet Type
    buffer.put(packetType.id)

    // [1-4] Sequence Number
    buffer.putInt(sequenceNumber)

    // [5-20] Player ID (UUID)
    buffer.putLong(playerId.getMostSignificantBits)
    buffer.putLong(playerId.getLeastSignificantBits)

    // [21] Action
    buffer.put(action)

    // [22-37] The report's id
    ProblemReportPacket.putId(buffer, reportId)

    action match {
      case ProblemReportAction.BEGIN =>
        // [38-41] size, [42-45] CRC-32, [46-61] the match, [62] kind
        buffer.putInt(size)
        buffer.putInt(crc)
        ProblemReportPacket.putId(buffer, matchId)
        buffer.put(kind)
      case ProblemReportAction.READY =>
        // [38-53] the ticket
        val t = getTicket
        buffer.put(t, 0, Math.min(t.length, ProblemReportPacket.TicketSize))
        var n = t.length
        while (n < ProblemReportPacket.TicketSize) { buffer.put(0.toByte); n += 1 }
      case ProblemReportAction.REFUSED =>
        // [38] why, [39-42] when to try again
        buffer.put(reason)
        buffer.putInt(retryAfterSeconds)
      case ProblemReportAction.ACCEPTED =>
        // [38-49] the code
        SerializeUtil.putText(buffer, code, ProblemReportPacket.CodeSize)
      case ProblemReportAction.FAILED =>
        // [38] why
        buffer.put(reason)
      case _ =>
    }

    // The rest is zero
    while (buffer.position() < buffer.capacity()) buffer.put(0.toByte)

    buffer.array().clone()
  }

  override def toString: String =
    s"ProblemReportPacket{seq=$sequenceNumber, action=$action, report=$reportId, size=$size, reason=$reason, code=$code}"
}

object ProblemReportPacket {
  /** Bytes in an upload ticket. */
  val TicketSize = 16
  /** Bytes the report's code is given (ACCEPTED). */
  val CodeSize = 12

  def begin(seq: Int, playerId: UUID, reportId: UUID, size: Int, crc: Int, matchId: UUID, kind: Byte): ProblemReportPacket =
    new ProblemReportPacket(seq, playerId, Packet.getCurrentTimestamp, ProblemReportAction.BEGIN, reportId,
      size = size, crc = crc, matchId = matchId, kind = kind)

  def ready(seq: Int, playerId: UUID, reportId: UUID, ticket: Array[Byte]): ProblemReportPacket =
    new ProblemReportPacket(seq, playerId, Packet.getCurrentTimestamp, ProblemReportAction.READY, reportId, ticket = ticket)

  def refused(seq: Int, playerId: UUID, reportId: UUID, reason: Byte, retryAfterSeconds: Int): ProblemReportPacket =
    new ProblemReportPacket(seq, playerId, Packet.getCurrentTimestamp, ProblemReportAction.REFUSED, reportId,
      reason = reason, retryAfterSeconds = retryAfterSeconds)

  def accepted(seq: Int, playerId: UUID, reportId: UUID, code: String): ProblemReportPacket =
    new ProblemReportPacket(seq, playerId, Packet.getCurrentTimestamp, ProblemReportAction.ACCEPTED, reportId, code = code)

  def failed(seq: Int, playerId: UUID, reportId: UUID, reason: Byte): ProblemReportPacket =
    new ProblemReportPacket(seq, playerId, Packet.getCurrentTimestamp, ProblemReportAction.FAILED, reportId, reason = reason)

  /**
   * What an upload connection logs in with (AuthRequestPacket's 32-byte token, AuthAction.UPLOAD): the
   * ticket the server gave for the report, then the report's id, so the server can tell the ticket is
   * this report's.
   */
  def uploadToken(ticket: Array[Byte], reportId: UUID): Array[Byte] = {
    val b = ByteBuffer.allocate(TicketSize + 16)
    b.put(ticket, 0, Math.min(ticket.length, TicketSize))
    b.position(TicketSize)
    putId(b, reportId)
    b.array()
  }

  /** The ticket in an upload connection's token. */
  def ticketOf(token: Array[Byte]): Array[Byte] = java.util.Arrays.copyOf(token, TicketSize)

  /** The report id in an upload connection's token, or null. */
  def reportIdOf(token: Array[Byte]): UUID =
    if (token == null || token.length < TicketSize + 16) null
    else {
      val b = ByteBuffer.wrap(token, TicketSize, 16)
      idFrom(b.getLong, b.getLong)
    }

  private[protocol] def putId(buffer: ByteBuffer, id: UUID): Unit =
    if (id == null) { buffer.putLong(0L); buffer.putLong(0L) }
    else { buffer.putLong(id.getMostSignificantBits); buffer.putLong(id.getLeastSignificantBits) }

  /** An id read from the wire: all zeros is none. */
  private[protocol] def idFrom(most: Long, least: Long): UUID =
    if (most == 0L && least == 0L) null else new UUID(most, least)
}
