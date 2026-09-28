package com.gridgame.client

import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

import java.util.UUID

/**
 * Which of the projectiles we ask the server for it answers (PendingSpawns, the client's
 * `gridgame.client.spawn_requests`): the first packet about a projectile of ours we hadn't heard
 * of answers the oldest request for its type, and a request nothing answers within a second is
 * counted as unanswered.
 */
class GameClientSpawnRequestsTest {
  private val t = new TestClient()
  private val c = t.client
  private def spawns = c.pendingSpawns

  private val soldier = CharacterDef.get(CharacterId.Soldier)
  private val shot = soldier.primaryProjectileType

  private def play(character: CharacterDef = soldier): Unit = {
    c.selectedCharacterId = character.id.id
    t.startMatch(spawn = (20, 20))
    t.clearSent()
  }

  private def later(ms: Long): Unit = t.nanos += ms * 1000000L

  /** The server's word of projectile `id`, one of ours. */
  private def ours(id: Int, pType: Byte = shot, action: Byte = ProjectileAction.SPAWN): Unit =
    t.projectile(action, id, t.id, pType = pType)

  @Test def aShotTheServerFiredIsAnsweredOnce(): Unit = {
    play()
    c.shootToward(1f, 0f)
    later(80)
    ours(70)
    later(1000)
    spawns.expire(t.nanos)
    assertEquals(1L, spawns.answered(AttackSlot.PRIMARY))
    assertEquals(0L, spawns.unanswered)
  }

  @Test def aShotNothingCameBackForIsUnansweredOnceItsSecondIsUp(): Unit = {
    play()
    c.shootToward(1f, 0f)
    later(999)
    spawns.expire(t.nanos)
    assertEquals("not before its second is up", 0L, spawns.unanswered)
    later(1)
    c.shootToward(1f, 0f) // the next request counts it
    assertEquals(1L, spawns.unanswered(AttackSlot.PRIMARY))
    assertEquals(0L, spawns.answered)
  }

  @Test def aShotWhoseSpawnWasLostIsAnsweredByWhateverOfItComesFirst(): Unit = {
    // Every projectile packet is a datagram, and a shot whose SPAWN was lost flew all the same:
    // its first MOVE, or its end, is the server's answer
    play()
    c.shootToward(1f, 0f)
    ours(71, action = ProjectileAction.MOVE)
    c.shootToward(1f, 0f)
    ours(72, action = ProjectileAction.DESPAWN)
    later(1000)
    spawns.expire(t.nanos)
    assertEquals(2L, spawns.answered)
    assertEquals(0L, spawns.unanswered)
  }

  @Test def moreNewsOfAShotAnswersNoOtherShot(): Unit = {
    play()
    c.shootToward(1f, 0f)
    ours(73)
    c.shootToward(1f, 0f) // lost on the way
    ours(73, action = ProjectileAction.MOVE)
    t.projectile(ProjectileAction.HIT, 73, t.id, target = UUID.randomUUID(), pType = shot)
    later(1000)
    spawns.expire(t.nanos)
    assertEquals(1L, spawns.answered)
    assertEquals(1L, spawns.unanswered)
  }

  @Test def someoneElsesProjectileAnswersNothing(): Unit = {
    play()
    c.shootToward(1f, 0f)
    t.projectile(ProjectileAction.SPAWN, 74, UUID.randomUUID(), pType = shot)
    later(1000)
    spawns.expire(t.nanos)
    assertEquals(0L, spawns.answered)
    assertEquals(1L, spawns.unanswered)
  }

  @Test def anAnswerIsForTheAttackThatAskedForItsKind(): Unit = {
    // A primary shot lost, then a Q answered: the Q's projectile answers the Q, not the older shot
    val caster = CharacterDef.all.find(d => d.qAbility.castBehavior == StandardProjectile &&
      d.qAbility.projectileType != d.primaryProjectileType).get
    play(caster)
    c.shootToward(1f, 0f)
    c.shootAbility(0)
    assertEquals(2, t.sentSpawns.size)
    ours(75, pType = caster.qAbility.projectileType)
    later(1000)
    spawns.expire(t.nanos)
    assertEquals(1L, spawns.answered(AttackSlot.Q))
    assertEquals(1L, spawns.unanswered(AttackSlot.PRIMARY))
    assertEquals(0L, spawns.answered(AttackSlot.PRIMARY))
  }

  @Test def aFanIsAnsweredProjectileByProjectile(): Unit = {
    val fanner = CharacterDef.all.find(_.qAbility.castBehavior.isInstanceOf[FanProjectile]).get
    play(fanner)
    c.shootAbility(0)
    val n = t.sentSpawns.size
    assertTrue(s"a fan of $n", n > 1)
    (0 until n - 1).foreach(i => ours(80 + i, pType = fanner.qAbility.projectileType))
    later(1000)
    spawns.expire(t.nanos)
    assertEquals(n - 1L, spawns.answered(AttackSlot.Q))
    assertEquals(1L, spawns.unanswered(AttackSlot.Q))
  }

  @Test def whatWeFiredAsWeDiedIsNotCountedAsLost(): Unit = {
    // The server had killed us before the shot got there, and refused it for that
    play()
    c.shootToward(1f, 0f)
    later(50)
    t.update(t.id, 20, 20, health = 0)
    later(1000)
    spawns.expire(t.nanos)
    assertEquals(0L, spawns.unanswered)
    assertEquals(0L, spawns.answered)
  }

  @Test def aShotPastItsSecondWhenWeDiedWasStillUnanswered(): Unit = {
    play()
    c.shootToward(1f, 0f)
    later(1200)
    t.update(t.id, 20, 20, health = 0)
    assertEquals(1L, spawns.unanswered)
  }

  @Test def aNewMatchNumbersItsProjectilesAfresh(): Unit = {
    play()
    c.shootToward(1f, 0f)
    ours(5)
    play() // the next match: the server counts its projectiles from the start again
    c.shootToward(1f, 0f)
    ours(5)
    assertEquals(2L, spawns.answered)
    assertEquals(0L, spawns.unanswered)
  }
}
