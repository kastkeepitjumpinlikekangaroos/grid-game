package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.GameClock
import com.gridgame.common.WorldRegistry
import com.gridgame.common.model._
import com.gridgame.common.protocol._
import com.gridgame.server.lobby.{Lobby, LobbyStatus}
import io.netty.buffer.ByteBuf
import io.netty.channel.embedded.EmbeddedChannel
import org.junit.Assert._
import org.junit.{After, Test}

import java.util.UUID
import scala.collection.mutable

/**
 * What the lobby browser and a lobby room take from a player, and what everyone is told of it:
 * the rules LobbyFlowTest's walk from the first packet to the match steps past. What a lobby is
 * made with (its name cleaned, its map, length and seats held to what there is), how soon another
 * may be made, which lobbies can be joined and what a joiner and the room each hear, the list,
 * the host's settings and bots, and a start that fails. On a clock of the test's own (SimKit), for
 * the two waits: three seconds between lobbies, two between lists.
 */
class LobbyRulesTest {
  import ServerTestKit.server

  private var seq = 0
  private def nextSeq(): Int = { seq += 1; seq }
  private val made = mutable.Buffer[Short]()

  private class Client(val name: String) {
    val id: UUID = UUID.randomUUID()
    val channel = new EmbeddedChannel()
    server.sessions.channelToPlayer.put(channel, id) // as a login binds it
    send(new PlayerJoinPacket(nextSeq(), id, new Position(1, 1), 0xFF3366CC, name))
    received()

    def send(p: Packet): Unit = server.handleIncomingPacket(p, channel, null)

    def act(action: Byte, lobbyId: Short = 0, lobbyName: String = "a lobby", map: Int = 0, minutes: Int = 5, seats: Int = 8,
            charId: Byte = 0, gameMode: Byte = 0, teamSize: Int = 2): Unit =
      send(new LobbyActionPacket(nextSeq(), id, Packet.getCurrentTimestamp, action, lobbyId, map.toByte, minutes.toByte,
        0.toByte, seats.toByte, 0.toByte, lobbyName, charId, gameMode, teamSize.toByte))

    def received(): Seq[Packet] =
      Iterator.continually(channel.readOutbound[ByteBuf]()).takeWhile(_ != null).map(ServerTestKit.decode).toSeq

    def heard(): Seq[LobbyActionPacket] = received().collect { case l: LobbyActionPacket => l }

    /** The one thing it was told of the lobbies since the last look, by its action. */
    def heardOne(): LobbyActionPacket = {
      val all = heard()
      assertEquals(s"$name was told: ${all.map(_.getAction).mkString(", ")}", 1, all.size)
      all.head
    }

    def failure(): Seq[Byte] = heard().filter(_.getAction == LobbyAction.ACTION_FAILED).map(_.getLobbyStatus)

    def lobby: Lobby = {
      val l = server.lobbyManager.getPlayerLobby(id)
      if (l != null && !made.contains(l.id)) made += l.id
      l
    }
  }

  @After def closeLobbies(): Unit = made.foreach { id =>
    if (server.gameInstances.containsKey(id)) ServerTestKit.quietly(server.endGame(id))
    if (server.lobbyManager.getLobby(id) != null) ServerTestKit.quietly(server.lobbyManager.removeLobby(id))
  }

  private def quiet[T](body: => T): T = {
    var out: Option[T] = None
    ServerTestKit.captureStderr(ServerTestKit.quietly { out = Some(body) })
    out.get
  }

  // --- Making one ---

