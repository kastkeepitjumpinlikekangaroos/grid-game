package com.gridgame.client

import com.gridgame.client.diagnostics.{Diagnostics, OutboxEntry}
import com.gridgame.client.game.ClientState
import com.gridgame.client.ui.screens.ReportAbout
import com.gridgame.common.diagnostics.Recording.{IncidentKind, NoticedKind}
import com.gridgame.common.protocol._
import com.google.gson.JsonParser
import javafx.scene.Node
import javafx.scene.control.{Button, CheckBox, TextArea, TextField}
import javafx.scene.layout.StackPane
import javafx.stage.Stage
import org.junit.Assert._
import org.junit.{After, Before, Test}

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.UUID
import java.util.zip.ZipFile

/**
 * A problem report as the player makes it (ReportDialog), over the page they are on: what it asks, the
 * moments they marked, what it says is sent; sending it (the server's answer through a client of the
 * test's own) and what it says when the report went, when the server said not now, and when there is
 * no connection; and the scoreboard asking for one when the match gave reason to.
 */
class ReportDialogTest {
  private val t = new TestClient(name = "me")
  private val matchId = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef")

  @Before def setUp(): Unit = Diagnostics.install(Nil, Some(Files.createTempDirectory("report-dialog")), teeConsole = false)
  @After def reset(): Unit = Diagnostics.resetForTest()

  /** A match played and over, with `marks` moments marked and maybe something gone wrong in it. */
  private def played(marks: Int, trouble: Boolean = false): Unit = {
    t.startMatch(spawn = (5, 5))
    Diagnostics.packetTap.received(new LobbyActionPacket(1, t.id, Packet.getCurrentTimestamp, LobbyAction.GAME_STARTING,
      7.toShort, 0.toByte, 5.toByte, 1.toByte, 8.toByte, 0.toByte, "Lobby", 0.toByte, 0.toByte, 2.toByte).serialize(), tcp = true)
    t.gameEvent(GameEvent.MATCH_INFO, target = matchId)
    for (_ <- 0 until marks) Diagnostics.markMoment(null, 0, 0)
    if (trouble) Diagnostics.incident(IncidentKind.Freeze, "not answering for 4200ms")
    Diagnostics.matchEnded("finished")
  }

  // What the client asked the server to take
  private val begins = new java.util.concurrent.CopyOnWriteArrayList[ProblemReportPacket]()

  /** The server, as far as the session goes: its answer to a BEGIN. */
  private def answering(say: ProblemReportPacket => Packet): Unit =
    t.client.packetSink = {
      case p: ProblemReportPacket if p.getAction == ProblemReportAction.BEGIN =>
        begins.add(p)
        val a = say(p)
        if (a != null) t.receive(a)
      case _ =>
    }

  /** The page the report opens over (the lobby browser), and the report on it. */
  private def open(about: ReportAbout): StackPane = Fx {
    val app = new ClientMain()
    app.client = t.client
    val stage = new Stage()
    app.showLobbyBrowser(stage)
    app.showProblemReport(stage, about)
    stage.getScene.getRoot.asInstanceOf[StackPane]
  }

  private def texts(root: Node): Seq[String] = Fx(Fx.labels(root))
  private def button(root: Node, text: String): Button = Fx(Fx.all(root).collectFirst { case b: Button if b.getText == text => b })
    .getOrElse(throw new AssertionError(s"no '$text' button in ${texts(root)}"))

  /** Wait for the report's card to say `text` (sending is done off the FX thread). */
  private def await(root: Node, text: String): Unit = {
    val until = System.currentTimeMillis() + 15000
    while (!texts(root).contains(text)) {
      if (System.currentTimeMillis() > until) fail(s"never said '$text': ${texts(root)}")
      Thread.sleep(50)
    }
  }

  @Test def itAsksWhatHappenedAndShowsTheMomentsMarked(): Unit = {
    played(marks = 2)
    val root = open(ReportAbout.LastMatch)
    val shown = texts(root)
    assertTrue(shown.contains("Report a problem"))
    assertTrue(shown.contains("Tell us what went wrong in the match you just played."))
    for (kind <- Seq("Movement", "Hits & damage", "Abilities", "Lag or disconnects", "Graphics", "Sound", "Crash or freeze", "Something else"))
      assertTrue(kind, shown.contains(kind))
    assertTrue(shown.contains("MOMENTS YOU MARKED"))
    assertEquals("a tick box for each, ticked", Seq(true, true), Fx(Fx.all(root).collect { case c: CheckBox if c.getText == "Include" => c.isSelected }))
    assertEquals(2, shown.count(_.endsWith("into the match")))
    assertEquals("one place to write", 1, Fx(Fx.all(root).count(_.isInstanceOf[TextArea])))
    assertTrue(shown.contains("Send report"))
    assertTrue(shown.contains("Save a copy..."))
  }

