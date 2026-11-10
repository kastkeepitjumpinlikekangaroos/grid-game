package com.gridgame.server

import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

/**
 * A team match opens with the two teams walled off from each other: each starts in its own half
 * of the map and, for the first thirty seconds, nothing at all crosses the line between them —
 * no shot, no blast, and nobody's feet. Team 1 holds the low half, team 2 the high one, and a
 * death puts a player back with their own team.
 */
class TeamDividerTest {
  private val m = new TestMatch(gameMode = 1, opening = true)
  private val MID = 30 // the divider's line on a 60x60 map

  /** Run the opening out: the next tick takes the wall down and says so. */
  private def expireDivider(): Unit = {
    m.instance.openingEndsAt = System.currentTimeMillis() - 1
    m.world.divider = TeamDivider.raise(m.world, System.currentTimeMillis() - 1)
  }

  private def unhurt(p: Player): Boolean = p.getHealth == p.getMaxHealth

  private def dividerEvents(packets: Seq[Packet]): Seq[GameEventPacket] =
    packets.collect { case g: GameEventPacket if g.getEventType == GameEvent.MATCH_OPENING => g }

  // --- The halves ---

  @Test def eachTeamStartsInItsOwnHalf(): Unit = {
    var taken = Set.empty[(Int, Int)]
    for (team <- Seq[Byte](1, 2); _ <- 0 until 8) {
      val sp = m.instance.spawnFor(team, taken)
      taken += ((sp.getX, sp.getY))
      assertEquals(s"team $team spawned at $sp", TeamDivider.sideOfTeam(team),
        TeamDivider.sideOf(m.world, sp.getX, sp.getY))
    }
  }

  @Test def aDeathPutsYouBackWithYourTeam(): Unit = {
    val p = m.join(CharacterId.Soldier, 20, 30, team = 1)
    p.setPosition(new Position(MID + 5, 30)) // however they ended up over there
    m.instance.respawn(p.getId)
    assertEquals("back on their own side", -1, TeamDivider.sideOf(m.world, m.at(p)._1, m.at(p)._2))
  }

  @Test def aFreeForAllHasNoDivider(): Unit = {
    val ffa = new TestMatch()
    assertNull(ffa.instance.divider)
    val a = ffa.join(CharacterId.Soldier, MID - 3, 30)
    val b = ffa.join(CharacterId.Soldier, MID + 3, 30)
    ffa.tickUntilGone(ffa.launch(a, ProjectileType.BULLET, 1f, 0f))
    assertFalse("nothing stands in the middle of the map", unhurt(b))
  }

  // --- What the wall stops ---

  @Test def aShotAtTheOtherTeamIsStopped(): Unit = {
    val a = m.join(CharacterId.Soldier, MID - 3, 30, team = 1)
    val b = m.join(CharacterId.Soldier, MID + 3, 30, team = 2)
    m.clearSent()
    m.tickUntilGone(m.launch(a, ProjectileType.BULLET, 1f, 0f))
    assertTrue("never touched", unhurt(b))
    val blocked = m.projectileEvents(m.sent(a), ProjectileAction.BLOCKED)
    assertEquals(1, blocked.size)
    assertNull("nobody's barrier: the wall between the teams", blocked.head.getTargetId)
    assertEquals("stopped on the line", MID.toFloat, blocked.head.getX, 0.2f)
  }

  @Test def aShotIsStoppedAHairShortOfTheLineOnItsOwnSideWhicheverWayItCame(): Unit = {
    // Where everyone is told it stopped, which is where it is drawn striking the wall: on the path
    // it was flying, a twentieth of a cell before the line
    for ((from, heading, team) <- Seq(((MID - 3, 30), (1f, 0f), 1), ((MID + 3, 30), (-1f, 0f), 2),
        ((MID - 4, 26), (0.8f, 0.6f), 1), ((MID + 4, 34), (-0.8f, -0.6f), 2))) {
      val fresh = new TestMatch(gameMode = 1, opening = true)
      val a = fresh.join(CharacterId.Soldier, from._1, from._2, team = team.toByte)
      fresh.clearSent()
      fresh.tickUntilGone(fresh.launch(a, ProjectileType.BULLET, heading._1, heading._2))
      val heard = fresh.sent(a).collect { case p: ProjectilePacket => p }
      val blocked = heard.filter(_.getAction == ProjectileAction.BLOCKED)
      assertEquals(s"from $from along $heading: stopped once", 1, blocked.size)
      assertEquals("and that is the last said of it", ProjectileAction.BLOCKED, heard.last.getAction)
      val side = if (team == 1) -1 else 1
      // Square on, a twentieth of a cell short of it exactly; on a slant, that much back along its
      // path at most, and never on the line or past it
      val short = side * (blocked.head.getX - MID) // how far into its own half
      if (heading._2 == 0f) assertEquals(s"from $from along $heading: a hair short of the line", 0.05f, short, 0.001f)
      else assertTrue(s"from $from along $heading: on its own side of the line and within a hair of it: $short", short > 0f && short <= 0.051f)
      // On the line it was flying: as far up or down it as it had come along
      val along = (blocked.head.getX - from._1) / heading._1
      assertEquals(s"from $from along $heading: on its path", from._2 + heading._2 * along, blocked.head.getY, 0.01f)
    }
  }

