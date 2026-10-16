package com.gridgame.client.render
package world

import com.gridgame.client.game.GameClient

/**
 * Where our aim lies, worked out each frame for AimArrowPainter (render/CLAUDE.md, *An aim moves
 * with us*): where it runs from and which way, and where a dash, a blink or a throw shown now lands.
 * Nothing in it draws, so a test can walk us along and watch it (AimGeometryTest).
 *
 * Our cell, which every cast starts from, runs up to a cell and a half ahead of where we are drawn
 * as we walk: a step puts us on the next cell at once, the sprite glides there over the step, and
 * the camera follows the sprite. An aim worked out from our cell and drawn from the sprite swung
 * with every step, 20 times a second at a walk, by 5 degrees with the pointer six cells off, and
 * pointed 13 degrees away from the pointer all the while; a landing hopped a cell at a time. So the
 * aim runs from where we are drawn toward the pointer as it is on this frame's screen, which as we
 * walk with the mouse still stays put on the screen, as our sprite does, and a landing is snapped to
 * its cell from there.
 */
private[render] final class AimGeometry(client: GameClient, camera: GameCamera) {
  import AimGeometry._

  /** Where the aim runs from, in the world: where we are drawn. */
  var ox = 0.0
  var oy = 0.0
  /** Which way it runs, a unit vector in the world. */
  var ux = 0.0
  var uy = 0.0
  /** Where the landing shown now is, in the world ([[landing]]). */
  var lx = 0.0
  var ly = 0.0

  // The landing shown: where it lands from where we are drawn with nothing in the way, and what the
  // terrain takes off the cast's own, each an offset in cells, eased; and which aim (an AttackSlot)
  // they are for, if any yet
  private var offX = 0.0; private var offY = 0.0
  private var cutX = 0.0; private var cutY = 0.0
  private var offFor = -1
  // How far each lane reaches as it is drawn (reach), and whether each has been drawn yet
  private val reaches = new Array[Float](MAX_LANES)
  private val reached = new Array[Boolean](MAX_LANES)

  /**
   * Where the aim runs from and which way: from where we are drawn toward the pointer when the aim is
   * the pointer's (`atPointer`: RenderContext.aimAtPointer), or else (a controller's stick, which aims
   * a way from our cell) the way from our cell to the aim point, as the cast takes it. How far the aim
   * point is along it; -1 with it on us, when the way is the one we face if `faceWhenOnUs` (as the
   * cast takes it then), else left as it was.
   */
  def aimFromHere(atPointer: Boolean, faceWhenOnUs: Boolean): Double = {
    val pos = client.getLocalPosition
    val gx = pos.getX.toDouble; val gy = pos.getY.toDouble
    ox = if (camera.visualX.isNaN) gx else camera.visualX
    oy = if (camera.visualY.isNaN) gy else camera.visualY
    val dx = client.getMouseWorldX - (if (atPointer) ox else gx)
    val dy = client.getMouseWorldY - (if (atPointer) oy else gy)
    val d = Math.sqrt(dx * dx + dy * dy)
    if (d > 0.01) { ux = dx / d; uy = dy / d; d }
    else {
      if (faceWhenOnUs) { val a = client.getAimAngle; ux = Math.cos(a); uy = Math.sin(a) }
      -1.0
    }
  }

  /**
   * Where a dash, blink or throw (`kind`) shown now lands, into (lx, ly); false if it goes nowhere.
   * The cast lands on a cell it works out from our cell, (castX, castY). It is shown where it would
   * land from where we are drawn, snapped to a cell as the cast snaps it but from there (openLanding:
   * what the cast gives with nothing in the way), and moved by whatever the terrain, the map's edge
   * or a barrier takes off the cast's own landing (that landing, less what it would be with nothing
   * in the way). On open ground it moves exactly as we do, and standing it is the cast's own cell.
   * Both are eased, `dt` seconds on: the snapped landing over LAND_EASE_MS as the pointer sweeps it
   * from cell to cell, and what the terrain takes off over LAND_CUT_MS (LAND_JUMP_MS for a change of
   * LAND_JUMP cells or more), since worked out from our cell it shifts with our steps along a wall.
   * Call after [[aimFromHere]] with the same `atPointer`.
   */
  def landing(kind: Int, maxDistance: Int, castX: Int, castY: Int, slot: Int, atPointer: Boolean,
              dt: Float): Boolean = {
    val pos = client.getLocalPosition
    val gx = pos.getX; val gy = pos.getY
    // Where the cast aims from our cell; a blink with the aim point on us goes the way we face
    var cx = client.getMouseWorldX; var cy = client.getMouseWorldY
    if (kind == BLINK && (cx - gx) * (cx - gx) + (cy - gy) * (cy - gy) <= 1e-4) { cx = gx + ux; cy = gy + uy }
    val open = openLanding(kind, gx, gy, cx, cy, maxDistance)
    val takenX = if (open == NOWHERE) 0 else castX - gx - unpackX(open)
    val takenY = if (open == NOWHERE) 0 else castY - gy - unpackY(open)
    // ...and the same aim from where we are drawn, toward the pointer (from the nearest cell, so the
    // arithmetic is the cast's): the aim point as far from it as the pointer is from us
    val nx = Math.round(ox).toInt; val ny = Math.round(oy).toInt
    var ax = nx + (if (atPointer) client.getMouseWorldX - ox else cx - gx)
    var ay = ny + (if (atPointer) client.getMouseWorldY - oy else cy - gy)
    if (kind == BLINK && (ax - nx) * (ax - nx) + (ay - ny) * (ay - ny) <= 1e-4) { ax = nx + ux; ay = ny + uy }
    val shown = openLanding(kind, nx, ny, ax, ay, maxDistance)
    if (shown == NOWHERE) { offFor = -1; return false }
    val sx = unpackX(shown).toDouble; val sy = unpackY(shown).toDouble
    if (offFor != slot) {
      offX = sx; offY = sy; cutX = takenX; cutY = takenY
      offFor = slot
    } else {
      val k = ease(dt, LAND_EASE_MS)
      offX += (sx - offX) * k; offY += (sy - offY) * k
      val ex = takenX - cutX; val ey = takenY - cutY
      val kc = ease(dt, if (ex * ex + ey * ey >= LAND_JUMP * LAND_JUMP) LAND_JUMP_MS else LAND_CUT_MS)
      cutX += ex * kc; cutY += ey * kc
    }
    lx = ox + offX + cutX; ly = oy + offY + cutY
    true
  }

  /** Nothing is landing: the next landing starts where it is, not eased in from the last. */
  def forgetLanding(): Unit = offFor = -1

  /**
   * How far lane `k` of the aim is drawn (the shot's or the charge's 0, a fan's spokes 1 on), `to`
   * cells out being where what flies along it stops now, `dt` seconds on: that far at once if it is
   * nearer than the lane was drawn, so it never runs on through what now stops it, else eased out to
   * it over GROW_MS. Swept or walked past a row of trees, a lane grows through each gap instead of
   * flicking out to its full length and back at every trunk.
   */
  def reach(k: Int, to: Float, dt: Float): Float = {
    if (k >= MAX_LANES) return to
    if (!reached(k) || to <= reaches(k)) reaches(k) = to
    else reaches(k) += (to - reaches(k)) * ease(dt, GROW_MS)
    reached(k) = true
    reaches(k)
  }

  /** Nothing is aimed: the next aim starts afresh, its lanes at their full reach and its landing where
    * it is. */
  def forget(): Unit = {
    offFor = -1
    java.util.Arrays.fill(reached, false)
  }
}

