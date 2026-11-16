package com.gridgame.client.audio

import com.gridgame.common.model._
import org.junit.Assert._
import org.junit.Test

import java.io.File

/**
 * Every attack, every cast and every blast makes a sound, from a file that exists, and each
 * character's are its own. A name with no file plays nothing, and silently: the mixer skips what
 * it can't load.
 */
class AbilitySoundsTest {
  private def exists(sound: String): Boolean = new File(s"sounds/$sound.wav").isFile // sounds/ is data here

  private val plainCasts = Set("dash", "teleport", "phase_shift", "barrier_up", "trap_place")

  @Test def everySoundNamedHasAFile(): Unit =
    AbilitySounds.allSoundNames.foreach(s => assertTrue(s, exists(s)))

  @Test def everyEventSoundHasAFileToo(): Unit = {
    // A spawn, a death, a hit, the plain cast of each kind, a trap, R, the music
    assertTrue(AudioManager.EVENT_SOUNDS.contains("cooldown_reset"))
    plainCasts.foreach(s => assertTrue(s, AudioManager.EVENT_SOUNDS.contains(s)))
    AudioManager.EVENT_SOUNDS.foreach(s => assertTrue(s, exists(s)))
  }

  @Test def everyFileIsASoundTheGameCanPlay(): Unit = {
    // The other way round: a file nothing names is a megabyte nobody hears
    val named = AbilitySounds.allSoundNames.toSet ++ AudioManager.EVENT_SOUNDS
    val files = new File("sounds").listFiles().map(_.getName).filter(_.endsWith(".wav")).map(_.dropRight(4))
    assertTrue("no sound files found", files.length > 100)
    files.foreach(f => assertTrue(s"sounds/$f.wav is played by nothing", named.contains(f)))
  }

  @Test def everyCharacterHasARowOfThreeSoundsWithFiles(): Unit =
    for (c <- CharacterDef.all) {
      val row = AbilitySounds.rowOf(c.id.id)
      assertNotNull(s"${c.displayName} has no row in AbilitySounds", row)
      assertEquals(3, row.length)
      row.foreach(s => assertTrue(s"${c.displayName}: $s", exists(s)))
    }

  @Test def everyAttackOfEveryCharacterSoundsAsItsRowSays(): Unit =
    for (c <- CharacterDef.all) {
      val row = AbilitySounds.rowOf(c.id.id)
      assertEquals(c.displayName, row(0), AbilitySounds.forAttack(c.primaryProjectileType, c.id.id))
      for ((ability, slot) <- Seq(c.qAbility, c.eAbility).zipWithIndex) {
        val kind = AbilitySounds.kindOf(ability.castBehavior)
        if (kind < 0) {
          // It throws a projectile: heard by the projectile's type and whose it is. (A character
          // whose primary and ability throw the same type hears the primary's for both: nothing
          // tells the client which key a projectile came from.)
          val heard = AbilitySounds.forAttack(ability.projectileType, c.id.id)
          if (ability.projectileType != c.primaryProjectileType)
            assertEquals(s"${c.displayName} ${ability.name}", row(slot + 1), heard)
          assertTrue(s"${c.displayName} ${ability.name} -> $heard", exists(heard))
        } else {
          // It throws nothing: heard as it is cast, by its slot
          assertEquals(s"${c.displayName} ${ability.name}", row(slot + 1), AbilitySounds.forCast(c.id.id, slot, kind))
          assertTrue(s"${c.displayName} ${ability.name}", AbilitySounds.hasCast(c.id.id, kind))
        }
      }
    }

  @Test def aCastIsItsCharactersOwnAndNotThePlainOne(): Unit = {
    // Every dash, blink, phase, barrier and trap used to make its kind's one sound, whoever cast it
    for (c <- CharacterDef.all; (ability, slot) <- Seq(c.qAbility, c.eAbility).zipWithIndex) {
      val kind = AbilitySounds.kindOf(ability.castBehavior)
      if (kind >= 0) {
        val s = AbilitySounds.forCast(c.id.id, slot, kind)
        assertFalse(s"${c.displayName}'s ${ability.name} is the plain $s", plainCasts.contains(s))
      }
    }
    assertNotEquals(AbilitySounds.forCast(CharacterId.Wizard.id, 1, AbilitySounds.TELEPORT),
      AbilitySounds.forCast(CharacterId.Barbarian.id, 0, AbilitySounds.TELEPORT))
  }

  @Test def anotherPlayersCastIsFoundByItsKind(): Unit = {
    // The client sees that they blinked, not which key they pressed
    assertEquals("cast_blink", AbilitySounds.forCastOf(CharacterId.Wizard.id, AbilitySounds.TELEPORT))
    assertEquals("cast_swoop", AbilitySounds.forCastOf(CharacterId.Raptor.id, AbilitySounds.DASH))
    assertEquals("cast_scutum", AbilitySounds.forCastOf(CharacterId.Gladiator.id, AbilitySounds.BARRIER))
    // The Spider burrows (a teleport) and spins a web (a trap): each found as itself
    assertEquals("cast_burrow", AbilitySounds.forCastOf(CharacterId.Spider.id, AbilitySounds.TELEPORT))
    assertEquals("cast_web_trap", AbilitySounds.forCastOf(CharacterId.Spider.id, AbilitySounds.TRAP))
    // One who has no such ability, moved by something else: the plain one
    assertEquals("teleport", AbilitySounds.forCastOf(CharacterId.Wraith.id, AbilitySounds.TELEPORT))
    assertFalse(AbilitySounds.hasCast(CharacterId.Wraith.id, AbilitySounds.DASH))
    assertTrue(AbilitySounds.hasCast(CharacterId.Wraith.id, AbilitySounds.PHASE))
  }