  @Test def whileTheWallStandsAShotOnItsOwnSideFliesAsAnyOtherDoes(): Unit = {
    // The wall is only the wall: short of it a shot keeps its pace, stops at a wall of its own
    // side's, and ends at the end of its range
    val bullet = ProjectileDef.get(ProjectileType.BULLET)
    val a = m.join(CharacterId.Soldier, MID - 5, 30, team = 1)
    // Away from the wall: its whole range, and gone there
    m.clearSent()
    val away = m.launch(a, ProjectileType.BULLET, -1f, 0f)
    m.tick()
    val first = away.getX
    m.tick()
    assertEquals("its pace, a tick", -bullet.speedMultiplier, away.getX - first, 1e-4f)
    m.tickUntilGone(away)
    val ended = m.sent(a).collect { case p: ProjectilePacket => p }
    assertEquals("ended at its range, not by the wall", ProjectileAction.DESPAWN, ended.last.getAction)
    assertTrue(s"having flown its ${bullet.maxRange} cells: ${away.getDistanceTraveled}",
      away.getDistanceTraveled >= bullet.maxRange && away.getDistanceTraveled < bullet.maxRange + 0.51f)
    // Toward it, with a wall of its own side's in the way: stopped there
    m.world.setTile(MID - 2, 30, Tile.Wall)
    m.clearSent()
    val shot = m.launch(a, ProjectileType.BULLET, 1f, 0f)
    m.tickUntilGone(shot)
    val stopped = m.sent(a).collect { case p: ProjectilePacket => p }
    assertEquals("stopped by the terrain", ProjectileAction.DESPAWN, stopped.last.getAction)
    assertTrue(s"at the wall two cells short of the line: ${stopped.last.getX}", stopped.last.getX < MID - 1.4f && stopped.last.getX > MID - 2.6f)
    m.world.setTile(MID - 2, 30, Tile.Grass)
  }

  @Test def aRocketStoppedByTheWallGoesOffThereAndEveryoneIsToldSo(): Unit = {
    // An explosive's end is its blast: said as any explosive's is, where the wall stopped it
    val a = m.join(CharacterId.Bombardier, MID - 3, 30, team = 1)
    val watcher = m.join(CharacterId.Soldier, MID + 10, 40, team = 2)
    m.clearSent()
    m.tickUntilGone(m.launch(a, ProjectileType.ROCKET, 1f, 0f))
    for (who <- Seq(a, watcher)) {
      val heard = m.sent(who).collect { case p: ProjectilePacket => p }
      assertTrue("no word of a block: it was not just stopped", heard.forall(_.getAction != ProjectileAction.BLOCKED))
      assertEquals("its end is told", ProjectileAction.DESPAWN, heard.last.getAction)
      assertEquals("a hair short of the line", MID - 0.05f, heard.last.getX, 0.01f)
    }
  }

  @Test def evenOneThatFliesOverWallsIsStopped(): Unit = {
    val a = m.join(CharacterId.Lich, MID - 3, 30, team = 1)
    val b = m.join(CharacterId.Soldier, MID + 3, 30, team = 2)
    m.tickUntilGone(m.launch(a, ProjectileType.DEATH_BOLT, 1f, 0f))
    assertTrue("a wall-passer is still held by this one", unhurt(b))
  }

  @Test def aBlastDoesNotReachOverIt(): Unit = {
    val a = m.join(CharacterId.Bombardier, MID - 3, 30, team = 1)
    val b = m.join(CharacterId.Soldier, MID + 2, 30, team = 2)
    m.tickUntilGone(m.launch(a, ProjectileType.ROCKET, 1f, 0f))
    assertTrue("the rocket went off on the line, and its blast stayed there", unhurt(b))
    // ...and it really did have the reach: the same shot, once the wall is down
    expireDivider()
    m.tick()
    m.tickUntilGone(m.launch(a, ProjectileType.ROCKET, 1f, 0f))
    assertFalse(unhurt(b))
  }

  @Test def aStepAcrossIsRefused(): Unit = {
    val p = m.join(CharacterId.Soldier, MID - 1, 30, team = 1)
    assertFalse("into the wall", m.move(p, MID, 30, gapMs = 60))
    assertFalse("or over it", m.move(p, MID + 1, 30, gapMs = 60))
    assertEquals((MID - 1, 30), m.at(p))
    assertTrue("along your own side is fine", m.move(p, MID - 1, 31, gapMs = 60))
  }

  @Test def aFenceCannotBeSetDownOverIt(): Unit = {
    val p = m.join(CharacterId.Soldier, MID - 2, 30, team = 1)
    m.useItem(p, ItemType.Fence, MID + 2, 30)
    assertNotEquals("nothing of yours reaches the far half", Tile.Fence, m.world.getTile(MID + 2, 30))
    m.useItem(p, ItemType.Fence, MID - 4, 30)
    assertEquals("one on your own side is fine", Tile.Fence, m.world.getTile(MID - 4, 30))
  }

