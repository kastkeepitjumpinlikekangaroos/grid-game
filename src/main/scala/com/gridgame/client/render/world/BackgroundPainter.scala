package com.gridgame.client.render
package world

import com.gridgame.common.model._

import org.lwjgl.opengl.GL11._

import com.gridgame.client.gl.{GLTexture, RenderQuality, ShapeBatch, TextureRegion}

/**
 * The sky, sea, city, desert, space or snowfield behind the map, by the world's `background`. It
 * only shows past the edge of the map, so it is drawn into a cached target of its own, redrawn every
 * few frames while it shows, and blitted behind the ground.
 */
private[render] final class BackgroundPainter(ctx: RenderContext) {
  import ctx._

  // Background FBO cache — re-render every N frames to amortize procedural generation cost
  private var bgCacheFBO: GLTexture = _
  private var bgCacheRegion: TextureRegion = _
  private var bgCacheValid = false
  private var bgCacheTick = 0
  private var bgCacheType: Byte = -1

  // Pre-allocated polygon scratch shared by the status-effect / buff renderers
  private val _fxXs = new Array[Float](8)
  private val _fxYs = new Array[Float](8)

  // Pre-allocated arrays for dune/wave background layers (avoids per-call allocation): a ribbon of
  // the ridge's 61 points left to right, then the bottom of the screen right to left
  private val _bgPolyXs = new Array[Float](122)
  private val _bgPolyYs = new Array[Float](122)

  private def hash(seed: Int): Double = {
    val x = Math.sin(seed.toDouble * 127.1 + 311.7) * 43758.5453
    x - x.floor
  }

  private def renderBackground(background: String): Unit = {
    (bgType: @scala.annotation.switch) match {
      case 0 => drawSkyBg()
      case 1 => drawCityscapeBg()
      case 2 => drawSpaceBg()
      case 3 => drawDesertBg()
      case 4 => drawOceanBg()
      case 5 => drawSnowBg()
      case 6 => drawSeaBg()
      case _ => drawSkyBg()
    }
  }

  // Ridges of hills for the bright backgrounds, as a ribbon: the ridge left to right, then the
  // bottom of the screen right to left (ShapeBatch.fillRibbon). A heightfield polygon fanned from
  // one corner (fillPolygon) mis-triangulates wherever a slope faces away from that corner.
  private val HILL_PTS = 41
  private val _hillXs = new Array[Float](HILL_PTS * 2)
  private val _hillYs = new Array[Float](HILL_PTS * 2)

  /** A vertical gradient over the whole screen, in one quad the GPU interpolates. It was drawn as
    * fourteen flat bands, and the steps between them showed as stripes across the sky. */
  private def skyGradient(w: Float, h: Float, r0: Float, g0: Float, b0: Float, r1: Float, g1: Float, b1: Float): Unit =
    verticalGradient(0f, h, w, r0, g0, b0, r1, g1, b1)

  private def verticalGradient(y0: Float, y1: Float, w: Float,
                               r0: Float, g0: Float, b0: Float, r1: Float, g1: Float, b1: Float): Unit =
    shapeBatch.fillRectGradient(0f, y0, w, y1 - y0, r0, g0, b0, 1f, r0, g0, b0, 1f, r1, g1, b1, 1f, r1, g1, b1, 1f)

  /** Height of a ridge at point i of HILL_PTS: round humps, as MapleStory draws its hills. */
  private def ridgeY(i: Int, baseY: Float, amp: Float, seed: Int, sharp: Boolean): Float = {
    val a = i * 0.34 + seed * 1.7
    val hump = if (sharp) Math.abs(Math.sin(a)) * 0.7 + Math.abs(Math.sin(a * 2.3 + 1.1)) * 0.3
               else 0.55 + 0.3 * Math.sin(a) + 0.15 * Math.sin(a * 2.7 + seed)
    (baseY - amp * hump).toFloat
  }

  /** One ridge of hills across the screen, with an outline along its top. `lift` raises the
    * outline pass above the fill, 0 for none. */
  private def drawHills(w: Float, h: Float, yFrac: Float, ampFrac: Float, parallax: Float, seed: Int,
                        r: Float, g: Float, b: Float, lr: Float, lg: Float, lb: Float, lift: Float,
                        sharp: Boolean = false): Unit = {
    val step = (w + 80f) / (HILL_PTS - 1)
    val shift = ((parallax % step) + step) % step
    val baseY = h * yFrac; val amp = h * ampFrac
    val first = Math.floor(parallax / step).toInt
    var pass = if (lift > 0f) 0 else 1
    while (pass < 2) {
      val up = if (pass == 0) lift else 0f
      var i = 0
      while (i < HILL_PTS) {
        _hillXs(i) = -40f + i * step - shift
        _hillYs(i) = ridgeY(i + first, baseY, amp, seed, sharp) - up
        _hillXs(HILL_PTS * 2 - 1 - i) = _hillXs(i)
        _hillYs(HILL_PTS * 2 - 1 - i) = h + 4f
        i += 1
      }
      if (pass == 0) shapeBatch.fillRibbon(_hillXs, _hillYs, HILL_PTS * 2, lr, lg, lb, 1f)
      else shapeBatch.fillRibbon(_hillXs, _hillYs, HILL_PTS * 2, r, g, b, 1f)
      pass += 1
    }
  }

  /** A fat round tree standing on ridge point i, for the hills behind the meadow. */
  private def drawHillTree(w: Float, h: Float, yFrac: Float, ampFrac: Float, parallax: Float, seed: Int,
                           every: Int, size: Float): Unit = {
    val step = (w + 80f) / (HILL_PTS - 1)
    val shift = ((parallax % step) + step) % step
    val first = Math.floor(parallax / step).toInt
    var i = 1
    while (i < HILL_PTS - 1) {
      if (((i + first) % every + every) % every == 0) {
        val x = -40f + i * step - shift
        val y = ridgeY(i + first, h * yFrac, h * ampFrac, seed, sharp = false) + size * 0.4f
        shapeBatch.fillRect(x - size * 0.12f, y - size * 0.9f, size * 0.24f, size * 0.9f, 0.45f, 0.30f, 0.20f, 1f)
        shapeBatch.fillOval(x, y - size * 1.25f, size * 0.78f, size * 0.7f, 0.22f, 0.45f, 0.26f, 1f, 16)
        shapeBatch.fillOval(x, y - size * 1.25f, size * 0.68f, size * 0.6f, 0.38f, 0.70f, 0.34f, 1f, 16)
        shapeBatch.fillOval(x - size * 0.16f, y - size * 1.42f, size * 0.36f, size * 0.3f, 0.50f, 0.80f, 0.42f, 1f, 12)
      }
      i += 1
    }
  }

  /** A pine standing on ridge point i, for the hills behind the snowfield: three tiers, snow on each. */
  private def drawHillPine(w: Float, h: Float, yFrac: Float, ampFrac: Float, parallax: Float, seed: Int,
                           every: Int, size: Float): Unit = {
    val step = (w + 80f) / (HILL_PTS - 1)
    val shift = ((parallax % step) + step) % step
    val first = Math.floor(parallax / step).toInt
    var i = 1
    while (i < HILL_PTS - 1) {
      if (((i + first) % every + every) % every == 0) {
        val x = -40f + i * step - shift
        val y = ridgeY(i + first, h * yFrac, h * ampFrac, seed, sharp = false) + size * 0.3f
        var k = 0
        while (k < 3) {
          val base = y - k * size * 0.55f
          val half = size * (0.62f - k * 0.14f)
          _fxXs(0) = x;        _fxYs(0) = base - size * 0.8f
          _fxXs(1) = x + half; _fxYs(1) = base
          _fxXs(2) = x - half; _fxYs(2) = base
          shapeBatch.fillPolygon(_fxXs, _fxYs, 3, 0.30f, 0.50f, 0.55f, 1f)
          _fxXs(1) = x + half * 0.45f; _fxYs(1) = base - size * 0.45f
          _fxXs(2) = x - half * 0.45f; _fxYs(2) = base - size * 0.45f
          shapeBatch.fillPolygon(_fxXs, _fxYs, 3, 0.93f, 0.96f, 1f, 1f)
          k += 1
        }
      }
      i += 1
    }
  }

  // A cloud's lobes: offset from its centre and radius, in multiples of its size
  private val CLOUD_LOBES = 5
  private val _cloudOX = Array(-0.95f, -0.38f, 0.30f, 0.92f, 0.0f)
  private val _cloudOY = Array(0.12f, -0.22f, -0.30f, 0.08f, 0.18f)
  private val _cloudR = Array(0.52f, 0.72f, 0.68f, 0.50f, 0.80f)

  /** A MapleStory cloud: fat round lobes, a pale shade on its underside, a blue outline. */
  private def drawCelCloud(cx: Float, cy: Float, s: Float, lr: Float, lg: Float, lb: Float,
                           sr: Float, sg: Float, sb: Float, br: Float, bg: Float, bb: Float): Unit = {
    var pass = 0
    while (pass < 3) {
      var k = 0
      while (k < CLOUD_LOBES) {
        val x = cx + _cloudOX(k) * s; val y = cy + _cloudOY(k) * s; val rr = _cloudR(k) * s
        pass match {
          case 0 => shapeBatch.fillOval(x, y, rr + 1.6f, rr * 0.82f + 1.6f, lr, lg, lb, 1f, 20)
          case 1 => shapeBatch.fillOval(x, y, rr, rr * 0.82f, sr, sg, sb, 1f, 20)
          case _ => shapeBatch.fillOval(x - rr * 0.1f, y - rr * 0.16f, rr * 0.9f, rr * 0.74f, br, bg, bb, 1f, 20)
        }
        k += 1
      }
      pass += 1
    }
  }

  /** Clouds drifting across a band of the sky, `count` of them, wrapping round the screen. */
  private def drawCloudBand(w: Float, h: Float, yFrac: Float, speed: Float, parallax: Float, scale: Float,
                            count: Int, seed: Int, snowy: Boolean): Unit = {
    val span = w + 240f
    var i = 0
    while (i < count) {
      val baseX = hash(seed + i * 17).toFloat * span
      val x = (((baseX + animationTick * speed + parallax) % span) + span) % span - 120f
      val y = h * yFrac + (hash(seed + i * 17 + 5).toFloat - 0.5f) * h * 0.12f
      val s = scale * (0.75f + hash(seed + i * 17 + 9).toFloat * 0.5f)
      if (snowy) drawCelCloud(x, y, s, 0.62f, 0.68f, 0.84f, 0.86f, 0.89f, 0.96f, 0.97f, 0.98f, 1f)
      else drawCelCloud(x, y, s, 0.50f, 0.68f, 0.92f, 0.82f, 0.90f, 1f, 1f, 1f, 1f)
      i += 1
    }
  }

  /** MapleStory's sky: bright blue paling toward the horizon, fat outlined clouds, and rolling
    * green hills with round trees on them. Behind the Meadow, and any map that names no background. */
  private def drawSkyBg(): Unit = {
    val w = canvasW.toFloat; val h = canvasH.toFloat
    skyGradient(w, h, 0.38f, 0.68f, 1f, 0.80f, 0.93f, 1f)
    val px = (camOffX * 0.02).toFloat
    drawCloudBand(w, h, 0.16f, 0.05f, px * 0.3f, 26f, 4, 311, snowy = false)
    // a far range, blue with distance, then nearer green hills with trees along them
    drawHills(w, h, 0.64f, 0.10f, px * 0.5f, 7, 0.60f, 0.78f, 0.90f, 0f, 0f, 0f, 0f, sharp = true)
    drawHillTree(w, h, 0.76f, 0.06f, px, 3, 4, 11f)
    drawHills(w, h, 0.76f, 0.06f, px, 3, 0.52f, 0.80f, 0.42f, 0.30f, 0.56f, 0.30f, 1.5f)
    drawHills(w, h, 0.88f, 0.05f, px * 1.6f, 13, 0.46f, 0.74f, 0.36f, 0.27f, 0.50f, 0.26f, 1.5f)
    drawCloudBand(w, h, 0.34f, 0.11f, px * 0.6f, 20f, 3, 733, snowy = false)
  }

  /** Open sea to a far horizon under MapleStory's sky, with a couple of islands on it, for the
    * Lagoon: its sea runs to the edge of the world, and the sky's green hills beyond that read as
    * the sea stopping at a field. The water below the horizon matches the deep water tiles. */
  private def drawSeaBg(): Unit = {
    val w = canvasW.toFloat; val h = canvasH.toFloat
    val horizon = h * 0.36f
    // sky, down to the horizon
    verticalGradient(0f, horizon, w, 0.38f, 0.68f, 1f, 0.82f, 0.93f, 1f)
    var i = 0
    val px = (camOffX * 0.02).toFloat
    drawCloudBand(w, h, 0.12f, 0.05f, px * 0.3f, 22f, 4, 919, snowy = false)
    // sea, pale at the horizon and deepening toward the viewer
    verticalGradient(horizon, h, w, 0.48f, 0.76f, 0.94f, 0.18f, 0.50f, 0.84f)
    // two islands on the horizon: a green hump with a palm on it
    var k = 0
    while (k < 2) {
      val span = w + 400f
      val ix = (((hash(907 + k * 31).toFloat * span + px * 0.4f) % span) + span) % span - 200f
      val iw = 46f + k * 30f
      shapeBatch.fillOval(ix, horizon + 1f, iw + 1.5f, 11f + k * 4f + 1.5f, 0.28f, 0.52f, 0.30f, 1f, 24)
      shapeBatch.fillOval(ix, horizon + 1f, iw, 11f + k * 4f, 0.46f, 0.76f, 0.38f, 1f, 24)
      shapeBatch.fillRect(ix - iw - 2f, horizon, iw * 2f + 4f, 16f, 0.46f, 0.74f, 0.92f, 1f)
      shapeBatch.fillOval(ix, horizon + 1.5f, iw, 3f, 0.95f, 0.90f, 0.72f, 1f, 20)
      shapeBatch.strokeLine(ix + 4f, horizon - 9f - k * 3f, ix + 8f, horizon - 22f - k * 3f, 1.6f, 0.52f, 0.36f, 0.24f, 1f)
      shapeBatch.fillOval(ix + 8f, horizon - 23f - k * 3f, 7f, 3f, 0.30f, 0.62f, 0.32f, 1f, 12)
      k += 1
    }
    // light on the water: short strokes drifting along, thicker toward the viewer
    i = 0
    while (i < 36) {
      val t = hash(1301 + i * 7).toFloat
      val y = horizon + 6f + (h - horizon - 6f) * t * t
      val len = 5f + t * 14f
      val span = w + 60f
      val x = (((hash(1302 + i * 7).toFloat * span + animationTick * (0.05f + t * 0.12f) + px * (0.5f + t)) % span) + span) % span - 30f
      val a = 0.35f + 0.35f * Math.sin(animationTick * 0.03 + i).toFloat
      shapeBatch.strokeLine(x, y, x + len, y, 1f + t * 1.4f, 0.85f, 0.95f, 1f, a)
      i += 1
    }
  }

  /** A winter sky over snowy hills with firs on them, for the Snowglobe. Snow falls in front of
    * the world as well (spawnSnowParticle). */
  private def drawSnowBg(): Unit = {
    val w = canvasW.toFloat; val h = canvasH.toFloat
    skyGradient(w, h, 0.58f, 0.70f, 0.93f, 0.90f, 0.94f, 1f)
    val px = (camOffX * 0.02).toFloat
    drawCloudBand(w, h, 0.18f, 0.04f, px * 0.3f, 24f, 4, 511, snowy = true)
    drawHills(w, h, 0.62f, 0.14f, px * 0.5f, 5, 0.80f, 0.86f, 0.97f, 0.62f, 0.70f, 0.88f, 1.2f, sharp = true)
    drawHillPine(w, h, 0.76f, 0.06f, px, 9, 3, 13f)
    drawHills(w, h, 0.76f, 0.06f, px, 9, 0.95f, 0.97f, 1f, 0.66f, 0.74f, 0.90f, 1.5f)
    drawHills(w, h, 0.88f, 0.05f, px * 1.6f, 17, 0.90f, 0.93f, 0.99f, 0.64f, 0.72f, 0.88f, 1.5f)
  }

  private def drawCityscapeBg(): Unit = {
    val w = canvasW.toFloat; val h = canvasH.toFloat
    // Dark gradient sky
    verticalGradient(0f, h, w, 0.05f, 0.02f, 0.15f, 0.13f, 0.07f, 0.10f)
    var i = 0
    // Horizon glow
    shapeBatch.fillRect(0, h * 0.55f, w, h * 0.15f, 0.2f, 0.08f, 0.3f, 0.3f)
    shapeBatch.fillRect(0, h * 0.5f, w, h * 0.1f, 0.3f, 0.1f, 0.4f, 0.15f)

    val px = (camOffX * 0.02).toFloat
    // Buildings
    i = 0
    while (i < 30) {
      val bx = ((hash(i * 7).toFloat * (w + 200) - 100 + px * 0.5f) % (w + 200)) - 100
      val bw = 25 + hash(i * 7 + 1).toFloat * 45
      val bh = h * (0.12f + hash(i * 7 + 2).toFloat * 0.30f)
      val by = h - bh
      shapeBatch.fillRect(bx, by, bw, bh, 0.08f, 0.06f, 0.16f, 0.85f)
      // Windows
      var wy = by + 5
      var windowIdx = 0
      while (wy < h - 8) {
        var wx = bx + 4
        while (wx < bx + bw - 5) {
          val windowSeed = i * 1000 + windowIdx
          val isLit = hash(windowSeed) > 0.4
          if (isLit) {
            val flicker = if (hash(windowSeed + 500) > 0.7)
              (0.6 + 0.4 * Math.sin(animationTick * 0.05 + hash(windowSeed + 300) * 20)).toFloat
            else 1f
            val warmth = hash(windowSeed + 100)
            var wr = 0f; var wg = 0f; var wb = 0f
            if (warmth < 0.6) { wr = 1f; wg = 0.85f; wb = 0.3f }
            else if (warmth < 0.85) { wr = 0.8f; wg = 0.85f; wb = 1f }
            else if (hash(windowSeed + 200) < 0.5) { wr = 0f; wg = 0.9f; wb = 1f }
            else { wr = 1f; wg = 0.2f; wb = 0.8f }
            shapeBatch.fillRect(wx, wy, 3f, 4f, wr, wg, wb, 0.7f * flicker)
          }
          wx += 7
          windowIdx += 1
        }
        wy += 8
      }
      i += 1
    }
    // Near buildings
    i = 0
    while (i < 10) {
      val bx = ((hash(i * 13 + 200).toFloat * (w + 300) - 150 + px) % (w + 300)) - 150
      val bw = 50 + hash(i * 13 + 201).toFloat * 60
      val bh = h * (0.08f + hash(i * 13 + 202).toFloat * 0.15f)
      shapeBatch.fillRect(bx, h - bh, bw, bh, 0.03f, 0.02f, 0.06f, 0.95f)
      i += 1
    }
    // Near foreground atmospheric overlay (0.15x parallax, very subtle)
    val nearAlpha = 0.04f
    shapeBatch.fillRect(0, h * 0.85f, w, h * 0.15f, 0.05f, 0.02f, 0.12f, nearAlpha)
  }

  private def drawSpaceBg(): Unit = {
    val w = canvasW.toFloat; val h = canvasH.toFloat
    verticalGradient(0f, h, w, 0.006f, 0.004f, 0.02f, 0.018f, 0.012f, 0.06f)
    var i = 0
    val px = (camOffX * 0.01).toFloat; val py = (camOffY * 0.01).toFloat

    // Nebulae
    drawNebula(w * 0.3f + px * 2, h * 0.35f + py * 2, w * 0.35f, h * 0.3f, 0.4f, 0.1f, 0.6f,
      (0.06 + 0.02 * Math.sin(animationTick * 0.008)).toFloat)
    drawNebula(w * 0.75f + px * 1.5f, h * 0.6f + py * 1.5f, w * 0.25f, h * 0.2f, 0.1f, 0.3f, 0.5f,
      (0.04 + 0.015 * Math.sin(animationTick * 0.01 + 2)).toFloat)

    // Stars with additive blending for glow
    shapeBatch.setAdditiveBlend(true)
    i = 0
    while (i < 120) {
      val sx = ((hash(i * 3).toFloat * w + px * (0.5f + hash(i * 3 + 10).toFloat * 2)) % w)
      val sy = ((hash(i * 3 + 1).toFloat * h + py * (0.5f + hash(i * 3 + 11).toFloat * 2)) % h)
      val baseBrightness = 0.3f + hash(i * 3 + 2).toFloat * 0.7f
      val twinkleSpeed = 0.02f + hash(i * 3 + 5).toFloat * 0.04f
      val twinklePhase = hash(i * 3 + 6).toFloat * Math.PI.toFloat * 2
      val twinkle = (0.5 + 0.5 * Math.sin(animationTick * twinkleSpeed + twinklePhase)).toFloat
      val brightness = baseBrightness * (0.4f + 0.6f * twinkle)
      val colorSeed = hash(i * 3 + 7)
      var sr = 0f; var sg = 0f; var sb = 0f
      if (colorSeed < 0.6) { sr = 1f; sg = 1f; sb = 1f }
      else if (colorSeed < 0.8) { sr = 0.7f; sg = 0.8f; sb = 1f }
      else { sr = 1f; sg = 0.95f; sb = 0.7f }
      val size = 1f + hash(i * 3 + 8).toFloat * 2f
      // Star core — use fillDot for small stars (6 verts vs 60)
      if (size <= 2f) shapeBatch.fillDot(sx, sy, size, sr, sg, sb, brightness)
      else shapeBatch.fillOval(sx, sy, size, size, sr, sg, sb, brightness, 8)
      // Glow halo (additive)
      if (baseBrightness > 0.6f) {
        shapeBatch.fillOvalSoft(sx, sy, size * 3, size * 3, sr, sg, sb, brightness * 0.3f, 0f, 12)
      }
      // Cross glint for bright stars
      if (baseBrightness > 0.8f && twinkle > 0.7f) {
        val glintLen = size * 2.5f * twinkle
        shapeBatch.strokeLine(sx - glintLen, sy, sx + glintLen, sy, 0.5f, sr, sg, sb, brightness * 0.4f)
        shapeBatch.strokeLine(sx, sy - glintLen, sx, sy + glintLen, 0.5f, sr, sg, sb, brightness * 0.4f)
      }
      i += 1
    }
    shapeBatch.setAdditiveBlend(false)

    // Moon
    val moonX = w * 0.8f + px * 3; val moonY = h * 0.2f + py * 3; val moonR = 25f
    shapeBatch.fillOval(moonX, moonY, moonR, moonR, 0.25f, 0.22f, 0.35f, 0.6f)
    shapeBatch.fillOval(moonX + moonR * 0.4f, moonY, moonR, moonR, 0.02f, 0.01f, 0.05f, 0.7f)
  }

  private def drawNebula(cx: Float, cy: Float, rw: Float, rh: Float, nr: Float, ng: Float, nb: Float, alpha: Float): Unit = {
    shapeBatch.setAdditiveBlend(true)
    var i = 5
    while (i >= 1) {
      val t = i.toFloat / 5
      shapeBatch.fillOvalSoft(cx, cy, rw * t * 0.5f, rh * t * 0.5f, nr, ng, nb, alpha * t * 0.6f, 0f, 16)
      i -= 1
    }
    shapeBatch.setAdditiveBlend(false)
  }

  private def drawDesertBg(): Unit = {
    val w = canvasW.toFloat; val h = canvasH.toFloat
    verticalGradient(0f, h, w, 0.95f, 0.65f, 0.25f, 0.70f, 0.45f, 0.15f)
    var i = 0
    val px = (camOffX * 0.02).toFloat
    // Sun with glow
    val sunX = w * 0.75f + px * 0.5f; val sunY = h * 0.18f; val sunR = 35f
    val pulse = (0.9 + 0.1 * Math.sin(animationTick * 0.015)).toFloat
    shapeBatch.setAdditiveBlend(true)
    shapeBatch.fillOvalSoft(sunX, sunY, sunR * 5, sunR * 5, 1f, 0.9f, 0.5f, 0.04f * pulse, 0f, 24)
    shapeBatch.fillOvalSoft(sunX, sunY, sunR * 3, sunR * 3, 1f, 0.85f, 0.4f, 0.08f * pulse, 0f, 24)
    shapeBatch.fillOvalSoft(sunX, sunY, sunR * 1.8f, sunR * 1.8f, 1f, 0.8f, 0.3f, 0.15f * pulse, 0f, 24)
    shapeBatch.setAdditiveBlend(false)
    shapeBatch.fillOval(sunX, sunY, sunR, sunR, 1f, 0.95f, 0.7f, 0.9f)
    // Heat shimmer
    i = 0
    while (i < 15) {
      val ly = h * 0.45f + i * h * 0.035f
      val phase = animationTick * 0.03f + i * 0.7f
      val points = 30
      val xStep = w / points
      var j = 0
      while (j < points) {
        val x1 = j * xStep; val x2 = (j + 1) * xStep
        val y1 = ly + Math.sin(phase + j * 0.3).toFloat * 2.5f
        val y2 = ly + Math.sin(phase + (j + 1) * 0.3).toFloat * 2.5f
        shapeBatch.strokeLine(x1, y1, x2, y2, 1.5f, 1f, 0.9f, 0.6f, 0.06f)
        j += 1
      }
      i += 1
    }
    // Sand dunes
    drawDuneLayer(w, h, 0.70f, 0.65f, 0.45f, 0.20f, 0.7f, px * 0.3f, 0)
    drawDuneLayer(w, h, 0.60f, 0.50f, 0.30f, 0.12f, 0.85f, px * 0.6f, 100)
    // Near atmospheric haze (warm dust overlay)
    shapeBatch.fillRect(0, h * 0.75f, w, h * 0.25f, 0.85f, 0.7f, 0.45f, 0.04f)
  }

  private def drawDuneLayer(w: Float, h: Float, r: Float, g: Float, b: Float, alpha: Float,
                            yFrac: Float, parallax: Float, seedOff: Int): Unit = {
    val baseY = h * yFrac
    val points = 60
    val xStep = (w + 40) / points
    val n = (points + 1) * 2
    var i = 0
    while (i <= points) {
      val x = -20 + i * xStep + parallax
      _bgPolyXs(i) = x
      _bgPolyYs(i) = (baseY +
        Math.sin(i * 0.15 + seedOff * 0.1) * h * 0.04 +
        Math.sin(i * 0.07 + seedOff * 0.3 + animationTick * 0.002) * h * 0.02 +
        Math.sin(i * 0.3 + seedOff * 0.5) * h * 0.015).toFloat
      _bgPolyXs(n - 1 - i) = x; _bgPolyYs(n - 1 - i) = h + 10
      i += 1
    }
    // A ribbon, not a polygon fanned from a corner, which overlapped itself wherever a slope faced
    // away from that corner and blended the overlap twice (ShapeBatch.fillRibbon)
    shapeBatch.fillRibbon(_bgPolyXs, _bgPolyYs, n, r, g, b, alpha)
  }

  private def drawOceanBg(): Unit = {
    val w = canvasW.toFloat; val h = canvasH.toFloat
    verticalGradient(0f, h, w, 0.02f, 0.08f, 0.25f, 0.06f, 0.20f, 0.40f)
    var i = 0
    val px = (camOffX * 0.02).toFloat
    // Caustics
    shapeBatch.setAdditiveBlend(true)
    i = 0
    while (i < 18) {
      val cx = ((hash(i * 5).toFloat * w * 1.3f - w * 0.15f + px * (0.3f + hash(i * 5 + 3).toFloat)) % (w + 100)) - 50
      val cy = hash(i * 5 + 1).toFloat * h
      val rad = 30 + hash(i * 5 + 2).toFloat * 60
      val pulse = (0.5 + 0.5 * Math.sin(animationTick * 0.02 + hash(i * 5 + 4) * Math.PI * 2)).toFloat
      shapeBatch.fillOvalSoft(cx, cy, rad, rad, 0.2f, 0.6f, 0.8f, 0.03f + 0.03f * pulse, 0f, 16)
      i += 1
    }
    shapeBatch.setAdditiveBlend(false)
    // Wave layers
    drawWaveLayer(w, h, 0.15f, px, 0.05f, 0.18f, 0.38f, 0.15f, 0.008f, 8f, 0)
    drawWaveLayer(w, h, 0.30f, px, 0.04f, 0.15f, 0.35f, 0.12f, 0.012f, 6f, 50)
    drawWaveLayer(w, h, 0.50f, px, 0.03f, 0.12f, 0.32f, 0.10f, 0.015f, 5f, 100)
    drawWaveLayer(w, h, 0.65f, px, 0.03f, 0.10f, 0.30f, 0.10f, 0.018f, 4.5f, 150)
    drawWaveLayer(w, h, 0.80f, px, 0.02f, 0.08f, 0.28f, 0.08f, 0.022f, 4f, 200)
    // Foam
    shapeBatch.setAdditiveBlend(true)
    i = 0
    while (i < 12) {
      val foamY = h * (0.2f + hash(i * 11).toFloat * 0.7f)
      val foamX = ((hash(i * 11 + 1).toFloat * w * 1.5f - w * 0.25f + animationTick * (0.2f + hash(i * 11 + 2).toFloat * 0.3f) + px) % (w + 200)) - 100
      val foamW = 20 + hash(i * 11 + 3).toFloat * 50
      val foamH = 2 + hash(i * 11 + 4).toFloat * 3
      val pulse = (0.5 + 0.5 * Math.sin(animationTick * 0.025 + hash(i * 11 + 5) * 10)).toFloat
      shapeBatch.fillOval(foamX + foamW / 2, foamY + foamH / 2, foamW / 2, foamH / 2, 0.6f, 0.8f, 0.9f, 0.08f * pulse)
      i += 1
    }
    shapeBatch.setAdditiveBlend(false)
    // Near surface light rays (0.15x parallax)
    val nearPx = (camOffX * 0.15).toFloat
    shapeBatch.setAdditiveBlend(true)
    var ri = 0
    while (ri < 5) {
      val rx = ((hash(ri * 11 + 400).toFloat * w + nearPx * 0.5f) % (w + 100)) - 50
      val rayW = 30f + hash(ri * 11 + 401).toFloat * 50f
      val rayA = 0.03f + 0.02f * Math.sin(animationTick * 0.015 + ri * 1.5).toFloat
      shapeBatch.fillRectGradient(rx, 0, rayW, h,
        0.3f, 0.6f, 0.8f, rayA, 0.3f, 0.6f, 0.8f, rayA,
        0.2f, 0.5f, 0.7f, 0f, 0.2f, 0.5f, 0.7f, 0f)
      ri += 1
    }
    shapeBatch.setAdditiveBlend(false)
  }

  private def drawWaveLayer(w: Float, h: Float, yFrac: Float, parallax: Float,
                            r: Float, g: Float, b: Float, alpha: Float,
                            speed: Float, amplitude: Float, seedOff: Int): Unit = {
    val baseY = h * yFrac
    val points = 60
    val xStep = (w + 40) / points
    val n = (points + 1) * 2
    var i = 0
    while (i <= points) {
      val x = -20 + i * xStep + parallax * (0.3f + yFrac)
      _bgPolyXs(i) = x
      _bgPolyYs(i) = (baseY +
        Math.sin(i * 0.12 + seedOff * 0.1 + animationTick * speed) * amplitude +
        Math.sin(i * 0.25 + seedOff * 0.3 + animationTick * speed * 1.3) * amplitude * 0.5 +
        Math.sin(i * 0.06 + seedOff * 0.7 + animationTick * speed * 0.7) * amplitude * 1.5).toFloat
      _bgPolyXs(n - 1 - i) = x; _bgPolyYs(n - 1 - i) = h + 10
      i += 1
    }
    shapeBatch.fillRibbon(_bgPolyXs, _bgPolyYs, n, r, g, b, alpha)
  }

  def create(width: Int, height: Int): Unit = {
    bgCacheFBO = GLTexture.createFBO(width, height)
    bgCacheRegion = flippedRegion(bgCacheFBO)
  }

  def resize(width: Int, height: Int): Unit = {
    bgCacheFBO = GLTexture.resizeFBO(bgCacheFBO, width, height)
    bgCacheRegion = flippedRegion(bgCacheFBO)
    bgCacheValid = false
  }

  /** Draw the background into its cached target again if it shows and has gone stale: its
    * animation moves on every few frames (RenderQuality.bgCacheInterval). */
  def refresh(world: WorldData, visible: Boolean): Unit = {
    if (!visible) bgCacheValid = false // stale by the time it next shows
    val bgStale = !bgCacheValid || bgType != bgCacheType ||
      (animationTick - bgCacheTick) >= RenderQuality.bgCacheInterval
    if (visible && bgStale) {
      bgCacheFBO.bindAsTarget()  // sets viewport to FBO dimensions
      glClearColor(0f, 0f, 0f, 1f)
      glClear(GL_COLOR_BUFFER_BIT)
      shapeBatch.begin(projection)
      renderBackground(world.background)
      shapeBatch.end()
      bgCacheFBO.unbindTarget()
      bgCacheValid = true; bgCacheTick = animationTick; bgCacheType = bgType
    }
  }

  /** The cached background, over the whole screen. */
  def blit(): Unit = {
    beginSprites()
    spriteBatch.draw(bgCacheRegion, 0f, 0f, canvasW.toFloat, canvasH.toFloat)
  }

  def dispose(): Unit = if (bgCacheFBO != null) bgCacheFBO.dispose()
}
