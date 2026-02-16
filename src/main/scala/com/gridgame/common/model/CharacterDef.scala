package com.gridgame.common.model

// Cast behavior sealed trait — describes HOW an ability is cast
sealed trait CastBehavior
case object StandardProjectile extends CastBehavior
case class PhaseShiftBuff(durationMs: Int) extends CastBehavior
case class DashBuff(maxDistance: Int, durationMs: Int, moveRateMs: Int) extends CastBehavior
case class TeleportCast(maxDistance: Int) extends CastBehavior
case class FanProjectile(count: Int, fanAngle: Double) extends CastBehavior
case class GroundSlam(radius: Float) extends CastBehavior

case class AbilityDef(
    name: String,
    description: String,
    cooldownMs: Int,
    maxRange: Int,
    damage: Int,
    projectileType: Byte,
    keybind: String,
    castBehavior: CastBehavior = StandardProjectile
)

case class CharacterDef(
    id: CharacterId,
    displayName: String,
    description: String,
    spriteSheet: String,
    qAbility: AbilityDef,
    eAbility: AbilityDef,
    primaryProjectileType: Byte,
    maxHealth: Int = 100
)

object CharacterDef {
  // === Projectile definitions (registered at init) ===

  private val TentacleDef = ProjectileDef(
    id = ProjectileType.TENTACLE, name = "Tentacle",
    speedMultiplier = 0.9f, damage = 5, maxRange = 15,
    onHitEffect = Some(PullToOwner)
  )

  private val IceBeamDef = ProjectileDef(
    id = ProjectileType.ICE_BEAM, name = "Ice Beam",
    speedMultiplier = 0.3f, damage = 5, maxRange = 20,
    onHitEffect = Some(Freeze(3000))
  )

  private val AxeDef = ProjectileDef(
    id = ProjectileType.AXE, name = "Axe",
    speedMultiplier = 0.6f, damage = 33, maxRange = 5,
    hitRadius = 2.5f
  )

  private val RopeDef = ProjectileDef(
    id = ProjectileType.ROPE, name = "Rope",
    speedMultiplier = 0.85f, damage = 5, maxRange = 25,
    onHitEffect = Some(PullToOwner)
  )

  private val SpearDef = ProjectileDef(
    id = ProjectileType.SPEAR, name = "Spear",
    speedMultiplier = 0.7f, damage = 10, maxRange = 20,
    distanceDamageScaling = Some(DistanceDamageScaling(10, 60, 20))
  )

  private val SoulBoltDef = ProjectileDef(
    id = ProjectileType.SOUL_BOLT, name = "Soul Bolt",
    speedMultiplier = 0.65f, damage = 15, maxRange = 15,
    passesThroughWalls = true
  )

  private val HauntDef = ProjectileDef(
    id = ProjectileType.HAUNT, name = "Haunt",
    speedMultiplier = 0.5f, damage = 30, maxRange = 15,
    passesThroughWalls = true,
    onHitEffect = Some(TeleportOwnerBehind(2, 2000))
  )

  private val ArcaneBoltDef = ProjectileDef(
    id = ProjectileType.ARCANE_BOLT, name = "Arcane Bolt",
    speedMultiplier = 0.85f, damage = 18, maxRange = 25,
    passesThroughWalls = true,
    chargeSpeedScaling = Some(ChargeScaling(0.6f, 1.2f)),
    chargeDamageScaling = Some(ChargeScaling(8f, 40f)),
    chargeRangeScaling = Some(ChargeScaling(15f, 30f))
  )

  private val FireballDef = ProjectileDef(
    id = ProjectileType.FIREBALL, name = "Fireball",
    speedMultiplier = 0.4f, damage = 45, maxRange = 18
  )

  private val SplashDef = ProjectileDef(
    id = ProjectileType.SPLASH, name = "Splash",
    speedMultiplier = 0.65f, damage = 20, maxRange = 15,
    chargeSpeedScaling = Some(ChargeScaling(0.5f, 1.0f)),
    chargeDamageScaling = Some(ChargeScaling(10f, 35f)),
    chargeRangeScaling = Some(ChargeScaling(10f, 20f)),
    aoeOnHit = Some(AoESplashConfig(3.0f, 10))
  )

  private val TidalWaveDef = ProjectileDef(
    id = ProjectileType.TIDAL_WAVE, name = "Tidal Wave",
    speedMultiplier = 0.5f, damage = 10, maxRange = 12,
    onHitEffect = Some(Push(3.0f))
  )

  private val GeyserDef = ProjectileDef(
    id = ProjectileType.GEYSER, name = "Geyser",
    speedMultiplier = 0.6f, damage = 25, maxRange = 18,
    hitRadius = 3.5f,
    aoeOnHit = Some(AoESplashConfig(4.0f, 25)),
    aoeOnMaxRange = Some(AoESplashConfig(4.0f, 25))
  )

  private val BulletDef = ProjectileDef(
    id = ProjectileType.BULLET, name = "Bullet",
    speedMultiplier = 1.0f, damage = 20, maxRange = 18
  )

  private val GrenadeDef = ProjectileDef(
    id = ProjectileType.GRENADE, name = "Grenade",
    speedMultiplier = 0.5f, damage = 40, maxRange = 12,
    passesThroughPlayers = true,
    explosionConfig = Some(ExplosionConfig(40, 10, 3.0f))
  )

  private val RocketDef = ProjectileDef(
    id = ProjectileType.ROCKET, name = "Rocket",
    speedMultiplier = 0.55f, damage = 35, maxRange = 20,
    explodesOnPlayerHit = true,
    explosionConfig = Some(ExplosionConfig(35, 10, 2.5f))
  )

  private val TalonDef = ProjectileDef(
    id = ProjectileType.TALON, name = "Talon",
    speedMultiplier = 0.7f, damage = 28, maxRange = 4,
    hitRadius = 2.2f
  )

  private val GustDef = ProjectileDef(
    id = ProjectileType.GUST, name = "Gust",
    speedMultiplier = 0.8f, damage = 10, maxRange = 6,
    onHitEffect = Some(Push(5.0f))
  )

  private val ShurikenDef = ProjectileDef(
    id = ProjectileType.SHURIKEN, name = "Shuriken",
    speedMultiplier = 0.95f, damage = 22, maxRange = 6,
    hitRadius = 2.2f
  )

  private val PoisonDartDef = ProjectileDef(
    id = ProjectileType.POISON_DART, name = "Poison Dart",
    speedMultiplier = 0.75f, damage = 15, maxRange = 16,
    onHitEffect = Some(Freeze(2500))
  )

  private val ChainBoltDef = ProjectileDef(
    id = ProjectileType.CHAIN_BOLT, name = "Chain Bolt",
    speedMultiplier = 0.7f, damage = 12, maxRange = 14,
    onHitEffect = Some(Freeze(200))
  )

  private val LockdownChainDef = ProjectileDef(
    id = ProjectileType.LOCKDOWN_CHAIN, name = "Lockdown Chain",
    speedMultiplier = 0.6f, damage = 8, maxRange = 12,
    onHitEffect = Some(Freeze(700))
  )

  private val SnareMineDef = ProjectileDef(
    id = ProjectileType.SNARE_MINE, name = "Snare Mine",
    speedMultiplier = 0.4f, damage = 15, maxRange = 16,
    aoeOnHit = Some(AoESplashConfig(3.5f, 15, freezeDurationMs = 1000)),
    aoeOnMaxRange = Some(AoESplashConfig(3.5f, 15, freezeDurationMs = 1000)),
    explodesOnPlayerHit = true,
    explosionConfig = Some(ExplosionConfig(0, 0, 3.5f))
  )

  private val KatanaDef = ProjectileDef(
    id = ProjectileType.KATANA, name = "Katana",
    speedMultiplier = 0.7f, damage = 30, maxRange = 4,
    hitRadius = 2.2f
  )

  private val SwordWaveDef = ProjectileDef(
    id = ProjectileType.SWORD_WAVE, name = "Sword Wave",
    speedMultiplier = 0.6f, damage = 20, maxRange = 5,
    hitRadius = 1.8f
  )

  private val PlagueBoltDef = ProjectileDef(
    id = ProjectileType.PLAGUE_BOLT, name = "Plague Bolt",
    speedMultiplier = 0.55f, damage = 15, maxRange = 16,
    aoeOnHit = Some(AoESplashConfig(2.5f, 8))
  )

  private val MiasmaDef = ProjectileDef(
    id = ProjectileType.MIASMA, name = "Miasma",
    speedMultiplier = 0.45f, damage = 12, maxRange = 14,
    hitRadius = 2.8f,
    explodesOnPlayerHit = true,
    explosionConfig = Some(ExplosionConfig(25, 8, 5.0f))
  )

  private val BlightBombDef = ProjectileDef(
    id = ProjectileType.BLIGHT_BOMB, name = "Blight Bomb",
    speedMultiplier = 0.5f, damage = 25, maxRange = 15,
    explodesOnPlayerHit = true,
    explosionConfig = Some(ExplosionConfig(30, 12, 4.0f))
  )

  private val BloodFangDef = ProjectileDef(
    id = ProjectileType.BLOOD_FANG, name = "Blood Fang",
    speedMultiplier = 0.7f, damage = 25, maxRange = 3,
    hitRadius = 2.2f,
    onHitEffect = Some(LifeSteal(50))
  )

  private val BloodSiphonDef = ProjectileDef(
    id = ProjectileType.BLOOD_SIPHON, name = "Blood Siphon",
    speedMultiplier = 0.65f, damage = 18, maxRange = 14,
    onHitEffect = Some(LifeSteal(40))
  )

  private val BatSwarmDef = ProjectileDef(
    id = ProjectileType.BAT_SWARM, name = "Bat Swarm",
    speedMultiplier = 0.5f, damage = 15, maxRange = 7,
    onHitEffect = Some(LifeSteal(60))
  )

  // --- Batch 1: Elemental projectile defs ---
  private val FlameBoltDef = ProjectileDef(id = ProjectileType.FLAME_BOLT, name = "Flame Bolt", speedMultiplier = 0.75f, damage = 18, maxRange = 16, onHitEffect = Some(Burn(12, 3000, 750)))
  private val FrostShardDef = ProjectileDef(id = ProjectileType.FROST_SHARD, name = "Frost Shard", speedMultiplier = 0.85f, damage = 16, maxRange = 18)
  private val LightningDef = ProjectileDef(id = ProjectileType.LIGHTNING, name = "Lightning", speedMultiplier = 1.0f, damage = 14, maxRange = 20, passesThroughWalls = true, pierceCount = 2)
  private val ChainLightningDef = ProjectileDef(id = ProjectileType.CHAIN_LIGHTNING, name = "Chain Lightning", speedMultiplier = 0.7f, damage = 20, maxRange = 14, aoeOnHit = Some(AoESplashConfig(3.0f, 12)))
  private val ThunderStrikeDef = ProjectileDef(id = ProjectileType.THUNDER_STRIKE, name = "Thunder Strike", speedMultiplier = 0.4f, damage = 30, maxRange = 16, aoeOnHit = Some(AoESplashConfig(4.0f, 20)), aoeOnMaxRange = Some(AoESplashConfig(4.0f, 20)))
  private val BoulderDef = ProjectileDef(id = ProjectileType.BOULDER, name = "Boulder", speedMultiplier = 0.4f, damage = 35, maxRange = 8, hitRadius = 2.5f)
  private val SeismicSlamDef = ProjectileDef(id = ProjectileType.SEISMIC_SLAM, name = "Seismic Slam", speedMultiplier = 0.5f, damage = 25, maxRange = 12, hitRadius = 2.8f, aoeOnHit = Some(AoESplashConfig(3.5f, 15)), aoeOnMaxRange = Some(AoESplashConfig(3.5f, 15)))
  private val WindBladeDef = ProjectileDef(id = ProjectileType.WIND_BLADE, name = "Wind Blade", speedMultiplier = 0.9f, damage = 15, maxRange = 14)
  private val MagmaBallDef = ProjectileDef(id = ProjectileType.MAGMA_BALL, name = "Magma Ball", speedMultiplier = 0.45f, damage = 22, maxRange = 14, aoeOnHit = Some(AoESplashConfig(2.5f, 10)), onHitEffect = Some(Burn(15, 4000, 800)))
  private val EruptionDef = ProjectileDef(id = ProjectileType.ERUPTION, name = "Eruption", speedMultiplier = 0.5f, damage = 30, maxRange = 16, passesThroughPlayers = true, explosionConfig = Some(ExplosionConfig(30, 10, 3.5f)))
  private val FrostTrapDef = ProjectileDef(id = ProjectileType.FROST_TRAP, name = "Frost Trap", speedMultiplier = 0.4f, damage = 15, maxRange = 14, passesThroughPlayers = true, aoeOnMaxRange = Some(AoESplashConfig(3.5f, 15, freezeDurationMs = 1500)))
  private val SandShotDef = ProjectileDef(id = ProjectileType.SAND_SHOT, name = "Sand Shot", speedMultiplier = 0.7f, damage = 16, maxRange = 14)
  private val SandBlastDef = ProjectileDef(id = ProjectileType.SAND_BLAST, name = "Sand Blast", speedMultiplier = 0.6f, damage = 10, maxRange = 10, onHitEffect = Some(Freeze(500)))
  private val ThornDef = ProjectileDef(id = ProjectileType.THORN, name = "Thorn", speedMultiplier = 0.65f, damage = 18, maxRange = 15, aoeOnHit = Some(AoESplashConfig(2.0f, 6)))
  private val VineWhipDef = ProjectileDef(id = ProjectileType.VINE_WHIP, name = "Vine Whip", speedMultiplier = 0.8f, damage = 8, maxRange = 18, onHitEffect = Some(PullToOwner))
  private val ThornWallDef = ProjectileDef(id = ProjectileType.THORN_WALL, name = "Thorn Wall", speedMultiplier = 0.5f, damage = 12, maxRange = 8)
  private val InfernoBlastDef = ProjectileDef(id = ProjectileType.INFERNO_BLAST, name = "Inferno Blast", speedMultiplier = 0.45f, damage = 40, maxRange = 15, explodesOnPlayerHit = true, explosionConfig = Some(ExplosionConfig(40, 12, 3.0f)), onHitEffect = Some(Burn(20, 4000, 800)))
  private val GlacierSpikeDef = ProjectileDef(id = ProjectileType.GLACIER_SPIKE, name = "Glacier Spike", speedMultiplier = 0.5f, damage = 30, maxRange = 14, onHitEffect = Some(Freeze(2000)))
  private val MudGlobDef = ProjectileDef(id = ProjectileType.MUD_GLOB, name = "Mud Glob", speedMultiplier = 0.55f, damage = 14, maxRange = 12, onHitEffect = Some(Freeze(1000)))
  private val MudBombDef = ProjectileDef(id = ProjectileType.MUD_BOMB, name = "Mud Bomb", speedMultiplier = 0.4f, damage = 20, maxRange = 14, passesThroughPlayers = true, explodesOnPlayerHit = true, explosionConfig = Some(ExplosionConfig(20, 8, 3.0f)))
  private val EmberShotDef = ProjectileDef(id = ProjectileType.EMBER_SHOT, name = "Ember Shot", speedMultiplier = 0.9f, damage = 10, maxRange = 10, onHitEffect = Some(Burn(8, 2000, 500)))
  private val AvalancheCrushDef = ProjectileDef(id = ProjectileType.AVALANCHE_CRUSH, name = "Avalanche Crush", speedMultiplier = 0.35f, damage = 35, maxRange = 12, hitRadius = 3.5f, aoeOnHit = Some(AoESplashConfig(4.0f, 20)), aoeOnMaxRange = Some(AoESplashConfig(4.0f, 20)))

