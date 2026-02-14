package com.gridgame.client.ui

import javafx.scene.canvas.GraphicsContext
import javafx.scene.paint.Color

object BackgroundRenderer {

  // Pseudo-random seeded by index for deterministic star/building positions
  private def hash(seed: Int): Double = {
    val x = Math.sin(seed.toDouble * 127.1 + 311.7) * 43758.5453
    x - x.floor
  }

  def render(gc: GraphicsContext, w: Double, h: Double, background: String,
             tick: Int, camOffX: Double, camOffY: Double): Unit = {
    background match {
      case "sky"       => drawSky(gc, w, h, tick, camOffX, camOffY)
      case "cityscape" => drawCityscape(gc, w, h, tick, camOffX, camOffY)
      case "space"     => drawSpace(gc, w, h, tick, camOffX, camOffY)
      case "desert"    => drawDesert(gc, w, h, tick, camOffX, camOffY)
      case "ocean"     => drawOcean(gc, w, h, tick, camOffX, camOffY)
      case _           => drawSky(gc, w, h, tick, camOffX, camOffY)
    }
  }

  // ── Sky: blue gradient + drifting clouds ──

  private def drawSky(gc: GraphicsContext, w: Double, h: Double, tick: Int,
                      camOffX: Double, camOffY: Double): Unit = {
    // Gradient sky
    val bands = 12
    for (i <- 0 until bands) {
      val t = i.toDouble / bands
      val r = 0.35 + t * 0.15
      val g = 0.55 + t * 0.15
      val b = 0.92 - t * 0.15
      gc.setFill(Color.color(r, g, b))
      gc.fillRect(0, h * t / 1.0, w, h / bands + 1)
    }

    // Parallax offset
    val px = camOffX * 0.03
    val py = camOffY * 0.02

    // Cloud layers: (yBase fraction, speed, scale, opacity, count)
    val layers = Seq(
      (0.10, 0.15, 1.6, 0.18, 6),  // far, big, faint
      (0.20, 0.3,  1.2, 0.25, 8),  // mid
      (0.05, 0.5,  1.0, 0.35, 7)   // near, sharp
    )

    for ((yFrac, speed, scale, alpha, count) <- layers) {
      for (i <- 0 until count) {
        val seed = yFrac.hashCode + i * 73
        val baseX = hash(seed) * w * 1.5 - w * 0.25
        val baseY = h * yFrac + hash(seed + 1) * h * 0.25

        // Drift with time + parallax
        val drift = (tick * speed + px * (1.0 + yFrac)) % (w + 300) - 150
        val cx = (baseX + drift) % (w + 300) - 150
        val cy = baseY + py * (0.5 + yFrac) + Math.sin(tick * 0.01 + i) * 3

        drawCloud(gc, cx, cy, scale, alpha)
      }
    }
  }

  private def drawCloud(gc: GraphicsContext, cx: Double, cy: Double, scale: Double, alpha: Double): Unit = {
    gc.setFill(Color.color(1.0, 1.0, 1.0, alpha))
    // Cluster of overlapping ovals
    val s = 30 * scale
    gc.fillOval(cx - s * 1.5, cy - s * 0.3, s * 3, s * 0.7)
    gc.fillOval(cx - s * 0.8, cy - s * 0.6, s * 1.8, s * 0.8)
    gc.fillOval(cx + s * 0.2, cy - s * 0.5, s * 1.4, s * 0.7)
    gc.fillOval(cx - s * 1.2, cy - s * 0.15, s * 2.5, s * 0.5)

    // Slight highlight on top
    gc.setFill(Color.color(1.0, 1.0, 1.0, alpha * 0.5))
    gc.fillOval(cx - s * 0.5, cy - s * 0.65, s * 1.2, s * 0.4)
  }

  // ── Cityscape: dark skyline + twinkling windows ──

