package com.gridgame.client.audio

import com.gridgame.common.model.ProjectileType

/** Maps each of the 150 ProjectileType ids to one of the ~70 generated attack
  * archetype sounds in sounds/atk_*.wav. Grouping mirrors GLProjectileRenderers'
  * registry — types that share a visual renderer share a sound. */
object AbilitySounds {
  private val table: Array[String] = new Array[String](256)

  private def set(sound: String, ids: Byte*): Unit =
    ids.foreach(id => table(id & 0xFF) = sound)

  set("atk_normal_bolt", ProjectileType.NORMAL)
  set("atk_soul_bolt", ProjectileType.SOUL_BOLT, ProjectileType.SOUL_BOLT_HEAVY)
  set("atk_haunt_bolt", ProjectileType.HAUNT)
  set("atk_arcane_bolt", ProjectileType.ARCANE_BOLT, ProjectileType.MYSTIC_BOLT)
  set("atk_plague_bolt", ProjectileType.PLAGUE_BOLT)
  set("atk_flame_bolt", ProjectileType.FLAME_BOLT, ProjectileType.MAGMA_BALL, ProjectileType.EMBER_SHOT,
    ProjectileType.FLAME_BOLT_HEAVY, ProjectileType.FLAME_BOLT_LIGHT, ProjectileType.FLAME_TRAIL)
  set("atk_mud_glob", ProjectileType.MUD_GLOB)
  set("atk_sand_shot", ProjectileType.SAND_SHOT)
  set("atk_death_bolt", ProjectileType.DEATH_BOLT)
  set("atk_charm_bolt", ProjectileType.CHARM)
  set("atk_nano_bolt", ProjectileType.NANO_BOLT, ProjectileType.ECHO_BOLT)
  set("atk_sting", ProjectileType.STING)
  set("atk_star_bolt", ProjectileType.STAR_BOLT, ProjectileType.RUNE_BOLT)
  set("atk_leech_bolt", ProjectileType.LEECH_BOLT)

  set("atk_beam_ice", ProjectileType.ICE_BEAM)
  set("atk_beam_drain", ProjectileType.BLOOD_SIPHON, ProjectileType.SOUL_DRAIN, ProjectileType.LIFE_DRAIN)
  set("atk_beam_vine", ProjectileType.VINE_WHIP)
  set("atk_beam_laser", ProjectileType.LASER, ProjectileType.LASER_HEAVY, ProjectileType.LASER_LIGHT, ProjectileType.EYE_BEAM)
  set("atk_beam_railgun", ProjectileType.RAILGUN, ProjectileType.SNIPER_BEAM)
  set("atk_beam_stone", ProjectileType.STONE_GAZE, ProjectileType.PETRIFY)
  set("atk_beam_whip", ProjectileType.BANDAGE_WHIP, ProjectileType.GRAB, ProjectileType.TONGUE)
  set("atk_beam_gravity", ProjectileType.GRAVITY_LANCE)

  set("atk_spinner_metal", ProjectileType.AXE, ProjectileType.SHURIKEN, ProjectileType.KATANA,
    ProjectileType.KNIFE, ProjectileType.CARD, ProjectileType.BOOMERANG_BLADE)
  set("atk_spinner_bone", ProjectileType.BONE_AXE, ProjectileType.BONE_THROW, ProjectileType.BONE_BOOMERANG)
  set("atk_spinner_cursed", ProjectileType.CURSED_BLADE)
  set("atk_spinner_holy", ProjectileType.HOLY_BLADE)

  set("atk_chain_bolt", ProjectileType.ROPE, ProjectileType.CHAIN_BOLT, ProjectileType.LOCKDOWN_CHAIN)

  set("atk_spear_thrust", ProjectileType.SPEAR)
  set("atk_arrow_shot", ProjectileType.ARROW, ProjectileType.POISON_ARROW, ProjectileType.ARROW_HEAVY, ProjectileType.ARROW_LIGHT)

  set("atk_grenade_lob", ProjectileType.GRENADE, ProjectileType.SNARE_MINE, ProjectileType.BLIGHT_BOMB,
    ProjectileType.FROST_TRAP, ProjectileType.MUD_BOMB, ProjectileType.SHOVEL, ProjectileType.HEAD_THROW,
    ProjectileType.CLUSTER_BOMB, ProjectileType.INK_SNARE, ProjectileType.HAMMER, ProjectileType.NAPALM_STRIKE)

  set("atk_aoe_boom", ProjectileType.SPLASH, ProjectileType.MIASMA, ProjectileType.SEISMIC_SLAM,
    ProjectileType.ERUPTION, ProjectileType.THORN_WALL, ProjectileType.SEISMIC_ROOT, ProjectileType.ROOT_GROWTH,
    ProjectileType.TREMOR_SLAM, ProjectileType.ENTANGLE, ProjectileType.VORTEX_BOMB, ProjectileType.SOUL_HARVEST,
    ProjectileType.OVERCLOCK_BEAM, ProjectileType.SONIC_BOOM, ProjectileType.POISON_CLOUD)

