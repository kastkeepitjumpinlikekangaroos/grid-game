package com.gridgame.client

import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

import java.util.UUID
import scala.jdk.CollectionConverters._

/** The lobby screens' model: lists, rooms, rosters, and what the client asks of the server. */
class GameClientLobbyTest {
  private val t = new TestClient()
  private val c = t.client

  @Test def creatingOrJoiningALobbyBringsTheSelectedCharacter(): Unit = {
    // Selected in practice, ranked or the last lobby, and shown as picked in the room: only a
    // click there used to reach the server, which then entered us as Spaceman
    c.selectedCharacterId = CharacterId.Wizard.id
    c.createLobby("mine", 0, 5)
    c.joinLobby(12.toShort)
    val actions = t.sentLobbyActions
    assertEquals(Seq(LobbyAction.CREATE, LobbyAction.JOIN), actions.map(_.getAction))
    assertTrue(actions.forall(_.getCharacterId == CharacterId.Wizard.id))
    assertEquals(12.toShort, actions(1).getLobbyId)
  }

  @Test def pickingACharacterTellsTheLobbyOnlyWhenInOne(): Unit = {
    c.selectCharacter(CharacterId.Samurai.id)
    assertTrue(t.sentLobbyActions.isEmpty)
    t.lobby(LobbyAction.JOINED, lobbyId = 7)
    c.selectCharacter(CharacterId.Samurai.id)
    val pick = t.sentLobbyActions.last
    assertEquals((LobbyAction.CHARACTER_SELECT, 7.toShort, CharacterId.Samurai.id),
      (pick.getAction, pick.getLobbyId, pick.getCharacterId))
  }

  @Test def practiceAndRankedBringTheCharacterToo(): Unit = {
    c.selectedCharacterId = CharacterId.Phoenix.id
    c.startPractice()
    c.queueRanked(RankedQueueMode.DUEL)
    assertEquals(CharacterId.Phoenix.id, t.sentLobbyActions.last.getCharacterId)
    val queue = t.sent.collect { case q: RankedQueuePacket => q }.last
    assertEquals((CharacterId.Phoenix.id, RankedQueueMode.DUEL), (queue.getCharacterId, queue.getMode))
  }

  @Test def theRosterIsTheServersOrder(): Unit = {
    val host = UUID.randomUUID()
    val other = UUID.randomUUID()
    t.lobby(LobbyAction.JOINED, lobbyId = 7, name = "The Room", players = 3)
    // The roster a joiner is sent: everyone, ourselves included, in the order teams are dealt
    t.lobby(LobbyAction.MEMBER, who = host, name = "host")
    t.lobby(LobbyAction.MEMBER, who = t.id, name = t.name)
    t.lobby(LobbyAction.MEMBER, who = other, name = "other")
    assertEquals(Seq("host", t.name, "other"), c.lobbyMembers.asScala.map(_.name))
    val late = UUID.randomUUID()
    t.lobby(LobbyAction.PLAYER_JOINED, who = late, name = "late", players = 4)
    t.lobby(LobbyAction.PLAYER_LEFT, who = other, name = "other", players = 3)
    assertEquals(Seq("host", t.name, "late"), c.lobbyMembers.asScala.map(_.name))
    assertEquals(3, c.currentLobbyPlayerCount)
    val notes = c.chatMessages.asScala.map(_(2).asInstanceOf[String])
    assertTrue(notes.contains("late joined the lobby"))
    assertTrue(notes.contains("other left the lobby"))
  }

  @Test def theTeamPreviewDealsHumansThenBots(): Unit = {
    val host = UUID.randomUUID()
    val bot1 = new UUID(0, 901)
    val bot2 = new UUID(0, 900)
    t.lobby(LobbyAction.JOINED, lobbyId = 7, gameMode = 1)
    t.lobby(LobbyAction.MEMBER, who = host, name = "host")
    t.lobby(LobbyAction.MEMBER, who = bot1, name = "Bot 901")
    t.lobby(LobbyAction.MEMBER, who = t.id, name = t.name)
    t.lobby(LobbyAction.MEMBER, who = bot2, name = "Bot 900")
    val preview = c.previewTeams.map { case (m, team) => m.id -> team }.toMap
    assertEquals(TeamAssignment.assign(Seq(host, t.id), Seq(bot2, bot1)).toMap, preview)
  }

