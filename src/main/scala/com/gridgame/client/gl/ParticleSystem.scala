package com.gridgame.client.gl

import com.gridgame.client.render.IsometricTransform
import com.gridgame.common.model.Tile

import scala.util.Random

/**
 * Ambient particle effects system. Spawns lightweight particles based on
 * map background theme and nearby tile types (lava embers, snow flakes, etc.).
 *
 * ~50-100 active particles at any time for minimal performance impact.
 */
class ParticleSystem {
  private val MAX_PARTICLES = 180
  private val particles = new Array[Particle](MAX_PARTICLES)
  private var activeCount = 0
  private var ambientActiveCount = 0  // category 0
  private var tileActiveCount = 0     // category 1
  private val rng = new Random()

  // Particle pool
  for (i <- particles.indices) particles(i) = new Particle()

  /**
   * Update particles and spawn new ones based on theme/tiles.
   * Call once per frame.
   */
  def update(deltaSec: Double, background: String,
             canvasW: Double, canvasH: Double,
             camOffX: Double, camOffY: Double,
             getTile: (Int, Int) => Tile,
             worldWidth: Int, worldHeight: Int,
             startX: Int, endX: Int, startY: Int, endY: Int): Unit = {
    // Update existing particles — x/y is screen position for ambient,
    // screen-space drift for tile-based
    var i = 0
    while (i < activeCount) {
      val p = particles(i)
      p.x += p.vx * deltaSec.toFloat
      p.y += p.vy * deltaSec.toFloat
      p.life += deltaSec.toFloat
      if (p.life >= p.maxLife) {
        // Decrement category counter
        if (p.category == 0) ambientActiveCount -= 1
        else if (p.category == 1) tileActiveCount -= 1
        // Swap with last active
        activeCount -= 1
        val tmp = particles(i)
        particles(i) = particles(activeCount)
        particles(activeCount) = tmp
      } else {
        i += 1
      }
    }

    // Spawn ambient particles based on background
    val targetAmbient = background match {
      case "sky"       => 30
      case "desert"    => 25
      case "ocean"     => 35
      case "cityscape" => 20
      case _           => 0 // space: stars already rendered
    }

    if (ambientActiveCount < targetAmbient && activeCount < MAX_PARTICLES) {
      background match {
        case "sky"       => spawnDustMote(canvasW, canvasH)
        case "desert"    => spawnSandGrain(canvasW, canvasH)
        case "ocean"     => spawnBubble(canvasW, canvasH)
        case "cityscape" => spawnCityLight(canvasW, canvasH)
        case _ =>
      }
    }

    // Spawn tile-based particles (embers near lava, snowflakes near snow)
    if (tileActiveCount < 30 && activeCount < MAX_PARTICLES) {
      // Sample a few random visible tiles for tile-based particles
      for (_ <- 0 until 3) {
        val wx = startX + rng.nextInt(math.max(1, endX - startX + 1))
        val wy = startY + rng.nextInt(math.max(1, endY - startY + 1))
        if (wx >= 0 && wx < worldWidth && wy >= 0 && wy < worldHeight) {
          val tile = getTile(wx, wy)
          tile.id match {
            case 10 => // Lava — embers
              spawnEmber(wx, wy)
            case 8 => // Snow — snowflakes
              spawnSnowflake(wx, wy)
            case 18 => // Toxic — bubbles
              spawnToxicBubble(wx, wy)
            case 1 | 7 => // Water / DeepWater — sparkle mist
              spawnWaterSparkle(wx, wy)
            case 24 => // Crystal — purple glint
              spawnCrystalGlint(wx, wy)
            case 15 => // EnergyField — electric arcs
              spawnEnergyArc(wx, wy)
            case 20 => // Flowers — drifting petals
              spawnPetal(wx, wy)
            case _ =>
          }
        }
      }
    }
  }

