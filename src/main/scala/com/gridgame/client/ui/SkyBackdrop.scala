package com.gridgame.client.ui

import javafx.scene.Node
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.shape.{Ellipse, Rectangle}

/**
 * The sky behind every screen, drawn as MapleStory draws it and as the match draws the Meadow's
 * (BackgroundPainter.drawSkyBg): blue paling toward the horizon (the page's gradient, in
 * theme.css), fat clouds with a pale underside and a blue outline, and rounded green hills with
 * round trees along the bottom. Characters can be stood on the nearest hills ([[stand]]).
 *
 * It is all ellipses, which JavaFX fills with a shader instead of rasterising a path, and it never
 * moves: the menus repaint the whole window whenever anything on them changes (ui/CLAUDE.md), so a
 * drifting sky would have them repainting all the time. It is rebuilt only when the window's size
 * changes.
 */
private[client] final class SkyBackdrop extends Pane {
  setMouseTransparent(true)
  // Hills run past the window's edges: clip them, and never ask the page for room for them
  private val frame = new Rectangle()
  frame.widthProperty().bind(widthProperty())
  frame.heightProperty().bind(heightProperty())
  setClip(frame)
  override protected def computeMinWidth(height: Double): Double = 0
  override protected def computeMinHeight(width: Double): Double = 0
  override protected def computePrefWidth(height: Double): Double = 0
  override protected def computePrefHeight(width: Double): Double = 0

  private var builtW = -1.0
  private var builtH = -1.0
  private var standers = Vector.empty[(Node, Double, Double)]
  // The nearest ridge's humps, (centre x, centre y, radius x, radius y), for standing things on it
  private var nearHumps = Vector.empty[(Double, Double, Double, Double)]

  /**
   * Stand `node`, a character's frame `height` pixels tall, on the nearest hills at `fx` of the
   * window's width, with a shadow under its feet. For the login screen's characters.
   */
  def stand(node: Node, fx: Double, height: Double): Unit = {
    standers :+= ((node, fx, height))
    builtW = -1
    requestLayout()
  }

  override def layoutChildren(): Unit = {
    val w = getWidth; val h = getHeight
    if (w <= 0 || h <= 0 || (w == builtW && h == builtH)) return
    builtW = w; builtH = h
    getChildren.clear()
    // Sized to the window, within reason: a 5K screen shouldn't have clouds the size of cards
    val k = Math.max(0.85, Math.min(1.35, w / 1440.0))
    clouds(w, h, k)
    // A far range, pale with distance, just showing over the nearer hills
    ridge(w, h, k, baseUp = 112, height = 66, spacing = 230, seed = 7, fill = Color.web("#a7d5ef"), edge = null)
    nearHumps = ridge(w, h, k, baseUp = 76, height = 74, spacing = 250, seed = 3,
      fill = Color.web("#86cf5e"), edge = Color.web("#4f9c3c"), trees = true)
    ridge(w, h, k, baseUp = 22, height = 42, spacing = 300, seed = 13, fill = Color.web("#72bf4d"), edge = Color.web("#468f35"))
    standers.foreach { case (node, fx, ht) =>
      val x = fx * w
      val ground = groundY(x)
      val shadow = new Ellipse(x, ground + 2, ht * 0.26, ht * 0.06)
      shadow.setFill(Color.rgb(28, 70, 40, 0.28))
      getChildren.add(shadow)
      // A sprite frame's feet sit about 85% of the way down it
      node.relocate(x - ht / 2, ground - ht * 0.86)
      getChildren.add(node)
    }
  }

  /** The top of the nearest hills at `x`. */
  private def groundY(x: Double): Double = {
    var top = getHeight
    nearHumps.foreach { case (cx, cy, rx, ry) =>
      val d = (x - cx) / rx
      if (Math.abs(d) < 1) top = Math.min(top, cy - ry * Math.sqrt(1 - d * d))
    }
    top
  }

  private def ellipse(cx: Double, cy: Double, rx: Double, ry: Double, c: Color): Ellipse = {
    val e = new Ellipse(cx, cy, rx, ry)
    e.setFill(c)
    getChildren.add(e)
    e
  }

  /** A cheap repeatable 0-1 for a seed, so the sky is the same on every screen. */
  private def hash(i: Int): Double = {
    val s = Math.sin(i * 12.9898 + 78.233) * 43758.5453
    s - Math.floor(s)
  }

  // A cloud's lobes: offset from its centre and radius in multiples of its size, as the match's
  private val lobeX = Array(-0.95, -0.38, 0.30, 0.92, 0.0)
  private val lobeY = Array(0.12, -0.22, -0.30, 0.08, 0.18)
  private val lobeR = Array(0.52, 0.72, 0.68, 0.50, 0.80)
  private val cloudEdge = Color.web("#86b1e8")
  private val cloudShade = Color.web("#d6e9ff")

  private def clouds(w: Double, h: Double, k: Double): Unit = {
    // Most pages are a column of cards 1200 wide down the middle, headed by a title on the sky.
    // The clouds keep to the margins beside it, clear of the title, and a few sit lower down in
    // the middle, which shows round the login screen's narrow card and is behind the others'.
    val margin = Math.max(100.0, (w - 1200) / 2)
    val top = 64.0
    def y(f: Double) = top + f * (h - top)
    val placed = Seq(
      (margin * 0.42, y(0.08), 36.0), (margin * 0.62, y(0.30), 26.0), (margin * 0.35, y(0.52), 32.0),
      (w - margin * 0.42, y(0.12), 40.0), (w - margin * 0.6, y(0.36), 28.0), (w - margin * 0.38, y(0.58), 30.0),
      (w * 0.27, y(0.46), 30.0), (w * 0.73, y(0.30), 34.0), (w * 0.62, y(0.62), 22.0))
    placed.foreach { case (cx, cy, size) =>
      val s = size * k
      val edge = Math.max(2.0, 2.2 * k)
      // An outline pass under a shade pass under the bright face, as the match's clouds: one
      // outlined mass with no lines where its lobes overlap
      for (i <- lobeX.indices)
        ellipse(cx + lobeX(i) * s, cy + lobeY(i) * s, lobeR(i) * s + edge, lobeR(i) * s * 0.82 + edge, cloudEdge)
      for (i <- lobeX.indices)
        ellipse(cx + lobeX(i) * s, cy + lobeY(i) * s, lobeR(i) * s, lobeR(i) * s * 0.82, cloudShade)
      for (i <- lobeX.indices) {
        val r = lobeR(i) * s
        ellipse(cx + lobeX(i) * s - r * 0.1, cy + lobeY(i) * s - r * 0.16, r * 0.9, r * 0.74, Color.WHITE)
      }
    }
  }

  /**
   * A ridge of round humps across the bottom of the window: their centres `baseUp` pixels above
   * the bottom, `height` tall, about `spacing` apart. With an `edge` colour, a line of it runs
   * along the ridge's top. Returns the humps.
   */
  private def ridge(w: Double, h: Double, k: Double, baseUp: Double, height: Double, spacing: Double,
                    seed: Int, fill: Color, edge: Color, trees: Boolean = false): Vector[(Double, Double, Double, Double)] = {
    val step = spacing * k
    val n = Math.ceil(w / step).toInt + 2
    val baseY = h - baseUp * k
    val humps = (0 until n).map { i =>
      val cx = (i - 0.5 + (hash(seed * 31 + i) - 0.5) * 0.5) * step
      val rx = step * (0.72 + hash(seed * 17 + i) * 0.22)
      val ry = height * k * (0.8 + hash(seed * 7 + i) * 0.45)
      (cx, baseY, rx, ry)
    }.toVector
    val lift = 2.5 * k
    if (edge != null) humps.foreach { case (cx, cy, rx, ry) => ellipse(cx, cy - lift, rx, ry, edge) }
    // Below the humps' centres the ridge is solid to the bottom of the window
    val base = new Rectangle(0, baseY, w, h - baseY + 1)
    base.setFill(fill)
    getChildren.add(base)
    humps.foreach { case (cx, cy, rx, ry) => ellipse(cx, cy, rx, ry, fill) }
    // A tree on every third hump, a little off its crown, its trunk sunk into the grass
    if (trees) humps.zipWithIndex.foreach { case ((cx, cy, rx, ry), i) =>
      if (i % 3 == 1) tree(cx + rx * 0.15, cy - ry * 0.98 + 3 * k, 26 * k)
    }
    humps
  }

  // A tree's crown: three lobes, offset from its centre and radius in multiples of its size
  private val crownX = Array(-0.42, 0.0, 0.42)
  private val crownY = Array(0.08, -0.22, 0.08)
  private val crownR = Array(0.46, 0.56, 0.46)

  /** A fat round tree, as the match's hills have: a short trunk under a crown of three round
    * lobes, outlined as one mass and lit from the upper left. */
  private def tree(x: Double, groundY: Double, size: Double): Unit = {
    val trunk = new Rectangle(x - size * 0.1, groundY - size * 0.62, size * 0.2, size * 0.66)
    trunk.setFill(Color.web("#8a5a36"))
    trunk.setArcWidth(size * 0.1); trunk.setArcHeight(size * 0.1)
    getChildren.add(trunk)
    val cy = groundY - size * 0.95
    val edge = Math.max(1.8, size * 0.07)
    for (i <- crownX.indices)
      ellipse(x + crownX(i) * size, cy + crownY(i) * size, crownR(i) * size + edge, crownR(i) * size * 0.9 + edge, Color.web("#3d7a3a"))
    for (i <- crownX.indices)
      ellipse(x + crownX(i) * size, cy + crownY(i) * size, crownR(i) * size, crownR(i) * size * 0.9, Color.web("#5fb052"))
    ellipse(x - size * 0.16, cy - size * 0.28, size * 0.3, size * 0.22, Color.web("#80c96a"))
  }
}
