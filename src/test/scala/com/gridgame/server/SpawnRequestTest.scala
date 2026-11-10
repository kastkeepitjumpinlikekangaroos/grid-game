package com.gridgame.server

import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

/**
 * What the server takes from a client's request to fire, and what it doesn't. The attack and its
 * fire rate are AbilityCastValidationTest's; this is the rest of the request: the heading, which
 * only says which way (a projectile flies at its own speed), and who is allowed to fire at all.
 */
class SpawnRequestTest {
  private val m = new TestMatch()

  private def primary(p: Player): Byte = CharacterDef.get(p.getCharacterId).primaryProjectileType

  private def speed(pr: Projectile): Double = Math.hypot(pr.dx, pr.dy)

  // --- The heading ---

  @Test def aShotWithNoHeadingIsRefusedAndCostsNothing(): Unit = {
    // It used to fly at a speed of nothing: it never moved, so it never reached the end of its
    // range, and it sat where it was fired for the rest of the match, a mine hitting whoever
    // walked into it — thirty of them per player
    val soldier = m.join(CharacterId.Soldier, 20, 20)
    assertTrue(m.fire(soldier, AttackSlot.PRIMARY, primary(soldier), Seq((0f, 0f))).isEmpty)
    assertEquals("the next real shot isn't held back by it", 1,
      m.fire(soldier, AttackSlot.PRIMARY, primary(soldier), Seq((1f, 0f))).size)
  }

  @Test def aHeadingLongerThanAUnitFliesNoFaster(): Unit = {
    // A diagonal of (1, 1) passed the check (its length is under sqrt 2) and flew 41% faster
    val soldier = m.join(CharacterId.Soldier, 20, 20)
    val shot = m.fire(soldier, AttackSlot.PRIMARY, primary(soldier), Seq((1f, 1f)))
    assertEquals(1, shot.size)
    assertEquals(1.0, speed(shot.head), 1e-4)
    assertEquals("still that way", shot.head.dx, shot.head.dy, 1e-6f)
  }

  @Test def aHeadingShorterThanAUnitFliesNoSlower(): Unit = {
    val soldier = m.join(CharacterId.Soldier, 20, 20)
    val shot = m.fire(soldier, AttackSlot.PRIMARY, primary(soldier), Seq((0.6f, 0f)))
    assertEquals(1, shot.size)
    assertEquals(1.0, speed(shot.head), 1e-4)
  }

  @Test def everyAttackTheClientFiresKeepsItsHeading(): Unit = {
    // The client sends unit headings, and they are what the projectile flies along
    val soldier = m.join(CharacterId.Soldier, 20, 20)
    val headings = AttackSlot.BurstDirections
    val shots = m.fire(soldier, AttackSlot.BURST, primary(soldier), headings)
    assertEquals(8, shots.size)
    val flown = shots.map(s => (Math.round(Math.toDegrees(Math.atan2(s.dy, s.dx)) + 360) % 360).toInt).toSet
    assertEquals(headings.map { case (dx, dy) => (Math.round(Math.toDegrees(Math.atan2(dy, dx)) + 360) % 360).toInt }.toSet, flown)
    shots.foreach(s => assertEquals(1.0, speed(s), 1e-4))
  }

  // The Crusader's Shield Bash: a slam on E that hurts and pushes whoever is within five cells
  private def shieldBash(p: Player, heading: (Float, Float)): Seq[Projectile] = {
    assertTrue(CharacterDef.get(CharacterId.Crusader).eAbility.castBehavior.isInstanceOf[GroundSlam])
    m.fire(p, AttackSlot.E, ProjectileType.SHOCKWAVE, Seq(heading))
  }

  @Test def aSlamLandsOnItsCasterWhateverHeadingItIsSent(): Unit = {
    // The client sends none; a heading carried the slam a cell off its caster
    val p = m.join(CharacterId.Crusader, 20, 20)
    val slam = shieldBash(p, (1f, 0f))
    assertEquals(1, slam.size)
    assertEquals(20f, slam.head.getX, 0f)
    assertEquals(20f, slam.head.getY, 0f)
  }

  @Test def aSlamSentAsTheClientSendsItStillLands(): Unit = {
    val p = m.join(CharacterId.Crusader, 20, 20)
    val victim = m.join(CharacterId.Gladiator, 21, 20)
    val slam = shieldBash(p, (0f, 0f))
    assertEquals(1, slam.size)
    m.tickUntilGone(slam.head)
    assertTrue("it caught someone beside its caster", victim.getHealth < victim.getMaxHealth)
  }

  // --- The charge ---

