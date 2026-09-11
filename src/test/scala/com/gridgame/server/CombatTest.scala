package com.gridgame.server

import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

/**
 * Shots, blasts and slams as the server resolves them, tick by tick, and what it tells the
 * players about them.
 */
class CombatTest {
  private val m = new TestMatch()

  private def hitsOn(packets: Seq[Packet]): Seq[(java.util.UUID, Byte)] =
    packets.collect {
      case pp: ProjectilePacket if pp.getAction == ProjectileAction.HIT || pp.getAction == ProjectileAction.PIERCE =>
        (pp.getTargetId, pp.getAction)
    }

  private def kills(packets: Seq[Packet]): Seq[GameEventPacket] =
    packets.collect { case e: GameEventPacket if e.getEventType == GameEvent.KILL => e }

  // --- Range and terrain ---

  @Test def aShotStopsAtTheEndOfItsRange(): Unit = {
    val shooter = m.join(CharacterId.Soldier, 5, 30)
    val bullet = m.launch(shooter, ProjectileType.BULLET, 1f, 0f) // starts a cell ahead, at x = 6
    m.clearSent()
    m.tickUntilGone(bullet)
    val despawn = m.projectileEvents(m.udpSent(shooter), ProjectileAction.DESPAWN)
    assertEquals(1, despawn.size)
    assertEquals(6f + ProjectileDef.get(ProjectileType.BULLET).maxRange, despawn.head.getX, 0.51f)
  }

  @Test def aShotStopsAtAWall(): Unit = {
    val shooter = m.join(CharacterId.Soldier, 5, 30)
    m.world.setTile(15, 30, Tile.Wall)
    val bullet = m.launch(shooter, ProjectileType.BULLET, 1f, 0f)
    m.clearSent()
    m.tickUntilGone(bullet)
    val despawn = m.projectileEvents(m.udpSent(shooter), ProjectileAction.DESPAWN)
    assertEquals(1, despawn.size)
    assertEquals("stopped in the wall's cell", 15, Math.floor(despawn.head.getX + 0.5f).toInt)
  }

  @Test def aBoomerangComesBack(): Unit = {
    val gladiator = m.join(CharacterId.Gladiator, 10, 30)
    val axe = m.launch(gladiator, ProjectileType.AXE, 1f, 0f)
    m.clearSent()
    m.tickUntilGone(axe)
    val moves = m.projectileEvents(m.udpSent(gladiator), ProjectileAction.MOVE).filter(_.getProjectileId == axe.id)
    assertTrue("goes out", moves.exists(_.getDx > 0))
    assertTrue("comes back", moves.exists(_.getDx < 0))
    assertTrue("out first", moves.head.getDx > 0 && moves.last.getDx < 0)
  }

  // --- Pierce ---

  @Test def aPiercingShotPassesThroughOnePlayerAndStopsAtTheNext(): Unit = {
    // An arrow pierces 1. It used to stop at its first hit all the same.
    val ranger = m.join(CharacterId.Ranger, 10, 30)
    val a = m.join(CharacterId.Gladiator, 14, 30)
    val b = m.join(CharacterId.Gladiator, 18, 30)
    val c = m.join(CharacterId.Gladiator, 22, 30)
    val full = a.getMaxHealth
    val arrow = m.launch(ranger, ProjectileType.ARROW, 1f, 0f)
    m.clearSent()
    m.tickUntilGone(arrow)
    val damage = ProjectileDef.get(ProjectileType.ARROW).damage
    assertEquals(full - damage, a.getHealth)
    assertEquals(full - damage, b.getHealth)
    assertEquals("the second hit used it up", full, c.getHealth)
    // Everyone is told the first hit was a pierce, so their client keeps drawing the arrow
    assertEquals(Seq((a.getId, ProjectileAction.PIERCE), (b.getId, ProjectileAction.HIT)), hitsOn(m.udpSent(c)))
  }

  @Test def aShotWithoutPierceStopsAtTheFirstPlayer(): Unit = {
    val soldier = m.join(CharacterId.Soldier, 10, 30)
    val a = m.join(CharacterId.Gladiator, 14, 30)
    val b = m.join(CharacterId.Gladiator, 18, 30)
    val bullet = m.launch(soldier, ProjectileType.BULLET, 1f, 0f)
    m.clearSent()
    m.tickUntilGone(bullet)
    assertTrue(a.getHealth < a.getMaxHealth)
    assertEquals(b.getMaxHealth, b.getHealth)
    assertEquals(Seq((a.getId, ProjectileAction.HIT)), hitsOn(m.udpSent(b)))
  }

  // --- Deaths ---

