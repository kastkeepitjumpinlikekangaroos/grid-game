package com.gridgame.client.ui.screens

import com.gridgame.client.i18n.Messages
import com.gridgame.client.ui.Icons
import com.gridgame.client.game.LeaderboardEntry

import javafx.application.Platform
import javafx.geometry.Pos
import javafx.scene.layout.VBox
import javafx.stage.Stage

import com.gridgame.client.ui.Theme._
import com.gridgame.client.ui.Widgets._

/** The ranked leaderboard. */
private[client] final class LeaderboardScreen(app: Screens) {
  import app._

  def show(stage: Stage): Unit = {
    switchScreen()

    val cols = Seq(Col(Messages.t("Rank"), 64), Col(Messages.t("Player"), 0), Col(Messages.t("Rating"), 110),
      Col(Messages.t("Wins"), 90, Pos.CENTER_RIGHT), Col(Messages.t("Matches"), 100, Pos.CENTER_RIGHT))

    def entryRow(e: LeaderboardEntry) = {
      val isLocal = e.username.equalsIgnoreCase(client.playerName)
      val who = row(10, avatar(e.username, 32), text(e.username, "h3"))
      if (isLocal) who.getChildren.add(chip(Messages.t("You"), "sky"))
      val rating = row(6, Icons.node(Icons.Star, 14, Palette.Gold), text(e.elo.toString, "number"))
      val r = tableRow(cols, Seq(medal(e.rank), who, rating,
        text(e.wins.toString, "number"), text(e.matchesPlayed.toString, "number")))
      if (isLocal) r.getStyleClass.add("row-self")
      r
    }

    val rows = new VBox(4)
    val board = card(tableHead(cols), rows)
    board.setSpacing(8)

    val body = new VBox(20, Chrome.heading(Messages.t("Leaderboard"), Messages.t("The highest-rated players on this server.")), board)
    Chrome.page(app, stage, Chrome.Tab.Leaderboard, body, maxWidth = 960).show(stage)

    // Show what we have now and replace it when the response lands. The server rate-limits
    // this query, so reopening the screen within a few seconds gets the cached board.
    def render(): Unit = {
      if (client.leaderboard.nonEmpty) rows.getChildren.setAll(client.leaderboard.map(entryRow): _*)
      else if (!client.leaderboardLoaded) rows.getChildren.setAll(emptyState(Messages.t("Loading..."), Icons.Trophy))
      else rows.getChildren.setAll(emptyState(Messages.t("No players found"), Icons.Trophy))
    }
    render()
    client.leaderboardListener = () => Platform.runLater(() => render())
    client.requestLeaderboard()
  }
}
