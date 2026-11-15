package com.gridgame.client.render

import com.gridgame.client.TestClient
import com.gridgame.client.render.world.{Blood, BloodPainter, PlayerPainter}
import com.gridgame.common.model._
import com.gridgame.common.protocol.ProjectileAction
import org.junit.Assert._
import org.junit.Test

import java.lang.management.ManagementFactory
import java.util.UUID

/**
 * The blood a hit draws (Blood, BloodPainter; render/CLAUDE.md, *A hit draws blood*): drops thrown
 * from the middle of the struck figure, away from the projectile, each arcing to the ground and
 * leaving a splat where it lands on ground anyone can walk on; worked out once, from the hit's own
 * seed, and placed by the time since the hit.
 */
class BloodTest {
  private val world = WorldData.createEmpty(40, 40)
  private val Body = (20.0, 20.0)

  /** A spurt from a figure at `at` struck by `pType` at `hit` going `heading`: its drops. */
  private def spurt(blood: Blood, pType: Byte, hit: (Float, Float), heading: (Float, Float), seed: Long = 7L,
                    lift: Double = 0.0, at: (Double, Double) = Body, w: WorldData = world): IndexedSeq[Int] = {
    blood.spurt(1000L, at._1, at._2, lift, hit._1, hit._2, heading._1, heading._2, pType, seed, w)
    val s = blood.oldestFirst(Blood.MaxSpurts - 1) // the newest
    (0 until blood.drops(s)).map(d => blood.drop(s, d))
  }

  /** Where the drops land, on average, from the figure: which way the spurt went. */
  private def meanLanding(blood: Blood, drops: Seq[Int], at: (Double, Double) = Body): (Double, Double) =
    (drops.map(i => blood.landX(i) - at._1).sum / drops.size, drops.map(i => blood.landY(i) - at._2).sum / drops.size)

  private def angleOff(v: (Double, Double), heading: (Double, Double)): Double =
    Math.abs(Math.IEEEremainder(Math.atan2(v._2, v._1) - Math.atan2(heading._2, heading._1), 2 * Math.PI))

  // --- A drop's flight ---

  @Test def aDropLeavesTheMiddleOfTheFigureAndArcsToTheGround(): Unit = {
    val blood = new Blood
    val drops = spurt(blood, ProjectileType.ARROW, (18.2f, 20f), (1f, 0f))
    assertEquals(Blood.dropsFor(ProjectileType.ARROW), drops.size)
    drops.foreach { i =>
      blood.flight(i, 0f)
      assertTrue(s"leaves the middle of the figure (16.5 above its soles), not ${blood.z}", blood.z >= 12f && blood.z <= 21f)
      assertEquals(Body._1, blood.x, 0.1); assertEquals(Body._2, blood.y, 0.1)
      val landsAt = blood.landsAfter(i)
      var t = 0f
      while (t < landsAt - 0.005f) {
        blood.flight(i, t)
        assertTrue(s"in the air until it lands, at $t of $landsAt", blood.z > 0f)
        t += 0.005f
      }
      blood.flight(i, landsAt)
      assertEquals("on the ground as it lands", 0f, blood.z, 0.01f)
      assertEquals("where its splat is", blood.landX(i), blood.x, 1e-4f)
      assertEquals(blood.landY(i), blood.y, 1e-4f)
    }
  }

  @Test def aDropIsDrawnOutAlongTheWayItIsGoing(): Unit = {
    // The painter stretches each drop along its velocity on the screen: it must be the drop's own
    val blood = new Blood
    def screen(i: Int, t: Float): (Double, Double) = {
      blood.flight(i, t)
      ((blood.x - blood.y) * 20.0, (blood.x + blood.y) * 10.0 - blood.z)
    }
    spurt(blood, ProjectileType.ARROW, (18.2f, 21f), (1f, -0.5f)).foreach { i =>
      var t = 0.002f
      while (t < blood.landsAfter(i) - 0.002f) {
        val (x0, y0) = screen(i, t - 0.001f); val (x1, y1) = screen(i, t + 0.001f)
        blood.velocity(i, t)
        assertEquals(s"across, at $t", (x1 - x0) / 0.002, blood.vsx.toDouble, 2.0)
        assertEquals(s"down, at $t", (y1 - y0) / 0.002, blood.vsy.toDouble, 2.0)
        t += 0.02f
      }
    }
  }

  // --- Which way ---