  @Test def aBodyDoesNotStopTheNextShot(): Unit = {
    // Two bullets reach one player in the same tick and the first kills: the second used to be
    // spent on the body. It flies on and hits the player behind.
    val x = m.join(CharacterId.Soldier, 10, 10)
    val y = m.join(CharacterId.Soldier, 10, 50)
    val victim = m.join(CharacterId.Gladiator, 20, 30)
    val behind = m.join(CharacterId.Gladiator, 25, 30)
    victim.setHealth(ProjectileDef.get(ProjectileType.BULLET).damage)
    // Both from (10, 30), so they arrive together
    val b1 = m.launch(x, ProjectileType.BULLET, 1f, 0f, from = (10, 30))
    val b2 = m.launch(y, ProjectileType.BULLET, 1f, 0f, from = (10, 30))
    m.clearSent()
    m.tickUntilGone(b1)
    m.tickUntilGone(b2)
    assertTrue(victim.isDead)
    assertEquals(behind.getMaxHealth - ProjectileDef.get(ProjectileType.BULLET).damage, behind.getHealth)
    assertEquals("one death", 1, m.instance.killTracker.getDeaths(victim.getId))
    assertEquals("one kill between them", 1,
      m.instance.killTracker.getKills(x.getId) + m.instance.killTracker.getKills(y.getId))
    assertEquals("one kill in the feed", 1, kills(m.tcpSent(behind)).size)
  }

  @Test def aBurnThatKillsScoresOnceForItsOwner(): Unit = {
    val pyro = m.join(CharacterId.Pyromancer, 5, 5)
    val victim = m.join(CharacterId.Gladiator, 30, 30)
    victim.setHealth(1)
    victim.applyBurn(totalDamage = 40, durationMs = 4000, tickMs = 100, ownerId = pyro.getId) // 1 a tick
    Thread.sleep(120)
    m.instance.tickPlayers()
    Thread.sleep(120)
    m.instance.tickPlayers()
    assertTrue(victim.isDead)
    assertEquals(1, m.instance.killTracker.getKills(pyro.getId))
    assertEquals(1, m.instance.killTracker.getDeaths(victim.getId))
  }

  @Test def theDeadDoNotRegenerate(): Unit = {
    val p = m.join(CharacterId.Gladiator, 30, 30)
    p.setHealth(0)
    for (_ <- 0 until 20) m.instance.tickPlayers()
    assertEquals(0, p.getHealth)
  }

  @Test def theLivingRegenerateTowardFullHealth(): Unit = {
    val p = m.join(CharacterId.Gladiator, 30, 30)
    p.setHealth(50)
    for (_ <- 0 until 25) m.instance.tickPlayers() // 5s of 200ms ticks
    assertTrue(s"healed to ${p.getHealth}", p.getHealth > 50 && p.getHealth < p.getMaxHealth)
  }

  // --- Blasts and slams: centred on where they happen ---

  @Test def aBlastIsCentredWhereItGoesOff(): Unit = {
    // A grenade flies 12 cells through players and goes off: from (11, 30), at (23, 30)
    val bomber = m.join(CharacterId.Soldier, 10, 30)
    val atCentre = m.join(CharacterId.Gladiator, 23, 30)
    val twoPast = m.join(CharacterId.Gladiator, 25, 30)
    val twoShort = m.join(CharacterId.Gladiator, 21, 30)
    val outside = m.join(CharacterId.Gladiator, 27, 30)
    val grenade = m.launch(bomber, ProjectileType.GRENADE, 1f, 0f)
    m.tickUntilGone(grenade)
    def lost(p: Player) = p.getMaxHealth - p.getHealth
    assertEquals("full damage at the centre", 40, lost(atCentre))
    assertTrue("less two cells out", lost(twoPast) < lost(atCentre) && lost(twoPast) > 0)
    // Measured from the corner of the victim's cell, the far side took 14 and the near side 24
    assertEquals("the same two cells out either side", lost(twoPast), lost(twoShort))
    assertEquals("none past the radius", 0, lost(outside))
  }

  @Test def aGroundSlamReachesAsFarOnEverySide(): Unit = {
    val caster = m.join(CharacterId.Shadowfiend, 30, 30)
    val radius = ProjectileDef.get(ProjectileType.TREMOR_SLAM).aoeOnMaxRange.get.radius.toInt // 6
    val inRange = Seq((30 + radius, 30), (30 - radius, 30), (30, 30 + radius), (30, 30 - radius))
      .map { case (x, y) => m.join(CharacterId.Gladiator, x, y) }
    val outOfRange = Seq((30 + radius + 1, 30), (30 - radius - 1, 30), (30, 30 + radius + 1), (30, 30 - radius - 1))
      .map { case (x, y) => m.join(CharacterId.Gladiator, x, y) }
    m.launch(caster, ProjectileType.TREMOR_SLAM, 0f, 0f)
    m.tick()
    inRange.foreach { p => assertTrue(s"at ${m.at(p)}", p.getHealth < p.getMaxHealth); assertTrue(p.isRooted) }
    outOfRange.foreach(p => assertEquals(s"at ${m.at(p)}", p.getMaxHealth, p.getHealth))
  }

  // --- Who can be hit ---

