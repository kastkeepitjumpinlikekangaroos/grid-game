package com.gridgame.server

import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

/**
 * What a hit does besides damage: knockbacks, pulls, freezes and the rest. Anything that moves
 * or holds a player is a server move: the player is told over TCP with the new count, and steps
 * their client sent before hearing of it are dropped rather than dragging them back.
 */
class OnHitEffectsTest {
  private val m = new TestMatch()

  /** Fly a projectile from `from` along +x until it is gone. */
  private def shoot(owner: Player, pType: Byte, from: (Int, Int) = null): Unit =
    m.tickUntilGone(m.launch(owner, pType, 1f, 0f, from))

  /** The server moves the player was told of over TCP. */
  private def movesToldOverTcp(p: Player): Seq[PlayerUpdatePacket] =
    m.updatesAbout(m.tcpSent(p), p)

  // --- Knockback ---

  @Test def aPushKnocksTheTargetStraightBack(): Unit = {
    val tidecaller = m.join(CharacterId.Tidecaller, 10, 30)
    val target = m.join(CharacterId.Gladiator, 15, 30)
    shoot(tidecaller, ProjectileType.TIDAL_WAVE)
    assertEquals((18, 30), m.at(target))
  }

  @Test def aPushStopsAtAWall(): Unit = {
    // It used to take the farthest open cell along the line, and came out beyond the wall
    val tidecaller = m.join(CharacterId.Tidecaller, 10, 30)
    val target = m.join(CharacterId.Gladiator, 15, 30)
    m.world.setTile(17, 30, Tile.Wall)
    shoot(tidecaller, ProjectileType.TIDAL_WAVE)
    assertEquals((16, 30), m.at(target))
  }

  @Test def aPushedPlayerIsToldWhereTheyWent(): Unit = {
    val tidecaller = m.join(CharacterId.Tidecaller, 10, 30)
    val target = m.join(CharacterId.Gladiator, 15, 30)
    m.clearSent()
    shoot(tidecaller, ProjectileType.TIDAL_WAVE)
    assertEquals(1, target.getServerMoves)
    val told = movesToldOverTcp(target)
    assertEquals(1, told.size)
    assertEquals(new Position(18, 30), told.head.getPosition)
    assertEquals(1, told.head.getServerMoves)
  }

  @Test def aPushIsNotUndoneByAStepSentBeforeIt(): Unit = {
    // The target was walking toward the Tidecaller: its client's next step, sent before it heard
    // of the push, lands after it. That used to be applied and drag the target back.
    val tidecaller = m.join(CharacterId.Tidecaller, 10, 30)
    val target = m.join(CharacterId.Gladiator, 15, 30)
    shoot(tidecaller, ProjectileType.TIDAL_WAVE)
    assertFalse("sent before the push", m.move(target, 14, 30, serverMoves = 0))
    assertEquals((18, 30), m.at(target))
    assertTrue("sent after hearing of it", m.move(target, 19, 30))
    assertEquals((19, 30), m.at(target))
  }

  // --- Pulls ---

  @Test def aGrabPullsTheTargetToTheOwner(): Unit = {
    val grabber = m.join(CharacterId.Gorilla, 10, 30)
    val target = m.join(CharacterId.Gladiator, 20, 30)
    shoot(grabber, ProjectileType.GRAB)
    assertEquals((10, 30), m.at(target))
    assertEquals(1, target.getServerMoves)
  }

  @Test def aGravityWellPullsNearbyPlayersTowardWhereItHit(): Unit = {
    val caster = m.join(CharacterId.Graviton, 10, 30)
    val hit = m.join(CharacterId.Gladiator, 15, 30)
    val beside = m.join(CharacterId.Gladiator, 15, 33)
    shoot(caster, ProjectileType.GRAVITY_WELL)
    assertTrue("pulled toward the blast", m.at(beside)._2 < 33)
    assertEquals(15, m.at(hit)._1, 1)
  }

  @Test def aVortexBombPullsInWhatItsBlastCatches(): Unit = {
    // Three characters' "pulls enemies in" ability: it passes through players and bursts at the
    // end of its flight, and the burst skipped the pull, so it never pulled anyone
    val caster = m.join(CharacterId.Graviton, 10, 30)
    val caught = m.join(CharacterId.Gladiator, 28, 30)
    shoot(caster, ProjectileType.VORTEX_BOMB) // bursts around x = 25
    assertTrue(s"pulled in from x = 28 to ${m.at(caught)._1}", m.at(caught)._1 < 28)
    assertTrue("and hurt", caught.getHealth < caught.getMaxHealth)
  }

