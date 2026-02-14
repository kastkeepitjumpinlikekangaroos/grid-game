package com.gridgame.mapeditor

import com.gridgame.common.model.Tile
import javafx.geometry.{Insets, Pos}
import javafx.scene.control._
import javafx.scene.layout.{GridPane, HBox}

object NewMapDialog {

  case class NewMapResult(name: String, width: Int, height: Int, background: String, fillTile: Tile)

  def show(): Option[NewMapResult] = {
    val dialog = new Dialog[NewMapResult]()
    dialog.setTitle("New Map")
    dialog.setHeaderText("Create a new map")

    dialog.getDialogPane.getButtonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)

    val grid = new GridPane()
    grid.setHgap(12)
    grid.setVgap(12)
    grid.setPadding(new Insets(24))
    grid.setStyle("-fx-background-color: #1e1e32;")

    val labelStyle = "-fx-text-fill: #8899bb; -fx-font-weight: bold; -fx-font-size: 11;"
    val fieldStyle = "-fx-background-color: #2e2e4a; -fx-text-fill: white; -fx-font-size: 13; -fx-padding: 8; -fx-background-radius: 6; -fx-border-color: #3a3a5e; -fx-border-radius: 6; -fx-border-width: 1;"

    val nameField = new TextField("My Map")
    nameField.setPrefWidth(220)
    nameField.setStyle(fieldStyle)

    val widthField = new Spinner[Integer](10, 500, 50)
    widthField.setEditable(true)
    widthField.setStyle("-fx-font-size: 13;")

    val heightField = new Spinner[Integer](10, 500, 50)
    heightField.setEditable(true)
    heightField.setStyle("-fx-font-size: 13;")

    val bgChoices = Seq("sky", "night", "sunset", "void", "neon")
    val bgCombo = new ComboBox[String]()
    bgChoices.foreach(bgCombo.getItems.add)
    bgCombo.setValue("sky")
    bgCombo.setStyle("-fx-font-size: 13;")

    val tileCombo = new ComboBox[String]()
    Tile.all.foreach(t => tileCombo.getItems.add(t.name))
    tileCombo.setValue("grass")
    tileCombo.setStyle("-fx-font-size: 13;")

    val nameLabel = new Label("NAME")
    nameLabel.setStyle(labelStyle)
    val widthLabel = new Label("WIDTH")
    widthLabel.setStyle(labelStyle)
    val heightLabel = new Label("HEIGHT")
    heightLabel.setStyle(labelStyle)
    val bgLabel = new Label("BACKGROUND")
    bgLabel.setStyle(labelStyle)
    val tileLabel = new Label("FILL TILE")
    tileLabel.setStyle(labelStyle)

    grid.add(nameLabel, 0, 0)
    grid.add(nameField, 1, 0)
    grid.add(widthLabel, 0, 1)
    grid.add(widthField, 1, 1)
    grid.add(heightLabel, 0, 2)
    grid.add(heightField, 1, 2)
    grid.add(bgLabel, 0, 3)
    grid.add(bgCombo, 1, 3)
    grid.add(tileLabel, 0, 4)
    grid.add(tileCombo, 1, 4)

    dialog.getDialogPane.setContent(grid)
    dialog.getDialogPane.setStyle("-fx-background-color: #1e1e32;")

    dialog.setResultConverter(btn => {
      if (btn == ButtonType.OK) {
        NewMapResult(
          nameField.getText,
          widthField.getValue,
          heightField.getValue,
          bgCombo.getValue,
          Tile.fromName(tileCombo.getValue)
        )
      } else null
    })

    val result = dialog.showAndWait()
    if (result.isPresent && result.get() != null) Some(result.get()) else None
  }
}
