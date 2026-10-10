package com.gridgame.client.render.projectiles

import com.gridgame.common.model.Projectile
import com.gridgame.client.gl.ShapeBatch
import ProjectileKit._

/**
 * Sound, drawn as whoever made it: the Bard's quaver dancing down a ribbon of stave, the Musician's
 * power chord crackling over the waveform it leaves, the Sphinx's riddle as the Eye of Horus, the
 * Harpy's screech as a volley of razor feathers, the Banshee's keening as her own face with her hair streaming
 * behind it. Every one of them was the same lilac crescent, with ripples behind it.
 *
 * Notes, the eye and the face are glyphs: they stand up facing the camera (they fly, over walls,
 * with a shadow beneath) rather than lying in the ground plane, and are drawn in a frame of their
 * own ([[frame]]) so they can dance and turn. Each is inked all round and filled after, so the ink
 * frames it on sand and snow and its colour carries it on dark stone.
 */
private[render] object Songs {
  // Scratch for glyph outlines and the lines behind them
  private val _gx = new Array[Float](24)
  private val _gy = new Array[Float](24)
  private val _lx = new Array[Float](24)
  private val _ly = new Array[Float](24)

  // The frame a glyph is drawn in: its centre on screen, turned by (_fc, _fs), scaled by _fk, and
  // mirrored left-right by _fm (±1) so an eye can look the way it flies
  private var _fx = 0f; private var _fy = 0f
  private var _fc = 1f; private var _fs = 0f; private var _fk = 1f; private var _fm = 1f

  private def frame(cx: Float, cy: Float, rot: Float, k: Float, mirror: Float = 1f): Unit = {
    _fx = cx; _fy = cy; _fc = Math.cos(rot).toFloat; _fs = Math.sin(rot).toFloat; _fk = k; _fm = mirror
  }
  @inline private def fx(lx: Float, ly: Float): Float = _fx + (lx * _fm * _fc - ly * _fs) * _fk
  @inline private def fy(lx: Float, ly: Float): Float = _fy + (lx * _fm * _fs + ly * _fc) * _fk

  /** A tilted ellipse in the glyph frame as a convex polygon of `n` points in _gx/_gy. */
  private def ovalPts(cx: Float, cy: Float, rx: Float, ry: Float, tilt: Float, n: Int): Unit = {
    val ct = Math.cos(tilt).toFloat; val st = Math.sin(tilt).toFloat
    var i = 0
    while (i < n) {
      val t = i * (2 * Math.PI / n)
      val ex = Math.cos(t).toFloat * rx; val ey = Math.sin(t).toFloat * ry
      val lx = cx + ex * ct - ey * st; val ly = cy + ex * st + ey * ct
      _gx(i) = fx(lx, ly); _gy(i) = fy(lx, ly)
      i += 1
    }
  }

  /** A line in the glyph frame, `w` glyph units wide. */
  @inline private def gLine(sb: ShapeBatch, x0: Float, y0: Float, x1: Float, y1: Float, w: Float,
                            r: Float, g: Float, b: Float, a: Float): Unit =
    sb.strokeLine(fx(x0, y0), fy(x0, y0), fx(x1, y1), fy(x1, y1), w * _fk, r, g, b, a)

  // ── Notes ──
  // A glyph unit is a note head's half-width. The quaver's head sits low left, its stem rises
  // from the head's right edge and its flag curls off the top; the beamed pair is two of them a
  // little apart, the second a step higher, stems joined by a beam.
  private final val HEAD_TILT = -0.38f
  private val FLAG_X = Array(0.30f, 0.95f, 1.25f, 0.95f)
  private val FLAG_Y = Array(-2.1f, -1.55f, -0.85f, -0.2f)

  /**
   * A note in the current frame: a quaver, or with `beamed` two of them joined by a beam. `pass` 0
   * is the ink (every part fattened by `ink` glyph units), 1 the body, 2 the glints.
   */
  private def noteGlyph(sb: ShapeBatch, beamed: Boolean, pass: Int, ink: Float,
                        r: Float, g: Float, b: Float, a: Float): Unit = {
    if (a <= 0.01f) return
    val heads = if (beamed) 2 else 1
    var h = 0
    while (h < heads) {
      val hx = if (!beamed) -0.55f else if (h == 0) -1.3f else 1.1f
      val hy = if (!beamed) 1.35f else if (h == 0) 1.45f else 1.05f
      val top = if (!beamed) -2.1f else if (h == 0) -1.9f else -2.3f
      val stemX = hx + 0.84f
      (pass: @scala.annotation.switch) match {
        case 0 =>
          ovalPts(hx, hy, 1f, 0.72f, HEAD_TILT, 12)
          sb.strokePolygon(_gx, _gy, 12, ink * 2f * _fk, r, g, b, a)
          gLine(sb, stemX, hy - 0.1f + ink, stemX, top - ink, 0.3f + ink * 2f, r, g, b, a)
        case 1 =>
          ovalPts(hx, hy, 1f, 0.72f, HEAD_TILT, 12)
          sb.fillPolygon(_gx, _gy, 12, r, g, b, a)
          gLine(sb, stemX, hy - 0.1f, stemX, top, 0.3f, r, g, b, a)
        case _ =>
          ovalPts(hx - 0.32f, hy - 0.26f, 0.34f, 0.2f, HEAD_TILT, 8)
          sb.fillPolygon(_gx, _gy, 8, r, g, b, a)
      }
      h += 1
    }
    if (pass == 2) return
    if (beamed) {
      // The beam, a parallelogram across the tops of the stems
      val e = if (pass == 0) ink else 0f
      _lx(0) = -0.63f - e; _ly(0) = -2.0f - e
      _lx(1) = 2.07f + e; _ly(1) = -2.42f - e
      _lx(2) = 2.07f + e; _ly(2) = -1.8f + e
      _lx(3) = -0.63f - e; _ly(3) = -1.38f + e
      var i = 0
      while (i < 4) { _gx(i) = fx(_lx(i), _ly(i)); _gy(i) = fy(_lx(i), _ly(i)); i += 1 }
      sb.fillPolygon(_gx, _gy, 4, r, g, b, a)
    } else {
      var i = 0
      while (i < 4) { _gx(i) = fx(FLAG_X(i), FLAG_Y(i)); _gy(i) = fy(FLAG_X(i), FLAG_Y(i)); i += 1 }
      val e = if (pass == 0) ink * 2f else 0f
      sb.strokePolylineTapered(_gx, _gy, 4, (0.62f + e) * _fk, (0.18f + e) * _fk, r, g, b, a, a)
    }
  }

  /** A whole note — ink, body, glint — at (cx, cy), `k` virtual units to a glyph unit. */
  private def note(sb: ShapeBatch, cx: Float, cy: Float, rot: Float, k: Float, beamed: Boolean,
                   r: Float, g: Float, b: Float, ir: Float, ig: Float, ib: Float, a: Float): Unit = {
    frame(cx, cy, rot, k)
    noteGlyph(sb, beamed, 0, 0.28f, ir, ig, ib, 0.92f * a)
    noteGlyph(sb, beamed, 1, 0f, r, g, b, a)
    noteGlyph(sb, beamed, 2, 0f, mix(r, 1f, 0.8f), mix(g, 1f, 0.8f), mix(b, 1f, 0.8f), 0.85f * a)
  }

  /**
   * The Bard's song: a golden quaver dancing along a ribbon of stave. The five lines stream back
   * from the note on a lilac band, rippling as the tune runs down them, with a crotchet or two
   * riding them and falling behind; a pair of glints twinkles round the note.
   */
  private[projectiles] def drawLuteNote(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 37) * 0.35
    computeAllDynamics(proj, 0.78f, 0.58f, 0.97f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx

    // The stave, from the note back: a centre line rippling more the further it is from the note,
    // and the five lines either side of it, closing up as they fade
    val n = 12
    val len = 74f * ds
    var i = 0
    while (i < n) {
      val t = i / (n - 1f)
      val c = Math.sin(phase * 1.15 - t * 6.2).toFloat * 6.5f * ds * t * (2f - t)
      _lx(i) = sx - ndx * len * t + px * c
      _ly(i) = sy + 3f * ds - ndy * len * t + py * c
      _pvW(i) = (14f - 5f * t) * ds; _pvA(i) = 0.42f * a * (1f - t)
      i += 1
    }
    sb.strokePolylineVar(_lx, _ly, _pvW, _pvA, n, 0.72f, 0.52f, 0.98f)
    var line = -2
    while (line <= 2) {
      i = 0
      while (i < n) {
        val t = i / (n - 1f)
        val off = line * 2.8f * ds * (1f - 0.4f * t)
        _gx(i) = _lx(i) + px * off; _gy(i) = _ly(i) + py * off
        i += 1
      }
      sb.strokePolylineTapered(_gx, _gy, n, 1.35f * ds, 0.8f * ds, 0.28f, 0.11f, 0.44f, 0.92f * a, 0.1f * a)
      line += 1
    }

    // Crotchets riding the stave, falling behind as the tune runs on
    var k = 0
    while (k < 2) {
      val t = 0.18f + 0.72f * ((tick * 0.011 + k * 0.5 + proj.id * 0.31) % 1.0).toFloat
      val at = t * (n - 1)
      val j = Math.min(n - 2, at.toInt); val f = at - j
      val lx = mix(_lx(j), _lx(j + 1), f); val ly = mix(_ly(j), _ly(j + 1), f)
      val off = (if (k == 0) 1.3f else -1.3f) * 2.6f * ds
      val fade = Math.min(1f, (1f - t) * 3f) * Math.min(1f, (t - 0.18f) * 12f)
      note(sb, lx + px * off, ly + py * off - 5f * ds, 0.1f * Math.sin(phase + k).toFloat, 2.7f * ds,
        beamed = false, 1f, 0.84f, 0.36f, 0.20f, 0.08f, 0.30f, 0.9f * a * fade)
      k += 1
    }

    // The note itself, rocking to the tune over a warm glow
    val bob = Math.sin(phase * 0.9).toFloat * 1.6f * ds
    sb.fillOvalSoft(sx, sy - 3f * ds + bob, 17f * ds * dynGlow, 16f * ds * dynGlow, 1f, 0.82f, 0.4f, 0.3f * a, 0f, 16)
    note(sb, sx + 1f * ds, sy - 1f * ds + bob, 0.22f * Math.sin(phase * 0.55).toFloat, 7.2f * ds,
      beamed = false, 1f, 0.80f, 0.26f, 0.20f, 0.08f, 0.30f, a)
    var s = 0
    while (s < 2) {
      val t = ((tick * 0.03 + s * 0.5 + proj.id * 0.17) % 1.0).toFloat
      val ang = s * Math.PI + phase * 0.3
      sb.fillStarFlare(sx + Math.cos(ang).toFloat * 16f * ds, sy - 7f * ds + Math.sin(ang).toFloat * 12f * ds,
        7f * ds * Math.sin(t * Math.PI).toFloat, 1.8f, 0f, 1f, 1f, 0.95f, 0.75f, 0.9f * a)
      s += 1
    }
  }

  /**
   * The Musician's power chord: a hot-pink pair of quavers inked black, thumping to the beat, a
   * cyan ghost of them a step behind, sparks crackling off them, and the waveform they have played
   * left hanging in the air behind — a zigzag of uneven peaks keyed to points fixed along the
   * flight line (as a bolt's channel is), so it stays where it was drawn as the notes fly on.
   */
  private[projectiles] def drawPowerChord(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 29) * 0.35
    computeAllDynamics(proj, 1f, 0.3f, 0.72f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    // The flight line in world units, how it lies on screen, and how far along it the notes are
    val wl = Math.sqrt(proj.dx * proj.dx + proj.dy * proj.dy).toFloat
    val ux = if (wl > 1e-4f) proj.dx / wl else 1f
    val uy = if (wl > 1e-4f) proj.dy / wl else 0f
    val esx = (ux - uy) * 20f; val esy = (ux + uy) * 10f
    val spp = Math.max(1f, Math.sqrt(esx * esx + esy * esy).toFloat)
    val ndx = esx / spp; val ndy = esy / spp
    val nx = -ndy; val ny = ndx
    val along = proj.getX * ux + proj.getY * uy
    val gap = 5.5f * ds
    val stepW = gap / spp
    val trail = 78f * ds
    val seed = proj.id * 173

    // The waveform: one peak a node, alternating sides, each as tall as its hash says, dying away
    // into the notes
    _lx(0) = sx; _ly(0) = sy; _pvA(0) = 0f
    var n = 1
    var k = Math.floor(along / stepW).toInt
    var more = true
    while (more && n < 20) {
      val d = (along - k * stepW) * spp
      if (d > trail) more = false
      else {
        if (d > gap * 0.5f) {
          val side = if ((k & 1) == 0) 1f else -1f
          val amp = (2f + 8f * Math.abs(hash2(seed, k >> 1))) * ds * Math.min(1f, d / (gap * 2.6f))
          _lx(n) = sx - ndx * d + nx * side * amp; _ly(n) = sy - ndy * d + ny * side * amp
          _pvA(n) = d / trail
          n += 1
        }
        k -= 1
      }
    }
    if (n >= 2) {
      val far = _pvA(n - 1)
      sb.strokePolylineTapered(_lx, _ly, n, 12f * ds, 6f * ds, 1f, 0.3f, 0.75f, 0.22f * a, 0f)
      sb.strokePolylineTapered(_lx, _ly, n, 6.4f * ds, 2.8f * ds, 0.12f, 0.02f, 0.10f, 0.6f * a, 0.2f * a * (1f - far))
      shifted(n, 1.7f * ds, 1.2f * ds)
      sb.strokePolylineTapered(_gx, _gy, n, 3.2f * ds, 1.4f * ds, 0.22f, 0.95f, 1f, 0.85f * a, 0.1f * a * (1f - far))
      sb.strokePolylineTapered(_lx, _ly, n, 3.4f * ds, 1.5f * ds, 1f, 0.26f, 0.72f, a, 0.15f * a * (1f - far))
      sb.strokePolylineTapered(_lx, _ly, n, 1.2f * ds, 0.5f * ds, 1f, 0.88f, 0.97f, 0.9f * a, 0f)
    }

    // The chord, bouncing on the beat
    val beat = Math.max(0f, Math.sin(phase * 0.9).toFloat)
    val kk = 6.2f * ds * (1f + 0.09f * beat * beat * beat)
    val rot = 0.14f * Math.sin(phase * 0.45).toFloat
    sb.fillOvalSoft(sx, sy - 2f * ds, 18f * ds * dynGlow, 16f * ds * dynGlow, 1f, 0.3f, 0.72f, 0.3f * a, 0f, 16)
    frame(sx - 1.9f * ds, sy + 1.3f * ds, rot, kk)
    noteGlyph(sb, beamed = true, 1, 0f, 0.2f, 0.9f, 1f, 0.5f * a)
    note(sb, sx, sy, rot, kk, beamed = true, 1f, 0.3f, 0.72f, 0.07f, 0.02f, 0.07f, a)

    // Sparks off the chord, struck anew five times a second
    val epoch = (tick + proj.id * 5) / 4
    var s = 0
    while (s < 2) {
      if (hash2(seed + 11 * s, epoch) > -0.2f) {
        val ang = (hash2(seed + 7 + s, epoch) * Math.PI).toFloat - (if (s == 0) 0.6f else -2.4f)
        val ca = Math.cos(ang).toFloat; val sa = Math.sin(ang).toFloat
        val r0 = 10f * ds; val r1 = (19f + 5f * hash2(seed + 3 * s, epoch)) * ds
        sparkArc(sb, sx + ca * r0, sy - 3f * ds + sa * r0 * 0.8f, sx + ca * r1, sy - 3f * ds + sa * r1 * 0.8f, 3,
          3f * ds, seed + epoch * 13 + s, 2.2f * ds, 1f, 0.45f, 0.85f, a)
      }
      s += 1
    }
  }

  /** The waveform again in _gx/_gy, shifted by (ox, oy): its cyan ghost. */
  private def shifted(n: Int, ox: Float, oy: Float): Unit = {
    var i = 0
    while (i < n) { _gx(i) = _lx(i) + ox; _gy(i) = _ly(i) + oy; i += 1 }
  }

  // ── The Eye of Horus ──
  // In virtual units, the eye looking along +x: the kohl line, the spiral and the brow's tail run
  // back behind it.
  private val BROW_X = Array(-12f, -3f, 6f, 11f)
  private val BROW_Y = Array(-10f, -12.4f, -12f, -9.5f)
  private val CURL_X = Array(-4f, -7f, -11f, -15f, -18f, -18.6f, -16.8f, -15.2f)
  private val CURL_Y = Array(5f, 9f, 12f, 13f, 11.5f, 9f, 7.8f, 9.4f)
  private final val EYE_N = 14

  /** The almond of the eye into _gx/_gy, fattened by `e`: a lens, which is convex. */
  private def almond(e: Float): Unit = {
    val half = EYE_N / 2
    var i = 0
    while (i < half) {
      val t = i / (half - 1f)
      val x = mix(-10f - e, 9f + e, t)
      val top = -6f * Math.sin(t * Math.PI).toFloat - t * 1.2f - e
      _gx(i) = fx(x, top); _gy(i) = fy(x, top)
      val bx = mix(9f + e, -10f - e, t)
      val bot = 4.6f * Math.sin(t * Math.PI).toFloat - (1f - t) * 1.2f + e
      _gx(half + i) = fx(bx, bot); _gy(half + i) = fy(bx, bot)
      i += 1
    }
  }

  /** A stroke of the eye's paint through the frame's points in (xs, ys): gold under lapis. */
  private def paint(sb: ShapeBatch, xs: Array[Float], ys: Array[Float], n: Int, w0: Float, w1: Float, a: Float): Unit = {
    var i = 0
    while (i < n) { _gx(i) = fx(xs(i), ys(i)); _gy(i) = fy(xs(i), ys(i)); i += 1 }
    sb.strokePolylineTapered(_gx, _gy, n, (w0 + 4f) * _fk, (w1 + 4f) * _fk, 1f, 0.84f, 0.36f, a, a)
    sb.strokePolylineTapered(_gx, _gy, n, w0 * _fk, w1 * _fk, 0.07f, 0.11f, 0.36f, a, a)
  }

  /**
   * The Sphinx's riddle: the Eye of Horus in lapis and gold, looking the way it flies — the brow
   * over it, the kohl line running back from its corner, the drop and the spiral hanging under it
   * — in a warm glow, with a ring of its voice pulsing off it and gold dust streaming behind.
   */
  private[projectiles] def drawRiddleEye(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 43) * 0.35
    computeAllDynamics(proj, 1f, 0.8f, 0.3f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx
    val bob = Math.sin(phase * 0.7).toFloat * 1.5f * ds
    val cy = sy + bob

    // Gold dust streaming back
    var d = 0
    while (d < 7) {
      val t = ((tick * 0.04 + d / 7.0 + proj.id * 0.23) % 1.0).toFloat
      val side = hash2(proj.id + 5, d) * 9f * ds
      val gx = sx - ndx * (10f + t * 46f) * ds + px * side * (0.5f + t)
      val gy = cy - ndy * (10f + t * 46f) * ds + py * side * (0.5f + t) + t * 5f * ds
      val gs = (1.9f - t) * ds
      sb.fillOval(gx, gy, gs + 0.8f, gs + 0.8f, 0.35f, 0.22f, 0.05f, 0.5f * (1f - t) * a, 6)
      sb.fillOval(gx, gy, gs, gs, 1f, 0.85f, 0.4f, 0.95f * (1f - t) * a, 6)
      d += 1
    }

    // Its voice: rings pulsing off it
    var r = 0
    while (r < 2) {
      val t = ((phase * 0.07 + r * 0.5) % 1.0).toFloat
      val rr = (18f + 16f * t) * ds
      sb.strokeOval(sx, cy, rr, rr * 0.8f, 2.2f * ds * (1f - t * 0.5f), 1f, 0.82f, 0.36f, 0.4f * (1f - t) * a, 20)
      r += 1
    }
    sb.fillOvalSoft(sx, cy, 26f * ds * dynGlow, 21f * ds * dynGlow, 1f, 0.78f, 0.3f, 0.42f * a, 0f, 16)

    // The eye, mirrored to look the way it flies
    frame(sx + 2f * ds, cy - 1f * ds, 0f, 1.12f * ds, if (ndx < -0.15f) -1f else 1f)
    paint(sb, BROW_X, BROW_Y, 4, 3.4f, 2.4f, a)
    _lx(0) = -10f; _ly(0) = 0.2f; _lx(1) = -20f; _ly(1) = 0.6f
    paint(sb, _lx, _ly, 2, 2.8f, 2f, a)
    _lx(0) = 1f; _ly(0) = 4.6f; _lx(1) = 0.2f; _ly(1) = 13.5f
    paint(sb, _lx, _ly, 2, 2.8f, 2.4f, a)
    paint(sb, CURL_X, CURL_Y, 8, 2.8f, 1.6f, a)
    almond(2f)
    sb.fillPolygon(_gx, _gy, EYE_N, 1f, 0.84f, 0.36f, a)
    almond(0f)
    sb.fillPolygon(_gx, _gy, EYE_N, 0.98f, 0.95f, 0.86f, a)
    // The iris looks ahead, a little further as it goes
    val look = 2.4f + 0.5f * Math.sin(phase * 0.4).toFloat
    sb.fillOval(fx(look, -0.8f), fy(look, -0.8f), 4.2f * _fk, 4.2f * _fk, 0.12f, 0.3f, 0.78f, a, 16)
    sb.fillOval(fx(look + 0.5f, -0.8f), fy(look + 0.5f, -0.8f), 2f * _fk, 2f * _fk, 0.03f, 0.03f, 0.08f, a, 12)
    sb.fillOval(fx(look - 1.2f, -2.2f), fy(look - 1.2f, -2.2f), 1f * _fk, 0.9f * _fk, 1f, 1f, 1f, 0.9f * a, 8)
    almond(0f)
    sb.strokePolygon(_gx, _gy, EYE_N, 2.2f * _fk, 0.07f, 0.11f, 0.36f, a)
  }

  // ── Feathers ──
  // A feather flying point-first, +x its tip, in units of its half-length: two convex halves of
  // vane either side of the quill, a lighter band at the tip, and a split in each half.
  private val VANE_X = Array(1.3f, 0.7f, -0.2f, -0.9f, -1.1f, -1.1f)
  private val VANE_TOP_Y = Array(0f, -0.36f, -0.46f, -0.35f, -0.12f, 0f)
  private val VANE_BOT_Y = Array(0f, 0.24f, 0.30f, 0.24f, 0.07f, 0f)
  private val TIP_X = Array(1.3f, 0.7f, 0.42f, 0.42f, 0.7f)
  private val TIP_Y = Array(0f, -0.36f, -0.41f, 0.26f, 0.24f)

  // The feather's frame: where it is, the way its tip points (fx, fy), and across it (qx, qy)
  private var _ex = 0f; private var _ey = 0f
  private var _efx = 1f; private var _efy = 0f; private var _eqx = 0f; private var _eqy = 1f

  private def featherFrame(cx: Float, cy: Float, dirX: Float, dirY: Float, len: Float, width: Float): Unit = {
    _ex = cx; _ey = cy
    _efx = dirX * len; _efy = dirY * len
    _eqx = -dirY * width; _eqy = dirX * width
  }

  private def featherPts(xs: Array[Float], ys: Array[Float], n: Int): Unit = {
    var i = 0
    while (i < n) {
      _gx(i) = _ex + xs(i) * _efx + ys(i) * _eqx; _gy(i) = _ey + xs(i) * _efy + ys(i) * _eqy
      i += 1
    }
  }

  /**
   * A feather centred on (cx, cy), its tip along (dirX, dirY), `len` of its half-length units along
   * it and `width` across (narrower as it rolls): ink round both halves, then both halves, the
   * lighter tip, the splits and the quill. (r, g, b) is the vane, (tr, tg, tb) its tip.
   */
  private[projectiles] def feather(sb: ShapeBatch, cx: Float, cy: Float, dirX: Float, dirY: Float,
                                   len: Float, width: Float, r: Float, g: Float, b: Float,
                                   tr: Float, tg: Float, tb: Float, a: Float): Unit = {
    if (a <= 0.01f) return
    featherFrame(cx, cy, dirX, dirY, len, width)
    val inkW = Math.max(1.3f, len * 0.11f)
    featherPts(VANE_X, VANE_TOP_Y, 6)
    sb.strokePolygon(_gx, _gy, 6, inkW, 0.08f, 0.05f, 0.12f, 0.85f * a)
    featherPts(VANE_X, VANE_BOT_Y, 6)
    sb.strokePolygon(_gx, _gy, 6, inkW, 0.08f, 0.05f, 0.12f, 0.85f * a)
    featherPts(VANE_X, VANE_TOP_Y, 6)
    sb.fillPolygon(_gx, _gy, 6, r, g, b, a)
    featherPts(VANE_X, VANE_BOT_Y, 6)
    sb.fillPolygon(_gx, _gy, 6, r * 0.82f, g * 0.82f, b * 0.82f, a)
    featherPts(TIP_X, TIP_Y, 5)
    sb.fillPolygon(_gx, _gy, 5, tr, tg, tb, a)
    // The splits in the vane, and the quill
    val lw = Math.max(0.8f, len * 0.05f)
    sb.strokeLine(_ex - 0.4f * _efx - 0.4f * _eqx, _ey - 0.4f * _efy - 0.4f * _eqy,
      _ex - 0.12f * _efx - 0.16f * _eqx, _ey - 0.12f * _efy - 0.16f * _eqy, lw, 0.08f, 0.05f, 0.12f, 0.8f * a)
    sb.strokeLine(_ex + 0.2f * _efx + 0.33f * _eqx, _ey + 0.2f * _efy + 0.33f * _eqy,
      _ex + 0.46f * _efx + 0.12f * _eqx, _ey + 0.46f * _efy + 0.12f * _eqy, lw, 0.08f, 0.05f, 0.12f, 0.8f * a)
    sb.strokeLine(_ex - 1.5f * _efx, _ey - 1.5f * _efy, _ex + 1.15f * _efx, _ey + 1.15f * _efy, lw,
      mix(r, 1f, 0.75f), mix(g, 1f, 0.75f), mix(b, 1f, 0.75f), 0.9f * a)
  }

  /**
   * The Harpy's screech: a volley of her razor feathers, point first, fluttering as they fly — one
   * in front, two lesser ones splayed either side of it — the screech torn off behind them as a cone
   * of two jagged magenta lines opening back (the wake of something faster than sound), and a
   * couple of smaller feathers shaken loose, tumbling behind. Run alongside the feathers, the jagged
   * lines were the legs of an insect; one feather alone was a thin dart.
   */
  private[projectiles] def drawScreechFeather(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 31) * 0.4
    computeAllDynamics(proj, 0.62f, 0.32f, 0.85f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx

    // The screech: a cone of jagged lines opening off the feather's back, torn anew five times a
    // second
    val epoch = (tick + proj.id * 3) / 4
    val len = 15f * ds
    var side = -1
    while (side <= 1) {
      var i = 0
      while (i < 8) {
        val t = i / 7f
        val back = 36f * ds + t * 42f * ds
        val jag = if (i == 0) 0f
                  else (if ((i & 1) == 0) 1f else -1f) * (2f + 1.2f * hash2(proj.id * 7 + side, epoch * 9 + i)) * ds
        val lat = side * t * 17f * ds + jag
        _lx(i) = sx - ndx * back + px * lat; _ly(i) = sy - ndy * back + py * lat
        i += 1
      }
      sb.strokePolylineTapered(_lx, _ly, 8, 4.8f * ds, 1.6f * ds, 0.2f, 0.04f, 0.2f, 0.55f * a, 0f)
      sb.strokePolylineTapered(_lx, _ly, 8, 2.8f * ds, 0.9f * ds, 0.98f, 0.36f, 0.88f, 0.95f * a, 0f)
      side += 2
    }

    // Feathers shaken loose, tumbling behind
    var f = 0
    while (f < 2) {
      val t = ((tick * 0.022 + f * 0.5 + proj.id * 0.19) % 1.0).toFloat
      val s2 = if (f == 0) 1f else -1f
      val back = (22f + t * 48f) * ds
      val cx = sx - ndx * back + px * s2 * (5f + t * 11f) * ds
      val cy = sy - ndy * back + py * s2 * (5f + t * 11f) * ds + t * t * 8f * ds
      val spin = phase * 0.9 + f * 2.1
      feather(sb, cx, cy, Math.cos(spin).toFloat, Math.sin(spin).toFloat * 0.7f, 7.5f * ds * (1f - 0.3f * t),
        6.5f * ds * (1f - 0.3f * t), 0.62f, 0.34f, 0.86f, 0.84f, 0.66f, 0.98f, 0.85f * (1f - t) * a)
      f += 1
    }

    // The feathers: the lesser two splayed out behind, then the one whose tip is on the hitbox, each
    // rolling as it flutters
    sb.fillOvalSoft(sx - ndx * 12f * ds, sy - ndy * 12f * ds, 24f * ds * dynGlow, 18f * ds * dynGlow,
      0.8f, 0.36f, 0.95f, 0.28f * a, 0f, 16)
    var fl = -1
    while (fl <= 2) {
      val main = fl == 2
      val wob = (if (main) 0f else fl * 0.24f) + 0.08f * Math.sin(phase * 1.3 + fl).toFloat
      val dxw = ndx * Math.cos(wob).toFloat - ndy * Math.sin(wob).toFloat
      val dyw = ndx * Math.sin(wob).toFloat + ndy * Math.cos(wob).toFloat
      val roll = 0.72f + 0.28f * Math.abs(Math.sin(phase * 0.8 + fl * 1.1).toFloat)
      val fLen = if (main) len else len * 0.76f
      val tx = if (main) sx else sx - ndx * 10f * ds + px * fl * 8.5f * ds
      val ty = if (main) sy else sy - ndy * 10f * ds + py * fl * 8.5f * ds
      feather(sb, tx - dxw * fLen * 1.3f, ty - dyw * fLen * 1.3f, dxw, dyw, fLen, fLen * 0.8f * roll,
        if (main) 0.56f else 0.5f, if (main) 0.28f else 0.25f, if (main) 0.8f else 0.74f, 0.86f, 0.68f, 1f, a)
      fl += (if (fl == -1) 2 else 1)
    }
  }

  // ── The Banshee ──

  /** Her face, egg-shaped (narrowing to the chin), fattened by `e`, as a convex polygon in _gx/_gy. */
  private def facePts(cx: Float, cy: Float, rx: Float, ry: Float, e: Float): Unit = {
    var i = 0
    while (i < 18) {
      val t = i * (2 * Math.PI / 18)
      val st = Math.sin(t).toFloat
      _gx(i) = cx + Math.cos(t).toFloat * (rx * (1f - 0.22f * Math.max(0f, st)) + e)
      _gy(i) = cy + st * (ry + e)
      i += 1
    }
  }

  /**
   * The Banshee's keening: her own face, pale and anguished, mouth open in the cry, with her long
   * white hair streaming back behind it and a wisp of her dress trailing under it; the cry rings
   * off her in faint cold circles.
   */
  private[projectiles] def drawKeeningGhost(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 41) * 0.35
    computeAllDynamics(proj, 0.8f, 0.86f, 1f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx
    val rx = 10.5f * ds; val ry = 12.5f * ds
    val sway = Math.sin(phase * 0.6).toFloat * 1.5f * ds
    val cx = sx + px * sway * 0.3f; val cy = sy

    // The wisp of her dress, from under her chin
    var i = 0
    while (i < 8) {
      val t = i / 7f
      val w = Math.sin(phase * 1.2 - t * 4.0).toFloat * 4f * ds * t
      _lx(i) = cx - ndx * 42f * ds * t + px * w; _ly(i) = cy + ry * 0.8f - ndy * 42f * ds * t + py * w + t * 5f * ds
      _pvW(i) = (13f * (1f - t) + 1f) * ds; _pvA(i) = 0.68f * a * (1f - t)
      i += 1
    }
    sb.strokePolylineVar(_lx, _ly, _pvW, _pvA, 8, 0.5f, 0.48f, 0.88f)

    // Her hair: eight locks from the crown and down her temples, streaming back, fanning out and
    // rippling as they go
    var lock = 0
    while (lock < 8) {
      val th = Math.PI * (0.84 + lock * 0.19)
      val rootX = cx + Math.cos(th).toFloat * rx * 0.8f
      val rootY = cy + Math.sin(th).toFloat * ry * 0.8f
      val len = (44f + 10f * hash2(proj.id + 1, lock)) * ds
      i = 0
      while (i < 7) {
        val t = i / 6f
        val w = Math.sin(phase * 1.4 - t * 4.2 + lock * 1.1).toFloat * 4.5f * ds * t +
          (lock - 3.5f) * 2.4f * ds * t
        _lx(i) = rootX - ndx * len * t + px * w; _ly(i) = rootY - ndy * len * t + py * w
        _pvW(i) = (6.2f * (1f - t) + 0.8f) * ds + 2.4f; _pvA(i) = 0.72f * a * (1f - t)
        i += 1
      }
      sb.strokePolylineVar(_lx, _ly, _pvW, _pvA, 7, 0.2f, 0.22f, 0.44f)
      i = 0
      while (i < 7) {
        val t = i / 6f
        _pvW(i) = (6.2f * (1f - t) + 0.8f) * ds; _pvA(i) = 0.95f * a * (1f - t * t)
        i += 1
      }
      sb.strokePolylineVar(_lx, _ly, _pvW, _pvA, 7, 0.88f, 0.9f, 1f)
      lock += 1
    }

    // The cry, ringing off her
    var r = 0
    while (r < 2) {
      val t = ((phase * 0.09 + r * 0.5) % 1.0).toFloat
      val rr = (12f + 20f * t) * ds
      sb.strokeOval(cx, cy + 4f * ds, rr, rr * 0.78f, 2f * ds * (1f - 0.5f * t), 0.78f, 0.88f, 1f, 0.32f * (1f - t) * a, 20)
      r += 1
    }

    // Her face: cold glow, ink, pale skin shading to blue at the chin
    sb.fillOvalSoft(cx, cy, 24f * ds * dynGlow, 23f * ds * dynGlow, 0.7f, 0.8f, 1f, 0.28f * a, 0f, 16)
    facePts(cx, cy, rx, ry, 2f)
    sb.fillPolygon(_gx, _gy, 18, 0.16f, 0.18f, 0.34f, 0.88f * a)
    facePts(cx, cy, rx, ry, 0f)
    sb.fillPolygon(_gx, _gy, 18, 0.86f, 0.9f, 1f, 0.96f * a)
    sb.fillOvalSoft(cx, cy + ry * 0.38f, rx * 0.86f, ry * 0.58f, 0.66f, 0.72f, 0.95f, 0.5f * a, 0f, 16)
    sb.fillOval(cx - rx * 0.42f, cy - ry * 0.55f, rx * 0.2f, ry * 0.12f, 1f, 1f, 1f, 0.6f * a, 8)
    // Eyes: hollow, drooping at their outer corners
    frame(cx, cy, 0f, ds)
    var e = -1
    while (e <= 1) {
      ovalPts(e * 4.3f, -2.4f, 3.1f, 1.9f, e * 0.42f, 10)
      sb.fillPolygon(_gx, _gy, 10, 0.05f, 0.05f, 0.15f, 0.96f * a)
      sb.fillOval(fx(e * 4.2f, -2.2f), fy(e * 4.2f, -2.2f), 0.9f * ds, 0.9f * ds, 0.62f, 0.95f, 1f, 0.95f * a, 6)
      e += 2
    }
    // The mouth, open in the cry, opening wider and closing a little as it goes on
    val open = (3.4f + 1.3f * Math.sin(phase * 0.8).toFloat) * ds
    sb.fillOval(cx, cy + 5.4f * ds, 3f * ds + 1.4f, open + 1.4f, 0.16f, 0.18f, 0.34f, 0.7f * a, 12)
    sb.fillOval(cx, cy + 5.4f * ds, 3f * ds, open, 0.04f, 0.03f, 0.12f, 0.96f * a, 12)
  }
}
