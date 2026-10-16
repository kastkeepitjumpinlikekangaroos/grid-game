package com.gridgame.client.render
package world

import com.gridgame.client.game.{Attacks, TerrainImpact}
import com.gridgame.common.model._
import com.gridgame.common.protocol.AttackSlot

/**
 * Where our attack will go, laid on the ground while we aim it (render/CLAUDE.md, *An aimed attack
 * shows where it will go*, *A charged shot's aim is a lane on the ground* and *An aim moves with us*).
 *
 * Every aim is drawn in one hand: a sheet of translucent gold on the ground in the shape of what it
 * shows, rimmed in the HUD's gold with a dark ink line just outside the rim, every edge smoothed over
 * a pixel (ShapeBatch's smooth shapes). A shot is a lane, rounded at both ends, from just outside our
 * ring to the end of its range, cut square at whatever stops it, a soft light running out along it;
 * a gem's three shots one lane widening to take them in; the blast a shot sets off a disc where it
 * goes off, the lane ending short of it; a fan a narrower lane for each of its projectiles; a dash the
 * lane to a disc where it ends, inside a thin ring of how far it can go; a blink a row of dots to a
 * disc where it lands; a trap dots to the ground it will watch. A charged shot's lane runs on to
 * where a full charge would carry it, filled as far as the charge would carry it now. Each is worked
 * out by what the cast itself does (stopAlong for a shot, GameClient.dashLanding, blinkLanding and
 * trapLanding).
 *
 * Where it lies is AimGeometry's: from where we are drawn toward the pointer as it is on this frame's
 * screen, so as we walk with the mouse still it stays put on the screen, as our sprite does, a
 * landing snapped to its cell from there.
 *
 * While the attack couldn't go off (the primary's cooldown, a ceasefire, a hold) it is grey and
 * dimmer, easing between the two. An ability's shows as its key goes down, and fades in; the
 * primary's after SHOW_AFTER_MS held, so a click shows nothing.
 */
