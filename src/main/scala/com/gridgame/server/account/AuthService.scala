package com.gridgame.server.account

import com.gridgame.common.observability.Attrs
import com.gridgame.common.observability.Metrics
import com.gridgame.common.observability.Tracing
import com.gridgame.common.protocol._
import com.gridgame.server.net.{Outbox, RateLimiter, Sessions}
import io.netty.channel.Channel

import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.UUID

/**
 * Logging in, signing up, logging in again with a refresh token, and logging out. A login that
 * checks out gets a session (Sessions) and a refresh token (RefreshTokens): the session's token goes
 * to the client first, then the refresh token, then the answer, since the client acts on a
 * successful answer by sending packets that must already be signed.
 */
final class AuthService(authDatabase: AuthDatabase, sessions: Sessions, rateLimiter: RateLimiter, outbox: Outbox) {
  def handle(packet: AuthRequestPacket, tcpCh: Channel): Unit = packet.getAction match {
    case AuthAction.LOGOUT => logout(packet.getRefreshToken)
    case action =>
      val name = action match {
        case AuthAction.SIGNUP => "signup"
        case AuthAction.REFRESH => "refresh"
        case _ => "login"
      }
      Tracing.span("auth.request", io.opentelemetry.api.common.Attributes.of(Attrs.Action, name)) {
        val startNs = System.nanoTime()
        val remoteAddr = tcpCh.remoteAddress().asInstanceOf[InetSocketAddress].getAddress
        // Rate limit auth attempts
        if (!rateLimiter.allowAuthAttempt(remoteAddr)) {
          println(s"Auth: Rate limited - $remoteAddr")
          Metrics.authAttempts.add(1L, action match {
            case AuthAction.SIGNUP => Attrs.AuthSignupRateLimited
            case AuthAction.REFRESH => Attrs.AuthRefreshRateLimited
            case _ => Attrs.AuthLoginRateLimited
          })
          Metrics.rateLimitTriggered.add(1L, Attrs.RlAuth)
          outbox.sendVia(new AuthResponsePacket(outbox.nextSeq(), false, null, AuthRules.TooManyAttempts), tcpCh)
        } else {
          action match {
            case AuthAction.SIGNUP => signup(packet.getUsername, packet.getPassword, tcpCh, remoteAddr)
            case AuthAction.REFRESH => refresh(packet.getRefreshToken, tcpCh, remoteAddr)
            case _ => login(packet.getUsername, packet.getPassword, tcpCh, remoteAddr)
          }
          Metrics.authDuration.record((System.nanoTime() - startNs) / 1e6, io.opentelemetry.api.common.Attributes.of(Attrs.Action, name))
        }
      }
  }

  private def signup(username: String, password: String, tcpCh: Channel, remoteAddr: InetAddress): Unit = {
    if (!AuthRules.isValidUsername(username)) {
      // register() refuses these too, and it used to be reported as "Username taken"
      println(s"Auth: Signup failed - '$username' (invalid username)")
      sessions.recordAuthFailure(tcpCh)
      Metrics.authAttempts.add(1L, Attrs.AuthSignupFail)
      outbox.sendVia(new AuthResponsePacket(outbox.nextSeq(), false, null, AuthRules.InvalidUsername), tcpCh)
    } else if (password.length < AuthRules.MinPasswordLength) {
      println(s"Auth: Signup failed - '$username' (password too short)")
      sessions.recordAuthFailure(tcpCh)
      Metrics.authAttempts.add(1L, Attrs.AuthSignupFail)
      outbox.sendVia(new AuthResponsePacket(outbox.nextSeq(), false, null, AuthRules.PasswordTooShort), tcpCh)
    } else if (authDatabase.register(username, password)) {
      val uuid = authDatabase.getOrCreateUUID(username)
      println(s"Auth: New account registered - '$username' (${uuid.toString.substring(0, 8)})")
      Metrics.authAttempts.add(1L, Attrs.AuthSignupSuccess)
      startSession(uuid, username, issue(uuid, username), tcpCh, remoteAddr, AuthRules.AccountCreated, exchanged = false)
    } else {
      println(s"Auth: Signup failed - '$username' (already exists)")
      sessions.recordAuthFailure(tcpCh)
      Metrics.authAttempts.add(1L, Attrs.AuthSignupFail)
      outbox.sendVia(new AuthResponsePacket(outbox.nextSeq(), false, null, AuthRules.UsernameTaken), tcpCh)
    }
  }

