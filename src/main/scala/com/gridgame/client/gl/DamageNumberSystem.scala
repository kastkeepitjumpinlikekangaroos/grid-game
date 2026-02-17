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
  private var _activeCount = 0

  /** Set the font used for text width measurement (called once from renderer init). */
  private var _font: GLFontRenderer = _
  def setFont(font: GLFontRenderer): Unit = { _font = font }

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
    val text = dmg.toString
    cachedText(idx) = text
    cachedTextW(idx) = if (_font != null) _font.measureWidth(text) else 0f
    // Slight random horizontal scatter based on damage value
    offsetX(idx) = ((dmg * 17 + wx.toInt * 7) % 21 - 10).toFloat
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

  /** Render all active damage numbers. */
  def render(font: GLFontRenderer, spriteBatch: SpriteBatch, camOffX: Double, camOffY: Double,
             worldToScreenX: (Double, Double) => Double,
             worldToScreenY: (Double, Double) => Double): Unit = {
    var i = 0
    while (i < MAX_NUMBERS) {
      if (active(i)) {
        val t = age(i) / LIFETIME
        val alpha = Math.max(0f, 1f - t * t) // quadratic fade
        val rise = t * 30f // float upward in screen space

        val sx = worldToScreenX(worldX(i).toDouble, worldY(i).toDouble).toFloat + offsetX(i)
        val sy = worldToScreenY(worldX(i).toDouble, worldY(i).toDouble).toFloat - rise - 20f

        val text = cachedText(i)
        val textW = cachedTextW(i)

        // Scale color toward white for high damage
        val brightFactor = Math.min(1f, damage(i) / 30f) * 0.3f
        val r = Math.min(1f, cr(i) + brightFactor)
        val g = Math.min(1f, cg(i) + brightFactor)
        val b = Math.min(1f, cb(i) + brightFactor)

        font.drawTextOutlined(spriteBatch, text, sx - textW / 2f, sy, r, g, b, alpha)
      }
      i += 1
    }
  }

  /** Check if any numbers are currently active. O(1) via tracked count. */
  def hasActive: Boolean = _activeCount > 0
}
