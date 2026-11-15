package com.gridgame.client.render
package world

/**
 * What the renderer remembers of the items on the client's ground from one frame to the next: when
 * each was first there, which is when it pops into being, and which of them have gone since the
 * last frame, which is a pickup.
 *
 * Every item on the client's ground is told to it every frame ([[see]], between [[beginFrame]] and
 * [[endFrame]]), on screen or not. An item is new the first frame it is seen and never again, however
 * often it scrolls off the screen and back; and it has gone when a frame passes without it. The
 * painter used to keep only the items it had drawn, so one that left the screen was taken for picked
 * up (a burst of sparks at the screen's edge) and forgotten, and popped into being again each time it
 * came back into view.
 *
 * It is keyed by an item's id, not by the Item: the client makes a new one whenever the server says
 * an item's SPAWN again (a joiner's catch-up), which is the same item still lying there. Two tables
 * of ids, the last frame's and this one's, filled afresh every frame, so nothing is ever removed
 * from one; nothing is allocated once they have grown to the most items a match has had.
 */
private[render] final class ItemTrack {
  import ItemTrack.Table

  private var now = new Table(64)
  private var before = new Table(64)

  // Those of the last frame that this one didn't see
  private var goneN = 0
  private var goneXs = new Array[Int](16)
  private var goneYs = new Array[Int](16)
  private var goneKinds = new Array[Byte](16)
  private var goneShown = new Array[Boolean](16)

  /** A frame's items are about to be seen: what the last frame saw becomes what to compare with. */
  def beginFrame(): Unit = {
    val t = before
    before = now
    now = if (t.cap == before.cap) { t.clear(); t } else new Table(before.cap)
    goneN = 0
  }

  /**
   * Item `id` of kind `kind` lies on cell (x, y) this frame, on screen or not (`onScreen`). Returns
   * when it was first seen: `at` if the last frame didn't have it.
   */
  def see(id: Int, x: Int, y: Int, kind: Byte, at: Long, onScreen: Boolean): Long = {
    val o = before.find(id)
    val first = if (o >= 0) { before.kept(o) = true; before.since(o) } else at
    if ((now.count + 1) * 2 > now.cap) now = now.grown()
    now.put(id, first, x, y, kind, onScreen)
    first
  }

  /** Every item has been seen: those the last frame had and this one hasn't are gone ([[gone]]). */
  def endFrame(): Unit = {
    val b = before
    var o = 0
    while (o < b.cap) {
      if (b.used(o) && !b.kept(o)) {
        if (goneN == goneXs.length) {
          goneXs = java.util.Arrays.copyOf(goneXs, goneN * 2)
          goneYs = java.util.Arrays.copyOf(goneYs, goneN * 2)
          goneKinds = java.util.Arrays.copyOf(goneKinds, goneN * 2)
          goneShown = java.util.Arrays.copyOf(goneShown, goneN * 2)
        }
        goneXs(goneN) = b.xs(o); goneYs(goneN) = b.ys(o); goneKinds(goneN) = b.kinds(o); goneShown(goneN) = b.shown(o)
        goneN += 1
      }
      o += 1
    }
  }

  /** How many items this frame has seen. */
  def size: Int = now.count

  /** When item `id` was first seen, or -1 if this frame hasn't seen it. */
  def firstSeen(id: Int): Long = {
    val i = now.find(id)
    if (i >= 0) now.since(i) else -1L
  }

  /** The items the last frame saw and this one didn't: where each lay, its kind, and whether it was
    * on screen then. */
  def gone: Int = goneN
  def goneX(i: Int): Int = goneXs(i)
  def goneY(i: Int): Int = goneYs(i)
  def goneKind(i: Int): Byte = goneKinds(i)
  def goneWasOnScreen(i: Int): Boolean = goneShown(i)

  /** Nothing is remembered: the next frame's items are all new, and none has gone. */
  def clear(): Unit = {
    now.clear()
    before.clear()
    goneN = 0
  }
}

private[render] object ItemTrack {
  /** Items by id: open addressing, linear probing, never more than half full (`cap` a power of two). */
  private final class Table(val cap: Int) {
    val used = new Array[Boolean](cap)
    val ids = new Array[Int](cap)
    val since = new Array[Long](cap)
    val xs = new Array[Int](cap)
    val ys = new Array[Int](cap)
    val kinds = new Array[Byte](cap)
    val shown = new Array[Boolean](cap)
    // seen again in the frame after this table's
    val kept = new Array[Boolean](cap)
    var count = 0

    @inline private def home(id: Int): Int = ((id * 0x9E3779B1) >>> 7) & (cap - 1)

    def clear(): Unit = {
      java.util.Arrays.fill(used, false)
      java.util.Arrays.fill(kept, false)
      count = 0
    }

    /** Where `id` is, or -1. */
    def find(id: Int): Int = {
      var i = home(id)
      while (used(i)) {
        if (ids(i) == id) return i
        i = (i + 1) & (cap - 1)
      }
      -1
    }

    def put(id: Int, first: Long, x: Int, y: Int, kind: Byte, onScreen: Boolean): Unit = {
      var i = home(id)
      while (used(i) && ids(i) != id) i = (i + 1) & (cap - 1)
      if (!used(i)) { used(i) = true; count += 1 }
      ids(i) = id; since(i) = first; xs(i) = x; ys(i) = y; kinds(i) = kind; shown(i) = onScreen
    }

    /** A table twice the size with everything this one holds. */
    def grown(): Table = {
      val t = new Table(cap * 2)
      var j = 0
      while (j < cap) {
        if (used(j)) t.put(ids(j), since(j), xs(j), ys(j), kinds(j), shown(j))
        j += 1
      }
      t
    }
  }
}
