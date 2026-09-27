package com.gridgame.client.render
package world

/**
 * What is on a player, drawn with them in the depth pass: a shield bubble, frost, stun stars, poison,
 * roots, a slow, a speed boost. The glowing effects are GlowEffectPainter's.
 */
private[render] final class StatusEffectPainter(ctx: RenderContext) {
  import ctx._

  private val _indicatorXs = new Array[Float](4)
  private val _indicatorYs = new Array[Float](4)

  // Pre-allocated polygon scratch shared by the status-effect / buff renderers
  private val _fxXs = new Array[Float](8)
  private val _fxYs = new Array[Float](8)

  // Screen-space facing vector per Direction.id (Down, Up, Left, Right), i.e. the world
  // step run through the isometric mapping ((dx - dy), (dx + dy) / 2) and normalised.
  private val _dirSX = Array(-0.894f, 0.894f, -0.894f, 0.894f)
  private val _dirSY = Array(0.447f, -0.447f, -0.447f, 0.447f)

  // Pre-allocated arrays for item shapes and the stun's stars: ten vertices, an outer and an
  // inner radius alternating. Each is built and drawn before the next one touches them.
  private val _starXs = new Array[Float](10)
  private val _starYs = new Array[Float](10)

  /**
   * Shield — a faceted force field turning around the player: three longitude ribs that
   * squeeze as they rotate (so the shell reads as a sphere, not a circle), two latitude
   * rings, hex cells flickering across the surface, and a shockwave when damage lands on it.
   * Drawn behind the sprite, so it is deliberately rim-weighted rather than filled.
   */
  def drawShieldBubble(cx: Double, cy: Double, hitTime: Long): Unit = {
    beginShapes()
    val x = cx.toFloat; val y = cy.toFloat
    val t = animTickF
    val pulse = (0.7 + 0.3 * Math.sin(t * 0.1)).toFloat
    // Impact reaction — the shell swells and brightens for 320ms after being hit
    val impact = if (hitTime > 0) {
      val el = (frameTimeMs - hitTime).toFloat
      if (el < 320f) 1f - el / 320f else 0f
    } else 0f
    val rx = 23f + impact * 3f
    val ry = 19f + impact * 2f

    // Refractive body: nearly clear at the centre, brightening toward the rim
    shapeBatch.fillOvalSoft(x, y, rx, ry, 0.35f, 0.65f, 1f, 0.02f, 0.10f + impact * 0.14f, 20)
    // Energy pooling where the shell meets the ground
    shapeBatch.fillOvalSoft(x, y + ry * 0.72f, rx * 0.85f, ry * 0.30f, 0.4f, 0.75f, 1f, 0.16f * pulse, 0f, 12)

    // Longitude ribs — |cos(phase)| squeezes each ellipse toward edge-on as it turns
    val spin = t * 0.022f
    var i = 0
    while (i < 3) {
      val ph = spin + i * 1.0471976f
      val squeeze = Math.abs(Math.cos(ph)).toFloat
      val ribRx = squeeze * rx
      val a = (0.09f + 0.15f * (1f - squeeze)) * pulse + impact * 0.22f
      if (ribRx > 1.5f) shapeBatch.strokeOval(x, y, ribRx, ry, 1.2f, 0.55f, 0.85f, 1f, clamp(a), 12)
      i += 1
    }
    // Latitude rings
    shapeBatch.strokeOval(x, y - ry * 0.42f, rx * 0.72f, ry * 0.30f, 1f, 0.5f, 0.8f, 1f,
      clamp((0.10f + impact * 0.2f) * pulse), 12)
    shapeBatch.strokeOval(x, y + ry * 0.34f, rx * 0.86f, ry * 0.34f, 1f, 0.5f, 0.8f, 1f,
      clamp((0.09f + impact * 0.2f) * pulse), 12)

    // Hex cells lighting up across the surface
    i = 0
    while (i < 4) {
      val flick = Math.sin(t * 0.05f + i * 1.9f).toFloat
      if (flick > 0.55f) {
        val fa = (flick - 0.55f) / 0.45f
        val ang = i * 1.5707963f + t * 0.012f
        val fx = x + Math.cos(ang).toFloat * rx * 0.55f
        val fy = y + Math.sin(ang).toFloat * ry * 0.62f
        var v = 0
        while (v < 6) {
          val va = v * 1.0471976f + 0.3f
          _fxXs(v) = fx + Math.cos(va).toFloat * 5.5f
          _fxYs(v) = fy + Math.sin(va).toFloat * 4.4f
          v += 1
        }
        shapeBatch.fillPolygon(_fxXs, _fxYs, 6, 0.6f, 0.85f, 1f, 0.09f * fa)
        shapeBatch.strokePolygon(_fxXs, _fxYs, 6, 0.8f, 0.75f, 0.95f, 1f, 0.22f * fa)
      }
      i += 1
    }

    // Rim outline + a brighter arc on the key-light side (upper left)
    shapeBatch.strokeOval(x, y, rx, ry, 1.4f, 0.5f, 0.8f, 1f, clamp((0.20f + impact * 0.4f) * pulse), 20)
    shapeBatch.strokeArc(x, y, rx, ry, 3.4f, 1.5f, 2.2f, 0.85f, 0.95f, 1f,
      clamp((0.28f + impact * 0.35f) * pulse), 8)

    // Shockwave racing off the shell after a hit
    if (impact > 0f) {
      val ringT = 1f - impact
      shapeBatch.strokeOval(x, y, rx * (1f + ringT * 0.9f), ry * (1f + ringT * 0.9f),
        2.5f * impact, 0.8f, 0.92f, 1f, 0.5f * impact, 16)
    }
  }

  def drawFrozenEffect(cx: Double, cy: Double): Unit = {
    beginShapes()
    val x = cx.toFloat; val y = cy.toFloat
    val tick = animationTick

    // Large pulsing frost ground circle (35px radius, unmissable)
    val groundPulse = (0.7 + 0.3 * Math.sin(tick * 0.05)).toFloat
    shapeBatch.fillOvalSoft(x, y + 8f, 38f, 18f, 0.55f, 0.8f, 1f, 0.18f * groundPulse, 0f, 18)
    shapeBatch.fillOvalSoft(x, y + 8f, 30f, 14f, 0.65f, 0.88f, 1f, 0.22f * groundPulse, 0f, 16)
    shapeBatch.fillOval(x, y + 8f, 22f, 10f, 0.75f, 0.93f, 1f, 0.12f, 14)

    // 8 crystalline ice shards radiating inward — thick multi-layer strokes
    var i = 0
    while (i < 8) {
      val baseAngle = (i * Math.PI / 4.0 + tick * 0.006).toFloat
      val dist = 22f + Math.sin(tick * 0.04 + i * 0.9).toFloat * 3f
      val shardX = x + dist * Math.cos(baseAngle).toFloat
      val shardY = y + dist * Math.sin(baseAngle).toFloat * 0.55f
      val shardLen = 16f + Math.sin(tick * 0.03 + i * 1.7).toFloat * 2f
      val innerDist = 4f
      val innerX = x + innerDist * Math.cos(baseAngle).toFloat
      val innerY = y + innerDist * Math.sin(baseAngle).toFloat * 0.55f

      // Dark blue outline (3px thick)
      shapeBatch.strokeLine(shardX, shardY, innerX, innerY, 5f, 0.15f, 0.3f, 0.55f, 0.6f)
      // Ice blue body (2px)
      shapeBatch.strokeLine(shardX, shardY, innerX, innerY, 3.2f, 0.5f, 0.78f, 1f, 0.75f)
      // White highlight edge (0.8px offset)
      shapeBatch.strokeLine(shardX + 0.6f, shardY - 0.6f, innerX + 0.6f, innerY - 0.6f, 1.2f, 0.9f, 0.96f, 1f, 0.55f)

      // Crystal tip facet (larger, brighter)
      shapeBatch.fillOval(shardX, shardY, 4.5f, 3.8f, 0.6f, 0.85f, 1f, 0.6f, 6)

      // Icicle drip at shard tip (tiny elongated dot below tip)
      val dripPhase = (tick * 0.06f + i * 1.4f) % 6.28f
      val dripLen = 2.5f + Math.sin(dripPhase).toFloat * 1.5f
      val dripX = shardX + Math.cos(baseAngle).toFloat * 1.5f
      val dripY = shardY + Math.sin(baseAngle).toFloat * 0.55f + dripLen
      shapeBatch.strokeLineSoft(shardX, shardY, dripX, dripY + 2f, 1.2f, 0.7f, 0.9f, 1f, 0.35f)
      shapeBatch.fillOval(dripX, dripY + 2.5f, 1.5f, 2f, 0.8f, 0.95f, 1f, 0.5f, 4)

      // Sparkle on crystal tips (cycling which 4 flash)
      val sparkle = (0.2 + 0.8 * Math.sin(tick * 0.14 + i * 2.3)).toFloat
      if (sparkle > 0.55f) {
        val sparkAlpha = (sparkle - 0.55f) / 0.45f * 0.8f
        shapeBatch.fillOval(shardX, shardY, 3f, 3f, 1f, 1f, 1f, sparkAlpha, 4)
        // Cross sparkle lines
        shapeBatch.strokeLine(shardX - 3f, shardY, shardX + 3f, shardY, 0.8f, 1f, 1f, 1f, sparkAlpha * 0.6f)
        shapeBatch.strokeLine(shardX, shardY - 3f, shardX, shardY + 3f, 0.8f, 1f, 1f, 1f, sparkAlpha * 0.6f)
      }
      i += 1
    }

    // 8 frost particles drifting in slow orbit
    { var p = 0; while (p < 8) {
      val orbitAngle = tick * 0.018f + p * Math.PI.toFloat * 2f / 8f
      val orbitR = 26f + Math.sin(tick * 0.03 + p * 1.1).toFloat * 4f
      val px = x + orbitR * Math.cos(orbitAngle).toFloat
      val py = y + 4f + orbitR * Math.sin(orbitAngle).toFloat * 0.4f
      val pAlpha = (0.4 + 0.3 * Math.sin(tick * 0.06 + p * 1.9)).toFloat
      shapeBatch.fillOval(px, py, 2.5f, 2.5f, 0.8f, 0.95f, 1f, pAlpha, 6)
      // Tiny trail behind particle
      val prevAngle = orbitAngle - 0.3f
      val prevX = x + orbitR * Math.cos(prevAngle).toFloat
      val prevY = y + 4f + orbitR * Math.sin(prevAngle).toFloat * 0.4f
      shapeBatch.strokeLineSoft(prevX, prevY, px, py, 1.2f, 0.6f, 0.85f, 1f, pAlpha * 0.3f)
    ; p += 1 } }

    // Inner ice glow pulse (strong core glow)
    val pulse = (0.5 + 0.5 * Math.sin(tick * 0.07)).toFloat
    shapeBatch.fillOvalSoft(x, y, 14f, 11f, 0.6f, 0.88f, 1f, 0.25f * pulse, 0f, 14)
    shapeBatch.fillOval(x, y, 8f, 6f, 0.8f, 0.95f, 1f, 0.15f * pulse, 10)
  }

  /**
   * Stunned — the cartoon shorthand, stars going round the head. Drawn instead of the frost,
   * since a stun is a freeze as far as everything else is concerned: the ice would say the
   * wrong thing about how the player got held and how they will get out of it.
   */
  def drawStunnedEffect(cx: Double, cy: Double): Unit = {
    beginShapes()
    val x = cx.toFloat
    val y = (cy - 22).toFloat // just above the head
    val tick = animationTick

    // A warm glow behind them, so the stars hold up over pale sand as well as dark stone
    val pulse = (0.6 + 0.4 * Math.sin(tick * 0.09)).toFloat
    shapeBatch.fillOvalSoft(x, y, 22f, 10f, 1f, 0.9f, 0.35f, 0.13f * pulse, 0f, 14)

    // Four stars round a squashed ellipse: the far half of the orbit is smaller and dimmer,
    // which is what makes it read as going round the head rather than as a ring of stickers
    var i = 0
    while (i < 4) {
      val a = tick * 0.07f + i * (Math.PI.toFloat / 2f)
      val sx = x + Math.cos(a).toFloat * 17f
      val sy = y + Math.sin(a).toFloat * 6f
      val front = (Math.sin(a).toFloat + 1f) * 0.5f
      star5(sx, sy, 3.2f + front * 1.8f, a * 0.6f + i, 1f, 0.87f, 0.25f, 0.45f + front * 0.45f)
      i += 1
    }

    // A spark or two thrown off the ring
    var s = 0
    while (s < 3) {
      val ph = (tick * 0.05f + s * 0.33f) % 1f
      val sa = tick * 0.03f + s * 2.1f
      val sx = x + Math.cos(sa).toFloat * (10f + ph * 14f)
      val sy = y + Math.sin(sa).toFloat * (4f + ph * 5f) - ph * 4f
      shapeBatch.fillOval(sx, sy, 1.6f, 1.6f, 1f, 0.95f, 0.6f, (1f - ph) * 0.5f, 5)
      s += 1
    }
  }

  /** A five-pointed star: a radius per vertex, so it is filled from its own centre. Fanned from
    * vertex 0 (fillPolygon) a star bridges its own notches and comes out a lopsided blob. */
  private def star5(cx: Float, cy: Float, radius: Float, rotation: Float,
                    r: Float, g: Float, b: Float, a: Float): Unit = {
    var i = 0
    while (i < 10) {
      val ang = rotation + i * (Math.PI.toFloat / 5f) - Math.PI.toFloat / 2f
      val rad = if ((i & 1) == 0) radius else radius * 0.42f
      _starXs(i) = cx + Math.cos(ang).toFloat * rad
      _starYs(i) = cy + Math.sin(ang).toFloat * rad * 0.85f
      i += 1
    }
    shapeBatch.fillFan(cx, cy, _starXs, _starYs, 10, r, g, b, a)
    shapeBatch.strokePolygon(_starXs, _starYs, 10, 1f, 0.4f, 0.22f, 0.02f, a * 0.8f)
  }

  /**
   * Poisoned — acid bubbles boiling off them over a dark, murky pool, with a sickly wash over
   * the body. Green alone is no use: half the maps are grass, and a mid-green effect over a
   * green ground disappears. Every part of this pairs an acid highlight with a near-black rim
   * so it reads by value as well as by hue, over grass as much as over sand or stone. Kept off
   * the burn's additive pass: poison is meant to look sickly, not to glow.
   */
  def drawPoisonEffect(cx: Double, cy: Double): Unit = {
    beginShapes()
    val x = cx.toFloat; val y = cy.toFloat
    val tick = animationTick
    val pulse = (0.6 + 0.4 * Math.sin(tick * 0.06)).toFloat

    // A sick wash over the body, which is what darkens them against a green map
    shapeBatch.fillOvalSoft(x, y, 26f, 21f, 0.10f, 0.30f, 0.06f, 0.26f * pulse, 0f, 16)
    shapeBatch.fillOval(x, y + 2f, 16f, 14f, 0.08f, 0.24f, 0.05f, 0.16f, 14)

    // The pool at their feet: dark and murky, ringed in acid
    shapeBatch.fillOvalSoft(x, y + 10f, 27f, 12f, 0.06f, 0.17f, 0.04f, 0.38f * pulse, 0f, 16)
    shapeBatch.fillOval(x, y + 10f, 18f, 8f, 0.10f, 0.26f, 0.06f, 0.35f, 14)
    shapeBatch.strokeOval(x, y + 10f, 21f, 9f, 2.2f, 0.55f, 1f, 0.2f, 0.55f * pulse, 16)

    // Bubbles boiling off them, each rimmed in near-black so it stands off the sprite
    var i = 0
    while (i < 9) {
      val ph = (tick * 0.02f + i * 0.111f) % 1f
      val bx = x + Math.sin(tick * 0.035 + i * 1.9).toFloat * (11f - ph * 5f)
      val by = y + 9f - ph * 31f
      val sz = 2.2f + (1f - ph) * 3f
      val fade = if (ph > 0.82f) (1f - ph) / 0.18f else 1f
      shapeBatch.fillOval(bx, by, sz + 1.3f, sz * 0.9f + 1.3f, 0.04f, 0.13f, 0.03f, 0.55f * fade, 8)
      shapeBatch.fillOval(bx, by, sz, sz * 0.9f, 0.45f, 0.92f, 0.22f, 0.8f * fade, 8)
      // The catchlight that makes it a bubble and not a dot
      shapeBatch.fillOval(bx - sz * 0.32f, by - sz * 0.34f, sz * 0.32f, sz * 0.28f, 0.9f, 1f, 0.75f, 0.7f * fade, 5)
      // The burst it goes out on
      if (ph > 0.82f) shapeBatch.strokeOval(bx, by, sz * 2.2f, sz * 1.9f, 1.4f, 0.6f, 1f, 0.3f, 0.5f * fade, 10)
      i += 1
    }

    // Drips running off them into it
    var d = 0
    while (d < 3) {
      val ph = (tick * 0.035f + d * 0.33f) % 1f
      val dx0 = x + (d - 1) * 9f + Math.sin(tick * 0.03 + d).toFloat * 2f
      val dy0 = y - 5f + ph * 16f
      shapeBatch.strokeLineSoft(dx0, dy0 - 5f, dx0, dy0, 1.6f, 0.12f, 0.3f, 0.07f, 0.4f * (1f - ph))
      shapeBatch.fillOval(dx0, dy0, 2.2f, 3.4f, 0.05f, 0.15f, 0.03f, 0.55f * (1f - ph * 0.5f), 6)
      shapeBatch.fillOval(dx0, dy0, 1.4f, 2.4f, 0.45f, 0.92f, 0.22f, 0.7f * (1f - ph * 0.5f), 6)
      d += 1
    }
  }

  def drawRootedEffect(cx: Double, cy: Double): Unit = {
    beginShapes()
    val x = cx.toFloat; val y = cy.toFloat
    val tick = animationTick

    // Large dark brown disturbed earth oval (30px wide)
    shapeBatch.fillOvalSoft(x, y + 10f, 34f, 15f, 0.22f, 0.15f, 0.06f, 0.25f, 0f, 16)
    shapeBatch.fillOval(x, y + 10f, 28f, 12f, 0.3f, 0.22f, 0.1f, 0.2f, 14)
    shapeBatch.strokeOval(x, y + 10f, 30f, 13f, 2.5f, 0.2f, 0.14f, 0.05f, 0.45f, 14)

    // Ground cracks radiating from earth patch (6 crack lines)
    { var c = 0; while (c < 6) {
      val crackAngle = c * Math.PI.toFloat / 3f + 0.3f
      val crackLen = 14f + Math.sin(tick * 0.02 + c * 1.3).toFloat * 3f
      val crackX1 = x + 12f * Math.cos(crackAngle).toFloat
      val crackY1 = y + 10f + 5f * Math.sin(crackAngle).toFloat
      val crackX2 = x + (12f + crackLen) * Math.cos(crackAngle).toFloat
      val crackY2 = y + 10f + (5f + crackLen * 0.4f) * Math.sin(crackAngle).toFloat
      shapeBatch.strokeLine(crackX1, crackY1, crackX2, crackY2, 1.8f, 0.18f, 0.12f, 0.04f, 0.4f)
      // Branch crack
      val branchAngle = crackAngle + 0.5f
      val bx = crackX2 + 5f * Math.cos(branchAngle).toFloat
      val by = crackY2 + 2f * Math.sin(branchAngle).toFloat
      shapeBatch.strokeLine(crackX2, crackY2, bx, by, 1.2f, 0.18f, 0.12f, 0.04f, 0.3f)
    ; c += 1 } }

    // Pulsing green glow at base
    val basePulse = (0.5 + 0.5 * Math.sin(tick * 0.08)).toFloat
    shapeBatch.fillOvalSoft(x, y + 10f, 22f, 10f, 0.2f, 0.6f, 0.1f, 0.12f * basePulse, 0f, 12)

    // 6 thick vine tendrils climbing up with 4 curve segments each
    var i = 0
    while (i < 6) {
      val baseAngle = (i * Math.PI / 3.0 + tick * 0.012).toFloat
      val dist = 15f
      val rootBaseX = x + dist * Math.cos(baseAngle).toFloat
      val rootBaseY = y + 10f + dist * Math.sin(baseAngle).toFloat * 0.3f
      // Curved tendril climbing up (4 segments for more sinuous shape)
      val wave = Math.sin(tick * 0.04 + i * 1.7).toFloat * 4f
      val wave2 = Math.cos(tick * 0.035 + i * 2.1).toFloat * 2.5f
      val mid1X = rootBaseX + wave * 0.4f
      val mid1Y = rootBaseY - 7f
      val mid2X = rootBaseX - wave * 0.8f
      val mid2Y = rootBaseY - 15f
      val mid3X = rootBaseX + wave2
      val mid3Y = rootBaseY - 22f
      val tipX = rootBaseX + wave * 0.2f + Math.sin(tick * 0.06 + i * 2.3).toFloat * 3f
      val tipY = rootBaseY - 28f - Math.sin(tick * 0.03 + i).toFloat * 2f

      // Dark outline (5px thick) for each segment
      shapeBatch.strokeLine(rootBaseX, rootBaseY, mid1X, mid1Y, 5.5f, 0.1f, 0.06f, 0.01f, 0.65f)
      shapeBatch.strokeLine(mid1X, mid1Y, mid2X, mid2Y, 4.8f, 0.1f, 0.06f, 0.01f, 0.6f)
      shapeBatch.strokeLine(mid2X, mid2Y, mid3X, mid3Y, 3.8f, 0.1f, 0.06f, 0.01f, 0.55f)
      shapeBatch.strokeLine(mid3X, mid3Y, tipX, tipY, 3f, 0.1f, 0.06f, 0.01f, 0.45f)
      // Green body fill (3.5px)
      shapeBatch.strokeLine(rootBaseX, rootBaseY, mid1X, mid1Y, 3.8f, 0.28f, 0.58f, 0.14f, 0.75f)
      shapeBatch.strokeLine(mid1X, mid1Y, mid2X, mid2Y, 3.2f, 0.32f, 0.62f, 0.16f, 0.7f)
      shapeBatch.strokeLine(mid2X, mid2Y, mid3X, mid3Y, 2.6f, 0.36f, 0.66f, 0.18f, 0.6f)
      shapeBatch.strokeLine(mid3X, mid3Y, tipX, tipY, 2f, 0.4f, 0.7f, 0.2f, 0.5f)
      // Lighter highlight (1.5px)
      shapeBatch.strokeLine(rootBaseX + 0.5f, rootBaseY, mid1X + 0.5f, mid1Y, 1.5f, 0.5f, 0.78f, 0.28f, 0.4f)
      shapeBatch.strokeLine(mid1X + 0.5f, mid1Y, mid2X + 0.5f, mid2Y, 1.2f, 0.5f, 0.78f, 0.28f, 0.35f)

      // Thorns along the vine (2-3 per vine)
      { var t = 0; while (t < 3) {
        val thornFrac = 0.25f + t * 0.25f
        val thornBaseX = rootBaseX + (tipX - rootBaseX) * thornFrac + Math.sin(tick * 0.05 + i + t).toFloat * 1f
        val thornBaseY = rootBaseY + (tipY - rootBaseY) * thornFrac
        val thornDir = if ((i + t) % 2 == 0) 1f else -1f
        val thornTipX = thornBaseX + thornDir * 4f
        val thornTipY = thornBaseY - 2f
        shapeBatch.strokeLine(thornBaseX, thornBaseY, thornTipX, thornTipY, 2f, 0.15f, 0.4f, 0.08f, 0.55f)
        shapeBatch.strokeLine(thornBaseX, thornBaseY, thornTipX, thornTipY, 1f, 0.35f, 0.6f, 0.15f, 0.4f)
      ; t += 1 } }

      // Leaf at vine tip (bigger and more visible)
      val leafAngle = tick * 0.03f + i * 1.5f
      val leafX = tipX + Math.cos(leafAngle).toFloat * 5f
      val leafY = tipY + Math.sin(leafAngle).toFloat * 2.5f
      shapeBatch.fillOval(leafX, leafY, 5f, 3f, 0.22f, 0.5f, 0.1f, 0.65f, 6)
      shapeBatch.fillOval(leafX + 0.5f, leafY - 0.5f, 3f, 1.8f, 0.4f, 0.72f, 0.22f, 0.4f, 4)
      // Second leaf offset
      val leaf2X = tipX - Math.cos(leafAngle + 1f).toFloat * 4f
      val leaf2Y = tipY - Math.sin(leafAngle + 1f).toFloat * 2f - 1f
      shapeBatch.fillOval(leaf2X, leaf2Y, 4f, 2.5f, 0.2f, 0.48f, 0.08f, 0.55f, 6)
      i += 1
    }

    // Small dirt particles kicked up (6 particles)
    { var d = 0; while (d < 6) {
      val driftPhase = (tick * 0.025f + d * 1.05f) % 6.28f
      val driftH = Math.abs(Math.sin(driftPhase)).toFloat * 12f
      val driftX = x + Math.cos(tick * 0.02 + d * 1.8).toFloat * (10f + d * 3f)
      val driftY = y + 8f - driftH
      val dAlpha = (1f - driftH / 12f) * 0.5f
      shapeBatch.fillOval(driftX, driftY, 2f, 1.5f, 0.35f, 0.25f, 0.12f, dAlpha, 4)
    ; d += 1 } }
  }

  def drawSlowedEffect(cx: Double, cy: Double): Unit = {
    beginShapes()
    val x = cx.toFloat; val y = cy.toFloat
    val tick = animationTick

    // Large pulsing blue-purple tint overlay (25px radius, strong alpha)
    val pulse = (0.6 + 0.4 * Math.sin(tick * 0.07)).toFloat
    shapeBatch.fillOvalSoft(x, y, 28f, 22f, 0.25f, 0.3f, 0.7f, 0.18f * pulse, 0f, 18)
    shapeBatch.fillOval(x, y, 20f, 16f, 0.3f, 0.35f, 0.8f, 0.12f * pulse, 16)

    // Blue frost-like ground tint below feet
    shapeBatch.fillOvalSoft(x, y + 10f, 22f, 8f, 0.35f, 0.45f, 0.9f, 0.15f * pulse, 0f, 12)

    // 8 blue particles in slow orbit with soft trails
    var i = 0
    while (i < 8) {
      val orbitAngle = tick * 0.025f + i * Math.PI.toFloat * 2f / 8f
      val orbitR = 18f + Math.sin(tick * 0.04 + i * 1.3).toFloat * 4f
      val px = x + orbitR * Math.cos(orbitAngle).toFloat
      val py = y + 5f + orbitR * Math.sin(orbitAngle).toFloat * 0.35f
      val particleAlpha = (0.35 + 0.3 * Math.sin(tick * 0.06 + i * 2.1)).toFloat
      shapeBatch.fillOval(px, py, 3.5f, 3f, 0.4f, 0.5f, 0.95f, particleAlpha, 6)
      // Soft trail behind particle (longer)
      val prevAngle1 = orbitAngle - 0.3f
      val prevAngle2 = orbitAngle - 0.6f
      val prevX1 = x + orbitR * Math.cos(prevAngle1).toFloat
      val prevY1 = y + 5f + orbitR * Math.sin(prevAngle1).toFloat * 0.35f
      val prevX2 = x + orbitR * Math.cos(prevAngle2).toFloat
      val prevY2 = y + 5f + orbitR * Math.sin(prevAngle2).toFloat * 0.35f
      shapeBatch.strokeLineSoft(prevX2, prevY2, prevX1, prevY1, 1.5f, 0.3f, 0.4f, 0.85f, particleAlpha * 0.25f)
      shapeBatch.strokeLineSoft(prevX1, prevY1, px, py, 2f, 0.35f, 0.45f, 0.9f, particleAlpha * 0.45f)
      i += 1
    }

    // Animated clock/hourglass symbol above player (bigger, 10px tall, 2px thick strokes, glowing blue)
    val hx = x; val hy = y - 26f
    val hPulse = (0.6 + 0.4 * Math.sin(tick * 0.12)).toFloat
    // Glow behind hourglass
    shapeBatch.fillOvalSoft(hx, hy, 10f, 8f, 0.4f, 0.5f, 1f, 0.12f * hPulse, 0f, 10)
    // Top triangle (bigger)
    shapeBatch.strokeLine(hx - 6f, hy - 6f, hx + 6f, hy - 6f, 2.2f, 0.5f, 0.6f, 1f, 0.6f * hPulse)
    shapeBatch.strokeLine(hx - 6f, hy - 6f, hx, hy, 2.2f, 0.5f, 0.6f, 1f, 0.6f * hPulse)
    shapeBatch.strokeLine(hx + 6f, hy - 6f, hx, hy, 2.2f, 0.5f, 0.6f, 1f, 0.6f * hPulse)
    // Bottom triangle (bigger)
    shapeBatch.strokeLine(hx - 6f, hy + 6f, hx + 6f, hy + 6f, 2.2f, 0.5f, 0.6f, 1f, 0.6f * hPulse)
    shapeBatch.strokeLine(hx - 6f, hy + 6f, hx, hy, 2.2f, 0.5f, 0.6f, 1f, 0.6f * hPulse)
    shapeBatch.strokeLine(hx + 6f, hy + 6f, hx, hy, 2.2f, 0.5f, 0.6f, 1f, 0.6f * hPulse)
    // Sand grains falling through center
    val sandY = hy - 4f + ((tick * 0.15f) % 8f)
    shapeBatch.fillOval(hx, sandY, 1.5f, 1.5f, 0.7f, 0.75f, 1f, 0.5f * hPulse, 4)
    val sandY2 = hy - 4f + ((tick * 0.15f + 4f) % 8f)
    shapeBatch.fillOval(hx + 1f, sandY2, 1f, 1f, 0.7f, 0.75f, 1f, 0.4f * hPulse, 4)

    // Trailing afterimage effect: 3 semi-transparent blue diamond shapes at 10px, 18px, 26px behind
    { var a = 0; while (a < 3) {
      val afterDist = 10f + a * 8f
      val afterAlpha = (0.2f - a * 0.055f) * pulse
      val ax = x - afterDist * 0.7f
      val ay = y + afterDist * 0.1f
      val dSize = 5f - a * 0.8f
      _indicatorXs(0) = ax; _indicatorXs(1) = ax + dSize; _indicatorXs(2) = ax; _indicatorXs(3) = ax - dSize
      _indicatorYs(0) = ay - dSize * 1.3f; _indicatorYs(1) = ay; _indicatorYs(2) = ay + dSize * 1.3f; _indicatorYs(3) = ay
      shapeBatch.fillPolygon(_indicatorXs, _indicatorYs, 4, 0.35f, 0.45f, 0.9f, afterAlpha)
    ; a += 1 } }

    // 4 blue energy wisps drifting slowly upward
    { var w = 0; while (w < 4) {
      val wispPhase = (tick * 0.02f + w * 1.57f) % 6.28f
      val wispX = x + Math.sin(tick * 0.03 + w * 2.1).toFloat * 10f
      val wispY = y - 6f - Math.abs(Math.sin(wispPhase)).toFloat * 20f
      val wispAlpha = (1f - Math.abs(Math.sin(wispPhase)).toFloat) * 0.35f
      shapeBatch.fillOvalSoft(wispX, wispY, 3f, 4f, 0.4f, 0.55f, 1f, wispAlpha, 0f, 6)
      shapeBatch.fillOval(wispX, wispY, 1.5f, 2f, 0.6f, 0.7f, 1f, wispAlpha * 1.2f, 4)
    ; w += 1 } }
  }

  /**
   * Speed boost — swept chevrons, a ground ribbon and kicked-up dust, all streaming off the
   * player's trailing side. Oriented by facing (through the isometric mapping) so the wake
   * points the right way instead of always to screen-left.
   */
  def drawSpeedBoostEffect(cx: Double, cy: Double, dirId: Int): Unit = {
    beginShapes()
    val x = cx.toFloat; val y = cy.toFloat
    val t = animTickF
    val d = if (dirId >= 0 && dirId < 4) dirId else 0
    // Trailing direction (opposite of facing) and its perpendicular, in screen space
    val bx = -_dirSX(d); val by = -_dirSY(d)
    val px = -by; val py = bx

    // Speed lines streaming off the body, staggered laterally and in time
    var i = 0
    while (i < 4) {
      val ph = ((t * 0.11f + i * 0.25f) % 1f)
      val lat = (i - 1.5f) * 6f              // lateral offset across the body
      val start = 4f + ph * 10f
      val len = 13f * (1f - ph * 0.45f)
      val a = (1f - ph) * 0.42f
      val ox = x + px * lat + by * 3f        // slight vertical bias so lines sit on the torso
      val oy = y + py * lat
      shapeBatch.strokeLineSoft(ox + bx * start, oy + by * start,
        ox + bx * (start + len), oy + by * (start + len), 2.2f * (1f - ph * 0.4f),
        0.45f, 0.85f, 1f, a)
      i += 1
    }

    // Chevrons pointing the way they're travelling, sliding backward down the wake
    i = 0
    while (i < 2) {
      val ph = ((t * 0.08f + i * 0.5f) % 1f)
      val dist = 10f + ph * 20f
      val a = (1f - ph) * (1f - ph) * 0.55f
      val arm = 11f - ph * 3f
      val apexX = x + bx * dist; val apexY = y + by * dist
      val w = 2.6f * (1f - ph * 0.4f)
      shapeBatch.strokeLineSoft(apexX, apexY, apexX + bx * arm + px * arm * 0.75f,
        apexY + by * arm + py * arm * 0.75f, w, 0.5f, 0.88f, 1f, a)
      shapeBatch.strokeLineSoft(apexX, apexY, apexX + bx * arm - px * arm * 0.75f,
        apexY + by * arm - py * arm * 0.75f, w, 0.5f, 0.88f, 1f, a)
      i += 1
    }

    // Dust kicked up along the wake, hugging the ground
    val gy = y + 20f
    i = 0
    while (i < 3) {
      val dp = ((t * 0.05f + i * 0.34f) % 1f)
      shapeBatch.fillOvalSoft(x + bx * (8f + dp * 22f) + px * (i - 1) * 4f,
        gy + by * (4f + dp * 9f) - dp * 5f,
        4f + dp * 8f, 2.5f + dp * 4f, 0.62f, 0.60f, 0.52f, 0.22f * (1f - dp), 0f, 8)
      i += 1
    }
  }

}
