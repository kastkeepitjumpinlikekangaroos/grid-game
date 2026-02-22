package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.model.Direction
import com.gridgame.common.model.ItemType
import com.gridgame.common.model.Player
import com.gridgame.common.model.Position
import com.gridgame.common.model.Tile
import com.gridgame.common.protocol._
import io.netty.channel.Channel

import java.io.File
import java.net.InetSocketAddress
import java.util.UUID

class ClientHandler(registry: ClientRegistry, server: GameServer, projectileManager: ProjectileManager, itemManager: ItemManager, instance: GameInstance = null, validator: PacketValidator = new PacketValidator()) {

  /**
   * Process a packet received from a client.
   * @param packet the decoded packet
   * @param tcpChannel non-null if this packet arrived over TCP
   * @param udpSender non-null if this packet arrived over UDP (the sender's UDP address)
   * @return true if the packet should be broadcast to other players
   */
  def processPacket(packet: Packet, tcpChannel: Channel, udpSender: InetSocketAddress): Boolean = {
    val playerId = packet.getPlayerId

    packet.getType match {
      case PacketType.PLAYER_JOIN =>
        handlePlayerJoin(packet.asInstanceOf[PlayerJoinPacket], tcpChannel)

      case PacketType.PLAYER_UPDATE =>
        handlePlayerUpdate(packet.asInstanceOf[PlayerUpdatePacket], udpSender)

      case PacketType.PLAYER_LEAVE =>
        handlePlayerLeave(packet.asInstanceOf[PlayerLeavePacket])

      case PacketType.HEARTBEAT =>
        handleHeartbeat(playerId, udpSender)

      case PacketType.PROJECTILE_UPDATE =>
        handleProjectileUpdate(packet.asInstanceOf[ProjectilePacket])

      case PacketType.ITEM_UPDATE =>
        handleItemUpdate(packet.asInstanceOf[ItemPacket])

      case _ =>
        System.err.println(s"Unknown packet type: ${packet.getType}")
        false
    }
  }

  private def handleProjectileUpdate(packet: ProjectilePacket): Boolean = {
    // Only handle spawn requests from clients
    if (packet.getAction != ProjectileAction.SPAWN) {
      return false // Ignore non-spawn packets from clients
    }

    val playerId = packet.getPlayerId
    val player = registry.get(playerId)

    if (player == null) {
      System.err.println(s"Received projectile spawn from unknown player: $playerId")
      return false
    }

    if (player.isFrozen) {
      return false
    }

    // Validate projectile velocity: reject NaN, Infinite, or excessive magnitude
    val pdx = packet.getDx
    val pdy = packet.getDy
    if (java.lang.Float.isNaN(pdx) || java.lang.Float.isNaN(pdy) ||
        java.lang.Float.isInfinite(pdx) || java.lang.Float.isInfinite(pdy)) {
      System.err.println(s"ClientHandler: Player ${playerId.toString.substring(0, 8)} projectile velocity NaN/Inf")
      return false
    }
    if (pdx * pdx + pdy * pdy > 2.0f) {
      System.err.println(s"ClientHandler: Player ${playerId.toString.substring(0, 8)} projectile velocity too large")
      return false
    }

    if (!validator.validateProjectileSpawn(packet, player)) {
      return false
    }

    // Spawn projectile at player's position with velocity from packet
    val projectile = projectileManager.spawnProjectile(
      playerId,
      packet.getX.toInt,
      packet.getY.toInt,
      packet.getDx,
      packet.getDy,
      packet.getColorRGB,
      packet.getChargeLevel,
      packet.getProjectileType
    )

    if (projectile == null) return false // Per-player projectile cap reached

    // Broadcast spawn to instance or all clients
    if (instance != null) {
      instance.broadcastProjectileSpawn(projectile)
    } else {
      server.broadcastProjectileSpawn(projectile)
    }

    false // Don't auto-broadcast; we handled it
  }

