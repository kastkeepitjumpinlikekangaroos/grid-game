package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.model.Item
import com.gridgame.common.model.ItemType
import com.gridgame.common.model.WorldData

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.CollectionConverters._
import scala.util.Random

sealed trait ItemEvent
case class ItemSpawned(item: Item) extends ItemEvent
case class ItemPickedUp(item: Item, playerId: UUID) extends ItemEvent

class ItemManager {
  private val items = new ConcurrentHashMap[Int, Item]()
  private val inventories = new ConcurrentHashMap[UUID, ConcurrentHashMap[Int, Item]]()
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
        val id = nextId.getAndIncrement() & 0x7FFFFFFF
        val itemType = ItemType.spawnable(random.nextInt(ItemType.spawnable.size))
        val item = new Item(id, x, y, itemType)
        items.put(id, item)
        return Some(ItemSpawned(item))
      }
      attempt += 1
    }
    None
  }

  def checkPickup(playerId: UUID, x: Int, y: Int): Option[ItemPickedUp] = {
    val playerInv = inventories.computeIfAbsent(playerId, _ => new ConcurrentHashMap[Int, Item]())

    // Enforce inventory size limit
    if (playerInv.size() >= Constants.MAX_INVENTORY_SIZE) return None

    val radius = Constants.ITEM_PICKUP_RADIUS
    val iter = items.values().iterator()
    while (iter.hasNext) {
      val item = iter.next()
      val dx = item.getCellX - x
      val dy = item.getCellY - y
      if (dx * dx + dy * dy <= radius * radius) {
        // Use remove-and-check to avoid double pickup race
        if (items.remove(item.id, item)) {
          playerInv.put(item.id, item)
          return Some(ItemPickedUp(item, playerId))
        }
      }
    }
    None
  }

  /** Remove an item from a player's inventory. Returns the removed Item, or null if not found. */
  def removeFromInventory(playerId: UUID, itemId: Int): Item = {
    val inv = inventories.get(playerId)
    if (inv == null) return null
    inv.remove(itemId)
  }

  /** Add an item back to a player's inventory (e.g. when fence placement fails). */
  def addToInventory(playerId: UUID, item: Item): Unit = {
    val inv = inventories.computeIfAbsent(playerId, _ => new ConcurrentHashMap[Int, Item]())
    inv.put(item.id, item)
  }

  def getInventory(playerId: UUID): Seq[Item] = {
    val inv = inventories.get(playerId)
    if (inv != null) inv.values().asScala.toSeq else Seq.empty
  }

  def clearInventory(playerId: UUID): Seq[Item] = {
    val inv = inventories.remove(playerId)
    if (inv != null) inv.values().asScala.toSeq else Seq.empty
  }

  def getAll: Seq[Item] = items.values().asScala.toSeq

  def size: Int = items.size()
}
