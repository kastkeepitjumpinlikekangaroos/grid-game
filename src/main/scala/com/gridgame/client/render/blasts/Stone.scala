package com.gridgame.client.render.blasts

import GLBlastRenderers._
import BlastKit._
import Pieces._

/** Stone: Medusa's eye opening and a wave of petrification; and the generic blast. */
private[blasts] object Stone {
  /** Medusa's eye opening over the blast: an almond, green-gold, with a slit pupil. */
  private def gorgonEye(x: Float, y: Float, w: Float, h: Float, a: Float): Unit = {
    if (a <= 0.02f || h < 0.8f) return
    sb.fillOvalSoft(x, y, w * 1.7f, w * 1.2f, 0.62f, 0.95f, 0.35f, 0.4f * a, 0f, 16)
    var pass = 0
    while (pass < 2) {
      val grow = if (pass == 0) 2.2f else 0f
      val n = 6
      var i = 0
      while (i < n) {
        val u = i.toFloat / (n - 1)
        _xs(i) = x - w - grow + 2f * (w + grow) * u; _ys(i) = y - (h + grow) * sinf(u * PI)
        i += 1
      }
      i = 1
      while (i < n - 1) {
        val u = 1f - i.toFloat / (n - 1)
        _xs(n + i - 1) = x - w - grow + 2f * (w + grow) * u; _ys(n + i - 1) = y + (h + grow) * sinf(u * PI)
        i += 1
      }
      if (pass == 0) sb.fillPolygon(_xs, _ys, 2 * n - 2, 0.08f, 0.1f, 0.04f, 0.9f * a)
      else sb.fillPolygon(_xs, _ys, 2 * n - 2, 0.74f, 0.95f, 0.32f, a)
      pass += 1
    }
    val ir = Math.min(h * 0.92f, w * 0.5f)
    sb.fillOval(x, y, ir, ir, 0.95f, 0.8f, 0.2f, a, 14)
    _xs(0) = x; _ys(0) = y - ir * 0.92f
    _xs(1) = x + ir * 0.2f; _ys(1) = y
    _xs(2) = x; _ys(2) = y + ir * 0.92f
    _xs(3) = x - ir * 0.2f; _ys(3) = y
    sb.fillPolygon(_xs, _ys, 4, 0.05f, 0.04f, 0.03f, 0.95f * a)
    sb.fillOval(x - ir * 0.36f, y - ir * 0.36f, ir * 0.2f, ir * 0.16f, 1f, 1f, 1f, 0.85f * a, 8)
  }

  private[blasts] def petrify(): Unit = {
    // A wave of stone going out over the ground, and the ground it has turned grey and cracked
    val wp = seg(T, 0.12f, 0.5f)
    if (layer == GROUND) {
      val sa = Math.min(1f, wp * 1.4f) * tail(T, 0.62f)
      if (sa > 0.01f) sb.fillOvalSoft(cx, cy, W * 0.8f * easeOut3(wp), H * 0.8f * easeOut3(wp), 0.52f, 0.5f, 0.47f, 0.4f * sa, 0.1f * sa, 22)
    }
    shockRing(0.12f, 0.5f, 10f, 0.62f, 0.6f, 0.56f, 0.95f)
    cracks(10, 0.85f, 0.15f, 0.45f, 2.6f, 0.2f, 0.19f, 0.17f, 0.65f)
    if (layer != AIR) return
    val ep = seg(T, 0f, 0.62f)
    if (ep < 1f) {
      val open = easeOut(seg(ep, 0.03f, 0.25f)) * (1f - smooth(seg(ep, 0.72f, 1f)))
      gorgonEye(cx, cy - 34f * k, 24f * k, 13f * k * open, Math.min(1f, open * 2f))
    }
    debris(D_CHIP, 10, 0.12f, 0.9f, 0.9f, 30f, 3f, 0.6f, 0.58f, 0.54f, 1, shadow = true, from = 0.3f)
    glints(4, 0.05f, 0.6f, 0.5f, 30f, 9f, 0.7f, 1f, 0.4f, 0.9f)
  }

  /** A blast with no style of its own: a ring, a scorch and a flash in the thrower's colour. */
  private[blasts] def generic(): Unit = {
    scorch(0.55f, pcR * 0.3f, pcG * 0.3f, pcB * 0.3f, 0.4f)
    shockRing(0f, 0.4f, 6f, pcR, pcG, pcB, 0.9f)
    if (layer != AIR) return
    flash(0.1f, 0.5f, lit(pcR), lit(pcG), lit(pcB))
    debris(D_SPARK, 8, 0f, 0.4f, 1f, 16f, 2f, lit(pcR), lit(pcG), lit(pcB), 1)
  }
}
