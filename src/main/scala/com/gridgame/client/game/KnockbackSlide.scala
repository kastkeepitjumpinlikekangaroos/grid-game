package com.gridgame.client.game

/**
 * How a knockback is drawn. The server moves a pushed player in one go (a push is a server move,
 * design/networking.md), and drawn where it put them they vanished and reappeared cells away — on
 * other screens with a teleport's flash, since a jump that long was taken for one. Every client
 * instead slides them there from where they were drawn: away faster than anyone runs, slowing to a
 * stop, over a time that grows a little with the distance, so every shove reads alike. Only the
 * drawing: the player is where the server put them, and that is where they are hit and shoot from.
 * The local player's is LocalMovement.updateVisualPosition's, everyone else's RemoteMotion's.
 */
object KnockbackSlide {
  private val BaseMs = 120.0
  private val PerCellMs = 25.0
  private val MinMs = 150.0
  private val MaxMs = 280.0

  /** How long a slide over `cells` takes, in ms. */
  def durationMs(cells: Double): Double = Math.max(MinMs, Math.min(MaxMs, BaseMs + PerCellMs * cells))

  /** The share of the way still to go `elapsedMs` into a slide that takes `durationMs`: 1 at its
    * start and 0 from its end, going fastest at first. */
  def remaining(elapsedMs: Double, durationMs: Double): Double =
    if (elapsedMs <= 0.0) 1.0
    else if (elapsedMs >= durationMs) 0.0
    else {
      val u = 1.0 - elapsedMs / durationMs
      u * u * u
    }
}
