package com.gridgame.client.ui

import com.gridgame.common.Constants
import com.gridgame.common.model.Direction

import javafx.scene.canvas.Canvas
import javafx.scene.image.{Image, WritableImage}
import javafx.scene.paint.Color

import java.util.UUID
import scala.collection.mutable

object SpriteGenerator {
  private val spriteCache: mutable.Map[(Int, Direction), Image] = mutable.Map.empty
  private val spriteSize = Constants.CELL_SIZE_PX

  def getSprite(colorRGB: Int, direction: Direction): Image = {
    spriteCache.getOrElseUpdate((colorRGB, direction), generateSprite(colorRGB, direction))
  }

  def clearCache(): Unit = {
    spriteCache.clear()
  }

  private def generateSprite(colorRGB: Int, direction: Direction): Image = {
    val canvas = new Canvas(spriteSize, spriteSize)
    val gc = canvas.getGraphicsContext2D

    val baseColor = intToColor(colorRGB)
    val darkColor = baseColor.darker()
    val lightColor = baseColor.brighter()
    val skinColor = Color.rgb(255, 220, 177)
    val hairColor = darkColor.darker()

    gc.clearRect(0, 0, spriteSize, spriteSize)

    direction match {
      case Direction.Down => drawCharacterDown(gc, baseColor, darkColor, lightColor, skinColor, hairColor)
      case Direction.Up => drawCharacterUp(gc, baseColor, darkColor, lightColor, skinColor, hairColor)
      case Direction.Left => drawCharacterLeft(gc, baseColor, darkColor, lightColor, skinColor, hairColor)
      case Direction.Right => drawCharacterRight(gc, baseColor, darkColor, lightColor, skinColor, hairColor)
    }

    val snapshot = new WritableImage(spriteSize, spriteSize)
    canvas.snapshot(null, snapshot)
    snapshot
  }

  private def drawCharacterDown(gc: javafx.scene.canvas.GraphicsContext,
                                 baseColor: Color, darkColor: Color, lightColor: Color,
                                 skinColor: Color, hairColor: Color): Unit = {
    val s = spriteSize / 20.0

    // Hair (top of head)
    gc.setFill(hairColor)
    gc.fillRect(6*s, 1*s, 8*s, 4*s)

    // Face
    gc.setFill(skinColor)
    gc.fillRect(6*s, 4*s, 8*s, 5*s)

    // Eyes
    gc.setFill(Color.BLACK)
    gc.fillRect(7*s, 5*s, 2*s, 2*s)
    gc.fillRect(11*s, 5*s, 2*s, 2*s)

    // Body (tunic/armor)
    gc.setFill(baseColor)
    gc.fillRect(4*s, 9*s, 12*s, 6*s)

    // Body highlight
    gc.setFill(lightColor)
    gc.fillRect(5*s, 9*s, 4*s, 5*s)

    // Body shadow
    gc.setFill(darkColor)
    gc.fillRect(13*s, 9*s, 2*s, 5*s)

    // Belt
    gc.setFill(Color.rgb(139, 90, 43))
    gc.fillRect(4*s, 14*s, 12*s, 1*s)

    // Arms
    gc.setFill(skinColor)
    gc.fillRect(2*s, 9*s, 2*s, 5*s)
    gc.fillRect(16*s, 9*s, 2*s, 5*s)

    // Legs
    gc.setFill(Color.rgb(70, 70, 90))
    gc.fillRect(5*s, 15*s, 4*s, 4*s)
    gc.fillRect(11*s, 15*s, 4*s, 4*s)

    // Boots
    gc.setFill(Color.rgb(80, 50, 30))
    gc.fillRect(5*s, 18*s, 4*s, 2*s)
    gc.fillRect(11*s, 18*s, 4*s, 2*s)
  }

