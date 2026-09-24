package com.gridgame.client

import com.gridgame.client.render.NetProjectile
import com.gridgame.common.Constants
import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

import java.util.UUID
import scala.collection.mutable

/**
 * How the client flies a projectile between the server's ticks (NetProjectile). The server moves
 * every projectile once every 30ms and sends where it is. Drawn where the newest packet put it, a
 * shot stood still in one frame and jumped the next: at 60 fps nearly half the frames showed it
 * still, and each packet that came early or late moved a jump onto another frame.
 *
 * Packets here are the server's as it sends them (see [[shot]]), delivered as a network would,
 * while frames are drawn at 60 fps on the test's own clock.
 */
class ProjectileFlightTest {
  private val TICK = Constants.PROJECTILE_SPEED_MS.toDouble
  private val FRAME = 1000.0 / 60
  private val shooter = UUID.randomUUID()

  /** A packet from the server, and the match time (ms) it went out at. */
  private case class Sent(atMs: Double, p: ProjectilePacket)

  private def packet(action: Byte, id: Int, tick: Int, x: Float, y: Float, ux: Float, uy: Float,
                     pType: Byte): ProjectilePacket =
    new ProjectilePacket(0, shooter, tick, x, y, 0xFF112233, id, ux, uy, action, null, 0.toByte, pType)

  /**
   * A shot as the server flies one: fired `firedMs` into the match from (x, y) — its SPAWN, stamped
   * with the tick last run — and moved `speed` cells along (ux, uy) by every tick after, which sends
   * a MOVE at its end, up to and including `lastTick`. Tick k runs k * 30ms into the match.
   */
  private def shot(id: Int, firedMs: Double, x: Float, y: Float, ux: Float, uy: Float, speed: Float,
                   lastTick: Int, pType: Byte = ProjectileType.BULLET): Seq[Sent] = {
    val k0 = (firedMs / TICK).toInt
    Sent(firedMs, packet(ProjectileAction.SPAWN, id, k0, x, y, ux, uy, pType)) +:
      (k0 + 1 to lastTick).map { k =>
        val d = (k - k0) * speed
        Sent(k * TICK, packet(ProjectileAction.MOVE, id, k, x + ux * d, y + uy * d, ux, uy, pType))
      }
  }

  /** What the server sends at the end of tick `k` when the projectile stops at (x, y). */
  private def stop(id: Int, k: Int, x: Float, y: Float, ux: Float, uy: Float, pType: Byte = ProjectileType.BULLET): Sent =
    Sent(k * TICK, packet(ProjectileAction.DESPAWN, id, k, x, y, ux, uy, pType))

  /** Two seconds of somebody else's shot going by, which is how a match looks by the time anyone
    * fires: the client already knows the route's timing. */
  private def warmUp: Seq[Sent] = shot(1, 0, 5f, 3f, 1f, 0f, 1f, lastTick = 67)

  /**
   * The network delivering `sent` — each packet `latencyMs` after it went out plus its own
   * `jitter`, the `lost` ones never — while frames are drawn every FRAME ms up to `toMs`. Where
   * projectile `id` was drawn in each frame from `fromMs` on (None once it is gone).
   */
  private def play(t: TestClient, sent: Seq[Sent], id: Int, fromMs: Double, toMs: Double,
                   latencyMs: Double = 20, jitter: Int => Double = _ => 0.0,
                   lost: Int => Boolean = _ => false): IndexedSeq[Option[(Float, Float)]] = {
    val zero = t.nanos
    def at(ms: Double): Long = zero + (ms * 1e6).toLong
    val arrivals = sent.zipWithIndex.collect { case (s, i) if !lost(i) => (s.atMs + latencyMs + jitter(i), s.p) }
      .sortBy(_._1)
    val drawn = mutable.ArrayBuffer[Option[(Float, Float)]]()
    var next = 0
    var frameMs = 0.0
    while (frameMs <= toMs) {
      while (next < arrivals.length && arrivals(next)._1 <= frameMs) {
        t.nanos = at(arrivals(next)._1)
        t.client.processPacket(arrivals(next)._2)
        next += 1
      }
      t.nanos = at(frameMs)
      t.client.flyProjectiles(t.nanos, FRAME / 1000)
      if (frameMs >= fromMs)
        drawn += Option(t.client.getProjectiles.get(id)).map(p => (p.getX, p.getY))
      frameMs += FRAME
    }
    drawn.toIndexedSeq
  }

