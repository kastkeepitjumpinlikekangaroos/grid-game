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
  final val SHF_CARROW = 7   // the Centaur's arrow
  // A spear: a slim ash shaft with a leather grip and a bronze butt spike, and a leaf blade on a bronze
  // socket with a wing either side of it, its upper face lit. Inked as one. (It was a plank with a
  // gold block on each end and two more crossed through it.)
  private val SPEAR_PARTS = Array(
    part(Array(-1.20f,-0.06f, 0.52f,-0.06f, 0.52f,0.06f, -1.20f,0.06f), WOOD_R, WOOD_G, WOOD_B, 0.08f),
    part(Array(-1.42f,0f, -1.18f,-0.10f, -1.18f,0.10f), GOLD_R, GOLD_G, GOLD_B, 0.12f),
    part(Array(-0.36f,-0.085f, 0.02f,-0.085f, 0.02f,0.085f, -0.36f,0.085f), LEATHER_R, LEATHER_G, LEATHER_B, 0.05f),
    part(Array(0.40f,-0.06f, 0.56f,-0.06f, 0.50f,-0.33f, 0.38f,-0.29f), GOLD_R, GOLD_G, GOLD_B, 0.12f),
    part(Array(0.40f,0.06f, 0.38f,0.29f, 0.50f,0.33f, 0.56f,0.06f), GOLD_R * 0.72f, GOLD_G * 0.72f, GOLD_B * 0.72f, 0.12f),
    part(Array(0.44f,-0.11f, 0.66f,-0.11f, 0.66f,0.11f, 0.44f,0.11f), GOLD_R, GOLD_G, GOLD_B, 0.12f),
    part(Array(0.62f,-0.12f, 0.84f,-0.30f, 1.44f,0f, 0.84f,0.30f, 0.62f,0.12f), DKSTEEL_R, DKSTEEL_G, DKSTEEL_B, 0.15f),
    part(Array(0.64f,0f, 0.84f,-0.30f, 1.44f,0f), STEEL_R, STEEL_G, STEEL_B, 0.12f),
    part(Array(0.84f,-0.04f, 0.88f,-0.19f, 1.20f,-0.04f), 1f, 1f, 1f, 0.05f)
  )

  /**
   * An arrow: a slim shaft with a nock, three feathers (one swept back off each side in (ar, ag, ab)
   * and (br, bg, bb), the third seen edge on down the shaft), a binding where the head is tied on, and
   * a broadhead in (hr, hg, hb), its upper face lit. Inked as one (drawPartsDirUnion). It was a plank
   * with two tiles for feathers and a head no wider than they were.
   */
  private def arrowParts(wr: Float, wg: Float, wb: Float, ar: Float, ag: Float, ab: Float,
                         br: Float, bg: Float, bb: Float, hr: Float, hg: Float, hb: Float, headTint: Float): Array[Part] = Array(
    part(Array(-1.02f,-0.055f, 0.64f,-0.055f, 0.64f,0.055f, -1.02f,0.055f), wr, wg, wb, 0.08f),
    part(Array(-1.16f,-0.085f, -1.00f,-0.085f, -1.00f,0.085f, -1.16f,0.085f), DKWOOD_R, DKWOOD_G, DKWOOD_B, 0.05f),
    part(Array(-0.98f,-0.05f, -0.40f,-0.05f, -0.64f,-0.38f, -1.12f,-0.42f), ar, ag, ab, 0.12f),
    part(Array(-0.98f,0.05f, -1.12f,0.42f, -0.64f,0.38f, -0.40f,0.05f), br, bg, bb, 0.12f),
    part(Array(-0.98f,-0.05f, -0.80f,-0.05f, -0.98f,-0.40f, -1.12f,-0.42f), ar * 0.72f, ag * 0.72f, ab * 0.72f, 0.12f),
    part(Array(-0.98f,0.05f, -1.12f,0.42f, -0.98f,0.40f, -0.80f,0.05f), br * 0.72f, bg * 0.72f, bb * 0.72f, 0.12f),
    part(Array(-1.00f,-0.03f, -0.44f,-0.03f, -0.44f,0.03f, -1.00f,0.03f), mix(ar, 1f, 0.45f), mix(ag, 1f, 0.45f), mix(ab, 1f, 0.45f), 0.08f),
    part(Array(0.48f,-0.085f, 0.62f,-0.085f, 0.62f,0.085f, 0.48f,0.085f), LEATHER_R, LEATHER_G, LEATHER_B, 0.05f),
    part(Array(0.60f,-0.10f, 0.72f,-0.36f, 1.40f,0f, 0.72f,0.36f, 0.60f,0.10f), hr * 0.6f, hg * 0.6f, hb * 0.64f, headTint),
    part(Array(0.64f,0f, 0.72f,-0.36f, 1.40f,0f), hr, hg, hb, headTint),
    part(Array(0.76f,-0.04f, 0.80f,-0.22f, 1.14f,-0.04f), mix(hr, 1f, 0.6f), mix(hg, 1f, 0.6f), mix(hb, 1f, 0.6f), headTint * 0.5f)
  )

  // The Ranger's: red and white feathers, a steel head
  private val ARROW_PARTS = arrowParts(WOOD_R, WOOD_G, WOOD_B, 0.86f, 0.24f, 0.2f, 0.95f, 0.94f, 0.9f,
    STEEL_R, STEEL_G, STEEL_B, 0.12f)
  // The Centaur's: hawk feathers, barred brown and cream, on a dark shaft with a bronze head
  private val CARROW_PARTS = arrowParts(0.36f, 0.25f, 0.16f, 0.62f, 0.42f, 0.2f, 0.93f, 0.86f, 0.68f,
    0.9f, 0.66f, 0.3f, 0.1f)
  // The Ranger's poisoned one: green feathers and a head wet with it
  private val PARROW_PARTS = arrowParts(0.4f, 0.34f, 0.2f, 0.28f, 0.52f, 0.2f, 0.62f, 0.8f, 0.36f,
    0.5f, 0.92f, 0.36f, 0.3f)

  // A blowpipe dart: a needle on a slim shaft, a tuft of down bound on behind it
  private val DART_PARTS = Array(
    part(Array(-0.56f,-0.045f, 0.66f,-0.045f, 0.66f,0.045f, -0.56f,0.045f), 0.34f, 0.26f, 0.16f, 0.08f),
    part(Array(-0.54f,-0.04f, -0.84f,-0.04f, -1.10f,-0.44f, -0.70f,-0.38f), 0.96f, 0.82f, 0.3f, 0.15f),
    part(Array(-0.54f,0.04f, -0.70f,0.38f, -1.10f,0.44f, -0.84f,0.04f), 0.9f, 0.6f, 0.2f, 0.15f),
    part(Array(-0.54f,-0.07f, -1.22f,-0.11f, -1.22f,0.11f, -0.54f,0.07f), 1f, 0.94f, 0.6f, 0.1f),
    part(Array(-0.58f,-0.10f, -0.44f,-0.10f, -0.44f,0.10f, -0.58f,0.10f), 0.7f, 0.16f, 0.14f, 0.05f),
    part(Array(0.60f,-0.085f, 1.36f,0f, 0.60f,0.085f), 0.66f, 0.72f, 0.74f, 0.2f),
    part(Array(0.96f,-0.045f, 1.36f,0f, 0.96f,0.045f), 0.5f, 0.95f, 0.38f, 0.5f)
  )

  private val THORN_PARTS = Array(
    part(Array(-1.00f,-0.17f, 0.45f,-0.13f, 1.32f,0f, 0.45f,0.13f, -1.00f,0.17f), 0.36f, 0.28f, 0.14f, 0.25f),
    part(Array(-0.34f,-0.12f, -0.06f,-0.56f, 0.12f,-0.11f), 0.30f, 0.24f, 0.12f, 0.25f),
    part(Array(0.06f,0.11f, 0.34f,0.54f, 0.48f,0.10f), 0.30f, 0.24f, 0.12f, 0.25f),
    part(Array(-0.74f,0.13f, -0.52f,0.48f, -0.38f,0.12f), 0.30f, 0.24f, 0.12f, 0.25f),
    part(Array(-0.98f,-0.12f, -0.62f,-0.44f, -1.12f,-0.56f, -1.30f,-0.18f), 0.30f, 0.62f, 0.22f, 0.55f),
    part(Array(0.52f,-0.07f, 1.16f,0f, 0.52f,0.05f), 0.62f, 0.86f, 0.40f, 0.45f)
  )

  // A shard of ice: a long crystal pointed at both ends with a ridge down it, its upper face lit, and
  // a splinter swept back off each side. (It was a slab with a square end and a diamond through it.)
  private val ICE_PARTS = Array(
    part(Array(-0.50f,-0.22f, -0.92f,-0.74f, 0.12f,-0.28f), 0.44f, 0.74f, 0.96f, 0.40f),
    part(Array(-0.50f,0.22f, -0.92f,0.74f, 0.12f,0.28f), 0.30f, 0.58f, 0.88f, 0.40f),
    part(Array(-1.10f,0f, -0.58f,-0.31f, 0.26f,-0.35f, 1.32f,0f, 0.26f,0.35f, -0.58f,0.31f), 0.28f, 0.56f, 0.88f, 0.45f),
    part(Array(-1.10f,0f, -0.58f,-0.31f, 0.26f,-0.35f, 1.32f,0f), 0.62f, 0.86f, 1f, 0.30f)
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
    case SHF_CARROW => CARROW_PARTS
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

      val fletched = kind == SHF_ARROW || kind == SHF_PARROW || kind == SHF_CARROW || kind == SHF_DART
      // Ground shadow under the shaft
      sb.fillOval(sx + 3f, sy + s * 0.42f, s * 0.72f, s * 0.16f, 0f, 0f, 0f, 0.22f * p, 12)

      // Its wake: the air parted behind it, one line off its tail and a thinner one off each side.
      // (Speed lines, a ribbon, two afterimages, a halo on its head and a star on its point were a
      // smear the shaft was lost in.)
      val wl = s * 1.7f * dynTrail
      dirPoint(-1.05f, 0f, sx, sy, ndx, ndy, s)
      fadeLine(sb, _ptX, _ptY, _ptX - ndx * wl, _ptY - ndy * wl, s * 0.11f, mix(dr, 1f, 0.55f), mix(dg, 1f, 0.55f),
        mix(db, 1f, 0.55f), 0.6f * p, 5)
      var side = -1
      while (side <= 1) {
        dirPoint(-0.7f, side * 0.4f, sx, sy, ndx, ndy, s)
        fadeLine(sb, _ptX, _ptY, _ptX - ndx * wl * 0.6f, _ptY - ndy * wl * 0.6f, 1.3f, 1f, 1f, 1f, 0.5f * p, 4)
        side += 2
      }

      dirPoint(0.9f, 0f, sx, sy, ndx, ndy, s)
      val headX = _ptX; val headY = _ptY
      if (fletched) {
        // Spinning as it flies: its feathers swap sides, the cock feather coming round
        val roll = if (((tick + proj.id * 3) / 4 & 1) == 0) 1f else -1f
        drawPartsDirUnion(sb, parts, sx, sy, ndx, ndy, s, dr, dg, db, 0.98f * dynAlpha, clampF(s * 0.09f, 1.3f, 2.3f), roll)
      } else if (kind == SHF_SPEAR)
        drawPartsDirUnion(sb, parts, sx, sy, ndx, ndy, s, dr, dg, db, 0.98f * dynAlpha, clampF(s * 0.09f, 1.3f, 2.4f))
      else
        drawPartsDir(sb, parts, sx, sy, ndx, ndy, s, dr, dg, db, 0.97f * dynAlpha, clampF(s * 0.11f, 1.3f, 2.8f))

      if (kind == SHF_SPEAR) {
        // The spear's point carries its distance-damage ramp: it brightens as the throw travels,
        // which is exactly when it starts hitting harder.
        val heat = _lifePct
        sb.fillStarFlare(headX, headY, s * (0.3f + heat * 0.6f), 2.2f,
          (phase * 0.6).toFloat, 0.5f, 1f, mix(1f, 0.85f, heat), mix(0.95f, 0.45f, heat), (0.35f + heat * 0.6f) * p)
      } else if (fletched) {
        // A glint running down the edge of the head
        val gt = Glyphs.loop(tick, 0.07, proj.id * 0.21)
        dirPoint(0.76f + gt * 0.5f, -0.2f + gt * 0.17f, sx, sy, ndx, ndy, s)
        sb.fillOval(_ptX, _ptY, 1.5f * (1f - gt) + 0.5f, 1.5f * (1f - gt) + 0.5f, 1f, 1f, 1f, (1f - gt) * p, 6)
      }

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