  // --- Batch 2: Undead/Dark projectile defs ---
  private val DeathBoltDef = ProjectileDef(id = ProjectileType.DEATH_BOLT, name = "Death Bolt", speedMultiplier = 0.7f, damage = 16, maxRange = 16, passesThroughWalls = true)
  private val RaiseDeadDef = ProjectileDef(id = ProjectileType.RAISE_DEAD, name = "Raise Dead", speedMultiplier = 0.5f, damage = 20, maxRange = 14, aoeOnHit = Some(AoESplashConfig(3.0f, 12)))
  private val BoneAxeDef = ProjectileDef(id = ProjectileType.BONE_AXE, name = "Bone Axe", speedMultiplier = 0.6f, damage = 30, maxRange = 5, hitRadius = 2.5f)
  private val BoneThrowDef = ProjectileDef(id = ProjectileType.BONE_THROW, name = "Bone Throw", speedMultiplier = 0.8f, damage = 20, maxRange = 16, boomerang = true)
  private val WailDef = ProjectileDef(id = ProjectileType.WAIL, name = "Wail", speedMultiplier = 0.5f, damage = 15, maxRange = 10, passesThroughWalls = true, aoeOnHit = Some(AoESplashConfig(3.0f, 10, freezeDurationMs = 800)))
  private val SoulDrainDef = ProjectileDef(id = ProjectileType.SOUL_DRAIN, name = "Soul Drain", speedMultiplier = 0.6f, damage = 25, maxRange = 14, onHitEffect = Some(LifeSteal(40)))
  private val ClawSwipeDef = ProjectileDef(id = ProjectileType.CLAW_SWIPE, name = "Claw Swipe", speedMultiplier = 0.7f, damage = 25, maxRange = 4, hitRadius = 2.2f, onHitEffect = Some(SpeedBoost(2000)))
  private val DevourDef = ProjectileDef(id = ProjectileType.DEVOUR, name = "Devour", speedMultiplier = 0.5f, damage = 30, maxRange = 6, onHitEffect = Some(LifeSteal(50)))
  private val ScytheDef = ProjectileDef(id = ProjectileType.SCYTHE, name = "Scythe", speedMultiplier = 0.65f, damage = 28, maxRange = 5, hitRadius = 2.5f)
  private val ReapDef = ProjectileDef(id = ProjectileType.REAP, name = "Reap", speedMultiplier = 0.5f, damage = 35, maxRange = 8, passesThroughWalls = true, hitRadius = 2.8f)
  private val ShadowBoltDef = ProjectileDef(id = ProjectileType.SHADOW_BOLT, name = "Shadow Bolt", speedMultiplier = 0.8f, damage = 15, maxRange = 16, passesThroughWalls = true)
  private val CursedBladeDef = ProjectileDef(id = ProjectileType.CURSED_BLADE, name = "Cursed Blade", speedMultiplier = 0.7f, damage = 24, maxRange = 5, hitRadius = 2.2f)
  private val LifeDrainDef = ProjectileDef(id = ProjectileType.LIFE_DRAIN, name = "Life Drain", speedMultiplier = 0.6f, damage = 20, maxRange = 14, onHitEffect = Some(LifeSteal(35)))
  private val ShovelDef = ProjectileDef(id = ProjectileType.SHOVEL, name = "Shovel", speedMultiplier = 0.6f, damage = 28, maxRange = 4, hitRadius = 2.2f)
  private val HeadThrowDef = ProjectileDef(id = ProjectileType.HEAD_THROW, name = "Head Throw", speedMultiplier = 0.7f, damage = 25, maxRange = 16, onHitEffect = Some(Freeze(1000)))
  private val BandageWhipDef = ProjectileDef(id = ProjectileType.BANDAGE_WHIP, name = "Bandage Whip", speedMultiplier = 0.75f, damage = 10, maxRange = 16, onHitEffect = Some(PullToOwner))
  private val CurseDef = ProjectileDef(id = ProjectileType.CURSE, name = "Curse", speedMultiplier = 0.5f, damage = 20, maxRange = 12, onHitEffect = Some(Freeze(2000)))

  // --- Batch 3: Medieval/Fantasy projectile defs ---
  private val HolyBladeDef = ProjectileDef(id = ProjectileType.HOLY_BLADE, name = "Holy Blade", speedMultiplier = 0.7f, damage = 26, maxRange = 5, hitRadius = 2.2f)
  private val HolyBoltDef = ProjectileDef(id = ProjectileType.HOLY_BOLT, name = "Holy Bolt", speedMultiplier = 0.8f, damage = 20, maxRange = 16)
  private val ArrowDef = ProjectileDef(id = ProjectileType.ARROW, name = "Arrow", speedMultiplier = 0.95f, damage = 18, maxRange = 22, pierceCount = 1)
  private val PoisonArrowDef = ProjectileDef(id = ProjectileType.POISON_ARROW, name = "Poison Arrow", speedMultiplier = 0.85f, damage = 12, maxRange = 20, onHitEffect = Some(Freeze(1500)))
  private val SonicWaveDef = ProjectileDef(id = ProjectileType.SONIC_WAVE, name = "Sonic Wave", speedMultiplier = 0.7f, damage = 14, maxRange = 14, passesThroughWalls = true)
  private val SonicBoomDef = ProjectileDef(id = ProjectileType.SONIC_BOOM, name = "Sonic Boom", speedMultiplier = 0.5f, damage = 10, maxRange = 8, onHitEffect = Some(Push(4.0f)), aoeOnHit = Some(AoESplashConfig(3.0f, 8)))
  private val FistDef = ProjectileDef(id = ProjectileType.FIST, name = "Fist", speedMultiplier = 0.8f, damage = 22, maxRange = 4, hitRadius = 2.2f, boomerang = true)
  private val SmiteDef = ProjectileDef(id = ProjectileType.SMITE, name = "Smite", speedMultiplier = 0.6f, damage = 30, maxRange = 14, aoeOnHit = Some(AoESplashConfig(3.0f, 15)))
  private val CharmDef = ProjectileDef(id = ProjectileType.CHARM, name = "Charm", speedMultiplier = 0.6f, damage = 15, maxRange = 14, onHitEffect = Some(Freeze(2000)))
  private val CardDef = ProjectileDef(id = ProjectileType.CARD, name = "Card", speedMultiplier = 0.85f, damage = 16, maxRange = 14, ricochetCount = 2)

  // --- Batch 4: Sci-Fi/Tech projectile defs ---
  private val DataBoltDef = ProjectileDef(id = ProjectileType.DATA_BOLT, name = "Data Bolt", speedMultiplier = 0.85f, damage = 14, maxRange = 16, ricochetCount = 2)
  private val VirusDef = ProjectileDef(id = ProjectileType.VIRUS, name = "Virus", speedMultiplier = 0.6f, damage = 10, maxRange = 14, onHitEffect = Some(Freeze(2000)))
  private val LaserDef = ProjectileDef(id = ProjectileType.LASER, name = "Laser", speedMultiplier = 0.95f, damage = 16, maxRange = 20)
  private val GravityBallDef = ProjectileDef(id = ProjectileType.GRAVITY_BALL, name = "Gravity Ball", speedMultiplier = 0.6f, damage = 18, maxRange = 14, onHitEffect = Some(PullToOwner))
  private val GravityWellDef = ProjectileDef(id = ProjectileType.GRAVITY_WELL, name = "Gravity Well", speedMultiplier = 0.4f, damage = 25, maxRange = 12, aoeOnHit = Some(AoESplashConfig(3.5f, 15, freezeDurationMs = 800)), onHitEffect = Some(VortexPull(4.0f, 3.0f)))
  private val TeslaCoilDef = ProjectileDef(id = ProjectileType.TESLA_COIL, name = "Tesla Coil", speedMultiplier = 0.5f, damage = 20, maxRange = 10, aoeOnHit = Some(AoESplashConfig(3.0f, 12, freezeDurationMs = 500)), ricochetCount = 3)
  private val NanoBoltDef = ProjectileDef(id = ProjectileType.NANO_BOLT, name = "Nano Bolt", speedMultiplier = 0.8f, damage = 12, maxRange = 16, aoeOnHit = Some(AoESplashConfig(2.0f, 6)))
  private val VoidBoltDef = ProjectileDef(id = ProjectileType.VOID_BOLT, name = "Void Bolt", speedMultiplier = 0.7f, damage = 20, maxRange = 18, passesThroughWalls = true)
  private val RailgunDef = ProjectileDef(id = ProjectileType.RAILGUN, name = "Railgun", speedMultiplier = 1.0f, damage = 30, maxRange = 25, passesThroughWalls = true, pierceCount = 3)
  private val ClusterBombDef = ProjectileDef(id = ProjectileType.CLUSTER_BOMB, name = "Cluster Bomb", speedMultiplier = 0.4f, damage = 35, maxRange = 14, passesThroughPlayers = true, explodesOnPlayerHit = true, explosionConfig = Some(ExplosionConfig(35, 12, 3.5f)))

  // --- Batch 5: Nature/Beast projectile defs ---
  private val VenomBoltDef = ProjectileDef(id = ProjectileType.VENOM_BOLT, name = "Venom Bolt", speedMultiplier = 0.75f, damage = 14, maxRange = 16, onHitEffect = Some(Freeze(1000)))
  private val WebShotDef = ProjectileDef(id = ProjectileType.WEB_SHOT, name = "Web Shot", speedMultiplier = 0.7f, damage = 12, maxRange = 14, onHitEffect = Some(Freeze(1500)))
  private val StingerDef = ProjectileDef(id = ProjectileType.STINGER, name = "Stinger", speedMultiplier = 0.7f, damage = 22, maxRange = 6, onHitEffect = Some(Freeze(1000)))
  private val AcidBombDef = ProjectileDef(id = ProjectileType.ACID_BOMB, name = "Acid Bomb", speedMultiplier = 0.5f, damage = 25, maxRange = 14, explodesOnPlayerHit = true, explosionConfig = Some(ExplosionConfig(25, 10, 3.0f)))

  // --- AoE Root projectile defs ---
  private val SeismicRootDef = ProjectileDef(id = ProjectileType.SEISMIC_ROOT, name = "Seismic Root", speedMultiplier = 0f, damage = 0, maxRange = 0, aoeOnMaxRange = Some(AoESplashConfig(4.0f, 20, rootDurationMs = 2000)), explosionConfig = Some(ExplosionConfig(0, 0, 4.0f)))
  private val RootGrowthDef = ProjectileDef(id = ProjectileType.ROOT_GROWTH, name = "Root Growth", speedMultiplier = 0f, damage = 0, maxRange = 0, aoeOnMaxRange = Some(AoESplashConfig(5.0f, 10, rootDurationMs = 2500)), explosionConfig = Some(ExplosionConfig(0, 0, 5.0f)))
  private val WebTrapDef = ProjectileDef(id = ProjectileType.WEB_TRAP, name = "Web Trap", speedMultiplier = 0.5f, damage = 0, maxRange = 14, passesThroughPlayers = true, aoeOnMaxRange = Some(AoESplashConfig(3.5f, 8, rootDurationMs = 2000)), explosionConfig = Some(ExplosionConfig(0, 0, 3.5f)))
  private val TremorSlamDef = ProjectileDef(id = ProjectileType.TREMOR_SLAM, name = "Tremor Slam", speedMultiplier = 0f, damage = 0, maxRange = 0, aoeOnMaxRange = Some(AoESplashConfig(3.5f, 30, rootDurationMs = 1500)), explosionConfig = Some(ExplosionConfig(0, 0, 3.5f)))
  private val EntangleDef = ProjectileDef(id = ProjectileType.ENTANGLE, name = "Entangle", speedMultiplier = 0f, damage = 0, maxRange = 0, aoeOnMaxRange = Some(AoESplashConfig(4.5f, 15, rootDurationMs = 2500)), explosionConfig = Some(ExplosionConfig(0, 0, 4.5f)))
  private val StoneGazeDef = ProjectileDef(id = ProjectileType.STONE_GAZE, name = "Stone Gaze", speedMultiplier = 0f, damage = 0, maxRange = 0, aoeOnMaxRange = Some(AoESplashConfig(3.5f, 15, rootDurationMs = 2000)), explosionConfig = Some(ExplosionConfig(0, 0, 3.5f)))
  private val InkSnareDef = ProjectileDef(id = ProjectileType.INK_SNARE, name = "Ink Snare", speedMultiplier = 0.5f, damage = 0, maxRange = 14, passesThroughPlayers = true, aoeOnMaxRange = Some(AoESplashConfig(3.5f, 10, rootDurationMs = 2000)), explosionConfig = Some(ExplosionConfig(0, 0, 3.5f)))
  private val GravityLockDef = ProjectileDef(id = ProjectileType.GRAVITY_LOCK, name = "Gravity Lock", speedMultiplier = 0.4f, damage = 0, maxRange = 12, passesThroughPlayers = true, aoeOnMaxRange = Some(AoESplashConfig(4.0f, 15, rootDurationMs = 2000)), explosionConfig = Some(ExplosionConfig(0, 0, 4.0f)))

  // Register all projectile defs
  ProjectileDef.register(
    TentacleDef, IceBeamDef, AxeDef, RopeDef, SpearDef,
    SoulBoltDef, HauntDef, ArcaneBoltDef, FireballDef,
    SplashDef, TidalWaveDef, GeyserDef,
    BulletDef, GrenadeDef, RocketDef, TalonDef, GustDef,
    ShurikenDef, PoisonDartDef,
    ChainBoltDef, LockdownChainDef, SnareMineDef,
    KatanaDef, SwordWaveDef,
    PlagueBoltDef, MiasmaDef, BlightBombDef,
    BloodFangDef, BloodSiphonDef, BatSwarmDef,
    // Batch 1: Elemental
    FlameBoltDef, FrostShardDef, LightningDef, ChainLightningDef, ThunderStrikeDef,
    BoulderDef, SeismicSlamDef, WindBladeDef, MagmaBallDef, EruptionDef,
    FrostTrapDef, SandShotDef, SandBlastDef, ThornDef, VineWhipDef,
    ThornWallDef, InfernoBlastDef, GlacierSpikeDef, MudGlobDef, MudBombDef,
    EmberShotDef, AvalancheCrushDef,
    // Batch 2: Undead/Dark
    DeathBoltDef, RaiseDeadDef, BoneAxeDef, BoneThrowDef, WailDef,
    SoulDrainDef, ClawSwipeDef, DevourDef, ScytheDef, ReapDef,
    ShadowBoltDef, CursedBladeDef, LifeDrainDef, ShovelDef, HeadThrowDef,
    BandageWhipDef, CurseDef,
    // Batch 3: Medieval/Fantasy
    HolyBladeDef, HolyBoltDef, ArrowDef, PoisonArrowDef, SonicWaveDef,
    SonicBoomDef, FistDef, SmiteDef, CharmDef, CardDef,
    // Batch 4: Sci-Fi/Tech
    DataBoltDef, VirusDef, LaserDef, GravityBallDef, GravityWellDef,
    TeslaCoilDef, NanoBoltDef, VoidBoltDef, RailgunDef, ClusterBombDef,
    // Batch 5: Nature/Beast
    VenomBoltDef, WebShotDef, StingerDef, AcidBombDef,
    // AoE Root
    SeismicRootDef, RootGrowthDef, WebTrapDef, TremorSlamDef,
    EntangleDef, StoneGazeDef, InkSnareDef, GravityLockDef
  )

  // === Character definitions ===

