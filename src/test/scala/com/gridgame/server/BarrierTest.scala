package com.gridgame.server

import com.gridgame.server.bots.BotController
import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

/**
 * A barrier, raised by its holder's client and carried in front of them facing their aim, stops
 * enemy projectiles (not their own, an ally's, or anything that flies over walls) and shelters
 * whoever is behind it from blasts centred in front. The holder here is a Crusader, whose Bulwark
 * is one; facing 0 is +x, so their barrier stands 2 cells out along it, 6 wide.
 */
class BarrierTest {
  private val m = new TestMatch()
  private val East = 0.0
  private val BARRIER = 0x04

  /** The holder's client saying its barrier is up, facing `angle`: a raise, or a turn. */
  private def raise(t: TestMatch, p: Player, angle: Double = East): Boolean =
    t.move(p, p.getPosition.getX, p.getPosition.getY, flags2 = BARRIER, aimAngle = angle)

  private def unhurt(p: Player): Boolean = p.getHealth == p.getMaxHealth

  // --- What it stops ---

  @Test def anEnemysShotFromTheFrontIsBlocked(): Unit = {
    val holder = m.join(CharacterId.Crusader, 20, 30)
    val enemy = m.join(CharacterId.Soldier, 30, 30)
    raise(m, holder)
    assertTrue(holder.hasBarrier)
    m.clearSent()
    m.tickUntilGone(m.launch(enemy, ProjectileType.BULLET, -1f, 0f))
    assertTrue("never touched", unhurt(holder))
    val told = m.sent(enemy)
    val blocked = m.projectileEvents(told, ProjectileAction.BLOCKED)
    assertEquals(1, blocked.size)
    assertEquals("whose barrier", holder.getId, blocked.head.getTargetId)
    assertEquals("where it met it, two cells out", 22f, blocked.head.getX, 0.1f)
    assertEquals(30f, blocked.head.getY, 0.01f)
    assertTrue(m.projectileEvents(told, ProjectileAction.HIT).isEmpty)
  }

  @Test def aShotFromBehindHits(): Unit = {
    val holder = m.join(CharacterId.Crusader, 20, 30)
    val enemy = m.join(CharacterId.Soldier, 10, 30)
    raise(m, holder)
    m.tickUntilGone(m.launch(enemy, ProjectileType.BULLET, 1f, 0f))
    assertEquals(holder.getMaxHealth - 20, holder.getHealth)
  }

  @Test def theHoldersOwnShotsPass(): Unit = {
    // Out through it, the way they fire...
    val holder = m.join(CharacterId.Crusader, 20, 30)
    val ahead = m.join(CharacterId.Gladiator, 25, 30)
    raise(m, holder)
    m.tickUntilGone(m.launch(holder, ProjectileType.BULLET, 1f, 0f))
    assertFalse(unhurt(ahead))
    // ...and back in through its front, the way a boomerang comes home
    val t = new TestMatch()
    val returning = t.join(CharacterId.Crusader, 20, 30)
    val behind = t.join(CharacterId.Gladiator, 16, 30)
    raise(t, returning)
    t.tickUntilGone(t.launch(returning, ProjectileType.BULLET, -1f, 0f, from = (30, 30)))
    assertFalse("through it to someone behind", unhurt(behind))
  }

  @Test def aTeammatesShotPasses(): Unit = {
    val t = new TestMatch(gameMode = 1)
    val holder = t.join(CharacterId.Crusader, 20, 30, team = 1)
    val ally = t.join(CharacterId.Soldier, 30, 30, team = 1)
    val enemy = t.join(CharacterId.Gladiator, 16, 30, team = 2)
    raise(t, holder)
    t.tickUntilGone(t.launch(ally, ProjectileType.BULLET, -1f, 0f))
    assertFalse("through it, past the holder, into the enemy", unhurt(enemy))
    assertTrue(unhurt(holder))
  }

  @Test def anEnemysShotIsBlockedInFreeForAll(): Unit = {
    // Everyone else is an enemy there, whatever team numbers they carry
    val holder = m.join(CharacterId.Crusader, 20, 30, team = 1)
    val other = m.join(CharacterId.Soldier, 30, 30, team = 1)
    raise(m, holder)
    m.tickUntilGone(m.launch(other, ProjectileType.BULLET, -1f, 0f))
    assertTrue(unhurt(holder))
  }