private[render] object AimGeometry {
  // A landing eases from cell to cell over LAND_EASE_MS as the pointer sweeps it, and onto what the
  // terrain takes off it over LAND_CUT_MS, or LAND_JUMP_MS when that changes by LAND_JUMP cells or more
  private val LAND_EASE_MS = 35f
  private val LAND_CUT_MS = 60f
  private val LAND_JUMP_MS = 35f
  private val LAND_JUMP = 1.5
  // Lanes drawn at once (a shot's, a fan's spokes) whose reach eases out (reach), and over what
  private val MAX_LANES = 8
  private val GROW_MS = 60f

  /** How far to ease toward a target `dt` seconds on, over a time constant of `ms`. */
  def ease(dt: Float, ms: Float): Float = 1f - Math.exp(-dt * 1000f / ms).toFloat

  // The kinds of landing (openLanding), and its answer when a dash goes nowhere
  val DASH = 0
  val BLINK = 1
  val THROW = 2
  val NOWHERE: Long = Long.MinValue

  /**
   * Where a dash, a blink or a throw (`kind`) cast from cell (fromX, fromY) at the aim point (aimX,
   * aimY) lands with nothing in the way: as an offset in whole cells from that cell, packed (unpackX,
   * unpackY); NOWHERE for a dash aimed within half a cell, which goes nowhere. The casts' own rules on
   * open ground, in the same arithmetic, so from our cell it is the cast's own answer to the last bit
   * of rounding: GameClient.dashLanding (whole cells along the line, as many as its length allows, each
   * the cell its point falls in), Teleport.blinkTarget (its whole range the way it is aimed, to the
   * nearest cell) and TrapPlacement.target (the nearest cell to where it is aimed, at most its range).
   * AimGeometryTest holds them to the casts. An offset in whole cells is the same from any cell, so
   * from the cell nearest where we are drawn it says where the cast would put us from there.
   */
  def openLanding(kind: Int, fromX: Int, fromY: Int, aimX: Double, aimY: Double, maxDistance: Int): Long =
    if (kind == DASH) {
      val dx = (aimX - fromX).toFloat; val dy = (aimY - fromY).toFloat
      val dist = Math.sqrt(dx * dx + dy * dy).toFloat
      val clamped = Math.min(dist, maxDistance.toFloat)
      if (clamped <= 0.5f) NOWHERE
      else {
        val steps = clamped.toInt
        if (steps < 1) pack(0, 0)
        else pack((fromX.toFloat + dx / dist * steps).toInt - fromX, (fromY.toFloat + dy / dist * steps).toInt - fromY)
      }
    } else if (kind == BLINK) {
      val dx = (aimX - fromX).toFloat; val dy = (aimY - fromY).toFloat
      val len = Math.sqrt(dx * dx + dy * dy).toFloat
      if (len <= 0.01f) pack(0, 0)
      else pack(Math.round(fromX + (dx / len).toDouble * maxDistance).toInt - fromX,
        Math.round(fromY + (dy / len).toDouble * maxDistance).toInt - fromY)
    } else {
      val dx = aimX - fromX; val dy = aimY - fromY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len < 0.01) pack(0, 0)
      else {
        val reach = Math.min(len, maxDistance.toDouble)
        pack(Math.round(fromX + dx / len * reach).toInt - fromX, Math.round(fromY + dy / len * reach).toInt - fromY)
      }
    }

  @inline private def pack(x: Int, y: Int): Long = (x.toLong << 32) | (y.toLong & 0xFFFFFFFFL)
  @inline def unpackX(p: Long): Int = (p >> 32).toInt
  @inline def unpackY(p: Long): Int = p.toInt
}
