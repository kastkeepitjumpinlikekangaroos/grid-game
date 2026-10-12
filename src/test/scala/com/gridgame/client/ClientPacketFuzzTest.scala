package com.gridgame.client

import com.gridgame.common.Constants
import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

import java.util.UUID
import scala.collection.mutable

/**
 * A client in a match told thousands of things at random, as the server's packets can arrive — out
 * of order, late, twice, about players it never heard join, projectiles of a type it doesn't know,
 * items, traps and tiles it has no record of, events of every kind — while its player walks, aims,
 * fires, casts and uses items, and frames are drawn between. Nothing it hears may throw: the client
 * hands its packets to GameClient.processPacket on one thread, a batch at a time, and whatever
 * throws there costs the rest of that batch. Every run is seeded; a failure says which seed and
 * step, with the packets that led to it.
 */
class ClientPacketFuzzTest {
  @Test def nothingTheServerCanSayThrows(): Unit =
    for (seed <- 1 to 12) new Run(seed).play(4000)

  private final class Run(seed: Long) {
    private val rnd = new scala.util.Random(seed)
    private val world = {
      val w = WorldData.createEmpty(50, 50)
      for (x <- 0 until 50; y <- 0 until 50) {
        val r = rnd.nextDouble()
        if (x == 0 || y == 0 || x == 49 || y == 49 || r < 0.06) w.setTile(x, y, Tile.Wall)
        else if (r < 0.09) w.setTile(x, y, Tile.Fence)
        else if (r < 0.12) w.setTile(x, y, Tile.Water)
      }
      w.setTile(25, 25, Tile.Grass)
      w
    }
    private val t = new TestClient(world)
    private val client = t.client
    private val trace = mutable.Queue[String]()
    private var step = 0
    private val tally = mutable.Map[String, Int]().withDefaultValue(0)
    private def most(what: String, n: Int): Unit = tally(what) = Math.max(tally(what), n)
    private var seq = 5000
    private var tick = 100

    private def note(s: String): Unit = { trace.enqueue(s"$step: $s"); if (trace.size > 40) trace.dequeue() }

    private def fail(why: String): Nothing =
      throw new AssertionError(s"seed $seed, step $step: $why\nThe last packets:\n  ${trace.mkString("\n  ")}")

    private val others = Seq.fill(4)(UUID.randomUUID())
    client.selectCharacter(CharacterDef.all(rnd.nextInt(CharacterDef.all.size)).id.id)
    t.startMatch(spawn = (25, 25), others = others.zipWithIndex.map { case (o, i) => o -> (10 + 5 * i, 10) })

    private val thrown: Seq[Byte] = CharacterDef.all
      .flatMap(c => Seq(c.primaryProjectileType, c.qAbility.projectileType, c.eAbility.projectileType)).filter(_ >= 0).distinct

    private def who(): UUID = rnd.nextInt(10) match {
      case 0 | 1 | 2 => t.id
      case 3 => UUID.randomUUID()
      case 4 => new UUID(0L, 0L)
      case _ => others(rnd.nextInt(others.size))
    }
    private def b(): Byte = (rnd.nextInt(256) - 128).toByte
    private def onMap(): Position =
      if (rnd.nextInt(8) == 0) new Position(rnd.nextInt(Constants.GRID_SIZE), rnd.nextInt(Constants.GRID_SIZE))
      else new Position(rnd.nextInt(world.width), rnd.nextInt(world.height))
    private def coord(): Float = if (rnd.nextInt(10) == 0) rnd.nextFloat() * 300 - 100 else rnd.nextFloat() * world.width
    private def nextSeq(): Int = { seq += (if (rnd.nextInt(10) == 0) -rnd.nextInt(20) else 1); seq }
    // The server moves it has told us of, more or less: a count a little behind, at it, or past it
    private var ownMoves = 0

