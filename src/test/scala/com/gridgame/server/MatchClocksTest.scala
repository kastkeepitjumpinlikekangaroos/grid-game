package com.gridgame.server.game

import com.gridgame.common.Constants
import com.gridgame.common.model._
import com.gridgame.common.protocol._
import com.gridgame.server.{ServerTestKit, Sim, SimMatch}
import com.gridgame.server.net.Outbox
import org.junit.Assert._
import org.junit.Test

import java.util.UUID
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}

/**
 * What a match does on its clocks, and that a started match runs them. The match keeps one list of
 * what it does when (GameInstance.clocked), which `start` gives threads of their own and the tests'
 * clockwork plays on a clock of the test's own: so the first half here holds that list to the
 * rules, to the millisecond, and the second starts a real match and watches the system's clock do
 * the same for a few seconds — its first items, its ticks, a death and the life after it, and its
 * end. (In package `server.game` for the match's host and what it keeps to itself.)
 */
class MatchClocksTest {
  private def events(packets: Seq[Packet], eventType: Byte): Seq[GameEventPacket] =
    packets.collect { case g: GameEventPacket if g.getEventType == eventType => g }

  private def itemSpawns(packets: Seq[Packet]): Seq[ItemPacket] =
    packets.collect { case i: ItemPacket if i.getAction == ItemAction.SPAWN => i }

  // --- On a clock of the test's own ---

  @Test def aMatchTicksSpawnsItsItemsTellsTheTimeAndEndsWhenItsListSays(): Unit = ServerTestKit.quietly(Sim() { clock =>
    val m = new SimMatch(clock, WorldData.createEmpty(100, 100), minutes = 3, items = true, respawns = false)
    // Dead, so nothing is picked up from under the count
    val watcher = m.join(CharacterId.Gladiator, 50, 50)
    watcher.setHealth(0)
    val length = 3 * 60000L
    m.clearSent()

    m.advance(0)
    assertEquals("a batch as the match starts: five for a map of 100x100", 5, itemSpawns(m.sent(watcher)).size)
    assertEquals(5, m.instance.itemManager.size)

    m.advance(Constants.ITEM_SPAWN_INTERVAL_MS - 1)
    var heard = m.sent(watcher)
    assertEquals("and no more for a minute", 0, itemSpawns(heard).size)
    assertEquals("the projectile tick, every 30ms", (Constants.ITEM_SPAWN_INTERVAL_MS - 1) / 30, m.clockwork.ran("projectiles"))
    assertEquals("the player tick, every 200ms", (Constants.ITEM_SPAWN_INTERVAL_MS - 1) / 200, m.clockwork.ran("players"))
    assertEquals("the time left, said every ten seconds", Seq(170, 160, 150, 140, 130), events(heard, GameEvent.TIME_SYNC).map(_.getRemainingSeconds))

    m.advance(1)
    heard = m.sent(watcher)
    assertEquals("the next batch, on the minute", 5, itemSpawns(heard).size)
    assertEquals(Seq(120), events(heard, GameEvent.TIME_SYNC).map(_.getRemainingSeconds))

    m.advance(length - Constants.ITEM_SPAWN_INTERVAL_MS - 1)
    heard = m.sent(watcher)
    assertFalse("a millisecond from its end, it is still played", m.clockwork.isOver)
    assertEquals("the third minute's batch", 5, itemSpawns(heard).size)
    assertEquals((110 to 10 by -10).toSeq, events(heard, GameEvent.TIME_SYNC).map(_.getRemainingSeconds))
    assertEquals(1, m.instance.getRemainingSeconds)
    assertFalse(m.instance.isTimeUp)

    m.advance(1)
    heard = m.sent(watcher)
    assertTrue("over on the millisecond its time is up", m.clockwork.isOver)
    assertTrue(m.instance.isTimeUp)
    assertEquals(0, m.instance.getRemainingSeconds)
    assertEquals("everyone is told once that it is over", 1, events(heard, GameEvent.GAME_OVER).size)
    assertEquals("and nothing else of it is played on that millisecond: no tick, no items, no time", (0, 0),
      (itemSpawns(heard).size, events(heard, GameEvent.TIME_SYNC).size))
    assertEquals("every tick it had, and none on its last millisecond",
      Map("projectiles" -> (length / 30 - 1).toInt, "players" -> (length / 200 - 1).toInt, "items" -> 3, "time" -> 17),
      m.clockwork.ran.view.filterKeys(Set("projectiles", "players", "items", "time")).toMap)
  })

  @Test def aBatchOfItemsIsSizedToTheMapAndTheGroundHoldsFortyAtMost(): Unit = ServerTestKit.quietly(Sim() { clock =>
    // One to every 2000 cells, three at least and twenty at most
    for (((w, h), batch) <- Seq((20, 20) -> 3, (60, 60) -> 3, (100, 80) -> 4, (100, 100) -> 5, (200, 200) -> 20, (400, 300) -> 20)) {
      val m = new SimMatch(clock, WorldData.createEmpty(w, h), minutes = 5, items = true, respawns = false)
      assertEquals(s"a batch on a map of ${w}x$h", batch, m.instance.itemsPerBatch)
      m.advance(0)
      assertEquals(s"on the ground as a match on ${w}x$h starts", batch, m.instance.itemManager.size)
      m.advance(4 * 60000L)
      assertEquals(s"after four minutes on ${w}x$h, nobody there to pick them up", Math.min(40, 5 * batch), m.instance.itemManager.size)
    }
  })

