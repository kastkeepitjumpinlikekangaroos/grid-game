package com.gridgame.client.ui

import com.gridgame.common.model._
import javafx.scene.canvas.GraphicsContext
import javafx.scene.paint.Color
import javafx.scene.shape.{ArcType, StrokeLineCap}

/**
 * Renders animated ability preview canvases for the character selection screen.
 * All rendering is in simple 2D screen-space (no isometric transforms).
 */
object AbilityPreviewRenderer {
  import AbilityPreviewPalette._

  /**
   * Render an ability preview animation on a canvas.
   * For non-projectile abilities (PhaseShift, Dash, Teleport), renders themed effects.
   * For FanProjectile, renders multiple paths.
   */
  // A dark window, darker than the glass round it, so the shots' glows read as they do in a match
  private val windowFill = Color.web("#0a1029")
  private val windowEdge = Color.rgb(255, 255, 255, 0.09)
  private val laneColour = Color.rgb(255, 255, 255, 0.10)

  def render(gc: GraphicsContext, projectileType: Byte, castBehavior: CastBehavior,
             animTick: Int, canvasWidth: Double, canvasHeight: Double): Unit = {
    gc.clearRect(0, 0, canvasWidth, canvasHeight)
    gc.setFill(windowFill)
    gc.fillRoundRect(0, 0, canvasWidth, canvasHeight, 16, 16)
    gc.setStroke(windowEdge)
    gc.setLineWidth(1)
    gc.strokeRoundRect(0.5, 0.5, canvasWidth - 1, canvasHeight - 1, 15, 15)
    // The lane it runs along, from where it is cast: faint, so the window reads as a range even
    // in the moment between two casts
    gc.setStroke(laneColour)
    gc.setLineDashes(2, 6)
    gc.strokeLine(16, canvasHeight / 2, canvasWidth - 16, canvasHeight / 2)
    gc.setLineDashes()
    gc.setFill(laneColour)
    gc.fillOval(13, canvasHeight / 2 - 3, 6, 6)

    castBehavior match {
      case PhaseShiftBuff(_) =>
        renderPhaseShift(gc, animTick, canvasWidth, canvasHeight)
      case DashBuff(_, _, _) =>
        renderDash(gc, animTick, canvasWidth, canvasHeight)
      case TeleportCast(_) =>
        renderTeleport(gc, animTick, canvasWidth, canvasHeight)
      case FanProjectile(count, fanAngle) =>
        renderFan(gc, projectileType, count, fanAngle, animTick, canvasWidth, canvasHeight)
      case GroundSlam(_) =>
        renderProjectile(gc, projectileType, animTick, canvasWidth, canvasHeight)
      case StandardProjectile =>
        renderProjectile(gc, projectileType, animTick, canvasWidth, canvasHeight)
      case BarrierCast(_) =>
        renderBarrier(gc, animTick, canvasWidth, canvasHeight)
      case TrapCast(trapType, _) =>
        renderTrap(gc, trapType, animTick, canvasWidth, canvasHeight)
    }
  }

  /**
   * A trap over its whole life: thrown out along an arc, arming where it lands, and snapping on
   * whoever walks into it. The three are what the ability actually is — a throw that does nothing
   * at all for a moment and then takes somebody — so the preview shows all three rather than the
   * object sitting still.
   */
  private def renderTrap(gc: GraphicsContext, trapType: Byte, animTick: Int, w: Double, h: Double): Unit = {
    val tDef = TrapDef.get(trapType)
    if (tDef == null) return
    val col = trapColor(tDef)
    val cy = h * 0.62
    val fromX = 24.0
    val toX = w * 0.66

    val period = 132
    val t = animTick % period
    val phase = animTick * 0.15

    // Where it lands: the ground under it, drawn throughout so the arc reads as a throw
    gc.setFill(Color.color(0, 0, 0, 0.25))
    gc.fillOval(toX - 13, cy - 4, 26, 8)

    if (t < 34) {
      // Thrown: an arc out to the landing cell, with its shadow running along the ground
      val f = t / 34.0
      val x = fromX + (toX - fromX) * f
      val lift = 22 * Math.sin(f * Math.PI)
      gc.setFill(Color.color(0, 0, 0, 0.22))
      gc.fillOval(x - 5, cy - 3, 10, 6)
      gc.setFill(col.deriveColor(0, 1, 0.7, 1))
      gc.fillOval(x - 5, cy - lift - 5, 10, 10)
      gc.setStroke(col.deriveColor(0, 1, 1, 0.35))
      gc.setLineWidth(1)
      gc.strokeOval(x - 7, cy - lift - 7, 14, 14)
    } else {
      val landed = t - 34
      drawTrapTop(gc, tDef, col, toX, cy, phase, snapped = landed >= 62)
      if (landed < 26) {
        // Arming: a ring closing on it, and it is not live until it has
        val f = landed / 26.0
        val rad = 27 - 12 * f
        gc.setStroke(Color.color(col.getRed, col.getGreen, col.getBlue, 0.25 + 0.55 * f))
        ring(gc, toX, cy, rad, rad * 0.5, 1.3)
      } else if (landed < 62) {
        // Armed: a ring runs out over the ground it watches, as one of ours does in a match
        // (TrapPainter), and something walks into it
        if (landed < 40) {
          val f = (landed - 26) / 14.0
          val rad = 16 + 10 * (1 - (1 - f) * (1 - f))
          gc.setStroke(Color.color(col.getRed, col.getGreen, col.getBlue, 0.8 * (1 - f)))
          ring(gc, toX, cy, rad, rad * 0.5, 1.4)
        }
        // Live, and something walking into it
        val f = (landed - 26) / 36.0
        val px = w * 0.06 + (toX - w * 0.06) * f
        gc.setFill(Color.color(0.75, 0.8, 0.9, 0.75))
        gc.fillOval(px - 5, cy - 16, 10, 12)
        gc.fillRect(px - 3, cy - 6, 6, 8)
      } else {
        // Sprung: a flash and a ring going out from it
        val f = (landed - 62) / 36.0
        val rad = 8 + 26 * f
        gc.setStroke(Color.color(col.getRed, col.getGreen, col.getBlue, Math.max(0.0, 0.75 * (1 - f))))
        gc.setLineWidth(2)
        gc.strokeOval(toX - rad, cy - rad * 0.45, rad * 2, rad * 0.9)
        if (f < 0.4) {
          gc.setFill(Color.color(1, 1, 0.92, 0.55 * (1 - f / 0.4)))
          gc.fillOval(toX - 16, cy - 9, 32, 18)
        }
      }
    }
  }

