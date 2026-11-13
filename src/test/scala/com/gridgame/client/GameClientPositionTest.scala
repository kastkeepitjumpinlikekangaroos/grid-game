package com.gridgame.client

import com.gridgame.common.model._
import org.junit.Assert._
import org.junit.Test
import org.lwjgl.glfw.GLFW._

/**
 * Where the client puts its own player. It moves it itself, and takes a position from the server
 * only when the server has moved it (a pull, a knockback, a freeze, a respawn, a correction),
 * which the server counts. Every other update about us carries wherever the server last heard we
 * were, a step or two behind while we walk; taking those rubber-banded us back on every regen
 * tick, burn tick and lifesteal.
 */
class GameClientPositionTest {
  private val t = new TestClient()
  private val c = t.client

  private def walkEast(steps: Int): Unit = for (_ <- 0 until steps) c.movePlayer(1, 0)

  @Test def nothingOfWhereWeAreIsSentBeforeTheServerSaysWhereItPutUs(): Unit = {
    // A match starts, and its world arrives: we stand wherever setWorld left us until our own join
    // echo says where the server placed us. A key held then sent steps from there, out of bounds or,
    // near enough the real spawn, taken as a step to it
    t.lobby(com.gridgame.common.protocol.LobbyAction.GAME_STARTING)
    c.setWorld(t.world)
    t.clearSent()
    walkEast(2)
    c.resendPositionIfQuiet()
    assertTrue("nothing sent", t.sentUpdates.isEmpty)
    t.join(t.id, 20, 20, t.name, c.selectedCharacterId)
    c.resendPositionIfQuiet()
    assertEquals("where the server put us", Seq(new Position(20, 20)), t.sentUpdates.map(_.getPosition))
  }

  @Test def aPlayerStandingStillSaysWhereItIsEveryHalfSecond(): Unit = {
    // Every update is a datagram: the last step of a walk, lost, left the server (and everyone it
    // relays us to) with us a step back for as long as we stood there
    t.startMatch(spawn = (10, 10))
    walkEast(1)
    t.clearSent()
    c.resendPositionIfQuiet()
    assertTrue("not straight after a step", t.sentUpdates.isEmpty)
    Thread.sleep(com.gridgame.common.Constants.POSITION_RESEND_MS + 20L)
    c.resendPositionIfQuiet()
    c.resendPositionIfQuiet()
    assertEquals("once, where we are", Seq(new Position(11, 10)), t.sentUpdates.map(_.getPosition))
  }

  @Test def aDashThatHitchedSaysWhereItLandsBeforeAnythingElse(): Unit = {
    // A frame that hitched for longer than POSITION_RESEND_MS mid-dash: the next says where the dash
    // landed, the first update after it, which the server takes past a wall's corner as a dash's.
    // Said again first, where the dash had got to took that place and the landing was a step
    c.selectedCharacterId = CharacterId.Ghoul.id // Lunge, Q: ten cells in 150ms
    t.startMatch(spawn = (20, 20))
    c.setMouseWorldPosition(30, 20)
    val keys = new com.gridgame.client.input.GLKeyboardHandler(c)
    t.clearSent()
    assertTrue(c.shootAbility(0))
    val cast = t.sentUpdates.size
    // The hitch, on the wall clock the resend counts on and the frames' clock the dash runs on
    Thread.sleep(com.gridgame.common.Constants.POSITION_RESEND_MS + 100L)
    t.nanos += (com.gridgame.common.Constants.POSITION_RESEND_MS + 100L) * 1000000L
    keys.update()
    val landing = c.getLocalPosition
    assertNotEquals("it went somewhere", new Position(20, 20), landing)
    assertEquals("the landing, twice, and nothing before it", Seq(landing, landing),
      t.sentUpdates.drop(cast).map(_.getPosition))
  }

