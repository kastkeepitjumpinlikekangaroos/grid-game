package com.gridgame.server

import com.gridgame.common.model._
import com.gridgame.common.protocol._
import io.netty.buffer.ByteBuf
import io.netty.channel.embedded.EmbeddedChannel
import org.junit.After
import org.junit.Assert._
import org.junit.Test

import java.util.UUID
import scala.collection.mutable

/**
 * Lobbies from the first packet to the match, through GameServer as clients drive them: create,
 * join, pick characters, start, leave.
 */
class LobbyFlowTest {
  import ServerTestKit.server

  private var seq = 0
  private val started = mutable.Buffer[Short]()

  private class Client(val name: String) {
    val id: UUID = UUID.randomUUID()
    val channel = new EmbeddedChannel()
    server.channelToPlayer.put(channel, id) // as a login binds it
    send(new PlayerJoinPacket(nextSeq(), id, new Position(1, 1), 0xFF3366CC, name))

    def send(p: Packet): Unit = server.handleIncomingPacket(p, channel, null)

    def lobbyAction(action: Byte, lobbyId: Short = 0, charId: Byte = 0, maxPlayers: Byte = 0,
                    gameMode: Byte = 0, teamSize: Byte = 2): Unit =
      send(new LobbyActionPacket(nextSeq(), id, Packet.getCurrentTimestamp, action, lobbyId, 0.toByte, 5.toByte,
        0.toByte, maxPlayers, 0.toByte, s"$name's lobby", charId, gameMode, teamSize))

    def received(): Seq[Packet] =
      Iterator.continually(channel.readOutbound[ByteBuf]()).takeWhile(_ != null).map(ServerTestKit.decode).toSeq

    def lobbyEvents(): Seq[LobbyActionPacket] = received().collect { case l: LobbyActionPacket => l }

    def lobby: Lobby = server.lobbyManager.getPlayerLobby(id)
  }

  private def nextSeq(): Int = { seq += 1; seq }

  private def start(host: Client): GameInstance = {
    host.lobbyAction(LobbyAction.START)
    val gi = host.lobby.gameInstance
    assertNotNull("the match started", gi)
    started += host.lobby.id
    gi
  }

  @After def endMatches(): Unit = started.foreach(server.endGame)

  // --- Teams ---

  @Test def aTeamsMatchStartsWithEachTeamInItsOwnHalf(): Unit = {
    val host = new Client("host")
    host.lobbyAction(LobbyAction.CREATE)
    val guest = new Client("guest")
    guest.lobbyAction(LobbyAction.JOIN, lobbyId = host.lobby.id)
    host.lobbyAction(LobbyAction.CONFIG_UPDATE, gameMode = 1, teamSize = 2)
    assertEquals("a Teams lobby", 1.toByte, host.lobby.gameMode)
    val gi = start(host)

    assertEquals(1, gi.gameMode)
    assertNotNull("the opening divider stands between them", gi.divider)
    Seq(host, guest).foreach { c =>
      val p = gi.registry.get(c.id)
      assertTrue(s"${c.name} is on a team", p.getTeamId != 0)
      val pos = p.getPosition
      assertEquals(s"${c.name} (team ${p.getTeamId}) started at $pos",
        TeamDivider.sideOfTeam(p.getTeamId), TeamDivider.sideOf(gi.world, pos.getX, pos.getY))
    }
    assertNotEquals("and the two teams are apart",
      gi.registry.get(host.id).getTeamId, gi.registry.get(guest.id).getTeamId)
  }

  @Test def aFreeForAllStartsWithoutOne(): Unit = {
    val host = new Client("host")
    host.lobbyAction(LobbyAction.CREATE)
    val gi = start(host)
    assertNull(gi.divider)
  }

  // --- Characters ---

