package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.GameClock
import com.gridgame.common.model._
import com.gridgame.common.protocol._
import com.gridgame.server.game.PacketValidator
import org.junit.Assert._
import org.junit.Test

import java.util.UUID

/**
 * The edges of what the server takes from a client, each on the very cell or millisecond it turns:
 * the map's first row and column, the last cell of a step's credit, a shot from exactly as far off
 * as one may be fired, the corner of a fence too new to have been heard of, a fan sent as requests,
 * a rejoin. Each was found unpinned by putting the rule's line wrong and seeing no test fail
 * (src/test/CLAUDE.md, *Mutation testing*): the suites walked the middle of every rule and none
 * stood on its edge.
 */
class RequestEdgesTest {
  /** With what the server says of the requests it refuses kept off the test's output. */
  private def silently[T](body: => T): T = {
    var result: Option[T] = None
    ServerTestKit.captureStderr(ServerTestKit.quietly { result = Some(body) })
    result.get
  }

  // --- Steps ---

  @Test def theMapsFirstRowAndColumnArePartOfIt(): Unit = silently(Sim() { clock =>
    // Every map in worlds/ is walled round, so nothing ever stood on a map's edge: a check that
    // took column 0 or row 0 for off the map went unnoticed
    val m = new SimMatch(clock, WorldData.createEmpty(30, 30))
    val p = m.join(CharacterId.Soldier, 1, 1)
    assertTrue("onto the first column", m.move(p, 0, 1, gapMs = 60))
    assertTrue("along it", m.move(p, 0, 0, gapMs = 60))
    assertTrue("and the first row", m.move(p, 1, 0, gapMs = 60))
    val q = m.join(CharacterId.Soldier, 28, 28)
    assertTrue("onto the last column", m.move(q, 29, 28, gapMs = 60))
    assertTrue("and the last row", m.move(q, 29, 29, gapMs = 60))
    assertFalse("but not off the map", m.move(q, 30, 29, gapMs = 60))
    assertFalse(m.move(q, 29, 30, gapMs = 60))
    assertEquals((29, 29), m.at(q))
  })

  @Test def standingStillBanksASecondAndAHalfOfWalkingAndNoMore(): Unit = silently(Sim() { clock =>
    // The credit: twice a Soldier's pace and a quarter more (a cell every 20ms), for the network's
    // tolerance and a second at most, and a step may overdraw it by a diagonal's two cells. So
    // however long they stood, the furthest one update carries them is 75 cells and two
    val m = new SimMatch(clock, WorldData.createEmpty(200, 200))
    val window = (Constants.NETWORK_TOLERANCE_MS + 1000) / 20
    val p = m.join(CharacterId.Soldier, 10, 10)
    assertTrue("their first word: where they stand", m.move(p, 10, 10, gapMs = 0))
    clock.advance(60000)
    assertFalse("a cell further than the credit and its overdraft", m.move(p, 10 + window + 3, 10, gapMs = 0))
    assertTrue("the whole of it, to the cell", m.move(p, 10 + window + 2, 10, gapMs = 0))
    // Spent, and overdrawn: what comes back is what the time since has built, a cell every 20ms
    assertFalse("nothing left for another", m.move(p, 10 + window + 2, 13, gapMs = 0))
    clock.advance(100)
    assertFalse("5 cells earned, 2 owed, and an overdraft of 2 make 5 and no more", m.move(p, 10 + window + 2, 16, gapMs = 0))
    assertTrue(m.move(p, 10 + window + 2, 15, gapMs = 0))
  })

  @Test def aPlayersFirstUpdateMayCarryThemWhatTheToleranceAllowsAndNoFurther(): Unit = silently(Sim() { clock =>
    // Judged on the credit of the network's tolerance alone, as though no time had passed: 25
    // cells at a Soldier's pace, and the overdraft's two
    val m = new SimMatch(clock, WorldData.createEmpty(200, 200))
    val first = Constants.NETWORK_TOLERANCE_MS / 20
    val far = m.join(CharacterId.Soldier, 10, 10)
    clock.advance(30000)
    assertFalse(m.move(far, 10 + first + 3, 10, gapMs = 0))
    val near = m.join(CharacterId.Soldier, 10, 20)
    assertTrue(m.move(near, 10 + first + 2, 20, gapMs = 0))
  })

