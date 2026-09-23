package com.gridgame.common.model

/**
 * The projectile defs Plan 5a gave the melee and skirmisher kits: its slams, its closers (the
 * pulls, stuns and roots fast enough to land on a ranged character walking away), and the
 * variants that give a shared type an effect of its own.
 *
 * Built here rather than as vals in CharacterDef, whose initializer builds every other def and all
 * 112 characters in one JVM method and had reached the 64KB a method may be. CharacterDef
 * registers these straight after its own, so `base` finds every type a variant copies.
 */
private[model] object MeleeKitProjectiles {
  def build(base: Byte => ProjectileDef): Seq[ProjectileDef] = Seq(
    // Slams around the caster. A bot casts one when its target is inside the ability's
    // GroundSlam(radius), so that radius and the AoE radius here are kept in step.
    ProjectileDef(id = ProjectileType.SHOCKWAVE, name = "Shockwave", speedMultiplier = 0f, damage = 0, maxRange = 0, aoeOnMaxRange = Some(AoESplashConfig(5.0f, 20)), onHitEffect = Some(Push(3.0f)), explosionConfig = Some(ExplosionConfig(0, 0, 5.0f))),
    ProjectileDef(id = ProjectileType.ICE_QUAKE, name = "Ice Quake", speedMultiplier = 0f, damage = 0, maxRange = 0, aoeOnMaxRange = Some(AoESplashConfig(6.0f, 25, freezeDurationMs = 1200)), explosionConfig = Some(ExplosionConfig(0, 0, 6.0f))),
    ProjectileDef(id = ProjectileType.HOWL, name = "Howl", speedMultiplier = 0f, damage = 0, maxRange = 0, aoeOnMaxRange = Some(AoESplashConfig(9.0f, 10)), onHitEffect = Some(Slow(2500, 0.6f)), explosionConfig = Some(ExplosionConfig(0, 0, 9.0f))),
    ProjectileDef(id = ProjectileType.FERAL_ROAR, name = "Feral Roar", speedMultiplier = 0f, damage = 0, maxRange = 0, aoeOnMaxRange = Some(AoESplashConfig(5.0f, 15)), onHitEffect = Some(Slow(2000, 0.5f)), explosionConfig = Some(ExplosionConfig(0, 0, 5.0f))),
    // Closers: each lands on a ranged character walking away from 8 cells, wherever they are in
    // their step (MeleeKitsTest). At 0.9 a projectile gains 10 cells a second on one; a 16-cell
    // throw at 0.9 still missed from 8 at 3 phases of the step in 20, so those are 0.95.
    ProjectileDef(id = ProjectileType.EARTHSPLITTER, name = "Earthsplitter", speedMultiplier = 0.9f, damage = 25, maxRange = 14, hitRadius = 2.5f, onHitEffect = Some(Root(1200))),
    ProjectileDef(id = ProjectileType.GRASPING_DEAD, name = "Grasping Dead", speedMultiplier = 0.95f, damage = 15, maxRange = 16, onHitEffect = Some(Root(1000))),
    ProjectileDef(id = ProjectileType.DEATH_GRIP, name = "Death Grip", speedMultiplier = 0.95f, damage = 10, maxRange = 16, onHitEffect = Some(PullToOwner)),
    ProjectileDef(id = ProjectileType.TALON_GRAB, name = "Talon Grab", speedMultiplier = 0.95f, damage = 12, maxRange = 16, onHitEffect = Some(PullToOwner)),
    ProjectileDef(id = ProjectileType.PARALYTIC_STING, name = "Paralytic Sting", speedMultiplier = 0.95f, damage = 12, maxRange = 16, onHitEffect = Some(Stun(700))),
    ProjectileDef(id = ProjectileType.HAMMER_THROW, name = "Hammer Throw", speedMultiplier = 0.95f, damage = 30, maxRange = 16, hitRadius = 2.2f, onHitEffect = Some(Stun(800))),
    ProjectileDef(id = ProjectileType.MEAT_HOOK, name = "Meat Hook", speedMultiplier = 0.9f, damage = 10, maxRange = 18, onHitEffect = Some(PullToOwner)),
    // A splash rather than an explosion: an explosion deals its blast and nothing else, so its
    // on-hit burn would never land
    ProjectileDef(id = ProjectileType.FLAMBE, name = "Flambe", speedMultiplier = 0.7f, damage = 20, maxRange = 6, aoeOnHit = Some(AoESplashConfig(2.5f, 15)), aoeOnMaxRange = Some(AoESplashConfig(2.5f, 15)), onHitEffect = Some(Burn(20, 3000, 750))),
    // Variants: the base type's numbers and look, with effects of their own
    base(ProjectileType.HOLY_BOLT).copy(id = ProjectileType.HOLY_NOVA, name = "Holy Nova", speedMultiplier = 0.95f, onHitEffect = Some(Stun(500))),
    base(ProjectileType.AXE).copy(id = ProjectileType.AXE_SPIN, name = "Axe Spin", onHitEffect = Some(Slow(2000, 0.5f))),
    base(ProjectileType.POISON_DART).copy(id = ProjectileType.VENOM_DART, name = "Venom Dart", alsoOnHit = Seq(Poison(15, 3000, 750))),
    base(ProjectileType.SHURIKEN).copy(id = ProjectileType.TOXIC_SHURIKEN, name = "Toxic Shuriken", onHitEffect = Some(Poison(10, 2000, 500)), alsoOnHit = Seq(Slow(1500, 0.6f))),
    base(ProjectileType.CLAW_SWIPE).copy(id = ProjectileType.BLOOD_FRENZY, name = "Blood Frenzy", onHitEffect = Some(LifeSteal(30)), alsoOnHit = Seq(Slow(1500, 0.6f))),
    base(ProjectileType.FIST).copy(id = ProjectileType.FLURRY, name = "Flurry", onHitEffect = Some(Stun(400))),
    base(ProjectileType.BOULDER).copy(id = ProjectileType.ROOTING_BOULDER, name = "Rooting Boulder", onHitEffect = Some(Root(1000))),
    base(ProjectileType.EMBER_SHOT).copy(id = ProjectileType.EMBER_FAN, name = "Ember Fan", alsoOnHit = Seq(Slow(1000, 0.7f))),
    // Primaries with an identity of their own. A slow at most, never a hold: primaries fire twice
    // a second, and a hold grants 1.5s of CC immunity that would turn the kit's own holds away.
    base(ProjectileType.CLAW_SWIPE).copy(id = ProjectileType.FENRIR_CLAW, name = "Fenrir Claw", onHitEffect = Some(LifeSteal(25))),
    base(ProjectileType.CLAW_SWIPE).copy(id = ProjectileType.GHOUL_CLAW, name = "Ghoul Claw", onHitEffect = Some(Slow(1000, 0.7f))),
    base(ProjectileType.CLAW_SWIPE).copy(id = ProjectileType.SHARK_CLAW, name = "Shark Claw", onHitEffect = Some(Poison(9, 1500, 500))),
    base(ProjectileType.BOULDER).copy(id = ProjectileType.ICE_BOULDER, name = "Ice Boulder", onHitEffect = Some(Slow(1000, 0.7f))),
    base(ProjectileType.CURSED_BLADE).copy(id = ProjectileType.DRAIN_BLADE, name = "Drain Blade", onHitEffect = Some(LifeSteal(20))),
    base(ProjectileType.CURSED_BLADE).copy(id = ProjectileType.CHILL_BLADE, name = "Chill Blade", onHitEffect = Some(Slow(1000, 0.7f)))
  )
}
