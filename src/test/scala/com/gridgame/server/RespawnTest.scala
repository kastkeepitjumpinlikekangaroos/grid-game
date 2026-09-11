package com.gridgame.server

import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

/** A dead player comes back at a spawn point, with nothing carried over from their last life. */
class RespawnTest {
  private val m = new TestMatch()

  private def deadWithEverything(): Player = {
    val p = m.join(CharacterId.Gladiator, 10, 10)
    val later = System.currentTimeMillis() + 60000
    p.setShieldUntil(later)
    p.setGemBoostUntil(later)
    p.setPhasedUntil(later)
    p.setFrozenUntil(later)
    p.setRootedUntil(later)
    p.setSpeedBoostUntil(later)
    p.trySlow(60000, 0.5f)
    p.applyBurn(30, 60000, 1000, java.util.UUID.randomUUID())
    m.instance.itemManager.addToInventory(p.getId, new Item(1, 0, 0, ItemType.Heart))
    p.setHealth(0)
    p
  }

  @Test def aRespawnRestoresHealthAndClearsEveryEffect(): Unit = {
    val p = deadWithEverything()
    m.instance.respawn(p.getId)
    assertEquals(p.getMaxHealth, p.getHealth)
    assertFalse(p.isBurning)
    assertFalse(p.isFrozen)
    assertFalse(p.isRooted)
    assertFalse(p.isSlowed)
    assertFalse(p.hasSpeedBoost)
    // These three used to survive the death: an unhittable, double-speed respawn
    assertFalse("shield", p.hasShield)
    assertFalse("gem boost", p.hasGemBoost)
    assertFalse("phase", p.isPhased)
    assertTrue("inventory emptied", m.instance.itemManager.getInventory(p.getId).isEmpty)
    assertEquals("nothing left to tell anyone", 0, m.instance.playerFlags(p))
  }

  @Test def aRespawnIsAtAFreeSpawnPoint(): Unit = {
    val world = new WorldData("w", 100, 100, Array.fill(100, 100)(Tile.Grass: Tile),
      Seq(new Position(5, 5), new Position(90, 90)))
    val two = new TestMatch(world)
    val alive = two.join(CharacterId.Gladiator, 5, 5)
    val p = two.join(CharacterId.Gladiator, 50, 50)
    p.setHealth(0)
    for (_ <- 0 until 10) {
      two.instance.respawn(p.getId)
      assertEquals("not on the living player's spawn", (90, 90), two.at(p))
      p.setHealth(0)
    }
    assertTrue(alive.getHealth > 0)
  }

  @Test def theRespawnedPlayerIsToldWhereTheyAre(): Unit = {
    val p = deadWithEverything()
    val other = m.join(CharacterId.Gladiator, 30, 30)
    m.clearSent()
    m.instance.respawn(p.getId)
    val toThem = m.tcpSent(p)
    val event = toThem.collect { case e: GameEventPacket if e.getEventType == GameEvent.RESPAWN => e }
    assertEquals(1, event.size)
    assertEquals((p.getPosition.getX, p.getPosition.getY), (event.head.getSpawnX.toInt, event.head.getSpawnY.toInt))
    // A server move, so the steps their client sent from where it died are dropped
    val move = m.updatesAbout(toThem, p)
    assertEquals(1, move.size)
    assertEquals(p.getServerMoves, move.head.getServerMoves)
    assertEquals(p.getPosition, move.head.getPosition)
    // Everyone else sees them back, whole
    val seen = m.updatesAbout(m.udpSent(other), p)
    assertEquals(p.getMaxHealth, seen.last.getHealth)
    assertEquals(p.getPosition, seen.last.getPosition)
  }
}
