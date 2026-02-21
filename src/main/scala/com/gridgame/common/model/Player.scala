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
  private var ccImmuneUntil: Long = 0
  @volatile private var phasedUntil: Long = 0
  private var serverTeleportedUntil: Long = 0
  private var characterId: Byte = CharacterId.DEFAULT.id
  private var teamId: Byte = 0

  // Burn (DoT) state
  private var burnUntil: Long = 0
  private var burnDamagePerTick: Int = 0
  private var burnTickMs: Int = 0
  private var lastBurnTick: Long = 0
  private var burnOwnerId: UUID = _

  // Speed boost state
  private var speedBoostUntil: Long = 0

  // Root state (can't move but CAN attack)
  private var rootedUntil: Long = 0

  // Slow state (reduced movement speed)
  private var slowedUntil: Long = 0
  private var slowMultiplier: Float = 1.0f

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
    ccImmuneUntil = frozenUntil + com.gridgame.common.Constants.CC_IMMUNITY_MS
    true
  }

  def getPhasedUntil: Long = phasedUntil

  def setPhasedUntil(until: Long): Unit = {
    this.phasedUntil = until
  }

  def isPhased: Boolean = System.currentTimeMillis() < phasedUntil

  def setServerTeleportedUntil(until: Long): Unit = {
    this.serverTeleportedUntil = until
  }

  def isServerTeleported: Boolean = System.currentTimeMillis() < serverTeleportedUntil

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
