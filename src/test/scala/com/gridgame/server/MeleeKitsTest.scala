package com.gridgame.server

import com.gridgame.common.model._
import org.junit.Assert._
import org.junit.Test

/**
 * The melee and skirmisher kits' effects as the server lands them (Plan 5a): the combinations
 * nothing else covers, more than one effect on a hit, and the closers that have to land on a
 * ranged character walking away.
 */
class MeleeKitsTest {
  private val m = new TestMatch()

  /** Fly a projectile from its owner along +x until it is gone. */
  private def shoot(owner: Player, pType: Byte): Unit =
    m.tickUntilGone(m.launch(owner, pType, 1f, 0f))

  /** A slam: thrown down where its caster stands. */
  private def slam(owner: Player, pType: Byte): Unit =
    m.tickUntilGone(m.launch(owner, pType, 0f, 0f))

  // --- Rings, slams and holds ---

  @Test def theBerserkersSpinningAxesSlow(): Unit = {
    val berserker = m.join(CharacterId.Berserker, 10, 30)
    val target = m.join(CharacterId.Soldier, 13, 30)
    shoot(berserker, ProjectileType.AXE_SPIN)
    assertTrue(target.isSlowed)
    assertEquals(0.5f, target.getSlowMultiplier, 0.001f)
  }

  @Test def theSamuraisCrescentCutKnocksUpAndStuns(): Unit = {
    val samurai = m.join(CharacterId.Samurai, 10, 30)
    val target = m.join(CharacterId.Soldier, 13, 30)
    val before = System.currentTimeMillis()
    shoot(samurai, ProjectileType.SWORD_WAVE)
    assertTrue("held", target.isFrozen)
    assertTrue("as a stun", target.getStunnedUntil > System.currentTimeMillis())
    assertTrue("in the air", target.isAirborne)
    assertEquals("for as long as the stun", target.getStunnedUntil, target.getAirborneUntil)
    assertTrue("for a second", target.getAirborneUntil - before >= 1000)
    assertEquals("everyone is told", 0x08, m.instance.playerFlags2(target) & 0x08)
  }

  @Test def theCrusadersShieldBashStunsEveryoneRoundHimWhereTheyStand(): Unit = {
    // It pushed them 3 cells back, out of his blade's reach: a hold keeps them in it
    val crusader = m.join(CharacterId.Crusader, 30, 30)
    val east = m.join(CharacterId.Soldier, 32, 30)
    val south = m.join(CharacterId.Soldier, 30, 35)
    val far = m.join(CharacterId.Soldier, 30, 24)
    slam(crusader, ProjectileType.SHOCKWAVE)
    for ((who, p, was) <- Seq(("east", east, (32, 30)), ("south, 5 cells off", south, (30, 35)))) {
      assertTrue(s"$who: stunned", p.isFrozen && p.getStunnedUntil > 0)
      assertEquals(s"$who: where they stood", was, m.at(p))
      assertEquals(s"$who: hurt", 22, p.getMaxHealth - p.getHealth)
    }
    assertFalse("6 cells off", far.isFrozen)
    assertEquals(far.getMaxHealth, far.getHealth)
  }

  @Test def aHowlSlowsOutToNineCells(): Unit = {
    val wolf = m.join(CharacterId.Wolf, 30, 30)
    val inside = m.join(CharacterId.Soldier, 38, 30)
    val outside = m.join(CharacterId.Soldier, 30, 40)
    slam(wolf, ProjectileType.HOWL)
    assertTrue("8 cells off", inside.isSlowed)
    assertFalse("10 cells off", outside.isSlowed)
  }

  @Test def theIceQuakeFreezes(): Unit = {
    val avalanche = m.join(CharacterId.Avalanche, 30, 30)
    val target = m.join(CharacterId.Soldier, 34, 30)
    slam(avalanche, ProjectileType.ICE_QUAKE)
    assertTrue(target.isFrozen)
    assertEquals("a freeze, not a stun", 0L, target.getStunnedUntil)
  }

  @Test def theEarthsplitterRootsWhoeverItReaches(): Unit = {
    val barbarian = m.join(CharacterId.Barbarian, 10, 30)
    val target = m.join(CharacterId.Soldier, 20, 30)
    shoot(barbarian, ProjectileType.EARTHSPLITTER)
    assertTrue(target.isRooted)
  }

  // --- More than one effect on a hit ---

