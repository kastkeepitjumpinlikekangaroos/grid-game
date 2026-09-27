package com.gridgame.client.gl

import com.gridgame.client.render.blasts.GLBlastRenderers._
import com.gridgame.common.Constants
import com.gridgame.common.model._
import org.junit.Assert._
import org.junit.Test

/** Every blast is its thrower's own: the table that picks them, held to the roster. */
class BlastStyleTest {
  private def hasBlast(t: Byte): Boolean = {
    if (t >= -4 && t < 0) return false // a buff, a teleport, a barrier, a trap: nothing thrown
    val d = ProjectileDef.get(t)
    d.aoeOnHit.isDefined || d.aoeOnMaxRange.isDefined || d.explosionConfig.isDefined
  }

  /** Every (character, attack) in the roster that sets off a blast, with the blast it gets. */
  private lazy val thrown: Seq[(CharacterDef, Byte, Int)] = CharacterDef.all.flatMap { c =>
    Seq(c.primaryProjectileType, c.qAbility.projectileType, c.eAbility.projectileType).distinct
      .filter(hasBlast).map(t => (c, t, styleOf(c.id.id, t)))
  }

  @Test def everyBlastInTheRosterHasALookOfItsOwn(): Unit = {
    assertTrue("the roster sets off blasts", thrown.size > 40)
    thrown.foreach { case (c, t, st) =>
      assertTrue(s"${c.displayName}'s type $t is drawn as the generic blast", st != S_GENERIC)
    }
  }

  @Test def everyTypeThatBlastsHasALookEvenWithNobodyThrowingIt(): Unit = {
    var n = 0
    var t = 0
    while (t < 256) {
      val b = t.toByte
      if (ProjectileDef.get(b).id == b && hasBlast(b)) {
        assertTrue(s"type $t blasts but has no look", hasStyle(b))
        n += 1
      }
      t += 1
    }
    assertTrue(n >= 50)
  }

  @Test def noTwoCharactersShareABlast(): Unit = {
    // Six characters slam a TREMOR_SLAM and four throw a GRENADE: each is drawn as its own
    val byStyle = thrown.groupBy(_._3)
    byStyle.foreach { case (st, users) =>
      val who = users.map(_._1.displayName).distinct
      assertEquals(s"${name(st)} is set off by ${who.mkString(", ")}", 1, who.size)
    }
  }

  @Test def anUnknownThrowerGetsTheTypesOwn(): Unit = {
    assertEquals(S_FRAG, styleOf(-1, ProjectileType.GRENADE))
    assertEquals(S_BOMB, styleOf(CharacterId.Bombardier.id, ProjectileType.GRENADE))
    assertEquals(S_CANNON, styleOf(CharacterId.Pirate.id, ProjectileType.GRENADE))
    assertEquals(S_WAIL, styleOf(CharacterId.Banshee.id, ProjectileType.TREMOR_SLAM))
    assertEquals(S_MINE, trapStyle(TrapType.MINE))
  }

  @Test def everyStyleHasANameAndALife(): Unit = {
    var st = 0
    while (st < STYLE_COUNT) {
      assertNotNull(s"style $st has no name", name(st))
      assertTrue(s"${name(st)} lasts ${durationMs(st)}ms", durationMs(st) >= 500 && durationMs(st) <= 2000)
      st += 1
    }
  }

  @Test def theFootprintIsTheGroundTheBlastReaches(): Unit = {
    // r cells in every direction in the world: along the screen's horizontal that is the world
    // diagonal, √2 r cells of half-tile across
    val r = 3f
    val w = footprintW(r); val h = footprintH(r)
    // The world point r cells from the middle along (1, -1)/√2, which lies across the screen
    val d = r / Math.sqrt(2).toFloat
    val sx = (d - (-d)) * Constants.ISO_HALF_W
    assertEquals(sx, w, 1e-3f)
    // and along (1, 1)/√2, which lies down it
    val sy = (d + d) * Constants.ISO_HALF_H
    assertEquals(sy, h, 1e-3f)
  }
}
