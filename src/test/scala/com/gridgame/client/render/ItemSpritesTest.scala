package com.gridgame.client.render

import com.gridgame.common.model.ItemType
import org.junit.Assert._
import org.junit.Test

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * That the items' atlas scripts/generate_items.py draws is what the renderer takes it for
 * (ItemSprites, ItemPainter): a cell for every frame of every item and one for the twinkle, each
 * item's art in the middle of its cell, which it is turned, swollen and floated about, and clear of
 * its cell's edge, where the texture's halvings would mix it with the cell beside it.
 */
class ItemSpritesTest {
  private lazy val atlas: BufferedImage = ImageIO.read(new File("sprites/items.png"))
  private val Cell = ItemSprites.Cell

  private def alpha(cell: Int, x: Int, y: Int): Int =
    atlas.getRGB((cell % ItemSprites.Cols) * Cell + x, (cell / ItemSprites.Cols) * Cell + y) >>> 24

  private def rgb(cell: Int, x: Int, y: Int): (Int, Int, Int) = {
    val p = atlas.getRGB((cell % ItemSprites.Cols) * Cell + x, (cell / ItemSprites.Cols) * Cell + y)
    ((p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF)
  }

  /** The box round what a cell draws: (x0, y0, x1, y1), the last two past its end; None if it is empty. */
  private def box(cell: Int): Option[(Int, Int, Int, Int)] = {
    val pts = for (y <- 0 until Cell; x <- 0 until Cell if alpha(cell, x, y) > 16) yield (x, y)
    if (pts.isEmpty) None
    else Some((pts.map(_._1).min, pts.map(_._2).min, pts.map(_._1).max + 1, pts.map(_._2).max + 1))
  }

  private def cellsOf(t: ItemType): Seq[Int] = (0 until ItemSprites.frames(t)).map(ItemSprites.firstCell(t) + _)

  private val drawn: Seq[Int] = ItemType.all.flatMap(cellsOf) :+ ItemSprites.twinkleCell

  @Test def theAtlasIsTheGridTheRendererCutsUp(): Unit = {
    assertEquals(ItemSprites.Cols * Cell, atlas.getWidth)
    assertEquals(ItemSprites.Rows * Cell, atlas.getHeight)
    assertTrue("it has an alpha channel", atlas.getColorModel.hasAlpha)
  }

  @Test def everyItemHasItsOwnCells(): Unit = {
    assertEquals("no two share a cell", drawn.size, drawn.distinct.size)
    assertTrue(drawn.forall(c => c >= 0 && c < ItemSprites.Cols * ItemSprites.Rows))
    for (c <- drawn) assertTrue(s"cell $c holds something", box(c).isDefined)
    for (c <- 0 until ItemSprites.Cols * ItemSprites.Rows if !drawn.contains(c))
      assertTrue(s"cell $c, which nothing is drawn from, is empty", box(c).isEmpty)
  }

  @Test def everyItemIsInTheMiddleOfItsCellAndClearOfItsEdge(): Unit = {
    for (c <- drawn) {
      val (x0, y0, x1, y1) = box(c).get
      assertEquals(s"cell $c across", Cell / 2.0, (x0 + x1) / 2.0, 2.0)
      assertEquals(s"cell $c down", Cell / 2.0, (y0 + y1) / 2.0, 2.0)
      val m = ItemSprites.Margin
      assertTrue(s"cell $c's art ($x0, $y0)-($x1, $y1) keeps $m texels from its cell's edge",
        x0 >= m && y0 >= m && x1 <= Cell - m && y1 <= Cell - m)
    }
  }

  @Test def anItemFillsAboutSevenTenthsOfItsCell(): Unit = {
    // what ItemPainter and the HUD size a cell by: an item about three quarters of it across
    for (t <- ItemType.all; c <- cellsOf(t)) {
      val (x0, y0, x1, y1) = box(c).get
      val across = Math.max(x1 - x0, y1 - y0) / Cell.toDouble
      assertTrue(s"${t.name} is $across of its cell across", across >= 0.62 && across <= 0.80)
    }
  }

  @Test def theGemTurnsFromFrameToFrame(): Unit = {
    val cells = cellsOf(ItemType.Gem)
    assertEquals(ItemSprites.GemFrames, cells.size)
    def differing(a: Int, b: Int): Int =
      (for (y <- 0 until Cell; x <- 0 until Cell if rgb(a, x, y) != rgb(b, x, y) || alpha(a, x, y) != alpha(b, x, y)) yield 1).size
    for (i <- cells.indices) {
      val next = cells((i + 1) % cells.size)
      val d = differing(cells(i), next)
      // a step of the turn moves its facets, and not the whole stone: the last frame steps on to the first
      assertTrue(s"frame $i to the next changes $d texels", d > 150 && d < Cell * Cell / 4)
    }
    // the same stone all the way round: as tall in every frame, and very nearly as wide
    val boxes = cells.map(box(_).get)
    assertTrue(boxes.map(b => b._4 - b._2).distinct.size <= 2)
    assertTrue(boxes.map(b => b._3 - b._1).max - boxes.map(b => b._3 - b._1).min <= 10)
  }

  @Test def everyItemIsDrawnInItsOwnColour(): Unit = {
    // The HUD tints an item's slot ItemType.colorRGB: the art's own colour is that hue, so no slot
    // holds a picture of another colour, and nobody finds two items' cells swapped
    def hue(r: Int, g: Int, b: Int): Float = java.awt.Color.RGBtoHSB(r, g, b, null)(0) * 360f
    for (t <- ItemType.all) {
      val want = hue((t.colorRGB >> 16) & 0xFF, (t.colorRGB >> 8) & 0xFF, t.colorRGB & 0xFF)
      val c = ItemSprites.firstCell(t)
      val hues = for {
        y <- 0 until Cell; x <- 0 until Cell
        if alpha(c, x, y) == 255
        (r, g, b) = rgb(c, x, y)
        hsb = java.awt.Color.RGBtoHSB(r, g, b, null)
        if hsb(1) > 0.3f && hsb(2) > 0.5f
      } yield hsb(0) * 360f
      assertTrue(s"${t.name} has colour to speak of", hues.size > 500)
      // the hue most of its coloured texels are within 30 degrees of
      val near = hues.count(h => Math.abs(Math.IEEEremainder(h - want, 360.0)) <= 30.0)
      assertTrue(s"${t.name}: $near of ${hues.size} coloured texels are its colour (hue $want)", near > hues.size * 0.6)
    }
  }

  @Test def theTwinkleIsWhiteForTheRendererToTint(): Unit = {
    val c = ItemSprites.twinkleCell
    val (r, g, b) = rgb(c, Cell / 2, Cell / 2)
    assertEquals(255, alpha(c, Cell / 2, Cell / 2))
    assertTrue(s"its heart is white, not ($r, $g, $b)", r >= 250 && g >= 250 && b >= 250)
  }
}
