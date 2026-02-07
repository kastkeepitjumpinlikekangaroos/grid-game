package com.gridgame.common.protocol

import com.gridgame.common.Constants
import com.gridgame.common.model.Position

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.UUID

object PacketSerializer {

  def deserialize(data: Array[Byte]): Packet = {
    if (data == null || data.length != Constants.PACKET_SIZE) {
      throw new IllegalArgumentException(
        s"Invalid packet size: expected ${Constants.PACKET_SIZE} bytes, got ${if (data != null) data.length else 0}"
      )
    }

    val buffer = ByteBuffer.wrap(data)
    buffer.order(ByteOrder.BIG_ENDIAN)

    // [0] Packet Type
    val typeId = buffer.get()
    val packetType = PacketType.fromId(typeId)

    // [1-4] Sequence Number
    val sequenceNumber = buffer.getInt()

    // [5-20] Player ID (UUID)
    val mostSigBits = buffer.getLong()
    val leastSigBits = buffer.getLong()
    val playerId = new UUID(mostSigBits, leastSigBits)

    // [21-24] X Position
    val x = buffer.getInt()

    // [25-28] Y Position
    val y = buffer.getInt()

    // [29-32] Color RGB
    val colorRGB = buffer.getInt()

    // [33-36] Timestamp
    val timestamp = buffer.getInt()

    // [37-63] Payload (27 bytes)
    val payload = new Array[Byte](27)
    buffer.get(payload)

    // Deserialize based on packet type
    packetType match {
      case PacketType.PLAYER_JOIN =>
        val playerName = extractString(payload)
        val joinPosition = try {
          new Position(x, y)
        } catch {
          case _: IllegalArgumentException => new Position(0, 0)
        }
        new PlayerJoinPacket(sequenceNumber, playerId, timestamp, joinPosition, colorRGB, playerName)

      case PacketType.PLAYER_UPDATE =>
        val updatePosition = try {
          new Position(x, y)
        } catch {
          case _: IllegalArgumentException => new Position(0, 0)
        }
        new PlayerUpdatePacket(sequenceNumber, playerId, timestamp, updatePosition, colorRGB)

      case PacketType.PLAYER_LEAVE =>
        new PlayerLeavePacket(sequenceNumber, playerId, timestamp)

      case PacketType.HEARTBEAT =>
        new PlayerLeavePacket(sequenceNumber, playerId, timestamp)
    }
  }

  private def extractString(bytes: Array[Byte]): String = {
    var length = 0
    var i = 0
    while (i < bytes.length) {
      if (bytes(i) == 0) {
        length = i
        i = bytes.length // break
      } else {
        length = i + 1
        i += 1
      }
    }

    if (length == 0) {
      ""
    } else {
      new String(bytes, 0, length, StandardCharsets.UTF_8)
    }
  }

  def validate(data: Array[Byte]): Boolean = {
    data != null && data.length == Constants.PACKET_SIZE
  }
}
