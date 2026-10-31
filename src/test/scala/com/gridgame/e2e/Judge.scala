package com.gridgame.e2e

import com.gridgame.common.model._
import com.gridgame.common.protocol._
import com.gridgame.server.game.{GameInstance, PacketValidator}

/**
 * What the server made of one request an honest client sent, on a strict loopback: the client had
 * heard everything the server had said before it acted, so whatever it asks for it believes the rules
 * allow, and the server must take it. A refusal is the two disagreeing about the rules, which a
 * player sees as a step snapping back, a shot that never leaves, or a cooldown spent on nothing.
 * The judge is made before the request reaches the server, sees everything the server sends while it
 * handles it ([[saw]]), and gives its verdict after.
 */
private[e2e] abstract class Judge(val loop: Loopback, val c: LoopClient, val gi: GameInstance, val me: Player) {
  def saw(to: LoopClient, p: Packet): Unit = ()
  def verdict(): Unit

  /** The first 8 characters of the player's id, which the server names them by on stderr. */
  protected def named: String = c.id.toString.substring(0, 8)

  /** How the server has the player, for a refusal to say. */
  protected def serverView: String = {
    val now = System.currentTimeMillis()
    s"the server has ${c.name} (${CharacterDef.get(me.getCharacterId).displayName}) at ${me.getPosition}, " +
      s"${if (me.isDead) "dead" else s"${me.getHealth} hp"}${if (me.isFrozen) ", held" else ""}" +
      s"${if (me.isRooted) ", rooted" else ""}${if (me.isPhased) s", phased for ${me.getPhasedUntil - now}ms" else ""}" +
      s"${if (gi.attacksLocked) ", in the ceasefire" else ""}"
  }
}

