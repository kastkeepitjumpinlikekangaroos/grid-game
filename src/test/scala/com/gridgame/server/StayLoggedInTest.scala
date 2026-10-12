package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.model.Position
import com.gridgame.common.protocol._
import com.gridgame.server.account.RefreshTokens
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.junit.Assert._
import org.junit.Test

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable

/**
 * Staying logged in (design/networking.md): a login's refresh token logs the client in again without
 * the password, once; a copy of it that comes back ends its login and the session it is in use on;
 * a logout ends it too; and a session the server ends on purpose is told why. Through the server's
 * real TCP handler, as a client's connection goes, each test from an address of its own, so the
 * failed logins one counts don't hold up another.
 */
class StayLoggedInTest {
  import ServerTestKit._

  import StayLoggedInTest._

  /** A name no other test has used. */
  private def fresh(): String = s"p${names.incrementAndGet()}_$suffix"

  /** A connection from `host`, as a client opens one: it reads what the server writes unsigned until
    * it has a session token, and only what that token signed after. */
  private class Conn(host: String = nextHost()) {
    val channel = new PeerChannel(host)
    channel.pipeline().addLast(server.tcpHandler())
    var sessionToken: Array[Byte] = _
    var playerId: UUID = _
    private var seq = 0

    def auth(p: AuthRequestPacket): Unit = {
      // Before a login there is nothing to sign with: the payload, padded to size
      val bytes = new Array[Byte](Constants.PACKET_SIZE)
      System.arraycopy(p.serialize(), 0, bytes, 0, Constants.PACKET_PAYLOAD_SIZE)
      channel.writeInbound(Unpooled.wrappedBuffer(bytes))
    }
    def signup(name: String): Unit = auth(new AuthRequestPacket(0, AuthAction.SIGNUP, name, "secret1"))
    def login(name: String): Unit = auth(new AuthRequestPacket(0, AuthAction.LOGIN, name, "secret1"))
    def refresh(token: Array[Byte]): Unit = auth(new AuthRequestPacket(0, AuthAction.REFRESH, token))
    def logout(token: Array[Byte]): Unit = auth(new AuthRequestPacket(0, AuthAction.LOGOUT, token))

    /** The join a client sends once it is in: its first packet signed with the session's token. */
    def join(): Unit = {
      seq += 1
      val p = new PlayerJoinPacket(seq, playerId, new Position(1, 1), 0xFF00FF00, "whatever it says")
      channel.writeInbound(Unpooled.wrappedBuffer(PacketSigner.sign(p.serialize(), sessionToken)))
    }

    /** What the server wrote since the last call, in order. */
    def received(): Seq[Packet] = {
      channel.runPendingTasks()
      val out = mutable.Buffer[Packet]()
      Iterator.continually(channel.readOutbound[ByteBuf]()).takeWhile(_ != null).foreach { buf =>
        val bytes = new Array[Byte](buf.readableBytes())
        buf.readBytes(bytes)
        buf.release()
        val payload =
          if (sessionToken == null) bytes.take(Constants.PACKET_PAYLOAD_SIZE)
          else PacketSigner.verify(bytes, sessionToken)
        assertNotNull("signed with the session's token", payload)
        val p = PacketSerializer.deserialize(payload)
        p match {
          case t: SessionTokenPacket => sessionToken = t.getSessionToken; playerId = t.getPlayerId
          case _ =>
        }
        out += p
      }
      out.toSeq
    }

    /** Log in with `ask` and hand back the refresh token the login gave. */
    def loggedIn(ask: => Unit): Array[Byte] = {
      ask
      val got = received()
      val answer = got.collectFirst { case r: AuthResponsePacket => r }.get
      assertTrue(s"logged in: ${answer.getMessage}", answer.getSuccess)
      got.collectFirst { case r: RefreshTokenPacket => r.getRefreshToken }.get
    }

    def answer(): AuthResponsePacket = received().collectFirst { case r: AuthResponsePacket => r }.orNull

    def endedBecause(): Option[Byte] = received().collectFirst { case e: SessionEndPacket => e.getReason }
  }

  @Test def aLoginsReplyCarriesARefreshTokenBetweenTheSessionsTokenAndTheAnswer(): Unit = {
    // The session's token first, so the refresh token is signed with it, and both before the answer,
    // which the client acts on
    val c = new Conn()
    c.signup(fresh())
    val got = c.received()
    assertEquals(Seq(PacketType.SESSION_TOKEN, PacketType.REFRESH_TOKEN, PacketType.AUTH_RESPONSE), got.map(_.getType))
    val r = got(1).asInstanceOf[RefreshTokenPacket]
    assertEquals(c.playerId, r.getPlayerId)
    assertEquals(Constants.REFRESH_TOKEN_SIZE, r.getRefreshToken.length)
    val monthFromNow = System.currentTimeMillis() + RefreshTokens.LifetimeMs
    assertTrue("good for a month", Math.abs(r.getExpiresAt - monthFromNow) < 60000)

    val again = new Conn()
    again.login(fresh() + "x") // no such account
    assertFalse("a refused login gets none", again.received().exists(_.isInstanceOf[RefreshTokenPacket]))
  }