  @Test def aRefusedStepCostsNothingOfTheCredit(): Unit = silently(Sim() { clock =>
    // What had built up when a step was refused is still there for the next: dropped with the
    // refusal, an honest client that had one step refused had the ones after it refused too
    val m = new SimMatch(clock, WorldData.createEmpty(400, 400))
    val p = m.join(CharacterId.Soldier, 10, 10)
    assertTrue(m.move(p, 11, 10, gapMs = 0))
    clock.advance(1000) // 50 cells more
    assertFalse("the far side of the map", m.move(p, 300, 10, gapMs = 0))
    assertTrue("the credit the second built is still to spend", m.move(p, 11 + 70, 10, gapMs = 0))
  })

  @Test def aTrapPassedOverFromTheMapsFirstColumnOrRowIsSprung(): Unit = silently(Sim() { clock =>
    // A step of several cells springs what it passes over, and one that starts on column 0 or row 0
    // is a step like any other. The trap is out of reach of both ends of the step (a trap catches
    // its own cell and the four beside it): only the way between them springs it
    for ((from, trapAt, to) <- Seq(((0, 5), (2, 5), (4, 5)), ((5, 0), (5, 2), (5, 4)))) {
      val m = new SimMatch(clock, WorldData.createEmpty(30, 30))
      val trapper = m.join(CharacterId.Soldier, 20, 20)
      val p = m.join(CharacterId.Soldier, from._1, from._2)
      assertNotNull(m.armedTrap(trapper, trapAt._1, trapAt._2, TrapType.BEAR_TRAP))
      assertTrue("their first word", m.move(p, from._1, from._2, gapMs = 0))
      m.advance(400)
      assertEquals(s"out of reach of $from, it lies there still", 1, m.instance.trapManager.getAll.size)
      assertTrue(s"four cells at once, from $from", m.move(p, to._1, to._2, gapMs = 0))
      assertEquals(s"the trap at $trapAt, passed over from $from", 0, m.instance.trapManager.getAll.size)
      assertTrue("and it caught them", p.isFrozen)
    }
  })

  @Test def anUpdateFromSomeoneWhoIsNotInTheMatchIsNotPassedOn(): Unit = silently(Sim() { clock =>
    // What the match takes of a step it tells everyone else (GameServer relays what the handler
    // says to): nothing of somebody it doesn't have, who must join it first
    val m = new SimMatch(clock)
    m.join(CharacterId.Soldier, 30, 30)
    val stranger = UUID.randomUUID()
    assertFalse(m.instance.handler.processPacket(new PlayerUpdatePacket(1, stranger, Packet.getCurrentTimestamp, new Position(31, 30),
      0, 100, 0, 0, CharacterId.Soldier.id, 0.toByte, 0), null, null))
    assertNull(m.instance.registry.get(stranger))
  })

  @Test def aPlayerFacesTheWayTheyLastStepped(): Unit = silently(Sim() { clock =>
    // Which is what "behind them" means to an attack that lands its owner there, and which way a
    // fence they put down lies: up or down if the step went up or down at all, else left or right.
    // An update from the cell they stand on turns nobody
    val m = new SimMatch(clock, WorldData.createEmpty(60, 60))
    val p = m.join(CharacterId.Soldier, 30, 30)
    assertTrue(m.move(p, 30, 30, gapMs = 0))
    val steps = Seq((0, -1) -> Direction.Up, (-1, 0) -> Direction.Left, (0, 1) -> Direction.Down, (1, 0) -> Direction.Right,
      (1, -1) -> Direction.Up, (-1, 0) -> Direction.Left, (-1, -1) -> Direction.Up, (1, 0) -> Direction.Right,
      (1, 1) -> Direction.Down, (-1, 0) -> Direction.Left, (-1, 1) -> Direction.Down)
    for (((dx, dy), facing) <- steps) {
      val (x, y) = m.at(p)
      assertTrue(m.move(p, x + dx, y + dy, gapMs = 100))
      assertEquals(s"after a step of ${(dx, dy)}", facing, p.getDirection)
      assertTrue(m.move(p, x + dx, y + dy, gapMs = 100))
      assertEquals(s"and an update from where it left them", facing, p.getDirection)
    }
  })

