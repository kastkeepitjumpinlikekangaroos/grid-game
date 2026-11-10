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
case class Burn(totalDamage: Int, durationMs: Int, tickMs: Int) extends OnHitEffect {
  /** What each bite takes: the total over the ticks its time holds, rounded down (Player.applyBurn). */
  def damagePerTick: Int = {
    val ticks = durationMs / tickMs
    if (ticks > 0) totalDamage / ticks else totalDamage
  }

  /**
   * What the burn takes in all, which is less than `totalDamage` (design/combat.md, *A burn does not
   * deal its listed total*): a bite on the first player tick (Constants.PLAYER_TICK_MS) a whole
   * `tickMs` after it was lit or last bit, for as long as it lasts. What a player is told a burn
   * does is this, not the total it was written with.
   */
  def dealt: Int = {
    var bites = 0
    var last = 0
    var t = com.gridgame.common.Constants.PLAYER_TICK_MS
    while (t < durationMs) {
      if (t >= last + tickMs) { bites += 1; last = t }
      t += com.gridgame.common.Constants.PLAYER_TICK_MS
    }
    bites * damagePerTick
  }
}
case class VortexPull(radius: Float, pullStrength: Float) extends OnHitEffect
case class SpeedBoost(durationMs: Int) extends OnHitEffect
case class Root(durationMs: Int) extends OnHitEffect
case class Slow(durationMs: Int, multiplier: Float) extends OnHitEffect
/** A hold with the same rules and timer as [[Freeze]]: the target reads as frozen everywhere the
  * server and client already gate on that, but clients draw a stun rather than ice. */
case class Stun(durationMs: Int) extends OnHitEffect
/** A [[Stun]] that knocks its target up into the air for as long as it holds them (the Samurai's
  * Crescent Cut): the same hold, timer and CC immunity, and clients draw the target lifted off the
  * ground on an arc over it, their shadow left below, as well as the stun's stars. */
case class Launch(durationMs: Int) extends OnHitEffect
/** Damage over time in its own slot, so it runs alongside a [[Burn]] rather than replacing it. */
case class Poison(totalDamage: Int, durationMs: Int, tickMs: Int) extends OnHitEffect