  @Test def theLobbyListChangesOnlyWhenTheWholeListHasCome(): Unit = {
    // A rate-limited refresh gets no reply: the last full list must stay up
    t.lobby(LobbyAction.LIST_ENTRY, lobbyId = 1, name = "one")
    t.lobby(LobbyAction.LIST_END)
    t.lobby(LobbyAction.LIST_ENTRY, lobbyId = 2, name = "two")
    assertEquals(Seq("one"), c.lobbyList.map(_.name))
    t.lobby(LobbyAction.LIST_ENTRY, lobbyId = 3, name = "three")
    t.lobby(LobbyAction.LIST_END)
    assertEquals(Seq("two", "three"), c.lobbyList.map(_.name))
  }

  @Test def aSettingsChangeUpdatesTheRoom(): Unit = {
    t.lobby(LobbyAction.JOINED, lobbyId = 7)
    t.lobby(LobbyAction.CONFIG_UPDATE, gameMode = 1, teamSize = 3, players = 4, maxPlayers = 6)
    assertEquals((1, 3, 4, 6), (c.currentLobbyGameMode.toInt, c.currentLobbyTeamSize, c.currentLobbyPlayerCount,
      c.currentLobbyMaxPlayers))
  }

  @Test def aClosedLobbySendsUsBackToTheBrowser(): Unit = {
    var closed = false
    c.lobbyClosedListener = () => closed = true
    t.lobby(LobbyAction.JOINED, lobbyId = 7)
    t.lobby(LobbyAction.LOBBY_CLOSED)
    assertTrue(closed)
    assertEquals(ClientState.LOBBY_BROWSER, c.clientState)
    assertEquals(0, c.currentLobbyId)
  }

  @Test def aRefusedRequestIsReportedWithItsReason(): Unit = {
    var reason: Byte = -1
    c.lobbyActionFailedListener = r => reason = r
    t.lobby(LobbyAction.ACTION_FAILED, status = LobbyFailure.LOBBY_FULL)
    assertEquals(LobbyFailure.LOBBY_FULL, reason)
  }

  @Test def aRankedMatchSetsItsMode(): Unit = {
    c.queueRanked(RankedQueueMode.TEAMS)
    assertTrue(c.isInRankedQueue)
    t.receive(new RankedQueuePacket(1, t.id, Packet.getCurrentTimestamp, RankedQueueAction.MATCH_FOUND, 0.toByte,
      6.toByte, 1000.toShort, 0, 44.toShort, 1.toByte, 5.toByte, 6.toByte, 6.toByte, "Ranked Teams", RankedQueueMode.TEAMS))
    assertFalse(c.isInRankedQueue)
    assertEquals((44, 1, 3), (c.currentLobbyId.toInt, c.currentLobbyGameMode.toInt, c.currentLobbyTeamSize))
    // ...and an FFA match after it isn't left in Teams
    t.receive(new RankedQueuePacket(2, t.id, Packet.getCurrentTimestamp, RankedQueueAction.MATCH_FOUND, 0.toByte,
      8.toByte, 1000.toShort, 0, 45.toShort, 1.toByte, 5.toByte, 8.toByte, 8.toByte, "Ranked Match", RankedQueueMode.FFA))
    assertEquals(0, c.currentLobbyGameMode.toInt)
  }

  @Test def aPostMatchRatingChangeIsKeptForTheScoreboard(): Unit = {
    t.receive(new MatchHistoryPacket(1, t.id, 0, MatchHistoryAction.STATS, elo = 1016.toShort, oldElo = 1000.toShort))
    assertEquals(Some((1000, 1016)), c.pendingEloChange)
    t.gameEvent(GameEvent.GAME_OVER)
    assertEquals(None, c.pendingEloChange)
    t.receive(new MatchHistoryPacket(2, t.id, 0, MatchHistoryAction.STATS, elo = 1016.toShort, oldElo = 1016.toShort))
    assertEquals("a profile query isn't a change", None, c.pendingEloChange)
  }
}
