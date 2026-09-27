package com.gridgame.client.render.blasts

import GLBlastRenderers._
import BlastKit._
import Pieces._

/** Sound: rings of it — a lute, a bass, a howl with its moon, a roar's claw marks, a shriek. */
private[blasts] object Sonic {
  /** A ring of `n` teeth, the points `depth` of the radius out past the notches. */
  private def jaggedRing(q: Float, n: Int, depth: Float, w: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    if (layer != GROUND || a <= 0.01f) return
    val rot = hash(seed, 2400) * PI
    var i = 0
    while (i < n) {
      val th = rot + i * TWO_PI / n
      val rr = q * (if ((i & 1) == 0) 1f else 1f - depth)
      _px(i) = gx(cosf(th), rr); _py(i) = gy(sinf(th), rr)
      i += 1
    }
    sb.strokePolygon(_px, _py, n, w + 2.4f, ink(r), ink(g), ink(b), 0.5f * a)
    sb.strokePolygon(_px, _py, n, w, r, g, b, a)
  }

  /** Three claw slashes raked across the ground at (th, rho), drawn in over [t0, t0 + 0.08]. */
  private def clawMarks(th: Float, rho: Float, len: Float, t0: Float, a0: Float): Unit = {
    if (layer != GROUND) return
    val grow = easeOut3(seg(T, t0, t0 + 0.08f))
    val a = a0 * tail(T, 0.6f)
    if (grow <= 0.02f || a <= 0.01f) return
    val px = gx(cosf(th), rho); val py = gy(sinf(th), rho)
    val sa = th + 1.2f
    val dx = cosf(sa) * W; val dy = sinf(sa) * H
    val dl = Math.max(0.01f, Math.sqrt(dx * dx + dy * dy).toFloat)
    val ux = dx / dl; val uy = dy / dl
    val nx = -uy; val ny = ux
    var j = -1
    while (j <= 1) {
      val off = j * 5.5f * k
      val l = len * k * (if (j == 0) 1f else 0.85f)
      val sx0 = px + nx * off - ux * l * 0.5f; val sy0 = py + ny * off - uy * l * 0.5f
      var i = 0
      while (i < 5) {
        val u = i / 4f * grow
        _px(i) = sx0 + ux * l * u + nx * sinf(u * PI) * l * 0.1f
        _py(i) = sy0 + uy * l * u + ny * sinf(u * PI) * l * 0.1f
        _pw(i) = 4.2f * k * sinf((i / 4f) * PI * 0.9f + 0.15f) + 2.6f
        _pa(i) = 0.7f * a
        i += 1
      }
      sb.strokePolylineVar(_px, _py, _pw, _pa, 5, 0.25f, 0.06f, 0.04f)
      i = 0
      while (i < 5) { _pw(i) -= 2.6f; _pa(i) = a; i += 1 }
      sb.strokePolylineVar(_px, _py, _pw, _pa, 5, 0.95f, 0.42f, 0.18f)
      i = 0
      while (i < 5) { _pw(i) *= 0.4f; i += 1 }
      sb.strokePolylineVar(_px, _py, _pw, _pa, 5, 1f, 0.85f, 0.6f)
      j += 1
    }
  }

  /** The Wolf's moon: a crescent over its howl, glowing. */
  private def moon(x: Float, y: Float, rr: Float, a: Float): Unit = {
    if (a <= 0.02f) return
    sb.fillOvalSoft(x, y, rr * 2.2f, rr * 2.2f, 0.6f, 0.72f, 1f, 0.4f * a, 0f, 18)
    val m = 10
    var i = 0
    while (i < m) {
      val u = i.toFloat / (m - 1)
      val ang = PI * 0.5f + 0.2f + (PI - 0.4f) * u
      val ox = x + cosf(ang) * rr; val oy = y + sinf(ang) * rr
      val ix = x + 0.42f * rr + cosf(ang) * 0.82f * rr; val iy = y + sinf(ang) * 0.82f * rr
      val taper = sinf(u * PI)
      _px(i) = ox; _py(i) = oy
      _px(2 * m - 1 - i) = mix(ox, ix, taper); _py(2 * m - 1 - i) = mix(oy, iy, taper)
      i += 1
    }
    sb.fillRibbon(_px, _py, 2 * m, 0.93f, 0.95f, 1f, a)
    sb.strokePolyline(_px, _py, m, 2.2f, 0.25f, 0.3f, 0.5f, 0.7f * a)
  }

  private[blasts] def sonic(st: Int): Unit = {
    (st: @scala.annotation.switch) match {
      case S_LUTE =>
        soundRings(3, 4, 0f, 0.7f, 1f, 4f, 0.72f, 0.5f, 0.95f, 0.9f)
        soundRings(2, 4, 0.1f, 0.8f, 0.8f, 2.5f, 1f, 0.82f, 0.4f, 0.8f)
        if (layer != AIR) return
        flash(0.07f, 0.35f, 0.9f, 0.7f, 1f, 0.7f)
        debris(D_NOTE, 4, 0f, 0.9f, 0.9f, 50f, 3.6f, 0.75f, 0.45f, 0.95f, 1)
        debris(D_NOTE, 3, 0f, 0.9f, 0.85f, 56f, 3.4f, 1f, 0.8f, 0.35f, 2)
        glints(4, 0.05f, 0.7f, 0.8f, 30f, 9f, 1f, 0.8f, 1f, 0.9f)
      case S_BASS =>
        var i = 0
        while (i < 3) {
          val t0 = i * 0.16f
          val even = (i & 1) == 0
          shockRing(t0, t0 + 0.34f, 10f, if (even) 1f else 0.3f, if (even) 0.3f else 0.9f, if (even) 0.82f else 1f, 0.9f)
          i += 1
        }
        // An equaliser standing round it, bouncing to the beat
        val ea = life(T, 0.08f, 0.62f)
        if (ea > 0.01f) {
          val n = 12
          i = 0
          while (i < n) {
            val th = i * TWO_PI / n + hash(seed, 2500) * 0.3f
            _ux(i) = gx(cosf(th), 0.62f); _ordY(i) = gy(sinf(th), 0.62f)
            i += 1
          }
          sortFarToNear(n)
          var j = 0
          val e = MS / 95f
          val e0 = e.toInt
          val f = smooth(e - e0)
          while (j < n) {
            val i2 = _ord(j)
            val x = _ux(i2); val y = _ordY(i2)
            if (mine(y)) {
              val lv = mix(h01(seed + i2, e0), h01(seed + i2, e0 + 1), f)
              val hgt = (6f + 22f * lv) * k * ea
              val bw = 3.2f * k
              sb.fillRect(x - bw - 1.3f, y - hgt - 1.3f, 2f * bw + 2.6f, hgt + 1.3f, 0.12f, 0.04f, 0.16f, 0.85f * ea)
              sb.fillRectGradient(x - bw, y - hgt, 2f * bw, hgt,
                1f, 0.3f, 0.85f, ea, 1f, 0.3f, 0.85f, ea, 0.3f, 0.9f, 1f, ea, 0.3f, 0.9f, 1f, ea)
              sb.fillRect(x - bw, y - hgt - 2.4f * k, 2f * bw, 1.6f * k, 1f, 1f, 1f, 0.9f * ea)
            }
            j += 1
          }
        }
        if (layer != AIR) return
        flash(0.06f, 0.4f, 1f, 0.5f, 0.9f, 0.8f)
        debris(D_NOTE, 3, 0f, 0.9f, 0.85f, 46f, 3.4f, 1f, 0.4f, 0.85f, 1)
        debris(D_NOTE, 2, 0f, 0.9f, 0.85f, 50f, 3.2f, 0.35f, 0.9f, 1f, 2)
      case S_HOWL =>
        scorch(0.5f, 0.6f, 0.7f, 0.95f, 0.18f)
        var i = 0
        while (i < 4) {
          val start = 0.16f * i
          val p = seg(T, start, start + 0.55f)
          if (p > 0f && p < 1f)
            wavyRing(easeOut(p), 0.03f, 11, MS * 0.008f + i * 1.3f, 3.6f * k * (1f - 0.4f * p), 0.74f, 0.84f, 1f, 0.85f * (1f - p))
          i += 1
        }
        if (layer != AIR) return
        val mp = seg(T, 0f, 0.85f)
        if (mp < 1f) moon(cx + 8f * k, cy - 92f * k - 10f * k * easeOut(mp), 20f * k, life(mp, 0.14f, 0.62f))
        motes(10, 0.05f, 0.9f, 0.6f, 40f, 1.4f, 0.7f, 0.82f, 1f, 0.9f, 1)
      case S_ROAR =>
        var i = 0
        while (i < 3) {
          val start = 0.14f * i
          val p = seg(T, start, start + 0.5f)
          if (p > 0f && p < 1f)
            jaggedRing(easeOut(p), 28, 0.07f, 3.4f * k * (1f - 0.4f * p), 1f, 0.6f, 0.25f, 0.9f * (1f - p))
          i += 1
        }
        i = 0
        while (i < 4) {
          clawMarks((i + 0.5f) * PI * 0.5f + hash(seed, 2600) * 0.4f, 0.55f, 34f, 0.04f + 0.04f * i, 0.95f)
          i += 1
        }
        if (layer != AIR) return
        flash(0.07f, 0.4f, 1f, 0.7f, 0.4f, 0.6f)
        debris(D_LEAF, 10, 0f, 0.85f, 0.9f, 36f, 2.6f, 0.72f, 0.45f, 0.22f, 1)
      case _ => // S_SHRIEK
        soundRings(4, 5, 0f, 0.75f, 1f, 4.6f, 0.82f, 0.74f, 1f, 0.95f)
        streaks(12, 0.02f, 0.5f, 0.2f, 1.05f, 0.3f, 3.2f, 0.9f, 0.88f, 1f, 0.9f, 1)
        if (layer != AIR) return
        flash(0.07f, 0.45f, 0.9f, 0.85f, 1f, 0.8f)
        swirl(D_FEATHER, 12, 0f, 1f, 1f, 44f, 0.45f, 4f, 0.62f, 0.46f, 0.4f, 1)
        swirl(D_FEATHER, 6, 0f, 1f, 0.9f, 50f, 0.4f, 3.8f, 0.78f, 0.7f, 0.95f, 2)
    }
  }
}
