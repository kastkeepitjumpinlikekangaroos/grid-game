package com.gridgame.client.game

import com.gridgame.client.diagnostics.Diagnostics
import com.gridgame.common.diagnostics.Recording.NoticedKind
import com.gridgame.client.audio.AudioManager
import com.gridgame.common.Constants
import com.gridgame.common.model._
import com.gridgame.common.protocol._

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Us: who we are and what we play as, where we are, our health, death and respawn, and the
 * effects on us. We move our own player and tell the server every step ([[sendPositionUpdate]]);
 * the server's word on us ([[handleOwnUpdate]]) sets our health and effects, and moves us only
 * when it is a server move we haven't taken yet (see Position authority in the server docs).
 */
trait LocalPlayer { this: GameClient =>
  // Written as we log in (the FX thread), read by the packet thread, the render thread and the
  // heartbeat's: a heartbeat under the id from before the login is one the server drops
  @volatile private[game] var localPlayerId: UUID = UUID.randomUUID()
  private[game] var localColorRGB: Int = Player.generateColorFromUUID(localPlayerId)
  private[game] val localPosition: AtomicReference[Position] = new AtomicReference(
    initialWorld.getValidSpawnPoint()
  )
  private[game] val localDirection: AtomicReference[Direction] = new AtomicReference(Direction.Down)
  private[game] val localHealth: AtomicInteger = new AtomicInteger(100)
  @volatile var localTeamId: Byte = 0
  @volatile var selectedCharacterId: Byte = CharacterId.DEFAULT.id

  @volatile private[game] var isDead = false
  @volatile var isRespawning: Boolean = false
  private val localDeathTime: AtomicLong = new AtomicLong(0)
  @volatile private var rejoinListener: () => Unit = _

  // The effects on us, each until a time on our clock
  private val frozenUntil: AtomicLong = new AtomicLong(0)
  private[game] val phasedUntil: AtomicLong = new AtomicLong(0)
  private val burningUntil: AtomicLong = new AtomicLong(0)
  private val speedBoostUntil: AtomicLong = new AtomicLong(0)
  // When we last cast a haste of our own (hasteCast): the server's updates sent before it heard of
  // the cast still arrive without the boost for a round trip, and mustn't take it back off us
  private val hasteCastAt: AtomicLong = new AtomicLong(0)
  private val rootedUntil: AtomicLong = new AtomicLong(0)
  private val slowedUntil: AtomicLong = new AtomicLong(0)
  // A stun is a freeze the client draws differently, so it comes with the frozen flag set too
  private val stunnedUntil: AtomicLong = new AtomicLong(0)
  private val poisonedUntil: AtomicLong = new AtomicLong(0)
  // How strong the slow we are under is (packet byte [52]). A Slow(_, 0.3f) really is 30% of
  // our pace; every slow used to be a flat half whatever its def said, which is what we assume
  // until the server tells us.
  private val DEFAULT_SLOW_MULTIPLIER = 0.5f
  // How long after casting a haste an update without the boost is taken to have been sent before the
  // server heard of it: a round trip and then some
  private val HASTE_PREDICTION_MS = 600L
  @volatile private[game] var slowMultiplier: Float = DEFAULT_SLOW_MULTIPLIER

  // Server moves we have taken (Player.getServerMoves): pulls, knockbacks, respawns, freezes,
  // corrections. Sent with every position; the server drops positions sent before a move we
  // hadn't seen yet. Reset each match, since each match counts its own.
  @volatile private[game] var serverMovesSeen: Int = 0
  // And how many of them were knockbacks (Player.getKnockbacks): a move one made is slid, not
  // jumped (KnockbackSlide). Reset with it.
  @volatile private[game] var knockbacksSeen: Int = 0

  def getSelectedCharacterDef: CharacterDef = CharacterDef.get(selectedCharacterId)

  def getSelectedCharacterMaxHealth: Int = getSelectedCharacterDef.maxHealth

  def getLocalPlayerId: UUID = localPlayerId
  def getLocalColorRGB: Int = localColorRGB
  def getLocalPosition: Position = localPosition.get()
  def getLocalDirection: Direction = localDirection.get()
  def getLocalHealth: Int = localHealth.get()
  def getIsDead: Boolean = isDead
  def getLocalDeathTime: Long = localDeathTime.get()

