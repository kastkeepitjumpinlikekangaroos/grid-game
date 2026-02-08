package com.gridgame.common.protocol

import com.gridgame.common.Constants

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class HeartbeatPacket(
    sequenceNumber: Int,
    playerId: UUID,
    timestamp: Int
) extends Packet(PacketType.HEARTBEAT, sequenceNumber, playerId, timestamp) {

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

    // [21-63] Zeroed payload
    buffer.put(new Array[Byte](43))

    buffer.array()
  }

  override def toString: String = {
    s"HeartbeatPacket{seq=$sequenceNumber, playerId=${playerId.toString.substring(0, 8)}}"
  }
}
