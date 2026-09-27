package com.gridgame.client.render.blasts

import GLBlastRenderers._
import BlastKit._
import Pieces._

/** Lava and flame: molten globs and pools, and fire standing up out of the ground. */
private[blasts] object Fire {
  private[blasts] def lava(st: Int): Unit = {
    val hell = st == S_HELLFIRE
    if (hell) lavaPool(0.4f, 0.9f, 0.16f, 0.1f, 0.13f, 0.04f, 0.07f, 1)
    else lavaPool(0.38f, 1f, 0.48f, 0.08f, 0.28f, 0.09f, 0.05f, 1)
    // Cerberus: three flames out of the pool, one for each head
    if (hell) flames(3, 0.2f, 0f, scatter = false, 24f, 5.5f, 0.02f, 0f, 0.55f,
      0.82f, 0.1f, 0.12f, 1f, 0.55f, 0.2f, 1)
    if (layer != AIR) return
    if (hell) {
      flash(0.06f, 0.45f, 1f, 0.35f, 0.2f)
      debris(D_GLOB, 7, 0f, 0.55f, 0.85f, 24f, 2.6f, 0.9f, 0.16f, 0.12f, 1, shadow = true)
      motes(9, 0.05f, 0.9f, 0.45f, 46f, 1.4f, 0.85f, 0.95f, 0.3f, 1f, 3)
      puffs(2, 0.3f, 1f, 0.1f, 30f, 6f, 12f, 0.24f, 0.14f, 0.2f, 0.6f, 2, lift = 6f)
    } else {
      flash(0.06f, 0.45f, 1f, 0.7f, 0.3f)
      debris(D_GLOB, 8, 0f, 0.55f, 0.85f, 22f, 2.6f, 1f, 0.55f, 0.12f, 1, shadow = true)
      bubbles(3, 0.1f, 0.6f, 0.25f, 3f, 1f, 0.6f, 0.15f, 0.9f, 1)
      puffs(2, 0.3f, 1f, 0.1f, 30f, 6f, 12f, 0.3f, 0.26f, 0.25f, 0.55f, 2, lift = 6f)
    }
  }

  // Scratch for a flame feather's outline
  private val _fx = new Array[Float](10)
  private val _fy = new Array[Float](10)

  /** A feather of flame lying on the ground from `rho0` to `rho1` along `th`, `hw` half-wide at
    * its widest: a lens, so convex. Red-orange round a gold vane, a white quill down it. */
  private def flameFeather(th: Float, rho0: Float, rho1: Float, hw: Float, a: Float): Unit = {
    val c = cosf(th); val s = sinf(th)
    val bx = gx(c, rho0); val by = gy(s, rho0)
    val tx = gx(c, rho1); val ty = gy(s, rho1)
    val dx = tx - bx; val dy = ty - by
    val len = Math.sqrt(dx * dx + dy * dy).toFloat
    if (len < 2f || a <= 0.02f) return
    val nx = -dy / len; val ny = dx / len
    var pass = 0
    while (pass < 3) {
      val wk = (pass: @scala.annotation.switch) match { case 0 => 1f; case 1 => 0.84f; case _ => 0.44f }
      val grow = if (pass == 0) 1.6f else 0f
      // base, one side at 0.25/0.5/0.75, tip, the other side back
      _fx(0) = bx - dx * 0.02f; _fy(0) = by - dy * 0.02f
      var i = 1
      while (i <= 3) {
        val u = i * 0.25f
        val wu = hw * wk * sinf(u * PI * 0.92f + 0.12f) + grow
        _fx(i) = bx + dx * u + nx * wu; _fy(i) = by + dy * u + ny * wu
        _fx(8 - i) = bx + dx * u - nx * wu; _fy(8 - i) = by + dy * u - ny * wu
        i += 1
      }
      _fx(4) = tx + dx * (grow / len); _fy(4) = ty + dy * (grow / len)
      (pass: @scala.annotation.switch) match {
        case 0 => sb.fillPolygon(_fx, _fy, 8, 0.3f, 0.06f, 0.03f, 0.85f * a)
        case 1 => sb.fillPolygon(_fx, _fy, 8, 0.96f, 0.36f, 0.1f, a)
        case _ => sb.fillPolygon(_fx, _fy, 8, 1f, 0.78f, 0.28f, a)
      }
      pass += 1
    }
    sb.strokeLine(bx, by, bx + dx * 0.85f, by + dy * 0.85f, 1.3f, 1f, 0.97f, 0.8f, 0.9f * a)
  }

  private[blasts] def flame(st: Int): Unit = {
    if (st == S_FLAMBE) {
      scorch(0.45f, 0.06f, 0.06f, 0.12f, 0.35f)
      // Blue-footed flames round the pan, orange at their tips
      flames(9, 0.45f, 0.05f, scatter = false, 24f, 5f, 0f, 0f, 0.3f,
        0.3f, 0.5f, 1f, 0.78f, 0.9f, 1f, 1, tr = 1f, tg = 0.6f, tb = 0.16f)
      if (layer != AIR) return
      flash(0.08f, 0.5f, 0.6f, 0.7f, 1f)
      // The whoosh up out of the middle
      val wp = seg(T, 0f, 0.42f)
      if (wp > 0f && wp < 1f) {
        val h = 44f * k * easeOut3(seg(wp, 0f, 0.3f)) * (1f - smooth(seg(wp, 0.45f, 1f)))
        flameTongue(cx, cy, 8f * k, h, sinf(MS * 0.015f) * 3f * k, 1f - seg(wp, 0.7f, 1f),
          0.3f, 0.5f, 1f, 0.8f, 0.9f, 1f, 1f, 0.62f, 0.18f)
      }
      // A pinch of spice
      debris(D_FLECK, 5, 0.02f, 0.6f, 0.8f, 30f, 2.4f, 0.85f, 0.2f, 0.1f, 1)
      debris(D_FLECK, 5, 0.02f, 0.6f, 0.8f, 34f, 2.4f, 0.3f, 0.62f, 0.2f, 2)
      debris(D_FLECK, 4, 0.02f, 0.6f, 0.75f, 26f, 2.4f, 0.95f, 0.78f, 0.3f, 3)
      glints(3, 0.05f, 0.5f, 0.6f, 24f, 8f, 1f, 0.9f, 0.6f, 0.9f)
    } else {
      // S_PHOENIX: a tail of flame fanned out over the ground
      scorch(0.55f, 0.12f, 0.05f, 0.02f, 0.4f)
      if (layer == GROUND) {
        var i = 0
        while (i < 9) {
          val th = (i + 0.3f * hash(seed, 700 + i)) * TWO_PI / 9
          val unfurl = easeOut3(seg(T, 0.01f + 0.015f * i, 0.2f + 0.015f * i))
          val a = tail(T, 0.5f)
          val flutter = 1f + 0.12f * sinf(MS * 0.02f + i * 1.3f)
          flameFeather(th, 0.08f, 0.1f + 0.78f * unfurl, W * 0.085f * flutter * unfurl, a)
          i += 1
        }
      }
      flames(3, 0.08f, 0f, scatter = false, 30f, 6f, 0.02f, 0f, 0.42f, 1f, 0.55f, 0.12f, 1f, 0.95f, 0.6f, 1)
      if (layer != AIR) return
      flash(0.08f, 0.7f, 1f, 0.8f, 0.4f)
      motes(14, 0.05f, 1f, 0.8f, 70f, 1.8f, 1f, 0.72f, 0.22f, 1f, 1)
      glints(3, 0.1f, 0.7f, 0.7f, 30f, 10f, 1f, 0.9f, 0.5f, 0.9f)
    }
  }
}