  /** How far it went from each drawn frame to the next. */
  private def steps(drawn: Seq[Option[(Float, Float)]]): Seq[Float] = {
    val at = drawn.flatten
    (1 until at.length).map(i => Math.hypot(at(i)._1 - at(i - 1)._1, at(i)._2 - at(i - 1)._2).toFloat)
  }

  /** The time of the last frame drawn by `toMs`. */
  private def lastFrame(toMs: Double): Double = (toMs / FRAME).toInt * FRAME

  private def match_(world: WorldData = WorldData.createEmpty(80, 40)): TestClient = {
    val t = new TestClient(world)
    t.startMatch(spawn = (40, 36))
    t
  }

  private def net(t: TestClient, id: Int): NetProjectile = t.client.getProjectiles.get(id).asInstanceOf[NetProjectile]

  // Jitter of up to 12ms a packet, the least of it none
  private val jittery: Int => Double = i => (i * 2 % 13).toDouble

  // --- In flight ---

  @Test def aStraightShotGoesAsFarInEveryFrameHoweverItsPacketsArrive(): Unit = {
    val t = match_()
    val sent = warmUp ++ shot(7, 2000, 5f, 20f, 1f, 0f, 1f, lastTick = 83)
    // A packet in seven lost, and every one up to 12ms late
    val drawn = play(t, sent, 7, fromMs = 2250, toMs = 2480, jitter = jittery, lost = i => i % 7 == 3)
    val perFrame = (FRAME / TICK).toFloat // a cell a tick
    val s = steps(drawn)
    assertTrue(s.nonEmpty)
    s.foreach(step => assertEquals("a cell a tick, every frame", perFrame, step, 0.005f))
  }

  @Test def packetsArrivingLateChangeNothingOnScreen(): Unit = {
    // The same shot over a steady network and a jittery one: its news arrives at other moments,
    // and every frame shows it in the same place
    val sent = warmUp ++ shot(7, 2000, 5f, 20f, 1f, 0f, 1f, lastTick = 83)
    val steady = play(match_(), sent, 7, fromMs = 2250, toMs = 2480)
    val shaken = play(match_(), sent, 7, fromMs = 2250, toMs = 2480, jitter = jittery)
    assertEquals(steady.length, shaken.length)
    for ((a, b) <- steady.flatten.zip(shaken.flatten)) {
      assertEquals(a._1, b._1, 1e-3f)
      assertEquals(a._2, b._2, 1e-3f)
    }
  }

  @Test def itLeavesFromWhereItWasFiredAndCatchesUp(): Unit = {
    val t = match_()
    // Fired 20ms into a tick: by the time the SPAWN is here it is most of a cell on its way
    val sent = warmUp ++ shot(7, 2020, 5f, 20f, 1f, 0f, 1f, lastTick = 83)
    val drawn = play(t, sent, 7, fromMs = 2020, toMs = 2390).flatten
    assertEquals("first drawn where it was fired", 5f, drawn.head._1, 1e-3f)
    // and a few frames on, where the server has it: a cell for every tick since its SPAWN's (67),
    // on the route's 20ms
    val expected = 5f + ((lastFrame(2390) - 20) / TICK - 67).toFloat
    assertEquals(expected, drawn.last._1, 0.01f)
    // having got there without ever going backwards
    for (i <- 1 until drawn.length) assertTrue(drawn(i)._1 >= drawn(i - 1)._1)
  }

