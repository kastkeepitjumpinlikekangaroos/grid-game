package com.gridgame.client.render.blasts

import GLBlastRenderers._
import BlastKit._
import Pieces._

/** Water: a splash's crown and a geyser's column. */
private[blasts] object Water {

  /** The Tidecaller's geyser: a column of water standing up out of the middle and falling back. */
  private def geyserColumn(): Unit = {
    if (layer != AIR) return
    val up = easeOut3(seg(T, 0f, 0.2f))
    val down = smooth(seg(T, 0.5f, 0.82f))
    val hgt = W * 1.05f * up * (1f - down)
    if (hgt < 3f) return
    val a = 1f - seg(T, 0.7f, 0.85f)
    val bw = W * 0.12f * (1f + 0.35f * down)
    val wob = sinf(MS * 0.02f) * 1.5f * k
    // Ink, body, a lighter stripe and a white core: a column is a trapezoid, so convex
    _xs(0) = cx - bw - 1.8f; _ys(0) = cy + 1f
    _xs(1) = cx - bw * 0.78f + wob - 1.8f; _ys(1) = cy - hgt - 1f
    _xs(2) = cx + bw * 0.78f + wob + 1.8f; _ys(2) = cy - hgt - 1f
    _xs(3) = cx + bw + 1.8f; _ys(3) = cy + 1f
    sb.fillPolygon(_xs, _ys, 4, 0.06f, 0.14f, 0.26f, 0.85f * a)
    _xs(0) = cx - bw; _ys(0) = cy
    _xs(1) = cx - bw * 0.78f + wob; _ys(1) = cy - hgt
    _xs(2) = cx + bw * 0.78f + wob; _ys(2) = cy - hgt
    _xs(3) = cx + bw; _ys(3) = cy
    sb.fillPolygon(_xs, _ys, 4, 0.3f, 0.6f, 0.95f, a)
    _xs(0) = cx - bw * 0.45f; _ys(0) = cy
    _xs(1) = cx - bw * 0.3f + wob; _ys(1) = cy - hgt
    _xs(2) = cx + bw * 0.2f + wob; _ys(2) = cy - hgt
    _xs(3) = cx + bw * 0.25f; _ys(3) = cy
    sb.fillPolygon(_xs, _ys, 4, 0.62f, 0.84f, 1f, 0.9f * a)
    sb.strokeLine(cx - bw * 0.1f, cy - 2f, cx - bw * 0.05f + wob, cy - hgt + 3f, 1.6f * k, 1f, 1f, 1f, 0.8f * a)
    // Foam boiling off the top, and round its foot
    var f = 0
    while (f < 3) {
      val fx = cx + wob + (f - 1) * bw * 0.7f
      val fy = cy - hgt - (if (f == 1) 3f * k else 0f)
      val fs = bw * (0.62f + (if (f == 1) 0.2f else 0f)) * (1f + 0.08f * sinf(MS * 0.03f + f))
      sb.fillOval(fx, fy, fs + 1.8f, fs * 0.8f + 1.8f, 0.1f, 0.2f, 0.32f, 0.8f * a, 14)
      f += 1
    }
    f = 0
    while (f < 3) {
      val fx = cx + wob + (f - 1) * bw * 0.7f
      val fy = cy - hgt - (if (f == 1) 3f * k else 0f)
      val fs = bw * (0.62f + (if (f == 1) 0.2f else 0f)) * (1f + 0.08f * sinf(MS * 0.03f + f))
      sb.fillOval(fx, fy, fs, fs * 0.8f, 0.9f, 0.96f, 1f, a, 14)
      f += 1
    }
    sb.fillOval(cx, cy, bw * 1.7f + 1.8f, bw * 0.55f + 1.8f, 0.1f, 0.2f, 0.32f, 0.7f * a, 16)
    sb.fillOval(cx, cy, bw * 1.7f, bw * 0.55f, 0.92f, 0.97f, 1f, 0.95f * a, 16)
  }

  private[blasts] def water(st: Int): Unit = {
    if (st == S_SPLASH) {
      scorch(0.5f, 0.12f, 0.25f, 0.4f, 0.35f, 0.5f)
      shockRing(0.04f, 0.6f, 2.6f, 0.75f, 0.9f, 1f, 0.75f)
      shockRing(0.18f, 0.8f, 2f, 0.75f, 0.9f, 1f, 0.5f)
      if (layer == GROUND) {
        // Foam round where it struck
        val fp = seg(T, 0f, 0.55f)
        if (fp < 1f) {
          val q = 0.28f + 0.26f * easeOut(fp)
          sb.strokeOval(cx, cy, W * q, H * q, 5f * k * (1f - fp) + 1f, 0.95f, 0.98f, 1f, 0.85f * (1f - fp), 22)
        }
      }
      crown(10, 0.28f, 26f, 3.4f, 0f, 0.55f, 0.35f, 0.65f, 1f, 1f, 1)
      if (layer != AIR) return
      debris(D_DROP, 14, 0f, 0.7f, 0.9f, 34f, 2.4f, 0.55f, 0.8f, 1f, 1)
      glints(3, 0.05f, 0.5f, 0.6f, 20f, 8f, 0.85f, 0.95f, 1f, 0.85f)
    } else {
      // S_GEYSER
      scorch(0.6f, 0.12f, 0.25f, 0.4f, 0.35f, 0.6f)
      shockRing(0.02f, 0.45f, 3f, 0.75f, 0.9f, 1f, 0.8f)
      shockRing(0.15f, 0.65f, 2.4f, 0.75f, 0.9f, 1f, 0.6f)
      shockRing(0.3f, 0.85f, 2f, 0.75f, 0.9f, 1f, 0.45f)
      crown(8, 0.18f, 18f, 3f, 0f, 0.4f, 0.35f, 0.65f, 1f, 1f, 1)
      geyserColumn()
      if (layer != AIR) return
      debris(D_DROP, 16, 0.08f, 0.9f, 1f, 90f, 2.6f, 0.55f, 0.8f, 1f, 1)
      glints(3, 0.1f, 0.6f, 0.4f, 60f, 9f, 0.85f, 0.95f, 1f, 0.85f)
    }
  }
}
