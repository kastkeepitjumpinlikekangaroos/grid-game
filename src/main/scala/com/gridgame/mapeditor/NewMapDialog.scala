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
    grid.setHgap(10)
    grid.setVgap(10)
    grid.setPadding(new Insets(20))

    val nameField = new TextField("My Map")
    nameField.setPrefWidth(200)

    val widthField = new Spinner[Integer](10, 500, 50)
    widthField.setEditable(true)

    val heightField = new Spinner[Integer](10, 500, 50)
    heightField.setEditable(true)

    val bgChoices = Seq("sky", "night", "sunset", "void", "neon")
    val bgCombo = new ComboBox[String]()
    bgChoices.foreach(bgCombo.getItems.add)
    bgCombo.setValue("sky")

    val tileCombo = new ComboBox[String]()
    Tile.all.foreach(t => tileCombo.getItems.add(t.name))
    tileCombo.setValue("grass")

    grid.add(new Label("Name:"), 0, 0)
    grid.add(nameField, 1, 0)
    grid.add(new Label("Width:"), 0, 1)
    grid.add(widthField, 1, 1)
    grid.add(new Label("Height:"), 0, 2)
    grid.add(heightField, 1, 2)
    grid.add(new Label("Background:"), 0, 3)
    grid.add(bgCombo, 1, 3)
    grid.add(new Label("Fill Tile:"), 0, 4)
    grid.add(tileCombo, 1, 4)

    dialog.getDialogPane.setContent(grid)

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
