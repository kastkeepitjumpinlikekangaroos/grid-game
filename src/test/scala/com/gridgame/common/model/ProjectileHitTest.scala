package com.gridgame.common.model

import org.junit.Assert._
import org.junit.Test

import java.util.UUID

/**
 * Where a projectile connects with a player. A player stands at the centre of their cell — where
 * they are drawn, and where their own shots start — so a shot must reach them at the same
 * distance from every side. Hits used to be measured from the cell's far corner (x + 0.5,
 * y + 0.5): a shot from the left connected a cell later than one from the right.
 */
class ProjectileHitTest {

  private val owner = UUID.randomUUID()

  private def player(x: Int, y: Int): Player = new Player(UUID.randomUUID(), "p", new Position(x, y), 0)

  private def shot(x: Float, y: Float, dx: Float, dy: Float, pType: Byte = ProjectileType.BULLET): Projectile =
    new Projectile(1, owner, x, y, dx, dy, 0, 0, pType)

  /** How far from the target's cell a shot travelling (dx, dy) toward it first connects. */
  private def reachFrom(dx: Float, dy: Float, pType: Byte = ProjectileType.BULLET): Float = {
    val target = player(50, 50)
    val p = shot(50 - dx * 10, 50 - dy * 10, dx * 0.01f, dy * 0.01f, pType)
    var n = 0
    while (!p.hitsPlayer(target) && n < 2000) { p.move(); n += 1 }
    assertTrue("reached the target", p.hitsPlayer(target))
    Math.hypot(p.getX - 50, p.getY - 50).toFloat
  }

  @Test def aShotReachesAPlayerAtTheSameDistanceFromEverySide(): Unit = {
    val radius = ProjectileDef.get(ProjectileType.BULLET).hitRadius
    for ((dx, dy) <- Seq((1f, 0f), (-1f, 0f), (0f, 1f), (0f, -1f), (0.7071f, 0.7071f), (-0.7071f, -0.7071f))) {
      assertEquals(s"from ($dx, $dy)", radius, reachFrom(dx, dy), 0.02f)
    }
  }

  @Test def theHitIsMeasuredFromTheCentreOfThePlayersCell(): Unit = {
    val target = player(20, 20)
    val r = ProjectileDef.get(ProjectileType.BULLET).hitRadius
    assertTrue(shot(20f + r - 0.01f, 20f, 1f, 0f).hitsPlayer(target))
    assertFalse(shot(20f + r + 0.01f, 20f, 1f, 0f).hitsPlayer(target))
    assertTrue(shot(20f - r + 0.01f, 20f, 1f, 0f).hitsPlayer(target))
    assertFalse(shot(20f - r - 0.01f, 20f, 1f, 0f).hitsPlayer(target))
  }

  @Test def aShotDoesNotHitItsOwner(): Unit = {
    val me = new Player(owner, "me", new Position(10, 10), 0)
    assertFalse(shot(10f, 10f, 1f, 0f).hitsPlayer(me))
  }

  @Test def aShotDoesNotHitTheDead(): Unit = {
    // Two shots landing in one tick: the first kills, and the body must not stop the second
    val target = player(10, 10)
    target.setHealth(0)
    assertFalse(shot(10f, 10f, 1f, 0f).hitsPlayer(target))
  }

  @Test def aShotDoesNotHitTheSamePlayerTwice(): Unit = {
    val target = player(10, 10)
    val p = shot(10f, 10f, 1f, 0f, ProjectileType.ARROW)
    assertTrue(p.hitsPlayer(target))
    p.hitPlayers += target.getId
    assertFalse(p.hitsPlayer(target))
  }

  @Test def typesThatPassThroughPlayersNeverHitOne(): Unit = {
    val target = player(10, 10)
    assertFalse(shot(10f, 10f, 1f, 0f, ProjectileType.FROST_TRAP).hitsPlayer(target))
  }

  @Test def pierceCountIsHowManyPlayersAShotPassesThrough(): Unit = {
    // Fourteen types have a pierce of 1 (Arrow, Soul Bolt, Boomerang Blade...), and it used to
    // stop them at their first hit all the same
    val arrow = ProjectileDef.get(ProjectileType.ARROW)
    assertEquals(1, arrow.pierceCount)
    assertTrue("passes through the first", arrow.piercesAfter(1))
    assertFalse("stopped by the second", arrow.piercesAfter(2))

    val railgun = ProjectileDef.get(ProjectileType.RAILGUN)
    assertEquals(3, railgun.pierceCount)
    assertTrue(railgun.piercesAfter(3))
    assertFalse(railgun.piercesAfter(4))

    assertFalse("no pierce: stopped by the first", ProjectileDef.get(ProjectileType.BULLET).piercesAfter(1))
  }

  @Test def withinPlayerAndDistanceToPlayerAgree(): Unit = {
    val target = player(30, 40)
    assertEquals(5f, Projectile.distanceToPlayer(33f, 44f, target), 1e-5f)
    assertTrue(Projectile.withinPlayer(33f, 44f, target, 5f))
    assertFalse(Projectile.withinPlayer(33f, 44f, target, 4.99f))
  }
}
