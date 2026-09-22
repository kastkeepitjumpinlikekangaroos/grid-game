package com.gridgame.common.model

import java.util.UUID

/**
 * What a trap looks like on the ground. Kept apart from the trap's id so two traps that do
 * different things can share a look — and so a renderer switches on a handful of shapes rather
 * than on the whole registry.
 */
object TrapKind {
  /** A bear trap's sprung jaws. */
  val JAWS: Byte = 0
  /** A cased charge with a blinking diode. */
  val MINE: Byte = 1
  /** A swollen spore pod. */
  val POD: Byte = 2
  /** A glyph burnt into the ground. */
  val RUNE: Byte = 3
  /** A web stretched across the cell. */
  val WEB: Byte = 4
}

/** Every trap the game knows, by the id that goes over the wire. 0 is "no trap". */
object TrapType {
  val BEAR_TRAP: Byte = 1
  val MINE: Byte = 2
  val POISON_POD: Byte = 3
  val FIRE_RUNE: Byte = 4
  val SNARE: Byte = 5
}

/**
 * What one kind of trap does. The server applies `damage` and `effects` to whoever set it off, or
 * `explosion` with its falloff to everyone in the blast; the client draws it by its `kind`.
 *
 * @param triggerRadius how near an enemy has to come, in cells. 1.0 is the trap's own cell and
 *                      its four neighbours.
 * @param maxActive     how many of these a player may have on the ground at once. A further one
 *                      takes the oldest away.
 */
case class TrapDef(
    id: Byte,
    name: String,
    kind: Byte,
    colorRGB: Int,
    triggerRadius: Float = 1.0f,
    armDelayMs: Int = 800,
    lifetimeMs: Int = 25000,
    maxActive: Int = 3,
    damage: Int = 0,
    effects: Seq[OnHitEffect] = Seq.empty,
    explosion: Option[ExplosionConfig] = None
)

object TrapDef {
  val BearTrap: TrapDef = TrapDef(
    id = TrapType.BEAR_TRAP, name = "Bear Trap", kind = TrapKind.JAWS, colorRGB = 0xFFB9A88A,
    damage = 10, effects = Seq(Stun(2000))
  )

  val Mine: TrapDef = TrapDef(
    id = TrapType.MINE, name = "Mine", kind = TrapKind.MINE, colorRGB = 0xFFD05030,
    explosion = Some(ExplosionConfig(45, 15, 3.0f))
  )

  val PoisonPod: TrapDef = TrapDef(
    id = TrapType.POISON_POD, name = "Poison Pod", kind = TrapKind.POD, colorRGB = 0xFF77BB44,
    effects = Seq(Poison(48, 6000, 500), Slow(2000, 0.6f))
  )

  val FireRune: TrapDef = TrapDef(
    id = TrapType.FIRE_RUNE, name = "Fire Rune", kind = TrapKind.RUNE, colorRGB = 0xFFFF7722,
    effects = Seq(Burn(40, 5000, 500))
  )

  val Snare: TrapDef = TrapDef(
    id = TrapType.SNARE, name = "Snare", kind = TrapKind.WEB, colorRGB = 0xFFCCCCDD,
    effects = Seq(Root(2000))
  )

  val all: Seq[TrapDef] = Seq(BearTrap, Mine, PoisonPod, FireRune, Snare)

  // Registered by this object's own initializer, so nothing can look a trap up before the
  // registry is filled — the trouble ProjectileDef has with CharacterDef can't arise here.
  private val registry: Array[TrapDef] = {
    val table = new Array[TrapDef](256)
    all.foreach(d => table(d.id & 0xFF) = d)
    table
  }

  /** The trap with this id, or null. */
  def get(id: Byte): TrapDef = registry(id & 0xFF)

  /** Add a trap to the registry. For tests pinning an effect no trap in the roster has yet, on
    * an id the game doesn't use; the roster's own are registered above, before anything can ask
    * for one. */
  def register(defs: TrapDef*): Unit = defs.foreach(d => registry(d.id & 0xFF) = d)

  def isDefined(id: Byte): Boolean = get(id) != null

  /** The furthest any trap reaches, so a scan for what a player is standing near is bounded. */
  val maxTriggerRadius: Float = all.map(_.triggerRadius).max
}

/**
 * One trap on the ground. Armed after its def's delay and gone at `expiresAt`, whichever comes
 * first — a trigger, the owner leaving the match, or an older one of theirs being pushed off.
 */
