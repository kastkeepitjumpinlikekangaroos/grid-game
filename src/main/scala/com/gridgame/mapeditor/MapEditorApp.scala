package com.gridgame.mapeditor

import javafx.animation.AnimationTimer
import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.layout.{BorderPane, VBox}
import javafx.stage.{Screen, Stage}

class MapEditorApp extends Application {

  override def start(primaryStage: Stage): Unit = {
    primaryStage.setTitle("Map Editor - Untitled")

    val state = new EditorState()
    val undoManager = new UndoManager()
    val statusBar = new StatusBar(state)
    val canvas = new EditorCanvas(state, undoManager, statusBar)
    val propertiesPanel = new PropertiesPanel(state)
    val menuBarBuilder = new MenuBarBuilder(state, undoManager, canvas, propertiesPanel, statusBar, primaryStage)

    val toolBar = new ToolBar(state)
    val tilePalette = new TilePalette(state)

    // Left side: tools + tile palette
    val leftPanel = new VBox()
    leftPanel.getChildren.addAll(toolBar, tilePalette)
    leftPanel.setStyle("-fx-background-color: #1e1e32;")
    VBox.setVgrow(tilePalette, javafx.scene.layout.Priority.ALWAYS)

    val root = new BorderPane()
    root.setTop(menuBarBuilder.build())
    root.setLeft(leftPanel)
    root.setCenter(canvas)
    root.setRight(propertiesPanel)
    root.setBottom(statusBar)

    // Bind canvas size to center region
    canvas.widthProperty().bind(root.widthProperty()
      .subtract(leftPanel.widthProperty())
      .subtract(propertiesPanel.widthProperty()))
    canvas.heightProperty().bind(root.heightProperty()
      .subtract(statusBar.heightProperty())
      .subtract(30)) // approx menu bar height

    val scene = new Scene(root)

    primaryStage.setScene(scene)
    val bounds = Screen.getPrimary.getVisualBounds
    primaryStage.setX(bounds.getMinX)
    primaryStage.setY(bounds.getMinY)
    primaryStage.setWidth(bounds.getWidth)
    primaryStage.setHeight(bounds.getHeight)
    primaryStage.show()

    // Initial UI state
    propertiesPanel.update()
    statusBar.update()

    // Render loop at ~30fps
    val frameIntervalNs = 1_000_000_000L / 30
    var lastFrameTime = 0L
    val renderLoop = new AnimationTimer() {
      override def handle(now: Long): Unit = {
        if (now - lastFrameTime >= frameIntervalNs) {
          lastFrameTime = now
          canvas.render()
          statusBar.update()
        }
      }
    }
    renderLoop.start()

    primaryStage.setOnCloseRequest(_ => {
      renderLoop.stop()
    })
  }
}
