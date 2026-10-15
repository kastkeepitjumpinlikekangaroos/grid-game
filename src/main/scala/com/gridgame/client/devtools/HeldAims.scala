package com.gridgame.client.devtools

import com.gridgame.client.game.GameClient
import com.gridgame.common.model._
import com.gridgame.common.protocol.AttackSlot

/**
 * The dev tools' attacks being aimed (render/CLAUDE.md, *An aimed attack shows where it will go*):
 * for each kind of aim, the first character in the roster's order with an attack of that kind, and the
 * AttackSlot it is in. Picked by what the attack does rather than by name, so the golden frames and
 * the render bench keep a scene of each kind whoever's kit changes.
 */
private[devtools] object HeldAims {
  /** Every kind, as `--hold=` takes it. */
  val kinds: Seq[String] = Seq("primary", "shot", "blast", "fan", "dash", "blink", "trap")

  def holder(kind: String): (CharacterId, Int) = {
    def ability(is: AbilityDef => Boolean): (CharacterId, Int) =
      CharacterDef.all.iterator.flatMap { c =>
        if (is(c.qAbility)) Some((c.id, AttackSlot.Q)) else if (is(c.eAbility)) Some((c.id, AttackSlot.E)) else None
      }.nextOption().getOrElse(throw new IllegalArgumentException(s"no character holds a $kind"))
    def shot(a: AbilityDef): ProjectileDef =
      if (a.castBehavior == StandardProjectile) ProjectileDef.get(a.projectileType) else null
    kind match {
      case "primary" =>
        CharacterDef.all.find(c => ProjectileDef.get(c.primaryProjectileType).chargeSpeedScaling.isEmpty)
          .map(c => (c.id, AttackSlot.PRIMARY)).get
      case "shot" => ability { a => val d = shot(a); d != null && !d.isExplosive && d.aoeOnMaxRange.isEmpty }
      case "blast" => ability { a => val d = shot(a); d != null && d.isExplosive }
      case "fan" => ability(_.castBehavior.isInstanceOf[FanProjectile])
      case "dash" => ability(_.castBehavior.isInstanceOf[DashBuff])
      case "blink" => ability(_.castBehavior.isInstanceOf[TeleportCast])
      case "trap" => ability(_.castBehavior.isInstanceOf[TrapCast])
      case other => throw new IllegalArgumentException(s"--hold=$other: one of ${kinds.mkString(", ")}")
    }
  }

  /** Take the attack in `slot` up to aim it, as the input handlers do: the primary held down, Q or E
    * its key pressed. */
  def press(client: GameClient, slot: Int): Unit =
    if (slot == AttackSlot.PRIMARY) client.pressPrimary() else client.pressAbility(slot - AttackSlot.Q)
}
