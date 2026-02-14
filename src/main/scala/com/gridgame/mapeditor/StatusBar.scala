package com.gridgame.mapeditor

import javafx.geometry.Insets
import javafx.scene.control.Label
import javafx.scene.layout.HBox

class StatusBar(state: EditorState) extends HBox(20) {
  setPadding(new Insets(4, 10, 4, 10))
  setStyle("-fx-background-color: #222233;")

  private val cursorLabel = new Label("Cursor: (-, -)")
  cursorLabel.setStyle("-fx-text-fill: #ccc; -fx-font-size: 11;")

  private val tileLabel = new Label("Tile: -")
  tileLabel.setStyle("-fx-text-fill: #ccc; -fx-font-size: 11;")

  private val mapSizeLabel = new Label("Map: -")
  mapSizeLabel.setStyle("-fx-text-fill: #ccc; -fx-font-size: 11;")

  private val toolLabel = new Label("Tool: Pencil")
  toolLabel.setStyle("-fx-text-fill: #ccc; -fx-font-size: 11;")

  private val zoomLabel = new Label("Zoom: 100%")
  zoomLabel.setStyle("-fx-text-fill: #ccc; -fx-font-size: 11;")

  getChildren.addAll(cursorLabel, tileLabel, mapSizeLabel, toolLabel, zoomLabel)

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
    zoomLabel.setText(f"Zoom: ${state.zoom * 100}%.0f%%")
  }
}
