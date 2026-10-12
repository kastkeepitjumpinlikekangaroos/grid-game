package com.gridgame.server

import com.gridgame.server.account.{AuthDatabase, RefreshGrant, RefreshTokens, Redemption}
import org.junit.After
import org.junit.Assert._
import org.junit.Test

import java.io.File
import java.sql.DriverManager
import java.util.UUID

/**
 * Refresh tokens in the account database, in one of the test's own: each logs in once and is
 * exchanged for the next, a copy that comes back ends its family, a family lasts a month from its
 * password, and only a token's hash is ever stored.
 */
class RefreshTokensTest {
  private val file = File.createTempFile("gridgame-refresh", ".db")
  private val db = new AuthDatabase(file.getAbsolutePath)
  private val alice = UUID.randomUUID()
  private val t0 = 1_000_000_000_000L

  @After def close(): Unit = { db.close(); file.delete() }

  private def granted(r: Redemption): Redemption.Granted = r match {
    case g: Redemption.Granted => g
    case other => throw new AssertionError(s"expected a login, got $other")
  }

  /** Log in with `token`, then be heard on the session, as a client that had the reply is. */
  private def exchange(token: Array[Byte], now: Long = t0): RefreshGrant = {
    val next = granted(db.redeemRefreshToken(token, now)).next
    db.confirmRefreshExchange(next.familyId)
    next
  }

  @Test def aTokenLogsInAsItsAccountByTheNameItsPasswordUsed(): Unit = {
    val first = db.issueRefreshToken(alice, "Alice", t0)
    val g = granted(db.redeemRefreshToken(first.token, t0 + 1000))
    assertEquals(alice, g.accountId)
    assertEquals("Alice", g.accountName)
    assertEquals("the same family", first.familyId, g.next.familyId)
    assertFalse("a new token", java.util.Arrays.equals(first.token, g.next.token))
  }

  @Test def aTokenLogsInOnceAndACopyThatComesBackEndsItsFamily(): Unit = {
    // The client never sends a token it has exchanged: one that comes back was copied, and whichever
    // of the two holds the family now, it isn't safe to let it go on
    val first = db.issueRefreshToken(alice, "Alice", t0)
    val second = exchange(first.token)
    assertEquals(Redemption.Reused(first.familyId), db.redeemRefreshToken(first.token, t0 + 2000))
    assertEquals("the family is gone: its newest token too", Redemption.Refused, db.redeemRefreshToken(second.token, t0 + 3000))
  }

  @Test def aRetryAfterALostReplyIsTaken(): Unit = {
    // The connection dropped after the server exchanged the token and before the client had the new
    // one: the client tries again with the one it has, and that has to work
    val first = db.issueRefreshToken(alice, "Alice", t0)
    val lost = granted(db.redeemRefreshToken(first.token, t0)).next
    val retry = exchange(first.token, t0 + 5000)
    assertFalse(java.util.Arrays.equals(lost.token, retry.token))
    // The reply that was lost is no good to anyone who finds it: it is a copy of a dead token
    assertEquals(Redemption.Reused(first.familyId), db.redeemRefreshToken(lost.token, t0 + 6000))
  }

  @Test def comingBackWithTheNewTokenConfirmsTheOldOneWasHad(): Unit = {
    // Not heard on its session (it crashed straight after keeping the new token), but it logs in
    // with the new one: so the old one was had, and is used
    val first = db.issueRefreshToken(alice, "Alice", t0)
    val second = granted(db.redeemRefreshToken(first.token, t0)).next
    exchange(second.token, t0 + 1000)
    assertEquals(Redemption.Reused(first.familyId), db.redeemRefreshToken(first.token, t0 + 2000))
  }

  @Test def aFamilyEndsAMonthAfterItsPasswordHoweverOftenItIsUsed(): Unit = {
    val first = db.issueRefreshToken(alice, "Alice", t0)
    assertEquals(t0 + RefreshTokens.LifetimeMs, first.expiresAt)
    val late = exchange(first.token, t0 + RefreshTokens.LifetimeMs - 1000)
    assertEquals("exchanging it doesn't put its end off", first.expiresAt, late.expiresAt)
    assertEquals(Redemption.Refused, db.redeemRefreshToken(late.token, t0 + RefreshTokens.LifetimeMs))
  }

