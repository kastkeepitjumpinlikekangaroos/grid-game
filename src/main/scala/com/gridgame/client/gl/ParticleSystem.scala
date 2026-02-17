package com.gridgame.client.gl

/**
 * Pool-based particle system for weather and combat effects.
 * Pre-allocates a fixed pool of particles to avoid GC pressure.
 * All coordinates are in screen space.
 */
class ParticleSystem(val maxParticles: Int = 2048) {
  // Particle data stored in parallel arrays for cache efficiency
  private val x = new Array[Float](maxParticles)
  private val y = new Array[Float](maxParticles)
  private val vx = new Array[Float](maxParticles)
  private val vy = new Array[Float](maxParticles)
  private val life = new Array[Float](maxParticles)    // remaining life in seconds
  private val maxLife = new Array[Float](maxParticles)  // initial lifetime
  private val r = new Array[Float](maxParticles)
  private val g = new Array[Float](maxParticles)
  private val b = new Array[Float](maxParticles)
  private val alpha = new Array[Float](maxParticles)   // starting alpha
  private val size = new Array[Float](maxParticles)
  private val gravity = new Array[Float](maxParticles)
  private val drag = new Array[Float](maxParticles)
  private val flags = new Array[Byte](maxParticles)    // bit flags: 1=additive, 2=shrink, 4=soft

  private var count = 0 // number of active particles
  // Buffer for deferred non-additive particle indices during single-pass rendering
  private val _deferBuf = new Array[Int](maxParticles)

  def activeCount: Int = count

  /** Emit a single particle. Returns false if pool is full. */
  def emit(px: Float, py: Float, pvx: Float, pvy: Float,
           plife: Float, pr: Float, pg: Float, pb: Float, palpha: Float,
           psize: Float, pgravity: Float = 0f, pdrag: Float = 0f,
           additive: Boolean = false, shrink: Boolean = true, soft: Boolean = false): Boolean = {
    if (count >= maxParticles) return false
    val i = count
    x(i) = px; y(i) = py
    vx(i) = pvx; vy(i) = pvy
    life(i) = plife; maxLife(i) = plife
    r(i) = pr; g(i) = pg; b(i) = pb
    alpha(i) = palpha; size(i) = psize
    gravity(i) = pgravity; drag(i) = pdrag
    var f: Byte = 0
    if (additive) f = (f | 1).toByte
    if (shrink) f = (f | 2).toByte
    if (soft) f = (f | 4).toByte
    flags(i) = f
    count += 1
    true
  }

  /** Update all particles. Call once per frame. */
  def update(dt: Float): Unit = {
    var i = 0
    while (i < count) {
      life(i) -= dt
      if (life(i) <= 0) {
        // Swap with last active particle
        count -= 1
        if (i < count) {
          x(i) = x(count); y(i) = y(count)
          vx(i) = vx(count); vy(i) = vy(count)
          life(i) = life(count); maxLife(i) = maxLife(count)
          r(i) = r(count); g(i) = g(count); b(i) = b(count)
          alpha(i) = alpha(count); size(i) = size(count)
          gravity(i) = gravity(count); drag(i) = drag(count)
          flags(i) = flags(count)
        }
        // Don't increment i - re-check the swapped particle
      } else {
        // Apply drag
        val d = drag(i)
        if (d > 0f) {
          val factor = 1f - d * dt
          vx(i) *= factor
          vy(i) *= factor
        }
        // Apply gravity
        vy(i) += gravity(i) * dt
        // Move
        x(i) += vx(i) * dt
        y(i) += vy(i) * dt
        i += 1
      }
    }
  }

  /** Render all particles using the given ShapeBatch. Batch must already be begun.
   * Single-pass: renders non-additive first, switches to additive on first additive particle,
   * defers any non-additive particles found after the switch to a small buffer. */
  def render(shapeBatch: ShapeBatch): Unit = {
    if (count == 0) return

    // Single pass with deferred non-additive particles found after mode switch
    var additiveMode = false
    var deferCount = 0
    var i = 0
    while (i < count) {
      val isAdditive = (flags(i) & 1) != 0
      if (!additiveMode) {
        if (!isAdditive) {
          renderParticle(shapeBatch, i)
        } else {
          // Switch to additive mode
          shapeBatch.setAdditiveBlend(true)
          additiveMode = true
          renderParticle(shapeBatch, i)
        }
      } else {
        if (isAdditive) {
          renderParticle(shapeBatch, i)
        } else {
          // Defer this non-additive particle — store index
          if (deferCount < _deferBuf.length) {
            _deferBuf(deferCount) = i
            deferCount += 1
          }
        }
      }
      i += 1
    }

    // Flush deferred non-additive particles (if any)
    if (deferCount > 0) {
      shapeBatch.setAdditiveBlend(false)
      additiveMode = false
      i = 0
      while (i < deferCount) {
        renderParticle(shapeBatch, _deferBuf(i))
        i += 1
      }
    } else if (additiveMode) {
      shapeBatch.setAdditiveBlend(false)
    }
  }

  private def renderParticle(shapeBatch: ShapeBatch, i: Int): Unit = {
    val t = life(i) / maxLife(i) // 1.0 at birth, 0.0 at death
    val a = alpha(i) * t // fade out over lifetime
    val s = if ((flags(i) & 2) != 0) size(i) * (0.3f + 0.7f * t) else size(i)
    if ((flags(i) & 4) != 0) {
      shapeBatch.fillOvalSoft(x(i), y(i), s, s, r(i), g(i), b(i), a, 0f, 8)
    } else {
      shapeBatch.fillOval(x(i), y(i), s, s, r(i), g(i), b(i), a, 6)
    }
  }

  /** Clear all particles. */
  def clear(): Unit = {
    count = 0
  }
}
