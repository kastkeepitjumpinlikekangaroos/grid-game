package com.gridgame.server.account

import com.gridgame.common.observability.Attrs
import com.gridgame.common.observability.Metrics
import com.gridgame.common.observability.Tracing
import com.gridgame.common.protocol._
import com.gridgame.server.net.{Outbox, RateLimiter, Sessions}
import io.netty.channel.Channel

import java.net.InetSocketAddress

/**
 * Logging in and signing up. A login that checks out gets a session (Sessions): its token goes to
 * the client first, then the answer, since the client acts on a successful answer by sending
 * packets that must already be signed.
 */
final class AuthService(authDatabase: AuthDatabase, sessions: Sessions, rateLimiter: RateLimiter, outbox: Outbox) {
  def handle(packet: AuthRequestPacket, tcpCh: Channel): Unit = Tracing.span("auth.request", io.opentelemetry.api.common.Attributes.of(Attrs.Action, if (packet.getAction == AuthAction.SIGNUP) "signup" else "login")) {
    val username = packet.getUsername
    val password = packet.getPassword
    val isSignup = packet.getAction == AuthAction.SIGNUP
    val startNs = System.nanoTime()

    // Rate limit auth attempts
    val remoteAddr = tcpCh.remoteAddress().asInstanceOf[InetSocketAddress].getAddress
    if (!rateLimiter.allowAuthAttempt(remoteAddr)) {
      println(s"Auth: Rate limited - $remoteAddr")
      Metrics.authAttempts.add(1L, if (isSignup) Attrs.AuthSignupRateLimited else Attrs.AuthLoginRateLimited)
      Metrics.rateLimitTriggered.add(1L, Attrs.RlAuth)
      val response = new AuthResponsePacket(outbox.nextSeq(), false, null, AuthRules.TooManyAttempts)
      outbox.sendVia(response, tcpCh)
      return
    }

    if (isSignup) {
      if (!AuthRules.isValidUsername(username)) {
        // register() refuses these too, and it used to be reported as "Username taken"
        println(s"Auth: Signup failed - '$username' (invalid username)")
        sessions.recordAuthFailure(tcpCh)
        Metrics.authAttempts.add(1L, Attrs.AuthSignupFail)
        outbox.sendVia(new AuthResponsePacket(outbox.nextSeq(), false, null, AuthRules.InvalidUsername), tcpCh)
        return
      }
      if (password.length < AuthRules.MinPasswordLength) {
        println(s"Auth: Signup failed - '$username' (password too short)")
        sessions.recordAuthFailure(tcpCh)
        Metrics.authAttempts.add(1L, Attrs.AuthSignupFail)
        val response = new AuthResponsePacket(outbox.nextSeq(), false, null, AuthRules.PasswordTooShort)
        outbox.sendVia(response, tcpCh)
        return
      }
      val registered = authDatabase.register(username, password)
      if (registered) {
        val uuid = authDatabase.getOrCreateUUID(username)
        println(s"Auth: New account registered - '$username' (${uuid.toString.substring(0, 8)})")
        sessions.playerTcpAddresses.put(uuid, remoteAddr)
        sessions.accountNames.put(uuid, username)
        val token = sessions.generateSessionToken(uuid, tcpCh)
        Metrics.authAttempts.add(1L, Attrs.AuthSignupSuccess)
        // Token first: the client acts on a successful AUTH_RESPONSE by sending packets that
        // must be signed, so it has to hold the token by then.
        val tokenPacket = new SessionTokenPacket(outbox.nextSeq(), uuid, token)
        outbox.sendVia(tokenPacket, tcpCh)
        val response = new AuthResponsePacket(outbox.nextSeq(), true, uuid, AuthRules.AccountCreated)
        outbox.sendVia(response, tcpCh)
      } else {
        println(s"Auth: Signup failed - '$username' (already exists)")
        sessions.recordAuthFailure(tcpCh)
        Metrics.authAttempts.add(1L, Attrs.AuthSignupFail)
        val response = new AuthResponsePacket(outbox.nextSeq(), false, null, AuthRules.UsernameTaken)
        outbox.sendVia(response, tcpCh)
      }
    } else {
      val authenticated = authDatabase.authenticate(username, password)
      if (authenticated) {
        rateLimiter.clearAuthFailures(remoteAddr)
        val uuid = authDatabase.getOrCreateUUID(username)
        println(s"Auth: Login successful - '$username' (${uuid.toString.substring(0, 8)})")
        sessions.playerTcpAddresses.put(uuid, remoteAddr)
        sessions.accountNames.put(uuid, username)
        val token = sessions.generateSessionToken(uuid, tcpCh)
        Metrics.authAttempts.add(1L, Attrs.AuthLoginSuccess)
        // Token first, as for signup
        val tokenPacket = new SessionTokenPacket(outbox.nextSeq(), uuid, token)
        outbox.sendVia(tokenPacket, tcpCh)
        val response = new AuthResponsePacket(outbox.nextSeq(), true, uuid, AuthRules.LoginSuccessful)
        outbox.sendVia(response, tcpCh)
      } else {
        rateLimiter.recordAuthFailure(remoteAddr)
        sessions.recordAuthFailure(tcpCh)
        println(s"Auth: Login failed - '$username' (invalid credentials)")
        Metrics.authAttempts.add(1L, Attrs.AuthLoginFail)
        val response = new AuthResponsePacket(outbox.nextSeq(), false, null, AuthRules.InvalidCredentials)
        outbox.sendVia(response, tcpCh)
      }
    }
    Metrics.authDuration.record((System.nanoTime() - startNs) / 1e6, io.opentelemetry.api.common.Attributes.of(Attrs.Action, if (isSignup) "signup" else "login"))
  }
}
