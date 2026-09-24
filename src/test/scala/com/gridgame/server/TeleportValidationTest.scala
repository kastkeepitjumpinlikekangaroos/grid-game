package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.model._
import com.gridgame.common.protocol._
import io.netty.buffer.ByteBuf
import io.netty.channel.embedded.EmbeddedChannel
import org.junit.Assert._
import org.junit.Test

import java.util.UUID

object TeleportValidationTest {
  // Building a GameServer generates a TLS certificate and opens the auth database, so the tests
  // share one. It is never started, so it holds no sockets; each test gets its own GameInstance.
  lazy val server = new GameServer(0)
}

/**
 * The packet sequences a real client produces around a star or a blink, fed to the server's
 * ClientHandler in the orders they can arrive in. A star used to snap the player back: the
 * server refused teleports the client had already shown, a step sent just before the star could
 * land after it and put the player back, and a pair of packets in one millisecond went unchecked,
 * which hid both most of the time.
 */
class TeleportValidationTest {
  import TeleportValidationTest.server

  private val instance = {
    val gi = new GameInstance(1.toShort, "", 5, server)
    gi.world = WorldData.createEmpty(60, 60)
    gi
  }
  private var nextItemId = 1000

  private def join(character: CharacterId, x: Int, y: Int, seq: Int): Player = {
    val p = new Player(UUID.randomUUID(), "p", new Position(x, y), 0xFF00FF00, 100, 100)
    p.setCharacterId(character.id)
    instance.registry.add(p)
    move(p, seq, x, y) // the validator times moves from the first update
    p
  }

  /** A position update, as sent over UDP. Returns whether the server accepted it. */
  private def move(p: Player, seq: Int, x: Int, y: Int, gapMs: Long = 2): Boolean = {
    if (gapMs > 0) Thread.sleep(gapMs)
    instance.handler.processPacket(
      new PlayerUpdatePacket(seq, p.getId, new Position(x, y), p.getColorRGB, 100, 0, 0, p.getCharacterId),
      null, null)
  }

  /** Use a star aimed at (x, y): the item packet, as sent over TCP. */
  private def useStar(p: Player, seq: Int, x: Int, y: Int): Item = {
    nextItemId += 1
    val star = new Item(nextItemId, 0, 0, ItemType.Star)
    instance.itemManager.addToInventory(p.getId, star)
    instance.handler.processPacket(
      new ItemPacket(seq, p.getId, x, y, ItemType.Star.id, star.id, ItemAction.USE), null, null)
    star
  }

  private def at(p: Player): (Int, Int) = (p.getPosition.getX, p.getPosition.getY)

  private def hasItem(p: Player, item: Item): Boolean =
    instance.itemManager.getInventory(p.getId).exists(_.id == item.id)

  /** Packets the server sent the player over TCP. */
  private def sentOverTcp(ch: EmbeddedChannel): Seq[Packet] =
    Iterator.continually(ch.readOutbound[ByteBuf]()).takeWhile(_ != null).map { buf =>
      val bytes = new Array[Byte](Constants.PACKET_PAYLOAD_SIZE)
      buf.readBytes(bytes)
      buf.release()
      PacketSerializer.deserialize(bytes)
    }.toSeq

  @Test
  def aStarMovesThePlayerOnTheServer(): Unit = {
    val p = join(CharacterId.Spaceman, 10, 10, seq = 1)
    val star = useStar(p, 2, 30, 10)
    assertEquals((30, 10), at(p))
    assertFalse(hasItem(p, star))
    assertTrue("first step from the new cell", move(p, 3, 31, 10))
  }

  @Test
  def aMoveSentBeforeTheStarCannotPullThePlayerBack(): Unit = {
    val p = join(CharacterId.Spaceman, 10, 10, seq = 1)
    assertTrue(move(p, 2, 11, 10))
    // seq 3 is a step sent just before the star, still in flight when the star lands
    useStar(p, 4, 30, 10)
    assertTrue(move(p, 5, 31, 10))
    assertFalse("late step from before the star", move(p, 3, 12, 10))
    assertEquals((31, 10), at(p))
    assertTrue(move(p, 6, 32, 10))
  }

  @Test
  def aMoveSentBeforeTheStarCannotPullThePlayerBackEvenWithinABlinksReach(): Unit = {
    // For a blink character the late step passes the distance check, so only its sequence
    // number shows it's from before the star
    val p = join(CharacterId.Phoenix, 20, 20, seq = 1)
    assertTrue(move(p, 2, 21, 20))
    useStar(p, 4, 30, 20)
    assertFalse("late step from before the star", move(p, 3, 22, 20))
    assertEquals((30, 20), at(p))
  }

  @Test
  def aBlinkIsNotUndoneByAStepSentBeforeIt(): Unit = {
    val p = join(CharacterId.Phoenix, 20, 20, seq = 1)
    assertTrue(move(p, 3, 27, 27)) // the blink overtook the step before it
    assertFalse(move(p, 2, 21, 21))
    assertEquals((27, 27), at(p))
  }

  @Test
  def stepsThatOvertakeTheStarDoNotUndoIt(): Unit = {
    val p = join(CharacterId.Spaceman, 10, 10, seq = 1)
    // The client stepped off the landing cell and that update beat the item packet here
    assertFalse(move(p, 3, 31, 10))
    useStar(p, 2, 30, 10)
    assertEquals((30, 10), at(p))
    assertTrue(move(p, 4, 32, 10))
    assertEquals((32, 10), at(p))
  }

