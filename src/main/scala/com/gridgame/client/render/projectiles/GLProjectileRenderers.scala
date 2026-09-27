package com.gridgame.client.render.projectiles

import com.gridgame.common.model.{Projectile, ProjectileDef, ProjectileType}
import com.gridgame.client.gl.ShapeBatch
import ProjectileKit._
import AoeRings._
import Beams._
import Bites._
import Blades._
import Bolts._
import Chains._
import Electricity._
import Grabs._
import Heavies._
import Lobbed._
import Missiles._
import Orbs._
import Shafts._
import Tethers._
import Undead._
import Waves._

/**
 * How every projectile type is drawn: the registry below maps each type to a renderer, most of them
 * made by a family's factory with a colour, a size and a kind, the rest one-offs. The families, a
 * file each in this package, are Orbs, Blades, Shafts, Lobbed, AoeRings, Chains, Missiles, Waves,
 * Bolts, Beams, Tethers, Grabs, Electricity, Heavies, Undead and Bites; what they share is in
 * ProjectileKit and Silhouettes.
 *
 * A renderer is handed the projectile's hitbox in screen space and draws the thing that hits there,
 * anything elongated trailing behind it. Standard alpha blending: the bloom supplies the glow.
 */
object GLProjectileRenderers {

  /**
   * Draws one projectile at screen position (sx, sy). A single-method trait rather than a
   * Function5: scala.Function5 isn't specialized, so calling one boxed both coordinates and
   * the tick — three allocations per projectile per frame. Factory lambdas convert to it as
   * they are; a method goes through [[asRenderer]].
   */
  trait Renderer { def apply(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit }

  /** A method as a [[Renderer]], without an intermediate Function5. */
  private def asRenderer(f: Renderer): Renderer = f

  /** Look up renderer by projectile type. Returns null if none registered. No Option allocation. */
  def getRenderer(pType: Byte): Renderer = _rendererLUT(pType & 0xFF)

  // ── Where the thrower of a pull stands, for its tether (see Tethers) ──

  def setAnchor(wx: Float, wy: Float): Unit = Tethers.setAnchor(wx, wy)
  def clearAnchor(): Unit = Tethers.clearAnchor()
  /** Is this type drawn tied to its thrower, so the renderer should say where they stand? */
  def wantsAnchor(pType: Byte): Boolean = Tethers.wantsAnchor(pType)

  /** A skeletal hand clawing up out of the ground: the Grasping Dead's, and a blast's. */
  private[render] def deadHand(sb: ShapeBatch, x: Float, y: Float, rise: Float, lean: Float, open: Float,
                               a: Float, s: Float): Unit = Grabs.deadHand(sb, x, y, rise, lean, open, a, s)

  /** Draw a projectile with its registered renderer, or the generic fallback. */
  def draw(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int,
           r: Float, g: Float, b: Float): Unit = {
    val rend = getRenderer(proj.projectileType)
    if (rend != null) rend(proj, sx, sy, sb, tick) else drawGeneric(proj, sx, sy, sb, tick, r, g, b)
  }

  // ═══════════════════════════════════════════════════════════════
  //  TERRAIN: FLYING OVER IT, AND BEING STOPPED BY IT
  // ═══════════════════════════════════════════════════════════════

  /** How far above its ground point a projectile that travels over terrain
   *  (passesThroughWalls) is drawn. At ground height those were drawn in the same depth pass
   *  as the wall blocks, so each wall they crossed sliced through them. Lifted, drawn after
   *  the terrain, with a shadow below, they read as flying over it. */
  val FLY_ALT = 20f

  /** Current lift of a flier: FLY_ALT with a slow bob, so the height reads as altitude and
   *  not as a draw offset. */
  def flyLift(proj: Projectile, tick: Int): Float =
    FLY_ALT + Math.sin((tick + proj.id * 13) * 0.08).toFloat * 2.2f

  /** The shadow under a flier. `groundY` is the surface it is over — the top face of a wall
   *  block when it is crossing one — so the shadow rides up onto the wall it clears. */
  def drawFlightShadow(proj: Projectile, sx: Float, groundY: Float, sb: ShapeBatch, tick: Int, lift: Float): Unit = {
    val k = 1f - (lift - FLY_ALT) * 0.05f
    // Dark enough to read on a dark wall top: the gap between shadow and body is what
    // says "altitude", so a shadow that disappears makes the lift read as an offset.
    sb.fillOvalSoft(sx, groundY + 2f, 14f * k, 6f * k, 0f, 0f, 0f, 0.5f, 0f, 12)
    sb.fillOval(sx, groundY + 2f, 7.5f * k, 3.2f * k, 0f, 0f, 0f, 0.42f, 10)
  }

