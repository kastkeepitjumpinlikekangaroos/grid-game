package com.gridgame.server

import com.gridgame.server.game.{ClientHandler, GameInstance, PacketValidator}
import com.gridgame.common.Constants
import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

import java.util.UUID

object AbilityCastValidationTest {
  // Building a GameServer generates a TLS certificate and opens the auth database, so the tests
  // share one. It is never started, so it holds no sockets; each test gets its own GameInstance.
  lazy val server = new GameServer(0)
}

/**
 * Attacks fed to the server's ClientHandler as the client sends them. The server used to work out
 * which attack fired a projectile from its type, and characters fire one type from two attacks:
 * Bear's Maul, then a ring of eight of the claws it swipes with, was taken for a gem-boosted primary
 * shot and cut to its first three claws, which the fan sent from straight behind the bear. No
 * volley is more than three since (design/characters.md), and the Jester's Card Fan is three of
 * the cards it throws.
 */
class AbilityCastValidationTest {
  import AbilityCastValidationTest.server

  /** A player pressing a second key a moment after the first: past the first cast, well inside
    * every cooldown. */
  private val PRESS_GAP_MS = 150L

  private val instance = {
    val gi = new GameInstance(2.toShort, "", 5, server)
    gi.world = WorldData.createEmpty(60, 60)
    gi
  }
  private var seq = 0

  private def join(character: CharacterId): Player = {
    val p = new Player(UUID.randomUUID(), "p", new Position(20, 20), 0xFF00FF00, 100, 100)
    p.setCharacterId(character.id)
    instance.registry.add(p)
    p
  }

  private def charDef(p: Player): CharacterDef = CharacterDef.get(p.getCharacterId)

  private def ability(p: Player, slot: Int): AbilityDef =
    if (slot == AttackSlot.Q) charDef(p).qAbility else charDef(p).eAbility

  /** Headings, off the aim, that one press of the attack fires along — as GameClient fires them. */
  private def headingsOf(p: Player, slot: Int): Seq[Double] =
    if (slot == AttackSlot.PRIMARY) Seq(0.0)
    else ability(p, slot).castBehavior match {
      case fan: FanProjectile => (0 until fan.count).map(fan.angleOf)
      case StandardProjectile | GroundSlam(_) => Seq(0.0)
      case _ => Seq.empty
    }

  /** Send spawn requests over the wire format, as the client does. Returns the projectiles the
    * server spawned for them. */
  private def send(p: Player, slot: Int, pType: Byte, headings: Seq[Double],
                   aim: (Float, Float) = (1f, 0f)): Seq[Projectile] = {
    val before = instance.projectileManager.getAll.map(_.id).toSet
    for (theta <- headings) {
      val (ax, ay) = aim
      val cos = Math.cos(theta).toFloat
      val sin = Math.sin(theta).toFloat
      seq += 1
      val request = ProjectilePacket.spawnRequest(seq, p.getId, 20f, 20f, p.getColorRGB,
        ax * cos - ay * sin, ax * sin + ay * cos, 0.toByte, pType, slot)
      instance.handler.processPacket(PacketSerializer.deserialize(request.serialize()), null, null)
    }
    instance.projectileManager.getAll.filterNot(pr => before.contains(pr.id))
  }

  /** Press one of the player's attacks. */
  private def fire(p: Player, slot: Int, aim: (Float, Float) = (1f, 0f)): Seq[Projectile] = {
    val pType = if (slot == AttackSlot.PRIMARY) charDef(p).primaryProjectileType else ability(p, slot).projectileType
    send(p, slot, pType, headingsOf(p, slot), aim)
  }

  /** Where each projectile is heading, in whole degrees off the aim, in [0, 360). */
  private def degreesOff(aim: (Float, Float), projectiles: Seq[Projectile]): Seq[Long] =
    projectiles.map { pr =>
      val d = Math.toDegrees(Math.atan2(pr.dy, pr.dx) - Math.atan2(aim._2, aim._1))
      Math.floorMod(Math.round(d), 360L)
    }.sorted

  @Test
  def theJestersCardFanFansOutRoundTheCursor(): Unit = {
    // Three of its primary's cards: taken for a gem boost's three, they would all be the shot's
    val aim = (0.6f, 0.8f)
    val cards = fire(join(CharacterId.Jester), AttackSlot.Q, aim)
    assertEquals(Seq(0L, 15L, 345L), degreesOff(aim, cards))
  }

  @Test
  def theVampiresBatsFanOutRoundTheCursor(): Unit = {
    val aim = (-1f, 0f)
    val bats = fire(join(CharacterId.Vampire), AttackSlot.E, aim)
    assertEquals(Seq(0L, 25L, 335L), degreesOff(aim, bats))
  }

  @Test
  def everyAbilityLandsEveryProjectileItFires(): Unit = {
    for (c <- CharacterDef.all; slot <- Seq(AttackSlot.Q, AttackSlot.E)) {
      val p = join(c.id)
      val expected = headingsOf(p, slot).size
      if (expected > 0) {
        val name = s"${c.displayName} ${ability(p, slot).name}"
        assertEquals(name, expected, fire(p, slot).size)
      }
    }
  }

  @Test
  def anAbilityRightAfterAPrimaryShotOfTheSameTypeLandsInFull(): Unit = {
    // Each of these fires its primary's projectile from Q as well. Straight after a primary shot,
    // the server used to count the cast as more of that shot and let only part of it through.
    val sharing = CharacterDef.all.filter(c => c.qAbility.projectileType == c.primaryProjectileType)
    assertEquals(Set(CharacterId.Enchantress, CharacterId.Jester), sharing.map(_.id).toSet)
    for (c <- sharing) {
      val p = join(c.id)
      assertEquals(c.displayName, 1, fire(p, AttackSlot.PRIMARY).size)
      Thread.sleep(PRESS_GAP_MS)
      assertEquals(c.displayName, headingsOf(p, AttackSlot.Q).size, fire(p, AttackSlot.Q).size)
    }
  }

