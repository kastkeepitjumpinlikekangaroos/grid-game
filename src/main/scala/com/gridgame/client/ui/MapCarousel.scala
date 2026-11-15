package com.gridgame.client.ui

import com.gridgame.client.i18n.Messages
import com.gridgame.client.ui.Theme.Palette
import com.gridgame.client.ui.Widgets._
import com.gridgame.common.WorldRegistry

import javafx.animation.{Interpolator, KeyFrame, KeyValue, Timeline}
import javafx.geometry.{Insets, Pos}
import javafx.scene.control.{Button, Tooltip}
import javafx.scene.image.{Image, ImageView}
import javafx.scene.input.{KeyCode, KeyEvent, MouseButton, MouseEvent, ScrollEvent}
import javafx.scene.layout.{HBox, Pane, Region, StackPane, VBox}
import javafx.scene.shape.Rectangle
import javafx.util.Duration

/**
 * The maps to pick from, as a carousel: the picked map big, with an arrow either side of it to step
 * to the one before or after (round from the last to the first), its name and how big it is under
 * it, and under those a strip of every map, small, the picked one ringed in gold. A click on a map
 * in the strip picks it, and the strip slides to keep the picked one in its middle; the wheel or a
 * trackpad slides it by hand. ← and → step while the carousel has the focus (a click anywhere on it
 * gives it the focus), Home and End go to the first and the last, Enter settles on the picked map
 * ([[onChoose]]), as a double click on one in the strip does.
 *
 * The big picture of a map stepped to is drawn as it is picked, and while it is (30-200ms) the
 * strip's little one stands in for it, blown up: the two are framed alike. Once the player has
 * stepped (or as it opens, for a carousel opened to be browsed: the map picker's), the maps either
 * side of the picked one are drawn ahead, so a step to either is there at once. A map stepped past
 * before its picture is drawn is given up on (MapPreview's `Ask.cancel`), so stepping quickly waits
 * only for the map stepped to. Nothing moves but the strip sliding and the new picture coming in,
 * for a fifth of a second after a pick (ui/CLAUDE.md, *every changed frame repaints the whole
 * window*), and only on a window that is showing. Only the strip's maps in sight are shown
 * (*Nothing changes out of sight*, there). [[close]] gives up on every picture not yet drawn.
 */
