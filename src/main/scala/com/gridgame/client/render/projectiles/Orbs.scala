package com.gridgame.client.render.projectiles

import com.gridgame.client.gl.ShapeBatch
import GLProjectileRenderers.Renderer
import ProjectileKit._
import Silhouettes._

/**
 * The orbs (energyBolt), the projectile seen most in a match: every style shares one anatomy — a
 * comet tail, a modest glow, a lit body (orbBody) glowing from inside, motes shed off its back — and
 * is told apart by what it adds outside that sphere (ORB_FIRE's flames, ORB_RUNE's circle,
 * ORB_ORBIT's ring...). Whatever a style draws inside the body is painted over.
 */
private[render] object Orbs {
  // ── energyBolt styles ──
  // Constants, not literals, because `registry` is a val built in textual order: a style
  // id declared below it reads 0 and silently renders the wrong shape.
  final val ORB_PLAIN  = 0   // the bare orb: a lit sphere and its comet tail
  final val ORB_FIRE   = 1   // flames swept back off a burning core
  final val ORB_RUNE   = 2   // a magic circle turning round it in the ground plane
  final val ORB_ORBIT  = 3   // orb inside a tilted orbit
  final val ORB_HEART  = 4   // a beating heart (charms)
  final val ORB_ASTRAL = 5   // star with a corona and an orbit of smaller stars
  final val ORB_SPIRIT = 6   // a soul: wisps streaming off it, a skull's sockets in it
  final val ORB_TOXIC  = 7   // bubbling, dripping plague
  final val ORB_SWARM  = 8   // a cloud of nanites round a small core
  final val ORB_SAND   = 9   // a ball of whirling sand shedding grains
  final val ORB_MUD    = 10  // a wet glob of mud flinging drops, with no glow at all
  final val ORB_SHOCK  = 11  // a plasma ball crackling with arcs

  /**
   * Half of a ring orbiting an orb, plus the motes riding that half. `front` picks the half
   * nearer the camera, which the caller draws *over* the body while the other half goes
   * behind it — the orb passing through the ring is the whole reason this reads as an orbit
   * and not as a halo painted around the outside.
   *
   * The ring's major axis is screen-horizontal at every heading. Turning it onto the travel
   * vector would make "front" swing to whichever side of the ellipse currently has the
   * larger screen y, and the ring would flip through the orb every time the shot changed
   * direction.
   */
  private def orbitHalf(sb: ShapeBatch, sx: Float, sy: Float, a: Float, b: Float,
                        spin: Double, front: Boolean, motes: Int, star: Boolean,
                        r: Float, g: Float, bl: Float, alpha: Float): Unit = {
    if (alpha <= 0.01f) return
    val base = if (front) 0f else Math.PI.toFloat
    val br = bright(r); val bg = bright(g); val bb = bright(bl)
    // Three passes: a wide dim glow, an ink line, then the lit edge on top. The ink is what
    // keeps the ring visible where it crosses the orb — bright() of an already pale colour
    // is near white, and a white ring over a white orb is not a ring.
    sb.strokeArc(sx, sy, a, b, base, Math.PI.toFloat, 5.5f, r, g, bl, 0.20f * alpha, 10)
    sb.strokeArc(sx, sy, a, b, base, Math.PI.toFloat, 3.4f, outline(r), outline(g), outline(bl), 0.62f * alpha, 10)
    sb.strokeArc(sx, sy, a, b, base, Math.PI.toFloat, 1.9f, br, bg, bb, 0.85f * alpha, 10)
    var i = 0
    while (i < motes) {
      val t = spin + i * (Math.PI * 2 / motes)
      val st = Math.sin(t).toFloat
      if ((st >= 0f) == front) {
        val ct = Math.cos(t).toFloat
        val mx = sx + a * ct; val my = sy + b * st
        // Streak along the orbit behind the mote, not out on a radius: a radial spike
        // reads as something stuck to the orb, a tangential one as something going round.
        val t2 = t - 0.62
        fadeLine(sb, mx, my, sx + a * Math.cos(t2).toFloat, sy + b * Math.sin(t2).toFloat,
          3.4f, br, bg, bb, 0.55f * alpha, 3)
        if (star) drawSparkleStar(mx, my, 5.4f, 1f, 1f, 1f, 0.9f * alpha, sb, spin * 1.7 + i)
        else {
          sb.fillOval(mx, my, 3.8f, 3.4f, br, bg, bb, 0.92f * alpha, 8)
          sb.fillOval(mx, my, 1.8f, 1.6f, 1f, 1f, 1f, 0.85f * alpha, 6)
        }
      }
      i += 1
    }
  }

  // A comet tail's four points, as fractions of its length, and its width and alpha at each.
  // Widths are per point, so where the points sit shapes the taper: nearly the orb's own width
  // where it leaves the orb (a teardrop, not a stalk), then a long thin wisp.
  private val TAIL_T = Array(0f, 0.36f, 0.66f, 1f)
  private val TAIL_W = Array(1f, 0.84f, 0.42f, 0.02f)
  private val TAIL_A = Array(1f, 0.78f, 0.36f, 0f)

  /**
   * A comet tail `len` long streaming back along (-ndx, -ndy) from (sx, sy), `w0` wide at its
   * root. Straight, and made of light: a tail swung from side to side behind a round head is a
   * tadpole swimming (see ORB_SPIRIT's history), and a narrow opaque one is a stalk.
   */
  private[projectiles] def cometTail(sb: ShapeBatch, sx: Float, sy: Float, ndx: Float, ndy: Float,
                        len: Float, w0: Float, r: Float, g: Float, b: Float, a0: Float): Unit = {
    if (a0 <= 0.01f || len < 2f) return
    var i = 0
    while (i < 4) {
      val t = TAIL_T(i)
      _pvX(i) = sx - ndx * len * t; _pvY(i) = sy - ndy * len * t
      _pvW(i) = w0 * TAIL_W(i); _pvA(i) = a0 * TAIL_A(i)
      i += 1
    }
    sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 4, r, g, b)
  }

  /**
   * The body every energy orb shares: an inked rim, the colour at full strength at the edge
   * lightening toward a hot core that breathes (`hot`), and a small glint. It glows from inside.
   * The first pass of this was lit from outside — a hard specular spot and a rim light on the
   * shadow side — and every orb came out a glass marble; with a skull's sockets in it, the soul
   * bolt's rim light was a smile. `ghost` thins the body for something that isn't quite there.
   */
  private[projectiles] def orbBody(sb: ShapeBatch, sx: Float, sy: Float, rx: Float, ry: Float,
                      r: Float, g: Float, b: Float, p: Float, hot: Float, ghost: Float = 1f): Unit = {
    val lr = bright(r); val lg = bright(g); val lb = bright(b)
    sb.fillOval(sx, sy, rx + 1.9f, ry + 1.9f, outline(r), outline(g), outline(b), 0.9f * p * ghost, 20)
    sb.fillOval(sx, sy, rx, ry, r * 0.84f, g * 0.84f, b * 0.84f, 0.97f * p * ghost, 20)
    sb.fillOvalSoft(sx + KEY_LIGHT_X * rx * 0.1f, sy + KEY_LIGHT_Y * ry * 0.1f, rx * 0.92f, ry * 0.92f,
      mix(r, lr, 0.6f), mix(g, lg, 0.6f), mix(b, lb, 0.6f), 0.95f * p, 0f, 20)
    val hk = Math.min(1f, 0.6f + 0.4f * hot)
    sb.fillOvalSoft(sx, sy, rx * 0.5f * hot, ry * 0.5f * hot, mix(lr, 1f, 0.78f), mix(lg, 1f, 0.78f),
      mix(lb, 1f, 0.78f), hk * p, 0.2f * hk * p, 14)
    sb.fillOval(sx + KEY_LIGHT_X * rx * 0.52f, sy + KEY_LIGHT_Y * ry * 0.54f, rx * 0.13f, ry * 0.1f,
      1f, 1f, 1f, 0.6f * p, 8)
  }

  /** Bright specks shed off the back of an orb and left behind, fading. */
  private[projectiles] def shedMotes(sb: ShapeBatch, sx: Float, sy: Float, ndx: Float, ndy: Float, rad: Float,
                        n: Int, r: Float, g: Float, b: Float, p: Float, tick: Int, id: Int): Unit = {
    val px = -ndy; val py = ndx
    var i = 0
    while (i < n) {
      val t = ((tick * 0.045 + i.toFloat / n + id * 0.37) % 1.0).toFloat
      val side = if ((i & 1) == 0) 1f else -1f
      val back = rad * (0.8f + t * 2.6f)
      val lat = side * rad * (0.3f + 0.45f * t) * (0.7f + 0.3f * Math.sin(i * 2.3 + id).toFloat)
      val ms = (1f - t) * rad * 0.085f + 0.7f
      sb.fillOval(sx - ndx * back + px * lat, sy - ndy * back + py * lat, ms, ms, r, g, b, 0.85f * (1f - t) * p, 6)
      i += 1
    }
  }

  /**
   * Energy orb. Every style shares one anatomy — a comet tail of light behind, a glow, a lit
   * sphere, specks shed off its back — and a style is whatever it adds *outside* that sphere:
   * inner detail is invisible at the size an orb is displayed, and whatever a style draws
   * inside the body's 0.90 x 0.68 sz ellipse is painted over. Nearly thirty characters' primary
   * attacks are one of these, so each style has to read as its element at a glance.
   *
   * What it used to be, and why it isn't: the whole orb pulsed between 100% and 30% alpha three
   * times a second (a strobe, and on pale ground its dim frames weren't there); a dozen white
   * specks orbited *inside* the body and read as dirt on a flat disc; and a halo 3.2 times its
   * size — about 0.7 million fragments an orb at 4K — tinted the ground round it for no read.
   */
  private[projectiles] def energyBolt(r: Float, g: Float, b: Float, size: Float = 20f, style: Int = ORB_PLAIN): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 37) * 0.35
      computeAllDynamics(proj, r, g, b, phase)
      // Steady: the body holds its strength and only the core breathes
      val p = (0.95f + 0.05f * Math.sin(phase * _stPulseMult).toFloat) * dynAlpha
      val dr = _evoR; val dg = _evoG; val db = _evoB
      val lr = bright(dr); val lg = bright(dg); val lb = bright(db)
      val breath = 0.97f + 0.03f * Math.sin(phase * 0.8).toFloat
      val sz = size * 1.4f * dynScale * breath
      val rx = sz * 0.9f; val ry = sz * 0.68f
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy
      val pxv = -ndy; val pyv = ndx
      // The body's width across the flight line, which is where its tail starts
      val across = 2f * Math.sqrt(rx * pxv * rx * pxv + ry * pyv * ry * pyv).toFloat
      val hot = 1f + 0.16f * Math.sin(phase * 1.9).toFloat + _chgBright
      val tailLen = sz * 2.5f * dynTrail * (0.93f + 0.07f * Math.sin(phase * 2.3).toFloat)

      // ── What streams out behind it ──
      style match {
        case ORB_MUD => mudTrail(sb, sx, sy, ndx, ndy, sz, p, tick, proj.id)
        case ORB_SAND =>
          cometTail(sb, sx, sy, ndx, ndy, tailLen * 1.05f, across * 1.05f, dr, dg, db, 0.3f * p)
          cometTail(sb, sx, sy, ndx, ndy, tailLen * 0.7f, across * 0.5f, lr, lg, lb, 0.45f * p)
          sandGrains(sb, sx, sy, ndx, ndy, sz, tailLen, p, tick, proj.id)
        case ORB_SWARM =>
          cometTail(sb, sx, sy, ndx, ndy, tailLen * 0.9f, across * 0.9f, dr, dg, db, 0.22f * p)
          naniteStream(sb, sx, sy, ndx, ndy, sz, tailLen, dr, dg, db, p, tick, proj.id)
        case ORB_FIRE => fireTail(sb, sx, sy, ndx, ndy, rx, sz, across, tailLen, dr, dg, db, p, phase)
        case ORB_SPIRIT =>
          cometTail(sb, sx, sy, ndx, ndy, tailLen * 1.1f, across * 1.1f, dr, dg, db, 0.3f * p)
          spiritWisps(sb, sx, sy, ndx, ndy, rx, across, tailLen, lr, lg, lb, p, phase)
          cometTail(sb, sx, sy, ndx, ndy, tailLen * 0.7f, across * 0.4f, mix(lr, 1f, 0.5f), mix(lg, 1f, 0.5f), mix(lb, 1f, 0.5f), 0.7f * p)
        case _ =>
          cometTail(sb, sx, sy, ndx, ndy, tailLen * 1.12f, across * 1.2f, dr, dg, db, 0.26f * p)
          cometTail(sb, sx, sy, ndx, ndy, tailLen, across * 0.88f, mix(dr, lr, 0.3f), mix(dg, lg, 0.3f), mix(db, lb, 0.3f), 0.6f * p)
          cometTail(sb, sx, sy, ndx, ndy, tailLen * 0.78f, across * 0.38f, mix(lr, 1f, 0.55f), mix(lg, 1f, 0.55f), mix(lb, 1f, 0.55f), 0.8f * p)
      }

      // ── Glow: modest, and none on mud ──
      if (style != ORB_MUD)
        sb.fillOvalSoft(sx, sy, rx * 1.75f * dynGlow, ry * 1.85f * dynGlow, dr, dg, db,
          (if (style == ORB_SAND) 0.18f else 0.3f) * p, 0f, 16)

      // ── Behind the body ──
      val orbits = style == ORB_ORBIT || style == ORB_ASTRAL
      val ringA = if (orbits) sz * 1.24f else 0f
      // Never quite edge-on (which reads as a bar through the orb) and never a circle
      // (which reads as a flat halo) — the nod between the two is what sells the tilt.
      val ringB = ringA * (0.17f + 0.33f * (0.5f + 0.5f * Math.sin(phase * 0.55).toFloat))
      val ringSpin = phase * 1.15
      if (orbits)
        orbitHalf(sb, sx, sy, ringA, ringB, ringSpin, front = false, 3, style == ORB_ASTRAL, dr, dg, db, 0.95f * p)
      style match {
        case ORB_RUNE => runeCircle(sb, sx, sy, sz, front = false, dr, dg, db, p, phase)
        case ORB_SWARM => nanites(sb, sx, sy, rx, front = false, dr, dg, db, p, phase)
        case _ => ()
      }

      // ── The body ──
      style match {
        case ORB_HEART => heartBody(sb, sx, sy, sz, dr, dg, db, p, phase)
        case ORB_MUD => mudBody(sb, sx, sy, ndx, ndy, rx, ry, p, phase)
        case ORB_SWARM => orbBody(sb, sx, sy, rx * 0.62f, ry * 0.62f, dr, dg, db, p, hot)
        case ORB_ASTRAL => starBody(sb, sx, sy, sz, dr, dg, db, p, phase)
        case ORB_SPIRIT => orbBody(sb, sx, sy, rx, ry, dr, dg, db, p, hot, ghost = 0.7f)
        case _ => orbBody(sb, sx, sy, rx, ry, dr, dg, db, p, hot)
      }

      // ── Over the body ──
      style match {
        case ORB_FIRE =>
          // Embers kicked up off the back, rising as they fall behind
          var e = 0
          while (e < 4) {
            val t = ((tick * 0.05 + e * 0.25 + proj.id * 0.29) % 1.0).toFloat
            val side = if ((e & 1) == 0) 1f else -1f
            val ex = sx - ndx * (rx * 0.7f + t * tailLen * 0.85f) + pxv * side * sz * (0.2f + 0.35f * t)
            val ey = sy - ndy * (rx * 0.7f + t * tailLen * 0.85f) + pyv * side * sz * (0.2f + 0.35f * t) - t * t * sz * 0.6f
            val es = (1f - t) * 2.3f + 0.7f
            sb.fillOval(ex, ey, es, es, 1f, mix(0.95f, 0.45f, t), mix(0.6f, 0.05f, t), 0.95f * (1f - t) * p, 6)
            e += 1
          }
        case ORB_RUNE => runeCircle(sb, sx, sy, sz, front = true, dr, dg, db, p, phase)
        case ORB_ASTRAL =>
          // The glint, over the core rather than behind it. Behind the body only the spike tips
          // showed, and a pair of hairlines poking past a round orb reads as a scratch.
          val flare = 0.85f + 0.15f * Math.sin(phase * 1.3).toFloat
          sb.fillStarFlare(sx, sy, sz * 1.75f * flare, sz * 0.26f,
            Math.sin(phase * 0.2).toFloat * 0.16f, 0.82f,
            1f, mix(lg, 1f, 0.7f), mix(lb, 1f, 0.5f), 0.9f * p)
        case ORB_TOXIC =>
          toxicBubbles(sb, sx, sy, rx, ry, dr, dg, db, p, tick, proj.id)
          // Drops of it falling away behind
          var d = 0
          while (d < 3) {
            val t = ((tick * 0.04 + d / 3.0 + proj.id * 0.17) % 1.0).toFloat
            val side = hash2(proj.id + 3, d) * sz * 0.5f
            val dx2 = sx - ndx * (rx * 0.5f + t * tailLen * 0.6f) + pxv * side
            val dy2 = sy - ndy * (rx * 0.5f + t * tailLen * 0.6f) + pyv * side + t * t * sz * 1.1f
            val ds2 = sz * 0.11f * (1f - t * 0.4f)
            sb.fillOval(dx2, dy2, ds2 + 1.2f, ds2 * 1.35f + 1.2f, outline(dr), outline(dg), outline(db), 0.7f * (1f - t) * p, 8)
            sb.fillOval(dx2, dy2, ds2, ds2 * 1.35f, dr, dg, db, 0.95f * (1f - t) * p, 8)
            d += 1
          }
        case ORB_SAND => sandSwirl(sb, sx, sy, rx, ry, dr, dg, db, p, phase)
        case ORB_SWARM => nanites(sb, sx, sy, rx, front = true, dr, dg, db, p, phase)
        case ORB_SHOCK =>
          // Arcs crackling off the ball, re-struck twenty times a second
          val epoch = (tick + proj.id * 5) / 3
          var a = 0
          while (a < 4) {
            val ang = a * (Math.PI / 2) + hash2(proj.id * 71 + a, epoch) * 0.7
            val al = rx * (1.55f + 0.4f * hash2(proj.id * 73 + a, epoch))
            val ca2 = Math.cos(ang).toFloat; val sa2 = Math.sin(ang).toFloat
            sparkArc(sb, sx + ca2 * rx * 0.55f, sy + sa2 * ry * 0.55f, sx + ca2 * al, sy + sa2 * al * 0.8f, 4,
              rx * 0.3f, proj.id * 13 + epoch * 7 + a, 3f, lr, lg, lb, p)
            a += 1
          }
        case _ => ()
      }
      if (orbits)
        orbitHalf(sb, sx, sy, ringA, ringB, ringSpin, front = true, 3, style == ORB_ASTRAL, dr, dg, db, p)

      // Specks shed off its back — not off mud, sand or the swarm, which shed their own
      if (style != ORB_MUD && style != ORB_SAND && style != ORB_SWARM)
        shedMotes(sb, sx, sy, ndx, ndy, rx, 3, lr, lg, lb, p, tick, proj.id)
      if (style == ORB_HEART) {
        // A pair of glints twinkling round the heart
        var k = 0
        while (k < 2) {
          val t = ((tick * 0.03 + k * 0.5 + proj.id * 0.23) % 1.0).toFloat
          val a = k * Math.PI + proj.id + t * 1.4
          val gx = sx + Math.cos(a).toFloat * sz * 1.05f; val gy = sy + Math.sin(a).toFloat * sz * 0.8f - sz * 0.1f
          sb.fillStarFlare(gx, gy, sz * 0.42f * Math.sin(t * Math.PI).toFloat, 1.8f, 0f, 1f, 1f, 0.92f, 0.96f,
            0.9f * p)
          k += 1
        }
      }

      drawChargeCrackle(sx, sy, sz, r, g, b, p, sb, phase, proj.chargeLevel)
      drawReturnGhosts(sx, sy, sz * 0.9f, dr, dg, db, p, sb, proj)
    }

  /**
   * Fire: a teardrop of flame trailing the ball — orange round a yellow heart — with its edge
   * broken into tongues, three down each side, peeling off backward and curling up as flames do.
   * Each tongue is one tapering stroke along a bent path. Drawn in layers so the ink outlines the
   * whole mane rather than every tongue: ink, the red-orange tongues, the orange teardrop, the
   * yellow tongues, the yellow heart.
   *
   * Straight triangles stood on the rim (the first pass of this) read as a chestnut; stood all
   * round the ball at even angles (the pass before), as a mine or a sun.
   */
  private def fireTail(sb: ShapeBatch, sx: Float, sy: Float, ndx: Float, ndy: Float, rx: Float, sz: Float,
                       across: Float, tailLen: Float, r: Float, g: Float, b: Float, p: Float, phase: Double): Unit = {
    val px = -ndy; val py = ndx
    cometTail(sb, sx, sy, ndx, ndy, tailLen * 1.2f, across * 1.35f, r, g * 0.5f, b * 0.25f, 0.28f * p)
    var pass = 0
    while (pass < 3) {
      if (pass == 2) cometTail(sb, sx, sy, ndx, ndy, tailLen, across * 0.95f, r, g * 0.8f, b * 0.55f, 0.9f * p)
      val inner = pass == 2
      var j = 0
      while (j < 6) {
        val side = if (j < 3) 1f else -1f
        val k = j % 3
        val flick = 0.7f + 0.3f * Math.sin(phase * 3.9 + j * 2.1).toFloat
        val start = rx * (0.1f + k * 0.62f)
        val len = tailLen * (0.62f - k * 0.13f) * flick * (if (inner) 0.6f else 1f)
        val w0 = sz * (0.5f - k * 0.09f) * (if (inner) 0.55f else 1f)
        // Rooted on the teardrop's edge, heading back and out from it
        val hw = across * 0.5f * Math.max(0.25f, 1f - start / (tailLen * 1.1f))
        val rootX = sx - ndx * start + px * side * hw * (if (inner) 0.45f else 0.8f)
        val rootY = sy - ndy * start + py * side * hw * (if (inner) 0.45f else 0.8f)
        val dx = -ndx * 0.9f + px * side * 0.44f; val dy = -ndy * 0.9f + py * side * 0.44f
        var i = 0
        while (i < 4) {
          val t = i / 3f
          val sway = Math.sin(phase * 2.9 + j * 1.3 + t * 3.0).toFloat * len * 0.08f * t
          _pvX(i) = rootX + dx * len * t + px * sway
          _pvY(i) = rootY + dy * len * t + py * sway - len * 0.12f * t * t
          _pvW(i) = (if (pass == 0) w0 + 2.8f else w0) * (1f - 0.97f * t)
          _pvA(i) = p * (if (pass == 0) 0.55f else 0.95f) * (1f - t * t)
          i += 1
        }
        if (pass == 0) sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 4, outline(r), outline(g), outline(b))
        else if (!inner) sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 4, r, g * 0.55f, b * 0.3f)
        else sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 4, 1f, mix(g, 1f, 0.72f), mix(b, 0.45f, 0.5f))
        j += 1
      }
      pass += 1
    }
    cometTail(sb, sx, sy, ndx, ndy, tailLen * 0.6f, across * 0.45f, 1f, mix(g, 1f, 0.8f), mix(b, 0.6f, 0.5f), 0.9f * p)
  }

  /** A magic circle lying in the ground plane round the orb: an inked double ring with glyphs
   *  on it turning slowly. Drawn in halves, the far one before the body and the near one after,
   *  so the orb sits inside the circle. The old rune style stood two grey rings round the orb on
   *  screen, and a ring round a disc drawn flat on the screen is a tyre. */
  private def runeCircle(sb: ShapeBatch, sx: Float, sy: Float, sz: Float, front: Boolean,
                         r: Float, g: Float, b: Float, p: Float, phase: Double): Unit = {
    val cy = sy + sz * 0.08f
    val a0 = sz * 1.34f; val b0 = a0 * 0.42f
    val a1 = a0 * 0.8f; val b1 = b0 * 0.8f
    val start = if (front) 0f else Math.PI.toFloat
    val lr = mix(bright(r), 1f, 0.25f); val lg = mix(bright(g), 1f, 0.25f); val lb = mix(bright(b), 1f, 0.25f)
    sb.strokeArc(sx, cy, a0, b0, start, Math.PI.toFloat, 3.6f, outline(r), outline(g), outline(b), 0.55f * p, 14)
    sb.strokeArc(sx, cy, a0, b0, start, Math.PI.toFloat, 1.9f, lr, lg, lb, 0.9f * p, 14)
    sb.strokeArc(sx, cy, a1, b1, start, Math.PI.toFloat, 1.3f, lr, lg, lb, 0.6f * p, 12)
    // Glyphs between the rings: small diamonds, each lying along the circle
    var k = 0
    while (k < 6) {
      val a = phase * 0.45 + k * Math.PI / 3
      val sa = Math.sin(a).toFloat
      if ((sa >= 0f) == front) {
        val ca = Math.cos(a).toFloat
        val gx = sx + ca * a0 * 0.9f; val gy = cy + sa * b0 * 0.9f
        // Tangent to the ellipse at a, and the way out of it
        val tx0 = -sa * a0; val ty0 = ca * b0
        val tl = Math.max(1e-3f, Math.sqrt(tx0 * tx0 + ty0 * ty0).toFloat)
        val tx = tx0 / tl; val ty = ty0 / tl
        val gl = sz * 0.2f; val gw = sz * 0.09f
        _polyXs4(0) = gx - tx * gl; _polyYs4(0) = gy - ty * gl
        _polyXs4(1) = gx - ty * gw; _polyYs4(1) = gy + tx * gw
        _polyXs4(2) = gx + tx * gl; _polyYs4(2) = gy + ty * gl
        _polyXs4(3) = gx + ty * gw; _polyYs4(3) = gy - tx * gw
        sb.strokePolygon(_polyXs4, _polyYs4, 4, 2f, outline(r), outline(g), outline(b), 0.6f * p)
        sb.fillPolygon(_polyXs4, _polyYs4, 4, lr, lg, lb, 0.95f * p)
      }
      k += 1
    }
  }

  /** A soul: two thin wisps peeling off its sides and curling away behind it, like the hem of
   *  something that isn't there. They bow outward and hold still — no swing. */
  private def spiritWisps(sb: ShapeBatch, sx: Float, sy: Float, ndx: Float, ndy: Float, rx: Float,
                          across: Float, tailLen: Float, r: Float, g: Float, b: Float, p: Float,
                          phase: Double): Unit = {
    val px = -ndy; val py = ndx
    var w = 0
    while (w < 2) {
      val side = if (w == 0) 1f else -1f
      val reach = 0.8f + 0.2f * Math.sin(phase * 1.3 + w * 2.2).toFloat
      var i = 0
      while (i < 4) {
        val t = i / 3f
        val back = rx * 0.4f + tailLen * 0.9f * t * reach
        val lat = side * across * (0.36f + 0.34f * t * t)
        _pvX(i) = sx - ndx * back + px * lat; _pvY(i) = sy - ndy * back + py * lat
        _pvW(i) = across * 0.22f * (1f - t * 0.92f)
        _pvA(i) = 0.62f * p * (1f - t)
        i += 1
      }
      sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 4, r, g, b)
      w += 1
    }
  }

  /** A charm: a heart, upright on screen whatever the heading, beating lub-dub. Its outline is
   *  the union of two lobes and a point: every piece is inked slightly larger first and filled
   *  over, so the ink shows only round the outside. */
  private def heartBody(sb: ShapeBatch, sx: Float, sy: Float, sz: Float, r: Float, g: Float, b: Float,
                        p: Float, phase: Double): Unit = {
    val bt = ((phase * 0.08) % 1.0).toFloat
    val beat = if (bt < 0.12f) Math.sin(bt / 0.12f * Math.PI).toFloat
      else if (bt > 0.2f && bt < 0.32f) 0.6f * Math.sin((bt - 0.2f) / 0.12f * Math.PI).toFloat else 0f
    val h = sz * 0.98f * (1f + 0.1f * beat)
    val ly = sy - h * 0.2f; val lo = h * 0.4f; val lrad = h * 0.47f
    var pass = 0
    while (pass < 2) {
      val grow = if (pass == 0) 2f else 0f
      val cr = if (pass == 0) outline(r) else r * 0.72f
      val cg = if (pass == 0) outline(g) else g * 0.72f
      val cb = if (pass == 0) outline(b) else b * 0.72f
      val ca = if (pass == 0) 0.9f * p else 0.98f * p
      sb.fillOval(sx - lo, ly, lrad + grow, lrad + grow, cr, cg, cb, ca, 18)
      sb.fillOval(sx + lo, ly, lrad + grow, lrad + grow, cr, cg, cb, ca, 18)
      _polyXs3(0) = sx - h * 0.84f - grow * 0.9f; _polyYs3(0) = sy - h * 0.06f
      _polyXs3(1) = sx + h * 0.84f + grow * 0.9f; _polyYs3(1) = sy - h * 0.06f
      _polyXs3(2) = sx; _polyYs3(2) = sy + h * 0.86f + grow * 1.4f
      sb.fillPolygon(_polyXs3, _polyYs3, 3, cr, cg, cb, ca)
      pass += 1
    }
    val lr = bright(r); val lg = bright(g); val lb = bright(b)
    // Lit from the upper left, as every orb is
    sb.fillOvalSoft(sx - lo * 0.7f, ly + h * 0.05f, h * 0.62f, h * 0.55f, r, g, b, 0.95f * p, 0f, 16)
    sb.fillOvalSoft(sx - lo * 0.3f, sy + h * 0.02f, h * 0.4f, h * 0.36f, mix(lr, 1f, 0.35f), mix(lg, 1f, 0.35f),
      mix(lb, 1f, 0.35f), (0.6f + 0.3f * beat) * p, 0f, 14)
    sb.fillOval(sx - lo - lrad * 0.25f, ly - lrad * 0.35f, lrad * 0.36f, lrad * 0.24f, 1f, 1f, 1f, 0.82f * p, 10)
    sb.strokeArc(sx + lo, ly, lrad * 0.8f, lrad * 0.8f, -0.4f, 1.3f, Math.max(1.3f, h * 0.07f),
      mix(lr, 1f, 0.45f), mix(lg, 1f, 0.45f), mix(lb, 1f, 0.45f), 0.6f * p, 8)
  }

  /** A shooting star: five points turning slowly, gold, facing the camera. Inked as a larger
   *  star filled behind it rather than stroked, since the mitre at a star's points is past the
   *  batch's limit and a clipped mitre folds over. Fanned from its centre (it isn't convex). */
  private def starBody(sb: ShapeBatch, sx: Float, sy: Float, sz: Float, r: Float, g: Float, b: Float,
                       p: Float, phase: Double): Unit = {
    val spin = phase * 0.22 - Math.PI / 2
    var pass = 0
    while (pass < 3) {
      val ro = sz * (if (pass == 0) 1.08f else if (pass == 1) 0.98f else 0.56f) + (if (pass == 0) 2.2f else 0f)
      val ri = ro * 0.47f
      var i = 0
      while (i < 10) {
        val a = spin + i * (Math.PI / 5)
        val rad = if ((i & 1) == 0) ro else ri
        _shpXs(i) = sx + Math.cos(a).toFloat * rad
        _shpYs(i) = sy + Math.sin(a).toFloat * rad * 0.92f
        i += 1
      }
      pass match {
        case 0 => sb.fillFan(sx, sy, _shpXs, _shpYs, 10, outline(r), outline(g), outline(b), 0.9f * p)
        case 1 => sb.fillFan(sx, sy, _shpXs, _shpYs, 10, r * 0.9f, g * 0.86f, b * 0.7f, 0.98f * p)
        case _ => sb.fillFan(sx, sy, _shpXs, _shpYs, 10, mix(bright(r), 1f, 0.3f), mix(bright(g), 1f, 0.3f),
          mix(bright(b), 1f, 0.2f), 0.9f * p)
      }
      pass += 1
    }
    sb.fillOvalSoft(sx, sy, sz * 0.4f, sz * 0.37f, 1f, 1f, 0.9f, 0.95f * p, 0f, 12)
  }

  /** Plague: bubbles swelling on the skin of the orb and bursting, and a blotch or two under it. */
  private def toxicBubbles(sb: ShapeBatch, sx: Float, sy: Float, rx: Float, ry: Float,
                           r: Float, g: Float, b: Float, p: Float, tick: Int, id: Int): Unit = {
    sb.fillOval(sx + rx * 0.3f, sy + ry * 0.28f, rx * 0.26f, ry * 0.22f, r * 0.45f, g * 0.5f, b * 0.35f, 0.55f * p, 10)
    sb.fillOval(sx - rx * 0.36f, sy + ry * 0.12f, rx * 0.16f, ry * 0.14f, r * 0.45f, g * 0.5f, b * 0.35f, 0.5f * p, 8)
    var k = 0
    while (k < 3) {
      val t = ((tick * 0.022 + k * 0.33 + id * 0.19) % 1.0).toFloat
      val a = k * 2.1 + id * 0.7 - 0.9
      val bx = sx + Math.cos(a).toFloat * rx * 0.72f; val by = sy + Math.sin(a).toFloat * ry * 0.72f
      if (t < 0.84f) {
        val bs = rx * 0.24f * Math.min(1f, t * 2.4f)
        sb.fillOval(bx, by, bs + 1.4f, bs + 1.4f, outline(r), outline(g), outline(b), 0.75f * p, 10)
        sb.fillOval(bx, by, bs, bs, mix(r, 1f, 0.3f), mix(g, 1f, 0.3f), mix(b, 1f, 0.2f), 0.95f * p, 10)
        sb.fillOval(bx - bs * 0.32f, by - bs * 0.36f, bs * 0.34f, bs * 0.28f, 1f, 1f, 1f, 0.8f * p, 6)
      } else {
        // Popped: a ring flung wide and gone
        val pt = (t - 0.84f) / 0.16f
        sb.strokeOval(bx, by, rx * (0.24f + pt * 0.3f), rx * (0.24f + pt * 0.3f) * 0.8f, 1.6f * (1f - pt),
          mix(r, 1f, 0.4f), mix(g, 1f, 0.4f), mix(b, 1f, 0.3f), 0.8f * (1f - pt) * p, 10)
      }
      k += 1
    }
  }

  /** Sand: three arms of darker grit spiralling out of the middle and past the rim, whirling.
   *  Concentric bands, the first try, read as the rings of a coin. */
  private def sandSwirl(sb: ShapeBatch, sx: Float, sy: Float, rx: Float, ry: Float,
                        r: Float, g: Float, b: Float, p: Float, phase: Double): Unit = {
    var k = 0
    while (k < 3) {
      val a0 = phase * 1.9 + k * (Math.PI * 2 / 3)
      var i = 0
      while (i < 5) {
        val t = i / 4f
        val a = a0 + t * 2.0
        val rad = 0.15f + 0.97f * t
        _pvX(i) = sx + Math.cos(a).toFloat * rx * rad
        _pvY(i) = sy + Math.sin(a).toFloat * ry * rad
        _pvW(i) = rx * (0.24f - 0.17f * t)
        _pvA(i) = 0.85f * p * (1f - 0.3f * t)
        i += 1
      }
      sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 5, r * 0.58f, g * 0.46f, b * 0.3f)
      k += 1
    }
  }

  /** Sand: grains streaming off the back and spreading out, darker than the dust they fly in. */
  private def sandGrains(sb: ShapeBatch, sx: Float, sy: Float, ndx: Float, ndy: Float, sz: Float,
                         tailLen: Float, p: Float, tick: Int, id: Int): Unit = {
    val px = -ndy; val py = ndx
    var i = 0
    while (i < 8) {
      val t = ((tick * 0.06 + i * 0.125 + id * 0.31) % 1.0).toFloat
      val lat = hash2(id, i) * sz * (0.3f + 0.7f * t)
      val gx = sx - ndx * (sz * 0.6f + t * tailLen) + px * lat
      val gy = sy - ndy * (sz * 0.6f + t * tailLen) + py * lat + t * t * 6f
      val gs = 1.2f + (1f - t) * 1.4f
      sb.fillRect(gx - gs * 0.5f, gy - gs * 0.5f, gs, gs, 0.45f, 0.34f, 0.18f, 0.9f * (1f - t) * p)
      i += 1
    }
  }

  /** Mud flies unlit: no tail of light, just drops flung back off it and a dark smear. */
  private def mudTrail(sb: ShapeBatch, sx: Float, sy: Float, ndx: Float, ndy: Float, sz: Float,
                       p: Float, tick: Int, id: Int): Unit = {
    val px = -ndy; val py = ndx
    var i = 0
    while (i < 3) {
      val t = i / 2f
      _pvX(i) = sx - ndx * sz * (0.3f + t * 1.5f); _pvY(i) = sy - ndy * sz * (0.3f + t * 1.5f) + t * t * 3f
      _pvW(i) = sz * 0.9f * (1f - t * 0.95f); _pvA(i) = 0.5f * p * (1f - t)
      i += 1
    }
    sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 3, 0.24f, 0.17f, 0.09f)
    i = 0
    while (i < 4) {
      val t = ((tick * 0.05 + i * 0.25 + id * 0.41) % 1.0).toFloat
      val lat = (if ((i & 1) == 0) 1f else -1f) * sz * (0.25f + 0.5f * t)
      val mx = sx - ndx * (sz * 0.7f + t * sz * 2.2f) + px * lat
      val my = sy - ndy * (sz * 0.7f + t * sz * 2.2f) + py * lat + t * t * sz * 0.8f
      val ms = sz * (0.2f - t * 0.1f) * (0.8f + 0.2f * (i % 3))
      sb.fillOval(mx, my, ms + 1.3f, ms * 0.85f + 1.3f, 0.12f, 0.08f, 0.04f, 0.8f * (1f - t) * p, 8)
      sb.fillOval(mx, my, ms, ms * 0.85f, 0.40f, 0.29f, 0.15f, 0.95f * (1f - t) * p, 8)
      i += 1
    }
  }

  /** A glob of wet mud: lumpy — three lobes inked as one — dark, and glossy where it catches the
   *  light. Mud doesn't glow, so none of the orb's light is on it. */
  private def mudBody(sb: ShapeBatch, sx: Float, sy: Float, ndx: Float, ndy: Float, rx: Float, ry: Float,
                      p: Float, phase: Double): Unit = {
    val px = -ndy; val py = ndx
    val wob = Math.sin(phase * 1.7).toFloat
    val l2x = sx - ndx * rx * 0.45f + px * rx * 0.34f; val l2y = sy - ndy * ry * 0.45f + py * ry * 0.34f
    val l3x = sx - ndx * rx * 0.38f - px * rx * 0.36f; val l3y = sy - ndy * ry * 0.38f - py * ry * 0.36f
    val r1 = 0.94f + 0.05f * wob; val r2 = 0.64f - 0.05f * wob; val r3 = 0.56f + 0.04f * wob
    var pass = 0
    while (pass < 2) {
      val grow = if (pass == 0) 2f else 0f
      val cr = if (pass == 0) 0.10f else 0.33f
      val cg = if (pass == 0) 0.07f else 0.23f
      val cb = if (pass == 0) 0.03f else 0.12f
      val ca = if (pass == 0) 0.9f * p else 0.98f * p
      sb.fillOval(l2x, l2y, rx * r2 + grow, ry * r2 + grow, cr, cg, cb, ca, 14)
      sb.fillOval(l3x, l3y, rx * r3 + grow, ry * r3 + grow, cr, cg, cb, ca, 14)
      sb.fillOval(sx, sy, rx * r1 + grow, ry * r1 + grow, cr, cg, cb, ca, 18)
      pass += 1
    }
    sb.fillOvalSoft(sx + KEY_LIGHT_X * rx * 0.25f, sy + KEY_LIGHT_Y * ry * 0.25f, rx * 0.8f, ry * 0.8f,
      0.52f, 0.38f, 0.2f, 0.9f * p, 0f, 16)
    // Wet: two hard highlights
    sb.fillOval(sx + KEY_LIGHT_X * rx * 0.45f, sy + KEY_LIGHT_Y * ry * 0.5f, rx * 0.2f, ry * 0.13f,
      1f, 0.97f, 0.9f, 0.85f * p, 10)
    sb.fillOval(sx + KEY_LIGHT_X * rx * 0.1f, sy + KEY_LIGHT_Y * ry * 0.62f, rx * 0.07f, ry * 0.06f,
      1f, 0.97f, 0.9f, 0.7f * p, 6)
  }

  /** Nanites: a dozen specks on tilted orbits of their own round a small core, drawn in two
   *  halves like the orbit style so the swarm has depth. Squares, since they are machines. */
  private def nanites(sb: ShapeBatch, sx: Float, sy: Float, rx: Float, front: Boolean,
                      r: Float, g: Float, b: Float, p: Float, phase: Double): Unit = {
    val lr = mix(r, bright(r), 0.5f); val lg = mix(g, bright(g), 0.5f); val lb = mix(b, bright(b), 0.5f)
    var i = 0
    while (i < 16) {
      val th = phase * (1.35 + 0.18 * (i % 4)) + i * (Math.PI * 2 / 16)
      val st = Math.sin(th).toFloat
      if ((st >= 0f) == front) {
        val ct = Math.cos(th).toFloat
        val oa = rx * (0.8f + 0.2f * (i % 3)); val ob = oa * (0.3f + 0.16f * (i % 2))
        val tilt = i * 0.9f
        val c = Math.cos(tilt).toFloat; val s = Math.sin(tilt).toFloat
        val ox = oa * ct; val oy = ob * st
        val nx = sx + ox * c - oy * s; val ny = sy + (ox * s + oy * c) * 0.8f
        val ns = 3.2f + (if (front) 0.8f else 0f)
        var pass = 0
        while (pass < 2) {
          val e = if (pass == 0) ns + 1.3f else ns
          _polyXs4(0) = nx; _polyYs4(0) = ny - e
          _polyXs4(1) = nx + e; _polyYs4(1) = ny
          _polyXs4(2) = nx; _polyYs4(2) = ny + e
          _polyXs4(3) = nx - e; _polyYs4(3) = ny
          if (pass == 0) sb.fillPolygon(_polyXs4, _polyYs4, 4, outline(r), outline(g), outline(b), 0.9f * p)
          else sb.fillPolygon(_polyXs4, _polyYs4, 4, lr, lg, lb, (if (front) 1f else 0.82f) * p)
          pass += 1
        }
      }
      i += 1
    }
  }

  /** The swarm's wake: nanites falling out of formation behind it. */
  private def naniteStream(sb: ShapeBatch, sx: Float, sy: Float, ndx: Float, ndy: Float, sz: Float,
                           tailLen: Float, r: Float, g: Float, b: Float, p: Float, tick: Int, id: Int): Unit = {
    val px = -ndy; val py = ndx
    val lr = mix(bright(r), 1f, 0.2f); val lg = mix(bright(g), 1f, 0.2f); val lb = mix(bright(b), 1f, 0.2f)
    var i = 0
    while (i < 7) {
      val t = ((tick * 0.055 + i / 7.0 + id * 0.27) % 1.0).toFloat
      val lat = hash2(id + 5, i) * sz * (0.35f + 0.4f * t)
      val nx = sx - ndx * (sz * 0.5f + t * tailLen) + px * lat
      val ny = sy - ndy * (sz * 0.5f + t * tailLen) + py * lat
      val ns = 2.4f * (1f - t * 0.6f)
      sb.fillRect(nx - ns, ny - ns, ns * 2, ns * 2, lr, lg, lb, 0.9f * (1f - t) * p)
      i += 1
    }
  }
}
