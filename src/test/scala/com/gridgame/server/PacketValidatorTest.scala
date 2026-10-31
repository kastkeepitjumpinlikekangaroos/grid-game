package com.gridgame.server

import com.gridgame.server.game.PacketValidator
import com.gridgame.server.net.ReplayGuard
import com.gridgame.common.Constants
import com.gridgame.common.model.CharacterDef
import com.gridgame.common.model.CharacterId
import com.gridgame.common.model.Movement
import com.gridgame.common.model.Player
import com.gridgame.common.model.Position
import com.gridgame.common.model.Tile
import com.gridgame.common.model.WorldData
import com.gridgame.common.protocol.PlayerUpdatePacket
import org.junit.Assert._
import org.junit.Test

import java.util.UUID

/** Replay protection (ReplayGuard) and the movement speed check (PacketValidator). */
class PacketValidatorTest {
  private val v = new PacketValidator()
  private val replay = new ReplayGuard()
  private val id = UUID.randomUUID()

  private def udp(seq: Int): Boolean = replay.validateSequence(id, seq, isUdp = true)
  private def tcp(seq: Int): Boolean = replay.validateSequence(id, seq, isUdp = false)

  @Test def tcpMustAlwaysMoveForward(): Unit = {
    assertTrue(tcp(1))
    assertTrue(tcp(5))
    assertFalse("a replay", tcp(5))
    assertFalse("from before", tcp(3))
    assertTrue(tcp(6))
  }

  @Test def udpMayArriveOutOfOrderButNotTwice(): Unit = {
    assertTrue(udp(10))
    assertTrue(udp(12))
    assertTrue("late, but new", udp(11))
    assertFalse("a replay", udp(11))
    assertFalse(udp(12))
  }

  @Test def udpFromTooFarBehindIsDropped(): Unit = {
    assertTrue(udp(5000))
    assertFalse(udp(5000 - Constants.SEQUENCE_WINDOW_SIZE - 10))
    assertTrue("within the window", udp(5000 - 10))
  }

  @Test def udpAndTcpAreCountedApart(): Unit = {
    // One counter on the client, two transports: UDP can run ahead of TCP
    assertTrue(udp(50))
    assertTrue(tcp(20))
    assertTrue(udp(21))
  }

  @Test def sequencesWrapAround(): Unit = {
    assertTrue(tcp(Int.MaxValue - 1))
    assertTrue(tcp(0x7FFFFFFF))
    assertTrue("past the wrap", tcp(0x80000000 & 0x7FFFFFFF))
    // A session's UDP count starts from zero and walks up to the wrap
    for (s <- Seq(0x2AAAAAAA, 0x55555554, 0x7FFFFFFE)) assertTrue(f"0x$s%x", udp(s))
    assertTrue("past the wrap", udp(5))
    assertFalse("and the far side of it is behind", udp(0x7FFFFFFE))
  }

  @Test def aNewSessionCountsAfresh(): Unit = {
    assertTrue(tcp(300))
    assertTrue(udp(300))
    replay.resetSequences(id)
    assertTrue(tcp(1))
    assertTrue(udp(1))
  }

  @Test def playersAreCountedApart(): Unit = {
    val other = UUID.randomUUID()
    assertTrue(tcp(100))
    assertTrue(replay.validateSequence(other, 1, isUdp = false))
  }

  // --- The pace check: movement credit ---

  @Test def theCreditBuildsAtTwiceThePaceThePlayerStepsAtNow(): Unit = {
    // Speed 1.0 steps every 50ms: two cells a step at the most (a diagonal), and a quarter more
    assertEquals(0.05, PacketValidator.creditRate(1.0f), 1e-9)
    assertEquals("phased, twice the pace", 0.1, PacketValidator.creditRate(1.0f, phased = true), 1e-9)
    assertEquals("sped up, a step every 30ms", 2.0 / 30 * 1.25, PacketValidator.creditRate(1.0f, speedBoost = true), 1e-9)
  }

