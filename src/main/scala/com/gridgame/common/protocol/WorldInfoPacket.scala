package com.gridgame.common.protocol

import com.gridgame.common.Constants

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.UUID

class WorldInfoPacket(
    sequenceNumber: Int,
    timestamp: Int,
    val worldFile: String
) extends Packet(PacketType.WORLD_INFO, sequenceNumber, new UUID(0, 0), timestamp) {

  private val _worldFile: String = if (worldFile != null) worldFile else ""

  def this(sequenceNumber: Int, worldFile: String) = {
    this(sequenceNumber, Packet.getCurrentTimestamp, worldFile)
  }

  def getWorldFile: String = _worldFile

  override def serialize(): Array[Byte] = {
    val buffer = ByteBuffer.allocate(Constants.PACKET_SIZE)
    buffer.order(ByteOrder.BIG_ENDIAN)

    // [0] Packet Type
    buffer.put(packetType.id)

    // [1-4] Sequence Number
    buffer.putInt(sequenceNumber)

    // [5-20] Player ID (UUID = 2 longs) - not used for world info, set to 0
    buffer.putLong(0L)
    buffer.putLong(0L)

    // [21-24] X Position - not used
    buffer.putInt(0)

    // [25-28] Y Position - not used
    buffer.putInt(0)

    // [29-32] Color RGB - not used
    buffer.putInt(0)

    // [33-36] Timestamp
    buffer.putInt(timestamp)

    // [37-63] World file name (max 27 bytes)
    val worldBytes = _worldFile.getBytes(StandardCharsets.UTF_8)
    val worldLength = Math.min(worldBytes.length, 27)
    buffer.put(worldBytes, 0, worldLength)
    // Fill remaining bytes with zeros
    buffer.put(new Array[Byte](27 - worldLength))

    buffer.array()
  }

  override def toString: String = {
    s"WorldInfoPacket{seq=$sequenceNumber, worldFile='${_worldFile}'}"
  }
}
