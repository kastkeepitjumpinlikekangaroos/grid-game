package com.gridgame.common.protocol

import com.gridgame.common.Constants

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.UUID

object RankedQueueAction {
  val QUEUE_JOIN: Byte = 0
  val QUEUE_LEAVE: Byte = 1
  val QUEUE_STATUS: Byte = 2
  val MATCH_FOUND: Byte = 3
  val CHARACTER_CHANGE: Byte = 4
}

object RankedQueueMode {
  val FFA: Byte = 0
  val DUEL: Byte = 1
  val TEAMS: Byte = 2
}

/**
 * 64-byte ranked queue packet.
 * Layout:
 * [0] type=0x0E, [1-4] seq, [5-20] playerUUID,
 * [21] action,
 * [22] characterId,
 * [23] queueSize,
 * [24-25] elo (short),
 * [26-29] waitTimeSeconds (int),
 * [30-31] lobbyId (short)       -- MATCH_FOUND
 * [32] mapIndex                  -- MATCH_FOUND
 * [33] durationMinutes           -- MATCH_FOUND
 * [34] playerCount               -- MATCH_FOUND
 * [35] maxPlayers                -- MATCH_FOUND
 * [36-59] lobbyName (24 bytes)   -- MATCH_FOUND
 * [60] mode (0=FFA, 1=DUEL)
 * [61-63] reserved
 */
class RankedQueuePacket(
    sequenceNumber: Int,
    playerId: UUID,
    timestamp: Int,
    val action: Byte,
    val characterId: Byte = 0,
    val queueSize: Byte = 0,
    val elo: Short = 1000,
    val waitTimeSeconds: Int = 0,
    val lobbyId: Short = 0,
    val mapIndex: Byte = 0,
    val durationMinutes: Byte = 0,
    val playerCount: Byte = 0,
    val maxPlayers: Byte = 0,
    val lobbyName: String = "",
    val mode: Byte = RankedQueueMode.FFA
) extends Packet(PacketType.RANKED_QUEUE, sequenceNumber, playerId, timestamp) {

  def this(sequenceNumber: Int, playerId: UUID, action: Byte) = {
    this(sequenceNumber, playerId, Packet.getCurrentTimestamp, action)
  }

  def this(sequenceNumber: Int, playerId: UUID, action: Byte, characterId: Byte) = {
    this(sequenceNumber, playerId, Packet.getCurrentTimestamp, action, characterId)
  }

  def this(sequenceNumber: Int, playerId: UUID, action: Byte, characterId: Byte, mode: Byte) = {
    this(sequenceNumber, playerId, Packet.getCurrentTimestamp, action, characterId, mode = mode)
  }

  def getAction: Byte = action
  def getCharacterId: Byte = characterId
  def getQueueSize: Byte = queueSize
  def getElo: Short = elo
  def getWaitTimeSeconds: Int = waitTimeSeconds
  def getLobbyId: Short = lobbyId
  def getMapIndex: Byte = mapIndex
  def getDurationMinutes: Byte = durationMinutes
  def getPlayerCount: Byte = playerCount
  def getMaxPlayers: Byte = maxPlayers
  def getLobbyName: String = lobbyName
  def getMode: Byte = mode

  override def serialize(): Array[Byte] = {
    val buffer = ByteBuffer.allocate(Constants.PACKET_SIZE)
    buffer.order(ByteOrder.BIG_ENDIAN)

    // [0] Packet Type
    buffer.put(packetType.id)

    // [1-4] Sequence Number
    buffer.putInt(sequenceNumber)

    // [5-20] Player ID (UUID)
    buffer.putLong(playerId.getMostSignificantBits)
    buffer.putLong(playerId.getLeastSignificantBits)

    // [21] Action
    buffer.put(action)

    // [22] Character ID
    buffer.put(characterId)

    // [23] Queue Size
    buffer.put(queueSize)

    // [24-25] ELO
    buffer.putShort(elo)

    // [26-29] Wait Time Seconds
    buffer.putInt(waitTimeSeconds)

    // [30-31] Lobby ID
    buffer.putShort(lobbyId)

    // [32] Map Index
    buffer.put(mapIndex)

    // [33] Duration Minutes
    buffer.put(durationMinutes)

    // [34] Player Count
    buffer.put(playerCount)

    // [35] Max Players
    buffer.put(maxPlayers)

    // [36-59] Lobby Name (24 bytes)
    val nameBytes = lobbyName.getBytes(StandardCharsets.UTF_8)
    val nameLen = Math.min(nameBytes.length, Constants.MAX_LOBBY_NAME_LEN)
    buffer.put(nameBytes, 0, nameLen)
    buffer.put(new Array[Byte](Constants.MAX_LOBBY_NAME_LEN - nameLen))

    // [60] Mode
    buffer.put(mode)

    // [61-63] Reserved
    buffer.put(new Array[Byte](3))

    buffer.array()
  }
}
