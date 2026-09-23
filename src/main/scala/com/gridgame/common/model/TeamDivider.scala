package com.gridgame.common.model

/**
 * The wall that stands between the two teams while a team match begins, and the fixed halves of
 * the map it splits them into.
 *
 * A Teams match spends its opening ([[MatchOpening]]) with both sides kept apart, so a team can
 * gather, pick its ground and say something to each other before anyone can shoot anyone. Team 1
 * starts in the low half of the map, team 2 in the high half, and every respawn puts a player back
 * in their own half for the rest of the match.
 *
 * The wall itself is one straight line of cells — the column (or row) down the middle of the map —
 * and while it is up **it is a hole in the world's walkability**: a raised divider hangs off
 * [[WorldData.divider]], so every check that already asks the world whether a cell can be stood on
 * refuses it. That covers a step, a dash, a blink, a knockback, a trap throw, a bot's path and a
 * spawn point without any of them knowing the divider exists. What it does not cover is what
 * ignores walls — a phased player, a star's jump, a projectile that flies over terrain — so those
 * are checked against it by hand ([[allowsMove]], [[crossing]]).
 *
 * The client runs the same wall on its own clock: the server says how long it has left, each side
 * works out the geometry from the world it loaded, and neither has to send the other a shape.
 */
object TeamDivider {
  /** Which axis the map is split across: its longer one, and x for a square map. */
  def axisX(world: WorldData): Boolean = world.width >= world.height

  /** The column (or row) the wall stands on: the middle of the map. */
  def line(world: WorldData): Int = (if (axisX(world)) world.width else world.height) / 2

  /** Which half of the map a cell is in: -1 or 1, and 0 for the line between them. */
  def sideOf(world: WorldData, x: Int, y: Int): Int =
    Integer.signum((if (axisX(world)) x else y) - line(world))

  /** The half a team spawns in: team 1 the low half, team 2 the high one. 0 — nobody's team, so
    * every free-for-all — is at home anywhere. */
  def sideOfTeam(teamId: Byte): Int = if (teamId == 1) -1 else if (teamId == 2) 1 else 0

  /** The wall over this world, standing until `endsAt` on the caller's own clock. */
  def raise(world: WorldData, endsAt: Long): TeamDivider =
    new TeamDivider(axisX(world), line(world), endsAt)
}

/** A raised divider: which way it runs, which line it stands on, and when it comes down. */
final class TeamDivider(val axisX: Boolean, val at: Int, val endsAt: Long) {

  def up: Boolean = System.currentTimeMillis() < endsAt
  def up(now: Long): Boolean = now < endsAt
  def msLeft(now: Long): Long = Math.max(0L, endsAt - now)

  /** How far a point lies across the wall: negative on one side, positive on the other. */
  @inline def across(x: Float, y: Float): Float = (if (axisX) x else y) - at

  /** Which side of the wall a cell is on: -1 or 1, and 0 for a cell the wall stands on. */
  def side(x: Int, y: Int): Int = Integer.signum((if (axisX) x else y) - at)

  /** The cells the wall stands on. Nothing can be there while it is up, which is what makes
    * [[WorldData.isWalkable]] refuse them. Checked before the clock is read, since every
    * walkability check in the game comes through here. */
  def blocks(x: Int, y: Int): Boolean = (if (axisX) x else y) == at && up

  /**
   * May a player at (fromX, fromY) end up at (toX, toY)? Not standing in the wall, and not on its
   * far side — which is the whole rule, so a dash, a blink, a star or a phase through terrain
   * can't do what a step can't. Someone the wall came up on top of may step off it either way.
   */
  def allowsMove(fromX: Int, fromY: Int, toX: Int, toY: Int): Boolean = {
    val to = side(toX, toY)
    if (to == 0) return false
    val from = side(fromX, fromY)
    from == 0 || from == to
  }

  /** Does a raised wall stop a player at (fromX, fromY) from ending up at (toX, toY)? The clock
    * is read once, here, rather than by each part of [[allowsMove]]. */
  def stops(fromX: Int, fromY: Int, toX: Int, toY: Int): Boolean =
    up && !allowsMove(fromX, fromY, toX, toY)

  /**
   * Where the path from (ax, ay) to (bx, by) meets the wall: the fraction of the way along it, in
   * [0, 1], or -1 if the path stays on one side. Both directions meet it — unlike a held barrier,
   * this one has no front and no back.
   */
  def crossing(ax: Float, ay: Float, bx: Float, by: Float): Float = {
    val a = across(ax, ay)
    val b = across(bx, by)
    if ((a < 0f && b < 0f) || (a > 0f && b > 0f) || a == b) return -1f
    val t = a / (a - b)
    if (t < 0f || t > 1f) -1f else t
  }

  /** Does the path from (ax, ay) to (bx, by) cross the wall? */
  def crosses(ax: Float, ay: Float, bx: Float, by: Float): Boolean =
    crossing(ax, ay, bx, by) >= 0f
}
