package com.gridgame.server

import com.gridgame.server.game.ItemManager
import com.gridgame.server.bots.BotController
import com.gridgame.common.Constants
import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

/** Items: picking them up by walking near, and what using each one does. */
class ItemTest {
  private val m = new TestMatch()
  private val items = m.instance.itemManager

  // --- Pickup ---

  @Test def itemsArePickedUpWithinTwoCells(): Unit = {
    val p = m.join(CharacterId.Gladiator, 10, 10)
    val near = spawnAt(12, 10)
    val far = spawnAt(13, 10)
    assertTrue(m.move(p, 10, 10))
    assertTrue(m.hasItem(p, near))
    assertFalse(m.hasItem(p, far))
    assertEquals("the far one is still lying there", 1, items.getAll.size)
  }

  @Test def anItemGoesToOnePlayerOnly(): Unit = {
    val a = m.join(CharacterId.Gladiator, 10, 10)
    val b = m.join(CharacterId.Gladiator, 11, 10)
    val gem = spawnAt(10, 11)
    assertTrue(m.move(a, 10, 10))
    assertTrue(m.move(b, 11, 10))
    assertTrue(m.hasItem(a, gem))
    assertFalse(m.hasItem(b, gem))
  }

  @Test def theInventoryHoldsTenItems(): Unit = {
    val p = m.join(CharacterId.Gladiator, 10, 10)
    val spawned = (0 until 12).map(_ => spawnAt(10, 10))
    for (_ <- 0 until 12) m.move(p, 10, 10)
    assertEquals(Constants.MAX_INVENTORY_SIZE, items.getInventory(p.getId).size)
    assertEquals(2, items.getAll.size)
    assertTrue(spawned.nonEmpty)
  }

  @Test def anItemBesideSomeoneStandingStillIsTheirsOnTheNextTick(): Unit = {
    // Only a step picked anything up: an item that appeared within reach of someone standing
    // still lay there until they moved
    val p = m.join(CharacterId.Gladiator, 10, 10)
    val gem = spawnAt(11, 10)
    m.clearSent()
    m.tick()
    assertTrue(m.hasItem(p, gem))
    assertTrue("gone from the ground", items.getAll.isEmpty)
    assertTrue("and they are told", m.tcpSent(p).exists {
      case i: ItemPacket => i.getAction == ItemAction.PICKUP && i.getItemId == gem.id && i.getPlayerId == p.getId
      case _ => false
    })
  }

  @Test def usingAnItemMakesRoomForTheOneWithinReach(): Unit = {
    // A full inventory leaves an item on the ground. Using one of ours makes room, and it used to
    // stay there all the same, under our feet, until our next step
    val p = m.join(CharacterId.Gladiator, 10, 10)
    val hearts = (0 until Constants.MAX_INVENTORY_SIZE).map { k =>
      val h = new Item(700 + k, 0, 0, ItemType.Heart)
      items.addToInventory(p.getId, h)
      h
    }
    val gem = spawnAt(10, 10)
    assertTrue(m.move(p, 10, 10))
    m.tick()
    assertFalse("no room for it", m.hasItem(p, gem))
    assertEquals(1, items.getAll.size)
    m.instance.handler.processPacket(
      new ItemPacket(1, p.getId, 0, 0, ItemType.Heart.id, hearts.head.id, ItemAction.USE), null, null)
    m.tick()
    assertTrue(m.hasItem(p, gem))
    assertEquals(Constants.MAX_INVENTORY_SIZE, items.getInventory(p.getId).size)
  }

  @Test def theDeadAreHandedNothing(): Unit = {
    val p = m.join(CharacterId.Gladiator, 10, 10)
    val gem = spawnAt(11, 10)
    p.damage(p.getHealth)
    m.tick()
    assertFalse(m.hasItem(p, gem))
    assertEquals("still on the ground", 1, items.getAll.size)
  }

