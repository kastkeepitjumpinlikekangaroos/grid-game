package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.GameClock
import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

import scala.collection.mutable

/**
 * How often an attack may be cast, held to both ends of what the fire-rate check promises
 * (PacketValidator.validateProjectileSpawn), for every attack of every character, on a clock of the
 * test's own (SimKit):
 *
 *  - **an honest client is never refused**: a cast every cooldown, each held up on the way by
 *    anything up to the network's tolerance (NETWORK_TOLERANCE_MS) and a fan's projectiles spread
 *    over its cast's window, is taken whole, every time;
 *  - **nobody gets ahead of the cooldown**: a client asking as fast as it can send is given, over
 *    any length of time, the casts its cooldown allows and the one the tolerance lets come early.
 *
 * A phase, a dash, a barrier and R are held to the same two ends by their own rules (StepRequests,
 * CooldownResetRequests): each may come early by the tolerance, every time.
 */
class CastPaceTest {
  private val Tolerance = Constants.NETWORK_TOLERANCE_MS

  // The two allowances, as the rule states them (design/networking.md, *Bad networks*), worked out
  // here rather than asked of the code they hold: how much sooner than its cooldown a cast may
  // come (the network's tolerance, or a fifth of a long cooldown, and never more than the
  // cooldown), and how long after its first projectile the rest of a cast may arrive (the
  // tolerance, but never more than half the cooldown)
  private def toleranceOf(cooldownMs: Long): Long = Math.min(cooldownMs, Math.max(cooldownMs / 5, Tolerance.toLong))
  private def windowOf(cooldownMs: Long): Long = Math.min(Tolerance.toLong, cooldownMs / 2)

  /** With what the server says of every request it refuses kept off the test's output: these
    * refuse thousands. */
  private def silently[T](body: => T): T = {
    var result: Option[T] = None
    ServerTestKit.captureStderr(ServerTestKit.quietly { result = Some(body) })
    result.get
  }

  /** One of a character's attacks that fires projectiles. */
  private case class Attack(character: CharacterDef, slot: Int, projectileType: Byte, cooldownMs: Int, perCast: Int,
                            headings: Seq[(Float, Float)]) {
    def name: String = s"${character.displayName}'s ${Seq("primary", "Q", "E", "burst")(slot)}"
  }

  private def fan(count: Int, angleOf: Int => Double): Seq[(Float, Float)] =
    (0 until count).map { i => val a = angleOf(i); (Math.cos(a).toFloat, Math.sin(a).toFloat) }

  private def ofAbility(c: CharacterDef, slot: Int, a: AbilityDef): Option[Attack] = a.castBehavior match {
    case f @ FanProjectile(count, _) => Some(Attack(c, slot, a.projectileType, a.cooldownMs, count, fan(count, f.angleOf)))
    case StandardProjectile => Some(Attack(c, slot, a.projectileType, a.cooldownMs, 1, Seq((1f, 0f))))
    case GroundSlam(_) => Some(Attack(c, slot, a.projectileType, a.cooldownMs, 1, Seq((0f, 0f))))
    case _ => None
  }

  /** Every attack in the roster that fires: a primary as it is fired with a gem (three of it, which
    * the server takes with or without the gem), its burst, and Q and E where they throw anything. */
  private val attacks: Seq[Attack] = CharacterDef.all.flatMap { c =>
    Seq(Attack(c, AttackSlot.PRIMARY, c.primaryProjectileType, Constants.SHOOT_COOLDOWN_MS, 3,
        fan(3, i => (i - 1) * Math.PI / 36)),
      Attack(c, AttackSlot.BURST, c.primaryProjectileType, Constants.BURST_SHOT_COOLDOWN_MS, 8, AttackSlot.BurstDirections)) ++
      ofAbility(c, AttackSlot.Q, c.qAbility) ++ ofAbility(c, AttackSlot.E, c.eAbility)
  }

