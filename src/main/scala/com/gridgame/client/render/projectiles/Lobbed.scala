package com.gridgame.client.render.projectiles

import com.gridgame.client.gl.ShapeBatch
import GLProjectileRenderers.Renderer
import ProjectileKit._
import Silhouettes._

/** Objects thrown on an arc (lobbed): bombs, flasks, shovels, hammers, horns, mines, ice chunks, mud
  * globs, with a shadow on the ground and a ring where it will land. */
private[render] object Lobbed {
  // ── Thrown-object kinds for `lobbed` ──
  // A grenade, a gravedigger's shovel, a blacksmith's hammer and a minotaur's horn are
  // all "an object on an arc", and the old factory drew all four as the same grey disc
  // with a shadow. The kind selects a body so the arc still says whose ability it is.
  final val LOB_BOMB = 0
  final val LOB_FLASK = 1
  final val LOB_SHOVEL = 2
  final val LOB_HAMMER = 3
  final val LOB_HORN = 4
  final val LOB_MINE = 5
  final val LOB_ICE = 6
  final val LOB_GLOB = 7
  private val SHOVEL_PARTS = Array(
    part(Array(-1.15f,-0.10f, 0.22f,-0.10f, 0.22f,0.10f, -1.15f,0.10f), WOOD_R, WOOD_G, WOOD_B, 0.12f),
    part(Array(-1.30f,-0.34f, -1.10f,-0.34f, -1.10f,0.34f, -1.30f,0.34f), DKWOOD_R, DKWOOD_G, DKWOOD_B, 0.12f),
    part(Array(0.18f,-0.44f, 0.72f,-0.42f, 1.16f,0f, 0.72f,0.42f, 0.18f,0.44f), DKSTEEL_R, DKSTEEL_G, DKSTEEL_B, 0.30f),
    part(Array(0.30f,-0.26f, 0.70f,-0.24f, 0.96f,0f, 0.70f,0.10f, 0.30f,0.06f), STEEL_R, STEEL_G, STEEL_B, 0.18f)
  )

  private val HAMMER_PARTS = Array(
    part(Array(-1.20f,-0.11f, 0.42f,-0.11f, 0.42f,0.11f, -1.20f,0.11f), WOOD_R, WOOD_G, WOOD_B, 0.12f),
    part(Array(0.38f,-0.56f, 1.18f,-0.50f, 1.18f,0.50f, 0.38f,0.56f), DKSTEEL_R, DKSTEEL_G, DKSTEEL_B, 0.35f),
    part(Array(0.46f,-0.42f, 1.06f,-0.38f, 1.06f,-0.06f, 0.46f,-0.10f), STEEL_R, STEEL_G, STEEL_B, 0.20f),
    part(Array(-1.32f,-0.16f, -1.14f,-0.16f, -1.14f,0.16f, -1.32f,0.16f), DKSTEEL_R, DKSTEEL_G, DKSTEEL_B, 0.30f)
  )

  private[projectiles] val HORN_PARTS = Array(
    part(Array(-1.05f,-0.30f, -0.30f,-0.34f, -0.30f,0.28f, -1.05f,0.32f), 0.90f, 0.86f, 0.76f, 0.20f),
    part(Array(-0.32f,-0.36f, 0.42f,-0.50f, 0.42f,0.06f, -0.32f,0.26f), 0.86f, 0.81f, 0.70f, 0.20f),
    part(Array(0.40f,-0.52f, 1.12f,-0.86f, 1.02f,-0.48f, 0.40f,0.04f), 0.78f, 0.72f, 0.62f, 0.20f),
    part(Array(-1.10f,-0.24f, -0.86f,-0.26f, -0.86f,0.26f, -1.10f,0.24f), 0.46f, 0.34f, 0.28f, 0.25f)
  )

  /** The object itself, drawn at the top of the arc. `spin` tumbles the rigid kinds; the
   *  round kinds stay upright and animate their own detail instead. */
  private def drawLobBody(sb: ShapeBatch, kind: Int, cx: Float, cy: Float, sz: Float,
                          spin: Float, phase: Double, sx2: Float, sy2: Float,
                          dr: Float, dg: Float, db: Float, r: Float, g: Float, b: Float,
                          a: Float): Unit = {
    val ca = Math.cos(spin).toFloat; val sa = Math.sin(spin).toFloat
    val ow = clampF(sz * 0.12f, 1.4f, 3f)
    kind match {
      case LOB_SHOVEL => drawParts(sb, SHOVEL_PARTS, cx, cy, ca, sa, sz * 0.95f, dr, dg, db, a, ow)
      case LOB_HAMMER => drawParts(sb, HAMMER_PARTS, cx, cy, ca, sa, sz * 0.90f, dr, dg, db, a, ow)
      case LOB_HORN   => drawParts(sb, HORN_PARTS, cx, cy, ca, sa, sz * 0.95f, dr, dg, db, a, ow)

      case LOB_BOMB =>
        // Cast-iron sphere with a banded seam, a collar and a burning fuse
        sb.strokeOval(cx, cy, sz * 0.98f, sz * 0.86f, ow + 1f, 0.05f, 0.05f, 0.07f, 0.9f * a, 18)
        sb.fillOval(cx, cy, sz * 0.95f, sz * 0.83f, 0.17f, 0.17f, 0.20f, a, 18)
        sb.fillOval(cx - sz * 0.26f, cy - sz * 0.26f, sz * 0.30f, sz * 0.24f, 0.62f, 0.64f, 0.70f, 0.55f * a, 10)
        sb.strokeArc(cx, cy, sz * 0.72f, sz * 0.62f, 0.6f, 2.2f, 2f, 0.34f, 0.35f, 0.40f, 0.8f * a, 8)
        sb.fillRoundedRect(cx - sz * 0.20f, cy - sz * 1.00f, sz * 0.40f, sz * 0.28f, 2f,
          mix(0.30f, dr, 0.4f), mix(0.30f, dg, 0.4f), mix(0.34f, db, 0.4f), a)
        // Fuse: a curl with a spark that eats along it
        val fx = cx + sz * 0.12f; val fy = cy - sz * 1.06f
        val curl = Math.sin(phase * 2.2).toFloat * sz * 0.16f
        sb.strokeLine(fx, fy, fx + sz * 0.22f + curl, fy - sz * 0.40f, 2.4f, 0.42f, 0.36f, 0.26f, 0.9f * a)
        val spark = fx + sz * 0.24f + curl
        val sparkY = fy - sz * 0.44f
        sb.fillOvalSoft(spark, sparkY, sz * 0.32f, sz * 0.32f, 1f, 0.72f, 0.2f, 0.7f * a, 0f, 10)
        sb.fillStarFlare(spark, sparkY, sz * 0.30f, 2f, phase.toFloat * 3f, 0.6f, 1f, 0.92f, 0.6f, 0.9f * a)

      case LOB_FLASK =>
        // Corked glass flask with sloshing contents
        sb.strokeOval(cx, cy + sz * 0.10f, sz * 0.82f, sz * 0.72f, ow, 0.06f, 0.08f, 0.06f, 0.85f * a, 16)
        sb.fillOval(cx, cy + sz * 0.10f, sz * 0.80f, sz * 0.70f, 0.62f, 0.72f, 0.66f, 0.35f * a, 16)
        val slosh = Math.sin(phase * 2.6).toFloat * sz * 0.09f
        sb.fillOval(cx + slosh * 0.5f, cy + sz * 0.26f, sz * 0.68f, sz * 0.42f, dr, dg, db, 0.92f * a, 14)
        sb.fillOval(cx + slosh, cy + sz * 0.10f, sz * 0.62f, sz * 0.14f,
          bright(r), bright(g), bright(b), 0.75f * a, 12)
        sb.fillRect(cx - sz * 0.17f, cy - sz * 0.64f, sz * 0.34f, sz * 0.56f, 0.66f, 0.75f, 0.68f, 0.4f * a)
        sb.fillRoundedRect(cx - sz * 0.21f, cy - sz * 0.88f, sz * 0.42f, sz * 0.26f, 2f,
          0.52f, 0.36f, 0.20f, 0.95f * a)
        sb.fillOval(cx - sz * 0.26f, cy - sz * 0.06f, sz * 0.14f, sz * 0.26f, 1f, 1f, 1f, 0.35f * a, 8)
        // Bubbles rising through the liquid
        var i = 0; while (i < 3) {
          val t = ((phase * 0.35 + i * 0.34) % 1.0).toFloat
          sb.fillOval(cx + (i - 1) * sz * 0.18f, cy + sz * 0.36f - t * sz * 0.34f,
            sz * 0.07f, sz * 0.07f, bright(r), bright(g), bright(b), 0.6f * (1f - t) * a, 6)
          i += 1
        }

      case LOB_MINE =>
        // Spiked contact mine with a blinking arming light
        var i = 0; while (i < 8) {
          val ang = spin * 0.5f + i * Math.PI.toFloat / 4f
          val c2 = Math.cos(ang).toFloat; val s2 = Math.sin(ang).toFloat * ISO_Y
          _polyXs3(0) = cx + c2 * sz * 0.62f - s2 * sz * 0.16f
          _polyYs3(0) = cy + s2 * sz * 0.62f + c2 * sz * 0.16f
          _polyXs3(1) = cx + c2 * sz * 0.62f + s2 * sz * 0.16f
          _polyYs3(1) = cy + s2 * sz * 0.62f - c2 * sz * 0.16f
          _polyXs3(2) = cx + c2 * sz * 1.06f; _polyYs3(2) = cy + s2 * sz * 1.06f
          sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.56f, 0.59f, 0.66f, 0.95f * a)
          i += 1
        }
        sb.strokeOval(cx, cy, sz * 0.72f, sz * 0.62f, ow + 1f, 0.05f, 0.05f, 0.07f, 0.9f * a, 16)
        sb.fillOval(cx, cy, sz * 0.70f, sz * 0.60f, 0.30f, 0.33f, 0.39f, a, 16)
        sb.strokeOval(cx, cy, sz * 0.56f, sz * 0.47f, 2f, 0.62f, 0.66f, 0.74f, 0.55f * a, 14)
        sb.fillOval(cx - sz * 0.20f, cy - sz * 0.20f, sz * 0.24f, sz * 0.18f, 0.74f, 0.78f, 0.84f, 0.55f * a, 8)
        val blink = (0.35f + 0.65f * Math.abs(Math.sin(phase * 3.2).toFloat))
        sb.fillOvalSoft(cx, cy, sz * 0.46f, sz * 0.40f, dr, dg, db, 0.55f * blink * a, 0f, 12)
        sb.fillOval(cx, cy, sz * 0.18f, sz * 0.15f, bright(r), bright(g), bright(b), blink * a, 8)

      case LOB_ICE =>
        // Faceted ice/rock chunk — an irregular hull, never a circle
        var i = 0; while (i < 7) {
          val ang = i * (Math.PI * 2 / 7) + spin * 0.35
          val rad = sz * (0.72f + 0.30f * Math.sin(i * 2.7 + proj7(i)).toFloat)
          _shpXs(i) = cx + Math.cos(ang).toFloat * rad
          _shpYs(i) = cy + Math.sin(ang).toFloat * rad * 0.82f
          i += 1
        }
        sb.strokePolygon(_shpXs, _shpYs, 7, ow + 1.5f, 0.05f, 0.09f, 0.16f, 0.92f * a)
        sb.fillFan(cx, cy, _shpXs, _shpYs, 7, dr * 0.72f, dg * 0.82f, db * 0.95f, a)
        // Facets: dark seams from the corners, then one lit face. Without the dark seams
        // a white block on pale ground is a flat cut-out.
        i = 0; while (i < 7) {
          sb.strokeLine(_shpXs(i), _shpYs(i), cx + (_shpXs(i) - cx) * 0.15f, cy + (_shpYs(i) - cy) * 0.15f,
            1.8f, 0.16f, 0.28f, 0.44f, 0.5f * a)
          i += 1
        }
        _polyXs3(0) = cx; _polyYs3(0) = cy
        _polyXs3(1) = _shpXs(5); _polyYs3(1) = _shpYs(5)
        _polyXs3(2) = _shpXs(6); _polyYs3(2) = _shpYs(6)
        sb.fillPolygon(_polyXs3, _polyYs3, 3, 1f, 1f, 1f, 0.34f * a)
        sb.fillOval(cx - sz * 0.22f, cy - sz * 0.26f, sz * 0.26f, sz * 0.18f, 1f, 1f, 1f, 0.55f * a, 8)

      case _ =>
        // LOB_GLOB — a wobbling sack of mud/ink with drips peeling off the bottom
        val wob = Math.sin(phase * 3.1).toFloat
        sb.strokeOval(cx, cy, sz * (0.92f + wob * 0.10f), sz * (0.80f - wob * 0.10f), ow + 1f,
          0.05f, 0.05f, 0.06f, 0.85f * a, 16)
        sb.fillOval(cx, cy, sz * (0.90f + wob * 0.10f), sz * (0.78f - wob * 0.10f), dr, dg, db, a, 16)
        sb.fillOval(cx - sz * 0.24f, cy - sz * 0.22f, sz * 0.24f, sz * 0.16f,
          bright(r), bright(g), bright(b), 0.4f * a, 8)
        var i = 0; while (i < 3) {
          val t = ((phase * 0.4 + i * 0.34) % 1.0).toFloat
          val dxo = (i - 1) * sz * 0.34f
          sb.fillOval(cx + dxo, cy + sz * 0.55f + t * sz * 0.7f, sz * 0.16f * (1f - t * 0.4f),
            sz * 0.22f * (1f - t * 0.4f), dark(r), dark(g), dark(b), 0.7f * (1f - t) * a, 8)
          i += 1
        }
    }
  }

  /** Deterministic per-vertex jitter for the ice chunk's hull. */
  @inline private def proj7(i: Int): Float = (i * 1.7f) % 3.1f

  /**
   * Object travelling on a lobbed arc: bomb, flask, shovel, hammer, horn, mine, ice
   * chunk, mud glob. The arc machinery (bounce, squash, landing shadow and target ring,
   * afterimages) is shared; `kind` picks the body, so a gravedigger's shovel and a
   * bombardier's grenade no longer arrive as the same grey disc.
   */
  private[projectiles] def lobbed(kind: Int, r: Float, g: Float, b: Float, size: Float = 18f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 13) * 0.3
      computeAllDynamics(proj, r, g, b, phase)
      val bounceRaw = Math.sin(phase * 1.5).toFloat
      val bounce = Math.abs(bounceRaw) * 16f
      val bounceContact = Math.abs(bounceRaw)
      val spin = tick * 0.16f + proj.id * 1.3f
      val p = (0.75f + 0.25f * Math.sin(phase * _stPulseMult).toFloat) * dynAlpha
      val dr = _evoR; val dg = _evoG; val db = _evoB
      val sz = size * 1.15f * Math.min(dynScale, 1.4f)
      val bodyY = sy - bounce

      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy

      drawSpeedLines(sx, sy, ndx, ndy, dr, dg, db, 0.2f * p, sb, 4, sz * 1.4f)
      drawRibbonTrail(sx, sy, ndx, ndy, dr, dg, db, 0.28f * p, sb, tick, proj.id, 7,
        sz * 2.2f * dynTrail, sz * 0.36f, 1f)

      // Landing shadow that tightens as the object comes down, plus a target ring — the
      // arc's whole read is "this lands over there", so the ground marker carries it.
      val shadowScale = 1f + bounce * 0.04f
      val shadowAlpha = Math.max(0.12f, 0.48f - bounce * 0.015f)
      sb.fillOval(sx, sy + sz * 0.42f, sz * 1.2f * shadowScale, sz * 0.32f * shadowScale,
        0f, 0f, 0f, shadowAlpha, 16)
      val targetPulse = 0.5f + 0.5f * Math.sin(phase * 3).toFloat
      val targetAlpha = Math.max(0f, 0.35f - bounce * 0.015f) * targetPulse * p
      if (targetAlpha > 0.01f) {
        sb.strokeOval(sx, sy + sz * 0.42f, sz * 1.0f * shadowScale, sz * 0.27f * shadowScale,
          2f, dark(r), dark(g), dark(b), targetAlpha, 12)
        sb.strokeOval(sx, sy + sz * 0.42f, sz * 0.6f * shadowScale, sz * 0.16f * shadowScale,
          1.2f, r, g, b, targetAlpha * 0.6f, 10)
      }

      if (bounceContact < 0.15f) {
        val impactT = 1f - bounceContact / 0.15f
        drawSparkBurst(sx, sy + sz * 0.38f, dr, dg, db, impactT * 0.6f * p, sb, tick, proj.id, 6, sz * 1.1f)
      }

      // Afterimages back along the arc
      var ghost = 3; while (ghost >= 1) {
        val gt = ghost * 0.3f
        val gBounce = Math.abs(Math.sin(phase * 1.5 - gt * Math.PI)).toFloat * 14f
        val gx = sx - ndx * gt * 42f; val gy = sy - ndy * gt * 42f - gBounce
        sb.fillOval(gx, gy, sz * (0.55f - ghost * 0.08f), sz * (0.45f - ghost * 0.07f),
          dr, dg, db, 0.16f * (1f - (ghost - 1) * 0.3f) * p, 10)
        ghost -= 1
      }

      // Halo — kept modest so the body's own silhouette stays the thing you read
      sb.fillOvalSoft(sx, bodyY, sz * 1.7f * dynGlow, sz * 1.45f * dynGlow, dr, dg, db, 0.24f * p, 0f, 18)

      drawLobBody(sb, kind, sx, bodyY, sz, spin, phase, sx, sy, dr, dg, db, r, g, b, 0.97f * dynAlpha)

      // A couple of sparkles so the object still catches the eye in a busy fight
      var i = 0; while (i < 3) {
        val starPhase = ((phase * 0.45 + i * 0.34) % 1.0).toFloat
        val starAngle = phase * 1.2 + i * Math.PI * 2 / 3
        val starDist = sz * (0.85f + starPhase * 0.5f)
        drawSparkleStar(sx + Math.cos(starAngle).toFloat * starDist,
          bodyY + Math.sin(starAngle).toFloat * starDist * 0.55f,
          4.5f * (1f - starPhase * 0.5f), bright(r), bright(g), bright(b),
          0.4f * (1f - starPhase) * p, sb, phase * 2 + i)
        i += 1
      }

      drawChargeCrackle(sx, bodyY, sz, r, g, b, p, sb, phase, proj.chargeLevel)
      drawReturnGhosts(sx, bodyY, sz, dr, dg, db, p, sb, proj)
    }
}
