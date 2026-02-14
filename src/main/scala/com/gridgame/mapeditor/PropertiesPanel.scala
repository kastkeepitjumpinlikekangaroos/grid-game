package com.gridgame.mapeditor

import javafx.geometry.{Insets, Pos}
import javafx.scene.control.{Label, ListView}
import javafx.scene.layout.VBox

class PropertiesPanel(state: EditorState) extends VBox(8) {
  setPadding(new Insets(10))
  setPrefWidth(160)
  setStyle("-fx-background-color: #2a2a3e;")

  private val propsLabel = new Label("Properties")
  propsLabel.setStyle("-fx-text-fill: #aaa; -fx-font-weight: bold; -fx-font-size: 12;")

  private val nameLabel = new Label("Name: ")
  nameLabel.setStyle("-fx-text-fill: #ccc; -fx-font-size: 11;")

  private val bgLabel = new Label("BG: ")
  bgLabel.setStyle("-fx-text-fill: #ccc; -fx-font-size: 11;")

  private val sizeLabel = new Label("Size: ")
  sizeLabel.setStyle("-fx-text-fill: #ccc; -fx-font-size: 11;")

  private val spawnLabel = new Label("Spawn Points")
  spawnLabel.setStyle("-fx-text-fill: #aaa; -fx-font-weight: bold; -fx-font-size: 12;")

  private val spawnList = new ListView[String]()
  spawnList.setPrefHeight(200)
  spawnList.setStyle("-fx-background-color: #3a3a4e; -fx-text-fill: white; -fx-control-inner-background: #3a3a4e;")

  getChildren.addAll(propsLabel, nameLabel, bgLabel, sizeLabel,
    new Label("") { setMinHeight(10) },
    spawnLabel, spawnList)

  def update(): Unit = {
    nameLabel.setText(s"Name: ${state.world.name}")
    bgLabel.setText(s"BG: ${state.world.background}")
    sizeLabel.setText(s"Size: ${state.mapWidth}x${state.mapHeight}")

    spawnList.getItems.clear()
    state.spawnPoints.foreach { case (x, y) =>
      spawnList.getItems.add(s"($x, $y)")
    }
  }
}
