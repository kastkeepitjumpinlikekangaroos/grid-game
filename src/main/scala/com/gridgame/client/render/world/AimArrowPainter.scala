package com.gridgame.client.render
package world

import com.gridgame.client.game.TerrainImpact
import com.gridgame.common.model._

/**
 * Where a charged shot will go, laid on the ground while we charge it (render/CLAUDE.md, *A charged
 * shot's aim is a lane on the ground*): a lane from our feet to where the shot would land if we let
 * go now, chevrons running out along it, a ring where it lands, and dots on to where a full charge
 * would carry it. It stops where the shot would, at the first thing that stops a shot, and it is in
 * the gold of the charge gauge round our feet, brightening as the charge builds. Everything is inked,
 * so it reads on sand and snow, and on a dark map it lights the ground it runs over.
 */
private[render] final class AimArrowPainter(ctx: RenderContext) {
  import ctx._
  import AimArrowPainter._

  // Where the lane runs from and which way, in the world, for sx/sy
  private var _ox = 0.0; private var _oy = 0.0
  private var _ux = 0.0; private var _uy = 0.0

  // How far the chevrons have run, and the frame it was worked out on; when the charge filled
  private var _flow = 0f
  private var _flowAt = 0L
  private var _fullSince = 0L

  private val _xs = new Array[Float](3)
  private val _ys = new Array[Float](3)
  private val _ws = new Array[Float](3)
  private val _as = new Array[Float](3)

  /** A point `along` cells out from our feet and `across` cells to the left of the aim, on screen. */
  private def sx(along: Float, across: Float): Float =
    worldToScreenX(_ox + _ux * along - _uy * across, _oy + _uy * along + _ux * across).toFloat
  private def sy(along: Float, across: Float): Float =
    worldToScreenY(_ox + _ux * along - _uy * across, _oy + _uy * along + _ux * across).toFloat

  def draw(): Unit = {
    if (!client.isCharging || client.getIsDead) { _flowAt = 0L; _fullSince = 0L; return }
    val pDef = ProjectileDef.get(client.getSelectedCharacterDef.primaryProjectileType)
    if (pDef.chargeSpeedScaling.isEmpty) return

    val pos = client.getLocalPosition
    val gridX = pos.getX.toDouble; val gridY = pos.getY.toDouble
    val adx = client.getMouseWorldX - gridX; val ady = client.getMouseWorldY - gridY
    val dist = Math.sqrt(adx * adx + ady * ady)
    if (dist < 0.01) return
    // From where we are drawn, so the lane stays under our feet as we walk
    _ox = if (camera.visualX.isNaN) gridX else camera.visualX
    _oy = if (camera.visualY.isNaN) gridY else camera.visualY
    _ux = adx / dist; _uy = ady / dist

    // How far the shot goes from our feet: it is fired from a cell out and flies its range from there
    val level = client.getChargeLevel
    val t = level / 100f
    val crs = pDef.chargeRangeScaling
    val reach = 1f + (if (crs.isDefined) crs.get.interpolate(level) else pDef.maxRange.toFloat)
    val fullReach = 1f + (if (crs.isDefined) crs.get.max else pDef.maxRange.toFloat)
    val wall = stopAlong(client.getWorld, _ox, _oy, _ux, _uy, fullReach, pDef.passesThroughWalls)
    val blocked = wall < reach
    val end = if (blocked) wall else reach

    // The chevrons run on the frame's clock, faster as the charge builds
    val now = frameTimeMs
    val dt = if (_flowAt == 0L) 0f else Math.max(0f, Math.min(0.1f, (now - _flowAt) / 1000f))
    _flowAt = now
    _flow = (_flow + (FLOW_SLOW + (FLOW_FAST - FLOW_SLOW) * t) * dt) % CHEV_SPACING
    if (level < 100) _fullSince = 0L else if (_fullSince == 0L) _fullSince = now

    // The gauge's gold, deeper at the start and bright at full, but never the gauge's white-hot: on
    // snow a white lane is only its ink. `l` is the same lit a little, for the lines, `f` paler for
    // the band between them, and `i` the ink
    val cg = 0.64f + 0.26f * t; val cb = 0.14f + 0.28f * t
    val lg = cg + (1f - cg) * 0.3f; val lb = cb + (1f - cb) * 0.3f
    val fg = 0.5f + 0.5f * cg; val fb = 0.5f + 0.5f * cb
    val ir = 0.22f; val ig = 0.12f; val ib = 0.04f

    // On a dark map it lights the ground it runs over, as a shot does: away from our own light the
    // light map dims the gold to khaki. Close together, or the glow comes out in beads. Not in
    // daylight (RenderContext.projectileLights)
    if (projectileLights) {
      var d = LANE_START + LIGHT_SPACING * 0.5f
      while (d < end) {
        lightSystem.addLight(sx(d, 0f), sy(d, 0f), 34f, 1f, cg, cb, 0.10f + 0.05f * t)
        d += LIGHT_SPACING
      }
      if (!blocked) lightSystem.addLight(sx(end, 0f), sy(end, 0f), 40f, 1f, cg, cb, 0.16f + 0.08f * t)
    }

    beginShapes()

    // The lane: a pale band, brighter down its middle, between two inked edges. It stops at the
    // landing ring, or at the wall
    val laneEnd = if (blocked) end else end - RING_R
    if (laneEnd > LANE_START + 0.05f) {
      val fadeTo = Math.min(laneEnd, LANE_START + LANE_FADE)
      band(laneEnd, fadeTo, 2f * LANE_HALF, 1f, fg, fb, 0.14f + 0.08f * t)
      band(laneEnd, fadeTo, LANE_HALF, 1f, fg, fb, 0.12f + 0.08f * t)
      edge(laneEnd, fadeTo, LANE_HALF, 3.2f, ir, ig, ib, 0.55f)
      edge(laneEnd, fadeTo, -LANE_HALF, 3.2f, ir, ig, ib, 0.55f)
      edge(laneEnd, fadeTo, LANE_HALF, 1.4f, 1f, lg, lb, 0.95f)
      edge(laneEnd, fadeTo, -LANE_HALF, 1.4f, 1f, lg, lb, 0.95f)

      // Chevrons running out along it, fading in at its start and out before its end
      var d = LANE_START + _flow
      while (d < laneEnd) {
        val a = Math.min(1f, (d - LANE_START) / LANE_FADE) * Math.min(1f, (laneEnd - d) / 0.6f)
        if (a > 0.02f) chevron(d, 4.6f, ir, ig, ib, 0.55f * a, 2.4f, 1f, lg, lb, a)
        d += CHEV_SPACING
      }
    }

    if (blocked) {
      // Stopped short: a bar across the lane at the wall's face
      bar(end, LANE_HALF + 0.1f, 4f, ir, ig, ib, 0.5f)
      bar(end, LANE_HALF + 0.1f, 2f, 1f, lg, lb, 0.95f)
      return
    }

    // Where it lands: a ring on the ground with a dot at its heart
    ring(end, RING_R, 4.4f, ir, ig, ib, 0.55f)
    ring(end, RING_R, 2.4f, 1f, lg, lb, 1f)
    val cx = sx(end, 0f); val cy = sy(end, 0f)
    shapeBatch.fillOval(cx, cy, 3.4f, 2.1f, ir, ig, ib, 0.55f, 10)
    shapeBatch.fillOval(cx, cy, 2.3f, 1.4f, 1f, lg, lb, 1f, 10)
    if (_fullSince != 0L) {
      // Full: a ripple breathing out off the ring, as the gauge's does round our feet
      val rp = ((now - _fullSince) % READY_MS) / READY_MS.toFloat
      ring(end, RING_R + 0.55f * rp, 2f * (1f - rp) + 0.4f, 1f, lg, lb, 0.7f * (1f - rp))
    }

    // On to where a full charge would land, as dots, up to a wall if one is in the way
    val ghostEnd = Math.min(wall, fullReach)
    var d = end + RING_R + DOT_SPACING * 0.5f
    while (d < ghostEnd - END_R * 0.5f) {
      val dx = sx(d, 0f); val dy = sy(d, 0f)
      shapeBatch.fillOval(dx, dy, 3.0f, 1.8f, ir, ig, ib, 0.40f, 10)
      shapeBatch.fillOval(dx, dy, 1.9f, 1.1f, 1f, lg, lb, 0.80f, 10)
      d += DOT_SPACING
    }
    if (ghostEnd - end > RING_R + END_R) {
      if (wall < fullReach) bar(ghostEnd, END_R + 0.05f, 1.2f, 1f, lg, lb, 0.55f)
      else {
        ring(ghostEnd, END_R, 3.0f, ir, ig, ib, 0.40f)
        ring(ghostEnd, END_R, 1.4f, 1f, lg, lb, 0.80f)
      }
    }
  }

  /** The lane's body, `width` cells across, from its start to `to`, fading in up to `fadeTo`. A band
    * on the ground is as wide on screen as the aim's direction leaves it: the projection scales areas
    * by 2·HW·HH, so its width across the aim is that over the aim's length on screen. */
  private def band(to: Float, fadeTo: Float, width: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    val slx = (_ux - _uy) * HW; val sly = (_ux + _uy) * HH
    val w = (width * 2.0 * HW * HH / Math.sqrt(slx * slx + sly * sly)).toFloat
    lanePoints(to, fadeTo, 0f)
    stroke(w, r, g, b, a)
  }

  /** One edge of the lane, `across` cells off its middle, fading in as the lane does. */
  private def edge(to: Float, fadeTo: Float, across: Float, lineWidth: Float,
                   r: Float, g: Float, b: Float, a: Float): Unit = {
    lanePoints(to, fadeTo, across)
    stroke(lineWidth, r, g, b, a)
  }

  private def lanePoints(to: Float, fadeTo: Float, across: Float): Unit = {
    _xs(0) = sx(LANE_START, across); _ys(0) = sy(LANE_START, across)
    _xs(1) = sx(fadeTo, across); _ys(1) = sy(fadeTo, across)
    _xs(2) = sx(to, across); _ys(2) = sy(to, across)
  }

  /** The three lane points as one band `w` wide, clear at the lane's start and `a` from its fade on. */
  private def stroke(w: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    _ws(0) = w; _ws(1) = w; _ws(2) = w
    _as(0) = 0f; _as(1) = a; _as(2) = a
    shapeBatch.strokePolylineVar(_xs, _ys, _ws, _as, 3, r, g, b)
  }

  /** A chevron lying on the ground, pointing out along the lane from `along` cells out: inked, then lit. */
  private def chevron(along: Float, inkW: Float, ir: Float, ig: Float, ib: Float, inkA: Float,
                      w: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    val back = along - CHEV_LEN
    _xs(0) = sx(back, CHEV_HALF); _ys(0) = sy(back, CHEV_HALF)
    _xs(1) = sx(along, 0f); _ys(1) = sy(along, 0f)
    _xs(2) = sx(back, -CHEV_HALF); _ys(2) = sy(back, -CHEV_HALF)
    shapeBatch.strokePolyline(_xs, _ys, 3, inkW, ir, ig, ib, inkA)
    shapeBatch.strokePolyline(_xs, _ys, 3, w, r, g, b, a)
  }

  /** A bar across the aim, `along` cells out, reaching `half` cells either side. */
  private def bar(along: Float, half: Float, w: Float, r: Float, g: Float, b: Float, a: Float): Unit =
    shapeBatch.strokeLine(sx(along, half), sy(along, half), sx(along, -half), sy(along, -half), w, r, g, b, a)

  /** A ring of `radius` cells lying on the ground round the point `along` cells out: a circle on the
    * ground is an ellipse twice as wide as it is tall on screen. */
  private def ring(along: Float, radius: Float, w: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    val rx = (radius * HW * Math.sqrt(2.0)).toFloat
    shapeBatch.strokeOval(sx(along, 0f), sy(along, 0f), rx, rx * HH / HW, w, r, g, b, a, 24)
  }
}

