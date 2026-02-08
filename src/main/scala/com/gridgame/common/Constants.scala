package com.gridgame.common

object Constants {
  // Grid configuration
  val GRID_SIZE: Int = 1000
  val CELL_SIZE_PX: Int = 20
  val VIEWPORT_CELLS: Int = 50
  val VIEWPORT_SIZE_PX: Int = VIEWPORT_CELLS * CELL_SIZE_PX // 500px

  // Network configuration
  val PACKET_SIZE: Int = 64
  val SERVER_PORT: Int = 25565
  val HEARTBEAT_INTERVAL_MS: Int = 3000 // 3 seconds
  val CLIENT_TIMEOUT_MS: Int = 10000 // 10 seconds

  // Input configuration
  val MOVE_RATE_LIMIT_MS: Int = 30 // Max 10 moves per second

  // Health configuration
  val MAX_HEALTH: Int = 100
  val HEALTH_BAR_WIDTH_PX: Int = 16
  val HEALTH_BAR_HEIGHT_PX: Int = 3
  val HEALTH_BAR_OFFSET_Y: Int = 4

  // Projectile configuration
  val PROJECTILE_SPEED_MS: Int = 50       // Move every 50ms (20 cells/second)
  val SHOOT_COOLDOWN_MS: Int = 5        // 2 shots per second max
  val PROJECTILE_DAMAGE: Int = 5          // Damage per hit
  val PROJECTILE_SIZE_PX: Int = 10         // Render size in pixels
  val PROJECTILE_MAX_RANGE: Int = 10        // Max travel distance in tiles

  // Burst shot configuration (Shift+Space)
  val BURST_SHOT_MOVEMENT_BLOCK_MS: Int = 500  // Immobilized for 500ms after burst
  val BURST_SHOT_COOLDOWN_MS: Int = 1000       // Can burst once per second

  // Item configuration
  val ITEM_SPAWN_INTERVAL_MS: Int = 60000     // 1 minute
  val ITEM_SIZE_PX: Int = 12
  val MAX_INVENTORY_SIZE: Int = 3

  // Item effect configuration
  val STAR_DURATION_MS: Int = 20000            // Speed boost lasts 20s
  val SHIELD_DURATION_MS: Int = 5000           // Shield lasts 5s
  val GEM_DURATION_MS: Int = 5000             // Fast projectiles last 20s
  val SPEED_BOOST_MOVE_RATE_MS: Int = 15       // Half of normal 60ms
  val GEM_PROJECTILE_SPEED_MULTIPLIER: Float = 2.0f
}
