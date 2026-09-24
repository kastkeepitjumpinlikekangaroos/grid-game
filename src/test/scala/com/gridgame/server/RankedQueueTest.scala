package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.After
import org.junit.Assert._
import org.junit.Test

import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * Ranked matchmaking: players queue for a duel, a free-for-all or a teams match, and the matchmaker
 * puts them in one — at once when it has enough of them, or after a wait, with bots making up the
 * numbers where the mode has any. Clients queue through GameServer as they do for real; the
 * matchmaker's pass is run by hand, on a clock of the test's own, so a minute's wait takes no time.
 */
class RankedQueueTest {
  import ServerTestKit._

  private val queue = server.rankedQueue
  private val sessions = mutable.Buffer[TestSession]()
  private val started = mutable.Buffer[Short]()

  @After def tidyUp(): Unit = {
    started.distinct.foreach(server.endGame)
    // The queues belong to the server every test in this class shares
    sessions.foreach(s => queue.removePlayer(s.playerId))
    sessions.foreach(s => Option(s.lobby).foreach(l => server.lobbyManager.removeLobby(l.id)))
  }

  private def player(id: java.util.UUID = java.util.UUID.randomUUID()): TestSession = {
    val s = new TestSession(id)
    s.join()
    sessions += s
    s
  }

  private def queued(n: Int, mode: Byte, charId: Byte = CharacterId.Soldier.id): Seq[TestSession] =
    (0 until n).map { _ => val s = player(); s.queueRanked(mode, charId); s }

  /** The match a player was put in (ended after the test), or null. */
  private def matchOf(s: TestSession): Lobby = {
    val l = s.lobby
    if (l != null && l.gameInstance != null) started += l.id
    l
  }

  private def everyoneIn(gi: GameInstance): Seq[Player] = gi.registry.getAll.asScala.toSeq
  private def bots(gi: GameInstance): Int = everyoneIn(gi).count(p => BotManager.isBotUUID(p.getId))

  private def matchFound(s: TestSession): Seq[RankedQueuePacket] =
    s.received().collect { case r: RankedQueuePacket if r.getAction == RankedQueueAction.MATCH_FOUND => r }

  // --- Duels ---

  @Test def twoPlayersQueuedForADuelAreMatchedAtOnce(): Unit = {
    val Seq(a, b) = queued(2, RankedQueueMode.DUEL)
    a.received()
    queue.checkQueue()
    val lobby = matchOf(a)
    assertNotNull("a is in a match", lobby)
    assertEquals("the same one as b", lobby, b.lobby)
    assertTrue(lobby.isRanked)
    assertEquals("a ranked duel", 3.toByte, lobby.matchType)
    assertEquals(LobbyStatus.IN_GAME, lobby.status)
    assertEquals(Constants.DUEL_GAME_DURATION_MIN, lobby.durationMinutes)
    assertEquals("the two of them and no bots", 2, everyoneIn(lobby.gameInstance).size)
    assertFalse(queue.isInQueue(a.playerId))
    assertFalse(queue.isInQueue(b.playerId))
    assertEquals(Seq(RankedQueueMode.DUEL), matchFound(a).map(_.getMode))
  }

  @Test def aDuelIsPlayedAsTheCharactersTheyQueuedAs(): Unit = {
    val Seq(a, b) = queued(2, RankedQueueMode.DUEL, charId = CharacterId.Wizard.id)
    // b changes their mind while waiting
    b.send(n => new RankedQueuePacket(n, b.playerId, RankedQueueAction.CHARACTER_CHANGE, CharacterId.Samurai.id))
    queue.checkQueue()
    val gi = matchOf(a).gameInstance
    assertEquals(CharacterId.Wizard.id, gi.registry.get(a.playerId).getCharacterId)
    assertEquals(CharacterId.Samurai.id, gi.registry.get(b.playerId).getCharacterId)
    assertEquals("with the health that character has", CharacterDef.get(CharacterId.Samurai).maxHealth,
      gi.registry.get(b.playerId).getHealth)
  }

  @Test def aLonePlayerKeepsWaitingForADuel(): Unit = {
    // Duels have no bots to fill in with, however long the wait
    val Seq(a) = queued(1, RankedQueueMode.DUEL)
    queue.checkQueue(System.currentTimeMillis() + 10 * 60000L)
    assertNull(a.lobby)
    assertTrue(queue.isInQueue(a.playerId))
  }

  @Test def ratingsFarApartWaitHalfAMinuteForACloserOpponent(): Unit = {
    val suffix = (System.nanoTime() % 1000000).toString
    val names = Seq(s"duela$suffix", s"duelb$suffix")
    names.foreach(n => assertTrue(server.authDatabase.register(n, "secret1")))
    server.authDatabase.updateElo(names(1), 1500)
    val Seq(a, b) = names.map(n => player(server.authDatabase.getOrCreateUUID(n)))
    a.queueRanked(RankedQueueMode.DUEL)
    b.queueRanked(RankedQueueMode.DUEL)
    val now = System.currentTimeMillis()
    queue.checkQueue(now)
    assertNull("500 apart: not yet", a.lobby)
    queue.checkQueue(now + 31000)
    assertNotNull("after half a minute, anyone will do", matchOf(a))
    assertEquals(a.lobby, b.lobby)
  }

  // --- Free-for-all ---

