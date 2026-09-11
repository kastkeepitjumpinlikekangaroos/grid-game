package com.gridgame.client.render

import com.gridgame.common.model.{Projectile, Tile, WorldData}

/** A projectile that has been stopped, kept on screen for a moment where it stopped so it
 *  can be seen to be absorbed rather than blinking out. `hitTerrain` is false when it simply
 *  ran out of range; `tileColor` is the ARGB colour of what it struck (0 at the map edge). */
final class FadingProjectile(val proj: Projectile, val startMs: Long, val hitTerrain: Boolean,
                             val tileColor: Int)

/**
 * Where a despawned projectile actually met the terrain.
 *
 * The server removes a projectile on the first movement sub-step that lands in a blocking
 * cell (a non-walkable tile, a fence for wall-passers, or off the map), so the position in
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

  private def blocks(world: WorldData, cx: Int, cy: Int, passesWalls: Boolean): Boolean =
    cx < 0 || cy < 0 || cx >= world.width || cy >= world.height || {
      val t = world.getTile(cx, cy)
      !t.walkable && (!passesWalls || t == Tile.Fence)
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