  @Test def aLobbyIsMadeAsItsMakerAskedWithinWhatThereIs(): Unit = quiet {
    val maps = WorldRegistry.size
    for (((map, minutes, seats), (asMap, asMinutes, asSeats)) <- Seq(
        (0, 5, 8) -> (0, 5, 8),
        (maps - 1, 1, 2) -> (maps - 1, 1, 2),
        (maps, 30, 32) -> (0, 30, 32),          // a map there isn't: the first
        (250, 31, 33) -> (0, 30, 32),           // no longer than half an hour, no more seats than a lobby has
        (1, 0, 0) -> (1, Constants.DEFAULT_GAME_DURATION_MIN, Constants.MAX_LOBBY_PLAYERS), // left unsaid: the usual
        (1, -5, -5) -> (1, Constants.DEFAULT_GAME_DURATION_MIN, Constants.MAX_LOBBY_PLAYERS),
        (1, 100, 1) -> (1, 30, 2))) {           // nobody plays alone in a lobby
      val c = new Client("maker")
      c.act(LobbyAction.CREATE, map = map, minutes = minutes, seats = seats)
      val joined = c.heardOne()
      val what = s"asked for map $map, $minutes minutes, $seats seats"
      assertEquals(what, LobbyAction.JOINED, joined.getAction)
      assertEquals(what, (asMap, asMinutes, asSeats), (joined.getMapIndex.toInt, joined.getDurationMinutes.toInt, joined.getMaxPlayers.toInt))
      assertEquals(what, (asMap, asMinutes, asSeats), (c.lobby.mapIndex, c.lobby.durationMinutes, c.lobby.maxPlayers))
      assertEquals(what, (c.lobby.id, 1, LobbyStatus.WAITING, "a lobby"),
        (joined.getLobbyId, joined.getPlayerCount.toInt, joined.getLobbyStatus, joined.getLobbyName))
      assertTrue("its maker is its host", c.lobby.isHost(c.id))
    }
  }

  @Test def aLobbysNameIsCleanedAndOneWithNothingLeftIsRefused(): Unit = quiet {
    def named(raw: String): (Client, Seq[LobbyActionPacket]) = {
      val c = new Client("Ada")
      c.act(LobbyAction.CREATE, lobbyName = raw)
      (c, c.heard())
    }
    // What can't be seen, what turns the line round, and what isn't a letter of any plain kind
    val unseen = Seq(0x0007, 0x001B, 0x007F, 0x0085, 0x200B, 0x200C, 0x200D, 0xFEFF, 0x200E,
      0x200F, 0x202A, 0x202B, 0x202C, 0xD83D, 0xDE00, 0xD800, 0xFFFF).map(_.toChar)
    for (ch <- unseen) {
      val (c, told) = named(s"  my${ch}game ")
      assertEquals(f"U+${ch.toInt}%04X is taken out", "mygame", c.lobby.name)
      assertEquals("mygame", told.head.getLobbyName)
    }
    for (ch <- Seq(0x20, 0x7E, 0xE9, 0x4E2D, 0xD7FF).map(_.toChar)) {
      val (c, _) = named(s"my${ch}game")
      assertEquals(f"U+${ch.toInt}%04X stays", s"my${ch}game", c.lobby.name)
    }
    val (unnamed, _) = named("")
    assertEquals("left unnamed, it is its maker's game", "Ada's Game", unnamed.lobby.name)
    val (nothing, told) = named(s" ${0x200B.toChar}${0x07.toChar} ")
    assertEquals("a name of nothing that shows", Seq(LobbyFailure.INVALID_NAME), told.filter(_.getAction == LobbyAction.ACTION_FAILED).map(_.getLobbyStatus))
    assertNull(nothing.lobby)
  }

  @Test def anotherLobbyCanBeMadeThreeSecondsAfterTheLastAndNoSooner(): Unit = quiet(Sim() { clock =>
    val c = new Client("maker")
    c.act(LobbyAction.CREATE)
    assertEquals(LobbyAction.JOINED, c.heardOne().getAction)
    c.lobby
    c.act(LobbyAction.LEAVE)
    c.received()
    clock.advance(2999)
    c.act(LobbyAction.CREATE)
    assertEquals("a millisecond short of three seconds", Seq(LobbyFailure.RATE_LIMITED), c.failure())
    assertNull(c.lobby)
    c.act(LobbyAction.PRACTICE_START)
    assertEquals("nor a practice session, which is a lobby too", Seq(LobbyFailure.RATE_LIMITED), c.failure())
    clock.advance(1)
    c.act(LobbyAction.CREATE)
    assertEquals("three seconds on", LobbyAction.JOINED, c.heardOne().getAction)
    assertNotNull(c.lobby)
    // In one already: refused as that, whatever the wait
    clock.advance(10000)
    c.act(LobbyAction.CREATE)
    assertEquals(Seq(LobbyFailure.ALREADY_IN_LOBBY), c.failure())
    c.act(LobbyAction.PRACTICE_START)
    assertEquals(Seq(LobbyFailure.ALREADY_IN_LOBBY), c.failure())
  })