  /**
   * The trap itself, seen from above, as a match draws it (TrapPainter): jaws, a mine, a pod, a rune
   * or a web, inked in a dark version of its own colour, with something bright on it.
   */
  private def drawTrapTop(gc: GraphicsContext, tDef: TrapDef, col: Color, cx: Double, cy: Double,
                          phase: Double, snapped: Boolean): Unit = {
    val ink = Color.color(col.getRed * 0.16 + 0.02, col.getGreen * 0.16 + 0.02, col.getBlue * 0.16 + 0.03)
    val lit = Color.color(lighter(col.getRed), lighter(col.getGreen), lighter(col.getBlue))
    gc.setLineCap(StrokeLineCap.BUTT)
    tDef.kind match {
      case TrapKind.MINE =>
        // A squat casing, a hazard band round its top, a diode blinking on its cap
        val top = cy - 2
        gc.setFill(ink)
        oval(gc, cx, cy + 1.3, 11.6, 6)
        gc.fillRect(cx - 11.6, top, 23.2, cy + 1.3 - top)
        oval(gc, cx, top, 11.6, 6)
        gc.setFill(shade(col, 0.42))
        oval(gc, cx, cy + 1.3, 10.4, 5)
        gc.fillRect(cx - 10.4, top, 20.8, cy + 1.3 - top)
        gc.setFill(shade(col, 0.85))
        oval(gc, cx, top, 10.4, 5)
        gc.setStroke(Color.color(0.1, 0.08, 0.07))
        band(gc, cx, top, 7.4, 3.6, 2.9, 0, 360)
        gc.setStroke(Color.color(1, 0.8, 0.14))
        for (k <- 0 until 6) band(gc, cx, top, 7.4, 3.6, 2.9, k * 60 + 15, 30)
        gc.setFill(ink)
        oval(gc, cx, top - 0.5, 4.7, 2.5)
        gc.setFill(Color.color(0.5, 0.5, 0.54))
        oval(gc, cx, top - 0.5, 3.9, 1.9)
        val blink = (phase * 40).toInt % 12 < 3
        if (blink) {
          gc.setFill(Color.color(1, 0.3, 0.2, 0.35))
          oval(gc, cx, top - 1.1, 7, 4.2)
        }
        gc.setFill(if (blink) Color.color(1, 0.42, 0.3) else Color.color(0.45, 0.08, 0.06))
        oval(gc, cx, top - 1.1, 1.5, 1.1)

      case TrapKind.POD =>
        // A swollen sac breathing on its slick, a glowing split at its top, spores rising off it
        val s = 1 + 0.05 * Math.sin(phase * 0.6)
        gc.setFill(Color.color(ink.getRed, ink.getGreen, ink.getBlue, 0.6))
        oval(gc, cx, cy + 1.2, 14.4, 7)
        gc.setFill(ink)
        oval(gc, cx - 5.6, cy + 0.5, 6.7 * s, 4.7 * s)
        oval(gc, cx + 5.8, cy + 0.7, 6.5 * s, 4.5 * s)
        oval(gc, cx, cy - 2, 10.4 * s, 7.2 * s)
        gc.setFill(shade(col, 0.6))
        oval(gc, cx - 5.6, cy + 0.5, 5.6 * s, 3.7 * s)
        oval(gc, cx + 5.8, cy + 0.7, 5.4 * s, 3.5 * s)
        gc.setFill(shade(col, 0.72))
        oval(gc, cx, cy - 2, 9.4 * s, 6.2 * s)
        gc.setFill(col)
        oval(gc, cx - 1.4, cy - 3.2, 7 * s, 4.4 * s)
        gc.setFill(ink)
        oval(gc, cx + 0.5, cy - 2 - 4.4 * s, 3.8, 1.9)
        gc.setFill(Color.color(0.86, 1, 0.38))
        oval(gc, cx + 0.5, cy - 2 - 4.4 * s, 2.8, 1.2)
        for (k <- 0 until 3) {
          val q = (phase * 0.35 + k / 3.0) % 1.0
          gc.setFill(Color.color(0.85, 1, 0.45, 1 - q))
          oval(gc, cx + 0.5 + (k - 1) * 2.2 + 2 * Math.sin(q * 7 + k * 2.1), cy - 8 - 10 * q, 1.1, 1)
        }

      case TrapKind.RUNE =>
        // A magic circle burnt into the ground, its marks turning, a glyph at its heart: flames
        // round a fire rune, motes rising off any other, turning the other way
        val fire = tDef.id == TrapType.FIRE_RUNE
        val flick = 0.78 + 0.22 * Math.sin(phase * 1.6)
        val hi = if (fire) Color.color(1, 0.9, 0.55) else lit
        gc.setFill(Color.color(ink.getRed * 0.4, ink.getGreen * 0.4, ink.getBlue * 0.4, 0.75))
        oval(gc, cx, cy + 0.5, 16.6, 8.3)
        gc.setFill(Color.color(col.getRed, col.getGreen, col.getBlue, 0.22 * flick))
        oval(gc, cx, cy, 14.4, 7.2)
        gc.setStroke(ink)
        ring(gc, cx, cy, 12.2, 6.1, 3.2)
        gc.setStroke(Color.color(col.getRed, col.getGreen, col.getBlue, flick))
        ring(gc, cx, cy, 12.2, 6.1, 1.8)
        val turn = (if (fire) 1 else -1) * phase * 8
        for (k <- 0 until 6) {
          gc.setStroke(ink)
          band(gc, cx, cy, 9, 4.5, 3, turn + k * 60 - 2, 28)
          gc.setStroke(hi)
          band(gc, cx, cy, 9, 4.5, 1.6, turn + k * 60, 24)
        }
        gc.setStroke(ink)
        ring(gc, cx, cy, 6.7, 3.3, 2.5)
        gc.setStroke(col)
        ring(gc, cx, cy, 6.7, 3.3, 1.2)
        if (fire) {
          glyphLine(gc, ink, hi, cx - 4, cy - 1.5, cx + 4, cy + 1.5)
          glyphLine(gc, ink, hi, cx + 4, cy - 1.5, cx - 4, cy + 1.5)
          glyphLine(gc, ink, hi, cx, cy - 2.7, cx, cy + 2.7)
          for (k <- 0 until 6) {
            val a = turn * Math.PI / 360 + k * Math.PI / 3 + 0.3
            val h = 3.1 + 2.3 * (0.5 + 0.5 * Math.sin(phase * 1.3 + k * 1.9))
            val bx = cx + 12.2 * Math.cos(a); val by = cy + 6.1 * Math.sin(a)
            lick(gc, bx, by, 2.1, h, Color.color(0.88, 0.26, 0.05, 0.95))
            lick(gc, bx, by + 0.1, 1.4, h * 0.72, Color.color(1, 0.6, 0.12))
            lick(gc, bx, by + 0.2, 0.8, h * 0.42, Color.color(1, 0.93, 0.6))
          }
        } else {
          _px(0) = cx; _py(0) = cy - 2.6
          _px(1) = cx + 4.7; _py(1) = cy
          _px(2) = cx; _py(2) = cy + 2.6
          _px(3) = cx - 4.7; _py(3) = cy
          gc.setStroke(ink); gc.setLineWidth(2.5); gc.strokePolygon(_px, _py, 4)
          gc.setStroke(hi); gc.setLineWidth(1.2); gc.strokePolygon(_px, _py, 4)
          for (k <- 0 until 3) {
            val q = (phase * 0.3 + k / 3.0) % 1.0
            val a = k * Math.PI * 2 / 3 + 0.6
            val mx = cx + 7.2 * Math.cos(a); val my = cy + 3.6 * Math.sin(a) - 11 * q
            gc.setFill(Color.color(hi.getRed, hi.getGreen, hi.getBlue, 1 - q))
            _px(0) = mx; _py(0) = my - 2
            _px(1) = mx + 1.4; _py(1) = my
            _px(2) = mx; _py(2) = my + 2
            _px(3) = mx - 1.4; _py(3) = my
            gc.fillPolygon(_px, _py, 4)
          }
        }

      case TrapKind.WEB =>
        // Silk over its own shadow, eight spokes and a spiral, dew on it
        val shadow = Color.color(0.07, 0.07, 0.11, 0.62)
        var j = 0
        while (j < WebSpiral) {
          val f = 0.2 + 0.68 * j / (WebSpiral - 1)
          val a = 0.2 + j * Math.PI / 4
          _px(j) = cx + 14.4 * f * Math.cos(a); _py(j) = cy + 7.2 * f * Math.sin(a)
          j += 1
        }
        for (pass <- 0 until 2) {
          gc.setStroke(if (pass == 0) shadow else lit)
          gc.setLineWidth(if (pass == 0) 2.1 else 0.95)
          for (k <- 0 until 8) {
            val a = 0.2 + k * Math.PI / 4
            gc.strokeLine(cx, cy, cx + 14.4 * Math.cos(a), cy + 7.2 * Math.sin(a))
          }
          gc.setLineWidth(if (pass == 0) 1.9 else 0.85)
          gc.strokePolyline(_px, _py, WebSpiral)
        }
        gc.setFill(shadow)
        oval(gc, cx, cy, 2.7, 1.7)
        gc.setFill(lit)
        oval(gc, cx, cy, 2, 1.2)
        for (d <- WebDew) {
          gc.setFill(shadow)
          oval(gc, _px(d), _py(d), 1.6, 1.25)
          gc.setFill(Color.color(0.72, 0.88, 1))
          oval(gc, _px(d), _py(d), 1.1, 0.85)
        }

      case _ =>
        // Jaws: open round the plate in a ring of teeth, a spring either side; once sprung, shut
        // in a bar across it
        val open = if (snapped) 0.25 else 1.0
        gc.setFill(ink)
        oval(gc, cx - 12.8, cy + 0.4, 5, 2.9)
        oval(gc, cx + 12.8, cy + 0.4, 5, 2.9)
        gc.setFill(shade(col, 0.55))
        oval(gc, cx - 12.8, cy + 0.4, 3.9, 1.9)
        oval(gc, cx + 12.8, cy + 0.4, 3.9, 1.9)
        gc.setFill(ink)
        oval(gc, cx, cy, 12.3, 6.3 * open + 0.8)
        gc.setStroke(col)
        band(gc, cx, cy, 9.5, 4.7 * open, 3.4, 0, 180)
        gc.setStroke(shade(col, 0.7))
        band(gc, cx, cy, 9.5, 4.7 * open, 3.4, 180, 180)
        gc.setFill(Color.color(0.96, 0.96, 0.98))
        for (k <- 0 until 10) {
          val a = Math.PI * 2 * (k + 0.5) / 10
          oval(gc, cx + 7 * Math.cos(a), cy + 3.4 * open * Math.sin(a), 1.1, 0.9)
        }
        if (!snapped) {
          gc.setFill(ink)
          oval(gc, cx, cy + 0.2, 4.4, 2.3)
          gc.setFill(Color.color(0.78, 0.3, 0.18))
          oval(gc, cx, cy + 0.2, 3.6, 1.7)
        }
    }
    gc.setLineCap(StrokeLineCap.SQUARE)
  }

