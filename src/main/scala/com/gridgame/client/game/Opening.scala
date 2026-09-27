package com.gridgame.client.game

import com.gridgame.client.audio.AudioManager
import com.gridgame.common.model._

import java.util.concurrent.atomic.AtomicLong

/**
 * The opening of the match (MatchOpening): the server says how long it has left and what it does
 * while it lasts, and we run it on our own clock, which is always a little behind the server's —
 * the word took a trip down the wire to get here — so ours never ends before theirs does. A Teams
 * match's wall hangs off our copy of the world, so every walkability check on this side refuses
 * its cells exactly as the server's does: a step, a blink, a star, a throw. A free-for-all's
 * ceasefire holds our attacks ([[attacksLocked]]).
 */
trait Opening { this: GameClient =>
  private val openingEndsAt: AtomicLong = new AtomicLong(0)
  @volatile private var openingRules: Byte = 0

  // Shots stopped on the wall, for the flash where each struck: a ring of slots in world coordinates
  val DIVIDER_IMPACT_SLOTS = 12
  private val dividerImpactX = new Array[Float](DIVIDER_IMPACT_SLOTS)
  private val dividerImpactY = new Array[Float](DIVIDER_IMPACT_SLOTS)
  private val dividerImpactTime = new Array[Long](DIVIDER_IMPACT_SLOTS)
  private var dividerImpactNext = 0

  /** The wall between the two halves of the map while a Teams match opens, or null. It may have
    * run out: the renderer sinks it into the ground over its last moments, and nothing is held
    * by it once it has. */
  def divider: TeamDivider = { val w = currentWorld.get(); if (w == null) null else w.divider }

  /** How long the opening has left on our clock, 0 once it is over. */
  def openingMsLeft: Long = Math.max(0L, openingEndsAt.get() - System.currentTimeMillis())

  /** When the opening ends (or ended), on our clock: what the HUD counts down to. */
  def openingEndsAtMs: Long = openingEndsAt.get()

  /** What this match's opening does, for as long as it lasts ([[MatchOpening]]). Kept after it
    * ends, so the HUD knows which opening it is seeing out. */
  def openingRulesNow: Byte = openingRules

  /** Are we holding our fire — the opening of a free-for-all? Nothing attacks: not the primary,
    * not a charge, not the burst, not an ability. The server would refuse any of them anyway. */
  def attacksLocked: Boolean =
    MatchOpening.has(openingRules, MatchOpening.NO_ATTACKS) && openingMsLeft > 0L

  /** The server's word on the opening: how long it has left (0 — it is over now) and what it
    * does while it lasts. */
  private[game] def setOpening(msLeft: Int, rules: Byte): Unit = {
    val now = System.currentTimeMillis()
    openingRules = rules
    val wasOn = openingEndsAt.getAndSet(if (msLeft > 0) now + msLeft else now) > now
    installDivider()
    if (msLeft > 0 && !wasOn && MatchOpening.has(rules, MatchOpening.DIVIDER)) AudioManager.playBarrierUp()
  }

  /** Put the Teams wall on the world. Called from both sides of the race between the world
    * arriving and the server's word about the opening, since either can come first. */
  private[game] def installDivider(): Unit = {
    val world = currentWorld.get()
    val ends = openingEndsAt.get()
    if (world == null || ends == 0L || !MatchOpening.has(openingRules, MatchOpening.DIVIDER)) return
    val d = world.divider
    if (d == null || d.endsAt != ends) world.divider = TeamDivider.raise(world, ends)
  }

  /** A new match: no opening until this one's is announced. */
  private[game] def clearOpening(): Unit = {
    openingEndsAt.set(0)
    openingRules = 0
    val world = currentWorld.get()
    if (world != null) world.divider = null
    java.util.Arrays.fill(dividerImpactTime, 0L)
  }

  /** A shot stopped on the divider at (x, y): kept for the flash where it struck. */
  private[client] def recordDividerImpact(x: Float, y: Float, now: Long): Unit = {
    dividerImpactX.synchronized {
      val i = dividerImpactNext
      dividerImpactX(i) = x
      dividerImpactY(i) = y
      dividerImpactTime(i) = now
      dividerImpactNext = (i + 1) % DIVIDER_IMPACT_SLOTS
    }
  }

  /** Slot `i` of the divider impacts: where a shot struck it, and when (0 if the slot is unused). */
  def getDividerImpactX(i: Int): Float = dividerImpactX(i)
  def getDividerImpactY(i: Int): Float = dividerImpactY(i)
  def getDividerImpactTime(i: Int): Long = dividerImpactTime(i)
}
