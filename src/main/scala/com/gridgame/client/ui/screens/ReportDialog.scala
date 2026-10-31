package com.gridgame.client.ui.screens

import com.gridgame.client.diagnostics.{ChosenMoment, Diagnostics, IncidentNote, MarkedMoment, MatchRecord, NoticedMoment, OutboxEntry, PreviousRun, ReportBuilder}
import com.gridgame.client.game.{ClientState, GameClient, ReportSender}
import com.gridgame.client.i18n.{I18n, Messages}
import com.gridgame.client.ui.Icons
import com.gridgame.common.diagnostics.Recording.{IncidentKind, NoticedKind}
import com.gridgame.common.model.CharacterDef
import com.gridgame.common.protocol.{ProblemReportKind, ProblemReportRefusal}

import javafx.animation.{FadeTransition, PauseTransition, SequentialTransition}
import javafx.application.Platform
import javafx.geometry.{Insets, Pos}
import javafx.scene.Node
import javafx.scene.control.{Button, CheckBox, Label, ProgressBar, ScrollPane, TextArea, TextField, TextFormatter}
import javafx.scene.image.{Image, ImageView}
import javafx.scene.input.{Clipboard, ClipboardContent, KeyCode, KeyEvent}
import javafx.scene.control.Tooltip
import javafx.scene.layout.{FlowPane, HBox, Pane, Priority, Region, StackPane, VBox}
import javafx.stage.{FileChooser, Stage}
import javafx.util.Duration

import java.nio.file.{Files, StandardCopyOption}
import java.time.{Instant, ZoneId}
import java.time.format.{DateTimeFormatter, FormatStyle}
import scala.jdk.CollectionConverters._

import com.gridgame.client.ui.Theme._
import com.gridgame.client.ui.Widgets._

/** What a problem report is about, which is where it was asked for. */
private[client] sealed trait ReportAbout
private[client] object ReportAbout {
  /** The match just played (the scoreboard's button), or left. */
  case object LastMatch extends ReportAbout
  /** The match the window had to close, `error` being why. */
  final case class MatchError(error: String) extends ReportAbout
  /** The run before this one, which never closed properly. */
  case object PreviousRun extends ReportAbout
  /** Anything at all (the top bar's button): the last match goes with it if the player wants. */
  case object General extends ReportAbout
}

/**
 * A problem report, as the player makes it (design/diagnostics.md, *Reporting*): a card over whatever
 * page is showing, which asks what kind of problem it was and what happened, shows the moments the player
 * marked in the match (their pictures, each kept or left out) and says plainly what goes with it, then
 * sends it — the progress as it goes, then the code the server keeps it under, or why it couldn't go and
 * that it will go by itself the next time the player logs in. "Save a copy" writes the same report to a
 * file of the player's choosing, to send some other way.
 */
