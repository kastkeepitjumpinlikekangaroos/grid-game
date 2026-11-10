package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

/**
 * A trap is thrown onto the ground, arms after a moment, and takes the first enemy to walk into
 * it. The placer here is a Warden, whose Snare Mine is a bear trap: 6 cells of range, a 12s
 * cooldown, 10 damage and a 2s stun. The Sentinel's Deploy Mine and the Runesmith's Rune Trap are
 * the same ability with different insides.
 */
class TrapTest {
  private val m = new TestMatch()
  private val E = AttackSlot.E
  private val BEAR = TrapType.BEAR_TRAP

  private def warden(x: Int, y: Int, team: Byte = 0): Player = m.join(CharacterId.Warden, x, y, team)
  private def victim(x: Int, y: Int, team: Byte = 0): Player = m.join(CharacterId.Gladiator, x, y, team)
  private def unhurt(p: Player): Boolean = p.getHealth == p.getMaxHealth
  private def onGround(t: Trap): Boolean = t != null && m.instance.trapManager.trapAt(t.x, t.y) != null

  // --- Where one may be put ---

  @Test def aTrapLandsWhereItIsThrown(): Unit = {
    val w = warden(20, 30)
    val watcher = victim(40, 40)
    m.clearSent()
    val trap = m.placeTrap(w, E, BEAR, 24, 30)
    assertNotNull("placed", trap)
    assertEquals(24, trap.x)
    assertEquals(BEAR, trap.trapType)
    assertEquals(w.getId, trap.ownerId)
    // Everyone is told about it, ours and theirs alike — an enemy's is drawn faintly, not hidden
    val spawns = m.trapEvents(m.sent(watcher), TrapAction.SPAWN)
    assertEquals(1, spawns.size)
    assertEquals(trap.id, spawns.head.getTrapId)
    assertEquals(24, spawns.head.getX)
  }

  @Test def aPlacementBeyondRangeIsRefused(): Unit = {
    // Its range, and the steps the server's copy of the thrower can trail them by on a bad network
    val w = warden(20, 30)
    val reach = 6 + Constants.TELEPORT_RANGE_TOLERANCE +
      Movement.lagCells(Constants.NETWORK_TOLERANCE_MS, CharacterDef.get(CharacterId.Warden.id).moveSpeed)
    m.clearSent()
    assertNull("past a 6-cell throw's reach", m.placeTrap(w, E, BEAR, 20 + reach + 1, 30))
    val told = m.trapEvents(m.tcpSent(w), TrapAction.REJECTED)
    assertEquals("and the placer is told, so the cooldown comes back", 1, told.size)
    assertEquals(E, told.head.getAttackSlot)
  }

  @Test def aPlacementIntoAWallIsRefused(): Unit = {
    val w = warden(20, 30)
    m.world.setTile(24, 30, Tile.Wall)
    assertNull(m.placeTrap(w, E, BEAR, 24, 30))
  }

  @Test def aPlacementPastAWallIsTakenSinceTheServerMayTrailTheThrower(): Unit = {
    // The client's throw stops at the first wall (TrapPlacement.target), but the server judges it
    // from its own copy of the thrower, which a bad network can leave steps behind: from there the
    // way to a cell the client threw to cleanly can cross a wall. So it takes a cell a trap can lie
    // on within reach, whatever lies between (isValidTargetLagging); a throw through a wall that only
    // a modified client would make is left to player reports. Onto a wall, never.
    val w = warden(20, 30)
    for (y <- 29 to 31) m.world.setTile(22, y, Tile.Wall)
    assertNotNull("past the wall", m.placeTrap(w, E, BEAR, 25, 30))
    assertFalse("though the strict check, from where it has them, would refuse it",
      TrapPlacement.isValidTargetNear(m.world, 20, 30, 25, 30, 6))
  }

  @Test def aThrowFromAStepAheadOfWhereTheServerHasThemIsTaken(): Unit = {
    // The client throws from where it has the player, a step ahead of the server's copy while that
    // step's update is on its way. From (21, 31) the way to (25, 30) is clear; from (20, 30), where
    // the server still has them, it crosses the pillar at (22, 30)'s corner. Refused, the trap the
    // player saw land never had, in 1-6% of the throws made walking about the real maps.
    val w = warden(20, 30)
    m.world.setTile(22, 30, Tile.Wall)
    assertFalse(TrapPlacement.isValidTarget(m.world, 20, 30, 25, 30, 6))
    assertTrue(TrapPlacement.isValidTarget(m.world, 21, 31, 25, 30, 6))
    assertNotNull(m.placeTrap(w, E, BEAR, 25, 30))
  }

