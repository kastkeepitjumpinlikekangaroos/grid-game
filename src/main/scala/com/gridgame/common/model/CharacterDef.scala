package com.gridgame.common.model

// Cast behavior sealed trait — describes HOW an ability is cast
sealed trait CastBehavior
case object StandardProjectile extends CastBehavior
case class PhaseShiftBuff(durationMs: Int) extends CastBehavior
case class DashBuff(maxDistance: Int, durationMs: Int, moveRateMs: Int) extends CastBehavior
case class TeleportCast(maxDistance: Int) extends CastBehavior
case class FanProjectile(count: Int, fanAngle: Double) extends CastBehavior

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
    hitRadius = 4.0f,
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
    hitRadius = 2.0f
  )

  private val GustDef = ProjectileDef(
    id = ProjectileType.GUST, name = "Gust",
    speedMultiplier = 0.8f, damage = 10, maxRange = 6,
    onHitEffect = Some(Push(5.0f))
  )

  private val ShurikenDef = ProjectileDef(
    id = ProjectileType.SHURIKEN, name = "Shuriken",
    speedMultiplier = 0.95f, damage = 22, maxRange = 6,
    hitRadius = 2.0f
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
    hitRadius = 2.0f
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
    hitRadius = 3.0f,
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
    hitRadius = 2.0f,
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
    BloodFangDef, BloodSiphonDef, BatSwarmDef
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
    description = "A deadly shadow operative with shurikens, poison darts, and a swift shadow dash.",
    spriteSheet = "sprites/assassin.png",
    qAbility = AbilityDef(
      name = "Poison Dart",
      description = "Fires a venomous dart that poisons and slows the target.",
      cooldownMs = 7000, maxRange = 16, damage = 15,
      projectileType = ProjectileType.POISON_DART, keybind = "Q"
    ),
    eAbility = AbilityDef(
      name = "Shadow Step",
      description = "Dash through shadows, phased and invulnerable during the leap.",
      cooldownMs = 9000, maxRange = 0, damage = 0,
      projectileType = -1, keybind = "E",
      castBehavior = DashBuff(8, 300, 15)
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

  private val byId: Map[Byte, CharacterDef] = Map(
    CharacterId.Spaceman.id -> Spaceman,
    CharacterId.Gladiator.id -> Gladiator,
    CharacterId.Wraith.id -> Wraith,
    CharacterId.Wizard.id -> Wizard,
    CharacterId.Tidecaller.id -> Tidecaller,
    CharacterId.Soldier.id -> Soldier,
    CharacterId.Raptor.id -> Raptor,
    CharacterId.Assassin.id -> Assassin,
    CharacterId.Warden.id -> Warden,
    CharacterId.Samurai.id -> Samurai,
    CharacterId.PlagueDoctor.id -> PlagueDoctor,
    CharacterId.Vampire.id -> Vampire
  )

  val all: Seq[CharacterDef] = Seq(Spaceman, Gladiator, Wraith, Wizard, Tidecaller, Soldier, Raptor, Assassin, Warden, Samurai, PlagueDoctor, Vampire)

  def get(id: CharacterId): CharacterDef = byId.getOrElse(id.id, Spaceman)

  def get(id: Byte): CharacterDef = byId.getOrElse(id, Spaceman)
}