  @Test def whatFliesOverWallsFliesOverItToo(): Unit = {
    for (pType <- Seq(ProjectileType.LIGHTNING, ProjectileType.SOUL_BOLT)) {
      val t = new TestMatch()
      val holder = t.join(CharacterId.Crusader, 20, 30)
      val enemy = t.join(CharacterId.Stormcaller, 30, 30)
      raise(t, holder)
      t.tickUntilGone(t.launch(enemy, pType, -1f, 0f))
      assertFalse(s"type $pType", unhurt(holder))
    }
  }

  @Test def everythingThatDoesNotFlyOverWallsIsStopped(): Unit = {
    // Every projectile in the game, fired at the front of a barrier from close in and from further
    // off, in the open and with walls about the holder (one behind them, one beside them, one
    // between them and their barrier): neither the holder nor a teammate behind them is touched —
    // no damage, no hold, no slow, no burn, no poison, no push or pull — by anything that doesn't
    // fly over walls. (What does fly over them flies over a barrier too, above.)
    val types = (Byte.MinValue.toInt to Byte.MaxValue.toInt).map(_.toByte)
      .map(ProjectileDef.get).filter(d => !d.passesThroughWalls).distinctBy(_.id)
    assertTrue(s"the whole roster's projectiles: ${types.size}", types.size > 100)
    val layouts: Seq[(String, WorldData => Unit)] = Seq(
      ("open ground", _ => ()),
      ("a wall behind", w => for (y <- 26 to 34) w.setTile(27, y, Tile.Wall)),
      ("a wall beside", w => for (x <- 25 to 35) w.setTile(x, 27, Tile.Wall)),
      ("a wall in front", w => for (y <- 27 to 33) w.setTile(31, y, Tile.Wall)))
    val through = for {
      pDef <- types
      (layout, build) <- layouts
      (dx, dy) <- Seq((3, 0), (5, 1), (9, 0))
      touched <- {
        val w = WorldData.createEmpty(60, 60)
        build(w)
        val t = new TestMatch(world = w, gameMode = 1)
        val holder = t.join(CharacterId.Crusader, 30, 30, team = 1)
        val ally = t.join(CharacterId.Wizard, 29, 30, team = 1)
        val enemy = t.join(CharacterId.Soldier, 30 + dx, 30 + dy, team = 2)
        raise(t, holder, Math.atan2(dy, dx))
        val len = Math.sqrt(dx * dx + dy * dy).toFloat
        t.tickUntilGone(t.launch(enemy, pDef.id, -dx / len, -dy / len))
        t.tick(3)
        def untouched(p: Player, at: (Int, Int)): Boolean = unhurt(p) && !p.isFrozen && !p.isRooted &&
          !p.isSlowed && !p.isBurning && !p.isPoisoned && t.at(p) == at
        val hit = Seq((holder, (30, 30), "the holder"), (ally, (29, 30), "a teammate behind them"))
          .collect { case (p, at, who) if !untouched(p, at) => s"${pDef.name} from ($dx, $dy), $layout: $who" }
        t.instance.projectileManager.close()
        hit
      }
    } yield touched
    assertEquals("", through.mkString("\n"))
  }

  // --- Who it shelters ---

  @Test def aTeammateBesideTheHolderIsSheltered(): Unit = {
    val t = new TestMatch(gameMode = 1)
    val holder = t.join(CharacterId.Crusader, 20, 30, team = 1)
    val beside = t.join(CharacterId.Wizard, 20, 31, team = 1)
    val enemy = t.join(CharacterId.Soldier, 30, 31, team = 2)
    raise(t, holder)
    t.tickUntilGone(t.launch(enemy, ProjectileType.BULLET, -1f, 0f))
    assertTrue(unhurt(beside))
    // Nor by a shot whose hit radius reaches past the barrier: an axe connects from 2.5 cells, so
    // it would hit the teammate, and the holder, before it got to it
    t.tickUntilGone(t.launch(enemy, ProjectileType.AXE, -1f, 0f, from = (25, 31)))
    assertTrue(unhurt(beside))
    assertTrue(unhurt(holder))
  }