  /** Which way every one of 40 hits alike sprayed (the mean of where its drops landed), and whether
    * each had a drop or two go back the other way along `heading`. */
  private def sprays(pType: Byte, hit: (Float, Float), heading: (Float, Float)): Seq[((Double, Double), Boolean)] = {
    val blood = new Blood
    (0 until 40).map { seed =>
      val drops = spurt(blood, pType, hit, heading, seed = seed * 7919L)
      val back = drops.exists(i => (blood.landX(i) - Body._1) * heading._1 + (blood.landY(i) - Body._2) * heading._2 < 0)
      (meanLanding(blood, drops), back)
    }
  }

  @Test def aShotSpraysOnTheWayItWasGoing(): Unit = {
    sprays(ProjectileType.ARROW, ((Body._1 - 1.2 * 0.6).toFloat, (Body._2 - 1.2 * 0.8).toFloat), (0.6f, 0.8f)).foreach {
      case (mean, back) =>
        assertTrue(s"out the far side: $mean", angleOff(mean, (0.6, 0.8)) < 0.5)
        assertTrue("and a drop or two back out the way the shot came in", back)
    }
  }

  @Test def aShotThatStrikesFromPastTheMiddleOfAFigureStillSpraysOnward(): Unit = {
    // A melee swing starts a cell out from its swinger, and its hit circle can find the figure from
    // just past its middle: the shot was still going on, and so does the blood
    sprays(ProjectileType.ARROW, ((Body._1 + 0.6).toFloat, Body._2.toFloat), (1f, 0f)).foreach { case (mean, _) =>
      assertTrue(s"onward, east: $mean", angleOff(mean, (1.0, 0.0)) < 0.5)
    }
  }

  @Test def aBlastSpraysAwayFromWhereItWentOff(): Unit = {
    // A grenade flying east goes off two cells east of them: it strikes them from the east
    assertTrue(Blood.isBlast(ProjectileType.GRENADE))
    sprays(ProjectileType.GRENADE, ((Body._1 + 2).toFloat, (Body._2 + 0.5).toFloat), (1f, 0f)).foreach { case (mean, _) =>
      assertTrue(s"away from the blast, not on the way the grenade was going: $mean", angleOff(mean, (-2.0, -0.5)) < 0.5)
    }
  }

  @Test def aSlamOnTopOfThemSpraysAllRound(): Unit = {
    // A slam has no heading, and goes off where it was cast: on top of whoever stands there
    val blood = new Blood
    var all = Seq.empty[(Double, Double)]
    (0 until 30).foreach { seed =>
      val drops = spurt(blood, ProjectileType.SHOCKWAVE, (Body._1.toFloat, Body._2.toFloat), (0f, 0f), seed = seed)
      all ++= drops.map(i => (blood.landX(i) - Body._1, blood.landY(i) - Body._2))
    }
    val reach = all.map { case (x, y) => Math.hypot(x, y) }.sum / all.size
    val (mx, my) = (all.map(_._1).sum / all.size, all.map(_._2).sum / all.size)
    assertTrue(s"no way more than another: off by ($mx, $my) of a reach of $reach", Math.hypot(mx, my) < reach * 0.15)
    Seq((1, 1), (1, -1), (-1, 1), (-1, -1)).foreach { case (sx, sy) =>
      val share = all.count { case (x, y) => x * sx > 0 && y * sy > 0 }.toDouble / all.size
      assertTrue(s"some on every side: ($sx, $sy) has $share", share > 0.12)
    }
  }

  // --- How much ---

  @Test def whatDealsNoDamageDrawsNoBlood(): Unit = {
    // A haste and nothing else never strikes anyone; the rule is any blow that can hurt bleeds
    def canHurt(d: ProjectileDef): Boolean =
      d.damage > 0 || d.effectiveDamage(100) > 0 || d.distanceDamageScaling.exists(_.maxDamage > 0) ||
        d.aoeOnHit.exists(_.damage > 0) || d.aoeOnMaxRange.exists(_.damage > 0) ||
        d.explosionConfig.exists(e => e.centerDamage > 0 || e.edgeDamage > 0)
    val thrown = CharacterDef.all.flatMap(c => Seq(c.primaryProjectileType, c.qAbility.projectileType, c.eAbility.projectileType))
      .filterNot(t => t >= -4 && t < 0).distinct
    assertTrue(thrown.size > 100)
    thrown.foreach { t =>
      val d = ProjectileDef.get(t)
      val drops = Blood.dropsFor(t)
      if (canHurt(d)) assertTrue(s"${d.name} bleeds 5 to ${Blood.MaxDrops}, not $drops", drops >= 5 && drops <= Blood.MaxDrops)
      else assertEquals(s"${d.name} hurts nobody", 0, drops)
    }
    assertEquals(0, Blood.dropsFor(ProjectileType.LUCKY_STREAK))
    assertEquals(0, Blood.dropsFor(ProjectileType.OVERCLOCK_BEAM))
    val blood = new Blood
    blood.spurt(1000L, 20, 20, 0, 19, 20, 1, 0, ProjectileType.LUCKY_STREAK, 1L, world)
    assertEquals("nothing spurted", Blood.NoHit, blood.newestHit)
  }

