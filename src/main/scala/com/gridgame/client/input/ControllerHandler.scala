package com.gridgame.client.input

import com.gridgame.client.GameClient
import com.gridgame.common.Constants
import com.gridgame.common.model.ItemType
import com.gridgame.common.model.ProjectileDef

import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW._
import org.lwjgl.glfw.GLFWGamepadState

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer

class ControllerHandler(client: GameClient) {
  private val DEADZONE = 0.25f
  private val AIM_DISTANCE = 10.0

  private var initialized = false
  private var gamepadId: Int = -1
  private var state: GLFWGamepadState = _

  // Previous button states for edge detection (15 GLFW gamepad buttons)
  private val prevButtons = new Array[Byte](15)

  // Timing
  private var lastMoveTime: Long = 0
  private var lastShootTime: Long = 0
  private var lastBurstTime: Long = 0
  private var lastScanTime: Long = 0

  // Trigger state for edge detection (triggers are axes, not buttons)
  private var prevRightTrigger: Float = -1.0f

  // Whether the gamepad was auto-mapped (Y-axis needs inversion on auto-mapped controllers)
  private var autoMapped = false

  // Diagnostic: log mapped gamepad axis values for first few seconds
  private var diagFrames: Int = 0
  private var lastDiagTime: Long = 0

  private def canCharge: Boolean = {
    val pt = client.getSelectedCharacterDef.primaryProjectileType
    ProjectileDef.get(pt).chargeSpeedScaling.isDefined
  }

  def init(): Unit = {
    try {
      glfwInitHint(GLFW_COCOA_MENUBAR, GLFW_FALSE)
      if (!glfwInit()) {
        println("ControllerHandler: GLFW init failed, controller support disabled")
        return
      }
      state = GLFWGamepadState.create()
      loadGamepadMappings()
      initialized = true
      println("ControllerHandler: GLFW initialized successfully")
    } catch {
      case e: Throwable =>
        println(s"ControllerHandler: Failed to initialize GLFW: ${e.getClass.getName}: ${e.getMessage}")
    }
  }

  private def loadGamepadMappings(): Unit = {
    try {
      val stream = resolveResourceStream("sprites/gamecontrollerdb.txt")
      if (stream == null) {
        println("ControllerHandler: gamecontrollerdb.txt not found, using GLFW built-in mappings only")
        return
      }
      val bytes = stream.readAllBytes()
      stream.close()
      // GLFW requires a null-terminated direct ByteBuffer
      val buf = BufferUtils.createByteBuffer(bytes.length + 1)
      buf.put(bytes)
      buf.put(0.toByte)
      buf.flip()
      if (glfwUpdateGamepadMappings(buf)) {
        println(s"ControllerHandler: Loaded ${bytes.length} bytes of gamepad mappings")
      } else {
        println("ControllerHandler: glfwUpdateGamepadMappings returned false")
      }
    } catch {
      case e: Throwable =>
        println(s"ControllerHandler: Failed to load gamepad mappings: ${e.getMessage}")
    }
  }

