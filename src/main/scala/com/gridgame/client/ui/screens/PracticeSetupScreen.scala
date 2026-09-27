package com.gridgame.client.ui.screens

import com.gridgame.client.ui.{CharacterSelectionPanel, ViewportCache}
import com.gridgame.client.i18n.Messages

import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.ScrollPane
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import javafx.stage.Stage

import com.gridgame.client.ui.Theme._

/** Practice: a character to try, against bots, with no opening. */
private[client] final class PracticeSetupScreen(app: Screens) {
  import app._

  def show(stage: Stage): Unit = {
    switchScreen()
    val root = new VBox(0)
    root.setAlignment(Pos.TOP_CENTER)
    root.setStyle(darkBg)

    // Header
    val headerBox = new VBox(8)
    headerBox.setAlignment(Pos.CENTER)
    headerBox.setPadding(new Insets(28, 24, 16, 24))

    val titleLabel = new Label(Messages.t("Target Practice"))
    titleLabel.setFont(Font.font("Exo 2", FontWeight.BOLD, 28))
    titleLabel.setTextFill(Color.WHITE)
    titleLabel.setStyle("-fx-effect: dropshadow(gaussian, rgba(61, 219, 128, 0.3), 12, 0, 0, 0);")

    val headerLine = new Region()
    headerLine.setMinHeight(2)
    headerLine.setMaxHeight(2)
    headerLine.setMaxWidth(60)
    headerLine.setStyle("-fx-background-color: linear-gradient(to right, transparent, #3ddb80, transparent); -fx-background-radius: 1;")

    headerBox.getChildren.addAll(titleLabel, headerLine)

    // Two-panel content
    val mainContent = new HBox(24)
    mainContent.setPadding(new Insets(0, 28, 24, 28))
    VBox.setVgrow(mainContent, Priority.ALWAYS)

    // Left panel: info + buttons
    val leftPanel = new VBox(14)
    leftPanel.setMinWidth(300)
    leftPanel.setPrefWidth(340)

    val infoCard = new VBox(14)
    infoCard.setPadding(new Insets(20, 28, 20, 28))
    infoCard.setStyle(cardBg)
    infoCard.setAlignment(Pos.CENTER)

    val descLabel = new Label(Messages.t("HOW IT WORKS"))
    descLabel.setStyle(sectionHeaderStyle)

    val descText = new Label(Messages.t("Shoot passive bots with\nsatisfying feedback. Bots\nrespawn quickly so you can\npractice non-stop."))
    descText.setTextFill(Color.web("#8899aa"))
    descText.setFont(Font.font("Exo 2", 14))
    descText.setWrapText(true)

    val exitHint = new Label(Messages.t("Press Esc twice in game to end the session."))
    exitHint.setTextFill(Color.web("#667788"))
    exitHint.setFont(Font.font("Exo 2", 12))
    exitHint.setWrapText(true)

    val sep = createSeparator()

    val startBtn = new Button(Messages.t("Start Practice"))
    addHoverEffect(startBtn, buttonGreenStyle, buttonGreenHoverStyle)
    startBtn.setFont(Font.font("Exo 2", FontWeight.BOLD, 16))
    startBtn.setMaxWidth(Double.MaxValue)

    val statusLabel = new Label("")
    statusLabel.setFont(Font.font("Exo 2", 12))
    statusLabel.setWrapText(true)

    val backBtn = new Button(Messages.t("Back"))
    addHoverEffect(backBtn, buttonGhostStyle, buttonGhostHoverStyle)
    backBtn.setMaxWidth(Double.MaxValue)

    infoCard.getChildren.addAll(descLabel, descText, exitHint, sep, startBtn, statusLabel)
    leftPanel.getChildren.addAll(infoCard, backBtn)

    // Right panel: character selection
    val rightPanel = new VBox(0)
    HBox.setHgrow(rightPanel, Priority.ALWAYS)

    val charPanel = new CharacterSelectionPanel(
      () => client.selectedCharacterId,
      id => { client.selectedCharacterId = id }
    )
    val charSection = charPanel.createPanel()
    rightPanel.getChildren.add(charSection)
    stopCurrentScreen = () => charPanel.stop()

    mainContent.getChildren.addAll(leftPanel, rightPanel)
    root.getChildren.addAll(headerBox, mainContent)

    val scrollPane = new ScrollPane(root)
    scrollPane.setFitToWidth(true)
    scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER)
    scrollPane.setStyle("-fx-background-color: #1a1a2e; -fx-border-color: transparent;")
    ViewportCache.disable(scrollPane) // the character panel inside animates

    backBtn.setOnAction(_ => {
      client.isPracticeMode = false
      showLobbyBrowser(stage)
    })

    startBtn.setOnAction(_ => {
      startBtn.setDisable(true)
      showStatus(statusLabel, Messages.t("Starting practice..."), error = false)
      // Set before sending: the reply can beat the next line on a local server. The session
      // starts straight away, so the JOINED it opens with must not flash up the lobby room.
      client.lobbyJoinedListener = () => ()
      client.gameStartingListener = () => {
        Platform.runLater(() => showGameScene(stage))
      }
      client.lobbyActionFailedListener = reason => Platform.runLater(() => {
        client.isPracticeMode = false
        startBtn.setDisable(false)
        showStatus(statusLabel, lobbyFailureMessage(reason), error = true)
      })
      client.startPractice()
    })

    val scene = newScene(scrollPane)
    stage.setScene(scene)
  }
}
