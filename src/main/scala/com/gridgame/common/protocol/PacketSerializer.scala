package com.gridgame.common.protocol

import com.gridgame.common.Constants
import com.gridgame.common.model.Direction
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

    // For PROJECTILE_UPDATE, x/y are floats; for other packets, they're ints
    // We need to handle this differently based on packet type
    packetType match {
      case PacketType.PROJECTILE_UPDATE =>
        // [21-24] X Position (as float)
        val x = buffer.getFloat()
        // [25-28] Y Position (as float)
        val y = buffer.getFloat()
        // [29-32] Color RGB
        val colorRGB = buffer.getInt()
        // [33-36] Timestamp
        val timestamp = buffer.getInt()
        // [37-63] Payload (27 bytes)
        val payload = new Array[Byte](27)
        buffer.get(payload)
        // Payload: [0-3] projectile ID, [4-5] dx, [6-7] dy, [8] action, [9-24] target UUID
        val payloadBuffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        val projectileId = payloadBuffer.getInt
        val dxShort = payloadBuffer.getShort()
        val dyShort = payloadBuffer.getShort()
        val dx = dxShort / 32767.0f
        val dy = dyShort / 32767.0f
        val action = payloadBuffer.get()
        val targetMostSig = payloadBuffer.getLong
        val targetLeastSig = payloadBuffer.getLong
        val targetId = if (targetMostSig != 0L || targetLeastSig != 0L) {
          new UUID(targetMostSig, targetLeastSig)
        } else {
          null
        }
        new ProjectilePacket(
          sequenceNumber, playerId, timestamp, x, y, colorRGB,
          projectileId, dx, dy, action, targetId
        )

      case _ =>
        // [21-24] X Position (as int)
        val x = buffer.getInt()
        // [25-28] Y Position (as int)
        val y = buffer.getInt()
        // [29-32] Color RGB
        val colorRGB = buffer.getInt()
        // [33-36] Timestamp
        val timestamp = buffer.getInt()
        // [37-63] Payload (27 bytes)
        val payload = new Array[Byte](27)
        buffer.get(payload)

        packetType match {
          case PacketType.PLAYER_JOIN =>
            // Name is in payload bytes 0-22 (23 bytes), health in bytes 23-26
            val nameBytes = payload.take(23)
            val playerName = extractString(nameBytes)
            val healthBuffer = ByteBuffer.wrap(payload, 23, 4).order(ByteOrder.BIG_ENDIAN)
            val health = healthBuffer.getInt
            val joinPosition = try {
              new Position(x, y)
            } catch {
              case _: IllegalArgumentException => new Position(0, 0)
            }
            new PlayerJoinPacket(sequenceNumber, playerId, timestamp, joinPosition, colorRGB, playerName, health)

          case PacketType.PLAYER_UPDATE =>
            // Health is in payload bytes 0-3
            val healthBuffer = ByteBuffer.wrap(payload, 0, 4).order(ByteOrder.BIG_ENDIAN)
            val health = healthBuffer.getInt
            val updatePosition = try {
              new Position(x, y)
            } catch {
              case _: IllegalArgumentException => new Position(0, 0)
            }
            new PlayerUpdatePacket(sequenceNumber, playerId, timestamp, updatePosition, colorRGB, health)

          case PacketType.PLAYER_LEAVE =>
            new PlayerLeavePacket(sequenceNumber, playerId, timestamp)

          case PacketType.WORLD_INFO =>
            val worldFile = extractString(payload)
            new WorldInfoPacket(sequenceNumber, timestamp, worldFile)

          case PacketType.HEARTBEAT =>
            new PlayerLeavePacket(sequenceNumber, playerId, timestamp)

          case PacketType.ITEM_UPDATE =>
            val payloadBuffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
            val itemId = payloadBuffer.getInt
            val action = payloadBuffer.get()
            val itemTypeId = payloadBuffer.get()
            new ItemPacket(sequenceNumber, playerId, timestamp, x, y, itemTypeId, itemId, action)

          case PacketType.TILE_UPDATE =>
            val payloadBuffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
            val tileId = payloadBuffer.getInt
            new TileUpdatePacket(sequenceNumber, playerId, timestamp, x, y, tileId)

          case _ =>
            throw new IllegalArgumentException(s"Unknown packet type: $packetType")
        }
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
