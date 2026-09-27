package com.gridgame.common.model

/**
 * A character's Q and E abilities: what each one is (AbilityDef), and how it is cast
 * (CastBehavior). Most abilities fire a projectile; the rest move the caster, raise a barrier or
 * throw a trap, and carry a projectile type below zero to say so (-1 a buff or dash, -2 a
 * teleport, -3 a barrier, -4 a trap).
 */

/** How an ability is cast. */
sealed trait CastBehavior
case object StandardProjectile extends CastBehavior
case class PhaseShiftBuff(durationMs: Int) extends CastBehavior
case class DashBuff(maxDistance: Int, durationMs: Int, moveRateMs: Int) extends CastBehavior
case class TeleportCast(maxDistance: Int) extends CastBehavior
case class FanProjectile(count: Int, fanAngle: Double) extends CastBehavior {
  def isFullCircle: Boolean = fanAngle >= 2 * Math.PI - 0.1

  /** Heading of projectile `i` relative to the aim, in radians. A fan spans its angle edge to edge.
    * A full circle is spaced evenly starting from the aim: spanned edge to edge, its first and last
    * projectiles land on the same heading, straight behind the caster, with none toward the cursor. */
  def angleOf(i: Int): Double =
    if (isFullCircle) 2 * Math.PI * i / count
    else if (count <= 1) 0.0
    else -fanAngle / 2 + fanAngle * i / (count - 1)
}
case class GroundSlam(radius: Float) extends CastBehavior
/** Raise a [[Barrier]]: a shield wall carried in front of the caster, facing their aim, that stops
  * enemy projectiles for `durationMs` or until the caster fires or casts anything. Fires nothing,
  * so its ability's projectile type is -3. */
case class BarrierCast(durationMs: Int) extends CastBehavior
/** Throw a [[Trap]] of `trapType` onto the ground, at most `maxRange` cells toward the cursor
  * ([[TrapPlacement]]). Fires nothing, so its ability's projectile type is -4. */
case class TrapCast(trapType: Byte, maxRange: Int) extends CastBehavior

case class AbilityDef(
    name: String,
    description: String,
    cooldownMs: Int,
    maxRange: Int,
    damage: Int,
    projectileType: Byte,
    keybind: String,
    castBehavior: CastBehavior = StandardProjectile
)
