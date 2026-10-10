package com.gridgame.client.render

import com.gridgame.common.model.{Item, Player, Projectile}

import java.util.UUID
import com.gridgame.client.game.{FadingProjectile, GameClient, TerrainImpact}

/**
 * Collects game entities (items, projectiles, players) for the depth pass, by the diagonal of the
 * map they are drawn on: `floor(x + y)`, how far down the screen their feet are. GLGameRenderer
 * draws the blocks and props a diagonal at a time, far to near, and after each diagonal's blocks
 * everything standing on it, back to front (render/CLAUDE.md, *Everything is sorted where it is
 * drawn, diagonal by diagonal*). Each is sorted where it is drawn: ours where the camera draws
 * us, everyone else's where RemoteMotion has walked them, a projectile where it is flown to.
 * Uses typed entity entries instead of closures to avoid per-entity lambda allocation.
 *
 * Diagonals live in a flat array over the collect window rather than a hash map keyed by them:
 * the renderer looks up every one on screen every frame, and a map keyed by a boxed Long
 * allocated a key per lookup — that alone was two thirds of everything a busy frame allocated.
 * Each diagonal holds a singly linked list of pooled entries, so a frame allocates nothing once
 * the pool has grown to the busiest frame seen.
 */
class EntityCollector(clock: FrameClock = FrameClock.System) {
  import EntityCollector._

  // Where each other player is drawn (RemoteMotion). A java.util.HashMap to avoid Scala Option
  // wrapping on get().
  val remoteVisualPositions: java.util.HashMap[UUID, RemoteMotion] = new java.util.HashMap()

  // Mutable output fields for getRemoteVisualPos — avoids tuple allocation on lookup
  private var _rvx: Double = 0.0
  private var _rvy: Double = 0.0
  def lastRVX: Double = _rvx
  def lastRVY: Double = _rvy

  /** Look up remote visual position. Returns true if found, result in lastRVX/lastRVY. */
  def getRemoteVisualPos(pid: UUID): Boolean = {
    val motion = remoteVisualPositions.get(pid)
    if (motion != null) {
      _rvx = motion.x
      _rvy = motion.y
      true
    } else false
  }

  // Entry pool — grow-only, reset index each frame
  private var entryPool = new Array[Entry](256)
  private var entryPoolIndex = 0
  private def acquireEntry(): Entry = {
    if (entryPoolIndex == entryPool.length) entryPool = java.util.Arrays.copyOf(entryPool, entryPool.length * 2)
    var e = entryPool(entryPoolIndex)
    if (e == null) { e = new Entry; entryPool(entryPoolIndex) = e }
    entryPoolIndex += 1
    e.next = null
    e
  }

  // Collect window: cells [cellX0, cellX1] x [cellY0, cellY1], which is diagonals
  // [diag0, diag0 + diags) — one further each way, for a point between two cells
  private var cellX0 = 0
  private var cellX1 = -1
  private var cellY0 = 0
  private var cellY1 = -1
  private var diag0 = 0
  private var diags = 0
  private var diagHead = new Array[Entry](0)
  private var diagTail = new Array[Entry](0)
  // Diagonals given entries this frame, so clearing and the leftover pass touch only those
  private var touched = new Array[Int](64)
  private var touchedCount = 0

  private def resetWindow(x0: Int, y0: Int, x1: Int, y1: Int): Unit = {
    var i = 0
    while (i < touchedCount) {
      val d = touched(i)
      if (d < diagHead.length) { diagHead(d) = null; diagTail(d) = null }
      i += 1
    }
    touchedCount = 0
    entryPoolIndex = 0
    cellX0 = x0; cellX1 = x1; cellY0 = y0; cellY1 = y1
    diag0 = x0 + y0 - 1
    diags = Math.max(0, (x1 + y1 + 1) - diag0 + 1)
    if (diags > diagHead.length) {
      diagHead = new Array[Entry](diags)
      diagTail = new Array[Entry](diags)
    }
  }

  /** Is (x, y) inside the collect window? Rounded to its cell, as the window is in cells. */
  @inline private def inWindow(x: Double, y: Double): Boolean = {
    val cx = Math.round(x); val cy = Math.round(y)
    cx >= cellX0 && cx <= cellX1 && cy >= cellY0 && cy <= cellY1
  }

