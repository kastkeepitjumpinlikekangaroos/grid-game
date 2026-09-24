package com.gridgame.server

import com.gridgame.common.Constants
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

  // --- How far a phase goes ---

  @Test def aPhaseIsNoLicenceToTeleport(): Unit = {
    // Phased, the speed check was skipped altogether: for as long as the phase lasted (five
    // seconds, for the Wraith) a client could put the player anywhere on the map
    val wraith = m.join(CharacterId.Wraith, 10, 10)
    assertTrue(m.move(wraith, 10, 10, flags = 0x08))
    assertTrue(wraith.isPhased)
    // Ninety cells: more than even a phased walk covers in the second this could take to run
    assertFalse(m.move(wraith, 55, 55, flags = 0x08))
    assertEquals((10, 10), m.at(wraith))
  }

  @Test def aPhasedPlayerWalksAtTwiceTheirPace(): Unit = {
    // Eight cells in 100ms is more than walking allows (six) and within a phase's twice (ten)
    val wraith = m.join(CharacterId.Wraith, 10, 10)
    assertTrue(m.move(wraith, 10, 10, flags = 0x08))
    assertTrue(m.move(wraith, 18, 10, flags = 0x08, gapMs = 100))
    assertEquals((18, 10), m.at(wraith))
  }

  /** A character with a dash, and how far it goes. */
  private val (dasher, dashCells) = CharacterDef.all.iterator.flatMap { d =>
    Seq(d.qAbility, d.eAbility).collectFirst { case a if a.castBehavior.isInstanceOf[DashBuff] =>
      (d.id, a.castBehavior.asInstanceOf[DashBuff].maxDistance)
    }
  }.next()

  @Test def aDashStillCoversItsDistance(): Unit = {
    // A dash is a phase too, and much quicker than a phased walk: it keeps the reach it had
    val p = m.join(dasher, 10, 10)
    assertTrue(m.move(p, 10, 10, flags = 0x08))
    assertTrue(p.isPhased)
    assertTrue(m.move(p, 10 + dashCells, 10, flags = 0x08))
    assertEquals((10 + dashCells, 10), m.at(p))
  }

  @Test def aDashGoesNoFurtherThanItReaches(): Unit = {
    val p = m.join(dasher, 10, 10)
    assertTrue(m.move(p, 10, 10, flags = 0x08))
    // Well past its reach, and more than a phased walk covers in the second this could take to run
    val far = 10 + 35
    assertTrue(Math.hypot(far - 10, far - 10) > dashCells + Constants.TELEPORT_RANGE_TOLERANCE)
    assertFalse(m.move(p, far, far, flags = 0x08))
    assertEquals((10, 10), m.at(p))
  }

  @Test def aPhaseStillGoesThroughWalls(): Unit = {
    val wraith = m.join(CharacterId.Wraith, 10, 10)
    m.world.setTile(11, 10, Tile.Wall)
    m.world.setTile(12, 10, Tile.Wall)
    assertTrue(m.move(wraith, 10, 10, flags = 0x08))
    assertTrue(m.move(wraith, 11, 10, flags = 0x08))
    assertTrue(m.move(wraith, 12, 10, flags = 0x08))
    assertTrue(m.move(wraith, 13, 10, flags = 0x08))
    assertEquals((13, 10), m.at(wraith))
  }

  @Test def aCharacterWithoutAPhaseCannotClaimOne(): Unit = {
    val gladiator = m.join(CharacterId.Gladiator, 10, 10)
    inGame(gladiator)
    phasedStep(gladiator, 10, 10)
    assertFalse(gladiator.isPhased)
  }
}