  /**
   * A projectile that has just been stopped, `t` running 0 (the moment it stopped) to 1
   * (gone). It sinks into the surface — shrinking toward the point of impact while it
   * fades — and, when it hit terrain rather than running out of range, throws a puff off
   * the face in the colour of what it struck: grey chips off a wall, spray off water, dust
   * off the map edge.
   */
  def drawAbsorbed(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int, t: Float,
                   hitTerrain: Boolean, tileColor: Int, r: Float, g: Float, b: Float): Unit = {
    val fade = (1f - t) * (1f - t)
    if (fade > 0.01f) {
      sb.setAlphaMultiplier(fade)
      sb.setScaleAbout(sx, sy, 1f - t * 0.6f)
      draw(proj, sx, sy, sb, tick, r, g, b)
      sb.resetModifiers()
    }
    if (!hitTerrain) return
    val tr = if (tileColor == 0) 0.55f else ((tileColor >> 16) & 0xFF) / 255f
    val tg = if (tileColor == 0) 0.56f else ((tileColor >> 8) & 0xFF) / 255f
    val tb = if (tileColor == 0) 0.62f else (tileColor & 0xFF) / 255f
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    // Contact flash, flattened against the surface
    if (t < 0.4f) {
      val ft = t / 0.4f
      sb.fillOvalSoft(sx, sy, 15f * (0.6f + ft), 7f * (0.6f + ft),
        mix(r, 1f, 0.6f), mix(g, 1f, 0.6f), mix(b, 1f, 0.6f), 0.85f * (1f - ft), 0f, 12)
    }
    // A ring spreading across the face
    val rr = 5f + t * 18f
    sb.strokeOval(sx, sy, rr, rr * 0.5f, 2.6f * (1f - t), mix(tr, 1f, 0.25f), mix(tg, 1f, 0.25f),
      mix(tb, 1f, 0.25f), 0.75f * (1f - t), 16)
    // Debris kicked back off the face, against the direction of travel
    val back = Math.atan2(-ndy, -ndx)
    var i = 0
    while (i < 6) {
      val a = back + (i - 2.5) * 0.42 + ((proj.id * 7 + i * 13) % 5) * 0.06
      val d = 3f + t * (13f + (i % 3) * 5f)
      val hop = Math.sin(t * Math.PI).toFloat * (6f + (i % 2) * 4f)
      val px = sx + Math.cos(a).toFloat * d
      val py = sy + Math.sin(a).toFloat * d * 0.6f - hop
      val ps = (2.6f - (i % 3) * 0.4f) * (1f - t * 0.5f)
      sb.fillOval(px, py, ps + 0.8f, ps * 0.85f + 0.8f, tr * 0.35f, tg * 0.35f, tb * 0.35f, 0.6f * (1f - t), 6)
      sb.fillOval(px, py, ps, ps * 0.85f, tr, tg, tb, 0.9f * (1f - t), 6)
      i += 1
    }
  }

  // ═══════════════════════════════════════════════════════════════
  //  PRE-ALLOCATED ARRAY POOLS (avoid per-frame GC pressure)
  // ═══════════════════════════════════════════════════════════════

