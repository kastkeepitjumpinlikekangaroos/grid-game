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
 * A picture is drawn for each size it is shown at, at twice that size for a Retina display (at
 * that size where the menus are drawn at 1x: [[Density]]), on a thread of its own, and handed over
 * on the FX thread: the frame shows its stage until then. The big ones are kept for the last few
 * maps shown, and the last few maps read from their files for a picture shown on its own (a
 * carousel's strip reads every one, and a big map read is a quarter of a megabyte, so what the strip
 * reads isn't kept).
 *
 * The thread draws what is wanted soonest first ([[Now]], [[Strip]], [[Ahead]]), and in the order
 * asked within each: a map's picture shown on its own goes before the carousel's strip of every map,
 * which goes before the pictures of the maps the player may step to next. A picture nobody wants
 * any more ([[Ask.cancel]]: a carousel stepped past it) isn't drawn, or stops being drawn, so a
 * player stepping quickly through the maps waits only for the one they stop on.
 */
private[client] object MapPreview {
  /** How soon a picture is wanted: one the page shows on its own (a map's, a lobby's, the world
    * behind), drawn before... */
  val Now = 0
  /** ...the little ones in a carousel's strip, every map at once, before... */
  val Strip = 1
  /** ...the maps a carousel may step to next, drawn while nothing else is wanted. */
  val Ahead = 2

  /** A picture asked for: told to whoever asked once it is drawn, unless they give up on it first. */
  final class Ask private[MapPreview] (private[MapPreview] val key: String, private[MapPreview] val urgency: Int,
                                       private[MapPreview] val ready: Image => Unit) {
    /** Not wanted any more (on the FX thread): nothing is told, and if nobody else wants the
      * picture, it isn't drawn, or stops being drawn. Nothing, for one already handed over. */
    def cancel(): Unit = MapPreview.cancel(this)
  }

  /** A picture being drawn or waiting to be: who wants it, what will draw it, and whether everyone
    * has given up on it (read by the painter's thread). */
  private final class Drawing(val key: String) {
    val asks = new java.util.ArrayList[Ask]()
    var job: Job = _
    @volatile var unwanted = false
  }

  // On the FX thread: the pictures drawn, by what and how big, the ones being drawn, and how big
  // each map read so far is
  private val pictures = new java.util.LinkedHashMap[String, Image](16, 0.75f, true)
  private val drawings = new java.util.HashMap[String, Drawing]()
  private val sizes = new java.util.HashMap[Integer, (Int, Int)]()
  /** 12MB of pictures: the map picker's big one is 2.7MB, the lobby form's 0.9MB, a lobby row's
    * 0.07MB, the form's strip of every map 0.6MB all told (a quarter of each at 1x: [[Density]]). */
  private val MaxPixels = 3_000_000L
  private var pixels = 0L
  // Bumped by clearCache: a picture asked for before it is dropped as it lands
  private var generation = 0

  // On the painter's thread: the last few maps read for a picture of their own
  private val worlds = new java.util.LinkedHashMap[Integer, WorldData](8, 0.75f, true) {
    override def removeEldestEntry(e: java.util.Map.Entry[Integer, WorldData]): Boolean = size > 4
  }

  /** Something for the painter to do: soonest wanted first, then first asked. A picture of a map
    * can be dropped before it is started ([[clearCache]], [[Ask.cancel]]); what [[paint]] is given
    * can't. */
  private final class Job(val urgency: Int, val order: Long, val body: Runnable, val droppable: Boolean) extends Runnable {
    def run(): Unit = body.run()
  }
  private val asked = new AtomicLong()
  private def job(urgency: Int, body: Runnable, droppable: Boolean): Job =
    new Job(urgency, asked.getAndIncrement(), body, droppable)

  /** How long the painter's thread waits for more to draw before it lets the tile art go (8MB, and
    * a tenth of a second to read again): long enough for a player browsing the maps never to wait
    * for it, short enough that the menus don't hold it while they are only being looked at. */
  private val KeepArtSeconds = 15L

  // One thread, which goes, and lets the tile art go with it, once it has had nothing to draw for
  // KeepArtSeconds
  private lazy val painter: ThreadPoolExecutor = {
    val queue = new PriorityBlockingQueue[Runnable](32, (a: Runnable, b: Runnable) => {
      val (x, y) = (a.asInstanceOf[Job], b.asInstanceOf[Job])
      if (x.urgency != y.urgency) Integer.compare(x.urgency, y.urgency) else java.lang.Long.compare(x.order, y.order)
    })
    val pool = new ThreadPoolExecutor(1, 1, KeepArtSeconds, TimeUnit.SECONDS, queue, { (r: Runnable) =>
      val t = new Thread(() => try r.run() finally MapArt.release(), "map-art")
      t.setDaemon(true)
      t
    })
    pool.allowCoreThreadTimeOut(true)
    pool
  }

  /** How deep the slab under a map is, as a share of how tall the map is drawn. */
  private val BaseShare = 0.035
  /** How much of a map's border is shown round where its matches are played, as a share of that. */
  private val Rim = 0.06

  /** Drop every picture (they are drawn again when next shown), any being drawn as it lands, and
    * every one not yet started, and let the tile art go. Called when a match starts: nothing on the
    * JavaFX side is shown while the game window is up, and the pictures are up to 12MB. */
  def clearCache(): Unit = {
    generation += 1
    pictures.clear()
    drawings.values.forEach(_.unwanted = true)
    drawings.clear()
    pixels = 0
    painter.getQueue.removeIf((r: Runnable) => r match {
      case j: Job => j.droppable
      case _ => false
    })
    MapArt.release()
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

  /** Map `mapIndex` as read from its file, kept with the last few read if `keep`. */
  private def world(mapIndex: Int, keep: Boolean): WorldData = {
    val key = Integer.valueOf(mapIndex)
    var w = worlds.get(key)
    if (w == null) {
      w = try WorldLoader.load("worlds/" + WorldRegistry.getFilename(mapIndex)) catch { case _: Exception => null }
      if (w != null) {
        if (keep) worlds.put(key, w)
        val (across, down) = (w.width, w.height)
        Platform.runLater(() => sizes.put(key, (across, down)))
      }
    }
    w
  }

  /** The picture `key` names, `pw` x `ph` pixels, for `ready` on the FX thread: at once if it is
    * drawn, else once `draw` has drawn it on the painter's thread (null if it can't be, or `draw`
    * was told to stop, by the argument it is given, because nobody wants it any more). One wanted
    * sooner than it was first asked for is drawn sooner, if it isn't being drawn already. */
  private def request(key: String, pw: Int, ph: Int, urgency: Int, draw: (() => Boolean) => Array[Int])(ready: Image => Unit): Ask = {
    val ask = new Ask(key, urgency, ready)
    val done = pictures.get(key)
    if (done != null) { ready(done); return ask }
    val going = drawings.get(key)
    if (going != null) {
      going.asks.add(ask)
      if (urgency < going.job.urgency && painter.getQueue.remove(going.job)) {
        going.job = job(urgency, going.job.body, droppable = true)
        painter.execute(going.job)
      }
      return ask
    }
    val drawing = new Drawing(key)
    drawing.asks.add(ask)
    drawings.put(key, drawing)
    val gen = generation
    val stop = () => drawing.unwanted
    drawing.job = job(urgency, () => {
      val img: Image =
        if (drawing.unwanted) null
        else try { val px = draw(stop); if (px == null) null else MapArt.image(px, pw, ph) }
        catch { case e: Exception => e.printStackTrace(); null }
      Platform.runLater(() => landed(drawing, gen, img, pw, ph))
    }, droppable = true)
    painter.execute(drawing.job)
    ask
  }

  /** A picture drawn (or not: null) on the painter's thread, on the FX thread: kept, and handed to
    * whoever still wants it. One that was given up on is kept all the same, if it was finished. */
  private def landed(drawing: Drawing, gen: Int, img: Image, pw: Int, ph: Int): Unit = {
    if (gen != generation) return
    val current = drawings.get(drawing.key)
    if (img == null) {
      if (current eq drawing) drawings.remove(drawing.key)
      return
    }
    pictures.put(drawing.key, img)
    pixels += pw.toLong * ph
    // The least lately shown go first, never the one just drawn
    val it = pictures.entrySet().iterator()
    while (pixels > MaxPixels && it.hasNext) {
      val e = it.next()
      if (e.getKey != drawing.key) {
        pixels -= (e.getValue.getWidth * e.getValue.getHeight).toLong
        it.remove()
      }
    }
    if (current != null) {
      drawings.remove(drawing.key)
      // Asked for again after it was given up on: this one is it
      if (current ne drawing) { current.unwanted = true; painter.getQueue.remove(current.job) }
      current.asks.forEach(_.ready(img))
    }
  }

  private def cancel(ask: Ask): Unit = {
    val drawing = drawings.get(ask.key)
    if (drawing == null || !drawing.asks.remove(ask)) return
    if (drawing.asks.isEmpty) {
      drawings.remove(ask.key)
      drawing.unwanted = true
      painter.getQueue.remove(drawing.job)
    } else {
      // Wanted no sooner than whoever is left wants it: a map stepped past and still wanted ahead
      // waits behind the one stepped to
      var soonest = Int.MaxValue
      drawing.asks.forEach(a => soonest = Math.min(soonest, a.urgency))
      if (soonest > drawing.job.urgency && painter.getQueue.remove(drawing.job)) {
        drawing.job = job(soonest, drawing.job.body, droppable = true)
        painter.execute(drawing.job)
      }
    }
  }

  /**
   * A picture's pixels for each of the menus' own: two on a Retina display, one where the menus are
   * drawn at 1x (Low quality's, which the system scales up: ClientMain) or every screen is a 1x one.
   * Drawn at twice the size for menus drawn at 1x, a picture was halved by the GPU on its way to the
   * screen: the same picture, with the samples half as far apart (`samples`), takes as long to draw,
   * a quarter of the memory and of the upload, and the cache holds four times as many.
   */
  private lazy val Density: Int =
    if ("false" == System.getProperty("prism.allowhidpi")) 1
    else try {
      val screens = javafx.stage.Screen.getScreens
      if (!screens.isEmpty && screens.stream().allMatch(s => s.getOutputScaleX <= 1.0 && s.getOutputScaleY <= 1.0)) 1 else 2
    } catch { case _: Exception => 2 }

  private def key(mapIndex: Int, pw: Int, ph: Int): String = s"map$mapIndex:${pw}x$ph"
  private def pixelsFor(size: Double): Int = Math.ceil(size * Density).toInt
  /** Samples a side for each pixel of a `w` x `h` frame's picture: three at 2x for a small frame
    * (the cost is in the pixels, and it has few), else two; twice as many at 1x, where a pixel is
    * twice as wide, so a picture is the same at either. */
  private def samples(w: Double, h: Double): Int = (if (w * h < 30_000) 3 else 2) * 2 / Density

  /** The map's picture for a `w` x `h` frame (drawn at [[Density]]), for `ready` on the FX thread:
    * at once if it is drawn, else once it is, `urgency` deciding how soon, unless the [[Ask]] is
    * cancelled first. */
  def picture(mapIndex: Int, w: Double, h: Double, urgency: Int = Now)(ready: Image => Unit): Ask = {
    val pw = pixelsFor(w); val ph = pixelsFor(h)
    val ss = samples(w, h)
    // A strip reads every map: only a map shown on its own is worth keeping read
    val keep = urgency != Strip
    request(key(mapIndex, pw, ph), pw, ph, urgency, stop => {
      val wd = world(mapIndex, keep)
      if (wd == null) null
      else {
        val win = MapArt.playArea(wd, Rim)
        val (_, top, _, bottom) = MapArt.bounds(win)
        val base = (bottom - top) * BaseShare
        val px = MapArt.draw(wd, win, pw, ph, MapArt.fit(win, pw, ph, 0.05, base), ss, base, stop)
        if (px != null) MapArt.light(px, pw, ph)
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
      val wd = world(mapIndex, keep = true)
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
    request(s"patch$cells:${pw}x$ph", pw, ph, Now, _ => {
      // Grass with a flower here and there, as the Meadow's is
      val tiles = Array.tabulate[Tile](cells, cells)((y, x) => if ((x * 5 + y * 3) % 7 == 3) Tile.Flowers else Tile.Grass)
      val wd = new WorldData("patch", cells, cells, tiles, Seq.empty, "sky")
      val win = MapArt.whole(wd)
      val base = 9.0
      MapArt.draw(wd, win, pw, ph, MapArt.fit(win, pw, ph, 0.02, base, rise = 0), 3 * 2 / Density, base)
    })(view.setImage)
    view
  }
}
