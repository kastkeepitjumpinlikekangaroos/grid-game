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
      case PacketType.AUTH_REQUEST =>
        // Buffer is at byte 21 (after standard header: type + seq + UUID)
        // [21] Action
        val action = buffer.get()
        // [22-41] Username (20 bytes)
        val usernameBytes = new Array[Byte](20)
        buffer.get(usernameBytes)
        val username = extractString(usernameBytes)
        // [42-61] Password (20 bytes)
        val passwordBytes = new Array[Byte](20)
        buffer.get(passwordBytes)
        val password = extractString(passwordBytes)
        new AuthRequestPacket(sequenceNumber, Packet.getCurrentTimestamp, action, username, password)

      case PacketType.AUTH_RESPONSE =>
        // Buffer is at byte 21 (after standard header: type + seq + UUID)
        // [21] Success
        val success = buffer.get() != 0
        // [22-37] Assigned UUID (16 bytes)
        val uuidMost = buffer.getLong()
        val uuidLeast = buffer.getLong()
        val assignedUUID = new UUID(uuidMost, uuidLeast)
        // [38-60] Message (23 bytes)
        val msgBytes = new Array[Byte](23)
        buffer.get(msgBytes)
        val message = extractString(msgBytes)
        new AuthResponsePacket(sequenceNumber, Packet.getCurrentTimestamp, success, assignedUUID, message)

      case PacketType.MATCH_HISTORY =>
        val action = buffer.get()
        action match {
          case MatchHistoryAction.ENTRY =>
            val matchId = buffer.getInt()
            val mapIndex = buffer.get()
            val duration = buffer.get()
            val playedAt = buffer.getInt()
            val kills = buffer.getShort()
            val deaths = buffer.getShort()
            val rank = buffer.get()
            val totalPlayers = buffer.get()
            new MatchHistoryPacket(sequenceNumber, playerId, Packet.getCurrentTimestamp, action,
              matchId, mapIndex, duration, playedAt, kills, deaths, rank, totalPlayers)

          case MatchHistoryAction.STATS =>
            val totalKills = buffer.getInt()
            val totalDeaths = buffer.getInt()
            val matchesPlayed = buffer.getInt()
            val wins = buffer.getInt()
            val elo = buffer.getShort()
            new MatchHistoryPacket(sequenceNumber, playerId, Packet.getCurrentTimestamp, action,
              totalKills = totalKills, totalDeaths = totalDeaths,
              matchesPlayed = matchesPlayed, wins = wins, elo = elo)

          case _ => // QUERY or END
            new MatchHistoryPacket(sequenceNumber, playerId, action)
        }

      case PacketType.LEADERBOARD =>
        val action = buffer.get()
        action match {
          case LeaderboardAction.ENTRY =>
            val rank = buffer.get()
            val elo = buffer.getShort()
            val wins = buffer.getInt()
            val matchesPlayed = buffer.getInt()
            val nameBytes = new Array[Byte](20)
            buffer.get(nameBytes)
            val username = extractString(nameBytes)
            new LeaderboardPacket(sequenceNumber, playerId, Packet.getCurrentTimestamp, action,
              rank, elo, wins, matchesPlayed, username)

          case _ => // QUERY or END
            new LeaderboardPacket(sequenceNumber, playerId, action)
        }

      case PacketType.RANKED_QUEUE =>
        val action = buffer.get()
        val characterId = buffer.get()
        val queueSize = buffer.get()
        val elo = buffer.getShort()
        val waitTimeSeconds = buffer.getInt()
        val lobbyId = buffer.getShort()
        val mapIndex = buffer.get()
        val durationMinutes = buffer.get()
        val playerCount = buffer.get()
        val maxPlayers = buffer.get()
        val nameBytes = new Array[Byte](Constants.MAX_LOBBY_NAME_LEN)
        buffer.get(nameBytes)
        val lobbyName = extractString(nameBytes)
        val mode = buffer.get() // [60] mode
        buffer.get(new Array[Byte](3)) // reserved
        new RankedQueuePacket(sequenceNumber, playerId, Packet.getCurrentTimestamp, action,
          characterId, queueSize, elo, waitTimeSeconds, lobbyId, mapIndex, durationMinutes,
          playerCount, maxPlayers, lobbyName, mode)

      case PacketType.LOBBY_ACTION =>
        // Custom layout from byte 21 onward (no standard x/y/color/timestamp)
        val action = buffer.get()
        val lobbyId = buffer.getShort()
        val mapIndex = buffer.get()
        val durationMinutes = buffer.get()
        val playerCount = buffer.get()
        val maxPlayers = buffer.get()
        val lobbyStatus = buffer.get()
        val nameBytes = new Array[Byte](Constants.MAX_LOBBY_NAME_LEN)
        buffer.get(nameBytes)
        val lobbyName = extractString(nameBytes)
        val characterId = buffer.get()
        new LobbyActionPacket(sequenceNumber, playerId, Packet.getCurrentTimestamp, action, lobbyId,
          mapIndex, durationMinutes, playerCount, maxPlayers, lobbyStatus, lobbyName, characterId)

      case PacketType.GAME_EVENT =>
        // Custom layout from byte 21 onward (no standard x/y/color/timestamp)
        val eventType = buffer.get()
        val gameId = buffer.getShort()
        val remainingSeconds = buffer.getInt()
        val kills = buffer.getShort()
        val deaths = buffer.getShort()
        val targetMost = buffer.getLong()
        val targetLeast = buffer.getLong()
        val targetId = if (targetMost != 0L || targetLeast != 0L) {
          new UUID(targetMost, targetLeast)
        } else {
          null
        }
        buffer.get(new Array[Byte](5)) // reserved
        val rank = buffer.get()
        val spawnX = buffer.getShort()
        val spawnY = buffer.getShort()
        new GameEventPacket(sequenceNumber, playerId, Packet.getCurrentTimestamp, eventType, gameId,
          remainingSeconds, kills, deaths, targetId, rank, spawnX, spawnY)

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
        // [25] charge level (absolute byte [62])
        val chargeLevel = payloadBuffer.get()
        // [26] projectile type (absolute byte [63])
        val projectileType = payloadBuffer.get()
        new ProjectilePacket(
          sequenceNumber, playerId, timestamp, x, y, colorRGB,
          projectileId, dx, dy, action, targetId, chargeLevel, projectileType
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
            // Name is in payload bytes 0-21 (22 bytes), characterId in byte 22, health in bytes 23-26
            val nameBytes = payload.take(22)
            val playerName = extractString(nameBytes)
            val characterId = payload(22)
            val healthBuffer = ByteBuffer.wrap(payload, 23, 4).order(ByteOrder.BIG_ENDIAN)
            val health = healthBuffer.getInt
            val joinPosition = try {
              new Position(x, y)
            } catch {
              case _: IllegalArgumentException => new Position(0, 0)
            }
            new PlayerJoinPacket(sequenceNumber, playerId, timestamp, joinPosition, colorRGB, playerName, health, characterId)

          case PacketType.PLAYER_UPDATE =>
            // Health is in payload bytes 0-3, chargeLevel is payload byte 4, effectFlags is payload byte 5, characterId is payload byte 6
            val healthBuffer = ByteBuffer.wrap(payload, 0, 4).order(ByteOrder.BIG_ENDIAN)
            val health = healthBuffer.getInt
            val chargeLevel = payload(4) & 0xFF
            val effectFlags = payload(5) & 0xFF
            val characterId = payload(6)
            val updatePosition = try {
              new Position(x, y)
            } catch {
              case _: IllegalArgumentException => new Position(0, 0)
            }
            new PlayerUpdatePacket(sequenceNumber, playerId, timestamp, updatePosition, colorRGB, health, chargeLevel, effectFlags, characterId)

          case PacketType.PLAYER_LEAVE =>
            new PlayerLeavePacket(sequenceNumber, playerId, timestamp)

          case PacketType.WORLD_INFO =>
            val worldFile = extractString(payload)
            new WorldInfoPacket(sequenceNumber, timestamp, worldFile)

          case PacketType.HEARTBEAT =>
            new HeartbeatPacket(sequenceNumber, playerId, timestamp)

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
