package com.gridgame.common

object Constants {
  // Grid configuration
  val GRID_SIZE: Int = 1000
  val CELL_SIZE_PX: Int = 20
  val VIEWPORT_CELLS: Int = 50
  val VIEWPORT_SIZE_PX: Int = VIEWPORT_CELLS * CELL_SIZE_PX // 500px

  // Sprite size for isometric view
  val SPRITE_SIZE_PX: Int = 64         // Source frame size in spritesheet (2x atlas)
  val PLAYER_DISPLAY_SIZE_PX: Int = 48 // Rendered display size (scaled up)

  // Camera zoom level (1.0 = default, 2.0 = 2x zoom)
  val CAMERA_ZOOM: Double = 1.3

  // Isometric tile dimensions (2:1 ratio)
  val ISO_TILE_WIDTH: Int = 40
  val ISO_TILE_HEIGHT: Int = 20
  val ISO_HALF_W: Int = ISO_TILE_WIDTH / 2
  val ISO_HALF_H: Int = ISO_TILE_HEIGHT / 2

  // Tile sprite cell dimensions (display size)
  val TILE_CELL_WIDTH: Int = ISO_TILE_WIDTH   // 40
  val TILE_CELL_HEIGHT: Int = 56

  // Tile atlas cell dimensions (2x resolution source)
  val TILE_ATLAS_CELL_W: Int = 80
  val TILE_ATLAS_CELL_H: Int = 112

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
  val PROJECTILE_SPEED_MS: Int = 30       // Move every 30ms
  val SHOOT_COOLDOWN_MS: Int = 500        // 2 shots per second max
  val PROJECTILE_DAMAGE: Int = 15         // Default damage per hit
  val PROJECTILE_MAX_RANGE: Int = 20      // Max travel distance in tiles

  // Burst shot configuration (Shift+Space)
  val BURST_SHOT_MOVEMENT_BLOCK_MS: Int = 500  // Immobilized for 500ms after burst
  val BURST_SHOT_COOLDOWN_MS: Int = 1000       // Can burst once per second

  // Collision configuration
  val ITEM_PICKUP_RADIUS: Int = 2              // Pick up items within 2 cells
  val PROJECTILE_HIT_RADIUS: Float = 1.5f      // Default projectile hit radius

  // Item configuration
  val ITEM_SPAWN_INTERVAL_MS: Int = 60000     // 1 minute
  val ITEM_SIZE_PX: Int = 30

  // Item effect configuration
  val SHIELD_DURATION_MS: Int = 5000           // Shield lasts 5s
  val GEM_DURATION_MS: Int = 5000             // Fast projectiles last 5s
  val GEM_PROJECTILE_SPEED_MULTIPLIER: Float = 2.0f

  // Charge shot configuration
  val CHARGE_MAX_MS: Int = 4000                // Time to reach full charge
  val CHARGE_MOVEMENT_SLOW: Int = 2            // Movement rate multiplier while charging

  // Lobby configuration
  val RESPAWN_DELAY_MS: Int = 3000
  val DEFAULT_GAME_DURATION_MIN: Int = 5
  val MAX_LOBBY_NAME_LEN: Int = 24
  val TIME_SYNC_INTERVAL_S: Int = 10
  val MAX_LOBBY_PLAYERS: Int = 32

  // CC immunity — guaranteed free window after a freeze expires
  val CC_IMMUNITY_MS: Int = 1500

  // Duel configuration
  val DUEL_GAME_DURATION_MIN: Int = 3
  val DUEL_MAX_PLAYERS: Int = 2

  // Ranked Teams configuration
  val TEAMS_GAME_DURATION_MIN: Int = 5
  val TEAMS_TEAM_SIZE: Int = 3  // 3v3
  val TEAMS_MAX_PLAYERS: Int = TEAMS_TEAM_SIZE * 2 // 6
}
