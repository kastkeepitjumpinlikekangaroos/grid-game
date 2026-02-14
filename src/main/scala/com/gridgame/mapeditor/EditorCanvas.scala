package com.gridgame.mapeditor

import com.gridgame.common.Constants
import javafx.scene.canvas.{Canvas, GraphicsContext}
import javafx.scene.paint.Color
import javafx.scene.input.{MouseButton, MouseEvent, ScrollEvent}

class EditorCanvas(state: EditorState, undoManager: UndoManager, statusBar: StatusBar) extends Canvas() {
  private val gc: GraphicsContext = getGraphicsContext2D
  gc.setImageSmoothing(false)

  private val HW = Constants.ISO_HALF_W  // 20
  private val HH = Constants.ISO_HALF_H  // 10
  private val cellH = Constants.TILE_CELL_HEIGHT // 56

  // Pan state
  private var panStartX: Double = 0
  private var panStartY: Double = 0
  private var panCamStartX: Double = 0
  private var panCamStartY: Double = 0
  private var isPanning: Boolean = false

  // Preview state for rect/circle tools
  private var previewEndX: Int = -1
  private var previewEndY: Int = -1

  setupMouseHandlers()
  setupScrollHandler()

  private def worldToScreenX(wx: Double, wy: Double): Double =
    (wx - wy) * HW * state.zoom + getWidth / 2.0 + state.cameraX

  private def worldToScreenY(wx: Double, wy: Double): Double =
    (wx + wy) * HH * state.zoom + getHeight / 2.0 + state.cameraY

  private def screenToWorldX(sx: Double, sy: Double): Double = {
    val rx = (sx - getWidth / 2.0 - state.cameraX) / state.zoom
    val ry = (sy - getHeight / 2.0 - state.cameraY) / state.zoom
    (rx / HW + ry / HH) / 2.0
  }

  private def screenToWorldY(sx: Double, sy: Double): Double = {
    val rx = (sx - getWidth / 2.0 - state.cameraX) / state.zoom
    val ry = (sy - getHeight / 2.0 - state.cameraY) / state.zoom
    (ry / HH - rx / HW) / 2.0
  }

  private def worldToTile(sx: Double, sy: Double): (Int, Int) = {
    val wx = screenToWorldX(sx, sy)
    val wy = screenToWorldY(sx, sy)
    (Math.floor(wx).toInt, Math.floor(wy).toInt)
  }

  private def setupMouseHandlers(): Unit = {
    setOnMousePressed((e: MouseEvent) => {
      if (e.isMiddleButtonDown || (e.isPrimaryButtonDown && e.isAltDown) ||
          (e.isPrimaryButtonDown && state.tool == Tool.Pan)) {
        isPanning = true
        panStartX = e.getX
        panStartY = e.getY
        panCamStartX = state.cameraX
        panCamStartY = state.cameraY
      } else if (e.isPrimaryButtonDown) {
        handlePrimaryPress(e)
      } else if (e.isSecondaryButtonDown) {
        handleSecondaryPress(e)
      }
    })

    setOnMouseDragged((e: MouseEvent) => {
      val (wx, wy) = worldToTile(e.getX, e.getY)
      state.cursorWorldX = wx
      state.cursorWorldY = wy
      if (statusBar != null) statusBar.update()

      if (isPanning) {
        state.cameraX = panCamStartX + (e.getX - panStartX)
        state.cameraY = panCamStartY + (e.getY - panStartY)
      } else if (e.isPrimaryButtonDown) {
        handlePrimaryDrag(e)
      }
    })

    setOnMouseReleased((e: MouseEvent) => {
      if (isPanning) {
        isPanning = false
      } else {
        handleMouseRelease(e)
      }
    })

    setOnMouseMoved((e: MouseEvent) => {
      val (wx, wy) = worldToTile(e.getX, e.getY)
      state.cursorWorldX = wx
      state.cursorWorldY = wy
      if (statusBar != null) statusBar.update()
    })
  }

  private def setupScrollHandler(): Unit = {
    setOnScroll((e: ScrollEvent) => {
      val oldZoom = state.zoom
      val factor = if (e.getDeltaY > 0) 1.15 else 1.0 / 1.15
      state.zoom = Math.max(0.25, Math.min(4.0, state.zoom * factor))

      // Zoom toward mouse position
      val mx = e.getX - getWidth / 2.0
      val my = e.getY - getHeight / 2.0
      state.cameraX = mx - (mx - state.cameraX) * (state.zoom / oldZoom)
      state.cameraY = my - (my - state.cameraY) * (state.zoom / oldZoom)
    })
  }

