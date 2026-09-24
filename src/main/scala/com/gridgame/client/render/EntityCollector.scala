package com.gridgame.client.render

import com.gridgame.client.GameClient
import com.gridgame.common.model.{Item, Player, Projectile}

import java.util.UUID

/**
 * Collects game entities (items, projectiles, players) by grid cell for depth-sorted rendering.
 * Uses typed entity entries instead of closures to avoid per-entity lambda allocation.
 *
 * Cells live in a flat grid over the collect window rather than a hash map keyed by cell:
 * the renderer looks up every visible cell every frame, and a map keyed by a boxed Long
 * allocated a key per lookup — that alone was two thirds of everything a busy frame
 * allocated. Each cell holds a singly linked list of pooled entries, so a frame allocates
 * nothing once the pool has grown to the busiest frame seen.
 */
class EntityCollector {
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
  private var entryPool = new Array[MutableCellEntry](256)
  private var entryPoolIndex = 0
  private def acquireEntry(): MutableCellEntry = {
    if (entryPoolIndex == entryPool.length) entryPool = java.util.Arrays.copyOf(entryPool, entryPool.length * 2)
    var e = entryPool(entryPoolIndex)
    if (e == null) { e = new MutableCellEntry; entryPool(entryPoolIndex) = e }
    entryPoolIndex += 1
    e.next = null
    e
  }

  // Collect window: cells [winX0, winX0 + winW) x [winY0, winY0 + winH), row-major
  private var winX0 = 0
  private var winY0 = 0
  private var winW = 0
  private var winH = 0
  private var cellHead = new Array[MutableCellEntry](0)
  private var cellTail = new Array[MutableCellEntry](0)
  // Cells given entries this frame, so clearing and the leftover pass touch only those
  private var touched = new Array[Int](64)
  private var touchedCount = 0

  private def resetWindow(x0: Int, y0: Int, x1: Int, y1: Int): Unit = {
    var i = 0
    while (i < touchedCount) {
      val c = touched(i)
      if (c < cellHead.length) { cellHead(c) = null; cellTail(c) = null }
      i += 1
    }
    touchedCount = 0
    entryPoolIndex = 0
    winX0 = x0; winY0 = y0
    winW = Math.max(0, x1 - x0 + 1); winH = Math.max(0, y1 - y0 + 1)
    val cells = winW * winH
    if (cells > cellHead.length) {
      cellHead = new Array[MutableCellEntry](cells)
      cellTail = new Array[MutableCellEntry](cells)
    }
  }

  private def addEntity(cx: Int, cy: Int, entry: MutableCellEntry): Unit = {
    val lx = cx - winX0; val ly = cy - winY0
    if (lx < 0 || ly < 0 || lx >= winW || ly >= winH) return
    val c = ly * winW + lx
    val tail = cellTail(c)
    if (tail == null) {
      cellHead(c) = entry
      if (touchedCount == touched.length) touched = java.util.Arrays.copyOf(touched, touched.length * 2)
      touched(touchedCount) = c
      touchedCount += 1
    } else tail.next = entry
    cellTail(c) = entry
  }

  /**
   * First entry in a cell, removing the cell so [[takeRemaining]] won't hand it out again.
   * Entries run in insertion order through `next`. Null when the cell is empty.
   */
  def takeCell(cx: Int, cy: Int): MutableCellEntry = {
    val lx = cx - winX0; val ly = cy - winY0
    if (lx < 0 || ly < 0 || lx >= winW || ly >= winH) return null
    val c = ly * winW + lx
    val head = cellHead(c)
    if (head != null) { cellHead(c) = null; cellTail(c) = null }
    head
  }

  // Cursor over touched cells for takeRemaining
  private var remainingCursor = 0

  /** Next cell not yet taken (collected off the edge of what was drawn), or null when done. */
  def takeRemaining(): MutableCellEntry = {
    while (remainingCursor < touchedCount) {
      val c = touched(remainingCursor)
      remainingCursor += 1
      val head = cellHead(c)
      if (head != null) { cellHead(c) = null; cellTail(c) = null; return head }
    }
    null
  }

  /** Collect everything within two cells of the given visible cell range. */
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
      val cx = Math.round(proj.getX).toInt
      val cy = Math.round(proj.getY).toInt
      if (cx >= cullingMinX && cx <= cullingMaxX && cy >= cullingMinY && cy <= cullingMaxY) {
        addEntity(cx, cy, acquireEntry().setProjectile(proj))
      }
    }

    // Projectiles that have been stopped, fading where they stopped. This is the one place
    // that walks them every frame, so expired ones are dropped here.
    val fadeNow = System.currentTimeMillis()
    val fadeIter = client.getFadingProjectiles.values().iterator()
    while (fadeIter.hasNext) {
      val fp = fadeIter.next()
      if (fadeNow - fp.startMs > TerrainImpact.FADE_MS) fadeIter.remove()
      else {
        val cx = Math.round(fp.proj.getX).toInt
        val cy = Math.round(fp.proj.getY).toInt
        if (cx >= cullingMinX && cx <= cullingMaxX && cy >= cullingMinY && cy <= cullingMaxY) {
          addEntity(cx, cy, acquireEntry().setFadingProjectile(fp))
        }
      }
    }

    // Remote players, each walked along the cells the server has put them on (RemoteMotion). One
    // is sorted into the depth order of the cell it is drawn in, not the one it was last heard on,
    // which mid-dash can be cells ahead of where it is drawn.
    val nowNanos = System.nanoTime()
    val playerIter = client.getPlayers.values().iterator()
    while (playerIter.hasNext) {
      val player = playerIter.next()
      val pid = player.getId
      if (player.isDead) remoteVisualPositions.remove(pid) // back where they respawn, not walked there
      else {
        val pos = player.getPosition
        var motion = remoteVisualPositions.get(pid)
        if (motion == null) { motion = new RemoteMotion; remoteVisualPositions.put(pid, motion) }
        motion.update(pos.getX, pos.getY, nowNanos, deltaSec)
        addEntity(Math.round(motion.x).toInt, Math.round(motion.y).toInt,
          acquireEntry().setPlayer(player, motion.x, motion.y))
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

    // Local player
    val localPos = client.getLocalPosition
    if (localDeathAnimActive) {
      addEntity(localPos.getX, localPos.getY, acquireEntry().setLocalDeath())
    } else if (!client.getIsDead) {
      addEntity(localPos.getX, localPos.getY, acquireEntry().setLocalPlayer())
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

  class MutableCellEntry {
    var entryType: Byte = 0
    var ref: AnyRef = _
    var vx: Double = 0.0
    var vy: Double = 0.0
    /** Next entry in the same cell, in insertion order. */
    var next: MutableCellEntry = _
    def setItem(item: Item): MutableCellEntry = { entryType = TYPE_ITEM; ref = item; this }
    def setProjectile(proj: Projectile): MutableCellEntry = { entryType = TYPE_PROJECTILE; ref = proj; this }
    def setPlayer(p: Player, pvx: Double, pvy: Double): MutableCellEntry = { entryType = TYPE_PLAYER; ref = p; vx = pvx; vy = pvy; this }
    def setLocalPlayer(): MutableCellEntry = { entryType = TYPE_LOCAL_PLAYER; ref = null; this }
    def setLocalDeath(): MutableCellEntry = { entryType = TYPE_LOCAL_DEATH; ref = null; this }
    def setFadingProjectile(fp: FadingProjectile): MutableCellEntry = { entryType = TYPE_FADING_PROJECTILE; ref = fp; this }
  }

  // Keep old type alias for compatibility
  type CellEntry = MutableCellEntry
}
