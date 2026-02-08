package com.gridgame.client

import com.gridgame.client.input.KeyboardHandler
import com.gridgame.client.input.MouseHandler
import com.gridgame.client.ui.GameCanvas
import com.gridgame.common.Constants
import com.gridgame.common.model.WorldData
import com.gridgame.common.world.WorldLoader

import javafx.animation.AnimationTimer
import javafx.application.Application
import javafx.application.Platform
import javafx.scene.Scene
import javafx.scene.layout.StackPane
import javafx.stage.Stage

import java.io.File

class ClientMain extends Application {

  private var client: GameClient = _
  private var canvas: GameCanvas = _

  override def start(primaryStage: Stage): Unit = {
    val params = getParameters
    val named = params.getNamed
    val unnamed = params.getUnnamed

    val serverHost = named.getOrDefault("host", "localhost")
    val serverPort = Integer.parseInt(named.getOrDefault("port", String.valueOf(Constants.SERVER_PORT)))

    // Start with an empty/default world - server will tell us which world to load
    val initialWorld = WorldData.createEmpty(Constants.GRID_SIZE, Constants.GRID_SIZE)

    client = new GameClient(serverHost, serverPort, initialWorld)

    // Set up listener for world file changes from server
    client.setWorldFileListener(worldFileName => {
      println(s"ClientMain: World file listener triggered with: '$worldFileName'")
      Platform.runLater(new Runnable {
        def run(): Unit = {
          println(s"ClientMain: Platform.runLater executing for world: '$worldFileName'")
          handleWorldFileFromServer(worldFileName)
        }
      })
    })

    canvas = new GameCanvas(client)

    val root = new StackPane(canvas)
    val scene = new Scene(root, Constants.VIEWPORT_SIZE_PX, Constants.VIEWPORT_SIZE_PX)

    val keyHandler = new KeyboardHandler(client)
    scene.setOnKeyPressed(keyHandler)
    scene.setOnKeyReleased(keyHandler)

    val mouseHandler = new MouseHandler(client)
    scene.setOnMousePressed(mouseHandler)

    primaryStage.setTitle("Grid Game - Multiplayer 2D")
    primaryStage.setScene(scene)
    primaryStage.setResizable(false)
    primaryStage.show()

    client.connect()

    val renderLoop = new AnimationTimer() {
      override def handle(now: Long): Unit = {
        canvas.render()
      }
    }
    renderLoop.start()

    primaryStage.setOnCloseRequest(_ => {
      println("Closing application...")
      client.disconnect()
      renderLoop.stop()
    })

    println("Client started successfully!")
    println("Waiting for world info from server...")
    println("Use WASD to move your character.")
  }

  private def handleWorldFileFromServer(worldFileName: String): Unit = {
    println(s"handleWorldFileFromServer called with: '$worldFileName'")
    if (worldFileName.isEmpty) {
      println("Server did not specify a world, using default")
      return
    }
    println(s"Server requested world: $worldFileName")
    val worldPath = "worlds/" + worldFileName
    println(s"Attempting to load world from: $worldPath")
    val world = loadWorld(worldPath)
    println(s"loadWorld returned: ${world.name}")
    client.setWorld(world)
    println(s"Loaded world: ${world.name} (${world.width}x${world.height})")
  }

  private def loadWorld(worldFile: String): WorldData = {
    if (worldFile.nonEmpty) {
      val resolvedPath = resolveWorldPath(worldFile)
      if (resolvedPath.nonEmpty) {
        println(s"Loading world from: $resolvedPath")
        WorldLoader.loadFromFile(resolvedPath)
      } else {
        println(s"World file not found: $worldFile, using default world")
        WorldData.createEmpty(Constants.GRID_SIZE, Constants.GRID_SIZE)
      }
    } else {
      println("No world file specified, using default world")
      WorldData.createEmpty(Constants.GRID_SIZE, Constants.GRID_SIZE)
    }
  }

  private def resolveWorldPath(worldFile: String): String = {
    println(s"resolveWorldPath called with: '$worldFile'")

    // Try the path as-is first (absolute or relative to current dir)
    val direct = new File(worldFile)
    println(s"Checking direct path: ${direct.getAbsolutePath} exists=${direct.exists()}")
    if (direct.exists()) {
      return direct.getAbsolutePath
    }

    // Try relative to BUILD_WORKING_DIRECTORY (set by Bazel)
    val buildWorkDir = System.getenv("BUILD_WORKING_DIRECTORY")
    println(s"BUILD_WORKING_DIRECTORY: $buildWorkDir")
    if (buildWorkDir != null) {
      val fromWorkDir = new File(buildWorkDir, worldFile)
      println(s"Checking BUILD_WORKING_DIRECTORY path: ${fromWorkDir.getAbsolutePath} exists=${fromWorkDir.exists()}")
      if (fromWorkDir.exists()) {
        return fromWorkDir.getAbsolutePath
      }
    }

    // Not found
    println(s"World file not found: $worldFile")
    ""
  }

  override def stop(): Unit = {
    if (client != null) {
      client.disconnect()
    }
  }
}

object ClientMain {
  def main(args: Array[String]): Unit = {
    Application.launch(classOf[ClientMain], args: _*)
  }
}
