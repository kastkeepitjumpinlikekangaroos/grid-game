package com.gridgame.client.audio

import com.gridgame.common.model._

/** Which sound every attack, every cast and every blast makes.
  *
  * A character's three attacks each have a sound of their own, as each has a look of its own
  * (render/projectiles/CLAUDE.md, *A projectile is drawn as its thrower's*): `hears` is the table,
  * a row a character — its primary, its Q, its E. An attack that throws a projectile is heard when
  * the projectile is first seen (`forAttack`, by its type and whose it is); one that throws nothing
  * — a dash, a blink, a phase, a barrier, a trap — when it is cast (`forCast`), or when another
  * player is seen to have cast it (`forCastOf`, `forTrap`).
  *
  * Under the table is `byType`: each projectile type's sound in nobody's hands, for a projectile
  * whose thrower the client doesn't know yet, or one a character throws that is none of its three
  * attacks' own. It is always one of its throwers' sounds.
  *
  * An explosive's detonation is `forBlast`, by what blew up and whose it was: a fire, a mud bomb
  * and an acid flask each go off as themselves, a pirate's cannonball as a cannonball, and
  * grenades as the frag (`explosion`).
  *
  * The files are sounds/atk_*.wav, cast_*.wav and blast_*.wav, made by scripts/generate_sounds.py,
  * which refuses to run if this file names a sound it doesn't make or it makes one this file
  * doesn't name: add a sound there and here together. `python3 scripts/sound_roster.py` prints
  * this table as the game resolves it. */
object AbilitySounds {
  private val NO_CHAR: Byte = -1

  /** The kinds of ability that throw nothing, for `forCast` and `forCastOf`. */
  final val DASH = 0
  final val PHASE = 1
  final val BARRIER = 2
  final val TELEPORT = 3
  final val TRAP = 4

  // What each kind sounds like for a character with no row (there is none, but a test's or a
  // future character's cast must still make a sound)
  private val plainCast = Array("dash", "phase_shift", "barrier_up", "teleport", "trap_place")

  private val byType: Array[String] = new Array[String](256)
  private val overrides = new java.util.HashMap[Int, String]()      // (character, projectile type)
  private val casts = new java.util.HashMap[Int, String]()          // (character, slot 0 = Q, 1 = E)
  private val castKinds = new java.util.HashMap[Int, Integer]()     // (character, slot) -> its kind
  private val traps = new java.util.HashMap[Int, String]()          // (character, trap type)
  private val rows = new java.util.LinkedHashMap[java.lang.Byte, Array[String]]()
  private val blastByType: Array[String] = new Array[String](256)
  private val blastOverrides = new java.util.HashMap[Int, String]() // (character, projectile type)

  /** The frag's bang: every explosive without a detonation of its own. */
  val FragBlast = "explosion"

  private def key(characterId: Byte, n: Int): Int = ((characterId & 0xFF) << 8) | (n & 0xFF)

  private def set(sound: String, ids: Byte*): Unit =
    ids.foreach(id => byType(id & 0xFF) = sound)

  private def blast(sound: String, ids: Byte*): Unit =
    ids.foreach(id => blastByType(id & 0xFF) = sound)

  private def blastOf(sound: String, character: CharacterId, id: Byte): Unit =
    blastOverrides.put(key(character.id, id), sound)

  /** Which kind of cast `cast` is (DASH...), or -1 for one that throws a projectile. */
  def kindOf(cast: CastBehavior): Int = cast match {
    case _: DashBuff => DASH
    case _: PhaseShiftBuff => PHASE
    case _: BarrierCast => BARRIER
    case _: TeleportCast => TELEPORT
    case _: TrapCast => TRAP
    case _ => -1
  }

  /** One character's three attacks. Each of `q` and `e` is filed by what its ability does: under
    * its projectile's type if it throws one, under its slot if it throws nothing. */
  private def hears(character: CharacterId, primary: String, q: String, e: String): Unit = {
    val c = CharacterDef.get(character)
    rows.put(character.id, Array(primary, q, e))
    overrides.put(key(character.id, c.primaryProjectileType), primary)
    var slot = 0
    while (slot < 2) {
      val ability = if (slot == 0) c.qAbility else c.eAbility
      val sound = if (slot == 0) q else e
      val kind = kindOf(ability.castBehavior)
      if (kind < 0) overrides.putIfAbsent(key(character.id, ability.projectileType), sound)
      else {
        casts.put(key(character.id, slot), sound)
        castKinds.put(key(character.id, slot), kind)
        ability.castBehavior match {
          case TrapCast(trapType, _) => traps.put(key(character.id, trapType), sound)
          case _ =>
        }
      }
      slot += 1
    }
  }

  // ═══ each projectile type, in nobody's hands ═══════════════════════════════════════════════

