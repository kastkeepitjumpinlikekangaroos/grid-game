package com.gridgame.common.model

// On-hit effects
sealed trait OnHitEffect
case object PullToOwner extends OnHitEffect
case class Freeze(durationMs: Int) extends OnHitEffect
case class Push(distance: Float) extends OnHitEffect
case class TeleportOwnerBehind(distance: Int, freezeDurationMs: Int) extends OnHitEffect
case class LifeSteal(healPercent: Int) extends OnHitEffect
case class Burn(totalDamage: Int, durationMs: Int, tickMs: Int) extends OnHitEffect
case class VortexPull(radius: Float, pullStrength: Float) extends OnHitEffect
case class SpeedBoost(durationMs: Int) extends OnHitEffect
case class Root(durationMs: Int) extends OnHitEffect
case class Slow(durationMs: Int, multiplier: Float) extends OnHitEffect
/** A hold with the same rules and timer as [[Freeze]]: the target reads as frozen everywhere the
  * server and client already gate on that, but clients draw a stun rather than ice. */
case class Stun(durationMs: Int) extends OnHitEffect
/** Damage over time in its own slot, so it runs alongside a [[Burn]] rather than replacing it. */
case class Poison(totalDamage: Int, durationMs: Int, tickMs: Int) extends OnHitEffect

// Explosion config (center/edge damage + blast radius)
case class ExplosionConfig(centerDamage: Int, edgeDamage: Int, blastRadius: Float)

// AoE splash config (radius + damage + optional hold)
case class AoESplashConfig(radius: Float, damage: Int, freezeDurationMs: Int = 0, rootDurationMs: Int = 0,
                           stunDurationMs: Int = 0)

// Charge scaling config
case class ChargeScaling(min: Float, max: Float) {
  def interpolate(chargeLevel: Int): Float =
    min + (chargeLevel / 100.0f) * (max - min)
}

// Distance-based damage scaling (e.g. spear)
case class DistanceDamageScaling(baseDamage: Int, maxDamage: Int, maxRange: Int)

case class ProjectileDef(
    id: Byte,
    name: String,
    speedMultiplier: Float,
    chargeSpeedScaling: Option[ChargeScaling] = None,
    damage: Int,
    chargeDamageScaling: Option[ChargeScaling] = None,
    distanceDamageScaling: Option[DistanceDamageScaling] = None,
    maxRange: Int,
    chargeRangeScaling: Option[ChargeScaling] = None,
    hitRadius: Float = 1.8f,
    passesThroughPlayers: Boolean = false,
    passesThroughWalls: Boolean = false,
    onHitEffect: Option[OnHitEffect] = None,
    // More effects on the same hit, after onHitEffect: a dart that poisons and slows, a horn that
    // knocks back and stuns. Applied in order, direct hits and blasts alike (onHitEffects)
    alsoOnHit: Seq[OnHitEffect] = Nil,
    aoeOnHit: Option[AoESplashConfig] = None,
    aoeOnMaxRange: Option[AoESplashConfig] = None,
    explosionConfig: Option[ExplosionConfig] = None,
    explodesOnPlayerHit: Boolean = false,
    boomerang: Boolean = false,
    pierceCount: Int = 0,
    ricochetCount: Int = 0
) {
  def effectiveSpeed(chargeLevel: Int): Float =
    chargeSpeedScaling match {
      case Some(scaling) if chargeLevel > 0 => scaling.interpolate(chargeLevel)
      case _ => speedMultiplier
    }

  def effectiveDamage(chargeLevel: Int, distanceTraveled: Float = 0f): Int =
    distanceDamageScaling match {
      case Some(dds) =>
        val fraction = Math.min(1.0f, distanceTraveled / dds.maxRange.toFloat)
        (dds.baseDamage + (fraction * (dds.maxDamage - dds.baseDamage))).toInt
      case None =>
        chargeDamageScaling match {
          case Some(scaling) if chargeLevel > 0 => scaling.interpolate(chargeLevel).toInt
          case _ => damage
        }
    }

  def effectiveMaxRange(chargeLevel: Int): Double =
    chargeRangeScaling match {
      case Some(scaling) if chargeLevel > 0 => scaling.interpolate(chargeLevel).toDouble
      case _ => maxRange.toDouble
    }

  def isExplosive: Boolean = explosionConfig.isDefined

  /** Everything a hit does to its target, in order: onHitEffect, then alsoOnHit. */
  val onHitEffects: Seq[OnHitEffect] = onHitEffect.toSeq ++ alsoOnHit

  /** After hitting `hits` players, does it fly on? `pierceCount` is how many players it passes
    * through, so it is used up by hit number pierceCount + 1. */
  def piercesAfter(hits: Int): Boolean = hits <= pierceCount
}

object ProjectileDef {
  @volatile private var registry: Map[Byte, ProjectileDef] = Map.empty

  def register(defs: ProjectileDef*): Unit =
    defs.foreach(d => registry += (d.id -> d))

  def get(id: Byte): ProjectileDef = {
    val d = registry.getOrElse(id, null)
    if (d != null) d
    else {
      // The definitions are registered by CharacterDef's initializer, so until something had
      // touched CharacterDef every lookup missed and came back as the default bolt: the gallery
      // drew wall-passers with no lift, and a Projectile built then took the default's speed.
      CharacterDef.ensureRegistered()
      registry.getOrElse(id, defaultDef)
    }
  }

  // Default def for the NORMAL (charge) projectile
  private val defaultDef: ProjectileDef = ProjectileDef(
    id = ProjectileType.NORMAL,
    name = "Normal",
    speedMultiplier = 0.8f, // not used directly — charge scaling overrides
    chargeSpeedScaling = Some(ChargeScaling(0.8f, 2.0f)),
    damage = 10,
    chargeDamageScaling = Some(ChargeScaling(10f, 100f)),
    maxRange = 15,
    chargeRangeScaling = Some(ChargeScaling(15f, 30f))
  )
}
