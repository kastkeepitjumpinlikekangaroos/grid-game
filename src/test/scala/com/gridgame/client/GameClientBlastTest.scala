package com.gridgame.client

import com.gridgame.client.game.GameClient._
import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

import java.util.UUID
import scala.jdk.CollectionConverters._

/** Blasts as the client records them: one for each explosion or splash, remembering who threw it. */
class GameClientBlastTest {
  private val world = WorldData.createEmpty(60, 60)
  world.setTile(40, 20, Tile.Wall)
  private val t = new TestClient(world)
  private val c = t.client

  private def blasts: Seq[Array[Long]] = c.getBlasts.values().asScala.toSeq

  @Test def anExplosiveGoesOffAsOneBlastWhereItStopped(): Unit = {
    t.startMatch(spawn = (5, 5))
    val shooter = UUID.randomUUID()
    t.join(shooter, 8, 8, charId = CharacterId.Soldier.id)
    t.projectile(ProjectileAction.SPAWN, 53, shooter, pType = ProjectileType.GRENADE)
    t.projectile(ProjectileAction.DESPAWN, 53, shooter, x = 30f, y = 30f, pType = ProjectileType.GRENADE)
    // and the blast's HITs on the two it caught, in the same tick: no more blasts
    t.projectile(ProjectileAction.HIT, 53, shooter, x = 30f, y = 30f, pType = ProjectileType.GRENADE,
      target = UUID.randomUUID(), tick = 102)
    assertNull(c.getProjectiles.get(53))
    assertEquals(1, blasts.size)
    val b = blasts.head
    assertEquals(ProjectileType.GRENADE.toLong, b(BLAST_TYPE))
    assertEquals(CharacterId.Soldier.id.toLong, b(BLAST_CHAR))
    assertEquals(3000L, b(BLAST_RADIUS))
    assertEquals(30000L, b(BLAST_X))
    assertEquals(0L, b(BLAST_TRAP))
  }

  @Test def aSlamIsOneBlastNotAnExplosionAndASplash(): Unit = {
    // A slam has both an explosion and a splash at the end of its range, and used to be drawn as
    // both at once: every one a fireball
    t.startMatch(spawn = (5, 5))
    val banshee = UUID.randomUUID()
    t.join(banshee, 20, 20, charId = CharacterId.Banshee.id)
    t.projectile(ProjectileAction.SPAWN, 60, banshee, x = 20f, y = 20f, pType = ProjectileType.TREMOR_SLAM)
    t.projectile(ProjectileAction.DESPAWN, 60, banshee, x = 20f, y = 20f, pType = ProjectileType.TREMOR_SLAM)
    assertEquals(1, blasts.size)
    assertEquals(CharacterId.Banshee.id.toLong, blasts.head(BLAST_CHAR))
    assertEquals(6000L, blasts.head(BLAST_RADIUS))
  }

  @Test def aSplashAndEveryoneItCaughtAreOneBlast(): Unit = {
    t.startMatch(spawn = (5, 5))
    val tide = UUID.randomUUID()
    t.join(tide, 20, 20, charId = CharacterId.Tidecaller.id)
    t.projectile(ProjectileAction.SPAWN, 61, tide, pType = ProjectileType.SPLASH, tick = 200)
    // Struck one, and its splash caught two more, all in one tick
    t.projectile(ProjectileAction.HIT, 61, tide, x = 25f, pType = ProjectileType.SPLASH, target = UUID.randomUUID(), tick = 204)
    t.projectile(ProjectileAction.HIT, 61, tide, x = 25f, pType = ProjectileType.SPLASH, target = UUID.randomUUID(), tick = 204)
    t.projectile(ProjectileAction.HIT, 61, tide, x = 25f, pType = ProjectileType.SPLASH, target = UUID.randomUUID(), tick = 204)
    assertEquals(1, blasts.size)
    assertEquals(3000L, blasts.head(BLAST_RADIUS))
  }

