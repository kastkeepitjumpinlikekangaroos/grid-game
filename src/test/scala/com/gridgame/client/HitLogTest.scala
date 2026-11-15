package com.gridgame.client

import com.gridgame.client.game.HitLog
import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

import java.util.UUID

/** Every hit the client hears, in order, for the renderer to bleed (game/HitLog; render/CLAUDE.md, *A hit
  * draws blood*). */
class HitLogTest {
  @Test def everyHitIsHeldInTheOrderItWasHeard(): Unit = {
    val log = new HitLog
    val ids = (0 until 5).map(i => new UUID(0L, i))
    ids.zipWithIndex.foreach { case (id, i) => log.add(id, i.toFloat, 2f * i, 1f, 0f, i.toByte, 100L + i) }
    assertEquals(5L, log.heard)
    assertEquals(0L, log.oldest)
    (0 until 5).foreach { n =>
      assertEquals(ids(n), log.target(n))
      assertEquals(100L + n, log.time(n))
      assertEquals(n.toFloat, log.x(n), 0f)
      assertEquals(2f * n, log.y(n), 0f)
      assertEquals(n.toByte, log.projectileType(n))
    }
  }

  @Test def aReaderThatFallsBehindLosesOnlyTheOldest(): Unit = {
    val log = new HitLog
    val total = HitLog.Size + 10
    (0 until total).foreach(i => log.add(new UUID(0L, i), 0f, 0f, 0f, 0f, 0, i.toLong))
    assertEquals(total.toLong, log.heard)
    assertEquals("the ten oldest are gone", 10L, log.oldest)
    (log.oldest until log.heard).foreach(n => assertEquals(n, log.time(n)))
  }

  @Test def theClientLogsEveryHitWithWhereTheProjectileStruck(): Unit = {
    val t = new TestClient()
    t.startMatch(spawn = (5, 5))
    val shooter = UUID.randomUUID()
    val target = UUID.randomUUID()
    val before = t.client.hitLog.heard
    t.projectile(ProjectileAction.SPAWN, 60, shooter)
    t.projectile(ProjectileAction.MOVE, 60, shooter, x = 12f)
    assertEquals("a move strikes nobody", before, t.client.hitLog.heard)
    t.projectile(ProjectileAction.PIERCE, 60, shooter, x = 13f, y = 9f, target = target, dx = 0.6f, dy = 0.8f)
    t.projectile(ProjectileAction.HIT, 60, shooter, x = 14f, y = 9.5f, target = target, pType = ProjectileType.SHOVEL)
    t.projectile(ProjectileAction.HIT, 61, shooter, x = 3f, y = 3f) // no one struck: an explosive's own end
    val log = t.client.hitLog
    assertEquals("the pierce and the hit", before + 2, log.heard)
    val n = log.heard - 1
    assertEquals(target, log.target(n))
    assertEquals(14f, log.x(n), 0f); assertEquals(9.5f, log.y(n), 0f)
    assertEquals(ProjectileType.SHOVEL, log.projectileType(n))
    assertEquals(0.6f, log.dx(n - 1), 0f); assertEquals(0.8f, log.dy(n - 1), 0f)
    assertEquals(13f, log.x(n - 1), 0f)
  }
}