  def isPhased: Boolean = System.currentTimeMillis() < phasedUntil.get()
  def isFrozen: Boolean = System.currentTimeMillis() < frozenUntil.get()
  def isBurning: Boolean = System.currentTimeMillis() < burningUntil.get()
  def hasSpeedBoost: Boolean = System.currentTimeMillis() < speedBoostUntil.get()
  def isRooted: Boolean = System.currentTimeMillis() < rootedUntil.get()
  def isSlowed: Boolean = System.currentTimeMillis() < slowedUntil.get()
  def isStunned: Boolean = System.currentTimeMillis() < stunnedUntil.get()
  def isPoisoned: Boolean = System.currentTimeMillis() < poisonedUntil.get()
  def getSlowMultiplier: Float = slowMultiplier

  /**
   * We cast a haste of our own (a ground slam whose projectile speeds its caster up): we walk at its
   * pace from now, as a phase starts at our cast, rather than a round trip later when the server's
   * word of it comes back. The server's own boost (bytes [59-60] of its updates) takes over when it
   * does, and a cast it refused ends with the next update after the round trip.
   */
  private[game] def hasteCast(durationMs: Int): Unit = {
    val now = System.currentTimeMillis()
    hasteCastAt.set(now)
    if (now + durationMs > speedBoostUntil.get()) speedBoostUntil.set(now + durationMs)
  }

  /** None of the effects on us any more (a new life), and nothing of the death before it. */
  private[game] def clearEffects(): Unit = {
    frozenUntil.set(0)
    phasedUntil.set(0)
    burningUntil.set(0)
    speedBoostUntil.set(0)
    hasteCastAt.set(0)
    rootedUntil.set(0)
    slowedUntil.set(0)
    stunnedUntil.set(0)
    poisonedUntil.set(0)
    slowMultiplier = DEFAULT_SLOW_MULTIPLIER
    localDeathTime.set(0)
  }

  private def getEffectFlags: Int = {
    (if (hasShield) 0x01 else 0) | (if (hasGemBoost) 0x02 else 0) | (if (isFrozen) 0x04 else 0) | (if (isPhased) 0x08 else 0) | (if (isBurning) 0x10 else 0) | (if (hasSpeedBoost) 0x20 else 0) | (if (isRooted) 0x40 else 0) | (if (isSlowed) 0x80 else 0)
  }

  private def getEffectFlags2: Int =
    (if (isStunned) 0x01 else 0) | (if (isPoisoned) 0x02 else 0) | (if (hasBarrier) 0x04 else 0)

  private def getSlowPercent: Int = if (isSlowed) Math.round(slowMultiplier * 100f) else 0

  /** Tell the server where we are, and everything else about us it relays: health, charge, our
    * effects, our barrier and which way it faces. */
  private[game] def sendPositionUpdate(position: Position): Unit = {
    // With a barrier up, where we aim is where it faces
    val barrierUp = hasBarrier
    val aim = if (barrierUp) getAimAngle else 0f
    send(new PlayerUpdatePacket(
      sequenceNumber.getAndIncrement(),
      localPlayerId,
      Packet.getCurrentTimestamp,
      position,
      localColorRGB,
      localHealth.get(),
      getChargeLevel,
      getEffectFlags,
      selectedCharacterId,
      localTeamId,
      serverMovesSeen,
      getEffectFlags2,
      if (barrierUp) PlayerUpdatePacket.encodeAimAngle(aim) else 0,
      getSlowPercent
    ))
    barrierAnnounced = barrierUp
    if (barrierUp) {
      lastBarrierSentAt.set(System.currentTimeMillis())
      lastBarrierSentAngle = aim
    }
  }