  @Test def theRefreshTokenLogsInAgainAsTheSameAccountByItsName(): Unit = {
    val name = "Kept_" + fresh()
    val first = new Conn()
    val token = first.loggedIn(first.signup(name))
    first.channel.close() // the connection drops

    val second = new Conn()
    val next = second.loggedIn(second.refresh(token))
    assertEquals("the same account", first.playerId, second.playerId)
    assertFalse("and a token of its own", java.util.Arrays.equals(token, next))
    second.join()
    assertEquals("by the name it logged in with, not the join's", name, server.getConnectedPlayer(second.playerId).getName)
  }

  @Test def aCopyOfAnExchangedTokenEndsTheLoginAndTheSessionItIsOn(): Unit = {
    val first = new Conn()
    val token = first.loggedIn(first.signup(fresh()))
    first.channel.close()
    val second = new Conn()
    val next = second.loggedIn(second.refresh(token))
    second.join() // heard on the session: the exchange is confirmed, the first token given up

    // The first token again: a copy (someone read it off the disk). Whoever holds the login now, it
    // is ended, and they are told
    val thief = new Conn()
    thief.refresh(token)
    assertEquals(AuthRules.LoginExpired, thief.answer().getMessage)
    assertEquals(Some(SessionEndReason.REVOKED), second.endedBecause())
    assertFalse("its session is closed", second.channel.isOpen)
    val late = new Conn()
    late.refresh(next)
    assertEquals("and its newest token is no good either", AuthRules.LoginExpired, late.answer().getMessage)
  }

  @Test def aRetryAfterTheReplyWasLostLogsIn(): Unit = {
    // The connection dropped as the server exchanged the token: the client never had the new one,
    // and logs in again with the one it has. Until it is heard on a session, that one still works.
    val first = new Conn()
    val token = first.loggedIn(first.signup(fresh()))
    first.channel.close()
    val cutOff = new Conn()
    cutOff.refresh(token)
    cutOff.channel.close() // gone before the client read a word of it

    val retry = new Conn()
    retry.loggedIn(retry.refresh(token))
    retry.join()

    // Now it has been heard: the token is given up for good
    val again = new Conn()
    again.refresh(token)
    assertEquals(AuthRules.LoginExpired, again.answer().getMessage)
  }

  @Test def aSessionALoginElsewhereReplacesIsToldWhy(): Unit = {
    // So its client waits for the player instead of logging back in by itself, which would throw the
    // new one out, and that one it, for ever
    val name = fresh()
    val here = new Conn()
    here.loggedIn(here.signup(name))
    here.join()
    val there = new Conn()
    there.loggedIn(there.login(name))
    assertEquals(Some(SessionEndReason.REPLACED), here.endedBecause())
    assertFalse(here.channel.isOpen)
  }

  @Test def aLogoutEndsTheLoginAndItsSession(): Unit = {
    val c = new Conn()
    val token = c.loggedIn(c.signup(fresh()))
    c.join()
    c.logout(token)
    assertEquals(Some(SessionEndReason.REVOKED), c.endedBecause())
    assertFalse(c.channel.isOpen)
    val later = new Conn()
    later.refresh(token)
    assertEquals(AuthRules.LoginExpired, later.answer().getMessage)
  }

  @Test def aLogoutFromAnotherConnectionEndsTheSessionItsLoginIsOn(): Unit = {
    // "Use another account" on the saved login's card ends it from a connection of its own
    val c = new Conn()
    val token = c.loggedIn(c.signup(fresh()))
    c.join()
    val other = new Conn()
    other.logout(token)
    assertEquals(Some(SessionEndReason.REVOKED), c.endedBecause())
    assertTrue("nothing is said to the one that asked", other.received().isEmpty)
  }

  @Test def aLogoutSaysNothingAboutAToken(): Unit = {
    // Whether a token was any good is not for anyone who has one to find out this way
    val c = new Conn()
    c.logout(RefreshTokens.newToken())
    assertTrue(c.received().isEmpty)
    assertTrue(c.channel.isOpen)
  }

  @Test def refusedRefreshTokensCountAsFailedLogins(): Unit = {
    // Or tokens could be guessed far faster than passwords (not that 256 bits can be)
    val host = nextHost()
    for (_ <- 0 until 5) {
      val c = new Conn(host)
      c.refresh(RefreshTokens.newToken())
      assertEquals(AuthRules.LoginExpired, c.answer().getMessage)
    }
    val c = new Conn(host)
    c.refresh(RefreshTokens.newToken())
    assertEquals(AuthRules.TooManyAttempts, c.answer().getMessage)
  }
}

object StayLoggedInTest {
  // Shared by every test in the class (JUnit makes an instance a test): one address a connection,
  // and one run's names
  private val hosts = new AtomicInteger(10)
  private def nextHost(): String = {
    val n = hosts.incrementAndGet()
    s"127.0.${n / 250}.${n % 250 + 1}"
  }
  private val suffix = (System.nanoTime() % 100000).toString
  private val names = new AtomicInteger()
}
