package com.gridgame.client.gl

object ParticleSystem {
  // Particle shapes. AUTO keeps the original behaviour (hard dot, or soft blob when
  // `soft` is set); the rest exist because a screen full of identical circles reads as
  // fog rather than as sparks, smoke and debris.
  val KIND_AUTO   = 0
  val KIND_STREAK = 1 // velocity-aligned tapered streak with a hot head
  val KIND_SPARK  = 2 // twinkling 4-point star flare
  val KIND_SMOKE  = 3 // layered puff that swells and thins as it dies
  val KIND_SHARD  = 4 // small spinning triangular chip of debris
}

/**
 * Pool-based particle system for weather and combat effects.
 * Pre-allocates a fixed pool of particles to avoid GC pressure.
 * All coordinates are in screen space.
 */
class ParticleSystem(val maxParticles: Int = 2048) {
  import ParticleSystem._

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
  private val kind = new Array[Byte](maxParticles)     // ParticleSystem.KIND_*
  // Per-particle phase so shape rotation/twinkle differs between neighbours. Indices are
  // reused by the swap-with-last removal, so this can't be derived from the index.
  private val seed = new Array[Float](maxParticles)

  private var count = 0 // number of active particles
  // Golden-ratio walk: successive emissions get well-spread phases without an RNG call
  private var seedPhase = 0f
  // Buffer for deferred non-additive particle indices during single-pass rendering
  private val _deferBuf = new Array[Int](maxParticles)

  // Scratch triangle for KIND_SHARD (no per-particle allocation)
  private val _triXs = new Array[Float](3)
  private val _triYs = new Array[Float](3)

  def activeCount: Int = count

  /** Emit a single particle. Returns false if pool is full. */
  def emit(px: Float, py: Float, pvx: Float, pvy: Float,
           plife: Float, pr: Float, pg: Float, pb: Float, palpha: Float,
           psize: Float, pgravity: Float = 0f, pdrag: Float = 0f,
           additive: Boolean = false, shrink: Boolean = true, soft: Boolean = false,
           pkind: Int = KIND_AUTO): Boolean = {
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
    kind(i) = pkind.toByte
    seedPhase += 0.6180339887f
    if (seedPhase >= 1f) seedPhase -= 1f
    seed(i) = seedPhase
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
          kind(i) = kind(count); seed(i) = seed(count)
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
    val pr = r(i); val pg = g(i); val pb = b(i)
    (kind(i): @scala.annotation.switch) match {
      case 1 => // KIND_STREAK — stretched along travel, hot head, cool tail
        val pvx = vx(i); val pvy = vy(i)
        val speed = Math.sqrt(pvx * pvx + pvy * pvy).toFloat
        if (speed < 1f) {
          shapeBatch.fillOvalSoft(x(i), y(i), s, s, pr, pg, pb, a, 0f, 6)
        } else {
          // Streak length grows with speed, floored so a slow particle still reads as a
          // streak and capped so fast debris doesn't smear across the screen
          val len = Math.max(s * 1.6f, Math.min(speed * 0.13f, s * 8f))
          val tx = x(i) - pvx / speed * len
          val ty = y(i) - pvy / speed * len
          shapeBatch.strokeLineSoft(x(i), y(i), tx, ty, s * 1.1f, pr, pg, pb, a * 0.55f)
          shapeBatch.strokeLineSoft(x(i), y(i), x(i) - pvx / speed * len * 0.4f,
            y(i) - pvy / speed * len * 0.4f, s * 0.7f, 1f, 1f, 1f, a * 0.5f)
          shapeBatch.fillOvalSoft(x(i), y(i), s * 0.85f, s * 0.85f, pr, pg, pb, a, 0f, 6)
        }

      case 2 => // KIND_SPARK — twinkling star flare, spins as it dies
        val ang = (seed(i) * 6.2831853f) + (1f - t) * 5f
        shapeBatch.fillStarFlare(x(i), y(i), s * 3.4f, s * 0.85f, ang, 0.45f, pr, pg, pb, a * 0.9f)

      case 3 => // KIND_SMOKE — a three-lobed puff that swells and thins instead of shrinking
        val swell = size(i) * (1.1f + 1.7f * (1f - t))
        val sp = seed(i) * 6.2831853f
        shapeBatch.fillOvalSoft(x(i), y(i), swell, swell * 0.78f, pr, pg, pb, a * 0.55f, 0f, 8)
        shapeBatch.fillOvalSoft(x(i) + Math.cos(sp).toFloat * swell * 0.5f,
          y(i) + Math.sin(sp).toFloat * swell * 0.35f, swell * 0.68f, swell * 0.55f,
          pr, pg, pb, a * 0.4f, 0f, 6)
        shapeBatch.fillOvalSoft(x(i) - Math.cos(sp * 1.7f).toFloat * swell * 0.55f,
          y(i) - Math.sin(sp * 1.7f).toFloat * swell * 0.3f, swell * 0.58f, swell * 0.48f,
          pr, pg, pb, a * 0.34f, 0f, 6)

      case 4 => // KIND_SHARD — spinning chip of debris with a lit edge
        val ang = seed(i) * 6.2831853f + (1f - t) * (6f + seed(i) * 8f)
        val c = Math.cos(ang).toFloat; val sn = Math.sin(ang).toFloat
        // Thin triangle: spin flattens it periodically, which reads as tumbling
        val hw = s * 1.15f; val hh = s * 0.75f
        _triXs(0) = x(i) + c * hw;              _triYs(0) = y(i) + sn * hw * 0.6f
        _triXs(1) = x(i) - c * hw * 0.6f - sn * hh; _triYs(1) = y(i) - sn * hw * 0.36f + c * hh
        _triXs(2) = x(i) - c * hw * 0.6f + sn * hh; _triYs(2) = y(i) - sn * hw * 0.36f - c * hh
        shapeBatch.fillPolygon(_triXs, _triYs, 3, pr, pg, pb, a)
        shapeBatch.strokeLine(_triXs(0), _triYs(0), _triXs(1), _triYs(1), 1f,
          Math.min(1f, pr * 0.4f + 0.6f), Math.min(1f, pg * 0.4f + 0.6f), Math.min(1f, pb * 0.4f + 0.6f), a * 0.8f)

      case _ =>
        if ((flags(i) & 4) != 0) shapeBatch.fillOvalSoft(x(i), y(i), s, s, pr, pg, pb, a, 0f, 8)
        else shapeBatch.fillOval(x(i), y(i), s, s, pr, pg, pb, a, 6)
    }
  }

  /** Emit a ring of particles expanding outward from a center point. */
  def emitRing(cx: Float, cy: Float, count: Int, speed: Float, life: Float,
               r: Float, g: Float, b: Float, alpha: Float, size: Float,
               gravity: Float = 0f, drag: Float = 2f, additive: Boolean = false,
               pkind: Int = KIND_AUTO): Unit = {
    val step = (Math.PI * 2.0 / count).toFloat
    var i = 0
    while (i < count) {
      val angle = i * step
      val vx = Math.cos(angle).toFloat * speed
      val vy = Math.sin(angle).toFloat * speed
      emit(cx, cy, vx, vy, life, r, g, b, alpha, size, gravity, drag, additive,
        shrink = true, soft = true, pkind = pkind)
      i += 1
    }
  }

  /** Clear all particles. */
  def clear(): Unit = {
    count = 0
  }
}