  /** The server's word on us: our health and the effects on us, and a position only when it is
    * a server move we haven't taken yet. Only the newest word counts (OtherPlayers.newWordOn): an
    * update that overtook another, or was overtaken by our death or respawn, is old news. */
  private[game] def handleOwnUpdate(updatePacket: PlayerUpdatePacket): Unit = {
    if (!newWordOn(localPlayerId, updatePacket.getSequenceNumber)) return
    val serverHealth = updatePacket.getHealth
    localHealth.set(serverHealth)

    // Handle effect flags from server. A hold (a freeze or a stun), a root and a slow last as long
    // as the update says they have left (bytes [61-63]): with the flag alone, armed for 5s or 3s
    // when it first came and lifted by the next update without it, we were held until the server
    // next happened to say anything about us — a regeneration tick, up to most of a second after
    // a 400ms stun had ended, or at full health nothing at all, and a 2s snare held us for 3.
    val flags = updatePacket.getEffectFlags
    val now = System.currentTimeMillis()
    if ((flags & 0x04) != 0) {
      frozenUntil.set(GameClient.effectEnds(updatePacket.getHeldMs, frozenUntil.get(), now, 5000))
    } else {
      frozenUntil.set(0)
    }
    if ((flags & 0x10) != 0) {
      if (now >= burningUntil.get()) burningUntil.set(now + 1000)
    } else {
      burningUntil.set(0)
    }
    if ((flags & 0x20) != 0) {
      // For as long as the server says it has left. Only the flag used to come, and it was taken
      // as a second's boost re-armed by the next update to carry it, which at full health might
      // be the next hit: a 4s boost walked for one
      val left = updatePacket.getSpeedBoostMs
      if (left > 0) speedBoostUntil.set(now + left)
      else if (now >= speedBoostUntil.get()) speedBoostUntil.set(now + 1000)
    } else if (now - hasteCastAt.get() >= HASTE_PREDICTION_MS) {
      // An update sent before the server heard of a haste we have just cast says we have none
      speedBoostUntil.set(0)
    }
    if ((flags & 0x40) != 0) {
      rootedUntil.set(GameClient.effectEnds(updatePacket.getRootedMs, rootedUntil.get(), now, 3000))
    } else {
      rootedUntil.set(0)
    }
    if ((flags & 0x80) != 0) {
      slowedUntil.set(GameClient.effectEnds(updatePacket.getSlowedMs, slowedUntil.get(), now, 3000))
      // How hard: a slow only makes us step at its own rate if we know what that rate is
      if (updatePacket.getSlowPercent > 0) slowMultiplier = updatePacket.getSlowPercent / 100f
    } else {
      slowedUntil.set(0)
      slowMultiplier = DEFAULT_SLOW_MULTIPLIER
    }
    // A stun comes with the frozen flag as well: this only says which of the two to draw
    val flags2 = updatePacket.getEffectFlags2
    if ((flags2 & 0x01) != 0) {
      stunnedUntil.set(GameClient.effectEnds(updatePacket.getHeldMs, stunnedUntil.get(), now, 5000))
    } else {
      stunnedUntil.set(0)
    }
    // Renewed by every update that carries it, not only the first. A poison stops regen, so
    // a player standing still hears nothing between its ticks, and a timer armed once ran
    // out between them and blinked the bubbles off. The update for its last tick clears it.
    if ((flags2 & 0x02) != 0) poisonedUntil.set(now + 3000)
    else poisonedUntil.set(0)
    // Don't overwrite local phased state from server echo — local timer is authoritative
    // Server echoes phased flag to confirm it, but we don't reset the timer. The same goes
    // for a barrier (flags2 bit 2): we raise, turn and drop our own, and the server's view
    // of it lags ours by the time it takes an update to get there and back.

    // The position counts only when it is a server move we haven't taken yet (a pull, a
    // knockback, a freeze, a respawn, a correction). Any other update carries wherever the
    // server last heard we were, a step or two behind a player who is walking, and
    // snapping to that on every regen tick, burn tick or lifesteal rubber-banded us back.
    if (updatePacket.getServerMoves - serverMovesSeen > 0) {
      serverMovesSeen = updatePacket.getServerMoves
      swoopingUntil.set(0) // the server's move ends a dash
      // A knockback among the moves: we are slid to where it put us, not jumped there
      val knockedBack = updatePacket.getKnockbacks > knockbacksSeen
      if (knockedBack) knockbacksSeen = updatePacket.getKnockbacks
      // Put somewhere we didn't walk to, by no push: a correction or a teleport, a player may mean it
      // when they report "it pulled me back"
      if (!knockedBack && !isDead) {
        val was = localPosition.get()
        val to = updatePacket.getPosition
        val cells = Math.hypot(to.getX - was.getX, to.getY - was.getY)
        if (cells >= 1.5) Diagnostics.noticed(NoticedKind.SnapBack, (cells * 100).toInt)
      }
      placedByServer(updatePacket.getPosition, knockedBack)
    }

    if (serverHealth <= 0) died()
  }

