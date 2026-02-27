package com.gridgame.common.protocol

import com.gridgame.common.Constants

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.UUID

object ChatScope {
  val LOBBY: Byte = 0
  val GAME: Byte = 1
  val TEAM: Byte = 2
}

class ChatMessagePacket(
    sequenceNumber: Int,
    playerId: UUID,
    timestamp: Int,
    val scope: Byte,
    val message: String
) extends Packet(PacketType.CHAT_MESSAGE, sequenceNumber, playerId, timestamp) {

  def getScope: Byte = scope
  def getMessage: String = message

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

    // [21] Scope
    buffer.put(scope)

    // [22-63] Message (42 bytes UTF-8, null-padded)
    val msgBytes = message.getBytes(StandardCharsets.UTF_8)
    val msgLen = Math.min(msgBytes.length, Constants.MAX_CHAT_MESSAGE_LEN)
    buffer.put(msgBytes, 0, msgLen)
    if (msgLen < Constants.MAX_CHAT_MESSAGE_LEN) {
      buffer.put(new Array[Byte](Constants.MAX_CHAT_MESSAGE_LEN - msgLen))
    }

    buffer.array()
  }
}
