package com.gridgame.server.lobby

import com.gridgame.common.Constants
import com.gridgame.common.WorldRegistry
import com.gridgame.common.model.CharacterDef
import com.gridgame.common.observability.Attrs
import com.gridgame.common.observability.Metrics
import com.gridgame.common.observability.Telemetry
import com.gridgame.common.protocol._
import io.opentelemetry.api.common.Attributes

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

case class QueueEntry(playerId: UUID, var characterId: Byte, elo: Int, joinTime: Long, mode: Byte = RankedQueueMode.FFA)

/**
 * One ranked mode: who is waiting for it, in the order they joined, and what a match of it is — its
 * lobby's name, length and size, whether bots fill the seats nobody took, and its match type.
 */
private final class RankedMode(
    val mode: Byte,
    /** How metrics and the log name it. */
    val label: String,
    val modeAttrs: Attributes,
    val lobbyName: String,
    val durationMinutes: Int,
    val maxPlayers: Int,
    /** The lobby's match type: 2 ranked FFA, 3 ranked duel, 4 ranked Teams. */
    val matchType: Byte,
    /** Teams: two teams of this size. 0 for every other mode. */
    val teamSize: Int,
    val fillWithBots: Boolean
) {
  val waiting = new CopyOnWriteArrayList[QueueEntry]()
  val byPlayer = new ConcurrentHashMap[UUID, QueueEntry]()

  def add(entry: QueueEntry): Unit = {
    waiting.add(entry)
    byPlayer.put(entry.playerId, entry)
  }

  /** Take a player out of the queue. Returns their entry, or null if they weren't in it. */
  def remove(playerId: UUID): QueueEntry = {
    val entry = byPlayer.remove(playerId)
    if (entry != null) waiting.remove(entry)
    entry
  }

  /**
   * Put entries back (keeping their joinTime, so the wait doesn't start over): used when a match
   * start fails after they were already taken out of the queue, so nobody is silently stranded.
   */
  def rollback(entries: Seq[QueueEntry]): Unit = {
    entries.foreach { entry =>
      val existing = byPlayer.putIfAbsent(entry.playerId, entry)
      if (existing == null) {
        waiting.add(entry)
      }
    }
  }

  def snapshot: Seq[QueueEntry] = waiting.asScala.toSeq
}

/**
 * Ranked matchmaking, in three queues: free-for-all (8, or whoever is waiting after a minute, the
 * rest bots), duels (the closest pair by rating, or any pair after 30s) and Teams (3v3, bots
 * filling the seats after a minute). A match found gets a lobby of its own and starts at once
 * (MatchLauncher).
 */
class RankedQueue(host: LobbyHost, lobbyManager: LobbyManager, launcher: MatchLauncher) {
  private val ffa = new RankedMode(RankedQueueMode.FFA, "ffa", Attrs.ModeFfa, "Ranked Match",
    Constants.DEFAULT_GAME_DURATION_MIN, Constants.RANKED_FFA_MAX_PLAYERS, matchType = 2, teamSize = 0, fillWithBots = true)
  private val duel = new RankedMode(RankedQueueMode.DUEL, "duel", Attrs.ModeDuel, "Ranked Duel",
    Constants.DUEL_GAME_DURATION_MIN, Constants.DUEL_MAX_PLAYERS, matchType = 3, teamSize = 0, fillWithBots = false)
  private val teams = new RankedMode(RankedQueueMode.TEAMS, "teams", Attrs.ModeTeams, "Ranked Teams",
    Constants.TEAMS_GAME_DURATION_MIN, Constants.TEAMS_MAX_PLAYERS, matchType = 4, teamSize = Constants.TEAMS_TEAM_SIZE,
    fillWithBots = true)
  private val modes = Seq(ffa, duel, teams)

  private def modeOf(mode: Byte): RankedMode =
    if (mode == RankedQueueMode.DUEL) duel else if (mode == RankedQueueMode.TEAMS) teams else ffa

  private var matchmakingExecutor: ScheduledExecutorService = _

  def start(): Unit = {
    matchmakingExecutor = Executors.newSingleThreadScheduledExecutor()
    matchmakingExecutor.scheduleAtFixedRate(
      new Runnable { def run(): Unit = checkQueue() },
      5, 5, TimeUnit.SECONDS
    )

    // Async gauges expose queue sizes per mode
    val meter = Telemetry.meter("com.gridgame.queue")
    meter.gaugeBuilder("gridgame.queue.players")
      .setDescription("Players currently in a ranked matchmaking queue")
      .setUnit("{player}")
      .ofLongs()
      .buildWithCallback { obs =>
        modes.foreach(m => obs.record(m.waiting.size().toLong, m.modeAttrs))
      }
    println("RankedQueue: Started matchmaking service")
  }

  def stop(): Unit = {
    if (matchmakingExecutor != null) {
      matchmakingExecutor.shutdown()
      try {
        if (!matchmakingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
          matchmakingExecutor.shutdownNow()
        }
      } catch {
        case _: InterruptedException =>
          matchmakingExecutor.shutdownNow()
      }
    }
  }

