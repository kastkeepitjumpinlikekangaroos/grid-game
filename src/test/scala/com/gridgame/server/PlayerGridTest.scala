package com.gridgame.server.game

import com.gridgame.common.model._
import org.junit.Assert._
import org.junit.Test

import java.util.UUID
import scala.collection.mutable

/**
 * The projectile tick finds who a shot or a blast can reach through PlayerGrid, squares of 4 cells,
 * rather than by looking at everyone. A shot looks only in the 3x3 squares round it, so it can
 * reach no further than they do: a type added with a hit radius past them (counted from the centre
 * a wave trails behind it, ProjectileDef.hitCentreBehind) would pass through players it plainly
 * covers, whenever they stood in the next square out. Held against looking at everyone, over the
 * roster's own furthest reach, at every offset within a square, off the map's edge included.
 */
class PlayerGridTest {
  private val reach: Float = CharacterDef.all
    .flatMap(c => Seq(c.primaryProjectileType, c.qAbility.projectileType, c.eAbility.projectileType))
    .filter(_ >= 0).distinct
    .map(t => { val d = ProjectileDef.get(t); d.hitRadius + d.hitCentreBehind })
    .max

  private def crowd(rnd: scala.util.Random, n: Int): Seq[Player] =
    (0 until n).map(_ => new Player(UUID.randomUUID(), "p", new Position(rnd.nextInt(80), rnd.nextInt(80)), 0, 100, 100))

  @Test def aShotIsShownEveryoneItCanReach(): Unit = {
    val rnd = new scala.util.Random(4)
    for (_ <- 0 until 200) {
      val players = crowd(rnd, 60)
      val grid = new PlayerGrid
      grid.rebuild(java.util.Arrays.asList(players: _*))
      for (_ <- 0 until 200) {
        // Anywhere a shot can be: a cell covers half a cell either side of its centre
        val (x, y) = (rnd.nextFloat() * 81 - 0.5f, rnd.nextFloat() * 81 - 0.5f)
        val shown = mutable.HashSet[Player]()
        grid.forEachNearby(x, y)(shown += _)
        for (p <- players if Projectile.distanceToPlayer(x, y, p) <= reach)
          assertTrue(s"a shot at ($x, $y) reaching $reach isn't shown ${p.getPosition}", shown.contains(p))
      }
    }
  }

  @Test def aBlastIsShownEveryoneWithinItsRadius(): Unit = {
    val rnd = new scala.util.Random(5)
    for (_ <- 0 until 200) {
      val players = crowd(rnd, 60)
      val grid = new PlayerGrid
      grid.rebuild(java.util.Arrays.asList(players: _*))
      for (_ <- 0 until 100) {
        val (x, y) = (rnd.nextFloat() * 81 - 0.5f, rnd.nextFloat() * 81 - 0.5f)
        val radius = rnd.nextFloat() * 12
        val shown = mutable.HashSet[Player]()
        grid.forEachNearbyPlayer(x, y, radius)(shown += _)
        for (p <- players if Projectile.distanceToPlayer(x, y, p) <= radius)
          assertTrue(s"a blast of $radius at ($x, $y) isn't shown ${p.getPosition}", shown.contains(p))
      }
    }
  }

  @Test def onlyThoseAShotCanHitAreInIt(): Unit = {
    val rnd = new scala.util.Random(6)
    val players = crowd(rnd, 30)
    players(0).damage(1000)
    players(1).setShieldUntil(System.currentTimeMillis() + 60000)
    players(2).setPhasedUntil(System.currentTimeMillis() + 60000)
    val grid = new PlayerGrid
    grid.rebuild(java.util.Arrays.asList(players: _*))
    val hittable = (0 until grid.hittableSize).map(grid.hittable(_)).toSet
    assertEquals(players.drop(3).toSet, hittable)
    // And rebuilt in place, a tick on, from fewer
    grid.rebuild(java.util.Arrays.asList(players.drop(20): _*))
    assertEquals(players.drop(20).toSet, (0 until grid.hittableSize).map(grid.hittable(_)).toSet)
    val shown = mutable.HashSet[Player]()
    for (p <- players) grid.forEachNearby(p.getPosition.getX.toFloat, p.getPosition.getY.toFloat)(shown += _)
    assertTrue("nobody from the last tick's", shown.subsetOf(players.drop(20).toSet))
  }
}
