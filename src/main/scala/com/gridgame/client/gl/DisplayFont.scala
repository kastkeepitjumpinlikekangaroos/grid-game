package com.gridgame.client.gl

import org.lwjgl.system.MemoryUtil

import java.awt.{BasicStroke, Color, Font, RenderingHints}
import java.awt.font.FontRenderContext
import java.awt.image.BufferedImage
import scala.collection.mutable

/**
 * Titles lettered as the menus letter theirs (ui/Widgets.displayTitle: the scoreboard's "Victory!",
 * the login screen's name): Exo 2 Black Italic, every letter inked all round and casting a hard
 * shadow straight down, its face lit from above — pale at the top of the capitals, its colour
 * through their middle, deep at their foot ([[DisplayFont.Face]]). For what the HUD says big: a kill
 * and the run of them, the opening's end, a death.
 *
 * A letter is rasterized once, at the display's pixel density, as two pictures of coverage alone
 * ([[DisplayFont.Letter]]): its face, and its face grown by the ink round it (stroked as wide as the
 * outline both ways, joined round). A line is drawn in three passes over its letters — every letter's
 * ink dropped by the shadow, every letter's ink, every letter's face — as the menus draw three layers
 * of a whole title, so one letter's ink never covers its neighbour's face where italic capitals lean
 * into each other. A face is tinted in up to four bands down the letter, at the gradient's stops,
 * measured from the baseline so every letter of a line is lit alike. Coverage is a byte a texel, read
 * as white at that alpha (GLTexture.createCoverage): the capitals, digits and the marks the HUD uses,
 * at 60 units on a HiDPI screen, fill two pages of 1024x512 bytes, where RGBA would take four times
 * the memory.
 *
 * All sizes and metrics are in units, as GLFontRenderer's are; every draw takes a `scale`, for a
 * title popping in. Only the render thread uses it, and it allocates nothing to draw a letter it has
 * already rasterized ([[prewarm]] the ones a frame will need before it needs them).
 */
final class DisplayFont(val size: Int, val pixelScale: Float = 1f) {
  import DisplayFont._

  private val rasterSize: Int = Math.max(1, Math.round(size * pixelScale))
  /** Units per rasterized pixel. */
  private val unit: Float = size.toFloat / rasterSize
  // Exo 2 Black Italic is black by its own weight, so it is derived plain; the CJK faces it falls back
  // on are regular, and derived bold to stand beside it
  private val fonts: Array[Font] =
    chain.zipWithIndex.map { case (f, i) => f.deriveFont(if (i == 0) Font.PLAIN else Font.BOLD, rasterSize.toFloat) }

  /** The ink all round a letter, in units: the menus' title's, 5.5% of its size. */
  val outline: Float = Math.max(1.5f, size * 0.055f)
  /** How far the shadow drops below the letters, in units: the menus' title's, 7.5% of its size. */
  val shadowDrop: Float = Math.max(3f, size * 0.075f)
  /** Space set after every letter, in units: a title's capitals stand a little apart. */
  val tracking: Float = size * 0.012f

  /** How tall the capitals stand over the baseline, in units: what a line is centred by. */
  val capHeight: Float = (-fonts(0).createGlyphVector(Frc, "H").getVisualBounds.getMinY * unit).toFloat

  private final class Glyph(val letter: Letter, val ink: TextureRegion, val face: TextureRegion)

  // Latin-1 in a flat table, the rest in a LongMap keyed by the unboxed code point: a lookup per letter
  // drawn allocates nothing
  private val latin = new Array[Glyph](256)
  private val others = mutable.LongMap.empty[Glyph]

  // Shelf-packed pages of coverage, grown a page at a time
  private val pages = mutable.ArrayBuffer[GLTexture]()
  private var penX = Pad
  private var penY = Pad
  private var rowH = 0

  private def newPage(): Unit = {
    pages += GLTexture.createCoverage(PageW, PageH)
    penX = Pad; penY = Pad; rowH = 0
  }

  private def glyph(cp: Int): Glyph = {
    if (cp >= 0 && cp < 256) {
      var g = latin(cp)
      if (g == null) { g = make(cp); latin(cp) = g }
      g
    } else {
      var g = others.getOrNull(cp.toLong)
      if (g == null) { g = make(cp); others.update(cp.toLong, g) }
      g
    }
  }