  // ── magic bolts ──
  set("atk_normal_bolt", ProjectileType.NORMAL)
  set("atk_arcane_bolt", ProjectileType.ARCANE_BOLT)
  set("atk_mystic_bolt", ProjectileType.MYSTIC_BOLT)
  set("atk_rune_bolt", ProjectileType.RUNE_BOLT)
  set("atk_star_bolt", ProjectileType.STAR_BOLT)
  set("atk_soul_bolt", ProjectileType.SOUL_BOLT, ProjectileType.SOUL_BOLT_HEAVY)
  set("atk_haunt_bolt", ProjectileType.HAUNT)
  set("atk_death_bolt", ProjectileType.DEATH_BOLT)
  set("atk_death_coil", ProjectileType.DEATH_COIL)
  set("atk_leech_bolt", ProjectileType.LEECH_BOLT)
  set("atk_shadow_bolt", ProjectileType.SHADOW_BOLT)
  set("atk_shadow_haunt", ProjectileType.SHADOW_HAUNT)
  set("atk_shadow_bind", ProjectileType.SHADOW_BIND)
  set("atk_plague_bolt", ProjectileType.PLAGUE_BOLT)
  set("atk_venom_bolt", ProjectileType.VENOM_BOLT, ProjectileType.VENOM_BOLT_LIGHT)
  set("atk_flame_bolt", ProjectileType.FLAME_BOLT, ProjectileType.FLAME_BOLT_HEAVY,
    ProjectileType.FLAME_BOLT_LIGHT)
  set("atk_ember_shot", ProjectileType.EMBER_SHOT)
  set("atk_ember_fan", ProjectileType.EMBER_FAN)
  set("atk_magma_ball", ProjectileType.MAGMA_BALL)
  set("atk_frost_shard", ProjectileType.FROST_SHARD, ProjectileType.FROST_SHARD_LIGHT)
  set("atk_ricochet_shard", ProjectileType.RICOCHET_SHARD)
  set("atk_ice_spike", ProjectileType.GLACIER_SPIKE, ProjectileType.FROSTBITE)
  set("atk_mud_glob", ProjectileType.MUD_GLOB)
  set("atk_sand_shot", ProjectileType.SAND_SHOT)
  set("atk_charm_bolt", ProjectileType.CHARM)
  set("atk_sphinx_riddle", ProjectileType.RIDDLE)
  set("atk_nano_bolt", ProjectileType.NANO_BOLT)
  set("atk_echo_bolt", ProjectileType.ECHO_BOLT)
  set("atk_sting", ProjectileType.STING)
  set("atk_holy_bolt", ProjectileType.HOLY_BOLT, ProjectileType.HOLY_BOLT_HEAVY)
  set("atk_judgment", ProjectileType.HOLY_NOVA)
  set("atk_smite", ProjectileType.SMITE)
  set("atk_divine_judgment", ProjectileType.DIVINE_JUDGMENT)
  set("atk_mummy_curse", ProjectileType.CURSE)
  set("atk_binding_talisman", ProjectileType.BINDING_TALISMAN)
  set("atk_void_bolt", ProjectileType.VOID_BOLT)
  set("atk_gravity_ball", ProjectileType.GRAVITY_BALL, ProjectileType.GRAVITY_LOCK)
  set("atk_temporal_rift", ProjectileType.GRAVITY_WELL)
  set("atk_repulse", ProjectileType.REPULSE)
  set("atk_data_bolt", ProjectileType.DATA_BOLT)
  set("atk_virus_glitch", ProjectileType.VIRUS)
  set("atk_lag_spike", ProjectileType.LAG_SPIKE)

  // ── beams, drains and gazes ──
  set("atk_beam_ice", ProjectileType.ICE_BEAM)
  set("atk_beam_laser", ProjectileType.LASER, ProjectileType.LASER_HEAVY, ProjectileType.LASER_LIGHT)
  set("atk_ion_shot", ProjectileType.ION_SHOT)
  set("atk_beam_railgun", ProjectileType.RAILGUN)
  set("atk_sniper_beam", ProjectileType.SNIPER_BEAM)
  set("atk_soul_drain", ProjectileType.SOUL_DRAIN)
  set("atk_life_drain", ProjectileType.LIFE_DRAIN, ProjectileType.BLOOD_SIPHON)
  set("atk_petrify", ProjectileType.PETRIFY)
  set("atk_stone_gaze", ProjectileType.STONE_GAZE)
  set("atk_gravity_lance", ProjectileType.GRAVITY_LANCE)
  set("atk_eye_beam", ProjectileType.EYE_BEAM)

  // ── blades, swung and thrown ──
  set("atk_barbarian_axe", ProjectileType.AXE)
  set("atk_arena_axe", ProjectileType.ARENA_AXE)
  set("atk_axe_spin", ProjectileType.AXE_SPIN)
  set("atk_hammer_swing", ProjectileType.HAMMER)
  set("atk_hammer_throw", ProjectileType.HAMMER_THROW)
  set("atk_bone_axe", ProjectileType.BONE_AXE)
  set("atk_bone_cage", ProjectileType.BONE_THROW)
  set("atk_bone_boomerang", ProjectileType.BONE_BOOMERANG)
  set("atk_shuriken", ProjectileType.SHURIKEN)
  set("atk_throwing_star", ProjectileType.THROWING_STAR)
  set("atk_knife_spray", ProjectileType.TOXIC_SHURIKEN)
  set("atk_chef_knife", ProjectileType.KNIFE)
  set("atk_jester_card", ProjectileType.CARD)
  set("atk_gambler_card", ProjectileType.CARD_HEAVY)
  set("atk_wild_card", ProjectileType.WILD_CARD)
  set("atk_lucky_streak", ProjectileType.LUCKY_STREAK)
  set("atk_cursed_blade", ProjectileType.CURSED_BLADE)
  set("atk_drain_blade", ProjectileType.DRAIN_BLADE)
  set("atk_deathknight_blade", ProjectileType.CHILL_BLADE)
  set("atk_holy_blade", ProjectileType.HOLY_BLADE)
  set("atk_boomerang_blade", ProjectileType.BOOMERANG_BLADE)
  set("atk_shovel_swing", ProjectileType.SHOVEL)
  set("atk_horn_toss", ProjectileType.HEAD_THROW)
  set("atk_katana_slash", ProjectileType.KATANA)
  set("atk_crescent_cut", ProjectileType.SWORD_WAVE)
  set("atk_scythe_swing", ProjectileType.SCYTHE)
  set("atk_reap_slash", ProjectileType.REAP)

