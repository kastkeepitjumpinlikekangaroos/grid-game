package com.gridgame.common.world

import com.google.gson.JsonParser
import com.gridgame.common.WorldRegistry
import com.gridgame.common.model.{Position, Tile, WorldData}
import org.junit.Assert._
import org.junit.Test

import java.io.{File, FileReader}
import java.nio.file.Files

/**
 * A map's JSON read into a world, layer type by layer type. Every map in worlds/ is one `grid`
 * layer (what the map editor saves), so the other six — fill, rect, border, circle, line, points,
 * which a map written by hand is made of — were read by no map and no test.
 */
class WorldLoaderTest {
  private val G = Tile.Grass
  private val W = Tile.Wall
  private val A = Tile.Water

  /** A world of this size from these layers (JSON objects), read as a map file is. */
  private def world(width: Int, height: Int, layers: String*)(rest: String = ""): WorldData = {
    val json = s"""{"name": "test", "width": $width, "height": $height, "layers": [${layers.mkString(", ")}]$rest}"""
    val file = File.createTempFile("world", ".json")
    try {
      Files.write(file.toPath, json.getBytes("UTF-8"))
      WorldLoader.loadFromFile(file.getAbsolutePath)
    } finally file.delete()
  }

  /** The cells of the world that are `tile`. */
  private def cells(w: WorldData, tile: Tile): Set[(Int, Int)] =
    (for (y <- 0 until w.height; x <- 0 until w.width if w.getTile(x, y) == tile) yield (x, y)).toSet

  private def layer(kind: String, tile: Tile, rest: String = ""): String =
    s"""{"type": "$kind", "tile": "${tile.name}"${if (rest.isEmpty) "" else ", " + rest}}"""

  @Test def aWorldWithNoLayersIsGrass(): Unit = {
    val w = world(6, 4)()
    assertEquals((6, 4), (w.width, w.height))
    assertEquals(24, cells(w, G).size)
    assertEquals("test", w.name)
    assertEquals("a map that names no background has the sky", "sky", w.background)
  }

  @Test def aFillCoversTheWholeMap(): Unit = {
    val w = world(5, 3, layer("fill", A))()
    assertEquals(15, cells(w, A).size)
  }

  @Test def aRectCoversExactlyItsCells(): Unit = {
    val w = world(8, 8, layer("rect", W, """"x": 2, "y": 3, "width": 3, "height": 2"""))()
    assertEquals((for (x <- 2 to 4; y <- 3 to 4) yield (x, y)).toSet, cells(w, W))
  }

  @Test def aRectIsCutOffAtTheMapsEdges(): Unit = {
    // Hanging off the far corner, and off the near one: only the part on the map is drawn
    val far = world(6, 6, layer("rect", W, """"x": 4, "y": 4, "width": 10, "height": 10"""))()
    assertEquals(Set((4, 4), (5, 4), (4, 5), (5, 5)), cells(far, W))
    val near = world(6, 6, layer("rect", W, """"x": -3, "y": -2, "width": 5, "height": 4"""))()
    assertEquals(Set((0, 0), (1, 0), (0, 1), (1, 1)), cells(near, W))
  }

  @Test def aBorderRunsRoundTheEdgeAsThickAsItSays(): Unit = {
    val one = world(6, 5, layer("border", W))()
    assertEquals("a border is one cell thick unless it says",
      (for (x <- 0 until 6; y <- 0 until 5 if x == 0 || y == 0 || x == 5 || y == 4) yield (x, y)).toSet, cells(one, W))
    val two = world(7, 7, layer("border", W, """"thickness": 2"""))()
    assertEquals((for (x <- 0 until 7; y <- 0 until 7 if x < 2 || y < 2 || x > 4 || y > 4) yield (x, y)).toSet, cells(two, W))
    // Thicker than the map is all of the map, and no error
    val all = world(4, 3, layer("border", W, """"thickness": 9"""))()
    assertEquals(12, cells(all, W).size)
  }

