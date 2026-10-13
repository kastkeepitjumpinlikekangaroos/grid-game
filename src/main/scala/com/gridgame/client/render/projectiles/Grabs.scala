package com.gridgame.client.render.projectiles

import com.gridgame.common.model.Projectile
import com.gridgame.client.gl.ShapeBatch
import ProjectileKit._
import Silhouettes._

/** Grabs that aren't tethered: the Grasping Dead's hands clawing up out of the ground along its path
  * (deadHand is the blasts' too). The Mummy's bandage and the Chameleon's tongue pull, so they are
  * tethers now (Tethers). */
private[render] object Grabs {
  // The hands of the grasping dead, sorted far to near before they are drawn
  private val _ghX = new Array[Float](6)
  private val _ghY = new Array[Float](6)
  private val _ghR = new Array[Float](6)
  private val _ghA = new Array[Float](6)

  /**
   * Gravedigger's Grasping Dead: the dead clawing up out of the ground along the path — a hand
   * bursting out at the hitbox, reaching, and behind it the ones it has passed sinking back into
   * their graves. The hands stand on points fixed to the ground along the flight line, as the
   * lightning's kinks do, so each stays where it came up. It used to be the Kraken's tentacle in
   * bone white: a three-fingered glove with nothing to do with graves.
   */
  private[projectiles] def drawGraspingDead(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 29) * 0.35
    computeAllDynamics(proj, 0.8f, 0.76f, 0.64f, phase)
    val p = dynAlpha
    // A size up from the other grabs: a hand out of the ground is small beside a character
    val ds = Math.min(dynScale, 1.3f) * 1.3f
    val wl = Math.sqrt(proj.dx * proj.dx + proj.dy * proj.dy).toFloat
    val ux = if (wl > 1e-4f) proj.dx / wl else 1f
    val uy = if (wl > 1e-4f) proj.dy / wl else 0f
    val esx = (ux - uy) * 20f; val esy = (ux + uy) * 10f
    val spp = Math.max(1f, Math.sqrt(esx * esx + esy * esy).toFloat)
    val ndx = esx / spp; val ndy = esy / spp
    val nx = -ndy; val ny = ndx
    val along = proj.getX * ux + proj.getY * uy
    val gap = 15f * ds
    val stepW = gap / spp
    val grip = 0.5f + 0.5f * Math.sin(phase * 1.6).toFloat

    // The furrow it tears along the ground behind it
    fadeLine(sb, sx, sy + 1f, sx - ndx * gap * 4.4f, sy - ndy * gap * 4.4f + 1f, 7f * ds, 0.2f, 0.14f, 0.08f, 0.5f * p, 4)
    // A sickly grave-light round the one at the head
    sb.fillOvalSoft(sx, sy - 10f * ds, 20f * ds, 16f * ds, 0.45f, 0.95f, 0.5f, 0.22f * p, 0f, 12)

    // The hand at the head, fully up, and the four behind it sinking
    _ghX(0) = sx; _ghY(0) = sy; _ghR(0) = 1f; _ghA(0) = p
    var n = 1
    val k0 = Math.floor(along / stepW).toInt
    var j = 0
    while (j < 5) {
      val k = k0 - j
      val d = (along - k * stepW) * spp
      val age = d / (gap * 4.4f)
      if (d > gap * 0.55f && age < 1f) {
        val lat = hash2(proj.id * 17, k) * 5f * ds
        _ghX(n) = sx - ndx * d + nx * lat; _ghY(n) = sy - ndy * d + ny * lat
        _ghR(n) = 0.75f * (1f - age * 0.7f); _ghA(n) = p * (1f - age * 0.6f)
        n += 1
      }
      j += 1
    }
    // Far to near, so a nearer grave covers a further one
    var i = 1
    while (i < n) {
      var m = i
      while (m > 0 && _ghY(m - 1) > _ghY(m)) {
        var tmp = _ghX(m); _ghX(m) = _ghX(m - 1); _ghX(m - 1) = tmp
        tmp = _ghY(m); _ghY(m) = _ghY(m - 1); _ghY(m - 1) = tmp
        tmp = _ghR(m); _ghR(m) = _ghR(m - 1); _ghR(m - 1) = tmp
        tmp = _ghA(m); _ghA(m) = _ghA(m - 1); _ghA(m - 1) = tmp
        m -= 1
      }
      i += 1
    }
    i = 0
    while (i < n) {
      val head = _ghR(i) >= 1f
      deadHand(sb, _ghX(i), _ghY(i), _ghR(i), ndx * (if (head) 0.35f else 0.15f), if (head) grip else 0.3f,
        _ghA(i), ds * (if (head) 1.25f else 0.95f))
      i += 1
    }
    // Clods of earth thrown up off the one at the head
    var c = 0
    while (c < 4) {
      val t = ((tick * 0.06 + c * 0.25 + proj.id * 0.13) % 1.0).toFloat
      val side = if ((c & 1) == 0) 1f else -1f
      val cx = sx + nx * side * (5f + t * 10f) * ds - ndx * t * 6f * ds
      val cy = sy + ny * side * (5f + t * 10f) * ds - (Math.sin(t * Math.PI) * 14f * ds).toFloat
      val cs = (2.6f - t * 1.2f) * ds
      sb.fillOval(cx, cy, cs + 0.9f, cs * 0.8f + 0.9f, 0.1f, 0.07f, 0.04f, 0.8f * (1f - t) * p, 6)
      sb.fillOval(cx, cy, cs, cs * 0.8f, 0.42f, 0.3f, 0.17f, (1f - t) * p, 6)
      c += 1
    }
    drawChargeCrackle(sx, sy - 12f * ds, 16f * ds, 0.8f, 0.76f, 0.64f, p, sb, phase, proj.chargeLevel)
  }

  /** One of the dead: a mound of turned earth with a skeletal hand and forearm `rise` of the way
   *  up out of it, leaning `lean` (screen x per unit of height), fingers open by `open`. */
  private[render] def deadHand(sb: ShapeBatch, x: Float, y: Float, rise: Float, lean: Float, open: Float,
                       a: Float, s: Float): Unit = {
    if (a <= 0.01f) return
    sb.fillOval(x, y + 1f, 9f * s + 1.6f, 3.8f * s + 1.6f, 0.1f, 0.07f, 0.04f, 0.85f * a, 12)
    sb.fillOval(x, y + 1f, 9f * s, 3.8f * s, 0.36f, 0.26f, 0.15f, a, 12)
    sb.fillOval(x - 1.6f * s, y, 6f * s, 2.2f * s, 0.5f, 0.38f, 0.23f, a, 10)
    sb.fillOval(x, y + 0.6f * s, 4.6f * s, 1.7f * s, 0.07f, 0.05f, 0.03f, 0.9f * a, 10)
    if (rise < 0.06f) return
    if (rise < 0.8f) {
      // Sinking back: only the fingers still out of the ground, curled like a claw
      var f = 0
      while (f < 4) {
        val fx = x + (f - 1.5f) * 2.4f * s
        val up = (5f + 3f * (1 - Math.abs(f - 1.5f) / 1.5f)) * s * (0.4f + 0.6f * rise)
        val bend = (if (f < 2) 1f else -1f) * 2.6f * s * rise
        sb.strokeLine(fx, y, fx + bend * 0.3f, y - up, 2.6f * s, 0.12f, 0.1f, 0.08f, 0.9f * a)
        sb.strokeLine(fx + bend * 0.3f, y - up, fx + bend, y - up * 0.72f, 2.3f * s, 0.12f, 0.1f, 0.08f, 0.9f * a)
        sb.strokeLine(fx, y, fx + bend * 0.3f, y - up, 1.4f * s, 0.93f, 0.9f, 0.8f, a)
        sb.strokeLine(fx + bend * 0.3f, y - up, fx + bend, y - up * 0.72f, 1.2f * s, 0.93f, 0.9f, 0.8f, a)
        f += 1
      }
      return
    }
    val h = 16f * s * rise
    val wx = x + lean * h; val wy = y - h
    val br = 0.93f; val bg = 0.9f; val bb = 0.8f
    val ir = 0.12f; val ig = 0.1f; val ib = 0.08f
    sb.strokeLine(x, y, wx, wy, 4.8f * s, ir, ig, ib, 0.9f * a)
    sb.strokeLine(x, y, wx, wy, 3f * s, br, bg, bb, a)
    // Fingers: four spread up and out, each in two joints, the tips curling in as it closes
    var pass = 0
    while (pass < 2) {
      var f = 0
      while (f < 5) {
        val thumb = f == 4
        val base = if (thumb) -Math.PI / 2 - 1.05 else -Math.PI / 2 + (f - 1.5) * 0.38 * (0.55 + 0.45 * open)
        val seg1 = (if (thumb) 4f else 6f) * s * rise
        val seg2 = (if (thumb) 3f else 5f) * s * rise
        val curl = (0.35 + 0.8 * (1 - open)) * (if (f < 2 || thumb) 1.0 else -1.0)
        val kx = wx + Math.cos(base).toFloat * seg1; val ky = wy + Math.sin(base).toFloat * seg1
        val tx = kx + Math.cos(base + curl).toFloat * seg2; val ty = ky + Math.sin(base + curl).toFloat * seg2
        if (pass == 0) {
          sb.strokeLine(wx, wy, kx, ky, 2.9f * s, ir, ig, ib, 0.9f * a)
          sb.strokeLine(kx, ky, tx, ty, 2.5f * s, ir, ig, ib, 0.9f * a)
        } else {
          sb.strokeLine(wx, wy, kx, ky, 1.6f * s, br, bg, bb, a)
          sb.strokeLine(kx, ky, tx, ty, 1.3f * s, br, bg, bb, a)
        }
        f += 1
      }
      if (pass == 0) sb.fillOval(wx, wy, 3.2f * s + 1.3f, 2.6f * s + 1.3f, ir, ig, ib, 0.9f * a, 10)
      else sb.fillOval(wx, wy, 3.2f * s, 2.6f * s, br, bg, bb, a, 10)
      pass += 1
    }
  }
}
