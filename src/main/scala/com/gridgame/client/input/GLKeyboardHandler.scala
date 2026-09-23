package com.gridgame.client.input

import com.gridgame.client.ClientState
import com.gridgame.client.GameClient
import com.gridgame.common.Constants
import com.gridgame.common.model.ItemType
import com.gridgame.common.model.Movement
import com.gridgame.common.protocol.ChatScope

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
  private var _f11JustPressed: Boolean = false

  // Chat input state
  var isChatMode: Boolean = false
  val chatInputBuffer = new StringBuilder(Constants.MAX_CHAT_MESSAGE_LEN)

  /** Set by the game scene: leaves the match (Esc, twice). */
  var onLeaveMatch: () => Unit = () => ()
  private val LEAVE_CONFIRM_MS = 3000L

  override def invoke(window: Long, key: Int, scancode: Int, action: Int, mods: Int): Unit = {
    if (isChatMode && action == GLFW_PRESS) {
      handleChatKey(key, mods)
      return
    }

    if (action == GLFW_PRESS && key == GLFW_KEY_ESCAPE) {
      // The first press only arms it (the HUD asks for a second), so a stray Esc, say one
      // meant to close chat, can't end a match.
      val now = System.currentTimeMillis()
      if (now < client.leaveConfirmUntil) onLeaveMatch()
      else client.leaveConfirmUntil = now + LEAVE_CONFIRM_MS
      return
    }

    if (action == GLFW_PRESS) {
      pressedKeys.add(key)
      if (key == GLFW_KEY_F11) _f11JustPressed = true
      // F7 cycles graphics quality, so a player on a slow machine can drop it mid-match
      // without restarting or finding a launch flag.
      if (key == GLFW_KEY_F7) com.gridgame.client.gl.RenderQuality.cycleTier()
      if (client.getIsDead) {
        if (client.clientState != ClientState.PLAYING) processRejoin()
      } else {
        // T or Enter opens chat (only when alive and not already in chat)
        if (key == GLFW_KEY_T || key == GLFW_KEY_ENTER) {
          enterChatMode()
          return
        }
        processUseItem(key)
        processBurstShot(key, mods)
        processAbilities(key)
      }
    } else if (action == GLFW_RELEASE) {
      pressedKeys.remove(key)
    }
  }

  private def enterChatMode(): Unit = {
    isChatMode = true
    client.isChatOpen = true
    pressedKeys.clear()
    chatInputBuffer.clear()
    client.chatInputText = ""
  }

  private def exitChatMode(): Unit = {
    isChatMode = false
    client.isChatOpen = false
    client.chatInputText = ""
  }

  private def handleChatKey(key: Int, mods: Int): Unit = {
    key match {
      case GLFW_KEY_ENTER =>
        val msg = chatInputBuffer.toString.trim
        if (msg.nonEmpty) {
          val scope = if ((mods & GLFW_MOD_SHIFT) != 0) ChatScope.TEAM else ChatScope.GAME
          client.sendChatMessage(msg, scope)
        }
        exitChatMode()

      case GLFW_KEY_ESCAPE =>
        exitChatMode()

      case GLFW_KEY_BACKSPACE =>
        if (chatInputBuffer.nonEmpty) {
          chatInputBuffer.deleteCharAt(chatInputBuffer.length - 1)
          client.chatInputText = chatInputBuffer.toString
        }

      case _ => // Character input handled by GLFW char callback
    }
  }

  def clearAllKeys(): Unit = {
    pressedKeys.clear()
    client.setMovementInputActive(false)
    if (isChatMode) exitChatMode()
  }

  /** Called each frame from the game loop. */
  def update(): Unit = {
    // Before the chat check: a barrier keeps turning, and its end is announced, while typing
    if (!client.getIsDead) client.streamBarrier()
    if (isChatMode) return
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

  /** Consume F11 press event. Returns true once per press, then resets. */
  def consumeF11Press(): Boolean = {
    if (_f11JustPressed) { _f11JustPressed = false; true } else false
  }

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

    val moveRate = client.moveStepIntervalMs

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

    // Pure left/right (A or D alone) moves 2x the screen distance of up/down
    // due to the 2:1 isometric tile ratio, so double the delay to equalize visual speed
    val isHorizontal = dx != 0 && dy != 0 && dx != dy
    val effectiveRate = if (isHorizontal) moveRate * 2 else moveRate
    if (now - lastMoveTime < effectiveRate) return

    if (dx != 0 || dy != 0) {
      client.movePlayer(dx, dy)
      // Counted from when the step fell due, not from this frame, or at 60 fps a melee
      // character's 53ms step would wait for the fourth frame and walk at 67ms (Movement)
      lastMoveTime = Movement.nextStepFrom(lastMoveTime + effectiveRate, now, effectiveRate)
    }
  }
}
