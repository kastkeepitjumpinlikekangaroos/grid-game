package com.gridgame.client.render
package world

import com.gridgame.common.model.{ProjectileDef, WorldData}

import java.util.UUID

/**
 * The blood a hit draws (render/CLAUDE.md, *A hit draws blood*), worked out for BloodPainter, which
 * draws it: a spurt of a few drops thrown from the middle of the struck player's figure, away from
 * the projectile, each arcing down to the ground and leaving a splat where it lands on open ground,
 * which dries and fades within a few seconds.
 *
 * Each drop's whole flight is worked out as its spurt is made ([[spurt]]), from a seed of the hit's
 * own, and where it is at any moment is a function of the time since the hit ([[flight]]): a frame at
 * any rate draws it the same, the golden frames' stopped clock draws one moment of it, and nothing
 * steps it from frame to frame. Nothing here draws, and nothing allocates once it is made, so a test
 * can follow a spurt (BloodTest).
 *
 * Positions are the world's (cells), heights are units on the screen above the ground, as a
 * player's figure is measured (Constants.PLAYER_MIDDLE_ABOVE_FEET).
 */
private[render] final class Blood {
  import Blood._

  // The spurts, a ring: each new one takes the oldest's place. A hit time of NoHit is no spurt.
  private val hitTimes = Array.fill(MaxSpurts)(NoHit)
  private val bodyXs = new Array[Float](MaxSpurts)
  private val bodyYs = new Array[Float](MaxSpurts)
  private val dropCounts = new Array[Int](MaxSpurts)
  private var next = 0
  private var newest = NoHit

  // Each drop, at spurt * MaxDrops + its number: where it leaves from, how fast it goes out (cells a
  // second) and up (units a second), from how high, how big it is, when and where it lands, and
  // whether there is ground there to splat on
  private val startXs = new Array[Float](MaxSpurts * MaxDrops)
  private val startYs = new Array[Float](MaxSpurts * MaxDrops)
  private val vxs = new Array[Float](MaxSpurts * MaxDrops)
  private val vys = new Array[Float](MaxSpurts * MaxDrops)
  private val vzs = new Array[Float](MaxSpurts * MaxDrops)
  private val z0s = new Array[Float](MaxSpurts * MaxDrops)
  private val radii = new Array[Float](MaxSpurts * MaxDrops)
  private val landTs = new Array[Float](MaxSpurts * MaxDrops)
  private val landXs = new Array[Float](MaxSpurts * MaxDrops)
  private val landYs = new Array[Float](MaxSpurts * MaxDrops)
  private val onGround = new Array[Boolean](MaxSpurts * MaxDrops)
  // A splat's outline: SplatPoints points round where its drop landed, on the screen and relative to
  // it, worked out once (the projection is linear, so they are the same wherever the camera is)
  private val outlineXs = new Array[Float](MaxSpurts * MaxDrops * SplatPoints)
  private val outlineYs = new Array[Float](MaxSpurts * MaxDrops * SplatPoints)
  // A fleck thrown on past a big drop's splat: where, on the screen and relative to the splat, and how
  // big (0: none)
  private val fleckXs = new Array[Float](MaxSpurts * MaxDrops)
  private val fleckYs = new Array[Float](MaxSpurts * MaxDrops)
  private val fleckRs = new Array[Float](MaxSpurts * MaxDrops)

  private var rand = 0L

  /** Where [[flight]] put a drop: in the world, and how high above the ground. */
  var x = 0f
  var y = 0f
  var z = 0f
  /** How fast [[velocity]] found a drop going across the screen and down it, in units a second. */
  var vsx = 0f
  var vsy = 0f

  /**
   * Blood from a player struck at `hitTime` by a projectile of type `pType`, their figure standing at
   * (bodyX, bodyY) and lifted `lift` units off the ground (a knock-up), the projectile at (hitX, hitY)
   * going (headingX, headingY) as it struck: [[dropsFor]] drops, none for a type that deals no damage.
   * They fly away from the projectile: from where it went off for a blast (a slam, an explosion, a
   * splash, which strike everyone round them), the way it was going for a shot, which may strike from
   * just past the middle of a figure it reaches; the other of the two if that one is nothing, and all
   * round if both are (a slam on top of them). `world` says where there is ground for a splat.
   */
  def spurt(hitTime: Long, bodyX: Double, bodyY: Double, lift: Double, hitX: Float, hitY: Float,
            headingX: Float, headingY: Float, pType: Byte, seed: Long, world: WorldData): Unit = {
    val drops = dropsFor(pType)
    if (drops == 0) return
    // Which way
    val radX = bodyX - hitX; val radY = bodyY - hitY
    val radLen = Math.sqrt(radX * radX + radY * radY)
    val headLen = Math.sqrt(headingX.toDouble * headingX + headingY.toDouble * headingY)
    var ux = 0.0; var uy = 0.0
    if (isBlast(pType)) {
      if (radLen >= OnTopCells) { ux = radX / radLen; uy = radY / radLen }
      else if (headLen > 1e-4) { ux = headingX / headLen; uy = headingY / headLen }
    } else {
      if (headLen > 1e-4) { ux = headingX / headLen; uy = headingY / headLen }
      else if (radLen >= OnTopCells) { ux = radX / radLen; uy = radY / radLen }
    }
    val aimed = ux != 0.0 || uy != 0.0
    val base = Math.atan2(uy, ux)

    val s = next
    next = (next + 1) % MaxSpurts
    hitTimes(s) = hitTime
    if (newest == NoHit || hitTime > newest) newest = hitTime
    bodyXs(s) = bodyX.toFloat; bodyYs(s) = bodyY.toFloat
    dropCounts(s) = drops
    rand = seed

    // A quarter of them (or none of a fountain) back out of the wound, the way the projectile came
    val back = if (aimed) drops / 4 else 0
    var d = 0
    while (d < drops) {
      val i = s * MaxDrops + d
      val forward = d < drops - back
      val a =
        if (!aimed) next01() * TwoPi
        else if (forward) base + (next01() + next01() - 1.0) * ForwardSpread
        else base + Math.PI + (next01() + next01() - 1.0) * BackSpread
      val speed =
        if (!aimed) lerp(2.0, 5.5, next01())
        else if (forward) lerp(4.0, 11.0, next01())
        else lerp(2.0, 4.5, next01())
      val ca = Math.cos(a); val sa = Math.sin(a)
      vxs(i) = (ca * speed).toFloat; vys(i) = (sa * speed).toFloat
      vzs(i) = (if (!aimed) lerp(70, 140, next01()) else if (forward) lerp(10, 70, next01()) else lerp(30, 90, next01())).toFloat
      val z0 = lerp(12, 21, next01()) + lift
      z0s(i) = z0.toFloat
      startXs(i) = (bodyX + (next01() - 0.5) * 0.15).toFloat
      startYs(i) = (bodyY + (next01() - 0.5) * 0.15).toFloat
      // The first is a big one, the rest smaller, most of them small
      val r = if (d == 0) lerp(1.3, 1.6, next01()) else { val k = next01(); lerp(0.55, 1.2, k * k) }
      radii(i) = r.toFloat

      // Down when its height runs out: z0 + vz t - g t^2 / 2 = 0
      val vz = vzs(i).toDouble
      val tl = (vz + Math.sqrt(vz * vz + 2.0 * Gravity * z0)) / Gravity
      landTs(i) = tl.toFloat
      val out = (1.0 - Math.exp(-Drag * tl)) / Drag
      val lx = startXs(i) + vxs(i) * out
      val ly = startYs(i) + vys(i) * out
      landXs(i) = lx.toFloat; landYs(i) = ly.toFloat
      onGround(i) = world != null && world.isTileWalkable(Math.floor(lx + 0.5).toInt, Math.floor(ly + 0.5).toInt)

      // Its splat: an ellipse on the ground, stretched the way it was going by how fast it still was,
      // its edge ragged
      val landSpeed = speed * Math.exp(-Drag * tl)
      val across = (1.2 + r * 1.3) * lerp(0.85, 1.2, next01()) / UnitsPerCell
      val along = across * (1.0 + Math.min(1.3, landSpeed * 0.3))
      val turn = next01() * TwoPi
      var k = 0
      while (k < SplatPoints) {
        val th = turn + k * TwoPi / SplatPoints
        val rag = lerp(0.8, 1.15, next01())
        val ox = (Math.cos(th) * along * ca - Math.sin(th) * across * sa) * rag
        val oy = (Math.cos(th) * along * sa + Math.sin(th) * across * ca) * rag
        outlineXs(i * SplatPoints + k) = ((ox - oy) * HalfW).toFloat
        outlineYs(i * SplatPoints + k) = ((ox + oy) * HalfH).toFloat
        k += 1
      }
      if (r > 1.0) {
        val past = along * lerp(1.6, 2.2, next01())
        val side = across * (next01() - 0.5)
        val ox = ca * past - sa * side; val oy = sa * past + ca * side
        fleckXs(i) = ((ox - oy) * HalfW).toFloat
        fleckYs(i) = ((ox + oy) * HalfH).toFloat
        fleckRs(i) = (0.5 + r * lerp(0.25, 0.35, next01())).toFloat
      } else fleckRs(i) = 0f
      d += 1
    }
  }

  /** Where drop `i` is `t` seconds after its hit (no later than [[landsAfter]]), in [[x]], [[y]], [[z]]. */
  def flight(i: Int, t: Float): Unit = {
    val out = (1f - Math.exp(-Drag * t).toFloat) / Drag
    x = startXs(i) + vxs(i) * out
    y = startYs(i) + vys(i) * out
    z = Math.max(0f, z0s(i) + vzs(i) * t - 0.5f * Gravity * t * t)
  }

  /** How fast drop `i` is going across the screen and down it `t` seconds after its hit, in [[vsx]]
    * and [[vsy]]: its way out in the world, slowing, and the fall under it. */
  def velocity(i: Int, t: Float): Unit = {
    val slow = Math.exp(-Drag * t).toFloat
    val wx = vxs(i) * slow; val wy = vys(i) * slow
    vsx = (wx - wy) * HalfW.toFloat
    vsy = (wx + wy) * HalfH.toFloat - (vzs(i) - Gravity * t)
  }

  /** Nothing drawn from before: a new life, or a new match. */
  def clear(): Unit = {
    java.util.Arrays.fill(hitTimes, NoHit)
    newest = NoHit
    next = 0
  }

  /** The newest spurt's hit time, [[NoHit]] if there has been none. */
  def newestHit: Long = newest

  /** The spurts oldest first: the k-th oldest's slot. */
  def oldestFirst(k: Int): Int = (next + k) % MaxSpurts

  def hitTime(s: Int): Long = hitTimes(s)
  def bodyX(s: Int): Float = bodyXs(s)
  def bodyY(s: Int): Float = bodyYs(s)
  def drops(s: Int): Int = dropCounts(s)

  /** Drop `d` of spurt `s`. */
  @inline def drop(s: Int, d: Int): Int = s * MaxDrops + d
  def radius(i: Int): Float = radii(i)
  /** How many seconds after its hit it reaches the ground. */
  def landsAfter(i: Int): Float = landTs(i)
  def landX(i: Int): Float = landXs(i)
  def landY(i: Int): Float = landYs(i)
  /** Whether it leaves a splat: it landed on ground anyone can walk on, not in water or a wall. */
  def splats(i: Int): Boolean = onGround(i)
  /** Point k of its splat's outline, on the screen, relative to where it landed. */
  def outlineX(i: Int, k: Int): Float = outlineXs(i * SplatPoints + k)
  def outlineY(i: Int, k: Int): Float = outlineYs(i * SplatPoints + k)
  def fleckX(i: Int): Float = fleckXs(i)
  def fleckY(i: Int): Float = fleckYs(i)
  def fleckR(i: Int): Float = fleckRs(i)

  @inline private def lerp(a: Double, b: Double, k: Double): Double = a + (b - a) * k

  /** The spurt's own random numbers (SplitMix64), in [0, 1). */
  private def next01(): Double = {
    rand += 0x9E3779B97F4A7C15L
    var r = rand
    r = (r ^ (r >>> 30)) * 0xBF58476D1CE4E5B9L
    r = (r ^ (r >>> 27)) * 0x94D049BB133111EBL
    r = r ^ (r >>> 31)
    (r >>> 11).toDouble / (1L << 53).toDouble
  }
}

