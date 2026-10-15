package com.gridgame.client.ui

import com.gridgame.common.WorldRegistry
import com.gridgame.common.model.{Tile, WorldData}
import com.gridgame.common.world.WorldLoader

import javafx.application.Platform
import javafx.scene.Node
import javafx.scene.image.{Image, ImageView}
import javafx.scene.layout.StackPane
import javafx.scene.shape.Rectangle

import java.util.concurrent.{LinkedBlockingQueue, ThreadPoolExecutor, TimeUnit}

/**
 * The maps for the menus (the lobby list, the lobby form, the lobby room): where a match on the
 * map is played and a rim of its border round that (the forest, the sea, the void), drawn as the
 * match draws it, seen from high above and standing on a slab of its own ground like a model of
 * it, on a dark stage (MapArt). All of the arena, always: the lobby form once cut a square map to
 * fill a wide frame, which hid half of it.
 *
 * A picture is drawn for each size it is shown at, at twice that size for a Retina display, on a
 * thread of its own, and handed over on the FX thread: the frame shows its stage until then. The
 * big ones are kept for the last few maps shown; each map is read from its file once.
 */
private[client] object MapPreview {
  // On the FX thread: the pictures drawn, by what and how big, and who is waiting for one
  private val pictures = new java.util.LinkedHashMap[String, Image](16, 0.75f, true)
  private val waiting = new java.util.HashMap[String, java.util.ArrayList[Image => Unit]]()
  /** 12MB of pictures: the lobby form's are 0.9MB each, a lobby row's 0.07MB. */
  private val MaxPixels = 3_000_000L
  private var pixels = 0L
  // Bumped by clearCache: a picture asked for before it is dropped as it lands
  private var generation = 0

  // On the painter's thread: each map, read once
  private val worlds = new java.util.HashMap[Integer, WorldData]()
  // One thread, which lets the tile art go whenever it runs out of pictures to draw
  private lazy val painter: ThreadPoolExecutor =
    new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue[Runnable](), { (r: Runnable) =>
      val t = new Thread(r, "map-art")
      t.setDaemon(true)
      t
    }) {
      override def afterExecute(r: Runnable, t: Throwable): Unit = if (getQueue.isEmpty) MapArt.release()
    }

  /** How deep the slab under a map is, as a share of how tall the map is drawn. */
  private val BaseShare = 0.035
  /** How much of a map's border is shown round where its matches are played, as a share of that. */
  private val Rim = 0.06

  /** Drop every picture (they are drawn again when next shown), and any being drawn as it lands.
    * Called when a match starts: nothing on the JavaFX side is shown while the game window is up,
    * and the pictures are up to 12MB. */
  def clearCache(): Unit = {
    generation += 1
    pictures.clear()
    waiting.clear()
    pixels = 0
  }

  /** Run `task` on the thread the pictures are drawn on (WorldBackdrop's is drawn there too). */
  def paint(task: Runnable): Unit = painter.execute(task)

  /** Wait, off the FX thread, until every picture asked for so far is drawn: for the gallery. Each
    * is handed over on the FX thread after, in the order asked. */
  def settle(): Unit = painter.submit(new Runnable { def run(): Unit = () }).get(60, TimeUnit.SECONDS)

  private def world(mapIndex: Int): WorldData = {
    val key = Integer.valueOf(mapIndex)
    var w = worlds.get(key)
    if (w == null) {
      w = try WorldLoader.load("worlds/" + WorldRegistry.getFilename(mapIndex)) catch { case _: Exception => null }
      if (w != null) worlds.put(key, w)
    }
    w
  }

  /** The picture `key` names, `pw` x `ph` pixels, for `ready` on the FX thread: at once if it is
    * drawn, else once `draw` has drawn it on the painter's thread (null if it can't be). */
  private def picture(key: String, pw: Int, ph: Int, draw: () => Array[Int])(ready: Image => Unit): Unit = {
    val done = pictures.get(key)
    if (done != null) { ready(done); return }
    val queued = waiting.get(key)
    if (queued != null) { queued.add(ready); return }
    val list = new java.util.ArrayList[Image => Unit]()
    list.add(ready)
    waiting.put(key, list)
    val gen = generation
    painter.execute { () =>
      val img: Image =
        try { val px = draw(); if (px == null) null else MapArt.image(px, pw, ph) }
        catch { case e: Exception => e.printStackTrace(); null }
      Platform.runLater { () =>
        val callbacks = if (gen == generation) waiting.remove(key) else null
        if (img != null && gen == generation) {
          pictures.put(key, img)
          pixels += pw.toLong * ph
          // The least lately shown go first, never the one just drawn
          val it = pictures.entrySet().iterator()
          while (pixels > MaxPixels && it.hasNext) {
            val e = it.next()
            if (e.getKey != key) {
              pixels -= (e.getValue.getWidth * e.getValue.getHeight).toLong
              it.remove()
            }
          }
          if (callbacks != null) callbacks.forEach(_(img))
        }
      }
    }
  }

  /**
   * The map in a `w` x `h` frame with rounded corners, on the stage the sheet gives `.map-frame`;
   * drawn in as soon as its picture is ready.
   */
  def view(mapIndex: Int, w: Double, h: Double, radius: Double = 12): Node = {
    val frame = new StackPane()
    frame.getStyleClass.add("map-frame")
    frame.setStyle(s"-fx-background-radius: $radius;")
    frame.setMinSize(w, h); frame.setPrefSize(w, h); frame.setMaxSize(w, h)
    val clip = new Rectangle(w, h)
    clip.setArcWidth(radius * 2); clip.setArcHeight(radius * 2)
    frame.setClip(clip)
    val view = new ImageView()
    view.setFitWidth(w); view.setFitHeight(h)
    view.setSmooth(true)
    frame.getChildren.add(view)
    val pw = Math.ceil(w * 2).toInt; val ph = Math.ceil(h * 2).toInt
    picture(s"map$mapIndex:${pw}x$ph", pw, ph, () => {
      val wd = world(mapIndex)
      if (wd == null) null
      else {
        val win = MapArt.playArea(wd, Rim)
        val (_, top, _, bottom) = MapArt.bounds(win)
        val base = (bottom - top) * BaseShare
        // Small pictures get more samples a pixel: the cost is in the pixels, and they have few
        val px = MapArt.draw(wd, win, pw, ph, MapArt.fit(win, pw, ph, 0.05, base), if (pw * ph < 120_000) 3 else 2, base)
        MapArt.light(px, pw, ph)
        px
      }
    })(view.setImage)
    frame
  }

  /** How far down a [[patch]]'s box its middle cell's centre is, where a character's feet go. */
  val PatchFeet = 0.44

  /**
   * A patch of the Meadow's grass `cells` square on its slab, drawn as the maps are, for a
   * character to stand on: as big as fits a `w` x `h` box (about 1.75 to 1), in the middle of it.
   */
  def patch(cells: Int, w: Double, h: Double): ImageView = {
    val view = new ImageView()
    view.setFitWidth(w); view.setFitHeight(h)
    view.setSmooth(true)
    view.setMouseTransparent(true)
    val pw = Math.ceil(w * 2).toInt; val ph = Math.ceil(h * 2).toInt
    picture(s"patch$cells:${pw}x$ph", pw, ph, () => {
      // Grass with a flower here and there, as the Meadow's is
      val tiles = Array.tabulate[Tile](cells, cells)((y, x) => if ((x * 5 + y * 3) % 7 == 3) Tile.Flowers else Tile.Grass)
      val wd = new WorldData("patch", cells, cells, tiles, Seq.empty, "sky")
      val win = MapArt.whole(wd)
      val base = 9.0
      MapArt.draw(wd, win, pw, ph, MapArt.fit(win, pw, ph, 0.02, base, rise = 0), 3, base)
    })(view.setImage)
    view
  }
}
