package com.gridgame.client.render
package hud

import com.gridgame.client.gl.{GLTexture, TextureRegion}

import java.awt.{BasicStroke, Color, RenderingHints, Shape}
import java.awt.geom.{Ellipse2D, Line2D}
import java.awt.image.BufferedImage

/**
 * The mouse's pointer in a match (render/CLAUDE.md, *The pointer is ours*): a crosshair drawn over
 * the finished frame, HUD and all, where the mouse is. The system's arrow is hidden over the match's
 * window (GLWindow.hideSystemCursor), since in a fight it was lost among the blasts and name plates.
 *
 * A gold ring with four white ticks crossing it and a white dot at the hotspot, every line inked and
 * the whole casting a soft shadow: the ink carries it over snow, sand and a blast's white heart, the
 * white and the gold over the dark maps and the name plates, and the gold is what no name plate or
 * HUD panel has. It is drawn from a picture made once with Java2D at the display's pixel density and
 * put down a texel to a pixel, rather than with the shape batch, whose shapes have hard staircased
 * edges: this is the one thing on the screen the eye is always on.
 */
private[render] final class CursorPainter(ctx: RenderContext) {
  import ctx._
  import CursorPainter._

  private var texture: GLTexture = _
  private var region: TextureRegion = _
  private var scale = 0f
  private var sizePx = 0

  /** The pointer with its hotspot at (x, y), in the HUD's units, which are `pixelScale` pixels each. */
  def draw(x: Float, y: Float, pixelScale: Float): Unit = {
    if (pixelScale != scale) make(pixelScale)
    val left = cornerPx(x * scale, sizePx)
    val top = cornerPx(y * scale, sizePx)
    beginSprites()
    spriteBatch.draw(region, left / scale, top / scale, sizePx / scale, sizePx / scale)
  }

  private def make(pixelScale: Float): Unit = {
    dispose()
    sizePx = artSize(pixelScale)
    texture = GLTexture.fromArgb(art(sizePx, pixelScale), sizePx, sizePx)
    region = texture.fullRegion
    scale = pixelScale
  }

  def dispose(): Unit = {
    if (texture != null) { texture.dispose(); texture = null }
    scale = 0f
  }
}