  val Spaceman: CharacterDef = CharacterDef(
    id = CharacterId.Spaceman,
    displayName = "Spaceman",
    description = "A versatile space explorer with tentacle grab and ice beam.",
    spriteSheet = "sprites/character.png",
    qAbility = AbilityDef(
      name = "Tentacle",
      description = "Shoots a tentacle that pulls enemies closer.",
      cooldownMs = 5000, maxRange = 15, damage = 5,
      projectileType = ProjectileType.TENTACLE, keybind = "Q"
    ),
    eAbility = AbilityDef(
      name = "Ice Beam",
      description = "Fires a freezing beam that immobilizes enemies.",
      cooldownMs = 5000, maxRange = 20, damage = 5,
      projectileType = ProjectileType.ICE_BEAM, keybind = "E"
    ),
    primaryProjectileType = ProjectileType.NORMAL
  )

  val Gladiator: CharacterDef = CharacterDef(
    id = CharacterId.Gladiator,
    displayName = "Gladiator",
    description = "A Roman warrior with axe, rope pull, and distance-scaling spear.",
    spriteSheet = "sprites/gladiator.png",
    qAbility = AbilityDef(
      name = "Spear Throw",
      description = "Hurls a spear that deals more damage the farther it travels.",
      cooldownMs = 8000, maxRange = 20, damage = 10,
      projectileType = ProjectileType.SPEAR, keybind = "Q"
    ),
    eAbility = AbilityDef(
      name = "Rope",
      description = "Throws a rope that pulls enemies to you.",
      cooldownMs = 20000, maxRange = 25, damage = 5,
      projectileType = ProjectileType.ROPE, keybind = "E"
    ),
    primaryProjectileType = ProjectileType.AXE
  )

  val Wraith: CharacterDef = CharacterDef(
    id = CharacterId.Wraith,
    displayName = "Wraith",
    description = "A spectral assassin that phases between planes and haunts targets.",
    spriteSheet = "sprites/wraith.png",
    qAbility = AbilityDef(
      name = "Phase Shift",
      description = "Become ethereal: walk through walls, immune to damage, can't attack.",
      cooldownMs = 12000, maxRange = 0, damage = 0,
      projectileType = -1, keybind = "Q",
      castBehavior = PhaseShiftBuff(5000)
    ),
    eAbility = AbilityDef(
      name = "Haunt",
      description = "Slow spectral bolt that teleports you behind the target on hit.",
      cooldownMs = 15000, maxRange = 15, damage = 30,
      projectileType = ProjectileType.HAUNT, keybind = "E"
    ),
    primaryProjectileType = ProjectileType.SOUL_BOLT
  )

  val Wizard: CharacterDef = CharacterDef(
    id = CharacterId.Wizard,
    displayName = "Wizard",
    description = "A fragile mage with wall-piercing bolts, devastating fireballs, and blink escape.",
    spriteSheet = "sprites/wizard.png",
    qAbility = AbilityDef(
      name = "Fireball",
      description = "Launches a slow but devastating fireball that deals massive damage.",
      cooldownMs = 6000, maxRange = 18, damage = 45,
      projectileType = ProjectileType.FIREBALL, keybind = "Q"
    ),
    eAbility = AbilityDef(
      name = "Blink",
      description = "Instantly teleport up to 6 cells toward your cursor.",
      cooldownMs = 8000, maxRange = 6, damage = 0,
      projectileType = -2, keybind = "E",
      castBehavior = TeleportCast(6)
    ),
    primaryProjectileType = ProjectileType.ARCANE_BOLT,
    maxHealth = 70
  )

  val Tidecaller: CharacterDef = CharacterDef(
    id = CharacterId.Tidecaller,
    displayName = "Tidecaller",
    description = "A water mage with devastating area-of-effect abilities.",
    spriteSheet = "sprites/tidecaller.png",
    qAbility = AbilityDef(
      name = "Tidal Wave",
      description = "Fires 5 projectiles in a fan that push enemies back.",
      cooldownMs = 8000, maxRange = 12, damage = 10,
      projectileType = ProjectileType.TIDAL_WAVE, keybind = "Q",
      castBehavior = FanProjectile(5, Math.toRadians(60))
    ),
    eAbility = AbilityDef(
      name = "Geyser",
      description = "Erupts on hit or at max range, damaging all nearby.",
      cooldownMs = 12000, maxRange = 18, damage = 25,
      projectileType = ProjectileType.GEYSER, keybind = "E"
    ),
    primaryProjectileType = ProjectileType.SPLASH
  )

  val Soldier: CharacterDef = CharacterDef(
    id = CharacterId.Soldier,
    displayName = "Soldier",
    description = "A military specialist with rifle, grenade, and rocket launcher.",
    spriteSheet = "sprites/soldier.png",
    qAbility = AbilityDef(
      name = "Grenade",
      description = "Thrown explosive that passes through players and detonates at range.",
      cooldownMs = 10000, maxRange = 12, damage = 40,
      projectileType = ProjectileType.GRENADE, keybind = "Q"
    ),
    eAbility = AbilityDef(
      name = "Rocket",
      description = "Fired explosive that detonates on impact with splash damage.",
      cooldownMs = 12000, maxRange = 20, damage = 35,
      projectileType = ProjectileType.ROCKET, keybind = "E"
    ),
    primaryProjectileType = ProjectileType.BULLET
  )

  val Raptor: CharacterDef = CharacterDef(
    id = CharacterId.Raptor,
    displayName = "Raptor",
    description = "A bird of prey that swoops in and blasts enemies away.",
    spriteSheet = "sprites/raptor.png",
    qAbility = AbilityDef(
      name = "Swoop",
      description = "Dash toward the cursor, phased and invulnerable during flight.",
      cooldownMs = 10000, maxRange = 0, damage = 0,
      projectileType = -1, keybind = "Q",
      castBehavior = DashBuff(12, 400, 20)
    ),
    eAbility = AbilityDef(
      name = "Gust",
      description = "Wind blast that pushes enemies away from you.",
      cooldownMs = 8000, maxRange = 6, damage = 10,
      projectileType = ProjectileType.GUST, keybind = "E"
    ),
    primaryProjectileType = ProjectileType.TALON
  )

  val Assassin: CharacterDef = CharacterDef(
    id = CharacterId.Assassin,
    displayName = "Assassin",
    description = "A deadly shadow operative with shurikens, poison darts, and a concealing smoke bomb.",
    spriteSheet = "sprites/assassin.png",
    qAbility = AbilityDef(
      name = "Poison Dart",
      description = "Fires a venomous dart that poisons and slows the target.",
      cooldownMs = 7000, maxRange = 16, damage = 15,
      projectileType = ProjectileType.POISON_DART, keybind = "Q"
    ),
    eAbility = AbilityDef(
      name = "Smoke Bomb",
      description = "Vanishes in a cloud of smoke, phased and immune.",
      cooldownMs = 12000, maxRange = 0, damage = 0,
      projectileType = -1, keybind = "E",
      castBehavior = PhaseShiftBuff(3000)
    ),
    primaryProjectileType = ProjectileType.SHURIKEN,
    maxHealth = 85
  )

  val Warden: CharacterDef = CharacterDef(
    id = CharacterId.Warden,
    displayName = "Warden",
    description = "A jailer who locks down foes with chains, lockdown fans, and snare mines.",
    spriteSheet = "sprites/warden.png",
    qAbility = AbilityDef(
      name = "Lockdown",
      description = "Fires 3 chains in a fan that freeze enemies for 0.7s on hit.",
      cooldownMs = 8000, maxRange = 12, damage = 8,
      projectileType = ProjectileType.LOCKDOWN_CHAIN, keybind = "Q",
      castBehavior = FanProjectile(3, Math.toRadians(40))
    ),
    eAbility = AbilityDef(
      name = "Snare Mine",
      description = "Lobs a mine that passes through players and detonates at max range, freezing all nearby for 1s.",
      cooldownMs = 14000, maxRange = 16, damage = 15,
      projectileType = ProjectileType.SNARE_MINE, keybind = "E"
    ),
    primaryProjectileType = ProjectileType.CHAIN_BOLT,
    maxHealth = 110
  )

  val Samurai: CharacterDef = CharacterDef(
    id = CharacterId.Samurai,
    displayName = "Samurai",
    description = "A precise swordsman who dashes through foes and cuts down anyone in reach.",
    spriteSheet = "sprites/samurai.png",
    qAbility = AbilityDef(
      name = "Iaijutsu",
      description = "Dash toward the cursor, phased and invulnerable during flight.",
      cooldownMs = 10000, maxRange = 0, damage = 0,
      projectileType = -1, keybind = "Q",
      castBehavior = DashBuff(8, 300, 20)
    ),
    eAbility = AbilityDef(
      name = "Whirlwind",
      description = "Unleash 8 sword waves in a full circle around you.",
      cooldownMs = 12000, maxRange = 5, damage = 20,
      projectileType = ProjectileType.SWORD_WAVE, keybind = "E",
      castBehavior = FanProjectile(8, 2 * Math.PI)
    ),
    primaryProjectileType = ProjectileType.KATANA,
    maxHealth = 110
  )

  val PlagueDoctor: CharacterDef = CharacterDef(
    id = CharacterId.PlagueDoctor,
    displayName = "Plague Doctor",
    description = "A pestilence-spreading alchemist who thrives in group fights with toxic AoE.",
    spriteSheet = "sprites/plaguedoctor.png",
    qAbility = AbilityDef(
      name = "Miasma",
      description = "Toxic cloud that explodes on contact or at max range in a massive toxic blast.",
      cooldownMs = 8000, maxRange = 14, damage = 12,
      projectileType = ProjectileType.MIASMA, keybind = "Q"
    ),
    eAbility = AbilityDef(
      name = "Blight Bomb",
      description = "Volatile vial that explodes on contact or at max range in a massive toxic blast.",
      cooldownMs = 14000, maxRange = 15, damage = 25,
      projectileType = ProjectileType.BLIGHT_BOMB, keybind = "E"
    ),
    primaryProjectileType = ProjectileType.PLAGUE_BOLT
  )

  val Vampire: CharacterDef = CharacterDef(
    id = CharacterId.Vampire,
    displayName = "Vampire",
    description = "A gothic life-stealer who heals from every attack. Low HP forces aggressive play.",
    spriteSheet = "sprites/vampire.png",
    qAbility = AbilityDef(
      name = "Blood Siphon",
      description = "Medium-range blood projectile that heals you for 40% of damage dealt.",
      cooldownMs = 7000, maxRange = 14, damage = 18,
      projectileType = ProjectileType.BLOOD_SIPHON, keybind = "Q"
    ),
    eAbility = AbilityDef(
      name = "Bat Swarm",
      description = "Fan of 3 draining projectiles that heal and briefly freeze enemies.",
      cooldownMs = 14000, maxRange = 7, damage = 15,
      projectileType = ProjectileType.BAT_SWARM, keybind = "E",
      castBehavior = FanProjectile(8, 2 * Math.PI)
    ),
    primaryProjectileType = ProjectileType.BLOOD_FANG,
    maxHealth = 90
  )

  // === Batch 1: Elemental (IDs 12-26) ===

  val Pyromancer: CharacterDef = CharacterDef(
    id = CharacterId.Pyromancer, displayName = "Pyromancer",
    description = "A fire mage who hurls flame bolts and devastating fireballs.",
    spriteSheet = "sprites/pyromancer.png",
    qAbility = AbilityDef(name = "Fireball", description = "Launches a slow but devastating fireball.", cooldownMs = 6000, maxRange = 18, damage = 45, projectileType = ProjectileType.FIREBALL, keybind = "Q"),
    eAbility = AbilityDef(name = "Fire Fan", description = "Sprays 5 flame bolts in a fan.", cooldownMs = 8000, maxRange = 16, damage = 18, projectileType = ProjectileType.FLAME_BOLT, keybind = "E", castBehavior = FanProjectile(5, Math.toRadians(60))),
    primaryProjectileType = ProjectileType.FLAME_BOLT
  )

  val Cryomancer: CharacterDef = CharacterDef(
    id = CharacterId.Cryomancer, displayName = "Cryomancer",
    description = "An ice mage who freezes enemies and shatters them with frost.",
    spriteSheet = "sprites/cryomancer.png",
    qAbility = AbilityDef(name = "Ice Beam", description = "Fires a freezing beam that immobilizes enemies.", cooldownMs = 5000, maxRange = 20, damage = 5, projectileType = ProjectileType.ICE_BEAM, keybind = "Q"),
    eAbility = AbilityDef(name = "Ice Nova", description = "Unleashes frost shards in all directions.", cooldownMs = 10000, maxRange = 18, damage = 16, projectileType = ProjectileType.FROST_SHARD, keybind = "E", castBehavior = FanProjectile(8, 2 * Math.PI)),
    primaryProjectileType = ProjectileType.FROST_SHARD
  )

  val Stormcaller: CharacterDef = CharacterDef(
    id = CharacterId.Stormcaller, displayName = "Stormcaller",
    description = "A storm mage with wall-piercing lightning and devastating thunder.",
    spriteSheet = "sprites/stormcaller.png",
    qAbility = AbilityDef(name = "Chain Lightning", description = "Electric bolt that splashes to nearby enemies.", cooldownMs = 8000, maxRange = 14, damage = 20, projectileType = ProjectileType.CHAIN_LIGHTNING, keybind = "Q"),
    eAbility = AbilityDef(name = "Thunder Strike", description = "Slow thunderbolt that erupts in a massive AoE.", cooldownMs = 12000, maxRange = 16, damage = 30, projectileType = ProjectileType.THUNDER_STRIKE, keybind = "E"),
    primaryProjectileType = ProjectileType.LIGHTNING
  )

  val Earthshaker: CharacterDef = CharacterDef(
    id = CharacterId.Earthshaker, displayName = "Earthshaker",
    description = "A hulking earth warrior who hurls boulders and shakes the ground.",
    spriteSheet = "sprites/earthshaker.png",
    qAbility = AbilityDef(name = "Seismic Root", description = "Slams the ground, rooting nearby enemies.", cooldownMs = 12000, maxRange = 0, damage = 20, projectileType = ProjectileType.SEISMIC_ROOT, keybind = "Q", castBehavior = GroundSlam(4.0f)),
    eAbility = AbilityDef(name = "Ground Charge", description = "Charges forward with unstoppable force.", cooldownMs = 12000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = DashBuff(10, 400, 25)),
    primaryProjectileType = ProjectileType.BOULDER, maxHealth = 120
  )

  val Windwalker: CharacterDef = CharacterDef(
    id = CharacterId.Windwalker, displayName = "Windwalker",
    description = "A swift air elementalist who slices with wind and summons cyclones.",
    spriteSheet = "sprites/windwalker.png",
    qAbility = AbilityDef(name = "Wind Fan", description = "Fires 5 gusts that push enemies back.", cooldownMs = 8000, maxRange = 6, damage = 10, projectileType = ProjectileType.GUST, keybind = "Q", castBehavior = FanProjectile(5, Math.toRadians(60))),
    eAbility = AbilityDef(name = "Cyclone", description = "Unleashes wind blades in all directions.", cooldownMs = 10000, maxRange = 14, damage = 15, projectileType = ProjectileType.WIND_BLADE, keybind = "E", castBehavior = FanProjectile(8, 2 * Math.PI)),
    primaryProjectileType = ProjectileType.WIND_BLADE, maxHealth = 85
  )

  val MagmaKnight: CharacterDef = CharacterDef(
    id = CharacterId.MagmaKnight, displayName = "Magma Knight",
    description = "An armored knight wreathed in molten rock.",
    spriteSheet = "sprites/magmaknight.png",
    qAbility = AbilityDef(name = "Magma Charge", description = "Charges through enemies in a blaze of magma.", cooldownMs = 10000, maxRange = 0, damage = 0, projectileType = -1, keybind = "Q", castBehavior = DashBuff(8, 350, 22)),
    eAbility = AbilityDef(name = "Eruption", description = "Launches a molten projectile that explodes at range.", cooldownMs = 12000, maxRange = 16, damage = 30, projectileType = ProjectileType.ERUPTION, keybind = "E"),
    primaryProjectileType = ProjectileType.MAGMA_BALL, maxHealth = 110
  )

