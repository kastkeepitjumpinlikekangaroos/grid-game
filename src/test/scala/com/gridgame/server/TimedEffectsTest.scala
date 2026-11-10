package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.GameClock
import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

import scala.collection.mutable

/**
 * Health over time, to the millisecond: a burn's bites, a poison's ticks, regeneration and the
 * wait for a respawn, each watched from its first moment to its last on a clock of the test's own
 * (SimKit). On the system's clock these were looked at through a shortened effect and a sleep, one
 * tick at a time: nothing held a burn to biting once a tick of its own (dropped, the line that
 * notes its last bite went unnoticed, and a burn bit five times a second), nor regeneration to its
 * pace.
 */
class TimedEffectsTest {
  private val PlayerTickMs = 200L

  /** What a player's health did over `ms`, a player tick at a time: (ms since now, change). */
  private def healthChanges(m: SimMatch, p: Player, ms: Long): Seq[(Long, Int)] = {
    val from = m.at
    val changes = mutable.Buffer[(Long, Int)]()
    var last = p.getHealth
    while (m.at - from < ms) {
      // To the next player tick, which falls on the 200s of the match's clock
      m.advance(PlayerTickMs - m.at % PlayerTickMs)
      if (p.getHealth != last) { changes += ((m.at - from, p.getHealth - last)); last = p.getHealth }
    }
    changes.toSeq
  }

  /** When a burn put on `sinceTick` ms after a player tick bites, by the rule: on the first player
    * tick a whole `tickMs` after it was lit or last bit, while it lasts. Ms since it was lit. */
  private def burnBites(sinceTick: Long, durationMs: Int, tickMs: Int): Seq[Long] = {
    val bites = mutable.Buffer[Long]()
    var last = 0L
    var t = PlayerTickMs - sinceTick
    while (t < durationMs) {
      if (t >= last + tickMs) { bites += t; last = t }
      t += PlayerTickMs
    }
    bites.toSeq
  }

  private def everyBurn: Seq[(String, Burn)] =
    (CharacterDef.all.flatMap(c => Seq(c.primaryProjectileType, c.qAbility.projectileType, c.eAbility.projectileType))
      .distinct.map(ProjectileDef.get).flatMap(d => d.onHitEffects.collect { case b: Burn => d.name -> b }) ++
      TrapDef.all.flatMap(t => t.effects.collect { case b: Burn => t.name -> b })).distinct

  private def everyPoison: Seq[(String, Poison)] =
    (CharacterDef.all.flatMap(c => Seq(c.primaryProjectileType, c.qAbility.projectileType, c.eAbility.projectileType))
      .distinct.map(ProjectileDef.get).flatMap(d => d.onHitEffects.collect { case p: Poison => d.name -> p }) ++
      TrapDef.all.flatMap(t => t.effects.collect { case p: Poison => t.name -> p })).distinct

  // --- Burns ---

  @Test def aBurnBitesOnceEveryTickOfItsOwn(): Unit = Sim() { clock =>
    val m = new SimMatch(clock)
    val pyro = m.join(CharacterId.Pyromancer, 10, 30)
    val target = m.join(CharacterId.Gladiator, 40, 30)
    target.applyBurn(20, 4000, 800, pyro.getId)
    // Five ticks of 800ms in its four seconds: 4 a bite. On the 200ms player tick each comes
    // round exactly when it is due, and the fifth would fall on the very moment it goes out
    assertEquals(Seq((800L, -4), (1600L, -4), (2400L, -4), (3200L, -4)), healthChanges(m, target, 4000))
    assertFalse(target.isBurning)
  }

  @Test def aBurnsBitesComeNoSoonerThanItsTickHoweverThePlayerTickFalls(): Unit =
    // Lit at every moment of the 200ms between two player ticks
    for (sinceTick <- Seq(0L, 1L, 49L, 50L, 51L, 100L, 150L, 199L)) Sim() { clock =>
      val m = new SimMatch(clock)
      val pyro = m.join(CharacterId.Pyromancer, 10, 30)
      val target = m.join(CharacterId.Gladiator, 40, 30)
      m.advance(1000 + sinceTick)
      target.applyBurn(15, 3000, 750, pyro.getId)
      val bites = healthChanges(m, target, 3000).filter(_._2 < 0)
      assertEquals(s"lit ${sinceTick}ms after a player tick", burnBites(sinceTick, 3000, 750).map(_ -> -3), bites)
      for (Seq(a, b) <- bites.map(_._1).sliding(2)) assertTrue(s"bites at ${a}ms and ${b}ms", b - a >= 750)
    }

