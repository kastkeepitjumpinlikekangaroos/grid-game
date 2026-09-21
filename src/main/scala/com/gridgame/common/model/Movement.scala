package com.gridgame.common.model

import com.gridgame.common.Constants

/**
 * How long a player waits between steps.
 *
 * One rule, shared by the keyboard and controller handlers, the bots and the server's speed
 * check, so a character's speed can't mean one thing to the client that moves them and another
 * to the server that checks the move. The two input handlers each carried their own copy of it.
 *
 * A character's `moveSpeed` (CharacterDef) multiplies the base rate of 20 cells a second, so a
 * speed of 1.2 steps every 42ms instead of 50. Everything else is a factor on that interval:
 * charging drags it out, a phase or a speed boost shortens it, a slow lengthens it by its own
 * multiplier — a Slow(_, 0.4f) really is 40% speed, where every slow used to be a flat half.
 */
object Movement {
  /** A character of speed 1.0 steps a cell every this many ms: 20 cells a second. */
  val BASE_STEP_MS: Int = Constants.MOVE_RATE_LIMIT_MS

  /** Phased (a phase shift or a dash): twice the pace. */
  private val PHASED_FACTOR = 0.5

  /** A speed boost: 40% quicker. */
  private val BOOST_FACTOR = 0.6

  /** A full charge drags a step out to ten times its length. */
  private val CHARGE_MAX_FACTOR = 9.0

  /** How slow a slow may make a player, whatever its multiplier says. */
  private val MIN_SLOW_MULTIPLIER = 0.05

  /** The interval this character steps at with nothing acting on them. */
  def baseStepIntervalMs(moveSpeed: Float): Double =
    BASE_STEP_MS / Math.max(0.1, moveSpeed.toDouble)

  /**
   * The interval between steps for a character of `moveSpeed` in this state. Charging wins over
   * a phase, a phase over a boost, a boost over a slow — the order the input handlers have
   * always used.
   */
  def stepIntervalMs(moveSpeed: Float, charging: Boolean, chargeLevel: Int, phased: Boolean,
                     speedBoost: Boolean, slowed: Boolean, slowMultiplier: Float): Int = {
    val base = baseStepIntervalMs(moveSpeed)
    if (charging) {
      // Truncated, as it always was: at full charge a step takes ten times as long
      val charge = Math.max(0, Math.min(100, chargeLevel)) / 100.0
      return Math.max(1, (base * (1.0 + charge * CHARGE_MAX_FACTOR)).toInt)
    }
    val factor =
      if (phased) PHASED_FACTOR
      else if (speedBoost) BOOST_FACTOR
      else if (slowed) 1.0 / Math.max(MIN_SLOW_MULTIPLIER, Math.min(1.0, slowMultiplier.toDouble))
      else 1.0
    // Rounded rather than truncated: 1/0.4 is 2.4999999 in floating point, and a truncated
    // 124ms interval for a 125ms slow is the kind of difference that only shows up as a
    // failing test years later.
    Math.max(1, Math.round(base * factor).toInt)
  }
}