  @Test def aRocketGoesOffOnTheBarrier(): Unit = {
    val t = new TestMatch(gameMode = 1)
    val holder = t.join(CharacterId.Crusader, 20, 30, team = 1)
    val behind = t.join(CharacterId.Wizard, 21, 31, team = 1)   // between the barrier and the holder
    val inFront = t.join(CharacterId.Gladiator, 23, 32, team = 1)
    val enemy = t.join(CharacterId.Soldier, 32, 30, team = 2)
    raise(t, holder)
    t.clearSent()
    t.tickUntilGone(t.launch(enemy, ProjectileType.ROCKET, -1f, 0f))
    val boom = t.projectileEvents(t.sent(enemy), ProjectileAction.DESPAWN)
    assertEquals("it goes off", 1, boom.size)
    assertEquals("on the barrier", 22f, boom.head.getX, 0.1f)
    // All three are inside its 2.5-cell blast, but only one is in front of the barrier
    assertFalse("in front of it", unhurt(inFront))
    assertTrue("the holder, behind it", unhurt(holder))
    assertTrue("a teammate behind it", unhurt(behind))
  }

  @Test def aSlamInFrontOfItDoesNotReachBehindIt(): Unit = {
    val holder = m.join(CharacterId.Crusader, 20, 30)
    val slammer = m.join(CharacterId.Beetle, 25, 30)
    val aside = m.join(CharacterId.Gladiator, 25, 34)
    raise(m, holder)
    m.tickUntilGone(m.launch(slammer, ProjectileType.TREMOR_SLAM, 0f, 0f))
    assertTrue("behind the barrier, 5 cells into a 6-cell slam", unhurt(holder))
    assertFalse("out to the side of it", unhurt(aside))
  }

  @Test def aThrownTrapStopsAtItAsAtAWall(): Unit = {
    // A throw stops at a wall, so a trap can't be dropped on the far side of one; and at a barrier,
    // so it can't be dropped behind one, where its holder and whoever they shelter stand
    val holder = m.join(CharacterId.Crusader, 20, 30)
    val warden = m.join(CharacterId.Warden, 26, 30)
    raise(m, holder)
    assertNull("behind it", m.placeTrap(warden, AttackSlot.E, TrapType.BEAR_TRAP, 21, 30))
    assertEquals("the throw lands short of it, where the server takes it", Some(new Position(23, 30)),
      TrapPlacement.target(m.world, 26, 30, 21.0, 30.0, 6,
        (x, y) => m.instance.barrierAcross(warden.getId, 26f, 30f, x.toFloat, y.toFloat)))
    assertNotNull("in front of it", m.placeTrap(warden, AttackSlot.E, TrapType.BEAR_TRAP, 23, 30))
  }

  @Test def aTrapThrownFromBehindItOrByAnAllyGoesOver(): Unit = {
    val t = new TestMatch(gameMode = 1)
    val holder = t.join(CharacterId.Crusader, 20, 30, team = 1)
    val ally = t.join(CharacterId.Warden, 26, 31, team = 1)
    val enemyBehind = t.join(CharacterId.Warden, 15, 30, team = 2)
    raise(t, holder)
    assertNotNull("an ally's, out through its front", t.placeTrap(ally, AttackSlot.E, TrapType.BEAR_TRAP, 21, 31))
    assertNotNull("an enemy's from behind, as a shot from behind", t.placeTrap(enemyBehind, AttackSlot.E, TrapType.BEAR_TRAP, 19, 30))
  }

  @Test def aPhasedHoldersBarrierIsAsInsubstantialAsThey(): Unit = {
    val t = new TestMatch(gameMode = 1)
    val holder = t.join(CharacterId.Crusader, 20, 30, team = 1)
    val beside = t.join(CharacterId.Wizard, 20, 31, team = 1)
    val enemy = t.join(CharacterId.Soldier, 30, 31, team = 2)
    raise(t, holder)
    holder.setPhasedUntil(System.currentTimeMillis() + 5000)
    t.tickUntilGone(t.launch(enemy, ProjectileType.BULLET, -1f, 0f))
    assertFalse(unhurt(beside))
  }

  // --- Where it is ---

  @Test def anEnemyPressedAgainstItCannotShootThroughIt(): Unit = {
    // They stand just outside it, and a shot spawns a cell out from whoever fires it: on the
    // barrier's far side, a cell from the holder
    val holder = m.join(CharacterId.Crusader, 20, 30)
    val enemy = m.join(CharacterId.Soldier, 22, 31)
    raise(m, holder, Math.atan2(1, 2)) // straight at them: they are 2.24 cells out
    m.clearSent()
    val (dx, dy) = ((-2 / Math.sqrt(5)).toFloat, (-1 / Math.sqrt(5)).toFloat)
    val shots = m.fire(enemy, AttackSlot.PRIMARY, ProjectileType.BULLET, Seq((dx, dy)))
    assertEquals(1, shots.size)
    m.tickUntilGone(shots.head)
    assertTrue(unhurt(holder))
    assertEquals(1, m.projectileEvents(m.sent(enemy), ProjectileAction.BLOCKED).size)
  }

