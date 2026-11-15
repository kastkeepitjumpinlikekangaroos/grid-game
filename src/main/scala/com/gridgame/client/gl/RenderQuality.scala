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
 * Two choices, both the player's: the quality (the tier: which effects, and at Medium and Low a
 * smaller world) and the resolution (the most lines the world is drawn at: the display's own, 1440,
 * 1080 or 720; a tier that draws fewer still does). Chosen in the menus' settings (saved, `choose`
 * and `chooseResolution`), with F7 in a match for the quality, or for a run with `--quality=` and
 * `--resolution=` (or `GRIDGAME_QUALITY`, `GRIDGAME_RESOLUTION`). Nothing changes either by itself: an
 * `auto` that stepped the quality down mid-match when frames stayed slow was taken out (2026-11-12),
 * the player deciding between more detail and more frames (gl/CLAUDE.md, *Graphics quality tiers*).
 *
 * With them, the camera's zoom (`zoomPercent`): how much closer than the widest view the match is
 * drawn, from 100% (the widest, the view every match had before) to 200%. It costs nothing to draw,
 * and is kept with them (`chooseZoom`, `--zoom=` or `GRIDGAME_ZOOM` for a run).
 */
object RenderQuality {
  val HIGH = 0
  val MEDIUM = 1
  val LOW = 2

  private val names = Array("high", "medium", "low")

  @volatile private var _tier: Int = HIGH

  def tier: Int = _tier
  def tierName: String = names(_tier)

  /** The resolutions the world can be drawn at, as the most lines it is drawn at: 0 for the
    * display's own. */
  val Resolutions: Seq[Int] = Seq(0, 1440, 1080, 720)

  /** The resolutions offered on a display `nativeLines` tall: its own, and the ones below it (the
    * settings' card, and the match's menu). */
  def resolutionsFor(nativeLines: Int): Seq[Int] = Resolutions.filter(r => r == 0 || r < nativeLines)

  @volatile private var _resolution: Int = 0

  /** The most lines the world is drawn at, 0 for the display's own. */
  def resolution: Int = _resolution
  def resolutionName: String = nameOfResolution(_resolution)
  def nameOfResolution(lines: Int): String = if (lines <= 0) "native" else s"${lines}p"

  /** The camera's zooms, in percent of the widest view: 100 is the widest, and the default. */
  val MinZoom = 100
  val MaxZoom = 200
  val ZoomStep = 10
  val Zooms: Seq[Int] = MinZoom to MaxZoom by ZoomStep

  @volatile private var _zoomPercent: Int = MinZoom

  /** How much closer than the widest view the match is drawn, in percent (100 to 200). */
  def zoomPercent: Int = _zoomPercent
  /** The same as a factor on the widest view's scale: 1 to 2. */
  def zoom: Double = _zoomPercent / 100.0
  def zoomName: String = s"${_zoomPercent}%"

  /** The nearest zoom there is to `percent`: a step of ZoomStep, from MinZoom to MaxZoom. */
  def zoomNearest(percent: Int): Int = {
    val clamped = Math.max(MinZoom, Math.min(MaxZoom, percent))
    MinZoom + Math.round((clamped - MinZoom).toFloat / ZoomStep) * ZoomStep
  }

  /** Where the player's choices are kept between runs: the user's preferences, unless a test has put
    * a store of its own in (it must never write the player's). */
  trait Store { def get(key: String): Option[String]; def put(key: String, value: String): Unit }
  @volatile var store: Store = new Store {
    private lazy val prefs = java.util.prefs.Preferences.userRoot().node("com/gridgame/client/graphics")
    def get(key: String): Option[String] = try Option(prefs.get(key, null)) catch { case _: Exception => None }
    def put(key: String, value: String): Unit =
      try { prefs.put(key, value); prefs.flush() } catch { case _: Exception => () }
  }

  private def tierNamed(name: String): Option[Int] = name.trim.toLowerCase match {
    case "low" => Some(LOW)
    case "medium" => Some(MEDIUM)
    case "high" | "auto" => Some(HIGH) // auto: what it began as, now that nothing steps down
    case _ => None
  }

  private def resolutionNamed(name: String): Option[Int] = name.trim.toLowerCase.stripSuffix("p") match {
    case "native" | "0" | "" => Some(0)
    case n if n.nonEmpty && n.forall(_.isDigit) && Resolutions.contains(n.toInt) => Some(n.toInt)
    case _ => None
  }