  @Test def anUpdateThatIsNotAMoveLeavesUsWhereWeAre(): Unit = {
    t.startMatch(spawn = (10, 10))
    walkEast(3)
    assertEquals((13, 10), t.at)
    // A regen tick, sent when the server had only heard of our first step
    t.update(t.id, 11, 10, health = 97, serverMoves = 0)
    assertEquals((13, 10), t.at)
    assertEquals("but its health is ours", 97, c.getLocalHealth)
  }

  @Test def aServerMoveTakesUsThereAndIsSentBack(): Unit = {
    t.startMatch(spawn = (10, 10))
    walkEast(2)
    t.update(t.id, 20, 30, serverMoves = 1) // pulled
    assertEquals((20, 30), t.at)
    t.clearSent()
    c.movePlayer(0, 1)
    assertEquals((20, 31), t.at)
    assertEquals("our steps say we've seen it", 1, t.sentUpdates.last.getServerMoves)
  }

  @Test def anOlderMoveArrivingLateIsIgnored(): Unit = {
    // Each server move goes over TCP and UDP; the UDP copy of an earlier one can come last
    t.startMatch(spawn = (10, 10))
    t.update(t.id, 20, 20, serverMoves = 2)
    t.update(t.id, 15, 15, serverMoves = 1)
    assertEquals((20, 20), t.at)
  }

  @Test def eachMatchCountsItsOwnMoves(): Unit = {
    t.startMatch(spawn = (10, 10))
    t.update(t.id, 20, 20, serverMoves = 3)
    t.startMatch(spawn = (5, 5))
    t.clearSent()
    c.movePlayer(1, 0)
    assertEquals(0, t.sentUpdates.last.getServerMoves)
    t.update(t.id, 30, 30, serverMoves = 1)
    assertEquals("the new match's first move is taken", (30, 30), t.at)
  }

  @Test def aServerMoveEndsADash(): Unit = {
    c.selectedCharacterId = CharacterId.Shapeshifter.id // Q: a dash
    t.startMatch(spawn = (10, 10))
    c.setMouseWorldPosition(25.0, 10.0)
    c.shootAbility(0)
    assertTrue(c.isSwooping)
    t.update(t.id, 12, 40, serverMoves = 1)
    assertFalse(c.isSwooping)
    assertEquals((12, 40), t.at)
  }

  @Test def ourStatusComesFromTheServer(): Unit = {
    t.startMatch(spawn = (10, 10))
    t.update(t.id, 10, 10, health = 40, flags = 0x04 | 0x40) // frozen, rooted
    assertTrue(c.isFrozen)
    assertTrue(c.isRooted)
    t.update(t.id, 10, 10, health = 40, flags = 0)
    assertFalse(c.isFrozen)
    assertFalse(c.isRooted)
  }

  @Test def frozenWeDoNotMove(): Unit = {
    t.startMatch(spawn = (10, 10))
    t.update(t.id, 10, 10, flags = 0x04)
    walkEast(3)
    assertEquals((10, 10), t.at)
  }

  // --- Held: a root lets us attack, but moves us nowhere ---

  @Test def rootedWeDoNotBlink(): Unit = {
    // The server applies no step, blink or dash while a root lasts, and said nothing when a rooted
    // client blinked anyway: the client showed the blink, and the server had it where it was
    c.selectedCharacterId = CharacterId.Wizard.id // E: blink 6
    t.startMatch(spawn = (10, 10))
    t.update(t.id, 10, 10, flags = 0x40)
    t.clearSent()
    c.setMouseWorldPosition(25.0, 10.0)
    c.shootAbility(1)
    assertEquals((10, 10), t.at)
    assertEquals("and it isn't spent", 0f, c.getECooldownFraction, 0.001f)
    assertTrue(t.sentUpdates.isEmpty)
  }

