package com.gridgame.common.model

import com.gridgame.common.Constants
import org.junit.Assert._
import org.junit.Test

/**
 * The one rule the two input handlers, the bots and the server's speed check all move by. At a
 * moveSpeed of 1.0 it has to reproduce the numbers the handlers each carried by hand, or every
 * character in the game changes pace the day it lands.
 */
class MovementTest {
  private def step(moveSpeed: Float = 1.0f, charging: Boolean = false, chargeLevel: Int = 0,
                   phased: Boolean = false, speedBoost: Boolean = false,
                   slowed: Boolean = false, slowMultiplier: Float = 1.0f): Int =
    Movement.stepIntervalMs(moveSpeed, charging, chargeLevel, phased, speedBoost, slowed, slowMultiplier)

  @Test def speedOneWalksAtTheRateItAlwaysDid(): Unit = {
    assertEquals(50, step())
    assertEquals(Constants.MOVE_RATE_LIMIT_MS, step())
    assertEquals(50.0, Movement.baseStepIntervalMs(1.0f), 0.0)
  }

  @Test def phasingAndBoostsKeepTheirOldNumbers(): Unit = {
    assertEquals("phased", 25, step(phased = true))
    assertEquals("speed boost", 30, step(speedBoost = true))
  }

  @Test def chargingDragsEachStepOutExactlyAsBefore(): Unit = {
    // What both handlers computed: 50 * (1 + charge/100 * 9), truncated
    for (charge <- 0 to 100) {
      assertEquals(s"charge $charge", (Constants.MOVE_RATE_LIMIT_MS * (1.0 + charge / 100.0 * 9.0)).toInt,
        step(charging = true, chargeLevel = charge))
    }
    assertEquals("a full charge is ten times as slow", 500, step(charging = true, chargeLevel = 100))
  }

  @Test def theOrderOfPrecedenceIsUnchanged(): Unit = {
    // Charging beats everything, a phase beats a boost, a boost beats a slow
    assertEquals(500, step(charging = true, chargeLevel = 100, phased = true, speedBoost = true,
      slowed = true, slowMultiplier = 0.5f))
    assertEquals(25, step(phased = true, speedBoost = true, slowed = true, slowMultiplier = 0.5f))
    assertEquals(30, step(speedBoost = true, slowed = true, slowMultiplier = 0.5f))
  }

  @Test def aSlowFinallyUsesItsOwnMultiplier(): Unit = {
    // Every slow used to be a flat 2x whatever its def said, so a Slow(_, 0.3f) and a
    // Slow(_, 0.6f) were the same slow
    assertEquals("half speed", 100, step(slowed = true, slowMultiplier = 0.5f))
    assertEquals("40%", 125, step(slowed = true, slowMultiplier = 0.4f))
    assertEquals("30%", 167, step(slowed = true, slowMultiplier = 0.3f))
    assertEquals("60%", 83, step(slowed = true, slowMultiplier = 0.6f))
  }

  @Test def aSlowNeitherSpeedsAnyoneUpNorStopsThemDead(): Unit = {
    assertEquals("a multiplier over 1 is still a slow", 50, step(slowed = true, slowMultiplier = 1.5f))
    assertEquals("and 0 doesn't divide by zero", 1000, step(slowed = true, slowMultiplier = 0f))
  }

  @Test def moveSpeedScalesTheWholeScale(): Unit = {
    assertEquals("twice the pace", 25, step(moveSpeed = 2.0f))
    assertEquals(40, step(moveSpeed = 1.25f))
    assertEquals(100, step(moveSpeed = 0.5f))
    assertEquals(33, step(moveSpeed = 1.5f))
    // and everything acting on a player is a factor on that, not on the old 50ms
    assertEquals("phased at 1.5", 17, step(moveSpeed = 1.5f, phased = true))
    assertEquals("half-slowed at 1.5", 67, step(moveSpeed = 1.5f, slowed = true, slowMultiplier = 0.5f))
    assertEquals("fully charged at 1.5", 333, step(moveSpeed = 1.5f, charging = true, chargeLevel = 100))
  }

  @Test def anAbsurdSpeedStillLeavesAnInterval(): Unit = {
    assertTrue(step(moveSpeed = 1000f) >= 1)
    assertTrue(step(moveSpeed = 0f) > 0)
    assertTrue(step(moveSpeed = -1f) > 0)
  }
}