  @Test def aPlacementOntoAnotherTrapIsRefused(): Unit = {
    val first = warden(20, 30)
    val second = warden(20, 32)
    assertNotNull(m.placeTrap(first, E, BEAR, 24, 30))
    assertNull("one trap to a cell", m.placeTrap(second, E, BEAR, 24, 30))
    assertNotNull("but the cell beside it is free", m.placeTrap(second, E, BEAR, 24, 31))
  }

  @Test def aPlacementFromAnAttackThatThrowsNoTrapIsRefused(): Unit = {
    // The Warden's Q is a fan of chains. Judged by the trap type alone, any attack would do.
    val w = warden(20, 30)
    assertNull(m.placeTrap(w, AttackSlot.Q, BEAR, 24, 30))
    assertNull(m.placeTrap(w, AttackSlot.PRIMARY, BEAR, 24, 30))
    assertNull("nor a trap this ability doesn't throw", m.placeTrap(w, E, TrapType.MINE, 24, 30))
  }

  @Test def aSecondCastBeforeTheCooldownIsRefused(): Unit = {
    val w = warden(20, 30)
    assertNotNull(m.placeTrap(w, E, BEAR, 24, 30))
    assertNull("a 12s cooldown, and no time has passed", m.placeTrap(w, E, BEAR, 22, 30))
  }

  @Test def aNewLifeStartsWithTheAbilityReady(): Unit = {
    // The client clears its own cooldowns on respawn, so the server has to clear the clock it
    // holds the player to as well. Left running, a respawned Warden's client showed the trap as
    // ready and every placement it sent was refused until the last life's cooldown ran out.
    val w = warden(20, 30)
    assertNotNull(m.placeTrap(w, E, BEAR, 24, 30))
    assertNull("on cooldown", m.placeTrap(w, E, BEAR, 22, 30))
    w.setHealth(0)
    m.instance.respawn(w.getId)
    val pos = w.getPosition
    assertNotNull("and ready again with the new life",
      m.placeTrap(w, E, BEAR, pos.getX + 2, pos.getY))
  }

  @Test def aFourthTrapTakesTheOldestAway(): Unit = {
    val w = warden(20, 30)
    val watcher = victim(40, 40)
    val oldest = m.armedTrap(w, 22, 30, BEAR)
    val second = m.armedTrap(w, 23, 31, BEAR)
    val third = m.armedTrap(w, 24, 32, BEAR)
    m.clearSent()
    // The fourth goes through the real placement path, so the removal is broadcast too
    val fourth = m.placeTrap(w, E, BEAR, 22, 33)
    assertNotNull(fourth)
    assertFalse("the oldest is gone", onGround(oldest))
    assertTrue(onGround(second))
    assertTrue(onGround(third))
    assertTrue(onGround(fourth))
    val removed = m.trapEvents(m.sent(watcher), TrapAction.REMOVE)
    assertEquals(1, removed.size)
    assertEquals(oldest.id, removed.head.getTrapId)
  }

  @Test def theDeadAndTheHeldPlaceNothing(): Unit = {
    val w = warden(20, 30)
    w.setFrozenUntil(System.currentTimeMillis() + 2000)
    assertNull(m.placeTrap(w, E, BEAR, 24, 30))
    w.setFrozenUntil(0)
    w.setHealth(0)
    assertNull(m.placeTrap(w, E, BEAR, 24, 30))
  }

  // --- What sets one off ---

