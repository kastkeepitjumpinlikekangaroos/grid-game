package com.gridgame.client.render.projectiles

import com.gridgame.common.model.Projectile
import com.gridgame.client.gl.ShapeBatch
import GLProjectileRenderers.Renderer
import ProjectileKit._
import Silhouettes._

/** Electricity fixed to the ground (lightningBolt): a channel whose kinks are keyed to points along
  * the flight line, so it stays where it was drawn as the head flies on, forks flashing off it; and
  * Thunder Strike's storm cloud striking its target spot. */
private[render] object Electricity {
  final val BOLT_MAX = 16
  private val _bnX = new Array[Float](BOLT_MAX + 1)
  private val _bnY = new Array[Float](BOLT_MAX + 1)
  private val _bnT = new Array[Float](BOLT_MAX + 1)
  private val _brX = new Array[Float](5)
  private val _brY = new Array[Float](5)

  /** The kink at an even lattice node: on alternate sides of the flight line from one to the next,
    * by an uneven amount — the lightning-bolt zigzag, never the same twice, and at most ~55° off
    * the line, so no bend is sharp enough for its mitre to fold into a needle. */
  @inline private def kink(seed: Int, k: Int): Float = {
    val side = if ((Math.floorDiv(k, 2) & 1) == 0) 1f else -1f
    side * (5f + 6f * Math.abs(hash2(seed, k)))
  }

  private[projectiles] def lightningBolt(r: Float, g: Float, b: Float, heavy: Boolean = false): Renderer =
    (proj, sx, sy, sb, tick) => drawLightning(proj, sx, sy, sb, tick, r, g, b, heavy)

  /** A channel of `n` points, 0 at the head, stroked as glow, ink, colour and white core, each
   *  one mitred band narrowing and fading toward where the bolt has been (`ts` from 0 to 1). */
  private def strokeChannel(sb: ShapeBatch, xs: Array[Float], ys: Array[Float], ts: Array[Float], n: Int,
                            w: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    if (n < 2) return
    var layer = 0
    while (layer < 4) {
      var i = 0
      while (i < n) {
        val t = ts(i); val k = 1f - t
        (layer: @scala.annotation.switch) match {
          case 0 => _pvW(i) = w * 2.8f * (1f - 0.5f * t); _pvA(i) = 0.16f * a * k
          case 1 => _pvW(i) = w * (1f - 0.5f * t * t) + 4f; _pvA(i) = 0.85f * a * Math.min(1f, k * 1.5f)
          case 2 => _pvW(i) = w * (1f - 0.5f * t * t); _pvA(i) = a * Math.min(1f, k * 1.8f)
          case _ => _pvW(i) = w * 0.3f * (1f - 0.6f * t); _pvA(i) = a * Math.min(1f, k * 1.5f)
        }
        i += 1
      }
      (layer: @scala.annotation.switch) match {
        case 0 => sb.strokePolylineVar(xs, ys, _pvW, _pvA, n, r, g, b)
        case 1 => sb.strokePolylineVar(xs, ys, _pvW, _pvA, n, r * 0.16f, g * 0.16f, b * 0.22f + 0.05f)
        case 2 => sb.strokePolylineVar(xs, ys, _pvW, _pvA, n, r, g, b)
        case _ => sb.strokePolylineVar(xs, ys, _pvW, _pvA, n, mix(r, 1f, 0.8f), mix(g, 1f, 0.8f), mix(b, 1f, 0.8f))
      }
      layer += 1
    }
  }

  /**
   * Lightning: a crackling ball of light at the hitbox, and the channel it has just drawn left
   * standing behind it (see Electricity above), with a fork flashing off it now and then and
   * sparks spat out of it. `heavy` (chain lightning, the Tesla's fork) is thicker, reaches
   * further back, forks twice and crackles more round the head.
   */
  private def drawLightning(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int,
                            lr: Float, lg: Float, lb: Float, heavy: Boolean): Unit = {
    val phase = (tick + proj.id * 41) * 0.5
    computeAllDynamics(proj, lr, lg, lb, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.35f)
    val hR = mix(lr, 1f, 0.55f); val hG = mix(lg, 1f, 0.55f); val hB = mix(lb, 1f, 0.55f)
    // Its flight line in world units, and how that lies on screen
    val wl = Math.sqrt(proj.dx * proj.dx + proj.dy * proj.dy).toFloat
    val ux = if (wl > 1e-4f) proj.dx / wl else 1f
    val uy = if (wl > 1e-4f) proj.dy / wl else 0f
    val esx = (ux - uy) * 20f; val esy = (ux + uy) * 10f
    val spp = Math.max(1f, Math.sqrt(esx * esx + esy * esy).toFloat) // screen units per world unit
    val ndx = esx / spp; val ndy = esy / spp
    val nx = -ndy; val ny = ndx
    // How far along its line it is: a coordinate fixed to the ground, which the kinks are keyed on
    val along = proj.getX * ux + proj.getY * uy
    val gap = 8f * ds
    val stepW = gap / spp
    val trail = (if (heavy) 84f else 70f) * ds
    val epoch = (tick + proj.id * 7) / 3
    val seed = proj.id * 131
    val strike = 0.9f + 0.1f * hash2(seed + 3, epoch)

    _bnX(0) = sx; _bnY(0) = sy; _bnT(0) = 0f
    var n = 1
    var k = Math.floor(along / stepW).toInt
    var more = true
    while (more && n < BOLT_MAX) {
      val d = (along - k * stepW) * spp
      if (d > trail) more = false
      else {
        if (d > gap * 0.4f) {
          val jag =
            if ((k & 1) == 0) kink(seed, k) + hash2(seed + 31 * epoch, k) * 1.5f
            else (kink(seed, k - 1) + kink(seed, k + 1)) * 0.5f + hash2(seed + 31 * epoch, k) * 2.8f
          // The kinks die away into the head, which is the hitbox
          val off = jag * ds * Math.min(1f, d / (gap * 2.2f))
          _bnX(n) = sx - ndx * d + nx * off; _bnY(n) = sy - ndy * d + ny * off
          _bnT(n) = d / trail
          n += 1
        }
        k -= 1
      }
    }
    val w = (if (heavy) 8.5f else 7f) * ds * strike
    strokeChannel(sb, _bnX, _bnY, _bnT, n, w, lr, lg, lb, a)

    // Forks: off a node partway back, forward and out the way the bolt was going, and never
    // reaching past the head. Not on every re-strike, so they flash in and out.
    var f = 0
    while (f < (if (heavy) 2 else 1)) {
      val hf = hash2(seed + 97, epoch * 3 + f)
      if (n > 5 && hf > -0.3f) {
        val at = 2 + (((hash2(seed + 13, epoch * 5 + f) + 1f) * 0.5f) * (n - 4)).toInt
        val side = if (hash2(seed + 29, epoch * 7 + f) > 0f) 1f else -1f
        val ang = 0.55f + 0.35f * (hash2(seed + 41, epoch * 11 + f) + 1f) * 0.5f
        val ca = Math.cos(ang).toFloat; val sa = Math.sin(ang).toFloat * side
        val fx = ndx * ca - ndy * sa; val fy = ndx * sa + ndy * ca
        val dAt = _bnT(at) * trail
        val flen = Math.min((16f + 12f * (hf + 1f) * 0.5f) * ds, (dAt - 4f) / ca)
        if (flen > 6f) {
          val qx = -fy; val qy = fx
          var i = 0
          while (i < 5) {
            val t = i / 4f
            val off = if (i == 0) 0f else hash2(seed + 53 + epoch, f * 7 + i) * 3.2f * ds
            _brX(i) = _bnX(at) + fx * flen * t + qx * off
            _brY(i) = _bnY(at) + fy * flen * t + qy * off
            i += 1
          }
          val fa = a * (1f - _bnT(at))
          val fw = w * 0.5f * (1f - _bnT(at) * 0.5f)
          sb.strokePolylineTapered(_brX, _brY, 5, fw + 3f, 1.8f, lr * 0.16f, lg * 0.16f, lb * 0.22f + 0.05f, 0.75f * fa, 0.1f * fa)
          sb.strokePolylineTapered(_brX, _brY, 5, fw, 0.5f, lr, lg, lb, 0.95f * fa, 0.2f * fa)
          sb.strokePolylineTapered(_brX, _brY, 5, fw * 0.4f, 0.3f, hR, hG, hB, fa, 0.2f * fa)
        }
      }
      f += 1
    }

    // Sparks spat sideways out of the channel, falling as they fade
    var s = 0
    while (s < 3 && n > 3) {
      val life = ((tick + proj.id * 3 + s * 3) % 9) / 9f
      val born = (tick + proj.id * 3 + s * 3) / 9
      val at = 1 + (((hash2(seed + 71, born * 3 + s) + 1f) * 0.5f) * (n - 2)).toInt
      val side = if (hash2(seed + 73, born + s) > 0f) 1f else -1f
      val ox = nx * side * 0.85f - ndx * 0.5f; val oy = ny * side * 0.85f - ndy * 0.5f
      val dist = (3f + life * 14f) * ds
      val px2 = _bnX(Math.min(at, n - 1)) + ox * dist
      val py2 = _bnY(Math.min(at, n - 1)) + oy * dist + life * life * 9f
      fadeLine(sb, px2, py2, px2 - ox * 5f * ds, py2 - oy * 5f * ds, 2.2f * ds, hR, hG, hB, 0.9f * (1f - life) * a, 2)
      s += 1
    }

    // The head: a ball of light with arcs crackling off it, re-struck with the channel. The arcs
    // run all round it and stop short — the three prongs this used to throw out ahead of the
    // head read as the legs of a bug.
    val hr = (if (heavy) 8f else 6.5f) * ds
    sb.fillOvalSoft(sx, sy, hr * 2.8f * dynGlow, hr * 2.4f * dynGlow, lr, lg, lb, 0.4f * a, 0f, 14)
    val arcs = if (heavy) 4 else 3
    var j = 0
    while (j < arcs) {
      val ang = (j * (2 * Math.PI / arcs) + hash2(seed + 61, epoch * 13 + j) * 0.9).toFloat
      val al = hr * (1.6f + 0.6f * hash2(seed + 67, epoch * 17 + j))
      val ca = Math.cos(ang).toFloat; val sa = Math.sin(ang).toFloat
      sparkArc(sb, sx + ca * hr * 0.5f, sy + sa * hr * 0.45f, sx + ca * al, sy + sa * al * 0.8f, 3, al * 0.22f,
        seed + epoch * 19 + j * 3, 2.8f * ds, lr, lg, lb, a)
      j += 1
    }
    sb.fillOvalSoft(sx, sy, hr * 1.25f, hr * 1.1f, lr, lg, lb, a, 0.2f * a, 14)
    sb.fillOvalSoft(sx, sy, hr * 0.7f, hr * 0.62f, 1f, 1f, 1f, a, 0.6f * a, 12)
    sb.fillStarFlare(sx, sy, hr * 3.2f * strike, 3f, (epoch * 0.7f) % 6.2832f, 0.5f, 1f, 1f, mix(lb, 1f, 0.6f), 0.9f * a)
    drawChargeCrackle(sx, sy, hr * 2f, lr, lg, lb, a, sb, phase, proj.chargeLevel)
  }

  // A thunder strike: its bolt and the puffs of its cloud
  private val _tsX = new Array[Float](17)
  private val _tsY = new Array[Float](17)
  private val CLOUD_PUFFS = Array(
    -18f, 3f, 9f,   -9f, -4f, 12f,   3f, -7f, 13f,   15f, -2f, 10f,   21f, 4f, 7.5f,   0f, 4f, 11f,   -10f, 5f, 9f)

  /**
   * Thunder Strike: a storm cloud rolling over the ground with lightning striking down out of it
   * onto the spot it is over — which is the projectile, so the strike lands on the hitbox. It
   * re-strikes fifteen times a second, every fifth strike a full flash, each laying a ring on the
   * ground. The old one was the same fixed zigzag on every frame, with its splash drawn 36px
   * below the hitbox.
   */
  private[projectiles] def drawThunderStrike(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 31) * 0.5
    computeAllDynamics(proj, 1f, 0.9f, 0.35f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    val bc = _chgBright
    val lr = 1f; val lg = 0.9f; val lb = 0.38f
    val clock = tick + proj.id * 5
    val epoch = clock / 4
    val within = (clock % 4) / 4f
    val big = epoch % 5 == 0
    val flash = if (big) 1f else 0.62f + 0.2f * hash2(proj.id, epoch)
    val cx = sx + Math.sin(phase * 0.21).toFloat * 2f * ds
    val cy = sy - 84f * ds + Math.sin(phase * 0.33).toFloat * 2.5f * ds

    // On the ground: the cloud's shadow, the strike's light, and a ring from the last strike
    sb.fillOvalSoft(sx, sy + 2f, 34f * ds, 12f * ds, 0.05f, 0.05f, 0.08f, 0.32f * a, 0f, 18)
    sb.fillOvalSoft(sx, sy, 30f * ds * flash, 11f * ds * flash, lr, lg, lb, 0.5f * flash * a, 0f, 18)
    val rr = (6f + within * 22f) * ds
    sb.strokeOval(sx, sy, rr, rr * 0.38f, 3f * (1f - within) * ds, lr, lg, lb, 0.85f * (1f - within) * a, 18)

    // The bolt, from the cloud's belly down to the ground point: the lightning-bolt zigzag, a
    // kink to alternate sides at every other point by an uneven amount, and the points between
    // knocked aside a little afresh with every strike. Midpoint displacement, tried first, came
    // out as a thin wavering thread — not what a storm's strike is drawn as.
    val n = 9
    val topX = cx + hash2(proj.id + 1, epoch) * 5f * ds; val topY = cy + 12f * ds
    var i = 0
    while (i < n) {
      val t = i.toFloat / (n - 1)
      val kinkX =
        if (i == 0 || i == n - 1) 0f
        else if ((i & 1) == 1) (if (((i >> 1) & 1) == 0) 1f else -1f) * (6f + 5f * Math.abs(hash2(proj.id * 7 + epoch * 131, i)))
        else hash2(proj.id * 11 + epoch * 137, i) * 3f
      _tsX(i) = topX + (sx - topX) * t + kinkX * ds
      _tsY(i) = topY + (sy - topY) * t + hash2(proj.id * 13 + epoch * 139, i) * 2f * ds
      i += 1
    }
    val w = (if (big) 14f else 11f) * ds
    sb.strokePolylineTapered(_tsX, _tsY, n, w * 2.2f, w * 3f, lr, lg, lb, 0.16f * flash * a, 0.24f * flash * a)
    sb.strokePolylineTapered(_tsX, _tsY, n, w * 0.75f + 4f, w + 4f, 0.16f, 0.14f, 0.1f, 0.88f * a, 0.9f * a)
    sb.strokePolylineTapered(_tsX, _tsY, n, w * 0.75f, w, lr, lg, lb, a, a)
    sb.strokePolylineTapered(_tsX, _tsY, n, w * 0.3f, w * 0.38f, 1f, 1f, mix(0.85f, 1f, bc), a, a)
    // Forks down and away from it
    var f = 0
    while (f < (if (big) 2 else 1)) {
      val at = 3 + ((hash2(proj.id + 17, epoch * 3 + f) + 1f) * 1.5f).toInt
      val side = if (hash2(proj.id + 19, epoch * 5 + f) > 0f) 1f else -1f
      val flen = (18f + 8f * hash2(proj.id + 23, epoch * 7 + f)) * ds
      val fx = side * 0.72f; val fy = 0.69f
      var i = 0
      while (i < 5) {
        val t = i / 4f
        val off = if (i == 0) 0f else hash2(proj.id + 29 + epoch, f * 7 + i) * 3.4f * ds
        _brX(i) = _tsX(at) + fx * flen * t - fy * off
        _brY(i) = _tsY(at) + fy * flen * t + fx * off
        i += 1
      }
      sb.strokePolylineTapered(_brX, _brY, 5, w * 0.45f + 3f, 1.8f, 0.16f, 0.14f, 0.1f, 0.8f * a, 0.1f * a)
      sb.strokePolylineTapered(_brX, _brY, 5, w * 0.45f, 0.5f, lr, lg, lb, 0.95f * a, 0.2f * a)
      sb.strokePolylineTapered(_brX, _brY, 5, w * 0.18f, 0.3f, 1f, 1f, 0.9f, a, 0.2f * a)
      f += 1
    }
    // Where it lands: a white-hot flash, and sparks thrown up off it
    sb.fillOvalSoft(sx, sy, 10f * ds * flash, 5.5f * ds * flash, 1f, 1f, 0.9f, a, 0.3f * a, 12)
    sb.fillStarFlare(sx, sy - 2f, 20f * ds * flash, 2.8f, 0f, 0.45f, 1f, 1f, 0.9f, 0.85f * a)
    var k = 0
    while (k < 5) {
      val ang = -Math.PI * (0.12 + 0.76 * ((hash2(proj.id + 31, epoch * 7 + k) + 1f) * 0.5f))
      val ca = Math.cos(ang).toFloat; val sa = Math.sin(ang).toFloat
      val dist = (4f + within * 16f) * ds
      val px2 = sx + ca * dist; val py2 = sy + sa * dist * 0.7f + within * within * 10f * ds
      fadeLine(sb, px2, py2, px2 - ca * 5f * ds, py2 - sa * 3.5f * ds, 2f * ds, 1f, 0.95f, 0.6f,
        0.9f * (1f - within) * a, 2)
      k += 1
    }

    // The cloud, over the top of its own bolt: seven puffs inked as one, slate grey, lit on top
    // and lit from underneath by the strike
    var pass = 0
    while (pass < 3) {
      var i = 0
      while (i < CLOUD_PUFFS.length) {
        val pr = CLOUD_PUFFS(i + 2) * ds * 1.2f * (1f + 0.04f * Math.sin(phase * 0.9 + i).toFloat)
        val px2 = cx + CLOUD_PUFFS(i) * ds * 1.2f; val py2 = cy + CLOUD_PUFFS(i + 1) * ds * 1.2f
        (pass: @scala.annotation.switch) match {
          case 0 => sb.fillOval(px2, py2, pr + 2f, pr * 0.82f + 2f, 0.07f, 0.07f, 0.1f, 0.92f * a, 14)
          case 1 => sb.fillOval(px2, py2, pr, pr * 0.82f, 0.33f, 0.35f, 0.44f, 0.98f * a, 14)
          case _ => sb.fillOval(px2 - pr * 0.18f, py2 - pr * 0.26f, pr * 0.62f, pr * 0.5f, 0.5f, 0.52f, 0.61f, 0.9f * a, 12)
        }
        i += 3
      }
      pass += 1
    }
    sb.fillOvalSoft(cx, cy + 7f * ds, 24f * ds, 7f * ds, lr, lg, lb, 0.55f * flash * a, 0f, 14)
    if (hash2(proj.id + 37, epoch) > 0.2f)
      sparkArc(sb, cx - 12f * ds, cy + hash2(proj.id + 41, epoch) * 3f * ds, cx + 10f * ds, cy + 2f * ds, 4, 3f * ds,
        proj.id * 3 + epoch, 1.8f * ds, lr, lg, lb, 0.7f * a)
  }
}
