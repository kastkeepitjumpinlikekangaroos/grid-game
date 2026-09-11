package com.gridgame.client.gl

import com.gridgame.common.model.{Projectile, ProjectileType}
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW._
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11._
import org.lwjgl.opengl.GL30._
import org.lwjgl.system.MemoryUtil.NULL

import java.awt.image.BufferedImage
import java.io.File
import java.util.UUID
import javax.imageio.ImageIO

/**
 * Dev tool: renders every registered projectile type into a contact sheet PNG so the
 * whole roster can be eyeballed side by side. Not shipped — run with
 *
 *   bazel run //src/main/scala/com/gridgame/client:projectile_gallery -- out/dir
 *
 * Each cell draws one projectile over three terrain bands (dark stone, grass, sand),
 * because a projectile that reads well on one ground can vanish on another. Frames are
 * emitted at several animation ticks so motion-driven shapes can be judged too.
 */
object ProjectileGallery {

  // The renderer draws in the game's virtual (pre-zoom) space: the scene projection
  // covers windowWidth / CAMERA_ZOOM units, so a shape authored at N units lands on
  // screen at N * 1.6 px. The sheet reproduces that, or every judgement about size and
  // legibility would be made at 62% of the size the player actually sees.
  private val Zoom = 1.6f
  private val CellW = 240   // virtual units
  private val CellH = 165
  private val Cols = 4
  private val Rows = 4
  private val PerPage = Cols * Rows
  private val W = Math.round(CellW * Cols * Zoom)
  private val H = Math.round(CellH * Rows * Zoom)

  /** All (name, id) pairs declared on ProjectileType, in declaration (id) order. */
  private def allTypes: Seq[(String, Byte)] = {
    val m = ProjectileType.getClass.getDeclaredMethods
      .filter(mm => mm.getParameterCount == 0 && mm.getReturnType == java.lang.Byte.TYPE)
      .map(mm => (mm.getName, mm.invoke(ProjectileType).asInstanceOf[java.lang.Byte].byteValue()))
    m.sortBy { case (_, id) => id & 0xFF }.toSeq
  }

  def main(args: Array[String]): Unit = {
    val outDir = new File(if (args.nonEmpty) args(0) else "gallery")
    outDir.mkdirs()

    GLFWManager.ensureInitialized()
    glfwDefaultWindowHints()
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
    glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE)
    glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
    val win = glfwCreateWindow(W, H, "gallery", NULL, NULL)
    if (win == NULL) throw new RuntimeException("no window")
    glfwMakeContextCurrent(win)
    GL.createCapabilities()
    glDisable(GL_DEPTH_TEST)

    val colorShader = new ShaderProgram(ShaderProgram.COLOR_VERT, ShaderProgram.COLOR_FRAG)
    val sb = new ShapeBatch(colorShader)
    val post = new PostProcessor(W, H)
    post.bloomThreshold = 0.80f
    post.bloomStrength = 0.22f
    post.vignetteStrength = 0f

    val types = allTypes
    val pages = (types.size + PerPage - 1) / PerPage
    val ticks = Seq(0, 9, 18, 27)

    if (args.length > 1 && args(1) == "--bench") {
      // Cost of one screen's worth of projectiles, so an art change can be checked
      // against the frame budget rather than guessed at.
      val slice = types.slice(0, PerPage)
      var w = 0; while (w < 40) { renderPage(sb, post, slice, w); w += 1 }
      org.lwjgl.opengl.GL11.glFinish()
      val t0 = System.nanoTime()
      var i2 = 0; while (i2 < 200) { renderPage(sb, post, slice, i2); i2 += 1 }
      org.lwjgl.opengl.GL11.glFinish()
      val full = (System.nanoTime() - t0) / 200.0 / 1e6
      val empty = Seq.empty[(String, Byte)]
      var w2 = 0; while (w2 < 40) { renderPage(sb, post, empty, w2); w2 += 1 }
      org.lwjgl.opengl.GL11.glFinish()
      val t1 = System.nanoTime()
      var i3 = 0; while (i3 < 200) { renderPage(sb, post, empty, i3); i3 += 1 }
      org.lwjgl.opengl.GL11.glFinish()
      val base = (System.nanoTime() - t1) / 200.0 / 1e6
      println(f"bench: $full%.3f ms/frame total, $base%.3f ms baseline (ground+post) => $PerPage%d projectiles cost ${full - base}%.3f ms at ${W}x$H")
    }