  @Test def aLostPacketOrTwoDoNotHoldItUp(): Unit = {
    val t = match_()
    val sent = warmUp ++ shot(7, 2000, 5f, 20f, 1f, 0f, 1f, lastTick = 83)
    // Its moves from ticks 76 and 77 lost, sixty milliseconds with no news: its SPAWN is packet
    // warmUp.length, and the move from tick 67 the one after
    def moveOf(k: Int): Int = warmUp.length + 1 + (k - 67)
    val drawn = play(t, sent, 7, fromMs = 2250, toMs = 2480, lost = i => i == moveOf(76) || i == moveOf(77))
    steps(drawn).foreach(step => assertEquals((FRAME / TICK).toFloat, step, 0.005f))
  }

  @Test def aMoveOlderThanWhatItHasIsIgnored(): Unit = {
    val t = match_()
    t.projectile(ProjectileAction.MOVE, 9, shooter, x = 20f, tick = 110)
    t.projectile(ProjectileAction.MOVE, 9, shooter, x = 19f, tick = 109) // overtaken on the way
    assertEquals(110, net(t, 9).heardTick)
    assertEquals(20f, net(t, 9).heardX, 0f)
    // and a SPAWN that arrives after its first move doesn't take it back to where it was fired
    t.projectile(ProjectileAction.SPAWN, 9, shooter, x = 15f, tick = 105)
    assertEquals(20f, net(t, 9).heardX, 0f)
  }

  @Test def aMoveFromTheTickItsSpawnIsStampedWithIsNewer(): Unit = {
    // Fired while a tick ran, and moved by that same tick
    val t = match_()
    t.projectile(ProjectileAction.SPAWN, 9, shooter, x = 15f, tick = 105)
    t.projectile(ProjectileAction.MOVE, 9, shooter, x = 16f, tick = 105)
    assertEquals(16f, net(t, 9).heardX, 0f)
  }

  @Test def underAGemItFliesTwiceAsFast(): Unit = {
    // The server moves the projectiles of an owner with a gem twice a tick. Its 18 cells are gone
    // by tick 75, so what is left of its launch is still being smoothed away here
    val t = match_()
    t.join(shooter, 10, 10)
    t.update(shooter, 10, 10, flags = 0x02) // a gem
    val sent = warmUp ++ shot(7, 2000, 5f, 20f, 1f, 0f, 2f, lastTick = 74)
    val drawn = play(t, sent, 7, fromMs = 2200, toMs = 2250)
    assertEquals(2, net(t, 7).heardSteps)
    steps(drawn).foreach(step => assertEquals(2f * (FRAME / TICK).toFloat, step, 0.02f))
  }

  @Test def aGemNobodyToldItOfShowsInTwoMoves(): Unit = {
    val t = match_()
    t.projectile(ProjectileAction.SPAWN, 9, shooter, x = 5f, tick = 100, pType = ProjectileType.BULLET)
    assertEquals("whose it is isn't known", 1, net(t, 9).heardSteps)
    t.projectile(ProjectileAction.MOVE, 9, shooter, x = 7f, tick = 101, pType = ProjectileType.BULLET)
    t.projectile(ProjectileAction.MOVE, 9, shooter, x = 9f, tick = 102, pType = ProjectileType.BULLET)
    assertEquals("two cells a tick is two steps of a bullet's one", 2, net(t, 9).heardSteps)
  }

  // --- Where it stops ---

