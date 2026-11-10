package com.gridgame.server

import com.gridgame.server.account.AuthDatabase
import com.gridgame.server.game.GameInstance
import com.gridgame.server.lobby.{Lobby, LobbyStatus}
import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

import java.util.UUID

/** The end of a match: it comes when the time is up, and everyone gets the final scoreboard, once. */
class EndGameTest {
  private val m = new TestMatch()
  private val server = m.server

  private def lobbyFor(players: Player*): Lobby = {
    val lobby = server.lobbyManager.createLobby(players.head.getId, "end", 0, 5, 8)
    players.tail.foreach { p => lobby.addPlayer(p.getId); server.lobbyManager.setPlayerLobby(p.getId, lobby.id) }
    lobby.gameInstance = m.instance
    lobby.status = LobbyStatus.IN_GAME
    lobby
  }

  private def scoreboard(packets: Seq[Packet]): Seq[GameEventPacket] =
    packets.collect { case e: GameEventPacket if e.getEventType != GameEvent.KILL => e }

  @Test def everyoneGetsTheFinalScoreboardInOrder(): Unit = {
    val a = m.join(CharacterId.Gladiator, 10, 10)
    val b = m.join(CharacterId.Wizard, 20, 20)
    val c = m.join(CharacterId.Soldier, 30, 30)
    val lobby = lobbyFor(a, b, c)
    val kt = m.instance.killTracker
    kt.recordKill(a.getId, b.getId)
    kt.recordKill(a.getId, c.getId)
    kt.recordKill(c.getId, b.getId)
    m.clearSent()
    server.endGame(lobby.id)
    for (p <- Seq(a, b, c)) {
      val events = scoreboard(m.tcpSent(p))
      assertEquals(GameEvent.GAME_OVER, events.head.getEventType)
      assertEquals(GameEvent.SCORE_END, events.last.getEventType)
      val entries = events.filter(_.getEventType == GameEvent.SCORE_ENTRY)
        .map(e => (e.getPlayerId, e.getKills.toInt, e.getDeaths.toInt, e.getRank.toInt))
      assertEquals(Seq((a.getId, 2, 0, 1), (c.getId, 1, 1, 2), (b.getId, 0, 2, 3)), entries)
    }
    assertNull("the lobby is gone", server.lobbyManager.getLobby(lobby.id))
    assertFalse(m.instance.isRunning)
  }

  @Test def theMatchIsWonOnKillsLessDeaths(): Unit = {
    // a has the most kills but died as often (3 - 3); c killed fewer and died less (2 - 1)
    val a = m.join(CharacterId.Gladiator, 10, 10)
    val b = m.join(CharacterId.Wizard, 20, 20)
    val c = m.join(CharacterId.Soldier, 30, 30)
    val lobby = lobbyFor(a, b, c)
    val kt = m.instance.killTracker
    kt.recordKill(a.getId, b.getId)
    kt.recordKill(a.getId, b.getId)
    kt.recordKill(a.getId, c.getId)
    kt.recordKill(b.getId, a.getId)
    kt.recordKill(c.getId, a.getId)
    kt.recordKill(c.getId, a.getId)
    m.clearSent()
    server.endGame(lobby.id)
    val entries = scoreboard(m.tcpSent(a)).filter(_.getEventType == GameEvent.SCORE_ENTRY)
      .map(e => (e.getPlayerId, e.getKills.toInt, e.getDeaths.toInt, e.getRank.toInt))
    assertEquals(Seq((c.getId, 2, 1, 1), (a.getId, 3, 3, 2), (b.getId, 1, 2, 3)), entries)
    // And the win is c's: what the profile and the leaderboard count
    val db = server.authDatabase
    assertEquals(1, db.getPlayerStats(c.getId)._4)
    assertEquals(0, db.getPlayerStats(a.getId)._4)
  }

