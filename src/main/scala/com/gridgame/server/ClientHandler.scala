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

class ClientHandler(registry: ClientRegistry, server: GameServer, projectileManager: ProjectileManager, itemManager: ItemManager) {

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

    // Broadcast spawn to all clients
    server.broadcastProjectileSpawn(projectile)

    false // Don't auto-broadcast; we handled it
  }

  private def handlePlayerJoin(packet: PlayerJoinPacket, tcpChannel: Channel): Boolean = {
    val playerId = packet.getPlayerId

    // Send world info to the joining player via TCP
    if (server.worldFile.nonEmpty) {
      val worldFileName = new File(server.worldFile).getName
      val worldInfoPacket = new WorldInfoPacket(server.getNextSequenceNumber, worldFileName)
      println(s"Sending world info to client: $worldFileName")
      server.sendPacketViaChannel(worldInfoPacket, tcpChannel)
    } else {
      println("No world file configured on server")
    }

    if (registry.contains(playerId)) {
      println(s"Player rejoining: $playerId")
      val existing = registry.get(playerId)
      existing.setPosition(packet.getPosition)
      existing.setColorRGB(packet.getColorRGB)
      existing.setName(packet.getPlayerName)
      existing.setHealth(Constants.MAX_HEALTH)
      existing.setTcpChannel(tcpChannel)
      existing.updateHeartbeat()

      // Send existing players, items, and tile modifications to rejoining player
      sendExistingPlayers(playerId, existing)
      sendExistingItems(existing)
      sendInventoryContents(playerId, existing)
      server.sendModifiedTiles(existing)

      true
    } else {
      val player = new Player(playerId, packet.getPlayerName, packet.getPosition, packet.getColorRGB, Constants.MAX_HEALTH)
      player.setTcpChannel(tcpChannel)

      registry.add(player)

      println(s"Player joined: ${playerId.toString.substring(0, 8)} ('${packet.getPlayerName}') at ${packet.getPosition} with health ${player.getHealth}")

      // Send existing players, items, and tile modifications to new player
      sendExistingPlayers(playerId, player)
      sendExistingItems(player)
      sendInventoryContents(playerId, player)
      server.sendModifiedTiles(player)

      true
    }
  }

  private def handlePlayerUpdate(packet: PlayerUpdatePacket, udpSender: InetSocketAddress): Boolean = {
    val playerId = packet.getPlayerId
    var player = registry.get(playerId)

    if (player == null) {
      System.err.println(s"Received update for unknown player: $playerId")
      player = new Player(playerId, "Player", packet.getPosition, packet.getColorRGB)
      if (udpSender != null) {
        player.setUdpAddress(udpSender)
      }
      registry.add(player)
    } else {
      val oldPos = player.getPosition
      val newPos = packet.getPosition
      val dx = newPos.getX - oldPos.getX
      val dy = newPos.getY - oldPos.getY
      if (dx != 0 || dy != 0) {
        player.setDirection(Direction.fromMovement(dx, dy))
      }
      player.setPosition(newPos)
      player.setColorRGB(packet.getColorRGB)
      if (udpSender != null) {
        player.setUdpAddress(udpSender)
      }
    }

    // Check for item pickup
    val pos = packet.getPosition
    itemManager.checkPickup(playerId, pos.getX, pos.getY).foreach { event =>
      server.broadcastItemPickup(event.item, event.playerId)
    }

    true
  }

  private def handlePlayerLeave(packet: PlayerLeavePacket): Boolean = {
    val playerId = packet.getPlayerId
    val player = registry.get(playerId)

    if (player != null) {
      registry.remove(playerId)
      itemManager.clearInventory(playerId)
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
      itemManager.removeFromInventory(packet.getPlayerId, packet.getItemId)

      val playerId = packet.getPlayerId
      val player = registry.get(playerId)
      if (player != null) {
        packet.getItemType match {
          case ItemType.Heart =>
            player.setHealth(Constants.MAX_HEALTH)
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
            server.broadcastPlayerUpdate(updatePacket)
            println(s"ClientHandler: Player ${playerId.toString.substring(0, 8)} healed to full")

          case ItemType.Shield =>
            player.setShieldUntil(System.currentTimeMillis() + Constants.SHIELD_DURATION_MS)
            println(s"ClientHandler: Player ${playerId.toString.substring(0, 8)} shield activated")

          case ItemType.Gem =>
            player.setGemBoostUntil(System.currentTimeMillis() + Constants.GEM_DURATION_MS)
            println(s"ClientHandler: Player ${playerId.toString.substring(0, 8)} gem boost activated")

          case ItemType.Fence =>
            placeFence(player)

          case _ => // Star is client-side only
        }
      }
    }
    false
  }

  private def placeFence(player: Player): Unit = {
    val pos = player.getPosition
    val px = pos.getX
    val py = pos.getY

    // Determine the cell directly in front and the perpendicular offsets
    val (frontDx, frontDy, perpDx, perpDy) = player.getDirection match {
      case Direction.Up    => (0, -1, 1, 0)
      case Direction.Down  => (0, 1, 1, 0)
      case Direction.Left  => (-1, 0, 0, 1)
      case Direction.Right => (1, 0, 0, 1)
    }

    val frontX = px + frontDx
    val frontY = py + frontDy

    // Place 3 tiles: center and ±1 perpendicular
    val positions = Seq(
      (frontX - perpDx, frontY - perpDy),
      (frontX, frontY),
      (frontX + perpDx, frontY + perpDy)
    )

    val world = server.getWorld
    positions.foreach { case (tx, ty) =>
      if (world.setTile(tx, ty, Tile.Fence)) {
        server.broadcastTileUpdate(player.getId, tx, ty, Tile.Fence.id)
      }
    }

    println(s"ClientHandler: Player ${player.getId.toString.substring(0, 8)} placed fence at ($frontX, $frontY) facing ${player.getDirection}")
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
          existing.getHealth
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
      println(s"Player disconnected (TCP): ${playerId.toString.substring(0, 8)} ('${player.getName}')")

      // Broadcast leave to remaining players
      val leavePacket = new PlayerLeavePacket(server.getNextSequenceNumber, playerId)
      server.broadcastToAllPlayers(leavePacket)
    }
  }
}
