package com.gridgame.client.game

import com.gridgame.common.model._
import com.gridgame.common.protocol._

import java.util.concurrent.atomic.AtomicReference

/** The map the match is played on: the server names its file (WORLD_INFO), the UI loads it and
  * hands it over ([[setWorld]]), and the server's tile changes are made to it. */
trait WorldState { this: GameClient =>
  private[game] val currentWorld: AtomicReference[WorldData] = new AtomicReference(initialWorld)
  @volatile private var worldFileListener: String => Unit = _

  private[game] def handleTileUpdate(packet: TileUpdatePacket): Unit = {
    val world = currentWorld.get()
    val tile = Tile.fromId(packet.getTileId)
    world.setTile(packet.getTileX, packet.getTileY, tile)
  }

  private[game] def handleWorldInfo(packet: WorldInfoPacket): Unit = {
    val worldFile = packet.getWorldFile
    println(s"GameClient: Received world info from server: $worldFile")

    if (worldFileListener != null) {
      worldFileListener(worldFile)
    }
  }

  def getWorld: WorldData = currentWorld.get()

  def setWorld(world: WorldData): Unit = {
    currentWorld.set(world)
    // The divider may have been announced before the world it stands on arrived, or after
    installDivider()
    // Somewhere in the new world until the server's join echo, which follows the world on the
    // same connection, says where it placed us. Kept to ourselves: sent, the server took it as
    // our position (see LocalPlayer.handleOwnJoin).
    val tempSpawn = world.getValidSpawnPoint()
    localPosition.set(tempSpawn)
    println(s"GameClient: World changed to '${world.name}', temp spawn at $tempSpawn")
  }

  def setWorldFileListener(listener: String => Unit): Unit = {
    worldFileListener = listener
  }
}
