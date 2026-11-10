package com.gridgame.server

import com.gridgame.common.GameClock
import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

import scala.collection.mutable

/**
 * Every projectile the roster throws, held to what its definition says of it (ProjectileDef): the
 * damage of a hit, each effect a hit lands and for how long, what a killing blow scores and leaves
 * alone, how far a blast and a splash reach and what they take at every distance, and what
 * everyone is told of each. The suites beside this one pin a rule with an attack or two of the
 * roster's, or with one made for the test; nothing held the other hundred and ninety to their
 * numbers, so the line that works out a blast's falloff could be put wrong, or the packets a
 * killing blow sends left out, and no test failed (src/test/CLAUDE.md, *Mutation testing*).
 *
 * What is expected of each attack is worked out here from its definition by the rules as
 * design/combat.md states them, not asked of the code that applies them. All of it on a clock
 * that stands still (SimKit), so a hold just landed has exactly its whole time left.
 */
class AttackSweepTest {
  private val Full = CharacterDef.get(CharacterId.Golem.id).maxHealth

  /** An open lane: the shooter at (20, 30) throwing east, whoever a test stands about it, and an
    * onlooker far out of reach of everything, who hears what everyone is told. */
  private final class Lane(attack: RosterAttack, clock: GameClock.Manual) {
    val m = new SimMatch(clock, WorldData.createEmpty(100, 60), respawns = false)
    val shooter: Player = m.join(attack.character.id, 20, 30)
    val onlooker: Player = m.join(CharacterId.Golem, 95, 5)
    val d: ProjectileDef = attack.defn
    var heard: Seq[Packet] = Nil

    def stand(x: Int, y: Int): Player = m.join(CharacterId.Golem, x, y)

    /** Throw it (a slam where they stand, anything else east) and fly it out. */
    def fire(): Unit = {
      m.clearSent()
      val shot = if (attack.slam) m.launch(shooter, attack.projectileType, 0f, 0f) else m.launch(shooter, attack.projectileType, 1f, 0f)
      m.tickUntilGone(shot)
      heard = m.sent(onlooker)
    }

    def shots(action: Byte): Seq[ProjectilePacket] = m.projectileEvents(heard, action)
    def hitsOn(p: Player): Seq[ProjectilePacket] =
      heard.collect { case pp: ProjectilePacket if (pp.getAction == ProjectileAction.HIT || pp.getAction == ProjectileAction.PIERCE) && p.getId.equals(pp.getTargetId) => pp }
    def lastWordOn(p: Player): PlayerUpdatePacket = m.updatesAbout(heard, p).lastOption.orNull
    def kills: Seq[GameEventPacket] = heard.collect { case g: GameEventPacket if g.getEventType == GameEvent.KILL => g }
  }

  private def sweep(attacks: Seq[RosterAttack])(body: (RosterAttack, GameClock.Manual) => Unit): Unit =
    ServerTestKit.quietly(for (a <- attacks) Sim() { clock => body(a, clock) })

  /** What a blast takes at this distance from its centre: its centre's damage falling evenly to
    * its edge's at its radius, rounded down. */
  private def blast(x: ExplosionConfig, distance: Float): Int =
    (x.centerDamage - (distance / x.blastRadius) * (x.centerDamage - x.edgeDamage)).toInt

  private def distance(x: Float, y: Float, p: Player): Float = distance(x, y, (p.getPosition.getX, p.getPosition.getY))

  private def distance(x: Float, y: Float, cell: (Int, Int)): Float = {
    val (dx, dy) = (x - cell._1, y - cell._2)
    Math.sqrt(dx * dx + dy * dy).toFloat
  }

  /** How far down the lane a target stands: within reach of even the shortest strike. */
  private def near(d: ProjectileDef): Int = if (d.maxRange >= 5) 4 else Math.max(1, d.maxRange - 1)

  private val flying: Seq[RosterAttack] = RosterAttack.all.filter(a => !a.slam && a.defn.speedMultiplier > 0f)
  private val striking: Seq[RosterAttack] = flying.filter(a => !a.defn.passesThroughPlayers)
  // A boomerang strikes whoever it flies through twice, out and back: it has a case of its own
  private val once: Seq[RosterAttack] = striking.filterNot(_.defn.boomerang)

  // --- A hit ---

  /** What a fresh target is under once a hit's effects have landed in their order, by the rules of
    * holds: a freeze, a stun and a launch are one hold, refused while held or immune and leaving
    * the target immune for 1.5s after it; a root is refused while immune and does the same; a slow
    * is refused while immune. */
  private final class Expected(effects: Seq[OnHitEffect]) {
    var heldMs = 0; var stunned = false; var airborne = false
    var rootedMs = 0; var slowedMs = 0; var slowTo = 1f
    var immune = false
    var burn: Option[Burn] = None; var poison: Option[Poison] = None
    var holds = 0
    effects.foreach {
      case Freeze(ms) => if (heldMs == 0 && !immune) { heldMs = ms; immune = true; holds += 1 }
      case Stun(ms) => if (heldMs == 0 && !immune) { heldMs = ms; stunned = true; immune = true; holds += 1 }
      case Launch(ms) => if (heldMs == 0 && !immune) { heldMs = ms; stunned = true; airborne = true; immune = true; holds += 1 }
      case TeleportOwnerBehind(_, ms) => if (heldMs == 0 && !immune) { heldMs = ms; immune = true; holds += 1 }
      case Root(ms) => if (!immune) { rootedMs = ms; immune = true; holds += 1 }
      case Slow(ms, to) => if (!immune) { slowedMs = ms; slowTo = to }
      case b: Burn => burn = Some(b)
      case p: Poison => poison = Some(p)
      case _ =>
    }
  }