  // Scratch for the traps' polygons and the web's spiral, which has a point on each of its eight
  // spokes for three and a quarter turns
  private val WebSpiral = 27
  private val WebDew = Array(7, 14, 20)
  private val _px = new Array[Double](WebSpiral)
  private val _py = new Array[Double](WebSpiral)

  private def oval(gc: GraphicsContext, x: Double, y: Double, rx: Double, ry: Double): Unit =
    gc.fillOval(x - rx, y - ry, rx * 2, ry * 2)

  private def ring(gc: GraphicsContext, x: Double, y: Double, rx: Double, ry: Double, w: Double): Unit = {
    gc.setLineWidth(w)
    gc.strokeOval(x - rx, y - ry, rx * 2, ry * 2)
  }

  /** A band `w` wide along the ellipse (rx, ry) round (x, y), from `start` degrees for `extent`,
    * counter-clockwise on screen from three o'clock. */
  private def band(gc: GraphicsContext, x: Double, y: Double, rx: Double, ry: Double, w: Double,
                   start: Double, extent: Double): Unit = {
    gc.setLineWidth(w)
    gc.strokeArc(x - rx, y - ry, rx * 2, ry * 2, start, extent, ArcType.OPEN)
  }

  private def glyphLine(gc: GraphicsContext, ink: Color, hi: Color, x0: Double, y0: Double, x1: Double, y1: Double): Unit = {
    gc.setStroke(ink); gc.setLineWidth(2.6); gc.strokeLine(x0, y0, x1, y1)
    gc.setStroke(hi); gc.setLineWidth(1.2); gc.strokeLine(x0, y0, x1, y1)
  }

