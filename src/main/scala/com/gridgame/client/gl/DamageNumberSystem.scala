package com.gridgame.client.gl

/**
 * Floating damage numbers that rise and fade out when players take damage.
 * Uses a pre-allocated pool to avoid per-frame allocations.
 */
class DamageNumberSystem {
  private val MAX_NUMBERS = 32
  private val LIFETIME = 1.2f // seconds

  // Pool arrays
  private val worldX = new Array[Float](MAX_NUMBERS)
  private val worldY = new Array[Float](MAX_NUMBERS)
  private val damage = new Array[Int](MAX_NUMBERS)
  private val cr = new Array[Float](MAX_NUMBERS)
  private val cg = new Array[Float](MAX_NUMBERS)
  private val cb = new Array[Float](MAX_NUMBERS)
  private val age = new Array[Float](MAX_NUMBERS)
  private val active = new Array[Boolean](MAX_NUMBERS)
  private val offsetX = new Array[Float](MAX_NUMBERS) // random horizontal scatter
  private val cachedText = new Array[String](MAX_NUMBERS) // cached damage.toString
  private val cachedTextW = new Array[Float](MAX_NUMBERS) // cached measureWidth result
  private val useLargeFont = new Array[Boolean](MAX_NUMBERS) // true for big hits (dmg >= 25)
  private val jumpV = new Array[Float](MAX_NUMBERS) // per-number arc velocity
  private val grav = new Array[Float](MAX_NUMBERS) // per-number arc gravity
  private var _activeCount = 0
  private var _spawnSeq = 0

  /** Set the fonts used for text width measurement (called once from renderer init). */
  private var _font: GLFontRenderer = _
  private var _fontLarge: GLFontRenderer = _
  def setFont(font: GLFontRenderer): Unit = { _font = font }
  def setFontLarge(font: GLFontRenderer): Unit = { _fontLarge = font }

  /** Spawn a new floating damage number at the given world position. */
  def spawn(wx: Float, wy: Float, dmg: Int, r: Float, g: Float, b: Float): Unit = {
    // Find inactive slot (or oldest)
    var bestIdx = 0
    var bestAge = -1f
    var i = 0
    while (i < MAX_NUMBERS) {
      if (!active(i)) {
        bestIdx = i
        bestAge = Float.MaxValue
        i = MAX_NUMBERS // break
      } else if (age(i) > bestAge) {
        bestAge = age(i)
        bestIdx = i
      }
      i += 1
    }
    val idx = bestIdx
    if (!active(idx)) _activeCount += 1 // replacing inactive slot
    worldX(idx) = wx
    worldY(idx) = wy
    damage(idx) = dmg
    cr(idx) = r
    cg(idx) = g
    cb(idx) = b
    age(idx) = 0f
    active(idx) = true
    val bigHit = dmg >= 25
    useLargeFont(idx) = bigHit
    // Big hits get a bigger arc
    if (bigHit) { jumpV(idx) = 90f; grav(idx) = 50f }
    else { jumpV(idx) = 60f; grav(idx) = 40f }
    val text = dmg.toString
    cachedText(idx) = text
    // Measure with the correct font
    val measureFont = if (bigHit && _fontLarge != null) _fontLarge else _font
    cachedTextW(idx) = if (measureFont != null) measureFont.measureWidth(text) else 0f
    // Horizontal scatter using spawn counter to avoid stacking
    _spawnSeq += 1
    offsetX(idx) = ((_spawnSeq * 13 + dmg * 17 + wx.toInt * 7) % 31 - 15).toFloat
  }

  /** Update all active numbers. Call once per frame. */
  def update(deltaSec: Float): Unit = {
    var i = 0
    while (i < MAX_NUMBERS) {
      if (active(i)) {
        age(i) += deltaSec
        if (age(i) >= LIFETIME) {
          active(i) = false
          _activeCount -= 1
        }
      }
      i += 1
    }
  }

  /** Render all active damage numbers with scale pop, color by amount, and parabolic arc. */
  def render(font: GLFontRenderer, fontLarge: GLFontRenderer, spriteBatch: SpriteBatch, camOffX: Double, camOffY: Double,
             worldToScreenX: (Double, Double) => Double,
             worldToScreenY: (Double, Double) => Double): Unit = {
    var i = 0
    while (i < MAX_NUMBERS) {
      if (active(i)) {
        val t = age(i) / LIFETIME
        val alpha = Math.max(0f, 1f - t * t) // quadratic fade

        // Parabolic arc: per-number velocity and gravity
        val rise = jumpV(i) * t - grav(i) * t * t

        val sx = worldToScreenX(worldX(i).toDouble, worldY(i).toDouble).toFloat + offsetX(i)
        val sy = worldToScreenY(worldX(i).toDouble, worldY(i).toDouble).toFloat - rise - 20f

        val text = cachedText(i)
        val textW = cachedTextW(i)
        val dmg = damage(i)

        // Color by damage amount: high=orange-red, medium=yellow, low=white
        val (dr, dg, db) = if (dmg >= 25) (1f, 0.25f, 0.15f)
                           else if (dmg >= 10) (1f, 0.85f, 0.2f)
                           else (1f, 1f, 1f)

        // Scale pop: 1.5x -> 1.0x over first 0.2s with ease-out-back
        val popDuration = 0.2f
        val scale = if (age(i) < popDuration) {
          val pt = age(i) / popDuration
          val overshoot = 1.70158f
          val eased = 1f + (overshoot + 1f) * Math.pow(pt - 1, 3).toFloat + overshoot * Math.pow(pt - 1, 2).toFloat
          1.5f - 0.5f * eased
        } else 1.0f

        val scaledW = textW * scale
        val drawX = sx - scaledW / 2f

        // Use large font for big hits (dmg >= 25)
        val drawFont = if (useLargeFont(i)) fontLarge else font
        drawFont.drawTextOutlined(spriteBatch, text, drawX, sy, dr, dg, db, alpha)
      }
      i += 1
    }
  }

  /** Check if any numbers are currently active. O(1) via tracked count. */
  def hasActive: Boolean = _activeCount > 0
}