  @Test def aLobbyThatWasNotMadeCostsNoWait(): Unit = quiet(Sim() { clock =>
    val c = new Client("maker")
    c.act(LobbyAction.CREATE, lobbyName = 0x200B.toChar.toString)
    assertEquals(Seq(LobbyFailure.INVALID_NAME), c.failure())
    c.act(LobbyAction.CREATE)
    assertEquals("at once", LobbyAction.JOINED, c.heardOne().getAction)
    c.lobby
  })

  // --- Joining one ---

  @Test def aJoinerHearsTheLobbyAndEveryoneInItAndTheRoomHearsOfTheJoiner(): Unit = quiet {
    val host = new Client("Hester")
    host.act(LobbyAction.CREATE, lobbyName = "the room", map = 1, minutes = 7, seats = 6)
    val lobby = host.lobby
    host.act(LobbyAction.ADD_BOT)
    val first = new Client("Finn")
    first.act(LobbyAction.JOIN, lobby.id)
    first.received()
    host.received()
    val joiner = new Client("Jo")
    joiner.act(LobbyAction.JOIN, lobby.id, charId = CharacterId.Wizard.id)

    val told = joiner.heard()
    assertEquals("the lobby, then everyone in it", LobbyAction.JOINED +: Seq.fill(4)(LobbyAction.MEMBER), told.map(_.getAction))
    val joined = told.head
    assertEquals((lobby.id, 1, 7, 4, 6, "the room"),
      (joined.getLobbyId, joined.getMapIndex.toInt, joined.getDurationMinutes.toInt, joined.getPlayerCount.toInt, joined.getMaxPlayers.toInt, joined.getLobbyName))
    val bot = lobby.botManager.getBots.head
    assertEquals("in the order teams are dealt: the humans as they came, themselves among them, then the bots",
      Seq((host.id, "Hester"), (first.id, "Finn"), (joiner.id, "Jo"), (bot.id, bot.name)), told.tail.map(m => (m.getPlayerId, m.getLobbyName)))

    for (there <- Seq(host, first)) {
      val news = there.heard()
      assertEquals(s"${there.name} hears of the joiner, and then of the character they came with",
        Seq(LobbyAction.PLAYER_JOINED, LobbyAction.CHARACTER_SELECT), news.map(_.getAction))
      assertEquals((joiner.id, "Jo", 4, lobby.id), (news.head.getPlayerId, news.head.getLobbyName, news.head.getPlayerCount.toInt, news.head.getLobbyId))
      assertEquals((joiner.id, CharacterId.Wizard.id), (news.last.getPlayerId, news.last.getCharacterId))
    }
    assertEquals(CharacterId.Wizard.id, lobby.getCharacter(joiner.id))
  }

