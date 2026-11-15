package com.gridgame.client.render.projectiles

import com.gridgame.common.model.{Projectile, ProjectileDef}
import com.gridgame.client.gl.ShapeBatch
import ProjectileKit._

/**
 * Cuts: what a blade or a claw leaves in the air as it is swung. One way of drawing them, shared by
 * the Samurai's Crescent Cut, the Reaper's Reap, every claw mark (Bites.clawRake) and the swing
 * behind every tumbling blade (Blades.bladeSpinner, the scythe).
 *
 * A cut is a band with a leading edge and a trailing one, laid out in Glyphs._gx/_gy as `fillRibbon`
 * wants it (the leading run, then the trailing run reversed): [[fang]] lays a crescent across the way
 * it flies, its trailing edge torn into tongues streaming back; [[sliver]] a claw mark along a spine;
 * [[comma]] the swoosh behind a blade's point as it turns. [[paint]] then draws any of them the same
 * way, back to front: the smear it leaves behind it, full where the blade is and nothing where it
 * ends; its ink; its body, the colour at its leading edge deepening to its back; a hot band along
 * the leading edge; and the edge itself, white.
 *
 * They were each a thin flat crescent, or three hairlines side by side, in one tone with a pale edge:
 * the right shape, and nothing a player would call a sword's cut.
 */
