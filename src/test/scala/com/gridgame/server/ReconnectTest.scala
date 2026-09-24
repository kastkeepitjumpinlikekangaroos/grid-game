package com.gridgame.server

import com.gridgame.common.model._
import com.gridgame.common.protocol._
import io.netty.channel.Channel
import io.netty.channel.embedded.EmbeddedChannel
import org.junit.Assert._
import org.junit.Test

import java.util.UUID

/**
 * A client coming back: a join from one the match already has, a login while the server still
 * holds the last connection open, and a connection that drops mid-match. What the client says
 * about itself when it comes back is not evidence of anything — it has just connected and knows
 * nothing of the match — so where the player is and how they are stays the server's.
 */
class ReconnectTest {
  import ServerTestKit._

  // --- A join from a player the match already has ---

  private def rejoinClaiming(m: TestMatch, p: Player, x: Int, y: Int, health: Int = 100,
                             channel: Channel = null): Unit =
    m.instance.handler.processPacket(new PlayerJoinPacket(0, p.getId, Packet.getCurrentTimestamp,
      new Position(x, y), p.getColorRGB, p.getName, health, p.getCharacterId, p.getTeamId),
      if (channel != null) channel else p.getTcpChannel.asInstanceOf[Channel], null)

  @Test def aRejoinDoesNotPutThePlayerWhereItSays(): Unit = {
    // It used to take any open cell it was sent: a join in the middle of a match was a teleport
    val m = new TestMatch()
    val p = m.join(CharacterId.Gladiator, 10, 10)
    rejoinClaiming(m, p, 50, 50)
    assertEquals((10, 10), m.at(p))
  }

  @Test def aRejoinDoesNotHeal(): Unit = {
    // ...and a full heal, as often as the client cared to send one
    val m = new TestMatch()
    val p = m.join(CharacterId.Gladiator, 10, 10)
    p.setHealth(20)
    rejoinClaiming(m, p, 10, 10, health = 999)
    assertEquals(20, p.getHealth)
  }

  @Test def theDeadStayDeadUntilTheirRespawn(): Unit = {
    val m = new TestMatch()
    val p = m.join(CharacterId.Gladiator, 10, 10)
    p.damage(p.getHealth)
    rejoinClaiming(m, p, 20, 20)
    assertTrue(p.isDead)
    assertEquals((10, 10), m.at(p))
  }

  @Test def aRejoiningClientIsToldWhereTheServerHasIt(): Unit = {
    val m = new TestMatch()
    val p = m.join(CharacterId.Gladiator, 10, 10)
    m.clearSent()
    rejoinClaiming(m, p, 50, 50)
    val told = m.updatesAbout(m.tcpSent(p), p)
    assertTrue(told.nonEmpty)
    assertEquals(new Position(10, 10), told.last.getPosition)
  }

  @Test def aRejoiningClientIsToldEveryonesTeam(): Unit = {
    // Sent without them, every other player came back as team 0: allies drawn as enemies and
    // their traps faint, until each of them next moved
    val m = new TestMatch(gameMode = 1)
    val me = m.join(CharacterId.Gladiator, 10, 10, team = 1)
    val ally = m.join(CharacterId.Gladiator, 12, 10, team = 1)
    val enemy = m.join(CharacterId.Gladiator, 50, 10, team = 2)
    m.clearSent()
    m.rejoin(me)
    val joins = m.tcpSent(me).collect { case j: PlayerJoinPacket => j.getPlayerId -> j.getTeamId }.toMap
    assertEquals(1.toByte, joins(ally.getId))
    assertEquals(2.toByte, joins(enemy.getId))
  }

  @Test def aRejoinOnANewConnectionIsTakenOutWithIt(): Unit = {
    // The match keeps the player on the channel they came back on: a disconnect of that one is
    // theirs, and takes them out of the match
    val m = new TestMatch()
    val p = m.join(CharacterId.Gladiator, 10, 10)
    val fresh = new EmbeddedChannel()
    rejoinClaiming(m, p, 10, 10, channel = fresh)
    assertEquals(p, m.instance.registry.getByChannel(fresh))
    m.instance.handler.handleDisconnect(fresh)
    assertNull(m.instance.registry.get(p.getId))
  }

  // --- A login while the last connection is still open ---

  private def leftTheMatch(packets: Seq[Packet], who: UUID): Boolean =
    packets.exists { case l: PlayerLeavePacket => l.getPlayerId == who; case _ => false }

  @Test def loggingInAgainTakesThePlayerOutOfTheMatchItWasIn(): Unit = {
    // The first connection dropped without the server noticing, and the player logged straight
    // back in. The login closed the old channel, but the player stayed in its match — a target
    // standing still for the rest of it — and in its lobby, so every lobby they tried to make or
    // join afterwards was refused as "already in a lobby" until that match ended.
    val first = new TestSession()
    first.join()
    val other = new TestSession()
    other.join()
    val m = new TestMatch()
    m.seat(Seq(first, other))
    other.received()

    val again = new TestSession(first.playerId)
    assertFalse("the old connection is closed", first.channel.isOpen)
    assertNull("out of the match", m.instance.registry.get(first.playerId))
    assertNull("and its lobby", first.lobby)
    assertTrue("the others are told it left", leftTheMatch(other.received(), first.playerId))

    again.join()
    again.createLobby()
    assertNotNull("free to make a lobby of its own", again.lobby)
    server.lobbyManager.removeLobby(again.lobby.id)
    server.lobbyManager.removeLobby(other.lobby.id)
  }