  private def handlePlayerJoin(packet: PlayerJoinPacket, tcpChannel: Channel): Boolean = {
    val playerId = packet.getPlayerId

    // Send world info to the joining player via TCP
    val wf = if (instance != null) instance.worldFile else server.worldFile
    if (wf.nonEmpty) {
      val worldFileName = new File(wf).getName
      val worldInfoPacket = new WorldInfoPacket(server.getNextSequenceNumber, worldFileName)
      println(s"Sending world info to client: $worldFileName")
      server.sendPacketViaChannel(worldInfoPacket, tcpChannel)
    } else {
      println("No world file configured on server")
    }

    if (registry.contains(playerId)) {
      println(s"Player rejoining: $playerId")
      val existing = registry.get(playerId)
      // Validate rejoin position — only accept if in-bounds and walkable
      val rejoinPos = packet.getPosition
      val world = if (instance != null) instance.world else server.getWorld
      if (world != null && rejoinPos.getX >= 0 && rejoinPos.getX < world.width &&
          rejoinPos.getY >= 0 && rejoinPos.getY < world.height && world.isWalkable(rejoinPos.getX, rejoinPos.getY)) {
        existing.setPosition(rejoinPos)
      } // else keep server-side position (prevents teleport-on-rejoin)
      existing.setColorRGB(packet.getColorRGB)
      existing.setName(packet.getPlayerName)
      existing.setHealth(existing.getMaxHealth)
      existing.setTcpChannel(tcpChannel)
      existing.updateHeartbeat()

      // Send existing players, items, and tile modifications to rejoining player
      sendExistingPlayers(playerId, existing)
      sendExistingItems(existing)
      sendInventoryContents(playerId, existing)
      if (instance != null) instance.sendModifiedTiles(existing)
      else server.sendModifiedTiles(existing)

      true
    } else {
      val charDef = com.gridgame.common.model.CharacterDef.get(packet.getCharacterId)
      if (charDef == null) {
        System.err.println(s"ClientHandler: Player ${playerId.toString.substring(0, 8)} invalid character ID: ${packet.getCharacterId}")
        return false
      }
      // Validate new player join position against world bounds and walkability
      val joinPos = packet.getPosition
      val world = if (instance != null) instance.world else server.getWorld
      val validPos = if (world != null && joinPos.getX >= 0 && joinPos.getX < world.width &&
          joinPos.getY >= 0 && joinPos.getY < world.height && world.isWalkable(joinPos.getX, joinPos.getY)) {
        joinPos
      } else if (world != null) {
        world.getRandomSpawnPoint
      } else {
        joinPos // No world loaded yet — accept client position
      }
      val player = new Player(playerId, packet.getPlayerName, validPos, packet.getColorRGB, charDef.maxHealth, charDef.maxHealth)
      player.setCharacterId(packet.getCharacterId)
      player.setTcpChannel(tcpChannel)

      registry.add(player)

      println(s"Player joined: ${playerId.toString.substring(0, 8)} ('${packet.getPlayerName}') at ${packet.getPosition} with health ${player.getHealth}")

      // Send existing players, items, and tile modifications to new player
      sendExistingPlayers(playerId, player)
      sendExistingItems(player)
      sendInventoryContents(playerId, player)
      if (instance != null) instance.sendModifiedTiles(player)
      else server.sendModifiedTiles(player)

      true
    }
  }

  private def handlePlayerUpdate(packet: PlayerUpdatePacket, udpSender: InetSocketAddress): Boolean = {
    val playerId = packet.getPlayerId
    var player = registry.get(playerId)

    // Validate movement if player exists
    if (player != null) {
      val world = if (instance != null) instance.world else server.getWorld
      if (world != null) {
        if (!validator.validateMovement(packet, player, world)) return false
      } else if (instance != null) {
        // World should be loaded for active game instances — reject if missing
        return false
      }
    }

    if (player == null) {
      // Reject updates from unknown players — they must join via PLAYER_JOIN first
      return false
    } else {
      val oldPos = player.getPosition
      val newPos = packet.getPosition
      val dx = newPos.getX - oldPos.getX
      val dy = newPos.getY - oldPos.getY
      if (dx != 0 || dy != 0) {
        player.setDirection(Direction.fromMovement(dx, dy))
      }
      // Don't overwrite position if player was recently teleported by server (e.g. haunt)
      // Don't allow movement if player is frozen
      if (!player.isServerTeleported && !player.isFrozen && !player.isRooted) {
        player.setPosition(newPos)
      }
      player.setColorRGB(packet.getColorRGB)
      // Character ID is set on join only — ignore mid-game character changes
      if (udpSender != null) {
        player.setUdpAddress(udpSender)
      }
    }

    // Check for item pickup
    val pos = packet.getPosition
    itemManager.checkPickup(playerId, pos.getX, pos.getY).foreach { event =>
      if (instance != null) {
        instance.broadcastItemPickup(event.item, event.playerId)
      } else {
        server.broadcastItemPickup(event.item, event.playerId)
      }
    }

    true
  }