  /** A lick of flame standing up from (bx, by), `half` either side at its foot, `h` tall. */
  private def lick(gc: GraphicsContext, bx: Double, by: Double, half: Double, h: Double, c: Color): Unit = {
    _px(0) = bx - half; _py(0) = by - 0.2 * h
    _px(1) = bx - 0.55 * half; _py(1) = by + 0.45 * half
    _px(2) = bx + 0.55 * half; _py(2) = by + 0.45 * half
    _px(3) = bx + half; _py(3) = by - 0.2 * h
    _px(4) = bx; _py(4) = by - h
    gc.setFill(c)
    gc.fillPolygon(_px, _py, 5)
  }

  private def shade(c: Color, k: Double): Color = Color.color(c.getRed * k, c.getGreen * k, c.getBlue * k)

  private def lighter(c: Double): Double = c + (1 - c) * 0.55

  private def trapColor(tDef: TrapDef): Color =
    Color.rgb((tDef.colorRGB >> 16) & 0xFF, (tDef.colorRGB >> 8) & 0xFF, tDef.colorRGB & 0xFF)

  private def renderProjectile(gc: GraphicsContext, projType: Byte, animTick: Int,
                                w: Double, h: Double): Unit = {
    val (primary, secondary, core) = getColors(projType)
    val shape = getShape(projType)
    val cycleLen = 120.0
    val t = (animTick % cycleLen) / cycleLen
    val x = 16 + t * (w - 32)
    val cy = h / 2.0
    val phase = animTick * 0.15
    val pulse = 0.8 + 0.2 * Math.sin(phase)

    // Trail
    drawTrail(gc, x, cy, primary, pulse, 30)

    shape match {
      case Beam => drawBeamShape(gc, x, cy, primary, secondary, core, pulse, phase)
      case Orb => drawOrbShape(gc, x, cy, primary, secondary, core, pulse, phase)
      case Spinner => drawSpinnerShape(gc, x, cy, primary, secondary, core, pulse, phase)
      case Tendril => drawTendrilShape(gc, x, cy, primary, secondary, core, pulse, phase)
      case Cloud => drawCloudShape(gc, x, cy, primary, secondary, core, pulse, phase)
      case Dart => drawDartShape(gc, x, cy, primary, secondary, core, pulse, phase)
      case Chain => drawChainShape(gc, x, cy, primary, secondary, core, pulse, phase)
      case Mine => drawMineShape(gc, x, cy, primary, secondary, core, pulse, phase)
    }
  }

