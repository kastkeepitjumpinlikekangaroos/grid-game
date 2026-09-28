package com.gridgame.client

import com.gridgame.common.model._
import org.junit.Assert._
import org.junit.Test

import java.util.UUID

/**
 * How the client shows a knockback. The server moves a pushed player in one go and counts it
 * (Player.getKnockbacks, in every update about them); we are slid there from where we were drawn
 * (LocalMovement.updateVisualPosition, KnockbackSlide), everyone else is slid by RemoteMotion
 * (RemoteMotionTest), and a push is neither a teleport nor a turn to walk off the way it threw
 * them. Drawn where the server put them, pushed players vanished and reappeared cells away, on
 * other screens with a teleport's flash.
 */
class GameClientKnockbackTest {
  private val t = new TestClient()
  private val c = t.client
  private val them = UUID.randomUUID()

  /** Draw a frame `ms` after the last one: where we are drawn in it. */
  private def frame(ms: Double): Double = {
    t.nanos += (ms * 1e6).toLong
    c.updateVisualPosition()
    c.visualPosX
  }

  @Test def aKnockbackSlidesUsThere(): Unit = {
    t.startMatch(spawn = (10, 10))
    frame(16)
    t.update(t.id, 15, 10, serverMoves = 1, knockbacks = 1) // a Gust, five cells east
    assertEquals("we are there at once", (15, 10), t.at)
    var last = frame(0)
    assertEquals("but drawn where it caught us", 10.0, last, 1e-9)
    val steps = (0 until 30).map { _ =>
      val x = frame(1000.0 / 60)
      val step = x - last
      last = x
      step
    }
    assertEquals("and slid all the way", 15.0, last, 1e-9)
    val moving = steps.filter(_ > 1e-9)
    assertTrue("only ever toward it", steps.forall(_ >= 0))
    assertTrue(s"over several frames: $steps", moving.size >= 6)
    assertTrue("fastest first", moving.head >= moving.last)
    assertTrue(s"none of it a jump: $steps", moving.max < 1.5)
  }

  @Test def aServerMoveThatIsNotAKnockbackIsDrawnThereAtOnce(): Unit = {
    // A pull, a respawn, a correction
    t.startMatch(spawn = (10, 10))
    frame(16)
    t.update(t.id, 15, 10, serverMoves = 1)
    assertEquals(15.0, frame(16), 1e-9)
  }

  @Test def aKnockbackHeardTwiceIsSlidOnce(): Unit = {
    // A server move comes over TCP, and the UDP update everyone is sent says it again
    t.startMatch(spawn = (10, 10))
    frame(16)
    t.update(t.id, 15, 10, serverMoves = 1, knockbacks = 1)
    frame(0)
    frame(500)
    t.update(t.id, 15, 10, serverMoves = 1, knockbacks = 1)
    assertEquals(15.0, frame(16), 1e-9)
  }

  @Test def aNewMatchStartsWithNoKnockbackToSlide(): Unit = {
    // Each match counts its own: the next one's first knockback is one
    t.startMatch(spawn = (10, 10))
    frame(16)
    t.update(t.id, 15, 10, serverMoves = 1, knockbacks = 1)
    frame(500)
    t.startMatch(spawn = (40, 40))
    frame(16)
    t.update(t.id, 44, 40, serverMoves = 1, knockbacks = 1)
    assertEquals("slid from where the new match put us", 40.0, frame(0), 1e-9)
  }

  @Test def anotherPlayerKnockedBackIsNotShownTeleporting(): Unit = {
    t.startMatch(spawn = (10, 10), others = Seq(them -> (20, 20)))
    t.update(them, 20, 20)
    val facing = c.getPlayers.get(them).getDirection
    t.update(them, 25, 20, serverMoves = 1, knockbacks = 1) // five cells east
    assertEquals("the renderer is told to slide them", 1, c.getPlayers.get(them).getKnockbacks)
    assertFalse("no teleport's flash", c.getTeleportAnimations.containsKey(them))
    assertEquals("and they still face the way they did", facing, c.getPlayers.get(them).getDirection)
  }

  @Test def aJumpThatIsNotAKnockbackIsStillATeleport(): Unit = {
    t.startMatch(spawn = (10, 10), others = Seq(them -> (20, 20)))
    t.update(them, 20, 20)
    t.update(them, 25, 20, serverMoves = 1) // a blink as far
    assertTrue(c.getTeleportAnimations.containsKey(them))
    assertEquals(0, c.getPlayers.get(them).getKnockbacks)
  }

  @Test def anOldUpdateDoesNotUndoTheCount(): Unit = {
    // Updates about them are datagrams, and one sent before the push can arrive after it
    t.startMatch(spawn = (10, 10), others = Seq(them -> (20, 20)))
    t.update(them, 25, 20, serverMoves = 1, knockbacks = 1)
    t.update(them, 20, 20)
    assertEquals(1, c.getPlayers.get(them).getKnockbacks)
  }
}
