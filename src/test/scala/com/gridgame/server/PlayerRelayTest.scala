package com.gridgame.server

import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

/**
 * What everyone else in a match is told about a player, fed through GameServer the way a
 * client's packets arrive. A position update used to be passed on as the client claimed it with
 * four of the eight status flags: other screens showed a rooted player walking away, and a
 * burning, rooted, slowed or sped-up player's effects blinked off on every step they took.
 */
class PlayerRelayTest {
  private val m = new TestMatch()
  private val server = m.server
  private var seq = 0

  /** Put the match's players in a lobby that is in game, as a started match has them. */
  private def inGame(players: Player*): Unit = {
    val lobby = server.lobbyManager.createLobby(players.head.getId, "relay", 0, 5, 8)
    players.tail.foreach { p => lobby.addPlayer(p.getId); server.lobbyManager.setPlayerLobby(p.getId, lobby.id) }
    lobby.gameInstance = m.instance
    lobby.status = LobbyStatus.IN_GAME
  }

  /** A position update arriving from the player's client over UDP. */
  private def fromClient(p: Player, x: Int, y: Int, flags: Int = 0): Unit = {
    seq += 1
    Thread.sleep(2)
    server.handleIncomingPacket(new PlayerUpdatePacket(seq, p.getId, Packet.getCurrentTimestamp, new Position(x, y),
      p.getColorRGB, p.getHealth, 0, flags, p.getCharacterId, p.getTeamId, p.getServerMoves), null, p.getUdpAddress)
  }

  private def lastAbout(watcher: Player, p: Player): PlayerUpdatePacket = {
    val seen = m.updatesAbout(m.udpSent(watcher), p)
    assertTrue(s"${watcher.getName} was told about ${p.getName}", seen.nonEmpty)
    seen.last
  }

  @Test def aStepIsPassedOnWithEveryStatusFlag(): Unit = {
    val mover = m.join(CharacterId.Gladiator, 10, 10)
    val watcher = m.join(CharacterId.Wizard, 30, 30)
    inGame(mover, watcher)
    val later = System.currentTimeMillis() + 60000
    mover.applyBurn(10, 60000, 1000, watcher.getId)
    mover.setSpeedBoostUntil(later)
    mover.trySlow(60000, 0.5f)
    mover.setShieldUntil(later)
    fromClient(mover, 11, 10)
    val flags = lastAbout(watcher, mover).getEffectFlags
    for ((bit, name) <- Seq(0x01 -> "shield", 0x10 -> "burning", 0x20 -> "speed boost", 0x80 -> "slowed"))
      assertTrue(s"$name flag 0x${bit.toHexString} in 0x${flags.toHexString}", (flags & bit) != 0)
  }

  @Test def aRootedPlayersStepIsPassedOnWhereTheServerHoldsThem(): Unit = {
    val rooted = m.join(CharacterId.Gladiator, 10, 10)
    val watcher = m.join(CharacterId.Wizard, 30, 30)
    inGame(rooted, watcher)
    rooted.tryRoot(60000)
    fromClient(rooted, 11, 10)
    val seen = lastAbout(watcher, rooted)
    assertEquals(new Position(10, 10), seen.getPosition)
    assertTrue("rooted flag", (seen.getEffectFlags & 0x40) != 0)
  }

  @Test def theSenderIsNotSentTheirOwnStep(): Unit = {
    val mover = m.join(CharacterId.Gladiator, 10, 10)
    val watcher = m.join(CharacterId.Wizard, 30, 30)
    inGame(mover, watcher)
    m.clearSent()
    fromClient(mover, 11, 10)
    assertTrue(m.updatesAbout(m.sent(mover), mover).isEmpty)
    assertEquals(new Position(11, 10), lastAbout(watcher, mover).getPosition)
  }

  @Test def aStaleStepIsNotPassedOn(): Unit = {
    val mover = m.join(CharacterId.Gladiator, 10, 10)
    val watcher = m.join(CharacterId.Wizard, 30, 30)
    inGame(mover, watcher)
    m.instance.moveByServer(mover, new Position(20, 10))
    m.clearSent()
    seq += 1
    server.handleIncomingPacket(new PlayerUpdatePacket(seq, mover.getId, Packet.getCurrentTimestamp,
      new Position(11, 10), 0, 100, 0, 0, mover.getCharacterId, 0.toByte, 0), null, mover.getUdpAddress)
    assertTrue(m.updatesAbout(m.udpSent(watcher), mover).isEmpty)
  }

  @Test def theServersOwnUpdatesCarryCharacterAndEveryFlag(): Unit = {
    // Regen, burn ticks and hits: built by hand, several left out flags and the character
    val p = m.join(CharacterId.Wizard, 10, 10)
    val watcher = m.join(CharacterId.Gladiator, 30, 30)
    p.setHealth(40)
    p.trySlow(60000, 0.5f)
    m.clearSent()
    for (_ <- 0 until 10) m.instance.tickPlayers()
    val seen = lastAbout(watcher, p)
    assertEquals(CharacterId.Wizard.id, seen.getCharacterId)
    assertTrue((seen.getEffectFlags & 0x80) != 0)
    assertTrue(seen.getHealth > 40)
  }

  @Test def aHeartIsSeenByEveryoneWithTheOtherFlagsIntact(): Unit = {
    val p = m.join(CharacterId.Gladiator, 10, 10)
    val watcher = m.join(CharacterId.Wizard, 30, 30)
    p.setHealth(10)
    p.applyBurn(10, 60000, 1000, watcher.getId)
    m.clearSent()
    m.useItem(p, ItemType.Heart)
    assertEquals(p.getMaxHealth, p.getHealth)
    val seen = lastAbout(watcher, p)
    assertEquals(p.getMaxHealth, seen.getHealth)
    assertTrue("still burning", (seen.getEffectFlags & 0x10) != 0)
  }

  @Test def aShieldIsSeenAsSoonAsItIsRaised(): Unit = {
    val p = m.join(CharacterId.Gladiator, 10, 10)
    val watcher = m.join(CharacterId.Wizard, 30, 30)
    m.clearSent()
    m.useItem(p, ItemType.Shield)
    assertTrue((lastAbout(watcher, p).getEffectFlags & 0x01) != 0)
  }

  @Test def aPlayersChargeIsKeptInTheServersUpdates(): Unit = {
    val p = m.join(CharacterId.Spaceman, 10, 10)
    val watcher = m.join(CharacterId.Wizard, 30, 30)
    inGame(p, watcher)
    seq += 1
    server.handleIncomingPacket(new PlayerUpdatePacket(seq, p.getId, Packet.getCurrentTimestamp, new Position(10, 10),
      p.getColorRGB, 100, 70, 0, p.getCharacterId, 0.toByte, 0), null, p.getUdpAddress)
    p.setHealth(50)
    m.clearSent()
    for (_ <- 0 until 10) m.instance.tickPlayers()
    assertEquals("a regen tick doesn't drop the charge to nothing", 70, lastAbout(watcher, p).getChargeLevel)
  }
}