  @Test def onlyAnOpenLobbyWithASeatCanBeJoined(): Unit = quiet {
    def refusal(lobbyId: Short): Seq[Byte] = {
      val c = new Client("joiner")
      c.act(LobbyAction.JOIN, lobbyId)
      val why = c.failure()
      assertNull(c.lobby)
      why
    }
    assertEquals("one there isn't", Seq(LobbyFailure.NOT_JOINABLE), refusal(30000.toShort))

    val full = new Client("host")
    full.act(LobbyAction.CREATE, seats = 2)
    val guest = new Client("guest")
    guest.act(LobbyAction.JOIN, full.lobby.id)
    assertEquals("its last seat", full.lobby, guest.lobby)
    assertEquals("a full one", Seq(LobbyFailure.LOBBY_FULL), refusal(full.lobby.id))

    val ranked = new Client("host")
    ranked.act(LobbyAction.CREATE)
    ranked.lobby.isRanked = true
    assertEquals("a ranked match's", Seq(LobbyFailure.NOT_JOINABLE), refusal(ranked.lobby.id))

    val practice = new Client("host")
    practice.act(LobbyAction.CREATE)
    practice.lobby.matchType = 5
    assertEquals("a practice session's", Seq(LobbyFailure.NOT_JOINABLE), refusal(practice.lobby.id))

    val started = new Client("host")
    started.act(LobbyAction.CREATE)
    started.lobby.status = LobbyStatus.IN_GAME
    assertEquals("one whose match has started", Seq(LobbyFailure.NOT_JOINABLE), refusal(started.lobby.id))
    started.lobby.status = LobbyStatus.WAITING

    // And nobody is in two: asked from inside one, it is refused as that
    guest.act(LobbyAction.JOIN, ranked.lobby.id)
    assertEquals(Seq(LobbyFailure.ALREADY_IN_LOBBY), guest.failure())
  }

  // --- The list ---

  @Test def theListIsEveryLobbyButPracticeAndComesNoOftenerThanEveryTwoSeconds(): Unit = quiet(Sim() { clock =>
    val a = new Client("a")
    a.act(LobbyAction.CREATE, lobbyName = "open", map = 1, minutes = 9, seats = 5)
    val hidden = new Client("b")
    hidden.act(LobbyAction.CREATE, lobbyName = "private")
    hidden.lobby.matchType = 5
    val browser = new Client("browser")
    def list(): Seq[LobbyActionPacket] = { browser.act(LobbyAction.LIST_REQUEST); browser.heard() }

    val first = list()
    assertEquals("ends with the end", LobbyAction.LIST_END, first.last.getAction)
    assertTrue(first.init.forall(_.getAction == LobbyAction.LIST_ENTRY))
    val mine = first.init.filter(e => Set(a.lobby.id, hidden.lobby.id).contains(e.getLobbyId))
    assertEquals("the open one, and not the practice session", Seq(a.lobby.id), mine.map(_.getLobbyId))
    assertEquals(("open", 1, 9, 1, 5, LobbyStatus.WAITING),
      (mine.head.getLobbyName, mine.head.getMapIndex.toInt, mine.head.getDurationMinutes.toInt, mine.head.getPlayerCount.toInt,
        mine.head.getMaxPlayers.toInt, mine.head.getLobbyStatus))
    clock.advance(1999)
    assertEquals("asked again a millisecond short of two seconds on", Seq.empty, list())
    clock.advance(1)
    assertEquals("two seconds on", LobbyAction.LIST_END, list().last.getAction)
  })

  // --- The room ---

  @Test def theHostsSettingsAreTakenWithinWhatThereIsAndToldToTheRoom(): Unit = quiet {
    val host = new Client("host")
    host.act(LobbyAction.CREATE, seats = 8)
    val lobby = host.lobby
    val guest = new Client("guest")
    guest.act(LobbyAction.JOIN, lobby.id)
    host.received(); guest.received()

    guest.act(LobbyAction.CONFIG_UPDATE, map = 2, minutes = 20)
    assertEquals("a guest's settings are nobody's", (0, 5), (lobby.mapIndex, lobby.durationMinutes))
    assertTrue(host.heard().isEmpty && guest.heard().isEmpty)

    val maps = WorldRegistry.size
    for (((map, minutes), (asMap, asMinutes)) <- Seq((2, 20) -> (2, 20), (maps - 1, 1) -> (maps - 1, 1), (maps, 31) -> (0, 30),
        (1, 0) -> (1, Constants.DEFAULT_GAME_DURATION_MIN), (200, -1) -> (0, Constants.DEFAULT_GAME_DURATION_MIN))) {
      host.act(LobbyAction.CONFIG_UPDATE, map = map, minutes = minutes)
      assertEquals(s"map $map for $minutes minutes", (asMap, asMinutes), (lobby.mapIndex, lobby.durationMinutes))
      for (there <- Seq(host, guest)) {
        val update = there.heardOne()
        assertEquals(s"${there.name} is told", (LobbyAction.CONFIG_UPDATE, asMap, asMinutes, lobby.id),
          (update.getAction, update.getMapIndex.toInt, update.getDurationMinutes.toInt, update.getLobbyId))
      }
    }
    lobby.status = LobbyStatus.IN_GAME
    host.act(LobbyAction.CONFIG_UPDATE, map = 3, minutes = 12)
    assertEquals("not once the match is on", (0, Constants.DEFAULT_GAME_DURATION_MIN), (lobby.mapIndex, lobby.durationMinutes))
    lobby.status = LobbyStatus.WAITING
  }