  private def handlePlayerLeave(packet: PlayerLeavePacket): Boolean = {
    val playerId = packet.getPlayerId
    val player = registry.get(playerId)

    if (player != null) {
      registry.remove(playerId)
      itemManager.clearInventory(playerId)
      validator.removePlayer(playerId)
      println(s"Player left: ${playerId.toString.substring(0, 8)} ('${player.getName}')")
    }

    true
  }

  private def handleHeartbeat(playerId: UUID, udpSender: InetSocketAddress): Boolean = {
    val player = registry.get(playerId)

    if (player != null) {
      player.updateHeartbeat()
      if (udpSender != null) {
        player.setUdpAddress(udpSender)
      }
    } else {
      System.err.println(s"Received heartbeat from unknown player: $playerId")
    }

    false
  }

  private def handleItemUpdate(packet: ItemPacket): Boolean = {
    if (packet.getAction == ItemAction.USE) {
      val playerId = packet.getPlayerId
      val removedItem = itemManager.removeFromInventory(playerId, packet.getItemId)
      if (removedItem == null) {
        System.err.println(s"ClientHandler: Item USE failed — item ${packet.getItemId} not in server inventory for ${playerId.toString.substring(0, 8)}")
        return false
      }

      val player = registry.get(playerId)
      if (player != null) {
        removedItem.itemType match {
          case ItemType.Heart =>
            player.setHealth(Math.min(player.getMaxHealth, Constants.MAX_HEALTH))
            val flags = (if (player.hasShield) 0x01 else 0) |
                        (if (player.hasGemBoost) 0x02 else 0) |
                        (if (player.isFrozen) 0x04 else 0)
            val updatePacket = new PlayerUpdatePacket(
              server.getNextSequenceNumber,
              playerId,
              player.getPosition,
              player.getColorRGB,
              player.getHealth,
              0,
              flags
            )
            if (instance != null) instance.broadcastPlayerUpdate(updatePacket)
            else server.broadcastPlayerUpdate(updatePacket)
            println(s"ClientHandler: Player ${playerId.toString.substring(0, 8)} healed to full")

          case ItemType.Shield =>
            player.setShieldUntil(System.currentTimeMillis() + Constants.SHIELD_DURATION_MS)
            println(s"ClientHandler: Player ${playerId.toString.substring(0, 8)} shield activated")

          case ItemType.Gem =>
            player.setGemBoostUntil(System.currentTimeMillis() + Constants.GEM_DURATION_MS)
            println(s"ClientHandler: Player ${playerId.toString.substring(0, 8)} gem boost activated")

          case ItemType.Fence =>
            if (!placeFence(player, packet.getX, packet.getY)) {
              // Placement failed — restore item to inventory so player can retry
              itemManager.addToInventory(playerId, removedItem)
              // Notify client to re-add item to their local inventory
              val restorePacket = new ItemPacket(
                server.getNextSequenceNumber,
                playerId,
                removedItem.x, removedItem.y,
                removedItem.itemType.id,
                removedItem.id,
                ItemAction.INVENTORY
              )
              try {
                server.sendRawToPlayer(restorePacket.serialize(), true, player)
              } catch { case _: Exception => }
            }

          case ItemType.Star =>
            // Star teleports the player to cursor — validate and apply position server-side
            val targetX = packet.getX
            val targetY = packet.getY
            val w = if (instance != null) instance.world else server.getWorld
            if (w != null && targetX >= 0 && targetX < w.width && targetY >= 0 && targetY < w.height && w.isWalkable(targetX, targetY)) {
              player.setPosition(new Position(targetX, targetY))
            }
            // Also allow the next UDP position update through speed validation
            // (handles race where UDP arrives before this TCP packet)
            validator.allowItemTeleport(playerId)
          case _ =>
        }
      }
    }
    false
  }