  @Test def aHarderBlowBleedsMore(): Unit = {
    // The Shovel strikes for 55, an arrow for 25, a slam's splash for 22
    assertTrue(Blood.dropsFor(ProjectileType.SHOVEL) > Blood.dropsFor(ProjectileType.ARROW))
    assertTrue(Blood.dropsFor(ProjectileType.ARROW) >= Blood.dropsFor(ProjectileType.SHOCKWAVE))
  }

  // --- Where it lands ---

  @Test def splatsOnlyOnGroundAnyoneCanWalkOn(): Unit = {
    // Water to the east, a wall to the south, the edge of the map to the west: no splat in any of
    // them, as on the ground they reach across
    val w = WorldData.createEmpty(40, 40)
    for (y <- 0 until 40; x <- 22 until 30) w.setTile(x, y, Tile.Water)
    for (x <- 0 until 22; y <- 22 until 26) w.setTile(x, y, Tile.Wall)
    val blood = new Blood
    var splatted = 0; var not = 0
    for (seed <- 0 until 20; (at, heading) <- Seq(((21.3, 15.0), (1f, 0f)), ((15.0, 21.4), (0f, 1f)), ((0.4, 10.0), (-1f, 0f)))) {
      spurt(blood, ProjectileType.ARROW, ((at._1 - heading._1).toFloat, (at._2 - heading._2).toFloat), heading, seed, at = at, w = w)
        .foreach { i =>
          val cx = Math.floor(blood.landX(i) + 0.5).toInt; val cy = Math.floor(blood.landY(i) + 0.5).toInt
          assertEquals(s"a drop landing on ($cx, $cy)", w.isTileWalkable(cx, cy), blood.splats(i))
          if (blood.splats(i)) splatted += 1 else not += 1
        }
    }
    assertTrue(s"both kinds of landing seen: $splatted splats, $not none", splatted > 50 && not > 50)
  }

  // --- Made once ---

  @Test def theSameHitSpurtsTheSameWay(): Unit = {
    val id = new UUID(3L, 4L)
    val a = new Blood; val b = new Blood; val c = new Blood
    val da = spurt(a, ProjectileType.ARROW, (18f, 20f), (1f, 0f), seed = Blood.seedOf(id, 5000L, 9L))
    val db = spurt(b, ProjectileType.ARROW, (18f, 20f), (1f, 0f), seed = Blood.seedOf(id, 5000L, 9L))
    val dc = spurt(c, ProjectileType.ARROW, (18f, 20f), (1f, 0f), seed = Blood.seedOf(id, 5000L, 10L))
    def landings(bl: Blood, ds: Seq[Int]) = ds.map(i => (bl.landX(i), bl.landY(i), bl.radius(i), bl.landsAfter(i)))
    assertEquals(landings(a, da), landings(b, db))
    assertNotEquals("the next hit heard, another way", landings(a, da), landings(c, dc))
  }

  @Test def aKnockedUpFigureBleedsFromWhereItIsDrawn(): Unit = {
    // A Launch lifts its figure off its shadow: the wound is up there with it
    val blood = new Blood
    spurt(blood, ProjectileType.ARROW, (18f, 20f), (1f, 0f), lift = 30.0).foreach { i =>
      blood.flight(i, 0f)
      assertTrue(s"up with the figure: ${blood.z}", blood.z >= 42f && blood.z <= 51f)
    }
  }

