package com.gridgame.client.game

import com.gridgame.common.model._
import com.gridgame.common.protocol._

/** Which screen the client is on. */
object ClientState {
  val CONNECTING = 0
  val LOBBY_BROWSER = 1
  val IN_LOBBY = 2
  val PLAYING = 3
  val SCOREBOARD = 4
}

object GameClient {
  /** A blast is keyed by the projectile that set it off and the server tick it went off on. The
    * HIT on whoever a splash struck and the HITs on everyone it caught all come in that one tick,
    * so they are one blast; a piercing splash that goes off again later is another. */
  def blastKey(projectileId: Int, tick: Int): Long = (projectileId.toLong << 32) | (tick & 0xFFFFFFFFL)
  /** A trap's blasts are keyed past every projectile's. */
  val TRAP_BLAST_KEYS: Long = 1L << 62

  // A blast's fields, in its array (getBlasts)
  final val BLAST_TIME = 0    // when it went off, currentTimeMillis
  final val BLAST_X = 1       // where, world x * 1000
  final val BLAST_Y = 2       // world y * 1000
  final val BLAST_COLOR = 3   // the thrower's colour (ARGB)
  final val BLAST_RADIUS = 4  // how far it reaches, cells * 1000
  final val BLAST_TYPE = 5    // the projectile type that set it off, or the trap's type
  final val BLAST_CHAR = 6    // the thrower's character (a CharacterId's id), -1 unknown
  final val BLAST_TRAP = 7    // 1 when a trap set it off
  final val BLAST_SEED = 8    // what tells it from the blast beside it
  final val BLAST_SEEN = 9    // set by the renderer once it has shaken the camera for it
}

/**
 * The client's side of the game: its connection to the server, everything the server has told it
 * (the lobbies, the match, the players in it) and everything the player does. The server's
 * packets arrive on the packet thread ([[processPacket]]); the renderer and the input handlers
 * read and drive it from the render thread, and the menus from the FX thread.
 *
 * Each part of it has a file of its own:
 *
 *  - Connection       connecting, logging in, the heartbeat, the packet thread, sending
 *  - Lobby            the lobby browser and the lobby room
 *  - Chat             lobby and match chat
 *  - Ranked           the ranked queue
 *  - Records          the leaderboard, and the profile's stats and match history
 *  - MatchProgress    the match as a whole: kills, the clock, the scoreboard, practice, leaving it
 *  - Opening          its first thirty seconds: the Teams wall, the free-for-all ceasefire
 *  - WorldState       the map the match is played on
 *  - LocalPlayer      us: our character, health and the effects on us, and what we tell the server
 *  - LocalMovement    how we move: steps, dashes, blinks and stars, and where we are drawn
 *  - Attacks          aiming, charging, firing and casting, and the cooldowns
 *  - Barriers         ours, raised and streamed; everyone else's, as heard; the shots they stop
 *  - OtherPlayers     everyone else in the match, as the server tells us of them
 *  - Projectiles      the projectiles in flight, flown between the server's ticks
 *  - Items            items on the ground, our inventory, and using it
 *  - Traps            traps on the ground, and going off
 *  - EventAnimations  what the renderer shows of an event for a moment: hits, deaths, teleports, blasts
 *
 * What is forgotten when is decided here: a new match, going back to the lobby browser, the
 * connection dropping, and a new life.
 */
