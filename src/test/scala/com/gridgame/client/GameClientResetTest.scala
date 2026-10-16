package com.gridgame.client

import com.gridgame.client.input.GLKeyboardHandler
import com.gridgame.common.Constants
import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test
import org.lwjgl.glfw.GLFW._

/**
 * R (design/characters.md, *R resets the cooldowns*), as the client has it: both abilities ready
 * again at once, from the press, and the server asked; R then on its own minute, which a match
 * starts on (practice doesn't) and a death leaves as it is. With nothing cooling down it does
 * nothing and spends nothing, and whoever couldn't cast can't press it. A refusal puts the cooldowns
 * back as the server has them.
 */
class GameClientResetTest {
  private val t = new TestClient()
  private val c = t.client
  private var serverSeq = 5000

  /** A Soldier, whose Q and E are both shots, in a match begun just now, aiming east. */
  private def play(as: CharacterId = CharacterId.Soldier): Unit = {
    c.selectedCharacterId = as.id
    t.startMatch(spawn = (30, 30))
    c.setMouseWorldPosition(38.0, 30.0)
    t.clearSent()
  }

  private def ms(n: Long): Unit = t.nanos += n * 1000000L
  private def minuteRuns(): Unit = ms(Constants.COOLDOWN_RESET_MS)
  private def resets: Seq[CooldownResetPacket] = t.sent.collect { case r: CooldownResetPacket => r }
  private def refused(msLeft: Int = 0): Unit = {
    serverSeq += 1
    t.receive(new CooldownResetPacket(serverSeq, t.id, CooldownResetAction.REJECTED, msLeft))
  }

  // --- What it does ---

  @Test def bothAbilitiesAreReadyAgainAndTheServerIsAsked(): Unit = {
    play()
    minuteRuns()
    assertTrue(c.shootAbility(0))
    assertTrue(c.shootAbility(1))
    assertTrue(c.getQCooldownFraction > 0f && c.getECooldownFraction > 0f)
    t.clearSent()
    c.pressReset()
    assertEquals(0f, c.getQCooldownFraction, 0f)
    assertEquals(0f, c.getECooldownFraction, 0f)
    assertEquals(Seq(CooldownResetAction.REQUEST), resets.map(_.getAction))
    assertEquals(t.id, resets.head.getPlayerId)
    assertEquals("and R is on its minute", 1f, c.getResetCooldownFraction, 0.001f)
    assertFalse(c.resetReady)
    assertTrue("both cast again at once", c.shootAbility(0) && c.shootAbility(1))
  }

  @Test def oneCoolingDownIsEnough(): Unit = {
    play()
    minuteRuns()
    c.shootAbility(1)
    c.pressReset()
    assertEquals(0f, c.getECooldownFraction, 0f)
    assertEquals(1, resets.size)
  }

  @Test def withNothingCoolingDownItDoesNothing(): Unit = {
    play()
    minuteRuns()
    assertTrue(c.resetReady)
    c.pressReset()
    assertTrue("nothing asked", resets.isEmpty)
    assertEquals("nothing spent", 0f, c.getResetCooldownFraction, 0f)
    c.shootAbility(0)
    c.pressReset()
    assertEquals(1, resets.size)
  }

  @Test def whatIsAimedStaysAimed(): Unit = {
    play()
    minuteRuns()
    c.shootAbility(1)
    c.pressAbility(0)
    assertEquals(0, c.aimedAbility)
    c.pressReset()
    assertEquals(0, c.aimedAbility)
    c.pressPrimary()
    assertEquals("the click casts it", Seq(AttackSlot.E, AttackSlot.Q), t.sentSpawns.map(_.getAttackSlot))
  }

  @Test def theRKeyPressesIt(): Unit = {
    val keys = new GLKeyboardHandler(c)
    play()
    minuteRuns()
    c.shootAbility(0)
    keys.invoke(0L, GLFW_KEY_R, 0, GLFW_PRESS, 0)
    keys.invoke(0L, GLFW_KEY_R, 0, GLFW_RELEASE, 0)
    assertEquals(0f, c.getQCooldownFraction, 0f)
    assertEquals(1, resets.size)
  }

  // --- Its own minute ---

  @Test def aMatchStartsWithItOnItsMinute(): Unit = {
    play()
    c.shootAbility(0)
    assertFalse(c.resetReady)
    assertEquals(1f, c.getResetCooldownFraction, 0.001f)
    assertEquals(Constants.COOLDOWN_RESET_MS / 1000f, c.getResetCooldownRemaining, 0.01f)
    c.pressReset()
    assertTrue(resets.isEmpty)
    assertTrue("the grenade still cools down", c.getQCooldownFraction > 0f)
    ms(Constants.COOLDOWN_RESET_MS - 1000L)
    assertEquals(1f, c.getResetCooldownRemaining, 0.01f)
    assertFalse(c.resetReady)
    ms(1000L)
    assertTrue(c.resetReady)
    assertEquals(0f, c.getResetCooldownFraction, 0f)
  }

  @Test def practiceStartsWithItReady(): Unit = {
    // Practice is where a player tries a character out, R and all, as the server has it
    c.isPracticeMode = true // the practice screen sets it as it asks the server for a session
    play()
    assertTrue(c.resetReady)
    assertEquals(0f, c.getResetCooldownFraction, 0f)
    c.shootAbility(0)
    c.pressReset()
    assertEquals(0f, c.getQCooldownFraction, 0f)
    assertEquals(Seq(CooldownResetAction.REQUEST), resets.map(_.getAction))
    assertEquals("then on its minute, as anywhere", 1f, c.getResetCooldownFraction, 0.001f)
  }

