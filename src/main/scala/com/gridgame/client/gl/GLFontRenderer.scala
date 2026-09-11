package com.gridgame.client.gl

import org.lwjgl.opengl.GL11._
import org.lwjgl.BufferUtils

import java.awt.image.BufferedImage
import java.awt.{Color, Font, FontMetrics, Graphics2D, RenderingHints}
import scala.collection.mutable

/**
 * Anti-aliased Unicode font renderer using Java AWT for glyph rasterization.
 *
 * Unlike a fixed baked ASCII atlas, glyphs are rasterized on demand and packed
 * into one or more GL texture pages, so any script the bundled fonts cover
 * (Latin, Simplified Chinese, Korean, ...) renders — this is what lets the
 * in-game HUD, player names, and chat display CJK text.
 *
 * Each requested code point is drawn with the first font in [[GLFontRenderer.fontChain]]
 * that can display it (Exo 2 for Latin, Noto Sans SC for Han, Noto Sans KR for
 * Hangul), so a single renderer mixes scripts on one baseline.
 *
 * Draw text as textured quads via [[SpriteBatch]]. The public API
 * (charHeight, drawText*, measureWidth) is unchanged from the previous ASCII-only
 * renderer, so existing call sites are untouched.
 */
class GLFontRenderer(val fontSize: Int) {
  import GLFontRenderer._

  // Font fallback chain derived to this size. First font that canDisplay a
  // code point wins for that glyph.
  private val fonts: Array[Font] = fontChain.map(_.deriveFont(Font.BOLD, fontSize.toFloat))

  // Shared graphics used only for measuring metrics.
  private val metricsG: Graphics2D = {
    val img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
    img.createGraphics()
  }
  private val metricsByFont: Array[FontMetrics] = fonts.map(metricsG.getFontMetrics)

  // Common baseline + line box sized to the tallest font in the chain so mixed
  // Latin/CJK text shares a baseline and nothing clips.
  private val ascent: Int = metricsByFont.map(_.getAscent).max
  val charHeight: Int = metricsByFont.map(_.getHeight).max + 2

  private final class Glyph(val region: TextureRegion, val advance: Int, val width: Int)

  // Looked up once per character drawn, so the lookup must not allocate: Latin-1 (nearly
  // all HUD text) sits in a flat table, and everything else in a LongMap, which takes the
  // code point unboxed. The HashMap[Int, Glyph] this replaces boxed every CJK code point
  // and built a closure for its getOrElseUpdate default on every character.
  private val latinGlyphs = new Array[Glyph](256)
  private val glyphs = mutable.LongMap.empty[Glyph]

  // Dynamic atlas: shelf-packed pages grown on demand.
  private val pages = mutable.ArrayBuffer[GLTexture]()
  private var penX = Pad
  private var penY = Pad
  private var rowH = 0

  newPage()

  private def newPage(): Unit = {
    pages += GLTexture.createEmpty(AtlasDim, AtlasDim, nearest = false)
    penX = Pad
    penY = Pad
    rowH = 0
  }

  private def pickFont(cp: Int): (Font, FontMetrics) = {
    var i = 0
    while (i < fonts.length) {
      if (fonts(i).canDisplay(cp)) return (fonts(i), metricsByFont(i))
      i += 1
    }
    (fonts(0), metricsByFont(0))
  }

