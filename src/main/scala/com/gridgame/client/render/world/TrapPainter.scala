package com.gridgame.client.render
package world

import com.gridgame.common.model._

/**
 * Traps on the ground and going off, drawn flat in the ground pass so the walls in front of them cover
 * them (design/traps.md, *Drawn*).
 *
 * Every kind fills most of its cell, inked in a dark version of its own colour as the characters
 * are, on a dark seat of ground, with something bright on it: the ink and the seat carry it over
 * snow and sand, the bright part over the dark floors. And something on it moves (a blink, a
 * flicker, a glint now and then, a breath), because a still decal on a busy ground is the last thing
 * the eye finds. Ours and our allies' are drawn plainly and send a ring out over the ground they
 * watch as they arm and every few seconds after; everyone else's are drawn fainter, there to be found
 * by their outline and by the glint that catches on them now and then.
 */
private[render] final class TrapPainter(ctx: RenderContext) {
  import ctx._
  import TrapPainter._

  // Scratch for one trap's polygons and polylines: a tooth, a flame, a mote, the web's spiral
  private val _xs = new Array[Float](SPIRAL_POINTS)
  private val _ys = new Array[Float](SPIRAL_POINTS)

  // What the traps add to the additive pass after them all — a rune's glow, a diode lit, a glint —
  // so the blend is switched once a frame rather than once a trap
  private val _gStar = new Array[Boolean](MAX_GLOWS)
  private val _gX = new Array[Float](MAX_GLOWS)
  private val _gY = new Array[Float](MAX_GLOWS)
  private val _gRx = new Array[Float](MAX_GLOWS)
  private val _gRy = new Array[Float](MAX_GLOWS)
  private val _gR = new Array[Float](MAX_GLOWS)
  private val _gG = new Array[Float](MAX_GLOWS)
  private val _gB = new Array[Float](MAX_GLOWS)
  private val _gA = new Array[Float](MAX_GLOWS)
  private var _glows = 0

  // The trap being drawn: its colour, its ink, how plainly it is drawn, and its glint (how far
  // through it is, 0 to 1, and how bright: 0 while there is none)
  private var _r = 0f; private var _g = 0f; private var _b = 0f
  private var _ir = 0f; private var _ig = 0f; private var _ib = 0f
  private var _alpha = 1f
  private var _glintT = 0f
  private var _glintA = 0f

  @inline private def onScreen(sx: Float, sy: Float): Boolean =
    sx > -60f && sx < canvasW + 60f && sy > -60f && sy < canvasH + 60f

  /**
   * Traps on the ground and traps going off, drawn flat in the ground pass: a wall in front of
   * one covers it when the depth pass draws that wall, with nothing asked of EntityCollector.
   */
  def draw(): Unit = {
    val traps = client.getTraps
    val effects = client.getTrapEffects
    if (traps.isEmpty && effects.isEmpty) return
    val now = frameTimeMs
    beginShapes()

    if (!traps.isEmpty) {
      _glows = 0
      val iter = traps.values().iterator()
      while (iter.hasNext) {
        val trap = iter.next()
        val sx = worldToScreenX(trap.x, trap.y).toFloat
        val sy = worldToScreenY(trap.x, trap.y).toFloat
        if (onScreen(sx, sy)) drawTrap(trap, sx, sy, now)
      }
      shapeBatch.setAlphaMultiplier(1f)
      if (_glows > 0) drawGlows()
    }

    if (!effects.isEmpty) {
      val iter = effects.entrySet().iterator()
      while (iter.hasNext) {
        val entry = iter.next()
        val data = entry.getValue
        val elapsed = now - data(0)
        if (elapsed > TRAP_EFFECT_MS) iter.remove()
        else {
          val wx = data(1) / 1000.0
          val wy = data(2) / 1000.0
          val sx = worldToScreenX(wx, wy).toFloat
          val sy = worldToScreenY(wx, wy).toFloat
          if (onScreen(sx, sy)) drawTrapEffect(data(3).toByte, sx, sy, elapsed / TRAP_EFFECT_MS)
        }
      }
    }
  }

  private def drawTrap(trap: Trap, sx: Float, sy: Float, now: Long): Unit = {
    val tDef = trap.defn
    if (tDef == null) return
    val friendly = client.isFriendlyTrap(trap)
    val arming = !trap.isArmed(now)
    var a = if (friendly) 1f else ENEMY_TRAP_ALPHA
    // Dimmer while it is still arming, and fading out as its time runs out
    if (arming) a *= ARMING_ALPHA
    val left = trap.expiresAt - now
    val fade = if (left < TRAP_FADE_MS) Math.max(0f, left / TRAP_FADE_MS) else 1f
    a *= fade
    if (a <= 0.01f) return
    _alpha = a

    intToRGB(tDef.colorRGB)
    _r = _rgb_r; _g = _rgb_g; _b = _rgb_b
    // Its ink: a dark version of its own colour, as a character's outline is
    _ir = _r * INK + 0.02f; _ig = _g * INK + 0.02f; _ib = _b * INK + 0.03f

    // The glint that catches on it now and then: on an armed trap only, and on an enemy's brighter
    // than the trap itself, since it is how one is found
    _glintA = 0f
    if (!arming) {
      val gp = (now + offset(trap.id, GLINT_PERIOD_MS)) % GLINT_PERIOD_MS
      if (gp < GLINT_MS) {
        _glintT = gp.toFloat / GLINT_MS
        _glintA = Math.sin(Math.PI * _glintT).toFloat * fade * (if (friendly) 1f else ENEMY_GLINT_ALPHA)
      }
    }

    shapeBatch.setAlphaMultiplier(a)
    // Its seat: a patch of darker ground under it, which is what lifts it off snow and sand
    shapeBatch.fillOvalSoft(sx, sy + 1.5f, 19f, 9.2f, _ir * 0.5f, _ig * 0.5f, _ib * 0.5f, 0.42f, 0f, 16)

    // The ground it watches, sent out as a ring as it arms and every few seconds after: ours only
    if (friendly && !arming) {
      val p = ((now - trap.armedAt) % PING_PERIOD_MS).toFloat / PING_MS
      if (p < 1f) ping(sx, sy, tDef.triggerRadius, p)
    }

    tDef.kind match {
      case TrapKind.JAWS => drawJawTrap(sx, sy)
      case TrapKind.MINE => drawMineTrap(sx, sy, now, trap.id)
      case TrapKind.POD  => drawPodTrap(sx, sy, now, trap.id)
      case TrapKind.RUNE => drawRuneTrap(sx, sy, now, trap.id, tDef.id == TrapType.FIRE_RUNE)
      case _             => drawWebTrap(sx, sy, now, trap.id)
    }

    // Arming: a ring closing on it. Nothing is going to happen until it has finished.
    if (arming) {
      val armT = 1f - (trap.armedAt - now).toFloat / Math.max(1, tDef.armDelayMs)
      val rx = ARM_RING_FROM - (ARM_RING_FROM - ARM_RING_TO) * armT
      val ra = 0.35f + 0.65f * armT
      shapeBatch.strokeOval(sx, sy, rx, rx * 0.5f, 3.0f, _ir, _ig, _ib, 0.45f * ra, 32)
      shapeBatch.strokeOval(sx, sy, rx, rx * 0.5f, 1.4f, lit(_r), lit(_g), lit(_b), 0.9f * ra, 32)
    }
  }

  /** A ring running out from the trap to the edge of the ground it watches (`radius` cells), over
    * the fraction `p` of its run: a circle on the ground is an ellipse twice as wide as it is tall. */
  private def ping(sx: Float, sy: Float, radius: Float, p: Float): Unit = {
    val to = radius * HW * SQRT2
    val rx = PING_FROM + (to - PING_FROM) * (1f - (1f - p) * (1f - p))
    val fa = (1f - p) * (1f - p * 0.5f)
    shapeBatch.strokeOval(sx, sy, rx, rx * 0.5f, 3.2f, _ir, _ig, _ib, 0.3f * fa, 40)
    shapeBatch.strokeOval(sx, sy, rx, rx * 0.5f, 1.5f, lit(_r), lit(_g), lit(_b), 0.85f * fa, 40)
  }

  /** A bear trap: its jaws standing open in a ring of teeth round the plate, a spring either side. */
  private def drawJawTrap(sx: Float, sy: Float): Unit = {
    val r = _r; val g = _g; val b = _b
    // The springs, under the ends of the jaws
    var side = -1f
    while (side <= 1f) {
      val kx = sx + 14.2f * side
      shapeBatch.fillOval(kx, sy + 0.4f, 5.6f, 3.2f, _ir, _ig, _ib, 0.95f, 12)
      shapeBatch.fillOval(kx, sy + 0.4f, 4.4f, 2.1f, r * 0.55f, g * 0.55f, b * 0.58f, 1f, 12)
      shapeBatch.strokeLine(kx - 2.6f, sy - 0.5f, kx + 2.6f, sy - 0.5f, 0.9f, lit(r), lit(g), lit(b), 0.8f)
      side += 2f
    }
    // The jaws' ring, inked round its outside: the far jaw in the light, the near one in shade, and
    // the pit between them dark
    shapeBatch.fillOval(sx, sy, 13.6f, 7.0f, _ir, _ig, _ib, 0.95f, 24)
    shapeBatch.fillArcBand(sx, sy, JAW_IN_X, JAW_IN_Y, JAW_OUT_X, JAW_OUT_Y, PI, PI, 14, r, g, b, 1f, 1f)
    shapeBatch.fillArcBand(sx, sy, JAW_IN_X, JAW_IN_Y, JAW_OUT_X, JAW_OUT_Y, 0f, PI, 14,
      r * 0.7f, g * 0.7f, b * 0.72f, 1f, 1f)
    shapeBatch.fillOval(sx, sy, JAW_IN_X, JAW_IN_Y, _ir * 1.4f, _ig * 1.4f, _ib * 1.4f, 1f, 20)
    // The light along the far jaw's back
    shapeBatch.strokeArc(sx, sy, JAW_OUT_X - 1f, JAW_OUT_Y - 0.6f, PI * 1.12f, PI * 0.62f, 1.0f,
      lit(r), lit(g), lit(b), 0.85f, 12)
    // Teeth along both jaws, pointing in at whatever steps on the plate: white over their ink, so
    // they read on the dark floors and the ink on snow
    var i = 0
    while (i < TEETH) {
      tooth(sx, sy, i, wide = true, JAW_IN_X + 0.6f, JAW_IN_Y + 0.3f, 4.8f, 2.4f, _ir, _ig, _ib, 0.95f)
      tooth(sx, sy, i, wide = false, JAW_IN_X, JAW_IN_Y, 5.6f, 2.8f, 0.96f, 0.96f, 0.98f, 1f)
      i += 1
    }
    // The hinges where the jaws meet
    shapeBatch.fillOval(sx - JAW_OUT_X + 0.4f, sy, 2.2f, 1.6f, _ir, _ig, _ib, 1f, 10)
    shapeBatch.fillOval(sx + JAW_OUT_X - 0.4f, sy, 2.2f, 1.6f, _ir, _ig, _ib, 1f, 10)
    shapeBatch.fillOval(sx - JAW_OUT_X + 0.1f, sy - 0.4f, 0.9f, 0.6f, lit(r), lit(g), lit(b), 0.9f, 8)
    shapeBatch.fillOval(sx + JAW_OUT_X - 0.7f, sy - 0.4f, 0.9f, 0.6f, lit(r), lit(g), lit(b), 0.9f, 8)
    // The pressure plate, in rust red: a colour no ground has
    shapeBatch.fillOval(sx, sy + 0.2f, 4.9f, 2.6f, _ir, _ig, _ib, 1f, 14)
    shapeBatch.fillOval(sx, sy + 0.2f, 4.0f, 1.9f, 0.78f, 0.3f, 0.18f, 1f, 14)
    shapeBatch.fillOval(sx - 1.2f, sy - 0.4f, 1.6f, 0.7f, 1f, 0.62f, 0.45f, 0.9f, 8)
    // The glint runs along the far jaw's back
    if (_glintA > 0f) {
      val ang = PI * (1.18f + 0.64f * _glintT)
      glint(sx + (JAW_OUT_X - 0.8f) * Math.cos(ang).toFloat, sy + (JAW_OUT_Y - 0.5f) * Math.sin(ang).toFloat, 1f, 1f, 0.95f)
    }
  }

  /** Tooth `i`: its base on the ellipse (bx, by) either side of its heading round the jaws (wide for
    * its ink, narrower for the tooth), its point inward on the ellipse (px, py). A triangle, so convex. */
  private def tooth(sx: Float, sy: Float, i: Int, wide: Boolean, bx: Float, by: Float, px: Float, py: Float,
                    r: Float, g: Float, b: Float, a: Float): Unit = {
    val e = i * 4 + (if (wide) 0 else 2)
    _xs(0) = sx + px * TOOTH_COS(i); _ys(0) = sy + py * TOOTH_SIN(i)
    _xs(1) = sx + bx * TOOTH_EDGE_COS(e); _ys(1) = sy + by * TOOTH_EDGE_SIN(e)
    _xs(2) = sx + bx * TOOTH_EDGE_COS(e + 1); _ys(2) = sy + by * TOOTH_EDGE_SIN(e + 1)
    shapeBatch.fillPolygon(_xs, _ys, 3, r, g, b, a)
  }

  /** A mine: a squat cased charge with a hazard band round its top and a diode that blinks. */
  private def drawMineTrap(sx: Float, sy: Float, now: Long, id: Int): Unit = {
    val r = _r; val g = _g; val b = _b
    val top = sy - 2.2f
    // The casing's outline, then its side in shade and its top in the light
    shapeBatch.fillOval(sx, sy + 1.4f, 12.8f, 6.6f, _ir, _ig, _ib, 0.95f, 22)
    shapeBatch.fillRect(sx - 12.8f, top, 25.6f, sy + 1.4f - top, _ir, _ig, _ib, 0.95f)
    shapeBatch.fillOval(sx, top, 12.8f, 6.6f, _ir, _ig, _ib, 0.95f, 22)
    shapeBatch.fillOval(sx, sy + 1.4f, 11.5f, 5.5f, r * 0.42f, g * 0.4f, b * 0.4f, 1f, 22)
    shapeBatch.fillRect(sx - 11.5f, top, 23f, sy + 1.4f - top, r * 0.42f, g * 0.4f, b * 0.4f, 1f)
    shapeBatch.fillOval(sx, top, 11.5f, 5.5f, r * 0.85f, g * 0.85f, b * 0.85f, 1f, 22)
    // The hazard band: yellow and black, the colours every ground was chosen not to be
    shapeBatch.fillArcBand(sx, top, 6.4f, 3.1f, 9.8f, 4.75f, 0f, PI * 2f, 24, 0.1f, 0.08f, 0.07f, 1f, 1f)
    var k = 0
    while (k < HAZARD_STRIPES) {
      shapeBatch.fillArcBand(sx, top, 6.4f, 3.1f, 9.8f, 4.75f, (k + 0.25f) * PI * 2f / HAZARD_STRIPES,
        PI / HAZARD_STRIPES, 3, 1f, 0.8f, 0.14f, 1f, 1f)
      k += 1
    }
    shapeBatch.strokeArc(sx, top, 11f, 5.1f, PI * 1.1f, PI * 0.6f, 0.9f, lit(r), lit(g), lit(b), 0.7f, 10)
    // The cap in the middle, and the diode on it: lit for a fifth of every beat, which is what
    // reads as blinking rather than pulsing. The blink is a mine's glint
    shapeBatch.fillOval(sx, top - 0.5f, 5.2f, 2.8f, _ir, _ig, _ib, 1f, 14)
    shapeBatch.fillOval(sx, top - 0.5f, 4.3f, 2.1f, 0.5f, 0.5f, 0.54f, 1f, 14)
    shapeBatch.fillOval(sx - 1.2f, top - 1.1f, 1.6f, 0.7f, 0.85f, 0.85f, 0.9f, 0.8f, 8)
    val on = (now + offset(id, BLINK_PERIOD_MS)) % BLINK_PERIOD_MS < BLINK_ON_MS
    val dy = top - 1.2f
    shapeBatch.fillOval(sx, dy, 2.0f, 1.4f, _ir, _ig, _ib, 1f, 10)
    if (on) {
      shapeBatch.fillOval(sx, dy, 1.5f, 1.0f, 1f, 0.42f, 0.3f, 1f, 10)
      shapeBatch.fillOval(sx - 0.4f, dy - 0.3f, 0.6f, 0.4f, 1f, 0.9f, 0.85f, 1f, 6)
      glow(sx, dy, 9f, 5.5f, 1f, 0.25f, 0.15f, 0.6f)
      if (projectileLights) lightSystem.addLight(sx, dy, 60f, 1f, 0.3f, 0.2f, 0.35f * _alpha)
    } else {
      shapeBatch.fillOval(sx, dy, 1.5f, 1.0f, 0.45f, 0.08f, 0.06f, 1f, 10)
    }
  }

  /** A spore pod: a swollen sac breathing on a slick of its own slime, puffing spores from the split
    * at its top. */
  private def drawPodTrap(sx: Float, sy: Float, now: Long, id: Int): Unit = {
    val r = _r; val g = _g; val b = _b
    // The slick it sits in
    shapeBatch.fillOval(sx, sy + 1.2f, 16f, 7.8f, _ir, _ig, _ib, 0.55f, 22)
    shapeBatch.strokeOval(sx, sy + 1.2f, 15.4f, 7.4f, 1.3f, r * 0.75f, g * 0.75f, b * 0.6f, 0.7f, 22)
    // It breathes: swelling and settling about its foot
    val breath = Math.sin(2 * Math.PI * cycle(now, id, BREATH_MS)).toFloat
    shapeBatch.setScaleAbout(sx, sy + 2f, 1f + 0.05f * breath)
    // Three lobes, inked round, in shade, and the light on the big one
    shapeBatch.fillOval(sx - 6.2f, sy + 0.6f, 7.4f, 5.2f, _ir, _ig, _ib, 1f, 16)
    shapeBatch.fillOval(sx + 6.4f, sy + 0.8f, 7.2f, 5.0f, _ir, _ig, _ib, 1f, 16)
    shapeBatch.fillOval(sx, sy - 2.2f, 11.6f, 8.0f, _ir, _ig, _ib, 1f, 20)
    shapeBatch.fillOval(sx - 6.2f, sy + 0.6f, 6.2f, 4.1f, r * 0.62f, g * 0.62f, b * 0.5f, 1f, 16)
    shapeBatch.fillOval(sx + 6.4f, sy + 0.8f, 6.0f, 3.9f, r * 0.58f, g * 0.58f, b * 0.48f, 1f, 16)
    shapeBatch.fillOval(sx, sy - 2.2f, 10.4f, 6.9f, r * 0.72f, g * 0.72f, b * 0.6f, 1f, 20)
    shapeBatch.fillOval(sx - 1.6f, sy - 3.6f, 7.8f, 4.9f, r, g, b, 1f, 18)
    // Dark spots
    shapeBatch.fillOval(sx - 5.6f, sy - 1.0f, 1.3f, 0.9f, _ir, _ig, _ib, 0.85f, 8)
    shapeBatch.fillOval(sx + 4.8f, sy - 0.2f, 1.1f, 0.8f, _ir, _ig, _ib, 0.85f, 8)
    shapeBatch.fillOval(sx + 1.8f, sy + 2.4f, 1.0f, 0.7f, _ir, _ig, _ib, 0.85f, 8)
    // The split at the top, glowing
    shapeBatch.fillOval(sx + 0.6f, sy - 7.0f, 4.2f, 2.1f, _ir, _ig, _ib, 1f, 12)
    shapeBatch.fillOval(sx + 0.6f, sy - 7.0f, 3.1f, 1.3f, 0.86f, 1f, 0.38f, 1f, 12)
    shapeBatch.setScaleAbout(sx, sy, 1f)
    glow(sx + 0.6f, sy - 7.0f, 8f, 5f, 0.6f, 1f, 0.3f, 0.35f)
    if (projectileLights) lightSystem.addLight(sx, sy - 4f, 50f, r, g, b, 0.22f * _alpha)
    // Spores drifting up off it, inked so they show against the snow
    var k = 0
    while (k < 3) {
      val q = cycle(now, id + k * 31, SPORE_MS).toFloat
      val mx = sx + 0.6f + (k - 1) * 2.4f + 2.2f * Math.sin(q * 7f + k * 2.1f).toFloat
      val my = sy - 8f - 13f * q
      val ma = 1f - q
      shapeBatch.fillOval(mx, my, 1.7f, 1.5f, _ir, _ig, _ib, 0.6f * ma, 8)
      shapeBatch.fillOval(mx, my, 1.05f, 0.95f, 0.85f, 1f, 0.45f, ma, 8)
      k += 1
    }
    if (_glintA > 0f) glint(sx - 4f, sy - 5.2f, 0.92f, 1f, 0.85f)
  }

  /**
   * A rune: a magic circle burnt into the ground, its marks turning, a glyph at its heart. A fire
   * rune smoulders, flames licking up round its rim; any other rune (the Runesmith's Binding Rune)
   * burns in its own colour, a lattice glyph at its heart and motes rising off it, and turns the
   * other way.
   */
  private def drawRuneTrap(sx: Float, sy: Float, now: Long, id: Int, fire: Boolean): Unit = {
    val r = _r; val g = _g; val b = _b
    val flicker = 0.78f + 0.22f * Math.sin(2 * Math.PI * cycle(now, id, FLICKER_MS)).toFloat
    // The light colour of its lines: white-hot for fire, a pale tint of its own colour otherwise
    val hr = if (fire) 1f else lit(r); val hg = if (fire) 0.9f else lit(g); val hb = if (fire) 0.55f else lit(b)
    // Burnt ground under it
    shapeBatch.fillOvalSoft(sx, sy + 0.5f, 18.5f, 9.2f, _ir * 0.4f, _ig * 0.4f, _ib * 0.4f, 0.7f, 0.15f, 18)
    // The outer ring, inked
    shapeBatch.strokeOval(sx, sy, RUNE_OUT_X, RUNE_OUT_X * 0.5f, 3.6f, _ir, _ig, _ib, 0.9f, 32)
    shapeBatch.strokeOval(sx, sy, RUNE_OUT_X, RUNE_OUT_X * 0.5f, 2.0f, r, g, b, flicker, 32)
    // The marks between the rings, turning: a fire rune's one way, any other the other
    val turn = (if (fire) 1f else -1f) * PI * 2f * ((now % RUNE_TURN_MS).toFloat / RUNE_TURN_MS)
    var k = 0
    while (k < RUNE_MARKS) {
      val a0 = turn + k * PI * 2f / RUNE_MARKS
      shapeBatch.fillArcBand(sx, sy, 8.4f, 4.2f, 11.6f, 5.8f, a0 - 0.05f, 0.52f, 4, _ir, _ig, _ib, 0.9f, 0.9f)
      shapeBatch.fillArcBand(sx, sy, 9.1f, 4.55f, 10.9f, 5.45f, a0, 0.42f, 4, hr, hg, hb, 0.95f * flicker, 0.95f * flicker)
      k += 1
    }
    // The inner ring
    shapeBatch.strokeOval(sx, sy, 7.4f, 3.7f, 2.8f, _ir, _ig, _ib, 0.9f, 22)
    shapeBatch.strokeOval(sx, sy, 7.4f, 3.7f, 1.3f, r, g, b, flicker, 22)
    // The glyph at its heart, bright enough that the bloom picks it up on a dark map
    if (fire) {
      glyphStroke(sx - 4.4f, sy - 1.7f, sx + 4.4f, sy + 1.7f, hr, hg, hb, flicker)
      glyphStroke(sx + 4.4f, sy - 1.7f, sx - 4.4f, sy + 1.7f, hr, hg, hb, flicker)
      glyphStroke(sx, sy - 3.0f, sx, sy + 3.0f, hr, hg, hb, flicker)
    } else {
      _xs(0) = sx; _ys(0) = sy - 2.9f
      _xs(1) = sx + 5.2f; _ys(1) = sy
      _xs(2) = sx; _ys(2) = sy + 2.9f
      _xs(3) = sx - 5.2f; _ys(3) = sy
      shapeBatch.strokePolygon(_xs, _ys, 4, 2.8f, _ir, _ig, _ib, 0.9f)
      shapeBatch.strokePolygon(_xs, _ys, 4, 1.3f, hr, hg, hb, flicker)
      shapeBatch.fillOval(sx, sy, 1.6f, 1.0f, _ir, _ig, _ib, 0.9f, 8)
      shapeBatch.fillOval(sx, sy, 1.0f, 0.6f, hr, hg, hb, flicker, 8)
    }
    if (fire) {
      // Flames licking up round the rim, each to its own height and leaning its own way: fire's
      // colours, deep to white-hot, and no ink, or they read as a crown of spikes
      k = 0
      while (k < RUNE_FLAMES) {
        val ang = turn * 0.5f + k * PI * 2f / RUNE_FLAMES + 0.3f
        val bx = sx + RUNE_OUT_X * Math.cos(ang).toFloat
        val by = sy + RUNE_OUT_X * 0.5f * Math.sin(ang).toFloat
        val ph = 2 * Math.PI * cycle(now + k * 977L, id, FLAME_MS)
        val h = 3.4f + 2.6f * (0.5f + 0.5f * Math.sin(ph * 9.0 + k * 1.9).toFloat)
        val lean = 1.1f * Math.sin(ph * 5.0 + k * 2.6).toFloat
        lick(bx, by, 2.3f, h, lean, 0.88f, 0.26f, 0.05f, 0.95f)
        lick(bx, by + 0.1f, 1.6f, h * 0.72f, lean * 0.7f, 1f, 0.6f, 0.12f, 1f)
        lick(bx, by + 0.2f, 0.9f, h * 0.42f, lean * 0.4f, 1f, 0.93f, 0.6f, 1f)
        k += 1
      }
      // An ember lifting off it
      val q = cycle(now, id, EMBER_MS).toFloat
      shapeBatch.fillOval(sx + 3.5f, sy - 4f - 9f * q, 1.0f, 1.0f, 1f, 0.75f, 0.35f, 0.8f * (1f - q), 6)
    } else {
      // Motes rising off it, in its colour, inked
      k = 0
      while (k < 3) {
        val q = cycle(now, id + k * 53, MOTE_MS).toFloat
        val ang = k * PI * 2f / 3f + 0.6f
        val mx = sx + 8f * Math.cos(ang).toFloat
        val my = sy + 4f * Math.sin(ang).toFloat - 12f * q
        val s = 1.5f * (1f - q * 0.5f)
        diamond(mx, my, s + 0.7f, _ir, _ig, _ib, 0.7f * (1f - q))
        diamond(mx, my, s, hr, hg, hb, 1f - q)
        k += 1
      }
    }
    glow(sx, sy, 16f, 8f, r, g, b, 0.3f * flicker)
    if (projectileLights) lightSystem.addLight(sx, sy, 56f, r, g, b, 0.3f * flicker * _alpha)
    if (_glintA > 0f) glint(sx, sy - 0.5f, hr, hg, hb)
  }

  private def glyphStroke(x0: Float, y0: Float, x1: Float, y1: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    shapeBatch.strokeLine(x0, y0, x1, y1, 2.9f, _ir, _ig, _ib, 0.9f)
    shapeBatch.strokeLine(x0, y0, x1, y1, 1.35f, r, g, b, a)
  }

  /** A lick of flame standing up from (bx, by), `half` either side of it at its round foot, `h` tall,
    * its tip leaning `lean` across. Convex while the lean stays within its width. */
  private def lick(bx: Float, by: Float, half: Float, h: Float, lean: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    _xs(0) = bx - half; _ys(0) = by - 0.2f * h
    _xs(1) = bx - 0.55f * half; _ys(1) = by + 0.45f * half
    _xs(2) = bx + 0.55f * half; _ys(2) = by + 0.45f * half
    _xs(3) = bx + half; _ys(3) = by - 0.2f * h
    _xs(4) = bx + Math.max(-half, Math.min(half, lean)); _ys(4) = by - h
    shapeBatch.fillPolygon(_xs, _ys, 5, r, g, b, a)
  }

  private def diamond(x: Float, y: Float, s: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    _xs(0) = x; _ys(0) = y - s * 1.4f
    _xs(1) = x + s; _ys(1) = y
    _xs(2) = x; _ys(2) = y + s * 1.4f
    _xs(3) = x - s; _ys(3) = y
    shapeBatch.fillPolygon(_xs, _ys, 4, r, g, b, a)
  }

  /** A snare: a web spun flat across the cell, silk over its own shadow, dew on it catching the
    * light. */
  private def drawWebTrap(sx: Float, sy: Float, now: Long, id: Int): Unit = {
    // Silk: its own colour lit, nearly white; its shadow a dark blue-grey
    val r = lit(_r); val g = lit(_g); val b = lit(_b)
    val dr = 0.07f; val dg = 0.07f; val db = 0.11f
    var k = 0
    while (k < SPOKES) {
      val ex = sx + WEB_RX * SPOKE_COS(k); val ey = sy + WEB_RY * SPOKE_SIN(k)
      shapeBatch.strokeLine(sx, sy, ex, ey, 2.3f, dr, dg, db, 0.62f)
      k += 1
    }
    var j = 0
    while (j < SPIRAL_POINTS) {
      _xs(j) = sx + WEB_RX * SPIRAL_U(j); _ys(j) = sy + WEB_RY * SPIRAL_V(j)
      j += 1
    }
    shapeBatch.strokePolyline(_xs, _ys, SPIRAL_POINTS, 2.1f, dr, dg, db, 0.55f)
    k = 0
    while (k < SPOKES) {
      val ex = sx + WEB_RX * SPOKE_COS(k); val ey = sy + WEB_RY * SPOKE_SIN(k)
      shapeBatch.strokeLine(sx, sy, ex, ey, 1.0f, r, g, b, 0.95f)
      // Where it is tied down
      shapeBatch.fillOval(ex, ey, 1.5f, 1.1f, dr, dg, db, 0.7f, 8)
      shapeBatch.fillOval(ex, ey, 0.9f, 0.65f, r, g, b, 1f, 8)
      k += 1
    }
    shapeBatch.strokePolyline(_xs, _ys, SPIRAL_POINTS, 0.9f, r, g, b, 0.92f)
    // The hub, wound tight
    shapeBatch.fillOval(sx, sy, 3.0f, 1.9f, dr, dg, db, 0.8f, 12)
    shapeBatch.fillOval(sx, sy, 2.2f, 1.3f, r, g, b, 1f, 12)
    // Dew on the silk, each drop dark-rimmed, with the light in it
    k = 0
    while (k < DEW.length) {
      val dx = sx + WEB_RX * SPIRAL_U(DEW(k)); val dy = sy + WEB_RY * SPIRAL_V(DEW(k))
      shapeBatch.fillOval(dx, dy, 1.75f, 1.35f, dr, dg, db, 0.85f, 10)
      shapeBatch.fillOval(dx, dy, 1.2f, 0.9f, 0.72f, 0.88f, 1f, 1f, 10)
      shapeBatch.fillOval(dx - 0.4f, dy - 0.3f, 0.45f, 0.35f, 1f, 1f, 1f, 1f, 6)
      k += 1
    }
    // The glint moves from drop to drop
    if (_glintA > 0f) {
      val which = (((now + offset(id, GLINT_PERIOD_MS)) / GLINT_PERIOD_MS) % DEW.length).toInt
      glint(sx + WEB_RX * SPIRAL_U(DEW(which)), sy + WEB_RY * SPIRAL_V(DEW(which)), 0.85f, 0.95f, 1f)
    }
  }

  // ── The additive pass ──

  /** A soft glow in the additive pass after every trap, at `a` of the trap's own opacity. */
  private def glow(x: Float, y: Float, rx: Float, ry: Float, r: Float, g: Float, b: Float, a: Float): Unit =
    defer(star = false, x, y, rx, ry, r, g, b, a * _alpha)

  /** The trap's glint, a star of light, where it catches. */
  private def glint(x: Float, y: Float, r: Float, g: Float, b: Float): Unit =
    defer(star = true, x, y, GLINT_LEN, 0f, r, g, b, _glintA)

  private def defer(star: Boolean, x: Float, y: Float, rx: Float, ry: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    if (_glows >= MAX_GLOWS || a <= 0.01f) return
    val i = _glows
    _gStar(i) = star; _gX(i) = x; _gY(i) = y; _gRx(i) = rx; _gRy(i) = ry
    _gR(i) = r; _gG(i) = g; _gB(i) = b; _gA(i) = a
    _glows += 1
  }

  private def drawGlows(): Unit = {
    shapeBatch.setAdditiveBlend(true)
    var i = 0
    while (i < _glows) {
      if (_gStar(i)) {
        val len = _gRx(i) * (0.55f + 0.45f * _gA(i))
        shapeBatch.fillStarFlare(_gX(i), _gY(i), len, 1.5f, 0.35f, 0.55f, _gR(i), _gG(i), _gB(i), _gA(i))
      } else {
        shapeBatch.fillOvalSoft(_gX(i), _gY(i), _gRx(i), _gRy(i), _gR(i), _gG(i), _gB(i), _gA(i), 0f, 16)
      }
      i += 1
    }
    shapeBatch.setAdditiveBlend(false)
  }

  /**
   * A trap going off, over the fraction `t` of its short life. A mine is never here: its blast is
   * the explosion a grenade's is, keyed into the same animations past every projectile id.
   */
  private def drawTrapEffect(trapType: Byte, sx: Float, sy: Float, t: Float): Unit = {
    val tDef = TrapDef.get(trapType)
    if (tDef == null) return
    intToRGB(tDef.colorRGB)
    val r = _rgb_r; val g = _rgb_g; val b = _rgb_b
    val ir = r * INK + 0.02f; val ig = g * INK + 0.02f; val ib = b * INK + 0.03f
    val fade = 1f - t

    // The flash of it springing, and the ring that runs out from it over the ground it watched
    if (t < 0.3f) {
      val f = 1f - t / 0.3f
      shapeBatch.setAdditiveBlend(true)
      shapeBatch.fillOvalSoft(sx, sy, 20f * (0.4f + 0.6f * (1f - f)), 10f * (0.4f + 0.6f * (1f - f)),
        1f, 1f, 0.92f, 0.6f * f, 0f, 16)
      shapeBatch.setAdditiveBlend(false)
    }
    val ring = 10f + (tDef.triggerRadius * HW * SQRT2 + 4f - 10f) * (1f - (1f - t) * (1f - t))
    shapeBatch.strokeOval(sx, sy, ring, ring * 0.5f, 3.4f, ir, ig, ib, 0.35f * fade, 40)
    shapeBatch.strokeOval(sx, sy, ring, ring * 0.5f, 1.6f, lit(r), lit(g), lit(b), 0.8f * fade, 40)

    tDef.kind match {
      case TrapKind.JAWS =>
        // The jaws snapping shut: the two halves fold in over the plate, faster as they go, until
        // they meet tooth to tooth in a bar across it; and chips of steel thrown off it
        val close = Math.min(1f, t * 4f)
        val e = close * close
        val oy = JAW_OUT_Y + (1.8f - JAW_OUT_Y) * e
        val iy = JAW_IN_Y + (0.3f - JAW_IN_Y) * e
        shapeBatch.fillOval(sx, sy, JAW_OUT_X + 1.2f, oy + 0.8f, ir, ig, ib, 0.95f * fade, 24)
        shapeBatch.fillArcBand(sx, sy, JAW_IN_X, iy, JAW_OUT_X, oy, PI, PI, 14, r, g, b, fade, fade)
        shapeBatch.fillArcBand(sx, sy, JAW_IN_X, iy, JAW_OUT_X, oy, 0f, PI, 14,
          r * 0.7f, g * 0.7f, b * 0.72f, fade, fade)
        var i = 0
        while (i < TEETH) {
          tooth(sx, sy, i, wide = false, JAW_IN_X, iy, 5.6f, iy * 0.65f, 0.96f, 0.96f, 0.98f, fade)
          i += 1
        }
        i = 0
        while (i < 6) {
          val ang = (i * 1.07f).toFloat
          val d = 7f + 22f * t
          val cx = sx + Math.cos(ang).toFloat * d
          val cy = sy + Math.sin(ang).toFloat * d * 0.5f - 7f * t
          shapeBatch.fillOval(cx, cy, 1.9f, 1.5f, ir, ig, ib, 0.7f * fade, 6)
          shapeBatch.fillOval(cx, cy, 1.3f, 1.0f, 0.92f, 0.92f, 0.94f, fade, 6)
          i += 1
        }

      case TrapKind.POD =>
        // A puff of spores, rising and spreading
        var i = 0
        while (i < 5) {
          val ang = (i * 1.26f + 0.4f).toFloat
          val d = 3f + 14f * t
          shapeBatch.fillOvalSoft(sx + Math.cos(ang).toFloat * d, sy + Math.sin(ang).toFloat * d * 0.5f - 8f * t,
            6f + 8f * t, 4f + 5.5f * t, r, g, b, 0.45f * fade, 0f, 12)
          i += 1
        }

      case TrapKind.RUNE =>
        // The rune going up: licks of flame standing up out of its ring and spreading, the far
        // ones first so the near ones stand in front of them. A fire rune's are fire, and any other
        // rune's (the Runesmith's Binding Rune) burn in the rune's own colour
        val fire = trapType == TrapType.FIRE_RUNE
        val dr = if (fire) 0.9f else r * 0.75f; val dg = if (fire) 0.28f else g * 0.6f; val db = if (fire) 0.05f else b * 0.9f
        val mr = if (fire) 1f else lit(r); val mg = if (fire) 0.62f else lit(g); val mb = if (fire) 0.14f else lit(b)
        val cr = if (fire) 1f else 0.97f; val cg = if (fire) 0.94f else 0.93f; val cb = if (fire) 0.62f else 1f
        if (fire) shapeBatch.fillOvalSoft(sx, sy - 4f, 15f, 10f, 1f, 0.45f, 0.15f, 0.35f * fade, 0f, 14)
        else shapeBatch.fillOvalSoft(sx, sy - 4f, 15f, 10f, r, g, b, 0.35f * fade, 0f, 14)
        var j = 0
        while (j < BURST_FLAMES) {
          val i = BURST_ORDER(j)
          val rad = 5f + 8f * t
          val bx = sx + BURST_COS(i) * rad
          val by = sy + BURST_SIN(i) * rad * 0.5f
          val h = (18f * (1f - t) + 5f) * (0.82f + 0.18f * Math.sin(i * 2.3).toFloat)
          val lean = 1.6f * Math.sin(t * 9f + i * 1.7f).toFloat
          lick(bx, by, 3.3f, h, lean, dr, dg, db, 0.9f * fade)
          lick(bx, by + 0.15f, 2.3f, h * 0.72f, lean * 0.7f, mr, mg, mb, 0.95f * fade)
          lick(bx, by + 0.3f, 1.25f, h * 0.42f, lean * 0.4f, cr, cg, cb, 0.95f * fade)
          j += 1
        }

      case _ =>
        // A web pulling taut: the strands whip back in
        var i = 0
        while (i < SPOKES) {
          val d = 1f - t
          val ex = sx + WEB_RX * SPOKE_COS(i) * d; val ey = sy + WEB_RY * SPOKE_SIN(i) * d
          shapeBatch.strokeLine(sx, sy, ex, ey, 2.3f, 0.07f, 0.07f, 0.11f, 0.5f * fade)
          shapeBatch.strokeLine(sx, sy, ex, ey, 1.1f, lit(r), lit(g), lit(b), 0.9f * fade)
          i += 1
        }
    }
  }
}