  @Test def stepsThatKeepBeingRefusedAreCorrectedAQuarterSecondInAndEveryHalfSecondAfter(): Unit = silently(Sim() { clock =>
    // A client that thinks it is somewhere the server doesn't have it is told where it is, as a
    // server move: not for a lone refusal (a race the server catches up with), but once they have
    // kept coming for 250ms, and then every 500ms for as long as they do
    val m = new SimMatch(clock, WorldData.createEmpty(100, 100))
    val p = m.join(CharacterId.Soldier, 10, 10)
    assertTrue("their first word", m.move(p, 10, 10, gapMs = 0))
    def correctedAt(everyMs: Long, forMs: Long)(step: Long => Boolean): Seq[Long] = {
      val from = m.at
      val corrected = scala.collection.mutable.Buffer[Long]()
      var t = 0L
      while (t <= forMs) {
        val before = p.getServerMoves
        step(t)
        if (p.getServerMoves != before) corrected += t
        m.advance(everyMs)
        t = m.at - from
      }
      corrected.toSeq
    }
    // Somewhere no network could have carried them, every 50ms, having heard each correction
    assertEquals("a refusal every 50ms", Seq(250L, 750L, 1250L, 1750L),
      correctedAt(50, 2000)(_ => { assertFalse(m.move(p, 90, 90, gapMs = 0)); true }))
    assertEquals("still where the server had them", (10, 10), m.at(p))
    // A step the server takes ends the run: the next refusal is the first of another
    assertTrue(m.move(p, 11, 10, gapMs = 1000))
    assertEquals("an honest step between every two refusals, 200ms apart", Seq.empty[Long],
      correctedAt(100, 3000)(t => if (t % 200 == 0) m.move(p, 90, 90, gapMs = 0) else m.move(p, 11 + (t % 400 / 300).toInt, 10, gapMs = 0)))
    // And nobody is corrected for steps that were taken
    val honest = m.join(CharacterId.Soldier, 50, 50)
    assertTrue(m.move(honest, 50, 50, gapMs = 0))
    for (i <- 1 to 30) assertTrue(m.move(honest, 50 + i, 50, gapMs = 100))
    assertEquals("an honest walker's server moves", 0, honest.getServerMoves)
  })

  @Test def aPlayersDatagramsFollowTheAddressTheirStepsComeFrom(): Unit = silently(Sim() { clock =>
    // A home router gives a connection another port whenever it likes: the server sends a player's
    // datagrams to wherever the last step it took from them came from (the address is the one
    // their session logged in from, which the UDP handler has checked: NetworkHandlerTest)
    val m = new SimMatch(clock)
    val p = m.join(CharacterId.Soldier, 30, 30)
    val was = p.getUdpAddress
    def step(x: Int, from: java.net.InetSocketAddress): Boolean = m.instance.handler.processPacket(
      new PlayerUpdatePacket(1000 + x, p.getId, Packet.getCurrentTimestamp, new Position(x, 30), p.getColorRGB, p.getHealth, 0, 0,
        p.getCharacterId, p.getTeamId, p.getServerMoves), null, from)
    assertTrue(step(30, was))
    val now = new java.net.InetSocketAddress(was.getAddress, was.getPort + 1)
    clock.advance(100)
    assertTrue(step(31, now))
    assertEquals("their steps come from another port now", now, p.getUdpAddress)
    clock.advance(100)
    assertTrue("one that came with no address (by TCP) says nothing of it", step(32, null))
    assertEquals(now, p.getUdpAddress)
  })

  @Test def aStepOntoANewFenceIsTakenForTheNetworksToleranceAfterItWentUpAndNotAMillisecondMore(): Unit = silently(Sim() { clock =>
    // A fence put down where someone is stepping: their client sent the step knowing nothing of it,
    // for as long as its tile update can be on the way (NETWORK_TOLERANCE_MS). After that a step
    // onto a fence is a step into a wall
    def stepOntoIt(msAfter: Long): Boolean = {
      val m = new SimMatch(clock, WorldData.createEmpty(60, 60))
      val builder = m.join(CharacterId.Soldier, 30, 30)
      val walker = m.join(CharacterId.Soldier, 30, 35)
      assertTrue(m.move(walker, 30, 35, gapMs = 0))
      m.advance(1000)
      m.useItem(builder, ItemType.Fence, 30, 34) // (29..31, 34), a step north of the walker
      assertEquals(Tile.Fence, m.world.getTile(30, 34))
      clock.advance(msAfter)
      m.move(walker, 30, 34, gapMs = 0)
    }
    assertTrue("as it goes up", stepOntoIt(0))
    assertTrue("the tolerance's last millisecond", stepOntoIt(Constants.NETWORK_TOLERANCE_MS - 1))
    assertFalse("and not one more", stepOntoIt(Constants.NETWORK_TOLERANCE_MS))
  })

