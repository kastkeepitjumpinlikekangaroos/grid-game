package com.gridgame.client.ui

import com.gridgame.client.i18n.Messages
import com.gridgame.common.protocol.LobbyFailure

import javafx.animation.{AnimationTimer, FadeTransition, ParallelTransition, TranslateTransition}
import javafx.collections.FXCollections
import javafx.scene.{Node, Parent, Scene}
import javafx.scene.control.{ComboBox, Label}
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.stage.Stage
import javafx.util.Duration

/**
 * The menus' look as code sees it. Nearly all of it is the stylesheet every screen's scene
 * carries (theme.css): a screen gives its nodes the sheet's classes, and Widgets builds the
 * common ones. What is here is the rest: the fonts, the palette for what is drawn in code (the
 * sky, map previews, canvases), and the few behaviours every screen shares — putting a page on
 * the stage, a status line, a stepped animation loop, the settings every lobby form offers.
 * Stateless: a screen imports it (`import Theme._`).
 */
private[client] object Theme {

  // ── Fonts ──────────────────────────────────────────────────────────────

  /** Nunito, a rounded sans, in four weights. The sheet names them: "Nunito" is Regular and Bold
    * (by weight), "Nunito ExtraBold" and "Nunito Black" are families of their own, because JavaFX
    * only tells regular from bold within a family. Chinese and Korean text falls back to the
    * bundled Noto Sans SC / KR. */
  object Fonts {
    val Black = "Nunito Black"
  }

  /** Register the bundled fonts with JavaFX, before any screen is built. (Exo 2 is the match's
    * font, loaded by the GL renderer itself.) */
  def loadFonts(): Unit =
    Seq("/fonts/Nunito-Regular.ttf", "/fonts/Nunito-Bold.ttf", "/fonts/Nunito-ExtraBold.ttf",
      "/fonts/Nunito-Black.ttf", "/fonts/NotoSansSC-i18n.ttf", "/fonts/NotoSansKR-i18n.ttf").foreach { p =>
      val s = getClass.getResourceAsStream(p)
      if (s != null) try Font.loadFont(s, 14) finally s.close()
    }

  // ── Colours drawn in code ──────────────────────────────────────────────

  /** The sheet's palette, for what code draws itself. Keep in step with theme.css's `.root`. */
  object Palette {
    val Ink: Color = Color.web("#1c3558")
    val Muted: Color = Color.web("#5e7899")
    val Sky: Color = Color.web("#2f8ff5")
    val Gold: Color = Color.web("#e0a100")
    val Leaf: Color = Color.web("#2f9b45")
    val Berry: Color = Color.web("#dc3f57")
    /** The sky's colour at the top of the window: the scene's fill, seen before the sheet applies. */
    val SkyTop: Color = Color.web("#56aefc")

    /** The teams, as the match colours them, dark enough to read as text on white. */
    def team(team: Int): Color = team match {
      case 1 => Color.web("#2f7fef")
      case 2 => Color.web("#e0455a")
      case 3 => Color.web("#2f9b45")
      case 4 => Color.web("#c98400")
      case _ => Muted
    }

    /** A player's colour, from their name: the same everywhere they appear (avatar, chat). */
    def forName(name: String): Color = {
      val colours = Array("#2f8ff5", "#e0455a", "#2f9b45", "#c98400", "#7a55d6", "#e0701f", "#1d9e9e", "#d14e9a")
      Color.web(colours(Math.floorMod(name.toLowerCase.hashCode, colours.length)))
    }
  }

  /** A colour as CSS writes it, for the few inline styles whose colour is decided at run time. */
  def hex(c: Color): String =
    f"#${(c.getRed * 255).round}%02x${(c.getGreen * 255).round}%02x${(c.getBlue * 255).round}%02x"

  // ── The scene ──────────────────────────────────────────────────────────

  /** The sheet, from beside this class on the classpath. */
  lazy val stylesheet: String = getClass.getResource("theme.css").toExternalForm

  def newScene(root: Parent): Scene = {
    val scene = new Scene(root, Palette.SkyTop)
    scene.getStylesheets.add(stylesheet)
    scene
  }

  /**
   * Put a page on the stage, its `body` fading and rising into place the way a web page's content
   * does when it changes: the sky and the top bar are the same on every page, so only what is new
   * moves. `body` is null for a page with nothing to bring in.
   */
  def showPage(stage: Stage, root: Parent, body: Node): Unit = {
    stage.setScene(newScene(root))
    if (body != null) {
      body.setOpacity(0)
      body.setTranslateY(10)
      val fade = new FadeTransition(Duration.millis(220), body)
      fade.setToValue(1)
      val rise = new TranslateTransition(Duration.millis(220), body)
      rise.setToY(0)
      new ParallelTransition(fade, rise).play()
    }
  }

  // ── Status lines ───────────────────────────────────────────────────────

  sealed trait Tone
  object Tone {
    case object Info extends Tone
    case object Error extends Tone
    case object Warn extends Tone
    case object Ok extends Tone
  }

  /** A form's status line: what is happening, or what went wrong. */
  def showStatus(label: Label, text: String, tone: Tone): Unit = {
    val classes = label.getStyleClass
    classes.removeAll("status-error", "status-warn", "status-ok")
    if (!classes.contains("status")) classes.add("status")
    tone match {
      case Tone.Error => classes.add("status-error")
      case Tone.Warn => classes.add("status-warn")
      case Tone.Ok => classes.add("status-ok")
      case Tone.Info =>
    }
    label.setText(text)
  }

  def showStatus(label: Label, text: String, error: Boolean): Unit =
    showStatus(label, text, if (error) Tone.Error else Tone.Info)

  def lobbyFailureMessage(reason: Byte): String = reason match {
    case LobbyFailure.RATE_LIMITED => Messages.t("Please wait a moment and try again")
    case LobbyFailure.LOBBY_FULL => Messages.t("That lobby is full")
    case LobbyFailure.NOT_JOINABLE => Messages.t("That lobby is no longer open")
    case LobbyFailure.SERVER_FULL => Messages.t("The server has no room for another lobby")
    case LobbyFailure.ALREADY_IN_LOBBY => Messages.t("You are already in a lobby")
    case LobbyFailure.INVALID_NAME => Messages.t("Invalid lobby name")
    case _ => Messages.t("Something went wrong, please try again")
  }

  // ── Lobby settings ─────────────────────────────────────────────────────

  val lobbyDurations = Seq(1, 3, 5, 10, 15, 20)

  def makeDurationCombo(selectedMinutes: Int): ComboBox[String] = {
    val combo = new ComboBox[String](FXCollections.observableArrayList(lobbyDurations.map(d => Messages.t("{0} min", d.toString)): _*))
    combo.getSelectionModel.select(Math.max(0, lobbyDurations.indexOf(selectedMinutes)))
    combo.setMaxWidth(Double.MaxValue)
    combo
  }

  def selectedDuration(combo: ComboBox[String]): Int =
    lobbyDurations(Math.max(0, combo.getSelectionModel.getSelectedIndex))

  // ── Animation ──────────────────────────────────────────────────────────

  /**
   * A looping menu animation stepped at 12 fps, calling `apply` with the loop's phase
   * (0 to 1). Any change on screen makes JavaFX present the whole window, and on macOS a
   * full-screen window doing that every pulse holds a CPU core at ~60% on a 5K display,
   * just for a gently pulsing label. The slow swings these animations make look the same
   * stepped, and they hold still while nobody is using the window (see UiActivity).
   */
  def steppedLoop(periodSec: Double, apply: Double => Unit): AnimationTimer = {
    UiActivity.touch()
    val timer = new AnimationTimer {
      private var last = 0L
      override def handle(now: Long): Unit = {
        if (now - last < 83_000_000L || UiActivity.idle) return
        last = now
        apply((now / 1e9 % periodSec) / periodSec)
      }
    }
    timer.start()
    timer
  }

  /** 0 -> 1 -> 0 as `t` runs 0 -> 1: what a Timeline with auto-reverse traces. */
  def triangle(t: Double): Double = if (t < 0.5) t * 2 else 2 - t * 2
}