  private def checkEffects(what: String, lane: Lane, target: Player, e: Expected, now: Long): Unit = {
    assertEquals(s"$what: held", e.heldMs.toLong, Math.max(0L, target.getFrozenUntil - now))
    assertEquals(s"$what: stunned", e.stunned, target.isStunned)
    assertEquals(s"$what: in the air", e.airborne, target.isAirborne)
    assertEquals(s"$what: rooted", e.rootedMs.toLong, Math.max(0L, target.getRootedUntil - now))
    assertEquals(s"$what: slowed", e.slowedMs.toLong, Math.max(0L, target.getSlowedUntil - now))
    if (e.slowedMs > 0) assertEquals(s"$what: slowed to", e.slowTo, target.getSlowMultiplier, 0f)
    assertEquals(s"$what: burning", e.burn.isDefined, target.isBurning)
    for (b <- e.burn) {
      assertEquals(s"$what: burning for", b.durationMs.toLong, target.getBurnUntil - now)
      assertEquals(s"$what: a bite of the burn", b.totalDamage / (b.durationMs / b.tickMs), target.getBurnDamagePerTick)
      assertEquals(s"$what: whose burn", lane.shooter.getId, target.getBurnOwnerId)
    }
    assertEquals(s"$what: poisoned", e.poison.isDefined, target.isPoisoned)
    for (p <- e.poison) {
      assertEquals(s"$what: a tick of the poison", p.totalDamage / (p.durationMs / p.tickMs), target.getPoisonDamagePerTick)
      assertEquals(s"$what: whose poison", lane.shooter.getId, target.getPoisonOwnerId)
    }
    // And everyone is told as much: the last word on the target carries all of it
    val word = lane.lastWordOn(target)
    assertNotNull(s"$what: everyone is told how the target stands", word)
    assertEquals(s"$what: the health everyone is told", target.getHealth, word.getHealth)
    assertEquals(s"$what: the hold everyone is told of", PlayerUpdatePacket.encodeEffectMs(e.heldMs), PlayerUpdatePacket.encodeEffectMs(word.getHeldMs))
    assertEquals(s"$what: the root everyone is told of", PlayerUpdatePacket.encodeEffectMs(e.rootedMs), PlayerUpdatePacket.encodeEffectMs(word.getRootedMs))
    assertEquals(s"$what: the slow everyone is told of", PlayerUpdatePacket.encodeEffectMs(e.slowedMs), PlayerUpdatePacket.encodeEffectMs(word.getSlowedMs))
    assertEquals(s"$what: frozen, as everyone is told", e.heldMs > 0, (word.getEffectFlags & 0x04) != 0)
    assertEquals(s"$what: burning, as everyone is told", e.burn.isDefined, (word.getEffectFlags & 0x10) != 0)
    assertEquals(s"$what: rooted, as everyone is told", e.rootedMs > 0, (word.getEffectFlags & 0x40) != 0)
    assertEquals(s"$what: slowed, as everyone is told", e.slowedMs > 0, (word.getEffectFlags & 0x80) != 0)
    assertEquals(s"$what: stunned, as everyone is told", e.stunned, (word.getEffectFlags2 & 0x01) != 0)
    assertEquals(s"$what: poisoned, as everyone is told", e.poison.isDefined, (word.getEffectFlags2 & 0x02) != 0)
    assertEquals(s"$what: in the air, as everyone is told", e.airborne, (word.getEffectFlags2 & 0x08) != 0)
    if (e.slowedMs > 0) assertEquals(s"$what: how slow, as everyone is told", Math.round(e.slowTo * 100f), word.getSlowPercent)
  }

  @Test def everyAttackThatStrikesDealsItsDamageAndLandsItsEffects(): Unit = {
    assertTrue(s"attacks that fly and strike (${once.size})", once.size > 120)
    sweep(once) { (a, clock) =>
      val lane = new Lane(a, clock)
      import lane._
      val what = a.name
      val target = stand(20 + near(d), 30)
      shooter.setHealth(shooter.getMaxHealth - 40)
      val shooterBefore = shooter.getHealth
      val now = clock.millis()
      fire()

      val struck = hitsOn(target)
      assertEquals(s"$what: one hit on the target, told to everyone", 1, struck.size)
      val (hx, hy) = (struck.head.getX, struck.head.getY)
      val dealt = Full - target.getHealth
      val expectedDamage =
        if (d.explodesOnPlayerHit) {
          // It goes off where it struck: the blast's damage at the target's distance from there
          val went = shots(ProjectileAction.DESPAWN)
          assertEquals(s"$what: goes off once", 1, went.size)
          blast(d.explosionConfig.get, distance(went.head.getX, went.head.getY, target))
        } else d.distanceDamageScaling match {
          // Harder the further it has flown from the cell beside the shooter where it set out. One
          // that flies on is told of where its tick left it, a step or two past where it struck:
          // what it dealt is what it would have somewhere along that tick
          case Some(s) =>
            def at(flown: Float): Int = (s.baseDamage + Math.min(1.0f, Math.max(0f, flown) / s.maxRange.toFloat) * (s.maxDamage - s.baseDamage)).toInt
            assertTrue(s"$what: harder the further it has flown ($dealt after ${hx - 21f} cells)",
              dealt >= at(hx - 21f - 2 * d.speedMultiplier) && dealt <= at(hx - 21f))
            dealt
          case None => d.damage
        }
      assertEquals(s"$what: the damage of a hit", expectedDamage, dealt)
      assertEquals(s"$what: flies on only if it pierces", if (d.pierceCount >= 1 && !d.explodesOnPlayerHit) ProjectileAction.PIERCE else ProjectileAction.HIT, struck.head.getAction)

      // An explosion deals its blast and nothing else; any other hit lands its effects, in order
      val effects = if (d.explodesOnPlayerHit) Nil else d.onHitEffects
      val e = new Expected(effects)
      checkEffects(what, lane, target, e, now)

      // Where it leaves the target: pulled to the shooter, pushed straight on down the lane, drawn
      // a cell in toward a vortex, or where they stood
      val stoodAt = 20 + near(d)
      val vortex = effects.collectFirst { case v: VortexPull => v }
      val expectedX =
        if (effects.contains(PullToOwner)) 20
        else effects.collectFirst { case Push(by) => stoodAt + by.toInt }.getOrElse {
          vortex match {
            // In toward where it struck, by as many whole cells as it is off or the pull is strong
            case Some(v) if distance(hx, hy, target) > 0.1f || target.getPosition.getX != stoodAt => stoodAt - Math.min(v.pullStrength, stoodAt - hx).toInt
            case _ => stoodAt
          }
        }
      assertEquals(s"$what: where it leaves the target", (expectedX, 30), m.at(target))
      val moved = if (expectedX != stoodAt) 1 else 0
      val knocked = if (expectedX != stoodAt && vortex.isEmpty) 1 else 0
      assertEquals(s"$what: counted as a knockback", knocked, target.getKnockbacks)
      assertEquals(s"$what: every hold and every move is a server move", e.holds + moved, target.getServerMoves)
      if (e.holds + moved > 0) {
        val told = m.updatesAbout(m.tcpSent(target), target)
        assertTrue(s"$what: the target is told where the server has it, over TCP", told.nonEmpty)
        assertEquals(s"$what: with the count of its moves", target.getServerMoves, told.last.getServerMoves)
        assertEquals(s"$what: and where", new Position(expectedX, 30), told.last.getPosition)
      }

      // What comes back to the shooter
      val healed = effects.collectFirst { case LifeSteal(share) => expectedDamage * share / 100 }.getOrElse(0)
      assertEquals(s"$what: the shooter's health", Math.min(shooter.getMaxHealth, shooterBefore + healed), shooter.getHealth)
      val boost = effects.collectFirst { case SpeedBoost(ms) => ms }.getOrElse(0)
      assertEquals(s"$what: the shooter's haste", boost.toLong, Math.max(0L, shooter.getSpeedBoostUntil - now))
      effects.collectFirst { case TeleportOwnerBehind(by, _) => by }.foreach { by =>
        // The target faces down the screen as it joins: behind it is up
        assertEquals(s"$what: the shooter lands behind the target", (stoodAt, 30 - by), m.at(shooter))
        assertEquals(s"$what: and everyone is told where", new Position(stoodAt, 30 - by), lastWordOn(shooter).getPosition)
      }
    }
  }