  @Test def aFasterCharacterIsAllowedToBeFaster(): Unit = {
    // It used to be a cell per MOVE_RATE_LIMIT_MS for everyone, so a character quicker than 1.0
    // would have been refused for walking at the speed they were given
    assertEquals("twice the pace, twice the credit", 0.1, PacketValidator.creditRate(2.0f), 1e-9)
    assertEquals(0.025, PacketValidator.creditRate(0.5f), 1e-9)
    // 25ms a cell at 2.0, 50 at 1.0, 100 at 0.5 — the rate Movement moves them at
    assertEquals(25.0, Movement.baseStepIntervalMs(2.0f), 0.0)
    assertEquals(100.0, Movement.baseStepIntervalMs(0.5f), 0.0)
  }

  /** A player on an open map, and their updates as their client numbers them: `take` is an update
    * the server takes as StepRequests does (moved, and the movement fence after it), or refuses. */
  private class Walker(character: CharacterId, x: Int = 20, y: Int = 20, validator: PacketValidator = new PacketValidator(),
                       val world: WorldData = WorldData.createEmpty(400, 400)) {
    val p = new Player(UUID.randomUUID(), "walker", new Position(x, y), 0)
    p.setCharacterId(character.id)
    private var seq = 0
    def skip(n: Int = 1): Unit = seq += n
    def take(tx: Int, ty: Int): Boolean = {
      seq += 1
      val ok = validator.validateMovement(new PlayerUpdatePacket(seq, p.getId, 0, new Position(tx, ty), 0, 100, 0, 0,
        p.getCharacterId, 0.toByte, 0), p, world)
      if (ok) { p.setPosition(new Position(tx, ty)); validator.movementApplied(p.getId, seq) }
      ok
    }
  }

  @Test def stepsABadNetworkBunchedUpAreTakenOnTheTimeTheyWereWalkedIn(): Unit = {
    // A second of walking with the steps all arriving at once, in order
    val bunched = new Walker(CharacterId.Soldier)
    for (_ <- 1 to 4) { assertTrue(bunched.take(bunched.p.getPosition.getX + 1, 20)); Thread.sleep(50) }
    Thread.sleep(600) // held up on the way
    for (i <- 1 to 12) assertTrue(s"bunched step $i", bunched.take(bunched.p.getPosition.getX + 1, 20))
  }

  @Test def aStepThatArrivesStraightAfterAHeldUpOneIsTakenThoughThoseBetweenWereLost(): Unit = {
    // One update held up on the way, and the next to arrive close behind it, the three sent between
    // them lost: four cells on in no time at all. Taken on the credit the walk built up. It used to
    // be judged on the time between the two arrivals, two cells, and refused as a speed hack, and
    // the player snapped back
    val w = new Walker(CharacterId.Soldier)
    for (_ <- 1 to 6) { assertTrue(w.take(w.p.getPosition.getX + 1, 20)); Thread.sleep(50) }
    Thread.sleep(150)
    assertTrue("the held-up step", w.take(w.p.getPosition.getX + 1, 20))
    w.skip(3)
    assertTrue("four cells on, straight after it", w.take(w.p.getPosition.getX + 4, 20))
  }

  /** Ten updates 50ms apart, each `cells` further on: how many the validator takes. */
  private def keepUp(validator: PacketValidator, cells: Int): Int = {
    val w = new Walker(CharacterId.Soldier, validator = validator)
    (1 to 10).count { _ =>
      val ok = w.take(w.p.getPosition.getX + cells, 20)
      Thread.sleep(50)
      ok
    }
  }

  @Test def aFasterCharacterIsJudgedByItsOwnPace(): Unit = {
    // Six cells every 50ms: within twice the pace of a character of speed 2.0, kept up all the way;
    // three times the Soldier's, whose credit is gone within half a second
    val twiceAsQuick = new PacketValidator(id => CharacterDef.get(id).copy(moveSpeed = 2.0f))
    assertEquals("a character twice as quick", 10, keepUp(twiceAsQuick, 6))
    assertTrue("the Soldier at a ranged character's 1.0", keepUp(new PacketValidator(), 6) < 9)
  }