  @Test def aTrapSetDownSoundsAsWhoeverSetIt(): Unit = {
    // The Warden's bear trap and the Gravedigger's grave are the same trap to the rules
    val warden = AbilitySounds.forTrap(CharacterId.Warden.id, TrapType.BEAR_TRAP)
    assertEquals("cast_snare_mine", warden)
    for (c <- CharacterDef.all; ability <- Seq(c.qAbility, c.eAbility)) ability.castBehavior match {
      case TrapCast(trapType, _) =>
        val s = AbilitySounds.forTrap(c.id.id, trapType)
        assertNotEquals(s"${c.displayName} ${ability.name}", "trap_place", s)
        assertTrue(s, exists(s))
      case _ =>
    }
    assertEquals("trap_place", AbilitySounds.forTrap(CharacterId.Wizard.id, TrapType.MINE))
  }

  @Test def noTwoCharactersPrimariesAreOneSound(): Unit = {
    // The primary is what a player hears for a whole match: nobody's is somebody else's
    val byPrimary = CharacterDef.all.groupBy(c => AbilitySounds.rowOf(c.id.id)(0))
    byPrimary.foreach { case (sound, cs) => assertEquals(s"$sound is the primary of ${cs.map(_.displayName).mkString(", ")}", 1, cs.size) }
  }

  @Test def everyDefinedProjectileHasASoundOfItsOwnOrTheDefault(): Unit = {
    for (t <- -128 to 127 if ProjectileDef.get(t.toByte).id == t.toByte) {
      val s = AbilitySounds.forAttack(t.toByte)
      assertNotNull(s)
      assertTrue(s"type $t -> $s", exists(s))
    }
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
    for (t <- Seq(INFERNO_BLAST, NAPALM_STRIKE, ERUPTION, MUD_BOMB, ACID_FLASK, BLIGHT_BOMB, MIASMA, ROCKET))
      assertNotEquals(s"type $t", frag, AbilitySounds.forBlast(t))
    assertNotEquals(AbilitySounds.forBlast(MUD_BOMB), AbilitySounds.forBlast(INFERNO_BLAST))
  }

  @Test def anExplosiveGoesOffAsItsThrowers(): Unit = {
    // The Soldier's grenade, the Pirate's cannonball and the Bombardier's bomb are one projectile
    import com.gridgame.common.model.ProjectileType.GRENADE
    val soldier = AbilitySounds.forBlast(GRENADE, CharacterId.Soldier.id)
    val pirate = AbilitySounds.forBlast(GRENADE, CharacterId.Pirate.id)
    val bombardier = AbilitySounds.forBlast(GRENADE, CharacterId.Bombardier.id)
    assertEquals(AbilitySounds.FragBlast, soldier)
    assertEquals(3, Set(soldier, pirate, bombardier).size)
    Seq(pirate, bombardier).foreach(s => assertTrue(s, exists(s)))
  }

  @Test def aCharactersSoundsToLoadAreItsRowAndItsBlasts(): Unit = {
    val pirate = AbilitySounds.soundsOf(CharacterId.Pirate.id)
    AbilitySounds.rowOf(CharacterId.Pirate.id).foreach(s => assertTrue(s, pirate.contains(s)))
    assertTrue(pirate.toString, pirate.contains("blast_cannonball"))
    assertTrue(AbilitySounds.soundsOf(CharacterId.Wizard.id).forall(exists))
    assertTrue(AbilitySounds.soundsOf(-1).isEmpty)
  }

  @Test def aCharactersOwnBeatsTheTypes(): Unit = {
    // A Bard's song and a Musician's are one projectile type, and not one sound
    val bard = AbilitySounds.forAttack(ProjectileType.SONIC_WAVE, CharacterId.Bard.id)
    val musician = AbilitySounds.forAttack(ProjectileType.SONIC_WAVE, CharacterId.Musician.id)
    assertNotEquals(bard, musician)
    // ...and a thrower nobody knows gets the type's, which is one of its throwers'
    val anyones = AbilitySounds.forAttack(ProjectileType.SONIC_WAVE)
    assertTrue(anyones, anyones == bard || anyones == musician)
  }

  @Test def aSoundWithAPitchKeepsIt(): Unit = {
    // A lute a semitone flat on every other strum
    assertTrue(AbilitySounds.isTuned("atk_lute_chord"))
    assertTrue(AbilitySounds.isTuned("atk_arcane_bolt"))
    assertFalse(AbilitySounds.isTuned("atk_gunshot"))
    assertFalse(AbilitySounds.isTuned("explosion"))
  }
}