private[render] object TrapPainter {
  private val PI = Math.PI.toFloat
  private val SQRT2 = Math.sqrt(2.0).toFloat

  // How plainly an enemy's trap is drawn (ours and our allies' are drawn in full), and its glint
  private val ENEMY_TRAP_ALPHA = 0.5f
  private val ENEMY_GLINT_ALPHA = 0.85f
  // While it arms it is dimmer, a ring closing on it, this wide at the start and the end
  private val ARMING_ALPHA = 0.55f
  private val ARM_RING_FROM = 30f
  private val ARM_RING_TO = 17f
  // How long before it runs out it starts to fade, and how long its going off is shown for
  private val TRAP_FADE_MS = 800f
  private val TRAP_EFFECT_MS = 620f
  // How dark its ink is, of its own colour
  private val INK = 0.16f

  // Ours: a ring sent out over the ground it watches, this long, this often, from about its own edge
  private val PING_PERIOD_MS = 3000L
  private val PING_MS = 900f
  private val PING_FROM = 17f
  // The glint that catches on it: this long, this often, this long a star
  private val GLINT_PERIOD_MS = 3400L
  private val GLINT_MS = 340L
  private val GLINT_LEN = 9f

  // A bear trap's jaws: the inner and outer ellipses of their ring, and how many teeth
  private val JAW_IN_X = 8.6f; private val JAW_IN_Y = 4.3f
  private val JAW_OUT_X = 12.4f; private val JAW_OUT_Y = 6.2f
  private val TEETH = 12
  // Each tooth's heading round the jaws, six along each, and where its base meets the jaw either
  // side of it: (heading - wide, heading + wide, heading - narrow, heading + narrow), wide for its ink
  private val TOOTH_HEADING: Array[Double] = Array.tabulate(TEETH) { i =>
    (if (i < TEETH / 2) Math.PI else 0.0) + Math.PI * ((i % (TEETH / 2)) + 0.5) / (TEETH / 2)
  }
  private val TOOTH_COS: Array[Float] = TOOTH_HEADING.map(a => Math.cos(a).toFloat)
  private val TOOTH_SIN: Array[Float] = TOOTH_HEADING.map(a => Math.sin(a).toFloat)
  private val TOOTH_EDGES: Array[Double] = TOOTH_HEADING.flatMap(a => Array(a - 0.24, a + 0.24, a - 0.16, a + 0.16))
  private val TOOTH_EDGE_COS: Array[Float] = TOOTH_EDGES.map(a => Math.cos(a).toFloat)
  private val TOOTH_EDGE_SIN: Array[Float] = TOOTH_EDGES.map(a => Math.sin(a).toFloat)
  // A mine: its blink, and the stripes of its hazard band
  private val BLINK_PERIOD_MS = 900L
  private val BLINK_ON_MS = 170L
  private val HAZARD_STRIPES = 6
  // A pod's breath and its spores' rise
  private val BREATH_MS = 1700L
  private val SPORE_MS = 1600L
  // A rune: its outer ring, how long its marks take to turn once, how many, its flames, and how
  // fast it flickers and they dance (a flame's height runs nine times round its cycle, its lean five)
  private val RUNE_OUT_X = 13.6f
  private val RUNE_TURN_MS = 12000L
  private val RUNE_MARKS = 6
  private val RUNE_FLAMES = 6
  private val FLICKER_MS = 571L
  private val FLAME_MS = 6283L
  private val EMBER_MS = 1300L
  private val MOTE_MS = 1900L
  // A rune going up: its flames round the ring, and the order to draw them in, far side first
  private val BURST_FLAMES = 7
  private val BURST_COS: Array[Float] = Array.tabulate(BURST_FLAMES)(i => Math.cos(0.2 + i * Math.PI * 2 / BURST_FLAMES).toFloat)
  private val BURST_SIN: Array[Float] = Array.tabulate(BURST_FLAMES)(i => Math.sin(0.2 + i * Math.PI * 2 / BURST_FLAMES).toFloat)
  private val BURST_ORDER: Array[Int] = (0 until BURST_FLAMES).sortBy(i => BURST_SIN(i)).toArray

  // A web: its reach, its eight spokes (turned a little, so none lies flat), and its spiral, three
  // and a quarter turns out from the hub with a point on each spoke, as fractions of its reach
  private val WEB_RX = 16f
  private val WEB_RY = 8f
  private val SPOKES = 8
  private val SPOKE_TURN = 0.2f
  private val SPOKE_COS: Array[Float] = Array.tabulate(SPOKES)(k => Math.cos(SPOKE_TURN + k * Math.PI * 2 / SPOKES).toFloat)
  private val SPOKE_SIN: Array[Float] = Array.tabulate(SPOKES)(k => Math.sin(SPOKE_TURN + k * Math.PI * 2 / SPOKES).toFloat)
  val SPIRAL_POINTS: Int = 27
  private val SPIRAL_U: Array[Float] = Array.tabulate(SPIRAL_POINTS) { j =>
    val f = 0.2f + 0.68f * j / (SPIRAL_POINTS - 1)
    (f * Math.cos(SPOKE_TURN + j * Math.PI * 2 / SPOKES)).toFloat
  }
  private val SPIRAL_V: Array[Float] = Array.tabulate(SPIRAL_POINTS) { j =>
    val f = 0.2f + 0.68f * j / (SPIRAL_POINTS - 1)
    (f * Math.sin(SPOKE_TURN + j * Math.PI * 2 / SPOKES)).toFloat
  }
  // The spiral's points that hold a drop of dew
  private val DEW: Array[Int] = Array(7, 14, 20)

  // Room in the additive pass: a glow and a glint for every trap a full match can have on screen
  private val MAX_GLOWS = 128

  /** The light colour of a part: its own colour most of the way to white. */
  @inline private def lit(c: Float): Float = c + (1f - c) * 0.55f

  /** Where trap `id` is in a cycle `period` long, so the traps on the ground don't all keep time. */
  @inline private def offset(id: Int, period: Long): Long = ((id * 0x9E3779B1) >>> 1) % period

  /** How far through a cycle `period` long trap `id` is at `now`, 0 to 1. */
  @inline private def cycle(now: Long, id: Int, period: Long): Double =
    ((now + offset(id, period)) % period).toDouble / period
}
