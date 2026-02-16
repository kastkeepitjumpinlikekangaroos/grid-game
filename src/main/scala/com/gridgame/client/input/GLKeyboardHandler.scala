package com.gridgame.client.input

import com.gridgame.client.ClientState
import com.gridgame.client.GameClient
import com.gridgame.common.Constants
import com.gridgame.common.model.ItemType

import org.lwjgl.glfw.GLFW._
import org.lwjgl.glfw.GLFWKeyCallback

import scala.collection.mutable

/**
 * GLFW keyboard handler. Mirrors KeyboardHandler logic but uses GLFW key codes.
 * Installed as a GLFW key callback on the game window.
 */
class GLKeyboardHandler(client: GameClient) extends GLFWKeyCallback {
  private val pressedKeys: mutable.Set[Int] = mutable.Set.empty
  private var lastMoveTime: Long = 0
  private var lastBurstTime: Long = 0

  override def invoke(window: Long, key: Int, scancode: Int, action: Int, mods: Int): Unit = {
    if (action == GLFW_PRESS) {
      pressedKeys.add(key)
      if (client.getIsDead) {
        if (client.clientState != ClientState.PLAYING) processRejoin()
      } else {
        processUseItem(key)
        processBurstShot(key, mods)
        processAbilities(key)
      }
    } else if (action == GLFW_RELEASE) {
      pressedKeys.remove(key)
    }
  }

  def clearAllKeys(): Unit = {
    pressedKeys.clear()
    client.setMovementInputActive(false)
  }

  /** Called each frame from the game loop. */
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

  /** Check if F11 was pressed for fullscreen toggle. */
  def isF11Pressed: Boolean = pressedKeys.contains(GLFW_KEY_F11)

  private def processRejoin(): Unit = {
    if (pressedKeys.contains(GLFW_KEY_ENTER)) client.rejoin()
  }

  private def processBurstShot(key: Int, mods: Int): Unit = {
    if (key != GLFW_KEY_SPACE || (mods & GLFW_MOD_SHIFT) == 0) return
    val now = System.currentTimeMillis()
    if (now - lastBurstTime < Constants.BURST_SHOT_COOLDOWN_MS) return
    client.shootAllDirections()
    lastBurstTime = now
  }

  private def processUseItem(key: Int): Unit = {
    val itemTypeId: Byte = key match {
      case GLFW_KEY_1 => ItemType.Heart.id
      case GLFW_KEY_2 => ItemType.Star.id
      case GLFW_KEY_3 => ItemType.Gem.id
      case GLFW_KEY_4 => ItemType.Shield.id
      case GLFW_KEY_5 => ItemType.Fence.id
      case _ => -1
    }
    if (itemTypeId >= 0) client.useItem(itemTypeId)
  }

  private def processAbilities(key: Int): Unit = {
    key match {
      case GLFW_KEY_Q => client.shootAbility(0)
      case GLFW_KEY_E => client.shootAbility(1)
      case _ =>
    }
  }

  private def processMovement(): Unit = {
    val now = System.currentTimeMillis()
    if (client.isSwooping) return

    val moveRate = if (client.isCharging) {
      val chargePct = client.getChargeLevel / 100.0
      (Constants.MOVE_RATE_LIMIT_MS * (1.0 + chargePct * 9.0)).toInt
    } else if (client.isPhased) {
      25
    } else if (client.hasSpeedBoost) {
      30
    } else if (client.isSlowed) {
      (Constants.MOVE_RATE_LIMIT_MS * 2)
    } else {
      Constants.MOVE_RATE_LIMIT_MS
    }

    if (now - lastMoveTime < moveRate) return

    // Isometric WASD
    var dx = 0
    var dy = 0
    if (pressedKeys.contains(GLFW_KEY_W)) { dx -= 1; dy -= 1 }
    if (pressedKeys.contains(GLFW_KEY_S)) { dx += 1; dy += 1 }
    if (pressedKeys.contains(GLFW_KEY_A)) { dx -= 1; dy += 1 }
    if (pressedKeys.contains(GLFW_KEY_D)) { dx += 1; dy -= 1 }

    dx = Math.max(-1, Math.min(1, dx))
    dy = Math.max(-1, Math.min(1, dy))

    client.setMovementInputActive(dx != 0 || dy != 0)

    if (dx != 0 || dy != 0) {
      client.movePlayer(dx, dy)
      lastMoveTime = now
    }
  }
}
