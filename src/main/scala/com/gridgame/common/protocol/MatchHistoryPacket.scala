package com.gridgame.common.protocol

import com.gridgame.common.Constants

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

object MatchHistoryAction {
  val QUERY: Byte = 0
  val ENTRY: Byte = 1
  val END: Byte = 2
  val STATS: Byte = 3
}

/**
 * 64-byte match history packet.
 * Layout:
 * [0] type=0x0D, [1-4] seq, [5-20] playerUUID,
 * [21] action,
 *
 * QUERY (client->server): no additional data
 *
 * ENTRY (server->client):
 * [22-25] matchId (int), [26] mapIndex (byte), [27] duration (byte),
 * [28-31] playedAt (int, epoch seconds), [32-33] kills (short),
 * [34-35] deaths (short), [36] rank (byte), [37] totalPlayers (byte),
 * [38] matchType (byte), [39-63] reserved
 *
 * END (server->client): no additional data
 *
 * STATS (server->client):
 * [22-25] totalKills (int), [26-29] totalDeaths (int),
 * [30-33] matchesPlayed (int), [34-37] wins (int),
 * [38-39] elo (short),
 * [40-63] reserved
 */
class MatchHistoryPacket(
    sequenceNumber: Int,
    playerId: UUID,
    timestamp: Int,
    val action: Byte,
    val matchId: Int = 0,
    val mapIndex: Byte = 0,
    val duration: Byte = 0,
    val playedAt: Int = 0,
    val kills: Short = 0,
    val deaths: Short = 0,
    val rank: Byte = 0,
    val totalPlayers: Byte = 0,
    val totalKills: Int = 0,
    val totalDeaths: Int = 0,
    val matchesPlayed: Int = 0,
    val wins: Int = 0,
    val elo: Short = 1000,
    val matchType: Byte = 0
) extends Packet(PacketType.MATCH_HISTORY, sequenceNumber, playerId, timestamp) {

  def this(sequenceNumber: Int, playerId: UUID, action: Byte) = {
    this(sequenceNumber, playerId, Packet.getCurrentTimestamp, action)
  }

  def getAction: Byte = action
  def getMatchId: Int = matchId
  def getMapIndex: Byte = mapIndex
  def getDuration: Byte = duration
  def getPlayedAt: Int = playedAt
  def getKills: Short = kills
  def getDeaths: Short = deaths
  def getRank: Byte = rank
  def getTotalPlayers: Byte = totalPlayers
  def getMatchType: Byte = matchType
  def getTotalKills: Int = totalKills
  def getTotalDeaths: Int = totalDeaths
  def getMatchesPlayed: Int = matchesPlayed
  def getWins: Int = wins
  def getElo: Short = elo

  override def serialize(): Array[Byte] = {
    val buffer = ByteBuffer.allocate(Constants.PACKET_PAYLOAD_SIZE)
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

    action match {
      case MatchHistoryAction.ENTRY =>
        buffer.putInt(matchId)       // [22-25]
        buffer.put(mapIndex)         // [26]
        buffer.put(duration)         // [27]
        buffer.putInt(playedAt)      // [28-31]
        buffer.putShort(kills)       // [32-33]
        buffer.putShort(deaths)      // [34-35]
        buffer.put(rank)             // [36]
        buffer.put(totalPlayers)     // [37]
        buffer.put(matchType)        // [38]
        buffer.put(new Array[Byte](25)) // [39-63] reserved

      case MatchHistoryAction.STATS =>
        buffer.putInt(totalKills)    // [22-25]
        buffer.putInt(totalDeaths)   // [26-29]
        buffer.putInt(matchesPlayed) // [30-33]
        buffer.putInt(wins)          // [34-37]
        buffer.putShort(elo)         // [38-39]
        buffer.put(new Array[Byte](24)) // [40-63] reserved

      case _ => // QUERY or END
        buffer.put(new Array[Byte](42)) // [22-63] reserved
    }

    buffer.array()
  }
}
