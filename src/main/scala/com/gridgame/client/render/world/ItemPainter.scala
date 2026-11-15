package com.gridgame.client.render
package world

import com.gridgame.common.Constants
import com.gridgame.common.model._

/**
 * Items on the ground, and the pictures of them the HUD's inventory shows ([[drawIcon]]).
 *
 * An item is a sprite, drawn in the characters' own hand (ItemSprites, scripts/generate_items.py)
 * and filtered as they are, so it is as smooth as the figure beside it at every quality tier. It
 * floats over the middle of its cell, rising and falling, each kind with a life of its own: the gem
 * turns, the heart beats, the star and the rest rock a little, and a glint comes and goes on each.
 * What lies on the ground under it — its shadow, and the light it sheds — is drawn with the ground
 * ([[drawGroundMarks]]), so a wall in front covers it; the item itself in the depth pass ([[draw]]),
 * among the walls and the players. It pops into being when it first appears and goes with a puff
 * when someone takes it ([[drawTaken]]); which is which is ItemTrack's to say.
 *
 * Everything that moves is placed by the frame's clock, each item in its own time (its id), never
 * by counting frames.
 */
private[render] final class ItemPainter(ctx: RenderContext) {
  import ctx._
  import ItemPainter._

  private val track = new ItemTrack
  // The last frame items were looked at: after a gap (a death's screen, a replay's jump) whatever
  // went missing meanwhile wasn't seen going
  private var lastFrameMs = 0L

  // What lies on the ground under each item on screen ([[collect]]): where, how much of its shadow
  // there is, how far it has popped into being, how bright its light is, and its kind
  private val _markX = new Array[Float](MAX_MARKS)
  private val _markY = new Array[Float](MAX_MARKS)
  private val _markShadow = new Array[Float](MAX_MARKS)
  private val _markPop = new Array[Float](MAX_MARKS)
  private val _markGlow = new Array[Float](MAX_MARKS)
  private val _markKind = new Array[Byte](MAX_MARKS)
  private var _marks = 0

  // The item being drawn, as [[pose]] holds it: how far over the ground its middle floats, how big it
  // is (1 at rest), how far it is turned, which frame of its art shows, how far it has popped into
  // being (0 to 1), and how bright its light is just now
  private var _lift = 0f
  private var _scale = 1f
  private var _angle = 0f
  private var _frame = 0
  private var _pop = 1f
  private var _pulse = 1f

  // Items taken a moment ago, each shown going: when, the cell it lay on, its kind
  private val _takenAt = new Array[Long](MAX_TAKEN)
  private val _takenX = new Array[Int](MAX_TAKEN)
  private val _takenY = new Array[Int](MAX_TAKEN)
  private val _takenKind = new Array[Byte](MAX_TAKEN)
  private var _takenNext = 0

  @inline private def onScreen(sx: Float, sy: Float): Boolean =
    sx > -60f && sx < canvasW + 60f && sy > -60f && sy < canvasH + 100f

  /**
   * How item `id` of kind `kind`, first seen at `since`, is held at `now`: floating, popping into
   * being over its first moments, and moving as its kind does.
   */
  private def pose(id: Int, kind: ItemType, since: Long, now: Long): Unit = {
    val age = now - since
    _pop = if (age >= POP_MS) 1f else if (age <= 0L) 0f else popEase(age.toFloat / POP_MS)
    _lift = HOVER + BOB * Math.sin(2 * Math.PI * cycle(now, id, BOB_MS)).toFloat
    _pulse = 0.82f + 0.18f * Math.sin(2 * Math.PI * cycle(now, id + 77, PULSE_MS)).toFloat
    _scale = _pop
    _angle = 0f
    _frame = 0
    kind match {
      case ItemType.Gem =>
        // turning: its eight frames are one eighth of a turn, so they loop
        _frame = (((now + offset(id, GEM_FRAME_MS * ItemSprites.GemFrames)) / GEM_FRAME_MS) % ItemSprites.GemFrames).toInt
      case ItemType.Heart =>
        // beating: two quick swells and a rest
        val u = cycle(now, id, BEAT_MS).toFloat
        val beat =
          if (u < 0.16f) 0.10f * Math.sin(Math.PI * u / 0.16f).toFloat
          else if (u >= 0.22f && u < 0.38f) 0.06f * Math.sin(Math.PI * (u - 0.22f) / 0.16f).toFloat
          else 0f
        _scale = _pop * (1f + beat)
      case ItemType.Star =>
        _angle = STAR_ROCK * Math.sin(2 * Math.PI * cycle(now, id, STAR_ROCK_MS)).toFloat
      case _ =>
        _angle = ROCK * Math.sin(2 * Math.PI * cycle(now, id, ROCK_MS)).toFloat
    }
  }

  /**
   * The frame's look at every item on the client's ground, on screen or not, which is what tells a
   * new one from one walked back to and a pickup from one walked away from (ItemTrack). Those on
   * screen are kept for [[drawGroundMarks]]; one that was on screen a frame ago and is nowhere now
   * has been taken, and goes with a puff ([[drawTaken]]). Nothing is drawn here.
   */
  def collect(): Unit = {
    val now = frameTimeMs
    // After a gap (a death's screen, a rejoin, a replay's jump) what went missing wasn't seen going
    val quiet = now - lastFrameMs > GAP_MS || now < lastFrameMs
    lastFrameMs = now
    track.beginFrame()
    _marks = 0
    val items = client.getItems
    if (!items.isEmpty) {
      val it = items.values().iterator()
      while (it.hasNext) {
        val item = it.next()
        val gx = worldToScreenX(item.getCellX, item.getCellY).toFloat
        val gy = worldToScreenY(item.getCellX, item.getCellY).toFloat
        val shown = onScreen(gx, gy)
        val since = track.see(item.id, item.getCellX, item.getCellY, item.itemType.id, now, shown)
        if (shown && _marks < MAX_MARKS) {
          pose(item.id, item.itemType, since, now)
          val m = _marks
          _markX(m) = gx; _markY(m) = gy
          // Its shadow is smaller and fainter the higher it floats, and grows with it as it appears
          _markShadow(m) = _pop * (1f - 0.12f * (_lift - HOVER) / BOB)
          _markPop(m) = _pop; _markGlow(m) = _pulse * _pop; _markKind(m) = item.itemType.id
          _marks += 1
        }
      }
    }
    track.endFrame()
    if (!quiet) {
      var i = 0
      while (i < track.gone) {
        if (track.goneWasOnScreen(i)) taken(track.goneX(i), track.goneY(i), track.goneKind(i), now)
        i += 1
      }
    }
  }

  /**
   * What lies on the ground under the items, drawn with the ground before the depth pass: each one's
   * shadow, and the light it sheds, so a wall in front of an item covers its part of them as it
   * covers a trap.
   */
  def drawGroundMarks(): Unit = {
    collect()
    if (_marks == 0) return
    beginShapes()
    var m = 0
    while (m < _marks) {
      val k = _markShadow(m)
      if (k > 0.02f) {
        shapeBatch.fillOvalSoft(_markX(m), _markY(m) + 0.5f, 16.5f * k, 6.8f * k, 0.02f, 0.01f, 0.05f, 0.24f * k, 0f, 14)
        shapeBatch.fillOvalSoft(_markX(m), _markY(m) + 0.5f, 11f * k, 4.6f * k, 0.01f, 0.005f, 0.03f, 0.5f * k, 0f, 12)
      }
      m += 1
    }
    // The light each sheds on the ground, all in one additive pass. None in daylight, as a shot
    // sheds none: on sand and snow it only washed the ground toward white, its own shadow with it
    if (!projectileLights) return
    shapeBatch.setAdditiveBlend(true)
    m = 0
    while (m < _marks) {
      val c = _markKind(m) * 3
      shapeBatch.fillOvalSoft(_markX(m), _markY(m), 25f * _markPop(m), 11.5f * _markPop(m),
        GLOW(c), GLOW(c + 1), GLOW(c + 2), GROUND_GLOW * _markGlow(m), 0f, 16)
      m += 1
    }
    shapeBatch.setAdditiveBlend(false)
  }

  /** How many items on screen the frame's look found, and how far each has popped into being. */
  def marks: Int = _marks
  def markPop(i: Int): Float = _markPop(i)

  /** How many taken items are being shown going. */
  def going: Int = {
    var n = 0
    var i = 0
    while (i < MAX_TAKEN) { if (_takenAt(i) != 0L) n += 1; i += 1 }
    n
  }

  /** An item in the depth pass: a soft light behind it, the item, and its glint. */
  def draw(item: Item): Unit = {
    val now = frameTimeMs
    val since = track.firstSeen(item.id)
    // On the ground only since this frame's marks were drawn: from the next frame
    if (since < 0L) return
    val kind = item.itemType
    pose(item.id, kind, since, now)
    if (_pop <= 0.01f) return
    val cx = worldToScreenX(item.getCellX, item.getCellY).toFloat
    val cy = worldToScreenY(item.getCellX, item.getCellY).toFloat - _lift
    val c = kind.id * 3
    val gr = GLOW(c); val gg = GLOW(c + 1); val gb = GLOW(c + 2)

    lightSystem.addLight(cx, cy, 85f, gr, gg, gb, 0.22f * _pulse * _pop)

    // The light round it: a soft disc behind it, blended as everything in the depth pass is, so the
    // wall in front of an item covers its glow too and the blend never changes for an item
    beginShapes()
    val aura = (if (projectileLights) AURA else AURA * DAYLIGHT) * _pulse * _pop
    shapeBatch.fillOvalSoft(cx, cy, AURA_R * _pop, AURA_R * _pop, gr, gg, gb, aura, 0f, 18)

    val region = ItemSprites.region(kind, _frame)
    if (region == null) {
      // No art to draw it with: a disc of its colour, so it can still be found
      shapeBatch.fillOvalSoft(cx, cy, 11f * _pop, 11f * _pop, gr, gg, gb, 0.95f, 0.5f, 16)
      return
    }
    beginSprites()
    val size = CELL * _scale
    if (_angle == 0f) spriteBatch.draw(region, cx - size * 0.5f, cy - size * 0.5f, size, size)
    else spriteBatch.drawRotated(region, cx - size * 0.5f, cy - size * 0.5f, size, size, _angle)

    // Its glint: a twinkle that comes and goes at one of three places on it, in turn
    val t = now + offset(item.id + 31, GLINT_PERIOD_MS)
    val phase = t % GLINT_PERIOD_MS
    if (phase < GLINT_MS && _pop >= 1f) {
      val u = phase.toFloat / GLINT_MS
      val s = Math.sin(Math.PI * u).toFloat
      val at = ((t / GLINT_PERIOD_MS + item.id) % 3).toInt * 2
      val gs = GLINT_SIZE * (0.35f + 0.65f * s)
      spriteBatch.drawRotated(ItemSprites.twinkle, cx + GLINT_AT(at) - gs * 0.5f, cy + GLINT_AT(at + 1) - gs * 0.5f,
        gs, gs, (u - 0.5f) * 1.1f, 1f, 1f, 1f, Math.min(1f, s * 1.8f))
    }
  }

  /** An item has been taken from cell (x, y): shown going ([[drawTaken]]), in a burst of sparks. */
  private def taken(x: Int, y: Int, kind: Byte, now: Long): Unit = {
    val i = _takenNext
    _takenNext = (_takenNext + 1) % MAX_TAKEN
    _takenAt(i) = now; _takenX(i) = x; _takenY(i) = y; _takenKind(i) = kind
    val sx = worldToScreenX(x, y).toFloat
    val sy = worldToScreenY(x, y).toFloat - HOVER
    val c = Math.max(0, Math.min(KINDS.length - 1, kind.toInt)) * 3
    // Sparks thrown out from round where it floated: little stars of light, soft at any size, where
    // they were hard six-sided dots that all began on one point. Light added to the dark maps; in
    // daylight, where added light is lost on sand and snow, the item's own colour laid over the ground
    val lit = projectileLights
    val w = if (lit) 0.5f else 0.12f
    val cr = GLOW(c) * (1f - w) + w; val cg = GLOW(c + 1) * (1f - w) + w; val cb = GLOW(c + 2) * (1f - w) + w
    var k = 0
    while (k < SPARKS) {
      val angle = (k + rng.nextFloat()) * (Math.PI.toFloat * 2f / SPARKS)
      val dx = Math.cos(angle).toFloat; val dy = Math.sin(angle).toFloat
      val from = 4f + rng.nextFloat() * 5f
      val speed = 34f + rng.nextFloat() * 40f
      combatParticles.emit(sx + dx * from, sy + dy * from, dx * speed, dy * speed - 12f,
        plife = 0.34f + rng.nextFloat() * 0.26f,
        pr = cr, pg = cg, pb = cb,
        palpha = if (lit) 0.85f else 0.95f,
        psize = 1.5f + rng.nextFloat() * 1.3f,
        pgravity = 40f, pdrag = 1.6f, additive = lit, pkind = ParticleSystem.KIND_SPARK)
      k += 1
    }
  }

  /** The items taken a moment ago, each swelling, rising and fading where it floated: over the
    * terrain, after the depth pass, for the fifth of a second it lasts. */
  def drawTaken(): Unit = {
    val now = frameTimeMs
    var i = 0
    while (i < MAX_TAKEN) {
      val at = _takenAt(i)
      if (at != 0L) {
        val age = now - at
        if (age < 0L || age >= TAKEN_MS) _takenAt(i) = 0L
        else {
          val kind = KINDS(Math.max(0, Math.min(KINDS.length - 1, _takenKind(i).toInt)))
          val region = if (kind == null) null else ItemSprites.region(kind, 0)
          if (region != null) {
            val u = age.toFloat / TAKEN_MS
            val size = CELL * (1f + 0.45f * u)
            val cx = worldToScreenX(_takenX(i), _takenY(i)).toFloat
            val cy = worldToScreenY(_takenX(i), _takenY(i)).toFloat - HOVER - 12f * u * (2f - u)
            beginSprites()
            spriteBatch.draw(region, cx - size * 0.5f, cy - size * 0.5f, size, size, 1f, 1f, 1f, (1f - u) * (1f - u))
          }
        }
      }
      i += 1
    }
  }

  /**
   * An item's picture for the HUD: its art centred on (cx, cy) in a square `size` units across, of
   * which the art fills about seven tenths; dimmed when we have none (`have`). It is a sprite, and
   * leaves the shape batch open, as it was when the HUD's own shapes were the picture.
   */
  def drawIcon(itemType: ItemType, cx: Float, cy: Float, size: Float, have: Boolean): Unit = {
    val region = ItemSprites.region(itemType, 0)
    if (region != null) {
      beginSprites()
      if (have) spriteBatch.draw(region, cx - size * 0.5f, cy - size * 0.5f, size, size)
      else spriteBatch.draw(region, cx - size * 0.5f, cy - size * 0.5f, size, size, 0.34f, 0.34f, 0.42f, 0.62f)
    }
    beginShapes()
  }

  /** A new life, or a rejoin: whatever is taken before the next frame wasn't seen going. */
  def reset(): Unit = {
    lastFrameMs = 0L
    java.util.Arrays.fill(_takenAt, 0L)
  }
}

