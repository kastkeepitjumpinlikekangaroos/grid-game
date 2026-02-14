package com.gridgame.common.protocol

import com.gridgame.common.Constants

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

object GameEvent {
  val KILL: Byte = 0
  val TIME_SYNC: Byte = 1
  val GAME_OVER: Byte = 2
  val SCORE_ENTRY: Byte = 3
  val SCORE_END: Byte = 4
  val RESPAWN: Byte = 5
}

/**
 * 64-byte game event packet.
 * Layout:
 * [0] type=0x0A, [1-4] seq, [5-20] playerUUID (killer/subject),
 * [21] eventType, [22-23] gameId (short), [24-27] remainingSeconds (int),
 * [28-29] kills (short), [30-31] deaths (short),
 * [32-47] targetUUID (victim), [48-52] reserved, [53] rank, [54-55] spawnX (short),
 * [56-57] spawnY (short), [58-63] reserved
 */
class GameEventPacket(
    sequenceNumber: Int,
    playerId: UUID,
    timestamp: Int,
    val eventType: Byte,
    val gameId: Short = 0,
    val remainingSeconds: Int = 0,
    val kills: Short = 0,
    val deaths: Short = 0,
    val targetId: UUID = null,
    val rank: Byte = 0,
    val spawnX: Short = 0,
    val spawnY: Short = 0
) extends Packet(PacketType.GAME_EVENT, sequenceNumber, playerId, timestamp) {


  def this(sequenceNumber: Int, playerId: UUID, eventType: Byte, gameId: Short,
           remainingSeconds: Int, kills: Short, deaths: Short, targetId: UUID,
           rank: Byte, spawnX: Short, spawnY: Short) = {
    this(sequenceNumber, playerId, Packet.getCurrentTimestamp, eventType, gameId,
         remainingSeconds, kills, deaths, targetId, rank, spawnX, spawnY)
  }

  def getEventType: Byte = eventType
  def getGameId: Short = gameId
  def getRemainingSeconds: Int = remainingSeconds
  def getKills: Short = kills
  def getDeaths: Short = deaths
  def getTargetId: UUID = targetId
  def getRank: Byte = rank
  def getSpawnX: Short = spawnX
  def getSpawnY: Short = spawnY

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

    // [21] Event Type
    buffer.put(eventType)

    // [22-23] Game ID
    buffer.putShort(gameId)

    // [24-27] Remaining Seconds
    buffer.putInt(remainingSeconds)

    // [28-29] Kills
    buffer.putShort(kills)

    // [30-31] Deaths
    buffer.putShort(deaths)

    // [32-47] Target UUID
    if (targetId != null) {
      buffer.putLong(targetId.getMostSignificantBits)
      buffer.putLong(targetId.getLeastSignificantBits)
    } else {
      buffer.putLong(0L)
      buffer.putLong(0L)
    }

    // [48-52] Reserved (5 bytes)
    buffer.put(new Array[Byte](5))

    // [53] Rank
    buffer.put(rank)

    // [54-55] Spawn X
    buffer.putShort(spawnX)

    // [56-57] Spawn Y
    buffer.putShort(spawnY)

    // [58-63] Reserved (6 bytes)
    buffer.put(new Array[Byte](6))

    buffer.array()
  }
}