  private def drawCharacterUp(gc: javafx.scene.canvas.GraphicsContext,
                               baseColor: Color, darkColor: Color, lightColor: Color,
                               skinColor: Color, hairColor: Color): Unit = {
    val s = spriteSize / 20.0

    // Hair (back of head)
    gc.setFill(hairColor)
    gc.fillRect(5*s, 1*s, 10*s, 7*s)

    // Body (tunic/armor)
    gc.setFill(baseColor)
    gc.fillRect(4*s, 9*s, 12*s, 6*s)

    // Cape/back detail
    gc.setFill(darkColor)
    gc.fillRect(5*s, 9*s, 10*s, 5*s)

    // Arms
    gc.setFill(skinColor)
    gc.fillRect(2*s, 9*s, 2*s, 5*s)
    gc.fillRect(16*s, 9*s, 2*s, 5*s)

    // Belt
    gc.setFill(Color.rgb(139, 90, 43))
    gc.fillRect(4*s, 14*s, 12*s, 1*s)

    // Legs
    gc.setFill(Color.rgb(70, 70, 90))
    gc.fillRect(5*s, 15*s, 4*s, 4*s)
    gc.fillRect(11*s, 15*s, 4*s, 4*s)

    // Boots
    gc.setFill(Color.rgb(80, 50, 30))
    gc.fillRect(5*s, 18*s, 4*s, 2*s)
    gc.fillRect(11*s, 18*s, 4*s, 2*s)
  }

  private def drawCharacterLeft(gc: javafx.scene.canvas.GraphicsContext,
                                 baseColor: Color, darkColor: Color, lightColor: Color,
                                 skinColor: Color, hairColor: Color): Unit = {
    val s = spriteSize / 20.0

    // Hair
    gc.setFill(hairColor)
    gc.fillRect(6*s, 1*s, 8*s, 5*s)
    gc.fillRect(4*s, 2*s, 3*s, 3*s) // Hair flowing left

    // Face (side view)
    gc.setFill(skinColor)
    gc.fillRect(8*s, 4*s, 6*s, 5*s)

    // Eye
    gc.setFill(Color.BLACK)
    gc.fillRect(9*s, 5*s, 2*s, 2*s)

    // Body
    gc.setFill(baseColor)
    gc.fillRect(6*s, 9*s, 10*s, 6*s)

    // Body highlight
    gc.setFill(lightColor)
    gc.fillRect(12*s, 9*s, 3*s, 5*s)

    // Arm (front)
    gc.setFill(skinColor)
    gc.fillRect(4*s, 10*s, 3*s, 4*s)

    // Belt
    gc.setFill(Color.rgb(139, 90, 43))
    gc.fillRect(6*s, 14*s, 10*s, 1*s)

    // Legs (walking pose)
    gc.setFill(Color.rgb(70, 70, 90))
    gc.fillRect(7*s, 15*s, 4*s, 4*s)
    gc.fillRect(11*s, 15*s, 3*s, 3*s)

    // Boots
    gc.setFill(Color.rgb(80, 50, 30))
    gc.fillRect(6*s, 18*s, 5*s, 2*s)
    gc.fillRect(11*s, 17*s, 3*s, 2*s)
  }

  private def drawCharacterRight(gc: javafx.scene.canvas.GraphicsContext,
                                  baseColor: Color, darkColor: Color, lightColor: Color,
                                  skinColor: Color, hairColor: Color): Unit = {
    val s = spriteSize / 20.0

    // Hair
    gc.setFill(hairColor)
    gc.fillRect(6*s, 1*s, 8*s, 5*s)
    gc.fillRect(13*s, 2*s, 3*s, 3*s) // Hair flowing right

    // Face (side view)
    gc.setFill(skinColor)
    gc.fillRect(6*s, 4*s, 6*s, 5*s)

    // Eye
    gc.setFill(Color.BLACK)
    gc.fillRect(9*s, 5*s, 2*s, 2*s)

    // Body
    gc.setFill(baseColor)
    gc.fillRect(4*s, 9*s, 10*s, 6*s)

    // Body shadow (opposite side now)
    gc.setFill(darkColor)
    gc.fillRect(5*s, 9*s, 3*s, 5*s)

    // Arm (front)
    gc.setFill(skinColor)
    gc.fillRect(13*s, 10*s, 3*s, 4*s)

    // Belt
    gc.setFill(Color.rgb(139, 90, 43))
    gc.fillRect(4*s, 14*s, 10*s, 1*s)

    // Legs (walking pose)
    gc.setFill(Color.rgb(70, 70, 90))
    gc.fillRect(9*s, 15*s, 4*s, 4*s)
    gc.fillRect(6*s, 15*s, 3*s, 3*s)

    // Boots
    gc.setFill(Color.rgb(80, 50, 30))
    gc.fillRect(9*s, 18*s, 5*s, 2*s)
    gc.fillRect(6*s, 17*s, 3*s, 2*s)
  }

  private def intToColor(argb: Int): Color = {
    val red = (argb >> 16) & 0xFF
    val green = (argb >> 8) & 0xFF
    val blue = argb & 0xFF
    Color.rgb(red, green, blue)
  }
}
