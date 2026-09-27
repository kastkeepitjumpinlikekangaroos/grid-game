package com.gridgame.server.game

import com.gridgame.common.model.Barrier
import com.gridgame.common.model.Player

import java.util.UUID

/** Where a holder's barrier was on the tick it was last snapshotted. One per holder, reused. */
private final class BarrierPose {
  var x = 0f
  var y = 0f
  var cos = 0f
  var sin = 0f
  var tick = 0L
}

private[game] object BarrierSnapshot {
  // A stopped projectile is left this far short of the barrier, on the side it came from: its
  // blast, if it has one, is then clearly in front of the barrier rather than on the line
  final val STANDOFF = 0.05f
}

/**
 * The raised barriers, snapshotted once a tick (Barriers in the gameplay docs) for the projectile
 * tick to ask of: which of them stop what a player fires, the first one a path meets, whether one
 * stands between a point and a player, and which one was carried or swung onto a point since the
 * last tick. A set of barriers is a bit mask over the snapshot's slots. It belongs to the
 * projectile tick's thread, which rebuilds it in place.
 */
private[game] final class BarrierSnapshot(isTeammate: (UUID, UUID) => Boolean) {
  import BarrierSnapshot.STANDOFF

  /** Raised barriers, snapshotted once per tick into preallocated arrays: whose, where the holder
   *  stands, which way it faces, and where it was on the tick before if it was up then, so a
   *  barrier carried or swung onto a projectile stops it as surely as one the projectile flies
   *  into. */
  private val MAX_BARRIERS = 64
  private val barrierOwner = new Array[UUID](MAX_BARRIERS)
  private val barrierX = new Array[Float](MAX_BARRIERS)
  private val barrierY = new Array[Float](MAX_BARRIERS)
  private val barrierCos = new Array[Float](MAX_BARRIERS)
  private val barrierSin = new Array[Float](MAX_BARRIERS)
  private val barrierMoved = new Array[Boolean](MAX_BARRIERS)
  private val barrierPrevX = new Array[Float](MAX_BARRIERS)
  private val barrierPrevY = new Array[Float](MAX_BARRIERS)
  private val barrierPrevCos = new Array[Float](MAX_BARRIERS)
  private val barrierPrevSin = new Array[Float](MAX_BARRIERS)
  private var barrierCount = 0
  private var tickCount = 0L
  private val barrierPoses = new java.util.HashMap[UUID, BarrierPose]()
  // Where the last crossing found by firstCrossing lay along its path (0-1)
  var crossingT = 0f
  // Where on its face the last barrier found by sweptOver met the point
  var faceX = 0f
  var faceY = 0f

  /** Whose barrier is in slot `i`. */
  def owner(i: Int): UUID = barrierOwner(i)

  def rebuild(allPlayers: java.util.Collection[Player]): Unit = {
    tickCount += 1
    var count = 0
    val iter = allPlayers.iterator()
    while (iter.hasNext && count < MAX_BARRIERS) {
      val player = iter.next()
      // A phased holder's barrier is as insubstantial as they are
      if (player.hasBarrier && !player.isDead && !player.isPhased) {
        val id = player.getId
        val pos = player.getPosition
        val x = pos.getX.toFloat
        val y = pos.getY.toFloat
        val angle = player.getBarrierAngle
        val cos = Math.cos(angle).toFloat
        val sin = Math.sin(angle).toFloat
        barrierOwner(count) = id
        barrierX(count) = x; barrierY(count) = y
        barrierCos(count) = cos; barrierSin(count) = sin
        var pose = barrierPoses.get(id)
        if (pose == null) { pose = new BarrierPose; barrierPoses.put(id, pose) }
        val moved = pose.tick == tickCount - 1 && (pose.x != x || pose.y != y || pose.cos != cos || pose.sin != sin)
        barrierMoved(count) = moved
        if (moved) {
          barrierPrevX(count) = pose.x; barrierPrevY(count) = pose.y
          barrierPrevCos(count) = pose.cos; barrierPrevSin(count) = pose.sin
        }
        pose.x = x; pose.y = y; pose.cos = cos; pose.sin = sin; pose.tick = tickCount
        count += 1
      }
    }
    var i = count
    while (i < barrierCount) { barrierOwner(i) = null; i += 1 }
    barrierCount = count
  }

  /** The barriers that stop what `ownerId` fires: everyone's but their own and their allies'.
   *  With `bodies`, what stops the projectile itself, which nothing does for one that flies over
   *  walls; without, what shelters a player from its blast. */
  def against(ownerId: UUID, passesThroughWalls: Boolean, bodies: Boolean): Long = {
    if (barrierCount == 0 || (bodies && passesThroughWalls)) return 0L
    var mask = 0L
    var i = 0
    while (i < barrierCount) {
      val holder = barrierOwner(i)
      if (!holder.equals(ownerId) && !isTeammate(ownerId, holder)) mask |= 1L << i
      i += 1
    }
    mask
  }

  /** The first of `mask`'s barriers the path from (ax, ay) to (bx, by) meets from the front, or
   *  -1. Where along the path it met it is left in crossingT. */
  def firstCrossing(mask: Long, ax: Float, ay: Float, bx: Float, by: Float): Int = {
    var best = -1
    var bestT = 2f
    // Nothing further than this from a holder can be anywhere near their barrier
    val reach = Barrier.REACH + Math.abs(bx - ax) + Math.abs(by - ay)
    var i = 0
    while (i < barrierCount) {
      if ((mask & (1L << i)) != 0L) {
        val hx = barrierX(i); val hy = barrierY(i)
        if (Math.abs(ax - hx) <= reach && Math.abs(ay - hy) <= reach) {
          val t = Barrier.crossing(hx, hy, barrierCos(i), barrierSin(i), ax, ay, bx, by)
          if (t >= 0f && t < bestT) { best = i; bestT = t }
        }
      }
      i += 1
    }
    crossingT = bestT
    best
  }

  /** Is the straight line from (x, y) to the player cut by one of `mask`'s barriers? */
  def shelters(mask: Long, x: Float, y: Float, player: Player): Boolean = {
    val pos = player.getPosition
    firstCrossing(mask, x, y, pos.getX.toFloat, pos.getY.toFloat) >= 0
  }

  /**
   * Which of `mask`'s barriers, having moved or turned since the last tick, was carried onto the
   * point (x, y) — a projectile that hasn't moved yet this tick — or -1. Seen from the barrier, the
   * point went from where it was relative to the old pose to where it is relative to the new one;
   * if that crosses the front, the barrier met it. Where on its face, just in front of it, is left
   * in faceX, faceY.
   */
  def sweptOver(mask: Long, x: Float, y: Float): Int = {
    var i = 0
    while (i < barrierCount) {
      if ((mask & (1L << i)) != 0L && barrierMoved(i)) {
        val hx = barrierX(i); val hy = barrierY(i); val c = barrierCos(i); val s = barrierSin(i)
        val px = barrierPrevX(i); val py = barrierPrevY(i); val pc = barrierPrevCos(i); val ps = barrierPrevSin(i)
        val reach = Barrier.REACH + Math.abs(hx - px) + Math.abs(hy - py)
        if (Math.abs(x - hx) <= reach && Math.abs(y - hy) <= reach) {
          val across = Barrier.acrossOf(hx, hy, c, s, x, y)
          val t = Barrier.crossingLocal(Barrier.forwardOf(px, py, pc, ps, x, y), Barrier.acrossOf(px, py, pc, ps, x, y),
            Barrier.forwardOf(hx, hy, c, s, x, y), across)
          if (t >= 0f) {
            // On the barrier's face where the point is, just in front of it
            val f = Barrier.forwardAt(across) + STANDOFF
            faceX = hx + f * c - across * s
            faceY = hy + f * s + across * c
            return i
          }
        }
      }
      i += 1
    }
    -1
  }

  def clear(): Unit = {
    barrierCount = 0
    java.util.Arrays.fill(barrierOwner.asInstanceOf[Array[AnyRef]], null)
    barrierPoses.clear()
  }
}
