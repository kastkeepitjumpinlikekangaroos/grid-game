package com.gridgame.server

import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

/**
 * A phase (a phase-shift ability, or a dash) makes the client's player untouchable and lets it
 * walk through walls; the server has to agree, or the player takes hits the client shows passing
 * through them and every step through a wall is refused. The client tells the server by setting
 * the phased flag on its updates, which arrive here through GameServer.
 */
class PhaseShiftTest {
  private val m = new TestMatch()
  private val server = m.server
  private var seq = 0

  private def phaseOf(c: CharacterId): (Int, Int) = {
    val d = CharacterDef.get(c)
    Seq(d.qAbility, d.eAbility).collectFirst {
      case a if a.castBehavior.isInstanceOf[PhaseShiftBuff] => (a.castBehavior.asInstanceOf[PhaseShiftBuff].durationMs, a.cooldownMs)
    }.get
  }

  private def inGame(players: Player*): Unit = {
    val lobby = server.lobbyManager.createLobby(players.head.getId, "phase", 0, 5, 8)
    players.tail.foreach { p => lobby.addPlayer(p.getId); server.lobbyManager.setPlayerLobby(p.getId, lobby.id) }
    lobby.gameInstance = m.instance
    lobby.status = LobbyStatus.IN_GAME
  }

  private def phasedStep(p: Player, x: Int, y: Int): Unit = {
    seq += 1
    Thread.sleep(2)
    server.handleIncomingPacket(new PlayerUpdatePacket(seq, p.getId, Packet.getCurrentTimestamp, new Position(x, y),
      p.getColorRGB, p.getHealth, 0, 0x08, p.getCharacterId, p.getTeamId, p.getServerMoves), null, p.getUdpAddress)
  }

  @Test def theFirstPhaseIsHonoured(): Unit = {
    val wraith = m.join(CharacterId.Wraith, 10, 10)
    inGame(wraith)
    phasedStep(wraith, 10, 10)
    assertTrue(wraith.isPhased)
  }

  @Test def aPhaseRecastAsSoonAsItIsReadyIsHonoured(): Unit = {
    // Counted from the end of the last phase at 90% of the cooldown, a Wraith (5s phase, 12s
    // cooldown) couldn't phase again until 15.8s after the last: every second phase showed on
    // the client and wasn't honoured
    val wraith = m.join(CharacterId.Wraith, 10, 10)
    inGame(wraith)
    val (duration, cooldown) = phaseOf(CharacterId.Wraith)
    // The last phase started one cooldown ago
    wraith.setPhasedUntil(System.currentTimeMillis() - cooldown + duration)
    assertFalse(wraith.isPhased)
    phasedStep(wraith, 10, 10)
    assertTrue(wraith.isPhased)
  }

  @Test def aPhaseRecastTooSoonIsRefused(): Unit = {
    val wraith = m.join(CharacterId.Wraith, 10, 10)
    inGame(wraith)
    val (duration, cooldown) = phaseOf(CharacterId.Wraith)
    // The last phase started half a cooldown ago, and has run out
    wraith.setPhasedUntil(System.currentTimeMillis() - cooldown / 2 + duration)
    assertFalse(wraith.isPhased)
    phasedStep(wraith, 10, 10)
    assertFalse(wraith.isPhased)
  }

  @Test def aPhaseTakesEffectForTheUpdateThatAnnouncesIt(): Unit = {
    // If that update already has the player inside a wall, it must be judged as phased: judged
    // first and phased after, it was refused, and the phase never began
    val wraith = m.join(CharacterId.Wraith, 10, 10)
    val watcher = m.join(CharacterId.Gladiator, 40, 40)
    inGame(wraith, watcher)
    m.world.setTile(11, 10, Tile.Wall)
    phasedStep(wraith, 11, 10)
    assertTrue(wraith.isPhased)
    assertEquals((11, 10), m.at(wraith))
  }

  @Test def aCharacterWithoutAPhaseCannotClaimOne(): Unit = {
    val gladiator = m.join(CharacterId.Gladiator, 10, 10)
    inGame(gladiator)
    phasedStep(gladiator, 10, 10)
    assertFalse(gladiator.isPhased)
  }
}