  private def zoomNamed(name: String): Option[Int] = name.trim.stripSuffix("%") match {
    case n if n.nonEmpty && n.length <= 4 && n.forall(_.isDigit) && Zooms.contains(n.toInt) => Some(n.toInt)
    case _ => None
  }

  /** The player's saved choices: the app reads them as it starts, before `configure`, whose flags win.
    * Dev tools don't, so a bench or the golden frames draw the same on every machine. */
  def loadSaved(): Unit = {
    store.get("quality").flatMap(tierNamed).foreach(_tier = _)
    store.get("resolution").flatMap(resolutionNamed).foreach(_resolution = _)
    store.get("zoom").flatMap(zoomNamed).foreach(_zoomPercent = _)
  }

  /** The quality, chosen by the player: from now on, and saved. */
  def choose(t: Int): Unit = {
    setTier(t)
    store.put("quality", tierName)
  }

  /** The resolution, chosen by the player: from the next frame on, and saved. */
  def chooseResolution(lines: Int): Unit = {
    _resolution = if (Resolutions.contains(lines)) lines else 0
    store.put("resolution", resolutionName)
    println(s"Resolution: $resolutionName")
  }

  /** The camera's zoom, chosen by the player (in the settings, or the match's menu): from the next
    * frame on, and saved. A zoom between two steps is taken as the nearest. */
  def chooseZoom(percent: Int): Unit = {
    _zoomPercent = zoomNearest(percent)
    store.put("zoom", zoomName)
    println(s"Camera zoom: $zoomName")
  }

  /** For a run, unsaved (a dev tool's or a test's), as the nearest zoom there is. */
  def setZoom(percent: Int): Unit = _zoomPercent = zoomNearest(percent)

  /**
   * Configure from command-line args and the environment. Returns the args with the
   * quality flag removed so the caller can pass the rest through.
   */
  def configure(args: Array[String]): Array[String] = {
    val quality = args.find(_.startsWith("--quality=")).map(_.substring("--quality=".length))
      .orElse(sys.env.get("GRIDGAME_QUALITY"))
    quality.foreach { q =>
      tierNamed(q) match {
        case Some(t) => _tier = t
        case None => System.err.println(s"Unknown --quality=$q (expected low, medium or high)")
      }
    }
    val resolution = args.find(_.startsWith("--resolution=")).map(_.substring("--resolution=".length))
      .orElse(sys.env.get("GRIDGAME_RESOLUTION"))
    resolution.foreach { r =>
      resolutionNamed(r) match {
        case Some(lines) => _resolution = lines
        case None => System.err.println(s"Unknown --resolution=$r (expected native, 1440, 1080 or 720)")
      }
    }
    val zoomFlag = args.find(_.startsWith("--zoom=")).map(_.substring("--zoom=".length))
      .orElse(sys.env.get("GRIDGAME_ZOOM"))
    zoomFlag.foreach { z =>
      zoomNamed(z) match {
        case Some(percent) => _zoomPercent = percent
        case None => System.err.println(s"Unknown --zoom=$z (expected $MinZoom to $MaxZoom in steps of $ZoomStep)")
      }
    }
    println(s"Graphics quality: $tierName, resolution: $resolutionName, zoom: $zoomName")
    args.filterNot(a => a.startsWith("--quality=") || a.startsWith("--resolution=") || a.startsWith("--zoom="))
  }

  /** Set the tier for this run, unsaved (a dev tool's, or `choose`'s). */
  def setTier(t: Int): Unit = {
    val clamped = Math.max(HIGH, Math.min(LOW, t))
    if (clamped != _tier) {
      _tier = clamped
      println(s"Graphics quality: $tierName")
    }
  }

  /** F7 in a match: High -> Medium -> Low -> High, chosen and saved as the menus' setting is. */
  def cycleTier(): Unit = choose(if (_tier >= LOW) HIGH else _tier + 1)

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
  def sceneScale(fbWidth: Int, fbHeight: Int, windowWidth: Int): Float = {
    val dpi = if (windowWidth > 0) fbWidth.toFloat / windowWidth else 1f
    val logical = if (dpi > 0.01f) 1f / dpi else 1f // scale that lands on 1 device px per logical px
    val byTier = (_tier: @scala.annotation.switch) match {
      case HIGH   => 1.0f
      case MEDIUM => Math.min(0.8f, logical)
      case _      => Math.min(0.55f, logical * 0.7f)
    }
    // And no more lines than the resolution chosen
    if (_resolution <= 0 || fbHeight <= _resolution) byTier
    else Math.min(byTier, _resolution.toFloat / fbHeight)
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
}