  @Test def loggingInAgainLeavesTheLobbyItWasWaitingIn(): Unit = {
    val host = new TestSession()
    host.join()
    host.createLobby()
    val lobby = host.lobby
    val guest = new TestSession()
    guest.join()
    guest.lobbyAction(LobbyAction.JOIN, lobby.id)
    assertEquals(lobby, guest.lobby)
    host.received()

    new TestSession(guest.playerId)
    assertNull(guest.lobby)
    assertFalse("its seat is free", lobby.players.contains(guest.playerId))
    val left = host.received().collect { case l: LobbyActionPacket if l.getAction == LobbyAction.PLAYER_LEFT => l.getPlayerId }
    assertEquals(Seq(guest.playerId), left)
    server.lobbyManager.removeLobby(lobby.id)
  }

  @Test def loggingInAgainLeavesTheRankedQueue(): Unit = {
    val s = new TestSession()
    s.join()
    s.queueRanked(RankedQueueMode.DUEL)
    assertTrue(server.rankedQueue.isInQueue(s.playerId))
    new TestSession(s.playerId)
    assertFalse(server.rankedQueue.isInQueue(s.playerId))
  }

  @Test def aFirstLoginTakesNobodyAnywhere(): Unit = {
    val host = new TestSession()
    host.join()
    host.createLobby()
    val lobby = host.lobby
    // Somebody else logging in for the first time
    new TestSession().join()
    assertEquals(lobby, host.lobby)
    server.lobbyManager.removeLobby(lobby.id)
  }

  // --- A connection that drops ---

  @Test def aDisconnectMidMatchTakesThePlayerOutAndTellsTheOthers(): Unit = {
    val gone = new TestSession()
    gone.join()
    val stays = new TestSession()
    stays.join()
    val m = new TestMatch()
    val lobby = m.seat(Seq(gone, stays))
    stays.received()
    gone.channel.close()
    assertNull(m.instance.registry.get(gone.playerId))
    assertNull(server.getConnectedPlayer(gone.playerId))
    assertFalse(lobby.players.contains(gone.playerId))
    assertTrue(leftTheMatch(stays.received(), gone.playerId))
    assertTrue("the match goes on for the rest", m.instance.isRunning)
    server.lobbyManager.removeLobby(lobby.id)
  }

  @Test def theLastHumanDisconnectingEndsTheMatch(): Unit = {
    val gone = new TestSession()
    gone.join()
    val m = new TestMatch()
    val lobby = m.seat(Seq(gone))
    gone.channel.close()
    assertFalse(m.instance.isRunning)
    assertNull(server.lobbyManager.getLobby(lobby.id))
  }

  @Test def aHostDisconnectingFromAWaitingLobbyClosesIt(): Unit = {
    val host = new TestSession()
    host.join()
    host.createLobby()
    val lobby = host.lobby
    val guest = new TestSession()
    guest.join()
    guest.lobbyAction(LobbyAction.JOIN, lobby.id)
    guest.received()
    host.channel.close()
    assertNull(server.lobbyManager.getLobby(lobby.id))
    assertNull(guest.lobby)
    assertTrue(guest.received().exists { case l: LobbyActionPacket => l.getAction == LobbyAction.LOBBY_CLOSED; case _ => false })
  }

  @Test def aDisconnectLeavesTheRankedQueue(): Unit = {
    val s = new TestSession()
    s.join()
    s.queueRanked(RankedQueueMode.FFA)
    assertTrue(server.rankedQueue.isInQueue(s.playerId))
    s.channel.close()
    assertFalse(server.rankedQueue.isInQueue(s.playerId))
  }

  @Test def aJoinFromAPlayerWhoLeftTheMatchDoesNotPutThemBackInIt(): Unit = {
    // A client that sends PLAYER_LEAVE and then PLAYER_JOIN, staying in the lobby: a fresh player,
    // back at full health wherever it said, as whichever character it named
    val s = new TestSession()
    s.join()
    val other = new TestSession()
    other.join()
    val m = new TestMatch()
    val lobby = m.seat(Seq(s, other))
    s.send(n => new PlayerLeavePacket(n, s.playerId))
    assertNull(m.instance.registry.get(s.playerId))
    s.send(n => new PlayerJoinPacket(n, s.playerId, Packet.getCurrentTimestamp, new Position(30, 30), 0,
      "back", 999, CharacterId.Wizard.id, 0.toByte))
    assertNull(m.instance.registry.get(s.playerId))
    server.lobbyManager.removeLobby(lobby.id)
  }
}