  def drawGeneric(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int,
                  r: Float, g: Float, b: Float): Unit = {
    val phase = (tick + proj.id * 37) * 0.35
    // Dynamics must be computed BEFORE reading _stPulseMult/dynAlpha — they live in
    // shared mutable state, so reading them first picked up the previously drawn
    // projectile's charge and dissipation values.
    computeAllDynamics(proj, r, g, b, phase)
    val p = (0.9 + 0.1 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val dr = _evoR; val dg = _evoG; val db = _evoB
    val ds = dynScale
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy

    // Speed lines
    drawSpeedLines(sx, sy, ndx, ndy, dr, dg, db, 0.25f * p, sb, 4 + (_lifePct * 2).toInt, 28f * ds)
    // Soft glow halo
    sb.fillOvalSoft(sx, sy, 36f * ds * dynGlow, 28.8f * ds * dynGlow, dr, dg, db, 0.38f * p, 0f, 18)
    // Dark cartoon outline
    sb.strokeOval(sx, sy, 24f * ds, 18f * ds, outlineW(24f * ds), outline(r), outline(g), outline(b), 0.8f * p, 14)
    // Core
    sb.fillOval(sx, sy, 22f * ds, 17f * ds, dr, dg, db, 0.95f * p, 14)
    // Cartoon highlight — offset scales with the body so it stays put when charged
    sb.fillOval(sx + KEY_LIGHT_X * 7f * ds, sy + KEY_LIGHT_Y * 5f * ds, 7f * ds, 5f * ds, 1f, 1f, 1f, 0.35f * p, 8)
    // Bright center — charge whitening
    val bc = _chgBright
    sb.fillOval(sx, sy, 10f * ds, 7f * ds,
      mix(bright(r), 1f, bc), mix(bright(g), 1f, bc), mix(bright(b), 1f, bc), 0.98f * dynAlpha, 10)
    // Trail — scaled by dynTrail
    val trailLen = 45f * dynTrail
    var i = 0; while (i < 8) {
      val t = ((tick * 0.05 + i * 0.125 + proj.id * 0.13) % 1.0).toFloat
      val taper = 1f - t * 0.65f
      val s = 14f * taper * ds
      val colorFade = 1f - t * 0.3f
      sb.fillOval(sx - ndx * t * trailLen, sy - ndy * t * trailLen, s, s * 0.7f,
        r * colorFade, g * colorFade, b * colorFade, 0.45f * (1f - t) * p, 8)
    ; i += 1 }
    // Charge crackle
    drawChargeCrackle(sx, sy, 22f * ds, r, g, b, p, sb, phase, proj.chargeLevel)
    // Boomerang return ghosts
    drawReturnGhosts(sx, sy, 22f * ds, dr, dg, db, p, sb, proj)
  }

  // ═══════════════════════════════════════════════════════════════
  //  HELPERS
  // ═══════════════════════════════════════════════════════════════

  // ═══════════════════════════════════════════════════════════════
  //  DYNAMIC VISUAL HELPERS (charge, lifetime, color, state)
  // ═══════════════════════════════════════════════════════════════

  // ═══════════════════════════════════════════════════════════════
  //  WEAPON / OBJECT SILHOUETTES
  // ═══════════════════════════════════════════════════════════════
  //
  // A thrown object is authored once in a local frame — +x along the object, +y across
  // it, both roughly within [-1.3, 1.3] — and stamped through `blitPart`, which rotates
  // it, squashes y into the isometric ground plane and scales it to the projectile's
  // size.
  //
  // This exists because the old `spinner` built its outline from a polar radius per
  // vertex, which can only ever describe a star: an axe, a katana, a femur and a playing
  // card all came out as the same spinning lens. A silhouette in a local frame can carry
  // a haft at one end and a head at the other, so it still reads as an axe at every spin
  // angle — which is the whole point of giving a character a themed weapon.
  //
  // Parts must be convex: ShapeBatch.fillPolygon fan-triangulates from vertex 0. Curved
  // blades are therefore split into two convex spans rather than described in one loop.

  // ═══════════════════════════════════════════════════════════════
  //  PATTERN FACTORIES
  // ═══════════════════════════════════════════════════════════════

  // ── Shared pieces of the orbs and the electricity ──

  // ═══════════════════════════════════════════════════════════════
  //  TIER 1: NEW SPECIALIZED RENDERERS
  // ═══════════════════════════════════════════════════════════════

  // ═══════════════════════════════════════════════════════════════
  //  HEAD-AT-HITBOX RENDERERS
  // ═══════════════════════════════════════════════════════════════
  //
  // Everything in this section replaces a renderer that drew its body as a line from the
  // projectile FORWARD to a point `worldLen` world units ahead of it (beamTip) and capped
  // that point with a disc — beams, the charge shot, the tentacle, tethers, lightning, the
  // rocket, the shark jaw. Two things were wrong with that:
  //
  //  - A stroked line with a ball on the end is the silhouette of a snake, not of an
  //    ability, and the whip and vine styles wiggled along their length so they slithered.
  //  - The hitbox is at (sx, sy), the BACK of that line. The disc that read as the
  //    projectile's head arrived 100-150px before the damage did.
  //
  // So each of these puts the thing that hits at (sx, sy), and anything elongated trails
  // BEHIND it through `fadeLine`, which dissolves instead of ending in a hard edge.

  // ═══════════════════════════════════════════════════════════════
  //  TETHERS: A PULL IS TIED TO WHOEVER IS PULLING
  // ═══════════════════════════════════════════════════════════════
  //
  // A grab that flies free — a hand, a paw, a claw with nothing behind it — reads as a glove
  // somebody threw, and that is what the old ones were: a brown mitten on a box-shaped cuff for
  // the Bear, the Gorilla and the Griffin, and a round knot with three sausage fingers for the
  // Kraken, the Spaceman, the Druid, the Thornweaver, the Treant, the Death Knight and the
  // Gravedigger. Every pull is drawn tied back to its thrower instead: a tentacle out of the
  // Kraken, a vine out of the Druid, a leash of spirit out of the Bear, a chain out of the Death
  // Knight, a rope out of the Gladiator's hand. GLGameRenderer says where the thrower is standing
  // (setAnchor); when it can't — they have died, or it is a dev tool with no players in it — the
  // tether runs back to where the projectile started (Projectile.originX/Y). The thing that hits
  // is still at (sx, sy); the tether only trails it, so it never arrives before the damage does.

  // ═══════════════════════════════════════════════════════════════
  //  REGISTRY (all 176 types)
  // ═══════════════════════════════════════════════════════════════

  private val registry: Map[Byte, Renderer] = Map(
    // ── Original (0-30) ──
    ProjectileType.NORMAL       -> asRenderer(drawNormal),
    ProjectileType.TENTACLE     -> tether(TETH_TENTACLE, 0.24f, 0.74f, 0.44f),
    ProjectileType.ICE_BEAM     -> (drawFrostComet _),
    ProjectileType.AXE          -> bladeSpinner(WPN_AXE, 0.78f, 0.70f, 0.55f, 30f),
    ProjectileType.ROPE         -> chainProj(CHN_ROPE, 0.72f, 0.54f, 0.30f, 6f),
    ProjectileType.SPEAR        -> flyingShaft(SHF_SPEAR, 0.95f, 0.82f, 0.42f, 28f),
    ProjectileType.SOUL_BOLT    -> energyBolt(0.30f, 0.84f, 0.82f, 20f, ORB_SPIRIT),
    ProjectileType.HAUNT        -> energyBolt(0.5f, 0.7f, 0.95f, 24f, ORB_SPIRIT),
    ProjectileType.ARCANE_BOLT  -> energyBolt(0.65f, 0.3f, 0.95f, 22f, ORB_RUNE),
    ProjectileType.FIREBALL     -> energyBolt(1f, 0.46f, 0.08f, 28f, ORB_FIRE),
    ProjectileType.SPLASH       -> aoeRing(AOE_WATER, 0.32f, 0.62f, 1f, 46f),
    ProjectileType.TIDAL_WAVE   -> wave(WAV_WATER, 0.35f, 0.66f, 1f, 40f),
    ProjectileType.GEYSER       -> asRenderer(drawGeyser),
    ProjectileType.BULLET       -> bulletProj(0.75f, 0.7f, 0.55f),
    ProjectileType.GRENADE      -> lobbed(LOB_BOMB, 0.95f, 0.62f, 0.25f, 17f),
    ProjectileType.ROCKET       -> asRenderer(drawRocket),
    ProjectileType.TALON        -> asRenderer(drawTalon),
    ProjectileType.GUST         -> wave(WAV_WIND, 0.82f, 0.92f, 1f, 33f),
    ProjectileType.SHURIKEN     -> shuriken(0.72f, 0.74f, 0.82f, 30f),
    ProjectileType.POISON_DART  -> flyingShaft(SHF_DART, 0.4f, 0.9f, 0.35f, 26f),
    ProjectileType.CHAIN_BOLT   -> chainProj(CHN_SHACKLE, 0.62f, 0.64f, 0.72f, 5.5f),
    ProjectileType.LOCKDOWN_CHAIN -> chainProj(CHN_LOCK, 0.52f, 0.54f, 0.62f, 6f),
    ProjectileType.SNARE_MINE   -> lobbed(LOB_MINE, 0.35f, 0.7f, 1f, 17f),
    ProjectileType.KATANA       -> bladeSpinner(WPN_KATANA, 0.78f, 0.84f, 0.95f, 32f),
    ProjectileType.SWORD_WAVE   -> asRenderer(drawSwordWave),
    ProjectileType.PLAGUE_BOLT  -> energyBolt(0.52f, 0.82f, 0.12f, 20f, ORB_TOXIC),
    ProjectileType.MIASMA       -> aoeRing(AOE_TOXIC, 0.42f, 0.72f, 0.18f, 42f),
    ProjectileType.BLIGHT_BOMB  -> lobbed(LOB_FLASK, 0.45f, 0.8f, 0.2f, 17f),
    ProjectileType.BLOOD_FANG   -> asRenderer(drawBloodFang),
    ProjectileType.BLOOD_SIPHON -> siphonVortex(SIPH_BLOOD, 0.88f, 0.12f, 0.14f, 19f),
    ProjectileType.BAT_SWARM    -> asRenderer(drawBatSwarm),

    // ── Elemental (31-52) ──
    ProjectileType.FLAME_BOLT   -> energyBolt(1f, 0.5f, 0.1f, 20f, ORB_FIRE),
    ProjectileType.FROST_SHARD  -> flyingShaft(SHF_ICE, 0.55f, 0.85f, 1f, 22f),
    ProjectileType.LIGHTNING    -> lightningBolt(1f, 0.90f, 0.30f),
    ProjectileType.CHAIN_LIGHTNING -> lightningBolt(0.95f, 0.85f, 0.35f, heavy = true),
    ProjectileType.THUNDER_STRIKE -> asRenderer(drawThunderStrike),
    ProjectileType.BOULDER      -> asRenderer((proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int) => drawBoulder(proj, sx, sy, sb, tick, 24f)),
    ProjectileType.SEISMIC_SLAM -> aoeRing(AOE_QUAKE, 0.68f, 0.50f, 0.28f, 50f),
    ProjectileType.WIND_BLADE   -> wave(WAV_WIND, 0.78f, 0.96f, 1f, 31f),
    ProjectileType.MAGMA_BALL   -> energyBolt(1f, 0.45f, 0.05f, 24f, ORB_FIRE),
    ProjectileType.ERUPTION     -> aoeRing(AOE_FIRE, 1f, 0.45f, 0.05f, 46f),
    ProjectileType.FROST_TRAP   -> lobbed(LOB_ICE, 0.62f, 0.85f, 1f, 17f),
    ProjectileType.SAND_SHOT    -> energyBolt(0.9f, 0.75f, 0.4f, 18f, ORB_SAND),
    ProjectileType.SAND_BLAST   -> wave(WAV_SAND, 0.88f, 0.76f, 0.42f, 30f),
    ProjectileType.THORN        -> flyingShaft(SHF_THORN, 0.42f, 0.75f, 0.28f, 26f),
    ProjectileType.VINE_WHIP    -> tether(TETH_VINE, 0.30f, 0.62f, 0.20f),
    ProjectileType.THORN_WALL   -> aoeRing(AOE_NATURE, 0.30f, 0.62f, 0.18f, 34f),
    ProjectileType.INFERNO_BLAST -> asRenderer(drawInfernoBlast),
    ProjectileType.GLACIER_SPIKE -> flyingShaft(SHF_ICE, 0.60f, 0.88f, 1f, 27f),
    ProjectileType.MUD_GLOB     -> energyBolt(0.4f, 0.3f, 0.15f, 22f, ORB_MUD),
    ProjectileType.MUD_BOMB     -> lobbed(LOB_GLOB, 0.40f, 0.30f, 0.14f, 18f),
    ProjectileType.EMBER_SHOT   -> energyBolt(1f, 0.55f, 0.15f, 18f, ORB_FIRE),
    ProjectileType.AVALANCHE_CRUSH -> lobbed(LOB_ICE, 0.72f, 0.85f, 0.98f, 40f),

    // ── Undead/Dark (53-69) ──
    ProjectileType.DEATH_BOLT   -> energyBolt(0.36f, 0.16f, 0.52f, 22f, ORB_ORBIT),
    ProjectileType.RAISE_DEAD   -> asRenderer(drawRaiseDead),
    ProjectileType.BONE_AXE     -> bladeSpinner(WPN_BONE_AXE, 0.94f, 0.92f, 0.84f, 30f),
    ProjectileType.BONE_THROW   -> bladeSpinner(WPN_BONE, 0.94f, 0.92f, 0.84f, 24f, 0.38),
    ProjectileType.WAIL         -> asRenderer(drawWail),
    ProjectileType.SOUL_DRAIN   -> siphonVortex(SIPH_SOUL, 0.40f, 0.95f, 0.55f, 19f),
    ProjectileType.CLAW_SWIPE   -> asRenderer(drawClawSwipe),
    ProjectileType.DEVOUR       -> asRenderer(drawDevour),
    ProjectileType.SCYTHE       -> asRenderer(drawScythe),
    ProjectileType.REAP         -> asRenderer(drawReap),
    ProjectileType.SHADOW_BOLT  -> asRenderer(drawShadowBolt),
    ProjectileType.CURSED_BLADE -> bladeSpinner(WPN_CURSED, 0.90f, 0.18f, 0.28f, 30f),
    ProjectileType.LIFE_DRAIN   -> siphonVortex(SIPH_LIFE, 0.80f, 0.14f, 0.30f, 19f),
    ProjectileType.SHOVEL       -> lobbed(LOB_SHOVEL, 0.62f, 0.64f, 0.62f, 26f),
    ProjectileType.HEAD_THROW   -> lobbed(LOB_HORN, 0.86f, 0.80f, 0.68f, 22f),
    ProjectileType.BANDAGE_WHIP -> (drawBandageWad _),
    ProjectileType.CURSE        -> asRenderer(drawCurse),

    // ── Medieval/Fantasy (70-79) ──
    ProjectileType.HOLY_BLADE   -> bladeSpinner(WPN_SWORD, 1f, 0.92f, 0.45f, 31f),
    ProjectileType.HOLY_BOLT    -> asRenderer(drawHolyBolt),
    ProjectileType.ARROW        -> flyingShaft(SHF_ARROW, 0.95f, 0.72f, 0.32f, 25f),
    ProjectileType.POISON_ARROW -> flyingShaft(SHF_PARROW, 0.4f, 0.9f, 0.32f, 25f),
    ProjectileType.SONIC_WAVE   -> wave(WAV_SONIC, 0.78f, 0.58f, 0.97f, 33f),
    ProjectileType.SONIC_BOOM   -> aoeRing(AOE_SONIC, 0.78f, 0.58f, 0.97f, 50f),
    ProjectileType.FIST         -> fistProj(0.75f, 0.6f, 0.45f, 28f),
    ProjectileType.SMITE        -> asRenderer(drawHolyBolt),
    ProjectileType.CHARM        -> energyBolt(1f, 0.38f, 0.62f, 18f, ORB_HEART),
    ProjectileType.CARD         -> bladeSpinner(WPN_CARD, 0.92f, 0.25f, 0.32f, 26f, 0.22),

    // ── Sci-Fi/Tech (80-89) ──
    ProjectileType.DATA_BOLT    -> asRenderer((proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int) => drawDataBolt(proj, sx, sy, sb, tick, isVirus = false)),
    ProjectileType.VIRUS        -> asRenderer((proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int) => drawDataBolt(proj, sx, sy, sb, tick, isVirus = true)),
    ProjectileType.LASER        -> laserBolt(BOLT_LASER, 1f, 0.25f, 0.2f, 40f, 9f),
    ProjectileType.GRAVITY_BALL -> asRenderer(drawGravityBall),
    ProjectileType.GRAVITY_WELL -> asRenderer(drawGravityBall),
    ProjectileType.TESLA_COIL   -> lightningBolt(0.35f, 0.85f, 1f),
    ProjectileType.NANO_BOLT    -> energyBolt(0.25f, 0.85f, 0.65f, 16f, ORB_SWARM),
    ProjectileType.VOID_BOLT    -> asRenderer(drawVoidBolt),
    ProjectileType.RAILGUN      -> railSlug(0.35f, 0.65f, 1f, 14f),
    ProjectileType.CLUSTER_BOMB -> lobbed(LOB_BOMB, 0.9f, 0.66f, 0.3f, 19f),

    // ── Nature/Beast (90-93) ──
    ProjectileType.VENOM_BOLT   -> asRenderer(drawVenomBolt),
    ProjectileType.WEB_SHOT     -> asRenderer(drawWebShot),
    ProjectileType.STINGER      -> asRenderer(drawStinger),
    ProjectileType.ACID_BOMB    -> asRenderer(drawVenomBolt),

    // ── AoE Root (94-101) ──
    ProjectileType.SEISMIC_ROOT -> aoeRing(AOE_QUAKE, 0.62f, 0.48f, 0.28f, 38f),
    ProjectileType.ROOT_GROWTH  -> aoeRing(AOE_NATURE, 0.30f, 0.66f, 0.18f, 36f),
    ProjectileType.WEB_TRAP     -> asRenderer(drawWebShot),
    ProjectileType.TREMOR_SLAM  -> aoeRing(AOE_QUAKE, 0.62f, 0.48f, 0.28f, 46f),
    ProjectileType.ENTANGLE     -> aoeRing(AOE_NATURE, 0.26f, 0.60f, 0.18f, 36f),
    ProjectileType.STONE_GAZE   -> gorgonEye(petrify = false),
    ProjectileType.INK_SNARE    -> lobbed(LOB_GLOB, 0.16f, 0.14f, 0.24f, 19f),
    ProjectileType.GRAVITY_LOCK -> asRenderer(drawGravityBall),

    // ── Character-specific (102-111) ──
    ProjectileType.KNIFE        -> bladeSpinner(WPN_KNIFE, 0.84f, 0.86f, 0.92f, 25f, 0.36),
    ProjectileType.STING        -> energyBolt(0.45f, 0.9f, 1f, 16f, ORB_SHOCK),
    ProjectileType.HAMMER       -> lobbed(LOB_HAMMER, 0.60f, 0.62f, 0.70f, 27f),
    ProjectileType.HORN         -> asRenderer(drawHorn),
    ProjectileType.MYSTIC_BOLT  -> energyBolt(0.92f, 0.26f, 0.70f, 20f, ORB_RUNE),
    ProjectileType.PETRIFY      -> gorgonEye(petrify = true),
    ProjectileType.GRAB         -> tether(TETH_PAW, 0.98f, 0.64f, 0.26f),
    ProjectileType.JAW          -> asRenderer(drawJaw),
    ProjectileType.TONGUE       -> (drawTongueLash _),
    ProjectileType.ACID_FLASK   -> asRenderer(drawVenomBolt),

    // ── Roster audit: new differentiation projectiles (112+) ──
    ProjectileType.BOOMERANG_BLADE -> bladeSpinner(WPN_CURSED, 0.90f, 0.18f, 0.28f, 30f),
    ProjectileType.VORTEX_BOMB     -> aoeRing(AOE_VOID, 0.55f, 0.25f, 0.85f, 42f),
    ProjectileType.CHAIN_LIGHTNING_FORK -> lightningBolt(1f, 0.92f, 0.42f, heavy = true),
    ProjectileType.SNIPER_BEAM     -> railSlug(0.40f, 0.70f, 1f, 11f),
    ProjectileType.MOMENTUM_STRIKE -> wave(WAV_IMPACT, 0.97f, 0.62f, 0.22f, 30f),
    ProjectileType.LEECH_BOLT      -> energyBolt(0.88f, 0.09f, 0.20f, 22f, ORB_ORBIT),
    ProjectileType.RICOCHET_SHARD  -> flyingShaft(SHF_ICE, 0.62f, 0.90f, 1f, 21f),
    ProjectileType.FLAME_WAVE      -> wave(WAV_FLAME, 1f, 0.5f, 0.1f, 35f),
    ProjectileType.POISON_CLOUD    -> aoeRing(AOE_TOXIC, 0.36f, 0.80f, 0.24f, 46f),
    ProjectileType.BONE_BOOMERANG  -> bladeSpinner(WPN_BONE, 0.94f, 0.92f, 0.84f, 24f, 0.34),
    ProjectileType.GRAVITY_LANCE   -> flyingShaft(SHF_LANCE, 0.55f, 0.25f, 0.85f, 26f),
    ProjectileType.SHADOW_HAUNT    -> asRenderer(drawVoidBolt),
    ProjectileType.CHARGE_FIST     -> fistProj(0.85f, 0.65f, 0.35f, 28f),
    ProjectileType.ACID_SPRAY      -> wave(WAV_ACID, 0.35f, 0.9f, 0.32f, 29f),
    ProjectileType.ECHO_BOLT       -> energyBolt(0.45f, 0.55f, 1f, 20f, ORB_RUNE),
    ProjectileType.FLAME_TRAIL     -> energyBolt(1f, 0.6f, 0.15f, 22f, ORB_FIRE),
    ProjectileType.STAR_BOLT       -> energyBolt(1f, 0.78f, 0.22f, 22f, ORB_ASTRAL),
    ProjectileType.RUNE_BOLT       -> energyBolt(0.32f, 0.58f, 0.98f, 18f, ORB_RUNE),
    ProjectileType.SOUL_HARVEST    -> aoeRing(AOE_VOID, 0.34f, 0.90f, 0.40f, 42f),
    ProjectileType.OVERCLOCK_BEAM  -> aoeRing(AOE_SONIC, 0.22f, 0.95f, 0.75f, 40f),
    ProjectileType.NAPALM_STRIKE   -> lobbed(LOB_BOMB, 1f, 0.5f, 0.12f, 20f),
    ProjectileType.THROWN_BOULDER  -> asRenderer((proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int) => drawBoulder(proj, sx, sy, sb, tick, 28f)),
    ProjectileType.EYE_BEAM        -> laserBolt(BOLT_EYE, 1f, 0.35f, 0.15f, 42f, 11f),

    // ── DPS balance variants (same visual as base type) ──
    ProjectileType.SOUL_BOLT_HEAVY   -> energyBolt(0.30f, 0.84f, 0.82f, 20f, ORB_SPIRIT),
    ProjectileType.FROST_SHARD_LIGHT -> flyingShaft(SHF_ICE, 0.55f, 0.85f, 1f, 20f),
    ProjectileType.FLAME_BOLT_HEAVY  -> energyBolt(1f, 0.5f, 0.1f, 20f, ORB_FIRE),
    ProjectileType.FLAME_BOLT_LIGHT  -> energyBolt(1f, 0.5f, 0.1f, 20f, ORB_FIRE),
    ProjectileType.BULLET_HEAVY      -> bulletProj(0.75f, 0.7f, 0.55f),
    ProjectileType.BULLET_LIGHT      -> bulletProj(0.75f, 0.7f, 0.55f),
    ProjectileType.SONIC_WAVE_HEAVY  -> wave(WAV_SONIC, 0.78f, 0.58f, 0.97f, 35f),
    ProjectileType.SONIC_WAVE_MED    -> wave(WAV_SONIC, 0.78f, 0.58f, 0.97f, 33f),
    ProjectileType.THORN_LIGHT       -> flyingShaft(SHF_THORN, 0.42f, 0.75f, 0.28f, 24f),
    ProjectileType.LASER_HEAVY       -> laserBolt(BOLT_PRISM, 1f, 0.9f, 0.55f, 44f, 10f),
    ProjectileType.LASER_LIGHT       -> laserBolt(BOLT_LASER, 0.3f, 0.95f, 0.9f, 34f, 7.5f),
    ProjectileType.ARROW_HEAVY       -> flyingShaft(SHF_ARROW, 0.95f, 0.72f, 0.32f, 27f),
    ProjectileType.ARROW_LIGHT       -> flyingShaft(SHF_ARROW, 0.95f, 0.72f, 0.32f, 24f),
    ProjectileType.HOLY_BOLT_HEAVY   -> asRenderer(drawHolyBolt),
    ProjectileType.VENOM_BOLT_LIGHT  -> asRenderer(drawVenomBolt),

    // ── Plan 5a: melee and skirmisher kits (150-169), on existing looks ──
    ProjectileType.SHOCKWAVE       -> aoeRing(AOE_SONIC, 1f, 0.90f, 0.55f, 44f),
    ProjectileType.ICE_QUAKE       -> aoeRing(AOE_QUAKE, 0.62f, 0.85f, 1f, 46f),
    ProjectileType.HOWL            -> aoeRing(AOE_SONIC, 0.72f, 0.78f, 0.90f, 60f),
    ProjectileType.FERAL_ROAR      -> aoeRing(AOE_SONIC, 0.95f, 0.62f, 0.25f, 44f),
    ProjectileType.EARTHSPLITTER   -> wave(WAV_IMPACT, 0.62f, 0.48f, 0.28f, 34f),
    ProjectileType.GRASPING_DEAD   -> asRenderer(drawGraspingDead),
    ProjectileType.DEATH_GRIP      -> tether(TETH_DEATH, 0.46f, 0.2f, 0.64f),
    ProjectileType.TALON_GRAB      -> tether(TETH_TALON, 1f, 0.8f, 0.34f),
    ProjectileType.PARALYTIC_STING -> asRenderer(drawStinger),
    ProjectileType.HAMMER_THROW    -> lobbed(LOB_HAMMER, 0.60f, 0.62f, 0.70f, 30f),
    ProjectileType.MEAT_HOOK       -> chainProj(CHN_HOOK, 0.80f, 0.80f, 0.84f, 6f),
    ProjectileType.FLAMBE          -> energyBolt(1f, 0.55f, 0.15f, 22f, ORB_FIRE),
    ProjectileType.HOLY_NOVA       -> asRenderer(drawHolyBolt),
    ProjectileType.AXE_SPIN        -> bladeSpinner(WPN_AXE, 0.78f, 0.70f, 0.55f, 30f),
    ProjectileType.VENOM_DART      -> flyingShaft(SHF_DART, 0.4f, 0.9f, 0.35f, 26f),
    ProjectileType.TOXIC_SHURIKEN  -> shuriken(0.45f, 0.78f, 0.40f, 30f),
    ProjectileType.BLOOD_FRENZY    -> asRenderer(drawClawSwipe),
    ProjectileType.FLURRY          -> fistProj(0.75f, 0.6f, 0.45f, 28f),
    ProjectileType.ROOTING_BOULDER -> asRenderer((proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int) => drawBoulder(proj, sx, sy, sb, tick, 24f)),
    ProjectileType.EMBER_FAN       -> energyBolt(1f, 0.55f, 0.15f, 18f, ORB_FIRE),
    // ── Plan 5a: primaries with an identity of their own (170-175), the base type's look ──
    ProjectileType.FENRIR_CLAW     -> asRenderer(drawClawSwipe),
    ProjectileType.GHOUL_CLAW      -> asRenderer(drawClawSwipe),
    ProjectileType.SHARK_CLAW      -> asRenderer(drawClawSwipe),
    ProjectileType.ICE_BOULDER     -> asRenderer((proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int) => drawBoulder(proj, sx, sy, sb, tick, 24f)),
    ProjectileType.DRAIN_BLADE     -> bladeSpinner(WPN_CURSED, 0.90f, 0.18f, 0.28f, 30f),
    ProjectileType.CHILL_BLADE     -> bladeSpinner(WPN_CURSED, 0.90f, 0.18f, 0.28f, 30f)
  )

  // Flat lookup table for O(1) renderer access without Option allocation.
  // Populated from registry at init time. Null entries = no renderer (use drawGeneric).
  private val _rendererLUT: Array[Renderer] = {
    val lut = new Array[Renderer](256)
    val iter = registry.iterator
    while (iter.hasNext) {
      val (pType, renderer) = iter.next()
      lut(pType & 0xFF) = renderer
    }
    lut
  }

  // ═══════════════════════════════════════════════════════════════
  //  SPECIALIZED RENDERERS
  // ═══════════════════════════════════════════════════════════════

  // ── Electricity ──
  //
  // A bolt is a channel through the air, and the air doesn't move: its kinks are fixed to the
  // ground along the flight line, so as the head flies on it draws the channel and leaves it
  // standing behind, fading. It is straight runs between sharp kinks — a smooth meander, tried
  // first, is a worm — laid out as two levels of midpoint displacement: a kink of its own every
  // other node, and each node between knocked aside from the middle of its neighbours by a
  // crackle re-struck twenty times a second. The big kinks hold while the small ones flicker.
  //
  // The bolt this replaced was a zigzag built relative to the head and re-rolled whole every
  // three frames, with kinks up to 23px either side of segments 7px long: a sawtooth stick
  // carried along by the head, jumping to a new shape twenty times a second, every hairpin
  // corner mitred into a needle. That, and three prongs striking out ahead of the head like
  // the legs of a bug, was the glitch.

  // ═══════════════════════════════════════════════════════════════
  //  CHARACTER-SPECIFIC SPECIALIZED RENDERERS
  // ═══════════════════════════════════════════════════════════════

}