    private def packet(): Packet = rnd.nextInt(100) match {
      case r if r < 25 =>
        val id = who()
        val moves = if (id == t.id) { ownMoves += rnd.nextInt(2); ownMoves + rnd.nextInt(3) - 1 } else rnd.nextInt(10)
        new PlayerUpdatePacket(nextSeq(), id, Packet.getCurrentTimestamp, onMap(), rnd.nextInt(), rnd.nextInt(400) - 100,
          rnd.nextInt(101), rnd.nextInt(256), CharacterDef.all(rnd.nextInt(CharacterDef.all.size)).id.id, rnd.nextInt(3).toByte,
          moves, rnd.nextInt(256), rnd.nextInt(65536), rnd.nextInt(101), rnd.nextInt(65536), rnd.nextInt(5))
      case r if r < 55 =>
        val action = if (rnd.nextInt(20) == 0) b() else rnd.nextInt(6).toByte
        tick += (if (rnd.nextInt(8) == 0) -rnd.nextInt(5) else rnd.nextInt(2))
        val a = rnd.nextDouble() * 2 * Math.PI
        val pType = if (rnd.nextInt(10) == 0) b() else thrown(rnd.nextInt(thrown.size))
        new ProjectilePacket(nextSeq(), who(), tick, coord(), coord(), rnd.nextInt(), 1 + rnd.nextInt(30),
          Math.cos(a).toFloat, Math.sin(a).toFloat, action, if (rnd.nextBoolean()) who() else null, rnd.nextInt(101).toByte, pType)
      case r if r < 63 =>
        val p = onMap()
        new ItemPacket(nextSeq(), who(), p.getX, p.getY, (rnd.nextInt(8)).toByte, 1 + rnd.nextInt(25),
          if (rnd.nextInt(10) == 0) b() else rnd.nextInt(5).toByte)
      case r if r < 69 =>
        val p = onMap()
        new TrapPacket(nextSeq(), who(), Packet.getCurrentTimestamp, p.getX, p.getY, 1 + rnd.nextInt(12),
          if (rnd.nextInt(10) == 0) b() else rnd.nextInt(5).toByte, rnd.nextInt(8).toByte, rnd.nextInt(3).toByte,
          rnd.nextInt(4), if (rnd.nextBoolean()) who() else null)
      case r if r < 77 =>
        val event = if (rnd.nextInt(10) == 0) b() else rnd.nextInt(7).toByte
        val p = onMap()
        new GameEventPacket(nextSeq(), who(), Packet.getCurrentTimestamp, event, 7.toShort, rnd.nextInt(400) - 10,
          rnd.nextInt(50).toShort, rnd.nextInt(50).toShort, if (rnd.nextBoolean()) who() else null, rnd.nextInt(10).toByte,
          p.getX.toShort, p.getY.toShort, rnd.nextInt(3).toByte, rnd.nextInt(40000) - 5000, rnd.nextInt(4).toByte)
      case r if r < 80 =>
        new TileUpdatePacket(nextSeq(), who(), rnd.nextInt(world.width + 10) - 5, rnd.nextInt(world.height + 10) - 5,
          rnd.nextInt(Tile.all.size + 5) - 2)
      case r if r < 84 =>
        val p = onMap()
        if (rnd.nextBoolean()) new PlayerJoinPacket(nextSeq(), who(), Packet.getCurrentTimestamp, p, rnd.nextInt(), "x" * rnd.nextInt(30),
          rnd.nextInt(300), if (rnd.nextInt(5) == 0) b() else CharacterDef.all(rnd.nextInt(CharacterDef.all.size)).id.id,
          rnd.nextInt(3).toByte)
        else new PlayerLeavePacket(nextSeq(), who())
      case r if r < 88 =>
        // Lobby news, the match starting over among it
        val action = if (rnd.nextInt(10) == 0) b() else rnd.nextInt(20).toByte
        new LobbyActionPacket(nextSeq(), who(), Packet.getCurrentTimestamp, action, rnd.nextInt(4).toShort, b(), b(), b(), b(),
          rnd.nextInt(3).toByte, "lobby", b(), rnd.nextInt(3).toByte, rnd.nextInt(6).toByte)
      case r if r < 90 => new ChatMessagePacket(nextSeq(), who(), Packet.getCurrentTimestamp, rnd.nextInt(4).toByte, "hello " * rnd.nextInt(8))
      case r if r < 92 => new HeartbeatPacket(nextSeq(), who())
      case r if r < 94 => new RankedQueuePacket(nextSeq(), who(), rnd.nextInt(6).toByte, b(), rnd.nextInt(3).toByte)
      case r if r < 96 => new MatchHistoryPacket(nextSeq(), who(), rnd.nextInt(5).toByte)
      case r if r < 98 => new LeaderboardPacket(nextSeq(), who(), rnd.nextInt(4).toByte)
      case _ => new WorldInfoPacket(nextSeq(), "the_cell.json")
    }