  private def handlePrimaryPress(e: MouseEvent): Unit = {
    val (wx, wy) = worldToTile(e.getX, e.getY)
    state.tool match {
      case Tool.Pencil | Tool.Eraser =>
        undoManager.saveSnapshot(state)
        applyBrush(wx, wy)
      case Tool.Fill =>
        undoManager.saveSnapshot(state)
        DrawingTools.floodFill(state, wx, wy, state.selectedTile)
      case Tool.Rect | Tool.Circle =>
        undoManager.saveSnapshot(state)
        state.dragStartX = wx
        state.dragStartY = wy
        state.isDragging = true
        previewEndX = wx
        previewEndY = wy
      case Tool.Spawn =>
        if (wx >= 0 && wx < state.mapWidth && wy >= 0 && wy < state.mapHeight) {
          state.spawnPoints += ((wx, wy))
          state.dirty = true
        }
      case Tool.Pan => // handled in mouse pressed before this method
    }
  }

  private def handleSecondaryPress(e: MouseEvent): Unit = {
    val (wx, wy) = worldToTile(e.getX, e.getY)
    state.tool match {
      case Tool.Spawn =>
        // Remove spawn point near click
        val idx = state.spawnPoints.indexWhere { case (sx, sy) => sx == wx && sy == wy }
        if (idx >= 0) {
          state.spawnPoints.remove(idx)
          state.dirty = true
        }
      case _ =>
        // Pick tile under cursor
        state.getTile(wx, wy).foreach(t => state.selectedTile = t)
    }
  }

  private def handlePrimaryDrag(e: MouseEvent): Unit = {
    val (wx, wy) = worldToTile(e.getX, e.getY)
    state.tool match {
      case Tool.Pencil | Tool.Eraser =>
        applyBrush(wx, wy)
      case Tool.Rect | Tool.Circle if state.isDragging =>
        previewEndX = wx
        previewEndY = wy
      case _ =>
    }
  }

  private def handleMouseRelease(e: MouseEvent): Unit = {
    if (state.isDragging) {
      val (wx, wy) = worldToTile(e.getX, e.getY)
      state.tool match {
        case Tool.Rect =>
          DrawingTools.drawRect(state, state.dragStartX, state.dragStartY, wx, wy, state.selectedTile)
        case Tool.Circle =>
          DrawingTools.drawCircle(state, state.dragStartX, state.dragStartY, wx, wy, state.selectedTile)
        case _ =>
      }
      state.isDragging = false
      previewEndX = -1
      previewEndY = -1
    }
  }

  private def applyBrush(wx: Int, wy: Int): Unit = {
    val tile = if (state.tool == Tool.Eraser) com.gridgame.common.model.Tile.Grass else state.selectedTile
    DrawingTools.pencil(state, wx, wy, state.brushSize, tile)
  }

  def render(): Unit = {
    val w = getWidth
    val h = getHeight

    // Dark background
    gc.setFill(Color.web("#1a1a2e"))
    gc.fillRect(0, 0, w, h)

    val world = state.world

    // Calculate visible tile bounds
    val corners = Seq(
      (screenToWorldX(0, 0), screenToWorldY(0, 0)),
      (screenToWorldX(w, 0), screenToWorldY(w, 0)),
      (screenToWorldX(0, h), screenToWorldY(0, h)),
      (screenToWorldX(w, h), screenToWorldY(w, h))
    )
    val startX = Math.max(0, corners.map(_._1).min.floor.toInt - 2)
    val endX = Math.min(world.width - 1, corners.map(_._1).max.ceil.toInt + 2)
    val startY = Math.max(0, corners.map(_._2).min.floor.toInt - 2)
    val endY = Math.min(world.height - 1, corners.map(_._2).max.ceil.toInt + 2)

    val scaledHW = HW * state.zoom
    val scaledHH = HH * state.zoom
    val scaledCellH = cellH * state.zoom

    // Phase 1: Walkable (ground) tiles
    for (wy <- startY to endY; wx <- startX to endX) {
      val tile = world.getTile(wx, wy)
      if (tile.walkable) {
        val sx = worldToScreenX(wx, wy)
        val sy = worldToScreenY(wx, wy)
        val img = EditorTileRenderer.getTileImage(tile.id)
        gc.drawImage(img, sx - scaledHW, sy - (scaledCellH - scaledHH),
          Constants.TILE_CELL_WIDTH * state.zoom, scaledCellH)
      }
    }

    // Phase 2: Elevated (non-walkable) tiles
    for (wy <- startY to endY; wx <- startX to endX) {
      val tile = world.getTile(wx, wy)
      if (!tile.walkable) {
        val sx = worldToScreenX(wx, wy)
        val sy = worldToScreenY(wx, wy)
        val img = EditorTileRenderer.getTileImage(tile.id)
        gc.drawImage(img, sx - scaledHW, sy - (scaledCellH - scaledHH),
          Constants.TILE_CELL_WIDTH * state.zoom, scaledCellH)
      }
    }

    // Grid overlay
    if (state.showGrid) {
      gc.setStroke(Color.rgb(255, 255, 255, 0.12))
      gc.setLineWidth(0.5)
      for (wy <- startY to endY; wx <- startX to endX) {
        val sx = worldToScreenX(wx, wy)
        val sy = worldToScreenY(wx, wy)
        drawDiamond(sx, sy, scaledHW, scaledHH)
      }
    }

    // Draw rect/circle preview
    if (state.isDragging && previewEndX >= 0) {
      drawToolPreview(startX, endX, startY, endY)
    }

    // Cursor highlight
    if (state.cursorWorldX >= 0 && state.cursorWorldX < world.width &&
        state.cursorWorldY >= 0 && state.cursorWorldY < world.height) {
      drawCursorHighlight()
    }

    // Spawn point markers
    if (state.showSpawnPoints) {
      drawSpawnPoints()
    }
  }

