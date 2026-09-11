package com.gridgame.client.ui

import com.gridgame.common.Constants
import com.gridgame.common.model.CharacterDef
import com.gridgame.common.model.Direction

import javafx.scene.canvas.GraphicsContext
import javafx.scene.image.Image
import javafx.stage.Screen

import java.io.File

/**
 * Character sprites for the JavaFX screens (character select, ability previews).
 *
 * Keeps whole sheets and draws a frame out of one with a source rectangle. It used to cut
 * every sheet into 16 separate 128px WritableImages and keep them all for the life of the
 * process: browsing the character grid loaded the whole roster, which came to 117MB of
 * heap and a GPU texture per frame (1792 of them), none of which was ever released.
 *
 * The grid draws each character at [[ThumbDisplayPx]], so its sheets are decoded straight
 * to that size (times the display's scale) — a quarter of the pixels on a HiDPI screen and
 * a sixteenth on a 1x one — and on JavaFX's background loader, so opening the grid doesn't
 * stall the FX thread for the few hundred ms it takes to decode the roster. Full-resolution
 * sheets are kept only for the last few characters shown large.
 */
object SpriteGenerator {
  private val frameSize = Constants.SPRITE_SIZE_PX // 128
  private val framesPerDirection = 4
  private val sheetSize = frameSize * framesPerDirection

  /** Size, in logical pixels, the character grid draws a sprite at. */
  val ThumbDisplayPx = 34.0

  // Device pixels per logical pixel on the densest screen, so a thumbnail is never
  // magnified. Read on first use, which is on the FX thread once the toolkit is up.
  private lazy val outputScale: Double = {
    var s = 1.0
    Screen.getScreens.forEach(sc => s = Math.max(s, Math.max(sc.getOutputScaleX, sc.getOutputScaleY)))
    s
  }
  private lazy val thumbFramePx: Int = Math.min(frameSize, Math.ceil(ThumbDisplayPx * outputScale).toInt)

  // Row order in spritesheet: Down=0, Up=1, Left=2, Right=3 (the same as Direction.id)
  private def rowOf(direction: Direction): Int = direction.id

  private val thumbSheets = new java.util.HashMap[java.lang.Byte, Image]()
  private val MaxFullSheets = 4
  private val fullSheets = new java.util.LinkedHashMap[java.lang.Byte, Image](8, 0.75f, true) {
    override def removeEldestEntry(e: java.util.Map.Entry[java.lang.Byte, Image]): Boolean = size() > MaxFullSheets
  }
  // Characters whose sheet failed to load, so a missing file is looked for once, not per frame
  private val missing = new java.util.HashSet[java.lang.Byte]()

  private def loadSheet(characterId: Byte, px: Int, background: Boolean): Image = {
    val charDef = CharacterDef.get(characterId)
    val url = resolveResourceUrl(charDef.spriteSheet)
    if (url == null) {
      System.err.println(s"Spritesheet not found: ${charDef.spriteSheet}")
      missing.add(characterId)
      return null
    }
    val side = Math.min(px * framesPerDirection, sheetSize).toDouble
    new Image(url, side, side, false, true, background)
  }

  private def sheetFor(characterId: Byte, displaySize: Double): Image = {
    val key = java.lang.Byte.valueOf(characterId)
    if (missing.contains(key)) return null
    val img =
      if (displaySize * outputScale <= thumbFramePx + 0.5) {
        var t = thumbSheets.get(key)
        if (t == null) {
          t = loadSheet(characterId, thumbFramePx, background = true)
          if (t != null) thumbSheets.put(key, t)
        }
        t
      } else {
        var f = fullSheets.get(key)
        if (f == null) {
          f = loadSheet(characterId, frameSize, background = false)
          if (f != null) fullSheets.put(key, f)
        }
        f
      }
    if (img != null && img.isError) {
      System.err.println(s"Spritesheet failed to load: ${CharacterDef.get(characterId).spriteSheet}")
      missing.add(key)
      thumbSheets.remove(key)
      fullSheets.remove(key)
      return null
    }
    img
  }

  /** Whether [[drawFrame]] at this size would draw now rather than wait on a background load. */
  def isReady(characterId: Byte, size: Double): Boolean = {
    val sheet = sheetFor(characterId, size)
    sheet == null || sheet.getProgress >= 1.0
  }

  /**
   * Draw one animation frame of a character into a `size` x `size` square at (x, y).
   * Returns false when the sheet is still loading in the background and nothing was drawn,
   * so the caller knows to try again on a later frame.
   */
  def drawFrame(gc: GraphicsContext, characterId: Byte, direction: Direction, frame: Int,
                x: Double, y: Double, size: Double): Boolean = {
    val sheet = sheetFor(characterId, size)
    if (sheet == null) return true // missing: nothing will ever draw, don't retry
    if (sheet.getProgress < 1.0) return false
    val cell = sheet.getWidth / framesPerDirection
    val col = frame % framesPerDirection
    gc.drawImage(sheet, col * cell, rowOf(direction) * cell, cell, cell, x, y, size, size)
    true
  }

  private def resolveResourceUrl(relativePath: String): String = {
    val direct = new File(relativePath)
    if (direct.exists()) return direct.toURI.toString

    val buildWorkDir = System.getenv("BUILD_WORKING_DIRECTORY")
    if (buildWorkDir != null) {
      val fromWorkDir = new File(buildWorkDir, relativePath)
      if (fromWorkDir.exists()) return fromWorkDir.toURI.toString
    }

    // Try classpath (bundled in JAR)
    val res = getClass.getClassLoader.getResource(relativePath)
    if (res != null) res.toExternalForm else null
  }

  /** Drop every cached sheet (they reload on demand). Called when a match starts, since
    * nothing on the JavaFX side is drawn while the game window is up. */
  def clearCache(): Unit = {
    thumbSheets.clear()
    fullSheets.clear()
    missing.clear()
  }
}