  private def make(cp: Int): Glyph = {
    val l = rasterize(fonts, cp, outline * pixelScale, unit)
    if (l.w == 0) return new Glyph(l, null, null)
    if (pages.isEmpty) newPage()
    // Both pictures side by side on one shelf
    val need = l.w * 2 + Pad
    if (penX + need + Pad > PageW) { penX = Pad; penY += rowH + Pad; rowH = 0 }
    if (penY + l.h + Pad > PageH) newPage()
    val page = pages.last
    val buf = MemoryUtil.memAlloc(l.w * l.h)
    try {
      buf.put(l.ink).flip()
      GLTexture.uploadCoverage(page, penX, penY, l.w, l.h, buf)
      buf.clear()
      buf.put(l.face).flip()
      GLTexture.uploadCoverage(page, penX + l.w + Pad, penY, l.w, l.h, buf)
    } finally MemoryUtil.memFree(buf)
    val g = new Glyph(l, page.region(penX, penY, l.w, l.h), page.region(penX + l.w + Pad, penY, l.w, l.h))
    penX += need + Pad
    rowH = Math.max(rowH, l.h)
    g
  }

  /** Rasterize every letter of `text` now, so the frame that first shows it doesn't. GL thread only. */
  def prewarm(text: String): Unit = {
    var i = 0
    while (i < text.length) {
      val cp = text.codePointAt(i)
      i += Character.charCount(cp)
      glyph(cp)
    }
  }

  /** How wide `text` is drawn at `scale`, in units: its letters' advances and the tracking between them. */
  def measure(text: String, scale: Float = 1f): Float = {
    var w = 0f
    var n = 0
    var i = 0
    while (i < text.length) {
      val cp = text.codePointAt(i)
      i += Character.charCount(cp)
      w += glyph(cp).letter.advance * unit
      n += 1
    }
    (w + tracking * Math.max(0, n - 1)) * scale
  }

  /**
   * `text` with its baseline at `y`, from `x`, in `face`, at `alpha`: its shadow, its ink, its face.
   * Returns how wide it is.
   */
  def draw(batch: SpriteBatch, text: String, x: Float, y: Float, face: Face, alpha: Float, scale: Float = 1f): Float = {
    if (alpha <= 0f) return measure(text, scale)
    inkPass(batch, text, x, y + shadowDrop * scale, scale, face, alpha * face.shadowAlpha)
    inkPass(batch, text, x, y, scale, face, alpha)
    facePass(batch, text, x, y, scale, face, alpha)
  }

  /** `text` centred on (cx, cy): across on its width, down on the middle of its capitals. */
  def drawCentred(batch: SpriteBatch, text: String, cx: Float, cy: Float, face: Face, alpha: Float,
                  scale: Float = 1f): Float =
    draw(batch, text, cx - measure(text, scale) * 0.5f, cy + capHeight * scale * 0.5f, face, alpha, scale)

  /**
   * Light passing over a line drawn with [[draw]] at the same place: each letter's face again, in white
   * at `a` where `litX` is, fading to nothing `reach` either side of it, so a sweep of `litX` across
   * the line lights its letters one after another. Drawn by the caller with the batch's additive blend.
   */
  def drawGlint(batch: SpriteBatch, text: String, x: Float, y: Float, litX: Float, reach: Float, a: Float,
                scale: Float = 1f): Unit = {
    if (a <= 0f || reach <= 0f) return
    var px = x
    var i = 0
    while (i < text.length) {
      val cp = text.codePointAt(i)
      i += Character.charCount(cp)
      val g = glyph(cp)
      val l = g.letter
      if (g.face != null) {
        val gx = px + l.ox * unit * scale
        val gw = l.w * unit * scale
        val lit = 1f - Math.abs(gx + gw * 0.5f - litX) / reach
        if (lit > 0f) batch.draw(g.face, gx, y + l.oy * unit * scale, gw, l.h * unit * scale, 1f, 1f, 1f, a * lit)
      }
      px += (l.advance * unit + tracking) * scale
    }
  }

  private def inkPass(batch: SpriteBatch, text: String, x: Float, y: Float, scale: Float, face: Face, a: Float): Unit = {
    var px = x
    var i = 0
    while (i < text.length) {
      val cp = text.codePointAt(i)
      i += Character.charCount(cp)
      val g = glyph(cp)
      val l = g.letter
      if (g.ink != null)
        batch.draw(g.ink, px + l.ox * unit * scale, y + l.oy * unit * scale, l.w * unit * scale, l.h * unit * scale,
          face.inkR, face.inkG, face.inkB, a)
      px += (l.advance * unit + tracking) * scale
    }
  }

