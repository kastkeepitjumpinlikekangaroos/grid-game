package com.gridgame.common.world

import com.gridgame.common.Constants
import com.gridgame.common.WorldRegistry
import com.gridgame.common.model.{TeamDivider, WorldData}
import org.junit.Assert._
import org.junit.Test

import scala.collection.mutable

/**
 * The maps a lobby can pick. The server spawns players on a map's spawn points and the client
 * draws it from the same file, so a spawn point in a wall, or ground no one can walk to from
 * the rest, is a player who can't play.
 */
class WorldMapsTest {
  private val worlds: Seq[(String, WorldData)] =
    (0 until WorldRegistry.size).map { i =>
      val f = WorldRegistry.getFilename(i)
      f -> WorldLoader.load("worlds/" + f)
    }

  @Test def everyRegisteredMapLoads(): Unit = {
    assertTrue(worlds.nonEmpty)
    for ((f, w) <- worlds) {
      assertTrue(f, w.width > 0 && w.height > 0)
      assertTrue(s"$f fits the grid positions can address", w.width <= Constants.GRID_SIZE && w.height <= Constants.GRID_SIZE)
      assertTrue(f, w.name.nonEmpty)
    }
  }

  @Test def everySpawnPointIsOpenGround(): Unit = {
    for ((f, w) <- worlds) {
      assertTrue(s"$f has spawn points", w.spawnPoints.nonEmpty)
      for (sp <- w.spawnPoints) assertTrue(s"$f $sp", w.isWalkable(sp))
    }
  }

  @Test def allOpenGroundIsOneRegion(): Unit = {
    // A walkable pocket nobody can reach is somewhere a spawn, a respawn or an item can strand you
    for ((f, w) <- worlds) {
      val start = w.spawnPoints.head
      val seen = mutable.HashSet[(Int, Int)]((start.getX, start.getY))
      val queue = mutable.Queue((start.getX, start.getY))
      while (queue.nonEmpty) {
        val (x, y) = queue.dequeue()
        for ((nx, ny) <- Seq((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1))
             if w.isWalkable(nx, ny) && seen.add((nx, ny))) queue.enqueue((nx, ny))
      }
      val walkable = (for (y <- 0 until w.height; x <- 0 until w.width if w.isWalkable(x, y)) yield 1).sum
      assertEquals(s"$f: open cells reachable from the first spawn", walkable, seen.size)
    }
  }

  @Test def bothTeamsHaveSpawnPointsInTheirOwnHalf(): Unit = {
    // A Teams match spawns each team in its own half of the map (TeamDivider)
    for ((f, w) <- worlds) {
      val sides = w.spawnPoints.map(p => TeamDivider.sideOf(w, p.getX, p.getY))
      assertTrue(s"$f: team 1's half", sides.contains(-1))
      assertTrue(s"$f: team 2's half", sides.contains(1))
    }
  }

  @Test def theGeneratedMapsAreMirrorImagesAcrossTheDivider(): Unit = {
    // scripts/generate_maps.py builds these symmetric about the divider's line, so a Teams match
    // on one is the same match from either side. (The older maps predate it: they are centred
    // half a cell off the line.)
    for ((f, w) <- worlds if Set("the_meadow.json", "the_lagoon.json", "the_snowglobe.json")(f)) {
      val line = TeamDivider.line(w)
      for (y <- 0 until w.height; x <- 0 until w.width if 2 * line - x < w.width)
        assertSame(s"$f ($x,$y)", w.getTile(x, y), w.getTile(2 * line - x, y))
      val sides = w.spawnPoints.map(p => TeamDivider.sideOf(w, p.getX, p.getY))
      assertEquals(s"$f: as many spawn points each side", sides.count(_ < 0), sides.count(_ > 0))
      assertFalse(s"$f: none on the line", sides.contains(0))
    }
  }

  @Test def displayNamesComeFromFileNames(): Unit = {
    assertEquals("The Cell", WorldRegistry.getDisplayName(0))
    assertEquals(WorldRegistry.getFilename(0), WorldRegistry.getFilename(-1))
    assertEquals(WorldRegistry.getFilename(0), WorldRegistry.getFilename(WorldRegistry.size))
    for (i <- 0 until WorldRegistry.size) assertEquals(i, WorldRegistry.getIndex(WorldRegistry.getFilename(i)))
  }
}
