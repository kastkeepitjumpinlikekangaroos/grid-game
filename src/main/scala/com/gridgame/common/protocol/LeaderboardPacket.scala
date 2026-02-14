package com.gridgame.common.protocol

import com.gridgame.common.Constants

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.UUID

object LeaderboardAction {
  val QUERY: Byte = 0
  val ENTRY: Byte = 1
  val END: Byte = 2
}

/**
 * 64-byte leaderboard packet.
 * Layout:
 * [0] type=0x0F, [1-4] seq, [5-20] playerUUID,
 * [21] action,
 *
 * QUERY (client->server): no additional data
 *
 * ENTRY (server->client):
 * [22] rank (byte), [23-24] elo (short), [25-28] wins (int),
 * [29-32] matchesPlayed (int), [33-52] username (20 bytes),
 * [53-63] reserved
 *
 * END (server->client): no additional data
 */
class LeaderboardPacket(
    sequenceNumber: Int,
    playerId: UUID,
    timestamp: Int,
    val action: Byte,
    val rank: Byte = 0,
    val elo: Short = 1000,
    val wins: Int = 0,
    val matchesPlayed: Int = 0,
    val username: String = ""
) extends Packet(PacketType.LEADERBOARD, sequenceNumber, playerId, timestamp) {

  def this(sequenceNumber: Int, playerId: UUID, action: Byte) = {
    this(sequenceNumber, playerId, Packet.getCurrentTimestamp, action)
  }

  def getAction: Byte = action
  def getRank: Byte = rank
  def getElo: Short = elo
  def getWins: Int = wins
  def getMatchesPlayed: Int = matchesPlayed
  def getUsername: String = username

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

    action match {
      case LeaderboardAction.ENTRY =>
        buffer.put(rank)              // [22]
        buffer.putShort(elo)          // [23-24]
        buffer.putInt(wins)           // [25-28]
        buffer.putInt(matchesPlayed)  // [29-32]
        // [33-52] username (20 bytes)
        val nameBytes = username.getBytes(StandardCharsets.UTF_8)
        val namePadded = new Array[Byte](20)
        System.arraycopy(nameBytes, 0, namePadded, 0, Math.min(nameBytes.length, 20))
        buffer.put(namePadded)
        buffer.put(new Array[Byte](11)) // [53-63] reserved

      case _ => // QUERY or END
        buffer.put(new Array[Byte](42)) // [22-63] reserved
    }

    buffer.array()
  }
}
