package com.gridgame.client.render
package world

import com.gridgame.common.Constants
import com.gridgame.common.model._

import scala.collection.mutable

/**
 * Items on the ground: each drawn as its shape, bobbing and glowing, with a burst when it appears and
 * sparkles when it is picked up ([[detectChanges]]). Their glows are drawn additively with the
 * status effects' ([[drawDeferred]]). The shapes are the HUD's inventory icons too ([[drawItemShape]]).
 */
private[render] final class ItemPainter(ctx: RenderContext) {
  import ctx._

  // Item spawn/pickup animation tracking — Java HashMaps avoid Option wrapping + tuple allocation
  private val itemSpawnTimes = new java.util.HashMap[java.lang.Integer, java.lang.Long]()

  private val itemLastWorldPos = new java.util.HashMap[java.lang.Integer, Array[Int]]() // id -> reusable int[3]{cellX, cellY, colorRGB}

  private val drawnItemIdsThisFrame = new java.util.HashSet[java.lang.Integer]()

  // Pre-allocated buffer for item removal during detectChanges (avoids per-frame ArrayBuffer allocation)
  private val _itemRemoveBuffer = new mutable.ArrayBuffer[java.lang.Integer]()

  // Pre-allocated arrays for inline fillPolygon calls (avoids per-call Array allocation)
  private val _heartTriXs = new Array[Float](3)
  private val _heartTriYs = new Array[Float](3)

  // Pre-allocated polygon scratch shared by the status-effect / buff renderers
  private val _fxXs = new Array[Float](8)
  private val _fxYs = new Array[Float](8)

  // Pre-allocated arrays for item shapes and the stun's stars: ten vertices, an outer and an
  // inner radius alternating. Each is built and drawn before the next one touches them.
  private val _starXs = new Array[Float](10)
  private val _starYs = new Array[Float](10)

  // Pre-allocated arrays for item shape highlight
  private val _hlXs = new Array[Float](10)
  private val _hlYs = new Array[Float](10)

  // Pre-allocated arrays for gem/shield/default item shapes (avoids per-item allocation)
  private val _gemXs = new Array[Float](6)
  private val _gemYs = new Array[Float](6)
  private val _gemHlXs = new Array[Float](4)
  private val _gemHlYs = new Array[Float](4)
  private val _shieldXs = new Array[Float](7)
  private val _shieldYs = new Array[Float](7)
  private val _shieldHlXs = new Array[Float](4)
  private val _shieldHlYs = new Array[Float](4)
  private val _defItemXs = new Array[Float](4)
  private val _defItemYs = new Array[Float](4)

  // Pre-allocated arrays for star triangle decomposition
  private val _starTriXs = new Array[Float](3)
  private val _starTriYs = new Array[Float](3)
  private val _starPentXs = new Array[Float](5)
  private val _starPentYs = new Array[Float](5)

  // Deferred item additive effects (ground glow + sparkles)
  private val MAX_DEFERRED_ITEM_FX = 64
  private val _difCX = new Array[Float](MAX_DEFERRED_ITEM_FX)
  private val _difCY = new Array[Float](MAX_DEFERRED_ITEM_FX)
  private val _difHS = new Array[Float](MAX_DEFERRED_ITEM_FX)
  private val _difR = new Array[Float](MAX_DEFERRED_ITEM_FX)
  private val _difG = new Array[Float](MAX_DEFERRED_ITEM_FX)
  private val _difB = new Array[Float](MAX_DEFERRED_ITEM_FX)
  private val _difGlowPulse = new Array[Float](MAX_DEFERRED_ITEM_FX)
  private val _difBobPhase = new Array[Float](MAX_DEFERRED_ITEM_FX)
  private var _difCount = 0

  def draw(item: Item): Unit = {
    val now = frameTimeMs
    val ix = item.getCellX; val iy = item.getCellY
    val centerX = worldToScreenX(ix, iy).toFloat
    val halfSize = Constants.ITEM_SIZE_PX / 2f

    // Track for pickup detection
    drawnItemIdsThisFrame.add(item.id)
    val existingPos = itemLastWorldPos.get(item.id)
    if (existingPos != null) {
      existingPos(0) = ix; existingPos(1) = iy; existingPos(2) = item.colorRGB
    } else {
      itemLastWorldPos.put(item.id, Array(ix, iy, item.colorRGB))
    }

    // Spawn pop animation (bounce ease over 400ms)
    val existingSpawn = itemSpawnTimes.get(item.id)
    val spawnTime = if (existingSpawn != null) existingSpawn.longValue() else { itemSpawnTimes.put(item.id, now); now }
    val spawnElapsed = (now - spawnTime).toFloat
    val spawnScale = if (spawnElapsed < 400f) {
      val t = spawnElapsed / 400f
      if (t < 0.5f) 1f + 0.4f * Math.sin(t * Math.PI).toFloat
      else 1f + 0.15f * Math.sin(t * Math.PI).toFloat * (1f - t)
    } else 1f

    val bobPhase = animationTick * 0.06f + item.id * 1.7f
    val bobOffset = Math.sin(bobPhase).toFloat * 4f

    // Item-specific scale pulses
    val itemPulse = item.itemType match {
      case ItemType.Heart => 1f + 0.06f * Math.max(0.0, Math.sin(bobPhase * 3.5)).toFloat
      case ItemType.Star  => 1f + 0.03f * Math.sin(bobPhase * 2.0).toFloat
      case _              => 1f
    }
    val hs = halfSize * spawnScale * itemPulse

    val centerY = worldToScreenY(ix, iy).toFloat + bobOffset

    // Rotation angle (slow spin)
    val rotAngle = animationTick * 0.02f + item.id * 0.5f

    intToRGB(item.colorRGB)
    val ir = _rgb_r; val ig = _rgb_g; val ib = _rgb_b
    val glowPulse = (0.6 + 0.4 * Math.sin(bobPhase * 1.3)).toFloat

    // Item emits colored light — type-specific radius, color, and intensity
    item.itemType match {
      case ItemType.Gem =>
        lightSystem.addLight(centerX, centerY, 90f, ir, ig, ib, 0.25f * glowPulse)
      case ItemType.Heart =>
        lightSystem.addLight(centerX, centerY, 85f, 1f, 0.3f, 0.2f, 0.22f * glowPulse)
      case ItemType.Star =>
        lightSystem.addLight(centerX, centerY, 95f, 1f, 0.95f, 0.5f, 0.28f * glowPulse)
        // Secondary twinkle light
        val twinkle = (0.5f + 0.5f * Math.sin(animationTick * 0.4 + item.id * 1.3).toFloat)
        lightSystem.addLight(centerX, centerY, 55f, 1f, 1f, 0.8f, 0.12f * twinkle)
      case ItemType.Shield =>
        lightSystem.addLight(centerX, centerY, 80f, 0.3f, 0.5f, 1f, 0.2f * glowPulse)
      case _ =>
        lightSystem.addLight(centerX, centerY, 70f, ir, ig, ib, 0.15f * glowPulse)
    }

    beginShapes()
    // Pulsing ground ring — draws attention to pickups
    val ringPulse = (0.4f + 0.6f * Math.sin(bobPhase * 1.5f).toFloat).abs
    val ringRadius = hs * 1.8f + ringPulse * 4f
    shapeBatch.strokeOval(centerX, centerY + hs * 0.85f, ringRadius, ringRadius * 0.4f, 0.8f,
      ir, ig, ib, 0.12f * ringPulse, 14)
    // Drop shadow on ground
    shapeBatch.fillOvalSoft(centerX, centerY + hs * 0.85f, hs * 1.2f, hs * 0.3f, 0f, 0f, 0f, 0.22f, 0f, 12)

    // Main item shape with highlights and outline
    drawItemShape(item.itemType, centerX, centerY, hs, ir, ig, ib, rotAngle)

    // Defer additive effects (ground glow + sparkles) to batch pass
    val di = _difCount
    if (di < MAX_DEFERRED_ITEM_FX) {
      _difCX(di) = centerX; _difCY(di) = centerY; _difHS(di) = hs
      _difR(di) = ir; _difG(di) = ig; _difB(di) = ib
      _difGlowPulse(di) = glowPulse; _difBobPhase(di) = bobPhase
      _difCount += 1
    }
  }

  /** Fill a 10-vertex star polygon (concave) by decomposing into 5 point triangles + center pentagon. */
  private def fillStarPolygon(xs: Array[Float], ys: Array[Float], r: Float, g: Float, b: Float, a: Float): Unit = {
    // 5 triangles for the star points: each outer vertex + its two adjacent inner vertices
    var k = 0
    while (k < 5) {
      val outer = k * 2
      val innerPrev = (k * 2 + 9) % 10
      val innerNext = (k * 2 + 1) % 10
      _starTriXs(0) = xs(outer);     _starTriYs(0) = ys(outer)
      _starTriXs(1) = xs(innerPrev); _starTriYs(1) = ys(innerPrev)
      _starTriXs(2) = xs(innerNext); _starTriYs(2) = ys(innerNext)
      shapeBatch.fillPolygon(_starTriXs, _starTriYs, 3, r, g, b, a)
      k += 1
    }
    // Center pentagon from the 5 inner vertices (convex, fan triangulation works)
    _starPentXs(0) = xs(1); _starPentYs(0) = ys(1)
    _starPentXs(1) = xs(3); _starPentYs(1) = ys(3)
    _starPentXs(2) = xs(5); _starPentYs(2) = ys(5)
    _starPentXs(3) = xs(7); _starPentYs(3) = ys(7)
    _starPentXs(4) = xs(9); _starPentYs(4) = ys(9)
    shapeBatch.fillPolygon(_starPentXs, _starPentYs, 5, r, g, b, a)
  }

  def drawItemShape(itemType: ItemType, cx: Float, cy: Float, hs: Float, r: Float, g: Float, b: Float, rotation: Float = 0f): Unit = {
    val cos = Math.cos(rotation).toFloat
    val sin = Math.sin(rotation).toFloat
    // Highlight and shadow colors for 3D depth
    val hr = clamp(r * 0.4f + 0.6f); val hg = clamp(g * 0.4f + 0.6f); val hb = clamp(b * 0.4f + 0.6f)
    val dr = r * 0.6f; val dg = g * 0.6f; val db = b * 0.6f

    itemType match {
      case ItemType.Heart =>
        val rr = hs * 0.6f
        // Dark outline layer (slightly larger)
        val or = rr + 1.5f
        shapeBatch.fillOval(cx - hs * 0.48f, cy - hs * 0.45f, or, or, dr * 0.7f, dg * 0.7f, db * 0.7f, 1f, 14)
        shapeBatch.fillOval(cx + hs * 0.48f, cy - hs * 0.45f, or, or, dr * 0.7f, dg * 0.7f, db * 0.7f, 1f, 14)
        _heartTriXs(0) = cx - hs * 1.08f; _heartTriXs(1) = cx; _heartTriXs(2) = cx + hs * 1.08f
        _heartTriYs(0) = cy - hs * 0.08f; _heartTriYs(1) = cy + hs * 1.05f; _heartTriYs(2) = cy - hs * 0.08f
        shapeBatch.fillPolygon(_heartTriXs, _heartTriYs, 3, dr * 0.7f, dg * 0.7f, db * 0.7f, 1f)
        // Main fill
        shapeBatch.fillOval(cx - hs * 0.48f, cy - hs * 0.45f, rr, rr, r, g, b, 1f, 14)
        shapeBatch.fillOval(cx + hs * 0.48f, cy - hs * 0.45f, rr, rr, r, g, b, 1f, 14)
        _heartTriXs(0) = cx - hs * 1.02f; _heartTriXs(1) = cx; _heartTriXs(2) = cx + hs * 1.02f
        _heartTriYs(0) = cy - hs * 0.1f; _heartTriYs(1) = cy + hs; _heartTriYs(2) = cy - hs * 0.1f
        shapeBatch.fillPolygon(_heartTriXs, _heartTriYs, 3, r, g, b, 1f)
        // Upper highlight band across both lobes
        shapeBatch.fillOval(cx - hs * 0.48f, cy - hs * 0.52f, rr * 0.7f, rr * 0.5f, hr, hg, hb, 0.5f, 10)
        shapeBatch.fillOval(cx + hs * 0.42f, cy - hs * 0.52f, rr * 0.5f, rr * 0.4f, hr, hg, hb, 0.35f, 10)
        // Specular highlight (white dot on left lobe)
        shapeBatch.fillOval(cx - hs * 0.5f, cy - hs * 0.58f, rr * 0.22f, rr * 0.18f, 1f, 1f, 1f, 0.6f, 8)
        // Lower shadow for depth
        shapeBatch.fillOvalSoft(cx, cy + hs * 0.5f, hs * 0.7f, hs * 0.3f, dr * 0.4f, dg * 0.4f, db * 0.4f, 0.3f, 0f, 8)

      case ItemType.Star =>
        val outerR = hs; val innerR = hs * 0.35f
        // Dark outline star (slightly larger)
        var i = 0
        while (i < 10) {
          val angle = Math.PI / 2 + i * Math.PI / 5 + rotation
          val rad = if (i % 2 == 0) outerR + 1.5f else innerR + 0.5f
          _hlXs(i) = cx + (rad * Math.cos(angle)).toFloat
          _hlYs(i) = cy - (rad * Math.sin(angle)).toFloat
          i += 1
        }
        fillStarPolygon(_hlXs, _hlYs, dr * 0.7f, dg * 0.7f, db * 0.7f, 1f)
        // Main star
        i = 0
        while (i < 10) {
          val angle = Math.PI / 2 + i * Math.PI / 5 + rotation
          val rad = if (i % 2 == 0) outerR else innerR
          _starXs(i) = cx + (rad * Math.cos(angle)).toFloat
          _starYs(i) = cy - (rad * Math.sin(angle)).toFloat
          i += 1
        }
        fillStarPolygon(_starXs, _starYs, r, g, b, 1f)
        // Inner bright star (50% size, lighter)
        i = 0
        while (i < 10) {
          val angle = Math.PI / 2 + i * Math.PI / 5 + rotation
          val rad = if (i % 2 == 0) outerR * 0.5f else innerR * 0.7f
          _hlXs(i) = cx + (rad * Math.cos(angle)).toFloat
          _hlYs(i) = cy - (rad * Math.sin(angle)).toFloat
          i += 1
        }
        fillStarPolygon(_hlXs, _hlYs, hr, hg, hb, 0.45f)
        // Bright center core
        shapeBatch.fillOvalSoft(cx, cy, hs * 0.4f, hs * 0.4f, 1f, 1f, 0.9f, 0.45f, 0f, 10)
        shapeBatch.fillOval(cx, cy, hs * 0.15f, hs * 0.15f, 1f, 1f, 1f, 0.65f, 8)

      case ItemType.Gem =>
        // Hexagonal gem with 6 distinct facets
        val n = 6
        var i = 0
        while (i < n) {
          val angle = rotation + i * Math.PI / 3.0
          _gemXs(i) = cx + (hs * Math.cos(angle)).toFloat
          _gemYs(i) = cy + (hs * Math.sin(angle)).toFloat
          i += 1
        }
        // Dark outline
        shapeBatch.strokePolygon(_gemXs, _gemYs, n, 2f, dr * 0.5f, dg * 0.5f, db * 0.5f, 0.8f)
        // Draw 6 triangular facets with alternating brightness
        i = 0
        while (i < n) {
          val ni = (i + 1) % n
          _gemHlXs(0) = cx;        _gemHlYs(0) = cy
          _gemHlXs(1) = _gemXs(i); _gemHlYs(1) = _gemYs(i)
          _gemHlXs(2) = _gemXs(ni); _gemHlYs(2) = _gemYs(ni)
          val facetBright = if (i % 2 == 0) 1.0f else 0.72f
          shapeBatch.fillPolygon(_gemHlXs, _gemHlYs, 3, clamp(r * facetBright), clamp(g * facetBright), clamp(b * facetBright), 1f)
          // Facet edge line from vertex to center
          shapeBatch.strokeLine(_gemXs(i), _gemYs(i), cx, cy, 0.5f, dr, dg, db, 0.25f)
          i += 1
        }
        // Top-left highlight for glassy look
        shapeBatch.fillOvalSoft(cx - hs * 0.2f, cy - hs * 0.3f, hs * 0.4f, hs * 0.35f, 1f, 1f, 1f, 0.3f, 0f, 8)
        // Center bright point
        shapeBatch.fillOval(cx, cy, hs * 0.12f, hs * 0.12f, 1f, 1f, 1f, 0.5f, 8)
        // Final outline
        shapeBatch.strokePolygon(_gemXs, _gemYs, n, 1f, dr, dg, db, 0.7f)

      case ItemType.Shield =>
        // Dark outline (slightly larger shape)
        _shieldXs(0) = cx - hs * 0.88f; _shieldYs(0) = cy - hs * 0.6f
        _shieldXs(1) = cx - hs * 0.52f; _shieldYs(1) = cy - hs * 1.03f
        _shieldXs(2) = cx + hs * 0.52f; _shieldYs(2) = cy - hs * 1.03f
        _shieldXs(3) = cx + hs * 0.88f; _shieldYs(3) = cy - hs * 0.6f
        _shieldXs(4) = cx + hs * 0.72f; _shieldYs(4) = cy + hs * 0.42f
        _shieldXs(5) = cx;              _shieldYs(5) = cy + hs * 1.03f
        _shieldXs(6) = cx - hs * 0.72f; _shieldYs(6) = cy + hs * 0.42f
        shapeBatch.fillPolygon(_shieldXs, _shieldYs, 7, dr * 0.6f, dg * 0.6f, db * 0.6f, 1f)
        // Main shield fill
        _shieldXs(0) = cx - hs * 0.85f; _shieldYs(0) = cy - hs * 0.6f
        _shieldXs(1) = cx - hs * 0.5f;  _shieldYs(1) = cy - hs
        _shieldXs(2) = cx + hs * 0.5f;  _shieldYs(2) = cy - hs
        _shieldXs(3) = cx + hs * 0.85f; _shieldYs(3) = cy - hs * 0.6f
        _shieldXs(4) = cx + hs * 0.7f;  _shieldYs(4) = cy + hs * 0.4f
        _shieldXs(5) = cx;              _shieldYs(5) = cy + hs
        _shieldXs(6) = cx - hs * 0.7f;  _shieldYs(6) = cy + hs * 0.4f
        shapeBatch.fillPolygon(_shieldXs, _shieldYs, 7, r, g, b, 1f)
        // Upper gradient highlight
        _shieldHlXs(0) = cx - hs * 0.45f; _shieldHlYs(0) = cy - hs * 0.55f
        _shieldHlXs(1) = cx - hs * 0.35f; _shieldHlYs(1) = cy - hs * 0.85f
        _shieldHlXs(2) = cx + hs * 0.35f; _shieldHlYs(2) = cy - hs * 0.85f
        _shieldHlXs(3) = cx + hs * 0.45f; _shieldHlYs(3) = cy - hs * 0.55f
        shapeBatch.fillPolygon(_shieldHlXs, _shieldHlYs, 4, hr, hg, hb, 0.35f)
        // Cross emblem in center
        val cw = hs * 0.12f; val ch = hs * 0.6f
        shapeBatch.fillRect(cx - cw, cy - ch * 0.55f, cw * 2, ch, hr, hg, hb, 0.3f)
        shapeBatch.fillRect(cx - ch * 0.35f, cy - cw * 0.8f, ch * 0.7f, cw * 1.6f, hr, hg, hb, 0.3f)
        // Metallic top-edge highlight
        shapeBatch.strokeLine(cx - hs * 0.48f, cy - hs * 0.98f, cx + hs * 0.48f, cy - hs * 0.98f, 1.5f, 1f, 1f, 1f, 0.2f)
        // Rivets at shoulder and tip
        shapeBatch.fillOval(cx - hs * 0.85f, cy - hs * 0.6f, 2f, 2f, hr, hg, hb, 0.5f, 6)
        shapeBatch.fillOval(cx + hs * 0.85f, cy - hs * 0.6f, 2f, 2f, hr, hg, hb, 0.5f, 6)
        shapeBatch.fillOval(cx, cy + hs * 0.95f, 2f, 2f, hr, hg, hb, 0.4f, 6)
        // Outline
        shapeBatch.strokePolygon(_shieldXs, _shieldYs, 7, 1f, dr, dg, db, 0.7f)

      case ItemType.Fence =>
        val barW = hs * 0.28f
        val tipH = barW * 0.8f
        val p0x = cx - hs * 0.75f; val p1x = cx; val p2x = cx + hs * 0.75f
        val postTop = cy - hs + tipH
        val postBot = cy + hs
        val postH = postBot - postTop
        // Dark post shadows (offset right)
        shapeBatch.fillRect(p0x + 1f, postTop, barW, postH, dr * 0.5f, dg * 0.5f, db * 0.5f, 0.6f)
        shapeBatch.fillRect(p1x - barW / 2 + 1f, postTop, barW, postH, dr * 0.5f, dg * 0.5f, db * 0.5f, 0.6f)
        shapeBatch.fillRect(p2x - barW + 1f, postTop, barW, postH, dr * 0.5f, dg * 0.5f, db * 0.5f, 0.6f)
        // Main posts
        shapeBatch.fillRect(p0x, postTop, barW, postH, r, g, b, 1f)
        shapeBatch.fillRect(p1x - barW / 2, postTop, barW, postH, r, g, b, 1f)
        shapeBatch.fillRect(p2x - barW, postTop, barW, postH, r, g, b, 1f)
        // Pointed tops (triangles)
        _heartTriXs(0) = p0x; _heartTriYs(0) = postTop
        _heartTriXs(1) = p0x + barW / 2; _heartTriYs(1) = cy - hs
        _heartTriXs(2) = p0x + barW; _heartTriYs(2) = postTop
        shapeBatch.fillPolygon(_heartTriXs, _heartTriYs, 3, clamp(r * 1.1f), clamp(g * 1.1f), clamp(b * 1.1f), 1f)
        _heartTriXs(0) = p1x - barW / 2; _heartTriYs(0) = postTop
        _heartTriXs(1) = p1x; _heartTriYs(1) = cy - hs
        _heartTriXs(2) = p1x + barW / 2; _heartTriYs(2) = postTop
        shapeBatch.fillPolygon(_heartTriXs, _heartTriYs, 3, clamp(r * 1.1f), clamp(g * 1.1f), clamp(b * 1.1f), 1f)
        _heartTriXs(0) = p2x - barW; _heartTriYs(0) = postTop
        _heartTriXs(1) = p2x - barW / 2; _heartTriYs(1) = cy - hs
        _heartTriXs(2) = p2x; _heartTriYs(2) = postTop
        shapeBatch.fillPolygon(_heartTriXs, _heartTriYs, 3, clamp(r * 1.1f), clamp(g * 1.1f), clamp(b * 1.1f), 1f)
        // Horizontal cross rails
        val railH = barW * 0.65f
        val rail1Y = cy - hs * 0.3f; val rail2Y = cy + hs * 0.3f
        shapeBatch.fillRect(p0x, rail1Y, p2x - p0x, railH, r * 0.9f, g * 0.9f, b * 0.9f, 1f)
        shapeBatch.fillRect(p0x, rail2Y, p2x - p0x, railH, r * 0.9f, g * 0.9f, b * 0.9f, 1f)
        // Rail top-edge highlight
        shapeBatch.fillRect(p0x, rail1Y, p2x - p0x, railH * 0.3f, hr, hg, hb, 0.25f)
        shapeBatch.fillRect(p0x, rail2Y, p2x - p0x, railH * 0.3f, hr, hg, hb, 0.25f)
        // Post left-edge highlights
        val hlW = barW * 0.3f
        shapeBatch.fillRect(p0x, postTop, hlW, postH, hr, hg, hb, 0.3f)
        shapeBatch.fillRect(p1x - barW / 2, postTop, hlW, postH, hr, hg, hb, 0.3f)
        shapeBatch.fillRect(p2x - barW, postTop, hlW, postH, hr, hg, hb, 0.3f)

      case _ =>
        _defItemXs(0) = cx;      _defItemYs(0) = cy - hs
        _defItemXs(1) = cx + hs; _defItemYs(1) = cy
        _defItemXs(2) = cx;      _defItemYs(2) = cy + hs
        _defItemXs(3) = cx - hs; _defItemYs(3) = cy
        shapeBatch.fillPolygon(_defItemXs, _defItemYs, 4, r, g, b, 1f)
        shapeBatch.strokePolygon(_defItemXs, _defItemYs, 1f, dr, dg, db, 0.7f)
    }
  }

  def detectChanges(): Unit = {
    // Items tracked but not drawn this frame = picked up
    _itemRemoveBuffer.clear()
    val iter = itemLastWorldPos.entrySet().iterator()
    while (iter.hasNext) {
      val entry = iter.next()
      val id = entry.getKey
      if (!drawnItemIdsThisFrame.contains(id)) {
        val posData = entry.getValue
        val wx = posData(0); val wy = posData(1); val colorRGB = posData(2)
        // Spawn pickup ring burst particles
        val sx = worldToScreenX(wx, wy).toFloat
        val sy = worldToScreenY(wx, wy).toFloat
        intToRGB(colorRGB)
        val cr = _rgb_r; val cg = _rgb_g; val cb = _rgb_b
        var _k = 0
        while (_k < 12) {
          val angle = rng.nextFloat() * Math.PI.toFloat * 2f
          val speed = 25f + rng.nextFloat() * 35f
          combatParticles.emit(sx, sy,
            Math.cos(angle).toFloat * speed, Math.sin(angle).toFloat * speed,
            plife = 0.3f + rng.nextFloat() * 0.25f,
            pr = clamp(cr * 0.5f + 0.5f), pg = clamp(cg * 0.5f + 0.5f), pb = clamp(cb * 0.5f + 0.5f),
            palpha = 0.6f,
            psize = 2f + rng.nextFloat() * 2f,
            pgravity = 15f, additive = true)
          _k += 1
        }
        _itemRemoveBuffer += id
        itemSpawnTimes.remove(id)
      }
    }
    var ri = 0
    while (ri < _itemRemoveBuffer.size) {
      itemLastWorldPos.remove(_itemRemoveBuffer(ri))
      ri += 1
    }
  }

  def beginFrame(): Unit = {
    drawnItemIdsThisFrame.clear()
    _difCount = 0
  }

  def reset(): Unit = {
    itemSpawnTimes.clear()
    itemLastWorldPos.clear()
    drawnItemIdsThisFrame.clear()
  }

  /** Whether any item's glow was deferred this frame. */
  def hasDeferred: Boolean = _difCount != 0

  /** Every item's glow (ground glow, light shafts, twinkles, motes), in the additive blend the
    * renderer has set. */
  def drawDeferred(): Unit = {
    var i = 0
    while (i < _difCount) {
      val cx = _difCX(i); val cy = _difCY(i); val hs = _difHS(i)
      val ir = _difR(i); val ig = _difG(i); val ib = _difB(i)
      val glowPulse = _difGlowPulse(i); val bobPhase = _difBobPhase(i)
      // Ground glow
      shapeBatch.fillOvalSoft(cx, cy + hs * 0.3f, hs * 2.5f, hs * 0.7f, ir, ig, ib, 0.22f * glowPulse, 0f, 14)
      shapeBatch.fillOvalSoft(cx, cy, hs * 2.8f, hs * 2f, ir, ig, ib, 0.1f * glowPulse, 0f, 14)
      // Light shafts sweeping off the pickup — two tapered wedges rotating slowly, so an
      // item reads as radiating light instead of sitting inside a glow blob
      var j = 0
      while (j < 2) {
        val ra = bobPhase * 0.35f + j * 3.1415927f
        val rc = Math.cos(ra).toFloat; val rs = Math.sin(ra).toFloat * 0.55f
        val rl = hs * (2.6f + 0.5f * Math.sin(bobPhase * 1.7f + j).toFloat)
        _fxXs(0) = cx - rs * hs * 0.35f; _fxYs(0) = cy + rc * hs * 0.2f
        _fxXs(1) = cx + rs * hs * 0.35f; _fxYs(1) = cy - rc * hs * 0.2f
        _fxXs(2) = cx + rc * rl;         _fxYs(2) = cy + rs * rl
        shapeBatch.fillPolygon(_fxXs, _fxYs, 3, ir, ig, ib, 0.09f * glowPulse)
        j += 1
      }
      // Orbiting twinkles
      j = 0
      while (j < 3) {
        val sparkAngle = bobPhase * 0.8f + j * 2.0943951f
        val sparkDist = hs * 1.1f
        val sx = cx + sparkDist * Math.cos(sparkAngle).toFloat
        val sy = cy + sparkDist * Math.sin(sparkAngle).toFloat * 0.6f
        val sparkAlpha = (0.28 + 0.34 * Math.sin(bobPhase * 2.5 + j * 2.1)).toFloat
        val sparkSize = (2.6 + Math.sin(bobPhase * 3.0 + j) * 1.1).toFloat
        shapeBatch.fillStarFlare(sx, sy, sparkSize * 2.2f, sparkSize * 0.5f,
          bobPhase * 0.6f + j, 0.5f, 1f, 1f, 0.95f, clamp(sparkAlpha))
        j += 1
      }
      // Motes rising off the item
      j = 0
      while (j < 2) {
        val mp = (bobPhase * 0.07f + j * 0.5f) % 1f
        shapeBatch.fillDot(cx + Math.sin(bobPhase * 0.9f + j * 2.2f).toFloat * hs * 0.8f,
          cy - hs * 0.4f - mp * hs * 2.4f, 1.1f, ir * 0.5f + 0.5f, ig * 0.5f + 0.5f, ib * 0.5f + 0.5f,
          0.28f * (1f - mp))
        j += 1
      }
      i += 1
    }
    _difCount = 0
  }
}