  @Test def aThrowIsNeverPassedOnAsItCame(): Unit = {
    // The server tells everyone of the trap it puts down itself (broadcastTrap); what the handler
    // says to pass on, GameServer sends to the rest of the match as it came — a throw it refused
    // among them
    var seq = 9000
    def passedOn(in: TestMatch, who: java.util.UUID, x: Int, y: Int, slot: Int = E, trapType: Byte = BEAR,
                 action: Byte = TrapAction.PLACE): Boolean = {
      seq += 1
      var said = false
      ServerTestKit.captureStderr {
        said = in.instance.handler.processPacket(PacketSerializer.deserialize(new TrapPacket(seq, who, x, y, 0, action, trapType,
          0.toByte, slot, null).serialize()), null, null)
      }
      said
    }
    val w = warden(20, 30)
    assertFalse("a throw the server took", passedOn(m, w.getId, 24, 30))
    assertNotNull(m.instance.trapManager.trapAt(24, 30))
    assertFalse("a second before its cooldown", passedOn(m, w.getId, 22, 30))
    m.instance.handler.resetAttacks(w.getId)
    assertFalse("onto another trap", passedOn(m, w.getId, 24, 30))
    assertFalse("out of reach", passedOn(m, w.getId, 58, 30))
    assertFalse("of a trap this attack doesn't throw", passedOn(m, w.getId, 22, 30, trapType = TrapType.MINE))
    assertFalse("from an attack that throws none", passedOn(m, w.getId, 22, 30, slot = AttackSlot.Q))
    for (action <- Seq(TrapAction.SPAWN, TrapAction.TRIGGER, TrapAction.REMOVE, TrapAction.REJECTED))
      assertFalse(s"what only the server says of a trap (action $action)", passedOn(m, w.getId, 22, 30, action = action))
    assertFalse("from somebody who isn't in the match", passedOn(m, java.util.UUID.randomUUID(), 22, 30))
    assertTrue(w.tryFreeze(2000))
    assertFalse("held", passedOn(m, w.getId, 22, 30))
    val phased = warden(20, 40)
    phased.setPhasedUntil(System.currentTimeMillis() + 3000)
    assertFalse("phased", passedOn(m, phased.getId, 22, 40))
    val ceasefire = new TestMatch(opening = true)
    val waiting = ceasefire.join(CharacterId.Warden, 20, 30)
    assertFalse("in a free-for-all's ceasefire", passedOn(ceasefire, waiting.getId, 22, 30))
    assertEquals("and only the one was ever put down", 1, m.instance.trapManager.getAll.size + ceasefire.instance.trapManager.getAll.size)
  }

  @Test def aThrowAsAPhaseEndsIsLetThroughForTheNetworksToleranceToTheMillisecond(): Unit = Sim() { clock =>
    // As a shot is (ShotRequests): the client's phase ends a trip across the wire before the server's
    val sim = new SimMatch(clock)
    val grace = Constants.NETWORK_TOLERANCE_MS
    val early = sim.join(CharacterId.Warden, 20, 20)
    early.setPhasedUntil(clock.millis() + grace + 1)
    ServerTestKit.captureStderr {
      assertNull("with a millisecond more than the tolerance of the phase left", sim.placeTrap(early, E, BEAR, 22, 20))
    }
    val onIt = sim.join(CharacterId.Warden, 20, 30)
    onIt.setPhasedUntil(clock.millis() + grace)
    assertNotNull("with exactly the tolerance left", sim.placeTrap(onIt, E, BEAR, 22, 30))
  }

  @Test def nothingTriggersBeforeItIsArmed(): Unit = {
    val w = warden(26, 30)
    val enemy = victim(28, 30)
    val trap = m.placeTrap(w, E, BEAR, 30, 30)
    assertNotNull(trap)
    assertTrue(m.move(enemy, 29, 30))
    m.tick()
    assertTrue("it is still arming", onGround(trap))
    assertTrue(unhurt(enemy))
    // Once it has, standing there is enough
    Thread.sleep(TrapDef.BearTrap.armDelayMs + 60L)
    m.tick()
    assertFalse(onGround(trap))
    assertFalse(unhurt(enemy))
  }