  def addPlayer(playerId: UUID, characterId: Byte, elo: Int, mode: Byte = RankedQueueMode.FFA): Unit = {
    if (isInQueue(playerId)) return
    // Validate character ID
    if (!CharacterDef.isValid(characterId)) {
      System.err.println(s"RankedQueue: Player ${playerId.toString.substring(0, 8)} invalid character ID: $characterId")
      return
    }

    val m = modeOf(mode)
    m.add(QueueEntry(playerId, characterId, elo, System.currentTimeMillis(), mode))
    println(s"RankedQueue: Player ${playerId.toString.substring(0, 8)} joined ${m.label} queue (elo=$elo, queue size=${m.waiting.size()})")
    sendQueueStatus(m, playerId)
  }

  def removePlayer(playerId: UUID): Unit = {
    val now = System.currentTimeMillis()
    modes.foreach { m =>
      val entry = m.remove(playerId)
      if (entry != null) {
        Metrics.queueWaitTime.record((now - entry.joinTime).toDouble / 1000.0, Attributes.of(Attrs.Mode, m.label, Attrs.Outcome, "left"))
        println(s"RankedQueue: Player ${playerId.toString.substring(0, 8)} left ${m.label} queue (queue size=${m.waiting.size()})")
      }
    }
  }

  def updateCharacter(playerId: UUID, characterId: Byte): Unit = {
    // Validate character ID before accepting the change
    if (!CharacterDef.isValid(characterId)) {
      System.err.println(s"RankedQueue: Player ${playerId.toString.substring(0, 8)} invalid character ID: $characterId")
      return
    }
    modes.foreach { m =>
      val entry = m.byPlayer.get(playerId)
      if (entry != null) {
        entry.characterId = characterId
        println(s"RankedQueue: Player ${playerId.toString.substring(0, 8)} changed character to $characterId (${m.label})")
      }
    }
  }

  def isInQueue(playerId: UUID): Boolean = modes.exists(_.byPlayer.containsKey(playerId))

  private def sendQueueStatus(m: RankedMode, playerId: UUID): Unit = {
    val player = host.getConnectedPlayer(playerId)
    val entry = m.byPlayer.get(playerId)
    if (player == null || entry == null) return

    val waitSeconds = ((System.currentTimeMillis() - entry.joinTime) / 1000).toInt
    val packet = new RankedQueuePacket(
      host.outbox.nextSeq(), playerId, Packet.getCurrentTimestamp,
      RankedQueueAction.QUEUE_STATUS,
      entry.characterId,
      m.waiting.size().toByte,
      entry.elo.toShort,
      waitSeconds,
      mode = m.mode
    )
    host.outbox.send(packet, player)
  }

  /**
   * Is this queued player still free to be put in a match: connected, and in no lobby? Entering a
   * lobby takes a player out of the queue (LobbyHandler), but the matchmaker works from a snapshot,
   * and a player it took from one lobby into another was left behind in the first as a member who
   * would never come back, holding a seat and — as its host — the lobby itself.
   */
  private def stillWaiting(e: QueueEntry): Boolean =
    host.getConnectedPlayer(e.playerId) != null && lobbyManager.getPlayerLobby(e.playerId) == null

  /** One matchmaking pass. Runs every five seconds once started; tests call it themselves, on a
    * clock of their own, so a minute's wait takes no time. */
  private[server] def checkQueue(now: Long = System.currentTimeMillis()): Unit = {
    try {
      checkFullOrWaited(ffa, now)
      checkDuelQueue(now)
      checkFullOrWaited(teams, now)
    } catch {
      case e: Exception =>
        System.err.println(s"RankedQueue: Error in checkQueue: ${e.getMessage}")
    }
  }

  /** Free-for-all and Teams: a match as soon as there are enough for one (the closest ratings
    * together), or with whoever is waiting once someone has waited a minute. */
  private def checkFullOrWaited(m: RankedMode, now: Long): Unit = {
    val snapshot = m.snapshot
    val queueSize = snapshot.size

    if (queueSize >= m.maxPlayers) {
      val sorted = snapshot.sortBy(_.elo)
      startMatch(m, sorted.take(m.maxPlayers))
    } else if (queueSize >= 1) {
      val oldest = snapshot.minBy(_.joinTime)
      val waitTime = now - oldest.joinTime
      if (waitTime > 60000) {
        startMatch(m, snapshot)
      }
    }

    // Send status updates to remaining queued players
    m.waiting.asScala.foreach(entry => sendQueueStatus(m, entry.playerId))
  }

  private def checkDuelQueue(now: Long): Unit = {
    val snapshot = duel.snapshot

    if (snapshot.size >= 2) {
      // Sort by ELO to find closest pair
      val sorted = snapshot.sortBy(_.elo)
      var matched = false

      // Try to find a pair within 200 ELO
      var i = 0
      while (i < sorted.size - 1 && !matched) {
        val a = sorted(i)
        val b = sorted(i + 1)
        val eloDiff = Math.abs(a.elo - b.elo)
        val oldestWait = now - Math.min(a.joinTime, b.joinTime)

        if (eloDiff <= 200 || oldestWait > 30000) {
          startMatch(duel, Seq(a, b))
          matched = true
        }
        i += 1
      }

      // If no close pair found but someone waited > 30s, match any pair
      if (!matched) {
        val oldest = sorted.minBy(_.joinTime)
        val oldestWait = now - oldest.joinTime
        if (oldestWait > 30000 && sorted.size >= 2) {
          startMatch(duel, Seq(sorted(0), sorted(1)))
        }
      }
    }

    // Send status updates to remaining queued players
    duel.waiting.asScala.foreach(entry => sendQueueStatus(duel, entry.playerId))
  }

