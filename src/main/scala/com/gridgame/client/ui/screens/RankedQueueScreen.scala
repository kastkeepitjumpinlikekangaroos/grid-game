package com.gridgame.client.ui.screens

import com.gridgame.client.ui.{CharacterSelectionPanel, ViewportCache}
import com.gridgame.client.i18n.Messages
import com.gridgame.common.protocol.RankedQueueMode

import javafx.animation.AnimationTimer
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

/** The ranked queue: the mode, the character to queue as, and the wait until a match is found. */
private[client] final class RankedQueueScreen(app: Screens) {
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

    val titleRow = new HBox(16)
    titleRow.setAlignment(Pos.CENTER)

    val queueTitle = new Label(Messages.t("Ranked Queue"))
    queueTitle.setFont(Font.font("Exo 2", FontWeight.BOLD, 28))
    queueTitle.setTextFill(Color.WHITE)
    queueTitle.setStyle("-fx-effect: dropshadow(gaussian, rgba(255, 215, 0, 0.3), 12, 0, 0, 0);")

    // ELO badge
    val eloLabel = new Label(s"ELO: ${client.rankedElo}")
    eloLabel.setFont(Font.font("Exo 2", FontWeight.BOLD, 18))
    eloLabel.setTextFill(Color.web("#ffd700"))
    eloLabel.setStyle("-fx-background-color: rgba(255, 215, 0, 0.08); -fx-padding: 6 20; -fx-background-radius: 20; -fx-border-color: rgba(255, 215, 0, 0.2); -fx-border-radius: 20; -fx-border-width: 1;")

    titleRow.getChildren.addAll(queueTitle, eloLabel)

    val headerLine = new Region()
    headerLine.setMinHeight(2)
    headerLine.setMaxHeight(2)
    headerLine.setMaxWidth(60)
    headerLine.setStyle("-fx-background-color: linear-gradient(to right, transparent, #ffd700, transparent); -fx-background-radius: 1;")

    headerBox.getChildren.addAll(titleRow, headerLine)

    // Two-panel content
    val mainContent = new HBox(24)
    mainContent.setPadding(new Insets(0, 28, 24, 28))
    VBox.setVgrow(mainContent, Priority.ALWAYS)

    // Left panel (~40%): mode selection, find match, queue status, back button
    val leftPanel = new VBox(14)
    leftPanel.setMinWidth(360)
    leftPanel.setPrefWidth(420)

    val modeCard = new VBox(14)
    modeCard.setPadding(new Insets(20, 28, 20, 28))
    modeCard.setStyle(cardBg)
    modeCard.setAlignment(Pos.CENTER)

    val modeLabel = new Label(Messages.t("SELECT MODE"))
    modeLabel.setStyle(sectionHeaderStyle)

    var selectedMode: Byte = RankedQueueMode.FFA

    val modeButtonActiveStyle = "-fx-background-color: linear-gradient(to bottom, #5aadff, #3a8eef); -fx-text-fill: white; -fx-font-size: 14; -fx-font-weight: bold; -fx-padding: 14 28; -fx-background-radius: 10; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(74, 158, 255, 0.5), 16, 0, 0, 3); -fx-border-color: #6db8ff; -fx-border-radius: 10; -fx-border-width: 2;"
    val modeButtonInactiveStyle = "-fx-background-color: rgba(255,255,255,0.06); -fx-text-fill: #8899aa; -fx-font-size: 14; -fx-font-weight: bold; -fx-padding: 14 28; -fx-background-radius: 10; -fx-cursor: hand; -fx-border-color: rgba(255,255,255,0.1); -fx-border-radius: 10; -fx-border-width: 1;"
    val modeButtonInactiveHoverStyle = "-fx-background-color: rgba(255,255,255,0.12); -fx-text-fill: #ccdde8; -fx-font-size: 14; -fx-font-weight: bold; -fx-padding: 14 28; -fx-background-radius: 10; -fx-cursor: hand; -fx-border-color: rgba(255,255,255,0.2); -fx-border-radius: 10; -fx-border-width: 1;"

    val ffaBtn = new Button(Messages.t("FFA (8 Players)"))
    ffaBtn.setStyle(modeButtonActiveStyle)
    ffaBtn.setMaxWidth(Double.MaxValue)

    val duelBtn = new Button(Messages.t("1v1 Duel"))
    duelBtn.setStyle(modeButtonInactiveStyle)
    duelBtn.setMaxWidth(Double.MaxValue)

    val teamsBtn = new Button(Messages.t("Teams (3v3)"))
    teamsBtn.setStyle(modeButtonInactiveStyle)
    teamsBtn.setMaxWidth(Double.MaxValue)

    val allModeButtons = Seq(ffaBtn, duelBtn, teamsBtn)

    def updateModeButtons(): Unit = {
      allModeButtons.foreach { btn =>
        val isActive = (btn == ffaBtn && selectedMode == RankedQueueMode.FFA) ||
          (btn == duelBtn && selectedMode == RankedQueueMode.DUEL) ||
          (btn == teamsBtn && selectedMode == RankedQueueMode.TEAMS)
        if (isActive) {
          btn.setStyle(modeButtonActiveStyle)
          btn.setOnMouseEntered(null)
          btn.setOnMouseExited(null)
        } else {
          btn.setStyle(modeButtonInactiveStyle)
          btn.setOnMouseEntered(_ => btn.setStyle(modeButtonInactiveHoverStyle))
          btn.setOnMouseExited(_ => btn.setStyle(modeButtonInactiveStyle))
        }
      }
    }
    updateModeButtons()

    ffaBtn.setOnAction(_ => {
      selectedMode = RankedQueueMode.FFA
      updateModeButtons()
    })

    duelBtn.setOnAction(_ => {
      selectedMode = RankedQueueMode.DUEL
      updateModeButtons()
    })

    teamsBtn.setOnAction(_ => {
      selectedMode = RankedQueueMode.TEAMS
      updateModeButtons()
    })

    // Stack mode buttons vertically for left panel
    val modeButtonsCol = new VBox(10, ffaBtn, duelBtn, teamsBtn)

    // Queue status elements (initially hidden)
    val queueSizeLabel = new Label(Messages.t("Players in queue: {0}", "1"))
    queueSizeLabel.setFont(Font.font("Exo 2", 14))
    queueSizeLabel.setTextFill(Color.web("#aabbcc"))
    queueSizeLabel.setVisible(false)
    queueSizeLabel.setManaged(false)

    val waitTimeLabel = new Label(Messages.t("Wait time: {0}s", "0"))
    waitTimeLabel.setFont(Font.font("Exo 2", 14))
    waitTimeLabel.setTextFill(Color.web("#aabbcc"))
    waitTimeLabel.setVisible(false)
    waitTimeLabel.setManaged(false)

    val searchingLabel = new Label("")
    searchingLabel.setFont(Font.font("Exo 2", FontWeight.BOLD, 14))
    searchingLabel.setTextFill(Color.web("#4a9eff"))
    searchingLabel.setVisible(false)
    searchingLabel.setManaged(false)

    val searchSeparator = createSeparator()
    searchSeparator.setVisible(false)
    searchSeparator.setManaged(false)

    // Animated dots for searching
    var dotTick = 0
    var isSearching = false
    val dotTimer = new AnimationTimer {
      private var lastUpdate = 0L
      override def handle(now: Long): Unit = {
        if (isSearching && now - lastUpdate > 500_000_000L) {
          lastUpdate = now
          dotTick = (dotTick + 1) % 4
          val dots = "." * dotTick
          val modeText = if (selectedMode == RankedQueueMode.DUEL) Messages.t("Searching for opponent")
            else if (selectedMode == RankedQueueMode.TEAMS) Messages.t("Searching for teammates")
            else Messages.t("Searching for match")
          searchingLabel.setText(s"$modeText$dots")
        }
      }
    }
    dotTimer.start()

    // Find Match button
    val findMatchBtn = new Button(Messages.t("Find Match"))
    addHoverEffect(findMatchBtn, buttonGreenStyle, buttonGreenHoverStyle)
    findMatchBtn.setFont(Font.font("Exo 2", FontWeight.BOLD, 15))
    findMatchBtn.setMaxWidth(Double.MaxValue)

    modeCard.getChildren.addAll(modeLabel, modeButtonsCol, findMatchBtn, searchingLabel, searchSeparator, queueSizeLabel, waitTimeLabel)

    // Character selection panel (declared before leaveBtn so it can reference it)
    val charPanel = new CharacterSelectionPanel(
      () => client.selectedCharacterId,
      id => client.changeRankedCharacter(id)
    )
    val charSection = charPanel.createPanel()
    stopCurrentScreen = () => { charPanel.stop(); dotTimer.stop() }

    // Back / Leave queue button
    val leaveBtn = new Button(Messages.t("Back"))
    addHoverEffect(leaveBtn, buttonRedStyle, buttonRedHoverStyle)
    leaveBtn.setMaxWidth(Double.MaxValue)
    leaveBtn.setOnAction(_ => {
      if (isSearching) {
        client.leaveRankedQueue()
      }
      showLobbyBrowser(stage)
    })

    // Find Match button action
    findMatchBtn.setOnAction(_ => {
      isSearching = true
      client.queueRanked(selectedMode)

      // Disable mode buttons and Find Match
      ffaBtn.setDisable(true)
      duelBtn.setDisable(true)
      teamsBtn.setDisable(true)
      findMatchBtn.setVisible(false)
      findMatchBtn.setManaged(false)

      // Show searching UI
      searchingLabel.setVisible(true)
      searchingLabel.setManaged(true)
      searchSeparator.setVisible(true)
      searchSeparator.setManaged(true)
      queueSizeLabel.setVisible(true)
      queueSizeLabel.setManaged(true)
      waitTimeLabel.setVisible(true)
      waitTimeLabel.setManaged(true)

      leaveBtn.setText(Messages.t("Leave Queue"))
    })

    leftPanel.getChildren.addAll(modeCard, leaveBtn)

    // Right panel (~60%): character selection grid
    val rightPanel = new VBox(0)
    HBox.setHgrow(rightPanel, Priority.ALWAYS)
    rightPanel.getChildren.add(charSection)

    mainContent.getChildren.addAll(leftPanel, rightPanel)

    root.getChildren.addAll(headerBox, mainContent)

    val scrollPane = new ScrollPane(root)
    scrollPane.setFitToWidth(true)
    scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER)
    scrollPane.setStyle("-fx-background-color: #1a1a2e; -fx-border-color: transparent;")
    ViewportCache.disable(scrollPane) // the character panel inside animates

    // Wire up queue status listener
    client.rankedQueueListener = () => {
      Platform.runLater(() => {
        queueSizeLabel.setText(Messages.t("Players in queue: {0}", client.rankedQueueSize.toString))
        waitTimeLabel.setText(Messages.t("Wait time: {0}s", client.rankedQueueWaitTime.toString))
        eloLabel.setText(s"ELO: ${client.rankedElo}")
      })
    }

    // The rating is only known once stats arrive; until then the badge showed the default 1000.
    client.matchHistoryListener = () => {
      Platform.runLater(() => eloLabel.setText(s"ELO: ${client.rankedElo}"))
    }
    client.requestMatchHistory()

    // Wire up match found listener - transition to game
    client.rankedMatchFoundListener = () => {
      Platform.runLater(() => {
        // Match found - game starting packets will follow
      })
    }

    // Wire up game starting listener to transition to game scene
    client.gameStartingListener = () => {
      Platform.runLater(() => showGameScene(stage))
    }

    client.lobbyClosedListener = () => {
      Platform.runLater(() => showLobbyBrowser(stage))
    }

    val scene = newScene(scrollPane)
    stage.setScene(scene)
  }
}