  val Frostbite: CharacterDef = CharacterDef(
    id = CharacterId.Frostbite, displayName = "Frostbite",
    description = "A frost assassin who fans frost shards and sets frozen traps.",
    spriteSheet = "sprites/frostbite.png",
    qAbility = AbilityDef(name = "Frost Fan", description = "Fires 3 frost shards in a tight spread.", cooldownMs = 8000, maxRange = 18, damage = 16, projectileType = ProjectileType.FROST_SHARD, keybind = "Q", castBehavior = FanProjectile(3, Math.toRadians(30))),
    eAbility = AbilityDef(name = "Frost Trap", description = "Deploys a trap that freezes all nearby on detonation.", cooldownMs = 12000, maxRange = 14, damage = 15, projectileType = ProjectileType.FROST_TRAP, keybind = "E"),
    primaryProjectileType = ProjectileType.FROST_SHARD, maxHealth = 85
  )

  val Sandstorm: CharacterDef = CharacterDef(
    id = CharacterId.Sandstorm, displayName = "Sandstorm",
    description = "A desert nomad who blinds foes with sand and vanishes in storms.",
    spriteSheet = "sprites/sandstorm.png",
    qAbility = AbilityDef(name = "Sand Blast", description = "Blinding sand that briefly freezes on hit.", cooldownMs = 7000, maxRange = 10, damage = 10, projectileType = ProjectileType.SAND_BLAST, keybind = "Q"),
    eAbility = AbilityDef(name = "Dust Devil", description = "Become a sandstorm: phased and invulnerable.", cooldownMs = 14000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = PhaseShiftBuff(4000)),
    primaryProjectileType = ProjectileType.SAND_SHOT
  )

  val Thornweaver: CharacterDef = CharacterDef(
    id = CharacterId.Thornweaver, displayName = "Thornweaver",
    description = "A nature mage who entangles enemies with vines and pierces with thorns.",
    spriteSheet = "sprites/thornweaver.png",
    qAbility = AbilityDef(name = "Vine Whip", description = "A vine that pulls enemies toward you.", cooldownMs = 8000, maxRange = 18, damage = 8, projectileType = ProjectileType.VINE_WHIP, keybind = "Q"),
    eAbility = AbilityDef(name = "Entangle", description = "Vines erupt from the ground, rooting nearby enemies.", cooldownMs = 12000, maxRange = 0, damage = 15, projectileType = ProjectileType.ENTANGLE, keybind = "E", castBehavior = GroundSlam(4.5f)),
    primaryProjectileType = ProjectileType.THORN
  )

  val Cloudrunner: CharacterDef = CharacterDef(
    id = CharacterId.Cloudrunner, displayName = "Cloudrunner",
    description = "A sky rider who pushes foes with wind and rides the tailwind.",
    spriteSheet = "sprites/cloudrunner.png",
    qAbility = AbilityDef(name = "Gust Fan", description = "Fires 3 gusts that push enemies back.", cooldownMs = 8000, maxRange = 6, damage = 10, projectileType = ProjectileType.GUST, keybind = "Q", castBehavior = FanProjectile(3, Math.toRadians(40))),
    eAbility = AbilityDef(name = "Tailwind", description = "Rides the wind, becoming phased and immune.", cooldownMs = 14000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = PhaseShiftBuff(3000)),
    primaryProjectileType = ProjectileType.WIND_BLADE
  )

  val Inferno: CharacterDef = CharacterDef(
    id = CharacterId.Inferno, displayName = "Inferno",
    description = "A living flame that engulfs everything in fire.",
    spriteSheet = "sprites/inferno.png",
    qAbility = AbilityDef(name = "Fire Circle", description = "Unleashes flame bolts in all directions.", cooldownMs = 10000, maxRange = 16, damage = 18, projectileType = ProjectileType.FLAME_BOLT, keybind = "Q", castBehavior = FanProjectile(8, 2 * Math.PI)),
    eAbility = AbilityDef(name = "Inferno Blast", description = "Massive fire explosion on impact.", cooldownMs = 14000, maxRange = 15, damage = 40, projectileType = ProjectileType.INFERNO_BLAST, keybind = "E"),
    primaryProjectileType = ProjectileType.FLAME_BOLT
  )

  val Glacier: CharacterDef = CharacterDef(
    id = CharacterId.Glacier, displayName = "Glacier",
    description = "A massive ice elemental that freezes and shatters foes.",
    spriteSheet = "sprites/glacier.png",
    qAbility = AbilityDef(name = "Glacier Spike", description = "Hurls a massive ice spike that freezes on hit.", cooldownMs = 10000, maxRange = 14, damage = 30, projectileType = ProjectileType.GLACIER_SPIKE, keybind = "Q"),
    eAbility = AbilityDef(name = "Ice Wall", description = "Fires 5 frost shards in a fan.", cooldownMs = 10000, maxRange = 18, damage = 16, projectileType = ProjectileType.FROST_SHARD, keybind = "E", castBehavior = FanProjectile(5, Math.toRadians(60))),
    primaryProjectileType = ProjectileType.FROST_SHARD, maxHealth = 120
  )

  val Mudslinger: CharacterDef = CharacterDef(
    id = CharacterId.Mudslinger, displayName = "Mudslinger",
    description = "A swamp dweller who slows enemies with mud and detonates mud bombs.",
    spriteSheet = "sprites/mudslinger.png",
    qAbility = AbilityDef(name = "Mud Bomb", description = "Mud explosive that slows all caught in the blast.", cooldownMs = 10000, maxRange = 14, damage = 20, projectileType = ProjectileType.MUD_BOMB, keybind = "Q"),
    eAbility = AbilityDef(name = "Mud Fan", description = "Fires 3 mud globs in a fan.", cooldownMs = 8000, maxRange = 12, damage = 14, projectileType = ProjectileType.MUD_GLOB, keybind = "E", castBehavior = FanProjectile(3, Math.toRadians(40))),
    primaryProjectileType = ProjectileType.MUD_GLOB
  )

  val EmberChar: CharacterDef = CharacterDef(
    id = CharacterId.Ember, displayName = "Ember",
    description = "A tiny fire sprite that fans embers and bursts into protective flame.",
    spriteSheet = "sprites/ember.png",
    qAbility = AbilityDef(name = "Ember Fan", description = "Fires 5 embers in a fan.", cooldownMs = 6000, maxRange = 10, damage = 10, projectileType = ProjectileType.EMBER_SHOT, keybind = "Q", castBehavior = FanProjectile(5, Math.toRadians(45))),
    eAbility = AbilityDef(name = "Flame Burst", description = "Erupts into protective flame, phased and immune.", cooldownMs = 14000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = PhaseShiftBuff(2000)),
    primaryProjectileType = ProjectileType.EMBER_SHOT, maxHealth = 75
  )

  val AvalancheChar: CharacterDef = CharacterDef(
    id = CharacterId.Avalanche, displayName = "Avalanche",
    description = "A massive ice-earth brute that crushes with avalanches and charges.",
    spriteSheet = "sprites/avalanche.png",
    qAbility = AbilityDef(name = "Avalanche Crush", description = "Hurls a massive icy boulder with huge AoE.", cooldownMs = 14000, maxRange = 12, damage = 35, projectileType = ProjectileType.AVALANCHE_CRUSH, keybind = "Q"),
    eAbility = AbilityDef(name = "Glacier Charge", description = "Charges forward with unstoppable icy force.", cooldownMs = 12000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = DashBuff(10, 400, 25)),
    primaryProjectileType = ProjectileType.BOULDER, maxHealth = 120
  )

  // === Batch 2: Undead/Dark (IDs 27-41) ===

  val Necromancer: CharacterDef = CharacterDef(
    id = CharacterId.Necromancer, displayName = "Necromancer",
    description = "A dark sorcerer who commands death bolts and raises the dead.",
    spriteSheet = "sprites/necromancer.png",
    qAbility = AbilityDef(name = "Raise Dead", description = "Summons a burst of necrotic energy.", cooldownMs = 8000, maxRange = 14, damage = 20, projectileType = ProjectileType.RAISE_DEAD, keybind = "Q"),
    eAbility = AbilityDef(name = "Death Fan", description = "Fires 3 death bolts in a fan.", cooldownMs = 10000, maxRange = 16, damage = 16, projectileType = ProjectileType.DEATH_BOLT, keybind = "E", castBehavior = FanProjectile(3, Math.toRadians(30))),
    primaryProjectileType = ProjectileType.DEATH_BOLT
  )

  val SkeletonKing: CharacterDef = CharacterDef(
    id = CharacterId.SkeletonKing, displayName = "Skeleton King",
    description = "An undead monarch wielding bone axes and commanding bone storms.",
    spriteSheet = "sprites/skeletonking.png",
    qAbility = AbilityDef(name = "Bone Throw", description = "Hurls a bone at long range.", cooldownMs = 7000, maxRange = 16, damage = 20, projectileType = ProjectileType.BONE_THROW, keybind = "Q"),
    eAbility = AbilityDef(name = "Bone Storm", description = "Unleashes bones in all directions.", cooldownMs = 12000, maxRange = 16, damage = 20, projectileType = ProjectileType.BONE_THROW, keybind = "E", castBehavior = FanProjectile(8, 2 * Math.PI)),
    primaryProjectileType = ProjectileType.BONE_AXE, maxHealth = 110
  )

  val Banshee: CharacterDef = CharacterDef(
    id = CharacterId.Banshee, displayName = "Banshee",
    description = "A wailing specter who pierces walls with shrieks and phases through reality.",
    spriteSheet = "sprites/banshee.png",
    qAbility = AbilityDef(name = "Wail", description = "A piercing shriek that freezes enemies.", cooldownMs = 8000, maxRange = 10, damage = 15, projectileType = ProjectileType.WAIL, keybind = "Q"),
    eAbility = AbilityDef(name = "Ghost Form", description = "Become ethereal: walk through walls.", cooldownMs = 14000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = PhaseShiftBuff(4000)),
    primaryProjectileType = ProjectileType.SOUL_BOLT, maxHealth = 80
  )

  val Lich: CharacterDef = CharacterDef(
    id = CharacterId.Lich, displayName = "Lich",
    description = "An ancient undead sorcerer who drains souls and commands frost.",
    spriteSheet = "sprites/lich.png",
    qAbility = AbilityDef(name = "Frost Shard", description = "Fires a freezing ice shard.", cooldownMs = 5000, maxRange = 18, damage = 16, projectileType = ProjectileType.FROST_SHARD, keybind = "Q"),
    eAbility = AbilityDef(name = "Soul Drain", description = "Drains the soul, healing for 40% of damage.", cooldownMs = 10000, maxRange = 14, damage = 25, projectileType = ProjectileType.SOUL_DRAIN, keybind = "E"),
    primaryProjectileType = ProjectileType.DEATH_BOLT
  )

  val Ghoul: CharacterDef = CharacterDef(
    id = CharacterId.Ghoul, displayName = "Ghoul",
    description = "A feral undead that lunges at prey and devours them for health.",
    spriteSheet = "sprites/ghoul.png",
    qAbility = AbilityDef(name = "Lunge", description = "Dashes at the target.", cooldownMs = 12000, maxRange = 0, damage = 0, projectileType = -1, keybind = "Q", castBehavior = DashBuff(8, 300, 20)),
    eAbility = AbilityDef(name = "Devour", description = "Bites an enemy, healing for 50% of damage.", cooldownMs = 10000, maxRange = 6, damage = 30, projectileType = ProjectileType.DEVOUR, keybind = "E"),
    primaryProjectileType = ProjectileType.CLAW_SWIPE, maxHealth = 110
  )

  val ReaperChar: CharacterDef = CharacterDef(
    id = CharacterId.Reaper, displayName = "Reaper",
    description = "Death incarnate, wielding a scythe that cuts through walls.",
    spriteSheet = "sprites/reaper.png",
    qAbility = AbilityDef(name = "Reap", description = "A scythe swing that passes through walls.", cooldownMs = 10000, maxRange = 8, damage = 35, projectileType = ProjectileType.REAP, keybind = "Q"),
    eAbility = AbilityDef(name = "Death Form", description = "Become death itself: phased and invulnerable.", cooldownMs = 14000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = PhaseShiftBuff(3000)),
    primaryProjectileType = ProjectileType.SCYTHE, maxHealth = 90
  )

  val Shade: CharacterDef = CharacterDef(
    id = CharacterId.Shade, displayName = "Shade",
    description = "A shadow entity that phases through walls and bursts from the dark.",
    spriteSheet = "sprites/shade.png",
    qAbility = AbilityDef(name = "Shadow Phase", description = "Become a shadow: walk through walls.", cooldownMs = 12000, maxRange = 0, damage = 0, projectileType = -1, keybind = "Q", castBehavior = PhaseShiftBuff(5000)),
    eAbility = AbilityDef(name = "Shadow Burst", description = "Fires 5 shadow bolts in a fan through walls.", cooldownMs = 10000, maxRange = 16, damage = 15, projectileType = ProjectileType.SHADOW_BOLT, keybind = "E", castBehavior = FanProjectile(5, Math.toRadians(60))),
    primaryProjectileType = ProjectileType.SHADOW_BOLT, maxHealth = 80
  )

  val Revenant: CharacterDef = CharacterDef(
    id = CharacterId.Revenant, displayName = "Revenant",
    description = "An undying warrior who dashes and drains life from foes.",
    spriteSheet = "sprites/revenant.png",
    qAbility = AbilityDef(name = "Spectral Charge", description = "Dashes forward through enemies.", cooldownMs = 10000, maxRange = 0, damage = 0, projectileType = -1, keybind = "Q", castBehavior = DashBuff(10, 350, 20)),
    eAbility = AbilityDef(name = "Life Drain", description = "Drains life, healing for 35% of damage.", cooldownMs = 10000, maxRange = 14, damage = 20, projectileType = ProjectileType.LIFE_DRAIN, keybind = "E"),
    primaryProjectileType = ProjectileType.CURSED_BLADE, maxHealth = 110
  )

  val Gravedigger: CharacterDef = CharacterDef(
    id = CharacterId.Gravedigger, displayName = "Gravedigger",
    description = "A graveyard keeper who hurls bones and detonates grave dirt.",
    spriteSheet = "sprites/gravedigger.png",
    qAbility = AbilityDef(name = "Bone Throw", description = "Hurls a bone projectile.", cooldownMs = 7000, maxRange = 16, damage = 20, projectileType = ProjectileType.BONE_THROW, keybind = "Q"),
    eAbility = AbilityDef(name = "Grave Dirt", description = "Explosive dirt that slows enemies.", cooldownMs = 10000, maxRange = 14, damage = 20, projectileType = ProjectileType.MUD_BOMB, keybind = "E"),
    primaryProjectileType = ProjectileType.SHOVEL
  )

  val Dullahan: CharacterDef = CharacterDef(
    id = CharacterId.Dullahan, displayName = "Dullahan",
    description = "A headless horseman who throws his cursed head and charges.",
    spriteSheet = "sprites/dullahan.png",
    qAbility = AbilityDef(name = "Head Throw", description = "Throws cursed head that freezes on hit.", cooldownMs = 10000, maxRange = 16, damage = 25, projectileType = ProjectileType.HEAD_THROW, keybind = "Q"),
    eAbility = AbilityDef(name = "Headless Charge", description = "Charges forward on spectral steed.", cooldownMs = 12000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = DashBuff(12, 400, 20)),
    primaryProjectileType = ProjectileType.CURSED_BLADE, maxHealth = 110
  )

