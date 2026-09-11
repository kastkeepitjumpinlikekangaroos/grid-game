package com.gridgame.common.model

import org.junit.Assert._
import org.junit.Test

/**
 * Where a fan's projectiles point, relative to the aim. Full circles used to be spread edge to edge
 * like a fan, from -180 to +180 degrees: the first and last projectiles both flew straight behind
 * the caster and none flew at the cursor.
 */
class FanProjectileTest {

  private def degrees(fan: FanProjectile): Seq[Long] =
    (0 until fan.count).map { i =>
      val a = fan.angleOf(i)
      // Math.round would quietly turn a NaN heading into 0, straight at the cursor
      assertFalse(s"projectile $i of $fan has no heading", a.isNaN || a.isInfinite)
      Math.floorMod(Math.round(Math.toDegrees(a)), 360L)
    }

  @Test
  def aFullCircleIsSpacedEvenlyFromTheAim(): Unit = {
    assertEquals(Seq(0L, 45L, 90L, 135L, 180L, 225L, 270L, 315L), degrees(FanProjectile(8, 2 * Math.PI)))
  }

  @Test
  def aFanSpansItsAngleEdgeToEdge(): Unit = {
    assertEquals(Seq(330L, 345L, 0L, 15L, 30L), degrees(FanProjectile(5, Math.toRadians(60))))
    assertEquals(Seq(345L, 0L, 15L), degrees(FanProjectile(3, Math.toRadians(30))))
  }

  @Test
  def aFanOfOneFliesAtTheAim(): Unit = {
    assertEquals(Seq(0L), degrees(FanProjectile(1, Math.toRadians(30))))
  }

  @Test
  def everyFanInTheRosterFiresAtTheCursorAndNeverTwiceOneWay(): Unit = {
    val fans = for {
      c <- CharacterDef.all
      a <- Seq(c.qAbility, c.eAbility)
      fan <- a.castBehavior match { case f: FanProjectile => Some(f); case _ => None }
    } yield (s"${c.displayName} ${a.name}", fan)
    assertTrue(fans.nonEmpty)
    for ((name, fan) <- fans) {
      val headings = degrees(fan)
      assertTrue(s"$name has a projectile on the aim", headings.contains(0L))
      assertEquals(s"$name sends two projectiles one way", headings.distinct.size, headings.size)
    }
  }
}
