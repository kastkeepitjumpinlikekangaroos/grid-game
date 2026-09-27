package com.gridgame.common.model.roster

import com.gridgame.common.model._
import com.gridgame.common.model.CombatRole.{Melee, Ranged, Skirmisher}

/**
 * The first twelve characters (character ids 0-11).
 *
 * First the definitions of the projectiles a character here is the first in the roster to throw,
 * then the characters. CharacterDef's initializer registers every definition (Roster); how a
 * projectile type looks is GLProjectileRenderers'.
 */
private[model] object Originals {
  // ── Projectiles ──

  val TentacleDef: ProjectileDef = ProjectileDef(
    id = ProjectileType.TENTACLE, name = "Tentacle",
    speedMultiplier = 0.9f, damage = 5, maxRange = 15,
    onHitEffect = Some(PullToOwner)
  )
  val IceBeamDef: ProjectileDef = ProjectileDef(
    id = ProjectileType.ICE_BEAM, name = "Ice Beam",
    speedMultiplier = 0.3f, damage = 5, maxRange = 20,
    onHitEffect = Some(Freeze(3000))
  )
  val AxeDef: ProjectileDef = ProjectileDef(
    id = ProjectileType.AXE, name = "Axe",
    speedMultiplier = 0.6f, damage = 33, maxRange = 5,
    hitRadius = 2.5f, boomerang = true
  )
  val RopeDef: ProjectileDef = ProjectileDef(
    id = ProjectileType.ROPE, name = "Rope",
    speedMultiplier = 0.85f, damage = 5, maxRange = 25,
    onHitEffect = Some(PullToOwner)
  )
  val SoulBoltDef: ProjectileDef = ProjectileDef(
    id = ProjectileType.SOUL_BOLT, name = "Soul Bolt",
    speedMultiplier = 0.65f, damage = 15, maxRange = 15,
    passesThroughWalls = true, pierceCount = 1
  )
  val HauntDef: ProjectileDef = ProjectileDef(
    id = ProjectileType.HAUNT, name = "Haunt",
    speedMultiplier = 0.5f, damage = 30, maxRange = 15,
    passesThroughWalls = true,
    onHitEffect = Some(TeleportOwnerBehind(2, 2000))
  )
  val ArcaneBoltDef: ProjectileDef = ProjectileDef(
    id = ProjectileType.ARCANE_BOLT, name = "Arcane Bolt",
    speedMultiplier = 0.85f, damage = 18, maxRange = 25,
    passesThroughWalls = true,
    chargeSpeedScaling = Some(ChargeScaling(0.6f, 1.2f)),
    chargeDamageScaling = Some(ChargeScaling(8f, 40f)),
    chargeRangeScaling = Some(ChargeScaling(15f, 30f))
  )
  val FireballDef: ProjectileDef = ProjectileDef(
    id = ProjectileType.FIREBALL, name = "Fireball",
    speedMultiplier = 0.4f, damage = 45, maxRange = 18
  )
  val SplashDef: ProjectileDef = ProjectileDef(
    id = ProjectileType.SPLASH, name = "Splash",
    speedMultiplier = 0.65f, damage = 20, maxRange = 15,
    chargeSpeedScaling = Some(ChargeScaling(0.5f, 1.0f)),
    chargeDamageScaling = Some(ChargeScaling(10f, 35f)),
    chargeRangeScaling = Some(ChargeScaling(10f, 20f)),
    aoeOnHit = Some(AoESplashConfig(3.0f, 10))
  )
  val TidalWaveDef: ProjectileDef = ProjectileDef(
    id = ProjectileType.TIDAL_WAVE, name = "Tidal Wave",
    speedMultiplier = 0.5f, damage = 10, maxRange = 12,
    onHitEffect = Some(Push(3.0f))
  )
  val GeyserDef: ProjectileDef = ProjectileDef(
    id = ProjectileType.GEYSER, name = "Geyser",
    speedMultiplier = 0.6f, damage = 25, maxRange = 18,
    hitRadius = 3.5f,
    aoeOnHit = Some(AoESplashConfig(4.0f, 25)),
    aoeOnMaxRange = Some(AoESplashConfig(4.0f, 25))
  )
  val BulletDef: ProjectileDef = ProjectileDef(
    id = ProjectileType.BULLET, name = "Bullet",
    speedMultiplier = 1.0f, damage = 20, maxRange = 18
  )
  val GrenadeDef: ProjectileDef = ProjectileDef(
    id = ProjectileType.GRENADE, name = "Grenade",
    speedMultiplier = 0.5f, damage = 40, maxRange = 12,
    passesThroughPlayers = true,
    explosionConfig = Some(ExplosionConfig(40, 10, 3.0f))
  )
  val RocketDef: ProjectileDef = ProjectileDef(
    id = ProjectileType.ROCKET, name = "Rocket",
    speedMultiplier = 0.55f, damage = 35, maxRange = 20,
    explodesOnPlayerHit = true,
    explosionConfig = Some(ExplosionConfig(35, 10, 2.5f))
  )
  val TalonDef: ProjectileDef = ProjectileDef(
    id = ProjectileType.TALON, name = "Talon",
    speedMultiplier = 0.7f, damage = 28, maxRange = 4,
    hitRadius = 2.2f
  )
  val GustDef: ProjectileDef = ProjectileDef(
    id = ProjectileType.GUST, name = "Gust",
    speedMultiplier = 0.8f, damage = 10, maxRange = 6,
    onHitEffect = Some(Push(5.0f))
  )
  val ShurikenDef: ProjectileDef = ProjectileDef(
    id = ProjectileType.SHURIKEN, name = "Shuriken",
    speedMultiplier = 0.95f, damage = 24, maxRange = 6,
    hitRadius = 2.2f
  )
  val ChainBoltDef: ProjectileDef = ProjectileDef(
    id = ProjectileType.CHAIN_BOLT, name = "Chain Bolt",
    speedMultiplier = 0.7f, damage = 10, maxRange = 14,
    onHitEffect = Some(Freeze(200))
  )
  val LockdownChainDef: ProjectileDef = ProjectileDef(
    id = ProjectileType.LOCKDOWN_CHAIN, name = "Lockdown Chain",
    speedMultiplier = 0.6f, damage = 8, maxRange = 12,
    onHitEffect = Some(Freeze(700))
  )
  val KatanaDef: ProjectileDef = ProjectileDef(
    id = ProjectileType.KATANA, name = "Katana",
    speedMultiplier = 0.7f, damage = 30, maxRange = 4,
    hitRadius = 2.2f
  )
  val SwordWaveDef: ProjectileDef = ProjectileDef(
    id = ProjectileType.SWORD_WAVE, name = "Sword Wave",
    speedMultiplier = 0.6f, damage = 20, maxRange = 5,
    hitRadius = 1.8f,
    onHitEffect = Some(Stun(500))
  )
  val PlagueBoltDef: ProjectileDef = ProjectileDef(
    id = ProjectileType.PLAGUE_BOLT, name = "Plague Bolt",
    speedMultiplier = 0.55f, damage = 14, maxRange = 16,
    aoeOnHit = Some(AoESplashConfig(2.5f, 8))
  )
  val MiasmaDef: ProjectileDef = ProjectileDef(
    id = ProjectileType.MIASMA, name = "Miasma",
    speedMultiplier = 0.45f, damage = 12, maxRange = 14,
    hitRadius = 2.8f,
    explodesOnPlayerHit = true,
    explosionConfig = Some(ExplosionConfig(25, 8, 5.0f))
  )
  val BlightBombDef: ProjectileDef = ProjectileDef(
    id = ProjectileType.BLIGHT_BOMB, name = "Blight Bomb",
    speedMultiplier = 0.5f, damage = 25, maxRange = 15,
    explodesOnPlayerHit = true,
    explosionConfig = Some(ExplosionConfig(30, 12, 4.0f))
  )
  val BloodFangDef: ProjectileDef = ProjectileDef(
    id = ProjectileType.BLOOD_FANG, name = "Blood Fang",
    speedMultiplier = 0.7f, damage = 25, maxRange = 3,
    hitRadius = 2.2f,
    onHitEffect = Some(LifeSteal(50))
  )
  val BatSwarmDef: ProjectileDef = ProjectileDef(
    id = ProjectileType.BAT_SWARM, name = "Bat Swarm",
    speedMultiplier = 0.5f, damage = 15, maxRange = 7,
    // The brief freeze used to be a special case in GameInstance, where nothing that reads defs
    // (the character panel, the roster tests) could see it
    onHitEffect = Some(LifeSteal(60)), alsoOnHit = Seq(Freeze(600))
  )

  val projectiles: Seq[ProjectileDef] = Seq(
    TentacleDef, IceBeamDef, AxeDef, RopeDef, SoulBoltDef,
    HauntDef, ArcaneBoltDef, FireballDef, SplashDef, TidalWaveDef,
    GeyserDef, BulletDef, GrenadeDef, RocketDef, TalonDef,
    GustDef, ShurikenDef, ChainBoltDef, LockdownChainDef, KatanaDef,
    SwordWaveDef, PlagueBoltDef, MiasmaDef, BlightBombDef, BloodFangDef,
    BatSwarmDef
  )

  /** Plan 5a's variants: another type's numbers and look, with an effect of their own. Made from
    * the registered base, so Roster makes them once every base is registered. */
  def variants(base: Byte => ProjectileDef): Seq[ProjectileDef] = Seq(
    base(ProjectileType.POISON_DART).copy(id = ProjectileType.VENOM_DART, name = "Venom Dart", alsoOnHit = Seq(Poison(15, 3000, 750)))
  )

  // ── Characters ──

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
    primaryProjectileType = ProjectileType.NORMAL,
    role = Ranged, maxHealth = 70
  )

  val Gladiator: CharacterDef = CharacterDef(
    id = CharacterId.Gladiator,
    displayName = "Gladiator",
    description = "A Roman warrior who locks a tower shield, ropes enemies in and cuts them down with an axe.",
    spriteSheet = "sprites/gladiator.png",
    qAbility = AbilityDef(
      name = "Scutum",
      description = "Locks a tower shield in front of you that stops enemy shots until you attack.",
      cooldownMs = 12000, maxRange = 0, damage = 0,
      projectileType = -3, keybind = "Q",
      castBehavior = BarrierCast(3000)
    ),
    eAbility = AbilityDef(
      name = "Rope",
      description = "Throws a rope that pulls enemies to you.",
      cooldownMs = 20000, maxRange = 25, damage = 5,
      projectileType = ProjectileType.ROPE, keybind = "E"
    ),
    primaryProjectileType = ProjectileType.AXE,
    role = Melee, maxHealth = 130, moveSpeed = Melee.speed
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
    primaryProjectileType = ProjectileType.SOUL_BOLT,
    role = Ranged, maxHealth = 70
  )

  val Wizard: CharacterDef = CharacterDef(
    id = CharacterId.Wizard,
    displayName = "Wizard",
    description = "A fragile mage with wall-piercing bolts, devastating fireballs, and blink escape.",
    spriteSheet = "sprites/wizard.png",
    qAbility = AbilityDef(
      name = "Fireball",
      description = "Launches a slow but devastating fireball that deals massive damage.",
      cooldownMs = 5000, maxRange = 18, damage = 45,
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
    role = Ranged, maxHealth = 60
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
    primaryProjectileType = ProjectileType.SPLASH,
    role = Ranged, maxHealth = 70
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
    primaryProjectileType = ProjectileType.BULLET,
    role = Ranged, maxHealth = 70
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
      castBehavior = DashBuff(12, 150, 20)
    ),
    eAbility = AbilityDef(
      name = "Gust",
      description = "Wind blast that pushes enemies away from you.",
      cooldownMs = 8000, maxRange = 6, damage = 10,
      projectileType = ProjectileType.GUST, keybind = "E"
    ),
    primaryProjectileType = ProjectileType.TALON,
    role = Melee, maxHealth = 130, moveSpeed = Melee.speed
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
      projectileType = ProjectileType.VENOM_DART, keybind = "Q"
    ),
    eAbility = AbilityDef(
      name = "Smoke Bomb",
      description = "Vanishes in a cloud of smoke, phased and immune.",
      cooldownMs = 12000, maxRange = 0, damage = 0,
      projectileType = -1, keybind = "E",
      castBehavior = PhaseShiftBuff(3000)
    ),
    primaryProjectileType = ProjectileType.SHURIKEN,
    role = Melee, maxHealth = 120, moveSpeed = Melee.speed
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
      description = "Throws a bear trap that arms in a moment and stuns the first enemy over it for 2s.",
      cooldownMs = 12000, maxRange = 6, damage = 10,
      projectileType = -4, keybind = "E",
      castBehavior = TrapCast(TrapType.BEAR_TRAP, 6)
    ),
    primaryProjectileType = ProjectileType.CHAIN_BOLT,
    role = Ranged, maxHealth = 75
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
      castBehavior = DashBuff(10, 150, 20)
    ),
    eAbility = AbilityDef(
      name = "Whirlwind",
      description = "Unleash 8 sword waves in a full circle around you that stun for 0.5s.",
      cooldownMs = 12000, maxRange = 5, damage = 20,
      projectileType = ProjectileType.SWORD_WAVE, keybind = "E",
      castBehavior = FanProjectile(8, 2 * Math.PI)
    ),
    primaryProjectileType = ProjectileType.KATANA,
    role = Melee, maxHealth = 135, moveSpeed = Melee.speed
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
    primaryProjectileType = ProjectileType.PLAGUE_BOLT,
    role = Ranged, maxHealth = 70
  )

  val Vampire: CharacterDef = CharacterDef(
    id = CharacterId.Vampire,
    displayName = "Vampire",
    description = "A gothic life-stealer who drifts in as mist and heals from every bite.",
    spriteSheet = "sprites/vampire.png",
    qAbility = AbilityDef(
      name = "Mist Form",
      description = "Dissolves into mist: phased and immune, moving at twice the pace, but unable to bite.",
      cooldownMs = 12000, maxRange = 0, damage = 0,
      projectileType = -1, keybind = "Q",
      castBehavior = PhaseShiftBuff(2000)
    ),
    eAbility = AbilityDef(
      name = "Bat Swarm",
      description = "Releases 8 draining bats in all directions that heal you and briefly freeze enemies.",
      cooldownMs = 14000, maxRange = 7, damage = 15,
      projectileType = ProjectileType.BAT_SWARM, keybind = "E",
      castBehavior = FanProjectile(8, 2 * Math.PI)
    ),
    primaryProjectileType = ProjectileType.BLOOD_FANG,
    role = Melee, maxHealth = 120, moveSpeed = Melee.speed
  )

  val characters: Seq[CharacterDef] = Seq(
    Spaceman, Gladiator, Wraith, Wizard, Tidecaller, Soldier,
    Raptor, Assassin, Warden, Samurai, PlagueDoctor, Vampire
  )
}