  /** A primary shot the Wizard charged to `charge`, as its client sends it: the projectile. */
  private def charged(p: Player, charge: Int): Projectile = {
    val before = m.instance.projectileManager.getAll.map(_.id).toSet
    m.instance.handler.processPacket(PacketSerializer.deserialize(ProjectilePacket.spawnRequest(1, p.getId,
      p.getPosition.getX.toFloat, p.getPosition.getY.toFloat, p.getColorRGB, 1f, 0f, charge.toByte, primary(p),
      AttackSlot.PRIMARY).serialize()), null, null)
    m.instance.projectileManager.getAll.find(pr => !before.contains(pr.id)).orNull
  }

  private def wizard(): Player = {
    val w = m.join(CharacterId.Wizard, 20, 20)
    assertTrue("the Wizard's bolt charges", ProjectileDef.get(primary(w)).chargeDamageScaling.isDefined)
    w
  }

  @Test def aShotIsNoMoreChargedThanTheTimeSinceTheLastAllows(): Unit = {
    // The charge was taken as the client said, 0 to 100: a full charge — the bolt's damage five
    // times over — on every shot, twice a second, without the four seconds of holding it takes
    val w = wizard()
    assertNotNull(charged(w, 0))
    Thread.sleep(450) // past the fire rate: long enough to shoot, nowhere near long enough to charge
    val shot = charged(w, 100)
    assertNotNull(shot)
    // Under a second held (however slow the machine running this), nowhere near a full charge
    assertTrue(s"charged to ${shot.chargeLevel}", shot.chargeLevel < 50)
  }

  @Test def aShotChargedForAsLongAsItSaysKeepsItsCharge(): Unit = {
    val w = wizard()
    assertNotNull(charged(w, 0))
    Thread.sleep(1000)
    assertEquals(20, charged(w, 20).chargeLevel)
  }

  @Test def theFirstShotOfALifeIsTimedFromTheRespawn(): Unit = {
    val w = wizard()
    w.damage(w.getHealth)
    m.instance.respawn(w.getId)
    val shot = charged(w, 100)
    assertNotNull(shot)
    assertTrue(s"charged to ${shot.chargeLevel}", shot.chargeLevel < 50)
  }

  @Test def onlyThePrimaryCharges(): Unit = {
    // The client sends every other attack uncharged
    val soldier = m.join(CharacterId.Soldier, 20, 20)
    val before = m.instance.projectileManager.getAll.map(_.id).toSet
    m.instance.handler.processPacket(PacketSerializer.deserialize(ProjectilePacket.spawnRequest(1, soldier.getId,
      20f, 20f, soldier.getColorRGB, 1f, 0f, 100.toByte, primary(soldier), AttackSlot.BURST).serialize()), null, null)
    val burst = m.instance.projectileManager.getAll.filterNot(pr => before.contains(pr.id))
    assertEquals(1, burst.size)
    assertEquals(0, burst.head.chargeLevel)
  }

  // --- Who may fire ---

  @Test def aPhasedPlayerFiresNothing(): Unit = {
    // Untouchable while phased — the client doesn't let it fire, and the server takes nothing
    // from it either, as it already took no trap and no barrier
    val wraith = m.join(CharacterId.Wraith, 20, 20)
    wraith.setPhasedUntil(System.currentTimeMillis() + 3000)
    assertTrue(m.fire(wraith, AttackSlot.PRIMARY, primary(wraith), Seq((1f, 0f))).isEmpty)
    // ...and it cost nothing: the first shot once the phase is over goes
    wraith.setPhasedUntil(System.currentTimeMillis() - 1)
    assertEquals(1, m.fire(wraith, AttackSlot.PRIMARY, primary(wraith), Seq((1f, 0f))).size)
  }

  @Test def aShotAsThePhaseEndsIsLetThrough(): Unit = {
    // The client's phase ends a trip across the wire before the server's does, so its first shot
    // can land in the last moments of the server's
    val wraith = m.join(CharacterId.Wraith, 20, 20)
    wraith.setPhasedUntil(System.currentTimeMillis() + 100)
    assertEquals(1, m.fire(wraith, AttackSlot.PRIMARY, primary(wraith), Seq((1f, 0f))).size)
  }

  @Test def theGraceAtAPhasesEndIsTheNetworksToleranceToTheMillisecond(): Unit = Sim() { clock =>
    val sim = new SimMatch(clock)
    val grace = com.gridgame.common.Constants.NETWORK_TOLERANCE_MS
    val early = sim.join(CharacterId.Wraith, 20, 20)
    early.setPhasedUntil(clock.millis() + grace + 1)
    assertTrue("with a millisecond more than the tolerance of the phase left",
      sim.fire(early, AttackSlot.PRIMARY, primary(early), Seq((1f, 0f))).isEmpty)
    val onIt = sim.join(CharacterId.Wraith, 20, 30)
    onIt.setPhasedUntil(clock.millis() + grace)
    assertEquals("with exactly the tolerance of the phase left", 1, sim.fire(onIt, AttackSlot.PRIMARY, primary(onIt), Seq((1f, 0f))).size)
  }