  @Test def theAssassinsDartPoisonsAndSlows(): Unit = {
    val assassin = m.join(CharacterId.Assassin, 10, 30)
    val target = m.join(CharacterId.Soldier, 18, 30)
    shoot(assassin, ProjectileType.VENOM_DART)
    assertTrue("poisoned", target.isPoisoned)
    assertTrue("and slowed", target.isSlowed)
  }

  @Test def theMinotaursHornKnocksBackAndStuns(): Unit = {
    val minotaur = m.join(CharacterId.Minotaur, 10, 30)
    val target = m.join(CharacterId.Soldier, 15, 30)
    shoot(minotaur, ProjectileType.HEAD_THROW)
    assertTrue(s"knocked back to ${m.at(target)}", target.getPosition.getX > 15)
    assertTrue("and held there", target.isFrozen)
  }

  @Test def fenrirsFrenzyDrainsAndSlows(): Unit = {
    val fenrir = m.join(CharacterId.Fenrir, 10, 30)
    val target = m.join(CharacterId.Soldier, 13, 30)
    fenrir.setHealth(50)
    shoot(fenrir, ProjectileType.BLOOD_FRENZY)
    assertTrue(s"healed to ${fenrir.getHealth}", fenrir.getHealth > 50)
    assertTrue(target.isSlowed)
  }

  @Test def theBatSwarmStillFreezes(): Unit = {
    // A special case in GameInstance until Plan 5a, now an effect in the def like any other
    val vampire = m.join(CharacterId.Vampire, 10, 30)
    val target = m.join(CharacterId.Soldier, 13, 30)
    vampire.setHealth(50)
    shoot(vampire, ProjectileType.BAT_SWARM)
    assertTrue("healed", vampire.getHealth > 50)
    assertTrue("frozen", target.isFrozen)
  }

  @Test def aFlambeSetsEveryoneItBurstsOverAlight(): Unit = {
    // A splash, not an explosion: an explosion deals its blast and nothing else
    val chef = m.join(CharacterId.Chef, 10, 30)
    val target = m.join(CharacterId.Soldier, 14, 30)
    val beside = m.join(CharacterId.Soldier, 14, 31)
    shoot(chef, ProjectileType.FLAMBE)
    assertTrue("the one it hit", target.isBurning)
    assertTrue("the one beside them", beside.isBurning)
  }

  // --- A melee swing strikes wide (design/characters.md) ---

  /** A swing of `swing` thrown east by `who` from (10, 30): does it strike someone standing `dx`
    * cells ahead of them and `dy` to the side? */
  private def swingStrikes(who: CharacterId, swing: Byte, dx: Int, dy: Int): Boolean = {
    val m = new TestMatch()
    val swinger = m.join(who, 10, 30)
    val target = m.join(CharacterId.Golem, 10 + dx, 30 + dy)
    m.tickUntilGone(m.launch(swinger, swing, 1f, 0f))
    target.getHealth < target.getMaxHealth
  }

  private val swings: Seq[(CharacterId, Byte)] = Seq(
    CharacterId.Wolf -> ProjectileType.CLAW_SWIPE, CharacterId.Deathknight -> ProjectileType.CHILL_BLADE,
    CharacterId.Scorpion -> ProjectileType.STINGER, CharacterId.Berserker -> ProjectileType.AXE,
    CharacterId.Shapeshifter -> ProjectileType.FIST, CharacterId.Rogue -> ProjectileType.SHURIKEN)

  @Test def aSwingStrikesSomeoneThreeCellsToTheSideOfItsFlight(): Unit = {
    // Where a runner is by the time a swing aimed at their picture lands: a swing strikes within 3.7
    // cells of a point that hangs back behind it, where it reached 2.2 from itself
    for ((who, swing) <- swings) {
      assertTrue(s"${who.name}: three cells to the side", swingStrikes(who, swing, 2, 3))
      assertTrue(s"${who.name}: and to the other", swingStrikes(who, swing, 2, -3))
      // The runner chased at a cell or two, whose picture is behind us: aimed at that, the swing
      // goes off backwards, and still finds them
      assertTrue(s"${who.name}: two cells behind", swingStrikes(who, swing, -2, 0))
      assertFalse(s"${who.name}: five cells to the side is out of it", swingStrikes(who, swing, 2, 5))
      assertFalse(s"${who.name}: four cells behind is out of it", swingStrikes(who, swing, -4, 0))
    }
  }

