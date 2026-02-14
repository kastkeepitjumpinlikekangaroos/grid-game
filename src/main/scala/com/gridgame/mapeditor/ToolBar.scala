package com.gridgame.mapeditor

import javafx.geometry.{Insets, Pos}
import javafx.scene.control.{Label, Slider, ToggleButton, ToggleGroup}
import javafx.scene.layout.VBox

class ToolBar(state: EditorState) extends VBox(6) {
  setPadding(new Insets(12))
  setPrefWidth(130)
  setStyle("-fx-background-color: #1e1e32; -fx-border-color: #2a2a45; -fx-border-width: 0 1 0 0;")
  setAlignment(Pos.TOP_CENTER)

  private val toolGroup = new ToggleGroup()

  private val toolLabel = new Label("TOOLS")
  toolLabel.setStyle("-fx-text-fill: #6678; -fx-font-weight: bold; -fx-font-size: 10; -fx-padding: 0 0 4 0;")

  private val panBtn = createToolButton("Pan", Tool.Pan)
  private val pencilBtn = createToolButton("Pencil", Tool.Pencil)
  private val rectBtn = createToolButton("Rect", Tool.Rect)
  private val circleBtn = createToolButton("Circle", Tool.Circle)
  private val fillBtn = createToolButton("Fill", Tool.Fill)
  private val eraserBtn = createToolButton("Eraser", Tool.Eraser)
  private val spawnBtn = createToolButton("Spawn", Tool.Spawn)

  pencilBtn.setSelected(true)

  // Separator
  private val sep = new javafx.scene.layout.Region()
  sep.setMinHeight(1)
  sep.setMaxHeight(1)
  sep.setStyle("-fx-background-color: #2a2a45;")
  VBox.setMargin(sep, new Insets(4, 0, 4, 0))

  private val brushLabel = new Label("BRUSH SIZE")
  brushLabel.setStyle("-fx-text-fill: #6678; -fx-font-weight: bold; -fx-font-size: 10;")

  private val brushValue = new Label("1")
  brushValue.setStyle("-fx-text-fill: #4a9eff; -fx-font-weight: bold; -fx-font-size: 16;")

  private val brushSlider = new Slider(1, 10, 1)
  brushSlider.setBlockIncrement(1)
  brushSlider.setMajorTickUnit(1)
  brushSlider.setMinorTickCount(0)
  brushSlider.setSnapToTicks(true)
  brushSlider.setShowTickMarks(true)
  brushSlider.setMaxWidth(100)
  brushSlider.valueProperty().addListener((_, _, newVal) => {
    val size = newVal.intValue()
    state.brushSize = size
    brushValue.setText(s"$size")
  })

  getChildren.addAll(toolLabel, panBtn, pencilBtn, rectBtn, circleBtn, fillBtn, eraserBtn, spawnBtn,
    sep, brushLabel, brushValue, brushSlider)

  private val normalBtnStyle = "-fx-background-color: #282842; -fx-text-fill: #bbc; -fx-font-size: 11; -fx-background-radius: 6; -fx-border-color: #3a3a55; -fx-border-radius: 6; -fx-border-width: 1; -fx-cursor: hand;"
  private val hoverBtnStyle = "-fx-background-color: #323252; -fx-text-fill: white; -fx-font-size: 11; -fx-background-radius: 6; -fx-border-color: #4a4a65; -fx-border-radius: 6; -fx-border-width: 1; -fx-cursor: hand;"
  private val selectedBtnStyle = "-fx-background-color: #3a4a6e; -fx-text-fill: white; -fx-font-size: 11; -fx-font-weight: bold; -fx-background-radius: 6; -fx-border-color: #4a9eff; -fx-border-radius: 6; -fx-border-width: 1.5; -fx-cursor: hand;"

  private def createToolButton(name: String, tool: Tool): ToggleButton = {
    val btn = new ToggleButton(name)
    btn.setToggleGroup(toolGroup)
    btn.setPrefWidth(106)
    btn.setStyle(normalBtnStyle)
    btn.setOnAction(_ => state.tool = tool)
    btn.selectedProperty().addListener((_, _, selected) => {
      btn.setStyle(if (selected) selectedBtnStyle else normalBtnStyle)
    })
    btn.setOnMouseEntered(_ => if (!btn.isSelected) btn.setStyle(hoverBtnStyle))
    btn.setOnMouseExited(_ => if (!btn.isSelected) btn.setStyle(normalBtnStyle))
    btn
  }
}
