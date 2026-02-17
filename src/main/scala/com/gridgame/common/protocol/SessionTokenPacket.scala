package com.gridgame.common.protocol

import com.gridgame.common.Constants

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class SessionTokenPacket(
    sequenceNumber: Int,
    playerId: UUID,
    timestamp: Int,
    val sessionToken: Array[Byte]
) extends Packet(PacketType.SESSION_TOKEN, sequenceNumber, playerId, timestamp) {

  def this(sequenceNumber: Int, playerId: UUID, sessionToken: Array[Byte]) = {
    this(sequenceNumber, playerId, Packet.getCurrentTimestamp, sessionToken)
  }

  def getSessionToken: Array[Byte] = sessionToken

  override def serialize(): Array[Byte] = {
    val buffer = ByteBuffer.allocate(Constants.PACKET_PAYLOAD_SIZE)
    buffer.order(ByteOrder.BIG_ENDIAN)

    // [0] Packet Type
    buffer.put(packetType.id)

    // [1-4] Sequence Number
    buffer.putInt(sequenceNumber)

    // [5-20] Player ID (UUID)
    buffer.putLong(playerId.getMostSignificantBits)
    buffer.putLong(playerId.getLeastSignificantBits)

    // [21-52] Session Token (32 bytes)
    val tokenLen = Math.min(sessionToken.length, 32)
    buffer.put(sessionToken, 0, tokenLen)
    if (tokenLen < 32) buffer.put(new Array[Byte](32 - tokenLen))

    // [53-63] Reserved
    buffer.put(new Array[Byte](11))

    buffer.array()
  }
}