  private def login(username: String, password: String, tcpCh: Channel, remoteAddr: InetAddress): Unit = {
    if (authDatabase.authenticate(username, password)) {
      rateLimiter.clearAuthFailures(remoteAddr)
      val uuid = authDatabase.getOrCreateUUID(username)
      println(s"Auth: Login successful - '$username' (${uuid.toString.substring(0, 8)})")
      Metrics.authAttempts.add(1L, Attrs.AuthLoginSuccess)
      startSession(uuid, username, issue(uuid, username), tcpCh, remoteAddr, AuthRules.LoginSuccessful, exchanged = false)
    } else {
      rateLimiter.recordAuthFailure(remoteAddr)
      sessions.recordAuthFailure(tcpCh)
      println(s"Auth: Login failed - '$username' (invalid credentials)")
      Metrics.authAttempts.add(1L, Attrs.AuthLoginFail)
      outbox.sendVia(new AuthResponsePacket(outbox.nextSeq(), false, null, AuthRules.InvalidCredentials), tcpCh)
    }
  }

  /**
   * A login with a refresh token: exchanged for the next in its family, and the client logged in as
   * the account, by the name the family's password login used. Refused as a wrong password is
   * (counted against the address and the connection), so tokens can't be guessed any faster than
   * passwords. A copied token ends its family, and the session it is in use on.
   */
  private def refresh(token: Array[Byte], tcpCh: Channel, remoteAddr: InetAddress): Unit = {
    val redemption =
      try authDatabase.redeemRefreshToken(token, System.currentTimeMillis())
      catch {
        case e: Exception =>
          // The token may be good: say nothing that makes the client forget it
          System.err.println(s"Auth: Refresh login failed - database error: ${e.getMessage}")
          outbox.sendVia(new AuthResponsePacket(outbox.nextSeq(), false, null, AuthRules.TryAgainLater), tcpCh)
          return
      }
    redemption match {
      case Redemption.Granted(next, accountId, accountName) =>
        println(s"Auth: Refresh login - '$accountName' (${accountId.toString.substring(0, 8)})")
        Metrics.authAttempts.add(1L, Attrs.AuthRefreshSuccess)
        startSession(accountId, accountName, next, tcpCh, remoteAddr, AuthRules.LoginSuccessful, exchanged = true)
      case Redemption.Reused(family) =>
        System.err.println(s"Auth: A refresh token came back after it was exchanged (copied?): its login is ended")
        Metrics.authAttempts.add(1L, Attrs.AuthRefreshReused)
        sessions.endFamily(family)
        refuseRefresh(tcpCh, remoteAddr)
      case Redemption.Refused =>
        println(s"Auth: Refresh login refused - $remoteAddr")
        Metrics.authAttempts.add(1L, Attrs.AuthRefreshFail)
        refuseRefresh(tcpCh, remoteAddr)
    }
  }

  private def refuseRefresh(tcpCh: Channel, remoteAddr: InetAddress): Unit = {
    rateLimiter.recordAuthFailure(remoteAddr)
    sessions.recordAuthFailure(tcpCh)
    outbox.sendVia(new AuthResponsePacket(outbox.nextSeq(), false, null, AuthRules.LoginExpired), tcpCh)
  }

  /** A logout: the token's family is revoked, and its sessions ended (this one, if it is one). Nothing
    * is said back, so it tells nobody whether a token was good. */
  private def logout(token: Array[Byte]): Unit = {
    val family =
      try authDatabase.revokeRefreshFamily(token)
      catch {
        case e: Exception =>
          System.err.println(s"Auth: Logout failed - database error: ${e.getMessage}")
          null
      }
    if (family != null) {
      println("Auth: Logged out")
      Metrics.authAttempts.add(1L, Attrs.AuthLogout)
      sessions.endFamily(family)
    }
  }

  /** A password login's new family. A login goes ahead without one if the database can't make it:
    * the client just can't log back in by itself. */
  private def issue(uuid: UUID, username: String): RefreshGrant =
    try authDatabase.issueRefreshToken(uuid, username, System.currentTimeMillis())
    catch {
      case e: Exception =>
        System.err.println(s"Auth: No refresh token for '$username' - database error: ${e.getMessage}")
        null
    }

  /** The session, its token first, then the refresh token (if there is one), then the answer. */
  private def startSession(uuid: UUID, name: String, grant: RefreshGrant, tcpCh: Channel, remoteAddr: InetAddress,
                           message: String, exchanged: Boolean): Unit = {
    sessions.playerTcpAddresses.put(uuid, remoteAddr)
    sessions.accountNames.put(uuid, name)
    val token = sessions.generateSessionToken(uuid, tcpCh, if (grant != null) grant.familyId else null, exchanged)
    // Token first: the client acts on a successful AUTH_RESPONSE by sending packets that must be
    // signed, so it has to hold the token by then. The refresh token after it, signed with it.
    outbox.sendVia(new SessionTokenPacket(outbox.nextSeq(), uuid, token), tcpCh)
    if (grant != null) outbox.sendVia(new RefreshTokenPacket(outbox.nextSeq(), uuid, grant.token, grant.expiresAt), tcpCh)
    outbox.sendVia(new AuthResponsePacket(outbox.nextSeq(), true, uuid, message), tcpCh)
  }
}