  @Test def aTrapIsArmedAndGoneOnTheMillisecondItsDefSays(): Unit = Sim() { clock =>
    // Every trap in the roster: nobody standing on it sets it off before its delay is up, the tick
    // that falls on that millisecond does; and with nobody near it lies its whole time, to be taken
    // away by the tick its time runs out on
    for (d <- TrapDef.all) ServerTestKit.quietly {
      val sim = new SimMatch(clock, respawns = false)
      val owner = sim.join(CharacterId.Warden, 10, 10)
      val standing = sim.join(CharacterId.Golem, 30, 30)
      sim.instance.trapManager.place(owner.getId, owner.getTeamId, 30, 30, d.id, clock.millis())
      clock.advance(d.armDelayMs - 1)
      sim.tick()
      assertEquals(s"${d.name}: a millisecond short of its ${d.armDelayMs}ms, still arming", 1, sim.instance.trapManager.getAll.size)
      assertEquals(standing.getMaxHealth, standing.getHealth)
      clock.advance(1)
      sim.tick()
      assertEquals(s"${d.name}: armed, and sprung by whoever stands on it", 0, sim.instance.trapManager.getAll.size)

      val quiet = new SimMatch(clock, respawns = false)
      val watcher = quiet.join(CharacterId.Golem, 50, 50)
      val laid = quiet.instance.trapManager.place(watcher.getId, watcher.getTeamId, 30, 30, d.id, clock.millis())
      clock.advance(d.lifetimeMs - 1)
      quiet.tick()
      assertEquals(s"${d.name}: a millisecond short of its ${d.lifetimeMs}ms, still there", 1, quiet.instance.trapManager.getAll.size)
      clock.advance(1)
      quiet.clearSent()
      quiet.tick()
      assertEquals(s"${d.name}: gone when its time is up", 0, quiet.instance.trapManager.getAll.size)
      assertEquals(s"${d.name}: and everyone is told", Seq(laid.trap.id), quiet.trapEvents(quiet.sent(watcher), TrapAction.REMOVE).map(_.getTrapId))
    }
  }

  @Test def aThrowReachesItsRangeAndTheStepsTheServerMayBeBehindToTheCell(): Unit = {
    // The furthest cell a throw is taken at: its range, the three cells every reach is given, and as
    // far as the thrower could have walked while their steps were on their way
    val w = warden(20, 30)
    val reach = 6 + Constants.TELEPORT_RANGE_TOLERANCE +
      Movement.lagCells(Constants.NETWORK_TOLERANCE_MS, CharacterDef.get(CharacterId.Warden.id).moveSpeed)
    assertNotNull("its last cell", m.placeTrap(w, E, BEAR, 20 + reach, 30))
    val other = warden(20, 40)
    assertNull("and not the next", m.placeTrap(other, E, BEAR, 20 + reach + 1, 40))
    assertNotNull("the same up and down", m.placeTrap(other, E, BEAR, 20, 40 - reach))
  }

  @Test def anEnemyWhoStepsOnABearTrapIsStunnedAndHurt(): Unit = {
    val w = warden(20, 30)
    val enemy = victim(28, 30)
    val watcher = victim(40, 40)
    val trap = m.armedTrap(w, 30, 30, BEAR)
    m.clearSent()
    assertTrue(m.move(enemy, 29, 30))
    assertEquals("10 damage", enemy.getMaxHealth - 10, enemy.getHealth)
    assertTrue("stunned", enemy.isStunned)
    assertTrue("which is a freeze wearing stars, so it holds them too", enemy.isFrozen)
    assertEquals("about two seconds", 2000L, enemy.getStunnedUntil - System.currentTimeMillis(), 120.0)
    assertFalse("and the trap is spent", onGround(trap))
    val told = m.trapEvents(m.sent(watcher), TrapAction.TRIGGER)
    assertEquals(1, told.size)
    assertEquals(enemy.getId, told.head.getVictimId)
    assertEquals(trap.id, told.head.getTrapId)
  }

  @Test def itReachesOneCellButNoFurther(): Unit = {
    val w = warden(20, 30)
    val enemy = victim(28, 30)
    val trap = m.armedTrap(w, 30, 31, BEAR)
    assertTrue(m.move(enemy, 29, 30))
    m.tick()
    assertTrue("a cell and a half away", onGround(trap))
    assertTrue(m.move(enemy, 30, 30))
    assertFalse("directly above it", onGround(trap))
  }

