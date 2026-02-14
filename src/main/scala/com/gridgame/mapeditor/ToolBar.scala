package com.gridgame.mapeditor

import javafx.geometry.{Insets, Pos}
import javafx.scene.control.{Label, Slider, ToggleButton, ToggleGroup}
import javafx.scene.layout.VBox

class ToolBar(state: EditorState) extends VBox(8) {
  setPadding(new Insets(10))
  setPrefWidth(120)
  setStyle("-fx-background-color: #2a2a3e;")
  setAlignment(Pos.TOP_CENTER)

  private val toolGroup = new ToggleGroup()

  private val toolLabel = new Label("Tools")
  toolLabel.setStyle("-fx-text-fill: #aaa; -fx-font-weight: bold; -fx-font-size: 12;")

  private val panBtn = createToolButton("Pan", Tool.Pan)
  private val pencilBtn = createToolButton("Pencil", Tool.Pencil)
  private val rectBtn = createToolButton("Rect", Tool.Rect)
  private val circleBtn = createToolButton("Circle", Tool.Circle)
  private val fillBtn = createToolButton("Fill", Tool.Fill)
  private val eraserBtn = createToolButton("Eraser", Tool.Eraser)
  private val spawnBtn = createToolButton("Spawn", Tool.Spawn)

  pencilBtn.setSelected(true)

  private val brushLabel = new Label("Brush: 1")
  brushLabel.setStyle("-fx-text-fill: #ccc; -fx-font-size: 11;")

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
    brushLabel.setText(s"Brush: $size")
  })

  getChildren.addAll(toolLabel, panBtn, pencilBtn, rectBtn, circleBtn, fillBtn, eraserBtn, spawnBtn,
    new Label("") { setMinHeight(10) },
    brushLabel, brushSlider)

  private def createToolButton(name: String, tool: Tool): ToggleButton = {
    val btn = new ToggleButton(name)
    btn.setToggleGroup(toolGroup)
    btn.setPrefWidth(100)
    btn.setStyle("-fx-background-color: #3a3a4e; -fx-text-fill: white; -fx-font-size: 11;")
    btn.setOnAction(_ => state.tool = tool)
    btn
  }
}
