package com.gridgame.client

import com.gridgame.client.ui.SpriteGenerator
import com.gridgame.client.ui.screens.ScoreboardScreen
import com.gridgame.common.model.{CharacterId, Direction}
import com.gridgame.common.protocol._
import javafx.scene.control.{Label, ScrollPane}
import javafx.scene.image.ImageView
import javafx.scene.shape.{Shape, StrokeType}
import javafx.stage.Stage
import org.junit.Assert._
import org.junit.Test

import java.util.concurrent.{CountDownLatch, TimeUnit}

import java.util.UUID

/** The results screen after a match, built from the scoreboard the server sent. */
class ScoreboardScreenTest {
  private val t = new TestClient(name = "me")
  private val a = UUID.randomUUID()
  private val b = UUID.randomUUID()

  /** The end of a match as the server sends it: GAME_OVER, the rows, a ranked match's new
    * rating, SCORE_END. Returns the texts on the screen that follows. */
  private def finish(rows: (UUID, Int, Int, Int, Byte)*): Seq[String] = finishRated(None, rows: _*)

  private def finishRated(elo: Option[(Int, Int)], rows: (UUID, Int, Int, Int, Byte)*): Seq[String] =
    Fx(Fx.labels(scoreboard(elo, rows: _*)))

  /** The places the rows show, top to bottom. */
  private def places(root: javafx.scene.Node): Seq[String] = Fx(Fx.labelsOf(root, "medal"))

  /** The scores the rows show, top to bottom: a row's numbers are its kills, deaths and score. */
  private def scores(root: javafx.scene.Node): Seq[String] =
    Fx(Fx.labelsOf(root, "number")).grouped(3).map(_.last).toSeq

  private def scoreboard(elo: Option[(Int, Int)], rows: (UUID, Int, Int, Int, Byte)*): javafx.scene.Node =
    scoreboardThen(elo, rows: _*)(identity)

  /** The scoreboard, and `look` at it on the FX thread in the moment it is built. */
  private def scoreboardThen[T](elo: Option[(Int, Int)], rows: (UUID, Int, Int, Int, Byte)*)(look: javafx.scene.Node => T): T = {
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
      look(stage.getScene.getRoot)
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
    val root = scoreboard(None, (a, 5, 1, 1, 0), (t.id, 3, 2, 2, 0), (b, 0, 5, 3, 0))
    val shown = Fx(Fx.labels(root))
    assertTrue(shown.contains("Game Over"))
    assertTrue(shown.contains("You placed #2 of 3"))
    assertEquals(Seq("1", "2", "3"), places(root))
  }

  @Test def tiedPlayersShareAPlace(): Unit = {
    match2()
    val root = scoreboard(None, (t.id, 3, 1, 1, 0), (a, 3, 1, 1, 0), (b, 0, 5, 3, 0))
    assertTrue(Fx(Fx.labels(root)).contains("Victory!"))
    // Both are first, and the next place is skipped
    assertEquals(Seq("1", "1", "3"), places(root))
  }

  @Test def eachRowShowsItsScore(): Unit = {
    match2()
    // Placed by the server on kills less deaths: 3 - 0 above 6 - 5
    val root = scoreboard(None, (t.id, 3, 0, 1, 0), (a, 6, 5, 2, 0), (b, 1, 4, 3, 0))
    assertTrue(Fx(Fx.labels(root)).contains("SCORE"))
    assertEquals(Seq("+3", "+1", "−3"), scores(root))
  }

  @Test def aTeamsMembersAreListedByScore(): Unit = {
    match2()
    // A team shares its place, so the screen orders its rows itself: 5 - 2 above 7 - 6
    val root = scoreboard(None, (a, 7, 6, 1, 1), (b, 5, 2, 1, 1), (t.id, 8, 12, 2, 2))
    assertEquals(Seq("+3", "+1", "−4"), scores(root))
  }

