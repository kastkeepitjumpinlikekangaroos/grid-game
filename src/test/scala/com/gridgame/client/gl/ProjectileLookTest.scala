package com.gridgame.client.gl

import com.gridgame.client.render.projectiles.GLProjectileRenderers._
import com.gridgame.common.model._
import org.junit.Assert._
import org.junit.Test

/** A projectile is drawn as its thrower's: the table that picks a character's own look for a type,
  * held to the roster. */
class ProjectileLookTest {
  private def thrown(t: Byte): Boolean = !(t >= -4 && t < 0) // a buff, a teleport, a barrier, a trap

  /** Every (character, attack type) in the roster. */
  private lazy val attacks: Seq[(CharacterDef, Byte)] = CharacterDef.all.flatMap { c =>
    Seq(c.primaryProjectileType, c.qAbility.projectileType, c.eAbility.projectileType).distinct
      .filter(thrown).map(t => (c, t))
  }

  // The types that were all drawn as one crescent, whoever threw them
  private val Crescents = Set(ProjectileType.TIDAL_WAVE, ProjectileType.GUST, ProjectileType.WIND_BLADE,
    ProjectileType.SAND_BLAST, ProjectileType.SONIC_WAVE, ProjectileType.SONIC_WAVE_HEAVY,
    ProjectileType.SONIC_WAVE_MED, ProjectileType.MOMENTUM_STRIKE, ProjectileType.FLAME_WAVE,
    ProjectileType.ACID_SPRAY, ProjectileType.EARTHSPLITTER)

  @Test def everyAttackInTheRosterIsDrawn(): Unit = {
    assertTrue(attacks.size > 200)
    attacks.foreach { case (c, t) =>
      assertNotNull(s"${c.displayName}'s type $t has no renderer", getRenderer(c.id.id, t))
    }
  }

  @Test def everyoneWhoThrewTheCrescentHasALookOfTheirOwn(): Unit = {
    // Fifteen characters, the Windwalker and the Cloudrunner with two types each
    val looks = attacks.filter { case (_, t) => Crescents(t) }
    assertEquals("the characters who threw a crescent, and what", 17, looks.size)
    for ((a, ta) <- looks; (b, tb) <- looks if a ne b)
      assertNotSame(s"${a.displayName}'s type $ta and ${b.displayName}'s type $tb are drawn alike",
        getRenderer(a.id.id, ta), getRenderer(b.id.id, tb))
  }

  @Test def aTypesOwnLookIsOneOfItsThrowers(): Unit = {
    // A thrower nobody knows gets the type's own look, so it should be somebody's
    Crescents.foreach { t =>
      val theirs = attacks.collect { case (c, u) if u == t => getRenderer(c.id.id, t) }
      assertTrue(s"type $t is thrown", theirs.nonEmpty)
      assertTrue(s"type $t's own look is none of its throwers'", theirs.exists(_ eq getRenderer(t)))
    }
  }

  @Test def aThrowerWithNoLookOfTheirOwnGetsTheTypes(): Unit = {
    val sonic = ProjectileType.SONIC_WAVE
    assertSame(getRenderer(sonic), getRenderer(-1.toByte, sonic))
    assertSame(getRenderer(sonic), getRenderer(CharacterId.Bard.id, sonic))
    assertSame(getRenderer(sonic), getRenderer(CharacterId.Gorilla.id, sonic))
    assertNotSame(getRenderer(sonic), getRenderer(CharacterId.Musician.id, sonic))
    assertNotSame(getRenderer(sonic), getRenderer(CharacterId.Sphinx.id, sonic))
    // and a type nobody has a look of their own for is the type's, whoever throws it
    assertSame(getRenderer(ProjectileType.FIREBALL), getRenderer(CharacterId.Musician.id, ProjectileType.FIREBALL))
  }
}