  @Test def aSwingReachesNoFurtherAheadThanItDid(): Unit = {
    // Wider, not longer: a swing reaches a cell, its range and 2.2 ahead of itself (the heavy ones
    // 2.5), as it did when its circle was that narrow and sat on it. A boomerang's is held to it
    // through its turn and all the way back: let go of there, it reached its whole radius past the
    // end of its flight
    for ((who, swing) <- swings) {
      val d = ProjectileDef.get(swing)
      val reach = 1 + d.maxRange + (d.hitRadius - d.hitCentreBehind)
      assertEquals(s"${who.name}'s ${d.name}", if (d.hitRadius > 3.8f) 2.5f else 2.2f, d.hitRadius - d.hitCentreBehind, 0.001f)
      assertTrue(s"${who.name}: ${reach.toInt} cells ahead", swingStrikes(who, swing, reach.toInt, 0))
      assertFalse(s"${who.name}: ${reach.toInt + 1} cells ahead is out of reach", swingStrikes(who, swing, reach.toInt + 1, 0))
    }
  }

  @Test def aSwingsCircleIsLookedForRoundItsCentreNotRoundTheSwing(): Unit = {
    // The projectile tick shows a shot the players in the squares of four cells round a point
    // (PlayerGrid). A swing's circle hangs back behind it, so someone in the back of the circle can
    // be two squares from the swing itself: looked for round the swing, they were never found
    val berserker = m.join(CharacterId.Berserker, 9, 30)
    val late = m.join(CharacterId.Golem, 40, 40)
    val axe = m.launch(berserker, ProjectileType.AXE, 1f, 0f)
    m.tick(3)
    assertEquals("nobody near it yet", late.getMaxHealth, late.getHealth)
    assertTrue(s"the axe is still out, at ${axe.getX}", axe.getX > 11.5f && axe.getX < 12f && !axe.isReturning)
    // Two cells behind the Berserker as the axe crosses into the next square of the grid
    late.setPosition(new Position(7, 30))
    m.tick()
    assertTrue(s"the axe is at ${axe.getX}, its circle's centre at ${axe.hitCentreX}", axe.getX > 12f && !axe.isReturning)
    assertTrue("struck, from the back of its circle", late.getHealth < late.getMaxHealth)
  }

  @Test def aSwingStillStrikesOnlyTheNearestOfThoseItReaches(): Unit = {
    val swinger = m.join(CharacterId.Wolf, 10, 30)
    val near = m.join(CharacterId.Golem, 12, 31)
    val far = m.join(CharacterId.Golem, 12, 33)
    shoot(swinger, ProjectileType.CLAW_SWIPE)
    assertTrue(near.getHealth < near.getMaxHealth)
    assertEquals("one swing, one struck", far.getMaxHealth, far.getHealth)
  }

  // --- A fighter can finish what it starts (design/characters.md) ---

  /** Every fighter's pull: who throws it, and its type. */
  private val fightersPulls: Seq[(CharacterId, Byte)] = Seq(
    CharacterId.Gladiator -> ProjectileType.LASSO, CharacterId.Reaper -> ProjectileType.REAP,
    CharacterId.Deathknight -> ProjectileType.DEATH_GRIP, CharacterId.Bear -> ProjectileType.GRAB,
    CharacterId.Gorilla -> ProjectileType.GRAB, CharacterId.Shark -> ProjectileType.JAW,
    CharacterId.Griffin -> ProjectileType.TALON_GRAB, CharacterId.Chef -> ProjectileType.MEAT_HOOK)

  @Test def theDeathKnightsGripStunsItsCatchForASecondOnceItHasThemBesideHim(): Unit = Sim() { clock =>
    val m = new SimMatch(clock)
    val knight = m.join(CharacterId.Deathknight, 10, 30)
    val target = m.join(CharacterId.Soldier, 20, 30)
    val watcher = m.join(CharacterId.Soldier, 10, 45)
    m.clearSent()
    m.tickUntilGone(m.launch(knight, ProjectileType.DEATH_GRIP, 1f, 0f))
    assertEquals("on the Death Knight's own cell", (10, 30), m.at(target))
    assertEquals("the grip's 10", 10, target.getMaxHealth - target.getHealth)
    assertTrue("held", target.isFrozen)
    assertEquals("as a stun, for a second from the pull", clock.millis() + 1000, target.getStunnedUntil)
    // Their own client hears the pull and then the hold, over TCP, and everyone else both at once
    val own = m.updatesAbout(m.tcpSent(target), target)
    assertEquals("the pull, then the hold", Seq((1, 1), (2, 1)), own.map(u => (u.getServerMoves, u.getKnockbacks)))
    assertEquals("held for a second", 1000, own.last.getHeldMs)
    val seen = m.updatesAbout(m.udpSent(watcher), target).last
    assertEquals(((10, 30), 1, 1000), ((seen.getPosition.getX, seen.getPosition.getY), seen.getKnockbacks, seen.getHeldMs))
    clock.advance(999)
    assertTrue("to its last millisecond", target.isFrozen)
    clock.advance(1)
    assertFalse("and no longer", target.isFrozen)
  }

