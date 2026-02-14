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
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import javafx.stage.Stage

class ClientMain extends Application {

  private var client: GameClient = _
  private var canvas: GameCanvas = _
  private var renderLoop: AnimationTimer = _

  override def start(primaryStage: Stage): Unit = {
    primaryStage.setTitle("Grid Game - Multiplayer 2D")
    primaryStage.setResizable(true)

    showWelcomeScreen(primaryStage)
  }

  private def showWelcomeScreen(stage: Stage): Unit = {
    val root = new VBox(16)
    root.setAlignment(Pos.CENTER)
    root.setPadding(new Insets(40))
    root.setStyle("-fx-background-color: #1e1e2e;")

    val title = new Label("Grid Game")
    title.setFont(Font.font("System", FontWeight.BOLD, 42))
    title.setTextFill(Color.WHITE)

    val subtitle = new Label("Multiplayer 2D")
    subtitle.setFont(Font.font("System", FontWeight.NORMAL, 16))
    subtitle.setTextFill(Color.web("#888"))

    val hostLabel = new Label("Host")
    hostLabel.setTextFill(Color.web("#ccc"))
    hostLabel.setFont(Font.font("System", 14))

    val hostField = new TextField()
    hostField.setPromptText("localhost")
    hostField.setMaxWidth(260)
    hostField.setStyle("-fx-background-color: #2a2a3e; -fx-text-fill: white; -fx-prompt-text-fill: #666; -fx-padding: 8; -fx-background-radius: 4;")

    val portLabel = new Label("Port")
    portLabel.setTextFill(Color.web("#ccc"))
    portLabel.setFont(Font.font("System", 14))

    val portField = new TextField()
    portField.setPromptText("25565")
    portField.setMaxWidth(260)
    portField.setStyle("-fx-background-color: #2a2a3e; -fx-text-fill: white; -fx-prompt-text-fill: #666; -fx-padding: 8; -fx-background-radius: 4;")

    val connectButton = new Button("Connect")
    connectButton.setStyle("-fx-background-color: #4a9eff; -fx-text-fill: white; -fx-font-size: 14; -fx-padding: 8 32; -fx-background-radius: 4; -fx-cursor: hand;")
    connectButton.setDefaultButton(true)

    val statusLabel = new Label("")
    statusLabel.setTextFill(Color.web("#ff6666"))
    statusLabel.setFont(Font.font("System", 12))

    val doConnect = () => {
      val host = if (hostField.getText.trim.isEmpty) "localhost" else hostField.getText.trim
      val portText = portField.getText.trim
      val port = if (portText.isEmpty) {
        Constants.SERVER_PORT
      } else {
        try {
          Integer.parseInt(portText)
        } catch {
          case _: NumberFormatException =>
            statusLabel.setText("Invalid port number")
            -1
        }
      }
      if (port > 0) {
        connectButton.setDisable(true)
        statusLabel.setTextFill(Color.web("#aaa"))
        statusLabel.setText(s"Connecting to $host:$port...")
        startGame(stage, host, port, statusLabel, connectButton)
      }
    }

    connectButton.setOnAction(_ => doConnect())

    // Allow Enter in port field to connect
    portField.setOnAction(_ => doConnect())

    root.getChildren.addAll(title, subtitle,
      new VBox(4, hostLabel, hostField),
      new VBox(4, portLabel, portField),
      connectButton, statusLabel)

    val scene = new Scene(root, 400, 400)
    stage.setScene(scene)
    stage.show()
  }

  private def startGame(stage: Stage, serverHost: String, serverPort: Int,
                        statusLabel: Label, connectButton: Button): Unit = {
    val initialWorld = WorldData.createEmpty(Constants.GRID_SIZE, Constants.GRID_SIZE)

    client = new GameClient(serverHost, serverPort, initialWorld)

    client.setWorldFileListener(worldFileName => {
      println(s"ClientMain: World file listener triggered with: '$worldFileName'")
      handleWorldFileFromServer(worldFileName)
    })

    // Connect on a background thread so the UI stays responsive
    new Thread(() => {
      try {
        client.connect()
        Platform.runLater(() => showGameScene(stage))
      } catch {
        case e: Exception =>
          Platform.runLater(() => {
            statusLabel.setTextFill(Color.web("#ff6666"))
            statusLabel.setText(s"Connection failed: ${e.getMessage}")
            connectButton.setDisable(false)
          })
      }
    }).start()
  }

  private def showGameScene(stage: Stage): Unit = {
    canvas = new GameCanvas(client)

    val root = new StackPane(canvas)
    val scene = new Scene(root, Constants.VIEWPORT_SIZE_PX, Constants.VIEWPORT_SIZE_PX)

    canvas.widthProperty().bind(scene.widthProperty())
    canvas.heightProperty().bind(scene.heightProperty())

    val keyHandler = new KeyboardHandler(client)
    scene.setOnKeyPressed(keyHandler)
    scene.setOnKeyReleased(keyHandler)

    val mouseHandler = new MouseHandler(client, canvas)
    scene.setOnMousePressed(mouseHandler)
    scene.setOnMouseReleased(mouseHandler)
    scene.setOnMouseDragged(mouseHandler)
    scene.setOnMouseMoved(mouseHandler)

    scene.addEventFilter(KeyEvent.KEY_PRESSED, new EventHandler[KeyEvent] {
      override def handle(event: KeyEvent): Unit = {
        if (event.getCode == KeyCode.F11) {
          stage.setFullScreen(!stage.isFullScreen)
        }
      }
    })

    stage.setScene(scene)

    val frameIntervalNs = 1_000_000_000L / 60
    var lastFrameTime = 0L
    renderLoop = new AnimationTimer() {
      override def handle(now: Long): Unit = {
        if (now - lastFrameTime >= frameIntervalNs) {
          lastFrameTime = now
          keyHandler.update()
          canvas.render()
        }
      }
    }
    renderLoop.start()

    stage.setOnCloseRequest(_ => {
      println("Closing application...")
      client.disconnect()
      renderLoop.stop()
    })

    println("Client started successfully!")
    println("Waiting for world info from server...")
    println("Use WASD to move your character.")
  }

  private def handleWorldFileFromServer(worldFileName: String): Unit = {
    if (worldFileName.isEmpty) {
      println("Server did not specify a world, using default")
      return
    }
    println(s"Server requested world: $worldFileName")
    val worldPath = "worlds/" + worldFileName
    try {
      val world = WorldLoader.load(worldPath)
      client.setWorld(world)
      println(s"Loaded world: ${world.name} (${world.width}x${world.height})")
    } catch {
      case e: Exception =>
        println(s"Failed to load world $worldPath: ${e.getMessage}, using default")
        client.setWorld(WorldData.createEmpty(Constants.GRID_SIZE, Constants.GRID_SIZE))
    }
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
