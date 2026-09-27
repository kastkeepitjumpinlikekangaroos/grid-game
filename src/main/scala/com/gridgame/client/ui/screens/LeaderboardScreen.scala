package com.gridgame.client.ui.screens

import com.gridgame.client.i18n.Messages

import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.util.Callback
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import javafx.stage.Stage
import com.gridgame.client.game.LeaderboardEntry

import com.gridgame.client.ui.Theme._

/** The ranked leaderboard. */
private[client] final class LeaderboardScreen(app: Screens) {
  import app._

  def show(stage: Stage): Unit = {
    switchScreen()
    val root = new VBox(16)
    root.setPadding(new Insets(24))
    root.setStyle(darkBg)

    val titleBar = new HBox(12)
    titleBar.setAlignment(Pos.CENTER_LEFT)
    val title = new Label(Messages.t("Leaderboard"))
    title.setFont(Font.font("Exo 2", FontWeight.BOLD, 28))
    title.setTextFill(Color.WHITE)
    val spacer = new Region()
    HBox.setHgrow(spacer, Priority.ALWAYS)
    val backBtn = new Button(Messages.t("Back"))
    addHoverEffect(backBtn, buttonStyle, buttonHoverStyle)
    backBtn.setOnAction(_ => showLobbyBrowser(stage))
    titleBar.getChildren.addAll(title, spacer, backBtn)

    val leaderboardListView = new ListView[LeaderboardEntry]()
    leaderboardListView.setStyle(listViewCss)
    VBox.setVgrow(leaderboardListView, Priority.ALWAYS)

    val leaderboardCellFactory = new Callback[ListView[LeaderboardEntry], ListCell[LeaderboardEntry]] {
      override def call(param: ListView[LeaderboardEntry]): ListCell[LeaderboardEntry] = new ListCell[LeaderboardEntry] {
        override def updateItem(entry: LeaderboardEntry, empty: Boolean): Unit = {
          super.updateItem(entry, empty)
          if (empty || entry == null) {
            setText(null)
            setStyle("-fx-background-color: #242440;")
          } else {
            setText(Messages.t("#{0}  |  {1}  |  ELO: {2}  |  {3}W  |  {4} Matches",
              entry.rank.toString, entry.username, entry.elo.toString, entry.wins.toString, entry.matchesPlayed.toString))
            val base = "-fx-font-size: 15; -fx-padding: 10 12; -fx-font-weight: bold;"
            val idx = getIndex
            if (entry.username.equalsIgnoreCase(client.playerName)) {
              setStyle(s"-fx-background-color: rgba(74, 158, 255, 0.15); -fx-text-fill: #4a9eff; $base")
            } else if (entry.rank == 1) {
              setStyle(s"-fx-background-color: #242440; -fx-text-fill: #ffd700; $base")
            } else if (entry.rank == 2) {
              setStyle(s"-fx-background-color: #2a2a48; -fx-text-fill: #c0c0c0; $base")
            } else if (entry.rank == 3) {
              setStyle(s"-fx-background-color: #242440; -fx-text-fill: #cd7f32; $base")
            } else if (idx % 2 == 0) {
              setStyle(s"-fx-background-color: #242440; -fx-text-fill: #dde; $base")
            } else {
              setStyle(s"-fx-background-color: #2a2a48; -fx-text-fill: #dde; $base")
            }
          }
        }
      }
    }
    leaderboardListView.setCellFactory(leaderboardCellFactory)

    val loadingLabel = new Label("")
    loadingLabel.setTextFill(Color.web("#8899bb"))
    loadingLabel.setFont(Font.font("Exo 2", 13))

    root.getChildren.addAll(titleBar, leaderboardListView, loadingLabel)

    val scene = newScene(root)
    stage.setScene(scene)

    // Show what we have now and replace it when the response lands. The server rate-limits
    // this query, so reopening the screen within a few seconds gets the cached board.
    import scala.jdk.CollectionConverters._
    def render(): Unit = {
      leaderboardListView.getItems.setAll(client.leaderboard.asJava)
      loadingLabel.setText(
        if (!client.leaderboardLoaded) Messages.t("Loading...")
        else if (client.leaderboard.isEmpty) Messages.t("No players found")
        else "")
    }
    render()
    client.leaderboardListener = () => Platform.runLater(() => render())
    client.requestLeaderboard()
  }
}
