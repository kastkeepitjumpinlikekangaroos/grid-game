package com.gridgame.client.gl

import com.gridgame.common.Constants
import com.gridgame.common.model.CharacterDef
import com.gridgame.common.model.Direction

/**
 * OpenGL character sprite loader. Loads character sprite sheets as GL textures
 * and provides TextureRegion per (characterId, direction, frame).
 * Mirrors the functionality of ui/SpriteGenerator.scala for the GL pipeline.
 */
object GLSpriteGenerator {
  private val frameSize = Constants.SPRITE_SIZE_PX // 32
  // Row order in spritesheet: Down=0, Up=1, Left=2, Right=3
  private val directionRow: Map[Direction, Int] = Map(
    Direction.Down  -> 0,
    Direction.Up    -> 1,
    Direction.Left  -> 2,
    Direction.Right -> 3
  )
  private val framesPerDirection = 4

  // Per-character: characterId -> (texture, (Direction, frame) -> TextureRegion)
  private var characterTextures: Map[Byte, GLTexture] = Map.empty
  private var characterRegions: Map[Byte, Map[(Direction, Int), TextureRegion]] = Map.empty

  private def ensureLoaded(characterId: Byte): Unit = {
    if (characterRegions.contains(characterId)) return

    val charDef = CharacterDef.get(characterId)
    try {
      val tex = GLTexture.load(charDef.spriteSheet, nearest = true)
      characterTextures = characterTextures + (characterId -> tex)

      val builder = Map.newBuilder[(Direction, Int), TextureRegion]
      for ((dir, row) <- directionRow; col <- 0 until framesPerDirection) {
        val srcX = col * frameSize
        val srcY = row * frameSize
        builder += (dir, col) -> tex.region(srcX, srcY, frameSize, frameSize)
      }
      characterRegions = characterRegions + (characterId -> builder.result())
    } catch {
      case e: Exception =>
        System.err.println(s"GLSpriteGenerator: Failed to load ${charDef.spriteSheet}: ${e.getMessage}")
        characterRegions = characterRegions + (characterId -> Map.empty)
    }
  }

  def getSpriteRegion(direction: Direction, frame: Int, characterId: Byte): TextureRegion = {
    ensureLoaded(characterId)
    val clampedFrame = frame % framesPerDirection
    val regions = characterRegions.getOrElse(characterId, Map.empty)
    regions.getOrElse((direction, clampedFrame), null)
  }

  def getTexture(characterId: Byte): GLTexture = {
    ensureLoaded(characterId)
    characterTextures.getOrElse(characterId, null)
  }

  def clearCache(): Unit = {
    characterTextures.values.foreach(_.dispose())
    characterTextures = Map.empty
    characterRegions = Map.empty
  }
}
