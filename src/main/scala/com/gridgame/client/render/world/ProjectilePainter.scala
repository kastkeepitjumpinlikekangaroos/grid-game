package com.gridgame.client.render
package world

import com.gridgame.common.model._

import com.gridgame.client.game.{FadingProjectile, TerrainImpact}
import com.gridgame.client.render.projectiles.GLProjectileRenderers

/**
 * Projectiles in the frame: each drawn by its type's renderer (GLProjectileRenderers) where the client
 * flies it, with the light it carries; a wall-passer's shadow in the depth pass and its body lifted
 * over everything after it; a stopped one sinking into what it struck.
 */
private[render] final class ProjectilePainter(ctx: RenderContext) {
  import ctx._

  def draw(proj: Projectile): Unit = {
    val px = proj.getX.toDouble
    val py = proj.getY.toDouble
    val sx = worldToScreenX(px, py).toFloat
    val sy = worldToScreenY(px, py).toFloat

    // Dynamic projectile lighting — charge-reactive, distance-boosted
    intToRGB(proj.colorRGB)
    val plr = _rgb_r; val plg = _rgb_g; val plb = _rgb_b
    if (projectileLights) addProjectileLights(proj, sx, sy, plr, plg, plb)

    beginShapes()
    if (ProjectileDef.get(proj.projectileType).passesThroughWalls) {
      // Travels over terrain: its shadow goes down here, in depth order, so a wall in front
      // still covers it; the projectile itself is drawn lifted after all the terrain
      // (drawFlying), so no wall block can slice through it on the way over.
      val lift = GLProjectileRenderers.flyLift(proj, animationTick)
      GLProjectileRenderers.drawFlightShadow(proj, sx, sy - surfaceLift(px, py), shapeBatch, animationTick, lift)
      if (_flyingCount < _flying.length) { _flying(_flyingCount) = proj; _flyingCount += 1 }
    } else {
      // Reuse plr/plg/plb from intToRGB call above (same proj.colorRGB)
      anchorToThrower(proj)
      GLProjectileRenderers.draw(proj, sx, sy, shapeBatch, animationTick, plr, plg, plb)
    }
  }

  /** A pull is drawn tied back to whoever threw it (GLProjectileRenderers' TETHERS): tell it where
    * they are standing, as they are drawn — the camera's position for us, the smoothed one for
    * anyone else. Looked up only for the types that are tethered. */
  private def anchorToThrower(proj: Projectile): Unit = {
    if (!GLProjectileRenderers.wantsAnchor(proj.projectileType)) { GLProjectileRenderers.clearAnchor(); return }
    val owner = proj.ownerId
    if (owner != null && owner.equals(client.getLocalPlayerId)) {
      if (client.getIsDead) GLProjectileRenderers.clearAnchor()
      else GLProjectileRenderers.setAnchor(camera.visualX.toFloat, camera.visualY.toFloat)
    } else if (owner != null && entityCollector.getRemoteVisualPos(owner)) {
      GLProjectileRenderers.setAnchor(entityCollector.lastRVX.toFloat, entityCollector.lastRVY.toFloat)
    } else GLProjectileRenderers.clearAnchor()
  }

  /** A projectile's light: its own colour, charge-reactive and distance-boosted, and a second one
    * in the colour of its element. */
  private def addProjectileLights(proj: Projectile, sx: Float, sy: Float, plr: Float, plg: Float, plb: Float): Unit = {
    val chargeT = proj.chargeLevel / 100f
    // Charge whitening for light color
    val lr = Math.min(1f, plr + chargeT * (1f - plr) * 0.35f)
    val lg = Math.min(1f, plg + chargeT * (1f - plg) * 0.35f)
    val lb = Math.min(1f, plb + chargeT * (1f - plb) * 0.35f)
    // Distance boost
    val pDef = ProjectileDef.get(proj.projectileType)
    val maxR = pDef.effectiveMaxRange(proj.chargeLevel).toFloat
    val lifePct = if (maxR > 0f) Math.min(1f, proj.getDistanceTraveled / maxR) else 0f
    val distBoost = 1f + lifePct * 0.3f
    // Charge-reactive radius (55→125) and intensity (0.15→0.35)
    val lightRadius = (55f + chargeT * 70f) * distBoost
    var lightIntensity = 0.15f + chargeT * 0.2f
    // Boomerang return pulse
    if (proj.isReturning) {
      lightIntensity *= 0.8f + 0.2f * Math.sin(animationTick * 0.5).toFloat
    }
    lightSystem.addLight(sx, sy, lightRadius, lr, lg, lb, lightIntensity)

    // Element-type-specific secondary lighting overlay
    proj.projectileType match {
      // Fire types: warm orange-red glow
      case ProjectileType.FIREBALL | ProjectileType.FLAME_BOLT | ProjectileType.FLAME_BOLT_HEAVY |
           ProjectileType.FLAME_BOLT_LIGHT | ProjectileType.MAGMA_BALL | ProjectileType.ERUPTION |
           ProjectileType.INFERNO_BLAST | ProjectileType.EMBER_SHOT | ProjectileType.NAPALM_STRIKE |
           ProjectileType.FLAME_WAVE | ProjectileType.FLAME_TRAIL | ProjectileType.FLAMBE |
           ProjectileType.EMBER_FAN =>
        lightSystem.addLight(sx, sy, 80f, 1.0f, 0.4f, 0.05f, 0.25f)

      // Ice types: cool blue-white glow
      case ProjectileType.ICE_BEAM | ProjectileType.FROST_SHARD | ProjectileType.FROST_SHARD_LIGHT |
           ProjectileType.FROST_TRAP | ProjectileType.GLACIER_SPIKE | ProjectileType.AVALANCHE_CRUSH |
           ProjectileType.ICE_QUAKE | ProjectileType.ICE_BOULDER =>
        lightSystem.addLight(sx, sy, 60f, 0.4f, 0.7f, 1.0f, 0.18f)

      // Lightning types: bright white-blue with flicker
      case ProjectileType.LIGHTNING | ProjectileType.CHAIN_LIGHTNING | ProjectileType.CHAIN_LIGHTNING_FORK |
           ProjectileType.THUNDER_STRIKE | ProjectileType.TESLA_COIL =>
        val flicker = 0.5f + 0.5f * Math.sin(animationTick * 0.8).toFloat
        lightSystem.addLight(sx, sy, 100f, 0.8f, 0.85f, 1.0f, 0.3f * flicker)

      // Shadow/dark types: deep purple glow
      case ProjectileType.SHADOW_BOLT | ProjectileType.SHADOW_HAUNT | ProjectileType.DEATH_BOLT |
           ProjectileType.CURSE | ProjectileType.SOUL_BOLT | ProjectileType.SOUL_BOLT_HEAVY |
           ProjectileType.HAUNT | ProjectileType.SOUL_DRAIN | ProjectileType.SOUL_HARVEST |
           ProjectileType.DEATH_GRIP | ProjectileType.DRAIN_BLADE | ProjectileType.CHILL_BLADE =>
        lightSystem.addLight(sx, sy, 65f, 0.4f, 0.1f, 0.6f, 0.15f)

      // Holy/light types: warm golden glow
      case ProjectileType.HOLY_BOLT | ProjectileType.HOLY_BOLT_HEAVY | ProjectileType.HOLY_BLADE |
           ProjectileType.SMITE | ProjectileType.STAR_BOLT | ProjectileType.HOLY_NOVA |
           ProjectileType.SHOCKWAVE =>
        lightSystem.addLight(sx, sy, 75f, 1.0f, 0.9f, 0.5f, 0.22f)

      // Poison/venom: sickly green glow
      case ProjectileType.VENOM_BOLT | ProjectileType.VENOM_BOLT_LIGHT | ProjectileType.POISON_DART |
           ProjectileType.POISON_ARROW | ProjectileType.PLAGUE_BOLT | ProjectileType.MIASMA |
           ProjectileType.BLIGHT_BOMB | ProjectileType.ACID_BOMB | ProjectileType.ACID_FLASK |
           ProjectileType.ACID_SPRAY | ProjectileType.POISON_CLOUD | ProjectileType.VENOM_DART |
           ProjectileType.TOXIC_SHURIKEN | ProjectileType.PARALYTIC_STING =>
        lightSystem.addLight(sx, sy, 55f, 0.3f, 0.8f, 0.15f, 0.15f)

      // Water types: ocean blue glow
      case ProjectileType.SPLASH | ProjectileType.TIDAL_WAVE | ProjectileType.GEYSER |
           ProjectileType.SNARE_MINE =>
        lightSystem.addLight(sx, sy, 60f, 0.2f, 0.5f, 0.9f, 0.15f)

      // Void/gravity: dark indigo glow
      case ProjectileType.VOID_BOLT | ProjectileType.GRAVITY_BALL | ProjectileType.GRAVITY_WELL |
           ProjectileType.GRAVITY_LOCK | ProjectileType.GRAVITY_LANCE | ProjectileType.VORTEX_BOMB =>
        lightSystem.addLight(sx, sy, 70f, 0.2f, 0.05f, 0.4f, 0.18f)

      // Explosive: bright orange flash
      case ProjectileType.ROCKET | ProjectileType.GRENADE | ProjectileType.CLUSTER_BOMB |
           ProjectileType.MUD_BOMB =>
        lightSystem.addLight(sx, sy, 90f, 1.0f, 0.7f, 0.3f, 0.25f)

      // Tech/data: neon cyan glow
      case ProjectileType.DATA_BOLT | ProjectileType.VIRUS | ProjectileType.NANO_BOLT |
           ProjectileType.LASER | ProjectileType.LASER_HEAVY | ProjectileType.LASER_LIGHT |
           ProjectileType.RAILGUN | ProjectileType.OVERCLOCK_BEAM =>
        lightSystem.addLight(sx, sy, 55f, 0.1f, 0.9f, 0.6f, 0.15f)

      case _ => // No additional element light for uncategorized projectiles
    }
  }

  // Wall-passing projectiles gathered during the depth pass; their bodies are drawn after it
  private val _flying = new Array[Projectile](256)
  private var _flyingCount = 0

  /** Height of the surface at a world point in virtual px: the top face of an elevated tile
   *  (wall, tree, mountain), 0 on flat ground and pools and off the map. Lets a flier's shadow
   *  ride up onto the wall it is crossing. */
  private def surfaceLift(wx: Double, wy: Double): Float = {
    val world = client.getWorld
    val cx = Math.floor(wx + 0.5).toInt; val cy = Math.floor(wy + 0.5).toInt
    if (cx < 0 || cy < 0 || cx >= world.width || cy >= world.height) return 0f
    val tile = world.getTile(cx, cy)
    if (tile.walkable || (tile.form eq TileForm.Pool)) 0f
    else Math.max(0f, tileCellH - 2f * HH - GLTileRenderer.getTrimTopPx(tile.id, 0))
  }

  /** Bodies of the wall-passing projectiles, lifted, drawn after every wall and entity in
   *  the frame. Their shadows were laid down in the depth pass. */
  def drawFlying(): Unit = {
    if (_flyingCount == 0) return
    beginShapes()
    var i = 0
    while (i < _flyingCount) {
      val proj = _flying(i)
      _flying(i) = null
      val px = proj.getX.toDouble; val py = proj.getY.toDouble
      val sx = worldToScreenX(px, py).toFloat
      val sy = worldToScreenY(px, py).toFloat - GLProjectileRenderers.flyLift(proj, animationTick)
      intToRGB(proj.colorRGB)
      anchorToThrower(proj)
      GLProjectileRenderers.draw(proj, sx, sy, shapeBatch, animationTick, _rgb_r, _rgb_g, _rgb_b)
      i += 1
    }
    _flyingCount = 0
  }

  /** A projectile that has just been stopped: drawn where it stopped, sinking into the
   *  surface and fading, with a puff off whatever it struck (TerrainImpact.FADE_MS long). */
  def drawFading(fp: FadingProjectile): Unit = {
    val t = (frameTimeMs - fp.startMs).toFloat / TerrainImpact.FADE_MS
    if (t >= 1f) return
    val proj = fp.proj
    val px = proj.getX.toDouble; val py = proj.getY.toDouble
    val lift = if (ProjectileDef.get(proj.projectileType).passesThroughWalls)
      GLProjectileRenderers.flyLift(proj, animationTick) else 0f
    val sx = worldToScreenX(px, py).toFloat
    val sy = worldToScreenY(px, py).toFloat - lift
    intToRGB(proj.colorRGB)
    beginShapes()
    anchorToThrower(proj)
    GLProjectileRenderers.drawAbsorbed(proj, sx, sy, shapeBatch, animationTick, Math.max(0f, t),
      fp.hitTerrain, fp.tileColor, _rgb_r, _rgb_g, _rgb_b)
  }
}
