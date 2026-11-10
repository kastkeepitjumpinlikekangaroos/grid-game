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

  @Test def aDiagonalStepGoesBetweenOpenCellsOnly(): Unit = {
    val w = WorldData.createEmpty(20, 20)
    assertTrue("in the open", w.canStep(10, 10, 1, 1))
    w.setTile(11, 10, Tile.Wall)
    assertFalse("past a wall's corner", w.canStep(10, 10, 1, 1))
    assertFalse("the same corner from the other side", w.canStep(11, 11, -1, -1))
    assertTrue("a straight step beside it", w.canStep(10, 10, 0, 1))
    assertTrue("a diagonal the other way", w.canStep(10, 10, -1, 1))
    w.setTile(10, 11, Tile.Rock)
    assertFalse("between two blocks touching at a corner", w.canStep(11, 11, -1, -1))
    assertFalse("onto a wall", w.canStep(10, 10, 1, 0))
    assertTrue("a straight step passes no corner", w.cornersOpen(10, 10, 1, 0))
  }

  @Test def spawnsAreKeptApartWhenTheMapAllows(): Unit = {
    val w = new WorldData("w", 120, 120, Array.fill(120, 120)(Tile.Grass: Tile),
      Seq(new Position(10, 10), new Position(12, 10), new Position(100, 100)))
    for (_ <- 0 until 50) {
      val sp = w.getValidSpawnPoint(Set((10, 10)))
      assertEquals("the only spawn point far enough from (10, 10)", new Position(100, 100), sp)
    }
  }

  private def openWorld(w: Int, h: Int, spawns: Position*): WorldData =
    new WorldData("w", w, h, Array.fill(h, w)(Tile.Grass: Tile), spawns)

  @Test def aSpawnPointFiftyCellsFromEveryoneIsFarEnoughAndOneNearerIsNot(): Unit = {
    // As the crow flies, from each of them: fifty exactly will do
    val taken = Set((100, 100))
    for ((x, y) <- Seq((150, 100), (50, 100), (100, 150), (100, 50), (130, 140), (70, 60), (60, 130))) {
      val w = openWorld(200, 200, new Position(x, y))
      for (_ <- 0 until 5) assertEquals(s"($x, $y), fifty from (100, 100)", new Position(x, y), w.getValidSpawnPoint(taken))
    }
    for ((x, y) <- Seq((149, 100), (51, 100), (100, 149), (100, 51), (130, 139), (135, 135), (100, 100), (101, 100))) {
      val w = openWorld(200, 200, new Position(x, y))
      for (_ <- 0 until 5) {
        val sp = w.getValidSpawnPoint(taken)
        assertNotEquals(s"($x, $y), nearer than fifty", new Position(x, y), sp)
        assertTrue(s"somewhere fifty off instead: $sp", Math.hypot(sp.getX - 100, sp.getY - 100) >= 50)
      }
    }
    // From every one of them, not from the nearest alone or the first
    val w = openWorld(200, 200, new Position(100, 100))
    for (_ <- 0 until 20) {
      val sp = w.getValidSpawnPoint(Set((20, 20), (180, 180), (100, 110)))
      assertTrue(s"fifty from all three: $sp", Seq((20, 20), (180, 180), (100, 110)).forall { case (ox, oy) =>
        Math.hypot(sp.getX - ox, sp.getY - oy) >= 50 })
    }
  }

  @Test def onAMapTooSmallForFiftyASpawnIsAsFarAsTheMapAllows(): Unit = {
    // Half of it, then a quarter, then five cells, then one: the furthest of those there is room for
    def nearest(side: Int, taken: Set[(Int, Int)]): Double = (0 until 30).map { _ =>
      val sp = openWorld(side, side).getValidSpawnPoint(taken)
      taken.map { case (ox, oy) => Math.hypot(sp.getX - ox, sp.getY - oy) }.min
    }.min
    // (Its corners and its middle taken, nowhere on 60x60 is fifty from them all)
    assertTrue("room for twenty-five", nearest(60, Set((0, 0), (59, 0), (0, 59), (59, 59), (30, 30))) >= 25)
    assertTrue("for twelve", nearest(24, Set((0, 0), (23, 23))) >= 12)
    assertTrue("for five", nearest(10, Set((0, 0), (9, 9))) >= 5)
    assertTrue("for one", nearest(3, Set((0, 0), (2, 2), (0, 2), (2, 0))) >= 1)
  }

  @Test def aHalfWithNowhereToStandFallsBackToTheWholeMapAndAMapWithNowhereToItsMiddle(): Unit = {
    val w = WorldData.createEmpty(20, 20)
    for (x <- 0 until 10; y <- 0 until 20) w.setTile(x, y, Tile.Wall)
    for (_ <- 0 until 10) {
      val sp = w.getValidSpawnPoint(Set.empty, (x, _) => x < 10)
      assertTrue(s"asked for the walled half: $sp", w.isWalkable(sp) && sp.getX >= 10)
    }
    val nowhere = WorldData.createEmpty(20, 30)
    for (x <- 0 until 20; y <- 0 until 30) nowhere.setTile(x, y, Tile.Wall)
    assertEquals(new Position(10, 15), nowhere.getValidSpawnPoint(Set.empty))
    assertEquals(new Position(10, 15), nowhere.getValidSpawnPoint(Set.empty, (x, _) => x < 10))
  }

  @Test def aTileIsSetOnTheMapAndNowhereElse(): Unit = {
    val w = WorldData.createEmpty(6, 4)
    for ((x, y) <- Seq((0, 0), (5, 0), (0, 3), (5, 3), (2, 1))) {
      assertTrue(s"($x, $y)", w.setTile(x, y, Tile.Water))
      assertEquals(Tile.Water, w.getTile(x, y))
    }
    for ((x, y) <- Seq((-1, 0), (6, 0), (0, -1), (0, 4), (6, 4), (-1, -1)))
      assertFalse(s"($x, $y) is off it", w.setTile(x, y, Tile.Water))
    assertEquals("five cells changed and no other", 5, (for (x <- 0 until 6; y <- 0 until 4 if w.getTile(x, y) == Tile.Water) yield 1).size)
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
