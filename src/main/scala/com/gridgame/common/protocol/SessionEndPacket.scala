package com.gridgame.common.protocol

import java.util.UUID

/** Why the server ended a session (SessionEndPacket). */
object SessionEndReason {
  /** Nothing said: the connection dropped, or the server closed it without a word. */
  val NONE: Byte = 0
  /** The account logged in somewhere else, which takes it over. The client doesn't log back in by
    * itself: two clients of one account would take it from each other for ever. */
  val REPLACED: Byte = 1
  /** The login is over: logged out, or its refresh token was used twice (someone else has a copy).
    * The client forgets the token and asks for the password. */
  val REVOKED: Byte = 2
}

/**
 * The server is closing this session, and why (server -> client only), sent just before it closes
 * the connection. The client reads it on its network thread as it arrives: the disconnect behind it
 * stops the packet thread, which may never get to it.
 *
 * {{{
 * [21]     reason (SessionEndReason)
 * [22-63]  zero
 * }}}
 */
class SessionEndPacket(
    sequenceNumber: Int,
    playerId: UUID,
    timestamp: Int,
    val reason: Byte
) extends Packet(PacketType.SESSION_END, sequenceNumber, playerId, timestamp) {

  def this(sequenceNumber: Int, playerId: UUID, reason: Byte) = {
    this(sequenceNumber, playerId, Packet.getCurrentTimestamp, reason)
  }

  def getReason: Byte = reason

  override def serialize(): Array[Byte] = {
    val buffer = SerializeUtil.acquireBuffer()

    // [0] Packet Type
    buffer.put(packetType.id)

    // [1-4] Sequence Number
    buffer.putInt(sequenceNumber)

    // [5-20] Player ID (UUID)
    buffer.putLong(playerId.getMostSignificantBits)
    buffer.putLong(playerId.getLeastSignificantBits)

    // [21] Reason
    buffer.put(reason)

    // [22-63] Reserved
    buffer.put(new Array[Byte](42))

    buffer.array().clone()
  }
}