  /** Generate and register a standard Xbox-layout mapping for an unmapped joystick. */
  private def tryAutoMap(jid: Int): Boolean = {
    val guid = glfwGetJoystickGUID(jid)
    if (guid == null) return false
    val name = glfwGetJoystickName(jid)

    val axes = glfwGetJoystickAxes(jid)
    val buttons = glfwGetJoystickButtons(jid)
    val numAxes = if (axes != null) axes.limit() else 0
    val numButtons = if (buttons != null) buttons.limit() else 0

    // Log raw resting axis values for diagnostics
    val axisVals = if (axes != null) {
      (0 until numAxes).map(i => f"a$i=${axes.get(i)}%.2f").mkString(", ")
    } else "none"
    println(s"ControllerHandler: Joystick $jid GUID=$guid name='$name' axes=$numAxes buttons=$numButtons")
    println(s"ControllerHandler: Raw axis rest values: $axisVals")

    // Need at least 4 axes (two sticks) and 1 button to be useful
    if (numAxes < 4 || numButtons < 1) return false

    val os = System.getProperty("os.name", "").toLowerCase
    val platform = if (os.contains("mac")) "Mac OS X"
                   else if (os.contains("win")) "Windows"
                   else "Linux"

    // Classify each axis by its rest value: triggers rest near -1.0, sticks rest near 0.0
    // Then assign stick axes as LX,LY,RX,RY and trigger axes as LT,RT in order.
    val stickAxes = new java.util.ArrayList[Int]()
    val triggerAxes = new java.util.ArrayList[Int]()
    if (axes != null) {
      for (i <- 0 until numAxes) {
        if (Math.abs(axes.get(i)) > 0.5) triggerAxes.add(i)
        else stickAxes.add(i)
      }
    }
    println(s"ControllerHandler: Stick axes: $stickAxes, Trigger axes: $triggerAxes")

    val sb = new StringBuilder()
    sb.append(s"$guid,$name,platform:$platform")
    if (stickAxes.size() >= 1) sb.append(s",leftx:a${stickAxes.get(0)}")
    if (stickAxes.size() >= 2) sb.append(s",lefty:a${stickAxes.get(1)}")
    if (stickAxes.size() >= 3) sb.append(s",rightx:a${stickAxes.get(2)}")
    if (stickAxes.size() >= 4) sb.append(s",righty:a${stickAxes.get(3)}")
    if (triggerAxes.size() >= 1) sb.append(s",lefttrigger:a${triggerAxes.get(0)}")
    if (triggerAxes.size() >= 2) sb.append(s",righttrigger:a${triggerAxes.get(1)}")
    if (numButtons >= 1)  sb.append(",a:b0")
    if (numButtons >= 2)  sb.append(",b:b1")
    if (numButtons >= 3)  sb.append(",x:b2")
    if (numButtons >= 4)  sb.append(",y:b3")
    if (numButtons >= 5)  sb.append(",leftshoulder:b4")
    if (numButtons >= 6)  sb.append(",rightshoulder:b5")
    if (numButtons >= 7)  sb.append(",back:b6")
    if (numButtons >= 8)  sb.append(",start:b7")
    if (numButtons >= 9)  sb.append(",leftstick:b8")
    if (numButtons >= 10) sb.append(",rightstick:b9")
    if (numButtons >= 11) sb.append(",guide:b10")
    if (numButtons >= 12) sb.append(",dpup:b11")
    if (numButtons >= 13) sb.append(",dpdown:b12")
    if (numButtons >= 14) sb.append(",dpleft:b13")
    if (numButtons >= 15) sb.append(",dpright:b14")
    sb.append(",")

    val mapping = sb.toString()
    println(s"ControllerHandler: Auto-mapping: $mapping")

    val mappingBytes = mapping.getBytes("UTF-8")
    val buf = BufferUtils.createByteBuffer(mappingBytes.length + 1)
    buf.put(mappingBytes)
    buf.put(0.toByte)
    buf.flip()

    if (glfwUpdateGamepadMappings(buf)) {
      println(s"ControllerHandler: Auto-mapping registered successfully")
      glfwJoystickIsGamepad(jid)
    } else {
      println(s"ControllerHandler: Auto-mapping registration failed")
      false
    }
  }

  private def resolveResourceStream(relativePath: String): InputStream = {
    val direct = new File(relativePath)
    if (direct.exists()) return new FileInputStream(direct)

    val buildWorkDir = System.getenv("BUILD_WORKING_DIRECTORY")
    if (buildWorkDir != null) {
      val fromWorkDir = new File(buildWorkDir, relativePath)
      if (fromWorkDir.exists()) return new FileInputStream(fromWorkDir)
    }

    getClass.getClassLoader.getResourceAsStream(relativePath)
  }