  @Test def everyFightersPullLandsItsCatchBesideThemStunned(): Unit =
    for ((who, pType) <- fightersPulls) Sim() { clock =>
      val m = new SimMatch(clock)
      val puller = m.join(who, 10, 30)
      val target = m.join(CharacterId.Soldier, 17, 30)
      m.tickUntilGone(m.launch(puller, pType, 1f, 0f))
      val what = s"${who.name}'s ${ProjectileDef.get(pType).name}"
      assertEquals(s"$what: pulled in", (10, 30), m.at(target))
      assertEquals(s"$what: stunned for a second", clock.millis() + 1000, target.getStunnedUntil)
      assertTrue(what, target.isFrozen)
    }

  @Test def aCatchWhoCannotBeHeldIsStillPulledIn(): Unit = Sim() { clock =>
    // Just out of another hold, nothing holds them for a second and a half; a pull is not a hold
    val m = new SimMatch(clock)
    val knight = m.join(CharacterId.Deathknight, 10, 30)
    val target = m.join(CharacterId.Soldier, 20, 30)
    assertTrue(target.tryStun(500))
    clock.advance(600)
    assertTrue(target.isCCImmune)
    m.tickUntilGone(m.launch(knight, ProjectileType.DEATH_GRIP, 1f, 0f))
    assertEquals((10, 30), m.at(target))
    assertFalse("pulled, and free to walk off", target.isFrozen)
  }

  @Test def theRevenantsLifeDrainHealsHimAndStunsItsTarget(): Unit = Sim() { clock =>
    val m = new SimMatch(clock)
    val revenant = m.join(CharacterId.Revenant, 10, 30)
    val target = m.join(CharacterId.Soldier, 16, 30)
    revenant.setHealth(50)
    m.tickUntilGone(m.launch(revenant, ProjectileType.LIFE_DRAIN, 1f, 0f))
    assertEquals("35% of its 20", 57, revenant.getHealth)
    assertEquals(clock.millis() + 800, target.getStunnedUntil)
    assertFalse("a stun where it slowed", target.isSlowed)
  }

  @Test def theDullahansBladeStunsBothItPassesThrough(): Unit = Sim() { clock =>
    val m = new SimMatch(clock)
    val dullahan = m.join(CharacterId.Dullahan, 10, 30)
    val first = m.join(CharacterId.Golem, 15, 30)
    val second = m.join(CharacterId.Golem, 19, 30)
    m.tickUntilGone(m.launch(dullahan, ProjectileType.BOOMERANG_BLADE, 1f, 0f))
    for (p <- Seq(first, second)) {
      assertTrue(p.getStunnedUntil > 0)
      assertTrue("hurt", p.getHealth < p.getMaxHealth)
    }
    assertFalse(first.isSlowed)
  }

  @Test def theRoguesKnivesPoisonAndStun(): Unit = Sim() { clock =>
    val m = new SimMatch(clock)
    val rogue = m.join(CharacterId.Rogue, 10, 30)
    val target = m.join(CharacterId.Soldier, 14, 30)
    m.tickUntilGone(m.launch(rogue, ProjectileType.TOXIC_SHURIKEN, 1f, 0f))
    assertTrue("poisoned", target.isPoisoned)
    assertEquals(clock.millis() + 600, target.getStunnedUntil)
    assertFalse(target.isSlowed)
  }

  @Test def theWolfsStrikeKnocksItsPreyDownAndSpeedsHimUp(): Unit = Sim() { clock =>
    val m = new SimMatch(clock)
    val wolf = m.join(CharacterId.Wolf, 10, 30)
    val target = m.join(CharacterId.Soldier, 14, 30)
    m.tickUntilGone(m.launch(wolf, ProjectileType.MOMENTUM_STRIKE, 1f, 0f))
    assertEquals(clock.millis() + 600, target.getStunnedUntil)
    assertTrue("and he is off", wolf.hasSpeedBoost)
  }

