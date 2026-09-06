package com.gridgame.mapeditor

import javafx.animation.AnimationTimer
import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.scene.layout.{BorderPane, VBox}
import javafx.stage.{Screen, Stage}

class MapEditorApp extends Application {

  override def start(primaryStage: Stage): Unit = {
    loadAppIcons(primaryStage)
    setDockIcon()

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

  /** Sets the title bar / taskbar icon (Windows/Linux; macOS uses the dock icon instead, see setDockIcon). */
  private def loadAppIcons(stage: Stage): Unit = {
    val images = Seq("sprites/icon_wizard_256.png", "sprites/icon_wizard_128.png").flatMap { path =>
      val stream = resolveIconStream(path)
      if (stream == null) None else Some(new Image(stream))
    }
    stage.getIcons.addAll(images: _*)
  }

  /** Sets the macOS dock icon via the AWT Taskbar API (no-op on platforms without a taskbar/dock). */
  private def setDockIcon(): Unit = {
    try {
      if (java.awt.Taskbar.isTaskbarSupported) {
        val taskbar = java.awt.Taskbar.getTaskbar
        if (taskbar.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) {
          val stream = resolveIconStream("sprites/icon_wizard_256.png")
          if (stream != null) {
            val img = try javax.imageio.ImageIO.read(stream) finally stream.close()
            if (img != null) taskbar.setIconImage(img)
          }
        }
      }
    } catch {
      case _: Throwable => // Taskbar API unavailable on this platform; ignore
    }
  }

  private def resolveIconStream(relativePath: String): java.io.InputStream = {
    val direct = new java.io.File(relativePath)
    if (direct.exists()) return new java.io.FileInputStream(direct)

    val buildWorkDir = System.getenv("BUILD_WORKING_DIRECTORY")
    if (buildWorkDir != null) {
      val fromWorkDir = new java.io.File(buildWorkDir, relativePath)
      if (fromWorkDir.exists()) return new java.io.FileInputStream(fromWorkDir)
    }

    getClass.getClassLoader.getResourceAsStream(relativePath)
  }
}
