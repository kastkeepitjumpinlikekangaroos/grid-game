package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.model.CharacterDef
import com.gridgame.common.model.CharacterId
import com.gridgame.common.model.Movement
import com.gridgame.common.model.Player
import com.gridgame.common.model.Position
import com.gridgame.common.model.WorldData
import com.gridgame.common.protocol.PlayerUpdatePacket
import org.junit.Assert._
import org.junit.Test

import java.util.UUID

/** Replay protection and the movement speed check. */
class PacketValidatorTest {
  private val v = new PacketValidator()
  private val id = UUID.randomUUID()

  private def udp(seq: Int): Boolean = v.validateSequence(id, seq, isUdp = true)
  private def tcp(seq: Int): Boolean = v.validateSequence(id, seq, isUdp = false)

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
    v.resetSequences(id)
    assertTrue(tcp(1))
    assertTrue(udp(1))
  }

  @Test def playersAreCountedApart(): Unit = {
    val other = UUID.randomUUID()
    assertTrue(tcp(100))
    assertTrue(v.validateSequence(other, 1, isUdp = false))
  }

  // --- The speed check ---

  @Test def theAllowanceIsTwiceTheCharactersOwnRatePlusTwoCells(): Unit = {
    // A second of walking at speed 1.0 is 20 cells; the allowance is 42
    assertEquals(42, PacketValidator.maxCellsIn(1000, 1.0f))
    assertEquals(22, PacketValidator.maxCellsIn(500, 1.0f))
    assertEquals("the grace with no time at all", 2, PacketValidator.maxCellsIn(0, 1.0f))
  }

  @Test def aFasterCharacterIsAllowedToBeFaster(): Unit = {
    // It used to be a cell per MOVE_RATE_LIMIT_MS for everyone, so a character quicker than 1.0
    // would have been refused for walking at the speed they were given
    assertEquals("twice the pace, twice the allowance", 82, PacketValidator.maxCellsIn(1000, 2.0f))
    assertEquals(52, PacketValidator.maxCellsIn(1000, 1.25f))
    assertEquals(42, PacketValidator.maxCellsIn(1000, 1.0f))
    assertEquals(22, PacketValidator.maxCellsIn(1000, 0.5f))
    // 25ms a cell at 2.0, 50 at 1.0, 100 at 0.5 — the rate Movement moves them at
    assertEquals(25.0, Movement.baseStepIntervalMs(2.0f), 0.0)
    assertEquals(100.0, Movement.baseStepIntervalMs(0.5f), 0.0)
  }

  /** A Gladiator takes one step to start the validator's clock, waits `gapMs`, then covers
    * `cells` more: does the validator let the second update through? */
  private def walk(validator: PacketValidator, cells: Int, gapMs: Long): Boolean = {
    val world = WorldData.createEmpty(60, 60)
    val p = new Player(UUID.randomUUID(), "walker", new Position(20, 20), 0)
    p.setCharacterId(CharacterId.Gladiator.id)
    def at(seq: Int, x: Int) = new PlayerUpdatePacket(seq, p.getId, 0, new Position(x, 20), 0, 100, 0, 0,
      p.getCharacterId, 0.toByte, 0)
    assertTrue("the first step", validator.validateMovement(at(1, 21), p, world))
    p.setPosition(new Position(21, 20))
    Thread.sleep(gapMs)
    validator.validateMovement(at(2, 21 + cells), p, world)
  }

  @Test def aFasterCharacterIsJudgedByItsOwnPace(): Unit = {
    // Ten cells in 110ms: at 2.0 the allowance is (110 / 25) * 2 + 2 = 10, at 1.0 it is 6, and
    // stays under 10 however late the second update is taken, up to 200ms. The check used to
    // allow a cell per MOVE_RATE_LIMIT_MS whoever was walking.
    val twiceAsQuick = new PacketValidator(id => CharacterDef.get(id).copy(moveSpeed = 2.0f))
    assertTrue("a character twice as quick", walk(twiceAsQuick, 10, 110))
    assertFalse("the Gladiator at the roster's 1.0", walk(new PacketValidator(), 10, 110))
  }

  @Test def aOneSpeedCharacterIsStillRefusedBeyondTheAllowance(): Unit = {
    val m = new TestMatch()
    val player = m.join(CharacterId.Gladiator, 20, 20) // a Gladiator walks at 1.0, as all of them do
    // Two updates a measurable gap apart: the first only starts the clock
    assertTrue(m.move(player, 21, 20, gapMs = 5))
    assertTrue("a step is fine", m.move(player, 22, 20, gapMs = 5))
    // A jump of 40 cells in a few milliseconds is beyond 2x + 2 however it is sliced, and the
    // Gladiator has neither a blink nor a dash to excuse it
    assertFalse("a 40-cell jump", m.move(player, 22, 60, gapMs = 5))
    assertEquals((22, 20), m.at(player))
  }
}
