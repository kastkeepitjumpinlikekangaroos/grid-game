package com.gridgame.client.gl

import org.lwjgl.opengl.GL11._
import org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE
import org.lwjgl.BufferUtils

import java.awt.{Color, Font, RenderingHints}
import java.awt.font.FontRenderContext
import java.awt.image.BufferedImage

/**
 * Anti-aliased font renderer using Java AWT for glyph rasterization.
 * Renders a glyph atlas at construction time using a system sans-serif font,
 * then draws text as textured quads via SpriteBatch.
 *
 * Supports proportional character widths for natural-looking text.
 */
class GLFontRenderer(val fontSize: Int) {
  private val FIRST_CHAR = 32
  private val LAST_CHAR = 126
  private val CHAR_COUNT = LAST_CHAR - FIRST_CHAR + 1
  private val ATLAS_COLS = 16
  private val ATLAS_ROWS = (CHAR_COUNT + ATLAS_COLS - 1) / ATLAS_COLS

  // Use a clean sans-serif font
  private val awtFont = new Font(Font.SANS_SERIF, Font.BOLD, fontSize)

  // Measure character metrics using AWT
  private val metrics = {
    val img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.setFont(awtFont)
    val m = g.getFontMetrics
    g.dispose()
    m
  }

  // Per-character advance widths
  private val charAdvances = new Array[Int](CHAR_COUNT)
  for (i <- 0 until CHAR_COUNT) {
    charAdvances(i) = metrics.charWidth((FIRST_CHAR + i).toChar)
  }

  // Cell dimensions for atlas (use max char width + padding)
  private val cellW = metrics.getMaxAdvance.max(metrics.charWidth('W')) + 2
  val charHeight: Int = metrics.getHeight + 2

  // Atlas dimensions
  private val atlasW = nextPow2(ATLAS_COLS * cellW)
  private val atlasH = nextPow2(ATLAS_ROWS * charHeight)

  // Generate atlas texture
  val texture: GLTexture = generateAtlas()

  // Pre-compute texture regions for each character
  private val regions = new Array[TextureRegion](CHAR_COUNT)
  for (i <- 0 until CHAR_COUNT) {
    val col = i % ATLAS_COLS
    val row = i / ATLAS_COLS
    regions(i) = texture.region(col * cellW, row * charHeight, charAdvances(i), charHeight)
  }

  private def nextPow2(v: Int): Int = {
    var n = v - 1
    n |= n >> 1; n |= n >> 2; n |= n >> 4; n |= n >> 8; n |= n >> 16
    n + 1
  }

  private def generateAtlas(): GLTexture = {
    val img = new BufferedImage(atlasW, atlasH, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()

    // Enable high-quality anti-aliasing
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

    g.setFont(awtFont)
    g.setColor(Color.WHITE)

    val ascent = metrics.getAscent

    for (i <- 0 until CHAR_COUNT) {
      val ch = (FIRST_CHAR + i).toChar
      val col = i % ATLAS_COLS
      val row = i / ATLAS_COLS
      val x = col * cellW
      val y = row * charHeight + ascent
      g.drawString(ch.toString, x, y)
    }

    g.dispose()

    // Upload to OpenGL
    val pixels = img.getRGB(0, 0, atlasW, atlasH, null, 0, atlasW)
    val buf = BufferUtils.createByteBuffer(atlasW * atlasH * 4)
    for (pixel <- pixels) {
      val a = (pixel >> 24) & 0xFF
      val r = (pixel >> 16) & 0xFF
      val gg = (pixel >> 8) & 0xFF
      val b = pixel & 0xFF
      buf.put(r.toByte).put(gg.toByte).put(b.toByte).put(a.toByte)
    }
    buf.flip()

    val texId = glGenTextures()
    glBindTexture(GL_TEXTURE_2D, texId)
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, atlasW, atlasH, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)

    new GLTexture(texId, atlasW, atlasH, false)
  }

  /** Get the region for a character, or space if not in range. */
  def getRegion(ch: Char): TextureRegion = {
    val idx = ch.toInt - FIRST_CHAR
    if (idx >= 0 && idx < CHAR_COUNT) regions(idx)
    else regions(0)
  }

  /** Get the advance width for a character. */
  def getAdvance(ch: Char): Int = {
    val idx = ch.toInt - FIRST_CHAR
    if (idx >= 0 && idx < CHAR_COUNT) charAdvances(idx)
    else charAdvances(0)
  }

  /** Draw text using a SpriteBatch. Returns the total width drawn. */
  def drawText(batch: SpriteBatch, text: String, x: Float, y: Float,
               r: Float = 1f, g: Float = 1f, b: Float = 1f, a: Float = 1f): Float = {
    var cx = x
    var i = 0
    while (i < text.length) {
      val ch = text.charAt(i)
      val region = getRegion(ch)
      val adv = getAdvance(ch)
      batch.draw(region, cx, y, adv.toFloat, charHeight.toFloat, r, g, b, a)
      cx += adv
      i += 1
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
   * Single-pass: iterates string once, drawing 5 quads per character (4 outline + 1 foreground).
   * This avoids 5x string iteration, 5x getRegion/getAdvance lookups per character. */
  def drawTextOutlined(batch: SpriteBatch, text: String, x: Float, y: Float,
                       r: Float = 1f, g: Float = 1f, b: Float = 1f, a: Float = 1f): Float = {
    val o = Math.max(1f, fontSize / 24f)
    val oa = a * 0.7f
    val ch = charHeight.toFloat
    var cx = x
    var i = 0
    while (i < text.length) {
      val c = text.charAt(i)
      val region = getRegion(c)
      val adv = getAdvance(c)
      val advF = adv.toFloat
      // Outline (4 offsets)
      batch.draw(region, cx - o, y, advF, ch, 0f, 0f, 0f, oa)
      batch.draw(region, cx + o, y, advF, ch, 0f, 0f, 0f, oa)
      batch.draw(region, cx, y - o, advF, ch, 0f, 0f, 0f, oa)
      batch.draw(region, cx, y + o, advF, ch, 0f, 0f, 0f, oa)
      // Foreground
      batch.draw(region, cx, y, advF, ch, r, g, b, a)
      cx += adv
      i += 1
    }
    cx - x
  }

  /** Measure the width of a string in pixels. */
  def measureWidth(text: String): Float = {
    var w = 0f
    var i = 0
    while (i < text.length) {
      w += getAdvance(text.charAt(i))
      i += 1
    }
    w
  }

  def dispose(): Unit = texture.dispose()
}
