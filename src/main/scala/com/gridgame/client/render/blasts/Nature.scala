package com.gridgame.client.render.blasts

import GLBlastRenderers._
import BlastKit._
import Pieces._

/** Nature: thorns, leaves, splinters, vines, root arches and the spider's web. */
private[blasts] object Nature {
  /** Vines whipping up out of the ground all over the footprint, thorned and leafed, curling,
    * and drawing back into it. Only this layer's, far to near. */
  private def vines(n0: Int, len0: Float, w0: Float, r: Float, g: Float, b: Float, sd: Int): Unit = {
    val n = Math.min(24, (n0 * (0.6f + 0.4f * det) + 0.5f).toInt)
    val s0 = seed * 67 + sd * 7
    var i = 0
    while (i < n) {
      val th = (i + 0.5f * hash(s0, i)) * TWO_PI / n
      val rr = 0.25f + 0.67f * h01(s0, 10 + i)
      _ux(i) = gx(cosf(th), rr); _ordY(i) = gy(sinf(th), rr); _us(i) = rr; _uy(i) = cosf(th)
      i += 1
    }
    sortFarToNear(n)
    var j = 0
    while (j < n) {
      val i2 = _ord(j)
      val x = _ux(i2); val y = _ordY(i2)
      if (mine(y)) {
        val start = 0.02f + 0.1f * _us(i2)
        val grow = easeOut3(seg(T, start, start + 0.12f)) * (1f - smooth(seg(T, 0.72f + 0.06f * h01(s0, 20 + i2), 0.97f)))
        if (grow > 0.02f) {
          val side = if (_uy(i2) >= 0f) 1f else -1f
          val curl = side * (1.1f + 0.5f * h01(s0, 30 + i2)) + 0.2f * sinf(MS * 0.005f + i2)
          sb.fillOval(x, y, 5f * k, 2f * k, 0.18f, 0.12f, 0.06f, 0.8f * grow, 10)
          tendril(x, y, -PI * 0.5f + side * 0.22f + 0.15f * hash(s0, 40 + i2), len0 * k * (0.75f + 0.45f * h01(s0, 50 + i2)),
            grow, curl, w0 * k, r, g, b, 1f)
          // Thorns along it and a couple of leaves off it
          var p = 2
          while (p < 8) {
            val ax = _px(p); val ay = _py(p)
            val nx = _py(p + 1) - ay; val ny = -(_px(p + 1) - ax)
            val nl = Math.max(0.01f, Math.sqrt(nx * nx + ny * ny).toFloat)
            val ts = 2.6f * k * grow * (if ((p & 2) == 0) 1f else -1f)
            _xs(0) = ax - (_px(p + 1) - ax) * 0.25f; _ys(0) = ay - (_py(p + 1) - ay) * 0.25f
            _xs(1) = ax + nx / nl * ts; _ys(1) = ay + ny / nl * ts
            _xs(2) = ax + (_px(p + 1) - ax) * 0.25f; _ys(2) = ay + (_py(p + 1) - ay) * 0.25f
            sb.fillPolygon(_xs, _ys, 3, ink(r) * 1.4f, ink(g) * 1.4f, ink(b) * 1.4f, grow)
            p += 2
          }
          piece(D_LEAF, _px(4) + side * 3f * k, _py(4), 2.6f * k * grow, side * 0.6f, 0.35f, 0.72f, 0.28f, grow)
          piece(D_LEAF, _px(6) - side * 3f * k, _py(6), 2.2f * k * grow, -side * 0.8f, 0.4f, 0.78f, 0.3f, grow)
        }
      }
      j += 1
    }
  }

  /** Great roots arching up out of the ground along the ray `th`, from `r0` to `r1` of the
    * footprint, standing `hMax` high at the top of the arch when `grow` is 1. */
  private def rootArch(th: Float, r0: Float, r1: Float, hMax: Float, grow: Float, w0: Float, a: Float): Unit = {
    if (grow <= 0.02f || a <= 0.02f) return
    val c = cosf(th); val s = sinf(th)
    val n = 9
    var i = 0
    while (i < n) {
      val u = i.toFloat / (n - 1)
      val rho = r0 + (r1 - r0) * u
      _px(i) = gx(c, rho)
      _py(i) = gy(s, rho) - hMax * grow * sinf(u * PI)
      _pw(i) = w0 * (1f - 0.5f * u) + 2.8f
      _pa(i) = 0.9f * a
      i += 1
    }
    // Mounds of earth where it goes in and comes out
    sb.fillOval(gx(c, r0), gy(s, r0), w0 * 1.3f + 1.6f, w0 * 0.5f + 1.6f, 0.12f, 0.08f, 0.05f, 0.85f * a, 12)
    sb.fillOval(gx(c, r0), gy(s, r0), w0 * 1.3f, w0 * 0.5f, 0.42f, 0.3f, 0.18f, a, 12)
    sb.fillOval(gx(c, r1), gy(s, r1), w0 * 0.9f + 1.6f, w0 * 0.36f + 1.6f, 0.12f, 0.08f, 0.05f, 0.85f * a, 12)
    sb.fillOval(gx(c, r1), gy(s, r1), w0 * 0.9f, w0 * 0.36f, 0.42f, 0.3f, 0.18f, a, 12)
    sb.strokePolylineVar(_px, _py, _pw, _pa, n, 0.12f, 0.08f, 0.05f)
    i = 0
    while (i < n) { _pw(i) -= 2.8f; _pa(i) = a; i += 1 }
    sb.strokePolylineVar(_px, _py, _pw, _pa, n, 0.44f, 0.3f, 0.18f)
    i = 0
    while (i < n) { _py(i) -= _pw(i) * 0.22f; _pw(i) *= 0.3f; _pa(i) = 0.85f * a; i += 1 }
    sb.strokePolylineVar(_px, _py, _pw, _pa, n, 0.66f, 0.5f, 0.32f)
    // Bark rings across it, and moss on its back
    var m = 2
    while (m < n - 1) {
      val tx = _px(m + 1) - _px(m - 1); val ty = _py(m + 1) - _py(m - 1)
      val tl = Math.max(0.01f, Math.sqrt(tx * tx + ty * ty).toFloat)
      val half = w0 * (1f - 0.5f * m / (n - 1)) * 0.45f
      sb.strokeLine(_px(m) - ty / tl * half, _py(m) + tx / tl * half, _px(m) + ty / tl * half, _py(m) - tx / tl * half,
        1.3f, 0.2f, 0.13f, 0.07f, 0.8f * a)
      m += 2
    }
    sb.fillOval(_px(3), _py(3) - w0 * 0.2f, w0 * 0.55f, w0 * 0.3f, 0.36f, 0.6f, 0.24f, 0.9f * a, 10)
  }

  /** The Spider's web, cast out over the ground: threads racing out from the middle, then the
    * spiral strung between them, sagging a little between each pair. */
  private def web(): Unit = {
    if (layer != GROUND) return
    val a = tail(T, 0.72f)
    if (a <= 0.01f) return
    val n = 12
    val out = easeOut3(seg(T, 0f, 0.18f))
    val rot = hash(seed, 1100) * 0.3f
    var i = 0
    while (i < n) {
      val th = rot + (i + 0.2f * hash(seed, 1110 + i)) * TWO_PI / n
      val rr = 0.95f + 0.05f * hash(seed, 1120 + i)
      _ux(i) = cosf(th); _uy(i) = sinf(th); _us(i) = rr
      val ex = gx(_ux(i), rr * out); val ey = gy(_uy(i), rr * out)
      sb.strokeLine(cx, cy, ex, ey, 2.8f, 0.2f, 0.2f, 0.26f, 0.35f * a)
      sb.strokeLine(cx, cy, ex, ey, 1.3f * k, 0.95f, 0.95f, 1f, 0.95f * a)
      i += 1
    }
    var ring = 0
    while (ring < 5) {
      val rr = 0.2f + 0.17f * ring
      val on = seg(T, 0.06f + 0.035f * ring, 0.14f + 0.035f * ring)
      if (on > 0f) {
        var m = 0
        i = 0
        while (i <= n) {
          val i0 = i % n
          val pr = rr * _us(i0)
          _px(m) = gx(_ux(i0), pr); _py(m) = gy(_uy(i0), pr); m += 1
          if (i < n) {
            val i1 = (i + 1) % n
            // Sagging toward the middle between two spokes
            val mx = (_ux(i0) + _ux(i1)) * 0.5f; val my = (_uy(i0) + _uy(i1)) * 0.5f
            val ml = Math.sqrt(mx * mx + my * my).toFloat
            val sag = rr * 0.9f * (_us(i0) + _us(i1)) * 0.5f
            _px(m) = gx(mx / ml, sag * ml); _py(m) = gy(my / ml, sag * ml); m += 1
          }
          i += 1
        }
        val ra = a * on
        sb.strokePolyline(_px, _py, m, 2.6f, 0.2f, 0.2f, 0.26f, 0.3f * ra)
        sb.strokePolyline(_px, _py, m, 1.1f * k, 0.95f, 0.95f, 1f, 0.9f * ra)
      }
      ring += 1
    }
    // Silk bunched where it landed
    sb.fillOval(cx, cy, 6f * k + 1.4f, 3.2f * k + 1.4f, 0.25f, 0.25f, 0.3f, 0.6f * a, 12)
    sb.fillOval(cx, cy, 6f * k, 3.2f * k, 0.96f, 0.96f, 1f, a, 12)
  }

  private[blasts] def nature(st: Int): Unit = {
    (st: @scala.annotation.switch) match {
      case S_THORNS =>
        scorch(0.42f, 0.15f, 0.2f, 0.08f, 0.35f)
        shockRing(0f, 0.3f, 4f, 0.5f, 0.8f, 0.3f, 0.85f)
        // A burst of thorns thrust out of where it struck, splaying out over the ground
        spikeRing(9, 0.36f, 0.1f, 24f, 0f, 0.5f, 0.34f, 0.54f, 0.18f, glass = false, 1, tilt = 1.1f)
        spikeRing(5, 0.13f, 0.05f, 18f, 0.02f, 0.5f, 0.3f, 0.48f, 0.16f, glass = false, 2, tilt = 0.6f)
        if (layer != AIR) return
        flash(0.06f, 0.3f, 0.7f, 1f, 0.5f, 0.6f)
        debris(D_LEAF, 5, 0.02f, 0.8f, 0.85f, 30f, 3f, 0.35f, 0.65f, 0.25f, 1)
        debris(D_DROP, 6, 0f, 0.5f, 0.75f, 20f, 2.2f, 0.55f, 0.75f, 0.2f, 2)
      case S_LEAVES =>
        scorch(0.4f, 0.25f, 0.45f, 0.15f, 0.25f)
        edgeRing(0.02f, 0.8f, 2f, 0.5f, 0.9f, 0.4f, 0.8f)
        if (layer != AIR) return
        flash(0.08f, 0.45f, 0.7f, 1f, 0.6f, 0.8f)
        // A whirl of leaves, a few of them gone gold
        swirl(D_LEAF, 11, 0f, 1f, 1f, 32f, 0.7f, 4.4f, 0.35f, 0.7f, 0.25f, 1)
        swirl(D_LEAF, 5, 0f, 1f, 0.9f, 38f, 0.6f, 4.2f, 0.88f, 0.75f, 0.25f, 2)
        motes(8, 0.05f, 0.9f, 0.6f, 34f, 1.5f, 0.6f, 1f, 0.5f, 1f, 3)
      case S_SPLINTERS =>
        scorch(0.3f, 0.2f, 0.14f, 0.08f, 0.3f)
        if (layer != AIR) return
        debris(D_SPLINTER, 12, 0f, 0.8f, 0.95f, 34f, 3.6f, 0.6f, 0.44f, 0.25f, 1, shadow = true)
        debris(D_CHIP, 6, 0f, 0.75f, 0.85f, 28f, 3f, 0.32f, 0.23f, 0.14f, 2)
        swirl(D_LEAF, 4, 0f, 0.95f, 0.8f, 30f, 0.5f, 3.2f, 0.4f, 0.64f, 0.26f, 3)
        debris(D_DROP, 4, 0f, 0.5f, 0.6f, 18f, 2.2f, 0.95f, 0.65f, 0.2f, 4)
        puffs(3, 0.02f, 0.8f, 0.2f, 16f, 7f, 14f, 0.72f, 0.6f, 0.44f, 0.6f, 5, outward = 0.3f, flat = 0.7f, inkK = 0.5f)
      case S_ENTANGLE =>
        scorch(0.5f, 0.18f, 0.3f, 0.1f, 0.3f)
        edgeRing(0.04f, 0.9f, 3f, 0.3f, 0.6f, 0.2f, 0.85f)
        vines(13, 56f, 8f, 0.26f, 0.55f, 0.18f, 1)
        if (layer != AIR) return
        debris(D_LEAF, 6, 0.05f, 0.95f, 0.9f, 40f, 3f, 0.35f, 0.66f, 0.26f, 1)
      case S_ROOTS =>
        cracks(8, 0.9f, 0.02f, 0.2f, 4.6f, 0.13f, 0.09f, 0.05f, 0.7f)
        scorch(0.45f, 0.22f, 0.16f, 0.1f, 0.3f)
        // The great roots, each belonging to the layer its middle stands in
        var i = 0
        while (i < 7) {
          val th = (i + 0.4f * hash(seed, 1200 + i)) * TWO_PI / 7
          val r0 = 0.2f + 0.15f * h01(seed, 1210 + i)
          val r1 = 0.62f + 0.28f * h01(seed, 1220 + i)
          if (mine(gy(sinf(th), (r0 + r1) * 0.5f))) {
            val start = 0.03f + 0.02f * i
            val grow = easeOut3(seg(T, start, start + 0.16f)) * (1f - smooth(seg(T, 0.74f, 0.97f)))
            rootArch(th, r0, r1, (16f + 10f * h01(seed, 1230 + i)) * k, grow, 7f * k, 1f)
          }
          i += 1
        }
        vines(5, 22f, 4f, 0.4f, 0.28f, 0.16f, 2)
        if (layer != AIR) return
        debris(D_CHIP, 12, 0.02f, 0.8f, 0.95f, 40f, 3.2f, 0.38f, 0.27f, 0.16f, 1, shadow = true)
        debris(D_LEAF, 4, 0.05f, 0.9f, 0.8f, 36f, 2.8f, 0.42f, 0.6f, 0.25f, 2)
        puffs(8, 0.05f, 0.85f, 0.7f, 12f, 14f, 26f, 0.6f, 0.5f, 0.38f, 0.45f, 3, flat = 0.56f, inkK = 0.4f)
      case _ => // S_WEB
        web()
        glints(5, 0.15f, 0.8f, 0.9f, 2f, 8f, 1f, 1f, 1f, 0.9f)
    }
  }
}
