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
    // Further than anything bunched up or lost on a bad network could explain
    assertFalse(m.move(p, 55, 55))
    assertFalse(m.move(p, 56, 55, gapMs = 300))
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
    assertFalse(m.move(p, 55, 55, serverMoves = 0))
    for (i <- 0 until 5) assertFalse(m.move(p, 55, 54 - i, serverMoves = 0, gapMs = 100))
    assertTrue("at most one every 500ms", p.getServerMoves <= 2)
  }

  @Test def aRefusedStarIsAServerMove(): Unit = {
    val p = m.join(CharacterId.Spaceman, 10, 10)
    assertTrue(m.move(p, 10, 10))
    m.clearSent()
    val star = m.useItem(p, ItemType.Star, 55, 55) // 90 cells: more than a star goes
    assertTrue("star given back", m.hasItem(p, star))
    val told = m.tcpSent(p)
    assertTrue(told.exists { case i: ItemPacket => i.getAction == ItemAction.USE_REJECTED; case _ => false })
    assertEquals(new Position(10, 10), m.updatesAbout(told, p).last.getPosition)
    // Steps the client took from where it landed, before it heard, are stale
    assertFalse(m.move(p, 55, 54, serverMoves = 0))
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

  @Test def anUpdateNeverCarriesAServerMovesCountWithThePositionFromBeforeIt(): Unit = {
    // An update built while another thread moves the player. A server move writes the position and
    // then counts it; the update read the position and then the count, so it could carry the old
    // cell with the new count. The player's client took that for the move, and then ignored the
    // update that really carried it, its count no longer new: it stood where the server no longer
    // had it, every step refused, until a correction put it right.
    var movedMidway = false
    val p = new Player(java.util.UUID.randomUUID(), "p", new Position(10, 10), 0xFF336699) {
      // The move lands just after the update has read the position
      override def getPosition: Position = {
        val pos = super.getPosition
        if (!movedMidway && pos == new Position(10, 10)) {
          movedMidway = true
          m.instance.moveByServer(this, new Position(20, 10))
        }
        pos
      }
    }
    p.setCharacterId(CharacterId.Gladiator.id)
    m.instance.registry.add(p)
    val told = m.instance.stateUpdate(p)
    assertTrue("the move happened while it was built", movedMidway)
    assertEquals(1, p.getServerMoves)
    assertFalse(s"the move's count with the cell from before it: $told",
      told.getServerMoves == 1 && told.getPosition == new Position(10, 10))
  }
}
