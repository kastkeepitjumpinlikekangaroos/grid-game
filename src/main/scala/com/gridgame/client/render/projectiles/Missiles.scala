package com.gridgame.client.render.projectiles

import GLProjectileRenderers.Renderer
import ProjectileKit._
import Silhouettes._

/** Small fast rounds (bulletProj) and the gauntleted punch (fistProj). */
private[render] object Missiles {
  /** Small fast bullet — CARTOONISH with bold outline, dramatic muzzle flash, ribbon trail, shell casings */
  private[projectiles] def bulletProj(r: Float, g: Float, b: Float, size: Float = 5f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 31) * 0.4
      computeAllDynamics(proj, r, g, b, phase)
      val p = (0.9 + 0.1 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
      val dr = _evoR; val dg = _evoG; val db = _evoB
      val ds = dynScale
      val sz = size * 1.5f * ds
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy
      val perpX = -ndy; val perpY = ndx

      // Speed lines behind
      drawSpeedLines(sx, sy, ndx, ndy, dr, dg, db, 0.35f * p, sb, 6, 35f * ds)

      // Ribbon trail behind bullet
      drawRibbonTrail(sx, sy, ndx, ndy, dr, dg, db, 0.3f * p, sb, tick, proj.id, 6, 40f * ds * dynTrail, 4f * ds, 0.8f)

      // Bullet body
      val bodyLen = sz * 3f
      val bodyW = sz * 1f
      val tipX = sx + ndx * bodyLen * 0.5f
      val tipY = sy + ndy * bodyLen * 0.5f
      val tailX = sx - ndx * bodyLen * 0.5f
      val tailY = sy - ndy * bodyLen * 0.5f
      val flashX = tailX - ndx * 4f; val flashY = tailY - ndy * 4f

      // Big 3-layer muzzle flash at rear — dramatic orange-yellow fire
      val flashSize = 22f * (0.7f + 0.3f * Math.sin(phase * 3).toFloat) * ds
      // Layer 1: Dark outline puff
      sb.fillOvalSoft(flashX, flashY, flashSize * 1.3f, flashSize * 0.9f, outline(1f), outline(0.5f), outline(0.1f), 0.3f * p, 0f, 12)
      // Layer 2: Orange-yellow fire
      sb.fillOvalSoft(flashX, flashY, flashSize, flashSize * 0.7f, 1f, 0.7f, 0.2f, 0.4f * p, 0f, 10)
      sb.fillOval(flashX, flashY, flashSize * 0.7f, flashSize * 0.5f, 1f, 0.85f, 0.3f, 0.5f * p, 8)
      // Layer 3: White-hot core
      sb.fillOval(flashX, flashY, flashSize * 0.35f, flashSize * 0.25f, 1f, 1f, 0.8f, 0.65f * p, 6)

      // 4 cartridge smoke puffs with outlines
      { var puff = 0; while (puff < 4) {
        val puffT = ((tick * 0.04 + puff * 0.25 + proj.id * 0.17) % 1.0).toFloat
        val puffDrift = puff * 0.4f - 0.6f
        val puffX = flashX - ndx * (8f + puffT * 20f) + perpX * puffDrift * 12f
        val puffY = flashY - ndy * (8f + puffT * 20f) + perpY * puffDrift * 12f - puffT * 6f
        val puffSz = (5f + puffT * 8f) * ds
        val puffA = 0.3f * (1f - puffT) * p
        sb.strokeOval(puffX, puffY, puffSz + 1f, puffSz * 0.8f + 1f, 1.5f, 0.2f, 0.2f, 0.2f, puffA * 0.4f, 8)
        sb.fillOval(puffX, puffY, puffSz, puffSz * 0.8f, 0.55f, 0.5f, 0.45f, puffA, 8)
      ; puff += 1 } }

      // 3 shell casing particles tumbling behind
      { var cas = 0; while (cas < 3) {
        val casT = ((tick * 0.05 + cas * 0.33 + proj.id * 0.19) % 1.0).toFloat
        val casSpin = tick * 0.25f + cas * 2.1f
        val casX = flashX - ndx * casT * 15f + perpX * (cas - 1) * 10f * casT
        val casY = flashY - ndy * casT * 15f + perpY * (cas - 1) * 10f * casT + casT * casT * 18f
        val casLen = 4f * ds; val casW = 2f * ds
        val casA = 0.5f * (1f - casT) * p
        val ccos = Math.cos(casSpin).toFloat; val csin = Math.sin(casSpin).toFloat
        sb.strokeLine(casX - ccos * casLen, casY - csin * casLen,
          casX + ccos * casLen, casY + csin * casLen, casW + 1f, 0.15f, 0.12f, 0.08f, casA * 0.5f)
        sb.strokeLine(casX - ccos * casLen, casY - csin * casLen,
          casX + ccos * casLen, casY + csin * casLen, casW, 0.8f, 0.7f, 0.3f, casA)
      ; cas += 1 } }

      // Dark cartoon outline
      sb.strokeLine(tailX, tailY, tipX, tipY, bodyW * 2.5f, outline(r), outline(g), outline(b), 0.9f * p)
      // Metallic body — evolved colors
      sb.strokeLine(tailX, tailY, tipX, tipY, bodyW * 1.8f, dr, dg, db, 0.95f * p)
      // Specular highlight — charge whitening
      sb.strokeLine(tailX, tailY, tipX, tipY, bodyW * 0.6f,
        mix(bright(r), 1f, _chgBright), mix(bright(g), 1f, _chgBright), mix(bright(b), 1f, _chgBright), 0.7f * p)
      // Rounded tip with outline
      sb.strokeOval(tipX, tipY, bodyW * 1.6f, bodyW * 1.2f, 2f, outline(r), outline(g), outline(b), 0.8f * p, 8)
      sb.fillOval(tipX, tipY, bodyW * 1.4f, bodyW * 1f, bright(r), bright(g), bright(b), 0.9f * p, 8)

      // Impact ring pulsing at front
      val impPulse = ((phase * 0.5) % 1.0).toFloat
      val impR = 4f + impPulse * 14f * ds
      val impA = 0.45f * (1f - impPulse) * p
      sb.strokeOval(tipX, tipY, impR, impR * 0.7f, 2.5f * (1f - impPulse * 0.4f),
        bright(r), bright(g), bright(b), impA, 10)

      // Spark burst at tip
      drawSparkBurst(tipX, tipY, dr, dg, db, 0.35f * p, sb, tick, proj.id, 4, 12f * ds)

      // 3 sparkle stars
      { var star = 0; while (star < 3) {
        val starPhase = ((phase * 0.4 + star * 0.33) % 1.0).toFloat
        val starAngle = phase * 1.5 + star * Math.PI * 2 / 3
        val starDist = sz * 1.5f + starPhase * sz * 1.2f
        val starX = sx + Math.cos(starAngle).toFloat * starDist
        val starY = sy + Math.sin(starAngle).toFloat * starDist * 0.55f
        drawSparkleStar(starX, starY, 4.5f * (1f - starPhase * 0.4f) * ds,
          bright(r), bright(g), bright(b), 0.45f * (1f - starPhase) * p, sb, phase * 2 + star)
      ; star += 1 } }

      // Charge crackle
      drawChargeCrackle(sx, sy, sz * 2.5f, r, g, b, p, sb, phase, proj.chargeLevel)
      drawReturnGhosts(sx, sy, sz * 2f, dr, dg, db, p, sb, proj)
    }

  private val FIST_PARTS = Array(
    part(Array(-1.20f,-0.28f, -0.30f,-0.38f, -0.30f,0.38f, -1.20f,0.28f), 0.62f, 0.46f, 0.34f, 0.35f),
    part(Array(-0.46f,-0.50f, -0.24f,-0.52f, -0.24f,0.52f, -0.46f,0.50f), DKSTEEL_R, DKSTEEL_G, DKSTEEL_B, 0.45f),
    part(Array(-0.26f,-0.52f, 0.42f,-0.58f, 0.82f,-0.30f, 0.82f,0.30f, 0.42f,0.58f, -0.26f,0.52f),
      0.72f, 0.55f, 0.42f, 0.35f),
    part(Array(-0.20f,-0.34f, 0.40f,-0.40f, 0.66f,-0.20f, 0.10f,-0.12f), 0.86f, 0.70f, 0.56f, 0.20f)
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
      val sz = size * 1.15f * ds
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
        clampF(sz * 0.13f, 1.5f, 3f))

      // Four knuckles across the leading face
      var k = 0; while (k < 4) {
        val ly = -0.36f + k * 0.24f
        dirPoint(0.70f, ly, sx, sy, ndx, ndy, sz)
        sb.fillOval(_ptX, _ptY, sz * 0.15f, sz * 0.15f, 0.06f, 0.05f, 0.07f, 0.6f * p, 8)
        sb.fillOval(_ptX - sz * 0.03f, _ptY - sz * 0.03f, sz * 0.11f, sz * 0.11f,
          mix(0.88f, dr, 0.3f), mix(0.72f, dg, 0.3f), mix(0.58f, db, 0.3f), 0.9f * p, 8)
        k += 1
      }

      drawSparkBurst(frontX, frontY, dr, dg, db, 0.45f * p, sb, tick, proj.id, 5, sz * 0.8f)
      drawChargeCrackle(sx, sy, sz, r, g, b, p, sb, phase, proj.chargeLevel)
      drawReturnGhosts(sx, sy, sz * 0.7f, dr, dg, db, p, sb, proj)
    }
}