  @Test def theOwnerAndTheirTeammatesWalkOverItSafely(): Unit = {
    val t = new TestMatch(gameMode = 1)
    val w = t.join(CharacterId.Warden, 20, 30, team = 1)
    val ally = t.join(CharacterId.Gladiator, 28, 30, team = 1)
    val enemy = t.join(CharacterId.Soldier, 28, 32, team = 2)
    val trap = t.armedTrap(w, 30, 30, BEAR)
    assertTrue(t.move(ally, 29, 30))
    t.tick()
    assertTrue("an ally", t.instance.trapManager.trapAt(30, 30) != null)
    assertTrue(t.move(w, 22, 30))
    w.setPosition(new Position(30, 30))
    t.tick()
    assertTrue("and the owner themselves", t.instance.trapManager.trapAt(30, 30) != null)
    assertTrue(unhurt(ally))
    assertTrue(unhurt(w))
    // An enemy is another matter
    assertTrue(t.move(enemy, 29, 31))
    assertTrue(t.move(enemy, 29, 30))
    assertNull("which it is there for", t.instance.trapManager.trapAt(30, 30))
    assertFalse(unhurt(enemy))
    assertTrue(enemy.isStunned)
    assertEquals(trap.ownerId, w.getId)
  }

  @Test def aPhasedEnemyPassesOverIt(): Unit = {
    val w = warden(20, 30)
    val enemy = victim(28, 30)
    val trap = m.armedTrap(w, 30, 30, BEAR)
    enemy.setPhasedUntil(System.currentTimeMillis() + 3000)
    assertTrue(m.move(enemy, 30, 30, flags = 0x08))
    m.tick()
    assertTrue("stepped clean over it", onGround(trap))
    assertTrue(unhurt(enemy))
    // And is caught by it the moment the phase ends
    enemy.setPhasedUntil(0)
    m.tick()
    assertFalse(onGround(trap))
  }

  @Test def aTrapHoppedOverInOneUpdateStillSprings(): Unit = {
    // A lost datagram carries a player several cells at once. Checked only where they ended up,
    // a trap they stepped clean over would never go off.
    val w = warden(20, 30)
    val enemy = victim(20, 40)
    val trap = m.armedTrap(w, 24, 40, BEAR)
    // The last update the server had from them, where they stood; the next arrives most of a
    // fifth of a second later, its predecessors lost
    assertTrue(m.move(enemy, 20, 40))
    assertTrue(m.move(enemy, 27, 40, gapMs = 180))
    assertFalse("they went over it", onGround(trap))
    assertFalse(unhurt(enemy))
  }

  // --- What each one does ---

  @Test def aMineBlastsWithFalloffAndCreditsItsOwner(): Unit = {
    val sentinel = m.join(CharacterId.Sentinel, 10, 10)
    val stepper = victim(28, 20)
    val nearby = victim(31, 20)   // 1 cell from the blast's centre
    val further = victim(32, 21)  // 2.2 cells out
    val outside = victim(35, 20)  // past its 3 cells
    val trap = m.armedTrap(sentinel, 30, 20, TrapType.MINE)
    assertTrue(m.move(stepper, 29, 20))
    assertFalse(onGround(trap))
    val took = (p: Player) => p.getMaxHealth - p.getHealth
    assertTrue("hurt most at the centre", took(nearby) > took(further))
    assertTrue(took(further) > 0)
    assertTrue("and not at all outside it", unhurt(outside))
    assertTrue("the one who stood on it is in the blast too", took(stepper) > 0)
  }

  @Test def aMinesBlastTakesWhatItsFalloffSaysToTheCellAndEveryoneIsTold(): Unit = {
    // 55 at its centre down to 27 at the edge of its three cells, and nothing past them
    val blast = TrapDef.Mine.explosion.get
    assertEquals((55, 27, 3.0f), (blast.centerDamage, blast.edgeDamage, blast.blastRadius))
    val sentinel = m.join(CharacterId.Sentinel, 10, 10)
    val watcher = victim(55, 55)
    val onIt = victim(30, 20)
    val atTheEdge = victim(33, 20)     // exactly three cells out
    val aKnightsMove = victim(31, 22)  // 2.24 cells
    val justPast = victim(33, 21)      // 3.16 cells
    val took = (p: Player) => p.getMaxHealth - p.getHealth
    m.armedTrap(sentinel, 30, 20, TrapType.MINE)
    m.clearSent()
    m.tick() // whoever stands on one sets it off
    assertEquals("the centre", 55, took(onIt))
    assertEquals("the edge, to the cell", 27, took(atTheEdge))
    assertEquals("between", (55 - Math.sqrt(5.0).toFloat / 3.0f * (55 - 27)).toInt, took(aKnightsMove))
    assertEquals("past it", 0, took(justPast))
    val heard = m.sent(watcher)
    for (p <- Seq(onIt, atTheEdge, aKnightsMove))
      assertEquals(s"everyone is told what it left ${m.at(p)} with", Some(p.getHealth), m.updatesAbout(heard, p).lastOption.map(_.getHealth))
    assertTrue("and nothing of anyone it missed", m.updatesAbout(heard, justPast).isEmpty)
    assertEquals("a blast holds nobody: no move of the server's", 0, onIt.getServerMoves + atTheEdge.getServerMoves)
  }

