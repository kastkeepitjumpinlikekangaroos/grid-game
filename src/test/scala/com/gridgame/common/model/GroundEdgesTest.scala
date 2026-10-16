package com.gridgame.common.model

import com.gridgame.common.Constants
import org.junit.Assert._
import org.junit.Test

import java.io.File
import javax.imageio.ImageIO

/**
 * Where two kinds of ground meet (GroundEdges): which ground spills over which, that what spills is
 * the colour its own tiles are painted, and that the edge is one edge — the same drawn from either
 * cell it passes between, mirrored with the ground, never over the middle of a cell, standing on a
 * riser only where it faces the camera.
 */
class GroundEdgesTest {
  import GroundEdges._

  private val legend = Map[Char, Tile]('.' -> Tile.Grass, 'f' -> Tile.Flowers, 'p' -> Tile.Path, 's' -> Tile.Sand,
    'n' -> Tile.Snow, 'i' -> Tile.Ice, 'w' -> Tile.Water, 'W' -> Tile.DeepWater, 'd' -> Tile.Dirt,
    'k' -> Tile.Planks, '#' -> Tile.Wall, 'T' -> Tile.Tree, 'c' -> Tile.Circuit, 'v' -> Tile.Void)

  private def world(rows: String*): WorldData = {
    val tiles: Array[Array[Tile]] = rows.map(row => row.map(legend).toArray[Tile]).toArray
    new WorldData("test", rows.head.length, rows.length, tiles, Seq(new Position(0, 0)))
  }

  private val layered = Seq(Tile.Void, Tile.Circuit, Tile.DeepWater, Tile.Water, Tile.Ice, Tile.Dirt, Tile.Path,
    Tile.Sand, Tile.Snow, Tile.Grass)

  // What a cell shows at a point: its edge over its own ground, which is its layer's colour wherever
  // an edge can reach (its tile's details keep to the middle)
  private val px = 3.2 // pixels to a display unit, as on a Retina screen
  private def composite(layers: Array[Byte], w: Int, h: Int, cx: Int, cy: Int, x: Double, y: Double): (Double, Double, Double) = {
    val e = new Array[Double](4)
    shade(layers, w, h, cx, cy, x, y, px, e)
    val own = base(layers(cy * w + cx) & 0xFF)
    def ch(i: Int, s: Int) = e(i) + ((own >> s) & 0xFF) / 255.0 * (1 - e(3))
    (ch(0, 16), ch(1, 8), ch(2, 0))
  }

  private def rgb(c: Int) = (((c >> 16) & 0xFF) / 255.0, ((c >> 8) & 0xFF) / 255.0, (c & 0xFF) / 255.0)

  private def close(a: (Double, Double, Double), b: (Double, Double, Double), tol: Double): Boolean =
    Math.abs(a._1 - b._1) <= tol && Math.abs(a._2 - b._2) <= tol && Math.abs(a._3 - b._3) <= tol

  @Test def groundIsLayeredAndNothingBuiltInPiecesTakesPart(): Unit = {
    // low to high: what spills over what
    assertEquals((1 to layered.size).toList, layered.map(layerOf).toList)
    assertEquals("the flowers are grass", layerOf(Tile.Grass), layerOf(Tile.Flowers))
    assertTrue("the shader packs a layer into four bits", Count <= 16)
    for (t <- Seq(Tile.Planks, Tile.Stone, Tile.Cobblestone, Tile.Metal, Tile.Glass, Tile.Lava, Tile.Wall,
                  Tile.Cliff, Tile.Tree, Tile.Fence, Tile.Obsidian, Tile.Toxic))
      assertEquals(t.name, 0, layerOf(t))
    assertEquals("one colour and one look a layer", Count, base.length)
    for (a <- Seq(ink, riser, riserKind, riserInk, shore)) assertEquals(Count, a.length)
    assertEquals(Count, shoreWidth.length); assertEquals(Count, shoreSoft.length)
    for (k <- 1 until Count) {
      assertEquals(s"layer $k's riser has a face and a height, or neither", riser(k) != 0, riserKind(k) != 0)
      assertEquals(s"layer $k's shore has a colour and a width, or neither", shore(k) != 0, shoreWidth(k) > 0)
    }
    // a riser reads the ground its height above each pixel, which has to stay in the nine cells round it
    for (h <- RiserHeights) assertTrue(s"riser $h", h > 0 && h < Constants.ISO_HALF_H)
  }