private[client] final class ReportDialog(app: Screens) {
  import ReportDialog._

  def show(stage: Stage, about: ReportAbout): Unit = {
    val root = stage.getScene match {
      case null => return
      case scene => scene.getRoot match {
        case p: StackPane => p
        case _ => return
      }
    }
    new Open(stage, root, about).show()
  }

  /** One report being made: its card, and what it is made of. */
  private final class Open(stage: Stage, root: StackPane, about: ReportAbout) {
    private val client: GameClient = app.client
    private val record: Option[MatchRecord] = about match {
      case ReportAbout.PreviousRun => None
      case _ => Diagnostics.lastMatch.filterNot(_.isRecording)
    }
    private val previous: Option[PreviousRun] = about match {
      case ReportAbout.PreviousRun => Diagnostics.previousRun
      case _ => None
    }
    private val kind: Byte = about match {
      case ReportAbout.PreviousRun => ProblemReportKind.CRASH
      case ReportAbout.General => ProblemReportKind.GENERAL
      case _ => ProblemReportKind.MATCH
    }
    record.foreach(Diagnostics.pin)

    // The card: its heading and its buttons stay put, and what is between them scrolls when the window
    // is too short for it (what is sent, opened, on a small laptop's)
    private val scrim = new StackPane()
    private val head = new HBox(12)
    private val body = new VBox(14)
    private val foot = new VBox(10)
    private val middle = new ScrollPane(body)
    private val dialog = card(head, middle, foot)
    private var category = ""
    // When the player says it happened, if they picked it on the timeline
    private var chosen: Option[ChosenMoment] = None
    private var sending = false
    private var sentEntry: OutboxEntry = _
    // The match whose recording went into it, to let go once the server has it
    private var sentRecord: Option[MatchRecord] = None

    def show(): Unit = {
      scrim.getStyleClass.add("dialog-scrim")
      dialog.getStyleClass.add("dialog")
      dialog.setMaxWidth(660)
      dialog.setMaxHeight(Region.USE_PREF_SIZE)
      head.setAlignment(Pos.CENTER_LEFT)
      middle.setFitToWidth(true)
      middle.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER)
      middle.getStyleClass.add("dialog-scroll")
      // As tall as what is in it, laid out at the card's width (its own guess lays it out narrower, and
      // came out a hand's breadth too tall); the window, if shorter, squeezes it and it scrolls
      middle.prefViewportHeightProperty().bind(body.heightProperty())
      VBox.setVgrow(middle, Priority.ALWAYS)
      StackPane.setAlignment(dialog, Pos.CENTER)
      scrim.getChildren.add(dialog)
      scrim.setPadding(new Insets(24))
      // Clicks on the shade go nowhere: the page under it waits
      scrim.setOnMouseClicked(e => e.consume())
      scrim.addEventFilter(KeyEvent.KEY_PRESSED, (e: KeyEvent) => if (e.getCode == KeyCode.ESCAPE && !sending) { close(); e.consume() })
      root.getChildren.add(scrim)
      form()
      val fade = new FadeTransition(Duration.millis(160), scrim)
      fade.setFromValue(0)
      fade.setToValue(1)
      fade.play()
    }

    private def close(): Unit = {
      root.getChildren.remove(scrim)
      record.foreach(Diagnostics.unpin)
    }

    // ── The form ───────────────────────────────────────────────────────────

    private def form(): Unit = {
      body.getChildren.clear()
      val closeBtn = iconButton(Icons.Close, Messages.t("Close"))
      closeBtn.setOnAction(_ => if (!sending) { dontSend(); close() })
      head.getChildren.setAll(Icons.node(Icons.Flag, 22, Palette.Gold), h2(Messages.t("Report a problem")), grow(), closeBtn)

      val lead = about match {
        case ReportAbout.LastMatch => Messages.t("Tell us what went wrong in the match you just played.")
        case ReportAbout.MatchError(_) => Messages.t("The match closed because of an error. We're sorry! Telling us about it helps us fix it.")
        case ReportAbout.PreviousRun => Messages.t("Grid Game didn't close properly last time. If it crashed or froze, a report helps us fix it.")
        case ReportAbout.General => Messages.t("Tell us what went wrong: in a match, in the menus, anywhere.")
      }
      body.getChildren.add(para(lead, "body"))
      previous.foreach(r => body.getChildren.add(previousRunNote(r)))
      record.foreach(r => body.getChildren.add(matchLine(r)))

      // What kind of problem: one, or none
      val kinds = new FlowPane(8, 8)
      val chips = Categories.map { case (key, label) =>
        val b = new Button(label)
        b.getStyleClass.add("filter-chip")
        b.setOnAction(_ => {
          category = if (category == key) "" else key
          kinds.getChildren.asScala.foreach(_.getStyleClass.remove("filter-chip-active"))
          if (category.nonEmpty) b.getStyleClass.add("filter-chip-active")
        })
        if (about == ReportAbout.PreviousRun && key == "crash") { category = key; b.getStyleClass.add("filter-chip-active") }
        b
      }
      kinds.getChildren.addAll(chips: _*)
      body.getChildren.add(new VBox(8, eyebrow(Messages.t("What kind of problem?")), kinds))

      // What happened, in the player's words
      val words = new TextArea()
      words.getStyleClass.add("report-text")
      words.setWrapText(true)
      words.setPrefRowCount(4)
      words.setPromptText(Messages.t("What did you see, and what did you expect to happen? Where on the map, and roughly when?"))
      words.setTextFormatter(new TextFormatter[String]((c: TextFormatter.Change) => if (c.getControlNewText.length <= MaxWords) c else null))
      val count = text(s"0 / $MaxWords", "small", "text-faint")
      words.textProperty().addListener((_, _, t) => count.setText(s"${t.length} / $MaxWords"))
      val countRow = new HBox(grow(), count)
      body.getChildren.add(new VBox(6, text(Messages.t("What happened?"), "field-label"), words, countRow))

      // When: the moments the player marked, and the match laid out to pick the moment from, with what
      // the game noticed on it (the whole match goes with the report either way)
      val marks = record.map(_.marks.asScala.toVector).getOrElse(Vector.empty)
      val kept = scala.collection.mutable.LinkedHashMap(marks.map(m => m -> true): _*)
      if (marks.nonEmpty) body.getChildren.add(marksStrip(marks, kept))
      record.foreach(r => body.getChildren.add(whenSection(r, marks)))
      if (marks.isEmpty && record.isDefined)
        body.getChildren.add(para(Messages.t("Tip: in a match, press F8 the moment something goes wrong (Fn+F8 on some laptops). It marks the moment and takes a picture, so we can find it."), "small", "muted"))

      // A general report can take the last match along
      val withMatch = new CheckBox(Messages.t("Include my last match"))
      if (about == ReportAbout.General && record.isDefined) {
        withMatch.setSelected(true)
        body.getChildren.add(withMatch)
      }
      val includeRecording = () => about != ReportAbout.General || (record.isDefined && withMatch.isSelected)

      body.getChildren.add(whatIsSent(marks, kept, includeRecording))

      val status = text("", "status")
      status.setWrapText(true)
      status.managedProperty().bind(status.textProperty().isNotEmpty)
      val progress = new ProgressBar(0)
      progress.getStyleClass.add("report-progress")
      progress.setMaxWidth(Double.MaxValue)
      progress.setVisible(false)
      progress.setManaged(false)

      val save = button(Messages.t("Save a copy..."), "ghost", Icons.Download, Palette.Muted)
      val cancel = button(if (about == ReportAbout.PreviousRun) Messages.t("Don't send") else Messages.t("Cancel"), "plain")
      val send = button(Messages.t("Send report"), "sun", Icons.Send, Palette.OnGold)
      send.setDefaultButton(true)
      val buttons = row(10, save, grow(), cancel, send)
      foot.getChildren.setAll(status, progress, buttons)
      words.requestFocus()

      def request(): ReportBuilder.Request = {
        val keptMarks = kept.collect { case (m, true) => m }.toVector
        ReportBuilder.Request(kind, category, words.getText.trim, chosen.map(c => ReportBuilder.matchTime(c.atMicros)).getOrElse(""),
          if (includeRecording()) record else None, includeRecording(), if (includeRecording()) keptMarks else Nil,
          previous, if (client != null) client.connectionSummary else null,
          if (client != null && record.isDefined && includeRecording()) client.matchSummary else null,
          chosen = if (includeRecording()) chosen else None)
      }
      def busy(on: Boolean): Unit = {
        sending = on
        Seq(save, cancel, send, closeBtn).foreach(_.setDisable(on))
        words.setEditable(!on)
      }

      cancel.setOnAction(_ => { dontSend(); close() })
      save.setOnAction(_ => {
        val chooser = new FileChooser()
        chooser.setTitle(Messages.t("Save a copy of the report"))
        chooser.setInitialFileName(s"grid-game-report-${DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").withZone(ZoneId.systemDefault()).format(Instant.now())}.zip")
        chooser.getExtensionFilters.add(new FileChooser.ExtensionFilter("Zip", "*.zip"))
        val target = chooser.showSaveDialog(stage)
        if (target != null) {
          busy(true)
          showStatus(status, Messages.t("Packing your report..."), Tone.Info)
          val req = request()
          worker("ReportSave") {
            val outcome = try {
              val entry = ReportBuilder.build(req, Diagnostics.outbox)
              Files.copy(Diagnostics.outbox.zip(entry.id), target.toPath, StandardCopyOption.REPLACE_EXISTING)
              // A copy to keep is not a report to send: nothing waits in the outbox for the next login
              Diagnostics.outbox.discard(entry.id)
              None
            } catch { case e: Exception => Some(e) }
            Platform.runLater(() => {
              busy(false)
              outcome match {
                case None => showStatus(status, Messages.t("Saved to {0}", target.getName), Tone.Ok)
                case Some(e) => showStatus(status, Messages.t("Could not save it ({0})", String.valueOf(e.getMessage)), Tone.Error)
              }
            })
          }
        }
      })

      send.setOnAction(_ => {
        busy(true)
        showStatus(status, Messages.t("Packing your report..."), Tone.Info)
        val req = request()
        worker("ReportSend") {
          val entry = try Some(ReportBuilder.build(req, Diagnostics.outbox)) catch {
            case e: Exception =>
              System.err.println(s"ReportDialog: couldn't put the report together: $e")
              Platform.runLater(() => { busy(false); showStatus(status, Messages.t("Could not put the report together ({0})", String.valueOf(e.getMessage)), Tone.Error) })
              None
          }
          entry.foreach { e =>
            sentEntry = e
            sentRecord = req.matchRecord
            if (previous.isDefined) Diagnostics.forgetPreviousRun()
            val c = client
            if (c == null || c.clientState == ClientState.CONNECTING) Platform.runLater(() => notSent(ReportSender.Broken, offline = true))
            else {
              Platform.runLater(() => {
                progress.setVisible(true); progress.setManaged(true); progress.setProgress(0)
                showStatus(status, Messages.t("Sending..."), Tone.Info)
              })
              val result = ReportSender.sendAlone(c, Diagnostics.outbox, e, (n, of) => Platform.runLater(() => {
                progress.setProgress(if (of > 0) n.toDouble / of else 0)
                showStatus(status, Messages.t("Sending... {0} of {1}", size(n), size(of)), Tone.Info)
              }))
              if (result.isInstanceOf[ReportSender.Sent]) req.matchRecord.foreach(Diagnostics.discard)
              Platform.runLater(() => result match {
                case ReportSender.Sent(code) => thanks(code)
                case ReportSender.NotSent(reason, _, _) => notSent(reason, offline = false)
              })
            }
          }
        }
      })
    }

    /**
     * When it happened: the match from start to end, the moments the player marked, what went wrong and
     * what the game noticed on it, and a click anywhere on it (or on one of them, or on a line of the list
     * below) picks the moment. Picked, it says when and what, and goes with the report.
     */
    private def whenSection(r: MatchRecord, marks: Seq[MarkedMoment]): Node = {
      val duration = Math.max(r.durationMicros, 1000000L)
      val noticed = r.noticed.asScala.toVector.sortBy(_.atMicros)
      val incidents = r.incidents.asScala.toVector
      val items =
        marks.map(m => Spot(m.atMicros, "mark", Messages.t("Moment {0} you marked", m.number.toString), "timeline-mark")) ++
          incidents.map(n => Spot(n.atMicros, "incident", capitalize(incidentText(n.kind)), "timeline-incident")) ++
          noticed.map(n => Spot(n.atMicros, "noticed", noticedText(n),
            if (n.kind == NoticedKind.Died) "timeline-died" else "timeline-noticed"))
      val picked = text("", "small", "text-gold")
      picked.setWrapText(true)
      val clear = button(Messages.t("Clear"), "ghost")
      clear.getStyleClass.add("btn-sm")
      clear.setVisible(false)
      clear.setManaged(false)
      lazy val timeline: Timeline = new Timeline(duration, items, spot => pick(spot))
      def pick(spot: Spot): Unit = {
        chosen = Some(ChosenMoment(spot.atMicros, spot.source, spot.text))
        timeline.select(spot.atMicros)
        picked.setText(if (spot.text.isEmpty) Messages.t("Around {0}", ReportBuilder.matchTime(spot.atMicros))
                       else Messages.t("Around {0}: {1}", ReportBuilder.matchTime(spot.atMicros), spot.text))
        clear.setVisible(true); clear.setManaged(true)
      }
      clear.setOnAction(_ => {
        chosen = None
        timeline.select(-1L)
        picked.setText("")
        clear.setVisible(false); clear.setManaged(false)
      })
      val hint = text(Messages.t("Click where on the match it happened"), "small", "muted")
      picked.managedProperty().bind(picked.textProperty().isNotEmpty)
      hint.managedProperty().bind(picked.textProperty().isEmpty)
      hint.visibleProperty().bind(picked.textProperty().isEmpty)
      val section = new VBox(8, eyebrow(Messages.t("When did it happen?")), timeline, row(8, hint, picked, grow(), clear))

      // What the game noticed, in words: the first few, the rest on the timeline
      if (noticed.nonEmpty) {
        val list = new VBox(6)
        def rows(all: Boolean): Unit = list.getChildren.setAll(
          (if (all) noticed else noticed.take(ListShown)).map { n =>
            val line = new HBox(10, text(ReportBuilder.matchTime(n.atMicros), "small", "text-sky"), text(noticedText(n), "small", "body"))
            line.getStyleClass.add("noticed-row")
            line.setOnMouseClicked(_ => pick(Spot(n.atMicros, "noticed", noticedText(n), "")))
            line
          }: _*)
        rows(all = false)
        section.getChildren.addAll(text(Messages.t("What the game noticed"), "field-label"), list)
        if (noticed.size > ListShown) {
          val more = button(Messages.t("Show all {0}", noticed.size.toString), "ghost")
          more.getStyleClass.add("btn-sm")
          more.setOnAction(_ => { rows(all = true); more.setVisible(false); more.setManaged(false) })
          section.getChildren.add(more)
        }
      }
      section
    }

    /** The player won't send it: a crashed run isn't offered again. */
    private def dontSend(): Unit = if (about == ReportAbout.PreviousRun) Diagnostics.forgetPreviousRun()

    /** "Meadow · 5 min · ended 2 minutes ago" */
    private def matchLine(r: MatchRecord): Node = {
      val info = r.matchInfo
      def str(k: String): String = if (info.has(k) && info.get(k).isJsonPrimitive) info.get(k).getAsString else ""
      // As WorldRegistry names a map: "the_lagoon.json" is The Lagoon
      val map = str("map").stripSuffix(".json").replace('_', ' ').split(' ').map(_.capitalize).mkString(" ").trim
      val minutes = Math.max(0L, (if (r.durationMicros > 0) r.durationMicros / 1000L else r.endedAtMs - r.startedAtMs) / 60000L)
      val bits = Seq(
        map,
        str("character"),
        Messages.t("{0} min", minutes.max(1L).toString)).filter(_.nonEmpty)
      row(8, Icons.node(Icons.MapPin, 14, Palette.Muted), text(bits.mkString("  ·  "), "muted"))
    }

    private def previousRunNote(r: PreviousRun): Node = {
      val at = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(Messages.currentLocale)
        .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(if (r.lastSeenAtMs > 0) r.lastSeenAtMs else r.startedAtMs))
      val what = Seq(Some(Messages.t("It was last seen running {0}.", at)),
        if (r.inMatch) Some(Messages.t("It was in a match: we'll include what it recorded.")) else None,
        r.hsErr.map(_ => Messages.t("Java wrote a crash report, which goes with it."))).flatten
      val l = para(what.mkString(" "), "small", "muted")
      val box = new VBox(l)
      box.getStyleClass.add("inset")
      box
    }

    /** The moments marked in the match: each one's picture and time, kept or left out. */
    private def marksStrip(marks: Seq[MarkedMoment], kept: scala.collection.mutable.Map[MarkedMoment, Boolean]): Node = {
      val strip = new FlowPane(10, 10)
      marks.foreach { m =>
        val frame = new StackPane()
        frame.getStyleClass.add("shot-frame")
        frame.setPrefSize(ShotW, ShotH)
        frame.setMaxSize(ShotW, ShotH)
        if (m.screenshot != null) {
          // Written off the drawing thread a moment after the key: shown once it is there
          val view = new ImageView()
          view.setFitWidth(ShotW); view.setFitHeight(ShotH); view.setPreserveRatio(true)
          def load(): Unit = if (Files.isRegularFile(m.screenshot)) view.setImage(new Image(m.screenshot.toUri.toString, ShotW * 2, 0, true, true, true))
          load()
          if (view.getImage == null) { val wait = new PauseTransition(Duration.millis(600)); wait.setOnFinished(_ => load()); wait.play() }
          frame.getChildren.add(view)
        } else frame.getChildren.add(Icons.node(Icons.Camera, 22, Palette.Faint))
        val time = chip(Messages.t("{0} into the match", ReportBuilder.matchTime(m.atMicros)), "gold")
        if (m.frames.nonEmpty) Tooltip.install(time, new Tooltip(Messages.t("With the {0} seconds before it", secondsBefore(m).toString)))
        val include = new CheckBox(Messages.t("Include"))
        include.setSelected(true)
        include.selectedProperty().addListener((_, _, on) => { kept(m) = on; frame.setOpacity(if (on) 1 else 0.35) })
        strip.getChildren.add(new VBox(6, frame, row(8, time, grow(), include)))
      }
      new VBox(8, eyebrow(Messages.t("Moments you marked")), strip)
    }

    /** What goes with the report, said plainly, and what never does. */
    private def whatIsSent(marks: Seq[MarkedMoment], kept: scala.collection.mutable.Map[MarkedMoment, Boolean],
                           includeRecording: () => Boolean): Node = {
      val list = new VBox(8)
      list.getStyleClass.add("inset")
      def item(icon: Icons.Icon, s: String): Node = {
        val l = para(s, "small", "body")
        HBox.setHgrow(l, Priority.ALWAYS)
        val r = new HBox(10, Icons.node(icon, 14, Palette.Muted), l)
        r.setAlignment(Pos.TOP_LEFT)
        r
      }
      def fill(): Unit = {
        list.getChildren.clear()
        val recording = record.filter(_ => includeRecording()).map(_.bytes)
          .orElse(previous.flatMap(_.recording).map(d => ReportBuilder.segmentsIn(d).map(p => try Files.size(p) catch { case _: Exception => 0L }).sum))
        recording.foreach(b => list.getChildren.add(item(Icons.Play,
          Messages.t("A recording of the match: what your game sent and received, the keys you pressed and where you aimed, and how long each frame took ({0})", size(b)))))
        val keptMarks = marks.filter(m => kept.getOrElse(m, false))
        val pictures = keptMarks.count(_.screenshot != null)
        val before = keptMarks.map(_.frames.size).sum
        if (pictures > 0 && includeRecording()) list.getChildren.add(item(Icons.Camera,
          if (before > 0) Messages.t("Pictures of the moments you marked ({0}), and {1} small ones of the seconds before them", pictures.toString, before.toString)
          else Messages.t("Pictures of the moments you marked ({0})", pictures.toString)))
        Option(Diagnostics.log).foreach(l => list.getChildren.add(item(Icons.Chat,
          Messages.t("The log your game kept since it started ({0})", size(try Files.size(l.path) catch { case _: Exception => 0L })))))
        previous.foreach { r =>
          if (r.log.isDefined) list.getChildren.add(item(Icons.Chat, Messages.t("The log of the time it didn't close properly")))
          if (r.hsErr.isDefined) list.getChildren.add(item(Icons.Alert, Messages.t("Java's crash report (it lists your computer's settings)")))
        }
        list.getChildren.add(item(Icons.Server, Messages.t("Your computer: its system, graphics card, screen and Java, and this game's version")))
        list.getChildren.add(item(Icons.Check, Messages.t("Never sent: your password, your login, your chat messages, or anything outside the game.")))
      }
      fill()
      val toggle = button(Messages.t("What's sent"), "ghost", Icons.ChevronDown, Palette.Muted)
      toggle.getStyleClass.add("btn-sm")
      list.setVisible(false); list.setManaged(false)
      toggle.setOnAction(_ => {
        val open = !list.isVisible
        if (open) fill()
        list.setVisible(open); list.setManaged(open)
        toggle.setGraphic(Icons.node(if (open) Icons.ChevronUp else Icons.ChevronDown, 16, Palette.Muted))
      })
      new VBox(8, toggle, list)
    }

    // ── After sending ─────────────────────────────────────────────────────

    private def thanks(code: String): Unit = {
      sending = false
      head.getChildren.clear()
      foot.getChildren.clear()
      body.getChildren.clear()
      val codeField = new TextField(code)
      codeField.setEditable(false)
      codeField.getStyleClass.add("report-code")
      codeField.setMaxWidth(260)
      val copy = button(Messages.t("Copy code"), "plain", Icons.Copy, Palette.Muted)
      copy.setOnAction(_ => {
        val content = new ClipboardContent()
        content.putString(code)
        Clipboard.getSystemClipboard.setContent(content)
        copy.setText(Messages.t("Copied"))
      })
      val done = button(Messages.t("Done"), "sun", "lg")
      done.setDefaultButton(true)
      done.setOnAction(_ => close())
      val top = new VBox(10, Icons.node(Icons.Check, 40, Palette.Leaf), h2(Messages.t("Thanks! Your report is in.")))
      top.setAlignment(Pos.CENTER)
      val codeRow = row(10, codeField, copy)
      codeRow.setAlignment(Pos.CENTER)
      val doneRow = new HBox(done)
      doneRow.setAlignment(Pos.CENTER)
      body.setAlignment(Pos.TOP_CENTER)
      body.getChildren.addAll(top, para(Messages.t("Its code is:"), "body"), codeRow,
        para(Messages.t("Mention the code if you get in touch with us about this problem."), "small", "muted"))
      foot.getChildren.setAll(doneRow)
      done.requestFocus()
    }

    private def notSent(reason: Int, offline: Boolean): Unit = {
      sending = false
      head.getChildren.clear()
      foot.getChildren.clear()
      body.getChildren.clear()
      val why =
        if (offline) Messages.t("You aren't connected to a server right now.")
        else reasonText(reason)
      val retryable = offline || ReportSender.worthRetrying(reason)
      head.getChildren.setAll(Icons.node(Icons.Alert, 22, if (retryable) Palette.Gold else Palette.Berry),
        h2(if (retryable) Messages.t("Not sent yet") else Messages.t("Couldn't send it")))
      body.getChildren.add(para(why, "body"))
      if (retryable) body.getChildren.add(para(Messages.t("It's saved on this computer and will be sent by itself the next time you log in."), "muted"))
      val closeBtn = button(Messages.t("Close"), "plain")
      closeBtn.setOnAction(_ => close())
      val buttons = row(10, grow(), closeBtn)
      if (retryable && !offline && sentEntry != null) {
        val again = button(Messages.t("Try again"), "sun")
        again.setDefaultButton(true)
        again.setOnAction(_ => {
          val e = sentEntry
          val status = text(Messages.t("Sending..."), "status")
          foot.getChildren.add(0, status)
          again.setDisable(true); closeBtn.setDisable(true); sending = true
          worker("ReportSend") {
            val result = ReportSender.sendAlone(client, Diagnostics.outbox, Diagnostics.outbox.get(e.id).getOrElse(e))
            if (result.isInstanceOf[ReportSender.Sent]) sentRecord.foreach(Diagnostics.discard)
            Platform.runLater(() => result match {
              case ReportSender.Sent(code) => thanks(code)
              case ReportSender.NotSent(r, _, _) => notSent(r, offline = false)
            })
          }
        })
        buttons.getChildren.add(again)
      }
      foot.getChildren.setAll(buttons)
    }
  }
}