  @Test
  def aPrimaryShotRightAfterTheAbilityIsNotDropped(): Unit = {
    val p = join(CharacterId.Jester)
    assertEquals(3, fire(p, AttackSlot.Q).size)
    Thread.sleep(PRESS_GAP_MS)
    assertEquals(1, fire(p, AttackSlot.PRIMARY).size)
  }

  @Test
  def twoAbilitiesLandBackToBack(): Unit = {
    // Each on its own clock. They used to share Q's when they fired one type, so whichever came
    // second lost its first projectile — all of it, for a single bolt
    for (c <- Seq(CharacterId.Cleric, CharacterId.Soldier, CharacterId.Photon, CharacterId.Gambler);
         (first, second) <- Seq((AttackSlot.Q, AttackSlot.E), (AttackSlot.E, AttackSlot.Q))) {
      val p = join(c)
      assertEquals(c.name, headingsOf(p, first).size, fire(p, first).size)
      Thread.sleep(PRESS_GAP_MS)
      assertEquals(c.name, headingsOf(p, second).size, fire(p, second).size)
    }
  }

  @Test
  def aHitLandingMidCastDoesNotCutTheCastShort(): Unit = {
    // Bat Swarm's type is its own, so this is only about the hit
    val p = join(CharacterId.Vampire)
    val fan = headingsOf(p, AttackSlot.E)
    val first = send(p, AttackSlot.E, ProjectileType.BAT_SWARM, fan.take(1))
    // A bat lands on someone before the rest of the fan has been handled
    instance.handler.notifyAbilityHit(p.getId, ProjectileType.BAT_SWARM, p.getCharacterId)
    val rest = send(p, AttackSlot.E, ProjectileType.BAT_SWARM, fan.drop(1))
    assertEquals(3, first.size + rest.size)
  }

  // --- What the server still refuses ---

  @Test
  def aCastCannotFireMoreThanTheAbilityDoes(): Unit = {
    val p = join(CharacterId.Vampire)
    val fan = headingsOf(p, AttackSlot.E)
    assertEquals(3, send(p, AttackSlot.E, ProjectileType.BAT_SWARM, fan :+ 0.0).size)
  }

  @Test
  def anAbilityCannotBeRecastInsideItsCooldown(): Unit = {
    val p = join(CharacterId.Vampire)
    assertEquals(3, fire(p, AttackSlot.E).size)
    Thread.sleep(PRESS_GAP_MS)
    assertEquals(0, fire(p, AttackSlot.E).size)
  }

  @Test
  def aBurstABadNetworkSpreadOutIsStillOneCast(): Unit = {
    // A bad link delays each datagram its own way: the burst's eight, sent together, arrive over a
    // few hundred ms. One cast still, up to the network's tolerance or half its cooldown
    // (PacketValidator.castWindowMs); at 100ms the rest of the burst was refused
    val p = join(CharacterId.Soldier)
    val primary = charDef(p).primaryProjectileType
    val ring = AttackSlot.BurstDirections.map { case (dx, dy) => Math.atan2(dy, dx).toDouble }
    val taken = ring.map { theta => val shot = send(p, AttackSlot.BURST, primary, Seq(theta)); Thread.sleep(50); shot.size }.sum
    assertEquals("all eight, over 400ms", 8, taken)
    // The primary's window stays half its 500ms: a press 300ms after the last is a press of its own
    assertEquals(250L, PacketValidator.castWindowMs(Constants.SHOOT_COOLDOWN_MS))
  }

  @Test
  def thePrimaryKeepsItsFireRate(): Unit = {
    val p = join(CharacterId.Bear)
    // A gem boost's three in one shot...
    assertEquals(3, send(p, AttackSlot.PRIMARY, ProjectileType.CLAW_SWIPE, Seq(0.0, 0.1, -0.1)).size)
    // ...and a press straight after it, as one the network hurried, taken early
    // (PacketValidator.castToleranceMs)...
    assertEquals(1, send(p, AttackSlot.PRIMARY, ProjectileType.CLAW_SWIPE, Seq(0.0)).size)
    // ...but not another: early once, but no faster than the cooldown over time (scheduledCastAt)
    Thread.sleep(PacketValidator.castWindowMs(Constants.SHOOT_COOLDOWN_MS) + 50)
    assertEquals(0, fire(p, AttackSlot.PRIMARY).size)
  }

  @Test
  def aProjectileMustBeTheOneItsAttackFires(): Unit = {
    val p = join(CharacterId.Bear)
    assertEquals("Bear Hug from E", 0, send(p, AttackSlot.E, ProjectileType.GRAB, Seq(0.0)).size)
    assertEquals("a claw from Q", 0, send(p, AttackSlot.Q, ProjectileType.CLAW_SWIPE, Seq(0.0)).size)
    assertEquals("Bear Hug as a primary", 0, send(p, AttackSlot.PRIMARY, ProjectileType.GRAB, Seq(0.0)).size)
    assertEquals("no such attack", 0, send(p, AttackSlot.BURST + 1, ProjectileType.CLAW_SWIPE, Seq(0.0)).size)
  }

  @Test
  def aDashFiresNothing(): Unit = {
    // Fenrir's Savage Leap is a dash, with projectile type -1. A spawn of that type passed as the
    // Q ability's projectile, and flew as a default bolt whose damage scales with charge.
    val p = join(CharacterId.Fenrir)
    assertEquals(0, send(p, AttackSlot.Q, charDef(p).qAbility.projectileType, Seq(0.0)).size)
  }
}
