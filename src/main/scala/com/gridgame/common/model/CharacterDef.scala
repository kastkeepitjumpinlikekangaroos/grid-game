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
    primaryProjectileType: Byte
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

  private val byId: Map[Byte, CharacterDef] = Map(
    CharacterId.Spaceman.id -> Spaceman
  )

  val all: Seq[CharacterDef] = Seq(Spaceman)

  def get(id: CharacterId): CharacterDef = byId.getOrElse(id.id, Spaceman)

  def get(id: Byte): CharacterDef = byId.getOrElse(id, Spaceman)
}
