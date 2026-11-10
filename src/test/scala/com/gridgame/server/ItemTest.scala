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

  @Test def anItemIsPickedUpFromTwoCellsRoundAndNoFurtherWhereverTheyStand(): Unit = {
    // Every offset within three cells, from cells either side of the lines the manager's own grid
    // is cut along (every four cells): as the crow flies, the same in every direction
    val manager = new ItemManager()
    try {
      var id = 0
      for (px <- 6 to 9; py <- 6 to 9; dx <- -3 to 3; dy <- -3 to 3) {
        id += 1
        val item = new Item(id, px + dx, py + dy, ItemType.Heart)
        manager.place(item)
        val got = manager.checkPickup(java.util.UUID.randomUUID(), px, py)
        assertEquals(s"an item at ${(px + dx, py + dy)}, from ($px, $py)", dx * dx + dy * dy <= 4, got.exists(_.item eq item))
        if (got.isEmpty) assertTrue(manager.checkPickup(java.util.UUID.randomUUID(), px + dx, py + dy).exists(_.item eq item))
        assertEquals(0, manager.size)
      }
    } finally manager.close()
  }

  @Test def theGroundHoldsFortyItemsAndNoMore(): Unit = {
    val w = WorldData.createEmpty(60, 60)
    val manager = new ItemManager()
    try {
      var made = 0
      for (_ <- 0 until 100) manager.randomItem(w).foreach { item => manager.place(item); made += 1 }
      assertEquals(40, made)
      assertEquals(40, manager.size)
      // One picked up makes room for one more
      val any = manager.getAll.head
      assertTrue(manager.checkPickup(java.util.UUID.randomUUID(), any.x, any.y).isDefined)
      assertTrue(manager.randomItem(w).isDefined)
    } finally manager.close()
  }

  @Test(timeout = 20000) def aMapWithNowhereOpenGetsNoItemAndTheSearchGivesUp(): Unit = {
    val w = WorldData.createEmpty(12, 12)
    for (x <- 0 until 12; y <- 0 until 12) w.setTile(x, y, Tile.Wall)
    val manager = new ItemManager()
    try assertEquals(None, manager.randomItem(w)) finally manager.close()
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
    // Its reach, and the steps the server's copy of them can trail them by on a bad network
    val far = 10 + Constants.FENCE_MAX_DISTANCE +
      Movement.lagCells(Constants.NETWORK_TOLERANCE_MS, CharacterDef.get(CharacterId.Gladiator.id).moveSpeed) + 1
    m.clearSent()
    val fence = m.useItem(p, ItemType.Fence, 10, far)
    assertEquals(Tile.Grass, m.world.getTile(10, far))
    assertTrue(m.hasItem(p, fence))
    assertTrue("the client is given it back", m.tcpSent(p).exists {
      case i: ItemPacket => i.getAction == ItemAction.INVENTORY && i.getItemId == fence.id
      case _ => false
    })
  }

  /** The cells everyone in `to` was told have become fence since the last look. */
  private def fenceToldTo(in: TestMatch, to: Player): Set[(Int, Int)] =
    in.sent(to).collect { case t: TileUpdatePacket if t.getTileId == Tile.Fence.id => (t.getTileX, t.getTileY) }.toSet

  private def givenBack(in: TestMatch, to: Player, item: Item): Boolean = in.tcpSent(to).exists {
    case i: ItemPacket => i.getAction == ItemAction.INVENTORY && i.getItemId == item.id
    case _ => false
  }

  @Test def aFencePutDownIsUsedUpAndEveryoneIsToldOfEachCell(): Unit = {
    // A fence nobody heard of is one their clients walk them into, every step refused
    val p = m.join(CharacterId.Gladiator, 10, 10)
    val other = m.join(CharacterId.Soldier, 40, 40)
    m.clearSent()
    val fence = m.useItem(p, ItemType.Fence, 10, 13)
    assertFalse("used up", m.hasItem(p, fence))
    val cells = Set((9, 13), (10, 13), (11, 13))
    assertEquals("the one who put it down", cells, fenceToldTo(m, p))
    assertEquals("and everyone else", cells, fenceToldTo(m, other))
    // Whoever comes back to the match later is told too
    m.rejoin(other)
    assertEquals("on rejoining", cells, fenceToldTo(m, other))
    m.clearSent()
    val second = m.useItem(p, ItemType.Fence, 10, 8)
    assertFalse("nothing is given back of a fence that went down", givenBack(m, p, second))
  }

  @Test def aFenceLiesAcrossTheWayItsBuilderLastStepped(): Unit = {
    // Facing up or down it runs left to right, facing left or right it runs up and down: the
    // target's cell and the one either side of it, and no other
    for (((dx, dy), across) <- Seq((0, 1) -> (1, 0), (0, -1) -> (1, 0), (1, 0) -> (0, 1), (-1, 0) -> (0, 1))) {
      val fresh = new TestMatch()
      val p = fresh.join(CharacterId.Gladiator, 20, 20)
      assertTrue(fresh.move(p, 20, 20))
      assertTrue(fresh.move(p, 20 + dx, 20 + dy, gapMs = 60))
      val (tx, ty) = (20 + dx * 4, 20 + dy * 4)
      fresh.useItem(p, ItemType.Fence, tx, ty)
      val expected = Set((tx - across._1, ty - across._2), (tx, ty), (tx + across._1, ty + across._2))
      val built = (for (x <- 0 until 60; y <- 0 until 60 if fresh.world.getTile(x, y) == Tile.Fence) yield (x, y)).toSet
      assertEquals(s"after a step of ${(dx, dy)}", expected, built)
    }
  }

  @Test def aFenceAtTheMapsEdgeIsWhatOfItFitsOnTheMap(): Unit = {
    // No map in worlds/ has open ground on its edge, so nothing ever stood a fence there
    val world = WorldData.createEmpty(30, 30)
    val edge = new TestMatch(world)
    def fences: Set[(Int, Int)] = (for (x <- 0 until 30; y <- 0 until 30 if world.getTile(x, y) == Tile.Fence) yield (x, y)).toSet
    val down = edge.join(CharacterId.Gladiator, 2, 2) // facing down: it runs along x
    edge.useItem(down, ItemType.Fence, 0, 4)
    assertEquals("centred on the first column", Set((0, 4), (1, 4)), fences)
    edge.useItem(down, ItemType.Fence, 1, 6)
    assertEquals("and beside it", Set((0, 4), (1, 4), (0, 6), (1, 6), (2, 6)), fences)
    val right = edge.join(CharacterId.Gladiator, 10, 2)
    assertTrue(edge.move(right, 10, 2))
    assertTrue(edge.move(right, 11, 2, gapMs = 60)) // facing right: it runs along y
    edge.useItem(right, ItemType.Fence, 13, 0)
    assertEquals("centred on the first row", Set((13, 0), (13, 1)), fences -- Set((0, 4), (1, 4), (0, 6), (1, 6), (2, 6)))
    edge.useItem(right, ItemType.Fence, 14, 1)
    assertTrue("and beside it", Set((14, 0), (14, 1), (14, 2)).subsetOf(fences))
    val corner = edge.join(CharacterId.Gladiator, 27, 27)
    edge.useItem(corner, ItemType.Fence, 29, 29)
    assertTrue("in the last corner", Set((28, 29), (29, 29)).subsetOf(fences))
  }

  @Test def aFenceReachesAsFarAsItMayToTheCell(): Unit = {
    val p = m.join(CharacterId.Gladiator, 10, 10)
    val reach = Constants.FENCE_MAX_DISTANCE +
      Movement.lagCells(Constants.NETWORK_TOLERANCE_MS, CharacterDef.get(CharacterId.Gladiator.id).moveSpeed)
    val fence = m.useItem(p, ItemType.Fence, 10, 10 + reach)
    assertEquals(Tile.Fence, m.world.getTile(10, 10 + reach))
    assertFalse(m.hasItem(p, fence))
    // Counted in steps, not as the crow flies
    val q = m.join(CharacterId.Gladiator, 20, 40)
    val diagonal = m.useItem(q, ItemType.Fence, 20 + reach, 41)
    assertEquals("a cell past it, round a corner", Tile.Grass, m.world.getTile(20 + reach, 41))
    assertTrue(m.hasItem(q, diagonal))
  }

  @Test def aFenceThatCannotGoDownIsGivenBack(): Unit = {
    // Off the map, onto a wall or water, or over the opening's wall: nothing is built, and the
    // fence is back in its builder's inventory on both ends
    val p = m.join(CharacterId.Gladiator, 10, 10)
    m.world.setTile(10, 13, Tile.Wall)
    m.world.setTile(13, 10, Tile.Water)
    for ((x, y) <- Seq((10, 13), (13, 10), (10, -1), (-1, 10), (10, 60), (60, 10))) {
      m.clearSent()
      val before = m.world.getTile(Math.max(0, Math.min(59, x)), Math.max(0, Math.min(59, y)))
      val item = { var it: Item = null; ServerTestKit.captureStderr { it = m.useItem(p, ItemType.Fence, x, y) }; it }
      assertTrue(s"a fence at ($x, $y) is still theirs", m.hasItem(p, item))
      assertTrue(s"a fence at ($x, $y) is given back", givenBack(m, p, item))
      assertEquals(before, m.world.getTile(Math.max(0, Math.min(59, x)), Math.max(0, Math.min(59, y))))
      assertEquals(s"nobody is told of a fence at ($x, $y)", Set.empty, fenceToldTo(m, p))
    }
    val walled = new TestMatch(gameMode = 1, opening = true)
    val q = walled.join(CharacterId.Soldier, 28, 30, team = 1)
    walled.clearSent()
    val over = walled.useItem(q, ItemType.Fence, 32, 30)
    assertTrue("over the opening's wall", walled.hasItem(q, over))
    assertTrue(givenBack(walled, q, over))
  }

  // --- What everyone else sees, and for how long ---

  @Test def aStarsJumpIsSeenByEveryoneAtOnce(): Unit = {
    // Not on the jumper's next step, which may be a while coming
    val p = m.join(CharacterId.Gladiator, 10, 10)
    val other = m.join(CharacterId.Soldier, 40, 40)
    assertTrue(m.move(p, 10, 10))
    m.clearSent()
    val star = m.useItem(p, ItemType.Star, 14, 10)
    assertEquals((14, 10), m.at(p))
    assertFalse("used up", m.hasItem(p, star))
    assertEquals("the others are told where it took them", Some(new Position(14, 10)),
      m.updatesAbout(m.sent(other), p).lastOption.map(_.getPosition))
  }

  @Test def aGemIsSeenByEveryoneAsItIsUsed(): Unit = {
    val p = m.join(CharacterId.Soldier, 10, 30)
    val other = m.join(CharacterId.Soldier, 40, 40)
    m.clearSent()
    m.useItem(p, ItemType.Gem)
    assertTrue(m.updatesAbout(m.sent(other), p).exists(u => (u.getEffectFlags & 0x02) != 0))
  }

  @Test def aGemsShotsFlyTwiceAsFastAndNoFurther(): Unit = {
    val plain = m.join(CharacterId.Soldier, 10, 20)
    val boosted = m.join(CharacterId.Soldier, 10, 40)
    m.useItem(boosted, ItemType.Gem)
    val a = m.launch(plain, ProjectileType.BULLET, 1f, 0f)
    val b = m.launch(boosted, ProjectileType.BULLET, 1f, 0f)
    m.tick()
    val (a1, b1) = (a.getX, b.getX)
    m.tick(4)
    assertEquals("twice the ground in the same ticks", 2 * (a.getX - a1), b.getX - b1, 1e-3f)
    // To the end of the same range, in half the time
    def ticksToTheEnd(owner: Player): (Int, Float) = {
      val shot = m.launch(owner, ProjectileType.BULLET, 1f, 0f)
      var ticks = 0
      var lastX = shot.getX
      while (m.instance.projectileManager.getProjectile(shot.id) != null && ticks < 1000) { lastX = shot.getX; m.tick(); ticks += 1 }
      (ticks, lastX)
    }
    m.tickUntilGone(a)
    m.tickUntilGone(b)
    val (slow, slowTo) = ticksToTheEnd(plain)
    val (fast, fastTo) = ticksToTheEnd(boosted)
    assertEquals(s"$slow ticks without it", (slow + 1) / 2, fast)
    assertEquals("as far and no further", slowTo, fastTo, 1.5f)
  }

  @Test def aShieldAndAGemLastTheirFiveSecondsToTheMillisecond(): Unit = Sim() { clock =>
    val sim = new SimMatch(clock)
    val p = sim.join(CharacterId.Soldier, 10, 30)
    ServerTestKit.quietly { sim.useItem(p, ItemType.Shield); sim.useItem(p, ItemType.Gem) }
    assertEquals(5000, Constants.SHIELD_DURATION_MS)
    assertEquals(5000, Constants.GEM_DURATION_MS)
    clock.advance(4999)
    assertTrue(p.hasShield)
    assertTrue(p.hasGemBoost)
    clock.advance(1)
    assertFalse("the shield, five seconds on", p.hasShield)
    assertFalse("the gem, five seconds on", p.hasGemBoost)
  }

  @Test def nothingAPlayerDoesWithAnItemIsPassedOnAsItCame(): Unit = {
    // The server tells everyone what came of it (a heal, a jump, a fence's cells) itself; what the
    // handler says to pass on, GameServer sends to the rest of the match as it came
    val p = m.join(CharacterId.Gladiator, 10, 10)
    var seq = 5000
    def passedOn(itemType: ItemType, x: Int = 0, y: Int = 0, held: Boolean = true, action: Byte = ItemAction.USE): Boolean = {
      seq += 1
      val item = new Item(seq, 0, 0, itemType)
      if (held) m.instance.itemManager.addToInventory(p.getId, item)
      var said = false
      ServerTestKit.captureStderr(ServerTestKit.quietly {
        said = m.instance.handler.processPacket(new ItemPacket(seq, p.getId, x, y, itemType.id, item.id, action), null, null)
      })
      if (held) m.instance.itemManager.removeFromInventory(p.getId, item.id)
      said
    }
    assertFalse("a heart", passedOn(ItemType.Heart))
    assertFalse("a shield", passedOn(ItemType.Shield))
    assertFalse("a gem", passedOn(ItemType.Gem))
    assertFalse("a fence that went down", passedOn(ItemType.Fence, 10, 13))
    assertFalse("a fence that didn't", passedOn(ItemType.Fence, 50, 50))
    assertFalse("a star that took them", passedOn(ItemType.Star, 12, 10))
    assertFalse("a star that didn't", passedOn(ItemType.Star, 55, 55))
    assertFalse("an item they haven't got", passedOn(ItemType.Heart, held = false))
    for (action <- Seq(ItemAction.SPAWN, ItemAction.PICKUP, ItemAction.INVENTORY, ItemAction.USE_REJECTED))
      assertFalse(s"what only the server says of an item (action $action)", passedOn(ItemType.Heart, action = action))
    p.setHealth(0)
    assertFalse("from the dead", passedOn(ItemType.Heart))
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
