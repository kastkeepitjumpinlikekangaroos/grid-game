package com.gridgame.client.ui

import com.gridgame.client.GameClient
import com.gridgame.common.Constants
import com.gridgame.common.model.Player
import com.gridgame.common.model.Position

import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.paint.Color

import java.util.UUID
import scala.jdk.CollectionConverters._

class GameCanvas(client: GameClient) extends Canvas(Constants.VIEWPORT_SIZE_PX, Constants.VIEWPORT_SIZE_PX) {
  private val gc: GraphicsContext = getGraphicsContext2D

  def render(): Unit = {
    gc.clearRect(0, 0, getWidth, getHeight)

    val localPos = client.getLocalPosition

    val viewportX = calculateViewportOffset(localPos.getX)
    val viewportY = calculateViewportOffset(localPos.getY)

    drawGrid(viewportX, viewportY)

    client.getPlayers.values().asScala.foreach { player =>
      val pos = player.getPosition
      if (isInViewport(pos, viewportX, viewportY)) {
        drawPlayer(player, viewportX, viewportY, isLocal = false)
      }
    }

    drawLocalPlayer(localPos, viewportX, viewportY)

    drawCoordinates(viewportX, viewportY)
  }

  private def calculateViewportOffset(playerCoord: Int): Int = {
    val offset = playerCoord - (Constants.VIEWPORT_CELLS / 2)
    Math.max(0, Math.min(Constants.GRID_SIZE - Constants.VIEWPORT_CELLS, offset))
  }

  private def isInViewport(pos: Position, viewportX: Int, viewportY: Int): Boolean = {
    pos.getX >= viewportX &&
    pos.getX < viewportX + Constants.VIEWPORT_CELLS &&
    pos.getY >= viewportY &&
    pos.getY < viewportY + Constants.VIEWPORT_CELLS
  }

  private def drawGrid(viewportX: Int, viewportY: Int): Unit = {
    gc.setStroke(Color.LIGHTGRAY)
    gc.setLineWidth(0.5)

    for (i <- 0 to Constants.VIEWPORT_CELLS) {
      val x = i * Constants.CELL_SIZE_PX
      gc.strokeLine(x, 0, x, Constants.VIEWPORT_SIZE_PX)
    }

    for (i <- 0 to Constants.VIEWPORT_CELLS) {
      val y = i * Constants.CELL_SIZE_PX
      gc.strokeLine(0, y, Constants.VIEWPORT_SIZE_PX, y)
    }
  }

  private def drawPlayer(player: Player, viewportX: Int, viewportY: Int, isLocal: Boolean): Unit = {
    val pos = player.getPosition

    val screenX = (pos.getX - viewportX) * Constants.CELL_SIZE_PX
    val screenY = (pos.getY - viewportY) * Constants.CELL_SIZE_PX

    gc.setFill(intToColor(player.getColorRGB))
    gc.fillRect(screenX + 1, screenY + 1, Constants.CELL_SIZE_PX - 2, Constants.CELL_SIZE_PX - 2)

    if (isLocal) {
      gc.setStroke(Color.WHITE)
      gc.setLineWidth(2)
      gc.strokeRect(screenX + 2, screenY + 2, Constants.CELL_SIZE_PX - 4, Constants.CELL_SIZE_PX - 4)
    }
  }

  private def drawLocalPlayer(pos: Position, viewportX: Int, viewportY: Int): Unit = {
    val screenX = (pos.getX - viewportX) * Constants.CELL_SIZE_PX
    val screenY = (pos.getY - viewportY) * Constants.CELL_SIZE_PX

    gc.setFill(intToColor(client.getLocalColorRGB))
    gc.fillRect(screenX + 1, screenY + 1, Constants.CELL_SIZE_PX - 2, Constants.CELL_SIZE_PX - 2)

    gc.setStroke(Color.WHITE)
    gc.setLineWidth(3)
    gc.strokeRect(screenX + 2, screenY + 2, Constants.CELL_SIZE_PX - 4, Constants.CELL_SIZE_PX - 4)
  }

  private def drawCoordinates(viewportX: Int, viewportY: Int): Unit = {
    val localPos = client.getLocalPosition
    val playerCount = client.getPlayers.size()

    gc.setFill(Color.BLACK)
    gc.setStroke(Color.WHITE)
    gc.setLineWidth(1)

    val coordText = s"Position: (${localPos.getX}, ${localPos.getY})"
    val viewportText = s"Viewport: [$viewportX-${viewportX + Constants.VIEWPORT_CELLS - 1}, $viewportY-${viewportY + Constants.VIEWPORT_CELLS - 1}]"
    val playersText = s"Players: ${playerCount + 1}"

    drawOutlinedText(coordText, 10, 20)
    drawOutlinedText(viewportText, 10, 40)
    drawOutlinedText(playersText, 10, 60)
  }

  private def drawOutlinedText(text: String, x: Double, y: Double): Unit = {
    gc.setStroke(Color.WHITE)
    gc.setLineWidth(3)
    gc.strokeText(text, x, y)

    gc.setFill(Color.BLACK)
    gc.fillText(text, x, y)
  }

  private def intToColor(argb: Int): Color = {
    val alpha = (argb >> 24) & 0xFF
    val red = (argb >> 16) & 0xFF
    val green = (argb >> 8) & 0xFF
    val blue = argb & 0xFF
    Color.rgb(red, green, blue, alpha / 255.0)
  }
}