  private def renderFan(gc: GraphicsContext, projType: Byte, count: Int, fanAngle: Double,
                         animTick: Int, w: Double, h: Double): Unit = {
    val (primary, secondary, core) = getColors(projType)
    val shape = getShape(projType)
    val cycleLen = 120.0
    val t = (animTick % cycleLen) / cycleLen
    val startX = 16.0
    val cx = h / 2.0
    val phase = animTick * 0.15
    val pulse = 0.8 + 0.2 * Math.sin(phase)

    val isFullCircle = fanAngle >= 2 * Math.PI - 0.1
    val displayCount = Math.min(count, if (isFullCircle) 6 else count)

    for (i <- 0 until displayCount) {
      val angle = if (isFullCircle) {
        (i.toDouble / displayCount) * 2 * Math.PI
      } else {
        -fanAngle / 2.0 + (i.toDouble / (displayCount - 1).max(1)) * fanAngle
      }

      val dist = t * (w - 32)
      val px = startX + Math.cos(angle) * dist
      val py = cx + Math.sin(angle) * dist * 0.6 // compress Y for canvas aspect

      if (px > 0 && px < w && py > 2 && py < h - 2) {
        // Smaller trail and shapes for fan
        drawTrail(gc, px, py, primary, pulse, 10)
        val smallPulse = pulse * 0.7
        shape match {
          case Beam => drawBeamShape(gc, px, py, primary, secondary, core, smallPulse, phase, 0.5)
          case Orb => drawOrbShape(gc, px, py, primary, secondary, core, smallPulse, phase, 0.5)
          case _ => drawOrbShape(gc, px, py, primary, secondary, core, smallPulse, phase, 0.5)
        }
      }
    }
  }

  private def renderPhaseShift(gc: GraphicsContext, animTick: Int, w: Double, h: Double): Unit = {
    val phase = animTick * 0.08
    val cx = w / 2.0
    val cy = h / 2.0

    // Ghost shimmer effect
    for (i <- 0 until 5) {
      val offset = Math.sin(phase + i * 1.2) * 15
      val alpha = 0.12 + 0.08 * Math.sin(phase * 2 + i)
      gc.setFill(Color.color(0.5, 0.7, 1.0, alpha))
      gc.fillOval(cx - 12 + offset, cy - 10 + Math.cos(phase + i) * 5, 24, 20)
    }

    // Central figure outline
    val flicker = 0.3 + 0.3 * Math.sin(phase * 3)
    gc.setStroke(Color.color(0.6, 0.8, 1.0, flicker))
    gc.setLineWidth(1.5)
    gc.strokeOval(cx - 8, cy - 10, 16, 20)

    // Phase arrows
    val arrowAlpha = 0.2 + 0.15 * Math.sin(phase * 2)
    gc.setFill(Color.color(0.5, 0.7, 1.0, arrowAlpha))
    val arrowX = (animTick % 60) / 60.0 * w
    gc.fillPolygon(
      Array(arrowX, arrowX - 6, arrowX - 6),
      Array(cy, cy - 4, cy + 4), 3)
  }

  private def renderDash(gc: GraphicsContext, animTick: Int, w: Double, h: Double): Unit = {
    val phase = animTick * 0.12
    val cy = h / 2.0
    val cycleLen = 80.0
    val t = (animTick % cycleLen) / cycleLen

    // Speed lines
    for (i <- 0 until 8) {
      val lineX = (t * w + i * w / 8.0) % w
      val lineY = cy + Math.sin(phase + i * 0.8) * (h * 0.3)
      val alpha = 0.15 + 0.1 * Math.sin(phase + i)
      gc.setStroke(Color.color(0.7, 0.85, 1.0, alpha))
      gc.setLineWidth(1.5)
      gc.strokeLine(lineX, lineY, lineX - 20, lineY)
    }

    // Moving figure
    val figX = 20 + t * (w - 40)
    gc.setFill(Color.color(0.6, 0.8, 1.0, 0.4))
    gc.fillOval(figX - 5, cy - 7, 10, 14)

    // Motion blur trail
    for (i <- 1 to 4) {
      val alpha = 0.15 - i * 0.03
      gc.setFill(Color.color(0.5, 0.7, 1.0, Math.max(0.02, alpha)))
      gc.fillOval(figX - 5 - i * 8, cy - 7, 10, 14)
    }
  }