  @Test def aPullStopsAtAWall(): Unit = {
    val caster = m.join(CharacterId.Graviton, 10, 30)
    val caught = m.join(CharacterId.Gladiator, 28, 30)
    m.world.setTile(27, 30, Tile.Wall)
    shoot(caster, ProjectileType.VORTEX_BOMB)
    assertEquals((28, 30), m.at(caught))
  }

  // --- Holds ---

  @Test def aFreezeHoldsThePlayerWhereTheServerHasThem(): Unit = {
    val spaceman = m.join(CharacterId.Spaceman, 10, 30)
    val target = m.join(CharacterId.Gladiator, 15, 30)
    m.clearSent()
    shoot(spaceman, ProjectileType.ICE_BEAM)
    assertTrue(target.isFrozen)
    // Its client may have stepped on before it heard: it goes back to where it was frozen
    assertEquals(1, target.getServerMoves)
    assertEquals(new Position(15, 30), movesToldOverTcp(target).last.getPosition)
    // Frozen, its steps are taken but go nowhere
    assertTrue(m.move(target, 16, 30))
    assertEquals((15, 30), m.at(target))
  }

  @Test def aFreezeCannotBeRenewedWhileTheImmunityLasts(): Unit = {
    val spaceman = m.join(CharacterId.Spaceman, 10, 30)
    val target = m.join(CharacterId.Gladiator, 15, 30)
    shoot(spaceman, ProjectileType.ICE_BEAM)
    val frozenUntil = target.getFrozenUntil
    target.setFrozenUntil(0) // as if it had worn off
    shoot(spaceman, ProjectileType.ICE_BEAM)
    assertFalse("immune", target.isFrozen)
    assertTrue(frozenUntil > 0)
  }

  @Test def aSlowLands(): Unit = {
    val ranger = m.join(CharacterId.Ranger, 10, 30)
    val target = m.join(CharacterId.Gladiator, 15, 30)
    shoot(ranger, ProjectileType.POISON_ARROW)
    assertTrue(target.isSlowed)
    assertEquals("a slow doesn't hold anyone", 0, target.getServerMoves)
  }

  // --- The shooter ---

  @Test def aLifeStealHealsTheShooter(): Unit = {
    val vampire = m.join(CharacterId.Vampire, 10, 30)
    val target = m.join(CharacterId.Gladiator, 15, 30)
    vampire.setHealth(50)
    shoot(vampire, ProjectileType.LEECH_BOLT)
    val damage = ProjectileDef.get(ProjectileType.LEECH_BOLT).damage
    assertEquals(50 + damage * 30 / 100, vampire.getHealth)
    assertEquals(target.getMaxHealth - damage, target.getHealth)
  }

  @Test def aHauntPutsTheOwnerBehindTheTarget(): Unit = {
    val wraith = m.join(CharacterId.Wraith, 10, 30)
    val target = m.join(CharacterId.Gladiator, 15, 30) // facing down: behind is up (y - 2)
    m.clearSent()
    shoot(wraith, ProjectileType.SHADOW_HAUNT)
    assertEquals((15, 28), m.at(wraith))
    assertTrue(target.isFrozen)
    assertEquals("the owner was moved by the server", 1, wraith.getServerMoves)
    assertEquals(new Position(15, 28), movesToldOverTcp(wraith).last.getPosition)
    // The Wraith's client, which walks on from where it landed, isn't held back for half a
    // second: that used to swallow every step and then refuse them all as too far
    assertTrue(m.move(wraith, 16, 28))
    assertTrue(m.move(wraith, 17, 28))
    assertEquals((17, 28), m.at(wraith))
  }

  @Test def aBurnRemembersWhoLitIt(): Unit = {
    val pyro = m.join(CharacterId.Pyromancer, 10, 30)
    val target = m.join(CharacterId.Gladiator, 15, 30)
    shoot(pyro, ProjectileType.FLAME_BOLT)
    assertTrue(target.isBurning)
    assertEquals(pyro.getId, target.getBurnOwnerId)
  }

  // --- Stuns ---