  @Test def everyBurnInTheGameDealsWhatItsRuleSays(): Unit = {
    assertTrue("the roster burns", everyBurn.size >= 8)
    for ((name, burn @ Burn(total, durationMs, tickMs)) <- everyBurn; sinceTick <- Seq(0L, 70L, 130L, 199L)) Sim() { clock =>
      val m = new SimMatch(clock)
      val owner = m.join(CharacterId.Pyromancer, 10, 30)
      // Someone with the health to see the Fire Rune's burn out
      val target = m.join(CharacterId.Golem, 40, 30)
      m.advance(600 + sinceTick)
      val before = target.getHealth
      target.applyBurn(total, durationMs, tickMs, owner.getId)
      val bites = healthChanges(m, target, durationMs).filter(_._2 < 0)
      val what = s"$name's $burn, lit ${sinceTick}ms after a player tick"
      val each = total / (durationMs / tickMs)
      assertEquals(what, burnBites(sinceTick, durationMs, tickMs).map(_ -> -each), bites)
      assertEquals(s"$what: all of it off the target's health, and nothing healed back while it burned",
        before - each * bites.size, target.getHealth)
      assertTrue(s"$what deals no more than it says", each * bites.size <= total)
      // What the character screen and the ability's description say it takes (Burn.dealt), however
      // the player tick fell
      assertEquals(s"$what: what a player is told it takes", burn.dealt, each * bites.size)
      assertEquals(each, burn.damagePerTick)
      assertFalse(s"$what is out once its time is up", target.isBurning)
    }
  }

  @Test def theBurnsTheDesignNotesNameDealWhatTheySay(): Unit = {
    // design/combat.md, *A burn does not deal its listed total*
    def dealt(total: Int, durationMs: Int, tickMs: Int): Int = Sim() { clock =>
      val m = new SimMatch(clock)
      val owner = m.join(CharacterId.Pyromancer, 10, 30)
      val target = m.join(CharacterId.Golem, 40, 30)
      target.applyBurn(total, durationMs, tickMs, owner.getId)
      -healthChanges(m, target, durationMs).filter(_._2 < 0).map(_._2).sum
    }
    assertEquals(9, dealt(15, 3000, 750))
    assertEquals(9, dealt(12, 3000, 750))
    assertEquals(16, dealt(20, 4000, 800))
  }

  @Test def aBurnThatKillsIsItsOwnersKillAndBitesNoMore(): Unit = Sim() { clock =>
    val m = new SimMatch(clock, respawns = false)
    val pyro = m.join(CharacterId.Pyromancer, 10, 30)
    val target = m.join(CharacterId.Gladiator, 40, 30)
    target.setHealth(7)
    target.applyBurn(20, 4000, 800, pyro.getId)
    m.advance(1600)
    assertTrue("4, then the 3 it had left", target.isDead)
    assertEquals(1, m.instance.killTracker.getKills(pyro.getId))
    assertEquals(1, m.instance.killTracker.getDeaths(target.getId))
    m.advance(4000)
    assertEquals("a death is scored once, however long the burn had to run", 1, m.instance.killTracker.getDeaths(target.getId))
  }

  // --- Poisons ---

  @Test def everyPoisonInTheGameDealsExactlyItsTotal(): Unit = {
    assertTrue("the roster poisons", everyPoison.size >= 5)
    for ((name, poison @ Poison(total, durationMs, tickMs)) <- everyPoison; sinceTick <- Seq(0L, 70L, 130L, 199L)) Sim() { clock =>
      val m = new SimMatch(clock)
      val owner = m.join(CharacterId.PlagueDoctor, 10, 30)
      val target = m.join(CharacterId.Golem, 40, 30)
      m.advance(600 + sinceTick)
      val before = target.getHealth
      target.applyPoison(total, durationMs, tickMs, owner.getId)
      val what = s"$name's $poison, landed ${sinceTick}ms after a player tick"
      // Each tick falls due a whole tickMs after the one before was due, and lands on the first
      // player tick from then: the last no later than a player tick after the poison's time
      val bites = healthChanges(m, target, durationMs + PlayerTickMs).filter(_._2 < 0)
      val ticks = durationMs / tickMs
      assertEquals(s"$what: a bite a tick", ticks, bites.size)
      assertEquals(s"$what: its total, to the point", total, -bites.map(_._2).sum)
      for ((at, i) <- bites.map(_._1).zipWithIndex) {
        val due = (i + 1L) * tickMs
        assertTrue(s"$what: tick ${i + 1} at ${at}ms, due at ${due}ms", at >= due && at < due + PlayerTickMs)
      }
      assertEquals(before - total, target.getHealth)
      assertFalse(s"$what is over with its last tick", target.isPoisoned)
    }
  }

