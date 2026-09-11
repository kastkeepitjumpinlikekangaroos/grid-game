package com.gridgame.server

import org.junit.After
import org.junit.Assert._
import org.junit.Test

import java.io.File
import java.util.UUID

/** Accounts, match records and ratings, in a database of the test's own. */
class AuthDatabaseTest {
  private val file = File.createTempFile("gridgame-auth", ".db")
  private val db = new AuthDatabase(file.getAbsolutePath)

  @After def close(): Unit = { db.close(); file.delete() }

  @Test def anAccountLogsInWithItsPasswordOnly(): Unit = {
    assertTrue(db.register("alice", "secret1"))
    assertTrue(db.authenticate("alice", "secret1"))
    assertFalse(db.authenticate("alice", "secret2"))
    assertFalse(db.authenticate("nobody", "secret1"))
  }

  @Test def usernamesAreCaseInsensitive(): Unit = {
    assertTrue(db.register("Bob", "secret1"))
    assertTrue(db.authenticate("BOB", "secret1"))
    assertFalse("taken, whatever the case", db.register("bob", "another"))
    assertEquals(db.getOrCreateUUID("Bob"), db.getOrCreateUUID("bOB"))
  }

  @Test def badUsernamesAndShortPasswordsAreRefused(): Unit = {
    assertFalse(db.register("John Doe", "secret1"))
    assertFalse(db.register("x" * 21, "secret1"))
    assertFalse(db.register("carol", "12345"))
    assertFalse(db.register("", "secret1"))
  }

  @Test def anAccountsIdIsStableAndFindsItsName(): Unit = {
    db.register("dave", "secret1")
    val id = db.getOrCreateUUID("dave")
    assertEquals(id, db.getOrCreateUUID("dave"))
    assertEquals("dave", db.getUsernameByUUID(id))
    assertNull(db.getUsernameByUUID(UUID.randomUUID()))
  }

  @Test def ratingsStartAt1000AndUpdate(): Unit = {
    db.register("erin", "secret1")
    val id = db.getOrCreateUUID("erin")
    assertEquals(1000, db.getEloByUUID(id))
    db.updateElo("erin", 1234)
    assertEquals(1234, db.getEloByUUID(id))
    assertEquals(1000, db.getEloByUUID(UUID.randomUUID()))
  }

  @Test def matchesAreRecordedAndCounted(): Unit = {
    db.register("fay", "secret1")
    val fay = db.getOrCreateUUID("fay")
    db.saveMatch(1, 5, Seq((fay, 7, 2, 1.toByte)), matchType = 0, playerCount = 8)
    db.saveMatch(2, 3, Seq((fay, 1, 4, 3.toByte)), matchType = 3, playerCount = 2)
    val history = db.getMatchHistory(fay)
    assertEquals(2, history.size)
    val (_, map, duration, _, kills, deaths, rank, players, kind) = history.find(_._2 == 1).get
    assertEquals((1, 5, 7, 2, 1, 8, 0), (map, duration, kills, deaths, rank, players, kind))
    val (k, d, played, wins, _) = db.getPlayerStats(fay)
    assertEquals((8, 6, 2, 1), (k, d, played, wins))
  }

  @Test def practiceIsNotARecord(): Unit = {
    db.register("gus", "secret1")
    val gus = db.getOrCreateUUID("gus")
    db.saveMatch(0, 30, Seq((gus, 40, 0, 1.toByte)), matchType = AuthDatabase.PracticeMatchType.toByte, playerCount = 6)
    val (k, _, played, wins, _) = db.getPlayerStats(gus)
    assertEquals((0, 0, 0), (k, played, wins))
    assertEquals("still in the history", 1, db.getMatchHistory(gus).size)
  }

  @Test def theLeaderboardIsByRating(): Unit = {
    for (name <- Seq("hal", "ivy", "jon")) db.register(name, "secret1")
    db.updateElo("hal", 900)
    db.updateElo("ivy", 1500)
    db.updateElo("jon", 1200)
    assertEquals(Seq("ivy", "jon", "hal"), db.getLeaderboard().map(_._1).take(3))
  }
}
