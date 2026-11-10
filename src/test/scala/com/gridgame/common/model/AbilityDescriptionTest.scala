package com.gridgame.common.model

import org.junit.Assert._
import org.junit.Test

import scala.collection.mutable

/**
 * What an ability says of itself, held to what it does. A description is what the player reads on
 * the character screen (the catalog's copy of it, which ContentCatalogTest holds to this one), and
 * its numbers are written by hand beside rules that are tuned without it: every number a
 * description gives — how long a hold lasts, how far a blast or a push reaches, how far a leap
 * goes, how many it fires, what share of the damage it heals, what a poison or a burn takes — is
 * one its ability's rule has.
 */
class AbilityDescriptionTest {
  private case class Told(character: CharacterDef, ability: AbilityDef) {
    val what = s"${character.displayName}'s ${ability.name} (\"${ability.description}\")"
    private val projectile: Option[ProjectileDef] =
      if (ability.projectileType >= 0 || ability.castBehavior == StandardProjectile || ability.castBehavior.isInstanceOf[FanProjectile] ||
          ability.castBehavior.isInstanceOf[GroundSlam]) Some(ProjectileDef.get(ability.projectileType)) else None
    private val trap: Option[TrapDef] = ability.castBehavior match {
      case TrapCast(t, _) => Option(TrapDef.get(t))
      case _ => None
    }
    val effects: Seq[OnHitEffect] = projectile.toSeq.flatMap(_.onHitEffects) ++ trap.toSeq.flatMap(_.effects)
    private val splashes: Seq[AoESplashConfig] = projectile.toSeq.flatMap(p => p.aoeOnHit.toSeq ++ p.aoeOnMaxRange.toSeq)

    /** Every length of time the ability's rule has, in ms. */
    val durations: Set[Int] = (effects.flatMap {
      case Freeze(ms) => Seq(ms)
      case Stun(ms) => Seq(ms)
      case Launch(ms) => Seq(ms)
      case Root(ms) => Seq(ms)
      case Slow(ms, _) => Seq(ms)
      case SpeedBoost(ms) => Seq(ms)
      case Burn(_, ms, _) => Seq(ms)
      case Poison(_, ms, _) => Seq(ms)
      case TeleportOwnerBehind(_, ms) => Seq(ms)
      case _ => Nil
    } ++ splashes.flatMap(s => Seq(s.freezeDurationMs, s.rootDurationMs, s.stunDurationMs)) ++ (ability.castBehavior match {
      case PhaseShiftBuff(ms) => Seq(ms)
      case DashBuff(_, ms, _) => Seq(ms)
      case BarrierCast(ms) => Seq(ms)
      case _ => Nil
    })).filter(_ > 0).toSet

    /** Every reach round a point the ability's rule has, in cells: a splash's, a blast's, a vortex's. */
    val radii: Set[Float] = (splashes.map(_.radius) ++ projectile.toSeq.flatMap(_.explosionConfig).map(_.blastRadius) ++
      effects.collect { case VortexPull(r, _) => r } ++ trap.toSeq.flatMap(_.explosion).map(_.blastRadius)).toSet

    val pushes: Set[Float] = effects.collect { case Push(d) => d }.toSet
    val leaps: Set[Int] = (ability.castBehavior match {
      case TeleportCast(d) => Seq(d)
      case DashBuff(d, _, _) => Seq(d)
      case _ => Nil
    }).toSet
    val volley: Int = ability.castBehavior match {
      case FanProjectile(n, _) => n
      case _ => 1
    }
    val heals: Set[Int] = effects.collect { case LifeSteal(p) => p }.toSet
    /** (what it takes, seconds) of every poison and burn. A poison takes its total; a burn what its
      * bites come to, which is less than the total its rule lists (design/combat.md, *A burn does
      * not deal its listed total*): a description says what the player loses. */
    val overTime: Set[(Int, Double)] = effects.collect {
      case Burn(total, ms, tickMs) => (Told.burnDeals(total, ms, tickMs), ms / 1000.0)
      case Poison(total, ms, _) => (total, ms / 1000.0)
    }.toSet
    val bounces: Int = projectile.map(_.ricochetCount).getOrElse(0)
    val pierces: Int = projectile.map(_.pierceCount).getOrElse(0)
  }

