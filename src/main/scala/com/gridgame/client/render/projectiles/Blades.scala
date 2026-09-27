package com.gridgame.client.render.projectiles

import com.gridgame.client.gl.ShapeBatch
import GLProjectileRenderers.Renderer
import ProjectileKit._
import Silhouettes._

/** Thrown weapons tumbling end over end (bladeSpinner: axes, katanas, knives, femurs, cursed blades,
  * playing cards) and the throwing star (shuriken). The spin is sold by a swept band and ghosts, not
  * by smearing the shape. */
private[render] object Blades {
  // ── Weapon kinds ──
  final val WPN_AXE = 0
  final val WPN_KATANA = 1
  final val WPN_KNIFE = 2
  final val WPN_SWORD = 3
  final val WPN_BONE = 4
  final val WPN_CURSED = 5
  final val WPN_CARD = 6
  final val WPN_BONE_AXE = 7
  private val AXE_PARTS = Array(
    part(Array(-1.05f,-0.11f, 0.55f,-0.11f, 0.55f,0.11f, -1.05f,0.11f), WOOD_R, WOOD_G, WOOD_B, 0.12f),
    part(Array(-1.26f,-0.17f, -0.96f,-0.17f, -0.96f,0.17f, -1.26f,0.17f), DKSTEEL_R, DKSTEEL_G, DKSTEEL_B, 0.35f),
    part(Array(0.28f,-0.14f, 0.44f,-0.86f, 0.80f,-1.02f, 1.22f,-0.60f, 1.24f,0.02f, 0.66f,0.18f),
      STEEL_R, STEEL_G, STEEL_B, 0.30f),
    part(Array(0.52f,-0.92f, 0.84f,-1.06f, 1.30f,-0.58f, 1.10f,-0.46f), 0.98f, 0.99f, 1f, 0.10f)
  )

  private val BONE_AXE_PARTS = Array(
    part(Array(-1.05f,-0.12f, 0.48f,-0.12f, 0.48f,0.12f, -1.05f,0.12f), BONE_R * 0.92f, BONE_G * 0.90f, BONE_B * 0.86f, 0.10f),
    // Broad jawbone bit
    part(Array(0.18f,-0.18f, 0.34f,-0.84f, 0.78f,-1.04f, 1.26f,-0.66f, 1.30f,-0.02f, 0.60f,0.24f),
      BONE_R, BONE_G, BONE_B, 0.22f),
    // Teeth standing proud of the cutting edge
    part(Array(0.44f,-0.86f, 0.58f,-1.20f, 0.74f,-0.96f), BONE_R, BONE_G, BONE_B, 0.15f),
    part(Array(0.80f,-1.02f, 1.02f,-1.24f, 1.06f,-0.86f), BONE_R, BONE_G, BONE_B, 0.15f),
    part(Array(1.16f,-0.72f, 1.46f,-0.62f, 1.28f,-0.30f), BONE_R, BONE_G, BONE_B, 0.15f),
    // Socket where haft meets bone
    part(Array(0.20f,-0.24f, 0.44f,-0.24f, 0.44f,0.24f, 0.20f,0.24f), 0.52f, 0.44f, 0.36f, 0.25f)
  )

  private val KATANA_PARTS = Array(
    part(Array(-1.10f,-0.10f, -0.34f,-0.10f, -0.34f,0.10f, -1.10f,0.10f), LEATHER_R, LEATHER_G, LEATHER_B, 0.15f),
    part(Array(-0.42f,-0.30f, -0.22f,-0.30f, -0.22f,0.30f, -0.42f,0.30f), GOLD_R, GOLD_G, GOLD_B, 0.20f),
    part(Array(-0.22f,-0.11f, 0.42f,-0.17f, 0.42f,0.05f, -0.22f,0.11f), STEEL_R, STEEL_G, STEEL_B, 0.22f),
    part(Array(0.42f,-0.17f, 0.98f,-0.16f, 1.30f,-0.03f, 0.98f,0.01f, 0.42f,0.05f), STEEL_R, STEEL_G, STEEL_B, 0.22f),
    // Hamon: the temper line that makes a katana read as a katana and not a steel bar.
    // A wedge along the spine rather than a run out and back — the doubled-back outline
    // was non-convex, and at two pixels wide what it fanned into was not a temper line.
    part(Array(-0.16f,-0.05f, 0.98f,-0.09f, 1.16f,-0.03f, -0.16f,-0.01f), 1f, 1f, 1f, 0.06f)
  )

  private val KNIFE_PARTS = Array(
    part(Array(-1.05f,-0.13f, -0.28f,-0.11f, -0.28f,0.13f, -1.05f,0.15f), DKWOOD_R, DKWOOD_G, DKWOOD_B, 0.12f),
    part(Array(-0.36f,-0.13f, -0.18f,-0.15f, -0.18f,0.15f, -0.36f,0.15f), GOLD_R, GOLD_G, GOLD_B, 0.20f),
    part(Array(-0.18f,-0.16f, 0.75f,-0.13f, 1.18f,0.02f, 0.30f,0.22f, -0.18f,0.16f), STEEL_R, STEEL_G, STEEL_B, 0.22f),
    part(Array(-0.10f,-0.10f, 0.72f,-0.08f, 1.02f,0.0f, 0.30f,0.06f, -0.10f,0.0f), 0.98f, 0.99f, 1f, 0.08f)
  )

  private val SWORD_PARTS = Array(
    part(Array(-1.05f,-0.10f, -0.30f,-0.10f, -0.30f,0.10f, -1.05f,0.10f), DKWOOD_R, DKWOOD_G, DKWOOD_B, 0.15f),
    part(Array(-1.22f,-0.18f, -1.00f,-0.18f, -1.00f,0.18f, -1.22f,0.18f), GOLD_R, GOLD_G, GOLD_B, 0.30f),
    part(Array(-0.40f,-0.54f, -0.18f,-0.54f, -0.18f,0.54f, -0.40f,0.54f), GOLD_R, GOLD_G, GOLD_B, 0.30f),
    part(Array(-0.18f,-0.17f, 0.85f,-0.15f, 1.28f,0f, 0.85f,0.15f, -0.18f,0.17f), 0.98f, 0.96f, 0.82f, 0.35f),
    part(Array(-0.14f,-0.05f, 0.88f,-0.04f, 1.10f,0f, 0.88f,0.04f, -0.14f,0.05f), 1f, 1f, 0.94f, 0.12f)
  )

  private val BONE_PARTS = Array(
    part(Array(-0.86f,-0.15f, 0.86f,-0.15f, 0.86f,0.15f, -0.86f,0.15f), BONE_R, BONE_G, BONE_B, 0.15f)
  )

  private val CURSED_PARTS = Array(
    part(Array(-1.10f,-0.11f, -0.42f,-0.11f, -0.42f,0.11f, -1.10f,0.11f), 0.13f, 0.09f, 0.12f, 0.20f),
    part(Array(-0.52f,-0.46f, -0.30f,-0.52f, -0.30f,0.52f, -0.52f,0.46f), 0.34f, 0.10f, 0.16f, 0.55f),
    part(Array(-0.30f,-0.24f, 0.34f,-0.30f, 0.34f,0.12f, -0.30f,0.24f), 0.22f, 0.08f, 0.13f, 0.45f),
    // The blade's back edge bows just outside the chord, so the span stays convex: bowed
    // the other way it was a notch, and fanning it cut the blade's own middle out.
    part(Array(0.34f,-0.30f, 0.80f,-0.18f, 1.30f,-0.02f, 0.36f,0.12f), 0.22f, 0.08f, 0.13f, 0.45f),
    // Glowing edge — this is the part that carries the character's colour
    part(Array(0.30f,-0.24f, 0.80f,-0.14f, 1.24f,-0.02f, 0.32f,-0.16f), 1f, 0.55f, 0.55f, 0.75f)
  )

  private val CARD_PARTS = Array(
    part(Array(-0.72f,-0.50f, 0.72f,-0.50f, 0.72f,0.50f, -0.72f,0.50f), 0.97f, 0.97f, 0.99f, 0.06f)
  )

  /** Four-bladed throwing star: one swept blade, stamped at exact quarter turns. A star is
   *  the one silhouette a polar radius per vertex really does describe — but it is also
   *  non-convex, so the old `spinner` handed `fillPolygon` a shape it fans into a blob with
   *  two of the notches bridged over. Four convex blades stay exact at every spin angle. */
  private val SHURIKEN_PARTS: Array[Part] = Array.tabulate(4) { q =>
    val blade = Array(0.16f,-0.34f, 0.74f,-0.30f, 1.30f,-0.02f, 0.60f,0.26f, 0.14f,0.30f)
    val pts = new Array[Float](blade.length)
    var i = 0
    while (i < blade.length / 2) {
      val x = blade(i * 2); val y = blade(i * 2 + 1)
      pts(i * 2)     = q match { case 0 => x; case 1 => -y; case 2 => -x; case _ => y }
      pts(i * 2 + 1) = q match { case 0 => y; case 1 => x;  case 2 => -y; case _ => -x }
      i += 1
    }
    part(pts, STEEL_R, STEEL_G, STEEL_B, 0.26f)
  }

  private def weaponParts(kind: Int): Array[Part] = kind match {
    case WPN_AXE      => AXE_PARTS
    case WPN_KATANA   => KATANA_PARTS
    case WPN_KNIFE    => KNIFE_PARTS
    case WPN_SWORD    => SWORD_PARTS
    case WPN_BONE     => BONE_PARTS
    case WPN_CURSED   => CURSED_PARTS
    case WPN_CARD     => CARD_PARTS
    case WPN_BONE_AXE => BONE_AXE_PARTS
    case _            => AXE_PARTS
  }

  /** Per-kind extras that a convex-polygon list cannot express (round bone knobs, card
   *  pips, the cursed blade's aura). Drawn after the parts. */
  private def weaponDetail(sb: ShapeBatch, kind: Int, cx: Float, cy: Float,
                           ca: Float, sa: Float, s: Float,
                           tr: Float, tg: Float, tb: Float, alpha: Float): Unit = kind match {
    case WPN_BONE =>
      var i = 0; while (i < 4) {
        val lx = if (i < 2) -0.94f else 0.94f
        val ly = if ((i & 1) == 0) -0.22f else 0.22f
        blitPoint(lx, ly, cx, cy, ca, sa, s)
        val kr = 0.27f * s
        sb.fillOval(_ptX, _ptY, kr + 1.2f, kr + 1.2f, 0.06f, 0.05f, 0.07f, 0.8f * alpha, 10)
        sb.fillOval(_ptX, _ptY, kr, kr, BONE_R, BONE_G, BONE_B, alpha, 10)
        sb.fillOval(_ptX - kr * 0.28f, _ptY - kr * 0.28f, kr * 0.42f, kr * 0.42f, 1f, 1f, 0.97f, 0.55f * alpha, 8)
        i += 1
      }
    case WPN_BONE_AXE =>
      blitPoint(-1.14f, 0f, cx, cy, ca, sa, s)
      val kr = 0.24f * s
      sb.fillOval(_ptX, _ptY, kr + 1.2f, kr + 1.2f, 0.06f, 0.05f, 0.07f, 0.8f * alpha, 10)
      sb.fillOval(_ptX, _ptY, kr, kr, BONE_R, BONE_G, BONE_B, alpha, 10)
    case WPN_CARD =>
      // Suit pip in the middle plus corner marks, so it reads as a card and not a tile
      blitPoint(0f, 0f, cx, cy, ca, sa, s)
      val px = _ptX; val py = _ptY
      sb.fillOval(px, py, s * 0.20f, s * 0.20f * ISO_Y + s * 0.05f, tr * 0.7f, tg * 0.25f, tb * 0.3f, 0.9f * alpha, 10)
      var i = 0; while (i < 2) {
        val sgn = if (i == 0) -1f else 1f
        blitPoint(sgn * 0.48f, sgn * 0.30f, cx, cy, ca, sa, s)
        sb.fillOval(_ptX, _ptY, s * 0.09f, s * 0.09f, tr * 0.7f, tg * 0.25f, tb * 0.3f, 0.85f * alpha, 8)
        i += 1
      }
    case WPN_CURSED =>
      blitPoint(0.80f, -0.10f, cx, cy, ca, sa, s)
      sb.fillOvalSoft(_ptX, _ptY, s * 0.55f, s * 0.42f, tr, tg * 0.4f, tb * 0.5f, 0.45f * alpha, 0f, 12)
    case _ => ()
  }

  /**
   * Four-bladed throwing star. Its plate lies in the ground plane, so it is stamped like a
   * tumbling weapon and sells its spin with a swept band across the blade tips rather than
   * with smeared copies of itself. Nothing is drawn on a radius out from the hub: the four
   * straight "swoosh" lines the old version fired off past the blades read as stray
   * geometry poking out of the star, not as motion.
   */
  private[projectiles] def shuriken(r: Float, g: Float, b: Float, size: Float = 26f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val spin = tick * 0.55 + proj.id * 2.1
      computeAllDynamics(proj, r, g, b, spin)
      val p = (0.88f + 0.12f * Math.sin(spin * 2 * _stPulseMult).toFloat) * dynAlpha
      val dr = _evoR; val dg = _evoG; val db = _evoB
      // Steel doesn't inflate with charge — a 2x throwing star reads as a bug.
      val ds = Math.min(dynScale, 1.3f)
      val s = size * 0.62f * ds
      val reach = s * 1.3f
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy
      val ca = Math.cos(spin).toFloat; val sa = Math.sin(spin).toFloat

      drawSpeedLines(sx, sy, ndx, ndy, dr, dg, db, 0.22f * p, sb, 4, reach * 1.2f)

      // Ground shadow — anchors the star to the arena instead of floating over it
      sb.fillOval(sx + 3f, sy + reach * 0.40f, reach * 0.55f, reach * 0.18f, 0f, 0f, 0f, 0.22f * p, 12)

      // Swept band across the blade tips: the spin read, drawn as geometry
      sb.fillArcBand(sx, sy, reach * 0.66f, reach * 0.66f * ISO_Y, reach * 1.02f, reach * 1.02f * ISO_Y,
        spin.toFloat - 1.9f, 1.9f, 10, bright(r), bright(g), bright(b), 0.02f * p, 0.34f * p)

      // Silhouette ghosts back along the flight path
      var ghost = 3; while (ghost >= 1) {
        val gA = 0.14f * (1f - (ghost - 1) * 0.3f) * p
        val gSpin = spin - ghost * 0.45
        drawPartsFlat(sb, SHURIKEN_PARTS, sx - ndx * ghost * 7f, sy - ndy * ghost * 7f,
          Math.cos(gSpin).toFloat, Math.sin(gSpin).toFloat, s * (1f - ghost * 0.05f),
          dr * 0.8f, dg * 0.8f, db * 0.8f, gA)
        ghost -= 1
      }

      // Halo so steel separates from busy ground without washing it out
      sb.fillOvalSoft(sx, sy, reach * 1.35f * dynGlow, reach * 1.35f * ISO_Y * dynGlow,
        dr, dg, db, 0.18f * p, 0f, 14)

      drawParts(sb, SHURIKEN_PARTS, sx, sy, ca, sa, s, dr, dg, db, 0.97f * dynAlpha,
        clampF(s * 0.13f, 1.2f, 2.6f))

      // Rimmed centre hole — what separates a throwing star from a pinwheel
      sb.fillOval(sx, sy, s * 0.30f, s * 0.30f * ISO_Y, 0.10f, 0.10f, 0.12f, 0.85f * p, 10)
      sb.strokeOval(sx, sy, s * 0.30f, s * 0.30f * ISO_Y, 1.6f, bright(r), bright(g), bright(b), 0.55f * p, 10)

      // Edge glint as a blade sweeps through the light direction
      val glint = Math.sin(spin * 2 + proj.id).toFloat
      if (glint > 0.74f) {
        blitPoint(1.18f, -0.10f, sx, sy, ca, sa, s)
        sb.fillStarFlare(_ptX, _ptY, s * 0.8f * (glint - 0.74f) / 0.26f, 2f,
          spin.toFloat * 0.5f, 0.45f, 1f, 1f, 0.96f, 0.7f * p)
      }

      drawChargeCrackle(sx, sy, reach, r, g, b, p, sb, spin, proj.chargeLevel)
      drawReturnGhosts(sx, sy, reach * 0.7f, dr, dg, db, p, sb, proj)
    }

  /**
   * Thrown weapon tumbling end over end. Rather than building a polar star — which
   * renders every melee weapon as the same lens — this stamps the weapon's own
   * silhouette, then sells the rotation with a swept arc and silhouette ghosts rather
   * than by smearing the shape itself.
   */
  private[projectiles] def bladeSpinner(kind: Int, r: Float, g: Float, b: Float, size: Float = 22f,
                           spinRate: Double = 0.30): Renderer =
    (proj, sx, sy, sb, tick) => {
      val spin = tick * spinRate + proj.id * 2.1
      computeAllDynamics(proj, r, g, b, spin)
      val p = (0.85f + 0.15f * Math.sin(spin * 2 * _stPulseMult).toFloat) * dynAlpha
      val dr = _evoR; val dg = _evoG; val db = _evoB
      // Physical weapons ignore most of the charge inflation: a 2x axe reads as a bug.
      val ds = Math.min(dynScale, 1.3f)
      val s = size * 0.80f * ds
      val reach = s * 1.3f
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy
      val ca = Math.cos(spin).toFloat; val sa = Math.sin(spin).toFloat
      // A card flips about its short axis instead of tumbling in plane.
      val sclX = if (kind == WPN_CARD) Math.max(0.42f, Math.abs(Math.cos(spin * 1.1).toFloat)) else 1f

      drawSpeedLines(sx, sy, ndx, ndy, dr, dg, db, 0.22f * p, sb, 4, reach * 1.1f)

      // Ground shadow — anchors the weapon to the arena instead of floating over it
      sb.fillOval(sx + 3f, sy + reach * 0.42f, reach * 0.62f, reach * 0.20f, 0f, 0f, 0f, 0.22f * p, 12)

      // Swept arc behind the leading edge: the tumble read, drawn as geometry rather than
      // as ghosts of the whole shape, which is what used to turn the silhouette to mush.
      val sweepStart = spin.toFloat - 2.5f
      sb.fillArcBand(sx, sy, reach * 0.62f, reach * 0.62f * ISO_Y, reach * 1.04f, reach * 1.04f * ISO_Y,
        sweepStart, 2.5f, 12, bright(r), bright(g), bright(b), 0.02f * p, 0.42f * p)

      // Silhouette ghosts a few frames back along the tumble AND the flight path
      var ghost = 3; while (ghost >= 1) {
        val gA = 0.16f * (1f - (ghost - 1) * 0.28f) * p
        val gSpin = spin - ghost * 0.32
        val gx = sx - ndx * ghost * 7f; val gy = sy - ndy * ghost * 7f
        drawPartsFlat(sb, weaponParts(kind), gx, gy,
          Math.cos(gSpin).toFloat, Math.sin(gSpin).toFloat, s * (1f - ghost * 0.04f),
          dr * 0.8f, dg * 0.8f, db * 0.8f, gA, 1f, sclX)
        ghost -= 1
      }

      // Halo so the weapon separates from busy ground without washing it out
      sb.fillOvalSoft(sx, sy, reach * 1.5f * dynGlow, reach * 1.5f * ISO_Y * dynGlow,
        dr, dg, db, 0.20f * p, 0f, 14)

      drawParts(sb, weaponParts(kind), sx, sy, ca, sa, s, dr, dg, db, 0.97f * dynAlpha,
        clampF(s * 0.13f, 1.4f, 3f), 1f, sclX)
      weaponDetail(sb, kind, sx, sy, ca, sa, s, dr, dg, db, 0.97f * dynAlpha)

      // Edge glint: a star flare that fires as the cutting edge sweeps through the
      // light direction, which is what makes steel read as steel.
      val glint = Math.sin(spin * 2 + proj.id).toFloat
      if (glint > 0.72f) {
        blitPoint(1.05f, -0.30f, sx, sy, ca, sa, s)
        sb.fillStarFlare(_ptX, _ptY, s * 0.85f * (glint - 0.72f) / 0.28f, 2.2f,
          spin.toFloat * 0.5f, 0.45f, 1f, 1f, 0.96f, 0.75f * p)
      }

      drawChargeCrackle(sx, sy, reach, r, g, b, p, sb, spin, proj.chargeLevel)
      drawReturnGhosts(sx, sy, reach * 0.7f, dr, dg, db, p, sb, proj)
    }
}
