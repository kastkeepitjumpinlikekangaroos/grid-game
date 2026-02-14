package com.gridgame.mapeditor

import com.gridgame.common.model.Tile
import scala.collection.mutable

object DrawingTools {

  def pencil(state: EditorState, wx: Int, wy: Int, brushSize: Int, tile: Tile): Unit = {
    val half = brushSize / 2
    for (dy <- -half until -half + brushSize; dx <- -half until -half + brushSize) {
      state.setTile(wx + dx, wy + dy, tile)
    }
  }

  def drawRect(state: EditorState, x1: Int, y1: Int, x2: Int, y2: Int, tile: Tile): Unit = {
    val minX = Math.min(x1, x2)
    val maxX = Math.max(x1, x2)
    val minY = Math.min(y1, y2)
    val maxY = Math.max(y1, y2)
    for (y <- minY to maxY; x <- minX to maxX) {
      state.setTile(x, y, tile)
    }
  }

  def drawCircle(state: EditorState, x1: Int, y1: Int, x2: Int, y2: Int, tile: Tile): Unit = {
    val cx = (x1 + x2) / 2.0
    val cy = (y1 + y2) / 2.0
    val rx = Math.abs(x2 - x1) / 2.0
    val ry = Math.abs(y2 - y1) / 2.0
    val radius = Math.max(rx, ry)

    val iRadius = radius.toInt + 1
    for (y <- (cy - iRadius).toInt to (cy + iRadius).toInt;
         x <- (cx - iRadius).toInt to (cx + iRadius).toInt) {
      val dx = x - cx
      val dy = y - cy
      if (dx * dx + dy * dy <= radius * radius) {
        state.setTile(x, y, tile)
      }
    }
  }

  def floodFill(state: EditorState, startX: Int, startY: Int, tile: Tile): Unit = {
    val targetTile = state.getTile(startX, startY).orNull
    if (targetTile == null || targetTile == tile) return

    val queue = mutable.Queue[(Int, Int)]()
    val visited = mutable.Set[(Int, Int)]()
    queue.enqueue((startX, startY))
    visited.add((startX, startY))

    while (queue.nonEmpty) {
      val (x, y) = queue.dequeue()
      state.setTile(x, y, tile)

      for ((dx, dy) <- Seq((-1, 0), (1, 0), (0, -1), (0, 1))) {
        val nx = x + dx
        val ny = y + dy
        if (!visited.contains((nx, ny))) {
          state.getTile(nx, ny) match {
            case Some(t) if t == targetTile =>
              visited.add((nx, ny))
              queue.enqueue((nx, ny))
            case _ =>
          }
        }
      }
    }
  }
}
