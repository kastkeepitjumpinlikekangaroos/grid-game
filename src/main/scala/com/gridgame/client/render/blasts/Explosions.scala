package com.gridgame.client.render.blasts

import GLBlastRenderers._
import BlastKit._
import Pieces._

/** Explosions: a fireball coming apart as it cools, what it throws, smoke — frags, rockets, bombs, cluster
  * bombs, cannonballs, napalm, the inferno, eruptions, mines. */
private[blasts] object Explosions {
  // Heat ramps, hottest first: white-hot, yellow, orange, red, smoke. A fireball's lobes run down
  // one as they cool.
  private val PAL_FIRE = Array(1f, 0.97f, 0.82f, 1f, 0.82f, 0.3f, 0.98f, 0.46f, 0.12f, 0.72f, 0.16f, 0.07f, 0.27f, 0.24f, 0.23f)
  private val PAL_PLASMA = Array(0.92f, 1f, 1f, 0.45f, 0.92f, 1f, 0.2f, 0.55f, 1f, 0.16f, 0.22f, 0.6f, 0.36f, 0.42f, 0.52f)
  private val PAL_EMP = Array(0.95f, 0.98f, 1f, 0.55f, 0.8f, 1f, 0.3f, 0.48f, 0.95f, 0.3f, 0.34f, 0.52f, 0.34f, 0.36f, 0.42f)
  private val PAL_CANNON = Array(1f, 0.95f, 0.75f, 1f, 0.66f, 0.25f, 0.62f, 0.48f, 0.4f, 0.64f, 0.62f, 0.6f, 0.76f, 0.75f, 0.73f)
  private val PAL_NAPALM = Array(1f, 0.95f, 0.7f, 1f, 0.7f, 0.2f, 0.95f, 0.38f, 0.08f, 0.5f, 0.12f, 0.05f, 0.13f, 0.11f, 0.11f)
  private val PAL_INFERNO = Array(1f, 1f, 0.9f, 1f, 0.86f, 0.36f, 1f, 0.5f, 0.12f, 0.56f, 0.14f, 0.06f, 0.28f, 0.2f, 0.2f)
  private val PAL_ERUPTION = Array(1f, 0.9f, 0.6f, 1f, 0.6f, 0.15f, 0.85f, 0.25f, 0.05f, 0.36f, 0.1f, 0.05f, 0.22f, 0.2f, 0.2f)
  private var _hr = 0f
  private var _hg = 0f
  private var _hb = 0f

  /** The colour `heat` of the way up a heat ramp (0 smoke, 1 white-hot), into _hr/_hg/_hb. */
  private def heatColor(pal: Array[Float], heat: Float): Unit = {
    val x = (1f - clamp01(heat)) * 4f
    val i = Math.min(3, x.toInt)
    val f = x - i
    val o = i * 3
    _hr = mix(pal(o), pal(o + 3), f); _hg = mix(pal(o + 1), pal(o + 4), f); _hb = mix(pal(o + 2), pal(o + 5), f)
  }

  private val _uh = new Array[Float](16)

  /**
   * The fireball: `n` lobes swelling out of the middle and rising `rise` units, each white-hot at
   * first and cooling down its heat ramp to smoke by `end`, the outer ones first. `size` of the
   * footprint across. Drawn ink, body, heart, so the cartoon line runs round the whole mass.
   */
  private def fireball(n0: Int, size: Float, end: Float, rise: Float, pal: Array[Float], lift: Float = 0.12f): Unit = {
    if (layer != AIR || T >= end) return
    val n = Math.min(12, n0)
    val grow = easeOut3(seg(T, 0f, 0.16f))
    val p = T / end
    val fade = tail(p, 0.55f)
    // Cooling, it comes apart: the lobes drift away from each other and shrink as they go to smoke
    val apart = 1f + 0.7f * smooth(seg(p, 0.35f, 1f))
    val shrink = 1f - 0.35f * smooth(seg(p, 0.55f, 1f))
    val baseY = cy - H * lift - rise * k * easeOut(p)
    var i = 0
    while (i < n) {
      val d = if (i == 0) 0f else 0.55f + 0.35f * h01(seed, 200 + i)
      val th = if (i == 0) 0f else (i - 1 + 0.4f * hash(seed, 210 + i)) * TWO_PI / (n - 1)
      val rr = W * size * (if (i == 0) 0.62f else 0.4f + 0.14f * h01(seed, 220 + i)) * grow * (1f + 0.22f * p) * shrink
      _ux(i) = cx + cosf(th) * d * W * size * 0.5f * grow * apart
      // Lobes on top rise faster: the whole thing is a column of hot air
      _uy(i) = baseY + sinf(th) * d * W * size * 0.36f * grow * apart - (if (sinf(th) < 0f) rise * 0.45f * k * p else 0f)
      _us(i) = rr
      _uh(i) = 1f - seg(T, 0.015f + 0.05f * d, end * (0.8f + 0.2f * (1f - d)))
      _ua(i) = fade
      i += 1
    }
    var pass = 0
    while (pass < 3) {
      i = 0
      while (i < n) {
        val x = _ux(i); val y = _uy(i); val rr = _us(i); val a = _ua(i)
        (pass: @scala.annotation.switch) match {
          case 0 =>
            heatColor(pal, _uh(i) * 0.6f)
            sb.fillOval(x, y, rr + 2.4f, rr * 0.9f + 2.4f, _hr * 0.3f, _hg * 0.24f, _hb * 0.24f, 0.9f * a, 20)
          case 1 =>
            heatColor(pal, _uh(i))
            sb.fillOval(x, y, rr, rr * 0.9f, _hr, _hg, _hb, a, 20)
          case _ =>
            heatColor(pal, Math.min(1f, _uh(i) + 0.3f))
            sb.fillOval(x + rr * 0.08f, y + rr * 0.14f, rr * 0.56f, rr * 0.48f, _hr, _hg, _hb, a * Math.min(1f, _uh(i) * 2.5f), 16)
        }
        i += 1
      }
      pass += 1
    }
  }

  /**
   * A comic starburst — the KA-BOOM shape: a ten-point star popping out over the first tenth of
   * the blast, overshooting and settling, then shrinking away by `end`. Yellow round a red star
   * round a white heart, inked. Upright, facing the camera, at (x, y), `size` of the footprint.
   */
  private def starburst(x: Float, y: Float, size: Float, t0: Float, end: Float, points: Int, sd: Int): Unit = {
    if (layer != AIR) return
    val p = seg(T, t0, end)
    if (p <= 0f || p >= 1f) return
    val popIn = seg(p, 0f, 0.28f)
    // Overshoot and settle, about 0 → 1.1 → 1, then shrink as it goes
    val pop = if (popIn < 1f) easeOut3(popIn) + 0.16f * sinf(popIn * PI) else 1f - 0.35f * smooth(seg(p, 0.5f, 1f))
    val a = tail(p, 0.62f)
    val rOut = W * size * pop
    val rot = hash(seed, sd) * 0.5f + p * 0.3f
    val n = Math.min(14, points) * 2
    var layerI = 0
    while (layerI < 4) {
      val scale = if (layerI < 2) 1f else if (layerI == 2) 0.64f else 0.3f
      val extra = if (layerI == 0) 2.8f else 0f
      var i = 0
      while (i < n) {
        val th = rot + i * TWO_PI / n
        val out = if ((i & 1) == 0) 1f else 0.56f + 0.08f * hash(seed + sd, i)
        val rr = rOut * scale * out + extra
        _xs(i) = x + cosf(th) * rr
        _ys(i) = y + sinf(th) * rr * 0.86f
        i += 1
      }
      (layerI: @scala.annotation.switch) match {
        case 0 => sb.fillFan(x, y, _xs, _ys, n, 0.32f, 0.08f, 0.04f, 0.92f * a)
        case 1 => sb.fillFan(x, y, _xs, _ys, n, 1f, 0.84f, 0.2f, a)
        case 2 => sb.fillFan(x, y, _xs, _ys, n, 1f, 0.42f, 0.1f, a)
        case _ => sb.fillFan(x, y, _xs, _ys, n, 1f, 0.98f, 0.85f, a)
      }
      layerI += 1
    }
  }

  /** A bomblet going off at ground point (x, y) at `t0`: a small starburst, its own scorch and
    * ring, and a puff of black smoke. */
  private def bomblet(x: Float, y: Float, t0: Float, size: Float, sd: Int): Unit = {
    val p = seg(T, t0, t0 + 0.34f)
    if (p <= 0f || p >= 1f) return
    if (layer == GROUND) {
      val sa = 0.5f * Math.min(1f, p * 8f) * tail(p, 0.5f)
      sb.fillOvalSoft(x, y, W * size * 1.1f, H * size * 1.1f, 0.07f, 0.05f, 0.04f, sa, 0f, 14)
      val q = easeOut3(seg(p, 0f, 0.5f))
      val ra = (1f - seg(p, 0f, 0.5f)) * 0.8f
      if (ra > 0.01f) {
        sb.strokeOval(x, y, W * size * 1.6f * q, H * size * 1.6f * q, 5f, 0.3f, 0.2f, 0.05f, 0.5f * ra, 14)
        sb.strokeOval(x, y, W * size * 1.6f * q, H * size * 1.6f * q, 2.5f, 1f, 0.82f, 0.3f, ra, 14)
      }
    } else {
      val keepT = T; T = p
      starburst(x, y - H * size * 0.5f, size * 1.4f, 0f, 0.55f, 8, sd)
      T = keepT
      val sp = seg(p, 0.15f, 1f)
      if (sp > 0f && sp < 1f) {
        val sa = (1f - sp) * Math.min(1f, sp * 6f) * 0.85f
        val py = y - H * size * 0.4f - 18f * k * easeOut(sp)
        val ps = W * size * (0.35f + 0.5f * easeOut(sp))
        sb.fillOval(x, py, ps + 1.8f, ps * 0.8f + 1.8f, 0.05f, 0.05f, 0.06f, 0.7f * sa, 14)
        sb.fillOval(x, py, ps, ps * 0.8f, 0.2f, 0.19f, 0.22f, sa, 14)
        sb.fillOval(x - ps * 0.22f, py - ps * 0.22f, ps * 0.55f, ps * 0.42f, 0.36f, 0.35f, 0.38f, 0.85f * sa, 12)
      }
    }
  }

  /** The Pirate's cannonball, bowled out of its own blast: bouncing twice and rolling to a stop. */
  private def cannonball(): Unit = {
    if (layer != AIR) return
    val p = seg(T, 0.02f, 0.9f)
    if (p <= 0f || p >= 1f) return
    val th = hash(seed, 77) * PI
    val c = cosf(th); val s = sinf(th)
    val rho = 0.12f + 0.62f * easeOut(p)
    val bounce = Math.abs(sinf(p * PI * 2.2f)) * 14f * k * (1f - p) * (1f - p)
    val x = gx(c, rho); val groundY = gy(s, rho)
    val y = groundY - 4.5f * k - bounce
    val a = tail(p, 0.8f)
    val br = 4.6f * k
    sb.fillOval(x, groundY, br * 1.1f, br * 0.42f, 0f, 0f, 0f, 0.3f * a, 10)
    sb.fillOval(x, y, br + 1.6f, br + 1.6f, 0.03f, 0.03f, 0.04f, 0.95f * a, 14)
    sb.fillOval(x, y, br, br, 0.2f, 0.2f, 0.23f, a, 14)
    sb.fillOval(x - br * 0.32f, y - br * 0.34f, br * 0.34f, br * 0.28f, 0.6f, 0.6f, 0.65f, 0.9f * a, 8)
  }

  private[blasts] def explosion(st: Int): Unit = {
    val pal = (st: @scala.annotation.switch) match {
      case S_PLASMA => PAL_PLASMA
      case S_EMP => PAL_EMP
      case S_CANNON => PAL_CANNON
      case S_NAPALM => PAL_NAPALM
      case S_INFERNO => PAL_INFERNO
      case S_ERUPTION => PAL_ERUPTION
      case _ => PAL_FIRE
    }
    // ── On the ground ──
    (st: @scala.annotation.switch) match {
      case S_ERUPTION =>
        lavaPool(0.46f, 1f, 0.48f, 0.08f, 0.24f, 0.08f, 0.05f, 1)
        shockRing(0f, 0.3f, 6f, 1f, 0.5f, 0.15f, 0.85f)
      case S_NAPALM =>
        scorch(0.95f, 0.1f, 0.06f, 0.03f, 0.55f, 0.72f)
        shockRing(0f, 0.28f, 6f, 1f, 0.6f, 0.2f, 0.8f)
      case S_PLASMA =>
        scorch(0.55f, 0.05f, 0.1f, 0.16f, 0.5f)
        cracks(5, 0.55f, 0.02f, 0.12f, 3f, 0.05f, 0.08f, 0.12f, 0.7f, 0.5f, 0.95f, 1f, 0.9f)
        shockRing(0f, 0.28f, 6f, 0.45f, 0.9f, 1f, 0.9f)
        shockRing(0.05f, 0.42f, 2.6f, 0.85f, 1f, 1f, 0.8f, reach = 0.8f)
      case S_EMP =>
        scorch(0.55f, 0.06f, 0.07f, 0.1f, 0.5f)
        cracks(5, 0.55f, 0.02f, 0.12f, 3f, 0.08f, 0.07f, 0.07f, 0.7f)
        // Coil rings, one after another, as off the Railgunner's slugs
        shockRing(0f, 0.24f, 3.4f, 0.6f, 0.82f, 1f, 1f)
        shockRing(0.05f, 0.3f, 3f, 0.75f, 0.9f, 1f, 0.9f)
        shockRing(0.1f, 0.36f, 2.6f, 0.9f, 0.95f, 1f, 0.8f)
      case S_CANNON =>
        scorch(0.5f, 0.07f, 0.05f, 0.04f, 0.6f, 0.7f)
        cracks(6, 0.6f, 0.02f, 0.1f, 3.4f, 0.1f, 0.08f, 0.06f, 0.75f)
        shockRing(0f, 0.3f, 6f, 0.9f, 0.85f, 0.75f, 0.85f)
      case S_INFERNO =>
        scorch(0.7f, 0.12f, 0.04f, 0.02f, 0.6f)
        cracks(6, 0.6f, 0.02f, 0.14f, 3.4f, 0.12f, 0.05f, 0.03f, 0.75f, 1f, 0.6f, 0.15f, 0.9f)
        shockRing(0f, 0.3f, 8f, 1f, 0.6f, 0.2f, 0.9f)
      case S_BOMB | S_CLUSTER | S_BOMB_ROCKET =>
        scorch(0.66f, 0.06f, 0.05f, 0.05f, 0.55f)
        cracks(5, 0.55f, 0.02f, 0.1f, 3.2f, 0.08f, 0.06f, 0.05f, 0.7f)
        shockRing(0f, 0.3f, 8f, 1f, 0.84f, 0.3f, 0.95f)
      case _ => // S_FRAG, S_ROCKET, S_MINE
        scorch(0.6f, 0.08f, 0.06f, 0.05f, 0.55f)
        cracks(6, 0.58f, 0.02f, 0.12f, 3.2f, 0.09f, 0.07f, 0.06f, 0.72f)
        shockRing(0f, 0.3f, 6.5f, 0.88f, 0.78f, 0.6f, 0.85f)
        if (st == S_MINE) shockRing(0.04f, 0.38f, 2.6f, 0.5f, 0.8f, 1f, 0.8f, reach = 0.85f)
    }

    // ── Standing: the flames of napalm and the inferno ──
    if (st == S_NAPALM) flames(14, 0.92f, 0f, scatter = true, 20f, 5f, 0.02f, 0.2f, 0.62f,
      0.98f, 0.42f, 0.08f, 1f, 0.86f, 0.4f, 1)
    if (st == S_INFERNO) flames(10, 0.78f, 0.06f, scatter = false, 28f, 6f, 0.03f, 0f, 0.55f,
      0.96f, 0.36f, 0.06f, 1f, 0.9f, 0.5f, 2)
    // The bomblets of a cluster, going off one after another round the first
    if (st == S_CLUSTER) {
      var i = 0
      while (i < 5) {
        val th = (i + 0.4f * hash(seed, 500 + i)) * TWO_PI / 5
        val rr = 0.5f + 0.35f * h01(seed, 510 + i)
        bomblet(gx(cosf(th), rr), gy(sinf(th), rr), 0.1f + 0.075f * i, 0.16f, 520 + i)
        i += 1
      }
    }

    if (layer != AIR) return

    // ── The blast itself. What it throws is drawn first, so it comes out of the fireball
    // instead of being painted across it ──
    (st: @scala.annotation.switch) match {
      case S_FRAG =>
        flash(0.08f, 0.6f, 1f, 0.92f, 0.7f)
        debris(D_SPARK, 10, 0f, 0.3f, 1f, 12f, 2.2f, 1f, 0.75f, 0.35f, 1)
        debris(D_CHIP, 9, 0.01f, 0.9f, 0.9f, 46f, 3.6f, 0.4f, 0.3f, 0.2f, 2, shadow = true)
        debris(D_CHIP, 5, 0.01f, 0.8f, 0.85f, 30f, 2.8f, 0.46f, 0.48f, 0.52f, 3)
        fireball(5, 0.42f, 0.5f, 14f, pal)
        puffs(5, 0.22f, 1f, 0.25f, 34f, 10f, 22f, 0.42f, 0.4f, 0.39f, 0.8f, 1, outward = 0.15f, lift = 10f)
      case S_ROCKET =>
        flash(0.08f, 0.65f, 1f, 0.9f, 0.6f)
        debris(D_SPARK, 12, 0f, 0.38f, 1f, 20f, 2.2f, 1f, 0.7f, 0.3f, 1)
        debris(D_CHIP, 6, 0.01f, 0.85f, 0.8f, 36f, 3f, 0.35f, 0.33f, 0.32f, 2, shadow = true)
        fireball(6, 0.46f, 0.5f, 18f, pal)
        // The smoke ring rolling out along the ground
        puffs(11, 0.1f, 0.95f, 0.3f, 6f, 12f, 22f, 0.52f, 0.5f, 0.48f, 0.62f, 2, outward = 0.6f, flat = 0.6f, inkK = 0.55f)
        puffs(3, 0.3f, 1f, 0.1f, 44f, 12f, 24f, 0.3f, 0.29f, 0.29f, 0.7f, 3, lift = 14f)
      case S_BOMB =>
        debris(D_CHIP, 8, 0.01f, 0.85f, 0.9f, 40f, 3.2f, 0.2f, 0.2f, 0.23f, 2, shadow = true)
        debris(D_SPARK, 8, 0f, 0.35f, 0.95f, 18f, 2.2f, 1f, 0.8f, 0.3f, 3)
        starburst(cx, cy - H * 0.3f, 0.52f, 0f, 0.32f, 10, 1)
        puffs(7, 0.14f, 1f, 0.3f, 30f, 12f, 24f, 0.2f, 0.19f, 0.22f, 0.9f, 1, outward = 0.25f, lift = 8f)
        glints(4, 0.05f, 0.6f, 0.8f, 30f, 10f, 1f, 0.92f, 0.45f, 0.95f)
      case S_CLUSTER =>
        debris(D_CHIP, 7, 0.01f, 0.8f, 0.85f, 36f, 3f, 0.2f, 0.2f, 0.23f, 2, shadow = true)
        starburst(cx, cy - H * 0.3f, 0.4f, 0f, 0.28f, 10, 1)
        puffs(5, 0.12f, 0.9f, 0.25f, 26f, 10f, 20f, 0.2f, 0.19f, 0.22f, 0.85f, 1, outward = 0.2f, lift = 8f)
      case S_BOMB_ROCKET =>
        debris(D_SPARK, 12, 0f, 0.38f, 1f, 20f, 2.2f, 1f, 0.72f, 0.3f, 1)
        starburst(cx, cy - H * 0.32f, 0.5f, 0f, 0.3f, 10, 1)
        puffs(11, 0.1f, 0.95f, 0.3f, 6f, 12f, 22f, 0.24f, 0.23f, 0.26f, 0.7f, 2, outward = 0.6f, flat = 0.6f, inkK = 0.55f)
        glints(3, 0.05f, 0.5f, 0.8f, 26f, 9f, 1f, 0.92f, 0.45f, 0.9f)
      case S_PLASMA =>
        flash(0.08f, 0.6f, 0.6f, 0.9f, 1f)
        debris(D_HEX, 8, 0.01f, 0.8f, 0.85f, 30f, 3f, 0.36f, 0.42f, 0.5f, 2, shadow = true)
        debris(D_SPARK, 10, 0f, 0.35f, 1f, 16f, 2f, 0.5f, 0.9f, 1f, 3)
        fireball(5, 0.38f, 0.42f, 10f, pal)
        if (T < 0.32f) {
          val epoch = (MS / 70f).toInt
          val a = 1f - T / 0.32f
          var i = 0
          while (i < 3) {
            val th = hash(seed, 300 + i + epoch * 3) * PI + i * 2.1f
            bolt(cx, cy - H * 0.2f, gx(cosf(th), 0.72f), gy(sinf(th), 0.72f), 6, 5f * k, seed + epoch * 7 + i,
              2.6f * k, 0.45f, 0.9f, 1f, a)
            i += 1
          }
        }
        puffs(3, 0.3f, 0.95f, 0.15f, 28f, 8f, 16f, 0.75f, 0.82f, 0.9f, 0.55f, 4, lift = 8f)
      case S_EMP =>
        flash(0.07f, 0.55f, 0.7f, 0.85f, 1f)
        debris(D_SHARD, 10, 0f, 0.6f, 1f, 22f, 3f, 0.78f, 0.8f, 0.86f, 1)
        debris(D_SPARK, 10, 0f, 0.4f, 0.95f, 14f, 2f, 0.5f, 0.75f, 1f, 2)
        fireball(5, 0.36f, 0.45f, 12f, pal)
        if (T > 0.06f && T < 0.5f) {
          // Static crawling round the rim
          val epoch = (MS / 80f).toInt
          val a = life(seg(T, 0.06f, 0.5f), 0.2f, 0.5f)
          var i = 0
          while (i < 4) {
            val th = (i + 0.5f * hash(seed + epoch, 400 + i)) * TWO_PI / 4
            bolt(gx(cosf(th), 0.8f), gy(sinf(th), 0.8f), gx(cosf(th + 0.4f), 0.85f), gy(sinf(th + 0.4f), 0.85f),
              4, 3f * k, seed * 3 + epoch * 5 + i, 1.8f * k, 0.55f, 0.8f, 1f, a)
            i += 1
          }
        }
        puffs(4, 0.3f, 1f, 0.2f, 30f, 9f, 20f, 0.4f, 0.42f, 0.46f, 0.7f, 3, lift = 8f)
      case S_CANNON =>
        flash(0.06f, 0.45f, 1f, 0.9f, 0.6f)
        debris(D_SPLINTER, 10, 0.01f, 0.85f, 0.95f, 40f, 3.4f, 0.58f, 0.4f, 0.22f, 2, shadow = true)
        debris(D_SPARK, 6, 0f, 0.3f, 0.9f, 12f, 2f, 1f, 0.8f, 0.4f, 3)
        fireball(4, 0.3f, 0.3f, 8f, pal)
        // Black powder: the smoke is the blast
        puffs(9, 0.04f, 1f, 0.28f, 26f, 12f, 28f, 0.8f, 0.79f, 0.77f, 0.88f, 1, outward = 0.45f, lift = 6f, inkK = 0.8f)
        cannonball()
      case S_NAPALM =>
        flash(0.07f, 0.7f, 1f, 0.85f, 0.5f)
        debris(D_GLOB, 8, 0f, 0.45f, 0.95f, 26f, 2.6f, 1f, 0.55f, 0.15f, 1)
        fireball(5, 0.4f, 0.35f, 10f, pal)
        puffs(6, 0.2f, 1f, 0.4f, 52f, 10f, 22f, 0.13f, 0.11f, 0.11f, 0.8f, 2, lift = 12f)
      case S_INFERNO =>
        flash(0.09f, 0.8f, 1f, 0.8f, 0.4f)
        fireball(7, 0.5f, 0.55f, 26f, pal)
        motes(12, 0.08f, 0.95f, 0.65f, 64f, 1.6f, 1f, 0.6f, 0.2f, 1f, 1)
        puffs(3, 0.4f, 1f, 0.1f, 52f, 12f, 24f, 0.28f, 0.2f, 0.19f, 0.6f, 2, lift = 24f)
      case S_ERUPTION =>
        flash(0.07f, 0.6f, 1f, 0.7f, 0.3f)
        debris(D_GLOB, 10, 0f, 0.8f, 1f, 72f, 3.6f, 1f, 0.5f, 0.1f, 1, shadow = true)
        debris(D_CHIP, 8, 0f, 0.75f, 0.85f, 52f, 3.4f, 0.2f, 0.17f, 0.16f, 2, shadow = true)
        fireball(5, 0.34f, 0.35f, 14f, pal)
        puffs(6, 0.12f, 1f, 0.12f, 80f, 10f, 24f, 0.24f, 0.22f, 0.22f, 0.8f, 3, lift = 14f)
        motes(10, 0.05f, 0.9f, 0.4f, 56f, 1.5f, 1f, 0.55f, 0.15f, 1f, 4)
      case _ => // S_MINE
        flash(0.07f, 0.6f, 1f, 0.9f, 0.7f)
        debris(D_BLOCK, 7, 0.01f, 0.8f, 0.9f, 36f, 3.2f, 0.42f, 0.45f, 0.5f, 1, shadow = true)
        debris(D_SPARK, 8, 0f, 0.35f, 1f, 16f, 2f, 0.5f, 0.8f, 1f, 2)
        fireball(5, 0.4f, 0.45f, 12f, pal)
        puffs(4, 0.28f, 1f, 0.2f, 32f, 9f, 20f, 0.4f, 0.4f, 0.42f, 0.75f, 3, lift = 8f)
    }
  }
}