  /** Fly everything in flight out, so nobody runs into the thirty a player may have in the air. */
  private def clearTheAir(m: SimMatch): Unit = {
    var n = 0
    while (m.instance.projectileManager.size > 0 && n < 2000) { m.tick(); n += 1 }
    assertEquals("the air cleared", 0, m.instance.projectileManager.size)
  }

  /** Requests arriving at these moments (ms from now, in order), each along its heading: how many
    * of them the server took. The clock is moved to each as it arrives. */
  private def arrive(m: SimMatch, p: Player, a: Attack, arrivals: Seq[(Long, (Float, Float))]): Int = {
    val from = m.clock.millis()
    var taken = 0
    for ((at, heading) <- arrivals.sortBy(_._1)) {
      if (from + at > m.clock.millis()) m.clock.advance(from + at - m.clock.millis())
      taken += m.fire(p, a.slot, a.projectileType, Seq(heading)).size
      if (m.instance.projectileManager.size > 16) clearTheAir(m)
    }
    clearTheAir(m)
    taken
  }

  @Test def anHonestClientsCastsAreNeverRefusedHoweverTheNetworkHoldsThemUp(): Unit = silently(Sim() { clock =>
    val rnd = new scala.util.Random(11)
    val m = new SimMatch(clock)
    val Casts = 12
    assertTrue("the roster's attacks", attacks.size > 300)
    for (a <- attacks) {
      val p = m.join(a.character.id, 30, 30)
      val window = windowOf(a.cooldownMs)
      val arrivals = (0 until Casts).flatMap { k =>
        // Sent on the cooldown; held up on the way by up to the tolerance, and its projectiles
        // spread over the cast's window as they arrive
        val first = k.toLong * a.cooldownMs + rnd.nextInt(Tolerance)
        a.headings.zipWithIndex.map { case (h, i) => (first + (if (i == 0) 0 else rnd.nextInt(window.toInt)), h) }
      }
      assertEquals(s"${a.name} (${ProjectileDef.get(a.projectileType).name}, every ${a.cooldownMs}ms): every projectile of every cast",
        Casts * a.perCast, arrive(m, p, a, arrivals))
      m.instance.handler.removePlayer(p.getId)
    }
  })

  @Test def anHonestClientsCastsAreTakenOnAPerfectNetworkToo(): Unit = silently(Sim() { clock =>
    // Nothing held up at all: each cast lands exactly a cooldown after the last
    val m = new SimMatch(clock)
    for (a <- attacks) {
      val p = m.join(a.character.id, 30, 30)
      val arrivals = (0 until 8).flatMap(k => a.headings.map(h => (k.toLong * a.cooldownMs, h)))
      assertEquals(a.name, 8 * a.perCast, arrive(m, p, a, arrivals))
      m.instance.handler.removePlayer(p.getId)
    }
  })

  @Test def nobodyFiresFasterThanTheCooldownInTheLongRun(): Unit = silently(Sim() { clock =>
    // One attack for each cooldown and count the roster has: the rule knows nothing else of it
    val kinds = attacks.groupBy(a => (a.slot, a.cooldownMs, a.perCast)).toSeq.sortBy(_._1).map(_._2.head)
    assertTrue(kinds.size > 20)
    val m = new SimMatch(clock)
    for (a <- kinds) {
      val p = m.join(a.character.id, 30, 30)
      // A request every 7ms, for long enough to cast it several times over
      val length = Math.max(20000L, 6L * a.cooldownMs)
      val arrivals = (0L until length by 7L).zipWithIndex.map { case (at, i) => (at, a.headings(i % a.headings.size)) }
      val taken = arrive(m, p, a, arrivals)
      val tolerance = toleranceOf(a.cooldownMs)
      val most = a.perCast * ((length + tolerance) / a.cooldownMs + 1)
      val least = a.perCast * (length / a.cooldownMs)
      assertTrue(s"${a.name} (every ${a.cooldownMs}ms, ${a.perCast} a cast): $taken taken in ${length}ms, $most at most", taken <= most)
      assertTrue(s"${a.name} (every ${a.cooldownMs}ms, ${a.perCast} a cast): $taken taken in ${length}ms, $least at least", taken >= least)
      m.instance.handler.removePlayer(p.getId)
    }
  })

