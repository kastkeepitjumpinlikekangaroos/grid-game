package com.gridgame.client.render.projectiles

import com.gridgame.client.gl.ShapeBatch
import ProjectileKit._

/**
 * What the characters' own looks (Spirits, Necromancy, Relics, Venoms, Arsenal, Elements, Vortices,
 * Critters) draw with: a frame to author a shape in and stamp it where it flies, and inked shapes.
 *
 * A shape is authored once in a local frame, in glyph units, and stamped through [[frame]]: centred
 * on (cx, cy), mirrored left-right by `mirror`, turned by `rot`, scaled by `k` virtual units to a
 * glyph unit, and squashed up the screen by `squash` after it is turned (1 for a thing standing up
 * facing the camera, about 0.55 for one lying in the ground plane). [[faceFrame]] turns a thing that
 * has a front, a head in profile, say, the way it flies, mirrored rather than turned upside down when
 * it flies left and leaning at most `maxLean` toward a heading up or down the screen.
 *
 * Every shape is inked: the ink stroked round it before it is filled, so half of the ink shows outside
 * it, which is what carries a shape over sand and snow. A shape in several convex pieces is inked as
 * one ([[inkAll]] before [[fillAll]]), or the ink of each piece is drawn across the one before it.
 */
private[render] object Glyphs {
  private[projectiles] var _fx = 0f; private[projectiles] var _fy = 0f
  private[projectiles] var _fc = 1f; private[projectiles] var _fs = 0f
  private[projectiles] var _fk = 1f; private[projectiles] var _fm = 1f; private[projectiles] var _fq = 1f

  /** Set the frame: see the object's doc. */
  private[projectiles] def frame(cx: Float, cy: Float, rot: Float, k: Float, mirror: Float = 1f, squash: Float = 1f): Unit = {
    _fx = cx; _fy = cy; _fc = Math.cos(rot).toFloat; _fs = Math.sin(rot).toFloat; _fk = k; _fm = mirror; _fq = squash
  }

  /** The frame for a thing whose local +x is its front, flying along (ndx, ndy) on screen. */
  private[projectiles] def faceFrame(cx: Float, cy: Float, ndx: Float, ndy: Float, k: Float, maxLean: Float): Unit = {
    val m = if (ndx < -0.02f) -1f else 1f
    val rot = clampF(Math.atan2(m * ndy, m * ndx).toFloat, -maxLean, maxLean)
    frame(cx, cy, rot, k, m)
  }

  /** The frame for a thing pointing along (ndx, ndy): its local +x is the heading, exactly. */
  private[projectiles] def headingFrame(cx: Float, cy: Float, ndx: Float, ndy: Float, k: Float): Unit = {
    _fx = cx; _fy = cy; _fc = ndx; _fs = ndy; _fk = k; _fm = 1f; _fq = 1f
  }

  @inline private[projectiles] def fx(lx: Float, ly: Float): Float = _fx + (lx * _fm * _fc - ly * _fs) * _fk
  @inline private[projectiles] def fy(lx: Float, ly: Float): Float = _fy + (lx * _fm * _fs + ly * _fc) * _fk * _fq

  // Scratch for outlines in screen space
  private[projectiles] val _gx = new Array[Float](64)
  private[projectiles] val _gy = new Array[Float](64)

  /** Local points (x0, y0, x1, y1, ...) through the frame into _gx/_gy. Returns how many. */
  private[projectiles] def pts(local: Array[Float]): Int = {
    val n = local.length / 2
    var i = 0
    while (i < n) {
      val lx = local(i * 2); val ly = local(i * 2 + 1)
      _gx(i) = fx(lx, ly); _gy(i) = fy(lx, ly)
      i += 1
    }
    n
  }

  /** Local points scaled about (ox, oy) by `s` and moved by (dx, dy), through the frame. */
  private[projectiles] def ptsAt(local: Array[Float], dx: Float, dy: Float, s: Float): Int = {
    val n = local.length / 2
    var i = 0
    while (i < n) {
      val lx = local(i * 2) * s + dx; val ly = local(i * 2 + 1) * s + dy
      _gx(i) = fx(lx, ly); _gy(i) = fy(lx, ly)
      i += 1
    }
    n
  }

  /** An ellipse in the frame, turned by `tilt` in it, as `n` points in _gx/_gy (convex). */
  private[projectiles] def ovalPts(cx: Float, cy: Float, rx: Float, ry: Float, tilt: Float, n: Int): Int = {
    val ct = Math.cos(tilt).toFloat; val st = Math.sin(tilt).toFloat
    // One sin/cos for the whole ring: each point is the last turned by the step
    val cs = Math.cos(2 * Math.PI / n).toFloat; val ss = Math.sin(2 * Math.PI / n).toFloat
    var c = 1f; var s = 0f
    var i = 0
    while (i < n) {
      val ex = c * rx; val ey = s * ry
      val lx = cx + ex * ct - ey * st; val ly = cy + ex * st + ey * ct
      _gx(i) = fx(lx, ly); _gy(i) = fy(lx, ly)
      val c1 = c * cs - s * ss; s = s * cs + c * ss; c = c1
      i += 1
    }
    n
  }

  /** Stroke the outline in _gx/_gy with ink, then fill it: half of the ink shows outside it. */
  private[projectiles] def inked(sb: ShapeBatch, n: Int, inkW: Float, ir: Float, ig: Float, ib: Float, ia: Float,
                                 r: Float, g: Float, b: Float, a: Float): Unit = {
    if (inkW > 0f && ia > 0.01f) sb.strokePolygon(_gx, _gy, n, inkW, ir, ig, ib, ia)
    sb.fillPolygon(_gx, _gy, n, r, g, b, a)
  }

  /** As [[inked]], fanned from (cx, cy) in the frame: for an outline that is star-shaped about it. */
  private[projectiles] def inkedFan(sb: ShapeBatch, cx: Float, cy: Float, n: Int, inkW: Float,
                                    ir: Float, ig: Float, ib: Float, ia: Float,
                                    r: Float, g: Float, b: Float, a: Float): Unit = {
    if (inkW > 0f && ia > 0.01f) sb.strokePolygon(_gx, _gy, n, inkW, ir, ig, ib, ia)
    sb.fillFan(fx(cx, cy), fy(cx, cy), _gx, _gy, n, r, g, b, a)
  }

  /** Just the ink of each local outline in `parts`: [[fillAll]] after it fills them, inked as one. */
  private[projectiles] def inkAll(sb: ShapeBatch, parts: Array[Array[Float]], inkW: Float,
                                  ir: Float, ig: Float, ib: Float, ia: Float): Unit = {
    var p = 0
    while (p < parts.length) { val n = pts(parts(p)); sb.strokePolygon(_gx, _gy, n, inkW, ir, ig, ib, ia); p += 1 }
  }

  private[projectiles] def fillAll(sb: ShapeBatch, parts: Array[Array[Float]], r: Float, g: Float, b: Float, a: Float): Unit = {
    var p = 0
    while (p < parts.length) { val n = pts(parts(p)); sb.fillPolygon(_gx, _gy, n, r, g, b, a); p += 1 }
  }

  /** A line in the frame, `w` glyph units wide. */
  @inline private[projectiles] def gLine(sb: ShapeBatch, x0: Float, y0: Float, x1: Float, y1: Float, w: Float,
                                         r: Float, g: Float, b: Float, a: Float): Unit =
    sb.strokeLine(fx(x0, y0), fy(x0, y0), fx(x1, y1), fy(x1, y1), w * _fk, r, g, b, a)

  /** A filled ellipse in the frame, upright on screen whatever the frame's turn (a dot, an eye). */
  @inline private[projectiles] def gDot(sb: ShapeBatch, lx: Float, ly: Float, rx: Float, ry: Float,
                                        r: Float, g: Float, b: Float, a: Float, segs: Int): Unit =
    sb.fillOval(fx(lx, ly), fy(lx, ly), rx * _fk, ry * _fk * _fq, r, g, b, a, segs)

  /** A deterministic 0..1 for (a, b): the same every frame. */
  @inline private[projectiles] def hash01(a: Int, b: Int): Float = (hash2(a, b) + 1f) * 0.5f

  /** `t` wrapped into 0..1 for a looping particle: (tick * rate + offset) mod 1. */
  @inline private[projectiles] def loop(tick: Int, rate: Double, offset: Double): Float =
    (((tick * rate + offset) % 1.0 + 1.0) % 1.0).toFloat

  // The last trail's own widths and alphas, which each layer of it is made from
  private val _trW = new Array[Float](48)
  private val _trA = new Array[Float](48)

  /**
   * A tapering trail of points back from (x0, y0) along (-ndx, -ndy), `len` long, `n` points, swung
   * across the line by `wave` (a travelling sine, `waves` crests along it) and sagging `sag` at its
   * end, with widths running from `w0` to `w1` and alpha from `a0` to nothing: into _pvX/_pvY. Each
   * layer of it is then [[trailLayer]] and a `strokePolylineVar(_pvX, _pvY, _pvW, _pvA, n, ...)`.
   */
  private[projectiles] def trailPts(x0: Float, y0: Float, ndx: Float, ndy: Float, len: Float, n: Int,
                                    wave: Float, waves: Float, wavePhase: Double, w0: Float, w1: Float,
                                    a0: Float, sag: Float, aEnd: Float = 0f): Unit = {
    val px = -ndy; val py = ndx
    var i = 0
    while (i < n) {
      val t = i.toFloat / (n - 1)
      val sw = if (wave != 0f) Math.sin(wavePhase - t * waves * 2 * Math.PI).toFloat * wave * t else 0f
      _pvX(i) = x0 - ndx * len * t + px * sw
      _pvY(i) = y0 - ndy * len * t + py * sw + sag * t * t
      _trW(i) = w0 + (w1 - w0) * t
      _trA(i) = a0 * (1f - (1f - aEnd) * t)
      i += 1
    }
  }

  /** One layer of the last [[trailPts]]: its widths times `wk` plus `wAdd`, its alphas times `ak`. */
  private[projectiles] def trailLayer(n: Int, wk: Float, wAdd: Float, ak: Float): Unit = {
    var i = 0
    while (i < n) { _pvW(i) = _trW(i) * wk + wAdd; _pvA(i) = _trA(i) * ak; i += 1 }
  }

  /** A tongue of flame from (x0, y0) along the unit (dx, dy), `len` long and `w` wide at its root,
    * swaying `sway` across at its tip: into _pvX/_pvY/_pvW/_pvA, five points. */
  private[projectiles] def flameTongue(x0: Float, y0: Float, dx: Float, dy: Float, len: Float, w: Float, sway: Float,
                                       a: Float): Unit = {
    val qx = -dy; val qy = dx
    var i = 0
    while (i < 5) {
      val t = i / 4f
      _pvX(i) = x0 + dx * len * t + qx * sway * t * t
      _pvY(i) = y0 + dy * len * t + qy * sway * t * t
      _pvW(i) = w * (1f - t) * (1f - 0.25f * t) + 0.4f
      _pvA(i) = a * (1f - t * t * 0.6f)
      i += 1
    }
  }

  /** Move the last trail's points `k` of their own width across it (toward its left, seen flying):
    * for a lit fold or a seam down it. Call it after the layers that sit on the line itself. */
  private[projectiles] def trailShift(n: Int, k: Float, ndx: Float, ndy: Float): Unit = {
    var i = 0
    while (i < n) { _pvX(i) -= ndy * _trW(i) * k; _pvY(i) += ndx * _trW(i) * k; i += 1 }
  }

  /** An ellipse in screen space with semi-axis `a` along the unit vector (mx, my) and `b` across it,
    * as `n` points in _gx/_gy, to be stroked as one band (strokePolygon): a ring standing across a
    * flight path turns with it. */
  private[projectiles] def rotEllipsePts(cx: Float, cy: Float, mx: Float, my: Float, a: Float, b: Float, n: Int): Int = {
    val qx = -my; val qy = mx
    val cs = Math.cos(2 * Math.PI / n).toFloat; val ss = Math.sin(2 * Math.PI / n).toFloat
    var c = 1f; var s = 0f
    var i = 0
    while (i < n) {
      _gx(i) = cx + mx * a * c + qx * b * s
      _gy(i) = cy + my * a * c + qy * b * s
      val c1 = c * cs - s * ss; s = s * cs + c * ss; c = c1
      i += 1
    }
    n
  }
}
