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
  private val maxWorldItems = 40

  // Spatial grid for O(1) pickup checks instead of iterating all items
  private val gridCellSize = 4
  private val itemGrid = new ConcurrentHashMap[Long, java.util.ArrayList[Item]]()

  private def gridKey(x: Int, y: Int): Long = {
    val cx = x / gridCellSize
    val cy = y / gridCellSize
    (cx.toLong << 32) | (cy.toLong & 0xFFFFFFFFL)
  }

  private def addToGrid(item: Item): Unit = {
    val key = gridKey(item.getCellX, item.getCellY)
    val cell = itemGrid.computeIfAbsent(key, _ => new java.util.ArrayList[Item](4))
    cell.synchronized { cell.add(item) }
  }

  private def removeFromGrid(item: Item): Unit = {
    val key = gridKey(item.getCellX, item.getCellY)
    val cell = itemGrid.get(key)
    if (cell != null) {
      cell.synchronized { cell.remove(item) }
    }
  }

  def spawnRandomItem(world: WorldData): Option[ItemSpawned] = {
    if (items.size() >= maxWorldItems) return None
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
        addToGrid(item)
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
    val radiusSq = radius * radius

    // Search 3x3 grid neighborhood
    val cx = x / gridCellSize
    val cy = y / gridCellSize
    var dy = -1
    while (dy <= 1) {
      var dx = -1
      while (dx <= 1) {
        val key = ((cx + dx).toLong << 32) | ((cy + dy).toLong & 0xFFFFFFFFL)
        val cell = itemGrid.get(key)
        if (cell != null) {
          cell.synchronized {
            var i = 0
            while (i < cell.size()) {
              val item = cell.get(i)
              val idx = item.getCellX - x
              val idy = item.getCellY - y
              if (idx * idx + idy * idy <= radiusSq) {
                // Use remove-and-check to avoid double pickup race
                if (items.remove(item.id, item)) {
                  cell.remove(i)
                  playerInv.put(item.id, item)
                  return Some(ItemPickedUp(item, playerId))
                }
              }
              i += 1
            }
          }
        }
        dx += 1
      }
      dy += 1
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

  def getInventory(playerId: UUID): Iterable[Item] = {
    val inv = inventories.get(playerId)
    if (inv != null) inv.values().asScala else Iterable.empty
  }

  def clearInventory(playerId: UUID): Seq[Item] = {
    val inv = inventories.remove(playerId)
    if (inv != null) inv.values().asScala.toSeq else Seq.empty
  }

  def getAll: Seq[Item] = items.values().asScala.toSeq

  def size: Int = items.size()
}
