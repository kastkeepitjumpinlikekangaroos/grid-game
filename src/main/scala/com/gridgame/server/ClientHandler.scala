package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.model.BarrierCast
import com.gridgame.common.model.CharacterDef
import com.gridgame.common.model.DashBuff
import com.gridgame.common.model.Direction
import com.gridgame.common.model.ItemType
import com.gridgame.common.model.PhaseShiftBuff
import com.gridgame.common.model.Player
import com.gridgame.common.model.Position
import com.gridgame.common.model.Teleport
import com.gridgame.common.model.Tile
import com.gridgame.common.observability.Attrs
import com.gridgame.common.observability.Metrics
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

    if (player.isFrozen || player.isDead) {
      return false
    }

    // Validate projectile velocity: reject NaN, Infinite, or excessive magnitude
    val pdx = packet.getDx
    val pdy = packet.getDy
    if (java.lang.Float.isNaN(pdx) || java.lang.Float.isNaN(pdy) ||
        java.lang.Float.isInfinite(pdx) || java.lang.Float.isInfinite(pdy)) {
      System.err.println(s"ClientHandler: Player ${playerId.toString.substring(0, 8)} projectile velocity NaN/Inf")
      Metrics.validationFailed.add(1L, Attrs.VfProjectileVelocity)
      return false
    }
    if (pdx * pdx + pdy * pdy > 2.0f) {
      System.err.println(s"ClientHandler: Player ${playerId.toString.substring(0, 8)} projectile velocity too large")
      Metrics.validationFailed.add(1L, Attrs.VfProjectileVelocity)
      return false
    }

    if (!validator.validateProjectileSpawn(packet, player)) {
      Metrics.validationFailed.add(1L, Attrs.VfProjectileFireRate)
      return false
    }

    // Firing anything drops a raised barrier. The client drops it first and says so, but that
    // update and this spawn race each other over UDP, so the spawn drops it too.
    if (player.hasBarrier) {
      player.dropBarrier()
      broadcastState(player)
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

    // The join places the player, and a reconnected client counts its packets from zero again
    validator.resetMovementFence(playerId)

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
      // Its state, with the count of server moves: a client that has lost count of them (a
      // restarted one starts from zero) picks it up, or all its updates would be taken as stale
      if (instance != null) instance.sendStateToPlayer(existing)

      true
    } else {
      val charDef = com.gridgame.common.model.CharacterDef.get(packet.getCharacterId)
      if (charDef == null) {
        System.err.println(s"ClientHandler: Player ${playerId.toString.substring(0, 8)} invalid character ID: ${packet.getCharacterId}")
        Metrics.validationFailed.add(1L, Attrs.VfCharacter)
        return false
      }
      // gridgame.character.played is incremented from ClientRegistry.add to cover all join paths
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
    val player = registry.get(playerId)
    // Reject updates from unknown players — they must join via PLAYER_JOIN first
    if (player == null) return false

    val world = if (instance != null) instance.world else server.getWorld
    if (world == null && instance != null) {
      // World should be loaded for active game instances — reject if missing
      Metrics.validationFailed.add(1L, Attrs.VfWorldMissing)
      return false
    }

    // Check and apply as one step under the player's lock. A star moves the player on the TCP
    // thread while this runs on the UDP one, and a move checked against the position from before
    // the star must not be written after it.
    var refused = false
    val accepted = player.synchronized {
      if (packet.getServerMoves != player.getServerMoves) {
        // Sent before the client knew the server had moved it (a pull, a knockback, a respawn):
        // it would only drag the player back to where they were
        false
      } else if (validator.isStaleMovement(playerId, packet.getSequenceNumber)) {
        // Sent before a position already applied: it would only put the player back
        false
      } else {
        // A phase lets the player through walls, so it takes effect before this very update is
        // checked: the first one sent with the flag can already be inside one
        if ((packet.getEffectFlags & 0x08) != 0) activatePhase(player)
        updateBarrier(player, packet)
        if (world != null && !validator.validateMovement(packet, player, world)) {
          Metrics.validationFailed.add(1L, Attrs.VfMovementSpeed)
          refused = true
          false
        } else {
          validator.movementApplied(playerId, packet.getSequenceNumber)
          val oldPos = player.getPosition
          val newPos = packet.getPosition
          val dx = newPos.getX - oldPos.getX
          val dy = newPos.getY - oldPos.getY
          if (dx != 0 || dy != 0) {
            player.setDirection(Direction.fromMovement(dx, dy))
          }
          // Don't allow movement if player is frozen or rooted
          if (!player.isFrozen && !player.isRooted) {
            player.setPosition(newPos)
          }
          player.setColorRGB(packet.getColorRGB)
          // Kept so the server's own updates about this player don't show their charge dropping
          player.setChargeLevel(packet.getChargeLevel)
          // Character ID is set on join only — ignore mid-game character changes
          if (udpSender != null) {
            player.setUdpAddress(udpSender)
          }
          true
        }
      }
    }
    if (refused) noteRefused(player) else if (accepted) refusedSince.remove(playerId)
    if (!accepted) return false

    // Check for item pickup using server-authoritative position
    val pickupPos = player.getPosition
    itemManager.checkPickup(playerId, pickupPos.getX, pickupPos.getY).foreach { event =>
      if (instance != null) {
        instance.broadcastItemPickup(event.item, event.playerId)
      } else {
        server.broadcastItemPickup(event.item, event.playerId)
      }
    }

    true
  }

  /**
   * The client says the player is phased (its phase ability, or a dash). Honour it if that
   * ability is off cooldown: 80% of it since the last phase started, the tolerance every other
   * attack gets. This used to count from when the last phase ended, at 90%, which a phase recast
   * as soon as it was ready never passed (Wraith: 5s phase, 12s cooldown, allowed after 15.8s),
   * so every second phase showed on the client and wasn't honoured: the player took hits, and
   * walking through walls was refused.
   */
  private def activatePhase(player: Player): Unit = {
    if (player.isPhased) return
    val charDef = CharacterDef.get(player.getCharacterId)
    val grants = Seq(charDef.qAbility, charDef.eAbility).flatMap { ability =>
      ability.castBehavior match {
        case PhaseShiftBuff(durationMs) => Some((durationMs, ability.cooldownMs))
        case DashBuff(_, durationMs, _) => Some((durationMs, ability.cooldownMs))
        case _ => None
      }
    }
    grants.headOption.foreach { case (durationMs, cooldownMs) =>
      val now = System.currentTimeMillis()
      val lastEnd = player.getPhasedUntil
      val lastStart = lastEnd - durationMs
      if (lastEnd == 0L || now - lastStart >= (cooldownMs * 0.8).toLong) {
        player.setPhasedUntil(now + durationMs)
      }
    }
  }

  /**
   * The client says whether the player's barrier is up (effect flags 2, bit 2) and which way it
   * faces (the aim angle). A raise is honoured if the character has a barrier and it is off
   * cooldown: 80% of it since the last raise, the tolerance every other attack gets, counted from
   * the raise because a barrier can drop early. While it is up every update turns it, and one
   * without the bit drops it. Stale and reordered updates never get here, so an old one can't.
   */
  private def updateBarrier(player: Player, packet: PlayerUpdatePacket): Unit = {
    val raised = (packet.getEffectFlags2 & 0x04) != 0
    if (player.hasBarrier) {
      if (raised) player.setBarrierAngle(packet.aimAngleRadians.toFloat)
      else player.dropBarrier()
    } else if (raised && !player.isDead && !player.isFrozen && !player.isPhased) {
      val charDef = CharacterDef.get(player.getCharacterId)
      Seq(charDef.qAbility, charDef.eAbility).collectFirst {
        case a if a.castBehavior.isInstanceOf[BarrierCast] => (a.castBehavior.asInstanceOf[BarrierCast].durationMs, a.cooldownMs)
      }.foreach { case (durationMs, cooldownMs) =>
        val now = System.currentTimeMillis()
        val last = player.getBarrierRaisedAt
        if (last == 0L || now - last >= (cooldownMs * 0.8).toLong) {
          player.raiseBarrier(now, durationMs, packet.aimAngleRadians.toFloat)
        }
      }
    }
  }

  // When the current run of refused positions began, per player; cleared by an accepted one
  private val refusedSince = new java.util.concurrent.ConcurrentHashMap[UUID, java.lang.Long]()
  // When each player was last corrected, so a client that keeps sending refused positions is
  // told where it is at most every CORRECTION_INTERVAL_MS
  private val lastCorrectionAt = new java.util.concurrent.ConcurrentHashMap[UUID, java.lang.Long]()
  private val CORRECTION_AFTER_MS = 250L
  private val CORRECTION_INTERVAL_MS = 500L

  /**
   * One of the player's positions was refused. A lone refusal is usually a race the server is
   * about to catch up with (a step that overtook the star it was taken from), so nothing is said.
   * Refusals that keep coming mean the client and server disagree about where the player is, and
   * every step it takes from where it thinks it is will be refused too: the client only takes a
   * position from the server when it is a server move, so tell it where it is with one.
   */
  private def noteRefused(player: Player): Unit = {
    val now = System.currentTimeMillis()
    val since = refusedSince.putIfAbsent(player.getId, now)
    if (since != null && now - since.longValue() >= CORRECTION_AFTER_MS) correctPosition(player)
  }

  /** Send the player the position the server has for them, as a server move: their client takes
    * it, and the steps it sent from where it wrongly thought it was are dropped. */
  private def correctPosition(player: Player): Unit = {
    if (instance == null) return
    val now = System.currentTimeMillis()
    val last = lastCorrectionAt.get(player.getId)
    if (last != null && now - last.longValue() < CORRECTION_INTERVAL_MS) return
    lastCorrectionAt.put(player.getId, now)
    instance.moveByServer(player, player.getPosition)
  }

  private def handlePlayerLeave(packet: PlayerLeavePacket): Boolean = {
    val playerId = packet.getPlayerId
    val player = registry.get(playerId)

    if (player != null) {
      registry.remove(playerId)
      itemManager.clearInventory(playerId)
      validator.removePlayer(playerId)
      lastCorrectionAt.remove(playerId)
      refusedSince.remove(playerId)
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
      val player = registry.get(playerId)
      // The dead can't use items. The client doesn't offer it, but a use sent just before the
      // death reached it arrived after: a heart then brought the player back where they fell,
      // with a respawn still due to move them three seconds later.
      if (player != null && player.isDead) return false
      val removedItem = itemManager.removeFromInventory(playerId, packet.getItemId)
      if (removedItem == null) {
        System.err.println(s"ClientHandler: Item USE failed — item ${packet.getItemId} not in server inventory for ${playerId.toString.substring(0, 8)}")
        return false
      }
      Metrics.itemsUsed.add(1L, Attrs.itemTypeAttrs(removedItem.itemType.id))

      if (player != null) {
        removedItem.itemType match {
          case ItemType.Heart =>
            player.synchronized { player.setHealth(player.getMaxHealth) }
            broadcastState(player)
            println(s"ClientHandler: Player ${playerId.toString.substring(0, 8)} healed to full")

          case ItemType.Shield =>
            player.setShieldUntil(System.currentTimeMillis() + Constants.SHIELD_DURATION_MS)
            broadcastState(player)
            println(s"ClientHandler: Player ${playerId.toString.substring(0, 8)} shield activated")

          case ItemType.Gem =>
            player.setGemBoostUntil(System.currentTimeMillis() + Constants.GEM_DURATION_MS)
            broadcastState(player)
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
            if (!teleportWithStar(player, packet.getX, packet.getY, packet.getSequenceNumber)) {
              // The client has already moved itself there, so give the star back and tell it
              // where the server has it
              itemManager.addToInventory(playerId, removedItem)
              val pos = player.getPosition
              System.err.println(s"ClientHandler: Star refused for ${playerId.toString.substring(0, 8)}: (${packet.getX},${packet.getY}) from (${pos.getX},${pos.getY})")
              val rejectPacket = new ItemPacket(
                server.getNextSequenceNumber,
                playerId,
                pos.getX, pos.getY,
                removedItem.itemType.id,
                removedItem.id,
                ItemAction.USE_REJECTED
              )
              try {
                server.sendRawToPlayer(rejectPacket.serialize(), true, player)
              } catch { case _: Exception => }
              // Anything the client sent from the landing cell is stale
              if (instance != null) instance.moveByServer(player, pos)
            }
          case _ =>
        }
      }
    }
    false
  }

  /** Tell everyone in the match the player's state as the server has it. */
  private def broadcastState(player: Player): Unit = {
    if (instance != null) instance.broadcastPlayerUpdate(instance.stateUpdate(player))
  }

  /**
   * Star: move the player to the cell they aimed at. This item packet is the teleport; the
   * client picked the cell with the same Teleport rule, so the server refuses only when its world
   * or its copy of the position has moved on since. Returns false if refused.
   */
  private def teleportWithStar(player: Player, targetX: Int, targetY: Int, seqNum: Int): Boolean = {
    val w = if (instance != null) instance.world else server.getWorld
    if (w == null) return false
    val moved = player.synchronized {
      val pos = player.getPosition
      if (validator.isStaleMovement(player.getId, seqNum)) {
        // An update the client sent after using the star is already applied, so it's past here
        true
      } else if (!Teleport.isValidStarTarget(w, pos.getX, pos.getY, targetX, targetY)) {
        false
      } else {
        player.setPosition(new Position(targetX, targetY))
        // Updates the client sent before using the star still describe where it was
        validator.movementApplied(player.getId, seqNum)
        true
      }
    }
    // Everyone else sees the teleport now rather than on the player's next step
    if (moved) broadcastState(player)
    moved
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

    // Distance check: fence must be placed near the player
    val fdx = Math.abs(targetX - player.getPosition.getX)
    val fdy = Math.abs(targetY - player.getPosition.getY)
    if (fdx + fdy > Constants.FENCE_MAX_DISTANCE) return false

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
    var sent = false
    itemManager.getAll.foreach { item =>
      val packet = new ItemPacket(
        server.getNextSequenceNumber,
        zeroUUID,
        item.x, item.y,
        item.itemType.id,
        item.id,
        ItemAction.SPAWN
      )
      val data = packet.serialize()
      server.sendRawToPlayerBuffered(data, true, player)
      sent = true
    }
    if (sent) server.flushPlayer(player)
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
      validator.removePlayer(playerId)
      lastCorrectionAt.remove(playerId)
      refusedSince.remove(playerId)
      println(s"Player timed out: ${playerId.toString.substring(0, 8)} ('${player.getName}')")
      new PlayerLeavePacket(sequenceNumber, playerId)
    } else {
      null
    }
  }

  /** Notify that a player's ability projectile hit a target. Reduces server-side fire-rate cooldown tracking to mirror client-side on-hit cooldown reduction. */
  def notifyAbilityHit(playerId: UUID, projectileType: Byte, characterId: Byte): Unit = {
    validator.reduceAbilityCooldown(playerId, projectileType, characterId)
  }

  def handleDisconnect(channel: Channel): Unit = {
    val player = registry.getByChannel(channel)
    if (player != null) {
      println(s"Player disconnected (TCP): ${player.getId.toString.substring(0, 8)} ('${player.getName}')")
      removePlayer(player.getId)
    }
  }

  /** Take a player out of the match (they disconnected or left it) and tell the others.
    * Their kills and deaths stay with the kill tracker, so they are still scored. */
  def removePlayer(playerId: UUID): Unit = {
    if (registry.get(playerId) == null) return
    registry.remove(playerId)
    itemManager.clearInventory(playerId)
    validator.removePlayer(playerId)
    lastCorrectionAt.remove(playerId)
    refusedSince.remove(playerId)

    val leavePacket = new PlayerLeavePacket(server.getNextSequenceNumber, playerId)
    if (instance != null) {
      instance.broadcastToInstance(leavePacket)
    } else {
      server.broadcastToAllPlayers(leavePacket)
    }
  }
}
