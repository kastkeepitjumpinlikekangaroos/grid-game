package com.gridgame.client.ui

import com.gridgame.common.Constants
import com.gridgame.common.model.CharacterDef
import com.gridgame.common.model.CharacterId
import com.gridgame.common.model.Direction

import javafx.scene.image.{Image, PixelReader, WritableImage}

import java.io.{File, FileInputStream, InputStream}

object SpriteGenerator {
  private val frameSize = Constants.SPRITE_SIZE_PX // 32
  // Row order in spritesheet: Down=0, Up=1, Left=2, Right=3
  private val directionRow: Map[Direction, Int] = Map(
    Direction.Down  -> 0,
    Direction.Up    -> 1,
    Direction.Left  -> 3,
    Direction.Right -> 2
  )
  private val framesPerDirection = 4

  // Per-character sprite frames: characterId -> ((Direction, frame) -> Image)
  private var characterFrames: Map[Byte, Map[(Direction, Int), Image]] = Map.empty

  private def ensureLoaded(characterId: Byte): Unit = {
    if (characterFrames.contains(characterId)) return

    val charDef = CharacterDef.get(characterId)
    val stream = resolveResourceStream(charDef.spriteSheet)
    if (stream == null) {
      System.err.println(s"Spritesheet not found: ${charDef.spriteSheet}")
      characterFrames = characterFrames + (characterId -> Map.empty)
      return
    }

    val sheet = new Image(stream)
    val reader = sheet.getPixelReader
    val builder = Map.newBuilder[(Direction, Int), Image]

    for ((dir, row) <- directionRow; col <- 0 until framesPerDirection) {
      val srcX = col * frameSize
      val srcY = row * frameSize
      val frameImg = new WritableImage(reader, srcX, srcY, frameSize, frameSize)
      builder += (dir, col) -> frameImg
    }

    characterFrames = characterFrames + (characterId -> builder.result())
  }

  private def resolveResourceStream(relativePath: String): InputStream = {
    val direct = new File(relativePath)
    if (direct.exists()) return new FileInputStream(direct)

    val buildWorkDir = System.getenv("BUILD_WORKING_DIRECTORY")
    if (buildWorkDir != null) {
      val fromWorkDir = new File(buildWorkDir, relativePath)
      if (fromWorkDir.exists()) return new FileInputStream(fromWorkDir)
    }

    // Try classpath (bundled in JAR)
    getClass.getClassLoader.getResourceAsStream(relativePath)
  }

  def getSprite(colorRGB: Int, direction: Direction): Image = {
    getSprite(colorRGB, direction, 0, CharacterId.DEFAULT.id)
  }

  def getSprite(colorRGB: Int, direction: Direction, frame: Int): Image = {
    getSprite(colorRGB, direction, frame, CharacterId.DEFAULT.id)
  }

  def getSprite(colorRGB: Int, direction: Direction, frame: Int, characterId: Byte): Image = {
    ensureLoaded(characterId)
    val clampedFrame = frame % framesPerDirection
    val frames = characterFrames.getOrElse(characterId, Map.empty)
    frames.getOrElse((direction, clampedFrame), {
      // Fallback: return a transparent 32x32 image
      new WritableImage(frameSize, frameSize)
    })
  }

  def clearCache(): Unit = {
    characterFrames = Map.empty
  }
}
