package com.gridgame.client

import com.gridgame.common.model._
import com.gridgame.common.protocol._
import javafx.application.Platform
import javafx.event.Event
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.ScrollPane
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent

import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * A GameClient with no network: what it sends is kept in `sent`, and the server's packets are
 * handed to it with [[receive]], as its packet thread would.
 */
class TestClient(val world: WorldData = WorldData.createEmpty(60, 60), val name: String = "me") {
  private val outbox = mutable.Buffer[Packet]()
  val client = new GameClient("localhost", 0, world, name)
  client.packetSink = p => outbox.synchronized { outbox += p }
  val id: UUID = UUID.randomUUID()
  client.completeAuthAndJoin(id, name)

  private var serverSeq = 1000

  def receive(p: Packet): Unit = client.processPacket(p)

  def sent: Seq[Packet] = outbox.synchronized(outbox.toList)
  def clearSent(): Unit = outbox.synchronized(outbox.clear())

  def sentUpdates: Seq[PlayerUpdatePacket] = sent.collect { case u: PlayerUpdatePacket => u }
  def sentSpawns: Seq[ProjectilePacket] = sent.collect { case p: ProjectilePacket if p.getAction == ProjectileAction.SPAWN => p }
  def sentLobbyActions: Seq[LobbyActionPacket] = sent.collect { case l: LobbyActionPacket => l }

  private def nextSeq(): Int = { serverSeq += 1; serverSeq }

  // --- What the server sends ---

  def lobby(action: Byte, lobbyId: Short = 7, who: UUID = id, name: String = "Lobby", players: Int = 1,
            maxPlayers: Int = 8, gameMode: Byte = 0, teamSize: Int = 2, charId: Byte = 0, status: Byte = 0): Unit =
    receive(new LobbyActionPacket(nextSeq(), who, Packet.getCurrentTimestamp, action, lobbyId, 0.toByte, 5.toByte,
      players.toByte, maxPlayers.toByte, status, name, charId, gameMode, teamSize.toByte))

  def gameEvent(event: Byte, who: UUID = new UUID(0, 0), target: UUID = null, kills: Int = 0, deaths: Int = 0,
                rank: Int = 0, spawn: (Int, Int) = (0, 0), team: Byte = 0, remaining: Int = 0): Unit =
    receive(new GameEventPacket(nextSeq(), who, Packet.getCurrentTimestamp, event, 7.toShort, remaining,
      kills.toShort, deaths.toShort, target, rank.toByte, spawn._1.toShort, spawn._2.toShort, team))

  /** The server's word on the match's opening (MatchOpening): how long it has left — 0, it is
    * over — and what it does while it lasts. */
  def opening(msLeft: Int, rules: Byte = MatchOpening.DIVIDER): Unit =
    receive(new GameEventPacket(nextSeq(), new UUID(0, 0), Packet.getCurrentTimestamp, GameEvent.MATCH_OPENING,
      7.toShort, 0, 0.toShort, 0.toShort, null, 0.toByte, 0.toShort, 0.toShort, 0.toByte, msLeft, rules))

  def join(who: UUID, x: Int, y: Int, name: String = "them", charId: Byte = 0, team: Byte = 0, health: Int = 100): Unit =
    receive(new PlayerJoinPacket(nextSeq(), who, Packet.getCurrentTimestamp, new Position(x, y), 0xFF00AA00, name,
      health, charId, team))

  def update(who: UUID, x: Int, y: Int, health: Int = 100, flags: Int = 0, serverMoves: Int = 0,
             charId: Byte = 0, flags2: Int = 0, slowPercent: Int = 0, aimAngle: Double = 0.0,
             barrierMs: Int = 0, seq: Int = -1): Unit =
    receive(new PlayerUpdatePacket(if (seq >= 0) seq else nextSeq(), who, Packet.getCurrentTimestamp,
      new Position(x, y), 0xFF00AA00, health, 0, flags, charId, 0.toByte, serverMoves, flags2,
      PlayerUpdatePacket.encodeAimAngle(aimAngle), slowPercent, barrierMs))

  /** The sequence number the server's next packet would carry, for a test that needs to deliver
    * two of them out of order. */
  def peekSeq: Int = serverSeq + 1

  def projectile(action: Byte, projectileId: Int, owner: UUID, x: Float = 10f, y: Float = 10f, target: UUID = null,
                 pType: Byte = ProjectileType.ARROW): Unit =
    receive(new ProjectilePacket(nextSeq(), owner, Packet.getCurrentTimestamp, x, y, 0xFF112233, projectileId,
      1f, 0f, action, target, 0.toByte, pType))

  def trap(action: Byte, trapId: Int, trapType: Byte = TrapType.BEAR_TRAP, who: UUID = id,
           x: Int = 5, y: Int = 5, team: Byte = 0, victim: UUID = null): Unit =
    receive(new TrapPacket(nextSeq(), who, Packet.getCurrentTimestamp, x, y, trapId, action,
      trapType, team, 0, victim))

  def sentTraps: Seq[TrapPacket] = sent.collect { case tp: TrapPacket => tp }

  def item(action: Byte, itemId: Int, itemType: ItemType, who: UUID = id, x: Int = 5, y: Int = 5): Unit =
    receive(new ItemPacket(nextSeq(), who, x, y, itemType.id, itemId, action))

  /** A match starting, as the server opens one: the start, the world, then where everyone is. */
  def startMatch(spawn: (Int, Int), others: Seq[(UUID, (Int, Int))] = Seq.empty, team: Byte = 0): Unit = {
    lobby(LobbyAction.GAME_STARTING)
    client.setWorld(world)
    join(id, spawn._1, spawn._2, name, client.selectedCharacterId, team)
    others.foreach { case (who, (x, y)) => join(who, x, y) }
  }

  def at: (Int, Int) = (client.getLocalPosition.getX, client.getLocalPosition.getY)
}

/** Runs code on the JavaFX thread, starting the toolkit once per test JVM. */
object Fx {
  private lazy val started: Unit = {
    val latch = new CountDownLatch(1)
    try Platform.startup(() => latch.countDown())
    catch { case _: IllegalStateException => latch.countDown() } // already running
    latch.await(30, TimeUnit.SECONDS)
    Platform.setImplicitExit(false)
  }

  def apply[T](body: => T): T = {
    started
    if (Platform.isFxApplicationThread) return body // already there: waiting on itself would never end
    val result = new AtomicReference[Either[Throwable, T]]()
    val done = new CountDownLatch(1)
    Platform.runLater(() => {
      result.set(try Right(body) catch { case t: Throwable => Left(t) })
      done.countDown()
    })
    if (!done.await(30, TimeUnit.SECONDS)) throw new AssertionError("the FX thread didn't run the task")
    result.get match {
      case Right(v) => v
      case Left(t) => throw t
    }
  }

  /** Every node under `root`, a scroll pane's content included. */
  def all(root: Node): Seq[Node] = root match {
    case sp: ScrollPane => sp +: (if (sp.getContent != null) all(sp.getContent) else Nil)
    case p: Parent => p +: p.getChildrenUnmodifiable.asScala.toSeq.flatMap(all)
    case n => Seq(n)
  }

  def labels(root: Node): Seq[String] = all(root).collect { case l: javafx.scene.control.Labeled if l.getText != null => l.getText }

  def click(node: Node): Unit =
    Event.fireEvent(node, new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 1,
      false, false, false, false, true, false, false, true, false, false, null))
}
