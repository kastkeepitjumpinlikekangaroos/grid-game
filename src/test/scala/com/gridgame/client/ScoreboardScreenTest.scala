package com.gridgame.client

import com.gridgame.common.model.CharacterId
import com.gridgame.common.protocol._
import javafx.stage.Stage
import org.junit.Assert._
import org.junit.Test

import java.util.UUID

/** The results screen after a match, built from the scoreboard the server sent. */
class ScoreboardScreenTest {
  private val t = new TestClient(name = "me")
  private val a = UUID.randomUUID()
  private val b = UUID.randomUUID()

  /** The end of a match as the server sends it: GAME_OVER, the rows, a ranked match's new
    * rating, SCORE_END. Returns the texts on the screen that follows. */
  private def finish(rows: (UUID, Int, Int, Int, Byte)*): Seq[String] = finishRated(None, rows: _*)

  private def finishRated(elo: Option[(Int, Int)], rows: (UUID, Int, Int, Int, Byte)*): Seq[String] = {
    t.gameEvent(GameEvent.GAME_OVER)
    rows.foreach { case (who, kills, deaths, rank, team) =>
      t.gameEvent(GameEvent.SCORE_ENTRY, who = who, kills = kills, deaths = deaths, rank = rank, team = team)
    }
    elo.foreach { case (before, after) =>
      t.receive(new MatchHistoryPacket(1, t.id, 0, MatchHistoryAction.STATS, elo = after.toShort, oldElo = before.toShort))
    }
    t.gameEvent(GameEvent.SCORE_END)
    Fx {
      val app = new ClientMain()
      app.client = t.client
      val stage = new Stage()
      app.showScoreboard(stage)
      Fx.labels(stage.getScene.getRoot)
    }
  }

  private def match2(): Unit = {
    t.client.selectedCharacterId = CharacterId.Wizard.id
    t.startMatch(spawn = (5, 5), others = Seq(a -> (10, 10), b -> (20, 20)))
  }

  @Test def theWinnerSeesVictory(): Unit = {
    match2()
    val shown = finish((t.id, 5, 1, 1, 0), (a, 3, 2, 2, 0), (b, 0, 5, 3, 0))
    assertTrue(shown.contains("Victory!"))
    assertTrue(shown.contains("You placed #1 of 3"))
    assertTrue(shown.contains("Wizard (you)"))
  }

  @Test def anotherPlaceIsGameOver(): Unit = {
    match2()
    val shown = finish((a, 5, 1, 1, 0), (t.id, 3, 2, 2, 0), (b, 0, 5, 3, 0))
    assertTrue(shown.contains("Game Over"))
    assertTrue(shown.contains("You placed #2 of 3"))
    assertTrue(shown.contains("#1"))
    assertTrue(shown.contains("#2"))
  }

  @Test def tiedPlayersShareAPlace(): Unit = {
    match2()
    val shown = finish((t.id, 3, 1, 1, 0), (a, 3, 1, 1, 0), (b, 0, 5, 3, 0))
    assertTrue(shown.contains("Victory!"))
    assertEquals(2, shown.count(_ == "#1"))
    assertTrue("the next place is skipped", shown.contains("#3"))
  }

  @Test def theLosingTeamSeesDefeatAndTheScore(): Unit = {
    match2()
    val shown = finish((a, 7, 1, 1, 1), (t.id, 2, 3, 2, 2), (b, 1, 4, 2, 2))
    assertTrue(shown.contains("Defeat"))
    assertTrue(shown.contains("Team 1 (Blue) wins 7 – 3"))
    assertTrue(shown.contains("WINNER"))
  }

  @Test def teamsLevelOnKillsDraw(): Unit = {
    match2()
    val shown = finish((a, 4, 1, 1, 1), (t.id, 2, 3, 1, 2), (b, 2, 4, 1, 2))
    assertTrue(shown.contains("Draw"))
    assertTrue(shown.contains("Tied at 4 – 4"))
    assertFalse(shown.contains("WINNER"))
  }

  @Test def aPlayerWhoLeftIsStillListedAndMarked(): Unit = {
    match2()
    t.receive(new PlayerLeavePacket(1, b))
    val shown = finish((t.id, 5, 1, 1, 0), (a, 3, 2, 2, 0), (b, 0, 5, 3, 0))
    assertTrue(shown.exists(_.endsWith("(left)")))
  }

  @Test def aRankedResultShowsTheRatingChange(): Unit = {
    match2()
    val shown = finishRated(Some((1000, 1016)), (t.id, 5, 1, 1, 0), (a, 3, 2, 2, 0))
    assertTrue(shown.contains("RANKED ELO"))
    assertTrue(shown.contains("1000"))
    assertTrue(shown.contains("1016"))
    assertTrue(shown.contains("+16"))
  }

  @Test def aCasualResultShowsNoRating(): Unit = {
    match2()
    assertFalse(finish((t.id, 5, 1, 1, 0), (a, 3, 2, 2, 0)).contains("RANKED ELO"))
  }

  @Test def practiceSaysSo(): Unit = {
    t.client.isPracticeMode = true
    match2()
    val shown = finish((t.id, 9, 0, 1, 0), (a, 0, 5, 2, 0))
    assertTrue(shown.contains("Practice Complete"))
    assertTrue(shown.contains("Best Combo"))
  }
}
