package com.gridgame.server.bots

import com.gridgame.common.Constants
import com.gridgame.common.model._
import com.gridgame.common.protocol._

/** A bot picking items up where it walks over them, and using them: a heart when hurt, a shield or
  * gem in a fight, a star to close in or get away, a fence against whoever is chasing it. */
trait BotItems { this: BotController =>
  private[bots] def tryPickupItems(bot: Player): Unit = {
    val botPos = bot.getPosition
    val pickup = instance.itemManager.checkPickup(bot.getId, botPos.getX, botPos.getY)
    pickup.foreach { event =>
      instance.broadcastItemPickup(event.item, event.playerId)
    }
  }

  private[bots] def tryUseItems(bot: Player, target: Player): Unit = {
    val inventory = instance.itemManager.getInventory(bot.getId)
    if (inventory.isEmpty) return

    inventory.foreach { item =>
      item.itemType match {
        case ItemType.Heart =>
          bot.synchronized {
            if (bot.getHealth.toFloat / bot.getMaxHealth.toFloat < 0.5f) {
              useItem(bot, item)
              bot.setHealth(bot.getMaxHealth)
              broadcastBotPosition(bot)
            }
          }

        case ItemType.Shield =>
          bot.synchronized {
            if (!bot.hasShield) {
              useItem(bot, item)
              bot.setShieldUntil(System.currentTimeMillis() + Constants.SHIELD_DURATION_MS)
              broadcastBotPosition(bot)
            }
          }

        case ItemType.Gem =>
          bot.synchronized {
            if (!bot.hasGemBoost) {
              useItem(bot, item)
              bot.setGemBoostUntil(System.currentTimeMillis() + Constants.GEM_DURATION_MS)
              broadcastBotPosition(bot)
            }
          }

        case ItemType.Fence =>
          if (target != null) {
            val botPos = bot.getPosition
            val targetPos = target.getPosition
            val fdx = Integer.signum(targetPos.getX - botPos.getX)
            val fdy = Integer.signum(targetPos.getY - botPos.getY)
            val fenceX = botPos.getX + fdx * 2
            val fenceY = botPos.getY + fdy * 2
            useItem(bot, item)
            placeFence(bot, fenceX, fenceY)
          }

        case _ =>
      }
    }
  }

  private def useItem(bot: Player, item: com.gridgame.common.model.Item): Unit = {
    instance.itemManager.removeFromInventory(bot.getId, item.id)
    val packet = new ItemPacket(
      instance.outbox.nextSeq(),
      bot.getId,
      bot.getPosition.getX, bot.getPosition.getY,
      item.itemType.id,
      item.id,
      ItemAction.USE
    )
    instance.broadcastToInstance(packet)
  }

  private[server] def placeFence(bot: Player, targetX: Int, targetY: Int): Unit = {
    val (perpDx, perpDy) = bot.getDirection match {
      case Direction.Up | Direction.Down    => (1, 0)
      case Direction.Left | Direction.Right => (0, 1)
    }

    val positions = Seq(
      (targetX - perpDx, targetY - perpDy),
      (targetX, targetY),
      (targetX + perpDx, targetY + perpDy)
    )

    // Only on open ground, as a player's fence (ClientHandler.placeFence): this used to turn any
    // cell into fence, walls and water included, and a fence stops the shots that fly over walls
    val w = instance.world
    positions.foreach { case (tx, ty) =>
      if (w.isWalkable(tx, ty) && w.setTile(tx, ty, com.gridgame.common.model.Tile.Fence)) {
        instance.broadcastTileUpdate(bot.getId, tx, ty, com.gridgame.common.model.Tile.Fence.id)
      }
    }
  }
}
