package com.gridgame.common.model

import org.junit.Assert._
import org.junit.Test

import java.io.File

/**
 * The roster is data, 112 characters of it, and the game trusts it everywhere: the server
 * validates attacks against it, the client fires by it, the lobby picks from it. A slip in it
 * (a duplicate id, an ability pointing at a projectile nobody defined) fails quietly in a match,
 * so it is checked here as a whole.
 */
class CharacterRosterTest {
  private val all = CharacterDef.all

  private def firesProjectiles(a: AbilityDef): Boolean = a.castBehavior match {
    case StandardProjectile | FanProjectile(_, _) | GroundSlam(_) => true
    case _ => false
  }

  /** Is `t` a type with a definition of its own (rather than falling back to the default)? */
  private def defined(t: Byte): Boolean = t == ProjectileType.NORMAL || ProjectileDef.get(t).id == t

  @Test def idsAreUniqueAndRunFromZero(): Unit = {
    assertEquals(112, all.size)
    assertEquals((0 until 112).toList, all.map(_.id.id.toInt).sorted.toList)
    all.foreach(c => assertEquals(c.id, CharacterId.fromId(c.id.id)))
  }

  @Test def isValidMatchesTheRoster(): Unit = {
    all.foreach(c => assertTrue(CharacterDef.isValid(c.id.id)))
    assertFalse(CharacterDef.isValid(112.toByte))
    assertFalse(CharacterDef.isValid(-1.toByte))
  }

  @Test def everyAttackThatFiresHasADefinedProjectile(): Unit = {
    for (c <- all) {
      assertTrue(s"${c.displayName} primary", defined(c.primaryProjectileType))
      for (a <- Seq(c.qAbility, c.eAbility) if firesProjectiles(a)) {
        assertTrue(s"${c.displayName} ${a.name} fires type ${a.projectileType}", defined(a.projectileType))
      }
    }
  }

  @Test def abilitiesHaveSensibleNumbers(): Unit = {
    for (c <- all; a <- Seq(c.qAbility, c.eAbility)) {
      val name = s"${c.displayName} ${a.name}"
      assertTrue(s"$name cooldown", a.cooldownMs > 0)
      a.castBehavior match {
        case FanProjectile(count, angle) =>
          assertTrue(s"$name count", count >= 2)
          assertTrue(s"$name angle", angle > 0)
        case DashBuff(dist, dur, rate) => assertTrue(name, dist > 0 && dur > 0 && rate > 0)
        case TeleportCast(dist) => assertTrue(name, dist > 0)
        case PhaseShiftBuff(dur) => assertTrue(name, dur > 0 && dur < a.cooldownMs)
        case GroundSlam(radius) => assertTrue(name, radius > 0)
        // Down before it is ready again: the server counts its cooldown from the raise, and one
        // that outlasted it could be raised afresh the moment the last came down
        case BarrierCast(dur) => assertTrue(name, dur > 0 && dur < a.cooldownMs)
        case StandardProjectile =>
      }
    }
    all.foreach(c => assertTrue(s"${c.displayName} health", c.maxHealth > 0))
    // A multiplier on the base walking rate (Movement), so a stray 10f is a character crossing
    // the map in a second and a stray 0.1f is one who can't get out of their spawn
    all.foreach(c => assertTrue(s"${c.displayName} speed ${c.moveSpeed}",
      c.moveSpeed >= 0.5f && c.moveSpeed <= 2.0f))
  }

  @Test def aBarrierFiresNothing(): Unit = {
    // It is raised by the player's update, not by a spawn: a spawn claiming it is refused, and its
    // projectile type is -3, which nothing is registered as
    val barriers = for (c <- all; a <- Seq(c.qAbility, c.eAbility) if a.castBehavior.isInstanceOf[BarrierCast]) yield a
    assertTrue("the Crusader has one", barriers.nonEmpty)
    barriers.foreach { a =>
      assertFalse(a.name, firesProjectiles(a))
      assertEquals(a.name, -3, a.projectileType.toInt)
    }
  }

  @Test def everySpriteSheetIsThere(): Unit = {
    // Bundled from sprites/ (a data dependency of this test)
    for (c <- all) assertTrue(c.spriteSheet, new File(c.spriteSheet).isFile)
  }

  @Test def keybindsAreQAndE(): Unit = {
    all.foreach { c =>
      assertEquals(c.displayName, "Q", c.qAbility.keybind)
      assertEquals(c.displayName, "E", c.eAbility.keybind)
    }
  }
}