  @Test def everyEffectAnAttackListsIsOneItCanLand(): Unit = {
    // An effect its own attack's earlier ones always shut out (a slow after a hold, which the hold's
    // immunity refuses) would be a promise the attack never keeps
    for (a <- RosterAttack.all) {
      val d = a.defn
      val e = new Expected(d.onHitEffects)
      val holds = d.onHitEffects.count { case _: Freeze | _: Stun | _: Launch | _: Root | _: TeleportOwnerBehind => true; case _ => false }
      val slows = d.onHitEffects.count(_.isInstanceOf[Slow])
      assertEquals(s"${a.name}: every hold it lists lands on a fresh target", holds, e.holds)
      assertEquals(s"${a.name}: its slow lands on a fresh target", slows > 0, e.slowedMs > 0)
    }
  }

  // --- A killing blow ---

  @Test def aKillingBlowIsScoredOnceAndLandsNothingOnTheDead(): Unit =
    sweep(once.filter(a => a.defn.damage > 0 || a.defn.explodesOnPlayerHit)) { (a, clock) =>
      val lane = new Lane(a, clock)
      import lane._
      val what = a.name
      val target = stand(20 + near(d), 30)
      target.setHealth(1)
      shooter.setHealth(shooter.getMaxHealth - 40)
      val shooterBefore = shooter.getHealth
      fire()

      assertTrue(s"$what: kills", target.isDead)
      assertEquals(s"$what: the blow that killed is told to everyone", 1, hitsOn(target).size)
      val word = lastWordOn(target)
      assertNotNull(s"$what: everyone is told the target is down", word)
      assertEquals(s"$what: with no health", 0, word.getHealth)
      assertEquals(s"$what: one kill", 1, kills.size)
      assertEquals(s"$what: the shooter's", shooter.getId, kills.head.getPlayerId)
      assertEquals(s"$what: of the target", target.getId, kills.head.getTargetId)
      assertEquals(s"$what: their first", (1, 0), (kills.head.getKills.toInt, kills.head.getDeaths.toInt))
      assertEquals(1, m.instance.killTracker.getKills(shooter.getId))
      assertEquals(1, m.instance.killTracker.getDeaths(target.getId))
      // The dead are left where they fell, with nothing on them
      assertEquals(s"$what: the dead aren't moved", (20 + near(d), 30), m.at(target))
      assertFalse(s"$what: nor held", target.isFrozen || target.isRooted || target.isSlowed)
      assertFalse(s"$what: nor set alight or poisoned", target.isBurning || target.isPoisoned)
      assertEquals(s"$what: nor counted as moved", 0, target.getServerMoves)
      // A life steal still drinks from the blow that killed
      if (!d.explodesOnPlayerHit) {
        val dealt = d.distanceDamageScaling match {
          case Some(s) => (s.baseDamage + Math.min(1.0f, (hitsOn(target).head.getX - 21f) / s.maxRange.toFloat) * (s.maxDamage - s.baseDamage)).toInt
          case None => d.damage
        }
        val healed = d.onHitEffects.collectFirst { case LifeSteal(share) => dealt * share / 100 }.getOrElse(0)
        assertEquals(s"$what: the shooter's health", Math.min(shooter.getMaxHealth, shooterBefore + healed), shooter.getHealth)
      }
    }

  // --- A blast ---

  /** Where the attack goes off with nobody in its way: where it is thrown down, or at the end of
    * its flight. */
  private def goesOffAt(a: RosterAttack): (Float, Float) = Sim() { clock =>
    val lane = new Lane(a, clock)
    lane.fire()
    val went = lane.shots(ProjectileAction.DESPAWN)
    assertEquals(s"${a.name}: ends once", 1, went.size)
    (went.head.getX, went.head.getY)
  }

  /** Cells round a point to stand victims on: across the lane from it and past its end, from
    * beside it to beyond `reach`, none near enough the lane to be struck on the way. */
  private def about(x: Float, y: Float, reach: Float, clearOfLane: Float): Seq[(Int, Int)] = {
    val (cx, cy) = (Math.round(x), Math.round(y))
    val off = Math.ceil(clearOfLane + 0.7).toInt
    val far = Math.ceil(reach).toInt + 1
    ((off to far).flatMap(k => Seq((cx, cy + k), (cx, cy - k), (cx - 1, cy + k), (cx + 1, cy - k))) ++
      (off to far).map(k => (cx + k, cy))).distinct
  }