  @Test def itIsNeverFlownIntoTheWallItStrikesAndFadesOnItsFace(): Unit = {
    val world = WorldData.createEmpty(80, 40)
    for (y <- 0 until 40) world.setTile(30, y, Tile.Wall)
    val t = match_(world)
    // From x 15, a cell a tick: at x 29 after tick 80, and tick 81's first half-cell sub-step
    // ends in the wall's cell (30 covers 29.5 to 30.5), where the server stops it
    val sent = warmUp ++ shot(7, 2000, 15f, 20f, 1f, 0f, 1f, lastTick = 80) :+ stop(7, 81, 29.5f, 20f, 1f, 0f)
    val drawn = play(t, sent, 7, fromMs = 2250, toMs = 2600, jitter = jittery).flatten
    val face = 29.45f // where the DESPAWN walks it back to, the last point clear of the wall
    drawn.foreach { case (x, _) => assertTrue(s"drawn at $x, inside the wall", x <= face + 1e-3f) }
    assertEquals("it was drawn reaching the face", face, drawn.map(_._1).max, 1e-3f)
    val fading = t.client.getFadingProjectiles.get(7)
    assertNotNull(fading)
    assertEquals("and fades where it was last drawn", face, fading.proj.getX, 1e-3f)
  }

  @Test def itIsNeverFlownPastTheEndOfItsRange(): Unit = {
    val t = match_()
    // A bullet goes 18 cells: 17 moves, and the server stops it at the end of the next tick's
    // sub-step that reaches 18
    val sent = warmUp ++ shot(7, 2000, 5f, 20f, 1f, 0f, 1f, lastTick = 83) :+ stop(7, 84, 23f, 20f, 1f, 0f)
    val drawn = play(t, sent, 7, fromMs = 2250, toMs = 2700, jitter = jittery)
    drawn.flatten.foreach { case (x, _) => assertTrue(s"drawn at $x, past its range", x <= 23f + 1e-3f) }
    assertEquals(23f, net2(t, 7).getX, 1e-3f)
  }

  /** The projectile, flying or fading. */
  private def net2(t: TestClient, id: Int): Projectile = {
    val p = t.client.getProjectiles.get(id)
    if (p != null) p else t.client.getFadingProjectiles.get(id).proj
  }

  @Test def howFarItHasComeIsCountedAsTheServerCountsIt(): Unit = {
    // The renderer's lifetime effects (the burn-out at the end of its range, a trail that grows)
    // read it, and on the client it used to stay 0
    val t = match_()
    val sent = warmUp ++ shot(7, 2000, 5f, 20f, 1f, 0f, 1f, lastTick = 83)
    play(t, sent, 7, fromMs = 2000, toMs = 2300)
    val p = net(t, 7)
    // (less what is left of the launch still being smoothed away, which is only where it's drawn)
    assertEquals("it is as far from where it was fired as it has flown", p.getX - 5f, p.getDistanceTraveled, 0.01f)
    assertTrue(p.getDistanceTraveled > 5f)
  }

  @Test def itIsNeverFlownThroughTheDivider(): Unit = {
    val world = WorldData.createEmpty(80, 40)
    val t = match_(world)
    world.divider = TeamDivider.raise(world, System.currentTimeMillis() + 60000) // x = 40
    val sent = warmUp ++ shot(7, 2000, 30f, 20f, 1f, 0f, 1f, lastTick = 75)
    val drawn = play(t, sent, 7, fromMs = 2100, toMs = 2500).flatten
    drawn.foreach { case (x, _) => assertTrue(s"drawn at $x, over the line", x <= 40f - 0.05f + 1e-3f) }
  }

  @Test def itIsNeverFlownThroughABarrierThatStopsIt(): Unit = {
    // A barrier facing the shot, its middle 2 cells out in front of its holder at x 40: the server
    // stops the shot on its face, and says so a tick or so after the shot has reached it
    val t = match_()
    val holder = UUID.randomUUID()
    t.join(holder, 40, 20)
    t.update(holder, 40, 20, flags2 = 0x04, aimAngle = Math.PI, barrierMs = 3000)
    // From x 25 its range would take it to 43, past the barrier and its holder; its moves stop at
    // 37, and the BLOCKED hasn't come yet
    val sent = warmUp ++ shot(7, 2000, 25f, 20f, 1f, 0f, 1f, lastTick = 78)
    val drawn = play(t, sent, 7, fromMs = 2100, toMs = 2500).flatten
    drawn.foreach { case (x, _) => assertTrue(s"drawn at $x, through the barrier", x <= 38f - 0.05f + 1e-3f) }
    assertEquals("held on its face", 37.95f, drawn.last._1, 1e-3f)
  }

