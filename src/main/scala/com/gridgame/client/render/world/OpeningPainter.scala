package com.gridgame.client.render
package world

import com.gridgame.client.i18n.Messages
import com.gridgame.common.Constants
import com.gridgame.common.model._

/**
 * The match's opening (MatchOpening): the Teams wall standing in the world down the middle of the map,
 * and the countdown the HUD shows under the match clock. The wall beats through its last seconds, as
 * the countdown does.
 */
private[render] final class OpeningPainter(ctx: RenderContext) {
  import ctx._

  private val DIVIDER_HEIGHT_PX = 52f
  private val DIVIDER_RAISE_MS = 700f
  private val DIVIDER_FADE_MS = 900f
  private val DIVIDER_IMPACT_MS = 420f

  // Half the width of the strip it glows in along the ground, and how often a rib of light
  // stands in it, both in cells
  private val DIVIDER_FOOT_CELLS = 0.42f
  private val DIVIDER_RIB_CELLS = 1.0f

  // The sheet is cut into pieces this long so the light in it can travel along its length
  private val DIVIDER_CHUNK_CELLS = 5f

  // The last seconds before it drops, when the whole wall beats
  private val DIVIDER_WARN_MS = 3000f
  private val _divQuadXs = new Array[Float](4)
  private val _divQuadYs = new Array[Float](4)

  /**
   * The wall between the two teams while a match opens ([[TeamDivider]]), drawn after the entity
   * pass like a held barrier: a sheet of amber light standing on the ground the whole way across
   * the map, on the one line the server will not let anything cross.
   *
   * Only the part of the line the screen can see is drawn, clipped in the projection's own axes
   * against the same bounds the tiles are culled with — the line itself is straight in world
   * space, so it is straight on screen and one quad covers a whole stretch of it.
   */
  def drawWall(uMin: Double, uMax: Double, vMin: Double, vMax: Double): Unit = {
    val d = client.divider
    if (d == null) return
    val world = client.getWorld
    if (world == null) return
    val now = frameTimeMs
    val raisedAt = d.endsAt - Constants.MATCH_OPENING_MS
    val raise = clamp((now - raisedAt) / DIVIDER_RAISE_MS)
    val fade = if (now < d.endsAt) 1f else clamp(1f - (now - d.endsAt) / DIVIDER_FADE_MS)
    val vis = (1f - (1f - raise) * (1f - raise)) * fade
    if (vis <= 0.01f) return

    // The visible stretch of the line, in the axes the tiles are culled in (u = wx - wy is
    // screen x, v = wx + wy is screen y): along a column x = at, a point is (at, t)
    val at = d.at.toFloat
    val lo = if (d.axisX) Math.max(at - uMax, vMin - at) else Math.max(uMin + at, vMin - at)
    val hi = if (d.axisX) Math.min(at - uMin, vMax - at) else Math.min(uMax + at, vMax - at)
    val span = (if (d.axisX) world.height else world.width) - 1
    val tLo = Math.max(-0.5, lo).toFloat
    val tHi = Math.min(span + 0.5, hi).toFloat
    if (tHi - tLo <= 0.01f) return

    // Amber: a starting gate, not somebody's shield (those are blue and red)
    val warn = if (d.endsAt - now < DIVIDER_WARN_MS && now < d.endsAt) {
      val beat = (Math.sin(now * 0.012).toFloat + 1f) * 0.5f
      0.35f * beat
    } else 0f
    val cr = 1f; val cg = 0.70f + 0.1f * warn; val cb = 0.16f
    val lr = 1f; val lg = 0.92f; val lb = 0.55f
    val h = DIVIDER_HEIGHT_PX * (0.12f + 0.88f * vis)

    beginShapes()
    shapeBatch.setAlphaMultiplier(vis)

    // Where a shot struck it lately: a flash on the sheet, and the whole wall picks up the glow
    var flare = 0f
    var k = 0
    while (k < client.DIVIDER_IMPACT_SLOTS) {
      val struck = client.getDividerImpactTime(k)
      val age = now - struck
      if (struck > 0L && age >= 0L && age < DIVIDER_IMPACT_MS) {
        val t = if (d.axisX) client.getDividerImpactY(k) else client.getDividerImpactX(k)
        if (t >= tLo && t <= tHi) {
          val fadeOut = 1f - age / DIVIDER_IMPACT_MS
          flare = Math.max(flare, fadeOut * 0.6f)
          val fx = dividerScreenX(d, at, t)
          val fy = dividerScreenY(d, at, t) - h * 0.5f
          shapeBatch.fillOval(fx, fy, 5f + 14f * (1f - fadeOut), h * 0.45f, lr, lg, lb, 0.5f * fadeOut * fadeOut, 12)
          shapeBatch.strokeOval(fx, fy, 6f + 26f * (1f - fadeOut), h * (0.25f + 0.3f * (1f - fadeOut)),
            1.4f, lr, lg, lb, 0.8f * fadeOut, 14)
        }
      }
      k += 1
    }

    // A light every few chunks, so the wall throws its colour onto the ground beside it
    val chunks = Math.ceil((tHi - tLo) / DIVIDER_CHUNK_CELLS).toInt
    val step = (tHi - tLo) / chunks
    val climb = ((now % 1600L) / 1600f)
    var i = 0
    while (i < chunks) {
      val t0 = tLo + step * i
      val t1 = t0 + step
      val x0 = dividerScreenX(d, at, t0); val y0 = dividerScreenY(d, at, t0)
      val x1 = dividerScreenX(d, at, t1); val y1 = dividerScreenY(d, at, t1)

      // Its footprint on the ground: a strip in the ground plane, which is what still shows the
      // wall where the sheet is seen edge on
      val nx = if (d.axisX) DIVIDER_FOOT_CELLS else 0f
      val ny = if (d.axisX) 0f else DIVIDER_FOOT_CELLS
      val ax = if (d.axisX) at else t0; val ay = if (d.axisX) t0 else at
      val bx = if (d.axisX) at else t1; val by = if (d.axisX) t1 else at
      _divQuadXs(0) = worldToScreenX(ax + nx, ay + ny).toFloat; _divQuadYs(0) = worldToScreenY(ax + nx, ay + ny).toFloat
      _divQuadXs(1) = worldToScreenX(bx + nx, by + ny).toFloat; _divQuadYs(1) = worldToScreenY(bx + nx, by + ny).toFloat
      _divQuadXs(2) = worldToScreenX(bx - nx, by - ny).toFloat; _divQuadYs(2) = worldToScreenY(bx - nx, by - ny).toFloat
      _divQuadXs(3) = worldToScreenX(ax - nx, ay - ny).toFloat; _divQuadYs(3) = worldToScreenY(ax - nx, ay - ny).toFloat
      shapeBatch.fillPolygon(_divQuadXs, _divQuadYs, 4, cr, cg, cb, 0.20f + 0.18f * (warn + flare))

      // The sheet: four bands rising off the ground, so it is dense where it stands and thins
      // out toward its top — one flat quad up the whole height reads as a ribbon, not a wall
      val lift = 0.065f + 0.05f * (warn + flare)
      dividerBand(x0, y0, x1, y1, 0f, h, cr, cg, cb, lift)
      dividerBand(x0, y0, x1, y1, 0f, h * 0.70f, cr, cg, cb, lift)
      dividerBand(x0, y0, x1, y1, 0f, h * 0.40f, cr, cg, cb, lift)
      dividerBand(x0, y0, x1, y1, 0f, h * 0.15f, cr, cg, cb, lift)
      val wave = (i.toFloat / chunks + climb) % 1f
      if (wave < 0.18f) dividerBand(x0, y0, x1, y1, 0f, h, lr, lg, lb, 0.10f * (1f - wave / 0.18f))

      // A hard line along its top and a glow where it meets the ground, not a line at both: two
      // bright parallel edges with an even fill between them is a road, not a wall
      shapeBatch.strokeLineSoft(x0, y0, x1, y1, 6f, cr, cg, cb, 0.22f + 0.25f * (warn + flare))
      shapeBatch.strokeLineSoft(x0, y0 - h, x1, y1 - h, 5f, cr, cg, cb, 0.30f + 0.3f * (warn + flare))
      shapeBatch.strokeLine(x0, y0 - h, x1, y1 - h, 2.1f, lr, lg, lb, 0.95f)

      if ((i & 1) == 0) {
        lightSystem.addLight((x0 + x1) * 0.5f, (y0 + y1) * 0.5f - h * 0.5f, 70f, cr, cg, cb,
          (0.10f + 0.10f * (warn + flare)) * vis)
      }
      i += 1
    }

    // Ribs standing in the sheet, drifting along it. They are what makes the eye read a surface
    // standing up out of the ground rather than light lying on it.
    val drift = ((now % 3000L) / 3000f) * DIVIDER_RIB_CELLS
    var t = Math.ceil((tLo - drift) / DIVIDER_RIB_CELLS).toFloat * DIVIDER_RIB_CELLS + drift
    while (t < tHi) {
      val sx = dividerScreenX(d, at, t)
      val sy = dividerScreenY(d, at, t)
      shapeBatch.strokeLine(sx, sy, sx, sy - h * 0.92f, 1.4f, lr, lg, lb, 0.30f + 0.2f * warn)
      shapeBatch.strokeLine(sx, sy - h * 0.92f, sx, sy - h, 2.4f, lr, lg, lb, 0.5f + 0.3f * warn)
      t += DIVIDER_RIB_CELLS
    }

    shapeBatch.setAlphaMultiplier(1f)
  }

  // How long the word that the opening is over stays up
  private val DIVIDER_GO_MS = 1400L
  private var _cachedOpeningSecs = -1
  private var _cachedOpeningStr = ""

  /**
   * Under the match clock while the match opens ([[MatchOpening]]): how long is left of it, and a
   * beat of FIGHT! when it ends. It says the same thing whichever opening it is — behind a wall or
   * holding your fire, the battle starts when the countdown does. The bar empties as it runs out.
   */
  def drawCountdown(screenW: Int): Unit = {
    val rules = client.openingRulesNow
    if (rules == 0) return
    val now = frameTimeMs
    val left = client.openingMsLeft
    val since = now - client.openingEndsAtMs
    val counting = left > 0L
    if (!counting && (since < 0L || since > DIVIDER_GO_MS)) return

    // The same words either way: behind a wall or holding your fire, the battle starts when the
    // countdown does
    if (counting) {
      val secs = ((left + 999L) / 1000L).toInt
      if (secs != _cachedOpeningSecs) {
        _cachedOpeningSecs = secs
        _cachedOpeningStr = Messages.t("Battle begins in {0}", secs)
      }
    } else if (_cachedOpeningSecs != 0) {
      _cachedOpeningSecs = 0
      _cachedOpeningStr = Messages.t("FIGHT!")
    }
    val text = _cachedOpeningStr
    val go = if (counting) 0f else clamp(1f - since / DIVIDER_GO_MS.toFloat)
    val beat = if (counting && left < DIVIDER_WARN_MS) (Math.sin(now * 0.012).toFloat + 1f) * 0.5f else 0f

    val textW = fontMedium.measureWidth(text)
    val w = textW + 36f
    val hgt = 26f
    val x = (screenW / 2f - w / 2f)
    val y = 42f

    beginShapes()
    shapeBatch.fillRoundedRectGradient(x, y, w, hgt, 7f,
      0.20f + 0.18f * (beat + go), 0.11f, 0.01f, 0.72f,
      0.10f + 0.10f * (beat + go), 0.05f, 0.01f, 0.62f)
    shapeBatch.strokeRect(x, y, w, hgt, 1f, 1f, 0.72f + 0.2f * go, 0.2f, 0.35f + 0.4f * (beat + go))
    if (counting) {
      // What is left of the opening, emptying left to right
      val frac = clamp(left.toFloat / Constants.MATCH_OPENING_MS)
      shapeBatch.fillRect(x + 6f, y + hgt - 5f, (w - 12f) * frac, 2f, 1f, 0.72f, 0.2f, 0.75f)
      shapeBatch.fillRect(x + 6f, y + hgt - 5f, w - 12f, 2f, 1f, 0.72f, 0.2f, 0.15f)
    }

    beginSprites()
    val ty = y + (hgt - fontMedium.charHeight) / 2f
    fontMedium.drawTextOutlined(spriteBatch, text, screenW / 2f - textW / 2f, ty,
      1f, 0.85f - 0.15f * beat, 0.45f + 0.3f * go)
  }

  @inline private def dividerScreenX(d: TeamDivider, at: Float, t: Float): Float =
    (if (d.axisX) worldToScreenX(at, t) else worldToScreenX(t, at)).toFloat

  @inline private def dividerScreenY(d: TeamDivider, at: Float, t: Float): Float =
    (if (d.axisX) worldToScreenY(at, t) else worldToScreenY(t, at)).toFloat

  /** One piece of the divider's sheet, from (x0, y0)-(x1, y1) on the ground, `lo` to `hi` px up. */
  private def dividerBand(x0: Float, y0: Float, x1: Float, y1: Float, lo: Float, hi: Float,
                          r: Float, g: Float, b: Float, a: Float): Unit = {
    _divQuadXs(0) = x0; _divQuadYs(0) = y0 - lo
    _divQuadXs(1) = x1; _divQuadYs(1) = y1 - lo
    _divQuadXs(2) = x1; _divQuadYs(2) = y1 - hi
    _divQuadXs(3) = x0; _divQuadYs(3) = y0 - hi
    shapeBatch.fillPolygon(_divQuadXs, _divQuadYs, 4, r, g, b, a)
  }
}