  @Test def theHostSeatsAndUnseatsBotsAndTheRoomHearsOfEach(): Unit = quiet {
    val host = new Client("host")
    host.act(LobbyAction.CREATE, seats = 3)
    val lobby = host.lobby
    val guest = new Client("guest")
    guest.act(LobbyAction.JOIN, lobby.id)
    host.received(); guest.received()

    guest.act(LobbyAction.ADD_BOT)
    assertEquals("a guest seats nobody", 0, lobby.botManager.botCount)
    host.act(LobbyAction.ADD_BOT)
    val bot = lobby.botManager.getBots.head
    for (there <- Seq(host, guest)) {
      val news = there.heardOne()
      assertEquals((LobbyAction.PLAYER_JOINED, bot.id, bot.name, 3), (news.getAction, news.getPlayerId, news.getLobbyName, news.getPlayerCount.toInt))
    }
    host.act(LobbyAction.ADD_BOT)
    assertEquals("no seat left: nobody is seated", 1, lobby.botManager.botCount)
    assertTrue("and nothing is said", host.heard().isEmpty && guest.heard().isEmpty)

    guest.act(LobbyAction.REMOVE_BOT)
    assertEquals("a guest unseats nobody", 1, lobby.botManager.botCount)
    host.act(LobbyAction.REMOVE_BOT)
    assertEquals(0, lobby.botManager.botCount)
    for (there <- Seq(host, guest)) {
      val news = there.heardOne()
      assertEquals((LobbyAction.PLAYER_LEFT, bot.id, bot.name, 2), (news.getAction, news.getPlayerId, news.getLobbyName, news.getPlayerCount.toInt))
    }
    host.act(LobbyAction.REMOVE_BOT)
    assertTrue("with none to unseat, nothing is said", host.heard().isEmpty && guest.heard().isEmpty)

    lobby.status = LobbyStatus.IN_GAME
    host.act(LobbyAction.ADD_BOT)
    assertEquals("nor once the match is on", 0, lobby.botManager.botCount)
    lobby.status = LobbyStatus.WAITING
  }

  @Test def aCharacterIsPickedInTheRoomUntilTheMatchStartsAndTheOthersAreTold(): Unit = quiet {
    val host = new Client("host")
    host.act(LobbyAction.CREATE)
    val lobby = host.lobby
    val guest = new Client("guest")
    guest.act(LobbyAction.JOIN, lobby.id)
    host.received(); guest.received()
    guest.act(LobbyAction.CHARACTER_SELECT, charId = CharacterId.Wizard.id)
    assertEquals(CharacterId.Wizard.id, lobby.getCharacter(guest.id))
    val news = host.heardOne()
    assertEquals((LobbyAction.CHARACTER_SELECT, guest.id, CharacterId.Wizard.id), (news.getAction, news.getPlayerId, news.getCharacterId))
    assertTrue("the picker isn't told of their own pick", guest.heard().isEmpty)
    lobby.status = LobbyStatus.IN_GAME
    guest.act(LobbyAction.CHARACTER_SELECT, charId = CharacterId.Soldier.id)
    assertEquals("the match on, a pick is too late", CharacterId.Wizard.id, lobby.getCharacter(guest.id))
    assertTrue(host.heard().isEmpty)
    lobby.status = LobbyStatus.WAITING
  }