  // ── claws, jaws and fists ──
  set("atk_claw_swipe", ProjectileType.CLAW_SWIPE)
  set("atk_bear_maul", ProjectileType.MAUL)
  set("atk_talon_swipe", ProjectileType.TALON)
  set("atk_pin_down", ProjectileType.PIN_DOWN)
  set("atk_mantis_scythe", ProjectileType.MANTIS_SCYTHE)
  set("atk_fenrir_rend", ProjectileType.FENRIR_CLAW)
  set("atk_blood_frenzy", ProjectileType.BLOOD_FRENZY)
  set("atk_ghoul_rake", ProjectileType.GHOUL_CLAW)
  set("atk_shark_rush", ProjectileType.SHARK_CLAW)
  set("atk_jaw_chomp", ProjectileType.JAW)
  set("atk_devour_bite", ProjectileType.DEVOUR)
  set("atk_blood_fang", ProjectileType.BLOOD_FANG)
  set("atk_bat_swarm", ProjectileType.BAT_SWARM)
  set("atk_horn_gore", ProjectileType.HORN)
  set("atk_stinger", ProjectileType.STINGER)
  set("atk_paralytic_sting", ProjectileType.PARALYTIC_STING)
  set("atk_shift_strike", ProjectileType.FIST)
  set("atk_charge_fist", ProjectileType.CHARGE_FIST)
  set("atk_flurry", ProjectileType.FLURRY)
  set("atk_momentum_strike", ProjectileType.MOMENTUM_STRIKE)

  // ── shafts ──
  set("atk_spear_thrust", ProjectileType.SPEAR)
  set("atk_pinning_spear", ProjectileType.PINNING_SPEAR)
  set("atk_arrow_shot", ProjectileType.ARROW, ProjectileType.ARROW_HEAVY, ProjectileType.ARROW_LIGHT)
  set("atk_poison_arrow", ProjectileType.POISON_ARROW)
  set("atk_thorn_shot", ProjectileType.THORN, ProjectileType.THORN_LIGHT)
  set("atk_poison_dart", ProjectileType.POISON_DART, ProjectileType.VENOM_DART)
  set("atk_serpent_strike", ProjectileType.SERPENT_STRIKE)
  set("atk_web_shot", ProjectileType.WEB_SHOT)

  // ── guns and ordnance ──
  set("atk_gunshot", ProjectileType.BULLET)
  set("atk_pilot_guns", ProjectileType.BULLET_HEAVY)
  set("atk_cyborg_gun", ProjectileType.BULLET_LIGHT)
  set("atk_rocket_launch", ProjectileType.ROCKET)
  set("atk_grenade_lob", ProjectileType.GRENADE)
  set("atk_cluster_bomb", ProjectileType.CLUSTER_BOMB)
  set("atk_napalm_strike", ProjectileType.NAPALM_STRIKE)
  set("atk_acid_flask", ProjectileType.ACID_FLASK, ProjectileType.ACID_BOMB)
  set("atk_mud_bomb", ProjectileType.MUD_BOMB)
  set("atk_blight_bomb", ProjectileType.BLIGHT_BOMB)
  set("atk_ink_snare", ProjectileType.INK_SNARE)
  set("atk_overclock", ProjectileType.OVERCLOCK_BEAM)

  // ── the ground, and what comes out of it ──
  set("atk_tremor_slam", ProjectileType.TREMOR_SLAM)
  set("atk_ground_pound", ProjectileType.SEISMIC_SLAM)
  set("atk_earthsplitter", ProjectileType.EARTHSPLITTER)
  set("atk_seismic_root", ProjectileType.SEISMIC_ROOT)
  set("atk_root_burst", ProjectileType.ROOT_GROWTH)
  set("atk_entangle", ProjectileType.ENTANGLE, ProjectileType.THORN_WALL)
  set("atk_grasping_dead", ProjectileType.GRASPING_DEAD)
  set("atk_raise_dead", ProjectileType.RAISE_DEAD)
  set("atk_soul_harvest", ProjectileType.SOUL_HARVEST)
  set("atk_shield_bash", ProjectileType.SHOCKWAVE)
  set("atk_horn_flip", ProjectileType.HORN_FLIP)
  set("atk_ice_quake", ProjectileType.ICE_QUAKE)
  set("atk_avalanche_crush", ProjectileType.AVALANCHE_CRUSH)
  set("atk_boulder_rumble", ProjectileType.BOULDER)
  set("atk_giant_boulder", ProjectileType.THROWN_BOULDER)
  set("atk_hurl_boulder", ProjectileType.ROOTING_BOULDER)
  set("atk_ice_chunk", ProjectileType.ICE_BOULDER)
  set("atk_meteor", ProjectileType.METEOR)
  set("atk_eruption", ProjectileType.ERUPTION)