private[client] final class MapCarousel(initial: Int, look: MapCarousel.Look = MapCarousel.Form) {
  private val count = WorldRegistry.size
  private var current = if (initial >= 0 && initial < count) initial else 0

  /** Told of each map the player picks (an arrow, the strip, a key); not of one shown by [[pick]]. */
  var onPick: Int => Unit = _ => ()
  /** Told when the player settles on the picked map: a double click on it in the strip, or Enter. */
  var onChoose: Int => Unit = _ => ()

  // ── The picked map, big, between its arrows ──
  private val (frame, picture) = MapPreview.frame(look.width, look.height, look.radius, moving = true)
  private def arrow(icon: Icons.Icon, tip: String, by: Int): Button = {
    val b = new Button()
    b.getStyleClass.add("carousel-arrow")
    b.setStyle(s"-fx-padding: ${look.arrowPadding};")
    b.setGraphic(Icons.node(icon, look.arrowIcon, Palette.Text))
    b.setTooltip(new Tooltip(tip))
    b.setFocusTraversable(false) // the carousel takes the keys; Tab stops on it once, not on each arrow
    b.setOnAction(_ => step(by))
    b
  }
  private val back = arrow(Icons.ChevronLeft, Messages.t("Previous map"), -1)
  private val forth = arrow(Icons.ChevronRight, Messages.t("Next map"), 1)
  private val counter = text("", "carousel-count")
  private val hero = new StackPane(frame, back, forth, counter)
  StackPane.setAlignment(back, Pos.CENTER_LEFT)
  StackPane.setMargin(back, new Insets(0, 0, 0, 8))
  StackPane.setAlignment(forth, Pos.CENTER_RIGHT)
  StackPane.setMargin(forth, new Insets(0, 8, 0, 0))
  StackPane.setAlignment(counter, Pos.TOP_RIGHT)
  StackPane.setMargin(counter, new Insets(8, 10, 0, 0))
  hero.getStyleClass.add("carousel-hero")
  hero.setStyle(s"-fx-border-radius: ${look.radius + 4};") // the focus ring, 4 outside it
  hero.setMinSize(look.width, look.height)
  hero.setMaxSize(look.width, look.height)
  hero.setFocusTraversable(true)

  // ── Its name and how big it is ──
  private val title = text("", "carousel-title")
  private val facts = text("", "carousel-facts")
  facts.setMinWidth(Region.USE_PREF_SIZE)
  title.setMinWidth(0)
  private val caption = row(10, title, grow(), facts)

  // ── Every map, small, in a strip that slides ──
  private val pictures = new Array[ImageView](count)
  // Each map's little picture as it lands, put in its view once it is in sight (showInSight)
  private val landed = new Array[Image](count)
  private val stripAsks = new Array[MapPreview.Ask](count)
  private val items: IndexedSeq[Region] = (0 until count).map(item)
  private val reel = new HBox(look.gap)
  reel.getChildren.addAll(items: _*)
  private val strip = new Pane(reel)
  private val window = new Rectangle()
  window.widthProperty.bind(strip.widthProperty)
  window.heightProperty.bind(strip.heightProperty)
  strip.setClip(window)
  strip.getStyleClass.add("carousel-strip")
  strip.setMinWidth(0)
  strip.setPrefWidth(look.width)
  strip.setMaxWidth(look.width)
  strip.setMinHeight(Region.USE_PREF_SIZE)

  /** The carousel, to put on a page. */
  val node: VBox = new VBox(10, hero, caption, strip)
  node.getStyleClass.add("carousel")
  node.setMinWidth(look.width)
  node.setPrefWidth(look.width)
  node.setMaxWidth(look.width)

  private def item(i: Int): Region = {
    val name = WorldRegistry.getDisplayName(i)
    val (box, image) = MapPreview.frame(look.thumbWidth, look.thumbHeight, MapCarousel.ThumbRadius)
    pictures(i) = image
    val ring = new Region()
    ring.getStyleClass.add("carousel-ring")
    ring.setMouseTransparent(true)
    val face = new StackPane(box, ring)
    face.getStyleClass.add("carousel-face")
    face.setMaxSize(look.thumbWidth, look.thumbHeight)
    val it: Region =
      if (look.names) {
        val label = text(name, "carousel-label")
        label.setMinWidth(0)
        new VBox(5, face, label)
      } else {
        Tooltip.install(face, new Tooltip(name))
        face
      }
    it.getStyleClass.add("carousel-item")
    it.setMinWidth(look.thumbWidth); it.setPrefWidth(look.thumbWidth); it.setMaxWidth(look.thumbWidth)
    it.setOnMouseClicked((e: MouseEvent) => if (e.getButton == MouseButton.PRIMARY) {
      choose(i, Integer.signum(i - current))
      if (e.getClickCount == 2) onChoose(i)
    })
    it
  }

  // ── Sliding the strip ──
  private val pitch = look.thumbWidth + look.gap
  private val slide = new Timeline()
  private def offset: Double = -reel.getTranslateX
  private def maxOffset: Double = Math.max(0, count * pitch - look.gap - strip.getWidth)
  /** How far along the strip has to be for map `i` to be in its middle, as near as it can be. */
  private def centred(i: Int): Double = Math.max(0, Math.min(maxOffset, i * pitch + look.thumbWidth / 2 - strip.getWidth / 2))
  /** Whether the carousel is on a window that is showing: only then does anything slide. */
  private def onScreen: Boolean = {
    val scene = node.getScene
    scene != null && scene.getWindow != null && scene.getWindow.isShowing
  }
  private def slideTo(to: Double): Unit = {
    slide.stop()
    val x = -Math.round(to).toDouble
    if (!onScreen) reel.setTranslateX(x)
    else {
      slide.getKeyFrames.setAll(new KeyFrame(Duration.millis(220),
        new KeyValue(reel.translateXProperty, Double.box(x): Number, Interpolator.EASE_OUT)))
      slide.playFromStart()
    }
  }
  /** Show only the strip's maps in sight, and put each one's picture in as it comes into sight. A
    * change to a node out of sight (clipped away, or not visible) changes nothing a frame could show,
    * and JavaFX, finding nothing on screen to repaint, repaints the whole window (ui/CLAUDE.md,
    * *Nothing changes out of sight*): most of the strip is out of sight, and each of its pictures
    * landed as a repaint of the whole window. A map comes into sight as the strip moves, which
    * repaints the strip anyway. */
  private def showInSight(): Unit = {
    val left = -reel.getTranslateX
    val right = left + strip.getWidth
    var i = 0
    while (i < count) {
      val x = i * pitch
      val seen = x + look.thumbWidth > left && x < right
      if (items(i).isVisible != seen) items(i).setVisible(seen)
      if (seen && (pictures(i).getImage ne landed(i))) pictures(i).setImage(landed(i))
      i += 1
    }
  }
  /** Map `i`'s little picture has landed: in its view now if the map is in sight, else once it is. */
  private def stripPicture(i: Int)(img: Image): Unit = {
    landed(i) = img
    if (items(i).isVisible) pictures(i).setImage(img)
  }
  reel.translateXProperty.addListener((_, _, _) => showInSight())
  strip.widthProperty.addListener((_, _, _) => {
    slide.stop()
    reel.setTranslateX(-Math.round(centred(current)).toDouble)
    showInSight()
  })
  showInSight()
  strip.addEventHandler(ScrollEvent.SCROLL, (e: ScrollEvent) => {
    val d = if (Math.abs(e.getDeltaX) > Math.abs(e.getDeltaY)) e.getDeltaX else e.getDeltaY
    val to = Math.max(0, Math.min(maxOffset, offset - d))
    if (Math.round(to) != Math.round(offset)) {
      slide.stop()
      reel.setTranslateX(-Math.round(to).toDouble)
      e.consume()
    }
  })

  // ── The new picture coming in from the side it was stepped to ──
  private val enter = new Timeline()
  private def comeIn(from: Int): Unit = {
    enter.stop()
    if (from == 0 || !onScreen) { picture.setTranslateX(0); picture.setOpacity(1); return }
    picture.setTranslateX(from * look.width * 0.06)
    picture.setOpacity(0.35)
    enter.getKeyFrames.setAll(new KeyFrame(Duration.millis(200),
      new KeyValue(picture.translateXProperty, Double.box(0): Number, Interpolator.EASE_OUT),
      new KeyValue(picture.opacityProperty, Double.box(1): Number, Interpolator.EASE_OUT)))
    enter.playFromStart()
  }

  /** Whether the maps either side of the picked one are drawn ahead. */
  private var ahead = look.eager
  // The picked map's big picture and the ones drawn ahead, as asked for: given up on once stepped past
  private var heroAsk: MapPreview.Ask = _
  private var aheadAsks = Map.empty[Int, MapPreview.Ask]

  /** Show map `i` picked: big, named, ringed in the strip, and in the strip's middle. `from` is the
    * side it comes in from: 1 from the right (a step forward), -1 from the left, 0 not at all. */
  private def show(i: Int, was: Int, from: Int): Unit = {
    title.setText(WorldRegistry.getDisplayName(i))
    counter.setText(s"${i + 1} / $count")
    facts.setText("")
    MapPreview.size(i)((w, h) => if (current == i) facts.setText(MapCarousel.sizeText(w, h)))
    // The strip's own picture of it, blown up, until the big one is drawn (MapPreview may have let
    // its copy go to make room for the big ones)
    picture.setImage(if (landed(i) != null) landed(i) else MapPreview.drawn(i, look.thumbWidth, look.thumbHeight))
    // What is wanted now asked for before what isn't is given up on, so a picture wanted by both
    // (a map drawn ahead, now picked) goes on being drawn
    val hero = MapPreview.picture(i, look.width, look.height)(img => if (current == i) picture.setImage(img))
    val nextAhead =
      if (!ahead) Map.empty[Int, MapPreview.Ask]
      else Seq(1, -1).map(d => Math.floorMod(i + d, count)).filter(_ != i).distinct.map { j =>
        j -> aheadAsks.getOrElse(j, MapPreview.picture(j, look.width, look.height, MapPreview.Ahead)(_ => ()))
      }.toMap
    if (heroAsk != null) heroAsk.cancel()
    aheadAsks.foreach { case (j, a) => if (!nextAhead.contains(j)) a.cancel() }
    heroAsk = hero
    aheadAsks = nextAhead
    if (was >= 0) items(was).getStyleClass.remove("carousel-item-picked")
    items(i).getStyleClass.add("carousel-item-picked")
    slideTo(centred(i))
    comeIn(from)
  }

  private def choose(i: Int, from: Int): Unit = if (i != current) {
    val was = current
    current = i
    ahead = true
    show(i, was, from)
    onPick(i)
  }

  private def step(by: Int): Unit = choose(Math.floorMod(current + by, count), by)

  node.addEventFilter(MouseEvent.MOUSE_PRESSED, (_: MouseEvent) => hero.requestFocus())
  hero.addEventHandler(KeyEvent.KEY_PRESSED, (e: KeyEvent) => e.getCode match {
    case KeyCode.LEFT | KeyCode.KP_LEFT => step(-1); e.consume()
    case KeyCode.RIGHT | KeyCode.KP_RIGHT => step(1); e.consume()
    case KeyCode.HOME => choose(0, -1); e.consume()
    case KeyCode.END => choose(count - 1, 1); e.consume()
    case KeyCode.ENTER => onChoose(current); e.consume()
    case _ =>
  })

  show(current, -1, 0)
  // The strip's pictures, the maps nearest the picked one first: the ones in sight
  (0 until count).sortBy(i => Math.abs(i - current)).foreach { i =>
    stripAsks(i) = MapPreview.picture(i, look.thumbWidth, look.thumbHeight, MapPreview.Strip)(stripPicture(i))
  }

  /** The map picked. */
  def picked: Int = current

  /** Show map `i` picked, as the server has it, say: [[onPick]] isn't told. */
  def pick(i: Int): Unit = if (i >= 0 && i < count && i != current) {
    val was = current
    current = i
    show(i, was, 0)
  }

  /** Take the keyboard's focus, so ← and → step at once. */
  def focus(): Unit = hero.requestFocus()

  /** The carousel is gone (its page left, its card closed): every picture it asked for and hasn't
    * got is given up on, so nothing is drawn for it any more. */
  def close(): Unit = {
    slide.stop()
    enter.stop()
    if (heroAsk != null) heroAsk.cancel()
    aheadAsks.values.foreach(_.cancel())
    aheadAsks = Map.empty
    stripAsks.foreach(a => if (a != null) a.cancel())
  }
}

