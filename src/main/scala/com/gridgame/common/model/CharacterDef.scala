package com.gridgame.common.model

import com.gridgame.common.model.roster.Roster

/**
 * One character: its abilities, its primary attack, its role and the numbers that come with it.
 * The characters themselves are in `roster/`, a file per category; this is where they are looked
 * up.
 */
case class CharacterDef(
    id: CharacterId,
    displayName: String,
    description: String,
    spriteSheet: String,
    qAbility: AbilityDef,
    eAbility: AbilityDef,
    primaryProjectileType: Byte,
    // Melee, skirmisher or ranged (CombatRole): the band the character's health sits in and the
    // pace they walk at. Named on every character rather than read off the primary's range.
    role: CombatRole,
    // Within the role's band: 60-80 ranged, 105-150 melee and skirmishers
    maxHealth: Int,
    // Multiplier on the base walking rate of 20 cells a second (Movement): the role's pace,
    // CombatRole.speed, which for ranged characters is the base rate itself
    moveSpeed: Float = 1.0f
)

object CharacterDef {
  // Every projectile definition is registered here, so once this object is initialized every lookup
  // finds them all; ProjectileDef.get makes sure it is. The variants copy their base's numbers from
  // the registry, so they come second.
  ProjectileDef.register(Roster.projectiles: _*)
  ProjectileDef.register(Roster.variants(ProjectileDef.get): _*)

  /** Every character, in id order. */
  val all: Seq[CharacterDef] = Roster.characters

  private val byId: Map[Byte, CharacterDef] = all.map(d => d.id.id -> d).toMap

  // What `get` answers for an id that isn't a character's
  private val fallback: CharacterDef = byId(CharacterId.DEFAULT.id)

  /** Does nothing but make sure this object, and with it every ProjectileDef, is initialized. */
  private[model] def ensureRegistered(): Unit = ()

  def get(id: CharacterId): CharacterDef = byId.getOrElse(id.id, fallback)

  def get(id: Byte): CharacterDef = byId.getOrElse(id, fallback)

  /** Is this the id of a real character? (`get` falls back to the Spaceman for anything else.) */
  def isValid(id: Byte): Boolean = byId.contains(id)
}
