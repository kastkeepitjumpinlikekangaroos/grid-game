package com.gridgame.client.render.blasts

import GLBlastRenderers._
import BlastKit._
import Pieces._

/** Earth: cracks, rubble and spikes thrust up — quakes, tremors, the golem's and the mech's pound,
  * Anubis's scales. */
private[blasts] object Earth {

  /** An ankh of gold at (x, y), `s` its scale: a loop, a shaft and a crossbar, inked. */
  private def ankh(x: Float, y: Float, s: Float, a: Float): Unit = {
    if (a <= 0.02f) return
    var pass = 0
    while (pass < 3) {
      val grow = if (pass == 0) 2.2f else 0f
      val wk = if (pass == 2) 0.35f else 1f
      val cr = if (pass == 0) 0.3f else if (pass == 1) 1f else 1f
      val cg = if (pass == 0) 0.2f else if (pass == 1) 0.8f else 0.97f
      val cb = if (pass == 0) 0.05f else if (pass == 1) 0.28f else 0.75f
      val al = if (pass == 0) 0.9f * a else a
      sb.strokeOval(x, y - 7.5f * s, 3.8f * s, 4.8f * s, 2.8f * s * wk + grow, cr, cg, cb, al, 16)
      sb.strokeLine(x, y - 2.8f * s, x, y + 9f * s, 3f * s * wk + grow, cr, cg, cb, al)
      sb.strokeLine(x - 5.6f * s, y - 1.6f * s, x + 5.6f * s, y - 1.6f * s, 2.6f * s * wk + grow, cr, cg, cb, al)
      pass += 1
    }
  }

  /** Spouts of sand shooting up round a ring and falling back. */
  private def sandSpouts(n: Int, rho: Float, h0: Float, t0: Float): Unit = {
    val s0 = seed * 59
    var i = 0
    while (i < n) {
      val th = (i + 0.4f * hash(s0, i)) * TWO_PI / n
      _ux(i) = th; _ordY(i) = gy(sinf(th), rho)
      i += 1
    }
    sortFarToNear(n)
    var j = 0
    while (j < n) {
      val i2 = _ord(j)
      val y = _ordY(i2)
      if (mine(y)) {
        val th = _ux(i2)
        val start = t0 + 0.08f * h01(s0, 30 + i2)
        val p = seg(T, start, start + 0.55f)
        if (p > 0f && p < 1f) {
          val hh = h0 * k * (0.75f + 0.4f * h01(s0, 40 + i2)) * (if (p < 0.3f) easeOut3(p / 0.3f) else 1f - smooth((p - 0.3f) / 0.7f))
          val x = gx(cosf(th), rho)
          val a = tail(p, 0.7f)
          val tx = x + cosf(th) * hh * 0.15f; val ty = y - hh
          val bw = 2.6f * k; val tw = 6.6f * k * (0.6f + 0.4f * Math.min(1f, hh / (h0 * k)))
          var pass = 0
          while (pass < 2) {
            val gr = if (pass == 0) 1.5f else 0f
            _xs(0) = x - bw - gr; _ys(0) = y + gr
            _xs(1) = tx - tw - gr; _ys(1) = ty
            _xs(2) = tx + tw + gr; _ys(2) = ty
            _xs(3) = x + bw + gr; _ys(3) = y + gr
            if (pass == 0) sb.fillPolygon(_xs, _ys, 4, 0.3f, 0.22f, 0.1f, 0.8f * a)
            else sb.fillPolygon(_xs, _ys, 4, 0.86f, 0.72f, 0.45f, a)
            pass += 1
          }
          // The plume billowing off its top
          var f = -1
          while (f <= 1) {
            val fx = tx + f * tw * 0.7f; val fy = ty - (if (f == 0) 2.6f * k else 0f)
            sb.fillOval(fx, fy, tw * 0.62f + 1.5f, tw * 0.5f + 1.5f, 0.3f, 0.22f, 0.1f, 0.7f * a, 12)
            f += 1
          }
          f = -1
          while (f <= 1) {
            val fx = tx + f * tw * 0.7f; val fy = ty - (if (f == 0) 2.6f * k else 0f)
            sb.fillOval(fx, fy, tw * 0.62f, tw * 0.5f, 0.95f, 0.85f, 0.6f, a, 12)
            f += 1
          }
        }
      }
      j += 1
    }
  }

  private[blasts] def earth(st: Int): Unit = {
    (st: @scala.annotation.switch) match {
      case S_SEISMIC =>
        cracks(10, 0.95f, 0.02f, 0.15f, 4.2f, 0.12f, 0.1f, 0.08f, 0.75f)
        scorch(0.55f, 0.3f, 0.24f, 0.16f, 0.3f)
        shockRing(0f, 0.35f, 8f, 0.8f, 0.68f, 0.5f, 0.9f)
        spikeRing(7, 0.45f, 0.06f, 18f, 0.02f, 0.72f, 0.56f, 0.49f, 0.4f, glass = false, 1)
        spikeRing(12, 0.8f, 0.06f, 26f, 0.07f, 0.74f, 0.52f, 0.45f, 0.37f, glass = false, 2)
        if (layer != AIR) return
        puffs(12, 0.08f, 0.9f, 0.82f, 10f, 14f, 26f, 0.72f, 0.62f, 0.48f, 0.5f, 1, outward = 0.18f, flat = 0.56f, inkK = 0.4f)
        debris(D_CHIP, 12, 0.02f, 0.8f, 1f, 40f, 3f, 0.5f, 0.42f, 0.34f, 1, shadow = true)
      case S_TREMOR =>
        cracks(8, 0.85f, 0.02f, 0.14f, 3.8f, 0.12f, 0.09f, 0.06f, 0.75f)
        scorch(0.5f, 0.3f, 0.22f, 0.12f, 0.3f)
        shockRing(0f, 0.35f, 8f, 0.75f, 0.62f, 0.4f, 0.9f)
        shockRing(0.08f, 0.45f, 4f, 0.5f, 0.72f, 0.36f, 0.8f)
        if (layer != AIR) return
        debris(D_CHIP, 14, 0.01f, 0.8f, 0.95f, 44f, 3.4f, 0.45f, 0.36f, 0.24f, 1, shadow = true)
        // Flecks of the beetle's bronze-green shell
        debris(D_SHARD, 6, 0f, 0.6f, 0.8f, 30f, 2.6f, 0.4f, 0.66f, 0.42f, 2)
        puffs(10, 0.05f, 0.9f, 0.78f, 12f, 14f, 26f, 0.68f, 0.56f, 0.4f, 0.5f, 3, outward = 0.2f, flat = 0.56f, inkK = 0.4f)
      case S_GOLEM =>
        runeCircle(0.62f, 8, T * 0.4f, 2.6f, 1f, 0.7f, 0.3f, 0.9f * life(T, 0.06f, 0.5f))
        cracks(8, 0.9f, 0.02f, 0.14f, 4.4f, 0.14f, 0.13f, 0.13f, 0.8f, 1f, 0.7f, 0.3f, 0.8f)
        shockRing(0f, 0.35f, 9f, 0.62f, 0.62f, 0.6f, 0.9f)
        if (layer != AIR) return
        debris(D_BLOCK, 10, 0.01f, 0.8f, 0.95f, 50f, 4.6f, 0.52f, 0.52f, 0.54f, 1, shadow = true)
        puffs(10, 0.05f, 0.9f, 0.78f, 12f, 14f, 26f, 0.62f, 0.6f, 0.58f, 0.5f, 2, outward = 0.2f, flat = 0.56f, inkK = 0.4f)
        motes(6, 0.05f, 0.7f, 0.6f, 30f, 1.4f, 1f, 0.72f, 0.3f, 1f, 3)
      case S_POUND =>
        scorch(0.42f, 0.2f, 0.14f, 0.08f, 0.6f)
        if (layer == GROUND) {
          // The crater's raised rim
          val ra = Math.min(1f, T * 10f) * tail(T, 0.6f)
          sb.strokeOval(cx, cy, W * 0.46f, H * 0.46f, 7f * k + 2.4f, 0.14f, 0.1f, 0.06f, 0.6f * ra, 22)
          sb.strokeOval(cx, cy, W * 0.46f, H * 0.46f, 7f * k, 0.55f, 0.42f, 0.26f, 0.85f * ra, 22)
          sb.strokeArc(cx, cy, W * 0.46f, H * 0.46f, PI * 1.05f, PI * 0.9f, 2f * k, 0.72f, 0.6f, 0.42f, 0.8f * ra, 14)
        }
        cracks(7, 0.75f, 0.02f, 0.12f, 3.6f, 0.13f, 0.09f, 0.05f, 0.75f)
        shockRing(0f, 0.32f, 9f, 0.78f, 0.66f, 0.48f, 0.9f)
        if (layer != AIR) return
        debris(D_CHIP, 10, 0.01f, 0.8f, 0.9f, 46f, 3.6f, 0.4f, 0.28f, 0.16f, 1, shadow = true)
        debris(D_LEAF, 6, 0.01f, 0.75f, 0.8f, 38f, 2.8f, 0.35f, 0.6f, 0.22f, 2)
        puffs(6, 0.05f, 0.9f, 0.5f, 18f, 13f, 24f, 0.7f, 0.58f, 0.42f, 0.5f, 3, outward = 0.3f, flat = 0.6f, inkK = 0.45f)
      case S_MECH =>
        scorch(0.45f, 0.12f, 0.12f, 0.14f, 0.45f)
        cracks(6, 0.7f, 0.02f, 0.12f, 3.4f, 0.1f, 0.1f, 0.12f, 0.7f)
        if (layer == GROUND) {
          // A hexagonal shock off the mech's foot
          val p = seg(T, 0f, 0.35f)
          if (p < 1f) hexRing(easeOut3(p), 0.2f, 6f * k * (1f - 0.5f * p), 0.55f, 0.66f, 0.82f, 0.95f * (1f - p))
        }
        if (layer != AIR) return
        flash(0.06f, 0.45f, 1f, 0.8f, 0.5f)
        debris(D_SPARK, 16, 0f, 0.35f, 1f, 12f, 2.2f, 1f, 0.65f, 0.25f, 1)
        debris(D_HEX, 7, 0.01f, 0.8f, 0.85f, 34f, 2.6f, 0.56f, 0.58f, 0.62f, 2, shadow = true)
        puffs(5, 0.1f, 0.95f, 0.35f, 34f, 8f, 18f, 0.9f, 0.92f, 0.95f, 0.6f, 3, lift = 4f, inkK = 0.5f)
      case _ => // S_SCALES: Anubis's judgment
        val ga = life(T, 0.1f, 0.65f)
        runeCircle(0.86f, 16, T * 0.3f, 3.4f, 1f, 0.8f, 0.3f, 0.95f * ga)
        scorch(0.6f, 0.75f, 0.62f, 0.35f, 0.25f)
        sandSpouts(10, 0.8f, 44f, 0.05f)
        if (layer != AIR) return
        val ap = seg(T, 0.03f, 0.88f)
        if (ap > 0f && ap < 1f) {
          val ay = cy - (16f + 36f * easeOut(ap)) * k
          val aa = life(ap, 0.12f, 0.6f)
          sb.fillOvalSoft(cx, ay - 6f * k, 34f * k, 34f * k, 1f, 0.85f, 0.4f, 0.45f * aa, 0f, 16)
          ankh(cx, ay, 2.2f * k, aa)
        }
        debris(D_FLECK, 14, 0.05f, 0.8f, 0.95f, 40f, 2.4f, 0.86f, 0.72f, 0.45f, 1)
        motes(10, 0.05f, 0.9f, 0.8f, 50f, 1.5f, 1f, 0.85f, 0.4f, 1f, 2)
    }
  }
}
