package com.gridgame.client.render
package world

import com.gridgame.common.Constants
import com.gridgame.common.model._

import com.gridgame.client.gl.{RenderQuality, TextureRegion}

/**
 * The ground's extras: the animated overlays on lava, energy fields and the like, water reflections,
 * and the shadows blocks cast on the ground beside them.
 */
private[render] final class TerrainPainter(ctx: RenderContext) {
  import ctx._

  // Tile overlay collection (parallel primitive arrays, reused each frame)
  private val MAX_SPECIAL_TILES = 512
  private val _specialTileWX = new Array[Int](MAX_SPECIAL_TILES)
  private val _specialTileWY = new Array[Int](MAX_SPECIAL_TILES)
  private val _specialTileTid = new Array[Int](MAX_SPECIAL_TILES)
  private var _specialTileCount = 0
  private val TILE_ANIM_SPEED = 15
  private val _shadowXs = new Array[Float](4)
  private val _shadowYs = new Array[Float](4)

  // Cached flipped TextureRegion for water reflections (avoids per-frame case class allocation)
  private var _reflectionRegion: TextureRegion = _
  private var _reflectionSourceRegion: TextureRegion = _

  // Pre-allocated polygon scratch shared by the status-effect / buff renderers
  private val _fxXs = new Array[Float](8)
  private val _fxYs = new Array[Float](8)

  /**
   * Stable 0..1 value per (tile, salt) — integer mix, no trig, so the tile-overlay pass can
   * call it several times per tile. Used to give each lava vent / ice nucleus / bubble its
   * own position and timing, so a field of one tile type doesn't animate in lockstep.
   */
  @inline private def tileHash(wx: Int, wy: Int, salt: Int): Float = {
    var h = wx * 374761393 + wy * 668265263 + salt * 1911520717
    h = (h ^ (h >>> 13)) * 1274126177
    ((h ^ (h >>> 16)) & 0xFFFF) * (1f / 65535f)
  }

  def drawOverlays(): Unit = {
    if (_specialTileCount == 0) return
    beginShapes()
    shapeBatch.setAdditiveBlend(true)
    val time = animTickF
    var i = 0
    while (i < _specialTileCount) {
      val wx = _specialTileWX(i); val wy = _specialTileWY(i); val tid = _specialTileTid(i)
      val sx = worldToScreenX(wx, wy).toFloat
      val sy = worldToScreenY(wx, wy).toFloat
      tid match {
        case 1 | 7 => // Water / DeepWater — shimmer, glints, spreading ripple rings
          val phase = time * 0.04f + wx * 1.1f + wy * 0.7f
          // Shimmer crescents sliding along the surface
          var j = 0
          while (j < 2) {
            val lp = phase + j * 2.1f
            val t = (lp % 3.0f) / 3.0f
            val lx = sx - HW * 0.55f + HW * 1.1f * t
            val ly = sy + HH * 0.35f - HH * 0.7f * t
            val sa = 0.24f * Math.abs(Math.sin(lp * 1.5).toFloat)
            shapeBatch.strokeLineSoft(lx - 5f, ly + 1.4f, lx + 5f, ly - 1.4f, 1.6f, 0.6f, 0.85f, 1f, sa)
            j += 1
          }
          // Sun glint skating over the water — a flare, not a dot
          val gp = time * 0.03f + wx * 2.3f + wy * 1.7f
          val glint = Math.sin(gp * 2.0).toFloat
          if (glint > 0.4f) {
            shapeBatch.fillStarFlare(sx + Math.sin(gp).toFloat * 6f, sy + Math.cos(gp * 0.7).toFloat * 2.5f,
              5.5f * glint, 1.1f, 0.5f, 0.55f, 0.85f, 0.97f, 1f, 0.28f * glint)
          }
          // Ripple ring spreading from a per-tile drip — staggered so only some tiles ripple
          val rt = (time * 0.012f + tileHash(wx, wy, 3)) % 1f
          if (rt < 0.55f) {
            val k = rt / 0.55f
            val rr = 2f + k * 13f
            val ra = 0.16f * (1f - k)
            shapeBatch.fillArcBand(sx, sy, rr, rr * 0.45f, rr + 1.7f, (rr + 1.7f) * 0.45f,
              0f, 6.2831853f, 6, 0.65f, 0.9f, 1f, ra, ra)
          }

        case 10 => // Lava — molten vent with glowing crust seams, blisters that pop, heat haze
          val lp = time * 0.05f + wx * 0.9f + wy * 1.3f
          shapeBatch.fillOvalSoft(sx, sy, HW.toFloat * 0.8f, HH.toFloat * 0.6f, 1f, 0.45f, 0.1f,
            (0.09f + 0.05f * Math.sin(lp)).toFloat, 0f, 10)
          // Vent, offset per tile so a lava field isn't a grid of identical cells
          val vx = sx + (tileHash(wx, wy, 1) - 0.5f) * 11f
          val vy = sy + (tileHash(wx, wy, 2) - 0.5f) * 5f
          // Crust seams: wedges radiating from the vent, brightest where the magma shows
          var j = 0
          while (j < 3) {
            val a = tileHash(wx, wy, j + 4) * 6.2831853f + Math.sin(lp * 0.4f + j).toFloat * 0.25f
            val len = 9f + 4f * Math.sin(lp * 1.7f + j * 2.1f).toFloat
            val ca = Math.cos(a).toFloat; val sa = Math.sin(a).toFloat * 0.5f
            val heat = clamp(0.24f + 0.13f * Math.sin(lp * 2.3f + j).toFloat)
            // Wide dull crust wedge with a hot core wedge inside it
            _fxXs(0) = vx + sa * 4.5f; _fxYs(0) = vy - ca * 2.2f
            _fxXs(1) = vx - sa * 4.5f; _fxYs(1) = vy + ca * 2.2f
            _fxXs(2) = vx + ca * len;  _fxYs(2) = vy + sa * len
            shapeBatch.fillPolygon(_fxXs, _fxYs, 3, 1f, 0.5f, 0.12f, heat)
            _fxXs(0) = vx + sa * 1.9f; _fxYs(0) = vy - ca * 0.95f
            _fxXs(1) = vx - sa * 1.9f; _fxYs(1) = vy + ca * 0.95f
            _fxXs(2) = vx + ca * len * 0.8f; _fxYs(2) = vy + sa * len * 0.8f
            shapeBatch.fillPolygon(_fxXs, _fxYs, 3, 1f, 0.88f, 0.45f, heat * 0.9f)
            j += 1
          }
          // Blister swelling at the vent, then bursting into a ring
          val bt = (time * 0.02f + tileHash(wx, wy, 7)) % 1f
          if (bt < 0.7f) {
            val k = bt / 0.7f
            val br = 1.5f + k * 4f
            shapeBatch.fillOvalSoft(vx, vy, br, br * 0.6f, 1f, 0.85f, 0.4f, 0.28f * (1f - k * 0.4f), 0f, 8)
          } else {
            val k = (bt - 0.7f) / 0.3f
            val rr = 5f + k * 7f
            shapeBatch.fillArcBand(vx, vy, rr, rr * 0.5f, rr + 1.6f, (rr + 1.6f) * 0.5f,
              0f, 6.2831853f, 6, 1f, 0.6f, 0.2f, 0.26f * (1f - k), 0.26f * (1f - k))
          }
          // Heat haze lifting off the surface
          val ht = (time * 0.018f + tileHash(wx, wy, 11)) % 1f
          shapeBatch.fillOvalSoft(vx + Math.sin(lp * 0.8f).toFloat * 3f, vy - 4f - ht * 13f,
            4f + ht * 4f, 2f + ht * 3f, 1f, 0.55f, 0.25f, 0.10f * (1f - ht), 0f, 8)
          // Occasional upward lava ember particles
          if (rng.nextFloat() < 0.02f) {
            val ex = sx + rng.nextFloat() * 16f - 8f
            val ey = sy + rng.nextFloat() * 4f - 2f
            combatParticles.emit(ex, ey,
              rng.nextFloat() * 4f - 2f, -(8f + rng.nextFloat() * 12f),
              plife = 0.6f + rng.nextFloat() * 0.4f,
              pr = 1f, pg = 0.5f + rng.nextFloat() * 0.3f, pb = 0.1f,
              palpha = 0.7f, psize = 1.5f + rng.nextFloat(),
              pgravity = -5f, additive = true, shrink = true, pkind = ParticleSystem.KIND_STREAK)
          }

        case 9 => // Ice — growing frost needles, a sheen sweep, twinkling glints
          val ip = time * 0.08f + wx * 3.7f + wy * 2.3f
          val nx = sx + (tileHash(wx, wy, 1) - 0.5f) * 10f
          val ny = sy + (tileHash(wx, wy, 2) - 0.5f) * 4f
          // Frost needles radiating from a nucleus, breathing in and out
          var j = 0
          while (j < 3) {
            val a = tileHash(wx, wy, j + 3) * 6.2831853f
            val len = 5f + 3.5f * Math.sin(ip * 0.6f + j * 2.1f).toFloat
            val ca = Math.cos(a).toFloat; val sa = Math.sin(a).toFloat * 0.5f
            _fxXs(0) = nx + sa * 2.6f; _fxYs(0) = ny - ca * 1.3f
            _fxXs(1) = nx - sa * 2.6f; _fxYs(1) = ny + ca * 1.3f
            _fxXs(2) = nx + ca * len;  _fxYs(2) = ny + sa * len
            shapeBatch.fillPolygon(_fxXs, _fxYs, 3, 0.72f, 0.9f, 1f, 0.22f)
            // Bright spine along the needle
            shapeBatch.strokeLine(nx, ny, nx + ca * len * 0.9f, ny + sa * len * 0.9f, 1f,
              0.92f, 0.98f, 1f, 0.24f)
            j += 1
          }
          shapeBatch.fillOvalSoft(nx, ny, 4.5f, 3f, 0.8f, 0.94f, 1f, 0.18f, 0f, 8)
          // Sheen sweeping across the facet
          val st = (time * 0.02f + tileHash(wx, wy, 9)) % 1f
          if (st < 0.35f) {
            val k = st / 0.35f
            val bx = sx - HW * 0.6f + HW * 1.2f * k
            val ba = 0.20f * Math.sin(k * 3.1415927f).toFloat
            shapeBatch.strokeLineSoft(bx - 4f, sy + 4f, bx + 4f, sy - 4f, 2.2f, 0.85f, 0.96f, 1f, ba)
          }
          // Glints on the crystal faces
          j = 0
          while (j < 2) {
            val flash = Math.sin(ip * 1.5f + j * 2.9f).toFloat
            if (flash > 0.55f) {
              val offX = (tileHash(wx, wy, j + 12) - 0.5f) * 14f
              val offY = (tileHash(wx, wy, j + 14) - 0.5f) * 6f
              shapeBatch.fillStarFlare(sx + offX, sy + offY, 4.5f, 1f, 0.7f, 0.5f,
                0.9f, 0.97f, 1f, 0.34f * (flash - 0.55f) / 0.45f)
            }
            j += 1
          }

        case 24 => // Crystal — prismatic facet cluster with a rotating light shaft
          val cp = time * 0.03f + wx * 1.3f + wy * 0.9f
          val cr = (0.5f + 0.5f * Math.sin(cp)).toFloat
          val cg = (0.5f + 0.5f * Math.sin(cp + 2.0943951f)).toFloat
          val cb = (0.5f + 0.5f * Math.sin(cp + 4.1887902f)).toFloat
          shapeBatch.fillOvalSoft(sx, sy, HW.toFloat * 0.6f, HH.toFloat * 0.5f, cr, cg, cb, 0.08f, 0f, 10)
          // Three facets, each refracting a different part of the spectrum
          var j = 0
          while (j < 3) {
            val a = j * 2.0943951f + cp * 0.25f
            val ca = Math.cos(a).toFloat; val sa = Math.sin(a).toFloat * 0.5f
            val fr = (0.5f + 0.5f * Math.sin(cp + j * 2.0943951f)).toFloat
            val fg = (0.5f + 0.5f * Math.sin(cp + j * 2.0943951f + 2.0943951f)).toFloat
            val fb = (0.5f + 0.5f * Math.sin(cp + j * 2.0943951f + 4.1887902f)).toFloat
            val len = 8.5f
            _fxXs(0) = sx;                _fxYs(0) = sy
            _fxXs(1) = sx + ca * len - sa * 3f; _fxYs(1) = sy + sa * len + ca * 1.5f
            _fxXs(2) = sx + ca * len + sa * 3f; _fxYs(2) = sy + sa * len - ca * 1.5f
            shapeBatch.fillPolygon(_fxXs, _fxYs, 3, fr, fg, fb, 0.16f)
            j += 1
          }
          // Light shaft sweeping off the cluster
          val shaftA = cp * 0.6f
          val sc = Math.cos(shaftA).toFloat; val ss = Math.sin(shaftA).toFloat * 0.5f
          _fxXs(0) = sx - ss * 2f; _fxYs(0) = sy + sc * 1f
          _fxXs(1) = sx + ss * 2f; _fxYs(1) = sy - sc * 1f
          _fxXs(2) = sx + sc * 16f; _fxYs(2) = sy + ss * 16f
          shapeBatch.fillPolygon(_fxXs, _fxYs, 3, 1f, 1f, 1f, 0.10f)
          // Twinkle at the apex
          val tw = Math.sin(cp * 2.2f).toFloat
          if (tw > 0.5f) shapeBatch.fillStarFlare(sx, sy - 3f, 7f, 1.3f, cp, 0.5f, 1f, 1f, 1f, 0.3f * tw)

        case 18 => // Toxic — bubbles surfacing and popping, sheen swirl, drifting spores
          val tp = time * 0.06f + wx * 1.7f + wy * 2.1f
          shapeBatch.fillOvalSoft(sx, sy, HW.toFloat * 0.7f, HH.toFloat * 0.5f, 0.2f, 0.9f, 0.1f,
            (0.05f + 0.04f * Math.sin(tp)).toFloat, 0f, 10)
          // Bubbles: rise, swell, then burst into a ring
          var j = 0
          while (j < 3) {
            val bt = (time * 0.016f + tileHash(wx, wy, j + 1)) % 1f
            val bx = sx + (tileHash(wx, wy, j + 5) - 0.5f) * 15f
            val by = sy + (tileHash(wx, wy, j + 8) - 0.5f) * 6f
            if (bt < 0.75f) {
              val k = bt / 0.75f
              val br = 1.2f + k * 3.2f
              shapeBatch.strokeOval(bx, by - k * 2f, br, br * 0.75f, 1f, 0.5f, 1f, 0.35f, 0.24f, 8)
              shapeBatch.fillOvalSoft(bx - br * 0.3f, by - k * 2f - br * 0.3f, br * 0.5f, br * 0.4f,
                0.8f, 1f, 0.6f, 0.20f, 0f, 6)
            } else {
              val k = (bt - 0.75f) / 0.25f
              val rr = 3.5f + k * 5f
              shapeBatch.fillArcBand(bx, by - 1.5f, rr, rr * 0.5f, rr + 1.3f, (rr + 1.3f) * 0.5f,
                0f, 6.2831853f, 6, 0.55f, 1f, 0.3f, 0.22f * (1f - k), 0.22f * (1f - k))
            }
            j += 1
          }
          // Spores drifting off the pool
          j = 0
          while (j < 2) {
            val spT = (time * 0.01f + tileHash(wx, wy, j + 16)) % 1f
            shapeBatch.fillDot(sx + Math.sin(tp * 0.7f + j * 2f).toFloat * 7f, sy - 2f - spT * 14f,
              1.1f, 0.6f, 1f, 0.4f, 0.16f * (1f - spT))
            j += 1
          }

        case 15 => // EnergyField — forked arcs strung between anchor nodes over a hex glow
          val ep = time * 0.12f + wx * 2.1f + wy * 1.3f
          val flicker = if (Math.sin(ep * 3.0) > 0.2) 1f else 0.35f
          // Containment hex pulsing on the tile floor
          val hexPulse = 0.06f + 0.04f * Math.sin(ep * 0.5f).toFloat
          var v = 0
          while (v < 6) {
            val va = v * 1.0471976f
            _fxXs(v) = sx + Math.cos(va).toFloat * 13f
            _fxYs(v) = sy + Math.sin(va).toFloat * 6.5f
            v += 1
          }
          shapeBatch.strokePolygon(_fxXs, _fxYs, 6, 1f, 0.55f, 0.3f, 1f, hexPulse * flicker)
          // Three anchor nodes with jagged arcs strung between them
          var j = 0
          while (j < 3) {
            val na = j * 2.0943951f + ep * 0.1f
            val ax = sx + Math.cos(na).toFloat * 9f
            val ay = sy + Math.sin(na).toFloat * 4.5f
            val nb = (j + 1) % 3 * 2.0943951f + ep * 0.1f
            val bx = sx + Math.cos(nb).toFloat * 9f
            val by = sy + Math.sin(nb).toFloat * 4.5f
            // Arc: two segments kinked off the midpoint, jittering every frame
            val mx = (ax + bx) * 0.5f + Math.sin(ep * 5f + j * 2.3f).toFloat * 4f
            val my = (ay + by) * 0.5f + Math.cos(ep * 7f + j * 1.7f).toFloat * 2.5f
            val arcA = 0.16f * flicker
            shapeBatch.strokeLine(ax, ay, mx, my, 1.1f, 0.65f, 0.35f, 1f, arcA)
            shapeBatch.strokeLine(mx, my, bx, by, 1.1f, 0.65f, 0.35f, 1f, arcA)
            shapeBatch.fillDot(ax, ay, 1.6f, 0.85f, 0.7f, 1f, 0.22f * flicker)
            j += 1
          }

        case _ => // shouldn't happen
      }
      i += 1
    }
    shapeBatch.setAdditiveBlend(false)
  }

  def drawReflections(): Unit = {
    if (!RenderQuality.waterReflections) return
    val world = client.getWorld
    val lvx = camera.visualX; val lvy = camera.visualY
    val localPX = Math.round(lvx).toInt
    val localPY = Math.round(lvy).toInt

    // Only check tiles within 2 cells of local player
    val range = 2
    var hasWater = false
    var wy = localPY - range
    while (wy <= localPY + range && !hasWater) {
      var wx = localPX - range
      while (wx <= localPX + range && !hasWater) {
        if (wx >= 0 && wx < world.width && wy >= 0 && wy < world.height) {
          val tid = world.getTile(wx, wy).id
          if (tid == 1 || tid == 7) hasWater = true
        }
        wx += 1
      }
      wy += 1
    }
    if (!hasWater) return

    // Get local player sprite for reflection
    val frame = if (client.getIsMoving) (animationTick / FRAMES_PER_STEP) % 4 else 0
    val region = GLSpriteGenerator.getSpriteRegion(client.getLocalDirection, frame, client.selectedCharacterId)
    if (region == null) return

    // Create flipped region (swap v and v2 for vertical flip) — cached to avoid per-frame allocation
    if (region ne _reflectionSourceRegion) {
      _reflectionSourceRegion = region
      _reflectionRegion = TextureRegion(region.texture, region.u, region.v2, region.u2, region.v)
    }
    val flippedRegion = _reflectionRegion
    val displaySz = Constants.PLAYER_DISPLAY_SIZE_PX.toFloat
    val playerScreenX = worldToScreenX(lvx, lvy).toFloat
    val playerScreenY = worldToScreenY(lvx, lvy).toFloat

    beginSprites()
    wy = localPY - range
    while (wy <= localPY + range) {
      var wx = localPX - range
      while (wx <= localPX + range) {
        if (wx >= 0 && wx < world.width && wy >= 0 && wy < world.height) {
          val tid = world.getTile(wx, wy).id
          if (tid == 1 || tid == 7) {
            val tileScreenX = worldToScreenX(wx, wy).toFloat
            val tileScreenY = worldToScreenY(wx, wy).toFloat
            val dx = tileScreenX - playerScreenX
            val dy = tileScreenY - playerScreenY
            if (Math.abs(dx) < 40 && Math.abs(dy) < 30) {
              // Shimmer distortion offset
              val shimmerOffset = Math.sin(animationTick * 0.08f + wx * 1.1f).toFloat * 2f
              val reflX = playerScreenX - displaySz / 2f + shimmerOffset
              val reflY = tileScreenY + 2f
              val dist = Math.sqrt(dx * dx + dy * dy).toFloat
              val reflAlpha = 0.15f * Math.max(0f, 1f - dist / 50f)
              if (reflAlpha > 0.01f) {
                spriteBatch.draw(flippedRegion, reflX, reflY, displaySz, displaySz * 0.6f,
                  0.6f, 0.7f, 0.9f, reflAlpha)
              }
            }
          }
        }
        wx += 1
      }
      wy += 1
    }
  }

  private def drawElevatedTileShadow(wx: Int, wy: Int, world: com.gridgame.common.model.WorldData): Unit = {
    val sx = worldToScreenX(wx, wy).toFloat
    val sy = worldToScreenY(wx, wy).toFloat
    val hw = HW.toFloat  // 20
    val hh = HH.toFloat  // 10

    // Single-layer shadow for ambient occlusion, on each edge of the block's diamond whose
    // neighbour is open ground. Neighbour (wx+1, wy) is down-right on screen and (wx, wy+1) down-left;
    // the checks used to be crossed, so a wall's base shadow went on the edge its next block covers
    // and was missing from the edge facing the open ground.
    val shadowDist = 5f; val shadowAlpha = 0.22f

    // Down-right edge, shared with (wx+1, wy)
    if (wx + 1 < world.width && world.getTile(wx + 1, wy).walkable) {
      _shadowXs(0) = sx; _shadowXs(1) = sx + hw; _shadowXs(2) = sx + hw; _shadowXs(3) = sx
      _shadowYs(0) = sy + hh; _shadowYs(1) = sy; _shadowYs(2) = sy + shadowDist; _shadowYs(3) = sy + hh + shadowDist
      shapeBatch.fillPolygon(_shadowXs, _shadowYs, 4, 0.0f, 0.0f, 0.03f, shadowAlpha)
    }
    // Down-left edge, shared with (wx, wy+1)
    if (wy + 1 < world.height && world.getTile(wx, wy + 1).walkable) {
      _shadowXs(0) = sx; _shadowXs(1) = sx - hw; _shadowXs(2) = sx - hw; _shadowXs(3) = sx
      _shadowYs(0) = sy + hh; _shadowYs(1) = sy; _shadowYs(2) = sy + shadowDist; _shadowYs(3) = sy + hh + shadowDist
      shapeBatch.fillPolygon(_shadowXs, _shadowYs, 4, 0.0f, 0.0f, 0.03f, shadowAlpha)
    }
    // Up-left edge, shared with (wx-1, wy) — subtler, and mostly behind the block
    if (wx - 1 >= 0 && world.getTile(wx - 1, wy).walkable) {
      _shadowXs(0) = sx; _shadowXs(1) = sx - hw; _shadowXs(2) = sx - hw; _shadowXs(3) = sx
      _shadowYs(0) = sy - hh; _shadowYs(1) = sy; _shadowYs(2) = sy - shadowDist; _shadowYs(3) = sy - hh - shadowDist
      shapeBatch.fillPolygon(_shadowXs, _shadowYs, 4, 0.0f, 0.0f, 0.03f, shadowAlpha * 0.4f)
    }
    // Up-right edge, shared with (wx, wy-1)
    if (wy - 1 >= 0 && world.getTile(wx, wy - 1).walkable) {
      _shadowXs(0) = sx; _shadowXs(1) = sx + hw; _shadowXs(2) = sx + hw; _shadowXs(3) = sx
      _shadowYs(0) = sy - hh; _shadowYs(1) = sy; _shadowYs(2) = sy - shadowDist; _shadowYs(3) = sy - hh - shadowDist
      shapeBatch.fillPolygon(_shadowXs, _shadowYs, 4, 0.0f, 0.0f, 0.03f, shadowAlpha * 0.4f)
    }
  }

  // The frame's animation frame for pools and moving blocks, how many frames a tile has, and how
  // many overlays the quality tier lets it draw
  private var tileFrame = 0
  private var numTileFrames = 1
  private var overlayBudget = 0
  private val cellH = Constants.TILE_CELL_HEIGHT

  def beginFrame(): Unit = {
    tileFrame = (animationTick / TILE_ANIM_SPEED) % GLTileRenderer.getNumFrames
    numTileFrames = GLTileRenderer.getNumFrames
    overlayBudget = Math.min(MAX_SPECIAL_TILES, RenderQuality.maxOverlayTiles)
    _specialTileCount = 0
  }

  /**
   * Everything that lies flat (TileForm): walkable ground, pools of water or lava, and the ground
   * each prop stands in, which its sprite leaves showing round it. Ground and props' ground use a
   * fixed variant per position so they don't animate; a pool cycles its frames. Every flat tile is
   * an opaque diamond meeting its neighbours exactly (GLTileRenderer.drawDiamond), so the ground
   * covers each pixel once, and needs no blending.
   */
  def drawGround(world: WorldData): Unit = {
    beginSprites()
    // Every flat tile is an opaque diamond meeting its neighbours exactly (GLTileRenderer.drawDiamond),
    // so the ground covers each pixel once, and needs no blending
    spriteBatch.setBlending(false)
    var wy = viewStartY
    while (wy <= viewEndY) {
      val loX = rowLo(wy, 0)
      val hiX = rowHi(wy, 0)
      var wx = loX
      while (wx <= hiX) {
        val cellTile = world.getTile(wx, wy)
        val form = cellTile.form
        val tile =
          if ((form eq TileForm.Ground) || (form eq TileForm.Pool)) cellTile
          else if (form eq TileForm.Prop) Tile.groundUnder(world, wx, wy)
          else null
        if (tile != null) {
          val tid = tile.id
          val variantFrame =
            if (form eq TileForm.Pool) (tileFrame + wx * 7 + wy * 13) % numTileFrames
            else ((wx * 7 + wy * 13) & 0x7FFFFFFF) % numTileFrames
          // Corners from the integer lattice (u = wx - wy across, v = wx + wy down), so the corner
          // two neighbours share is the same float in both and they meet without a gap
          val u = wx - wy; val v = wx + wy
          val sx = (u * HW + camOffX).toFloat
          val sy = (v * HH + camOffY).toFloat
          GLTileRenderer.drawDiamond(spriteBatch, tid, variantFrame,
            ((u - 1) * HW + camOffX).toFloat, sx, ((u + 1) * HW + camOffX).toFloat,
            ((v - 1) * HH + camOffY).toFloat, sy, ((v + 1) * HH + camOffY).toFloat)
          // Collect special tiles for overlay pass. Not water or ice: their tiles animate and
          // shine on their own, and the glints, ripples and frost needles drawn over them turned a
          // sea into static and a frozen pond into flocks of white birds.
          if (tid == 10 || tid == 15 || tid == 18 || tid == 24) {
            if (_specialTileCount < overlayBudget) {
              _specialTileWX(_specialTileCount) = wx
              _specialTileWY(_specialTileCount) = wy
              _specialTileTid(_specialTileCount) = tid
              _specialTileCount += 1
            }
          }
          // Lava and energy field tiles emit light (reuse sx/sy computed above)
          if (tid == 10) { // Lava
            val lavaPulse = (0.8 + 0.2 * Math.sin(animationTick * 0.06 + wx * 1.3 + wy * 0.7)).toFloat
            lightSystem.addLight(sx, sy, 40f, 1f, 0.5f, 0.1f, 0.12f * lavaPulse)
          } else if (tid == 15) { // EnergyField
            lightSystem.addLight(sx, sy, 35f, 0.5f, 0.2f, 0.8f, 0.08f)
          }
        }
        wx += 1
      }
      wy += 1
    }
    spriteBatch.setBlending(true)
  }

  /** The shadows blocks cast on the ground beside them, batched in one shapes pass. Blocks only: a
    * pool lies flat, and a prop carries its own round shadow in its sprite. */
  def drawBlockShadows(world: WorldData): Unit = {
    beginShapes()
    var wy = viewStartY
    while (wy <= viewEndY) {
      val loX = rowLo(wy, 0)
      val hiX = rowHi(wy, 0)
      var wx = loX
      while (wx <= hiX) {
        if (world.getTile(wx, wy).form eq TileForm.Block) {
          drawElevatedTileShadow(wx, wy, world)
        }
        wx += 1
      }
      wy += 1
    }
  }

  /** The block or prop standing on cell (wx, wy), if there is one: drawn in the depth pass, among
    * the entities. */
  def drawBlock(world: WorldData, wx: Int, wy: Int): Unit = {
    val tile = world.getTile(wx, wy)
    val form = tile.form
    if ((form eq TileForm.Block) || (form eq TileForm.Prop)) {
      // A block's frames are an animation; a prop's are four variants, picked by position
      val variantFrame =
        if (form eq TileForm.Prop) ((wx * 7 + wy * 13) & 0x7FFFFFFF) % numTileFrames
        else (tileFrame + wx * 7 + wy * 13) % numTileFrames
      val region = GLTileRenderer.getTrimmedRegion(tile.id, variantFrame)
      if (region != null) {
        beginSprites()
        val sx = worldToScreenX(wx, wy).toFloat
        val sy = worldToScreenY(wx, wy).toFloat
        val top = GLTileRenderer.getTrimTopPx(tile.id, variantFrame)
        spriteBatch.draw(region, sx - HW, sy - (cellH - HH) + top, tileW, tileCellH - top)
      }
    }
  }
}
