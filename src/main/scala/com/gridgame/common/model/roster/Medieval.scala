package com.gridgame.common.model.roster

import com.gridgame.common.model._
import com.gridgame.common.model.CombatRole.{Melee, Ranged, Skirmisher}

/**
 * Knights, rogues and spellcasters of the medieval fantasy (character ids 42-56).
 *
 * First the definitions of the projectiles a character here is the first in the roster to throw,
 * then the characters. CharacterDef's initializer registers every definition (Roster); how a
 * projectile type looks is GLProjectileRenderers'.
 */
private[model] object Medieval {
  // ── Projectiles ──

  val SpearDef: ProjectileDef = ProjectileDef(
    id = ProjectileType.SPEAR, name = "Spear",
    speedMultiplier = 0.7f, damage = 10, maxRange = 20,
    distanceDamageScaling = Some(DistanceDamageScaling(10, 60, 20))
  )
  val HolyBladeDef: ProjectileDef = ProjectileDef(id = ProjectileType.HOLY_BLADE, name = "Holy Blade", speedMultiplier = 0.7f, damage = 22, maxRange = 5, hitRadius = 2.2f)
  val HolyBoltDef: ProjectileDef = ProjectileDef(id = ProjectileType.HOLY_BOLT, name = "Holy Bolt", speedMultiplier = 0.8f, damage = 20, maxRange = 16, pierceCount = 1)
  val ArrowDef: ProjectileDef = ProjectileDef(id = ProjectileType.ARROW, name = "Arrow", speedMultiplier = 0.95f, damage = 18, maxRange = 22, pierceCount = 1)
  val PoisonArrowDef: ProjectileDef = ProjectileDef(id = ProjectileType.POISON_ARROW, name = "Poison Arrow", speedMultiplier = 0.85f, damage = 12, maxRange = 20, onHitEffect = Some(Slow(2500, 0.5f)))
  val SonicWaveDef: ProjectileDef = ProjectileDef(id = ProjectileType.SONIC_WAVE, name = "Sonic Wave", speedMultiplier = 0.7f, damage = 14, maxRange = 14, passesThroughWalls = true, pierceCount = 1)
  val SonicBoomDef: ProjectileDef = ProjectileDef(id = ProjectileType.SONIC_BOOM, name = "Sonic Boom", speedMultiplier = 0.5f, damage = 10, maxRange = 8, onHitEffect = Some(Push(4.0f)), aoeOnHit = Some(AoESplashConfig(3.0f, 8)))
  val SmiteDef: ProjectileDef = ProjectileDef(id = ProjectileType.SMITE, name = "Smite", speedMultiplier = 0.6f, damage = 30, maxRange = 14, aoeOnHit = Some(AoESplashConfig(3.0f, 15)))
  val CardDef: ProjectileDef = ProjectileDef(id = ProjectileType.CARD, name = "Card", speedMultiplier = 0.85f, damage = 19, maxRange = 14, ricochetCount = 2)
  val ChargeFistDef: ProjectileDef = ProjectileDef(id = ProjectileType.CHARGE_FIST, name = "Charge Fist", speedMultiplier = 0.8f, damage = 20, maxRange = 6, hitRadius = 2.2f, chargeSpeedScaling = Some(ChargeScaling(0.6f, 1.1f)), chargeDamageScaling = Some(ChargeScaling(12f, 40f)), chargeRangeScaling = Some(ChargeScaling(4f, 10f)))
  val ArrowHeavyDef: ProjectileDef = ProjectileDef(id = ProjectileType.ARROW_HEAVY, name = "Arrow Heavy", speedMultiplier = 0.95f, damage = 22, maxRange = 22, pierceCount = 1)
  val HolyBoltHeavyDef: ProjectileDef = ProjectileDef(id = ProjectileType.HOLY_BOLT_HEAVY, name = "Holy Bolt Heavy", speedMultiplier = 0.8f, damage = 24, maxRange = 16, pierceCount = 1)
  // A slam (Plan 5a): a bot casts it when its target is inside the ability's GroundSlam radius,
  // so that radius and the AoE radius here are kept in step
  val ShockwaveDef: ProjectileDef = ProjectileDef(id = ProjectileType.SHOCKWAVE, name = "Shockwave", speedMultiplier = 0f, damage = 0, maxRange = 0, aoeOnMaxRange = Some(AoESplashConfig(5.0f, 20)), onHitEffect = Some(Push(3.0f)), explosionConfig = Some(ExplosionConfig(0, 0, 5.0f)))
  // A closer (Plan 5a): lands on a ranged character walking away from 8 cells, wherever they are
  // in their step (MeleeKitsTest). See common/model/CLAUDE.md, "Melee kits"
  val EarthsplitterDef: ProjectileDef = ProjectileDef(id = ProjectileType.EARTHSPLITTER, name = "Earthsplitter", speedMultiplier = 0.9f, damage = 25, maxRange = 14, hitRadius = 2.5f, onHitEffect = Some(Root(1200)))

  val projectiles: Seq[ProjectileDef] = Seq(
    SpearDef, HolyBladeDef, HolyBoltDef, ArrowDef, PoisonArrowDef,
    SonicWaveDef, SonicBoomDef, SmiteDef, CardDef, ChargeFistDef,
    ArrowHeavyDef, HolyBoltHeavyDef, ShockwaveDef, EarthsplitterDef
  )

  /** Plan 5a's variants: another type's numbers and look, with an effect of their own. Made from
    * the registered base, so Roster makes them once every base is registered. */
  def variants(base: Byte => ProjectileDef): Seq[ProjectileDef] = Seq(
    base(ProjectileType.HOLY_BOLT).copy(id = ProjectileType.HOLY_NOVA, name = "Holy Nova", speedMultiplier = 0.95f, onHitEffect = Some(Stun(500))),
    base(ProjectileType.AXE).copy(id = ProjectileType.AXE_SPIN, name = "Axe Spin", onHitEffect = Some(Slow(2000, 0.5f))),
    base(ProjectileType.SHURIKEN).copy(id = ProjectileType.TOXIC_SHURIKEN, name = "Toxic Shuriken", onHitEffect = Some(Poison(10, 2000, 500)), alsoOnHit = Seq(Slow(1500, 0.6f))),
    base(ProjectileType.FIST).copy(id = ProjectileType.FLURRY, name = "Flurry", onHitEffect = Some(Stun(400)))
  )

  // ── Characters ──

  val Paladin: CharacterDef = CharacterDef(
    id = CharacterId.Paladin, displayName = "Paladin",
    description = "A holy knight who raises an aegis against enemy fire and stuns everyone near with a holy nova.",
    spriteSheet = "sprites/paladin.png",
    qAbility = AbilityDef(name = "Aegis", description = "Raises a shield of holy light that stops enemy shots until you attack.", cooldownMs = 12000, maxRange = 0, damage = 0, projectileType = -3, keybind = "Q", castBehavior = BarrierCast(3000)),
    eAbility = AbilityDef(name = "Holy Nova", description = "Unleashes holy bolts in all directions that stun for 0.5s.", cooldownMs = 16000, maxRange = 16, damage = 20, projectileType = ProjectileType.HOLY_NOVA, keybind = "E", castBehavior = FanProjectile(8, 2 * Math.PI)),
    primaryProjectileType = ProjectileType.HOLY_BLADE, role = Melee, maxHealth = 145, moveSpeed = Melee.speed
  )

  val Ranger: CharacterDef = CharacterDef(
    id = CharacterId.Ranger, displayName = "Ranger",
    description = "A skilled archer with poison arrows and multi-shot.",
    spriteSheet = "sprites/ranger.png",
    qAbility = AbilityDef(name = "Poison Arrow", description = "Arrow that slows on hit.", cooldownMs = 7000, maxRange = 20, damage = 12, projectileType = ProjectileType.POISON_ARROW, keybind = "Q"),
    eAbility = AbilityDef(name = "Multi Shot", description = "Fires 3 arrows in a tight fan.", cooldownMs = 6000, maxRange = 22, damage = 18, projectileType = ProjectileType.ARROW, keybind = "E", castBehavior = FanProjectile(3, Math.toRadians(20))),
    primaryProjectileType = ProjectileType.ARROW_HEAVY, role = Ranged, maxHealth = 65
  )

  val Berserker: CharacterDef = CharacterDef(
    id = CharacterId.Berserker, displayName = "Berserker",
    description = "A raging warrior who charges and spins axes.",
    spriteSheet = "sprites/berserker.png",
    qAbility = AbilityDef(name = "Rage Charge", description = "Charges forward in a rage.", cooldownMs = 10000, maxRange = 0, damage = 0, projectileType = -1, keybind = "Q", castBehavior = DashBuff(10, 150, 18)),
    eAbility = AbilityDef(name = "Axe Spin", description = "Spins axes in all directions that slow whoever they hit.", cooldownMs = 16000, maxRange = 5, damage = 33, projectileType = ProjectileType.AXE_SPIN, keybind = "E", castBehavior = FanProjectile(8, 2 * Math.PI)),
    primaryProjectileType = ProjectileType.AXE, role = Melee, maxHealth = 145, moveSpeed = Melee.speed
  )

  val Crusader: CharacterDef = CharacterDef(
    id = CharacterId.Crusader, displayName = "Crusader",
    description = "A melee-focused shield warrior who walls off enemy fire and bashes enemies away.",
    spriteSheet = "sprites/crusader.png",
    qAbility = AbilityDef(name = "Bulwark", description = "Raises a shield wall that stops enemy shots until you attack.", cooldownMs = 12000, maxRange = 0, damage = 0, projectileType = -3, keybind = "Q", castBehavior = BarrierCast(3500)),
    eAbility = AbilityDef(name = "Shield Bash", description = "Bashes all nearby enemies, pushing them back.", cooldownMs = 10000, maxRange = 0, damage = 20, projectileType = ProjectileType.SHOCKWAVE, keybind = "E", castBehavior = GroundSlam(5.0f)),
    primaryProjectileType = ProjectileType.HOLY_BLADE, role = Melee, maxHealth = 135, moveSpeed = Melee.speed
  )

  val Druid: CharacterDef = CharacterDef(
    id = CharacterId.Druid, displayName = "Druid",
    description = "A nature shapeshifter with vine pulls and nature's leap teleport.",
    spriteSheet = "sprites/druid.png",
    qAbility = AbilityDef(name = "Vine Whip", description = "A vine that pulls enemies.", cooldownMs = 8000, maxRange = 18, damage = 8, projectileType = ProjectileType.VINE_WHIP, keybind = "Q"),
    eAbility = AbilityDef(name = "Nature's Leap", description = "Teleports through nature.", cooldownMs = 12000, maxRange = 8, damage = 0, projectileType = -2, keybind = "E", castBehavior = TeleportCast(8)),
    primaryProjectileType = ProjectileType.THORN, role = Ranged, maxHealth = 70
  )

  val Bard: CharacterDef = CharacterDef(
    id = CharacterId.Bard, displayName = "Bard",
    description = "A musical warrior with sonic waves that pass through walls.",
    spriteSheet = "sprites/bard.png",
    qAbility = AbilityDef(name = "Sonic Fan", description = "Fires 5 sonic waves in a fan.", cooldownMs = 8000, maxRange = 14, damage = 14, projectileType = ProjectileType.SONIC_WAVE, keybind = "Q", castBehavior = FanProjectile(5, Math.toRadians(90))),
    eAbility = AbilityDef(name = "Sonic Boom", description = "Blast that pushes enemies back.", cooldownMs = 10000, maxRange = 8, damage = 10, projectileType = ProjectileType.SONIC_BOOM, keybind = "E"),
    primaryProjectileType = ProjectileType.SONIC_WAVE, role = Ranged, maxHealth = 70
  )

  val Monk: CharacterDef = CharacterDef(
    id = CharacterId.Monk, displayName = "Monk",
    description = "A martial artist with charge-scaling fists, a dash, and flurry of punches.",
    spriteSheet = "sprites/monk.png",
    qAbility = AbilityDef(name = "Palm Strike", description = "Darts forward, phased and untouchable.", cooldownMs = 12000, maxRange = 0, damage = 0, projectileType = -1, keybind = "Q", castBehavior = DashBuff(10, 150, 15)),
    eAbility = AbilityDef(name = "Flurry", description = "Punches in all directions, stunning for 0.4s.", cooldownMs = 10000, maxRange = 4, damage = 22, projectileType = ProjectileType.FLURRY, keybind = "E", castBehavior = FanProjectile(8, 2 * Math.PI)),
    primaryProjectileType = ProjectileType.CHARGE_FIST, role = Melee, maxHealth = 120, moveSpeed = Melee.speed
  )

  val Cleric: CharacterDef = CharacterDef(
    id = CharacterId.Cleric, displayName = "Cleric",
    description = "A glass cannon holy nuker with devastating smites.",
    spriteSheet = "sprites/cleric.png",
    qAbility = AbilityDef(name = "Smite", description = "Holy smite with AoE splash.", cooldownMs = 6000, maxRange = 14, damage = 30, projectileType = ProjectileType.SMITE, keybind = "Q"),
    eAbility = AbilityDef(name = "Divine Judgment", description = "Fires 5 smites in a devastating fan.", cooldownMs = 14000, maxRange = 14, damage = 30, projectileType = ProjectileType.SMITE, keybind = "E", castBehavior = FanProjectile(5, Math.toRadians(60))),
    primaryProjectileType = ProjectileType.HOLY_BOLT_HEAVY, role = Ranged, maxHealth = 60
  )

  val Rogue: CharacterDef = CharacterDef(
    id = CharacterId.Rogue, displayName = "Rogue",
    description = "A stealthy fighter who fans shurikens and shadow steps away.",
    spriteSheet = "sprites/rogue.png",
    qAbility = AbilityDef(name = "Knife Spray", description = "Throws 3 poisoned shurikens in a fan that slow.", cooldownMs = 7000, maxRange = 6, damage = 22, projectileType = ProjectileType.TOXIC_SHURIKEN, keybind = "Q", castBehavior = FanProjectile(3, Math.toRadians(30))),
    eAbility = AbilityDef(name = "Shadow Step", description = "Teleports through shadows to cursor.", cooldownMs = 10000, maxRange = 6, damage = 0, projectileType = -2, keybind = "E", castBehavior = TeleportCast(6)),
    primaryProjectileType = ProjectileType.SHURIKEN, role = Melee, maxHealth = 115, moveSpeed = Melee.speed
  )

  val Barbarian: CharacterDef = CharacterDef(
    id = CharacterId.Barbarian, displayName = "Barbarian",
    description = "A savage warrior with triple axes and an earth-splitting fissure.",
    spriteSheet = "sprites/barbarian.png",
    qAbility = AbilityDef(name = "Triple Axe", description = "Throws 3 axes in a fan.", cooldownMs = 12000, maxRange = 5, damage = 33, projectileType = ProjectileType.AXE, keybind = "Q", castBehavior = FanProjectile(3, Math.toRadians(40))),
    eAbility = AbilityDef(name = "Earthsplitter", description = "Splits the earth in a line, rooting the first enemy it reaches for 1.2s.", cooldownMs = 12000, maxRange = 14, damage = 25, projectileType = ProjectileType.EARTHSPLITTER, keybind = "E"),
    primaryProjectileType = ProjectileType.AXE, role = Melee, maxHealth = 145, moveSpeed = Melee.speed
  )

  val Enchantress: CharacterDef = CharacterDef(
    id = CharacterId.Enchantress, displayName = "Enchantress",
    description = "A slow-focused controller whose every attack charms and slows foes.",
    spriteSheet = "sprites/enchantress.png",
    qAbility = AbilityDef(name = "Charm", description = "Mesmerizes an enemy, heavily slowing for 3s.", cooldownMs = 10000, maxRange = 14, damage = 15, projectileType = ProjectileType.CHARM, keybind = "Q"),
    eAbility = AbilityDef(name = "Blink", description = "Teleports to cursor.", cooldownMs = 12000, maxRange = 7, damage = 0, projectileType = -2, keybind = "E", castBehavior = TeleportCast(7)),
    primaryProjectileType = ProjectileType.CHARM, role = Ranged, maxHealth = 65
  )

  val Jester: CharacterDef = CharacterDef(
    id = CharacterId.Jester, displayName = "Jester",
    description = "A tricky jester who flings cards and teleports unpredictably.",
    spriteSheet = "sprites/jester.png",
    qAbility = AbilityDef(name = "Card Fan", description = "Throws 3 cards in a fan.", cooldownMs = 6000, maxRange = 14, damage = 16, projectileType = ProjectileType.CARD, keybind = "Q", castBehavior = FanProjectile(3, Math.toRadians(30))),
    eAbility = AbilityDef(name = "Trick", description = "Teleports to cursor.", cooldownMs = 12000, maxRange = 6, damage = 0, projectileType = -2, keybind = "E", castBehavior = TeleportCast(6)),
    primaryProjectileType = ProjectileType.CARD, role = Ranged, maxHealth = 65
  )

  val Valkyrie: CharacterDef = CharacterDef(
    id = CharacterId.Valkyrie, displayName = "Valkyrie",
    description = "A winged warrior with distance-scaling spears and aerial charge.",
    spriteSheet = "sprites/valkyrie.png",
    qAbility = AbilityDef(name = "Aerial Charge", description = "Dashes forward through the air.", cooldownMs = 10000, maxRange = 0, damage = 0, projectileType = -1, keybind = "Q", castBehavior = DashBuff(10, 350, 20)),
    eAbility = AbilityDef(name = "Spear Fan", description = "Throws 5 spears in a fan.", cooldownMs = 12000, maxRange = 20, damage = 10, projectileType = ProjectileType.SPEAR, keybind = "E", castBehavior = FanProjectile(5, Math.toRadians(60))),
    primaryProjectileType = ProjectileType.SPEAR, role = Ranged, maxHealth = 75
  )

  val Warlock: CharacterDef = CharacterDef(
    id = CharacterId.Warlock, displayName = "Warlock",
    description = "A dark caster with death bolts, fireballs, and shadow step.",
    spriteSheet = "sprites/warlock.png",
    qAbility = AbilityDef(name = "Demon Fire", description = "Launches a demonic fireball.", cooldownMs = 6000, maxRange = 18, damage = 45, projectileType = ProjectileType.FIREBALL, keybind = "Q"),
    eAbility = AbilityDef(name = "Shadow Step", description = "Teleports through shadow to cursor.", cooldownMs = 12000, maxRange = 8, damage = 0, projectileType = -2, keybind = "E", castBehavior = TeleportCast(8)),
    primaryProjectileType = ProjectileType.DEATH_BOLT, role = Ranged, maxHealth = 70
  )

  val Inquisitor: CharacterDef = CharacterDef(
    id = CharacterId.Inquisitor, displayName = "Inquisitor",
    description = "A CC lockdown specialist with chains that freeze and lock down groups.",
    spriteSheet = "sprites/inquisitor.png",
    qAbility = AbilityDef(name = "Chain Bolt", description = "Electrified chain that briefly freezes.", cooldownMs = 6000, maxRange = 14, damage = 12, projectileType = ProjectileType.CHAIN_BOLT, keybind = "Q"),
    eAbility = AbilityDef(name = "Chains of Justice", description = "Fires 5 lockdown chains in a fan.", cooldownMs = 10000, maxRange = 12, damage = 8, projectileType = ProjectileType.LOCKDOWN_CHAIN, keybind = "E", castBehavior = FanProjectile(5, Math.toRadians(60))),
    primaryProjectileType = ProjectileType.HOLY_BOLT, role = Ranged, maxHealth = 70
  )

  val characters: Seq[CharacterDef] = Seq(
    Paladin, Ranger, Berserker, Crusader, Druid, Bard,
    Monk, Cleric, Rogue, Barbarian, Enchantress, Jester,
    Valkyrie, Warlock, Inquisitor
  )
}
