package com.gridgame.common.model

import org.junit.Assert._
import org.junit.Test

/**
 * The kits as a whole (design/characters.md, *No volleys of more than three*). A volley used to be
 * the easy ability, and forty-seven characters fired one, up to eight at once: fifty volleys that
 * made kits alike. No volley is more than three now, and one is left only where it is what the
 * character is; the rest became crowd control, hastes, blinks and the like. Held here: the cap,
 * the list of volleys left, no two kits alike, and what the server assumes of a kit.
 */
class KitTest {
  private val all = CharacterDef.all

  private def abilities(c: CharacterDef): Seq[AbilityDef] = Seq(c.qAbility, c.eAbility)

  /** Every volley left, and why it is that character's. A new one needs a reason here. */
  private val volleys: Map[CharacterId, String] = Map(
    CharacterId.Warden -> "a jailer's chains flung wide",
    CharacterId.Vampire -> "a swarm of bats",
    CharacterId.Ember -> "a sprite's scattered embers",
    CharacterId.Ranger -> "an archer's multishot",
    CharacterId.Monk -> "a flurry of blows",
    CharacterId.Rogue -> "a fan of thrown knives",
    CharacterId.Jester -> "a fanned hand of cards",
    CharacterId.Photon -> "light split by a prism",
    CharacterId.Pilot -> "a plane's strafing guns",
    CharacterId.Hydra -> "three of its heads at once",
    CharacterId.Cerberus -> "its three heads"
  )

  @Test def noVolleyIsMoreThanThree(): Unit =
    for (c <- all; a <- abilities(c)) a.castBehavior match {
      case fan @ FanProjectile(count, _) =>
        assertTrue(s"${c.displayName}'s ${a.name} fires $count", count <= 3)
        // Three in a ring are 120 degrees apart, which is a volley that misses
        assertFalse(s"${c.displayName}'s ${a.name} is a ring", fan.isFullCircle)
      case _ =>
    }

  @Test def aVolleyIsLeftOnlyWhereItIsWhatTheCharacterIs(): Unit = {
    val firing = all.filter(c => abilities(c).exists(_.castBehavior.isInstanceOf[FanProjectile])).map(_.id).toSet
    assertEquals(volleys.keySet, firing)
    // One volley a kit: a second would make it a volley kit again
    for (c <- all)
      assertTrue(c.displayName, abilities(c).count(_.castBehavior.isInstanceOf[FanProjectile]) <= 1)
  }

  @Test def noTwoKitsAreAlike(): Unit = {
    // A kit is what its Q and E throw and how they are cast, in either order. The Wizard's,
    // the Warlock's and the Astronomer's were the same fireball and blink, and three were a charm
    // and a blink
    def shape(a: AbilityDef): (Byte, String) = (a.projectileType, a.castBehavior.getClass.getSimpleName)
    val byKit = all.groupBy(c => Set(shape(c.qAbility), shape(c.eAbility)))
    byKit.foreach { case (_, cs) => assertEquals(cs.map(_.displayName).mkString(" and "), 1, cs.size) }
  }

  @Test def aKitHasAtMostOnePhaseOrDash(): Unit = {
    // The server honours the phased flag by the first phase or dash in the kit
    // (StepRequests.activatePhase): a second would be timed by the first's numbers
    for (c <- all) {
      val n = abilities(c).count(_.castBehavior match {
        case PhaseShiftBuff(_) | DashBuff(_, _, _) => true
        case _ => false
      })
      assertTrue(c.displayName, n <= 1)
    }
  }

  /** Every attack in the roster that throws a projectile, with who throws it. */
  private def thrown: Seq[(CharacterDef, AbilityDef)] =
    for (c <- all; a <- abilities(c) if a.projectileType >= 0 || ProjectileDef.get(a.projectileType).id == a.projectileType)
      yield (c, a)

  @Test def aHasteSpeedsItsCasterUpAsItIsCast(): Unit = {
    // A self-haste is a ground slam whose projectile carries a SpeedBoost: the server gives it to
    // the caster as the slam goes down (GameInstance.applyCastSelfBuff), whoever is in it, so every
    // one of them is a slam. Two are nothing but a haste and go off as their key goes down; the
    // others also burn or slow whoever is near
    val hastes = thrown.filter { case (_, a) => ProjectileDef.get(a.projectileType).hastesCaster }
    val slams = hastes.filter(_._2.castBehavior.isInstanceOf[GroundSlam])
    assertEquals(Set(CharacterId.Cyborg, CharacterId.Pyromancer, CharacterId.Berserker, CharacterId.Gambler),
      slams.map(_._1.id).toSet)
    assertEquals(Set(CharacterId.Cyborg, CharacterId.Gambler),
      slams.filter { case (_, a) => ProjectileDef.get(a.projectileType).onlyHastesCaster }.map(_._1.id).toSet)
    // The rest haste on the hit: the Wolf's two strikes and the Windwalker's Gale
    assertEquals(Set(CharacterId.Wolf, CharacterId.Windwalker), hastes.filterNot(slams.contains).map(_._1.id).toSet)
  }

  @Test def aPureHasteHurtsNobody(): Unit = {
    // Overclock's shape: what makes it a self-buff the client sets off on the key going down
    for ((c, a) <- thrown if ProjectileDef.get(a.projectileType).onlyHastesCaster) {
      val d = ProjectileDef.get(a.projectileType)
      assertEquals(c.displayName, 0, d.damage)
      assertEquals(c.displayName, 0, a.damage)
      assertTrue(c.displayName, d.aoeOnMaxRange.forall(_.damage == 0))
    }
  }

  @Test def everyNewSlamReachesWhereItsBotCastsIt(): Unit = {
    // GroundSlam(radius) is when a bot casts it, the AoE what it reaches. Each of the volleys' slams
    // keeps the two in step (MeleeKitTest holds the fighters' to it)
    val news = Set(ProjectileType.COMBUST, ProjectileType.FROST_NOVA, ProjectileType.REPULSE,
      ProjectileType.SHOCK_BURST, ProjectileType.LUCKY_STREAK, ProjectileType.AXE_SPIN, ProjectileType.MAUL,
      ProjectileType.BLOOD_FRENZY, ProjectileType.HORN_FLIP)
    val seen = for ((c, a) <- thrown if news(a.projectileType)) yield {
      val GroundSlam(radius) = a.castBehavior
      assertEquals(s"${c.displayName} ${a.name}", radius, ProjectileDef.get(a.projectileType).aoeOnMaxRange.get.radius, 0.01f)
      a.projectileType
    }
    assertEquals(news, seen.toSet)
  }
}