  @Test def everyBlastTakesWhatItsFalloffSaysOfEveryoneWithinItsReach(): Unit = {
    val blasts = RosterAttack.all.filter(a => a.defn.explosionConfig.exists(x => x.centerDamage > 0 || x.edgeDamage > 0) && (a.slam || a.defn.speedMultiplier > 0f))
    assertTrue(s"attacks that blast (${blasts.size})", blasts.size >= 8)
    var hurt = 0
    var spared = 0
    ServerTestKit.quietly(for (a <- blasts) {
      val (ex, ey) = goesOffAt(a)
      Sim() { clock =>
        val lane = new Lane(a, clock)
        import lane._
        val x = d.explosionConfig.get
        val what = s"${a.name}, going off at ($ex, $ey)"
        // A slam is thrown down on its caster, who is in nobody's way; a shot's victims stand clear
        // of its lane, so none of them is struck on its way to where it goes off
        val clear = if (a.slam || d.passesThroughPlayers) 0f else d.hitRadius
        val victims = about(ex, ey, x.blastRadius, clear).filter(c => c != ((20, 30))).map { case (vx, vy) => ((vx, vy), stand(vx, vy)) }
        fire()
        assertEquals(s"$what: goes off where it did with nobody about", (ex, ey), (shots(ProjectileAction.DESPAWN).head.getX, shots(ProjectileAction.DESPAWN).head.getY))
        // What its splash takes besides, of those the splash reaches (a slam's is its damage). The
        // splash comes first: where it moves its victims, the blast is measured from where it left them
        val splash = d.aoeOnMaxRange
        val moves = splash.isDefined && d.onHitEffects.exists { case _: Push | PullToOwner | _: VortexPull => true; case _ => false }
        for ((cell, v) <- victims if !moves) {
          val far = distance(ex, ey, cell)
          val fromBlast = if (far <= x.blastRadius) blast(x, far) else 0
          val fromSplash = splash.filter(s => far <= s.radius).map(_.damage).getOrElse(0)
          val who = s"$what: whoever stands $cell, $far from it"
          assertEquals(s"$who", Math.max(0, Full - fromBlast - fromSplash), v.getHealth)
          if (fromBlast + fromSplash > 0 && v.getHealth > 0) {
            hurt += 1
            assertTrue(s"$who is told to everyone as hit", hitsOn(v).nonEmpty)
            assertEquals(s"$who: the health everyone is told", v.getHealth, lastWordOn(v).getHealth)
          } else if (fromBlast + fromSplash == 0) {
            spared += 1
            assertTrue(s"$who is beyond it, and nobody is told otherwise", hitsOn(v).isEmpty)
          }
        }
      }
    })
    assertTrue(s"victims within reach ($hurt) and beyond it ($spared)", hurt > 50 && spared > 30)
  }

  // --- A splash ---

  @Test def everySplashTakesItsDamageOfEveryoneWithinItsReachAndLandsItsHolds(): Unit = {
    // Where a shot ends (or a slam is thrown down): everyone within the splash's reach of there,
    // the same from its middle to its edge, with the splash's own hold and the attack's effects
    val splashing = RosterAttack.all.filter(a => a.defn.aoeOnMaxRange.isDefined && (a.slam || a.defn.speedMultiplier > 0f))
    assertTrue(s"attacks that splash where they end (${splashing.size})", splashing.size >= 25)
    var caught = 0
    ServerTestKit.quietly(for (a <- splashing) {
      val (ex, ey) = goesOffAt(a)
      Sim() { clock =>
        val lane = new Lane(a, clock)
        import lane._
        val splash = d.aoeOnMaxRange.get
        val what = s"${a.name}, splashing at ($ex, $ey)"
        val clear = if (a.slam || d.passesThroughPlayers) 0f else d.hitRadius
        val blastReach = d.explosionConfig.filter(x => x.centerDamage > 0 || x.edgeDamage > 0)
        val victims = about(ex, ey, splash.radius, clear).filter(c => c != ((20, 30))).map { case (vx, vy) => ((vx, vy), stand(vx, vy)) }
        val now = clock.millis()
        fire()
        for ((stoodAt, v) <- victims) {
          val far = distance(ex, ey, stoodAt)
          val who = s"$what: whoever stood $stoodAt, $far from it"
          if (far <= splash.radius) {
            caught += 1
            val fromBlast = blastReach.filter(x => far <= x.blastRadius).map(x => blast(x, far)).getOrElse(0)
            // Pushed, pulled and drawn-in victims are somewhere else by the time a blast is measured:
            // their health is held to the splash alone where nothing moves them
            val moves = d.onHitEffects.exists { case _: Push | PullToOwner | _: VortexPull => true; case _ => false }
            if (!moves) assertEquals(s"$who: the splash's damage, and the blast's", Full - splash.damage - fromBlast, v.getHealth)
            else assertTrue(s"$who: at least the splash's damage", Full - v.getHealth >= splash.damage)
            // The splash's own hold first, then the attack's effects, as a hit's
            val own = (if (splash.freezeDurationMs > 0) Seq(Freeze(splash.freezeDurationMs)) else Nil) ++
              (if (splash.stunDurationMs > 0) Seq(Stun(splash.stunDurationMs)) else Nil) ++
              (if (splash.rootDurationMs > 0) Seq(Root(splash.rootDurationMs)) else Nil)
            val e = new Expected(own ++ d.onHitEffects)
            checkEffects(who, lane, v, e, now)
            if (!moves) {
              assertEquals(s"$who: stays put", stoodAt, m.at(v))
              assertEquals(s"$who: a hold is a server move, once", if (e.holds > 0) 1 else 0, v.getServerMoves)
            }
            assertTrue(s"$who is told to everyone as hit", hitsOn(v).nonEmpty)
          } else if (blastReach.forall(x => far > x.blastRadius)) {
            assertEquals(s"$who is beyond it", Full, v.getHealth)
            assertTrue(s"$who: and nobody is told otherwise", hitsOn(v).isEmpty)
          }
        }
      }
    })
    assertTrue(s"victims caught ($caught)", caught > 150)
  }

  @Test def aSplashOffAHitTakesItsDamageOfWhoeverStandsBesideTheOneStruck(): Unit = {
    // (Not one that flies on: it strikes the one beside next, and splashes the first from there)
    val splashing = once.filter(a => a.defn.aoeOnHit.isDefined && !a.defn.explodesOnPlayerHit && a.defn.pierceCount == 0)
    assertTrue(s"attacks that splash off a hit (${splashing.size})", splashing.size >= 3)
    sweep(splashing) { (a, clock) =>
      val lane = new Lane(a, clock)
      import lane._
      val splash = d.aoeOnHit.get
      val what = a.name
      val target = stand(20 + near(d), 30)
      val beside = stand(20 + near(d), 31)
      val beyond = stand(20 + near(d), 30 + Math.ceil(splash.radius + d.hitRadius + 1).toInt)
      val now = clock.millis()
      fire()
      val struck = hitsOn(target)
      assertEquals(s"$what: strikes the one in its way", 1, struck.size)
      val far = distance(struck.head.getX, struck.head.getY, (20 + near(d), 31))
      assertTrue(s"$what: the one beside them is within its splash ($far of ${splash.radius})", far <= splash.radius)
      val moves = d.onHitEffects.exists { case _: Push | PullToOwner | _: VortexPull => true; case _ => false }
      if (!moves) assertEquals(s"$what: the splash's damage to the one beside", Full - splash.damage, beside.getHealth)
      else assertTrue(s"$what: the splash's damage to the one beside", Full - beside.getHealth >= splash.damage)
      val own = (if (splash.freezeDurationMs > 0) Seq(Freeze(splash.freezeDurationMs)) else Nil) ++
        (if (splash.stunDurationMs > 0) Seq(Stun(splash.stunDurationMs)) else Nil) ++
        (if (splash.rootDurationMs > 0) Seq(Root(splash.rootDurationMs)) else Nil)
      checkEffects(s"$what, the one beside", lane, beside, new Expected(own ++ d.onHitEffects), now)
      assertTrue(s"$what: the one beside is told to everyone as hit", hitsOn(beside).nonEmpty)
      assertEquals(s"$what: nobody beyond its splash", Full, beyond.getHealth)
    }
  }

