package com.gridgame.common.protocol

import com.gridgame.common.Constants

import java.util.UUID

/**
 * The refresh token a login gives the client (server -> client only): what logs it in again
 * without the password, after the connection drops or, if the player asked to be remembered, the
 * next time the game starts (design/networking.md, *Staying logged in*). Every login sends a new
 * one, a login with a refresh token included: each token logs in once. Sent after the session's
 * token and before the AUTH_RESPONSE, so it is signed, and held before the client acts on the answer.
 *
 * {{{
 * [21-52]  the refresh token (32 bytes)
 * [53-60]  when its login runs out, epoch ms: it says, so a client can drop one that has
 * [61-63]  zero
 * }}}
 */
class RefreshTokenPacket(
    sequenceNumber: Int,
    playerId: UUID,
    timestamp: Int,
    val refreshToken: Array[Byte],
    val expiresAt: Long
) extends Packet(PacketType.REFRESH_TOKEN, sequenceNumber, playerId, timestamp) {

  def this(sequenceNumber: Int, playerId: UUID, refreshToken: Array[Byte], expiresAt: Long) = {
    this(sequenceNumber, playerId, Packet.getCurrentTimestamp, refreshToken, expiresAt)
  }

  def getRefreshToken: Array[Byte] = refreshToken
  def getExpiresAt: Long = expiresAt

  override def serialize(): Array[Byte] = {
    val buffer = SerializeUtil.acquireBuffer()

    // [0] Packet Type
    buffer.put(packetType.id)

    // [1-4] Sequence Number
    buffer.putInt(sequenceNumber)

    // [5-20] Player ID (UUID)
    buffer.putLong(playerId.getMostSignificantBits)
    buffer.putLong(playerId.getLeastSignificantBits)

    // [21-52] Refresh token (32 bytes)
    val tokenLen = Math.min(refreshToken.length, Constants.REFRESH_TOKEN_SIZE)
    buffer.put(refreshToken, 0, tokenLen)
    buffer.put(new Array[Byte](Constants.REFRESH_TOKEN_SIZE - tokenLen))

    // [53-60] Expiry (epoch ms)
    buffer.putLong(expiresAt)

    // [61-63] Reserved
    buffer.put(new Array[Byte](3))

    buffer.array().clone()
  }

  // Never the token: this is what a log line would print
  override def toString: String = s"RefreshTokenPacket{seq=$sequenceNumber, expiresAt=$expiresAt}"
}
