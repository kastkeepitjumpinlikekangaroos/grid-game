package com.gridgame.client.render.projectiles

import com.gridgame.common.model.Projectile
import com.gridgame.client.gl.ShapeBatch
import ProjectileKit._
import Glyphs._

/**
 * The elementalists' shots, drawn as theirs: the Tidecaller's trident of water, the Sandstorm's whirling
 * blade of sand, the Inferno's wheel of fire with its own face in the hub. They were a ring of water on
 * the ground, a sand orb, and an orange fireball with a flower of flame round it.
 */
private[render] object Elements {

  // ── The Tidecaller ──
  private val TRI_MID = Array(-4.4f, -1.3f, 5f, -1.1f, 9f, 0f, 5f, 1.1f, -4.4f, 1.3f)
  private val TRI_UP = Array(-5.6f, -6.9f, 2.4f, -6.4f, 6.6f, -5f, 2f, -4.4f, -5.6f, -4.6f)
  private val TRI_LOW = Array(-5.6f, 4.6f, 2f, 4.4f, 6.6f, 5f, 2.4f, 6.4f, -5.6f, 6.9f)
  private val TRI_BAR = Array(-7.2f, -7.2f, -4.6f, -7f, -4.6f, 7f, -7.2f, 7.2f)
  private val TRI_SHAFT = Array(-24f, -1.2f, -7f, -1.5f, -7f, 1.5f, -24f, 1.2f)
  private val TRI_PARTS = Array(TRI_MID, TRI_UP, TRI_LOW, TRI_BAR, TRI_SHAFT)

  /**
   * The Tidecaller's splash: her trident in water — three prongs, point first, its shaft dissolving into
   * the stream of water it is made of, bubbles in it, drops falling off its tines, foam at its points.
   * It grows with the charge.
   */
  private[projectiles] def drawWaterTrident(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 37) * 0.35
    computeAllDynamics(proj, 0.3f, 0.62f, 1f, phase)
    val a = dynAlpha
    val ds = dynScale
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx
    val k = 1.35f * ds
    val bx = sx - ndx * 9f * k; val by = sy - ndy * 9f * k

    sb.fillOvalSoft(bx - ndx * 6f * k, by - ndy * 6f * k, 26f * ds * dynGlow, 18f * ds * dynGlow, 0.3f, 0.6f, 1f, 0.28f * a, 0f, 16)
    headingFrame(bx, by, ndx, ndy, k)
    // The stream it is made of, from the end of its shaft back
    val n = 7
    trailPts(fx(-23f, 0f), fy(-23f, 0f), ndx, ndy, 40f * ds, n, 3.5f * ds, 1.4f, phase * 1.8, 4.2f * k, 1.2f * k, a, 3f * ds)
    trailLayer(n, 1f, 2.4f, 0.85f)
    sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, n, 0.04f, 0.14f, 0.36f)
    trailLayer(n, 1f, 0f, 0.95f)
    sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, n, 0.28f, 0.6f, 0.98f)
    trailLayer(n, 0.35f, 0f, 0.9f)
    trailShift(n, 0.18f, ndx, ndy)
    sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, n, 0.85f, 0.97f, 1f)
    // The trident: inked as one, water, a lit streak down each prong
    inkAll(sb, TRI_PARTS, 2.4f, 0.04f, 0.14f, 0.36f, 0.92f * a)
    fillAll(sb, TRI_PARTS, 0.3f, 0.64f, 1f, 0.95f * a)
    gLine(sb, -3.6f, -0.4f, 6f, -0.3f, 0.9f, 0.85f, 0.97f, 1f, a)
    gLine(sb, -4.8f, -6.2f, 4.2f, -5.6f, 0.8f, 0.85f, 0.97f, 1f, 0.9f * a)
    gLine(sb, -4.8f, 5.6f, 4.2f, 5.2f, 0.8f, 0.6f, 0.85f, 1f, 0.9f * a)
    gLine(sb, -22f, -0.4f, -8f, -0.5f, 0.8f, 0.85f, 0.97f, 1f, 0.8f * a)
    // Foam on its points
    gDot(sb, 8.4f, 0f, 1.3f, 1.3f, 1f, 1f, 1f, a, 8)
    gDot(sb, 6f, -5f, 1.1f, 1.1f, 1f, 1f, 1f, a, 8)
    gDot(sb, 6f, 5f, 1.1f, 1.1f, 1f, 1f, 1f, a, 8)
    // Bubbles rising in the stream, drops falling off the tines
    var b = 0
    while (b < 3) {
      val t = loop(tick, 0.05, b / 3.0 + proj.id * 0.13)
      val lat = hash2(proj.id + 2, b) * 2.6f * ds
      val qx = fx(-24f - t * 30f, 0f) + px * lat; val qy = fy(-24f - t * 30f, 0f) + py * lat - t * 3f * ds
      sb.strokeOval(qx, qy, (1.2f + t) * ds, (1.2f + t) * ds, 1f, 0.9f, 0.98f, 1f, 0.85f * (1f - t) * a, 8)
      b += 1
    }
    var d = 0
    while (d < 3) {
      val t = loop(tick, 0.045, d / 3.0 + proj.id * 0.27)
      val lx = 2f - t * 16f; val ly = (d - 1) * 5.2f
      val qx = fx(lx, ly); val qy = fy(lx, ly) + t * t * 12f * ds
      val dr = (2f - t) * ds
      sb.fillOval(qx, qy, dr + 0.8f, dr * 1.3f + 0.8f, 0.04f, 0.14f, 0.36f, 0.7f * (1f - t) * a, 8)
      sb.fillOval(qx, qy, dr, dr * 1.3f, 0.55f, 0.85f, 1f, (1f - t) * a, 8)
      d += 1
    }
    drawChargeCrackle(sx, sy, 18f * ds, 0.3f, 0.62f, 1f, a, sb, phase, proj.chargeLevel)
  }

  // ── The Sandstorm ──
  private final val SB_N = 10
  private val _blade = new Array[Float](SB_N * 4)

  /**
   * The Sandstorm's sand shot: a crescent blade of packed sand — the curve of the scimitar the Sandstorm
   * carries — whirling flat over the ground like a thrown boomerang, the grit of it blurred round
   * behind its horns, grains flung off its edge and its dust trailing behind.
   */
  private[projectiles] def drawSandBlade(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 31) * 0.35
    computeAllDynamics(proj, 0.86f, 0.7f, 0.42f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx
    val k = 1.7f * ds
    val dir = if (ndx < 0f) -1f else 1f
    val spin = (phase * 1.3).toFloat * dir
    val cx = sx; val cy = sy - 3f * ds

    // Its dust trailing behind, and its shadow on the ground
    var p = 0
    while (p < 4) {
      val t = loop(tick, 0.04, p / 4.0 + proj.id * 0.21)
      val lat = hash2(proj.id + 7, p) * 7f * ds
      sb.fillOvalSoft(cx - ndx * (12f + t * 34f) * ds + px * lat, cy - ndy * (12f + t * 34f) * ds + py * lat + 2f * ds,
        (5f + 7f * t) * ds, (3.6f + 4f * t) * ds, 0.82f, 0.68f, 0.45f, 0.5f * (1f - t) * a, 0f, 12)
      p += 1
    }
    sb.fillOvalSoft(sx, sy + 3f * ds, 15f * k, 6f * k, 0.25f, 0.18f, 0.08f, 0.32f * a, 0f, 14)
    // The grit of it blurred round behind its horns: the arc each has just swept, fading
    var h = 0
    while (h < 2) {
      val horn = spin + (if (h == 0) 2f else -2f)
      sb.fillArcBand(cx, cy, 11f * k, 11f * k * 0.55f, 15f * k, 15f * k * 0.55f, horn, -dir * 1.6f, 8,
        0.86f, 0.72f, 0.45f, 0.5f * a, 0f)
      h += 1
    }
    // The blade: an outer arc round the middle and an inner one round a point behind it, meeting at its
    // horns; inked, sand, banded with darker grit, its edge lit
    frame(cx, cy, spin, k, 1f, 0.55f)
    var i = 0
    while (i < SB_N) {
      val u = -1f + 2f * i / (SB_N - 1)
      val phiO = u * 2f; val phiI = -u * 1.52f
      _blade(i * 2) = Math.cos(phiO).toFloat * 13f; _blade(i * 2 + 1) = Math.sin(phiO).toFloat * 13f
      _blade((SB_N + i) * 2) = -6f + Math.cos(phiI).toFloat * 11.83f; _blade((SB_N + i) * 2 + 1) = Math.sin(phiI).toFloat * 11.83f
      i += 1
    }
    val n = pts(_blade)
    sb.strokePolygon(_gx, _gy, n, 3.8f, 0.17f, 0.09f, 0.03f, 0.98f * a)
    sb.fillRibbon(_gx, _gy, n, 0.94f, 0.76f, 0.42f, a)
    // Darker bands of grit along it, its outer edge lit
    var bnd = 0
    while (bnd < 2) {
      val rr = 9.2f + bnd * 2f
      i = 0
      while (i < 7) {
        val u = -0.75f + 1.5f * i / 6f
        val ph = u * 1.9f
        _pvX(i) = fx(-2.5f + bnd * 1.2f + Math.cos(ph).toFloat * rr, Math.sin(ph).toFloat * rr)
        _pvY(i) = fy(-2.5f + bnd * 1.2f + Math.cos(ph).toFloat * rr, Math.sin(ph).toFloat * rr)
        _pvW(i) = 1.1f * k * (1f - Math.abs(u) * 0.6f); _pvA(i) = 0.9f * a
        i += 1
      }
      sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 7, 0.62f, 0.46f, 0.24f)
      bnd += 1
    }
    i = 0
    while (i < 8) {
      val u = -0.85f + 1.7f * i / 7f
      val ph = u * 2f
      _pvX(i) = fx(Math.cos(ph).toFloat * 12.2f, Math.sin(ph).toFloat * 12.2f)
      _pvY(i) = fy(Math.cos(ph).toFloat * 12.2f, Math.sin(ph).toFloat * 12.2f)
      _pvW(i) = 1.3f * k * (1f - Math.abs(u) * 0.7f); _pvA(i) = a
      i += 1
    }
    sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 8, 1f, 0.92f, 0.7f)
    // Grains flung off its edge
    var g = 0
    while (g < 6) {
      val t = loop(tick, 0.06, g / 6.0 + proj.id * 0.37)
      val ang = spin + g * 1.05f
      val rr = (13f + t * 12f) * k
      val gx = cx + Math.cos(ang).toFloat * rr - ndx * t * 14f * ds; val gy = cy + Math.sin(ang).toFloat * rr * 0.55f - ndy * t * 14f * ds
      val gs = (1.5f - t) * ds + 0.5f
      sb.fillRect(gx - gs, gy - gs, gs * 2f, gs * 2f, 0.55f, 0.4f, 0.2f, 0.9f * (1f - t) * a)
      g += 1
    }
  }

  // ── The Inferno ──
  /**
   * The Inferno's inferno blast: a wheel of fire rolling the way it goes — a rim of flame streaming fire
   * behind it, spokes of flame turning round the hub — with the Inferno's own scowling face in the hub,
   * as the wanyūdō's is, and embers flung off it.
   */
  private[projectiles] def drawFireWheel(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 31) * 0.35
    computeAllDynamics(proj, 1f, 0.5f, 0.1f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val dir = if (ndx < 0f) -1f else 1f
    val spin = (phase * 0.9).toFloat * dir
    val R = 13.5f * ds
    val cx = sx; val cy = sy - 4f * ds

    sb.fillOvalSoft(sx, sy + 4f * ds, 22f * ds, 8f * ds, 1f, 0.45f, 0.1f, 0.4f * a, 0f, 14)
    sb.fillOvalSoft(cx, cy, 30f * ds * dynGlow, 28f * ds * dynGlow, 1f, 0.5f, 0.1f, 0.3f * a, 0f, 16)
    // Fire streaming back off the rim
    var layer = 0
    while (layer < 3) {
      var f = 0
      while (f < 9) {
        val th = spin + f * (2 * Math.PI / 9)
        val ca = Math.cos(th).toFloat; val sa = Math.sin(th).toFloat
        val rx0 = cx + ca * R; val ry0 = cy + sa * R * 0.96f
        // Back from where it rolls, up as flames go, and a little against its turning
        var dx0 = -ndx * 0.8f + sa * dir * 0.35f; var dy0 = -ndy * 0.8f - ca * dir * 0.35f - 0.45f
        val dl = Math.sqrt(dx0 * dx0 + dy0 * dy0).toFloat
        dx0 /= dl; dy0 /= dl
        val flick = 0.75f + 0.25f * Math.sin(phase * 3.6 + f * 2.3).toFloat
        val kk = if (layer == 0) 1f else if (layer == 1) 0.85f else 0.5f
        flameTongue(rx0, ry0, dx0, dy0, 22f * ds * flick * kk, (if (layer == 0) 7f * ds + 2.4f else 7f * ds) * kk,
          Math.sin(phase * 2.6 + f).toFloat * 3f * ds, a)
        (layer: @scala.annotation.switch) match {
          case 0 => sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 5, 0.32f, 0.06f, 0.02f)
          case 1 => sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 5, 1f, 0.42f, 0.08f)
          case _ => sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 5, 1f, 0.82f, 0.3f)
        }
        f += 1
      }
      layer += 1
    }
    // The rim: a ring of fire
    sb.strokeOval(cx, cy, R, R * 0.96f, 6.4f * ds + 2.6f, 0.32f, 0.06f, 0.02f, 0.92f * a, 26)
    sb.strokeOval(cx, cy, R, R * 0.96f, 6.4f * ds, 1f, 0.4f, 0.08f, a, 26)
    sb.strokeOval(cx, cy, R, R * 0.96f, 3.4f * ds, 1f, 0.72f, 0.2f, a, 26)
    sb.strokeOval(cx, cy, R, R * 0.96f, 1.3f * ds, 1f, 0.95f, 0.7f, a, 26)
    // Spokes of flame turning round the hub
    var sp = 0
    while (sp < 6) {
      val th = spin + sp * (Math.PI / 3)
      var i = 0
      while (i < 3) {
        val u = i / 2f
        val ang = th + u * 0.35 * dir
        val rr = 5f * ds + (R - 6.5f * ds) * u
        _pvX(i) = cx + Math.cos(ang).toFloat * rr; _pvY(i) = cy + Math.sin(ang).toFloat * rr * 0.96f
        i += 1
      }
      sb.strokePolylineTapered(_pvX, _pvY, 3, 5.6f * ds + 2.4f, 3.2f * ds + 2.4f, 0.32f, 0.06f, 0.02f, 0.9f * a, 0.9f * a)
      sb.strokePolylineTapered(_pvX, _pvY, 3, 5.6f * ds, 3.2f * ds, 1f, 0.6f, 0.12f, a, a)
      sb.strokePolylineTapered(_pvX, _pvY, 3, 1.8f * ds, 1f * ds, 1f, 0.92f, 0.55f, a, a)
      sp += 1
    }
    // The hub, and the Inferno's own face in it, scowling, upright as the wheel turns
    val hr = 6.4f * ds
    sb.fillOval(cx, cy, hr + 2f, hr + 2f, 0.32f, 0.06f, 0.02f, 0.94f * a, 18)
    sb.fillOval(cx, cy, hr, hr, 1f, 0.5f, 0.1f, a, 18)
    sb.fillOvalSoft(cx, cy + hr * 0.1f, hr * 0.8f, hr * 0.75f, 1f, 0.88f, 0.45f, a, 0.5f * a, 14)
    frame(cx, cy, 0f, hr / 10f)
    val m = if (dir < 0f) -1f else 1f
    var e = -1
    while (e <= 1) {
      val ex = e * 3.6f + m * 0.6f
      _gx(0) = fx(ex - e * 3.4f, -1.2f); _gy(0) = fy(ex - e * 3.4f, -1.2f)
      _gx(1) = fx(ex + e * 3f, -5.4f); _gy(1) = fy(ex + e * 3f, -5.4f)
      _gx(2) = fx(ex + e * 3f, -2.6f); _gy(2) = fy(ex + e * 3f, -2.6f)
      sb.fillPolygon(_gx, _gy, 3, 0.36f, 0.04f, 0.02f, a)
      gDot(sb, ex + e * 0.6f, -0.9f, 1f, 0.8f, 1f, 0.97f, 0.7f, a, 6)
      e += 2
    }
    // A grimace, its fangs bared
    _gx(0) = fx(-4.4f + m * 0.6f, 2.2f); _gy(0) = fy(-4.4f + m * 0.6f, 2.2f)
    _gx(1) = fx(4.4f + m * 0.6f, 2.2f); _gy(1) = fy(4.4f + m * 0.6f, 2.2f)
    _gx(2) = fx(3f + m * 0.6f, 5.6f); _gy(2) = fy(3f + m * 0.6f, 5.6f)
    _gx(3) = fx(-3f + m * 0.6f, 5.6f); _gy(3) = fy(-3f + m * 0.6f, 5.6f)
    sb.fillPolygon(_gx, _gy, 4, 0.36f, 0.04f, 0.02f, a)
    var fang = 0
    while (fang < 3) {
      val fxp = -2.6f + fang * 2.6f + m * 0.6f
      _gx(0) = fx(fxp - 1f, 2.3f); _gy(0) = fy(fxp - 1f, 2.3f)
      _gx(1) = fx(fxp + 1f, 2.3f); _gy(1) = fy(fxp + 1f, 2.3f)
      _gx(2) = fx(fxp, 4.4f); _gy(2) = fy(fxp, 4.4f)
      sb.fillPolygon(_gx, _gy, 3, 1f, 0.97f, 0.85f, a)
      fang += 1
    }
    // Embers flung off it
    var em = 0
    while (em < 5) {
      val t = loop(tick, 0.05, em / 5.0 + proj.id * 0.29)
      val ang = spin + em * 1.26f
      val rr = R + t * 18f * ds
      sb.fillOval(cx + Math.cos(ang).toFloat * rr - ndx * t * 16f * ds, cy + Math.sin(ang).toFloat * rr - ndy * t * 16f * ds - t * 8f * ds,
        (2f - t) * ds + 0.4f, (2f - t) * ds + 0.4f, 1f, mix(0.9f, 0.4f, t), 0.15f, (1f - t) * a, 6)
      em += 1
    }
  }
}