  // --- Flying on, and coming back ---

  @Test def aShotPassesThroughAsManyAsItPiercesAndIsUsedUpByTheNext(): Unit = {
    val piercing = once.filter(a => a.defn.pierceCount >= 1 && !a.defn.explodesOnPlayerHit && a.defn.aoeOnHit.isEmpty &&
      a.defn.maxRange >= 4 + 3 * (a.defn.pierceCount + 1) && a.defn.onHitEffects.forall { case _: Push | PullToOwner | _: VortexPull => false; case _ => true })
    assertTrue(s"attacks that pierce (${piercing.size})", piercing.size >= 8)
    sweep(piercing) { (a, clock) =>
      val lane = new Lane(a, clock)
      import lane._
      // One more in the row than it can strike
      val row = (0 to d.pierceCount + 1).map(i => stand(24 + 3 * i, 30))
      fire()
      val struck = row.map(p => hitsOn(p))
      for (i <- 0 until d.pierceCount) assertEquals(s"${a.name}: flies on through number ${i + 1}", Seq(ProjectileAction.PIERCE), struck(i).map(_.getAction))
      assertEquals(s"${a.name}: is used up by number ${d.pierceCount + 1}", Seq(ProjectileAction.HIT), struck(d.pierceCount).map(_.getAction))
      assertEquals(s"${a.name}: never reaches the one after", Full, row.last.getHealth)
      assertTrue(row.init.forall(_.getHealth < Full))
    }
  }

  @Test def aBoomerangStrikesAgainOnItsWayBack(): Unit = {
    val boomerangs = striking.filter(a => a.defn.boomerang && a.defn.pierceCount >= 1 && !a.defn.explodesOnPlayerHit &&
      a.defn.distanceDamageScaling.isEmpty && a.defn.onHitEffects.forall { case _: Push | PullToOwner | _: VortexPull | _: LifeSteal => false; case _ => true })
    assertTrue(s"boomerangs that fly on through whoever they strike (${boomerangs.size})", boomerangs.nonEmpty)
    sweep(boomerangs) { (a, clock) =>
      val lane = new Lane(a, clock)
      import lane._
      val target = stand(20 + near(d), 30)
      fire()
      assertEquals(s"${a.name}: struck on the way out and again on the way back", 2, hitsOn(target).size)
      assertEquals(s"${a.name}: twice its damage", Full - 2 * d.damage, target.getHealth)
    }
  }

  // --- Behind the target, and into a vortex ---

  @Test def anAttackThatPutsItsOwnerBehindTheTargetDoesWhicheverWayTheTargetFaces(): Unit = {
    val haunts = once.filter(_.defn.onHitEffects.exists(_.isInstanceOf[TeleportOwnerBehind]))
    assertTrue(haunts.nonEmpty)
    val behind = Seq(Direction.Up -> (0, 1), Direction.Down -> (0, -1), Direction.Left -> (1, 0), Direction.Right -> (-1, 0))
    ServerTestKit.quietly(for (a <- haunts; (facing, (bx, by)) <- behind) Sim() { clock =>
      val lane = new Lane(a, clock)
      import lane._
      val by2 = d.onHitEffects.collectFirst { case TeleportOwnerBehind(cells, _) => cells }.get
      val target = stand(24, 30)
      target.setDirection(facing)
      fire()
      val there = (24 + bx * by2, 30 + by * by2)
      assertEquals(s"${a.name}: behind a target facing $facing", there, m.at(shooter))
      assertEquals(s"${a.name}: everyone is told", new Position(there._1, there._2), lastWordOn(shooter).getPosition)
      val told = m.updatesAbout(m.tcpSent(shooter), shooter)
      assertTrue(s"${a.name}: and its owner, as a move of the server's", told.nonEmpty && told.last.getServerMoves == 1 && shooter.getServerMoves == 1)
    })
    // With no ground to land on behind them the owner stays where they are, and the target is held all the same
    ServerTestKit.quietly(for (a <- haunts) Sim() { clock =>
      val lane = new Lane(a, clock)
      import lane._
      val by2 = d.onHitEffects.collectFirst { case TeleportOwnerBehind(cells, _) => cells }.get
      val target = stand(24, 30)
      m.world.setTile(24, 30 - by2, Tile.Wall)
      fire()
      assertEquals(s"${a.name}: a wall behind the target", (20, 30), m.at(shooter))
      assertTrue(target.isFrozen)
      assertEquals(0, shooter.getServerMoves)
    })
    // And at the map's edge, as far behind them as the map goes
    ServerTestKit.quietly(for (a <- haunts) Sim() { clock =>
      val m = new SimMatch(clock, WorldData.createEmpty(100, 60), respawns = false)
      val shooter = m.join(a.character.id, 20, 1)
      val target = m.join(CharacterId.Golem, 24, 1)
      m.tickUntilGone(m.launch(shooter, a.projectileType, 1f, 0f))
      assertEquals(s"${a.name}: behind a target a cell from the map's edge", (24, 0), m.at(shooter))
      assertTrue(target.isFrozen)
    })
  }

