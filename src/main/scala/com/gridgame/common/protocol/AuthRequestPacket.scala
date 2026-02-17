package com.gridgame.common.protocol

import com.gridgame.common.Constants

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.UUID

object AuthAction {
  val LOGIN: Byte = 0
  val SIGNUP: Byte = 1
}

class AuthRequestPacket(
    sequenceNumber: Int,
    timestamp: Int,
    val action: Byte,
    val username: String,
    val password: String
) extends Packet(PacketType.AUTH_REQUEST, sequenceNumber, new UUID(0L, 0L), timestamp) {

  def this(sequenceNumber: Int, action: Byte, username: String, password: String) = {
    this(sequenceNumber, Packet.getCurrentTimestamp, action, username, password)
  }

  def getAction: Byte = action
  def getUsername: String = username
  def getPassword: String = password

  override def serialize(): Array[Byte] = {
    val buffer = ByteBuffer.allocate(Constants.PACKET_PAYLOAD_SIZE)
    buffer.order(ByteOrder.BIG_ENDIAN)

    // [0] Packet Type
    buffer.put(packetType.id)

    // [1-4] Sequence Number
    buffer.putInt(sequenceNumber)

    // [5-20] Player ID (zero UUID - standard header)
    buffer.putLong(playerId.getMostSignificantBits)
    buffer.putLong(playerId.getLeastSignificantBits)

    // [21] Action (0=LOGIN, 1=SIGNUP)
    buffer.put(action)

    // [22-41] Username (20 bytes UTF-8)
    val usernameBytes = username.getBytes(StandardCharsets.UTF_8)
    val usernameLen = Math.min(usernameBytes.length, 20)
    buffer.put(usernameBytes, 0, usernameLen)
    buffer.put(new Array[Byte](20 - usernameLen))

    // [42-61] Password (20 bytes UTF-8)
    val passwordBytes = password.getBytes(StandardCharsets.UTF_8)
    val passwordLen = Math.min(passwordBytes.length, 20)
    buffer.put(passwordBytes, 0, passwordLen)
    buffer.put(new Array[Byte](20 - passwordLen))

    // [62-63] Reserved
    buffer.put(new Array[Byte](2))

    buffer.array()
  }
}
