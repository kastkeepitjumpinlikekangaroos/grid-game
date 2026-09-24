package com.gridgame.server

import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

/**
 * Who decides where a player is. The client moves its player and the server checks each step;
 * the server moves players itself only through server moves (see OnHitEffectsTest), which it
 * counts. Its other updates about a player (a regen tick, a burn tick, a hit) carry wherever it
 * last heard the player was, and must not look like moves: the client took every one of them
 * as a correction and rubber-banded back while walking.
 */
class PositionAuthorityTest {
  private val m = new TestMatch()

  private def serverMovesToldOverTcp(p: Player): Seq[PlayerUpdatePacket] = m.updatesAbout(m.tcpSent(p), p)

  @Test def updatesThatAreNotMovesDoNotCountOne(): Unit = {
    val p = m.join(CharacterId.Gladiator, 10, 10)
    p.setHealth(50)
    m.clearSent()
    for (_ <- 0 until 10) m.instance.tickPlayers() // regen
    val regen = m.updatesAbout(m.udpSent(p), p)
    assertTrue("regen went out", regen.nonEmpty)
    assertTrue("none of them is a move", regen.forall(_.getServerMoves == 0))
    assertTrue("and none went over TCP", serverMovesToldOverTcp(p).isEmpty)
  }

  @Test def aStepSentBeforeAServerMoveIsDropped(): Unit = {
    val p = m.join(CharacterId.Gladiator, 10, 10)
    m.instance.moveByServer(p, new Position(20, 10))
    assertFalse(m.move(p, 11, 10, serverMoves = 0))
    assertEquals((20, 10), m.at(p))
    assertTrue(m.move(p, 21, 10, serverMoves = 1))
  }

  @Test def aLoneRefusedStepGetsNoCorrection(): Unit = {
    // Usually a race the server catches up with (a step that overtook its star)
    val p = m.join(CharacterId.Gladiator, 10, 10)
    assertTrue(m.move(p, 10, 10))
    m.clearSent()
    assertFalse(m.move(p, 40, 10))
    assertTrue(serverMovesToldOverTcp(p).isEmpty)
    assertEquals(0, p.getServerMoves)
  }

  @Test def refusalsThatKeepComingGetTheClientCorrected(): Unit = {
    // The client thinks it is somewhere the server doesn't. Now that it only takes positions
    // from server moves, nothing else would ever tell it.
    val p = m.join(CharacterId.Gladiator, 10, 10)
    assertTrue(m.move(p, 10, 10))
    m.clearSent()
    assertFalse(m.move(p, 40, 10))
    assertFalse(m.move(p, 41, 10, gapMs = 300))
    val told = serverMovesToldOverTcp(p)
    assertEquals(1, told.size)
    assertEquals(new Position(10, 10), told.head.getPosition)
    assertEquals(1, told.head.getServerMoves)
    // The client takes it and walks on from there
    assertTrue(m.move(p, 11, 10))
    assertEquals((11, 10), m.at(p))
  }

  @Test def correctionsAreNotSentOnEveryRefusal(): Unit = {
    val p = m.join(CharacterId.Gladiator, 10, 10)
    assertTrue(m.move(p, 10, 10))
    assertFalse(m.move(p, 40, 10, serverMoves = 0))
    for (i <- 0 until 5) assertFalse(m.move(p, 41 + i, 10, serverMoves = 0, gapMs = 100))
    assertTrue("at most one every 500ms", p.getServerMoves <= 2)
  }

  @Test def aRefusedStarIsAServerMove(): Unit = {
    val p = m.join(CharacterId.Spaceman, 10, 10)
    assertTrue(m.move(p, 10, 10))
    m.clearSent()
    val star = m.useItem(p, ItemType.Star, 10, 50) // 40 cells: more than a star goes
    assertTrue("star given back", m.hasItem(p, star))
    val told = m.tcpSent(p)
    assertTrue(told.exists { case i: ItemPacket => i.getAction == ItemAction.USE_REJECTED; case _ => false })
    assertEquals(new Position(10, 10), m.updatesAbout(told, p).last.getPosition)
    // Steps the client took from where it landed, before it heard, are stale
    assertFalse(m.move(p, 10, 49, serverMoves = 0))
    assertEquals((10, 10), m.at(p))
  }

  @Test def theDeadDoNotMove(): Unit = {
    // A client goes on sending steps until it hears it has died. They were taken: the body walked
    // on across everyone else's screens
    val p = m.join(CharacterId.Gladiator, 10, 10)
    assertTrue(m.move(p, 10, 10))
    p.damage(p.getHealth)
    m.clearSent()
    assertFalse(m.move(p, 11, 10))
    assertEquals((10, 10), m.at(p))
    assertTrue("and nobody is told it moved", m.updatesAbout(m.udpSent(p), p).isEmpty)
  }

  @Test def theDeadPickNothingUp(): Unit = {
    // ...and picked up whatever they walked past, which the respawn then threw away: an item
    // gone from the ground for everyone
    val p = m.join(CharacterId.Gladiator, 10, 10)
    val heart = new Item(9001, 11, 10, ItemType.Heart)
    m.instance.itemManager.place(heart)
    p.damage(p.getHealth)
    m.move(p, 10, 10)
    assertTrue("still on the ground", m.instance.itemManager.getAll.exists(_.id == heart.id))
    assertFalse(m.hasItem(p, heart))
  }

  @Test def theRespawnedWalkOnFromTheirSpawn(): Unit = {
    val p = m.join(CharacterId.Gladiator, 10, 10)
    p.damage(p.getHealth)
    m.instance.respawn(p.getId)
    val (x, y) = m.at(p)
    assertTrue(m.move(p, x + 1, y))
    assertEquals((x + 1, y), m.at(p))
  }

  @Test def aRejoinTellsTheClientTheCountSoFar(): Unit = {
    // A restarted client counts from zero; the server may be on 3
    val p = m.join(CharacterId.Gladiator, 10, 10)
    for (_ <- 0 until 3) m.instance.moveByServer(p, new Position(10, 10))
    m.clearSent()
    m.instance.handler.processPacket(new PlayerJoinPacket(0, p.getId, new Position(12, 10), p.getColorRGB,
      "p", 100, p.getCharacterId), p.getTcpChannel.asInstanceOf[io.netty.channel.Channel], null)
    val told = serverMovesToldOverTcp(p)
    assertEquals(3, told.last.getServerMoves)
    // Where the server has it, not the cell the join claimed (ReconnectTest)
    assertEquals(new Position(10, 10), told.last.getPosition)
  }
}