  // ── fire, frost, water, weather ──
  set("atk_fireball", ProjectileType.FIREBALL)
  set("atk_combust", ProjectileType.COMBUST)
  set("atk_flambe", ProjectileType.FLAMBE)
  set("atk_inferno_blast", ProjectileType.INFERNO_BLAST)
  set("atk_flame_wave", ProjectileType.FLAME_WAVE)
  set("atk_flame_trail", ProjectileType.FLAME_TRAIL)
  set("atk_frost_nova", ProjectileType.FROST_NOVA, ProjectileType.FROST_TRAP)
  set("atk_splash", ProjectileType.SPLASH)
  set("atk_tidal_wave", ProjectileType.TIDAL_WAVE)
  set("atk_geyser_burst", ProjectileType.GEYSER)
  set("atk_gust", ProjectileType.GUST)
  set("atk_wind_blade", ProjectileType.WIND_BLADE)
  set("atk_gale", ProjectileType.GALE)
  set("atk_hailstorm", ProjectileType.HAILSTORM)
  set("atk_sand_blast", ProjectileType.SAND_BLAST)
  set("atk_acid_spray", ProjectileType.ACID_SPRAY)
  set("atk_lightning_crack", ProjectileType.LIGHTNING)
  set("atk_chain_lightning", ProjectileType.CHAIN_LIGHTNING, ProjectileType.CHAIN_LIGHTNING_FORK,
    ProjectileType.TESLA_COIL)
  set("atk_electric_burst", ProjectileType.SHOCK_BURST)
  set("atk_thunder_strike", ProjectileType.THUNDER_STRIKE)
  set("atk_miasma", ProjectileType.MIASMA)
  set("atk_poison_cloud", ProjectileType.POISON_CLOUD)
  set("atk_vortex_bomb", ProjectileType.VORTEX_BOMB)
  set("atk_djinn_whirlwind", ProjectileType.WHIRLWIND)

  // ── voices and songs ──
  set("atk_lute_chord", ProjectileType.SONIC_WAVE)
  set("atk_banshee_cry", ProjectileType.SONIC_WAVE_HEAVY)
  set("atk_harpy_screech", ProjectileType.SONIC_WAVE_MED)
  set("atk_wail_shriek", ProjectileType.WAIL)
  set("atk_lullaby", ProjectileType.LULLABY)
  set("atk_bard_boom", ProjectileType.SONIC_BOOM)
  set("atk_howl", ProjectileType.HOWL)
  set("atk_feral_roar", ProjectileType.FERAL_ROAR)

  // ── chains, ropes and reaching things ──
  set("atk_chain_bolt", ProjectileType.CHAIN_BOLT)
  set("atk_lockdown", ProjectileType.LOCKDOWN_CHAIN)
  set("atk_shackles", ProjectileType.SHACKLES)
  set("atk_death_grip", ProjectileType.DEATH_GRIP)
  set("atk_puppet_string", ProjectileType.CHAIN_BOLT_HEAVY)
  set("atk_marionette", ProjectileType.MARIONETTE)
  set("atk_grapple", ProjectileType.ROPE)
  set("atk_lasso", ProjectileType.LASSO)
  set("atk_meat_hook", ProjectileType.MEAT_HOOK)
  set("atk_vine_whip", ProjectileType.VINE_WHIP)
  set("atk_bandage_whip", ProjectileType.BANDAGE_WHIP)
  set("atk_tentacle_slap", ProjectileType.TENTACLE)
  set("atk_tongue_lash", ProjectileType.TONGUE)
  set("atk_bear_hug", ProjectileType.GRAB)
  set("atk_talon_grab", ProjectileType.TALON_GRAB)
  set("atk_fling", ProjectileType.FLING)

  // ── retired types (nothing throws them now; each keeps the nearest sound) ──
  set("cast_snare_mine", ProjectileType.SNARE_MINE)
  set("cast_web_trap", ProjectileType.WEB_TRAP)

  // ═══ every character's three attacks ══════════════════════════════════════════════════════
  // A row a character: its primary, its Q, its E. A sound is shared only where the thing is the
  // same thing in the same hands (the Railgunner's frag grenade is the Soldier's; the Pyromancer's
  // fireball is the Wizard's).