  /** A spawn request from the player's client that says it was fired from (x, y), heading +x. */
  private def fireFrom(p: Player, x: Int, y: Int, seq: Int): Unit =
    m.instance.handler.processPacket(PacketSerializer.deserialize(ProjectilePacket.spawnRequest(seq, p.getId, x.toFloat, y.toFloat,
      p.getColorRGB, 1f, 0f, 0.toByte, CharacterDef.get(p.getCharacterId).primaryProjectileType, AttackSlot.PRIMARY).serialize()), null, null)

  @Test def aShotSaidToBeFiredFromTheFarHalfIsRefused(): Unit = {
    // Where a shot says it was fired from is taken within three cells, and the lag, of where the
    // server has its shooter: far enough to reach over the wall, where the shot was put down past
    // the one line it is stopped on
    val a = m.join(CharacterId.Soldier, MID - 2, 30, team = 1)
    val b = m.join(CharacterId.Soldier, MID + 6, 30, team = 2)
    fireFrom(a, MID + 2, 30, seq = 1)
    assertEquals("nothing is fired from the other team's half", 0, m.instance.projectileManager.size)
    fireFrom(a, MID, 30, seq = 2)
    assertEquals("nor from the wall itself", 0, m.instance.projectileManager.size)
    m.tick(40)
    assertTrue(unhurt(b))
    // It cost nothing: the shot from where they stand goes, and stops at the wall
    fireFrom(a, MID - 2, 30, seq = 3)
    assertEquals(1, m.instance.projectileManager.size)
    m.tick(40)
    assertTrue(unhurt(b))
    // Once the wall is down a shot from a step or two ahead of the server's copy is taken, as ever
    expireDivider()
    m.tick()
    m.instance.handler.resetAttacks(a.getId)
    fireFrom(a, MID + 2, 30, seq = 4)
    assertEquals(1, m.instance.projectileManager.size)
    m.tick(40)
    assertFalse(unhurt(b))
  }

  @Test def aTrapCannotBeThrownOverItHoweverFarBehindTheServersCopyIs(): Unit = {
    // A throw the strict check refuses is taken on any open cell within its range and the lag
    // (TrapPlacement.isValidTargetLagging), without the way there judged: the wall is in the way
    // of that too
    val warden = m.join(CharacterId.Warden, MID - 2, 30, team = 1)
    val trapSlot = if (CharacterDef.get(CharacterId.Warden.id).qAbility.castBehavior.isInstanceOf[TrapCast]) AttackSlot.Q else AttackSlot.E
    assertNull("not into the other team's half", m.placeTrap(warden, trapSlot, TrapType.BEAR_TRAP, MID + 3, 30))
    assertNull("not onto the wall", m.placeTrap(warden, trapSlot, TrapType.BEAR_TRAP, MID, 30))
    assertEquals(0, m.instance.trapManager.size)
    assertNotNull("the refusals cost nothing: one in their own half is taken", m.placeTrap(warden, trapSlot, TrapType.BEAR_TRAP, MID - 4, 30))
  }

  @Test def aPhaseDoesNotPassThroughItEither(): Unit = {
    val p = m.join(CharacterId.Wraith, MID - 1, 30, team = 1)
    // Flags 0x08 is the client saying it has phased, which walks at twice the pace (and walked
    // through walls once)
    assertFalse(m.move(p, MID + 1, 30, flags = 0x08, gapMs = 60))
    assertTrue(p.isPhased)
    assertEquals((MID - 1, 30), m.at(p))
  }

  // --- Going up and coming down ---

  @Test def bothTeamsAreToldWhenItGoesUpAndWhenItDrops(): Unit = {
    val p = m.join(CharacterId.Soldier, MID - 5, 30, team = 1)
    m.clearSent()
    m.tick()
    val up = dividerEvents(m.sent(p))
    assertEquals(1, up.size)
    assertTrue(s"how long it has left: ${up.head.getOpeningMs}", up.head.getOpeningMs > 25000)
    assertEquals("a wall down the middle", MatchOpening.DIVIDER, up.head.getOpeningRules)
    m.tick()
    assertTrue("said once, not every tick", dividerEvents(m.sent(p)).isEmpty)

    expireDivider()
    m.tick()
    val down = dividerEvents(m.sent(p))
    assertEquals(1, down.size)
    assertEquals(0, down.head.getOpeningMs)
    assertEquals(MatchOpening.DIVIDER, down.head.getOpeningRules)
    assertNull("and it is gone from the world", m.instance.divider)
  }

  @Test def onceItIsDownTheTeamsCanReachEachOther(): Unit = {
    val a = m.join(CharacterId.Soldier, MID - 3, 30, team = 1)
    val b = m.join(CharacterId.Soldier, MID + 3, 30, team = 2)
    expireDivider()
    m.tick()
    assertTrue(m.move(a, MID - 3, 30))
    assertTrue(m.move(a, MID, 30, gapMs = 60))
    m.tickUntilGone(m.launch(a, ProjectileType.BULLET, 1f, 0f))
    assertFalse(unhurt(b))
  }
}
