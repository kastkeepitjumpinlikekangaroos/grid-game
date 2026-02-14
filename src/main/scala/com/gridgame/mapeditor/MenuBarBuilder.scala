package com.gridgame.mapeditor

import com.gridgame.common.world.WorldLoader
import javafx.scene.control.{Menu, MenuBar, MenuItem, SeparatorMenuItem, CheckMenuItem}
import javafx.scene.input.{KeyCode, KeyCodeCombination, KeyCombination}
import javafx.stage.{FileChooser, Stage}

class MenuBarBuilder(
  state: EditorState,
  undoManager: UndoManager,
  canvas: EditorCanvas,
  propertiesPanel: PropertiesPanel,
  statusBar: StatusBar,
  stage: Stage
) {

  def build(): MenuBar = {
    val menuBar = new MenuBar()
    menuBar.getMenus.addAll(fileMenu(), editMenu(), viewMenu())
    menuBar
  }

  private def fileMenu(): Menu = {
    val menu = new Menu("File")

    val newItem = new MenuItem("New")
    newItem.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN))
    newItem.setOnAction(_ => newMap())

    val openItem = new MenuItem("Open...")
    openItem.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN))
    openItem.setOnAction(_ => openMap())

    val saveItem = new MenuItem("Save")
    saveItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN))
    saveItem.setOnAction(_ => saveMap())

    val saveAsItem = new MenuItem("Save As...")
    saveAsItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN))
    saveAsItem.setOnAction(_ => saveMapAs())

    menu.getItems.addAll(newItem, openItem, new SeparatorMenuItem(), saveItem, saveAsItem)
    menu
  }

  private def editMenu(): Menu = {
    val menu = new Menu("Edit")

    val undoItem = new MenuItem("Undo")
    undoItem.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN))
    undoItem.setOnAction(_ => {
      undoManager.undo(state)
      propertiesPanel.update()
    })

    val redoItem = new MenuItem("Redo")
    redoItem.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN))
    redoItem.setOnAction(_ => {
      undoManager.redo(state)
      propertiesPanel.update()
    })

    menu.getItems.addAll(undoItem, redoItem)
    menu
  }

  private def viewMenu(): Menu = {
    val menu = new Menu("View")

    val gridItem = new CheckMenuItem("Show Grid")
    gridItem.setSelected(state.showGrid)
    gridItem.setAccelerator(new KeyCodeCombination(KeyCode.G, KeyCombination.SHORTCUT_DOWN))
    gridItem.setOnAction(_ => state.showGrid = gridItem.isSelected)

    val spawnItem = new CheckMenuItem("Show Spawn Points")
    spawnItem.setSelected(state.showSpawnPoints)
    spawnItem.setOnAction(_ => state.showSpawnPoints = spawnItem.isSelected)

    val centerItem = new MenuItem("Center on Map")
    centerItem.setAccelerator(new KeyCodeCombination(KeyCode.HOME))
    centerItem.setOnAction(_ => canvas.centerOnMap())

    menu.getItems.addAll(gridItem, spawnItem, new SeparatorMenuItem(), centerItem)
    menu
  }

  private def newMap(): Unit = {
    NewMapDialog.show().foreach { result =>
      undoManager.clear()
      state.createNewWorld(result.name, result.width, result.height, result.background, result.fillTile)
      propertiesPanel.update()
      canvas.centerOnMap()
      updateTitle()
    }
  }

  private def openMap(): Unit = {
    val fc = new FileChooser()
    fc.setTitle("Open World File")
    fc.getExtensionFilters.add(new FileChooser.ExtensionFilter("JSON files", "*.json"))

    // Default to worlds/ directory
    val worldsDir = new java.io.File("worlds")
    val buildWorkDir = System.getenv("BUILD_WORKING_DIRECTORY")
    if (buildWorkDir != null) {
      val dir = new java.io.File(buildWorkDir, "worlds")
      if (dir.exists()) fc.setInitialDirectory(dir)
    } else if (worldsDir.exists()) {
      fc.setInitialDirectory(worldsDir)
    }

    val file = fc.showOpenDialog(stage)
    if (file != null) {
      try {
        val worldData = WorldLoader.loadFromFile(file.getAbsolutePath)
        undoManager.clear()
        state.loadWorld(worldData, file.getAbsolutePath)
        propertiesPanel.update()
        canvas.centerOnMap()
        updateTitle()
      } catch {
        case e: Exception =>
          val alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR)
          alert.setTitle("Error")
          alert.setHeaderText("Failed to load world")
          alert.setContentText(e.getMessage)
          alert.showAndWait()
      }
    }
  }

  private def saveMap(): Unit = {
    state.currentFile match {
      case Some(path) =>
        WorldSaver.save(state, path)
        updateTitle()
      case None =>
        saveMapAs()
    }
  }

  private def saveMapAs(): Unit = {
    val fc = new FileChooser()
    fc.setTitle("Save World File")
    fc.getExtensionFilters.add(new FileChooser.ExtensionFilter("JSON files", "*.json"))

    val buildWorkDir = System.getenv("BUILD_WORKING_DIRECTORY")
    if (buildWorkDir != null) {
      val dir = new java.io.File(buildWorkDir, "worlds")
      if (dir.exists()) fc.setInitialDirectory(dir)
    } else {
      val dir = new java.io.File("worlds")
      if (dir.exists()) fc.setInitialDirectory(dir)
    }

    val file = fc.showSaveDialog(stage)
    if (file != null) {
      var path = file.getAbsolutePath
      if (!path.endsWith(".json")) path += ".json"
      WorldSaver.save(state, path)
      updateTitle()
    }
  }

  def updateTitle(): Unit = {
    val dirtyMark = if (state.dirty) " *" else ""
    val fileName = state.currentFile.map(f => new java.io.File(f).getName).getOrElse("Untitled")
    stage.setTitle(s"Map Editor - $fileName$dirtyMark")
  }
}