  /** We are dead: by an update that says so, or the server's word of the kill (GameEvent.KILL,
    * OtherPlayers.heardKilled), whichever comes first. Until the respawn puts us back. */
  private[game] def died(): Unit = {
    localHealth.set(0)
    if (!isDead) {
      isDead = true
      isRespawning = true
      // The server dropped it with the killing blow; there is nothing to tell it
      clearBarrier()
      // And refuses whatever we fired after it: not lost, so not counted as lost
      pendingSpawns.dropWaiting(nanoClock())
      localDeathTime.set(System.currentTimeMillis())
      println("GameClient: You have died! Auto-respawning in 3s...")
    }
  }

  /** Our own join echo, at the start of a match: our team (it colours our health bar), where the
    * server placed us, which it picked to keep players apart, and the character it has us play. We
    * used to keep a spawn setWorld had picked at random and send that instead, which the server
    * took: two players could start on one spawn, and everyone saw each other teleport. And the
    * character is the server's: one picked as the host started the match, or in the ranked queue as
    * it found the match, reached it too late to count, and we played the match as the one picked —
    * every shot and every cast refused as another character's, every step judged at another's pace. */
  private[game] def handleOwnJoin(join: PlayerJoinPacket): Unit = {
    localTeamId = join.getTeamId
    if (CharacterDef.isValid(join.getCharacterId)) selectedCharacterId = join.getCharacterId
    noteOwnCharacter(selectedCharacterId)
    localPosition.set(join.getPosition)
    localHealth.set(join.getHealth)
    AudioManager.playSpawn()
  }

  /** The server respawned us at (spawnX, spawnY) (GameEvent.RESPAWN). */
  private[game] def respawned(spawnX: Int, spawnY: Int): Unit = {
    AudioManager.playSpawn()
    isDead = false
    isRespawning = false
    localHealth.set(getSelectedCharacterMaxHealth)
    localPosition.set(new Position(spawnX, spawnY))
    newLife()
    if (rejoinListener != null) rejoinListener()
    println(s"GameClient: Auto-respawned at ($spawnX, $spawnY)")
  }

  def rejoin(): Unit = {
    if (!isDead) return

    // Reset local state
    isDead = false
    localHealth.set(getSelectedCharacterMaxHealth)

    // Get a new spawn point
    val world = currentWorld.get()
    val newSpawn = world.getValidSpawnPoint()
    localPosition.set(newSpawn)
    localDirection.set(Direction.Down)

    // Nothing of the last life's projectiles and traps, nor of the life itself
    forgetProjectiles()
    forgetTraps()
    newLife()

    // Send join packet to server
    sendJoinPacket()

    if (rejoinListener != null) rejoinListener()

    println(s"GameClient: Rejoined at $newSpawn")
  }

  def setRejoinListener(listener: () => Unit): Unit = {
    rejoinListener = listener
  }

  /** Distance in grid cells from the local player — drives sound attenuation. */
  private[game] def distanceFromLocal(x: Float, y: Float): Float = {
    val pos = localPosition.get()
    val dx = x - pos.getX
    val dy = y - pos.getY
    Math.sqrt((dx * dx + dy * dy).toDouble).toFloat
  }

  /** Stereo pan (-1 left .. +1 right) for a world position. Screen X in an
    * isometric projection is (wx - wy), so that — not world X — is the axis
    * that maps to left/right for the listener. */
  private[game] def panFromLocal(x: Float, y: Float): Float = {
    val pos = localPosition.get()
    val screenDx = (x - pos.getX) - (y - pos.getY)
    Math.max(-1f, Math.min(1f, screenDx / Constants.AUDIO_PAN_RANGE_CELLS))
  }
}
