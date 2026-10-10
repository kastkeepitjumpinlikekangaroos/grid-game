package com.gridgame.server

import com.gridgame.server.account.{AuthDatabase, MatchRow}
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
    db.saveMatch(1, 5, Seq(MatchRow(fay, 7, 2, 1)), matchType = 0, playerCount = 8)
    db.saveMatch(2, 3, Seq(MatchRow(fay, 1, 4, 3)), matchType = 3, playerCount = 2)
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
    db.saveMatch(0, 30, Seq(MatchRow(gus, 40, 0, 1)), matchType = AuthDatabase.PracticeMatchType.toByte, playerCount = 6)
    val (k, _, played, wins, _) = db.getPlayerStats(gus)
    assertEquals((0, 0, 0), (k, played, wins))
    assertEquals("still in the history", 1, db.getMatchHistory(gus).size)
  }

  @Test def aMatchKeepsEveryonesCharacterAndItsBots(): Unit = {
    db.register("kim", "secret1")
    val kim = db.getOrCreateUUID("kim")
    val bot = new UUID(0L, 7L)
    val id = db.saveMatch(4, 5, Seq(MatchRow(bot, 12, 3, 1, Some(51), bot = true), MatchRow(kim, 9, 4, 2, Some(8))),
      playerCount = 2)
    assertEquals(Seq(MatchRow(bot, 12, 3, 1, Some(51), bot = true), MatchRow(kim, 9, 4, 2, Some(8))), db.getMatchRows(id))
    // A bot's row is nobody's record
    val (k, d, played, wins, _) = db.getPlayerStats(kim)
    assertEquals((9, 4, 1, 0), (k, d, played, wins))
    assertEquals(Seq("kim"), db.getLeaderboard().map(_._1))
  }

  @Test def aRowSavedBeforeCharactersWereHasNone(): Unit = {
    // The columns are added to a table that already has rows: an old database, opened again
    val old = File.createTempFile("gridgame-old", ".db")
    try {
      val conn = java.sql.DriverManager.getConnection(s"jdbc:sqlite:${old.getAbsolutePath}")
      val s = conn.createStatement()
      s.executeUpdate("CREATE TABLE matches(match_id INTEGER PRIMARY KEY AUTOINCREMENT, map_index INTEGER NOT NULL, " +
        "duration_minutes INTEGER NOT NULL, played_at INTEGER NOT NULL, player_count INTEGER NOT NULL)")
      s.executeUpdate("CREATE TABLE match_results(id INTEGER PRIMARY KEY AUTOINCREMENT, match_id INTEGER NOT NULL, " +
        "player_uuid TEXT NOT NULL, kills INTEGER NOT NULL, deaths INTEGER NOT NULL, rank INTEGER NOT NULL)")
      val someone = UUID.randomUUID()
      s.executeUpdate("INSERT INTO matches(map_index, duration_minutes, played_at, player_count) VALUES(0, 5, 0, 15)")
      s.executeUpdate(s"INSERT INTO match_results(match_id, player_uuid, kills, deaths, rank) VALUES(1, '$someone', 31, 11, 2)")
      conn.close()
      val reopened = new AuthDatabase(old.getAbsolutePath)
      try assertEquals(Seq(MatchRow(someone, 31, 11, 2)), reopened.getMatchRows(1))
      finally reopened.close()
    } finally old.delete()
  }

  @Test def theLeaderboardIsByRating(): Unit = {
    for (name <- Seq("hal", "ivy", "jon")) db.register(name, "secret1")
    db.updateElo("hal", 900)
    db.updateElo("ivy", 1500)
    db.updateElo("jon", 1200)
    assertEquals(Seq("ivy", "jon", "hal"), db.getLeaderboard().map(_._1).take(3))
  }

  /** The statements the connection has open, by sqlite-jdbc's own count (reached by reflection:
    * nothing public says). */
  private def openStatements(db: AuthDatabase): Int = {
    val field = classOf[AuthDatabase].getDeclaredFields.find(_.getName.endsWith("connection")).get
    field.setAccessible(true)
    val conn = field.get(db)
    val native = conn.getClass.getMethod("getDatabase").invoke(conn)
    val stmts = Iterator.iterate[Class[_]](native.getClass)(_.getSuperclass).takeWhile(_ != null)
      .flatMap(_.getDeclaredFields).find(_.getName == "stmts").get
    stmts.setAccessible(true)
    stmts.get(native).asInstanceOf[java.util.Set[_]].size
  }

  /** Run `sql` against the test's database on a connection of its own. */
  private def otherConnection[T](body: java.sql.Connection => T): T = {
    val conn = java.sql.DriverManager.getConnection(s"jdbc:sqlite:${file.getAbsolutePath}")
    try body(conn) finally conn.close()
  }

  @Test def nothingIsLeftOpenOnceTheDatabaseIsOpen(): Unit = {
    // Two of its first queries' statements were never closed
    assertEquals(0, openStatements(db))
  }

  @Test def aQueryThatFailsLeavesNothingOpen(): Unit = {
    // Statements were closed only on the way out of a query that worked: one that threw (the
    // database locked by another connection, say) left its native statement behind
    otherConnection(_.createStatement().executeUpdate(
      "INSERT INTO match_results(match_id, player_uuid, kills, deaths, rank) VALUES (77, 'not a player', 0, 0, 1)"))
    try {
      db.getMatchRows(77)
      fail("a row that isn't a player's can't be read")
    } catch { case _: IllegalArgumentException => }
    assertEquals(0, openStatements(db))
  }

  @Test def theLeaderboardIsReadThroughIndexes(): Unit = {
    // It joined every account to every result and grouped them: each request read the whole table,
    // the bots' rows and all, and took longer the longer the server was played on
    val plan = otherConnection { conn =>
      val st = conn.prepareStatement("EXPLAIN QUERY PLAN " + AuthDatabase.LeaderboardQuery)
      st.setInt(1, 50)
      val rs = st.executeQuery()
      Iterator.continually(rs).takeWhile(_.next()).map(_.getString("detail")).toList
    }
    assertTrue(plan.mkString("\n"), plan.exists(_.contains("idx_match_results_player")))
    assertTrue(plan.mkString("\n"), plan.filter(_.startsWith("SCAN")).forall(_.contains("INDEX")))
    val indexes = otherConnection { conn =>
      val rs = conn.createStatement().executeQuery("SELECT name FROM sqlite_master WHERE type = 'index'")
      Iterator.continually(rs).takeWhile(_.next()).map(_.getString(1)).toSet
    }
    assertTrue("a player's rows, for a profile and its history", indexes("idx_match_results_player"))
    assertTrue("a match's rows", indexes("idx_match_results_match"))
  }
}
