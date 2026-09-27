package com.gridgame.client.render
package world

import com.gridgame.client.game.GameClient
import com.gridgame.client.gl.RenderQuality
import com.gridgame.client.render.blasts.GLBlastRenderers

/**
 * Every explosion and splash going off, drawn as whoever threw it (GLBlastRenderers): gathered once a
 * frame into flat arrays ([[collect]]), drawn in two layers — on the ground under the players
 * and in the air over everything — and read again for the light they give and the air they shake.
 */
private[render] final class BlastPainter(ctx: RenderContext) {
  import ctx._

  // ── Blasts: every explosion and splash, drawn as whoever threw it (GLBlastRenderers) ──
  // Gathered once a frame into flat arrays (collect), then drawn in two layers (draw), and read
  // again for their lights and the air they shake.
  private val MAX_BLASTS = 48
  private var _blN = 0
  private val _blStyle = new Array[Int](MAX_BLASTS)
  private val _blSX = new Array[Float](MAX_BLASTS)
  private val _blSY = new Array[Float](MAX_BLASTS)
  private val _blWX = new Array[Float](MAX_BLASTS)
  private val _blWY = new Array[Float](MAX_BLASTS)
  private val _blW = new Array[Float](MAX_BLASTS)
  private val _blH = new Array[Float](MAX_BLASTS)
  private val _blT = new Array[Float](MAX_BLASTS)
  private val _blMs = new Array[Float](MAX_BLASTS)
  private val _blSeed = new Array[Int](MAX_BLASTS)
  private val _blCR = new Array[Float](MAX_BLASTS)
  private val _blCG = new Array[Float](MAX_BLASTS)
  private val _blCB = new Array[Float](MAX_BLASTS)
  private val _blOrder = new Array[Int](MAX_BLASTS)
  private var _blDetail = 1f

  /**
   * The blasts going off this frame: the finished ones dropped, the rest placed on screen and
   * sorted far to near, those wholly off it left out. A blast that has just gone off near us
   * shakes the camera, once.
   */
  def collect(): Unit = {
    _blN = 0
    val map = client.getBlasts
    if (map.isEmpty) return
    val now = frameTimeMs
    val lvx = client.visualPosX; val lvy = client.visualPosY
    val iter = map.values().iterator()
    while (iter.hasNext) {
      val d = iter.next()
      val style =
        if (d(GameClient.BLAST_TRAP) != 0L) GLBlastRenderers.trapStyle(d(GameClient.BLAST_TYPE).toByte)
        else GLBlastRenderers.styleOf(d(GameClient.BLAST_CHAR).toByte, d(GameClient.BLAST_TYPE).toByte)
      val dur = GLBlastRenderers.durationMs(style)
      val elapsed = now - d(GameClient.BLAST_TIME)
      if (elapsed >= dur) iter.remove()
      else if (elapsed >= 0L) {
        val wx = d(GameClient.BLAST_X) / 1000.0; val wy = d(GameClient.BLAST_Y) / 1000.0
        val radius = d(GameClient.BLAST_RADIUS) / 1000f
        val heavy = GLBlastRenderers.heaviness(style)
        // Close by and just gone off: the ground shakes under us, once
        if (heavy >= 0.8f && d(GameClient.BLAST_SEEN) == 0L) {
          d(GameClient.BLAST_SEEN) = 1L
          val ddx = wx - lvx; val ddy = wy - lvy
          val dist = Math.sqrt(ddx * ddx + ddy * ddy)
          if (elapsed < 250L && dist < 6.0) camera.addShake(heavy * (2.0 + radius * 0.5) * (1.0 - dist / 6.0))
        }
        val sx = worldToScreenX(wx, wy).toFloat
        val sy = worldToScreenY(wx, wy).toFloat
        val w = GLBlastRenderers.footprintW(radius)
        val h = GLBlastRenderers.footprintH(radius)
        // On screen with room round it: what flies off a blast lands past its edge, and what
        // rises (a geyser, a bolt out of the sky) stands well above it
        if (_blN < MAX_BLASTS && sx + w * 1.3f > 0f && sx - w * 1.3f < canvasW &&
            sy + h * 1.3f > 0f && sy - h * 1.3f - 260f < canvasH) {
          val i = _blN
          _blStyle(i) = style
          _blSX(i) = sx; _blSY(i) = sy; _blWX(i) = wx.toFloat; _blWY(i) = wy.toFloat
          _blW(i) = w; _blH(i) = h
          _blT(i) = elapsed.toFloat / dur
          _blMs(i) = elapsed.toFloat
          _blSeed(i) = d(GameClient.BLAST_SEED).toInt
          intToRGB(d(GameClient.BLAST_COLOR).toInt)
          _blCR(i) = _rgb_r; _blCG(i) = _rgb_g; _blCB(i) = _rgb_b
          // Far to near, so a nearer blast draws over a further one
          var j = i
          while (j > 0 && _blSY(_blOrder(j - 1)) > sy) { _blOrder(j) = _blOrder(j - 1); j -= 1 }
          _blOrder(j) = i
          _blN += 1
        }
      }
    }
    // A crowd of blasts draws fewer of the small things each: what distinguishes them — their
    // shapes, their glyphs — is kept whole
    val tier = (RenderQuality.tier: @scala.annotation.switch) match {
      case RenderQuality.HIGH => 1f
      case RenderQuality.MEDIUM => 0.85f
      case _ => 0.65f
    }
    _blDetail = tier * Math.max(0.5f, Math.min(1f, 8f / Math.max(1, _blN)))
  }

  /** One layer of every blast this frame: GROUND in the ground pass, under whoever stands in
    * them and the walls in front of them; AIR over everything. */
  def draw(layer: Int): Unit = {
    if (_blN == 0) return
    beginShapes()
    var j = 0
    while (j < _blN) {
      val i = _blOrder(j)
      GLBlastRenderers.draw(shapeBatch, _blStyle(i), layer, _blSX(i), _blSY(i), _blW(i), _blH(i),
        _blT(i), _blMs(i), _blSeed(i), _blDetail, _blCR(i), _blCG(i), _blCB(i))
      j += 1
    }
  }

  def updateDistortion(): Unit = {
    var bestStrength = 0f
    var bestCX = 0.5f
    var bestCY = 0.5f
    val lvx = client.visualPosX; val lvy = client.visualPosY
    var b = 0
    while (b < _blN) {
      val heavy = GLBlastRenderers.heaviness(_blStyle(b))
      val elapsed = _blMs(b)
      if (heavy > 0f && elapsed < 600f) { // the air shakes for its first 600ms
        // In UV space (0-1) for the shader, whose v runs up the screen: without the flip an
        // explosion above the player rippled the screen below them
        val uvX = _blSX(b) / canvasW.toFloat
        val uvY = 1f - _blSY(b) / canvasH.toFloat
        val dx = _blWX(b) - lvx; val dy = _blWY(b) - lvy
        val dist = Math.sqrt(dx * dx + dy * dy)
        if (dist < 8) { // only within 8 tiles of us
          val proximity = Math.max(0, 1.0 - dist / 8.0).toFloat
          val timeDecay = Math.max(0f, 1f - elapsed / 600f)
          val strength = 0.008f * heavy * proximity * timeDecay
          if (strength > bestStrength) {
            bestStrength = strength
            bestCX = uvX
            bestCY = uvY
          }
        }
      }
      b += 1
    }

    postProcessor.distortionStrength = bestStrength
    postProcessor.distortionCenterX = bestCX
    postProcessor.distortionCenterY = bestCY
  }

  /** The blasts' light in the light map. */
  def addLights(): Unit = {
    // Blasts light up what is round them, in their own colour, dying away as they do; a heavy
    // one flashes white as it goes off
    var b = 0
    while (b < _blN) {
      val st = _blStyle(b)
      val li = GLBlastRenderers.lightStrength(st)
      if (li > 0f) {
        val t = _blT(b)
        val decay = (1f - t) * (1f - t)
        val generic = st == GLBlastRenderers.S_GENERIC
        val lr = if (generic) _blCR(b) else GLBlastRenderers.lightR(st)
        val lg = if (generic) _blCG(b) else GLBlastRenderers.lightG(st)
        val lb = if (generic) _blCB(b) else GLBlastRenderers.lightB(st)
        lightSystem.addLight(_blSX(b), _blSY(b) - _blH(b) * 0.3f, _blW(b) * 1.5f * (0.6f + 0.4f * decay),
          lr, lg, lb, li * decay)
        if (_blMs(b) < 180f && GLBlastRenderers.heaviness(st) > 0f) {
          val flash = 1f - _blMs(b) / 180f
          lightSystem.addLight(_blSX(b), _blSY(b), _blW(b) * 1.2f * flash, 1f, 1f, 1f, 0.5f * flash)
        }
      }
      b += 1
    }
  }
}
