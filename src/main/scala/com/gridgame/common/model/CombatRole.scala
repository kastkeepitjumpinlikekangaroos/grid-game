package com.gridgame.common.model

/**
 * What a character is for, read from how far their primary attack reaches: a melee fighter has
 * to close in, a ranged one wants distance, and a skirmisher — the boulder-throwing bruisers —
 * fights in between. Every character names its role outright (CharacterDef.role) rather than
 * having it worked out from the range, because the rule has exceptions: the Monk's fist is a
 * 6-cell punch that reaches 10 fully charged, and it is a melee character.
 *
 * The role sets the two numbers that trade range for staying power:
 *  - health: ranged characters sit in `minHealth`..`maxHealth` 60-80, melee and skirmishers in
 *    105-150, so whoever has to get into range can survive getting there;
 *  - pace: `speed`, the multiplier a character's moveSpeed takes (Movement). The further a role
 *    reaches, the quicker it walks, but only just: ranged steps every 50ms, skirmishers every
 *    52ms, melee every 53ms. A ranged character walking away from a melee one opens the gap by
 *    about a cell a second, so it can keep its distance and shoot back for as long as they both
 *    only walk. Closing it takes an ability or an item: a pull or a teleport does it outright.
 */
sealed abstract class CombatRole(val name: String, val speed: Float, val minHealth: Int, val maxHealth: Int)

object CombatRole {
  case object Melee extends CombatRole("Melee", 0.94f, 105, 150)
  case object Skirmisher extends CombatRole("Skirmisher", 0.97f, 105, 150)
  case object Ranged extends CombatRole("Ranged", 1.0f, 60, 80)

  val all: Seq[CombatRole] = Seq(Melee, Skirmisher, Ranged)

  /** The role a primary attack of this reach reads as: 6 cells or less is melee, up to 10 a
    * skirmisher, anything further ranged. */
  def forRange(cells: Double): CombatRole =
    if (cells <= 6) Melee else if (cells <= 10) Skirmisher else Ranged
}
