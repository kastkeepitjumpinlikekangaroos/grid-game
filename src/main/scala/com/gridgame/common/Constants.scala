package com.gridgame.common

object Constants {
  // Grid configuration
  val GRID_SIZE: Int = 1000
  val CELL_SIZE_PX: Int = 20
  val VIEWPORT_CELLS: Int = 50
  val VIEWPORT_SIZE_PX: Int = VIEWPORT_CELLS * CELL_SIZE_PX // 500px

  // Sprite size for isometric view
  val SPRITE_SIZE_PX: Int = 32         // Source frame size in spritesheet
  val PLAYER_DISPLAY_SIZE_PX: Int = 48 // Rendered display size (scaled up)

  // Isometric tile dimensions (2:1 ratio)
  val ISO_TILE_WIDTH: Int = 40
  val ISO_TILE_HEIGHT: Int = 20
  val ISO_HALF_W: Int = ISO_TILE_WIDTH / 2
  val ISO_HALF_H: Int = ISO_TILE_HEIGHT / 2

  // Tile sprite cell dimensions (for tileset image)
  val TILE_CELL_WIDTH: Int = ISO_TILE_WIDTH   // 40
  val TILE_CELL_HEIGHT: Int = 56

  // Network configuration
  val PACKET_SIZE: Int = 64
  val SERVER_PORT: Int = 25565
  val HEARTBEAT_INTERVAL_MS: Int = 3000 // 3 seconds
  val CLIENT_TIMEOUT_MS: Int = 10000 // 10 seconds

  // Input configuration
  val MOVE_RATE_LIMIT_MS: Int = 50 // Max 10 moves per second

  // Health configuration
  val MAX_HEALTH: Int = 100
  val HEALTH_BAR_WIDTH_PX: Int = 24
  val HEALTH_BAR_HEIGHT_PX: Int = 4
  val HEALTH_BAR_OFFSET_Y: Int = 5

  // Projectile configuration
  val PROJECTILE_SPEED_MS: Int = 30       // Move every 50ms (20 cells/second)
  val SHOOT_COOLDOWN_MS: Int = 500        // 2 shots per second max
  val PROJECTILE_DAMAGE: Int = 15          // Damage per hit
  val PROJECTILE_MAX_RANGE: Int = 20        // Max travel distance in tiles

  // Burst shot configuration (Shift+Space)
  val BURST_SHOT_MOVEMENT_BLOCK_MS: Int = 500  // Immobilized for 500ms after burst
  val BURST_SHOT_COOLDOWN_MS: Int = 1000       // Can burst once per second

  // Collision configuration
  val ITEM_PICKUP_RADIUS: Int = 2              // Pick up items within 2 cells
  val PROJECTILE_HIT_RADIUS: Float = 1.5f      // Projectile hits within 1.5 cells (float distance)

  // Item configuration
  val ITEM_SPAWN_INTERVAL_MS: Int = 60000     // 1 minute
  val ITEM_SIZE_PX: Int = 30

  // Item effect configuration
  val SHIELD_DURATION_MS: Int = 5000           // Shield lasts 5s
  val GEM_DURATION_MS: Int = 5000             // Fast projectiles last 20s
  val GEM_PROJECTILE_SPEED_MULTIPLIER: Float = 2.0f

  // Charge shot configuration
  val CHARGE_MAX_MS: Int = 4000                // Time to reach full charge
  val CHARGE_MIN_DAMAGE: Int = 10              // Damage at 0% charge
  val CHARGE_MAX_DAMAGE: Int = 100             // Damage at 100% charge (instant kill)
  val CHARGE_MIN_RANGE: Int = 15               // Range at 0% charge
  val CHARGE_MAX_RANGE: Int = 30               // Range at 100% charge
  val CHARGE_MIN_SPEED: Float = 0.8f           // Projectile speed multiplier at 0%
  val CHARGE_MAX_SPEED: Float = 2.0f           // Projectile speed multiplier at 100%
  val CHARGE_MOVEMENT_SLOW: Int = 2            // Movement rate multiplier while charging

  // Tentacle ability (Q)
  val TENTACLE_COOLDOWN_MS: Int = 5000
  val TENTACLE_MAX_RANGE: Int = 15
  val TENTACLE_PULL_DISTANCE: Float = 3.0f
  val TENTACLE_DAMAGE: Int = 5

  // Ice Beam ability (E)
  val ICE_BEAM_COOLDOWN_MS: Int = 5000
  val ICE_BEAM_MAX_RANGE: Int = 20
  val ICE_BEAM_FREEZE_DURATION_MS: Int = 3000
  val ICE_BEAM_DAMAGE: Int = 5