  @Test def noDropIsInTheAirLongerThanTheAirIsDrawn(): Unit = {
    // BloodPainter stops looking for drops in the air AirMs after their hit: the fountain of a slam,
    // thrown from a figure knocked up as high as it goes, is the longest any is up
    val blood = new Blood
    var longest = 0f
    for (seed <- 0 until 300; lift <- Seq(0.0, PlayerPainter.AirLiftPx);
         (t, hit, heading) <- Seq((ProjectileType.ARROW, (18f, 20f), (1f, 0f)), (ProjectileType.SHOCKWAVE, (20f, 20f), (0f, 0f)),
           (ProjectileType.GRENADE, (22f, 20f), (1f, 0f))))
      spurt(blood, t, hit, heading, seed, lift).foreach(i => longest = Math.max(longest, blood.landsAfter(i)))
    assertTrue(s"the longest flight is ${longest * 1000}ms", longest * 1000 < BloodPainter.AirMs)
  }

  @Test def aSpurtTakesTheOldestsPlace(): Unit = {
    val blood = new Blood
    (0 to Blood.MaxSpurts).foreach(k => blood.spurt(k.toLong, 20, 20, 0, 19, 20, 1, 0, ProjectileType.ARROW, k, world))
    assertEquals("the first is gone, the second the oldest", 1L, blood.hitTime(blood.oldestFirst(0)))
    assertEquals(Blood.MaxSpurts.toLong, blood.hitTime(blood.oldestFirst(Blood.MaxSpurts - 1)))
    assertEquals(Blood.MaxSpurts.toLong, blood.newestHit)
    blood.clear()
    assertEquals(Blood.NoHit, blood.newestHit)
    assertTrue((0 until Blood.MaxSpurts).forall(s => blood.hitTime(s) == Blood.NoHit))
  }

  @Test def nothingIsAllocatedOnceMade(): Unit = {
    // A spurt is made on the render thread, and every frame reads every drop: neither may allocate
    val blood = new Blood
    var sink = 0f
    def work(n: Int): Unit = {
      blood.spurt(n.toLong, 20.0 + (n % 7), 20.0, 0.0, 19f, 20f, 1f, 0f, ProjectileType.ARROW, n.toLong, world)
      val s = blood.oldestFirst(Blood.MaxSpurts - 1)
      var d = 0
      while (d < blood.drops(s)) {
        val i = blood.drop(s, d)
        blood.flight(i, 0.1f); blood.velocity(i, 0.1f)
        sink += blood.x + blood.vsx + blood.outlineX(i, 3)
        d += 1
      }
    }
    val mx = ManagementFactory.getThreadMXBean.asInstanceOf[com.sun.management.ThreadMXBean]
    val thread = Thread.currentThread().getId
    for (n <- 0 until 20000) work(n)
    for (_ <- 0 until 2000) mx.getThreadAllocatedBytes(thread)
    def measure(body: => Unit): Long = { val b = mx.getThreadAllocatedBytes(thread); body; mx.getThreadAllocatedBytes(thread) - b }
    val control = measure { for (n <- 0 until 20000) sink += n }
    val bytes = measure { for (n <- 0 until 20000) work(n) }
    assertTrue(s"20000 spurts allocated $bytes bytes (an empty loop $control)", bytes - control < 2048)
  }

  // --- What the painter takes from the client ---

  /** A client in a match, the renderer's context looking at (30, 30) at the system's time (which the
    * client stamps a hit it hears with), and a painter on it. */
  private class Scene {
    val T: Long = System.currentTimeMillis()
    val t = new TestClient(WorldData.createEmpty(120, 120))
    val c = t.client
    t.startMatch(spawn = (30, 30))
    val ctx = new RenderContext(c, FrameClock.System)
    val painter = new BloodPainter(ctx)
    ctx.frameTimeMs = T
    ctx.canvasW = 800; ctx.canvasH = 450
    ctx.camera.update(30, 30, 1.0 / 60, 800, 450)
    ctx.camOffX = ctx.camera.camOffX; ctx.camOffY = ctx.camera.camOffY

    def someone(at: (Int, Int)): UUID = {
      val id = UUID.randomUUID()
      c.getPlayers.put(id, new Player(id, "P", new Position(at._1, at._2), 0xFF336699, 100))
      id
    }
    def hit(target: UUID, at: Long = T, pType: Byte = ProjectileType.ARROW): Unit =
      c.recordHit(target, 0xFFFF7733, 28f, 30f, 1f, 0f, pType, at)
    def spurts: Seq[Int] = (0 until Blood.MaxSpurts).filter(s => painter.blood.hitTime(s) != Blood.NoHit)
  }

