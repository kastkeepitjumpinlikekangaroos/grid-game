package com.gridgame.e2e

import com.gridgame.client.game.{ClientState, GameClient}
import com.gridgame.common.{Constants, WorldRegistry}
import com.gridgame.common.model._
import com.gridgame.common.world.WorldLoader

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{ConcurrentHashMap, Executors, TimeUnit}
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.util.Random

/**
 * Load on a central server and its match servers from real clients, for measuring what a match
 * server can play at once (ops/kubernetes/CLAUDE.md, *How many matches a match server plays*):
 *
 * {{{
 * load_driver --host=H --port=P --stages=4,8,16 [--players=8] [--bots=0] [--settle=40] [--hold=60]
 *             [--map=-1] [--minutes=30] [--seed=1]
 * }}}
 *
 * Matches of `--players` humans (and `--bots` bots) are added stage by stage, until there are as
 * many as the stage says. Every human is a `GameClient` signed up for the run, on a random
 * character, walking nonstop and firing its primary at someone in its match every 0.6-1s: a
 * busier match than any real one. Once a stage's matches are all being played on their match
 * servers it waits `--settle` seconds (a match's opening holds fire for its first 30), then holds
 * for `--hold` seconds, sampling every client's round trip to its match server each second, and
 * prints a line: STAGE matches, players, round trips p50/p99/max, and clients out of their match.
 * What the match servers say (their status lines: ticks, CPU, heap) and the containers' own stats
 * are read beside it. Maps are taken in turn (`--map=-1`) or one. It exits once the last stage is
 * held, its clients disconnected.
 */
object LoadDriver {
  private final class Hand(val c: GameClient, val id: UUID, val rnd: Random) {
    @volatile var dx = 1
    @volatile var dy = 0
    @volatile var nextShot = 0L
  }

  private final class Table(val index: Int, val hands: Seq[Hand])