  @Test def aCastThatCameEarlyDoesNotBringTheNextOneForward(): Unit = silently(Sim() { clock =>
    // The schedule is the attack's own: a cooldown after the last cast was due, not after it came
    val m = new SimMatch(clock)
    val c = CharacterDef.all.find(_.qAbility.castBehavior == StandardProjectile).get
    val a = ofAbility(c, AttackSlot.Q, c.qAbility).get
    val p = m.join(c.id, 30, 30)
    val cd = a.cooldownMs.toLong
    val early = toleranceOf(a.cooldownMs)
    assertEquals("the first", 1, arrive(m, p, a, Seq((0L, (1f, 0f)))))
    clock.advance(cd - early)
    assertEquals("one may come early by the tolerance", 1, arrive(m, p, a, Seq((0L, (1f, 0f)))))
    // The next is due two cooldowns after the first, and may come early by the tolerance again,
    // but no earlier than that: not a cooldown less the tolerance after the one that came early
    clock.advance(cd - early - 1)
    assertEquals("a millisecond before the tolerance allows", 0, arrive(m, p, a, Seq((0L, (1f, 0f)))))
    clock.advance(early + 1)
    assertEquals("refused a moment ago, it is taken once its time has come", 1, arrive(m, p, a, Seq((0L, (1f, 0f)))))
  })

  /** One of every kind of cast of several projectiles whose window closes before its next cast
    * could come: a projectile after the window is then a cast too soon, not the next one early. (A
    * primary's next may come at once, its tolerance being its whole cooldown.) */
  private def fansWithAWindowThatCloses: Seq[Attack] =
    attacks.filter(a => a.perCast >= 2 && windowOf(a.cooldownMs) + 1 < a.cooldownMs - toleranceOf(a.cooldownMs))
      .groupBy(a => (a.slot, a.cooldownMs, a.perCast)).toSeq.sortBy(_._1).map(_._2.head)

  @Test def theRestOfACastIsTakenUntilItsWindowsLastMillisecondAndNotOneAfter(): Unit = silently(Sim() { clock =>
    val m = new SimMatch(clock)
    val kinds = fansWithAWindowThatCloses
    assertTrue("the roster's fans and bursts", kinds.size >= 3)
    for (a <- kinds) {
      val window = windowOf(a.cooldownMs)
      val onTime = m.join(a.character.id, 30, 30)
      assertEquals(s"${a.name} (every ${a.cooldownMs}ms): its second, on the last millisecond of its cast's ${window}ms", 2,
        arrive(m, onTime, a, Seq((0L, a.headings(0)), (window, a.headings(1)))))
      m.instance.handler.removePlayer(onTime.getId)
      val late = m.join(a.character.id, 30, 30)
      assertEquals(s"${a.name} (every ${a.cooldownMs}ms): its second, a millisecond after its cast's ${window}ms", 1,
        arrive(m, late, a, Seq((0L, a.headings(0)), (window + 1, a.headings(1)))))
      m.instance.handler.removePlayer(late.getId)
    }
  })

