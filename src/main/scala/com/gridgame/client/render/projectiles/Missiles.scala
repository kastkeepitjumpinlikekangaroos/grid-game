package com.gridgame.client.render.projectiles

import GLProjectileRenderers.Renderer
import ProjectileKit._
import Silhouettes._

/** The gauntleted punch (fistProj). Rounds are Arsenal's. */
private[render] object Missiles {

  // A fist end on to its punch: the forearm, the hand wraps round the wrist, the fist itself (its
  // lit top, then its shaded underside), the thumb folded across the front of it
  private val FIST_PARTS = Array(
    part(Array(-1.25f,-0.26f, -0.4f,-0.36f, -0.4f,0.36f, -1.25f,0.26f), 0.80f, 0.60f, 0.46f, 0.25f),
    part(Array(-0.66f,-0.44f, -0.3f,-0.48f, -0.3f,0.48f, -0.66f,0.44f), 0.93f, 0.90f, 0.82f, 0f),
    part(Array(-0.32f,-0.5f, 0.3f,-0.62f, 0.72f,-0.5f, 0.9f,-0.2f, 0.9f,0.24f, 0.7f,0.52f, 0.28f,0.6f, -0.32f,0.5f),
      0.90f, 0.70f, 0.54f, 0.25f),
    part(Array(-0.3f,0.14f, 0.9f,0.14f, 0.9f,0.24f, 0.7f,0.52f, 0.28f,0.6f, -0.3f,0.5f), 0.72f, 0.5f, 0.38f, 0.25f),
    part(Array(0.02f,0.2f, 0.62f,0.16f, 0.74f,0.34f, 0.6f,0.5f, 0.06f,0.48f), 0.94f, 0.76f, 0.6f, 0.2f)
  )

  /** Thrown/charged punch. The old version was three concentric rings over a plain
   *  ellipse, so a monk's strike and a gorilla's grab arrived as the same grey coin. */
  private[projectiles] def fistProj(r: Float, g: Float, b: Float, size: Float = 14f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 23) * 0.4
      computeAllDynamics(proj, r, g, b, phase)
      val p = (0.8f + 0.2f * Math.sin(phase * _stPulseMult).toFloat) * dynAlpha
      val dr = _evoR; val dg = _evoG; val db = _evoB
      val ds = Math.min(dynScale, 1.35f)
      val sz = size * 0.92f * ds
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy
      val frontX = sx + ndx * sz * 0.9f; val frontY = sy + ndy * sz * 0.9f

      drawSpeedLines(sx, sy, ndx, ndy, dr, dg, db, 0.42f * p, sb, 6, sz * 2.4f)
      drawRibbonTrail(sx, sy, ndx, ndy, dr, dg, db, 0.3f * p, sb, tick, proj.id, 6,
        sz * 2.6f * dynTrail, sz * 0.55f, 1.5f)
      sb.fillOval(sx, sy + sz * 0.62f, sz * 1.3f, sz * 0.3f, 0f, 0f, 0f, 0.22f * p, 14)

      // Impact shock stacking up in front of the knuckles
      val shock = ((phase * 0.4) % 1.0).toFloat
      if (shock < 0.6f) {
        val sr = sz * (0.7f + shock * 2.2f)
        sb.strokeOval(frontX, frontY, sr, sr * ISO_Y, 5f * (1f - shock / 0.6f),
          bright(r), bright(g), bright(b), 0.42f * (1f - shock / 0.6f) * p, 16)
      }
      sb.fillOvalSoft(frontX, frontY, sz * 1.5f * dynGlow, sz * 1.2f * dynGlow, dr, dg, db, 0.3f * p, 0f, 14)

      // Two afterimages so the punch reads as travelling, not hovering
      var ghost = 2; while (ghost >= 1) {
        drawPartsDirFlat(sb, FIST_PARTS, sx - ndx * ghost * 10f, sy - ndy * ghost * 10f, ndx, ndy,
          sz * (1f - ghost * 0.06f), dr * 0.8f, dg * 0.8f, db * 0.8f, 0.15f * (1f - (ghost - 1) * 0.35f) * p)
        ghost -= 1
      }

      drawPartsDir(sb, FIST_PARTS, sx, sy, ndx, ndy, sz, dr, dg, db, 0.97f * dynAlpha,
        clampF(sz * 0.11f, 1.5f, 2.6f))
      // The wraps' turns
      var w = 0; while (w < 2) {
        dirPoint(-0.56f + w * 0.16f, -0.44f, sx, sy, ndx, ndy, sz); val wx = _ptX; val wy = _ptY
        dirPoint(-0.5f + w * 0.16f, 0.44f, sx, sy, ndx, ndy, sz)
        sb.strokeLine(wx, wy, _ptX, _ptY, 1.2f, 0.5f, 0.42f, 0.36f, 0.8f * p)
        w += 1
      }

      // Four knuckles across the leading face
      var k = 0; while (k < 4) {
        val ly = -0.42f + k * 0.2f
        dirPoint(0.78f, ly, sx, sy, ndx, ndy, sz)
        sb.fillOval(_ptX, _ptY, sz * 0.14f, sz * 0.14f, 0.06f, 0.05f, 0.07f, 0.8f * p, 8)
        sb.fillOval(_ptX - sz * 0.02f, _ptY - sz * 0.02f, sz * 0.1f, sz * 0.1f,
          mix(0.97f, dr, 0.2f), mix(0.8f, dg, 0.2f), mix(0.64f, db, 0.2f), p, 8)
        k += 1
      }

      drawSparkBurst(frontX, frontY, dr, dg, db, 0.45f * p, sb, tick, proj.id, 5, sz * 0.8f)
      drawChargeCrackle(sx, sy, sz, r, g, b, p, sb, phase, proj.chargeLevel)
      drawReturnGhosts(sx, sy, sz * 0.7f, dr, dg, db, p, sb, proj)
    }
}