  @Test def aPiercingSplashGoesOffEachTimeItStrikes(): Unit = {
    t.startMatch(spawn = (5, 5))
    val tesla = UUID.randomUUID()
    t.join(tesla, 20, 20, charId = CharacterId.Tesla.id)
    t.projectile(ProjectileAction.SPAWN, 62, tesla, pType = ProjectileType.CHAIN_LIGHTNING_FORK, tick = 300)
    t.projectile(ProjectileAction.PIERCE, 62, tesla, x = 24f, pType = ProjectileType.CHAIN_LIGHTNING_FORK,
      target = UUID.randomUUID(), tick = 305)
    t.projectile(ProjectileAction.HIT, 62, tesla, x = 29f, pType = ProjectileType.CHAIN_LIGHTNING_FORK,
      target = UUID.randomUUID(), tick = 310)
    assertEquals(2, blasts.size)
    assertEquals(Set(24000L, 29000L), blasts.map(_(BLAST_X)).toSet)
  }

  @Test def aSplashAtTheEndOfItsRangeIsNotDrawnWhereTheTerrainStoppedIt(): Unit = {
    // The server sets one off only at the end of the range; a wall that stops the shot first is
    // just a wall
    t.startMatch(spawn = (5, 5))
    val tide = UUID.randomUUID()
    t.join(tide, 20, 20, charId = CharacterId.Tidecaller.id)
    t.projectile(ProjectileAction.SPAWN, 63, tide, x = 37f, y = 20f, pType = ProjectileType.GEYSER)
    t.projectile(ProjectileAction.DESPAWN, 63, tide, x = 40.2f, y = 20f, pType = ProjectileType.GEYSER)
    assertEquals(0, blasts.size)
    t.projectile(ProjectileAction.SPAWN, 64, tide, x = 10f, y = 30f, pType = ProjectileType.GEYSER)
    t.projectile(ProjectileAction.DESPAWN, 64, tide, x = 12f, y = 30f, pType = ProjectileType.GEYSER)
    assertEquals(1, blasts.size)
    assertEquals(4000L, blasts.head(BLAST_RADIUS))
  }

  @Test def aThrowerWeDoNotKnowIsRecordedAsUnknown(): Unit = {
    t.startMatch(spawn = (5, 5))
    val stranger = UUID.randomUUID()
    t.projectile(ProjectileAction.SPAWN, 65, stranger, pType = ProjectileType.ROCKET)
    t.projectile(ProjectileAction.DESPAWN, 65, stranger, x = 30f, y = 30f, pType = ProjectileType.ROCKET)
    assertEquals(-1L, blasts.head(BLAST_CHAR))
  }

  @Test def ourOwnBlastIsOurCharacters(): Unit = {
    c.selectedCharacterId = CharacterId.Bombardier.id
    t.startMatch(spawn = (5, 5))
    t.projectile(ProjectileAction.SPAWN, 66, t.id, pType = ProjectileType.GRENADE)
    t.projectile(ProjectileAction.DESPAWN, 66, t.id, x = 30f, y = 30f, pType = ProjectileType.GRENADE)
    assertEquals(CharacterId.Bombardier.id.toLong, blasts.head(BLAST_CHAR))
  }

  @Test def aMineGoingOffIsATrapsBlast(): Unit = {
    t.startMatch(spawn = (5, 5))
    val sentinel = UUID.randomUUID()
    t.join(sentinel, 20, 20, charId = CharacterId.Sentinel.id)
    t.trap(TrapAction.SPAWN, 7, TrapType.MINE, who = sentinel, x = 12, y = 12)
    t.trap(TrapAction.TRIGGER, 7, TrapType.MINE, who = sentinel, x = 12, y = 12, victim = t.id)
    assertEquals(1, blasts.size)
    val b = blasts.head
    assertEquals(1L, b(BLAST_TRAP))
    assertEquals(TrapType.MINE.toLong, b(BLAST_TYPE))
    assertEquals((TrapDef.get(TrapType.MINE).explosion.get.blastRadius * 1000).toLong, b(BLAST_RADIUS))
    assertTrue("a trap's blast is keyed past every projectile's",
      c.getBlasts.keySet().asScala.forall(_ >= TRAP_BLAST_KEYS))
  }
}
