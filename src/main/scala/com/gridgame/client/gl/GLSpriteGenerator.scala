package com.gridgame.client.gl

import com.gridgame.common.Constants
import com.gridgame.common.model.CharacterDef
import com.gridgame.common.model.Direction

/**
 * OpenGL character sprite loader. Packs all character sprite sheets into a
 * single texture atlas to eliminate per-character texture bind flushes in SpriteBatch.
 *
 * Atlas layout: 8 sprite sheets per row (each 256×256), up to 16 rows = 128 slots.
 * Characters are uploaded lazily on first use via glTexSubImage2D.
 */
object GLSpriteGenerator {
  private val frameSize = Constants.SPRITE_SIZE_PX // 64
  private val framesPerDirection = 4
  private val sheetSize = frameSize * framesPerDirection // 256

  // Atlas dimensions: 8 columns × 16 rows = 128 character slots
  private val ATLAS_COLS = 8
  private val ATLAS_ROWS = 16
  private val ATLAS_W = ATLAS_COLS * sheetSize // 2048
  private val ATLAS_H = ATLAS_ROWS * sheetSize // 4096

  // Direction.id → row index within a spritesheet (identity mapping — Down=0, Up=1, Left=2, Right=3)
  // Direction.id already matches spritesheet row order, so we use it directly

  // Atlas texture (created lazily)
  private var atlas: GLTexture = _

  // Per-character: flat array of 16 TextureRegions (4 dirs × 4 frames), indexed by characterId
  // Null entry means not yet loaded
  private val MAX_CHARACTERS = 128
  private val regionsByChar = new Array[Array[TextureRegion]](MAX_CHARACTERS)
  private val loadAttempted = new Array[Boolean](MAX_CHARACTERS)

  // Slot counter for atlas placement
  private var nextSlot = 0

  private def ensureAtlas(): Unit = {
    if (atlas == null) {
      atlas = GLTexture.createEmpty(ATLAS_W, ATLAS_H, nearest = false)
    }
  }

  private def ensureLoaded(characterId: Byte): Unit = {
    val id = characterId & 0xFF
    if (id >= MAX_CHARACTERS || loadAttempted(id)) return
    loadAttempted(id) = true

    ensureAtlas()
    val charDef = CharacterDef.get(characterId)
    try {
      val slot = nextSlot
      nextSlot += 1
      val atlasX = (slot % ATLAS_COLS) * sheetSize
      val atlasY = (slot / ATLAS_COLS) * sheetSize

      if (!GLTexture.uploadSubImage(atlas, atlasX, atlasY, charDef.spriteSheet)) {
        System.err.println(s"GLSpriteGenerator: Failed to load ${charDef.spriteSheet}")
        return
      }

      // Build 16 TextureRegions (4 directions × 4 frames)
      val regions = new Array[TextureRegion](16)
      val invW = 1f / ATLAS_W
      val invH = 1f / ATLAS_H
      var dir = 0
      while (dir < 4) {
        var frame = 0
        while (frame < framesPerDirection) {
          val px = atlasX + frame * frameSize
          val py = atlasY + dir * frameSize
          regions(dir * 4 + frame) = TextureRegion(
            atlas,
            px * invW,
            py * invH,
            (px + frameSize) * invW,
            (py + frameSize) * invH
          )
          frame += 1
        }
        dir += 1
      }
      regionsByChar(id) = regions
    } catch {
      case e: Exception =>
        System.err.println(s"GLSpriteGenerator: Failed to load ${charDef.spriteSheet}: ${e.getMessage}")
    }
  }

  def getSpriteRegion(direction: Direction, frame: Int, characterId: Byte): TextureRegion = {
    ensureLoaded(characterId)
    val id = characterId & 0xFF
    if (id >= MAX_CHARACTERS) return null
    val regions = regionsByChar(id)
    if (regions == null) return null
    val dir = direction.id
    regions(dir * 4 + (frame % framesPerDirection))
  }

  def getTexture(characterId: Byte): GLTexture = {
    ensureLoaded(characterId)
    atlas
  }

  def clearCache(): Unit = {
    if (atlas != null) {
      atlas.dispose()
      atlas = null
    }
    var i = 0
    while (i < MAX_CHARACTERS) {
      regionsByChar(i) = null
      loadAttempted(i) = false
      i += 1
    }
    nextSlot = 0
  }
}