  @Test def aLogoutEndsTheWholeFamilyByAnyOfItsTokens(): Unit = {
    val first = db.issueRefreshToken(alice, "Alice", t0)
    val second = exchange(first.token)
    assertEquals("an old token still names its family", first.familyId, db.revokeRefreshFamily(first.token))
    assertEquals(Redemption.Refused, db.redeemRefreshToken(second.token, t0 + 1000))
    assertNull("nothing left to end", db.revokeRefreshFamily(second.token))
  }

  @Test def aTokenNobodyWasGivenLogsNobodyIn(): Unit = {
    db.issueRefreshToken(alice, "Alice", t0)
    assertEquals(Redemption.Refused, db.redeemRefreshToken(RefreshTokens.newToken(), t0))
    assertNull(db.revokeRefreshFamily(RefreshTokens.newToken()))
  }

  @Test def familiesAreApart(): Unit = {
    // Logged in on two computers: logging out on one leaves the other logged in
    val laptop = db.issueRefreshToken(alice, "Alice", t0)
    val desktop = db.issueRefreshToken(alice, "Alice", t0 + 10)
    db.revokeRefreshFamily(laptop.token)
    assertEquals(alice, granted(db.redeemRefreshToken(desktop.token, t0 + 20)).accountId)
  }

  @Test def onlyATokensHashIsStored(): Unit = {
    // A copy of the database logs nobody in
    val grant = db.issueRefreshToken(alice, "Alice", t0)
    val conn = DriverManager.getConnection(s"jdbc:sqlite:${file.getAbsolutePath}")
    try {
      val rs = conn.createStatement().executeQuery("SELECT token_hash FROM refresh_tokens")
      assertTrue(rs.next())
      val stored = rs.getString(1)
      assertEquals(RefreshTokens.hash(grant.token), stored)
      val hex = grant.token.map(b => f"${b & 0xff}%02x").mkString
      assertNotEquals(hex, stored)
    } finally conn.close()
    val bytes = java.nio.file.Files.readAllBytes(file.toPath)
    assertFalse("the token's bytes are nowhere in the file", bytes.containsSlice(grant.token.toSeq))
  }

  @Test def anAccountKeepsItsTenLatestFamilies(): Unit = {
    val oldest = db.issueRefreshToken(alice, "Alice", t0)
    val rest = (1 to RefreshTokens.MaxFamiliesPerAccount).map(i => db.issueRefreshToken(alice, "Alice", t0 + i * 1000L))
    assertEquals("the one used longest ago went", Redemption.Refused, db.redeemRefreshToken(oldest.token, t0 + 60000))
    rest.foreach(g => assertEquals(alice, granted(db.redeemRefreshToken(g.token, t0 + 60000)).accountId))
    // Another account's are its own
    val bob = UUID.randomUUID()
    assertEquals(bob, granted(db.redeemRefreshToken(db.issueRefreshToken(bob, "Bob", t0).token, t0)).accountId)
  }

  @Test def aFamilyKeepsOnlyItsLatestUsedTokens(): Unit = {
    var token = db.issueRefreshToken(alice, "Alice", t0).token
    for (i <- 1 to RefreshTokens.UsedKeptPerFamily + 20) token = exchange(token, t0 + i).token
    val conn = DriverManager.getConnection(s"jdbc:sqlite:${file.getAbsolutePath}")
    try {
      val rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM refresh_tokens")
      rs.next()
      assertEquals("the used ones kept, and the current one", RefreshTokens.UsedKeptPerFamily + 1, rs.getInt(1))
    } finally conn.close()
    assertEquals(alice, granted(db.redeemRefreshToken(token, t0 + 100)).accountId)
  }

  @Test def familiesThatRanOutAreDeleted(): Unit = {
    db.issueRefreshToken(alice, "Alice", t0)
    val fresh = db.issueRefreshToken(alice, "Alice", t0 + RefreshTokens.LifetimeMs)
    assertEquals(1, db.purgeExpiredRefreshTokens(t0 + RefreshTokens.LifetimeMs))
    assertEquals(alice, granted(db.redeemRefreshToken(fresh.token, t0 + RefreshTokens.LifetimeMs)).accountId)
  }
}