  /**
   * Render all active particles using the provided ShapeBatch.
   * Must be called while shape batch is active.
   * camOffX/camOffY are the current camera offsets, used to project
   * tile-based particle world positions to screen space.
   */
  def render(shapeBatch: ShapeBatch, camOffX: Double, camOffY: Double): Unit = {
    var i = 0
    while (i < activeCount) {
      val p = particles(i)
      val lifeRatio = p.life / p.maxLife
      val alpha = if (lifeRatio < 0.2f) lifeRatio / 0.2f
                  else if (lifeRatio > 0.7f) (1f - lifeRatio) / 0.3f
                  else 1f
      val a = alpha * p.alpha

      if (p.additive) {
        shapeBatch.setAdditiveBlend(true)
      }

      // Ambient particles (category 0): x/y is screen position.
      // Tile particles (category 1): base position from world coords + current camera,
      // with x/y as accumulated screen-space drift from velocity.
      val (drawX, drawY) = if (p.category == 1) {
        val baseX = IsometricTransform.worldToScreenX(p.worldX, p.worldY, camOffX).toFloat
        val baseY = IsometricTransform.worldToScreenY(p.worldX, p.worldY, camOffY).toFloat
        (baseX + p.x, baseY + p.y)
      } else {
        (p.x, p.y)
      }

      val size = p.size * (1f + lifeRatio * p.growthRate)
      val sy = if (p.sizeY > 0f) p.sizeY * (1f + lifeRatio * p.growthRate) else size
      shapeBatch.fillOval(drawX - size * 0.5f, drawY - sy * 0.5f,
        size, sy, p.r, p.g, p.b, a)

      if (p.additive) {
        shapeBatch.setAdditiveBlend(false)
      }
      i += 1
    }
  }

  // ── Ambient particle spawners ──

  private def spawnDustMote(cw: Double, ch: Double): Unit = {
    val p = nextParticle(0)
    if (p == null) return
    p.x = rng.nextFloat() * cw.toFloat
    p.y = rng.nextFloat() * ch.toFloat
    p.vx = (rng.nextFloat() - 0.5f) * 8f
    p.vy = (rng.nextFloat() - 0.5f) * 4f - 2f
    p.size = 0.5f + rng.nextFloat() * 1.5f
    p.maxLife = 3f + rng.nextFloat() * 4f
    p.r = 0.7f; p.g = 0.65f; p.b = 0.5f; p.alpha = 0.25f
    p.category = 0; p.additive = false; p.growthRate = 0f
  }

  private def spawnSandGrain(cw: Double, ch: Double): Unit = {
    val p = nextParticle(0)
    if (p == null) return
    p.x = -5f
    p.y = rng.nextFloat() * ch.toFloat
    p.vx = 15f + rng.nextFloat() * 25f
    p.vy = (rng.nextFloat() - 0.5f) * 6f
    p.size = 0.5f + rng.nextFloat() * 1f
    p.maxLife = 2f + rng.nextFloat() * 3f
    p.r = 0.85f; p.g = 0.78f; p.b = 0.6f; p.alpha = 0.3f
    p.category = 0; p.additive = false; p.growthRate = 0f
  }

  private def spawnBubble(cw: Double, ch: Double): Unit = {
    val p = nextParticle(0)
    if (p == null) return
    p.x = rng.nextFloat() * cw.toFloat
    p.y = ch.toFloat + 5f
    p.vx = (rng.nextFloat() - 0.5f) * 4f
    p.vy = -8f - rng.nextFloat() * 12f
    p.size = 1f + rng.nextFloat() * 3f
    p.maxLife = 3f + rng.nextFloat() * 5f
    p.r = 0.85f; p.g = 0.92f; p.b = 1f; p.alpha = 0.2f
    p.category = 0; p.additive = false; p.growthRate = 0.3f
  }

