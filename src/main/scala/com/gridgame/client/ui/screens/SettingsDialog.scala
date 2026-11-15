package com.gridgame.client.ui.screens

import com.gridgame.client.gl.RenderQuality
import com.gridgame.client.i18n.Messages
import com.gridgame.client.ui.Icons

import javafx.geometry.{Insets, Pos}
import javafx.scene.control.ComboBox
import javafx.scene.input.{KeyCode, KeyEvent}
import javafx.scene.layout.{HBox, Region, StackPane, VBox}
import javafx.stage.{Screen, Stage}

import com.gridgame.client.ui.Theme._
import com.gridgame.client.ui.Widgets._

/**
 * The game's settings, from the sliders in the top bar: a card over whatever page is showing, as a
 * problem report's is. The graphics quality (which effects, RenderQuality's tier), the resolution
 * the world is drawn at (its most lines, RenderQuality.resolution) and the camera's zoom (how much
 * closer than the widest view, RenderQuality.zoomPercent), each taken and saved as it is picked, for
 * every match from the next on. The quality and the resolution are the player's to weigh: more
 * detail or more frames; nothing changes them by itself. All three can be changed in a match too,
 * in the menu Esc brings up there (render/MatchMenu).
 */
private[client] object SettingsDialog {
  /** The resolutions offered on a display `nativeLines` tall: its own, and the ones below it. */
  private[client] def resolutionsFor(nativeLines: Int): Seq[Int] = RenderQuality.resolutionsFor(nativeLines)

  /** A zoom as the settings name it: "100% (widest)", "150%". */
  private[client] def zoomLabel(percent: Int): String =
    if (percent <= RenderQuality.MinZoom) Messages.t("{0}% (widest)", percent.toString) else s"$percent%"

  def show(stage: Stage): Unit = {
    val root = stage.getScene match {
      case null => return
      case scene => scene.getRoot match {
        case p: StackPane => p
        case _ => return
      }
    }
    val scrim = new StackPane()
    def close(): Unit = root.getChildren.remove(scrim)

    val closeBtn = iconButton(Icons.Close, Messages.t("Close"))
    closeBtn.setOnAction(_ => close())
    val head = new HBox(12, Icons.node(Icons.Sliders, 22, Palette.Gold), h2(Messages.t("Settings")), grow(), closeBtn)
    head.setAlignment(Pos.CENTER_LEFT)

    // The quality: High, Medium, Low (the tiers in RenderQuality's order)
    val tiers = Seq(RenderQuality.HIGH -> Messages.t("High"), RenderQuality.MEDIUM -> Messages.t("Medium"),
      RenderQuality.LOW -> Messages.t("Low"))
    val quality = new ComboBox[String]()
    tiers.foreach { case (_, name) => quality.getItems.add(name) }
    quality.setValue(tiers.find(_._1 == RenderQuality.tier).map(_._2).orNull)
    quality.setMaxWidth(Double.MaxValue)
    // On the value, not the action: a combo box's action comes from its skin, a value's change always
    quality.valueProperty.addListener((_, _, picked: String) =>
      tiers.find(_._2 == picked).foreach { case (t, _) => RenderQuality.choose(t) })

    // The resolution: the display's own, and each one below it. The match's window is the screen's
    // usable area, at its pixel density
    val screen = Screen.getPrimary
    val nativeW = Math.round(screen.getVisualBounds.getWidth * screen.getOutputScaleX).toInt
    val nativeH = Math.round(screen.getVisualBounds.getHeight * screen.getOutputScaleY).toInt
    val offered = resolutionsFor(nativeH).map { r =>
      r -> (if (r == 0) Messages.t("Native ({0} × {1})", nativeW.toString, nativeH.toString) else s"${r}p")
    }
    val resolution = new ComboBox[String]()
    offered.foreach { case (_, name) => resolution.getItems.add(name) }
    resolution.setValue(offered.find(_._1 == RenderQuality.resolution).orElse(offered.headOption).map(_._2).orNull)
    resolution.setMaxWidth(Double.MaxValue)
    resolution.valueProperty.addListener((_, _, picked: String) =>
      offered.find(_._2 == picked).foreach { case (r, _) => RenderQuality.chooseResolution(r) })

    // The camera's zoom: from the widest view, which every match had before, to twice as close
    val zooms = RenderQuality.Zooms.map(z => z -> zoomLabel(z))
    val zoom = new ComboBox[String]()
    zooms.foreach { case (_, name) => zoom.getItems.add(name) }
    zoom.setValue(zooms.find(_._1 == RenderQuality.zoomPercent).orElse(zooms.headOption).map(_._2).orNull)
    zoom.setMaxWidth(Double.MaxValue)
    zoom.valueProperty.addListener((_, _, picked: String) =>
      zooms.find(_._2 == picked).foreach { case (z, _) => RenderQuality.chooseZoom(z) })

    def setting(label: String, control: Region, about: String): VBox = {
      val note = text(about, "small", "muted")
      note.setWrapText(true)
      new VBox(6, text(label, "field-label"), control, note)
    }
    val body = new VBox(18,
      setting(Messages.t("Graphics quality"), quality,
        Messages.t("High draws every effect. Medium and Low leave some out, and draw the world with fewer pixels, for a slower computer. F7 changes it in a match.")),
      setting(Messages.t("Resolution"), resolution,
        Messages.t("How many lines the world is drawn at. Fewer are quicker to draw, which keeps a match smooth on a large screen; text and the HUD stay sharp.")),
      setting(Messages.t("Camera zoom"), zoom,
        Messages.t("How close the camera is in a match. Closer shows the fight bigger, but less of the map around you. Esc in a match changes it too.")))

    val done = button(Messages.t("Done"), "sun")
    done.setOnAction(_ => close())
    val foot = new HBox(grow(), done)
    foot.setAlignment(Pos.CENTER_RIGHT)

    val dialog = card(head, body, foot)
    dialog.getStyleClass.add("dialog")
    dialog.setMaxWidth(480)
    dialog.setMaxHeight(Region.USE_PREF_SIZE)
    scrim.getStyleClass.add("dialog-scrim")
    StackPane.setAlignment(dialog, Pos.CENTER)
    scrim.getChildren.add(dialog)
    scrim.setPadding(new Insets(24))
    // Clicks on the shade go nowhere: the page under it waits; Escape closes it
    scrim.setOnMouseClicked(e => e.consume())
    scrim.addEventFilter(KeyEvent.KEY_PRESSED, (e: KeyEvent) => if (e.getCode == KeyCode.ESCAPE) { close(); e.consume() })
    root.getChildren.add(scrim)
    bringIn(dialog)
  }
}
