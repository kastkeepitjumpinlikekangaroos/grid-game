package com.gridgame.mapeditor

import javafx.geometry.{Insets, Pos}
import javafx.scene.control.{Label, ListView}
import javafx.scene.layout.VBox

class PropertiesPanel(state: EditorState) extends VBox(6) {
  setPadding(new Insets(12))
  setPrefWidth(170)
  setStyle("-fx-background-color: #1e1e32; -fx-border-color: #2a2a45; -fx-border-width: 0 0 0 1;")

  private val propsLabel = new Label("PROPERTIES")
  propsLabel.setStyle("-fx-text-fill: #6678; -fx-font-weight: bold; -fx-font-size: 10; -fx-padding: 0 0 4 0;")

  private val nameTitle = new Label("Name")
  nameTitle.setStyle("-fx-text-fill: #6678; -fx-font-size: 10;")
  private val nameValue = new Label("")
  nameValue.setStyle("-fx-text-fill: #dde; -fx-font-size: 13; -fx-font-weight: bold;")
  nameValue.setWrapText(true)

  private val bgTitle = new Label("Background")
  bgTitle.setStyle("-fx-text-fill: #6678; -fx-font-size: 10;")
  private val bgValue = new Label("")
  bgValue.setStyle("-fx-text-fill: #bbc; -fx-font-size: 12;")

  private val sizeTitle = new Label("Dimensions")
  sizeTitle.setStyle("-fx-text-fill: #6678; -fx-font-size: 10;")
  private val sizeValue = new Label("")
  sizeValue.setStyle("-fx-text-fill: #bbc; -fx-font-size: 12;")

  // Separator
  private val sep = new javafx.scene.layout.Region()
  sep.setMinHeight(1)
  sep.setMaxHeight(1)
  sep.setStyle("-fx-background-color: #2a2a45;")
  VBox.setMargin(sep, new Insets(4, 0, 4, 0))

  private val spawnLabel = new Label("SPAWN POINTS")
  spawnLabel.setStyle("-fx-text-fill: #6678; -fx-font-weight: bold; -fx-font-size: 10;")

  private val spawnList = new ListView[String]()
  spawnList.setPrefHeight(200)
  spawnList.setStyle("-fx-background-color: #242440; -fx-control-inner-background: #242440; -fx-background-radius: 6; -fx-border-color: #2a2a45; -fx-border-radius: 6; -fx-border-width: 1;")
  VBox.setVgrow(spawnList, javafx.scene.layout.Priority.ALWAYS)

  getChildren.addAll(propsLabel,
    nameTitle, nameValue,
    bgTitle, bgValue,
    sizeTitle, sizeValue,
    sep,
    spawnLabel, spawnList)

  def update(): Unit = {
    nameValue.setText(state.world.name)
    bgValue.setText(state.world.background)
    sizeValue.setText(s"${state.mapWidth} x ${state.mapHeight}")

    spawnList.getItems.clear()
    state.spawnPoints.foreach { case (x, y) =>
      spawnList.getItems.add(s"($x, $y)")
    }
  }
}
