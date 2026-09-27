package com.gridgame.client.render.projectiles

import GLProjectileRenderers.Renderer
import ProjectileKit._
import Silhouettes._
import Tethers._

/** Thrown restraints (chainProj): a grappling hook or meat hook on a rope back to the thrower's hand,
  * a tumbling manacle trailing links, a loop of links round a padlock. */
private[render] object Chains {
  final val CHN_ROPE = 0
  final val CHN_SHACKLE = 1
  final val CHN_LOCK = 2
  final val CHN_HOOK = 3   // the rope, with a single meat hook on it rather than a grapple

  /**
   * Thrown restraint, drawn as the object that hits rather than a line out ahead of it.
   *
   *  - CHN_ROPE: a grappling hook at the hitbox with the rope paying out behind it — only
   *    as far as the throw has travelled, capped, and dissolving at the far end.
   *  - CHN_SHACKLE: a heavy manacle tumbling end over end, trailing a few swinging links.
   *  - CHN_LOCK: a loop of links spinning around a padlock — lockdown, read at a glance.
   *
   * The old tether ran out AHEAD of the hitbox to a hook and began at a round "cleat", so
   * it read as a line with a ball on one end and a hook on the other, arriving early.
   */
  private[projectiles] def chainProj(kind: Int, r: Float, g: Float, b: Float, worldLen: Float = 6f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 23) * 0.3
      computeAllDynamics(proj, r, g, b, phase)
      val p = (0.88f + 0.12f * Math.sin(phase * _stPulseMult).toFloat) * dynAlpha
      val dr = _evoR; val dg = _evoG; val db = _evoB
      // The manacle and the padlock loop are compact objects; they carry more area than the hook
      val ds = Math.min(dynScale, 1.3f) * (if (kind == CHN_ROPE || kind == CHN_HOOK) 1f else 1.25f)
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy
      val px = -ndy; val py = ndx
      val heading = Math.atan2(ndy, ndx)
      val hiR = mix(bright(r), 1f, 0.3f); val hiG = mix(bright(g), 1f, 0.3f); val hiB = mix(bright(b), 1f, 0.3f)

      kind match {
        case CHN_ROPE | CHN_HOOK =>
          // The rope, paid out all the way back to the hand that threw it (see TETHERS) — two
          // strands laid round each other, hanging a little in the middle. It used to stop 2.4
          // world units back, measured by getDistanceTraveled, which the client never advances:
          // in a real match no rope was ever drawn at all, only a hook flying on its own.
          if (layTether(proj, sx, sy, phase, 7f * ds, 5f * ds, 7f * ds)) {
            shapeTether(3.4f * ds, 4f * ds, 1f, p, 0.1f)
            var strand = 0
            while (strand < 2) {
              val ph = strand * Math.PI
              var i = 0
              while (i < _tN) {
                val twist = Math.sin(i * (Math.PI / 2) + ph + phase * 0.8).toFloat * 1.5f * ds
                _pvX(i) = _tX(i) + _tNX(i) * twist; _pvY(i) = _tY(i) + _tNY(i) * twist
                _pvW(i) = _tW(i) + 2.2f; _pvA(i) = _tA(i) * 0.75f
                i += 1
              }
              sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, _tN, 0.10f, 0.07f, 0.05f)
              i = 0
              while (i < _tN) { _pvW(i) = _tW(i); _pvA(i) = _tA(i) * 0.97f; i += 1 }
              val k = if (strand == 0) 1f else 0.8f
              sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, _tN, dr * k, dg * k, db * k)
              strand += 1
            }
          }
          if (kind == CHN_HOOK) {
            // A single hook, point back toward the one hauling on it
            sb.fillOvalSoft(sx, sy, 17f * ds * dynGlow, 14f * ds * dynGlow, dr, dg, db, 0.24f * p, 0f, 12)
            drawPartsDirUnion(sb, DEATH_HOOK_PARTS, sx, sy, ndx, ndy, 15f * ds, STEEL_R, STEEL_G, STEEL_B, 0.97f * p, 2.4f)
          } else {
            sb.fillOvalSoft(sx, sy, 17f * ds * dynGlow, 14f * ds * dynGlow, dr, dg, db, 0.24f * p, 0f, 12)
            val shx = sx - Math.cos(heading).toFloat * 12f * ds
            val shy = sy - Math.sin(heading).toFloat * 12f * ds * ISO_Y
            sb.strokeLine(shx, shy, sx, sy, 6.4f * ds, 0.06f, 0.06f, 0.08f, 0.85f * p)
            sb.strokeLine(shx, shy, sx, sy, 4.2f * ds, DKSTEEL_R, DKSTEEL_G, DKSTEEL_B, 0.95f * p)
            var k = 0
            while (k < 3) {
              val a = heading + (k - 1) * 0.95
              val mx = sx + Math.cos(a).toFloat * 10f * ds
              val my = sy + Math.sin(a).toFloat * 10f * ds * ISO_Y
              val ex = mx + Math.cos(a - 1.15).toFloat * 9f * ds
              val ey = my + Math.sin(a - 1.15).toFloat * 9f * ds * ISO_Y
              sb.strokeLine(sx, sy, mx, my, 5.8f * ds, 0.06f, 0.06f, 0.08f, 0.85f * p)
              sb.strokeLine(mx, my, ex, ey, 5f * ds, 0.06f, 0.06f, 0.08f, 0.85f * p)
              sb.strokeLine(sx, sy, mx, my, 3.6f * ds, STEEL_R, STEEL_G, STEEL_B, 0.97f * p)
              sb.strokeLine(mx, my, ex, ey, 2.8f * ds, STEEL_R, STEEL_G, STEEL_B, 0.97f * p)
              k += 1
            }
            sb.fillOval(sx, sy, 4f * ds, 3.4f * ds, STEEL_R, STEEL_G, STEEL_B, 0.97f * p, 8)
          }

        case CHN_SHACKLE =>
          // A few links swinging behind, fading
          var i = 1
          while (i <= 3) {
            val t = i / 3.5f
            val swing = Math.sin(phase * 1.8 + i * 0.9).toFloat * 5f * ds * t
            val lx = sx - ndx * (9f + i * 8f) * ds + px * swing
            val ly = sy - ndy * (9f + i * 8f) * ds + py * swing
            val flat = (i & 1) == 1
            val la = if (flat) 5.4f * ds else 2.4f * ds
            val lb = if (flat) 3f * ds else 4.4f * ds
            val a = 1f - t * 0.7f
            strokeRotEllipse(sb, lx, ly, ndx, ndy, la, lb, 3.6f, 0.06f, 0.06f, 0.08f, 0.8f * a * p, 10)
            strokeRotEllipse(sb, lx, ly, ndx, ndy, la, lb, 2.2f, dr, dg, db, 0.95f * a * p, 10)
            i += 1
          }
          // The manacle, tumbling: its ring foreshortens as it turns over
          val spin = phase * 1.5
          val ma = spin * 0.35
          val mx = Math.cos(ma).toFloat; val my = Math.sin(ma).toFloat * ISO_Y
          val ml = Math.max(0.001f, Math.sqrt(mx * mx + my * my).toFloat)
          val ux = mx / ml; val uy = my / ml
          val R = 11f * ds
          val minor = R * (0.3f + 0.7f * Math.abs(Math.cos(spin).toFloat))
          sb.fillOvalSoft(sx, sy, R * 2f * dynGlow, R * 1.6f * dynGlow, dr, dg, db, 0.24f * p, 0f, 12)
          strokeRotEllipse(sb, sx, sy, ux, uy, R, minor, 7.5f * ds, 0.06f, 0.06f, 0.08f, 0.88f * p, 18)
          strokeRotEllipse(sb, sx, sy, ux, uy, R, minor, 4.8f * ds, dr, dg, db, 0.97f * p, 18)
          strokeRotEllipse(sb, sx - 0.6f, sy - 0.8f, ux, uy, R * 0.98f, minor * 0.96f, 1.4f * ds, hiR, hiG, hiB, 0.55f * p, 18)
          // Hinge at one end of the ring, lock plate at the other
          val hx = sx + ux * R; val hy = sy + uy * R
          sb.fillOval(hx, hy, 4.2f * ds, 4.2f * ds, 0.06f, 0.06f, 0.08f, 0.88f * p, 8)
          sb.fillOval(hx, hy, 3f * ds, 3f * ds, DKSTEEL_R, DKSTEEL_G, DKSTEEL_B, 0.97f * p, 8)
          val lx = sx - ux * R; val ly = sy - uy * R
          sb.fillRoundedRect(lx - 5.2f * ds, ly - 4.2f * ds, 10.4f * ds, 8.4f * ds, 2f, 0.06f, 0.06f, 0.08f, 0.88f * p)
          sb.fillRoundedRect(lx - 4f * ds, ly - 3f * ds, 8f * ds, 6f * ds, 1.5f, dr, dg, db, 0.97f * p)
          sb.fillOval(lx, ly - 0.6f * ds, 1.3f * ds, 1.3f * ds, 0.05f, 0.05f, 0.06f, 0.95f * p, 6)
          sb.strokeLine(lx, ly, lx, ly + 2f * ds, 1.2f * ds, 0.05f, 0.05f, 0.06f, 0.95f * p)

        case _ =>
          // CHN_LOCK: a loop of links spinning around a padlock
          val spin = phase * 0.9
          val R = 15f * ds
          sb.fillOvalSoft(sx, sy, R * 1.8f * dynGlow, R * 1.4f * dynGlow, dr, dg, db, 0.26f * p, 0f, 14)
          var pass = 0
          while (pass < 2) {
            var i = 0
            while (i < 8) {
              val a = spin + i * Math.PI / 4
              val lx = sx + Math.cos(a).toFloat * R; val ly = sy + Math.sin(a).toFloat * R * ISO_Y
              val tx = -Math.sin(a).toFloat; val ty = Math.cos(a).toFloat * ISO_Y
              val tl = Math.max(0.001f, Math.sqrt(tx * tx + ty * ty).toFloat)
              val flat = (i & 1) == 0
              val la = if (flat) 6f * ds else 3.6f * ds
              val lb = if (flat) 3.2f * ds else 2.2f * ds
              if (pass == 0) strokeRotEllipse(sb, lx, ly, tx / tl, ty / tl, la, lb, 3.8f, 0.06f, 0.06f, 0.08f, 0.85f * p, 10)
              else {
                strokeRotEllipse(sb, lx, ly, tx / tl, ty / tl, la, lb, 2.4f, dr, dg, db, 0.97f * p, 10)
                strokeRotEllipse(sb, lx - 0.5f, ly - 0.6f, tx / tl, ty / tl, la * 0.9f, lb * 0.8f, 1f, hiR, hiG, hiB, 0.5f * p, 8)
              }
              i += 1
            }
            pass += 1
          }
          // Padlock riding the front of the loop
          val lx = sx + Math.cos(heading).toFloat * R * 0.2f
          val ly = sy + Math.sin(heading).toFloat * R * 0.2f * ISO_Y
          sb.strokeArc(lx, ly - 4f * ds, 4.4f * ds, 5f * ds, 3.14f, 3.14f, 4f * ds, 0.06f, 0.06f, 0.08f, 0.88f * p, 8)
          sb.strokeArc(lx, ly - 4f * ds, 4.4f * ds, 5f * ds, 3.14f, 3.14f, 2.4f * ds, STEEL_R, STEEL_G, STEEL_B, 0.97f * p, 8)
          sb.fillRoundedRect(lx - 7f * ds, ly - 4.6f * ds, 14f * ds, 11.5f * ds, 2.5f, 0.06f, 0.06f, 0.08f, 0.9f * p)
          sb.fillRoundedRect(lx - 5.8f * ds, ly - 3.4f * ds, 11.6f * ds, 9.1f * ds, 2f, GOLD_R, GOLD_G, GOLD_B, 0.98f * p)
          sb.fillRect(lx - 5.8f * ds, ly - 3.4f * ds, 11.6f * ds, 2.2f * ds, 1f, 0.92f, 0.6f, 0.45f * p)
          sb.fillOval(lx, ly + 0.2f * ds, 1.6f * ds, 1.6f * ds, 0.05f, 0.04f, 0.03f, 0.95f * p, 6)
          sb.strokeLine(lx, ly + 0.2f * ds, lx, ly + 3f * ds, 1.4f * ds, 0.05f, 0.04f, 0.03f, 0.95f * p)
      }
      drawChargeCrackle(sx, sy, 14f * ds, r, g, b, p, sb, phase, proj.chargeLevel)
    }
}
