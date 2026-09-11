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
}
