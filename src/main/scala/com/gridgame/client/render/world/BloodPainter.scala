package com.gridgame.client.render
package world

import com.gridgame.client.game.HitLog

/**
 * The blood a hit draws (render/CLAUDE.md, *A hit draws blood*). Every hit the client hears
 * ([[collect]], from its HitLog) spurts from where its target is drawn, Blood working out each drop;
 * the drops in the air are drawn over the frame with the other effects ([[drawAir]]), and the splats
 * they leave on the ground with the ground ([[drawGround]]), under the traps, the players' shadows
 * and anything standing in front of them.
 *
 * Blood is drawn opaque, deep red and inked, never added as light: the hit's own flash is a glow in
 * the colour of what struck, which on sand and snow added next to nothing, and in a firefight was one
 * glow among the shots' and the blasts'.
 */
private[render] final class BloodPainter(ctx: RenderContext) {
  import ctx._
  import Blood._
  import BloodPainter._

  val blood = new Blood
  // How many of the client's hits have been taken
  private var seen = 0L
  private val _xs = new Array[Float](SplatPoints)
  private val _ys = new Array[Float](SplatPoints)
  private val _tail = new Array[Float](2)
  private val _tailY = new Array[Float](2)

  /** Every hit heard since the last frame, spurted from where its target is drawn now. */
  def collect(): Unit = {
    val log = client.hitLog
    val heard = log.heard
    var n = Math.max(seen, log.oldest)
    while (n < heard) {
      take(log, n)
      n += 1
    }
    seen = heard
  }

  private def take(log: HitLog, n: Long): Unit = {
    val time = log.time(n)
    // Long over: heard before this renderer drew anything, or while it drew no world (the game over)
    if (frameTimeMs - time >= LifeMs) return
    val pType = log.projectileType(n)
    if (dropsFor(pType) == 0) return
    val target = log.target(n)
    if (target == null) return
    var wx = 0.0; var wy = 0.0; var lift = 0.0
    if (target == client.getLocalPlayerId) {
      wx = camera.visualX; wy = camera.visualY
      if (wx.isNaN || wy.isNaN) { val pos = client.getLocalPosition; wx = pos.getX; wy = pos.getY }
      if (client.isAirborne)
        lift = PlayerPainter.airLiftAt(frameTimeMs, client.getAirborneFrom, client.getAirborneUntil)
    } else {
      val p = client.getPlayers.get(target)
      if (p == null) return
      if (entityCollector.getRemoteVisualPos(target)) { wx = entityCollector.lastRVX; wy = entityCollector.lastRVY }
      else { val pos = p.getPosition; wx = pos.getX; wy = pos.getY }
      if (p.isAirborne) lift = PlayerPainter.airLiftAt(frameTimeMs, p.getAirborneFrom, p.getAirborneUntil)
    }
    // Nobody could see it before it is gone
    val sx = worldToScreenX(wx, wy); val sy = worldToScreenY(wx, wy)
    if (sx < -canvasW || sx > 2 * canvasW || sy < -canvasH || sy > 2 * canvasH) return
    blood.spurt(time, wx, wy, lift, log.x(n), log.y(n), log.dx(n), log.dy(n), pType, seedOf(target, time, n),
      client.getWorld)
  }

  /** Is spurt `s` near enough the screen for anything of it to show? */
  private def near(s: Int): Boolean = {
    val bx = blood.bodyX(s).toDouble; val by = blood.bodyY(s).toDouble
    val sx = worldToScreenX(bx, by); val sy = worldToScreenY(bx, by)
    sx > -Margin && sx < canvasW + Margin && sy > -Margin && sy < canvasH + Margin
  }

  /** The splats, oldest first, so a new one lies over an old. */
  def drawGround(): Unit = {
    val newest = blood.newestHit
    if (newest == NoHit || frameTimeMs - newest >= LifeMs) return
    beginShapes()
    var k = 0
    while (k < MaxSpurts) {
      val s = blood.oldestFirst(k)
      val hit = blood.hitTime(s)
      if (hit != NoHit) {
        val age = frameTimeMs - hit
        if (age >= 0 && age < LifeMs && near(s)) {
          val t = age / 1000f
          val fade = if (age < FadeFromMs) 1f else 1f - (age - FadeFromMs).toFloat / (LifeMs - FadeFromMs)
          val n = blood.drops(s)
          var d = 0
          while (d < n) {
            val i = blood.drop(s, d)
            val landed = t - blood.landsAfter(i)
            if (landed >= 0f && blood.splats(i)) drawSplat(i, landed, fade)
            d += 1
          }
        }
      }
      k += 1
    }
  }

  /** Drop `i`'s splat, `since` seconds after it landed: spreading as it lands, wet and darkening as it
    * dries, faded by `fade`. */
  private def drawSplat(i: Int, since: Float, fade: Float): Unit = {
    val lx = blood.landX(i).toDouble; val ly = blood.landY(i).toDouble
    val cx = worldToScreenX(lx, ly).toFloat; val cy = worldToScreenY(lx, ly).toFloat
    if (cx < -20f || cx > canvasW + 20f || cy < -20f || cy > canvasH + 20f) return
    val grow = if (since < SplashS) 0.55f + 0.45f * since / SplashS else 1f
    val dry = Math.min(1f, since / DryS)
    val r = WetR + (DryR - WetR) * dry
    val g = WetG + (DryG - WetG) * dry
    val b = WetB + (DryB - WetB) * dry
    val a = SplatA * fade
    var k = 0
    while (k < SplatPoints) {
      _xs(k) = cx + blood.outlineX(i, k) * grow
      _ys(k) = cy + blood.outlineY(i, k) * grow
      k += 1
    }
    shapeBatch.fillFan(cx, cy, _xs, _ys, SplatPoints, r, g, b, a)
    val fr = blood.fleckR(i)
    if (fr > 0f)
      shapeBatch.fillOval(cx + blood.fleckX(i) * grow, cy + blood.fleckY(i) * grow, fr, fr * 0.6f, r, g, b, a, 8)
    // Its sheen while it is wet, a little up toward the light
    if (since < SheenS) {
      val sa = SheenA * (1f - since / SheenS) * fade
      val hx = cx - 0.5f; val hy = cy - 0.4f
      k = 0
      while (k < SplatPoints) {
        _xs(k) = hx + blood.outlineX(i, k) * grow * 0.45f
        _ys(k) = hy + blood.outlineY(i, k) * grow * 0.45f
        k += 1
      }
      shapeBatch.fillFan(hx, hy, _xs, _ys, SplatPoints, SheenR, SheenG, SheenB, sa)
    }
  }

  /** The spurts still in the air: every drop not yet landed. */
  def drawAir(): Unit = {
    val newest = blood.newestHit
    if (newest == NoHit || frameTimeMs - newest >= AirMs) return
    beginShapes()
    var k = 0
    while (k < MaxSpurts) {
      val s = blood.oldestFirst(k)
      val hit = blood.hitTime(s)
      if (hit != NoHit) {
        val age = frameTimeMs - hit
        if (age >= 0 && age < AirMs && near(s)) drawSpurt(s, age)
      }
      k += 1
    }
  }

  private def drawSpurt(s: Int, age: Long): Unit = {
    val t = age / 1000f
    val n = blood.drops(s)
    var d = 0
    while (d < n) {
      val i = blood.drop(s, d)
      if (t < blood.landsAfter(i)) drawDrop(i, t)
      d += 1
    }
  }

  /** Drop `i` in the air `t` seconds after its hit: a teardrop of red, inked, its tail drawn out behind
    * it the faster it goes (and up from it as it hangs at the top of its arc). */
  private def drawDrop(i: Int, t: Float): Unit = {
    blood.flight(i, t)
    val hx = worldToScreenX(blood.x, blood.y).toFloat
    val hy = worldToScreenY(blood.x, blood.y).toFloat - blood.z
    blood.velocity(i, t)
    val speed = Math.sqrt(blood.vsx * blood.vsx + blood.vsy * blood.vsy).toFloat
    val r = blood.radius(i)
    val len = Math.max(r * 1.3f, Math.min(r * 7f, speed * StretchS))
    val bx = if (speed > 1f) -blood.vsx / speed else 0f
    val by = if (speed > 1f) -blood.vsy / speed else -1f
    _tail(0) = hx + bx * len; _tailY(0) = hy + by * len
    _tail(1) = hx; _tailY(1) = hy
    shapeBatch.strokePolylineTapered(_tail, _tailY, 2, 0.6f, (r + InkW) * 2f, InkR, InkG, InkB, InkA, InkA)
    shapeBatch.fillOval(hx, hy, r + InkW, r + InkW, InkR, InkG, InkB, InkA, 8)
    _tail(0) = hx + bx * (len - InkW); _tailY(0) = hy + by * (len - InkW)
    shapeBatch.strokePolylineTapered(_tail, _tailY, 2, 0.2f, r * 2f, BodyR, BodyG, BodyB, 1f, 1f)
    shapeBatch.fillOval(hx, hy, r, r, BodyR, BodyG, BodyB, 1f, 8)
  }

  /** A new life, or a new match: nothing drawn from before it carries on. */
  def reset(): Unit = blood.clear()
}

private[render] object BloodPainter {
  /** The longest a drop is in the air: thrown up hardest from a figure knocked up highest (BloodTest). */
  val AirMs = 900L
  /** How far off the screen a spurt's figure may be with some of it still showing, in units. */
  private val Margin = 80.0

  // A drop: a teardrop of red with an ink rim, drawn out behind it by how far it goes in StretchS
  private val InkR = 0.22f; private val InkG = 0.01f; private val InkB = 0.02f; private val InkA = 0.9f
  private val InkW = 0.45f
  private val BodyR = 0.78f; private val BodyG = 0.05f; private val BodyB = 0.07f
  private val StretchS = 0.03f
  // A splat: spreading for SplashS as it lands, darkening as it dries over DryS, a sheen on it while wet
  private val WetR = 0.6f; private val WetG = 0.03f; private val WetB = 0.05f
  private val DryR = 0.4f; private val DryG = 0.02f; private val DryB = 0.03f
  private val SplatA = 0.9f
  private val SplashS = 0.06f
  private val DryS = 1.6f
  private val SheenR = 0.9f; private val SheenG = 0.22f; private val SheenB = 0.2f; private val SheenA = 0.45f
  private val SheenS = 0.9f
}
