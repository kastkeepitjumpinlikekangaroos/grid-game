package com.gridgame.mapeditor

import com.gridgame.common.Constants
import com.gridgame.common.model.Tile
import javafx.geometry.{Insets, Pos}
import javafx.scene.canvas.Canvas
import javafx.scene.control.{Label, ScrollPane, ToggleButton, ToggleGroup, Tooltip}
import javafx.scene.layout.{FlowPane, VBox}
import javafx.scene.paint.Color

class TilePalette(state: EditorState) extends VBox(6) {
  setPadding(new Insets(12, 10, 10, 10))
  setStyle("-fx-background-color: #1e1e32;")

  // Separator line at top
  private val sep = new javafx.scene.layout.Region()
  sep.setMinHeight(1)
  sep.setMaxHeight(1)
  sep.setStyle("-fx-background-color: #2a2a45;")

  private val label = new Label("TILES")
  label.setStyle("-fx-text-fill: #6678; -fx-font-weight: bold; -fx-font-size: 10;")

  private val tileGroup = new ToggleGroup()
  private val flow = new FlowPane(4, 4)
  flow.setPrefWidth(106)
  flow.setAlignment(Pos.TOP_LEFT)

  private var firstButton: ToggleButton = _

  private val normalTileStyle = "-fx-background-color: #282842; -fx-padding: 2; -fx-background-radius: 4; -fx-border-color: #3a3a55; -fx-border-radius: 4; -fx-border-width: 1; -fx-cursor: hand;"
  private val hoverTileStyle = "-fx-background-color: #323252; -fx-padding: 2; -fx-background-radius: 4; -fx-border-color: #5a5a75; -fx-border-radius: 4; -fx-border-width: 1; -fx-cursor: hand;"
  private val selectedTileStyle = "-fx-background-color: #3a4a6e; -fx-padding: 2; -fx-background-radius: 4; -fx-border-color: #4a9eff; -fx-border-radius: 4; -fx-border-width: 2; -fx-cursor: hand;"

  Tile.all.foreach { tile =>
    val btn = createTileButton(tile)
    flow.getChildren.add(btn)
    if (tile == Tile.Grass && firstButton == null) firstButton = btn
  }

  if (firstButton != null) firstButton.setSelected(true)

  private val scrollPane = new ScrollPane(flow)
  scrollPane.setFitToWidth(true)
  scrollPane.setStyle("-fx-background: #1e1e32; -fx-background-color: #1e1e32;")

  getChildren.addAll(sep, label, scrollPane)

  private def createTileButton(tile: Tile): ToggleButton = {
    val previewSize = 36.0
    val preview = new Canvas(previewSize, previewSize)
    val gc = preview.getGraphicsContext2D
    gc.setImageSmoothing(false)

    val img = EditorTileRenderer.getTileImage(tile.id)
    gc.drawImage(img, 0, 0, previewSize, previewSize)

    val btn = new ToggleButton()
    btn.setGraphic(preview)
    btn.setToggleGroup(tileGroup)
    btn.setTooltip(new Tooltip(tile.name))
    btn.setPrefSize(44, 44)
    btn.setStyle(normalTileStyle)
    btn.setOnAction(_ => state.selectedTile = tile)
    btn.selectedProperty().addListener((_, _, selected) => {
      btn.setStyle(if (selected) selectedTileStyle else normalTileStyle)
    })
    btn.setOnMouseEntered(_ => if (!btn.isSelected) btn.setStyle(hoverTileStyle))
    btn.setOnMouseExited(_ => if (!btn.isSelected) btn.setStyle(normalTileStyle))
    btn
  }
}