  def update(): Unit = {
    if (!initialized) return

    // Poll GLFW events - required for joystick/gamepad discovery and state updates
    glfwPollEvents()

    // Scan for connected gamepad if we don't have one (throttled to once per second)
    if (gamepadId < 0) {
      val now = System.currentTimeMillis()
      if (now - lastScanTime < 1000) return
      lastScanTime = now

      var i = GLFW_JOYSTICK_1
      while (i <= GLFW_JOYSTICK_LAST && gamepadId < 0) {
        if (glfwJoystickPresent(i)) {
          if (glfwJoystickIsGamepad(i)) {
            gamepadId = i
            println(s"ControllerHandler: Gamepad detected: ${glfwGetGamepadName(i)}")
          } else {
            // Try auto-mapping with standard Xbox layout
            if (tryAutoMap(i)) {
              gamepadId = i
              autoMapped = true
              println(s"ControllerHandler: Gamepad detected via auto-map: ${glfwGetGamepadName(i)}")
            }
          }
        }
        i += 1
      }
      if (gamepadId < 0) return
    }

    // Check gamepad is still connected
    if (!glfwJoystickIsGamepad(gamepadId)) {
      println("ControllerHandler: Gamepad disconnected")
      gamepadId = -1
      autoMapped = false
      return
    }

    if (!glfwGetGamepadState(gamepadId, state)) return

    // Log mapped axis values once per second for first 5 seconds
    if (diagFrames < 5) {
      val now = System.currentTimeMillis()
      if (now - lastDiagTime >= 1000) {
        lastDiagTime = now
        diagFrames += 1
        val lx = state.axes(GLFW_GAMEPAD_AXIS_LEFT_X)
        val ly = state.axes(GLFW_GAMEPAD_AXIS_LEFT_Y)
        val rx = state.axes(GLFW_GAMEPAD_AXIS_RIGHT_X)
        val ry = state.axes(GLFW_GAMEPAD_AXIS_RIGHT_Y)
        val lt = state.axes(GLFW_GAMEPAD_AXIS_LEFT_TRIGGER)
        val rt = state.axes(GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER)
        val btns = (0 until 15).map(i => state.buttons(i).toInt).mkString(",")
        println(f"ControllerHandler DIAG: LX=$lx%.3f LY=$ly%.3f RX=$rx%.3f RY=$ry%.3f LT=$lt%.3f RT=$rt%.3f btns=[$btns]")
      }
    }

    if (!client.getIsDead) {
      processMovement()
      processAiming()
      processShooting()
      processAbilities()
      processItems()
    }

    // Save button states for next frame's edge detection
    for (i <- 0 until 15) {
      prevButtons(i) = state.buttons(i)
    }
    prevRightTrigger = state.axes(GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER)
  }

  private def justPressed(button: Int): Boolean = {
    state.buttons(button) == GLFW_PRESS.toByte && prevButtons(button) != GLFW_PRESS.toByte
  }

  private def processMovement(): Unit = {
    if (client.isSwooping) {
      client.tickSwoop()
      return
    }

    val now = System.currentTimeMillis()
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

    // Left stick for movement
    val rawX = state.axes(GLFW_GAMEPAD_AXIS_LEFT_X)
    val rawY = if (autoMapped) -state.axes(GLFW_GAMEPAD_AXIS_LEFT_Y) else state.axes(GLFW_GAMEPAD_AXIS_LEFT_Y)

    val magnitude = Math.sqrt(rawX * rawX + rawY * rawY).toFloat
    if (magnitude < DEADZONE) {
      client.setMovementInputActive(false)
      return
    }

    // Normalize and discretize to 8-way screen direction
    val nx = rawX / magnitude
    val ny = rawY / magnitude
    val sx = if (nx > 0.38f) 1 else if (nx < -0.38f) -1 else 0
    val sy = if (ny > 0.38f) 1 else if (ny < -0.38f) -1 else 0

    if (sx == 0 && sy == 0) {
      client.setMovementInputActive(false)
      return
    }

    // Convert screen direction to isometric world direction (same as WASD)
    var dx = sx + sy
    var dy = -sx + sy
    dx = Math.max(-1, Math.min(1, dx))
    dy = Math.max(-1, Math.min(1, dy))

    client.setMovementInputActive(true)
    client.movePlayer(dx, dy)
    lastMoveTime = now
  }

