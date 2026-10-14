package com.gridgame.client.render.projectiles

import com.gridgame.common.model.Projectile
import com.gridgame.client.gl.ShapeBatch
import GLProjectileRenderers.Renderer
import ProjectileKit._
import Silhouettes._
import Tethers._
import Glyphs._

/** Thrown restraints (chainProj): a pull on a rope or a chain back to the thrower's hand — the
  * Pirate's grappling hook, the Gladiator's lasso, the Puppeteer's strings, the Chef's meat hook — a
  * tumbling manacle trailing links, a loop of links round a padlock. */
private[render] object Chains {
  final val CHN_ROPE = 0     // a grappling hook on a rope (the Pirate's)
  final val CHN_SHACKLE = 1
  final val CHN_LOCK = 2
  final val CHN_HOOK = 3     // the Chef's meat hook, on a chain
  final val CHN_LASSO = 4    // the Gladiator's lasso
  final val CHN_STRING = 5   // the Puppeteer's strings

  /**
   * Thrown restraint, drawn as the object that hits rather than a line out ahead of it.
   *
   *  - CHN_ROPE, CHN_LASSO, CHN_STRING, CHN_HOOK: pulls, tied back to the hand that threw them (see
   *    Tethers) and as solid at the end of their reach as anywhere, since what they do there is be
   *    reeled back in: a grappling hook, a lasso's open loop, three puppet strings ending in finger
   *    rings, a butcher's hook on a chain.
   *  - CHN_SHACKLE: a heavy manacle tumbling end over end, trailing a few swinging links.
   *  - CHN_LOCK: a loop of links spinning around a padlock — lockdown, read at a glance.
   *
   * The old tether ran out AHEAD of the hitbox to a hook and began at a round "cleat", so
   * it read as a line with a ball on one end and a hook on the other, arriving early.
   */
  private[projectiles] def chainProj(kind: Int, r: Float, g: Float, b: Float, worldLen: Float = 6f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 23) * 0.3
      computeAllDynamics(proj, r, g, b, phase)
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy
      kind match {
        case CHN_ROPE => grapple(sb, proj, sx, sy, ndx, ndy, phase, r, g, b)
        case CHN_LASSO => lasso(sb, proj, sx, sy, ndx, ndy, phase, r, g, b)
        case CHN_STRING => puppetStrings(sb, proj, sx, sy, ndx, ndy, phase, tick)
        case CHN_HOOK => meatHook(sb, proj, sx, sy, ndx, ndy, phase)
        case _ => restraint(kind, sb, proj, sx, sy, ndx, ndy, phase, tick, r, g, b)
      }
    }

  /** The manacle and the padlock loop: compact objects, flying free. */
  private def restraint(kind: Int, sb: ShapeBatch, proj: Projectile, sx: Float, sy: Float, ndx: Float, ndy: Float,
                        phase: Double, tick: Int, r: Float, g: Float, b: Float): Unit = {
      val p = (0.88f + 0.12f * Math.sin(phase * _stPulseMult).toFloat) * dynAlpha
      val dr = _evoR; val dg = _evoG; val db = _evoB
      // They carry more area than a hook
      val ds = Math.min(dynScale, 1.3f) * 1.25f
      val px = -ndy; val py = ndx
      val heading = Math.atan2(ndy, ndx)
      val hiR = mix(bright(r), 1f, 0.3f); val hiG = mix(bright(g), 1f, 0.3f); val hiB = mix(bright(b), 1f, 0.3f)
      kind match {
        case CHN_SHACKLE =>
          // A few links swinging behind, fading
          var i = 1
          while (i <= 3) {
            val t = i / 3.5f
            val swing = Math.sin(phase * 1.8 + i * 0.9).toFloat * 5f * ds * t
            val lx = sx - ndx * (9f + i * 8f) * ds + px * swing
            val ly = sy - ndy * (9f + i * 8f) * ds + py * swing
            val flat = (i & 1) == 1
            val la = if (flat) 5.4f * ds else 2.4f * ds
            val lb = if (flat) 3f * ds else 4.4f * ds
            val a = 1f - t * 0.7f
            strokeRotEllipse(sb, lx, ly, ndx, ndy, la, lb, 3.6f, 0.06f, 0.06f, 0.08f, 0.8f * a * p, 10)
            strokeRotEllipse(sb, lx, ly, ndx, ndy, la, lb, 2.2f, dr, dg, db, 0.95f * a * p, 10)
            i += 1
          }
          // The manacle, tumbling: its ring foreshortens as it turns over
          val spin = phase * 1.5
          val ma = spin * 0.35
          val mx = Math.cos(ma).toFloat; val my = Math.sin(ma).toFloat * ISO_Y
          val ml = Math.max(0.001f, Math.sqrt(mx * mx + my * my).toFloat)
          val ux = mx / ml; val uy = my / ml
          val R = 11f * ds
          val minor = R * (0.3f + 0.7f * Math.abs(Math.cos(spin).toFloat))
          sb.fillOvalSoft(sx, sy, R * 2f * dynGlow, R * 1.6f * dynGlow, dr, dg, db, 0.24f * p, 0f, 12)
          strokeRotEllipse(sb, sx, sy, ux, uy, R, minor, 7.5f * ds, 0.06f, 0.06f, 0.08f, 0.88f * p, 18)
          strokeRotEllipse(sb, sx, sy, ux, uy, R, minor, 4.8f * ds, dr, dg, db, 0.97f * p, 18)
          strokeRotEllipse(sb, sx - 0.6f, sy - 0.8f, ux, uy, R * 0.98f, minor * 0.96f, 1.4f * ds, hiR, hiG, hiB, 0.55f * p, 18)
          // Hinge at one end of the ring, lock plate at the other
          val hx = sx + ux * R; val hy = sy + uy * R
          sb.fillOval(hx, hy, 4.2f * ds, 4.2f * ds, 0.06f, 0.06f, 0.08f, 0.88f * p, 8)
          sb.fillOval(hx, hy, 3f * ds, 3f * ds, DKSTEEL_R, DKSTEEL_G, DKSTEEL_B, 0.97f * p, 8)
          val lx = sx - ux * R; val ly = sy - uy * R
          sb.fillRoundedRect(lx - 5.2f * ds, ly - 4.2f * ds, 10.4f * ds, 8.4f * ds, 2f, 0.06f, 0.06f, 0.08f, 0.88f * p)
          sb.fillRoundedRect(lx - 4f * ds, ly - 3f * ds, 8f * ds, 6f * ds, 1.5f, dr, dg, db, 0.97f * p)
          sb.fillOval(lx, ly - 0.6f * ds, 1.3f * ds, 1.3f * ds, 0.05f, 0.05f, 0.06f, 0.95f * p, 6)
          sb.strokeLine(lx, ly, lx, ly + 2f * ds, 1.2f * ds, 0.05f, 0.05f, 0.06f, 0.95f * p)

        case _ =>
          // CHN_LOCK: a loop of links spinning around a padlock
          val spin = phase * 0.9
          val R = 15f * ds
          sb.fillOvalSoft(sx, sy, R * 1.8f * dynGlow, R * 1.4f * dynGlow, dr, dg, db, 0.26f * p, 0f, 14)
          var pass = 0
          while (pass < 2) {
            var i = 0
            while (i < 8) {
              val a = spin + i * Math.PI / 4
              val lx = sx + Math.cos(a).toFloat * R; val ly = sy + Math.sin(a).toFloat * R * ISO_Y
              val tx = -Math.sin(a).toFloat; val ty = Math.cos(a).toFloat * ISO_Y
              val tl = Math.max(0.001f, Math.sqrt(tx * tx + ty * ty).toFloat)
              val flat = (i & 1) == 0
              val la = if (flat) 6f * ds else 3.6f * ds
              val lb = if (flat) 3.2f * ds else 2.2f * ds
              if (pass == 0) strokeRotEllipse(sb, lx, ly, tx / tl, ty / tl, la, lb, 3.8f, 0.06f, 0.06f, 0.08f, 0.85f * p, 10)
              else {
                strokeRotEllipse(sb, lx, ly, tx / tl, ty / tl, la, lb, 2.4f, dr, dg, db, 0.97f * p, 10)
                strokeRotEllipse(sb, lx - 0.5f, ly - 0.6f, tx / tl, ty / tl, la * 0.9f, lb * 0.8f, 1f, hiR, hiG, hiB, 0.5f * p, 8)
              }
              i += 1
            }
            pass += 1
          }
          // Padlock riding the front of the loop
          val lx = sx + Math.cos(heading).toFloat * R * 0.2f
          val ly = sy + Math.sin(heading).toFloat * R * 0.2f * ISO_Y
          sb.strokeArc(lx, ly - 4f * ds, 4.4f * ds, 5f * ds, 3.14f, 3.14f, 4f * ds, 0.06f, 0.06f, 0.08f, 0.88f * p, 8)
          sb.strokeArc(lx, ly - 4f * ds, 4.4f * ds, 5f * ds, 3.14f, 3.14f, 2.4f * ds, STEEL_R, STEEL_G, STEEL_B, 0.97f * p, 8)
          sb.fillRoundedRect(lx - 7f * ds, ly - 4.6f * ds, 14f * ds, 11.5f * ds, 2.5f, 0.06f, 0.06f, 0.08f, 0.9f * p)
          sb.fillRoundedRect(lx - 5.8f * ds, ly - 3.4f * ds, 11.6f * ds, 9.1f * ds, 2f, GOLD_R, GOLD_G, GOLD_B, 0.98f * p)
          sb.fillRect(lx - 5.8f * ds, ly - 3.4f * ds, 11.6f * ds, 2.2f * ds, 1f, 0.92f, 0.6f, 0.45f * p)
          sb.fillOval(lx, ly + 0.2f * ds, 1.6f * ds, 1.6f * ds, 0.05f, 0.04f, 0.03f, 0.95f * p, 6)
          sb.strokeLine(lx, ly + 0.2f * ds, lx, ly + 3f * ds, 1.4f * ds, 0.05f, 0.04f, 0.03f, 0.95f * p)
      }
      drawChargeCrackle(sx, sy, 14f * ds, r, g, b, p, sb, phase, proj.chargeLevel)
  }

  // ── Rope ──

  /** A rope from where it ties on, (startX, startY), back to the thrower: two strands laid round
    * each other, inked, taut but hanging a little in the middle, fading in from the thrower's hand. */
  private def rope(sb: ShapeBatch, proj: Projectile, sx: Float, sy: Float, startX: Float, startY: Float,
                   phase: Double, r: Float, g: Float, b: Float): Unit = {
    if (!layTether(proj, sx, sy, phase, 7f, 5f, 7f, startX = startX, startY = startY)) return
    shapeTether(3.6f, 4.2f, 1f, 1f, 16f)
    var strand = 0
    while (strand < 2) {
      val ph = strand * Math.PI
      var i = 0
      while (i < _tN) {
        val twist = Math.sin(i * (Math.PI / 2) + ph + phase * 0.8).toFloat * 1.5f
        _pvX(i) = _tX(i) + _tNX(i) * twist; _pvY(i) = _tY(i) + _tNY(i) * twist
        _pvW(i) = _tW(i) + 2.2f; _pvA(i) = _tA(i) * 0.9f
        i += 1
      }
      sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, _tN, r * 0.2f, g * 0.16f, b * 0.12f)
      i = 0
      while (i < _tN) { _pvW(i) = _tW(i); _pvA(i) = _tA(i); i += 1 }
      val k = if (strand == 0) 1f else 0.78f
      sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, _tN, r * k, g * k, b * k)
      strand += 1
    }
  }

  /**
   * The Pirate's grapple: a grappling hook on its rope — an iron shank, the ring the rope is made fast
   * to, and three tines curving back from its crown, points toward the thrower so they bite as it is
   * hauled in. Inked as one piece. Its tines used to be six loose strokes, translucent where they
   * crossed, which read as a broken fork.
   */
  private def grapple(sb: ShapeBatch, proj: Projectile, sx: Float, sy: Float, ndx: Float, ndy: Float,
                      phase: Double, r: Float, g: Float, b: Float): Unit = {
    val k = 13.5f
    headingFrame(sx - ndx * 0.5f * k, sy - ndy * 0.5f * k, ndx, ndy, k)
    rope(sb, proj, sx, sy, fx(-1.05f, 0f), fy(-1.05f, 0f), phase, r, g, b)
    sb.fillOvalSoft(sx, sy, 1.5f * k, 1.2f * k, 0.9f, 0.85f, 0.7f, 0.16f, 0f, 12)
    var pass = 0
    while (pass < 2) {
      val ink = pass == 0
      val wAdd = if (ink) 2.6f else 0f
      val cr = if (ink) 0.06f else STEEL_R * 0.86f; val cg = if (ink) 0.06f else STEEL_G * 0.86f
      val cb = if (ink) 0.08f else STEEL_B * 0.88f
      val ca = if (ink) 0.95f else 1f
      // The ring at its back, the shank, then the tines: either side, and one toward us, foreshortened
      sb.strokeOval(fx(-1.0f, 0f), fy(-1.0f, 0f), 0.2f * k, 0.18f * k, 0.12f * k + wAdd, cr, cg, cb, ca, 12)
      sb.strokeLine(fx(-0.82f, 0f), fy(-0.82f, 0f), fx(0.42f, 0f), fy(0.42f, 0f), 0.24f * k + wAdd, cr, cg, cb, ca)
      var t = 0
      while (t < 3) {
        val side = t - 1f
        if (t == 1) {
          _pvX(0) = fx(0.36f, 0f); _pvY(0) = fy(0.36f, 0f)
          _pvX(1) = fx(0.62f, 0.04f); _pvY(1) = fy(0.62f, 0.04f)
          _pvX(2) = fx(0.58f, 0.2f); _pvY(2) = fy(0.58f, 0.2f)
          _pvX(3) = fx(0.44f, 0.26f); _pvY(3) = fy(0.44f, 0.26f)
        } else {
          _pvX(0) = fx(0.36f, 0f); _pvY(0) = fy(0.36f, 0f)
          _pvX(1) = fx(0.62f, side * 0.34f); _pvY(1) = fy(0.62f, side * 0.34f)
          _pvX(2) = fx(0.5f, side * 0.66f); _pvY(2) = fy(0.5f, side * 0.66f)
          _pvX(3) = fx(0.16f, side * 0.74f); _pvY(3) = fy(0.16f, side * 0.74f)
        }
        sb.strokePolylineTapered(_pvX, _pvY, 4, 0.24f * k + wAdd, 0.05f * k + wAdd * 0.6f, cr, cg, cb, ca, ca)
        t += 1
      }
      pass += 1
    }
    // Light along the shank, and the crown
    sb.strokeLine(fx(-0.7f, -0.05f), fy(-0.7f, -0.05f), fx(0.3f, -0.05f), fy(0.3f, -0.05f), 0.07f * k, 1f, 1f, 1f, 0.55f)
    sb.fillOval(fx(0.38f, 0f), fy(0.38f, 0f), 0.17f * k, 0.15f * k, DKSTEEL_R, DKSTEEL_G, DKSTEEL_B, 1f, 10)
  }

  /**
   * The Gladiator's lasso: its rope ending in a running loop flung open over the ground, the knot where
   * it closes, and the loop drawn tight round what it caught. It was the Pirate's grappling hook.
   */
  private def lasso(sb: ShapeBatch, proj: Projectile, sx: Float, sy: Float, ndx: Float, ndy: Float,
                    phase: Double, r: Float, g: Float, b: Float): Unit = {
    // Flung open, swinging a little, and cinched shut on a catch
    val cinch = if (_holding) 0.62f else 1f
    val rx = (17f + Math.sin(phase * 0.9).toFloat * 1.2f) * cinch
    val ry = rx * 0.55f
    // The knot is on the side toward the thrower
    val kn = Math.atan2(-ndy / 0.55f, -ndx)
    val kx = sx + Math.cos(kn).toFloat * rx; val ky = sy + Math.sin(kn).toFloat * ry
    rope(sb, proj, sx, sy, kx, ky, phase, r, g, b)
    sb.strokeOval(sx, sy, rx, ry, 4.4f + 2.6f, r * 0.2f, g * 0.16f, b * 0.12f, 0.95f, 24)
    sb.strokeOval(sx, sy, rx, ry, 4.4f, r, g, b, 1f, 24)
    // The lay of the rope round the loop
    var i = 0
    while (i < 14) {
      val an = i * (2 * Math.PI / 14) + phase * 0.2
      val c = Math.cos(an).toFloat; val s = Math.sin(an).toFloat
      val x0 = sx + c * (rx - 1.6f); val y0 = sy + s * (ry - 1.4f)
      val x1 = sx + c * (rx + 1.6f) - s * 2f; val y1 = sy + s * (ry + 1.4f) + c * 1.2f
      sb.strokeLine(x0, y0, x1, y1, 1.1f, r * 0.6f, g * 0.6f, b * 0.6f, 0.85f)
      i += 1
    }
    // The knot (the honda) the loop runs through
    sb.fillOval(kx, ky, 4.6f + 1.4f, 3.8f + 1.4f, r * 0.2f, g * 0.16f, b * 0.12f, 0.95f, 10)
    sb.fillOval(kx, ky, 4.6f, 3.8f, r * 0.9f, g * 0.9f, b * 0.9f, 1f, 10)
    sb.fillOval(kx - 1.2f, ky - 1.2f, 1.6f, 1.1f, mix(r, 1f, 0.5f), mix(g, 1f, 0.5f), mix(b, 1f, 0.5f), 0.9f, 6)
  }

  /**
   * The Puppeteer's strings: three fine threads out of its hand, fanning apart as they reach, each
   * ending in a little brass finger-ring that hooks on to whoever it catches, a violet shimmer running
   * out along them. It was the Pirate's grappling hook on the Pirate's rope.
   */
  private def puppetStrings(sb: ShapeBatch, proj: Projectile, sx: Float, sy: Float, ndx: Float, ndy: Float,
                            phase: Double, tick: Int): Unit = {
    if (!layTether(proj, sx, sy, phase, 8f, 6f, 4f)) return
    shapeTether(1.4f, 1.4f, 1f, 1f, 18f)
    val px = -ndy; val py = ndx
    var s = 0
    while (s < 3) {
      // Spread across the way they fly at the head, closing to the one hand at the root
      val spread = (s - 1) * 8f + (if (s == 1) 0f else Math.sin(phase * 1.3 + s).toFloat * 1.2f)
      var i = 0
      while (i < _tN) {
        val t = _tT(i)
        val k = (1f - t) * (1f - t)
        _pvX(i) = _tX(i) + px * spread * k; _pvY(i) = _tY(i) + py * spread * k + (if (s == 1) -3f * (1f - t) else 0f)
        i += 1
      }
      // A violet shimmer round each, then its ink, then the thread
      i = 0
      while (i < _tN) { _pvW(i) = 6.4f; _pvA(i) = 0.26f * _tA(i); i += 1 }
      sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, _tN, 0.72f, 0.45f, 1f)
      i = 0
      while (i < _tN) { _pvW(i) = 3.4f; _pvA(i) = 0.9f * _tA(i); i += 1 }
      sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, _tN, 0.2f, 0.08f, 0.3f)
      i = 0
      while (i < _tN) { _pvW(i) = 1.8f; _pvA(i) = _tA(i); i += 1 }
      sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, _tN, 0.97f, 0.94f, 1f)
      // Its ring at the head
      val rx = _pvX(0); val ry = _pvY(0)
      sb.strokeOval(rx, ry, 3.4f, 2.7f, 2.6f + 1.6f, 0.2f, 0.12f, 0.04f, 0.95f, 12)
      sb.strokeOval(rx, ry, 3.4f, 2.7f, 2.6f, 0.98f, 0.8f, 0.36f, 1f, 12)
      // A mote of violet running out along it
      val mt = Glyphs.loop(tick, 0.03, s * 0.33 + proj.id * 0.17)
      val mi = Math.min(_tN - 1, ((1f - mt) * (_tN - 1)).toInt)
      sb.fillOvalSoft(_pvX(mi), _pvY(mi), 5f, 4.2f, 0.75f, 0.5f, 1f, 0.6f * _tA(mi), 0f, 8)
      sb.fillOval(_pvX(mi), _pvY(mi), 1.3f, 1.3f, 1f, 1f, 1f, 0.95f * _tA(mi), 6)
      s += 1
    }
  }

  /**
   * The Chef's meat hook: a butcher's hook — an eye, a shank, and a hook curling round to a point that
   * faces the Chef, so it bites as it is hauled in — on a steel chain back to the Chef's hand. It was
   * the Death Knight's sickle hook, in grey, on a rope.
   */
  private def meatHook(sb: ShapeBatch, proj: Projectile, sx: Float, sy: Float, ndx: Float, ndy: Float,
                       phase: Double): Unit = {
    val k = 14.5f
    headingFrame(sx - ndx * 0.34f * k, sy - ndy * 0.34f * k, ndx, ndy, k)
    // The chain, from the hook's eye back to the Chef: links lying flat and standing on edge by turns
    if (layTether(proj, sx, sy, phase, 6f, 4f, 6f, startX = fx(-1.06f, 0f), startY = fy(-1.06f, 0f))) {
      shapeTether(4f, 4.4f, 1f, 1f, 16f)
      var i = 0
      while (i < _tN - 1) {
        val x0 = _tX(i); val y0 = _tY(i); val x1 = _tX(i + 1); val y1 = _tY(i + 1)
        val mx = (x0 + x1) * 0.5f; val my = (y0 + y1) * 0.5f
        val dx = x1 - x0; val dy = y1 - y0
        val dl = Math.max(1e-3f, Math.sqrt(dx * dx + dy * dy).toFloat)
        val ux = dx / dl; val uy = dy / dl
        val a = _tA(i)
        val half = dl * 0.62f
        if ((i & 1) == 0) {
          val n = rotEllipsePts(mx, my, ux, uy, half, 2.6f, 12)
          sb.strokePolygon(_gx, _gy, n, 3.6f, 0.06f, 0.06f, 0.08f, 0.9f * a)
          sb.strokePolygon(_gx, _gy, n, 1.8f, STEEL_R * 0.86f, STEEL_G * 0.86f, STEEL_B * 0.88f, a)
        } else {
          sb.strokeLine(mx - ux * half, my - uy * half, mx + ux * half, my + uy * half, 3.6f, 0.06f, 0.06f, 0.08f, 0.9f * a)
          sb.strokeLine(mx - ux * half, my - uy * half, mx + ux * half, my + uy * half, 1.8f, DKSTEEL_R, DKSTEEL_G, DKSTEEL_B, a)
        }
        i += 1
      }
    }
    sb.fillOvalSoft(sx, sy, 1.4f * k, 1.2f * k, 0.9f, 0.9f, 1f, 0.14f, 0f, 12)
    // The hook: from its eye along the shank, then round to its point, facing back
    _pvX(0) = fx(-0.82f, 0f); _pvY(0) = fy(-0.82f, 0f)
    _pvX(1) = fx(0.02f, 0f); _pvY(1) = fy(0.02f, 0f)
    var j = 0
    while (j < 6) {
      val an = -Math.PI / 2 + j * (Math.PI * 1.15 / 5)
      val hx = 0.06f + Math.cos(an).toFloat * 0.36f; val hy = 0.36f + Math.sin(an).toFloat * 0.36f
      _pvX(2 + j) = fx(hx, hy); _pvY(2 + j) = fy(hx, hy)
      j += 1
    }
    _pvX(8) = fx(-0.42f, 0.38f); _pvY(8) = fy(-0.42f, 0.38f)
    var pass = 0
    while (pass < 2) {
      val ink = pass == 0
      val wAdd = if (ink) 2.8f else 0f
      val cr = if (ink) 0.06f else STEEL_R * 0.9f; val cg = if (ink) 0.06f else STEEL_G * 0.9f
      val cb = if (ink) 0.08f else STEEL_B * 0.92f
      sb.strokePolylineTapered(_pvX, _pvY, 9, 0.26f * k + wAdd, 0.04f * k + wAdd * 0.6f, cr, cg, cb, if (ink) 0.95f else 1f,
        if (ink) 0.95f else 1f)
      sb.strokeOval(fx(-0.96f, 0f), fy(-0.96f, 0f), 0.16f * k, 0.15f * k, 0.1f * k + wAdd, cr, cg, cb, if (ink) 0.95f else 1f, 12)
      pass += 1
    }
    // Light along it, and a glint on its point
    sb.strokeLine(fx(-0.7f, -0.06f), fy(-0.7f, -0.06f), fx(-0.05f, -0.06f), fy(-0.05f, -0.06f), 0.06f * k, 1f, 1f, 1f, 0.6f)
    sb.fillStarFlare(_pvX(8), _pvY(8), 0.4f * k, 1.6f, (phase * 0.5).toFloat, 0.5f, 1f, 1f, 1f, 0.6f)
  }
}