  @Test def practiceBotsAreHandedNothing(): Unit = {
    // Bots pick up on a tick of their own (BotItems), and practice's, passive targets, not at all
    val practice = new TestMatch(practice = true)
    val bot = practice.join(CharacterId.Gladiator, 10, 10, id = new java.util.UUID(0L, 7L))
    val gem = new Item(9100, 11, 10, ItemType.Gem)
    practice.instance.itemManager.place(gem)
    practice.tick()
    assertFalse(practice.hasItem(bot, gem))
  }

  @Test def pickupsAtOnceStillStopAtTen(): Unit = {
    // A player's pickups are asked for on their steps' thread and on the projectile tick's, and
    // two at once could each find room for one more
    for (round <- 0 until 2000) {
      val manager = new ItemManager()
      try {
        val p = java.util.UUID.randomUUID()
        (0 until Constants.MAX_INVENTORY_SIZE - 1).foreach(k => manager.addToInventory(p, new Item(1000 + k, 0, 0, ItemType.Heart)))
        (0 until 4).foreach(k => manager.place(new Item(k, 10 + k % 2, 10 + k / 2, ItemType.Gem)))
        val go = new java.util.concurrent.CountDownLatch(1)
        val takers = (0 until 2).map(_ => new Thread(() => { go.await(); manager.checkPickup(p, 10, 10) }))
        takers.foreach(_.start())
        go.countDown()
        takers.foreach(_.join())
        assertEquals(s"round $round", Constants.MAX_INVENTORY_SIZE, manager.getInventory(p).size)
      } finally manager.close()
    }
  }

  @Test def anItemIsOnTheGroundOnlyOnceEveryoneHasBeenSentIt(): Unit = {
    // Placed before its SPAWN went out, a pickup in between (a bot's tick, a step, the projectile
    // tick, all lined up with the spawner's minute) could send its PICKUP first, and a client that
    // heard them in that order kept the item on its ground for good, where nobody could pick it up
    var watching = false
    var onTheGroundAsItWasSent: Option[Boolean] = None
    val watcher = new Player(java.util.UUID.randomUUID(), "w", new Position(5, 5), 0xFF336699) {
      // The outbox looks the player's channel up to send them each packet: the first is the SPAWN
      override def getTcpChannel: AnyRef = {
        if (watching && onTheGroundAsItWasSent.isEmpty) onTheGroundAsItWasSent = Some(items.getAll.nonEmpty)
        super.getTcpChannel
      }
    }
    m.instance.registry.add(watcher)
    watching = true
    m.instance.spawnItemBatch(1)
    assertEquals("not on the ground as it was sent", Some(false), onTheGroundAsItWasSent)
    assertEquals("on it after", 1, items.getAll.size)
  }

  @Test def spawnedItemsLieOnOpenGround(): Unit = {
    val w = WorldData.createEmpty(20, 20)
    for (x <- 0 until 20; y <- 0 until 20 if (x + y) % 2 == 0) w.setTile(x, y, Tile.Water)
    val manager = new ItemManager()
    try {
      val spawned = (0 until 30).flatMap(_ => manager.randomItem(w))
      assertTrue(spawned.nonEmpty)
      spawned.foreach(i => assertTrue(s"(${i.x}, ${i.y})", w.isWalkable(i.x, i.y)))
    } finally manager.close()
  }

  // --- Use ---

  @Test def aHeartHealsToFull(): Unit = {
    val p = m.join(CharacterId.Gladiator, 10, 10)
    p.setHealth(12)
    val heart = m.useItem(p, ItemType.Heart)
    assertEquals(p.getMaxHealth, p.getHealth)
    assertFalse("used up", m.hasItem(p, heart))
  }

  @Test def theDeadCannotUseItems(): Unit = {
    // A heart sent just before its user's death reached them arrived after it, and brought the
    // player back where they fell with a respawn still to come
    val p = m.join(CharacterId.Gladiator, 10, 10)
    p.setHealth(0)
    m.useItem(p, ItemType.Heart)
    assertEquals(0, p.getHealth)
    assertTrue(p.isDead)
  }

