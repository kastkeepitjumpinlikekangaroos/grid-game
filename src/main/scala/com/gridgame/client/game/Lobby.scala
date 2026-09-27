package com.gridgame.client.game

import com.gridgame.client.i18n.Messages
import com.gridgame.common.Constants
import com.gridgame.common.model._
import com.gridgame.common.protocol._

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** Someone in the current lobby, human or bot. */
final case class LobbyMember(id: UUID, name: String)

/**
 * The lobby browser and the lobby room: the list of lobbies, the one we are in (its settings, its
 * members and the characters they have picked), and what we ask of it — create, join, leave,
 * configure, add a bot, start. Its screens listen for changes through the listeners here, which
 * the packet thread calls.
 */
trait Lobby { this: GameClient =>
  val lobbyCharacterSelections: ConcurrentHashMap[UUID, Byte] = new ConcurrentHashMap[UUID, Byte]()

  @volatile var currentLobbyId: Short = 0
  @volatile var currentLobbyName: String = ""
  @volatile var currentLobbyMapIndex: Int = 0
  @volatile var currentLobbyDuration: Int = Constants.DEFAULT_GAME_DURATION_MIN
  @volatile var currentLobbyPlayerCount: Int = 0
  @volatile var currentLobbyMaxPlayers: Int = Constants.MAX_LOBBY_PLAYERS
  @volatile var isLobbyHost: Boolean = false
  @volatile var currentLobbyGameMode: Byte = 0  // 0=FFA, 1=Teams
  @volatile var currentLobbyTeamSize: Int = 2

  // Server listings (lobbies, leaderboard, match history) arrive as ENTRY... END. Entries
  // collect in a pending buffer (packet-processor thread only) and replace the published
  // list at END. A request the server rate-limits gets no reply, so it leaves the last
  // listing up; clearing the list on request used to leave blank rows or "Loading..." forever.
  @volatile var lobbyList: Vector[LobbyInfo] = Vector.empty
  private val pendingLobbyList = scala.collection.mutable.ArrayBuffer.empty[LobbyInfo]

  // In server order: humans as they joined, bots as they were added (see previewTeams).
  val lobbyMembers: CopyOnWriteArrayList[LobbyMember] = new CopyOnWriteArrayList[LobbyMember]()

  @volatile var lobbyActionFailedListener: Byte => Unit = _
  @volatile var lobbyListListener: () => Unit = _
  @volatile var lobbyJoinedListener: () => Unit = _
  @volatile var lobbyUpdatedListener: () => Unit = _
  @volatile var gameStartingListener: () => Unit = _
  @volatile var lobbyClosedListener: () => Unit = _