private[render] final class AimArrowPainter(ctx: RenderContext) {
  import ctx._
  import AimArrowPainter._

  // Where the aim lies (AimGeometry); and where it runs from (where we are drawn) and which way, in
  // the world, for sx/sy, which a fan's spokes and a dash's lane turn as they are drawn
  private val aim = new AimGeometry(client, camera)
  private var _ox = 0.0; private var _oy = 0.0
  private var _ux = 0.0; private var _uy = 0.0

  // The last frame's time, and how long since it, in seconds, for what eases from frame to frame
  private var _lastAt = 0L
  private var _dt = 0f
  // When the charge filled
  private var _fullSince = 0L

  // How gold the aim is (1) rather than grey (0), eased between them; below 0 before its first frame
  private var _gold = -1f
  // The aim's colours this frame: its fill, its rim, its ink; and how opaque it is, fading in
  private var _fr = 1f; private var _fg = 1f; private var _fb = 1f
  private var _rr = 1f; private var _rg = 1f; private var _rb = 1f
  private var _ir = 0f; private var _ig = 0f; private var _ib = 0f
  private var _alpha = 1f
  // ...and the light it casts on a dark map: gold, or white while it couldn't go off
  private var _hg = 1f; private var _hb = 1f

  // A lane's stations along the aim (cells out) and half its width at each; then its outline on
  // screen, laid out as a ribbon (its left side out, its right side back), with an alpha at each
  // point for its fill and one for its rim and ink
  private val _sAlong = new Array[Float](MAX_STATIONS)
  private val _sHalf = new Array[Float](MAX_STATIONS)
  private var _ns = 0
  private val _xs = new Array[Float](MAX_STATIONS * 2)
  private val _ys = new Array[Float](MAX_STATIONS * 2)
  private val _fa = new Array[Float](MAX_STATIONS * 2)
  private val _ra = new Array[Float](MAX_STATIONS * 2)
  private var _n = 0
  // The lane being laid out: where its light is now, and from where it is only a charge's promise
  private var _glowAt = Float.NaN
  private var _split = Float.MaxValue
  // A fan's arc
  private val _arcX = new Array[Float](ARC_POINTS)
  private val _arcY = new Array[Float](ARC_POINTS)

  /** A point `along` cells out from where we are drawn and `across` cells to the left of the aim, on
    * screen. */
  private def sx(along: Float, across: Float): Float =
    worldToScreenX(_ox + _ux * along - _uy * across, _oy + _uy * along + _ux * across).toFloat
  private def sy(along: Float, across: Float): Float =
    worldToScreenY(_ox + _ux * along - _uy * across, _oy + _uy * along + _ux * across).toFloat

  /** How far to ease toward a target this frame, over a time constant of `ms`. */
  private def ease(ms: Float): Float = AimGeometry.ease(_dt, ms)

  def draw(): Unit = {
    val now = frameTimeMs
    _dt = if (_lastAt == 0L) 0f else Math.max(0f, Math.min(0.1f, (now - _lastAt) / 1000f))
    _lastAt = now
    if (client.getIsDead) { forget(); return }
    // A charge is the primary held: drawn unless Q or E has been pressed since
    val slot = client.aimingSlot
    if (client.isCharging && (slot < 0 || slot == AttackSlot.PRIMARY)) {
      aim.forgetLanding()
      drawCharge()
      return
    }
    _fullSince = 0L
    if (slot >= 0) drawAimed(slot) else forget()
  }

  /** Nothing is aimed: the next aim starts afresh. */
  private def forget(): Unit = { _gold = -1f; aim.forget(); _fullSince = 0L }

  /** How far lane `k` (the shot's or the charge's 0, a fan's spokes 1 on) is drawn this frame, `to`
    * cells out being where it stops now (AimGeometry.reach). */
  private def laneReach(k: Int, to: Float): Float = aim.reach(k, to, _dt)

  /** Where the aim runs from and which way, this frame (AimGeometry.aimFromHere), into _ox.._uy: how
    * far the aim point is, or -1 with it on us. */
  private def aimFromHere(faceWhenOnUs: Boolean): Double = {
    val d = aim.aimFromHere(aimAtPointer, faceWhenOnUs)
    _ox = aim.ox; _oy = aim.oy; _ux = aim.ux; _uy = aim.uy
    d
  }

  // ── A charged shot ──────────────────────────────────────────────────────

  private def drawCharge(): Unit = {
    val pDef = ProjectileDef.get(client.getSelectedCharacterDef.primaryProjectileType)
    if (pDef.chargeSpeedScaling.isEmpty || aimFromHere(faceWhenOnUs = false) < 0) return

    // How far the shot goes from our feet: it is fired from a cell out and flies its range from there
    val level = client.getChargeLevel
    val t = level / 100f
    val crs = pDef.chargeRangeScaling
    val reach = 1f + (if (crs.isDefined) crs.get.interpolate(level) else pDef.maxRange.toFloat)
    val fullReach = 1f + (if (crs.isDefined) crs.get.max else pDef.maxRange.toFloat)
    val wall = stopAlong(client.getWorld, _ox, _oy, _ux, _uy, fullReach, pDef.passesThroughWalls)
    val blocked = wall < reach
    val stop = Math.min(wall, fullReach)
    // Drawn as far as it has grown (laneReach), and filled to where the shot would land now
    val farEnd = laneReach(0, stop)
    val end = Math.min(if (blocked) wall else reach, farEnd)
    val now = frameTimeMs
    if (level < 100) _fullSince = 0L else if (_fullSince == 0L) _fullSince = now

    // The charge gauge's gold, deep as it starts and bright at full, never the gauge's white-hot
    // (on snow a white aim is only its ink)
    _gold = 1f
    _fr = 1f; _fg = 0.70f + 0.16f * t; _fb = 0.20f + 0.12f * t
    _rr = 1f; _rg = 0.80f + 0.10f * t; _rb = 0.41f + 0.20f * t
    _ir = INK_R; _ig = INK_G; _ib = INK_B
    _hg = 0.64f + 0.26f * t; _hb = 0.14f + 0.28f * t
    _alpha = 1f
    laneLights(end, lightEnd = !blocked, 0.10f + 0.05f * t, 0.16f + 0.08f * t)

    beginShapes()
    // One lane on to where a full charge would land, filled as far as this one would carry it; a
    // light running out along the filled part, quicker as the charge builds
    _glowAt = glowAlong(LANE_START, end, PULSE_MS - (PULSE_MS - PULSE_FULL_MS) * t)
    _split = if (farEnd > end + 0.05f) end else Float.MaxValue
    laneShape(LANE_START, farEnd, LANE_HALF, roundTo = farEnd >= fullReach - 0.01f || farEnd < stop)
    paintLane(FILL_A + 0.08f * t, RIM_A, INK_A, RIM_W)
    _split = Float.MaxValue
    // Where it lands now: a disc on the ground, and at full a ripple breathing out off it, as the
    // gauge's does round our feet. Stopped short of that, its square end at the face is the stop
    if (!blocked) {
      marker(_ox + _ux * end, _oy + _uy * end)
      if (_fullSince != 0L) {
        val rp = ((now - _fullSince) % READY_MS) / READY_MS.toFloat
        val rx = groundRx(LAND_R + 0.6f * rp)
        val cx = sx(end, 0f); val cy = sy(end, 0f)
        shapeBatch.strokeOvalSmooth(cx, cy, rx, rx * HH / HW, RIM_W * (1f - rp) + 0.3f, _rr, _rg, _rb, 0.8f * (1f - rp))
      }
    }
  }

  // ── An attack being aimed ───────────────────────────────────────────────

  /** The aim of the attack being aimed in `slot` (an AttackSlot), as its cast will work it out. */
  private def drawAimed(slot: Int): Unit = {
    // A click shows nothing: the primary is aimed once held a moment. An ability is aimed from its key
    val shownMs = client.aimedForMs(slot) - (if (slot == AttackSlot.PRIMARY) SHOW_AFTER_MS else 0L)
    if (shownMs < 0L) { forget(); return }
    val charDef = client.getSelectedCharacterDef
    val ability =
      if (slot == AttackSlot.Q) charDef.qAbility else if (slot == AttackSlot.E) charDef.eAbility else null
    // A primary that charges shows its charge's lane, and an ability with nothing to aim went off as
    // its key went down
    if (ability == null && ProjectileDef.get(charDef.primaryProjectileType).chargeSpeedScaling.isDefined) return
    if (ability != null && Attacks.goesOffOnPress(ability)) return

    // Toward the aim; with it on us, the way we face, as the cast takes it. The primary fires nothing
    // with the cursor on us
    if (aimFromHere(faceWhenOnUs = ability != null) < 0.0 && ability == null) return

    val ready = if (ability == null) client.primaryReady else client.abilityReady(slot - AttackSlot.Q)
    colours(ready, Math.min(1f, shownMs / FADE_IN_MS.toFloat))

    beginShapes()
    shapeBatch.setAlphaMultiplier(_alpha)
    if (ability == null) {
      // A gem fires three, five degrees apart (Attacks.shootToward): one lane, widening to take in the
      // two either side, as three lanes tangled into each other
      aimedShot(ProjectileDef.get(charDef.primaryProjectileType),
        if (client.hasGemBoost) Math.tan(GEM_SPREAD).toFloat else 0f)
    } else ability.castBehavior match {
      case StandardProjectile => aimedShot(ProjectileDef.get(ability.projectileType))
      case fan: FanProjectile => aimedFan(ProjectileDef.get(ability.projectileType), fan)
      case DashBuff(maxDistance, _, _) => aimedDash(maxDistance, slot)
      case TeleportCast(maxDistance) => aimedBlink(maxDistance, slot)
      case TrapCast(trapType, range) => aimedTrap(trapType, range, slot)
      case _ =>
    }
    shapeBatch.setAlphaMultiplier(1f)
  }

  /** The gold while the attack would go off now, a stone grey while it wouldn't, easing between the
    * two over COLOUR_MS; fading in over FADE_IN_MS either way, and dimmer grey. */
  private def colours(ready: Boolean, fade: Float): Unit = {
    val target = if (ready) 1f else 0f
    _gold = if (_gold < 0f) target else _gold + (target - _gold) * ease(COLOUR_MS)
    val k = _gold
    @inline def mix(grey: Float, gold: Float): Float = grey + (gold - grey) * k
    _fr = mix(0.80f, 1f); _fg = mix(0.80f, 0.78f); _fb = mix(0.78f, 0.26f)
    _rr = mix(0.86f, 1f); _rg = mix(0.85f, 0.85f); _rb = mix(0.82f, 0.51f)
    _ir = mix(0.10f, INK_R); _ig = mix(0.10f, INK_G); _ib = mix(0.12f, INK_B)
    _hg = mix(1f, 0.78f); _hb = mix(1f, 0.29f)
    _alpha = fade * (NOT_READY_ALPHA + (1f - NOT_READY_ALPHA) * k)
  }

  /** A shot: the lane to where it stops, rounded where its range runs out and cut square at what
    * stops it (rounded at a wall it bounces off), and the reach of the blast it sets off there —
    * wherever it stops for an explosive, only at the end of its range for a splash that goes off there
    * (the server's ProjectileManager), the lane ending at the blast's edge. */
  private def aimedShot(pDef: ProjectileDef, flare: Float = 0f): Unit = {
    // It is fired from a cell out and flies its range from there
    val reach = 1f + pDef.maxRange.toFloat
    val wall = stopAlong(client.getWorld, _ox, _oy, _ux, _uy, reach, pDef.passesThroughWalls)
    val blocked = wall < reach
    val stop = if (blocked) wall else reach
    val end = laneReach(0, stop)
    val blast =
      if (pDef.explosionConfig.isDefined) pDef.explosionConfig.get.blastRadius
      else if (!blocked && pDef.aoeOnMaxRange.isDefined) pDef.aoeOnMaxRange.get.radius
      else 0f
    laneLights(end, lightEnd = !blocked, 0.10f, 0.16f)
    // Short of the blast's edge by a gap, so the two read as one thing pointing at the other
    val laneEnd = if (blast > 0f) end - blast - END_GAP else end
    if (laneEnd > LANE_START + 0.1f) {
      _glowAt = glowAlong(LANE_START, laneEnd, PULSE_MS)
      laneShape(LANE_START, laneEnd, LANE_HALF,
        roundTo = blast > 0f || !blocked || pDef.ricochetCount > 0 || end < stop, flare)
      paintLane(FILL_A, RIM_A, INK_A, RIM_W)
    }
    if (blast > 0f) area(_ox + _ux * end, _oy + _uy * end, blast, AREA_FILL_A)
  }

  /** A fan: a narrower lane for each of its projectiles, each to where it stops, and an arc at their
    * reach from the first to the last. (One that goes all round has nothing to aim, and goes off as
    * its key goes down.) */
  private def aimedFan(pDef: ProjectileDef, fan: FanProjectile): Unit = {
    reachArc(fan.angleOf(0), fan.angleOf(fan.count - 1), 1f + pDef.maxRange.toFloat)
    var i = 0
    while (i < fan.count) { spoke(pDef, fan.angleOf(i), 1 + i); i += 1 }
  }

  /** One projectile of a fan, `theta` radians round from the aim: a narrow lane to where it stops,
    * rounded at the end of its range and square at what stops it; lane `k` (laneReach). */
  private def spoke(pDef: ProjectileDef, theta: Double, k: Int): Unit = {
    val ux = _ux; val uy = _uy
    val c = Math.cos(theta); val s = Math.sin(theta)
    // Turned as the cast turns each one (Attacks.shootAbility)
    _ux = ux * c - uy * s; _uy = ux * s + uy * c
    val reach = 1f + pDef.maxRange.toFloat
    val wall = stopAlong(client.getWorld, _ox, _oy, _ux, _uy, reach, pDef.passesThroughWalls)
    val stop = Math.min(wall, reach)
    val end = laneReach(k, stop)
    if (end > LANE_START + 0.1f) {
      _glowAt = glowAlong(LANE_START, end, PULSE_MS)
      laneShape(LANE_START, end, SPOKE_HALF, roundTo = wall >= reach || pDef.ricochetCount > 0 || end < stop)
      paintLane(FILL_A, RIM_A, INK_A, SPOKE_RIM_W)
    }
    _ux = ux; _uy = uy
  }

  /** A dash: how far it can go, a thin ring round us (it ends at the cursor, or at that), the lane to
    * where it ends (GameClient.dashLanding: over whatever lies between, as a dash goes) and a disc
    * there. Its light runs at a dash's pace. */
  private def aimedDash(maxDistance: Int, slot: Int): Unit = {
    reachRing(maxDistance.toFloat)
    val cast = client.dashLanding(maxDistance)
    if (cast == null || !aim.landing(AimGeometry.DASH, maxDistance, cast.getX, cast.getY, slot, aimAtPointer, _dt)) {
      aim.forgetLanding()
      return
    }
    val len = aimAt(aim.lx, aim.ly)
    laneLights(len, lightEnd = true, 0.10f, 0.16f)
    val laneEnd = len - LAND_R - END_GAP
    if (laneEnd > LANE_START + 0.1f) {
      _glowAt = glowAlong(LANE_START, laneEnd, DASH_PULSE_MS)
      laneShape(LANE_START, laneEnd, LANE_HALF, roundTo = true)
      paintLane(FILL_A, RIM_A, INK_A, RIM_W)
    }
    marker(aim.lx, aim.ly)
  }

  /** A blink: dots to where it lands (GameClient.blinkLanding: its whole range toward the aim, short
    * of the first cell it can't land on), and a disc there. */
  private def aimedBlink(maxDistance: Int, slot: Int): Unit = {
    val cast = client.blinkLanding(maxDistance)
    if (!aim.landing(AimGeometry.BLINK, maxDistance, cast.getX, cast.getY, slot, aimAtPointer, _dt)) return
    val len = aimAt(aim.lx, aim.ly)
    dots(len - LAND_R)
    marker(aim.lx, aim.ly)
  }

  /** A trap: how far it can be thrown, a thin ring round us; dots to where it lands
    * (GameClient.trapLanding); and the ground it watches there, whoever comes within its trigger
    * radius setting it off. */
  private def aimedTrap(trapType: Byte, range: Int, slot: Int): Unit = {
    reachRing(range.toFloat)
    val cast = client.trapLanding(range)
    if (cast.isEmpty ||
        !aim.landing(AimGeometry.THROW, range, cast.get.getX, cast.get.getY, slot, aimAtPointer, _dt)) {
      aim.forgetLanding()
      return
    }
    val len = aimAt(aim.lx, aim.ly)
    val d = TrapDef.get(trapType)
    val watch = if (d != null) d.triggerRadius else 1f
    dots(len - watch)
    area(aim.lx, aim.ly, watch, TRAP_FILL_A)
    marker(aim.lx, aim.ly)
  }

  /** Turn the aim to point at world (x, y) from where we are drawn: how far that is. */
  private def aimAt(x: Double, y: Double): Float = {
    val dx = x - _ox; val dy = y - _oy
    val len = Math.sqrt(dx * dx + dy * dy)
    if (len > 1e-3) { _ux = dx / len; _uy = dy / len }
    len.toFloat
  }

  // ── The shapes ──────────────────────────────────────────────────────────

  /** Where along a lane from `from` to `to` its light is now: running out from its start to past its
    * end every `periodMs`, on the frame's clock. */
  private def glowAlong(from: Float, to: Float, periodMs: Float): Float = {
    val phase = (frameTimeMs % periodMs.toLong) / periodMs
    from - GLOW_W + (to - from + 2f * GLOW_W) * phase
  }

  /**
   * A lane lying on the ground along the aim from `from` to `to` cells out, `half` cells either side
   * at its start and widening by `flare` cells a cell out (a gem's spread), into the outline (_xs, _ys)
   * with its fill's and rim's alphas (_fa, _ra): rounded at its start, and at its end where `roundTo`,
   * else cut square across. A lane shorter than it is wide is a round end alone. Its fill fades in
   * from its start over LANE_FADE, and its light (_glowAt) brightens it where it is; past _split it is
   * only a charge's promise, faint.
   */
  private def laneShape(from: Float, to: Float, half: Float, roundTo: Boolean, flare: Float = 0f): Unit = {
    _ns = 0
    val h = Math.max(0.02f, Math.min(half, (to - from) * 0.5f))
    // Half its width at its end, and where its sides meet that round end
    val h1 = Math.max(h, Math.min(h + flare * (to - from), (to - from - h) * 0.5f + h))
    // Its start, rounded: from the tip round to its side
    var k = 0
    while (k <= CAP_STATIONS) {
      val psi = Math.PI - (Math.PI / 2) * Math.max(0.12, k.toDouble) / CAP_STATIONS
      station(from + h + h * Math.cos(psi).toFloat, h * Math.sin(psi).toFloat)
      k += 1
    }
    // its sides: a station every STATION where its fill changes along it (as it fades in from its
    // start, and round its light), either side of where a charge would land now, and otherwise one
    // every SPARSE_STATION, between which a straight blend is all there is to draw anyway
    val a0 = from + h; val a1 = if (roundTo) to - h1 else to
    @inline def halfAt(along: Float): Float = if (a1 > a0) h + (h1 - h) * (along - a0) / (a1 - a0) else h1
    val fadeEnd = from + LANE_FADE
    val glow0 = if (_glowAt.isNaN) Float.MaxValue else _glowAt - GLOW_REACH
    val glow1 = if (_glowAt.isNaN) Float.MinValue else _glowAt + GLOW_REACH
    var prev = a0
    while (prev < a1 - 1e-4f) {
      val dense = prev < fadeEnd || (prev >= glow0 && prev < glow1)
      var at = Math.min(a1, prev + (if (dense) STATION else SPARSE_STATION))
      // Where the fill starts changing, a station, so the change is drawn from where it starts
      if (prev < glow0 && at > glow0) at = glow0
      if (prev < fadeEnd && at > fadeEnd) at = fadeEnd
      // (and should the stations run short, straight to the end)
      if (_ns >= MAX_STATIONS - CAP_STATIONS - 4) at = a1
      if (_split > prev && _split < at) {
        station(_split - SPLIT_EDGE, halfAt(_split - SPLIT_EDGE))
        station(_split + SPLIT_EDGE, halfAt(_split + SPLIT_EDGE))
      }
      station(at, halfAt(at))
      prev = at
    }
    // and its end, rounded from its side round to the tip, or cut square
    if (roundTo) {
      k = 1
      while (k <= CAP_STATIONS) {
        val psi = (Math.PI / 2) * (1.0 - Math.min(CAP_STATIONS - 0.12, k.toDouble) / CAP_STATIONS)
        station(to - h1 + h1 * Math.cos(psi).toFloat, h1 * Math.sin(psi).toFloat)
        k += 1
      }
    }
    // Laid out on screen: the left side out, then the right side back
    val n = _ns
    var s = 0
    while (s < n) {
      val al = _sAlong(s); val hw = _sHalf(s)
      val l = s; val r = 2 * n - 1 - s
      _xs(l) = sx(al, hw); _ys(l) = sy(al, hw)
      _xs(r) = sx(al, -hw); _ys(r) = sy(al, -hw)
      val fade = Math.min(1f, Math.max(0f, (al - from) / LANE_FADE))
      val g = if (_glowAt.isNaN) 0f else { val z = (al - _glowAt) / GLOW_W; GLOW_GAIN * Math.exp(-z * z).toFloat }
      val promise = al > _split
      val fa = fade * (1f + g) * (if (promise) PROMISE_FILL else 1f)
      val ra = fade * (if (promise) PROMISE_RIM else 1f)
      _fa(l) = fa; _fa(r) = fa; _ra(l) = ra; _ra(r) = ra
      s += 1
    }
    _n = 2 * n
    _glowAt = Float.NaN
  }

  private def station(along: Float, half: Float): Unit = {
    if (_ns >= MAX_STATIONS) return
    _sAlong(_ns) = along; _sHalf(_ns) = half
    _ns += 1
  }

  /** The lane laid out by laneShape: its fill, then its ink just outside its rim, then the rim. */
  private def paintLane(fillA: Float, rimA: Float, inkA: Float, rimW: Float): Unit = {
    shapeBatch.fillRibbonSmooth(_xs, _ys, _fa, _n, _fr, _fg, _fb, fillA)
    shapeBatch.strokePathSmooth(_xs, _ys, _ra, _n, closed = true, INK_W, (rimW + INK_W) * 0.5f - INK_TUCK,
      _ir, _ig, _ib, inkA)
    shapeBatch.strokePathSmooth(_xs, _ys, _ra, _n, closed = true, rimW, 0f, _rr, _rg, _rb, rimA)
  }

  /** How wide a circle of `radius` cells on the ground is on screen, either side of its centre: a
    * circle on the ground is an ellipse twice as wide as it is tall. */
  private def groundRx(radius: Float): Float = (radius * HW * Math.sqrt(2.0)).toFloat

  /** A disc of `radius` cells on the ground round world (wx, wy): filled faintly, rimmed and inked,
    * as a lane is. The reach of a blast, or the ground a trap watches. */
  private def area(wx: Double, wy: Double, radius: Float, fillA: Float): Unit = {
    val cx = worldToScreenX(wx, wy).toFloat; val cy = worldToScreenY(wx, wy).toFloat
    val rx = groundRx(radius); val ry = rx * HH / HW
    shapeBatch.fillOvalSmooth(cx, cy, rx, ry, _fr, _fg, _fb, fillA)
    val o = (RIM_W + INK_W) * 0.5f - INK_TUCK
    shapeBatch.strokeOvalSmooth(cx, cy, rx + o, ry + o, INK_W, _ir, _ig, _ib, INK_A)
    shapeBatch.strokeOvalSmooth(cx, cy, rx, ry, RIM_W, _rr, _rg, _rb, RIM_A)
  }

  /** Where something lands, at world (wx, wy): a small disc on the ground with a dot at its heart. */
  private def marker(wx: Double, wy: Double): Unit = {
    val cx = worldToScreenX(wx, wy).toFloat; val cy = worldToScreenY(wx, wy).toFloat
    val rx = groundRx(LAND_R); val ry = rx * HH / HW
    shapeBatch.fillOvalSmooth(cx, cy, rx, ry, _fr, _fg, _fb, MARKER_FILL_A)
    val o = (RIM_W + INK_W) * 0.5f - INK_TUCK
    shapeBatch.strokeOvalSmooth(cx, cy, rx + o, ry + o, INK_W, _ir, _ig, _ib, INK_A)
    shapeBatch.strokeOvalSmooth(cx, cy, rx, ry, RIM_W, _rr, _rg, _rb, RIM_A)
    val dx = groundRx(DOT_R); val dy = dx * HH / HW
    shapeBatch.fillOvalSmooth(cx, cy, dx + INK_W, dy + INK_W, _ir, _ig, _ib, INK_A)
    shapeBatch.fillOvalSmooth(cx, cy, dx, dy, _rr, _rg, _rb, RIM_A)
  }

  /** Dots along the aim from the lane's start to `to` cells out: a blink's jump or a throw. */
  private def dots(to: Float): Unit = {
    val dx = groundRx(DOT_R); val dy = dx * HH / HW
    var d = LANE_START + DOT_SPACING * 0.5f
    while (d < to - DOT_SPACING * 0.25f) {
      val a = Math.min(1f, (d - LANE_START) / LANE_FADE)
      val cx = sx(d, 0f); val cy = sy(d, 0f)
      shapeBatch.fillOvalSmooth(cx, cy, dx + INK_W, dy + INK_W, _ir, _ig, _ib, INK_A * a)
      shapeBatch.fillOvalSmooth(cx, cy, dx, dy, _rr, _rg, _rb, RIM_A * a)
      d += DOT_SPACING
    }
  }

  /** How far a dash or a throw can reach: a thin ring of `radius` cells round us. */
  private def reachRing(radius: Float): Unit = {
    val cx = sx(0f, 0f); val cy = sy(0f, 0f)
    val rx = groundRx(radius); val ry = rx * HH / HW
    val o = (REACH_W + INK_W) * 0.5f - INK_TUCK
    shapeBatch.strokeOvalSmooth(cx, cy, rx + o, ry + o, INK_W, _ir, _ig, _ib, REACH_INK_A)
    shapeBatch.strokeOvalSmooth(cx, cy, rx, ry, REACH_W, _rr, _rg, _rb, REACH_A)
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
    // The ink on the arc's outer side: the left of its way, turning as it does from a0 to a1
    val o = (REACH_W + INK_W) * 0.5f - INK_TUCK
    shapeBatch.strokePathSmooth(_arcX, _arcY, null, ARC_POINTS, closed = false, INK_W, if (a1 > a0) -o else o,
      _ir, _ig, _ib, REACH_INK_A)
    shapeBatch.strokePathSmooth(_arcX, _arcY, null, ARC_POINTS, closed = false, REACH_W, 0f, _rr, _rg, _rb, REACH_A)
  }

  /** On a dark map an aim's lane lights the ground it runs over, as a shot does: away from our own
    * light the light map dims the gold to khaki. Close together, or the glow comes out in beads. Not
    * in daylight (RenderContext.projectileLights). */
  private def laneLights(end: Float, lightEnd: Boolean, along: Float, atEnd: Float): Unit = {
    if (!projectileLights) return
    var d = LANE_START + LIGHT_SPACING * 0.5f
    while (d < end) {
      lightSystem.addLight(sx(d, 0f), sy(d, 0f), 34f, 1f, _hg, _hb, along * _alpha)
      d += LIGHT_SPACING
    }
    if (lightEnd) lightSystem.addLight(sx(end, 0f), sy(end, 0f), 40f, 1f, _hg, _hb, atEnd * _alpha)
  }
}