  @Test def aFullFreeForAllIsMatchedAtOnce(): Unit = {
    val players = queued(Constants.RANKED_FFA_MAX_PLAYERS, RankedQueueMode.FFA)
    queue.checkQueue()
    val lobby = matchOf(players.head)
    assertNotNull(lobby)
    players.foreach(p => assertEquals(lobby, p.lobby))
    assertEquals("a ranked free-for-all", 2.toByte, lobby.matchType)
    assertEquals(0, bots(lobby.gameInstance))
    assertEquals(0.toByte, lobby.gameInstance.gameMode)
  }

  @Test def aLonePlayerIsGivenAFreeForAllOfBotsAfterAMinute(): Unit = {
    val Seq(a) = queued(1, RankedQueueMode.FFA)
    val now = System.currentTimeMillis()
    queue.checkQueue(now)
    assertNull("not yet", a.lobby)
    queue.checkQueue(now + 61000)
    val lobby = matchOf(a)
    assertNotNull(lobby)
    assertEquals(Constants.RANKED_FFA_MAX_PLAYERS - 1, bots(lobby.gameInstance))
  }

  // --- Teams ---

  @Test def sixPlayersQueuedForTeamsPlayThreeAgainstThree(): Unit = {
    val players = queued(Constants.TEAMS_MAX_PLAYERS, RankedQueueMode.TEAMS)
    queue.checkQueue()
    val lobby = matchOf(players.head)
    assertNotNull(lobby)
    assertEquals("ranked teams", 4.toByte, lobby.matchType)
    val gi = lobby.gameInstance
    assertEquals(1.toByte, gi.gameMode)
    assertEquals(0, bots(gi))
    val teams = players.map(p => gi.registry.get(p.playerId).getTeamId)
    assertEquals(Constants.TEAMS_TEAM_SIZE, teams.count(_ == 1))
    assertEquals(Constants.TEAMS_TEAM_SIZE, teams.count(_ == 2))
    players.foreach { s =>
      val p = gi.registry.get(s.playerId)
      assertEquals(s"team ${p.getTeamId} starts in its own half", TeamDivider.sideOfTeam(p.getTeamId),
        TeamDivider.sideOf(gi.world, p.getPosition.getX, p.getPosition.getY))
    }
  }

  @Test def aLoneTeamsPlayerIsGivenBotsForTeammatesAndOpponents(): Unit = {
    val Seq(a) = queued(1, RankedQueueMode.TEAMS)
    queue.checkQueue(System.currentTimeMillis() + 61000)
    val gi = matchOf(a).gameInstance
    assertEquals(Constants.TEAMS_MAX_PLAYERS - 1, bots(gi))
    val teams = everyoneIn(gi).groupBy(_.getTeamId).map { case (t, ps) => t -> ps.size }
    assertEquals(Map(1.toByte -> 3, 2.toByte -> 3), teams)
  }

  // --- Getting in and out of the queue ---

  @Test def aPlayerIsInOneQueueAtATime(): Unit = {
    val Seq(a) = queued(1, RankedQueueMode.DUEL)
    a.queueRanked(RankedQueueMode.FFA) // ignored: already waiting for a duel
    val Seq(b) = queued(1, RankedQueueMode.DUEL)
    queue.checkQueue()
    assertEquals("the duel", 3.toByte, matchOf(a).matchType)
  }

  @Test def leavingTheQueue(): Unit = {
    val Seq(a, b) = queued(2, RankedQueueMode.DUEL)
    a.send(n => new RankedQueuePacket(n, a.playerId, RankedQueueAction.QUEUE_LEAVE))
    assertFalse(queue.isInQueue(a.playerId))
    queue.checkQueue()
    assertNull(a.lobby)
    assertNull("nobody left to play", b.lobby)
  }

  @Test def queueingIsRefusedWhileInALobby(): Unit = {
    val a = player()
    a.createLobby()
    a.queueRanked(RankedQueueMode.DUEL)
    assertFalse(queue.isInQueue(a.playerId))
  }

  @Test def anUnknownCharacterCannotQueue(): Unit = {
    val a = player()
    a.queueRanked(RankedQueueMode.DUEL, charId = 120.toByte)
    assertFalse(queue.isInQueue(a.playerId))
  }

  @Test def makingALobbyTakesAPlayerOutOfTheQueue(): Unit = {
    // Queueing is refused to anyone in a lobby, but a lobby could be made from the queue. The
    // matchmaker then took the player into its match and left them behind in their own lobby, its
    // host, never to come back to it.
    val Seq(a) = queued(1, RankedQueueMode.DUEL)
    a.createLobby()
    val own = a.lobby
    assertNotNull(own)
    assertFalse(queue.isInQueue(a.playerId))
    queued(1, RankedQueueMode.DUEL)
    queue.checkQueue()
    assertEquals("still in the lobby they made", own, a.lobby)
  }

  @Test def joiningALobbyTakesAPlayerOutOfTheQueue(): Unit = {
    val host = player()
    host.createLobby()
    val Seq(guest) = queued(1, RankedQueueMode.FFA)
    guest.lobbyAction(LobbyAction.JOIN, host.lobby.id)
    assertEquals(host.lobby, guest.lobby)
    assertFalse(queue.isInQueue(guest.playerId))
  }

  @Test def aQueuedPlayerFoundInALobbyIsLeftOutOfTheMatch(): Unit = {
    // The matchmaker works from a snapshot of the queue, so a player can be in a lobby by the time
    // the match is made whatever the lobby handler does
    val Seq(a, b) = queued(2, RankedQueueMode.DUEL)
    val own = server.lobbyManager.createLobby(a.playerId, "elsewhere", 0, 5, 8)
    queue.checkQueue()
    assertEquals(own, a.lobby)
    assertNull("no duel without a", b.lobby)
    assertTrue("b waits on for someone else", queue.isInQueue(b.playerId))
  }
}
