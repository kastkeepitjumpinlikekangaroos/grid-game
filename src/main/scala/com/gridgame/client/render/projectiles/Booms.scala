package com.gridgame.client.render.projectiles

import com.gridgame.common.model.Projectile
import com.gridgame.client.gl.ShapeBatch
import ProjectileKit._
import Glyphs._

/**
 * The sonic boom, drawn as whoever struck it: the Bard's lyre, its strings ringing, and the Musician's
 * guitar pick, thumping to the beat, each with the sound rippling off it. Both were the same lilac ring
 * on the ground. (Their songs, the sonic waves, are in Songs.)
 */
private[render] object Booms {

  // ── The Bard ──
  // The lyre's right arm, from the sound box up to its scroll; the left is its mirror
  private val LYRE_ARM = Array(3.2f, 5.6f, 6.8f, 2.6f, 8.2f, -2.6f, 7.4f, -7.6f, 5.2f, -10.8f, 5.4f, -13f, 7.2f, -13.6f)
  private final val LYRE_N = 7
  private val _armX = new Array[Float](LYRE_N)
  private val _armY = new Array[Float](LYRE_N)

  /**
   * The Bard's sonic boom: a golden lyre — two curving arms, the yoke across them, five strings ringing
   * so hard they blur — a ribbon in the Bard's red and purple tied to it and streaming back, and the boom
   * rippling off it in gold rings, notes shaken loose.
   */
  private[projectiles] def drawLyre(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 37) * 0.35
    computeAllDynamics(proj, 1f, 0.82f, 0.35f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val k = 1.35f * ds
    val cx = sx; val cy = sy - 6f * ds + Math.sin(phase * 0.6).toFloat * 1.2f * ds

    // The boom rippling off it
    var r = 0
    while (r < 2) {
      val t = loop(tick, 0.03, r * 0.5 + proj.id * 0.13)
      val rr = (14f + 24f * t) * ds
      sb.strokeOval(cx, cy, rr, rr * 0.86f, 4f * (1f - t) + 1.6f, 0.3f, 0.2f, 0.05f, 0.4f * (1f - t) * a, 22)
      sb.strokeOval(cx, cy, rr, rr * 0.86f, 2.4f * (1f - t) + 0.6f, 1f, 0.9f, 0.55f, 0.8f * (1f - t) * a, 22)
      r += 1
    }
    sb.fillOvalSoft(cx, cy, 26f * ds * dynGlow, 28f * ds * dynGlow, 1f, 0.85f, 0.4f, 0.26f * a, 0f, 16)
    frame(cx, cy, Math.sin(phase * 0.5).toFloat * 0.12f, k)
    // The ribbon tied to its right arm, streaming back
    val rn = 6
    trailPts(fx(6.6f, -8.4f), fy(6.6f, -8.4f), ndx, ndy, 30f * ds, rn, 4f * ds, 1.3f, phase * 2.0, 3.6f * k, 2f * k, a, 6f * ds, aEnd = 0.3f)
    trailLayer(rn, 1f, 2f, 0.9f)
    sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, rn, 0.2f, 0.04f, 0.08f)
    trailLayer(rn, 1f, 0f, 1f)
    sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, rn, 0.82f, 0.14f, 0.2f)
    trailLayer(rn, 0.35f, 0f, 1f)
    sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, rn, 0.5f, 0.25f, 0.7f)
    // The sound box
    var n = ovalPts(0f, 6.4f, 6.6f, 3.6f, 0f, 16)
    sb.strokePolygon(_gx, _gy, n, 2.6f, 0.25f, 0.14f, 0.03f, 0.94f * a)
    sb.fillPolygon(_gx, _gy, n, 0.74f, 0.48f, 0.2f, a)
    n = ovalPts(0f, 5.8f, 4.6f, 2f, 0f, 14)
    sb.fillPolygon(_gx, _gy, n, 0.55f, 0.32f, 0.12f, a)
    // The strings, ringing: each bowed out by its own blur
    var s = 0
    while (s < 5) {
      val x = -3.2f + s * 1.6f
      val blur = Math.sin(phase * 9.0 + s * 1.3).toFloat * 0.8f
      _pvX(0) = fx(x, -10.4f); _pvY(0) = fy(x, -10.4f)
      _pvX(1) = fx(x + blur, -2.6f); _pvY(1) = fy(x + blur, -2.6f)
      _pvX(2) = fx(x, 5.2f); _pvY(2) = fy(x, 5.2f)
      sb.strokePolyline(_pvX, _pvY, 3, 1.4f, 0.35f, 0.25f, 0.1f, 0.5f * a)
      sb.strokePolyline(_pvX, _pvY, 3, 0.7f * k, 1f, 0.98f, 0.88f, a)
      s += 1
    }
    // Its arms and the yoke across them: ink, gold, a lit edge
    var pass = 0
    while (pass < 3) {
      var side = -1f
      while (side <= 1f) {
        var i = 0
        while (i < LYRE_N) { _armX(i) = fx(LYRE_ARM(i * 2) * side, LYRE_ARM(i * 2 + 1)); _armY(i) = fy(LYRE_ARM(i * 2) * side, LYRE_ARM(i * 2 + 1)); i += 1 }
        (pass: @scala.annotation.switch) match {
          case 0 => sb.strokePolyline(_armX, _armY, LYRE_N, 2.6f * k + 2.6f, 0.25f, 0.14f, 0.03f, 0.94f * a)
          case 1 => sb.strokePolyline(_armX, _armY, LYRE_N, 2.6f * k, 0.95f, 0.75f, 0.28f, a)
          case _ => sb.strokePolyline(_armX, _armY, LYRE_N - 1, 0.8f * k, 1f, 0.95f, 0.7f, 0.85f * a)
        }
        side += 2f
      }
      (pass: @scala.annotation.switch) match {
        case 0 => gLine(sb, -6.2f, -10.4f, 6.2f, -10.4f, 2.4f + 2.6f / k, 0.25f, 0.14f, 0.03f, 0.94f * a)
        case 1 => gLine(sb, -6.2f, -10.4f, 6.2f, -10.4f, 2.4f, 0.95f, 0.75f, 0.28f, a)
        case _ => ()
      }
      pass += 1
    }
    // Notes shaken loose
    var q = 0
    while (q < 2) {
      val t = loop(tick, 0.035, q * 0.5 + proj.id * 0.29)
      val nx = cx - ndx * (12f + t * 26f) * ds + (q * 2 - 1) * 9f * ds; val ny = cy - ndy * (12f + t * 26f) * ds - t * 14f * ds
      val na = (1f - t) * a
      sb.fillOval(nx, ny, 2.4f * ds + 1f, 1.9f * ds + 1f, 0.25f, 0.14f, 0.03f, 0.8f * na, 8)
      sb.fillOval(nx, ny, 2.4f * ds, 1.9f * ds, 1f, 0.85f, 0.4f, na, 8)
      sb.strokeLine(nx + 2f * ds, ny, nx + 2f * ds, ny - 7f * ds, 1.4f * ds, 1f, 0.85f, 0.4f, na)
      q += 1
    }
  }

  // ── The Musician ──
  private val PICK = Array(-7.6f, -4f, -6.2f, -6.6f, -3.2f, -7.6f, 3.2f, -7.6f, 6.2f, -6.6f, 7.6f, -4f,
    6.8f, -0.6f, 3.4f, 5.2f, 1.2f, 8.4f, -1.2f, 8.4f, -3.4f, 5.2f, -6.8f, -0.6f)
  private val BOLT_A = Array(1.6f, -5.4f, -2.8f, 1.2f, 0.4f, 0.8f)
  private val BOLT_B = Array(-0.4f, -0.8f, 2.8f, -1.2f, -1.6f, 5.4f)
  private val _pick = new Array[Float](24)

  /**
   * The Musician's sonic boom: a hot-pink guitar pick with a black lightning bolt on it, flipping as it
   * flies and thumping to the beat, rings of the speaker stack's pink and cyan pumping off it.
   */
  private[projectiles] def drawGuitarPick(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 41) * 0.35
    computeAllDynamics(proj, 1f, 0.35f, 0.7f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val bt = loop(tick, 0.04, proj.id * 0.31)
    val thump = if (bt < 0.15f) 1f + 0.14f * Math.sin(bt / 0.15f * Math.PI).toFloat else 1f
    val k = 1.45f * ds * thump
    val dir = if (ndx < 0f) -1f else 1f
    val cx = sx; val cy = sy - 4f * ds

    // Rings pumping off it, pink and cyan in turn
    var r = 0
    while (r < 3) {
      val t = loop(tick, 0.04, r / 3.0 + proj.id * 0.17)
      val rr = (12f + 26f * t) * ds
      val ex = cx - ndx * t * 10f * ds; val ey = cy - ndy * t * 10f * ds
      sb.strokeOval(ex, ey, rr, rr * 0.86f, 3.6f * (1f - t) + 1.4f, 0.05f, 0.02f, 0.06f, 0.4f * (1f - t) * a, 22)
      if ((r & 1) == 0) sb.strokeOval(ex, ey, rr, rr * 0.86f, 2.2f * (1f - t) + 0.6f, 1f, 0.38f, 0.72f, 0.85f * (1f - t) * a, 22)
      else sb.strokeOval(ex, ey, rr, rr * 0.86f, 2.2f * (1f - t) + 0.6f, 0.35f, 0.95f, 1f, 0.85f * (1f - t) * a, 22)
      r += 1
    }
    sb.fillOvalSoft(cx, cy, 26f * ds * dynGlow, 24f * ds * dynGlow, 1f, 0.4f, 0.75f, 0.28f * a, 0f, 16)
    // Flipping as it spins: its width the cosine of the flip, its back a darker pink
    val flip = Math.cos(phase * 0.8).toFloat
    val wx = 0.3f + 0.7f * Math.abs(flip)
    frame(cx, cy, (phase * 0.45).toFloat * dir, k)
    var i = 0
    while (i < 12) { _pick(i * 2) = PICK(i * 2) * wx; _pick(i * 2 + 1) = PICK(i * 2 + 1); i += 1 }
    var n = pts(_pick)
    sb.strokePolygon(_gx, _gy, n, 3f, 0.05f, 0.02f, 0.06f, 0.95f * a)
    if (flip >= 0f) {
      sb.fillPolygon(_gx, _gy, n, 1f, 0.36f, 0.7f, a)
      // The bolt on its face
      i = 0
      while (i < 3) { _pick(i * 2) = BOLT_A(i * 2) * wx; _pick(i * 2 + 1) = BOLT_A(i * 2 + 1); i += 1 }
      _gx(0) = fx(_pick(0), _pick(1)); _gy(0) = fy(_pick(0), _pick(1))
      _gx(1) = fx(_pick(2), _pick(3)); _gy(1) = fy(_pick(2), _pick(3))
      _gx(2) = fx(_pick(4), _pick(5)); _gy(2) = fy(_pick(4), _pick(5))
      sb.fillPolygon(_gx, _gy, 3, 0.06f, 0.03f, 0.08f, a)
      i = 0
      while (i < 3) { _pick(i * 2) = BOLT_B(i * 2) * wx; _pick(i * 2 + 1) = BOLT_B(i * 2 + 1); i += 1 }
      _gx(0) = fx(_pick(0), _pick(1)); _gy(0) = fy(_pick(0), _pick(1))
      _gx(1) = fx(_pick(2), _pick(3)); _gy(1) = fy(_pick(2), _pick(3))
      _gx(2) = fx(_pick(4), _pick(5)); _gy(2) = fy(_pick(4), _pick(5))
      sb.fillPolygon(_gx, _gy, 3, 0.06f, 0.03f, 0.08f, a)
      gLine(sb, -5.6f * wx, -5.6f, -2f * wx, -6.4f, 1.2f, 1f, 0.85f, 0.95f, 0.85f * a)
    } else {
      sb.fillPolygon(_gx, _gy, n, 0.72f, 0.2f, 0.5f, a)
      gLine(sb, -4.6f * wx, -5.6f, 4.6f * wx, -5.6f, 0.9f, 0.9f, 0.5f, 0.75f, 0.7f * a)
    }
    // Cyan sparks off it
    var s = 0
    while (s < 3) {
      val t = loop(tick, 0.07, s / 3.0 + proj.id * 0.23)
      val ang = s * 2.1 + proj.id + t
      val d = (10f + t * 14f) * ds
      sb.fillStarFlare(cx + Math.cos(ang).toFloat * d - ndx * t * 12f * ds, cy + Math.sin(ang).toFloat * d * 0.8f - ndy * t * 12f * ds,
        5f * ds * (1f - t), 1.4f, ang.toFloat, 0.6f, 0.45f, 1f, 1f, (1f - t) * a)
      s += 1
    }
  }
}
