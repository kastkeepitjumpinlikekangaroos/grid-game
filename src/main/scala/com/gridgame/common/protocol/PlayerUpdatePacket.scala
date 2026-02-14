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
    val health: Int = 100,
    val chargeLevel: Int = 0,
    val effectFlags: Int = 0,
    val characterId: Byte = 0
) extends Packet(PacketType.PLAYER_UPDATE, sequenceNumber, playerId, timestamp) {

  def this(sequenceNumber: Int, playerId: UUID, position: Position, colorRGB: Int) = {
    this(sequenceNumber, playerId, Packet.getCurrentTimestamp, position, colorRGB, 100, 0, 0, 0.toByte)
  }

  def this(sequenceNumber: Int, playerId: UUID, position: Position, colorRGB: Int, health: Int) = {
    this(sequenceNumber, playerId, Packet.getCurrentTimestamp, position, colorRGB, health, 0, 0, 0.toByte)
  }

  def this(sequenceNumber: Int, playerId: UUID, position: Position, colorRGB: Int, health: Int, chargeLevel: Int) = {
    this(sequenceNumber, playerId, Packet.getCurrentTimestamp, position, colorRGB, health, chargeLevel, 0, 0.toByte)
  }

  def this(sequenceNumber: Int, playerId: UUID, position: Position, colorRGB: Int, health: Int, chargeLevel: Int, effectFlags: Int) = {
    this(sequenceNumber, playerId, Packet.getCurrentTimestamp, position, colorRGB, health, chargeLevel, effectFlags, 0.toByte)
  }

  def this(sequenceNumber: Int, playerId: UUID, position: Position, colorRGB: Int, health: Int, chargeLevel: Int, effectFlags: Int, characterId: Byte) = {
    this(sequenceNumber, playerId, Packet.getCurrentTimestamp, position, colorRGB, health, chargeLevel, effectFlags, characterId)
  }

  def getPosition: Position = position

  def getColorRGB: Int = colorRGB

  def getHealth: Int = health

  def getChargeLevel: Int = chargeLevel

  def getEffectFlags: Int = effectFlags

  def getCharacterId: Byte = characterId

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

    // [41] Charge level (0-100)
    buffer.put(chargeLevel.toByte)

    // [42] Effect flags bitfield (bit 0: shield, bit 1: gem boost)
    buffer.put(effectFlags.toByte)

    // [43] Character ID
    buffer.put(characterId)

    // [44-63] Reserved (20 bytes) - fill with zeros
    buffer.put(new Array[Byte](20))

    buffer.array()
  }

  override def toString: String = {
    s"PlayerUpdatePacket{seq=$sequenceNumber, playerId=${playerId.toString.substring(0, 8)}, position=$position, color=0x${colorRGB.toHexString.toUpperCase}, health=$health, charge=$chargeLevel, effects=$effectFlags}"
  }
}
