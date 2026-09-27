package com.gridgame.client.render.blasts

import com.gridgame.client.render.projectiles.GLProjectileRenderers
import GLBlastRenderers._
import BlastKit._
import Pieces._

/** The dark: souls streaming in, the dead raised, bones, the banshee's wail and echo, a shadow burst. */
private[blasts] object Dark {
  /** A soul, or a spirit: a pale head (two dark eyes if `eyes`) and a straight tail of light
    * streaming away behind it, opposite (dx, dy). */
  private def soul(x: Float, y: Float, dx: Float, dy: Float, sz: Float, a: Float,
                   r: Float, g: Float, b: Float, eyes: Boolean): Unit = {
    if (a <= 0.02f) return
    val dl = Math.max(0.01f, Math.sqrt(dx * dx + dy * dy).toFloat)
    val ux = dx / dl; val uy = dy / dl
    var i = 0
    while (i < 4) {
      val t = i / 3f
      _px(i) = x - ux * sz * 3.4f * t; _py(i) = y - uy * sz * 3.4f * t
      _pw(i) = sz * 1.7f * (1f - t * 0.92f)
      _pa(i) = a * 0.75f * (1f - t)
      i += 1
    }
    sb.strokePolylineVar(_px, _py, _pw, _pa, 4, r, g, b)
    sb.fillOval(x, y, sz + 1.3f, sz * 1.05f + 1.3f, ink(r), ink(g), ink(b), 0.6f * a, 12)
    sb.fillOval(x, y, sz, sz * 1.05f, lit(r), lit(g), lit(b), a, 12)
    if (eyes) {
      sb.fillOval(x - sz * 0.34f, y - sz * 0.05f, sz * 0.17f, sz * 0.24f, 0.05f, 0.15f, 0.1f, a, 6)
      sb.fillOval(x + sz * 0.34f, y - sz * 0.05f, sz * 0.17f, sz * 0.24f, 0.05f, 0.15f, 0.1f, a, 6)
    }
  }

  /** The Banshee's face: a pale head rising out of the wail, hair streaming up off it, its mouth
    * opening wider as it screams (`open` 0-1). */
  private def wailFace(x: Float, y: Float, sz: Float, open: Float, a: Float): Unit = {
    if (a <= 0.02f) return
    var i = -1
    while (i <= 1) {
      tendril(x + i * sz * 0.45f, y - sz * 0.55f, -PI * 0.5f + i * 0.55f, sz * 1.7f, 1f, i * 0.9f + 0.2f,
        sz * 0.55f, 0.62f, 0.82f, 0.96f, 0.55f * a, n = 7, wisp = true)
      i += 1
    }
    sb.fillOvalSoft(x, y, sz * 1.9f, sz * 2f, 0.6f, 0.85f, 1f, 0.32f * a, 0f, 18)
    sb.fillOval(x, y, sz * 0.8f + 2f, sz + 2f, 0.12f, 0.22f, 0.32f, 0.7f * a, 18)
    sb.fillOval(x, y, sz * 0.8f, sz, 0.84f, 0.95f, 1f, 0.85f * a, 18)
    sb.fillOval(x - sz * 0.28f, y - sz * 0.12f, sz * 0.17f, sz * 0.25f, 0.06f, 0.1f, 0.2f, a, 10)
    sb.fillOval(x + sz * 0.28f, y - sz * 0.12f, sz * 0.17f, sz * 0.25f, 0.06f, 0.1f, 0.2f, a, 10)
    sb.fillOval(x, y + sz * 0.42f, sz * (0.16f + 0.08f * open), sz * (0.18f + 0.3f * open), 0.06f, 0.1f, 0.2f, a, 12)
  }

  private[blasts] def dark(st: Int): Unit = {
    (st: @scala.annotation.switch) match {
      case S_SOUL_HARVEST =>
        scorch(0.7f, 0.05f, 0.12f, 0.08f, 0.45f)
        runeCircle(0.9f, 10, -T * 0.5f, 3f, 0.35f, 0.92f, 0.5f, 0.95f * life(T, 0.08f, 0.68f))
        if (layer != AIR) return
        // Souls rising out of the ground, then streaming in to whoever called them
        val n = Math.max(5, (9 * det + 0.5f).toInt)
        var i = 0
        while (i < n) {
          val th = (i + 0.5f * hash(seed, 1400 + i)) * TWO_PI / n
          val rr = 0.35f + 0.6f * h01(seed, 1410 + i)
          val bx = gx(cosf(th), rr); val by = gy(sinf(th), rr)
          val s1 = 0.03f + 0.1f * h01(seed, 1420 + i)
          val up = easeOut(seg(T, s1, s1 + 0.2f))
          val s2 = 0.36f + 0.1f * h01(seed, 1430 + i)
          val in = seg(T, s2, s2 + 0.34f)
          if (up > 0f && in < 1f) {
            val q = in * in
            val sx0 = bx; val sy0 = by - 16f * k * up
            val tx = cx; val ty = cy - 18f * k
            val swirl = sinf(in * PI) * 0.35f
            val x = mix(sx0, tx, q) + (ty - sy0) * swirl * 0.3f
            val y = mix(sy0, ty, q) - (tx - sx0) * swirl * 0.15f
            val dx = if (in > 0f) tx - sx0 else 0f
            val dy = if (in > 0f) ty - sy0 else -1f
            soul(x, y, dx, dy, 5.6f * k * (1f - 0.5f * q), Math.min(1f, up * 2f) * (1f - seg(in, 0.8f, 1f)),
              0.35f, 0.95f, 0.55f, eyes = true)
          }
          i += 1
        }
        flash(0.14f, 0.35f, 0.5f, 1f, 0.7f, 0.9f, at = 0.7f)
        motes(8, 0.05f, 0.95f, 0.8f, 36f, 1.4f, 0.45f, 1f, 0.6f, 0.9f, 1)
      case S_RAISE_DEAD =>
        scorch(0.6f, 0.3f, 0.85f, 0.45f, 0.22f, 0.6f)
        scorch(0.4f, 0.1f, 0.08f, 0.05f, 0.35f)
        // Hands clawing up out of the ground, closing on whatever is there, and sinking back
        val n = 5
        var i = 0
        while (i < n) {
          val th = (i + 0.4f * hash(seed, 1500 + i)) * TWO_PI / n
          val rr = 0.32f + 0.45f * h01(seed, 1510 + i)
          _ux(i) = gx(cosf(th), rr); _ordY(i) = gy(sinf(th), rr)
          i += 1
        }
        sortFarToNear(n)
        var j = 0
        while (j < n) {
          val i2 = _ord(j)
          val y = _ordY(i2)
          if (mine(y)) {
            val start = 0.03f + 0.08f * h01(seed, 1520 + i2)
            val rise = easeOut3(seg(T, start, start + 0.16f)) * (1f - smooth(seg(T, 0.7f, 0.95f)))
            val open = 1f - 0.8f * smooth(seg(T, 0.3f, 0.52f))
            GLProjectileRenderers.deadHand(sb, _ux(i2), y, rise, hash(seed, 1530 + i2) * 0.25f, open,
              Math.min(1f, rise * 3f), 1.15f * k)
          }
          j += 1
        }
        if (layer != AIR) return
        debris(D_BONE, 5, 0f, 0.7f, 0.8f, 26f, 2.4f, 0.92f, 0.9f, 0.8f, 1)
        debris(D_CHIP, 6, 0f, 0.6f, 0.75f, 24f, 2.6f, 0.32f, 0.24f, 0.15f, 2)
        motes(6, 0.1f, 0.9f, 0.6f, 40f, 1.4f, 0.45f, 1f, 0.5f, 0.9f, 3)
      case S_BONES =>
        scorch(0.4f, 0.15f, 0.12f, 0.18f, 0.35f)
        shockRing(0f, 0.3f, 5f, 0.75f, 0.6f, 0.95f, 0.85f)
        if (layer != AIR) return
        debris(D_BONE, 12, 0f, 0.8f, 0.95f, 38f, 3.2f, 0.93f, 0.9f, 0.8f, 1, shadow = true)
        flash(0.07f, 0.35f, 0.8f, 0.7f, 1f, 0.6f)
        puffs(4, 0.05f, 0.9f, 0.3f, 20f, 11f, 20f, 0.75f, 0.7f, 0.65f, 0.5f, 2, outward = 0.3f, flat = 0.66f, inkK = 0.45f)
        glints(2, 0.05f, 0.5f, 0.6f, 20f, 8f, 0.85f, 0.7f, 1f, 0.8f)
      case S_ECHO =>
        scorch(0.5f, 0.4f, 0.62f, 0.95f, 0.26f)
        soundRings(4, 3, 0f, 0.75f, 1f, 5.6f, 0.52f, 0.8f, 1f, 1f)
        if (layer != AIR) return
        flash(0.1f, 0.55f, 0.7f, 0.9f, 1f, 1f)
        // The ghost of the bolt, hanging where it struck for a moment
        val gp = seg(T, 0f, 0.5f)
        if (gp < 1f) sb.fillOvalSoft(cx, cy - 12f * k, 16f * k * (0.7f + 0.3f * gp), 14f * k * (0.7f + 0.3f * gp),
          0.6f, 0.85f, 1f, 0.55f * (1f - gp), 0f, 16)
        var i = 0
        while (i < 6) {
          val th = (i + 0.5f * hash(seed, 1600 + i)) * TWO_PI / 6
          val p = seg(T, 0.02f, 0.75f)
          if (p > 0f && p < 1f) {
            val rr = 0.15f + 0.8f * easeOut(p)
            val x = gx(cosf(th), rr); val y = gy(sinf(th), rr) - 10f * k * easeOut(p)
            soul(x, y, cosf(th) * W, sinf(th) * H - 10f, 5f * k, (1f - p) * Math.min(1f, p * 6f), 0.55f, 0.8f, 1f, eyes = false)
          }
          i += 1
        }
      case S_WAIL =>
        var i = 0
        while (i < 5) {
          val start = 0.03f * i + 0.14f * i
          val p = seg(T, start, start + 0.5f)
          if (p > 0f && p < 1f)
            wavyRing(easeOut(p), 0.04f, 9, MS * 0.012f + i, 3.4f * k * (1f - 0.5f * p), 0.65f, 0.9f, 1f, 0.85f * (1f - p))
          i += 1
        }
        if (layer != AIR) return
        flash(0.08f, 0.45f, 0.7f, 0.92f, 1f, 0.7f)
        val fp = seg(T, 0f, 0.8f)
        if (fp < 1f) wailFace(cx, cy - (14f + 26f * easeOut(fp)) * k, 16f * k * (1f + 0.25f * seg(fp, 0.1f, 0.6f)),
          seg(fp, 0.05f, 0.4f), 0.85f * life(fp, 0.1f, 0.55f))
        i = 0
        while (i < 8) {
          val th = (i + 0.5f * hash(seed, 1700 + i)) * TWO_PI / 8
          val p = seg(T, 0.05f + 0.03f * (i & 3), 0.85f)
          if (p > 0f && p < 1f) {
            val rr = 0.12f + 0.85f * easeOut(p)
            soul(gx(cosf(th), rr), gy(sinf(th), rr) - 16f * k * (1f - p), cosf(th) * W, sinf(th) * H, 3.4f * k,
              (1f - p) * Math.min(1f, p * 5f), 0.6f, 0.86f, 1f, eyes = false)
          }
          i += 1
        }
      case _ => // S_SHADOW_BURST
        scorch(0.72f, 0.06f, 0.02f, 0.1f, 0.6f)
        if (layer == GROUND) {
          // Tendrils of shadow lashing out over the ground, whipping as they go
          var i = 0
          while (i < 10) {
            val th = (i + 0.4f * hash(seed, 1800 + i)) * TWO_PI / 10
            val c = cosf(th); val s = sinf(th)
            val ang = Math.atan2(s * H, c * W).toFloat
            val len = Math.sqrt(c * W * c * W + s * H * s * H).toFloat * (0.72f + 0.22f * h01(seed, 1810 + i))
            val grow = easeOut3(seg(T, 0.02f, 0.16f)) * (1f - smooth(seg(T, 0.55f + 0.1f * h01(seed, 1820 + i), 0.92f)))
            tendril(cx, cy, ang, len, grow, sinf(MS * 0.012f + i * 1.7f) * 0.9f, 9f * k,
              0.12f, 0.04f, 0.18f, 1f, n = 10, wisp = true, hr = 0.72f, hg = 0.38f, hb = 1f)
            i += 1
          }
        }
        if (layer != AIR) return
        val cp = seg(T, 0f, 0.42f)
        if (cp < 1f) {
          val cr = W * 0.34f * easeOut3(seg(cp, 0f, 0.3f)) * (1f - smooth(seg(cp, 0.45f, 1f)))
          sb.fillOval(cx, cy - H * 0.1f, cr, cr * 0.6f, 0.05f, 0.02f, 0.08f, 0.92f, 22)
          sb.strokeOval(cx, cy - H * 0.1f, cr, cr * 0.6f, 3f * k, 0.72f, 0.38f, 1f, 0.9f * (1f - cp), 22)
        }
        debris(D_SPARK, 10, 0f, 0.4f, 1f, 18f, 2.2f, 0.7f, 0.35f, 1f, 1)
        puffs(5, 0.1f, 0.9f, 0.4f, 30f, 8f, 16f, 0.2f, 0.1f, 0.28f, 0.6f, 2, inkK = 0.6f)
    }
  }
}
