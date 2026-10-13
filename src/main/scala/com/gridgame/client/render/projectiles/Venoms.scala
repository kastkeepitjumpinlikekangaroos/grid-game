package com.gridgame.client.render.projectiles

import com.gridgame.common.model.Projectile
import com.gridgame.client.gl.ShapeBatch
import ProjectileKit._
import Glyphs._

/**
 * Poison, drawn as whoever spat or brewed it: the Plague Doctor's glob of pestilence in a cloud of
 * flies, and his miasma rolling along as a skull of fog; the Serpent's venom striking as a snake of
 * itself; the Hydra's three heads' spit braided together, one of its heads lunging, and the swamp gas
 * it breathes out in bubbles; the Chimera's fire and venom twisted into one; Medusa's ouroboros,
 * shedding stone. They were one green blob five times over, a toxic orb, and two green rings.
 */
private[render] object Venoms {

  // ── The Plague Doctor ──
  private val _lobX = new Array[Float](3)
  private val _lobY = new Array[Float](3)
  private val _lobR = new Array[Float](3)

  /** A fly at (x, y), its wings a blur that beats with the frame. */
  private def fly(sb: ShapeBatch, x: Float, y: Float, s: Float, beat: Boolean, a: Float): Unit = {
    val wy = if (beat) -1.2f * s else -0.7f * s
    sb.fillOval(x - 0.9f * s, y + wy, 1.5f * s, 0.9f * s, 0.92f, 0.95f, 1f, 0.7f * a, 6)
    sb.fillOval(x + 0.9f * s, y + wy, 1.5f * s, 0.9f * s, 0.92f, 0.95f, 1f, 0.7f * a, 6)
    sb.fillOval(x, y, 1.5f * s, 1.1f * s, 0.07f, 0.07f, 0.05f, a, 6)
  }

  /**
   * The Plague Doctor's plague bolt: a glob of pestilence, lumpy, pus-yellow, blotched and bubbling,
   * buzzing with flies — circling it and trailing after it — and a stink rising off it in wavy lines.
   */
  private[projectiles] def drawFlyBlownGlob(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 37) * 0.35
    computeAllDynamics(proj, 0.7f, 0.78f, 0.25f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx
    val R = 12.5f * ds

    sb.fillOvalSoft(sx, sy, R * 2.6f * dynGlow, R * 2.3f * dynGlow, 0.6f, 0.75f, 0.2f, 0.26f * a, 0f, 16)
    // The stink rising off it, in wavy lines
    var l = 0
    while (l < 3) {
      val t = loop(tick, 0.03, l / 3.0 + proj.id * 0.11)
      val bx = sx - ndx * (R * 0.6f + t * 22f * ds) + px * (l - 1) * 6f * ds
      val by = sy - ndy * (R * 0.6f + t * 22f * ds) - R * 0.6f - t * 16f * ds
      var i = 0
      while (i < 5) {
        val u = i / 4f
        _pvX(i) = bx + Math.sin(phase * 1.4 + u * 6.0 + l).toFloat * 2.6f * ds
        _pvY(i) = by - u * 12f * ds
        i += 1
      }
      sb.strokePolylineTapered(_pvX, _pvY, 5, 2.4f * ds, 0.6f, 0.55f, 0.66f, 0.2f, 0.75f * (1f - t) * a, 0.1f * a)
      l += 1
    }
    // Flies trailing after it, then the glob, then the flies circling it in front
    val ap = (tick + proj.id) & 1
    var f = 0
    while (f < 9) {
      val behind = f >= 6
      val orbit = phase * (0.9 + 0.35 * hash01(proj.id + 21, f)) * (if ((f & 1) == 0) 1 else -1) + f * 2.1
      val rr = (if (behind) 10f else 17f + 6f * hash01(proj.id + 13, f)) * ds
      val back = if (behind) (14f + 12f * hash01(proj.id + 17, f)) * ds else 0f
      val jx = hash2(proj.id + f * 7, tick / 2) * 1.6f * ds; val jy = hash2(proj.id + f * 7 + 3, tick / 2) * 1.2f * ds
      val fx0 = sx - ndx * back + Math.cos(orbit).toFloat * rr + jx
      val fy0 = sy - ndy * back + Math.sin(orbit).toFloat * rr * 0.6f + jy - 2f * ds
      val inFront = !behind && Math.sin(orbit) > 0
      if (!inFront) fly(sb, fx0, fy0, 1.5f * ds, ((ap + f) & 1) == 0, a)
      f += 1
    }
    // The glob: three lumps inked as one, pus over a dark underside, blotched and glossy
    val wob = Math.sin(phase * 1.5).toFloat
    _lobX(0) = sx; _lobY(0) = sy; _lobR(0) = R * (0.95f + 0.04f * wob)
    _lobX(1) = sx - ndx * R * 0.55f + px * R * 0.45f; _lobY(1) = sy - ndy * R * 0.55f + py * R * 0.45f; _lobR(1) = R * (0.62f - 0.05f * wob)
    _lobX(2) = sx - ndx * R * 0.5f - px * R * 0.42f; _lobY(2) = sy - ndy * R * 0.5f - py * R * 0.42f; _lobR(2) = R * (0.55f + 0.05f * wob)
    var i = 0
    while (i < 3) { sb.fillOval(_lobX(i), _lobY(i), _lobR(i) + 2f, _lobR(i) * 0.88f + 2f, 0.12f, 0.13f, 0.03f, 0.92f * a, 16); i += 1 }
    i = 0
    while (i < 3) { sb.fillOval(_lobX(i), _lobY(i), _lobR(i), _lobR(i) * 0.88f, 0.48f, 0.52f, 0.14f, a, 16); i += 1 }
    sb.fillOvalSoft(sx - R * 0.2f, sy - R * 0.25f, R * 0.85f, R * 0.7f, 0.82f, 0.86f, 0.35f, a, 0.3f * a, 16)
    sb.fillOval(sx + R * 0.35f, sy + R * 0.25f, R * 0.22f, R * 0.16f, 0.3f, 0.34f, 0.06f, 0.9f * a, 8)
    sb.fillOval(sx - R * 0.3f, sy + R * 0.35f, R * 0.14f, R * 0.1f, 0.3f, 0.34f, 0.06f, 0.85f * a, 6)
    sb.fillOval(sx - R * 0.45f, sy - R * 0.45f, R * 0.24f, R * 0.15f, 1f, 1f, 0.85f, 0.9f * a, 8)
    // A bubble swelling and bursting on it
    val bt = loop(tick, 0.03, proj.id * 0.19)
    val bx = sx + R * 0.45f; val by = sy - R * 0.35f
    if (bt < 0.85f) {
      val bs = R * 0.26f * Math.min(1f, bt * 2.2f)
      sb.fillOval(bx, by, bs + 1.2f, bs + 1.2f, 0.12f, 0.13f, 0.03f, 0.8f * a, 8)
      sb.fillOval(bx, by, bs, bs, 0.86f, 0.9f, 0.45f, a, 8)
    } else {
      val pt = (bt - 0.85f) / 0.15f
      sb.strokeOval(bx, by, R * (0.26f + pt * 0.3f), R * (0.26f + pt * 0.3f), 1.5f * (1f - pt), 0.85f, 0.9f, 0.45f, (1f - pt) * a, 10)
    }
    f = 0
    while (f < 6) {
      val orbit = phase * (0.9 + 0.35 * hash01(proj.id + 21, f)) * (if ((f & 1) == 0) 1 else -1) + f * 2.1
      if (Math.sin(orbit) > 0) {
        val rr = (17f + 6f * hash01(proj.id + 13, f)) * ds
        val jx = hash2(proj.id + f * 7, tick / 2) * 1.6f * ds; val jy = hash2(proj.id + f * 7 + 3, tick / 2) * 1.2f * ds
        fly(sb, sx + Math.cos(orbit).toFloat * rr + jx, sy + Math.sin(orbit).toFloat * rr * 0.6f + jy - 2f * ds,
          1.5f * ds, ((ap + f) & 1) == 0, a)
      }
      f += 1
    }
  }

  // The skull of fog: cranium puffs, then jaw puffs (x, y, r)
  private val SKULL_PUFFS = Array(0f, -8.5f, 9.4f, -6.4f, -5f, 7.2f, 6.4f, -5f, 7.2f, -7.4f, 1f, 6f, 7.4f, 1f, 6f, 0f, -2f, 8f)
  private val JAW_PUFFS = Array(-4.6f, 7.4f, 4.4f, 0f, 8.4f, 5f, 4.6f, 7.4f, 4.4f)
  private val _puffR = new Array[Float](9)

  /** Puffs (x, y, r triples) in the frame, `pass` 0 ink, 1 fog, 2 its lit tops; breathing by `br`. */
  private def puffs(sb: ShapeBatch, p: Array[Float], dy: Float, pass: Int, br: Double, a: Float): Unit = {
    var i = 0
    while (i < p.length) {
      val r = p(i + 2) * (1f + 0.05f * Math.sin(br + i).toFloat)
      val x = p(i); val y = p(i + 1) + dy
      (pass: @scala.annotation.switch) match {
        case 0 => sb.fillOval(fx(x, y), fy(x, y), (r + 1.8f) * _fk, (r * 0.92f + 1.8f) * _fk, 0.12f, 0.15f, 0.04f, 0.85f * a, 16)
        case 1 => sb.fillOval(fx(x, y), fy(x, y), r * _fk, r * 0.92f * _fk, 0.5f, 0.62f, 0.22f, a, 16)
        case _ => sb.fillOval(fx(x - r * 0.15f, y - r * 0.25f), fy(x - r * 0.15f, y - r * 0.25f), r * 0.72f * _fk,
          r * 0.56f * _fk, 0.7f, 0.8f, 0.36f, a, 14)
      }
      i += 3
    }
  }

  /**
   * The Plague Doctor's miasma: a skull of sickly fog rolling along, churning — hollow eyes glowing
   * green, its jaw working — dissolving into wisps behind it.
   */
  private[projectiles] def drawMiasmaSkull(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 41) * 0.35
    computeAllDynamics(proj, 0.55f, 0.7f, 0.25f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx
    val k = 1.3f * ds
    val cx = sx; val cy = sy - 6f * ds + Math.sin(phase * 0.5).toFloat * 1.5f * ds

    // Wisps of it dissolving behind
    var w = 0
    while (w < 3) {
      val n = 6
      trailPts(cx + px * (w - 1) * 8f * ds, cy + py * (w - 1) * 8f * ds + 4f * ds, ndx, ndy, (34f + 8f * hash01(proj.id, w)) * ds,
        n, 4f * ds, 1f, phase * 1.2 + w * 1.9, 16f * ds, 4f * ds, 0.55f * a, -4f * ds)
      trailLayer(n, 1f, 0f, 1f)
      sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, n, 0.52f, 0.64f, 0.24f)
      w += 1
    }
    sb.fillOvalSoft(cx, cy, 34f * ds * dynGlow, 30f * ds * dynGlow, 0.55f, 0.75f, 0.2f, 0.22f * a, 0f, 16)
    frame(cx, cy, Math.sin(phase * 0.35).toFloat * 0.08f, k)
    val jaw = 1f + Math.abs(Math.sin(phase * 0.7).toFloat) * 2f
    val br = phase * 0.8
    puffs(sb, SKULL_PUFFS, 0f, 0, br, a)
    puffs(sb, JAW_PUFFS, jaw, 0, br, a)
    puffs(sb, JAW_PUFFS, jaw, 1, br, a)
    puffs(sb, SKULL_PUFFS, 0f, 1, br, a)
    puffs(sb, JAW_PUFFS, jaw, 2, br, a)
    puffs(sb, SKULL_PUFFS, 0f, 2, br, a)
    // The mouth between them, and its teeth
    var n = ovalPts(0f, 4.6f + jaw * 0.5f, 6.4f, 1.2f + jaw * 0.45f, 0f, 14)
    sb.fillPolygon(_gx, _gy, n, 0.1f, 0.13f, 0.03f, 0.95f * a)
    var t = 0
    while (t < 4) {
      val tx = -3.6f + t * 2.4f
      gLine(sb, tx, 3.9f, tx, 5f + jaw * 0.35f, 1.3f, 0.86f, 0.9f, 0.6f, a)
      t += 1
    }
    // Its eyes, hollow and glowing; its nose
    var e = -1
    while (e <= 1) {
      n = ovalPts(e * 4.6f, -1.6f, 3.2f, 3.8f, e * 0.2f, 14)
      sb.fillPolygon(_gx, _gy, n, 0.08f, 0.1f, 0.02f, a)
      sb.fillOvalSoft(fx(e * 4.6f, -1.2f), fy(e * 4.6f, -1.2f), 3.4f * k, 3.4f * k, 0.6f, 1f, 0.3f, 0.75f * a, 0f, 10)
      gDot(sb, e * 4.6f, -1.2f, 0.9f, 1.1f, 0.85f, 1f, 0.6f, a, 6)
      e += 2
    }
    _gx(0) = fx(-1.4f, 1.4f); _gy(0) = fy(-1.4f, 1.4f)
    _gx(1) = fx(1.4f, 1.4f); _gy(1) = fy(1.4f, 1.4f)
    _gx(2) = fx(0f, 3.4f); _gy(2) = fy(0f, 3.4f)
    sb.fillPolygon(_gx, _gy, 3, 0.08f, 0.1f, 0.02f, a)
  }

  // ── The Serpent ──
  private final val SNAKE_N = 11
  private val _snX = new Array[Float](SNAKE_N)
  private val _snY = new Array[Float](SNAKE_N)
  private val _snW = new Array[Float](SNAKE_N)
  private val SNAKE_HEAD = Array(-3.6f, -3.6f, 1.6f, -4.4f, 6f, -2.8f, 7.6f, 0f, 6f, 2.8f, 1.6f, 4.4f, -3.6f, 3.6f)

  /**
   * The Serpent's venom bolt: a snake of its own venom, striking — green and glossy with its yellow
   * belly, its body swimming back from its head in an S, eyes slitted, fangs bared and dripping, its
   * tongue flicking.
   */
  private[projectiles] def drawVenomSnake(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 29) * 0.35
    computeAllDynamics(proj, 0.35f, 0.85f, 0.3f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx
    val k = 1.3f * ds
    val hx = sx - ndx * 7f * k; val hy = sy - ndy * 7f * k
    val L = 54f * ds

    sb.fillOvalSoft(hx - ndx * 14f * ds, hy - ndy * 14f * ds, 30f * ds * dynGlow, 18f * ds * dynGlow, 0.35f, 0.85f, 0.3f, 0.22f * a, 0f, 16)
    // Its body, swimming back from the head in an S
    var i = 0
    while (i < SNAKE_N) {
      val t = i.toFloat / (SNAKE_N - 1)
      val sw = Math.sin(phase * 1.6 - t * 7.5).toFloat * 6.5f * ds * Math.min(1f, t * 2.2f)
      _snX(i) = hx - ndx * (3f * k + L * t) + px * sw
      _snY(i) = hy - ndy * (3f * k + L * t) + py * sw
      _snW(i) = (5.8f + 3.2f * Math.sin(Math.PI * Math.min(1f, t * 1.4f)).toFloat) * ds * (1f - t * t * 0.85f)
      i += 1
    }
    i = 0
    while (i < SNAKE_N) { _pvW(i) = _snW(i) + 2.6f; _pvA(i) = 0.92f * a; i += 1 }
    sb.strokePolylineVar(_snX, _snY, _pvW, _pvA, SNAKE_N, 0.03f, 0.14f, 0.04f)
    i = 0
    while (i < SNAKE_N) { _pvW(i) = _snW(i); _pvA(i) = a; i += 1 }
    sb.strokePolylineVar(_snX, _snY, _pvW, _pvA, SNAKE_N, 0.28f, 0.66f, 0.24f)
    // Its yellow belly down one side, a lit ridge down its back
    i = 0
    while (i < SNAKE_N) {
      val j = Math.max(0, i - 1); val m = Math.min(SNAKE_N - 1, i + 1)
      val ex = _snX(m) - _snX(j); val ey = _snY(m) - _snY(j)
      val el = Math.max(1e-3f, Math.sqrt(ex * ex + ey * ey).toFloat)
      val nx = -ey / el; val ny = ex / el
      val down = if (ny >= 0f) 1f else -1f
      _pvX(i) = _snX(i) + nx * down * _snW(i) * 0.26f; _pvY(i) = _snY(i) + ny * down * _snW(i) * 0.26f
      _pvW(i) = _snW(i) * 0.4f; _pvA(i) = a
      i += 1
    }
    sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, SNAKE_N, 0.95f, 0.86f, 0.36f)
    // Darker bands across its back
    i = 1
    while (i < SNAKE_N - 2) {
      val ex = _snX(i + 1) - _snX(i); val ey = _snY(i + 1) - _snY(i)
      val el = Math.max(1e-3f, Math.sqrt(ex * ex + ey * ey).toFloat)
      val nx = -ey / el; val ny = ex / el
      val w = _snW(i) * 0.42f
      sb.strokeLine(_snX(i) + nx * w, _snY(i) + ny * w, _snX(i) - nx * w, _snY(i) - ny * w, 1.6f * ds, 0.12f, 0.38f, 0.1f, 0.85f * a)
      i += 2
    }
    // Venom dripping from its fangs, falling behind
    var d = 0
    while (d < 3) {
      val t = loop(tick, 0.05, d / 3.0 + proj.id * 0.27)
      val dx0 = sx - ndx * (4f + t * 22f) * ds + px * hash2(proj.id, d) * 3f * ds; val dy0 = sy - ndy * (4f + t * 22f) * ds + t * t * 12f * ds + 3f * ds
      val dr = (2.2f - t) * ds
      sb.fillOval(dx0, dy0, dr + 0.9f, dr * 1.3f + 0.9f, 0.03f, 0.14f, 0.04f, 0.8f * (1f - t) * a, 8)
      sb.fillOval(dx0, dy0, dr, dr * 1.3f, 0.6f, 1f, 0.35f, (1f - t) * a, 8)
      d += 1
    }
    // The head, seen from above, its tongue flicking out of it
    headingFrame(hx, hy, ndx, ndy, k)
    val flick = loop(tick, 0.06, proj.id * 0.13)
    if (flick < 0.4f) {
      val tl = 3f * Math.sin(flick / 0.4f * Math.PI).toFloat
      gLine(sb, 7.4f, 0f, 7.6f + tl, 0f, 0.8f, 0.85f, 0.15f, 0.2f, a)
      gLine(sb, 7.6f + tl, 0f, 8.6f + tl, -0.9f, 0.6f, 0.85f, 0.15f, 0.2f, a)
      gLine(sb, 7.6f + tl, 0f, 8.6f + tl, 0.9f, 0.6f, 0.85f, 0.15f, 0.2f, a)
    }
    var n = pts(SNAKE_HEAD)
    sb.strokePolygon(_gx, _gy, n, 2.6f, 0.03f, 0.14f, 0.04f, 0.94f * a)
    sb.fillPolygon(_gx, _gy, n, 0.3f, 0.7f, 0.26f, a)
    gLine(sb, -2.4f, 0f, 5.6f, 0f, 1.6f, 0.45f, 0.85f, 0.38f, 0.8f * a)
    // Fangs, bared and dripping
    var side = -1f
    while (side <= 1f) {
      _gx(0) = fx(5.6f, side * 1.4f); _gy(0) = fy(5.6f, side * 1.4f)
      _gx(1) = fx(7.6f, side * 1.9f); _gy(1) = fy(7.6f, side * 1.9f)
      _gx(2) = fx(5.8f, side * 2.4f); _gy(2) = fy(5.8f, side * 2.4f)
      sb.fillPolygon(_gx, _gy, 3, 0.98f, 0.98f, 0.92f, a)
      // Its eyes, yellow and slitted
      n = ovalPts(1.4f, side * 2.9f, 1.6f, 1.1f, 0f, 10)
      sb.fillPolygon(_gx, _gy, n, 0.04f, 0.1f, 0.03f, a)
      n = ovalPts(1.4f, side * 2.9f, 1.15f, 0.8f, 0f, 10)
      sb.fillPolygon(_gx, _gy, n, 1f, 0.86f, 0.2f, a)
      gLine(sb, 1.4f, side * 2.9f - 0.7f, 1.4f, side * 2.9f + 0.7f, 0.5f, 0.05f, 0.05f, 0.02f, a)
      side += 2f
    }
  }

  // ── The Hydra ──
  private final val BRAID_N = 9

  /**
   * The Hydra's venom bolt: its three heads' spit flying as one — three globs of venom wheeling round
   * each other, the paths they have flown braided behind them.
   */
  private[projectiles] def drawVenomBraid(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 31) * 0.35
    computeAllDynamics(proj, 0.65f, 0.95f, 0.3f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx
    val rad = 9f * ds
    val spin = phase * 1.5

    sb.fillOvalSoft(sx, sy, 24f * ds * dynGlow, 18f * ds * dynGlow, 0.5f, 0.95f, 0.4f, 0.26f * a, 0f, 16)
    // Each glob's braid: where it has been, wheeling back along the path. Behind first.
    var pass = 0
    while (pass < 2) {
      var j = 0
      while (j < 3) {
        var i = 0
        while (i < BRAID_N) {
          val t = i.toFloat / (BRAID_N - 1)
          val ang = spin + j * (2 * Math.PI / 3) - t * 3.4
          val s = Math.sin(ang).toFloat
          _pvX(i) = sx - ndx * t * 46f * ds + px * Math.cos(ang).toFloat * rad * (1f - 0.3f * t)
          _pvY(i) = sy - ndy * t * 46f * ds + py * Math.cos(ang).toFloat * rad * (1f - 0.3f * t) - s * rad * 0.35f
          val front = s >= 0f
          _pvW(i) = (if (pass == 0) 7f else 4.6f) * ds * (1f - 0.75f * t) + (if (pass == 0) 1.6f else 0f)
          _pvA(i) = (if (front == (pass == 1)) 1f else 0.35f) * a * (1f - t)
          i += 1
        }
        if (pass == 0) sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, BRAID_N, 0.05f, 0.25f, 0.18f)
        else sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, BRAID_N, 0.45f, 0.9f, 0.45f)
        j += 1
      }
      pass += 1
    }
    // The three globs, the far ones first
    var order = 0
    while (order < 2) {
      var j = 0
      while (j < 3) {
        val ang = spin + j * (2 * Math.PI / 3)
        val s = Math.sin(ang).toFloat
        if ((s >= 0f) == (order == 1)) {
          val gx = sx + px * Math.cos(ang).toFloat * rad; val gy = sy + py * Math.cos(ang).toFloat * rad - s * rad * 0.35f
          val gr = (5.8f + 1f * s) * ds
          sb.fillOval(gx, gy, gr + 1.8f, gr + 1.8f, 0.04f, 0.2f, 0.14f, 0.92f * a, 14)
          sb.fillOval(gx, gy, gr, gr, 0.6f, 0.92f, 0.28f, a, 14)
          sb.fillOval(gx - gr * 0.1f, gy + gr * 0.3f, gr * 0.75f, gr * 0.45f, 0.3f, 0.66f, 0.3f, 0.7f * a, 10)
          sb.fillOval(gx - gr * 0.35f, gy - gr * 0.38f, gr * 0.35f, gr * 0.24f, 1f, 1f, 0.85f, 0.95f * a, 8)
        }
        j += 1
      }
      order += 1
    }
  }

  private val HY_SKULL_X = -1.6f; private val HY_SKULL_Y = -1.4f
  private val _upJaw = new Array[Float](10)
  private val _loJaw = new Array[Float](10)
  private val _gape = new Array[Float](8)
  private val HY_HORN1 = Array(-4.8f, -4.6f, -1.6f, -5.8f, -11f, -10.6f)
  private val HY_HORN2 = Array(-6.4f, -2.2f, -3.6f, -3.8f, -12.6f, -5.6f)

  /**
   * One of the Hydra's heads, lunging out with its venom: teal, horned, jaws snapping open and shut on
   * dripping fangs, a yellow eye — its neck trailing back to the Hydra and fading.
   */
  private[projectiles] def drawHydraHead(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 43) * 0.35
    computeAllDynamics(proj, 0.25f, 0.6f, 0.55f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val k = 1.35f * ds
    val hx = sx - ndx * 8f * k; val hy = sy - ndy * 8f * k

    sb.fillOvalSoft(hx, hy, 24f * ds * dynGlow, 20f * ds * dynGlow, 0.35f, 0.9f, 0.6f, 0.22f * a, 0f, 16)
    // Its neck, trailing back and fading
    val n = 7
    trailPts(hx - ndx * 4f * k, hy - ndy * 4f * k + 2f * k, ndx, ndy, 44f * ds, n, 5f * ds, 0.8f, phase * 1.2,
      12f * k, 6f * k, a, 6f * ds, aEnd = 0f)
    trailLayer(n, 1f, 2.8f, 0.92f)
    sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, n, 0.03f, 0.13f, 0.11f)
    trailLayer(n, 1f, 0f, 1f)
    sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, n, 0.2f, 0.52f, 0.48f)
    trailLayer(n, 0.38f, 0f, 1f)
    trailShift(n, if (ndx < -0.02f) -0.26f else 0.26f, ndx, ndy)
    sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, n, 0.9f, 0.88f, 0.68f)
    faceFrame(hx, hy, ndx, ndy, k, 0.45f)
    // Jaws snapping: the upper one lifts and the lower drops as they open
    val gape = 0.5f + 0.5f * Math.sin(phase * 1.7).toFloat
    val up = -1.4f * gape; val dn = 2f * gape
    _upJaw(0) = -1.2f; _upJaw(1) = -4.6f; _upJaw(2) = 11.6f; _upJaw(3) = -3f + up
    _upJaw(4) = 12.8f; _upJaw(5) = -1.3f + up; _upJaw(6) = 10.8f; _upJaw(7) = -0.3f + up
    _upJaw(8) = -1.2f; _upJaw(9) = 0.4f
    _loJaw(0) = -0.4f; _loJaw(1) = 1.2f; _loJaw(2) = 9.6f; _loJaw(3) = 1.8f + dn
    _loJaw(4) = 10.6f; _loJaw(5) = 3.2f + dn; _loJaw(6) = 9f; _loJaw(7) = 4.4f + dn
    _loJaw(8) = -0.4f; _loJaw(9) = 4.4f
    _gape(0) = 0f; _gape(1) = 0f; _gape(2) = 10.6f; _gape(3) = -0.4f + up
    _gape(4) = 9.4f; _gape(5) = 2.2f + dn; _gape(6) = 0f; _gape(7) = 1.8f
    val ir = 0.03f; val ig = 0.13f; val ib = 0.11f
    var m = pts(HY_HORN2)
    sb.strokePolygon(_gx, _gy, m, 2.2f, ir, ig, ib, 0.9f * a)
    sb.fillPolygon(_gx, _gy, m, 0.78f, 0.76f, 0.58f, a)
    m = pts(HY_HORN1)
    sb.strokePolygon(_gx, _gy, m, 2.2f, ir, ig, ib, 0.9f * a)
    sb.fillPolygon(_gx, _gy, m, 0.9f, 0.88f, 0.68f, a)
    m = pts(_gape)
    sb.fillPolygon(_gx, _gy, m, 0.42f, 0.06f, 0.1f, a)
    // Head and jaws inked as one, then teal, its jaw's underside cream
    m = ovalPts(HY_SKULL_X, HY_SKULL_Y, 6.4f, 5.2f, 0f, 16)
    sb.strokePolygon(_gx, _gy, m, 2.6f, ir, ig, ib, 0.94f * a)
    m = pts(_upJaw); sb.strokePolygon(_gx, _gy, m, 2.6f, ir, ig, ib, 0.94f * a)
    m = pts(_loJaw); sb.strokePolygon(_gx, _gy, m, 2.6f, ir, ig, ib, 0.94f * a)
    sb.fillPolygon(_gx, _gy, m, 0.86f, 0.84f, 0.64f, a)
    m = pts(_upJaw); sb.fillPolygon(_gx, _gy, m, 0.24f, 0.6f, 0.55f, a)
    m = ovalPts(HY_SKULL_X, HY_SKULL_Y, 6.4f, 5.2f, 0f, 16)
    sb.fillPolygon(_gx, _gy, m, 0.24f, 0.6f, 0.55f, a)
    gLine(sb, -5f, -4.4f, 9f, -3.4f + up * 0.8f, 1.3f, 0.45f, 0.8f, 0.72f, 0.85f * a)
    // Fangs
    var f = 0
    while (f < 2) {
      val fxp = 8.4f + f * 2.4f
      _gx(0) = fx(fxp - 0.7f, -0.6f + up); _gy(0) = fy(fxp - 0.7f, -0.6f + up)
      _gx(1) = fx(fxp + 0.7f, -0.6f + up); _gy(1) = fy(fxp + 0.7f, -0.6f + up)
      _gx(2) = fx(fxp, 1.4f + up); _gy(2) = fy(fxp, 1.4f + up)
      sb.fillPolygon(_gx, _gy, 3, 1f, 1f, 0.95f, a)
      _gx(0) = fx(fxp - 1.4f, 2.4f + dn); _gy(0) = fy(fxp - 1.4f, 2.4f + dn)
      _gx(1) = fx(fxp - 0.2f, 2.4f + dn); _gy(1) = fy(fxp - 0.2f, 2.4f + dn)
      _gx(2) = fx(fxp - 0.8f, 0.8f + dn); _gy(2) = fy(fxp - 0.8f, 0.8f + dn)
      sb.fillPolygon(_gx, _gy, 3, 1f, 1f, 0.95f, a)
      f += 1
    }
    // Its eye, yellow and slitted, under a brow
    m = ovalPts(3f, -3f, 1.9f, 1.3f, -0.2f, 10)
    sb.fillPolygon(_gx, _gy, m, 1f, 0.86f, 0.2f, a)
    gLine(sb, 3.1f, -4f, 3.1f, -2f, 0.6f, 0.05f, 0.05f, 0.02f, a)
    gLine(sb, 0.6f, -4.8f, 5.4f, -4.2f, 1f, ir, ig, ib, a)
    gDot(sb, 11.4f, -2.4f + up, 0.5f, 0.45f, ir, ig, ib, a, 6)
    // Venom drooling from its jaws
    var d = 0
    while (d < 3) {
      val t = loop(tick, 0.05, d / 3.0 + proj.id * 0.31)
      val ox = fx(8.6f, 3.6f + dn); val oy = fy(8.6f, 3.6f + dn)
      val dx0 = ox - ndx * t * 18f * ds + hash2(proj.id, d) * 2f * ds; val dy0 = oy - ndy * t * 18f * ds + t * t * 14f * ds
      val dr = (2f - t) * ds
      sb.fillOval(dx0, dy0, dr + 0.9f, dr * 1.3f + 0.9f, 0.04f, 0.2f, 0.06f, 0.8f * (1f - t) * a, 8)
      sb.fillOval(dx0, dy0, dr, dr * 1.3f, 0.65f, 1f, 0.3f, (1f - t) * a, 8)
      d += 1
    }
  }

  /**
   * The Hydra's poison cloud: swamp gas breathed out of the fen it lives in — a clutch of fat bubbles
   * of it, sickly teal, wobbling, bursting and swelling again, small ones rising off the top and a low
   * mist rolling under it.
   */
  private[projectiles] def drawSwampGas(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 47) * 0.35
    computeAllDynamics(proj, 0.4f, 0.75f, 0.45f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f) * 1.35f
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx

    // The mist rolling under and behind it
    val n = 6
    trailPts(sx, sy + 4f * ds, ndx, ndy, 50f * ds, n, 4f * ds, 0.8f, phase * 0.9, 40f * ds, 16f * ds, 0.4f * a, 0f)
    trailLayer(n, 1f, 0f, 1f)
    sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, n, 0.42f, 0.6f, 0.45f)
    sb.fillOvalSoft(sx, sy, 36f * ds * dynGlow, 26f * ds * dynGlow, 0.4f, 0.8f, 0.5f, 0.28f * a, 0f, 16)
    // The bubbles: each swells, bursts and swells again, in its own time; the far ones first
    var b = 0
    while (b < 7) {
      val ang = b * 0.9 + proj.id
      val ring = if (b == 0) 0f else 1f
      val ox = (Math.cos(ang).toFloat * 13f * ring + hash2(proj.id + 4, b) * 2f) * ds
      val oy = (Math.sin(ang).toFloat * 7f * ring - 6f + hash2(proj.id + 5, b) * 2f) * ds
      val bx = sx + px * ox * 0.3f + ox * 0.7f + Math.sin(phase * 0.7 + b).toFloat * 1.2f * ds
      val by = sy + py * ox * 0.1f + oy + Math.cos(phase * 0.6 + b * 1.7).toFloat * 1.2f * ds
      val t = loop(tick, 0.012 + 0.004 * (b % 3), b / 7.0 + proj.id * 0.13)
      val full = (if (b == 0) 11f else 7f + 2.5f * hash01(proj.id + 6, b)) * ds
      if (t < 0.9f) {
        val r = full * Math.min(1f, 0.25f + t * 1.6f) * (1f + 0.05f * Math.sin(phase * 1.3 + b).toFloat)
        sb.fillOval(bx, by, r, r * 0.92f, 0.36f, 0.72f, 0.46f, 0.42f * a, 18)
        sb.strokeOval(bx, by, r, r * 0.92f, 2.2f, 0.04f, 0.2f, 0.12f, 0.85f * a, 18)
        sb.strokeArc(bx, by, r * 0.72f, r * 0.66f, 3.5f, 1.5f, 1.5f * ds, 0.85f, 1f, 0.85f, 0.85f * a, 8)
        sb.fillOval(bx - r * 0.38f, by - r * 0.4f, r * 0.2f, r * 0.14f, 1f, 1f, 1f, 0.95f * a, 8)
      } else {
        val pt = (t - 0.9f) / 0.1f
        sb.strokeOval(bx, by, full * (1f + pt * 0.5f), full * 0.92f * (1f + pt * 0.5f), 2f * (1f - pt), 0.7f, 1f, 0.75f, (1f - pt) * a, 18)
      }
      b += 1
    }
    // Little ones rising off the top
    var r = 0
    while (r < 3) {
      val t = loop(tick, 0.04, r / 3.0 + proj.id * 0.27)
      val bx = sx + hash2(proj.id + 9, r) * 10f * ds - ndx * t * 10f * ds; val by = sy - (14f + t * 20f) * ds
      val rs = (1.4f + 1.4f * (1f - t)) * ds
      sb.strokeOval(bx, by, rs, rs, 1.2f, 0.04f, 0.2f, 0.12f, 0.8f * (1f - t) * a, 8)
      sb.fillOval(bx, by, rs * 0.8f, rs * 0.8f, 0.5f, 0.85f, 0.55f, 0.6f * (1f - t) * a, 8)
      r += 1
    }
  }

  // ── The Chimera ──
  private final val HELIX_N = 9

  /**
   * The Chimera's venom fan: its lion's fire and its serpent's venom twisted into one — a ball of the
   * two curled round each other, turning, and the two of them braided behind it, the fire guttering
   * and the venom dripping.
   */
  private[projectiles] def drawFireVenomHelix(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 37) * 0.35
    computeAllDynamics(proj, 0.8f, 0.7f, 0.2f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx
    val R = 11.5f * ds
    val spin = phase * 1.4

    sb.fillOvalSoft(sx, sy, R * 2.8f * dynGlow, R * 2.4f * dynGlow, 0.85f, 0.6f, 0.2f, 0.26f * a, 0f, 16)
    // The two strands, braided: those behind first
    var pass = 0
    while (pass < 2) {
      var strand = 0
      while (strand < 2) {
        var layer = 0
        while (layer < 3) {
          var i = 0
          while (i < HELIX_N) {
            val t = i.toFloat / (HELIX_N - 1)
            val ang = spin + strand * Math.PI - t * 4.4
            val s = Math.sin(ang).toFloat
            val off = Math.cos(ang).toFloat * R * 0.8f * (1f - 0.35f * t)
            val flick = if (strand == 0) 0.85f + 0.15f * Math.sin(phase * 4 + i * 2.1).toFloat else 1f
            _pvX(i) = sx - ndx * t * 44f * ds + px * off; _pvY(i) = sy - ndy * t * 44f * ds + py * off - s * R * 0.3f
            val w = (if (layer == 0) 6.2f + 2.4f / ds else if (layer == 1) 6.2f else 2.6f) * ds * (1f - 0.8f * t) * flick
            _pvW(i) = w
            _pvA(i) = (if ((s >= 0f) == (pass == 1)) 1f else 0f) * a * (1f - t * t)
            i += 1
          }
          if (strand == 0) (layer: @scala.annotation.switch) match {
            case 0 => sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, HELIX_N, 0.3f, 0.06f, 0.02f)
            case 1 => sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, HELIX_N, 1f, 0.45f, 0.08f)
            case _ => sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, HELIX_N, 1f, 0.9f, 0.45f)
          } else (layer: @scala.annotation.switch) match {
            case 0 => sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, HELIX_N, 0.04f, 0.2f, 0.05f)
            case 1 => sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, HELIX_N, 0.4f, 0.86f, 0.22f)
            case _ => sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, HELIX_N, 0.8f, 1f, 0.6f)
          }
          layer += 1
        }
        strand += 1
      }
      pass += 1
    }
    // The ball: venom and fire curled round each other, turning
    val c = Math.cos(spin).toFloat; val s = Math.sin(spin).toFloat
    sb.fillOval(sx, sy, R + 2f, R + 2f, 0.12f, 0.08f, 0.02f, 0.92f * a, 20)
    sb.fillOval(sx, sy, R, R, 0.42f, 0.86f, 0.22f, a, 20)
    // The fire's half: a half-disc on one side of the turning divide
    var i = 0
    while (i < 10) {
      val th = spin - Math.PI / 2 + i * (Math.PI / 9)
      _gx(i) = sx + Math.cos(th).toFloat * R; _gy(i) = sy + Math.sin(th).toFloat * R
      i += 1
    }
    sb.fillPolygon(_gx, _gy, 10, 1f, 0.48f, 0.1f, a)
    sb.fillOval(sx - s * R * 0.5f, sy + c * R * 0.5f, R * 0.5f, R * 0.5f, 1f, 0.48f, 0.1f, a, 14)
    sb.fillOval(sx + s * R * 0.5f, sy - c * R * 0.5f, R * 0.5f, R * 0.5f, 0.42f, 0.86f, 0.22f, a, 14)
    sb.fillOval(sx - s * R * 0.5f, sy + c * R * 0.5f, R * 0.17f, R * 0.17f, 0.65f, 1f, 0.4f, a, 8)
    sb.fillOval(sx + s * R * 0.5f, sy - c * R * 0.5f, R * 0.17f, R * 0.17f, 1f, 0.9f, 0.4f, a, 8)
    sb.fillOvalSoft(sx - R * 0.3f, sy - R * 0.35f, R * 0.45f, R * 0.3f, 1f, 1f, 0.9f, 0.7f * a, 0f, 10)
    // Embers off the fire, drips off the venom
    var e = 0
    while (e < 4) {
      val t = loop(tick, 0.05, e / 4.0 + proj.id * 0.23)
      val lat = hash2(proj.id + 2, e) * (4f + 8f * t) * ds
      val ex = sx - ndx * (R + t * 30f * ds) + px * lat
      val ey = sy - ndy * (R + t * 30f * ds) + py * lat + (if ((e & 1) == 0) -t * 10f else t * t * 12f) * ds
      val es = (2f - t) * ds + 0.4f
      if ((e & 1) == 0) sb.fillOval(ex, ey, es, es, 1f, mix(0.9f, 0.4f, t), 0.2f, (1f - t) * a, 6)
      else {
        sb.fillOval(ex, ey, es + 0.8f, es * 1.3f + 0.8f, 0.04f, 0.2f, 0.05f, 0.8f * (1f - t) * a, 8)
        sb.fillOval(ex, ey, es, es * 1.3f, 0.55f, 1f, 0.3f, (1f - t) * a, 8)
      }
      e += 1
    }
  }

  // ── Medusa ──
  private final val OURO_N = 22
  private val _ouX = new Array[Float](OURO_N)
  private val _ouY = new Array[Float](OURO_N)

  /**
   * Medusa's venom bolt: one of the snakes of her hair, curled into a ring with its tail in its mouth —
   * the ouroboros — rolling as it flies, green with a pale belly and gold eyes, shedding flakes of stone
   * behind it.
   */
  private[projectiles] def drawOuroboros(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 41) * 0.35
    computeAllDynamics(proj, 0.4f, 0.75f, 0.35f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx
    val R = 14f * ds
    val dir = if (ndx < 0f) -1f else 1f
    // It rolls the way it flies (clockwise on screen to the right), its head leading round the ring
    val head = (phase * 0.9 * dir).toFloat
    val cx = sx; val cy = sy

    // Flakes of stone shed behind it
    var f = 0
    while (f < 5) {
      val t = loop(tick, 0.045, f / 5.0 + proj.id * 0.21)
      val lat = hash2(proj.id + 1, f) * (5f + 8f * t) * ds
      val gx = cx - ndx * (R + t * 30f * ds) + px * lat; val gy = cy - ndy * (R + t * 30f * ds) + py * lat + t * t * 10f * ds
      val gs = (2.2f - t) * ds + 0.5f
      _polyXs4(0) = gx; _polyYs4(0) = gy - gs
      _polyXs4(1) = gx + gs; _polyYs4(1) = gy
      _polyXs4(2) = gx; _polyYs4(2) = gy + gs * 0.8f
      _polyXs4(3) = gx - gs * 0.9f; _polyYs4(3) = gy
      sb.fillPolygon(_polyXs4, _polyYs4, 4, 0.55f, 0.56f, 0.55f, (1f - t) * a)
      f += 1
    }
    sb.fillOvalSoft(cx, cy, R * 2.3f * dynGlow, R * 2.2f * dynGlow, 0.45f, 0.85f, 0.4f, 0.24f * a, 0f, 16)
    // The ring: from just behind its head round to its tail tip, which is in its mouth
    var i = 0
    while (i < OURO_N) {
      val t = i.toFloat / (OURO_N - 1)
      val th = head - dir * (0.35f + t * 5.75f)
      _ouX(i) = cx + Math.cos(th).toFloat * R; _ouY(i) = cy + Math.sin(th).toFloat * R * 0.9f
      i += 1
    }
    i = 0
    while (i < OURO_N) { val t = i.toFloat / (OURO_N - 1); _pvW(i) = (7.2f * (1f - t * t * 0.7f)) * ds + 2.6f; _pvA(i) = 0.94f * a; i += 1 }
    sb.strokePolylineVar(_ouX, _ouY, _pvW, _pvA, OURO_N, 0.04f, 0.14f, 0.05f)
    i = 0
    while (i < OURO_N) { val t = i.toFloat / (OURO_N - 1); _pvW(i) = 7.2f * (1f - t * t * 0.7f) * ds; _pvA(i) = a; i += 1 }
    sb.strokePolylineVar(_ouX, _ouY, _pvW, _pvA, OURO_N, 0.3f, 0.6f, 0.28f)
    // Its pale belly round the inside of the ring, dark bands across its back
    i = 0
    while (i < OURO_N) {
      val dx = _ouX(i) - cx; val dy = _ouY(i) - cy
      val dl = Math.max(1e-3f, Math.sqrt(dx * dx + dy * dy).toFloat)
      val t = i.toFloat / (OURO_N - 1)
      _pvX(i) = _ouX(i) - dx / dl * 1.7f * ds * (1f - t * 0.6f); _pvY(i) = _ouY(i) - dy / dl * 1.7f * ds * (1f - t * 0.6f)
      _pvW(i) = 2.2f * ds * (1f - t * t * 0.7f); _pvA(i) = a
      i += 1
    }
    sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, OURO_N, 0.9f, 0.88f, 0.55f)
    i = 1
    while (i < OURO_N - 2) {
      val dx = _ouX(i) - cx; val dy = _ouY(i) - cy
      val dl = Math.max(1e-3f, Math.sqrt(dx * dx + dy * dy).toFloat)
      val w = 2.6f * ds * (1f - (i.toFloat / OURO_N) * 0.5f)
      sb.strokeLine(_ouX(i) + dx / dl * w * 0.2f, _ouY(i) + dy / dl * w * 0.2f, _ouX(i) + dx / dl * w, _ouY(i) + dy / dl * w,
        1.4f * ds, 0.12f, 0.3f, 0.1f, 0.85f * a)
      i += 2
    }
    // The head, biting its tail: along the ring, turned the way it goes round
    val hcx = cx + Math.cos(head).toFloat * R; val hcy = cy + Math.sin(head).toFloat * R * 0.9f
    val tx = -Math.sin(head).toFloat * dir; val ty = Math.cos(head).toFloat * 0.9f * dir
    val tl = Math.max(1e-3f, Math.sqrt(tx * tx + ty * ty).toFloat)
    headingFrame(hcx, hcy, tx / tl, ty / tl, 1.4f * ds)
    var m = ovalPts(-1f, 0f, 5.4f, 3.9f, 0f, 14)
    sb.strokePolygon(_gx, _gy, m, 2.6f, 0.04f, 0.14f, 0.05f, 0.94f * a)
    sb.fillPolygon(_gx, _gy, m, 0.34f, 0.66f, 0.3f, a)
    gLine(sb, -4.6f, 0f, 2.6f, 0f, 1.2f, 0.5f, 0.82f, 0.42f, 0.8f * a)
    var side = -1f
    while (side <= 1f) {
      m = ovalPts(0.4f, side * 2.2f, 1.3f, 0.95f, 0f, 10)
      sb.fillPolygon(_gx, _gy, m, 0.04f, 0.1f, 0.03f, a)
      m = ovalPts(0.4f, side * 2.2f, 0.9f, 0.65f, 0f, 10)
      sb.fillPolygon(_gx, _gy, m, 1f, 0.82f, 0.25f, a)
      side += 2f
    }
  }
}
