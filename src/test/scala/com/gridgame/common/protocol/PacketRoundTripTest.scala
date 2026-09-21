package com.gridgame.common.protocol

import com.gridgame.common.Constants
import com.gridgame.common.WorldRegistry
import com.gridgame.common.model.Position
import com.gridgame.common.model.ProjectileType
import org.junit.Assert._
import org.junit.Test

import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Every packet goes over the wire as 64 bytes and comes back as the packet that was sent. The
 * client and server share these classes, so a field that doesn't survive the trip is wrong on
 * both sides at once and no other test would notice.
 */
class PacketRoundTripTest {
  private val id = UUID.fromString("0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0")
  private val other = UUID.fromString("11111111-2222-3333-4444-555555555555")

  private def trip[P <: Packet](p: P): P = {
    val bytes = p.serialize()
    assertEquals(s"${p.getType} payload size", Constants.PACKET_PAYLOAD_SIZE, bytes.length)
    val back = PacketSerializer.deserialize(bytes)
    assertEquals(p.getType, back.getType)
    assertEquals(p.getSequenceNumber, back.getSequenceNumber)
    back.asInstanceOf[P]
  }

  @Test def playerUpdateKeepsEveryField(): Unit = {
    val p = trip(new PlayerUpdatePacket(77, id, 1234, new Position(999, 0), 0xFF102030, 87, 100,
      0xFF, 111.toByte, 2.toByte, 123456))
    assertEquals(id, p.getPlayerId)
    assertEquals(new Position(999, 0), p.getPosition)
    assertEquals(0xFF102030, p.getColorRGB)
    assertEquals(87, p.getHealth)
    assertEquals(100, p.getChargeLevel)
    assertEquals("all eight status flags, the top one included", 0xFF, p.getEffectFlags)
    assertEquals(111.toByte, p.getCharacterId)
    assertEquals(2.toByte, p.getTeamId)
    assertEquals(123456, p.getServerMoves)
  }

  @Test def playerUpdateKeepsTheSecondFlagByteAimAndSlowStrength(): Unit = {
    val p = trip(new PlayerUpdatePacket(78, id, 1234, new Position(4, 9), 0, 100, 0, 0x84,
      1.toByte, 0.toByte, 5, 0x07, 40000, 30))
    assertEquals("stunned, poisoned and a barrier", 0x07, p.getEffectFlags2)
    assertEquals(40000, p.getAimAngle)
    assertEquals("a Slow(_, 0.3f) reaches the client as 30%", 30, p.getSlowPercent)
    // The first flag byte is untouched by the second
    assertEquals(0x84, p.getEffectFlags)
    assertEquals(5, p.getServerMoves)
  }

  @Test def eachSecondaryStatusFlagSurvivesOnItsOwn(): Unit = {
    for (bit <- 0 until 8) {
      val flag = 1 << bit
      val p = trip(new PlayerUpdatePacket(1, id, 0, new Position(5, 5), 0, 100, 0, 0, 0.toByte,
        0.toByte, 0, flag))
      assertEquals(s"flag2 0x${flag.toHexString}", flag, p.getEffectFlags2)
    }
  }

  @Test def aimAnglesMakeTheRoundTripToWithinAStep(): Unit = {
    // 16 bits over a full turn: a step is 2pi/65536, well under a pixel of aim at any range
    val step = 2 * Math.PI / 65536
    for (i <- 0 until 16) {
      val radians = i * (2 * Math.PI / 16)
      val p = trip(new PlayerUpdatePacket(1, id, 0, new Position(5, 5), 0, 100, 0, 0, 0.toByte,
        0.toByte, 0, 0, PlayerUpdatePacket.encodeAimAngle(radians)))
      assertEquals(f"$radians%.3f rad", radians, p.aimAngleRadians, step)
    }
    assertEquals("a full turn wraps to zero", 0, PlayerUpdatePacket.encodeAimAngle(2 * Math.PI))
    assertEquals("and so does a negative angle", PlayerUpdatePacket.encodeAimAngle(1.0),
      PlayerUpdatePacket.encodeAimAngle(1.0 - 2 * Math.PI))
  }

  @Test def slowPercentCoversTheWholeRange(): Unit = {
    for (pct <- Seq(0, 1, 30, 50, 60, 100)) {
      val p = trip(new PlayerUpdatePacket(1, id, 0, new Position(5, 5), 0, 100, 0, 0, 0.toByte,
        0.toByte, 0, 0, 0, pct))
      assertEquals(pct, p.getSlowPercent)
    }
  }

  @Test def eachStatusFlagSurvivesOnItsOwn(): Unit = {
    for (bit <- 0 until 8) {
      val flag = 1 << bit
      val p = trip(new PlayerUpdatePacket(1, id, new Position(5, 5), 0, 100, 0, flag))
      assertEquals(s"flag 0x${flag.toHexString}", flag, p.getEffectFlags)
    }
  }