  @Test def aRefusedShotIsWrittenWithWhatItsCastHadFiredOrHowLongSinceIt(): Unit = silently(Sim() { clock =>
    // What a problem report says of a shot the server refused (MatchJournal): which of the two it
    // was, and the numbers that say so, to the millisecond
    import com.gridgame.server.game.MatchJournal
    val m = new SimMatch(clock)
    val a = fansWithAWindowThatCloses.find(a => a.slot == AttackSlot.Q || a.slot == AttackSlot.E).get
    val p = m.join(a.character.id, 30, 30)
    def refusals: Vector[String] = m.instance.journal.lines.filter(_.contains("shot refused"))
    val window = windowOf(a.cooldownMs)
    assertEquals(a.perCast, arrive(m, p, a, a.headings.map(h => (0L, h))))
    assertEquals("nothing refused, nothing written", Vector.empty[String], refusals)
    // One more than the cast has, while its window is still open: to its last millisecond
    assertEquals(0, arrive(m, p, a, Seq((window, a.headings.head))))
    assertEquals(1, refusals.size)
    assertTrue(refusals.last, refusals.last.contains(
      s"shot refused (projectile_fire_rate): ${MatchJournal.slotName(a.slot)}'s cast has fired all ${a.perCast} it may"))
    // And after it: a cast too soon
    assertEquals(0, arrive(m, p, a, Seq((1L, a.headings.head))))
    assertEquals(2, refusals.size)
    assertTrue(refusals.last, refusals.last.contains(
      s"shot refused (projectile_fire_rate): ${MatchJournal.slotName(a.slot)} cast again ${window + 1}ms after the last, " +
        s"before ${a.cooldownMs - toleranceOf(a.cooldownMs)}ms (its ${a.cooldownMs}ms cooldown, less the network's tolerance)"))
  })

  // --- A phase, a dash and a barrier ---

  private def phases: Seq[(CharacterDef, Int, Int)] = CharacterDef.all.flatMap { c =>
    Seq(c.qAbility, c.eAbility).collectFirst {
      case a if a.castBehavior.isInstanceOf[PhaseShiftBuff] => (c, a.castBehavior.asInstanceOf[PhaseShiftBuff].durationMs, a.cooldownMs)
      case a if a.castBehavior.isInstanceOf[DashBuff] => (c, a.castBehavior.asInstanceOf[DashBuff].durationMs, a.cooldownMs)
    }
  }

  private def barriers: Seq[(CharacterDef, Int, Int)] = CharacterDef.all.flatMap { c =>
    Seq(c.qAbility, c.eAbility).collectFirst {
      case a if a.castBehavior.isInstanceOf[BarrierCast] => (c, a.castBehavior.asInstanceOf[BarrierCast].durationMs, a.cooldownMs)
    }
  }

  @Test def anHonestPhaseOrDashIsHonouredEveryTime(): Unit = silently(Sim() { clock =>
    val rnd = new scala.util.Random(5)
    val m = new SimMatch(clock)
    assertTrue(phases.size > 10)
    for ((c, durationMs, cooldownMs) <- phases) {
      val p = m.join(c.id, 30, 30)
      val start = clock.millis()
      for (k <- 0 until 6) {
        // Cast on the cooldown, its first update held up by anything up to the tolerance
        val arrives = start + k.toLong * cooldownMs + rnd.nextInt(Tolerance)
        clock.advance(arrives - clock.millis())
        assertFalse(s"${c.displayName}'s last phase is over before the next is due", p.isPhased)
        m.move(p, 30, 30, flags = 0x08, gapMs = 0)
        assertEquals(s"${c.displayName}'s phase ${k + 1}, taken as it arrives", clock.millis() + durationMs, p.getPhasedUntil)
      }
      m.instance.handler.removePlayer(p.getId)
    }
  })