  @Test def aPoisonsLastTickSaysItIsOverEvenWhenItTakesNothing(): Unit = Sim() { clock =>
    // A tick of no damage is still a tick: the update for the last one is how clients hear the
    // poison is over, and nothing else says so
    val m = new SimMatch(clock)
    val owner = m.join(CharacterId.PlagueDoctor, 10, 30)
    val target = m.join(CharacterId.Gladiator, 40, 30)
    target.applyPoison(0, 400, 200, owner.getId)
    m.clearSent()
    m.advance(400)
    assertFalse(target.isPoisoned)
    val told = m.updatesAbout(m.udpSent(owner), target)
    assertEquals("an update a tick", 2, told.size)
    assertEquals("poisoned after the first", 0x02, told.head.getEffectFlags2 & 0x02)
    assertEquals("and over after the last", 0, told.last.getEffectFlags2 & 0x02)
  }

  @Test def aNewPoisonTakesTheOldOnesPlace(): Unit = Sim() { clock =>
    val m = new SimMatch(clock)
    val owner = m.join(CharacterId.PlagueDoctor, 10, 30)
    val other = m.join(CharacterId.Spider, 12, 30)
    val target = m.join(CharacterId.Golem, 40, 30)
    val full = target.getHealth
    target.applyPoison(40, 2000, 500, owner.getId)
    m.advance(1000) // two ticks of 10
    assertEquals(full - 20, target.getHealth)
    target.applyPoison(12, 1500, 500, other.getId)
    assertEquals(other.getId, target.getPoisonOwnerId)
    m.advance(1600)
    assertEquals("the new one's 12, and none of the 20 the old one had left", full - 32, target.getHealth)
    assertFalse(target.isPoisoned)
  }

  // --- Regeneration ---

  @Test def everyoneComesBackFromNothingInFiftySeconds(): Unit = {
    // A share of their own maximum a second, so the frailest and the toughest take as long
    val byHealth = CharacterDef.all.groupBy(_.maxHealth).toSeq.sortBy(_._1).map(_._2.head)
    assertTrue(byHealth.size >= 10)
    for (c <- byHealth) Sim() { clock =>
      val m = new SimMatch(clock)
      val p = m.join(c.id, 30, 30)
      p.setHealth(1)
      val changes = healthChanges(m, p, 52000)
      assertEquals(s"${c.displayName} is back to full", p.getMaxHealth, p.getHealth)
      assertTrue(s"${c.displayName} only ever gained", changes.forall(_._2 > 0))
      val took = changes.last._1
      assertTrue(s"${c.displayName} (${c.maxHealth} HP) took ${took}ms", took > 49000 && took <= 50000)
      // And evenly: by half way, half of it
      val halfWay = 1 + changes.takeWhile(_._1 <= 25000).map(_._2).sum
      assertTrue(s"${c.displayName} had $halfWay of ${c.maxHealth} at 25s", Math.abs(halfWay - c.maxHealth / 2) <= 2)
    }
  }

  @Test def aPointComesBackTheMomentItIsEarned(): Unit = Sim() { clock =>
    // 125 HP: half a point a player tick, so a whole one on every second tick exactly
    val m = new SimMatch(clock)
    val c = CharacterDef.all.find(_.maxHealth == 125).get
    val p = m.join(c.id, 30, 30)
    p.setHealth(p.getMaxHealth - 2)
    assertEquals(Seq((400L, 1), (800L, 1)), healthChanges(m, p, 2000))
  }

  @Test def nothingIsSavedUpAtFullHealth(): Unit = Sim() { clock =>
    // 75 HP: 0.3 a player tick, so the first point back takes four ticks. Standing at full health
    // banks none of them, and neither does a point just healed with some left over
    val m = new SimMatch(clock)
    val c = CharacterDef.all.find(_.maxHealth == 75).get
    val p = m.join(c.id, 30, 30)
    m.advance(3 * PlayerTickMs)
    p.setHealth(p.getMaxHealth - 1)
    assertEquals("the first point after three ticks at full health", Seq((800L, 1)), healthChanges(m, p, 1600))
    // That point left 0.2 over, and the ticks since at full health are worth nothing
    p.setHealth(p.getMaxHealth - 1)
    assertEquals("and the next takes as long", Seq((800L, 1)), healthChanges(m, p, 1600))
  }

