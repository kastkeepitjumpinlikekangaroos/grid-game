package com.gridgame.client.render.blasts

import GLBlastRenderers._
import BlastKit._
import Pieces._

/** Liquids and gas: splats and droplets of plague, alchemy, acid, mud and ink, and clouds of miasma and
  * venom (a skull rising out of the Plague Doctor's). */
private[blasts] object Liquids {
  /** A few flies circling `height` units over the middle (the plague). */
  private def flies(n: Int, t0: Float, t1: Float, height: Float): Unit = {
    if (layer != AIR || T <= t0 || T >= t1) return
    val a = life(seg(T, t0, t1), 0.15f, 0.7f)
    var i = 0
    while (i < n) {
      val ph = MS * (0.009f + 0.004f * h01(seed, 600 + i)) + i * 1.9f
      val rr = (7f + 5f * h01(seed, 610 + i)) * k
      val x = cx + cosf(ph) * rr + sinf(ph * 2.3f) * 3f * k
      val y = cy - height * k + sinf(ph * 1.3f) * rr * 0.45f
      val flap = 0.5f + 0.5f * sinf(MS * 0.12f + i)
      sb.fillOval(x - 1.4f, y - 1.2f, 1.6f, 1.1f * flap + 0.3f, 0.85f, 0.9f, 0.85f, 0.55f * a, 6)
      sb.fillOval(x + 1.4f, y - 1.2f, 1.6f, 1.1f * flap + 0.3f, 0.85f, 0.9f, 0.85f, 0.55f * a, 6)
      sb.fillOval(x, y, 1.5f, 1.2f, 0.06f, 0.07f, 0.04f, 0.95f * a, 6)
      i += 1
    }
  }

  /** Bubbles floating up off it, rings with a glint. */
  private def risingBubbles(n: Int, t0: Float, t1: Float, spread: Float, rise: Float, size: Float,
                            r: Float, g: Float, b: Float, a0: Float, sd: Int): Unit = {
    if (layer != AIR || T <= t0 || T >= t1) return
    var i = 0
    while (i < n) {
      val s0 = seed * 29 + sd * 11 + i * 7
      val start = t0 + (t1 - t0) * 0.5f * h01(s0, 1)
      val tau = seg(T, start, t1)
      if (tau > 0f && tau < 1f) {
        val th = h01(s0, 2) * TWO_PI
        val rho = spread * h01(s0, 3)
        val x = gx(cosf(th), rho) + sinf(tau * 7f + i) * 2.5f * k
        val y = gy(sinf(th), rho) - rise * k * easeOut(tau)
        val bs = size * k * (0.6f + 0.6f * h01(s0, 4)) * (0.6f + 0.4f * tau)
        val a = a0 * Math.min(1f, tau * 6f) * (1f - tau)
        sb.strokeOval(x, y, bs, bs, 2.2f, ink(r), ink(g), ink(b), 0.6f * a, 10)
        sb.fillOval(x, y, bs, bs, r, g, b, 0.35f * a, 10)
        sb.strokeOval(x, y, bs, bs, 0.9f, lit(r), lit(g), lit(b), a, 10)
        sb.fillOval(x - bs * 0.35f, y - bs * 0.35f, bs * 0.25f, bs * 0.22f, 1f, 1f, 1f, 0.9f * a, 6)
      }
      i += 1
    }
  }

  /** Tentacles rising out of the ink, far to near: up, swaying, and slapping back down. */
  private def tentacles(n: Int): Unit = {
    val s0 = seed * 41
    var i = 0
    while (i < n) {
      val th = (i + 0.5f * hash(s0, i)) * TWO_PI / n
      val rr = 0.25f + 0.45f * h01(s0, 10 + i)
      _ordY(i) = gy(sinf(th), rr); _ux(i) = gx(cosf(th), rr); _us(i) = cosf(th)
      i += 1
    }
    sortFarToNear(n)
    var j = 0
    while (j < n) {
      val i2 = _ord(j)
      val x = _ux(i2); val y = _ordY(i2)
      if (mine(y)) {
        val start = 0.08f + 0.12f * h01(s0, 20 + i2)
        val rise = easeOut3(seg(T, start, start + 0.18f)) * (1f - smooth(seg(T, 0.66f, 0.94f)))
        if (rise > 0.02f) {
          val side = if (_us(i2) >= 0f) 1f else -1f
          sb.strokeOval(x, y, 9f * k, 3.4f * k, 1.8f, 0.45f, 0.3f, 0.55f, 0.6f * rise, 12)
          tendril(x, y, -PI * 0.5f + side * 0.25f, (44f + 18f * h01(s0, 30 + i2)) * k, rise,
            side * (1.5f + 0.5f * sinf(MS * 0.006f + i2)), 10f * k, 0.32f, 0.14f, 0.38f, 1f,
            hr = 0.72f, hg = 0.46f, hb = 0.82f)
          // Suckers down its inner side
          var p = 2
          while (p < 8) {
            sb.fillOval(_px(p) - side * 2.2f * k, _py(p), 1.7f * k, 1.3f * k, 0.9f, 0.72f, 0.88f, 0.9f * rise, 6)
            p += 2
          }
        }
      }
      j += 1
    }
  }

  private[blasts] def liquid(st: Int): Unit = {
    (st: @scala.annotation.switch) match {
      case S_PLAGUE =>
        splat(0.3f, easeOut3(seg(T, 0f, 0.1f)), 0.35f, 0.55f, 0.12f, 0.85f * tail(T, 0.55f), 1, glossA = 0.5f, drops = 6)
        if (layer != AIR) return
        puffs(5, 0.02f, 0.95f, 0.2f, 18f, 8f, 18f, 0.45f, 0.72f, 0.2f, 0.75f, 1, outward = 0.3f, flat = 0.7f)
        bubbles(3, 0.1f, 0.7f, 0.25f, 3f, 0.5f, 0.8f, 0.25f, 0.9f, 1)
        debris(D_DROP, 6, 0f, 0.5f, 0.75f, 20f, 2.4f, 0.5f, 0.8f, 0.2f, 2)
        flies(4, 0.15f, 0.98f, 16f)
      case S_ALCHEMY =>
        splat(0.26f, easeOut3(seg(T, 0f, 0.1f)), 0.55f, 0.95f, 0.3f, 0.8f * tail(T, 0.5f), 1, drops = 5)
        if (layer != AIR) return
        flash(0.07f, 0.4f, 0.8f, 1f, 0.6f)
        debris(D_DROP, 5, 0f, 0.55f, 0.85f, 28f, 3f, 0.55f, 0.95f, 0.3f, 1)
        debris(D_DROP, 4, 0f, 0.55f, 0.8f, 32f, 3f, 0.95f, 0.3f, 0.8f, 2)
        debris(D_DROP, 4, 0f, 0.55f, 0.85f, 26f, 3f, 0.3f, 0.85f, 1f, 3)
        risingBubbles(8, 0.05f, 0.95f, 0.45f, 40f, 3.8f, 0.7f, 0.95f, 0.85f, 0.95f, 1)
        glints(4, 0.05f, 0.6f, 0.7f, 20f, 8f, 1f, 1f, 0.8f, 0.9f)
      case S_BLIGHT =>
        splat(0.42f, easeOut3(seg(T, 0.02f, 0.16f)), 0.3f, 0.42f, 0.12f, 0.9f * tail(T, 0.6f), 1, glossA = 0.45f, drops = 9)
        shockRing(0f, 0.25f, 5f, 0.55f, 0.75f, 0.3f, 0.8f)
        if (layer != AIR) return
        flash(0.06f, 0.45f, 0.7f, 0.95f, 0.5f)
        debris(D_SHARD, 9, 0f, 0.6f, 0.95f, 30f, 3.2f, 0.75f, 0.95f, 0.8f, 1)
        debris(D_GLOB, 8, 0f, 0.7f, 0.9f, 36f, 3.2f, 0.3f, 0.44f, 0.12f, 2, shadow = true)
        puffs(6, 0.1f, 1f, 0.3f, 40f, 10f, 24f, 0.5f, 0.35f, 0.6f, 0.7f, 1, outward = 0.35f, lift = 6f)
        puffs(3, 0.2f, 1f, 0.1f, 50f, 10f, 20f, 0.45f, 0.62f, 0.22f, 0.6f, 2, lift = 10f)
        bubbles(5, 0.15f, 0.85f, 0.35f, 3.4f, 0.45f, 0.6f, 0.2f, 0.9f, 3)
      case S_ACID_FLASK =>
        splat(0.38f, easeOut3(seg(T, 0f, 0.14f)), 0.72f, 0.92f, 0.2f, 0.9f * tail(T, 0.6f), 1, glossA = 0.6f, drops = 8)
        if (layer != AIR) return
        flash(0.06f, 0.45f, 0.85f, 1f, 0.5f)
        debris(D_SHARD, 9, 0f, 0.55f, 0.95f, 28f, 3f, 0.8f, 0.92f, 1f, 1)
        debris(D_DROP, 9, 0f, 0.6f, 0.9f, 30f, 2.6f, 0.72f, 0.92f, 0.2f, 2)
        // It fizzes: bubbles on it and steam off it
        bubbles(7, 0.1f, 0.9f, 0.35f, 2.6f, 0.85f, 1f, 0.5f, 0.9f, 3)
        puffs(5, 0.15f, 1f, 0.3f, 36f, 6f, 14f, 0.9f, 0.95f, 0.85f, 0.55f, 4, lift = 4f, inkK = 0.5f)
      case S_ACID_SPRAY =>
        splat(0.34f, easeOut3(seg(T, 0f, 0.14f)), 0.78f, 0.85f, 0.15f, 0.85f * tail(T, 0.55f), 1, drops = 10)
        if (layer == GROUND) {
          // Burning holes in what it landed on
          val ha = 0.8f * seg(T, 0.1f, 0.3f) * tail(T, 0.55f)
          var i = 0
          while (i < 4) {
            val th = h01(seed, 800 + i) * TWO_PI; val rr = 0.22f * h01(seed, 810 + i)
            sb.fillOval(gx(cosf(th), rr), gy(sinf(th), rr), 3.2f * k, 1.6f * k, 0.12f, 0.1f, 0.03f, ha, 8)
            i += 1
          }
          return
        }
        debris(D_DROP, 14, 0f, 0.45f, 1f, 14f, 2.2f, 0.78f, 0.85f, 0.15f, 1)
        puffs(5, 0.12f, 1f, 0.3f, 30f, 6f, 13f, 0.92f, 0.95f, 0.8f, 0.55f, 2, lift = 4f, inkK = 0.5f)
        bubbles(6, 0.1f, 0.8f, 0.3f, 2.4f, 0.9f, 1f, 0.4f, 0.9f, 3)
      case S_MUD =>
        splat(0.45f, easeOut3(seg(T, 0.02f, 0.3f)), 0.4f, 0.28f, 0.15f, 0.95f * tail(T, 0.62f), 1, glossA = 0.55f, drops = 10)
        shockRing(0f, 0.3f, 5f, 0.55f, 0.42f, 0.28f, 0.8f)
        crown(9, 0.2f, 20f, 3.4f, 0f, 0.42f, 0.42f, 0.3f, 0.16f, 1f, 1)
        if (layer != AIR) return
        debris(D_GLOB, 11, 0f, 0.7f, 1f, 48f, 3.4f, 0.42f, 0.3f, 0.16f, 1, shadow = true)
        debris(D_FLECK, 10, 0f, 0.5f, 1f, 30f, 2f, 0.28f, 0.2f, 0.12f, 2)
        bubbles(4, 0.2f, 0.9f, 0.3f, 3f, 0.55f, 0.42f, 0.26f, 0.9f, 3)
      case _ => // S_INK
        splat(0.5f, easeOut3(seg(T, 0f, 0.15f)), 0.1f, 0.06f, 0.16f, 0.92f * tail(T, 0.65f), 1, glossA = 0.5f, drops = 12)
        tentacles(6)
        if (layer != AIR) return
        debris(D_DROP, 10, 0f, 0.6f, 0.9f, 30f, 2.6f, 0.14f, 0.08f, 0.22f, 1)
        bubbles(4, 0.2f, 0.9f, 0.4f, 3f, 0.35f, 0.25f, 0.5f, 0.9f, 2)
    }
  }

  /** A skull made of gas: cranium and jaw inked as one, sockets and a nose in the dark. */
  private def skull(x: Float, y: Float, sz: Float, a: Float, r: Float, g: Float, b: Float): Unit = {
    if (a <= 0.02f) return
    sb.fillOval(x, y, sz + 2f, sz * 0.86f + 2f, ink(r), ink(g), ink(b), 0.8f * a, 18)
    sb.fillOval(x, y + sz * 0.62f, sz * 0.62f + 2f, sz * 0.36f + 2f, ink(r), ink(g), ink(b), 0.8f * a, 14)
    sb.fillOval(x, y, sz, sz * 0.86f, r, g, b, a, 18)
    sb.fillOval(x, y + sz * 0.62f, sz * 0.62f, sz * 0.36f, r, g, b, a, 14)
    sb.fillOval(x - sz * 0.25f, y - sz * 0.32f, sz * 0.45f, sz * 0.3f, lit(r), lit(g), lit(b), 0.8f * a, 12)
    val dr = ink(r) * 0.6f; val dg = ink(g) * 0.6f; val db = ink(b) * 0.6f
    sb.fillOval(x - sz * 0.36f, y + sz * 0.14f, sz * 0.25f, sz * 0.27f, dr, dg, db, 0.95f * a, 10)
    sb.fillOval(x + sz * 0.36f, y + sz * 0.14f, sz * 0.25f, sz * 0.27f, dr, dg, db, 0.95f * a, 10)
    _xs(0) = x; _ys(0) = y + sz * 0.34f
    _xs(1) = x + sz * 0.1f; _ys(1) = y + sz * 0.52f
    _xs(2) = x - sz * 0.1f; _ys(2) = y + sz * 0.52f
    sb.fillPolygon(_xs, _ys, 3, dr, dg, db, 0.95f * a)
    var t = 0
    while (t < 3) {
      val tx = x + (t - 1) * sz * 0.2f
      sb.strokeLine(tx, y + sz * 0.58f, tx, y + sz * 0.82f, 1.2f, dr, dg, db, 0.8f * a)
      t += 1
    }
  }

  private[blasts] def gas(st: Int): Unit = {
    if (st == S_MIASMA) {
      scorch(0.75f, 0.22f, 0.35f, 0.12f, 0.35f)
      if (layer != AIR) return
      // The cloud rolls out along the ground, and a skull rises out of its middle
      puffs(12, 0.02f, 1f, 0.25f, 12f, 12f, 26f, 0.46f, 0.66f, 0.26f, 0.7f, 1, outward = 0.65f, flat = 0.65f)
      puffs(5, 0.05f, 1f, 0.12f, 50f, 14f, 30f, 0.52f, 0.42f, 0.6f, 0.65f, 2, lift = 8f)
      val sp = seg(T, 0.08f, 0.9f)
      if (sp > 0f && sp < 1f)
        skull(cx, cy - (20f + 50f * easeOut(sp)) * k, 18f * k * (0.8f + 0.3f * easeOut(sp)),
          0.85f * life(sp, 0.2f, 0.55f), 0.74f, 0.9f, 0.68f)
      motes(8, 0.1f, 0.9f, 0.6f, 40f, 1.4f, 0.55f, 1f, 0.4f, 0.9f, 3)
    } else {
      // S_VENOM_CLOUD: a low fog, and plumes rising out of it like the Hydra's heads
      scorch(0.7f, 0.3f, 0.4f, 0.08f, 0.3f)
      val s0 = seed * 43
      var i = 0
      while (i < 3) {
        val th = (i + 0.3f * hash(s0, i)) * TWO_PI / 3 + 0.5f
        _ordY(i) = gy(sinf(th), 0.3f); _ux(i) = gx(cosf(th), 0.3f); _us(i) = cosf(th)
        i += 1
      }
      sortFarToNear(3)
      var j = 0
      while (j < 3) {
        val i2 = _ord(j)
        val x = _ux(i2); val y = _ordY(i2)
        if (mine(y)) {
          val start = 0.05f + 0.07f * i2
          val grow = easeOut3(seg(T, start, start + 0.35f))
          val a = life(seg(T, start, 0.95f), 0.1f, 0.6f)
          if (grow > 0.02f && a > 0.02f) {
            val side = if (_us(i2) >= 0f) 1f else -1f
            tendril(x, y, -PI * 0.5f + side * 0.3f, 44f * k, grow, side * (1.6f + 0.4f * sinf(MS * 0.004f + i2)),
              11f * k, 0.62f, 0.8f, 0.22f, 0.85f * a)
            val hx = _px(8); val hy = _py(8)
            val hs = 7f * k * grow
            sb.fillOval(hx, hy, hs + 1.8f, hs * 0.78f + 1.8f, ink(0.62f), ink(0.8f), ink(0.22f), 0.8f * a, 14)
            sb.fillOval(hx, hy, hs, hs * 0.78f, 0.7f, 0.86f, 0.3f, 0.9f * a, 14)
            sb.fillOval(hx - hs * 0.4f, hy - hs * 0.1f, hs * 0.16f, hs * 0.2f, 0.1f, 0.14f, 0.03f, a, 6)
            sb.fillOval(hx + hs * 0.4f, hy - hs * 0.1f, hs * 0.16f, hs * 0.2f, 0.1f, 0.14f, 0.03f, a, 6)
          }
        }
        j += 1
      }
      if (layer != AIR) return
      puffs(10, 0.02f, 1f, 0.45f, 8f, 12f, 22f, 0.62f, 0.78f, 0.2f, 0.65f, 1, outward = 0.35f, flat = 0.55f)
      debris(D_DROP, 6, 0f, 0.6f, 0.7f, 24f, 2.2f, 0.7f, 0.9f, 0.2f, 2)
    }
  }
}