  @Test
  def aStarLandingAfterALaterStepLeavesThePlayerOnThatStep(): Unit = {
    // Phoenix's first step off the landing cell is within its blink's reach of where it stood,
    // so it can be accepted before the item packet arrives
    val p = join(CharacterId.Phoenix, 20, 20, seq = 1)
    assertTrue(move(p, 3, 29, 20))
    val star = useStar(p, 2, 28, 20)
    assertEquals((29, 20), at(p))
    assertFalse("star spent", hasItem(p, star))
  }

  @Test
  def aStarAtFullRangeIsAcceptedWhileTheServerIsAStepBehind(): Unit = {
    val p = join(CharacterId.Spaceman, 10, 10, seq = 1)
    // The client is at (11, 11), a diagonal step the server hasn't seen yet, and stars the full
    // STAR_MAX_DISTANCE from there
    useStar(p, 3, 11 + Constants.STAR_MAX_DISTANCE, 11)
    assertEquals((11 + Constants.STAR_MAX_DISTANCE, 11), at(p))
  }

  @Test
  def aRefusedStarIsReturnedAndTheClientIsToldWhereItIs(): Unit = {
    val p = join(CharacterId.Spaceman, 10, 10, seq = 1)
    val ch = new EmbeddedChannel()
    p.setTcpChannel(ch)
    val star = useStar(p, 2, 10, 50) // 40 cells: more than any client picks
    assertEquals((10, 10), at(p))
    assertTrue("star back in the server's inventory", hasItem(p, star))
    val rejected = sentOverTcp(ch).collect { case ip: ItemPacket => ip }
    assertEquals(1, rejected.size)
    assertEquals(ItemAction.USE_REJECTED, rejected.head.getAction)
    assertEquals(star.id, rejected.head.getItemId)
    assertEquals((10, 10), (rejected.head.getX, rejected.head.getY))
    ch.finishAndReleaseAll()
  }

  @Test
  def aStarIntoAWallIsRefused(): Unit = {
    val p = join(CharacterId.Spaceman, 10, 10, seq = 1)
    instance.world.setTile(20, 10, Tile.Wall)
    val star = useStar(p, 2, 20, 10)
    assertEquals((10, 10), at(p))
    assertTrue(hasItem(p, star))
  }

  @Test
  def aStarIsNotAFreeTeleportForTheNextPacket(): Unit = {
    val p = join(CharacterId.Spaceman, 10, 10, seq = 1)
    useStar(p, 2, 20, 10)
    assertEquals((20, 10), at(p))
    assertFalse("jump across the map", move(p, 3, 50, 50))
    assertEquals((20, 10), at(p))
  }

  @Test
  def twoPacketsInTheSameMillisecondAreBothChecked(): Unit = {
    val p = join(CharacterId.Spaceman, 10, 10, seq = 1)
    move(p, 2, 11, 10)
    assertFalse(move(p, 3, 50, 50, gapMs = 0))
    assertEquals((11, 10), at(p))
    // ...while the client's second copy of a position still goes through
    assertTrue(move(p, 4, 12, 10))
    assertTrue(move(p, 5, 12, 10, gapMs = 0))
  }

  @Test
  def aFullRangeDiagonalBlinkIsAccepted(): Unit = {
    // Phoenix blinks 10 cells. At 45 degrees the client lands on (+7, +7): 9.9 cells, but 14 by
    // the Manhattan gate, which allowed 12
    val p = join(CharacterId.Phoenix, 20, 20, seq = 1)
    assertTrue("blink", move(p, 2, 27, 27))
    assertEquals((27, 27), at(p))
  }

  @Test
  def aJumpPastTheBlinksReachIsRefused(): Unit = {
    val p = join(CharacterId.Phoenix, 20, 20, seq = 1)
    assertFalse(move(p, 2, 20 + 10 + Constants.TELEPORT_RANGE_TOLERANCE + 1, 20))
    assertEquals((20, 20), at(p))
  }

  @Test
  def glitchersBlinkReachesSixCellsNotSixteen(): Unit = {
    // Glitcher's blink is Q, 6 cells; its E fires 16, and the client blinked that far instead
    val p = join(CharacterId.Glitcher, 20, 20, seq = 1)
    assertFalse(move(p, 2, 36, 20))
    assertTrue(move(p, 3, 26, 20))
  }

  @Test
  def aStarDoesNotBreakAHold(): Unit = {
    // A root or a freeze holds a player where the server has them: a step, a blink and a dash are
    // all held by it, and a star was the one way out
    val rooted = join(CharacterId.Spaceman, 10, 10, seq = 1)
    assertTrue(rooted.tryRoot(3000))
    val star = useStar(rooted, 2, 20, 10)
    assertEquals(new Position(10, 10), rooted.getPosition)
    assertTrue("given back", instance.itemManager.getInventory(rooted.getId).exists(_.id == star.id))

    val frozen = join(CharacterId.Spaceman, 30, 30, seq = 3)
    assertTrue(frozen.tryFreeze(3000))
    useStar(frozen, 4, 40, 30)
    assertEquals(new Position(30, 30), frozen.getPosition)
  }

  @Test
  def aRejoinStartsTheSequenceAfresh(): Unit = {
    val p = join(CharacterId.Spaceman, 10, 10, seq = 500)
    assertTrue(move(p, 501, 11, 10))
    // A reconnected client counts from zero again
    instance.handler.processPacket(new PlayerJoinPacket(0, p.getId, new Position(11, 10), p.getColorRGB,
      "p", 100, CharacterId.Spaceman.id), null, null)
    assertTrue(move(p, 1, 12, 10))
  }
}
