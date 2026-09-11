package com.gridgame.common.model

import com.gridgame.common.Constants

/**
 * Where teleports may land. The client picks its landing cell with these and the server checks
 * the result with these, so a teleport the client shows is one the server accepts. When the two
 * disagreed, the client moved and the server didn't, and the next update from the server put the
 * player back where they started.
 */
object Teleport {

  /**
   * Star item: the farthest cell toward the aim point that can be stood on, at most
   * STAR_MAX_DISTANCE (Manhattan) from (fromX, fromY). Aiming past the range, off the map or
   * into a wall lands short along the same line. None when that leaves nowhere to go.
   */
  def starTarget(world: WorldData, fromX: Int, fromY: Int, aimX: Double, aimY: Double): Option[Position] = {
    val range = Constants.STAR_MAX_DISTANCE
    val dx = aimX - fromX
    val dy = aimY - fromY
    val reach = Math.abs(dx) + Math.abs(dy)
    val scale = if (reach > range) range / reach else 1.0
    val ex = dx * scale
    val ey = dy * scale
    // Back along the line from its far end in half-cell steps: the first cell that is walkable
    // and in range is the farthest one
    val steps = Math.ceil(Math.max(Math.abs(ex), Math.abs(ey)) * 2).toInt
    var i = steps
    while (i > 0) {
      val t = i.toDouble / steps
      val x = Math.round(fromX + ex * t).toInt
      val y = Math.round(fromY + ey * t).toInt
      if ((x != fromX || y != fromY) && Math.abs(x - fromX) + Math.abs(y - fromY) <= range &&
          world.isWalkable(x, y)) {
        return Some(new Position(x, y))
      }
      i -= 1
    }
    None
  }

  /** Server side: may a player the server has at (fromX, fromY) star to (x, y)? */
  def isValidStarTarget(world: WorldData, fromX: Int, fromY: Int, x: Int, y: Int): Boolean =
    world.isWalkable(x, y) &&
      Math.abs(x.toLong - fromX) + Math.abs(y.toLong - fromY) <=
        Constants.STAR_MAX_DISTANCE + Constants.TELEPORT_RANGE_TOLERANCE

  /**
   * Blink (TeleportCast): step along the unit vector (dirX, dirY) up to maxDistance cells,
   * stopping before the first cell that can't be stood on. (fromX, fromY) if the first step
   * is blocked.
   */
  def blinkTarget(world: WorldData, fromX: Int, fromY: Int, dirX: Double, dirY: Double, maxDistance: Int): Position = {
    var bestX = fromX
    var bestY = fromY
    var step = 1
    while (step <= maxDistance) {
      val x = Math.max(0, Math.min(world.width - 1, Math.round(fromX + dirX * step).toInt))
      val y = Math.max(0, Math.min(world.height - 1, Math.round(fromY + dirY * step).toInt))
      if (!world.isWalkable(x, y)) return new Position(bestX, bestY)
      bestX = x
      bestY = y
      step += 1
    }
    new Position(bestX, bestY)
  }

  /**
   * Knockback and vortex pulls: the same straight walk as a blink, up to `cells` along
   * (dirX, dirY), stopping before the first cell that can't be stood on. They used to take the
   * farthest open cell along the line whatever lay between, which carried players through walls.
   */
  def slide(world: WorldData, fromX: Int, fromY: Int, dirX: Double, dirY: Double, cells: Int): Position =
    blinkTarget(world, fromX, fromY, dirX, dirY, cells)

  /**
   * Server side: is a jump of (dx, dy) within reach of a blink or dash of range maxDistance?
   * Measured in a straight line, the way both are aimed: a diagonal blink covers up to 1.4x its
   * range in Manhattan distance, which a Manhattan check refused.
   */
  def withinReach(dx: Int, dy: Int, maxDistance: Int): Boolean = {
    val r = (maxDistance + Constants.TELEPORT_RANGE_TOLERANCE).toLong
    dx.toLong * dx + dy.toLong * dy <= r * r
  }
}
