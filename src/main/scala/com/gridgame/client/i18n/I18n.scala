package com.gridgame.client.i18n

import com.gridgame.common.model.{AbilityDef, CharacterDef, ItemType}

/**
 * Resolves translated display text for shared game-content models
 * ([[CharacterDef]], [[AbilityDef]], [[ItemType]]) that live in `common` and
 * still hold their English text.
 *
 * Keys are derived from stable numeric ids so the English strings baked into the
 * models remain the authoritative fallback (and the source the English catalog
 * is generated from). Server code is unaffected — this is purely client display.
 *
 * Key scheme:
 *   char.<charId>.name / .desc
 *   char.<charId>.q.name / .q.desc / .e.name / .e.desc
 *   item.<itemId>
 */
object I18n {

  def characterName(d: CharacterDef): String = Messages.tOr(s"char.${d.id.id}.name", d.displayName)

  def characterDesc(d: CharacterDef): String = Messages.tOr(s"char.${d.id.id}.desc", d.description)

  def abilityName(charId: Byte, slot: String, a: AbilityDef): String =
    Messages.tOr(s"char.$charId.$slot.name", a.name)

  def abilityDesc(charId: Byte, slot: String, a: AbilityDef): String =
    Messages.tOr(s"char.$charId.$slot.desc", a.description)

  def qName(d: CharacterDef): String = abilityName(d.id.id, "q", d.qAbility)
  def qDesc(d: CharacterDef): String = abilityDesc(d.id.id, "q", d.qAbility)
  def eName(d: CharacterDef): String = abilityName(d.id.id, "e", d.eAbility)
  def eDesc(d: CharacterDef): String = abilityDesc(d.id.id, "e", d.eAbility)

  def itemName(t: ItemType): String = Messages.tOr(s"item.${t.id}", t.name)
}
