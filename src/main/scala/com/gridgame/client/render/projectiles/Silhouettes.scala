package com.gridgame.client.render.projectiles

import com.gridgame.client.gl.ShapeBatch
import ProjectileKit._

/**
 * Recognisable objects — an axe, a spear, a playing card — authored once in a local frame (+x along
 * the object, +y across it) as convex Parts, each a material colour with a weight toward the
 * projectile's own colour, and stamped two ways: tumbling (drawParts: turned by the spin, squashed
 * into the ground plane by ISO_Y) or flying point-first (drawPartsDir: turned onto the travel vector,
 * only its breadth narrowed by FLAT_Y). A Part must be convex: fillPolygon fans from its first vertex.
 */
private[render] object Silhouettes {
  private[projectiles] val ISO_Y = 0.6f

  /** One convex piece of a silhouette. `tint` blends the part toward the projectile's
   *  registered colour, so steel stays steel while the energy parts take the character's
   *  palette. */
  private[projectiles] final class Part(val xs: Array[Float], val ys: Array[Float],
                           val r: Float, val g: Float, val b: Float, val tint: Float) {
    val n: Int = xs.length
  }

  private[projectiles] def part(pts: Array[Float], r: Float, g: Float, b: Float, tint: Float): Part = {
    val n = pts.length / 2
    val xs = new Array[Float](n); val ys = new Array[Float](n)
    var i = 0; while (i < n) { xs(i) = pts(i * 2); ys(i) = pts(i * 2 + 1); i += 1 }
    new Part(xs, ys, r, g, b, tint)
  }

  // Scratch for the transformed polygon. Sized for the largest part plus headroom.
  private[projectiles] val _shpXs = new Array[Float](16)
  private[projectiles] val _shpYs = new Array[Float](16)

  /** Rotate/squash/scale a part's local points into _shpXs/_shpYs. */
  @inline private[projectiles] def blitPart(p: Part, cx: Float, cy: Float, ca: Float, sa: Float, s: Float,
                               flipY: Float, sclX: Float): Unit = {
    var i = 0; while (i < p.n) {
      val lx = p.xs(i) * sclX; val ly = p.ys(i) * flipY
      _shpXs(i) = cx + (lx * ca - ly * sa) * s
      _shpYs(i) = cy + (lx * sa + ly * ca) * s * ISO_Y
      i += 1
    }
  }

  /** Transform a single local point into (_ptX, _ptY). */
  private[projectiles] var _ptX = 0f; private[projectiles] var _ptY = 0f

  @inline private[projectiles] def blitPoint(lx: Float, ly: Float, cx: Float, cy: Float,
                                ca: Float, sa: Float, s: Float): Unit = {
    _ptX = cx + (lx * ca - ly * sa) * s
    _ptY = cy + (lx * sa + ly * ca) * s * ISO_Y
  }

  /** Draw a whole silhouette: dark contour behind, then each part's body. The contour is
   *  stroked before the fill so exactly half of its width survives — the same read as the
   *  sprite sheets' `add_contour`, which is what keeps these legible over pale sand. */
  private[projectiles] def drawParts(sb: ShapeBatch, parts: Array[Part], cx: Float, cy: Float,
                        ca: Float, sa: Float, s: Float, tr: Float, tg: Float, tb: Float,
                        alpha: Float, outlineW: Float, flipY: Float = 1f, sclX: Float = 1f): Unit = {
    if (alpha <= 0.01f) return
    var i = 0; while (i < parts.length) {
      val pt = parts(i)
      blitPart(pt, cx, cy, ca, sa, s, flipY, sclX)
      if (outlineW > 0f) sb.strokePolygon(_shpXs, _shpYs, pt.n, outlineW, 0.06f, 0.05f, 0.07f, 0.85f * alpha)
      sb.fillPolygon(_shpXs, _shpYs, pt.n,
        mix(pt.r, tr, pt.tint), mix(pt.g, tg, pt.tint), mix(pt.b, tb, pt.tint), alpha)
      i += 1
    }
  }

  /** Flat silhouette pass used for motion-blur ghosts — one colour, no contour. */
  private[projectiles] def drawPartsFlat(sb: ShapeBatch, parts: Array[Part], cx: Float, cy: Float,
                            ca: Float, sa: Float, s: Float,
                            r: Float, g: Float, b: Float, alpha: Float, flipY: Float = 1f,
                            sclX: Float = 1f): Unit = {
    if (alpha <= 0.01f) return
    var i = 0; while (i < parts.length) {
      val pt = parts(i)
      blitPart(pt, cx, cy, ca, sa, s, flipY, sclX)
      sb.fillPolygon(_shpXs, _shpYs, pt.n, r, g, b, alpha)
      i += 1
    }
  }

  // ── Material palette shared by the silhouettes ──
  private[projectiles] val WOOD_R = 0.58f; private[projectiles] val WOOD_G = 0.42f; private[projectiles] val WOOD_B = 0.25f
  private[projectiles] val DKWOOD_R = 0.30f; private[projectiles] val DKWOOD_G = 0.20f; private[projectiles] val DKWOOD_B = 0.12f
  private[projectiles] val STEEL_R = 0.84f; private[projectiles] val STEEL_G = 0.87f; private[projectiles] val STEEL_B = 0.93f
  private[projectiles] val DKSTEEL_R = 0.46f; private[projectiles] val DKSTEEL_G = 0.50f; private[projectiles] val DKSTEEL_B = 0.58f
  private[projectiles] val BONE_R = 0.93f; private[projectiles] val BONE_G = 0.91f; private[projectiles] val BONE_B = 0.83f
  private[projectiles] val GOLD_R = 0.86f; private[projectiles] val GOLD_G = 0.70f; private[projectiles] val GOLD_B = 0.30f
  private[projectiles] val LEATHER_R = 0.24f; private[projectiles] val LEATHER_G = 0.19f; private[projectiles] val LEATHER_B = 0.20f

  // ── Direction-aligned silhouettes (spears, arrows, darts, thorns) ──
  //
  // A flying shaft must NOT go through blitPart's extra ISO_Y squash: the travel vector
  // handed to the renderer is already in screen space, and squashing it again shortens a
  // spear thrown "north" to two thirds of one thrown "east". These stamp with a pure
  // screen rotation and narrow only the cross-axis, so the object still reads as lying
  // flat without changing length with heading.
  private val FLAT_Y = 0.80f

  @inline private[projectiles] def blitPartDir(p: Part, cx: Float, cy: Float, fx: Float, fy: Float,
                                  s: Float, flipY: Float): Unit = {
    val px = -fy * FLAT_Y; val py = fx * FLAT_Y
    var i = 0; while (i < p.n) {
      val lx = p.xs(i) * s; val ly = p.ys(i) * flipY * s
      _shpXs(i) = cx + lx * fx + ly * px
      _shpYs(i) = cy + lx * fy + ly * py
      i += 1
    }
  }

  private[projectiles] def drawPartsDir(sb: ShapeBatch, parts: Array[Part], cx: Float, cy: Float,
                           fx: Float, fy: Float, s: Float, tr: Float, tg: Float, tb: Float,
                           alpha: Float, outlineW: Float): Unit = {
    if (alpha <= 0.01f) return
    var i = 0; while (i < parts.length) {
      val pt = parts(i)
      blitPartDir(pt, cx, cy, fx, fy, s, 1f)
      if (outlineW > 0f) sb.strokePolygon(_shpXs, _shpYs, pt.n, outlineW, 0.06f, 0.05f, 0.07f, 0.85f * alpha)
      sb.fillPolygon(_shpXs, _shpYs, pt.n,
        mix(pt.r, tr, pt.tint), mix(pt.g, tg, pt.tint), mix(pt.b, tb, pt.tint), alpha)
      i += 1
    }
  }

  /** As [[drawPartsDir]], but every part's contour before any part's body, so a shape split into
   *  spans for convexity — a curved blade — is inked as one piece. Contour then body part by part
   *  draws each span's ink across the one before it, and the blade comes out segmented. */
  private[projectiles] def drawPartsDirUnion(sb: ShapeBatch, parts: Array[Part], cx: Float, cy: Float,
                                fx: Float, fy: Float, s: Float, tr: Float, tg: Float, tb: Float,
                                alpha: Float, outlineW: Float): Unit = {
    if (alpha <= 0.01f) return
    var i = 0
    while (i < parts.length) {
      val pt = parts(i)
      blitPartDir(pt, cx, cy, fx, fy, s, 1f)
      sb.strokePolygon(_shpXs, _shpYs, pt.n, outlineW, 0.06f, 0.05f, 0.07f, 0.85f * alpha)
      i += 1
    }
    i = 0
    while (i < parts.length) {
      val pt = parts(i)
      blitPartDir(pt, cx, cy, fx, fy, s, 1f)
      sb.fillPolygon(_shpXs, _shpYs, pt.n,
        mix(pt.r, tr, pt.tint), mix(pt.g, tg, pt.tint), mix(pt.b, tb, pt.tint), alpha)
      i += 1
    }
  }

  private[projectiles] def drawPartsDirFlat(sb: ShapeBatch, parts: Array[Part], cx: Float, cy: Float,
                               fx: Float, fy: Float, s: Float,
                               r: Float, g: Float, b: Float, alpha: Float): Unit = {
    if (alpha <= 0.01f) return
    var i = 0; while (i < parts.length) {
      val pt = parts(i)
      blitPartDir(pt, cx, cy, fx, fy, s, 1f)
      sb.fillPolygon(_shpXs, _shpYs, pt.n, r, g, b, alpha)
      i += 1
    }
  }

  @inline private[projectiles] def dirPoint(lx: Float, ly: Float, cx: Float, cy: Float,
                               fx: Float, fy: Float, s: Float): Unit = {
    _ptX = cx + lx * s * fx + ly * s * FLAT_Y * -fy
    _ptY = cy + lx * s * fy + ly * s * FLAT_Y * fx
  }
}