class GameClient(private[game] val serverHost: String, private[game] val serverPort: Int,
                 private[game] val initialWorld: WorldData, var playerName: String = "Player")
    extends Connection with Lobby with Chat with Ranked with Records with MatchProgress with Opening
    with WorldState with LocalPlayer with LocalMovement with Attacks with Barriers with OtherPlayers
    with Projectiles with Items with Traps with EventAnimations {

  @volatile var clientState: Int = ClientState.CONNECTING

  /** Run a screen's listener if one is registered. The listener is read once: the FX thread
    * clears these as screens go away (ClientMain.switchScreen), so a check-then-call on the
    * field could race to a null. */
  private[game] def fire(listener: () => Unit): Unit = if (listener != null) listener()

  /** One packet from the server, on the packet thread (or a test's). */
  private[client] def processPacket(packet: Packet): Unit = {
    cleanupHitTimes()
    packet.getType match {
      case PacketType.SESSION_TOKEN => takeSessionToken(packet.asInstanceOf[SessionTokenPacket])
      case PacketType.AUTH_RESPONSE => handleAuthResponse(packet.asInstanceOf[AuthResponsePacket])
      case PacketType.HEARTBEAT => handleHeartbeatEcho()
      case PacketType.LEADERBOARD => handleLeaderboard(packet.asInstanceOf[LeaderboardPacket])
      case PacketType.MATCH_HISTORY => handleMatchHistory(packet.asInstanceOf[MatchHistoryPacket])
      case PacketType.CHAT_MESSAGE => handleChatMessage(packet.asInstanceOf[ChatMessagePacket])
      case PacketType.RANKED_QUEUE => handleRankedQueue(packet.asInstanceOf[RankedQueuePacket])
      case PacketType.LOBBY_ACTION => handleLobbyAction(packet.asInstanceOf[LobbyActionPacket])
      case PacketType.GAME_EVENT => handleGameEvent(packet.asInstanceOf[GameEventPacket])
      case PacketType.WORLD_INFO => handleWorldInfo(packet.asInstanceOf[WorldInfoPacket])
      case PacketType.TILE_UPDATE => handleTileUpdate(packet.asInstanceOf[TileUpdatePacket])
      case PacketType.PROJECTILE_UPDATE => handleProjectileUpdate(packet.asInstanceOf[ProjectilePacket])
      case PacketType.ITEM_UPDATE => handleItemUpdate(packet.asInstanceOf[ItemPacket])
      case PacketType.TRAP_UPDATE => handleTrapUpdate(packet.asInstanceOf[TrapPacket])
      // The server's word on a player: on us (LocalPlayer), or on anyone else (OtherPlayers)
      case PacketType.PLAYER_UPDATE =>
        val update = packet.asInstanceOf[PlayerUpdatePacket]
        if (update.getPlayerId.equals(localPlayerId)) handleOwnUpdate(update) else handlePlayerUpdate(update)
      case PacketType.PLAYER_JOIN =>
        val join = packet.asInstanceOf[PlayerJoinPacket]
        if (join.getPlayerId.equals(localPlayerId)) handleOwnJoin(join) else handlePlayerJoin(join)
      case PacketType.PLAYER_LEAVE =>
        if (!packet.getPlayerId.equals(localPlayerId)) handlePlayerLeave(packet.asInstanceOf[PlayerLeavePacket])
      case _ =>
    }
  }

  // ── What is forgotten when ────────────────────────────────────────────────

  /** The server says a match is starting (LobbyAction.GAME_STARTING): nothing of the last one
    * carries over, and we start it on a new life. The last match's cooldowns, effects and dash
    * used to come with us: an ability cast as it ended was still cooling down, and a freeze still
    * held us. */
  private[game] def matchStarting(): Unit = {
    clientState = ClientState.PLAYING
    forgetMatch()
    newLife()
    // Each match counts its own server moves
    serverMovesSeen = 0
    beginScoring()
    localHealth.set(getSelectedCharacterMaxHealth)
    if (gameStartingListener != null) gameStartingListener()
  }

  /** Back to the lobby browser, from a match or its scoreboard. */
  def returnToLobbyBrowser(): Unit = {
    clientState = ClientState.LOBBY_BROWSER
    forgetLobby()
    isPracticeMode = false
    localTeamId = 0
    forgetMatch()
  }

  /** Everything about the match we were in: gone when the next one starts, when we go back to the
    * lobby browser, and when the connection drops. */
  private[game] def forgetMatch(): Unit = {
    forgetScores()
    clearOpening()
    forgetChat()
    forgetPlayers()
    forgetProjectiles()
    forgetItems()
    forgetTraps()
    forgetAnimations()
    isDead = false
    isRespawning = false
  }

  /** What a death takes away, at a respawn, a rejoin or the connection dropping, and what a new
    * match starts without: every effect on us, our items, our barrier, our cooldowns and a charge,
    * and a dash or a burst's standstill under way. */
  private[game] def newLife(): Unit = {
    clearEffects()
    loseItems()
    clearBarrier()
    resetAttacks()
    stopMoving()
  }
}