    /** What the player does: walk, aim, fire, cast, use what they carry, raise a barrier. */
    private def act(): Unit = rnd.nextInt(9) match {
      case 0 | 1 => note("walks"); client.movePlayer(rnd.nextInt(3) - 1, rnd.nextInt(3) - 1)
      case 2 => note("aims and fires"); client.setMouseWorldPosition(coord(), coord()); client.shoot()
      case 3 => note("fires toward"); client.shootToward(rnd.nextFloat() * 2 - 1, rnd.nextFloat() * 2 - 1, rnd.nextInt(101))
      case 4 => note("casts"); client.setMouseWorldPosition(coord(), coord()); client.shootAbility(rnd.nextInt(2) + 1)
      case 5 => note("bursts"); client.shootAllDirections()
      case 6 => note("uses an item"); client.setMouseWorldPosition(coord(), coord()); client.useItem((1 + rnd.nextInt(5)).toByte)
      case 7 => note("charges"); if (rnd.nextBoolean()) client.startCharging() else client.cancelCharging()
      case _ => note("dashes on, streams its barrier"); client.tickSwoop(); client.streamBarrier()
    }

    def play(steps: Int): Unit = {
      ServerQuiet.quietly {
        while (step < steps) {
          step += 1
          try {
            rnd.nextInt(10) match {
              case 0 => act()
              case 1 | 2 =>
                note("a frame")
                t.frame(5 + rnd.nextInt(40))
                client.updateVisualPosition()
              case _ =>
                // As the network thread hands it on: read back off the wire
                val p = PacketSerializer.deserialize(packet().serialize())
                note(p.toString)
                t.receive(p)
            }
          } catch {
            case e: AssertionError => throw e
            case e: Throwable =>
              val sw = new java.io.StringWriter
              e.printStackTrace(new java.io.PrintWriter(sw))
              fail(s"threw $sw")
          }
          if (client.clientState == com.gridgame.client.game.ClientState.PLAYING) tally("steps in a match") += 1
          else if (rnd.nextInt(4) == 0) {
            // Out of the match (it ended, the lobby closed): the next one starts
            note("a match starts")
            t.startMatch(spawn = (25, 25), others = others.zipWithIndex.map { case (o, i) => o -> (10 + 5 * i, 10) })
            tally("matches") += 1
          }
          most("most projectiles drawn", client.getProjectiles.size)
          most("most other players", client.getPlayers.size)
          most("most items on the ground", client.getItems.size)
          client.getProjectiles.values().forEach { p =>
            if (!java.lang.Float.isFinite(p.getX) || !java.lang.Float.isFinite(p.getY))
              fail(s"projectile ${p.id} drawn at (${p.getX}, ${p.getY})")
          }
        }
      }
      println(s"ClientPacketFuzzTest seed $seed: ${tally.toSeq.sortBy(_._1).map { case (k, v) => s"$k $v" }.mkString(", ")}")
    }
  }
}

/** Stdout thrown away while a run goes: the client says something about most of what it hears. */
private object ServerQuiet {
  def quietly[T](body: => T): T = {
    // Scala's println writes to Console.out, taken from System.out once, as Console starts
    val old = Console.out
    val nothing = new java.io.PrintStream(java.io.OutputStream.nullOutputStream())
    System.setOut(nothing)
    try Console.withOut(nothing)(body) finally System.setOut(old)
  }
}
