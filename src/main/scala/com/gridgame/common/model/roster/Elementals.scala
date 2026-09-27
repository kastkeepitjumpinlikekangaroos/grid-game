package com.gridgame.common.model.roster

import com.gridgame.common.model._
import com.gridgame.common.model.CombatRole.{Melee, Ranged, Skirmisher}

/**
 * Masters of fire, ice, storm, earth and wind (character ids 12-26).
 *
 * First the definitions of the projectiles a character here is the first in the roster to throw,
 * then the characters. CharacterDef's initializer registers every definition (Roster); how a
 * projectile type looks is GLProjectileRenderers'.
 */
private[model] object Elementals {
  // ── Projectiles ──

  val FlameBoltDef: ProjectileDef = ProjectileDef(id = ProjectileType.FLAME_BOLT, name = "Flame Bolt", speedMultiplier = 0.75f, damage = 18, maxRange = 16, onHitEffect = Some(Burn(12, 3000, 750)))
  val FrostShardDef: ProjectileDef = ProjectileDef(id = ProjectileType.FROST_SHARD, name = "Frost Shard", speedMultiplier = 0.85f, damage = 15, maxRange = 18)
  val LightningDef: ProjectileDef = ProjectileDef(id = ProjectileType.LIGHTNING, name = "Lightning", speedMultiplier = 1.0f, damage = 14, maxRange = 20, passesThroughWalls = true, pierceCount = 2)
  val ChainLightningDef: ProjectileDef = ProjectileDef(id = ProjectileType.CHAIN_LIGHTNING, name = "Chain Lightning", speedMultiplier = 0.7f, damage = 20, maxRange = 14, aoeOnHit = Some(AoESplashConfig(3.0f, 12)))
  val ThunderStrikeDef: ProjectileDef = ProjectileDef(id = ProjectileType.THUNDER_STRIKE, name = "Thunder Strike", speedMultiplier = 0.4f, damage = 30, maxRange = 16, aoeOnHit = Some(AoESplashConfig(4.0f, 20)), aoeOnMaxRange = Some(AoESplashConfig(4.0f, 20)))
  val BoulderDef: ProjectileDef = ProjectileDef(id = ProjectileType.BOULDER, name = "Boulder", speedMultiplier = 0.4f, damage = 30, maxRange = 8, hitRadius = 2.5f)
  val WindBladeDef: ProjectileDef = ProjectileDef(id = ProjectileType.WIND_BLADE, name = "Wind Blade", speedMultiplier = 0.9f, damage = 18, maxRange = 14)
  val MagmaBallDef: ProjectileDef = ProjectileDef(id = ProjectileType.MAGMA_BALL, name = "Magma Ball", speedMultiplier = 0.45f, damage = 15, maxRange = 14, aoeOnHit = Some(AoESplashConfig(2.5f, 10)), onHitEffect = Some(Burn(15, 4000, 800)))
  val EruptionDef: ProjectileDef = ProjectileDef(id = ProjectileType.ERUPTION, name = "Eruption", speedMultiplier = 0.5f, damage = 30, maxRange = 16, passesThroughPlayers = true, explosionConfig = Some(ExplosionConfig(30, 10, 3.5f)))
  val SandShotDef: ProjectileDef = ProjectileDef(id = ProjectileType.SAND_SHOT, name = "Sand Shot", speedMultiplier = 0.7f, damage = 16, maxRange = 14)
  val SandBlastDef: ProjectileDef = ProjectileDef(id = ProjectileType.SAND_BLAST, name = "Sand Blast", speedMultiplier = 0.6f, damage = 10, maxRange = 10, onHitEffect = Some(Slow(1500, 0.6f)))
  val ThornDef: ProjectileDef = ProjectileDef(id = ProjectileType.THORN, name = "Thorn", speedMultiplier = 0.65f, damage = 18, maxRange = 15, aoeOnHit = Some(AoESplashConfig(2.0f, 6)))
  val VineWhipDef: ProjectileDef = ProjectileDef(id = ProjectileType.VINE_WHIP, name = "Vine Whip", speedMultiplier = 0.8f, damage = 8, maxRange = 18, onHitEffect = Some(PullToOwner))
  val InfernoBlastDef: ProjectileDef = ProjectileDef(id = ProjectileType.INFERNO_BLAST, name = "Inferno Blast", speedMultiplier = 0.45f, damage = 40, maxRange = 15, explodesOnPlayerHit = true, explosionConfig = Some(ExplosionConfig(40, 12, 3.0f)), onHitEffect = Some(Burn(20, 4000, 800)))
  val MudGlobDef: ProjectileDef = ProjectileDef(id = ProjectileType.MUD_GLOB, name = "Mud Glob", speedMultiplier = 0.55f, damage = 13, maxRange = 12, onHitEffect = Some(Slow(2000, 0.4f)))
  val MudBombDef: ProjectileDef = ProjectileDef(id = ProjectileType.MUD_BOMB, name = "Mud Bomb", speedMultiplier = 0.4f, damage = 20, maxRange = 14, passesThroughPlayers = true, explodesOnPlayerHit = true, explosionConfig = Some(ExplosionConfig(20, 8, 3.0f)))
  val EmberShotDef: ProjectileDef = ProjectileDef(id = ProjectileType.EMBER_SHOT, name = "Ember Shot", speedMultiplier = 0.9f, damage = 24, maxRange = 10, onHitEffect = Some(Burn(8, 2000, 500)))
  val AvalancheCrushDef: ProjectileDef = ProjectileDef(id = ProjectileType.AVALANCHE_CRUSH, name = "Avalanche Crush", speedMultiplier = 0.35f, damage = 35, maxRange = 12, hitRadius = 3.5f, aoeOnHit = Some(AoESplashConfig(4.0f, 20)), aoeOnMaxRange = Some(AoESplashConfig(4.0f, 20)), onHitEffect = Some(Slow(2000, 0.5f)))
  val SeismicRootDef: ProjectileDef = ProjectileDef(id = ProjectileType.SEISMIC_ROOT, name = "Seismic Root", speedMultiplier = 0f, damage = 0, maxRange = 0, aoeOnMaxRange = Some(AoESplashConfig(7.0f, 20, rootDurationMs = 2000)), explosionConfig = Some(ExplosionConfig(0, 0, 7.0f)))
  val EntangleDef: ProjectileDef = ProjectileDef(id = ProjectileType.ENTANGLE, name = "Entangle", speedMultiplier = 0f, damage = 0, maxRange = 0, aoeOnMaxRange = Some(AoESplashConfig(7.0f, 15, rootDurationMs = 2500)), explosionConfig = Some(ExplosionConfig(0, 0, 7.0f)))
  val VortexBombDef: ProjectileDef = ProjectileDef(id = ProjectileType.VORTEX_BOMB, name = "Vortex Bomb", speedMultiplier = 0.45f, damage = 20, maxRange = 14, passesThroughPlayers = true, aoeOnMaxRange = Some(AoESplashConfig(4.0f, 15, freezeDurationMs = 600)), onHitEffect = Some(VortexPull(4.0f, 3.0f)))
  val RicochetShardDef: ProjectileDef = ProjectileDef(id = ProjectileType.RICOCHET_SHARD, name = "Ricochet Shard", speedMultiplier = 0.75f, damage = 18, maxRange = 20, ricochetCount = 3, onHitEffect = Some(Freeze(500)))
  val FlameWaveDef: ProjectileDef = ProjectileDef(id = ProjectileType.FLAME_WAVE, name = "Flame Wave", speedMultiplier = 0.65f, damage = 16, maxRange = 14, pierceCount = 2, passesThroughWalls = true, onHitEffect = Some(Burn(10, 3000, 750)))
  val FrostShardLightDef: ProjectileDef = ProjectileDef(id = ProjectileType.FROST_SHARD_LIGHT, name = "Frost Shard Light", speedMultiplier = 0.85f, damage = 10, maxRange = 18)
  // A slam (Plan 5a): a bot casts it when its target is inside the ability's GroundSlam radius,
  // so that radius and the AoE radius here are kept in step
  val IceQuakeDef: ProjectileDef = ProjectileDef(id = ProjectileType.ICE_QUAKE, name = "Ice Quake", speedMultiplier = 0f, damage = 0, maxRange = 0, aoeOnMaxRange = Some(AoESplashConfig(6.0f, 25, freezeDurationMs = 1200)), explosionConfig = Some(ExplosionConfig(0, 0, 6.0f)))

  val projectiles: Seq[ProjectileDef] = Seq(
    FlameBoltDef, FrostShardDef, LightningDef, ChainLightningDef, ThunderStrikeDef,
    BoulderDef, WindBladeDef, MagmaBallDef, EruptionDef, SandShotDef,
    SandBlastDef, ThornDef, VineWhipDef, InfernoBlastDef, MudGlobDef,
    MudBombDef, EmberShotDef, AvalancheCrushDef, SeismicRootDef, EntangleDef,
    VortexBombDef, RicochetShardDef, FlameWaveDef, FrostShardLightDef, IceQuakeDef
  )

  /** Plan 5a's variants: another type's numbers and look, with an effect of their own. Made from
    * the registered base, so Roster makes them once every base is registered. */
  def variants(base: Byte => ProjectileDef): Seq[ProjectileDef] = Seq(
    base(ProjectileType.EMBER_SHOT).copy(id = ProjectileType.EMBER_FAN, name = "Ember Fan", alsoOnHit = Seq(Slow(1000, 0.7f))),
    // A primary with an identity of its own (Plan 5a): a slow at most, never a hold. Primaries fire
    // twice a second, and a hold grants 1.5s of CC immunity that would turn the kit's own holds away
    base(ProjectileType.BOULDER).copy(id = ProjectileType.ICE_BOULDER, name = "Ice Boulder", onHitEffect = Some(Slow(1000, 0.7f)))
  )

  // ── Characters ──

  val Pyromancer: CharacterDef = CharacterDef(
    id = CharacterId.Pyromancer, displayName = "Pyromancer",
    description = "A fire mage who hurls flame bolts and devastating fireballs.",
    spriteSheet = "sprites/pyromancer.png",
    qAbility = AbilityDef(name = "Fireball", description = "Launches a slow but devastating fireball.", cooldownMs = 6000, maxRange = 18, damage = 45, projectileType = ProjectileType.FIREBALL, keybind = "Q"),
    eAbility = AbilityDef(name = "Fire Fan", description = "Sprays 5 flame bolts in a fan.", cooldownMs = 8000, maxRange = 16, damage = 18, projectileType = ProjectileType.FLAME_BOLT, keybind = "E", castBehavior = FanProjectile(5, Math.toRadians(60))),
    primaryProjectileType = ProjectileType.FLAME_BOLT, role = Ranged, maxHealth = 70
  )

  val Cryomancer: CharacterDef = CharacterDef(
    id = CharacterId.Cryomancer, displayName = "Cryomancer",
    description = "An ice mage who freezes enemies and shatters them with frost.",
    spriteSheet = "sprites/cryomancer.png",
    qAbility = AbilityDef(name = "Ice Beam", description = "Fires a freezing beam that immobilizes enemies.", cooldownMs = 5000, maxRange = 20, damage = 5, projectileType = ProjectileType.ICE_BEAM, keybind = "Q"),
    eAbility = AbilityDef(name = "Ice Nova", description = "Unleashes frost shards in all directions.", cooldownMs = 10000, maxRange = 18, damage = 16, projectileType = ProjectileType.FROST_SHARD, keybind = "E", castBehavior = FanProjectile(8, 2 * Math.PI)),
    primaryProjectileType = ProjectileType.FROST_SHARD, role = Ranged, maxHealth = 70
  )

  val Stormcaller: CharacterDef = CharacterDef(
    id = CharacterId.Stormcaller, displayName = "Stormcaller",
    description = "A storm mage with wall-piercing lightning and devastating thunder.",
    spriteSheet = "sprites/stormcaller.png",
    qAbility = AbilityDef(name = "Chain Lightning", description = "Electric bolt that splashes to nearby enemies.", cooldownMs = 8000, maxRange = 14, damage = 20, projectileType = ProjectileType.CHAIN_LIGHTNING, keybind = "Q"),
    eAbility = AbilityDef(name = "Thunder Strike", description = "Slow thunderbolt that erupts in a massive AoE.", cooldownMs = 12000, maxRange = 16, damage = 30, projectileType = ProjectileType.THUNDER_STRIKE, keybind = "E"),
    primaryProjectileType = ProjectileType.LIGHTNING, role = Ranged, maxHealth = 70
  )

  val Earthshaker: CharacterDef = CharacterDef(
    id = CharacterId.Earthshaker, displayName = "Earthshaker",
    description = "A hulking earth warrior who hurls boulders and shakes the ground.",
    spriteSheet = "sprites/earthshaker.png",
    qAbility = AbilityDef(name = "Seismic Root", description = "Slams the ground, rooting nearby enemies.", cooldownMs = 12000, maxRange = 0, damage = 20, projectileType = ProjectileType.SEISMIC_ROOT, keybind = "Q", castBehavior = GroundSlam(7.0f)),
    eAbility = AbilityDef(name = "Ground Charge", description = "Charges forward with unstoppable force.", cooldownMs = 12000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = DashBuff(10, 150, 25)),
    primaryProjectileType = ProjectileType.BOULDER, role = Skirmisher, maxHealth = 145, moveSpeed = Skirmisher.speed
  )

  val Windwalker: CharacterDef = CharacterDef(
    id = CharacterId.Windwalker, displayName = "Windwalker",
    description = "A swift air elementalist who slices with wind and summons cyclones.",
    spriteSheet = "sprites/windwalker.png",
    qAbility = AbilityDef(name = "Wind Fan", description = "Fires 5 gusts that push enemies back.", cooldownMs = 8000, maxRange = 6, damage = 10, projectileType = ProjectileType.GUST, keybind = "Q", castBehavior = FanProjectile(5, Math.toRadians(60))),
    eAbility = AbilityDef(name = "Cyclone", description = "Unleashes wind blades in all directions.", cooldownMs = 10000, maxRange = 14, damage = 15, projectileType = ProjectileType.WIND_BLADE, keybind = "E", castBehavior = FanProjectile(8, 2 * Math.PI)),
    primaryProjectileType = ProjectileType.WIND_BLADE, role = Ranged, maxHealth = 65
  )

  val MagmaKnight: CharacterDef = CharacterDef(
    id = CharacterId.MagmaKnight, displayName = "Magma Knight",
    description = "An armored knight wreathed in molten rock.",
    spriteSheet = "sprites/magmaknight.png",
    qAbility = AbilityDef(name = "Magma Charge", description = "Charges through enemies in a blaze of magma.", cooldownMs = 10000, maxRange = 0, damage = 0, projectileType = -1, keybind = "Q", castBehavior = DashBuff(8, 350, 22)),
    eAbility = AbilityDef(name = "Eruption", description = "Launches a molten projectile that explodes at range.", cooldownMs = 12000, maxRange = 16, damage = 30, projectileType = ProjectileType.ERUPTION, keybind = "E"),
    primaryProjectileType = ProjectileType.MAGMA_BALL, role = Ranged, maxHealth = 75
  )

  val Frostbite: CharacterDef = CharacterDef(
    id = CharacterId.Frostbite, displayName = "Frostbite",
    description = "A frost assassin who fans frost shards and blinks through ice.",
    spriteSheet = "sprites/frostbite.png",
    qAbility = AbilityDef(name = "Frost Fan", description = "Fires 3 frost shards in a tight spread.", cooldownMs = 8000, maxRange = 18, damage = 16, projectileType = ProjectileType.FROST_SHARD, keybind = "Q", castBehavior = FanProjectile(3, Math.toRadians(30))),
    eAbility = AbilityDef(name = "Ice Blink", description = "Teleports through a shard of ice.", cooldownMs = 10000, maxRange = 6, damage = 0, projectileType = -2, keybind = "E", castBehavior = TeleportCast(6)),
    primaryProjectileType = ProjectileType.FROST_SHARD, role = Ranged, maxHealth = 65
  )

  val Sandstorm: CharacterDef = CharacterDef(
    id = CharacterId.Sandstorm, displayName = "Sandstorm",
    description = "A desert nomad who blinds foes with sand and vanishes in storms.",
    spriteSheet = "sprites/sandstorm.png",
    qAbility = AbilityDef(name = "Sand Blast", description = "Blinding sand that slows on hit.", cooldownMs = 7000, maxRange = 10, damage = 10, projectileType = ProjectileType.SAND_BLAST, keybind = "Q"),
    eAbility = AbilityDef(name = "Dust Devil", description = "Become a sandstorm: phased and invulnerable.", cooldownMs = 14000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = PhaseShiftBuff(4000)),
    primaryProjectileType = ProjectileType.SAND_SHOT, role = Ranged, maxHealth = 70
  )

  val Thornweaver: CharacterDef = CharacterDef(
    id = CharacterId.Thornweaver, displayName = "Thornweaver",
    description = "A nature mage who entangles enemies with vines and pierces with thorns.",
    spriteSheet = "sprites/thornweaver.png",
    qAbility = AbilityDef(name = "Vine Whip", description = "A vine that pulls enemies toward you.", cooldownMs = 8000, maxRange = 18, damage = 8, projectileType = ProjectileType.VINE_WHIP, keybind = "Q"),
    eAbility = AbilityDef(name = "Entangle", description = "Vines erupt from the ground, rooting nearby enemies.", cooldownMs = 12000, maxRange = 0, damage = 15, projectileType = ProjectileType.ENTANGLE, keybind = "E", castBehavior = GroundSlam(7.0f)),
    primaryProjectileType = ProjectileType.THORN, role = Ranged, maxHealth = 70
  )

  val Cloudrunner: CharacterDef = CharacterDef(
    id = CharacterId.Cloudrunner, displayName = "Cloudrunner",
    description = "A sky rider who pushes foes with gusts and dashes on tailwinds.",
    spriteSheet = "sprites/cloudrunner.png",
    qAbility = AbilityDef(name = "Gust Fan", description = "Fires 3 gusts that push enemies back.", cooldownMs = 8000, maxRange = 6, damage = 10, projectileType = ProjectileType.GUST, keybind = "Q", castBehavior = FanProjectile(3, Math.toRadians(40))),
    eAbility = AbilityDef(name = "Tailwind Dash", description = "Rides the wind forward in a swift dash.", cooldownMs = 10000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = DashBuff(10, 350, 18)),
    primaryProjectileType = ProjectileType.WIND_BLADE, role = Ranged, maxHealth = 70
  )

  val Inferno: CharacterDef = CharacterDef(
    id = CharacterId.Inferno, displayName = "Inferno",
    description = "A living flame that pierces through everything, leaving fire in its wake.",
    spriteSheet = "sprites/inferno.png",
    qAbility = AbilityDef(name = "Inferno Blast", description = "Massive fire explosion on impact.", cooldownMs = 14000, maxRange = 15, damage = 40, projectileType = ProjectileType.INFERNO_BLAST, keybind = "Q"),
    eAbility = AbilityDef(name = "Fire Vortex", description = "Creates a vortex that pulls enemies and burns.", cooldownMs = 16000, maxRange = 14, damage = 20, projectileType = ProjectileType.VORTEX_BOMB, keybind = "E"),
    primaryProjectileType = ProjectileType.FLAME_WAVE, role = Ranged, maxHealth = 70
  )

  val Glacier: CharacterDef = CharacterDef(
    id = CharacterId.Glacier, displayName = "Glacier",
    description = "A massive ice elemental whose ricocheting shards freeze and shatter foes.",
    spriteSheet = "sprites/glacier.png",
    qAbility = AbilityDef(name = "Ricochet Shard", description = "Hurls a ricocheting ice shard that freezes on hit.", cooldownMs = 8000, maxRange = 20, damage = 18, projectileType = ProjectileType.RICOCHET_SHARD, keybind = "Q"),
    eAbility = AbilityDef(name = "Ice Wall", description = "Fires 5 frost shards in a fan.", cooldownMs = 10000, maxRange = 18, damage = 16, projectileType = ProjectileType.FROST_SHARD, keybind = "E", castBehavior = FanProjectile(5, Math.toRadians(60))),
    primaryProjectileType = ProjectileType.FROST_SHARD_LIGHT, role = Ranged, maxHealth = 80
  )

  val Mudslinger: CharacterDef = CharacterDef(
    id = CharacterId.Mudslinger, displayName = "Mudslinger",
    description = "A swamp dweller who slows enemies with mud and detonates mud bombs.",
    spriteSheet = "sprites/mudslinger.png",
    qAbility = AbilityDef(name = "Mud Bomb", description = "Mud explosive that slows all caught in the blast.", cooldownMs = 10000, maxRange = 14, damage = 20, projectileType = ProjectileType.MUD_BOMB, keybind = "Q"),
    eAbility = AbilityDef(name = "Mud Fan", description = "Fires 3 mud globs in a fan.", cooldownMs = 8000, maxRange = 12, damage = 14, projectileType = ProjectileType.MUD_GLOB, keybind = "E", castBehavior = FanProjectile(3, Math.toRadians(40))),
    primaryProjectileType = ProjectileType.MUD_GLOB, role = Ranged, maxHealth = 70
  )

  val Ember: CharacterDef = CharacterDef(
    id = CharacterId.Ember, displayName = "Ember",
    description = "A tiny fire sprite that fans embers and bursts into protective flame.",
    spriteSheet = "sprites/ember.png",
    qAbility = AbilityDef(name = "Ember Fan", description = "Fires 5 embers in a fan that burn and briefly slow.", cooldownMs = 4000, maxRange = 10, damage = 10, projectileType = ProjectileType.EMBER_FAN, keybind = "Q", castBehavior = FanProjectile(5, Math.toRadians(45))),
    eAbility = AbilityDef(name = "Flame Burst", description = "Erupts into protective flame, phased and immune.", cooldownMs = 10000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = PhaseShiftBuff(2000)),
    primaryProjectileType = ProjectileType.EMBER_SHOT, role = Skirmisher, maxHealth = 105, moveSpeed = Skirmisher.speed
  )

  val Avalanche: CharacterDef = CharacterDef(
    id = CharacterId.Avalanche, displayName = "Avalanche",
    description = "A slow siege brute with avalanche crush and ice quake that freezes nearby foes.",
    spriteSheet = "sprites/avalanche.png",
    qAbility = AbilityDef(name = "Avalanche Crush", description = "Hurls a massive icy boulder that splashes a wide area and slows.", cooldownMs = 18000, maxRange = 12, damage = 35, projectileType = ProjectileType.AVALANCHE_CRUSH, keybind = "Q"),
    eAbility = AbilityDef(name = "Ice Quake", description = "Slams the ground with icy force, freezing nearby enemies for 1.2s.", cooldownMs = 18000, maxRange = 0, damage = 25, projectileType = ProjectileType.ICE_QUAKE, keybind = "E", castBehavior = GroundSlam(6.0f)),
    primaryProjectileType = ProjectileType.ICE_BOULDER, role = Skirmisher, maxHealth = 145, moveSpeed = Skirmisher.speed
  )

  val characters: Seq[CharacterDef] = Seq(
    Pyromancer, Cryomancer, Stormcaller, Earthshaker, Windwalker, MagmaKnight,
    Frostbite, Sandstorm, Thornweaver, Cloudrunner, Inferno, Glacier,
    Mudslinger, Ember, Avalanche
  )
}