private[render] object AimArrowPainter {
  // The lane's layout along the aim, in cells from our feet
  private val LANE_START = 0.85f  // clear of the gauge's ring round our feet
  private val LANE_FADE = 0.9f    // it fades in over this much from its start
  private val LANE_HALF = 0.3f    // half its width
  private val CHEV_SPACING = 1.15f
  private val CHEV_LEN = 0.26f    // a chevron's point to the ends of its arms
  private val CHEV_HALF = 0.22f   // how far either arm reaches across the lane
  private val RING_R = 0.5f       // the landing ring
  private val DOT_SPACING = 0.6f
  private val END_R = 0.2f        // the small ring where a full charge would land
  private val LIGHT_SPACING = 1.2f
  // How fast the chevrons run, in cells a second, from the start of a charge to full
  private val FLOW_SLOW = 1.6f
  private val FLOW_FAST = 4.0f
  private val READY_MS = 900L     // a full charge's ripple off the landing ring
  // The terrain is looked at this far apart along the aim, and a face found between two looks
  private val PROBE_STEP = 0.1f

  /**
   * How far a shot fired from (ox, oy) along the unit (ux, uy) goes before something stops it — what
   * stops one in flight (TerrainImpact.blocks: walls, props and the map's edge, and for one that flies
   * over walls only fences and the edge, but never water) — looking `limit` cells out; `limit` if
   * nothing does. The face is found to a few hundredths of a cell.
   */
  def stopAlong(world: WorldData, ox: Double, oy: Double, ux: Double, uy: Double, limit: Float,
                passesWalls: Boolean): Float = {
    if (world == null) return limit
    @inline def stops(d: Float): Boolean = TerrainImpact.blocks(world,
      TerrainImpact.cellOf((ox + ux * d).toFloat), TerrainImpact.cellOf((oy + uy * d).toFloat), passesWalls)
    var clear = 0f
    while (clear < limit) {
      val d = clear + PROBE_STEP
      if (stops(d)) {
        // The face lies between the last look that was clear and this one
        var lo = clear; var hi = d
        var k = 0
        while (k < 5) {
          val mid = (lo + hi) * 0.5f
          if (stops(mid)) hi = mid else lo = mid
          k += 1
        }
        return Math.min(lo, limit)
      }
      clear = d
    }
    limit
  }
}