  private def rasterize(cp: Int): Glyph = {
    val (font, fm) = pickFont(cp)
    val advance = math.max(0, fm.charWidth(cp))
    val gw = math.max(1, advance)
    val gh = charHeight

    // Rasterize the single glyph to a transparent ARGB tile.
    val img = new BufferedImage(gw, gh, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    g.setFont(font)
    g.setColor(Color.WHITE)
    if (cp != ' '.toInt) g.drawString(new String(Character.toChars(cp)), 0, ascent)
    g.dispose()

    // Advance the shelf cursor, wrapping rows / adding pages as needed.
    if (penX + gw + Pad > AtlasDim) { penX = Pad; penY += rowH + Pad; rowH = 0 }
    if (penY + gh + Pad > AtlasDim) newPage()
    val page = pages.last
    uploadGlyph(page, penX, penY, img)
    val region = page.region(penX, penY, gw, gh)
    penX += gw + Pad
    rowH = math.max(rowH, gh)

    new Glyph(region, advance, gw)
  }

  private def uploadGlyph(page: GLTexture, x: Int, y: Int, img: BufferedImage): Unit = {
    val w = img.getWidth
    val h = img.getHeight
    val px = img.getRGB(0, 0, w, h, null, 0, w)
    val buf = BufferUtils.createByteBuffer(w * h * 4)
    var i = 0
    while (i < px.length) {
      val p = px(i)
      buf.put(((p >> 16) & 0xFF).toByte) // r
        .put(((p >> 8) & 0xFF).toByte)   // g
        .put((p & 0xFF).toByte)          // b
        .put(((p >> 24) & 0xFF).toByte)  // a
      i += 1
    }
    buf.flip()
    glBindTexture(GL_TEXTURE_2D, page.id)
    glTexSubImage2D(GL_TEXTURE_2D, 0, x, y, w, h, GL_RGBA, GL_UNSIGNED_BYTE, buf)
  }

  private def glyph(cp: Int): Glyph = {
    if (cp >= 0 && cp < 256) {
      var g = latinGlyphs(cp)
      if (g == null) { g = rasterize(cp); latinGlyphs(cp) = g }
      g
    } else {
      var g = glyphs.getOrNull(cp.toLong)
      if (g == null) { g = rasterize(cp); glyphs.update(cp.toLong, g) }
      g
    }
  }

  /**
   * Pre-rasterize a set of code points (e.g. every glyph in the active language
   * catalog) so the first frame that shows them doesn't hitch. Must be called on
   * the GL thread.
   */
  def prewarm(codepoints: Array[Int]): Unit = {
    var i = 0
    while (i < codepoints.length) { glyph(codepoints(i)); i += 1 }
  }

  /** Draw text using a SpriteBatch. Returns the total width drawn. */
  def drawText(batch: SpriteBatch, text: String, x: Float, y: Float,
               r: Float = 1f, g: Float = 1f, b: Float = 1f, a: Float = 1f): Float = {
    var cx = x
    var i = 0
    val h = charHeight.toFloat
    while (i < text.length) {
      val cp = text.codePointAt(i)
      i += Character.charCount(cp)
      val gl = glyph(cp)
      if (gl.advance > 0) batch.draw(gl.region, cx, y, gl.width.toFloat, h, r, g, b, a)
      cx += gl.advance
    }
    cx - x
  }

  /** Draw text with a dark drop shadow for readability. */
  def drawTextShadow(batch: SpriteBatch, text: String, x: Float, y: Float,
                     r: Float = 1f, g: Float = 1f, b: Float = 1f, a: Float = 1f): Float = {
    val offset = Math.max(1f, fontSize / 16f)
    drawText(batch, text, x + offset, y + offset, 0f, 0f, 0f, a * 0.6f)
    drawText(batch, text, x, y, r, g, b, a)
  }

  /** Draw outlined text (text with dark outline for strong readability).
   * Single-pass: iterates string once, drawing 5 quads per glyph (4 outline + 1 foreground). */
  def drawTextOutlined(batch: SpriteBatch, text: String, x: Float, y: Float,
                       r: Float = 1f, g: Float = 1f, b: Float = 1f, a: Float = 1f): Float = {
    val o = Math.max(1f, fontSize / 24f)
    val oa = a * 0.7f
    val ch = charHeight.toFloat
    var cx = x
    var i = 0
    while (i < text.length) {
      val cp = text.codePointAt(i)
      i += Character.charCount(cp)
      val gl = glyph(cp)
      if (gl.advance > 0) {
        val region = gl.region
        val wF = gl.width.toFloat
        batch.draw(region, cx - o, y, wF, ch, 0f, 0f, 0f, oa)
        batch.draw(region, cx + o, y, wF, ch, 0f, 0f, 0f, oa)
        batch.draw(region, cx, y - o, wF, ch, 0f, 0f, 0f, oa)
        batch.draw(region, cx, y + o, wF, ch, 0f, 0f, 0f, oa)
        batch.draw(region, cx, y, wF, ch, r, g, b, a)
      }
      cx += gl.advance
    }
    cx - x
  }

  /** Measure the width of a string in pixels. */
  def measureWidth(text: String): Float = {
    var w = 0f
    var i = 0
    while (i < text.length) {
      val cp = text.codePointAt(i)
      i += Character.charCount(cp)
      w += glyph(cp).advance
    }
    w
  }

  def dispose(): Unit = pages.foreach(_.dispose())
}

object GLFontRenderer {
  private val AtlasDim = 1024
  private val Pad = 2

  private def loadFont(resource: String): Option[Font] =
    try {
      val stream = getClass.getResourceAsStream(resource)
      if (stream == null) None
      else {
        val f = Font.createFont(Font.TRUETYPE_FONT, stream)
        stream.close()
        Some(f)
      }
    } catch { case _: Exception => None }

  /**
   * Ordered font fallback chain, loaded once from bundled TTFs. Exo 2 (Latin)
   * first for its game aesthetic, then Noto Sans SC (Han) and KR (Hangul) for
   * CJK coverage. Falls back to system sans-serif if a resource is missing.
   */
  val fontChain: Array[Font] = {
    val chain = Seq(
      loadFont("/fonts/Exo2-Bold.ttf"),
      loadFont("/fonts/NotoSansSC-i18n.ttf"),
      loadFont("/fonts/NotoSansKR-i18n.ttf")
    ).flatten.toArray
    if (chain.isEmpty) Array(new Font(Font.SANS_SERIF, Font.BOLD, 16)) else chain
  }

  /** Primary (Latin) font — retained for any callers that want a base AWT font. */
  val gameFont: Font = fontChain.head
}