  @Test def whatWasEarnedBeforeABurnIsGoneAfterIt(): Unit = Sim() { clock =>
    val m = new SimMatch(clock)
    val c = CharacterDef.all.find(_.maxHealth == 75).get
    val p = m.join(c.id, 30, 30)
    val pyro = m.join(CharacterId.Pyromancer, 10, 30)
    p.setHealth(40)
    m.advance(3 * PlayerTickMs) // 0.9 of a point earned
    p.applyBurn(1, 1000, 2000, pyro.getId) // a burn that never bites
    // Out on the tick a second on, which is the first of the four a point then takes
    val changes = healthChanges(m, p, 1000 + 800)
    assertEquals("nothing while it burns, then a point after four ticks, not one", Seq((1600L, 1)), changes)
  }

  @Test def theDeadDoNotRegenerate(): Unit = Sim() { clock =>
    val m = new SimMatch(clock, respawns = false)
    val p = m.join(CharacterId.Gladiator, 30, 30)
    p.damage(p.getHealth)
    m.advance(20000)
    assertEquals(0, p.getHealth)
    assertTrue(p.isDead)
  }

  // --- The next life ---

  @Test def theDeadWaitThreeSecondsForTheirNextLife(): Unit = Sim() { clock =>
    val m = new SimMatch(clock)
    val killer = m.join(CharacterId.Spaceman, 10, 30)
    val victim = m.join(CharacterId.Gladiator, 14, 30)
    m.advance(1000)
    victim.setHealth(1)
    m.launch(killer, ProjectileType.NORMAL, 1f, 0f)
    while (!victim.isDead) m.advance(1)
    val diedAt = m.at
    m.clearSent()
    m.advance(Constants.RESPAWN_DELAY_MS - 1)
    assertTrue("still dead a millisecond before", victim.isDead)
    m.advance(1)
    assertFalse("alive on the moment", victim.isDead)
    assertEquals(victim.getMaxHealth, victim.getHealth)
    assertEquals(diedAt + Constants.RESPAWN_DELAY_MS, m.at)
    val respawn = m.tcpSent(killer).collect { case g: GameEventPacket if g.getEventType == GameEvent.RESPAWN => g }
    assertEquals("everyone is told of it, once", 1, respawn.size)
    assertEquals(victim.getId, respawn.head.getPlayerId)
  }

  @Test def practicesDeadWaitASecond(): Unit = Sim() { clock =>
    val m = new SimMatch(clock, practice = true)
    val killer = m.join(CharacterId.Spaceman, 10, 30)
    val victim = m.join(CharacterId.Gladiator, 14, 30)
    victim.setHealth(1)
    m.launch(killer, ProjectileType.NORMAL, 1f, 0f)
    while (!victim.isDead) m.advance(1)
    m.advance(999)
    assertTrue(victim.isDead)
    m.advance(1)
    assertFalse(victim.isDead)
  }

  @Test def aNewLifeCarriesNothingOver(): Unit = Sim() { clock =>
    val m = new SimMatch(clock)
    val pyro = m.join(CharacterId.Pyromancer, 10, 30)
    val victim = m.join(CharacterId.Gladiator, 40, 30)
    victim.applyBurn(15, 3000, 750, pyro.getId)
    victim.applyPoison(48, 6000, 500, pyro.getId)
    victim.trySlow(5000, 0.5f)
    victim.setShieldUntil(GameClock.now() + 5000)
    victim.setGemBoostUntil(GameClock.now() + 5000)
    victim.damage(victim.getHealth)
    m.advance(Constants.RESPAWN_DELAY_MS)
    assertFalse(victim.isDead)
    assertFalse("no burn", victim.isBurning)
    assertFalse("no poison", victim.isPoisoned)
    assertFalse("no slow", victim.isSlowed)
    assertFalse("no shield", victim.hasShield)
    assertFalse("no gem", victim.hasGemBoost)
    // And the poison that was to run three seconds more takes nothing from the new life
    val full = victim.getHealth
    m.advance(6000)
    assertEquals(full, victim.getHealth)
  }

  // --- Holds, slows and boosts ---

  private def someone(): Player = new Player(java.util.UUID.randomUUID(), "p", new Position(5, 5), 0)