  @Test def aVortexDrawsInItsVictimsAndNobodyElse(): Unit = {
    // Whoever is within its reach of where it struck, toward there: but for the dead, the phased,
    // its thrower and its thrower's team
    val vortices = once.filter(a => a.defn.onHitEffects.exists(_.isInstanceOf[VortexPull]) && a.defn.aoeOnHit.isEmpty && a.defn.distanceDamageScaling.isEmpty)
    assertTrue(vortices.nonEmpty)
    ServerTestKit.quietly(for (a <- vortices) Sim() { clock =>
      val m = new SimMatch(clock, WorldData.createEmpty(100, 60), gameMode = 1, respawns = false)
      val d = a.defn
      val v = d.onHitEffects.collectFirst { case p: VortexPull => p }.get
      val shooter = m.join(a.character.id, 20, 30, team = 1)
      val onlooker = m.join(CharacterId.Golem, 95, 5, team = 2)
      val target = m.join(CharacterId.Golem, 23, 30, team = 2)
      // Each three cells off where it strikes (about (22, 30)), out of the shot's own reach
      val enemy = m.join(CharacterId.Golem, 22, 33, team = 2)
      val ally = m.join(CharacterId.Golem, 23, 27, team = 1)
      val phased = m.join(CharacterId.Golem, 21, 33, team = 2)
      val dead = m.join(CharacterId.Golem, 23, 33, team = 2)
      phased.setPhasedUntil(clock.millis() + 60000)
      dead.setHealth(0)
      m.clearSent()
      m.tickUntilGone(m.launch(shooter, a.projectileType, 1f, 0f))
      val heard = m.sent(onlooker)
      assertTrue(s"${a.name}: strikes the one in its way", target.getHealth < Full)
      assertTrue(s"${a.name}: draws in an enemy within its reach (from (22,33) to ${m.at(enemy)})", m.at(enemy)._2 < 33 && m.at(enemy)._2 >= 33 - v.pullStrength.toInt)
      assertEquals(s"${a.name}: a pull is a server move", 1, enemy.getServerMoves)
      assertEquals(s"${a.name}: and everyone is told where it left them", enemy.getPosition, m.updatesAbout(heard, enemy).last.getPosition)
      assertEquals(s"${a.name}: not its thrower", (20, 30), m.at(shooter))
      assertEquals(s"${a.name}: nor their team", (23, 27), m.at(ally))
      assertEquals(s"${a.name}: nor the phased", (21, 33), m.at(phased))
      assertEquals(s"${a.name}: nor the dead", (23, 33), m.at(dead))
      assertEquals(0, ally.getServerMoves + phased.getServerMoves + dead.getServerMoves + shooter.getServerMoves)
    })
    // In a free-for-all, where nobody is on its thrower's side: still not its thrower, who stands
    // within its reach of where it struck
    ServerTestKit.quietly(for (a <- vortices) Sim() { clock =>
      val m = new SimMatch(clock, WorldData.createEmpty(100, 60), respawns = false)
      val shooter = m.join(a.character.id, 20, 30)
      val target = m.join(CharacterId.Golem, 23, 30)
      val other = m.join(CharacterId.Golem, 22, 33)
      m.tickUntilGone(m.launch(shooter, a.projectileType, 1f, 0f))
      assertTrue(target.getHealth < Full)
      assertTrue(s"${a.name}: draws in whoever else is within its reach", m.at(other)._2 < 33)
      assertEquals(s"${a.name}: but never its thrower", ((20, 30), 0), (m.at(shooter), shooter.getServerMoves))
    })
  }

  @Test def aSplashsKillingBlowIsToldAndScoredAsAHitsIs(): Unit = {
    // A slam that kills whoever it catches: the blow is told to everyone, the dead seen with no
    // health, the kill its caster's, once, and a life steal still drinks from it
    val slams = RosterAttack.all.filter(a => a.slam && a.defn.aoeOnMaxRange.exists(s => s.damage > 0 && s.radius >= 2.5f) &&
      a.defn.explosionConfig.forall(x => x.centerDamage == 0 && x.edgeDamage == 0))
    assertTrue(s"slams that splash (${slams.size})", slams.size >= 5)
    sweep(slams) { (a, clock) =>
      val lane = new Lane(a, clock)
      import lane._
      val what = a.name
      val splash = d.aoeOnMaxRange.get
      val target = stand(22, 30)
      target.setHealth(1)
      shooter.setHealth(shooter.getMaxHealth - 40)
      val before = shooter.getHealth
      fire()
      assertTrue(s"$what: kills", target.isDead)
      assertEquals(s"$what: the blow that killed is told to everyone", 1, hitsOn(target).size)
      assertEquals(s"$what: everyone is told the target is down", Some(0), Option(lastWordOn(target)).map(_.getHealth))
      assertEquals(s"$what: one kill, its caster's, of the target", Seq((shooter.getId, target.getId, 1)),
        kills.map(k => (k.getPlayerId, k.getTargetId, k.getKills.toInt)))
      assertEquals((1, 1), (m.instance.killTracker.getKills(shooter.getId), m.instance.killTracker.getDeaths(target.getId)))
      assertFalse(s"$what: the dead are held by nothing", target.isFrozen || target.isRooted || target.isSlowed || target.isBurning)
      assertEquals(s"$what: nor moved", ((22, 30), 0), (m.at(target), target.getServerMoves))
      d.onHitEffects.collectFirst { case l: LifeSteal => l }.foreach { steal =>
        assertEquals(s"$what: heals its caster ${steal.healPercent}% of what the splash took", before + splash.damage * steal.healPercent / 100, shooter.getHealth)
      }
    }
  }

  @Test def aSlamThatHastesItsCasterIsSeenByEveryoneAsItIsCast(): Unit = {
    // The caster walks the haste by what they are told over TCP; everyone else must see it too
    val hastes = RosterAttack.all.filter(a => a.slam && a.defn.hastesCaster)
    assertTrue(hastes.size >= 2)
    sweep(hastes) { (a, clock) =>
      val lane = new Lane(a, clock)
      import lane._
      val boost = d.onHitEffects.collectFirst { case b: SpeedBoost => b }.get
      m.clearSent()
      m.launch(shooter, a.projectileType, 0f, 0f)
      assertEquals(s"${a.name}: hasted for its time from the cast", boost.durationMs.toLong, shooter.getSpeedBoostUntil - clock.millis())
      val seen = m.updatesAbout(m.sent(onlooker), shooter)
      assertTrue(s"${a.name}: everyone sees it", seen.exists(u => (u.getEffectFlags & 0x20) != 0 && u.speedBoostMs == boost.durationMs))
      assertTrue(s"${a.name}: and its caster is told over TCP", m.updatesAbout(m.tcpSent(shooter), shooter).exists(u => (u.getEffectFlags & 0x20) != 0))
    }
  }

