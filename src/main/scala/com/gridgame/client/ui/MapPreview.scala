package com.gridgame.client.ui

import com.gridgame.common.WorldRegistry
import com.gridgame.common.model.{Tile, WorldData}
import com.gridgame.common.world.WorldLoader

import javafx.application.Platform
import javafx.scene.Node
import javafx.scene.image.{Image, ImageView}
import javafx.scene.layout.StackPane
import javafx.scene.shape.Rectangle

import java.util.concurrent.{CountDownLatch, PriorityBlockingQueue, ThreadPoolExecutor, TimeUnit}
import java.util.concurrent.atomic.AtomicLong

/**
 * The maps for the menus (the lobby list, the lobby form, the lobby room, the map carousel): where a
 * match on the map is played and a rim of its border round that (the forest, the sea, the void),
 * drawn as the match draws it, seen from high above and standing on a slab of its own ground like a
 * model of it, on a dark stage (MapArt). All of the arena, always: the lobby form once cut a square
 * map to fill a wide frame, which hid half of it.
 *
 * A picture is drawn for each size it is shown at, at twice that size for a Retina display, on a
 * thread of its own, and handed over on the FX thread: the frame shows its stage until then. The
 * big ones are kept for the last few maps shown, and the last few maps read from their files (a
 * carousel's strip reads every one, and a big map read is a quarter of a megabyte).
 *
 * The thread draws what is wanted soonest first ([[Now]], [[Strip]], [[Ahead]]), and in the order
 * asked within each: a map's picture shown on its own goes before the carousel's strip of every map,
 * which goes before the pictures of the maps the player may step to next.
 */