  @Test def aHitSpurtsFromWhereItsTargetIs(): Unit = {
    val sc = new Scene
    val them = sc.someone((33, 29))
    sc.t.projectile(ProjectileAction.HIT, 70, UUID.randomUUID(), x = 31f, y = 29f, target = them)
    sc.painter.collect()
    val s = sc.spurts
    assertEquals(1, s.size)
    assertEquals(33f, sc.painter.blood.bodyX(s.head), 0f)
    assertEquals(29f, sc.painter.blood.bodyY(s.head), 0f)
  }

  @Test def aHitOnUsSpurtsFromWhereTheCameraDrawsUs(): Unit = {
    val sc = new Scene
    sc.ctx.camera.visualX = 29.6; sc.ctx.camera.visualY = 30.2 // a step's glide behind our cell
    sc.hit(sc.c.getLocalPlayerId)
    sc.painter.collect()
    val s = sc.spurts.head
    assertEquals(29.6f, sc.painter.blood.bodyX(s), 1e-5f)
    assertEquals(30.2f, sc.painter.blood.bodyY(s), 1e-5f)
  }

  @Test def eachHitIsTakenOnceAndEveryOneOfAVolley(): Unit = {
    // A fan's three shots striking in one tick are three hits, each of them its own spurt
    val sc = new Scene
    val them = sc.someone((32, 31))
    (0 until 3).foreach(_ => sc.hit(them))
    sc.painter.collect()
    sc.painter.collect()
    assertEquals(3, sc.spurts.size)
  }

  @Test def noSpurtForWhatCannotBeSeen(): Unit = {
    val sc = new Scene
    val gone = UUID.randomUUID() // left the match
    val far = sc.someone((110, 2)) // off the screen by more than a screen
    val near = sc.someone((31, 31))
    sc.hit(gone)
    sc.hit(far)
    sc.hit(near, at = sc.T - Blood.LifeMs) // heard long ago: over before this frame
    sc.hit(near, pType = ProjectileType.LUCKY_STREAK) // hurts nobody
    sc.painter.collect()
    assertEquals(Nil, sc.spurts)
    sc.hit(near)
    sc.painter.collect()
    assertEquals(1, sc.spurts.size)
  }

  @Test def aKnockedUpTargetBleedsFromUpInTheAir(): Unit = {
    val sc = new Scene
    val them = sc.someone((31, 30))
    // Half way through a second in the air: at the top of the arc
    sc.c.getPlayers.get(them).setAirborne(sc.T - 500, sc.T + 500)
    sc.hit(them)
    sc.painter.collect()
    val b = sc.painter.blood
    val i = b.drop(sc.spurts.head, 0)
    b.flight(i, 0f)
    assertTrue(s"from the figure lifted 30 units: ${b.z}", b.z >= 12f + 29f)
  }

  @Test def thePainterTakesHitsAllocatingNothing(): Unit = {
    // Taken on the render thread every frame: a few hits a frame, as a crowded fight hears them
    val sc = new Scene
    val them = (0 until 8).map(k => sc.someone((28 + k, 31))).toArray
    val log = sc.c.hitLog
    var n = 0
    def frame(): Unit = {
      var k = 0
      while (k < 3) { log.add(them(n % them.length), 27f, 31f, 1f, 0f, ProjectileType.ARROW, sc.T); n += 1; k += 1 }
      sc.painter.collect()
    }
    val mx = ManagementFactory.getThreadMXBean.asInstanceOf[com.sun.management.ThreadMXBean]
    val thread = Thread.currentThread().getId
    for (_ <- 0 until 20000) frame()
    for (_ <- 0 until 2000) mx.getThreadAllocatedBytes(thread)
    var sink = 0L
    def measure(body: => Unit): Long = { val b = mx.getThreadAllocatedBytes(thread); body; mx.getThreadAllocatedBytes(thread) - b }
    val control = measure { for (k <- 0 until 20000) sink += k }
    val bytes = measure { for (_ <- 0 until 20000) frame() }
    // Under a byte a frame: one object a frame would be 320,000
    assertTrue(s"20000 frames' hits allocated $bytes bytes (an empty loop $control)", bytes - control < 20000)
    assertEquals("and every one was spurted", Blood.MaxSpurts, sc.spurts.size)
  }

  @Test def aNewLifeForgetsTheBlood(): Unit = {
    val sc = new Scene
    sc.hit(sc.someone((31, 30)))
    sc.painter.collect()
    sc.painter.reset()
    assertEquals(Nil, sc.spurts)
    sc.painter.collect()
    assertEquals("and doesn't take the hit again", Nil, sc.spurts)
  }
}