  val Phantom: CharacterDef = CharacterDef(
    id = CharacterId.Phantom, displayName = "Phantom",
    description = "A ghostly figure that phases for long durations and haunts targets.",
    spriteSheet = "sprites/phantom.png",
    qAbility = AbilityDef(name = "Long Phase", description = "Extended ethereal form.", cooldownMs = 16000, maxRange = 0, damage = 0, projectileType = -1, keybind = "Q", castBehavior = PhaseShiftBuff(6000)),
    eAbility = AbilityDef(name = "Haunt", description = "Teleports behind the target on hit.", cooldownMs = 15000, maxRange = 15, damage = 30, projectileType = ProjectileType.HAUNT, keybind = "E"),
    primaryProjectileType = ProjectileType.SHADOW_BOLT, maxHealth = 80
  )

  val MummyChar: CharacterDef = CharacterDef(
    id = CharacterId.Mummy, displayName = "Mummy",
    description = "An ancient wrapped horror that pulls with bandages and curses foes.",
    spriteSheet = "sprites/mummy.png",
    qAbility = AbilityDef(name = "Bandage Whip", description = "Wraps bandages around an enemy and pulls them.", cooldownMs = 8000, maxRange = 16, damage = 10, projectileType = ProjectileType.BANDAGE_WHIP, keybind = "Q"),
    eAbility = AbilityDef(name = "Curse", description = "Ancient curse that freezes for 2 seconds.", cooldownMs = 12000, maxRange = 12, damage = 20, projectileType = ProjectileType.CURSE, keybind = "E"),
    primaryProjectileType = ProjectileType.SAND_SHOT
  )

  val Deathknight: CharacterDef = CharacterDef(
    id = CharacterId.Deathknight, displayName = "Death Knight",
    description = "An armored undead knight with cursed blade and unholy charge.",
    spriteSheet = "sprites/deathknight.png",
    qAbility = AbilityDef(name = "Death Bolt", description = "Fires a death bolt through walls.", cooldownMs = 6000, maxRange = 16, damage = 16, projectileType = ProjectileType.DEATH_BOLT, keybind = "Q"),
    eAbility = AbilityDef(name = "Unholy Charge", description = "Charges forward with dark energy.", cooldownMs = 12000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = DashBuff(10, 400, 22)),
    primaryProjectileType = ProjectileType.CURSED_BLADE, maxHealth = 120
  )

  val Shadowfiend: CharacterDef = CharacterDef(
    id = CharacterId.Shadowfiend, displayName = "Shadowfiend",
    description = "A shadow demon that fans dark bolts and slips into shadow form.",
    spriteSheet = "sprites/shadowfiend.png",
    qAbility = AbilityDef(name = "Shadow Fan", description = "Fires 5 shadow bolts in a fan.", cooldownMs = 8000, maxRange = 16, damage = 15, projectileType = ProjectileType.SHADOW_BOLT, keybind = "Q", castBehavior = FanProjectile(5, Math.toRadians(60))),
    eAbility = AbilityDef(name = "Shadow Form", description = "Dissolve into shadow: phased and immune.", cooldownMs = 14000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = PhaseShiftBuff(4000)),
    primaryProjectileType = ProjectileType.SHADOW_BOLT
  )

  val Poltergeist: CharacterDef = CharacterDef(
    id = CharacterId.Poltergeist, displayName = "Poltergeist",
    description = "A mischievous spirit that flings soul bolts and blinks around.",
    spriteSheet = "sprites/poltergeist.png",
    qAbility = AbilityDef(name = "Soul Fan", description = "Fires 3 soul bolts in a fan.", cooldownMs = 7000, maxRange = 15, damage = 15, projectileType = ProjectileType.SOUL_BOLT, keybind = "Q", castBehavior = FanProjectile(3, Math.toRadians(30))),
    eAbility = AbilityDef(name = "Blink", description = "Teleports to cursor.", cooldownMs = 12000, maxRange = 10, damage = 0, projectileType = -2, keybind = "E", castBehavior = TeleportCast(10)),
    primaryProjectileType = ProjectileType.SOUL_BOLT, maxHealth = 80
  )

  // === Batch 3: Medieval/Fantasy (IDs 42-56) ===

  val PaladinChar: CharacterDef = CharacterDef(
    id = CharacterId.Paladin, displayName = "Paladin",
    description = "A holy knight who smites with radiant energy and holy nova.",
    spriteSheet = "sprites/paladin.png",
    qAbility = AbilityDef(name = "Holy Bolt", description = "Fires a bolt of holy light.", cooldownMs = 6000, maxRange = 16, damage = 20, projectileType = ProjectileType.HOLY_BOLT, keybind = "Q"),
    eAbility = AbilityDef(name = "Holy Nova", description = "Unleashes holy bolts in all directions.", cooldownMs = 12000, maxRange = 16, damage = 20, projectileType = ProjectileType.HOLY_BOLT, keybind = "E", castBehavior = FanProjectile(8, 2 * Math.PI)),
    primaryProjectileType = ProjectileType.HOLY_BLADE, maxHealth = 120
  )

  val RangerChar: CharacterDef = CharacterDef(
    id = CharacterId.Ranger, displayName = "Ranger",
    description = "A skilled archer with poison arrows and multi-shot.",
    spriteSheet = "sprites/ranger.png",
    qAbility = AbilityDef(name = "Poison Arrow", description = "Arrow that slows on hit.", cooldownMs = 7000, maxRange = 20, damage = 12, projectileType = ProjectileType.POISON_ARROW, keybind = "Q"),
    eAbility = AbilityDef(name = "Multi Shot", description = "Fires 3 arrows in a tight fan.", cooldownMs = 8000, maxRange = 22, damage = 18, projectileType = ProjectileType.ARROW, keybind = "E", castBehavior = FanProjectile(3, Math.toRadians(20))),
    primaryProjectileType = ProjectileType.ARROW, maxHealth = 85
  )

  val BerserkerChar: CharacterDef = CharacterDef(
    id = CharacterId.Berserker, displayName = "Berserker",
    description = "A raging warrior who charges and spins axes.",
    spriteSheet = "sprites/berserker.png",
    qAbility = AbilityDef(name = "Rage Charge", description = "Charges forward in a rage.", cooldownMs = 10000, maxRange = 0, damage = 0, projectileType = -1, keybind = "Q", castBehavior = DashBuff(10, 350, 18)),
    eAbility = AbilityDef(name = "Axe Spin", description = "Spins axes in all directions.", cooldownMs = 12000, maxRange = 5, damage = 33, projectileType = ProjectileType.AXE, keybind = "E", castBehavior = FanProjectile(8, 2 * Math.PI)),
    primaryProjectileType = ProjectileType.AXE, maxHealth = 120
  )

  val CrusaderChar: CharacterDef = CharacterDef(
    id = CharacterId.Crusader, displayName = "Crusader",
    description = "A holy warrior who charges with a shield and smites from range.",
    spriteSheet = "sprites/crusader.png",
    qAbility = AbilityDef(name = "Shield Charge", description = "Charges forward with shield.", cooldownMs = 10000, maxRange = 0, damage = 0, projectileType = -1, keybind = "Q", castBehavior = DashBuff(8, 350, 20)),
    eAbility = AbilityDef(name = "Holy Bolt", description = "Fires a holy bolt.", cooldownMs = 6000, maxRange = 16, damage = 20, projectileType = ProjectileType.HOLY_BOLT, keybind = "E"),
    primaryProjectileType = ProjectileType.HOLY_BLADE, maxHealth = 110
  )

  val DruidChar: CharacterDef = CharacterDef(
    id = CharacterId.Druid, displayName = "Druid",
    description = "A nature shapeshifter with vine pulls and animal form.",
    spriteSheet = "sprites/druid.png",
    qAbility = AbilityDef(name = "Vine Whip", description = "A vine that pulls enemies.", cooldownMs = 8000, maxRange = 18, damage = 8, projectileType = ProjectileType.VINE_WHIP, keybind = "Q"),
    eAbility = AbilityDef(name = "Animal Form", description = "Shift into animal form: phased.", cooldownMs = 14000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = PhaseShiftBuff(4000)),
    primaryProjectileType = ProjectileType.THORN
  )

  val BardChar: CharacterDef = CharacterDef(
    id = CharacterId.Bard, displayName = "Bard",
    description = "A musical warrior with sonic waves that pass through walls.",
    spriteSheet = "sprites/bard.png",
    qAbility = AbilityDef(name = "Sonic Fan", description = "Fires 5 sonic waves in a fan.", cooldownMs = 8000, maxRange = 14, damage = 14, projectileType = ProjectileType.SONIC_WAVE, keybind = "Q", castBehavior = FanProjectile(5, Math.toRadians(90))),
    eAbility = AbilityDef(name = "Sonic Boom", description = "Blast that pushes enemies back.", cooldownMs = 10000, maxRange = 8, damage = 10, projectileType = ProjectileType.SONIC_BOOM, keybind = "E"),
    primaryProjectileType = ProjectileType.SONIC_WAVE
  )

  val MonkChar: CharacterDef = CharacterDef(
    id = CharacterId.Monk, displayName = "Monk",
    description = "A martial artist who dashes and unleashes flurries of punches.",
    spriteSheet = "sprites/monk.png",
    qAbility = AbilityDef(name = "Palm Strike", description = "Dashes forward with a palm strike.", cooldownMs = 12000, maxRange = 0, damage = 0, projectileType = -1, keybind = "Q", castBehavior = DashBuff(8, 300, 15)),
    eAbility = AbilityDef(name = "Flurry", description = "Punches in all directions.", cooldownMs = 10000, maxRange = 4, damage = 22, projectileType = ProjectileType.FIST, keybind = "E", castBehavior = FanProjectile(8, 2 * Math.PI)),
    primaryProjectileType = ProjectileType.FIST, maxHealth = 90
  )

  val ClericChar: CharacterDef = CharacterDef(
    id = CharacterId.Cleric, displayName = "Cleric",
    description = "A divine caster with smiting power and holy nova.",
    spriteSheet = "sprites/cleric.png",
    qAbility = AbilityDef(name = "Smite", description = "Holy smite with AoE splash.", cooldownMs = 8000, maxRange = 14, damage = 30, projectileType = ProjectileType.SMITE, keybind = "Q"),
    eAbility = AbilityDef(name = "Blessing", description = "Holy bolts in all directions.", cooldownMs = 12000, maxRange = 16, damage = 20, projectileType = ProjectileType.HOLY_BOLT, keybind = "E", castBehavior = FanProjectile(8, 2 * Math.PI)),
    primaryProjectileType = ProjectileType.HOLY_BOLT
  )

  val RogueChar: CharacterDef = CharacterDef(
    id = CharacterId.Rogue, displayName = "Rogue",
    description = "A stealthy fighter who fans shurikens and vanishes into the shadows.",
    spriteSheet = "sprites/rogue.png",
    qAbility = AbilityDef(name = "Knife Spray", description = "Throws 3 shurikens in a fan.", cooldownMs = 7000, maxRange = 6, damage = 22, projectileType = ProjectileType.SHURIKEN, keybind = "Q", castBehavior = FanProjectile(3, Math.toRadians(30))),
    eAbility = AbilityDef(name = "Vanish", description = "Disappear into shadows: phased and immune.", cooldownMs = 14000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = PhaseShiftBuff(3000)),
    primaryProjectileType = ProjectileType.SHURIKEN, maxHealth = 80
  )

  val BarbarianChar: CharacterDef = CharacterDef(
    id = CharacterId.Barbarian, displayName = "Barbarian",
    description = "A savage warrior with triple axes and a frenzy charge.",
    spriteSheet = "sprites/barbarian.png",
    qAbility = AbilityDef(name = "Triple Axe", description = "Throws 3 axes in a fan.", cooldownMs = 8000, maxRange = 5, damage = 33, projectileType = ProjectileType.AXE, keybind = "Q", castBehavior = FanProjectile(3, Math.toRadians(40))),
    eAbility = AbilityDef(name = "Frenzy Charge", description = "Charges forward in a frenzy.", cooldownMs = 12000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = DashBuff(12, 450, 25)),
    primaryProjectileType = ProjectileType.AXE, maxHealth = 120
  )

  val EnchanterChar: CharacterDef = CharacterDef(
    id = CharacterId.Enchantress, displayName = "Enchantress",
    description = "A mystical enchantress who charms foes and blinks away.",
    spriteSheet = "sprites/enchantress.png",
    qAbility = AbilityDef(name = "Charm", description = "Mesmerizes an enemy, freezing for 2s.", cooldownMs = 10000, maxRange = 14, damage = 15, projectileType = ProjectileType.CHARM, keybind = "Q"),
    eAbility = AbilityDef(name = "Blink", description = "Teleports to cursor.", cooldownMs = 12000, maxRange = 7, damage = 0, projectileType = -2, keybind = "E", castBehavior = TeleportCast(7)),
    primaryProjectileType = ProjectileType.ARCANE_BOLT, maxHealth = 80
  )

  val JesterChar: CharacterDef = CharacterDef(
    id = CharacterId.Jester, displayName = "Jester",
    description = "A tricky jester who flings cards and teleports unpredictably.",
    spriteSheet = "sprites/jester.png",
    qAbility = AbilityDef(name = "Card Fan", description = "Throws 3 cards in a fan.", cooldownMs = 6000, maxRange = 14, damage = 16, projectileType = ProjectileType.CARD, keybind = "Q", castBehavior = FanProjectile(3, Math.toRadians(30))),
    eAbility = AbilityDef(name = "Trick", description = "Teleports to cursor.", cooldownMs = 12000, maxRange = 6, damage = 0, projectileType = -2, keybind = "E", castBehavior = TeleportCast(6)),
    primaryProjectileType = ProjectileType.CARD, maxHealth = 85
  )

  val ValkyrieChar: CharacterDef = CharacterDef(
    id = CharacterId.Valkyrie, displayName = "Valkyrie",
    description = "A winged warrior with distance-scaling spears and aerial charge.",
    spriteSheet = "sprites/valkyrie.png",
    qAbility = AbilityDef(name = "Aerial Charge", description = "Dashes forward through the air.", cooldownMs = 10000, maxRange = 0, damage = 0, projectileType = -1, keybind = "Q", castBehavior = DashBuff(10, 350, 20)),
    eAbility = AbilityDef(name = "Spear Fan", description = "Throws 5 spears in a fan.", cooldownMs = 12000, maxRange = 20, damage = 10, projectileType = ProjectileType.SPEAR, keybind = "E", castBehavior = FanProjectile(5, Math.toRadians(60))),
    primaryProjectileType = ProjectileType.SPEAR, maxHealth = 110
  )

  val WarlockChar: CharacterDef = CharacterDef(
    id = CharacterId.Warlock, displayName = "Warlock",
    description = "A dark caster with death bolts, fireballs, and death fans.",
    spriteSheet = "sprites/warlock.png",
    qAbility = AbilityDef(name = "Demon Fire", description = "Launches a demonic fireball.", cooldownMs = 6000, maxRange = 18, damage = 45, projectileType = ProjectileType.FIREBALL, keybind = "Q"),
    eAbility = AbilityDef(name = "Death Fan", description = "Fires 3 death bolts in a fan through walls.", cooldownMs = 10000, maxRange = 16, damage = 16, projectileType = ProjectileType.DEATH_BOLT, keybind = "E", castBehavior = FanProjectile(3, Math.toRadians(30))),
    primaryProjectileType = ProjectileType.DEATH_BOLT
  )

  val InquisitorChar: CharacterDef = CharacterDef(
    id = CharacterId.Inquisitor, displayName = "Inquisitor",
    description = "A righteous judge who chains and smites the guilty.",
    spriteSheet = "sprites/inquisitor.png",
    qAbility = AbilityDef(name = "Chain Bolt", description = "Electrified chain that briefly freezes.", cooldownMs = 6000, maxRange = 14, damage = 12, projectileType = ProjectileType.CHAIN_BOLT, keybind = "Q"),
    eAbility = AbilityDef(name = "Judgment", description = "Holy smite with AoE splash.", cooldownMs = 10000, maxRange = 14, damage = 30, projectileType = ProjectileType.SMITE, keybind = "E"),
    primaryProjectileType = ProjectileType.HOLY_BOLT
  )

