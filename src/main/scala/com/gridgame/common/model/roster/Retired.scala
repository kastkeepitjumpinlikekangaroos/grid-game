package com.gridgame.common.model.roster

import com.gridgame.common.model._
import com.gridgame.common.model.CombatRole.{Melee, Ranged, Skirmisher}

/**
 * Projectiles no character throws any more. They are still registered, so every renderer, sound
 * and blast that names one keeps a definition to look up.
 */
private[model] object Retired {
  // ── Projectiles ──

  val SnareMineDef: ProjectileDef = ProjectileDef(
    id = ProjectileType.SNARE_MINE, name = "Snare Mine",
    speedMultiplier = 0.4f, damage = 15, maxRange = 16,
    aoeOnHit = Some(AoESplashConfig(3.5f, 15, freezeDurationMs = 1000)),
    aoeOnMaxRange = Some(AoESplashConfig(3.5f, 15, freezeDurationMs = 1000)),
    explodesOnPlayerHit = true,
    explosionConfig = Some(ExplosionConfig(0, 0, 3.5f))
  )
  val BloodSiphonDef: ProjectileDef = ProjectileDef(
    id = ProjectileType.BLOOD_SIPHON, name = "Blood Siphon",
    speedMultiplier = 0.65f, damage = 18, maxRange = 14,
    onHitEffect = Some(LifeSteal(40))
  )
  val FrostTrapDef: ProjectileDef = ProjectileDef(id = ProjectileType.FROST_TRAP, name = "Frost Trap", speedMultiplier = 0.4f, damage = 15, maxRange = 14, passesThroughPlayers = true, aoeOnMaxRange = Some(AoESplashConfig(3.5f, 15, freezeDurationMs = 1500)))
  val ThornWallDef: ProjectileDef = ProjectileDef(id = ProjectileType.THORN_WALL, name = "Thorn Wall", speedMultiplier = 0.5f, damage = 12, maxRange = 8)
  val GlacierSpikeDef: ProjectileDef = ProjectileDef(id = ProjectileType.GLACIER_SPIKE, name = "Glacier Spike", speedMultiplier = 0.5f, damage = 30, maxRange = 14, onHitEffect = Some(Freeze(2000)))
  val WailDef: ProjectileDef = ProjectileDef(id = ProjectileType.WAIL, name = "Wail", speedMultiplier = 0.5f, damage = 15, maxRange = 10, passesThroughWalls = true, aoeOnHit = Some(AoESplashConfig(3.0f, 10, freezeDurationMs = 800)))
  val TeslaCoilDef: ProjectileDef = ProjectileDef(id = ProjectileType.TESLA_COIL, name = "Tesla Coil", speedMultiplier = 0.5f, damage = 20, maxRange = 10, aoeOnHit = Some(AoESplashConfig(3.0f, 12, freezeDurationMs = 500)), ricochetCount = 3)
  val AcidBombDef: ProjectileDef = ProjectileDef(id = ProjectileType.ACID_BOMB, name = "Acid Bomb", speedMultiplier = 0.5f, damage = 25, maxRange = 14, explodesOnPlayerHit = true, explosionConfig = Some(ExplosionConfig(25, 10, 3.0f)))
  val GravityLockDef: ProjectileDef = ProjectileDef(id = ProjectileType.GRAVITY_LOCK, name = "Gravity Lock", speedMultiplier = 0.4f, damage = 0, maxRange = 12, passesThroughPlayers = true, aoeOnMaxRange = Some(AoESplashConfig(6.0f, 15, rootDurationMs = 2000)), explosionConfig = Some(ExplosionConfig(0, 0, 6.0f)))

  val projectiles: Seq[ProjectileDef] = Seq(
    SnareMineDef, BloodSiphonDef, FrostTrapDef, ThornWallDef, GlacierSpikeDef,
    WailDef, TeslaCoilDef, AcidBombDef, GravityLockDef
  )
}