  private def renderTeleport(gc: GraphicsContext, animTick: Int, w: Double, h: Double): Unit = {
    val phase = animTick * 0.1
    val cy = h / 2.0
    val cycleLen = 90.0
    val t = (animTick % cycleLen) / cycleLen

    val startX = w * 0.25
    val endX = w * 0.75

    if (t < 0.4) {
      // Fade out at start position
      val fadeOut = 1.0 - t / 0.4
      gc.setFill(Color.color(0.5, 0.4, 1.0, 0.3 * fadeOut))
      gc.fillOval(startX - 8, cy - 10, 16, 20)

      // Sparkle particles expanding
      for (i <- 0 until 6) {
        val angle = i * Math.PI / 3 + phase
        val dist = (1.0 - fadeOut) * 15
        val px = startX + Math.cos(angle) * dist
        val py = cy + Math.sin(angle) * dist
        gc.setFill(Color.color(0.7, 0.5, 1.0, 0.4 * fadeOut))
        gc.fillOval(px - 2, py - 2, 4, 4)
      }
    } else if (t < 0.6) {
      // Flash line connecting start to end
      val flashAlpha = Math.sin((t - 0.4) / 0.2 * Math.PI) * 0.3
      gc.setStroke(Color.color(0.6, 0.4, 1.0, flashAlpha))
      gc.setLineWidth(2)
      gc.strokeLine(startX, cy, endX, cy)
    } else {
      // Fade in at end position
      val fadeIn = (t - 0.6) / 0.4
      gc.setFill(Color.color(0.5, 0.4, 1.0, 0.3 * fadeIn))
      gc.fillOval(endX - 8, cy - 10, 16, 20)

      // Sparkle particles contracting
      for (i <- 0 until 6) {
        val angle = i * Math.PI / 3 + phase
        val dist = (1.0 - fadeIn) * 15
        val px = endX + Math.cos(angle) * dist
        val py = cy + Math.sin(angle) * dist
        gc.setFill(Color.color(0.7, 0.5, 1.0, 0.4 * fadeIn))
        gc.fillOval(px - 2, py - 2, 4, 4)
      }
    }
  }

  /** A figure behind a raised barrier, and enemy shots coming in from the right and stopping on
    * it, each with a ripple where it struck. The barrier is Barrier's own polyline, turned to face
    * right: bent back at the ends, as it is in the game. */
  private def renderBarrier(gc: GraphicsContext, animTick: Int, w: Double, h: Double): Unit = {
    val cy = h / 2.0
    val figX = 34.0
    // Barrier's frame on the canvas: px per cell across it, and where the ends and middle stand
    val perCell = (h / 2.0 - 5.0) / (Barrier.WIDTH / 2)
    def barrierX(across: Double): Double =
      figX + 13.0 + (Barrier.forwardAt(across.toFloat) - (Barrier.DISTANCE - Barrier.BEND)) / Barrier.BEND * 7.0
    def barrierY(across: Double): Double = cy + across * perCell

    val period = 54
    val flight = 36
    val shots = Array(-2.0, 0.4, 2.2)
    // How hard the barrier has just been struck: it flares with each ripple
    var flare = 0.0
    for (i <- shots.indices) {
      val t = (animTick + i * (period / shots.length)) % period
      if (t >= flight) flare = Math.max(flare, 1.0 - (t - flight).toDouble / (period - flight))
    }

    // The holder
    gc.setFill(Color.color(0.6, 0.8, 1.0, 0.45))
    gc.fillOval(figX - 5, cy - 7, 10, 14)

    // The barrier: a glow along it, the line of it, and a post at each end
    val xs = Array.tabulate(Barrier.POINTS)(i => barrierX(Barrier.localA(i)))
    val ys = Array.tabulate(Barrier.POINTS)(i => barrierY(Barrier.localA(i)))
    gc.setStroke(Color.color(0.35, 0.7, 1.0, 0.16 + 0.2 * flare))
    gc.setLineWidth(7)
    gc.strokePolyline(xs, ys, Barrier.POINTS)
    gc.setStroke(Color.color(0.7, 0.88, 1.0, 0.75 + 0.25 * flare))
    gc.setLineWidth(1.8)
    gc.strokePolyline(xs, ys, Barrier.POINTS)
    gc.setFill(Color.color(0.8, 0.92, 1.0, 0.9))
    gc.fillOval(xs(0) - 2, ys(0) - 2, 4, 4)
    gc.fillOval(xs(Barrier.POINTS - 1) - 2, ys(Barrier.POINTS - 1) - 2, 4, 4)

    // Shots flying in, and the ripple each leaves where the barrier stops it
    for (i <- shots.indices) {
      val t = (animTick + i * (period / shots.length)) % period
      val sy = barrierY(shots(i))
      val stopX = barrierX(shots(i)) + 3
      if (t < flight) {
        val x = (w - 8) - (t.toDouble / flight) * ((w - 8) - stopX)
        drawTrail(gc, x + 10, sy, Color.web("#ff7744"), 1.0, 22)
        gc.setFill(Color.color(1.0, 0.45, 0.25, 0.3))
        gc.fillOval(x - 6, sy - 6, 12, 12)
        gc.setFill(Color.color(1.0, 0.75, 0.5, 0.9))
        gc.fillOval(x - 3, sy - 3, 6, 6)
      } else {
        val r = (t - flight).toDouble / (period - flight)
        val fade = 1.0 - r
        gc.setFill(Color.color(0.8, 0.92, 1.0, 0.55 * fade * fade))
        gc.fillOval(stopX - 4, sy - 4, 8, 8)
        gc.setStroke(Color.color(0.6, 0.85, 1.0, 0.85 * fade))
        gc.setLineWidth(0.8 + 1.4 * fade)
        val rx = 2.0 + 5.0 * r
        val ry = 3.0 + 9.0 * r
        gc.strokeOval(stopX - 3 - rx, sy - ry, rx * 2, ry * 2)
      }
    }
  }