  // === Batch 4: Sci-Fi/Tech (IDs 57-71) ===

  val CyborgChar: CharacterDef = CharacterDef(
    id = CharacterId.Cyborg, displayName = "Cyborg",
    description = "A cybernetic soldier with bullets, rockets, and jet boost.",
    spriteSheet = "sprites/cyborg.png",
    qAbility = AbilityDef(name = "Arm Rocket", description = "Fires a rocket from the arm.", cooldownMs = 12000, maxRange = 20, damage = 35, projectileType = ProjectileType.ROCKET, keybind = "Q"),
    eAbility = AbilityDef(name = "Jet Boost", description = "Jet-powered dash forward.", cooldownMs = 12000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = DashBuff(8, 300, 18)),
    primaryProjectileType = ProjectileType.BULLET, maxHealth = 110
  )

  val HackerChar: CharacterDef = CharacterDef(
    id = CharacterId.Hacker, displayName = "Hacker",
    description = "A digital infiltrator with data bolts and system hops.",
    spriteSheet = "sprites/hacker.png",
    qAbility = AbilityDef(name = "Virus", description = "Digital virus that freezes systems.", cooldownMs = 8000, maxRange = 14, damage = 10, projectileType = ProjectileType.VIRUS, keybind = "Q"),
    eAbility = AbilityDef(name = "System Hop", description = "Teleports through the network.", cooldownMs = 12000, maxRange = 8, damage = 0, projectileType = -2, keybind = "E", castBehavior = TeleportCast(8)),
    primaryProjectileType = ProjectileType.DATA_BOLT, maxHealth = 80
  )

  val MechPilotChar: CharacterDef = CharacterDef(
    id = CharacterId.MechPilot, displayName = "Mech Pilot",
    description = "A heavy mech with minigun, suppression chains, and ground slam.",
    spriteSheet = "sprites/mechpilot.png",
    qAbility = AbilityDef(name = "Suppress Fire", description = "Fires 3 lockdown chains in a fan.", cooldownMs = 8000, maxRange = 12, damage = 8, projectileType = ProjectileType.LOCKDOWN_CHAIN, keybind = "Q", castBehavior = FanProjectile(3, Math.toRadians(40))),
    eAbility = AbilityDef(name = "Mech Slam", description = "Slams mech fist into the ground with devastating AoE.", cooldownMs = 12000, maxRange = 12, damage = 25, projectileType = ProjectileType.SEISMIC_SLAM, keybind = "E"),
    primaryProjectileType = ProjectileType.BULLET, maxHealth = 120
  )

  val AndroidChar: CharacterDef = CharacterDef(
    id = CharacterId.Android, displayName = "Android",
    description = "A precision android with laser beams and jet dash.",
    spriteSheet = "sprites/android.png",
    qAbility = AbilityDef(name = "Laser Fan", description = "Fires 3 lasers in a tight fan.", cooldownMs = 7000, maxRange = 20, damage = 16, projectileType = ProjectileType.LASER, keybind = "Q", castBehavior = FanProjectile(3, Math.toRadians(20))),
    eAbility = AbilityDef(name = "Jet Dash", description = "Quick jet-powered dash.", cooldownMs = 12000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = DashBuff(8, 300, 15)),
    primaryProjectileType = ProjectileType.LASER
  )

  val ChronomancerChar: CharacterDef = CharacterDef(
    id = CharacterId.Chronomancer, displayName = "Chronomancer",
    description = "A time mage with lightning-fast attacks, gravity wells, and time stops.",
    spriteSheet = "sprites/chronomancer.png",
    qAbility = AbilityDef(name = "Temporal Rift", description = "Opens a gravity well that traps enemies.", cooldownMs = 14000, maxRange = 12, damage = 25, projectileType = ProjectileType.GRAVITY_WELL, keybind = "Q"),
    eAbility = AbilityDef(name = "Time Stop", description = "Freezes time around yourself, becoming phased and immune.", cooldownMs = 14000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = PhaseShiftBuff(3000)),
    primaryProjectileType = ProjectileType.LIGHTNING
  )

  val GravitonChar: CharacterDef = CharacterDef(
    id = CharacterId.Graviton, displayName = "Graviton",
    description = "A gravity manipulator who pulls enemies and creates gravity wells.",
    spriteSheet = "sprites/graviton.png",
    qAbility = AbilityDef(name = "Gravity Fan", description = "Fires 5 gravity balls in a fan.", cooldownMs = 10000, maxRange = 14, damage = 18, projectileType = ProjectileType.GRAVITY_BALL, keybind = "Q", castBehavior = FanProjectile(5, Math.toRadians(60))),
    eAbility = AbilityDef(name = "Gravity Lock", description = "Launches a gravity field that roots enemies on landing.", cooldownMs = 14000, maxRange = 12, damage = 15, projectileType = ProjectileType.GRAVITY_LOCK, keybind = "E"),
    primaryProjectileType = ProjectileType.GRAVITY_BALL
  )

  val TeslaChar: CharacterDef = CharacterDef(
    id = CharacterId.Tesla, displayName = "Tesla",
    description = "An electric genius with chain lightning and tesla coils.",
    spriteSheet = "sprites/tesla.png",
    qAbility = AbilityDef(name = "Chain Lightning", description = "Electric bolt that chains to nearby enemies.", cooldownMs = 8000, maxRange = 14, damage = 20, projectileType = ProjectileType.CHAIN_LIGHTNING, keybind = "Q"),
    eAbility = AbilityDef(name = "Tesla Coil", description = "Deploys a shocking coil that stuns nearby enemies.", cooldownMs = 12000, maxRange = 10, damage = 20, projectileType = ProjectileType.TESLA_COIL, keybind = "E"),
    primaryProjectileType = ProjectileType.LIGHTNING
  )

  val NanoswarmChar: CharacterDef = CharacterDef(
    id = CharacterId.Nanoswarm, displayName = "Nanoswarm",
    description = "A nanite cloud that dissolves and reforms at will.",
    spriteSheet = "sprites/nanoswarm.png",
    qAbility = AbilityDef(name = "Nano Fan", description = "Fires 5 nano bolts in a fan.", cooldownMs = 8000, maxRange = 16, damage = 12, projectileType = ProjectileType.NANO_BOLT, keybind = "Q", castBehavior = FanProjectile(5, Math.toRadians(45))),
    eAbility = AbilityDef(name = "Dissolve", description = "Dissolve into nanites: phased.", cooldownMs = 14000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = PhaseShiftBuff(4000)),
    primaryProjectileType = ProjectileType.NANO_BOLT
  )

  val VoidwalkerChar: CharacterDef = CharacterDef(
    id = CharacterId.Voidwalker, displayName = "Voidwalker",
    description = "A void entity that pulls enemies with gravity fans and teleports at will.",
    spriteSheet = "sprites/voidwalker.png",
    qAbility = AbilityDef(name = "Void Pull", description = "Fires 5 void gravity balls that pull enemies.", cooldownMs = 10000, maxRange = 14, damage = 18, projectileType = ProjectileType.GRAVITY_BALL, keybind = "Q", castBehavior = FanProjectile(5, Math.toRadians(60))),
    eAbility = AbilityDef(name = "Void Step", description = "Teleport through the void.", cooldownMs = 12000, maxRange = 10, damage = 0, projectileType = -2, keybind = "E", castBehavior = TeleportCast(10)),
    primaryProjectileType = ProjectileType.VOID_BOLT, maxHealth = 80
  )

  val PhotonChar: CharacterDef = CharacterDef(
    id = CharacterId.Photon, displayName = "Photon",
    description = "A being of pure light with laser fans and prismatic explosions.",
    spriteSheet = "sprites/photon.png",
    qAbility = AbilityDef(name = "Light Spray", description = "Fires 5 lasers in a fan.", cooldownMs = 8000, maxRange = 20, damage = 16, projectileType = ProjectileType.LASER, keybind = "Q", castBehavior = FanProjectile(5, Math.toRadians(60))),
    eAbility = AbilityDef(name = "Prismatic Burst", description = "Fires lasers in all directions.", cooldownMs = 10000, maxRange = 20, damage = 16, projectileType = ProjectileType.LASER, keybind = "E", castBehavior = FanProjectile(8, 2 * Math.PI)),
    primaryProjectileType = ProjectileType.LASER, maxHealth = 80
  )

  val RailgunnerChar: CharacterDef = CharacterDef(
    id = CharacterId.Railgunner, displayName = "Railgunner",
    description = "A sniper with a devastating railgun that pierces everything.",
    spriteSheet = "sprites/railgunner.png",
    qAbility = AbilityDef(name = "Frag Grenade", description = "Thrown explosive grenade.", cooldownMs = 10000, maxRange = 12, damage = 40, projectileType = ProjectileType.GRENADE, keybind = "Q"),
    eAbility = AbilityDef(name = "Smoke Screen", description = "Deploys smoke cover, becoming phased and immune.", cooldownMs = 14000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = PhaseShiftBuff(2000)),
    primaryProjectileType = ProjectileType.RAILGUN, maxHealth = 80
  )

  val BombardierChar: CharacterDef = CharacterDef(
    id = CharacterId.Bombardier, displayName = "Bombardier",
    description = "An explosives expert with grenades, rockets, and cluster bombs.",
    spriteSheet = "sprites/bombardier.png",
    qAbility = AbilityDef(name = "Rocket", description = "Fires an explosive rocket.", cooldownMs = 12000, maxRange = 20, damage = 35, projectileType = ProjectileType.ROCKET, keybind = "Q"),
    eAbility = AbilityDef(name = "Cluster Bomb", description = "Deploys a devastating cluster bomb.", cooldownMs = 16000, maxRange = 14, damage = 35, projectileType = ProjectileType.CLUSTER_BOMB, keybind = "E"),
    primaryProjectileType = ProjectileType.GRENADE
  )

  val SentinelChar: CharacterDef = CharacterDef(
    id = CharacterId.Sentinel, displayName = "Sentinel",
    description = "A defensive specialist with lockdown chains and snare mines.",
    spriteSheet = "sprites/sentinel.png",
    qAbility = AbilityDef(name = "Suppress", description = "Fires a lockdown chain.", cooldownMs = 8000, maxRange = 12, damage = 8, projectileType = ProjectileType.LOCKDOWN_CHAIN, keybind = "Q"),
    eAbility = AbilityDef(name = "Deploy Mine", description = "Deploys a snare mine.", cooldownMs = 14000, maxRange = 16, damage = 15, projectileType = ProjectileType.SNARE_MINE, keybind = "E"),
    primaryProjectileType = ProjectileType.BULLET, maxHealth = 110
  )

  val PilotChar: CharacterDef = CharacterDef(
    id = CharacterId.Pilot, displayName = "Pilot",
    description = "An ace pilot with sidearm, grenades, and strafing runs.",
    spriteSheet = "sprites/pilot.png",
    qAbility = AbilityDef(name = "Airdrop Grenade", description = "Drops an explosive grenade.", cooldownMs = 10000, maxRange = 12, damage = 40, projectileType = ProjectileType.GRENADE, keybind = "Q"),
    eAbility = AbilityDef(name = "Strafing Run", description = "Fires 3 bullets in a tight spread.", cooldownMs = 8000, maxRange = 18, damage = 20, projectileType = ProjectileType.BULLET, keybind = "E", castBehavior = FanProjectile(3, Math.toRadians(20))),
    primaryProjectileType = ProjectileType.BULLET
  )

  val GlitcherChar: CharacterDef = CharacterDef(
    id = CharacterId.Glitcher, displayName = "Glitcher",
    description = "A reality glitch that blinks and fires data bursts.",
    spriteSheet = "sprites/glitcher.png",
    qAbility = AbilityDef(name = "Glitch Blink", description = "Teleports through a glitch.", cooldownMs = 12000, maxRange = 6, damage = 0, projectileType = -2, keybind = "Q", castBehavior = TeleportCast(6)),
    eAbility = AbilityDef(name = "Data Burst", description = "Fires data bolts in all directions.", cooldownMs = 10000, maxRange = 16, damage = 14, projectileType = ProjectileType.DATA_BOLT, keybind = "E", castBehavior = FanProjectile(8, 2 * Math.PI)),
    primaryProjectileType = ProjectileType.DATA_BOLT, maxHealth = 80
  )

  // === Batch 5: Nature/Beast (IDs 72-86) ===

  val WolfChar: CharacterDef = CharacterDef(
    id = CharacterId.Wolf, displayName = "Wolf",
    description = "A fierce predator that pounces and slashes in packs.",
    spriteSheet = "sprites/wolf.png",
    qAbility = AbilityDef(name = "Pounce", description = "Leaps at prey.", cooldownMs = 12000, maxRange = 0, damage = 0, projectileType = -1, keybind = "Q", castBehavior = DashBuff(10, 350, 18)),
    eAbility = AbilityDef(name = "Triple Slash", description = "Slashes 3 times in a fan.", cooldownMs = 8000, maxRange = 4, damage = 25, projectileType = ProjectileType.CLAW_SWIPE, keybind = "E", castBehavior = FanProjectile(3, Math.toRadians(30))),
    primaryProjectileType = ProjectileType.CLAW_SWIPE, maxHealth = 90
  )

  val SerpentChar: CharacterDef = CharacterDef(
    id = CharacterId.Serpent, displayName = "Serpent",
    description = "A venomous snake that poisons and slithers away.",
    spriteSheet = "sprites/serpent.png",
    qAbility = AbilityDef(name = "Venom Spit", description = "Spits venom that slows.", cooldownMs = 7000, maxRange = 16, damage = 15, projectileType = ProjectileType.POISON_DART, keybind = "Q"),
    eAbility = AbilityDef(name = "Slither", description = "Dashes sideways.", cooldownMs = 12000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = DashBuff(8, 300, 15)),
    primaryProjectileType = ProjectileType.VENOM_BOLT
  )

  val SpiderChar: CharacterDef = CharacterDef(
    id = CharacterId.Spider, displayName = "Spider",
    description = "A web-spinning arachnid that traps and burrows.",
    spriteSheet = "sprites/spider.png",
    qAbility = AbilityDef(name = "Web Spray", description = "Fires 5 webs in a fan.", cooldownMs = 10000, maxRange = 14, damage = 12, projectileType = ProjectileType.WEB_SHOT, keybind = "Q", castBehavior = FanProjectile(5, Math.toRadians(45))),
    eAbility = AbilityDef(name = "Web Trap", description = "Launches a web that roots enemies on landing.", cooldownMs = 12000, maxRange = 14, damage = 8, projectileType = ProjectileType.WEB_TRAP, keybind = "E"),
    primaryProjectileType = ProjectileType.WEB_SHOT
  )

  val BearChar: CharacterDef = CharacterDef(
    id = CharacterId.Bear, displayName = "Bear",
    description = "A powerful bear that charges and mauls in all directions.",
    spriteSheet = "sprites/bear.png",
    qAbility = AbilityDef(name = "Bear Charge", description = "Charges forward.", cooldownMs = 10000, maxRange = 0, damage = 0, projectileType = -1, keybind = "Q", castBehavior = DashBuff(8, 400, 25)),
    eAbility = AbilityDef(name = "Maul", description = "Swipes claws in all directions.", cooldownMs = 10000, maxRange = 4, damage = 25, projectileType = ProjectileType.CLAW_SWIPE, keybind = "E", castBehavior = FanProjectile(8, 2 * Math.PI)),
    primaryProjectileType = ProjectileType.CLAW_SWIPE, maxHealth = 120
  )