  private def drawDiamond(cx: Double, cy: Double, hw: Double, hh: Double): Unit = {
    gc.strokePolygon(
      Array(cx, cx + hw, cx, cx - hw),
      Array(cy - hh, cy, cy + hh, cy),
      4
    )
  }

  private def drawCursorHighlight(): Unit = {
    val size = state.brushSize
    val halfSize = size / 2

    gc.setStroke(Color.YELLOW)
    gc.setLineWidth(2.0)

    for (dy <- -halfSize until -halfSize + size; dx <- -halfSize until -halfSize + size) {
      val wx = state.cursorWorldX + dx
      val wy = state.cursorWorldY + dy
      if (wx >= 0 && wx < state.mapWidth && wy >= 0 && wy < state.mapHeight) {
        val sx = worldToScreenX(wx, wy)
        val sy = worldToScreenY(wx, wy)
        val hw = HW * state.zoom
        val hh = HH * state.zoom
        drawDiamond(sx, sy, hw, hh)
      }
    }
  }

  private def drawToolPreview(startX: Int, endX: Int, startY: Int, endY: Int): Unit = {
    gc.setFill(Color.rgb(255, 255, 0, 0.2))
    gc.setStroke(Color.YELLOW)
    gc.setLineWidth(1.5)

    val x1 = Math.min(state.dragStartX, previewEndX)
    val y1 = Math.min(state.dragStartY, previewEndY)
    val x2 = Math.max(state.dragStartX, previewEndX)
    val y2 = Math.max(state.dragStartY, previewEndY)

    state.tool match {
      case Tool.Rect =>
        for (wy <- Math.max(y1, startY) to Math.min(y2, endY);
             wx <- Math.max(x1, startX) to Math.min(x2, endX)) {
          val sx = worldToScreenX(wx, wy)
          val sy = worldToScreenY(wx, wy)
          val hw = HW * state.zoom
          val hh = HH * state.zoom
          gc.fillPolygon(
            Array(sx, sx + hw, sx, sx - hw),
            Array(sy - hh, sy, sy + hh, sy),
            4
          )
        }
      case Tool.Circle =>
        val cx = (state.dragStartX + previewEndX) / 2.0
        val cy = (state.dragStartY + previewEndY) / 2.0
        val rx = Math.abs(previewEndX - state.dragStartX) / 2.0
        val ry = Math.abs(previewEndY - state.dragStartY) / 2.0
        val radius = Math.max(rx, ry)
        for (wy <- Math.max(startY, (cy - radius - 1).toInt) to Math.min(endY, (cy + radius + 1).toInt);
             wx <- Math.max(startX, (cx - radius - 1).toInt) to Math.min(endX, (cx + radius + 1).toInt)) {
          val dx = wx - cx
          val dy = wy - cy
          if (dx * dx + dy * dy <= radius * radius) {
            val sx = worldToScreenX(wx, wy)
            val sy = worldToScreenY(wx, wy)
            val hw = HW * state.zoom
            val hh = HH * state.zoom
            gc.fillPolygon(
              Array(sx, sx + hw, sx, sx - hw),
              Array(sy - hh, sy, sy + hh, sy),
              4
            )
          }
        }
      case _ =>
    }
  }

  private def drawSpawnPoints(): Unit = {
    for ((sx, sy) <- state.spawnPoints) {
      val screenX = worldToScreenX(sx, sy)
      val screenY = worldToScreenY(sx, sy)
      val hw = HW * state.zoom
      val hh = HH * state.zoom

      gc.setFill(Color.rgb(255, 215, 0, 0.5))
      gc.fillPolygon(
        Array(screenX, screenX + hw, screenX, screenX - hw),
        Array(screenY - hh, screenY, screenY + hh, screenY),
        4
      )
      gc.setStroke(Color.GOLD)
      gc.setLineWidth(2.0)
      gc.strokePolygon(
        Array(screenX, screenX + hw, screenX, screenX - hw),
        Array(screenY - hh, screenY, screenY + hh, screenY),
        4
      )

      // "S" label
      gc.setFill(Color.BLACK)
      gc.setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 10 * state.zoom))
      gc.fillText("S", screenX - 3 * state.zoom, screenY + 4 * state.zoom)
    }
  }

  def centerOnMap(): Unit = {
    state.cameraX = 0.0
    state.cameraY = 0.0
  }
}
