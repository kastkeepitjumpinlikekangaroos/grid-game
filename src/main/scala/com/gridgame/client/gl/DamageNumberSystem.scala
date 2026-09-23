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

  /** Set the fonts numbers are drawn and measured in (by the renderer, whenever it makes them). */
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

  /**
   * Render all active damage numbers with scale pop, color by amount, and parabolic arc. Drawn with
   * the world's projection, over the finished frame (GLGameRenderer), in the fonts [[setFont]] and
   * [[setFontLarge]] gave: scaled to 14 world units high, or 22 for a big hit.
   */
  def render(spriteBatch: SpriteBatch,
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
        val dmg = damage(i)

        // Color by damage amount: high=orange-red, medium=yellow, low=white
        // (three vals rather than a destructured tuple, which boxed all three every frame)
        val dr = 1f
        val dg = if (dmg >= 25) 0.25f else if (dmg >= 10) 0.85f else 1f
        val db = if (dmg >= 25) 0.15f else if (dmg >= 10) 0.2f else 1f

        // Scale pop: 1.5x -> 1.0x over first 0.2s with ease-out-back. It used to move the text
        // as though it had grown without growing it
        val popDuration = 0.2f
        val pop = if (age(i) < popDuration) {
          val pt = age(i) / popDuration
          val overshoot = 1.70158f
          val eased = 1f + (overshoot + 1f) * Math.pow(pt - 1, 3).toFloat + overshoot * Math.pow(pt - 1, 2).toFloat
          1.5f - 0.5f * eased
        } else 1.0f

        // Use large font for big hits (dmg >= 25)
        val big = useLargeFont(i)
        val drawFont = if (big) _fontLarge else _font
        val scale = pop * (if (big) 22f else 14f) / drawFont.fontSize
        val drawX = sx - cachedTextW(i) * scale / 2f
        drawFont.drawTextOutlinedHeavy(spriteBatch, text, drawX, sy, dr, dg, db, alpha, scale)
      }
      i += 1
    }
  }

  /** Check if any numbers are currently active. O(1) via tracked count. */
  def hasActive: Boolean = _activeCount > 0
}
