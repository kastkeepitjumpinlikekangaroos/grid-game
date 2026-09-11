package com.gridgame.server

import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

/** The end of a match: everyone gets the final scoreboard, once. */
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
}
