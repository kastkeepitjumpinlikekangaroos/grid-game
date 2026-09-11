package com.gridgame.common.protocol

import com.gridgame.common.Constants

import java.nio.charset.StandardCharsets
import java.util.UUID

object AuthAction {
  val LOGIN: Byte = 0
  val SIGNUP: Byte = 1
}

/** What the server accepts for a new account. The client checks the same rules before it sends
  * a signup, so a name the server would refuse is reported as that, not as "Username taken". */
object AuthRules {
  /** The username field of an AuthRequestPacket; usernames are ASCII, so bytes are characters. */
  val MaxUsernameLength = 20
  val MinPasswordLength = 6
  /** The password field of an AuthRequestPacket, in characters (the form's limit). */
  val MaxPasswordLength = 20

  private val UsernamePattern = "[a-zA-Z0-9_-]{1,20}".r

  def isValidUsername(username: String): Boolean = username != null && UsernamePattern.matches(username)

  // Replies, each within the 23 bytes an AuthResponsePacket carries. "Password must be 6+ chars"
  // was 25, and reached the login screen as "Password must be 6+ cha".
  val PasswordTooShort = "Password needs 6+ chars"
  val InvalidUsername = "Invalid username"
  val UsernameTaken = "Username taken"
  val InvalidCredentials = "Invalid credentials"
  val TooManyAttempts = "Too many attempts"
  val AccountCreated = "Account created"
  val LoginSuccessful = "Login successful"
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
    val buffer = SerializeUtil.acquireBuffer()

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

    buffer.array().clone()
  }
}
