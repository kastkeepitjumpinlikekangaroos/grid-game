package com.gridgame.server

import com.gridgame.server.game.PacketValidator
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

  @Test def aShotFliesOverWater(): Unit = {
    val shooter = m.join(CharacterId.Soldier, 5, 30)
    val target = m.join(CharacterId.Gladiator, 16, 30)
    // A river down the map between them: shallows either side of deep water
    for (y <- 0 until m.world.height) {
      m.world.setTile(10, y, Tile.Water)
      m.world.setTile(11, y, Tile.DeepWater)
      m.world.setTile(12, y, Tile.Water)
    }
    val bullet = m.launch(shooter, ProjectileType.BULLET, 1f, 0f)
    m.clearSent()
    m.tickUntilGone(bullet)
    assertTrue("hit on the far bank", target.getHealth < target.getMaxHealth)
    assertEquals(Seq((target.getId, ProjectileAction.HIT)), hitsOn(m.udpSent(shooter)))
  }

  @Test def aBoomerangComesBack(): Unit = {
    val gladiator = m.join(CharacterId.Gladiator, 10, 30)
    val axe = m.launch(gladiator, ProjectileType.ARENA_AXE, 1f, 0f)
    m.clearSent()
    m.tickUntilGone(axe)
    val moves = m.projectileEvents(m.udpSent(gladiator), ProjectileAction.MOVE).filter(_.getProjectileId == axe.id)
    assertTrue("goes out", moves.exists(_.getDx > 0))
    assertTrue("comes back", moves.exists(_.getDx < 0))
    assertTrue("out first", moves.head.getDx > 0 && moves.last.getDx < 0)
  }

  /** Every type an attack in the roster throws. */
  private val thrown: Seq[Byte] = CharacterDef.all
    .flatMap(c => Seq(c.primaryProjectileType, c.qAbility.projectileType, c.eAbility.projectileType))
    .filter(_ >= 0).distinct

  @Test def aShotStopsAtAWallItsShooterStandsAgainst(): Unit = {
    // A shot starts a cell out from its shooter, and the sub-steps it flies in were only looked at
    // where each ended: from that first cell, a step of exactly half a cell (a speed of 0.5 or 1.0
    // a tick) put it straight into the next one. So fired east or south, fifteen of the roster's
    // types flew through a fence, or a wall a cell thick, that their shooter stood against — the
    // Sniper Beam for 29 cells — while the aim lane and the bots' line of fire had them stop there.
    val directions = Seq((1f, 0f), (0f, 1f), (-1f, 0f), (0f, -1f), (0.7071068f, 0.7071068f),
      (-0.7071068f, 0.7071068f), (0.7071068f, -0.7071068f), (-0.7071068f, -0.7071068f))
    val failures = scala.collection.mutable.Buffer[String]()
    for (t <- thrown; tile <- Seq(Tile.Fence, Tile.Wall)) {
      val d = ProjectileDef.get(t)
      val flies = d.speedMultiplier > 0f || d.chargeSpeedScaling.isDefined
      if (flies && (tile == Tile.Fence || !d.passesThroughWalls)) {
        val charges = if (d.chargeSpeedScaling.isDefined) Seq(0, 25, 50, 75, 100) else Seq(0)
        val m = new TestMatch()
        val shooter = m.join(CharacterId.Soldier, 30, 30)
        for ((dx, dy) <- directions; charge <- charges) {
          val (wx, wy) = (30 + Math.round(dx), 30 + Math.round(dy))
          m.world.setTile(wx, wy, tile)
          val shot = m.instance.projectileManager.spawnProjectile(shooter.getId, 30, 30, dx, dy,
            shooter.getColorRGB, charge, t)
          // How far into the wall's cell, along the shot's own line, it ever got: no further than
          // the cell's far edge (half a cell past it, along a row; to its far corner diagonally)
          val farEdge = if (dx != 0f && dy != 0f) 1.5f * Math.sqrt(2).toFloat else 1.5f
          var furthest = 0f
          var ticks = 0
          while (m.instance.projectileManager.getProjectile(shot.id) != null && ticks < 400) {
            m.tick()
            ticks += 1
            furthest = Math.max(furthest, (shot.getX - 30) * dx + (shot.getY - 30) * dy)
          }
          if (furthest > farEdge + 0.01f)
            failures += s"${d.name} (speed ${d.effectiveSpeed(charge)}) along ($dx, $dy) through a $tile: $furthest"
          m.world.setTile(wx, wy, Tile.Grass)
        }
      }
    }
    assertTrue(failures.mkString("\n"), failures.isEmpty)
  }

  @Test def nobodyIsHitThroughAWall(): Unit = {
    // A hit radius (1.8 to 3.5 cells) reaches past a wall right in front of a player. A shot drawn
    // stopping at a fence hit whoever stood behind it: from a cell behind, 26 of the roster's
    // 48 single-target shots did. Like a barrier (BarrierTest), a wall shelters whoever is behind it.
    val failures = scala.collection.mutable.Buffer[String]()
    for (t <- thrown; behind <- 1 to 2) {
      val d = ProjectileDef.get(t)
      if (d.speedMultiplier > 0f && !d.passesThroughWalls && !d.passesThroughPlayers && !d.isExplosive &&
          d.aoeOnHit.isEmpty && d.aoeOnMaxRange.isEmpty && d.maxRange >= 8) {
        val m = new TestMatch()
        val shooter = m.join(CharacterId.Soldier, 20, 30)
        for (y <- 0 until m.world.height) m.world.setTile(25, y, Tile.Fence)
        val sheltered = m.join(CharacterId.Gladiator, 25 + behind, 30)
        m.tickUntilGone(m.launch(shooter, t, 1f, 0f))
        if (sheltered.getHealth < sheltered.getMaxHealth) failures += s"${d.name} from $behind behind the fence"
      }
    }
    assertTrue(failures.mkString("\n"), failures.isEmpty)
  }

  @Test def aWallDoesNotShelterAnyoneFromWhatFliesThroughIt(): Unit = {
    // The Reaper's scythe passes through walls, and its reach (2.8) with it
    val reaper = m.join(CharacterId.Reaper, 20, 30)
    for (y <- 0 until m.world.height) m.world.setTile(25, y, Tile.Wall)
    val behind = m.join(CharacterId.Gladiator, 26, 30)
    m.tickUntilGone(m.launch(reaper, ProjectileType.REAP, 1f, 0f))
    assertTrue(behind.getHealth < behind.getMaxHealth)
  }

  @Test def someoneStandingInAFencePutDownOnThemIsStillHit(): Unit = {
    // A fence can go down on an occupied cell. Its own cell is no shelter to whoever stands in it,
    // or a fence dropped on yourself would make you unhittable
    val shooter = m.join(CharacterId.Soldier, 20, 30)
    val boxedIn = m.join(CharacterId.Gladiator, 26, 30)
    m.world.setTile(26, 30, Tile.Fence)
    m.tickUntilGone(m.launch(shooter, ProjectileType.BULLET, 1f, 0f))
    assertTrue(boxedIn.getHealth < boxedIn.getMaxHealth)
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

  @Test def aShotStrikesTheNearestOfThoseItReaches(): Unit = {
    // A hit radius takes in two players one behind the other the moment a shot leaves its shooter.
    // It struck whichever the grid listed last: fired east, the one in front was passed through
    // untouched and the one behind them hit
    for ((from, near, far, dx) <- Seq((5, 7, 8, 1f), (10, 8, 7, -1f), (20, 22, 23, 1f), (30, 28, 27, -1f))) {
      val m = new TestMatch()
      val shooter = m.join(CharacterId.Soldier, from, 30)
      val inFront = m.join(CharacterId.Gladiator, near, 30)
      val behind = m.join(CharacterId.Gladiator, far, 30)
      m.tickUntilGone(m.launch(shooter, ProjectileType.BULLET, dx, 0f))
      assertTrue(s"fired from $from: the one in front, at $near", inFront.getHealth < inFront.getMaxHealth)
      assertEquals(s"fired from $from: not the one behind, at $far", behind.getMaxHealth, behind.getHealth)
    }
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

  @Test def everyoneRegeneratesTheSameShareOfTheirHealth(): Unit = {
    // 2% of their own max health a second, whoever they are. It was 3.0 - (max - 70) * 0.04 a
    // second: the frailest healed fastest, and from 145 HP, where the melee band tops out,
    // nobody healed at all.
    val bruiser = m.join(CharacterId.Crusader, 20, 30)
    val caster = m.join(CharacterId.Wizard, 40, 30)
    assertTrue("the top of the band", bruiser.getMaxHealth >= 145)
    assertTrue("the bottom of it", caster.getMaxHealth <= 65)
    for (p <- Seq(bruiser, caster)) p.setHealth(p.getMaxHealth / 2)
    for (_ <- 0 until 51) m.instance.tickPlayers() // 10.2s of 200ms ticks
    for (p <- Seq(bruiser, caster)) {
      assertEquals(s"${p.getName} back a fifth of ${p.getMaxHealth}", p.getMaxHealth / 2 + p.getMaxHealth / 5,
        p.getHealth)
    }
  }

  @Test def regenStopsAtFullHealth(): Unit = {
    val p = m.join(CharacterId.Golem, 30, 30)
    p.setHealth(p.getMaxHealth - 1)
    for (_ <- 0 until 25) m.instance.tickPlayers()
    assertEquals(p.getMaxHealth, p.getHealth)
  }

  // --- Blasts and slams: centred on where they happen ---

  @Test def aBlastIsCentredWhereItGoesOff(): Unit = {
    // An eruption flies 16 cells through players and goes off: from (11, 30), at (27, 30)
    val caster = m.join(CharacterId.MagmaKnight, 10, 30)
    val atCentre = m.join(CharacterId.Gladiator, 27, 30)
    val twoPast = m.join(CharacterId.Gladiator, 29, 30)
    val twoShort = m.join(CharacterId.Gladiator, 25, 30)
    val outside = m.join(CharacterId.Gladiator, 31, 30)
    m.tickUntilGone(m.launch(caster, ProjectileType.ERUPTION, 1f, 0f))
    def lost(p: Player) = p.getMaxHealth - p.getHealth
    assertEquals("full damage at the centre",
      ProjectileDef.get(ProjectileType.ERUPTION).explosionConfig.get.centerDamage, lost(atCentre))
    assertTrue("less two cells out", lost(twoPast) < lost(atCentre) && lost(twoPast) > 0)
    // Measured from the corner of the victim's cell, the far side took less than the near side
    assertEquals("the same two cells out either side", lost(twoPast), lost(twoShort))
    assertEquals("none past the radius", 0, lost(outside))
  }

  @Test def aGrenadeGoesOffOnTheFirstEnemyItReaches(): Unit = {
    // It used to fly through players and go off only at the end of its 12 cells, here beside the
    // one behind: thrown at anyone nearer than about 9 cells, it burst out of reach behind them
    val bomber = m.join(CharacterId.Bombardier, 10, 30)
    val target = m.join(CharacterId.Gladiator, 16, 30)
    val behind = m.join(CharacterId.Gladiator, 23, 30)
    m.tickUntilGone(m.launch(bomber, ProjectileType.GRENADE, 1f, 0f))
    assertTrue("the target is caught", target.getHealth < target.getMaxHealth)
    assertEquals("nobody past it", behind.getMaxHealth, behind.getHealth)
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