  @Test def theDeadAndTheHeldFireNothing(): Unit = {
    val a = m.join(CharacterId.Soldier, 20, 20)
    a.damage(a.getHealth)
    assertTrue(m.fire(a, AttackSlot.PRIMARY, primary(a), Seq((1f, 0f))).isEmpty)
    val b = m.join(CharacterId.Soldier, 30, 20)
    assertTrue(b.tryFreeze(2000))
    assertTrue(m.fire(b, AttackSlot.PRIMARY, primary(b), Seq((1f, 0f))).isEmpty)
  }

  @Test def aShotFromFarAwayIsRefused(): Unit = {
    val soldier = m.join(CharacterId.Soldier, 20, 20)
    val before = m.instance.projectileManager.size
    m.instance.handler.processPacket(PacketSerializer.deserialize(ProjectilePacket.spawnRequest(1, soldier.getId,
      55f, 20f, soldier.getColorRGB, 1f, 0f, 0.toByte, primary(soldier), AttackSlot.PRIMARY).serialize()), null, null)
    assertEquals("35 cells: past three and what a bad network can leave the server behind by", before,
      m.instance.projectileManager.size)
  }

  @Test def aShotFromNowhereIsRefusedAndCostsNothing(): Unit = {
    // A NaN compares false with everything, so a spawn request from (NaN, NaN) passed the check
    // that it is within three cells of its shooter, and was fired from the corner of the map
    val soldier = m.join(CharacterId.Soldier, 30, 30)
    def request(x: Float, y: Float): Unit =
      m.instance.handler.processPacket(PacketSerializer.deserialize(ProjectilePacket.spawnRequest(1, soldier.getId,
        x, y, soldier.getColorRGB, 1f, 0f, 0.toByte, primary(soldier), AttackSlot.PRIMARY).serialize()), null, null)
    for ((x, y) <- Seq((Float.NaN, Float.NaN), (Float.NaN, 30f), (30f, Float.NaN),
                       (Float.PositiveInfinity, 30f), (30f, Float.NegativeInfinity))) {
      request(x, y)
      assertEquals(s"a shot from ($x, $y)", 0, m.instance.projectileManager.size)
    }
    val shot = m.fire(soldier, AttackSlot.PRIMARY, primary(soldier), Seq((1f, 0f)))
    assertEquals("the next real shot isn't held back by them", 1, shot.size)
    assertEquals("and it leaves its shooter", 31f, shot.head.getX, 0f)
  }

  @Test def aHeadingThatIsNoNumberIsRefusedAndCostsNothing(): Unit = {
    // Either part of it. A NaN is neither shorter than the shortest heading nor longer, and an
    // infinite one comes out of being made a unit as (NaN, 0): a projectile heading nowhere,
    // which never reaches the end of its range. The wire can't carry one as it stands (a heading
    // goes as two scaled shorts, and a NaN is written as 0), so these are handed to the match as
    // packets already read: the check is there for the day the heading is sent some other way
    val soldier = m.join(CharacterId.Soldier, 30, 30)
    val nan = Float.NaN
    val inf = Float.PositiveInfinity
    var seq = 0
    for (heading <- Seq((nan, nan), (nan, 1f), (1f, nan), (inf, 1f), (1f, -inf), (-inf, 0f), (0f, inf), (inf, inf))) {
      seq += 1
      ServerTestKit.captureStderr {
        assertFalse(m.instance.handler.processPacket(new ProjectilePacket(seq, soldier.getId, 30f, 30f, 0, AttackSlot.PRIMARY,
          heading._1, heading._2, ProjectileAction.SPAWN, null, 0.toByte, primary(soldier)), null, null))
      }
      assertEquals(s"a shot heading $heading", 0, m.instance.projectileManager.size)
    }
    val shot = m.fire(soldier, AttackSlot.PRIMARY, primary(soldier), Seq((0f, 1f)))
    assertEquals("the next real shot isn't held back by them", 1, shot.size)
    assertEquals((0f, 1f), (Math.signum(shot.head.dx), Math.signum(shot.head.dy)))
  }

  // --- What the rest of the match hears of it ---

