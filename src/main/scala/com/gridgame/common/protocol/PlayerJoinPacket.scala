package com.gridgame.common.protocol

import com.gridgame.common.Constants
import com.gridgame.common.model.Position

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.UUID

class PlayerJoinPacket(
    sequenceNumber: Int,
    playerId: UUID,
    timestamp: Int,
    val position: Position,
    val colorRGB: Int,
    val playerName: String
) extends Packet(PacketType.PLAYER_JOIN, sequenceNumber, playerId, timestamp) {

  private val _playerName: String = if (playerName != null) playerName else "Player"

  def this(sequenceNumber: Int, playerId: UUID, position: Position, colorRGB: Int, playerName: String) = {
    this(sequenceNumber, playerId, Packet.getCurrentTimestamp, position, colorRGB, playerName)
  }

  def getPosition: Position = position

  def getColorRGB: Int = colorRGB

  def getPlayerName: String = _playerName

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

    // [29-32] Color RGB
    buffer.putInt(colorRGB)

    // [33-36] Timestamp
    buffer.putInt(timestamp)

    // [37-63] Player name (max 27 bytes)
    val nameBytes = _playerName.getBytes(StandardCharsets.UTF_8)
    val nameLength = Math.min(nameBytes.length, 27)
    buffer.put(nameBytes, 0, nameLength)
    // Fill remaining bytes with zeros
    buffer.put(new Array[Byte](27 - nameLength))

    buffer.array()
  }

  override def toString: String = {
    s"PlayerJoinPacket{seq=$sequenceNumber, playerId=${playerId.toString.substring(0, 8)}, name='${_playerName}', position=$position, color=0x${colorRGB.toHexString.toUpperCase}}"
  }
}