private[render] object Slashes {
  // The band being painted is in Glyphs._gx/_gy; this is how deep it is at each station of its
  // leading run, 0 (a horn) to 1 (its deepest), which the smear behind it follows
  private val _th = new Array[Float](32)
  // Scratch for the bands paint makes of it
  private val _wx = new Array[Float](64)
  private val _wy = new Array[Float](64)

  // A tongue of the torn edge, station by station across it: drawn out to a point at its middle,
  // hollow either side, so a row of them is flame and not a saw
  private val TONGUE = Array(0f, 0.1f, 0.36f, 1f, 0.36f, 0.1f)

  /**
   * A crescent lying in the ground plane across the way it flies, as Glyphs.crescentPts lays one
   * (an arc of radius `r` about (cx, cy), `half` radians either side of the screen angle `ang`,
   * squashed by `flat`, `thick` deep at `peak`), but with its trailing edge torn into `tongues`
   * tongues streaming back along the flight (ndx, ndy), each up to `teeth` of the blade's depth
   * there long, of a length of its own, flickering in its own time. Six stations a tongue: returns
   * how many points, 2 * (6 * tongues + 1), so five tongues at most.
   */
  private[projectiles] def fang(cx: Float, cy: Float, ang: Float, r: Float, flat: Float, half: Float,
                                thick: Float, peak: Float, tongues: Int, ndx: Float, ndy: Float,
                                teeth: Float, phase: Double, seed: Int): Int = {
    val segs = 6 * tongues
    val n = 2 * (segs + 1)
    var i = 0
    while (i <= segs) {
      val u = -1f + 2f * i / segs
      val a = ang + u * half
      val c = Math.cos(a).toFloat; val s = Math.sin(a).toFloat
      val d = if (u < peak) (peak - u) / (peak + 1f) else (u - peak) / (1f - peak)
      // Never quite nothing: two points on one spot have no direction to mitre a stroke by
      val prof = Math.max(0.06f, Math.cos(d * (Math.PI / 2)).toFloat)
      val ri = r - thick * prof
      var ix = cx + c * ri; var iy = cy + s * ri * flat
      if (teeth > 0f) {
        val k = i / 6
        val flick = 0.5f + 0.5f * Math.sin(phase * 1.7 + k * 2.4 + seed * 0.7).toFloat
        val len = teeth * thick * prof * TONGUE(i % 6) * (0.45f + 0.55f * flick) * (0.6f + 0.4f * Glyphs.hash01(seed, k))
        ix -= ndx * len; iy -= ndy * len
      }
      Glyphs._gx(i) = cx + c * r; Glyphs._gy(i) = cy + s * r * flat
      Glyphs._gx(n - 1 - i) = ix; Glyphs._gy(n - 1 - i) = iy
      _th(i) = prof
      i += 1
    }
    n
  }

  /** The air peeled back off a point: a wedge `w` wide at (x, y) drawn out to nothing `len` behind
    * it along the flight. A line of one width there is a post. */
  private[projectiles] def peel(sb: ShapeBatch, x: Float, y: Float, ndx: Float, ndy: Float, len: Float, w: Float,
                                r: Float, g: Float, b: Float, a: Float): Unit = {
    if (a <= 0.02f || len < 1f) return
    _polyXs3(0) = x - ndy * w * 0.5f; _polyYs3(0) = y + ndx * w * 0.5f
    _polyXs3(1) = x + ndy * w * 0.5f; _polyYs3(1) = y - ndx * w * 0.5f
    _polyXs3(2) = x - ndx * len; _polyYs3(2) = y - ndy * len
    sb.fillPolygon(_polyXs3, _polyYs3, 3, r, g, b, a)
  }

  /**
   * A cut along a spine: the `m` points in _pvX/_pvY (ProjectileKit's), `ws(i)` wide at each, as a
   * band whose leading run is the side of it toward (ndx, ndy). Returns how many points: 2 * m.
   */
  private[projectiles] def sliver(m: Int, ws: Array[Float], ndx: Float, ndy: Float): Int = {
    val n = 2 * m
    // One side for the whole of it: the side of its chord the flight is on
    val chx = _pvX(m - 1) - _pvX(0); val chy = _pvY(m - 1) - _pvY(0)
    val side = if (-chy * ndx + chx * ndy >= 0f) 1f else -1f
    var wMax = 0.001f
    var i = 0
    while (i < m) { if (ws(i) > wMax) wMax = ws(i); i += 1 }
    i = 0
    while (i < m) {
      val a = if (i == 0) 0 else i - 1; val b = if (i == m - 1) m - 1 else i + 1
      var tx = _pvX(b) - _pvX(a); var ty = _pvY(b) - _pvY(a)
      val tl = Math.sqrt(tx * tx + ty * ty).toFloat
      if (tl < 1e-4f) { tx = chx; ty = chy } else { tx /= tl; ty /= tl }
      val h = Math.max(0.12f, ws(i) * 0.5f)
      val nx = -ty * side * h; val ny = tx * side * h
      Glyphs._gx(i) = _pvX(i) + nx; Glyphs._gy(i) = _pvY(i) + ny
      Glyphs._gx(n - 1 - i) = _pvX(i) - nx; Glyphs._gy(n - 1 - i) = _pvY(i) - ny
      _th(i) = ws(i) / wMax
      i += 1
    }
    n
  }

  /**
   * The swoosh behind a point turning about (cx, cy) in the ground plane: a comma, `thick` deep where
   * the point is now (at `tipAng`, `rOut` out) and drawn out to nothing `sweep` radians back the way
   * it came. Its leading run is its outer edge. Returns how many points: 2 * (segs + 1).
   */
  private[projectiles] def comma(cx: Float, cy: Float, tipAng: Float, sweep: Float, rOut: Float, thick: Float,
                                 flat: Float, segs: Int): Int = {
    val n = 2 * (segs + 1)
    var i = 0
    while (i <= segs) {
      val t = i.toFloat / segs
      val a = tipAng - sweep * (1f - t)
      val c = Math.cos(a).toFloat; val s = Math.sin(a).toFloat
      val prof = Math.max(0.05f, Math.pow(t, 0.75).toFloat)
      val ri = rOut - thick * prof
      Glyphs._gx(i) = cx + c * rOut; Glyphs._gy(i) = cy + s * rOut * flat
      Glyphs._gx(n - 1 - i) = cx + c * ri; Glyphs._gy(n - 1 - i) = cy + s * ri * flat
      _th(i) = prof
      i += 1
    }
    n
  }

  /**
   * Paint the band of `n` points in Glyphs._gx/_gy, flying along (ndx, ndy): the smear it leaves
   * (`wake` long behind its deepest part, in its main colour, fading to nothing), its ink (`inkW`
   * wide, half of it showing outside), its body from the main colour (mr, mg, mb) at its leading
   * edge to the deep one (dr, dg, db) at its back, the hot band (hr, hg, hb) over the leading
   * `hotK` of it, and its leading edge in white. `a` is the alpha of all of it, `wakeA` the smear's
   * where it leaves the blade.
   */
  private[projectiles] def paint(sb: ShapeBatch, n: Int, ndx: Float, ndy: Float, wake: Float, inkW: Float,
                                 ir: Float, ig: Float, ib: Float, dr: Float, dg: Float, db: Float,
                                 mr: Float, mg: Float, mb: Float, hr: Float, hg: Float, hb: Float,
                                 a: Float, hotK: Float = 0.3f, edgeW: Float = 1.7f, wakeA: Float = 0.62f): Unit = {
    if (a <= 0.01f || n < 4) return
    val gx = Glyphs._gx; val gy = Glyphs._gy
    val m = n / 2
    var i = 0
    if (wake > 0f) {
      while (i < m) {
        val j = n - 1 - i
        val midX = (gx(i) + gx(j)) * 0.5f; val midY = (gy(i) + gy(j)) * 0.5f
        _wx(i) = midX; _wy(i) = midY
        _wx(j) = midX - ndx * wake * _th(i); _wy(j) = midY - ndy * wake * _th(i)
        i += 1
      }
      sb.fillRibbonGradient(_wx, _wy, n, mr, mg, mb, wakeA * a, mr, mg, mb, 0f)
    }
    if (inkW > 0f) sb.strokePolygon(gx, gy, n, inkW, ir, ig, ib, 0.94f * a)
    sb.fillRibbonGradient(gx, gy, n, mr, mg, mb, a, dr, dg, db, a)
    if (hotK > 0f) {
      i = 0
      while (i < m) {
        val j = n - 1 - i
        // Thinner toward the horns than the blade is, so it is a highlight on it and not a stripe
        val k = hotK * (0.35f + 0.65f * _th(i))
        _wx(i) = gx(i); _wy(i) = gy(i)
        _wx(j) = gx(i) + (gx(j) - gx(i)) * k; _wy(j) = gy(i) + (gy(j) - gy(i)) * k
        i += 1
      }
      sb.fillRibbonGradient(_wx, _wy, n, 1f, 1f, 1f, a, hr, hg, hb, a)
    }
    if (edgeW > 0f) sb.strokePolyline(gx, gy, m, edgeW, 1f, 1f, 1f, 0.97f * a)
  }

  private val _wa = new Array[Float](64)

  /**
   * Paint the [[comma]] of `n` points in Glyphs._gx/_gy as the smear a turning edge leaves: nothing
   * where it begins and full where the edge is now, in (r, g, b), with a hot rim (hr, hg, hb) along
   * its outer edge and the edge itself in white. No ink: inked, a swoosh is a second blade on the
   * weapon, a sickle on every sword.
   */
  private[projectiles] def smear(sb: ShapeBatch, n: Int, r: Float, g: Float, b: Float,
                                 hr: Float, hg: Float, hb: Float, a: Float): Unit = {
    if (a <= 0.01f || n < 4) return
    val gx = Glyphs._gx; val gy = Glyphs._gy
    val m = n / 2
    var i = 0
    while (i < m) {
      val j = n - 1 - i
      val al = _th(i) * _th(i)
      _wa(i) = al; _wa(j) = al * 0.55f
      _wx(i) = gx(i); _wy(i) = gy(i)
      _wx(j) = gx(i) + (gx(j) - gx(i)) * 0.42f; _wy(j) = gy(i) + (gy(j) - gy(i)) * 0.42f
      i += 1
    }
    sb.fillRibbonSmooth(gx, gy, _wa, n, r, g, b, 0.88f * a)
    i = 0
    while (i < m) { _wa(n - 1 - i) = _wa(i); i += 1 }
    sb.fillRibbonSmooth(_wx, _wy, _wa, n, hr, hg, hb, a)
    sb.strokePolylineTapered(gx, gy, m, 0.3f, 1.8f, 1f, 1f, 1f, 0f, a)
  }

  /** A petal, or a scrap of anything, turned over as it falls: an inked diamond `s` long at (x, y). */
  private def petal(sb: ShapeBatch, x: Float, y: Float, rot: Double, s: Float,
                    ir: Float, ig: Float, ib: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    if (a <= 0.02f) return
    val c = Math.cos(rot).toFloat * s; val sn = Math.sin(rot).toFloat * s
    _polyXs4(0) = x + c; _polyYs4(0) = y + sn * 0.6f
    _polyXs4(1) = x - sn * 0.5f; _polyYs4(1) = y + c * 0.3f
    _polyXs4(2) = x - c; _polyYs4(2) = y - sn * 0.6f
    _polyXs4(3) = x + sn * 0.5f; _polyYs4(3) = y - c * 0.3f
    sb.strokePolygon(_polyXs4, _polyYs4, 4, 1.4f, ir, ig, ib, 0.85f * a)
    sb.fillPolygon(_polyXs4, _polyYs4, 4, r, g, b, a)
  }

  /**
   * The Samurai's Crescent Cut: one great cut of blue-white light flying broadside, as wide as it
   * strikes, held off the ground over its own shadow. It is swung open from one horn as it leaves
   * the sword; its back is torn into tongues of light and smears out behind it; a glint runs along
   * its edge; its horns score the ground and peel the air back; and cherry blossom is turned over
   * all through its wake. It was a thin flat crescent in one blue, with four streaks and three
   * petals behind it.
   */
  private[projectiles] def drawSwordWave(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 37) * 0.4
    computeAllDynamics(proj, 0.5f, 0.72f, 1f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx
    // As wide as it cuts: 34 for the 1.8 cells either side the ring of eight struck, wider for the
    // one Crescent Cut it is now
    val r = 34f * ds * ProjectileDef.get(proj.projectileType).hitRadius / 1.8f
    val flat = 0.55f
    // Swung open from one horn over its first cell or so
    val open = clampF(_lifePct / 0.16f, 0f, 1f)
    val ease = 1f - (1f - open) * (1f - open)
    val half = 1.28f * (0.3f + 0.7f * ease)
    val ang = Math.atan2(ndy, ndx).toFloat - (1f - ease) * 0.75f
    val lift = 10f * ds
    val thick = 21f * ds
    val by = sy - lift

    // Its shadow on the ground under it
    var n = Glyphs.crescentPts(sx, sy + 2f, ang, r * 0.96f, flat, half, thick * 0.7f, 0f, 12)
    sb.fillRibbon(Glyphs._gx, Glyphs._gy, n, 0.02f, 0.04f, 0.12f, 0.3f * a)
    // What its horns do: score the ground, and peel the air back in a long streak each
    var side = -1
    while (side <= 1) {
      val ha = ang + side * half
      val hx = sx + Math.cos(ha).toFloat * r; val hy = sy + Math.sin(ha).toFloat * r * flat
      // Drawn in toward the middle of the wake as they go, as the arcs behind are
      var tx = ndx + (sx - hx) / r * 0.3f; var ty = ndy + (sy - hy) / r * 0.3f
      val tl = Math.sqrt(tx * tx + ty * ty).toFloat
      tx /= tl; ty /= tl
      peel(sb, hx, hy + 2f, tx, ty, 40f * ds, 3f * ds, 0.03f, 0.06f, 0.2f, 0.34f * a * ease)
      peel(sb, hx - ndx * 2f, hy - lift, tx, ty, 32f * ds, 5f * ds, 0.82f, 0.93f, 1f, 0.62f * a * ease)
      side += 2
    }
    // The air it has cut: two thin arcs of it left hanging behind
    var g = 2
    while (g >= 1) {
      val back = (16f + g * 15f) * ds
      n = Glyphs.crescentPts(sx - ndx * back, by - ndy * back, ang, r * (1f - g * 0.07f), flat, half * (1f - g * 0.1f),
        (6.5f - g * 1.6f) * ds, 0f, 12)
      sb.fillRibbon(Glyphs._gx, Glyphs._gy, n, 0.74f, 0.88f, 1f, (0.6f - g * 0.2f) * a * ease)
      g -= 1
    }
    // Cherry blossom turned over all through its wake
    var i = 0
    while (i < 8) {
      val t = Glyphs.loop(tick, 0.03, i / 8.0 + proj.id * 0.29)
      val lat = hash2(proj.id + 3, i) * r * 0.9f
      val back = (4f + t * 72f) * ds
      val flutter = Math.sin(phase * 0.9 + i * 1.9).toFloat * 4f * ds
      val cx = sx - ndx * back + px * lat + px * flutter
      val cy = by - ndy * back + py * lat * flat - (1f - t) * 7f * ds + t * t * 12f * ds
      val pa = Math.min(1f, t * 6f) * (1f - t * t) * a
      val pale = (i & 1) == 0
      petal(sb, cx, cy, phase * 0.7 + i * 2.3, (4.4f + 1.6f * Glyphs.hash01(proj.id, i + 20)) * ds, 0.5f, 0.1f, 0.26f,
        1f, if (pale) 0.8f else 0.62f, if (pale) 0.88f else 0.78f, pa)
      i += 1
    }
    // The cut itself
    n = fang(sx, by, ang, r, flat, half, thick, 0f, 5, ndx, ndy, 1.5f * ease, phase, proj.id)
    paint(sb, n, ndx, ndy, 36f * ds * ease, 3.4f, 0.04f, 0.07f, 0.3f, 0.14f, 0.28f, 0.9f,
      0.3f, 0.66f, 1f, 0.8f, 0.94f, 1f, a, 0.26f, 2f)
    // A glint running along its edge, and one that stays at its horn
    val gt = Glyphs.loop(tick, 0.024, proj.id * 0.37)
    val ga = ang + (gt * 2f - 1f) * half * 0.86f
    val gs = Math.sin(gt * Math.PI).toFloat
    sb.fillStarFlare(sx + Math.cos(ga).toFloat * r, by + Math.sin(ga).toFloat * r * flat, 17f * ds * gs, 2.8f,
      0.55f, 0.45f, 1f, 1f, 1f, 0.95f * a)
    val tw = 0.6f + 0.4f * Math.sin(phase * 2.1).toFloat
    val ta = ang - half * 0.97f
    sb.fillStarFlare(sx + Math.cos(ta).toFloat * r, by + Math.sin(ta).toFloat * r * flat, 9f * ds * tw, 2f,
      0.2f, 0.5f, 1f, 1f, 1f, 0.9f * a)
    drawChargeCrackle(sx, by, 22f * ds, 0.62f, 0.76f, 1f, a, sb, phase, proj.chargeLevel)
    drawReturnGhosts(sx, by, 22f * ds, 0.62f, 0.76f, 1f, a, sb, proj)
  }

  /** A soul dragged along: a pale teardrop with two dark eyes, its tail behind it. */
  private def soul(sb: ShapeBatch, x: Float, y: Float, ndx: Float, ndy: Float, s: Float, phase: Double, a: Float): Unit = {
    if (a <= 0.03f) return
    val n = Glyphs.flamePts(x, y, s, -ndx, -ndy, s * 3.4f, s * 3.4f, s * 0.5f, s * 0.5f, phase, 7)
    sb.strokePolygon(Glyphs._gx, Glyphs._gy, n, 1.5f, 0.14f, 0.05f, 0.3f, 0.8f * a)
    sb.fillRibbon(Glyphs._gx, Glyphs._gy, n, 0.9f, 0.84f, 1f, a)
    val qx = -ndy; val qy = ndx
    sb.fillOval(x + qx * s * 0.42f, y + qy * s * 0.42f, s * 0.24f, s * 0.3f, 0.14f, 0.05f, 0.3f, a, 6)
    sb.fillOval(x - qx * s * 0.42f, y - qy * s * 0.42f, s * 0.24f, s * 0.3f, 0.14f, 0.05f, 0.3f, a, 6)
  }

  /**
   * The Reaper's Reap: the scythe's blade alone, spectral, sweeping broadside through walls: a great
   * violet crescent deepest toward its heel, swung open as it leaves, its back burning away in
   * tongues of ghost-fire, and the souls it has reaped dragged along behind it, each with its eyes.
   * It was a thin crescent in one violet with five streaks behind it.
   */
  private[projectiles] def drawReap(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 31) * 0.4
    computeAllDynamics(proj, 0.62f, 0.42f, 0.95f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx
    val open = clampF(_lifePct / 0.14f, 0f, 1f)
    val ease = 1f - (1f - open) * (1f - open)
    val half = 1.32f * (0.3f + 0.7f * ease)
    val ang = Math.atan2(ndy, ndx).toFloat + (1f - ease) * 0.7f
    val r = 48f * ds
    val flat = 0.55f
    val thick = 24f * ds
    // The air it parts, off each horn
    var side = -1
    while (side <= 1) {
      val ha = ang + side * half
      val hx = sx + Math.cos(ha).toFloat * r; val hy = sy + Math.sin(ha).toFloat * r * flat
      var tx = ndx + (sx - hx) / r * 0.3f; var ty = ndy + (sy - hy) / r * 0.3f
      val tl = Math.sqrt(tx * tx + ty * ty).toFloat
      tx /= tl; ty /= tl
      peel(sb, hx - ndx * 2f, hy, tx, ty, 32f * ds, 5f * ds, 0.82f, 0.7f, 1f, 0.6f * a * ease)
      side += 2
    }
    // Where it was: two arcs of it left hanging
    var g = 2
    while (g >= 1) {
      val back = (18f + g * 16f) * ds
      val gn = Glyphs.crescentPts(sx - ndx * back, sy - ndy * back, ang, r * (1f - g * 0.07f), flat, half * (1f - g * 0.1f),
        (7f - g * 1.8f) * ds, 0.35f, 12)
      sb.fillRibbon(Glyphs._gx, Glyphs._gy, gn, 0.7f, 0.52f, 1f, (0.58f - g * 0.2f) * a * ease)
      g -= 1
    }
    // The souls it drags, streaming back out of its hollow
    var i = 0
    while (i < 5) {
      val t = Glyphs.loop(tick, 0.026, i / 5.0 + proj.id * 0.23)
      val lat = hash2(proj.id + 5, i) * r * 0.7f
      val back = (10f + t * 66f) * ds
      val bob = Math.sin(phase * 1.1 + i * 2.2).toFloat * 3.5f * ds
      soul(sb, sx - ndx * back + px * (lat + bob), sy - ndy * back + py * (lat + bob) * flat - t * 9f * ds,
        ndx, ndy, (4.6f - t * 1.6f) * ds, phase + i, Math.min(1f, t * 5f) * (1f - t * t) * a * ease)
      i += 1
    }
    val n = fang(sx, sy, ang, r, flat, half, thick, 0.35f, 5, ndx, ndy, 1.9f * ease, phase * 0.8, proj.id)
    paint(sb, n, ndx, ndy, 40f * ds * ease, 3.6f, 0.08f, 0.03f, 0.22f, 0.26f, 0.1f, 0.6f,
      0.6f, 0.36f, 0.98f, 0.9f, 0.8f, 1f, a, 0.26f, 2f)
    val gt = Glyphs.loop(tick, 0.02, proj.id * 0.41)
    val ga = ang + (gt * 2f - 1f) * half * 0.86f
    sb.fillStarFlare(sx + Math.cos(ga).toFloat * r, sy + Math.sin(ga).toFloat * r * flat,
      16f * ds * Math.sin(gt * Math.PI).toFloat, 2.6f, 0.55f, 0.45f, 1f, 0.96f, 1f, 0.95f * a)
    drawChargeCrackle(sx, sy, r * 0.5f, 0.62f, 0.42f, 0.95f, a, sb, phase, proj.chargeLevel)
  }
}