  @Test def aMinesBlastSparesItsOwnerTheirTeamTheShieldedThePhasedAndTheDead(): Unit = {
    val teams = new TestMatch(gameMode = 1)
    val owner = teams.join(CharacterId.Sentinel, 30, 22, team = 1)
    val ally = teams.join(CharacterId.Gladiator, 28, 20, team = 1)
    val stepper = teams.join(CharacterId.Gladiator, 30, 20, team = 2)
    val shielded = teams.join(CharacterId.Gladiator, 30, 18, team = 2)
    val phased = teams.join(CharacterId.Gladiator, 32, 21, team = 2)
    val dead = teams.join(CharacterId.Gladiator, 29, 22, team = 2)
    val enemy = teams.join(CharacterId.Gladiator, 31, 19, team = 2)
    shielded.setShieldUntil(System.currentTimeMillis() + 5000)
    phased.setPhasedUntil(System.currentTimeMillis() + 5000)
    dead.setHealth(0)
    teams.armedTrap(owner, 30, 20, TrapType.MINE)
    teams.tick()
    assertEquals(0, teams.instance.trapManager.getAll.size)
    assertTrue("whoever set it off", stepper.getHealth < stepper.getMaxHealth)
    assertTrue("and an enemy beside them", enemy.getHealth < enemy.getMaxHealth)
    assertEquals("not whoever laid it", owner.getMaxHealth, owner.getHealth)
    assertEquals("nor their team", ally.getMaxHealth, ally.getHealth)
    assertEquals("nor the shielded", shielded.getMaxHealth, shielded.getHealth)
    assertEquals("nor the phased", phased.getMaxHealth, phased.getHealth)
    assertEquals("and the dead are not killed again", 0, teams.instance.killTracker.getDeaths(dead.getId))
    // In a free-for-all nobody is on its owner's side, and it still spares them
    val ffa = new TestMatch()
    val alone = ffa.join(CharacterId.Sentinel, 30, 22)
    val other = ffa.join(CharacterId.Gladiator, 30, 20)
    ffa.armedTrap(alone, 30, 20, TrapType.MINE)
    ffa.tick()
    assertTrue(other.getHealth < other.getMaxHealth)
    assertEquals("its owner, two cells off, in a free-for-all", alone.getMaxHealth, alone.getHealth)
  }