  /** File an entity drawn at (x, y) on its diagonal, behind whatever there is nearer the screen's
    * bottom and in front of the rest; one level with another goes after it. */
  private def addEntity(x: Double, y: Double, entry: Entry): Unit = {
    val d = diagonalOf(x, y) - diag0
    if (d < 0 || d >= diags) return
    val depth = x + y
    entry.depth = depth
    val head = diagHead(d)
    if (head == null) {
      diagHead(d) = entry
      diagTail(d) = entry
      if (touchedCount == touched.length) touched = java.util.Arrays.copyOf(touched, touched.length * 2)
      touched(touchedCount) = d
      touchedCount += 1
    } else if (depth >= diagTail(d).depth) {
      diagTail(d).next = entry
      diagTail(d) = entry
    } else if (depth < head.depth) {
      entry.next = head
      diagHead(d) = entry
    } else {
      var prev = head
      while (prev.next.depth <= depth) prev = prev.next
      entry.next = prev.next
      prev.next = entry
    }
  }

  /**
   * The entities on diagonal `d`, back to front, removing them so [[takeRemaining]] won't hand
   * them out again. Entries run through `next`. Null when there are none.
   */
  def takeDiagonal(d: Int): Entry = {
    val i = d - diag0
    if (i < 0 || i >= diags) return null
    val head = diagHead(i)
    if (head != null) { diagHead(i) = null; diagTail(i) = null }
    head
  }

  // Where each other player collected this frame is drawn, for the shadow under them
  // (PlayerPainter.drawGroundMarks): kept here as they are collected rather than walked off the
  // map of them again
  private var standX = new Array[Double](32)
  private var standY = new Array[Double](32)
  private var standCount = 0
  def standing: Int = standCount
  def standingX(i: Int): Double = standX(i)
  def standingY(i: Int): Double = standY(i)

  private def stands(x: Double, y: Double): Unit = {
    if (standCount == standX.length) {
      standX = java.util.Arrays.copyOf(standX, standX.length * 2)
      standY = java.util.Arrays.copyOf(standY, standY.length * 2)
    }
    standX(standCount) = x; standY(standCount) = y
    standCount += 1
  }

  // Cursor over touched diagonals for takeRemaining
  private var remainingCursor = 0

  /** The next diagonal's entities not yet taken (collected off the edge of what was drawn), or
    * null when done. */
  def takeRemaining(): Entry = {
    while (remainingCursor < touchedCount) {
      val d = touched(remainingCursor)
      remainingCursor += 1
      val head = diagHead(d)
      if (head != null) { diagHead(d) = null; diagTail(d) = null; return head }
    }
    null
  }

