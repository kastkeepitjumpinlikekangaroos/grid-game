package com.gridgame.client.input

import com.gridgame.client.GameClient
import com.gridgame.common.Constants

import javafx.event.EventHandler
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent

import scala.collection.mutable

class KeyboardHandler(client: GameClient) extends EventHandler[KeyEvent] {
  private val pressedKeys: mutable.Set[KeyCode] = mutable.Set.empty
  private var lastMoveTime: Long = 0
  private var lastShootTime: Long = 0

  override def handle(event: KeyEvent): Unit = {
    if (event.getEventType == KeyEvent.KEY_PRESSED) {
      pressedKeys.add(event.getCode)
      if (client.getIsDead) {
        processRejoin()
      } else {
        processMovement()
        processShoot()
      }
    } else if (event.getEventType == KeyEvent.KEY_RELEASED) {
      pressedKeys.remove(event.getCode)
    }
  }

  private def processRejoin(): Unit = {
    if (pressedKeys.contains(KeyCode.ENTER)) {
      client.rejoin()
    }
  }

  private def processShoot(): Unit = {
    if (!pressedKeys.contains(KeyCode.SPACE)) {
      return
    }

    val now = System.currentTimeMillis()
    if (now - lastShootTime < Constants.SHOOT_COOLDOWN_MS) {
      return
    }

    client.shoot()
    lastShootTime = now
  }

  private def processMovement(): Unit = {
    val now = System.currentTimeMillis()

    if (now - lastMoveTime < Constants.MOVE_RATE_LIMIT_MS) {
      return
    }

    var dx = 0
    var dy = 0

    if (pressedKeys.contains(KeyCode.W)) {
      dy = -1
    }
    if (pressedKeys.contains(KeyCode.S)) {
      dy = 1
    }
    if (pressedKeys.contains(KeyCode.A)) {
      dx = -1
    }
    if (pressedKeys.contains(KeyCode.D)) {
      dx = 1
    }

    if (dx != 0 || dy != 0) {
      client.movePlayer(dx, dy)
      lastMoveTime = now
    }
  }
}
