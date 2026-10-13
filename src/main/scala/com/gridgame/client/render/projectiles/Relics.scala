package com.gridgame.client.render.projectiles

import com.gridgame.common.model.Projectile
import com.gridgame.client.gl.ShapeBatch
import ProjectileKit._
import Glyphs._

/**
 * Things with a shape of their own, drawn as whoever threw them: the Mummy's scarab and the gold death
 * mask of its curse; Anubis's ankh and the head of his jackal; the Yokai's foxfire and the paper
 * talismans of her curse; the Djinn's lamp pouring out its smoke; the Runesmith's runestones; the
 * Alchemist's flasks. They were orbs (a sand one, two purple ones, two pink ones, a blue one), a purple
 * curse ball for all three curses, and the venom blob for a flask.
 */
private[render] object Relics {
  private val GOLD_R = 0.95f; private val GOLD_G = 0.77f; private val GOLD_B = 0.3f
  private val LAPIS_R = 0.13f; private val LAPIS_G = 0.27f; private val LAPIS_B = 0.68f

  // ── The Mummy ──
  private val SCARAB_HEAD = Array(6.4f, -2.6f, 8.6f, -3.2f, 10.2f, -1.6f, 10.6f, 0f, 10.2f, 1.6f, 8.6f, 3.2f, 6.4f, 2.6f)
  private val _legX = Array(3f, -1f, -5f)

  /**
   * The Mummy's sand shot: a sacred scarab, head first — its wing cases lapis and teal rimmed in gold,
   * its wings a buzzing blur either side, its legs tucked — with tomb sand streaming off it.
   */
  private[projectiles] def drawScarab(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 37) * 0.35
    computeAllDynamics(proj, 0.2f, 0.55f, 0.6f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx
    val k = 1.5f * ds

    // Tomb sand streaming off it
    var g = 0
    while (g < 7) {
      val t = loop(tick, 0.05, g / 7.0 + proj.id * 0.31)
      val lat = hash2(proj.id, g) * (4f + 9f * t) * ds
      val gx = sx - ndx * (8f + t * 40f) * ds + px * lat; val gy = sy - ndy * (8f + t * 40f) * ds + py * lat + t * t * 6f * ds
      val gs = (1.6f - t) * ds + 0.5f
      sb.fillRect(gx - gs, gy - gs, gs * 2f, gs * 2f, 0.62f, 0.5f, 0.3f, 0.9f * (1f - t) * a)
      g += 1
    }
    sb.fillOvalSoft(sx - ndx * 4f * k, sy - ndy * 4f * k, 22f * ds * dynGlow, 17f * ds * dynGlow, 0.9f, 0.75f, 0.35f, 0.22f * a, 0f, 14)
    headingFrame(sx - ndx * 10.5f * k, sy - ndy * 10.5f * k, ndx, ndy, k)
    // Its wings, a buzzing blur either side
    val buzz = if (((tick + proj.id) & 1) == 0) 1f else 0.82f
    var side = -1f
    while (side <= 1f) {
      sb.fillOvalSoft(fx(-3f, side * 8.6f), fy(-3f, side * 8.6f), 8f * k * buzz, 5f * k * buzz, 0.85f, 0.92f, 1f, 0.5f * a, 0.1f * a, 14)
      side += 2f
    }
    // Legs, tucked under it
    var pass = 0
    while (pass < 2) {
      var l = 0
      while (l < 3) {
        side = -1f
        while (side <= 1f) {
          val lx = _legX(l)
          val kx = lx - 1.5f; val ky = side * 6.6f
          val tx = kx - 2.5f - l * 0.6f; val ty = side * 7.2f
          val w = if (pass == 0) 2.4f else 1f
          val c = if (pass == 0) 0.04f else 0.25f
          gLine(sb, lx, side * 3.6f, kx, ky, w, c, c * 1.2f, c, a)
          gLine(sb, kx, ky, tx, ty, w * 0.9f, c, c * 1.2f, c, a)
          side += 2f
        }
        l += 1
      }
      pass += 1
    }
    // The body: its head, its thorax shield and its wing cases, inked as one
    var n = ovalPts(-3f, 0f, 7f, 5.6f, 0f, 18)
    sb.strokePolygon(_gx, _gy, n, 2.6f, 0.04f, 0.06f, 0.05f, 0.92f * a)
    n = ovalPts(4f, 0f, 3.8f, 4.6f, 0f, 14)
    sb.strokePolygon(_gx, _gy, n, 2.6f, 0.04f, 0.06f, 0.05f, 0.92f * a)
    n = pts(SCARAB_HEAD)
    sb.strokePolygon(_gx, _gy, n, 2.4f, 0.04f, 0.06f, 0.05f, 0.92f * a)
    sb.fillPolygon(_gx, _gy, n, 0.12f, 0.2f, 0.2f, a)
    gLine(sb, 9.6f, -1.2f, 11.4f, -1.6f, 0.9f, GOLD_R, GOLD_G, GOLD_B, a)
    gLine(sb, 9.8f, 0f, 11.8f, 0f, 0.9f, GOLD_R, GOLD_G, GOLD_B, a)
    gLine(sb, 9.6f, 1.2f, 11.4f, 1.6f, 0.9f, GOLD_R, GOLD_G, GOLD_B, a)
    n = ovalPts(-3f, 0f, 7f, 5.6f, 0f, 18)
    sb.fillPolygon(_gx, _gy, n, 0.1f, 0.36f, 0.46f, a)
    sb.strokePolygon(_gx, _gy, n, 1f * k, GOLD_R, GOLD_G, GOLD_B, a)
    n = ovalPts(4f, 0f, 3.8f, 4.6f, 0f, 14)
    sb.fillPolygon(_gx, _gy, n, LAPIS_R, LAPIS_G, LAPIS_B, a)
    sb.strokePolygon(_gx, _gy, n, 0.9f * k, GOLD_R, GOLD_G, GOLD_B, a)
    // The seam between its wing cases, and their sheen
    gLine(sb, -9.6f, 0f, 0.2f, 0f, 1f, 0.04f, 0.1f, 0.1f, a)
    sb.fillOvalSoft(fx(-3.5f, -2.4f), fy(-3.5f, -2.4f), 4f * k, 2.2f * k, 0.55f, 0.95f, 0.9f, 0.7f * a, 0f, 10)
    sb.fillOvalSoft(fx(-3.5f, 2.4f), fy(-3.5f, 2.4f), 4f * k, 2.2f * k, 0.35f, 0.75f, 0.85f, 0.5f * a, 0f, 10)
  }

  private val NEMES_TOP = Array(-10.4f, -8.6f, -8.8f, -15f, -4f, -17.6f, 4f, -17.6f, 8.8f, -15f, 10.4f, -8.6f)
  private val LAPPET_L = Array(-10.6f, -9f, -7f, -4f, -7.6f, 13f, -15.6f, 13f)
  private val LAPPET_R = Array(10.6f, -9f, 15.6f, 13f, 7.6f, 13f, 7f, -4f)
  private val BEARD = Array(-1.7f, 7.6f, 1.7f, 7.6f, 1.3f, 15f, -1.3f, 15f)
  private val _band = new Array[Float](8)

  /** A lapis band across a lappet (`side` ±1) from y0 to y1, its ends on the lappet's edges. */
  private def lappetBand(side: Float, y0: Float, y1: Float): Unit = {
    // Outer edge from (-10.6, -9) to (-15.6, 13); inner from (-7, -4) to (-7.6, 13)
    @inline def outer(y: Float): Float = side * (10.6f + 5f * (y + 9f) / 22f)
    @inline def inner(y: Float): Float = side * (7f + 0.6f * Math.max(0f, y + 4f) / 17f)
    _band(0) = outer(y0); _band(1) = y0
    _band(2) = inner(y0); _band(3) = y0
    _band(4) = inner(y1); _band(5) = y1
    _band(6) = outer(y1); _band(7) = y1
  }

  /**
   * The Mummy's curse: a pharaoh's death mask, gold and lapis — the striped nemes falling either side of
   * the face, kohl-lined eyes burning with the curse's green fire, the cobra on the brow, the plaited
   * beard — with the green fire streaming back from its eyes and a haze of it round it.
   */
  private[projectiles] def drawDeathMask(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 41) * 0.35
    computeAllDynamics(proj, 0.4f, 1f, 0.5f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val cx = sx; val cy = sy + Math.sin(phase * 0.45).toFloat * 1.5f * ds - 4f * ds
    val k = 1.25f * ds

    sb.fillOvalSoft(cx, cy, 30f * ds * dynGlow, 32f * ds * dynGlow, 0.35f, 1f, 0.45f, 0.26f * a, 0f, 16)
    frame(cx, cy, Math.sin(phase * 0.3).toFloat * 0.07f, k)
    // The curse's fire, streaming back from its eyes
    var e = -1
    while (e <= 1) {
      val n = 6
      trailPts(fx(e * 3.3f, -2.4f), fy(e * 3.3f, -2.4f), ndx, ndy, 34f * ds, n, 3f * ds, 1.2f, phase * 1.6 + e,
        5f * ds, 1.4f * ds, 0.9f * a, -6f * ds)
      trailLayer(n, 1f, 0f, 0.7f)
      sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, n, 0.3f, 0.95f, 0.4f)
      trailLayer(n, 0.4f, 0f, 1f)
      sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, n, 0.8f, 1f, 0.75f)
      e += 2
    }
    val ir = 0.16f; val ig = 0.1f; val ib = 0.03f
    // The nemes: its crown and lappets inked as one, gold, banded in lapis
    var n = pts(NEMES_TOP); sb.strokePolygon(_gx, _gy, n, 2.8f, ir, ig, ib, 0.92f * a)
    n = pts(LAPPET_L); sb.strokePolygon(_gx, _gy, n, 2.8f, ir, ig, ib, 0.92f * a)
    n = pts(LAPPET_R); sb.strokePolygon(_gx, _gy, n, 2.8f, ir, ig, ib, 0.92f * a)
    n = pts(NEMES_TOP); sb.fillPolygon(_gx, _gy, n, GOLD_R, GOLD_G, GOLD_B, a)
    n = pts(LAPPET_L); sb.fillPolygon(_gx, _gy, n, GOLD_R, GOLD_G, GOLD_B, a)
    n = pts(LAPPET_R); sb.fillPolygon(_gx, _gy, n, GOLD_R * 0.86f, GOLD_G * 0.86f, GOLD_B * 0.86f, a)
    var b = 0
    while (b < 4) {
      val y0 = -2.6f + b * 4f
      var side = -1f
      while (side <= 1f) {
        lappetBand(side, y0, y0 + 1.9f)
        val m = pts(_band)
        sb.fillPolygon(_gx, _gy, m, LAPIS_R, LAPIS_G, LAPIS_B, a)
        side += 2f
      }
      b += 1
    }
    gLine(sb, -7.6f, -14.4f, 7.6f, -14.4f, 1.6f, LAPIS_R, LAPIS_G, LAPIS_B, a)
    gLine(sb, -9.6f, -11.2f, 9.6f, -11.2f, 1.6f, LAPIS_R, LAPIS_G, LAPIS_B, a)
    // The beard
    n = pts(BEARD)
    sb.strokePolygon(_gx, _gy, n, 2.2f, ir, ig, ib, 0.92f * a)
    sb.fillPolygon(_gx, _gy, n, LAPIS_R, LAPIS_G, LAPIS_B, a)
    gLine(sb, -1.4f, 10.2f, 1.4f, 10.2f, 0.8f, GOLD_R, GOLD_G, GOLD_B, a)
    gLine(sb, -1.3f, 12.8f, 1.3f, 12.8f, 0.8f, GOLD_R, GOLD_G, GOLD_B, a)
    // The face: gold, shaded down its far side and under its brow, lit on the cheek
    n = ovalPts(0f, -1f, 7.4f, 9f, 0f, 18)
    sb.strokePolygon(_gx, _gy, n, 2.4f, ir, ig, ib, 0.92f * a)
    sb.fillPolygon(_gx, _gy, n, 0.98f, 0.82f, 0.38f, a)
    sb.fillOvalSoft(fx(4f, 1f), fy(4f, 1f), 4f * k, 7.5f * k, 0.78f, 0.56f, 0.18f, 0.55f * a, 0f, 12)
    gDot(sb, -3.6f, 3.4f, 2.2f, 1.6f, 1f, 0.95f, 0.7f, 0.7f * a, 8)
    // The cobra on its brow
    n = ovalPts(0f, -9.6f, 1.6f, 2.6f, 0f, 10)
    sb.strokePolygon(_gx, _gy, n, 1.6f, ir, ig, ib, 0.9f * a)
    sb.fillPolygon(_gx, _gy, n, GOLD_R, GOLD_G, GOLD_B, a)
    gDot(sb, 0f, -9.4f, 0.7f, 0.7f, 0.85f, 0.15f, 0.15f, a, 6)
    // Brows and kohl-lined eyes, burning green
    e = -1
    while (e <= 1) {
      gLine(sb, e * 1.4f, -5.4f, e * 5.6f, -5f, 1.1f, LAPIS_R, LAPIS_G, LAPIS_B, a)
      n = ovalPts(e * 3.3f, -2.4f, 2.5f, 1.2f, 0f, 12)
      sb.fillPolygon(_gx, _gy, n, 0.06f, 0.04f, 0.04f, a)
      gLine(sb, e * 5.6f, -2.4f, e * 7.8f, -1.4f, 0.9f, 0.06f, 0.04f, 0.04f, a)
      sb.fillOvalSoft(fx(e * 3.3f, -2.4f), fy(e * 3.3f, -2.4f), 4f * k, 3f * k, 0.35f, 1f, 0.45f, 0.75f * a, 0f, 10)
      gDot(sb, e * 3.3f, -2.4f, 1f, 0.9f, 0.8f, 1f, 0.7f, a, 6)
      e += 2
    }
    // Nose and mouth
    gLine(sb, 0f, -1.4f, 0.6f, 2.4f, 0.8f, 0.6f, 0.42f, 0.12f, a)
    gLine(sb, -2.2f, 4.8f, 2.2f, 4.8f, 0.9f, 0.45f, 0.25f, 0.08f, a)
  }

  // ── Anubis ──
  /**
   * Anubis's death bolt: a golden ankh, inlaid with lapis, turning as it flies so its gold catches the
   * light, with gold dust falling from it.
   */
  private[projectiles] def drawAnkh(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 29) * 0.35
    computeAllDynamics(proj, 0.95f, 0.77f, 0.3f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx
    val k = 1.35f * ds
    // Turning about its own upright: its width the cosine of the turn, its back a darker gold
    val turn = phase * 0.55
    val ct = Math.cos(turn).toFloat
    val wx = 0.3f + 0.7f * Math.abs(ct)
    val back = ct < 0f
    val gr = if (back) 0.78f else GOLD_R; val gg = if (back) 0.58f else GOLD_G; val gb = if (back) 0.2f else GOLD_B

    // Gold dust falling behind it, a glint now and then
    var g = 0
    while (g < 6) {
      val t = loop(tick, 0.045, g / 6.0 + proj.id * 0.23)
      val lat = hash2(proj.id + 4, g) * (3f + 8f * t) * ds
      val dx0 = sx - ndx * (6f + t * 36f) * ds + px * lat; val dy0 = sy - ndy * (6f + t * 36f) * ds + py * lat + t * t * 8f * ds
      val gs = (1.8f - t) * ds + 0.4f
      sb.fillOval(dx0, dy0, gs, gs, 1f, 0.85f, 0.4f, 0.95f * (1f - t) * a, 6)
      g += 1
    }
    fadeLine(sb, sx, sy, sx - ndx * 34f * ds, sy - ndy * 34f * ds, 9f * ds, 0.95f, 0.78f, 0.3f, 0.35f * a, 4)
    sb.fillOvalSoft(sx, sy, 22f * ds * dynGlow, 24f * ds * dynGlow, 1f, 0.8f, 0.35f, 0.3f * a, 0f, 16)
    frame(sx, sy, Math.sin(phase * 0.5).toFloat * 0.18f, k)
    val ir = 0.18f; val ig = 0.1f; val ib = 0.02f
    // Its loop: ink, gold, a lapis line round inside it
    var n = ovalPts(0f, -7.2f, 4.1f * wx, 5.2f, 0f, 18)
    sb.strokePolygon(_gx, _gy, n, 2.9f * k + 2.6f, ir, ig, ib, 0.92f * a)
    // The crossbar's halves and the shaft
    _bar(0) = -9.2f * wx; _bar(1) = -2.6f; _bar(2) = 0f; _bar(3) = -1.3f; _bar(4) = 0f; _bar(5) = 1.3f; _bar(6) = -9.2f * wx; _bar(7) = 2.6f
    _barR(0) = 9.2f * wx; _barR(1) = -2.6f; _barR(2) = 9.2f * wx; _barR(3) = 2.6f; _barR(4) = 0f; _barR(5) = 1.3f; _barR(6) = 0f; _barR(7) = -1.3f
    _shaft(0) = -1.5f * wx; _shaft(1) = 0.6f; _shaft(2) = 1.5f * wx; _shaft(3) = 0.6f
    _shaft(4) = 3.4f * wx; _shaft(5) = 14f; _shaft(6) = -3.4f * wx; _shaft(7) = 14f
    var m = pts(_bar); sb.strokePolygon(_gx, _gy, m, 2.6f, ir, ig, ib, 0.92f * a)
    m = pts(_barR); sb.strokePolygon(_gx, _gy, m, 2.6f, ir, ig, ib, 0.92f * a)
    m = pts(_shaft); sb.strokePolygon(_gx, _gy, m, 2.6f, ir, ig, ib, 0.92f * a)
    n = ovalPts(0f, -7.2f, 4.1f * wx, 5.2f, 0f, 18)
    sb.strokePolygon(_gx, _gy, n, 2.9f * k, gr, gg, gb, a)
    m = pts(_bar); sb.fillPolygon(_gx, _gy, m, gr, gg, gb, a)
    m = pts(_barR); sb.fillPolygon(_gx, _gy, m, gr * 0.88f, gg * 0.88f, gb * 0.88f, a)
    m = pts(_shaft); sb.fillPolygon(_gx, _gy, m, gr, gg, gb, a)
    if (!back) {
      // Lapis inlaid down its shaft, along its arms and round its loop
      n = ovalPts(0f, -7.2f, 4.1f * wx, 5.2f, 0f, 18)
      sb.strokePolygon(_gx, _gy, n, 0.8f * k, LAPIS_R, LAPIS_G, LAPIS_B, a)
      gLine(sb, -7.6f * wx, 0f, 7.6f * wx, 0f, 0.9f, LAPIS_R, LAPIS_G, LAPIS_B, a)
      gLine(sb, 0f, 2f, 0f, 12.6f, 1.1f, LAPIS_R, LAPIS_G, LAPIS_B, a)
      gDot(sb, -1.6f * wx, -10.4f, 1.2f, 0.8f, 1f, 1f, 0.9f, 0.85f * a, 6)
    }
    val gl = loop(tick, 0.02, proj.id * 0.41)
    if (gl < 0.25f) sb.fillStarFlare(fx(-5f * wx, -1.6f), fy(-5f * wx, -1.6f), 9f * ds * Math.sin(gl / 0.25f * Math.PI).toFloat,
      1.6f, 0f, 0.8f, 1f, 1f, 0.9f, a)
  }
  private val _bar = new Array[Float](8)
  private val _barR = new Array[Float](8)
  private val _shaft = new Array[Float](8)

  private val JACKAL_SNOUT = Array(1.6f, -3.4f, 12.6f, -1.4f, 14f, 0.2f, 12.6f, 1.8f, 1.6f, 3.6f)
  private val JACKAL_EAR = Array(-5.2f, -3.8f, -1.2f, -5.6f, -6f, -17f)
  private val JACKAL_EAR_FAR = Array(-8f, -3.6f, -4.6f, -5f, -10.2f, -15.6f)
  private val JACKAL_NECK = Array(-7.6f, -0.6f, 0.6f, 2.4f, -1.4f, 9.6f, -8.8f, 8.6f)
  private val JACKAL_PARTS = Array(JACKAL_SNOUT, JACKAL_EAR, JACKAL_NECK)

  /**
   * Anubis's curse: the head of his jackal, black and rimmed in gold, ears laid back, a gold eye
   * burning, his collar of gold and lapis at its throat — lunging the way it flies with dark smoke and
   * gold sparks streaming from it.
   */
  private[projectiles] def drawJackalSpirit(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 43) * 0.35
    computeAllDynamics(proj, 0.95f, 0.75f, 0.3f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx
    val k = 1.35f * ds

    // Smoke and sparks streaming from it
    var w = 0
    while (w < 3) {
      val n = 6
      trailPts(sx - ndx * 8f * k + px * (w - 1) * 5f * ds, sy - ndy * 8f * k + py * (w - 1) * 5f * ds + 4f * ds,
        ndx, ndy, (30f + 10f * hash01(proj.id, w)) * ds, n, 3.5f * ds, 1.1f, phase * 1.5 + w * 2.1,
        10f * ds, 2f * ds, 0.75f * a, -3f * ds)
      trailLayer(n, 1f, 0f, 1f)
      sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, n, 0.05f, 0.04f, 0.1f)
      w += 1
    }
    var s = 0
    while (s < 4) {
      val t = loop(tick, 0.05, s / 4.0 + proj.id * 0.27)
      val lat = hash2(proj.id + 2, s) * (5f + 8f * t) * ds
      val gx = sx - ndx * (10f + t * 34f) * ds + px * lat; val gy = sy - ndy * (10f + t * 34f) * ds + py * lat - t * 6f * ds
      sb.fillOval(gx, gy, (1.8f - t) * ds + 0.4f, (1.8f - t) * ds + 0.4f, 1f, 0.8f, 0.35f, (1f - t) * a, 6)
      s += 1
    }
    sb.fillOvalSoft(sx, sy, 24f * ds * dynGlow, 20f * ds * dynGlow, 1f, 0.75f, 0.3f, 0.22f * a, 0f, 16)
    faceFrame(sx - ndx * 6f * k, sy - ndy * 6f * k, ndx, ndy, k, 0.4f)
    val gr = 0.92f; val gg = 0.72f; val gb = 0.28f
    // The far ear, darker, then the head inked in gold as one
    var n = pts(JACKAL_EAR_FAR)
    sb.strokePolygon(_gx, _gy, n, 2.2f, gr, gg, gb, 0.9f * a)
    sb.fillPolygon(_gx, _gy, n, 0.04f, 0.04f, 0.07f, a)
    n = ovalPts(-2f, -1f, 6.8f, 5.8f, 0f, 16)
    sb.strokePolygon(_gx, _gy, n, 2.4f, gr, gg, gb, 0.95f * a)
    inkAll(sb, JACKAL_PARTS, 2.4f, gr, gg, gb, 0.95f * a)
    fillAll(sb, JACKAL_PARTS, 0.08f, 0.08f, 0.13f, a)
    n = ovalPts(-2f, -1f, 6.8f, 5.8f, 0f, 16)
    sb.fillPolygon(_gx, _gy, n, 0.08f, 0.08f, 0.13f, a)
    // A blue sheen along the top of the head and snout, gold in the ear
    gLine(sb, -6f, -5f, 1f, -5.4f, 1.6f, 0.26f, 0.28f, 0.45f, 0.9f * a)
    gLine(sb, 2f, -3f, 12f, -1.2f, 1.2f, 0.26f, 0.28f, 0.45f, 0.9f * a)
    _gx(0) = fx(-4.6f, -5f); _gy(0) = fy(-4.6f, -5f)
    _gx(1) = fx(-2.4f, -5.6f); _gy(1) = fy(-2.4f, -5.6f)
    _gx(2) = fx(-5.4f, -13.6f); _gy(2) = fy(-5.4f, -13.6f)
    sb.fillPolygon(_gx, _gy, 3, 0.6f, 0.42f, 0.16f, a)
    // His collar: bands of gold and lapis round the neck
    var c = 0
    while (c < 3) {
      val y = 4.4f + c * 1.8f
      gLine(sb, -8.4f + c * 0.25f, y, -0.6f - c * 0.4f, y + 0.8f, 1.5f, if ((c & 1) == 0) gr else LAPIS_R,
        if ((c & 1) == 0) gg else LAPIS_G, if ((c & 1) == 0) gb else LAPIS_B, a)
      c += 1
    }
    // The eye, burning gold, its kohl line running back; the nose; the mouth
    n = ovalPts(3.4f, -1.8f, 2f, 1f, -0.12f, 10)
    sb.fillPolygon(_gx, _gy, n, 1f, 0.82f, 0.3f, a)
    sb.fillOvalSoft(fx(3.4f, -1.8f), fy(3.4f, -1.8f), 4f * k, 3f * k, 1f, 0.8f, 0.3f, 0.6f * a, 0f, 10)
    gDot(sb, 4f, -1.8f, 0.6f, 0.8f, 0.05f, 0.03f, 0.02f, a, 6)
    gLine(sb, 1.4f, -1.6f, -2.6f, -1f, 0.8f, gr, gg, gb, a)
    gDot(sb, 13.5f, 0.1f, 1.1f, 0.9f, 0.02f, 0.02f, 0.03f, a, 6)
    gLine(sb, 12.8f, 1.3f, 6f, 2.2f, 0.7f, gr, gg, gb, 0.8f * a)
  }

  // ── The Yokai ──
  /** A teardrop of foxfire at (x, y), `r` across its head, its tail along the unit (dx, dy): ink, blue,
    * cyan, white. */
  private def foxfire(sb: ShapeBatch, x: Float, y: Float, r: Float, dx: Float, dy: Float, len: Float,
                      phase: Double, a: Float): Unit = {
    var layer = 0
    while (layer < 4) {
      val kk = if (layer == 0) 1f else if (layer == 1) 0.86f else if (layer == 2) 0.6f else 0.32f
      val qx = -dy; val qy = dx
      var i = 0
      while (i < 5) {
        val t = i / 4f
        val sw = Math.sin(phase * 2.4 - t * 3.4).toFloat * r * 0.5f * t
        _pvX(i) = x + dx * len * kk * t + qx * sw; _pvY(i) = y + dy * len * kk * t + qy * sw
        _pvW(i) = (2f * r * kk + (if (layer == 0) 2.6f else 0f)) * (1f - t * 0.92f) + 0.3f
        _pvA(i) = a * (1f - t * t * 0.5f)
        i += 1
      }
      (layer: @scala.annotation.switch) match {
        case 0 => sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 5, 0.03f, 0.07f, 0.3f)
        case 1 => sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 5, 0.22f, 0.45f, 1f)
        case 2 => sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 5, 0.45f, 0.85f, 1f)
        case _ => sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 5, 0.92f, 1f, 1f)
      }
      layer += 1
    }
  }

  /**
   * The Yokai's mystic bolt: kitsune-bi, the foxfire she carries in her hands — a teardrop of blue flame
   * curling back on itself, two lesser wisps of it circling it — leaving blue sparks behind.
   */
  private[projectiles] def drawFoxfire(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 31) * 0.35
    computeAllDynamics(proj, 0.3f, 0.55f, 1f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val tdx0 = -ndx * 0.8f; val tdy0 = -ndy * 0.8f - 0.6f
    val tl = Math.sqrt(tdx0 * tdx0 + tdy0 * tdy0).toFloat
    val tdx = tdx0 / tl; val tdy = tdy0 / tl

    sb.fillOvalSoft(sx, sy, 26f * ds * dynGlow, 24f * ds * dynGlow, 0.25f, 0.5f, 1f, 0.32f * a, 0f, 16)
    Orbs.shedMotes(sb, sx, sy, ndx, ndy, 12f * ds, 4, 0.6f, 0.85f, 1f, a, tick, proj.id)
    // The lesser wisps circling it: those behind it first
    var pass = 0
    while (pass < 2) {
      var w = 0
      while (w < 2) {
        val ang = phase * 0.9 + w * Math.PI
        val behind = Math.sin(ang) < 0
        if (behind == (pass == 0)) {
          val wx = sx + Math.cos(ang).toFloat * 18.5f * ds; val wy = sy + Math.sin(ang).toFloat * 7f * ds - 2f * ds
          foxfire(sb, wx, wy, 2.7f * ds, tdx, tdy, 10f * ds, phase + w * 2.0, a * (if (behind) 0.8f else 1f))
        }
        w += 1
      }
      if (pass == 0) {
        // The flame itself: its tail curling back and up, its head round
        foxfire(sb, sx, sy, 8f * ds, tdx, tdy, 30f * ds, phase, a)
        sb.fillOval(sx, sy, 8.6f * ds + 1.6f, 8.2f * ds + 1.6f, 0.03f, 0.07f, 0.3f, 0.9f * a, 18)
        sb.fillOval(sx, sy, 8.6f * ds, 8.2f * ds, 0.22f, 0.45f, 1f, a, 18)
        sb.fillOvalSoft(sx + tdx * 1.5f * ds, sy + tdy * 1.5f * ds, 6.4f * ds, 6f * ds, 0.5f, 0.88f, 1f, a, 0.4f * a, 16)
        val hot = 0.9f + 0.1f * Math.sin(phase * 1.8).toFloat
        sb.fillOvalSoft(sx, sy + 0.8f * ds, 3.8f * ds * hot, 3.6f * ds * hot, 1f, 1f, 1f, a, 0.5f * a, 12)
      }
      pass += 1
    }
  }

  private final val OFUDA_ROWS = 6
  private val _ofL = new Array[Float](OFUDA_ROWS * 2)
  private val _ofR = new Array[Float](OFUDA_ROWS * 2)
  private var _ofPhase = 0.0

  /** The talisman's flutter: how far across a point `y` down it has been blown. */
  @inline private def flutter(y: Float): Float =
    Math.sin(_ofPhase * 2.2 + y * 0.32).toFloat * 1.8f * ((y + 11f) / 22f)

  /** A brush stroke on the talisman, from (x0, y0) to (x1, y1), following its flutter. */
  private def brush(sb: ShapeBatch, x0: Float, y0: Float, x1: Float, y1: Float, w: Float,
                    r: Float, g: Float, b: Float, a: Float): Unit = {
    val ax = x0 + flutter(y0); val bx = x1 + flutter(y1)
    sb.strokeLine(fx(ax, y0), fy(ax, y0), fx(bx, y1), fy(bx, y1), w * _fk, r, g, b, a)
  }

  /**
   * The Yokai's curse: an ofuda, a paper talisman, red-bordered and brushed with a charm in black and a
   * red seal, fluttering as it flies, with blue foxfire sparks in its wake.
   */
  private[projectiles] def drawOfuda(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 37) * 0.35
    computeAllDynamics(proj, 0.95f, 0.3f, 0.3f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val k = 1.3f * ds
    _ofPhase = phase

    // Foxfire sparks in its wake
    var s = 0
    while (s < 5) {
      val t = loop(tick, 0.045, s / 5.0 + proj.id * 0.21)
      val lat = hash2(proj.id + 6, s) * (4f + 8f * t) * ds
      val gx = sx - ndx * (8f + t * 32f) * ds - ndy * lat; val gy = sy - ndy * (8f + t * 32f) * ds + ndx * lat - t * 5f * ds
      val gs = (2.2f - 1.4f * t) * ds
      sb.fillOval(gx, gy, gs + 0.8f, gs + 0.8f, 0.05f, 0.1f, 0.4f, 0.6f * (1f - t) * a, 6)
      sb.fillOval(gx, gy, gs, gs, 0.5f, 0.8f, 1f, (1f - t) * a, 6)
      s += 1
    }
    sb.fillOvalSoft(sx, sy, 22f * ds * dynGlow, 26f * ds * dynGlow, 0.95f, 0.3f, 0.3f, 0.3f * a, 0f, 14)
    // Leaning into its flight and turning slowly as it flutters
    frame(sx, sy, clampF(ndx, -1f, 1f) * 0.35f + Math.sin(phase * 0.4).toFloat * 0.15f, k)
    var i = 0
    while (i < OFUDA_ROWS) {
      val y = -11f + 22f * i / (OFUDA_ROWS - 1)
      val f = flutter(y)
      _ofL(i * 2) = fx(-4.6f + f, y); _ofL(i * 2 + 1) = fy(-4.6f + f, y)
      _ofR(i * 2) = fx(4.6f + f, y); _ofR(i * 2 + 1) = fy(4.6f + f, y)
      i += 1
    }
    // Its outline: down its left edge and back up its right
    i = 0
    while (i < OFUDA_ROWS) {
      _gx(i) = _ofL(i * 2); _gy(i) = _ofL(i * 2 + 1)
      _gx(2 * OFUDA_ROWS - 1 - i) = _ofR(i * 2); _gy(2 * OFUDA_ROWS - 1 - i) = _ofR(i * 2 + 1)
      i += 1
    }
    sb.strokePolygon(_gx, _gy, 2 * OFUDA_ROWS, 3.2f, 0.14f, 0.06f, 0.05f, 0.96f * a)
    // The paper, a strip at a time (each one convex)
    i = 0
    while (i < OFUDA_ROWS - 1) {
      _polyXs4(0) = _ofL(i * 2); _polyYs4(0) = _ofL(i * 2 + 1)
      _polyXs4(1) = _ofR(i * 2); _polyYs4(1) = _ofR(i * 2 + 1)
      _polyXs4(2) = _ofR(i * 2 + 2); _polyYs4(2) = _ofR(i * 2 + 3)
      _polyXs4(3) = _ofL(i * 2 + 2); _polyYs4(3) = _ofL(i * 2 + 3)
      val shade = 0.94f + 0.06f * Math.sin(phase * 2.2 + i).toFloat
      sb.fillPolygon(_polyXs4, _polyYs4, 4, 0.98f * shade, 0.95f * shade, 0.84f * shade, a)
      i += 1
    }
    // Its red border
    brush(sb, -3.4f, -9.8f, 3.4f, -9.8f, 0.9f, 0.85f, 0.15f, 0.15f, a)
    brush(sb, -3.4f, 9.8f, 3.4f, 9.8f, 0.9f, 0.85f, 0.15f, 0.15f, a)
    brush(sb, -3.4f, -9.8f, -3.4f, 9.8f, 0.9f, 0.85f, 0.15f, 0.15f, a)
    brush(sb, 3.4f, -9.8f, 3.4f, 9.8f, 0.9f, 0.85f, 0.15f, 0.15f, a)
    // The charm: a stroke down it with strokes across, and the red seal under it
    brush(sb, 0f, -8f, 0.2f, 4.2f, 1.6f, 0.06f, 0.05f, 0.05f, a)
    brush(sb, -2.2f, -6f, 2.2f, -6.4f, 1.2f, 0.06f, 0.05f, 0.05f, a)
    brush(sb, -2f, -3f, 2f, -2.4f, 1.1f, 0.06f, 0.05f, 0.05f, a)
    brush(sb, -1.8f, 0.6f, 0.1f, 2.2f, 1f, 0.06f, 0.05f, 0.05f, a)
    brush(sb, 1.9f, 0.4f, 0.4f, 2.4f, 1f, 0.06f, 0.05f, 0.05f, a)
    val sy0 = 6.6f; val sx0 = flutter(sy0)
    sb.fillOval(fx(sx0, sy0), fy(sx0, sy0), 1.9f * k, 1.9f * k, 0.85f, 0.12f, 0.12f, a, 10)
    sb.fillOval(fx(sx0, sy0), fy(sx0, sy0), 0.9f * k, 0.9f * k, 0.98f, 0.9f, 0.8f, a, 8)
  }

  // ── The Djinn ──
  private val LAMP_SPOUT = Array(-4.6f, -1.6f, -13.6f, -5.4f, -14.4f, -3.8f, -5.6f, 2.4f)
  private val LAMP_FOOT = Array(-2.6f, 3.4f, 2.6f, 3.4f, 3.6f, 5.8f, -3.6f, 5.8f)
  private val LAMP_LID = Array(-3.4f, -3.2f, 3.4f, -3.2f, 2.2f, -5.6f, 0f, -6.4f, -2.2f, -5.6f)
  private val LAMP_PARTS = Array(LAMP_SPOUT, LAMP_FOOT, LAMP_LID)

  /**
   * The Djinn's mystic bolt: the lamp itself, gold and jewelled, its handle to the front, pouring out
   * of its spout the blue smoke of the Djinn, curling away behind it with gold sparkles in it.
   */
  private[projectiles] def drawGenieLamp(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 41) * 0.35
    computeAllDynamics(proj, 0.45f, 0.45f, 1f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val k = 1.4f * ds
    val cx = sx; val cy = sy + Math.sin(phase * 0.6).toFloat * 1.2f * ds

    sb.fillOvalSoft(cx, cy, 26f * ds * dynGlow, 22f * ds * dynGlow, 0.55f, 0.5f, 1f, 0.26f * a, 0f, 16)
    faceFrame(cx, cy, ndx, ndy, k, 0.3f)
    // The smoke pouring out of its spout, curling away behind and billowing as it goes
    val tipX = fx(-14f, -4.6f); val tipY = fy(-14f, -4.6f)
    val n = 8
    trailPts(tipX, tipY, ndx, ndy, 48f * ds, n, 6f * ds, 1.3f, phase * 1.3, 6f * ds, 22f * ds, 0.9f * a, -8f * ds, aEnd = 0.1f)
    trailLayer(n, 1f, 0f, 0.55f)
    sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, n, 0.32f, 0.3f, 0.85f)
    trailLayer(n, 0.6f, 0f, 0.8f)
    sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, n, 0.55f, 0.55f, 1f)
    trailLayer(n, 0.25f, 0f, 0.9f)
    trailShift(n, 0.25f, ndx, ndy)
    sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, n, 0.85f, 0.85f, 1f)
    var g = 0
    while (g < 4) {
      val t = loop(tick, 0.035, g / 4.0 + proj.id * 0.19)
      val i0 = Math.min(n - 1, (t * (n - 1)).toInt)
      val gx = _pvX(i0) + hash2(proj.id, g) * 5f * ds; val gy = _pvY(i0) + hash2(proj.id + 1, g) * 4f * ds
      sb.fillStarFlare(gx, gy, 5f * ds * Math.sin(t * Math.PI).toFloat, 1.4f, 0f, 1f, 1f, 0.88f, 0.45f, a)
      g += 1
    }
    // The lamp: spout, foot and lid inked as one with its body, then gold
    val ir = 0.2f; val ig = 0.12f; val ib = 0.02f
    inkAll(sb, LAMP_PARTS, 2.4f, ir, ig, ib, 0.92f * a)
    var m = ovalPts(0f, 0f, 7.6f, 4.2f, 0f, 18)
    sb.strokePolygon(_gx, _gy, m, 2.6f, ir, ig, ib, 0.92f * a)
    // Its handle, a ring at the front
    val hx = fx(9f, -0.4f); val hy = fy(9f, -0.4f)
    sb.strokeOval(hx, hy, 3f * k, 3.2f * k, 1.6f * k + 2.4f, ir, ig, ib, 0.92f * a, 14)
    sb.strokeOval(hx, hy, 3f * k, 3.2f * k, 1.6f * k, GOLD_R, GOLD_G, GOLD_B, a, 14)
    fillAll(sb, LAMP_PARTS, GOLD_R, GOLD_G, GOLD_B, a)
    m = ovalPts(0f, 0f, 7.6f, 4.2f, 0f, 18)
    sb.fillPolygon(_gx, _gy, m, GOLD_R, GOLD_G, GOLD_B, a)
    // Shaded underneath, a band of engraving round it, lit along the top, a ruby on its lid
    sb.fillOvalSoft(fx(0.6f, 2.2f), fy(0.6f, 2.2f), 6.6f * k, 2.2f * k, 0.66f, 0.45f, 0.12f, 0.7f * a, 0f, 12)
    gLine(sb, -6.6f, 0.4f, 6.6f, 0.4f, 0.8f, 0.62f, 0.4f, 0.1f, a)
    gDot(sb, -2.4f, -2.2f, 3f, 0.9f, 1f, 0.97f, 0.8f, 0.85f * a, 8)
    gDot(sb, 0f, -6.6f, 1.2f, 1.1f, 0.85f, 0.12f, 0.2f, a, 8)
    gDot(sb, -0.3f, -6.9f, 0.45f, 0.4f, 1f, 0.85f, 0.85f, a, 6)
  }

  // ── The Runesmith ──
  private val STONE = Array(-6.6f, -6.4f, -4.4f, -8.6f, 4.6f, -8.6f, 6.6f, -6.6f, 6.6f, 6.4f, 4.4f, 8.6f, -4.6f, 8.6f, -6.6f, 6.6f)
  // Its far bevel, down the right and along the bottom, in three convex pieces
  private val STONE_SHADE = Array(
    Array(4.9f, -6.6f, 6.6f, -6.6f, 6.6f, 6.4f, 4.9f, 6.9f),
    Array(-3.2f, 6.9f, 4.9f, 6.9f, 4.4f, 8.6f, -4.6f, 8.6f),
    Array(4.9f, 6.9f, 6.6f, 6.4f, 4.4f, 8.6f))
  // Five runes of the elder futhark, as strokes (x0, y0, x1, y1, ...): fehu, algiz, wunjo, tiwaz, sowilo
  private val RUNES: Array[Array[Float]] = Array(
    Array(-1.6f, -5.4f, -1.6f, 5.6f, -1.6f, -2.6f, 2.8f, -5.6f, -1.6f, 0.8f, 2.8f, -2.4f),
    Array(0f, -5.6f, 0f, 5.6f, 0f, -0.4f, -3.6f, -5f, 0f, -0.4f, 3.6f, -5f),
    Array(-1.8f, -5.6f, -1.8f, 5.6f, -1.8f, -5.6f, 2.6f, -2.8f, 2.6f, -2.8f, -1.8f, 0f),
    Array(0f, -5.6f, 0f, 5.6f, -3.8f, -1.8f, 0f, -5.6f, 0f, -5.6f, 3.8f, -1.8f),
    Array(2.6f, -5.6f, -2.4f, -0.8f, -2.4f, -0.8f, 2.4f, 0.8f, 2.4f, 0.8f, -2.6f, 5.6f)
  )

  /**
   * The Runesmith's rune bolt: one of its runestones, grey and chipped, a rune of the elder futhark cut
   * into it and glowing cyan, tumbling as it flies with chips of stone and cyan sparks off it. Each
   * stone has its own rune.
   */
  private[projectiles] def drawRunestone(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 23) * 0.35
    computeAllDynamics(proj, 0.35f, 0.9f, 1f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx
    val k = 1.35f * ds
    val dir = if (ndx < 0f) -1f else 1f

    // Chips of stone and sparks of the rune falling behind
    var c = 0
    while (c < 5) {
      val t = loop(tick, 0.05, c / 5.0 + proj.id * 0.29)
      val lat = hash2(proj.id + 8, c) * (4f + 9f * t) * ds
      val gx = sx - ndx * (9f + t * 34f) * ds + px * lat; val gy = sy - ndy * (9f + t * 34f) * ds + py * lat + t * t * 7f * ds
      if ((c & 1) == 0) {
        val gs = (2f - t) * ds + 0.5f
        sb.fillRect(gx - gs - 0.6f, gy - gs - 0.6f, gs * 2f + 1.2f, gs * 2f + 1.2f, 0.1f, 0.1f, 0.12f, 0.8f * (1f - t) * a)
        sb.fillRect(gx - gs, gy - gs, gs * 2f, gs * 2f, 0.6f, 0.62f, 0.66f, (1f - t) * a)
      } else sb.fillStarFlare(gx, gy, 5f * ds * (1f - t), 1.4f, 0.5f, 0.8f, 0.55f, 0.95f, 1f, (1f - t) * a)
      c += 1
    }
    sb.fillOvalSoft(sx, sy, 24f * ds * dynGlow, 22f * ds * dynGlow, 0.3f, 0.85f, 1f, 0.28f * a, 0f, 16)
    frame(sx, sy, (phase * 0.32).toFloat * dir, k)
    // The stone: ink, grey, its far bevel shaded, its near one lit
    var n = pts(STONE)
    sb.strokePolygon(_gx, _gy, n, 2.8f, 0.08f, 0.08f, 0.1f, 0.94f * a)
    sb.fillPolygon(_gx, _gy, n, 0.6f, 0.62f, 0.66f, a)
    fillAll(sb, STONE_SHADE, 0.44f, 0.46f, 0.52f, a)
    gLine(sb, -5.6f, -6f, -3.8f, -7.6f, 1.2f, 0.82f, 0.84f, 0.88f, a)
    gLine(sb, -5.6f, -6f, -5.6f, 5f, 1.2f, 0.78f, 0.8f, 0.84f, a)
    // The rune cut into it, glowing
    val rune = RUNES((proj.id & 0x7fffffff) % RUNES.length)
    sb.fillOvalSoft(sx, sy, 8f * k, 9f * k, 0.3f, 0.9f, 1f, 0.45f * a, 0f, 12)
    var i = 0
    while (i < rune.length) {
      gLine(sb, rune(i), rune(i + 1), rune(i + 2), rune(i + 3), 2.5f, 0.08f, 0.16f, 0.2f, a)
      i += 4
    }
    val glow = 0.85f + 0.15f * Math.sin(phase * 1.5).toFloat
    i = 0
    while (i < rune.length) {
      gLine(sb, rune(i), rune(i + 1), rune(i + 2), rune(i + 3), 1.4f, 0.45f * glow, 0.95f * glow, 1f, a)
      i += 4
    }
  }

  // ── The Alchemist ──
  private val NECK = Array(-1.9f, -11f, 1.9f, -11f, 1.9f, -3.6f, -1.9f, -3.6f)
  private val CORK = Array(-2.6f, -14.2f, 2.6f, -14.2f, 2.2f, -10.4f, -2.2f, -10.4f)
  private val CONE = Array(-2.4f, -3.4f, 2.4f, -3.4f, 8.6f, 8.6f, -8.6f, 8.6f)
  private val CONE_ACID = Array(-4.4f, 0.8f, 4.4f, 0.8f, 7.8f, 7.8f, -7.8f, 7.8f)
  private val CONE_NECK = Array(-2.2f, -10.4f, 2.2f, -10.4f, 2.2f, -3f, -2.2f, -3f)
  private val STOPPER = Array(-2.9f, -13.4f, 2.9f, -13.4f, 2.5f, -10f, -2.5f, -10f)

  /**
   * The Alchemist's explosive potion: a round-bottomed flask of fizzing orange, corked, tumbling end
   * over end, sparks spitting from round its cork and bubbles boiling up inside it.
   */
  private[projectiles] def drawExplosivePotion(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 37) * 0.35
    computeAllDynamics(proj, 1f, 0.5f, 0.15f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx
    val k = 1.4f * ds
    val dir = if (ndx < 0f) -1f else 1f

    // Smoke and sparks behind it
    var p = 0
    while (p < 3) {
      val t = loop(tick, 0.04, p / 3.0 + proj.id * 0.31)
      sb.fillOvalSoft(sx - ndx * (12f + t * 30f) * ds, sy - ndy * (12f + t * 30f) * ds - t * 8f * ds,
        (4f + 6f * t) * ds, (3.6f + 5f * t) * ds, 0.45f, 0.4f, 0.38f, 0.4f * (1f - t) * a, 0f, 10)
      p += 1
    }
    sb.fillOvalSoft(sx, sy, 26f * ds * dynGlow, 24f * ds * dynGlow, 1f, 0.55f, 0.15f, 0.3f * a, 0f, 16)
    frame(sx, sy, (phase * 0.5).toFloat * dir, k)
    val ir = 0.1f; val ig = 0.08f; val ib = 0.12f
    // Glass: neck and bulb inked as one, then the glass, the potion in it, the cork
    var n = pts(NECK)
    sb.strokePolygon(_gx, _gy, n, 2.6f, ir, ig, ib, 0.92f * a)
    var m = ovalPts(0f, 2.8f, 8f, 8f, 0f, 20)
    sb.strokePolygon(_gx, _gy, m, 2.6f, ir, ig, ib, 0.92f * a)
    n = pts(CORK)
    sb.strokePolygon(_gx, _gy, n, 2.4f, ir, ig, ib, 0.92f * a)
    sb.fillPolygon(_gx, _gy, n, 0.62f, 0.42f, 0.24f, a)
    n = pts(NECK)
    sb.fillPolygon(_gx, _gy, n, 0.78f, 0.88f, 0.95f, 0.85f * a)
    m = ovalPts(0f, 2.8f, 8f, 8f, 0f, 20)
    sb.fillPolygon(_gx, _gy, m, 0.78f, 0.88f, 0.95f, 0.85f * a)
    val slosh = Math.sin(phase * 1.3).toFloat * 0.6f
    m = ovalPts(0f, 3.6f, 6.6f, 6.4f + slosh, 0f, 18)
    sb.fillPolygon(_gx, _gy, m, 0.95f, 0.36f, 0.1f, a)
    sb.fillOvalSoft(fx(0f, 3.4f), fy(0f, 3.4f), 5f * k, 4.6f * k, 1f, 0.8f, 0.3f, 0.9f * a, 0f, 14)
    // Bubbles boiling up in it
    var b = 0
    while (b < 3) {
      val t = loop(tick, 0.06, b / 3.0 + proj.id * 0.13)
      val bx = hash2(proj.id + 5, b) * 3.6f; val by = 8f - t * 9f
      gDot(sb, bx, by, 0.9f + 0.5f * t, 0.9f + 0.5f * t, 1f, 0.95f, 0.75f, 0.9f * a, 6)
      b += 1
    }
    // The shine on the glass
    gLine(sb, -5.4f, -0.4f, -3.4f, -3.6f, 1.3f, 1f, 1f, 1f, 0.85f * a)
    gLine(sb, -1f, -10.2f, -1f, -5f, 0.8f, 1f, 1f, 1f, 0.7f * a)
    // Sparks spitting from round the cork
    var s = 0
    while (s < 3) {
      val t = loop(tick, 0.09, s / 3.0 + proj.id * 0.37)
      val ang = -Math.PI / 2 + hash2(proj.id + 9, s + (tick / 11)) * 0.9
      val ox = Math.cos(ang).toFloat * (2f + t * 9f); val oy = -14f + Math.sin(ang).toFloat * (2f + t * 9f)
      sb.fillStarFlare(fx(ox, oy), fy(ox, oy), 4.6f * ds * (1f - t), 1.3f, t * 3f, 0.7f, 1f, 0.85f, 0.35f, (1f - t) * a)
      s += 1
    }
  }

  /**
   * The Alchemist's acid flask: a conical flask of glowing green acid, stoppered, tumbling, fuming from
   * round its stopper and dripping acid behind it.
   */
  private[projectiles] def drawAcidFlask(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 41) * 0.35
    computeAllDynamics(proj, 0.5f, 1f, 0.25f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx
    val k = 1.4f * ds
    val dir = if (ndx < 0f) -1f else 1f

    // Fumes rising off it and drips of acid falling behind
    var f = 0
    while (f < 3) {
      val t = loop(tick, 0.035, f / 3.0 + proj.id * 0.19)
      sb.fillOvalSoft(sx - ndx * (8f + t * 24f) * ds + hash2(proj.id, f) * 6f * ds, sy - ndy * (8f + t * 24f) * ds - (6f + t * 14f) * ds,
        (4f + 5f * t) * ds, (3.4f + 4f * t) * ds, 0.65f, 1f, 0.45f, 0.42f * (1f - t) * a, 0f, 10)
      f += 1
    }
    var d = 0
    while (d < 3) {
      val t = loop(tick, 0.045, d / 3.0 + proj.id * 0.23)
      val lat = hash2(proj.id + 3, d) * 6f * ds
      val dx0 = sx - ndx * (10f + t * 30f) * ds + px * lat; val dy0 = sy - ndy * (10f + t * 30f) * ds + py * lat + t * t * 12f * ds
      val dr = (2.4f - t) * ds
      sb.fillOval(dx0, dy0, dr + 1f, dr * 1.3f + 1f, 0.08f, 0.25f, 0.04f, 0.8f * (1f - t) * a, 8)
      sb.fillOval(dx0, dy0, dr, dr * 1.3f, 0.6f, 1f, 0.25f, (1f - t) * a, 8)
      d += 1
    }
    sb.fillOvalSoft(sx, sy, 24f * ds * dynGlow, 22f * ds * dynGlow, 0.5f, 1f, 0.3f, 0.3f * a, 0f, 16)
    frame(sx, sy, (phase * 0.45).toFloat * dir + 0.4f * dir, k)
    val ir = 0.08f; val ig = 0.1f; val ib = 0.08f
    var n = pts(CONE_NECK)
    sb.strokePolygon(_gx, _gy, n, 2.6f, ir, ig, ib, 0.92f * a)
    n = pts(CONE)
    sb.strokePolygon(_gx, _gy, n, 2.6f, ir, ig, ib, 0.92f * a)
    n = pts(STOPPER)
    sb.strokePolygon(_gx, _gy, n, 2.4f, ir, ig, ib, 0.92f * a)
    sb.fillPolygon(_gx, _gy, n, 0.3f, 0.3f, 0.34f, a)
    n = pts(CONE_NECK)
    sb.fillPolygon(_gx, _gy, n, 0.8f, 0.92f, 0.88f, 0.85f * a)
    n = pts(CONE)
    sb.fillPolygon(_gx, _gy, n, 0.8f, 0.92f, 0.88f, 0.85f * a)
    n = pts(CONE_ACID)
    sb.fillPolygon(_gx, _gy, n, 0.45f, 0.95f, 0.18f, a)
    sb.fillOvalSoft(fx(0f, 5f), fy(0f, 5f), 4.6f * k, 2.8f * k, 0.85f, 1f, 0.6f, 0.9f * a, 0f, 12)
    gLine(sb, -4.4f, 0.8f, 4.4f, 0.8f, 1f, 0.75f, 1f, 0.55f, a)
    gLine(sb, -6.2f, 7.2f, -2.2f, -2f, 1.2f, 1f, 1f, 1f, 0.75f * a)
    // A skull and crossbones on it, the label for what it is
    gDot(sb, 0f, 4.2f, 1.5f, 1.3f, 0.95f, 0.95f, 0.9f, a, 8)
    gDot(sb, -0.6f, 4f, 0.35f, 0.35f, 0.1f, 0.15f, 0.08f, a, 4)
    gDot(sb, 0.6f, 4f, 0.35f, 0.35f, 0.1f, 0.15f, 0.08f, a, 4)
    gLine(sb, -2.2f, 5.2f, 2.2f, 7.2f, 0.7f, 0.95f, 0.95f, 0.9f, a)
    gLine(sb, 2.2f, 5.2f, -2.2f, 7.2f, 0.7f, 0.95f, 0.95f, 0.9f, a)
  }
}
