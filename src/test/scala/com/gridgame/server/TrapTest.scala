package com.gridgame.server

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
    val w = warden(20, 30)
    m.clearSent()
    assertNull("12 cells from a 6-cell throw", m.placeTrap(w, E, BEAR, 32, 30))
    val told = m.trapEvents(m.tcpSent(w), TrapAction.REJECTED)
    assertEquals("and the placer is told, so the cooldown comes back", 1, told.size)
    assertEquals(E, told.head.getAttackSlot)
  }

  @Test def aPlacementIntoAWallIsRefused(): Unit = {
    val w = warden(20, 30)
    m.world.setTile(24, 30, Tile.Wall)
    assertNull(m.placeTrap(w, E, BEAR, 24, 30))
  }

  @Test def aPlacementThroughAWallIsRefused(): Unit = {
    // The throw stops at the first wall, so a trap can't be dropped on the far side of one
    val w = warden(20, 30)
    m.world.setTile(22, 30, Tile.Wall)
    assertNull(m.placeTrap(w, E, BEAR, 25, 30))
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
    assertTrue(m.move(enemy, 27, 40, gapMs = 90))
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
