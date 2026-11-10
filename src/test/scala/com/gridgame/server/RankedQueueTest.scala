package com.gridgame.server

import com.gridgame.server.game.GameInstance
import com.gridgame.server.bots.BotManager
import com.gridgame.server.lobby.{Lobby, LobbyStatus}
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

  private def status(s: TestSession): Seq[(Byte, Int, Int, Int, Byte)] = s.received().collect {
    case r: RankedQueuePacket if r.getAction == RankedQueueAction.QUEUE_STATUS =>
      (r.getMode, r.getQueueSize.toInt, r.getElo.toInt, r.getWaitTimeSeconds, r.getCharacterId)
  }

  /** Someone waiting for a duel with this rating (a rating is an account's: here it is said). */
  private def duellist(elo: Int, charId: Byte = CharacterId.Soldier.id): TestSession = {
    val s = player()
    queue.addPlayer(s.playerId, charId, elo, RankedQueueMode.DUEL)
    s
  }

  @Test def aDuelIsMadeOfRatingsWithinTwoHundredAtOnceAndOfAnyTwoOnceOneHasWaitedOverHalfAMinute(): Unit = Sim() { clock =>
    val (a, b) = (duellist(1000), duellist(1200))
    quietly(queue.checkQueue())
    assertNotNull("200 apart: at once", matchOf(a))
    assertEquals(a.lobby, b.lobby)
    val (c, d) = (duellist(1000), duellist(1201))
    quietly(queue.checkQueue())
    assertNull("201 apart: not yet", c.lobby)
    clock.advance(30000)
    quietly(queue.checkQueue())
    assertNull("half a minute, to the millisecond: not yet", c.lobby)
    assertTrue(queue.isInQueue(c.playerId) && queue.isInQueue(d.playerId))
    clock.advance(1)
    quietly(queue.checkQueue())
    assertNotNull("a millisecond over: anyone will do", matchOf(c))
    assertEquals(c.lobby, d.lobby)
  }

  @Test def ofThreeWaitingForADuelTheTwoNearestInRatingPlayAndTheThirdWaitsOn(): Unit = Sim() { clock =>
    val (low, mid, high) = (duellist(1000), duellist(1300), duellist(1350))
    val errors = captureStderr(quietly(queue.checkQueue()))
    assertEquals("", errors)
    assertNotNull(matchOf(mid))
    assertEquals("the two fifty apart", mid.lobby, high.lobby)
    assertEquals("and nobody else: no bots in a duel", 2, everyoneIn(mid.lobby.gameInstance).size)
    assertNull(low.lobby)
    assertTrue("the third waits on", queue.isInQueue(low.playerId))
    clock.advance(10 * 60000L)
    assertEquals("alone, however long: a duel has no bots", "", captureStderr(quietly(queue.checkQueue())))
    assertNull(low.lobby)
  }

  @Test def threeTooFarApartAllWaitAndThePassGoesOnToTheOtherQueues(): Unit = Sim() { clock =>
    // With no two of them near enough, the pass looks at every pair and no further: an error in
    // the duels' pass took the Teams pass after it down with it
    val waiting = Seq(duellist(1000), duellist(1300), duellist(1700))
    val Seq(teamsPlayer) = queued(1, RankedQueueMode.TEAMS)
    clock.advance(60001)
    waiting.foreach(s => queue.removePlayer(s.playerId))
    val again = Seq(duellist(1000), duellist(1300), duellist(1700))
    val errors = captureStderr(quietly(queue.checkQueue()))
    assertEquals("nothing went wrong", "", errors)
    assertTrue("none of the three is matched", again.forall(s => s.lobby == null && queue.isInQueue(s.playerId)))
    assertNotNull("and the Teams queue was still looked at: its player, a minute in, has a match", matchOf(teamsPlayer))
  }

  @Test def everyPassTellsWhoeverIsStillWaitingHowLongTheyHaveWaitedAndHowManyAreWaiting(): Unit = Sim() { clock =>
    val a = player()
    queue.addPlayer(a.playerId, CharacterId.Wizard.id, 1234, RankedQueueMode.DUEL)
    assertEquals("as they join", Seq((RankedQueueMode.DUEL, 1, 1234, 0, CharacterId.Wizard.id)), status(a))
    clock.advance(12999)
    val b = player()
    queue.addPlayer(b.playerId, CharacterId.Soldier.id, 2000, RankedQueueMode.DUEL)
    assertEquals(Seq((RankedQueueMode.DUEL, 2, 2000, 0, CharacterId.Soldier.id)), status(b))
    quietly(queue.checkQueue())
    assertEquals("twelve whole seconds in", Seq((RankedQueueMode.DUEL, 2, 1234, 12, CharacterId.Wizard.id)), status(a))
    assertEquals(Seq((RankedQueueMode.DUEL, 2, 2000, 0, CharacterId.Soldier.id)), status(b))
    clock.advance(1)
    quietly(queue.checkQueue())
    assertEquals("and thirteen", Seq(13), status(a).map(_._4))
    assertEquals(Seq(0), status(b).map(_._4))
    queue.removePlayer(b.playerId)
    quietly(queue.checkQueue())
    assertEquals("one waiting now", Seq(1), status(a).map(_._2))
    assertTrue("whoever left hears no more of it", status(b).isEmpty)
  }

  @Test def aMatchFoundIsToldToEachOfItsPlayersWithTheLobbyItIs(): Unit = Sim() { clock =>
    val (a, b) = (duellist(1000, CharacterId.Wizard.id), duellist(1100, CharacterId.Samurai.id))
    a.received(); b.received()
    quietly(queue.checkQueue())
    val lobby = matchOf(a)
    for ((s, elo, charId) <- Seq((a, 1000, CharacterId.Wizard.id), (b, 1100, CharacterId.Samurai.id))) {
      val found = matchFound(s)
      assertEquals(1, found.size)
      val f = found.head
      assertEquals((lobby.id, lobby.mapIndex, Constants.DUEL_GAME_DURATION_MIN, 2, 2, "Ranked Duel", RankedQueueMode.DUEL, elo, charId),
        (f.getLobbyId, f.getMapIndex.toInt, f.getDurationMinutes.toInt, f.getPlayerCount.toInt, f.getMaxPlayers.toInt, f.getLobbyName,
          f.getMode, f.getElo.toInt, f.getCharacterId))
    }
  }

  @Test def aMatchThatCannotStartPutsItsPlayersBackInTheQueueWithTheWaitTheyHad(): Unit = Sim() { clock =>
    val (a, b) = (duellist(1000), duellist(1050))
    clock.advance(7000)
    a.received(); b.received()
    val starting = server.launcher.startMatch
    server.launcher.startMatch = _ => throw new IllegalStateException("no room for it")
    val errors = captureStderr(quietly { try queue.checkQueue() finally server.launcher.startMatch = starting })
    assertTrue(s"said so: $errors", errors.contains("rolling back 2 entries"))
    for (s <- Seq(a, b)) {
      assertTrue("back in the queue", queue.isInQueue(s.playerId))
      assertNull("in no lobby", s.lobby)
      assertEquals("with the seven seconds they had waited", Seq(7), status(s).map(_._4))
    }
    assertTrue("and no lobby of it is left", server.lobbyManager.getActiveLobbies.forall(l => !l.players.contains(a.playerId)))
    quietly(queue.checkQueue())
    assertNotNull("the next pass starts it", matchOf(a))
    assertEquals(a.lobby, b.lobby)
  }

  @Test def someoneWhoWentAwayWhileQueuedHoldsNobodyElseUp(): Unit = Sim() { clock =>
    // Their connection gone, they are still in the queue until its pass finds them out: it has
    // nobody to tell of their wait, and goes on to everyone else's
    val gone = java.util.UUID.randomUUID()
    queue.addPlayer(gone, CharacterId.Soldier.id, 1000, RankedQueueMode.FFA)
    try {
      val (a, b) = (duellist(1000), duellist(1010))
      assertEquals("nothing went wrong", "", captureStderr(quietly(queue.checkQueue())))
      assertNotNull("the duel after it in the pass is still made", matchOf(a))
      assertEquals(a.lobby, b.lobby)
      // And a minute on they are not given a match of their own to be absent from
      clock.advance(60001)
      assertEquals("", captureStderr(quietly(queue.checkQueue())))
      assertNull(server.lobbyManager.getPlayerLobby(gone))
    } finally queue.removePlayer(gone)
  }

  @Test def withNoRoomForAnotherLobbyAMatchFoundWaitsInTheQueueForOne(): Unit = Sim() { clock =>
    val filling = (0 until Constants.MAX_LOBBIES).flatMap(i => Option(quietly(
      server.lobbyManager.createLobby(java.util.UUID.randomUUID(), s"filler $i", 0, 5, 8))))
    try {
      val (a, b) = (duellist(1000), duellist(1010))
      clock.advance(4000)
      val errors = captureStderr(quietly(queue.checkQueue()))
      assertTrue(s"said so: $errors", errors.contains("rolling back 2 entries"))
      for (s <- Seq(a, b)) {
        assertTrue("still queued", queue.isInQueue(s.playerId))
        assertNull(s.lobby)
        assertEquals("with the wait they had", Seq(4), status(s).map(_._4).takeRight(1))
      }
    } finally filling.foreach(l => quietly(server.lobbyManager.removeLobby(l.id)))
  }

  @Test def onceStartedTheMatchmakerLooksItsQueuesOverByItselfAndStoppedItDoesNot(): Unit = {
    // Every five seconds, on a real server: here every 40ms, on a matchmaker of the test's own
    val own = new com.gridgame.server.lobby.RankedQueue(server, server.lobbyManager, server.launcher)
    own.passEveryMs = 40L
    val (a, b) = (player(), player())
    quietly {
      own.addPlayer(a.playerId, CharacterId.Soldier.id, 1000, RankedQueueMode.DUEL)
      own.addPlayer(b.playerId, CharacterId.Soldier.id, 1000, RankedQueueMode.DUEL)
      assertNull("nothing happens before it is started", a.lobby)
      own.start()
    }
    try {
      // (The pass is another thread's: it seats the one, then the other, then starts their match)
      val from = System.nanoTime()
      def started: Boolean = { val l = a.lobby; l != null && l == b.lobby && l.gameInstance != null }
      while (!started && System.nanoTime() - from < 5000000000L) Thread.sleep(5)
      assertNotNull("matched by a pass nobody called", matchOf(a))
      assertEquals(a.lobby, b.lobby)
    } finally quietly(own.stop())
    val (c, d) = (player(), player())
    quietly {
      own.addPlayer(c.playerId, CharacterId.Soldier.id, 1000, RankedQueueMode.DUEL)
      own.addPlayer(d.playerId, CharacterId.Soldier.id, 1000, RankedQueueMode.DUEL)
    }
    Thread.sleep(250)
    assertNull("stopped, it makes no more matches", c.lobby)
    quietly { own.removePlayer(c.playerId); own.removePlayer(d.playerId) }
  }

  // --- Free-for-all ---

  @Test def everyoneWaitingForAFreeForAllIsToldOfTheirWaitToo(): Unit = Sim() { clock =>
    val Seq(a, b) = queued(2, RankedQueueMode.FFA)
    a.received(); b.received()
    clock.advance(5500)
    quietly(queue.checkQueue())
    for (s <- Seq(a, b)) {
      val told = status(s)
      assertEquals(1, told.size)
      assertEquals((RankedQueueMode.FFA, 2, 5), (told.head._1, told.head._2, told.head._4))
    }
  }

  @Test def aFreeForAllWaitsAMinuteForMoreAndIsThenFilledWithBots(): Unit = Sim() { clock =>
    val Seq(a) = queued(1, RankedQueueMode.FFA)
    clock.advance(60000)
    quietly(queue.checkQueue())
    assertNull("a minute, to the millisecond: not yet", a.lobby)
    clock.advance(1)
    quietly(queue.checkQueue())
    val lobby = matchOf(a)
    assertNotNull("a millisecond over", lobby)
    assertEquals("the other seven are bots", Constants.RANKED_FFA_MAX_PLAYERS - 1, bots(lobby.gameInstance))
    assertEquals((2.toByte, true), (lobby.matchType, lobby.isRanked))
  }

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
