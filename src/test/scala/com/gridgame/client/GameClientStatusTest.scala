package com.gridgame.client

import com.gridgame.common.model._
import org.junit.Assert._
import org.junit.Test

import java.util.UUID

/**
 * Status effects as they arrive from the server: the second flag byte (stunned, poisoned) and
 * how hard the slow we are under is, which is what lets the client step at the rate the slow
 * that landed actually calls for.
 */
class GameClientStatusTest {
  private val t = new TestClient()
  private val c = t.client

  private val FROZEN = 0x04
  private val SLOWED = 0x80
  private val STUNNED2 = 0x01
  private val POISONED2 = 0x02

  // --- Us ---

  @Test def aStunReachesUsAsAStunAndAsAHold(): Unit = {
    t.startMatch(spawn = (20, 20))
    t.update(t.id, 20, 20, flags = FROZEN, flags2 = STUNNED2)
    assertTrue("held", c.isFrozen)
    assertTrue("and it is a stun, not ice", c.isStunned)
    t.update(t.id, 20, 20)
    assertFalse(c.isStunned)
    assertFalse(c.isFrozen)
  }

  @Test def aPoisonReachesUs(): Unit = {
    t.startMatch(spawn = (20, 20))
    t.update(t.id, 20, 20, flags2 = POISONED2)
    assertTrue(c.isPoisoned)
    t.update(t.id, 20, 20)
    assertFalse(c.isPoisoned)
  }

  @Test def aPoisonStaysOnUsBetweenItsTicks(): Unit = {
    // A poison stops regen, so a player standing still hears of it only when it ticks. Armed
    // once, on the first update, the bubbles went out a second later however long it had left.
    t.startMatch(spawn = (20, 20))
    t.update(t.id, 20, 20, flags2 = POISONED2)
    Thread.sleep(700)
    t.update(t.id, 20, 20, flags2 = POISONED2) // its next tick
    Thread.sleep(700)
    assertTrue("still poisoned, 0.7s after a tick said so", c.isPoisoned)
    t.update(t.id, 20, 20) // its last tick, which goes out without the flag
    assertFalse(c.isPoisoned)
  }

  @Test def aPoisonAndABurnShowAtOnce(): Unit = {
    t.startMatch(spawn = (20, 20))
    t.update(t.id, 20, 20, flags = 0x10, flags2 = POISONED2)
    assertTrue(c.isBurning)
    assertTrue(c.isPoisoned)
  }

  @Test def weTellTheServerWhatWeAreUnder(): Unit = {
    t.startMatch(spawn = (20, 20))
    t.update(t.id, 20, 20, flags = FROZEN, flags2 = STUNNED2 | POISONED2)
    t.clearSent()
    c.movePlayer(1, 0) // held, so it goes nowhere, but the position still goes out
    val sent = t.sentUpdates.last
    assertEquals(STUNNED2 | POISONED2, sent.getEffectFlags2 & (STUNNED2 | POISONED2))
  }

  // --- How hard a slow is ---

  @Test def weStepAtTheRateTheSlowThatLandedCallsFor(): Unit = {
    // Every slow used to be a flat half speed on the client, whatever the def said, because the
    // only thing that crossed the wire was "slowed"
    t.startMatch(spawn = (20, 20))
    assertEquals("unslowed", 50, c.moveStepIntervalMs)

    t.update(t.id, 20, 20, flags = SLOWED, slowPercent = 30)
    assertTrue(c.isSlowed)
    assertEquals(0.3f, c.getSlowMultiplier, 0.001f)
    assertEquals("30% of our pace", 167, c.moveStepIntervalMs)

    t.update(t.id, 20, 20, flags = SLOWED, slowPercent = 60)
    assertEquals("and a weaker one is weaker", 83, c.moveStepIntervalMs)

    t.update(t.id, 20, 20)
    assertFalse(c.isSlowed)
    assertEquals(50, c.moveStepIntervalMs)
  }

  @Test def weSendBackHowHardTheSlowIs(): Unit = {
    t.startMatch(spawn = (20, 20))
    t.update(t.id, 20, 20, flags = SLOWED, slowPercent = 40)
    t.clearSent()
    c.movePlayer(1, 0)
    val sent = t.sentUpdates.last
    assertEquals(40, sent.getSlowPercent)
    t.update(t.id, 20, 20)
    t.clearSent()
    c.movePlayer(1, 0)
    assertEquals("nothing to report when not slowed", 0, t.sentUpdates.last.getSlowPercent)
  }

  // --- Everyone else ---

  @Test def theyReachOtherPlayersToo(): Unit = {
    val them = UUID.randomUUID()
    t.startMatch(spawn = (20, 20), others = Seq(them -> (25, 25)))
    t.update(them, 25, 25, flags = FROZEN | SLOWED, flags2 = STUNNED2 | POISONED2, slowPercent = 40)
    val p = c.getPlayers.get(them)
    assertNotNull(p)
    assertTrue("stunned", p.isStunned)
    assertTrue("frozen, so the rest of the client still holds them", p.isFrozen)
    assertTrue("poisoned", p.isPoisoned)
    assertEquals(0.4f, p.getSlowMultiplier, 0.001f)
    t.update(them, 25, 25)
    assertFalse(c.getPlayers.get(them).isStunned)
    assertFalse(c.getPlayers.get(them).isPoisoned)
  }

  @Test def aPlayerWeFirstSeeAlreadyStunnedIsDrawnThatWay(): Unit = {
    // Their first update can be the one that carries the effects: joining mid-fight, we never
    // saw the transition
    val them = UUID.randomUUID()
    t.startMatch(spawn = (20, 20))
    t.update(them, 30, 30, flags = FROZEN, flags2 = STUNNED2 | POISONED2, slowPercent = 50)
    val p = c.getPlayers.get(them)
    assertNotNull(p)
    assertTrue(p.isStunned)
    assertTrue(p.isPoisoned)
  }
}