  // ── the first twelve ──
  hears(CharacterId.Spaceman,        "atk_normal_bolt",        "atk_alien_tentacle",     "atk_freeze_ray")
  hears(CharacterId.Gladiator,       "atk_arena_axe",          "cast_scutum",            "atk_lasso")
  hears(CharacterId.Wraith,          "atk_soul_bolt",          "cast_phase_shift",       "atk_haunt_bolt")
  hears(CharacterId.Wizard,          "atk_arcane_bolt",        "atk_fireball",           "cast_blink")
  hears(CharacterId.Tidecaller,      "atk_splash",             "atk_tidal_wave",         "atk_geyser_burst")
  hears(CharacterId.Soldier,         "atk_gunshot",            "atk_grenade_lob",        "atk_rocket_launch")
  hears(CharacterId.Raptor,          "atk_talon_swipe",        "cast_swoop",             "atk_gust")
  hears(CharacterId.Assassin,        "atk_throwing_star",      "atk_poison_dart",        "cast_smoke_bomb")
  hears(CharacterId.Warden,          "atk_chain_bolt",         "atk_lockdown",           "cast_snare_mine")
  hears(CharacterId.Samurai,         "atk_katana_slash",       "cast_iaijutsu",          "atk_crescent_cut")
  hears(CharacterId.PlagueDoctor,    "atk_plague_bolt",        "atk_miasma",             "atk_blight_bomb")
  hears(CharacterId.Vampire,         "atk_blood_fang",         "cast_mist_form",         "atk_bat_swarm")

  // ── elementals ──
  hears(CharacterId.Pyromancer,      "atk_flame_bolt",         "atk_fireball",           "atk_combust")
  hears(CharacterId.Cryomancer,      "atk_frost_shard",        "atk_beam_ice",           "atk_frost_nova")
  hears(CharacterId.Stormcaller,     "atk_lightning_crack",    "atk_chain_lightning",    "atk_thunder_strike")
  hears(CharacterId.Earthshaker,     "atk_boulder_rumble",     "atk_seismic_root",       "cast_ground_charge")
  hears(CharacterId.Windwalker,      "atk_wind_blade",         "atk_gale",               "cast_wind_form")
  hears(CharacterId.MagmaKnight,     "atk_magma_ball",         "cast_magma_charge",      "atk_eruption")
  hears(CharacterId.Frostbite,       "atk_frostbite_shard",    "atk_ice_spike",          "cast_ice_blink")
  hears(CharacterId.Sandstorm,       "atk_sand_shot",          "atk_sand_blast",         "cast_dust_devil")
  hears(CharacterId.Thornweaver,     "atk_thorn_shot",         "atk_vine_whip",          "atk_entangle")
  hears(CharacterId.Cloudrunner,     "atk_cloud_puff",         "atk_hailstorm",          "cast_tailwind")
  hears(CharacterId.Inferno,         "atk_flame_wave",         "atk_inferno_blast",      "atk_fire_vortex")
  hears(CharacterId.Glacier,         "atk_glacier_shard",      "atk_ricochet_shard",     "cast_ice_wall")
  hears(CharacterId.Mudslinger,      "atk_mud_glob",           "atk_mud_bomb",           "cast_bog_hop")
  hears(CharacterId.Ember,           "atk_ember_shot",         "atk_ember_fan",          "cast_flame_burst")
  hears(CharacterId.Avalanche,       "atk_ice_chunk",          "atk_avalanche_crush",    "atk_ice_quake")

  // ── the undead ──
  hears(CharacterId.Necromancer,     "atk_death_bolt",         "atk_raise_dead",         "atk_soul_harvest")
  hears(CharacterId.SkeletonKing,    "atk_bone_axe",           "atk_bone_boomerang",     "atk_bone_cage")
  hears(CharacterId.Banshee,         "atk_banshee_cry",        "atk_echo_bolt",          "atk_wail_shriek")
  hears(CharacterId.Lich,            "atk_leech_bolt",         "atk_grave_ice",          "atk_soul_drain")
  hears(CharacterId.Ghoul,           "atk_ghoul_rake",         "cast_ghoul_lunge",       "atk_devour_bite")
  hears(CharacterId.Reaper,          "atk_scythe_swing",       "atk_reap_slash",         "cast_death_dash")
  hears(CharacterId.Shade,           "atk_shadow_bolt",        "atk_shadow_bind",        "cast_shadow_step")
  hears(CharacterId.Revenant,        "atk_drain_blade",        "cast_spectral_charge",   "atk_life_drain")
  hears(CharacterId.Gravedigger,     "atk_shovel_swing",       "atk_grasping_dead",      "cast_open_grave")
  hears(CharacterId.Dullahan,        "atk_cursed_blade",       "atk_boomerang_blade",    "cast_headless_charge")
  hears(CharacterId.Phantom,         "atk_phantom_blade",      "atk_shadow_haunt",       "cast_phase_out")
  hears(CharacterId.Mummy,           "atk_scarab_shot",        "atk_bandage_whip",       "atk_mummy_curse")
  hears(CharacterId.Deathknight,     "atk_deathknight_blade",  "atk_death_grip",         "cast_unholy_charge")
  hears(CharacterId.Shadowfiend,     "atk_fiend_eye",          "atk_shadow_burst",       "atk_void_maw")
  hears(CharacterId.Poltergeist,     "atk_poltergeist_hurl",   "atk_fling",              "cast_polter_blink")