  private[game] def handleLobbyAction(packet: LobbyActionPacket): Unit = {
    packet.getAction match {
      case LobbyAction.LIST_ENTRY =>
        val info = new LobbyInfo(
          packet.getLobbyId, packet.getLobbyName, packet.getMapIndex & 0xFF,
          packet.getDurationMinutes & 0xFF, packet.getPlayerCount & 0xFF,
          packet.getMaxPlayers & 0xFF, packet.getLobbyStatus & 0xFF,
          packet.getGameMode & 0xFF, packet.getTeamSize & 0xFF
        )
        pendingLobbyList += info

      case LobbyAction.LIST_END =>
        lobbyList = pendingLobbyList.toVector
        pendingLobbyList.clear()
        fire(lobbyListListener)

      case LobbyAction.JOINED =>
        currentLobbyId = packet.getLobbyId
        currentLobbyName = packet.getLobbyName
        currentLobbyMapIndex = packet.getMapIndex & 0xFF
        currentLobbyDuration = packet.getDurationMinutes & 0xFF
        currentLobbyPlayerCount = packet.getPlayerCount & 0xFF
        currentLobbyMaxPlayers = packet.getMaxPlayers & 0xFF
        currentLobbyGameMode = packet.getGameMode
        currentLobbyTeamSize = packet.getTeamSize & 0xFF
        clientState = ClientState.IN_LOBBY
        lobbyMembers.clear()
        lobbyMembers.add(LobbyMember(localPlayerId, playerName))
        forgetChat() // the last lobby's chat isn't this one's
        if (lobbyJoinedListener != null) lobbyJoinedListener()

      case LobbyAction.MEMBER =>
        // The roster a joiner is sent, in server order and including themselves: re-adding
        // at the end moves us from the front, where JOINED put us, to our real place.
        currentLobbyPlayerCount = packet.getPlayerCount & 0xFF
        val memberId = packet.getPlayerId
        lobbyMembers.removeIf(_.id == memberId)
        lobbyMembers.add(LobbyMember(memberId, packet.getLobbyName))
        fire(lobbyUpdatedListener)

      case LobbyAction.PLAYER_JOINED =>
        currentLobbyPlayerCount = packet.getPlayerCount & 0xFF
        currentLobbyMaxPlayers = packet.getMaxPlayers & 0xFF
        val memberName = packet.getLobbyName
        val memberId = packet.getPlayerId
        lobbyMembers.removeIf(_.id == memberId)
        lobbyMembers.add(LobbyMember(memberId, memberName))
        addLobbySystemMessage(Messages.t("{0} joined the lobby", memberName))
        fire(lobbyUpdatedListener)

      case LobbyAction.PLAYER_LEFT =>
        currentLobbyPlayerCount = packet.getPlayerCount & 0xFF
        currentLobbyMaxPlayers = packet.getMaxPlayers & 0xFF
        val leftId = packet.getPlayerId
        import scala.jdk.CollectionConverters._
        val leftName = Option(packet.getLobbyName).filter(_.nonEmpty)
          .orElse(lobbyMembers.asScala.find(_.id == leftId).map(_.name))
          .getOrElse("?")
        lobbyMembers.removeIf(_.id == leftId)
        addLobbySystemMessage(Messages.t("{0} left the lobby", leftName))
        fire(lobbyUpdatedListener)

      case LobbyAction.CONFIG_UPDATE =>
        currentLobbyMapIndex = packet.getMapIndex & 0xFF
        currentLobbyDuration = packet.getDurationMinutes & 0xFF
        currentLobbyGameMode = packet.getGameMode
        currentLobbyTeamSize = packet.getTeamSize & 0xFF
        // Switching to Teams caps the lobby (and may drop bots), so the counts change too.
        currentLobbyPlayerCount = packet.getPlayerCount & 0xFF
        currentLobbyMaxPlayers = packet.getMaxPlayers & 0xFF
        fire(lobbyUpdatedListener)

      case LobbyAction.ACTION_FAILED =>
        val listener = lobbyActionFailedListener
        if (listener != null) listener(packet.getLobbyStatus)

      case LobbyAction.GAME_STARTING =>
        matchStarting()

      case LobbyAction.CHARACTER_SELECT =>
        lobbyCharacterSelections.put(packet.getPlayerId, packet.getCharacterId)
        fire(lobbyUpdatedListener)

      case LobbyAction.LOBBY_CLOSED =>
        clientState = ClientState.LOBBY_BROWSER
        currentLobbyId = 0
        if (lobbyClosedListener != null) lobbyClosedListener()

      case _ =>
        println(s"GameClient: Unknown lobby action ${packet.getAction}")
    }
  }

  /** Out of the lobby we were in: none, not its host, and back to the defaults it set. */
  private[game] def forgetLobby(): Unit = {
    currentLobbyId = 0
    isLobbyHost = false
    currentLobbyGameMode = 0
    currentLobbyTeamSize = 2
    lobbyMembers.clear()
  }