  private def drawCityscape(gc: GraphicsContext, w: Double, h: Double, tick: Int,
                            camOffX: Double, camOffY: Double): Unit = {
    // Dark gradient sky
    val bands = 12
    for (i <- 0 until bands) {
      val t = i.toDouble / bands
      val r = 0.05 + t * 0.08
      val g = 0.02 + t * 0.05
      val b = 0.15 - t * 0.05
      gc.setFill(Color.color(r, g, b))
      gc.fillRect(0, h * t, w, h / bands + 1)
    }

    // Horizon glow
    gc.setFill(Color.color(0.2, 0.08, 0.3, 0.3))
    gc.fillRect(0, h * 0.55, w, h * 0.15)
    gc.setFill(Color.color(0.3, 0.1, 0.4, 0.15))
    gc.fillRect(0, h * 0.5, w, h * 0.1)

    val px = camOffX * 0.02

    // Far buildings (silhouettes)
    val buildingCount = 30
    for (i <- 0 until buildingCount) {
      val bx = (hash(i * 7) * (w + 200) - 100 + px * 0.5) % (w + 200) - 100
      val bw = 25 + hash(i * 7 + 1) * 45
      val bh = h * (0.12 + hash(i * 7 + 2) * 0.30)
      val by = h - bh

      // Building body
      gc.setFill(Color.color(0.06, 0.04, 0.1, 0.9))
      gc.fillRect(bx, by, bw, bh)

      // Windows
      val windowW = 3.0
      val windowH = 4.0
      val gapX = 7.0
      val gapY = 8.0
      var wy = by + 5
      var windowIdx = 0
      while (wy < h - gapY) {
        var wx = bx + 4
        while (wx < bx + bw - windowW - 2) {
          val windowSeed = i * 1000 + windowIdx
          val isLit = hash(windowSeed) > 0.4
          if (isLit) {
            // Twinkle: some windows flicker
            val flicker = if (hash(windowSeed + 500) > 0.7)
              0.6 + 0.4 * Math.sin(tick * 0.05 + hash(windowSeed + 300) * 20)
            else 1.0

            val warmth = hash(windowSeed + 100)
            if (warmth < 0.6) {
              // Warm yellow/orange
              gc.setFill(Color.color(1.0, 0.85, 0.3, 0.7 * flicker))
            } else if (warmth < 0.85) {
              // Cool white
              gc.setFill(Color.color(0.8, 0.85, 1.0, 0.6 * flicker))
            } else {
              // Neon accent (cyan/magenta)
              val neonHue = hash(windowSeed + 200)
              if (neonHue < 0.5)
                gc.setFill(Color.color(0.0, 0.9, 1.0, 0.7 * flicker))
              else
                gc.setFill(Color.color(1.0, 0.2, 0.8, 0.7 * flicker))
            }
            gc.fillRect(wx, wy, windowW, windowH)
          }
          wx += gapX
          windowIdx += 1
        }
        wy += gapY
      }
    }

    // Near buildings (darker, larger, fewer windows)
    for (i <- 0 until 10) {
      val bx = (hash(i * 13 + 200) * (w + 300) - 150 + px) % (w + 300) - 150
      val bw = 50 + hash(i * 13 + 201) * 60
      val bh = h * (0.08 + hash(i * 13 + 202) * 0.15)
      val by = h - bh

      gc.setFill(Color.color(0.03, 0.02, 0.06, 0.95))
      gc.fillRect(bx, by, bw, bh)
    }
  }

  // ── Space: starfield + nebula ──

