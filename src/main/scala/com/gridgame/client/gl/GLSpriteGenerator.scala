package com.gridgame.client.gl

import com.gridgame.common.Constants
import com.gridgame.common.model.CharacterDef
import com.gridgame.common.model.Direction

/**
 * OpenGL character sprite loader. Packs the character sprite sheets in play into a
 * single texture atlas to eliminate per-character texture bind flushes in SpriteBatch.
 *
 * Atlas layout: 8 sprite sheets per row (each 512×512), rows added as characters appear.
 * Characters are uploaded lazily on first use via glTexSubImage2D.
 *
 * The atlas grows rather than reserving every slot up front. Sized for all 112 characters
 * it would be 4096x8192 RGBA = 128MB of texture memory held for the whole session no
 * matter how few characters a match actually uses — a real cost on an integrated GPU
 * sharing system RAM. A typical match touches a handful of characters and never grows
 * past the first 16MB; growth only happens while characters are first coming into view.
 */
object GLSpriteGenerator {
  private val frameSize = Constants.SPRITE_SIZE_PX // 128
  private val framesPerDirection = 4
  private val sheetSize = frameSize * framesPerDirection // 512

  private val ATLAS_COLS = 8
  private val INITIAL_ROWS = 2  // 16 slots, 16MB
  private val MAX_ROWS = 8      // 64 slots, 64MB — twice the biggest lobby
  private val ATLAS_W = ATLAS_COLS * sheetSize // 4096 (128px frames)

  // Direction.id → row index within a spritesheet (identity mapping — Down=0, Up=1, Left=2, Right=3)
  // Direction.id already matches spritesheet row order, so we use it directly

  // Atlas texture (created lazily)
  private var atlas: GLTexture = _
  private var atlasRows = 0

  // Per-character: flat array of 16 TextureRegions (4 dirs × 4 frames), indexed by characterId
  // Null entry means not yet loaded
  private val MAX_CHARACTERS = 128
  private val regionsByChar = new Array[Array[TextureRegion]](MAX_CHARACTERS)
  private val loadAttempted = new Array[Boolean](MAX_CHARACTERS)

  // Slot counter for atlas placement, and which character sits in each slot so the sheets
  // can be re-uploaded when the atlas grows (a GL texture can't be resized in place).
  private var nextSlot = 0
  private val slotOwner = new Array[Int](ATLAS_COLS * MAX_ROWS)

  // Atlases replaced by a grow are retired, not deleted on the spot: a batch mid-frame may
  // still hold queued vertices that name the old texture. [[disposeRetired]] frees them at
  // the top of the next frame, when nothing is queued.
  private val retired = scala.collection.mutable.ArrayBuffer.empty[GLTexture]

  /** Free atlases replaced by a grow. Call between frames, never mid-frame. */
  def disposeRetired(): Unit = {
    if (retired.isEmpty) return
    retired.foreach(_.dispose())
    retired.clear()
  }

  private def atlasHeight: Int = atlasRows * sheetSize

  // Mip levels below the sheets as drawn: 128px frames down to 32px. A frame is drawn at 77px at
  // High on an ordinary screen and 42px at Low; minified that far with no mip chain, bilinear
  // filtering skips texels, and the one-texel contour that keeps a character readable breaks up
  // and shimmers as it moves. Two levels keep each frame's margin between it and the next.
  private val MIP_LEVELS = 2

  private def ensureAtlas(): Unit = {
    if (atlas == null) {
      atlasRows = INITIAL_ROWS
      atlas = GLTexture.createEmpty(ATLAS_W, atlasHeight, nearest = false, mipLevels = MIP_LEVELS)
    }
  }

  /** Double the atlas height and re-upload everything already in it. Returns false if full. */
  private def growAtlas(): Boolean = {
    if (atlasRows >= MAX_ROWS) return false
    val filled = nextSlot
    retired += atlas
    atlasRows = Math.min(MAX_ROWS, atlasRows * 2)
    atlas = GLTexture.createEmpty(ATLAS_W, atlasHeight, nearest = false, mipLevels = MIP_LEVELS)
    // Re-place every sheet: the slot grid is unchanged, but the regions' V coordinates
    // are relative to the new height, so both the pixels and the regions are rebuilt.
    nextSlot = 0
    var i = 0
    while (i < filled) {
      val id = slotOwner(i)
      regionsByChar(id) = null
      uploadToNextSlot(id, CharacterDef.get(id.toByte).spriteSheet)
      i += 1
    }
    true
  }

  /** Upload one sheet into the next free slot and build its regions. */
  private def uploadToNextSlot(id: Int, spriteSheet: String): Boolean = {
    val slot = nextSlot
    val atlasX = (slot % ATLAS_COLS) * sheetSize
    val atlasY = (slot / ATLAS_COLS) * sheetSize
    // Padded, so the mip levels and filtering average the sprite's edge with its own colours
    // rather than with the black of its transparent texels
    if (!GLTexture.uploadSubImage(atlas, atlasX, atlasY, spriteSheet, padCell = frameSize)) {
      System.err.println(s"GLSpriteGenerator: Failed to load $spriteSheet")
      return false
    }
    nextSlot += 1
    slotOwner(slot) = id

    // Build 16 TextureRegions (4 directions × 4 frames)
    val regions = new Array[TextureRegion](16)
    val invW = 1f / ATLAS_W
    val invH = 1f / atlasHeight
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
    true
  }

  private def ensureLoaded(characterId: Byte): Unit = {
    val id = characterId & 0xFF
    if (id >= MAX_CHARACTERS || loadAttempted(id)) return
    loadAttempted(id) = true

    ensureAtlas()
    val charDef = CharacterDef.get(characterId)
    try {
      if (nextSlot >= atlasRows * ATLAS_COLS && !growAtlas()) {
        System.err.println(s"GLSpriteGenerator: atlas full (${MAX_ROWS * ATLAS_COLS} slots), " +
          s"${charDef.spriteSheet} will not render")
        return
      }
      uploadToNextSlot(id, charDef.spriteSheet)
      GLTexture.generateMipmaps(atlas)
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

  /** Load a character's sheet now if it isn't already: at once for every player in the match,
    * rather than the first time each walks into view. A load decodes, pads and uploads the sheet
    * and rebuilds the atlas's mip chain, which on a weak machine is a hitch mid-fight. */
  def preload(characterId: Byte): Unit = ensureLoaded(characterId)

  def getTexture(characterId: Byte): GLTexture = {
    ensureLoaded(characterId)
    atlas
  }

  def clearCache(): Unit = {
    disposeRetired()
    if (atlas != null) {
      atlas.dispose()
      atlas = null
    }
    atlasRows = 0
    var i = 0
    while (i < MAX_CHARACTERS) {
      regionsByChar(i) = null
      loadAttempted(i) = false
      i += 1
    }
    nextSlot = 0
  }
}
