package com.gridgame.client.render

import com.gridgame.client.gl.{GLTexture, TextureRegion}
import com.gridgame.common.model.ItemType

/**
 * The pickups' art on the GPU: sprites/items.png (scripts/generate_items.py) as one texture, and a
 * TextureRegion for each item's frames and for the twinkle. The world's items and the HUD's
 * inventory are both drawn from it (ItemPainter).
 *
 * It is filtered as the characters and the terrain are, linearly and through two halvings: an item
 * is a texel a pixel at High on a HiDPI screen and under a third of that at Low, and without the
 * halvings its ink broke up and crawled as it bobbed. Its clear texels take the colour of the art
 * beside them first (GLTexture.padTransparentRings), so a sample at an item's edge, or a halving of
 * it, mixes the edge with its own colour and not with the black a PNG leaves in a clear texel. The
 * art keeps clear of its cell's edge (generate_items.MARGIN, which ItemSpritesTest holds it to), so
 * no halving mixes one cell with the next and the cells need no gutters.
 */
object ItemSprites {
  /** A cell of the atlas, in texels, and how many of them a row holds. */
  val Cell = 128
  val Cols = 4
  val Rows = 4

  /** The gem's frames: a turn of 45 degrees, which brings an eight-sided stone back to where it
    * began, so they loop. Every other item is one frame. */
  val GemFrames = 8

  /** The texels at every edge of a cell that the art keeps out of. */
  val Margin = 6

  // Where each thing is in the atlas, cells counted along the rows (generate_items.py)
  private val GemCell = 0
  private val HeartCell = 8
  private val StarCell = 9
  private val ShieldCell = 10
  private val FenceCell = 11
  private val TwinkleCell = 12

  private val MipLevels = 2

  /** The first cell of an item's frames, and how many it has. */
  def firstCell(itemType: ItemType): Int = itemType match {
    case ItemType.Gem => GemCell
    case ItemType.Heart => HeartCell
    case ItemType.Star => StarCell
    case ItemType.Shield => ShieldCell
    case ItemType.Fence => FenceCell
  }

  def frames(itemType: ItemType): Int = if (itemType == ItemType.Gem) GemFrames else 1

  /** The twinkle's cell. */
  def twinkleCell: Int = TwinkleCell

  private var texture: GLTexture = _
  private var regions: Array[TextureRegion] = _
  private var loadFailed = false

  private def ensureLoaded(): Unit = {
    if (texture != null || loadFailed) return
    try {
      texture = GLTexture.decode("sprites/items.png") { (px, w, h) =>
        if (w != Cols * Cell || h != Rows * Cell)
          throw new RuntimeException(s"items.png is ${w}x$h, not ${Cols * Cell}x${Rows * Cell}")
        // as far as the last halving and a linear sample of it reach
        GLTexture.padTransparentRings(px, w, h, Cell, Cell, 2 << MipLevels)
        val tex = GLTexture.allocate(w, h, MipLevels)
        GLTexture.upload(tex, 0, 0, 0, w, h, px)
        var src = px
        var level = 1
        var scratch: java.nio.ByteBuffer = null
        try {
          while (level <= MipLevels) {
            val lw = w >> level; val lh = h >> level
            val dst = GLTexture.allocPixels(lw * lh * 4)
            GLTexture.halve(src, lw * 2, lh * 2, dst)
            GLTexture.upload(tex, level, 0, 0, lw, lh, dst)
            if (scratch != null) GLTexture.freePixels(scratch)
            scratch = dst
            src = dst
            level += 1
          }
        } finally if (scratch != null) GLTexture.freePixels(scratch)
        tex
      }
      regions = Array.tabulate(Cols * Rows)(i => texture.region((i % Cols) * Cell, (i / Cols) * Cell, Cell, Cell))
    } catch {
      case e: Exception =>
        System.err.println(s"ItemSprites: failed to load sprites/items.png: ${e.getMessage}")
        loadFailed = true
        texture = null
        regions = null
    }
  }

  /** Frame `frame` of an item (any number: it wraps), or null if the atlas couldn't be loaded. */
  def region(itemType: ItemType, frame: Int): TextureRegion = {
    ensureLoaded()
    if (regions == null) return null
    val n = frames(itemType)
    regions(firstCell(itemType) + (if (n == 1) 0 else ((frame % n) + n) % n))
  }

  /** The twinkle: white, for the renderer to tint, turn and size. Null if the atlas couldn't be loaded. */
  def twinkle: TextureRegion = {
    ensureLoaded()
    if (regions == null) null else regions(TwinkleCell)
  }

  def dispose(): Unit = {
    if (texture != null) texture.dispose()
    texture = null
    regions = null
    loadFailed = false
  }
}