  @Test def theCharacterPickedBeforeCreatingIsTheOnePlayed(): Unit = {
    // The client shows the character it has selected (in practice, ranked, the last lobby) as
    // picked, and only a click in the room used to reach the server: the player was entered as
    // Spaceman, and every shot they fired was refused as another character's
    val host = new Client("host")
    host.lobbyAction(LobbyAction.CREATE, charId = CharacterId.Wizard.id)
    val gi = start(host)
    val p = gi.registry.get(host.id)
    assertEquals(CharacterId.Wizard.id, p.getCharacterId)
    // ...so the Wizard's shots are the Wizard's, once the opening ceasefire is over (MatchOpening)
    gi.skipOpening()
    val before = gi.projectileManager.size
    gi.handler.processPacket(ProjectilePacket.spawnRequest(nextSeq(), host.id, p.getPosition.getX.toFloat,
      p.getPosition.getY.toFloat, 0, 1f, 0f, 0.toByte, CharacterDef.get(CharacterId.Wizard).primaryProjectileType,
      AttackSlot.PRIMARY), null, null)
    assertEquals(before + 1, gi.projectileManager.size)
  }

  @Test def theCharacterPickedBeforeJoiningIsTheOnePlayedAndShownToTheRoom(): Unit = {
    val host = new Client("host")
    host.lobbyAction(LobbyAction.CREATE, charId = CharacterId.Gladiator.id)
    val joiner = new Client("joiner")
    host.received()
    joiner.lobbyAction(LobbyAction.JOIN, host.lobby.id, charId = CharacterId.Vampire.id)
    val shown = host.lobbyEvents().filter(_.getAction == LobbyAction.CHARACTER_SELECT)
    assertEquals(Seq((joiner.id, CharacterId.Vampire.id)), shown.map(e => (e.getPlayerId, e.getCharacterId)))
    val gi = start(host)
    assertEquals(CharacterId.Vampire.id, gi.registry.get(joiner.id).getCharacterId)
    assertEquals(CharacterId.Gladiator.id, gi.registry.get(host.id).getCharacterId)
  }

  @Test def aPickInTheRoomReplacesTheOneBroughtIn(): Unit = {
    val host = new Client("host")
    host.lobbyAction(LobbyAction.CREATE, charId = CharacterId.Wizard.id)
    host.lobbyAction(LobbyAction.CHARACTER_SELECT, charId = CharacterId.Samurai.id)
    assertEquals(CharacterId.Samurai.id, start(host).registry.get(host.id).getCharacterId)
  }

  @Test def anUnknownCharacterIsIgnored(): Unit = {
    val host = new Client("host")
    host.lobbyAction(LobbyAction.CREATE, charId = 120.toByte)
    assertEquals(CharacterId.DEFAULT.id, host.lobby.getCharacter(host.id))
  }

  // --- Seats ---

  @Test def botsTakeSeats(): Unit = {
    val host = new Client("host")
    host.lobbyAction(LobbyAction.CREATE, maxPlayers = 2)
    host.lobbyAction(LobbyAction.ADD_BOT)
    val late = new Client("late")
    late.lobbyAction(LobbyAction.JOIN, host.lobby.id)
    assertNull(late.lobby)
    val failed = late.lobbyEvents().filter(_.getAction == LobbyAction.ACTION_FAILED)
    assertEquals(Seq(LobbyFailure.LOBBY_FULL), failed.map(_.getLobbyStatus))
  }

  @Test def aPlayerCannotBeInTwoLobbies(): Unit = {
    val a = new Client("a")
    a.lobbyAction(LobbyAction.CREATE)
    val b = new Client("b")
    b.lobbyAction(LobbyAction.CREATE)
    b.received()
    b.lobbyAction(LobbyAction.JOIN, a.lobby.id)
    assertEquals(Seq(LobbyFailure.ALREADY_IN_LOBBY),
      b.lobbyEvents().filter(_.getAction == LobbyAction.ACTION_FAILED).map(_.getLobbyStatus))
  }

  // --- Leaving ---

  @Test def theHostLeavingAWaitingLobbyClosesIt(): Unit = {
    val host = new Client("host")
    host.lobbyAction(LobbyAction.CREATE)
    val lobbyId = host.lobby.id
    val guest = new Client("guest")
    guest.lobbyAction(LobbyAction.JOIN, lobbyId)
    guest.received()
    host.lobbyAction(LobbyAction.LEAVE)
    assertTrue(guest.lobbyEvents().exists(_.getAction == LobbyAction.LOBBY_CLOSED))
    assertNull(server.lobbyManager.getLobby(lobbyId))
    assertNull(guest.lobby)
  }