  /** Collect everything within two cells of the given visible cell range. `localVisualX/Y` is
    * where we are drawn this frame (the camera's position), which is where we are sorted. */
  def collect(
    client: GameClient,
    deltaSec: Double,
    localVisualX: Double,
    localVisualY: Double,
    localDeathAnimActive: Boolean,
    minVisX: Int,
    maxVisX: Int,
    minVisY: Int,
    maxVisY: Int
  ): Unit = {
    // Bounds for off-screen culling (pre-expanded by 2 cells for margin), never wider than
    // the world plus that margin — nothing stands further out than that
    val world = client.getWorld
    val cullingMinX = Math.max(minVisX - 2, -2)
    val cullingMaxX = Math.min(maxVisX + 2, world.width + 1)
    val cullingMinY = Math.max(minVisY - 2, -2)
    val cullingMaxY = Math.min(maxVisY + 2, world.height + 1)
    resetWindow(cullingMinX, cullingMinY, cullingMaxX, cullingMaxY)
    remainingCursor = 0
    standCount = 0

    // Items — use Java iterator directly, skip off-screen
    val itemIter = client.getItems.values().iterator()
    while (itemIter.hasNext) {
      val item = itemIter.next()
      val ix = item.getCellX; val iy = item.getCellY
      if (ix >= cullingMinX && ix <= cullingMaxX && iy >= cullingMinY && iy <= cullingMaxY) {
        addEntity(ix, iy, acquireEntry().setItem(item))
      }
    }

    // Projectiles — use Java iterator directly, skip off-screen
    val projIter = client.getProjectiles.values().iterator()
    while (projIter.hasNext) {
      val proj = projIter.next()
      val px = proj.getX.toDouble; val py = proj.getY.toDouble
      if (inWindow(px, py)) addEntity(px, py, acquireEntry().setProjectile(proj))
    }

    // Projectiles that have been stopped, fading where they stopped. This is the one place
    // that walks them every frame, so expired ones are dropped here.
    val fadeNow = clock.nowMs()
    val fadeIter = client.getFadingProjectiles.values().iterator()
    while (fadeIter.hasNext) {
      val fp = fadeIter.next()
      if (fadeNow - fp.startMs > TerrainImpact.FADE_MS) fadeIter.remove()
      else {
        val px = fp.proj.getX.toDouble; val py = fp.proj.getY.toDouble
        if (inWindow(px, py)) addEntity(px, py, acquireEntry().setFadingProjectile(fp))
      }
    }

    // Remote players, each walked along the cells the server has put them on (RemoteMotion), and
    // sorted where that has them, not on the cell they were last heard on, which mid-dash can be
    // cells ahead of where they are drawn.
    val nowNanos = clock.nowNanos()
    val playerIter = client.getPlayers.values().iterator()
    while (playerIter.hasNext) {
      val player = playerIter.next()
      val pid = player.getId
      if (player.isDead) remoteVisualPositions.remove(pid) // back where they respawn, not walked there
      else {
        val pos = player.getPosition
        var motion = remoteVisualPositions.get(pid)
        if (motion == null) { motion = new RemoteMotion; remoteVisualPositions.put(pid, motion) }
        motion.update(pos.getX, pos.getY, player.getKnockbacks, nowNanos, deltaSec)
        if (inWindow(motion.x, motion.y)) {
          addEntity(motion.x, motion.y, acquireEntry().setPlayer(player, motion.x, motion.y))
          stands(motion.x, motion.y)
        }
      }
    }

    // Clean up disconnected players — iterate and remove entries not in current players
    val cleanIter = remoteVisualPositions.entrySet().iterator()
    while (cleanIter.hasNext) {
      val entry = cleanIter.next()
      if (!client.getPlayers.containsKey(entry.getKey)) {
        cleanIter.remove()
      }
    }

    // Us, sorted where we are drawn. The cell we are on is the one we are stepping into or were
    // pushed to; we are drawn a step's glide and the camera's ease behind it (a cell and more as
    // we walk, several in a knockback's slide), and a wall between the two went on the wrong side
    // of us: we were drawn over one in front of us, or under one behind.
    val lx = if (localVisualX.isNaN) client.getLocalPosition.getX.toDouble else localVisualX
    val ly = if (localVisualY.isNaN) client.getLocalPosition.getY.toDouble else localVisualY
    if (localDeathAnimActive) {
      addEntity(lx, ly, acquireEntry().setLocalDeath())
    } else if (!client.getIsDead) {
      addEntity(lx, ly, acquireEntry().setLocalPlayer())
    }
  }
}

object EntityCollector {
  /** Mutable entity entry — pooled to avoid per-frame case class allocation */
  val TYPE_ITEM: Byte = 0
  val TYPE_PROJECTILE: Byte = 1
  val TYPE_PLAYER: Byte = 2
  val TYPE_LOCAL_PLAYER: Byte = 3
  val TYPE_LOCAL_DEATH: Byte = 4
  val TYPE_FADING_PROJECTILE: Byte = 5

  /** The diagonal of the map a point is on: `wx + wy`, rounded down, so a point between two
    * diagonals is drawn after the blocks of the one behind it and before those of the one in
    * front. The small nudge keeps one standing on a cell on that cell's diagonal however its
    * position was summed. */
  def diagonalOf(x: Double, y: Double): Int = Math.floor(x + y + 1e-6).toInt

  class Entry {
    var entryType: Byte = 0
    var ref: AnyRef = _
    var vx: Double = 0.0
    var vy: Double = 0.0
    /** `x + y` where it is drawn: how far down the screen, so how near. */
    var depth: Double = 0.0
    /** The next entry on the same diagonal, the same depth or nearer. */
    var next: Entry = _
    def setItem(item: Item): Entry = { entryType = TYPE_ITEM; ref = item; this }
    def setProjectile(proj: Projectile): Entry = { entryType = TYPE_PROJECTILE; ref = proj; this }
    def setPlayer(p: Player, pvx: Double, pvy: Double): Entry = { entryType = TYPE_PLAYER; ref = p; vx = pvx; vy = pvy; this }
    def setLocalPlayer(): Entry = { entryType = TYPE_LOCAL_PLAYER; ref = null; this }
    def setLocalDeath(): Entry = { entryType = TYPE_LOCAL_DEATH; ref = null; this }
    def setFadingProjectile(fp: FadingProjectile): Entry = { entryType = TYPE_FADING_PROJECTILE; ref = fp; this }
  }
}