  @Test def aPhaseOrDashComesNoSoonerThanItsCooldownLessTheTolerance(): Unit = silently(Sim() { clock =>
    val m = new SimMatch(clock)
    for ((c, durationMs, cooldownMs) <- phases) {
      val p = m.join(c.id, 30, 30)
      val length = 6L * cooldownMs
      val start = clock.millis()
      var began = 0
      var lastBegan = -1L
      val gaps = mutable.Buffer[Long]()
      // The flag on every update, twenty a second: honoured only when one is due
      while (clock.millis() - start < length) {
        val before = p.getPhasedUntil
        m.move(p, 30, 30, flags = 0x08, gapMs = 0)
        if (p.getPhasedUntil != before) {
          if (lastBegan >= 0) gaps += clock.millis() - lastBegan
          lastBegan = clock.millis()
          began += 1
        }
        clock.advance(50)
      }
      val soonest = cooldownMs - toleranceOf(cooldownMs)
      assertTrue(s"${c.displayName}: $began phases in ${length}ms", began >= 6)
      // On the first update from the moment it may, and they come every 50ms
      for (gap <- gaps) assertEquals(s"${c.displayName}: from one phase's start to the next's, of $soonest at the soonest",
        (soonest + 49) / 50 * 50, gap)
      m.instance.handler.removePlayer(p.getId)
    }
  })

  @Test def aBarrierIsRaisedOnItsCooldownAndNoSoonerThanTheToleranceAllows(): Unit = silently(Sim() { clock =>
    val m = new SimMatch(clock)
    assertTrue(barriers.size >= 5)
    for ((c, durationMs, cooldownMs) <- barriers) {
      val p = m.join(c.id, 30, 30)
      val soonest = cooldownMs - toleranceOf(cooldownMs)
      // The bit on every update, ten a second, as a client streams a barrier's facing: it goes up
      // when one is due, stays up its whole time, and goes up again no sooner than it may
      val raisedAt = mutable.Buffer[Long]()
      val start = clock.millis()
      while (clock.millis() - start < 5L * cooldownMs) {
        val was = p.getBarrierRaisedAt
        m.move(p, 30, 30, flags2 = 0x04, aimAngle = 1.0, gapMs = 0)
        if (p.getBarrierRaisedAt != was) {
          raisedAt += clock.millis() - start
          assertEquals(s"${c.displayName}'s barrier is up for its whole time", clock.millis() + durationMs, p.getBarrierUntil)
        }
        clock.advance(100)
      }
      assertEquals(s"${c.displayName}: raised at ${raisedAt.mkString(", ")}", (0 until raisedAt.size).map(_ * (((soonest + 99) / 100) * 100L)), raisedAt.toSeq)
      assertTrue(s"${c.displayName}: ${raisedAt.size} raises", raisedAt.size >= 5)
      m.instance.handler.removePlayer(p.getId)
    }
  })

  // --- A trap ---

  private def traps: Seq[(CharacterDef, Int, Byte, Int)] = CharacterDef.all.flatMap { c =>
    Seq((AttackSlot.Q, c.qAbility), (AttackSlot.E, c.eAbility)).collect {
      case (slot, a) if a.castBehavior.isInstanceOf[TrapCast] => (c, slot, a.castBehavior.asInstanceOf[TrapCast].trapType, a.cooldownMs)
    }
  }

  // Open cells beside (30, 30) to throw onto, one after another: by the time they come round
  // again the trap that lay there has run out or been pushed off by its owner's newer ones
  private val trapCells = Seq((31, 30), (32, 30), (33, 30), (30, 31), (30, 32), (30, 33), (29, 30), (28, 30))

