package com.gridgame.client

import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

import java.util.UUID

/** A match as the client lives it: the start, deaths and respawns, items, and the end. */
class GameClientMatchTest {
  private val t = new TestClient()
  private val c = t.client

  @Test def aMatchStartsWhereTheServerPlacedUs(): Unit = {
    // The client used to keep a spawn of its own picking and send it, which the server took:
    // players could start on the same spawn, and everyone saw each other teleport
    t.clearSent()
    t.startMatch(spawn = (40, 12))
    assertEquals((40, 12), t.at)
    assertTrue("nothing sent of its own", t.sentUpdates.isEmpty)
    assertEquals(ClientState.PLAYING, c.clientState)
  }

  @Test def ourTeamComesFromOurJoin(): Unit = {
    t.startMatch(spawn = (5, 5), team = 2)
    assertEquals(2.toByte, c.localTeamId)
  }

  @Test def aMatchStartClearsTheLastOne(): Unit = {
    t.startMatch(spawn = (5, 5))
    val other = UUID.randomUUID()
    t.join(other, 9, 9)
    t.gameEvent(GameEvent.KILL, who = t.id, target = other, kills = 3)
    t.item(ItemAction.SPAWN, 1, ItemType.Heart)
    assertEquals(3, c.killCount)
    t.startMatch(spawn = (6, 6))
    assertEquals(0, c.killCount)
    assertTrue(c.killFeed.isEmpty)
    assertTrue(c.getItems.isEmpty)
    assertFalse(c.getIsDead)
  }

  @Test def aKillIsCountedAndReachesTheFeed(): Unit = {
    t.startMatch(spawn = (5, 5))
    val victim = UUID.randomUUID()
    t.join(victim, 9, 9, charId = CharacterId.Wizard.id)
    t.gameEvent(GameEvent.KILL, who = t.id, target = victim, kills = 1, deaths = 0)
    assertEquals(1, c.killCount)
    val feed = c.killFeed.get(c.killFeed.size - 1)
    assertEquals("You", feed(1))
    assertEquals("Wizard", feed(2))
  }

  @Test def ourDeathIsCounted(): Unit = {
    t.startMatch(spawn = (5, 5))
    val killer = UUID.randomUUID()
    t.join(killer, 9, 9, charId = CharacterId.Samurai.id)
    t.update(t.id, 5, 5, health = 0)
    assertTrue(c.getIsDead)
    t.gameEvent(GameEvent.KILL, who = killer, target = t.id, kills = 1)
    assertEquals(1, c.deathCount)
    assertEquals("Samurai", c.lastKillerCharacterName)
  }

  @Test def aRespawnBringsUsBackWholeAtTheSpawn(): Unit = {
    t.startMatch(spawn = (5, 5))
    t.item(ItemAction.INVENTORY, 1, ItemType.Heart)
    t.update(t.id, 5, 5, health = 0)
    assertTrue(c.getIsDead)
    t.gameEvent(GameEvent.RESPAWN, who = t.id, spawn = (33, 44))
    assertFalse(c.getIsDead)
    assertEquals(c.getSelectedCharacterMaxHealth, c.getLocalHealth)
    assertEquals((33, 44), t.at)
    assertEquals("the inventory went with the death", 0, c.getInventoryCount)
    assertEquals(0f, c.getQCooldownRemaining, 0f)
  }

  @Test def theScoreboardEndsTheMatch(): Unit = {
    t.startMatch(spawn = (5, 5))
    var shown = 0
    c.gameOverListener = () => shown += 1
    t.gameEvent(GameEvent.GAME_OVER)
    t.gameEvent(GameEvent.SCORE_ENTRY, who = t.id, kills = 4, deaths = 1, rank = 1)
    t.gameEvent(GameEvent.SCORE_ENTRY, who = UUID.randomUUID(), kills = 2, deaths = 3, rank = 2)
    t.gameEvent(GameEvent.SCORE_END)
    assertEquals(ClientState.SCOREBOARD, c.clientState)
    assertEquals(1, shown)
    assertEquals(2, c.scoreboard.size)
    assertEquals(4, c.scoreboard.get(0).kills)
  }

  @Test def theEndOfAMatchWeLeftDoesNotPullUsOutOfTheBrowser(): Unit = {
    t.startMatch(spawn = (5, 5))
    var shown = 0
    c.gameOverListener = () => shown += 1
    c.leaveMatch()
    assertEquals(ClientState.LOBBY_BROWSER, c.clientState)
    t.gameEvent(GameEvent.GAME_OVER)
    t.gameEvent(GameEvent.SCORE_END)
    assertEquals(ClientState.LOBBY_BROWSER, c.clientState)
    assertEquals(0, shown)
    assertTrue(t.sentLobbyActions.exists(_.getAction == LobbyAction.LEAVE))
  }

  @Test def whatWePickUpIsOursAndWhatOthersPickUpIsNot(): Unit = {
    t.startMatch(spawn = (5, 5))
    t.item(ItemAction.SPAWN, 1, ItemType.Gem)
    t.item(ItemAction.SPAWN, 2, ItemType.Heart)
    t.item(ItemAction.PICKUP, 1, ItemType.Gem)
    t.item(ItemAction.PICKUP, 2, ItemType.Heart, who = UUID.randomUUID())
    assertEquals(1, c.getItemCount(ItemType.Gem.id))
    assertEquals(0, c.getItemCount(ItemType.Heart.id))
    assertTrue("both are gone from the ground", c.getItems.isEmpty)
  }

  @Test def aRefusedStarIsGivenBackAndPutsUsBack(): Unit = {
    t.startMatch(spawn = (10, 10))
    t.item(ItemAction.INVENTORY, 9, ItemType.Star)
    c.setMouseWorldPosition(20.0, 10.0)
    c.useItem(ItemType.Star.id)
    assertEquals("the client shows the teleport at once", (20, 10), t.at)
    assertEquals(0, c.getItemCount(ItemType.Star.id))
    t.item(ItemAction.USE_REJECTED, 9, ItemType.Star, x = 10, y = 10)
    assertEquals((10, 10), t.at)
    assertEquals(1, c.getItemCount(ItemType.Star.id))
  }

  @Test def aStarGoesToTheCellTheServerWillCheck(): Unit = {
    t.startMatch(spawn = (10, 10))
    t.item(ItemAction.INVENTORY, 9, ItemType.Star)
    c.setMouseWorldPosition(55.0, 10.0) // past its range
    c.useItem(ItemType.Star.id)
    val use = t.sent.collect { case i: ItemPacket if i.getAction == ItemAction.USE => i }.last
    assertEquals((use.getX, use.getY), t.at)
    assertTrue(Teleport.isValidStarTarget(WorldData.createEmpty(60, 60), 10, 10, use.getX, use.getY))
  }

  @Test def theTimerCountsDownFromTheServersClock(): Unit = {
    t.startMatch(spawn = (5, 5))
    t.gameEvent(GameEvent.TIME_SYNC, remaining = 95)
    assertTrue(c.gameTimeRemaining <= 95 && c.gameTimeRemaining >= 94)
  }
}