  @Test def aCharacterThereIsNotIsNobodysPickAndNothingIsSaidOfIt(): Unit = quiet {
    val host = new Client("host")
    host.act(LobbyAction.CREATE, charId = CharacterId.Wizard.id)
    val lobby = host.lobby
    host.received()
    val guest = new Client("guest")
    guest.act(LobbyAction.JOIN, lobby.id, charId = (-3).toByte)
    assertEquals("the room hears of the joiner, and of no character", Seq(LobbyAction.PLAYER_JOINED), host.heard().map(_.getAction))
    guest.received()
    guest.act(LobbyAction.CHARACTER_SELECT, charId = (-3).toByte)
    host.act(LobbyAction.CHARACTER_SELECT, charId = 120.toByte)
    assertTrue("nor of one picked in the room", host.heard().isEmpty && guest.heard().isEmpty)
    assertEquals("and the host keeps the one they came with", CharacterId.Wizard.id, lobby.getCharacter(host.id))
  }

  @Test def settingTeamsUnseatsTheBotsThatNoLongerFitAndTheRoomHearsOfEach(): Unit = quiet {
    val host = new Client("host")
    host.act(LobbyAction.CREATE)
    val lobby = host.lobby
    val guest = new Client("guest")
    guest.act(LobbyAction.JOIN, lobby.id)
    for (_ <- 1 to 5) host.act(LobbyAction.ADD_BOT)
    val bots = lobby.botManager.getBots
    assertEquals(5, bots.size)
    host.received(); guest.received()
    // Two a side: four seats, two of them the humans', so three of the five bots go, the last seated first
    host.act(LobbyAction.CONFIG_UPDATE, gameMode = 1, teamSize = 2)
    assertEquals((1.toByte, 2, 4, 2), (lobby.gameMode, lobby.teamSize, lobby.maxPlayers, lobby.botManager.botCount))
    for (there <- Seq(host, guest)) {
      val news = there.heard()
      assertEquals(s"${there.name}: each bot that went, then the settings",
        Seq.fill(3)(LobbyAction.PLAYER_LEFT) :+ LobbyAction.CONFIG_UPDATE, news.map(_.getAction))
      assertEquals(bots.reverse.take(3).map(b => (b.id, b.name)), news.take(3).map(n => (n.getPlayerId, n.getLobbyName)))
      assertEquals((1.toByte, 2.toByte, 4.toByte, 4.toByte), (news.last.getGameMode, news.last.getTeamSize, news.last.getMaxPlayers, news.last.getPlayerCount))
    }
  }

  // --- Practice ---

  @Test def practiceIsALobbyOfOnesOwnStartedAtOnceAndLeavingItEndsIt(): Unit = quiet(Sim() { clock =>
    val p = new Client("solo")
    server.rankedQueue.addPlayer(p.id, CharacterId.Wizard.id, 1000)
    p.act(LobbyAction.PRACTICE_START, charId = CharacterId.Phoenix.id)
    val lobby = p.lobby
    assertNotNull(lobby)
    assertFalse("no longer waiting for a ranked match", server.rankedQueue.isInQueue(p.id))
    val told = p.heard()
    val joined = told.find(_.getAction == LobbyAction.JOINED).orNull
    assertNotNull("told of the lobby it is in", joined)
    assertEquals((lobby.id, "Practice", 30, 6, 32), (joined.getLobbyId, joined.getLobbyName, joined.getDurationMinutes.toInt,
      joined.getPlayerCount.toInt, joined.getMaxPlayers.toInt))
    assertTrue("and then that its match is starting",
      told.indexWhere(_.getAction == LobbyAction.GAME_STARTING) > told.indexWhere(_.getAction == LobbyAction.JOINED))
    assertEquals((LobbyStatus.IN_GAME, true), (lobby.status, lobby.isPractice))
    // Leaving it is its end: scored, with a scoreboard to look at, and nothing of it left
    p.received()
    p.act(LobbyAction.LEAVE)
    val after = p.received().collect { case g: GameEventPacket => g.getEventType }
    assertTrue(s"its results: $after", after.contains(GameEvent.GAME_OVER) && after.contains(GameEvent.SCORE_END))
    assertNull(server.lobbyManager.getLobby(lobby.id))
    assertFalse(server.gameInstances.containsKey(lobby.id))
    // And it counts as a lobby made: the next waits its three seconds, practice or not
    clock.advance(2999)
    p.act(LobbyAction.CREATE)
    assertEquals(Seq(LobbyFailure.RATE_LIMITED), p.failure())
    clock.advance(1)
    p.act(LobbyAction.PRACTICE_START)
    assertNotNull("practice again, three seconds on", p.lobby)
    assertTrue(p.lobby.isPractice)
  })

