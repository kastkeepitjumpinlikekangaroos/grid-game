package com.gridgame.server

import com.gridgame.server.net.RateLimiter
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

  // --- To the millisecond, on a clock of the test's own ---

  @Test def aBudgetIsBackAMillisecondAfterItsWindowAndNoSooner(): Unit = Sim() { clock =>
    val limiter = new RateLimiter()
    val p = UUID.randomUUID()
    val ch = new EmbeddedChannel()
    val ip = InetAddress.getByName("10.0.1.1")
    val budgets = Seq[(String, Int, Long, () => Boolean)](
      ("datagrams", MAX_UDP_PER_SECOND, 1000L, () => limiter.allowPacket(p, isUdp = true)),
      ("TCP packets", MAX_TCP_PER_SECOND, 1000L, () => limiter.allowPacket(p, isUdp = false)),
      ("chat lines", MAX_CHAT_PER_SECOND, 1000L, () => limiter.allowChat(p)),
      ("requests before a login", MAX_PRE_AUTH_PER_SECOND, 1000L, () => limiter.allowPreAuthPacket(ch)),
      ("connections", MAX_CONNECTIONS_PER_MINUTE, 60000L, () => limiter.allowConnection(ip)))
    for ((what, max, windowMs, ask) <- budgets) {
      assertEquals(what, max, allowed(max + 3)(ask()))
      clock.advance(windowMs)
      assertFalse(s"$what: still spent ${windowMs}ms after the first", ask())
      clock.advance(1)
      assertEquals(s"$what: a whole budget again a millisecond later", max, allowed(max + 3)(ask()))
    }
  }

  @Test def aLockoutIsThirtySecondsAndDoublesWithEveryFiveMoreFailuresUpToAnHour(): Unit = Sim() { clock =>
    val limiter = new RateLimiter()
    val ip = InetAddress.getByName("10.0.1.2")
    def fail(times: Int): Unit = for (_ <- 1 to times) limiter.recordAuthFailure(ip)
    val minute = 60000L
    val waits = Seq(30000L, minute, 2 * minute, 4 * minute, 8 * minute, 16 * minute, 32 * minute, 60 * minute, 60 * minute, 60 * minute)
    assertEquals(MAX_COOLDOWN_MS, waits.last)
    for ((wait, batch) <- waits.zipWithIndex) {
      fail(MAX_AUTH_FAILURES)
      val failed = MAX_AUTH_FAILURES * (batch + 1)
      assertFalse(s"$failed failures: locked out", limiter.allowAuthAttempt(ip))
      clock.advance(wait)
      assertFalse(s"$failed failures: still locked out ${wait}ms after the last", limiter.allowAuthAttempt(ip))
      clock.advance(1)
      assertTrue(s"$failed failures: and may try again a millisecond later", limiter.allowAuthAttempt(ip))
    }
  }

  @Test def onceLockedOutEveryFurtherFailureLocksTheAddressOutAgain(): Unit = Sim() { clock =>
    // The five it was first allowed are spent for good (until a login that works): the wait it
    // has earned comes after each wrong password from then on, not after another five
    val limiter = new RateLimiter()
    val ip = InetAddress.getByName("10.0.1.3")
    for (_ <- 1 to MAX_AUTH_FAILURES) limiter.recordAuthFailure(ip)
    clock.advance(BASE_COOLDOWN_MS + 1)
    assertTrue(limiter.allowAuthAttempt(ip))
    assertTrue("as often as it likes, until it fails again", limiter.allowAuthAttempt(ip))
    limiter.recordAuthFailure(ip)
    assertFalse("one more failure: out again", limiter.allowAuthAttempt(ip))
    clock.advance(BASE_COOLDOWN_MS)
    assertFalse(limiter.allowAuthAttempt(ip))
    clock.advance(1)
    assertTrue("for the same thirty seconds, four more times", limiter.allowAuthAttempt(ip))
    limiter.clearAuthFailures(ip)
    for (_ <- 1 until MAX_AUTH_FAILURES) limiter.recordAuthFailure(ip)
    assertTrue("a login that worked gives it its five back", limiter.allowAuthAttempt(ip))
  }

  @Test def anAddressThatHasFailedNothingForTwoHoursIsForgottenWhenTheServerTidiesUp(): Unit = Sim() { clock =>
    def lockedAfterOneMoreFailure(quietForMs: Long, tidy: Boolean): Boolean = {
      val limiter = new RateLimiter()
      val ip = InetAddress.getByName("10.0.1.4")
      for (_ <- 1 to MAX_AUTH_FAILURES) limiter.recordAuthFailure(ip)
      clock.advance(quietForMs)
      if (tidy) limiter.cleanup()
      limiter.recordAuthFailure(ip)
      !limiter.allowAuthAttempt(ip)
    }
    assertTrue("two hours to the millisecond: still remembered", lockedAfterOneMoreFailure(2 * MAX_COOLDOWN_MS, tidy = true))
    assertFalse("a millisecond over: its next failure is its first", lockedAfterOneMoreFailure(2 * MAX_COOLDOWN_MS + 1, tidy = true))
    assertTrue("(it is the tidying that forgets, not the time)", lockedAfterOneMoreFailure(2 * MAX_COOLDOWN_MS + 1, tidy = false))
  }

  @Test def tidyingUpGivesNobodyTheirBudgetBackEarly(): Unit = Sim() { clock =>
    // The server tidies its counters every so often (RateLimiter.cleanup): only ones nobody has
    // touched for a minute go, so a budget that is spent stays spent for the rest of its window
    val limiter = new RateLimiter()
    val p = UUID.randomUUID()
    val ip = InetAddress.getByName("10.0.1.5")
    val asks = Seq[(String, Int, () => Boolean)](
      ("datagrams", MAX_UDP_PER_SECOND, () => limiter.allowPacket(p, isUdp = true)),
      ("TCP packets", MAX_TCP_PER_SECOND, () => limiter.allowPacket(p, isUdp = false)),
      ("chat lines", MAX_CHAT_PER_SECOND, () => limiter.allowChat(p)),
      ("connections", MAX_CONNECTIONS_PER_MINUTE, () => limiter.allowConnection(ip)))
    for ((_, max, ask) <- asks) allowed(max)(ask())
    clock.advance(500)
    limiter.cleanup()
    for ((what, _, ask) <- asks) assertFalse(s"$what: spent, and still spent after a tidy", ask())
    // A minute's connections are a minute's, to the millisecond, tidied or not
    clock.advance(59500)
    limiter.cleanup()
    assertFalse("connections: a minute to the millisecond", limiter.allowConnection(ip))
    clock.advance(1)
    limiter.cleanup()
    assertTrue("and a millisecond over", limiter.allowConnection(ip))
  }

  @Test def aSuccessfulLoginForgetsTheFailures(): Unit = {
    val ip = InetAddress.getByName("10.0.0.5")
    for (_ <- 1 until MAX_AUTH_FAILURES) limiter.recordAuthFailure(ip)
    limiter.clearAuthFailures(ip)
    limiter.recordAuthFailure(ip)
    assertTrue(limiter.allowAuthAttempt(ip))
  }
}