private[client] object MapPreview {
  /** How soon a picture is wanted: one the page shows on its own (a map's, a lobby's, the world
    * behind), drawn before... */
  val Now = 0
  /** ...the little ones in a carousel's strip, every map at once, before... */
  val Strip = 1
  /** ...the maps a carousel may step to next, drawn while nothing else is wanted. */
  val Ahead = 2

  // On the FX thread: the pictures drawn, by what and how big, who is waiting for one and what
  // will draw it, and how big each map read so far is
  private val pictures = new java.util.LinkedHashMap[String, Image](16, 0.75f, true)
  private val waiting = new java.util.HashMap[String, java.util.ArrayList[Image => Unit]]()
  private val jobs = new java.util.HashMap[String, Job]()
  private val sizes = new java.util.HashMap[Integer, (Int, Int)]()
  /** 12MB of pictures: the map picker's big one is 2.7MB, the lobby form's 0.9MB, a lobby row's
    * 0.07MB, the form's strip of every map 0.6MB all told. */
  private val MaxPixels = 3_000_000L
  private var pixels = 0L
  // Bumped by clearCache: a picture asked for before it is dropped as it lands
  private var generation = 0

  // On the painter's thread: the last few maps read
  private val worlds = new java.util.LinkedHashMap[Integer, WorldData](8, 0.75f, true) {
    override def removeEldestEntry(e: java.util.Map.Entry[Integer, WorldData]): Boolean = size > 4
  }

  /** Something for the painter to do: soonest wanted first, then first asked. A picture of a map
    * can be dropped before it is started ([[clearCache]]); what [[paint]] is given can't. */
  private final class Job(val urgency: Int, val order: Long, val body: Runnable, val droppable: Boolean) extends Runnable {
    def run(): Unit = body.run()
  }
  private val asked = new AtomicLong()
  private def job(urgency: Int, body: Runnable, droppable: Boolean): Job =
    new Job(urgency, asked.getAndIncrement(), body, droppable)

  // One thread, which lets the tile art go whenever it runs out of pictures to draw
  private lazy val painter: ThreadPoolExecutor = {
    val queue = new PriorityBlockingQueue[Runnable](32, (a: Runnable, b: Runnable) => {
      val (x, y) = (a.asInstanceOf[Job], b.asInstanceOf[Job])
      if (x.urgency != y.urgency) Integer.compare(x.urgency, y.urgency) else java.lang.Long.compare(x.order, y.order)
    })
    new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, queue, { (r: Runnable) =>
      val t = new Thread(r, "map-art")
      t.setDaemon(true)
      t
    }) {
      override def afterExecute(r: Runnable, t: Throwable): Unit = if (getQueue.isEmpty) MapArt.release()
    }
  }

  /** How deep the slab under a map is, as a share of how tall the map is drawn. */
  private val BaseShare = 0.035
  /** How much of a map's border is shown round where its matches are played, as a share of that. */
  private val Rim = 0.06

  /** Drop every picture (they are drawn again when next shown), any being drawn as it lands, and
    * every one not yet started. Called when a match starts: nothing on the JavaFX side is shown
    * while the game window is up, and the pictures are up to 12MB. */
  def clearCache(): Unit = {
    generation += 1
    pictures.clear()
    waiting.clear()
    jobs.clear()
    pixels = 0
    painter.getQueue.removeIf((r: Runnable) => r match {
      case j: Job => j.droppable
      case _ => false
    })
  }

  /** Run `task` on the thread the pictures are drawn on (WorldBackdrop's is drawn there too), before
    * any picture asked for later. */
  def paint(task: Runnable): Unit = painter.execute(job(Now, task, droppable = false))

  /** Wait, off the FX thread, until every picture asked for so far is drawn: for the gallery. Each
    * is handed over on the FX thread after, in the order asked. */
  def settle(): Unit = {
    val done = new CountDownLatch(1)
    painter.execute(job(Int.MaxValue, () => done.countDown(), droppable = false))
    if (!done.await(60, TimeUnit.SECONDS)) throw new IllegalStateException("the map pictures weren't drawn in a minute")
  }

  private def world(mapIndex: Int): WorldData = {
    val key = Integer.valueOf(mapIndex)
    var w = worlds.get(key)
    if (w == null) {
      w = try WorldLoader.load("worlds/" + WorldRegistry.getFilename(mapIndex)) catch { case _: Exception => null }
      if (w != null) {
        worlds.put(key, w)
        val (across, down) = (w.width, w.height)
        Platform.runLater(() => sizes.put(key, (across, down)))
      }
    }
    w
  }

  /** The picture `key` names, `pw` x `ph` pixels, for `ready` on the FX thread: at once if it is
    * drawn, else once `draw` has drawn it on the painter's thread (null if it can't be). One wanted
    * sooner than it was first asked for is drawn sooner, if it isn't being drawn already. */
  private def request(key: String, pw: Int, ph: Int, urgency: Int, draw: () => Array[Int])(ready: Image => Unit): Unit = {
    val done = pictures.get(key)
    if (done != null) { ready(done); return }
    val queued = waiting.get(key)
    if (queued != null) {
      queued.add(ready)
      val asked = jobs.get(key)
      if (asked != null && urgency < asked.urgency && painter.getQueue.remove(asked)) {
        val sooner = job(urgency, asked.body, droppable = true)
        jobs.put(key, sooner)
        painter.execute(sooner)
      }
      return
    }
    val list = new java.util.ArrayList[Image => Unit]()
    list.add(ready)
    waiting.put(key, list)
    val gen = generation
    val drawing = job(urgency, () => {
      val img: Image =
        try { val px = draw(); if (px == null) null else MapArt.image(px, pw, ph) }
        catch { case e: Exception => e.printStackTrace(); null }
      Platform.runLater { () =>
        val callbacks = if (gen == generation) { jobs.remove(key); waiting.remove(key) } else null
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
    }, droppable = true)
    jobs.put(key, drawing)
    painter.execute(drawing)
  }

  private def key(mapIndex: Int, pw: Int, ph: Int): String = s"map$mapIndex:${pw}x$ph"
  private def pixelsFor(size: Double): Int = Math.ceil(size * 2).toInt

  /** The map's picture for a `w` x `h` frame (drawn at twice that), for `ready` on the FX thread:
    * at once if it is drawn, else once it is, `urgency` deciding how soon. */
  def picture(mapIndex: Int, w: Double, h: Double, urgency: Int = Now)(ready: Image => Unit): Unit = {
    val pw = pixelsFor(w); val ph = pixelsFor(h)
    request(key(mapIndex, pw, ph), pw, ph, urgency, () => {
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
    })(ready)
  }

  /** The map's picture for a `w` x `h` frame if it is drawn already, else null. Asks for nothing. */
  def drawn(mapIndex: Int, w: Double, h: Double): Image = pictures.get(key(mapIndex, pixelsFor(w), pixelsFor(h)))

  /** How big the map is, in cells across and down, for `ready` on the FX thread once its file is
    * read (at once if it has been, for any picture). Nothing if it can't be read. */
  def size(mapIndex: Int)(ready: (Int, Int) => Unit): Unit = {
    val known = sizes.get(mapIndex)
    if (known != null) { ready(known._1, known._2); return }
    painter.execute(job(Now, () => {
      val wd = world(mapIndex)
      if (wd != null) Platform.runLater { () =>
        sizes.put(mapIndex, (wd.width, wd.height))
        ready(wd.width, wd.height)
      }
    }, droppable = true))
  }

  /**
   * An empty frame `w` x `h` with rounded corners, on the stage the sheet gives `.map-frame`, and
   * the view in it that a map's picture goes in. The picture isn't clipped to the corners: a map is
   * drawn a twentieth of the frame in from its edges, and a diamond in a box leaves the box's corners
   * clear. A rounded clip renders the frame off screen on every repaint (ui/CLAUDE.md, *Depth is
   * layered backgrounds and borders, not effects*), and a carousel's strip is twenty frames. A
   * `moving` picture (one that slides in) is clipped to the frame's square, which costs nothing.
   */
  def frame(w: Double, h: Double, radius: Double, moving: Boolean = false): (StackPane, ImageView) = {
    val frame = new StackPane()
    frame.getStyleClass.add("map-frame")
    frame.setStyle(s"-fx-background-radius: $radius;")
    frame.setMinSize(w, h); frame.setPrefSize(w, h); frame.setMaxSize(w, h)
    if (moving) frame.setClip(new Rectangle(w, h))
    val view = new ImageView()
    view.setFitWidth(w); view.setFitHeight(h)
    view.setSmooth(true)
    frame.getChildren.add(view)
    (frame, view)
  }

  /**
   * The map in a `w` x `h` frame with rounded corners, on the stage the sheet gives `.map-frame`;
   * drawn in as soon as its picture is ready.
   */
  def view(mapIndex: Int, w: Double, h: Double, radius: Double = 12, urgency: Int = Now): Node = {
    val (box, image) = frame(w, h, radius)
    picture(mapIndex, w, h, urgency)(image.setImage)
    box
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
    val pw = pixelsFor(w); val ph = pixelsFor(h)
    request(s"patch$cells:${pw}x$ph", pw, ph, Now, () => {
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