  @Test def aMatchEndsOnce(): Unit = {
    // The timer and a practice player leaving can both end it; it must be scored once
    val a = m.join(CharacterId.Gladiator, 10, 10)
    val lobby = lobbyFor(a)
    server.endGame(lobby.id)
    m.clearSent()
    lobby.status = LobbyStatus.IN_GAME
    server.endGame(lobby.id)
    assertTrue(scoreboard(m.tcpSent(a)).isEmpty)
  }

  @Test def aPlayerWhoLeftIsStillScored(): Unit = {
    val a = m.join(CharacterId.Gladiator, 10, 10)
    val b = m.join(CharacterId.Wizard, 20, 20)
    val lobby = lobbyFor(a, b)
    m.instance.killTracker.recordKill(b.getId, a.getId)
    m.instance.handler.removePlayer(b.getId)
    m.clearSent()
    server.endGame(lobby.id)
    val entries = scoreboard(m.tcpSent(a)).filter(_.getEventType == GameEvent.SCORE_ENTRY)
    assertEquals(Seq(b.getId, a.getId), entries.map(_.getPlayerId))
  }

  @Test def aDatabaseErrorAtTheEndStillTakesTheMatchDown(): Unit = {
    // As when another connection holds the database's lock: the ratings can't be read. Thrown out of
    // the end of the match, it left the lobby and its match behind for good, its players on a frozen
    // match with no scoreboard, refused "already in a lobby", and a lobby slot gone until a restart.
    val file = java.io.File.createTempFile("gridgame-end", ".db")
    val failing = new AuthDatabase(file.getAbsolutePath) {
      override def getEloByUUID(playerUUID: UUID): Int =
        throw new java.sql.SQLException("[SQLITE_BUSY] The database file is locked (database is locked)")
    }
    val unregistered = scala.collection.mutable.Buffer[Short]()
    val matchEnd = new MatchEnd(server.lobbyManager, failing, server.outbox, server.getConnectedPlayer, id => unregistered += id)
    try {
      val a = m.join(CharacterId.Gladiator, 10, 10)
      val b = m.join(CharacterId.Wizard, 20, 20)
      val lobby = lobbyFor(a, b)
      lobby.isRanked = true
      m.instance.killTracker.recordKill(a.getId, b.getId)
      m.clearSent()
      matchEnd.endGame(lobby.id)
      assertNull("the lobby is gone", server.lobbyManager.getLobby(lobby.id))
      assertEquals("and its match", Seq(lobby.id), unregistered.toSeq)
      for (p <- Seq(a, b)) {
        assertNull("free to make or join another", server.lobbyManager.getPlayerLobby(p.getId))
        assertEquals("on to the scoreboard", GameEvent.SCORE_END, scoreboard(m.tcpSent(p)).last.getEventType)
      }
    } finally { failing.close(); file.delete() }
  }

  /** A match end with a database of the test's own, and two players with accounts in it (a rating
    * is an account's): the first of them has killed the second. */
  private def withAccounts(saveAlso: () => Unit = () => ())(body: (MatchEnd, AuthDatabase, Player, Player, Lobby) => Unit): Unit = {
    val file = java.io.File.createTempFile("gridgame-end", ".db")
    var saves = 0
    val db = new AuthDatabase(file.getAbsolutePath) {
      override def saveMatch(mapIndex: Int, durationMinutes: Int, results: Seq[com.gridgame.server.account.MatchRow], matchType: Byte, playerCount: Int): Long = {
        saves += 1
        if (saves == 1) saveAlso()
        super.saveMatch(mapIndex, durationMinutes, results, matchType, playerCount)
      }
    }
    val matchEnd = new MatchEnd(server.lobbyManager, db, server.outbox, server.getConnectedPlayer, _ => ())
    try {
      assertTrue(db.register("ends_first", "a password"))
      assertTrue(db.register("ends_second", "a password"))
      val a = m.join(CharacterId.Gladiator, 10, 10, id = db.getOrCreateUUID("ends_first"))
      val b = m.join(CharacterId.Wizard, 20, 20, id = db.getOrCreateUUID("ends_second"))
      val lobby = lobbyFor(a, b)
      m.instance.killTracker.recordKill(a.getId, b.getId)
      body(matchEnd, db, a, b, lobby)
    } finally { db.close(); file.delete() }
  }