  @Test def aPropTakesTheLayerOfTheGroundItStandsIn(): Unit = {
    val w = world(
      "sss",
      "sTs",
      "sss")
    assertEquals(layerOf(Tile.Sand), layerAt(w, 1, 1))
  }

  @Test def whatSpillsOverIsTheColourItsOwnTilesArePainted(): Unit = {
    // An edge paints the higher ground's colour over the lower's tile, where the tile is that colour
    // all the way to its edge: any difference shows as a patch along every edge on the map
    val atlas = ImageIO.read(new File("sprites/tiles.png"))
    val cw = Constants.TILE_ATLAS_CELL_W; val ch = Constants.TILE_ATLAS_CELL_H
    // just inside the diamond's four corners, centred at (40, 92), 80 by 40: top, right, left, bottom
    val corners = Seq((40, 74), (cw - 4, 92), (3, 92), (40, 110))
    for (t <- layered :+ Tile.Flowers; f <- 0 until 4) {
      // The deck's plates are two cells across, its four frames a plate's quarters (Tile.variantAt):
      // only a quarter's outer corner is the deck's margin, its others the plate's middle, a shade off
      // it. What spills over a deck cell's edge is the margin's colour.
      val at = if (t eq Tile.Circuit) Seq(corners(f)) else corners
      for ((x, y) <- at) {
        val texel = atlas.getRGB(t.id * cw + x, f * ch + y) & 0xFFFFFF
        assertEquals(f"${t.name} frame $f at ($x, $y): ${texel}%06x", base(layerOf(t)) & 0xFFFFFF, texel)
      }
    }
  }

  @Test def anEdgeReachesTheCellsRoundItAndNoFurther(): Unit = {
    val w = world(
      ".....",
      ".....",
      "..p..",
      ".....",
      ".....")
    val l = layers(w)
    for (y <- 0 until 5; x <- 0 until 5)
      assertEquals(s"($x, $y)", Math.abs(x - 2) <= 1 && Math.abs(y - 2) <= 1, hasEdge(l, 5, 5, x, y))
  }

  @Test def builtGroundKeepsItsStraightEdges(): Unit = {
    // A boardwalk takes no part, and a cell reads it as its own ground: neither spills over the other
    val w = world(
      ".kk",
      ".kk",
      "kkw")
    val l = layers(w)
    assertFalse("the grass beside the boardwalk", hasEdge(l, 3, 3, 0, 0))
    assertFalse("the boardwalk", hasEdge(l, 3, 3, 1, 1))
    assertFalse("the water beside it", hasEdge(l, 3, 3, 2, 2))
    val e = new Array[Double](4)
    shade(l, 3, 3, 0, 1, 0.45, 1.0, px, e)
    assertEquals("nothing drawn on the grass at the boardwalk's edge", 0.0, e(3), 0.0)
  }

  /** Grids of every layer, seeded. */
  private def randomGrids(n: Int, size: Int): Iterator[Array[Byte]] = {
    val rng = new java.util.Random(20261014)
    Iterator.fill(n) {
      val g = new Array[Byte](size * size)
      // patches rather than noise: a layer per 2 x 2 block, some cells changed
      for (y <- 0 until size; x <- 0 until size) {
        g(y * size + x) = (1 + ((x / 2 * 7 + y / 2 * 13 + rng.nextInt(3)) % (Count - 1))).toByte
      }
      g
    }
  }

  @Test def nothingIsDrawnOverTheMiddleOfACell(): Unit = {
    // A cell's own ground shows at its centre, where a player standing on it is drawn: what they
    // stand in is what the cell is. (A riser and its foam come closest, hanging into a pool in a
    // corner of higher ground: to within a couple of display units of its centre. Open space is the
    // exception, and nobody stands in it: the deck's hull hangs right over a cell of it in a corner.)
    val e = new Array[Double](4)
    val size = 6
    for (g <- randomGrids(200, size); y <- 0 until size; x <- 0 until size if g(y * size + x) != layerOf(Tile.Void);
         (dx, dy) <- Seq((0.0, 0.0), (0.05, 0.0), (0.0, 0.05), (-0.05, 0.0), (0.0, -0.05))) {
      shade(g, size, size, x, y, x + dx, y + dy, px, e)
      assertEquals(s"cell ($x, $y)", 0.0, e(3), 0.0)
    }
  }