  @Test def aBarrierCarriedOntoAShotStopsIt(): Unit = {
    val holder = m.join(CharacterId.Crusader, 20, 30)
    val enemy = m.join(CharacterId.Wizard, 30, 30)
    raise(m, holder)
    // A slow shot, flown until it is just short of the barrier...
    val shot = m.launch(enemy, ProjectileType.FIREBALL, -1f, 0f)
    while (shot.getX > 22.7f) m.tick()
    assertNotNull(m.instance.projectileManager.getProjectile(shot.id))
    // ...and the holder steps into it, carrying the barrier past where it is
    assertTrue(m.move(holder, 21, 30, flags2 = BARRIER, aimAngle = East))
    m.tickUntilGone(shot)
    assertTrue(unhurt(holder))
  }

  @Test def itTurnsWithTheAim(): Unit = {
    val holder = m.join(CharacterId.Crusader, 20, 30)
    val enemy = m.join(CharacterId.Soldier, 20, 40)
    raise(m, holder, East)
    // Facing +x, a shot coming up from +y takes them in the side
    m.tickUntilGone(m.launch(enemy, ProjectileType.BULLET, 0f, -1f))
    assertEquals(holder.getMaxHealth - 20, holder.getHealth)
    // Turned to face it, the next one is stopped
    raise(m, holder, Math.PI / 2)
    m.tickUntilGone(m.launch(enemy, ProjectileType.BULLET, 0f, -1f))
    assertEquals(holder.getMaxHealth - 20, holder.getHealth)
  }

  @Test def everyoneIsToldOfItAndWhichWayItFaces(): Unit = {
    val holder = m.join(CharacterId.Crusader, 20, 30)
    raise(m, holder, 1.0)
    val up = m.instance.stateUpdate(holder)
    assertEquals(BARRIER, up.getEffectFlags2 & BARRIER)
    assertEquals(1.0, up.aimAngleRadians, 1e-3)
    raise(m, holder, 2.5)
    assertEquals(2.5, m.instance.stateUpdate(holder).aimAngleRadians, 1e-3)
    m.move(holder, 20, 30) // an update without it
    assertEquals(0, m.instance.stateUpdate(holder).getEffectFlags2 & BARRIER)
  }

  // --- Up, and down ---

  @Test def everyoneIsToldHowLongItHasLeft(): Unit = {
    // Its holder's client streams the updates that carry it, and those can stop for longer than
    // a lease — a hitch in its render loop, a burst of lost datagrams, a run the server refused.
    // Everyone runs the barrier's own timer instead, so a gap can't take it off their screens.
    val holder = m.join(CharacterId.Crusader, 20, 30)
    raise(m, holder)
    val left = m.instance.stateUpdate(holder).getBarrierMs
    assertTrue(s"the Bulwark's 3.5s, less what has passed: $left", left > 3000 && left <= 3500)
    holder.dropBarrier()
    assertEquals("and nothing once it is down", 0, m.instance.stateUpdate(holder).getBarrierMs)
  }

  @Test def itLastsItsTimeAndThenComesDown(): Unit = {
    val holder = m.join(CharacterId.Crusader, 20, 30)
    val enemy = m.join(CharacterId.Soldier, 30, 30)
    raise(m, holder)
    assertEquals("the Bulwark's 3.5s", holder.getBarrierRaisedAt + 3500, holder.getBarrierUntil)
    holder.setBarrierUntil(System.currentTimeMillis() + 40) // its last moments
    Thread.sleep(70)
    assertFalse(holder.hasBarrier)
    m.tickUntilGone(m.launch(enemy, ProjectileType.BULLET, -1f, 0f))
    assertEquals(holder.getMaxHealth - 20, holder.getHealth)
    assertEquals(0, m.instance.stateUpdate(holder).getEffectFlags2 & BARRIER)
  }

  @Test def aRaiseBeforeTheCooldownIsIgnored(): Unit = {
    val holder = m.join(CharacterId.Crusader, 20, 30)
    raise(m, holder)
    m.move(holder, 20, 30) // down early
    assertFalse(holder.hasBarrier)
    raise(m, holder)
    assertFalse("the cooldown counts from the raise", holder.hasBarrier)
    // At 80% of the 12s, the tolerance every other attack gets, it goes up again
    holder.setBarrierRaisedAt(System.currentTimeMillis() - (12000 * 0.8).toLong - 10)
    raise(m, holder)
    assertTrue(holder.hasBarrier)
  }

