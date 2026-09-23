package com.gridgame.common.model

import com.gridgame.common.Constants
import org.junit.Assert._
import org.junit.Test

import java.io.File
import javax.imageio.ImageIO

/**
 * How tiles stand on the map (TileForm), what a prop is drawn standing in (Tile.groundUnder), and
 * that the tileset scripts/generate_tiles.py draws agrees with both: a column for every tile, a
 * ground tile that covers its diamond, a prop that leaves its ground showing.
 */
class TileTest {

  private def world(rows: String*): WorldData = {
    val legend = Map[Char, Tile]('.' -> Tile.Grass, 's' -> Tile.Sand, 'p' -> Tile.Path, 'n' -> Tile.Snow,
      'T' -> Tile.Tree, 'P' -> Tile.Palm, 'R' -> Tile.Rock, 'w' -> Tile.Water, '#' -> Tile.Wall)
    val tiles: Array[Array[Tile]] = rows.map(row => row.map(legend).toArray[Tile]).toArray
    new WorldData("test", rows.head.length, rows.length, tiles, Seq(new Position(0, 0)))
  }

  @Test def walkableGroundAndNothingElseIsGround(): Unit = {
    for (t <- Tile.all) assertEquals(t.name, t.walkable, t.form eq TileForm.Ground)
  }

  @Test def waterAndLavaArePoolsNotWalls(): Unit = {
    for (t <- Seq(Tile.Water, Tile.DeepWater, Tile.Lava)) {
      assertEquals(t.name, TileForm.Pool, t.form)
      assertFalse(t.name, t.walkable)
    }
  }

  @Test def everyPropGrowsOutOfWalkableGround(): Unit = {
    val props = Tile.all.filter(_.form eq TileForm.Prop)
    assertTrue(props.contains(Tile.Tree) && props.contains(Tile.Palm) && props.contains(Tile.Snowman))
    for (t <- props) {
      assertFalse(t.name, t.walkable)
      assertEquals(t.name, TileForm.Ground, t.ground.form)
    }
  }

  @Test def tileIdsRunWithoutGapsAndRoundTrip(): Unit = {
    // an id is its column in sprites/tiles.png
    assertEquals(Tile.all.indices.toList, Tile.all.map(_.id).toList)
    assertEquals(Tile.all.size, Tile.all.map(_.name).distinct.size)
    for (t <- Tile.all) {
      assertSame(t, Tile.fromId(t.id))
      assertSame(t, Tile.fromName(t.name))
    }
  }

  @Test def aPropStandsInItsOwnGroundWhenAnyIsBesideIt(): Unit = {
    // The palm's own ground is sand. Grass comes first in the order the neighbours are looked at
    // (south, east, west, north), but there is sand to the north.
    val w = world(
      ".s.",
      ".P.",
      "...")
    assertSame(Tile.Sand, Tile.groundUnder(w, 1, 1))
  }

  @Test def otherwiseInWhateverGroundIsAroundIt(): Unit = {
    // a palm in a meadow stands on grass, a tree by a path on the path if that is all there is
    assertSame(Tile.Grass, Tile.groundUnder(world("...", ".P.", "..."), 1, 1))
    assertSame(Tile.Path, Tile.groundUnder(world("#p#", "#T#", "#w#"), 1, 1))
  }

  @Test def andInItsOwnGroundWithNoGroundAroundIt(): Unit = {
    // deep in a forest, on an islet in a pool, or in the corner of the map
    assertSame(Tile.Grass, Tile.groundUnder(world("TTT", "TTT", "TTT"), 1, 1))
    assertSame(Tile.Sand, Tile.groundUnder(world("www", "wPw", "www"), 1, 1))
    assertSame(Tile.Grass, Tile.groundUnder(world("TT", "TT"), 0, 0))
  }

  // -- the tileset --------------------------------------------------------------------------

  private lazy val atlas = ImageIO.read(new File("sprites/tiles.png"))
  private val CellW = Constants.TILE_ATLAS_CELL_W
  private val CellH = Constants.TILE_ATLAS_CELL_H

  private def alpha(tile: Tile, frame: Int, x: Int, y: Int): Int =
    (atlas.getRGB(tile.id * CellW + x, frame * CellH + y) >>> 24) & 0xFF

  @Test def theTilesetHasAColumnForEveryTile(): Unit = {
    // Adding a tile without rerunning scripts/generate_tiles.py draws it as grass
    assertEquals("columns", Tile.all.size * CellW, atlas.getWidth)
    assertEquals("four rows", 4 * CellH, atlas.getHeight)
  }

  @Test def theTilesetDrawsEachTileAsItsFormSays(): Unit = {
    // The diamond a tile stands on is centred at (40, 92), 80 wide and 40 tall. Just inside its
    // left and right corners is ground a prop leaves showing and a ground tile covers.
    val (cx, cy) = (CellW / 2, 92)
    for (t <- Tile.all; f <- 0 until 4) {
      val corners = Seq(alpha(t, f, 3, cy), alpha(t, f, CellW - 4, cy))
      t.form match {
        case TileForm.Ground | TileForm.Pool =>
          assertEquals(s"${t.name} covers its diamond", 255, alpha(t, f, cx, cy))
          assertTrue(s"${t.name} covers its diamond", corners.forall(_ == 255))
          assertEquals(s"${t.name} is flat", 0, alpha(t, f, cx, cy - 30))
        case TileForm.Block =>
          assertTrue(s"${t.name} fills its cell", corners.forall(_ == 255))
          assertEquals(s"${t.name} rises off the ground", 255, alpha(t, f, cx, cy - 22))
        case TileForm.Prop =>
          assertTrue(s"${t.name} leaves its ground showing", corners.forall(_ == 0))
      }
    }
  }
}
