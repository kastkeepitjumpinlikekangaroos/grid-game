package com.gridgame.client.audio

import com.gridgame.common.model.CharacterDef
import com.gridgame.common.model.ProjectileDef
import org.junit.Assert._
import org.junit.Test

import java.io.File

/**
 * Every attack makes a sound, from a file that exists. A name with no file plays nothing, and
 * silently: the mixer skips what it can't load.
 */
class AbilitySoundsTest {
  private def exists(sound: String): Boolean = new File(s"sounds/$sound.wav").isFile // sounds/ is data here

  @Test def everySoundNamedHasAFile(): Unit =
    AbilitySounds.allSoundNames.foreach(s => assertTrue(s, exists(s)))

  @Test def everyAttackOfEveryCharacterSoundsFromAFile(): Unit = {
    for (c <- CharacterDef.all; t <- Seq(c.primaryProjectileType, c.qAbility.projectileType, c.eAbility.projectileType)
         if t >= 0 || ProjectileDef.get(t).id == t) {
      val s = AbilitySounds.forAttack(t, c.id.id)
      assertTrue(s"${c.displayName} type $t -> $s", exists(s))
    }
  }

  @Test def everyDefinedProjectileHasASoundOfItsOwnOrTheDefault(): Unit = {
    for (t <- -128 to 127 if ProjectileDef.get(t.toByte).id == t.toByte)
      assertNotNull(AbilitySounds.forAttack(t.toByte))
  }

  @Test def everyExplosiveThatDealsItsBlastGoesOffFromAFile(): Unit = {
    for (t <- -128 to 127 if ProjectileDef.get(t.toByte).id == t.toByte) {
      val d = ProjectileDef.get(t.toByte)
      if (d.explosionConfig.exists(e => e.centerDamage > 0 || e.edgeDamage > 0)) {
        val s = AbilitySounds.forBlast(t.toByte)
        assertTrue(s"${d.name} -> $s", exists(s))
      }
    }
  }

  @Test def aFireAMudBombAndAFlaskDoNotGoOffAsAGrenade(): Unit = {
    // Every damaging explosion used to be the one frag grenade
    import com.gridgame.common.model.ProjectileType._
    val frag = AbilitySounds.forBlast(GRENADE)
    assertEquals(AbilitySounds.FragBlast, frag)
    for (t <- Seq(INFERNO_BLAST, NAPALM_STRIKE, ERUPTION, MUD_BOMB, ACID_FLASK, BLIGHT_BOMB, MIASMA))
      assertNotEquals(s"type $t", frag, AbilitySounds.forBlast(t))
    assertNotEquals(AbilitySounds.forBlast(MUD_BOMB), AbilitySounds.forBlast(INFERNO_BLAST))
  }

  @Test def aCharacterOverrideBeatsTheSharedSound(): Unit = {
    // A wolf's howl and a barbarian's slam share a projectile type, but not a sound
    val shared = AbilitySounds.forAttack(com.gridgame.common.model.ProjectileType.SONIC_WAVE)
    val bard = AbilitySounds.forAttack(com.gridgame.common.model.ProjectileType.SONIC_WAVE,
      com.gridgame.common.model.CharacterId.Bard.id)
    assertNotEquals(shared, bard)
  }
}