  @Test def anUpdateOvertakenOnTheWayCannotTakeItDown(): Unit = {
    // Datagrams overtake each other. The movement fence drops an update older than the last
    // position taken, but a position refused doesn't move it: when the raise came in on an update
    // whose position was refused, the one sent just before it — without the barrier — arriving
    // after it, took the barrier down, and the cooldown kept it down while its holder saw it up
    val holder = m.join(CharacterId.Crusader, 20, 30)
    m.move(holder, 20, 30)
    def update(seq: Int, x: Int, flags2: Int): Boolean = m.instance.handler.processPacket(
      new PlayerUpdatePacket(seq, holder.getId, Packet.getCurrentTimestamp, new Position(x, 30), holder.getColorRGB,
        holder.getHealth, 0, 0, holder.getCharacterId, holder.getTeamId, holder.getServerMoves, flags2,
        PlayerUpdatePacket.encodeAimAngle(East)), null, holder.getUdpAddress)
    assertFalse("a position no walk reaches is refused", update(1001, 34, BARRIER))
    assertTrue("but the barrier it carries goes up", holder.hasBarrier)
    assertTrue("the update sent before it, arriving after it, is taken", update(1000, 20, 0))
    assertTrue("but not its word on the barrier", holder.hasBarrier)
    update(1002, 20, 0)
    assertFalse("a newer one's still counts", holder.hasBarrier)
  }

  @Test def aCharacterWithoutOneCannotClaimOne(): Unit = {
    val soldier = m.join(CharacterId.Soldier, 20, 30)
    raise(m, soldier)
    assertFalse(soldier.hasBarrier)
  }

  @Test def firingDropsIt(): Unit = {
    val holder = m.join(CharacterId.Crusader, 20, 30)
    val watcher = m.join(CharacterId.Gladiator, 40, 40)
    raise(m, holder)
    m.clearSent()
    assertEquals(1, m.fire(holder, AttackSlot.PRIMARY, ProjectileType.HOLY_BLADE, Seq((1f, 0f))).size)
    assertFalse(holder.hasBarrier)
    assertTrue("and everyone is told", m.updatesAbout(m.udpSent(watcher), holder).exists(u => (u.getEffectFlags2 & BARRIER) == 0))
  }

  @Test def castingDropsIt(): Unit = {
    val holder = m.join(CharacterId.Crusader, 20, 30)
    raise(m, holder)
    assertEquals(1, m.fire(holder, AttackSlot.E, ProjectileType.SHOCKWAVE, Seq((0f, 0f))).size)
    assertFalse(holder.hasBarrier)
  }

  // --- Bots ---

  @Test def aBotRaisesItAgainstAGunItCannotReachAndDropsItToAttack(): Unit = {
    val bot = m.join(CharacterId.Crusader, 20, 30)
    val soldier = m.join(CharacterId.Soldier, 32, 30) // 12 cells: in a bullet's range, out of a blade's
    val bots = new BotController(m.instance)
    try {
      bots.addBotId(bot.getId)
      bots.tick()
      assertTrue(bot.hasBarrier)
      assertEquals("facing the soldier", 0.0, bot.getBarrierAngle, 0.15)
      m.tickUntilGone(m.launch(soldier, ProjectileType.BULLET, -1f, 0f))
      assertTrue("and it stops their shots", unhurt(bot))
      // Close enough to strike, it drops it to
      soldier.setPosition(new Position(bot.getPosition.getX + 3, bot.getPosition.getY))
      bots.tick()
      assertFalse(bot.hasBarrier)
    } finally bots.stop()
  }

  @Test def aBotRaisesItAgainstABoulderThrowerThatOutrangesIt(): Unit = {
    // A skirmisher: 8 cells of boulder against 5 of blade. Judged as ranged by a reach of 10 or
    // more, it never counted, and the barrier stayed down while the boulders came in
    val bot = m.join(CharacterId.Crusader, 20, 30)
    m.join(CharacterId.Golem, 27, 30)
    val bots = new BotController(m.instance)
    try {
      bots.addBotId(bot.getId)
      bots.tick()
      assertTrue(bot.hasBarrier)
    } finally bots.stop()
  }

