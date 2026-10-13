package com.gridgame.client.render.projectiles

import com.gridgame.common.model.Projectile
import com.gridgame.client.gl.ShapeBatch
import ProjectileKit._
import Glyphs._

/**
 * The undead and the damned, drawn as whoever cast it: the Revenant's own great helm with its crown of
 * cyan soul-fire, stolen life flowing into its visor; the Necromancer's skull in green fire, from the
 * head of her staff; the Lich's bony hand reaching out of its sleeve, its spike of grave-ice banded in
 * gold, and its phylactery drinking the souls round it — the Lich's navy, bone, gold and cyan, where it
 * had a red orb, a pale icicle and a green whirlpool; the Shadowfiend's third eye, flown on bat wings.
 */
private[render] object Necromancy {

  /** A crown of soul-fire off (x0, y0): `n` tongues spread `spread` across, streaming along (dx, dy),
    * in three layers (ink, colour, a pale core) — (r, g, b) is the flame, (cr, cg, cb) its heart. */
  private def soulFire(sb: ShapeBatch, x0: Float, y0: Float, dx: Float, dy: Float, n: Int, spread: Float,
                       len: Float, w: Float, phase: Double, id: Int, a: Float,
                       ir: Float, ig: Float, ib: Float, r: Float, g: Float, b: Float,
                       cr: Float, cg: Float, cb: Float): Unit = {
    val qx = -dy; val qy = dx
    var layer = 0
    while (layer < 3) {
      var j = 0
      while (j < n) {
        val u = if (n == 1) 0f else -1f + 2f * j / (n - 1)
        val flick = 0.78f + 0.22f * Math.sin(phase * 3.4 + j * 2.3).toFloat
        val l = len * (1f - 0.35f * Math.abs(u)) * flick * (0.85f + 0.3f * hash01(id, j))
        val sway = Math.sin(phase * 2.6 + j * 1.9).toFloat * w * 0.9f
        val k = if (layer == 0) 1f else if (layer == 1) 0.92f else 0.5f
        flameTongue(x0 + qx * u * spread, y0 + qy * u * spread, dx, dy, l * k,
          (if (layer == 0) w + 2.6f else w) * k, sway, a)
        (layer: @scala.annotation.switch) match {
          case 0 => sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 5, ir, ig, ib)
          case 1 => sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 5, r, g, b)
          case _ => sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 5, cr, cg, cb)
        }
        j += 1
      }
      layer += 1
    }
  }

  // ── The Revenant ──
  private val HELM = Array(-9f, -11f, 9f, -11f, 10.6f, -6.5f, 10.6f, 10f, -10.6f, 10f, -10.6f, -6.5f)
  private val HELM_SHADE = Array(3.5f, -11f, 9f, -11f, 10.6f, -6.5f, 10.6f, 10f, 3.5f, 10f)
  private val VISOR = Array(-9.4f, -2.4f, 9.6f, -2.4f, 9.6f, 0.9f, -9.4f, 0.9f)
  private val HELM_RIDGE = Array(-1.1f, -11f, 1.1f, -11f, 1.1f, 10f, -1.1f, 10f)

  /**
   * The Revenant's life drain: its own great helm, dented and rusting, the slit of its visor burning
   * cyan, a crown of cyan soul-fire streaming back off its top as it flies, and the life it drains as
   * red motes spiralling into the slit.
   */
  private[projectiles] def drawRevenantHelm(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 37) * 0.35
    computeAllDynamics(proj, 0.35f, 0.9f, 0.95f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val cx = sx; val cy = sy + Math.sin(phase * 0.5).toFloat * 1.2f * ds
    val k = 1.4f * ds

    sb.fillOvalSoft(cx, cy - 5f * ds, 32f * ds * dynGlow, 30f * ds * dynGlow, 0.3f, 0.9f, 0.95f, 0.24f * a, 0f, 16)
    fadeLine(sb, cx, cy, cx - ndx * 40f * ds, cy - ndy * 40f * ds, 12f * ds, 0.35f, 0.85f, 0.95f, 0.3f * a, 4)
    faceFrame(cx, cy, ndx, ndy, k, 0.25f)
    // The soul-fire off its crown, streaming back and up
    val fdx0 = -ndx * 0.75f; val fdy0 = -ndy * 0.75f - 0.85f
    val fl = Math.sqrt(fdx0 * fdx0 + fdy0 * fdy0).toFloat
    soulFire(sb, fx(0f, -10f), fy(0f, -10f), fdx0 / fl, fdy0 / fl, 6, 7.5f * k, 23f * k, 5.8f * k, phase, proj.id, a,
      0.02f, 0.18f, 0.22f, 0.25f, 0.85f, 0.95f, 0.85f, 1f, 1f)
    // The helm: steel, shaded on its far side, a ridge down its face, rust, rivets
    var n = pts(HELM)
    sb.strokePolygon(_gx, _gy, n, 3f, 0.08f, 0.08f, 0.1f, 0.92f * a)
    sb.fillPolygon(_gx, _gy, n, 0.6f, 0.62f, 0.66f, a)
    n = pts(HELM_SHADE)
    sb.fillPolygon(_gx, _gy, n, 0.44f, 0.46f, 0.52f, a)
    n = pts(HELM_RIDGE)
    sb.fillPolygon(_gx, _gy, n, 0.74f, 0.76f, 0.8f, a)
    gDot(sb, -6f, 5.5f, 1.8f, 1.3f, 0.62f, 0.34f, 0.18f, 0.9f * a, 8)
    gDot(sb, 6.5f, -7f, 1.4f, 1f, 0.62f, 0.34f, 0.18f, 0.85f * a, 6)
    gDot(sb, -7.5f, -8f, 0.9f, 0.9f, 0.9f, 0.92f, 0.95f, a, 6)
    gDot(sb, 7.8f, 7.5f, 0.9f, 0.9f, 0.9f, 0.92f, 0.95f, a, 6)
    // Breathing holes
    var h = 0
    while (h < 3) { gDot(sb, 4f + h * 1.9f, 4.5f + (h & 1) * 1.6f, 0.6f, 0.6f, 0.1f, 0.1f, 0.12f, a, 6); h += 1 }
    // The visor slit, and the soul-fire burning in it
    n = pts(VISOR)
    sb.fillPolygon(_gx, _gy, n, 0.03f, 0.04f, 0.06f, a)
    sb.fillOvalSoft(fx(0.5f, -0.7f), fy(0.5f, -0.7f), 13f * k, 4.5f * k, 0.3f, 0.95f, 1f, 0.5f * a, 0f, 12)
    gLine(sb, -6.2f, -0.7f, -1.4f, -0.7f, 1.6f, 0.7f, 1f, 1f, a)
    gLine(sb, 2.4f, -0.7f, 6.6f, -0.7f, 1.6f, 0.7f, 1f, 1f, a)
    // Life drained into it: red motes spiralling in to the slit
    val vx = fx(0.5f, -0.7f); val vy = fy(0.5f, -0.7f)
    var m = 0
    while (m < 5) {
      val t = loop(tick, 0.03, m / 5.0 + proj.id * 0.23)
      val ang = m * 1.26 + t * 2.6 + proj.id
      val rad = (1f - t) * 24f * ds
      val mx = vx + Math.cos(ang).toFloat * rad; val my = vy + Math.sin(ang).toFloat * rad * 0.75f
      val ms = (1.4f + 1.4f * (1f - t)) * ds
      val ma = a * Math.min(1f, t * 4f) * (0.4f + 0.6f * (1f - t))
      sb.fillOval(mx, my, ms + 1f, ms + 1f, 0.25f, 0.02f, 0.05f, 0.6f * ma, 8)
      sb.fillOval(mx, my, ms, ms, 0.95f, 0.2f, 0.25f, ma, 8)
      sb.fillOval(mx - ms * 0.3f, my - ms * 0.3f, ms * 0.4f, ms * 0.4f, 1f, 0.8f, 0.8f, ma, 6)
      m += 1
    }
  }

  // ── The Necromancer ──
  private val JAW = Array(-5.6f, 3.2f, 7.4f, 3.2f, 6.6f, 8.6f, 3.6f, 10.6f, -3f, 10.2f, -5.2f, 7.4f)

  /**
   * The Necromancer's death bolt: the skull off the head of her staff, alight with the same green
   * fire — the flames streaming back off its crown, the sockets burning green, the jaw chattering —
   * with green sparks falling away behind and a breath of her purple about it.
   */
  private[projectiles] def drawGreenFlameSkull(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 31) * 0.35
    computeAllDynamics(proj, 0.35f, 0.9f, 0.4f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val k = 1.3f * ds

    sb.fillOvalSoft(sx - ndx * 8f * ds, sy - 4f * ds, 34f * ds * dynGlow, 30f * ds * dynGlow, 0.55f, 0.25f, 0.75f, 0.18f * a, 0f, 16)
    sb.fillOvalSoft(sx, sy, 25f * ds * dynGlow, 23f * ds * dynGlow, 0.3f, 0.95f, 0.4f, 0.3f * a, 0f, 16)
    faceFrame(sx, sy, ndx, ndy, k, 0.3f)
    // Green fire streaming back off its crown
    val fdx0 = -ndx * 0.85f - _fm * 0.15f; val fdy0 = -ndy * 0.85f - 0.55f
    val fl = Math.sqrt(fdx0 * fdx0 + fdy0 * fdy0).toFloat
    soulFire(sb, fx(-2.5f, -5f), fy(-2.5f, -5f), fdx0 / fl, fdy0 / fl, 7, 8.5f * k, 30f * k, 7.4f * k, phase, proj.id + 7, a,
      0.03f, 0.16f, 0.05f, 0.3f, 0.86f, 0.32f, 0.82f, 1f, 0.7f)
    // Sparks falling away
    Orbs.shedMotes(sb, sx, sy, ndx, ndy, 13f * ds, 4, 0.55f, 1f, 0.5f, a, tick, proj.id)
    // The skull: cranium and jaw inked as one, bone shaded under its brow and down its far side
    val chatter = Math.abs(Math.sin(phase * 1.9).toFloat) * 1.1f
    var n = ovalPts(0f, -1.6f, 9.6f, 8.8f, 0f, 18)
    sb.strokePolygon(_gx, _gy, n, 2.8f, 0.1f, 0.09f, 0.05f, 0.92f * a)
    n = ptsAt(JAW, 0f, chatter, 1f)
    sb.strokePolygon(_gx, _gy, n, 2.8f, 0.1f, 0.09f, 0.05f, 0.92f * a)
    sb.fillPolygon(_gx, _gy, n, 0.8f, 0.78f, 0.68f, a)
    n = ovalPts(0f, -1.6f, 9.6f, 8.8f, 0f, 18)
    sb.fillPolygon(_gx, _gy, n, 0.93f, 0.91f, 0.82f, a)
    sb.fillOvalSoft(fx(-4.5f, 2f), fy(-4.5f, 2f), 5f * k, 6f * k, 0.66f, 0.64f, 0.55f, 0.6f * a, 0f, 12)
    gDot(sb, -3f, -6.5f, 2.6f, 1.2f, 1f, 1f, 0.95f, 0.8f * a, 8)
    // Teeth
    gLine(sb, -3.6f, 5.6f + chatter * 0.5f, 6.2f, 5.6f + chatter * 0.5f, 1.1f, 0.15f, 0.12f, 0.08f, a)
    var t = 0
    while (t < 4) {
      val tx = -1.8f + t * 2.3f
      gLine(sb, tx, 3.8f, tx, 7.6f + chatter, 0.8f, 0.15f, 0.12f, 0.08f, 0.9f * a)
      t += 1
    }
    // Nose
    _gx(0) = fx(2.4f, 1.4f); _gy(0) = fy(2.4f, 1.4f)
    _gx(1) = fx(4.4f, 1.4f); _gy(1) = fy(4.4f, 1.4f)
    _gx(2) = fx(3.3f, 3.6f); _gy(2) = fy(3.3f, 3.6f)
    sb.fillPolygon(_gx, _gy, 3, 0.12f, 0.1f, 0.06f, a)
    // The sockets, burning green
    var e = 0
    while (e < 2) {
      val elx = if (e == 0) -1.6f else 5.4f
      val erx = if (e == 0) 3.1f else 2.4f
      n = ovalPts(elx, -1.4f, erx, 3.2f, 0f, 12)
      sb.fillPolygon(_gx, _gy, n, 0.04f, 0.08f, 0.04f, a)
      sb.fillOvalSoft(fx(elx, -1.2f), fy(elx, -1.2f), 4.2f * k, 4.2f * k, 0.35f, 1f, 0.4f, 0.65f * a, 0f, 10)
      gDot(sb, elx + 0.3f, -1.2f, 1.1f, 1.3f, 0.85f, 1f, 0.75f, a, 6)
      e += 1
    }
  }

  // ── The Lich ──
  private val _fingerX = Array(2f, 2.4f, 2.2f, 1.6f)
  private val _fingerY = Array(-4.4f, -1.5f, 1.4f, 4.2f)
  private val _fingerA = Array(-0.22f, -0.07f, 0.08f, 0.24f)
  private val _fingerL = Array(1f, 1.12f, 1.05f, 0.82f)
  private val _palm = new Array[Float](8)

  /** One bone from (x0, y0) to (x1, y1) in the frame: `pass` 0 is its ink, 1 the bone. */
  private def bone(sb: ShapeBatch, x0: Float, y0: Float, x1: Float, y1: Float, w: Float, pass: Int, a: Float): Unit =
    if (pass == 0) gLine(sb, x0, y0, x1, y1, w + 1.9f, 0.05f, 0.06f, 0.18f, 0.92f * a)
    else gLine(sb, x0, y0, x1, y1, w, 0.92f, 0.9f, 0.82f, a)

  /**
   * The Lich's leech bolt: its own bony hand reaching out of its navy sleeve — a gold cuff at the
   * wrist, a cyan gem on one finger, the fingers clutching and opening — with the life it is leeching
   * swirling in round it as red motes.
   */
  private[projectiles] def drawLichHand(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 29) * 0.35
    computeAllDynamics(proj, 0.45f, 0.9f, 1f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val k = 1.45f * ds

    sb.fillOvalSoft(sx - ndx * 9f * k, sy - ndy * 9f * k, 24f * ds * dynGlow, 20f * ds * dynGlow, 0.35f, 0.9f, 1f, 0.26f * a, 0f, 16)
    headingFrame(sx - ndx * 11f * k, sy - ndy * 11f * k, ndx, ndy, k)
    // The sleeve it reaches out of, its hem ragged, its cuff gold
    val n = 6
    trailPts(fx(-7f, 0f), fy(-7f, 0f), ndx, ndy, 30f * ds, n, 3f * ds, 1f, phase * 1.2, 15f * k, 9f * k, a, 0f, aEnd = 0.2f)
    trailLayer(n, 1f, 3f, 0.9f)
    sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, n, 0.03f, 0.04f, 0.14f)
    trailLayer(n, 1f, 0f, 1f)
    sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, n, 0.12f, 0.15f, 0.38f)
    trailLayer(n, 0.25f, 0f, 0.7f)
    trailShift(n, 0.2f, ndx, ndy)
    sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, n, 0.24f, 0.3f, 0.6f)
    gLine(sb, -7.6f, -7.2f, -7.6f, 7.2f, 2.6f, 0.08f, 0.06f, 0.02f, 0.9f * a)
    gLine(sb, -7.6f, -6.6f, -7.6f, 6.6f, 1.6f, 0.92f, 0.74f, 0.32f, a)

    // The palm: a pale light of the hand that was, under its bones
    _palm(0) = fx(-6f, -3.2f); _palm(1) = fy(-6f, -3.2f)
    _palm(2) = fx(2.6f, -5.6f); _palm(3) = fy(2.6f, -5.6f)
    _palm(4) = fx(2.6f, 5.4f); _palm(5) = fy(2.6f, 5.4f)
    _palm(6) = fx(-6f, 3.2f); _palm(7) = fy(-6f, 3.2f)
    var q = 0
    while (q < 4) { _gx(q) = _palm(q * 2); _gy(q) = _palm(q * 2 + 1); q += 1 }
    sb.fillPolygon(_gx, _gy, 4, 0.55f, 0.92f, 1f, 0.45f * a)
    // The hand: bones, ink first then bone, the fingers clutching and opening, knobbed at every joint
    val clutch = 0.5f + 0.5f * Math.sin(phase * 1.3).toFloat
    var pass = 0
    while (pass < 2) {
      // Wrist to knuckles
      var f = 0
      while (f < 4) {
        bone(sb, -5.5f, _fingerY(f) * 0.35f, _fingerX(f), _fingerY(f), 1.7f, pass, a)
        f += 1
      }
      // The fingers: three joints each, curling in as they clutch, a claw at each tip
      f = 0
      while (f < 4) {
        var x = _fingerX(f); var y = _fingerY(f)
        var ang = _fingerA(f) * (1f - 0.6f * clutch)
        var j = 0
        while (j < 3) {
          val sl = (if (j == 0) 4.2f else if (j == 1) 3.2f else 2.6f) * _fingerL(f) * (1f - 0.28f * clutch * j)
          ang += (-_fingerY(f) * 0.06f) * clutch
          val x1 = x + Math.cos(ang).toFloat * sl; val y1 = y + Math.sin(ang).toFloat * sl
          bone(sb, x, y, x1, y1, 1.9f - j * 0.3f, pass, a)
          if (pass == 1) gDot(sb, x, y, 1.15f, 1.15f, 0.97f, 0.95f, 0.88f, a, 8)
          if (j == 2) {
            val cxx = Math.cos(ang).toFloat; val cyy = Math.sin(ang).toFloat
            _gx(0) = fx(x1 - cyy * 0.9f, y1 + cxx * 0.9f); _gy(0) = fy(x1 - cyy * 0.9f, y1 + cxx * 0.9f)
            _gx(1) = fx(x1 + cxx * 2.4f, y1 + cyy * 2.4f); _gy(1) = fy(x1 + cxx * 2.4f, y1 + cyy * 2.4f)
            _gx(2) = fx(x1 + cyy * 0.9f, y1 - cxx * 0.9f); _gy(2) = fy(x1 + cyy * 0.9f, y1 - cxx * 0.9f)
            if (pass == 0) sb.strokePolygon(_gx, _gy, 3, 1.8f, 0.05f, 0.06f, 0.18f, 0.9f * a)
            else sb.fillPolygon(_gx, _gy, 3, 0.36f, 0.4f, 0.55f, a)
          }
          x = x1; y = y1
          j += 1
        }
        f += 1
      }
      // The thumb
      bone(sb, -3.6f, 3.2f, -0.6f, 6.6f, 1.8f, pass, a)
      bone(sb, -0.6f, 6.6f, 2.6f - clutch * 1.4f, 7.6f - clutch * 1.6f, 1.6f, pass, a)
      pass += 1
    }
    // Knuckles glowing cyan, and the gem on its ring
    var f = 0
    while (f < 4) { gDot(sb, _fingerX(f), _fingerY(f), 0.9f, 0.9f, 0.5f, 0.95f, 1f, 0.9f * a, 6); f += 1 }
    gDot(sb, _fingerX(1) + 1.6f, _fingerY(1), 1.2f, 1.6f, 0.92f, 0.74f, 0.32f, a, 8)
    sb.fillOvalSoft(fx(_fingerX(1) + 1.6f, _fingerY(1)), fy(_fingerX(1) + 1.6f, _fingerY(1)), 3.6f * k, 3.6f * k,
      0.4f, 1f, 1f, 0.7f * a, 0f, 8)
    gDot(sb, _fingerX(1) + 1.6f, _fingerY(1), 0.7f, 0.9f, 0.7f, 1f, 1f, a, 6)
    // The life it leeches, swirling in round it
    var m = 0
    while (m < 4) {
      val t = loop(tick, 0.028, m / 4.0 + proj.id * 0.19)
      val ang = m * 1.57 + t * 3.0 + proj.id
      val rad = (1f - t) * 22f * ds + 3f * ds
      val mx = sx - ndx * 9f * k + Math.cos(ang).toFloat * rad; val my = sy - ndy * 9f * k + Math.sin(ang).toFloat * rad * 0.7f
      val ms = (1.2f + 1.2f * (1f - t)) * ds
      val ma = a * Math.min(1f, t * 4f) * (1f - t * 0.5f)
      sb.fillOval(mx, my, ms + 0.9f, ms + 0.9f, 0.25f, 0.02f, 0.05f, 0.6f * ma, 8)
      sb.fillOval(mx, my, ms, ms, 0.95f, 0.22f, 0.28f, ma, 8)
      m += 1
    }
  }

  private val ICE = Array(-10f, 0f, -2f, -3.8f, 14f, 0f, -2f, 3.8f)
  private val ICE_LIT = Array(-10f, 0f, -2f, -3.8f, 14f, 0f)
  private val ICE_BAND = Array(-6.4f, -2.4f, -4f, -3.1f, -4f, 3.1f, -6.4f, 2.4f)
  private val _shard = new Array[Float](8)

  /** A lesser shard off the spike's back, `side` ±1, angled back and out: a kite, bulging on its
    * outer side. */
  private def sideShard(side: Float, len: Float): Unit = {
    val dx = -Math.cos(0.55).toFloat; val dy = Math.sin(0.55).toFloat * side
    val qx = -dy * side; val qy = dx * side
    val bx = -3.5f; val by = side * 2.2f
    _shard(0) = bx - qx * 1.6f; _shard(1) = by - qy * 1.6f
    _shard(2) = bx + dx * len * 0.45f - qx * 2f; _shard(3) = by + dy * len * 0.45f - qy * 2f
    _shard(4) = bx + dx * len; _shard(5) = by + dy * len
    _shard(6) = bx + qx * 1.6f; _shard(7) = by + qy * 1.6f
  }

  /**
   * The Lich's frost shard: a spike of grave-ice — near black, cold light running down its heart — with
   * lesser shards off its back and a band of gold carved with its runes, shedding frost as it flies.
   */
  private[projectiles] def drawGraveIce(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 23) * 0.35
    computeAllDynamics(proj, 0.35f, 0.75f, 1f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val k = 1.6f * ds

    // Frost breathing off it
    var p = 0
    while (p < 3) {
      val t = loop(tick, 0.04, p / 3.0 + proj.id * 0.31)
      val lat = hash2(proj.id + 9, p) * 6f * ds
      val fx0 = sx - ndx * (10f + t * 30f) * k - ndy * lat; val fy0 = sy - ndy * (10f + t * 30f) * k + ndx * lat
      sb.fillOvalSoft(fx0, fy0 - t * 4f * ds, (4f + 6f * t) * ds, (3.4f + 4.5f * t) * ds, 0.75f, 0.95f, 1f, 0.42f * (1f - t) * a, 0f, 10)
      p += 1
    }
    sb.fillOvalSoft(sx - ndx * 12f * k, sy - ndy * 12f * k, 24f * ds * dynGlow, 16f * ds * dynGlow, 0.35f, 0.75f, 1f, 0.26f * a, 0f, 14)
    headingFrame(sx - ndx * 14f * k, sy - ndy * 14f * k, ndx, ndy, k)
    // Lesser shards off its back, behind it
    var side = -1f
    while (side <= 1f) {
      sideShard(side, 8.5f)
      val n = pts(_shard)
      sb.strokePolygon(_gx, _gy, n, 2.2f, 0.02f, 0.03f, 0.1f, 0.9f * a)
      sb.fillPolygon(_gx, _gy, n, 0.18f, 0.27f, 0.55f, a)
      side += 2f
    }
    // The spike: ink, its dark body, the lit upper facet, its glowing heart
    var n = pts(ICE)
    sb.strokePolygon(_gx, _gy, n, 2.6f, 0.02f, 0.03f, 0.1f, 0.94f * a)
    sb.fillPolygon(_gx, _gy, n, 0.12f, 0.16f, 0.38f, a)
    n = pts(ICE_LIT)
    sb.fillPolygon(_gx, _gy, n, 0.3f, 0.44f, 0.76f, a)
    val pulse = 0.75f + 0.25f * Math.sin(phase * 1.4).toFloat
    gLine(sb, -7f, 0f, 11.5f, 0f, 2.2f, 0.35f, 0.9f, 1f, 0.6f * a * pulse)
    gLine(sb, -6f, 0f, 10.5f, 0f, 0.9f, 0.85f, 1f, 1f, a)
    // The gold band and its runes
    n = pts(ICE_BAND)
    sb.strokePolygon(_gx, _gy, n, 1.8f, 0.12f, 0.08f, 0.02f, 0.9f * a)
    sb.fillPolygon(_gx, _gy, n, 0.9f, 0.72f, 0.3f, a)
    gLine(sb, -5.6f, -1.4f, -4.8f, 0f, 0.6f, 0.35f, 0.2f, 0.05f, a)
    gLine(sb, -4.8f, 0f, -5.6f, 1.4f, 0.6f, 0.35f, 0.2f, 0.05f, a)
    // A glint at the point
    sb.fillStarFlare(fx(12f, -0.4f), fy(12f, -0.4f), 6f * ds * pulse, 1.4f, 0.8f, 0.6f, 0.85f, 1f, 1f, 0.9f * a)
  }

  private val PHY_GEM = Array(0f, -10.5f, 4.6f, -6f, 4.6f, 6f, 0f, 10.5f, -4.6f, 6f, -4.6f, -6f)
  private val PHY_TOP = Array(-6f, -9.2f, 6f, -9.2f, 4f, -12.6f, 2.2f, -11.4f, 0f, -16f, -2.2f, -11.4f, -4f, -12.6f)
  private val PHY_BOTTOM = Array(-6f, 9.2f, 6f, 9.2f, 3.4f, 12.8f, 0f, 14.6f, -3.4f, 12.8f)

  /** The cage's bars: those at the back (`front` false) before the gem, the rest after it. */
  private def cageBars(sb: ShapeBatch, turn: Double, front: Boolean, a: Float): Unit = {
    var i = 0
    while (i < 4) {
      val th = turn + i * (Math.PI / 2)
      val inFront = Math.cos(th) > 0
      if (inFront == front) {
        val bx = Math.sin(th).toFloat * 5.4f
        gLine(sb, bx, -9f, bx, 9f, 2.4f, 0.12f, 0.08f, 0.02f, 0.9f * a)
        gLine(sb, bx, -9f, bx, 9f, 1.3f, if (front) 0.95f else 0.66f, if (front) 0.78f else 0.52f,
          if (front) 0.36f else 0.22f, a)
      }
      i += 1
    }
  }

  /**
   * The Lich's soul drain: its phylactery — a cyan soul-gem caged in gold, a crown of gold on it, a soul
   * turning inside — and the souls it drinks as wisps spiralling in to it.
   */
  private[projectiles] def drawPhylactery(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 41) * 0.35
    computeAllDynamics(proj, 0.4f, 0.9f, 1f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val cx = sx; val cy = sy + Math.sin(phase * 0.5).toFloat * 1.3f * ds
    val k = 1.4f * ds

    fadeLine(sb, cx, cy, cx - ndx * 34f * ds, cy - ndy * 34f * ds, 10f * ds, 0.4f, 0.9f, 1f, 0.3f * a, 4)
    sb.fillOvalSoft(cx, cy, 26f * ds * dynGlow, 26f * ds * dynGlow, 0.35f, 0.9f, 1f, 0.3f * a, 0f, 16)
    // The souls it drinks, spiralling in
    var w = 0
    while (w < 4) {
      val t = loop(tick, 0.022, w / 4.0 + proj.id * 0.17)
      val ang = w * 1.57 + t * 3.4 + proj.id
      val rad = (1f - t) * 30f * ds + 6f * ds
      val wx = cx + Math.cos(ang).toFloat * rad; val wy = cy + Math.sin(ang).toFloat * rad * 0.75f
      val ang2 = ang - 0.5
      val rad2 = rad + 6f * ds
      val tx = cx + Math.cos(ang2).toFloat * rad2; val ty = cy + Math.sin(ang2).toFloat * rad2 * 0.75f
      val wa = a * Math.min(1f, t * 3f) * (1f - t * t)
      fadeLine(sb, wx, wy, tx, ty, 4.2f * ds, 0.75f, 1f, 1f, 0.75f * wa, 3)
      sb.fillOval(wx, wy, 2.6f * ds + 1f, 2.3f * ds + 1f, 0.05f, 0.25f, 0.3f, 0.6f * wa, 8)
      sb.fillOval(wx, wy, 2.6f * ds, 2.3f * ds, 0.85f, 1f, 1f, wa, 8)
      w += 1
    }
    frame(cx, cy, Math.sin(phase * 0.4).toFloat * 0.08f, k)
    val turn = phase * 0.45
    cageBars(sb, turn, front = false, a)
    // The gem: ink, cyan, facets, the soul turning inside
    var n = pts(PHY_GEM)
    sb.strokePolygon(_gx, _gy, n, 2.6f, 0.02f, 0.14f, 0.2f, 0.92f * a)
    sb.fillPolygon(_gx, _gy, n, 0.3f, 0.82f, 0.95f, a)
    val fa = Math.sin(turn * 2).toFloat * 2.6f
    gLine(sb, fa, -9f, fa, 9f, 1.1f, 0.75f, 1f, 1f, 0.7f * a)
    gLine(sb, fa - 3f, -6f, fa - 3f, 6f, 0.8f, 0.15f, 0.55f, 0.7f, 0.6f * a)
    sb.fillOvalSoft(cx, cy, 6f * k, 9f * k, 0.85f, 1f, 1f, 0.75f * a, 0f, 12)
    val sa = phase * 1.3
    gDot(sb, Math.cos(sa).toFloat * 1.6f, Math.sin(sa).toFloat * 3.2f, 1.6f, 1.6f, 1f, 1f, 1f, a, 8)
    // Its gold: the crown on it and the foot under it, inked as one
    n = pts(PHY_TOP)
    sb.strokePolygon(_gx, _gy, n, 2.4f, 0.12f, 0.08f, 0.02f, 0.92f * a)
    n = pts(PHY_BOTTOM)
    sb.strokePolygon(_gx, _gy, n, 2.4f, 0.12f, 0.08f, 0.02f, 0.92f * a)
    sb.fillPolygon(_gx, _gy, n, 0.86f, 0.68f, 0.28f, a)
    n = pts(PHY_TOP)
    sb.fillFan(fx(0f, -10.5f), fy(0f, -10.5f), _gx, _gy, n, 0.92f, 0.75f, 0.32f, a)
    gDot(sb, 0f, -10.6f, 1.2f, 1f, 0.45f, 1f, 1f, a, 6)
    cageBars(sb, turn, front = true, a)
  }

  // ── The Shadowfiend ──
  // A bat wing about its shoulder: the leading edge up to the wrist, its fingertips and the scallops
  // between them, back to the body
  private val WING_X = Array(-1f, 5f, 13f, 10f, 15f, 10f, 12f, 5f, 0f)
  private val WING_Y = Array(-3f, -9f, -8f, -4.2f, -1f, 0.6f, 5f, 2.6f, 2.4f)

  /**
   * The Shadowfiend's death bolt: its third eye, torn out and flown on its own little bat wings — pink,
   * slit-pupilled, bloodshot, two stubby horns over it, looking where it flies and blinking now and
   * then — with a devil's tail whipping behind it.
   */
  private[projectiles] def drawFiendEye(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 47) * 0.35
    computeAllDynamics(proj, 0.85f, 0.35f, 0.75f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val cx = sx; val cy = sy + Math.sin(phase * 0.9).toFloat * 1.5f * ds
    val k = 1.2f * ds

    // Its tail, whipping behind, with the spade at its tip
    val n = 6
    trailPts(cx - ndx * 6f * k, cy + 4f * k, ndx, ndy, 30f * ds, n, 4f * ds, 1.2f, phase * 1.6, 3.2f * k, 1.4f * k, a, 2f * ds, aEnd = 1f)
    val tx = _pvX(n - 1); val ty = _pvY(n - 1)
    trailLayer(n, 1f, 2f, 0.9f)
    sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, n, 0.08f, 0.02f, 0.12f)
    trailLayer(n, 1f, 0f, 1f)
    sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, n, 0.36f, 0.16f, 0.5f)
    _gx(0) = tx + ndy * 3.6f * ds; _gy(0) = ty - ndx * 3.6f * ds
    _gx(1) = tx - ndx * 6f * ds; _gy(1) = ty - ndy * 6f * ds
    _gx(2) = tx - ndy * 3.6f * ds; _gy(2) = ty + ndx * 3.6f * ds
    _gx(3) = tx + ndx * 1.5f * ds; _gy(3) = ty + ndy * 1.5f * ds
    sb.strokePolygon(_gx, _gy, 4, 2f, 0.08f, 0.02f, 0.12f, 0.9f * a)
    sb.fillPolygon(_gx, _gy, 4, 0.36f, 0.16f, 0.5f, a)
    sb.fillOvalSoft(cx, cy, 24f * ds * dynGlow, 20f * ds * dynGlow, 0.9f, 0.35f, 0.75f, 0.24f * a, 0f, 16)

    faceFrame(cx, cy, ndx, ndy, k, 0.2f)
    // The wings, beating
    val flap = 0.5f + 0.5f * Math.sin(phase * 1.9).toFloat
    val wy = 0.35f + 0.65f * flap
    var side = -1f
    while (side <= 1f) {
      var i = 0
      while (i < 9) {
        val lx = side * (7.5f + WING_X(i) * (0.85f + 0.15f * flap))
        val ly = -1f + WING_Y(i) * wy - (1f - flap) * 2f
        _gx(i) = fx(lx, ly); _gy(i) = fy(lx, ly)
        i += 1
      }
      sb.strokePolygon(_gx, _gy, 9, 2.4f, 0.08f, 0.02f, 0.12f, 0.9f * a)
      sb.fillFan(fx(side * 7.5f, -1f), fy(side * 7.5f, -1f), _gx, _gy, 9, 0.3f, 0.13f, 0.44f, a)
      // its finger bones
      var b = 2
      while (b <= 6) {
        sb.strokeLine(fx(side * 7.5f, -1f), fy(side * 7.5f, -1f), _gx(b), _gy(b), 1.1f * k, 0.5f, 0.28f, 0.66f, a)
        b += 2
      }
      side += 2f
    }
    // Its horns
    side = -1f
    while (side <= 1f) {
      _gx(0) = fx(side * 2.8f, -7.4f); _gy(0) = fy(side * 2.8f, -7.4f)
      _gx(1) = fx(side * 6.4f, -6f); _gy(1) = fy(side * 6.4f, -6f)
      _gx(2) = fx(side * 6.6f, -12f); _gy(2) = fy(side * 6.6f, -12f)
      sb.strokePolygon(_gx, _gy, 3, 2f, 0.12f, 0.08f, 0.06f, 0.9f * a)
      sb.fillPolygon(_gx, _gy, 3, 0.9f, 0.84f, 0.74f, a)
      side += 2f
    }
    // The eye: ink, the white, veins, the pink iris looking ahead, its slit, a glint
    var m = ovalPts(0f, 0f, 8.8f, 8.2f, 0f, 18)
    sb.strokePolygon(_gx, _gy, m, 3f, 0.12f, 0.03f, 0.15f, 0.94f * a)
    sb.fillPolygon(_gx, _gy, m, 0.99f, 0.92f, 0.94f, a)
    sb.fillOvalSoft(fx(0f, 3f), fy(0f, 3f), 8f * k, 5f * k, 0.85f, 0.62f, 0.72f, 0.5f * a, 0f, 12)
    var v = 0
    while (v < 3) {
      val va = 2.3 + v * 1.7
      val c = Math.cos(va).toFloat; val s = Math.sin(va).toFloat
      gLine(sb, c * 8f, s * 7.5f, c * 5.6f + s * 0.8f, s * 5.2f - c * 0.8f, 0.6f, 0.85f, 0.2f, 0.3f, 0.85f * a)
      v += 1
    }
    val lookX = 2.2f * (if (ndx < -0.02f) -ndx else ndx) + 0.4f; val lookY = ndy * 1.8f
    m = ovalPts(lookX, lookY, 4.4f, 4.4f, 0f, 14)
    sb.fillPolygon(_gx, _gy, m, 0.95f, 0.36f, 0.62f, a)
    m = ovalPts(lookX, lookY, 1.1f, 3.4f, 0f, 10)
    sb.fillPolygon(_gx, _gy, m, 0.08f, 0.02f, 0.08f, a)
    gDot(sb, lookX - 1.6f, lookY - 1.8f, 1.1f, 0.9f, 1f, 1f, 1f, 0.95f * a, 6)
    // Blinking now and then: its purple lid sweeping down over it and back
    val bt = loop(tick, 0.012, proj.id * 0.37)
    if (bt < 0.08f) {
      val shut = Math.sin(bt / 0.08f * Math.PI).toFloat
      m = ovalPts(0f, -8.2f + 8.2f * shut, 9.2f, 8.4f * shut + 0.5f, 0f, 16)
      sb.fillPolygon(_gx, _gy, m, 0.32f, 0.14f, 0.46f, a)
    }
  }
}