  @Test def aMatchAfterPracticeHoldsItBackAgain(): Unit = {
    c.isPracticeMode = true
    play()
    assertTrue(c.resetReady)
    c.returnToLobbyBrowser()
    play()
    assertFalse(c.resetReady)
    assertEquals(1f, c.getResetCooldownFraction, 0.001f)
  }

  @Test def aDeathLeavesItAsItIs(): Unit = {
    play()
    minuteRuns()
    c.shootAbility(0)
    c.pressReset()
    ms(20000L)
    t.update(t.id, 30, 30, health = 0)
    assertTrue(c.getIsDead)
    t.gameEvent(GameEvent.RESPAWN, who = t.id, spawn = (31, 31))
    assertFalse(c.getIsDead)
    assertEquals("40s still to go", 40f, c.getResetCooldownRemaining, 0.01f)
    assertFalse(c.resetReady)
  }

  @Test def aNewMatchStartsItOnItsMinuteAgain(): Unit = {
    play()
    minuteRuns()
    assertTrue(c.resetReady)
    play()
    assertFalse(c.resetReady)
    assertEquals(1f, c.getResetCooldownFraction, 0.001f)
  }

  // --- Who can't ---

  @Test def theHeldTheDeadAndTheCeasefireCant(): Unit = {
    play()
    minuteRuns()
    c.shootAbility(0)
    t.clearSent()
    t.update(t.id, 30, 30, flags = 0x04) // frozen
    assertFalse(c.resetReady)
    c.pressReset()
    t.update(t.id, 30, 30)
    assertTrue(c.resetReady)
    t.opening(30000, MatchOpening.NO_ATTACKS)
    c.pressReset()
    t.opening(0, MatchOpening.NO_ATTACKS)
    t.update(t.id, 30, 30, health = 0)
    c.pressReset()
    assertTrue("none of them asked", resets.isEmpty)
    assertTrue("nor reset anything", c.getQCooldownFraction > 0f)
  }

  @Test def norThePhased(): Unit = {
    play(CharacterId.Wraith)
    minuteRuns()
    assertTrue(c.shootAbility(1)) // Haunt
    assertTrue(c.shootAbility(0)) // Phase Shift
    assertTrue(c.isPhased)
    c.pressReset()
    assertTrue(resets.isEmpty)
    assertTrue(c.getECooldownFraction > 0f)
  }

  @Test def oneBarrierAtATime(): Unit = {
    // A barrier's cooldown always outlasted it; R has it ready while it is still up, and raised
    // again then, ours would have run on past the server's
    play(CharacterId.Crusader)
    minuteRuns()
    assertTrue(c.shootAbility(0))
    assertTrue(c.hasBarrier)
    val until = c.getBarrierUntil
    c.pressReset()
    assertEquals(0f, c.getQCooldownFraction, 0f)
    assertFalse("ready, but not while it is up", c.abilityReady(0))
    c.pressAbility(0)
    assertEquals(until, c.getBarrierUntil)
  }

  // --- The server's word ---

  @Test def aRefusalPutsTheCooldownsBack(): Unit = {
    play()
    minuteRuns()
    c.shootAbility(0)
    c.shootAbility(1)
    val q = c.getQCooldownRemaining
    val e = c.getECooldownRemaining
    c.pressReset()
    refused()
    assertEquals(q, c.getQCooldownRemaining, 0.1f)
    assertEquals(e, c.getECooldownRemaining, 0.1f)
    assertFalse("R in a moment, not this instant", c.resetReady)
    ms(400L)
    assertTrue(c.resetReady)
  }

  @Test def aRefusalForItsMinuteWaitsAsLongAsTheServerSays(): Unit = {
    play()
    minuteRuns()
    c.shootAbility(0)
    c.pressReset()
    refused(msLeft = 30000)
    assertEquals(30f, c.getResetCooldownRemaining, 0.01f)
  }

  @Test def aCastOnlyTheResetAllowedGoesBackAndOneThatCouldGoAnywayStands(): Unit = {
    play()
    minuteRuns()
    c.shootAbility(0)
    c.pressReset()
    assertTrue(c.shootAbility(0)) // refused by a server that never took the reset
    assertTrue(c.shootAbility(1)) // E was ready anyway, so the server took this one
    val e = c.getECooldownRemaining
    refused()
    assertTrue("Q is back on its first cooldown", c.getQCooldownRemaining > 9.5f)
    assertEquals("E's cast stands", e, c.getECooldownRemaining, 0.1f)
  }

  @Test def aRefusalOfLastLifesResetPutsNothingIntoThisOne(): Unit = {
    play()
    minuteRuns()
    c.shootAbility(0)
    c.pressReset()
    c.shootAbility(0)
    t.update(t.id, 30, 30, health = 0)
    t.gameEvent(GameEvent.RESPAWN, who = t.id, spawn = (31, 31))
    refused()
    assertEquals("a new life's abilities are ready", 0f, c.getQCooldownFraction, 0f)
  }

  @Test def aRefusalOfLastMatchsResetIsNothingToThisOne(): Unit = {
    play()
    minuteRuns()
    c.shootAbility(0)
    c.pressReset()
    play() // the next match, before the refusal came
    refused()
    assertFalse("R is on this match's minute", c.resetReady)
    assertEquals(1f, c.getResetCooldownFraction, 0.001f)
  }

  @Test def someoneElsesRefusalIsNotOurs(): Unit = {
    play()
    minuteRuns()
    c.shootAbility(0)
    c.pressReset()
    serverSeq += 1
    t.receive(new CooldownResetPacket(serverSeq, java.util.UUID.randomUUID(), CooldownResetAction.REJECTED, 0))
    assertEquals(0f, c.getQCooldownFraction, 0f)
    assertFalse(c.resetReady)
  }
}
