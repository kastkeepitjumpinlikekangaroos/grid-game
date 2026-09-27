package com.gridgame.client.ui.screens

import com.gridgame.client.i18n.{I18n, Messages}
import com.gridgame.common.model.CharacterDef
import com.gridgame.common.model.ScoreEntry

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

/** A finished match's scoreboard (practice's results, or a ranked match's rating change), and the way back. */
private[client] final class ScoreboardScreen(app: Screens) {
  import app._

  def show(stage: Stage): Unit = {
    switchScreen()
    import scala.jdk.CollectionConverters._

    val isPractice = client.isPracticeMode
    val localId = client.getLocalPlayerId
    val entries = client.scoreboard.asScala.toVector
    // Decided by the results rather than by lobby state that can outlive its lobby
    val teamMode = entries.exists(_.teamId != 0)

    val rows: Vector[ScoreEntry] =
      if (teamMode) entries.sortBy(e => (e.rank, e.teamId, -e.kills, e.deaths))
      else entries.sortBy(e => (e.rank, -e.kills, e.deaths))
    val localEntry = entries.find(_.playerId.equals(localId))
    val teamRanks: Map[Int, Int] = entries.groupBy(_.teamId).map { case (team, members) => team -> members.head.rank }
    val teamKills: Map[Int, Int] = entries.groupBy(_.teamId).map { case (team, members) => team -> members.map(_.kills).sum }
    val isDraw = teamMode && teamRanks.size > 1 && teamRanks.values.forall(_ == 1)

    def teamName(team: Int): String = team match {
      case 1 => Messages.t("Team 1 (Blue)")
      case 2 => Messages.t("Team 2 (Red)")
      case n => Messages.t("Team {0}", n.toString)
    }
    def teamColor(team: Int): String = team match {
      case 1 => "#4a82ff"
      case 2 => "#e84057"
      case 3 => "#2ecc71"
      case 4 => "#f1c40f"
      case _ => "#8899aa"
    }
    val teamRowColors = Map(1 -> "rgba(74, 130, 255, 0.12)", 2 -> "rgba(232, 64, 87, 0.12)", 3 -> "rgba(46, 204, 113, 0.12)", 4 -> "rgba(241, 196, 15, 0.12)")

    // Headline: the player's own result, which is what they came to this screen for
    val (titleText, titleColor, titleGlow) =
      if (isPractice) (Messages.t("Practice Complete"), "#ffffff", "rgba(61, 219, 128, 0.4)")
      else localEntry match {
        case Some(_) if isDraw => (Messages.t("Draw"), "#ffffff", "rgba(74, 158, 255, 0.4)")
        case Some(e) if e.rank == 1 => (Messages.t("Victory!"), "#ffd700", "rgba(255, 215, 0, 0.5)")
        case Some(_) if teamMode => (Messages.t("Defeat"), "#ff8090", "rgba(232, 64, 87, 0.45)")
        case _ => (Messages.t("Game Over"), "#ffffff", "rgba(74, 158, 255, 0.4)")
      }
    val subtitleText =
      if (isPractice) Messages.t("Target Practice")
      else if (teamMode) {
        val byPlace = teamKills.toSeq.sortBy { case (team, kills) => (teamRanks(team), team) }
        val score = byPlace.map(_._2.toString).mkString(" – ")
        if (isDraw) Messages.t("Tied at {0}", score)
        else byPlace.headOption.map { case (team, _) => Messages.t("{0} wins {1}", teamName(team), score) }.getOrElse(Messages.t("Final Scoreboard"))
      } else localEntry match {
        case Some(e) => Messages.t("You placed #{0} of {1}", e.rank.toString, entries.size.toString)
        case None => Messages.t("Final Scoreboard")
      }

    val root = new VBox(0)
    root.setAlignment(Pos.TOP_CENTER)
    root.setStyle(darkBg)
    root.setPadding(new Insets(0, 24, 36, 24))
    root.setMinHeight(Region.USE_PREF_SIZE)

    // Title section with glow
    val titleBox = new VBox(6)
    titleBox.setAlignment(Pos.CENTER)
    titleBox.setPadding(new Insets(36, 0, 24, 0))

    val title = new Label(titleText)
    title.setFont(Font.font("Exo 2", FontWeight.BOLD, 40))
    title.setTextFill(Color.web(titleColor))
    title.setStyle(s"-fx-effect: dropshadow(gaussian, $titleGlow, 20, 0, 0, 0);")

    val subtitle = new Label(subtitleText)
    subtitle.setFont(Font.font("Exo 2", 15))
    subtitle.setTextFill(Color.web("#8899aa"))

    titleBox.getChildren.addAll(title, subtitle, createAccentLine())
    root.getChildren.add(titleBox)

    // Ranked ELO delta — only shown when the server pushed a post-match STATS
    // (i.e. the just-ended match was ranked with >=2 humans).
    client.pendingEloChange.foreach { case (oldElo, newElo) =>
      val delta = newElo - oldElo
      val (deltaColor, deltaText) =
        if (delta > 0) ("#2ecc71", s"+$delta")
        else if (delta < 0) ("#e84057", delta.toString)
        else ("#8899aa", "±0")

      val eloBox = new VBox(6)
      eloBox.setAlignment(Pos.CENTER)
      eloBox.setPadding(new Insets(0, 0, 20, 0))

      val eloHeader = new Label(Messages.t("RANKED ELO"))
      eloHeader.setFont(Font.font("Exo 2", FontWeight.BOLD, 11))
      eloHeader.setTextFill(Color.web("#8899aa"))
      eloHeader.setStyle("-fx-letter-spacing: 2px;")

      val eloRow = new HBox(14)
      eloRow.setAlignment(Pos.CENTER)
      val oldLbl = new Label(oldElo.toString)
      oldLbl.setFont(Font.font("Exo 2", FontWeight.BOLD, 24))
      oldLbl.setTextFill(Color.web("#8899aa"))
      val arrow = new Label("→")
      arrow.setFont(Font.font("Exo 2", 22))
      arrow.setTextFill(Color.web("#556677"))
      val newLbl = new Label(newElo.toString)
      newLbl.setFont(Font.font("Exo 2", FontWeight.BOLD, 28))
      newLbl.setTextFill(Color.web("#ffd700"))
      newLbl.setStyle("-fx-effect: dropshadow(gaussian, rgba(255, 215, 0, 0.4), 12, 0, 0, 0);")
      val deltaLbl = new Label(deltaText)
      deltaLbl.setFont(Font.font("Exo 2", FontWeight.BOLD, 22))
      deltaLbl.setTextFill(Color.web(deltaColor))
      eloRow.getChildren.addAll(oldLbl, arrow, newLbl, deltaLbl)

      eloBox.getChildren.addAll(eloHeader, eloRow)
      root.getChildren.add(eloBox)
    }

    // Scoreboard card
    val scoreCard = new VBox(0)
    scoreCard.setMaxWidth(760)
    scoreCard.setStyle(cardBg)

    // Header row
    val header = new HBox(0)
    header.setAlignment(Pos.CENTER_LEFT)
    header.setPadding(new Insets(14, 20, 14, 20))
    header.setStyle("-fx-background-color: rgba(255,255,255,0.03); -fx-background-radius: 16 16 0 0; -fx-border-color: transparent transparent rgba(255,255,255,0.06) transparent; -fx-border-width: 0 0 1 0;")
    val hRank = new Label(Messages.t("RANK"))
    hRank.setMinWidth(80); hRank.setStyle(sectionHeaderStyle)
    val hPlayer = new Label(Messages.t("PLAYER"))
    hPlayer.setMinWidth(240); hPlayer.setMaxWidth(Double.MaxValue); hPlayer.setStyle(sectionHeaderStyle)
    HBox.setHgrow(hPlayer, Priority.ALWAYS)
    val hKills = new Label(Messages.t("KILLS"))
    hKills.setMinWidth(90); hKills.setStyle(sectionHeaderStyle)
    val hDeaths = new Label(Messages.t("DEATHS"))
    hDeaths.setMinWidth(90); hDeaths.setStyle(sectionHeaderStyle)
    header.getChildren.addAll(hRank, hPlayer, hKills, hDeaths)
    scoreCard.getChildren.add(header)

    val rowBorder = "-fx-border-color: transparent transparent rgba(255,255,255,0.04) transparent; -fx-border-width: 0 0 1 0;"
    var lastTeamId = -1
    var placeInTeam = 0
    rows.zipWithIndex.foreach { case (entry, rowIndex) =>
      // Team header: name, total kills, and the result, above each team's rows
      if (teamMode && entry.teamId != lastTeamId) {
        lastTeamId = entry.teamId
        placeInTeam = 0
        val teamLabel = new Label(teamName(entry.teamId))
        teamLabel.setFont(Font.font("Exo 2", FontWeight.BOLD, 15))
        teamLabel.setTextFill(Color.web(teamColor(entry.teamId)))
        val killsTotal = new Label(Messages.t("{0} kills", teamKills(entry.teamId).toString))
        killsTotal.setFont(Font.font("Exo 2", FontWeight.BOLD, 13))
        killsTotal.setTextFill(Color.web("#aabbcc"))
        val teamSpacer = new Region()
        HBox.setHgrow(teamSpacer, Priority.ALWAYS)
        val teamHeaderBox = new HBox(12, teamLabel, killsTotal, teamSpacer)
        teamHeaderBox.setAlignment(Pos.CENTER_LEFT)
        if (!isDraw && teamRanks(entry.teamId) == 1) {
          val winnerBadge = new Label(Messages.t("WINNER"))
          winnerBadge.setFont(Font.font("Exo 2", FontWeight.BOLD, 11))
          winnerBadge.setTextFill(Color.web("#ffd700"))
          winnerBadge.setStyle("-fx-background-color: rgba(255, 215, 0, 0.12); -fx-padding: 3 10; -fx-background-radius: 10;")
          teamHeaderBox.getChildren.add(winnerBadge)
        }
        teamHeaderBox.setPadding(new Insets(12, 20, 6, 20))
        teamHeaderBox.setStyle(s"-fx-background-color: ${teamRowColors.getOrElse(entry.teamId, "transparent")};")
        scoreCard.getChildren.add(teamHeaderBox)
      }
      placeInTeam += 1

      val row = new HBox(0)
      row.setAlignment(Pos.CENTER_LEFT)

      val isLocal = entry.playerId.equals(localId)
      val bottomRadius = if (rowIndex == rows.size - 1) "-fx-background-radius: 0 0 16 16;" else ""
      // Our own row is marked by an accent bar, so in Teams it keeps its team's tint rather
      // than taking a blue that reads as the other team.
      val rowBg =
        if (teamMode) teamRowColors.getOrElse(entry.teamId, "transparent")
        else if (isLocal) "rgba(74, 158, 255, 0.14)"
        else if (rowIndex % 2 == 0) "transparent"
        else "rgba(255,255,255,0.02)"
      if (isLocal) {
        row.setPadding(new Insets(12, 20, 12, 17))
        row.setStyle(s"-fx-background-color: $rowBg; -fx-border-color: transparent transparent rgba(255,255,255,0.04) #4a9eff; -fx-border-width: 0 0 1 3; $bottomRadius")
      } else {
        row.setPadding(new Insets(12, 20, 12, 20))
        row.setStyle(s"-fx-background-color: $rowBg; $rowBorder $bottomRadius")
      }

      // FFA: the finishing place, medal-coloured, shared by exact ties. Teams: the place is
      // the team's (in its header), so rows show each player's standing within their team.
      val rankLabel = new Label(if (teamMode) placeInTeam.toString else s"#${entry.rank}")
      rankLabel.setMinWidth(80)
      if (teamMode) {
        rankLabel.setTextFill(Color.web("#667788"))
        rankLabel.setFont(Font.font("Exo 2", FontWeight.BOLD, 15))
      } else {
        rankLabel.setTextFill(entry.rank match {
          case 1 => Color.web("#ffd700")
          case 2 => Color.web("#c0c0c0")
          case 3 => Color.web("#cd7f32")
          case _ => Color.web("#556677")
        })
        rankLabel.setFont(Font.font("Exo 2", FontWeight.BOLD, if (entry.rank <= 3) 20 else 16))
        if (entry.rank <= 3) {
          rankLabel.setStyle(s"-fx-effect: dropshadow(gaussian, ${if (entry.rank == 1) "rgba(255,215,0,0.4)" else if (entry.rank == 2) "rgba(192,192,192,0.3)" else "rgba(205,127,50,0.3)"}, 8, 0, 0, 0);")
        }
      }

      // Character first, as the match showed everyone; then who was playing it
      val player = if (isLocal) null else client.findPlayer(entry.playerId)
      val characterName =
        if (isLocal) I18n.characterName(client.getSelectedCharacterDef)
        else if (player != null) I18n.characterName(CharacterDef.get(player.getCharacterId))
        else "?"
      val playerName =
        if (isLocal) client.playerName
        else if (player != null) player.getName
        else entry.playerId.toString.substring(0, 8)
      val charLabel = new Label(if (isLocal) s"$characterName ${Messages.t("(you)")}" else characterName)
      charLabel.setTextFill(if (isLocal) Color.web("#4a9eff") else Color.web("#ccdde8"))
      charLabel.setFont(Font.font("Exo 2", FontWeight.BOLD, 15))
      val left = !isLocal && client.playerLeftMatch(entry.playerId)
      val playerLabel = new Label(if (left) s"$playerName  ${Messages.t("(left)")}" else playerName)
      playerLabel.setTextFill(Color.web("#778899"))
      playerLabel.setFont(Font.font("Exo 2", 12))
      val nameCell = new HBox(10, charLabel, playerLabel)
      nameCell.setAlignment(Pos.BASELINE_LEFT)
      nameCell.setMinWidth(240)
      nameCell.setMaxWidth(Double.MaxValue)
      HBox.setHgrow(nameCell, Priority.ALWAYS)

      val killsLabel = new Label(entry.kills.toString)
      killsLabel.setMinWidth(90)
      killsLabel.setTextFill(Color.web("#2ecc71"))
      killsLabel.setFont(Font.font("Exo 2", FontWeight.BOLD, 16))

      val deathsLabel = new Label(entry.deaths.toString)
      deathsLabel.setMinWidth(90)
      deathsLabel.setTextFill(Color.web("#e84057"))
      deathsLabel.setFont(Font.font("Exo 2", FontWeight.BOLD, 15))

      row.getChildren.addAll(rankLabel, nameCell, killsLabel, deathsLabel)
      scoreCard.getChildren.add(row)
    }
    root.getChildren.add(scoreCard)

    // Practice stats row (only shown in practice mode)
    if (isPractice) {
      val practiceStatsRow = new HBox(12)
      practiceStatsRow.setAlignment(Pos.CENTER)
      practiceStatsRow.setPadding(new Insets(12, 20, 12, 20))
      practiceStatsRow.setMaxWidth(760)
      practiceStatsRow.setStyle(cardBgSubtle)
      VBox.setMargin(practiceStatsRow, new Insets(16, 0, 0, 0))

      val accuracy = if (client.practiceShots > 0) (client.practiceHits * 100.0 / client.practiceShots).toInt else 0
      val (bestComboBox, _) = createStatBox(Messages.t("Best Combo"), client.practiceBestCombo.toString, "#ffd700")
      val (accuracyBox, _) = createStatBox(Messages.t("Accuracy"), s"$accuracy%", "#4a9eff")
      val (totalKillsBox, _) = createStatBox(Messages.t("Total Kills"), client.killCount.toString, "#2ecc71")
      HBox.setHgrow(bestComboBox, Priority.ALWAYS)
      HBox.setHgrow(accuracyBox, Priority.ALWAYS)
      HBox.setHgrow(totalKillsBox, Priority.ALWAYS)
      practiceStatsRow.getChildren.addAll(bestComboBox, accuracyBox, totalKillsBox)
      root.getChildren.add(practiceStatsRow)
    }

    val returnBtn = new Button(Messages.t("Return to Lobby"))
    addHoverEffect(returnBtn, buttonStyle, buttonHoverStyle)
    returnBtn.setFont(Font.font("Exo 2", FontWeight.BOLD, 15))
    returnBtn.setOnAction(_ => {
      client.pendingEloChange = None
      client.returnToLobbyBrowser()
      showLobbyBrowser(stage)
    })

    // Buttons sit right under the results; pinned to the bottom edge they were cut off
    val btnBox = new HBox(12)
    btnBox.setAlignment(Pos.CENTER)
    btnBox.setPadding(new Insets(24, 0, 0, 0))

    if (isPractice) {
      val practiceAgainBtn = new Button(Messages.t("Practice Again"))
      addHoverEffect(practiceAgainBtn, buttonGreenStyle, buttonGreenHoverStyle)
      practiceAgainBtn.setFont(Font.font("Exo 2", FontWeight.BOLD, 15))
      practiceAgainBtn.setOnAction(_ => {
        client.returnToLobbyBrowser()
        showPracticeSetup(stage)
      })
      btnBox.getChildren.add(practiceAgainBtn)
    }

    btnBox.getChildren.add(returnBtn)
    root.getChildren.add(btnBox)

    // The whole page scrolls when a big lobby outgrows the window. The card used to sit in a
    // scroll pane of its own, which pinned it to the left edge and the button to the bottom.
    val scrollPane = new ScrollPane(root)
    scrollPane.setFitToWidth(true)
    scrollPane.setFitToHeight(true)
    scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER)
    scrollPane.setStyle("-fx-background-color: #151528; -fx-border-color: transparent;")

    fadeInScene(stage, scrollPane)
  }
}
