package com.gridgame.server

import com.gridgame.common.WorldRegistry
import com.gridgame.common.model._
import com.gridgame.server.game.GameInstance
import com.gridgame.server.lobby.{LobbyStatus, MatchLauncher}
import org.junit.After
import org.junit.Assert._
import org.junit.Test

import java.util.UUID
import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * Real matches of bots, one on every map, all at once, as a practice or ranked match full of bots
 * is played: started by the real launcher, on their real ticks and threads (the projectile tick,
 * the player tick, the bots', the item spawner), the opening skipped so they fight from the start.
 * While they play, every 25ms: everyone's health between 0 and their maximum, everyone on the map,
 * the living on ground someone can stand on, nobody with more than thirty shots in flight, a trap
 * to a cell. Then each ends as a match does (MatchEnd: scored, saved), and nothing the matches
 * caught along the way and only printed — a tick's error, a bot's, the end's — went wrong.
 */
class BotMatchSoakTest {
  import ServerTestKit._

  private val lobbies = mutable.Buffer[Short]()

  @After def endMatches(): Unit = lobbies.foreach(server.endGame)

  private def launch(mapIndex: Int, teams: Boolean): GameInstance = {
    // A host who never connected isn't seated: a match of bots alone
    val lobby = server.lobbyManager.createLobby(new UUID(7L, mapIndex), s"soak $mapIndex", mapIndex, 1, 8)
    lobbies += lobby.id
    if (teams) lobby.configure(1.toByte, 4)
    while (lobby.addBot().isDefined) ()
    lobby.status = LobbyStatus.IN_GAME
    val gi = new MatchLauncher(server).launch(lobby)
    gi.skipOpening()
    gi
  }

  @Test def botsFightOnEveryMapAndKeepTheRules(): Unit = {
    val tally = mutable.Map[String, Int]().withDefaultValue(0)
    var problem: String = null
    def check(ok: Boolean, why: => String): Unit = if (!ok && problem == null) problem = why

    val errors = captureStderr(quietly {
      val matches = (0 until WorldRegistry.size).map(i => (WorldRegistry.getFilename(i), launch(i, teams = i % 2 == 1)))
      val until = System.nanoTime() + 8000000000L
      while (System.nanoTime() < until && problem == null) {
        for ((map, gi) <- matches) {
          val w = gi.world
          for (p <- gi.registry.getAll.asScala) {
            val pos = p.getPosition
            check(p.getHealth >= 0 && p.getHealth <= p.getMaxHealth, s"$map: ${p.getName} has ${p.getHealth} of ${p.getMaxHealth}")
            check(pos.getX >= 0 && pos.getY >= 0 && pos.getX < w.width && pos.getY < w.height, s"$map: ${p.getName} is off the map at $pos")
            if (!p.isDead && !p.isPhased)
              check(w.isWalkable(pos.getX, pos.getY) || w.getTile(pos.getX, pos.getY) == Tile.Fence,
                s"$map: ${p.getName} (${CharacterDef.get(p.getCharacterId).displayName}) stands on " +
                  s"${w.getTile(pos.getX, pos.getY)} at $pos")
          }
          val shots = gi.projectileManager.getAll
          for ((owner, theirs) <- shots.groupBy(_.ownerId)) check(theirs.size <= 30, s"$map: $owner has ${theirs.size} shots in flight")
          tally("most shots in flight on a map") = Math.max(tally("most shots in flight on a map"), shots.size)
          for (t <- gi.trapManager.getAll) check(gi.trapManager.trapAt(t.x, t.y) eq t, s"$map: trap ${t.id} shares its cell")
        }
        Thread.sleep(25)
      }
      for ((map, gi) <- matches) {
        val deaths = gi.registry.getAll.asScala.map(p => gi.killTracker.getDeaths(p.getId)).sum
        tally("deaths") += deaths
        tally("fence cells") += gi.modifiedTiles.size
        server.endGame(gi.gameId)
        check(server.lobbyManager.getLobby(gi.gameId) == null, s"$map's lobby outlived its match")
        check(!gi.isRunning, s"$map's match is still running")
      }
      lobbies.clear()
    })
    if (problem != null) fail(problem)
    for (line <- errors.linesIterator)
      if (line.contains(" error:") || line.contains("Error in") || line.contains("Exception") || line.contains("weren't all recorded"))
        fail(s"a match printed: $line")
    println(s"BotMatchSoakTest: ${tally.toSeq.sortBy(_._1).map { case (k, v) => s"$k $v" }.mkString(", ")}")
    assertTrue("the bots fought", tally("deaths") > 0 || tally("most shots in flight on a map") > 5)
  }
}
