package com.gridgame.common.model

import org.junit.Assert._
import org.junit.Test

/**
 * The melee and skirmisher kits (Plan 5a). A ranged character walks a little quicker than they do,
 * so one can kite them for as long as both only walk: each kit needs crowd control, and a way in
 * that doesn't depend on walking faster, unless it is one of the anchors that hold ground instead.
 */
class MeleeKitTest {
  import CombatRole.{Melee, Ranged, Skirmisher}

  private val fighters = CharacterDef.all.filter(c => c.role == Melee || c.role == Skirmisher)

  /** The kits that hold ground rather than close on anyone: a barrier or a big hold around them,
    * and a team to do the chasing. */
  private val anchors: Set[CharacterId] =
    Set(CharacterId.Crusader, CharacterId.Golem, CharacterId.Beetle, CharacterId.Avalanche)

  private def attacks(c: CharacterDef): Seq[(AbilityDef, Byte)] =
    Seq(c.qAbility, c.eAbility).map(a => a -> a.projectileType)

  private def isHold(e: OnHitEffect): Boolean = e match {
    case Freeze(_) | Stun(_) | Root(_) | TeleportOwnerBehind(_, _) => true
    case _ => false
  }

  private def isCrowdControl(e: OnHitEffect): Boolean = isHold(e) || (e match {
    case Slow(_, _) | PullToOwner | Push(_) | VortexPull(_, _) => true
    case _ => false
  })

  private def defOf(t: Byte): Option[ProjectileDef] =
    if (t >= 0 || ProjectileDef.get(t).id == t) Some(ProjectileDef.get(t)) else None

  private def splashHolds(d: ProjectileDef): Boolean =
    (d.aoeOnHit.toSeq ++ d.aoeOnMaxRange).exists(a => a.freezeDurationMs > 0 || a.rootDurationMs > 0 || a.stunDurationMs > 0)

  /** What a pace of 1.0 walks: the speed anyone kiting a melee character opens the gap at. */
  private val RunnerCellsPerSec = 20.0

  /** How far off a projectile of this def lands on someone walking straight away from it: it
    * flies 33.3 x speedMultiplier cells a second from a cell in front of its thrower, and hits
    * inside its hit radius (see Plan 5a's findings; the play test measured the rope's 10). */
  private def reachOnARunner(d: ProjectileDef): Double = {
    val speed = 1000.0 / 30 * d.speedMultiplier
    val gained = if (speed > RunnerCellsPerSec) d.maxRange * (1 - RunnerCellsPerSec / speed) else 0.0
    gained + d.hitRadius + 1
  }

  /** The cells a dash gains over walking: a player's dash moves its distance over its duration,
    * while the one being chased keeps walking. */
  private def dashGain(c: CharacterDef, d: DashBuff): Double =
    d.maxDistance - 1000.0 / Movement.baseStepIntervalMs(c.moveSpeed) * d.durationMs / 1000.0

  /** Every way this kit gets in on a runner 8 cells off, named. */
  private def waysIn(c: CharacterDef): Seq[String] = attacks(c).flatMap { case (a, t) =>
    a.castBehavior match {
      case TeleportCast(_) => Seq(s"${a.name}: a teleport")
      case PhaseShiftBuff(_) => Seq(s"${a.name}: a phase at twice the pace")
      case d: DashBuff if dashGain(c, d) >= 6 => Seq(f"${a.name}: a dash gaining ${dashGain(c, d)}%.1f cells")
      case GroundSlam(radius) =>
        defOf(t).filter(d => radius >= 8 && (d.onHitEffects.exists(isCrowdControl) || splashHolds(d)))
          .map(_ => s"${a.name}: a slam reaching $radius").toSeq
      case StandardProjectile | FanProjectile(_, _) =>
        defOf(t).filter(d => d.onHitEffects.exists(isCrowdControl) && reachOnARunner(d) >= 8)
          .map(d => f"${a.name}: lands on a runner from ${reachOnARunner(d)}%.1f cells").toSeq
      case _ => Nil
    }
  }

