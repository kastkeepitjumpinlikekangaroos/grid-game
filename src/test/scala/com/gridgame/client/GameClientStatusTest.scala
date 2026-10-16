package com.gridgame.client

import com.gridgame.common.model._
import org.junit.Assert._
import org.junit.Test

import java.util.UUID

/**
 * Status effects as they arrive from the server: the second flag byte (stunned, poisoned), how
 * hard the slow we are under is, which is what lets the client step at the rate the slow that
 * landed actually calls for, and how long a speed boost has left, and a haste of our own walked
 * from its cast.
 */
class GameClientStatusTest {
  private val t = new TestClient()
  private val c = t.client

  private val FROZEN = 0x04
  private val BOOSTED = 0x20
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

  // --- A speed boost: as long as the server says it has left ---

  @Test def aBoostLastsAsLongAsTheServerSaysItHasLeft(): Unit = {
    // Bytes [59-60]. With the flag alone it was taken as a second's boost, re-armed only by the next
    // update to carry it, which at full health is the next hit: a 4s Overclock walked for one
    t.startMatch(spawn = (20, 20))
    t.update(t.id, 20, 20, flags = BOOSTED, speedBoostMs = 3000)
    Thread.sleep(1300)
    assertTrue("1.3s into a 3s boost", c.hasSpeedBoost)
    t.update(t.id, 20, 20)
    assertFalse("over when the server says it is", c.hasSpeedBoost)
  }

  @Test def aHasteWeCastIsWalkedFromTheCast(): Unit = {
    // The Gambler's Lucky Streak: our pace at once, as a phase is, not a round trip later
    c.selectedCharacterId = CharacterId.Gambler.id
    t.startMatch(spawn = (20, 20))
    t.clearSent()
    c.shootAbility(0)
    assertEquals("cast", 1, t.sentSpawns.count(_.getProjectileType == ProjectileType.LUCKY_STREAK))
    assertTrue(c.hasSpeedBoost)
    // An update the server sent before it heard of the cast says we have none; it mustn't take it
    t.update(t.id, 20, 20)
    assertTrue("still ours", c.hasSpeedBoost)
    // ...but past a round trip and then some, one without it means the server never gave it
    Thread.sleep(700)
    t.update(t.id, 20, 20)
    assertFalse(c.hasSpeedBoost)
  }

  @Test def aSlamThatHastesNobodyIsNotPredicted(): Unit = {
    // The Graviton's Repulse throws people about and speeds nobody up
    c.selectedCharacterId = CharacterId.Graviton.id
    t.startMatch(spawn = (20, 20))
    c.shootAbility(0)
    assertFalse(c.hasSpeedBoost)
  }

  // --- Everyone else ---

  @Test def anotherPlayersBoostLastsAsLongAsTheServerSays(): Unit = {
    val them = UUID.randomUUID()
    t.startMatch(spawn = (20, 20), others = Seq(them -> (25, 25)))
    t.update(them, 25, 25, flags = BOOSTED, speedBoostMs = 2500)
    Thread.sleep(1300)
    assertTrue(c.getPlayers.get(them).hasSpeedBoost)
  }

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

  // --- A hold, a root and a slow: as long as the server says they have left ---

  private val ROOTED = 0x40

  @Test def aHoldLetsUsGoWhenTheServerSaysItEnds(): Unit = {
    // Bytes [61-63]. With the flags alone a hold was armed for 5s and lifted by the next update
    // without it, which nothing sends when a hold ends: we were held until the server next said
    // anything about us, a regeneration tick most of a second after a 400ms stun, or at full
    // health nothing at all
    t.startMatch(spawn = (20, 20))
    t.update(t.id, 20, 20, flags = FROZEN, flags2 = STUNNED2, heldMs = 300)
    assertTrue(c.isFrozen)
    assertTrue(c.isStunned)
    Thread.sleep(400)
    assertFalse("let go with nothing more said", c.isFrozen)
    assertFalse(c.isStunned)
    c.setMouseWorldPosition(30.0, 20.0)
    t.clearSent()
    c.shootToward(1f, 0f)
    assertEquals("and free to fire again", 1, t.sentSpawns.size)
  }

  @Test def aRootAndASlowEndWhenTheServerSaysTheyDo(): Unit = {
    // A 2s snare at full health held us for the 3s the flag was armed for
    t.startMatch(spawn = (20, 20))
    t.update(t.id, 20, 20, flags = ROOTED | SLOWED, slowPercent = 50, rootedMs = 300, slowedMs = 500)
    assertTrue(c.isRooted)
    assertTrue(c.isSlowed)
    Thread.sleep(400)
    assertFalse("the root is over", c.isRooted)
    assertTrue("the slow isn't yet", c.isSlowed)
    t.clearSent()
    c.movePlayer(1, 0)
    assertEquals("free to walk", (21, 20), t.at)
    Thread.sleep(200)
    assertFalse(c.isSlowed)
    assertEquals(50, c.moveStepIntervalMs)
  }

  @Test def everyUpdateSaysAgainHowLongItHasLeft(): Unit = {
    // Not only the first: the flag used to arm its guess once, and the updates after it that still
    // carried it changed nothing
    t.startMatch(spawn = (20, 20))
    t.update(t.id, 20, 20, flags = ROOTED, rootedMs = 2500)
    assertTrue(c.isRooted)
    t.update(t.id, 20, 20, flags = ROOTED, rootedMs = 200)
    Thread.sleep(300)
    assertFalse("over when the newest word says", c.isRooted)
  }

  @Test def aPhaseIsSaidAgainWhileItLasts(): Unit = {
    // Its cast is one datagram: lost, a player standing still in their phase was never phased on
    // the server, and was hit all through it while their client showed them untouchable
    val (phaser, slot) = CharacterDef.all.iterator.flatMap { d =>
      Seq((0, d.qAbility), (1, d.eAbility)).collectFirst { case (s, a) if a.castBehavior.isInstanceOf[PhaseShiftBuff] => (d.id.id, s) }
    }.next()
    c.selectedCharacterId = phaser
    t.startMatch(spawn = (20, 20))
    val keys = new com.gridgame.client.input.GLKeyboardHandler(c)
    assertTrue(c.shootAbility(slot))
    assertTrue(c.isPhased)
    t.clearSent() // the cast's own update, as though it were lost
    for (_ <- 0 until 16) { keys.update(); Thread.sleep(20) } // a third of a second of frames, standing still
    val said = t.sentUpdates.filter(u => (u.getEffectFlags & 0x08) != 0)
    assertTrue(s"said again while it lasts, not once: ${said.size}", said.size >= 2)
    assertTrue("from where we stand", said.forall(_.getPosition == new Position(20, 20)))
  }

  @Test def anotherPlayersHoldAndSlowLastAsLongAsTheServerSays(): Unit = {
    val them = UUID.randomUUID()
    t.startMatch(spawn = (20, 20), others = Seq(them -> (25, 25)))
    t.update(them, 25, 25, flags = FROZEN | ROOTED | SLOWED, flags2 = STUNNED2, slowPercent = 40,
      heldMs = 300, rootedMs = 300, slowedMs = 300)
    val p = c.getPlayers.get(them)
    assertTrue(p.isFrozen && p.isStunned && p.isRooted && p.isSlowed)
    Thread.sleep(400)
    assertFalse("drawn free when they are", p.isFrozen || p.isStunned || p.isRooted || p.isSlowed)
  }
}
