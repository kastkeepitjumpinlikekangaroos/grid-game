package com.gridgame.client

import com.gridgame.client.render.projectiles.GLProjectileRenderers
import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

import java.util.UUID

/** A projectile the server tells us of knows whose it is, so it is drawn as its thrower's: the
  * Musician's sonic wave is not the Bard's. */
class ProjectileThrowerTest {
  private val t = new TestClient()
  private val c = t.client

  @Test def aProjectileIsTheCharacterWhoThrewIts(): Unit = {
    t.startMatch(spawn = (5, 5))
    val musician = UUID.randomUUID(); val bard = UUID.randomUUID()
    t.join(musician, 20, 20, charId = CharacterId.Musician.id)
    t.join(bard, 30, 30, charId = CharacterId.Bard.id)
    t.projectile(ProjectileAction.SPAWN, 70, musician, pType = ProjectileType.SONIC_WAVE)
    t.projectile(ProjectileAction.SPAWN, 71, bard, pType = ProjectileType.SONIC_WAVE)
    val m = c.getProjectiles.get(70); val b = c.getProjectiles.get(71)
    assertEquals(CharacterId.Musician.id, m.ownerCharacterId)
    assertEquals(CharacterId.Bard.id, b.ownerCharacterId)
    assertNotSame(GLProjectileRenderers.getRenderer(m.ownerCharacterId, m.projectileType),
      GLProjectileRenderers.getRenderer(b.ownerCharacterId, b.projectileType))
  }

  @Test def oneFirstHeardOfInFlightKnowsWhoseItIsToo(): Unit = {
    // A MOVE that overtook the SPAWN, or came instead of a lost one
    t.startMatch(spawn = (5, 5))
    val hawk = UUID.randomUUID()
    t.join(hawk, 20, 20, charId = CharacterId.Hawk.id)
    t.projectile(ProjectileAction.MOVE, 72, hawk, x = 22f, pType = ProjectileType.GUST)
    assertEquals(CharacterId.Hawk.id, c.getProjectiles.get(72).ownerCharacterId)
  }

  @Test def oursIsTheCharacterWePlay(): Unit = {
    c.selectedCharacterId = CharacterId.Sphinx.id
    t.startMatch(spawn = (5, 5))
    t.projectile(ProjectileAction.SPAWN, 73, t.id, pType = ProjectileType.SONIC_WAVE)
    assertEquals(CharacterId.Sphinx.id, c.getProjectiles.get(73).ownerCharacterId)
  }

  @Test def oneFromSomebodyWeHaveNotHeardOfIsTheTypesOwn(): Unit = {
    t.startMatch(spawn = (5, 5))
    t.projectile(ProjectileAction.SPAWN, 74, UUID.randomUUID(), pType = ProjectileType.SONIC_WAVE)
    val p = c.getProjectiles.get(74)
    assertEquals(-1.toByte, p.ownerCharacterId)
    assertSame(GLProjectileRenderers.getRenderer(ProjectileType.SONIC_WAVE),
      GLProjectileRenderers.getRenderer(p.ownerCharacterId, p.projectileType))
  }
}