  // --- Shape drawing helpers ---

  private def drawTrail(gc: GraphicsContext, x: Double, cy: Double,
                         color: Color, pulse: Double, length: Int): Unit = {
    for (i <- 1 to 4) {
      val alpha = (0.08 - i * 0.015) * pulse
      if (alpha > 0) {
        gc.setFill(Color.color(
          color.getRed, color.getGreen, color.getBlue, Math.max(0.01, alpha)))
        gc.fillOval(x - 3 - i * (length / 4.0), cy - 2, 6, 4)
      }
    }
  }

  private def drawBeamShape(gc: GraphicsContext, x: Double, cy: Double,
                              primary: Color, secondary: Color, core: Color,
                              pulse: Double, phase: Double, scale: Double = 1.0): Unit = {
    val len = 18 * scale
    val width = 4 * scale * pulse

    // Outer glow
    gc.setStroke(Color.color(primary.getRed, primary.getGreen, primary.getBlue, 0.2 * pulse))
    gc.setLineWidth(width + 4 * scale)
    gc.strokeLine(x - len, cy, x, cy)

    // Main beam
    gc.setStroke(Color.color(secondary.getRed, secondary.getGreen, secondary.getBlue, 0.6 * pulse))
    gc.setLineWidth(width)
    gc.strokeLine(x - len * 0.8, cy, x, cy)

    // Core
    gc.setStroke(Color.color(core.getRed, core.getGreen, core.getBlue, 0.8))
    gc.setLineWidth(Math.max(1, width * 0.4))
    gc.strokeLine(x - len * 0.5, cy, x, cy)

    // Tip glow
    gc.setFill(Color.color(core.getRed, core.getGreen, core.getBlue, 0.5 * pulse))
    gc.fillOval(x - 3 * scale, cy - 3 * scale, 6 * scale, 6 * scale)
  }

  private def drawOrbShape(gc: GraphicsContext, x: Double, cy: Double,
                             primary: Color, secondary: Color, core: Color,
                             pulse: Double, phase: Double, scale: Double = 1.0): Unit = {
    val r = 7 * scale * pulse

    // Outer glow
    gc.setFill(Color.color(primary.getRed, primary.getGreen, primary.getBlue, 0.12 * pulse))
    gc.fillOval(x - r * 1.8, cy - r * 1.8, r * 3.6, r * 3.6)

    // Main orb
    gc.setFill(Color.color(secondary.getRed, secondary.getGreen, secondary.getBlue, 0.5 * pulse))
    gc.fillOval(x - r, cy - r, r * 2, r * 2)

    // Inner core
    gc.setFill(Color.color(core.getRed, core.getGreen, core.getBlue, 0.7))
    gc.fillOval(x - r * 0.4, cy - r * 0.4, r * 0.8, r * 0.8)
  }

  private def drawSpinnerShape(gc: GraphicsContext, x: Double, cy: Double,
                                 primary: Color, secondary: Color, core: Color,
                                 pulse: Double, phase: Double, scale: Double = 1.0): Unit = {
    val r = 6 * scale
    val spinAngle = phase * 4

    // Spinning blades
    for (i <- 0 until 4) {
      val angle = spinAngle + i * Math.PI / 2
      val bx = x + Math.cos(angle) * r
      val by = cy + Math.sin(angle) * r * 0.6
      gc.setFill(Color.color(secondary.getRed, secondary.getGreen, secondary.getBlue, 0.6 * pulse))
      gc.fillOval(bx - 2 * scale, by - 2 * scale, 4 * scale, 4 * scale)
    }

    // Center
    gc.setFill(Color.color(core.getRed, core.getGreen, core.getBlue, 0.7))
    gc.fillOval(x - 3 * scale, cy - 3 * scale, 6 * scale, 6 * scale)
  }

  private def drawTendrilShape(gc: GraphicsContext, x: Double, cy: Double,
                                 primary: Color, secondary: Color, core: Color,
                                 pulse: Double, phase: Double, scale: Double = 1.0): Unit = {
    val len = 14 * scale

    // Wavy tendrils
    gc.setStroke(Color.color(primary.getRed, primary.getGreen, primary.getBlue, 0.3 * pulse))
    gc.setLineWidth(2 * scale)
    for (i <- 0 until 3) {
      val yOff = (i - 1) * 4 * scale
      val waveOff = Math.sin(phase * 3 + i * 2) * 3 * scale
      gc.strokeLine(x - len, cy + yOff + waveOff, x, cy + yOff)
    }

    // Tip
    gc.setFill(Color.color(secondary.getRed, secondary.getGreen, secondary.getBlue, 0.6 * pulse))
    gc.fillOval(x - 3 * scale, cy - 3 * scale, 6 * scale, 6 * scale)

    // Core dot
    gc.setFill(Color.color(core.getRed, core.getGreen, core.getBlue, 0.7))
    gc.fillOval(x - 1.5 * scale, cy - 1.5 * scale, 3 * scale, 3 * scale)
  }