  private def spawnCityLight(cw: Double, ch: Double): Unit = {
    val p = nextParticle(0)
    if (p == null) return
    p.x = rng.nextFloat() * cw.toFloat
    p.y = rng.nextFloat() * ch.toFloat
    p.vx = (rng.nextFloat() - 0.5f) * 3f
    p.vy = (rng.nextFloat() - 0.5f) * 2f
    p.size = 1f + rng.nextFloat() * 1.5f
    p.maxLife = 2f + rng.nextFloat() * 3f
    val warm = rng.nextFloat()
    p.r = 0.9f + warm * 0.1f; p.g = 0.7f + warm * 0.2f; p.b = 0.4f + warm * 0.2f
    p.alpha = 0.15f
    p.category = 0; p.additive = true; p.growthRate = 0f
  }

  // ── Tile-based particle spawners ──
  // These store world coordinates on the particle. x/y starts as an initial
  // screen-space offset (usually 0) and accumulates velocity drift each frame.
  // At render time, the final screen position = worldToScreen(worldX, worldY) + (x, y).

  private def spawnEmber(wx: Int, wy: Int): Unit = {
    val p = nextParticle(1)
    if (p == null) return
    p.worldX = wx + rng.nextFloat(); p.worldY = wy + rng.nextFloat()
    p.x = 0f; p.y = 0f
    p.vx = (rng.nextFloat() - 0.5f) * 10f
    p.vy = -15f - rng.nextFloat() * 20f
    p.size = 1f + rng.nextFloat() * 1.5f
    p.maxLife = 1f + rng.nextFloat() * 2f
    p.r = 1f; p.g = 0.5f + rng.nextFloat() * 0.4f; p.b = 0.1f; p.alpha = 0.7f
    p.category = 1; p.additive = true; p.growthRate = -0.3f
  }

  private def spawnSnowflake(wx: Int, wy: Int): Unit = {
    val p = nextParticle(1)
    if (p == null) return
    p.worldX = wx + rng.nextFloat(); p.worldY = wy + rng.nextFloat()
    p.x = 0f; p.y = -30f
    p.vx = (rng.nextFloat() - 0.5f) * 8f
    p.vy = 8f + rng.nextFloat() * 12f
    p.size = 1f + rng.nextFloat() * 2f
    p.maxLife = 2f + rng.nextFloat() * 3f
    p.r = 0.95f; p.g = 0.97f; p.b = 1f; p.alpha = 0.5f
    p.category = 1; p.additive = false; p.growthRate = 0f
  }

  private def spawnToxicBubble(wx: Int, wy: Int): Unit = {
    val p = nextParticle(1)
    if (p == null) return
    p.worldX = wx + rng.nextFloat(); p.worldY = wy + rng.nextFloat()
    p.x = 0f; p.y = 0f
    p.vx = (rng.nextFloat() - 0.5f) * 5f
    p.vy = -10f - rng.nextFloat() * 8f
    p.size = 1.5f + rng.nextFloat() * 2f
    p.maxLife = 1.5f + rng.nextFloat() * 2f
    p.r = 0.3f; p.g = 0.9f; p.b = 0.1f; p.alpha = 0.4f
    p.category = 1; p.additive = true; p.growthRate = 0.5f
  }

  private def spawnWaterSparkle(wx: Int, wy: Int): Unit = {
    val p = nextParticle(1)
    if (p == null) return
    p.worldX = wx + rng.nextFloat(); p.worldY = wy + rng.nextFloat()
    p.x = 0f; p.y = 0f
    p.vx = (rng.nextFloat() - 0.5f) * 4f
    p.vy = -6f - rng.nextFloat() * 8f
    p.size = 1f + rng.nextFloat() * 1.5f; p.sizeY = 0f
    p.maxLife = 1.5f + rng.nextFloat() * 2f
    p.r = 0.7f; p.g = 0.85f; p.b = 1f; p.alpha = 0.35f
    p.category = 1; p.additive = true; p.growthRate = 0.2f
  }