private[client] object MapCarousel {
  /**
   * How big a carousel is: the picked map's picture (`width` x `height`, its corners rounded by
   * `radius`), the strip's (each `thumbWidth` x `thumbHeight`, `gap` apart, with each map's name
   * under its picture or only in its tooltip), and its arrows (their icon and the room round it).
   * The strip's pictures are framed as the big one is, so one can stand in for the other. An
   * `eager` carousel is one opened to be browsed, which draws the maps either side of the picked
   * one as it opens; any other, once the player first steps (each is 0.9-2.7MB).
   */
  final case class Look(width: Double, height: Double, radius: Double, thumbWidth: Double, thumbHeight: Double,
                        gap: Double, names: Boolean, arrowIcon: Double, arrowPadding: Double, eager: Boolean)

  /** In a form 320 wide (the lobby browser's): the map as big as the form showed it before. */
  val Form = Look(320, 180, 14, 64, 36, 6, names = false, arrowIcon = 16, arrowPadding = 6, eager = false)

  /** In the map picker over the lobby room. */
  val Wide = Look(544, 306, 16, 96, 54, 8, names = true, arrowIcon = 20, arrowPadding = 9, eager = true)

  /** The corners of the strip's pictures. */
  val ThumbRadius = 8.0

  /** How big a map `w` x `h` cells is, in words and cells: "Medium map · 120 × 120". */
  def sizeText(w: Int, h: Int): String = {
    val cells = Math.max(w, h)
    val kind =
      if (cells <= 100) Messages.t("Small map")
      else if (cells <= 160) Messages.t("Medium map")
      else Messages.t("Large map")
    s"$kind  ·  $w × $h"
  }
}
