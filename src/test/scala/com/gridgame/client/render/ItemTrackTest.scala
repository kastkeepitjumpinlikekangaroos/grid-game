package com.gridgame.client.render

import com.gridgame.client.render.world.ItemTrack
import org.junit.Assert._
import org.junit.Test

/**
 * What the renderer remembers of the items on the ground between frames (ItemTrack): when each was
 * first there, and which have gone since the last frame.
 */
class ItemTrackTest {
  private val Gem: Byte = 1
  private val Heart: Byte = 2

  /** A frame in which the items `ids` are on the ground, all on screen unless listed in `off`. */
  private def frame(track: ItemTrack, at: Long, ids: Seq[Int], off: Set[Int] = Set.empty): Map[Int, Long] = {
    track.beginFrame()
    val since = ids.map(id => id -> track.see(id, id * 2, id * 3, if (id % 2 == 0) Heart else Gem, at, !off(id))).toMap
    track.endFrame()
    since
  }

  private def gone(track: ItemTrack): Seq[(Int, Int, Byte, Boolean)] =
    (0 until track.gone).map(i => (track.goneX(i), track.goneY(i), track.goneKind(i), track.goneWasOnScreen(i)))

  @Test def anItemIsNewTheFirstFrameItIsSeenAndNeverAgain(): Unit = {
    val track = new ItemTrack
    assertEquals(1000L, frame(track, 1000L, Seq(7))(7))
    assertEquals(1000L, frame(track, 1016L, Seq(7))(7))
    assertEquals(1000L, frame(track, 9000L, Seq(7))(7))
    assertEquals(1000L, track.firstSeen(7))
    assertEquals("an item this frame hasn't seen", -1L, track.firstSeen(8))
    assertEquals(0, track.gone)
  }

  @Test def anItemOffTheScreenAndBackIsNeitherGoneNorNew(): Unit = {
    val track = new ItemTrack
    frame(track, 1000L, Seq(1, 2))
    // we walk away from item 1 and back: it is still on the ground, and still told of
    assertEquals(1000L, frame(track, 2000L, Seq(1, 2), off = Set(1))(1))
    assertEquals(0, track.gone)
    assertEquals(1000L, frame(track, 3000L, Seq(1, 2))(1))
    assertEquals(0, track.gone)
  }

  @Test def anItemNoLongerOnTheGroundHasGoneOnce(): Unit = {
    val track = new ItemTrack
    frame(track, 1000L, Seq(1, 2, 3), off = Set(3))
    frame(track, 1016L, Seq(2))
    // where each lay, its kind, and whether it was on screen when last seen
    assertEquals(Set((2, 3, Gem, true), (6, 9, Gem, false)), gone(track).toSet)
    frame(track, 1032L, Seq(2))
    assertEquals("gone the frame it went, not after", 0, track.gone)
  }

  @Test def anItemThatComesBackAfterGoingIsNewAgain(): Unit = {
    // the server numbers no two items of a match alike, but a new match's are numbered from the start
    val track = new ItemTrack
    frame(track, 1000L, Seq(5))
    frame(track, 2000L, Seq())
    assertEquals(1, track.gone)
    assertEquals(3000L, frame(track, 3000L, Seq(5))(5))
  }

  @Test def manyItemsAreKeptApartWhateverTheirIds(): Unit = {
    val track = new ItemTrack
    // more than a table starts with room for, ids that land on one another's places, and a negative one
    val ids = (0 until 300).map(i => i * 64) ++ Seq(-1, Int.MaxValue, Int.MinValue, 1, 2, 3)
    val first = frame(track, 500L, ids)
    assertTrue(first.values.forall(_ == 500L))
    assertEquals(ids.size, track.size)
    val again = frame(track, 600L, ids)
    assertTrue("every one of them known the frame after", again.values.forall(_ == 500L))
    assertEquals(0, track.gone)
    // half of them taken at once
    val (stay, go) = ids.partition(id => (id & 64) == 0)
    frame(track, 700L, stay)
    assertEquals(go.map(id => (id * 2, id * 3)).toSet, gone(track).map(g => (g._1, g._2)).toSet)
    assertEquals(go.size, track.gone)
    assertTrue(stay.forall(id => track.firstSeen(id) == 500L))
    assertTrue(go.forall(id => track.firstSeen(id) == -1L))
  }

  @Test def anItemSeenTwiceInAFrameIsOneItem(): Unit = {
    val track = new ItemTrack
    track.beginFrame()
    track.see(4, 1, 1, Gem, 100L, onScreen = true)
    track.see(4, 1, 1, Gem, 100L, onScreen = true)
    track.endFrame()
    assertEquals(1, track.size)
  }

  @Test def clearedItRemembersNothing(): Unit = {
    val track = new ItemTrack
    frame(track, 1000L, Seq(1, 2))
    track.clear()
    assertEquals(0, track.size)
    assertEquals(2000L, frame(track, 2000L, Seq(1))(1))
    assertEquals("and nothing has gone", 0, track.gone)
  }

  @Test def framesAllocateNothingOnceTheTablesHaveGrown(): Unit = {
    val track = new ItemTrack
    val bean = java.lang.management.ManagementFactory.getThreadMXBean.asInstanceOf[com.sun.management.ThreadMXBean]
    val me = Thread.currentThread().getId
    var since = 0L
    def frames(n: Int): Unit = {
      var f = 0
      while (f < n) {
        track.beginFrame()
        var id = 0
        while (id < 200) {
          // one in sixteen taken and put down again every few frames. (What `see` answers is kept in
          // a Long: left as the value of an `if` with no else, Scala boxes it, and that is all the
          // allocation there would be to find.)
          if ((id + f / 4) % 16 != 0) since = track.see(id * 7, id, id, Gem, f.toLong, onScreen = (id & 1) == 0)
          id += 1
        }
        track.endFrame()
        f += 1
      }
    }
    frames(2000) // the tables grown, the code compiled
    val before = bean.getThreadAllocatedBytes(me)
    frames(2000)
    val allocated = bean.getThreadAllocatedBytes(me) - before
    assertTrue(s"2000 frames of 200 items allocated $allocated bytes", allocated < 4096)
  }
}