  @Test def aTrapIsThrownOnItsCooldownAndNoSoonerThanTheToleranceAllows(): Unit = silently(Sim() { clock =>
    val rnd = new scala.util.Random(3)
    assertTrue(traps.size >= 7)
    for ((c, slot, trapType, cooldownMs) <- traps) {
      // An honest client: a throw every cooldown, each held up by anything up to the tolerance
      val honest = new SimMatch(clock)
      val thrower = honest.join(c.id, 30, 30)
      val start = clock.millis()
      for (k <- 0 until 6) {
        clock.advance(start + k.toLong * cooldownMs + rnd.nextInt(Tolerance) - clock.millis())
        val (x, y) = trapCells(k % trapCells.size)
        assertNotNull(s"${c.displayName}'s throw ${k + 1}, on its cooldown", honest.placeTrap(thrower, slot, trapType, x, y))
      }
      // And one asking ten times a second: the first, then each as early as the tolerance lets
      // it come before its place on the schedule, a cooldown on from the last
      val m = new SimMatch(clock)
      val p = m.join(c.id, 30, 30)
      val began = clock.millis()
      val placedAt = mutable.Buffer[Long]()
      while (clock.millis() - began < 5L * cooldownMs) {
        val (x, y) = trapCells(placedAt.size % trapCells.size)
        if (m.placeTrap(p, slot, trapType, x, y) != null) placedAt += clock.millis() - began
        clock.advance(100)
      }
      val early = toleranceOf(cooldownMs)
      val expected = 0L +: (1 until placedAt.size).map(k => (k.toLong * cooldownMs - early + 99) / 100 * 100)
      assertEquals(s"${c.displayName}'s trap, asked for all the time", expected, placedAt.toSeq)
      assertEquals("the first, and one early in each cooldown after", 6, placedAt.size)
    }
  })

  // --- A hit ---

  /** An ability that flies and strikes one enemy and nothing else: no blast, no splash, no pierce,
    * nothing that moves anyone, and not the primary's own projectile. */
  private def plainShots: Seq[(CharacterDef, Int, AbilityDef)] = CharacterDef.all.flatMap { c =>
    Seq((AttackSlot.Q, c.qAbility), (AttackSlot.E, c.eAbility)).filter { case (_, a) =>
      val d = ProjectileDef.get(a.projectileType)
      a.castBehavior == StandardProjectile && a.projectileType != c.primaryProjectileType && a.cooldownMs >= 4000 &&
        d.maxRange >= 8 && d.damage > 0 && d.damage < 60 && !d.isExplosive && d.pierceCount == 0 && !d.boomerang &&
        d.aoeOnHit.isEmpty && d.aoeOnMaxRange.isEmpty && !d.passesThroughPlayers && d.chargeDamageScaling.isEmpty &&
        d.onHitEffects.forall { case _: Slow | _: Burn | _: Poison | _: Root | _: Freeze | _: Stun => true; case _ => false }
    }.map { case (slot, a) => (c, slot, a) }
  }

  @Test def aHitTakesHalfOfWhatIsLeftOffItsAbilitysCooldown(): Unit = silently(Sim() { clock =>
    // As the shooter's own client does when it hears of the hit (Attacks.reduceAbilityCooldownOnHit):
    // were the server's clock not brought forward with it, the cast the client then shows ready
    // would be refused
    val shots = plainShots
    assertTrue(s"abilities that fly and strike (${shots.size})", shots.size >= 20)
    for ((c, slot, a) <- shots) {
      val m = new SimMatch(clock, respawns = false)
      val p = m.join(c.id, 20, 30)
      val target = m.join(CharacterId.Golem, 25, 30)
      val what = s"${c.displayName}'s ${a.name}"
      val full = target.getHealth
      var hitAt = -1L
      m.clockwork.afterEach = _ => if (hitAt < 0 && target.getHealth < full) hitAt = clock.millis()
      val castAt = clock.millis()
      assertEquals(what, 1, m.fire(p, slot, a.projectileType, Seq((1f, 0f))).size)
      while (m.instance.projectileManager.size > 0) m.advance(30)
      assertTrue(s"$what struck", hitAt > castAt)
      val cd = a.cooldownMs.toLong
      val left = cd - (hitAt - castAt)
      // Its cooldown now counts from half of what was left sooner, and the next cast may come
      // early by the tolerance, as any may
      val readyAt = castAt - left / 2 + cd - toleranceOf(cd)
      assertTrue(s"$what: the hit came inside the cooldown", left > 0 && readyAt > clock.millis())
      clock.advance(readyAt - 1 - clock.millis())
      assertEquals(s"$what: a millisecond before half of what was left has gone", 0, m.fire(p, slot, a.projectileType, Seq((-1f, 0f))).size)
      clock.advance(1)
      assertEquals(s"$what: half of what was left taken off", 1, m.fire(p, slot, a.projectileType, Seq((-1f, 0f))).size)
    }
  })