  @Test def aTeammatesBarrierLetsTheShotThrough(): Unit = {
    val t = new TestClient(WorldData.createEmpty(80, 40))
    t.startMatch(spawn = (40, 36), team = 1)
    val holder = UUID.randomUUID()
    t.join(holder, 40, 20, team = 1)
    t.update(holder, 40, 20, flags2 = 0x04, aimAngle = Math.PI, barrierMs = 3000)
    // Our own shot, at the barrier of someone on our side
    val sent = warmUp ++ shot(7, 2000, 25f, 20f, 1f, 0f, 1f, lastTick = 78).map(s =>
      s.copy(p = new ProjectilePacket(0, t.id, s.p.getTick, s.p.getX, s.p.getY, 0, 7, 1f, 0f, s.p.getAction, null,
        0.toByte, ProjectileType.BULLET)))
    val drawn = play(t, sent, 7, fromMs = 2100, toMs = 2500).flatten
    assertTrue("flown on past it", drawn.map(_._1).max > 39f)
  }

  @Test def aBoomerangTurnsAtTheEndOfItsRangeAndCountsItsWayBack(): Unit = {
    val t = match_()
    // 0.7 cells a tick in half-cell sub-steps of 0.35: it turns at the end of the sub-step that
    // reaches 12 cells, 12.25 out, in the second half of its 18th tick
    val out = shot(7, 2010, 5f, 20f, 1f, 0f, 0.7f, lastTick = 84, pType = ProjectileType.BOOMERANG_BLADE)
    val k0 = 67
    val apex = 5f + 12.25f
    val back = (k0 + 18 to k0 + 24).map { k =>
      val x = apex - 0.35f - (k - (k0 + 18)) * 0.7f
      Sent(k * TICK, packet(ProjectileAction.MOVE, 7, k, x, 20f, -1f, 0f, ProjectileType.BOOMERANG_BLADE))
    }
    val drawn = play(t, warmUp ++ out ++ back, 7, fromMs = 2100, toMs = k0 * TICK + 24 * TICK)
    drawn.flatten.foreach { case (x, _) => assertTrue(s"drawn at $x, past where it turned", x <= apex + 1e-3f) }
    val p = net(t, 7)
    assertTrue("on its way back", p.isReturning)
    assertTrue("counting from where it turned", p.getDistanceTraveled < 6f)
    assertTrue("and heading home", p.dx < 0f)
  }

  // --- When the news that ended it never came ---

  @Test def aProjectileWhoseEndWasLostFadesOut(): Unit = {
    // Every packet about projectiles is a datagram, and one lost HIT or DESPAWN used to leave the
    // projectile hanging in the air, where it last was, for the rest of the match
    val t = match_()
    val sent = warmUp ++ shot(7, 2000, 5f, 20f, 1f, 0f, 1f, lastTick = 75) // then nothing
    val drawn = play(t, sent, 7, fromMs = 2100, toMs = 75 * TICK + 20 + 400)
    assertTrue("drawn while it was heard of", drawn.head.isDefined)
    assertTrue("and gone once it wasn't", drawn.last.isEmpty)
    assertNotNull("fading out", t.client.getFadingProjectiles.get(7))
    // Only the news had stalled: its next move brings it back
    t.projectile(ProjectileAction.MOVE, 7, shooter, x = 40f, y = 20f, tick = 95, pType = ProjectileType.BULLET)
    assertNotNull(t.client.getProjectiles.get(7))
  }
}
