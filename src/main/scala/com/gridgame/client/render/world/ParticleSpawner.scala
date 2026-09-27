package com.gridgame.client.render
package world

import com.gridgame.common.model._

import com.gridgame.client.gl.RenderQuality
import com.gridgame.client.render.projectiles.GLProjectileRenderers

/**
 * What the frame throws into the particle systems: weather over the map, footstep dust, projectile
 * trails, ambient motes, impact sparks and death bursts. Rate-based, so a quality tier scales how many.
 */
private[render] final class ParticleSpawner(ctx: RenderContext) {
  import ctx._

  // Particle tracking
  private var weatherSpawnAccum: Float = 0f
  private var prevLocalVX: Double = Double.NaN
  private var prevLocalVY: Double = Double.NaN
  private var footstepAccum: Float = 0f

  def spawnWeather(background: String, dt: Float): Unit = {
    val w = canvasW.toFloat; val h = canvasH.toFloat
    val rate = (bgType: @scala.annotation.switch) match {
      case 5 => 26f * RenderQuality.particleScale  // snow: flakes falling in front of everything
      case 0 => 12f  // sky: gentle leaf/pollen drift
      case 4 => 20f  // ocean: spray mist
      case 2 => 4f   // space: drifting dust motes
      case 3 => 15f  // desert: sand particles
      case 1 => 8f   // cityscape: dust/ash
      case _ => 8f
    }
    weatherSpawnAccum += rate * dt
    while (weatherSpawnAccum >= 1f) {
      weatherSpawnAccum -= 1f
      (bgType: @scala.annotation.switch) match {
        case 5 => spawnSnowParticle(w, h)
        case 0 => spawnSkyParticle(w, h)
        case 4 => spawnOceanParticle(w, h)
        case 2 => spawnSpaceParticle(w, h)
        case 3 => spawnDesertParticle(w, h)
        case 1 => spawnCityParticle(w, h)
        case _ => spawnSkyParticle(w, h)
      }
    }
  }

  private def spawnSnowParticle(w: Float, h: Float): Unit = {
    // A flake anywhere on screen, so a snowfall is already falling when the match starts, drifting
    // down and a little sideways; small flakes are the far ones, slower and fainter
    val near = rng.nextFloat()
    weatherParticles.emit(
      rng.nextFloat() * (w + 60f) - 30f, rng.nextFloat() * h * 0.9f - 10f,
      -4f + rng.nextFloat() * 10f, 9f + near * 16f,
      plife = 3.5f + rng.nextFloat() * 3f,
      pr = 0.94f, pg = 0.97f, pb = 1f, palpha = 0.6f + near * 0.4f,
      psize = 1f + near * 1.8f,
      shrink = false, soft = true
    )
  }

  private def spawnSkyParticle(w: Float, h: Float): Unit = {
    // Drifting pollen/leaf particles
    val px = rng.nextFloat() * (w + 40) - 20
    weatherParticles.emit(
      px, -5f,
      rng.nextFloat() * 15f - 5f, 12f + rng.nextFloat() * 8f,
      plife = 4f + rng.nextFloat() * 3f,
      pr = 0.95f, pg = 0.95f, pb = 0.85f, palpha = 0.15f + rng.nextFloat() * 0.1f,
      psize = 1.5f + rng.nextFloat() * 1.5f,
      soft = true
    )
  }

  private def spawnOceanParticle(w: Float, h: Float): Unit = {
    // Rising spray/mist
    val px = rng.nextFloat() * w
    val py = h * 0.6f + rng.nextFloat() * h * 0.4f
    weatherParticles.emit(
      px, py,
      rng.nextFloat() * 6f - 3f, -(4f + rng.nextFloat() * 6f),
      plife = 2f + rng.nextFloat() * 2f,
      pr = 0.7f, pg = 0.85f, pb = 0.95f, palpha = 0.08f + rng.nextFloat() * 0.07f,
      psize = 2f + rng.nextFloat() * 3f,
      shrink = false, soft = true, pkind = ParticleSystem.KIND_SMOKE
    )
  }

  private def spawnSpaceParticle(w: Float, h: Float): Unit = {
    // Slow-drifting dust motes
    val px = rng.nextFloat() * w
    val py = rng.nextFloat() * h
    weatherParticles.emit(
      px, py,
      rng.nextFloat() * 4f - 2f, rng.nextFloat() * 2f - 1f,
      plife = 5f + rng.nextFloat() * 4f,
      pr = 0.6f, pg = 0.5f, pb = 0.8f, palpha = 0.06f + rng.nextFloat() * 0.06f,
      psize = 1f + rng.nextFloat() * 1.5f,
      additive = true, soft = true, pkind = ParticleSystem.KIND_SPARK
    )
  }

  private def spawnDesertParticle(w: Float, h: Float): Unit = {
    // Wind-blown sand
    val px = -10f
    val py = h * 0.3f + rng.nextFloat() * h * 0.6f
    weatherParticles.emit(
      px, py,
      30f + rng.nextFloat() * 20f, rng.nextFloat() * 8f - 4f,
      plife = 2.5f + rng.nextFloat() * 2f,
      pr = 0.9f, pg = 0.8f, pb = 0.5f, palpha = 0.08f + rng.nextFloat() * 0.06f,
      psize = 1f + rng.nextFloat() * 1.5f,
      soft = true, pkind = ParticleSystem.KIND_STREAK
    )
  }

  private def spawnCityParticle(w: Float, h: Float): Unit = {
    // Floating dust/ash
    val px = rng.nextFloat() * w
    weatherParticles.emit(
      px, h + 5f,
      rng.nextFloat() * 6f - 3f, -(5f + rng.nextFloat() * 4f),
      plife = 4f + rng.nextFloat() * 3f,
      pr = 0.5f, pg = 0.45f, pb = 0.55f, palpha = 0.07f + rng.nextFloat() * 0.06f,
      psize = 1.2f + rng.nextFloat() * 1.2f,
      shrink = false, soft = true, pkind = ParticleSystem.KIND_SMOKE
    )
  }

  private var ambientSpawnAccum: Float = 0f

  def spawnGameplay(dt: Float): Unit = {
    // Emission is rate-based, so scaling dt scales how many particles a tier spawns.
    val pdt = dt * RenderQuality.particleScale
    spawnFootstepDust(pdt)
    spawnProjectileTrails(pdt)
    spawnAmbientMotes(pdt)
  }

  /** Spawn ambient energy motes floating near the terrain — adds life to the world. */
  private def spawnAmbientMotes(dt: Float): Unit = {
    ambientSpawnAccum += dt * 3f // ~3 particles per second
    while (ambientSpawnAccum >= 1f) {
      ambientSpawnAccum -= 1f
      // Random visible screen position
      val w = canvasW.toFloat; val h = canvasH.toFloat
      val px = rng.nextFloat() * w
      val py = rng.nextFloat() * h * 0.85f + h * 0.05f
      // Gentle floating motion (slowly upward with slight horizontal drift)
      val vxp = rng.nextFloat() * 6f - 3f
      val vyp = -(1.5f + rng.nextFloat() * 3f)
      // Color varies: warm gold, cool cyan, or soft white
      val colorRoll = rng.nextFloat()
      val (mr, mg, mb) = if (colorRoll < 0.35f) (1f, 0.9f, 0.5f) // warm gold
        else if (colorRoll < 0.65f) (0.5f, 0.85f, 1f) // cool cyan
        else (0.9f, 0.9f, 0.95f) // soft white
      combatParticles.emit(
        px, py, vxp, vyp,
        plife = 2.5f + rng.nextFloat() * 2f,
        pr = mr, pg = mg, pb = mb,
        palpha = 0.06f + rng.nextFloat() * 0.06f,
        psize = 1f + rng.nextFloat() * 1.5f,
        pdrag = 0.3f, additive = true, soft = true,
        // A third of them twinkle as tiny flares so the ambience isn't a field of dots
        pkind = if (rng.nextFloat() < 0.33f) ParticleSystem.KIND_SPARK else ParticleSystem.KIND_AUTO
      )
    }
  }

  private def spawnFootstepDust(dt: Float): Unit = {
    val lvx = camera.visualX; val lvy = camera.visualY
    if (prevLocalVX.isNaN) {
      prevLocalVX = lvx; prevLocalVY = lvy
      return
    }
    val dx = lvx - prevLocalVX; val dy = lvy - prevLocalVY
    val moved = Math.sqrt(dx * dx + dy * dy)
    prevLocalVX = lvx; prevLocalVY = lvy

    if (moved > 0.01 && client.getIsMoving) {
      footstepAccum += dt
      if (footstepAccum >= 0.10f) {
        footstepAccum = 0f
        val sx = worldToScreenX(lvx, lvy).toFloat
        val sy = worldToScreenY(lvx, lvy).toFloat
        // Dust puffs at feet — more particles, slightly larger
        var _k = 0
        while (_k < 4) {
          combatParticles.emit(
            sx + rng.nextFloat() * 8f - 4f, sy + rng.nextFloat() * 3f - 1f,
            rng.nextFloat() * 10f - 5f, -(3f + rng.nextFloat() * 5f),
            plife = 0.5f + rng.nextFloat() * 0.35f,
            pr = 0.55f, pg = 0.50f, pb = 0.42f, palpha = 0.28f,
            psize = 2f + rng.nextFloat() * 2f,
            pgravity = 6f, shrink = false, soft = true, pkind = ParticleSystem.KIND_SMOKE
          )
          _k += 1
        }
      }
    }
  }

  private def spawnProjectileTrails(dt: Float): Unit = {
    val projs = client.getProjectiles
    if (projs.isEmpty) return
    val iter = projs.values().iterator()
    while (iter.hasNext) {
      val proj = iter.next()
      val chargeT = proj.chargeLevel / 100f
      // Spawn probability: 35% base → 70% at full charge
      val spawnChance = 0.35f + chargeT * 0.35f
      if (rng.nextFloat() < spawnChance) {
        val sx = worldToScreenX(proj.getX, proj.getY).toFloat
        val sy = worldToScreenY(proj.getX, proj.getY).toFloat -
          (if (ProjectileDef.get(proj.projectileType).passesThroughWalls)
            GLProjectileRenderers.flyLift(proj, animationTick) else 0f)
        intToRGB(proj.colorRGB)
        val pr = _rgb_r; val pg = _rgb_g; val pb = _rgb_b
        // Particle alpha and size scale with charge
        val chgAlpha = 0.30f + chargeT * 0.25f
        val chgSize = 2f + rng.nextFloat() * 1.5f + chargeT * 2f
        // Brighter trails for charged projectiles
        val bright = 0.3f + chargeT * 0.2f
        // Core trail particle — a soft ember of the projectile's own colour
        combatParticles.emit(
          sx + rng.nextFloat() * 4f - 2f, sy + rng.nextFloat() * 2f - 1f,
          rng.nextFloat() * 4f - 2f, rng.nextFloat() * 4f - 2f,
          plife = 0.3f + rng.nextFloat() * 0.2f,
          pr = clamp(pr * (1f - bright) + bright), pg = clamp(pg * (1f - bright) + bright), pb = clamp(pb * (1f - bright) + bright),
          palpha = chgAlpha,
          psize = chgSize,
          additive = true, soft = true
        )
        // High-charge radial sparks (>60%): shoot outward with gravity
        if (chargeT > 0.6f && rng.nextFloat() < (chargeT - 0.5f) * 0.8f) {
          val angle = rng.nextFloat() * Math.PI.toFloat * 2f
          val speed = 20f + rng.nextFloat() * 30f
          combatParticles.emit(
            sx, sy,
            Math.cos(angle).toFloat * speed, Math.sin(angle).toFloat * speed - 8f,
            plife = 0.2f + rng.nextFloat() * 0.15f,
            pr = clamp(pr * 0.4f + 0.6f), pg = clamp(pg * 0.4f + 0.6f), pb = clamp(pb * 0.4f + 0.6f),
            palpha = 0.4f + chargeT * 0.2f,
            psize = 1.2f + rng.nextFloat() * 1.2f,
            pgravity = 15f, pkind = ParticleSystem.KIND_STREAK
          )
        }
        // Boomerang pulsing soft particles when returning
        if (proj.isReturning && rng.nextFloat() < 0.5f) {
          combatParticles.emit(
            sx + rng.nextFloat() * 6f - 3f, sy + rng.nextFloat() * 4f - 2f,
            rng.nextFloat() * 2f - 1f, rng.nextFloat() * 2f - 1f,
            plife = 0.3f + rng.nextFloat() * 0.2f,
            pr = clamp(pr * 0.5f + 0.5f), pg = clamp(pg * 0.5f + 0.5f), pb = clamp(pb * 0.5f + 0.5f),
            palpha = 0.35f,
            psize = 2.5f + rng.nextFloat() * 2.5f,
            soft = true
          )
        }
      }
    }
  }

  /** Spawn impact sparks when a player takes damage (called from detectDamageNumbers). */
  def spawnImpactSparks(worldX: Float, worldY: Float, cr: Float, cg: Float, cb: Float): Unit = {
    val sx = worldToScreenX(worldX, worldY).toFloat
    val sy = worldToScreenY(worldX, worldY).toFloat - 12f
    var _k = 0
    while (_k < 8) {
      val angle = rng.nextFloat() * Math.PI.toFloat * 2f
      val speed = 25f + rng.nextFloat() * 35f
      // Fast debris streaks, punctuated by twinkling star flares
      val kind = if ((_k & 1) == 0) ParticleSystem.KIND_STREAK else ParticleSystem.KIND_SPARK
      combatParticles.emit(
        sx, sy,
        Math.cos(angle).toFloat * speed, Math.sin(angle).toFloat * speed - 10f,
        plife = 0.2f + rng.nextFloat() * 0.2f,
        pr = clamp(cr * 0.5f + 0.5f), pg = clamp(cg * 0.5f + 0.5f), pb = clamp(cb * 0.5f + 0.5f),
        palpha = 0.7f,
        psize = 1.5f + rng.nextFloat() * 1.5f,
        pgravity = 60f, additive = true, pkind = kind
      )
      _k += 1
    }
    // Fragments knocked loose — non-additive so they stay dark against the flash
    _k = 0
    while (_k < 3) {
      val angle = rng.nextFloat() * Math.PI.toFloat * 2f
      val speed = 18f + rng.nextFloat() * 22f
      combatParticles.emit(
        sx, sy,
        Math.cos(angle).toFloat * speed, Math.sin(angle).toFloat * speed - 18f,
        plife = 0.3f + rng.nextFloat() * 0.25f,
        pr = cr * 0.7f, pg = cg * 0.7f, pb = cb * 0.7f,
        palpha = 0.55f,
        psize = 1.4f + rng.nextFloat() * 1.2f,
        pgravity = 110f, shrink = false, pkind = ParticleSystem.KIND_SHARD
      )
      _k += 1
    }
  }

  /** Spawn a burst of particles on player death. */
  def spawnDeathBurst(worldX: Float, worldY: Float, colorRGB: Int): Unit = {
    val sx = worldToScreenX(worldX, worldY).toFloat
    val sy = worldToScreenY(worldX, worldY).toFloat - 12f
    intToRGB(colorRGB)
    val cr = _rgb_r; val cg = _rgb_g; val cb = _rgb_b
    var _k = 0
    while (_k < 20) {
      val angle = rng.nextFloat() * Math.PI.toFloat * 2f
      val speed = 15f + rng.nextFloat() * 45f
      val bright = rng.nextFloat()
      val pr = if (bright > 0.6f) 1f else cr
      val pg = if (bright > 0.6f) 1f else cg
      val pb = if (bright > 0.6f) 0.9f else cb
      // Bright bits fling out as star flares, the rest as streaks of ejecta
      val kind = if (bright > 0.6f) ParticleSystem.KIND_SPARK else ParticleSystem.KIND_STREAK
      combatParticles.emit(
        sx + rng.nextFloat() * 4f - 2f, sy + rng.nextFloat() * 4f - 2f,
        Math.cos(angle).toFloat * speed, Math.sin(angle).toFloat * speed * 0.7f - 15f,
        plife = 0.5f + rng.nextFloat() * 0.6f,
        pr = pr, pg = pg, pb = pb,
        palpha = 0.6f,
        psize = 2f + rng.nextFloat() * 2.5f,
        pgravity = 40f, additive = true, pkind = kind
      )
      _k += 1
    }
    // Tumbling debris and a smoke column left behind — gives the burst weight and an aftermath
    _k = 0
    while (_k < 6) {
      val angle = rng.nextFloat() * Math.PI.toFloat * 2f
      val speed = 20f + rng.nextFloat() * 30f
      combatParticles.emit(
        sx, sy,
        Math.cos(angle).toFloat * speed, Math.sin(angle).toFloat * speed * 0.6f - 30f,
        plife = 0.6f + rng.nextFloat() * 0.5f,
        pr = cr * 0.75f, pg = cg * 0.75f, pb = cb * 0.75f,
        palpha = 0.6f,
        psize = 1.8f + rng.nextFloat() * 1.6f,
        pgravity = 130f, shrink = false, pkind = ParticleSystem.KIND_SHARD
      )
      _k += 1
    }
    _k = 0
    while (_k < 5) {
      combatParticles.emit(
        sx + rng.nextFloat() * 10f - 5f, sy + rng.nextFloat() * 6f - 3f,
        rng.nextFloat() * 10f - 5f, -(8f + rng.nextFloat() * 12f),
        plife = 0.8f + rng.nextFloat() * 0.7f,
        pr = 0.32f, pg = 0.30f, pb = 0.34f,
        palpha = 0.30f,
        psize = 4f + rng.nextFloat() * 3f,
        pdrag = 0.8f, shrink = false, pkind = ParticleSystem.KIND_SMOKE
      )
      _k += 1
    }
  }

  def reset(): Unit = {
    prevLocalVX = Double.NaN
    prevLocalVY = Double.NaN
  }
}
