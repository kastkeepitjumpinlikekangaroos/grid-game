package com.gridgame.client.render
package world

import com.gridgame.common.model._

import java.util.UUID
import com.gridgame.client.gl.ShapeBatch

/**
 * Raised barriers (see Barriers in the gameplay docs): noted as the entity pass meets their holders,
 * then drawn after it as walls of light along Barrier's polyline, rippling where shots struck them.
 */
private[render] final class BarrierPainter(ctx: RenderContext) {
  import ctx._

  // Raised barriers met in the entity pass, drawn after it: whose, where the
  // holder is drawn (so the wall moves with the sprite, not a step ahead of it), which way it
  // faces, when it went up and comes down, and whether it is ours or an ally's
  private val MAX_BARRIERS_DRAWN = 32
  private val _barOwner = new Array[UUID](MAX_BARRIERS_DRAWN)
  private val _barWX = new Array[Double](MAX_BARRIERS_DRAWN)
  private val _barWY = new Array[Double](MAX_BARRIERS_DRAWN)
  private val _barAngle = new Array[Float](MAX_BARRIERS_DRAWN)
  private val _barRaisedAt = new Array[Long](MAX_BARRIERS_DRAWN)
  private val _barUntil = new Array[Long](MAX_BARRIERS_DRAWN)
  private val _barAlly = new Array[Boolean](MAX_BARRIERS_DRAWN)
  private var _barCount = 0

  // Screen-space scratch for one barrier: the polyline on the ground, a quad, a ripple ring
  private val _barSX = new Array[Float](Barrier.POINTS)
  private val _barSY = new Array[Float](Barrier.POINTS)
  private val _barQuadXs = new Array[Float](4)
  private val _barQuadYs = new Array[Float](4)
  private val BARRIER_RING_POINTS = 14
  private val _barRingXs = new Array[Float](BARRIER_RING_POINTS)
  private val _barRingYs = new Array[Float](BARRIER_RING_POINTS)
  private val BARRIER_HEIGHT_PX = 22f
  private val BARRIER_RAISE_MS = 140f
  private val BARRIER_FADE_MS = 200f
  private val BARRIER_RIPPLE_MS = 300f

  // Half the width of the strip it glows in along the ground, in cells
  private val BARRIER_FOOT_CELLS = 0.2f
  private val BARRIER_RIB_CELLS = 0.6f

  /** Keep a barrier to draw after the entity pass, if it is up or still fading out. */
  def note(owner: UUID, wx: Double, wy: Double, angle: Float, raisedAt: Long, until: Long,
           ally: Boolean): Unit = {
    if (raisedAt <= 0L || frameTimeMs >= until + BARRIER_FADE_MS.toLong || _barCount >= MAX_BARRIERS_DRAWN) return
    val i = _barCount
    _barOwner(i) = owner
    _barWX(i) = wx; _barWY(i) = wy
    _barAngle(i) = angle
    _barRaisedAt(i) = raisedAt; _barUntil(i) = until
    _barAlly(i) = ally
    _barCount += 1
  }

  /**
   * Every raised barrier, after the terrain and the entities: a translucent wall of light standing
   * on the ground along Barrier's polyline, the line the server stops shots on. Ours and our
   * allies' are a cool blue, everyone else's a warm red. It grows up out of the ground as it is
   * raised, sinks and fades as it drops, and ripples for a moment wherever a shot struck it.
   *
   * It also glows in a strip along the ground. A wall whose line runs up the screen (aimed straight
   * left or right) is seen edge on in this projection, and the strip is what still shows it then.
   * Every piece is a convex quad or ellipse: fillPolygon fans from vertex 0.
   */
  def draw(): Unit = {
    if (_barCount == 0) return
    beginShapes()
    var i = 0
    while (i < _barCount) {
      drawBarrier(i)
      _barOwner(i) = null
      i += 1
    }
    shapeBatch.resetModifiers()
    _barCount = 0
  }

  /** Fill the band a barrier segment makes from (x0, y0)-(x1, y1) on the ground, lifted `lo` to
    * `hi` px up the wall. */
  private def barrierBand(x0: Float, y0: Float, x1: Float, y1: Float, lo: Float, hi: Float,
                          r: Float, g: Float, b: Float, a: Float): Unit = {
    _barQuadXs(0) = x0; _barQuadYs(0) = y0 - lo
    _barQuadXs(1) = x1; _barQuadYs(1) = y1 - lo
    _barQuadXs(2) = x1; _barQuadYs(2) = y1 - hi
    _barQuadXs(3) = x0; _barQuadYs(3) = y0 - hi
    shapeBatch.fillPolygon(_barQuadXs, _barQuadYs, 4, r, g, b, a)
  }

  private def drawBarrier(i: Int): Unit = {
    val now = frameTimeMs
    val raise = clamp((now - _barRaisedAt(i)) / BARRIER_RAISE_MS)
    val until = _barUntil(i)
    val fade = if (now < until) 1f else clamp(1f - (now - until) / BARRIER_FADE_MS)
    val vis = (1f - (1f - raise) * (1f - raise)) * fade
    if (vis <= 0.01f) return
    // Up out of the ground with a little overshoot, and back down into it as it goes
    val back = raise - 1f
    val overshoot = 1f + 2.70158f * back * back * back + 1.70158f * back * back
    val h = BARRIER_HEIGHT_PX * (0.15f + 0.85f * overshoot) * (0.45f + 0.55f * fade)

    val ally = _barAlly(i)
    val cr = if (ally) 0.22f else 1f
    val cg = if (ally) 0.60f else 0.26f
    val cb = if (ally) 1f else 0.16f
    // The same hue, lit, for the edges, where the bloom picks it up. Lit only part of the way to
    // white: bright() takes red to a pink that reads as nobody's in particular.
    val lr = if (ally) 0.62f else 1f
    val lg = if (ally) 0.88f else 0.62f
    val lb = if (ally) 1f else 0.48f

    // How hard it was struck just now: the whole wall flares with the ripples
    val owner = _barOwner(i)
    var flare = 0f
    var k = 0
    while (k < client.BARRIER_IMPACT_SLOTS) {
      if (owner.equals(client.getBarrierImpactOwner(k))) {
        val age = now - client.getBarrierImpactTime(k)
        if (age >= 0L && age < BARRIER_RIPPLE_MS) flare = Math.max(flare, 1f - age / BARRIER_RIPPLE_MS)
      }
      k += 1
    }

    // The polyline on the ground, on screen
    val hx = _barWX(i).toFloat
    val hy = _barWY(i).toFloat
    val cos = Math.cos(_barAngle(i)).toFloat
    val sin = Math.sin(_barAngle(i)).toFloat
    var p = 0
    while (p < Barrier.POINTS) {
      val px = Barrier.pointX(hx, cos, sin, p)
      val py = Barrier.pointY(hy, cos, sin, p)
      _barSX(p) = worldToScreenX(px, py).toFloat
      _barSY(p) = worldToScreenY(px, py).toFloat
      p += 1
    }
    lightSystem.addLight((_barSX(1) + _barSX(2)) * 0.5f, (_barSY(1) + _barSY(2)) * 0.5f - h * 0.5f,
      75f, cr, cg, cb, (0.10f + 0.14f * flare) * vis)

    shapeBatch.setAlphaMultiplier(vis)

    // Its footprint: a strip along the ground, each segment's own rectangle in the ground plane
    var s = 0
    while (s < Barrier.SEGMENTS) {
      val ax = Barrier.pointX(hx, cos, sin, s); val ay = Barrier.pointY(hy, cos, sin, s)
      val bx = Barrier.pointX(hx, cos, sin, s + 1); val by = Barrier.pointY(hy, cos, sin, s + 1)
      val dx = bx - ax; val dy = by - ay
      val len = Math.sqrt(dx * dx + dy * dy).toFloat
      val nx = dy / len * BARRIER_FOOT_CELLS; val ny = -dx / len * BARRIER_FOOT_CELLS
      _barQuadXs(0) = worldToScreenX(ax + nx, ay + ny).toFloat; _barQuadYs(0) = worldToScreenY(ax + nx, ay + ny).toFloat
      _barQuadXs(1) = worldToScreenX(bx + nx, by + ny).toFloat; _barQuadYs(1) = worldToScreenY(bx + nx, by + ny).toFloat
      _barQuadXs(2) = worldToScreenX(bx - nx, by - ny).toFloat; _barQuadYs(2) = worldToScreenY(bx - nx, by - ny).toFloat
      _barQuadXs(3) = worldToScreenX(ax - nx, ay - ny).toFloat; _barQuadYs(3) = worldToScreenY(ax - nx, ay - ny).toFloat
      shapeBatch.fillPolygon(_barQuadXs, _barQuadYs, 4, cr, cg, cb, 0.22f + 0.15f * flare)
      s += 1
    }

    // The sheet, brightest at its foot and fading as it rises, with a band of light climbing it
    val climb = ((now % 1400L) / 1400f) * h
    s = 0
    while (s < Barrier.SEGMENTS) {
      val x0 = _barSX(s); val y0 = _barSY(s); val x1 = _barSX(s + 1); val y1 = _barSY(s + 1)
      barrierBand(x0, y0, x1, y1, 0f, h, cr, cg, cb, 0.16f + 0.14f * flare)
      barrierBand(x0, y0, x1, y1, 0f, h * 0.6f, cr, cg, cb, 0.10f)
      barrierBand(x0, y0, x1, y1, 0f, h * 0.25f, cr, cg, cb, 0.14f)
      barrierBand(x0, y0, x1, y1, climb, Math.min(h, climb + 2.5f), lr, lg, lb, 0.30f * (1f - climb / h))
      s += 1
    }

    // Faint ribs of light up the sheet, drifting along it: stopping short of its top, so they read
    // as light in the wall rather than as the slats of a fence
    val drift = ((now % 2400L) / 2400f) * BARRIER_RIB_CELLS
    var across = -Barrier.WIDTH / 2 + drift
    while (across < Barrier.WIDTH / 2) {
      val f = Barrier.forwardAt(across)
      val wx = hx + f * cos - across * sin
      val wy = hy + f * sin + across * cos
      val sx = worldToScreenX(wx, wy).toFloat
      val sy = worldToScreenY(wx, wy).toFloat
      val edge = 1f - Math.abs(across) / (Barrier.WIDTH / 2)
      shapeBatch.strokeLine(sx, sy, sx, sy - h * 0.7f, 0.8f, lr, lg, lb, 0.06f + 0.12f * edge)
      across += BARRIER_RIB_CELLS
    }

    // Edges: a hard line where it meets the ground, its top, and a post at each end
    s = 0
    while (s < Barrier.SEGMENTS) {
      val x0 = _barSX(s); val y0 = _barSY(s); val x1 = _barSX(s + 1); val y1 = _barSY(s + 1)
      shapeBatch.strokeLineSoft(x0, y0, x1, y1, 6f, cr, cg, cb, 0.55f)
      shapeBatch.strokeLine(x0, y0, x1, y1, 1.3f, lr, lg, lb, 0.9f)
      shapeBatch.strokeLineSoft(x0, y0 - h, x1, y1 - h, 4f, cr, cg, cb, 0.45f + 0.3f * flare)
      shapeBatch.strokeLine(x0, y0 - h, x1, y1 - h, 1.2f, lr, lg, lb, 0.8f + 0.2f * flare)
      s += 1
    }
    val last = Barrier.POINTS - 1
    shapeBatch.strokeLine(_barSX(0), _barSY(0), _barSX(0), _barSY(0) - h, 2f, lr, lg, lb, 0.9f)
    shapeBatch.strokeLine(_barSX(last), _barSY(last), _barSX(last), _barSY(last) - h, 2f, lr, lg, lb, 0.9f)

    // A ripple where each recent shot struck, spreading over the sheet from there
    k = 0
    while (k < client.BARRIER_IMPACT_SLOTS) {
      if (owner.equals(client.getBarrierImpactOwner(k))) {
        val age = now - client.getBarrierImpactTime(k)
        if (age >= 0L && age < BARRIER_RIPPLE_MS) {
          drawBarrierRipple(client.getBarrierImpactAcross(k), age / BARRIER_RIPPLE_MS, h, lr, lg, lb)
        }
      }
      k += 1
    }

    shapeBatch.setAlphaMultiplier(1f)
  }

  /** A ring spreading over the sheet from where a shot struck it, `across` cells along it, `t`
    * of the way through its life. Drawn in the sheet's own plane: along the wall and up it. */
  private def drawBarrierRipple(across: Float, t: Float, h: Float, r: Float, g: Float, b: Float): Unit = {
    // The segment it struck, and the wall's direction on screen there, in px per cell along it
    var s = 0
    while (s < Barrier.SEGMENTS - 1 && across > Barrier.localA(s + 1)) s += 1
    val span = Barrier.localA(s + 1) - Barrier.localA(s)
    val u = (across - Barrier.localA(s)) / span
    val tx = (_barSX(s + 1) - _barSX(s)) / span
    val ty = (_barSY(s + 1) - _barSY(s)) / span
    val cx = _barSX(s) + (_barSX(s + 1) - _barSX(s)) * u
    val cy = _barSY(s) + (_barSY(s + 1) - _barSY(s)) * u - h * 0.5f
    val fadeOut = 1f - t
    val n = BARRIER_RING_POINTS
    // The flash where it struck, gone in the first half of the ripple
    var j = 0
    while (j < n) {
      val c = ShapeBatch.getCos(n, j) * 0.4f
      _barRingXs(j) = cx + tx * c
      _barRingYs(j) = cy + ty * c - ShapeBatch.getSin(n, j) * h * 0.28f
      j += 1
    }
    shapeBatch.fillPolygon(_barRingXs, _barRingYs, n, r, g, b, 0.6f * fadeOut * fadeOut)
    // The ring
    val along = 0.3f + 1.2f * t
    val up = h * (0.2f + 0.3f * t)
    j = 0
    while (j < n) {
      val c = ShapeBatch.getCos(n, j) * along
      _barRingXs(j) = cx + tx * c
      _barRingYs(j) = cy + ty * c - ShapeBatch.getSin(n, j) * up
      j += 1
    }
    shapeBatch.strokePolygon(_barRingXs, _barRingYs, n, 0.6f + 1.6f * fadeOut, r, g, b, 0.9f * fadeOut)
  }

  def beginFrame(): Unit = _barCount = 0
}
