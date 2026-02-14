package com.gridgame.common.model

import com.gridgame.common.Constants

case class AbilityDef(
    name: String,
    description: String,
    cooldownMs: Int,
    maxRange: Int,
    damage: Int,
    projectileType: Byte,
    keybind: String
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
  val Spaceman: CharacterDef = CharacterDef(
    id = CharacterId.Spaceman,
    displayName = "Spaceman",
    description = "A versatile space explorer with tentacle grab and ice beam.",
    spriteSheet = "sprites/character.png",
    qAbility = AbilityDef(
      name = "Tentacle",
      description = "Shoots a tentacle that pulls enemies closer.",
      cooldownMs = Constants.TENTACLE_COOLDOWN_MS,
      maxRange = Constants.TENTACLE_MAX_RANGE,
      damage = Constants.TENTACLE_DAMAGE,
      projectileType = ProjectileType.TENTACLE,
      keybind = "Q"
    ),
    eAbility = AbilityDef(
      name = "Ice Beam",
      description = "Fires a freezing beam that immobilizes enemies.",
      cooldownMs = Constants.ICE_BEAM_COOLDOWN_MS,
      maxRange = Constants.ICE_BEAM_MAX_RANGE,
      damage = Constants.ICE_BEAM_DAMAGE,
      projectileType = ProjectileType.ICE_BEAM,
      keybind = "E"
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
      cooldownMs = Constants.SPEAR_COOLDOWN_MS,
      maxRange = Constants.SPEAR_MAX_RANGE,
      damage = Constants.SPEAR_BASE_DAMAGE,
      projectileType = ProjectileType.SPEAR,
      keybind = "Q"
    ),
    eAbility = AbilityDef(
      name = "Rope",
      description = "Throws a rope that pulls enemies to you.",
      cooldownMs = Constants.ROPE_COOLDOWN_MS,
      maxRange = Constants.ROPE_MAX_RANGE,
      damage = Constants.ROPE_DAMAGE,
      projectileType = ProjectileType.ROPE,
      keybind = "E"
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
      cooldownMs = Constants.PHASE_SHIFT_COOLDOWN_MS,
      maxRange = 0,
      damage = 0,
      projectileType = -1,
      keybind = "Q"
    ),
    eAbility = AbilityDef(
      name = "Haunt",
      description = "Slow spectral bolt that teleports you behind the target on hit.",
      cooldownMs = Constants.HAUNT_COOLDOWN_MS,
      maxRange = Constants.HAUNT_MAX_RANGE,
      damage = Constants.HAUNT_DAMAGE,
      projectileType = ProjectileType.HAUNT,
      keybind = "E"
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
      cooldownMs = Constants.FIREBALL_COOLDOWN_MS,
      maxRange = Constants.FIREBALL_MAX_RANGE,
      damage = Constants.FIREBALL_DAMAGE,
      projectileType = ProjectileType.FIREBALL,
      keybind = "Q"
    ),
    eAbility = AbilityDef(
      name = "Blink",
      description = "Instantly teleport up to 6 cells toward your cursor.",
      cooldownMs = Constants.BLINK_COOLDOWN_MS,
      maxRange = Constants.BLINK_DISTANCE,
      damage = 0,
      projectileType = -2,
      keybind = "E"
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
      cooldownMs = Constants.TIDAL_WAVE_COOLDOWN_MS,
      maxRange = Constants.TIDAL_WAVE_MAX_RANGE,
      damage = Constants.TIDAL_WAVE_DAMAGE,
      projectileType = ProjectileType.TIDAL_WAVE,
      keybind = "Q"
    ),
    eAbility = AbilityDef(
      name = "Geyser",
      description = "Erupts on hit or at max range, damaging all nearby.",
      cooldownMs = Constants.GEYSER_COOLDOWN_MS,
      maxRange = Constants.GEYSER_MAX_RANGE,
      damage = Constants.GEYSER_DAMAGE,
      projectileType = ProjectileType.GEYSER,
      keybind = "E"
    ),
    primaryProjectileType = ProjectileType.SPLASH
  )

  val Raptor: CharacterDef = CharacterDef(
    id = CharacterId.Raptor,
    displayName = "Raptor",
    description = "A bird of prey that swoops in and blasts enemies away.",
    spriteSheet = "sprites/raptor.png",
    qAbility = AbilityDef(
      name = "Swoop",
      description = "Dash toward the cursor, phased and invulnerable during flight.",
      cooldownMs = Constants.SWOOP_COOLDOWN_MS,
      maxRange = 0,
      damage = 0,
      projectileType = -1,
      keybind = "Q"
    ),
    eAbility = AbilityDef(
      name = "Gust",
      description = "Wind blast that pushes enemies away from you.",
      cooldownMs = Constants.GUST_COOLDOWN_MS,
      maxRange = Constants.GUST_MAX_RANGE,
      damage = Constants.GUST_DAMAGE,
      projectileType = ProjectileType.GUST,
      keybind = "E"
    ),
    primaryProjectileType = ProjectileType.TALON
  )

  private val byId: Map[Byte, CharacterDef] = Map(
    CharacterId.Spaceman.id -> Spaceman,
    CharacterId.Gladiator.id -> Gladiator,
    CharacterId.Wraith.id -> Wraith,
    CharacterId.Wizard.id -> Wizard,
    CharacterId.Tidecaller.id -> Tidecaller,
    CharacterId.Raptor.id -> Raptor
  )

  val all: Seq[CharacterDef] = Seq(Spaceman, Gladiator, Wraith, Wizard, Tidecaller, Raptor)

  def get(id: CharacterId): CharacterDef = byId.getOrElse(id.id, Spaceman)

  def get(id: Byte): CharacterDef = byId.getOrElse(id, Spaceman)
}
