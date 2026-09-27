package com.gridgame.common.model.roster

import com.gridgame.common.model._
import com.gridgame.common.model.CombatRole.{Melee, Ranged, Skirmisher}

/**
 * Science fiction: machines, energy and time (character ids 57-71).
 *
 * First the definitions of the projectiles a character here is the first in the roster to throw,
 * then the characters. CharacterDef's initializer registers every definition (Roster); how a
 * projectile type looks is GLProjectileRenderers'.
 */
private[model] object SciFi {
  // ── Projectiles ──

  val SeismicSlamDef: ProjectileDef = ProjectileDef(id = ProjectileType.SEISMIC_SLAM, name = "Seismic Slam", speedMultiplier = 0.5f, damage = 25, maxRange = 12, hitRadius = 2.8f, aoeOnHit = Some(AoESplashConfig(3.5f, 15)), aoeOnMaxRange = Some(AoESplashConfig(3.5f, 15)))
  val DataBoltDef: ProjectileDef = ProjectileDef(id = ProjectileType.DATA_BOLT, name = "Data Bolt", speedMultiplier = 0.85f, damage = 20, maxRange = 16, ricochetCount = 2)
  val VirusDef: ProjectileDef = ProjectileDef(id = ProjectileType.VIRUS, name = "Virus", speedMultiplier = 0.6f, damage = 10, maxRange = 14, onHitEffect = Some(Slow(3000, 0.4f)))
  val LaserDef: ProjectileDef = ProjectileDef(id = ProjectileType.LASER, name = "Laser", speedMultiplier = 0.95f, damage = 16, maxRange = 20, ricochetCount = 1)
  val GravityBallDef: ProjectileDef = ProjectileDef(id = ProjectileType.GRAVITY_BALL, name = "Gravity Ball", speedMultiplier = 0.6f, damage = 18, maxRange = 14, onHitEffect = Some(PullToOwner))
  val GravityWellDef: ProjectileDef = ProjectileDef(id = ProjectileType.GRAVITY_WELL, name = "Gravity Well", speedMultiplier = 0.4f, damage = 25, maxRange = 12, aoeOnHit = Some(AoESplashConfig(3.5f, 15, freezeDurationMs = 800)), onHitEffect = Some(VortexPull(4.0f, 3.0f)))
  val NanoBoltDef: ProjectileDef = ProjectileDef(id = ProjectileType.NANO_BOLT, name = "Nano Bolt", speedMultiplier = 0.8f, damage = 12, maxRange = 16, aoeOnHit = Some(AoESplashConfig(2.0f, 6)))
  val VoidBoltDef: ProjectileDef = ProjectileDef(id = ProjectileType.VOID_BOLT, name = "Void Bolt", speedMultiplier = 0.7f, damage = 22, maxRange = 18, passesThroughWalls = true)
  val RailgunDef: ProjectileDef = ProjectileDef(id = ProjectileType.RAILGUN, name = "Railgun", speedMultiplier = 1.0f, damage = 30, maxRange = 25, passesThroughWalls = true, pierceCount = 3)
  val ClusterBombDef: ProjectileDef = ProjectileDef(id = ProjectileType.CLUSTER_BOMB, name = "Cluster Bomb", speedMultiplier = 0.4f, damage = 35, maxRange = 14, passesThroughPlayers = true, explodesOnPlayerHit = true, explosionConfig = Some(ExplosionConfig(35, 12, 3.5f)))
  val ChainLightningForkDef: ProjectileDef = ProjectileDef(id = ProjectileType.CHAIN_LIGHTNING_FORK, name = "Chain Lightning Fork", speedMultiplier = 0.7f, damage = 22, maxRange = 16, ricochetCount = 3, pierceCount = 1, aoeOnHit = Some(AoESplashConfig(2.5f, 10)))
  val SniperBeamDef: ProjectileDef = ProjectileDef(id = ProjectileType.SNIPER_BEAM, name = "Sniper Beam", speedMultiplier = 1.0f, damage = 10, maxRange = 28, pierceCount = 2, distanceDamageScaling = Some(DistanceDamageScaling(5, 80, 28)))
  val GravityLanceDef: ProjectileDef = ProjectileDef(id = ProjectileType.GRAVITY_LANCE, name = "Gravity Lance", speedMultiplier = 0.7f, damage = 15, maxRange = 20, distanceDamageScaling = Some(DistanceDamageScaling(10, 50, 20)), onHitEffect = Some(VortexPull(3.5f, 2.5f)))
  val OverclockBeamDef: ProjectileDef = ProjectileDef(id = ProjectileType.OVERCLOCK_BEAM, name = "Overclock Beam", speedMultiplier = 0f, damage = 0, maxRange = 0, aoeOnMaxRange = Some(AoESplashConfig(5.0f, 0)), onHitEffect = Some(SpeedBoost(4000)), explosionConfig = Some(ExplosionConfig(0, 0, 5.0f)))
  val NapalmStrikeDef: ProjectileDef = ProjectileDef(id = ProjectileType.NAPALM_STRIKE, name = "Napalm Strike", speedMultiplier = 0.45f, damage = 30, maxRange = 16, explodesOnPlayerHit = true, explosionConfig = Some(ExplosionConfig(30, 10, 3.0f)), onHitEffect = Some(Burn(15, 4000, 800)))
  val BulletHeavyDef: ProjectileDef = ProjectileDef(id = ProjectileType.BULLET_HEAVY, name = "Bullet Heavy", speedMultiplier = 1.0f, damage = 24, maxRange = 18)
  val BulletLightDef: ProjectileDef = ProjectileDef(id = ProjectileType.BULLET_LIGHT, name = "Bullet Light", speedMultiplier = 1.0f, damage = 12, maxRange = 18)
  val LaserHeavyDef: ProjectileDef = ProjectileDef(id = ProjectileType.LASER_HEAVY, name = "Laser Heavy", speedMultiplier = 0.95f, damage = 22, maxRange = 20, ricochetCount = 1)
  val LaserLightDef: ProjectileDef = ProjectileDef(id = ProjectileType.LASER_LIGHT, name = "Laser Light", speedMultiplier = 0.95f, damage = 11, maxRange = 20, ricochetCount = 1)

  val projectiles: Seq[ProjectileDef] = Seq(
    SeismicSlamDef, DataBoltDef, VirusDef, LaserDef, GravityBallDef,
    GravityWellDef, NanoBoltDef, VoidBoltDef, RailgunDef, ClusterBombDef,
    ChainLightningForkDef, SniperBeamDef, GravityLanceDef, OverclockBeamDef, NapalmStrikeDef,
    BulletHeavyDef, BulletLightDef, LaserHeavyDef, LaserLightDef
  )

  // ── Characters ──

  val Cyborg: CharacterDef = CharacterDef(
    id = CharacterId.Cyborg, displayName = "Cyborg",
    description = "A heavy weapons cyborg with rockets and an overclock speed boost.",
    spriteSheet = "sprites/cyborg.png",
    qAbility = AbilityDef(name = "Arm Rocket", description = "Fires a rocket from the arm.", cooldownMs = 12000, maxRange = 20, damage = 35, projectileType = ProjectileType.ROCKET, keybind = "Q"),
    eAbility = AbilityDef(name = "Overclock", description = "Overclocks systems, gaining massive speed boost.", cooldownMs = 14000, maxRange = 0, damage = 0, projectileType = ProjectileType.OVERCLOCK_BEAM, keybind = "E", castBehavior = GroundSlam(5.0f)),
    primaryProjectileType = ProjectileType.BULLET_LIGHT, role = Ranged, maxHealth = 75
  )

  val Hacker: CharacterDef = CharacterDef(
    id = CharacterId.Hacker, displayName = "Hacker",
    description = "A digital infiltrator with data bolts and system hops.",
    spriteSheet = "sprites/hacker.png",
    qAbility = AbilityDef(name = "Virus", description = "Digital virus that slows systems.", cooldownMs = 8000, maxRange = 14, damage = 10, projectileType = ProjectileType.VIRUS, keybind = "Q"),
    eAbility = AbilityDef(name = "System Hop", description = "Teleports through the network.", cooldownMs = 12000, maxRange = 8, damage = 0, projectileType = -2, keybind = "E", castBehavior = TeleportCast(8)),
    primaryProjectileType = ProjectileType.DATA_BOLT, role = Ranged, maxHealth = 65
  )

  val MechPilot: CharacterDef = CharacterDef(
    id = CharacterId.MechPilot, displayName = "Mech Pilot",
    description = "A heavy mech with minigun, suppression chains, and ground slam.",
    spriteSheet = "sprites/mechpilot.png",
    qAbility = AbilityDef(name = "Suppress Fire", description = "Fires 3 lockdown chains in a fan.", cooldownMs = 8000, maxRange = 12, damage = 8, projectileType = ProjectileType.LOCKDOWN_CHAIN, keybind = "Q", castBehavior = FanProjectile(3, Math.toRadians(40))),
    eAbility = AbilityDef(name = "Mech Slam", description = "Slams mech fist into the ground with devastating AoE.", cooldownMs = 12000, maxRange = 12, damage = 25, projectileType = ProjectileType.SEISMIC_SLAM, keybind = "E"),
    primaryProjectileType = ProjectileType.BULLET_LIGHT, role = Ranged, maxHealth = 80
  )

  val Android: CharacterDef = CharacterDef(
    id = CharacterId.Android, displayName = "Android",
    description = "A precision android with laser beams and jet dash.",
    spriteSheet = "sprites/android.png",
    qAbility = AbilityDef(name = "Laser Fan", description = "Fires 3 lasers in a tight fan.", cooldownMs = 7000, maxRange = 20, damage = 16, projectileType = ProjectileType.LASER, keybind = "Q", castBehavior = FanProjectile(3, Math.toRadians(20))),
    eAbility = AbilityDef(name = "Jet Dash", description = "Quick jet-powered dash.", cooldownMs = 12000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = DashBuff(8, 300, 15)),
    primaryProjectileType = ProjectileType.LASER, role = Ranged, maxHealth = 70
  )

  val Chronomancer: CharacterDef = CharacterDef(
    id = CharacterId.Chronomancer, displayName = "Chronomancer",
    description = "A time mage with lightning-fast attacks, gravity wells, and time stops.",
    spriteSheet = "sprites/chronomancer.png",
    qAbility = AbilityDef(name = "Temporal Rift", description = "Opens a gravity well that traps enemies.", cooldownMs = 14000, maxRange = 12, damage = 25, projectileType = ProjectileType.GRAVITY_WELL, keybind = "Q"),
    eAbility = AbilityDef(name = "Time Stop", description = "Freezes time around yourself, becoming phased and immune.", cooldownMs = 14000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = PhaseShiftBuff(3000)),
    primaryProjectileType = ProjectileType.LIGHTNING, role = Ranged, maxHealth = 70
  )

  val Graviton: CharacterDef = CharacterDef(
    id = CharacterId.Graviton, displayName = "Graviton",
    description = "A gravity manipulator who pulls with fans and creates vortex traps.",
    spriteSheet = "sprites/graviton.png",
    qAbility = AbilityDef(name = "Gravity Fan", description = "Fires 5 gravity balls in a fan.", cooldownMs = 10000, maxRange = 14, damage = 18, projectileType = ProjectileType.GRAVITY_BALL, keybind = "Q", castBehavior = FanProjectile(5, Math.toRadians(60))),
    eAbility = AbilityDef(name = "Vortex Bomb", description = "Creates a vortex that pulls enemies in and freezes.", cooldownMs = 14000, maxRange = 14, damage = 20, projectileType = ProjectileType.VORTEX_BOMB, keybind = "E"),
    primaryProjectileType = ProjectileType.GRAVITY_BALL, role = Ranged, maxHealth = 70
  )

  val Tesla: CharacterDef = CharacterDef(
    id = CharacterId.Tesla, displayName = "Tesla",
    description = "An electric genius with chain lightning and a forking lightning upgrade.",
    spriteSheet = "sprites/tesla.png",
    qAbility = AbilityDef(name = "Chain Lightning", description = "Electric bolt that chains to nearby enemies.", cooldownMs = 8000, maxRange = 14, damage = 20, projectileType = ProjectileType.CHAIN_LIGHTNING, keybind = "Q"),
    eAbility = AbilityDef(name = "Lightning Fork", description = "Ricocheting lightning bolt that splashes AoE on each bounce.", cooldownMs = 12000, maxRange = 16, damage = 22, projectileType = ProjectileType.CHAIN_LIGHTNING_FORK, keybind = "E"),
    primaryProjectileType = ProjectileType.LIGHTNING, role = Ranged, maxHealth = 70
  )

  val Nanoswarm: CharacterDef = CharacterDef(
    id = CharacterId.Nanoswarm, displayName = "Nanoswarm",
    description = "A nanite cloud that fans nano bolts and explodes in a nano burst.",
    spriteSheet = "sprites/nanoswarm.png",
    qAbility = AbilityDef(name = "Nano Fan", description = "Fires 5 nano bolts in a fan.", cooldownMs = 8000, maxRange = 16, damage = 12, projectileType = ProjectileType.NANO_BOLT, keybind = "Q", castBehavior = FanProjectile(5, Math.toRadians(45))),
    eAbility = AbilityDef(name = "Nano Explosion", description = "Detonates nanites in a burst, damaging all nearby.", cooldownMs = 12000, maxRange = 0, damage = 25, projectileType = ProjectileType.TREMOR_SLAM, keybind = "E", castBehavior = GroundSlam(5.0f)),
    primaryProjectileType = ProjectileType.NANO_BOLT, role = Ranged, maxHealth = 70
  )

  val Voidwalker: CharacterDef = CharacterDef(
    id = CharacterId.Voidwalker, displayName = "Voidwalker",
    description = "A void entity with a distance-scaling gravity lance and void teleport.",
    spriteSheet = "sprites/voidwalker.png",
    qAbility = AbilityDef(name = "Gravity Lance", description = "Fires a lance that pulls enemies and scales with distance.", cooldownMs = 10000, maxRange = 20, damage = 15, projectileType = ProjectileType.GRAVITY_LANCE, keybind = "Q"),
    eAbility = AbilityDef(name = "Void Step", description = "Teleport through the void.", cooldownMs = 12000, maxRange = 10, damage = 0, projectileType = -2, keybind = "E", castBehavior = TeleportCast(10)),
    primaryProjectileType = ProjectileType.VOID_BOLT, role = Ranged, maxHealth = 65
  )

  val Photon: CharacterDef = CharacterDef(
    id = CharacterId.Photon, displayName = "Photon",
    description = "A being of pure light with laser fans and prismatic explosions.",
    spriteSheet = "sprites/photon.png",
    qAbility = AbilityDef(name = "Light Spray", description = "Fires 5 lasers in a fan.", cooldownMs = 6000, maxRange = 20, damage = 16, projectileType = ProjectileType.LASER, keybind = "Q", castBehavior = FanProjectile(5, Math.toRadians(60))),
    eAbility = AbilityDef(name = "Prismatic Burst", description = "Fires lasers in all directions.", cooldownMs = 10000, maxRange = 20, damage = 16, projectileType = ProjectileType.LASER, keybind = "E", castBehavior = FanProjectile(8, 2 * Math.PI)),
    primaryProjectileType = ProjectileType.LASER_HEAVY, role = Ranged, maxHealth = 65
  )

  val Railgunner: CharacterDef = CharacterDef(
    id = CharacterId.Railgunner, displayName = "Railgunner",
    description = "A distance-scaling sniper whose beam deals more damage the farther it travels.",
    spriteSheet = "sprites/railgunner.png",
    qAbility = AbilityDef(name = "Frag Grenade", description = "Thrown explosive grenade.", cooldownMs = 10000, maxRange = 12, damage = 40, projectileType = ProjectileType.GRENADE, keybind = "Q"),
    eAbility = AbilityDef(name = "Railgun Shot", description = "Fires a devastating railgun shot that pierces everything.", cooldownMs = 14000, maxRange = 25, damage = 30, projectileType = ProjectileType.RAILGUN, keybind = "E"),
    primaryProjectileType = ProjectileType.SNIPER_BEAM, role = Ranged, maxHealth = 60
  )

  val Bombardier: CharacterDef = CharacterDef(
    id = CharacterId.Bombardier, displayName = "Bombardier",
    description = "An explosives expert with grenades, rockets, and cluster bombs.",
    spriteSheet = "sprites/bombardier.png",
    qAbility = AbilityDef(name = "Rocket", description = "Fires an explosive rocket.", cooldownMs = 12000, maxRange = 20, damage = 35, projectileType = ProjectileType.ROCKET, keybind = "Q"),
    eAbility = AbilityDef(name = "Cluster Bomb", description = "Deploys a devastating cluster bomb.", cooldownMs = 16000, maxRange = 14, damage = 35, projectileType = ProjectileType.CLUSTER_BOMB, keybind = "E"),
    primaryProjectileType = ProjectileType.GRENADE, role = Ranged, maxHealth = 70
  )

  val Sentinel: CharacterDef = CharacterDef(
    id = CharacterId.Sentinel, displayName = "Sentinel",
    description = "A defensive specialist with ricocheting lasers, lockdown chains, and mines.",
    spriteSheet = "sprites/sentinel.png",
    qAbility = AbilityDef(name = "Suppress", description = "Fires a lockdown chain.", cooldownMs = 8000, maxRange = 12, damage = 8, projectileType = ProjectileType.LOCKDOWN_CHAIN, keybind = "Q"),
    eAbility = AbilityDef(name = "Deploy Mine", description = "Lays a mine that arms in a moment and blows up under the first enemy over it.", cooldownMs = 14000, maxRange = 6, damage = 45, projectileType = -4, keybind = "E", castBehavior = TrapCast(TrapType.MINE, 6)),
    primaryProjectileType = ProjectileType.LASER_LIGHT, role = Ranged, maxHealth = 75
  )

  val Pilot: CharacterDef = CharacterDef(
    id = CharacterId.Pilot, displayName = "Pilot",
    description = "A fragile air support pilot with strafing runs and napalm strikes.",
    spriteSheet = "sprites/pilot.png",
    qAbility = AbilityDef(name = "Strafing Run", description = "Fires 3 bullets in a tight spread.", cooldownMs = 6000, maxRange = 18, damage = 20, projectileType = ProjectileType.BULLET, keybind = "Q", castBehavior = FanProjectile(3, Math.toRadians(20))),
    eAbility = AbilityDef(name = "Napalm Strike", description = "Calls in a napalm strike that burns on impact.", cooldownMs = 14000, maxRange = 16, damage = 30, projectileType = ProjectileType.NAPALM_STRIKE, keybind = "E"),
    primaryProjectileType = ProjectileType.BULLET_HEAVY, role = Ranged, maxHealth = 60
  )

  val Glitcher: CharacterDef = CharacterDef(
    id = CharacterId.Glitcher, displayName = "Glitcher",
    description = "A reality glitch that blinks and fires data bursts.",
    spriteSheet = "sprites/glitcher.png",
    qAbility = AbilityDef(name = "Glitch Blink", description = "Teleports through a glitch.", cooldownMs = 12000, maxRange = 6, damage = 0, projectileType = -2, keybind = "Q", castBehavior = TeleportCast(6)),
    eAbility = AbilityDef(name = "Data Burst", description = "Fires data bolts in all directions.", cooldownMs = 10000, maxRange = 16, damage = 14, projectileType = ProjectileType.DATA_BOLT, keybind = "E", castBehavior = FanProjectile(8, 2 * Math.PI)),
    primaryProjectileType = ProjectileType.DATA_BOLT, role = Ranged, maxHealth = 65
  )

  val characters: Seq[CharacterDef] = Seq(
    Cyborg, Hacker, MechPilot, Android, Chronomancer, Graviton,
    Tesla, Nanoswarm, Voidwalker, Photon, Railgunner, Bombardier,
    Sentinel, Pilot, Glitcher
  )
}