  /** Try to place a fence. Returns true if at least one tile was placed. */
  private def placeFence(player: Player, targetX: Int, targetY: Int): Boolean = {
    val w = if (instance != null) instance.world else server.getWorld
    if (w == null) return false

    // Bounds check
    if (targetX < 0 || targetX >= w.width || targetY < 0 || targetY >= w.height) {
      System.err.println(s"ClientHandler: Fence out of bounds ($targetX,$targetY)")
      return false
    }

    // Center tile must be walkable
    if (!w.isWalkable(targetX, targetY)) {
      System.err.println(s"ClientHandler: Fence center tile not walkable ($targetX,$targetY)")
      return false
    }

    // Determine the perpendicular offsets based on player facing direction
    val (perpDx, perpDy) = player.getDirection match {
      case Direction.Up | Direction.Down    => (1, 0)
      case Direction.Left | Direction.Right => (0, 1)
    }

    // Place 3 tiles centered on the target position: center and ±1 perpendicular
    val positions = Seq(
      (targetX - perpDx, targetY - perpDy),
      (targetX, targetY),
      (targetX + perpDx, targetY + perpDy)
    )

    var placed = 0
    positions.foreach { case (tx, ty) =>
      if (tx >= 0 && tx < w.width && ty >= 0 && ty < w.height && w.isWalkable(tx, ty)) {
        if (w.setTile(tx, ty, Tile.Fence)) {
          if (instance != null) instance.broadcastTileUpdate(player.getId, tx, ty, Tile.Fence.id)
          else server.broadcastTileUpdate(player.getId, tx, ty, Tile.Fence.id)
          placed += 1
        }
      }
    }

    if (placed > 0) {
      println(s"ClientHandler: Player ${player.getId.toString.substring(0, 8)} placed $placed fence tiles at ($targetX, $targetY)")
    } else {
      System.err.println(s"ClientHandler: Fence placement failed at ($targetX,$targetY) — no walkable tiles")
    }
    placed > 0
  }

  private def sendExistingPlayers(joiningPlayerId: UUID, joiningPlayer: Player): Unit = {
    import scala.jdk.CollectionConverters._
    registry.getAll.asScala.foreach { existing =>
      if (!existing.getId.equals(joiningPlayerId)) {
        val packet = new PlayerJoinPacket(
          server.getNextSequenceNumber,
          existing.getId,
          existing.getPosition,
          existing.getColorRGB,
          existing.getName,
          existing.getHealth,
          existing.getCharacterId
        )
        server.sendPacketToPlayer(packet, joiningPlayer)
      }
    }
  }

  private def sendExistingItems(player: Player): Unit = {
    val zeroUUID = new UUID(0L, 0L)
    itemManager.getAll.foreach { item =>
      val packet = new ItemPacket(
        server.getNextSequenceNumber,
        zeroUUID,
        item.x, item.y,
        item.itemType.id,
        item.id,
        ItemAction.SPAWN
      )
      server.sendPacketToPlayer(packet, player)
    }
  }

  private def sendInventoryContents(playerId: UUID, player: Player): Unit = {
    itemManager.getInventory(playerId).foreach { item =>
      val packet = new ItemPacket(
        server.getNextSequenceNumber,
        playerId,
        item.x, item.y,
        item.itemType.id,
        item.id,
        ItemAction.INVENTORY
      )
      server.sendPacketToPlayer(packet, player)
    }
  }

  def handleTimeout(playerId: UUID, sequenceNumber: Int): PlayerLeavePacket = {
    val player = registry.get(playerId)
    if (player != null) {
      registry.remove(playerId)
      itemManager.clearInventory(playerId)
      println(s"Player timed out: ${playerId.toString.substring(0, 8)} ('${player.getName}')")
      new PlayerLeavePacket(sequenceNumber, playerId)
    } else {
      null
    }
  }

  def handleDisconnect(channel: Channel): Unit = {
    val player = registry.getByChannel(channel)
    if (player != null) {
      val playerId = player.getId
      registry.remove(playerId)
      itemManager.clearInventory(playerId)
      validator.removePlayer(playerId)
      println(s"Player disconnected (TCP): ${playerId.toString.substring(0, 8)} ('${player.getName}')")

      // Broadcast leave to remaining players
      val leavePacket = new PlayerLeavePacket(server.getNextSequenceNumber, playerId)
      if (instance != null) {
        instance.broadcastToInstance(leavePacket)
      } else {
        server.broadcastToAllPlayers(leavePacket)
      }
    }
  }
}
