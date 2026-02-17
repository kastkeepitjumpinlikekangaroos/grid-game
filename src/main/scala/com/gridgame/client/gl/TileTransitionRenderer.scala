package com.gridgame.client.gl

import com.gridgame.common.Constants
import com.gridgame.common.model.{Tile, WorldData}

import org.lwjgl.opengl.GL11._
import org.lwjgl.opengl.GL13._
import org.lwjgl.opengl.GL15._
import org.lwjgl.opengl.GL20._
import org.lwjgl.opengl.GL30._
import org.lwjgl.BufferUtils

import java.nio.FloatBuffer

/**
 * Renders smooth tile transitions between adjacent walkable tiles of different types.
 * Uses alpha masks to blend neighbor tile textures at edges.
 *
 * For each visible walkable tile, checks 4 cardinal neighbors (N/E/S/W).
 * Where an adjacent walkable tile differs, draws the neighbor's texture
 * masked by a directional alpha gradient, producing smooth blends.
 */
object TileTransitionRenderer {
  private val HW = Constants.ISO_HALF_W
  private val HH = Constants.ISO_HALF_H
  private val tileW = (HW * 2).toFloat
  private val tileCellH = Constants.TILE_CELL_HEIGHT.toFloat

  // Cardinal directions: dx, dy, mask index
  // N = (-1, 0), E = (0, -1), S = (1, 0), W = (0, 1) in iso world coords
  private val DIRECTIONS = Array(
    (-1, 0, 0), // North mask
    (0, -1, 1), // East mask
    (1, 0, 2),  // South mask
    (0, 1, 3)   // West mask
  )

  private var maskTexture: GLTexture = _
  private var shader: ShaderProgram = _
  private var vao: Int = 0
  private var vbo: Int = 0
  private var initialized = false

  // Floats per vertex: posX, posY, tileU, tileV, maskU, maskV = 6
  private val FLOATS_PER_VERTEX = 6
  private val VERTICES_PER_QUAD = 6 // two triangles
  // Max quads per frame (generous upper bound)
  private val MAX_QUADS = 4096
  private val BUFFER_SIZE = MAX_QUADS * VERTICES_PER_QUAD * FLOATS_PER_VERTEX

  private var dataBuffer: FloatBuffer = _
  private var quadCount: Int = 0

  private def ensureInitialized(): Unit = {
    if (initialized) return
    try {
      maskTexture = GLTexture.load("sprites/transition_masks.png", nearest = false)
    } catch {
      case _: Exception =>
        // No mask texture available — transitions disabled
        return
    }
    shader = new ShaderProgram(ShaderProgram.TRANSITION_VERT, ShaderProgram.TRANSITION_FRAG)
    vao = glGenVertexArrays()
    vbo = glGenBuffers()
    dataBuffer = BufferUtils.createFloatBuffer(BUFFER_SIZE)

    glBindVertexArray(vao)
    glBindBuffer(GL_ARRAY_BUFFER, vbo)
    glBufferData(GL_ARRAY_BUFFER, BUFFER_SIZE.toLong * 4, GL_DYNAMIC_DRAW)

    // aPos (location 0)
    glVertexAttribPointer(0, 2, GL_FLOAT, false, FLOATS_PER_VERTEX * 4, 0)
    glEnableVertexAttribArray(0)
    // aTileUV (location 1)
    glVertexAttribPointer(1, 2, GL_FLOAT, false, FLOATS_PER_VERTEX * 4, 2 * 4L)
    glEnableVertexAttribArray(1)
    // aMaskUV (location 2)
    glVertexAttribPointer(2, 2, GL_FLOAT, false, FLOATS_PER_VERTEX * 4, 4 * 4L)
    glEnableVertexAttribArray(2)

    glBindVertexArray(0)
    initialized = true
  }

