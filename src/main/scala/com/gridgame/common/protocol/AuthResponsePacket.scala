package com.gridgame.common.protocol

import com.gridgame.common.Constants

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.UUID

class AuthResponsePacket(
    sequenceNumber: Int,
    timestamp: Int,
    val success: Boolean,
    val assignedUUID: UUID,
    val message: String
) extends Packet(PacketType.AUTH_RESPONSE, sequenceNumber, new UUID(0L, 0L), timestamp) {

  def this(sequenceNumber: Int, success: Boolean, assignedUUID: UUID, message: String) = {
    this(sequenceNumber, Packet.getCurrentTimestamp, success, assignedUUID, message)
  }

  def getSuccess: Boolean = success
  def getAssignedUUID: UUID = assignedUUID
  def getMessage: String = message

  override def serialize(): Array[Byte] = {
    val buffer = ByteBuffer.allocate(Constants.PACKET_SIZE)
    buffer.order(ByteOrder.BIG_ENDIAN)

    // [0] Packet Type
    buffer.put(packetType.id)

    // [1-4] Sequence Number
    buffer.putInt(sequenceNumber)

    // [5-20] Player ID (zero UUID - standard header)
    buffer.putLong(playerId.getMostSignificantBits)
    buffer.putLong(playerId.getLeastSignificantBits)

    // [21] Success (0=fail, 1=success)
    buffer.put(if (success) 1.toByte else 0.toByte)

    // [22-37] Assigned UUID (16 bytes)
    if (assignedUUID != null) {
      buffer.putLong(assignedUUID.getMostSignificantBits)
      buffer.putLong(assignedUUID.getLeastSignificantBits)
    } else {
      buffer.putLong(0L)
      buffer.putLong(0L)
    }

    // [38-60] Message string (23 bytes)
    val msgBytes = message.getBytes(StandardCharsets.UTF_8)
    val msgLen = Math.min(msgBytes.length, 23)
    buffer.put(msgBytes, 0, msgLen)
    buffer.put(new Array[Byte](23 - msgLen))

    // [61-63] Reserved
    buffer.put(new Array[Byte](3))

    buffer.array()
  }
}
