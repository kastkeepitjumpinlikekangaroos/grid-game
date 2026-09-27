package com.gridgame.client.ui

import com.gridgame.client.audio.AudioManager
import com.gridgame.client.ui.UiActivity
import com.gridgame.client.i18n.Messages
import com.gridgame.common.WorldRegistry
import com.gridgame.common.protocol.LobbyFailure
import com.gridgame.common.world.WorldLoader

import javafx.animation.AnimationTimer
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.canvas.Canvas
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.control.TextField
import javafx.util.Callback
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import javafx.stage.Stage
import com.gridgame.client.audio.Sound

/**
 * The menus' look: the palette and control styles every screen uses, and the small widgets built
 * from them (hover effects, styled combos, accent lines, stat boxes, a map's preview), plus the few
 * bits of behaviour every screen shares (fading a scene in, a stepped animation loop, a status line).
 * Stateless: a screen imports it (`import Theme._`).
 */
private[client] object Theme {
  val lobbyDurations = Seq(1, 3, 5, 10, 15, 20)

  def makeDurationCombo(selectedMinutes: Int): ComboBox[String] = {
    val combo = new ComboBox[String](FXCollections.observableArrayList(lobbyDurations.map(d => Messages.t("{0} min", d.toString)): _*))
    combo.getSelectionModel.select(Math.max(0, lobbyDurations.indexOf(selectedMinutes)))
    combo.setMaxWidth(Double.MaxValue)
    styleCombo(combo)
    combo
  }

  def selectedDuration(combo: ComboBox[String]): Int =
    lobbyDurations(Math.max(0, combo.getSelectionModel.getSelectedIndex))

  def showStatus(label: Label, text: String, error: Boolean): Unit = {
    label.setTextFill(Color.web(if (error) "#e84057" else "#8899bb"))
    label.setText(text)
  }

  def lobbyFailureMessage(reason: Byte): String = reason match {
    case LobbyFailure.RATE_LIMITED => Messages.t("Please wait a moment and try again")
    case LobbyFailure.LOBBY_FULL => Messages.t("That lobby is full")
    case LobbyFailure.NOT_JOINABLE => Messages.t("That lobby is no longer open")
    case LobbyFailure.SERVER_FULL => Messages.t("The server has no room for another lobby")
    case LobbyFailure.ALREADY_IN_LOBBY => Messages.t("You are already in a lobby")
    case LobbyFailure.INVALID_NAME => Messages.t("Invalid lobby name")
    case _ => Messages.t("Something went wrong, please try again")
  }

  // Lets a scroll pane show its page's background. The other way, defining -fx-background on
  // the pane, recolours every label inside: Modena derives label text from that looked-up
  // colour, and taken from an inline style it outranks setTextFill, so all the coloured text
  // in a scroll pane (scoreboard ranks and teams, roster, chat, the ELO badge) came out white.
  val appStylesheet = "data:text/css;base64," + java.util.Base64.getEncoder.encodeToString(
    ".scroll-pane > .viewport { -fx-background-color: transparent; }".getBytes(java.nio.charset.StandardCharsets.UTF_8))

  def newScene(root: javafx.scene.Parent): Scene = {
    val scene = new Scene(root)
    scene.getStylesheets.add(appStylesheet)
    scene
  }

  def placeholderLabel(text: String): Label = {
    val label = new Label(text)
    label.setTextFill(Color.web("#667788"))
    label.setFont(Font.font("Exo 2", 14))
    label
  }

  // -- Enhanced color palette & styles --
  val darkBg = "-fx-background-color: linear-gradient(to bottom, #1a1a2e 0%, #151528 50%, #111124 100%);"

  val cardBg = "-fx-background-color: #20203a; -fx-background-radius: 16; -fx-border-color: rgba(255,255,255,0.07); -fx-border-radius: 16; -fx-border-width: 1; -fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.5), 24, 0, 0, 8);"

  val cardBgSubtle = "-fx-background-color: #1c1c34; -fx-background-radius: 12; -fx-border-color: rgba(255,255,255,0.05); -fx-border-radius: 12; -fx-border-width: 1; -fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.3), 12, 0, 0, 4);"

  val fieldStyle = "-fx-background-color: #181830; -fx-text-fill: #eef; -fx-font-size: 14; -fx-prompt-text-fill: #778; -fx-padding: 12 14; -fx-background-radius: 8; -fx-border-color: rgba(255,255,255,0.08); -fx-border-radius: 8; -fx-border-width: 1;"

  val fieldFocusStyle = "-fx-background-color: #181830; -fx-text-fill: #eef; -fx-font-size: 14; -fx-prompt-text-fill: #778; -fx-padding: 12 14; -fx-background-radius: 8; -fx-border-color: #4a9eff; -fx-border-radius: 8; -fx-border-width: 2; -fx-effect: dropshadow(gaussian, rgba(74, 158, 255, 0.3), 16, 0, 0, 0);"

  val buttonStyle = "-fx-background-color: linear-gradient(to bottom, #5aadff, #3a8eef); -fx-text-fill: white; -fx-font-size: 14; -fx-font-weight: bold; -fx-padding: 11 28; -fx-background-radius: 8; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(74, 158, 255, 0.35), 12, 0, 0, 3);"

  val buttonHoverStyle = "-fx-background-color: linear-gradient(to bottom, #6db8ff, #4a9eff); -fx-text-fill: white; -fx-font-size: 14; -fx-font-weight: bold; -fx-padding: 11 28; -fx-background-radius: 8; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(74, 158, 255, 0.55), 18, 0, 0, 4);"

  val buttonRedStyle = "-fx-background-color: linear-gradient(to bottom, #f05068, #d83850); -fx-text-fill: white; -fx-font-size: 14; -fx-font-weight: bold; -fx-padding: 11 28; -fx-background-radius: 8; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(232, 64, 87, 0.35), 12, 0, 0, 3);"

  val buttonRedHoverStyle = "-fx-background-color: linear-gradient(to bottom, #ff6078, #e84860); -fx-text-fill: white; -fx-font-size: 14; -fx-font-weight: bold; -fx-padding: 11 28; -fx-background-radius: 8; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(232, 64, 87, 0.55), 18, 0, 0, 4);"

  val buttonGreenStyle = "-fx-background-color: linear-gradient(to bottom, #3ddb80, #28b865); -fx-text-fill: white; -fx-font-size: 14; -fx-font-weight: bold; -fx-padding: 11 28; -fx-background-radius: 8; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(46, 204, 113, 0.35), 12, 0, 0, 3);"

  val buttonGreenHoverStyle = "-fx-background-color: linear-gradient(to bottom, #4deb90, #38c875); -fx-text-fill: white; -fx-font-size: 14; -fx-font-weight: bold; -fx-padding: 11 28; -fx-background-radius: 8; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(46, 204, 113, 0.55), 18, 0, 0, 4);"

  val buttonGhostStyle = "-fx-background-color: rgba(255,255,255,0.06); -fx-text-fill: #99aabb; -fx-font-size: 13; -fx-font-weight: bold; -fx-padding: 9 20; -fx-background-radius: 8; -fx-cursor: hand; -fx-border-color: rgba(255,255,255,0.1); -fx-border-radius: 8; -fx-border-width: 1;"

  val buttonGhostHoverStyle = "-fx-background-color: rgba(255,255,255,0.12); -fx-text-fill: #ccdde8; -fx-font-size: 13; -fx-font-weight: bold; -fx-padding: 9 20; -fx-background-radius: 8; -fx-cursor: hand; -fx-border-color: rgba(255,255,255,0.2); -fx-border-radius: 8; -fx-border-width: 1;"

  val labelStyle = "-fx-text-fill: #bbc; -fx-font-size: 14;"

  val comboStyle = "-fx-background-color: #181830; -fx-text-fill: white; -fx-font-size: 14; -fx-padding: 8; -fx-background-radius: 8; -fx-border-color: rgba(255,255,255,0.08); -fx-border-radius: 8; -fx-border-width: 1;"

  // No -fx-control-inner-background here: list cells derive their labels' text colour from it,
  // so defining it inline turned every coloured label in a lobby card white (see appStylesheet).
  // The cell factories paint each cell's background themselves.
  val listViewCss = "-fx-background-color: #1a1a32; -fx-font-size: 14; -fx-background-radius: 12; -fx-border-color: rgba(255,255,255,0.05); -fx-border-radius: 12; -fx-border-width: 1;"

  val sectionHeaderStyle = "-fx-text-fill: #99aabb; -fx-font-size: 13; -fx-font-weight: bold;"

  def addHoverEffect(btn: Button, normalStyle: String, hoverStyle: String): Unit = {
    btn.setStyle(normalStyle)
    btn.setOnMouseEntered(_ => btn.setStyle(hoverStyle))
    btn.setOnMouseExited(_ => {
      btn.setStyle(normalStyle)
      btn.setScaleX(1.0); btn.setScaleY(1.0)
    })
    btn.setOnMousePressed(_ => {
      btn.setScaleX(0.95); btn.setScaleY(0.95)
    })
    btn.setOnMouseReleased(_ => {
      btn.setScaleX(1.0); btn.setScaleY(1.0)
    })
  }

  /** Sound on/off toggle. `AudioManager` is a global, so one button covers the
    * JavaFX screens and the in-game GLFW window alike, and the choice is
    * persisted so it survives a restart. */
  def createSoundToggleButton(): Button = {
    val btn = new Button()
    def soundLabel: String =
      if (AudioManager.isMuted) Messages.t("Sound: Off") else Messages.t("Sound: On")
    btn.setText(soundLabel)
    addHoverEffect(btn, buttonGhostStyle, buttonGhostHoverStyle)
    btn.setOnAction(_ => {
      AudioManager.toggleMuted()
      btn.setText(soundLabel)
    })
    btn
  }

  def addFieldFocusEffect(field: TextField): Unit = {
    field.setStyle(fieldStyle)
    field.focusedProperty().addListener((_, _, focused) => {
      field.setStyle(if (focused) fieldFocusStyle else fieldStyle)
    })
  }

  /**
   * A looping menu animation stepped at 12 fps, calling `apply` with the loop's phase
   * (0 to 1). Any change on screen makes JavaFX present the whole window, and on macOS a
   * full-screen window doing that every pulse holds a CPU core at ~60% on a 5K display,
   * just for a gently pulsing label. The slow swings these animations make look the same
   * stepped, and they hold still while nobody is using the window (see UiActivity).
   */
  def steppedLoop(periodSec: Double, apply: Double => Unit): AnimationTimer = {
    UiActivity.touch()
    val timer = new AnimationTimer {
      private var last = 0L
      override def handle(now: Long): Unit = {
        if (now - last < 83_000_000L || UiActivity.idle) return
        last = now
        apply((now / 1e9 % periodSec) / periodSec)
      }
    }
    timer.start()
    timer
  }

  /** 0 -> 1 -> 0 as `t` runs 0 -> 1: what a Timeline with auto-reverse traces. */
  def triangle(t: Double): Double = if (t < 0.5) t * 2 else 2 - t * 2

  def fadeInScene(stage: Stage, root: javafx.scene.Parent): Unit = {
    val overlay = new javafx.scene.shape.Rectangle()
    overlay.setFill(Color.BLACK)
    overlay.setMouseTransparent(true)
    val stack = new StackPane(root, overlay)
    overlay.widthProperty().bind(stack.widthProperty())
    overlay.heightProperty().bind(stack.heightProperty())
    val scene = newScene(stack)
    scene.setFill(Color.BLACK)
    stage.setScene(scene)
    val fade = new javafx.animation.FadeTransition(javafx.util.Duration.millis(300), overlay)
    fade.setFromValue(1.0)
    fade.setToValue(0.0)
    fade.setOnFinished(_ => stack.getChildren.remove(overlay))
    fade.play()
  }

  def styleCombo(combo: ComboBox[String]): Unit = {
    combo.setStyle(comboStyle)
    val cellFactory = new Callback[ListView[String], ListCell[String]] {
      override def call(param: ListView[String]): ListCell[String] = new ListCell[String] {
        override def updateItem(item: String, empty: Boolean): Unit = {
          super.updateItem(item, empty)
          if (empty || item == null) {
            setText(null)
            setStyle("-fx-background-color: #181830;")
          } else {
            setText(item)
            setStyle("-fx-background-color: #181830; -fx-text-fill: #dde; -fx-font-size: 14; -fx-padding: 8 12;")
          }
          if (isSelected) setStyle("-fx-background-color: #3a6eaf; -fx-text-fill: white; -fx-font-size: 14; -fx-padding: 8 12; -fx-background-radius: 6;")
        }
      }
    }
    combo.setCellFactory(cellFactory)
    combo.setButtonCell(cellFactory.call(null))
  }

  def createAccentLine(): Region = {
    val line = new Region()
    line.setMinHeight(2)
    line.setMaxHeight(2)
    line.setMaxWidth(60)
    line.setStyle("-fx-background-color: linear-gradient(to right, transparent, #4a9eff, transparent); -fx-background-radius: 1;")
    line
  }

  def createSeparator(): Region = {
    val sep = new Region()
    sep.setMinHeight(1)
    sep.setMaxHeight(1)
    sep.setStyle("-fx-background-color: linear-gradient(to right, transparent, rgba(255,255,255,0.08), transparent);")
    sep
  }

  def renderMapPreview(canvas: Canvas, mapIndex: Int): Unit = {
    val gc = canvas.getGraphicsContext2D
    val canvasW = canvas.getWidth
    val canvasH = canvas.getHeight
    gc.setFill(Color.web("#111124"))
    gc.fillRect(0, 0, canvasW, canvasH)
    try {
      val filename = WorldRegistry.getFilename(mapIndex)
      val world = WorldLoader.load("worlds/" + filename)
      val scale = Math.min(canvasW / world.width, canvasH / world.height)
      val offsetX = (canvasW - world.width * scale) / 2
      val offsetY = (canvasH - world.height * scale) / 2
      for (y <- 0 until world.height) {
        for (x <- 0 until world.width) {
          val tile = world.getTile(x, y)
          val argb = tile.color
          val r = ((argb >> 16) & 0xFF) / 255.0
          val g = ((argb >> 8) & 0xFF) / 255.0
          val b = (argb & 0xFF) / 255.0
          gc.setFill(Color.color(r, g, b))
          gc.fillRect(offsetX + x * scale, offsetY + y * scale, Math.ceil(scale), Math.ceil(scale))
        }
      }
      // Draw spawn points as small white markers
      gc.setFill(Color.color(1, 1, 1, 0.6))
      for (spawn <- world.spawnPoints) {
        val sx = offsetX + spawn.getX * scale
        val sy = offsetY + spawn.getY * scale
        gc.fillOval(sx - 2, sy - 2, 4, 4)
      }
      // Draw map name and size
      gc.setFill(Color.color(1, 1, 1, 0.4))
      gc.setFont(Font.font("Exo 2", 10))
      gc.fillText(s"${world.width}x${world.height}", 4, canvasH - 4)
    } catch {
      case _: Exception =>
        gc.setFill(Color.web("#556677"))
        gc.setFont(Font.font("Exo 2", 12))
        gc.fillText("Preview unavailable", canvasW / 2 - 50, canvasH / 2)
    }
  }

  /** A labelled stat tile; returns the tile and its value label, to update later. */
  def createStatBox(label: String, value: String, accentColor: String): (StackPane, Label) = {
    val container = new StackPane()
    container.setStyle(s"-fx-background-color: rgba(255,255,255,0.03); -fx-background-radius: 12; -fx-border-color: rgba(255,255,255,0.05); -fx-border-radius: 12; -fx-border-width: 1;")
    container.setPadding(new Insets(12, 8, 12, 8))
    container.setMinWidth(80)

    val inner = new VBox(4)
    inner.setAlignment(Pos.CENTER)
    val nameLabel = new Label(label)
    nameLabel.setStyle(sectionHeaderStyle)
    val valueLabel = new Label(value)
    valueLabel.setFont(Font.font("Exo 2", FontWeight.BOLD, 22))
    valueLabel.setTextFill(Color.web(accentColor))
    inner.getChildren.addAll(nameLabel, valueLabel)

    container.getChildren.add(inner)
    (container, valueLabel)
  }
}