  /**
   * Start a ranked match for these entries: those still waiting get a lobby of their own on a random
   * map, are told the match was found, and the match starts, the empty seats filled with bots where
   * the mode has them. Everyone taken from the queue is put back in it if the start fails.
   */
  private def startMatch(m: RankedMode, entries: Seq[QueueEntry]): Unit = {
    // Only those still waiting — connected, and in no lobby. The rest are dropped.
    val connected = entries.filter(stillWaiting)

    // Remove all entries (connected + disconnected) from the queue up front;
    // we'll restore `connected` if anything fails before the match actually starts.
    entries.foreach(entry => m.remove(entry.playerId))

    // A duel needs both of its players
    if (m eq duel) {
      if (connected.size < 2) {
        println(s"RankedQueue: ${m.label} match aborted — only ${connected.size} of ${entries.size} entries are still connected")
        // Restore any survivors so they can rematch in the next tick
        if (connected.nonEmpty) m.rollback(connected)
        return
      }
    } else if (connected.isEmpty) {
      println(s"RankedQueue: ${m.label} match aborted — none of ${entries.size} entries are still connected")
      return
    }

    var createdLobby: Lobby = null
    try {
      // Pick random map
      val random = new java.util.Random()
      val mapIndex = random.nextInt(WorldRegistry.size)
      val worldFileName = WorldRegistry.getFilename(mapIndex)

      // Create lobby
      val hostId = connected.head.playerId
      createdLobby = lobbyManager.createLobby(hostId, m.lobbyName, mapIndex, m.durationMinutes, m.maxPlayers)
      if (createdLobby == null) {
        System.err.println(s"RankedQueue: ${m.label} match failed — createLobby returned null (max lobbies reached?); rolling back ${connected.size} entries to the ${m.label} queue")
        m.rollback(connected)
        return
      }
      val lobby = createdLobby
      lobby.isRanked = true
      if (m.teamSize > 0) {
        lobby.gameMode = 1 // Teams mode
        lobby.teamSize = m.teamSize
      }
      lobby.matchType = m.matchType

      println(s"RankedQueue: ${m.label} match creating lobby ${lobby.id} for ${connected.size} players on $worldFileName")

      // Add remaining players to lobby
      connected.tail.foreach { entry =>
        lobby.addPlayer(entry.playerId)
        lobbyManager.setPlayerLobby(entry.playerId, lobby.id)
      }

      // Set character selections
      connected.foreach { entry =>
        lobby.setCharacter(entry.playerId, entry.characterId)
      }

      // Send MATCH_FOUND to each player
      connected.foreach { entry =>
        val player = host.getConnectedPlayer(entry.playerId)
        if (player != null) {
          val matchFoundPacket = new RankedQueuePacket(
            host.outbox.nextSeq(), entry.playerId, Packet.getCurrentTimestamp,
            RankedQueueAction.MATCH_FOUND,
            entry.characterId,
            connected.size.toByte,
            entry.elo.toShort,
            0,
            lobby.id,
            mapIndex.toByte,
            m.durationMinutes.toByte,
            connected.size.toByte,
            m.maxPlayers.toByte,
            m.lobbyName,
            m.mode
          )
          host.outbox.send(matchFoundPacket, player)
        }
      }

      // Start the game, the empty seats filled with bots where the mode has them (added first, so
      // Teams deals them to the teams too)
      lobby.status = LobbyStatus.IN_GAME
      val botsNeeded = if (m.fillWithBots) m.maxPlayers - connected.size else 0
      for (_ <- 1 to botsNeeded) {
        lobby.botManager.addBot()
      }
      launcher.launch(lobby, withBots = m.fillWithBots)

      // Emit metrics only after the match has actually been set up successfully
      val matchedNow = System.currentTimeMillis()
      Metrics.queueMatchesMade.add(1L, m.modeAttrs)
      connected.foreach { e =>
        Metrics.queueWaitTime.record((matchedNow - e.joinTime).toDouble / 1000.0, Attributes.of(Attrs.Mode, m.label, Attrs.Outcome, "matched"))
      }

      println(s"RankedQueue: Started ranked ${m.label} match (lobby ${lobby.id}) with ${connected.size} players + $botsNeeded bots on $worldFileName")
    } catch {
      case NonFatal(e) =>
        System.err.println(s"RankedQueue: ${m.label} match threw ${e.getClass.getSimpleName}: ${e.getMessage}; rolling back ${connected.size} entries to the ${m.label} queue")
        e.printStackTrace()
        if (createdLobby != null) {
          lobbyManager.removeLobby(createdLobby.id)
        }
        m.rollback(connected)
    }
  }
}
