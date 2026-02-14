package com.gridgame.mapeditor

import com.gridgame.common.Constants
import com.gridgame.common.model.Tile
import javafx.geometry.{Insets, Pos}
import javafx.scene.canvas.Canvas
import javafx.scene.control.{Label, ScrollPane, ToggleButton, ToggleGroup, Tooltip}
import javafx.scene.layout.{FlowPane, VBox}
import javafx.scene.paint.Color

class TilePalette(state: EditorState) extends VBox(4) {
  setPadding(new Insets(10))
  setStyle("-fx-background-color: #2a2a3e;")

  private val label = new Label("Tiles")
  label.setStyle("-fx-text-fill: #aaa; -fx-font-weight: bold; -fx-font-size: 12;")

  private val tileGroup = new ToggleGroup()
  private val flow = new FlowPane(4, 4)
  flow.setPrefWidth(100)
  flow.setAlignment(Pos.TOP_LEFT)

  private var firstButton: ToggleButton = _

  Tile.all.foreach { tile =>
    val btn = createTileButton(tile)
    flow.getChildren.add(btn)
    if (tile == Tile.Grass && firstButton == null) firstButton = btn
  }

  if (firstButton != null) firstButton.setSelected(true)

  private val scrollPane = new ScrollPane(flow)
  scrollPane.setFitToWidth(true)
  scrollPane.setStyle("-fx-background: #2a2a3e; -fx-background-color: #2a2a3e;")

  getChildren.addAll(label, scrollPane)

  private def createTileButton(tile: Tile): ToggleButton = {
    // Create a small canvas with the tile sprite
    val previewSize = 36.0
    val preview = new Canvas(previewSize, previewSize)
    val gc = preview.getGraphicsContext2D
    gc.setImageSmoothing(false)

    val img = EditorTileRenderer.getTileImage(tile.id)
    // Scale the tile image to fit the button
    gc.drawImage(img, 0, 0, previewSize, previewSize)

    val btn = new ToggleButton()
    btn.setGraphic(preview)
    btn.setToggleGroup(tileGroup)
    btn.setTooltip(new Tooltip(tile.name))
    btn.setPrefSize(42, 42)
    btn.setStyle("-fx-background-color: #3a3a4e; -fx-padding: 2;")
    btn.setOnAction(_ => state.selectedTile = tile)
    btn
  }
}