  @Test def anEdgeIsTheSameFromEitherCellItPassesBetween(): Unit = {
    val size = 6
    val rng = new java.util.Random(7)
    for (g <- randomGrids(200, size); _ <- 0 until 40) {
      val x = 1 + rng.nextInt(size - 3); val y = 1 + rng.nextInt(size - 3)
      val t = rng.nextDouble() - 0.5
      // on the edge between (x, y) and (x + 1, y), and between (x, y) and (x, y + 1)
      val a = composite(g, size, size, x, y, x + 0.5, y + t)
      val b = composite(g, size, size, x + 1, y, x + 0.5, y + t)
      assertTrue(s"($x, $y) | (${x + 1}, $y) at ${y + t}: $a / $b", close(a, b, 1e-9))
      val c = composite(g, size, size, x, y, x + t, y + 0.5)
      val d = composite(g, size, size, x, y + 1, x + t, y + 0.5)
      assertTrue(s"($x, $y) | ($x, ${y + 1}) at ${x + t}: $c / $d", close(c, d, 1e-9))
    }
  }

  @Test def mirroredGroundHasAMirroredEdge(): Unit = {
    // Swapping a map's x and y mirrors it left to right on the screen; the riser hangs straight down,
    // so it mirrors too
    val size = 6
    val rng = new java.util.Random(11)
    val e = new Array[Double](4); val m = new Array[Double](4)
    for (g <- randomGrids(100, size); _ <- 0 until 40) {
      val t = new Array[Byte](g.length)
      for (y <- 0 until size; x <- 0 until size) t(x * size + y) = g(y * size + x)
      val x = rng.nextDouble() * (size - 1); val y = rng.nextDouble() * (size - 1)
      val cx = Math.floor(x + 0.5).toInt; val cy = Math.floor(y + 0.5).toInt
      shade(g, size, size, cx, cy, x, y, px, e)
      shade(t, size, size, cy, cx, y, x, px, m)
      for (i <- 0 until 4) assertEquals(s"($x, $y) channel $i", e(i), m(i), 1e-9)
    }
  }

  @Test def higherGroundStandsOnARiserWhereItsEdgeFacesTheCamera(): Unit = {
    // Grass up the screen from a path (at smaller x) faces the camera across it, and stands on a
    // step of soil; grass down the screen from it (larger x) shows only its ink
    val behind = layers(world("..pp", "..pp", "..pp", "..pp"))
    val inFront = layers(world("pp..", "pp..", "pp..", "pp.."))
    val path = rgb(base(layerOf(Tile.Path)))
    // just past the grass's edge (x = 1.5), on the path's side, a display unit down the screen
    val below = composite(behind, 4, 4, 2, 1, 1.5 + 0.05, 1.0 + 0.05)
    assertTrue(s"a riser below grass that faces the camera: $below", close(below, rgb(riser(layerOf(Tile.Grass))), 0.02))
    val above = composite(inFront, 4, 4, 1, 1, 1.5 - 0.05, 1.0 - 0.05)
    assertTrue(s"the bare path above grass in front of it: $above", close(above, path, 0.02))
  }

  @Test def spaceGlowsRoundTheDeckFadingAwayFromIt(): Unit = {
    // the deck is down the screen from the space, so no hull covers the glow; the glow is brightest at
    // the deck's edge and gone a little way out
    val g = layers(world("vvcc", "vvcc", "vvcc", "vvcc"))
    val glow = rgb(shore(layerOf(Tile.Void)))
    val space = rgb(base(layerOf(Tile.Void)))
    def at(back: Double) = composite(g, 4, 4, 1, 1, 1.5 - back, 1.0 - back)
    def dist(a: (Double, Double, Double), b: (Double, Double, Double)) =
      Math.abs(a._1 - b._1) + Math.abs(a._2 - b._2) + Math.abs(a._3 - b._3)
    val near = at(0.01); val mid = at(0.06); val far = at(0.3)
    assertTrue(s"glow at the edge: $near", dist(near, glow) < dist(near, space) / 4)
    assertTrue(s"fading: $near, $mid", dist(mid, glow) > dist(near, glow) && dist(mid, space) < dist(near, space))
    assertTrue(s"space further out: $far", close(far, space, 0.01))
  }

  @Test def waterCarriesFoamWhereTheShoreMeetsIt(): Unit = {
    // the grass is down the screen from the water, so no riser covers the foam
    val g = layers(world("ww..", "ww..", "ww..", "ww.."))
    val c = composite(g, 4, 4, 1, 1, 1.5 - 0.04, 1.0 - 0.04)
    assertTrue(s"foam: $c", close(c, rgb(shore(layerOf(Tile.Water))), 0.05))
  }
}
