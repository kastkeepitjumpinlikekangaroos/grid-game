package com.gridgame.common.model

import org.junit.Assert._
import org.junit.Test

/** Terrain queries and where players are put down. */
class WorldDataTest {
  @Test def offTheMapIsWall(): Unit = {
    val w = WorldData.createEmpty(10, 10)
    assertEquals(Tile.Wall, w.getTile(-1, 0))
    assertEquals(Tile.Wall, w.getTile(10, 5))
    assertFalse(w.isWalkable(5, -1))
    assertFalse("setTile off the map changes nothing", w.setTile(10, 10, Tile.Grass))
  }

  @Test def spawnsAreKeptApartWhenTheMapAllows(): Unit = {
    val w = new WorldData("w", 120, 120, Array.fill(120, 120)(Tile.Grass: Tile),
      Seq(new Position(10, 10), new Position(12, 10), new Position(100, 100)))
    for (_ <- 0 until 50) {
      val sp = w.getValidSpawnPoint(Set((10, 10)))
      assertEquals("the only spawn point far enough from (10, 10)", new Position(100, 100), sp)
    }
  }

  @Test def aSpawnIsAlwaysOpenGround(): Unit = {
    val tiles = Array.fill(20, 20)(Tile.Wall: Tile)
    tiles(7)(3) = Tile.Grass
    val w = new WorldData("w", 20, 20, tiles, Seq(new Position(0, 0))) // the spawn point is in a wall
    for (_ <- 0 until 20) assertEquals(new Position(3, 7), w.getValidSpawnPoint())
  }

  @Test def aCrowdedSmallMapStillFindsSpawns(): Unit = {
    val w = WorldData.createEmpty(8, 8)
    val occupied = (for (x <- 0 until 8; y <- 0 until 8 if (x + y) % 3 != 0) yield (x, y)).toSet
    val sp = w.getValidSpawnPoint(occupied)
    assertTrue(w.isWalkable(sp))
    assertFalse("not on someone", occupied.contains((sp.getX, sp.getY)))
  }

  @Test def tilesLookUpByIdAndName(): Unit = {
    Tile.all.foreach { t =>
      assertEquals(t, Tile.fromId(t.id))
      assertEquals(t, Tile.fromName(t.name))
    }
    assertEquals(42, Tile.all.map(_.id).distinct.size)
    assertFalse(Tile.Fence.walkable)
  }

  @Test def directionsFollowMovement(): Unit = {
    assertEquals(Direction.Up, Direction.fromMovement(0, -1))
    assertEquals(Direction.Down, Direction.fromMovement(0, 1))
    assertEquals(Direction.Left, Direction.fromMovement(-1, 0))
    assertEquals(Direction.Right, Direction.fromMovement(1, 0))
    Seq(Direction.Down, Direction.Up, Direction.Left, Direction.Right).foreach(d => assertEquals(d, Direction.fromId(d.id)))
  }

  @Test def itemTypesLookUpById(): Unit = {
    ItemType.all.foreach(t => assertEquals(t, ItemType.fromId(t.id)))
    assertEquals(ItemType.all.toSet, ItemType.spawnable.toSet)
  }
}