  val ScorpionChar: CharacterDef = CharacterDef(
    id = CharacterId.Scorpion, displayName = "Scorpion",
    description = "A venomous scorpion with stinger strikes and tail spit.",
    spriteSheet = "sprites/scorpion.png",
    qAbility = AbilityDef(name = "Tail Spit", description = "Spits venom from the tail.", cooldownMs = 7000, maxRange = 16, damage = 15, projectileType = ProjectileType.POISON_DART, keybind = "Q"),
    eAbility = AbilityDef(name = "Burrow Dash", description = "Burrows and dashes.", cooldownMs = 10000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = DashBuff(8, 300, 20)),
    primaryProjectileType = ProjectileType.STINGER
  )

  val HawkChar: CharacterDef = CharacterDef(
    id = CharacterId.Hawk, displayName = "Hawk",
    description = "A swift bird of prey with piercing screeches and wind gusts.",
    spriteSheet = "sprites/hawk.png",
    qAbility = AbilityDef(name = "Screech", description = "Fires 5 gusts in a wide fan that push enemies.", cooldownMs = 10000, maxRange = 6, damage = 10, projectileType = ProjectileType.GUST, keybind = "Q", castBehavior = FanProjectile(5, Math.toRadians(90))),
    eAbility = AbilityDef(name = "Wing Beat", description = "Pushes enemies with wind.", cooldownMs = 8000, maxRange = 6, damage = 10, projectileType = ProjectileType.GUST, keybind = "E"),
    primaryProjectileType = ProjectileType.TALON, maxHealth = 85
  )

  val SharkChar: CharacterDef = CharacterDef(
    id = CharacterId.Shark, displayName = "Shark",
    description = "A ferocious ocean predator that drags prey close and dives through terrain.",
    spriteSheet = "sprites/shark.png",
    qAbility = AbilityDef(name = "Jaw Drag", description = "Bites and pulls an enemy toward you.", cooldownMs = 8000, maxRange = 18, damage = 10, projectileType = ProjectileType.ROPE, keybind = "Q"),
    eAbility = AbilityDef(name = "Deep Dive", description = "Submerges and swims through terrain, immune.", cooldownMs = 14000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = PhaseShiftBuff(3000)),
    primaryProjectileType = ProjectileType.CLAW_SWIPE, maxHealth = 110
  )

  val BeetleChar: CharacterDef = CharacterDef(
    id = CharacterId.Beetle, displayName = "Beetle",
    description = "An armored beetle that charges and spins its shell.",
    spriteSheet = "sprites/beetle.png",
    qAbility = AbilityDef(name = "Rolling Charge", description = "Rolls forward.", cooldownMs = 10000, maxRange = 0, damage = 0, projectileType = -1, keybind = "Q", castBehavior = DashBuff(8, 400, 25)),
    eAbility = AbilityDef(name = "Shell Spin", description = "Spins shell in all directions.", cooldownMs = 12000, maxRange = 8, damage = 35, projectileType = ProjectileType.BOULDER, keybind = "E", castBehavior = FanProjectile(8, 2 * Math.PI)),
    primaryProjectileType = ProjectileType.BOULDER, maxHealth = 120
  )

  val TreantChar: CharacterDef = CharacterDef(
    id = CharacterId.Treant, displayName = "Treant",
    description = "A living tree with root pulls and thorn bursts.",
    spriteSheet = "sprites/treant.png",
    qAbility = AbilityDef(name = "Root Pull", description = "Roots that pull an enemy.", cooldownMs = 8000, maxRange = 18, damage = 8, projectileType = ProjectileType.VINE_WHIP, keybind = "Q"),
    eAbility = AbilityDef(name = "Root Growth", description = "Roots erupt from the ground, entangling nearby enemies.", cooldownMs = 14000, maxRange = 0, damage = 10, projectileType = ProjectileType.ROOT_GROWTH, keybind = "E", castBehavior = GroundSlam(5.0f)),
    primaryProjectileType = ProjectileType.THORN, maxHealth = 120
  )

  val PhoenixChar: CharacterDef = CharacterDef(
    id = CharacterId.Phoenix, displayName = "Phoenix",
    description = "A fire bird that fans flames and teleports in rebirth.",
    spriteSheet = "sprites/phoenix.png",
    qAbility = AbilityDef(name = "Fire Fan", description = "Fires 5 flame bolts in a fan.", cooldownMs = 8000, maxRange = 16, damage = 18, projectileType = ProjectileType.FLAME_BOLT, keybind = "Q", castBehavior = FanProjectile(5, Math.toRadians(60))),
    eAbility = AbilityDef(name = "Rebirth", description = "Teleports in a burst of flame.", cooldownMs = 10000, maxRange = 8, damage = 0, projectileType = -2, keybind = "E", castBehavior = TeleportCast(8)),
    primaryProjectileType = ProjectileType.FLAME_BOLT, maxHealth = 80
  )

  val HydraChar: CharacterDef = CharacterDef(
    id = CharacterId.Hydra, displayName = "Hydra",
    description = "A multi-headed serpent with venom fans and acid bombs.",
    spriteSheet = "sprites/hydra.png",
    qAbility = AbilityDef(name = "Multi-Head Spit", description = "Spits venom from 5 heads.", cooldownMs = 10000, maxRange = 16, damage = 14, projectileType = ProjectileType.VENOM_BOLT, keybind = "Q", castBehavior = FanProjectile(5, Math.toRadians(60))),
    eAbility = AbilityDef(name = "Acid Bomb", description = "Spits acid that explodes.", cooldownMs = 12000, maxRange = 14, damage = 25, projectileType = ProjectileType.ACID_BOMB, keybind = "E"),
    primaryProjectileType = ProjectileType.VENOM_BOLT, maxHealth = 110
  )

  val MantisChar: CharacterDef = CharacterDef(
    id = CharacterId.Mantis, displayName = "Mantis",
    description = "A swift insect that camouflages and slashes in a flurry.",
    spriteSheet = "sprites/mantis.png",
    qAbility = AbilityDef(name = "Leaf Cloak", description = "Camouflages among foliage, phased and immune.", cooldownMs = 14000, maxRange = 0, damage = 0, projectileType = -1, keybind = "Q", castBehavior = PhaseShiftBuff(2500)),
    eAbility = AbilityDef(name = "Flurry", description = "Slashes 5 times in a fan.", cooldownMs = 8000, maxRange = 4, damage = 25, projectileType = ProjectileType.CLAW_SWIPE, keybind = "E", castBehavior = FanProjectile(5, Math.toRadians(45))),
    primaryProjectileType = ProjectileType.CLAW_SWIPE, maxHealth = 85
  )

  val JellyfishChar: CharacterDef = CharacterDef(
    id = CharacterId.Jellyfish, displayName = "Jellyfish",
    description = "A floating jellyfish with electric bursts and transparent form.",
    spriteSheet = "sprites/jellyfish.png",
    qAbility = AbilityDef(name = "Electric Burst", description = "Fires stinging tentacles in all directions.", cooldownMs = 10000, maxRange = 14, damage = 12, projectileType = ProjectileType.WEB_SHOT, keybind = "Q", castBehavior = FanProjectile(8, 2 * Math.PI)),
    eAbility = AbilityDef(name = "Transparent", description = "Becomes transparent: phased.", cooldownMs = 14000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = PhaseShiftBuff(4000)),
    primaryProjectileType = ProjectileType.WEB_SHOT
  )

  val GorillaChar: CharacterDef = CharacterDef(
    id = CharacterId.Gorilla, displayName = "Gorilla",
    description = "A mighty primate that grabs enemies and slams the ground.",
    spriteSheet = "sprites/gorilla.png",
    qAbility = AbilityDef(name = "Primate Grab", description = "Long-range grab that pulls enemies to you.", cooldownMs = 8000, maxRange = 18, damage = 8, projectileType = ProjectileType.VINE_WHIP, keybind = "Q"),
    eAbility = AbilityDef(name = "Ground Pound", description = "Slams the ground with devastating AoE.", cooldownMs = 12000, maxRange = 12, damage = 25, projectileType = ProjectileType.SEISMIC_SLAM, keybind = "E"),
    primaryProjectileType = ProjectileType.BOULDER, maxHealth = 120
  )

  val ChameleonChar: CharacterDef = CharacterDef(
    id = CharacterId.Chameleon, displayName = "Chameleon",
    description = "A stealthy lizard that camouflages and snares prey with its tongue.",
    spriteSheet = "sprites/chameleon.png",
    qAbility = AbilityDef(name = "Camouflage", description = "Becomes invisible: phased.", cooldownMs = 12000, maxRange = 0, damage = 0, projectileType = -1, keybind = "Q", castBehavior = PhaseShiftBuff(5000)),
    eAbility = AbilityDef(name = "Tongue Lash", description = "Snaps out a long tongue that pulls enemies toward you.", cooldownMs = 10000, maxRange = 18, damage = 8, projectileType = ProjectileType.VINE_WHIP, keybind = "E"),
    primaryProjectileType = ProjectileType.POISON_DART
  )

  // === Batch 6: Mythological (IDs 87-101) ===

  val MinotaurChar: CharacterDef = CharacterDef(
    id = CharacterId.Minotaur, displayName = "Minotaur",
    description = "A bull-headed brute with a devastating long charge and stunning horn toss.",
    spriteSheet = "sprites/minotaur.png",
    qAbility = AbilityDef(name = "Bull Charge", description = "Massive charge forward with horns lowered.", cooldownMs = 12000, maxRange = 0, damage = 0, projectileType = -1, keybind = "Q", castBehavior = DashBuff(14, 500, 25)),
    eAbility = AbilityDef(name = "Horn Toss", description = "Flings a cursed horn that freezes on hit.", cooldownMs = 10000, maxRange = 16, damage = 25, projectileType = ProjectileType.HEAD_THROW, keybind = "E"),
    primaryProjectileType = ProjectileType.AXE, maxHealth = 120
  )

  val MedusaChar: CharacterDef = CharacterDef(
    id = CharacterId.Medusa, displayName = "Medusa",
    description = "A gorgon who petrifies with her gaze and fires soul bolts.",
    spriteSheet = "sprites/medusa.png",
    qAbility = AbilityDef(name = "Petrify", description = "Petrifying gaze that freezes.", cooldownMs = 5000, maxRange = 20, damage = 5, projectileType = ProjectileType.ICE_BEAM, keybind = "Q"),
    eAbility = AbilityDef(name = "Stone Gaze", description = "Petrifying gaze roots all nearby enemies.", cooldownMs = 12000, maxRange = 0, damage = 15, projectileType = ProjectileType.STONE_GAZE, keybind = "E", castBehavior = GroundSlam(3.5f)),
    primaryProjectileType = ProjectileType.SOUL_BOLT
  )

  val CerberusChar: CharacterDef = CharacterDef(
    id = CharacterId.Cerberus, displayName = "Cerberus",
    description = "A three-headed hellhound that breathes fire and pounces.",
    spriteSheet = "sprites/cerberus.png",
    qAbility = AbilityDef(name = "Triple Blast", description = "Fires 3 flame bolts.", cooldownMs = 7000, maxRange = 16, damage = 18, projectileType = ProjectileType.FLAME_BOLT, keybind = "Q", castBehavior = FanProjectile(3, Math.toRadians(30))),
    eAbility = AbilityDef(name = "Pounce", description = "Leaps at prey.", cooldownMs = 10000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = DashBuff(10, 350, 20)),
    primaryProjectileType = ProjectileType.FLAME_BOLT, maxHealth = 110
  )

  val CentaurChar: CharacterDef = CharacterDef(
    id = CharacterId.Centaur, displayName = "Centaur",
    description = "A horse-bodied archer with lance charge and bow.",
    spriteSheet = "sprites/centaur.png",
    qAbility = AbilityDef(name = "Lance", description = "Hurls a distance-scaling spear.", cooldownMs = 8000, maxRange = 20, damage = 10, projectileType = ProjectileType.SPEAR, keybind = "Q"),
    eAbility = AbilityDef(name = "Gallop", description = "Gallops forward.", cooldownMs = 10000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = DashBuff(12, 400, 18)),
    primaryProjectileType = ProjectileType.ARROW, maxHealth = 110
  )

  val KrakenChar: CharacterDef = CharacterDef(
    id = CharacterId.Kraken, displayName = "Kraken",
    description = "A sea monster with tentacle pulls and tidal pushes.",
    spriteSheet = "sprites/kraken.png",
    qAbility = AbilityDef(name = "Tentacle Storm", description = "Fires 5 tentacles in a fan.", cooldownMs = 10000, maxRange = 15, damage = 5, projectileType = ProjectileType.TENTACLE, keybind = "Q", castBehavior = FanProjectile(5, Math.toRadians(60))),
    eAbility = AbilityDef(name = "Ink Snare", description = "Launches ink that roots enemies on landing.", cooldownMs = 12000, maxRange = 14, damage = 10, projectileType = ProjectileType.INK_SNARE, keybind = "E"),
    primaryProjectileType = ProjectileType.TENTACLE, maxHealth = 120
  )

  val SphinxChar: CharacterDef = CharacterDef(
    id = CharacterId.Sphinx, displayName = "Sphinx",
    description = "A riddle-master with mesmerizing charms and piercing sonic riddles.",
    spriteSheet = "sprites/sphinx.png",
    qAbility = AbilityDef(name = "Mesmerize", description = "Charm that freezes for 2s.", cooldownMs = 10000, maxRange = 14, damage = 15, projectileType = ProjectileType.CHARM, keybind = "Q"),
    eAbility = AbilityDef(name = "Riddle Burst", description = "Fires 5 sonic waves that pass through walls.", cooldownMs = 10000, maxRange = 14, damage = 14, projectileType = ProjectileType.SONIC_WAVE, keybind = "E", castBehavior = FanProjectile(5, Math.toRadians(60))),
    primaryProjectileType = ProjectileType.SONIC_WAVE
  )

  val CyclopsChar: CharacterDef = CharacterDef(
    id = CharacterId.Cyclops, displayName = "Cyclops",
    description = "A one-eyed giant that hurls boulders and pounds the ground.",
    spriteSheet = "sprites/cyclops.png",
    qAbility = AbilityDef(name = "Ground Pound", description = "Slams the ground with AoE.", cooldownMs = 10000, maxRange = 12, damage = 25, projectileType = ProjectileType.SEISMIC_SLAM, keybind = "Q"),
    eAbility = AbilityDef(name = "Brute Charge", description = "Charges forward.", cooldownMs = 12000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = DashBuff(8, 400, 28)),
    primaryProjectileType = ProjectileType.BOULDER, maxHealth = 120
  )

  val HarpyChar: CharacterDef = CharacterDef(
    id = CharacterId.Harpy, displayName = "Harpy",
    description = "A flying terror that pushes with wing gusts and takes to the sky.",
    spriteSheet = "sprites/harpy.png",
    qAbility = AbilityDef(name = "Wing Gust", description = "Pushes enemies with wind.", cooldownMs = 8000, maxRange = 6, damage = 10, projectileType = ProjectileType.GUST, keybind = "Q"),
    eAbility = AbilityDef(name = "Sky Dance", description = "Takes flight, becoming phased and immune.", cooldownMs = 14000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = PhaseShiftBuff(3000)),
    primaryProjectileType = ProjectileType.TALON, maxHealth = 85
  )

  val GriffinChar: CharacterDef = CharacterDef(
    id = CharacterId.Griffin, displayName = "Griffin",
    description = "A majestic beast that dives and blasts wind.",
    spriteSheet = "sprites/griffin.png",
    qAbility = AbilityDef(name = "Sky Dive", description = "Dives from above.", cooldownMs = 10000, maxRange = 0, damage = 0, projectileType = -1, keybind = "Q", castBehavior = DashBuff(12, 400, 18)),
    eAbility = AbilityDef(name = "Wind Blast", description = "Gusts in a fan.", cooldownMs = 8000, maxRange = 6, damage = 10, projectileType = ProjectileType.GUST, keybind = "E", castBehavior = FanProjectile(5, Math.toRadians(60))),
    primaryProjectileType = ProjectileType.TALON, maxHealth = 110
  )