  @Test def playerJoinKeepsEveryFieldAndCutsLongNames(): Unit = {
    val p = trip(new PlayerJoinPacket(3, id, new Position(12, 34), 0x00ABCDEF, "Wizardly", 120, 42.toByte, 1.toByte))
    assertEquals(new Position(12, 34), p.getPosition)
    assertEquals("Wizardly", p.getPlayerName)
    assertEquals(120, p.getHealth)
    assertEquals(42.toByte, p.getCharacterId)
    assertEquals(1.toByte, p.getTeamId)
    val long = trip(new PlayerJoinPacket(4, id, new Position(1, 1), 0, "abcdefghijklmnopqrstuvwxyz"))
    assertEquals("names are cut to the 21-byte field", "abcdefghijklmnopqrstu", long.getPlayerName)
  }

  @Test def projectileKeepsPositionTypeTargetAndAction(): Unit = {
    for (action <- Seq(ProjectileAction.SPAWN, ProjectileAction.MOVE, ProjectileAction.HIT,
                       ProjectileAction.DESPAWN, ProjectileAction.PIERCE)) {
      val p = trip(new ProjectilePacket(9, id, 12.375f, 998.5f, 0x44556677, 31337, 0.6f, -0.8f, action,
        other, 100.toByte, ProjectileType.VENOM_BOLT_LIGHT))
      assertEquals(12.375f, p.getX, 0f)
      assertEquals(998.5f, p.getY, 0f)
      assertEquals(31337, p.getProjectileId)
      assertEquals(action, p.getAction)
      assertEquals(other, p.getTargetId)
      assertEquals(100, p.getChargeLevel)
      assertEquals("types past 127 travel as negative bytes", ProjectileType.VENOM_BOLT_LIGHT, p.getProjectileType)
      assertEquals(0.6f, p.getDx, 1f / 32767)
      assertEquals(-0.8f, p.getDy, 1f / 32767)
    }
  }

  @Test def aProjectileWithNoTargetHasNone(): Unit = {
    val p = trip(new ProjectilePacket(1, id, 1f, 1f, 0, 5, 1f, 0f, ProjectileAction.MOVE))
    assertNull(p.getTargetId)
  }

  @Test def unitHeadingsSurviveInEveryDirection(): Unit = {
    for (deg <- 0 until 360 by 15) {
      val a = Math.toRadians(deg)
      val (dx, dy) = (Math.cos(a).toFloat, Math.sin(a).toFloat)
      val p = trip(new ProjectilePacket(1, id, 1f, 1f, 0, 5, dx, dy, ProjectileAction.MOVE))
      assertEquals(s"$deg degrees", dx, p.getDx, 1e-4f)
      assertEquals(s"$deg degrees", dy, p.getDy, 1e-4f)
    }
  }

  @Test def aSpawnRequestCarriesItsAttackSlot(): Unit = {
    for (slot <- Seq(AttackSlot.PRIMARY, AttackSlot.Q, AttackSlot.E, AttackSlot.BURST)) {
      val p = trip(ProjectilePacket.spawnRequest(5, id, 3f, 4f, 0, 1f, 0f, 0.toByte, ProjectileType.ARROW, slot))
      assertEquals(ProjectileAction.SPAWN, p.getAction)
      assertEquals(slot, p.getAttackSlot)
      assertEquals(ProjectileType.ARROW, p.getProjectileType)
    }
  }

  @Test def burstDirectionsAreTheEightCompassPoints(): Unit = {
    val dirs = AttackSlot.BurstDirections
    assertEquals(8, dirs.size)
    dirs.foreach { case (dx, dy) => assertEquals(1.0, Math.hypot(dx, dy), 1e-6) }
    val degrees = dirs.map { case (dx, dy) => Math.floorMod(Math.round(Math.toDegrees(Math.atan2(dy, dx))), 360L) }.sorted
    assertEquals(Seq(0L, 45L, 90L, 135L, 180L, 225L, 270L, 315L), degrees)
  }

  @Test def itemKeepsItsFields(): Unit = {
    for (action <- Seq(ItemAction.SPAWN, ItemAction.PICKUP, ItemAction.INVENTORY, ItemAction.USE, ItemAction.USE_REJECTED)) {
      val p = trip(new ItemPacket(2, id, 40, 50, 3.toByte, 901, action))
      assertEquals((40, 50), (p.getX, p.getY))
      assertEquals(3.toByte, p.getItemTypeId)
      assertEquals(901, p.getItemId)
      assertEquals(action, p.getAction)
    }
  }