  @Test def aCircleCoversTheCellsWithinItsRadius(): Unit = {
    val w = world(11, 11, layer("circle", A, """"cx": 5, "cy": 5, "radius": 3"""))()
    val expected = (for (x <- 0 until 11; y <- 0 until 11 if (x - 5) * (x - 5) + (y - 5) * (y - 5) <= 9) yield (x, y)).toSet
    assertEquals(expected, cells(w, A))
    assertTrue("its edge on an axis is in it", expected.contains((8, 5)) && expected.contains((5, 2)))
    assertFalse("its bounding square's corner is not", expected.contains((8, 8)))
    // One hanging off the corner of the map is cut off there
    val corner = world(6, 6, layer("circle", A, """"cx": 0, "cy": 0, "radius": 2"""))()
    assertEquals(Set((0, 0), (1, 0), (2, 0), (0, 1), (1, 1), (0, 2)), cells(corner, A))
  }

  @Test def aLineJoinsItsEndsWhicheverWayItRuns(): Unit = {
    val ends = Seq(
      ((1, 1), (8, 1)), ((8, 1), (1, 1)), // along a row, both ways
      ((2, 0), (2, 9)), ((2, 9), (2, 0)), // along a column
      ((0, 0), (9, 9)), ((9, 0), (0, 9)), // both diagonals
      ((1, 2), (9, 5)), ((9, 5), (1, 2)), ((3, 9), (5, 0)), // shallow and steep
      ((4, 4), (4, 4))) // a point
    for (((x1, y1), (x2, y2)) <- ends) {
      val w = world(10, 10, layer("line", W, s""""x1": $x1, "y1": $y1, "x2": $x2, "y2": $y2"""))()
      val drawn = cells(w, W)
      val what = s"the line ($x1,$y1) to ($x2,$y2)"
      assertTrue(s"$what has both its ends", drawn.contains((x1, y1)) && drawn.contains((x2, y2)))
      // One cell a step of its longer side: no gaps, and none doubled up
      assertEquals(s"$what is a cell wide", Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)) + 1, drawn.size)
      for ((x, y) <- drawn) {
        assertTrue(s"$what stays between its ends", x >= Math.min(x1, x2) && x <= Math.max(x1, x2) &&
          y >= Math.min(y1, y2) && y <= Math.max(y1, y2))
        val joined = drawn.size == 1 || drawn.exists { case (ox, oy) => (ox, oy) != ((x, y)) && Math.abs(ox - x) <= 1 && Math.abs(oy - y) <= 1 }
        assertTrue(s"$what is unbroken at ($x,$y)", joined)
      }
    }
  }

  @Test def aThickLineIsThatWideAndCutOffAtTheEdge(): Unit = {
    val w = world(8, 8, layer("line", A, """"x1": 0, "y1": 3, "x2": 7, "y2": 3, "thickness": 3"""))()
    assertEquals((for (x <- 0 until 8; y <- 2 to 4) yield (x, y)).toSet, cells(w, A))
    // Along the top edge, half of it is off the map
    val top = world(8, 8, layer("line", A, """"x1": 0, "y1": 0, "x2": 7, "y2": 0, "thickness": 3"""))()
    assertEquals((for (x <- 0 until 8; y <- 0 to 1) yield (x, y)).toSet, cells(top, A))
  }

  @Test def pointsArePutWhereTheySayAndNotOffTheMap(): Unit = {
    val w = world(5, 5, layer("points", W,
      """"points": [{"x": 0, "y": 0}, {"x": 4, "y": 4}, {"x": 2, "y": 3}, {"x": 5, "y": 1}, {"x": -1, "y": 2}, {"x": 1, "y": 99}]"""))()
    assertEquals(Set((0, 0), (4, 4), (2, 3)), cells(w, W))
  }

  @Test def aGridIsReadRowByRowAsTileIds(): Unit = {
    val w = world(3, 2, s"""{"type": "grid", "data": ["${W.id} ${G.id} ${A.id}", "${A.id} ${W.id} ${G.id}"]}""")()
    assertEquals(Seq(W, G, A), (0 until 3).map(w.getTile(_, 0)))
    assertEquals(Seq(A, W, G), (0 until 3).map(w.getTile(_, 1)))
  }

  @Test def aGridShorterOrLongerThanTheMapIsReadAsFarAsEitherGoes(): Unit = {
    // Rows and cells past the map's size are left out; what a short grid doesn't reach stays grass
    val w = world(3, 3, s"""{"type": "grid", "data": ["${W.id} ${W.id} ${W.id} ${W.id} ${W.id}", "${W.id}"]}""")()
    assertEquals(Set((0, 0), (1, 0), (2, 0), (0, 1)), cells(w, W))
    val long = world(2, 1, s"""{"type": "grid", "data": ["${A.id} ${A.id}", "${A.id} ${A.id}", "${A.id} ${A.id}"]}""")()
    assertEquals(2, cells(long, A).size)
  }

  @Test def layersAreLaidDownInOrder(): Unit = {
    val w = world(6, 6, layer("fill", A), layer("rect", G, """"x": 1, "y": 1, "width": 4, "height": 4"""),
      layer("points", W, """"points": [{"x": 2, "y": 2}]"""))()
    assertEquals(A, w.getTile(0, 0))
    assertEquals(G, w.getTile(1, 1))
    assertEquals("the last layer to cover a cell is the one it keeps", W, w.getTile(2, 2))
    // And the other way round, the fill wipes out everything under it
    val wiped = world(6, 6, layer("points", W, """"points": [{"x": 2, "y": 2}]"""), layer("fill", A))()
    assertEquals(36, cells(wiped, A).size)
  }

  @Test def aLayerOfAKindNobodyKnowsChangesNothing(): Unit = {
    val w = world(4, 4, layer("spiral", W, """"turns": 3"""))()
    assertEquals(16, cells(w, G).size)
  }

  @Test def spawnPointsOffTheMapAreLeftOut(): Unit = {
    val w = world(10, 8)(""", "spawnPoints": [{"x": 2, "y": 3}, {"x": 10, "y": 3}, {"x": 4, "y": -1}, {"x": 9, "y": 7}]""")
    assertEquals(Seq(new Position(2, 3), new Position(9, 7)), w.spawnPoints)
  }

  @Test def aMapWithNoSpawnPointsOfItsOwnSpawnsInItsMiddle(): Unit = {
    assertEquals(Seq(new Position(5, 4)), world(10, 8)().spawnPoints)
    assertEquals("and one whose every point is off it",
      Seq(new Position(5, 4)), world(10, 8)(""", "spawnPoints": [{"x": 40, "y": 40}]""").spawnPoints)
  }

  @Test def aMapNamesItsBackground(): Unit =
    assertEquals("space", world(4, 4)(""", "background": "space"""").background)

  @Test def aMapThatIsNotThereIsAnErrorNotAnEmptyWorld(): Unit = {
    for (load <- Seq[() => WorldData](() => WorldLoader.loadFromFile("/no/such/map.json"), () => WorldLoader.load("worlds/no_such_map.json")))
      try { load(); fail("a missing map loaded") } catch { case _: IllegalArgumentException => }
  }

  /**
   * An id the tileset doesn't have reads as grass (Tile.fromId), so a map saved against a tile that
   * has since gone, or with a slip of the hand in it, would load with grass where its walls were,
   * and say nothing. Every cell of every map a lobby can pick is held to the tiles there are.
   */
  @Test def everyCellOfEveryMapIsATileTheGameHas(): Unit = {
    val known = Tile.all.map(_.id).toSet
    for (i <- 0 until WorldRegistry.size) {
      val name = WorldRegistry.getFilename(i)
      val reader = new FileReader("worlds/" + name)
      val root = try JsonParser.parseReader(reader).getAsJsonObject finally reader.close()
      val (width, height) = (root.get("width").getAsInt, root.get("height").getAsInt)
      val layers = root.getAsJsonArray("layers")
      for (l <- 0 until layers.size()) {
        val layer = layers.get(l).getAsJsonObject
        if (layer.get("type").getAsString == "grid") {
          val data = layer.getAsJsonArray("data")
          assertEquals(s"$name has a row for each of its $height", height, data.size())
          for (y <- 0 until data.size()) {
            val ids = data.get(y).getAsString.split("\\s+")
            assertEquals(s"$name's row $y has a cell for each of its $width columns", width, ids.length)
            for (id <- ids) assertTrue(s"$name's row $y has tile $id, which the game has no tile for", known.contains(id.toInt))
          }
        } else {
          assertTrue(s"$name's ${layer.get("type").getAsString} layer names a tile the game has",
            Tile.all.exists(_.name == layer.get("tile").getAsString))
        }
      }
    }
  }
}