  @Test def whatATrapDidToItsVictimIsToldToEveryoneAndAHoldIsAMoveOfTheServers(): Unit = {
    // A hold puts its victim's client back where the server has them, as any hold does
    // (holdByServer): counted as a server move, and said to them over TCP
    for ((character, trapType, holds) <- Seq((CharacterId.Warden, TrapType.BEAR_TRAP, true), (CharacterId.Warden, TrapType.SNARE, true),
        (CharacterId.Runesmith, TrapType.BINDING_RUNE, true), (CharacterId.Runesmith, TrapType.FIRE_RUNE, false),
        (CharacterId.Warden, TrapType.POISON_POD, false))) {
      val fresh = new TestMatch()
      val owner = fresh.join(character, 20, 30)
      val enemy = fresh.join(CharacterId.Gladiator, 28, 30)
      val watcher = fresh.join(CharacterId.Gladiator, 50, 50)
      val d = TrapDef.get(trapType)
      assertNotNull(fresh.armedTrap(owner, 30, 30, trapType))
      assertTrue(fresh.move(enemy, 28, 30))
      fresh.clearSent()
      assertTrue(fresh.move(enemy, 29, 30, gapMs = 60))
      assertEquals(s"${d.name}: sprung", 0, fresh.instance.trapManager.getAll.size)
      assertEquals(s"${d.name}: a hold is a move of the server's, and nothing else a trap does is", if (holds) 1 else 0, enemy.getServerMoves)
      val toldToAll = fresh.updatesAbout(fresh.sent(watcher), enemy)
      assertTrue(s"${d.name}: everyone is told", toldToAll.nonEmpty)
      assertEquals(s"${d.name}: what it took", enemy.getMaxHealth - d.damage, toldToAll.last.getHealth)
      val flags = (toldToAll.last.getEffectFlags, toldToAll.last.getEffectFlags2)
      d.effects.foreach {
        case Stun(_) => assertTrue(s"${d.name}: seen stunned", (flags._2 & 0x01) != 0 && (flags._1 & 0x04) != 0)
        case Root(_) => assertTrue(s"${d.name}: seen rooted", (flags._1 & 0x40) != 0)
        case Burn(_, _, _) => assertTrue(s"${d.name}: seen burning", (flags._1 & 0x10) != 0)
        case Poison(_, _, _) => assertTrue(s"${d.name}: seen poisoned", (flags._2 & 0x02) != 0)
        case Slow(_, _) => assertTrue(s"${d.name}: seen slowed", (flags._1 & 0x80) != 0)
        case _ =>
      }
      val toldToThem = fresh.updatesAbout(fresh.tcpSent(enemy), enemy)
      if (holds) assertEquals(s"${d.name}: its victim is told where the server holds them", Some((new Position(29, 30), 1)),
        toldToThem.lastOption.map(u => (u.getPosition, u.getServerMoves)))
    }
    // And a hold that didn't land (they were just out of another) moves nobody
    val w = warden(20, 40)
    val immune = victim(28, 40)
    m.armedTrap(w, 30, 40, BEAR)
    immune.tryFreeze(10)
    Thread.sleep(30)
    assertTrue(immune.isCCImmune)
    assertTrue(m.move(immune, 29, 40))
    assertEquals("the trap bit", immune.getMaxHealth - 10, immune.getHealth)
    assertEquals("but held nobody", 0, immune.getServerMoves)
  }

  @Test def aMinesKillIsCreditedToWhoeverLaidIt(): Unit = {
    // Scored as a trap kill (Attrs.CauseTrap), to whoever laid it — not to whoever walked in
    val sentinel = m.join(CharacterId.Sentinel, 10, 10)
    val stepper = victim(28, 20)
    stepper.setHealth(10)
    m.armedTrap(sentinel, 30, 20, TrapType.MINE)
    assertTrue(m.move(stepper, 29, 20))
    assertTrue(stepper.isDead)
    assertEquals(1, m.instance.killTracker.getKills(sentinel.getId))
    assertEquals(1, m.instance.killTracker.getDeaths(stepper.getId))
  }

  @Test def aFireRuneBurnsForItsOwner(): Unit = {
    val smith = m.join(CharacterId.Runesmith, 10, 10)
    val enemy = victim(28, 20)
    m.armedTrap(smith, 30, 20, TrapType.FIRE_RUNE)
    assertTrue(m.move(enemy, 29, 20))
    assertTrue("alight", enemy.isBurning)
    assertEquals("and it is the rune's owner who is burning them", smith.getId, enemy.getBurnOwnerId)
    val before = enemy.getHealth
    Thread.sleep(TrapDef.FireRune.effects.collectFirst { case b: Burn => b.tickMs }.get + 60L)
    m.instance.tickPlayers()
    assertTrue("and it bites", enemy.getHealth < before)
  }

  @Test def aPoisonPodPoisonsAndSlows(): Unit = {
    val w = warden(10, 10)
    val enemy = victim(28, 20)
    m.armedTrap(w, 30, 20, TrapType.POISON_POD)
    assertTrue(m.move(enemy, 29, 20))
    assertTrue(enemy.isPoisoned)
    assertTrue("and slowed on top of it", enemy.isSlowed)
    assertEquals(0.6f, enemy.getSlowMultiplier, 0.001f)
    assertEquals(w.getId, enemy.getPoisonOwnerId)
    assertEquals("48 over twelve ticks", 4, enemy.getPoisonDamagePerTick)
  }