  @Test def aDiagonalPastTheCornerOfAFenceTooNewToHaveBeenHeardOfIsTaken(): Unit = {
    // Whichever of the two cells beside the step the fence went up on
    for (corner <- Seq((21, 20), (20, 21))) {
      val world = WorldData.createEmpty(60, 60)
      val p = new Player(UUID.randomUUID(), "walker", new Position(20, 20), 0)
      p.setCharacterId(CharacterId.Soldier.id)
      def update(seq: Int, x: Int, y: Int) = new PlayerUpdatePacket(seq, p.getId, 0, new Position(x, y), 0, 100, 0, 0,
        p.getCharacterId, 0.toByte, 0)
      world.setTile(corner._1, corner._2, Tile.Fence)
      val heardOf = new PacketValidator()
      assertTrue(heardOf.validateMovement(update(1, 20, 20), p, world))
      assertFalse(s"past the corner of the fence at $corner, long since heard of", silently(heardOf.validateMovement(update(2, 21, 21), p, world)))
      val tooNew = new PacketValidator()
      assertTrue(tooNew.validateMovement(update(1, 20, 20), p, world))
      assertTrue(s"past the corner of the fence at $corner, too new to have been heard of",
        tooNew.validateMovement(update(2, 21, 21), p, world, changedLately = (x, y) => (x, y) == corner))
    }
  }

  // --- Shots ---

  /** A spawn request of the player's primary, said to be fired from (x, y) heading +x: how many
    * projectiles the server spawned for it. */
  private def fireFrom(m: TestMatch, p: Player, x: Float, y: Float, seq: Int): Int = {
    val before = m.instance.projectileManager.size
    m.instance.handler.processPacket(PacketSerializer.deserialize(ProjectilePacket.spawnRequest(seq, p.getId, x, y, p.getColorRGB,
      1f, 0f, 0.toByte, CharacterDef.get(p.getCharacterId).primaryProjectileType, AttackSlot.PRIMARY).serialize()), null, null)
    m.instance.projectileManager.size - before
  }

  @Test def aShotIsTakenFromExactlyAsFarOffAsOneMayBeFired(): Unit = silently(Sim() { clock =>
    // Three cells, and as far again as a Soldier could have walked in the network's tolerance at
    // their quickest (a cell every 25ms: twenty): the same either way along both axes
    val m = new SimMatch(clock, WorldData.createEmpty(100, 100))
    val reach = 3 + Constants.NETWORK_TOLERANCE_MS / 25
    val p = m.join(CharacterId.Soldier, 50, 50)
    var seq = 0
    for ((dx, dy) <- Seq((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1))) {
      seq += 1
      assertEquals(s"from a cell past the reach, ${(dx, dy)}", 0, fireFrom(m, p, 50f + dx * (reach + 1), 50f + dy * (reach + 1), seq))
      seq += 1
      assertEquals(s"from the reach's last cell, ${(dx, dy)}", 1, fireFrom(m, p, 50f + dx * reach, 50f + dy * reach, seq))
      m.instance.handler.resetAttacks(p.getId)
    }
  })

  @Test def aFanSentAsRequestsIsOneCast(): Unit = silently(Sim() { clock =>
    // Everything one press fires pushes a player once (Cast), and the server puts a fan together
    // from the requests it arrives as: each of them its own cast, a fan pushed once a projectile
    val m = new SimMatch(clock)
    val (c, fan) = CharacterDef.all.flatMap(c => Seq(c.qAbility, c.eAbility).collectFirst {
      case a if a.castBehavior.isInstanceOf[FanProjectile] => (c, a)
    }).head
    val slot = if (fan eq c.qAbility) AttackSlot.Q else AttackSlot.E
    val p = m.join(c.id, 30, 30)
    val shape = fan.castBehavior.asInstanceOf[FanProjectile]
    val headings = (0 until shape.count).map { i => val a = shape.angleOf(i); (Math.cos(a).toFloat, Math.sin(a).toFloat) }
    val first = m.fire(p, slot, fan.projectileType, headings)
    assertEquals(shape.count, first.size)
    assertEquals(s"${c.displayName}'s ${fan.name}: one cast for the fan", 1, first.map(_.cast).distinct.size)
    assertNotNull(first.head.cast)
    assertEquals("fired from where they stand", (30f, 30f), (first.head.cast.fromX, first.head.cast.fromY))
    clock.advance(fan.cooldownMs)
    val second = m.fire(p, slot, fan.projectileType, headings)
    assertEquals(shape.count, second.size)
    assertEquals(1, second.map(_.cast).distinct.size)
    assertNotSame("and the next press another", first.head.cast, second.head.cast)
    // The primary beside it is a cast of its own
    val shot = m.fire(p, AttackSlot.PRIMARY, c.primaryProjectileType, Seq((1f, 0f)))
    assertNotSame(second.head.cast, shot.head.cast)
    // And the three a gem makes of a primary are one cast, as a fan's are
    clock.advance(Constants.SHOOT_COOLDOWN_MS)
    val spread = Seq(0.0, 5.0, -5.0).map { deg => val a = Math.toRadians(deg); (Math.cos(a).toFloat, Math.sin(a).toFloat) }
    val three = m.fire(p, AttackSlot.PRIMARY, c.primaryProjectileType, spread)
    assertEquals(3, three.size)
    assertEquals("a primary's three: one cast", 1, three.map(_.cast).distinct.size)
    assertNotNull(three.head.cast)
    assertNotSame(shot.head.cast, three.head.cast)
  })