  @Test def rootedWeDoNotDash(): Unit = {
    val (dasher, slot) = CharacterDef.all.iterator.flatMap { d =>
      Seq((0, d.qAbility), (1, d.eAbility)).collectFirst { case (s, a) if a.castBehavior.isInstanceOf[DashBuff] => (d.id.id, s) }
    }.next()
    c.selectedCharacterId = dasher
    t.startMatch(spawn = (10, 10))
    t.update(t.id, 10, 10, flags = 0x40)
    c.setMouseWorldPosition(25.0, 10.0)
    c.shootAbility(slot)
    assertFalse(c.isSwooping)
    assertEquals((10, 10), t.at)
  }

  @Test def rootedWeStillAttack(): Unit = {
    c.selectedCharacterId = CharacterId.Soldier.id
    t.startMatch(spawn = (10, 10))
    t.update(t.id, 10, 10, flags = 0x40)
    t.clearSent()
    c.shootToward(1f, 0f)
    assertEquals(1, t.sentSpawns.size)
  }

  @Test def heldWeKeepOurStar(): Unit = {
    t.startMatch(spawn = (10, 10))
    t.item(com.gridgame.common.protocol.ItemAction.INVENTORY, 9, ItemType.Star)
    t.clearSent()
    for (flags <- Seq(0x40, 0x04)) { // rooted, then frozen
      t.update(t.id, 10, 10, flags = flags)
      c.setMouseWorldPosition(20.0, 10.0)
      c.useItem(ItemType.Star.id)
      assertEquals((10, 10), t.at)
      assertEquals(1, c.getItemCount(ItemType.Star.id))
    }
    assertTrue("nothing asked of the server", t.sent.isEmpty)
  }

  @Test def wallsStopUsAndDiagonalsSlideAlongThem(): Unit = {
    val world = WorldData.createEmpty(30, 30)
    world.setTile(11, 10, Tile.Wall)
    world.setTile(11, 11, Tile.Wall)
    val w = new TestClient(world)
    w.startMatch(spawn = (10, 10))
    w.client.movePlayer(1, 0)
    assertEquals((10, 10), w.at)
    w.client.movePlayer(1, 1) // diagonal into the wall: slides along it
    assertEquals((10, 11), w.at)
  }

  @Test def aDiagonalStepGoesRoundAWallsCornerNotThroughIt(): Unit = {
    // Cutting the corner, our sprite stood in the wall for half the step, on every screen
    val world = WorldData.createEmpty(30, 30)
    world.setTile(11, 10, Tile.Wall)
    val w = new TestClient(world)
    w.startMatch(spawn = (10, 10))
    w.client.movePlayer(1, 1)
    assertEquals("along the open side", (10, 11), w.at)
    w.client.movePlayer(1, 1)
    assertEquals("and on past it", (11, 12), w.at)
  }

  @Test def betweenTwoBlocksTouchingAtACornerThereIsNoWay(): Unit = {
    val world = WorldData.createEmpty(30, 30)
    world.setTile(11, 10, Tile.Wall)
    world.setTile(10, 11, Tile.Rock)
    val w = new TestClient(world)
    w.startMatch(spawn = (10, 10))
    w.client.movePlayer(1, 1)
    assertEquals((10, 10), w.at)
    assertTrue("nothing to tell the server", w.sentUpdates.isEmpty)
  }

  @Test def aStepAfterStandingStillGlidesOverAStepNotTheWait(): Unit = {
    // A tap after a few seconds stood still glided one cell over those seconds, the camera with it
    t.startMatch(spawn = (10, 10))
    c.movePlayer(1, 0)
    t.nanos += 400 * 1000000L
    c.movePlayer(1, 0)
    t.nanos += 3L * c.moveStepIntervalMs * 1000000L
    c.updateVisualPosition()
    assertEquals("there a step's glide later", 12.0, c.visualPosX, 1e-9)
  }

