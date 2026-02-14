package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.model.Item
import com.gridgame.common.model.ItemType
import com.gridgame.common.model.WorldData

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.CollectionConverters._
import scala.util.Random

sealed trait ItemEvent
case class ItemSpawned(item: Item) extends ItemEvent
case class ItemPickedUp(item: Item, playerId: UUID) extends ItemEvent

class ItemManager {
  private val items = new ConcurrentHashMap[Int, Item]()
  private val inventories = new ConcurrentHashMap[UUID, CopyOnWriteArrayList[Item]]()
  private val nextId = new AtomicInteger(1)
  private val random = new Random()

  def spawnRandomItem(world: WorldData): Option[ItemSpawned] = {
    // Try to find a random walkable tile
    val maxAttempts = 100
    var attempt = 0
    while (attempt < maxAttempts) {
      val x = (Math.random() * world.width).toInt
      val y = (Math.random() * world.height).toInt
      if (world.isWalkable(x, y)) {
        val id = nextId.getAndIncrement()
        val itemType = ItemType.spawnable(random.nextInt(ItemType.spawnable.size))
        val item = new Item(id, x, y, itemType)
        items.put(id, item)
        println(s"ItemManager: Spawned $item")
        return Some(ItemSpawned(item))
      }
      attempt += 1
    }
    println("ItemManager: Failed to find walkable tile for item spawn")
    None
  }

  def checkPickup(playerId: UUID, x: Int, y: Int): Option[ItemPickedUp] = {
    val playerInv = inventories.computeIfAbsent(playerId, _ => new CopyOnWriteArrayList[Item]())

    val radius = Constants.ITEM_PICKUP_RADIUS
    val iter = items.values().iterator()
    while (iter.hasNext) {
      val item = iter.next()
      val dx = item.getCellX - x
      val dy = item.getCellY - y
      if (dx * dx + dy * dy <= radius * radius) {
        items.remove(item.id)
        playerInv.add(item)
        println(s"ItemManager: Player ${playerId.toString.substring(0, 8)} picked up $item (inventory: ${playerInv.size()} items)")
        return Some(ItemPickedUp(item, playerId))
      }
    }
    None
  }

  def removeFromInventory(playerId: UUID, itemId: Int): Boolean = {
    val inv = inventories.get(playerId)
    if (inv == null) return false
    val iter = inv.iterator()
    while (iter.hasNext) {
      val item = iter.next()
      if (item.id == itemId) {
        inv.remove(item)
        println(s"ItemManager: Player ${playerId.toString.substring(0, 8)} used item $itemId (inventory: ${inv.size()} items)")
        return true
      }
    }
    false
  }

  def getInventory(playerId: UUID): Seq[Item] = {
    val inv = inventories.get(playerId)
    if (inv != null) inv.asScala.toSeq else Seq.empty
  }

  def clearInventory(playerId: UUID): Seq[Item] = {
    val inv = inventories.remove(playerId)
    if (inv != null) inv.asScala.toSeq else Seq.empty
  }

  def getAll: Seq[Item] = items.values().asScala.toSeq

  def size: Int = items.size()
}
