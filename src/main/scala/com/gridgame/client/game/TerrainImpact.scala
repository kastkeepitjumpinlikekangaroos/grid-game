package com.gridgame.client.game

import com.gridgame.common.model.{Projectile, ProjectileDef, PullToOwner, Tile, WorldData}

import java.util.UUID

/** A projectile that has been stopped, kept on screen for a moment where it stopped so it
 *  can be seen to be absorbed rather than blinking out. `hitTerrain` is false when it simply
 *  ran out of range; `tileColor` is the ARGB colour of what it struck (0 at the map edge).
 *
 *  A pull (a hook, a tongue, a tentacle) is reeled back in to its thrower instead: empty-handed
 *  when it missed, and holding on to the player it `caught` while it drags them in. It stays
 *  `lifeMs`. */
final class FadingProjectile(val proj: Projectile, val startMs: Long, val hitTerrain: Boolean,
                             val tileColor: Int, val caught: UUID = null,
                             val lifeMs: Long = TerrainImpact.FADE_MS)

object FadingProjectile {
  /** How long a pull that missed takes to be reeled back in to its thrower: no longer than a stopped
    * shot is kept (TerrainImpact.FADE_MS), so a miss needs no lifetime of its own. */
  val RETRACT_MS = 240L
  /** How long a pull that caught someone holds on to them: the server puts them on the thrower's
    * cell at once and every client slides them there (KnockbackSlide, under 300ms), its head on
    * them all the way. Then whatever is left of it is reeled in, as a miss's is. */
  val HOLD_MS = 340L

  /** Does a projectile of this type pull what it hits to its thrower? */
  def isPull(pType: Byte): Boolean = ProjectileDef.get(pType).onHitEffect.contains(PullToOwner)

  /** One that stopped, `hitTerrain` or at the end of its range: a shot sinks into what stopped it, and
    * a pull tied to its thrower is reeled back in, in less time than that (RETRACT_MS). */
  def stopped(proj: Projectile, now: Long, hitTerrain: Boolean, tileColor: Int): FadingProjectile =
    new FadingProjectile(proj, now, hitTerrain, tileColor)

  /** A pull that caught `target`: held on to while they are dragged in, then reeled back. */
  def caught(proj: Projectile, now: Long, target: UUID): FadingProjectile =
    new FadingProjectile(proj, now, false, 0, target, HOLD_MS + RETRACT_MS)
}

/**
 * Where a despawned projectile actually met the terrain.
 *
 * The server removes a projectile on the first movement sub-step that lands in a blocking
 * cell (terrain that stops it, a fence for wall-passers, or off the map), so the position in
 * the DESPAWN packet can sit up to half a cell INSIDE the wall. Drawing the stop there puts
 * the projectile in the wall's own draw slot, over the block's face. Walking back along the
 * heading to the last clear point puts it on the face instead.
 */
object TerrainImpact {
  /** How long a stopped projectile takes to sink into the surface and fade out. */
  val FADE_MS = 260L

  final case class Impact(x: Float, y: Float, hitTerrain: Boolean, tileColor: Int)

  /** The cell a world point falls in: tiles are drawn centred on integer coordinates.
   *  Matches Projectile.getCellX, which the server's collision uses. */
  @inline def cellOf(v: Float): Int = Math.floor(v + 0.5f).toInt

  /** Does the cell stop a projectile? Terrain it can't cross (not water: Tile.stopsProjectiles),
    * the map's edge, and for one that flies over walls a fence — what the server's collision
    * stops it on. The aim lane stops where this says too (AimArrowPainter). */
  private[client] def blocks(world: WorldData, cx: Int, cy: Int, passesWalls: Boolean): Boolean =
    cx < 0 || cy < 0 || cx >= world.width || cy >= world.height || {
      val t = world.getTile(cx, cy)
      t.stopsProjectiles && (!passesWalls || t == Tile.Fence)
    }

  def resolve(world: WorldData, x: Float, y: Float, dx: Float, dy: Float, passesWalls: Boolean): Impact = {
    if (world == null) return Impact(x, y, hitTerrain = false, 0)
    val cx = cellOf(x); val cy = cellOf(y)
    if (!blocks(world, cx, cy, passesWalls)) return Impact(x, y, hitTerrain = false, 0)
    val inside = cx >= 0 && cy >= 0 && cx < world.width && cy < world.height
    val tileColor = if (inside) world.getTile(cx, cy).color else 0
    val len = Math.sqrt(dx * dx + dy * dy).toFloat
    if (len < 1e-4f) return Impact(x, y, hitTerrain = true, tileColor)
    val ux = dx / len; val uy = dy / len
    // Back out in 1/20-cell steps; a sub-step never exceeds half a cell, so 2 cells is ample
    var px = x; var py = y
    var n = 0
    while (n < 40 && blocks(world, cellOf(px), cellOf(py), passesWalls)) {
      px -= ux * 0.05f; py -= uy * 0.05f
      n += 1
    }
    Impact(px, py, hitTerrain = true, tileColor)
  }
}
