package com.gridgame.common.model

/**
 * What a hit does to its target besides the damage: a projectile's (ProjectileDef.onHitEffects),
 * a splash's and a slam's, and a trap's (TrapDef.effects). The server applies them; the client
 * only draws what the server says they did.
 */
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
