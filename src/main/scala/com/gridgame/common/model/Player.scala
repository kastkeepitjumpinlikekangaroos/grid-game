package com.gridgame.common.model

import java.net.InetSocketAddress
import java.util.Objects
import java.util.UUID

class Player(
    private val id: UUID,
    private var name: String,
    @volatile private var position: Position,
    private var colorRGB: Int,
    @volatile private var health: Int = 100,
    private var maxHealth: Int = 100
) {
  Objects.requireNonNull(id, "Player ID cannot be null")
  Objects.requireNonNull(position, "Position cannot be null")

  private var lastUpdateTime: Long = System.currentTimeMillis()
  // Stored as AnyRef to avoid Netty dependency in common module; cast to Channel in server code
  private var tcpChannel: AnyRef = _
  private var udpAddress: InetSocketAddress = _
  private var direction: Direction = Direction.Down
  private var shieldUntil: Long = 0
  private var gemBoostUntil: Long = 0
  private var _chargeLevel: Int = 0
  private var frozenUntil: Long = 0
  // A stun is a freeze that is drawn differently: frozenUntil holds it, this only says which
  // of the two it is, so every "is frozen" gate keeps working untouched.
  private var stunnedUntil: Long = 0
  private var ccImmuneUntil: Long = 0
  @volatile private var phasedUntil: Long = 0
  private var characterId: Byte = CharacterId.DEFAULT.id
  private var teamId: Byte = 0

  // Burn (DoT) state
  private var burnUntil: Long = 0
  private var burnDamagePerTick: Int = 0
  private var burnTickMs: Int = 0
  private var lastBurnTick: Long = 0
  private var burnOwnerId: UUID = _

  // Poison (DoT) state — a slot of its own, so a poison and a burn run at once. Counted in
  // ticks rather than timed out: each tick falls due a whole tickMs after the one before was
  // due, and the poison lasts until its last tick has landed. Timed out on the clock like a
  // burn, every bite lands up to a server tick late, the last one falls after the deadline, and
  // the poison comes up a tick short of the damage its def promises.
  private var poisonTicksLeft: Int = 0
  private var poisonDamageLeft: Int = 0
  private var poisonTickMs: Int = 0
  private var nextPoisonTickAt: Long = 0
  private var poisonOwnerId: UUID = _

  // Speed boost state
  private var speedBoostUntil: Long = 0

  // Root state (can't move but CAN attack)
  private var rootedUntil: Long = 0

  // Slow state (reduced movement speed)
  private var slowedUntil: Long = 0
  private var slowMultiplier: Float = 1.0f

  // Barrier state (BarrierCast): a shield wall carried in front, facing barrierAngle (world
  // radians). Up until barrierUntil; dropping it early moves that to the moment it dropped, which
  // is what a client fades it out from. The cooldown counts from the raise, so that is kept apart.
  @volatile private var barrierRaisedAt: Long = 0
  @volatile private var barrierUntil: Long = 0
  @volatile private var barrierAngle: Float = 0f

  // Health regen accumulator
  private var _regenAccumulator: Double = 0.0

  def getId: UUID = id

  def getName: String = name

  def setName(name: String): Unit = {
    this.name = name
  }

  def getPosition: Position = position

  def setPosition(position: Position): Unit = {
    this.position = Objects.requireNonNull(position, "Position cannot be null")
    this.lastUpdateTime = System.currentTimeMillis()
  }

  def getColorRGB: Int = colorRGB

  def setColorRGB(colorRGB: Int): Unit = {
    this.colorRGB = colorRGB
  }

  def getLastUpdateTime: Long = lastUpdateTime

  def updateHeartbeat(): Unit = {
    this.lastUpdateTime = System.currentTimeMillis()
  }

  def getTcpChannel: AnyRef = tcpChannel

  def setTcpChannel(channel: AnyRef): Unit = {
    this.tcpChannel = channel
  }

  def getUdpAddress: InetSocketAddress = udpAddress

  def setUdpAddress(address: InetSocketAddress): Unit = {
    this.udpAddress = address
  }

  def getDirection: Direction = direction

  def setDirection(direction: Direction): Unit = {
    this.direction = direction
  }

  def getHealth: Int = health

  def getMaxHealth: Int = maxHealth

  def setMaxHealth(mh: Int): Unit = {
    this.maxHealth = mh
  }

  def setHealth(h: Int): Unit = {
    this.health = Math.max(0, Math.min(maxHealth, h))
  }

  /**
   * Take `amount` damage. True if this is the blow that killed the player: however many hits
   * land together, from whichever threads (projectile tick, burn tick), exactly one of them sees
   * the player go from alive to dead, so a death is scored and a respawn scheduled once.
   */
  def damage(amount: Int): Boolean = synchronized {
    val wasAlive = health > 0
    setHealth(health - amount)
    val killed = wasAlive && health <= 0
    // The dead hold nothing up
    if (killed) dropBarrier()
    killed
  }

  // Server side: how many times the server has put this player somewhere their client didn't —
  // a pull, a knockback, a respawn, a freeze holding them in place. The client sends back the
  // count it has seen with every position, which is how the server tells a step sent before
  // the client knew about the move (drop it) from one sent after (apply it).
  @volatile private var serverMoves: Int = 0

  def getServerMoves: Int = serverMoves

  /** The server has just moved (or pinned) this player. Returns the new count. */
  def recordServerMove(): Int = synchronized {
    serverMoves += 1
    serverMoves
  }

  def getShieldUntil: Long = shieldUntil

  def setShieldUntil(until: Long): Unit = {
    this.shieldUntil = until
  }

  def hasShield: Boolean = System.currentTimeMillis() < shieldUntil

  def getGemBoostUntil: Long = gemBoostUntil

  def setGemBoostUntil(until: Long): Unit = {
    this.gemBoostUntil = until
  }

  def hasGemBoost: Boolean = System.currentTimeMillis() < gemBoostUntil

  def getChargeLevel: Int = _chargeLevel

  def setChargeLevel(level: Int): Unit = {
    this._chargeLevel = Math.max(0, Math.min(100, level))
  }

  def getFrozenUntil: Long = frozenUntil

  def setFrozenUntil(until: Long): Unit = {
    this.frozenUntil = until
  }

  def isFrozen: Boolean = System.currentTimeMillis() < frozenUntil

  def isCCImmune: Boolean = System.currentTimeMillis() < ccImmuneUntil

  /** Try to freeze this player. Returns false if already frozen, CC immune, or phased. */
  def tryFreeze(durationMs: Long): Boolean = {
    if (isFrozen || isCCImmune || isPhased) return false
    val now = System.currentTimeMillis()
    frozenUntil = now + durationMs
    stunnedUntil = 0
    ccImmuneUntil = frozenUntil + com.gridgame.common.Constants.CC_IMMUNITY_MS
    true
  }

  def getStunnedUntil: Long = stunnedUntil

  def setStunnedUntil(until: Long): Unit = {
    this.stunnedUntil = until
  }

  /** A stunned player is frozen too — this is only what the client draws over them. */
  def isStunned: Boolean = System.currentTimeMillis() < stunnedUntil

  /**
   * Try to stun this player: a freeze by another name, with the same rules, the same timer and
   * the same CC immunity after it, so everything that already refuses to move, fire or cast
   * while frozen refuses while stunned without knowing stuns exist.
   */
  def tryStun(durationMs: Long): Boolean = {
    if (isFrozen || isCCImmune || isPhased) return false
    val now = System.currentTimeMillis()
    frozenUntil = now + durationMs
    stunnedUntil = frozenUntil
    ccImmuneUntil = frozenUntil + com.gridgame.common.Constants.CC_IMMUNITY_MS
    true
  }

  def getPhasedUntil: Long = phasedUntil

  def setPhasedUntil(until: Long): Unit = {
    this.phasedUntil = until
  }

  def isPhased: Boolean = System.currentTimeMillis() < phasedUntil

  def getCharacterId: Byte = characterId

  def setCharacterId(id: Byte): Unit = {
    this.characterId = id
    this.maxHealth = CharacterDef.get(id).maxHealth
  }

  def getTeamId: Byte = teamId

  def setTeamId(id: Byte): Unit = {
    this.teamId = id
  }

  // Burn accessors
  def isBurning: Boolean = System.currentTimeMillis() < burnUntil

  def getBurnUntil: Long = burnUntil

  def getBurnDamagePerTick: Int = burnDamagePerTick

  def getBurnTickMs: Int = burnTickMs

  def getLastBurnTick: Long = lastBurnTick

  def setLastBurnTick(t: Long): Unit = { lastBurnTick = t }

  def getBurnOwnerId: UUID = burnOwnerId

  def applyBurn(totalDamage: Int, durationMs: Int, tickMs: Int, ownerId: UUID): Unit = {
    val now = System.currentTimeMillis()
    this.burnUntil = now + durationMs
    this.burnTickMs = tickMs
    val numTicks = durationMs / tickMs
    this.burnDamagePerTick = if (numTicks > 0) totalDamage / numTicks else totalDamage
    this.lastBurnTick = now
    this.burnOwnerId = ownerId
  }

  def clearBurn(): Unit = {
    this.burnUntil = 0
    this.burnDamagePerTick = 0
  }

  // Poison accessors
  /** Poisoned until the last tick has landed. On the client, where nothing ticks it, until the
    * server's updates stop saying so (clearPoison). */
  def isPoisoned: Boolean = poisonTicksLeft > 0

  /** What the next tick will take: the damage left over the ticks left, so they add up to the
    * total exactly rather than losing the remainder of an uneven split. */
  def getPoisonDamagePerTick: Int = if (poisonTicksLeft > 0) poisonDamageLeft / poisonTicksLeft else 0

  def getNextPoisonTickAt: Long = nextPoisonTickAt

  def getPoisonOwnerId: UUID = poisonOwnerId

  /** Poison this player: `totalDamage` over `durationMs`, a tick every `tickMs`. A new poison
    * replaces the one running, as a new burn does. Under the lock the ticks are taken under, so
    * a tick can't read half of the old poison and half of the new. */
  def applyPoison(totalDamage: Int, durationMs: Int, tickMs: Int, ownerId: UUID): Unit = synchronized {
    val tick = Math.max(1, tickMs)
    this.poisonTickMs = tick
    this.poisonTicksLeft = Math.max(1, durationMs / tick)
    this.poisonDamageLeft = Math.max(0, totalDamage)
    this.nextPoisonTickAt = System.currentTimeMillis() + tick
    this.poisonOwnerId = ownerId
  }

  /** Take the poison's next tick if it has fallen due by `now`: the damage it does, or -1 when
    * none is due. Callers hold the player's lock, so two ticks can't both take the same one. */
  def takePoisonTick(now: Long): Int = {
    if (poisonTicksLeft <= 0 || now < nextPoisonTickAt) return -1
    val dmg = poisonDamageLeft / poisonTicksLeft
    poisonDamageLeft -= dmg
    poisonTicksLeft -= 1
    nextPoisonTickAt += poisonTickMs
    dmg
  }

  def clearPoison(): Unit = {
    this.poisonTicksLeft = 0
    this.poisonDamageLeft = 0
  }

  // Speed boost accessors
  def hasSpeedBoost: Boolean = System.currentTimeMillis() < speedBoostUntil

  def getSpeedBoostUntil: Long = speedBoostUntil

  def setSpeedBoostUntil(until: Long): Unit = { this.speedBoostUntil = until }

  // Root accessors
  def isRooted: Boolean = System.currentTimeMillis() < rootedUntil

  def getRootedUntil: Long = rootedUntil

  def setRootedUntil(until: Long): Unit = { this.rootedUntil = until }

  /** Try to root this player. Returns false if CC immune or phased. */
  def tryRoot(durationMs: Long): Boolean = {
    if (isCCImmune || isPhased) return false
    val now = System.currentTimeMillis()
    rootedUntil = now + durationMs
    ccImmuneUntil = rootedUntil + com.gridgame.common.Constants.CC_IMMUNITY_MS
    true
  }

  // Slow accessors
  def isSlowed: Boolean = System.currentTimeMillis() < slowedUntil

  def getSlowedUntil: Long = slowedUntil

  def getSlowMultiplier: Float = slowMultiplier

  def setSlowedUntil(until: Long): Unit = { this.slowedUntil = until }

  def setSlowMultiplier(m: Float): Unit = { this.slowMultiplier = m }

  /** Try to slow this player. Returns false if CC immune or phased. */
  def trySlow(durationMs: Long, multiplier: Float): Boolean = {
    if (isCCImmune || isPhased) return false
    val now = System.currentTimeMillis()
    slowedUntil = now + durationMs
    slowMultiplier = multiplier
    true
  }

  def clearSlow(): Unit = {
    this.slowedUntil = 0
    this.slowMultiplier = 1.0f
  }

  // Barrier accessors
  def hasBarrier: Boolean = System.currentTimeMillis() < barrierUntil

  def getBarrierUntil: Long = barrierUntil

  def setBarrierUntil(until: Long): Unit = { this.barrierUntil = until }

  def getBarrierRaisedAt: Long = barrierRaisedAt

  def setBarrierRaisedAt(at: Long): Unit = { this.barrierRaisedAt = at }

  def getBarrierAngle: Float = barrierAngle

  def setBarrierAngle(radians: Float): Unit = { this.barrierAngle = radians }

  /** Raise a barrier facing `angle` (world radians) for `durationMs` from `now`. */
  def raiseBarrier(now: Long, durationMs: Int, angle: Float): Unit = {
    this.barrierAngle = angle
    this.barrierRaisedAt = now
    this.barrierUntil = now + durationMs
  }

  /** Drop the barrier, if it is up: it ends now. */
  def dropBarrier(): Unit = {
    val now = System.currentTimeMillis()
    if (now < barrierUntil) barrierUntil = now
  }

  // Health regen accessors
  def getRegenAccumulator: Double = _regenAccumulator
  def addRegenAccumulator(amount: Double): Unit = { _regenAccumulator += amount }
  def subtractRegenAccumulator(amount: Double): Unit = { _regenAccumulator -= amount }
  def resetRegenAccumulator(): Unit = { _regenAccumulator = 0.0 }

  def isDead: Boolean = health <= 0

  override def equals(obj: Any): Boolean = {
    obj match {
      case that: Player => this.id.equals(that.id)
      case _ => false
    }
  }

  override def hashCode(): Int = id.hashCode()

  override def toString: String = {
    s"Player{id=${id.toString.substring(0, 8)}, name='$name', position=$position}"
  }
}

object Player {
  def generateColorFromUUID(id: UUID): Int = {
    val hash = id.getLeastSignificantBits
    val hue = (hash % 360) / 360.0f
    val saturation = 0.7f
    val brightness = 0.9f

    val rgb = java.awt.Color.HSBtoRGB(hue, saturation, brightness)
    0xFF000000 | (rgb & 0x00FFFFFF)
  }
}
