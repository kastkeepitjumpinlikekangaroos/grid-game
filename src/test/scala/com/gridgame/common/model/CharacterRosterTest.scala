package com.gridgame.common.model

import com.gridgame.common.Constants
import org.junit.Assert._
import org.junit.Test

import java.io.File
import javax.imageio.ImageIO

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

  @Test def everyProjectileFliesOrEndsWhereItIsCast(): Unit = {
    // A projectile that neither moves nor has a range of nothing never reaches the end of its
    // range: it sits where it was cast for the rest of the match, hitting whoever walks into it
    for (c <- all) {
      val fired = c.primaryProjectileType +: Seq(c.qAbility, c.eAbility).filter(firesProjectiles).map(_.projectileType)
      for (t <- fired; charge <- Seq(0, 1, 50, 100)) {
        val d = ProjectileDef.get(t)
        assertTrue(s"${c.displayName}'s ${d.name} at charge $charge",
          d.effectiveSpeed(charge) > 0f || d.effectiveMaxRange(charge) <= 0.0)
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
        // Thrown somewhere, so it needs a range; and a trap nobody defined would be a cooldown
        // spent on a cell that stays empty
        case TrapCast(trapType, maxRange) =>
          assertTrue(s"$name range", maxRange > 0)
          assertNotNull(s"$name trap type $trapType", TrapDef.get(trapType))
        case StandardProjectile =>
      }
    }
    all.foreach(c => assertTrue(s"${c.displayName} health", c.maxHealth > 0))
    // A multiplier on the base walking rate (Movement), so a stray 10f is a character crossing
    // the map in a second and a stray 0.1f is one who can't get out of their spawn
    all.foreach(c => assertTrue(s"${c.displayName} speed ${c.moveSpeed}",
      c.moveSpeed >= 0.5f && c.moveSpeed <= 2.0f))
  }

  @Test def everyHitAndEveryTrapHasSensibleNumbers(): Unit = {
    // What the server does with each without asking: a burn's damage divided by its ticks
    // (Player.applyBurn: a tick of 0 is an ArithmeticException on the projectile tick, every hit),
    // a slow's multiplier taken as a pace, a blast's damage falling from its centre to its edge
    def effect(name: String, e: OnHitEffect): Unit = e match {
      case Freeze(ms) => assertTrue(name, ms > 0)
      case Stun(ms) => assertTrue(name, ms > 0)
      case Root(ms) => assertTrue(name, ms > 0)
      case Slow(ms, m) => assertTrue(s"$name: $ms ms at $m", ms > 0 && m > 0f && m < 1f)
      case Burn(total, ms, tick) => assertTrue(s"$name: $total over $ms every $tick", total > 0 && tick > 0 && ms >= tick)
      case Poison(total, ms, tick) => assertTrue(s"$name: $total over $ms every $tick", total > 0 && tick > 0 && ms >= tick)
      case Push(d) => assertTrue(name, d >= 1f)
      case VortexPull(r, s) => assertTrue(name, r > 0f && s > 0f)
      case LifeSteal(pct) => assertTrue(name, pct > 0 && pct <= 100)
      case SpeedBoost(ms) => assertTrue(name, ms > 0)
      case TeleportOwnerBehind(d, ms) => assertTrue(name, d > 0 && ms >= 0)
      case PullToOwner =>
    }
    val thrown = all.flatMap(c => Seq(c.primaryProjectileType, c.qAbility.projectileType, c.eAbility.projectileType))
      .filter(_ >= 0).distinct
    for (t <- thrown) {
      val d = ProjectileDef.get(t)
      val name = d.name
      assertTrue(s"$name hit radius", d.hitRadius > 0f)
      assertTrue(s"$name numbers", d.damage >= 0 && d.maxRange >= 0 && d.speedMultiplier >= 0f && d.pierceCount >= 0 &&
        d.ricochetCount >= 0 && d.hitCentreBehind >= 0f)
      d.onHitEffects.foreach(effect(name, _))
      (d.aoeOnHit.toSeq ++ d.aoeOnMaxRange).foreach(a => assertTrue(s"$name splash", a.radius > 0f && a.damage >= 0))
      d.explosionConfig.foreach(x => assertTrue(s"$name blast", x.blastRadius > 0f && x.centerDamage >= x.edgeDamage && x.edgeDamage >= 0))
    }
    for (trap <- TrapDef.all) {
      assertTrue(trap.name, trap.triggerRadius > 0f && trap.armDelayMs >= 0 && trap.lifetimeMs > trap.armDelayMs &&
        trap.maxActive > 0 && trap.damage >= 0)
      trap.effects.foreach(effect(trap.name, _))
      trap.explosion.foreach(x => assertTrue(s"${trap.name} blast", x.blastRadius > 0f && x.centerDamage >= x.edgeDamage))
    }
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

  @Test def aTrapFiresNothingAndLandsWithinItsAbilitysRange(): Unit = {
    // It goes down through a trap packet, not a spawn, so its projectile type is -4 — which
    // nothing is registered as — and a spawn claiming it is refused
    val traps = for (c <- all; a <- Seq(c.qAbility, c.eAbility) if a.castBehavior.isInstanceOf[TrapCast]) yield (c, a)
    assertTrue("the Warden has one", traps.nonEmpty)
    traps.foreach { case (c, a) =>
      val cast = a.castBehavior.asInstanceOf[TrapCast]
      val name = s"${c.displayName} ${a.name}"
      assertFalse(name, firesProjectiles(a))
      assertEquals(name, -4, a.projectileType.toInt)
      // The ability's own range is what the character panel shows and what the bots aim by
      assertEquals(s"$name range", cast.maxRange, a.maxRange)
      val tDef = TrapDef.get(cast.trapType)
      assertTrue(s"$name lasts longer than it takes to arm", tDef.lifetimeMs > tDef.armDelayMs)
      assertTrue(s"$name may have more than one out", tDef.maxActive >= 1)
      assertTrue(s"$name does something", tDef.damage > 0 || tDef.effects.nonEmpty || tDef.explosion.isDefined)
    }
  }

  @Test def everySpriteSheetIsThere(): Unit = {
    // Bundled from sprites/ (a data dependency of this test)
    for (c <- all) assertTrue(c.spriteSheet, new File(c.spriteSheet).isFile)
  }

  @Test def everyCharacterFitsInsideItsTile(): Unit = {
    // The renderer puts a sprite in front of a wall or behind it as one no wider than its tile
    // (client/render/CLAUDE.md, *Everything is sorted where it is drawn*): whatever reaches past
    // the tile lies over the corner of a wall level with it. The Chameleon's tongue reached 22
    // units out, at the 48 a sprite is drawn at, on a tile 20 either side of its middle.
    val frame = Constants.SPRITE_SIZE_PX
    val scale = Constants.PLAYER_DISPLAY_SIZE_PX.toDouble / frame
    for (c <- all) {
      val sheet = ImageIO.read(new File(c.spriteSheet))
      val argb = sheet.getRGB(0, 0, sheet.getWidth, sheet.getHeight, null, 0, sheet.getWidth)
      var widest = 0
      var i = 0
      while (i < argb.length) {
        // Anything more than faintly there: the antialiased fringe is not the sprite
        if ((argb(i) >>> 24) > 32) {
          val x = (i % sheet.getWidth) % frame
          widest = Math.max(widest, Math.max(frame / 2 - x, x + 1 - frame / 2))
        }
        i += 1
      }
      assertTrue(s"${c.spriteSheet} reaches ${widest * scale} either side",
        widest * scale <= Constants.ISO_HALF_W)
    }
  }

  @Test def keybindsAreQAndE(): Unit = {
    all.foreach { c =>
      assertEquals(c.displayName, "Q", c.qAbility.keybind)
      assertEquals(c.displayName, "E", c.eAbility.keybind)
    }
  }
}