  @Test def everyHoldSlowAndBoostIsOverOnTheMillisecondItRunsOut(): Unit = Sim() { clock =>
    // On for the whole of its time and not a millisecond of the next: each of them, by everything
    // that says a player has it
    val length = 700L
    val effects: Seq[(String, Player => Unit, Seq[(String, Player => Boolean)])] = Seq(
      ("a freeze", p => assertTrue(p.tryFreeze(length)), Seq[(String, Player => Boolean)]("frozen" -> (_.isFrozen))),
      ("a stun", p => assertTrue(p.tryStun(length)), Seq[(String, Player => Boolean)]("stunned" -> (_.isStunned), "held" -> (_.isFrozen))),
      ("a launch", p => assertTrue(p.tryLaunch(length)),
        Seq[(String, Player => Boolean)]("in the air" -> (_.isAirborne), "stunned" -> (_.isStunned), "held" -> (_.isFrozen))),
      ("a root", p => assertTrue(p.tryRoot(length)), Seq[(String, Player => Boolean)]("rooted" -> (_.isRooted))),
      ("a slow", p => assertTrue(p.trySlow(length, 0.5f)), Seq[(String, Player => Boolean)]("slowed" -> (_.isSlowed))),
      ("a phase", p => p.setPhasedUntil(GameClock.now() + length), Seq[(String, Player => Boolean)]("phased" -> (_.isPhased))),
      ("a haste", p => p.setSpeedBoostUntil(GameClock.now() + length), Seq[(String, Player => Boolean)]("hasted" -> (_.hasSpeedBoost))),
      ("a shield", p => p.setShieldUntil(GameClock.now() + length), Seq[(String, Player => Boolean)]("shielded" -> (_.hasShield))),
      ("a gem", p => p.setGemBoostUntil(GameClock.now() + length), Seq[(String, Player => Boolean)]("boosted" -> (_.hasGemBoost))),
      ("a barrier", p => p.raiseBarrier(GameClock.now(), length.toInt, 0f), Seq[(String, Player => Boolean)]("behind it" -> (_.hasBarrier))))
    for ((what, land, says) <- effects) {
      val p = someone()
      land(p)
      for ((how, on) <- says) assertTrue(s"$what: $how as it lands", on(p))
      clock.advance(length - 1)
      for ((how, on) <- says) assertTrue(s"$what: $how in its last millisecond", on(p))
      clock.advance(1)
      for ((how, on) <- says) assertFalse(s"$what: not $how once its ${length}ms are up", on(p))
    }
  }

  @Test def aHoldKeepsEveryOtherOffForItsLengthAndASecondAndAHalfMore(): Unit = Sim() { clock =>
    // Nobody is held twice running: from the moment a freeze, a stun, a launch or a root lands
    // until 1500ms after it ends, no other lands, and no slow. A slow itself keeps nothing off
    val immunity = Constants.CC_IMMUNITY_MS.toLong
    assertEquals(1500L, immunity)
    val length = 600L
    def nothingLands(what: String, p: Player): Unit = {
      val (frozen, rooted, air) = (p.getFrozenUntil, p.getRootedUntil, p.getAirborneUntil)
      assertTrue(s"$what: immune", p.isCCImmune)
      assertFalse(s"$what: no freeze", p.tryFreeze(5000))
      assertFalse(s"$what: no stun", p.tryStun(5000))
      assertFalse(s"$what: no launch", p.tryLaunch(5000))
      assertFalse(s"$what: no root", p.tryRoot(5000))
      assertFalse(s"$what: no slow", p.trySlow(5000, 0.5f))
      assertEquals(s"$what: and nothing of them took", (frozen, rooted, air, false), (p.getFrozenUntil, p.getRootedUntil, p.getAirborneUntil, p.isSlowed))
    }
    val holds = Seq[(String, Player => Boolean)](("a freeze", _.tryFreeze(length)), ("a stun", _.tryStun(length)),
      ("a launch", _.tryLaunch(length)), ("a root", _.tryRoot(length)))
    for ((what, hold) <- holds; (again, next) <- holds) {
      val p = someone()
      assertTrue(hold(p))
      nothingLands(s"as $what lands", p)
      clock.advance(length - 1)
      nothingLands(s"in the last millisecond of $what", p)
      clock.advance(1)
      nothingLands(s"as $what ends", p)
      clock.advance(immunity - 1)
      nothingLands(s"${immunity - 1}ms after $what", p)
      clock.advance(1)
      assertFalse(s"${immunity}ms after $what: immune no longer", p.isCCImmune)
      assertTrue(s"${immunity}ms after $what: $again lands", next(p))
    }
    val slowed = someone()
    assertTrue(slowed.trySlow(length, 0.5f))
    assertFalse("a slow keeps nothing off", slowed.isCCImmune)
    assertTrue(slowed.tryRoot(length))
    // And nothing lands on the phased
    val phased = someone()
    phased.setPhasedUntil(GameClock.now() + 1000)
    assertFalse(phased.tryFreeze(500) || phased.tryStun(500) || phased.tryLaunch(500) || phased.tryRoot(500) || phased.trySlow(500, 0.5f))
    assertFalse(phased.isFrozen || phased.isRooted || phased.isSlowed || phased.isAirborne)
  }

