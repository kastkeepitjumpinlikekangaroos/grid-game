package com.gridgame.common.protocol

import com.gridgame.common.Constants

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class PlayerLeavePacket(
    sequenceNumber: Int,
    playerId: UUID,
    timestamp: Int
) extends Packet(PacketType.PLAYER_LEAVE, sequenceNumber, playerId, timestamp) {

  def this(sequenceNumber: Int, playerId: UUID) = {
    this(sequenceNumber, playerId, Packet.getCurrentTimestamp)
  }

  override def serialize(): Array[Byte] = {
    val buffer = ByteBuffer.allocate(Constants.PACKET_SIZE)
    buffer.order(ByteOrder.BIG_ENDIAN)

    // [0] Packet Type
    buffer.put(packetType.id)

    // [1-4] Sequence Number
    buffer.putInt(sequenceNumber)

    // [5-20] Player ID (UUID = 2 longs)
    buffer.putLong(playerId.getMostSignificantBits)
    buffer.putLong(playerId.getLeastSignificantBits)

    // [21-36] Unused (position, color, timestamp) - fill with zeros
    buffer.putInt(0) // X
    buffer.putInt(0) // Y
    buffer.putInt(0) // Color
    buffer.putInt(timestamp) // Timestamp

    // [37-63] Payload/Reserved (27 bytes) - fill with zeros
    buffer.put(new Array[Byte](27))

    buffer.array()
  }

  override def toString: String = {
    s"PlayerLeavePacket{seq=$sequenceNumber, playerId=${playerId.toString.substring(0, 8)}}"
  }
}