  @Test def shotsPassShieldedAndPhasedPlayers(): Unit = {
    val soldier = m.join(CharacterId.Soldier, 10, 30)
    val shielded = m.join(CharacterId.Gladiator, 14, 30)
    val phased = m.join(CharacterId.Gladiator, 18, 30)
    val open = m.join(CharacterId.Gladiator, 22, 30)
    shielded.setShieldUntil(System.currentTimeMillis() + 10000)
    phased.setPhasedUntil(System.currentTimeMillis() + 10000)
    m.tickUntilGone(m.launch(soldier, ProjectileType.BULLET, 1f, 0f))
    assertEquals(shielded.getMaxHealth, shielded.getHealth)
    assertEquals(phased.getMaxHealth, phased.getHealth)
    assertTrue(open.getHealth < open.getMaxHealth)
  }

  @Test def teammatesAreNeitherShotNorBlasted(): Unit = {
    val teams = new TestMatch(gameMode = 1)
    val shooter = teams.join(CharacterId.Soldier, 10, 30, team = 1)
    val mate = teams.join(CharacterId.Gladiator, 14, 30, team = 1)
    val enemy = teams.join(CharacterId.Gladiator, 18, 30, team = 2)
    teams.tickUntilGone(teams.launch(shooter, ProjectileType.BULLET, 1f, 0f))
    assertEquals(mate.getMaxHealth, mate.getHealth)
    assertTrue(enemy.getHealth < enemy.getMaxHealth)

    val mateAtBlast = teams.join(CharacterId.Gladiator, 23, 40, team = 1)
    val enemyAtBlast = teams.join(CharacterId.Gladiator, 23, 41, team = 2)
    teams.tickUntilGone(teams.launch(shooter, ProjectileType.GRENADE, 1f, 0f, from = (10, 40)))
    assertEquals(mateAtBlast.getMaxHealth, mateAtBlast.getHealth)
    assertTrue(enemyAtBlast.getHealth < enemyAtBlast.getMaxHealth)
  }

  // --- Speed and limits ---

  @Test def aGemBoostDoublesAShotsSpeed(): Unit = {
    val plain = m.join(CharacterId.Soldier, 10, 20)
    val boosted = m.join(CharacterId.Soldier, 10, 40)
    boosted.setGemBoostUntil(System.currentTimeMillis() + 10000)
    val a = m.launch(plain, ProjectileType.BULLET, 1f, 0f)
    val b = m.launch(boosted, ProjectileType.BULLET, 1f, 0f)
    m.tick()
    assertEquals(1f, a.getX - 11f, 1e-4f)
    assertEquals(2f, b.getX - 11f, 1e-4f)
  }

  @Test def aPlayerHasAtMostThirtyShotsInFlight(): Unit = {
    val p = m.join(CharacterId.Soldier, 30, 30)
    val spawned = (0 until 40).map(_ => m.instance.projectileManager.spawnProjectile(p.getId, 30, 30, 0.1f, 0f, 0, 0, ProjectileType.BULLET))
    assertEquals(30, spawned.count(_ != null))
  }

  // --- Burst shot: the primary in all eight directions ---

  /** A cast's projectiles must reach the server within 100ms of its first (PacketValidator's
    * cast window). The first spawn a JVM handles loads the classes on that path, which can take
    * longer than that, so the path is run once before a cast is timed. */
  private def warmUp(): Unit = {
    val other = m.join(CharacterId.Soldier, 50, 50)
    m.fire(other, AttackSlot.PRIMARY, CharacterDef.get(CharacterId.Soldier).primaryProjectileType, Seq((1f, 0f)))
  }

  @Test def aBurstShotFiresThePrimaryInAllEightDirections(): Unit = {
    warmUp()
    // It used to send four of the default bolt, refused from anyone but Spaceman
    val soldier = m.join(CharacterId.Soldier, 30, 30)
    val primary = CharacterDef.get(CharacterId.Soldier).primaryProjectileType
    val burst = m.fire(soldier, AttackSlot.BURST, primary, AttackSlot.BurstDirections :+ ((1f, 0f)))
    assertEquals("eight, and not a ninth", 8, burst.size)
    assertTrue(burst.forall(_.projectileType == primary))
  }

  @Test def aBurstShotKeepsItsOwnCooldown(): Unit = {
    warmUp()
    val soldier = m.join(CharacterId.Soldier, 30, 30)
    val primary = CharacterDef.get(CharacterId.Soldier).primaryProjectileType
    assertEquals(8, m.fire(soldier, AttackSlot.BURST, primary, AttackSlot.BurstDirections).size)
    Thread.sleep(150)
    assertEquals("not again straight away", 0, m.fire(soldier, AttackSlot.BURST, primary, AttackSlot.BurstDirections).size)
    assertEquals("the primary still fires", 1, m.fire(soldier, AttackSlot.PRIMARY, primary, Seq((1f, 0f))).size)
    val q = CharacterDef.get(CharacterId.Soldier).qAbility.projectileType
    if (q != primary) assertEquals("only the primary bursts", 0, m.fire(soldier, AttackSlot.BURST, q, Seq((1f, 0f))).size)
  }
}
