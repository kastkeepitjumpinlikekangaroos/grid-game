package com.gridgame.common.model

import org.junit.Test
import org.junit.Assert._

import java.util.UUID

/**
 * Terrain collision has to agree with where tiles are drawn. The renderer draws tile (c, r)
 * centred on world (c, r), so it covers [c - 0.5, c + 0.5). Collision used to truncate
 * (x.toInt), which put every wall half a tile down-screen of its sprite: shots heading
 * toward the camera sank halfway into wall blocks before stopping, shots heading away
 * stopped short, and projectiles flew half a tile off the bottom map edges.
 */
class ProjectileTerrainTest {

  private def proj(x: Float, y: Float, dx: Float, dy: Float, pType: Byte = ProjectileType.NORMAL) =
    new Projectile(1, UUID.randomUUID(), x, y, dx, dy, 0xFFFFFF, 0, pType)

  private def worldWithWall(wx: Int, wy: Int): WorldData = {
    val w = WorldData.createEmpty(8, 8)
    w.setTile(wx, wy, Tile.Wall)
    w
  }

  @Test
  def stopsAtTheDrawnFaceHeadingTowardTheCamera(): Unit = {
    // Heading +x into a wall at (4, 3), whose drawn face is at x = 3.5
    val world = worldWithWall(4, 3)
    assertFalse(proj(3.45f, 3f, 1f, 0f).hitsNonWalkable(world))
    assertTrue(proj(3.55f, 3f, 1f, 0f).hitsNonWalkable(world))
  }

  @Test
  def reachesTheDrawnFaceHeadingAwayFromTheCamera(): Unit = {
    // Heading -x into a wall at (2, 3), whose drawn face is at x = 2.5
    val world = worldWithWall(2, 3)
    assertFalse(proj(2.55f, 3f, -1f, 0f).hitsNonWalkable(world))
    assertTrue(proj(2.45f, 3f, -1f, 0f).hitsNonWalkable(world))
  }

  @Test
  def mapEdgesAreWhereTheOuterTilesEnd(): Unit = {
    val world = WorldData.createEmpty(8, 8)
    assertFalse(proj(7.45f, 3f, 1f, 0f).isOutOfBounds(world))
    assertTrue(proj(7.55f, 3f, 1f, 0f).isOutOfBounds(world))
    assertFalse(proj(3f, -0.45f, 0f, -1f).isOutOfBounds(world))
    assertTrue(proj(3f, -0.55f, 0f, -1f).isOutOfBounds(world))
  }

  @Test
  def ricochetLeavesTheShardOnTheWalkableSideOfTheFace(): Unit = {
    CharacterDef.all // registers every ProjectileDef, including the shard's bounce count
    val world = worldWithWall(4, 3)
    val p = proj(3.6f, 3f, 1f, 0f, ProjectileType.RICOCHET_SHARD)
    assertTrue(p.hitsNonWalkable(world))
    assertTrue(p.ricochet(world))
    assertFalse("snapped back out of the wall", p.hitsNonWalkable(world))
    assertEquals(3.49f, p.getX, 1e-4f)
  }
}
