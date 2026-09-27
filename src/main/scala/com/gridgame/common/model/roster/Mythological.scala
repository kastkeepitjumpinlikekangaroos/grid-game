package com.gridgame.common.model.roster

import com.gridgame.common.model._
import com.gridgame.common.model.CombatRole.{Melee, Ranged, Skirmisher}

/**
 * Creatures of myth (character ids 87-101).
 *
 * First the definitions of the projectiles a character here is the first in the roster to throw,
 * then the characters. CharacterDef's initializer registers every definition (Roster); how a
 * projectile type looks is GLProjectileRenderers'.
 */
private[model] object Mythological {
  // ── Projectiles ──

  val HeadThrowDef: ProjectileDef = ProjectileDef(id = ProjectileType.HEAD_THROW, name = "Head Throw", speedMultiplier = 0.7f, damage = 25, maxRange = 16, onHitEffect = Some(Push(2.5f)), alsoOnHit = Seq(Stun(800)))
  val StoneGazeDef: ProjectileDef = ProjectileDef(id = ProjectileType.STONE_GAZE, name = "Stone Gaze", speedMultiplier = 0f, damage = 0, maxRange = 0, aoeOnMaxRange = Some(AoESplashConfig(6.0f, 15, rootDurationMs = 2000)), explosionConfig = Some(ExplosionConfig(0, 0, 6.0f)))
  val InkSnareDef: ProjectileDef = ProjectileDef(id = ProjectileType.INK_SNARE, name = "Ink Snare", speedMultiplier = 0.5f, damage = 0, maxRange = 14, passesThroughPlayers = true, aoeOnMaxRange = Some(AoESplashConfig(5.0f, 10, rootDurationMs = 2000)), explosionConfig = Some(ExplosionConfig(0, 0, 5.0f)))
  val HornDef: ProjectileDef = ProjectileDef(id = ProjectileType.HORN, name = "Horn", speedMultiplier = 0.6f, damage = 33, maxRange = 5, hitRadius = 2.5f)
  val MysticBoltDef: ProjectileDef = ProjectileDef(id = ProjectileType.MYSTIC_BOLT, name = "Mystic Bolt", speedMultiplier = 0.75f, damage = 18, maxRange = 16, onHitEffect = Some(Burn(12, 3000, 750)))
  val PetrifyDef: ProjectileDef = ProjectileDef(id = ProjectileType.PETRIFY, name = "Petrify", speedMultiplier = 0.3f, damage = 5, maxRange = 20, onHitEffect = Some(Freeze(3000)))
  val ThrownBoulderDef: ProjectileDef = ProjectileDef(id = ProjectileType.THROWN_BOULDER, name = "Thrown Boulder", speedMultiplier = 0.3f, damage = 38, maxRange = 10, hitRadius = 3.0f)
  val EyeBeamDef: ProjectileDef = ProjectileDef(id = ProjectileType.EYE_BEAM, name = "Eye Beam", speedMultiplier = 1.0f, damage = 25, maxRange = 20, pierceCount = 2, onHitEffect = Some(Burn(10, 3000, 750)), alsoOnHit = Seq(Slow(2000, 0.6f)))
  val FlameBoltLightDef: ProjectileDef = ProjectileDef(id = ProjectileType.FLAME_BOLT_LIGHT, name = "Flame Bolt Light", speedMultiplier = 0.75f, damage = 12, maxRange = 16, onHitEffect = Some(Burn(12, 3000, 750)))
  val SonicWaveMedDef: ProjectileDef = ProjectileDef(id = ProjectileType.SONIC_WAVE_MED, name = "Sonic Wave Med", speedMultiplier = 0.7f, damage = 18, maxRange = 14, passesThroughWalls = true, pierceCount = 1)
  val ArrowLightDef: ProjectileDef = ProjectileDef(id = ProjectileType.ARROW_LIGHT, name = "Arrow Light", speedMultiplier = 0.95f, damage = 12, maxRange = 22, pierceCount = 1)
  // A closer (Plan 5a): lands on a ranged character walking away from 8 cells, wherever they are
  // in their step (MeleeKitsTest). See common/model/CLAUDE.md, "Melee kits"
  val TalonGrabDef: ProjectileDef = ProjectileDef(id = ProjectileType.TALON_GRAB, name = "Talon Grab", speedMultiplier = 0.95f, damage = 12, maxRange = 16, onHitEffect = Some(PullToOwner))

  val projectiles: Seq[ProjectileDef] = Seq(
    HeadThrowDef, StoneGazeDef, InkSnareDef, HornDef, MysticBoltDef,
    PetrifyDef, ThrownBoulderDef, EyeBeamDef, FlameBoltLightDef, SonicWaveMedDef,
    ArrowLightDef, TalonGrabDef
  )

  /** Plan 5a's variants: another type's numbers and look, with an effect of their own. Made from
    * the registered base, so Roster makes them once every base is registered. */
  def variants(base: Byte => ProjectileDef): Seq[ProjectileDef] = Seq(
    base(ProjectileType.CLAW_SWIPE).copy(id = ProjectileType.BLOOD_FRENZY, name = "Blood Frenzy", onHitEffect = Some(LifeSteal(30)), alsoOnHit = Seq(Slow(1500, 0.6f))),
    base(ProjectileType.BOULDER).copy(id = ProjectileType.ROOTING_BOULDER, name = "Rooting Boulder", onHitEffect = Some(Root(1000))),
    // A primary with an identity of its own (Plan 5a): a slow at most, never a hold. Primaries fire
    // twice a second, and a hold grants 1.5s of CC immunity that would turn the kit's own holds away
    base(ProjectileType.CLAW_SWIPE).copy(id = ProjectileType.FENRIR_CLAW, name = "Fenrir Claw", onHitEffect = Some(LifeSteal(25)))
  )

  // ── Characters ──

  val Minotaur: CharacterDef = CharacterDef(
    id = CharacterId.Minotaur, displayName = "Minotaur",
    description = "A bull-headed brute with a devastating long charge and stunning horn toss.",
    spriteSheet = "sprites/minotaur.png",
    qAbility = AbilityDef(name = "Bull Charge", description = "Massive charge forward with horns lowered.", cooldownMs = 12000, maxRange = 0, damage = 0, projectileType = -1, keybind = "Q", castBehavior = DashBuff(14, 200, 25)),
    eAbility = AbilityDef(name = "Horn Toss", description = "Flings a horn that knocks back and stuns for 0.8s.", cooldownMs = 14000, maxRange = 16, damage = 25, projectileType = ProjectileType.HEAD_THROW, keybind = "E"),
    primaryProjectileType = ProjectileType.HORN, role = Melee, maxHealth = 150, moveSpeed = Melee.speed
  )

  val Medusa: CharacterDef = CharacterDef(
    id = CharacterId.Medusa, displayName = "Medusa",
    description = "A gorgon who petrifies with her gaze and strikes with serpent venom.",
    spriteSheet = "sprites/medusa.png",
    qAbility = AbilityDef(name = "Petrify", description = "Petrifying gaze that freezes.", cooldownMs = 5000, maxRange = 20, damage = 5, projectileType = ProjectileType.PETRIFY, keybind = "Q"),
    eAbility = AbilityDef(name = "Stone Gaze", description = "Petrifying gaze roots all nearby enemies.", cooldownMs = 12000, maxRange = 0, damage = 15, projectileType = ProjectileType.STONE_GAZE, keybind = "E", castBehavior = GroundSlam(6.0f)),
    primaryProjectileType = ProjectileType.VENOM_BOLT, role = Ranged, maxHealth = 70
  )

  val Cerberus: CharacterDef = CharacterDef(
    id = CharacterId.Cerberus, displayName = "Cerberus",
    description = "An aggressive three-headed hellhound with burning magma attacks and a short pounce.",
    spriteSheet = "sprites/cerberus.png",
    qAbility = AbilityDef(name = "Triple Blast", description = "Fires 3 flame bolts.", cooldownMs = 7000, maxRange = 16, damage = 18, projectileType = ProjectileType.FLAME_BOLT, keybind = "Q", castBehavior = FanProjectile(3, Math.toRadians(30))),
    eAbility = AbilityDef(name = "Hellfire Pounce", description = "Short pounce followed by fiery landing.", cooldownMs = 10000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = DashBuff(6, 250, 20)),
    primaryProjectileType = ProjectileType.MAGMA_BALL, role = Ranged, maxHealth = 75
  )

  val Centaur: CharacterDef = CharacterDef(
    id = CharacterId.Centaur, displayName = "Centaur",
    description = "A horse-bodied archer with lance charge and bow.",
    spriteSheet = "sprites/centaur.png",
    qAbility = AbilityDef(name = "Lance", description = "Hurls a distance-scaling spear.", cooldownMs = 8000, maxRange = 20, damage = 10, projectileType = ProjectileType.SPEAR, keybind = "Q"),
    eAbility = AbilityDef(name = "Gallop", description = "Gallops forward.", cooldownMs = 10000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = DashBuff(12, 400, 18)),
    primaryProjectileType = ProjectileType.ARROW_LIGHT, role = Ranged, maxHealth = 75
  )

  val Kraken: CharacterDef = CharacterDef(
    id = CharacterId.Kraken, displayName = "Kraken",
    description = "A sea monster with tentacle pulls and tidal pushes.",
    spriteSheet = "sprites/kraken.png",
    qAbility = AbilityDef(name = "Tentacle Storm", description = "Fires 5 tentacles in a fan.", cooldownMs = 10000, maxRange = 15, damage = 5, projectileType = ProjectileType.TENTACLE, keybind = "Q", castBehavior = FanProjectile(5, Math.toRadians(60))),
    eAbility = AbilityDef(name = "Ink Snare", description = "Launches ink that roots enemies on landing.", cooldownMs = 12000, maxRange = 14, damage = 10, projectileType = ProjectileType.INK_SNARE, keybind = "E"),
    primaryProjectileType = ProjectileType.TENTACLE, role = Ranged, maxHealth = 80
  )

  val Sphinx: CharacterDef = CharacterDef(
    id = CharacterId.Sphinx, displayName = "Sphinx",
    description = "A riddle-master whose every attack slows, with charm projectiles and sonic riddles.",
    spriteSheet = "sprites/sphinx.png",
    qAbility = AbilityDef(name = "Mesmerize", description = "Charm that heavily slows for 3s.", cooldownMs = 10000, maxRange = 14, damage = 15, projectileType = ProjectileType.CHARM, keybind = "Q"),
    eAbility = AbilityDef(name = "Riddle Burst", description = "Fires 5 sonic waves that pass through walls.", cooldownMs = 10000, maxRange = 14, damage = 14, projectileType = ProjectileType.SONIC_WAVE, keybind = "E", castBehavior = FanProjectile(5, Math.toRadians(60))),
    primaryProjectileType = ProjectileType.CHARM, role = Ranged, maxHealth = 70
  )

  val Cyclops: CharacterDef = CharacterDef(
    id = CharacterId.Cyclops, displayName = "Cyclops",
    description = "A one-eyed giant who hurls massive boulders and fires a piercing eye beam.",
    spriteSheet = "sprites/cyclops.png",
    qAbility = AbilityDef(name = "Boulder Fan", description = "Hurls 3 boulders in a fan that root for 1s.", cooldownMs = 14000, maxRange = 8, damage = 35, projectileType = ProjectileType.ROOTING_BOULDER, keybind = "Q", castBehavior = FanProjectile(3, Math.toRadians(40))),
    eAbility = AbilityDef(name = "Eye Beam", description = "Fires a searing beam that pierces, burns and slows.", cooldownMs = 16000, maxRange = 20, damage = 25, projectileType = ProjectileType.EYE_BEAM, keybind = "E"),
    primaryProjectileType = ProjectileType.THROWN_BOULDER, role = Skirmisher, maxHealth = 145, moveSpeed = Skirmisher.speed
  )

  val Harpy: CharacterDef = CharacterDef(
    id = CharacterId.Harpy, displayName = "Harpy",
    description = "A screaming terror with sonic attacks, a push shriek, and aerial escape.",
    spriteSheet = "sprites/harpy.png",
    qAbility = AbilityDef(name = "Shriek", description = "Screams, pushing all nearby enemies away.", cooldownMs = 10000, maxRange = 0, damage = 15, projectileType = ProjectileType.TREMOR_SLAM, keybind = "Q", castBehavior = GroundSlam(5.0f)),
    eAbility = AbilityDef(name = "Sky Dance", description = "Takes flight, becoming phased and immune.", cooldownMs = 14000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = PhaseShiftBuff(3000)),
    primaryProjectileType = ProjectileType.SONIC_WAVE_MED, role = Ranged, maxHealth = 65
  )

  val Griffin: CharacterDef = CharacterDef(
    id = CharacterId.Griffin, displayName = "Griffin",
    description = "A majestic beast that dives on prey and snatches it in its talons.",
    spriteSheet = "sprites/griffin.png",
    qAbility = AbilityDef(name = "Sky Dive", description = "Dives from above.", cooldownMs = 10000, maxRange = 0, damage = 0, projectileType = -1, keybind = "Q", castBehavior = DashBuff(12, 150, 18)),
    eAbility = AbilityDef(name = "Talon Grab", description = "Snatches an enemy in its talons and drags them to you.", cooldownMs = 10000, maxRange = 16, damage = 12, projectileType = ProjectileType.TALON_GRAB, keybind = "E"),
    primaryProjectileType = ProjectileType.TALON, role = Melee, maxHealth = 135, moveSpeed = Melee.speed
  )

  val Anubis: CharacterDef = CharacterDef(
    id = CharacterId.Anubis, displayName = "Anubis",
    description = "The jackal god of death who curses and judges with rooting slams.",
    spriteSheet = "sprites/anubis.png",
    qAbility = AbilityDef(name = "Curse of Anubis", description = "Ancient curse that burns over time.", cooldownMs = 12000, maxRange = 12, damage = 20, projectileType = ProjectileType.CURSE, keybind = "Q"),
    eAbility = AbilityDef(name = "Scales of Judgment", description = "Judges all nearby, rooting and burning them.", cooldownMs = 14000, maxRange = 0, damage = 20, projectileType = ProjectileType.SEISMIC_ROOT, keybind = "E", castBehavior = GroundSlam(6.0f)),
    primaryProjectileType = ProjectileType.DEATH_BOLT, role = Ranged, maxHealth = 70
  )

  val Yokai: CharacterDef = CharacterDef(
    id = CharacterId.Yokai, displayName = "Yokai",
    description = "A trickster spirit that turns invisible and fans curses in all directions.",
    spriteSheet = "sprites/yokai.png",
    qAbility = AbilityDef(name = "Illusion", description = "Vanishes into illusion, phased and immune.", cooldownMs = 12000, maxRange = 0, damage = 0, projectileType = -1, keybind = "Q", castBehavior = PhaseShiftBuff(3000)),
    eAbility = AbilityDef(name = "Yokai Curse Fan", description = "Fires 3 curses in a fan.", cooldownMs = 10000, maxRange = 12, damage = 20, projectileType = ProjectileType.CURSE, keybind = "E", castBehavior = FanProjectile(3, Math.toRadians(30))),
    primaryProjectileType = ProjectileType.MYSTIC_BOLT, role = Ranged, maxHealth = 70
  )

  val Golem: CharacterDef = CharacterDef(
    id = CharacterId.Golem, displayName = "Golem",
    description = "An immovable stone fortress that raises walls and roots anyone who comes close.",
    spriteSheet = "sprites/golem.png",
    qAbility = AbilityDef(name = "Stone Wall", description = "Raises a wall of stone that stops enemy shots until you attack.", cooldownMs = 14000, maxRange = 0, damage = 0, projectileType = -3, keybind = "Q", castBehavior = BarrierCast(3500)),
    eAbility = AbilityDef(name = "Tremor Slam", description = "Slams the ground, rooting nearby enemies.", cooldownMs = 20000, maxRange = 0, damage = 30, projectileType = ProjectileType.TREMOR_SLAM, keybind = "E", castBehavior = GroundSlam(6.0f)),
    primaryProjectileType = ProjectileType.BOULDER, role = Skirmisher, maxHealth = 150, moveSpeed = Skirmisher.speed
  )

  val Djinn: CharacterDef = CharacterDef(
    id = CharacterId.Djinn, displayName = "Djinn",
    description = "A wish-granting spirit who mesmerizes foes and teleports via mirage.",
    spriteSheet = "sprites/djinn.png",
    qAbility = AbilityDef(name = "Mesmerize", description = "Hypnotic charm that heavily slows for 3 seconds.", cooldownMs = 10000, maxRange = 14, damage = 15, projectileType = ProjectileType.CHARM, keybind = "Q"),
    eAbility = AbilityDef(name = "Mirage", description = "Teleports through a shimmering mirage.", cooldownMs = 12000, maxRange = 8, damage = 0, projectileType = -2, keybind = "E", castBehavior = TeleportCast(8)),
    primaryProjectileType = ProjectileType.MYSTIC_BOLT, role = Ranged, maxHealth = 70
  )

  val Fenrir: CharacterDef = CharacterDef(
    id = CharacterId.Fenrir, displayName = "Fenrir",
    description = "A frenzy wolf that leaps in and drains life with savage bites.",
    spriteSheet = "sprites/fenrir.png",
    qAbility = AbilityDef(name = "Savage Leap", description = "Leaps at prey with savage force.", cooldownMs = 10000, maxRange = 0, damage = 0, projectileType = -1, keybind = "Q", castBehavior = DashBuff(12, 150, 20)),
    eAbility = AbilityDef(name = "Blood Frenzy", description = "Bites 8 times around self, draining life and slowing.", cooldownMs = 14000, maxRange = 4, damage = 25, projectileType = ProjectileType.BLOOD_FRENZY, keybind = "E", castBehavior = FanProjectile(8, 2 * Math.PI)),
    primaryProjectileType = ProjectileType.FENRIR_CLAW, role = Melee, maxHealth = 135, moveSpeed = Melee.speed
  )

  val Chimera: CharacterDef = CharacterDef(
    id = CharacterId.Chimera, displayName = "Chimera",
    description = "A multi-headed beast with fire, venom fan, and lion charge.",
    spriteSheet = "sprites/chimera.png",
    qAbility = AbilityDef(name = "Venom Fan", description = "Spits 3 venom bolts from the snake heads.", cooldownMs = 7000, maxRange = 16, damage = 14, projectileType = ProjectileType.VENOM_BOLT, keybind = "Q", castBehavior = FanProjectile(3, Math.toRadians(30))),
    eAbility = AbilityDef(name = "Lion Charge", description = "Charges forward with a speed boost on arrival.", cooldownMs = 10000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = DashBuff(10, 350, 22)),
    primaryProjectileType = ProjectileType.FLAME_BOLT_LIGHT, role = Ranged, maxHealth = 75
  )

  val characters: Seq[CharacterDef] = Seq(
    Minotaur, Medusa, Cerberus, Centaur, Kraken, Sphinx,
    Cyclops, Harpy, Griffin, Anubis, Yokai, Golem,
    Djinn, Fenrir, Chimera
  )
}
