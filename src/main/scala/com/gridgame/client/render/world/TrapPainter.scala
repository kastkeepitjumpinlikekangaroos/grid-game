package com.gridgame.client.render
package world

import com.gridgame.client.render.EntityCollector
import com.gridgame.common.model._

/**
 * Traps on the ground and going off, drawn flat in the ground pass so the walls in front of them cover
 * them. Ours and our allies' plainly, everyone else's faintly.
 */
private[render] final class TrapPainter(ctx: RenderContext) {
  import ctx._

  // Traps: how plainly an enemy's is drawn (ours and our allies' are drawn in full), how long
  // before it runs out it starts to fade, and how long its going off is shown for
  private val ENEMY_TRAP_ALPHA = 0.30f
  private val TRAP_FADE_MS = 800f
  private val TRAP_EFFECT_MS = 620f

  // Scratch for one trap's decal: a tooth, a chip, a flame tongue
  private val _trapXs = new Array[Float](8)
  private val _trapYs = new Array[Float](8)

  @inline private def onScreen(sx: Float, sy: Float): Boolean =
    sx > -60f && sx < canvasW + 60f && sy > -60f && sy < canvasH + 60f

  /**
   * Traps on the ground and traps going off, drawn flat in the ground pass: a wall in front of
   * one covers it when the depth pass draws that wall, with nothing asked of EntityCollector.
   *
   * Ours and our allies' are drawn plainly, everyone else's at [[ENEMY_TRAP_ALPHA]] — there to
   * be found if you look for it, not invisible and not obvious.
   */
  def draw(): Unit = {
    val traps = client.getTraps
    val effects = client.getTrapEffects
    if (traps.isEmpty && effects.isEmpty) return
    val now = frameTimeMs
    beginShapes()

    if (!traps.isEmpty) {
      val iter = traps.values().iterator()
      while (iter.hasNext) {
        val trap = iter.next()
        val sx = worldToScreenX(trap.x, trap.y).toFloat
        val sy = worldToScreenY(trap.x, trap.y).toFloat
        if (onScreen(sx, sy)) drawTrap(trap, sx, sy, now)
      }
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
    if (arming) a *= 0.55f
    val left = trap.expiresAt - now
    if (left < TRAP_FADE_MS) a *= Math.max(0f, left / TRAP_FADE_MS)
    if (a <= 0.01f) return

    intToRGB(tDef.colorRGB)
    val r = _rgb_r; val g = _rgb_g; val b = _rgb_b

    // Sitting in the cell rather than floating over it
    shapeBatch.fillOvalSoft(sx, sy + 1.5f, 12f, 5.5f, 0f, 0f, 0f, 0.3f * a, 0f, 12)

    tDef.kind match {
      case TrapKind.JAWS => drawJawTrap(sx, sy, r, g, b, a)
      case TrapKind.MINE => drawMineTrap(sx, sy, r, g, b, a, now)
      case TrapKind.POD  => drawPodTrap(sx, sy, r, g, b, a)
      case TrapKind.RUNE => drawRuneTrap(sx, sy, r, g, b, a, trap.id)
      case _             => drawWebTrap(sx, sy, r, g, b, a)
    }

    // Arming: a ring closing on it. Nothing is going to happen until it has finished.
    if (arming) {
      val armT = 1f - (trap.armedAt - now).toFloat / Math.max(1, tDef.armDelayMs)
      val rad = 21f - 10f * armT
      shapeBatch.strokeOval(sx, sy, rad, rad * 0.5f, 1.2f, r, g, b,
        (if (friendly) 0.6f else 0.3f) * (0.3f + 0.7f * armT), 16)
    }
  }

  /** A bear trap: a plate between two springs, jaws standing open around it. */
  private def drawJawTrap(sx: Float, sy: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    // Base plate and the springs at either end
    shapeBatch.fillOval(sx, sy, 9.5f, 4.6f, r * 0.34f, g * 0.34f, b * 0.38f, 0.9f * a, 14)
    shapeBatch.fillOval(sx - 10.5f, sy, 3.2f, 2.1f, r * 0.55f, g * 0.55f, b * 0.55f, 0.9f * a, 8)
    shapeBatch.fillOval(sx + 10.5f, sy, 3.2f, 2.1f, r * 0.55f, g * 0.55f, b * 0.55f, 0.9f * a, 8)
    // The two jaws, open: a band of steel above the plate and one below
    shapeBatch.fillArcBand(sx, sy, 7.6f, 3.6f, 10.4f, 5.0f, Math.PI.toFloat, Math.PI.toFloat, 9,
      r, g, b, 0.95f * a, 0.95f * a)
    shapeBatch.fillArcBand(sx, sy, 7.6f, 3.6f, 10.4f, 5.0f, 0f, Math.PI.toFloat, 9,
      r * 0.82f, g * 0.82f, b * 0.82f, 0.95f * a, 0.95f * a)
    // Teeth, pointing in at whatever stands on the plate. Kept short: a tooth authored much
    // longer than this reads as a row of shark's teeth at the size a trap is actually seen.
    var i = 0
    while (i < 8) {
      val ang = (Math.PI * (0.18 + 0.21 * (i % 4)) + (if (i < 4) Math.PI else 0.0)).toFloat
      val c = Math.cos(ang).toFloat
      val sn = Math.sin(ang).toFloat
      _trapXs(0) = sx + 7.6f * c; _trapYs(0) = sy + 3.6f * sn
      _trapXs(1) = sx + 4.4f * c - 1.4f * sn; _trapYs(1) = sy + 2.1f * sn + 0.7f * c
      _trapXs(2) = sx + 4.4f * c + 1.4f * sn; _trapYs(2) = sy + 2.1f * sn - 0.7f * c
      shapeBatch.fillPolygon(_trapXs, _trapYs, 3, 0.92f, 0.93f, 0.95f, 0.85f * a)
      i += 1
    }
    // The pressure plate
    shapeBatch.fillOval(sx, sy, 3.6f, 1.8f, r * 0.75f, g * 0.7f, b * 0.6f, 0.95f * a, 10)
  }

  /** A mine: a cased charge sunk into the ground with a diode that blinks. */
  private def drawMineTrap(sx: Float, sy: Float, r: Float, g: Float, b: Float, a: Float, now: Long): Unit = {
    shapeBatch.fillOval(sx, sy, 8.4f, 4.4f, r * 0.3f, g * 0.3f, b * 0.32f, 0.9f * a, 14)
    shapeBatch.fillOval(sx, sy - 2.4f, 8.4f, 4.4f, r * 0.55f, g * 0.5f, b * 0.5f, 0.95f * a, 14)
    // A warning band round the casing
    shapeBatch.fillArcBand(sx, sy - 2.4f, 5.4f, 2.8f, 7.4f, 3.9f, 0f, (Math.PI * 2).toFloat, 14,
      r, g * 0.55f, b * 0.35f, 0.6f * a, 0.6f * a)
    shapeBatch.strokeOval(sx, sy - 2.4f, 8.4f, 4.4f, 1f, r * 0.8f, g * 0.75f, b * 0.7f, 0.6f * a, 14)
    // The diode: on for a fifth of every beat, which is what reads as blinking rather than pulsing
    val blink = if ((now % 900L) < 170L) 1f else 0.12f
    shapeBatch.fillOvalSoft(sx, sy - 3.4f, 5.5f, 3.4f, 1f, 0.25f, 0.15f, 0.4f * blink * a, 0f, 10)
    shapeBatch.fillOval(sx, sy - 3.4f, 1.6f, 1.2f, 1f, 0.35f, 0.25f, (0.35f + 0.65f * blink) * a, 8)
  }

  /** A spore pod: a swollen sac, split at the top, ready to burst. */
  private def drawPodTrap(sx: Float, sy: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    shapeBatch.fillOval(sx, sy - 0.5f, 8.2f, 5.0f, r * 0.45f, g * 0.5f, b * 0.35f, 0.92f * a, 14)
    shapeBatch.fillOval(sx - 3.8f, sy + 1.2f, 4.4f, 2.9f, r * 0.55f, g * 0.6f, b * 0.4f, 0.9f * a, 10)
    shapeBatch.fillOval(sx + 3.9f, sy + 1.3f, 4.2f, 2.8f, r * 0.5f, g * 0.55f, b * 0.38f, 0.9f * a, 10)
    // The lit cap, and the split across it
    shapeBatch.fillOval(sx, sy - 2.6f, 5.4f, 3.2f, r, g, b, 0.9f * a, 12)
    shapeBatch.strokeLine(sx - 3.4f, sy - 3.2f, sx + 3.2f, sy - 2.0f, 1.1f,
      r * 0.35f, g * 0.4f, b * 0.25f, 0.8f * a)
    // Pores, and the haze over it
    shapeBatch.fillOvalSoft(sx, sy - 3.4f, 7.5f, 4.5f, r, g, b, 0.18f * a, 0f, 12)
    shapeBatch.fillOval(sx - 2.2f, sy - 0.2f, 1.1f, 0.8f, r * 0.3f, g * 0.35f, b * 0.2f, 0.8f * a, 6)
    shapeBatch.fillOval(sx + 2.6f, sy + 0.4f, 1.0f, 0.7f, r * 0.3f, g * 0.35f, b * 0.2f, 0.8f * a, 6)
  }

  /** A fire rune: a glyph burnt into the ground, still smouldering. */
  private def drawRuneTrap(sx: Float, sy: Float, r: Float, g: Float, b: Float, a: Float, seed: Int): Unit = {
    val flicker = 0.68f + 0.32f * Math.sin(animTickF * 0.21f + seed * 1.7f).toFloat
    // Scorched ground under it
    shapeBatch.fillOvalSoft(sx, sy, 12f, 6f, 0.06f, 0.04f, 0.03f, 0.5f * a, 0f, 14)
    shapeBatch.strokeOval(sx, sy, 9.4f, 4.7f, 1.4f, r, g, b, 0.85f * a * flicker, 16)
    shapeBatch.strokeOval(sx, sy, 6.2f, 3.1f, 0.9f, r, g * 0.8f, b * 0.6f, 0.5f * a * flicker, 14)
    // The glyph: three strokes, bright enough that the bloom picks them up
    shapeBatch.strokeLine(sx - 4.2f, sy - 1.6f, sx + 4.2f, sy + 1.6f, 1.3f, 1f, g + 0.25f, b + 0.2f, 0.9f * a * flicker)
    shapeBatch.strokeLine(sx + 4.2f, sy - 1.6f, sx - 4.2f, sy + 1.6f, 1.3f, 1f, g + 0.25f, b + 0.2f, 0.9f * a * flicker)
    shapeBatch.strokeLine(sx, sy - 3.1f, sx, sy + 3.1f, 1.1f, 1f, g + 0.15f, b + 0.1f, 0.7f * a * flicker)
    // An ember lifting off it
    val emberY = sy - 3f - 3.5f * ((animTickF * 0.05f + seed * 0.3f) % 1f)
    shapeBatch.fillOval(sx + 2.5f, emberY, 0.9f, 0.9f, 1f, 0.7f, 0.35f, 0.55f * a * flicker, 6)
  }

  /** A snare: a web stretched flat across the cell. */
  private def drawWebTrap(sx: Float, sy: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    var i = 0
    while (i < 8) {
      val ang = (Math.PI * 2 * i / 8).toFloat
      val c = Math.cos(ang).toFloat
      val sn = Math.sin(ang).toFloat
      shapeBatch.strokeLine(sx, sy, sx + 11.5f * c, sy + 5.6f * sn, 0.8f, r, g, b, 0.7f * a)
      i += 1
    }
    shapeBatch.strokeOval(sx, sy, 5.6f, 2.8f, 0.8f, r, g, b, 0.6f * a, 12)
    shapeBatch.strokeOval(sx, sy, 10.5f, 5.2f, 0.8f, r, g, b, 0.45f * a, 14)
    shapeBatch.fillOval(sx, sy, 1.4f, 1.0f, r, g, b, 0.7f * a, 6)
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
    val fade = 1f - t

    // The flash of it springing, and the ring that runs out from it
    if (t < 0.3f) {
      val f = 1f - t / 0.3f
      shapeBatch.setAdditiveBlend(true)
      shapeBatch.fillOvalSoft(sx, sy, 16f * (0.4f + 0.6f * (1f - f)), 8f * (0.4f + 0.6f * (1f - f)),
        1f, 1f, 0.92f, 0.55f * f, 0f, 14)
      shapeBatch.setAdditiveBlend(false)
    }
    val ring = 8f + 16f * t
    shapeBatch.strokeOval(sx, sy, ring, ring * 0.5f, 1.4f, r, g, b, 0.5f * fade, 16)

    tDef.kind match {
      case TrapKind.JAWS =>
        // The jaws slamming shut, and chips of steel thrown off the plate
        val close = Math.min(1f, t * 3.2f)
        val open = (1f - close) * 0.85f
        shapeBatch.fillArcBand(sx, sy, 7.6f, 3.6f, 10.4f, 5.0f,
          (Math.PI - open).toFloat, (Math.PI + 2 * open).toFloat, 8, r, g, b, fade, fade)
        shapeBatch.fillArcBand(sx, sy, 7.6f, 3.6f, 10.4f, 5.0f,
          (-open).toFloat, (Math.PI + 2 * open).toFloat, 8, r * 0.8f, g * 0.8f, b * 0.8f, fade, fade)
        var i = 0
        while (i < 5) {
          val ang = (i * 1.27f).toFloat
          val d = 6f + 20f * t
          shapeBatch.fillOval(sx + Math.cos(ang).toFloat * d, sy + Math.sin(ang).toFloat * d * 0.5f - 6f * t,
            1.3f, 1.0f, 0.9f, 0.9f, 0.92f, 0.8f * fade, 5)
          i += 1
        }

      case TrapKind.POD =>
        // A puff of spores, rising and spreading
        var i = 0
        while (i < 4) {
          val ang = (i * 1.57f + 0.4f).toFloat
          val d = 3f + 12f * t
          shapeBatch.fillOvalSoft(sx + Math.cos(ang).toFloat * d, sy + Math.sin(ang).toFloat * d * 0.5f - 7f * t,
            5f + 7f * t, 3.5f + 5f * t, r, g, b, 0.4f * fade, 0f, 10)
          i += 1
        }

      case TrapKind.RUNE =>
        // Tongues of flame standing up out of the glyph
        var i = 0
        while (i < 5) {
          val ang = (i * 1.257f).toFloat
          val bx = sx + Math.cos(ang).toFloat * (4f + 6f * t)
          val by = sy + Math.sin(ang).toFloat * (2f + 3f * t)
          val h = 13f * (1f - t) + 4f
          _trapXs(0) = bx - 2.4f; _trapYs(0) = by
          _trapXs(1) = bx + 2.4f; _trapYs(1) = by
          _trapXs(2) = bx; _trapYs(2) = by - h
          shapeBatch.fillPolygon(_trapXs, _trapYs, 3, 1f, 0.55f + 0.3f * fade, 0.2f, 0.75f * fade)
          i += 1
        }
        shapeBatch.fillOvalSoft(sx, sy - 4f, 11f, 8f, 1f, 0.45f, 0.15f, 0.3f * fade, 0f, 12)

      case _ =>
        // A web pulling taut: the strands whip back in
        var i = 0
        while (i < 8) {
          val ang = (Math.PI * 2 * i / 8).toFloat
          val d = 12f * (1f - t)
          shapeBatch.strokeLine(sx, sy, sx + Math.cos(ang).toFloat * d, sy + Math.sin(ang).toFloat * d * 0.5f,
            1f, r, g, b, 0.7f * fade)
          i += 1
        }
    }
  }
}