  // Axe ability (Gladiator primary)
  val AXE_DAMAGE: Int = 33
  val AXE_MAX_RANGE: Int = 5
  val AXE_HIT_RADIUS: Float = 2.5f

  // Rope ability (Gladiator E)
  val ROPE_COOLDOWN_MS: Int = 20000
  val ROPE_MAX_RANGE: Int = 25
  val ROPE_DAMAGE: Int = 5

  // Spear ability (Gladiator Q)
  val SPEAR_COOLDOWN_MS: Int = 8000
  val SPEAR_MAX_RANGE: Int = 20
  val SPEAR_BASE_DAMAGE: Int = 10
  val SPEAR_MAX_DAMAGE: Int = 60

  // Wraith: Soul Bolt (primary)
  val SOUL_BOLT_DAMAGE: Int = 15
  val SOUL_BOLT_MAX_RANGE: Int = 15

  // Wraith: Phase Shift (Q)
  val PHASE_SHIFT_COOLDOWN_MS: Int = 12000
  val PHASE_SHIFT_DURATION_MS: Int = 5000
  val PHASE_SHIFT_MOVE_RATE_MS: Int = 25       // 2x move speed while phased

  // Wraith: Haunt (E)
  val HAUNT_COOLDOWN_MS: Int = 15000
  val HAUNT_MAX_RANGE: Int = 15
  val HAUNT_DAMAGE: Int = 30
  val HAUNT_TELEPORT_DISTANCE: Int = 2
  val HAUNT_FREEZE_DURATION_MS: Int = 2000

  // Splash Shot (Tidecaller primary)
  val SPLASH_DAMAGE: Int = 20
  val SPLASH_AOE_DAMAGE: Int = 10
  val SPLASH_MAX_RANGE: Int = 15
  val SPLASH_AOE_RADIUS: Float = 3.0f

  // Tidal Wave (Tidecaller Q)
  val TIDAL_WAVE_COOLDOWN_MS: Int = 8000
  val TIDAL_WAVE_DAMAGE: Int = 10
  val TIDAL_WAVE_MAX_RANGE: Int = 12
  val TIDAL_WAVE_FAN_COUNT: Int = 5
  val TIDAL_WAVE_FAN_ANGLE: Double = Math.toRadians(60)
  val TIDAL_WAVE_PUSHBACK_DISTANCE: Float = 3.0f

  // Geyser (Tidecaller E)
  val GEYSER_COOLDOWN_MS: Int = 12000
  val GEYSER_DAMAGE: Int = 25
  val GEYSER_MAX_RANGE: Int = 18
  val GEYSER_AOE_RADIUS: Float = 4.0f

  // Soldier: Bullet (primary)
  val BULLET_DAMAGE: Int = 20
  val BULLET_MAX_RANGE: Int = 18

  // Soldier: Grenade (Q)
  val GRENADE_COOLDOWN_MS: Int = 10000
  val GRENADE_MAX_RANGE: Int = 12
  val GRENADE_CENTER_DAMAGE: Int = 40
  val GRENADE_EDGE_DAMAGE: Int = 10
  val GRENADE_BLAST_RADIUS: Float = 3.0f

  // Soldier: Rocket (E)
  val ROCKET_COOLDOWN_MS: Int = 12000
  val ROCKET_MAX_RANGE: Int = 20
  val ROCKET_CENTER_DAMAGE: Int = 35
  val ROCKET_EDGE_DAMAGE: Int = 10
  val ROCKET_BLAST_RADIUS: Float = 2.5f

  // Raptor: Talon Strike (primary)
  val TALON_DAMAGE: Int = 28
  val TALON_MAX_RANGE: Int = 4
  val TALON_HIT_RADIUS: Float = 2.0f

  // Raptor: Swoop (Q)
  val SWOOP_COOLDOWN_MS: Int = 10000
  val SWOOP_MAX_DISTANCE: Int = 12
  val SWOOP_DURATION_MS: Int = 400
  val SWOOP_MOVE_RATE_MS: Int = 20

  // Raptor: Gust (E)
  val GUST_COOLDOWN_MS: Int = 8000
  val GUST_MAX_RANGE: Int = 6
  val GUST_DAMAGE: Int = 10
  val GUST_PUSH_DISTANCE: Int = 5

  // Lobby configuration
  val RESPAWN_DELAY_MS: Int = 3000
  val DEFAULT_GAME_DURATION_MIN: Int = 5
  val MAX_LOBBY_NAME_LEN: Int = 24
  val TIME_SYNC_INTERVAL_S: Int = 10
  val MAX_LOBBY_PLAYERS: Int = 8
}