  @Test def withNoMomentsMarkedItSaysHowToMarkOneAndOffersTheMatchToPickFrom(): Unit = {
    played(marks = 0)
    val shown = texts(open(ReportAbout.LastMatch))
    assertTrue(shown.exists(_.startsWith("Tip: in a match, press F8")))
    assertTrue(shown.contains("WHEN DID IT HAPPEN?"))
    assertTrue(shown.contains("Click where on the match it happened"))
  }

  @Test def whatTheGameNoticedCanBePickedAsWhenItHappened(): Unit = {
    t.startMatch(spawn = (5, 5))
    Diagnostics.packetTap.received(new LobbyActionPacket(1, t.id, Packet.getCurrentTimestamp, LobbyAction.GAME_STARTING,
      7.toShort, 0.toByte, 5.toByte, 1.toByte, 8.toByte, 0.toByte, "Lobby", 0.toByte, 0.toByte, 2.toByte).serialize(), tcp = true)
    Diagnostics.noticed(NoticedKind.SnapBack, 320)
    Diagnostics.noticed(NoticedKind.Died, -1)
    Diagnostics.matchEnded("finished")
    answering(_ => null)
    t.client.clientState = ClientState.CONNECTING // made, and kept to look at
    val root = open(ReportAbout.LastMatch)
    val shown = texts(root)
    assertTrue(shown.contains("What the game noticed"))
    assertTrue(shown.contains("The server moved you 3.2 cells"))
    assertTrue(shown.contains("You died"))
    // Picked from the list: it says so, and goes with the report
    Fx(Fx.click(Fx.all(root).collect { case h: javafx.scene.layout.HBox if h.getStyleClass.contains("noticed-row") => h }.head))
    assertTrue(texts(root).exists(_.matches("Around \\d+:\\d\\d: The server moved you 3.2 cells")))
    Fx(button(root, "Send report").fire())
    await(root, "Not sent yet")
    val zip = new ZipFile(Diagnostics.outbox.zip(Diagnostics.outbox.pending.head.id).toFile)
    try {
      val report = JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("report.json")).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject
      val chosen = report.getAsJsonObject("chosenMoment")
      assertEquals("noticed", chosen.get("source").getAsString)
      assertEquals("The server moved you 3.2 cells", chosen.get("text").getAsString)
      assertEquals(chosen.get("matchTime").getAsString, report.get("whenInMatch").getAsString)
      assertEquals(2, report.getAsJsonArray("noticed").size)
    } finally zip.close()
  }

  @Test def aSentReportLetsItsMatchGo(): Unit = {
    played(marks = 1)
    val matchDir = Diagnostics.lastMatch.get.dir
    answering(p => ProblemReportPacket.accepted(1, t.id, p.getReportId, "GG-7K3F9QX2"))
    val root = open(ReportAbout.LastMatch)
    Fx(button(root, "Send report").fire())
    await(root, "Thanks! Your report is in.")
    assertFalse("the recording goes once the server has it", Files.exists(matchDir))
    assertEquals(None, Diagnostics.lastMatch)
  }

  @Test def whatIsSentIsSaidPlainly(): Unit = {
    played(marks = 1)
    val root = open(ReportAbout.LastMatch)
    Fx(button(root, "What's sent").fire())
    val shown = texts(root)
    assertTrue(shown.exists(_.startsWith("A recording of the match")))
    assertTrue(shown.exists(_.startsWith("The log your game kept since it started")))
    assertTrue(shown.contains("Never sent: your password, your login, your chat messages, or anything outside the game."))
  }

  @Test def aReportSentShowsItsCodeAndCarriesWhatThePlayerSaid(): Unit = {
    played(marks = 2)
    // The server already has it (a resend after a lost reply): ACCEPTED straight away, no upload to make
    answering(p => ProblemReportPacket.accepted(1, t.id, p.getReportId, "GG-7K3F9QX2"))
    val root = open(ReportAbout.LastMatch)
    Fx {
      button(root, "Movement").fire()
      Fx.all(root).collectFirst { case a: TextArea => a }.get.setText("My dash went through the wall by the pond")
      // The second moment left out
      Fx.all(root).collect { case c: CheckBox if c.getText == "Include" => c }.last.setSelected(false)
      button(root, "Send report").fire()
    }
    await(root, "Thanks! Your report is in.")
    assertEquals("GG-7K3F9QX2", Fx(Fx.all(root).collectFirst { case f: TextField if f.getStyleClass.contains("report-code") => f.getText }).get)

    val entry = Diagnostics.outbox.entries.last
    assertEquals((OutboxEntry.Sent, "GG-7K3F9QX2", matchId), (entry.status, entry.code, entry.matchId))
    val begin = begins.get(begins.size - 1)
    assertEquals(ProblemReportKind.MATCH, begin.getKind)
    assertEquals(matchId, begin.getMatchId)
  }

  @Test def whatGoesInTheReportIsWhatThePlayerChose(): Unit = {
    played(marks = 2)
    answering(_ => null)
    t.client.clientState = ClientState.CONNECTING // no connection: it is made, and waits
    val root = open(ReportAbout.LastMatch)
    Fx {
      button(root, "Lag or disconnects").fire()
      Fx.all(root).collectFirst { case a: TextArea => a }.get.setText("rubber-banding all match")
      Fx.all(root).collect { case c: CheckBox if c.getText == "Include" => c }.head.setSelected(false)
      button(root, "Send report").fire()
    }
    await(root, "Not sent yet")
    val shown = texts(root)
    assertTrue(shown.contains("You aren't connected to a server right now."))
    assertTrue(shown.contains("It's saved on this computer and will be sent by itself the next time you log in."))

    val waiting = Diagnostics.outbox.pending
    assertEquals(1, waiting.size)
    val zip = new ZipFile(Diagnostics.outbox.zip(waiting.head.id).toFile)
    try {
      val report = JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("report.json")).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject
      assertEquals("lag", report.get("category").getAsString)
      assertEquals("rubber-banding all match", report.get("description").getAsString)
      assertEquals("the moment left out isn't in it", Seq(2), (0 until report.getAsJsonArray("marks").size).map(i =>
        report.getAsJsonArray("marks").get(i).getAsJsonObject.get("number").getAsInt))
      assertNotNull("the match's recording is", zip.getEntry("recording/seg-0000.rec"))
      assertEquals("me", report.getAsJsonObject("connection").get("player").getAsString)
    } finally zip.close()
  }

  @Test def aReportTheServerWontTakeNowWaitsAndSaysWhy(): Unit = {
    played(marks = 0)
    answering(p => ProblemReportPacket.refused(1, t.id, p.getReportId, ProblemReportRefusal.TOO_MANY, 900))
    val root = open(ReportAbout.LastMatch)
    Fx(button(root, "Send report").fire())
    await(root, "Not sent yet")
    val shown = texts(root)
    assertTrue(shown.contains("You've sent several reports recently. The server will take more in a while."))
    assertTrue(shown.contains("Try again"))
    assertEquals(1, Diagnostics.outbox.pending.size)
  }

  @Test def cancellingTakesTheCardAwayAndSendsNothing(): Unit = {
    played(marks = 1)
    val root = open(ReportAbout.LastMatch)
    val before = Fx(root.getChildren.size)
    Fx(button(root, "Cancel").fire())
    assertEquals(before - 1, Fx(root.getChildren.size))
    assertFalse(texts(root).contains("Report a problem") && texts(root).contains("Send report"))
    assertEquals(0, begins.size)
    assertEquals(Nil, t.sent.collect { case p: ProblemReportPacket => p })
    assertEquals(Nil, Diagnostics.outbox.entries)
  }

  @Test def theScoreboardAsksForAReportWhenTheMatchGaveReasonTo(): Unit = {
    played(marks = 2)
    t.gameEvent(GameEvent.GAME_OVER)
    t.gameEvent(GameEvent.SCORE_ENTRY, who = t.id, kills = 1, deaths = 0, rank = 1)
    t.gameEvent(GameEvent.SCORE_END)
    val shown = Fx {
      val app = new ClientMain()
      app.client = t.client
      val stage = new Stage()
      app.showScoreboard(stage)
      Fx.labels(stage.getScene.getRoot)
    }
    assertTrue(shown.contains("You marked 2 moments in this match. Tell us what happened?"))
    assertTrue(shown.contains("Report it"))
    assertTrue(shown.contains("Report a problem"))
  }

  @Test def theScoreboardSaysWhatWentWrongInTheMatch(): Unit = {
    played(marks = 0, trouble = true)
    t.gameEvent(GameEvent.GAME_OVER)
    t.gameEvent(GameEvent.SCORE_END)
    val shown = Fx {
      val app = new ClientMain()
      app.client = t.client
      val stage = new Stage()
      app.showScoreboard(stage)
      Fx.labels(stage.getScene.getRoot)
    }
    assertTrue(shown.contains("Something went wrong in this match: the game froze for a while. A report helps us fix it."))
  }
}
