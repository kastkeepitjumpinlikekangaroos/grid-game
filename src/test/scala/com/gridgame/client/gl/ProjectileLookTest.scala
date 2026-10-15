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
  // ...and the three made from them since the volleys went, drawn as the crescent their thrower
  // drew: the Windwalker's Gale, the Bard's Lullaby, the Sphinx's Riddle
  private val FromCrescents = Set(ProjectileType.GALE, ProjectileType.LULLABY, ProjectileType.RIDDLE)

  @Test def everyAttackInTheRosterIsDrawn(): Unit = {
    assertTrue(attacks.size > 200)
    attacks.foreach { case (c, t) =>
      assertNotNull(s"${c.displayName}'s type $t has no renderer", getRenderer(c.id.id, t))
    }
  }

  @Test def everyoneWhoThrewTheCrescentHasALookOfTheirOwn(): Unit = {
    // Fifteen characters threw one in seventeen attacks; four of those went with the volleys (the
    // Windwalker's, the Cloudrunner's and the Hawk's gusts, the Sphinx's sonic waves) and three of
    // their replacements are drawn as the crescent was
    val looks = attacks.filter { case (_, t) => Crescents(t) || FromCrescents(t) }
    assertEquals("the characters who threw a crescent, and what", 16, looks.size)
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

  // The attacks that were an orb, a ring on the ground or someone else's blob, in colours that weren't
  // their thrower's, and are now drawn as their thrower's own
  private val ownLooks: Seq[(CharacterId, Byte)] = {
    import CharacterId._
    import ProjectileType._
    Seq(Spaceman -> NORMAL, Wraith -> SOUL_BOLT, Tidecaller -> SPLASH, Soldier -> BULLET,
      PlagueDoctor -> PLAGUE_BOLT, PlagueDoctor -> MIASMA, Sandstorm -> SAND_SHOT,
      Inferno -> INFERNO_BLAST, Inferno -> VORTEX_BOMB, Necromancer -> DEATH_BOLT, Banshee -> ECHO_BOLT,
      Lich -> LEECH_BOLT, Lich -> FROST_SHARD, Lich -> SOUL_DRAIN, Shade -> SHADOW_BOLT, Revenant -> LIFE_DRAIN,
      Phantom -> SHADOW_HAUNT, Phantom -> SOUL_BOLT_HEAVY, Mummy -> SAND_SHOT, Mummy -> BANDAGE_WHIP, Mummy -> CURSE,
      Shadowfiend -> DEATH_BOLT, Shadowfiend -> VORTEX_BOMB, Poltergeist -> SOUL_BOLT_HEAVY, Bard -> SONIC_BOOM,
      MechPilot -> SEISMIC_SLAM, Graviton -> GRAVITY_BALL, Graviton -> VORTEX_BOMB, Pilot -> BULLET_HEAVY,
      Pilot -> BULLET, Serpent -> VENOM_BOLT, Spider -> WEB_SHOT, Hydra -> VENOM_BOLT_LIGHT, Hydra -> VENOM_BOLT,
      Hydra -> POISON_CLOUD, Mantis -> SHADOW_HAUNT, Jellyfish -> STING, Gorilla -> SEISMIC_SLAM,
      Chameleon -> TONGUE, Medusa -> VENOM_BOLT, Anubis -> DEATH_BOLT, Anubis -> CURSE, Yokai -> MYSTIC_BOLT,
      Yokai -> BINDING_TALISMAN, Djinn -> MYSTIC_BOLT, Chimera -> SERPENT_STRIKE, Alchemist -> FIREBALL, Alchemist -> ACID_FLASK,
      Pirate -> BULLET, Musician -> SONIC_BOOM, Runesmith -> RUNE_BOLT)
  }

  @Test def everyOneOfThemIsAnAttackWithALookAndAColourOfItsOwn(): Unit = {
    assertEquals(51, ownLooks.size)
    ownLooks.foreach { case (c, t) =>
      assertTrue(s"${c.name} doesn't throw type $t", attacks.exists { case (d, u) => d.id == c && u == t })
      assertNotNull(s"${c.name}'s type $t has no renderer", getRenderer(c.id, t))
      // Lit, and shedding its embers, in a colour of its own rather than the player's random one
      assertTrue(s"${c.name}'s type $t has no colour of its own", glowOf(c.id, t) >= 0)
    }
  }

  @Test def nobodyElseWhoThrowsTheirTypeDrawsItTheSameWay(): Unit = {
    // Shared types: the Soldier's, the Pirate's and the Pilot's bullets; the Necromancer's, the
    // Shadowfiend's, Anubis's and the Warlock's death bolts; three curses, three vortex bombs, four venom bolts...
    for ((a, t) <- ownLooks; (b, u) <- attacks if u == t && b.id != a)
      assertNotSame(s"${a.name} and ${b.displayName} draw type $t alike", getRenderer(a.id, t), getRenderer(b.id.id, u))
  }

  @Test def aLooksColourFollowsWhichLookItIs(): Unit = {
    // A thrower with no look of their own for a type is lit as the type's own look is
    attacks.foreach { case (c, t) =>
      if (getRenderer(c.id.id, t) eq getRenderer(t))
        assertEquals(s"${c.displayName}'s type $t", glowOf(-1.toByte, t), glowOf(c.id.id, t))
    }
    // A look that has no colour of its own keeps the thrower's: the Warlock's death bolt is still the orb
    assertEquals(-1, glowOf(CharacterId.Warlock.id, ProjectileType.DEATH_BOLT))
    assertEquals(-1, glowOf(CharacterId.Wizard.id, ProjectileType.FIREBALL))
  }

  @Test def theTongueAndTheBandagePullSoTheyAreTiedToWhoeverThrewThem(): Unit = {
    assertTrue(wantsAnchor(ProjectileType.TONGUE))
    assertTrue(wantsAnchor(ProjectileType.BANDAGE_WHIP))
  }

  @Test def everyPullToTheThrowerIsTiedToThem(): Unit = {
    // A pull is drawn on a rope, a limb or a surge back to whoever threw it, and reeled in along it. The
    // two that aren't are a ball of gravity and a scythe's swing, which nothing holds
    val untied = Set(ProjectileType.GRAVITY_BALL, ProjectileType.REAP)
    val pulls = attacks.map(_._2).distinct.filter(t => ProjectileDef.get(t).onHitEffect.contains(PullToOwner))
    assertTrue(pulls.size >= 10)
    pulls.filterNot(untied).foreach(t => assertTrue(s"type $t pulls but is drawn flying free", wantsAnchor(t)))
  }

  @Test def aSharedPullOrStrikeIsDrawnAsEachThrowersOwn(): Unit = {
    import CharacterId._
    import ProjectileType._
    // Each was one look in every thrower's hands: the Kraken's tentacle the Spaceman's green, the Gorilla's
    // grab the Bear's paw, the Gladiator's and the Puppeteer's the Pirate's grappling hook, every bird's
    // kick the same brown ball with three sticks, and the Bear's claws the Wolf's
    val shared = Seq(
      TENTACLE -> Seq(Spaceman, Kraken), GRAB -> Seq(Bear, Gorilla), VINE_WHIP -> Seq(Druid, Thornweaver),
      ROPE -> Seq(Pirate, Gladiator, Puppeteer), TALON -> Seq(Raptor, Hawk, Griffin), CLAW_SWIPE -> Seq(Wolf, Bear))
    shared.foreach { case (t, who) =>
      who.foreach(c => assertTrue(s"${c.name} doesn't throw type $t", attacks.exists { case (d, u) => d.id == c && u == t }))
      for (a <- who; b <- who if a != b)
        assertNotSame(s"${a.name} and ${b.name} draw type $t alike", getRenderer(a.id, t), getRenderer(b.id, t))
    }
  }

  @Test def aThrowerWithNoLookOfTheirOwnGetsTheTypes(): Unit = {
    val sonic = ProjectileType.SONIC_WAVE
    assertSame(getRenderer(sonic), getRenderer(-1.toByte, sonic))
    assertSame(getRenderer(sonic), getRenderer(CharacterId.Bard.id, sonic))
    assertSame(getRenderer(sonic), getRenderer(CharacterId.Gorilla.id, sonic))
    assertNotSame(getRenderer(sonic), getRenderer(CharacterId.Musician.id, sonic))
    // and a type nobody has a look of their own for is the type's, whoever throws it
    assertSame(getRenderer(ProjectileType.FIREBALL), getRenderer(CharacterId.Musician.id, ProjectileType.FIREBALL))
  }
}