  private def drawSpace(gc: GraphicsContext, w: Double, h: Double, tick: Int,
                        camOffX: Double, camOffY: Double): Unit = {
    // Near-black gradient
    val bands = 8
    for (i <- 0 until bands) {
      val t = i.toDouble / bands
      val b = 0.02 + t * 0.04
      gc.setFill(Color.color(b * 0.3, b * 0.2, b))
      gc.fillRect(0, h * t, w, h / bands + 1)
    }

    val px = camOffX * 0.01
    val py = camOffY * 0.01

    // Nebula blobs
    drawNebula(gc, w * 0.3 + px * 2, h * 0.35 + py * 2, w * 0.35, h * 0.3,
      0.4, 0.1, 0.6, 0.06 + 0.02 * Math.sin(tick * 0.008))
    drawNebula(gc, w * 0.75 + px * 1.5, h * 0.6 + py * 1.5, w * 0.25, h * 0.2,
      0.1, 0.3, 0.5, 0.04 + 0.015 * Math.sin(tick * 0.01 + 2))

    // Stars
    val starCount = 120
    for (i <- 0 until starCount) {
      val sx = (hash(i * 3) * w + px * (0.5 + hash(i * 3 + 10) * 2)) % w
      val sy = (hash(i * 3 + 1) * h + py * (0.5 + hash(i * 3 + 11) * 2)) % h
      val baseBrightness = 0.3 + hash(i * 3 + 2) * 0.7

      // Twinkle
      val twinkleSpeed = 0.02 + hash(i * 3 + 5) * 0.04
      val twinklePhase = hash(i * 3 + 6) * Math.PI * 2
      val twinkle = 0.5 + 0.5 * Math.sin(tick * twinkleSpeed + twinklePhase)
      val brightness = baseBrightness * (0.4 + 0.6 * twinkle)

      // Star color (mostly white, some blue/yellow tint)
      val colorSeed = hash(i * 3 + 7)
      val (sr, sg, sb) = if (colorSeed < 0.6) (1.0, 1.0, 1.0)
        else if (colorSeed < 0.8) (0.7, 0.8, 1.0) // blue
        else (1.0, 0.95, 0.7) // yellow

      val size = 1.0 + hash(i * 3 + 8) * 2.0

      gc.setFill(Color.color(sr, sg, sb, brightness))
      gc.fillOval(sx - size / 2, sy - size / 2, size, size)

      // Bright stars get a cross-shaped glint
      if (baseBrightness > 0.8 && twinkle > 0.7) {
        val glintLen = size * 2.5 * twinkle
        gc.setStroke(Color.color(sr, sg, sb, brightness * 0.4))
        gc.setLineWidth(0.5)
        gc.strokeLine(sx - glintLen, sy, sx + glintLen, sy)
        gc.strokeLine(sx, sy - glintLen, sx, sy + glintLen)
      }
    }

    // Distant planet/moon
    val moonX = w * 0.8 + px * 3
    val moonY = h * 0.2 + py * 3
    val moonR = 25.0
    gc.setFill(Color.color(0.25, 0.22, 0.35, 0.6))
    gc.fillOval(moonX - moonR, moonY - moonR, moonR * 2, moonR * 2)
    // Crescent shadow
    gc.setFill(Color.color(0.02, 0.01, 0.05, 0.7))
    gc.fillOval(moonX - moonR * 0.6, moonY - moonR, moonR * 2, moonR * 2)
  }

  private def drawNebula(gc: GraphicsContext, cx: Double, cy: Double,
                         rw: Double, rh: Double,
                         nr: Double, ng: Double, nb: Double, alpha: Double): Unit = {
    val layers = 5
    for (i <- layers to 1 by -1) {
      val t = i.toDouble / layers
      gc.setFill(Color.color(nr, ng, nb, alpha * t * 0.6))
      val lw = rw * t
      val lh = rh * t
      gc.fillOval(cx - lw / 2, cy - lh / 2, lw, lh)
    }
  }

  // ── Desert: amber sky + dunes + sun ──