  private def facePass(batch: SpriteBatch, text: String, x: Float, y: Float, scale: Float, face: Face, a: Float): Float = {
    // The gradient's stops down a letter, from its baseline: the top of the capitals, their middle, the foot
    val sTop = y - capHeight * scale
    val sMid = y - capHeight * scale * 0.5f
    val sBot = y
    var px = x
    var i = 0
    while (i < text.length) {
      val cp = text.codePointAt(i)
      i += Character.charCount(cp)
      val g = glyph(cp)
      val l = g.letter
      if (g.face != null) {
        val gx = px + l.ox * unit * scale
        val top = y + l.oy * unit * scale
        val gw = l.w * unit * scale
        val gh = l.h * unit * scale
        val bottom = top + gh
        // Cut where the stops fall inside the letter, each band tinted from its top's colour to its foot's
        var ya = top
        var k = 0
        while (k < 4 && ya < bottom) {
          val cut = if (k == 0) sTop else if (k == 1) sMid else if (k == 2) sBot else bottom
          val yb = Math.min(bottom, cut)
          if (yb > ya) {
            band(batch, g.face, gx, gw, top, gh, ya, yb, sTop, sMid, sBot, face, a)
            ya = yb
          }
          k += 1
        }
      }
      px += (l.advance * unit + tracking) * scale
    }
    px - x - (if (text.nonEmpty) tracking * scale else 0f)
  }

  // The colour of a face at height yy: the top's above the capitals, its colour through their middle,
  // the foot's below the baseline
  private var _cr = 0f; private var _cg = 0f; private var _cb = 0f
  private def faceAt(yy: Float, sTop: Float, sMid: Float, sBot: Float, f: Face): Unit = {
    if (yy <= sTop) { _cr = f.topR; _cg = f.topG; _cb = f.topB }
    else if (yy <= sMid) {
      val t = (yy - sTop) / Math.max(1e-4f, sMid - sTop)
      _cr = f.topR + (f.midR - f.topR) * t; _cg = f.topG + (f.midG - f.topG) * t; _cb = f.topB + (f.midB - f.topB) * t
    } else if (yy <= sBot) {
      val t = (yy - sMid) / Math.max(1e-4f, sBot - sMid)
      _cr = f.midR + (f.footR - f.midR) * t; _cg = f.midG + (f.footG - f.midG) * t; _cb = f.midB + (f.footB - f.midB) * t
    } else { _cr = f.footR; _cg = f.footG; _cb = f.footB }
  }

  private def band(batch: SpriteBatch, r: TextureRegion, gx: Float, gw: Float, top: Float, gh: Float,
                   ya: Float, yb: Float, sTop: Float, sMid: Float, sBot: Float, f: Face, a: Float): Unit = {
    val v0 = r.v + (r.v2 - r.v) * ((ya - top) / gh)
    val v1 = r.v + (r.v2 - r.v) * ((yb - top) / gh)
    faceAt(ya, sTop, sMid, sBot, f)
    val r0 = _cr; val g0 = _cg; val b0 = _cb
    faceAt(yb, sTop, sMid, sBot, f)
    batch.drawGradient(r.texture, gx, ya, gw, yb - ya, r.u, v0, r.u2, v1, r0, g0, b0, a, _cr, _cg, _cb, a)
  }

  def dispose(): Unit = { pages.foreach(_.dispose()); pages.clear() }
}

object DisplayFont {
  private val PageW = 1024
  private val PageH = 512
  private val Pad = 2
  private val Frc = new FontRenderContext(null, true, true)

  /**
   * A face's colours: its top, middle and foot (pale, its colour, deep), and its ink, which also
   * casts the shadow. The menus' own are the scoreboard's: gold, sky, leaf and dusk.
   */
  final class Face(val topR: Float, val topG: Float, val topB: Float,
                   val midR: Float, val midG: Float, val midB: Float,
                   val footR: Float, val footG: Float, val footB: Float,
                   val inkR: Float, val inkG: Float, val inkB: Float,
                   val shadowAlpha: Float = 1f)

  object Face {
    private def c(hex: String, shift: Int): Float = ((Integer.parseInt(hex.stripPrefix("#"), 16) >> shift) & 0xFF) / 255f
    def of(top: String, mid: String, foot: String, ink: String): Face =
      new Face(c(top, 16), c(top, 8), c(top, 0), c(mid, 16), c(mid, 8), c(mid, 0),
        c(foot, 16), c(foot, 8), c(foot, 0), c(ink, 16), c(ink, 8), c(ink, 0))

