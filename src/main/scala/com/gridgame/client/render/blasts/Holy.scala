package com.gridgame.client.render.blasts

import GLBlastRenderers._
import BlastKit._
import Pieces._

/** Holy light: a smite's sunburst and the Crusader's shield emblem. */
private[blasts] object Holy {
  /** A sunburst on the ground: a ring, rays out of it, and a cross in the middle. */
  private def sunburst(a: Float): Unit = {
    if (layer != GROUND || a <= 0.01f) return
    val r = 1f; val g = 0.86f; val b = 0.36f
    sb.strokeOval(cx, cy, W * 0.72f, H * 0.72f, 3f * k + 2.4f, ink(r), ink(g), ink(b), 0.45f * a, 24)
    sb.strokeOval(cx, cy, W * 0.72f, H * 0.72f, 3f * k, r, g, b, a, 24)
    var i = 0
    while (i < 12) {
      val th = i * TWO_PI / 12 + hash(seed, 2200) * 0.3f
      val out = if ((i & 1) == 0) 1f else 0.9f
      _xs(0) = gx(cosf(th), 0.78f); _ys(0) = gy(sinf(th), 0.78f)
      _xs(1) = gx(cosf(th), out); _ys(1) = gy(sinf(th), out)
      sb.strokePolylineTapered(_xs, _ys, 2, 4f * k, 0.6f, r, g, b, a, 0.3f * a)
      i += 1
    }
    // The cross, upright on the screen and lying on the ground. Along the ground's own axes it
    // was an X
    val l = W * 0.42f
    var pass = 0
    while (pass < 2) {
      val lw = 4.4f * k + (if (pass == 0) 2.4f else 0f)
      val cr = if (pass == 0) ink(r) else 1f; val cg = if (pass == 0) ink(g) else 0.95f; val cb = if (pass == 0) ink(b) else 0.7f
      val ca = if (pass == 0) 0.5f * a else a
      sb.strokeLine(cx - l * 0.55f, cy - l * 0.1f, cx + l * 0.55f, cy - l * 0.1f, lw, cr, cg, cb, ca)
      sb.strokeLine(cx, cy - l * 0.32f, cx, cy + l * 0.46f, lw, cr, cg, cb, ca)
      pass += 1
    }
  }

  /** A heater shield of gold at (x, y), `s` half its height, with a white cross on it. */
  private def shieldEmblem(x: Float, y: Float, s: Float, a: Float): Unit = {
    if (a <= 0.02f || s < 1f) return
    var pass = 0
    while (pass < 2) {
      val grow = if (pass == 0) 2.2f else 0f
      _xs(0) = x - 0.9f * s - grow; _ys(0) = y - s - grow
      _xs(1) = x + 0.9f * s + grow; _ys(1) = y - s - grow
      _xs(2) = x + 0.9f * s + grow; _ys(2) = y + 0.15f * s
      _xs(3) = x; _ys(3) = y + 1.3f * s + grow * 1.4f
      _xs(4) = x - 0.9f * s - grow; _ys(4) = y + 0.15f * s
      if (pass == 0) sb.fillPolygon(_xs, _ys, 5, 0.3f, 0.2f, 0.05f, 0.9f * a)
      else sb.fillPolygon(_xs, _ys, 5, 1f, 0.8f, 0.28f, a)
      pass += 1
    }
    _xs(0) = x - 0.78f * s; _ys(0) = y - 0.88f * s
    _xs(1) = x; _ys(1) = y - 0.88f * s
    _xs(2) = x; _ys(2) = y + 1.1f * s
    _xs(3) = x - 0.78f * s; _ys(3) = y + 0.1f * s
    sb.fillPolygon(_xs, _ys, 4, 1f, 0.92f, 0.55f, a)
    sb.strokeLine(x, y - 0.72f * s, x, y + 0.92f * s, 0.3f * s, 1f, 1f, 1f, a)
    sb.strokeLine(x - 0.6f * s, y - 0.18f * s, x + 0.6f * s, y - 0.18f * s, 0.3f * s, 1f, 1f, 1f, a)
  }

  private[blasts] def holy(st: Int): Unit = {
    if (st == S_SMITE) {
      scorch(0.55f, 1f, 0.95f, 0.7f, 0.25f, 0.5f)
      sunburst(0.95f * life(T, 0.1f, 0.6f))
      if (layer != AIR) return
      // The beam of light driven down onto it
      val bp = seg(T, 0f, 0.45f)
      if (bp < 1f) {
        val a = 1f - smooth(bp)
        val bw = W * 0.13f * (1f - 0.55f * bp)
        val top = cy - 260f * k
        val ht = cy - top
        sb.fillRectGradient(cx - bw * 2f, top, bw * 4f, ht,
          1f, 0.9f, 0.55f, 0f, 1f, 0.9f, 0.55f, 0f, 1f, 0.9f, 0.55f, 0.35f * a, 1f, 0.9f, 0.55f, 0.35f * a)
        sb.fillRectGradient(cx - bw, top, bw * 2f, ht,
          1f, 0.92f, 0.6f, 0.2f * a, 1f, 0.92f, 0.6f, 0.2f * a, 1f, 0.92f, 0.6f, 0.95f * a, 1f, 0.92f, 0.6f, 0.95f * a)
        sb.fillRect(cx - bw * 0.36f, top, bw * 0.72f, ht, 1f, 1f, 0.96f, a)
        sb.strokeLine(cx - bw, top, cx - bw, cy, 1.4f, 0.8f, 0.55f, 0.15f, 0.7f * a)
        sb.strokeLine(cx + bw, top, cx + bw, cy, 1.4f, 0.8f, 0.55f, 0.15f, 0.7f * a)
      }
      flash(0.1f, 0.6f, 1f, 0.95f, 0.7f)
      debris(D_FEATHER, 5, 0.05f, 1f, 0.8f, 60f, 3.4f, 1f, 0.97f, 0.88f, 1)
      glints(6, 0.05f, 0.8f, 0.8f, 30f, 10f, 1f, 0.95f, 0.6f, 1f)
    } else {
      // S_SHIELD_BASH: a golden ring driving everyone back, and the Crusader's shield
      shockRing(0f, 0.45f, 12f, 1f, 0.82f, 0.35f, 0.95f)
      shockRing(0.06f, 0.5f, 5f, 1f, 0.95f, 0.75f, 0.8f)
      if (layer == GROUND) {
        val p = seg(T, 0f, 0.45f)
        if (p > 0f && p < 1f) {
          val q = easeOut3(p)
          val a = (1f - p) * Math.min(1f, p * 10f)
          var i = 0
          while (i < 10) {
            val th = (i + 0.5f) * TWO_PI / 10 + hash(seed, 2300) * 0.5f
            _xs(0) = gx(cosf(th - 0.09f), q - 0.05f); _ys(0) = gy(sinf(th - 0.09f), q - 0.05f)
            _xs(1) = gx(cosf(th), q + 0.03f); _ys(1) = gy(sinf(th), q + 0.03f)
            _xs(2) = gx(cosf(th + 0.09f), q - 0.05f); _ys(2) = gy(sinf(th + 0.09f), q - 0.05f)
            sb.strokePolyline(_xs, _ys, 3, 3.6f * k + 2.4f, 0.3f, 0.2f, 0.05f, 0.6f * a)
            sb.strokePolyline(_xs, _ys, 3, 3.6f * k, 1f, 0.9f, 0.55f, a)
            i += 1
          }
        }
      }
      if (layer != AIR) return
      flash(0.08f, 0.5f, 1f, 0.9f, 0.6f)
      val sp = seg(T, 0f, 0.5f)
      if (sp < 1f) {
        val pop = if (sp < 0.2f) { val q = easeOut3(sp / 0.2f); q * (1f + 0.5f * (1f - q)) } else 1f
        shieldEmblem(cx, cy - 28f * k - 10f * k * easeOut(sp), 17f * k * pop, 1f - smooth(seg(sp, 0.55f, 1f)))
      }
      puffs(8, 0.05f, 0.85f, 0.85f, 10f, 12f, 22f, 0.8f, 0.72f, 0.58f, 0.45f, 1, outward = 0.15f, flat = 0.56f, inkK = 0.4f)
      glints(3, 0.05f, 0.5f, 0.6f, 24f, 9f, 1f, 0.92f, 0.6f, 0.9f)
    }
  }
}
