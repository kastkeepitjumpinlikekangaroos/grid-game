package com.gridgame.client.input

import com.gridgame.client.ClientState
import com.gridgame.client.GameClient
import com.gridgame.common.Constants
import com.gridgame.common.model.ItemType

import javafx.event.EventHandler
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent

import scala.collection.mutable

class KeyboardHandler(client: GameClient) extends EventHandler[KeyEvent] {
  private val pressedKeys: mutable.Set[KeyCode] = mutable.Set.empty
  private var lastMoveTime: Long = 0
  private var lastBurstTime: Long = 0

  override def handle(event: KeyEvent): Unit = {
    if (event.getEventType == KeyEvent.KEY_PRESSED) {
      pressedKeys.add(event.getCode)
      if (client.getIsDead) {
        // In lobby mode, auto-respawn handles it; only allow manual rejoin in non-lobby mode
        if (client.clientState != ClientState.PLAYING) {
          processRejoin()
        }
      } else {
        processUseItem(event)
        processBurstShot(event)
        processAbilities(event)
      }
    } else if (event.getEventType == KeyEvent.KEY_RELEASED) {
      pressedKeys.remove(event.getCode)
    }
  }

  /** Clears all pressed keys. Called when window loses focus to prevent stuck keys. */
  def clearAllKeys(): Unit = {
    pressedKeys.clear()
    client.setMovementInputActive(false)
  }

  /** Called from the game loop each frame to poll movement from held keys. */
  def update(): Unit = {
    if (!client.getIsDead) {
      if (client.isSwooping) {
        client.tickSwoop()
      } else {
        processMovement()
      }
      if (client.isCharging || client.hasShield || client.hasGemBoost) {
        client.sendChargingUpdate()
      }
    }
  }

  private def processRejoin(): Unit = {
    if (pressedKeys.contains(KeyCode.ENTER)) {
      client.rejoin()
    }
  }

  private def processBurstShot(event: KeyEvent): Boolean = {
    if (!pressedKeys.contains(KeyCode.SPACE) || !event.isShiftDown) {
      return false
    }

    val now = System.currentTimeMillis()
    if (now - lastBurstTime < Constants.BURST_SHOT_COOLDOWN_MS) {
      return true // Still consumed the input, just on cooldown
    }

    client.shootAllDirections()
    lastBurstTime = now
    true
  }

  private def processUseItem(event: KeyEvent): Unit = {
    val itemTypeId: Byte = event.getCode match {
      case KeyCode.DIGIT1 => ItemType.Heart.id
      case KeyCode.DIGIT2 => ItemType.Star.id
      case KeyCode.DIGIT3 => ItemType.Gem.id
      case KeyCode.DIGIT4 => ItemType.Shield.id
      case KeyCode.DIGIT5 => ItemType.Fence.id
      case _ => -1
    }
    if (itemTypeId >= 0) {
      client.useItem(itemTypeId)
    }
  }

  private def processAbilities(event: KeyEvent): Unit = {
    event.getCode match {
      case KeyCode.Q => client.shootAbility(0)
      case KeyCode.E => client.shootAbility(1)
      case _ =>
    }
  }

  private def processMovement(): Unit = {
    val now = System.currentTimeMillis()
    if (client.isSwooping) return // Block WASD during swoop
    val moveRate = if (client.isCharging) {
      // Slowdown proportional to charge: 1x at 0% → 10x at 100%
      val chargePct = client.getChargeLevel / 100.0
      (Constants.MOVE_RATE_LIMIT_MS * (1.0 + chargePct * 9.0)).toInt
    } else if (client.isPhased) {
      25 // 2x move speed while phased
    } else if (client.hasSpeedBoost) {
      30 // ~1.7x move speed with speed boost
    } else if (client.isSlowed) {
      (Constants.MOVE_RATE_LIMIT_MS * 2) // Half speed while slowed
    } else {
      Constants.MOVE_RATE_LIMIT_MS
    }

    if (now - lastMoveTime < moveRate) {
      return
    }

    // Isometric WASD: each key maps to a screen direction
    // W=screen up, S=screen down, A=screen left, D=screen right
    var dx = 0
    var dy = 0

    if (pressedKeys.contains(KeyCode.W)) { dx -= 1; dy -= 1 }
    if (pressedKeys.contains(KeyCode.S)) { dx += 1; dy += 1 }
    if (pressedKeys.contains(KeyCode.A)) { dx -= 1; dy += 1 }
    if (pressedKeys.contains(KeyCode.D)) { dx += 1; dy -= 1 }

    // Clamp to unit movement
    dx = Math.max(-1, Math.min(1, dx))
    dy = Math.max(-1, Math.min(1, dy))

    client.setMovementInputActive(dx != 0 || dy != 0)

    if (dx != 0 || dy != 0) {
      client.movePlayer(dx, dy)
      lastMoveTime = now
    }
  }
}