    val index = new StringBuilder
    for (page <- 0 until pages) {
      val slice = types.slice(page * PerPage, (page + 1) * PerPage)
      slice.zipWithIndex.foreach { case ((n, id), i) =>
        index.append(s"$page ${i / Cols} ${i % Cols} $n ${id & 0xFF}\n")
      }
      for (t <- ticks) {
        renderPage(sb, post, slice, t)
        val img = readback()
        ImageIO.write(img, "png", new File(outDir, f"page${page}%02d_t${t}%02d.png"))
      }
    }
    java.nio.file.Files.write(new File(outDir, "index.txt").toPath, index.toString.getBytes("UTF-8"))
    renderImpacts(sb, post, types, outDir)
    renderFlyers(sb, post, types, outDir)
    println(s"wrote ${pages * ticks.size} sheets for ${types.size} types to ${outDir.getAbsolutePath}")
    glfwDestroyWindow(win)
    glfwTerminate()
  }

  private def renderPage(sb: ShapeBatch, post: PostProcessor,
                         slice: Seq[(String, Byte)], tick: Int): Unit = {
    post.animationTime = tick * 0.016f
    post.beginScene()
    val proj = Matrix4.orthographic(0f, W / Zoom, H / Zoom, 0f)
    sb.begin(proj)

    // Ground: real isometric tile diamonds (40x20 virtual units, as in game) in three
    // bands — dark stone, grass, sand — because a projectile that reads on one ground
    // can disappear on another.
    var i = 0
    while (i < slice.size) {
      val cx = (i % Cols) * CellW
      val cy = (i / Cols) * CellH
      drawGround(sb, cx.toFloat, cy.toFloat)
      i += 1
    }

    i = 0
    while (i < slice.size) {
      val (_, id) = slice(i)
      val cx = (i % Cols) * CellW
      val cy = (i / Cols) * CellH
      // Origin sits right-of-centre: every projectile's head is at its hitbox and
      // trails, tethers and wakes stream out BEHIND it (leftward here), so that is where
      // the room is needed.
      val sx = cx + CellW * 0.62f
      val sy = cy + CellH * 0.55f
      val p = makeProjectile(id, i)
      val r = GLProjectileRenderers.getRenderer(id)
      if (r != null) r(p, sx, sy, sb, tick)
      else GLProjectileRenderers.drawGeneric(p, sx, sy, sb, tick, 0.55f, 0.7f, 0.95f)
      i += 1
    }
    sb.end()
    post.endScene(W, H)
  }

  /** One cell of ground: iso diamonds banded dark / grass / sand, plus a player-sized
   *  reference box so the projectile's scale against a character is obvious. */
  private def drawGround(sb: ShapeBatch, ox: Float, oy: Float): Unit = {
    sb.fillRect(ox, oy, CellW.toFloat, CellH.toFloat, 0.30f, 0.42f, 0.26f, 1f)
    val hw = 20f; val hh = 10f
    var row = -2
    while (row < 14) {
      var col = -6
      while (col < 12) {
        val dx = ox + (col - row) * hw + CellW * 0.5f
        val dy = oy + (col + row) * hh - CellH * 0.15f
        if (dx > ox - hw && dx < ox + CellW + hw && dy > oy - hh && dy < oy + CellH + hh) {
          val band = ((dy - oy) / (CellH / 3f)).toInt
          val (r, g, b) = band match {
            case 0 => (0.17f, 0.19f, 0.23f)
            case 1 => (0.29f, 0.45f, 0.27f)
            case _ => (0.74f, 0.66f, 0.48f)
          }
          val v = if (((row + col) & 1) == 0) 1f else 0.92f
          val xs = Array(dx, dx + hw, dx, dx - hw)
          val ys = Array(dy - hh, dy, dy + hh, dy)
          sb.fillPolygon(xs, ys, r * v, g * v, b * v, 1f)
        }
        col += 1
      }
      row += 1
    }
    // 48-unit player footprint marker (PLAYER_DISPLAY_SIZE_PX) for scale
    sb.strokeRect(ox + 6f, oy + CellH - 54f, 48f, 48f, 1f, 1f, 1f, 1f, 0.18f)
  }

  /**
   * Terrain impact sheet: each row is one projectile running into a wall block and being
   * absorbed, each column a later moment of the fade (t = 0.05, 0.3, 0.55, 0.8). Written
   * as impacts_NN.png next to the pages.
   */
  private def renderImpacts(sb: ShapeBatch, post: PostProcessor, types: Seq[(String, Byte)], outDir: File): Unit = {
    val picks = Seq("NORMAL", "FIREBALL", "ARROW", "LASER", "AXE", "FROST_SHARD", "GRENADE", "BULLET")
    val byName = types.toMap
    val ts = Array(0.05f, 0.3f, 0.55f, 0.8f)
    val wall = com.gridgame.common.model.Tile.Wall.color
    picks.grouped(Rows).zipWithIndex.foreach { case (group, sheet) =>
      post.beginScene()
      sb.begin(Matrix4.orthographic(0f, W / Zoom, H / Zoom, 0f))
      var row = 0
      while (row < group.size) {
        var col = 0
        while (col < Cols) {
          val cx = col * CellW; val cy = row * CellH
          drawGround(sb, cx.toFloat, cy.toFloat)
          // Impact point on the wall's up-left face; the wall's own diamond is half a tile on
          val ix = cx + CellW * 0.5f; val iy = cy + CellH * 0.55f
          val wx = ix + 10f; val wy = iy + 5f
          drawWallBlock(sb, wx, wy, 18f)
          val p = makeProjectile(byName(group(row)), row * 7 + col)
          GLProjectileRenderers.drawAbsorbed(p, ix, iy, sb, 9, ts(col), hitTerrain = true, wall, 0.55f, 0.7f, 0.95f)
          col += 1
        }
        row += 1
      }
      sb.end()
      post.endScene(W, H)
      ImageIO.write(readback(), "png", new File(outDir, f"impacts_$sheet%02d.png"))
      val idx = new StringBuilder
      group.zipWithIndex.foreach { case (n, r) => idx.append(s"$r $n\n") }
      java.nio.file.Files.write(new File(outDir, f"impacts_$sheet%02d.txt").toPath, idx.toString.getBytes("UTF-8"))
    }
  }

  /**
   * Flight sheet: each row is one wall-passing projectile crossing a wall block, each column
   * a point along the crossing — before it, over its near half, over its far half, past it.
   * Mirrors the game's order: the block, then the shadow on whatever surface is under the
   * projectile, then the projectile itself lifted by GLProjectileRenderers.flyLift.
   * Written as flyers_00.png.
   */
  private def renderFlyers(sb: ShapeBatch, post: PostProcessor, types: Seq[(String, Byte)], outDir: File): Unit = {
    val picks = Seq("SOUL_BOLT", "LIGHTNING", "SONIC_WAVE", "RAILGUN")
    val byName = types.toMap
    val offs = Array(-1.3f, -0.25f, 0.25f, 1.3f)
    val e = 18f
    post.beginScene()
    sb.begin(Matrix4.orthographic(0f, W / Zoom, H / Zoom, 0f))
    var row = 0
    while (row < picks.size) {
      var col = 0
      while (col < Cols) {
        val cx = col * CellW; val cy = row * CellH
        drawGround(sb, cx.toFloat, cy.toFloat)
        val bx = cx + CellW * 0.5f; val by = cy + CellH * 0.62f
        drawWallBlock(sb, bx, by, e)
        val k = offs(col)
        val sx = bx + k * 20f; val sy = by + k * 10f
        val p = makeProjectile(byName(picks(row)), row * 5 + col)
        val lift = GLProjectileRenderers.flyLift(p, 9)
        val groundY = if (Math.abs(k) < 0.5f) sy - e else sy
        GLProjectileRenderers.drawFlightShadow(p, sx, groundY, sb, 9, lift)
        GLProjectileRenderers.draw(p, sx, sy - lift, sb, 9, 0.55f, 0.7f, 0.95f)
        col += 1
      }
      row += 1
    }
    sb.end()
    post.endScene(W, H)
    ImageIO.write(readback(), "png", new File(outDir, "flyers_00.png"))
  }

  /** A wall-coloured elevated block, `e` tall, whose ground diamond is centred on (wx, wy). */
  private def drawWallBlock(sb: ShapeBatch, wx: Float, wy: Float, e: Float): Unit = {
    val wall = com.gridgame.common.model.Tile.Wall.color
    val wr = ((wall >> 16) & 0xFF) / 255f; val wg = ((wall >> 8) & 0xFF) / 255f; val wb = (wall & 0xFF) / 255f
    _polyXs(0) = wx - 20f; _polyYs(0) = wy - e
    _polyXs(1) = wx; _polyYs(1) = wy - 10f - e
    _polyXs(2) = wx + 20f; _polyYs(2) = wy - e
    _polyXs(3) = wx + 20f; _polyYs(3) = wy
    _polyXs(4) = wx; _polyYs(4) = wy + 10f
    _polyXs(5) = wx - 20f; _polyYs(5) = wy
    sb.fillPolygon(_polyXs, _polyYs, 6, wr * 0.7f, wg * 0.7f, wb * 0.7f, 1f)
    _polyXs(0) = wx - 20f; _polyYs(0) = wy - e
    _polyXs(1) = wx; _polyYs(1) = wy - 10f - e
    _polyXs(2) = wx + 20f; _polyYs(2) = wy - e
    _polyXs(3) = wx; _polyYs(3) = wy + 10f - e
    sb.fillPolygon(_polyXs, _polyYs, 4, wr * 1.15f, wg * 1.15f, wb * 1.15f, 1f)
  }

  private val _polyXs = new Array[Float](8)
  private val _polyYs = new Array[Float](8)

  /** A projectile travelling right-and-down (screen-right in the isometric projection). */
  private def makeProjectile(id: Byte, seed: Int): Projectile = {
    val p = new Projectile(seed * 7 + 3, UUID.randomUUID(), 10f, 10f, 1f, 0f, 0x88AAFF, 0, id)
    var n = 0
    while (n < 6) { p.moveStep(1f); n += 1 } // mid-flight, so lifetime-driven visuals show
    p
  }

  private def readback(): BufferedImage = {
    val buf = BufferUtils.createByteBuffer(W * H * 4)
    glBindFramebuffer(GL_FRAMEBUFFER, 0)
    glReadPixels(0, 0, W, H, GL_RGBA, GL_UNSIGNED_BYTE, buf)
    val img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB)
    var y = 0
    while (y < H) {
      var x = 0
      while (x < W) {
        val o = ((H - 1 - y) * W + x) * 4
        val r = buf.get(o) & 0xFF
        val g = buf.get(o + 1) & 0xFF
        val b = buf.get(o + 2) & 0xFF
        img.setRGB(x, y, (r << 16) | (g << 8) | b)
        x += 1
      }
      y += 1
    }
    img
  }
}