  @Test def aGuestLeavingIsAnnouncedAndFreesTheSeat(): Unit = {
    val host = new Client("host")
    host.lobbyAction(LobbyAction.CREATE)
    val guest = new Client("guest")
    guest.lobbyAction(LobbyAction.JOIN, host.lobby.id)
    host.received()
    guest.lobbyAction(LobbyAction.LEAVE)
    val left = host.lobbyEvents().filter(_.getAction == LobbyAction.PLAYER_LEFT)
    assertEquals(Seq(guest.id), left.map(_.getPlayerId))
    assertEquals(1, host.lobby.playerCount)
  }

  @Test def aMatchGoesOnWhenAPlayerLeavesAndStopsWhenTheLastHumanDoes(): Unit = {
    val host = new Client("host")
    host.lobbyAction(LobbyAction.CREATE)
    val guest = new Client("guest")
    guest.lobbyAction(LobbyAction.JOIN, host.lobby.id)
    val lobbyId = host.lobby.id
    val gi = start(host)
    host.lobbyAction(LobbyAction.LEAVE)
    assertTrue("the guest plays on", gi.isRunning)
    assertNull(gi.registry.get(host.id))
    guest.lobbyAction(LobbyAction.LEAVE)
    assertFalse(gi.isRunning)
    assertNull(server.lobbyManager.getLobby(lobbyId))
    started -= lobbyId
  }

  // --- Teams and practice ---

  @Test def teamsAreDealtAsTheRoomShowedThem(): Unit = {
    val host = new Client("host")
    host.lobbyAction(LobbyAction.CREATE)
    val guest = new Client("guest")
    guest.lobbyAction(LobbyAction.JOIN, host.lobby.id)
    host.lobbyAction(LobbyAction.CONFIG_UPDATE, gameMode = 1, teamSize = 2)
    host.lobbyAction(LobbyAction.ADD_BOT)
    host.lobbyAction(LobbyAction.ADD_BOT)
    val lobby = host.lobby
    val preview = TeamAssignment.assign(Seq(host.id, guest.id), lobby.botManager.getBots.map(_.id)).toMap
    val gi = start(host)
    preview.foreach { case (id, team) => assertEquals(s"$id", team, gi.teamAssignments.get(id)) }
    assertEquals(Set(1.toByte, 2.toByte), preview.values.toSet)
    assertEquals("the hosts are split", 1.toByte, preview(host.id))
    assertEquals(2.toByte, preview(guest.id))
  }

  @Test def practiceStartsAtOnceWithTheChosenCharacterAndFiveBots(): Unit = {
    val p = new Client("solo")
    p.lobbyAction(LobbyAction.PRACTICE_START, charId = CharacterId.Phoenix.id)
    val lobby = p.lobby
    assertNotNull(lobby)
    assertTrue(lobby.isPractice)
    assertEquals(LobbyStatus.IN_GAME, lobby.status)
    started += lobby.id
    val gi = lobby.gameInstance
    assertEquals(CharacterId.Phoenix.id, gi.registry.get(p.id).getCharacterId)
    assertEquals(5, lobby.botManager.botCount)
    assertEquals(6, gi.registry.size)
    assertTrue(p.received().exists { case l: LobbyActionPacket => l.getAction == LobbyAction.GAME_STARTING; case _ => false })
  }

  @Test def aStartedMatchTellsEveryoneWhereEveryoneIs(): Unit = {
    val host = new Client("host")
    host.lobbyAction(LobbyAction.CREATE)
    val guest = new Client("guest")
    guest.lobbyAction(LobbyAction.JOIN, host.lobby.id)
    guest.received()
    val gi = start(host)
    val got = guest.received()
    val order = got.collect {
      case l: LobbyActionPacket if l.getAction == LobbyAction.GAME_STARTING => "start"
      case _: WorldInfoPacket => "world"
      case j: PlayerJoinPacket => "join"
    }
    assertEquals("the world before the players placed in it", Seq("start", "world", "join", "join"), order)
    val joins = got.collect { case j: PlayerJoinPacket => j }
    joins.foreach { j =>
      assertEquals("each join is where the server put them", gi.registry.get(j.getPlayerId).getPosition, j.getPosition)
      assertTrue(gi.world.isWalkable(j.getPosition))
    }
    assertNotEquals("apart", joins(0).getPosition, joins(1).getPosition)
  }
}