  @Test def tileUpdateKeepsItsFields(): Unit = {
    val p = trip(new TileUpdatePacket(8, id, 17, 23, 12))
    assertEquals((17, 23, 12), (p.getTileX, p.getTileY, p.getTileId))
  }

  @Test def everyRegisteredWorldNameFitsTheWorldInfoPacket(): Unit = {
    for (i <- 0 until WorldRegistry.size) {
      val name = WorldRegistry.getFilename(i)
      assertEquals(name, trip(new WorldInfoPacket(1, name)).getWorldFile)
    }
  }

  @Test def lobbyActionKeepsEveryField(): Unit = {
    val p = trip(new LobbyActionPacket(6, id, 99, LobbyAction.CONFIG_UPDATE, 4321.toShort, 3.toByte, 15.toByte,
      7.toByte, 8.toByte, LobbyStatusCode, "The Lobby With A Long Name", 88.toByte, 1.toByte, 4.toByte))
    assertEquals(LobbyAction.CONFIG_UPDATE, p.getAction)
    assertEquals(4321.toShort, p.getLobbyId)
    assertEquals(3.toByte, p.getMapIndex)
    assertEquals(15.toByte, p.getDurationMinutes)
    assertEquals(7.toByte, p.getPlayerCount)
    assertEquals(8.toByte, p.getMaxPlayers)
    assertEquals(LobbyStatusCode, p.getLobbyStatus)
    assertEquals("cut to 24 bytes", "The Lobby With A Long Na", p.getLobbyName)
    assertEquals(88.toByte, p.getCharacterId)
    assertEquals(1.toByte, p.getGameMode)
    assertEquals(4.toByte, p.getTeamSize)
  }

  private val LobbyStatusCode: Byte = 2

  @Test def aJoinRequestCarriesTheCharacter(): Unit = {
    // GameClient.joinLobby: the character rides in the join, so the server enters us as it
    val p = trip(new LobbyActionPacket(1, id, 0, LobbyAction.JOIN, 12.toShort, characterId = 57.toByte))
    assertEquals(LobbyAction.JOIN, p.getAction)
    assertEquals(12.toShort, p.getLobbyId)
    assertEquals(57.toByte, p.getCharacterId)
  }

  @Test def gameEventKeepsEveryField(): Unit = {
    val p = trip(new GameEventPacket(4, id, 0, GameEvent.SCORE_ENTRY, 777.toShort, 299, 31.toShort, 12.toShort,
      other, 3.toByte, 230.toShort, 17.toShort, 2.toByte))
    assertEquals(GameEvent.SCORE_ENTRY, p.getEventType)
    assertEquals(777.toShort, p.getGameId)
    assertEquals(299, p.getRemainingSeconds)
    assertEquals(31.toShort, p.getKills)
    assertEquals(12.toShort, p.getDeaths)
    assertEquals(other, p.getTargetId)
    assertEquals(3.toByte, p.getRank)
    assertEquals(230.toShort, p.getSpawnX)
    assertEquals(17.toShort, p.getSpawnY)
    assertEquals(2.toByte, p.getTeamId)
    assertNull(trip(new GameEventPacket(5, id, GameEvent.TIME_SYNC, 1.toShort, 10, 0.toShort, 0.toShort,
      null, 0.toByte, 0.toShort, 0.toShort)).getTargetId)
  }

  @Test def authRequestKeepsCredentialsUpToTheirFields(): Unit = {
    val p = trip(new AuthRequestPacket(0, AuthAction.SIGNUP, "user_name-20chars_ok", "password1234567890ab"))
    assertEquals(AuthAction.SIGNUP, p.getAction)
    assertEquals("user_name-20chars_ok", p.getUsername)
    assertEquals("password1234567890ab", p.getPassword)
  }

  @Test def everyAuthReplyReachesTheClientWhole(): Unit = {
    // The message field is 23 bytes. "Password must be 6+ chars" didn't fit, and the login
    // screen showed "Password must be 6+ cha".
    val replies = Seq(AuthRules.PasswordTooShort, AuthRules.InvalidUsername, AuthRules.UsernameTaken,
      AuthRules.InvalidCredentials, AuthRules.TooManyAttempts, AuthRules.AccountCreated, AuthRules.LoginSuccessful)
    for (msg <- replies) {
      assertTrue(s"'$msg' is ${msg.getBytes(StandardCharsets.UTF_8).length} bytes",
        msg.getBytes(StandardCharsets.UTF_8).length <= 23)
      val p = trip(new AuthResponsePacket(1, false, null, msg))
      assertEquals(msg, p.getMessage)
    }
    val ok = trip(new AuthResponsePacket(2, true, id, AuthRules.LoginSuccessful))
    assertTrue(ok.getSuccess)
    assertEquals(id, ok.getAssignedUUID)
  }