  private object Told {
    /** What a burn takes in all: a bite of its total over its ticks (rounded down) on the first
      * 200ms player tick a whole tick after it was lit or last bit, while it lasts (the server's
      * Vitals, which TimedEffectsTest holds to this for every burn in the game). */
    def burnDeals(total: Int, durationMs: Int, tickMs: Int): Int = {
      val each = if (durationMs / tickMs > 0) total / (durationMs / tickMs) else total
      var bites = 0
      var last = 0
      var t = 200
      while (t < durationMs) {
        if (t >= last + tickMs) { bites += 1; last = t }
        t += 200
      }
      each * bites
    }
  }

  private val told: Seq[Told] = CharacterDef.all.flatMap(c => Seq(Told(c, c.qAbility), Told(c, c.eAbility)))

  private val wrong = mutable.Buffer[String]()
  private def hold(ok: Boolean, why: => String): Unit = if (!ok) wrong += why
  private def verdict(): Unit = if (wrong.nonEmpty) fail(s"${wrong.size} descriptions say what their ability doesn't do:\n  " + wrong.mkString("\n  "))

  private def number(s: String): Double = s.toDouble
  private val words = Map("once" -> 1, "twice" -> 2, "two" -> 2, "three" -> 3, "four" -> 4)

  @Test def everyLengthOfTimeADescriptionGivesIsOneItsAbilityHas(): Unit = {
    // "for 1.2s", "for 3 seconds", "over 5s"
    val time = """(\d+(?:\.\d+)?) ?(?:s\b|seconds?\b)""".r
    var seen = 0
    for (t <- told; m <- time.findAllMatchIn(t.ability.description)) {
      seen += 1
      val ms = Math.round(number(m.group(1)) * 1000).toInt
      hold(t.durations.contains(ms), s"${t.what}: ${m.group(1)}s, where it has ${t.durations.toSeq.sorted.map(_ / 1000.0).mkString(", ")}s")
    }
    assertTrue(s"descriptions give times ($seen)", seen > 40)
    verdict()
  }

  @Test def everyReachADescriptionGivesIsOneItsAbilityHas(): Unit = {
    val within = """within (\d+(?:\.\d+)?) cells?""".r
    var seen = 0
    for (t <- told; m <- within.findAllMatchIn(t.ability.description)) {
      seen += 1
      val cells = number(m.group(1)).toFloat
      hold(t.radii.contains(cells), s"${t.what}: within ${m.group(1)} cells, where it reaches ${t.radii.toSeq.sorted.mkString(", ")}")
    }
    assertTrue(s"descriptions give reaches ($seen)", seen > 10)
    verdict()
  }

  @Test def everyPushADescriptionGivesIsItsAbilitys(): Unit = {
    // "back 5 cells", "back 4", "thrown back 2 cells"
    val back = """back (\d+(?:\.\d+)?)""".r
    var seen = 0
    for (t <- told; m <- back.findAllMatchIn(t.ability.description)) {
      seen += 1
      hold(t.pushes.contains(number(m.group(1)).toFloat), s"${t.what}: back ${m.group(1)}, where it pushes ${t.pushes.mkString(", ")}")
    }
    assertTrue(s"descriptions give pushes ($seen)", seen >= 4)
    verdict()
  }

