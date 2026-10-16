package com.gridgame.client

import com.gridgame.common.Constants
import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

import java.util.UUID
import com.gridgame.client.game.ClientState

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

  @Test def aListenerThatThrowsAsTheMatchStartsCostsNoneOfTheMatch(): Unit = {
    // A match arrives as one batch on the packet thread: its start, then where everyone is. The
    // batch was caught as a whole, so a screen's listener throwing at the start lost the rest of
    // it: we stayed where the last match had us, and nobody else was there.
    c.gameStartingListener = () => throw new IllegalStateException("a screen's listener, broken")
    val other = UUID.randomUUID()
    c.processAll(java.util.List.of[Packet](
      new LobbyActionPacket(1, t.id, Packet.getCurrentTimestamp, LobbyAction.GAME_STARTING, 7.toShort),
      new PlayerJoinPacket(2, t.id, Packet.getCurrentTimestamp, new Position(12, 34), 0xFF00AA00, "me", 100, 0.toByte, 0.toByte),
      new PlayerJoinPacket(3, other, Packet.getCurrentTimestamp, new Position(40, 40), 0xFF00AA00, "them", 100, 0.toByte, 0.toByte)))
    assertEquals(ClientState.PLAYING, c.clientState)
    assertEquals("placed where the server put us", (12, 34), t.at)
    assertTrue("and everyone else is there", c.getPlayers.containsKey(other))
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

  @Test def aMatchStartsOnANewLife(): Unit = {
    // What the last match ended with came into the next: an ability cast as it ended still cooling
    // down, a freeze still holding us, a gem still tripling our shots
    c.selectedCharacterId = CharacterId.Soldier.id
    t.startMatch(spawn = (5, 5))
    c.setMouseWorldPosition(30.0, 20.0)
    c.shootAbility(1)
    t.item(ItemAction.INVENTORY, 1, ItemType.Gem)
    c.useItem(ItemType.Gem.id)
    t.update(t.id, 5, 5, flags = 0x04 | 0x40) // frozen and rooted
    assertTrue(c.getECooldownRemaining > 0 && c.isFrozen && c.isRooted && c.hasGemBoost)
    t.startMatch(spawn = (6, 6))
    assertEquals(0f, c.getECooldownRemaining, 0f)
    assertFalse(c.isFrozen)
    assertFalse(c.isRooted)
    assertFalse(c.hasGemBoost)
  }

  @Test def aChargeOrADashTheLastMatchEndedInEndsWithIt(): Unit = {
    // Its window is gone before the button comes up or the dash is over. The charge went on at
    // full strength, and at a charge's pace; the dash finished on the next match's first frame,
    // putting us where it had been going in the last one
    val (dasher, slot) = CharacterDef.all.iterator.flatMap { d =>
      Seq((0, d.qAbility), (1, d.eAbility)).collectFirst { case (s, a) if a.castBehavior.isInstanceOf[DashBuff] => (d.id.id, s) }
    }.next()
    c.selectedCharacterId = dasher
    t.startMatch(spawn = (5, 5))
    c.startCharging()
    c.setMouseWorldPosition(15.0, 5.0)
    c.shootAbility(slot)
    assertTrue(c.isCharging && c.isSwooping)
    t.startMatch(spawn = (40, 40))
    assertFalse("still charging", c.isCharging)
    assertFalse("still dashing", c.isSwooping)
    c.tickSwoop() // what the input handlers do each frame
    assertEquals((40, 40), t.at)
  }

  @Test def aChargeBuildsOnTheClientsOwnClock(): Unit = {
    // It was timed on the wall clock, which the OS slews and now and then steps: a charge could jump,
    // or read below nothing. On nanoClock it only runs forward, and the golden frames can hold one
    c.selectedCharacterId = CharacterId.Tidecaller.id
    t.startMatch(spawn = (5, 5))
    c.startCharging()
    assertEquals(0, c.getChargeLevel)
    t.nanos += Constants.CHARGE_MAX_MS * 1000000L / 4
    assertEquals(25, c.getChargeLevel)
    t.nanos += Constants.CHARGE_MAX_MS * 1000000L
    assertEquals("full, and no more", 100, c.getChargeLevel)
    t.nanos -= Constants.CHARGE_MAX_MS * 3000000L
    assertEquals("never below nothing", 0, c.getChargeLevel)
  }

  @Test def weAreDrawnAtTheNewMatchsSpawn(): Unit = {
    // Drawn between the last two cells we stepped to, over the time between those steps: a step
    // after a pause at the end of a match had us drawn, and the camera on us, in the last match's
    // place until we walked
    t.startMatch(spawn = (5, 5))
    c.movePlayer(1, 0)
    Thread.sleep(250)
    c.movePlayer(1, 0)
    t.startMatch(spawn = (40, 40))
    c.updateVisualPosition()
    assertEquals((40.0, 40.0), (c.visualPosX, c.visualPosY))
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

  // --- A death and a respawn reach every client by TCP (GameEvent), whatever the datagrams do ---

  @Test def ourDeathReachesUsByTheKillAlone(): Unit = {
    // The update that says our health is 0 is a datagram, and was all that told us: lost, we went
    // on walking and shooting, every bit of it refused, until the respawn put us back at a spawn
    t.startMatch(spawn = (5, 5))
    val killer = UUID.randomUUID()
    t.join(killer, 9, 9)
    t.gameEvent(GameEvent.KILL, who = killer, target = t.id, kills = 1)
    assertTrue("dead", c.getIsDead)
    assertTrue("and waiting to respawn", c.isRespawning)
    assertEquals(0, c.getLocalHealth)
    assertTrue("the death shown", c.getLocalDeathTime > 0)
    t.gameEvent(GameEvent.RESPAWN, who = t.id, spawn = (30, 30))
    assertFalse(c.getIsDead)
    assertEquals((30, 30), t.at)
  }

  @Test def anotherPlayersDeathReachesUsByTheKillAlone(): Unit = {
    // Lost, their death update left them standing where they fell, alive, until they respawned
    t.startMatch(spawn = (5, 5))
    val victim = UUID.randomUUID()
    t.join(victim, 9, 9)
    t.gameEvent(GameEvent.KILL, who = t.id, target = victim, kills = 1)
    assertTrue("dead on our screen", c.getPlayers.get(victim).isDead)
    assertTrue("and seen dying", c.getDeathAnimations.containsKey(victim))
  }

  @Test def anotherPlayersRespawnReachesUsByTheRespawnAlone(): Unit = {
    // Only the update after it said so, a datagram: lost, a player who respawned and stood still at
    // full health stayed dead, and unseen, on our screen until something next happened to them
    t.startMatch(spawn = (5, 5))
    val them = UUID.randomUUID()
    t.join(them, 9, 9, charId = CharacterId.Gladiator.id)
    t.update(them, 9, 9, flags = 0x04 | 0x10 | 0x40, heldMs = 3000, rootedMs = 3000)
    t.gameEvent(GameEvent.KILL, who = t.id, target = them, kills = 1)
    t.gameEvent(GameEvent.RESPAWN, who = them, spawn = (40, 44))
    val p = c.getPlayers.get(them)
    assertFalse("back", p.isDead)
    assertEquals(new Position(40, 44), p.getPosition)
    assertEquals("whole", p.getMaxHealth, p.getHealth)
    assertFalse("and nothing on them", p.isFrozen || p.isBurning || p.isRooted)
  }

  @Test def anUpdateSentBeforeAKillCannotStandTheDeadBackUp(): Unit = {
    // Updates are datagrams and overtake each other: one sent just before the kill and delivered
    // after it put the dead player back on their feet, where they fell, until they respawned
    t.startMatch(spawn = (5, 5))
    val victim = UUID.randomUUID()
    t.join(victim, 9, 9)
    val before = t.peekSeq
    t.gameEvent(GameEvent.KILL, who = t.id, target = victim, kills = 1, seq = before + 5)
    t.update(victim, 10, 9, health = 40, seq = before + 2)
    assertTrue("still dead", c.getPlayers.get(victim).isDead)
    // What came after the kill counts: their respawn's update
    t.update(victim, 30, 30, health = 100, seq = before + 9)
    assertFalse(c.getPlayers.get(victim).isDead)
    assertEquals(new Position(30, 30), c.getPlayers.get(victim).getPosition)
  }

  @Test def aKillHeldUpBehindTheRespawnsUpdateKillsNobody(): Unit = {
    // The other way round: the kill, by TCP, held up on the way, behind their respawn's datagram
    t.startMatch(spawn = (5, 5))
    val them = UUID.randomUUID()
    t.join(them, 9, 9)
    val before = t.peekSeq
    t.update(them, 30, 30, health = 100, seq = before + 9)
    t.gameEvent(GameEvent.KILL, who = t.id, target = them, kills = 1, seq = before + 5)
    assertFalse("alive where they respawned", c.getPlayers.get(them).isDead)
    assertEquals("but the kill still counts", 1, c.killCount)
  }

  @Test def anUpdateSentBeforeOurRespawnCannotKillUsAgain(): Unit = {
    // The update saying we had died, delivered after the respawn, killed us again on our screen:
    // dead for the rest of the match, as no second respawn would come for a death the server
    // never had
    t.startMatch(spawn = (5, 5))
    val before = t.peekSeq
    t.gameEvent(GameEvent.KILL, who = UUID.randomUUID(), target = t.id, kills = 1, seq = before + 5)
    t.gameEvent(GameEvent.RESPAWN, who = t.id, spawn = (30, 30), seq = before + 7)
    t.update(t.id, 5, 5, health = 0, seq = before + 4)
    assertFalse("alive", c.getIsDead)
    assertEquals(c.getSelectedCharacterMaxHealth, c.getLocalHealth)
  }

  @Test def anUpdateThatOvertookANewerOneIsOldNews(): Unit = {
    // About anyone: the older one put them back where they had been, with the health and the
    // effects they had then
    t.startMatch(spawn = (5, 5))
    val them = UUID.randomUUID()
    t.join(them, 9, 9)
    val before = t.peekSeq
    t.update(them, 12, 9, health = 60, flags = 0x04, heldMs = 2000, seq = before + 3)
    t.update(them, 10, 9, health = 100, seq = before + 1)
    val p = c.getPlayers.get(them)
    assertEquals(new Position(12, 9), p.getPosition)
    assertEquals(60, p.getHealth)
    assertTrue("still frozen", p.isFrozen)
    // About us: a freeze that had just landed was lifted by an update sent before it
    t.update(t.id, 5, 5, health = 50, flags = 0x04, heldMs = 2000, seq = before + 6)
    t.update(t.id, 5, 5, health = 90, seq = before + 4)
    assertTrue("held", c.isFrozen)
    assertEquals(50, c.getLocalHealth)
  }

  @Test def wePlayTheCharacterTheServerHasUsPlay(): Unit = {
    // A character picked as the host started the match, or in the ranked queue as it found the
    // match, reached the server too late to count: we played the match as the one we had picked,
    // and every shot and cast was refused as another character's
    c.selectedCharacterId = CharacterId.Wizard.id
    t.lobby(LobbyAction.GAME_STARTING)
    c.setWorld(t.world)
    t.join(t.id, 12, 12, charId = CharacterId.Samurai.id)
    assertEquals(CharacterId.Samurai.id, c.selectedCharacterId)
    assertEquals(CharacterDef.get(CharacterId.Samurai).maxHealth, c.getSelectedCharacterMaxHealth)
    // Nor is one the server doesn't know taken from it
    t.lobby(LobbyAction.GAME_STARTING)
    t.join(t.id, 12, 12, charId = (-100).toByte)
    assertEquals(CharacterId.Samurai.id, c.selectedCharacterId)
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

  @Test def anItemPickedUpBeforeWeHeardOfItStaysOffTheGround(): Unit = {
    // The server sends an item's appearance and its pickup from different threads, and a pickup
    // made the moment it appeared can reach us first: the SPAWN after it left an item on the
    // ground for the rest of the match that the server no longer had, and nobody could pick up
    t.startMatch(spawn = (5, 5))
    t.item(ItemAction.PICKUP, 7, ItemType.Star, who = UUID.randomUUID())
    t.item(ItemAction.SPAWN, 7, ItemType.Star)
    assertTrue(c.getItems.isEmpty)
    // The next match numbers its items from 1 again
    t.startMatch(spawn = (6, 6))
    t.item(ItemAction.SPAWN, 7, ItemType.Star)
    assertEquals(1, c.getItems.size)
  }

  @Test def aFullInventoryIsToldFromAnItemWithinReach(): Unit = {
    // The server hands over items within ITEM_PICKUP_RADIUS of our cell while we carry fewer than
    // MAX_INVENTORY_SIZE, and leaves them on the ground after. The HUD says so from these.
    t.startMatch(spawn = (5, 5))
    for (k <- 0 until Constants.MAX_INVENTORY_SIZE - 1) t.item(ItemAction.INVENTORY, 100 + k, ItemType.Heart)
    t.item(ItemAction.SPAWN, 1, ItemType.Gem, x = 7, y = 6) // just out of reach
    assertFalse(c.isInventoryFull)
    assertFalse(c.itemWithinReach)
    t.item(ItemAction.SPAWN, 2, ItemType.Gem, x = 7, y = 5) // two cells: in reach
    assertTrue(c.itemWithinReach)
    t.item(ItemAction.INVENTORY, 199, ItemType.Heart)
    assertTrue(c.isInventoryFull)
    t.item(ItemAction.PICKUP, 2, ItemType.Gem, who = UUID.randomUUID())
    assertFalse(c.itemWithinReach)
    c.useItem(ItemType.Heart.id)
    assertFalse("room for one", c.isInventoryFull)
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

  @Test def aPlayerWhoLeftStaysGoneWhenALateUpdateAboutThemArrives(): Unit = {
    // Updates come over UDP and a leave over TCP, so one sent before the leave can arrive after
    // it. It used to bring them back: a player called "Player", standing where they left, for the
    // rest of the match, since no second leave would ever come for them
    t.startMatch(spawn = (5, 5))
    val other = UUID.randomUUID()
    t.join(other, 9, 9)
    t.leave(other)
    t.update(other, 10, 9)
    assertFalse(c.getPlayers.containsKey(other))
    assertTrue("still listed as having left", c.playerLeftMatch(other))
  }

  @Test def aPlayerTheServerSaysIsBackIsBack(): Unit = {
    t.startMatch(spawn = (5, 5))
    val other = UUID.randomUUID()
    t.join(other, 9, 9)
    t.leave(other)
    t.join(other, 12, 9)
    t.update(other, 13, 9)
    assertEquals(new Position(13, 9), c.getPlayers.get(other).getPosition)
    assertFalse(c.playerLeftMatch(other))
  }

  @Test def theTimerCountsDownFromTheServersClock(): Unit = {
    t.startMatch(spawn = (5, 5))
    t.gameEvent(GameEvent.TIME_SYNC, remaining = 95)
    assertTrue(c.gameTimeRemaining <= 95 && c.gameTimeRemaining >= 94)
  }
}