  /** Cast the ability so that it strikes `target` once, and hold its next cast to half of what
    * was left of its cooldown when it struck, to the millisecond. */
  private def assertHalvedByTheStrike(what: String, m: SimMatch, p: Player, target: Player, slot: Int, a: AbilityDef,
                                      heading: (Float, Float)): Unit = {
    val clock = m.clock
    val before = target.getHealth
    var hitAt = -1L
    m.clockwork.afterEach = _ => if (hitAt < 0 && target.getHealth < before) hitAt = clock.millis()
    val castAt = clock.millis()
    assertEquals(what, 1, m.fire(p, slot, a.projectileType, Seq(heading)).size)
    while (m.instance.projectileManager.size > 0) m.advance(30)
    assertTrue(s"$what struck", hitAt > castAt)
    val cd = a.cooldownMs.toLong
    val left = cd - (hitAt - castAt)
    val readyAt = castAt - left / 2 + cd - toleranceOf(cd)
    assertTrue(s"$what: struck inside its cooldown", left > 0 && readyAt > clock.millis())
    clock.advance(readyAt - 1 - clock.millis())
    assertEquals(s"$what: a millisecond before half of what was left has gone", 0, m.fire(p, slot, a.projectileType, Seq(heading)).size)
    clock.advance(1)
    assertEquals(s"$what: half of what was left taken off", 1, m.fire(p, slot, a.projectileType, Seq(heading)).size)
  }

  private def abilities: Seq[(CharacterDef, Int, AbilityDef, ProjectileDef)] = for {
    c <- CharacterDef.all
    (slot, a) <- Seq((AttackSlot.Q, c.qAbility), (AttackSlot.E, c.eAbility))
    if a.cooldownMs >= 4000 && a.projectileType != c.primaryProjectileType
  } yield (c, slot, a, ProjectileDef.get(a.projectileType))

  @Test def aKillingBlowABlastAndASplashEachTakeHalfOffTooAsAHitDoes(): Unit = silently(Sim() { clock =>
    // The shooter's client halves what is left on every strike it hears of, whatever kind: the
    // blow that kills, a blast's reach, a slam's splash
    val (kc, kslot, ka) = plainShots.head
    val killing = new SimMatch(clock, respawns = false)
    val doomed = killing.join(CharacterId.Golem, 25, 30)
    doomed.setHealth(1)
    assertHalvedByTheStrike(s"${kc.displayName}'s ${ka.name}, killing", killing, killing.join(kc.id, 20, 30), doomed, kslot, ka, (1f, 0f))
    assertTrue(doomed.isDead)

    // A blast: thrown at a wall, with someone beside where it goes off and out of its own way
    val blasts = abilities.filter { case (_, _, a, d) =>
      a.castBehavior == StandardProjectile && d.maxRange >= 8 && d.hitRadius <= 1.9f && !d.passesThroughWalls && d.ricochetCount == 0 &&
        d.aoeOnHit.isEmpty && d.aoeOnMaxRange.isEmpty && d.explosionConfig.exists(x => x.edgeDamage > 0 && x.blastRadius >= 3f)
    }
    assertTrue(s"abilities that blast (${blasts.size})", blasts.size >= 3)
    for ((c, slot, a, _) <- blasts.take(6)) {
      val m = new SimMatch(clock, respawns = false)
      m.world.setTile(26, 30, Tile.Wall)
      val bystander = m.join(CharacterId.Golem, 25, 32)
      assertHalvedByTheStrike(s"${c.displayName}'s ${a.name}, its blast", m, m.join(c.id, 20, 30), bystander, slot, a, (1f, 0f))
    }

    // A splash: a slam, with someone inside it
    val slams = abilities.filter { case (_, _, a, d) =>
      a.castBehavior.isInstanceOf[GroundSlam] && !d.hastesCaster && d.aoeOnMaxRange.exists(sp => sp.damage > 0 && sp.radius >= 2.5f) &&
        d.explosionConfig.forall(x => x.centerDamage == 0 && x.edgeDamage == 0)
    }
    assertTrue(s"slams that splash (${slams.size})", slams.size >= 3)
    for ((c, slot, a, _) <- slams.take(6)) {
      val m = new SimMatch(clock, respawns = false)
      val caught = m.join(CharacterId.Golem, 22, 30)
      assertHalvedByTheStrike(s"${c.displayName}'s ${a.name}, its splash", m, m.join(c.id, 20, 30), caught, slot, a, (0f, 0f))
    }
  })