  @Test def aCharacterTheServerDoesNotKnowFiresNothing(): Unit = {
    // Nobody is in a match as one (a lobby takes only the roster's), so nothing is known of what
    // they fire: refused, not judged as anyone
    val nobodyKnown = new PacketValidator(characterOf = _ => null)
    val p = new Player(UUID.randomUUID(), "stranger", new Position(20, 20), 0)
    p.setCharacterId(CharacterId.Soldier.id)
    val shot = PacketSerializer.deserialize(ProjectilePacket.spawnRequest(1, p.getId, 20f, 20f, 0, 1f, 0f, 0.toByte,
      CharacterDef.get(CharacterId.Soldier.id).primaryProjectileType, AttackSlot.PRIMARY).serialize()).asInstanceOf[ProjectilePacket]
    assertFalse(silently(nobodyKnown.validateProjectileSpawn(shot, p)))
    assertTrue("the same shot, of a character it knows", new PacketValidator().validateProjectileSpawn(shot, p))
  }

  // --- A dash ---

  @Test def aDashsStepsGoOverWallsForTheNetworksToleranceAfterItsPhaseAndNotAMillisecondMore(): Unit = silently(Sim() { clock =>
    // A dash's step sent near its end can arrive after the server's phase is over (the server's
    // began a trip across the wire late), still over the wall the dash crosses: taken for the
    // network's tolerance after, and after that a step into a wall like anyone's
    val world = WorldData.createEmpty(60, 60)
    world.setTile(21, 20, Tile.Wall)
    def stepIntoTheWall(msAfterThePhase: Long): Boolean = {
      val v = new PacketValidator()
      val p = new Player(UUID.randomUUID(), "dasher", new Position(20, 20), 0)
      p.setCharacterId(CharacterId.Valkyrie.id)
      def update(seq: Int, x: Int, y: Int) = new PlayerUpdatePacket(seq, p.getId, 0, new Position(x, y), 0, 100, 0, 0,
        p.getCharacterId, 0.toByte, 0)
      assertTrue(v.validateMovement(update(1, 20, 20), p, world))
      p.setPhasedUntil(clock.millis() - msAfterThePhase)
      v.validateMovement(update(2, 21, 20), p, world)
    }
    assertTrue("as its phase ends", stepIntoTheWall(0))
    assertTrue("the tolerance's last millisecond", stepIntoTheWall(Constants.NETWORK_TOLERANCE_MS - 1))
    assertFalse("and not one more", stepIntoTheWall(Constants.NETWORK_TOLERANCE_MS))
  })

  // --- A rejoin ---

  @Test def aRejoinedClientsWordOnItsBarrierIsTakenThoughItCountsFromOneAgain(): Unit = silently(Sim() { clock =>
    // A client that has just reconnected numbers its packets from the start, and has forgotten the
    // barrier it held: its first update, saying none, has to be heard
    val m = new SimMatch(clock)
    val p = m.join(CharacterId.Gladiator, 30, 30)
    def update(seq: Int, raised: Boolean): Unit = m.instance.handler.processPacket(
      new PlayerUpdatePacket(seq, p.getId, Packet.getCurrentTimestamp, new Position(30, 30), p.getColorRGB, p.getHealth, 0, 0,
        p.getCharacterId, p.getTeamId, p.getServerMoves, if (raised) 0x04 else 0, PlayerUpdatePacket.encodeAimAngle(0.0)),
      null, p.getUdpAddress)
    update(500, raised = true)
    assertTrue(p.hasBarrier)
    update(400, raised = false)
    assertTrue("an older word than the raise is no word", p.hasBarrier)
    m.rejoin(p)
    update(1, raised = false)
    assertFalse("the rejoined client's first word is heard", p.hasBarrier)
  })
}