  @Test def whatAHitGivesItsThrowerIsToldToEveryoneAndTheDeadAreGivenNothing(): Unit = {
    // A life steal's health and a haste on the hit land on whoever threw the shot: everyone sees
    // them at once (and a hasted thrower is told over TCP, since they walk by it). A shot can
    // outlive its thrower: it still strikes, but heals nobody back to life and speeds up no body
    val giving = once.filter(a => a.defn.damage > 0 && a.defn.aoeOnHit.isEmpty && !a.defn.explodesOnPlayerHit &&
      a.defn.distanceDamageScaling.isEmpty && a.defn.onHitEffects.exists { case _: LifeSteal | _: SpeedBoost => true; case _ => false })
    assertTrue(s"shots that give their thrower something (${giving.size})", giving.size >= 4)
    sweep(giving) { (a, clock) =>
      val steal = a.defn.onHitEffects.collectFirst { case l: LifeSteal => l }
      val haste = a.defn.onHitEffects.collectFirst { case b: SpeedBoost => b }
      val living = new Lane(a, clock)
      val target = living.stand(20 + near(living.d), 30)
      living.shooter.setHealth(living.shooter.getMaxHealth - 40)
      val before = living.shooter.getHealth
      living.fire()
      assertTrue(s"${a.name}: strikes", target.getHealth < Full)
      val told = living.lastWordOn(living.shooter)
      assertNotNull(s"${a.name}: everyone hears of its thrower", told)
      for (l <- steal) {
        assertEquals(s"${a.name}: heals ${l.healPercent}% of what it took", before + living.d.damage * l.healPercent / 100, living.shooter.getHealth)
        assertEquals(s"${a.name}: and everyone is told", living.shooter.getHealth, told.getHealth)
      }
      for (b <- haste) {
        assertTrue(s"${a.name}: its thrower hasted", living.shooter.hasSpeedBoost)
        assertTrue(s"${a.name}: seen hasted by everyone", (told.getEffectFlags & 0x20) != 0 && told.getSpeedBoostMs > b.durationMs - 100)
        assertTrue(s"${a.name}: and told so over TCP",
          living.m.updatesAbout(living.m.tcpSent(living.shooter), living.shooter).exists(u => (u.getEffectFlags & 0x20) != 0))
      }

      val dead = new Lane(a, clock)
      val struck = dead.stand(20 + near(dead.d), 30)
      dead.m.clearSent()
      val shot = dead.m.launch(dead.shooter, a.projectileType, 1f, 0f)
      dead.shooter.setHealth(0)
      dead.m.tickUntilGone(shot)
      assertTrue(s"${a.name}: its thrower dead, it still strikes", struck.getHealth < Full)
      assertEquals(s"${a.name}: and brings nobody back", (0, true), (dead.shooter.getHealth, dead.shooter.isDead))
      assertFalse(s"${a.name}: nor speeds a body up", dead.shooter.hasSpeedBoost)
    }
  }

  // --- What grows with the charge, and with the distance ---

  /** A charged shot's figure: its least at no charge at all... which is the shot's own plain one,
    * and from the first point of charge up, evenly from the scaling's least to its most at 100. */
  private def charged(plain: Float, scaling: Option[ChargeScaling], charge: Int): Float = scaling match {
    case Some(s) if charge > 0 => s.min + charge / 100.0f * (s.max - s.min)
    case _ => plain
  }

  @Test def aChargeMakesAShotAsQuickAsStrongAndAsLongAsItsScalingSays(): Unit = {
    val charging = RosterAttack.all.filter(a => a.defn.chargeSpeedScaling.isDefined || a.defn.chargeDamageScaling.isDefined ||
      a.defn.chargeRangeScaling.isDefined)
    assertTrue("the roster's charged shots", charging.size >= 3)
    ServerTestKit.quietly(for (a <- charging; charge <- Seq(0, 1, 25, 50, 99, 100)) Sim() { clock =>
      val d = a.defn
      val what = s"${a.name} at a charge of $charge"
      val pace = charged(d.speedMultiplier, d.chargeSpeedScaling, charge)
      val reach = charged(d.maxRange.toFloat, d.chargeRangeScaling, charge)
      // Its pace and its reach: flown out over open ground
      val open = new SimMatch(clock, WorldData.createEmpty(100, 60), respawns = false)
      val thrower = open.join(a.character.id, 20, 30)
      val shot = open.instance.projectileManager.spawnProjectile(thrower.getId, 20, 30, 1f, 0f, 0, charge, a.projectileType)
      open.tick()
      val first = shot.getX
      open.tick()
      if (open.instance.projectileManager.getProjectile(shot.id) != null)
        assertEquals(s"$what: cells a tick", pace, shot.getX - first, 1e-4f)
      open.tickUntilGone(shot)
      assertTrue(s"$what: flies its $reach cells and is gone within a step of them, at ${shot.getDistanceTraveled}",
        shot.getDistanceTraveled >= reach - 1e-3f && shot.getDistanceTraveled < reach + Projectile.MAX_SUB_STEP + 1e-3f)
      // Its damage: someone in its way
      if (d.distanceDamageScaling.isEmpty && !d.passesThroughPlayers) {
        val lane = new SimMatch(clock, WorldData.createEmpty(100, 60), respawns = false)
        val shooter = lane.join(a.character.id, 20, 30)
        val target = lane.join(CharacterId.Golem, 20 + near(d), 30)
        lane.tickUntilGone(lane.instance.projectileManager.spawnProjectile(shooter.getId, 20, 30, 1f, 0f, 0, charge, a.projectileType))
        assertEquals(s"$what: damage", charged(d.damage.toFloat, d.chargeDamageScaling, charge).toInt, Full - target.getHealth)
      }
    })
  }

  @Test def aShotThatGrowsWithDistanceTakesWhatItHasFlownSaysToThePoint(): Unit = {
    // From its least where it is thrown, evenly up to its most at the distance its scaling names,
    // and no more past it: by how far the shot itself had flown as it struck
    val growing = once.filter(_.defn.distanceDamageScaling.isDefined)
    assertTrue("the roster's shots that grow with distance", growing.size >= 4)
    ServerTestKit.quietly(for (a <- growing) {
      val dds = a.defn.distanceDamageScaling.get
      var least = Int.MaxValue
      var most = 0
      for (away <- 1 until a.defn.maxRange) Sim() { clock =>
        val m = new SimMatch(clock, WorldData.createEmpty(120, 60), respawns = false)
        val shooter = m.join(a.character.id, 20, 30)
        val target = m.join(CharacterId.Golem, 20 + away, 30)
        val shot = m.launch(shooter, a.projectileType, 1f, 0f)
        var ticks = 0
        while (target.getHealth == Full && m.instance.projectileManager.getProjectile(shot.id) != null && ticks < 400) { m.tick(); ticks += 1 }
        val took = Full - target.getHealth
        assertTrue(s"${a.name}: strikes someone $away cells off", took > 0)
        val flown = shot.getDistanceTraveled
        def at(distance: Float): Int =
          (dds.baseDamage + Math.min(1.0f, Math.max(0f, distance) / dds.maxRange) * (dds.maxDamage - dds.baseDamage)).toInt
        if (a.defn.pierceCount == 0) assertEquals(s"${a.name}: $away cells off, having flown $flown", at(flown), took)
        else {
          // One that flies on through them has flown on since, by the rest of that tick at most
          val before = flown - a.defn.speedMultiplier
          assertTrue(s"${a.name}: $away cells off, struck between $before and $flown cells of flight: ${at(before)} to ${at(flown)}, took $took",
            took >= at(before) && took <= at(flown))
        }
        least = Math.min(least, took)
        most = Math.max(most, took)
      }
      assertTrue(s"${a.name}: from near its least (${dds.baseDamage}) up close to near its most (${dds.maxDamage}) far off: $least to $most",
        least < dds.baseDamage + (dds.maxDamage - dds.baseDamage) / 3 && most > dds.maxDamage - (dds.maxDamage - dds.baseDamage) / 3)
    })
  }

