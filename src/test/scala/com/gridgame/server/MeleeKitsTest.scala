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

  @Test def theSamuraisWhirlwindStuns(): Unit = {
    val samurai = m.join(CharacterId.Samurai, 10, 30)
    val target = m.join(CharacterId.Soldier, 13, 30)
    shoot(samurai, ProjectileType.SWORD_WAVE)
    assertTrue("held", target.isFrozen)
    assertTrue("as a stun", target.getStunnedUntil > System.currentTimeMillis())
  }

  @Test def aShockwavePushesEveryoneAwayFromTheCrusader(): Unit = {
    val crusader = m.join(CharacterId.Crusader, 30, 30)
    val east = m.join(CharacterId.Soldier, 32, 30)
    val south = m.join(CharacterId.Soldier, 30, 33)
    slam(crusader, ProjectileType.SHOCKWAVE)
    assertEquals("pushed east", (35, 30), m.at(east))
    assertEquals("pushed south", (30, 36), m.at(south))
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
      case Freeze(_) | Stun(_) | Root(_) | TeleportOwnerBehind(_, _) | Slow(_, _) | PullToOwner | Push(_) | VortexPull(_, _) => true
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
