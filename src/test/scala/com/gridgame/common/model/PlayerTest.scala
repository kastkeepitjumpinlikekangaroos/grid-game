package com.gridgame.common.model

import com.gridgame.common.Constants
import org.junit.Assert._
import org.junit.Test

import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

/** The player state the server keeps and the client mirrors. */
class PlayerTest {
  private def player(health: Int = 100): Player =
    new Player(UUID.randomUUID(), "p", new Position(10, 10), 0, health, 100)

  @Test def healthStaysWithinZeroAndMax(): Unit = {
    val p = player()
    p.setHealth(250)
    assertEquals(100, p.getHealth)
    p.setHealth(-40)
    assertEquals(0, p.getHealth)
    assertTrue(p.isDead)
  }

  @Test def onlyTheKillingBlowReportsTheDeath(): Unit = {
    val p = player(30)
    assertFalse(p.damage(20))
    assertEquals(10, p.getHealth)
    assertTrue("this one kills", p.damage(20))
    assertEquals(0, p.getHealth)
    assertFalse("the dead can't be killed again", p.damage(20))
  }

  @Test def ofManyLethalHitsAtOnceExactlyOneIsTheKill(): Unit = {
    // The projectile tick and the burn tick run on different threads. Before, both could read
    // the health as lethal and each score the death and schedule a respawn.
    for (_ <- 0 until 200) {
      val p = player(10)
      val kills = new AtomicInteger()
      val start = new CountDownLatch(1)
      val threads = (0 until 8).map { _ =>
        val t = new Thread(() => { start.await(); if (p.damage(15)) kills.incrementAndGet() })
        t.start(); t
      }
      start.countDown()
      threads.foreach(_.join())
      assertEquals(1, kills.get)
    }
  }

  @Test def aFreezeIsFollowedByImmunity(): Unit = {
    val p = player()
    assertTrue(p.tryFreeze(1000))
    assertTrue(p.isFrozen)
    assertFalse("already frozen", p.tryFreeze(1000))
    p.setFrozenUntil(0)
    assertFalse("immune for a while after", p.tryFreeze(1000))
    assertFalse("roots too", p.tryRoot(1000))
    assertTrue(p.isCCImmune)
  }

  @Test def aStunIsAFreezeByAnotherName(): Unit = {
    // The freeze's own timer and rules, so everything that holds a frozen player holds a stunned
    // one without knowing stuns exist; the marker only says which of the two to draw
    val p = player()
    assertTrue(p.tryStun(1000))
    assertTrue(p.isStunned)
    assertTrue("held", p.isFrozen)
    assertEquals(p.getFrozenUntil, p.getStunnedUntil)
    assertFalse("already held", p.tryStun(1000))
    assertFalse(p.tryFreeze(1000))
    p.setFrozenUntil(0)
    p.setStunnedUntil(0)
    assertFalse("immune for a while after, as after a freeze", p.tryStun(1000))
    assertTrue(p.isCCImmune)
  }

  @Test def aPhasedPlayerCannotBeHeld(): Unit = {
    val p = player()
    p.setPhasedUntil(System.currentTimeMillis() + 5000)
    assertFalse(p.tryFreeze(1000))
    assertFalse(p.tryStun(1000))
    assertFalse(p.tryRoot(1000))
    assertFalse(p.trySlow(1000, 0.5f))
  }

  @Test def ccImmunityOutlastsTheFreezeByTheConfiguredWindow(): Unit = {
    val p = player()
    val before = System.currentTimeMillis()
    p.tryFreeze(400)
    // Immune until ~400 + CC_IMMUNITY_MS from now
    assertTrue(p.getFrozenUntil >= before + 400)
    p.setFrozenUntil(0)
    assertTrue(p.isCCImmune)
    Thread.sleep(400 + Constants.CC_IMMUNITY_MS + 50)
    assertFalse(p.isCCImmune)
    assertTrue(p.tryRoot(100))
  }

  @Test def aBurnSpreadsItsDamageOverItsTicks(): Unit = {
    val p = player()
    p.applyBurn(totalDamage = 12, durationMs = 3000, tickMs = 750, ownerId = UUID.randomUUID())
    assertTrue(p.isBurning)
    assertEquals(3, p.getBurnDamagePerTick)
    p.clearBurn()
    assertFalse(p.isBurning)
  }

  @Test def aPoisonDealsItsWholeTotalOnAFixedSchedule(): Unit = {
    val p = player()
    p.applyPoison(totalDamage = 25, durationMs = 4000, tickMs = 1000, ownerId = UUID.randomUUID())
    assertTrue(p.isPoisoned)
    val due = p.getNextPoisonTickAt
    assertEquals("not due yet", -1, p.takePoisonTick(due - 1))
    // Each tick falls due a whole tick after the one before was due, however late that one was
    // taken (the server only looks every 200ms), so lateness never pushes the last one out
    val bites = Seq(p.takePoisonTick(due + 190), p.takePoisonTick(due + 1000),
      p.takePoisonTick(due + 2150), p.takePoisonTick(due + 3199))
    assertEquals("25 won't split four ways: the remainder comes last", Seq(6, 6, 6, 7), bites)
    assertFalse("over once the last has landed", p.isPoisoned)
    assertEquals(-1, p.takePoisonTick(due + 60000))
  }

  @Test def serverMovesAreCounted(): Unit = {
    val p = player()
    assertEquals(0, p.getServerMoves)
    assertEquals(1, p.recordServerMove())
    assertEquals(2, p.recordServerMove())
    assertEquals(2, p.getServerMoves)
  }

  @Test def characterSetsMaxHealth(): Unit = {
    val p = player()
    p.setCharacterId(CharacterId.Blacksmith.id)
    assertEquals(CharacterDef.get(CharacterId.Blacksmith).maxHealth, p.getMaxHealth)
  }
}
