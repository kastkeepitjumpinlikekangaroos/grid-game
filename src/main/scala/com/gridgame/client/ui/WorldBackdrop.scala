package com.gridgame.client.ui

import com.gridgame.common.WorldRegistry
import com.gridgame.common.world.WorldLoader

import javafx.application.Platform
import javafx.scene.Node
import javafx.scene.image.{Image, ImageView}
import javafx.scene.layout.{Pane, Region}
import javafx.scene.shape.Rectangle

/**
 * The world behind every screen: the middle of the Meadow as the match draws it (MapArt), seen
 * from high above and softened a little, under a shade that dims it most along the top and the
 * bottom so the page reads over it (theme.css, `.backdrop-shade`; the login screen's lighter
 * `.backdrop-shade-open`). Characters can be stood along the bottom of it ([[stand]]), each on a
 * patch of ground of their own.
 *
 * It is one picture, drawn on the map-art thread (it shows the night until then, and fades in) and
 * shared by every screen, scaled to cover the window; dropped while a match is on. It never moves: the menus repaint the whole window
 * whenever anything on them changes (ui/CLAUDE.md), and one picture under a gradient is about the
 * cheapest thing there is to repaint. It asks the page for no room and is clipped to it.
 */
private[client] final class WorldBackdrop(open: Boolean = false) extends Pane {
  setMouseTransparent(true)
  getStyleClass.add("backdrop")
  private val frame = new Rectangle()
  frame.widthProperty().bind(widthProperty())
  frame.heightProperty().bind(heightProperty())
  setClip(frame)
  override protected def computeMinWidth(height: Double): Double = 0
  override protected def computeMinHeight(width: Double): Double = 0
  override protected def computePrefWidth(height: Double): Double = 0
  override protected def computePrefHeight(width: Double): Double = 0

  private val view = new ImageView()
  view.setSmooth(true)
  private val shade = new Region()
  shade.getStyleClass.add(if (open) "backdrop-shade-open" else "backdrop-shade")
  getChildren.addAll(view, shade)
  // Fades in if it wasn't drawn yet (as the client starts, and after a match)
  private val fadeIn = !WorldBackdrop.ready
  WorldBackdrop.picture { img =>
    view.setImage(img)
    requestLayout()
    if (fadeIn) {
      view.setOpacity(0)
      val fade = new javafx.animation.FadeTransition(javafx.util.Duration.millis(300), view)
      fade.setToValue(1)
      fade.play()
    }
  }

  private var standers = Vector.empty[(Node, Node, Double, Double)]

  /**
   * Stand `node`, a character's frame `height` pixels tall, at `fx` of the window's width near its
   * bottom, on a patch of grass of its own. For the login screen's characters.
   */
  def stand(node: Node, fx: Double, height: Double): Unit = {
    val ground = MapPreview.patch(2, height * 1.2, height * 0.62)
    standers :+= ((node, ground, fx, height))
    getChildren.addAll(ground, node)
    requestLayout()
  }

  override def layoutChildren(): Unit = {
    val w = getWidth; val h = getHeight
    if (w <= 0 || h <= 0) return
    val img = view.getImage
    if (img != null) {
      // Cover the window, the picture's middle in the window's middle
      val k = Math.max(w / img.getWidth, h / img.getHeight)
      view.setFitWidth(img.getWidth * k); view.setFitHeight(img.getHeight * k)
      view.relocate((w - img.getWidth * k) / 2, (h - img.getHeight * k) / 2)
    }
    shade.resizeRelocate(0, 0, w, h)
    standers.foreach { case (node, ground, fx, ht) =>
      val x = fx * w
      val feet = h - ht * 0.42
      ground.autosize()
      val gw = ground.prefWidth(-1); val gh = ground.prefHeight(-1)
      ground.relocate(x - gw / 2, feet - gh * MapPreview.PatchFeet)
      // A sprite frame's feet sit about 85% of the way down it
      node.relocate(x - ht / 2, feet - ht * 0.86)
    }
  }
}

private[client] object WorldBackdrop {
  /** The picture's size: covered over a 1440 x 900 window, a Retina one too, once it is softened. */
  private val W = 1280
  private val H = 800
  /** Pixels to a unit of the match's projection: about 27 pixels across a cell. */
  private val Scale = 0.68

  // On the FX thread
  private var image: Image = _
  private var asked = false
  private val waiting = new java.util.ArrayList[Image => Unit]()
  // Bumped by clearCache: a picture asked for before it is dropped as it lands
  private var generation = 0

  /** The picture, for `ready` on the FX thread: at once if it is drawn, else once it is. */
  def picture(ready: Image => Unit): Unit = {
    if (image != null) { ready(image); return }
    waiting.add(ready)
    prepare()
  }

  /** Start drawing the picture, if it isn't drawn or being drawn (on the FX thread). */
  def prepare(): Unit = if (!asked) {
    asked = true
    val gen = generation
    MapPreview.paint { () =>
      val img: Image = try {
        val meadow = WorldRegistry.displayNames.indices
          .find(i => WorldRegistry.getFilename(i) == "the_meadow.json").getOrElse(0)
        val world = WorldLoader.load("worlds/" + WorldRegistry.getFilename(meadow))
        val win = MapArt.whole(world)
        // The middle of the map a little above the window's: the huts at the ends of its north
        // and south paths are further apart than a window is tall, and this way the north one is
        // off the top and the south one low down, in the shade's darkest band
        val v = MapArt.centred(win, W, H, Scale)
        val px = MapArt.draw(world, win, W, H, v.copy(oy = v.oy - H * 0.07), 2)
        MapArt.soften(px, W, H, 1)
        MapArt.image(px, W, H)
      } catch { case e: Exception => e.printStackTrace(); null }
      Platform.runLater { () =>
        if (gen == generation) {
          image = img
          if (img != null) waiting.forEach(_(img))
          waiting.clear()
        }
      }
    }
  }

  /** Drop the picture (4MB), and one being drawn as it lands: called when a match starts, while
    * nothing of the menus is shown. The next screen draws it again. */
  def clearCache(): Unit = {
    generation += 1
    image = null
    asked = false
    waiting.clear()
  }

  /** Whether the picture is drawn yet: for the gallery, which waits for it. */
  def ready: Boolean = image != null
}