  @Test def everyKitHasCrowdControlOrABarrier(): Unit = {
    for (c <- fighters) {
      val types = c.primaryProjectileType +: attacks(c).map(_._2)
      val cc = types.flatMap(defOf).exists(d => d.onHitEffects.exists(isCrowdControl) || splashHolds(d))
      val other = attacks(c).exists { case (a, _) => a.castBehavior match {
        case BarrierCast(_) | TrapCast(_, _) => true
        case _ => false
      } }
      assertTrue(s"${c.displayName} has no crowd control", cc || other)
    }
  }

  @Test def everyKitButTheAnchorsCanGetInOnARunner(): Unit = {
    for (c <- fighters if !anchors.contains(c.id))
      assertTrue(s"${c.displayName} has no way in on someone walking away", waysIn(c).nonEmpty)
  }

  @Test def theAnchorsAreAnchors(): Unit = {
    // One that can close after all no longer needs the exemption, and hides the next real one
    for (id <- anchors) {
      val c = CharacterDef.get(id)
      assertTrue(s"${c.displayName} is a fighter", fighters.contains(c))
      assertEquals(s"${c.displayName}'s ways in", Nil, waysIn(c))
    }
  }

  @Test def noFightersPrimaryHoldsItsTarget(): Unit = {
    // Primaries fire twice a second, and a hold grants 1.5s of CC immunity, which turns away the
    // kit's own holds (and its team's). A slow grants none.
    for (c <- fighters; d <- defOf(c.primaryProjectileType))
      assertFalse(s"${c.displayName}'s primary holds: ${d.onHitEffects}", d.onHitEffects.exists(isHold))
  }

  @Test def aFightersSlamReachesWhereItsBotCastsIt(): Unit = {
    // GroundSlam(radius) is when a bot casts it; the def's AoE is what it really reaches
    for (c <- fighters; (a, t) <- attacks(c)) a.castBehavior match {
      case GroundSlam(radius) =>
        val aoe = ProjectileDef.get(t).aoeOnMaxRange
        assertEquals(s"${c.displayName} ${a.name}", radius, aoe.map(_.radius).getOrElse(0f), 0.01f)
      case _ =>
    }
  }

  @Test def aVariantKeepsItsBaseTypesNumbers(): Unit = {
    // Same flight, reach and damage as the type it copies, so the look and sound it borrows fit it
    val variants = Seq(
      ProjectileType.AXE_SPIN -> ProjectileType.AXE, ProjectileType.VENOM_DART -> ProjectileType.POISON_DART,
      ProjectileType.TOXIC_SHURIKEN -> ProjectileType.SHURIKEN, ProjectileType.BLOOD_FRENZY -> ProjectileType.CLAW_SWIPE,
      ProjectileType.FLURRY -> ProjectileType.FIST, ProjectileType.ROOTING_BOULDER -> ProjectileType.BOULDER,
      ProjectileType.EMBER_FAN -> ProjectileType.EMBER_SHOT, ProjectileType.FENRIR_CLAW -> ProjectileType.CLAW_SWIPE,
      ProjectileType.GHOUL_CLAW -> ProjectileType.CLAW_SWIPE, ProjectileType.SHARK_CLAW -> ProjectileType.CLAW_SWIPE,
      ProjectileType.ICE_BOULDER -> ProjectileType.BOULDER, ProjectileType.DRAIN_BLADE -> ProjectileType.CURSED_BLADE,
      ProjectileType.CHILL_BLADE -> ProjectileType.CURSED_BLADE)
    for ((v, b) <- variants) {
      val (vd, bd) = (ProjectileDef.get(v), ProjectileDef.get(b))
      assertEquals(vd.name, v, vd.id)
      assertEquals(vd.name, (bd.speedMultiplier, bd.maxRange, bd.damage, bd.hitRadius),
        (vd.speedMultiplier, vd.maxRange, vd.damage, vd.hitRadius))
    }
  }

  @Test def moreThanOneEffectOnAHitKeepsItsOrder(): Unit = {
    val horn = ProjectileDef.get(ProjectileType.HEAD_THROW)
    assertEquals(Seq(Push(2.5f), Stun(800)), horn.onHitEffects)
    assertEquals(Seq(Slow(3000, 0.4f), Poison(15, 3000, 750)), ProjectileDef.get(ProjectileType.VENOM_DART).onHitEffects)
  }

  @Test def noRangedCharacterIsAFighter(): Unit =
    assertTrue(fighters.forall(_.role != Ranged))
}
