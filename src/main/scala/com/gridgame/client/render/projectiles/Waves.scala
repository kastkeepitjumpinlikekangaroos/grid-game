package com.gridgame.client.render.projectiles

import GLProjectileRenderers.Renderer
import ProjectileKit._
import Silhouettes._

/** Crescent sweeps (wave): a real arc band kept square to the travel direction at every heading —
  * wind, sand, sound, flame, acid, impact, water — inked all round so it reads on pale ground. */
private[render] object Waves {
  // ── Sweep kinds for `wave` ──
  final val WAV_WIND = 0
  final val WAV_SAND = 1
  final val WAV_SONIC = 2
  final val WAV_FLAME = 3
  final val WAV_ACID = 4
  final val WAV_IMPACT = 5
  final val WAV_WATER = 6

  /**
   * Crescent sweep travelling forward — a gust, a sand blast, a sonic wave, a wall of
   * flame, an acid spray, a shoulder charge.
   *
   * This used to be a four-vertex polygon (tip, both wingtips, tail), which renders as a
   * triangle: a bard's song, a windwalker's cyclone and an inferno's flame wall all came
   * out as the same outlined triangle in three colours. It is now a real arc band, built
   * so the arc passes exactly through the projectile's position and bulges forward,
   * which is what a wave front actually looks like from above.
   */
  private[projectiles] def wave(kind: Int, r: Float, g: Float, b: Float, spread: Float = 32f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 41) * 0.4
      computeAllDynamics(proj, r, g, b, phase)
      // A shallow pulse: at 0.72 the whole front thinned by a quarter every beat
      val p = (0.86f + 0.14f * Math.sin(phase * _stPulseMult).toFloat) * dynAlpha
      val dr = _evoR; val dg = _evoG; val db = _evoB
      val ds = dynScale
      val R = spread * 1.25f * ds
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy

      // Arc centre placed so the band's leading edge runs through (sx, sy). Solving in the
      // ellipse's own parameter space keeps the crescent square to the travel direction at
      // every heading — using atan2(ndy, ndx) directly skews it badly on the diagonals.
      val th = Math.atan2(ndy / ISO_Y, ndx).toFloat
      val cxA = sx - R * Math.cos(th).toFloat
      val cyA = sy - R * ISO_Y * Math.sin(th).toFloat
      val sweep = kind match {
        case WAV_WATER => 1.75f
        case WAV_SONIC => 1.9f
        case WAV_IMPACT => 1.1f
        case WAV_FLAME => 1.7f
        case _ => 1.55f
      }
      val start = th - sweep * 0.5f
      val thickness = kind match {
        case WAV_WATER => 0.48f
        case WAV_IMPACT => 0.42f
        case WAV_SONIC => 0.22f
        case _ => 0.32f
      }

      // Trailing echoes of the front, fading back
      var e = 3; while (e >= 1) {
        val eR = R * (1f - e * 0.16f)
        val eA = 0.20f * (1f - (e - 1) * 0.3f) * p
        sb.fillArcBand(cxA, cyA, eR * (1f - thickness), eR * (1f - thickness) * ISO_Y, eR, eR * ISO_Y,
          start + e * 0.05f, sweep - e * 0.1f, 12, r, g, b, eA, eA)
        e -= 1
      }

      // Soft glow hugging the front. fillArcBand ramps alpha ALONG the sweep, so the
      // radial falloff has to come from nesting bands rather than from its ramp — using
      // the ramp for it leaves one horn of the crescent bright and the other invisible.
      sb.fillArcBand(cxA, cyA, R * 0.90f, R * 0.90f * ISO_Y, R * 1.34f * dynGlow, R * 1.34f * ISO_Y * dynGlow,
        start - 0.06f, sweep + 0.12f, 14, dr, dg, db, 0.20f * p, 0.20f * p)

      // Body of the wave front: three nested bands, densest at the leading edge. Denser than it
      // was: at 42/34/40% a pale front (wind, sand, water) vanished on sand and snow, and even a
      // saturated one (acid) into grass of its own colour (render_audit)
      sb.fillArcBand(cxA, cyA, R * (1f - thickness), R * (1f - thickness) * ISO_Y, R, R * ISO_Y,
        start, sweep, 16, dr, dg, db, 0.58f * p, 0.58f * p)
      sb.fillArcBand(cxA, cyA, R * (1f - thickness * 0.6f), R * (1f - thickness * 0.6f) * ISO_Y, R, R * ISO_Y,
        start, sweep, 16, dr, dg, db, 0.42f * p, 0.42f * p)
      sb.fillArcBand(cxA, cyA, R * (1f - thickness * 0.25f), R * (1f - thickness * 0.25f) * ISO_Y, R, R * ISO_Y,
        start, sweep, 16, mix(dr, 1f, 0.25f), mix(dg, 1f, 0.25f), mix(db, 1f, 0.25f), 0.46f * p, 0.46f * p)
      // Ink round the whole crescent: the trailing edge and both horns as well as the front, so
      // the shape holds on ground as pale as it is. Only the leading edge used to be inked, and
      // the rest of the band faded into the ground behind it
      val rIn = R * (1f - thickness)
      sb.strokeArc(cxA, cyA, rIn, rIn * ISO_Y, start, sweep, 2.2f, 0.06f, 0.05f, 0.07f, 0.5f * p, 16)
      val c0 = Math.cos(start).toFloat; val s0 = Math.sin(start).toFloat
      val c1 = Math.cos(start + sweep).toFloat; val s1 = Math.sin(start + sweep).toFloat
      sb.strokeLine(cxA + c0 * rIn, cyA + s0 * rIn * ISO_Y, cxA + c0 * R * 1.02f, cyA + s0 * R * 1.02f * ISO_Y,
        2.2f, 0.06f, 0.05f, 0.07f, 0.5f * p)
      sb.strokeLine(cxA + c1 * rIn, cyA + s1 * rIn * ISO_Y, cxA + c1 * R * 1.02f, cyA + s1 * R * 1.02f * ISO_Y,
        2.2f, 0.06f, 0.05f, 0.07f, 0.5f * p)
      // Dark contour behind the leading edge, then the bright edge itself
      sb.strokeArc(cxA, cyA, R * 1.015f, R * 1.015f * ISO_Y, start, sweep,
        if (kind == WAV_WIND || kind == WAV_WATER) 6f else 4.5f,
        0.06f, 0.05f, 0.07f, if (kind == WAV_WIND || kind == WAV_WATER) 0.8f * p else 0.7f * p, 16)
      sb.strokeArc(cxA, cyA, R, R * ISO_Y, start, sweep, 2.4f,
        mix(bright(r), 1f, _chgBright), mix(bright(g), 1f, _chgBright), mix(bright(b), 1f, _chgBright),
        0.95f * p, 16)

      kind match {
        case WAV_WIND =>
          // Streaks curling along the front — the wind's direction made visible
          var i = 0; while (i < 5) {
            val t = ((phase * 0.18 + i * 0.2) % 1.0).toFloat
            val a0 = start + sweep * (0.12f + 0.76f * ((i + t * 0.5f) % 1f))
            val rr = R * (0.62f + 0.3f * Math.sin(phase * 1.4 + i).toFloat)
            sb.strokeArc(cxA, cyA, rr, rr * ISO_Y, a0, 0.42f, 1.8f,
              bright(r), bright(g), bright(b), 0.4f * p, 6)
            i += 1
          }
        case WAV_SAND =>
          // Grains scoured off the front
          var i = 0; while (i < 14) {
            val t = ((phase * 0.25 + i * 0.0714) % 1.0).toFloat
            val a0 = start + sweep * ((i * 0.137f + t * 0.3f) % 1f)
            val rr = R * (0.72f + t * 0.42f)
            val gx = cxA + Math.cos(a0).toFloat * rr
            val gy = cyA + Math.sin(a0).toFloat * rr * ISO_Y
            sb.fillOval(gx, gy, 2.6f + t * 2f, 2f + t * 1.6f, dark(r), dark(g), dark(b), 0.5f * (1f - t) * p, 5)
            i += 1
          }
        case WAV_SONIC =>
          // Concentric ripples behind the front
          var i = 1; while (i <= 3) {
            val rr = R * (1f - i * 0.17f) - ((phase * 3) % 8).toFloat
            if (rr > 6f) sb.strokeArc(cxA, cyA, rr, rr * ISO_Y, start + 0.08f, sweep - 0.16f, 1.6f,
              bright(r), bright(g), bright(b), 0.34f * (1f - i * 0.22f) * p, 14)
            i += 1
          }
        case WAV_FLAME =>
          // Tongues licking forward off the crest, plus embers riding behind it
          var i = 0; while (i < 7) {
            val a0 = start + sweep * (0.08f + 0.84f * i / 6f)
            val lick = (0.5f + 0.5f * Math.sin(phase * 3.4 + i * 1.9).toFloat)
            val ca2 = Math.cos(a0).toFloat; val sa2 = Math.sin(a0).toFloat
            val bx = cxA + ca2 * R * 0.98f; val by = cyA + sa2 * R * ISO_Y * 0.98f
            val tx = cxA + ca2 * (R + R * 0.34f * lick); val ty = cyA + sa2 * (R + R * 0.34f * lick) * ISO_Y
            sb.strokeLineSoft(bx, by, tx, ty, 5f + lick * 4f, 1f, mix(0.45f, 0.9f, lick), 0.15f, 0.55f * p)
            sb.strokeLineSoft(bx, by, (bx + tx) * 0.5f, (by + ty) * 0.5f, 3f, 1f, 0.95f, 0.6f, 0.5f * p)
            i += 1
          }
          var k = 0; while (k < 6) {
            val t = ((phase * 0.3 + k * 0.167) % 1.0).toFloat
            val a0 = start + sweep * ((k * 0.19f) % 1f)
            val rr = R * (0.9f - t * 0.5f)
            sb.fillOval(cxA + Math.cos(a0).toFloat * rr, cyA + Math.sin(a0).toFloat * rr * ISO_Y - t * 10f,
              3.5f * (1f - t), 3.5f * (1f - t), 1f, 0.7f, 0.25f, 0.7f * (1f - t) * p, 6)
            k += 1
          }
        case WAV_WATER =>
          // Foam caps riding the crest and spray thrown off the top of it
          var i = 0; while (i < 9) {
            val a0 = start + sweep * (0.06f + 0.88f * i / 8f)
            val ca2 = Math.cos(a0).toFloat; val sa2 = Math.sin(a0).toFloat
            val bob = 0.5f + 0.5f * Math.sin(phase * 2.6 + i * 1.4).toFloat
            val fx = cxA + ca2 * R * 0.97f; val fy = cyA + sa2 * R * ISO_Y * 0.97f
            sb.fillOval(fx, fy - bob * 5f, 7f + bob * 4f, 5f + bob * 3f, 1f, 1f, 1f, 0.55f * p, 8)
            sb.fillOval(fx, fy - bob * 9f, 3.4f, 2.6f, 1f, 1f, 1f, 0.45f * p, 6)
            i += 1
          }
          var k = 0; while (k < 7) {
            val t = ((phase * 0.4 + k * 0.143) % 1.0).toFloat
            val a0 = start + sweep * ((k * 0.19f) % 1f)
            val rr = R * 0.95f
            sb.fillOval(cxA + Math.cos(a0).toFloat * rr, cyA + Math.sin(a0).toFloat * rr * ISO_Y
              - Math.sin(t * Math.PI).toFloat * 22f, 3.2f * (1f - t * 0.4f), 4f * (1f - t * 0.4f),
              bright(r), bright(g), bright(b), 0.8f * (1f - t) * p, 6)
            k += 1
          }
        case WAV_ACID =>
          // Droplets sagging off the underside of the spray
          var i = 0; while (i < 8) {
            val t = ((phase * 0.35 + i * 0.125) % 1.0).toFloat
            val a0 = start + sweep * (0.1f + 0.8f * i / 7f)
            val rr = R * 0.92f
            val gx = cxA + Math.cos(a0).toFloat * rr
            val gy = cyA + Math.sin(a0).toFloat * rr * ISO_Y + t * 16f
            sb.fillOval(gx, gy, 3.4f * (1f - t * 0.4f), 4.6f * (1f - t * 0.4f),
              bright(r), bright(g), bright(b), 0.65f * (1f - t) * p, 7)
            i += 1
          }
        case _ =>
          // WAV_IMPACT — a hard double crest with speed streaks raking back
          sb.strokeArc(cxA, cyA, R * 0.80f, R * 0.80f * ISO_Y, start + 0.14f, sweep - 0.28f, 3f,
            bright(r), bright(g), bright(b), 0.55f * p, 12)
          var i = 0; while (i < 5) {
            val a0 = start + sweep * (0.12f + 0.76f * i / 4f)
            val ca2 = Math.cos(a0).toFloat; val sa2 = Math.sin(a0).toFloat
            sb.strokeLineSoft(cxA + ca2 * R * 0.5f, cyA + sa2 * R * 0.5f * ISO_Y,
              cxA + ca2 * R * 0.94f, cyA + sa2 * R * 0.94f * ISO_Y, 3f,
              bright(r), bright(g), bright(b), 0.4f * p)
            i += 1
          }
      }

      // Sparks riding the crest
      var s2 = 0; while (s2 < 5) {
        val t = ((phase * 0.5 + s2 * 0.2) % 1.0).toFloat
        val a0 = start + sweep * ((s2 * 0.23f + t * 0.4f) % 1f)
        drawSparkleStar(cxA + Math.cos(a0).toFloat * R, cyA + Math.sin(a0).toFloat * R * ISO_Y,
          5f * (1f - t * 0.5f) * ds, bright(r), bright(g), bright(b), 0.55f * (1f - t) * p, sb, phase + s2)
        s2 += 1
      }

      drawChargeCrackle(sx, sy, R * 0.4f, r, g, b, p, sb, phase, proj.chargeLevel)
      drawReturnGhosts(sx, sy, R * 0.35f, dr, dg, db, p, sb, proj)
    }
}