  @Test def aBotLeavesItDownAgainstSomeoneWhoCannotShootItFromThere(): Unit = {
    val bot = m.join(CharacterId.Crusader, 20, 30)
    m.join(CharacterId.Gladiator, 32, 30) // throws axes 5 cells
    val bots = new BotController(m.instance)
    try {
      bots.addBotId(bot.getId)
      bots.tick()
      assertFalse(bot.hasBarrier)
    } finally bots.stop()
  }

  @Test def aBotDoesNotRaiseItAgainstSomeoneWhoCannotShootItThroughAWall(): Unit = {
    val t = new TestMatch()
    for (y <- 24 to 36) t.world.setTile(26, y, Tile.Wall)
    val bot = t.join(CharacterId.Crusader, 20, 30)
    t.join(CharacterId.Soldier, 32, 30) // a gun in range, but the wall is in the way
    val bots = new BotController(t.instance)
    try {
      bots.addBotId(bot.getId)
      bots.tick()
      assertFalse(bot.hasBarrier)
    } finally bots.stop()
  }

  @Test def aBotDoesNotRaiseItAgainstShotsThatFlyThroughIt(): Unit = {
    val bot = m.join(CharacterId.Crusader, 20, 30)
    m.join(CharacterId.Wizard, 32, 30) // arcane bolts fly over walls, and over barriers
    val bots = new BotController(m.instance)
    try {
      bots.addBotId(bot.getId)
      bots.tick()
      assertFalse(bot.hasBarrier)
    } finally bots.stop()
  }

  @Test def aBotDoesNotCastItsBarrierAwayAsSoonAsItIsUp(): Unit = {
    // The Gladiator's rope reaches 25 cells, and was thrown the moment the barrier went up — which
    // took the barrier straight down again, spent for nothing
    val bot = m.join(CharacterId.Gladiator, 20, 30)
    m.join(CharacterId.Soldier, 32, 30)
    val bots = new BotController(m.instance)
    try {
      bots.addBotId(bot.getId)
      bots.tick()
      assertTrue(bot.hasBarrier)
      assertTrue("nothing thrown", m.instance.projectileManager.getAll.forall(_.ownerId != bot.getId))
    } finally bots.stop()
  }

  @Test def aBotHoldsItUpRatherThanThrowAtAWall(): Unit = {
    // Stopped by a wall with its target just beyond it, in its axe's reach, a bot used to drop its
    // barrier and throw at the wall
    val t = new TestMatch()
    for (y <- 25 to 35) t.world.setTile(22, y, Tile.Wall)
    val bot = t.join(CharacterId.Gladiator, 20, 30)
    val soldier = t.join(CharacterId.Soldier, 20, 42) // 12 cells down the open side of the wall
    val bots = new BotController(t.instance)
    try {
      bots.addBotId(bot.getId)
      bots.tick()
      assertTrue(bot.hasBarrier)
      soldier.setPosition(new Position(24, 30)) // 4 cells off, the wall between them
      bots.tick()
      assertTrue("still up", bot.hasBarrier)
      assertTrue("and nothing thrown at the wall", t.instance.projectileManager.getAll.forall(_.ownerId != bot.getId))
      // In the open and in reach, it drops it to strike
      soldier.setPosition(new Position(bot.getPosition.getX, bot.getPosition.getY + 3))
      bots.tick()
      assertFalse(bot.hasBarrier)
      assertTrue(t.instance.projectileManager.getAll.exists(_.ownerId == bot.getId))
    } finally bots.stop()
  }

  @Test def aBotsTrapStopsAtABarrier(): Unit = {
    val holder = m.join(CharacterId.Crusader, 20, 30)
    val warden = m.join(CharacterId.Warden, 26, 30)
    raise(m, holder)
    val bots = new BotController(m.instance)
    try {
      bots.addBotId(warden.getId)
      bots.tick()
      val traps = m.instance.trapManager.getAll
      assertEquals("it lays one for the holder coming in", 1, traps.size)
      assertTrue(s"in front of the barrier, not under the holder: (${traps.head.x}, ${traps.head.y})", traps.head.x >= 23)
    } finally bots.stop()
  }

  @Test def itComesDownWithItsHolder(): Unit = {
    val holder = m.join(CharacterId.Crusader, 20, 30)
    raise(m, holder)
    assertTrue(holder.damage(holder.getHealth))
    assertFalse(holder.hasBarrier)
    m.instance.respawn(holder.getId)
    assertEquals("and a new life starts with it ready", 0L, holder.getBarrierRaisedAt)
  }
}
