package com.gridgame.client.game

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * What the renderer shows of an event for a moment after it: a player being hit (the colour and
 * heading of what struck them), a death, a teleport, and every blast — each recorded here as it is
 * heard, read by the renderer every frame without allocating, and let expire.
 */
trait EventAnimations { this: GameClient =>
  // Hit animation tracking (entries expire after HIT_EXPIRE_MS)
  // Stored as parallel maps so renderer can read each component without allocation per frame.
  private val playerHitTimes: ConcurrentHashMap[UUID, Long] = new ConcurrentHashMap()

  // Projectile colorRGB and screen-space direction at hit moment — used for themed/directional hit FX.
  private val playerHitColors: ConcurrentHashMap[UUID, Integer] = new ConcurrentHashMap()
  private val playerHitDx: ConcurrentHashMap[UUID, java.lang.Float] = new ConcurrentHashMap()
  private val playerHitDy: ConcurrentHashMap[UUID, java.lang.Float] = new ConcurrentHashMap()
  private val playerHitProjType: ConcurrentHashMap[UUID, java.lang.Byte] = new ConcurrentHashMap()
  private val HIT_EXPIRE_MS = 600L
  private var lastHitCleanupTime: Long = 0L

  // Death animation tracking: (timestamp, worldX, worldY, colorRGB)
  private[game] val deathAnimations: ConcurrentHashMap[UUID, Array[Long]] = new ConcurrentHashMap()

  // Teleport animation tracking: (timestamp, oldX, oldY, newX, newY, colorRGB)
  private[game] val teleportAnimations: ConcurrentHashMap[UUID, Array[Long]] = new ConcurrentHashMap()

  // Blasts going off: every explosion and splash, drawn as whoever threw it (GLBlastRenderers).
  // blastKey -> the fields at GameClient.BLAST_*. One map for both: a slam used to put an
  // explosion in one and a splash in another, and was drawn as both at once.
  private val blasts: ConcurrentHashMap[Long, Array[Long]] = new ConcurrentHashMap()

  /** `targetId` was struck at `now` by a projectile of this colour, heading and type: what the hit
    * effect drawn on them is made of. */
  private[client] def recordHit(targetId: UUID, colorRGB: Int, dx: Float, dy: Float, pType: Byte, now: Long): Unit = {
    playerHitTimes.put(targetId, now)
    playerHitColors.put(targetId, Integer.valueOf(colorRGB))
    playerHitDx.put(targetId, java.lang.Float.valueOf(dx))
    playerHitDy.put(targetId, java.lang.Float.valueOf(dy))
    playerHitProjType.put(targetId, java.lang.Byte.valueOf(pType))
  }

  def getPlayerHitTime(playerId: UUID): Long =
    playerHitTimes.getOrDefault(playerId, 0L)

  /** Color of the projectile that last hit this player (0 if none). */
  def getPlayerHitColorRGB(playerId: UUID): Int = {
    val v = playerHitColors.get(playerId)
    if (v == null) 0 else v.intValue()
  }

  /** Direction (world space) the projectile that last hit this player was traveling. */
  def getPlayerHitDx(playerId: UUID): Float = {
    val v = playerHitDx.get(playerId)
    if (v == null) 0f else v.floatValue()
  }

  def getPlayerHitDy(playerId: UUID): Float = {
    val v = playerHitDy.get(playerId)
    if (v == null) 0f else v.floatValue()
  }

  def getPlayerHitProjectileType(playerId: UUID): Byte = {
    val v = playerHitProjType.get(playerId)
    if (v == null) 0.toByte else v.byteValue()
  }

  /** Periodic cleanup of expired hit entries to prevent unbounded growth. Call from packet processing, not render. */
  private[game] def cleanupHitTimes(): Unit = {
    val now = System.currentTimeMillis()
    if (now - lastHitCleanupTime > 2000) {
      lastHitCleanupTime = now
      val iter = playerHitTimes.entrySet().iterator()
      while (iter.hasNext) {
        val entry = iter.next()
        if (now - entry.getValue > HIT_EXPIRE_MS) {
          val k = entry.getKey
          iter.remove()
          playerHitColors.remove(k)
          playerHitDx.remove(k)
          playerHitDy.remove(k)
          playerHitProjType.remove(k)
        }
      }
    }
  }

  /** They left the match: no hit on them left to draw. */
  private[game] def forgetHitOn(playerId: UUID): Unit = {
    playerHitTimes.remove(playerId)
    playerHitColors.remove(playerId)
    playerHitDx.remove(playerId)
    playerHitDy.remove(playerId)
    playerHitProjType.remove(playerId)
  }

  /** No match: nothing of its events left to draw. */
  private[game] def forgetAnimations(): Unit = {
    deathAnimations.clear()
    teleportAnimations.clear()
    blasts.clear()
    playerHitTimes.clear()
    playerHitColors.clear()
    playerHitDx.clear()
    playerHitDy.clear()
    playerHitProjType.clear()
  }

  def getDeathAnimations: ConcurrentHashMap[UUID, Array[Long]] = deathAnimations

  def getTeleportAnimations: ConcurrentHashMap[UUID, Array[Long]] = teleportAnimations

  /** Blasts going off, for the renderer: blastKey -> the fields at GameClient.BLAST_*. */
  def getBlasts: ConcurrentHashMap[Long, Array[Long]] = blasts

  /** A blast going off at (x, y): an explosion or a splash of `radius` cells, set off by a
    * projectile of `pType` (or a trap of that type) thrown by a `charId`. The first under a key
    * is the one drawn. */
  private[client] def blast(key: Long, x: Float, y: Float, colorRGB: Int, radius: Float, pType: Byte, charId: Byte,
                            trap: Boolean): Unit =
    blasts.putIfAbsent(key, Array(System.currentTimeMillis(), (x * 1000).toLong, (y * 1000).toLong,
      colorRGB.toLong, (radius * 1000).toLong, pType.toLong, charId.toLong, if (trap) 1L else 0L,
      ((key ^ (key >>> 29)) * 0x9E3779B97F4A7C15L) >>> 40, 0L))
}