private[render] object AimArrowPainter {
  // An aim (drawAimed) fades in over FADE_IN_MS: an ability's from its key, the primary's once held
  // SHOW_AFTER_MS, so a click shows nothing
  private val SHOW_AFTER_MS = 60L
  private val FADE_IN_MS = 90L
  // It eases between gold and grey over this, as the attack comes ready or stops being
  private val COLOUR_MS = 70f
  // While it couldn't go off it is grey and this much as opaque
  private val NOT_READY_ALPHA = 0.6f
  // How far apart a gem's three shots go, either side of the aim (Attacks.shootToward)
  private val GEM_SPREAD = Math.PI / 36.0
  // Points along a fan's arc
  private val ARC_POINTS = 33

  // Its ink, under every rim: the HUD's ink, a warm near-black
  private val INK_R = 0.20f; private val INK_G = 0.11f; private val INK_B = 0.04f
  // How opaque its parts are: a lane's fill, a blast's or a trap's ground, a landing's disc, the rims,
  // the ink; how wide the rims and the ink are, in units, and how far the ink tucks under the rim
  private val FILL_A = 0.30f
  private val AREA_FILL_A = 0.13f
  private val TRAP_FILL_A = 0.18f
  private val MARKER_FILL_A = 0.34f
  private val RIM_A = 0.95f
  private val INK_A = 0.75f
  private val RIM_W = 1.6f
  private val SPOKE_RIM_W = 1.3f
  private val INK_W = 1.3f
  private val INK_TUCK = 0.25f
  // A reach ring: thinner and fainter than a rim
  private val REACH_W = 1.0f
  private val REACH_A = 0.55f
  private val REACH_INK_A = 0.3f

  // The lane's layout along the aim, in cells from our feet
  private val LANE_START = 0.8f   // clear of our ring
  private val LANE_FADE = 0.8f    // its fill fades in over this much from its start
  private val LANE_HALF = 0.3f    // half its width
  private val SPOKE_HALF = 0.17f  // half a fan's spoke's width
  private val LAND_R = 0.42f      // a landing's disc
  private val DOT_R = 0.1f        // a dot, and the dot at a landing's heart
  private val DOT_SPACING = 0.55f
  private val END_GAP = 0.12f     // between a lane's end and the disc it points at
  private val LIGHT_SPACING = 1.2f
  private val READY_MS = 900L     // a full charge's ripple off where it lands
  // A lane's light: how wide it is (cells) and how much it brightens the fill, and how long it takes to
  // run out along a lane: a shot's, a full charge's, a dash's
  private val GLOW_W = 0.9f
  private val GLOW_GAIN = 0.55f
  private val PULSE_MS = 1300f
  private val PULSE_FULL_MS = 700f
  private val DASH_PULSE_MS = 650f
  // Past where a charge would land now, the lane on to where a full one would is this faint
  private val PROMISE_FILL = 0.28f
  private val PROMISE_RIM = 0.45f
  private val SPLIT_EDGE = 0.03f
  // Stations along a lane: one every STATION cells along its sides where its fill changes (within
  // GLOW_REACH of its light, as it fades in), every SPARSE_STATION where it doesn't, CAP_STATIONS
  // round each end
  private val STATION = 0.25f
  private val SPARSE_STATION = 1.5f
  private val GLOW_REACH = 2f * GLOW_W
  private val CAP_STATIONS = 7
  private val MAX_STATIONS = 200
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