  // --- Starting ---

  @Test def onlyTheHostStartsTheMatchAndItIsTheKindTheRoomWasSetTo(): Unit = quiet {
    for (gameMode <- Seq[Byte](0, 1)) {
      val host = new Client("host")
      host.act(LobbyAction.CREATE, seats = 4)
      val lobby = host.lobby
      val guest = new Client("guest")
      guest.act(LobbyAction.JOIN, lobby.id)
      host.act(LobbyAction.CONFIG_UPDATE, gameMode = gameMode, teamSize = 2)
      guest.act(LobbyAction.START)
      assertEquals("a guest starts nothing", LobbyStatus.WAITING, lobby.status)
      assertNull(lobby.gameInstance)
      host.act(LobbyAction.START)
      assertEquals(LobbyStatus.IN_GAME, lobby.status)
      assertNotNull(lobby.gameInstance)
      assertEquals("a casual free-for-all, or casual Teams", gameMode, lobby.matchType)
      assertEquals(gameMode, lobby.gameInstance.gameMode)
      val first = lobby.gameInstance
      host.act(LobbyAction.START)
      assertSame("asked twice, it is started once", first, lobby.gameInstance)
    }
  }

  @Test def aMatchThatCannotStartClosesItsLobbyForEveryoneInIt(): Unit = {
    val host = new Client("host")
    host.act(LobbyAction.CREATE)
    val lobby = host.lobby
    val guest = new Client("guest")
    guest.act(LobbyAction.JOIN, lobby.id)
    host.received(); guest.received()
    val starting = server.launcher.startMatch
    server.launcher.startMatch = _ => throw new IllegalStateException("no room for it")
    try quiet(host.act(LobbyAction.START)) finally server.launcher.startMatch = starting
    assertNull("the lobby is gone", server.lobbyManager.getLobby(lobby.id))
    assertNull(server.lobbyManager.getPlayerLobby(guest.id))
    assertFalse("and nothing of the match is left", server.gameInstances.containsKey(lobby.id))
    for (there <- Seq(host, guest))
      assertTrue(s"${there.name} is told it closed", there.heard().exists(l => l.getAction == LobbyAction.LOBBY_CLOSED && l.getLobbyId == lobby.id))
    // And both are free to go elsewhere at once
    guest.act(LobbyAction.CREATE)
    assertNotNull(quiet(guest.lobby))
  }

  @Test def aMatchThatFailsOnceItsClocksAreRunningIsStopped(): Unit = {
    // Its ticks had begun when its bots could not: left running, they ran for good
    val host = new Client("host")
    host.act(LobbyAction.CREATE)
    host.act(LobbyAction.ADD_BOT)
    val lobby = host.lobby
    var begun: com.gridgame.server.game.GameInstance = null
    val (starting, startingBots) = (server.launcher.startMatch, server.launcher.startBots)
    server.launcher.startMatch = gi => { begun = gi; starting(gi) }
    server.launcher.startBots = _ => throw new IllegalStateException("no bots today")
    try quiet(host.act(LobbyAction.START)) finally { server.launcher.startMatch = starting; server.launcher.startBots = startingBots }
    assertNotNull("it had started", begun)
    assertFalse("and is stopped", begun.isRunning)
    assertNull(server.lobbyManager.getLobby(lobby.id))
    assertFalse(server.gameInstances.containsKey(lobby.id))
  }
}
