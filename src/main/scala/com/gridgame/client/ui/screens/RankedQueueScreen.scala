package com.gridgame.client.ui.screens

import com.gridgame.client.ui.{CharacterSelectionPanel, Icons}
import com.gridgame.client.i18n.Messages
import com.gridgame.common.protocol.RankedQueueMode

import javafx.animation.AnimationTimer
import javafx.application.Platform
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.layout.{HBox, Priority, Region, VBox}
import javafx.scene.paint.Color
import javafx.stage.Stage

import com.gridgame.client.ui.Theme._
import com.gridgame.client.ui.Widgets._

/** The ranked queue: the mode, the character to queue as, and the wait until a match is found. */
private[client] final class RankedQueueScreen(app: Screens) {
  import app._

  def show(stage: Stage): Unit = {
    switchScreen()

    // The player's rating, as a badge at the right of the heading
    val eloValue = text(client.rankedElo.toString, "stat-value", "text-gold")
    val badge = row(10, Icons.node(Icons.Trophy, 28, Palette.Gold), new VBox(-2, eloValue, text(Messages.t("Rating"), "stat-label")))
    badge.getStyleClass.add("badge-card")

    var selectedMode: Byte = RankedQueueMode.FFA

    /** A mode to queue for, as a card to pick: an icon, its name and a line about it. */
    def option(mode: Byte, icon: Icons.Icon, title: String, blurb: String): Button = {
      val b = new Button()
      b.getStyleClass.add("option")
      val words = new VBox(1, text(title, "h3"), small(blurb))
      b.setGraphic(row(12, Icons.node(icon, 22, Palette.Sky), words))
      b.setMaxWidth(Double.MaxValue)
      b.setAlignment(Pos.CENTER_LEFT)
      b.setUserData(java.lang.Byte.valueOf(mode))
      b
    }
    val options = Seq(
      option(RankedQueueMode.FFA, Icons.Users, Messages.t("FFA (8 Players)"), Messages.t("Everyone for themselves")),
      option(RankedQueueMode.DUEL, Icons.Sword, Messages.t("1v1 Duel"), Messages.t("Just you and one rival")),
      option(RankedQueueMode.TEAMS, Icons.Crown, Messages.t("Teams (3v3)"), Messages.t("Three on three")))
    def updateModeButtons(): Unit = options.foreach { b =>
      b.getStyleClass.remove("option-selected")
      if (b.getUserData == java.lang.Byte.valueOf(selectedMode)) b.getStyleClass.add("option-selected")
    }
    options.foreach(b => b.setOnAction(_ => {
      selectedMode = b.getUserData.asInstanceOf[java.lang.Byte].byteValue()
      updateModeButtons()
    }))
    updateModeButtons()

    // While searching: how many are queued and for how long, and the way out
    val searchingLabel = text("", "h3", "text-sky")
    val queueSizeLabel = chip(Messages.t("Players in queue: {0}", "1"), "sky", Icons.Users, Palette.Sky)
    val waitTimeLabel = chip(Messages.t("Wait time: {0}s", "0"), "grey", Icons.Clock, Palette.Muted)
    val leaveQueueBtn = button(Messages.t("Leave Queue"), "plain")
    leaveQueueBtn.setMaxWidth(Double.MaxValue)
    val queueFacts = new javafx.scene.layout.FlowPane(6, 6, queueSizeLabel, waitTimeLabel)
    val searchingBox = new VBox(12, searchingLabel, queueFacts, leaveQueueBtn)
    searchingBox.getStyleClass.add("inset")
    searchingBox.setVisible(false)
    searchingBox.setManaged(false)

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

    val findMatchBtn = button(Messages.t("Find Match"), "sun", "lg")
    findMatchBtn.setGraphic(Icons.node(Icons.Play, 16, Color.web("#5a3300")))
    findMatchBtn.setMaxWidth(Double.MaxValue)

    val modeCard = card(cardHeader(Messages.t("Pick a mode")), new VBox(8, options: _*), findMatchBtn, searchingBox)
    modeCard.setMinWidth(340)
    modeCard.setPrefWidth(340)
    modeCard.setMaxWidth(340)
    modeCard.setMaxHeight(Region.USE_PREF_SIZE)

    // Character selection panel
    val charPanel = new CharacterSelectionPanel(
      () => client.selectedCharacterId,
      id => client.changeRankedCharacter(id)
    )
    val charSection = charPanel.createPanel()
    HBox.setHgrow(charSection, Priority.ALWAYS)
    stopCurrentScreen = () => { charPanel.stop(); dotTimer.stop() }

    // Leaving the page leaves the queue
    val leave = () => if (isSearching) client.leaveRankedQueue()
    leaveQueueBtn.setOnAction(_ => {
      leave()
      showRankedQueue(stage)
    })

    findMatchBtn.setOnAction(_ => {
      isSearching = true
      client.queueRanked(selectedMode)
      options.foreach(b => b.setDisable(!b.getStyleClass.contains("option-selected")))
      options.foreach(b => b.setMouseTransparent(true))
      findMatchBtn.setVisible(false)
      findMatchBtn.setManaged(false)
      searchingBox.setVisible(true)
      searchingBox.setManaged(true)
    })

    val columns = new HBox(24, modeCard, charSection)
    columns.setAlignment(Pos.TOP_LEFT)
    val body = new VBox(20,
      Chrome.heading(Messages.t("Ranked Queue"), Messages.t("Every ranked match moves your rating up or down."), badge),
      columns)

    val page = Chrome.page(app, stage, Chrome.Tab.Ranked, body, leave, maxWidth = 1560)

    // Wire up queue status listener
    client.rankedQueueListener = () => {
      Platform.runLater(() => {
        queueSizeLabel.setText(Messages.t("Players in queue: {0}", client.rankedQueueSize.toString))
        waitTimeLabel.setText(Messages.t("Wait time: {0}s", client.rankedQueueWaitTime.toString))
        eloValue.setText(client.rankedElo.toString)
      })
    }

    // The rating is only known once stats arrive; until then the badge showed the default 1000.
    client.matchHistoryListener = () => {
      Platform.runLater(() => {
        eloValue.setText(client.rankedElo.toString)
        page.refreshUser()
      })
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

    page.show(stage)
  }
}
