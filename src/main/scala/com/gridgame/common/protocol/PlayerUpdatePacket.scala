package com.gridgame.common.protocol

import com.gridgame.common.Constants
import com.gridgame.common.model.Position

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class PlayerUpdatePacket(
    sequenceNumber: Int,
    playerId: UUID,
    timestamp: Int,
    val position: Position,
    val colorRGB: Int,
    val health: Int = 100
) extends Packet(PacketType.PLAYER_UPDATE, sequenceNumber, playerId, timestamp) {

  def this(sequenceNumber: Int, playerId: UUID, position: Position, colorRGB: Int) = {
    this(sequenceNumber, playerId, Packet.getCurrentTimestamp, position, colorRGB, 100)
  }

  def this(sequenceNumber: Int, playerId: UUID, position: Position, colorRGB: Int, health: Int) = {
    this(sequenceNumber, playerId, Packet.getCurrentTimestamp, position, colorRGB, health)
  }

  def getPosition: Position = position

  def getColorRGB: Int = colorRGB

  def getHealth: Int = health

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

    // [21-24] X Position
    buffer.putInt(position.getX)

    // [25-28] Y Position
    buffer.putInt(position.getY)

    // [29-32] Color RGB (4 bytes for ARGB)
    buffer.putInt(colorRGB)

    // [33-36] Timestamp
    buffer.putInt(timestamp)

    // [37-40] Health
    buffer.putInt(health)

    // [41-63] Reserved (23 bytes) - fill with zeros
    buffer.put(new Array[Byte](23))

    buffer.array()
  }

  override def toString: String = {
    s"PlayerUpdatePacket{seq=$sequenceNumber, playerId=${playerId.toString.substring(0, 8)}, position=$position, color=0x${colorRGB.toHexString.toUpperCase}, health=$health}"
  }
}