  @Test def aRequestToFireIsNeverPassedOnAsItCame(): Unit = {
    // The server tells of the shots it spawns itself (broadcastProjectileSpawn). What the handler
    // says to pass on, GameServer sends to everyone else in the match as it came: a request it
    // refused would be a shot on their screens that was never fired, and one it took a second shot
    // beside the real one
    var seq = 0
    def passedOn(in: TestMatch, who: java.util.UUID, pType: Byte, x: Float = 30f, y: Float = 30f, dx: Float = 1f, dy: Float = 0f,
                 charge: Int = 0, slot: Int = AttackSlot.PRIMARY, action: Byte = ProjectileAction.SPAWN,
                 overTheWire: Boolean = true): Boolean = {
      seq += 1
      val request = new ProjectilePacket(seq, who, x, y, 0, slot, dx, dy, action, null, charge.toByte, pType)
      in.instance.handler.processPacket(if (overTheWire) PacketSerializer.deserialize(request.serialize()) else request, null, null)
    }
    ServerTestKit.captureStderr(ServerTestKit.quietly {
      val a = m.join(CharacterId.Soldier, 30, 30)
      val shot = primary(a)
      assertFalse("a shot the server took", passedOn(m, a.getId, shot))
      assertEquals(1, m.instance.projectileManager.size)
      assertFalse("nor any of a dozen more at once, most of them too soon", (0 until 12).map(_ => passedOn(m, a.getId, shot)).exists(identity))
      assertTrue("some of them were refused", m.instance.projectileManager.size < 13)
      m.instance.handler.resetAttacks(a.getId)
      for (action <- Seq(ProjectileAction.MOVE, ProjectileAction.HIT, ProjectileAction.DESPAWN, ProjectileAction.PIERCE, ProjectileAction.BLOCKED))
        assertFalse(s"what only the server says of a projectile (action $action)", passedOn(m, a.getId, shot, action = action))
      assertFalse("from somebody who isn't in the match", passedOn(m, java.util.UUID.randomUUID(), shot))
      assertFalse("from nowhere", passedOn(m, a.getId, shot, x = Float.NaN))
      assertFalse("heading nowhere (which the wire can't carry)", passedOn(m, a.getId, shot, dx = Float.NaN, overTheWire = false))
      assertFalse("with no heading", passedOn(m, a.getId, shot, dx = 0f, dy = 0f))
      assertFalse("from the far side of the map", passedOn(m, a.getId, shot, x = 58f))
      assertFalse("charged past full", passedOn(m, a.getId, shot, charge = 101))
      assertFalse("another attack's projectile", passedOn(m, a.getId, CharacterDef.get(a.getCharacterId).qAbility.projectileType))
      assertFalse("an attack there isn't", passedOn(m, a.getId, shot, slot = 9))
      // With as many in flight as a player may have
      while (m.instance.projectileManager.getAll.count(_.ownerId == a.getId) < 30) {
        m.instance.handler.resetAttacks(a.getId)
        val before = m.instance.projectileManager.size
        assertFalse(passedOn(m, a.getId, shot))
        assertEquals(before + 1, m.instance.projectileManager.size)
      }
      m.instance.handler.resetAttacks(a.getId)
      val full = m.instance.projectileManager.size
      assertFalse("one more than a player may have in flight", passedOn(m, a.getId, shot))
      assertEquals(full, m.instance.projectileManager.size)

      val held = m.join(CharacterId.Soldier, 30, 40)
      assertTrue(held.tryFreeze(2000))
      assertFalse("held", passedOn(m, held.getId, shot, y = 40f))
      val dead = m.join(CharacterId.Soldier, 30, 45)
      dead.damage(dead.getHealth)
      assertFalse("dead", passedOn(m, dead.getId, shot, y = 45f))
      val phased = m.join(CharacterId.Wraith, 30, 50)
      phased.setPhasedUntil(System.currentTimeMillis() + 3000)
      assertFalse("phased", passedOn(m, phased.getId, primary(phased), y = 50f))

      val ceasefire = new TestMatch(opening = true)
      val waiting = ceasefire.join(CharacterId.Soldier, 30, 30)
      assertFalse("in a free-for-all's ceasefire", passedOn(ceasefire, waiting.getId, shot))
      val walled = new TestMatch(gameMode = 1, opening = true)
      val behindTheWall = walled.join(CharacterId.Soldier, 28, 30, team = 1)
      assertFalse("from across the opening's wall", passedOn(walled, behindTheWall.getId, shot, x = 32f))
      assertEquals(0, ceasefire.instance.projectileManager.size + walled.instance.projectileManager.size)
    })
  }

  @Test def aChargeOutOfRangeIsRefused(): Unit = {
    val soldier = m.join(CharacterId.Soldier, 20, 20)
    val before = m.instance.projectileManager.size
    m.instance.handler.processPacket(PacketSerializer.deserialize(ProjectilePacket.spawnRequest(1, soldier.getId,
      20f, 20f, soldier.getColorRGB, 1f, 0f, 101.toByte, primary(soldier), AttackSlot.PRIMARY).serialize()), null, null)
    assertEquals(before, m.instance.projectileManager.size)
  }
}
