package com.gridgame.client.input

import com.gridgame.common.Constants
import com.gridgame.common.model.ItemType
import com.gridgame.common.model.Movement
import com.gridgame.common.protocol.ChatScope

import org.lwjgl.glfw.GLFW._
import org.lwjgl.glfw.GLFWKeyCallback

import scala.collection.mutable
import com.gridgame.client.diagnostics.Diagnostics
import com.gridgame.client.game.{ClientState, GameClient}
import com.gridgame.common.diagnostics.Recording.InputDevice

/**
 * GLFW keyboard handler. Mirrors KeyboardHandler logic but uses GLFW key codes.
 * Installed as a GLFW key callback on the game window.
 */
class GLKeyboardHandler(client: GameClient) extends GLFWKeyCallback {
  private val pressedKeys: mutable.Set[Int] = mutable.Set.empty
  // The keys held that a match's recording keeps (Recording.Keys), kept up as they go down and up
  private var _heldMask = 0
  private var lastMoveTime: Long = 0
  private var lastBurstTime: Long = 0
  private var _f11JustPressed: Boolean = false

  // Chat input state
  var isChatMode: Boolean = false
  val chatInputBuffer = new StringBuilder(Constants.MAX_CHAT_MESSAGE_LEN)

  /** Set by the game scene: leaves the match (Esc, twice). */
  var onLeaveMatch: () => Unit = () => ()
  /** Set by the game scene: the player marks this moment for a problem report (F8). */
  var onMarkMoment: () => Unit = () => ()
  private val LEAVE_CONFIRM_MS = 3000L

  override def invoke(window: Long, key: Int, scancode: Int, action: Int, mods: Int): Unit = {
    // Into the match's recording, but for what is typed into chat, which is the player's own
    val recorder = Diagnostics.recorder
    if (recorder != null) recorder.input(InputDevice.Keyboard, action, if (isChatMode) -1 else key, mods)

    // F8 marks the moment, whatever else is going on: the moment something looked wrong
    if (action == GLFW_PRESS && key == GLFW_KEY_F8) {
      onMarkMoment()
      return
    }

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

    if (action == GLFW_PRESS) _heldMask |= GLKeyboardHandler.maskOf(key)
    else if (action == GLFW_RELEASE) _heldMask &= ~GLKeyboardHandler.maskOf(key)

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
    _heldMask = 0
    // Typing isn't attacking: whatever was aimed or held is put away without going off
    client.cancelAims()
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
    _heldMask = 0
    client.setMovementInputActive(false)
    // A key or button let go of in another window never comes up in this one, and the click that
    // brings this one back is no cast of what was aimed before it went
    client.cancelAims()
    if (isChatMode) exitChatMode()
  }

  /** Called each frame from the game loop. */
  def update(): Unit = {
    // A barrier keeps turning, and its end is announced, while typing
    if (!client.getIsDead) client.streamBarrier()
    if (!isChatMode && !client.getIsDead) {
      if (client.isSwooping) {
        client.tickSwoop()
      } else {
        processMovement()
      }
      // Said again every 100ms while it lasts. A phase too: its cast is one datagram, and with it
      // lost, a player standing still in their phase was never phased on the server — hit through
      // it all, while their own client showed them untouchable and held their fire
      if (client.isCharging || client.hasShield || client.hasGemBoost || client.isPhased) {
        client.sendChargingUpdate()
      }
    }
    // Where we are, said again now and then whatever we are doing, typing too: last, so what this
    // frame has sent already counts (GameClient.resendPositionIfQuiet)
    if (!client.getIsDead) client.resendPositionIfQuiet()
  }

  /** The keys held that a match's recording keeps, as Recording.Keys has them. */
  def heldMask: Int = _heldMask

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

  /** Q and E going down: taken up to be aimed, and cast by the next click, or cast now with nothing
    * to aim (Attacks). Their coming up does nothing. R has both ready again, once a minute. */
  private def processAbilities(key: Int): Unit = {
    key match {
      case GLFW_KEY_Q => client.pressAbility(0)
      case GLFW_KEY_E => client.pressAbility(1)
      case GLFW_KEY_R => client.pressReset()
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

object GLKeyboardHandler {
  import com.gridgame.common.diagnostics.Recording.Keys

  /** A key's bit in a match recording's frames (Recording.Keys), 0 for one it doesn't keep. */
  def maskOf(key: Int): Int = key match {
    case GLFW_KEY_W => Keys.W
    case GLFW_KEY_A => Keys.A
    case GLFW_KEY_S => Keys.S
    case GLFW_KEY_D => Keys.D
    case GLFW_KEY_SPACE => Keys.Space
    case GLFW_KEY_LEFT_SHIFT | GLFW_KEY_RIGHT_SHIFT => Keys.Shift
    case GLFW_KEY_Q => Keys.Q
    case GLFW_KEY_E => Keys.E
    case GLFW_KEY_R => Keys.R
    case _ => 0
  }
}