  val AnubisChar: CharacterDef = CharacterDef(
    id = CharacterId.Anubis, displayName = "Anubis",
    description = "The jackal god of death with curses and a circle of death bolts.",
    spriteSheet = "sprites/anubis.png",
    qAbility = AbilityDef(name = "Curse of Anubis", description = "Ancient curse that freezes.", cooldownMs = 12000, maxRange = 12, damage = 20, projectileType = ProjectileType.CURSE, keybind = "Q"),
    eAbility = AbilityDef(name = "Death Circle", description = "Fires death bolts in all directions through walls.", cooldownMs = 12000, maxRange = 16, damage = 16, projectileType = ProjectileType.DEATH_BOLT, keybind = "E", castBehavior = FanProjectile(8, 2 * Math.PI)),
    primaryProjectileType = ProjectileType.DEATH_BOLT
  )

  val YokaiChar: CharacterDef = CharacterDef(
    id = CharacterId.Yokai, displayName = "Yokai",
    description = "A trickster spirit that fires soul fans and curses enemies in place.",
    spriteSheet = "sprites/yokai.png",
    qAbility = AbilityDef(name = "Soul Fan", description = "Fires 3 soul bolts in a fan through walls.", cooldownMs = 8000, maxRange = 15, damage = 15, projectileType = ProjectileType.SOUL_BOLT, keybind = "Q", castBehavior = FanProjectile(3, Math.toRadians(30))),
    eAbility = AbilityDef(name = "Yokai Curse", description = "Ancient curse that freezes for 2 seconds.", cooldownMs = 12000, maxRange = 12, damage = 20, projectileType = ProjectileType.CURSE, keybind = "E"),
    primaryProjectileType = ProjectileType.SOUL_BOLT
  )

  val GolemChar: CharacterDef = CharacterDef(
    id = CharacterId.Golem, displayName = "Golem",
    description = "A stone construct that hurls boulder fans and grabs enemies with stone grip.",
    spriteSheet = "sprites/golem.png",
    qAbility = AbilityDef(name = "Boulder Fan", description = "Hurls 5 boulders in a fan.", cooldownMs = 10000, maxRange = 8, damage = 35, projectileType = ProjectileType.BOULDER, keybind = "Q", castBehavior = FanProjectile(5, Math.toRadians(60))),
    eAbility = AbilityDef(name = "Tremor Slam", description = "Slams the ground, rooting nearby enemies.", cooldownMs = 14000, maxRange = 0, damage = 30, projectileType = ProjectileType.TREMOR_SLAM, keybind = "E", castBehavior = GroundSlam(3.5f)),
    primaryProjectileType = ProjectileType.BOULDER, maxHealth = 120
  )

  val DjinnChar: CharacterDef = CharacterDef(
    id = CharacterId.Djinn, displayName = "Djinn",
    description = "A wish-granting spirit who mesmerizes foes and vanishes into mirage.",
    spriteSheet = "sprites/djinn.png",
    qAbility = AbilityDef(name = "Mesmerize", description = "Hypnotic charm that freezes for 2 seconds.", cooldownMs = 10000, maxRange = 14, damage = 15, projectileType = ProjectileType.CHARM, keybind = "Q"),
    eAbility = AbilityDef(name = "Mirage", description = "Dissolves into a shimmering mirage, phased and immune.", cooldownMs = 14000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = PhaseShiftBuff(4000)),
    primaryProjectileType = ProjectileType.FLAME_BOLT
  )

  val FenrirChar: CharacterDef = CharacterDef(
    id = CharacterId.Fenrir, displayName = "Fenrir",
    description = "The great wolf that pounces and bites in a frenzy.",
    spriteSheet = "sprites/fenrir.png",
    qAbility = AbilityDef(name = "Giant Pounce", description = "Massive leap at prey.", cooldownMs = 12000, maxRange = 0, damage = 0, projectileType = -1, keybind = "Q", castBehavior = DashBuff(12, 400, 20)),
    eAbility = AbilityDef(name = "Triple Bite", description = "Bites 3 times in a fan.", cooldownMs = 8000, maxRange = 4, damage = 25, projectileType = ProjectileType.CLAW_SWIPE, keybind = "E", castBehavior = FanProjectile(3, Math.toRadians(30))),
    primaryProjectileType = ProjectileType.CLAW_SWIPE, maxHealth = 110
  )

  val ChimeraChar: CharacterDef = CharacterDef(
    id = CharacterId.Chimera, displayName = "Chimera",
    description = "A multi-headed beast with fire, venom, and lion charge.",
    spriteSheet = "sprites/chimera.png",
    qAbility = AbilityDef(name = "Snake Spit", description = "Venomous spit from snake tail.", cooldownMs = 7000, maxRange = 16, damage = 14, projectileType = ProjectileType.VENOM_BOLT, keybind = "Q"),
    eAbility = AbilityDef(name = "Lion Charge", description = "Charges forward.", cooldownMs = 10000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = DashBuff(10, 350, 22)),
    primaryProjectileType = ProjectileType.FLAME_BOLT, maxHealth = 110
  )

  // === Batch 7: Specialist (IDs 102-111) ===

  val AlchemistChar: CharacterDef = CharacterDef(
    id = CharacterId.Alchemist, displayName = "Alchemist",
    description = "A potion-brewing expert with explosive and corrosive concoctions.",
    spriteSheet = "sprites/alchemist.png",
    qAbility = AbilityDef(name = "Explosive Potion", description = "Hurls an explosive potion.", cooldownMs = 6000, maxRange = 18, damage = 45, projectileType = ProjectileType.FIREBALL, keybind = "Q"),
    eAbility = AbilityDef(name = "Acid Flask", description = "Throws acid that explodes and slows.", cooldownMs = 10000, maxRange = 14, damage = 20, projectileType = ProjectileType.MUD_BOMB, keybind = "E"),
    primaryProjectileType = ProjectileType.PLAGUE_BOLT
  )

  val PuppeteerChar: CharacterDef = CharacterDef(
    id = CharacterId.Puppeteer, displayName = "Puppeteer",
    description = "A string master who controls enemies with chains and pulls.",
    spriteSheet = "sprites/puppeteer.png",
    qAbility = AbilityDef(name = "Puppet String", description = "Pulls an enemy with string.", cooldownMs = 8000, maxRange = 25, damage = 5, projectileType = ProjectileType.ROPE, keybind = "Q"),
    eAbility = AbilityDef(name = "String Web", description = "Fires 3 strings in a fan.", cooldownMs = 10000, maxRange = 14, damage = 12, projectileType = ProjectileType.CHAIN_BOLT, keybind = "E", castBehavior = FanProjectile(3, Math.toRadians(30))),
    primaryProjectileType = ProjectileType.CHAIN_BOLT
  )

  val GamblerChar: CharacterDef = CharacterDef(
    id = CharacterId.Gambler, displayName = "Gambler",
    description = "A risk-taker who flings cards and teleports out of danger.",
    spriteSheet = "sprites/gambler.png",
    qAbility = AbilityDef(name = "Card Fan", description = "Throws 5 cards in a fan.", cooldownMs = 7000, maxRange = 14, damage = 16, projectileType = ProjectileType.CARD, keybind = "Q", castBehavior = FanProjectile(5, Math.toRadians(45))),
    eAbility = AbilityDef(name = "Lucky Escape", description = "Teleports away.", cooldownMs = 12000, maxRange = 6, damage = 0, projectileType = -2, keybind = "E", castBehavior = TeleportCast(6)),
    primaryProjectileType = ProjectileType.CARD, maxHealth = 85
  )

  val BlacksmithChar: CharacterDef = CharacterDef(
    id = CharacterId.Blacksmith, displayName = "Blacksmith",
    description = "A forge master who throws axes in fans and lays traps from the anvil.",
    spriteSheet = "sprites/blacksmith.png",
    qAbility = AbilityDef(name = "Triple Hammer", description = "Hurls 3 hammers in a fan.", cooldownMs = 8000, maxRange = 5, damage = 33, projectileType = ProjectileType.AXE, keybind = "Q", castBehavior = FanProjectile(3, Math.toRadians(40))),
    eAbility = AbilityDef(name = "Anvil Trap", description = "Deploys a snare trap that freezes all nearby.", cooldownMs = 14000, maxRange = 16, damage = 15, projectileType = ProjectileType.SNARE_MINE, keybind = "E"),
    primaryProjectileType = ProjectileType.AXE, maxHealth = 120
  )

  val PirateChar: CharacterDef = CharacterDef(
    id = CharacterId.Pirate, displayName = "Pirate",
    description = "A swashbuckler with pistol, grapple, and cannonball.",
    spriteSheet = "sprites/pirate.png",
    qAbility = AbilityDef(name = "Grapple", description = "Pulls enemy with rope.", cooldownMs = 8000, maxRange = 25, damage = 5, projectileType = ProjectileType.ROPE, keybind = "Q"),
    eAbility = AbilityDef(name = "Cannonball", description = "Fires an explosive cannonball.", cooldownMs = 10000, maxRange = 12, damage = 40, projectileType = ProjectileType.GRENADE, keybind = "E"),
    primaryProjectileType = ProjectileType.BULLET
  )

  val ChefChar: CharacterDef = CharacterDef(
    id = CharacterId.Chef, displayName = "Chef",
    description = "A culinary warrior who throws knives and flambes.",
    spriteSheet = "sprites/chef.png",
    qAbility = AbilityDef(name = "Knife Fan", description = "Throws 5 knives in a fan.", cooldownMs = 7000, maxRange = 6, damage = 22, projectileType = ProjectileType.SHURIKEN, keybind = "Q", castBehavior = FanProjectile(5, Math.toRadians(45))),
    eAbility = AbilityDef(name = "Flambe", description = "Explosive fire attack.", cooldownMs = 8000, maxRange = 18, damage = 45, projectileType = ProjectileType.FIREBALL, keybind = "E"),
    primaryProjectileType = ProjectileType.SHURIKEN
  )

  val MusicianChar: CharacterDef = CharacterDef(
    id = CharacterId.Musician, displayName = "Musician",
    description = "A hypnotic performer who mesmerizes foes and dashes with rhythm.",
    spriteSheet = "sprites/musician.png",
    qAbility = AbilityDef(name = "Hypnotic Melody", description = "Mesmerizing song that freezes for 2 seconds.", cooldownMs = 10000, maxRange = 14, damage = 15, projectileType = ProjectileType.CHARM, keybind = "Q"),
    eAbility = AbilityDef(name = "Rhythmic Dash", description = "Dashes forward on a wave of sound.", cooldownMs = 12000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = DashBuff(8, 300, 15)),
    primaryProjectileType = ProjectileType.SONIC_WAVE
  )

  val AstronomerChar: CharacterDef = CharacterDef(
    id = CharacterId.Astronomer, displayName = "Astronomer",
    description = "A stargazer with cosmic bolts and meteor strikes.",
    spriteSheet = "sprites/astronomer.png",
    qAbility = AbilityDef(name = "Meteor", description = "Summons a devastating meteor.", cooldownMs = 8000, maxRange = 18, damage = 45, projectileType = ProjectileType.FIREBALL, keybind = "Q"),
    eAbility = AbilityDef(name = "Astral Project", description = "Teleports through the stars.", cooldownMs = 12000, maxRange = 8, damage = 0, projectileType = -2, keybind = "E", castBehavior = TeleportCast(8)),
    primaryProjectileType = ProjectileType.ARCANE_BOLT, maxHealth = 80
  )

  val RunesmithChar: CharacterDef = CharacterDef(
    id = CharacterId.Runesmith, displayName = "Runesmith",
    description = "A rune crafter with holy bolts and rune traps.",
    spriteSheet = "sprites/runesmith.png",
    qAbility = AbilityDef(name = "Rune Burst", description = "Fires 5 rune bolts in a fan.", cooldownMs = 8000, maxRange = 16, damage = 20, projectileType = ProjectileType.HOLY_BOLT, keybind = "Q", castBehavior = FanProjectile(5, Math.toRadians(60))),
    eAbility = AbilityDef(name = "Rune Trap", description = "Deploys a rune trap.", cooldownMs = 14000, maxRange = 16, damage = 15, projectileType = ProjectileType.SNARE_MINE, keybind = "E"),
    primaryProjectileType = ProjectileType.HOLY_BOLT
  )

  val ShapeshifterChar: CharacterDef = CharacterDef(
    id = CharacterId.Shapeshifter, displayName = "Shapeshifter",
    description = "A form-changer that charges and shifts between shapes.",
    spriteSheet = "sprites/shapeshifter.png",
    qAbility = AbilityDef(name = "Beast Charge", description = "Charges forward in beast form.", cooldownMs = 10000, maxRange = 0, damage = 0, projectileType = -1, keybind = "Q", castBehavior = DashBuff(10, 350, 18)),
    eAbility = AbilityDef(name = "Shift Form", description = "Shifts form: phased.", cooldownMs = 14000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = PhaseShiftBuff(5000)),
    primaryProjectileType = ProjectileType.CLAW_SWIPE
  )

  private val allDefs: Seq[CharacterDef] = Seq(
    Spaceman, Gladiator, Wraith, Wizard, Tidecaller, Soldier, Raptor, Assassin, Warden, Samurai, PlagueDoctor, Vampire,
    // Batch 1: Elemental
    Pyromancer, Cryomancer, Stormcaller, Earthshaker, Windwalker, MagmaKnight, Frostbite, Sandstorm, Thornweaver, Cloudrunner, Inferno, Glacier, Mudslinger, EmberChar, AvalancheChar,
    // Batch 2: Undead/Dark
    Necromancer, SkeletonKing, Banshee, Lich, Ghoul, ReaperChar, Shade, Revenant, Gravedigger, Dullahan, Phantom, MummyChar, Deathknight, Shadowfiend, Poltergeist,
    // Batch 3: Medieval/Fantasy
    PaladinChar, RangerChar, BerserkerChar, CrusaderChar, DruidChar, BardChar, MonkChar, ClericChar, RogueChar, BarbarianChar, EnchanterChar, JesterChar, ValkyrieChar, WarlockChar, InquisitorChar,
    // Batch 4: Sci-Fi/Tech
    CyborgChar, HackerChar, MechPilotChar, AndroidChar, ChronomancerChar, GravitonChar, TeslaChar, NanoswarmChar, VoidwalkerChar, PhotonChar, RailgunnerChar, BombardierChar, SentinelChar, PilotChar, GlitcherChar,
    // Batch 5: Nature/Beast
    WolfChar, SerpentChar, SpiderChar, BearChar, ScorpionChar, HawkChar, SharkChar, BeetleChar, TreantChar, PhoenixChar, HydraChar, MantisChar, JellyfishChar, GorillaChar, ChameleonChar,
    // Batch 6: Mythological
    MinotaurChar, MedusaChar, CerberusChar, CentaurChar, KrakenChar, SphinxChar, CyclopsChar, HarpyChar, GriffinChar, AnubisChar, YokaiChar, GolemChar, DjinnChar, FenrirChar, ChimeraChar,
    // Batch 7: Specialist
    AlchemistChar, PuppeteerChar, GamblerChar, BlacksmithChar, PirateChar, ChefChar, MusicianChar, AstronomerChar, RunesmithChar, ShapeshifterChar
  )

  private val byId: Map[Byte, CharacterDef] = allDefs.map(d => d.id.id -> d).toMap

  val all: Seq[CharacterDef] = allDefs

  def get(id: CharacterId): CharacterDef = byId.getOrElse(id.id, Spaceman)

  def get(id: Byte): CharacterDef = byId.getOrElse(id, Spaceman)
}