private[render] object CursorPainter {
  // The crosshair, in units from the hotspot: the ring, the ticks crossing it, the dot, and the ink
  // round every one of them
  val RingRadius = 11f
  val RingWidth = 3f
  val TickFrom = 7.5f
  val TickTo = 17f
  val TickWidth = 3f
  val DotRadius = 2f
  val Ink = 1.5f
  // The shadow: the ink's outline blurred this much, this dark at its heart
  private val ShadowSigma = 1.6f
  private val ShadowAlpha = 0.55f
  /** How far the picture reaches from the hotspot, shadow and all. */
  val Extent: Float = TickTo + TickWidth / 2 + Ink + ShadowSigma * 3 + 1f

  private val InkColor = new Color(18, 14, 30)
  private val Gold = new Color(255, 206, 72)
  private val White = Color.WHITE

  /** Pixels across the picture at `pixelScale`: odd at an odd scale, so the hotspot is a pixel's
    * centre and an odd width of line (3 units at 1x) covers whole pixels; even at an even one, where
    * the hotspot is a corner and every line is an even number of pixels wide. */
  def artSize(pixelScale: Float): Int = {
    val half = Math.ceil(Extent * pixelScale).toInt
    if (Math.round(pixelScale) % 2 == 1) half * 2 + 1 else half * 2
  }

  /** Where a picture `size` pixels across starts, in whole pixels, for a hotspot at `hotspotPx`, so
    * that each texel lands on exactly one pixel and stays sharp: an odd picture's middle texel on the
    * pixel under the hotspot, an even one's middle corner on the pixel corner nearest it. */
  def cornerPx(hotspotPx: Float, size: Int): Int =
    if ((size & 1) == 1) Math.floor(hotspotPx).toInt - size / 2
    else Math.round(hotspotPx) - size / 2

  /** The picture, `size` pixels square with the hotspot at its middle, at `pixelScale` pixels a unit:
    * ARGB, straight alpha, row by row from the top. */
  def art(size: Int, pixelScale: Float): Array[Int] = {
    val c = size / 2.0
    val ticks: Seq[Shape] = Seq((1f, 0f), (0f, 1f), (-1f, 0f), (0f, -1f)).map { case (dx, dy) =>
      new Line2D.Float(dx * TickFrom, dy * TickFrom, dx * TickTo, dy * TickTo)
    }
    val ring = new Ellipse2D.Float(-RingRadius, -RingRadius, RingRadius * 2, RingRadius * 2)
    def dot(r: Float) = new Ellipse2D.Float(-r, -r, r * 2, r * 2)

    // All the ink first, then all the lines, so where a tick crosses the ring the white runs over the
    // gold unbroken
    def paint(ink: Boolean): Array[Int] = {
      val img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
      val g = img.createGraphics()
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
      g.translate(c, c)
      g.scale(pixelScale, pixelScale)
      val grow = if (ink) Ink * 2 else 0f
      g.setColor(if (ink) InkColor else Gold)
      g.setStroke(new BasicStroke(RingWidth + grow))
      g.draw(ring)
      g.setColor(if (ink) InkColor else White)
      g.setStroke(new BasicStroke(TickWidth + grow, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND))
      ticks.foreach(g.draw)
      g.fill(dot(DotRadius + grow / 2))
      g.dispose()
      img.getRGB(0, 0, size, size, null, 0, size)
    }
    val inked = paint(ink = true)
    val lines = paint(ink = false)

    val n = size * size
    val shadow = new Array[Float](n)
    var i = 0
    while (i < n) { shadow(i) = (inked(i) >>> 24) / 255f; i += 1 }
    blur(shadow, size, ShadowSigma * pixelScale)

    // The shadow, the ink over it, the lines over that
    val out = new Array[Int](n)
    val ir = InkColor.getRed / 255f; val ig = InkColor.getGreen / 255f; val ib = InkColor.getBlue / 255f
    i = 0
    while (i < n) {
      var a = Math.min(1f, shadow(i) * ShadowAlpha)
      var r = ir; var g = ig; var b = ib
      var layer = 0
      while (layer < 2) {
        val p = if (layer == 0) inked(i) else lines(i)
        val pa = (p >>> 24) / 255f
        if (pa > 0f) {
          val oa = pa + a * (1f - pa)
          val keep = a * (1f - pa)
          r = (((p >> 16) & 0xFF) / 255f * pa + r * keep) / oa
          g = (((p >> 8) & 0xFF) / 255f * pa + g * keep) / oa
          b = ((p & 0xFF) / 255f * pa + b * keep) / oa
          a = oa
        }
        layer += 1
      }
      out(i) = (Math.round(a * 255) << 24) | (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255)
      i += 1
    }
    out
  }

  /** A Gaussian blur of a `size` x `size` field, in place: across, then down. */
  private def blur(field: Array[Float], size: Int, sigma: Float): Unit = {
    val rad = Math.ceil(sigma * 3).toInt
    val k = Array.tabulate(rad * 2 + 1)(j => Math.exp(-(j - rad) * (j - rad) / (2.0 * sigma * sigma)).toFloat)
    val sum = k.sum
    val tmp = new Array[Float](size * size)
    def pass(from: Array[Float], to: Array[Float], across: Boolean): Unit = {
      var y = 0
      while (y < size) {
        var x = 0
        while (x < size) {
          var acc = 0f
          var t = -rad
          while (t <= rad) {
            val xx = if (across) x + t else x
            val yy = if (across) y else y + t
            if (xx >= 0 && xx < size && yy >= 0 && yy < size) acc += from(yy * size + xx) * k(t + rad)
            t += 1
          }
          to(y * size + x) = acc / sum
          x += 1
        }
        y += 1
      }
    }
    pass(field, tmp, across = true)
    pass(tmp, field, across = false)
  }
}