  @Test def aBlinkStopsBeforeAWall(): Unit = {
    val world = WorldData.createEmpty(30, 30)
    world.setTile(14, 10, Tile.Wall)
    val w = new TestClient(world)
    w.client.selectedCharacterId = CharacterId.Wizard.id // E: blink 6
    w.startMatch(spawn = (10, 10))
    w.client.setMouseWorldPosition(25.0, 10.0)
    w.client.shootAbility(1)
    assertEquals((13, 10), w.at)
  }

  // --- A phase: untouchable and twice as quick, and walls stop it ---

  @Test def phasedAWallStillStopsUs(): Unit = {
    // A phase walked through walls, corners and all
    val world = WorldData.createEmpty(30, 30)
    world.setTile(11, 10, Tile.Wall)
    val w = new TestClient(world)
    w.client.selectedCharacterId = CharacterId.Wraith.id // Q: Phase Shift
    w.startMatch(spawn = (10, 10))
    assertTrue(w.client.shootAbility(0))
    assertTrue(w.client.isPhased)
    w.client.movePlayer(1, 0)
    assertEquals("into the wall", (10, 10), w.at)
    w.client.movePlayer(1, -1)
    assertEquals("past its corner: along the open side", (10, 9), w.at)
  }

  // --- Where we are drawn as we walk and dash ---

  /** `n` frames at 60 a second, each begun up to 1.5ms early or late as the match's loop's are, with
    * `keys` held through the real keyboard handler: when each was, and where we were drawn. */
  private def walkFrames(n: Int, keys: Int*): IndexedSeq[(Long, Double, Double)] = {
    val handler = new com.gridgame.client.input.GLKeyboardHandler(c)
    keys.foreach(k => handler.invoke(0L, k, 0, GLFW_PRESS, 0))
    val base = t.nanos
    (1 to n).map { i =>
      t.nanos = base + Math.round(i * 1e9 / 60 + ((i * 7919) % 31 - 15) * 100000.0)
      handler.update()
      c.updateVisualPosition()
      (t.nanos, c.visualPosX, c.visualPosY)
    }
  }

  /** Walking east (S and D), as each pace falls on the frames: drawn exactly where the time since the
    * first step takes us at that pace, every frame. A step is only taken on a frame, the first after it
    * falls due: at 60 fps a 50ms step falls due on every third, a millisecond either side of it, and a
    * 53ms one on the third or the fourth, so some are taken a frame late and the next a frame early.
    * Glided from the frame that took each, over the gap since the last, the sprite stood still in some
    * frames and jumped up to half a cell in others; from when each fell due, over its interval, it
    * moves as far as the time between two frames takes it. */
  private def walksAtItsPace(character: CharacterId): Unit = {
    c.selectedCharacterId = character.id
    t.startMatch(spawn = (10, 30))
    val interval = c.moveStepIntervalMs * 1e6
    val drawn = walkFrames(90, GLFW_KEY_S, GLFW_KEY_D)
    val (t1, x1, _) = drawn.head
    assertEquals("the first step taken at once", 10.0, x1, 1e-9)
    drawn.foreach { case (at, x, y) =>
      assertEquals(s"at ${(at - t1) / 1e6}ms", 10.0 + (at - t1) / interval, x, 1e-6)
      assertEquals(30.0, y, 1e-9)
    }
    // And the steps did fall unevenly on the frames, which is what is being drawn past
    val stepsAt = drawn.indices.filter(i => i > 0 && Math.floor(drawn(i)._2) != Math.floor(drawn(i - 1)._2))
    val gaps = stepsAt.indices.drop(1).map(i => stepsAt(i) - stepsAt(i - 1)).toSet
    assertTrue(s"steps $gaps frames apart", gaps.size > 1)
  }

  @Test def aRangedWalkIsDrawnAtItsPaceWhicheverFramesItsStepsFallOn(): Unit =
    walksAtItsPace(CharacterId.Wizard) // 50ms a step