  @Test def theMonksFlurryStunsLongEnoughForTwoBlows(): Unit = Sim() { clock =>
    val m = new SimMatch(clock)
    val monk = m.join(CharacterId.Monk, 10, 30)
    val target = m.join(CharacterId.Soldier, 13, 30)
    m.tickUntilGone(m.launch(monk, ProjectileType.FLURRY, 1f, 0f))
    assertEquals(clock.millis() + 600, target.getStunnedUntil)
  }

  // --- Closers ---

  @Test def theReapersScytheDragsItsTargetThroughAWall(): Unit = {
    val reaper = m.join(CharacterId.Reaper, 10, 30)
    val target = m.join(CharacterId.Soldier, 16, 30)
    for (y <- 28 to 32) m.world.setTile(13, y, Tile.Wall)
    shoot(reaper, ProjectileType.REAP)
    assertEquals("onto the Reaper's own cell", (10, 30), m.at(target))
  }

  /** A projectile of `pType` thrown after someone `gap` cells off who walks straight away at a
    * ranged character's pace, `phase` of the way through a step: did it reach them? */
  private def catchesARunner(thrower: CharacterId, pType: Byte, gap: Int, phase: Double = 0.0,
                             m: TestMatch = m): Boolean = {
    val owner = m.join(thrower, 5, 30)
    val runner = m.join(CharacterId.Soldier, 5 + gap, 30)
    val full = runner.getHealth
    val p = m.launch(owner, pType, 1f, 0f)
    // A projectile tick is 30ms, in which a runner at 20 cells a second covers 0.6 of a cell
    var walked = phase
    var ticks = 0
    while (m.instance.projectileManager.getProjectile(p.id) != null && ticks < 200) {
      if (runner.getHealth == full) {
        walked += 0.6
        if (walked >= 1.0) {
          walked -= 1.0
          runner.setPosition(new Position(runner.getPosition.getX + 1, 30))
        }
      }
      m.tick()
      ticks += 1
    }
    runner.getHealth < full
  }

  @Test def everyProjectileWayInLandsFromEightCellsWhereverTheRunnerIsInItsStep(): Unit = {
    // MeleeKitTest counts these as ways in by a formula for a runner moving smoothly. One walks in
    // one-cell steps, and a 16-cell closer at 0.9 that the formula put at 8.1 cells missed from 8
    // at 3 phases of the step in 20 (and a Hammer Throw from 8 in the play test)
    def reach(d: ProjectileDef): Double = {
      val speed = 1000.0 / 30 * d.speedMultiplier
      (if (speed > 20) d.maxRange * (1 - 20 / speed) else 0.0) + d.hitRadius + 1
    }
    def isCrowdControl(e: OnHitEffect): Boolean = e match {
      case Freeze(_) | Stun(_) | Launch(_) | Root(_) | TeleportOwnerBehind(_, _) | Slow(_, _) | PullToOwner | Push(_) | VortexPull(_, _) => true
      case _ => false
    }
    val closers = for {
      c <- CharacterDef.all if c.role != CombatRole.Ranged
      a <- Seq(c.qAbility, c.eAbility)
      if (a.castBehavior match { case StandardProjectile | FanProjectile(_, _) => true; case _ => false })
      d = ProjectileDef.get(a.projectileType)
      if d.id == a.projectileType && d.onHitEffects.exists(isCrowdControl) && reach(d) >= 8
    } yield (c, a, d)
    assertTrue(s"${closers.size} closers", closers.size >= 14)
    for ((c, a, d) <- closers; step <- 0 until 20)
      assertTrue(s"${c.displayName}'s ${a.name} misses from 8 cells ${step * 5}% of the way through a step",
        catchesARunner(c.id, d.id, 8, step / 20.0, new TestMatch()))
  }

  @Test def aFastPullCatchesSomeoneWalkingAwayFromEightCells(): Unit =
    assertTrue(catchesARunner(CharacterId.Chef, ProjectileType.MEAT_HOOK, 8))

  @Test def theDeathBoltThatDeathGripReplacedNeverCould(): Unit =
    // At 0.7 a bolt gains under 4 cells a second on a runner, and runs out of its 16 cells first
    assertFalse(catchesARunner(CharacterId.Deathknight, ProjectileType.DEATH_BOLT, 8))

  @Test def theDeathGripDoes(): Unit =
    assertTrue(catchesARunner(CharacterId.Deathknight, ProjectileType.DEATH_GRIP, 8))

  @Test def theHammerThrowStunsSomeoneWalkingAway(): Unit =
    assertTrue(catchesARunner(CharacterId.Blacksmith, ProjectileType.HAMMER_THROW, 8))
}
