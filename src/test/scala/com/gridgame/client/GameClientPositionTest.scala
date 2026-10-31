package com.gridgame.client

import com.gridgame.common.model._
import org.junit.Assert._
import org.junit.Test

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
    Thread.sleep(com.gridgame.common.Constants.POSITION_RESEND_MS + 100L)
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
    Thread.sleep(400)
    c.movePlayer(1, 0)
    Thread.sleep(3L * c.moveStepIntervalMs)
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
}
