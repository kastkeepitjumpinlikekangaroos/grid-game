package com.gridgame.client.render.blasts

import GLBlastRenderers._
import BlastKit._
import Pieces._

/** Things that pull inward: vortices, gravity, implosions — and the Chronomancer's clock running backwards. */
private[blasts] object Inward {
  /** Spiral arms turning in over the ground, `arms` of them, tightening as the blast goes on. */
  private def spiralArms(arms: Int, t0: Float, t1: Float, spin: Float, twist: Float, w: Float,
                         r: Float, g: Float, b: Float, a0: Float): Unit = {
    if (layer != GROUND) return
    val p = seg(T, t0, t1)
    if (p <= 0f || p >= 1f) return
    val a = a0 * life(p, 0.15f, 0.6f)
    val shrink = 1f - 0.45f * smooth(p)
    var j = 0
    while (j < arms) {
      val base = j * TWO_PI / arms + spin * MS * 0.001f + hash(seed, 1900) * PI
      var i = 0
      while (i < 12) {
        val u = i / 11f
        val rho = (0.95f - 0.82f * u) * shrink
        val th = base + twist * u
        _px(i) = gx(cosf(th), rho); _py(i) = gy(sinf(th), rho)
        _pw(i) = w * k * (0.3f + 0.9f * u) + 2.4f
        _pa(i) = a * (0.25f + 0.75f * u) * 0.6f
        i += 1
      }
      sb.strokePolylineVar(_px, _py, _pw, _pa, 12, ink(r), ink(g), ink(b))
      i = 0
      while (i < 12) { _pw(i) -= 2.4f; _pa(i) /= 0.6f; i += 1 }
      sb.strokePolylineVar(_px, _py, _pw, _pa, 12, r, g, b)
      i = 0
      while (i < 12) { _pw(i) *= 0.35f; i += 1 }
      sb.strokePolylineVar(_px, _py, _pw, _pa, 12, lit(r), lit(g), lit(b))
      j += 1
    }
  }

  /** Rings closing in on the middle, one after another. */
  private def closingRings(n: Int, t0: Float, t1: Float, w: Float, r: Float, g: Float, b: Float, a0: Float): Unit = {
    if (layer != GROUND) return
    var i = 0
    while (i < n) {
      val start = t0 + (t1 - t0) * 0.3f * i / Math.max(1, n - 1)
      val p = seg(T, start, start + (t1 - t0) * 0.7f)
      if (p > 0f && p < 1f) {
        val q = 1f - 0.9f * smooth(p)
        val a = a0 * life(p, 0.2f, 0.7f)
        sb.strokeOval(cx, cy, W * q, H * q, w * k * (1f + p) + 2.4f, ink(r), ink(g), ink(b), 0.45f * a, 24)
        sb.strokeOval(cx, cy, W * q, H * q, w * k * (1f + p), r, g, b, a, 24)
      }
      i += 1
    }
  }

  /** Motes pulled in on a spiral from the edge to the middle over [t0, t1], additive. */
  private def pullMotes(n0: Int, t0: Float, t1: Float, spiral: Float, size: Float,
                        r: Float, g: Float, b: Float, a0: Float, sd: Int): Unit = {
    if (layer != AIR || T <= t0 || T >= t1) return
    val n = Math.max(1, (n0 * det + 0.5f).toInt)
    sb.setAdditiveBlend(true)
    var i = 0
    while (i < n) {
      val s0 = seed * 71 + sd * 13
      val start = t0 + (t1 - t0) * 0.45f * h01(s0, i)
      val tau = seg(T, start, t1)
      if (tau > 0f && tau < 1f) {
        val th = (i + 0.5f * hash(s0, 20 + i)) * TWO_PI / n + spiral * tau
        val rho = 0.95f * (1f - tau * tau)
        val x = gx(cosf(th), rho); val y = gy(sinf(th), rho) - 6f * k * sinf(tau * PI)
        val a = a0 * Math.min(1f, tau * 5f) * (1f - seg(tau, 0.85f, 1f))
        sb.fillOval(x, y, size * k * 2f, size * k * 2f, r, g, b, 0.3f * a, 8)
        sb.fillOval(x, y, size * k, size * k, mix(r, 1f, 0.6f), mix(g, 1f, 0.6f), mix(b, 1f, 0.6f), a, 6)
      }
      i += 1
    }
    sb.setAdditiveBlend(false)
  }

  /** A hole in the middle: dark, rimmed with light, swelling over [0, 0.15] and collapsing at `pop`. */
  private def blackHole(pop: Float, size: Float, r: Float, g: Float, b: Float): Unit = {
    if (layer != GROUND) return
    val rr = size * easeOut3(seg(T, 0f, 0.15f)) * (1f - smooth(seg(T, pop - 0.08f, pop)))
    if (rr <= 0.005f) return
    sb.fillOval(cx, cy, W * rr, H * rr, 0.03f, 0.01f, 0.06f, 0.95f, 22)
    sb.strokeOval(cx, cy, W * rr, H * rr, 3f * k, r, g, b, 0.95f, 22)
    sb.strokeOval(cx, cy, W * rr * 1.25f, H * rr * 1.25f, 1.4f * k, r, g, b, 0.5f, 22)
  }

  /** A clock face on the ground, its hands running backwards, fast and then slowing. */
  private def clockFace(a: Float): Unit = {
    if (layer != GROUND || a <= 0.01f) return
    val tr = 0.35f; val tg = 0.95f; val tb = 0.85f
    val yr = 1f; val yg = 0.85f; val yb = 0.4f
    sb.strokeOval(cx, cy, W * 0.9f, H * 0.9f, 3.4f * k + 2.6f, ink(tr), ink(tg), ink(tb), 0.5f * a, 24)
    sb.strokeOval(cx, cy, W * 0.9f, H * 0.9f, 3.4f * k, tr, tg, tb, a, 24)
    sb.strokeOval(cx, cy, W * 0.78f, H * 0.78f, 1.4f * k, yr, yg, yb, 0.8f * a, 24)
    var i = 0
    while (i < 12) {
      val th = i * PI / 6 - PI * 0.5f
      val long = i % 3 == 0
      val inner = if (long) 0.64f else 0.71f
      sb.strokeLine(gx(cosf(th), inner), gy(sinf(th), inner), gx(cosf(th), 0.78f), gy(sinf(th), 0.78f),
        (if (long) 2.6f else 1.4f) * k, yr, yg, yb, a)
      i += 1
    }
    val sweep = -(MS * 0.011f) * (1f - 0.6f * T) + hash(seed, 2000) * PI
    var hnd = 0
    while (hnd < 2) {
      val ang = if (hnd == 0) sweep else sweep / 12f + 1.3f
      val len = if (hnd == 0) 0.62f else 0.4f
      _xs(0) = cx; _ys(0) = cy
      _xs(1) = gx(cosf(ang), len); _ys(1) = gy(sinf(ang), len)
      sb.strokePolylineTapered(_xs, _ys, 2, (if (hnd == 0) 3.2f else 4.2f) * k + 2.4f, 1.6f, ink(yr), ink(yg), ink(yb), 0.6f * a, 0.5f * a)
      sb.strokePolylineTapered(_xs, _ys, 2, (if (hnd == 0) 3.2f else 4.2f) * k, 0.6f, yr, yg, yb, a, a)
      hnd += 1
    }
    sb.fillOval(cx, cy, 3f * k, 2f * k, 1f, 0.95f, 0.7f, a, 10)
  }

  /** A tear of light standing in the middle: a lens, `h` tall, inked. */
  private def rift(x: Float, y: Float, w: Float, h: Float, a: Float, r: Float, g: Float, b: Float): Unit = {
    if (a <= 0.02f || h < 2f) return
    var pass = 0
    while (pass < 3) {
      val grow = if (pass == 0) 1.8f else 0f
      val wk = if (pass == 2) 0.4f else 1f
      _xs(0) = x; _ys(0) = y - h * 0.5f - grow
      _xs(1) = x + w * wk + grow; _ys(1) = y - h * 0.18f
      _xs(2) = x + w * wk + grow; _ys(2) = y + h * 0.18f
      _xs(3) = x; _ys(3) = y + h * 0.5f + grow
      _xs(4) = x - w * wk - grow; _ys(4) = y + h * 0.18f
      _xs(5) = x - w * wk - grow; _ys(5) = y - h * 0.18f
      (pass: @scala.annotation.switch) match {
        case 0 => sb.fillPolygon(_xs, _ys, 6, ink(r), ink(g), ink(b), 0.8f * a)
        case 1 => sb.fillPolygon(_xs, _ys, 6, r, g, b, a)
        case _ => sb.fillPolygon(_xs, _ys, 6, 1f, 1f, 1f, a)
      }
      pass += 1
    }
  }

  private[blasts] def inward(st: Int): Unit = {
    (st: @scala.annotation.switch) match {
      case S_GRAVITY =>
        spiralArms(4, 0.02f, 0.72f, 2.2f, 2.4f, 4f, 0.55f, 0.42f, 1f, 0.9f)
        closingRings(3, 0.02f, 0.7f, 2.6f, 0.62f, 0.5f, 1f, 0.9f)
        blackHole(0.7f, 0.2f, 0.7f, 0.55f, 1f)
        shockRing(0.68f, 0.98f, 5f, 0.66f, 0.55f, 1f, 0.9f)
        if (layer != AIR) return
        pullMotes(12, 0.02f, 0.7f, 2.6f, 1.6f, 0.65f, 0.55f, 1f, 1f, 1)
        flash(0.12f, 0.5f, 0.7f, 0.6f, 1f, 1f, at = 0.68f)
        debris(D_SPARK, 10, 0.68f, 0.98f, 1f, 16f, 2f, 0.7f, 0.55f, 1f, 1)
      case S_VOID_PULL =>
        scorch(0.6f, 0.06f, 0.02f, 0.1f, 0.5f)
        closingRings(3, 0.02f, 0.68f, 3f, 0.55f, 0.3f, 0.9f, 0.9f)
        streaks(12, 0.02f, 0.62f, 1f, 0.12f, 0.3f, 3f, 0.4f, 0.2f, 0.7f, 0.85f, 1)
        blackHole(0.68f, 0.24f, 0.6f, 0.3f, 1f)
        shockRing(0.66f, 0.96f, 5f, 0.6f, 0.3f, 1f, 0.85f)
        if (layer != AIR) return
        puffs(8, 0.02f, 0.7f, 0.9f, 8f, 10f, 16f, 0.18f, 0.08f, 0.26f, 0.65f, 1, outward = -0.75f, flat = 0.7f, inkK = 0.6f)
        flash(0.12f, 0.45f, 0.6f, 0.3f, 1f, 0.9f, at = 0.66f)
        debris(D_SPARK, 12, 0.66f, 0.98f, 1f, 18f, 2.2f, 0.65f, 0.35f, 1f, 1)
      case S_FIRE_VORTEX =>
        scorch(0.6f, 0.14f, 0.05f, 0.02f, 0.5f)
        spiralArms(5, 0.02f, 0.75f, 3f, 3f, 11f, 1f, 0.5f, 0.1f, 0.95f)
        flames(5, 0.1f, 0.04f, scatter = false, 38f, 6f, 0.06f, 0f, 0.62f, 0.98f, 0.42f, 0.08f, 1f, 0.88f, 0.45f, 1)
        shockRing(0.7f, 0.97f, 6f, 1f, 0.55f, 0.15f, 0.85f)
        if (layer != AIR) return
        pullMotes(14, 0.02f, 0.72f, 3f, 1.6f, 1f, 0.6f, 0.2f, 1f, 1)
        flash(0.12f, 0.55f, 1f, 0.7f, 0.3f, 1f, at = 0.7f)
        puffs(3, 0.55f, 1f, 0.1f, 50f, 10f, 20f, 0.24f, 0.18f, 0.17f, 0.6f, 2, lift = 16f)
      case _ => // S_TEMPORAL
        clockFace(0.95f * life(T, 0.08f, 0.68f))
        closingRings(2, 0.1f, 0.8f, 2f, 0.4f, 0.95f, 0.88f, 0.7f)
        if (layer != AIR) return
        // Shards of time turning back round it, drifting in
        var i = 0
        while (i < 8) {
          val p = seg(T, 0.04f, 0.92f)
          if (p > 0f && p < 1f) {
            val th = (i + 0.3f * hash(seed, 2100 + i)) * TWO_PI / 8 - p * 2.6f
            val rr = 0.66f - 0.36f * smooth(p)
            val x = gx(cosf(th), rr); val y = gy(sinf(th), rr) - (8f + 4f * sinf(MS * 0.006f + i)) * k
            piece(D_SHARD, x, y, 2.6f * k, th * 2f, 1f, 0.9f, 0.55f, life(p, 0.1f, 0.75f))
          }
          i += 1
        }
        val rp = seg(T, 0.06f, 0.88f)
        if (rp > 0f && rp < 1f) {
          val h = 28f * k * easeOut3(seg(rp, 0f, 0.25f)) * (1f - smooth(seg(rp, 0.7f, 1f)))
          sb.fillOvalSoft(cx, cy - 16f * k, 12f * k, 20f * k, 0.4f, 1f, 0.9f, 0.35f * life(rp, 0.1f, 0.7f), 0f, 16)
          rift(cx, cy - 16f * k, 4.5f * k, h, life(rp, 0.1f, 0.75f), 0.45f, 1f, 0.9f)
        }
        glints(4, 0.05f, 0.8f, 0.7f, 20f, 8f, 1f, 0.92f, 0.6f, 0.9f)
    }
  }
}
