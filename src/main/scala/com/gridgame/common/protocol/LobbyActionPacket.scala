package com.gridgame.common.protocol

import com.gridgame.common.Constants

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.UUID

object LobbyAction {
  val CREATE: Byte = 0
  val JOIN: Byte = 1
  val LEAVE: Byte = 2
  val START: Byte = 3
  val LIST_REQUEST: Byte = 4
  val LIST_ENTRY: Byte = 5
  val LIST_END: Byte = 6
  val JOINED: Byte = 7
  val PLAYER_JOINED: Byte = 8
  val PLAYER_LEFT: Byte = 9
  val GAME_STARTING: Byte = 10
  val LOBBY_CLOSED: Byte = 11
  val CONFIG_UPDATE: Byte = 12
}

/**
 * 64-byte lobby action packet.
 * Layout:
 * [0] type=0x09, [1-4] seq, [5-20] playerUUID,
 * [21] action, [22-23] lobbyId (short), [24] mapIndex, [25] durationMinutes,
 * [26] playerCount, [27] maxPlayers, [28] lobbyStatus,
 * [29-52] lobbyName (24 bytes UTF-8), [53-63] reserved
 */
class LobbyActionPacket(
    sequenceNumber: Int,
    playerId: UUID,
    timestamp: Int,
    val action: Byte,
    val lobbyId: Short = 0,
    val mapIndex: Byte = 0,
    val durationMinutes: Byte = 5,
    val playerCount: Byte = 0,
    val maxPlayers: Byte = Constants.MAX_LOBBY_PLAYERS.toByte,
    val lobbyStatus: Byte = 0,
    val lobbyName: String = ""
) extends Packet(PacketType.LOBBY_ACTION, sequenceNumber, playerId, timestamp) {

  def this(sequenceNumber: Int, playerId: UUID, action: Byte) = {
    this(sequenceNumber, playerId, Packet.getCurrentTimestamp, action)
  }

  def this(sequenceNumber: Int, playerId: UUID, action: Byte, lobbyId: Short) = {
    this(sequenceNumber, playerId, Packet.getCurrentTimestamp, action, lobbyId)
  }

  def this(sequenceNumber: Int, playerId: UUID, action: Byte, lobbyId: Short,
           mapIndex: Byte, durationMinutes: Byte, playerCount: Byte, maxPlayers: Byte,
           lobbyStatus: Byte, lobbyName: String) = {
    this(sequenceNumber, playerId, Packet.getCurrentTimestamp, action, lobbyId,
         mapIndex, durationMinutes, playerCount, maxPlayers, lobbyStatus, lobbyName)
  }

  def getAction: Byte = action
  def getLobbyId: Short = lobbyId
  def getMapIndex: Byte = mapIndex
  def getDurationMinutes: Byte = durationMinutes
  def getPlayerCount: Byte = playerCount
  def getMaxPlayers: Byte = maxPlayers
  def getLobbyStatus: Byte = lobbyStatus
  def getLobbyName: String = lobbyName

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

    // [22-23] Lobby ID
    buffer.putShort(lobbyId)

    // [24] Map Index
    buffer.put(mapIndex)

    // [25] Duration Minutes
    buffer.put(durationMinutes)

    // [26] Player Count
    buffer.put(playerCount)

    // [27] Max Players
    buffer.put(maxPlayers)

    // [28] Lobby Status
    buffer.put(lobbyStatus)

    // [29-52] Lobby Name (24 bytes)
    val nameBytes = lobbyName.getBytes(StandardCharsets.UTF_8)
    val nameLen = Math.min(nameBytes.length, Constants.MAX_LOBBY_NAME_LEN)
    buffer.put(nameBytes, 0, nameLen)
    buffer.put(new Array[Byte](Constants.MAX_LOBBY_NAME_LEN - nameLen))

    // [53-63] Reserved (11 bytes)
    buffer.put(new Array[Byte](11))

    buffer.array()
  }
}
