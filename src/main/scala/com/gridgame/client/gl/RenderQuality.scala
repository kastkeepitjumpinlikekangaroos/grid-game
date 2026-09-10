package com.gridgame.client.gl

/**
 * Graphics quality tiers.
 *
 * The renderer's cost is almost entirely fill rate: at 3456x2160 the scene, the bloom
 * chain and the composite together take an order of magnitude more GPU time than the
 * same frame at 960x540, while the CPU side barely moves. So the knobs that matter on a
 * weak GPU are, in order: how many pixels the world is rendered at, how many full-screen
 * post passes run over it, and how much translucent decoration is layered on top.
 *
 * [[sceneScale]] shrinks only the world: the HUD and the final composite still run at the
 * display's own resolution, so text stays crisp while the expensive part gets cheaper
 * quadratically.
 *
 * Selected with `--quality=low|medium|high|auto`, `GRIDGAME_QUALITY`, or F7 in game. The
 * default is `auto`, which starts at High and steps down (never back up) when frames are
 * persistently slow, so an underpowered machine settles at a playable tier on its own
 * without the player needing to find a setting.
 */
object RenderQuality {
  val HIGH = 0
  val MEDIUM = 1
  val LOW = 2

  private val names = Array("high", "medium", "low")

  @volatile private var _tier: Int = HIGH
  @volatile private var _auto: Boolean = true

  def tier: Int = _tier
  def tierName: String = names(_tier)
  def isAuto: Boolean = _auto

  /**
   * Configure from command-line args and the environment. Returns the args with the
   * quality flag removed so the caller can pass the rest through.
   */
  def configure(args: Array[String]): Array[String] = {
    val flag = args.find(_.startsWith("--quality="))
    val value = flag.map(_.substring("--quality=".length))
      .orElse(sys.env.get("GRIDGAME_QUALITY"))
      .map(_.trim.toLowerCase)
    value match {
      case Some("low")    => _tier = LOW;    _auto = false
      case Some("medium") => _tier = MEDIUM; _auto = false
      case Some("high")   => _tier = HIGH;   _auto = false
      case Some("auto") | None => _tier = HIGH; _auto = true
      case Some(other) =>
        System.err.println(s"Unknown --quality=$other (expected low, medium, high or auto)")
        _tier = HIGH; _auto = true
    }
    if (!_auto) println(s"Graphics quality: $tierName")
    args.filterNot(_.startsWith("--quality="))
  }

  /** Force a tier at runtime (used by the in-game toggle and by the auto stepper). */
  def setTier(t: Int): Unit = {
    val clamped = Math.max(HIGH, Math.min(LOW, t))
    if (clamped != _tier) {
      _tier = clamped
      println(s"Graphics quality: $tierName")
    }
  }

  /** Cycle High -> Medium -> Low -> High, and stop auto-stepping once the player chooses. */
  def cycleTier(): Unit = {
    _auto = false
    setTier(if (_tier >= LOW) HIGH else _tier + 1)
  }

  // ── Per-tier settings ──────────────────────────────────────────────

  /**
   * Fraction of the framebuffer the world is rendered at, given the framebuffer and the
   * logical window width.
   *
   * The cap is expressed against both, because the worst case for this game is a cheap
   * machine driving a HiDPI screen: there the framebuffer is 2x the logical window, so the
   * world is being rendered at four times the pixels the art actually carries (tiles are
   * 40x56 magnified 1.6x). Medium first gives up that free 2x, which costs almost nothing
   * visually and is 4x cheaper; on a 1x display, where there is no free 2x to give up, it
   * falls back to a modest reduction instead.
   */
  def sceneScale(fbWidth: Int, windowWidth: Int): Float = {
    val dpi = if (windowWidth > 0) fbWidth.toFloat / windowWidth else 1f
    val logical = if (dpi > 0.01f) 1f / dpi else 1f // scale that lands on 1 device px per logical px
    (_tier: @scala.annotation.switch) match {
      case HIGH   => 1.0f
      case MEDIUM => Math.min(0.8f, logical)
      case _      => Math.min(0.55f, logical * 0.7f)
    }
  }

  /** Bloom extract + half-res blur. */
  def bloom: Boolean = _tier != LOW

  /** The extra quarter-res blur pair that widens the glow. */
  def wideBloom: Boolean = _tier == HIGH

  /** Four-tap unsharp mask in the composite — the composite's most expensive part. */
  def sharpen: Boolean = _tier == HIGH

  /** Per-pixel film grain. */
  def grain: Boolean = _tier != LOW

  /** Dynamic light map (an extra half-res target plus a composite fetch). */
  def lighting: Boolean = _tier != LOW

  /** Player reflections on water tiles. */
  def waterReflections: Boolean = _tier == HIGH

  /** How many animated tile overlays (lava vents, water glints, ...) may draw per frame. */
  def maxOverlayTiles: Int = (_tier: @scala.annotation.switch) match {
    case HIGH   => 512
    case MEDIUM => 256
    case _      => 0
  }

  /** Frames between background re-renders. The background is cached to a texture. */
  def bgCacheInterval: Int = (_tier: @scala.annotation.switch) match {
    case HIGH   => 3
    case MEDIUM => 5
    case _      => 10
  }

  /** Scales particle emission rates. */
  def particleScale: Float = (_tier: @scala.annotation.switch) match {
    case HIGH   => 1.0f
    case MEDIUM => 0.6f
    case _      => 0.25f
  }

  // ── Automatic step-down ────────────────────────────────────────────
  //
  // Only ever steps down, and only after a couple of seconds of consistently slow
  // frames, so a one-off hitch (a shader compile, a GC, an alt-tab) can't drag the
  // quality with it and there is nothing to oscillate.

  private val SlowFrameMs = 20.0     // below 50 fps
  private val SlowFramesToStep = 120 // ~2s of them
  private var slowFrames = 0

  def noteFrame(frameMs: Double): Unit = {
    if (!_auto || _tier >= LOW) return
    if (frameMs > SlowFrameMs) {
      slowFrames += 1
      if (slowFrames >= SlowFramesToStep) {
        slowFrames = 0
        setTier(_tier + 1)
      }
    } else if (slowFrames > 0) {
      slowFrames -= 1 // decay, so only a sustained problem accumulates
    }
  }
}