  @Test def aMeleeWalkIsDrawnAtItsPaceWhicheverFramesItsStepsFallOn(): Unit =
    walksAtItsPace(CharacterId.Ghoul) // 53ms a step

  @Test def aWalkThatQuickensGoesOnAtItsNewPaceNotInALurch(): Unit = {
    // A step every 150ms (slowed), then every 50 (the slow over, or a charge let go): the step after
    // comes a third of the way through the last one's glide. Finished within the new step's 50ms,
    // the rest of that glide and the new one were drawn at 1.7 times the new pace, a lurch the
    // camera carried to the whole screen. Taken up at the new pace, the walk never goes faster
    t.startMatch(spawn = (10, 30))
    val ms = 1000000L
    val base = t.nanos
    val dues = Seq(0L, 150L, 300L, 350L, 400L, 450L, 500L)
    var next = 0
    val xs = (0 to 45).map { f =>
      val now = base + Math.round(f * 1e9 / 60)
      t.nanos = now
      while (next < dues.length && base + dues(next) * ms <= now) {
        c.movePlayer(1, 0, base + dues(next) * ms, (if (next < 3) 150L else 50L) * ms)
        next += 1
      }
      c.updateVisualPosition()
      c.visualPosX
    }
    val newPace = 1000.0 / 60 / 50
    (1 until xs.length).foreach(i =>
      assertTrue(s"frame $i moved ${xs(i) - xs(i - 1)} cells", xs(i) - xs(i - 1) <= newPace * 1.01))
    assertEquals("and the walk ends on its cell", 17.0, xs.last, 1e-9)
  }

  @Test def letGoTheWalkEndsOnItsCell(): Unit = {
    t.startMatch(spawn = (10, 30))
    val handler = new com.gridgame.client.input.GLKeyboardHandler(c)
    handler.invoke(0L, GLFW_KEY_D, 0, GLFW_PRESS, 0)
    handler.invoke(0L, GLFW_KEY_S, 0, GLFW_PRESS, 0)
    (1 to 20).foreach { i => t.nanos += 16666667L; handler.update(); c.updateVisualPosition() }
    handler.invoke(0L, GLFW_KEY_D, 0, GLFW_RELEASE, 0)
    handler.invoke(0L, GLFW_KEY_S, 0, GLFW_RELEASE, 0)
    val xs = (1 to 10).map { _ => t.nanos += 16666667L; handler.update(); c.updateVisualPosition(); c.visualPosX }
    assertEquals("on the cell it stopped on", c.getLocalPosition.getX.toDouble, xs.last, 1e-9)
    (1 until xs.length).foreach(i => assertTrue("and never back", xs(i) >= xs(i - 1)))
  }

  @Test def aDashIsDrawnAlongItsLineNotACellAtATime(): Unit = {
    // Ten cells in 150ms is 1.1 a frame: drawn on the cell it had reached, the dash moved a cell in one
    // frame and two in the next. Drawn along its line, it moves as far every frame, and lands
    c.selectedCharacterId = CharacterId.Ghoul.id // Lunge, Q: ten cells in 150ms
    t.startMatch(spawn = (20, 20))
    c.setMouseWorldPosition(30, 20)
    val keys = new com.gridgame.client.input.GLKeyboardHandler(c)
    assertTrue(c.shootAbility(0))
    val from = t.nanos
    val xs = (1 to 12).map { i =>
      t.nanos = from + Math.round(i * 1e9 / 60)
      keys.update()
      c.updateVisualPosition()
      c.visualPosX
    }
    val perFrame = 10.0 / 150 * (1000.0 / 60)
    assertEquals(20 + perFrame, xs.head, 1e-6)
    (1 until 9).foreach(i => assertEquals(s"frame ${i + 1}", perFrame, xs(i) - xs(i - 1), 1e-6))
    assertEquals("landed", (30, 20), t.at)
    xs.drop(8).foreach(x => assertEquals(30.0, x, 1e-9))
  }
}

