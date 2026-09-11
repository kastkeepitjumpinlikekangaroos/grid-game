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
}