private[render] object Blood {
  /** No spurt in a slot, and no hit yet. */
  final val NoHit = Long.MinValue

  /** How many spurts are drawn at once: a crowded fight's hits for longer than a spurt lasts. */
  val MaxSpurts = 96
  /** The most drops in a spurt (the hardest blows). */
  val MaxDrops = 12
  /** How many points a splat's outline has. */
  val SplatPoints = 8

  /** How fast a drop falls: units of height a second, each second. Snappier than the world's own
    * (a figure is 48 units, so a metre is about 28 of them). */
  val Gravity = 460f
  /** How quickly the air slows a drop on its way out: by e every 1/Drag of a second. */
  val Drag = 6f
  /** How long a spurt is drawn: its splats begin to fade at FadeFromMs after the hit and are gone at
    * LifeMs. */
  val FadeFromMs = 2300L
  val LifeMs = 3500L
  /** Nearer than this to where the projectile struck, the body is on top of it, and the blood can't
    * spray away from it. */
  val OnTopCells = 0.3

  private val ForwardSpread = 0.7
  private val BackSpread = 0.8
  private val TwoPi = 2 * Math.PI
  // A cell on the screen: half its width and half its height, and about how many units across it is
  private val HalfW = 20.0
  private val HalfH = 10.0
  private val UnitsPerCell = 26.0

  /** How many drops a hit of this type spurts: none for one that deals no damage, more the harder it
    * can strike (its hit, its splash or its explosion, whichever is hardest), 5 to MaxDrops. */
  def dropsFor(pType: Byte): Int = kinds(pType & 0xFF) & 0xFF

  /** Does this type strike as a blast: everyone round where it went off (a slam, an explosion, a
    * splash), rather than whoever its shot reached? */
  def isBlast(pType: Byte): Boolean = (kinds(pType & 0xFF) & 0x100) != 0

  /** The seed of the spurt for the `n`-th hit heard, on `target` at `time`: the same hit spurts the
    * same way, two hits never quite alike. */
  def seedOf(target: UUID, time: Long, n: Long): Long =
    (target.getMostSignificantBits * 31 + target.getLeastSignificantBits) * 0x9E3779B97F4A7C15L ^
      (time * 0xC2B2AE3D27D4EB4FL) ^ (n * 0x165667B19E3779F9L)

  private lazy val kinds: Array[Int] = Array.tabulate(256) { t =>
    val d = ProjectileDef.get(t.toByte)
    val hardest = Seq(d.damage, d.effectiveDamage(100), d.distanceDamageScaling.map(_.maxDamage).getOrElse(0),
      d.aoeOnHit.map(_.damage).getOrElse(0), d.aoeOnMaxRange.map(_.damage).getOrElse(0),
      d.explosionConfig.map(e => Math.max(e.centerDamage, e.edgeDamage)).getOrElse(0)).max
    val drops = if (hardest <= 0) 0 else Math.min(MaxDrops, 5 + hardest / 7)
    val blast = d.speedMultiplier == 0f || d.isExplosive || d.aoeOnHit.isDefined || d.aoeOnMaxRange.isDefined
    drops | (if (blast) 0x100 else 0)
  }
}
