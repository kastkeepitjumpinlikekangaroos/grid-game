package com.gridgame.client

import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

import java.util.UUID

/** A barrier as the client raises, turns and drops it, and as it is told about everyone else's. */
class GameClientBarrierTest {
  private val t = new TestClient()
  private val c = t.client
  private val BARRIER = 0x04

  /** A Crusader at (20, 20), aiming at the cell given. */
  private def crusader(aimX: Double = 25, aimY: Double = 20): Unit = {
    c.selectedCharacterId = CharacterId.Crusader.id
    t.startMatch(spawn = (20, 20))
    c.setMouseWorldPosition(aimX, aimY)
    t.clearSent()
  }

  private def up(u: PlayerUpdatePacket): Boolean = (u.getEffectFlags2 & BARRIER) != 0

  // --- Ours ---

  @Test def raisingItTellsTheServerWhichWayItFaces(): Unit = {
    crusader(aimX = 20, aimY = 25) // straight down +y
    c.shootAbility(0)
    assertTrue(c.hasBarrier)
    val u = t.sentUpdates.last
    assertTrue(up(u))
    assertEquals(Math.PI / 2, u.aimAngleRadians, 1e-3)
    assertTrue("and fires nothing", t.sentSpawns.isEmpty)
  }

  @Test def itTurnsWithTheAim(): Unit = {
    crusader()
    c.shootAbility(0)
    t.clearSent()
    c.setMouseWorldPosition(15, 20) // round to face -x
    Thread.sleep(60)
    c.streamBarrier()
    assertEquals(Math.PI, t.sentUpdates.last.aimAngleRadians, 1e-3)
    // Held still, it is still sent, so everyone else keeps it up
    t.clearSent()
    Thread.sleep(110)
    c.streamBarrier()
    assertTrue(up(t.sentUpdates.last))
  }

  @Test def firingDropsItBeforeTheShotGoes(): Unit = {
    crusader()
    c.shootAbility(0)
    t.clearSent()
    c.shootToward(1f, 0f)
    assertFalse(c.hasBarrier)
    val sent = t.sent
    val drop = sent.indexWhere { case u: PlayerUpdatePacket => !up(u); case _ => false }
    val shot = sent.indexWhere { case p: ProjectilePacket => p.getAction == ProjectileAction.SPAWN; case _ => false }
    assertTrue("down, then the shot", drop >= 0 && shot > drop)
  }

  @Test def castingDropsItToo(): Unit = {
    crusader()
    c.shootAbility(0)
    c.shootAbility(1) // Shield Bash
    assertFalse(c.hasBarrier)
    assertEquals(1, t.sentSpawns.size)
  }

  @Test def whenItRunsOutEveryoneIsTold(): Unit = {
    crusader()
    c.shootAbility(0)
    Thread.sleep(3600) // the Bulwark's 3.5s
    assertFalse(c.hasBarrier)
    t.clearSent()
    c.streamBarrier()
    assertTrue("that it is down", t.sentUpdates.nonEmpty && t.sentUpdates.forall(u => !up(u)))
    t.clearSent()
    c.streamBarrier()
    assertTrue("once", t.sentUpdates.isEmpty)
  }

  @Test def theServersViewOfOursDoesNotOverruleIt(): Unit = {
    // A regen tick or a hit sent before the server heard of the raise says it is down: ours is ours
    crusader()
    c.shootAbility(0)
    t.update(t.id, 20, 20, health = 90)
    assertTrue(c.hasBarrier)
  }

  @Test def itComesDownWhenWeDie(): Unit = {
    crusader()
    c.shootAbility(0)
    t.update(t.id, 20, 20, health = 0)
    assertFalse(c.hasBarrier)
  }

  // --- Everyone else's ---

  @Test def anotherPlayersComesAndGoesWithTheirUpdates(): Unit = {
    t.startMatch(spawn = (5, 5))
    val holder = UUID.randomUUID()
    t.join(holder, 30, 30, charId = CharacterId.Crusader.id)
    t.update(holder, 30, 30, flags2 = BARRIER, aimAngle = 1.0, charId = CharacterId.Crusader.id)
    val p = c.getPlayers.get(holder)
    assertTrue(p.hasBarrier)
    assertEquals(1.0, p.getBarrierAngle, 1e-3)
    assertTrue("it fades in from here", p.getBarrierRaisedAt > 0)
    t.update(holder, 30, 30, flags2 = BARRIER, aimAngle = 2.0, charId = CharacterId.Crusader.id)
    assertEquals("turned", 2.0, p.getBarrierAngle, 1e-3)
    t.update(holder, 30, 30, charId = CharacterId.Crusader.id)
    assertFalse(p.hasBarrier)
    assertTrue("and fades out from now", System.currentTimeMillis() - p.getBarrierUntil < 1000)
  }

  @Test def oneWeFirstHearOfUpIsUp(): Unit = {
    t.startMatch(spawn = (5, 5))
    val holder = UUID.randomUUID()
    t.update(holder, 30, 30, flags2 = BARRIER, aimAngle = 0.5)
    assertTrue(c.getPlayers.get(holder).hasBarrier)
  }

  @Test def aShotStoppedOnABarrierFadesWhereItMetIt(): Unit = {
    t.startMatch(spawn = (5, 5))
    val holder = UUID.randomUUID()
    val shooter = UUID.randomUUID()
    t.join(holder, 20, 30)
    t.update(holder, 20, 30, flags2 = BARRIER, aimAngle = 0.0)
    t.projectile(ProjectileAction.SPAWN, 70, shooter, x = 29f, y = 30f, pType = ProjectileType.BULLET)
    t.projectile(ProjectileAction.BLOCKED, 70, shooter, x = 22.05f, y = 30.5f, target = holder, pType = ProjectileType.BULLET)
    assertNull("gone", c.getProjectiles.get(70))
    val fading = c.getFadingProjectiles.get(70)
    assertNotNull("but fading out", fading)
    assertEquals("where it met the barrier", 22.05f, fading.proj.getX, 0f)
    assertFalse("with no terrain to kick up", fading.hitTerrain)
    // And the barrier ripples there: half a cell along it from the middle
    val slot = (0 until c.BARRIER_IMPACT_SLOTS).find(i => c.getBarrierImpactOwner(i) == holder)
    assertTrue(slot.isDefined)
    assertEquals(0.5f, c.getBarrierImpactAcross(slot.get), 1e-3f)
    // A late move can't bring it back
    t.projectile(ProjectileAction.MOVE, 70, shooter, x = 21f, y = 30f, pType = ProjectileType.BULLET)
    assertNull(c.getProjectiles.get(70))
  }
}