  @Test def theTimePlayedAndTheTimeLeftAreCountedInWholeSeconds(): Unit = Sim() { clock =>
    // Left: the second that is running out, so it reads 0 only once the time is up. Played: the
    // seconds that have gone by whole, which is what a match's results are kept with
    val m = new SimMatch(clock, minutes = 2)
    assertEquals((120, 0), (m.instance.getRemainingSeconds, m.instance.getElapsedSeconds))
    m.advance(1)
    assertEquals((120, 0), (m.instance.getRemainingSeconds, m.instance.getElapsedSeconds))
    m.advance(999)
    assertEquals((119, 1), (m.instance.getRemainingSeconds, m.instance.getElapsedSeconds))
    m.advance(89500)
    assertEquals((30, 90), (m.instance.getRemainingSeconds, m.instance.getElapsedSeconds))
    assertEquals(30, m.instance.timeSyncPacket().getRemainingSeconds)
  }

  // --- On the system's ---

  /** Where a match of the test's own sends its packets and says it is over. */
  private final class Host extends MatchHost {
    def outbox: Outbox = ServerTestKit.server.outbox
    val ended = new LinkedBlockingQueue[(Short, Long)]()
    def endGame(lobbyId: Short): Unit = ended.add((lobbyId, System.nanoTime()))
  }

  private def waitUntil(what: String, withinMs: Long)(done: => Boolean): Long = {
    val from = System.nanoTime()
    while (!done) {
      if ((System.nanoTime() - from) / 1000000L > withinMs) fail(s"$what: not within ${withinMs}ms")
      Thread.sleep(5)
    }
    System.nanoTime()
  }

  @Test def aStartedMatchRunsItsClocksBringsBackItsDeadAndEndsItselfOnTime(): Unit = {
    val host = new Host
    val instance = new GameInstance(4242.toShort, "", 1, host)
    instance.world = WorldData.createEmpty(100, 100)
    instance.isPractice = true // no opening, and a second's wait for a new life
    instance.durationMs = 2600L
    def player(x: Int, y: Int): Player = {
      val p = new Player(UUID.randomUUID(), "p", new Position(x, y), 0xFF336699)
      p.setCharacterId(CharacterId.Gladiator.id)
      p.setHealth(p.getMaxHealth)
      instance.registry.add(p)
      p
    }
    val victim = player(20, 20)
    val poisoner = player(70, 70)
    // The threads a match's clocks run on (an executor's, named pool-N-thread-M), over whatever
    // the server the test kit shares already had running
    host.outbox
    def clockThreads: Set[Thread] = {
      import scala.jdk.CollectionConverters._
      Thread.getAllStackTraces.keySet.asScala.filter(t => t.isAlive && t.getName.startsWith("pool-")).toSet
    }
    val threadsBefore = clockThreads
    val errors = ServerTestKit.captureStderr(ServerTestKit.quietly {
      val startedAt = System.nanoTime()
      instance.start()
      assertTrue("a thread for each of its clocks, its respawns and its end", (clockThreads -- threadsBefore).size >= instance.clocked.size)
      try {
        assertEquals("its first items are on the ground as start returns", instance.itemsPerBatch, instance.itemManager.size)
        // The player tick: a poison that kills in well under a second
        victim.setHealth(5)
        victim.applyPoison(60, 600, 200, poisoner.getId)
        val diedAt = waitUntil("the poison's bites, on the player tick", 1500)(victim.isDead)
        // (Scored by the tick that killed them, a moment after the blow: another thread's)
        waitUntil("the kill scored", 1000)(instance.killTracker.getKills(poisoner.getId) == 1)
        Thread.sleep(500)
        assertTrue("still down half a second on", victim.isDead)
        val backAt = waitUntil("the new life, on the respawn clock", 2500)(!victim.isDead)
        assertTrue(s"a second after the death, or thereabouts: ${(backAt - diedAt) / 1000000L}ms", (backAt - diedAt) / 1000000L >= 900)
        assertEquals(victim.getMaxHealth, victim.getHealth)
        // Its end, by itself, when its time is up
        val over = host.ended.poll(4, TimeUnit.SECONDS)
        assertNotNull("the match said it was over", over)
        assertEquals(4242.toShort, over._1)
        val lastedMs = (over._2 - startedAt) / 1000000L
        assertTrue(s"when its 2600ms were up, and no sooner: ${lastedMs}ms", lastedMs >= 2600 && lastedMs < 4200)
        val ticks = instance.getProjectileTick
        assertTrue(s"a projectile tick every 30ms the while: $ticks of them", ticks >= 2600 / 30 / 2 && ticks <= lastedMs / 30 + 2)
      } finally instance.stop()
      val ticksAtTheStop = instance.getProjectileTick
      Thread.sleep(150)
      assertEquals("and stopped, it ticks no more", ticksAtTheStop, instance.getProjectileTick)
      assertFalse(instance.isRunning)
      waitUntil("every thread it ran on, gone with it", 3000)((clockThreads -- threadsBefore).isEmpty)
    })
    assertEquals("nothing went wrong on any of its threads", "", errors)
  }
}