final class Trap(
    val id: Int,
    val ownerId: UUID,
    val teamId: Byte,
    val x: Int,
    val y: Int,
    val trapType: Byte,
    val placedAt: Long,
    val armedAt: Long,
    val expiresAt: Long
) {
  def defn: TrapDef = TrapDef.get(trapType)

  def colorRGB: Int = {
    val d = defn
    if (d != null) d.colorRGB else 0xFFFFFFFF
  }

  def isArmed(now: Long): Boolean = now >= armedAt

  def isExpired(now: Long): Boolean = now >= expiresAt

  /** Is (px, py) near enough to set this trap off? */
  def catches(px: Int, py: Int): Boolean = {
    val d = defn
    if (d == null) return false
    val dx = px - x
    val dy = py - y
    (dx * dx + dy * dy).toFloat <= d.triggerRadius * d.triggerRadius
  }

  override def toString: String = s"Trap{id=$id, type=$trapType, pos=($x, $y)}"
}

/**
 * Where a thrown trap lands. The client picks the cell with this and the server checks it with
 * this, so a placement the client shows is one the server takes — the same reason [[Teleport]]
 * exists for blinks and stars.
 */
object TrapPlacement {
  /**
   * The cell a trap thrown from (fromX, fromY) toward (aimX, aimY) lands on: as far along that
   * line as the aim asks for, at most `maxRange` cells, stopping before the first cell it can't
   * lie on. None when there is nowhere to put it (the thrower is somehow standing in a wall).
   */
  def target(world: WorldData, fromX: Int, fromY: Int, aimX: Double, aimY: Double,
             maxRange: Int): Option[Position] = {
    val here = if (world.isWalkable(fromX, fromY)) Some(new Position(fromX, fromY)) else None
    val dx = aimX - fromX
    val dy = aimY - fromY
    val len = Math.sqrt(dx * dx + dy * dy)
    if (len < 0.01) return here
    val reach = Math.min(len, maxRange.toDouble)
    val ux = dx / len
    val uy = dy / len
    var best = here
    val steps = Math.ceil(reach).toInt
    var i = 1
    while (i <= steps) {
      // The last step lands exactly on the aim point, so a cursor inside the range picks the
      // cell under it rather than the nearest whole cell out along the line
      val t = Math.min(reach, i.toDouble)
      val x = Math.round(fromX + ux * t).toInt
      val y = Math.round(fromY + uy * t).toInt
      if (!world.isWalkable(x, y)) return best
      best = Some(new Position(x, y))
      i += 1
    }
    best
  }

  /**
   * Server side: may a player the server has at (fromX, fromY) put a trap on (x, y)? The cell has
   * to be one a trap can lie on, within reach, with nothing solid on the way to it — the throw
   * stops at the first wall, so a trap can't be dropped on the far side of one.
   *
   * Measured with the slack a teleport gets ([[Teleport.withinReach]]): the client threw from
   * where it has the player, which can be a step ahead of where the server has them.
   */
  def isValidTarget(world: WorldData, fromX: Int, fromY: Int, x: Int, y: Int, maxRange: Int): Boolean =
    world.isWalkable(x, y) &&
      Teleport.withinReach(x - fromX, y - fromY, maxRange) &&
      pathIsClear(world, fromX, fromY, x, y)

  /** Is every cell on the straight line from (fromX, fromY) to (x, y) one a throw passes over? */
  private def pathIsClear(world: WorldData, fromX: Int, fromY: Int, x: Int, y: Int): Boolean = {
    val dx = (x - fromX).toDouble
    val dy = (y - fromY).toDouble
    val len = Math.sqrt(dx * dx + dy * dy)
    if (len < 0.01) return true
    // Half-cell steps: a whole-cell walk of a shallow diagonal can hop the corner of a wall
    val steps = Math.ceil(len * 2).toInt
    var i = 1
    while (i <= steps) {
      val t = i.toDouble / steps
      val cx = Math.round(fromX + dx * t).toInt
      val cy = Math.round(fromY + dy * t).toInt
      if (!world.isWalkable(cx, cy)) return false
      i += 1
    }
    true
  }

  /** Every cell a player stepping from (fromX, fromY) to (toX, toY) passed over, the ends
    * included, visited in order. A lost update can move a player several cells at once, and a
    * trap they stepped clean over has still been stepped on. */
  def walkCells(fromX: Int, fromY: Int, toX: Int, toY: Int)(fn: (Int, Int) => Unit): Unit = {
    val dx = (toX - fromX).toDouble
    val dy = (toY - fromY).toDouble
    val steps = Math.max(Math.abs(toX - fromX), Math.abs(toY - fromY))
    fn(fromX, fromY)
    var i = 1
    while (i <= steps) {
      val t = i.toDouble / steps
      fn(Math.round(fromX + dx * t).toInt, Math.round(fromY + dy * t).toInt)
      i += 1
    }
  }
}
