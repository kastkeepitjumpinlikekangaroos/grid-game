package com.gridgame.client.ui.screens

import com.gridgame.client.ui.{CharacterSelectionPanel, Icons}
import com.gridgame.client.i18n.Messages

import javafx.application.Platform
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.layout.{HBox, Priority, Region, VBox}
import javafx.scene.paint.Color
import javafx.stage.Stage

import com.gridgame.client.ui.Theme._
import com.gridgame.client.ui.Widgets._

/** Practice: a character to try, against bots, with no opening. */
private[client] final class PracticeSetupScreen(app: Screens) {
  import app._

  def show(stage: Stage): Unit = {
    switchScreen()

    def point(icon: Node, words: String): HBox = {
      val l = para(words, "body")
      HBox.setHgrow(l, Priority.ALWAYS)
      val r = row(12, icon, l)
      r.setAlignment(Pos.TOP_LEFT)
      r
    }
    val escKeys = row(3, keycap("Esc"), keycap("Esc"))
    escKeys.setMinWidth(Region.USE_PREF_SIZE)

    val startBtn = button(Messages.t("Start Practice"), "sun", "lg")
    startBtn.setGraphic(Icons.node(Icons.Play, 16, Color.web("#5a3300")))
    startBtn.setMaxWidth(Double.MaxValue)

    val statusLabel = text("", "status")
    statusLabel.setWrapText(true)
    statusLabel.managedProperty().bind(statusLabel.textProperty().isNotEmpty)

    val infoCard = card(
      cardHeader(Messages.t("How it works")),
      point(Icons.node(Icons.Target, 20, Palette.Sky), Messages.t("Shoot passive bots that respawn right away, so you can practice non-stop.")),
      point(Icons.node(Icons.Trophy, 20, Palette.Gold), Messages.t("Practice sessions don't count toward stats.")),
      point(escKeys, Messages.t("Press Esc twice in game to end the session.")),
      startBtn, statusLabel)
    infoCard.setMinWidth(340)
    infoCard.setPrefWidth(340)
    infoCard.setMaxWidth(340)
    infoCard.setMaxHeight(Region.USE_PREF_SIZE)

    val charPanel = new CharacterSelectionPanel(
      () => client.selectedCharacterId,
      id => { client.selectedCharacterId = id }
    )
    val charSection = charPanel.createPanel()
    HBox.setHgrow(charSection, Priority.ALWAYS)
    stopCurrentScreen = () => charPanel.stop()

    startBtn.setOnAction(_ => {
      startBtn.setDisable(true)
      showStatus(statusLabel, Messages.t("Starting practice..."), Tone.Info)
      // Set before sending: the reply can beat the next line on a local server. The session
      // starts straight away, so the JOINED it opens with must not flash up the lobby room.
      client.lobbyJoinedListener = () => ()
      client.gameStartingListener = () => {
        Platform.runLater(() => showGameScene(stage))
      }
      client.lobbyActionFailedListener = reason => Platform.runLater(() => {
        client.isPracticeMode = false
        startBtn.setDisable(false)
        showStatus(statusLabel, lobbyFailureMessage(reason), Tone.Error)
      })
      client.startPractice()
    })

    val columns = new HBox(24, infoCard, charSection)
    columns.setAlignment(Pos.TOP_LEFT)
    val body = new VBox(20,
      Chrome.heading(Messages.t("Target Practice"), Messages.t("Warm up against bots before a real match.")),
      columns)

    Chrome.page(app, stage, Chrome.Tab.Practice, body, () => client.isPracticeMode = false, maxWidth = 1560).show(stage)
  }
}
