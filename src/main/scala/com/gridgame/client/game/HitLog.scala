package com.gridgame.client.game

import java.util.UUID

/**
 * Every hit on a player as it is heard, in order, for the renderer to bleed (render/world/Blood): who
 * was struck, where the projectile was as it struck them (a shot a step short of them, a blast's
 * centre), which way it was going, what it was, and when. A ring the packet thread writes ([[add]])
 * and the render thread reads by number, each hit once, nothing allocated on either side.
 *
 * One writer and one reader. The count is written after the hit's slot, so a reader that sees hit
 * `n` counted sees all of it. A reader that falls more than [[HitLog.Size]] hits behind finds the
 * oldest gone ([[oldest]]); one overtaken while it reads a slot (as many hits as the ring holds heard
 * in the time it takes to read one) would read a mixture of two, which is only ever drawn.
 */
final class HitLog {
  import HitLog._

  private val targets = new Array[UUID](Size)
  private val times = new Array[Long](Size)
  private val xs = new Array[Float](Size)
  private val ys = new Array[Float](Size)
  private val dxs = new Array[Float](Size)
  private val dys = new Array[Float](Size)
  private val types = new Array[Byte](Size)
  @volatile private var count: Long = 0L

  /** `target` was struck at `now` by a projectile of type `pType` at (x, y), going (dx, dy). */
  def add(target: UUID, x: Float, y: Float, dx: Float, dy: Float, pType: Byte, now: Long): Unit = {
    val n = count
    val i = slot(n)
    targets(i) = target
    times(i) = now
    xs(i) = x; ys(i) = y
    dxs(i) = dx; dys(i) = dy
    types(i) = pType
    count = n + 1
  }

  /** How many hits have been heard: they are numbered from 0, and the next will be this. */
  def heard: Long = count

  /** The oldest hit still held. */
  def oldest: Long = Math.max(0L, count - Size)

  def target(n: Long): UUID = targets(slot(n))
  def time(n: Long): Long = times(slot(n))
  def x(n: Long): Float = xs(slot(n))
  def y(n: Long): Float = ys(slot(n))
  def dx(n: Long): Float = dxs(slot(n))
  def dy(n: Long): Float = dys(slot(n))
  def projectileType(n: Long): Byte = types(slot(n))
}

object HitLog {
  /** How many hits the ring holds: a power of two, and many more than are heard between two frames
    * (a crowded fight's splashes and fans are a few a tick). */
  val Size = 64

  @inline private def slot(n: Long): Int = (n & (Size - 1)).toInt
}
