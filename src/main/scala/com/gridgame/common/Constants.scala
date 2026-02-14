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
  val MOVE_RATE_LIMIT_MS: Int = 10 // Max 10 moves per second

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
  val MAX_INVENTORY_SIZE: Int = 3

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
}
