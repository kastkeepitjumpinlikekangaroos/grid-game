package com.gridgame.client.render.projectiles

import GLProjectileRenderers.Renderer
import ProjectileKit._
import Silhouettes._

/** Shafts flying point-first (flyingShaft): spears, arrows, darts, thorns, ice spikes, the void
  * lance. */
private[render] object Shafts {
  final val SHF_SPEAR = 0
  final val SHF_ARROW = 1
  final val SHF_DART = 2
  final val SHF_THORN = 3
  final val SHF_PARROW = 4
  final val SHF_ICE = 5
  final val SHF_LANCE = 6
  private val SPEAR_PARTS = Array(
    part(Array(-1.10f,-0.11f, 0.42f,-0.11f, 0.42f,0.11f, -1.10f,0.11f), WOOD_R, WOOD_G, WOOD_B, 0.15f),
    part(Array(-1.30f,-0.15f, -1.04f,-0.15f, -1.04f,0.15f, -1.30f,0.15f), GOLD_R, GOLD_G, GOLD_B, 0.35f),
    part(Array(0.16f,-0.09f, 0.27f,-0.09f, 0.27f,-0.46f, 0.16f,-0.46f), GOLD_R, GOLD_G, GOLD_B, 0.35f),
    part(Array(0.16f,0.09f, 0.27f,0.09f, 0.27f,0.46f, 0.16f,0.46f), GOLD_R, GOLD_G, GOLD_B, 0.35f),
    part(Array(0.30f,-0.17f, 0.52f,-0.17f, 0.52f,0.17f, 0.30f,0.17f), GOLD_R, GOLD_G, GOLD_B, 0.35f),
    part(Array(0.48f,-0.34f, 0.78f,-0.30f, 1.38f,0f, 0.78f,0.30f, 0.48f,0.34f), STEEL_R, STEEL_G, STEEL_B, 0.28f),
    part(Array(0.56f,-0.13f, 0.86f,-0.10f, 1.20f,0f, 0.86f,0.06f, 0.56f,0.09f), 1f, 1f, 1f, 0.10f)
  )

  private val ARROW_PARTS = Array(
    part(Array(-0.85f,-0.10f, 0.55f,-0.10f, 0.55f,0.10f, -0.85f,0.10f), WOOD_R, WOOD_G, WOOD_B, 0.18f),
    part(Array(-1.06f,-0.10f, -0.86f,-0.10f, -0.86f,0.10f, -1.06f,0.10f), DKWOOD_R, DKWOOD_G, DKWOOD_B, 0.10f),
    part(Array(-0.90f,-0.07f, -0.42f,-0.11f, -0.32f,-0.48f, -0.96f,-0.38f), 0.82f, 0.30f, 0.26f, 0.45f),
    part(Array(-0.90f,0.07f, -0.42f,0.11f, -0.32f,0.48f, -0.96f,0.38f), 0.92f, 0.92f, 0.90f, 0.35f),
    part(Array(0.50f,-0.30f, 0.72f,-0.24f, 1.28f,0f, 0.72f,0.24f, 0.50f,0.30f), STEEL_R, STEEL_G, STEEL_B, 0.30f)
  )

  private val DART_PARTS = Array(
    part(Array(-0.70f,-0.09f, 0.55f,-0.07f, 0.55f,0.07f, -0.70f,0.09f), 0.42f, 0.34f, 0.24f, 0.18f),
    part(Array(-0.74f,-0.06f, -0.46f,-0.09f, -0.38f,-0.44f, -0.84f,-0.36f), 0.92f, 0.72f, 0.24f, 0.40f),
    part(Array(-0.74f,0.06f, -0.46f,0.09f, -0.38f,0.44f, -0.84f,0.36f), 0.92f, 0.72f, 0.24f, 0.40f),
    part(Array(0.46f,-0.20f, 1.32f,0f, 0.46f,0.20f), 0.55f, 0.92f, 0.42f, 0.70f)
  )

  private val THORN_PARTS = Array(
    part(Array(-1.00f,-0.17f, 0.45f,-0.13f, 1.32f,0f, 0.45f,0.13f, -1.00f,0.17f), 0.36f, 0.28f, 0.14f, 0.25f),
    part(Array(-0.34f,-0.12f, -0.06f,-0.56f, 0.12f,-0.11f), 0.30f, 0.24f, 0.12f, 0.25f),
    part(Array(0.06f,0.11f, 0.34f,0.54f, 0.48f,0.10f), 0.30f, 0.24f, 0.12f, 0.25f),
    part(Array(-0.74f,0.13f, -0.52f,0.48f, -0.38f,0.12f), 0.30f, 0.24f, 0.12f, 0.25f),
    part(Array(-0.98f,-0.12f, -0.62f,-0.44f, -1.12f,-0.56f, -1.30f,-0.18f), 0.30f, 0.62f, 0.22f, 0.55f),
    part(Array(0.52f,-0.07f, 1.16f,0f, 0.52f,0.05f), 0.62f, 0.86f, 0.40f, 0.45f)
  )

  private val PARROW_PARTS = Array(
    part(Array(-0.85f,-0.10f, 0.55f,-0.10f, 0.55f,0.10f, -0.85f,0.10f), 0.40f, 0.34f, 0.20f, 0.15f),
    part(Array(-1.06f,-0.10f, -0.86f,-0.10f, -0.86f,0.10f, -1.06f,0.10f), DKWOOD_R, DKWOOD_G, DKWOOD_B, 0.10f),
    part(Array(-0.90f,-0.07f, -0.42f,-0.11f, -0.32f,-0.48f, -0.96f,-0.38f), 0.30f, 0.48f, 0.22f, 0.45f),
    part(Array(-0.90f,0.07f, -0.42f,0.11f, -0.32f,0.48f, -0.96f,0.38f), 0.52f, 0.62f, 0.34f, 0.35f),
    part(Array(0.50f,-0.30f, 0.72f,-0.24f, 1.28f,0f, 0.72f,0.24f, 0.50f,0.30f), 0.45f, 0.88f, 0.35f, 0.65f)
  )

  private val ICE_PARTS = Array(
    part(Array(-0.86f,-0.30f, 0.10f,-0.36f, 1.26f,0f, 0.10f,0.36f, -0.86f,0.28f), 0.30f, 0.60f, 0.88f, 0.45f),
    part(Array(-0.40f,-0.28f, -0.06f,-0.76f, 0.30f,-0.24f), 0.52f, 0.80f, 0.98f, 0.40f),
    part(Array(-0.52f,0.24f, -0.18f,0.70f, 0.18f,0.22f), 0.52f, 0.80f, 0.98f, 0.40f),
    part(Array(-0.62f,-0.16f, 0.10f,-0.18f, 1.00f,0f, 0.10f,0.06f, -0.62f,0.04f), 0.86f, 0.96f, 1f, 0.18f)
  )

  private val LANCE_PARTS = Array(
    part(Array(-1.15f,-0.12f, 0.15f,-0.30f, 1.45f,0f, 0.15f,0.30f, -1.15f,0.12f), 0.30f, 0.12f, 0.50f, 0.55f),
    part(Array(-0.85f,-0.05f, 0.15f,-0.14f, 1.15f,0f, 0.15f,0.14f, -0.85f,0.05f), 0.05f, 0.02f, 0.09f, 0.05f),
    part(Array(0.30f,-0.22f, 0.62f,-0.17f, 1.32f,0f, 0.62f,-0.05f), 0.85f, 0.65f, 1f, 0.35f)
  )

  private def shaftParts(kind: Int): Array[Part] = kind match {
    case SHF_SPEAR => SPEAR_PARTS
    case SHF_ARROW => ARROW_PARTS
    case SHF_DART  => DART_PARTS
    case SHF_THORN => THORN_PARTS
    case SHF_PARROW => PARROW_PARTS
    case SHF_ICE   => ICE_PARTS
    case SHF_LANCE => LANCE_PARTS
    case _         => ARROW_PARTS
  }

  /**
   * Shaft weapon flying point-first: spear, arrow, blowdart, thorn.
   *
   * These used to be drawn as a `strokeLine` from the projectile back over `worldLen`
   * world units — 6 units is 120 virtual px, so what reached the screen was a ~190px
   * hairline with a 13px head on the end. The silhouette below is a whole object about
   * two tiles long with a head that carries real area, which is what makes a spear read
   * as a spear rather than as a scratch on the display.
   */
  private[projectiles] def flyingShaft(kind: Int, r: Float, g: Float, b: Float, size: Float = 24f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 31) * 0.4
      computeAllDynamics(proj, r, g, b, phase)
      val p = (0.86f + 0.14f * Math.sin(phase * _stPulseMult).toFloat) * dynAlpha
      val dr = _evoR; val dg = _evoG; val db = _evoB
      // Physical shafts barely inflate with charge — they gain a hotter head instead.
      val ds = Math.min(dynScale, 1.25f)
      val s = size * ds
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy
      val parts = shaftParts(kind)

      drawSpeedLines(sx, sy, ndx, ndy, dr, dg, db, 0.28f * p, sb, 4 + (_lifePct * 2).toInt, s * 1.3f)
      drawRibbonTrail(sx, sy, ndx, ndy, dr, dg, db, 0.24f * p, sb, tick, proj.id, 7,
        s * 1.9f * dynTrail, s * 0.16f, 1f)

      // Ground shadow under the shaft
      sb.fillOval(sx + 3f, sy + s * 0.42f, s * 0.72f, s * 0.16f, 0f, 0f, 0f, 0.22f * p, 12)

      // Two afterimages down the flight path — motion without smearing the silhouette
      var ghost = 2; while (ghost >= 1) {
        drawPartsDirFlat(sb, parts, sx - ndx * ghost * 9f, sy - ndy * ghost * 9f, ndx, ndy,
          s * (1f - ghost * 0.05f), dr * 0.85f, dg * 0.85f, db * 0.85f, 0.14f * (1f - (ghost - 1) * 0.35f) * p)
        ghost -= 1
      }

      // Halo pinned to the head, not the middle, so the eye lands on the business end
      dirPoint(0.9f, 0f, sx, sy, ndx, ndy, s)
      val headX = _ptX; val headY = _ptY
      sb.fillOvalSoft(headX, headY, s * 0.85f * dynGlow, s * 0.68f * dynGlow, dr, dg, db, 0.24f * p, 0f, 14)

      drawPartsDir(sb, parts, sx, sy, ndx, ndy, s, dr, dg, db, 0.97f * dynAlpha,
        clampF(s * 0.11f, 1.3f, 2.8f))

      // Head glint. For the spear this also carries the distance-damage ramp: the point
      // brightens as the throw travels, which is exactly when it starts hitting harder.
      val heat = if (kind == SHF_SPEAR) _lifePct else 0f
      sb.fillStarFlare(headX, headY, s * (0.42f + heat * 0.5f), 2.2f,
        (phase * 0.6).toFloat, 0.5f, 1f, mix(1f, 0.85f, heat), mix(0.95f, 0.45f, heat),
        (0.55f + heat * 0.4f) * p)

      kind match {
        case SHF_DART | SHF_PARROW =>
          // Venom beading off the point and falling away
          var i = 0; while (i < 3) {
            val t = ((tick * 0.05 + i * 0.34 + proj.id * 0.19) % 1.0).toFloat
            dirPoint(1.15f - t * 0.5f, 0f, sx, sy, ndx, ndy, s)
            val dy2 = t * t * 14f
            sb.fillOval(_ptX, _ptY + dy2, 3.2f * (1f - t * 0.5f), 3.8f * (1f - t * 0.5f),
              0.45f, 0.9f, 0.35f, 0.65f * (1f - t) * p, 8)
            i += 1
          }
        case SHF_THORN =>
          // Leaf motes shaken loose behind the spike
          var i = 0; while (i < 4) {
            val t = ((tick * 0.035 + i * 0.25 + proj.id * 0.13) % 1.0).toFloat
            val drift = Math.sin(t * 6.0 + i).toFloat * 7f
            val lx = sx - ndx * t * s * 1.6f - ndy * drift
            val ly = sy - ndy * t * s * 1.6f + ndx * drift + t * t * 9f
            sb.fillOval(lx, ly, 3.6f * (1f - t * 0.5f), 2.4f * (1f - t * 0.5f),
              0.32f, 0.62f, 0.24f, 0.5f * (1f - t) * p, 6)
            i += 1
          }
        case SHF_ARROW =>
          drawSparkBurst(headX, headY, dr, dg, db, 0.34f * p, sb, tick, proj.id, 3, s * 0.4f)
        case SHF_LANCE =>
          // Space bending around the lance: warp rings contracting onto the shaft
          var k = 0
          while (k < 3) {
            val t = ((tick * 0.07 + k * 0.33 + proj.id * 0.13) % 1.0).toFloat
            val ti = 1f - t
            dirPoint(-0.7f + k * 0.55f, 0f, sx, sy, ndx, ndy, s)
            strokeRotEllipse(sb, _ptX, _ptY, -ndy, ndx, s * (0.22f + ti * 0.6f), s * (0.07f + ti * 0.18f),
              1.8f, bright(r), bright(g), bright(b), 0.65f * t * p, 12)
            k += 1
          }
        case SHF_ICE =>
          // Frost crystals shedding off the spike and settling behind it
          var i = 0; while (i < 5) {
            val t = ((tick * 0.04 + i * 0.2 + proj.id * 0.17) % 1.0).toFloat
            val drift = Math.sin(t * 5.0 + i * 2.1).toFloat * 8f
            val fx = sx - ndx * t * s * 1.7f - ndy * drift
            val fy = sy - ndy * t * s * 1.7f + ndx * drift
            drawSparkleStar(fx, fy, 5.5f * (1f - t * 0.6f), 0.85f, 0.96f, 1f, 0.6f * (1f - t) * p, sb, t * 6.0 + i)
            i += 1
          }
        case _ =>
          // Spear: a light streak riding the shaft toward the point
          val t = ((tick * 0.09 + proj.id * 0.21) % 1.0).toFloat
          dirPoint(-1f + t * 2.2f, 0f, sx, sy, ndx, ndy, s)
          sb.fillOvalSoft(_ptX, _ptY, s * 0.22f, s * 0.14f, 1f, 0.95f, 0.7f, 0.5f * (1f - t) * p, 0f, 8)
      }

      drawChargeCrackle(headX, headY, s * 0.5f, r, g, b, p, sb, phase, proj.chargeLevel)
      drawReturnGhosts(sx, sy, s * 0.5f, dr, dg, db, p, sb, proj)
    }
}