  @Test def aStunHoldsThePlayerAndRefusesTheirShots(): Unit = {
    val shooter = m.join(CharacterId.Spaceman, 10, 30)
    val target = m.join(CharacterId.Gladiator, 15, 30)
    m.clearSent()
    shoot(shooter, TestEffects.StunBolt)
    assertTrue("stunned", target.isStunned)
    assertTrue("and held, like any freeze", target.isFrozen)
    assertEquals(1, target.getServerMoves)
    assertEquals(new Position(15, 30), movesToldOverTcp(target).last.getPosition)
    // Held: their steps are taken but go nowhere, and they can't fire
    assertTrue(m.move(target, 16, 30))
    assertEquals((15, 30), m.at(target))
    val primary = CharacterDef.get(target.getCharacterId).primaryProjectileType
    assertTrue("no shot while stunned", m.fire(target, AttackSlot.PRIMARY, primary, Seq((1f, 0f))).isEmpty)
  }

  @Test def aStunReachesTheirScreenAsAStunAndNotAsIce(): Unit = {
    val shooter = m.join(CharacterId.Spaceman, 10, 30)
    val target = m.join(CharacterId.Gladiator, 15, 30)
    m.clearSent()
    shoot(shooter, TestEffects.StunBolt)
    val told = movesToldOverTcp(target).last
    assertEquals("frozen: every gate that already reads this keeps working", 0x04, told.getEffectFlags & 0x04)
    assertEquals("and stunned, so the client draws stars rather than frost", 0x01, told.getEffectFlags2 & 0x01)
  }

  @Test def aStunCannotBeRenewedWhileTheImmunityLasts(): Unit = {
    val shooter = m.join(CharacterId.Spaceman, 10, 30)
    val target = m.join(CharacterId.Gladiator, 15, 30)
    shoot(shooter, TestEffects.StunBolt)
    assertTrue(target.isStunned)
    target.setFrozenUntil(0) // as if it had worn off
    target.setStunnedUntil(0)
    shoot(shooter, TestEffects.StunBolt)
    assertFalse("immune", target.isStunned)
    assertFalse(target.isFrozen)
  }

  @Test def aSplashStunsWhatItCatches(): Unit = {
    // AoESplashConfig.stunDurationMs, the way freezes and roots already travel with a splash
    val caster = m.join(CharacterId.Earthshaker, 10, 30)
    val target = m.join(CharacterId.Gladiator, 13, 30)
    m.tickUntilGone(m.launch(caster, TestEffects.StunSplashSlam, 0f, 0f))
    assertTrue("stunned by the splash", target.isStunned)
    assertEquals("and held where the server has them", 1, target.getServerMoves)
  }

  @Test def aBlastCarriesItsStun(): Unit = {
    // The same, from the on-hit effect rather than the splash config
    val caster = m.join(CharacterId.Earthshaker, 10, 30)
    val target = m.join(CharacterId.Gladiator, 13, 30)
    m.tickUntilGone(m.launch(caster, TestEffects.StunSlam, 0f, 0f))
    assertTrue(target.isStunned)
  }

  // --- Poison ---

  @Test def aPoisonLandsAndRemembersWhoCastIt(): Unit = {
    val shooter = m.join(CharacterId.PlagueDoctor, 10, 30)
    val target = m.join(CharacterId.Gladiator, 15, 30)
    shoot(shooter, TestEffects.PoisonBolt)
    assertTrue(target.isPoisoned)
    assertEquals(shooter.getId, target.getPoisonOwnerId)
    assertEquals("20 over four ticks", 5, target.getPoisonDamagePerTick)
  }

  /** Tick the match, on the real clock, until the player's poison has run its course. */
  private def tickOutPoison(p: Player): Unit = {
    val deadline = System.currentTimeMillis() + 3000
    while (p.isPoisoned && System.currentTimeMillis() < deadline) {
      Thread.sleep(25)
      m.instance.tickPlayers()
    }
    assertFalse("the poison ran its course", p.isPoisoned)
  }

  @Test def aPoisonTicksToItsTotalAndThenStops(): Unit = {
    // On the real clock. Timed out the way a burn is, each bite landed up to a server tick late,
    // the last one fell after the deadline, and 20 over four ticks came to 15.
    val poisoner = m.join(CharacterId.PlagueDoctor, 10, 30)
    val target = m.join(CharacterId.Gladiator, 15, 30)
    val full = target.getHealth
    target.applyPoison(20, 400, 100, poisoner.getId)
    tickOutPoison(target)
    assertEquals("the whole 20", full - 20, target.getHealth)
    Thread.sleep(150)
    m.instance.tickPlayers()
    assertEquals("and no more", full - 20, target.getHealth)
  }