  /**
   * Render tile transitions for all visible tiles.
   * Call after drawing ground tiles, with sprite batch ended.
   */
  def render(world: WorldData, startX: Int, endX: Int, startY: Int, endY: Int,
             tileFrame: Int, camOffX: Double, camOffY: Double,
             projection: FloatBuffer): Unit = {
    ensureInitialized()
    if (maskTexture == null) return

    val tileAtlas = GLTileRenderer.getTexture
    if (tileAtlas == null) return

    quadCount = 0
    dataBuffer.clear()

    val maskCellW = maskTexture.width / 4.0f  // 4 masks side by side
    val maskCellH = maskTexture.height.toFloat

    for (wy <- startY to endY; wx <- startX to endX) {
      val tile = world.getTile(wx, wy)
      if (tile.walkable) {
        for ((dx, dy, maskIdx) <- DIRECTIONS) {
          val nx = wx + dx
          val ny = wy + dy
          if (nx >= 0 && nx < world.width && ny >= 0 && ny < world.height) {
            val neighbor = world.getTile(nx, ny)
            if (neighbor.walkable && neighbor.id != tile.id) {
              addTransitionQuad(wx, wy, neighbor, tileFrame, maskIdx,
                tileAtlas, maskCellW, maskCellH, camOffX, camOffY)
            }
          }
        }
      }
    }

    if (quadCount == 0) return

    dataBuffer.flip()

    glEnable(GL_BLEND)
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

    shader.use()
    shader.setUniformMat4("uProjection", projection)
    shader.setUniform1i("uTileAtlas", 0)
    shader.setUniform1i("uMaskAtlas", 1)

    tileAtlas.bind(0)
    maskTexture.bind(1)

    glBindVertexArray(vao)
    glBindBuffer(GL_ARRAY_BUFFER, vbo)
    glBufferSubData(GL_ARRAY_BUFFER, 0, dataBuffer)
    glDrawArrays(GL_TRIANGLES, 0, quadCount * VERTICES_PER_QUAD)
    glBindVertexArray(0)
  }

  private def addTransitionQuad(wx: Int, wy: Int, neighbor: Tile, tileFrame: Int,
                                 maskIdx: Int, tileAtlas: GLTexture,
                                 maskCellW: Float, maskCellH: Float,
                                 camOffX: Double, camOffY: Double): Unit = {
    if (quadCount >= MAX_QUADS) return

    // Screen position
    val sx = (com.gridgame.client.render.IsometricTransform.worldToScreenX(wx, wy, camOffX) - HW).toFloat
    val sy = (com.gridgame.client.render.IsometricTransform.worldToScreenY(wx, wy, camOffY) - (Constants.TILE_CELL_HEIGHT - HH)).toFloat

    // Neighbor tile UV in atlas — fixed position-based variant (no animation)
    val variantFrame = ((wx * 7 + wy * 13) & 0x7FFFFFFF) % GLTileRenderer.getNumFrames
    val neighborRegion = GLTileRenderer.getTileRegion(neighbor.id, variantFrame)
    if (neighborRegion == null) return

    val tu0 = neighborRegion.u
    val tv0 = neighborRegion.v
    val tu1 = neighborRegion.u2
    val tv1 = neighborRegion.v2

    // Mask UV
    val mu0 = maskIdx * maskCellW / maskTexture.width
    val mv0 = 0f
    val mu1 = (maskIdx + 1) * maskCellW / maskTexture.width
    val mv1 = 1f

    // Two triangles for quad
    // Triangle 1: top-left, top-right, bottom-left
    putVertex(sx, sy, tu0, tv0, mu0, mv0)
    putVertex(sx + tileW, sy, tu1, tv0, mu1, mv0)
    putVertex(sx, sy + tileCellH, tu0, tv1, mu0, mv1)
    // Triangle 2: top-right, bottom-right, bottom-left
    putVertex(sx + tileW, sy, tu1, tv0, mu1, mv0)
    putVertex(sx + tileW, sy + tileCellH, tu1, tv1, mu1, mv1)
    putVertex(sx, sy + tileCellH, tu0, tv1, mu0, mv1)

    quadCount += 1
  }

  private def putVertex(x: Float, y: Float, tu: Float, tv: Float, mu: Float, mv: Float): Unit = {
    dataBuffer.put(x).put(y).put(tu).put(tv).put(mu).put(mv)
  }

  def dispose(): Unit = {
    if (maskTexture != null) { maskTexture.dispose(); maskTexture = null }
    if (shader != null) { shader.dispose(); shader = null }
    if (vao != 0) { glDeleteVertexArrays(vao); vao = 0 }
    if (vbo != 0) { glDeleteBuffers(vbo); vbo = 0 }
    initialized = false
  }
}
