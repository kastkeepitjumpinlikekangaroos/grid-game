package com.gridgame.client.render.blasts

import GLBlastRenderers._
import BlastKit._
import Pieces._

/** Electricity: arcs bursting out, crackling rims, sparks, and the burnt scar they leave. */
private[blasts] object Electric {
  /** `n` bolts out of the middle to `reach` of the footprint, struck afresh every `every` ms
    * until `t1`: its shape jumps, as lightning's does, but its strength only ever falls. */
  private def arcBurst(n: Int, reach: Float, t1: Float, every: Float, w: Float,
                       r: Float, g: Float, b: Float, sd: Int, fork: Boolean = false): Unit = {
    if (layer != AIR || T >= t1) return
    val epoch = (MS / every).toInt
    val a = 1f - smooth(T / t1)
    val s0 = seed * 5 + sd * 17 + epoch * 131
    var i = 0
    while (i < n) {
      val th = (i + 0.6f * hash(s0, i)) * TWO_PI / n
      val c = cosf(th); val s = sinf(th)
      if (fork) {
        // To half way, then splitting in two
        val mx = gx(c, reach * 0.55f); val my = gy(s, reach * 0.55f)
        bolt(cx, cy - H * 0.15f, mx, my, 5, 5f * k, s0 + i, w * k, r, g, b, a)
        var f = 0
        while (f < 2) {
          val fa = th + (if (f == 0) -0.34f else 0.34f) + 0.12f * hash(s0, 40 + i * 2 + f)
          bolt(mx, my, gx(cosf(fa), reach), gy(sinf(fa), reach), 5, 4f * k, s0 + 50 + i * 2 + f, w * 0.7f * k, r, g, b, a)
          f += 1
        }
      } else {
        val rr = reach * (0.72f + 0.28f * h01(s0, 20 + i))
        bolt(cx, cy - H * 0.15f, gx(c, rr), gy(s, rr), 7, 6f * k, s0 + i, w * k, r, g, b, a)
      }
      i += 1
    }
  }

  /** Electricity crawling round the edge of the footprint over [t0, t1]. */
  private def rimArcs(n: Int, t0: Float, t1: Float, every: Float, r: Float, g: Float, b: Float): Unit = {
    if (layer != AIR || T <= t0 || T >= t1) return
    val epoch = (MS / every).toInt
    val a = life(seg(T, t0, t1), 0.15f, 0.5f)
    var i = 0
    while (i < n) {
      val th = (i + 0.5f * hash(seed + epoch * 7, 900 + i)) * TWO_PI / n
      bolt(gx(cosf(th), 0.92f), gy(sinf(th), 0.92f), gx(cosf(th + 0.38f), 0.96f), gy(sinf(th + 0.38f), 0.96f),
        4, 3f * k, seed * 3 + epoch * 5 + i, 1.9f * k, r, g, b, a)
      i += 1
    }
  }

  /** A ball of light crackling where it struck, over [0, t1]. */
  private def sparkBall(t1: Float, size: Float, r: Float, g: Float, b: Float): Unit = {
    if (layer != AIR || T >= t1) return
    val a = 1f - smooth(T / t1)
    val rr = size * k * (1f + 0.15f * sinf(MS * 0.05f))
    sb.fillOvalSoft(cx, cy - H * 0.15f, rr * 2.4f, rr * 2f, r, g, b, 0.4f * a, 0f, 16)
    sb.fillOval(cx, cy - H * 0.15f, rr, rr * 0.9f, mix(r, 1f, 0.7f), mix(g, 1f, 0.7f), mix(b, 1f, 0.7f), a, 14)
    sb.fillStarFlare(cx, cy - H * 0.15f, rr * 3.4f, 2.6f, (MS * 0.004f) % TWO_PI, 0.5f, 1f, 1f, mix(b, 1f, 0.6f), 0.9f * a)
  }

  private[blasts] def electric(st: Int): Unit = {
    (st: @scala.annotation.switch) match {
      case S_STORM =>
        scar(7, 0.8f, 1f, 0.9f, 0.35f, 0.9f)
        scorch(0.35f, 0.1f, 0.1f, 0.08f, 0.35f)
        if (layer != AIR) return
        flash(0.08f, 0.55f, 1f, 0.95f, 0.6f)
        sparkBall(0.35f, 6f, 1f, 0.9f, 0.35f)
        arcBurst(6, 0.95f, 0.5f, 70f, 4f, 1f, 0.9f, 0.35f, 1)
        rimArcs(7, 0.05f, 0.55f, 80f, 1f, 0.92f, 0.45f)
        debris(D_SPARK, 10, 0f, 0.35f, 1f, 18f, 2f, 1f, 0.9f, 0.4f, 1)
      case S_THUNDER =>
        scar(9, 0.9f, 1f, 0.95f, 0.6f, 1f)
        scorch(0.45f, 0.08f, 0.08f, 0.07f, 0.45f)
        shockRing(0f, 0.3f, 7f, 1f, 0.92f, 0.5f, 0.9f)
        if (layer != AIR) return
        flash(0.1f, 0.9f, 1f, 1f, 0.85f)
        // The bolt out of the sky, and a second strike down the same channel
        var s = 0
        while (s < 2) {
          val t0 = if (s == 0) 0f else 0.13f
          val sp = seg(T, t0, t0 + 0.13f)
          if (sp > 0f && sp < 1f) {
            val a = 1f - sp * sp
            val topX = cx + hash(seed, 950 + s) * 12f * k
            bolt(topX, cy - 230f * k, cx, cy - 2f, 10, 14f * k, seed * 7 + s, 15f * k, 1f, 0.95f, 0.55f, a)
          }
          s += 1
        }
        sparkBall(0.3f, 8f, 1f, 0.95f, 0.6f)
        if (T > 0.05f && T < 0.45f) {
          // Arcs crawling out over the ground from where it struck
          val epoch = (MS / 75f).toInt
          val a = life(seg(T, 0.05f, 0.45f), 0.1f, 0.4f)
          var i = 0
          while (i < 4) {
            val th = (i + 0.5f * hash(seed + epoch, 960 + i)) * TWO_PI / 4
            bolt(cx, cy, gx(cosf(th), 0.85f), gy(sinf(th), 0.85f), 7, 4f * k, seed + epoch * 9 + i, 2.2f * k, 1f, 0.95f, 0.55f, a)
            i += 1
          }
        }
        debris(D_SPARK, 12, 0f, 0.4f, 1f, 24f, 2.2f, 1f, 0.95f, 0.6f, 1)
        puffs(3, 0.3f, 1f, 0.1f, 30f, 8f, 16f, 0.45f, 0.45f, 0.48f, 0.5f, 2, lift = 4f)
      case S_TESLA =>
        scar(6, 0.75f, 0.45f, 0.85f, 1f, 0.9f)
        shockRing(0f, 0.25f, 3f, 0.55f, 0.9f, 1f, 1f)
        shockRing(0.06f, 0.32f, 2.6f, 0.7f, 0.95f, 1f, 0.9f)
        shockRing(0.12f, 0.4f, 2.2f, 0.85f, 1f, 1f, 0.8f)
        if (layer != AIR) return
        flash(0.08f, 0.55f, 0.5f, 0.85f, 1f)
        sparkBall(0.38f, 6f, 0.45f, 0.85f, 1f)
        arcBurst(5, 0.95f, 0.5f, 70f, 3.8f, 0.45f, 0.85f, 1f, 1)
        debris(D_SPARK, 10, 0f, 0.35f, 1f, 18f, 2f, 0.5f, 0.9f, 1f, 1)
      case S_TESLA_FORK =>
        scar(5, 0.7f, 0.55f, 0.9f, 1f, 0.85f)
        shockRing(0f, 0.3f, 3.4f, 0.6f, 0.92f, 1f, 0.95f)
        if (layer != AIR) return
        flash(0.07f, 0.45f, 0.55f, 0.9f, 1f)
        sparkBall(0.3f, 5f, 0.55f, 0.9f, 1f)
        arcBurst(3, 0.95f, 0.45f, 80f, 4.6f, 0.55f, 0.9f, 1f, 1, fork = true)
        debris(D_SPARK, 8, 0f, 0.35f, 1f, 16f, 2f, 0.6f, 0.92f, 1f, 1)
      case _ => // S_OVERCLOCK
        val ga = life(T, 0.08f, 0.5f) * 0.9f
        if (layer == GROUND) {
          val rot = T * 0.6f
          hexRing(0.92f, rot, 3f * k, 0.3f, 1f, 0.78f, ga)
          hexRing(0.56f, rot + PI / 6, 2.2f * k, 0.55f, 1f, 0.9f, ga * 0.9f)
          var i = 0
          while (i < 6) {
            val th = rot + i * TWO_PI / 6
            sb.strokeLine(gx(cosf(th), 0.56f), gy(sinf(th), 0.56f), gx(cosf(th), 0.92f), gy(sinf(th), 0.92f),
              1.6f * k, 0.3f, 1f, 0.78f, 0.7f * ga)
            i += 1
          }
        }
        shockRing(0f, 0.35f, 5f, 0.3f, 1f, 0.78f, 0.9f)
        streaks(14, 0.02f, 0.42f, 0.2f, 1.1f, 0.3f, 3f, 0.55f, 1f, 0.9f, 0.9f, 1)
        if (layer != AIR) return
        flash(0.08f, 0.5f, 0.4f, 1f, 0.8f)
        debris(D_SQUARE, 12, 0.05f, 0.9f, 0.6f, 50f, 2.2f, 0.35f, 1f, 0.8f, 1)
        motes(8, 0.05f, 0.8f, 0.7f, 50f, 1.3f, 0.35f, 1f, 0.85f, 1f, 2)
    }
  }
}