  @Test def aPoisonsLastTickTellsEveryoneItIsOver(): Unit = {
    // Nothing else would: the client only stops drawing it when an update says so
    val poisoner = m.join(CharacterId.PlagueDoctor, 10, 30)
    val target = m.join(CharacterId.Gladiator, 15, 30)
    target.applyPoison(10, 200, 100, poisoner.getId)
    m.clearSent()
    tickOutPoison(target)
    val told = m.updatesAbout(m.udpSent(poisoner), target)
    assertEquals("one update a tick", 2, told.size)
    assertEquals("poisoned after the first", 0x02, told.head.getEffectFlags2 & 0x02)
    assertEquals("and over after the last", 0, told.last.getEffectFlags2 & 0x02)
  }

  @Test def nobodyRegeneratesWhilePoisoned(): Unit = {
    // Between its ticks as well as on them. A burn only holds regen off on the ticks it bites,
    // so a poison built the same way healed its victim back up between them.
    val poisoner = m.join(CharacterId.PlagueDoctor, 10, 30)
    val target = m.join(CharacterId.Gladiator, 15, 30)
    target.setHealth(50)
    target.applyPoison(10, 5000, 2500, poisoner.getId) // nothing due for 2.5s
    for (_ <- 0 until 25) m.instance.tickPlayers() // 5s of 200ms ticks
    assertEquals("not a point back", 50, target.getHealth)
    target.clearPoison()
    for (_ <- 0 until 25) m.instance.tickPlayers()
    assertTrue("and regen resumes once it is gone", target.getHealth > 50)
  }

  @Test def aPoisonAndABurnRunAtOnce(): Unit = {
    // A DoT slot of its own: sharing burn's, the second one applied wiped the first out
    val poisoner = m.join(CharacterId.PlagueDoctor, 10, 30)
    val target = m.join(CharacterId.Gladiator, 15, 30)
    val full = target.getHealth
    target.applyBurn(20, 400, 100, poisoner.getId)   // 5 a tick
    target.applyPoison(40, 400, 100, poisoner.getId) // 10 a tick
    assertTrue(target.isBurning)
    assertTrue(target.isPoisoned)
    Thread.sleep(120)
    m.instance.tickPlayers()
    assertEquals("both bit", full - 15, target.getHealth)
  }

  @Test def aPoisonCreditsTheKillToWhoeverCastIt(): Unit = {
    val poisoner = m.join(CharacterId.PlagueDoctor, 10, 30)
    val target = m.join(CharacterId.Gladiator, 15, 30)
    target.setHealth(4)
    target.applyPoison(20, 400, 100, poisoner.getId)
    Thread.sleep(120)
    m.instance.tickPlayers()
    assertTrue("killed by the poison", target.isDead)
    assertEquals(1, m.instance.killTracker.getKills(poisoner.getId))
    assertEquals(1, m.instance.killTracker.getDeaths(target.getId))
  }

  // --- How hard a slow is ---

  @Test def aSlowTellsClientsHowHardItIs(): Unit = {
    // Only "slowed" used to cross the wire, so every client stepped at half pace whatever the
    // slow's def said. The Charm is a Slow(3000, 0.3f): 30% of their pace.
    val enchantress = m.join(CharacterId.Enchantress, 10, 30)
    val target = m.join(CharacterId.Gladiator, 15, 30)
    m.clearSent()
    shoot(enchantress, ProjectileType.CHARM)
    assertTrue(target.isSlowed)
    val told = m.updatesAbout(m.udpSent(target), target).last
    assertEquals("slowed", 0x80, told.getEffectFlags & 0x80)
    assertEquals("to 30%", 30, told.getSlowPercent)
  }

  // --- What a blast carries ---

  @Test def aSlamThrowsWhatItCatchesOutwards(): Unit = {
    // Away from where the blast went off, not from the caster's aim: the caster is standing in it
    val caster = m.join(CharacterId.Earthshaker, 10, 30)
    val east = m.join(CharacterId.Gladiator, 12, 30)
    val north = m.join(CharacterId.Gladiator, 10, 27)
    m.tickUntilGone(m.launch(caster, TestEffects.PushSlam, 0f, 0f))
    assertEquals("thrown three cells east", (15, 30), m.at(east))
    assertEquals("and three cells north", (10, 24), m.at(north))
    assertEquals(1, east.getServerMoves)
  }

  @Test def aSlamCanDragWhatItCatchesIn(): Unit = {
    val caster = m.join(CharacterId.Earthshaker, 10, 30)
    val target = m.join(CharacterId.Gladiator, 13, 30)
    m.tickUntilGone(m.launch(caster, TestEffects.PullSlam, 0f, 0f))
    assertEquals("pulled onto the caster", (10, 30), m.at(target))
  }