  // ── the medieval ──
  hears(CharacterId.Paladin,         "atk_holy_blade",         "cast_aegis",             "atk_judgment")
  hears(CharacterId.Ranger,          "atk_arrow_shot",         "atk_poison_arrow",       "atk_multi_shot")
  hears(CharacterId.Berserker,       "atk_berserk_axe",        "cast_rage_charge",       "atk_axe_spin")
  hears(CharacterId.Crusader,        "atk_crusader_blade",     "cast_bulwark",           "atk_shield_bash")
  hears(CharacterId.Druid,           "atk_druid_thorn",        "atk_druid_vine",         "cast_natures_leap")
  hears(CharacterId.Bard,            "atk_lute_chord",         "atk_lullaby",            "atk_bard_boom")
  hears(CharacterId.Monk,            "atk_charge_fist",        "cast_palm_strike",       "atk_flurry")
  hears(CharacterId.Cleric,          "atk_holy_bolt",          "atk_smite",              "atk_divine_judgment")
  hears(CharacterId.Rogue,           "atk_shuriken",           "atk_knife_spray",        "cast_rogue_step")
  hears(CharacterId.Barbarian,       "atk_barbarian_axe",      "cast_leap",              "atk_earthsplitter")
  hears(CharacterId.Enchantress,     "atk_charm_bolt",         "atk_charm_bolt",         "cast_ench_blink")
  hears(CharacterId.Jester,          "atk_jester_card",        "atk_card_fan",           "cast_trick")
  hears(CharacterId.Valkyrie,        "atk_spear_thrust",       "cast_aerial_charge",     "atk_pinning_spear")
  hears(CharacterId.Warlock,         "atk_warlock_bolt",       "atk_death_coil",         "cast_warlock_step")
  hears(CharacterId.Inquisitor,      "atk_inquisitor_bolt",    "atk_inq_chain",          "atk_shackles")

  // ── science fiction ──
  hears(CharacterId.Cyborg,          "atk_cyborg_gun",         "atk_arm_rocket",         "atk_overclock")
  hears(CharacterId.Hacker,          "atk_data_bolt",          "atk_virus_glitch",       "cast_system_hop")
  hears(CharacterId.MechPilot,       "atk_mech_gun",           "cast_jump_jets",         "atk_mech_slam")
  hears(CharacterId.Android,         "atk_beam_laser",         "atk_ion_shot",           "cast_jet_dash")
  hears(CharacterId.Chronomancer,    "atk_time_bolt",          "atk_temporal_rift",      "cast_time_stop")
  hears(CharacterId.Graviton,        "atk_gravity_ball",       "atk_repulse",            "atk_vortex_bomb")
  hears(CharacterId.Tesla,           "atk_tesla_arc",          "atk_tesla_chain",        "atk_tesla_fork")
  hears(CharacterId.Nanoswarm,       "atk_nano_bolt",          "cast_disperse",          "atk_nano_burst")
  hears(CharacterId.Voidwalker,      "atk_void_bolt",          "atk_gravity_lance",      "cast_void_step")
  hears(CharacterId.Photon,          "atk_photon_beam",        "cast_light_speed",       "atk_prism")
  hears(CharacterId.Railgunner,      "atk_sniper_beam",        "atk_grenade_lob",        "atk_beam_railgun")
  hears(CharacterId.Bombardier,      "atk_comic_bomb",         "atk_rocket_launch",      "atk_cluster_bomb")
  hears(CharacterId.Sentinel,        "atk_turret_laser",       "atk_suppress_fire",      "cast_deploy_mine")
  hears(CharacterId.Pilot,           "atk_pilot_guns",         "atk_strafing_run",       "atk_napalm_strike")
  hears(CharacterId.Glitcher,        "atk_glitch_bolt",        "cast_glitch_blink",      "atk_lag_spike")

  // ── beasts ──
  hears(CharacterId.Wolf,            "atk_claw_swipe",         "atk_momentum_strike",    "atk_howl")
  hears(CharacterId.Serpent,         "atk_venom_bolt",         "atk_venom_spit",         "cast_slither")
  hears(CharacterId.Spider,          "atk_web_shot",           "cast_burrow",            "cast_web_trap")
  hears(CharacterId.Bear,            "atk_bear_swipe",         "atk_bear_hug",           "atk_bear_maul")
  hears(CharacterId.Scorpion,        "atk_stinger",            "atk_paralytic_sting",    "atk_acid_spray")
  hears(CharacterId.Hawk,            "atk_hawk_dive",          "cast_dive_bomb",         "atk_pin_down")
  hears(CharacterId.Shark,           "atk_shark_rush",         "atk_jaw_chomp",          "cast_deep_dive")
  hears(CharacterId.Beetle,          "atk_shell_charge",       "cast_carapace",          "atk_horn_flip")
  hears(CharacterId.Treant,          "atk_treant_bark",        "atk_root_pull",          "atk_root_burst")
  hears(CharacterId.Phoenix,         "atk_phoenix_flare",      "atk_flame_trail",        "cast_rebirth")
  hears(CharacterId.Hydra,           "atk_hydra_spit",         "atk_hydra_heads",        "atk_poison_cloud")
  hears(CharacterId.Mantis,          "atk_mantis_scythe",      "cast_leaf_cloak",        "atk_mantis_strike")
  hears(CharacterId.Jellyfish,       "atk_sting",              "atk_electric_burst",     "cast_drift")
  hears(CharacterId.Gorilla,         "atk_ape_throw",          "atk_primate_grab",       "atk_ground_pound")
  hears(CharacterId.Chameleon,       "atk_chameleon_dart",     "cast_camouflage",        "atk_tongue_lash")