  def main(args: Array[String]): Unit = {
    val flags = args.collect { case a if a.startsWith("--") =>
      val kv = a.drop(2).split("=", 2)
      kv(0) -> (if (kv.length > 1) kv(1) else "true")
    }.toMap
    val host = flags.getOrElse("host", "127.0.0.1")
    val port = flags.getOrElse("port", "25570").toInt
    val stages = flags.getOrElse("stages", "1").split(",").map(_.trim.toInt).toSeq
    val players = flags.getOrElse("players", "8").toInt
    val bots = flags.getOrElse("bots", "0").toInt
    val settleS = flags.getOrElse("settle", "40").toInt
    val holdS = flags.getOrElse("hold", "60").toInt
    val map = flags.getOrElse("map", "-1").toInt
    val minutes = flags.getOrElse("minutes", "30").toInt
    val seed = flags.getOrElse("seed", "1").toLong
    val run = java.lang.Long.toString(System.currentTimeMillis() % 60466176L, 36)
    val roster = CharacterDef.all.map(_.id.id).toIndexedSeq
    println(s"Load: stages ${stages.mkString(",")} of $players players${if (bots > 0) s" and $bots bots" else ""} " +
      s"against $host:$port (run $run)")

    // One copy of each map for every client: the driver's memory is its limit, not the server's
    val worlds = new ConcurrentHashMap[String, WorldData]()
    val hands = Executors.newScheduledThreadPool(4)
    val tables = mutable.Buffer[Table]()
    val failures = new java.util.concurrent.atomic.AtomicInteger()

    def waitUntil(what: String, ms: Long)(cond: => Boolean): Unit = {
      val end = System.currentTimeMillis() + ms
      while (!cond) {
        if (System.currentTimeMillis() > end) throw new RuntimeException(s"timed out waiting for $what")
        Thread.sleep(50)
      }
    }

    def signUp(name: String, rnd: Random): Hand = {
      val c = new GameClient(host, port, WorldData.createEmpty(Constants.GRID_SIZE, Constants.GRID_SIZE), name)
      c.setWorldFileListener(file => c.setWorld(worlds.computeIfAbsent(file, f => WorldLoader.load("worlds/" + f))))
      val answer = new AtomicReference[Either[String, UUID]]()
      c.authResponseListener = (ok: Boolean, id: UUID, message: String) => {
        if (ok) c.completeAuthAndJoin(id, name)
        answer.set(if (ok) Right(id) else Left(message))
      }
      c.connect()
      c.sendAuthRequest(name, "hunter22", isSignup = true)
      waitUntil(s"$name's sign-up", 30000)(answer.get != null)
      val id = answer.get.fold(m => throw new RuntimeException(s"$name refused: $m"), identity)
      c.selectCharacter(roster(rnd.nextInt(roster.size)))
      new Hand(c, id, rnd)
    }

    /** One player's hands, every step: walk on (a new way now and then, or at a wall), and fire. */
    def act(h: Hand): Unit = try {
      val c = h.c
      if (c.clientState != ClientState.PLAYING || !c.onMatchServer || c.getIsDead) return
      val world = c.getWorld
      val pos = c.getLocalPosition
      if (h.rnd.nextInt(8) == 0 || !world.isWalkable(pos.getX + h.dx, pos.getY + h.dy)) {
        val (x, y) = Seq((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1))(h.rnd.nextInt(8))
        h.dx = x
        h.dy = y
      }
      c.movePlayer(h.dx, h.dy)
      val now = System.currentTimeMillis()
      if (now >= h.nextShot && !c.attacksLocked) {
        val others = c.getPlayers.values().asScala.iterator.filter(_.getId != h.id).toIndexedSeq
        if (others.nonEmpty) {
          val t = others(h.rnd.nextInt(others.size)).getPosition
          c.setMouseWorldPosition(t.getX + 0.5, t.getY + 0.5)
          c.pressPrimary()
          c.releasePrimary()
        }
        h.nextShot = now + 600 + h.rnd.nextInt(400)
      }
    } catch { case e: Throwable => if (failures.incrementAndGet() < 20) System.err.println(s"Load: a hand failed: $e") }

    def addTable(index: Int): Table = {
      val rnd = new Random(seed * 1000003L + index)
      val hs = (0 until players).map(k => signUp(s"ld$run${index}x$k", new Random(rnd.nextLong())))
      val host = hs.head
      val mapIndex = if (map >= 0) map else index % WorldRegistry.size
      host.c.createLobby(s"load $index", mapIndex, minutes)
      waitUntil(s"table $index's lobby", 15000)(host.c.currentLobbyId != 0)
      val lobbyId = host.c.currentLobbyId
      hs.tail.foreach(_.c.joinLobby(lobbyId))
      waitUntil(s"table $index's players in its lobby", 15000)(hs.forall(_.c.currentLobbyId == lobbyId))
      (1 to bots).foreach(_ => host.c.addBot())
      if (bots > 0) waitUntil(s"table $index's bots", 15000)(host.c.currentLobbyPlayerCount >= players + bots)
      host.c.startGame()
      val table = new Table(index, hs)
      hs.foreach { h =>
        val step = math.max(50, h.c.moveStepIntervalMs + 15).toLong
        hands.scheduleAtFixedRate(() => act(h), h.rnd.nextInt(step.toInt).toLong, step, TimeUnit.MILLISECONDS)
      }
      table
    }

    def playing(t: Table): Boolean = t.hands.forall(h => h.c.clientState == ClientState.PLAYING && h.c.onMatchServer)

    for (stage <- stages) {
      val start = System.currentTimeMillis()
      val adding = (tables.size until stage).toSeq
      // Four tables at a time: a sign-up is a password hashed on the central server
      adding.grouped(4).foreach { group =>
        val made = new ConcurrentHashMap[Int, Table]()
        val threads = group.map { i =>
          val t = new Thread(() => try made.put(i, addTable(i)) catch { case e: Throwable => System.err.println(s"Load: table $i: $e") })
          t.start()
          t
        }
        threads.foreach(_.join())
        group.foreach(i => Option(made.get(i)).foreach(tables += _))
      }
      try waitUntil(s"stage $stage's matches all playing", 120000)(tables.forall(playing))
      catch { case e: RuntimeException => System.err.println(s"Load: ${e.getMessage}: ${tables.count(playing)} of ${tables.size}") }
      println(s"STAGE-READY $stage: ${tables.count(playing)} of ${tables.size} matches playing after " +
        s"${(System.currentTimeMillis() - start) / 1000}s; settling ${settleS}s")
      Thread.sleep(settleS * 1000L)

      val holdStart = System.currentTimeMillis()
      println(s"HOLD-START $stage ${holdStart}")
      val rtts = mutable.ArrayBuffer[Int]()
      var out = 0
      for (_ <- 1 to holdS) {
        Thread.sleep(1000)
        tables.foreach(_.hands.foreach { h =>
          if (h.c.onMatchServer) { val r = h.c.lastRoundTripMs; if (r >= 0) rtts += r }
        })
      }
      out = tables.map(_.hands.count(h => !h.c.onMatchServer || h.c.clientState != ClientState.PLAYING)).sum
      val sorted = rtts.sorted
      def pct(p: Double): Int = if (sorted.isEmpty) -1 else sorted(math.min(sorted.size - 1, (p / 100 * sorted.size).toInt))
      println(s"STAGE $stage: ${tables.size} matches, ${tables.map(_.hands.size).sum} players, " +
        s"rtt p50 ${pct(50)}ms p90 ${pct(90)}ms p99 ${pct(99)}ms max ${if (sorted.isEmpty) -1 else sorted.last}ms " +
        s"(${sorted.size} samples), $out players out of their match; hold ${holdStart}-${System.currentTimeMillis()}")
    }

    hands.shutdownNow()
    tables.foreach(_.hands.foreach(h => try h.c.disconnect() catch { case _: Throwable => }))
    println("Load: done")
    System.exit(0)
  }
}
