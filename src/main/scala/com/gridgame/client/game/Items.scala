package com.gridgame.client.game

import com.gridgame.common.Constants
import com.gridgame.common.model._
import com.gridgame.common.protocol._

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong

/**
 * Items: those lying on the ground, the ones we carry (a queue per kind), and using one — a star's
 * jump, a gem's doubled fire, a shield, a heart, a fence. The server says what we picked up, and
 * gives an item back when it refuses its use (ItemAction.USE_REJECTED).
 */
trait Items { this: GameClient =>
  private val items: ConcurrentHashMap[Int, Item] = new ConcurrentHashMap()
  private val inventory: ConcurrentHashMap[Byte, ConcurrentLinkedDeque[Item]] = new ConcurrentHashMap()
  // What a gem and a shield do for us, until
  private val fastProjectilesUntil: AtomicLong = new AtomicLong(0)
  private val shieldUntil: AtomicLong = new AtomicLong(0)

  def hasGemBoost: Boolean = System.currentTimeMillis() < fastProjectilesUntil.get()

  def hasShield: Boolean = System.currentTimeMillis() < shieldUntil.get()

  /** No match: nothing on the ground, nothing carried. */
  private[game] def forgetItems(): Unit = {
    items.clear()
    inventory.clear()
  }

  /** A death takes everything we carry, and what a gem or a shield was doing for us. */
  private[game] def loseItems(): Unit = {
    inventory.clear()
    fastProjectilesUntil.set(0)
    shieldUntil.set(0)
  }

  private[game] def handleItemUpdate(packet: ItemPacket): Unit = {
    packet.getAction match {
      case ItemAction.SPAWN =>
        val item = new Item(packet.getItemId, packet.getX, packet.getY, packet.getItemType)
        items.put(packet.getItemId, item)

      case ItemAction.PICKUP =>
        items.remove(packet.getItemId)
        if (packet.getPlayerId.equals(localPlayerId)) {
          val item = new Item(packet.getItemId, packet.getX, packet.getY, packet.getItemType)
          addToInventory(item)
        }

      case ItemAction.INVENTORY =>
        if (packet.getPlayerId.equals(localPlayerId)) {
          val item = new Item(packet.getItemId, packet.getX, packet.getY, packet.getItemType)
          addToInventory(item)
        }

      case ItemAction.USE_REJECTED =>
        if (packet.getPlayerId.equals(localPlayerId)) {
          addToInventory(new Item(packet.getItemId, packet.getX, packet.getY, packet.getItemType))
          if (packet.getItemType == ItemType.Star) {
            // The server didn't take the teleport: back to where it has us
            localPosition.set(new Position(packet.getX, packet.getY))
            println(s"GameClient: Teleport refused by server, back at (${packet.getX}, ${packet.getY})")
          }
        }

      case _ =>
        // Unknown action
    }
  }

  private def addToInventory(item: Item): Boolean = {
    val deque = inventory.computeIfAbsent(item.itemType.id, _ => new ConcurrentLinkedDeque[Item]())
    deque.add(item)
    true
  }

  def useItem(itemTypeId: Byte): Unit = {
    val deque = inventory.get(itemTypeId)
    if (deque == null || deque.isEmpty) {
      return
    }

    // A star lands on the cell the server will check it against (Teleport), so it can't show a
    // teleport the server then undoes. Nowhere to go keeps the star, and so does being held: a
    // freeze or a root keeps us where we are, a star's jump as much as a step.
    var starTarget: Position = null
    if (itemTypeId == ItemType.Star.id) {
      if (isFrozen || isRooted) return
      val pos = localPosition.get()
      starTarget = Teleport.starTarget(currentWorld.get(), pos.getX, pos.getY, mouseWorldX, mouseWorldY).orNull
      if (starTarget == null) {
        println("GameClient: Nowhere to teleport to!")
        return
      }
    }

    val item = deque.poll()
    if (item == null) return

    // Notify server that item was used
    // For fence and star, send the target cell as coordinates
    val (packetX, packetY) = if (item.itemType == ItemType.Star) {
      (starTarget.getX, starTarget.getY)
    } else if (item.itemType == ItemType.Fence) {
      (Math.round(mouseWorldX).toInt, Math.round(mouseWorldY).toInt)
    } else {
      (item.getCellX, item.getCellY)
    }
    val packet = new ItemPacket(
      sequenceNumber.getAndIncrement(),
      localPlayerId,
      packetX, packetY,
      item.itemType.id,
      item.id,
      ItemAction.USE
    )
    send(packet)

    println(s"GameClient: Used item '${item.itemType.name}' (${deque.size()} remaining)")

    val now = System.currentTimeMillis()
    item.itemType match {
      case ItemType.Star =>
        teleportTo(starTarget)
      case ItemType.Gem =>
        fastProjectilesUntil.set(now + Constants.GEM_DURATION_MS)
      case ItemType.Shield =>
        shieldUntil.set(now + Constants.SHIELD_DURATION_MS)
      case ItemType.Heart =>
        // Server heals
      case ItemType.Fence =>
        // Server handles
      case _ =>
    }
  }

  def getItems: ConcurrentHashMap[Int, Item] = items

  def getItemCount(itemTypeId: Byte): Int = {
    val deque = inventory.get(itemTypeId)
    if (deque == null) 0 else deque.size()
  }

  def getInventoryCount: Int = {
    var sum = 0
    val iter = inventory.values().iterator()
    while (iter.hasNext) sum += iter.next().size()
    sum
  }

}