  @Test def aPoisonTrapDealsItsWholeTotalAndCreditsItsOwner(): Unit = {
    // On a poison short enough to watch run out. The roster's pod is the same thing over 6s.
    val w = warden(10, 10)
    val enemy = victim(28, 20)
    val full = enemy.getHealth
    m.armedTrap(w, 30, 20, TestTraps.QuickPoison.id)
    assertTrue(m.move(enemy, 29, 20))
    val deadline = System.currentTimeMillis() + 3000
    while (enemy.isPoisoned && System.currentTimeMillis() < deadline) {
      Thread.sleep(25)
      m.instance.tickPlayers()
    }
    assertFalse("it ran its course", enemy.isPoisoned)
    assertEquals("the whole 20", full - 20, enemy.getHealth)
  }

  @Test def aTrapsHoldObeysCCImmunityAndIsSpentEitherWay(): Unit = {
    val w = warden(20, 30)
    val enemy = victim(28, 30)
    val trap = m.armedTrap(w, 30, 30, BEAR)
    // Just out of a freeze, so the stun can't land
    enemy.tryFreeze(10)
    Thread.sleep(30)
    assertTrue(enemy.isCCImmune)
    assertTrue(m.move(enemy, 29, 30))
    assertFalse("no stun while immune", enemy.isStunned)
    assertFalse("but the trap is spent all the same", onGround(trap))
    assertEquals("and it still bites", enemy.getMaxHealth - 10, enemy.getHealth)
  }

  // --- Coming and going ---

  @Test def aTrapRunsOutAndIsTakenAway(): Unit = {
    val w = warden(20, 30)
    val watcher = victim(40, 40)
    val now = System.currentTimeMillis()
    val placed = m.instance.trapManager.place(w.getId, w.getTeamId, 24, 30, BEAR,
      now - TrapDef.BearTrap.lifetimeMs - 10)
    assertNotNull(placed)
    m.clearSent()
    m.tick()
    assertNull("gone", m.instance.trapManager.trapAt(24, 30))
    val removed = m.trapEvents(m.sent(watcher), TrapAction.REMOVE)
    assertEquals(1, removed.size)
    assertEquals(placed.trap.id, removed.head.getTrapId)
  }

  @Test def theOwnerLeavingTakesTheirTrapsWithThem(): Unit = {
    val w = warden(20, 30)
    val other = warden(20, 34)
    val watcher = victim(40, 40)
    val mine = m.armedTrap(w, 24, 30, BEAR)
    val theirs = m.armedTrap(other, 24, 34, BEAR)
    m.clearSent()
    m.instance.handler.removePlayer(w.getId)
    assertFalse(onGround(mine))
    assertTrue("and nobody else's", onGround(theirs))
    val removed = m.trapEvents(m.sent(watcher), TrapAction.REMOVE)
    assertEquals(1, removed.size)
    assertEquals(mine.id, removed.head.getTrapId)
  }

  @Test def aRejoiningPlayerIsSentTheTrapsOnTheGround(): Unit = {
    val w = warden(20, 30)
    val back = victim(40, 40)
    val a = m.armedTrap(w, 24, 30, BEAR)
    val b = m.armedTrap(w, 25, 32, TrapType.MINE)
    m.clearSent()
    m.rejoin(back)
    val spawns = m.trapEvents(m.tcpSent(back), TrapAction.SPAWN)
    assertEquals(2, spawns.size)
    assertEquals(Set(a.id, b.id), spawns.map(_.getTrapId).toSet)
    assertEquals(Set(BEAR, TrapType.MINE), spawns.map(_.getTrapType).toSet)
  }
}

/**
 * Traps the roster hasn't got, on ids the game doesn't use, for what can't be watched on the
 * roster's own numbers — a poison over six seconds is not something a test can sit through.
 * Each test file gets a JVM of its own, so these can't leak into another suite.
 */
object TestTraps {
  val QuickPoison: TrapDef = TrapDef(
    id = 100.toByte, name = "Test Pod", kind = TrapKind.POD, colorRGB = 0xFF77BB44,
    effects = Seq(Poison(20, 400, 100))
  )

  TrapDef.register(QuickPoison)
}
