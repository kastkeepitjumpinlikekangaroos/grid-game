package com.gridgame.common.model.roster

import com.gridgame.common.model.{CharacterDef, ProjectileDef}

/**
 * The whole roster: every character, and every projectile definition they throw, gathered from the
 * category files beside this one. CharacterDef's initializer registers the definitions and serves
 * the characters; nothing else reads this.
 */
private[model] object Roster {
  /** Every character, in id order: the order the character grid and the website list them. */
  val characters: Seq[CharacterDef] =
    Originals.characters ++ Elementals.characters ++ Undead.characters ++ Medieval.characters ++
      SciFi.characters ++ Beasts.characters ++ Mythological.characters ++ Specialists.characters

  /** Every projectile definition that isn't a variant of another. */
  val projectiles: Seq[ProjectileDef] =
    Originals.projectiles ++ Elementals.projectiles ++ Undead.projectiles ++ Medieval.projectiles ++
      SciFi.projectiles ++ Beasts.projectiles ++ Mythological.projectiles ++ Specialists.projectiles ++
      Retired.projectiles

  /** The variants: another type's numbers and look with effects of their own, made from `base`,
    * which must already hold every definition above. */
  def variants(base: Byte => ProjectileDef): Seq[ProjectileDef] =
    Originals.variants(base) ++ Elementals.variants(base) ++ Undead.variants(base) ++
      Medieval.variants(base) ++ Beasts.variants(base) ++ Mythological.variants(base)
}