  private def drawDesert(gc: GraphicsContext, w: Double, h: Double, tick: Int,
                         camOffX: Double, camOffY: Double): Unit = {
    // Warm gradient
    val bands = 12
    for (i <- 0 until bands) {
      val t = i.toDouble / bands
      val r = 0.95 - t * 0.25
      val g = 0.65 - t * 0.20
      val b = 0.25 - t * 0.10
      gc.setFill(Color.color(
        Math.max(0, Math.min(1, r)),
        Math.max(0, Math.min(1, g)),
        Math.max(0, Math.min(1, b))
      ))
      gc.fillRect(0, h * t, w, h / bands + 1)
    }

    val px = camOffX * 0.02
    val py = camOffY * 0.015

    // Sun
    val sunX = w * 0.75 + px * 0.5
    val sunY = h * 0.18 + py * 0.5
    val sunR = 35.0

    // Sun glow layers
    val pulse = 0.9 + 0.1 * Math.sin(tick * 0.015)
    gc.setFill(Color.color(1.0, 0.9, 0.5, 0.04 * pulse))
    gc.fillOval(sunX - sunR * 5, sunY - sunR * 5, sunR * 10, sunR * 10)
    gc.setFill(Color.color(1.0, 0.85, 0.4, 0.08 * pulse))
    gc.fillOval(sunX - sunR * 3, sunY - sunR * 3, sunR * 6, sunR * 6)
    gc.setFill(Color.color(1.0, 0.8, 0.3, 0.15 * pulse))
    gc.fillOval(sunX - sunR * 1.8, sunY - sunR * 1.8, sunR * 3.6, sunR * 3.6)
    // Sun disc
    gc.setFill(Color.color(1.0, 0.95, 0.7, 0.9))
    gc.fillOval(sunX - sunR, sunY - sunR, sunR * 2, sunR * 2)

    // Heat shimmer lines
    gc.setStroke(Color.color(1.0, 0.9, 0.6, 0.06))
    gc.setLineWidth(1.5)
    for (i <- 0 until 15) {
      val ly = h * 0.45 + i * h * 0.035
      val phase = tick * 0.03 + i * 0.7
      val points = 30
      val xStep = w / points
      for (j <- 0 until points) {
        val x1 = j * xStep
        val x2 = (j + 1) * xStep
        val y1 = ly + Math.sin(phase + j * 0.3) * 2.5
        val y2 = ly + Math.sin(phase + (j + 1) * 0.3) * 2.5
        gc.strokeLine(x1, y1, x2, y2)
      }
    }

    // Sand dune silhouettes (back layer)
    drawDuneLayer(gc, w, h, 0.70, 0.65, 0.45, 0.20, 0.7, px * 0.3, tick, 0)
    // Front layer
    drawDuneLayer(gc, w, h, 0.60, 0.50, 0.30, 0.12, 0.85, px * 0.6, tick, 100)
  }

  private def drawDuneLayer(gc: GraphicsContext, w: Double, h: Double,
                            r: Double, g: Double, b: Double, alpha: Double,
                            yFrac: Double, parallax: Double, tick: Int, seedOff: Int): Unit = {
    gc.setFill(Color.color(r, g, b, alpha))

    val baseY = h * yFrac
    val points = 60
    val xStep = (w + 40) / points
    val xPoints = new Array[Double](points + 2)
    val yPoints = new Array[Double](points + 2)

    for (i <- 0 to points) {
      val x = -20 + i * xStep + parallax
      val duneY = baseY +
        Math.sin(i * 0.15 + seedOff * 0.1) * h * 0.04 +
        Math.sin(i * 0.07 + seedOff * 0.3 + tick * 0.002) * h * 0.02 +
        Math.sin(i * 0.3 + seedOff * 0.5) * h * 0.015
      xPoints(i) = x
      yPoints(i) = duneY
    }
    // Close polygon at bottom
    xPoints(points + 1) = w + 20
    yPoints(points + 1) = h + 10
    // Prepend bottom-left
    val allX = Array(-20.0) ++ xPoints
    val allY = Array(h + 10.0) ++ yPoints

    gc.fillPolygon(allX, allY, allX.length)
  }

  // ── Ocean: deep water gradient + rolling waves + light caustics ──

