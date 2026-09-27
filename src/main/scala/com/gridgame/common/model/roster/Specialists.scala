package com.gridgame.common.model.roster

import com.gridgame.common.model._
import com.gridgame.common.model.CombatRole.{Melee, Ranged, Skirmisher}

/**
 * Specialists: characters with a trade (character ids 102-111).
 *
 * First the definitions of the projectiles a character here is the first in the roster to throw,
 * then the characters. CharacterDef's initializer registers every definition (Roster); how a
 * projectile type looks is GLProjectileRenderers'.
 */
private[model] object Specialists {
  // ── Projectiles ──

  val FistDef: ProjectileDef = ProjectileDef(id = ProjectileType.FIST, name = "Fist", speedMultiplier = 0.8f, damage = 22, maxRange = 4, hitRadius = 2.2f, boomerang = true)
  val KnifeDef: ProjectileDef = ProjectileDef(id = ProjectileType.KNIFE, name = "Knife", speedMultiplier = 0.95f, damage = 18, maxRange = 6, hitRadius = 2.2f)
  val HammerDef: ProjectileDef = ProjectileDef(id = ProjectileType.HAMMER, name = "Hammer", speedMultiplier = 0.6f, damage = 33, maxRange = 5, hitRadius = 2.5f)
  val AcidFlaskDef: ProjectileDef = ProjectileDef(id = ProjectileType.ACID_FLASK, name = "Acid Flask", speedMultiplier = 0.4f, damage = 20, maxRange = 14, passesThroughPlayers = true, explodesOnPlayerHit = true, explosionConfig = Some(ExplosionConfig(20, 8, 3.0f)))
  val StarBoltDef: ProjectileDef = ProjectileDef(id = ProjectileType.STAR_BOLT, name = "Star Bolt", speedMultiplier = 0.8f, damage = 16, maxRange = 22, chargeSpeedScaling = Some(ChargeScaling(0.6f, 1.1f)), chargeDamageScaling = Some(ChargeScaling(10f, 35f)), chargeRangeScaling = Some(ChargeScaling(14f, 28f)), distanceDamageScaling = Some(DistanceDamageScaling(8, 45, 22)))
  val RuneBoltDef: ProjectileDef = ProjectileDef(id = ProjectileType.RUNE_BOLT, name = "Rune Bolt", speedMultiplier = 0.75f, damage = 18, maxRange = 18, ricochetCount = 2)
  // A slam (Plan 5a): a bot casts it when its target is inside the ability's GroundSlam radius,
  // so that radius and the AoE radius here are kept in step
  val FeralRoarDef: ProjectileDef = ProjectileDef(id = ProjectileType.FERAL_ROAR, name = "Feral Roar", speedMultiplier = 0f, damage = 0, maxRange = 0, aoeOnMaxRange = Some(AoESplashConfig(5.0f, 15)), onHitEffect = Some(Slow(2000, 0.5f)), explosionConfig = Some(ExplosionConfig(0, 0, 5.0f)))
  // A closer (Plan 5a): lands on a ranged character walking away from 8 cells, wherever they are
  // in their step (MeleeKitsTest). See common/model/CLAUDE.md, "Melee kits"
  val HammerThrowDef: ProjectileDef = ProjectileDef(id = ProjectileType.HAMMER_THROW, name = "Hammer Throw", speedMultiplier = 0.95f, damage = 30, maxRange = 16, hitRadius = 2.2f, onHitEffect = Some(Stun(800)))
  val MeatHookDef: ProjectileDef = ProjectileDef(id = ProjectileType.MEAT_HOOK, name = "Meat Hook", speedMultiplier = 0.9f, damage = 10, maxRange = 18, onHitEffect = Some(PullToOwner))
  // A splash rather than an explosion: an explosion deals its blast and nothing else, so its
  // on-hit burn would never land (Plan 5a)
  val FlambeDef: ProjectileDef = ProjectileDef(id = ProjectileType.FLAMBE, name = "Flambe", speedMultiplier = 0.7f, damage = 20, maxRange = 6, aoeOnHit = Some(AoESplashConfig(2.5f, 15)), aoeOnMaxRange = Some(AoESplashConfig(2.5f, 15)), onHitEffect = Some(Burn(20, 3000, 750)))

  val projectiles: Seq[ProjectileDef] = Seq(
    FistDef, KnifeDef, HammerDef, AcidFlaskDef, StarBoltDef,
    RuneBoltDef, FeralRoarDef, HammerThrowDef, MeatHookDef, FlambeDef
  )

  // ── Characters ──

  val Alchemist: CharacterDef = CharacterDef(
    id = CharacterId.Alchemist, displayName = "Alchemist",
    description = "A potion-brewing expert with explosive and corrosive concoctions.",
    spriteSheet = "sprites/alchemist.png",
    qAbility = AbilityDef(name = "Explosive Potion", description = "Hurls an explosive potion.", cooldownMs = 6000, maxRange = 18, damage = 45, projectileType = ProjectileType.FIREBALL, keybind = "Q"),
    eAbility = AbilityDef(name = "Acid Flask", description = "Throws acid that explodes and slows.", cooldownMs = 10000, maxRange = 14, damage = 20, projectileType = ProjectileType.ACID_FLASK, keybind = "E"),
    primaryProjectileType = ProjectileType.PLAGUE_BOLT, role = Ranged, maxHealth = 70
  )

  val Puppeteer: CharacterDef = CharacterDef(
    id = CharacterId.Puppeteer, displayName = "Puppeteer",
    description = "A string master who controls enemies with chains and pulls.",
    spriteSheet = "sprites/puppeteer.png",
    qAbility = AbilityDef(name = "Puppet String", description = "Pulls an enemy with string.", cooldownMs = 8000, maxRange = 25, damage = 5, projectileType = ProjectileType.ROPE, keybind = "Q"),
    eAbility = AbilityDef(name = "String Web", description = "Fires 3 strings in a fan.", cooldownMs = 10000, maxRange = 14, damage = 12, projectileType = ProjectileType.CHAIN_BOLT, keybind = "E", castBehavior = FanProjectile(3, Math.toRadians(30))),
    primaryProjectileType = ProjectileType.CHAIN_BOLT, role = Ranged, maxHealth = 70
  )

  val Gambler: CharacterDef = CharacterDef(
    id = CharacterId.Gambler, displayName = "Gambler",
    description = "A chaotic risk-taker who flings cards in fans and wild card circles.",
    spriteSheet = "sprites/gambler.png",
    qAbility = AbilityDef(name = "Card Fan", description = "Throws 5 cards in a fan.", cooldownMs = 7000, maxRange = 14, damage = 16, projectileType = ProjectileType.CARD, keybind = "Q", castBehavior = FanProjectile(5, Math.toRadians(45))),
    eAbility = AbilityDef(name = "Wild Card", description = "Flings 8 cards in all directions.", cooldownMs = 10000, maxRange = 14, damage = 16, projectileType = ProjectileType.CARD, keybind = "E", castBehavior = FanProjectile(8, 2 * Math.PI)),
    primaryProjectileType = ProjectileType.CARD, role = Ranged, maxHealth = 65
  )

  val Blacksmith: CharacterDef = CharacterDef(
    id = CharacterId.Blacksmith, displayName = "Blacksmith",
    description = "A forge master who hurls hammers and lays traps from the anvil.",
    spriteSheet = "sprites/blacksmith.png",
    qAbility = AbilityDef(name = "Hammer Throw", description = "Hurls one heavy hammer that stuns for 0.8s.", cooldownMs = 12000, maxRange = 16, damage = 30, projectileType = ProjectileType.HAMMER_THROW, keybind = "Q"),
    eAbility = AbilityDef(name = "Anvil Trap", description = "Sets a forged trap that arms in a moment and stuns the first enemy over it for 2s.", cooldownMs = 12000, maxRange = 6, damage = 10, projectileType = -4, keybind = "E", castBehavior = TrapCast(TrapType.BEAR_TRAP, 6)),
    primaryProjectileType = ProjectileType.HAMMER, role = Melee, maxHealth = 145, moveSpeed = Melee.speed
  )

  val Pirate: CharacterDef = CharacterDef(
    id = CharacterId.Pirate, displayName = "Pirate",
    description = "A swashbuckler with pistol, grapple, and cannonball.",
    spriteSheet = "sprites/pirate.png",
    qAbility = AbilityDef(name = "Grapple", description = "Pulls enemy with rope.", cooldownMs = 8000, maxRange = 25, damage = 5, projectileType = ProjectileType.ROPE, keybind = "Q"),
    eAbility = AbilityDef(name = "Cannonball", description = "Fires an explosive cannonball.", cooldownMs = 10000, maxRange = 12, damage = 40, projectileType = ProjectileType.GRENADE, keybind = "E"),
    primaryProjectileType = ProjectileType.BULLET, role = Ranged, maxHealth = 70
  )

  val Chef: CharacterDef = CharacterDef(
    id = CharacterId.Chef, displayName = "Chef",
    description = "A culinary warrior who hooks dinner in, carves it with knives and flambes it.",
    spriteSheet = "sprites/chef.png",
    qAbility = AbilityDef(name = "Meat Hook", description = "Hooks an enemy and drags them to you.", cooldownMs = 10000, maxRange = 18, damage = 10, projectileType = ProjectileType.MEAT_HOOK, keybind = "Q"),
    eAbility = AbilityDef(name = "Flambe", description = "A short gout of flame that bursts over everyone near it and sets them alight.", cooldownMs = 8000, maxRange = 6, damage = 20, projectileType = ProjectileType.FLAMBE, keybind = "E"),
    primaryProjectileType = ProjectileType.KNIFE, role = Melee, maxHealth = 130, moveSpeed = Melee.speed
  )

  val Musician: CharacterDef = CharacterDef(
    id = CharacterId.Musician, displayName = "Musician",
    description = "A hypnotic performer who mesmerizes foes and blasts them away with sonic booms.",
    spriteSheet = "sprites/musician.png",
    qAbility = AbilityDef(name = "Hypnotic Melody", description = "Mesmerizing song that heavily slows for 3 seconds.", cooldownMs = 10000, maxRange = 14, damage = 15, projectileType = ProjectileType.CHARM, keybind = "Q"),
    eAbility = AbilityDef(name = "Sonic Boom", description = "Blast that pushes enemies back.", cooldownMs = 10000, maxRange = 8, damage = 10, projectileType = ProjectileType.SONIC_BOOM, keybind = "E"),
    primaryProjectileType = ProjectileType.SONIC_WAVE, role = Ranged, maxHealth = 70
  )

  val Astronomer: CharacterDef = CharacterDef(
    id = CharacterId.Astronomer, displayName = "Astronomer",
    description = "A stargazer whose star bolts scale with charge and distance.",
    spriteSheet = "sprites/astronomer.png",
    qAbility = AbilityDef(name = "Meteor", description = "Summons a devastating meteor.", cooldownMs = 6000, maxRange = 18, damage = 45, projectileType = ProjectileType.FIREBALL, keybind = "Q"),
    eAbility = AbilityDef(name = "Astral Project", description = "Teleports through the stars.", cooldownMs = 12000, maxRange = 8, damage = 0, projectileType = -2, keybind = "E", castBehavior = TeleportCast(8)),
    primaryProjectileType = ProjectileType.STAR_BOLT, role = Ranged, maxHealth = 60
  )

  val Runesmith: CharacterDef = CharacterDef(
    id = CharacterId.Runesmith, displayName = "Runesmith",
    description = "A trap specialist whose ricocheting rune bolts bounce off walls.",
    spriteSheet = "sprites/runesmith.png",
    qAbility = AbilityDef(name = "Rune Burst", description = "Fires 5 rune bolts in a fan.", cooldownMs = 8000, maxRange = 18, damage = 18, projectileType = ProjectileType.RUNE_BOLT, keybind = "Q", castBehavior = FanProjectile(5, Math.toRadians(60))),
    eAbility = AbilityDef(name = "Rune Trap", description = "Burns a rune into the ground that sets the first enemy over it alight for 40 over 5s.", cooldownMs = 12000, maxRange = 6, damage = 40, projectileType = -4, keybind = "E", castBehavior = TrapCast(TrapType.FIRE_RUNE, 6)),
    primaryProjectileType = ProjectileType.RUNE_BOLT, role = Ranged, maxHealth = 70
  )

  val Shapeshifter: CharacterDef = CharacterDef(
    id = CharacterId.Shapeshifter, displayName = "Shapeshifter",
    description = "A form-changer that hurls boomerang fists, charges in beast form and roars prey to a crawl.",
    spriteSheet = "sprites/shapeshifter.png",
    qAbility = AbilityDef(name = "Beast Charge", description = "Charges forward in beast form.", cooldownMs = 10000, maxRange = 0, damage = 0, projectileType = -1, keybind = "Q", castBehavior = DashBuff(10, 150, 18)),
    eAbility = AbilityDef(name = "Feral Roar", description = "Roars in beast form, slowing every enemy nearby.", cooldownMs = 12000, maxRange = 0, damage = 15, projectileType = ProjectileType.FERAL_ROAR, keybind = "E", castBehavior = GroundSlam(5.0f)),
    primaryProjectileType = ProjectileType.FIST, role = Melee, maxHealth = 130, moveSpeed = Melee.speed
  )

  val characters: Seq[CharacterDef] = Seq(
    Alchemist, Puppeteer, Gambler, Blacksmith, Pirate, Chef,
    Musician, Astronomer, Runesmith, Shapeshifter
  )
}