  @Test def soulHarvestHealsTheNecromancer(): Unit = {
    // Its life steal was skipped for blasts, and the projectile's own damage is 0 anyway, so
    // the ability described as "healing from damage dealt" healed nothing at all
    val necro = m.join(CharacterId.Necromancer, 10, 30)
    val target = m.join(CharacterId.Gladiator, 13, 30)
    necro.setHealth(50)
    val splash = ProjectileDef.get(ProjectileType.SOUL_HARVEST).aoeOnMaxRange.get
    m.tickUntilGone(m.launch(necro, ProjectileType.SOUL_HARVEST, 0f, 0f))
    assertEquals("the blast hurt", target.getMaxHealth - splash.damage, target.getHealth)
    assertEquals("and healed 30% of it", 50 + splash.damage * 30 / 100, necro.getHealth)
  }

  @Test def soulHarvestHealsFromAVictimItKills(): Unit = {
    val necro = m.join(CharacterId.Necromancer, 10, 30)
    val target = m.join(CharacterId.Gladiator, 13, 30)
    necro.setHealth(50)
    target.setHealth(5)
    val splash = ProjectileDef.get(ProjectileType.SOUL_HARVEST).aoeOnMaxRange.get
    m.tickUntilGone(m.launch(necro, ProjectileType.SOUL_HARVEST, 0f, 0f))
    assertTrue(target.isDead)
    assertEquals(50 + splash.damage * 30 / 100, necro.getHealth)
  }

  @Test def overclockSpeedsTheCyborgUpWithNobodyNear(): Unit = {
    // A ring that does no damage and buffs on hit only ever caught someone standing in it,
    // which for an ability that is entirely a self-buff is never when it matters
    val cyborg = m.join(CharacterId.Cyborg, 10, 30)
    assertFalse(cyborg.hasSpeedBoost)
    m.launch(cyborg, ProjectileType.OVERCLOCK_BEAM, 0f, 0f)
    assertTrue("boosted on the cast", cyborg.hasSpeedBoost)
  }

  @Test def aThrownAttackThatBuffsOnHitStillOnlyBuffsOnAHit(): Unit = {
    // The self-buff on cast is for slams; a claw swipe has to connect
    val bear = m.join(CharacterId.Bear, 10, 30)
    m.launch(bear, ProjectileType.CLAW_SWIPE, 1f, 0f)
    assertFalse(bear.hasSpeedBoost)
  }
}

/**
 * Projectiles that exist only here. Nothing in the roster stuns or poisons yet (the kits come in
 * a later pass), and no slam pushes or pulls, so the engine's handling of those is pinned with
 * definitions of their own on ids the game does not use.
 */
private object TestEffects {
  val StunBolt: Byte = -2
  val PoisonBolt: Byte = -3
  val PushSlam: Byte = -4
  val PullSlam: Byte = -5
  val StunSlam: Byte = -6
  val StunSplashSlam: Byte = -7

  /** A ground slam: no speed, no range, everything it does happens in the splash where it lands. */
  private def slam(id: Byte, name: String, splash: AoESplashConfig, effect: Option[OnHitEffect]) =
    ProjectileDef(id = id, name = name, speedMultiplier = 0f, damage = 0, maxRange = 0,
      aoeOnMaxRange = Some(splash), onHitEffect = effect,
      explosionConfig = Some(ExplosionConfig(0, 0, splash.radius)))

  ProjectileDef.register(
    ProjectileDef(id = StunBolt, name = "Test Stun Bolt", speedMultiplier = 0.9f, damage = 5,
      maxRange = 20, onHitEffect = Some(Stun(1200))),
    ProjectileDef(id = PoisonBolt, name = "Test Poison Bolt", speedMultiplier = 0.9f, damage = 5,
      maxRange = 20, onHitEffect = Some(Poison(20, 400, 100))),
    slam(PushSlam, "Test Push Slam", AoESplashConfig(6.0f, 10), Some(Push(3.0f))),
    slam(PullSlam, "Test Pull Slam", AoESplashConfig(6.0f, 10), Some(PullToOwner)),
    slam(StunSlam, "Test Stun Slam", AoESplashConfig(6.0f, 10), Some(Stun(1200))),
    slam(StunSplashSlam, "Test Stun Splash", AoESplashConfig(6.0f, 10, stunDurationMs = 1200), None)
  )
}
