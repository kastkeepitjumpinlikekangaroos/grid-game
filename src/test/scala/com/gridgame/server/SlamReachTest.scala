package com.gridgame.server

import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

/**
 * What a slam reaches, as the model says (AbilityDef.slamRadius), is the ground the server's slam
 * strikes: for every slam in the roster, cast as a client casts it, an enemy whose cell's centre is
 * within that radius of the caster's is hit and nobody further off is. The GroundSlam's own radius
 * is a cell short of the splash for five of them: taken as the reach, it would leave out a cell of
 * victims all the way round.
 */
class SlamReachTest {
  private def slams: Seq[(CharacterDef, AbilityDef, Int)] = CharacterDef.all.flatMap { c =>
    Seq((c.qAbility, AttackSlot.Q), (c.eAbility, AttackSlot.E)).collect {
      case (a, slot) if a.castBehavior.isInstanceOf[GroundSlam] => (c, a, slot)
    }
  }

  @Test def everySlamHitsExactlyWithinItsReach(): Unit = {
    assertTrue(slams.nonEmpty)
    for ((c, ability, slot) <- slams) {
      val m = new TestMatch(WorldData.createEmpty(41, 41))
      val caster = m.join(c.id, 20, 20)
      val r = ability.slamRadius
      // An enemy on every cell of a band round the ring's edge, a cell and a half either side of it
      val victims = for {
        dx <- -12 to 12; dy <- -12 to 12
        d = Math.sqrt(dx * dx + dy * dy)
        if d >= r - 1.5 && d <= r + 1.5
      } yield (dx, dy, m.join(CharacterId.Gladiator, 20 + dx, 20 + dy))
      m.clearSent()
      val cast = m.fire(caster, slot, ability.projectileType, Seq((0f, 0f)))
      assertEquals(s"${c.displayName}'s ${ability.name} is cast", 1, cast.size)
      m.tick()
      val hit = m.projectileEvents(m.udpSent(caster), ProjectileAction.HIT).map(_.getTargetId).toSet
      victims.foreach { case (dx, dy, v) =>
        assertEquals(s"${c.displayName}'s ${ability.name} (ring $r) on ($dx, $dy)", dx * dx + dy * dy <= r * r,
          hit.contains(v.getId))
      }
    }
  }
}