  // ── the myths ──
  hears(CharacterId.Minotaur,        "atk_horn_gore",          "cast_bull_charge",       "atk_horn_toss")
  hears(CharacterId.Medusa,          "atk_medusa_spit",        "atk_petrify",            "atk_stone_gaze")
  hears(CharacterId.Cerberus,        "atk_hellhound_spit",     "atk_triple_blast",       "cast_hellfire_pounce")
  hears(CharacterId.Centaur,         "atk_centaur_arrow",      "atk_lance",              "cast_gallop")
  hears(CharacterId.Kraken,          "atk_tentacle_slap",      "cast_ink_cloud",         "atk_ink_snare")
  hears(CharacterId.Sphinx,          "atk_sphinx_charm",       "cast_pounce",            "atk_sphinx_riddle")
  hears(CharacterId.Cyclops,         "atk_giant_boulder",      "atk_hurl_boulder",       "atk_eye_beam")
  hears(CharacterId.Harpy,           "atk_harpy_screech",      "atk_harpy_shriek",       "cast_sky_dance")
  hears(CharacterId.Griffin,         "atk_griffin_rake",       "cast_sky_dive",          "atk_talon_grab")
  hears(CharacterId.Anubis,          "atk_anubis_bolt",        "atk_anubis_curse",       "atk_scales_judgment")
  hears(CharacterId.Yokai,           "atk_mystic_bolt",        "cast_illusion",          "atk_binding_talisman")
  hears(CharacterId.Golem,           "atk_stone_fist",         "cast_stone_wall",        "atk_tremor_slam")
  hears(CharacterId.Djinn,           "atk_djinn_bolt",         "atk_djinn_whirlwind",    "cast_mirage")
  hears(CharacterId.Fenrir,          "atk_fenrir_rend",        "cast_savage_leap",       "atk_blood_frenzy")
  hears(CharacterId.Chimera,         "atk_chimera_breath",     "atk_serpent_strike",     "cast_lion_charge")

  // ── the specialists ──
  hears(CharacterId.Alchemist,       "atk_alchemist_vial",     "atk_explosive_potion",   "atk_acid_flask")
  hears(CharacterId.Puppeteer,       "atk_puppet_string",      "atk_puppet_pull",        "atk_marionette")
  hears(CharacterId.Gambler,         "atk_gambler_card",       "atk_lucky_streak",       "atk_wild_card")
  hears(CharacterId.Blacksmith,      "atk_hammer_swing",       "atk_hammer_throw",       "cast_anvil_trap")
  hears(CharacterId.Pirate,          "atk_flintlock",          "atk_grapple",            "atk_cannonball")
  hears(CharacterId.Chef,            "atk_chef_knife",         "atk_meat_hook",          "atk_flambe")
  hears(CharacterId.Musician,        "atk_song_wave",          "atk_hypnotic_melody",    "atk_musician_boom")
  hears(CharacterId.Astronomer,      "atk_star_bolt",          "atk_meteor",             "cast_astral_project")
  hears(CharacterId.Runesmith,       "atk_rune_bolt",          "cast_binding_rune",      "cast_rune_trap")
  hears(CharacterId.Shapeshifter,    "atk_shift_strike",       "cast_beast_charge",      "atk_feral_roar")

  // ═══ detonations ══════════════════════════════════════════════════════════════════════════
  // Every damaging explosion used to play the one frag grenade: napalm, a mud bomb and an acid
  // flask all went off with the same bang, and since a thrown bomb's own sound is only the throw,
  // the bang was all there was of its landing.
  blast("blast_rocket", ProjectileType.ROCKET)
  blast("blast_fire", ProjectileType.INFERNO_BLAST, ProjectileType.NAPALM_STRIKE)
  blast("blast_magma", ProjectileType.ERUPTION)
  blast("blast_toxic", ProjectileType.MIASMA, ProjectileType.BLIGHT_BOMB)
  blast("blast_acid", ProjectileType.ACID_FLASK, ProjectileType.ACID_BOMB)
  blast("blast_mud", ProjectileType.MUD_BOMB)
  blast("blast_cluster", ProjectileType.CLUSTER_BOMB)
  // The same projectile, and not the same explosive: the Pirate's GRENADE is a cannonball and the
  // Bombardier's a round black bomb with a fuse
  blastOf("blast_cannonball", CharacterId.Pirate, ProjectileType.GRENADE)
  blastOf("blast_bomb", CharacterId.Bombardier, ProjectileType.GRENADE)