  /** The lobby roster with the team each member will play on, as `TeamAssignment` deals them
    * when the match starts: humans in join order, then bots in the order they were added. */
  def previewTeams: Seq[(LobbyMember, Byte)] = {
    import scala.jdk.CollectionConverters._
    val members = lobbyMembers.asScala.toVector
    val (bots, humans) = members.partition(m => TeamAssignment.isBot(m.id))
    val byId = members.map(m => m.id -> m).toMap
    TeamAssignment.assign(humans.map(_.id), bots.sortBy(_.id.getLeastSignificantBits).map(_.id))
      .map { case (id, team) => (byId(id), team) }
  }

  def selectCharacter(id: Byte): Unit = {
    selectedCharacterId = id
    if (currentLobbyId != 0) {
      val packet = new LobbyActionPacket(
        sequenceNumber.getAndIncrement(), localPlayerId, Packet.getCurrentTimestamp,
        LobbyAction.CHARACTER_SELECT, currentLobbyId,
        0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, "", id
      )
      send(packet)
    }
  }

  def requestLobbyList(): Unit = {
    val packet = new LobbyActionPacket(
      sequenceNumber.getAndIncrement(), localPlayerId, LobbyAction.LIST_REQUEST
    )
    send(packet)
  }

  // Both carry the character already selected (in practice, ranked or the last lobby), which
  // the lobby room shows as picked: the server enters us as it, not as whatever it last heard.
  def createLobby(name: String, mapIndex: Int, durationMinutes: Int): Unit = {
    isLobbyHost = true
    val packet = new LobbyActionPacket(
      sequenceNumber.getAndIncrement(), localPlayerId, Packet.getCurrentTimestamp, LobbyAction.CREATE, 0.toShort,
      mapIndex.toByte, durationMinutes.toByte, 0.toByte, Constants.MAX_LOBBY_PLAYERS.toByte,
      0.toByte, name, selectedCharacterId
    )
    send(packet)
  }

  def joinLobby(lobbyId: Short): Unit = {
    isLobbyHost = false
    val packet = new LobbyActionPacket(
      sequenceNumber.getAndIncrement(), localPlayerId, Packet.getCurrentTimestamp, LobbyAction.JOIN, lobbyId,
      characterId = selectedCharacterId
    )
    send(packet)
  }

  def leaveLobby(): Unit = {
    val packet = new LobbyActionPacket(
      sequenceNumber.getAndIncrement(), localPlayerId, LobbyAction.LEAVE
    )
    send(packet)
    clientState = ClientState.LOBBY_BROWSER
    forgetLobby()
  }

  def startGame(): Unit = {
    val packet = new LobbyActionPacket(
      sequenceNumber.getAndIncrement(), localPlayerId, LobbyAction.START
    )
    send(packet)
  }

  def addBot(): Unit = {
    val packet = new LobbyActionPacket(
      sequenceNumber.getAndIncrement(), localPlayerId, LobbyAction.ADD_BOT
    )
    send(packet)
  }

  def removeBot(): Unit = {
    val packet = new LobbyActionPacket(
      sequenceNumber.getAndIncrement(), localPlayerId, LobbyAction.REMOVE_BOT
    )
    send(packet)
  }

  def startPractice(): Unit = {
    isPracticeMode = true
    val packet = new LobbyActionPacket(
      sequenceNumber.getAndIncrement(), localPlayerId, Packet.getCurrentTimestamp,
      LobbyAction.PRACTICE_START, 0.toShort,
      0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, "", selectedCharacterId
    )
    send(packet)
  }

  def updateLobbyConfig(mapIndex: Int, durationMinutes: Int, gameMode: Byte = -1, teamSize: Int = -1): Unit = {
    val gm = if (gameMode >= 0) gameMode else currentLobbyGameMode
    val ts = if (teamSize > 0) teamSize else currentLobbyTeamSize
    val packet = new LobbyActionPacket(
      sequenceNumber.getAndIncrement(), localPlayerId, Packet.getCurrentTimestamp,
      LobbyAction.CONFIG_UPDATE, currentLobbyId,
      mapIndex.toByte, durationMinutes.toByte, 0.toByte, 0.toByte, 0.toByte, "",
      0.toByte, gm, ts.toByte
    )
    send(packet)
  }
}