private[e2e] object Judge {
  /** How long a client a race has put a step off the server has to come back into line: the server
    * corrects one whose steps it has refused for 250ms, at most every 500ms (StepRequests). */
  val RaceSettleMs = 1500L

  /** The judge of a request sent at `sentAt`, or null for one with nothing to judge (or from nobody
    * in a match). */
  def of(loop: Loopback, c: LoopClient, p: Packet, sentAt: Long): Judge = {
    val gi = loop.matchOf(c)
    val me = if (gi == null) null else gi.registry.get(c.id)
    if (me == null) return null
    p match {
      case u: PlayerUpdatePacket => new Step(loop, c, gi, me, u)
      case s: ProjectilePacket if s.getAction == ProjectileAction.SPAWN =>
        // When its cast's first request reached the server: a cast's requests are sent together, and
        // only a stall of the whole process (the test's) spreads them out
        val first = c.castDelivery.get(s.getAttackSlot) match {
          case Some((sent, at)) if Math.abs(sentAt - sent) <= 5 => at
          case _ =>
            val now = System.currentTimeMillis()
            c.castDelivery(s.getAttackSlot) = (sentAt, now)
            now
        }
        new Shot(loop, c, gi, me, s, first)
      case t: TrapPacket if t.getAction == TrapAction.PLACE => new Throw(loop, c, gi, me, t, sentAt)
      case i: ItemPacket if i.getAction == ItemAction.USE => new Use(loop, c, gi, me, i)
      case r: CooldownResetPacket if r.getAction == CooldownResetAction.REQUEST => new Reset(loop, c, gi, me)
      case _ => null
    }
  }

  private def slotName(slot: Int): String = slot match {
    case AttackSlot.PRIMARY => "primary"
    case AttackSlot.Q => "Q"
    case AttackSlot.E => "E"
    case AttackSlot.BURST => "burst"
    case other => s"slot $other"
  }

  /** A position update: a step (or a dash's, a blink's), and with it a phase and a barrier. */
  private final class Step(loop: Loopback, c: LoopClient, gi: GameInstance, me: Player, u: PlayerUpdatePacket)
      extends Judge(loop, c, gi, me) {
    private val from = me.getPosition
    private val moves = me.getServerMoves
    private val view = serverView
    // A phase or a barrier this update puts up moves these, or finds them up already. Asked after, a
    // dash's phase (the Revenant's lasts 150ms) could be over again if the test stalled in between
    private val judgedAt = System.currentTimeMillis()
    private val phasedUntil = me.getPhasedUntil
    private val barrierRaisedAt = me.getBarrierRaisedAt
    private val barrierUp = me.hasBarrier

    def verdict(): Unit = {
      val claim = u.getPosition
      if (claim != from) {
        if (me.getPosition == claim) loop.count("steps taken")
        else if (me.getServerMoves != moves) loop.count("steps overtaken by a server move")
        else if (worldChangedUnder(claim)) {
          // A fence put up by someone acting in the same moment, which the client hadn't heard of:
          // it is a step off the server until the server corrects it (Loopback.checkAgreement)
          loop.count("steps refused for a fence put up as they were taken")
          loop.forgive(s"Player $named moved to non-walkable tile")
          c.racedUntil = System.currentTimeMillis() + Judge.RaceSettleMs
        } else {
          val world = gi.world
          val cells = Math.max(Math.abs(claim.getX - from.getX), Math.abs(claim.getY - from.getY))
          loop.problem(s"${c.name}'s step from $from to $claim ($cells cells, onto ${world.getTile(claim.getX, claim.getY).name}) " +
            s"was refused; it counted ${u.getServerMoves} server moves to the server's $moves, and $view${c.recent}")
        }
      }
      // A phase or a barrier going up with this update, which the server must have taken
      val phased = (u.getEffectFlags & 0x08) != 0
      if (phased && !c.saidPhased) {
        if (me.getPhasedUntil != phasedUntil || phasedUntil > judgedAt) loop.count("phases taken")
        else loop.problem(s"${c.name}'s phase was refused: $view${c.recent}")
      }
      val barrier = (u.getEffectFlags2 & 0x04) != 0
      if (barrier && !c.saidBarrier) {
        if (me.getBarrierRaisedAt != barrierRaisedAt || barrierUp) loop.count("barriers taken")
        else loop.problem(s"${c.name}'s barrier was refused: $view${c.recent}")
      }
      c.saidPhased = phased
      c.saidBarrier = barrier
    }

    /** Can a cell this step depends on — where it lands, and a diagonal's two corners — be stood on in
      * the client's world but not in the server's? The client stepped knowing nothing of it. */
    private def worldChangedUnder(claim: Position): Boolean = {
      val (dx, dy) = (claim.getX - from.getX, claim.getY - from.getY)
      val cells = Seq((claim.getX, claim.getY)) ++
        (if (Math.abs(dx) == 1 && Math.abs(dy) == 1) Seq((from.getX + dx, from.getY), (from.getX, from.getY + dy)) else Nil)
      val mine = c.client.getWorld
      cells.exists { case (x, y) => mine.isWalkable(x, y) && !gi.world.isWalkable(x, y) }
    }
  }

  /** A spawn request: one projectile of an attack, which the server sends everyone as it spawns it.
    * A cast's projectiles have PacketValidator.castWindowMs to arrive: ones the test's own stall
    * spread out further than that are counted, not failed. */
  private final class Shot(loop: Loopback, c: LoopClient, gi: GameInstance, me: Player, s: ProjectilePacket,
                           castFirstAt: Long) extends Judge(loop, c, gi, me) {
    private var taken = false
    private val view = serverView

    override def saw(to: LoopClient, p: Packet): Unit = p match {
      case pp: ProjectilePacket if pp.getAction == ProjectileAction.SPAWN && c.id.equals(pp.getPlayerId) &&
        pp.getProjectileType == s.getProjectileType => taken = true
      case _ =>
    }

    private def cooldownOf(slot: Int): Long = {
      val d = CharacterDef.get(me.getCharacterId)
      slot match {
        case AttackSlot.Q => d.qAbility.cooldownMs
        case AttackSlot.E => d.eAbility.cooldownMs
        case AttackSlot.BURST => com.gridgame.common.Constants.BURST_SHOT_COOLDOWN_MS
        case _ => com.gridgame.common.Constants.SHOOT_COOLDOWN_MS
      }
    }

    def verdict(): Unit =
      if (taken) loop.count(s"${slotName(s.getAttackSlot)} shots taken")
      // Timed after the server judged it, so never shorter than the spread the server saw
      else if (System.currentTimeMillis() - castFirstAt > PacketValidator.castWindowMs(cooldownOf(s.getAttackSlot))) {
        loop.count("shots refused when a stall spread their cast")
        loop.forgive(s"Player $named attack ${s.getAttackSlot} fired too fast")
      }
      else loop.problem(s"${c.name}'s ${slotName(s.getAttackSlot)} shot (type ${s.getProjectileType}, charge ${s.getChargeLevel}, " +
        f"from (${s.getX}%.1f,${s.getY}%.1f) toward (${s.getDx}%.2f,${s.getDy}%.2f)) was refused: $view${c.recent}")
  }

  /** A trap thrown: down for everyone to hear of, or refused (TrapAction.REJECTED, which the server also
    * prints). One refused because someone else's landed there after it was thrown is a race the
    * thrower couldn't have seen, and counted. */
  private final class Throw(loop: Loopback, c: LoopClient, gi: GameInstance, me: Player, t: TrapPacket,
                            sentAt: Long) extends Judge(loop, c, gi, me) {
    private var taken = false
    private val view = serverView

    override def saw(to: LoopClient, p: Packet): Unit = p match {
      case tp: TrapPacket if tp.getAction == TrapAction.SPAWN && c.id.equals(tp.getPlayerId) => taken = true
      case _ =>
    }

    def verdict(): Unit = {
      val there = gi.trapManager.trapAt(t.getX, t.getY)
      if (taken) loop.count("traps taken")
      else if (there != null && !c.id.equals(there.ownerId) && there.placedAt >= sentAt) {
        loop.count("traps refused for one thrown there first")
        loop.forgive(s"Trap refused for $named")
      }
      else loop.problem(s"${c.name}'s trap ${t.getTrapType} at (${t.getX},${t.getY}) was refused: $view${c.recent}")
    }
  }

  /** An item used: a star refused comes back as USE_REJECTED. A fence the server won't put where it
    * was aimed is handed back (ItemAction.INVENTORY): the client lets the server judge where a fence
    * may go, so that is counted, not failed. */
  private final class Use(loop: Loopback, c: LoopClient, gi: GameInstance, me: Player, i: ItemPacket)
      extends Judge(loop, c, gi, me) {
    private var refused = false
    private var handedBack = false
    private val view = serverView

    override def saw(to: LoopClient, p: Packet): Unit = p match {
      case ip: ItemPacket if (to eq c) && ip.getItemId == i.getItemId && ip.getAction == ItemAction.USE_REJECTED => refused = true
      case ip: ItemPacket if (to eq c) && ip.getItemId == i.getItemId && ip.getAction == ItemAction.INVENTORY => handedBack = true
      case _ =>
    }

    def verdict(): Unit = {
      val kind = i.getItemType.name
      if (refused) loop.problem(s"${c.name}'s $kind (to (${i.getX},${i.getY})) was refused: $view${c.recent}")
      else if (handedBack) loop.count(s"${kind}s handed back")
      else loop.count(s"${kind}s used")
    }
  }

  /** R: refused, the server says so (CooldownResetAction.REJECTED, and prints it). */
  private final class Reset(loop: Loopback, c: LoopClient, gi: GameInstance, me: Player) extends Judge(loop, c, gi, me) {
    private var refused = false
    private val view = serverView

    override def saw(to: LoopClient, p: Packet): Unit = p match {
      case r: CooldownResetPacket if (to eq c) && r.getAction == CooldownResetAction.REJECTED => refused = true
      case _ =>
    }

    def verdict(): Unit = if (refused) loop.problem(s"${c.name}'s R was refused: $view${c.recent}") else loop.count("R taken")
  }
}