  @Test def matchHistoryEntryStatsAndEnd(): Unit = {
    val e = trip(new MatchHistoryPacket(1, id, 0, MatchHistoryAction.ENTRY, 4000000, 3.toByte, 20.toByte,
      Int.MaxValue, 250.toShort, 40.toShort, 7.toByte, 32.toByte, matchType = 4.toByte))
    assertEquals(MatchHistoryAction.ENTRY, e.getAction)
    assertEquals(4000000, e.getMatchId)
    assertEquals(3.toByte, e.getMapIndex)
    assertEquals(20.toByte, e.getDuration)
    assertEquals(Int.MaxValue, e.getPlayedAt)
    assertEquals(250.toShort, e.getKills)
    assertEquals(40.toShort, e.getDeaths)
    assertEquals(7.toByte, e.getRank)
    assertEquals(32.toByte, e.getTotalPlayers)
    assertEquals(4.toByte, e.getMatchType)
    val s = trip(new MatchHistoryPacket(2, id, 0, MatchHistoryAction.STATS, totalKills = 1234, totalDeaths = 567,
      matchesPlayed = 89, wins = 10, elo = 1543.toShort, oldElo = 1521.toShort))
    assertEquals((1234, 567, 89, 10), (s.getTotalKills, s.getTotalDeaths, s.getMatchesPlayed, s.getWins))
    assertEquals(1543.toShort, s.getElo)
    assertEquals(1521.toShort, s.getOldElo)
    assertEquals(MatchHistoryAction.END, trip(new MatchHistoryPacket(3, id, MatchHistoryAction.END)).getAction)
  }

  @Test def leaderboardEntryAndEnd(): Unit = {
    val e = trip(new LeaderboardPacket(1, id, 0, LeaderboardAction.ENTRY, 50.toByte, 1999.toShort, 321, 654, "top_player"))
    assertEquals((50.toByte, 1999.toShort, 321, 654, "top_player"),
      (e.getRank, e.getElo, e.getWins, e.getMatchesPlayed, e.getUsername))
    assertEquals(LeaderboardAction.END, trip(new LeaderboardPacket(2, id, LeaderboardAction.END)).getAction)
  }

  @Test def rankedQueueKeepsEveryField(): Unit = {
    val p = trip(new RankedQueuePacket(1, id, 0, RankedQueueAction.MATCH_FOUND, 99.toByte, 6.toByte, 1234.toShort,
      75, 300.toShort, 2.toByte, 5.toByte, 6.toByte, 6.toByte, "Ranked Teams", RankedQueueMode.TEAMS))
    assertEquals(RankedQueueAction.MATCH_FOUND, p.getAction)
    assertEquals(99.toByte, p.getCharacterId)
    assertEquals(6.toByte, p.getQueueSize)
    assertEquals(1234.toShort, p.getElo)
    assertEquals(75, p.getWaitTimeSeconds)
    assertEquals(300.toShort, p.getLobbyId)
    assertEquals(2.toByte, p.getMapIndex)
    assertEquals(5.toByte, p.getDurationMinutes)
    assertEquals((6.toByte, 6.toByte), (p.getPlayerCount, p.getMaxPlayers))
    assertEquals("Ranked Teams", p.getLobbyName)
    assertEquals(RankedQueueMode.TEAMS, p.getMode)
  }

  @Test def sessionTokenKeepsAll32Bytes(): Unit = {
    val token = Array.tabulate[Byte](32)(i => (i * 7 - 100).toByte)
    assertArrayEquals(token, trip(new SessionTokenPacket(1, id, token)).getSessionToken)
  }

  @Test def chatKeepsScopeAndTextUpToItsField(): Unit = {
    val msg = "gg well played, rematch?"
    val p = trip(new ChatMessagePacket(1, id, 0, ChatScope.TEAM, msg))
    assertEquals(ChatScope.TEAM, p.getScope)
    assertEquals(msg, p.getMessage)
    val long = trip(new ChatMessagePacket(2, id, 0, ChatScope.GAME, "x" * 60))
    assertEquals("x" * Constants.MAX_CHAT_MESSAGE_LEN, long.getMessage)
  }

  @Test def heartbeatAndLeaveKeepTheirPlayer(): Unit = {
    assertEquals(id, trip(new HeartbeatPacket(1, id)).getPlayerId)
    assertEquals(id, trip(new PlayerLeavePacket(2, id)).getPlayerId)
  }

  @Test(expected = classOf[IllegalArgumentException])
  def anUnknownTypeIsRefused(): Unit = {
    val bytes = new Array[Byte](Constants.PACKET_PAYLOAD_SIZE)
    bytes(0) = 0x7F
    PacketSerializer.deserialize(bytes)
  }

  @Test(expected = classOf[IllegalArgumentException])
  def aShortPacketIsRefused(): Unit = PacketSerializer.deserialize(new Array[Byte](10))
}