  @Test def aPullDragsItsCatchOntoItsThrowersCellAndOntoNoCellNobodyCanStandOn(): Unit = {
    // A dasher's shot can land while they are over a pool or a wall, and a fence can go up under
    // anyone: whoever the pull catches stays where they are, rather than be put where no step of
    // their own could ever take them off again
    val pulls = once.filter(a => a.defn.onHitEffects.contains(PullToOwner) && a.defn.aoeOnHit.isEmpty && a.defn.maxRange >= 5)
    assertTrue("the roster's pulls", pulls.size >= 3)
    ServerTestKit.quietly(for (a <- pulls; overWater <- Seq(false, true)) Sim() { clock =>
      val m = new SimMatch(clock, WorldData.createEmpty(100, 60), respawns = false)
      val shooter = m.join(a.character.id, 20, 30)
      val target = m.join(CharacterId.Golem, 24, 30)
      val shot = m.launch(shooter, a.projectileType, 1f, 0f)
      if (overWater) m.world.setTile(20, 30, Tile.Water)
      m.tickUntilGone(shot)
      assertTrue(s"${a.name}: strikes", target.getHealth < Full)
      if (overWater) assertEquals(s"${a.name}: its thrower over water, nobody is dragged into it", ((24, 30), 0), (m.at(target), target.getKnockbacks))
      else assertEquals(s"${a.name}: dragged to its thrower's cell, as a shove is drawn", ((20, 30), 1), (m.at(target), target.getKnockbacks))
    })
  }

  // --- Effects no attack in the roster has quite this way ---

  @Test def aSlamWhoseOwnEffectHoldsTellsEveryoneItCatchesWhereTheServerHasThem(): Unit = {
    // A hold that comes of the attack's effect rather than of its splash's own: a move of the
    // server's all the same, told to whoever it holds over TCP
    for ((effect, i) <- Seq[OnHitEffect](Freeze(900), Stun(900), Launch(900), Root(900)).zipWithIndex) Sim() { clock =>
      val id = AttackSweepTest.spareIds(i)
      ProjectileDef.register(ProjectileDef(id = id, name = s"Test slam $effect", speedMultiplier = 0f, damage = 0, maxRange = 0,
        aoeOnMaxRange = Some(AoESplashConfig(4.0f, 10)), onHitEffect = Some(effect), explosionConfig = Some(ExplosionConfig(0, 0, 4.0f))))
      val m = new SimMatch(clock, respawns = false)
      val caster = m.join(CharacterId.Golem, 30, 30)
      val caught = m.join(CharacterId.Golem, 32, 30)
      val beyond = m.join(CharacterId.Golem, 36, 30)
      m.tickUntilGone(m.launch(caster, id, 0f, 0f))
      assertEquals(s"$effect: caught", Full - 10, caught.getHealth)
      assertTrue(s"$effect: held", caught.isFrozen || caught.isRooted)
      assertEquals(s"$effect: a move of the server's", 1, caught.getServerMoves)
      val told = m.updatesAbout(m.tcpSent(caught), caught)
      assertTrue(s"$effect: told over TCP, with the count", told.nonEmpty && told.last.getServerMoves == 1)
      assertEquals(s"$effect: nobody beyond its reach", (Full, 0), (beyond.getHealth, beyond.getServerMoves))
    }
  }

  @Test def aBlastThatTakesNothingAtItsEdgeStillTakesWhatItTakesNearer(): Unit = Sim() { clock =>
    // (Either end of a blast's damage may be nothing; only a blast that is nothing at both is none)
    val id = AttackSweepTest.spareIds(5)
    ProjectileDef.register(ProjectileDef(id = id, name = "Test fading blast", speedMultiplier = 0f, damage = 0, maxRange = 0,
      explosionConfig = Some(ExplosionConfig(30, 0, 4.0f))))
    val m = new SimMatch(clock, respawns = false)
    val caster = m.join(CharacterId.Golem, 30, 30)
    val near = m.join(CharacterId.Golem, 31, 30)
    val edge = m.join(CharacterId.Golem, 34, 30)
    m.tickUntilGone(m.launch(caster, id, 0f, 0f))
    assertEquals(Full - blast(ExplosionConfig(30, 0, 4.0f), 1f), near.getHealth)
    assertEquals("nothing at its very edge", Full, edge.getHealth)
  }

  // --- What flies over what ---

  @Test def whatPassesThroughPlayersStrikesNobodyInItsWay(): Unit =
    sweep(flying.filter(_.defn.passesThroughPlayers)) { (a, clock) =>
      val lane = new Lane(a, clock)
      import lane._
      // In its way, and well short of where it ends (where its blast or its splash is its own affair)
      val reachAtEnd = Math.max(d.aoeOnMaxRange.map(_.radius).getOrElse(0f), d.explosionConfig.map(_.blastRadius).getOrElse(0f))
      if (d.maxRange - reachAtEnd >= 4) {
        val inTheWay = stand(22, 30)
        fire()
        assertEquals(s"${a.name}: flies through whoever is in its way", Full, inTheWay.getHealth)
        assertTrue(hitsOn(inTheWay).isEmpty)
      }
    }
}

object AttackSweepTest {
  /** Projectile ids the game has no use for, for the attacks a test makes of its own (each test
    * file runs in a JVM of its own, so they are nobody else's). */
  lazy val spareIds: Seq[Byte] = {
    // (Looking one up registers the roster's first)
    (-128 to 127).map(_.toByte).filter(id => ProjectileDef.get(id).id != id).take(8)
  }
}