  @Test def aRankedMatchMovesItsPlayersRatings(): Unit = withAccounts() { (matchEnd, db, a, b, lobby) =>
    assertEquals((1000, 1000), (db.getEloByUUID(a.getId), db.getEloByUUID(b.getId)))
    lobby.isRanked = true
    matchEnd.endGame(lobby.id)
    val (won, lost) = (db.getEloByUUID(a.getId), db.getEloByUUID(b.getId))
    assertTrue(s"the winner's goes up: $won", won > 1000)
    assertTrue(s"the loser's down: $lost", lost < 1000)
    assertEquals("by as much", 2000, won + lost)
  }

  @Test def aCasualMatchMovesNobodysRating(): Unit = withAccounts() { (matchEnd, db, a, b, lobby) =>
    // However many of its players have accounts: a rating is only ever played for in a ranked match
    assertFalse(lobby.isRanked)
    matchEnd.endGame(lobby.id)
    assertEquals("saved, as any match is", 1, db.getMatchHistory(a.getId).size)
    assertEquals((1000, 1000), (db.getEloByUUID(a.getId), db.getEloByUUID(b.getId)))
  }

  @Test def aRankedMatchWithOneHumanInItMovesNoRating(): Unit = {
    // Against bots alone there is nobody to take the points from
    val db = server.authDatabase
    assertTrue(db.register("ends_alone", "a password"))
    val alone = new TestMatch()
    val a = alone.join(CharacterId.Gladiator, 10, 10, id = db.getOrCreateUUID("ends_alone"))
    val bot = alone.join(CharacterId.Barbarian, 30, 30, id = new UUID(0L, 9003L))
    val lobby = server.lobbyManager.createLobby(a.getId, "end", 0, 5, 8)
    lobby.gameInstance = alone.instance
    lobby.status = LobbyStatus.IN_GAME
    lobby.isRanked = true
    alone.instance.killTracker.recordKill(a.getId, bot.getId)
    server.endGame(lobby.id)
    assertEquals("saved", 1, db.getMatchHistory(a.getId).size)
    assertEquals(1000, db.getEloByUUID(a.getId))
  }

  @Test def twoEndsAtOnceScoreAndSaveTheMatchOnce(): Unit = {
    // The match's timer and a practice player leaving can both get to the end with the lobby still
    // there. The second arrives here as the first is saving the match: it must find it ending
    var matchEnd: MatchEnd = null
    var lobbyId: Short = 0
    var again = 0
    withAccounts(saveAlso = () => { again += 1; matchEnd.endGame(lobbyId) }) { (end, db, a, b, lobby) =>
      matchEnd = end
      lobbyId = lobby.id
      lobby.isRanked = true
      m.clearSent()
      end.endGame(lobby.id)
      assertEquals("(the second end did come, mid-save)", 1, again)
      assertEquals("saved once", 1, db.getMatchHistory(a.getId).size)
      assertEquals("everyone told once that it is over", 1, scoreboard(m.tcpSent(a)).count(_.getEventType == GameEvent.GAME_OVER))
      val (won, lost) = (db.getEloByUUID(a.getId), db.getEloByUUID(b.getId))
      assertEquals("and rated once", 2000, won + lost)
      val once = com.gridgame.common.model.MatchResults.eloChanges(Seq(
        (PlayerResult(a.getId, 1, 0, 1), 1000), (PlayerResult(b.getId, 0, 1, 2), 1000)))
      assertEquals(once(a.getId)._2, won)
    }
  }