  @Test def anItemNotInTheInventoryDoesNothing(): Unit = {
    val p = m.join(CharacterId.Gladiator, 10, 10)
    p.setHealth(12)
    m.instance.handler.processPacket(new ItemPacket(1, p.getId, 0, 0, ItemType.Heart.id, 424242, ItemAction.USE), null, null)
    assertEquals(12, p.getHealth)
  }

  @Test def aShieldTurnsShotsAside(): Unit = {
    val soldier = m.join(CharacterId.Soldier, 10, 30)
    val p = m.join(CharacterId.Gladiator, 14, 30)
    m.useItem(p, ItemType.Shield)
    assertTrue(p.hasShield)
    m.tickUntilGone(m.launch(soldier, ProjectileType.BULLET, 1f, 0f))
    assertEquals(p.getMaxHealth, p.getHealth)
  }

  @Test def aGemBoostsTheShootersShots(): Unit = {
    val p = m.join(CharacterId.Soldier, 10, 30)
    m.useItem(p, ItemType.Gem)
    assertTrue(p.hasGemBoost)
  }

  // --- Fences ---

  @Test def aFenceIsThreeCellsAcrossTheWayThePlayerFaces(): Unit = {
    val p = m.join(CharacterId.Gladiator, 10, 10) // facing down: the fence runs along x
    m.useItem(p, ItemType.Fence, 10, 13)
    for (x <- 9 to 11) assertEquals(s"($x, 13)", Tile.Fence, m.world.getTile(x, 13))
    assertEquals(Tile.Grass, m.world.getTile(10, 12))
  }

  @Test def aFenceIsOnlyBuiltOnOpenGround(): Unit = {
    val p = m.join(CharacterId.Gladiator, 10, 10)
    m.world.setTile(9, 13, Tile.Water)
    m.useItem(p, ItemType.Fence, 10, 13)
    assertEquals(Tile.Water, m.world.getTile(9, 13))
    assertEquals(Tile.Fence, m.world.getTile(10, 13))
  }

  @Test def aFenceTooFarAwayIsRefusedAndTheItemGivenBack(): Unit = {
    val p = m.join(CharacterId.Gladiator, 10, 10)
    m.clearSent()
    val fence = m.useItem(p, ItemType.Fence, 10, 10 + Constants.FENCE_MAX_DISTANCE + 1)
    assertEquals(Tile.Grass, m.world.getTile(10, 10 + Constants.FENCE_MAX_DISTANCE + 1))
    assertTrue(m.hasItem(p, fence))
    assertTrue("the client is given it back", m.tcpSent(p).exists {
      case i: ItemPacket => i.getAction == ItemAction.INVENTORY && i.getItemId == fence.id
      case _ => false
    })
  }

  @Test def aBotsFenceDoesNotReplaceWalls(): Unit = {
    // It used to turn whatever was there into fence, walls and water included — and a fence
    // stops the shots that fly over walls
    val bot = m.join(CharacterId.Gladiator, 10, 10)
    m.world.setTile(9, 12, Tile.Wall)
    m.world.setTile(11, 12, Tile.Water)
    new BotController(m.instance).placeFence(bot, 10, 12)
    assertEquals(Tile.Wall, m.world.getTile(9, 12))
    assertEquals(Tile.Water, m.world.getTile(11, 12))
    assertEquals(Tile.Fence, m.world.getTile(10, 12))
  }

  private var nextItemId = 500
  /** ItemManager spawns items at random; this puts one exactly where the test wants it. */
  private def spawnAt(x: Int, y: Int): Item = {
    nextItemId += 1
    val item = new Item(nextItemId, x, y, ItemType.Gem)
    items.place(item)
    item
  }
}