  @Test def aDiagonalStepPastAWallsCornerIsRefused(): Unit = {
    // As the client and the bots take one: between open cells only
    val w = new Walker(CharacterId.Soldier)
    w.world.setTile(21, 20, Tile.Wall)
    assertTrue("standing", w.take(20, 20))
    assertFalse("past the wall's corner", w.take(21, 21))
    // A phase walked through walls, and their corners with them
    w.p.setPhasedUntil(System.currentTimeMillis() + 5000)
    assertFalse("phased, past it as well", w.take(21, 21))
    w.p.setPhasedUntil(0)
    assertTrue("the other way, past none", w.take(19, 21))
  }

  @Test def aDiagonalAfterALostDatagramIsNotJudgedByTheWayTheServerNeverSaw(): Unit = {
    // Two steps round a corner, the first of them lost: from where the server last had the player
    // the second looks a diagonal past it. Only an update that follows the last one the server took
    // is judged so; it used to be refused, and the player snapped back from a corner they had walked
    val w = new Walker(CharacterId.Soldier)
    w.world.setTile(20, 21, Tile.Wall)
    assertTrue(w.take(20, 20))
    w.skip() // (21, 20), lost
    assertTrue("(21, 21) by way of (21, 20)", w.take(21, 21))
  }

  @Test def aDashsLandingIsTakenPastACornerHoweverLateItComes(): Unit = {
    // Its client sends the landing as the dash's phase ends, which is after the server's has (its
    // phase began a trip across the wire later), or long after if a frame hitched: the first update
    // after the dash, from the cell its last step left the player on, is still the dash's
    def walled(ch: CharacterId, x: Int, y: Int): Walker = {
      val w = new Walker(ch, x, y)
      w.world.setTile(21, 20, Tile.Wall)
      w.world.setTile(22, 21, Tile.Wall)
      w
    }
    // Waited out past the tail a dash's own steps have (aDashsStepsStillGoOverWallsJustAfterItsPhase)
    val afterTheTail = Constants.NETWORK_TOLERANCE_MS + 100L
    val dasher = walled(CharacterId.Valkyrie, 19, 19)
    dasher.p.setPhasedUntil(System.currentTimeMillis() + 40)
    assertTrue("the dash's last step", dasher.take(20, 20))
    Thread.sleep(afterTheTail) // a hitch: the phase long over
    assertTrue("its landing, past the corner", dasher.take(21, 21))
    assertFalse("but the step after it is a step", dasher.take(22, 22))
    // Only into the open: nothing lands in a wall once the dash is over
    val intoAWall = walled(CharacterId.Valkyrie, 19, 19)
    intoAWall.p.setPhasedUntil(System.currentTimeMillis() + 40)
    assertTrue(intoAWall.take(20, 20))
    Thread.sleep(afterTheTail)
    assertFalse("into a wall", intoAWall.take(21, 20))
    // Nor from anywhere but where the dash left them: a knockback cut it short
    val knocked = walled(CharacterId.Valkyrie, 19, 19)
    knocked.p.setPhasedUntil(System.currentTimeMillis() + 40)
    assertTrue(knocked.take(20, 21))
    knocked.p.setPosition(new Position(20, 20)) // moved by the server, not by the dash
    Thread.sleep(afterTheTail)
    assertFalse("from where the server put them", knocked.take(21, 21))
    // And only a dash's: a phase shift's last step is a step
    val phaser = walled(CharacterId.Wraith, 20, 20)
    assertTrue(phaser.take(20, 20))
    phaser.p.setPhasedUntil(System.currentTimeMillis() - 20)
    assertFalse("a phase shift's last step", phaser.take(21, 21))
  }

  @Test def aDashsStepsStillGoOverWallsJustAfterItsPhase(): Unit = {
    // The server's phase began when the dash's first update arrived; on a bad link a step sent near
    // the dash's end can arrive after it is over, still over the wall the dash crosses. A dash's
    // steps go over walls for the network's tolerance after its phase; a phase shift's never do.
    val w = new Walker(CharacterId.Valkyrie, 19, 20)
    w.world.setTile(20, 20, Tile.Wall)
    w.p.setPhasedUntil(System.currentTimeMillis() - 100)
    assertTrue("a step over the wall, late", w.take(20, 20))
    val phaser = new Walker(CharacterId.Wraith, 19, 20)
    phaser.world.setTile(20, 20, Tile.Wall)
    phaser.p.setPhasedUntil(System.currentTimeMillis() + 2000)
    assertFalse("a phase shift's, never", phaser.take(20, 20))
  }

