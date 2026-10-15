package com.gridgame.server

import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

/**
 * What the volleys became (design/characters.md, *No volleys of more than three*), each landed in
 * a match: the slams on everyone in them, the splashes on whoever they burst over, the shots that
 * go on through or bounce off walls, the hastes on their casters, the traps and the ice wall.
 */
class KitReworkTest {
  private val m = new TestMatch()

  private def now: Long = System.currentTimeMillis()

  /** Cast a slam the way the server takes one (it goes off on its caster). */
  private def slam(owner: Player, pType: Byte): Unit = m.tickUntilGone(m.launch(owner, pType, 0f, 0f))

  private def shoot(owner: Player, pType: Byte, dx: Float = 1f, dy: Float = 0f): Unit =
    m.tickUntilGone(m.launch(owner, pType, dx, dy))

  // What each deals, read from its definition (the balance passes set the numbers)
  private def hit(pType: Byte): Int = ProjectileDef.get(pType).damage
  private def slamDamage(pType: Byte): Int = ProjectileDef.get(pType).aoeOnMaxRange.get.damage
  private def splashDamage(pType: Byte): Int = ProjectileDef.get(pType).aoeOnHit.get.damage

  // --- Hastes ---

  @Test def aHasteSpeedsItsCasterUpWithNobodyInIt(): Unit = {
    // The Gambler's Lucky Streak, alone on the map: its slam catches nobody, and he is quicker all
    // the same, for its whole time
    val gambler = m.join(CharacterId.Gambler, 10, 30)
    m.clearSent()
    m.fire(gambler, AttackSlot.Q, ProjectileType.LUCKY_STREAK, Seq((0f, 0f)))
    assertTrue(gambler.hasSpeedBoost)
    assertTrue(gambler.getSpeedBoostUntil - now > 2500)
    // And he is told so over TCP, with how long it has left, which is what his client walks by
    val told = m.updatesAbout(m.tcpSent(gambler), gambler).filter(u => (u.getEffectFlags & 0x20) != 0)
    assertTrue("told over TCP", told.nonEmpty)
    assertTrue(s"${told.last.getSpeedBoostMs}ms left", told.last.getSpeedBoostMs > 2500 && told.last.getSpeedBoostMs <= 3000)
  }

  @Test def everyUpdateAboutABoostedPlayerSaysHowLongItHasLeft(): Unit = {
    val wolf = m.join(CharacterId.Wolf, 10, 30)
    wolf.setSpeedBoostUntil(now + 1800)
    val update = m.instance.stateUpdate(wolf)
    assertTrue(update.getSpeedBoostMs > 1500 && update.getSpeedBoostMs <= 1800)
    wolf.setSpeedBoostUntil(0)
    assertEquals(0, m.instance.stateUpdate(wolf).getSpeedBoostMs)
  }

  @Test def axeSpinSlowsEveryoneInItAndSpinsTheBerserkerOn(): Unit = {
    val berserker = m.join(CharacterId.Berserker, 10, 30)
    val near = m.join(CharacterId.Soldier, 13, 30)
    val alsoNear = m.join(CharacterId.Soldier, 10, 34)
    val far = m.join(CharacterId.Soldier, 18, 30)
    slam(berserker, ProjectileType.AXE_SPIN)
    for (p <- Seq(near, alsoNear)) {
      assertTrue(p.isSlowed)
      assertEquals(0.5f, p.getSlowMultiplier, 1e-6f)
      assertEquals(p.getMaxHealth - slamDamage(ProjectileType.AXE_SPIN), p.getHealth)
    }
    assertFalse(far.isSlowed)
    assertEquals(far.getMaxHealth, far.getHealth)
    assertTrue(berserker.hasSpeedBoost)
  }

  @Test def combustSetsEveryoneNearAlightAndCarriesThePyromancerOff(): Unit = {
    val pyromancer = m.join(CharacterId.Pyromancer, 10, 30)
    val near = m.join(CharacterId.Gladiator, 12, 31)
    val far = m.join(CharacterId.Gladiator, 16, 30)
    slam(pyromancer, ProjectileType.COMBUST)
    assertTrue(near.isBurning)
    assertEquals(near.getMaxHealth - slamDamage(ProjectileType.COMBUST), near.getHealth)
    assertFalse(far.isBurning)
    assertTrue(pyromancer.hasSpeedBoost)
  }

