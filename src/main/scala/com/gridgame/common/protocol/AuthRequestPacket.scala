package com.gridgame.common.protocol

import com.gridgame.common.Constants

import java.nio.charset.StandardCharsets
import java.util.UUID

object AuthAction {
  val LOGIN: Byte = 0
  val SIGNUP: Byte = 1
  /** Log in with the refresh token an earlier login was given, instead of a password. */
  val REFRESH: Byte = 2
  /** End the login a refresh token belongs to: the token is no good from then on, and neither is any
    * session it started. No reply. */
  val LOGOUT: Byte = 3

  /** The actions that carry a refresh token rather than a name and a password. */
  def carriesToken(action: Byte): Boolean = action == REFRESH || action == LOGOUT
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
  /** A refresh token the server won't take: unknown, past its login's lifetime, logged out, or used
    * already. The client forgets it and asks for the password. */
  val LoginExpired = "Login expired"
  /** The server couldn't check a refresh token (its database failed): the token may be good, so the
    * client keeps it. */
  val TryAgainLater = "Try again later"
}

/**
 * A login (a name and a password), a signup, or a refresh token: logging in with one (REFRESH),
 * or ending its login (LOGOUT). The server takes it without checking a signature: there is no
 * session yet, or (a LOGOUT) the token it carries is the proof. TLS is what keeps it private.
 *
 * {{{
 * [21]     action (AuthAction)
 * LOGIN, SIGNUP:    [22-41] username (20 bytes UTF-8)  [42-61] password (20 bytes UTF-8)  [62-63] zero
 * REFRESH, LOGOUT:  [22-53] the refresh token (32 bytes)                                  [54-63] zero
 * }}}
 */
class AuthRequestPacket(
    sequenceNumber: Int,
    timestamp: Int,
    val action: Byte,
    val username: String,
    val password: String,
    val refreshToken: Array[Byte] = null
) extends Packet(PacketType.AUTH_REQUEST, sequenceNumber, new UUID(0L, 0L), timestamp) {

  def this(sequenceNumber: Int, action: Byte, username: String, password: String) = {
    this(sequenceNumber, Packet.getCurrentTimestamp, action, username, password)
  }

  /** A REFRESH or a LOGOUT, with the refresh token it is for. */
  def this(sequenceNumber: Int, action: Byte, refreshToken: Array[Byte]) = {
    this(sequenceNumber, Packet.getCurrentTimestamp, action, "", "", refreshToken)
  }

  def getAction: Byte = action
  def getUsername: String = username
  def getPassword: String = password
  /** The refresh token a REFRESH or LOGOUT carries; null for a login or a signup. */
  def getRefreshToken: Array[Byte] = refreshToken

  override def serialize(): Array[Byte] = {
    val buffer = SerializeUtil.acquireBuffer()

    // [0] Packet Type
    buffer.put(packetType.id)

    // [1-4] Sequence Number
    buffer.putInt(sequenceNumber)

    // [5-20] Player ID (zero UUID - standard header)
    buffer.putLong(playerId.getMostSignificantBits)
    buffer.putLong(playerId.getLeastSignificantBits)

    // [21] Action
    buffer.put(action)

    if (AuthAction.carriesToken(action)) {
      // [22-53] Refresh token (32 bytes)
      val token = if (refreshToken != null) refreshToken else new Array[Byte](0)
      val tokenLen = Math.min(token.length, Constants.REFRESH_TOKEN_SIZE)
      buffer.put(token, 0, tokenLen)
      buffer.put(new Array[Byte](Constants.REFRESH_TOKEN_SIZE - tokenLen))

      // [54-63] Reserved
      buffer.put(new Array[Byte](10))
    } else {
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
    }

    buffer.array().clone()
  }

  // Never the password or the token: this is what a log line would print
  override def toString: String = s"AuthRequestPacket{seq=$sequenceNumber, action=$action}"
}
