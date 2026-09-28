package com.gridgame.client.ui

import com.gridgame.common.Constants
import com.gridgame.common.model.CharacterDef
import com.gridgame.common.model.Direction

import javafx.application.Platform
import javafx.geometry.Rectangle2D
import javafx.scene.image.{Image, ImageView, WritableImage}

import java.io.File
import java.util.concurrent.{ExecutorService, Executors}

/**
 * Character sprites for the menus, in two forms:
 *
 *  - **Portraits**: a character standing still, one frame cut out of its sheet. The character grid
 *    shows the whole roster this way, and so do the scoreboard and the login screen. A portrait is
 *    the sheet's own 128-pixel frame, 64KB, 7MB for the roster. They are cut on a thread of their
 *    own and handed over on the FX thread, so opening the grid never stalls it.
 *  - **Sheets**: a character's whole sheet at full resolution, for the few that animate — the one
 *    picked (the detail panel walks it round) and the one under the mouse. Only the last few are
 *    kept, and they load in the background ([[Image.getProgress]]).
 *
 * The grid used to animate every cell from a sheet each, decoded at the cell's size: 33MB for the
 * roster, and a repaint of every visible cell six times a second. Before that it kept 16 separate
 * WritableImages per character forever: 117MB of heap and 1792 GPU textures.
 */
object SpriteGenerator {
  private val frameSize = Constants.SPRITE_SIZE_PX // 128
  private val framesPerDirection = 4

  /** The size of a portrait: the sheet's own frame, so the login screen's characters can be drawn
    * one for one (or their pixels exactly doubled on a Retina display). */
  val PortraitMaxPx: Double = frameSize

  // Row order in spritesheet: Down=0, Up=1, Left=2, Right=3 (the same as Direction.id)
  private def rowOf(direction: Direction): Int = direction.id

  // All on the FX thread, but for the cutting itself
  private val portraits = new java.util.HashMap[Integer, Image]()
  private val waiting = new java.util.HashMap[Integer, java.util.ArrayList[Image => Unit]]()
  private var generation = 0
  private lazy val cutter: ExecutorService = Executors.newSingleThreadExecutor { r =>
    val t = new Thread(r, "portrait-cutter")
    t.setDaemon(true)
    t
  }

  private val MaxSheets = 4
  private val sheets = new java.util.LinkedHashMap[java.lang.Byte, Image](8, 0.75f, true) {
    override def removeEldestEntry(e: java.util.Map.Entry[java.lang.Byte, Image]): Boolean = size() > MaxSheets
  }

  /**
   * A character standing still, facing `direction`, for `ready` — on the FX thread, at once if it
   * is already cut. Nothing is called if the character's sheet is missing.
   */
  def portrait(characterId: Byte, direction: Direction)(ready: Image => Unit): Unit = {
    val key = Integer.valueOf(((characterId & 0xFF) << 2) | rowOf(direction))
    val done = portraits.get(key)
    if (done != null) { ready(done); return }
    val queued = waiting.get(key)
    if (queued != null) { queued.add(ready); return }
    val url = resolveResourceUrl(CharacterDef.get(characterId).spriteSheet)
    if (url == null) {
      System.err.println(s"Spritesheet not found: ${CharacterDef.get(characterId).spriteSheet}")
      return
    }
    val list = new java.util.ArrayList[Image => Unit]()
    list.add(ready)
    waiting.put(key, list)
    val px = frameSize
    val row = rowOf(direction)
    val gen = generation
    cutter.execute { () =>
      val cut: Image = try {
        val sheet = new Image(url, false)
        if (sheet.isError) null else new WritableImage(sheet.getPixelReader, 0, row * px, px, px)
      } catch { case _: Exception => null }
      Platform.runLater { () =>
        // One cut before clearCache is dropped, leaving the waiting list to the cut asked for since
        if (gen == generation) {
          val callbacks = waiting.remove(key)
          if (cut != null) {
            portraits.put(key, cut)
            if (callbacks != null) callbacks.forEach(_(cut))
          }
        }
      }
    }
  }

  /** An image view showing a character's portrait at `size`, filled in once it is cut: smoothed
    * when it is drawn smaller than the frame, pixels kept square when drawn at its full size. */
  def portraitView(characterId: Byte, direction: Direction, size: Double): ImageView = {
    val view = new ImageView()
    view.setFitWidth(size)
    view.setFitHeight(size)
    view.setSmooth(size < PortraitMaxPx)
    view.setPreserveRatio(true)
    portrait(characterId, direction)(view.setImage)
    view
  }

  /** A character's whole sheet at full resolution, loading in the background: check its
    * progress before showing a frame of it. The last few asked for are kept. */
  def sheet(characterId: Byte): Image = {
    val key = java.lang.Byte.valueOf(characterId)
    var img = sheets.get(key)
    if (img == null) {
      val url = resolveResourceUrl(CharacterDef.get(characterId).spriteSheet)
      if (url == null) return null
      img = new Image(url, true)
      sheets.put(key, img)
    }
    if (img.isError) null else img
  }

  /** Where a frame is in a full-resolution sheet, for an ImageView's viewport. */
  def frame(direction: Direction, frame: Int): Rectangle2D =
    new Rectangle2D((frame % framesPerDirection) * frameSize.toDouble, rowOf(direction) * frameSize.toDouble,
      frameSize, frameSize)

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

  /** Drop every portrait and sheet (they reload on demand). Called when a match starts, since
    * nothing on the JavaFX side is drawn while the game window is up; a portrait still being cut
    * is dropped when it lands. */
  def clearCache(): Unit = {
    generation += 1
    portraits.clear()
    waiting.clear()
    sheets.clear()
  }
}
