package com.gridgame.client.render.blasts

import GLBlastRenderers._
import BlastKit._

/** The building blocks the engines share: the flash, rings racing out, the scorch, cracks, debris of
  * every material, smoke puffs, flame tongues, spikes, tendrils, bolts and burnt scars, sound rings,
  * motes, swirls, glints, splats and bubbles. */
private[blasts] object Pieces {
  /** The flash where it went off, over the first `len` of its life: a soft disc `size` of the
    * footprint across and a hot core, lying on the ground. */
  private[blasts] def flash(len: Float, size: Float, r: Float, g: Float, b: Float, strength: Float = 1f, at: Float = 0f): Unit = {
    if (layer != AIR || T < at || T >= at + len) return
    val p = (T - at) / len
    val a = (1f - p) * (1f - p) * strength
    val rad = W * size * (0.55f + 0.45f * easeOut(p))
    sb.fillOvalSoft(cx, cy - H * 0.12f, rad, rad * 0.62f, r, g, b, 0.8f * a, 0f, 22)
    sb.fillOval(cx, cy - H * 0.12f, rad * 0.36f, rad * 0.26f, mix(r, 1f, 0.8f), mix(g, 1f, 0.8f), mix(b, 1f, 0.8f), a, 16)
  }

  /** A ring racing out along the ground over [a, b] of the blast's life, to `reach` of the
    * footprint, thinning as it goes, inked so it reads on snow and sand. */
  private[blasts] def shockRing(a: Float, b: Float, w0: Float, r: Float, g: Float, bl: Float, alpha: Float,
                        reach: Float = 1f): Unit = {
    if (layer != GROUND) return
    val p = seg(T, a, b)
    if (p <= 0f || p >= 1f) return
    val q = easeOut3(p) * reach
    val lw = w0 * k * (1f - 0.6f * p)
    val al = alpha * (1f - p) * Math.min(1f, p * 10f)
    if (al <= 0.01f) return
    sb.strokeOval(cx, cy, W * q, H * q, lw + 3f, ink(r), ink(g), ink(bl), 0.55f * al, 24)
    sb.strokeOval(cx, cy, W * q, H * q, lw, r, g, bl, al, 24)
  }

  /** A thin ring standing at exactly the edge of the footprint: who was in it. */
  private[blasts] def edgeRing(a: Float, b: Float, w: Float, r: Float, g: Float, bl: Float, alpha: Float): Unit = {
    if (layer != GROUND) return
    val al = alpha * life(seg(T, a, b), 0.15f, 0.6f)
    if (al <= 0.01f || T < a || T > b) return
    sb.strokeOval(cx, cy, W, H, w + 2.4f, ink(r), ink(g), ink(bl), 0.45f * al, 24)
    sb.strokeOval(cx, cy, W, H, w, r, g, bl, al, 24)
  }

  /** A soft patch on the ground, `size` of the footprint: in over the first tenth of the
    * blast's life, out from `out`. The only fill that covers the whole blast. */
  private[blasts] def scorch(size: Float, r: Float, g: Float, b: Float, a: Float, out: Float = 0.6f): Unit = {
    if (layer != GROUND) return
    val al = a * Math.min(1f, T * 10f) * tail(T, out)
    if (al <= 0.01f) return
    sb.fillOvalSoft(cx, cy, W * size, H * size, r, g, b, al, 0f, 22)
  }

  /** A ground point: angle `th` round the footprint, `rho` of the way out. */
  @inline private[blasts] def gx(c: Float, rho: Float): Float = cx + c * rho * W

  @inline private[blasts] def gy(s: Float, rho: Float): Float = cy + s * rho * H

  /**
   * `n` jagged cracks out from the middle to `reach` of the footprint, growing over [g0, g1] and
   * fading from 60%, each with a fork. `glow` lights a thin line down their middles (lava, holy
   * light, runes), `ga` its strength.
   */
  private[blasts] def cracks(n: Int, reach: Float, g0: Float, g1: Float, width: Float,
                     r: Float, g: Float, b: Float, a: Float,
                     glowR: Float = 0f, glowG: Float = 0f, glowB: Float = 0f, ga: Float = 0f, sd: Int = 0): Unit = {
    if (layer != GROUND) return
    val grow = easeOut(seg(T, g0, g1))
    val al = a * tail(T, 0.6f)
    if (grow <= 0.01f || al <= 0.01f) return
    val s0 = seed + sd * 131
    var i = 0
    while (i < n) {
      val th = (i + 0.5f * hash(s0, i * 7 + 1)) * TWO_PI / n
      val c = cosf(th); val s = sinf(th)
      val len = reach * (0.65f + 0.35f * h01(s0, i * 7 + 2)) * grow
      var j = 0
      while (j < 5) {
        val u = j / 4f
        val rho = 0.08f + (len - 0.08f) * u
        val off = if (j == 0) 0f else hash(s0 + i * 13, j) * 0.06f
        _px(j) = cx + (c * rho - s * off) * W
        _py(j) = cy + (s * rho + c * off) * H
        j += 1
      }
      if (len > 0.1f) {
        sb.strokePolylineTapered(_px, _py, 5, width * k, 0.7f, r, g, b, al, al * 0.6f)
        if (ga > 0f) sb.strokePolylineTapered(_px, _py, 5, width * 0.34f * k, 0.3f, glowR, glowG, glowB, ga * al, 0.2f * ga * al)
        // A fork off the third point, out to one side
        val side = if (hash(s0, i * 7 + 3) > 0f) 1f else -1f
        val fa = th + side * 0.6f
        val fl = len * 0.35f
        _xs(0) = _px(2); _ys(0) = _py(2)
        _xs(1) = _px(2) + (cosf(fa) * fl * 0.5f) * W + hash(s0, i * 7 + 4) * 2f
        _ys(1) = _py(2) + (sinf(fa) * fl * 0.5f) * H
        _xs(2) = _px(2) + cosf(fa + side * 0.2f) * fl * W
        _ys(2) = _py(2) + sinf(fa + side * 0.2f) * fl * H
        sb.strokePolylineTapered(_xs, _ys, 3, width * 0.6f * k, 0.5f, r, g, b, al * 0.9f, al * 0.4f)
      }
      i += 1
    }
  }

  // ── Debris ──
  final val D_CHIP = 0      // rock, dirt or metal: an inked triangle with a lit edge
  final val D_SHARD = 1     // glass or ice: a thin bright diamond
  final val D_SPLINTER = 2  // wood: a thin inked sliver
  final val D_BONE = 3      // a little bone with a knob at each end
  final val D_LEAF = 4      // a leaf with a midrib
  final val D_FEATHER = 5   // a feather with its quill
  final val D_DROP = 6      // a drop of liquid with a highlight
  final val D_GLOB = 7      // a fat inked blob with a gloss (lava, mud, sludge)
  final val D_NOTE = 8      // a music note
  final val D_SQUARE = 9    // a nanite, a spark of data
  final val D_SPARK = 10    // a hot streak along its flight, drawn additive
  final val D_HEX = 11      // a hexagonal fragment of something built
  final val D_BLOCK = 12    // a chunky block of stone
  final val D_FLECK = 13    // a tiny speck (spice, grit), no ink

  /**
   * `n` pieces flung out from the middle over [t0, t1] of the blast's life, each on its own
   * arc: out to `reach` of the footprint at most, `lift` units up at the top of the arc,
   * landing partway and fading by the end. `size` in units at the size of a 3-cell blast.
   * `shadow` lays a little shadow under the ones in the air, which is what shows their height.
   */
  private[blasts] def debris(kind: Int, n0: Int, t0: Float, t1: Float, reach: Float, lift: Float, size: Float,
                     r: Float, g: Float, b: Float, sd: Int, shadow: Boolean = false, from: Float = 0.05f): Unit = {
    if (layer != AIR) return
    val p = seg(T, t0, t1)
    if (p <= 0f || p >= 1f) return
    val n = Math.max(1, (n0 * det + 0.5f).toInt)
    val s0 = seed * 7 + sd * 977
    val additive = kind == D_SPARK
    if (additive) sb.setAdditiveBlend(true)
    var i = 0
    while (i < n) {
      val hA = hash(s0, i * 5); val hB = h01(s0, i * 5 + 1); val hC = h01(s0, i * 5 + 2)
      val th = (i + 0.45f * hA) * TWO_PI / n
      val c = cosf(th); val s = sinf(th)
      val out = easeOut(Math.min(1f, p * 1.35f))
      val rho = from + (reach * (0.45f + 0.55f * hB) - from) * out
      val land = 0.5f + 0.35f * hC
      val tau = Math.min(1f, p / land)
      val z = lift * (0.5f + 0.5f * hB) * 4f * tau * (1f - tau)
      val x = gx(c, rho)
      val groundY = gy(s, rho)
      val y = groundY - z
      val a = if (p < land) 1f else 1f - (p - land) / (1f - land)
      val sz = size * k * (0.65f + 0.7f * hC)
      if (shadow && z > 3f && a > 0.05f) sb.fillOval(x, groundY, sz * 0.9f, sz * 0.36f, 0f, 0f, 0f, 0.2f * a, 8)
      if (additive) {
        // A streak back along where it has come from
        val q = Math.max(0f, p - 0.06f)
        val outQ = easeOut(Math.min(1f, q * 1.35f))
        val rq = from + (reach * (0.45f + 0.55f * hB) - from) * outQ
        val tq = Math.min(1f, q / land)
        val zq = lift * (0.5f + 0.5f * hB) * 4f * tq * (1f - tq)
        val bx = gx(c, rq); val by = gy(s, rq) - zq
        if (a > 0.03f) {
          sb.strokeLineSoft(x, y, bx, by, sz * 1.1f, r, g, b, 0.8f * a)
          sb.fillOval(x, y, sz * 0.55f, sz * 0.55f, mix(r, 1f, 0.6f), mix(g, 1f, 0.6f), mix(b, 1f, 0.6f), a, 6)
        }
      } else piece(kind, x, y, sz, hA * 3f + p * (5f + 7f * hB) * (if ((i & 1) == 0) 1f else -1f), r, g, b, a)
      i += 1
    }
    if (additive) sb.setAdditiveBlend(false)
  }

  /** Rotate the unit shape in (_xs, _ys) by (c, s), scale it by `sz` and put it at (x, y). */
  @inline private[blasts] def place(n: Int, x: Float, y: Float, sz: Float, c: Float, s: Float): Unit = {
    var i = 0
    while (i < n) {
      val lx = _xs(i); val ly = _ys(i)
      _xs(i) = x + (lx * c - ly * s) * sz
      _ys(i) = y + (lx * s + ly * c) * sz
      i += 1
    }
  }

  /** One piece of debris, `sz` across, turned to `ang`. */
  private[blasts] def piece(kind: Int, x: Float, y: Float, sz: Float, ang: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    if (a <= 0.02f) return
    val c = cosf(ang); val s = sinf(ang)
    (kind: @scala.annotation.switch) match {
      case D_CHIP | D_BLOCK =>
        val block = kind == D_BLOCK
        var pass = 0
        while (pass < 2) {
          val grow = if (pass == 0) 1.35f else 1f
          if (block) {
            _xs(0) = -0.9f; _ys(0) = -0.6f; _xs(1) = 0.8f; _ys(1) = -0.75f
            _xs(2) = 0.95f; _ys(2) = 0.6f; _xs(3) = -0.75f; _ys(3) = 0.7f
          } else {
            _xs(0) = 1f; _ys(0) = 0f; _xs(1) = -0.6f; _ys(1) = 0.78f; _xs(2) = -0.5f; _ys(2) = -0.72f
          }
          val m = if (block) 4 else 3
          place(m, x, y, sz * grow, c, s)
          if (pass == 0) sb.fillPolygon(_xs, _ys, m, ink(r), ink(g), ink(b), 0.9f * a)
          else {
            sb.fillPolygon(_xs, _ys, m, r, g, b, a)
            sb.strokeLine(_xs(0), _ys(0), _xs(1), _ys(1), 1.1f, lit(r), lit(g), lit(b), 0.85f * a)
          }
          pass += 1
        }
      case D_SHARD =>
        _xs(0) = 1.3f; _ys(0) = 0f; _xs(1) = 0f; _ys(1) = 0.36f; _xs(2) = -1f; _ys(2) = 0f; _xs(3) = 0f; _ys(3) = -0.36f
        place(4, x, y, sz, c, s)
        sb.strokePolygon(_xs, _ys, 4, 1.2f, ink(r), ink(g), ink(b), 0.7f * a)
        sb.fillPolygon(_xs, _ys, 4, r, g, b, 0.85f * a)
        sb.strokeLine(_xs(0), _ys(0), _xs(3), _ys(3), 0.9f, 1f, 1f, 1f, 0.9f * a)
      case D_SPLINTER =>
        _xs(0) = 1.4f; _ys(0) = 0f; _xs(1) = -1f; _ys(1) = 0.24f; _xs(2) = -1.2f; _ys(2) = -0.1f; _xs(3) = 0.1f; _ys(3) = -0.2f
        place(4, x, y, sz, c, s)
        sb.strokePolygon(_xs, _ys, 4, 1.3f, ink(r), ink(g), ink(b), 0.85f * a)
        sb.fillPolygon(_xs, _ys, 4, r, g, b, a)
      case D_BONE =>
        val hx = c * sz * 1.05f; val hy = s * sz * 1.05f
        sb.strokeLine(x - hx, y - hy, x + hx, y + hy, sz * 0.55f + 1.8f, 0.2f, 0.17f, 0.14f, 0.9f * a)
        sb.fillOval(x - hx, y - hy, sz * 0.42f + 0.9f, sz * 0.42f + 0.9f, 0.2f, 0.17f, 0.14f, 0.9f * a, 8)
        sb.fillOval(x + hx, y + hy, sz * 0.42f + 0.9f, sz * 0.42f + 0.9f, 0.2f, 0.17f, 0.14f, 0.9f * a, 8)
        sb.strokeLine(x - hx, y - hy, x + hx, y + hy, sz * 0.55f, r, g, b, a)
        sb.fillOval(x - hx, y - hy, sz * 0.42f, sz * 0.42f, r, g, b, a, 8)
        sb.fillOval(x + hx, y + hy, sz * 0.42f, sz * 0.42f, r, g, b, a, 8)
      case D_LEAF | D_FEATHER =>
        val len = if (kind == D_LEAF) 1.2f else 1.6f
        val wid = if (kind == D_LEAF) 0.52f else 0.36f
        // A lens: pointed at both ends, convex
        _xs(0) = len; _ys(0) = 0f; _xs(1) = len * 0.3f; _ys(1) = wid; _xs(2) = -len * 0.4f; _ys(2) = wid * 0.85f
        _xs(3) = -len; _ys(3) = 0f; _xs(4) = -len * 0.4f; _ys(4) = -wid * 0.85f; _xs(5) = len * 0.3f; _ys(5) = -wid
        place(6, x, y, sz, c, s)
        sb.strokePolygon(_xs, _ys, 6, 1.2f, ink(r), ink(g), ink(b), 0.85f * a)
        sb.fillPolygon(_xs, _ys, 6, r, g, b, a)
        val qx = c * sz * len * 1.25f; val qy = s * sz * len * 1.25f
        sb.strokeLine(x - qx, y - qy, x + qx * 0.8f, y + qy * 0.8f, 0.9f, ink(r) * 2f, ink(g) * 2f, ink(b) * 2f, 0.8f * a)
      case D_DROP =>
        sb.fillOval(x, y, sz * 0.62f + 1f, sz * 0.78f + 1f, ink(r), ink(g), ink(b), 0.75f * a, 8)
        sb.fillOval(x, y, sz * 0.62f, sz * 0.78f, r, g, b, a, 8)
        sb.fillOval(x - sz * 0.2f, y - sz * 0.28f, sz * 0.2f, sz * 0.24f, 1f, 1f, 1f, 0.8f * a, 6)
      case D_GLOB =>
        // A blob stretched a little along its spin, a tone lighter on its upper side, a small gloss
        val sx = sz * (1f + 0.18f * Math.abs(c)); val sy = sz * (0.84f + 0.14f * Math.abs(s))
        sb.fillOval(x, y, sx + 1.5f, sy + 1.5f, ink(r), ink(g), ink(b), 0.9f * a, 9)
        sb.fillOval(x, y, sx, sy, r, g, b, a, 9)
        sb.fillOval(x - sx * 0.14f, y - sy * 0.2f, sx * 0.66f, sy * 0.5f, mix(r, lit(r), 0.55f), mix(g, lit(g), 0.55f),
          mix(b, lit(b), 0.55f), a, 8)
        sb.fillOval(x - sx * 0.38f, y - sy * 0.4f, sx * 0.17f, sy * 0.13f, 1f, 1f, 0.92f, 0.75f * a, 6)
      case D_NOTE =>
        // A head, a stem and a flag
        val hx = x; val hy = y
        sb.fillOval(hx, hy, sz * 0.62f + 1.3f, sz * 0.46f + 1.3f, ink(r), ink(g), ink(b), 0.9f * a, 9)
        sb.strokeLine(hx + sz * 0.5f, hy, hx + sz * 0.5f, hy - sz * 2.1f, 2.6f, ink(r), ink(g), ink(b), 0.9f * a)
        sb.fillOval(hx, hy, sz * 0.62f, sz * 0.46f, r, g, b, a, 9)
        sb.strokeLine(hx + sz * 0.5f, hy, hx + sz * 0.5f, hy - sz * 2.1f, 1.3f, r, g, b, a)
        _xs(0) = hx + sz * 0.5f; _ys(0) = hy - sz * 2.1f
        _xs(1) = hx + sz * 1.25f; _ys(1) = hy - sz * 1.35f
        _xs(2) = hx + sz * 0.5f; _ys(2) = hy - sz * 1.45f
        sb.fillPolygon(_xs, _ys, 3, r, g, b, a)
      case D_SQUARE =>
        _xs(0) = -0.7f; _ys(0) = -0.7f; _xs(1) = 0.7f; _ys(1) = -0.7f; _xs(2) = 0.7f; _ys(2) = 0.7f; _xs(3) = -0.7f; _ys(3) = 0.7f
        place(4, x, y, sz, c, s)
        sb.fillPolygon(_xs, _ys, 4, r, g, b, a)
        sb.fillOval(x, y, sz * 0.3f, sz * 0.3f, lit(r), lit(g), lit(b), a, 6)
      case D_HEX =>
        sb.fillOval(x, y, sz + 1.2f, sz * 0.8f + 1.2f, ink(r), ink(g), ink(b), 0.85f * a, 6)
        sb.fillOval(x, y, sz, sz * 0.8f, r, g, b, a, 6)
        sb.fillOval(x - sz * 0.2f, y - sz * 0.2f, sz * 0.4f, sz * 0.3f, lit(r), lit(g), lit(b), a, 6)
      case _ => // D_FLECK
        sb.fillOval(x, y, sz * 0.5f, sz * 0.4f, r, g, b, a, 5)
    }
  }

  /**
   * A cluster of `n` puffs — smoke, dust, steam, gas — inked as one mass: each rises `rise`
   * units and swells from `s0` to `s1` units (at the size of a 3-cell blast) over its own part
   * of [t0, t1], thinning away. They start `spread` of the footprint out and drift `outward`
   * further; `flat` squashes them (dust along the ground), `lift` raises the whole cluster.
   * Drawn ink, body, light, so the line runs round the mass and not across every seam.
   */
  private[blasts] def puffs(n0: Int, t0: Float, t1: Float, spread: Float, rise: Float, s0: Float, s1: Float,
                    r: Float, g: Float, b: Float, a0: Float, sd: Int,
                    outward: Float = 0f, flat: Float = 0.8f, lift: Float = 0f, inkK: Float = 1f): Unit = {
    if (layer != AIR) return
    if (T <= t0 || T >= t1) return
    val n = Math.min(64, Math.max(1, (n0 * (0.5f + 0.5f * det) + 0.5f).toInt))
    val s00 = seed * 3 + sd * 541
    var m = 0
    var i = 0
    while (i < n) {
      val hA = hash(s00, i * 3); val hB = h01(s00, i * 3 + 1); val hC = h01(s00, i * 3 + 2)
      val start = t0 + (t1 - t0) * 0.22f * hC
      val tau = seg(T, start, t1)
      if (tau > 0f && tau < 1f) {
        val th = (i + 0.5f * hA) * TWO_PI / n
        val c = cosf(th); val s = sinf(th)
        val e = easeOut(tau)
        val rho = spread * (0.25f + 0.75f * hB) + outward * e
        _ux(m) = gx(c, rho)
        _uy(m) = gy(s, rho) - lift * k - rise * k * e * (0.65f + 0.35f * hB)
        _us(m) = (s0 + (s1 - s0) * e) * k * (0.75f + 0.5f * hC)
        _ua(m) = a0 * Math.min(1f, tau * 7f) * (1f - tau) * (1f - 0.4f * tau)
        m += 1
      }
      i += 1
    }
    if (m == 0) return
    val ir = mix(r, ink(r), 0.8f); val ig = mix(g, ink(g), 0.8f); val ib = mix(b, ink(b), 0.8f)
    // The lit side is a tone up, not a highlight: lit hard, every puff was a glass ball
    val lr = mix(r, lit(r), 0.5f); val lg = mix(g, lit(g), 0.5f); val lb = mix(b, lit(b), 0.5f)
    var pass = 0
    while (pass < 3) {
      i = 0
      while (i < m) {
        val x = _ux(i); val y = _uy(i); val sz = _us(i); val a = _ua(i)
        (pass: @scala.annotation.switch) match {
          case 0 => sb.fillOval(x, y, sz + 1.8f, sz * flat + 1.8f, ir, ig, ib, 0.55f * a * inkK, 18)
          case 1 => sb.fillOval(x, y, sz, sz * flat, r, g, b, a, 18)
          case _ => sb.fillOval(x - sz * 0.16f, y - sz * 0.2f * flat, sz * 0.72f, sz * 0.62f * flat, lr, lg, lb, 0.9f * a, 14)
        }
        i += 1
      }
      pass += 1
    }
  }

  /** A tongue of flame standing at (bx, by), `h` tall and `w` half-wide at its foot, its tip
    * `lean` to the side: an inked teardrop, its colour, and a hotter heart. */
  private[blasts] def flameTongue(bx: Float, by: Float, w: Float, h: Float, lean: Float, a: Float,
                          or: Float, og: Float, ob: Float, hr: Float, hg: Float, hb: Float,
                          tr: Float = -1f, tg: Float = 0f, tb: Float = 0f): Unit = {
    if (h < 1.5f || a <= 0.02f) return
    val l = Math.max(-w * 0.55f, Math.min(w * 0.55f, lean))
    tongue(bx, by, w + 1.4f, h + 2.2f, l, ink(or), ink(og), ink(ob), 0.85f * a)
    tongue(bx, by, w, h, l, or, og, ob, a)
    tongue(bx, by + w * 0.12f, w * 0.52f, h * 0.6f, l * 0.6f, hr, hg, hb, a)
    // A tip of another colour standing in its top half (the orange over a blue flame)
    if (tr >= 0f) tongue(bx + l * 0.3f, by - h * 0.34f, w * 0.7f, h * 0.66f, l * 0.7f, tr, tg, tb, a)
  }

  private def tongue(bx: Float, by: Float, w: Float, h: Float, lean: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    _xs(0) = bx - w; _ys(0) = by
    _xs(1) = bx - w * 0.8f + lean * 0.3f; _ys(1) = by - h * 0.4f
    _xs(2) = bx + lean; _ys(2) = by - h
    _xs(3) = bx + w * 0.8f + lean * 0.3f; _ys(3) = by - h * 0.4f
    _xs(4) = bx + w; _ys(4) = by
    _xs(5) = bx; _ys(5) = by + w * 0.34f
    sb.fillPolygon(_xs, _ys, 6, r, g, b, a)
  }

  /**
   * A spike standing up out of the ground at (bx, by): `h` tall, `w` half-wide at its foot, its
   * tip `lean` to the side. A lit face and a shaded face meeting along its ridge, inked round.
   * `glass` makes it translucent with a white edge (ice, crystal).
   */
  private[blasts] def spike(bx: Float, by: Float, w: Float, h: Float, lean: Float, a: Float,
                    r: Float, g: Float, b: Float, glass: Boolean = false): Unit = {
    if (h < 1.5f || a <= 0.02f) return
    val tx = bx + lean; val ty = by - h
    _xs(0) = bx - w - 1.4f; _ys(0) = by + 0.6f
    _xs(1) = tx; _ys(1) = ty - 2.2f
    _xs(2) = bx + w + 1.4f; _ys(2) = by + 0.6f
    _xs(3) = bx + w * 0.1f; _ys(3) = by + w * 0.42f + 1.4f
    sb.fillPolygon(_xs, _ys, 4, ink(r), ink(g), ink(b), (if (glass) 0.7f else 0.9f) * a)
    val body = if (glass) 0.82f else 1f
    _xs(0) = bx - w; _ys(0) = by
    _xs(1) = tx; _ys(1) = ty
    _xs(2) = bx + w * 0.1f; _ys(2) = by + w * 0.42f
    sb.fillPolygon(_xs, _ys, 3, mix(r, lit(r), 0.6f), mix(g, lit(g), 0.6f), mix(b, lit(b), 0.6f), body * a)
    _xs(0) = bx + w * 0.1f; _ys(0) = by + w * 0.42f
    _xs(1) = tx; _ys(1) = ty
    _xs(2) = bx + w; _ys(2) = by
    sb.fillPolygon(_xs, _ys, 3, r * 0.72f, g * 0.72f, b * 0.78f, body * a)
    if (glass) sb.strokeLine(bx - w * 0.55f, by - h * 0.1f, tx - w * 0.05f, ty + h * 0.12f, 1.3f, 1f, 1f, 1f, 0.75f * a)
  }

  /**
   * A tendril growing out of the ground at (bx, by): heading off at screen angle `ang0`
   * (-π/2 is straight up), `len` long when `grow` is 1, curling by `curl` radians toward its tip.
   * Inked, then its colour, then a lit line along one side. Returns nothing; its points are
   * left in _px/_py for anything that hangs on it (leaves, suckers, thorns).
   */
  private[blasts] def tendril(bx: Float, by: Float, ang0: Float, len: Float, grow: Float, curl: Float, w0: Float,
                      r: Float, g: Float, b: Float, a: Float, n: Int = 9, wisp: Boolean = false,
                      hr: Float = -1f, hg: Float = 0f, hb: Float = 0f): Unit = {
    if (grow <= 0.02f || a <= 0.02f) return
    var ang = ang0
    var x = bx; var y = by
    val step = len * grow / (n - 1)
    var i = 0
    while (i < n) {
      val u = i.toFloat / (n - 1)
      _px(i) = x; _py(i) = y
      _pw(i) = w0 * (1f - 0.88f * u) + 0.6f
      _pa(i) = if (wisp) a * (1f - u * u) else a
      ang += curl * (0.25f + 1.5f * u) / (n - 1)
      x += cosf(ang) * step
      y += sinf(ang) * step
      i += 1
    }
    var j = 0
    while (j < n) { _pw(j) += 2.6f; _pa(j) *= 0.85f; j += 1 }
    sb.strokePolylineVar(_px, _py, _pw, _pa, n, ink(r), ink(g), ink(b))
    j = 0
    while (j < n) { _pw(j) -= 2.6f; _pa(j) /= 0.85f; j += 1 }
    sb.strokePolylineVar(_px, _py, _pw, _pa, n, r, g, b)
    j = 0
    while (j < n) { _pw(j) *= 0.32f; _pa(j) *= 0.8f; j += 1 }
    if (hr < 0f) sb.strokePolylineVar(_px, _py, _pw, _pa, n, lit(r), lit(g), lit(b))
    else sb.strokePolylineVar(_px, _py, _pw, _pa, n, hr, hg, hb)
  }

  /** A zigzag of electricity from (x0, y0) to (x1, y1): `segs` kinks up to `amp` aside, the same
    * for the same seed. Ink, colour and a white core, thinning toward the far end. */
  private[blasts] def bolt(x0: Float, y0: Float, x1: Float, y1: Float, segs: Int, amp: Float, sd: Int,
                   w: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    if (a <= 0.01f) return
    val dx = x1 - x0; val dy = y1 - y0
    val len = Math.sqrt(dx * dx + dy * dy).toFloat
    if (len < 1f) return
    val nx = -dy / len; val ny = dx / len
    val n = Math.min(segs, 14) + 1
    var i = 0
    while (i < n) {
      val t = i.toFloat / (n - 1)
      // Alternate sides by an uneven amount: the lightning-bolt zigzag, never a smooth worm
      val side = if ((i & 1) == 0) 1f else -1f
      val off = if (i == 0 || i == n - 1) 0f else side * (0.35f + 0.65f * h01(sd, i)) * amp
      _px(i) = x0 + dx * t + nx * off; _py(i) = y0 + dy * t + ny * off
      i += 1
    }
    sb.strokePolylineTapered(_px, _py, n, w + 3f, 2f, ink(r), ink(g), ink(b) + 0.05f, 0.75f * a, 0.3f * a)
    sb.strokePolylineTapered(_px, _py, n, w, w * 0.35f, r, g, b, 0.95f * a, 0.5f * a)
    sb.strokePolylineTapered(_px, _py, n, w * 0.42f, 0.5f, mix(r, 1f, 0.8f), mix(g, 1f, 0.8f), mix(b, 1f, 0.8f), a, 0.5f * a)
  }

  /**
   * A burnt branching scar on the ground, the mark lightning leaves (a Lichtenberg figure):
   * `n` branches out to `reach`, each forking twice. Dark, with a line of the bolt's colour down
   * it that fades well before the scar does.
   */
  private[blasts] def scar(n: Int, reach: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    if (layer != GROUND) return
    val grow = easeOut(seg(T, 0f, 0.12f))
    val al = a * tail(T, 0.55f)
    val glow = a * (1f - seg(T, 0.05f, 0.45f))
    if (al <= 0.01f) return
    var i = 0
    while (i < n) {
      val s0 = seed * 11 + i * 71
      var ang = (i + 0.4f * hash(s0, 1)) * TWO_PI / n
      var x = cx; var y = cy
      val step = reach * grow / 5f
      var j = 0
      while (j < 6) {
        _px(j) = x; _py(j) = y
        ang += hash(s0, j + 2) * 0.55f
        x += cosf(ang) * step * W
        y += sinf(ang) * step * H
        j += 1
      }
      sb.strokePolylineTapered(_px, _py, 6, 3.4f * k, 0.6f, 0.08f, 0.07f, 0.06f, 0.55f * al, 0.25f * al)
      if (glow > 0.01f) sb.strokePolylineTapered(_px, _py, 6, 1.4f * k, 0.3f, r, g, b, glow, 0.2f * glow)
      // Two twigs off it
      var f = 0
      while (f < 2) {
        val at = 2 + f * 2
        val fa = ang + (if ((f & 1) == 0) 0.9f else -0.9f) + hash(s0, 20 + f) * 0.3f
        val fl = step * 1.3f
        val ex = _px(at) + cosf(fa) * fl * W; val ey = _py(at) + sinf(fa) * fl * H
        sb.strokeLine(_px(at), _py(at), ex, ey, 1.8f * k, 0.08f, 0.07f, 0.06f, 0.45f * al)
        if (glow > 0.01f) sb.strokeLine(_px(at), _py(at), ex, ey, 0.8f * k, r, g, b, 0.7f * glow)
        f += 1
      }
      i += 1
    }
  }

  /** Rings of sound: `n` rings, each a set of `arcs` arcs with gaps between, going out one after
    * another over [t0, t1] to `reach`, turning a little as they go. */
  private[blasts] def soundRings(n: Int, arcs: Int, t0: Float, t1: Float, reach: Float, w0: Float,
                         r: Float, g: Float, b: Float, a0: Float, lift: Float = 0f): Unit = {
    if (layer != GROUND) return
    var i = 0
    while (i < n) {
      val start = t0 + (t1 - t0) * 0.5f * i / Math.max(1, n - 1)
      val p = seg(T, start, start + (t1 - t0) * 0.5f)
      if (p > 0f && p < 1f) {
        val q = easeOut(p) * reach
        val rx = W * q; val ry = H * q
        val a = a0 * (1f - p) * Math.min(1f, p * 8f)
        val lw = w0 * k * (1f - 0.5f * p)
        val rot = hash(seed, i) * PI + p * 0.6f
        val span = TWO_PI / arcs
        var j = 0
        while (j < arcs) {
          val st = rot + j * span
          sb.strokeArc(cx, cy - lift * k, rx, ry, st, span * 0.72f, lw + 2.6f, ink(r), ink(g), ink(b), 0.5f * a, 10)
          sb.strokeArc(cx, cy - lift * k, rx, ry, st, span * 0.72f, lw, r, g, b, a, 10)
          j += 1
        }
      }
      i += 1
    }
  }

  /** Motes: `n` little lights rising `rise` units and drifting over [t0, t1], additive. */
  private[blasts] def motes(n0: Int, t0: Float, t1: Float, spread: Float, rise: Float, size: Float,
                    r: Float, g: Float, b: Float, a0: Float, sd: Int): Unit = {
    if (layer != AIR || T <= t0 || T >= t1) return
    val n = Math.max(1, (n0 * det + 0.5f).toInt)
    sb.setAdditiveBlend(true)
    var i = 0
    while (i < n) {
      val s0 = seed * 13 + sd * 97
      val start = t0 + (t1 - t0) * 0.4f * h01(s0, i * 3)
      val tau = seg(T, start, t1)
      if (tau > 0f && tau < 1f) {
        val th = (i + 0.5f * hash(s0, i * 3 + 1)) * TWO_PI / n
        val rho = spread * (0.2f + 0.8f * h01(s0, i * 3 + 2))
        val x = gx(cosf(th), rho) + sinf(tau * 5f + i) * 3f * k
        val y = gy(sinf(th), rho) - rise * k * easeOut(tau)
        val a = a0 * Math.min(1f, tau * 6f) * (1f - tau)
        val sz = size * k * (1f - 0.4f * tau)
        sb.fillOval(x, y, sz * 2.2f, sz * 2.2f, r, g, b, 0.3f * a, 8)
        sb.fillOval(x, y, sz, sz, mix(r, 1f, 0.5f), mix(g, 1f, 0.5f), mix(b, 1f, 0.5f), a, 6)
      }
      i += 1
    }
    sb.setAdditiveBlend(false)
  }

  /** `n` pieces whirled out and up round the middle over [t0, t1]: each turning `turns` times
    * round as it goes out to `reach`, rising to `lift` units and settling as it fades. Leaves,
    * feathers, nanites — what a gust or a swarm carries rather than what a blast throws. */
  private[blasts] def swirl(kind: Int, n0: Int, t0: Float, t1: Float, reach: Float, lift: Float, turns: Float, size: Float,
                    r: Float, g: Float, b: Float, sd: Int): Unit = {
    if (layer != AIR) return
    val p = seg(T, t0, t1)
    if (p <= 0f || p >= 1f) return
    val n = Math.max(1, (n0 * det + 0.5f).toInt)
    val s0 = seed * 83 + sd * 29
    var i = 0
    while (i < n) {
      val hB = h01(s0, i * 3); val hC = h01(s0, i * 3 + 1)
      val tau = seg(p, 0.12f * hC, 1f)
      if (tau > 0f && tau < 1f) {
        val dir = if ((i & 1) == 0) 1f else 0.8f
        val th = (i + 0.5f * hash(s0, i * 3 + 2)) * TWO_PI / n + turns * TWO_PI * easeOut(tau) * dir
        val rho = reach * (0.35f + 0.65f * hB) * easeOut3(Math.min(1f, tau * 1.6f))
        val z = lift * (0.6f + 0.4f * hC) * sinf(Math.min(1f, tau * 1.25f) * PI * 0.9f + 0.1f)
        val a = Math.min(1f, tau * 8f) * (1f - smooth(seg(tau, 0.6f, 1f)))
        piece(kind, gx(cosf(th), rho), gy(sinf(th), rho) - z * k, size * k * (0.75f + 0.5f * hB),
          th * 2.2f + tau * 9f, r, g, b, a)
      }
      i += 1
    }
  }

  /** A star flare twinkling somewhere on the blast for a moment in [t0, t1]. */
  private[blasts] def glints(n: Int, t0: Float, t1: Float, spread: Float, lift: Float, size: Float,
                     r: Float, g: Float, b: Float, a0: Float): Unit = {
    if (layer != AIR || T <= t0 || T >= t1) return
    var i = 0
    while (i < n) {
      val s0 = seed * 17 + i * 29
      val start = t0 + (t1 - t0) * 0.7f * h01(s0, 1)
      val p = seg(T, start, start + (t1 - t0) * 0.3f)
      if (p > 0f && p < 1f) {
        val th = h01(s0, 2) * TWO_PI
        val rho = spread * (0.2f + 0.8f * h01(s0, 3))
        val x = gx(cosf(th), rho); val y = gy(sinf(th), rho) - lift * k * h01(s0, 4)
        val a = a0 * (if (p < 0.3f) p / 0.3f else (1f - p) / 0.7f)
        sb.fillStarFlare(x, y, size * k * (0.6f + 0.4f * a), 2.2f, p * 1.5f, 0.5f, r, g, b, a)
      }
      i += 1
    }
  }

  /** A splat on the ground: a blob with a ragged edge (a fan, since its outline is a radius per
    * point), inked, with a gloss and drops round it. `size` of the footprint. */
  private[blasts] def splat(size: Float, grow: Float, r: Float, g: Float, b: Float, a: Float, sd: Int,
                    glossA: Float = 0.6f, drops: Int = 7): Unit = {
    if (layer != GROUND || a <= 0.01f || grow <= 0.01f) return
    val s0 = seed * 5 + sd * 313
    val n = 28
    // Lobes: the sum of a few waves round it, each with its own phase, and a little grain
    val p3 = hash(s0, 1) * PI; val p5 = hash(s0, 2) * PI; val p7 = hash(s0, 3) * PI
    var pass = 0
    while (pass < 2) {
      val extra = if (pass == 0) 1.8f else 0f
      var i = 0
      while (i < n) {
        val th = i * TWO_PI / n
        val wob = 0.14f * sinf(3f * th + p3) + 0.09f * sinf(5f * th + p5) + 0.05f * sinf(7f * th + p7)
        val rr = size * grow * (0.9f + wob + 0.03f * hash(s0, 10 + i))
        _xs(i) = cx + cosf(th) * (rr * W + extra)
        _ys(i) = cy + sinf(th) * (rr * H + extra * 0.6f)
        i += 1
      }
      if (pass == 0) sb.fillFan(cx, cy, _xs, _ys, n, ink(r), ink(g), ink(b), 0.85f * a)
      else sb.fillFan(cx, cy, _xs, _ys, n, r, g, b, a)
      pass += 1
    }
    // Drops flung round it
    var d = 0
    while (d < drops) {
      val th = (d + 0.5f * hash(s0, 40 + d)) * TWO_PI / drops
      val rho = size * grow * (1.1f + 0.45f * h01(s0, 50 + d))
      val x = gx(cosf(th), rho); val y = gy(sinf(th), rho)
      val ds = (2.2f + 2.6f * h01(s0, 60 + d)) * k * grow
      sb.fillOval(x, y, ds + 1.2f, ds * 0.6f + 1.2f, ink(r), ink(g), ink(b), 0.8f * a, 8)
      sb.fillOval(x, y, ds, ds * 0.6f, r, g, b, a, 8)
      d += 1
    }
    if (glossA > 0f) {
      sb.fillOval(cx - size * W * 0.25f, cy - size * H * 0.28f, size * W * 0.3f * grow, size * H * 0.16f * grow,
        lit(r), lit(g), lit(b), glossA * a, 12)
      sb.fillOval(cx + size * W * 0.2f, cy + size * H * 0.1f, size * W * 0.12f * grow, size * H * 0.07f * grow,
        lit(r), lit(g), lit(b), glossA * 0.7f * a, 8)
    }
  }

  /** Bubbles popping on a surface: each swells for a moment and bursts into a ring. */
  private[blasts] def bubbles(n: Int, t0: Float, t1: Float, spread: Float, size: Float,
                      r: Float, g: Float, b: Float, a0: Float, sd: Int): Unit = {
    if (layer != AIR || T <= t0 || T >= t1) return
    var i = 0
    while (i < n) {
      val s0 = seed * 19 + sd * 7 + i * 31
      val start = t0 + (t1 - t0) * 0.75f * h01(s0, 1)
      val p = seg(T, start, start + 0.18f)
      if (p > 0f && p < 1f) {
        val th = h01(s0, 2) * TWO_PI
        val rho = spread * Math.sqrt(h01(s0, 3)).toFloat
        val x = gx(cosf(th), rho); val y = gy(sinf(th), rho)
        val sz = size * k * (0.6f + 0.6f * h01(s0, 4))
        if (p < 0.7f) {
          val q = p / 0.7f
          val bs = sz * (0.4f + 0.6f * q)
          sb.strokeOval(x, y - bs * 0.6f, bs, bs * 0.9f, 1.3f, ink(r), ink(g), ink(b), 0.7f * a0, 10)
          sb.fillOval(x, y - bs * 0.6f, bs, bs * 0.9f, r, g, b, 0.55f * a0, 10)
          sb.fillOval(x - bs * 0.3f, y - bs * 0.95f, bs * 0.25f, bs * 0.2f, 1f, 1f, 1f, 0.8f * a0, 6)
        } else {
          val q = (p - 0.7f) / 0.3f
          sb.strokeOval(x, y - sz * 0.6f, sz * (1f + q * 0.8f), sz * (0.9f + q * 0.6f), 1.2f, lit(r), lit(g), lit(b), a0 * (1f - q), 10)
        }
      }
      i += 1
    }
  }

  /** A jet of liquid from (bx, by) to (tx, ty), `w` half-wide at its foot: four points, convex for
    * any lean a jet takes. */
  private def jet(bx: Float, by: Float, tx: Float, ty: Float, w: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    _xs(0) = bx - w; _ys(0) = by
    _xs(1) = tx; _ys(1) = ty
    _xs(2) = bx + w; _ys(2) = by
    _xs(3) = bx; _ys(3) = by + w * 0.32f
    sb.fillPolygon(_xs, _ys, 4, r, g, b, a)
  }

  // ── Pieces more than one engine draws ──

  /** A ring whose radius waves `waves` times round, `amp` of itself: a spectral shudder. */
  private[blasts] def wavyRing(q: Float, amp: Float, waves: Int, phase: Float, w: Float,
                       r: Float, g: Float, b: Float, a: Float): Unit = {
    if (layer != GROUND || a <= 0.01f || q <= 0.01f) return
    val n = 40
    var i = 0
    while (i < n) {
      val th = i * TWO_PI / n
      val rr = q * (1f + amp * sinf(waves * th + phase))
      _px(i) = gx(cosf(th), rr); _py(i) = gy(sinf(th), rr)
      i += 1
    }
    sb.strokePolygon(_px, _py, n, w + 2.4f, ink(r), ink(g), ink(b), 0.45f * a)
    sb.strokePolygon(_px, _py, n, w, r, g, b, a)
  }

  /**
   * Spikes standing up out of the ground on a ring at `rho` (± `jitter`): each rises over
   * [t0, t0 + 0.09], holds, and sinks back from `sink`. `tilt` leans them out from the middle.
   * Only this layer's, far to near.
   */
  private[blasts] def spikeRing(n0: Int, rho: Float, jitter: Float, h0: Float, t0: Float, sink: Float,
                        r: Float, g: Float, b: Float, glass: Boolean, sd: Int, tilt: Float = 0.12f): Unit = {
    val n = Math.min(32, n0)
    val s0 = seed * 37 + sd * 101
    var i = 0
    while (i < n) {
      val th = (i + 0.4f * hash(s0, i)) * TWO_PI / n
      val rr = rho + jitter * hash(s0, 20 + i)
      _ux(i) = th
      _us(i) = rr
      _ordY(i) = gy(sinf(th), rr)
      i += 1
    }
    sortFarToNear(n)
    var j = 0
    while (j < n) {
      val i2 = _ord(j)
      val y = _ordY(i2)
      if (mine(y)) {
        val th = _ux(i2)
        val start = t0 + 0.05f * h01(s0, 40 + i2)
        val up = easeOut3(seg(T, start, start + 0.09f))
        val down = smooth(seg(T, sink + 0.08f * h01(s0, 50 + i2), 1f))
        val hh = h0 * k * (0.7f + 0.5f * h01(s0, 60 + i2)) * up * (1f - down)
        val w = h0 * k * 0.28f * (0.8f + 0.35f * h01(s0, 70 + i2))
        val lean = (cosf(th) * tilt + 0.15f * hash(s0, 80 + i2)) * hh
        spike(gx(cosf(th), _us(i2)), y, w, hh, lean, 1f - down * 0.5f, r, g, b, glass)
      }
      j += 1
    }
  }

  /** A circle of glyphs on the ground: a ring at `rho`, and `n` marks inside it, glowing. */
  private[blasts] def runeCircle(rho: Float, n: Int, rot: Float, w: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    if (layer != GROUND || a <= 0.01f) return
    sb.strokeOval(cx, cy, W * rho, H * rho, w * k + 2.6f, ink(r), ink(g), ink(b), 0.5f * a, 24)
    sb.strokeOval(cx, cy, W * rho, H * rho, w * k, r, g, b, a, 24)
    sb.strokeOval(cx, cy, W * rho * 0.8f, H * rho * 0.8f, w * 0.5f * k, r, g, b, 0.7f * a, 24)
    var i = 0
    while (i < n) {
      val th = rot + i * TWO_PI / n
      val c = cosf(th); val s = sinf(th)
      // An angular mark: a stroke across the band with a hook, different on alternate marks
      val x0 = gx(c, rho * 0.84f); val y0 = gy(s, rho * 0.84f)
      val x1 = gx(c, rho * 0.96f); val y1 = gy(s, rho * 0.96f)
      val tx = -s * 3f * k; val ty = c * 1.5f * k
      _xs(0) = x0; _ys(0) = y0
      _xs(1) = x1; _ys(1) = y1
      _xs(2) = x1 + (if ((i & 1) == 0) tx else -tx); _ys(2) = y1 + (if ((i & 1) == 0) ty else -ty)
      sb.strokePolyline(_xs, _ys, 3, 1.4f * k + 1.6f, ink(r), ink(g), ink(b), 0.5f * a)
      sb.strokePolyline(_xs, _ys, 3, 1.4f * k, lit(r), lit(g), lit(b), a)
      i += 1
    }
  }

  /** A hexagon on the ground, `rho` of the footprint out, turned by `rot`. */
  private[blasts] def hexRing(rho: Float, rot: Float, w: Float, r: Float, g: Float, b: Float, a: Float,
                      ox: Float = 0f, oy: Float = 0f): Unit = {
    if (a <= 0.01f) return
    var i = 0
    while (i < 6) {
      val th = rot + i * TWO_PI / 6
      _xs(i) = ox + gx(cosf(th), rho); _ys(i) = oy + gy(sinf(th), rho)
      i += 1
    }
    sb.strokePolygon(_xs, _ys, 6, w + 2.4f, ink(r), ink(g), ink(b), 0.5f * a)
    sb.strokePolygon(_xs, _ys, 6, w, r, g, b, a)
  }

  /** Streaks racing out along the ground from `from` to `to` of the footprint over [t0, t1],
    * each a stroke that is thin and clear at its tail and bright at its head. */
  private[blasts] def streaks(n: Int, t0: Float, t1: Float, from: Float, to: Float, len: Float, w: Float,
                      r: Float, g: Float, b: Float, a0: Float, sd: Int): Unit = {
    if (layer != GROUND) return
    var i = 0
    while (i < n) {
      val s0 = seed * 47 + sd * 13
      val start = t0 + (t1 - t0) * 0.35f * h01(s0, i)
      val p = seg(T, start, t1)
      if (p > 0f && p < 1f) {
        val th = (i + 0.5f * hash(s0, 30 + i)) * TWO_PI / n
        val c = cosf(th); val s = sinf(th)
        val head = from + (to - from) * easeOut(p)
        val tl = Math.max(from, head - len)
        _xs(0) = gx(c, tl); _ys(0) = gy(s, tl)
        _xs(1) = gx(c, head); _ys(1) = gy(s, head)
        val a = a0 * (1f - p) * Math.min(1f, p * 6f)
        sb.strokePolylineTapered(_xs, _ys, 2, 0.6f, w * k + 2.2f, ink(r), ink(g), ink(b), 0f, 0.5f * a)
        sb.strokePolylineTapered(_xs, _ys, 2, 0.4f, w * k, r, g, b, 0f, a)
      }
      i += 1
    }
  }

  /** A pool of lava on the ground, bright when it lands and cooling to a dark crust, its cracks
    * still glowing. `hot` and `crust` are the two ends of its colour. */
  private[blasts] def lavaPool(size: Float, hr: Float, hg: Float, hb: Float, cr: Float, cg: Float, cb: Float, sd: Int): Unit = {
    if (layer != GROUND) return
    val cool = smooth(seg(T, 0.2f, 0.85f))
    val grow = easeOut3(seg(T, 0f, 0.14f))
    val a = tail(T, 0.72f)
    splat(size, grow, mix(hr, cr, cool), mix(hg, cg, cool), mix(hb, cb, cool), a, sd, glossA = 0f, drops = 6)
    // The heart of it stays hot longest
    val ha = (1f - cool) * a
    if (ha > 0.02f) sb.fillOval(cx, cy, W * size * 0.45f * grow, H * size * 0.4f * grow, 1f, 0.9f, 0.45f, 0.9f * ha, 16)
    cracks(5, size * 0.9f, 0.25f, 0.5f, 2.4f, cr * 0.5f, cg * 0.5f, cb * 0.5f, cool * 0.9f,
      1f, mix(0.55f, 0.35f, cool), 0.12f, cool * 0.95f, sd)
  }

  /** Flames standing on `n` points: a ring at `rho` (`jitter` of it either way), or scattered
    * over the footprint when `scatter`. Each rises over [t0, t0 + 0.1] (later further out when it
    * spreads), licks, and dies back from `die`. Only the ones this layer owns are drawn, far to near. */
  private[blasts] def flames(n0: Int, rho: Float, jitter: Float, scatter: Boolean, h0: Float, w0: Float, t0: Float, spreadT: Float,
                     die: Float, or: Float, og: Float, ob: Float, hr: Float, hg: Float, hb: Float, sd: Int,
                     tr: Float = -1f, tg: Float = 0f, tb: Float = 0f): Unit = {
    val n = Math.min(32, Math.max(1, (n0 * (0.6f + 0.4f * det) + 0.5f).toInt))
    val s0 = seed * 23 + sd * 61
    var i = 0
    while (i < n) {
      val th = (i + 0.5f * hash(s0, i)) * TWO_PI / n
      val rr = if (scatter) 0.12f + (rho - 0.12f) * Math.sqrt(h01(s0, 30 + i)).toFloat else rho + jitter * hash(s0, 60 + i)
      _ordY(i) = gy(sinf(th), rr)
      _ux(i) = gx(cosf(th), rr)
      _us(i) = rr
      i += 1
    }
    sortFarToNear(n)
    var j = 0
    while (j < n) {
      val i2 = _ord(j)
      val y = _ordY(i2)
      if (mine(y)) {
        val start = t0 + spreadT * _us(i2)
        val up = easeOut3(seg(T, start, start + 0.1f))
        val down = 1f - smooth(seg(T, die + 0.1f * h01(s0, 90 + i2), 1f))
        val lick = 0.82f + 0.18f * sinf(MS * 0.018f + i2 * 1.7f)
        val h = h0 * k * (0.7f + 0.5f * h01(s0, 120 + i2)) * up * down * lick
        val lean = sinf(MS * 0.011f + i2 * 2.3f) * 3f * k
        flameTongue(_ux(i2), y, w0 * k, h, lean, Math.min(1f, down * 1.4f), or, og, ob, hr, hg, hb, tr, tg, tb)
      }
      j += 1
    }
  }

  /**
   * A crown thrown up round a ring at `rho`: `n` jets flaring out and up to `h0` units, falling
   * back over [t0, t1], each with a drop leaving its tip. Water, or mud.
   */
  private[blasts] def crown(n0: Int, rho: Float, h0: Float, w0: Float, t0: Float, t1: Float,
                    r: Float, g: Float, b: Float, a0: Float, sd: Int): Unit = {
    val p = seg(T, t0, t1)
    if (p <= 0f || p >= 1f) return
    val n = Math.min(32, n0)
    val up = if (p < 0.35f) easeOut3(p / 0.35f) else 1f - smooth((p - 0.35f) / 0.65f)
    val a = a0 * tail(p, 0.6f)
    val s0 = seed + sd * 53
    var i = 0
    while (i < n) {
      val th = (i + 0.35f * hash(s0, i)) * TWO_PI / n
      _ux(i) = th
      _ordY(i) = gy(sinf(th), rho)
      i += 1
    }
    sortFarToNear(n)
    var j = 0
    while (j < n) {
      val i2 = _ord(j)
      val th = _ux(i2)
      val c = cosf(th); val s = sinf(th)
      val bx = gx(c, rho); val by = _ordY(i2)
      if (mine(by)) {
        val hh = h0 * k * (0.7f + 0.5f * h01(s0, 40 + i2)) * up
        if (hh > 2f) {
          val tx = bx + c * hh * 0.5f; val ty = by - hh + s * hh * 0.1f
          val w = w0 * k
          jet(bx, by + 0.8f, tx, ty - 1.8f, w + 1.4f, ink(r), ink(g), ink(b), 0.8f * a)
          jet(bx, by, tx, ty, w, r, g, b, a)
          sb.strokeLine(bx - w * 0.3f, by - 1f, tx - c * 0.5f, ty + 2f, 1.1f, lit(r), lit(g), lit(b), 0.85f * a)
          // A drop leaving the tip
          val dx = tx + c * hh * 0.2f * p; val dy = ty - 3f * k + p * p * 10f * k
          sb.fillOval(dx, dy, 2.4f * k + 1f, 2.8f * k + 1f, ink(r), ink(g), ink(b), 0.7f * a, 8)
          sb.fillOval(dx, dy, 2.4f * k, 2.8f * k, lit(r), lit(g), lit(b), a, 8)
        }
      }
      j += 1
    }
  }
}
