package com.gridgame.e2e

import com.gridgame.client.game.{ClientState, GameClient}
import com.gridgame.common.Constants
import com.gridgame.common.model._
import com.gridgame.common.world.WorldLoader

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * Plays matches against a running central server and its match servers, as players would, and says
 * what happened at each step (ops/kubernetes/CLAUDE.md):
 *
 * {{{
 * bazel run //src/test/scala/com/gridgame/e2e:cluster_smoke -- [host] [port] [matches]
 * }}}
 *
 * Each match: three players sign up, meet in a lobby (a one-minute free-for-all), and are sent to a
 * match server; all three must be playing there and see each other, one walks and the others must
 * see it, one fires, one leaves (the menu's Leave) and must be gone from the others' screens, and the other
 * two must reach the scoreboard when the minute is up. Matches run at once, packed onto the match
 * servers with room (several to one, as many as its capacity holds). Exits 0 if every match did all of it.
 */
object ClusterSmoke {
  private val run = (System.nanoTime() % 100000).abs

  def main(args: Array[String]): Unit = {
    val host = args.lift(0).getOrElse("127.0.0.1")
    val port = args.lift(1).map(_.toInt).getOrElse(25570)
    val matches = args.lift(2).map(_.toInt).getOrElse(1)
    println(s"Cluster smoke: $matches match(es) against $host:$port")
    val results = atOnce(0 until matches)(m => playOne(host, port, m))
    val failed = results.count(!_)
    println(if (failed == 0) s"Cluster smoke: all $matches match(es) played through" else s"Cluster smoke: $failed of $matches FAILED")
    System.exit(if (failed == 0) 0 else 1)
  }

  /** Each on a thread of its own, all at once. */
  private def atOnce(xs: Seq[Int])(f: Int => Boolean): Seq[Boolean] = {
    val out = new Array[Boolean](xs.size)
    val threads = xs.zipWithIndex.map { case (x, i) =>
      val t = new Thread(() => out(i) = try f(x) catch { case e: Throwable => println(s"[match $x] threw $e"); false })
      t.start()
      t
    }
    threads.foreach(_.join())
    out.toSeq
  }

  private def playOne(host: String, port: Int, m: Int): Boolean = {
    val clients = scala.collection.mutable.Buffer[GameClient]()
    def say(s: String): Unit = println(s"[match $m] $s")
    def waitUntil(what: String, ms: Long = 20000)(cond: => Boolean): Unit = {
      val start = System.currentTimeMillis()
      while (!cond) {
        if (System.currentTimeMillis() - start > ms) throw new RuntimeException(s"timed out waiting for $what")
        Thread.sleep(20)
      }
      say(s"$what (${System.currentTimeMillis() - start}ms)")
    }
    def player(name: String): (GameClient, UUID) = {
      val c = new GameClient(host, port, WorldData.createEmpty(Constants.GRID_SIZE, Constants.GRID_SIZE), name)
      clients += c
      c.setWorldFileListener(file => c.setWorld(WorldLoader.load("worlds/" + file)))
      val answer = new AtomicReference[Either[String, UUID]]()
      c.authResponseListener = (ok: Boolean, id: UUID, message: String) => {
        if (ok) c.completeAuthAndJoin(id, name)
        answer.set(if (ok) Right(id) else Left(message))
      }
      c.connect()
      c.sendAuthRequest(name, "hunter22", isSignup = true)
      waitUntil(s"$name signed up")(answer.get != null)
      (c, answer.get.fold(msg => throw new RuntimeException(s"$name refused: $msg"), identity))
    }

    try {
      val (a, aId) = player(s"k${run}a$m")
      val (b, bId) = player(s"k${run}b$m")
      val (c, cId) = player(s"k${run}c$m")
      val all = Seq(a, b, c)
      val ids = Seq(aId, bId, cId)
      a.selectCharacter(CharacterId.Ranger.id)
      a.createLobby(s"smoke $m", 0, 1)
      waitUntil("the lobby made")(a.currentLobbyId != 0)
      Seq(b, c).foreach(_.joinLobby(a.currentLobbyId))
      waitUntil("everyone in it")(all.forall(_.currentLobbyId == a.currentLobbyId))
      a.startGame()
      waitUntil("everyone playing on a match server, seeing the others") {
        all.forall(x => x.clientState == ClientState.PLAYING && x.onMatchServer &&
          ids.count(id => x.getPlayers.containsKey(id)) == 2)
      }

      // The ceasefire is half a minute: walking is allowed in it
      val start = a.getLocalPosition
      val world = a.getWorld
      val (dx, dy) = Seq((1, 0), (-1, 0), (0, 1), (0, -1)).find { case (x, y) =>
        (1 to 3).forall(k => world.isWalkable(start.getX + x * k, start.getY + y * k))
      }.getOrElse(throw new RuntimeException(s"nowhere to walk from $start"))
      for (_ <- 1 to 3) {
        a.movePlayer(dx, dy)
        Thread.sleep(a.moveStepIntervalMs + 30L)
      }
      val walked = a.getLocalPosition
      waitUntil("the others seeing the walk")(Seq(b, c).forall(_.getPlayers.get(aId).getPosition == walked))

      c.leaveMatch()
      waitUntil("the one who left gone from the others' screens")(Seq(a, b).forall(x => !x.getPlayers.containsKey(cId)))
      if (c.clientState != ClientState.LOBBY_BROWSER) throw new RuntimeException("the leaver isn't in the browser")

      waitUntil("the opening over", 40000)(!a.attacksLocked)
      val target = b.getLocalPosition
      a.setMouseWorldPosition(target.getX, target.getY)
      a.pressPrimary()
      a.releasePrimary()
      waitUntil("the shot seen") {
        b.getProjectiles.values().stream().anyMatch(_.ownerId == aId) || !b.getFadingProjectiles.isEmpty
      }

      waitUntil("the scoreboard, the minute up", 90000)(Seq(a, b).forall(_.clientState == ClientState.SCOREBOARD))
      val board = a.scoreboard.toArray(Array.empty[ScoreEntry]).map(_.playerId).toSet
      if (board != ids.toSet) throw new RuntimeException(s"the scoreboard has $board, not all three")
      waitUntil("the match server letting them go")(Seq(a, b).forall(!_.onMatchServer))
      say("played through")
      true
    } catch {
      case e: Throwable =>
        say(s"FAILED: $e")
        false
    } finally clients.foreach(x => try x.disconnect() catch { case _: Throwable => })
  }
}
