package com.gridgame.client.render.projectiles

import com.gridgame.common.model.Projectile
import com.gridgame.client.gl.ShapeBatch
import ProjectileKit._
import Glyphs._

/**
 * Ghosts, drawn as whoever sent them: the Wraith's soul as a little wraith — a hood with nothing in
 * it but two eyes, its cloak streaming back to a ragged hem; the Phantom's ghost-blade with the
 * afterimages of its blink behind it, and her half-mask gliding ahead of the shadow she steps out of;
 * the Poltergeist's crockery, possessed and flung; the Banshee's tear, ringing with the echoes it
 * carries; the Shade's grin, with nothing round it but a wisp of the dark. They were a teal orb, a
 * pale blue one, a void orb in rings, a blue rune orb and a dark blob with eyes.
 */
private[render] object Spirits {

  // ── The Wraith ──
  private val _peak = new Array[Float](6)

  /**
   * The Wraith's soul bolt: a little wraith of its own — a teal hood, its peak flopping back, nothing
   * in it but the dark and two cold slits of eyes, the cloak streaming back from under it to a hem
   * torn into tatters — and a soul mote or two falling away behind.
   */
  private[projectiles] def drawWraithHood(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 41) * 0.35
    computeAllDynamics(proj, 0.25f, 0.62f, 0.6f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx
    val cx = sx; val cy = sy + Math.sin(phase * 0.45).toFloat * 1.3f * ds

    sb.fillOvalSoft(cx, cy, 25f * ds * dynGlow, 21f * ds * dynGlow, 0.18f, 0.62f, 0.58f, 0.26f * a, 0f, 16)

    // The cloak, streaming back from under the hood to its hem
    val n = 7
    trailPts(cx - ndx * 4f * ds, cy + 6f * ds, ndx, ndy, 42f * ds, n, 5f * ds, 1.1f, phase * 1.4,
      23f * ds, 14f * ds, a, 3f * ds, aEnd = 0.8f)
    val ex = _pvX(n - 1); val ey = _pvY(n - 1)
    trailLayer(n, 1f, 3.2f, 0.88f)
    sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, n, 0.03f, 0.12f, 0.14f)
    trailLayer(n, 1f, 0f, 0.96f)
    sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, n, 0.15f, 0.43f, 0.45f)
    // The hem torn into tatters, fluttering
    var t = 0
    while (t < 3) {
      val lat = (t - 1) * 5f * ds
      val bx = ex + px * lat; val by = ey + py * lat
      val len = (6.5f + 3f * hash01(proj.id + 5, t) + 1.5f * Math.sin(phase * 1.7 + t * 2.1).toFloat) * ds
      val flick = Math.sin(phase * 2.1 + t * 1.3).toFloat * 2.2f * ds
      _gx(0) = bx + px * 2.4f * ds; _gy(0) = by + py * 2.4f * ds
      _gx(1) = bx - ndx * len + px * flick; _gy(1) = by - ndy * len + py * flick
      _gx(2) = bx - px * 2.4f * ds; _gy(2) = by - py * 2.4f * ds
      sb.strokePolygon(_gx, _gy, 3, 2.4f, 0.03f, 0.12f, 0.14f, 0.8f * a)
      sb.fillPolygon(_gx, _gy, 3, 0.15f, 0.43f, 0.45f, 0.86f * a)
      t += 1
    }
    // A lit fold down the cloak
    trailLayer(n, 0.2f, 0f, 0.55f)
    trailShift(n, 0.22f, ndx, ndy)
    sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, n, 0.32f, 0.68f, 0.66f)

    // The hood: its dome and its peak, inked as one, then the dark inside it
    faceFrame(cx, cy, ndx, ndy, 1.25f * ds, 0.22f)
    val flop = Math.sin(phase * 0.9).toFloat * 1.6f
    _peak(0) = -0.5f; _peak(1) = -10.6f
    _peak(2) = -13f + flop; _peak(3) = -19f + flop * 0.5f
    _peak(4) = -9.4f; _peak(5) = -3.2f
    var nn = ovalPts(0f, 0f, 10.6f, 11.6f, 0f, 18)
    sb.strokePolygon(_gx, _gy, nn, 3.2f, 0.03f, 0.12f, 0.14f, 0.92f * a)
    nn = pts(_peak)
    sb.strokePolygon(_gx, _gy, nn, 3.2f, 0.03f, 0.12f, 0.14f, 0.92f * a)
    sb.fillPolygon(_gx, _gy, nn, 0.2f, 0.5f, 0.52f, a)
    nn = ovalPts(0f, 0f, 10.6f, 11.6f, 0f, 18)
    sb.fillPolygon(_gx, _gy, nn, 0.2f, 0.5f, 0.52f, a)
    // lit across its crown, from the upper left as everything is
    sb.fillOvalSoft(cx - 3.6f * ds, cy - 6f * ds, 8.5f * ds, 6f * ds, 0.42f, 0.8f, 0.76f, 0.6f * a, 0f, 12)
    nn = ovalPts(2.4f, 1.8f, 7.2f, 7.8f, 0f, 16)
    sb.fillPolygon(_gx, _gy, nn, 0.02f, 0.04f, 0.06f, 0.98f * a)
    // Its eyes: two cold slits, slanted, glowing
    var e = 0
    while (e < 2) {
      val elx = if (e == 0) -0.2f else 5.4f
      val ely = if (e == 0) 1.7f else 1.3f
      val tilt = if (e == 0) 0.38f else -0.38f
      sb.fillOvalSoft(fx(elx, ely), fy(elx, ely), 6f * ds, 4.4f * ds, 0.4f, 1f, 0.92f, 0.5f * a, 0f, 10)
      nn = ovalPts(elx, ely, if (e == 0) 2.5f else 2.1f, 0.95f, tilt, 10)
      sb.fillPolygon(_gx, _gy, nn, 0.78f, 1f, 0.96f, a)
      e += 1
    }
    Orbs.shedMotes(sb, cx, cy + 3f * ds, ndx, ndy, 11f * ds, 3, 0.6f, 1f, 0.92f, a, tick, proj.id)
  }

  // ── The Phantom ──
  private val PH_BLADE = Array(-3f, -2.6f, 10f, -1.6f, 17.5f, 0f, 10f, 1.6f, -3f, 2.6f)
  private val PH_EDGE = Array(-3f, 0f, 17.5f, 0f, 10f, 1.6f, -3f, 2.6f)
  private val PH_GUARD = Array(-5.4f, -6.4f, -3f, -5.6f, -3f, 5.6f, -5.4f, 6.4f)
  private val PH_GRIP = Array(-12.5f, -1.5f, -5.4f, -1.8f, -5.4f, 1.8f, -12.5f, 1.5f)
  private val PH_PARTS = Array(PH_BLADE, PH_GUARD, PH_GRIP)

  /**
   * The Phantom's ghost-blade: a slender silver dagger, point first, the afterimages of its blink
   * strung out behind it and fading, a wisp off its pommel and a glint running down its edge.
   */
  private[projectiles] def drawPhantomBlade(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 29) * 0.35
    computeAllDynamics(proj, 0.62f, 0.75f, 1f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val k = 1.6f * ds

    // Its point on the hitbox, the rest of it behind
    val bx = sx - ndx * 16f * k; val by = sy - ndy * 16f * k
    sb.fillOvalSoft(bx + ndx * 6f * k, by + ndy * 6f * k, 28f * ds * dynGlow, 19f * ds * dynGlow,
      0.55f, 0.7f, 1f, 0.24f * a, 0f, 14)
    headingFrame(bx, by, ndx, ndy, k)
    // The afterimages of its blink, furthest first
    var g = 3
    while (g >= 1) {
      val back = -10.5f * g - 1.5f * Math.sin(phase * 0.8 + g).toFloat
      val ga = a * (0.6f - g * 0.14f)
      var n = ptsAt(PH_BLADE, back, 0f, 1f)
      sb.fillPolygon(_gx, _gy, n, 0.6f, 0.74f, 1f, ga)
      n = ptsAt(PH_GUARD, back, 0f, 1f)
      sb.fillPolygon(_gx, _gy, n, 0.45f, 0.55f, 0.95f, ga * 0.8f)
      g -= 1
    }
    fadeLine(sb, fx(-13f, 0f), fy(-13f, 0f), fx(-46f, 0f), fy(-46f, 0f), 5f * ds, 0.7f, 0.82f, 1f, 0.55f * a, 4)
    // The dagger: inked as one, then the blade, its shaded edge and fuller, the guard, grip and pommel
    inkAll(sb, PH_PARTS, 2.6f, 0.06f, 0.08f, 0.22f, 0.9f * a)
    var n = pts(PH_BLADE)
    sb.fillPolygon(_gx, _gy, n, 0.86f, 0.92f, 1f, 0.96f * a)
    n = pts(PH_EDGE)
    sb.fillPolygon(_gx, _gy, n, 0.58f, 0.68f, 0.92f, 0.96f * a)
    gLine(sb, -2f, -0.25f, 11f, -0.25f, 0.7f, 1f, 1f, 1f, 0.9f * a)
    n = pts(PH_GUARD)
    sb.fillPolygon(_gx, _gy, n, 0.22f, 0.27f, 0.55f, a)
    n = pts(PH_GRIP)
    sb.fillPolygon(_gx, _gy, n, 0.12f, 0.13f, 0.28f, a)
    sb.fillOval(fx(-13.6f, 0f), fy(-13.6f, 0f), 2.6f * k + 1.3f, 2.6f * k + 1.3f, 0.06f, 0.08f, 0.22f, 0.9f * a, 10)
    sb.fillOval(fx(-13.6f, 0f), fy(-13.6f, 0f), 2.6f * k, 2.6f * k, 0.78f, 0.84f, 1f, a, 10)
    val gt = loop(tick, 0.035, proj.id * 0.13)
    sb.fillStarFlare(fx(-2f + gt * 18f, -1f), fy(-2f + gt * 18f, -1f), 6f * ds, 1.5f, 0.3f, 0.7f,
      1f, 1f, 1f, 0.85f * a * Math.sin(gt * Math.PI).toFloat)
  }

  private val MASK = Array(-0.6f, -13f, 4.5f, -12.2f, 8.6f, -8.8f, 10.6f, -3f, 10.2f, 3.6f, 7.8f, 9.4f,
    3.6f, 12.6f, -0.6f, 13.4f)
  private val MASK_NOSE = Array(-0.6f, -2.5f, -3.4f, 4.6f, -0.6f, 5.8f)
  private val MASK_PARTS = Array(MASK, MASK_NOSE)
  private val FRINGE = Array(-12.2f, -3f, -7.5f, -13.2f, 0.5f, -14.2f, -1.6f, -3.5f)

  /**
   * The Phantom's shadow haunt: her white half-mask gliding ahead of the shadow she will step out of —
   * the dark of her head behind it with its fringe swept over the other eye, a pale eye glowing in the
   * mask, and a cape of shadow billowing back from it.
   */
  private[projectiles] def drawPhantomMask(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 37) * 0.35
    computeAllDynamics(proj, 0.75f, 0.82f, 1f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val cx = sx; val cy = sy + Math.sin(phase * 0.5).toFloat * 1.2f * ds

    // The cape of shadow, billowing back
    val n = 7
    trailPts(cx - ndx * 2f * ds, cy + 3f * ds, ndx, ndy, 46f * ds, n, 6f * ds, 1f, phase * 1.1,
      27f * ds, 6f * ds, a, 4f * ds, aEnd = 0.1f)
    trailLayer(n, 1f, 3f, 0.8f)
    sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, n, 0.02f, 0.03f, 0.1f)
    trailLayer(n, 1f, 0f, 0.95f)
    sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, n, 0.1f, 0.12f, 0.3f)
    trailLayer(n, 0.3f, 0f, 0.6f)
    trailShift(n, 0.2f, ndx, ndy)
    sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, n, 0.24f, 0.28f, 0.56f)
    sb.fillOvalSoft(cx, cy, 24f * ds * dynGlow, 25f * ds * dynGlow, 0.6f, 0.72f, 1f, 0.22f * a, 0f, 16)

    faceFrame(cx, cy, ndx, ndy, 1.25f * ds, 0.15f)
    // The shadow she is made of, behind the mask: a soft dark, her fringe swept across it
    sb.fillOvalSoft(fx(-2f, 0f), fy(-2f, 0f), 15f * _fk, 16f * _fk, 0.05f, 0.06f, 0.17f, 0.92f * a, 0f, 18)
    var nn = pts(FRINGE)
    sb.strokePolygon(_gx, _gy, nn, 2.4f, 0.02f, 0.03f, 0.1f, 0.85f * a)
    sb.fillPolygon(_gx, _gy, nn, 0.15f, 0.17f, 0.42f, a)
    gLine(sb, -9f, -9.5f, -2.5f, -12.5f, 1.2f, 0.32f, 0.36f, 0.66f, 0.8f * a)
    // The ribbon it is tied on, fluttering back from its edge
    val rb = 5
    trailPts(fx(9.6f, -4f), fy(9.6f, -4f), ndx, ndy, 24f * ds, rb, 3f * ds, 1.4f, phase * 2.2, 2.6f * ds, 1.4f * ds, a, 2f * ds, aEnd = 0.4f)
    trailLayer(rb, 1f, 1.8f, 0.9f)
    sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, rb, 0.03f, 0.03f, 0.1f)
    trailLayer(rb, 1f, 0f, 1f)
    sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, rb, 0.24f, 0.28f, 0.6f)
    // The mask: porcelain, inked as one with its nose, shaded toward its edge and lit on the brow
    inkAll(sb, MASK_PARTS, 2.6f, 0.05f, 0.05f, 0.14f, 0.92f * a)
    fillAll(sb, MASK_PARTS, 0.95f, 0.95f, 0.97f, a)
    sb.fillOvalSoft(fx(7.6f, 2f), fy(7.6f, 2f), 4.4f * _fk, 9f * _fk, 0.6f, 0.64f, 0.8f, 0.55f * a, 0f, 12)
    gDot(sb, 2.8f, -9.2f, 2.6f, 1.1f, 1f, 1f, 1f, 0.85f * a, 8)
    // Its brow arched over the eye, its cheekbone under it
    gLine(sb, 1.2f, -6.4f, 4.6f, -7.2f, 1f, 0.55f, 0.58f, 0.72f, 0.9f * a)
    gLine(sb, 4.6f, -7.2f, 8.2f, -5.8f, 1f, 0.55f, 0.58f, 0.72f, 0.9f * a)
    gLine(sb, 3f, 3.4f, 8.6f, 2.2f, 0.9f, 0.62f, 0.65f, 0.78f, 0.8f * a)
    // The eye-hole, and her eye glowing in it
    nn = ovalPts(4.4f, -2.6f, 3.6f, 2.2f, -0.12f, 12)
    sb.fillPolygon(_gx, _gy, nn, 0.04f, 0.05f, 0.14f, a)
    sb.fillOvalSoft(fx(4.7f, -2.6f), fy(4.7f, -2.6f), 4.6f * _fk, 3.4f * _fk, 0.55f, 0.8f, 1f, 0.6f * a, 0f, 10)
    gDot(sb, 4.7f, -2.6f, 1.6f, 1.6f, 0.82f, 0.95f, 1f, a, 8)
  }

  // ── The Poltergeist ──
  private val CUP = Array(-7.5f, -5f, 7.5f, -5f, 6.2f, 2f, 3.6f, 5.6f, -3.6f, 5.6f, -6.2f, 2f)
  private val CUP_BAND = Array(-7f, -2.6f, 7f, -2.6f, 6.6f, -0.8f, -6.6f, -0.8f)
  private val FORK_HANDLE = Array(-15f, -1.4f, -3f, -0.9f, -3f, 0.9f, -15f, 1.4f)
  private val FORK_NECK = Array(-3f, -0.9f, 1.4f, -3f, 1.4f, 3f, -3f, 0.9f)
  private def tine(y: Float): Array[Float] = Array(1.4f, y - 0.6f, 9.6f, y - 0.45f, 9.6f, y + 0.45f, 1.4f, y + 0.6f)
  private val FORK_PARTS = Array(FORK_HANDLE, FORK_NECK, tine(-2.4f), tine(-0.8f), tine(0.8f), tine(2.4f))
  private val _cover = new Array[Float](8)

  /** The book's cover and page on one side (`side` ±1), opened by `w` and lifted by `lift`, into
    * _cover; `inset` takes the page in from the cover. */
  private def leaf(side: Float, w: Float, lift: Float, inset: Float): Unit = {
    _cover(0) = 0f; _cover(1) = -7.4f + inset
    _cover(2) = side * (w - inset); _cover(3) = -7f + lift + inset
    _cover(4) = side * (w - inset); _cover(5) = 7f + lift - inset
    _cover(6) = 0f; _cover(7) = 7.4f - inset
  }

  /**
   * The Poltergeist's soul bolt: whatever it had to hand — a teacup, a plate, a book flapping its
   * covers, a fork — possessed and flung, tumbling in a pale haunted glow with a wisp of ectoplasm
   * trailing it. Which one is the projectile's own (by its id), so a volley is a cupboard emptied.
   */
  private[projectiles] def drawPoltergeistThrow(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 43) * 0.35
    computeAllDynamics(proj, 0.75f, 0.75f, 1f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val kind = (proj.id & 0x7fffffff) % 4
    val dir = if (ndx < 0f) -1f else 1f
    val spin = (phase * (if (kind == 3) 0.6 else 0.3)).toFloat * dir

    // Ectoplasm trailing it, and the glow of whatever has hold of it
    val n = 6
    trailPts(sx, sy, ndx, ndy, 40f * ds, n, 5f * ds, 1.3f, phase * 1.3, 13f * ds, 3f * ds, 0.45f * a, 0f)
    trailLayer(n, 1f, 0f, 1f)
    sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, n, 0.78f, 0.78f, 1f)
    sb.fillOvalSoft(sx, sy, 22f * ds * dynGlow, 18f * ds * dynGlow, 0.55f, 0.4f, 1f, 0.36f * a, 0f, 14)

    val ir = 0.07f; val ig = 0.07f; val ib = 0.2f
    (kind: @scala.annotation.switch) match {
      case 0 =>
        // A teacup, its tea slopping out
        frame(sx, sy, spin, 1.6f * ds)
        val hx = fx(9.2f, -0.6f); val hy = fy(9.2f, -0.6f); val hr = 3.3f * _fk
        var nn = pts(CUP)
        sb.strokePolygon(_gx, _gy, nn, 9f, 0.58f, 0.42f, 1f, 0.6f * a)
        sb.strokeOval(hx, hy, hr, hr, 2.2f * _fk + 2.6f, ir, ig, ib, 0.9f * a, 12)
        sb.strokePolygon(_gx, _gy, nn, 3f, ir, ig, ib, 0.95f * a)
        sb.strokeOval(hx, hy, hr, hr, 2.2f * _fk, 0.36f, 0.56f, 0.95f, a, 12)
        sb.fillPolygon(_gx, _gy, nn, 0.36f, 0.56f, 0.95f, a)
        nn = pts(CUP_BAND)
        sb.fillPolygon(_gx, _gy, nn, 0.95f, 0.96f, 1f, a)
        nn = ovalPts(0f, -5f, 7.5f, 1.9f, 0f, 14)
        sb.fillPolygon(_gx, _gy, nn, 0.82f, 0.88f, 1f, a)
        nn = ovalPts(0f, -4.8f, 6.1f, 1.25f, 0f, 12)
        sb.fillPolygon(_gx, _gy, nn, 0.5f, 0.3f, 0.14f, a)
        var d = 0
        while (d < 3) {
          val t = loop(tick, 0.05, d / 3.0 + proj.id * 0.29)
          val dx0 = sx - ndx * (8f + t * 26f) * ds + hash2(proj.id, d) * 5f * ds
          val dy0 = sy - ndy * (8f + t * 26f) * ds + t * t * 10f * ds - 3f * ds
          val dr = (2.2f - t) * ds
          sb.fillOval(dx0, dy0, dr + 1f, dr * 1.2f + 1f, 0.16f, 0.08f, 0.03f, 0.8f * (1f - t) * a, 8)
          sb.fillOval(dx0, dy0, dr, dr * 1.2f, 0.58f, 0.36f, 0.17f, (1f - t) * a, 8)
          d += 1
        }
      case 1 =>
        // A plate, spinning flat like a thrown dish
        frame(sx, sy, 0f, 1.55f * ds)
        val sq = 0.42f + 0.2f * Math.sin(phase * 0.9).toFloat
        val turn = spin * 0.25f
        var nn = ovalPts(0f, 0f, 11f, 11f * sq, turn, 22)
        sb.strokePolygon(_gx, _gy, nn, 9f, 0.58f, 0.42f, 1f, 0.6f * a)
        sb.strokePolygon(_gx, _gy, nn, 3f, ir, ig, ib, 0.95f * a)
        sb.fillPolygon(_gx, _gy, nn, 0.96f, 0.96f, 0.98f, a)
        nn = ovalPts(0f, 0f, 8.4f, 8.4f * sq, turn, 20)
        sb.strokePolygon(_gx, _gy, nn, 1.7f * _fk, 0.22f, 0.4f, 0.86f, a)
        nn = ovalPts(0f, 0.5f * sq, 5f, 5f * sq, turn, 16)
        sb.fillPolygon(_gx, _gy, nn, 0.84f, 0.86f, 0.93f, a)
        val gl = spin * 2.2f
        val glx = Math.cos(gl).toFloat * 7f; val gly = Math.sin(gl).toFloat * 7f * sq
        sb.fillStarFlare(fx(glx, gly), fy(glx, gly), 4.5f * ds, 1.3f, 0f, 0.7f, 1f, 1f, 1f, 0.85f * a)
      case 2 =>
        // A book, flapping its covers like wings
        frame(sx, sy, spin * 0.35f, 1.5f * ds)
        val flap = 0.5f + 0.5f * Math.sin(phase * 1.6).toFloat
        val w = 11f * (0.35f + 0.65f * flap)
        val lift = -3.5f * (1f - flap)
        var side = -1f
        while (side <= 1f) {
          leaf(side, w, lift, 0f)
          var nn = pts(_cover)
          sb.strokePolygon(_gx, _gy, nn, 9f, 0.58f, 0.42f, 1f, 0.6f * a)
          sb.strokePolygon(_gx, _gy, nn, 3f, ir, ig, ib, 0.95f * a)
          sb.fillPolygon(_gx, _gy, nn, 0.56f, 0.15f, 0.15f, a)
          side += 2f
        }
        side = -1f
        while (side <= 1f) {
          leaf(side, w, lift, 1.3f)
          val nn = pts(_cover)
          sb.fillPolygon(_gx, _gy, nn, 0.97f, 0.93f, 0.82f, a)
          var l = 0
          while (l < 3) {
            val ly = -3.6f + l * 3f + lift * 0.4f
            gLine(sb, side * 2f, ly, side * (w - 3f), ly + lift * 0.3f, 0.7f, 0.45f, 0.4f, 0.35f, 0.8f * a)
            l += 1
          }
          side += 2f
        }
        gLine(sb, 0f, -7.4f, 0f, 7.4f, 1.4f, 0.86f, 0.7f, 0.3f, a)
      case _ =>
        // A fork, end over end
        frame(sx, sy, spin, 1.45f * ds)
        var p = 0
        while (p < FORK_PARTS.length) {
          val nn = pts(FORK_PARTS(p))
          sb.strokePolygon(_gx, _gy, nn, 8.5f, 0.58f, 0.42f, 1f, 0.6f * a)
          p += 1
        }
        inkAll(sb, FORK_PARTS, 3f, ir, ig, ib, 0.95f * a)
        fillAll(sb, FORK_PARTS, 0.74f, 0.77f, 0.86f, a)
        gLine(sb, -14f, -0.5f, -3.5f, -0.3f, 0.7f, 1f, 1f, 1f, 0.8f * a)
    }
    // Spooky sparkles, twinkling about it
    var s = 0
    while (s < 2) {
      val t = loop(tick, 0.03, s * 0.5 + proj.id * 0.11)
      val ang = s * Math.PI + proj.id + t * 1.6
      val gx = sx + Math.cos(ang).toFloat * 15f * ds; val gy = sy + Math.sin(ang).toFloat * 11f * ds
      sb.fillStarFlare(gx, gy, 6f * ds * Math.sin(t * Math.PI).toFloat, 1.6f, 0f, 1f, 0.86f, 0.86f, 1f, 0.9f * a)
      s += 1
    }
  }

  // ── The Banshee ──
  private val _tear = new Array[Float](24)

  /**
   * The Banshee's echo bolt: one of her tears, big and glassy, cold silver-lilac, round at its head and
   * drawn to a point behind, with the echoes of her cry ringing out round the path it has come along.
   */
  private[projectiles] def drawBansheeTear(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 31) * 0.35
    computeAllDynamics(proj, 0.78f, 0.8f, 1f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val R = 10.5f * ds

    // Its echoes, ringing out round the path behind it
    var e = 0
    while (e < 3) {
      val t = loop(tick, 0.022, e / 3.0 + proj.id * 0.17)
      val d = R * 1.4f + t * 40f * ds
      val ex = sx - ndx * d; val ey = sy - ndy * d
      val nn = rotEllipsePts(ex, ey, ndx, ndy, (2.6f + 2.4f * t) * ds, (9f + 10f * t) * ds, 18)
      sb.strokePolygon(_gx, _gy, nn, 3.6f * (1f - t) + 1.6f, 0.16f, 0.12f, 0.38f, 0.6f * (1f - t) * a)
      sb.strokePolygon(_gx, _gy, nn, 2.2f * (1f - t) + 0.7f, 0.76f, 0.7f, 1f, 0.9f * (1f - t) * a)
      e += 1
    }
    sb.fillOvalSoft(sx - ndx * R * 0.6f, sy - ndy * R * 0.6f, R * 2.4f * dynGlow, R * 2.1f * dynGlow,
      0.7f, 0.72f, 1f, 0.26f * a, 0f, 14)

    // The tear: a round head and a point behind, inked, glassy
    headingFrame(sx, sy, ndx, ndy, 1f)
    var i = 0
    while (i < 11) {
      val ang = -1.83 + i * 0.366
      _tear(i * 2) = Math.cos(ang).toFloat * R; _tear(i * 2 + 1) = Math.sin(ang).toFloat * R
      i += 1
    }
    _tear(22) = -R * 2.8f; _tear(23) = Math.sin(phase * 0.9).toFloat * R * 0.12f
    val nn = pts(_tear)
    sb.strokePolygon(_gx, _gy, nn, 3.2f, 0.14f, 0.11f, 0.36f, 0.95f * a)
    sb.fillPolygon(_gx, _gy, nn, 0.66f, 0.64f, 0.95f, 0.95f * a)
    // Glass: the light through it gathering low in the head, a darker rim behind, a hard glint
    sb.fillOvalSoft(fx(-R * 0.9f, 0f), fy(-R * 0.9f, 0f), R * 1.1f, R * 0.7f, 0.42f, 0.4f, 0.8f, 0.6f * a, 0f, 12)
    sb.fillOvalSoft(sx + R * 0.1f, sy + R * 0.3f, R * 0.62f, R * 0.5f, 1f, 1f, 1f, 0.7f * a, 0f, 12)
    sb.fillOval(sx - R * 0.36f, sy - R * 0.42f, R * 0.3f, R * 0.18f, 1f, 1f, 1f, 0.95f * a, 8)
    // Glints shed behind it
    var s = 0
    while (s < 2) {
      val t = loop(tick, 0.04, s * 0.5 + proj.id * 0.21)
      val lat = hash2(proj.id + 3, s) * R
      val gx = sx - ndx * (R * 2.5f + t * 26f * ds) - ndy * lat; val gy = sy - ndy * (R * 2.5f + t * 26f * ds) + ndx * lat
      sb.fillStarFlare(gx, gy, 5f * ds * (1f - t), 1.3f, 0.4f, 0.8f, 0.92f, 0.9f, 1f, 0.9f * (1f - t) * a)
      s += 1
    }
  }

  // ── The Shade ──
  private final val GRIN_N = 9
  private val _grin = new Array[Float](GRIN_N * 4)
  private val _tooth = new Array[Float](6)

  @inline private def lipUp(u: Float): Float = 1f - 5.2f * u * u
  @inline private def lipLow(u: Float, open: Float): Float = (1f + 6.5f * open) - (5.2f + 6.5f * open) * u * u

  /**
   * The Shade's shadow bolt: its grin, and nothing else of it — a wide crescent of a mouth full of
   * teeth, lips glowing lilac, two round eyes hanging over it looking the way it flies, in a wisp of
   * the dark streaming back. It bounces round corners grinning.
   */
  private[projectiles] def drawShadeGrin(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 47) * 0.35
    computeAllDynamics(proj, 0.45f, 0.2f, 0.7f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx
    val cx = sx; val cy = sy + Math.sin(phase * 0.55).toFloat * 1.4f * ds

    // Wisps of the dark streaming back
    var w = 0
    while (w < 3) {
      val side = (w - 1).toFloat
      val n = 6
      trailPts(cx + px * side * 7f * ds - ndx * 3f * ds, cy + py * side * 7f * ds - 2f * ds, ndx, ndy,
        (30f + 8f * hash01(proj.id, w)) * ds, n, 3.5f * ds, 1.1f, phase * 1.5 + w * 1.7, 9f * ds, 1.5f * ds,
        0.8f * a, 0f)
      trailLayer(n, 1f, 0f, 1f)
      sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, n, 0.12f, 0.04f, 0.2f)
      w += 1
    }
    sb.fillOvalSoft(cx, cy - 2f * ds, 30f * ds * dynGlow, 23f * ds * dynGlow, 0.5f, 0.25f, 0.85f, 0.22f * a, 0f, 16)
    sb.fillOvalSoft(cx, cy - 2f * ds, 22f * ds, 17f * ds, 0.06f, 0.01f, 0.1f, 0.6f * a, 0f, 16)

    frame(cx, cy, Math.sin(phase * 0.6).toFloat * 0.08f + clampF(ndx, -1f, 1f) * 0.08f, 1.2f * ds)
    // The grin: its upper lip left to right, then its lower lip back
    val open = 1f + 0.2f * Math.sin(phase * 0.9).toFloat
    var i = 0
    while (i < GRIN_N) {
      val u = -1f + 2f * i / (GRIN_N - 1)
      _grin(i * 2) = u * 13f; _grin(i * 2 + 1) = lipUp(u)
      _grin((GRIN_N + i) * 2) = -u * 13f; _grin((GRIN_N + i) * 2 + 1) = lipLow(-u, open)
      i += 1
    }
    val nn = pts(_grin)
    sb.strokePolygon(_gx, _gy, nn, 3.6f, 0.04f, 0f, 0.08f, 0.9f * a)
    sb.fillRibbon(_gx, _gy, nn, 0.11f, 0.02f, 0.17f, 0.98f * a)
    // Teeth, top and bottom
    var t = 0
    while (t < 6) {
      val x = -8.75f + t * 3.5f; val y = lipUp(x / 13f)
      _tooth(0) = x - 1.55f; _tooth(1) = y + 0.2f
      _tooth(2) = x + 1.55f; _tooth(3) = y + 0.2f
      _tooth(4) = x; _tooth(5) = y + 2.5f
      val m = pts(_tooth)
      sb.fillPolygon(_gx, _gy, m, 0.97f, 0.94f, 1f, a)
      t += 1
    }
    t = 0
    while (t < 5) {
      val x = -7f + t * 3.5f; val y = lipLow(x / 13f, open)
      _tooth(0) = x - 1.45f; _tooth(1) = y - 0.2f
      _tooth(2) = x + 1.45f; _tooth(3) = y - 0.2f
      _tooth(4) = x; _tooth(5) = y - 2.3f
      val m = pts(_tooth)
      sb.fillPolygon(_gx, _gy, m, 0.9f, 0.86f, 0.98f, a)
      t += 1
    }
    // Its lips, glowing
    pts(_grin)
    sb.strokePolygon(_gx, _gy, nn, 1.5f, 0.84f, 0.64f, 1f, 0.95f * a)
    // Its eyes, looking the way it flies
    var e = -1
    while (e <= 1) {
      val elx = e * 5.8f; val ely = -9.6f
      sb.fillOvalSoft(fx(elx, ely), fy(elx, ely), 8f * ds, 8f * ds, 0.7f, 0.45f, 1f, 0.45f * a, 0f, 12)
      var m = ovalPts(elx, ely, 3.9f, 4.3f, 0f, 14)
      sb.strokePolygon(_gx, _gy, m, 2.6f, 0.04f, 0f, 0.08f, 0.9f * a)
      sb.fillPolygon(_gx, _gy, m, 0.8f, 0.64f, 1f, a)
      m = ovalPts(elx + ndx * 1.2f, ely + ndy * 1.1f, 1.9f, 2.3f, 0f, 10)
      sb.fillPolygon(_gx, _gy, m, 0.16f, 0.03f, 0.26f, a)
      gDot(sb, elx - 1.3f, ely - 1.6f, 1f, 0.8f, 1f, 1f, 1f, 0.9f * a, 6)
      e += 2
    }
  }
}
