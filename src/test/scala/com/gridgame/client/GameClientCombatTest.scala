package com.gridgame.client

import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

import java.util.UUID

/** Attacks as the client sends them, and projectiles as it is told about them. */
class GameClientCombatTest {
  private val t = new TestClient()
  private val c = t.client

  private def play(character: CharacterId): Unit = {
    c.selectedCharacterId = character.id
    t.startMatch(spawn = (20, 20))
    t.clearSent()
  }

  // --- Projectiles we are told about ---

  @Test def aPierceShowsTheHitAndKeepsTheProjectile(): Unit = {
    // The server keeps a piercing projectile flying. Told HIT, the client dropped it, then
    // ignored its moves as those of a projectile it had seen hit.
    t.startMatch(spawn = (5, 5))
    val shooter = UUID.randomUUID()
    val target = UUID.randomUUID()
    t.projectile(ProjectileAction.SPAWN, 50, shooter)
    t.projectile(ProjectileAction.PIERCE, 50, shooter, target = target)
    assertNotNull("still flying", c.getProjectiles.get(50))
    assertTrue("the hit is shown", c.getPlayerHitTime(target) > 0)
    t.projectile(ProjectileAction.MOVE, 50, shooter, x = 14f)
    assertEquals("and flown on from its moves", 14f,
      c.getProjectiles.get(50).asInstanceOf[com.gridgame.client.render.NetProjectile].heardX, 0f)
  }

  @Test def aHitEndsAProjectileAndALateMoveCannotBringItBack(): Unit = {
    t.startMatch(spawn = (5, 5))
    val shooter = UUID.randomUUID()
    t.projectile(ProjectileAction.SPAWN, 51, shooter)
    t.projectile(ProjectileAction.HIT, 51, shooter, target = UUID.randomUUID())
    assertNull(c.getProjectiles.get(51))
    t.projectile(ProjectileAction.MOVE, 51, shooter, x = 15f) // UDP, overtaken by the HIT
    assertNull(c.getProjectiles.get(51))
  }

  @Test def aProjectileWeMissedTheSpawnOfAppearsOnItsFirstMove(): Unit = {
    t.startMatch(spawn = (5, 5))
    t.projectile(ProjectileAction.MOVE, 52, UUID.randomUUID(), x = 12f)
    assertEquals(12f, c.getProjectiles.get(52).getX, 0f)
  }

  @Test def anExplosiveGoesOffWhereItStopped(): Unit = {
    t.startMatch(spawn = (5, 5))
    val shooter = UUID.randomUUID()
    t.projectile(ProjectileAction.SPAWN, 53, shooter, pType = ProjectileType.GRENADE)
    t.projectile(ProjectileAction.DESPAWN, 53, shooter, x = 30f, y = 30f, pType = ProjectileType.GRENADE)
    assertNull(c.getProjectiles.get(53))
    assertNotNull(c.getExplosionAnimations.get(53))
  }

  @Test def aShotStoppedByTerrainFadesWhereItStruck(): Unit = {
    t.startMatch(spawn = (5, 5))
    val shooter = UUID.randomUUID()
    t.projectile(ProjectileAction.SPAWN, 54, shooter, pType = ProjectileType.BULLET)
    t.projectile(ProjectileAction.DESPAWN, 54, shooter, x = 30f, y = 30f, pType = ProjectileType.BULLET)
    assertNull(c.getProjectiles.get(54))
    assertNotNull(c.getFadingProjectiles.get(54))
  }

  // --- Attacks we send ---

  @Test def aPrimaryShotIsOneRequestFromThePrimarySlot(): Unit = {
    play(CharacterId.Soldier)
    c.shootToward(1f, 0f)
    val spawns = t.sentSpawns
    assertEquals(1, spawns.size)
    assertEquals(AttackSlot.PRIMARY, spawns.head.getAttackSlot)
    assertEquals(CharacterDef.get(CharacterId.Soldier).primaryProjectileType, spawns.head.getProjectileType)
    assertEquals((20f, 20f), (spawns.head.getX, spawns.head.getY))
  }

  @Test def aGemBoostedShotIsThreeInANarrowCone(): Unit = {
    play(CharacterId.Soldier)
    t.item(ItemAction.INVENTORY, 3, ItemType.Gem)
    c.useItem(ItemType.Gem.id)
    t.clearSent()
    c.shootToward(1f, 0f)
    val spawns = t.sentSpawns
    assertEquals(3, spawns.size)
    spawns.foreach(s => assertTrue(Math.abs(Math.toDegrees(Math.atan2(s.getDy, s.getDx))) < 10))
  }

  @Test def aBurstShotIsThePrimaryInAllEightDirections(): Unit = {
    // It used to send four of the default bolt, refused by the server from everyone but Spaceman
    play(CharacterId.Soldier)
    c.shootAllDirections()
    val spawns = t.sentSpawns
    assertEquals(8, spawns.size)
    assertTrue(spawns.forall(_.getAttackSlot == AttackSlot.BURST))
    assertTrue(spawns.forall(_.getProjectileType == CharacterDef.get(CharacterId.Soldier).primaryProjectileType))
    val degrees = spawns.map(s => Math.floorMod(Math.round(Math.toDegrees(Math.atan2(s.getDy, s.getDx))), 360L)).sorted
    assertEquals(Seq(0L, 45L, 90L, 135L, 180L, 225L, 270L, 315L), degrees)
    assertTrue("it roots the shooter for a moment", c.isMovementBlocked)
  }

  @Test def noBurstWhileFrozen(): Unit = {
    play(CharacterId.Soldier)
    t.update(t.id, 20, 20, flags = 0x04)
    c.shootAllDirections()
    assertTrue(t.sentSpawns.isEmpty)
    assertFalse(c.isMovementBlocked)
  }

  @Test def aFanAbilitySendsItsWholeFanFromItsSlot(): Unit = {
    play(CharacterId.Ranger)
    val e = CharacterDef.get(CharacterId.Ranger).eAbility // Volley: a fan of 3
    val FanProjectile(count, _) = e.castBehavior
    c.setMouseWorldPosition(30.0, 20.0)
    c.shootAbility(1)
    val spawns = t.sentSpawns
    assertEquals(count, spawns.size)
    assertTrue(spawns.forall(s => s.getAttackSlot == AttackSlot.E && s.getProjectileType == e.projectileType))
  }

  @Test def anAbilityWaitsForItsCooldown(): Unit = {
    play(CharacterId.Soldier)
    c.setMouseWorldPosition(30.0, 20.0)
    c.shootAbility(1)
    val first = t.sentSpawns.size
    c.shootAbility(1)
    assertEquals(first, t.sentSpawns.size)
    assertTrue(c.getECooldownRemaining > 0)
  }

  @Test def aHitWithAnAbilityHalvesWhatIsLeftOfItsCooldown(): Unit = {
    play(CharacterId.Soldier)
    c.setMouseWorldPosition(30.0, 20.0)
    c.shootAbility(1)
    val before = c.getECooldownRemaining
    t.projectile(ProjectileAction.HIT, 60, t.id, target = UUID.randomUUID(),
      pType = CharacterDef.get(CharacterId.Soldier).eAbility.projectileType)
    assertEquals(before / 2, c.getECooldownRemaining, 0.1f)
  }

  @Test def theDeadDoNotShoot(): Unit = {
    play(CharacterId.Soldier)
    t.update(t.id, 20, 20, health = 0)
    c.shootToward(1f, 0f)
    c.shootAbility(0)
    c.shootAllDirections()
    assertTrue(t.sentSpawns.isEmpty)
  }
}
