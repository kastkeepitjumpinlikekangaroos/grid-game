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
  val MOVE_RATE_LIMIT_MS: Int = 100 // Max 10 moves per second

  // Health configuration
  val MAX_HEALTH: Int = 100
  val HEALTH_BAR_WIDTH_PX: Int = 16
  val HEALTH_BAR_HEIGHT_PX: Int = 3
  val HEALTH_BAR_OFFSET_Y: Int = 4

  // Projectile configuration
  val PROJECTILE_SPEED_MS: Int = 50       // Move every 50ms (20 cells/second)
  val SHOOT_COOLDOWN_MS: Int = 5        // 2 shots per second max
  val PROJECTILE_DAMAGE: Int = 1          // Damage per hit
  val PROJECTILE_SIZE_PX: Int = 6         // Render size in pixels
}
