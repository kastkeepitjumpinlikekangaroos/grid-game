package com.gridgame.mapeditor

import javafx.geometry.{Insets, Pos}
import javafx.scene.control.Label
import javafx.scene.layout.HBox

class StatusBar(state: EditorState) extends HBox(0) {
  setPadding(new Insets(0))
  setStyle("-fx-background-color: #16162a; -fx-border-color: #2a2a45; -fx-border-width: 1 0 0 0;")
  setAlignment(Pos.CENTER_LEFT)

  private val itemStyle = "-fx-text-fill: #99a; -fx-font-size: 11; -fx-padding: 5 12;"
  private val sepStyle = "-fx-background-color: #2a2a45; -fx-min-width: 1; -fx-max-width: 1;"

  private val cursorLabel = new Label("Cursor: (-, -)")
  cursorLabel.setStyle(itemStyle)

  private val tileLabel = new Label("Tile: -")
  tileLabel.setStyle(itemStyle)

  private val mapSizeLabel = new Label("Map: -")
  mapSizeLabel.setStyle(itemStyle)

  private val toolLabel = new Label("Tool: Pencil")
  toolLabel.setStyle(itemStyle + "-fx-text-fill: #4a9eff;")

  private val spacer = new javafx.scene.layout.Region()
  HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS)

  private val zoomLabel = new Label("Zoom: 100%")
  zoomLabel.setStyle(itemStyle)

  private def sep(): javafx.scene.layout.Region = {
    val s = new javafx.scene.layout.Region()
    s.setStyle(sepStyle)
    s.setMinHeight(20)
    s
  }

  getChildren.addAll(cursorLabel, sep(), tileLabel, sep(), mapSizeLabel, sep(), toolLabel, spacer, zoomLabel)

  def update(): Unit = {
    val wx = state.cursorWorldX
    val wy = state.cursorWorldY

    cursorLabel.setText(s"Cursor: ($wx, $wy)")

    val tileName = state.getTile(wx, wy).map(_.name).getOrElse("-")
    tileLabel.setText(s"Tile: $tileName")

    mapSizeLabel.setText(s"Map: ${state.mapWidth}x${state.mapHeight}")

    val toolName = state.tool match {
      case Tool.Pan => "Pan"
      case Tool.Pencil => "Pencil"
      case Tool.Rect => "Rect"
      case Tool.Circle => "Circle"
      case Tool.Fill => "Fill"
      case Tool.Eraser => "Eraser"
      case Tool.Spawn => "Spawn"
    }
    toolLabel.setText(s"Tool: $toolName")
    zoomLabel.setText(f"${state.zoom * 100}%.0f%%")
  }
}