  @Test def aGaleBlowsItsTargetBackAndPutsTheWindAtTheWindwalkersBack(): Unit = {
    val windwalker = m.join(CharacterId.Windwalker, 10, 30)
    val target = m.join(CharacterId.Gladiator, 13, 30)
    shoot(windwalker, ProjectileType.GALE)
    assertEquals((18, 30), m.at(target))
    assertEquals(target.getMaxHealth - hit(ProjectileType.GALE), target.getHealth)
    assertTrue(windwalker.hasSpeedBoost)
    assertTrue(windwalker.getSpeedBoostUntil - now <= 1500)
  }

  // --- Slams ---

  @Test def repulseThrowsEveryoneNearStraightBack(): Unit = {
    val graviton = m.join(CharacterId.Graviton, 20, 30)
    val east = m.join(CharacterId.Gladiator, 22, 30)
    val south = m.join(CharacterId.Gladiator, 20, 33)
    slam(graviton, ProjectileType.REPULSE)
    assertEquals((26, 30), m.at(east))
    assertEquals((20, 37), m.at(south))
    assertEquals(1, east.getKnockbacks)
    assertEquals(east.getMaxHealth - slamDamage(ProjectileType.REPULSE), east.getHealth)
  }

  @Test def electricBurstStunsEveryoneNear(): Unit = {
    val jellyfish = m.join(CharacterId.Jellyfish, 20, 30)
    val near = m.join(CharacterId.Gladiator, 23, 31)
    val far = m.join(CharacterId.Gladiator, 26, 30)
    slam(jellyfish, ProjectileType.SHOCK_BURST)
    assertTrue(near.isStunned)
    assertFalse(far.isStunned)
  }

  @Test def frostNovaSlowsEveryoneNearToACrawl(): Unit = {
    val cryomancer = m.join(CharacterId.Cryomancer, 20, 30)
    val near = m.join(CharacterId.Gladiator, 24, 30)
    slam(cryomancer, ProjectileType.FROST_NOVA)
    assertTrue(near.isSlowed)
    assertEquals(0.35f, near.getSlowMultiplier, 1e-6f)
    assertFalse("a slow, not a hold", near.isFrozen || near.isRooted)
  }

  @Test def maulAndBloodFrenzyStrikeEveryoneInReach(): Unit = {
    val bear = m.join(CharacterId.Bear, 10, 30)
    val clawed = m.join(CharacterId.Soldier, 12, 32)
    slam(bear, ProjectileType.MAUL)
    assertEquals(clawed.getMaxHealth - slamDamage(ProjectileType.MAUL), clawed.getHealth)

    val fenrir = m.join(CharacterId.Fenrir, 40, 30)
    val bitten = m.join(CharacterId.Soldier, 42, 30)
    fenrir.setHealth(fenrir.getMaxHealth - 50)
    slam(fenrir, ProjectileType.BLOOD_FRENZY)
    val frenzy = slamDamage(ProjectileType.BLOOD_FRENZY)
    assertEquals(bitten.getMaxHealth - frenzy, bitten.getHealth)
    assertTrue(bitten.isSlowed)
    assertEquals("healed by 30% of what it dealt", fenrir.getMaxHealth - 50 + frenzy * 30 / 100, fenrir.getHealth)
  }

  // --- Splashes ---
  // Each bursts where it reaches whoever it strikes: two cells (its hit radius) short of them

  @Test def divineJudgmentStunsTheOneItStrikesAndEveryoneNearThem(): Unit = {
    val cleric = m.join(CharacterId.Cleric, 10, 30)
    val struck = m.join(CharacterId.Gladiator, 15, 30)
    val beside = m.join(CharacterId.Gladiator, 15, 32)
    shoot(cleric, ProjectileType.DIVINE_JUDGMENT)
    assertTrue(struck.isStunned)
    assertTrue(beside.isStunned)
    assertEquals("the one it strikes takes the hit", struck.getMaxHealth - hit(ProjectileType.DIVINE_JUDGMENT), struck.getHealth)
    assertEquals("and those beside them the splash", beside.getMaxHealth - splashDamage(ProjectileType.DIVINE_JUDGMENT),
      beside.getHealth)
  }

  @Test def aHailstormFreezesEveryoneItBurstsOver(): Unit = {
    val cloudrunner = m.join(CharacterId.Cloudrunner, 10, 30)
    val struck = m.join(CharacterId.Gladiator, 15, 30)
    val beside = m.join(CharacterId.Gladiator, 14, 32)
    shoot(cloudrunner, ProjectileType.HAILSTORM)
    assertTrue(struck.isFrozen)
    assertTrue(beside.isFrozen)
  }

