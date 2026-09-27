package com.gridgame.client.render

/**
 * Where the game renderer reads the time: the system's clocks, unless a dev tool holds them still.
 * GoldenFrames renders fixed scenes on a stopped clock, so two builds of the renderer can be
 * compared pixel for pixel.
 */
trait FrameClock {
  def nowMs(): Long
  def nowNanos(): Long
}

object FrameClock {
  val System: FrameClock = new FrameClock {
    def nowMs(): Long = java.lang.System.currentTimeMillis()
    def nowNanos(): Long = java.lang.System.nanoTime()
  }
}
