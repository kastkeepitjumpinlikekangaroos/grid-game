package com.gridgame.client.render.blasts

import GLBlastRenderers._
import BlastKit._
import Pieces._

/** Ice: a quake of frost, an avalanche's crush, a frost nova, a hailstorm. */
private[blasts] object Ice {
  /** Frost spreading over the ground in a six-armed flake, each arm branching twice. */
  private def snowflake(size: Float, grow: Float, w: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    if (layer != GROUND || a <= 0.01f || grow <= 0.01f) return
    val rot = hash(seed, 1000) * 0.5f
    var i = 0
    while (i < 6) {
      val th = rot + i * PI / 3
      val c = cosf(th); val s = sinf(th)
      val len = size * grow
      _xs(0) = cx; _ys(0) = cy
      _xs(1) = gx(c, len); _ys(1) = gy(s, len)
      sb.strokePolylineTapered(_xs, _ys, 2, w * k + 2.6f, 1.6f, ink(r), ink(g), ink(b), 0.45f * a, 0.2f * a)
      sb.strokePolylineTapered(_xs, _ys, 2, w * k, 0.8f, r, g, b, a, 0.7f * a)
      var br = 0
      while (br < 2) {
        val at = len * (if (br == 0) 0.4f else 0.68f)
        val bl = len * (if (br == 0) 0.26f else 0.18f)
        val bx = gx(c, at); val by = gy(s, at)
        var side = -1
        while (side <= 1) {
          val ba = th + side * 0.62f
          sb.strokeLine(bx, by, bx + cosf(ba) * bl * W, by + sinf(ba) * bl * H, w * 0.55f * k, r, g, b, 0.85f * a)
          side += 2
        }
        br += 1
      }
      i += 1
    }
  }

  /** Hail out of the cloud over the blast: `n` stones each falling from `drop` units up onto
    * a point of the footprint over its own short span of [t0, t1], bouncing once as it lands, and
    * a white tick where it struck. */
  private def hailFall(n0: Int, t0: Float, t1: Float, drop: Float, size: Float, sd: Int): Unit = {
    if (layer != AIR) return
    val n = Math.max(1, (n0 * det + 0.5f).toInt)
    val s0 = seed * 41 + sd * 13
    var i = 0
    while (i < n) {
      val start = t0 + (t1 - t0) * 0.7f * h01(s0, i * 3)
      val p = seg(T, start, start + (t1 - t0) * 0.3f)
      if (p > 0f && p < 1f) {
        val th = (i + 0.6f * hash(s0, i * 3 + 1)) * TWO_PI / n
        val rho = 0.15f + 0.8f * h01(s0, i * 3 + 2)
        val bx = gx(cosf(th), rho); val by = gy(sinf(th), rho)
        val fall = seg(p, 0f, 0.55f)
        val z = if (fall < 1f) drop * k * (1f - fall * fall)
                else { val q = seg(p, 0.55f, 1f); 7f * k * 4f * q * (1f - q) }
        val a = if (p < 0.8f) 1f else (1f - p) / 0.2f
        val sz = size * k * (0.7f + 0.5f * h01(s0, i))
        if (z > 2f) sb.fillOval(bx, by, sz * 0.9f, sz * 0.35f, 0f, 0f, 0f, 0.2f * a, 8)
        sb.fillOval(bx, by - z, sz + 1.3f, sz + 1.3f, 0.2f, 0.28f, 0.4f, 0.8f * a, 9)
        sb.fillOval(bx, by - z, sz, sz, 0.86f, 0.94f, 1f, a, 9)
        sb.fillOval(bx - sz * 0.3f, by - z - sz * 0.3f, sz * 0.35f, sz * 0.35f, 1f, 1f, 1f, a, 6)
        if (fall >= 1f && p < 0.75f) {
          val ta = 1f - seg(p, 0.55f, 0.75f)
          sb.strokeOval(bx, by, sz * 2f, sz * 0.8f, 1.2f, 1f, 1f, 1f, 0.8f * ta, 10)
        }
      }
      i += 1
    }
  }

  private[blasts] def ice(st: Int): Unit = {
    (st: @scala.annotation.switch) match {
      case S_ICE_QUAKE =>
        scorch(0.8f, 0.7f, 0.88f, 1f, 0.3f)
        snowflake(0.92f, easeOut3(seg(T, 0f, 0.25f)), 3.2f, 0.78f, 0.93f, 1f, 0.9f * tail(T, 0.62f))
        shockRing(0f, 0.35f, 6f, 0.8f, 0.95f, 1f, 0.9f)
        spikeRing(5, 0.4f, 0.05f, 24f, 0.03f, 0.72f, 0.6f, 0.85f, 1f, glass = true, 1)
        spikeRing(11, 0.78f, 0.05f, 30f, 0.08f, 0.74f, 0.55f, 0.82f, 1f, glass = true, 2)
        if (layer != AIR) return
        // The spikes shatter
        debris(D_SHARD, 12, 0.72f, 1f, 1.05f, 22f, 2.8f, 0.7f, 0.9f, 1f, 1, from = 0.75f)
        puffs(8, 0.05f, 1f, 0.7f, 14f, 10f, 20f, 0.88f, 0.95f, 1f, 0.5f, 2, outward = 0.2f, flat = 0.6f, inkK = 0.4f)
        glints(6, 0.05f, 0.8f, 0.9f, 30f, 10f, 0.85f, 0.97f, 1f, 1f)
      case S_AVALANCHE =>
        scorch(0.6f, 0.92f, 0.96f, 1f, 0.45f, 0.55f)
        shockRing(0f, 0.32f, 7f, 0.85f, 0.95f, 1f, 0.9f)
        if (layer != AIR) return
        puffs(9, 0.02f, 1f, 0.3f, 26f, 12f, 26f, 0.93f, 0.96f, 1f, 0.9f, 1, outward = 0.5f, flat = 0.72f)
        debris(D_SHARD, 9, 0f, 0.75f, 0.95f, 40f, 3.4f, 0.62f, 0.85f, 1f, 2, shadow = true)
        motes(10, 0.05f, 0.9f, 0.9f, 16f, 1.2f, 0.95f, 0.98f, 1f, 0.9f, 3)
        glints(4, 0.05f, 0.7f, 0.8f, 24f, 9f, 0.9f, 0.97f, 1f, 0.9f)
      case S_HAIL =>
        // The Cloudrunner's storm cloud breaking over it: hail hammering down onto a ring of frost
        scorch(0.6f, 0.75f, 0.88f, 1f, 0.32f)
        shockRing(0f, 0.3f, 5f, 0.82f, 0.94f, 1f, 0.9f)
        snowflake(0.55f, easeOut3(seg(T, 0.05f, 0.3f)), 2f, 0.8f, 0.94f, 1f, 0.7f * tail(T, 0.6f))
        if (layer != AIR) return
        puffs(8, 0f, 0.85f, 0.55f, 6f, 18f, 30f, 0.42f, 0.46f, 0.57f, 0.9f, 1, outward = 0.12f, flat = 0.5f, lift = 74f)
        hailFall(20, 0.04f, 0.8f, 70f, 4.2f, 2)
        glints(5, 0.1f, 0.75f, 0.8f, 20f, 9f, 0.85f, 0.97f, 1f, 1f)
      case _ => // S_FROST: the Cryomancer's Frost Nova, a ring of frost racing out (and the frost traps nobody lays now)
        scorch(0.6f, 0.7f, 0.88f, 1f, 0.3f)
        snowflake(0.8f, easeOut3(seg(T, 0f, 0.22f)), 2.6f, 0.78f, 0.93f, 1f, 0.9f * tail(T, 0.6f))
        shockRing(0f, 0.34f, 6f, 0.72f, 0.9f, 1f, 0.9f)
        shockRing(0.08f, 0.42f, 3f, 0.9f, 0.97f, 1f, 0.75f)
        spikeRing(6, 0.55f, 0.06f, 18f, 0.04f, 0.7f, 0.6f, 0.85f, 1f, glass = true, 1)
        if (layer != AIR) return
        puffs(5, 0.05f, 1f, 0.5f, 12f, 8f, 16f, 0.88f, 0.95f, 1f, 0.5f, 2, outward = 0.2f, flat = 0.6f, inkK = 0.4f)
        glints(4, 0.05f, 0.7f, 0.8f, 24f, 9f, 0.85f, 0.97f, 1f, 1f)
    }
  }
}
