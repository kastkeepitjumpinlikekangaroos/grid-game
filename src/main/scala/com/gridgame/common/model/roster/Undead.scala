package com.gridgame.common.model.roster

import com.gridgame.common.model._
import com.gridgame.common.model.CombatRole.{Melee, Ranged, Skirmisher}

/**
 * The undead and the dark (character ids 27-41).
 *
 * First the definitions of the projectiles a character here is the first in the roster to throw,
 * then the characters. CharacterDef's initializer registers every definition (Roster); how a
 * projectile type looks is GLProjectileRenderers'.
 */
private[model] object Undead {
  // ── Projectiles ──

  val DeathBoltDef: ProjectileDef = ProjectileDef(id = ProjectileType.DEATH_BOLT, name = "Death Bolt", speedMultiplier = 0.7f, damage = 14, maxRange = 16, passesThroughWalls = true, pierceCount = 1)
  val RaiseDeadDef: ProjectileDef = ProjectileDef(id = ProjectileType.RAISE_DEAD, name = "Raise Dead", speedMultiplier = 0.5f, damage = 20, maxRange = 14, aoeOnHit = Some(AoESplashConfig(3.0f, 12)))
  val BoneAxeDef: ProjectileDef = ProjectileDef(id = ProjectileType.BONE_AXE, name = "Bone Axe", speedMultiplier = 0.6f, damage = 26, maxRange = 5, hitRadius = 2.5f)
  val BoneThrowDef: ProjectileDef = ProjectileDef(id = ProjectileType.BONE_THROW, name = "Bone Throw", speedMultiplier = 0.8f, damage = 20, maxRange = 16, boomerang = true)
  val SoulDrainDef: ProjectileDef = ProjectileDef(id = ProjectileType.SOUL_DRAIN, name = "Soul Drain", speedMultiplier = 0.6f, damage = 25, maxRange = 14, onHitEffect = Some(LifeSteal(40)))
  val DevourDef: ProjectileDef = ProjectileDef(id = ProjectileType.DEVOUR, name = "Devour", speedMultiplier = 0.5f, damage = 30, maxRange = 6, onHitEffect = Some(LifeSteal(50)), alsoOnHit = Seq(Root(1000)))
  val ScytheDef: ProjectileDef = ProjectileDef(id = ProjectileType.SCYTHE, name = "Scythe", speedMultiplier = 0.65f, damage = 28, maxRange = 5, hitRadius = 2.5f)
  val ReapDef: ProjectileDef = ProjectileDef(id = ProjectileType.REAP, name = "Reap", speedMultiplier = 0.9f, damage = 35, maxRange = 10, passesThroughWalls = true, hitRadius = 2.8f, onHitEffect = Some(PullToOwner))
  val ShadowBoltDef: ProjectileDef = ProjectileDef(id = ProjectileType.SHADOW_BOLT, name = "Shadow Bolt", speedMultiplier = 0.8f, damage = 21, maxRange = 16, passesThroughWalls = true, ricochetCount = 2)
  val CursedBladeDef: ProjectileDef = ProjectileDef(id = ProjectileType.CURSED_BLADE, name = "Cursed Blade", speedMultiplier = 0.7f, damage = 22, maxRange = 5, hitRadius = 2.2f)
  val LifeDrainDef: ProjectileDef = ProjectileDef(id = ProjectileType.LIFE_DRAIN, name = "Life Drain", speedMultiplier = 0.6f, damage = 20, maxRange = 14, onHitEffect = Some(LifeSteal(35)), alsoOnHit = Seq(Slow(2000, 0.6f)))
  val ShovelDef: ProjectileDef = ProjectileDef(id = ProjectileType.SHOVEL, name = "Shovel", speedMultiplier = 0.6f, damage = 28, maxRange = 4, hitRadius = 2.2f, boomerang = true)
  val BandageWhipDef: ProjectileDef = ProjectileDef(id = ProjectileType.BANDAGE_WHIP, name = "Bandage Whip", speedMultiplier = 0.75f, damage = 10, maxRange = 16, onHitEffect = Some(PullToOwner))
  val CurseDef: ProjectileDef = ProjectileDef(id = ProjectileType.CURSE, name = "Curse", speedMultiplier = 0.5f, damage = 20, maxRange = 12, onHitEffect = Some(Burn(15, 3000, 750)))
  val CharmDef: ProjectileDef = ProjectileDef(id = ProjectileType.CHARM, name = "Charm", speedMultiplier = 0.6f, damage = 15, maxRange = 14, onHitEffect = Some(Slow(3000, 0.3f)))
  val TremorSlamDef: ProjectileDef = ProjectileDef(id = ProjectileType.TREMOR_SLAM, name = "Tremor Slam", speedMultiplier = 0f, damage = 0, maxRange = 0, aoeOnMaxRange = Some(AoESplashConfig(6.0f, 30, rootDurationMs = 1500)), explosionConfig = Some(ExplosionConfig(0, 0, 6.0f)))
  val BoomerangBladeDef: ProjectileDef = ProjectileDef(id = ProjectileType.BOOMERANG_BLADE, name = "Boomerang Blade", speedMultiplier = 0.7f, damage = 28, maxRange = 12, hitRadius = 2.2f, boomerang = true, pierceCount = 1, onHitEffect = Some(Slow(1500, 0.6f)))
  val LeechBoltDef: ProjectileDef = ProjectileDef(id = ProjectileType.LEECH_BOLT, name = "Leech Bolt", speedMultiplier = 0.7f, damage = 16, maxRange = 16, pierceCount = 1, onHitEffect = Some(LifeSteal(30)))
  val BoneBoomerangDef: ProjectileDef = ProjectileDef(id = ProjectileType.BONE_BOOMERANG, name = "Bone Boomerang", speedMultiplier = 0.95f, damage = 25, maxRange = 16, boomerang = true, aoeOnHit = Some(AoESplashConfig(2.5f, 10)), onHitEffect = Some(Slow(2000, 0.6f)))
  val ShadowHauntDef: ProjectileDef = ProjectileDef(id = ProjectileType.SHADOW_HAUNT, name = "Shadow Haunt", speedMultiplier = 0.55f, damage = 25, maxRange = 14, passesThroughWalls = true, onHitEffect = Some(TeleportOwnerBehind(2, 1500)))
  val EchoBoltDef: ProjectileDef = ProjectileDef(id = ProjectileType.ECHO_BOLT, name = "Echo Bolt", speedMultiplier = 0.6f, damage = 16, maxRange = 18, ricochetCount = 2, aoeOnHit = Some(AoESplashConfig(2.5f, 8)))
  val SoulHarvestDef: ProjectileDef = ProjectileDef(id = ProjectileType.SOUL_HARVEST, name = "Soul Harvest", speedMultiplier = 0f, damage = 0, maxRange = 0, aoeOnMaxRange = Some(AoESplashConfig(6.0f, 25)), onHitEffect = Some(LifeSteal(30)), explosionConfig = Some(ExplosionConfig(0, 0, 6.0f)))
  val SoulBoltHeavyDef: ProjectileDef = ProjectileDef(id = ProjectileType.SOUL_BOLT_HEAVY, name = "Soul Bolt Heavy", speedMultiplier = 0.65f, damage = 22, maxRange = 15, passesThroughWalls = true, pierceCount = 1)
  val SonicWaveHeavyDef: ProjectileDef = ProjectileDef(id = ProjectileType.SONIC_WAVE_HEAVY, name = "Sonic Wave Heavy", speedMultiplier = 0.7f, damage = 22, maxRange = 14, passesThroughWalls = true, pierceCount = 1)
  // A closer (Plan 5a): lands on a ranged character walking away from 8 cells, wherever they are
  // in their step (MeleeKitsTest). See common/model/CLAUDE.md, "Melee kits"
  val GraspingDeadDef: ProjectileDef = ProjectileDef(id = ProjectileType.GRASPING_DEAD, name = "Grasping Dead", speedMultiplier = 0.95f, damage = 15, maxRange = 16, onHitEffect = Some(Root(1000)))
  val DeathGripDef: ProjectileDef = ProjectileDef(id = ProjectileType.DEATH_GRIP, name = "Death Grip", speedMultiplier = 0.95f, damage = 10, maxRange = 16, onHitEffect = Some(PullToOwner))

  val projectiles: Seq[ProjectileDef] = Seq(
    DeathBoltDef, RaiseDeadDef, BoneAxeDef, BoneThrowDef, SoulDrainDef,
    DevourDef, ScytheDef, ReapDef, ShadowBoltDef, CursedBladeDef,
    LifeDrainDef, ShovelDef, BandageWhipDef, CurseDef, CharmDef,
    TremorSlamDef, BoomerangBladeDef, LeechBoltDef, BoneBoomerangDef, ShadowHauntDef,
    EchoBoltDef, SoulHarvestDef, SoulBoltHeavyDef, SonicWaveHeavyDef, GraspingDeadDef,
    DeathGripDef
  )

  /** Plan 5a's variants: another type's numbers and look, with an effect of their own. Made from
    * the registered base, so Roster makes them once every base is registered. */
  def variants(base: Byte => ProjectileDef): Seq[ProjectileDef] = Seq(
    // A primary with an identity of its own (Plan 5a): a slow at most, never a hold. Primaries fire
    // twice a second, and a hold grants 1.5s of CC immunity that would turn the kit's own holds away
    base(ProjectileType.CLAW_SWIPE).copy(id = ProjectileType.GHOUL_CLAW, name = "Ghoul Claw", onHitEffect = Some(Slow(1000, 0.7f))),
    base(ProjectileType.CURSED_BLADE).copy(id = ProjectileType.DRAIN_BLADE, name = "Drain Blade", onHitEffect = Some(LifeSteal(20))),
    base(ProjectileType.CURSED_BLADE).copy(id = ProjectileType.CHILL_BLADE, name = "Chill Blade", onHitEffect = Some(Slow(1000, 0.7f)))
  )

  // ── Characters ──

  val Necromancer: CharacterDef = CharacterDef(
    id = CharacterId.Necromancer, displayName = "Necromancer",
    description = "A dark sorcerer who commands death, raises the dead, and harvests souls.",
    spriteSheet = "sprites/necromancer.png",
    qAbility = AbilityDef(name = "Raise Dead", description = "Summons a burst of necrotic energy.", cooldownMs = 8000, maxRange = 14, damage = 20, projectileType = ProjectileType.RAISE_DEAD, keybind = "Q"),
    eAbility = AbilityDef(name = "Soul Harvest", description = "Harvests souls in a wide AoE, healing from damage dealt.", cooldownMs = 14000, maxRange = 0, damage = 25, projectileType = ProjectileType.SOUL_HARVEST, keybind = "E", castBehavior = GroundSlam(6.0f)),
    primaryProjectileType = ProjectileType.DEATH_BOLT, role = Ranged, maxHealth = 70
  )

  val SkeletonKing: CharacterDef = CharacterDef(
    id = CharacterId.SkeletonKing, displayName = "Skeleton King",
    description = "An undead monarch wielding bone axes and boomerang bones with AoE splash.",
    spriteSheet = "sprites/skeletonking.png",
    qAbility = AbilityDef(name = "Bone Boomerang", description = "Hurls a bone that returns, splashing and slowing on hit.", cooldownMs = 10000, maxRange = 16, damage = 25, projectileType = ProjectileType.BONE_BOOMERANG, keybind = "Q"),
    eAbility = AbilityDef(name = "Bone Storm", description = "Unleashes bones in all directions.", cooldownMs = 12000, maxRange = 16, damage = 20, projectileType = ProjectileType.BONE_THROW, keybind = "E", castBehavior = FanProjectile(8, 2 * Math.PI)),
    primaryProjectileType = ProjectileType.BONE_AXE, role = Melee, maxHealth = 135, moveSpeed = Melee.speed
  )

  val Banshee: CharacterDef = CharacterDef(
    id = CharacterId.Banshee, displayName = "Banshee",
    description = "A sonic disruptor whose echoing bolts ricochet and wail freezes all nearby.",
    spriteSheet = "sprites/banshee.png",
    qAbility = AbilityDef(name = "Echo Bolt", description = "A ricocheting sonic bolt that splashes AoE on each bounce.", cooldownMs = 6000, maxRange = 18, damage = 16, projectileType = ProjectileType.ECHO_BOLT, keybind = "Q"),
    eAbility = AbilityDef(name = "Banshee Wail", description = "Screams, freezing all nearby enemies.", cooldownMs = 14000, maxRange = 0, damage = 15, projectileType = ProjectileType.TREMOR_SLAM, keybind = "E", castBehavior = GroundSlam(5.0f)),
    primaryProjectileType = ProjectileType.SONIC_WAVE_HEAVY, role = Ranged, maxHealth = 65
  )

  val Lich: CharacterDef = CharacterDef(
    id = CharacterId.Lich, displayName = "Lich",
    description = "A dark sustain mage with life-leeching bolts, frost shards, and soul drain.",
    spriteSheet = "sprites/lich.png",
    qAbility = AbilityDef(name = "Frost Shard", description = "Fires a freezing ice shard.", cooldownMs = 5000, maxRange = 18, damage = 16, projectileType = ProjectileType.FROST_SHARD, keybind = "Q"),
    eAbility = AbilityDef(name = "Soul Drain", description = "Drains the soul, healing for 40% of damage.", cooldownMs = 10000, maxRange = 14, damage = 25, projectileType = ProjectileType.SOUL_DRAIN, keybind = "E"),
    primaryProjectileType = ProjectileType.LEECH_BOLT, role = Ranged, maxHealth = 70
  )

  val Ghoul: CharacterDef = CharacterDef(
    id = CharacterId.Ghoul, displayName = "Ghoul",
    description = "A feral undead that lunges at prey and devours them for health.",
    spriteSheet = "sprites/ghoul.png",
    qAbility = AbilityDef(name = "Lunge", description = "Dashes at the target.", cooldownMs = 12000, maxRange = 0, damage = 0, projectileType = -1, keybind = "Q", castBehavior = DashBuff(10, 150, 20)),
    eAbility = AbilityDef(name = "Devour", description = "Bites an enemy, healing for 50% of damage and rooting them for 1s.", cooldownMs = 10000, maxRange = 6, damage = 30, projectileType = ProjectileType.DEVOUR, keybind = "E"),
    primaryProjectileType = ProjectileType.GHOUL_CLAW, role = Melee, maxHealth = 135, moveSpeed = Melee.speed
  )

  val Reaper: CharacterDef = CharacterDef(
    id = CharacterId.Reaper, displayName = "Reaper",
    description = "Death incarnate, wielding a scythe and dashing through souls.",
    spriteSheet = "sprites/reaper.png",
    qAbility = AbilityDef(name = "Reap", description = "A scythe swing that passes through walls and drags its target to you.", cooldownMs = 10000, maxRange = 10, damage = 35, projectileType = ProjectileType.REAP, keybind = "Q"),
    eAbility = AbilityDef(name = "Death Dash", description = "Dashes through enemies as death itself.", cooldownMs = 12000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = DashBuff(10, 150, 18)),
    primaryProjectileType = ProjectileType.SCYTHE, role = Melee, maxHealth = 120, moveSpeed = Melee.speed
  )

  val Shade: CharacterDef = CharacterDef(
    id = CharacterId.Shade, displayName = "Shade",
    description = "A ricochet specialist whose shadow bolts bounce off walls and can shadow step.",
    spriteSheet = "sprites/shade.png",
    qAbility = AbilityDef(name = "Shadow Ricochet Fan", description = "Fires 3 ricocheting shadow bolts.", cooldownMs = 10000, maxRange = 16, damage = 15, projectileType = ProjectileType.SHADOW_BOLT, keybind = "Q", castBehavior = FanProjectile(3, Math.toRadians(30))),
    eAbility = AbilityDef(name = "Shadow Step", description = "Teleports through the shadows.", cooldownMs = 12000, maxRange = 8, damage = 0, projectileType = -2, keybind = "E", castBehavior = TeleportCast(8)),
    primaryProjectileType = ProjectileType.SHADOW_BOLT, role = Ranged, maxHealth = 65
  )

  val Revenant: CharacterDef = CharacterDef(
    id = CharacterId.Revenant, displayName = "Revenant",
    description = "An undying warrior who dashes and drains life from foes.",
    spriteSheet = "sprites/revenant.png",
    qAbility = AbilityDef(name = "Spectral Charge", description = "Dashes forward through enemies.", cooldownMs = 10000, maxRange = 0, damage = 0, projectileType = -1, keybind = "Q", castBehavior = DashBuff(10, 150, 20)),
    eAbility = AbilityDef(name = "Life Drain", description = "Drains life, healing for 35% of damage and slowing the target.", cooldownMs = 10000, maxRange = 14, damage = 20, projectileType = ProjectileType.LIFE_DRAIN, keybind = "E"),
    primaryProjectileType = ProjectileType.DRAIN_BLADE, role = Melee, maxHealth = 135, moveSpeed = Melee.speed
  )

  val Gravedigger: CharacterDef = CharacterDef(
    id = CharacterId.Gravedigger, displayName = "Gravedigger",
    description = "A graveyard keeper whose dead drag at the living, and who digs graves for them to fall into.",
    spriteSheet = "sprites/gravedigger.png",
    qAbility = AbilityDef(name = "Grasping Dead", description = "The dead claw up out of the ground, rooting the first enemy they reach for 1s.", cooldownMs = 10000, maxRange = 16, damage = 15, projectileType = ProjectileType.GRASPING_DEAD, keybind = "Q"),
    eAbility = AbilityDef(name = "Open Grave", description = "Digs a grave that is ready in a moment and stuns the first enemy to fall in for 2s.", cooldownMs = 12000, maxRange = 6, damage = 10, projectileType = -4, keybind = "E", castBehavior = TrapCast(TrapType.BEAR_TRAP, 6)),
    primaryProjectileType = ProjectileType.SHOVEL, role = Melee, maxHealth = 130, moveSpeed = Melee.speed
  )

  val Dullahan: CharacterDef = CharacterDef(
    id = CharacterId.Dullahan, displayName = "Dullahan",
    description = "A headless horseman with a boomerang blade and spectral charge.",
    spriteSheet = "sprites/dullahan.png",
    qAbility = AbilityDef(name = "Boomerang Blade", description = "Hurls a blade that returns, piercing through enemies and slowing them.", cooldownMs = 8000, maxRange = 12, damage = 28, projectileType = ProjectileType.BOOMERANG_BLADE, keybind = "Q"),
    eAbility = AbilityDef(name = "Headless Charge", description = "Charges forward on spectral steed.", cooldownMs = 12000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = DashBuff(12, 150, 20)),
    primaryProjectileType = ProjectileType.CURSED_BLADE, role = Melee, maxHealth = 135, moveSpeed = Melee.speed
  )

  val Phantom: CharacterDef = CharacterDef(
    id = CharacterId.Phantom, displayName = "Phantom",
    description = "An assassin ghost that teleports behind targets and blinks away.",
    spriteSheet = "sprites/phantom.png",
    qAbility = AbilityDef(name = "Shadow Haunt", description = "Ghost bolt that teleports you behind the target on hit.", cooldownMs = 10000, maxRange = 14, damage = 25, projectileType = ProjectileType.SHADOW_HAUNT, keybind = "Q"),
    eAbility = AbilityDef(name = "Phase Out", description = "Teleports through the spirit realm.", cooldownMs = 12000, maxRange = 10, damage = 0, projectileType = -2, keybind = "E", castBehavior = TeleportCast(10)),
    primaryProjectileType = ProjectileType.SOUL_BOLT_HEAVY, role = Ranged, maxHealth = 65
  )

  val Mummy: CharacterDef = CharacterDef(
    id = CharacterId.Mummy, displayName = "Mummy",
    description = "An ancient wrapped horror that pulls with bandages and curses foes.",
    spriteSheet = "sprites/mummy.png",
    qAbility = AbilityDef(name = "Bandage Whip", description = "Wraps bandages around an enemy and pulls them.", cooldownMs = 8000, maxRange = 16, damage = 10, projectileType = ProjectileType.BANDAGE_WHIP, keybind = "Q"),
    eAbility = AbilityDef(name = "Curse", description = "Ancient curse that burns over time.", cooldownMs = 12000, maxRange = 12, damage = 20, projectileType = ProjectileType.CURSE, keybind = "E"),
    primaryProjectileType = ProjectileType.SAND_SHOT, role = Ranged, maxHealth = 70
  )

  val Deathknight: CharacterDef = CharacterDef(
    id = CharacterId.Deathknight, displayName = "Death Knight",
    description = "An armored undead knight with a chilling cursed blade, a death grip and an unholy charge.",
    spriteSheet = "sprites/deathknight.png",
    qAbility = AbilityDef(name = "Death Grip", description = "Reaches out with death itself, dragging an enemy to you.", cooldownMs = 10000, maxRange = 16, damage = 10, projectileType = ProjectileType.DEATH_GRIP, keybind = "Q"),
    eAbility = AbilityDef(name = "Unholy Charge", description = "Charges forward with dark energy.", cooldownMs = 12000, maxRange = 0, damage = 0, projectileType = -1, keybind = "E", castBehavior = DashBuff(10, 150, 22)),
    primaryProjectileType = ProjectileType.CHILL_BLADE, role = Melee, maxHealth = 145, moveSpeed = Melee.speed
  )

  val Shadowfiend: CharacterDef = CharacterDef(
    id = CharacterId.Shadowfiend, displayName = "Shadowfiend",
    description = "A dark AoE demon with shadow burst slam and void pull.",
    spriteSheet = "sprites/shadowfiend.png",
    qAbility = AbilityDef(name = "Shadow Burst", description = "Slams shadow energy into the ground, burning nearby enemies.", cooldownMs = 10000, maxRange = 0, damage = 20, projectileType = ProjectileType.TREMOR_SLAM, keybind = "Q", castBehavior = GroundSlam(5.0f)),
    eAbility = AbilityDef(name = "Void Pull", description = "Creates a vortex that pulls enemies in.", cooldownMs = 14000, maxRange = 14, damage = 20, projectileType = ProjectileType.VORTEX_BOMB, keybind = "E"),
    primaryProjectileType = ProjectileType.DEATH_BOLT, role = Ranged, maxHealth = 70
  )

  val Poltergeist: CharacterDef = CharacterDef(
    id = CharacterId.Poltergeist, displayName = "Poltergeist",
    description = "A mischievous trickster that charms foes and blinks unpredictably.",
    spriteSheet = "sprites/poltergeist.png",
    qAbility = AbilityDef(name = "Possession", description = "Charm that heavily slows.", cooldownMs = 6000, maxRange = 14, damage = 15, projectileType = ProjectileType.CHARM, keybind = "Q"),
    eAbility = AbilityDef(name = "Blink", description = "Teleports to cursor.", cooldownMs = 10000, maxRange = 10, damage = 0, projectileType = -2, keybind = "E", castBehavior = TeleportCast(10)),
    primaryProjectileType = ProjectileType.SOUL_BOLT_HEAVY, role = Ranged, maxHealth = 60
  )

  val characters: Seq[CharacterDef] = Seq(
    Necromancer, SkeletonKing, Banshee, Lich, Ghoul, Reaper,
    Shade, Revenant, Gravedigger, Dullahan, Phantom, Mummy,
    Deathknight, Shadowfiend, Poltergeist
  )
}
