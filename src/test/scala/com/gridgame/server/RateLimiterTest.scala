package com.gridgame.server

import io.netty.channel.embedded.EmbeddedChannel
import org.junit.Assert._
import org.junit.Test

import java.net.InetAddress
import java.util.UUID

/** How much of anything the server takes from one player, one connection or one address. */
class RateLimiterTest {
  import RateLimiter._

  private val limiter = new RateLimiter()

  private def allowed(times: Int)(ask: => Boolean): Int = (1 to times).count(_ => ask)

  @Test def aPlayerHasABudgetOfDatagramsASecondAndOneOfTcpPackets(): Unit = {
    val p = UUID.randomUUID()
    assertEquals(MAX_UDP_PER_SECOND, allowed(MAX_UDP_PER_SECOND + 50)(limiter.allowPacket(p, isUdp = true)))
    assertEquals("counted apart from its datagrams", MAX_TCP_PER_SECOND,
      allowed(MAX_TCP_PER_SECOND + 10)(limiter.allowPacket(p, isUdp = false)))
  }

  @Test def playersAreCountedApart(): Unit = {
    val a = UUID.randomUUID()
    allowed(MAX_UDP_PER_SECOND + 1)(limiter.allowPacket(a, isUdp = true))
    assertFalse(limiter.allowPacket(a, isUdp = true))
    assertTrue(limiter.allowPacket(UUID.randomUUID(), isUdp = true))
  }

  @Test def theBudgetComesBackWithTheNextSecond(): Unit = {
    val p = UUID.randomUUID()
    allowed(MAX_TCP_PER_SECOND + 1)(limiter.allowPacket(p, isUdp = false))
    assertFalse(limiter.allowPacket(p, isUdp = false))
    Thread.sleep(1100)
    assertTrue(limiter.allowPacket(p, isUdp = false))
  }

  @Test def chatIsFiveLinesASecond(): Unit = {
    val p = UUID.randomUUID()
    assertEquals(MAX_CHAT_PER_SECOND, allowed(MAX_CHAT_PER_SECOND + 3)(limiter.allowChat(p)))
  }

  @Test def anAddressMayConnectFiveTimesAMinute(): Unit = {
    val ip = InetAddress.getByName("10.0.0.1")
    assertEquals(MAX_CONNECTIONS_PER_MINUTE, allowed(MAX_CONNECTIONS_PER_MINUTE + 2)(limiter.allowConnection(ip)))
    assertTrue("another address may", limiter.allowConnection(InetAddress.getByName("10.0.0.2")))
  }

  @Test def beforeLoggingInAConnectionMaySendFiveRequestsASecond(): Unit = {
    val ch = new EmbeddedChannel()
    assertEquals(MAX_PRE_AUTH_PER_SECOND, allowed(MAX_PRE_AUTH_PER_SECOND + 3)(limiter.allowPreAuthPacket(ch)))
    assertTrue("another connection may", limiter.allowPreAuthPacket(new EmbeddedChannel()))
    limiter.removeChannel(ch)
    assertTrue("and a closed one is forgotten", limiter.allowPreAuthPacket(ch))
  }

  @Test def anAddressIsLockedOutAfterFiveFailedLogins(): Unit = {
    val ip = InetAddress.getByName("10.0.0.3")
    for (_ <- 1 until MAX_AUTH_FAILURES) {
      assertTrue(limiter.allowAuthAttempt(ip))
      limiter.recordAuthFailure(ip)
    }
    assertTrue("four failures still may try", limiter.allowAuthAttempt(ip))
    limiter.recordAuthFailure(ip)
    assertFalse("the fifth locks it out", limiter.allowAuthAttempt(ip))
    assertTrue("nobody else", limiter.allowAuthAttempt(InetAddress.getByName("10.0.0.4")))
  }

  @Test def aSuccessfulLoginForgetsTheFailures(): Unit = {
    val ip = InetAddress.getByName("10.0.0.5")
    for (_ <- 1 until MAX_AUTH_FAILURES) limiter.recordAuthFailure(ip)
    limiter.clearAuthFailures(ip)
    limiter.recordAuthFailure(ip)
    assertTrue(limiter.allowAuthAttempt(ip))
  }
}