  private def processAiming(): Unit = {
    // Right stick for aiming
    val rawX = state.axes(GLFW_GAMEPAD_AXIS_RIGHT_X)
    val rawY = if (autoMapped) -state.axes(GLFW_GAMEPAD_AXIS_RIGHT_Y) else state.axes(GLFW_GAMEPAD_AXIS_RIGHT_Y)

    val magnitude = Math.sqrt(rawX * rawX + rawY * rawY)
    if (magnitude < DEADZONE) return

    val nx = rawX / magnitude
    val ny = rawY / magnitude

    // Convert screen-space stick direction to world-space using inverse isometric transform
    val worldDx = nx + 2.0 * ny
    val worldDy = 2.0 * ny - nx

    val worldMag = Math.sqrt(worldDx * worldDx + worldDy * worldDy)
    if (worldMag < 0.001) return

    val normDx = worldDx / worldMag
    val normDy = worldDy / worldMag

    val playerPos = client.getLocalPosition
    val aimX = playerPos.getX + normDx * AIM_DISTANCE
    val aimY = playerPos.getY + normDy * AIM_DISTANCE
    client.setMouseWorldPosition(aimX, aimY)
  }

  private def processShooting(): Unit = {
    val trigger = state.axes(GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER)
    val triggerPressed = trigger > 0.5f
    val triggerWasPressed = prevRightTrigger > 0.5f

    // Edge detect: trigger just pressed
    if (triggerPressed && !triggerWasPressed) {
      val now = System.currentTimeMillis()
      if (now - lastShootTime < Constants.SHOOT_COOLDOWN_MS) return

      if (canCharge) {
        client.startCharging()
      } else {
        lastShootTime = now
        fireTowardAim(0)
      }
    }

    // Edge detect: trigger just released
    if (!triggerPressed && triggerWasPressed) {
      if (client.isCharging) {
        val chargeLevel = client.getChargeLevel
        client.cancelCharging()
        lastShootTime = System.currentTimeMillis()
        fireTowardAim(chargeLevel)
      }
    }
  }

  private def fireTowardAim(chargeLevel: Int): Unit = {
    val playerPos = client.getLocalPosition
    val deltaX = client.getMouseWorldX - playerPos.getX
    val deltaY = client.getMouseWorldY - playerPos.getY
    val magnitude = Math.sqrt(deltaX * deltaX + deltaY * deltaY)
    if (magnitude < 0.001) return
    val dx = (deltaX / magnitude).toFloat
    val dy = (deltaY / magnitude).toFloat
    client.shootToward(dx, dy, chargeLevel)
  }

  private def processAbilities(): Unit = {
    // A button -> Q ability (slot 0)
    if (justPressed(GLFW_GAMEPAD_BUTTON_A)) {
      client.shootAbility(0)
    }
    // B button -> E ability (slot 1)
    if (justPressed(GLFW_GAMEPAD_BUTTON_B)) {
      client.shootAbility(1)
    }
    // X button -> burst shot
    if (justPressed(GLFW_GAMEPAD_BUTTON_X)) {
      val now = System.currentTimeMillis()
      if (now - lastBurstTime >= Constants.BURST_SHOT_COOLDOWN_MS) {
        client.shootAllDirections()
        lastBurstTime = now
      }
    }
  }

  private def processItems(): Unit = {
    // D-pad -> items
    if (justPressed(GLFW_GAMEPAD_BUTTON_DPAD_UP)) {
      client.useItem(ItemType.Heart.id)
    }
    if (justPressed(GLFW_GAMEPAD_BUTTON_DPAD_RIGHT)) {
      client.useItem(ItemType.Star.id)
    }
    if (justPressed(GLFW_GAMEPAD_BUTTON_DPAD_DOWN)) {
      client.useItem(ItemType.Gem.id)
    }
    if (justPressed(GLFW_GAMEPAD_BUTTON_DPAD_LEFT)) {
      client.useItem(ItemType.Shield.id)
    }
  }

  def cleanup(): Unit = {
    if (initialized) {
      glfwTerminate()
      initialized = false
    }
  }
}