  @Test def everyLeapADescriptionGivesIsItsAbilitys(): Unit = {
    // "up to 7 cells", "Pounces 10 cells toward the cursor", "dashing 10 cells", "up to 8 cells away"
    val leap = """(?:up to |ounces |ashing |eaps |ashes )(\d+) cells""".r
    var seen = 0
    for (t <- told if t.leaps.nonEmpty; m <- leap.findAllMatchIn(t.ability.description)) {
      seen += 1
      hold(t.leaps.contains(m.group(1).toInt), s"${t.what}: ${m.group(1)} cells, where it goes ${t.leaps.mkString(", ")}")
    }
    assertTrue(s"descriptions give leaps ($seen)", seen >= 8)
    verdict()
  }

  @Test def everyVolleyADescriptionCountsIsAsManyAsItFires(): Unit = {
    // "Fires 3 arrows", "Flicks 3 embers in a fan", "Throws 3 cards", "from 3 heads at once"
    val count = """(?:Fires|Flicks|Throws|Releases|from) (\d+) [a-z]""".r
    var seen = 0
    for (t <- told; m <- count.findAllMatchIn(t.ability.description)) {
      seen += 1
      hold(t.volley == m.group(1).toInt, s"${t.what}: ${m.group(1)} of them, where it fires ${t.volley}")
    }
    assertTrue(s"descriptions count volleys ($seen)", seen >= 8)
    // And none fires several while speaking of one, or of none
    for (t <- told if t.volley > 1)
      hold(("""\b""" + t.volley + """\b""").r.findFirstIn(t.ability.description).isDefined ||
        words.exists { case (w, n) => n == t.volley && t.ability.description.contains(w) },
        s"${t.what}: it fires ${t.volley}, and doesn't say how many")
    verdict()
  }

  @Test def everyShareOfDamageADescriptionHealsIsItsAbilitys(): Unit = {
    val share = """healing for (\d+)% of""".r
    var seen = 0
    for (t <- told; m <- share.findAllMatchIn(t.ability.description)) {
      seen += 1
      hold(t.heals.contains(m.group(1).toInt), s"${t.what}: heals ${m.group(1)}%, where it steals ${t.heals.mkString(", ")}%")
    }
    assertTrue(s"descriptions give shares ($seen)", seen >= 3)
    verdict()
  }

  @Test def whatADescriptionSaysAPoisonOrABurnTakesIsItsTotal(): Unit = {
    // "poisons for 24 over 3s", "alight for 56 over 5s"
    val over = """for (\d+) over (\d+(?:\.\d+)?)s""".r
    var seen = 0
    for (t <- told; m <- over.findAllMatchIn(t.ability.description)) {
      seen += 1
      hold(t.overTime.contains((m.group(1).toInt, number(m.group(2)))),
        s"${t.what}: ${m.group(1)} over ${m.group(2)}s, where it has ${t.overTime.map { case (n, s) => s"$n over ${s}s" }.mkString(", ")}")
    }
    assertTrue(s"descriptions give damage over time ($seen)", seen >= 2)
    verdict()
  }

  @Test def bouncesAndPiercesAreAsManyAsADescriptionSays(): Unit = {
    // "bounces off walls up to 4 times", "bounces off walls twice", "banks off a wall once"
    val bounce = """(?:bounces off walls|banks off a wall)(?: up to)? (\d+|once|twice)""".r
    var seen = 0
    for (t <- told; m <- bounce.findAllMatchIn(t.ability.description)) {
      seen += 1
      val n = words.getOrElse(m.group(1), m.group(1).toIntOption.getOrElse(-1))
      hold(t.bounces == n, s"${t.what}: bounces ${m.group(1)}, where it ricochets ${t.bounces} times")
    }
    // "the first two enemies": passes through one and is used up by the next
    val firstFew = """the first (two|three|four) enemies""".r
    for (t <- told; m <- firstFew.findAllMatchIn(t.ability.description)) {
      seen += 1
      hold(t.pierces == words(m.group(1)) - 1, s"${t.what}: the first ${m.group(1)}, where it passes through ${t.pierces}")
    }
    assertTrue(s"descriptions give bounces and pierces ($seen)", seen >= 5)
    verdict()
  }
}