  // ═══ pitch ═════════════════════════════════════════════════════════════════════════════════
  // Every sound is played a little faster or slower each time, so rapid fire isn't one sample
  // repeated. These have a pitch that is the point of them — strings, voices on a chord, chimes,
  // a flute, glass — and are all in the music's key: they get a fraction of the spread, since a
  // lute a semitone flat on every other strum is a joke at the Bard's expense.
  private val tuned: java.util.Set[String] = new java.util.HashSet[String](java.util.Arrays.asList(
    "atk_lute_chord", "atk_lullaby", "atk_bard_boom", "atk_song_wave", "atk_hypnotic_melody", "atk_musician_boom",
    "atk_charm_bolt", "cast_ench_blink", "atk_puppet_string", "atk_puppet_pull", "atk_marionette",
    "atk_lucky_streak", "atk_wild_card", "atk_star_bolt", "cast_astral_project",
    "atk_holy_bolt", "atk_holy_blade", "atk_judgment", "atk_smite", "atk_divine_judgment", "cast_aegis",
    "atk_inquisitor_bolt", "atk_arcane_bolt", "cast_blink", "atk_rune_bolt", "cast_binding_rune",
    "atk_mystic_bolt", "atk_djinn_bolt", "atk_binding_talisman", "cast_illusion", "cast_mirage",
    "atk_sphinx_charm", "atk_sphinx_riddle", "atk_frost_shard", "atk_frostbite_shard", "atk_glacier_shard",
    "atk_ricochet_shard", "atk_grave_ice", "atk_frost_nova", "cast_ice_blink", "cast_ice_wall", "atk_prism",
    "atk_howl", "atk_banshee_cry", "atk_echo_bolt", "atk_wail_shriek", "cast_deploy_mine", "cast_time_stop",
    "spawn", "death", "hit_dealt", "cooldown_reset", "barrier_up"))

  /** Has `sound` a pitch that must stay where it is? */
  def isTuned(sound: String): Boolean = tuned.contains(sound)

  // ═══ lookups ═══════════════════════════════════════════════════════════════════════════════

  /** What a projectile of `projectileType` sounds like as it is thrown.
    * @param characterId the thrower's character, or -1 when it isn't known. */
  def forAttack(projectileType: Byte, characterId: Byte = NO_CHAR): String = {
    if (characterId != NO_CHAR) {
      val o = overrides.get(key(characterId, projectileType))
      if (o != null) return o
    }
    val s = byType(projectileType & 0xFF)
    if (s != null) s else "atk_normal_bolt"
  }

  /** What `characterId`'s ability in `slot` (0 is Q, 1 is E) sounds like as it is cast, for one
    * that throws nothing; `kind` (DASH...) is what it is, for a character with no row. */
  def forCast(characterId: Byte, slot: Int, kind: Int): String = {
    val s = casts.get(key(characterId, slot))
    if (s != null) s else plainCast(kind)
  }

  /** What `characterId` casting an ability of `kind` sounds like: the first of its two that is one.
    * For another player's cast, which the client sees happen (they are somewhere else, their
    * barrier is up) without being told which key they pressed. */
  def forCastOf(characterId: Byte, kind: Int): String = {
    var slot = 0
    while (slot < 2) {
      val k = castKinds.get(key(characterId, slot))
      if (k != null && k.intValue == kind) return casts.get(key(characterId, slot))
      slot += 1
    }
    plainCast(kind)
  }

  /** Has `characterId` an ability of `kind` at all? */
  def hasCast(characterId: Byte, kind: Int): Boolean = {
    val q = castKinds.get(key(characterId, 0))
    val e = castKinds.get(key(characterId, 1))
    (q != null && q.intValue == kind) || (e != null && e.intValue == kind)
  }

  /** What `characterId` setting down a trap of `trapType` sounds like. */
  def forTrap(characterId: Byte, trapType: Byte): String = {
    val s = traps.get(key(characterId, trapType))
    if (s != null) s else plainCast(TRAP)
  }

  /** What an explosive sounds like going off, by what it is and whose: the frag's bang unless it
    * has its own. */
  def forBlast(projectileType: Byte, characterId: Byte = NO_CHAR): String = {
    if (characterId != NO_CHAR) {
      val o = blastOverrides.get(key(characterId, projectileType))
      if (o != null) return o
    }
    val s = blastByType(projectileType & 0xFF)
    if (s != null) s else FragBlast
  }

  /** The three sounds of `characterId`'s row (its primary, its Q, its E), or null without one. */
  def rowOf(characterId: Byte): Array[String] = rows.get(characterId)

  /** Every sound a match may play because `characterId` is in it: their row, and what their
    * explosives go off as. What is loaded when a character joins a match. */
  def soundsOf(characterId: Byte): Seq[String] = {
    val out = scala.collection.mutable.LinkedHashSet[String]()
    val row = rows.get(characterId)
    if (row != null) row.foreach(out += _)
    if (CharacterDef.isValid(characterId)) {
      val c = CharacterDef.get(characterId)
      Seq(c.primaryProjectileType, c.qAbility.projectileType, c.eAbility.projectileType).foreach { t =>
        if (t >= 0 && ProjectileDef.get(t).explosionConfig.exists(e => e.centerDamage > 0 || e.edgeDamage > 0))
          out += forBlast(t, characterId)
      }
    }
    out.toIndexedSeq
  }

  /** Every attack, cast and detonation sound file there is. */
  val allSoundNames: Seq[String] = {
    val out = scala.collection.mutable.LinkedHashSet[String]()
    byType.foreach(s => if (s != null) out += s)
    rows.values().forEach(r => r.foreach(out += _))
    blastByType.foreach(s => if (s != null) out += s)
    blastOverrides.values().forEach(s => out += s)
    out.toIndexedSeq
  }
}