  private def spawnCrystalGlint(wx: Int, wy: Int): Unit = {
    val p = nextParticle(1)
    if (p == null) return
    p.worldX = wx + rng.nextFloat(); p.worldY = wy + rng.nextFloat()
    p.x = 0f; p.y = 0f
    p.vx = (rng.nextFloat() - 0.5f) * 6f
    p.vy = -10f - rng.nextFloat() * 10f
    p.size = 1.5f + rng.nextFloat() * 1.5f; p.sizeY = 0.5f + rng.nextFloat() * 0.5f
    p.maxLife = 0.8f + rng.nextFloat() * 1.2f
    p.r = 0.6f; p.g = 0.4f; p.b = 0.9f; p.alpha = 0.6f
    p.category = 1; p.additive = true; p.growthRate = -0.2f
  }

  private def spawnEnergyArc(wx: Int, wy: Int): Unit = {
    val p = nextParticle(1)
    if (p == null) return
    p.worldX = wx + rng.nextFloat(); p.worldY = wy + rng.nextFloat()
    p.x = 0f; p.y = 0f
    p.vx = (rng.nextFloat() - 0.5f) * 20f
    p.vy = (rng.nextFloat() - 0.5f) * 15f
    p.size = 2f + rng.nextFloat() * 2f; p.sizeY = 0.5f + rng.nextFloat() * 0.5f
    p.maxLife = 0.3f + rng.nextFloat() * 0.5f
    p.r = 0.7f; p.g = 0.4f; p.b = 0.95f; p.alpha = 0.7f
    p.category = 1; p.additive = true; p.growthRate = -0.4f
  }

  private def spawnPetal(wx: Int, wy: Int): Unit = {
    val p = nextParticle(1)
    if (p == null) return
    p.worldX = wx + rng.nextFloat(); p.worldY = wy + rng.nextFloat()
    p.x = 0f; p.y = -15f
    p.vx = 3f + rng.nextFloat() * 6f
    p.vy = 4f + rng.nextFloat() * 8f
    p.size = 2f + rng.nextFloat() * 1.5f; p.sizeY = 1f + rng.nextFloat() * 0.5f
    p.maxLife = 2f + rng.nextFloat() * 3f
    val colorRoll = rng.nextFloat()
    if (colorRoll < 0.3f) { p.r = 1f; p.g = 0.6f; p.b = 0.7f }
    else if (colorRoll < 0.6f) { p.r = 1f; p.g = 0.9f; p.b = 0.5f }
    else { p.r = 0.9f; p.g = 0.5f; p.b = 0.9f }
    p.alpha = 0.45f
    p.category = 1; p.additive = false; p.growthRate = 0f
  }

  private def nextParticle(category: Int): Particle = {
    if (activeCount >= MAX_PARTICLES) return null
    val p = particles(activeCount)
    p.life = 0f
    activeCount += 1
    if (category == 0) ambientActiveCount += 1
    else if (category == 1) tileActiveCount += 1
    p
  }

  def dispose(): Unit = {
    activeCount = 0
    ambientActiveCount = 0
    tileActiveCount = 0
  }
}

/** Lightweight particle data. Mutable for pool reuse. */
class Particle {
  var x: Float = 0f       // screen position (ambient) or screen-space drift (tile-based)
  var y: Float = 0f
  var vx: Float = 0f
  var vy: Float = 0f
  var life: Float = 0f
  var maxLife: Float = 1f
  var size: Float = 1f
  var r: Float = 1f
  var g: Float = 1f
  var b: Float = 1f
  var alpha: Float = 1f
  var sizeY: Float = 0f // if > 0, use as vertical size (elongated particles)
  var category: Int = 0  // 0 = ambient, 1 = tile-based
  var additive: Boolean = false
  var growthRate: Float = 0f // size multiplier over lifetime
  var worldX: Float = 0f // world tile coords (tile-based particles only)
  var worldY: Float = 0f
}
