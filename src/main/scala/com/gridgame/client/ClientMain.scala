package com.gridgame.client

import com.gridgame.client.input.KeyboardHandler
import com.gridgame.client.ui.GameCanvas
import com.gridgame.common.Constants

import javafx.animation.AnimationTimer
import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.layout.StackPane
import javafx.stage.Stage

class ClientMain extends Application {

  private var client: GameClient = _
  private var canvas: GameCanvas = _

  override def start(primaryStage: Stage): Unit = {
    val params = getParameters
    val named = params.getNamed
    val unnamed = params.getUnnamed

    val serverHost = named.getOrDefault("host", "localhost")
    val serverPort = Integer.parseInt(named.getOrDefault("port", String.valueOf(Constants.SERVER_PORT)))

    client = new GameClient(serverHost, serverPort)

    canvas = new GameCanvas(client)

    val root = new StackPane(canvas)
    val scene = new Scene(root, Constants.VIEWPORT_SIZE_PX, Constants.VIEWPORT_SIZE_PX)

    val keyHandler = new KeyboardHandler(client)
    scene.setOnKeyPressed(keyHandler)
    scene.setOnKeyReleased(keyHandler)

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
    println("Use WASD to move your character.")
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
