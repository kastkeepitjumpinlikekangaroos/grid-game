package com.gridgame.common.model.roster

import com.gridgame.common.model._
import com.gridgame.common.model.CombatRole.{Melee, Ranged, Skirmisher}

/**
 * Beasts of nature (character ids 72-86).
 *
 * First the definitions of the projectiles a character here is the first in the roster to throw,
 * then the characters. CharacterDef's initializer registers every definition (Roster); how a
 * projectile type looks is GLProjectileRenderers'.
 */
private[model] object Beasts {
  // ── Projectiles ──

  val PoisonDartDef: ProjectileDef = ProjectileDef(
    id = ProjectileType.POISON_DART, name = "Poison Dart",
    speedMultiplier = 0.75f, damage = 15, maxRange = 16,
    onHitEffect = Some(Slow(3000, 0.4f))
  )
  val ClawSwipeDef: ProjectileDef = ProjectileDef(id = ProjectileType.CLAW_SWIPE, name = "Claw Swipe", speedMultiplier = 0.7f, damage = 25, maxRange = 4, hitRadius = 2.2f, onHitEffect = Some(SpeedBoost(2000)))
  val VenomBoltDef: ProjectileDef = ProjectileDef(id = ProjectileType.VENOM_BOLT, name = "Venom Bolt", speedMultiplier = 0.75f, damage = 14, maxRange = 16, onHitEffect = Some(Slow(2000, 0.5f)))
  val WebShotDef: ProjectileDef = ProjectileDef(id = ProjectileType.WEB_SHOT, name = "Web Shot", speedMultiplier = 0.7f, damage = 12, maxRange = 14, onHitEffect = Some(Root(1500)))
  val StingerDef: ProjectileDef = ProjectileDef(id = ProjectileType.STINGER, name = "Stinger", speedMultiplier = 0.7f, damage = 18, maxRange = 6, onHitEffect = Some(Poison(8, 2000, 500)))
  val RootGrowthDef: ProjectileDef = ProjectileDef(id = ProjectileType.ROOT_GROWTH, name = "Root Growth", speedMultiplier = 0f, damage = 0, maxRange = 0, aoeOnMaxRange = Some(AoESplashConfig(8.0f, 10, rootDurationMs = 2500)), explosionConfig = Some(ExplosionConfig(0, 0, 8.0f)))
  val WebTrapDef: ProjectileDef = ProjectileDef(id = ProjectileType.WEB_TRAP, name = "Web Trap", speedMultiplier = 0.5f, damage = 0, maxRange = 14, passesThroughPlayers = true, aoeOnMaxRange = Some(AoESplashConfig(5.0f, 8, rootDurationMs = 2000)), explosionConfig = Some(ExplosionConfig(0, 0, 5.0f)))
  val StingDef: ProjectileDef = ProjectileDef(id = ProjectileType.STING, name = "Sting", speedMultiplier = 0.7f, damage = 12, maxRange = 14, onHitEffect = Some(Slow(2000, 0.5f)))
  val GrabDef: ProjectileDef = ProjectileDef(id = ProjectileType.GRAB, name = "Grab", speedMultiplier = 0.9f, damage = 8, maxRange = 18, onHitEffect = Some(PullToOwner))
  val JawDef: ProjectileDef = ProjectileDef(id = ProjectileType.JAW, name = "Jaw", speedMultiplier = 0.85f, damage = 5, maxRange = 25, onHitEffect = Some(PullToOwner))
  val TongueDef: ProjectileDef = ProjectileDef(id = ProjectileType.TONGUE, name = "Tongue", speedMultiplier = 0.8f, damage = 8, maxRange = 18, onHitEffect = Some(PullToOwner))
  val MomentumStrikeDef: ProjectileDef = ProjectileDef(id = ProjectileType.MOMENTUM_STRIKE, name = "Momentum Strike", speedMultiplier = 0.8f, damage = 30, maxRange = 5, hitRadius = 2.5f, onHitEffect = Some(SpeedBoost(3000)))
  val PoisonCloudDef: ProjectileDef = ProjectileDef(id = ProjectileType.POISON_CLOUD, name = "Poison Cloud", speedMultiplier = 0.4f, damage = 15, maxRange = 14, passesThroughPlayers = true, aoeOnHit = Some(AoESplashConfig(3.5f, 10)), aoeOnMaxRange = Some(AoESplashConfig(4.0f, 15)), onHitEffect = Some(Slow(2000, 0.4f)))
  val AcidSprayDef: ProjectileDef = ProjectileDef(id = ProjectileType.ACID_SPRAY, name = "Acid Spray", speedMultiplier = 0.5f, damage = 18, maxRange = 12, aoeOnHit = Some(AoESplashConfig(3.0f, 10)), onHitEffect = Some(Burn(10, 2500, 500)))
  val FlameTrailDef: ProjectileDef = ProjectileDef(id = ProjectileType.FLAME_TRAIL, name = "Flame Trail", speedMultiplier = 0.7f, damage = 15, maxRange = 16, passesThroughPlayers = true, aoeOnMaxRange = Some(AoESplashConfig(3.5f, 15)), onHitEffect = Some(Burn(12, 3000, 750)))
  val FlameBoltHeavyDef: ProjectileDef = ProjectileDef(id = ProjectileType.FLAME_BOLT_HEAVY, name = "Flame Bolt Heavy", speedMultiplier = 0.75f, damage = 22, maxRange = 16, onHitEffect = Some(Burn(12, 3000, 750)))
  val ThornLightDef: ProjectileDef = ProjectileDef(id = ProjectileType.THORN_LIGHT, name = "Thorn Light", speedMultiplier = 0.65f, damage = 10, maxRange = 15, aoeOnHit = Some(AoESplashConfig(2.0f, 6)))
  val VenomBoltLightDef: ProjectileDef = ProjectileDef(id = ProjectileType.VENOM_BOLT_LIGHT, name = "Venom Bolt Light", speedMultiplier = 0.75f, damage = 11, maxRange = 16, onHitEffect = Some(Slow(2000, 0.5f)))
  // A slam (Plan 5a): a bot casts it when its target is inside the ability's GroundSlam radius,
  // so that radius and the AoE radius here are kept in step
  val HowlDef: ProjectileDef = ProjectileDef(id = ProjectileType.HOWL, name = "Howl", speedMultiplier = 0f, damage = 0, maxRange = 0, aoeOnMaxRange = Some(AoESplashConfig(9.0f, 10)), onHitEffect = Some(Slow(2500, 0.6f)), explosionConfig = Some(ExplosionConfig(0, 0, 9.0f)))
  // A closer (Plan 5a): lands on a ranged character walking away from 8 cells, wherever they are
  // in their step (MeleeKitsTest). See common/model/CLAUDE.md, "Melee kits"
  val ParalyticStingDef: ProjectileDef = ProjectileDef(id = ProjectileType.PARALYTIC_STING, name = "Paralytic Sting", speedMultiplier = 0.95f, damage = 12, maxRange = 16, onHitEffect = Some(Stun(700)))

  val projectiles: Seq[ProjectileDef] = Seq(
    PoisonDartDef, ClawSwipeDef, VenomBoltDef, WebShotDef, StingerDef,
    RootGrowthDef, WebTrapDef, StingDef, GrabDef, JawDef,
    TongueDef, MomentumStrikeDef, PoisonCloudDef, AcidSprayDef, FlameTrailDef,
    FlameBoltHeavyDef, ThornLightDef, VenomBoltLightDef, HowlDef, ParalyticStingDef
  )

  /** Plan 5a's variants: another type's numbers and look, with an effect of their own. Made from
    * the registered base, so Roster makes them once every base is registered. */
  def variants(base: Byte => ProjectileDef): Seq[ProjectileDef] = Seq(
    // A primary with an identity of its own (Plan 5a): a slow at most, never a hold. Primaries fire
    // twice a second, and a hold grants 1.5s of CC immunity that would turn the kit's own holds away
    base(ProjectileType.CLAW_SWIPE).copy(id = ProjectileType.SHARK_CLAW, name = "Shark Claw", onHitEffect = Some(Poison(9, 1500, 500)))
  )

  // ── Characters ──

  val Wolf: CharacterDef = CharacterDef(
    id = CharacterId.Wolf, displayName = "Wolf",
    description = "A pack hunter with a speed-boosting strike and a howl that runs prey down.",
    spriteSheet = "sprites/wolf.png",
    qAbility = AbilityDef(name = "Momentum Strike", description = "Powerful strike that grants a speed boost on hit.", cooldownMs = 10000, maxRange = 5, damage = 30, projectileType = ProjectileType.MOMENTUM_STRIKE, keybind = "Q"),
    eAbility = AbilityDef(name = "Howl", description = "Howls, slowing every enemy within 9 cells for 2.5s.", cooldownMs = 12000, maxRange = 0, damage = 10, projectileType = ProjectileType.HOWL, keybind = "E", castBehavior = GroundSlam(9.0f)),
    primaryProjectileType = ProjectileType.CLAW_SWIPE, role = Melee, maxHealth = 120, moveSpeed = Melee.speed
  )

  val Serpent: CharacterDef = CharacterDef(
    id = CharacterId.Serpent, displayName = "Serpent",
    description = "A venomous snake that poisons and slithers away.",
    spriteSheet = "sprites/serpent.png",
    qAbility = AbilityDef(name = "Venom Spit", description = "Spits venom that slows.", cooldownMs = 7000, maxRange = 16, damage = 15, projectileType = ProjectileType.POISON_DART, keybind = "Q"),
    eAbility = AbilityDef(name = "Slither", description = "Dashes sideways.", cooldownMs = 12000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = DashBuff(8, 300, 15)),
    primaryProjectileType = ProjectileType.VENOM_BOLT, role = Ranged, maxHealth = 70
  )

  val Spider: CharacterDef = CharacterDef(
    id = CharacterId.Spider, displayName = "Spider",
    description = "A web-spinning arachnid that traps and burrows.",
    spriteSheet = "sprites/spider.png",
    qAbility = AbilityDef(name = "Web Spray", description = "Fires 5 webs in a fan.", cooldownMs = 10000, maxRange = 14, damage = 12, projectileType = ProjectileType.WEB_SHOT, keybind = "Q", castBehavior = FanProjectile(5, Math.toRadians(45))),
    eAbility = AbilityDef(name = "Web Trap", description = "Launches a web that roots enemies on landing.", cooldownMs = 12000, maxRange = 14, damage = 8, projectileType = ProjectileType.WEB_TRAP, keybind = "E"),
    primaryProjectileType = ProjectileType.WEB_SHOT, role = Ranged, maxHealth = 70
  )

  val Bear: CharacterDef = CharacterDef(
    id = CharacterId.Bear, displayName = "Bear",
    description = "A devastating brawler that grabs enemies close and mauls in all directions.",
    spriteSheet = "sprites/bear.png",
    qAbility = AbilityDef(name = "Bear Hug", description = "Grabs and pulls an enemy toward you.", cooldownMs = 8000, maxRange = 18, damage = 8, projectileType = ProjectileType.GRAB, keybind = "Q"),
    eAbility = AbilityDef(name = "Maul", description = "Swipes claws in all directions.", cooldownMs = 14000, maxRange = 4, damage = 25, projectileType = ProjectileType.CLAW_SWIPE, keybind = "E", castBehavior = FanProjectile(8, 2 * Math.PI)),
    primaryProjectileType = ProjectileType.CLAW_SWIPE, role = Melee, maxHealth = 145, moveSpeed = Melee.speed
  )

  val Scorpion: CharacterDef = CharacterDef(
    id = CharacterId.Scorpion, displayName = "Scorpion",
    description = "A venomous scorpion with a poison stinger, a paralytic sting and an acid spray that burns.",
    spriteSheet = "sprites/scorpion.png",
    qAbility = AbilityDef(name = "Paralytic Sting", description = "Flicks a paralytic sting that stuns for 0.7s.", cooldownMs = 9000, maxRange = 16, damage = 12, projectileType = ProjectileType.PARALYTIC_STING, keybind = "Q"),
    eAbility = AbilityDef(name = "Acid Spray", description = "Sprays acid that burns and splashes.", cooldownMs = 10000, maxRange = 12, damage = 18, projectileType = ProjectileType.ACID_SPRAY, keybind = "E"),
    primaryProjectileType = ProjectileType.STINGER, role = Melee, maxHealth = 130, moveSpeed = Melee.speed
  )

  val Hawk: CharacterDef = CharacterDef(
    id = CharacterId.Hawk, displayName = "Hawk",
    description = "A speed hunter that dive-bombs targets and fans gusts to scatter foes.",
    spriteSheet = "sprites/hawk.png",
    qAbility = AbilityDef(name = "Dive Bomb", description = "Swoops to the target location.", cooldownMs = 10000, maxRange = 8, damage = 0, projectileType = -2, keybind = "Q", castBehavior = TeleportCast(8)),
    eAbility = AbilityDef(name = "Wind Wall", description = "Fires 5 gusts in a wide fan that push enemies.", cooldownMs = 8000, maxRange = 6, damage = 10, projectileType = ProjectileType.GUST, keybind = "E", castBehavior = FanProjectile(5, Math.toRadians(90))),
    primaryProjectileType = ProjectileType.TALON, role = Melee, maxHealth = 120, moveSpeed = Melee.speed
  )

  val Shark: CharacterDef = CharacterDef(
    id = CharacterId.Shark, displayName = "Shark",
    description = "A ferocious ocean predator that drags prey close and dives through terrain.",
    spriteSheet = "sprites/shark.png",
    qAbility = AbilityDef(name = "Jaw Drag", description = "Bites and pulls an enemy toward you.", cooldownMs = 8000, maxRange = 18, damage = 10, projectileType = ProjectileType.JAW, keybind = "Q"),
    eAbility = AbilityDef(name = "Deep Dive", description = "Submerges and swims through terrain, immune.", cooldownMs = 14000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = PhaseShiftBuff(3000)),
    primaryProjectileType = ProjectileType.SHARK_CLAW, role = Melee, maxHealth = 135, moveSpeed = Melee.speed
  )

  val Beetle: CharacterDef = CharacterDef(
    id = CharacterId.Beetle, displayName = "Beetle",
    description = "An armored tank that shields in its shell and slams the ground.",
    spriteSheet = "sprites/beetle.png",
    qAbility = AbilityDef(name = "Carapace", description = "Braces your shell into a wall that stops enemy shots until you attack.", cooldownMs = 12000, maxRange = 0, damage = 0, projectileType = -3, keybind = "Q", castBehavior = BarrierCast(3000)),
    eAbility = AbilityDef(name = "Tremor Slam", description = "Slams the ground, rooting nearby enemies.", cooldownMs = 14000, maxRange = 0, damage = 30, projectileType = ProjectileType.TREMOR_SLAM, keybind = "E", castBehavior = GroundSlam(6.0f)),
    primaryProjectileType = ProjectileType.BOULDER, role = Skirmisher, maxHealth = 145, moveSpeed = Skirmisher.speed
  )

  val Treant: CharacterDef = CharacterDef(
    id = CharacterId.Treant, displayName = "Treant",
    description = "A living tree with root pulls and thorn bursts.",
    spriteSheet = "sprites/treant.png",
    qAbility = AbilityDef(name = "Root Pull", description = "Roots that pull an enemy.", cooldownMs = 12000, maxRange = 18, damage = 8, projectileType = ProjectileType.VINE_WHIP, keybind = "Q"),
    eAbility = AbilityDef(name = "Root Growth", description = "Roots erupt from the ground, entangling nearby enemies.", cooldownMs = 14000, maxRange = 0, damage = 10, projectileType = ProjectileType.ROOT_GROWTH, keybind = "E", castBehavior = GroundSlam(8.0f)),
    primaryProjectileType = ProjectileType.THORN_LIGHT, role = Ranged, maxHealth = 80
  )

  val Phoenix: CharacterDef = CharacterDef(
    id = CharacterId.Phoenix, displayName = "Phoenix",
    description = "A hit-and-run fire bird that leaves burning trails and teleports to safety.",
    spriteSheet = "sprites/phoenix.png",
    qAbility = AbilityDef(name = "Flame Trail", description = "Passes through players and leaves burn AoE at max range.", cooldownMs = 8000, maxRange = 16, damage = 15, projectileType = ProjectileType.FLAME_TRAIL, keybind = "Q"),
    eAbility = AbilityDef(name = "Rebirth", description = "Teleports in a burst of flame.", cooldownMs = 10000, maxRange = 10, damage = 0, projectileType = -2, keybind = "E", castBehavior = TeleportCast(10)),
    primaryProjectileType = ProjectileType.FLAME_BOLT_HEAVY, role = Ranged, maxHealth = 65
  )

  val Hydra: CharacterDef = CharacterDef(
    id = CharacterId.Hydra, displayName = "Hydra",
    description = "A multi-headed serpent with venom fans and toxic poison clouds.",
    spriteSheet = "sprites/hydra.png",
    qAbility = AbilityDef(name = "Multi-Head Spit", description = "Spits venom from 5 heads.", cooldownMs = 10000, maxRange = 16, damage = 14, projectileType = ProjectileType.VENOM_BOLT, keybind = "Q", castBehavior = FanProjectile(5, Math.toRadians(60))),
    eAbility = AbilityDef(name = "Poison Cloud", description = "Launches a poison cloud that slows and AoE damages.", cooldownMs = 12000, maxRange = 14, damage = 15, projectileType = ProjectileType.POISON_CLOUD, keybind = "E"),
    primaryProjectileType = ProjectileType.VENOM_BOLT_LIGHT, role = Ranged, maxHealth = 75
  )

  val Mantis: CharacterDef = CharacterDef(
    id = CharacterId.Mantis, displayName = "Mantis",
    description = "An ambush predator that camouflages, then strikes from behind.",
    spriteSheet = "sprites/mantis.png",
    qAbility = AbilityDef(name = "Leaf Cloak", description = "Camouflages: phased and immune.", cooldownMs = 12000, maxRange = 0, damage = 0, projectileType = -1, keybind = "Q", castBehavior = PhaseShiftBuff(2000)),
    eAbility = AbilityDef(name = "Mantis Strike", description = "Strikes from behind the target, teleporting you there.", cooldownMs = 14000, maxRange = 14, damage = 25, projectileType = ProjectileType.SHADOW_HAUNT, keybind = "E"),
    primaryProjectileType = ProjectileType.CLAW_SWIPE, role = Melee, maxHealth = 120, moveSpeed = Melee.speed
  )

  val Jellyfish: CharacterDef = CharacterDef(
    id = CharacterId.Jellyfish, displayName = "Jellyfish",
    description = "A floating jellyfish with electric bursts and drift teleportation.",
    spriteSheet = "sprites/jellyfish.png",
    qAbility = AbilityDef(name = "Electric Burst", description = "Fires stinging tentacles in all directions.", cooldownMs = 10000, maxRange = 14, damage = 12, projectileType = ProjectileType.STING, keybind = "Q", castBehavior = FanProjectile(8, 2 * Math.PI)),
    eAbility = AbilityDef(name = "Drift", description = "Drifts through the water to a new location.", cooldownMs = 12000, maxRange = 8, damage = 0, projectileType = -2, keybind = "E", castBehavior = TeleportCast(8)),
    primaryProjectileType = ProjectileType.STING, role = Ranged, maxHealth = 70
  )

  val Gorilla: CharacterDef = CharacterDef(
    id = CharacterId.Gorilla, displayName = "Gorilla",
    description = "A mighty primate that grabs enemies and slams the ground.",
    spriteSheet = "sprites/gorilla.png",
    qAbility = AbilityDef(name = "Primate Grab", description = "Long-range grab that pulls enemies to you.", cooldownMs = 8000, maxRange = 18, damage = 8, projectileType = ProjectileType.GRAB, keybind = "Q"),
    eAbility = AbilityDef(name = "Ground Pound", description = "Slams the ground with devastating AoE.", cooldownMs = 12000, maxRange = 12, damage = 25, projectileType = ProjectileType.SEISMIC_SLAM, keybind = "E"),
    primaryProjectileType = ProjectileType.BOULDER, role = Skirmisher, maxHealth = 145, moveSpeed = Skirmisher.speed
  )

  val Chameleon: CharacterDef = CharacterDef(
    id = CharacterId.Chameleon, displayName = "Chameleon",
    description = "A stealthy lizard that camouflages and snares prey with its tongue.",
    spriteSheet = "sprites/chameleon.png",
    qAbility = AbilityDef(name = "Camouflage", description = "Becomes invisible: phased.", cooldownMs = 12000, maxRange = 0, damage = 0, projectileType = -1, keybind = "Q", castBehavior = PhaseShiftBuff(5000)),
    eAbility = AbilityDef(name = "Tongue Lash", description = "Snaps out a long tongue that pulls enemies toward you.", cooldownMs = 10000, maxRange = 18, damage = 8, projectileType = ProjectileType.TONGUE, keybind = "E"),
    primaryProjectileType = ProjectileType.POISON_DART, role = Ranged, maxHealth = 70
  )

  val characters: Seq[CharacterDef] = Seq(
    Wolf, Serpent, Spider, Bear, Scorpion, Hawk,
    Shark, Beetle, Treant, Phoenix, Hydra, Mantis,
    Jellyfish, Gorilla, Chameleon
  )
}