  @Test def theMinutesItWasPlayedAreSavedWithAMatchRoundedAndOneAtLeast(): Unit = {
    for ((playedMs, minutes) <- Seq(20000L -> 1, 89000L -> 1, 91000L -> 2, 150000L -> 3, 299000L -> 5)) Sim() { clock =>
      val sim = new SimMatch(clock, minutes = 5)
      val a = sim.join(CharacterId.Gladiator, 10, 10)
      val lobby = server.lobbyManager.createLobby(a.getId, "timed", 0, 5, 8)
      lobby.gameInstance = sim.instance
      lobby.status = LobbyStatus.IN_GAME
      clock.advance(playedMs)
      ServerTestKit.quietly(server.endGame(lobby.id))
      assertEquals(s"${playedMs / 1000}s played", minutes, server.authDatabase.getMatchHistory(a.getId).head._3)
    }
  }

  @Test def everyonesCharacterIsSavedBotsAndLeaversIncluded(): Unit = {
    val a = m.join(CharacterId.Gladiator, 10, 10)
    val bot = m.join(CharacterId.Barbarian, 20, 20, id = new UUID(0L, 9001L))
    val leaver = m.join(CharacterId.Wizard, 30, 30)
    val lobby = lobbyFor(a, leaver)
    m.instance.killTracker.recordKill(bot.getId, a.getId)
    m.instance.handler.removePlayer(leaver.getId)
    server.endGame(lobby.id)
    val db = server.authDatabase
    val saved = db.getMatchRows(db.getMatchHistory(a.getId).head._1).map(r => (r.playerId, r.characterId, r.bot))
    assertEquals(Set((a.getId, Some(CharacterId.Gladiator.id), false), (bot.getId, Some(CharacterId.Barbarian.id), true),
      (leaver.getId, Some(CharacterId.Wizard.id), false)), saved.toSet)
  }

  @Test def aPracticeSessionsBotsAreNotSaved(): Unit = {
    // They are passive targets: topping them is no record, and neither are they
    val a = m.join(CharacterId.Gladiator, 10, 10)
    m.join(CharacterId.Barbarian, 20, 20, id = new UUID(0L, 9002L))
    val lobby = lobbyFor(a)
    lobby.matchType = AuthDatabase.PracticeMatchType.toByte
    server.endGame(lobby.id)
    val db = server.authDatabase
    assertEquals(Seq(a.getId), db.getMatchRows(db.getMatchHistory(a.getId).head._1).map(_.playerId))
  }

  /**
   * A match ends when its time is up, not at the next 10-second clock sync to notice. The sync due
   * at the deadline came round a few milliseconds after it, so with the wall clock set back further
   * than that during the match it found a second left, and the match ran on for another ten with
   * every countdown at 0:00.
   */
  @Test def aMatchEndsWhenItsTimeIsUp(): Unit = {
    val lobby = server.lobbyManager.createLobby(UUID.randomUUID(), "timed", 0, 1, 8)
    val instance = new GameInstance(lobby.id, "", 1, server)
    instance.world = WorldData.createEmpty(30, 30)
    instance.durationMs = 400
    lobby.gameInstance = instance
    lobby.status = LobbyStatus.IN_GAME
    val started = System.nanoTime()
    instance.start()
    try {
      // Taking the lobby away is the last thing the end of a match does
      while (server.lobbyManager.getLobby(lobby.id) != null && System.nanoTime() - started < 5000000000L)
        Thread.sleep(2)
      val tookMs = (System.nanoTime() - started) / 1000000L
      assertNull(s"still going after ${tookMs}ms", server.lobbyManager.getLobby(lobby.id))
      assertTrue(s"ended after ${tookMs}ms", tookMs >= 400 && tookMs < 2000)
      assertFalse(instance.isRunning)
    } finally instance.stop()
  }

  @Test def theCountdownReadsZeroOnlyOnceTheTimeIsUp(): Unit = {
    // A second that has started running out still shows: 0:00 means the match is over
    m.instance.durationMs = 10999
    m.instance.begin()
    assertEquals(11, m.instance.getRemainingSeconds)
    assertFalse(m.instance.isTimeUp)
    m.instance.durationMs = 0
    m.instance.begin()
    assertEquals(0, m.instance.getRemainingSeconds)
    assertTrue(m.instance.isTimeUp)
  }
}
