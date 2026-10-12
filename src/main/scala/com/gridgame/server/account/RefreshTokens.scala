package com.gridgame.server.account

import com.gridgame.common.Constants

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/**
 * Refresh tokens: what keeps a player logged in between connections (design/networking.md,
 * *Staying logged in*). A login with a password starts a family; the client is given its first
 * token, and every time a token logs the client in again it is exchanged for the next one in the
 * family, which lasts as long as the family does: LifetimeMs from the password, however often it is
 * used. The server keeps only each token's SHA-256 (AuthDatabase), so a copy of the database logs
 * nobody in.
 *
 * A token is taken once. Its exchange is confirmed when the client is first heard on the session it
 * started (Sessions.heardFrom); until then the old token is still taken, since the reply carrying the
 * new one may have been lost with the connection. A token that comes back after its exchange was
 * confirmed was copied: the client never sends one it has replaced. So the whole family is revoked,
 * and the session it is in use on ended, whichever of the two copies that is.
 */
object RefreshTokens {
  /** A family lasts this long from the password login that started it: a month. */
  val LifetimeMs: Long = 30L * 24 * 60 * 60 * 1000
  /** Families an account keeps at once (one a computer it stays logged in on); a new login past
    * this ends the one used longest ago. */
  val MaxFamiliesPerAccount = 10
  /** Used tokens a family keeps the hash of, newest first, to know one when it comes back. */
  val UsedKeptPerFamily = 32

  // A token's state, in the database
  /** The family's newest token: the one its client holds. */
  private[account] val Current = 0
  /** Exchanged for a newer one the client hasn't been heard using yet: still taken, in case the
    * reply was lost. */
  private[account] val Exchanged = 1
  /** Exchanged, and the client has had the newer one since: it should never come back. */
  private[account] val Used = 2

  private val random = new SecureRandom()

  def newToken(): Array[Byte] = {
    val token = new Array[Byte](Constants.REFRESH_TOKEN_SIZE)
    random.nextBytes(token)
    token
  }

  def newFamilyId(): String = {
    val id = new Array[Byte](16)
    random.nextBytes(id)
    hex(id)
  }

  /** What the database keeps of a token. A token is 256 random bits, so a plain hash is as good as a
    * slow one: there is nothing to guess. */
  def hash(token: Array[Byte]): String = hex(MessageDigest.getInstance("SHA-256").digest(token))

  private def hex(bytes: Array[Byte]): String = {
    val sb = new java.lang.StringBuilder(bytes.length * 2)
    bytes.foreach(b => sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16)))
    sb.toString
  }
}

/** A refresh token handed to a client: the token (the server keeps only its hash), its family, and
  * when the family runs out (epoch ms). */
final case class RefreshGrant(token: Array[Byte], familyId: String, expiresAt: Long)

/** What came of a refresh token a client logged in with (AuthDatabase.redeemRefreshToken). */
sealed trait Redemption

object Redemption {
  /** Good: the client logs in as `accountId`, by the name it logged in with, and is given `next`. */
  final case class Granted(next: RefreshGrant, accountId: UUID, accountName: String) extends Redemption
  /** No token of any family still going: never issued, run out, revoked, or long since used. */
  case object Refused extends Redemption
  /** A token whose exchange was confirmed, come back: someone has a copy. Its family is revoked. */
  final case class Reused(familyId: String) extends Redemption
}
