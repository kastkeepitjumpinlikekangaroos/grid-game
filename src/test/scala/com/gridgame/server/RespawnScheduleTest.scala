package com.gridgame.server.game

import com.gridgame.common.Constants
import com.gridgame.common.model._
import com.gridgame.server.TestMatch
import org.junit.Assert._
import org.junit.Test

import java.util.concurrent.{ScheduledFuture, ScheduledThreadPoolExecutor, TimeUnit}
import scala.collection.mutable

/**
 * A death books its player's next life on the match's own respawn clock. Every other suite brings
 * the dead back by hand (`GameInstance.respawn`, or SimKit's clockwork, which reads the same
 * delay), so a death that booked nothing — the dead staying dead for the rest of a real match —
 * failed none of them. Here the match is given a clock that only notes down what it is asked to
 * run and when (in package `server.game` to hand it one).
 */
class RespawnScheduleTest {
  /** A respawn clock that runs nothing: it keeps what was booked, and for how many ms on. */
  private final class Booked extends ScheduledThreadPoolExecutor(1) {
    val bookings = mutable.Buffer[(Long, Runnable)]()
    override def schedule(command: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture[_] = {
      bookings += ((unit.toMillis(delay), command))
      super.schedule(new Runnable { def run(): Unit = () }, 0L, TimeUnit.MILLISECONDS)
    }
  }

  /** A match with such a clock, an attacker and someone a touch from death, 4 cells east. */
  private def withAVictim(practice: Boolean = false)(body: (TestMatch, Player, Player, Booked) => Unit): Unit = {
    val m = new TestMatch(practice = practice)
    val clock = new Booked
    m.instance.respawnExecutor = clock
    try {
      val attacker = m.join(CharacterId.Soldier, 10, 30)
      val victim = m.join(CharacterId.Gladiator, 14, 30)
      victim.setHealth(1)
      body(m, attacker, victim, clock)
    } finally clock.shutdownNow()
  }

  private def assertBookedOnceAndBroughtBack(what: String, victim: Player, clock: Booked, afterMs: Long): Unit = {
    assertTrue(s"$what: dead", victim.isDead)
    assertEquals(s"$what: one new life booked, ${afterMs}ms on", Seq(afterMs), clock.bookings.map(_._1).toSeq)
    clock.bookings.head._2.run()
    assertFalse(s"$what: and it brings them back", victim.isDead)
    assertEquals(victim.getMaxHealth, victim.getHealth)
  }

  @Test def aShotsKillBooksTheNextLifeThreeSecondsOn(): Unit = withAVictim() { (m, attacker, victim, clock) =>
    m.tickUntilGone(m.launch(attacker, ProjectileType.BULLET, 1f, 0f))
    assertBookedOnceAndBroughtBack("a shot", victim, clock, Constants.RESPAWN_DELAY_MS.toLong)
    assertEquals(3000L, Constants.RESPAWN_DELAY_MS.toLong)
  }

  @Test def inPracticeItIsASecondOn(): Unit = withAVictim(practice = true) { (m, attacker, victim, clock) =>
    m.tickUntilGone(m.launch(attacker, ProjectileType.BULLET, 1f, 0f))
    assertBookedOnceAndBroughtBack("a shot in practice", victim, clock, 1000L)
  }

  @Test def aBlastsABurnsAPoisonsAndATrapsKillsEachBookOne(): Unit = {
    withAVictim() { (m, attacker, victim, clock) =>
      // A rocket going off beside them, out of its own way
      val rocket = m.launch(attacker, ProjectileType.ROCKET, 1f, 0f, from = (10, 31))
      m.world.setTile(15, 31, Tile.Wall)
      m.tickUntilGone(rocket)
      assertBookedOnceAndBroughtBack("a blast", victim, clock, Constants.RESPAWN_DELAY_MS.toLong)
    }
    withAVictim() { (m, attacker, victim, clock) =>
      victim.applyBurn(30, 3000, 500, attacker.getId)
      var ticks = 0
      while (!victim.isDead && ticks < 50) { Thread.sleep(60); m.instance.tickPlayers(); ticks += 1 }
      assertBookedOnceAndBroughtBack("a burn", victim, clock, Constants.RESPAWN_DELAY_MS.toLong)
    }
    withAVictim() { (m, attacker, victim, clock) =>
      victim.applyPoison(30, 3000, 500, attacker.getId)
      var ticks = 0
      while (!victim.isDead && ticks < 50) { Thread.sleep(60); m.instance.tickPlayers(); ticks += 1 }
      assertBookedOnceAndBroughtBack("a poison", victim, clock, Constants.RESPAWN_DELAY_MS.toLong)
    }
    withAVictim() { (m, attacker, victim, clock) =>
      assertNotNull(m.armedTrap(attacker, 15, 30, TrapType.BEAR_TRAP))
      assertTrue(m.move(victim, 14, 30))
      assertTrue(m.move(victim, 15, 30, gapMs = 60))
      assertBookedOnceAndBroughtBack("a trap", victim, clock, Constants.RESPAWN_DELAY_MS.toLong)
    }
  }
}