  @Test def aMeteorKnocksEveryoneNearItsImpactBack(): Unit = {
    val astronomer = m.join(CharacterId.Astronomer, 10, 30)
    val struck = m.join(CharacterId.Gladiator, 16, 30)
    val beside = m.join(CharacterId.Gladiator, 16, 32)
    shoot(astronomer, ProjectileType.METEOR)
    assertEquals("straight back along the throw", 30, m.at(struck)._2)
    assertTrue(s"struck at ${m.at(struck)}", m.at(struck)._1 >= 18)
    assertTrue(s"and beside it, out from the impact: ${m.at(beside)}", m.at(beside)._2 >= 33)
    assertEquals(1, beside.getKnockbacks)
  }

  @Test def aWhirlwindDragsEveryoneNearItsTargetInAndSlowsTheTarget(): Unit = {
    val djinn = m.join(CharacterId.Djinn, 10, 30)
    val struck = m.join(CharacterId.Gladiator, 16, 30)
    val beside = m.join(CharacterId.Gladiator, 16, 33)
    shoot(djinn, ProjectileType.WHIRLWIND)
    assertTrue(struck.isSlowed)
    assertTrue(s"dragged in: ${m.at(beside)}", m.at(beside)._2 < 33)
  }

  // --- Through, and off the walls ---

  @Test def shacklesRootTheFirstTwoThroughAndNoMore(): Unit = {
    val inquisitor = m.join(CharacterId.Inquisitor, 10, 30)
    val first = m.join(CharacterId.Gladiator, 13, 30)
    val second = m.join(CharacterId.Gladiator, 17, 30)
    val third = m.join(CharacterId.Gladiator, 21, 30)
    shoot(inquisitor, ProjectileType.SHACKLES)
    assertTrue(first.isRooted)
    assertTrue(second.isRooted)
    assertFalse(third.isRooted)
  }

  @Test def aRiddleIsHeardThroughAWall(): Unit = {
    val world = WorldData.createEmpty(60, 60)
    for (y <- 25 to 35) world.setTile(14, y, Tile.Wall)
    val walled = new TestMatch(world)
    val sphinx = walled.join(CharacterId.Sphinx, 10, 30)
    val behind = walled.join(CharacterId.Gladiator, 17, 30)
    walled.tickUntilGone(walled.launch(sphinx, ProjectileType.RIDDLE, 1f, 0f))
    assertTrue(behind.isRooted)
  }

  @Test def shadowBindAndWildCardBounceOffAWallOntoWhoeverIsRoundIt(): Unit = {
    // Fired east into a wall, a bounce turns them north, onto someone standing out of the line of fire
    for ((who, pType, held) <- Seq[(CharacterId, Byte, Player => Boolean)](
           (CharacterId.Shade, ProjectileType.SHADOW_BIND, _.isRooted),
           (CharacterId.Gambler, ProjectileType.WILD_CARD, _.isStunned))) {
      val world = WorldData.createEmpty(60, 60)
      for (y <- 10 to 50) world.setTile(20, y, Tile.Wall)
      val walled = new TestMatch(world)
      val thrower = walled.join(who, 10, 30)
      val round = walled.join(CharacterId.Gladiator, 19, 25)
      walled.tickUntilGone(walled.launch(thrower, pType, 1f, 0f))
      assertTrue(s"$who's bounced off the wall onto them", held(round))
    }
  }

  // --- Traps and a wall of ice ---

  @Test def aBindingRuneRootsWhoeverCrossesIt(): Unit = {
    val runesmith = m.join(CharacterId.Runesmith, 10, 30)
    val walker = m.join(CharacterId.Gladiator, 20, 30)
    assertNotNull(m.armedTrap(runesmith, 21, 30, TrapType.BINDING_RUNE))
    assertTrue(m.move(walker, 21, 30))
    assertTrue(walker.isRooted)
    assertEquals(walker.getMaxHealth - TrapDef.get(TrapType.BINDING_RUNE).damage, walker.getHealth)
  }

  @Test def theSpidersWebTrapIsASnareItLays(): Unit = {
    val spider = m.join(CharacterId.Spider, 10, 30)
    assertNotNull("laid from E", m.placeTrap(spider, AttackSlot.E, TrapType.SNARE, 13, 30))
    val runesmith = m.join(CharacterId.Runesmith, 30, 30)
    assertNotNull("and the Runesmith's from Q", m.placeTrap(runesmith, AttackSlot.Q, TrapType.BINDING_RUNE, 33, 30))
  }

  @Test def glacierRaisesAWallOfIce(): Unit = {
    val glacier = m.join(CharacterId.Glacier, 10, 30)
    assertTrue(m.move(glacier, 10, 30, flags2 = 0x04))
    assertTrue(glacier.hasBarrier)
  }
}