  @Test def aMissTakesNothingOffAndAHitOfThePrimarysNothingOffAnAbility(): Unit = silently(Sim() { clock =>
    val (c, slot, a) = plainShots.head
    val m = new SimMatch(clock, respawns = false)
    val p = m.join(c.id, 20, 30)
    val target = m.join(CharacterId.Golem, 23, 30)
    val full = target.getHealth
    val castAt = clock.millis()
    assertEquals(1, m.fire(p, slot, a.projectileType, Seq((-1f, 0f))).size) // away from them
    // The primary strikes them meanwhile
    assertEquals(1, m.fire(p, AttackSlot.PRIMARY, c.primaryProjectileType, Seq((1f, 0f))).size)
    while (m.instance.projectileManager.size > 0) m.advance(30)
    assertTrue("the primary struck", target.getHealth < full)
    val readyAt = castAt + a.cooldownMs - toleranceOf(a.cooldownMs)
    clock.advance(readyAt - 1 - clock.millis())
    assertEquals("the whole cooldown, less only the tolerance", 0, m.fire(p, slot, a.projectileType, Seq((-1f, 0f))).size)
    clock.advance(1)
    assertEquals(1, m.fire(p, slot, a.projectileType, Seq((-1f, 0f))).size)
  })

  // --- R ---

  @Test def rIsTakenOnItsMinuteAndNoSoonerThanTheToleranceAllows(): Unit = silently(Sim() { clock =>
    val m = new SimMatch(clock)
    val p = m.join(CharacterId.Spaceman, 30, 30)
    def pressR(): Boolean = {
      m.clearSent()
      m.instance.handler.processPacket(new CooldownResetPacket(1, p.getId, CooldownResetAction.REQUEST, 0), null, null)
      !m.tcpSent(p).exists { case r: CooldownResetPacket => r.getAction == CooldownResetAction.REJECTED; case _ => false }
    }
    val minute = Constants.COOLDOWN_RESET_MS.toLong
    val soonest = minute - toleranceOf(minute)
    // Asked for ten times a second through five minutes of a match
    val taken = mutable.Buffer[Long]()
    while (m.at < 5 * minute) {
      if (pressR()) taken += m.at
      clock.advance(100)
    }
    assertEquals("from the match's start, then from the last", (1 to taken.size).map(_ * soonest), taken.toSeq)
    assertEquals(((5 * minute - 1) / soonest).toInt, taken.size)
  })

  @Test def aRefusedRSaysHowLongItsMinuteHasLeft(): Unit = silently(Sim() { clock =>
    val m = new SimMatch(clock)
    val p = m.join(CharacterId.Spaceman, 30, 30)
    clock.advance(12345)
    m.clearSent()
    m.instance.handler.processPacket(new CooldownResetPacket(1, p.getId, CooldownResetAction.REQUEST, 0), null, null)
    val refusal = m.tcpSent(p).collect { case r: CooldownResetPacket => r }
    assertEquals(1, refusal.size)
    assertEquals(CooldownResetAction.REJECTED, refusal.head.getAction)
    assertEquals("to the millisecond", Constants.COOLDOWN_RESET_MS - 12345, refusal.head.getMsLeft)
  })
}