  set("atk_wind_wave", ProjectileType.GUST, ProjectileType.WIND_BLADE, ProjectileType.SAND_BLAST,
    ProjectileType.MOMENTUM_STRIKE, ProjectileType.FLAME_WAVE, ProjectileType.ACID_SPRAY)
  set("atk_sonic_wave", ProjectileType.SONIC_WAVE, ProjectileType.SONIC_WAVE_HEAVY, ProjectileType.SONIC_WAVE_MED)

  set("atk_gunshot", ProjectileType.BULLET, ProjectileType.BULLET_HEAVY, ProjectileType.BULLET_LIGHT)
  set("atk_punch", ProjectileType.FIST, ProjectileType.CHARGE_FIST)

  set("atk_tentacle_slap", ProjectileType.TENTACLE)
  set("atk_fireball_whoosh", ProjectileType.FIREBALL)
  set("atk_tidal_wave_crash", ProjectileType.TIDAL_WAVE)
  set("atk_geyser_burst", ProjectileType.GEYSER)
  set("atk_rocket_launch", ProjectileType.ROCKET)
  set("atk_talon_swipe", ProjectileType.TALON)
  set("atk_poison_dart", ProjectileType.POISON_DART)
  set("atk_sword_wave_slash", ProjectileType.SWORD_WAVE)
  set("atk_blood_fang_bite", ProjectileType.BLOOD_FANG)
  set("atk_bat_swarm_screech", ProjectileType.BAT_SWARM)
  set("atk_frost_shard_shatter", ProjectileType.FROST_SHARD, ProjectileType.GLACIER_SPIKE,
    ProjectileType.RICOCHET_SHARD, ProjectileType.FROST_SHARD_LIGHT)
  set("atk_lightning_crack", ProjectileType.LIGHTNING, ProjectileType.CHAIN_LIGHTNING,
    ProjectileType.TESLA_COIL, ProjectileType.CHAIN_LIGHTNING_FORK)
  set("atk_thunder_strike_boom", ProjectileType.THUNDER_STRIKE)
  set("atk_boulder_rumble", ProjectileType.BOULDER, ProjectileType.THROWN_BOULDER, ProjectileType.AVALANCHE_CRUSH)
  set("atk_thorn_shot", ProjectileType.THORN, ProjectileType.THORN_LIGHT)
  set("atk_inferno_blast_roar", ProjectileType.INFERNO_BLAST)
  set("atk_raise_dead_moan", ProjectileType.RAISE_DEAD)
  set("atk_wail_shriek", ProjectileType.WAIL)
  set("atk_claw_swipe_slash", ProjectileType.CLAW_SWIPE)
  set("atk_devour_bite", ProjectileType.DEVOUR)
  set("atk_scythe_swing", ProjectileType.SCYTHE)
  set("atk_reap_slash", ProjectileType.REAP)
  set("atk_shadow_bolt_whoosh", ProjectileType.SHADOW_BOLT, ProjectileType.SHADOW_HAUNT)
  set("atk_curse_hex", ProjectileType.CURSE)
  set("atk_holy_bolt_chime", ProjectileType.HOLY_BOLT, ProjectileType.SMITE, ProjectileType.HOLY_BOLT_HEAVY)
  set("atk_data_bolt_blip", ProjectileType.DATA_BOLT)
  set("atk_virus_glitch", ProjectileType.VIRUS)
  set("atk_gravity_ball_warp", ProjectileType.GRAVITY_BALL, ProjectileType.GRAVITY_WELL, ProjectileType.GRAVITY_LOCK)
  set("atk_void_bolt_implode", ProjectileType.VOID_BOLT)
  set("atk_venom_bolt_splat", ProjectileType.VENOM_BOLT, ProjectileType.ACID_BOMB, ProjectileType.ACID_FLASK,
    ProjectileType.VENOM_BOLT_LIGHT)
  set("atk_web_shot_splat", ProjectileType.WEB_SHOT, ProjectileType.WEB_TRAP)
  set("atk_stinger_prick", ProjectileType.STINGER)
  set("atk_horn_bellow", ProjectileType.HORN)
  set("atk_jaw_chomp", ProjectileType.JAW)

  def forProjectileType(id: Byte): String = {
    val s = table(id & 0xFF)
    if (s != null) s else "atk_normal_bolt"
  }

  /** Distinct attack sound files, for preloading. */
  val allSoundNames: Seq[String] = table.filter(_ != null).distinct.toIndexedSeq
}
