package com.gridgame.client.render.projectiles

import GLProjectileRenderers.Renderer
import ProjectileKit._
import Silhouettes._

/** Ground blasts as they travel (aoeRing): filled shockwave bands over a darkened scorch, each kind
  * throwing off its own debris — rubble, water, spores, flame, pressure rings, motes, roots. */
private[render] object AoeRings {
  // ── Blast flavours for `aoeRing` ──
  final val AOE_QUAKE = 0
  final val AOE_WATER = 1
  final val AOE_TOXIC = 2
  final val AOE_FIRE = 3
  final val AOE_SONIC = 4
  final val AOE_VOID = 5
  final val AOE_NATURE = 6

  /**
   * Expanding ground blast.
   *
   * The rings used to be hairline strokes at low alpha, which on grass came out as a
   * barely-visible ripple — the same ripple for a tidal splash, a plague cloud, an
   * eruption and a gravity vortex. They are now filled bands over a darkened ground
   * scorch, and `kind` chooses what the blast throws off: rubble, water, spores, flame,
   * pressure rings, inward-falling motes, or roots.
   */
  private[projectiles] def aoeRing(kind: Int, r: Float, g: Float, b: Float, maxR: Float = 50f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 23) * 0.4
      computeAllDynamics(proj, r, g, b, phase)
      val p = (0.78f + 0.22f * Math.sin(phase * 2 * _stPulseMult).toFloat) * dynAlpha
      val dr = _evoR; val dg = _evoG; val db = _evoB
      val mR = maxR * 1.3f * dynScale
      val flat = 0.45f // ground ellipse ratio for this projection

      // Ground scorch: without something darker than the terrain underneath, a bright
      // ring on grass reads as a smudge. This is what gives the blast a floor.
      sb.fillOvalSoft(sx, sy, mR * 0.86f, mR * 0.86f * flat, dark(r) * 0.42f, dark(g) * 0.42f, dark(b) * 0.45f,
        (if (kind == AOE_QUAKE) 0.62f else 0.44f) * p, 0.02f, 20)
      val groundPulse = 0.75f + 0.25f * Math.sin(phase * 1.8).toFloat
      sb.fillOvalSoft(sx, sy, mR * 0.66f * dynGlow * groundPulse, mR * 0.66f * flat * dynGlow * groundPulse,
        dr, dg, db, 0.34f * p, 0f, 20)

      // Three filled shockwave bands, widest and brightest at the leading edge
      var ring = 0; while (ring < 3) {
        val rp = ((phase * 0.20 + ring * 0.333) % 1.0).toFloat
        val rr = 10f + rp * mR
        val a = Math.max(0f, 0.62f * (1f - rp * rp) * p)
        val bandIn = rr * (1f - 0.20f - 0.10f * rp)
        sb.fillArcBand(sx, sy, bandIn, bandIn * flat, rr, rr * flat, 0f, 6.2832f, 22,
          mix(r, bright(r), rp * 0.5f), mix(g, bright(g), rp * 0.5f), mix(b, bright(b), rp * 0.5f), a, a)
        sb.strokeOval(sx, sy, rr + 1.5f, (rr + 1.5f) * flat, 3.5f, 0.05f, 0.05f, 0.07f, a * 0.7f, 22)
        sb.strokeOval(sx, sy, rr, rr * flat, 2.2f, mix(bright(r), 1f, 0.4f), mix(bright(g), 1f, 0.4f),
          mix(bright(b), 1f, 0.4f), a * 1.1f, 22)
        ring += 1
      }

      kind match {
        case AOE_QUAKE =>
          // Forked ground cracks plus rubble thrown clear of the rim
          var c = 0; while (c < 9) {
            val ang = c * Math.PI * 2 / 9 + proj.id * 0.5
            val cl = mR * (0.36f + 0.16f * Math.sin(phase * 0.3 + c * 2.1).toFloat)
            val ex = sx + Math.cos(ang).toFloat * cl; val ey = sy + Math.sin(ang).toFloat * cl * flat
            sb.strokeLine(sx, sy, ex, ey, 3.2f, 0.06f, 0.05f, 0.05f, 0.55f * p)
            val fa = ang + 0.45
            sb.strokeLine(ex, ey, ex + Math.cos(fa).toFloat * cl * 0.35f,
              ey + Math.sin(fa).toFloat * cl * 0.35f * flat, 2f, 0.06f, 0.05f, 0.05f, 0.4f * p)
            c += 1
          }
          var d = 0; while (d < 9) {
            val t = ((phase * 0.28 + d * 0.111) % 1.0).toFloat
            val ang = phase * 0.4 + d * Math.PI * 2 / 9
            val dist = 12f + t * mR
            val dx2 = sx + Math.cos(ang).toFloat * dist
            val dy2 = sy + Math.sin(ang).toFloat * dist * flat - t * t * 26f
            val sz2 = 4f + (1f - t) * 5f
            _polyXs3(0) = dx2 - sz2; _polyYs3(0) = dy2 + sz2 * 0.5f
            _polyXs3(1) = dx2 + sz2 * 0.3f; _polyYs3(1) = dy2 - sz2 * 0.8f
            _polyXs3(2) = dx2 + sz2; _polyYs3(2) = dy2 + sz2 * 0.4f
            sb.fillPolygon(_polyXs3, _polyYs3, 3, dark(r), dark(g), dark(b), 0.75f * (1f - t) * p)
            d += 1
          }
        case AOE_WATER =>
          // Droplets thrown up and falling back, over a rippled surface
          var i = 1; while (i <= 4) {
            val rr = mR * (0.18f * i) + ((phase * 4) % 10).toFloat
            sb.strokeOval(sx, sy, rr, rr * flat, 1.4f, bright(r), bright(g), bright(b), 0.30f * p, 18)
            i += 1
          }
          var d = 0; while (d < 12) {
            val t = ((phase * 0.36 + d * 0.083) % 1.0).toFloat
            val ang = d * Math.PI * 2 / 12 + proj.id * 0.3
            val dist = 10f + t * mR * 0.85f
            val rise = Math.sin(t * Math.PI).toFloat * 30f
            sb.fillOval(sx + Math.cos(ang).toFloat * dist, sy + Math.sin(ang).toFloat * dist * flat - rise,
              3.4f * (1f - t * 0.4f), 4.6f * (1f - t * 0.4f), bright(r), bright(g), bright(b), 0.8f * (1f - t) * p, 7)
            d += 1
          }
        case AOE_TOXIC =>
          // Spore puffs boiling upward out of the cloud
          var d = 0; while (d < 10) {
            val t = ((phase * 0.22 + d * 0.1) % 1.0).toFloat
            val ang = d * 2.39f + proj.id * 0.4f
            val dist = mR * (0.2f + 0.55f * t)
            val bx = sx + Math.cos(ang).toFloat * dist
            val by = sy + Math.sin(ang).toFloat * dist * flat - t * 26f
            val bs = 7f + t * 12f
            sb.fillOvalSoft(bx, by, bs, bs * 0.8f, dr, dg, db, 0.38f * (1f - t) * p, 0f, 10)
            sb.strokeOval(bx, by, bs * 0.6f, bs * 0.48f, 1.4f, bright(r), bright(g), bright(b), 0.28f * (1f - t) * p, 8)
            d += 1
          }
        case AOE_FIRE =>
          // Flame tongues standing up around the rim, embers drifting off
          var f = 0; while (f < 10) {
            val ang = f * Math.PI * 2 / 10 + phase * 0.2
            val lick = 0.5f + 0.5f * Math.sin(phase * 3.6 + f * 1.7).toFloat
            val rr = mR * 0.52f
            val bx = sx + Math.cos(ang).toFloat * rr; val by = sy + Math.sin(ang).toFloat * rr * flat
            val fh = (14f + lick * 26f) * (0.65f + 0.55f * ((f * 5) % 7) / 6f)
            val lean = Math.sin(phase * 2.1 + f).toFloat * 7f
            _polyXs3(0) = bx - 7f; _polyYs3(0) = by + 3f
            _polyXs3(1) = bx + lean; _polyYs3(1) = by - fh
            _polyXs3(2) = bx + 7f; _polyYs3(2) = by + 3f
            sb.fillPolygon(_polyXs3, _polyYs3, 3, 1f, 0.42f + lick * 0.3f, 0.08f, 0.75f * p)
            _polyXs3(0) = bx - 3.4f; _polyYs3(0) = by + 2f
            _polyXs3(1) = bx + lean * 0.6f; _polyYs3(1) = by - fh * 0.62f
            _polyXs3(2) = bx + 3.4f; _polyYs3(2) = by + 2f
            sb.fillPolygon(_polyXs3, _polyYs3, 3, 1f, 0.92f, 0.55f, 0.8f * p)
            f += 1
          }
          var e = 0; while (e < 8) {
            val t = ((phase * 0.3 + e * 0.125) % 1.0).toFloat
            val ang = e * 0.9f + proj.id * 0.3f
            sb.fillOval(sx + Math.cos(ang).toFloat * mR * 0.4f * (0.5f + t),
              sy + Math.sin(ang).toFloat * mR * 0.4f * flat - t * 42f,
              3.5f * (1f - t), 3.5f * (1f - t), 1f, 0.72f, 0.28f, 0.8f * (1f - t) * p, 6)
            e += 1
          }
        case AOE_SONIC =>
          // A dense stack of pressure rings — the blast is the air itself
          var i = 0; while (i < 7) {
            val rp = ((phase * 0.30 + i * 0.143) % 1.0).toFloat
            val rr = 6f + rp * mR
            sb.strokeOval(sx, sy, rr, rr * flat, 2.6f * (1f - rp * 0.6f),
              bright(r), bright(g), bright(b), 0.45f * (1f - rp) * p, 20)
            i += 1
          }
        case AOE_VOID =>
          // Everything falls inward, and the middle is a hole rather than a light
          var d = 0; while (d < 14) {
            val t = ((phase * 0.4 + d * 0.0714) % 1.0).toFloat
            val ti = 1f - t
            val ang = d * 0.9f + phase * 0.8 + ti * 3.2
            val dist = mR * 0.9f * ti
            val px2 = sx + Math.cos(ang).toFloat * dist
            val py2 = sy + Math.sin(ang).toFloat * dist * flat
            sb.strokeLineSoft(px2, py2, sx + Math.cos(ang + 0.3).toFloat * dist * 0.86f,
              sy + Math.sin(ang + 0.3).toFloat * dist * 0.86f * flat, 2.6f,
              bright(r), bright(g), bright(b), 0.55f * t * p)
            d += 1
          }
          sb.fillOval(sx, sy, mR * 0.20f, mR * 0.20f * flat, 0.02f, 0.01f, 0.04f, 0.9f * p, 16)
          sb.strokeOval(sx, sy, mR * 0.21f, mR * 0.21f * flat, 2.5f, bright(r), bright(g), bright(b), 0.8f * p, 16)
        case _ =>
          // AOE_NATURE — barbed roots shoving up out of the ground
          var v = 0; while (v < 9) {
            val ang = v * Math.PI * 2 / 9 + proj.id * 0.4
            val grow = 0.55f + 0.45f * Math.sin(phase * 0.8 + v * 1.3).toFloat
            val rr = mR * 0.62f * grow
            val ca2 = Math.cos(ang).toFloat; val sa2 = Math.sin(ang).toFloat
            var seg = 0; while (seg < 3) {
              val t0 = seg / 3f; val t1 = (seg + 1) / 3f
              val wob0 = Math.sin(t0 * 5.0 + v).toFloat * 6f
              val wob1 = Math.sin(t1 * 5.0 + v).toFloat * 6f
              val x0 = sx + ca2 * rr * t0 - sa2 * wob0; val y0 = sy + sa2 * rr * t0 * flat + ca2 * wob0 * flat - t0 * 9f
              val x1 = sx + ca2 * rr * t1 - sa2 * wob1; val y1 = sy + sa2 * rr * t1 * flat + ca2 * wob1 * flat - t1 * 9f
              sb.strokeLine(x0, y0, x1, y1, 6f * (1f - t0 * 0.5f), 0.10f, 0.09f, 0.05f, 0.7f * p)
              sb.strokeLine(x0, y0, x1, y1, 3.8f * (1f - t0 * 0.5f), dr, dg, db, 0.9f * p)
              if (seg == 1) {
                sb.strokeLine(x1, y1, x1 - sa2 * 9f, y1 + ca2 * 9f * flat - 4f, 2.4f, dark(r), dark(g), dark(b), 0.7f * p)
              }
              seg += 1
            }
            v += 1
          }
      }

      // Core: bright and outlined so the epicentre is never ambiguous
      sb.strokeOval(sx, sy, 17f, 17f * flat, 3.5f, 0.05f, 0.05f, 0.07f, 0.85f * p, 14)
      sb.fillOval(sx, sy, 16f, 16f * flat, dr, dg, db, 0.92f * p, 14)
      sb.fillOval(sx, sy, 10f, 10f * flat, mix(dr, bright(r), 0.5f), mix(dg, bright(g), 0.5f),
        mix(db, bright(b), 0.5f), 0.85f * p, 12)
      if (kind != AOE_VOID) sb.fillOval(sx, sy, 4.5f, 4.5f * flat, 1f, 1f, 1f, 0.9f * p, 8)

      var star = 0; while (star < 6) {
        val starPhase = ((phase * 0.35 + star * 0.167) % 1.0).toFloat
        val starAngle = phase * 0.6 + star * Math.PI / 3
        val starDist = 12f + starPhase * mR
        drawSparkleStar(sx + Math.cos(starAngle).toFloat * starDist,
          sy + Math.sin(starAngle).toFloat * starDist * flat, 6f * (1f - starPhase * 0.5f),
          bright(r), bright(g), bright(b), 0.6f * (1f - starPhase) * p, sb, phase + star)
        star += 1
      }
    }
}