private[render] object ItemPainter {
  /** How big a cell of the items' atlas is drawn on the ground, in world units: an item's art is about
    * seven tenths of its cell, so an item is about `ITEM_SIZE_PX` across. */
  val CELL: Float = Constants.ITEM_SIZE_PX * 4f / 3f
  /** How far over the ground the middle of an item floats, and how far it rises and falls about that. */
  val HOVER = 16f
  val BOB = 2f
  private val BOB_MS = 2600L
  /** How long an item takes to pop into being, and one taken to go. */
  val POP_MS = 420L
  val TAKEN_MS = 220L
  /** The sparks a taken item goes in. */
  val SPARKS = 10
  // A gap between frames longer than this and what went missing over it wasn't seen going
  private val GAP_MS = 500L
  // The gem's turn: this long a frame (a whole turn in 5.8 seconds)
  private val GEM_FRAME_MS = 90L
  // The heart's beat; how far the star rocks, and everything else, and how long each takes
  private val BEAT_MS = 1150L
  private val STAR_ROCK = 0.17f
  private val STAR_ROCK_MS = 2300L
  private val ROCK = 0.07f
  private val ROCK_MS = 3100L
  // Its light: how long a pulse of it takes, how strong it is on the ground and behind the item, how
  // far that reaches, and how much of the disc behind it is left in daylight (the ground gets none)
  private val PULSE_MS = 1900L
  private val GROUND_GLOW = 0.26f
  private val AURA = 0.30f
  private val AURA_R = 19f
  private val DAYLIGHT = 0.4f
  // The glint: this often, this long, this big, at these places from an item's middle in turn
  private val GLINT_PERIOD_MS = 1700L
  private val GLINT_MS = 520L
  private val GLINT_SIZE = 15f
  private val GLINT_AT: Array[Float] = Array(-8.5f, -9.5f, 10.5f, -4.5f, -9.5f, 6.5f)

  private val MAX_MARKS = 64
  private val MAX_TAKEN = 8

  /** The kinds by id, so one remembered by its id is its kind again without a search. */
  private val KINDS: Array[ItemType] = {
    val byId = new Array[ItemType](ItemType.all.map(_.id.toInt).max + 1)
    ItemType.all.foreach(t => byId(t.id) = t)
    byId
  }

  /** The colour of the light each kind sheds, three numbers a kind, by its id. */
  private val GLOW: Array[Float] = {
    val g = new Array[Float](KINDS.length * 3)
    def set(t: ItemType, r: Float, gr: Float, b: Float): Unit = { g(t.id * 3) = r; g(t.id * 3 + 1) = gr; g(t.id * 3 + 2) = b }
    set(ItemType.Gem, 0.30f, 0.90f, 1.00f)
    set(ItemType.Heart, 1.00f, 0.36f, 0.50f)
    set(ItemType.Star, 1.00f, 0.88f, 0.35f)
    set(ItemType.Shield, 0.74f, 0.42f, 1.00f)
    set(ItemType.Fence, 1.00f, 0.78f, 0.46f)
    g
  }

  /** Popping into being: from nothing, a little past its size, and back to it. */
  private def popEase(t: Float): Float = {
    val s = 2.2f
    val u = t - 1f
    1f + (s + 1f) * u * u * u + s * u * u
  }

  /** Where item `id` is in a cycle `period` long, so the items on the ground don't all keep time. */
  @inline private def offset(id: Int, period: Long): Long = ((id * 0x9E3779B1) >>> 1) % period

  /** How far through a cycle `period` long item `id` is at `now`, 0 to 1. */
  @inline private def cycle(now: Long, id: Int, period: Long): Double =
    ((now + offset(id, period)) % period).toDouble / period
}