  @Test def aNewLifeBeginsAwayFromTheLivingAndTheDeadAreInNobodysWay(): Unit = Sim() { clock =>
    // A life begins on a spawn point fifty cells from everyone alive, where the map has one; a body
    // lying on a spawn point keeps nobody off it
    val world = new WorldData("two spawns", 120, 120, Array.fill(120, 120)(Tile.Grass: Tile),
      Seq(new Position(10, 10), new Position(100, 100)))
    val m = new SimMatch(clock, world)
    val living = m.join(CharacterId.Gladiator, 10, 10)
    val first = m.join(CharacterId.Gladiator, 60, 60)
    val body = m.join(CharacterId.Gladiator, 100, 100)
    first.damage(first.getHealth)
    m.advance(1000)
    body.damage(body.getHealth)
    m.advance(Constants.RESPAWN_DELAY_MS - 1000)
    assertFalse(first.isDead)
    assertTrue("still down, a second behind", body.isDead)
    assertEquals("the spawn point the living are far from, though a body lies on it", (100, 100), m.at(first))
    m.advance(1000)
    assertFalse(body.isDead)
    def apart(a: Player, b: Player): Double = Math.hypot(m.at(a)._1 - m.at(b)._1, m.at(a)._2 - m.at(b)._2)
    assertTrue(s"with both spawn points taken, somewhere fifty cells from everyone: ${m.at(body)}",
      apart(body, living) >= 50 && apart(body, first) >= 50)
    assertEquals("the living are left where they were", ((10, 10), (100, 100)), (m.at(living), m.at(first)))
  }

  @Test def aNewLifeIsHeldByNothingAndHoldsNothingOfTheLast(): Unit = Sim() { clock =>
    // Every hold, boost and cast the last life had running, and what it had picked up: the client
    // starts each life without them, and a server that kept one (a stun, a barrier still up, a
    // phase) played a different life from the one its player saw
    val m = new SimMatch(clock)
    val p = m.join(CharacterId.Gladiator, 40, 30)
    assertTrue(m.move(p, 40, 30, gapMs = 0))
    assertTrue(m.move(p, 41, 30, gapMs = 100))
    assertEquals(Direction.Right, p.getDirection)
    val later = GameClock.now() + 20000
    p.setFrozenUntil(later)
    p.setStunnedUntil(later)
    p.setRootedUntil(later)
    p.setAirborne(GameClock.now(), later)
    p.setSpeedBoostUntil(later)
    p.setPhasedUntil(later)
    p.raiseBarrier(GameClock.now(), 20000, 1f)
    p.addRegenAccumulator(0.9)
    m.instance.itemManager.addToInventory(p.getId, new Item(77, 0, 0, ItemType.Heart))
    p.setPhasedUntil(0) // so it can be struck down
    p.setHealth(1)
    p.damage(1)
    p.setPhasedUntil(later)
    assertTrue(p.isDead)
    m.advance(Constants.RESPAWN_DELAY_MS)
    assertFalse(p.isDead)
    assertFalse("not frozen", p.isFrozen)
    assertFalse("not stunned", p.isStunned)
    assertFalse("not rooted", p.isRooted)
    assertFalse("not in the air", p.isAirborne)
    assertFalse("not hasted", p.hasSpeedBoost)
    assertFalse("not phased", p.isPhased)
    assertFalse("its barrier down", p.hasBarrier)
    assertEquals("and ready to be raised", 0L, p.getBarrierRaisedAt)
    assertEquals("facing down, as every life begins", Direction.Down, p.getDirection)
    assertEquals("nothing banked toward its next point of health", 0.0, p.getRegenAccumulator, 0.0)
    assertTrue("its pockets empty", m.instance.itemManager.getInventory(p.getId).isEmpty)
  }
}