  @Test def theLosingTeamSeesDefeatAndTheScore(): Unit = {
    match2()
    val shown = finish((a, 7, 3, 1, 1), (t.id, 2, 3, 2, 2), (b, 1, 4, 2, 2))
    assertTrue(shown.contains("Defeat"))
    assertTrue(shown.contains("Team 1 (Blue) wins 7 – 3"))
    assertTrue(shown.contains("WINNER"))
  }

  @Test def teamsLevelOnScoreDraw(): Unit = {
    match2()
    val shown = finish((a, 4, 4, 1, 1), (t.id, 2, 1, 1, 2), (b, 2, 3, 1, 2))
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

  // ── Coming up quickly (ui/CLAUDE.md, *Coming back from a match*) ──

  /** Its title is outlined, and nothing on it is stroked inside or outside an edge: JavaFX works such a
    * stroke out with area geometry every time it draws it, 57-317ms a title at 2x, on every frame of
    * the scoreboard's entrance (Widgets.displayTitle). */
  @Test def nothingOnItIsStrokedOffItsEdge(): Unit = {
    for (practice <- Seq(false, true)) {
      t.client.isPracticeMode = practice
      match2()
      val stroked = Fx(Fx.all(scoreboard(None, (t.id, 5, 1, 1, 0), (a, 3, 2, 2, 0))).collect {
        case s: Shape if s.getStroke != null => s
      })
      assertTrue("the title has its outline", stroked.exists(_.isInstanceOf[javafx.scene.text.Text]))
      assertEquals(Nil, stroked.filter(_.getStrokeType != StrokeType.CENTERED).map(_.toString))
    }
  }

  /** Put on the stage before it shows, as it is when a match ends, it comes up whole and styled: no
    * entrance to run on a window that has only just appeared, and no styling left to its first frame. */
  @Test def onAStageNotYetShowingItIsWholeAndStyledAtOnce(): Unit = {
    match2()
    val root = scoreboard(None, (t.id, 5, 1, 1, 0), (a, 3, 2, 2, 0))
    val (opacity, rise, nameSize) = Fx {
      val column = Fx.all(root).collectFirst { case sp: ScrollPane => sp.getContent }.get
      // A character's name is an h3, 15px in the sheet (13 unstyled)
      val name = Fx.all(root).collectFirst { case l: Label if l.getStyleClass.contains("h3") => l }.get
      (column.getOpacity, column.getTranslateY, name.getFont.getSize)
    }
    assertEquals(1.0, opacity, 0.0)
    assertEquals(0.0, rise, 0.0)
    assertEquals(15.0, nameSize, 0.01)
  }

  /** The portraits the match readied as it was played (ScoreboardScreen.prepare, which the match's
    * window calls) are on the scoreboard as it is built, not cut after it shows. */
  @Test def itComesUpWithThePortraitsTheMatchReadied(): Unit = {
    // Not the wizard, who is on the top bar whoever plays
    t.client.selectedCharacterId = CharacterId.Assassin.id
    t.startMatch(spawn = (5, 5))
    t.join(a, 10, 10, "Aria", CharacterId.Gladiator.id)
    t.join(b, 20, 20, "Bram", CharacterId.Samurai.id)
    // A match starts with none (MatchWindow.open), and readies them as it goes
    Fx { SpriteGenerator.clearCache(); ScoreboardScreen.prepare(t.client) }
    // They are cut a thread of their own, in turn, and handed over in turn: once one asked for after
    // them is here, so are they
    val after = new CountDownLatch(1)
    Fx(SpriteGenerator.portrait(CharacterId.Tidecaller.id, Direction.Down)(_ => after.countDown()))
    assertTrue("the portraits are cut", after.await(20, TimeUnit.SECONDS))
    // The rows' portraits (36 across) and the top bar's wizard (40), each there as it is built: one cut
    // only now lands a moment after
    val portraits = scoreboardThen(None, (t.id, 5, 1, 1, 0), (a, 3, 2, 2, 0), (b, 0, 5, 3, 0)) { root =>
      Fx.all(root).collect { case v: ImageView if v.getFitWidth == 36 || v.getFitWidth == 40 => v.getImage != null }
    }
    assertEquals(Seq(true, true, true, true), portraits)
  }
}
