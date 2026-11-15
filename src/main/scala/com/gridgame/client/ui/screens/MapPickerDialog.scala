package com.gridgame.client.ui.screens

import com.gridgame.client.i18n.Messages
import com.gridgame.client.ui.{Icons, MapCarousel}

import javafx.geometry.{Insets, Pos}
import javafx.scene.input.{KeyCode, KeyEvent}
import javafx.scene.layout.{HBox, Region, StackPane}
import javafx.stage.Stage

import com.gridgame.client.ui.Theme._
import com.gridgame.client.ui.Widgets._

/**
 * The maps for a lobby's host to pick from, in a carousel (MapCarousel) on a card over the lobby
 * room, as the settings' card is over a page. It opens on the lobby's map; browsing it changes
 * nothing for the lobby until the host settles on one — "Use this map", a double click on a map in
 * the strip, or Enter — which closes the card and hands `chosen` the map, if it isn't the one the
 * lobby has. Cancel, the cross or Escape close it as it was. What `show` returns closes it from
 * outside (the room going), giving up on its pictures not yet drawn.
 */
private[client] object MapPickerDialog {
  def show(stage: Stage, current: Int, chosen: Int => Unit): () => Unit = {
    val root = stage.getScene match {
      case null => return () => ()
      case scene => scene.getRoot match {
        case p: StackPane => p
        case _ => return () => ()
      }
    }
    val scrim = new StackPane()
    val maps = new MapCarousel(current, MapCarousel.Wide)
    def close(): Unit = {
      root.getChildren.remove(scrim)
      maps.close()
    }
    def settle(map: Int): Unit = {
      close()
      if (map != current) chosen(map)
    }
    maps.onChoose = settle

    val closeBtn = iconButton(Icons.Close, Messages.t("Close"))
    closeBtn.setOnAction(_ => close())
    val head = new HBox(12, Icons.node(Icons.MapPin, 22, Palette.Gold), h2(Messages.t("Choose a map")), grow(), closeBtn)
    head.setAlignment(Pos.CENTER_LEFT)

    val cancel = button(Messages.t("Cancel"), "plain")
    cancel.setOnAction(_ => close())
    val use = button(Messages.t("Use this map"), "sun")
    use.setOnAction(_ => settle(maps.picked))
    val foot = new HBox(10, grow(), cancel, use)
    foot.setAlignment(Pos.CENTER_RIGHT)

    val dialog = card(head, maps.node, foot)
    dialog.getStyleClass.add("dialog")
    dialog.setMaxWidth(Region.USE_PREF_SIZE)
    dialog.setMaxHeight(Region.USE_PREF_SIZE)
    scrim.getStyleClass.add("dialog-scrim")
    StackPane.setAlignment(dialog, Pos.CENTER)
    scrim.getChildren.add(dialog)
    scrim.setPadding(new Insets(24))
    // Clicks on the shade go nowhere: the room under it waits; Escape closes it
    scrim.setOnMouseClicked(e => e.consume())
    scrim.addEventFilter(KeyEvent.KEY_PRESSED, (e: KeyEvent) => if (e.getCode == KeyCode.ESCAPE) { close(); e.consume() })
    root.getChildren.add(scrim)
    bringIn(dialog)
    // ← and → step through the maps at once
    maps.focus()
    () => close()
  }
}