private[client] object ReportDialog {
  /** The kinds of problem a player can pick, by what report.json calls each. */
  def Categories: Seq[(String, String)] = Seq(
    "movement" -> Messages.t("Movement"),
    "hits" -> Messages.t("Hits & damage"),
    "abilities" -> Messages.t("Abilities"),
    "lag" -> Messages.t("Lag or disconnects"),
    "graphics" -> Messages.t("Graphics"),
    "sound" -> Messages.t("Sound"),
    "crash" -> Messages.t("Crash or freeze"),
    "other" -> Messages.t("Something else"))

  /** The most the player can write. */
  val MaxWords = 2000
  /** What the game noticed, as many lines as the card lists before "Show all". */
  private val ListShown = 4

  /** A moment on the timeline: when, what it is (report.json's chosenMoment source), what it says, its look. */
  private final case class Spot(atMicros: Long, source: String, text: String, styleClass: String)

  /** How many seconds of pictures a mark has before it. */
  private def secondsBefore(m: MarkedMoment): Long =
    if (m.frames.isEmpty) 0L else Math.max(1L, Math.round((m.atMicros - m.frames.map(_.atMicros).min) / 1e6))

  private def capitalize(s: String): String = if (s.isEmpty) s else s.substring(0, 1).toUpperCase(Messages.currentLocale) + s.substring(1)

  /** What the game noticed, in the player's language. */
  def noticedText(n: NoticedMoment): String = n.kind match {
    case NoticedKind.SnapBack => Messages.t("The server moved you {0} cells", f"${n.a / 100.0}%.1f")
    case NoticedKind.ShotUnanswered => Messages.t("Your {0} got no answer from the server", n.a match {
      case 1 => "Q"
      case 2 => "E"
      case 3 => Messages.t("burst shot")
      case _ => Messages.t("primary attack")
    })
    case NoticedKind.Hitch => Messages.t("The game stalled for {0} ms", n.a.toString)
    case NoticedKind.ServerQuiet => Messages.t("The server went quiet for {0} s", f"${n.a / 1000.0}%.1f")
    case NoticedKind.Died =>
      if (n.a >= 0) Messages.t("You died (killed by {0})", I18n.characterName(CharacterDef.get(n.a.toByte)))
      else Messages.t("You died")
    case NoticedKind.Refused => n.a match {
      case 0 => Messages.t("The server refused your R")
      case 1 => Messages.t("The server refused your trap")
      case _ => Messages.t("The server refused an item")
    }
    case NoticedKind.PingSpike => Messages.t("Your ping spiked to {0} ms", n.a.toString)
    case _ => Messages.t("Something the game noticed")
  }

  /**
   * The match from its start to its end, as a track: a dot for each moment on it (marked, gone wrong,
   * noticed), a tooltip each, minutes along it, and the picked moment a gold line. A click on a dot
   * picks it; anywhere else on the track picks that time.
   */
  private final class Timeline(durationMicros: Long, spots: Seq[Spot], onPick: Spot => Unit) extends Pane {
    getStyleClass.add("timeline")
    private val Pad = 10.0
    private val TrackY = 12.0
    private val track = new Region()
    track.getStyleClass.add("timeline-track")
    private val cursor = new Region()
    cursor.getStyleClass.add("timeline-cursor")
    cursor.setVisible(false)
    cursor.setMouseTransparent(true)
    private var selected = -1L
    // A label a minute, or every few minutes in a long match
    private val stepS: Long = {
      val minutes = Math.max(1L, durationMicros / 60000000L)
      if (minutes <= 6) 60L else if (minutes <= 15) 180L else 300L
    }
    private val ticks: Seq[(Long, Label)] = (0L to durationMicros / 1000000L by stepS).map { s =>
      val l = text(ReportBuilder.matchTime(s * 1000000L), "timeline-tick")
      l.setMouseTransparent(true)
      (s * 1000000L) -> l
    }
    private val dots: Seq[(Spot, Region)] = spots.map { spot =>
      val d = new Region()
      d.getStyleClass.addAll("timeline-dot", spot.styleClass)
      Tooltip.install(d, new Tooltip(s"${ReportBuilder.matchTime(spot.atMicros)}  ${spot.text}"))
      d.setOnMouseClicked(e => { onPick(spot); e.consume() })
      spot -> d
    }
    getChildren.add(track)
    ticks.foreach(t => getChildren.add(t._2))
    dots.foreach(d => getChildren.add(d._2))
    getChildren.add(cursor)
    setMinHeight(44)
    setPrefHeight(44)
    setOnMouseClicked(e => {
      val w = getWidth - 2 * Pad
      if (w > 0) {
        val at = (((e.getX - Pad) / w).max(0.0).min(1.0) * durationMicros).toLong
        onPick(Spot(at, "timeline", "", ""))
      }
    })

    private def x(at: Long): Double = Pad + (getWidth - 2 * Pad) * (at.toDouble / durationMicros).max(0.0).min(1.0)

    /** Show the picked moment (or none, at -1). */
    def select(at: Long): Unit = { selected = at; requestLayout() }

    override def layoutChildren(): Unit = {
      track.resizeRelocate(Pad, TrackY, Math.max(0.0, getWidth - 2 * Pad), 6)
      ticks.foreach { case (at, l) =>
        val w = l.prefWidth(-1)
        l.resizeRelocate((x(at) - w / 2).max(0.0).min(getWidth - w), TrackY + 12, w, l.prefHeight(w))
      }
      dots.foreach { case (spot, d) => d.resizeRelocate(x(spot.atMicros) - 6, TrackY - 3, 12, 12) }
      cursor.setVisible(selected >= 0)
      if (selected >= 0) cursor.resizeRelocate(x(selected) - 1, TrackY - 7, 2, 20)
    }
  }
  private val ShotW = 176.0
  private val ShotH = 99.0

  /** What a refusal or a failure means, for the player. */
  def reasonText(reason: Int): String = reason match {
    case ProblemReportRefusal.TOO_MANY => Messages.t("You've sent several reports recently. The server will take more in a while.")
    case ProblemReportRefusal.TOO_BIG => Messages.t("It's bigger than the server takes.")
    case ProblemReportRefusal.STORAGE_FULL => Messages.t("The server has no room for more reports right now.")
    case ProblemReportRefusal.BUSY => Messages.t("The server is busy with other reports.")
    case ProblemReportRefusal.UNAVAILABLE => Messages.t("The server couldn't keep it right now.")
    case ReportSender.NoAnswer => Messages.t("The server didn't answer. It may not take reports yet.")
    case ReportSender.Broken => Messages.t("Couldn't reach the server to send it.")
    case ReportSender.Missing => Messages.t("The report's file is missing.")
    case _ => Messages.t("The report didn't arrive whole.")
  }

  /** "3.4 MB" */
  def size(bytes: Long): String =
    if (bytes >= 1024L * 1024) f"${bytes / (1024.0 * 1024)}%.1f MB"
    else if (bytes >= 1024L) s"${bytes / 1024} KB"
    else s"$bytes B"

  /** A thread of its own for the slow part (packing, sending), never the FX thread. */
  private def worker(name: String)(body: => Unit): Unit = {
    val t = new Thread(() => body, name)
    t.setDaemon(true)
    t.start()
  }

  /** What went wrong in a match, in a few words, for the scoreboard. */
  def incidentText(kind: Int): String = kind match {
    case IncidentKind.Error | IncidentKind.PacketError => Messages.t("the game hit an error")
    case IncidentKind.RenderCrash => Messages.t("the match's window hit an error")
    case IncidentKind.Freeze => Messages.t("the game froze for a while")
    case IncidentKind.Disconnect => Messages.t("the connection dropped")
    case IncidentKind.QueueFull | IncidentKind.SendDropped => Messages.t("the game fell behind the server")
    case IncidentKind.BadSignature => Messages.t("some of the server's messages were garbled")
    case _ => Messages.t("something went wrong")
  }

  /**
   * A line at the foot of the page for a few seconds ("Your saved report was sent"): over whatever page
   * is showing, gone by itself.
   */
  def toast(stage: Stage, message: String): Unit = {
    val root = Option(stage.getScene).map(_.getRoot).collect { case p: StackPane => p }.orNull
    if (root == null) return
    val l = text(message, "toast")
    l.setWrapText(true)
    l.setMaxWidth(560)
    StackPane.setAlignment(l, Pos.BOTTOM_CENTER)
    StackPane.setMargin(l, new Insets(0, 24, 28, 24))
    l.setMouseTransparent(true)
    root.getChildren.add(l)
    val in = new FadeTransition(Duration.millis(200), l)
    in.setFromValue(0); in.setToValue(1)
    val out = new FadeTransition(Duration.millis(400), l)
    out.setFromValue(1); out.setToValue(0)
    val seq = new SequentialTransition(in, new PauseTransition(Duration.seconds(5)), out)
    seq.setOnFinished(_ => root.getChildren.remove(l))
    seq.play()
  }
}
