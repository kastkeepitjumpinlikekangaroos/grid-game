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
    speedMultiplier = 0.75f, damage = 42, maxRange = 16,
    onHitEffect = Some(Slow(3000, 0.4f))
  )
  val ClawSwipeDef: ProjectileDef = ProjectileDef(id = ProjectileType.CLAW_SWIPE, name = "Claw Swipe", speedMultiplier = 0.7f, damage = 24, maxRange = 4, hitRadius = 2.2f, onHitEffect = Some(SpeedBoost(2000)))
  val VenomBoltDef: ProjectileDef = ProjectileDef(id = ProjectileType.VENOM_BOLT, name = "Venom Bolt", speedMultiplier = 0.75f, damage = 30, maxRange = 16, onHitEffect = Some(Slow(2000, 0.5f)))
  val WebShotDef: ProjectileDef = ProjectileDef(id = ProjectileType.WEB_SHOT, name = "Web Shot", speedMultiplier = 0.7f, damage = 34, maxRange = 14, onHitEffect = Some(Root(1500)))
  val StingerDef: ProjectileDef = ProjectileDef(id = ProjectileType.STINGER, name = "Stinger", speedMultiplier = 0.7f, damage = 21, maxRange = 6, onHitEffect = Some(Poison(9, 2000, 500)))
  val RootGrowthDef: ProjectileDef = ProjectileDef(id = ProjectileType.ROOT_GROWTH, name = "Root Growth", speedMultiplier = 0f, damage = 0, maxRange = 0, aoeOnMaxRange = Some(AoESplashConfig(8.0f, 18, rootDurationMs = 2500)), explosionConfig = Some(ExplosionConfig(0, 0, 8.0f)))
  val StingDef: ProjectileDef = ProjectileDef(id = ProjectileType.STING, name = "Sting", speedMultiplier = 0.7f, damage = 35, maxRange = 14, onHitEffect = Some(Slow(2000, 0.5f)))
  val GrabDef: ProjectileDef = ProjectileDef(id = ProjectileType.GRAB, name = "Grab", speedMultiplier = 0.9f, damage = 9, maxRange = 18, onHitEffect = Some(PullToOwner))
  val JawDef: ProjectileDef = ProjectileDef(id = ProjectileType.JAW, name = "Jaw", speedMultiplier = 0.85f, damage = 5, maxRange = 25, onHitEffect = Some(PullToOwner))
  val TongueDef: ProjectileDef = ProjectileDef(id = ProjectileType.TONGUE, name = "Tongue", speedMultiplier = 0.8f, damage = 15, maxRange = 18, onHitEffect = Some(PullToOwner))
  val MomentumStrikeDef: ProjectileDef = ProjectileDef(id = ProjectileType.MOMENTUM_STRIKE, name = "Momentum Strike", speedMultiplier = 0.8f, damage = 32, maxRange = 5, hitRadius = 2.5f, onHitEffect = Some(SpeedBoost(3000)))
  val PoisonCloudDef: ProjectileDef = ProjectileDef(id = ProjectileType.POISON_CLOUD, name = "Poison Cloud", speedMultiplier = 0.4f, damage = 27, maxRange = 14, passesThroughPlayers = true, aoeOnHit = Some(AoESplashConfig(3.5f, 18)), aoeOnMaxRange = Some(AoESplashConfig(4.0f, 27)), onHitEffect = Some(Slow(2000, 0.4f)))
  val AcidSprayDef: ProjectileDef = ProjectileDef(id = ProjectileType.ACID_SPRAY, name = "Acid Spray", speedMultiplier = 0.5f, damage = 18, maxRange = 12, aoeOnHit = Some(AoESplashConfig(3.0f, 10)), onHitEffect = Some(Burn(10, 2500, 500)))
  val FlameTrailDef: ProjectileDef = ProjectileDef(id = ProjectileType.FLAME_TRAIL, name = "Flame Trail", speedMultiplier = 0.7f, damage = 27, maxRange = 16, passesThroughPlayers = true, aoeOnMaxRange = Some(AoESplashConfig(3.5f, 27)), onHitEffect = Some(Burn(21, 3000, 750)))
  val FlameBoltHeavyDef: ProjectileDef = ProjectileDef(id = ProjectileType.FLAME_BOLT_HEAVY, name = "Flame Bolt Heavy", speedMultiplier = 0.75f, damage = 34, maxRange = 16, onHitEffect = Some(Burn(19, 3000, 750)))
  val ThornLightDef: ProjectileDef = ProjectileDef(id = ProjectileType.THORN_LIGHT, name = "Thorn Light", speedMultiplier = 0.65f, damage = 38, maxRange = 15, aoeOnHit = Some(AoESplashConfig(2.0f, 14)))
  val VenomBoltLightDef: ProjectileDef = ProjectileDef(id = ProjectileType.VENOM_BOLT_LIGHT, name = "Venom Bolt Light", speedMultiplier = 0.75f, damage = 21, maxRange = 16, onHitEffect = Some(Slow(2000, 0.5f)))
  // A slam (Plan 5a): a bot casts it when its target is inside the ability's GroundSlam radius,
  // so that radius and the AoE radius here are kept in step
  val HowlDef: ProjectileDef = ProjectileDef(id = ProjectileType.HOWL, name = "Howl", speedMultiplier = 0f, damage = 0, maxRange = 0, aoeOnMaxRange = Some(AoESplashConfig(9.0f, 10)), onHitEffect = Some(Slow(2500, 0.6f)), explosionConfig = Some(ExplosionConfig(0, 0, 9.0f)))
  // A closer (Plan 5a): lands on a ranged character walking away from 8 cells, wherever they are
  // in their step (MeleeKitsTest). See common/model/CLAUDE.md, "Melee kits"
  val ParalyticStingDef: ProjectileDef = ProjectileDef(id = ProjectileType.PARALYTIC_STING, name = "Paralytic Sting", speedMultiplier = 0.95f, damage = 12, maxRange = 16, onHitEffect = Some(Stun(700)))
  // What the volleys became (design/characters.md, *No volleys of more than three*). Slams, so
  // their GroundSlam radius and their AoE's are kept in step.
  // The Bear's Maul: claws raked all round him. It was a ring of eight claw swipes
  val MaulDef: ProjectileDef = ProjectileDef(id = ProjectileType.MAUL, name = "Maul", speedMultiplier = 0f, damage = 0, maxRange = 0, aoeOnMaxRange = Some(AoESplashConfig(4.0f, 33)), explosionConfig = Some(ExplosionConfig(0, 0, 4.0f)))
  // The Jellyfish's Electric Burst: a jolt that stuns everyone near. It was eight stings in a ring
  val ShockBurstDef: ProjectileDef = ProjectileDef(id = ProjectileType.SHOCK_BURST, name = "Shock Burst", speedMultiplier = 0f, damage = 0, maxRange = 0, aoeOnMaxRange = Some(AoESplashConfig(4.0f, 20, stunDurationMs = 600)), explosionConfig = Some(ExplosionConfig(0, 0, 4.0f)))
  // The Beetle's Horn Flip: its horn flips everyone near onto their backs, thrown back and stunned.
  // Its kit was the Golem's to the letter (a barrier and the same Tremor Slam), caught by KitTest
  val HornFlipDef: ProjectileDef = ProjectileDef(id = ProjectileType.HORN_FLIP, name = "Horn Flip", speedMultiplier = 0f, damage = 0, maxRange = 0, aoeOnMaxRange = Some(AoESplashConfig(5.0f, 28, stunDurationMs = 600)), onHitEffect = Some(Push(2.0f)), explosionConfig = Some(ExplosionConfig(0, 0, 5.0f)))

  val projectiles: Seq[ProjectileDef] = Seq(
    PoisonDartDef, ClawSwipeDef, VenomBoltDef, WebShotDef, StingerDef,
    RootGrowthDef, StingDef, GrabDef, JawDef,
    TongueDef, MomentumStrikeDef, PoisonCloudDef, AcidSprayDef, FlameTrailDef,
    FlameBoltHeavyDef, ThornLightDef, VenomBoltLightDef, HowlDef, ParalyticStingDef,
    MaulDef, ShockBurstDef, HornFlipDef
  )

  /** Plan 5a's variants: another type's flight and look, with an effect and a damage of their own
    * where they differ from it (the balance passes set each one's damage for its thrower). Made
    * from the registered base, so Roster makes them once every base is registered. */
  def variants(base: Byte => ProjectileDef): Seq[ProjectileDef] = Seq(
    // A primary with an identity of its own (Plan 5a): a slow at most, never a hold. Primaries fire
    // twice a second, and a hold grants 1.5s of CC immunity that would turn the kit's own holds away
    base(ProjectileType.CLAW_SWIPE).copy(id = ProjectileType.SHARK_CLAW, name = "Shark Claw", damage = 29, onHitEffect = Some(Poison(10, 1500, 500))),
    // Of its own, so its numbers can differ from the others who swipe the base
    base(ProjectileType.CLAW_SWIPE).copy(id = ProjectileType.MANTIS_SCYTHE, name = "Mantis Scythe", damage = 27),
    // The Hawk's Pin Down: its talons pinning its prey to the ground, where it fanned five gusts
    base(ProjectileType.TALON).copy(id = ProjectileType.PIN_DOWN, name = "Pin Down", damage = 18, onHitEffect = Some(Root(1200)))
  )

  // ── Characters ──

  val Wolf: CharacterDef = CharacterDef(
    id = CharacterId.Wolf, displayName = "Wolf",
    description = "A pack hunter with a speed-boosting strike and a howl that runs prey down.",
    spriteSheet = "sprites/wolf.png",
    qAbility = AbilityDef(name = "Momentum Strike", description = "Powerful strike that grants a speed boost on hit.", cooldownMs = 10000, maxRange = 5, damage = 32, projectileType = ProjectileType.MOMENTUM_STRIKE, keybind = "Q"),
    eAbility = AbilityDef(name = "Howl", description = "Howls, slowing every enemy within 9 cells for 2.5s.", cooldownMs = 12000, maxRange = 0, damage = 10, projectileType = ProjectileType.HOWL, keybind = "E", castBehavior = GroundSlam(9.0f)),
    primaryProjectileType = ProjectileType.CLAW_SWIPE, role = Melee, maxHealth = 130, moveSpeed = Melee.speed
  )

  val Serpent: CharacterDef = CharacterDef(
    id = CharacterId.Serpent, displayName = "Serpent",
    description = "A venomous snake that poisons and slithers away.",
    spriteSheet = "sprites/serpent.png",
    qAbility = AbilityDef(name = "Venom Spit", description = "Spits venom that slows.", cooldownMs = 7000, maxRange = 16, damage = 42, projectileType = ProjectileType.POISON_DART, keybind = "Q"),
    eAbility = AbilityDef(name = "Slither", description = "Dashes sideways.", cooldownMs = 12000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = DashBuff(8, 300, 15)),
    primaryProjectileType = ProjectileType.VENOM_BOLT, role = Ranged, maxHealth = 70
  )

  val Spider: CharacterDef = CharacterDef(
    id = CharacterId.Spider, displayName = "Spider",
    description = "A web-spinning arachnid that lays webs for foes to blunder into, and burrows away.",
    spriteSheet = "sprites/spider.png",
    qAbility = AbilityDef(name = "Burrow", description = "Burrows underground and comes up again up to 8 cells away.", cooldownMs = 12000, maxRange = 8, damage = 0, projectileType = -2, keybind = "Q", castBehavior = TeleportCast(8)),
    eAbility = AbilityDef(name = "Web Trap", description = "Spins a web across the ground that is ready in a moment and roots the first enemy over it for 2s.", cooldownMs = 12000, maxRange = 6, damage = 0, projectileType = -4, keybind = "E", castBehavior = TrapCast(TrapType.SNARE, 6)),
    primaryProjectileType = ProjectileType.WEB_SHOT, role = Ranged, maxHealth = 75
  )

  val Bear: CharacterDef = CharacterDef(
    id = CharacterId.Bear, displayName = "Bear",
    description = "A devastating brawler that grabs enemies close and mauls everything around it.",
    spriteSheet = "sprites/bear.png",
    qAbility = AbilityDef(name = "Bear Hug", description = "Grabs and pulls an enemy toward you.", cooldownMs = 8000, maxRange = 18, damage = 9, projectileType = ProjectileType.GRAB, keybind = "Q"),
    eAbility = AbilityDef(name = "Maul", description = "Rakes its claws all around, mauling every enemy within 4 cells.", cooldownMs = 12000, maxRange = 0, damage = 33, projectileType = ProjectileType.MAUL, keybind = "E", castBehavior = GroundSlam(4.0f)),
    primaryProjectileType = ProjectileType.CLAW_SWIPE, role = Melee, maxHealth = 145, moveSpeed = Melee.speed
  )

  val Scorpion: CharacterDef = CharacterDef(
    id = CharacterId.Scorpion, displayName = "Scorpion",
    description = "A venomous scorpion with a poison stinger, a paralytic sting and an acid spray that burns.",
    spriteSheet = "sprites/scorpion.png",
    qAbility = AbilityDef(name = "Paralytic Sting", description = "Flicks a paralytic sting that stuns for 0.7s.", cooldownMs = 9000, maxRange = 16, damage = 12, projectileType = ProjectileType.PARALYTIC_STING, keybind = "Q"),
    eAbility = AbilityDef(name = "Acid Spray", description = "Sprays acid that burns and splashes.", cooldownMs = 10000, maxRange = 12, damage = 18, projectileType = ProjectileType.ACID_SPRAY, keybind = "E"),
    primaryProjectileType = ProjectileType.STINGER, role = Melee, maxHealth = 145, moveSpeed = Melee.speed
  )

  val Hawk: CharacterDef = CharacterDef(
    id = CharacterId.Hawk, displayName = "Hawk",
    description = "A speed hunter that dive-bombs its prey and pins it to the ground.",
    spriteSheet = "sprites/hawk.png",
    qAbility = AbilityDef(name = "Dive Bomb", description = "Swoops to the target location.", cooldownMs = 10000, maxRange = 8, damage = 0, projectileType = -2, keybind = "Q", castBehavior = TeleportCast(8)),
    eAbility = AbilityDef(name = "Pin Down", description = "Strikes with its talons, pinning the first enemy it reaches to the ground for 1.2s.", cooldownMs = 10000, maxRange = 4, damage = 18, projectileType = ProjectileType.PIN_DOWN, keybind = "E"),
    primaryProjectileType = ProjectileType.TALON, role = Melee, maxHealth = 135, moveSpeed = Melee.speed
  )

  val Shark: CharacterDef = CharacterDef(
    id = CharacterId.Shark, displayName = "Shark",
    description = "A ferocious ocean predator that drags prey close and dives through terrain.",
    spriteSheet = "sprites/shark.png",
    qAbility = AbilityDef(name = "Jaw Drag", description = "Bites and pulls an enemy toward you.", cooldownMs = 8000, maxRange = 18, damage = 10, projectileType = ProjectileType.JAW, keybind = "Q"),
    eAbility = AbilityDef(name = "Deep Dive", description = "Submerges and swims through terrain, immune.", cooldownMs = 14000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = PhaseShiftBuff(3000)),
    primaryProjectileType = ProjectileType.SHARK_CLAW, role = Melee, maxHealth = 140, moveSpeed = Melee.speed
  )

  val Beetle: CharacterDef = CharacterDef(
    id = CharacterId.Beetle, displayName = "Beetle",
    description = "An armored tank that shields in its shell and flips anyone who comes close onto their back.",
    spriteSheet = "sprites/beetle.png",
    qAbility = AbilityDef(name = "Carapace", description = "Braces your shell into a wall that stops enemy shots, even while you attack.", cooldownMs = 12000, maxRange = 0, damage = 0, projectileType = -3, keybind = "Q", castBehavior = BarrierCast(3000)),
    eAbility = AbilityDef(name = "Horn Flip", description = "Flips every enemy within 5 cells onto their back: thrown back 2 cells and stunned for 0.6s.", cooldownMs = 15500, maxRange = 0, damage = 28, projectileType = ProjectileType.HORN_FLIP, keybind = "E", castBehavior = GroundSlam(5.0f)),
    primaryProjectileType = ProjectileType.BOULDER, role = Skirmisher, maxHealth = 130, moveSpeed = Skirmisher.speed
  )

  val Treant: CharacterDef = CharacterDef(
    id = CharacterId.Treant, displayName = "Treant",
    description = "A living tree with root pulls and thorn bursts.",
    spriteSheet = "sprites/treant.png",
    qAbility = AbilityDef(name = "Root Pull", description = "Roots that pull an enemy.", cooldownMs = 12000, maxRange = 18, damage = 14, projectileType = ProjectileType.VINE_WHIP, keybind = "Q"),
    eAbility = AbilityDef(name = "Root Growth", description = "Roots erupt from the ground, entangling nearby enemies.", cooldownMs = 14000, maxRange = 0, damage = 18, projectileType = ProjectileType.ROOT_GROWTH, keybind = "E", castBehavior = GroundSlam(8.0f)),
    primaryProjectileType = ProjectileType.THORN_LIGHT, role = Ranged, maxHealth = 80
  )

  val Phoenix: CharacterDef = CharacterDef(
    id = CharacterId.Phoenix, displayName = "Phoenix",
    description = "A hit-and-run fire bird that leaves burning trails and teleports to safety.",
    spriteSheet = "sprites/phoenix.png",
    qAbility = AbilityDef(name = "Flame Trail", description = "Passes through players and leaves burn AoE at max range.", cooldownMs = 8000, maxRange = 16, damage = 27, projectileType = ProjectileType.FLAME_TRAIL, keybind = "Q"),
    eAbility = AbilityDef(name = "Rebirth", description = "Teleports in a burst of flame.", cooldownMs = 10000, maxRange = 10, damage = 0, projectileType = -2, keybind = "E", castBehavior = TeleportCast(10)),
    primaryProjectileType = ProjectileType.FLAME_BOLT_HEAVY, role = Ranged, maxHealth = 65
  )

  val Hydra: CharacterDef = CharacterDef(
    id = CharacterId.Hydra, displayName = "Hydra",
    description = "A many-headed serpent that spits venom from three heads at once and breathes poison clouds.",
    spriteSheet = "sprites/hydra.png",
    qAbility = AbilityDef(name = "Multi-Head Spit", description = "Spits slowing venom from 3 heads at once.", cooldownMs = 9000, maxRange = 16, damage = 30, projectileType = ProjectileType.VENOM_BOLT, keybind = "Q", castBehavior = FanProjectile(3, Math.toRadians(40))),
    eAbility = AbilityDef(name = "Poison Cloud", description = "Launches a poison cloud that slows and AoE damages.", cooldownMs = 12000, maxRange = 14, damage = 27, projectileType = ProjectileType.POISON_CLOUD, keybind = "E"),
    primaryProjectileType = ProjectileType.VENOM_BOLT_LIGHT, role = Ranged, maxHealth = 80
  )

  val Mantis: CharacterDef = CharacterDef(
    id = CharacterId.Mantis, displayName = "Mantis",
    description = "An ambush predator that camouflages, then strikes from behind.",
    spriteSheet = "sprites/mantis.png",
    qAbility = AbilityDef(name = "Leaf Cloak", description = "Camouflages: phased and immune.", cooldownMs = 12000, maxRange = 0, damage = 0, projectileType = -1, keybind = "Q", castBehavior = PhaseShiftBuff(2000)),
    eAbility = AbilityDef(name = "Mantis Strike", description = "Strikes from behind the target, teleporting you there.", cooldownMs = 14000, maxRange = 14, damage = 31, projectileType = ProjectileType.SHADOW_HAUNT, keybind = "E"),
    primaryProjectileType = ProjectileType.MANTIS_SCYTHE, role = Melee, maxHealth = 135, moveSpeed = Melee.speed
  )

  val Jellyfish: CharacterDef = CharacterDef(
    id = CharacterId.Jellyfish, displayName = "Jellyfish",
    description = "A floating jellyfish whose electric burst stuns everyone near, and who drifts away.",
    spriteSheet = "sprites/jellyfish.png",
    qAbility = AbilityDef(name = "Electric Burst", description = "Discharges a jolt that stuns every enemy within 4 cells for 0.6s.", cooldownMs = 12000, maxRange = 0, damage = 20, projectileType = ProjectileType.SHOCK_BURST, keybind = "Q", castBehavior = GroundSlam(4.0f)),
    eAbility = AbilityDef(name = "Drift", description = "Drifts through the water to a new location.", cooldownMs = 12000, maxRange = 8, damage = 0, projectileType = -2, keybind = "E", castBehavior = TeleportCast(8)),
    primaryProjectileType = ProjectileType.STING, role = Ranged, maxHealth = 75
  )

  val Gorilla: CharacterDef = CharacterDef(
    id = CharacterId.Gorilla, displayName = "Gorilla",
    description = "A mighty primate that grabs enemies and slams the ground.",
    spriteSheet = "sprites/gorilla.png",
    qAbility = AbilityDef(name = "Primate Grab", description = "Long-range grab that pulls enemies to you.", cooldownMs = 8000, maxRange = 18, damage = 9, projectileType = ProjectileType.GRAB, keybind = "Q"),
    eAbility = AbilityDef(name = "Ground Pound", description = "Slams the ground with devastating AoE.", cooldownMs = 10500, maxRange = 12, damage = 24, projectileType = ProjectileType.SEISMIC_SLAM, keybind = "E"),
    primaryProjectileType = ProjectileType.BOULDER, role = Skirmisher, maxHealth = 150, moveSpeed = Skirmisher.speed
  )

  val Chameleon: CharacterDef = CharacterDef(
    id = CharacterId.Chameleon, displayName = "Chameleon",
    description = "A stealthy lizard that camouflages and snares prey with its tongue.",
    spriteSheet = "sprites/chameleon.png",
    qAbility = AbilityDef(name = "Camouflage", description = "Becomes invisible: phased.", cooldownMs = 12000, maxRange = 0, damage = 0, projectileType = -1, keybind = "Q", castBehavior = PhaseShiftBuff(5000)),
    eAbility = AbilityDef(name = "Tongue Lash", description = "Snaps out a long tongue that pulls enemies toward you.", cooldownMs = 10000, maxRange = 18, damage = 15, projectileType = ProjectileType.TONGUE, keybind = "E"),
    primaryProjectileType = ProjectileType.POISON_DART, role = Ranged, maxHealth = 75
  )

  val characters: Seq[CharacterDef] = Seq(
    Wolf, Serpent, Spider, Bear, Scorpion, Hawk,
    Shark, Beetle, Treant, Phoenix, Hydra, Mantis,
    Jellyfish, Gorilla, Chameleon
  )
}
