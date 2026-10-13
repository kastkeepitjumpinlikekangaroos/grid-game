package com.gridgame.client.render.projectiles

import com.gridgame.common.model.Projectile
import com.gridgame.client.gl.ShapeBatch
import ProjectileKit._
import Glyphs._

/**
 * The vortex bomb — a slow thing that drags everything near it in — drawn as whichever of its three
 * throwers made it: the Graviton's black hole, its disk of hot gas bent round it; the Inferno's fire
 * whirl, a tornado of flame; the Shadowfiend's maw, a ring of fangs round the void with its third eye in
 * the dark of it. All three were the same purple ring on the ground.
 */
private[render] object Vortices {

  // ── The Graviton ──
  /** Half of the accretion disk (the far half behind the hole, the near half in front): nested bands,
    * outer to inner, inked at its rim, with hot streaks going round it. */
  private def disk(sb: ShapeBatch, cx: Float, cy: Float, R: Float, tilt: Float, front: Boolean, phase: Double, a: Float): Unit = {
    val start = if (front) 0f else Math.PI.toFloat
    val sweep = Math.PI.toFloat
    sb.fillArcBand(cx, cy, R * 1.25f, R * 1.25f * tilt, R * 2.8f, R * 2.8f * tilt, start, sweep, 18, 0.5f, 0.22f, 0.92f, 0.85f * a, 0.85f * a)
    sb.fillArcBand(cx, cy, R * 1.2f, R * 1.2f * tilt, R * 2.15f, R * 2.15f * tilt, start, sweep, 18, 0.88f, 0.42f, 1f, 0.9f * a, 0.9f * a)
    sb.fillArcBand(cx, cy, R * 1.12f, R * 1.12f * tilt, R * 1.6f, R * 1.6f * tilt, start, sweep, 16, 1f, 0.86f, 1f, 0.95f * a, 0.95f * a)
    sb.strokeArc(cx, cy, R * 2.8f, R * 2.8f * tilt, start, sweep, 2f, 0.12f, 0.03f, 0.22f, 0.7f * a, 18)
    var k = 0
    while (k < 4) {
      val ang = (phase * 1.7 + k * (Math.PI / 2)) % (2 * Math.PI)
      val inFront = Math.sin(ang) >= 0
      if (inFront == front) {
        val rr = R * (1.5f + 0.3f * k)
        sb.strokeArc(cx, cy, rr, rr * tilt, ang.toFloat, 0.55f, 1.6f, 1f, 0.95f, 1f, 0.9f * a, 5)
      }
      k += 1
    }
  }

  /**
   * The Graviton's vortex bomb: a black hole — a hole of perfect black, a ring of light hugging it, its
   * disk of hot gas whirling round it and passing in front, the light of the disk's far side bent up
   * over the top of it — with debris spiralling in.
   */
  private[projectiles] def drawBlackHole(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 37) * 0.3
    computeAllDynamics(proj, 0.7f, 0.4f, 1f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val cx = sx; val cy = sy - 5f * ds + Math.sin(phase * 0.5).toFloat * 1.2f * ds
    val R = 10.5f * ds
    val tilt = 0.27f + 0.04f * Math.sin(phase * 0.3).toFloat

    Orbs.cometTail(sb, cx, cy, ndx, ndy, 52f * ds, 24f * ds, 0.35f, 0.15f, 0.6f, 0.35f * a)
    sb.fillOvalSoft(cx, cy, 36f * ds * dynGlow, 24f * ds * dynGlow, 0.6f, 0.3f, 1f, 0.26f * a, 0f, 16)
    // Debris spiralling in
    var d = 0
    while (d < 6) {
      val t = loop(tick, 0.03, d / 6.0 + proj.id * 0.17)
      val ang = d * 1.05 + t * 4.0 + proj.id
      val rr = R * (1.3f + (1f - t) * 2.4f)
      val dx = cx + Math.cos(ang).toFloat * rr; val dy = cy + Math.sin(ang).toFloat * rr * tilt * 1.4f
      val s = (1.4f + (1f - t)) * ds
      sb.fillRect(dx - s, dy - s, s * 2f, s * 2f, 0.85f, 0.7f, 1f, Math.min(1f, t * 4f) * a)
      d += 1
    }
    disk(sb, cx, cy, R, tilt, front = false, phase, a)
    // The far side's light, bent up over the top of the hole and under it
    sb.strokeArc(cx, cy, R * 1.45f, R * 1.45f, Math.PI.toFloat, Math.PI.toFloat, 2.6f * ds, 1f, 0.86f, 1f, 0.92f * a, 16)
    sb.strokeArc(cx, cy, R * 1.22f, R * 1.22f, 0f, Math.PI.toFloat, 1.4f * ds, 0.86f, 0.6f, 1f, 0.6f * a, 14)
    // The hole, and the ring of light hugging it
    sb.fillOval(cx, cy, R, R, 0.01f, 0f, 0.02f, a, 22)
    sb.strokeOval(cx, cy, R * 1.05f, R * 1.05f, 1.3f * ds, 1f, 0.92f, 1f, 0.95f * a, 24)
    disk(sb, cx, cy, R, tilt, front = true, phase, a)
  }

  // ── The Inferno ──
  private final val TW_N = 9
  private val _twLX = new Array[Float](TW_N)
  private val _twRX = new Array[Float](TW_N)
  private val _twY = new Array[Float](TW_N)
  private val _twC = new Array[Float](TW_N)
  private val _twR = new Array[Float](TW_N)

  /** The funnel's outline, `k` of its width, into _gx/_gy as its left side up then its right down. */
  private def funnel(k: Float): Int = {
    var i = 0
    while (i < TW_N) {
      _gx(i) = _twC(i) - _twR(i) * k; _gy(i) = _twY(i)
      _gx(2 * TW_N - 1 - i) = _twC(i) + _twR(i) * k; _gy(2 * TW_N - 1 - i) = _twY(i)
      i += 1
    }
    2 * TW_N
  }

  /**
   * The Inferno's fire vortex: a whirl of fire, a tornado of flame standing on the ground and leaning
   * back from where it goes — orange round yellow round white, bands of flame spiralling up it, flames
   * licking off its top into smoke — the ground scorched under it and embers sucked in round its foot.
   */
  private[projectiles] def drawFireTornado(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 41) * 0.35
    computeAllDynamics(proj, 1f, 0.5f, 0.1f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val H = 46f * ds

    // The ground under it, scorched and glowing, and embers sucked in round its foot
    sb.fillOvalSoft(sx, sy, 26f * ds, 11f * ds, 1f, 0.45f, 0.1f, 0.45f * a, 0f, 16)
    sb.strokeOval(sx, sy, 11f * ds, 4.6f * ds, 2.2f * ds, 1f, 0.62f, 0.2f, 0.7f * a, 16)
    var e = 0
    while (e < 6) {
      val t = loop(tick, 0.04, e / 6.0 + proj.id * 0.23)
      val ang = e * 1.05 + t * 3.4 + proj.id
      val rr = (1f - t) * 28f * ds + 6f * ds
      sb.fillOval(sx + Math.cos(ang).toFloat * rr, sy + Math.sin(ang).toFloat * rr * 0.42f - t * 6f * ds,
        (1.8f - t) * ds + 0.5f, (1.8f - t) * ds + 0.5f, 1f, mix(0.6f, 0.95f, t), 0.2f, Math.min(1f, t * 3f) * a, 6)
      e += 1
    }
    // The funnel: levels from the ground up, widening, swaying, leaning back from where it goes
    var i = 0
    while (i < TW_N) {
      val h = i.toFloat / (TW_N - 1)
      _twY(i) = sy - h * H
      _twR(i) = (4.5f + 19f * Math.pow(h, 1.25).toFloat) * ds
      _twC(i) = sx + Math.sin(phase * 0.9 + h * 3.0).toFloat * h * 5f * ds - ndx * h * 9f * ds
      i += 1
    }
    // Flames licking off its sides as it spins, behind the funnel's body
    var layer = 0
    while (layer < 2) {
      var j = 1
      while (j < TW_N) {
        var side = -1f
        while (side <= 1f) {
          val h = j.toFloat / (TW_N - 1)
          val flick = 0.6f + 0.4f * Math.sin(phase * 3.1 + j * 1.9 + side).toFloat
          val dx0 = side * 0.85f - ndx * 0.3f; val dy0 = -0.55f
          val dl = Math.sqrt(dx0 * dx0 + dy0 * dy0).toFloat
          val kk = if (layer == 0) 1f else 0.6f
          flameTongue(_twC(j) + side * _twR(j) * 0.9f, _twY(j), dx0 / dl, dy0 / dl, (5f + 9f * h) * ds * flick * kk,
            ((3.6f + 2.4f * h) * ds + (if (layer == 0) 2.2f else 0f)) * kk, Math.sin(phase * 2.2 + j).toFloat * 2f * ds, a)
          if (layer == 0) sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 5, 0.36f, 0.05f, 0.02f)
          else sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 5, 1f, 0.5f, 0.1f)
          side += 2f
        }
        j += 2
      }
      layer += 1
    }
    var n = funnel(1f)
    sb.strokePolygon(_gx, _gy, n, 3f, 0.36f, 0.05f, 0.02f, 0.9f * a)
    sb.fillRibbon(_gx, _gy, n, 0.9f, 0.28f, 0.05f, 0.95f * a)
    n = funnel(0.7f)
    sb.fillRibbon(_gx, _gy, n, 1f, 0.55f, 0.1f, a)
    n = funnel(0.32f)
    sb.fillRibbon(_gx, _gy, n, 1f, 0.86f, 0.38f, a)
    // Bands of flame spiralling up it, bright where they come round in front
    var s = 0
    while (s < 3) {
      i = 0
      while (i < TW_N) {
        val h = i.toFloat / (TW_N - 1)
        val th = phase * 2.2 + s * (2 * Math.PI / 3) - h * 7.0
        val sn = Math.sin(th).toFloat
        _pvX(i) = _twC(i) + Math.cos(th).toFloat * _twR(i) * 0.9f
        _pvY(i) = _twY(i) + sn * _twR(i) * 0.25f
        _pvW(i) = (2f + 1.6f * h) * ds
        _pvA(i) = (if (sn > 0f) 0.85f else 0f) * a
        i += 1
      }
      sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, TW_N, 1f, 0.95f, 0.62f)
      s += 1
    }
    // Flames licking up off its top, curling outward, and the smoke it throws off
    val top = TW_N - 1
    layer = 0
    while (layer < 2) {
      var f = 0
      while (f < 6) {
        val u = -1f + f * 0.4f
        val rx0 = _twC(top) + u * _twR(top) * 0.85f; val ry0 = _twY(top) + Math.abs(u) * 2f * ds
        val flick = 0.7f + 0.3f * Math.sin(phase * 3.4 + f * 2.2).toFloat
        val dx0 = -ndx * 0.35f + u * 0.7f; val dy0 = -0.9f
        val dl = Math.sqrt(dx0 * dx0 + dy0 * dy0).toFloat
        val kk = if (layer == 0) 1f else 0.6f
        flameTongue(rx0, ry0, dx0 / dl, dy0 / dl, 12f * ds * flick * kk, (5f * ds + (if (layer == 0) 2.2f else 0f)) * kk,
          Math.sin(phase * 2.4 + f).toFloat * 4f * ds, a)
        if (layer == 0) sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 5, 0.36f, 0.05f, 0.02f)
        else sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 5, 1f, 0.62f, 0.15f)
        f += 1
      }
      layer += 1
    }
    var p = 0
    while (p < 3) {
      val t = loop(tick, 0.03, p / 3.0 + proj.id * 0.19)
      sb.fillOvalSoft(_twC(top) - ndx * t * 16f * ds + (p - 1) * 8f * ds, _twY(top) - (6f + t * 20f) * ds,
        (6f + 8f * t) * ds, (5f + 6f * t) * ds, 0.35f, 0.3f, 0.3f, 0.45f * (1f - t) * a, 0f, 12)
      p += 1
    }
  }

  // ── The Shadowfiend ──
  /**
   * The Shadowfiend's void pull: a maw — a ring of fangs round a void, turning slowly, pink light deep in
   * it and the fiend's third eye looking out of the dark — with tendrils of shadow reaching out from its
   * lips and curling back to drag things in.
   */
  private[projectiles] def drawVoidMaw(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 43) * 0.35
    computeAllDynamics(proj, 0.75f, 0.3f, 0.85f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val cx = sx; val cy = sy - 6f * ds + Math.sin(phase * 0.5).toFloat * 1.3f * ds
    val R = 13f * ds

    Orbs.cometTail(sb, cx, cy, ndx, ndy, 46f * ds, 26f * ds, 0.2f, 0.06f, 0.3f, 0.45f * a)
    sb.fillOvalSoft(cx, cy, 34f * ds * dynGlow, 32f * ds * dynGlow, 0.85f, 0.3f, 0.8f, 0.24f * a, 0f, 16)
    // Tendrils reaching out of its lips and curling back in
    var t = 0
    while (t < 5) {
      val th = t * (2 * Math.PI / 5) + phase * 0.18
      val reach = 0.6f + 0.4f * Math.sin(phase * 0.9 + t * 1.7).toFloat
      val curl = if ((t & 1) == 0) 1.0 else -1.0
      var i = 0
      while (i < 6) {
        val u = i / 5f
        val ang = th + u * 1.3 * curl
        val rr = R * (1.08f + 0.9f * Math.sin(u * Math.PI * 0.85).toFloat * reach)
        _pvX(i) = cx + Math.cos(ang).toFloat * rr; _pvY(i) = cy + Math.sin(ang).toFloat * rr * 0.94f
        _pvW(i) = (4.6f * (1f - u) + 0.5f) * ds; _pvA(i) = a
        i += 1
      }
      i = 0
      while (i < 6) { _pvW(i) += 2.2f; _pvA(i) = 0.85f * a; i += 1 }
      sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 6, 0.06f, 0.01f, 0.08f)
      i = 0
      while (i < 6) { _pvW(i) -= 2.2f; _pvA(i) = a; i += 1 }
      sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 6, 0.32f, 0.12f, 0.44f)
      t += 1
    }
    // The lips, the void inside them, a swirl of pink deep in it
    sb.fillOval(cx, cy, R * 1.22f + 2.2f, R * 1.15f + 2.2f, 0.06f, 0.01f, 0.08f, 0.95f * a, 24)
    sb.fillOval(cx, cy, R * 1.22f, R * 1.15f, 0.38f, 0.15f, 0.5f, a, 24)
    sb.fillOval(cx, cy, R * 0.95f, R * 0.89f, 0.03f, 0f, 0.05f, a, 22)
    var arm = 0
    while (arm < 3) {
      var i = 0
      while (i < 5) {
        val u = i / 4f
        val ang = phase * 0.9 + arm * (2 * Math.PI / 3) + u * 2.4
        _pvX(i) = cx + Math.cos(ang).toFloat * R * 0.85f * u; _pvY(i) = cy + Math.sin(ang).toFloat * R * 0.8f * u
        _pvW(i) = 2.4f * ds * u + 0.4f; _pvA(i) = 0.7f * a * u
        i += 1
      }
      sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 5, 0.85f, 0.3f, 0.7f)
      arm += 1
    }
    sb.fillOvalSoft(cx, cy, R * 0.55f, R * 0.5f, 1f, 0.35f, 0.7f, 0.5f * a, 0f, 14)
    // The third eye, looking out of the dark
    _gx(0) = cx - R * 0.42f; _gy(0) = cy
    _gx(1) = cx; _gy(1) = cy - R * 0.2f
    _gx(2) = cx + R * 0.42f; _gy(2) = cy
    _gx(3) = cx; _gy(3) = cy + R * 0.2f
    sb.fillPolygon(_gx, _gy, 4, 1f, 0.5f, 0.78f, a)
    sb.fillOval(cx + ndx * R * 0.08f, cy, R * 0.07f, R * 0.17f, 0.1f, 0.01f, 0.1f, a, 8)
    // Its fangs, round its lips, pointing in, turning slowly
    var k = 0
    while (k < 12) {
      val ang = k * (Math.PI / 6) + phase * 0.12
      val ca = Math.cos(ang).toFloat; val sa = Math.sin(ang).toFloat
      val bx = cx + ca * R * 0.98f; val by = cy + sa * R * 0.92f
      val len = (if ((k & 1) == 0) 0.4f else 0.3f) * R
      _gx(0) = bx - sa * 2.3f * ds; _gy(0) = by + ca * 2.3f * ds
      _gx(1) = bx + sa * 2.3f * ds; _gy(1) = by - ca * 2.3f * ds
      _gx(2) = bx - ca * len; _gy(2) = by - sa * len * 0.94f
      sb.strokePolygon(_gx, _gy, 3, 1.6f, 0.08f, 0.02f, 0.1f, 0.9f * a)
      sb.fillPolygon(_gx, _gy, 3, 0.96f, 0.92f, 0.86f, a)
      k += 1
    }
    sb.strokeOval(cx, cy, R * 1.1f, R * 1.04f, 1.4f * ds, 0.92f, 0.42f, 0.8f, 0.85f * a, 24)
  }
}
