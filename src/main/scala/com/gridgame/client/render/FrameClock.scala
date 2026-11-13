package com.gridgame.client.render

/**
 * Where the game renderer reads the time: the system's clocks, unless a dev tool holds them still,
 * or a match's window paces its frames (onFrames). GoldenFrames renders fixed scenes on a stopped
 * clock, so two builds of the renderer can be compared pixel for pixel.
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

  /** The system's time of day, and in nanoseconds the time of the frame being drawn
    * (GameClient.frameNanos, which a match's window paces on the display's cadence): everything the
    * renderer moves itself, other players among them, is placed at it. */
  def onFrames(frameNanos: () => Long): FrameClock = new FrameClock {
    def nowMs(): Long = java.lang.System.currentTimeMillis()
    def nowNanos(): Long = frameNanos()
  }
}