    // The scoreboard's titles' (ui/screens/ScoreboardScreen)
    val Gold: Face = of("#fff4b8", "#ffd447", "#f5a01a", "#140c02")
    val Sky: Face = of("#ffffff", "#cfe9ff", "#6fc0ff", "#060a1a")
    val Leaf: Face = of("#f0fff4", "#a9f2c2", "#45d27e", "#03200e")
    val Dusk: Face = of("#f6f8fd", "#c9d2e6", "#8b97b5", "#060a1a")
    // And the HUD's, for a run of kills heating up and a death
    val Ember: Face = of("#fff1d6", "#ffb34a", "#f2691c", "#1a0802")
    val Berry: Face = of("#ffe9ed", "#ff8fa0", "#e8344f", "#1c0308")
    val Violet: Face = of("#f5f0ff", "#c8b4ff", "#8758ff", "#0d0622")
  }

  /**
   * One letter's two pictures, as coverage a byte a pixel, row by row from the top: its `ink` (the
   * letter grown by the outline, joined round) and its `face`, both `w` x `h` raster pixels, the
   * picture's top left `ox`, `oy` raster pixels from the pen on the baseline. `advance` is how far
   * the pen moves on, in raster pixels. A letter with nothing to draw (a space) is 0 x 0.
   */
  final class Letter(val ink: Array[Byte], val face: Array[Byte], val w: Int, val h: Int,
                     val ox: Int, val oy: Int, val advance: Float)

  /** The first face in `fonts` that has `cp`, as GLFontRenderer picks one. */
  private def pick(fonts: Array[Font], cp: Int): Font = {
    var i = 0
    while (i < fonts.length) {
      if (fonts(i).canDisplay(cp)) return fonts(i)
      i += 1
    }
    fonts(0)
  }

  /**
   * One letter rasterized: its outline taken from the font and filled for its face, and stroked
   * `outlinePx` out all round for its ink, on a picture with a pixel clear all round the ink, so
   * filtering never reads past it and nothing of an italic letter's lean is cut off.
   */
  def rasterize(fonts: Array[Font], cp: Int, outlinePx: Float, unit: Float): Letter = {
    val font = pick(fonts, cp)
    val gv = font.createGlyphVector(Frc, new String(Character.toChars(cp)))
    val advance = gv.getGlyphMetrics(0).getAdvanceX
    val shape = gv.getOutline(0f, 0f)
    val b = shape.getBounds2D
    if (b.isEmpty || b.getWidth <= 0 || b.getHeight <= 0) return new Letter(Array.emptyByteArray, Array.emptyByteArray, 0, 0, 0, 0, advance)
    val m = outlinePx + 2f
    val x0 = Math.floor(b.getMinX - m).toInt; val y0 = Math.floor(b.getMinY - m).toInt
    val x1 = Math.ceil(b.getMaxX + m).toInt; val y1 = Math.ceil(b.getMaxY + m).toInt
    val w = x1 - x0; val h = y1 - y0
    def picture(ink: Boolean): Array[Byte] = {
      val img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
      val g = img.createGraphics()
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
      g.translate(-x0, -y0)
      g.setColor(Color.WHITE)
      g.fill(shape)
      if (ink) {
        g.setStroke(new BasicStroke(outlinePx * 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND))
        g.draw(shape)
      }
      g.dispose()
      val argb = img.getRGB(0, 0, w, h, null, 0, w)
      val out = new Array[Byte](w * h)
      var i = 0
      while (i < out.length) { out(i) = (argb(i) >>> 24).toByte; i += 1 }
      out
    }
    new Letter(picture(ink = true), picture(ink = false), w, h, x0, y0, advance)
  }

  /** Exo 2 Black Italic, then the CJK faces the HUD's other text falls back on. */
  val chain: Array[Font] = {
    def load(resource: String): Option[Font] =
      try {
        val in = classOf[DisplayFont].getResourceAsStream(resource)
        if (in == null) None
        else try Some(Font.createFont(Font.TRUETYPE_FONT, in)) finally in.close()
      } catch { case _: Exception => None }
    val faces = Seq(load("/fonts/Exo2-BlackItalic.ttf"), load("/fonts/NotoSansSC-i18n.ttf"), load("/fonts/NotoSansKR-i18n.ttf")).flatten
    if (faces.isEmpty) Array(new Font(Font.SANS_SERIF, Font.BOLD | Font.ITALIC, 16)) else faces.toArray
  }
}