  @Test def aPlayerAFenceWentUpOnIsNotRefusedForStandingThere(): Unit = {
    // Nothing stops a fence going up where a player stands, and their barrier, a charge or a phase
    // goes on sending updates from that cell: not steps onto a fence. Stepping into one still is.
    val world = WorldData.createEmpty(60, 60)
    val p = new Player(UUID.randomUUID(), "caged", new Position(20, 20), 0)
    p.setCharacterId(CharacterId.Soldier.id)
    def to(seq: Int, x: Int, y: Int) = new PlayerUpdatePacket(seq, p.getId, 0, new Position(x, y), 0, 100, 0, 0,
      p.getCharacterId, 0.toByte, 0)
    val v = new PacketValidator()
    world.setTile(20, 20, Tile.Fence)
    world.setTile(21, 20, Tile.Fence)
    assertTrue("standing where it went up", v.validateMovement(to(1, 20, 20), p, world))
    assertFalse("but not into the next one", v.validateMovement(to(2, 21, 20), p, world))
    assertTrue("and out of it", v.validateMovement(to(3, 19, 20), p, world))
  }

  @Test def aStepOntoAFenceTheClientCouldNotHaveHeardOfIsTaken(): Unit = {
    // A fence went up where the player was stepping, too lately for their client to have heard (on a
    // bad network its tile update is still on the way): a step onto it, or past its corner, was sent
    // knowing nothing of it. Taken, where it used to be refused, and the player snapped back. One the
    // client had time to hear of is still refused.
    val w = new Walker(CharacterId.Soldier)
    var lately = Set((21, 20), (20, 21))
    def take(x: Int, y: Int, seq: Int) = new PacketValidator().validateMovement(
      new PlayerUpdatePacket(seq, w.p.getId, 0, new Position(x, y), 0, 100, 0, 0, w.p.getCharacterId, 0.toByte, 0),
      w.p, w.world, changedLately = (cx, cy) => lately.contains((cx, cy)))
    w.world.setTile(21, 20, Tile.Fence)
    w.world.setTile(20, 21, Tile.Fence)
    assertTrue("onto it", take(21, 20, 1))
    assertTrue("between it and another", take(21, 21, 1))
    lately = Set.empty
    assertFalse("heard of by now", take(21, 20, 1))
  }

  @Test def aPlayersFirstUpdateInAMatchIsCheckedToo(): Unit = {
    // Nothing had started the clock, so the first update was let through unchecked: a client's
    // first could put its player anywhere on the map, once a match. It is judged from where the
    // server put them, as though no time had passed: a step, or two bunched by a lost datagram.
    val m = new TestMatch()
    val jumper = m.join(CharacterId.Soldier, 10, 10)
    Thread.sleep(50) // however long they stood at the spawn first
    assertFalse("40 cells off", m.move(jumper, 50, 50))
    assertEquals((10, 10), m.at(jumper))
    val walker = m.join(CharacterId.Soldier, 20, 20)
    assertTrue("a step", m.move(walker, 21, 20))
    val lagged = m.join(CharacterId.Soldier, 30, 20)
    assertTrue("two steps, the first lost on the way", m.move(lagged, 32, 20))
    val blinker = m.join(CharacterId.Wizard, 40, 20)
    assertTrue("a blink is still a blink", m.move(blinker, 46, 20))
  }

  @Test def aOneSpeedCharacterIsStillRefusedBeyondTheAllowance(): Unit = {
    val m = new TestMatch()
    val player = m.join(CharacterId.Soldier, 20, 20) // a Soldier walks at 1.0, as every ranged character does
    // Two updates a measurable gap apart: the first only starts the clock
    assertTrue(m.move(player, 21, 20, gapMs = 5))
    assertTrue("a step is fine", m.move(player, 22, 20, gapMs = 5))
    // A jump of 40 cells in a few milliseconds is beyond 2x + 2 however it is sliced, and the
    // Soldier has neither a blink nor a dash to excuse it
    assertFalse("a 40-cell jump", m.move(player, 22, 60, gapMs = 5))
    assertEquals((22, 20), m.at(player))
  }
}
