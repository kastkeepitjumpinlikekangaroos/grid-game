package com.gridgame.client.render.blasts

import GLBlastRenderers._
import BlastKit._
import Pieces._

/** Nanites: a swarm scattering, or flung out over a hex grid. */
private[blasts] object Nanites {
  /** A swarm of nanites flung out to `reach` over [0, t1 * 0.3], circling there and scattering. */
  private def nanites(n0: Int, reach: Float, t1: Float, size: Float): Unit = {
    if (layer != AIR || T >= t1) return
    val n = Math.max(4, (n0 * det + 0.5f).toInt)
    val out = easeOut3(seg(T, 0f, t1 * 0.3f))
    val fade = 1f - smooth(seg(T, t1 * 0.62f, t1))
    var i = 0
    while (i < n) {
      val dir = if ((i & 1) == 0) 1f else -1f
      val th = (i + 0.5f * hash(seed, 2700 + i)) * TWO_PI / n + dir * T * 3.2f
      val rr = reach * (0.4f + 0.6f * h01(seed, 2710 + i)) * out
      val x = gx(cosf(th), rr); val y = gy(sinf(th), rr) - (6f + 5f * sinf(MS * 0.01f + i)) * k * out
      sb.fillOval(x, y, size * k * 1.9f, size * k * 1.9f, 0.3f, 1f, 0.8f, 0.22f * fade, 8)
      piece(D_SQUARE, x, y, size * k, th, 0.25f, 0.95f, 0.72f, fade)
      i += 1
    }
  }

  private[blasts] def nano(st: Int): Unit = {
    if (st == S_NANO_SPLASH) {
      if (layer == GROUND) {
        val p = seg(T, 0f, 0.8f)
        if (p < 1f) {
          val q = 0.25f + 0.5f * easeOut3(seg(p, 0f, 0.25f))
          sb.fillOvalSoft(cx, cy, W * q, H * q, 0.3f, 1f, 0.78f, 0.3f * life(p, 0.1f, 0.4f), 0f, 18)
          hexRing(q, T * 0.8f, 2.8f * k, 0.3f, 1f, 0.78f, life(p, 0.1f, 0.4f))
        }
      }
      flash(0.07f, 0.35f, 0.4f, 1f, 0.8f, 0.7f)
      nanites(22, 0.9f, 1f, 2.3f)
    } else {
      // S_NANO_BURST: the swarm flung out over a flash of hex grid
      if (layer == GROUND) {
        val ga = life(T, 0.06f, 0.42f) * 0.9f
        val hr = 0.23f
        hexRing(hr, PI / 6, 2f * k, 0.3f, 1f, 0.78f, ga)
        var i = 0
        while (i < 6) {
          val th = i * TWO_PI / 6
          hexRing(hr, PI / 6, 2f * k, 0.3f, 1f, 0.78f, ga * (0.8f - 0.1f * (i & 1)),
            cosf(th) * W * 0.44f, sinf(th) * H * 0.44f)
          i += 1
        }
      }
      shockRing(0f, 0.35f, 5f, 0.3f, 1f, 0.78f, 0.9f)
      flash(0.08f, 0.45f, 0.4f, 1f, 0.8f, 0.8f)
      nanites(52, 0.92f, 1f, 2.4f)
      motes(8, 0.05f, 0.8f, 0.8f, 30f, 1.3f, 0.35f, 1f, 0.8f, 1f, 1)
    }
  }
}