  private def drawCloudShape(gc: GraphicsContext, x: Double, cy: Double,
                               primary: Color, secondary: Color, core: Color,
                               pulse: Double, phase: Double, scale: Double = 1.0): Unit = {
    // Roiling cloud particles
    for (i <- 0 until 5) {
      val angle = phase * 2 + i * Math.PI * 2 / 5
      val dist = 5 * scale * pulse
      val px = x + Math.cos(angle) * dist
      val py = cy + Math.sin(angle) * dist * 0.5
      val alpha = 0.15 + 0.1 * Math.sin(phase + i)
      gc.setFill(Color.color(primary.getRed, primary.getGreen, primary.getBlue, alpha))
      gc.fillOval(px - 4 * scale, py - 4 * scale, 8 * scale, 8 * scale)
    }

    // Center mass
    gc.setFill(Color.color(secondary.getRed, secondary.getGreen, secondary.getBlue, 0.3 * pulse))
    gc.fillOval(x - 6 * scale, cy - 5 * scale, 12 * scale, 10 * scale)
  }

  private def drawDartShape(gc: GraphicsContext, x: Double, cy: Double,
                              primary: Color, secondary: Color, core: Color,
                              pulse: Double, phase: Double, scale: Double = 1.0): Unit = {
    // Arrow/dart shape pointing right
    val len = 10 * scale
    val halfW = 3 * scale

    // Shaft glow
    gc.setStroke(Color.color(primary.getRed, primary.getGreen, primary.getBlue, 0.3 * pulse))
    gc.setLineWidth(3 * scale)
    gc.strokeLine(x - len, cy, x, cy)

    // Arrow head
    gc.setFill(Color.color(secondary.getRed, secondary.getGreen, secondary.getBlue, 0.7 * pulse))
    gc.fillPolygon(
      Array(x + 4 * scale, x - 4 * scale, x - 4 * scale),
      Array(cy, cy - halfW, cy + halfW), 3)

    // Tip
    gc.setFill(Color.color(core.getRed, core.getGreen, core.getBlue, 0.8))
    gc.fillOval(x + 1 * scale, cy - 2 * scale, 4 * scale, 4 * scale)
  }

  private def drawChainShape(gc: GraphicsContext, x: Double, cy: Double,
                               primary: Color, secondary: Color, core: Color,
                               pulse: Double, phase: Double, scale: Double = 1.0): Unit = {
    // Chain links
    val linkCount = 4
    val linkSpacing = 5 * scale

    for (i <- 0 until linkCount) {
      val lx = x - i * linkSpacing
      val ly = cy + Math.sin(phase * 3 + i * 1.5) * 2 * scale
      gc.setStroke(Color.color(secondary.getRed, secondary.getGreen, secondary.getBlue, 0.5 * pulse))
      gc.setLineWidth(1.5 * scale)
      gc.strokeOval(lx - 3 * scale, ly - 2 * scale, 6 * scale, 4 * scale)
    }

    // Tip
    gc.setFill(Color.color(core.getRed, core.getGreen, core.getBlue, 0.7))
    gc.fillOval(x - 2 * scale, cy - 2 * scale, 4 * scale, 4 * scale)
  }

  private def drawMineShape(gc: GraphicsContext, x: Double, cy: Double,
                              primary: Color, secondary: Color, core: Color,
                              pulse: Double, phase: Double, scale: Double = 1.0): Unit = {
    val bounce = Math.abs(Math.sin(phase * 2)) * 3 * scale
    val my = cy - bounce
    val r = 6 * scale

    // Outer warning glow
    gc.setFill(Color.color(primary.getRed, primary.getGreen, primary.getBlue, 0.1 * pulse))
    gc.fillOval(x - r * 2, my - r * 2, r * 4, r * 4)

    // Mine body
    gc.setFill(Color.color(secondary.getRed, secondary.getGreen, secondary.getBlue, 0.6 * pulse))
    gc.fillOval(x - r, my - r, r * 2, r * 2)

    // Spikes
    for (i <- 0 until 6) {
      val angle = phase * 0.5 + i * Math.PI / 3
      val sx = x + Math.cos(angle) * (r + 2 * scale)
      val sy = my + Math.sin(angle) * (r + 2 * scale) * 0.7
      gc.setFill(Color.color(core.getRed, core.getGreen, core.getBlue, 0.5 * pulse))
      gc.fillOval(sx - 1.5 * scale, sy - 1.5 * scale, 3 * scale, 3 * scale)
    }

    // Blinking light
    val blinkAlpha = if (Math.sin(phase * 4) > 0) 0.8 else 0.2
    gc.setFill(Color.color(core.getRed, core.getGreen, core.getBlue, blinkAlpha))
    gc.fillOval(x - 2 * scale, my - 2 * scale, 4 * scale, 4 * scale)
  }
}
