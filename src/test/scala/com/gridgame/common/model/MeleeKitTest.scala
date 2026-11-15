package com.gridgame.common.model

import org.junit.Assert._
import org.junit.Test

/**
 * The melee and skirmisher kits (Plan 5a). A ranged character walks a little quicker than they do,
 * so one can kite them for as long as both only walk: each kit needs crowd control, and a way in
 * that doesn't depend on walking faster, unless it is one of the anchors that hold ground instead.
 * And once in, it has to be able to finish: a hold long enough for two blows (every fighter's pull
 * has one), or a way to get on top of someone and one of the hardest-hitting primaries.
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
    case Freeze(_) | Stun(_) | Launch(_) | Root(_) | TeleportOwnerBehind(_, _) => true
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

  /** A hold worth the name is one long enough for two blows: a primary goes every half second. */
  private val LockdownMs = 600

  /** The primaries that hit hardest, the axes' 49 up to the 55 no blow goes past: two fell any ranged
    * character (80 health at most) with health to spare. */
  private val BurstDamage = 49

  /** How long this attack holds whoever it lands on, in ms; 0 if it holds nobody. */
  private def holdMs(d: ProjectileDef): Int = {
    val onHit = d.onHitEffects.collect {
      case Freeze(ms) => ms
      case Stun(ms) => ms
      case Launch(ms) => ms
      case Root(ms) => ms
      case TeleportOwnerBehind(_, ms) => ms
    }
    val splashes = (d.aoeOnHit.toSeq ++ d.aoeOnMaxRange).flatMap(a => Seq(a.freezeDurationMs, a.rootDurationMs, a.stunDurationMs))
    (onHit ++ splashes :+ 0).max
  }

  private def locksDown(c: CharacterDef): Boolean = attacks(c).flatMap { case (_, t) => defOf(t) }.exists(holdMs(_) >= LockdownMs)

  private def closes(c: CharacterDef): Boolean = attacks(c).exists { case (a, _) => a.castBehavior match {
    case TeleportCast(_) | DashBuff(_, _, _) | PhaseShiftBuff(_) => true
    case _ => false
  } }

  private def bursts(c: CharacterDef): Boolean = ProjectileDef.get(c.primaryProjectileType).damage >= BurstDamage

  /** The fighters held to neither, each with why. Ember's shots reach 10 cells, the furthest of any
    * fighter's, and its phase is for getting out. */
  private val neither: Set[CharacterId] = Set(CharacterId.Ember)

  @Test def everyFighterLocksItsTargetDownOrBurstsItDown(): Unit = {
    // design/characters.md, *A fighter can finish what it starts*: getting to someone is not
    // enough, since they walk away a little quicker than a fighter follows. A kit holds them there
    // (a stun, a freeze, a root, a knock-up) long enough for two blows, or it has a blink, a dash
    // or a phase to get on top of them and one of the hardest-hitting primaries to end it with
    for (c <- fighters if !neither.contains(c.id))
      assertTrue(s"${c.displayName} can neither hold a target nor burst one down", locksDown(c) || (closes(c) && bursts(c)))
    // One that has come to do either needs no exemption, and would hide the next real one
    for (id <- neither) {
      val c = CharacterDef.get(id)
      assertTrue(s"${c.displayName} is a fighter", fighters.contains(c))
      assertFalse(s"${c.displayName} needs no exemption", locksDown(c) || (closes(c) && bursts(c)))
    }
  }

  @Test def aFightersPullHoldsItsCatchForASecond(): Unit = {
    // A pull lands its catch on the fighter's own cell, and they used to walk straight off it: the
    // Death Knight's grip, and every other fighter's pull with it, stuns for a second once it has
    // them there. In that order, the pull first: the stun is counted from the moment they arrive
    val pulls = for (c <- fighters; (a, t) <- attacks(c); d <- defOf(t) if d.onHitEffects.contains(PullToOwner)) yield (c, a, d)
    assertEquals("the fighters who pull",
      Set("Gladiator", "Reaper", "Death Knight", "Bear", "Shark", "Gorilla", "Griffin", "Chef"), pulls.map(_._1.displayName).toSet)
    for ((c, a, d) <- pulls)
      assertEquals(s"${c.displayName}'s ${a.name}", Seq(PullToOwner, Stun(1000)), d.onHitEffects)
  }

  @Test def aRangedCharactersPullOnlyPulls(): Unit = {
    // Whoever fights from range has no need to keep anyone beside them, and a pull that stunned
    // would be a stun thrown from 15 to 25 cells. The Gladiator's rope is a type of its own (the
    // Lasso) so that the Puppeteer's and the Pirate's stays as it was
    for (c <- CharacterDef.all if c.role == Ranged; t <- c.primaryProjectileType +: attacks(c).map(_._2); d <- defOf(t)
         if d.onHitEffects.contains(PullToOwner))
      assertEquals(s"${c.displayName}'s ${d.name}", Seq(PullToOwner), d.onHitEffects)
    val (lasso, rope) = (ProjectileDef.get(ProjectileType.LASSO), ProjectileDef.get(ProjectileType.ROPE))
    assertEquals(ProjectileType.LASSO, lasso.id)
    assertEquals(rope.copy(id = lasso.id, name = lasso.name, alsoOnHit = Seq(Stun(1000))), lasso)
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

  @Test def aVariantKeepsItsBaseTypesFlightAndReach(): Unit = {
    // Same flight and reach as the type it copies, so the look and sound it borrows fit it. Neither
    // depends on the damage, which a variant may set for itself (the Arena Axe's 51 to the Axe's
    // 49). The Axe Spin, the Maul and the Blood Frenzy were rings of eight thrown variants; they are
    // slams of their own now
    val variants = Seq(
      ProjectileType.VENOM_DART -> ProjectileType.POISON_DART, ProjectileType.ROOTING_BOULDER -> ProjectileType.BOULDER,
      ProjectileType.EMBER_FAN -> ProjectileType.EMBER_SHOT, ProjectileType.FENRIR_CLAW -> ProjectileType.CLAW_SWIPE,
      ProjectileType.GHOUL_CLAW -> ProjectileType.CLAW_SWIPE, ProjectileType.SHARK_CLAW -> ProjectileType.CLAW_SWIPE,
      ProjectileType.ICE_BOULDER -> ProjectileType.BOULDER, ProjectileType.DRAIN_BLADE -> ProjectileType.CURSED_BLADE,
      ProjectileType.CHILL_BLADE -> ProjectileType.CURSED_BLADE,
      ProjectileType.ARENA_AXE -> ProjectileType.AXE, ProjectileType.THROWING_STAR -> ProjectileType.SHURIKEN,
      ProjectileType.MANTIS_SCYTHE -> ProjectileType.CLAW_SWIPE)
    for ((v, b) <- variants) {
      val (vd, bd) = (ProjectileDef.get(v), ProjectileDef.get(b))
      assertEquals(vd.name, v, vd.id)
      assertEquals(vd.name, (bd.speedMultiplier, bd.maxRange, bd.hitRadius),
        (vd.speedMultiplier, vd.maxRange, vd.hitRadius))
    }
  }

  @Test def anAbilityCopyingAMeleePrimaryKeepsItsReach(): Unit = {
    // The melee primaries were cut to half their reach for much harder hits (design/characters.md,
    // *Melee strikes short and hard*), and the abilities weren't: the three that copy one fly as it
    // does, and as far as it did, which is the reach their abilities say. Nor were they widened when
    // the primaries were (*A melee swing strikes wide*): each strikes within the 2.2 cells it always
    // has, a hold or a root being a thing to aim
    val copies = Seq(
      (ProjectileType.FLURRY, ProjectileType.FIST, CharacterId.Monk),
      (ProjectileType.PIN_DOWN, ProjectileType.TALON, CharacterId.Hawk),
      (ProjectileType.TOXIC_SHURIKEN, ProjectileType.SHURIKEN, CharacterId.Rogue))
    for ((v, b, thrower) <- copies) {
      val (vd, bd) = (ProjectileDef.get(v), ProjectileDef.get(b))
      val c = CharacterDef.get(thrower)
      val ability = Seq(c.qAbility, c.eAbility).find(_.projectileType == v).get
      assertEquals(vd.name, v, vd.id)
      assertEquals(vd.name, bd.speedMultiplier, vd.speedMultiplier, 0.001f)
      assertEquals(vd.name, (2.2f, 0f), (vd.hitRadius, vd.hitCentreBehind))
      assertTrue(s"${bd.name}, a primary, strikes wider", bd.hitRadius >= 3.7f)
      assertEquals(vd.name, ability.maxRange, vd.maxRange)
      assertTrue(s"${vd.name} reaches further than the ${bd.name}", vd.maxRange > bd.maxRange)
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
