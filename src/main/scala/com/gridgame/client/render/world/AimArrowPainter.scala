package com.gridgame.client.render
package world

import com.gridgame.client.game.{Attacks, TerrainImpact}
import com.gridgame.common.model._
import com.gridgame.common.protocol.AttackSlot

/**
 * Where our attack will go, laid on the ground while we aim it (render/CLAUDE.md, *An aimed attack
 * shows where it will go*, and *A charged shot's aim is a lane on the ground*).
 *
 * A charged shot: a lane from our feet to where the shot would land if we let go now, chevrons
 * running out along it, a ring where it lands, and dots on to where a full charge would carry it, in
 * the gold of the charge gauge round our feet, brightening as the charge builds.
 *
 * Any other attack being aimed (Attacks: Q or E from its key to the click that casts it, the primary
 * held until its button comes up): a shot is the same lane to the end of its range, with its blast's
 * reach where it goes off; a fan, a spoke for each of its projectiles; a dash, the lane to where it
 * ends and a ring for how far it can go; a blink, dots to where it lands; a trap, where it lands and
 * the ground it watches. Each is worked out by what the cast itself does (AimArrowPainter.stopAlong
 * for a shot, GameClient.dashLanding, blinkLanding and trapLanding). While it couldn't go off (the
 * primary's cooldown, a ceasefire, a hold) it is grey and dimmer. An ability's shows as its key goes
 * down, and fades in; the primary's after SHOW_AFTER_MS held, so a click shows nothing.
 *
 * Every aim stops where its attack would, at the first thing that stops it. Everything is inked, so
 * it reads on sand and snow, and on a dark map a lane lights the ground it runs over.
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

  // The same for an aimed attack's lane, apart, so a charge's lane is drawn as it always was
  private var _heldFlow = 0f
  private var _heldFlowAt = 0L

  // An aim's colours this frame: its lit lines, the pale fill between them, and its ink; and how
  // opaque it is, fading in and dimmer while the attack couldn't go off
  private var _lr = 1f; private var _lg = 1f; private var _lb = 1f
  private var _fr = 1f; private var _fg = 1f; private var _fb = 1f
  private var _ir = 0f; private var _ig = 0f; private var _ib = 0f
  private var _alpha = 1f
  // ...and the light it casts on a dark map: gold, or white while it couldn't go off
  private var _hg = 1f; private var _hb = 1f

  private val _xs = new Array[Float](3)
  private val _ys = new Array[Float](3)
  private val _ws = new Array[Float](3)
  private val _as = new Array[Float](3)
  private val _arcX = new Array[Float](ARC_POINTS)
  private val _arcY = new Array[Float](ARC_POINTS)

  /** A point `along` cells out from our feet and `across` cells to the left of the aim, on screen. */
  private def sx(along: Float, across: Float): Float =
    worldToScreenX(_ox + _ux * along - _uy * across, _oy + _uy * along + _ux * across).toFloat
  private def sy(along: Float, across: Float): Float =
    worldToScreenY(_ox + _ux * along - _uy * across, _oy + _uy * along + _ux * across).toFloat

  def draw(): Unit = {
    if (client.getIsDead) { _flowAt = 0L; _fullSince = 0L; _heldFlowAt = 0L; return }
    // A charge is the primary held: drawn unless Q or E has been pressed since
    val slot = client.aimingSlot
    if (client.isCharging && (slot < 0 || slot == AttackSlot.PRIMARY)) {
      _heldFlowAt = 0L
      drawCharge()
      return
    }
    _flowAt = 0L; _fullSince = 0L
    if (slot >= 0) drawHeld(slot) else _heldFlowAt = 0L
  }

  private def drawCharge(): Unit = {
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

  // ── An attack being aimed ───────────────────────────────────────────────

  /** The aim of the attack being aimed in `slot` (an AttackSlot), as its cast will work it out. */
  private def drawHeld(slot: Int): Unit = {
    // A click shows nothing: the primary is aimed once held a moment. An ability is aimed from its key
    val shownMs = client.aimedForMs(slot) - (if (slot == AttackSlot.PRIMARY) SHOW_AFTER_MS else 0L)
    if (shownMs < 0L) { _heldFlowAt = 0L; return }
    val charDef = client.getSelectedCharacterDef
    val ability =
      if (slot == AttackSlot.Q) charDef.qAbility else if (slot == AttackSlot.E) charDef.eAbility else null
    // A primary that charges shows its charge's lane, and an ability with nothing to aim went off as
    // its key went down
    if (ability == null && ProjectileDef.get(charDef.primaryProjectileType).chargeSpeedScaling.isDefined) return
    if (ability != null && Attacks.goesOffOnPress(ability)) return

    val pos = client.getLocalPosition
    val gridX = pos.getX.toDouble; val gridY = pos.getY.toDouble
    // From where we are drawn, so the aim stays under our feet as we walk; which way, as the cast
    // works it out from where we stand: at the cursor, or with it on us the way we face. The primary
    // fires nothing with the cursor on us
    _ox = if (camera.visualX.isNaN) gridX else camera.visualX
    _oy = if (camera.visualY.isNaN) gridY else camera.visualY
    val adx = client.getMouseWorldX - gridX; val ady = client.getMouseWorldY - gridY
    val dist = Math.sqrt(adx * adx + ady * ady)
    if (dist > 0.01) { _ux = adx / dist; _uy = ady / dist }
    else if (ability == null) return
    else { val a = client.getAimAngle; _ux = Math.cos(a); _uy = Math.sin(a) }

    val ready = if (ability == null) client.primaryReady else client.abilityReady(slot - AttackSlot.Q)
    heldColours(ready, Math.min(1f, shownMs / FADE_IN_MS.toFloat))
    val now = frameTimeMs
    val dt = if (_heldFlowAt == 0L) 0f else Math.max(0f, Math.min(0.1f, (now - _heldFlowAt) / 1000f))
    _heldFlowAt = now

    beginShapes()
    shapeBatch.setAlphaMultiplier(_alpha)
    if (ability == null) {
      val pDef = ProjectileDef.get(charDef.primaryProjectileType)
      heldShot(pDef, dt)
      // A gem fires three, five degrees apart (Attacks.shootToward)
      if (client.hasGemBoost) { heldSpoke(pDef, -GEM_SPREAD); heldSpoke(pDef, GEM_SPREAD) }
    } else ability.castBehavior match {
      case StandardProjectile => heldShot(ProjectileDef.get(ability.projectileType), dt)
      case fan: FanProjectile => heldFan(ProjectileDef.get(ability.projectileType), fan)
      case DashBuff(maxDistance, _, _) => heldDash(maxDistance, dt)
      case TeleportCast(maxDistance) => heldBlink(maxDistance)
      case TrapCast(trapType, range) => heldTrap(trapType, range)
      case _ =>
    }
    shapeBatch.setAlphaMultiplier(1f)
  }

  /** The charge gauge's gold, as a charge part way up, while the attack would go off now; a stone grey,
    * dimmer, while it wouldn't. Fading in over FADE_IN_MS either way. */
  private def heldColours(ready: Boolean, fade: Float): Unit = {
    if (ready) {
      val cg = 0.64f + 0.26f * HELD_T; val cb = 0.14f + 0.28f * HELD_T
      _lr = 1f; _lg = cg + (1f - cg) * 0.3f; _lb = cb + (1f - cb) * 0.3f
      _fr = 1f; _fg = 0.5f + 0.5f * cg; _fb = 0.5f + 0.5f * cb
      _ir = 0.22f; _ig = 0.12f; _ib = 0.04f
      _hg = cg; _hb = cb
      _alpha = fade
    } else {
      _lr = 0.86f; _lg = 0.85f; _lb = 0.82f
      _fr = 0.80f; _fg = 0.79f; _fb = 0.76f
      _ir = 0.10f; _ig = 0.10f; _ib = 0.12f
      _hg = 1f; _hb = 1f
      _alpha = fade * NOT_READY_ALPHA
    }
  }

  /** A shot: the lane to where it stops, a ring where it lands or a bar at what stops it, and the
    * reach of the blast it sets off there — wherever it stops for an explosive, only at the end of its
    * range for a splash that goes off there (the server's ProjectileManager). */
  private def heldShot(pDef: ProjectileDef, dt: Float): Unit = {
    // It is fired from a cell out and flies its range from there
    val reach = 1f + pDef.maxRange.toFloat
    val wall = stopAlong(client.getWorld, _ox, _oy, _ux, _uy, reach, pDef.passesThroughWalls)
    val blocked = wall < reach
    val end = if (blocked) wall else reach
    laneLights(end, lightEnd = !blocked)
    _heldFlow = (_heldFlow + FLOW_SLOW * dt) % CHEV_SPACING
    heldLane(if (blocked) end else end - RING_R)
    // One that bounces off a wall goes on from it: no bar there
    if (blocked && pDef.ricochetCount == 0) {
      bar(end, LANE_HALF + 0.1f, 4f, _ir, _ig, _ib, 0.5f)
      bar(end, LANE_HALF + 0.1f, 2f, _lr, _lg, _lb, 0.95f)
    } else landingRing(end)
    val blast =
      if (pDef.explosionConfig.isDefined) pDef.explosionConfig.get.blastRadius
      else if (!blocked && pDef.aoeOnMaxRange.isDefined) pDef.aoeOnMaxRange.get.radius
      else 0f
    if (blast > 0f) areaRing(_ox + _ux * end, _oy + _uy * end, blast, AREA_FILL)
  }

  /** The lane of an aimed shot or dash, from its start to `laneEnd`, with chevrons running out along it. */
  private def heldLane(laneEnd: Float): Unit = {
    if (laneEnd <= LANE_START + 0.05f) return
    val fadeTo = Math.min(laneEnd, LANE_START + LANE_FADE)
    band(laneEnd, fadeTo, 2f * LANE_HALF, _fr, _fg, _fb, 0.14f + 0.08f * HELD_T)
    band(laneEnd, fadeTo, LANE_HALF, _fr, _fg, _fb, 0.12f + 0.08f * HELD_T)
    edge(laneEnd, fadeTo, LANE_HALF, 3.2f, _ir, _ig, _ib, 0.55f)
    edge(laneEnd, fadeTo, -LANE_HALF, 3.2f, _ir, _ig, _ib, 0.55f)
    edge(laneEnd, fadeTo, LANE_HALF, 1.4f, _lr, _lg, _lb, 0.95f)
    edge(laneEnd, fadeTo, -LANE_HALF, 1.4f, _lr, _lg, _lb, 0.95f)
    var d = LANE_START + _heldFlow
    while (d < laneEnd) {
      val a = Math.min(1f, (d - LANE_START) / LANE_FADE) * Math.min(1f, (laneEnd - d) / 0.6f)
      if (a > 0.02f) chevron(d, 4.6f, _ir, _ig, _ib, 0.55f * a, 2.4f, _lr, _lg, _lb, a)
      d += CHEV_SPACING
    }
  }

  /** Where something lands, `along` cells out: a ring on the ground with a dot at its heart. */
  private def landingRing(along: Float): Unit = {
    ring(along, RING_R, 4.4f, _ir, _ig, _ib, 0.55f)
    ring(along, RING_R, 2.4f, _lr, _lg, _lb, 1f)
    val cx = sx(along, 0f); val cy = sy(along, 0f)
    shapeBatch.fillOval(cx, cy, 3.4f, 2.1f, _ir, _ig, _ib, 0.55f, 10)
    shapeBatch.fillOval(cx, cy, 2.3f, 1.4f, _lr, _lg, _lb, 1f, 10)
  }

  /** A fan: a spoke for each of its projectiles, to where each stops, and the span it covers at its
    * reach, an arc from its first spoke to its last. (One that goes all round has nothing to aim, and
    * goes off as its key goes down.) */
  private def heldFan(pDef: ProjectileDef, fan: FanProjectile): Unit = {
    var i = 0
    while (i < fan.count) { heldSpoke(pDef, fan.angleOf(i)); i += 1 }
    reachArc(fan.angleOf(0), fan.angleOf(fan.count - 1), 1f + pDef.maxRange.toFloat)
  }

  /** One projectile of a fan, `theta` radians round from the aim: a line to where it stops, with a
    * dot where it lands or a bar at what stops it. */
  private def heldSpoke(pDef: ProjectileDef, theta: Double): Unit = {
    val ux = _ux; val uy = _uy
    val c = Math.cos(theta); val s = Math.sin(theta)
    // Turned as the cast turns each one (Attacks.shootAbility)
    _ux = ux * c - uy * s; _uy = ux * s + uy * c
    val reach = 1f + pDef.maxRange.toFloat
    val wall = stopAlong(client.getWorld, _ox, _oy, _ux, _uy, reach, pDef.passesThroughWalls)
    val end = Math.min(wall, reach)
    if (end > LANE_START + 0.05f) {
      val fadeTo = Math.min(end, LANE_START + LANE_FADE)
      edge(end, fadeTo, 0f, 4.2f, _ir, _ig, _ib, 0.55f)
      edge(end, fadeTo, 0f, 2.1f, _lr, _lg, _lb, 0.95f)
      if (wall < reach && pDef.ricochetCount == 0) {
        bar(end, 0.24f, 3.6f, _ir, _ig, _ib, 0.5f)
        bar(end, 0.24f, 1.8f, _lr, _lg, _lb, 0.95f)
      } else {
        val dx = sx(end, 0f); val dy = sy(end, 0f)
        shapeBatch.fillOval(dx, dy, 3.4f, 2.1f, _ir, _ig, _ib, 0.55f, 10)
        shapeBatch.fillOval(dx, dy, 2.3f, 1.4f, _lr, _lg, _lb, 1f, 10)
      }
    }
    _ux = ux; _uy = uy
  }

  /** A dash: how far it can go, round us (it ends at the cursor, or at that), and the lane to where it
    * ends (GameClient.dashLanding), its chevrons running out at a dash's pace. */
  private def heldDash(maxDistance: Int, dt: Float): Unit = {
    reachRing(maxDistance.toFloat)
    val landing = client.dashLanding(maxDistance)
    if (landing == null) return
    val len = aimAt(landing.getX, landing.getY)
    laneLights(len, lightEnd = true)
    _heldFlow = (_heldFlow + FLOW_FAST * dt) % CHEV_SPACING
    heldLane(len - RING_R)
    landingRing(len)
  }

  /** A blink: dots to where it lands (GameClient.blinkLanding), its whole range toward the aim. */
  private def heldBlink(maxDistance: Int): Unit = {
    val landing = client.blinkLanding(maxDistance)
    val len = aimAt(landing.getX, landing.getY)
    landingDots(len)
    landingRing(len)
  }

  /** A trap: how far it can be thrown, round us; dots to where it lands (GameClient.trapLanding); and
    * the ground it watches there, whoever comes within its trigger radius setting it off. */
  private def heldTrap(trapType: Byte, range: Int): Unit = {
    reachRing(range.toFloat)
    val landing = client.trapLanding(range)
    if (landing.isEmpty) return
    val cell = landing.get
    val len = aimAt(cell.getX, cell.getY)
    landingDots(len)
    val d = TrapDef.get(trapType)
    areaRing(cell.getX, cell.getY, if (d != null) d.triggerRadius else 1f, AREA_FILL * 1.6f)
    landingRing(len)
  }

  /** Turn the aim to point at the centre of cell (x, y) from where we are drawn: how far that is. */
  private def aimAt(x: Int, y: Int): Float = {
    val dx = x - _ox; val dy = y - _oy
    val len = Math.sqrt(dx * dx + dy * dy)
    if (len > 1e-3) { _ux = dx / len; _uy = dy / len }
    len.toFloat
  }

  /** Dots from the lane's start to a landing ring `len` cells out. */
  private def landingDots(len: Float): Unit = {
    var d = LANE_START + DOT_SPACING * 0.5f
    while (d < len - RING_R - END_R) {
      val dx = sx(d, 0f); val dy = sy(d, 0f)
      shapeBatch.fillOval(dx, dy, 3.0f, 1.8f, _ir, _ig, _ib, 0.45f, 10)
      shapeBatch.fillOval(dx, dy, 1.9f, 1.1f, _lr, _lg, _lb, 0.9f, 10)
      d += DOT_SPACING
    }
  }

  /** The reach of a blast or a trap: a ring of `radius` cells round world (wx, wy), its ground faintly
    * filled. A circle on the ground is an ellipse twice as wide as it is tall on screen. */
  private def areaRing(wx: Double, wy: Double, radius: Float, fillA: Float): Unit = {
    val cx = worldToScreenX(wx, wy).toFloat; val cy = worldToScreenY(wx, wy).toFloat
    val rx = (radius * HW * Math.sqrt(2.0)).toFloat; val ry = rx * HH / HW
    shapeBatch.fillOval(cx, cy, rx, ry, _fr, _fg, _fb, fillA, 48)
    shapeBatch.strokeOval(cx, cy, rx, ry, 4.6f, _ir, _ig, _ib, 0.55f, 48)
    shapeBatch.strokeOval(cx, cy, rx, ry, 2.4f, _lr, _lg, _lb, 0.95f, 48)
  }

  /** How far a dash or a throw can reach: a thin ring of `radius` cells round us. */
  private def reachRing(radius: Float): Unit = {
    val rx = (radius * HW * Math.sqrt(2.0)).toFloat
    shapeBatch.strokeOval(sx(0f, 0f), sy(0f, 0f), rx, rx * HH / HW, 2.8f, _ir, _ig, _ib, 0.32f, 48)
    shapeBatch.strokeOval(sx(0f, 0f), sy(0f, 0f), rx, rx * HH / HW, 1.2f, _lr, _lg, _lb, 0.6f, 48)
  }

  /** A fan's reach between two of its headings (`a0` to `a1` radians round from the aim): an arc on
    * the ground `radius` cells from us, thin as a reach ring. */
  private def reachArc(a0: Double, a1: Double, radius: Float): Unit = {
    val base = Math.atan2(_uy, _ux)
    var k = 0
    while (k < ARC_POINTS) {
      val phi = base + a0 + (a1 - a0) * k / (ARC_POINTS - 1)
      val wx = _ox + radius * Math.cos(phi); val wy = _oy + radius * Math.sin(phi)
      _arcX(k) = worldToScreenX(wx, wy).toFloat; _arcY(k) = worldToScreenY(wx, wy).toFloat
      k += 1
    }
    shapeBatch.strokePolyline(_arcX, _arcY, ARC_POINTS, 2.8f, _ir, _ig, _ib, 0.32f)
    shapeBatch.strokePolyline(_arcX, _arcY, ARC_POINTS, 1.2f, _lr, _lg, _lb, 0.6f)
  }

  /** On a dark map an aim's lane lights the ground it runs over, as a charge's does. */
  private def laneLights(end: Float, lightEnd: Boolean): Unit = {
    if (!projectileLights) return
    var d = LANE_START + LIGHT_SPACING * 0.5f
    while (d < end) {
      lightSystem.addLight(sx(d, 0f), sy(d, 0f), 34f, 1f, _hg, _hb, (0.10f + 0.05f * HELD_T) * _alpha)
      d += LIGHT_SPACING
    }
    if (lightEnd) lightSystem.addLight(sx(end, 0f), sy(end, 0f), 40f, 1f, _hg, _hb, (0.16f + 0.08f * HELD_T) * _alpha)
  }
}

private[render] object AimArrowPainter {
  // An aim (drawHeld) fades in over FADE_IN_MS: an ability's from its key, the primary's once held
  // SHOW_AFTER_MS, so a click shows nothing
  private val SHOW_AFTER_MS = 60L
  private val FADE_IN_MS = 90L
  // Its gold is the charge gauge's this far up; while it couldn't go off, it is grey and dimmer
  private val HELD_T = 0.55f
  private val NOT_READY_ALPHA = 0.6f
  // The faint fill of a blast's or a trap's ground
  private val AREA_FILL = 0.10f
  // How far apart a gem's three shots go, either side of the aim (Attacks.shootToward)
  private val GEM_SPREAD = Math.PI / 36.0
  // Points along a fan's arc
  private val ARC_POINTS = 33

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
