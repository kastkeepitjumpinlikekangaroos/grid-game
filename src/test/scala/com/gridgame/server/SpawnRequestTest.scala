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
      40f, 20f, soldier.getColorRGB, 1f, 0f, 0.toByte, primary(soldier), AttackSlot.PRIMARY).serialize()), null, null)
    assertEquals(before, m.instance.projectileManager.size)
  }

  @Test def aChargeOutOfRangeIsRefused(): Unit = {
    val soldier = m.join(CharacterId.Soldier, 20, 20)
    val before = m.instance.projectileManager.size
    m.instance.handler.processPacket(PacketSerializer.deserialize(ProjectilePacket.spawnRequest(1, soldier.getId,
      20f, 20f, soldier.getColorRGB, 1f, 0f, 101.toByte, primary(soldier), AttackSlot.PRIMARY).serialize()), null, null)
    assertEquals(before, m.instance.projectileManager.size)
  }
}