  private def drawOcean(gc: GraphicsContext, w: Double, h: Double, tick: Int,
                        camOffX: Double, camOffY: Double): Unit = {
    // Deep ocean gradient (dark blue at top to slightly lighter at bottom)
    val bands = 12
    for (i <- 0 until bands) {
      val t = i.toDouble / bands
      val r = 0.02 + t * 0.04
      val g = 0.08 + t * 0.12
      val b = 0.25 + t * 0.15
      gc.setFill(Color.color(r, g, b))
      gc.fillRect(0, h * t, w, h / bands + 1)
    }

    val px = camOffX * 0.02
    val py = camOffY * 0.015

    // Underwater caustic light patches
    for (i <- 0 until 18) {
      val cx = (hash(i * 5) * w * 1.3 - w * 0.15 + px * (0.3 + hash(i * 5 + 3))) % (w + 100) - 50
      val cy = hash(i * 5 + 1) * h
      val rad = 30 + hash(i * 5 + 2) * 60
      val pulse = 0.5 + 0.5 * Math.sin(tick * 0.02 + hash(i * 5 + 4) * Math.PI * 2)
      val alpha = 0.03 + 0.03 * pulse
      gc.setFill(Color.color(0.2, 0.6, 0.8, alpha))
      gc.fillOval(cx - rad, cy - rad, rad * 2, rad * 2)
    }

    // Wave layers (back to front)
    drawWaveLayer(gc, w, h, 0.15, tick, px,
      0.05, 0.18, 0.38, 0.15, 0.008, 8.0, 0)
    drawWaveLayer(gc, w, h, 0.30, tick, px,
      0.04, 0.15, 0.35, 0.12, 0.012, 6.0, 50)
    drawWaveLayer(gc, w, h, 0.50, tick, px,
      0.03, 0.12, 0.32, 0.10, 0.015, 5.0, 100)
    drawWaveLayer(gc, w, h, 0.65, tick, px,
      0.03, 0.10, 0.30, 0.10, 0.018, 4.5, 150)
    drawWaveLayer(gc, w, h, 0.80, tick, px,
      0.02, 0.08, 0.28, 0.08, 0.022, 4.0, 200)

    // Foam/whitecap highlights
    for (i <- 0 until 12) {
      val foamY = h * (0.2 + hash(i * 11) * 0.7)
      val foamX = (hash(i * 11 + 1) * w * 1.5 - w * 0.25 + tick * (0.2 + hash(i * 11 + 2) * 0.3) + px) % (w + 200) - 100
      val foamW = 20 + hash(i * 11 + 3) * 50
      val foamH = 2 + hash(i * 11 + 4) * 3
      val pulse = 0.5 + 0.5 * Math.sin(tick * 0.025 + hash(i * 11 + 5) * 10)
      gc.setFill(Color.color(0.6, 0.8, 0.9, 0.08 * pulse))
      gc.fillOval(foamX, foamY, foamW, foamH)
    }
  }

  private def drawWaveLayer(gc: GraphicsContext, w: Double, h: Double,
                            yFrac: Double, tick: Int, parallax: Double,
                            r: Double, g: Double, b: Double, alpha: Double,
                            speed: Double, amplitude: Double, seedOff: Int): Unit = {
    gc.setFill(Color.color(r, g, b, alpha))

    val baseY = h * yFrac
    val points = 60
    val xStep = (w + 40) / points
    val xPoints = new Array[Double](points + 3)
    val yPoints = new Array[Double](points + 3)

    for (i <- 0 to points) {
      val x = -20 + i * xStep + parallax * (0.3 + yFrac)
      val waveY = baseY +
        Math.sin(i * 0.12 + seedOff * 0.1 + tick * speed) * amplitude +
        Math.sin(i * 0.25 + seedOff * 0.3 + tick * speed * 1.3) * amplitude * 0.5 +
        Math.sin(i * 0.06 + seedOff * 0.7 + tick * speed * 0.7) * amplitude * 1.5
      xPoints(i) = x
      yPoints(i) = waveY
    }
    // Close polygon at bottom corners
    xPoints(points + 1) = w + 20
    yPoints(points + 1) = h + 10
    xPoints(points + 2) = -20
    yPoints(points + 2) = h + 10

    gc.fillPolygon(xPoints, yPoints, points + 3)
  }
}
