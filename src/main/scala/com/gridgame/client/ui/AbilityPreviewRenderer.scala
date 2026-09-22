package com.gridgame.client.ui

import com.gridgame.common.model._
import javafx.scene.canvas.GraphicsContext
import javafx.scene.paint.Color

/**
 * Renders animated ability preview canvases for the character selection screen.
 * All rendering is in simple 2D screen-space (no isometric transforms).
 */
object AbilityPreviewRenderer {

  // (primary, secondary, core) colors per projectile type
  private val colorRegistry: Map[Byte, (Color, Color, Color)] = Map(
    ProjectileType.NORMAL       -> (Color.web("#7799cc"), Color.web("#aaccff"), Color.WHITE),
    ProjectileType.TENTACLE     -> (Color.web("#4a8060"), Color.web("#60cc80"), Color.web("#90ffb0")),
    ProjectileType.ICE_BEAM     -> (Color.web("#5090cc"), Color.web("#80ccff"), Color.web("#d0eeff")),
    ProjectileType.AXE          -> (Color.web("#aa7744"), Color.web("#cc9955"), Color.web("#eebb77")),
    ProjectileType.ROPE         -> (Color.web("#8a6633"), Color.web("#bb9955"), Color.web("#ddbb77")),
    ProjectileType.SPEAR        -> (Color.web("#8a7030"), Color.web("#ccaa44"), Color.web("#ffdd66")),
    ProjectileType.SOUL_BOLT    -> (Color.web("#6644aa"), Color.web("#9966ee"), Color.web("#cc99ff")),
    ProjectileType.HAUNT        -> (Color.web("#554488"), Color.web("#8866cc"), Color.web("#bb99ff")),
    ProjectileType.ARCANE_BOLT  -> (Color.web("#6644cc"), Color.web("#9977ff"), Color.web("#ccaaff")),
    ProjectileType.FIREBALL     -> (Color.web("#cc4400"), Color.web("#ff6600"), Color.web("#ffcc33")),
    ProjectileType.SPLASH       -> (Color.web("#2266aa"), Color.web("#4499dd"), Color.web("#77ccff")),
    ProjectileType.TIDAL_WAVE   -> (Color.web("#1155aa"), Color.web("#3388dd"), Color.web("#66bbff")),
    ProjectileType.GEYSER       -> (Color.web("#1166bb"), Color.web("#2299ee"), Color.web("#55ccff")),
    ProjectileType.BULLET       -> (Color.web("#bbaa44"), Color.web("#ddcc66"), Color.web("#ffee88")),
    ProjectileType.GRENADE      -> (Color.web("#556633"), Color.web("#778844"), Color.web("#99aa66")),
    ProjectileType.ROCKET       -> (Color.web("#884422"), Color.web("#cc6633"), Color.web("#ff9944")),
    ProjectileType.TALON        -> (Color.web("#886633"), Color.web("#bb8844"), Color.web("#ddaa66")),
    ProjectileType.GUST         -> (Color.web("#88aacc"), Color.web("#aaccee"), Color.web("#ccddff")),
    ProjectileType.SHURIKEN     -> (Color.web("#443366"), Color.web("#665588"), Color.web("#9988bb")),
    ProjectileType.POISON_DART  -> (Color.web("#447733"), Color.web("#66aa44"), Color.web("#88dd66")),
    ProjectileType.CHAIN_BOLT   -> (Color.web("#667788"), Color.web("#88aacc"), Color.web("#aaccee")),
    ProjectileType.LOCKDOWN_CHAIN -> (Color.web("#556677"), Color.web("#7799aa"), Color.web("#99bbcc")),
    ProjectileType.SNARE_MINE   -> (Color.web("#887744"), Color.web("#aaaa55"), Color.web("#cccc77")),
    ProjectileType.KATANA       -> (Color.web("#8899aa"), Color.web("#aabbcc"), Color.web("#ddeeff")),
    ProjectileType.SWORD_WAVE   -> (Color.web("#7788aa"), Color.web("#99aacc"), Color.web("#bbccee")),
    ProjectileType.PLAGUE_BOLT  -> (Color.web("#557733"), Color.web("#77aa44"), Color.web("#aadd66")),
    ProjectileType.MIASMA       -> (Color.web("#556633"), Color.web("#88aa44"), Color.web("#bbdd66")),
    ProjectileType.BLIGHT_BOMB  -> (Color.web("#668833"), Color.web("#88bb44"), Color.web("#aadd55")),
    ProjectileType.BLOOD_FANG   -> (Color.web("#881122"), Color.web("#cc2233"), Color.web("#ff4455")),
    ProjectileType.BLOOD_SIPHON -> (Color.web("#772233"), Color.web("#aa3344"), Color.web("#dd5566")),
    ProjectileType.BAT_SWARM    -> (Color.web("#662233"), Color.web("#993344"), Color.web("#cc5566")),
    // Batch 1: Elemental
    ProjectileType.FLAME_BOLT   -> (Color.web("#cc4400"), Color.web("#ff6600"), Color.web("#ffaa33")),
    ProjectileType.FROST_SHARD  -> (Color.web("#4488cc"), Color.web("#66bbff"), Color.web("#aaddff")),
    ProjectileType.LIGHTNING    -> (Color.web("#cccc22"), Color.web("#ffff44"), Color.web("#ffffaa")),
    ProjectileType.CHAIN_LIGHTNING -> (Color.web("#aaaa22"), Color.web("#dddd44"), Color.web("#ffff88")),
    ProjectileType.THUNDER_STRIKE -> (Color.web("#9999cc"), Color.web("#bbbbff"), Color.web("#ddddff")),
    ProjectileType.BOULDER      -> (Color.web("#886644"), Color.web("#aa8855"), Color.web("#ccaa77")),
    ProjectileType.SEISMIC_SLAM -> (Color.web("#776644"), Color.web("#998866"), Color.web("#bbaa88")),
    ProjectileType.WIND_BLADE   -> (Color.web("#88bbcc"), Color.web("#aaddee"), Color.web("#ccffff")),
    ProjectileType.MAGMA_BALL   -> (Color.web("#aa3300"), Color.web("#dd5500"), Color.web("#ff8833")),
    ProjectileType.ERUPTION     -> (Color.web("#992200"), Color.web("#cc4400"), Color.web("#ff7722")),
    ProjectileType.FROST_TRAP   -> (Color.web("#3377aa"), Color.web("#55aadd"), Color.web("#88ccff")),
    ProjectileType.SAND_SHOT    -> (Color.web("#aa9944"), Color.web("#ccbb66"), Color.web("#eedd88")),
    ProjectileType.SAND_BLAST   -> (Color.web("#998833"), Color.web("#bbaa55"), Color.web("#ddcc77")),
    ProjectileType.THORN        -> (Color.web("#448833"), Color.web("#66aa44"), Color.web("#88cc66")),
    ProjectileType.VINE_WHIP    -> (Color.web("#337733"), Color.web("#559955"), Color.web("#77bb77")),
    ProjectileType.THORN_WALL   -> (Color.web("#336633"), Color.web("#558855"), Color.web("#77aa77")),
    ProjectileType.INFERNO_BLAST -> (Color.web("#cc2200"), Color.web("#ff4400"), Color.web("#ffaa22")),
    ProjectileType.GLACIER_SPIKE -> (Color.web("#3366aa"), Color.web("#5599dd"), Color.web("#88ccff")),
    ProjectileType.MUD_GLOB     -> (Color.web("#665533"), Color.web("#887744"), Color.web("#aa9966")),
    ProjectileType.MUD_BOMB     -> (Color.web("#554422"), Color.web("#776633"), Color.web("#998855")),
    ProjectileType.EMBER_SHOT   -> (Color.web("#cc5500"), Color.web("#ff7700"), Color.web("#ffbb44")),
    ProjectileType.AVALANCHE_CRUSH -> (Color.web("#5577aa"), Color.web("#7799cc"), Color.web("#99bbee")),
    // Batch 2: Undead/Dark
    ProjectileType.DEATH_BOLT   -> (Color.web("#443366"), Color.web("#665588"), Color.web("#9977bb")),
    ProjectileType.RAISE_DEAD   -> (Color.web("#445533"), Color.web("#667744"), Color.web("#889966")),
    ProjectileType.BONE_AXE     -> (Color.web("#998877"), Color.web("#bbaa99"), Color.web("#ddccbb")),
    ProjectileType.BONE_THROW   -> (Color.web("#887766"), Color.web("#aa9988"), Color.web("#ccbbaa")),
    ProjectileType.WAIL         -> (Color.web("#554488"), Color.web("#7766aa"), Color.web("#aa99dd")),
    ProjectileType.SOUL_DRAIN   -> (Color.web("#553377"), Color.web("#775599"), Color.web("#9977bb")),
    ProjectileType.CLAW_SWIPE   -> (Color.web("#884433"), Color.web("#aa6644"), Color.web("#cc8866")),
    ProjectileType.DEVOUR       -> (Color.web("#772233"), Color.web("#993344"), Color.web("#bb5566")),
    ProjectileType.SCYTHE       -> (Color.web("#556677"), Color.web("#778899"), Color.web("#99aabb")),
    ProjectileType.REAP         -> (Color.web("#445566"), Color.web("#667788"), Color.web("#8899aa")),
    ProjectileType.SHADOW_BOLT  -> (Color.web("#332244"), Color.web("#554466"), Color.web("#776688")),
    ProjectileType.CURSED_BLADE -> (Color.web("#443355"), Color.web("#665577"), Color.web("#887799")),
    ProjectileType.LIFE_DRAIN   -> (Color.web("#662244"), Color.web("#884466"), Color.web("#aa6688")),
    ProjectileType.SHOVEL       -> (Color.web("#777766"), Color.web("#999988"), Color.web("#bbbbaa")),
    ProjectileType.HEAD_THROW   -> (Color.web("#556644"), Color.web("#778866"), Color.web("#99aa88")),
    ProjectileType.BANDAGE_WHIP -> (Color.web("#998866"), Color.web("#bbaa88"), Color.web("#ddccaa")),
    ProjectileType.CURSE        -> (Color.web("#553388"), Color.web("#7755aa"), Color.web("#9977cc")),
    // Batch 3: Medieval/Fantasy
    ProjectileType.HOLY_BLADE   -> (Color.web("#aa9933"), Color.web("#ccbb44"), Color.web("#eedd66")),
    ProjectileType.HOLY_BOLT    -> (Color.web("#bbaa44"), Color.web("#ddcc55"), Color.web("#ffee77")),
    ProjectileType.ARROW        -> (Color.web("#887755"), Color.web("#aa9966"), Color.web("#ccbb88")),
    ProjectileType.POISON_ARROW -> (Color.web("#558833"), Color.web("#77aa44"), Color.web("#99cc66")),
    ProjectileType.SONIC_WAVE   -> (Color.web("#7766aa"), Color.web("#9988cc"), Color.web("#bbaaee")),
    ProjectileType.SONIC_BOOM   -> (Color.web("#8877bb"), Color.web("#aa99dd"), Color.web("#ccbbff")),
    ProjectileType.FIST         -> (Color.web("#aa8866"), Color.web("#ccaa88"), Color.web("#eeccaa")),
    ProjectileType.SMITE        -> (Color.web("#ccaa33"), Color.web("#eecc44"), Color.web("#ffee66")),
    ProjectileType.CHARM        -> (Color.web("#cc44aa"), Color.web("#ee66cc"), Color.web("#ff88ee")),
    ProjectileType.CARD         -> (Color.web("#cc3333"), Color.web("#ee5555"), Color.web("#ff8888")),
    // Batch 4: Sci-Fi/Tech
    ProjectileType.DATA_BOLT    -> (Color.web("#2299aa"), Color.web("#44bbcc"), Color.web("#66ddee")),
    ProjectileType.VIRUS        -> (Color.web("#33aa44"), Color.web("#55cc66"), Color.web("#77ee88")),
    ProjectileType.LASER        -> (Color.web("#cc2222"), Color.web("#ee4444"), Color.web("#ff7777")),
    ProjectileType.GRAVITY_BALL -> (Color.web("#5533aa"), Color.web("#7755cc"), Color.web("#9977ee")),
    ProjectileType.GRAVITY_WELL -> (Color.web("#442288"), Color.web("#6644aa"), Color.web("#8866cc")),
    ProjectileType.TESLA_COIL   -> (Color.web("#aaaa22"), Color.web("#cccc44"), Color.web("#eeee66")),
    ProjectileType.NANO_BOLT    -> (Color.web("#668899"), Color.web("#88aabb"), Color.web("#aaccdd")),
    ProjectileType.VOID_BOLT    -> (Color.web("#332255"), Color.web("#554477"), Color.web("#776699")),
    ProjectileType.RAILGUN      -> (Color.web("#5588cc"), Color.web("#77aaee"), Color.web("#aaccff")),
    ProjectileType.CLUSTER_BOMB -> (Color.web("#aa4422"), Color.web("#cc6633"), Color.web("#ee8855")),
    // Batch 5: Nature/Beast
    ProjectileType.VENOM_BOLT   -> (Color.web("#447733"), Color.web("#669944"), Color.web("#88bb66")),
    ProjectileType.WEB_SHOT     -> (Color.web("#888888"), Color.web("#aaaaaa"), Color.web("#cccccc")),
    ProjectileType.STINGER      -> (Color.web("#aa8822"), Color.web("#ccaa33"), Color.web("#eecc55")),
    ProjectileType.ACID_BOMB    -> (Color.web("#559922"), Color.web("#77bb33"), Color.web("#99dd55")),
    // AoE Root
    ProjectileType.SEISMIC_ROOT -> (Color.web("#8B6914"), Color.web("#A0822B"), Color.web("#C4A04E")),
    ProjectileType.ROOT_GROWTH  -> (Color.web("#3A6B35"), Color.web("#5B8A4E"), Color.web("#7DAA6B")),
    ProjectileType.WEB_TRAP     -> (Color.web("#999999"), Color.web("#BBBBBB"), Color.web("#DDDDDD")),
    ProjectileType.TREMOR_SLAM  -> (Color.web("#7A5C3A"), Color.web("#9B7D5B"), Color.web("#BDA07E")),
    ProjectileType.ENTANGLE     -> (Color.web("#2D5A1E"), Color.web("#4A7A3A"), Color.web("#6B9B5B")),
    ProjectileType.STONE_GAZE   -> (Color.web("#666655"), Color.web("#888877"), Color.web("#AAAA99")),
    ProjectileType.INK_SNARE    -> (Color.web("#222244"), Color.web("#444466"), Color.web("#666688")),
    ProjectileType.GRAVITY_LOCK -> (Color.web("#553399"), Color.web("#7755BB"), Color.web("#9977DD")),
    // Character-specific
    ProjectileType.KNIFE       -> (Color.web("#889099"), Color.web("#aab0bb"), Color.web("#ccd0dd")),
    ProjectileType.STING       -> (Color.web("#2277cc"), Color.web("#44aaee"), Color.web("#77ccff")),
    ProjectileType.HAMMER      -> (Color.web("#665544"), Color.web("#887766"), Color.web("#aa9988")),
    ProjectileType.HORN        -> (Color.web("#aa9966"), Color.web("#ccbb88"), Color.web("#eeddaa")),
    ProjectileType.MYSTIC_BOLT -> (Color.web("#6633aa"), Color.web("#9955dd"), Color.web("#cc88ff")),
    ProjectileType.PETRIFY     -> (Color.web("#776655"), Color.web("#998877"), Color.web("#bbaa99")),
    ProjectileType.GRAB        -> (Color.web("#554422"), Color.web("#776644"), Color.web("#998866")),
    ProjectileType.JAW         -> (Color.web("#556677"), Color.web("#778899"), Color.web("#99aabb")),
    ProjectileType.TONGUE      -> (Color.web("#cc3344"), Color.web("#ee5566"), Color.web("#ff8899")),
    ProjectileType.ACID_FLASK  -> (Color.web("#228833"), Color.web("#44aa55"), Color.web("#66cc77"))
  )

  private def getColors(projType: Byte): (Color, Color, Color) =
    colorRegistry.getOrElse(projType, (Color.web("#7799cc"), Color.web("#aaccff"), Color.WHITE))

  // Shape categories
  private sealed trait ShapeCategory
  private case object Beam extends ShapeCategory
  private case object Orb extends ShapeCategory
  private case object Spinner extends ShapeCategory
  private case object Tendril extends ShapeCategory
  private case object Cloud extends ShapeCategory
  private case object Dart extends ShapeCategory
  private case object Chain extends ShapeCategory
  private case object Mine extends ShapeCategory

  private val shapeMapping: Map[Byte, ShapeCategory] = Map(
    ProjectileType.NORMAL       -> Orb,
    ProjectileType.TENTACLE     -> Tendril,
    ProjectileType.ICE_BEAM     -> Beam,
    ProjectileType.AXE          -> Spinner,
    ProjectileType.ROPE         -> Tendril,
    ProjectileType.SPEAR        -> Dart,
    ProjectileType.SOUL_BOLT    -> Orb,
    ProjectileType.HAUNT        -> Cloud,
    ProjectileType.ARCANE_BOLT  -> Orb,
    ProjectileType.FIREBALL     -> Orb,
    ProjectileType.SPLASH       -> Orb,
    ProjectileType.TIDAL_WAVE   -> Beam,
    ProjectileType.GEYSER       -> Orb,
    ProjectileType.BULLET       -> Dart,
    ProjectileType.GRENADE      -> Mine,
    ProjectileType.ROCKET       -> Dart,
    ProjectileType.TALON        -> Dart,
    ProjectileType.GUST         -> Cloud,
    ProjectileType.SHURIKEN     -> Spinner,
    ProjectileType.POISON_DART  -> Dart,
    ProjectileType.CHAIN_BOLT   -> Chain,
    ProjectileType.LOCKDOWN_CHAIN -> Chain,
    ProjectileType.SNARE_MINE   -> Mine,
    ProjectileType.KATANA       -> Dart,
    ProjectileType.SWORD_WAVE   -> Beam,
    ProjectileType.PLAGUE_BOLT  -> Orb,
    ProjectileType.MIASMA       -> Cloud,
    ProjectileType.BLIGHT_BOMB  -> Mine,
    ProjectileType.BLOOD_FANG   -> Spinner,
    ProjectileType.BLOOD_SIPHON -> Beam,
    ProjectileType.BAT_SWARM    -> Cloud,
    // Batch 1: Elemental
    ProjectileType.FLAME_BOLT   -> Dart,
    ProjectileType.FROST_SHARD  -> Dart,
    ProjectileType.LIGHTNING    -> Beam,
    ProjectileType.CHAIN_LIGHTNING -> Chain,
    ProjectileType.THUNDER_STRIKE -> Orb,
    ProjectileType.BOULDER      -> Orb,
    ProjectileType.SEISMIC_SLAM -> Orb,
    ProjectileType.WIND_BLADE   -> Dart,
    ProjectileType.MAGMA_BALL   -> Orb,
    ProjectileType.ERUPTION     -> Orb,
    ProjectileType.FROST_TRAP   -> Mine,
    ProjectileType.SAND_SHOT    -> Dart,
    ProjectileType.SAND_BLAST   -> Cloud,
    ProjectileType.THORN        -> Dart,
    ProjectileType.VINE_WHIP    -> Tendril,
    ProjectileType.THORN_WALL   -> Dart,
    ProjectileType.INFERNO_BLAST -> Orb,
    ProjectileType.GLACIER_SPIKE -> Dart,
    ProjectileType.MUD_GLOB     -> Orb,
    ProjectileType.MUD_BOMB     -> Mine,
    ProjectileType.EMBER_SHOT   -> Dart,
    ProjectileType.AVALANCHE_CRUSH -> Orb,
    // Batch 2: Undead/Dark
    ProjectileType.DEATH_BOLT   -> Orb,
    ProjectileType.RAISE_DEAD   -> Orb,
    ProjectileType.BONE_AXE     -> Spinner,
    ProjectileType.BONE_THROW   -> Dart,
    ProjectileType.WAIL         -> Cloud,
    ProjectileType.SOUL_DRAIN   -> Beam,
    ProjectileType.CLAW_SWIPE   -> Dart,
    ProjectileType.DEVOUR       -> Spinner,
    ProjectileType.SCYTHE       -> Spinner,
    ProjectileType.REAP         -> Spinner,
    ProjectileType.SHADOW_BOLT  -> Orb,
    ProjectileType.CURSED_BLADE -> Dart,
    ProjectileType.LIFE_DRAIN   -> Beam,
    ProjectileType.SHOVEL       -> Dart,
    ProjectileType.HEAD_THROW   -> Orb,
    ProjectileType.BANDAGE_WHIP -> Tendril,
    ProjectileType.CURSE        -> Cloud,
    // Batch 3: Medieval/Fantasy
    ProjectileType.HOLY_BLADE   -> Dart,
    ProjectileType.HOLY_BOLT    -> Orb,
    ProjectileType.ARROW        -> Dart,
    ProjectileType.POISON_ARROW -> Dart,
    ProjectileType.SONIC_WAVE   -> Beam,
    ProjectileType.SONIC_BOOM   -> Cloud,
    ProjectileType.FIST         -> Spinner,
    ProjectileType.SMITE        -> Orb,
    ProjectileType.CHARM        -> Orb,
    ProjectileType.CARD         -> Spinner,
    // Batch 4: Sci-Fi/Tech
    ProjectileType.DATA_BOLT    -> Dart,
    ProjectileType.VIRUS        -> Orb,
    ProjectileType.LASER        -> Beam,
    ProjectileType.GRAVITY_BALL -> Orb,
    ProjectileType.GRAVITY_WELL -> Cloud,
    ProjectileType.TESLA_COIL   -> Chain,
    ProjectileType.NANO_BOLT    -> Dart,
    ProjectileType.VOID_BOLT    -> Orb,
    ProjectileType.RAILGUN      -> Beam,
    ProjectileType.CLUSTER_BOMB -> Mine,
    // Batch 5: Nature/Beast
    ProjectileType.VENOM_BOLT   -> Dart,
    ProjectileType.WEB_SHOT     -> Tendril,
    ProjectileType.STINGER      -> Dart,
    ProjectileType.ACID_BOMB    -> Mine,
    // AoE Root
    ProjectileType.SEISMIC_ROOT -> Cloud,
    ProjectileType.ROOT_GROWTH  -> Cloud,
    ProjectileType.WEB_TRAP     -> Tendril,
    ProjectileType.TREMOR_SLAM  -> Cloud,
    ProjectileType.ENTANGLE     -> Tendril,
    ProjectileType.STONE_GAZE   -> Cloud,
    ProjectileType.INK_SNARE    -> Cloud,
    ProjectileType.GRAVITY_LOCK -> Orb,
    // Character-specific
    ProjectileType.KNIFE       -> Dart,
    ProjectileType.STING       -> Chain,
    ProjectileType.HAMMER      -> Spinner,
    ProjectileType.HORN        -> Dart,
    ProjectileType.MYSTIC_BOLT -> Orb,
    ProjectileType.PETRIFY     -> Beam,
    ProjectileType.GRAB        -> Tendril,
    ProjectileType.JAW         -> Tendril,
    ProjectileType.TONGUE      -> Tendril,
    ProjectileType.ACID_FLASK  -> Mine
  )

  private def getShape(projType: Byte): ShapeCategory =
    shapeMapping.getOrElse(projType, Orb)

  /**
   * Render an ability preview animation on a canvas.
   * For non-projectile abilities (PhaseShift, Dash, Teleport), renders themed effects.
   * For FanProjectile, renders multiple paths.
   */
  def render(gc: GraphicsContext, projectileType: Byte, castBehavior: CastBehavior,
             animTick: Int, canvasWidth: Double, canvasHeight: Double): Unit = {
    // Dark background
    gc.setFill(Color.web("#111124"))
    gc.fillRect(0, 0, canvasWidth, canvasHeight)

    // Subtle border
    gc.setStroke(Color.web("#2a2a44"))
    gc.setLineWidth(1)
    gc.strokeRect(0.5, 0.5, canvasWidth - 1, canvasHeight - 1)

    castBehavior match {
      case PhaseShiftBuff(_) =>
        renderPhaseShift(gc, animTick, canvasWidth, canvasHeight)
      case DashBuff(_, _, _) =>
        renderDash(gc, animTick, canvasWidth, canvasHeight)
      case TeleportCast(_) =>
        renderTeleport(gc, animTick, canvasWidth, canvasHeight)
      case FanProjectile(count, fanAngle) =>
        renderFan(gc, projectileType, count, fanAngle, animTick, canvasWidth, canvasHeight)
      case GroundSlam(_) =>
        renderProjectile(gc, projectileType, animTick, canvasWidth, canvasHeight)
      case StandardProjectile =>
        renderProjectile(gc, projectileType, animTick, canvasWidth, canvasHeight)
      case BarrierCast(_) =>
        renderBarrier(gc, animTick, canvasWidth, canvasHeight)
      case TrapCast(trapType, _) =>
        renderTrap(gc, trapType, animTick, canvasWidth, canvasHeight)
    }
  }

  /**
   * A trap over its whole life: thrown out along an arc, arming where it lands, and snapping on
   * whoever walks into it. The three are what the ability actually is — a throw that does nothing
   * at all for a moment and then takes somebody — so the preview shows all three rather than the
   * object sitting still.
   */
  private def renderTrap(gc: GraphicsContext, trapType: Byte, animTick: Int, w: Double, h: Double): Unit = {
    val tDef = TrapDef.get(trapType)
    if (tDef == null) return
    val col = trapColor(tDef)
    val cy = h * 0.62
    val fromX = 24.0
    val toX = w * 0.66

    val period = 132
    val t = animTick % period
    val phase = animTick * 0.15

    // Where it lands: the ground under it, drawn throughout so the arc reads as a throw
    gc.setFill(Color.color(0, 0, 0, 0.25))
    gc.fillOval(toX - 13, cy - 4, 26, 8)

    if (t < 34) {
      // Thrown: an arc out to the landing cell, with its shadow running along the ground
      val f = t / 34.0
      val x = fromX + (toX - fromX) * f
      val lift = 22 * Math.sin(f * Math.PI)
      gc.setFill(Color.color(0, 0, 0, 0.22))
      gc.fillOval(x - 5, cy - 3, 10, 6)
      gc.setFill(col.deriveColor(0, 1, 0.7, 1))
      gc.fillOval(x - 5, cy - lift - 5, 10, 10)
      gc.setStroke(col.deriveColor(0, 1, 1, 0.35))
      gc.setLineWidth(1)
      gc.strokeOval(x - 7, cy - lift - 7, 14, 14)
    } else {
      val landed = t - 34
      drawTrapTop(gc, tDef, col, toX, cy, phase, snapped = landed >= 62)
      if (landed < 26) {
        // Arming: a ring closing on it, and it is not live until it has
        val f = landed / 26.0
        val rad = 22 - 11 * f
        gc.setStroke(Color.color(col.getRed, col.getGreen, col.getBlue, 0.25 + 0.55 * f))
        gc.setLineWidth(1.3)
        gc.strokeOval(toX - rad, cy - rad * 0.45, rad * 2, rad * 0.9)
      } else if (landed < 62) {
        // Live, and something walking into it
        val f = (landed - 26) / 36.0
        val px = w * 0.06 + (toX - w * 0.06) * f
        gc.setFill(Color.color(0.75, 0.8, 0.9, 0.75))
        gc.fillOval(px - 5, cy - 16, 10, 12)
        gc.fillRect(px - 3, cy - 6, 6, 8)
      } else {
        // Sprung: a flash and a ring going out from it
        val f = (landed - 62) / 36.0
        val rad = 8 + 26 * f
        gc.setStroke(Color.color(col.getRed, col.getGreen, col.getBlue, Math.max(0.0, 0.75 * (1 - f))))
        gc.setLineWidth(2)
        gc.strokeOval(toX - rad, cy - rad * 0.45, rad * 2, rad * 0.9)
        if (f < 0.4) {
          gc.setFill(Color.color(1, 1, 0.92, 0.55 * (1 - f / 0.4)))
          gc.fillOval(toX - 16, cy - 9, 32, 18)
        }
      }
    }
  }

  /** The trap itself, seen from above: jaws, a mine, a pod, a rune or a web. */
  private def drawTrapTop(gc: GraphicsContext, tDef: TrapDef, col: Color, cx: Double, cy: Double,
                          phase: Double, snapped: Boolean): Unit = {
    val dark = col.deriveColor(0, 1, 0.45, 1)
    tDef.kind match {
      case TrapKind.MINE =>
        gc.setFill(dark)
        gc.fillOval(cx - 11, cy - 4, 22, 11)
        gc.setFill(col)
        gc.fillOval(cx - 11, cy - 8, 22, 11)
        gc.setStroke(Color.color(1, 0.75, 0.4, 0.8))
        gc.setLineWidth(1.4)
        gc.strokeOval(cx - 7, cy - 6, 14, 7)
        val blink = if ((phase * 40).toInt % 12 < 3) 1.0 else 0.2
        gc.setFill(Color.color(1, 0.35, 0.25, blink))
        gc.fillOval(cx - 2, cy - 7, 4, 3)

      case TrapKind.POD =>
        gc.setFill(dark)
        gc.fillOval(cx - 11, cy - 6, 22, 13)
        gc.setFill(col)
        gc.fillOval(cx - 7, cy - 9, 14, 9)
        gc.setFill(Color.color(col.getRed, col.getGreen, col.getBlue, 0.25))
        gc.fillOval(cx - 13, cy - 13, 26, 16)

      case TrapKind.RUNE =>
        gc.setFill(Color.color(0.05, 0.03, 0.02, 0.6))
        gc.fillOval(cx - 15, cy - 7, 30, 14)
        val flick = 0.65 + 0.35 * Math.sin(phase * 1.6)
        gc.setStroke(Color.color(col.getRed, col.getGreen, col.getBlue, flick))
        gc.setLineWidth(1.6)
        gc.strokeOval(cx - 12, cy - 6, 24, 12)
        gc.strokeLine(cx - 6, cy - 2, cx + 6, cy + 2)
        gc.strokeLine(cx + 6, cy - 2, cx - 6, cy + 2)
        gc.strokeLine(cx, cy - 4, cx, cy + 4)

      case TrapKind.WEB =>
        gc.setStroke(Color.color(col.getRed, col.getGreen, col.getBlue, 0.7))
        gc.setLineWidth(1)
        for (i <- 0 until 8) {
          val a = Math.PI * 2 * i / 8
          gc.strokeLine(cx, cy, cx + Math.cos(a) * 14, cy + Math.sin(a) * 7)
        }
        gc.strokeOval(cx - 7, cy - 3.5, 14, 7)
        gc.strokeOval(cx - 13, cy - 6.5, 26, 13)

      case _ =>
        // Jaws: open around the plate, shut once it has sprung
        val open = if (snapped) 0.0 else 4.0
        gc.setFill(dark)
        gc.fillOval(cx - 12, cy - 5, 24, 11)
        gc.setStroke(col)
        gc.setLineWidth(3)
        gc.strokeArc(cx - 13, cy - 7 - open, 26, 14, 20, 140, javafx.scene.shape.ArcType.OPEN)
        gc.strokeArc(cx - 13, cy - 7 + open, 26, 14, 200, 140, javafx.scene.shape.ArcType.OPEN)
        gc.setFill(col.deriveColor(0, 1, 1.3, 1))
        gc.fillOval(cx - 4, cy - 2, 8, 4)
    }
  }

  private def trapColor(tDef: TrapDef): Color =
    Color.rgb((tDef.colorRGB >> 16) & 0xFF, (tDef.colorRGB >> 8) & 0xFF, tDef.colorRGB & 0xFF)

  private def renderProjectile(gc: GraphicsContext, projType: Byte, animTick: Int,
                                w: Double, h: Double): Unit = {
    val (primary, secondary, core) = getColors(projType)
    val shape = getShape(projType)
    val cycleLen = 120.0
    val t = (animTick % cycleLen) / cycleLen
    val x = 16 + t * (w - 32)
    val cy = h / 2.0
    val phase = animTick * 0.15
    val pulse = 0.8 + 0.2 * Math.sin(phase)

    // Trail
    drawTrail(gc, x, cy, primary, pulse, 30)

    shape match {
      case Beam => drawBeamShape(gc, x, cy, primary, secondary, core, pulse, phase)
      case Orb => drawOrbShape(gc, x, cy, primary, secondary, core, pulse, phase)
      case Spinner => drawSpinnerShape(gc, x, cy, primary, secondary, core, pulse, phase)
      case Tendril => drawTendrilShape(gc, x, cy, primary, secondary, core, pulse, phase)
      case Cloud => drawCloudShape(gc, x, cy, primary, secondary, core, pulse, phase)
      case Dart => drawDartShape(gc, x, cy, primary, secondary, core, pulse, phase)
      case Chain => drawChainShape(gc, x, cy, primary, secondary, core, pulse, phase)
      case Mine => drawMineShape(gc, x, cy, primary, secondary, core, pulse, phase)
    }
  }

  private def renderFan(gc: GraphicsContext, projType: Byte, count: Int, fanAngle: Double,
                         animTick: Int, w: Double, h: Double): Unit = {
    val (primary, secondary, core) = getColors(projType)
    val shape = getShape(projType)
    val cycleLen = 120.0
    val t = (animTick % cycleLen) / cycleLen
    val startX = 16.0
    val cx = h / 2.0
    val phase = animTick * 0.15
    val pulse = 0.8 + 0.2 * Math.sin(phase)

    val isFullCircle = fanAngle >= 2 * Math.PI - 0.1
    val displayCount = Math.min(count, if (isFullCircle) 6 else count)

    for (i <- 0 until displayCount) {
      val angle = if (isFullCircle) {
        (i.toDouble / displayCount) * 2 * Math.PI
      } else {
        -fanAngle / 2.0 + (i.toDouble / (displayCount - 1).max(1)) * fanAngle
      }

      val dist = t * (w - 32)
      val px = startX + Math.cos(angle) * dist
      val py = cx + Math.sin(angle) * dist * 0.6 // compress Y for canvas aspect

      if (px > 0 && px < w && py > 2 && py < h - 2) {
        // Smaller trail and shapes for fan
        drawTrail(gc, px, py, primary, pulse, 10)
        val smallPulse = pulse * 0.7
        shape match {
          case Beam => drawBeamShape(gc, px, py, primary, secondary, core, smallPulse, phase, 0.5)
          case Orb => drawOrbShape(gc, px, py, primary, secondary, core, smallPulse, phase, 0.5)
          case _ => drawOrbShape(gc, px, py, primary, secondary, core, smallPulse, phase, 0.5)
        }
      }
    }
  }

  private def renderPhaseShift(gc: GraphicsContext, animTick: Int, w: Double, h: Double): Unit = {
    val phase = animTick * 0.08
    val cx = w / 2.0
    val cy = h / 2.0

    // Ghost shimmer effect
    for (i <- 0 until 5) {
      val offset = Math.sin(phase + i * 1.2) * 15
      val alpha = 0.12 + 0.08 * Math.sin(phase * 2 + i)
      gc.setFill(Color.color(0.5, 0.7, 1.0, alpha))
      gc.fillOval(cx - 12 + offset, cy - 10 + Math.cos(phase + i) * 5, 24, 20)
    }

    // Central figure outline
    val flicker = 0.3 + 0.3 * Math.sin(phase * 3)
    gc.setStroke(Color.color(0.6, 0.8, 1.0, flicker))
    gc.setLineWidth(1.5)
    gc.strokeOval(cx - 8, cy - 10, 16, 20)

    // Phase arrows
    val arrowAlpha = 0.2 + 0.15 * Math.sin(phase * 2)
    gc.setFill(Color.color(0.5, 0.7, 1.0, arrowAlpha))
    val arrowX = (animTick % 60) / 60.0 * w
    gc.fillPolygon(
      Array(arrowX, arrowX - 6, arrowX - 6),
      Array(cy, cy - 4, cy + 4), 3)
  }

  private def renderDash(gc: GraphicsContext, animTick: Int, w: Double, h: Double): Unit = {
    val phase = animTick * 0.12
    val cy = h / 2.0
    val cycleLen = 80.0
    val t = (animTick % cycleLen) / cycleLen

    // Speed lines
    for (i <- 0 until 8) {
      val lineX = (t * w + i * w / 8.0) % w
      val lineY = cy + Math.sin(phase + i * 0.8) * (h * 0.3)
      val alpha = 0.15 + 0.1 * Math.sin(phase + i)
      gc.setStroke(Color.color(0.7, 0.85, 1.0, alpha))
      gc.setLineWidth(1.5)
      gc.strokeLine(lineX, lineY, lineX - 20, lineY)
    }

    // Moving figure
    val figX = 20 + t * (w - 40)
    gc.setFill(Color.color(0.6, 0.8, 1.0, 0.4))
    gc.fillOval(figX - 5, cy - 7, 10, 14)

    // Motion blur trail
    for (i <- 1 to 4) {
      val alpha = 0.15 - i * 0.03
      gc.setFill(Color.color(0.5, 0.7, 1.0, Math.max(0.02, alpha)))
      gc.fillOval(figX - 5 - i * 8, cy - 7, 10, 14)
    }
  }

  private def renderTeleport(gc: GraphicsContext, animTick: Int, w: Double, h: Double): Unit = {
    val phase = animTick * 0.1
    val cy = h / 2.0
    val cycleLen = 90.0
    val t = (animTick % cycleLen) / cycleLen

    val startX = w * 0.25
    val endX = w * 0.75

    if (t < 0.4) {
      // Fade out at start position
      val fadeOut = 1.0 - t / 0.4
      gc.setFill(Color.color(0.5, 0.4, 1.0, 0.3 * fadeOut))
      gc.fillOval(startX - 8, cy - 10, 16, 20)

      // Sparkle particles expanding
      for (i <- 0 until 6) {
        val angle = i * Math.PI / 3 + phase
        val dist = (1.0 - fadeOut) * 15
        val px = startX + Math.cos(angle) * dist
        val py = cy + Math.sin(angle) * dist
        gc.setFill(Color.color(0.7, 0.5, 1.0, 0.4 * fadeOut))
        gc.fillOval(px - 2, py - 2, 4, 4)
      }
    } else if (t < 0.6) {
      // Flash line connecting start to end
      val flashAlpha = Math.sin((t - 0.4) / 0.2 * Math.PI) * 0.3
      gc.setStroke(Color.color(0.6, 0.4, 1.0, flashAlpha))
      gc.setLineWidth(2)
      gc.strokeLine(startX, cy, endX, cy)
    } else {
      // Fade in at end position
      val fadeIn = (t - 0.6) / 0.4
      gc.setFill(Color.color(0.5, 0.4, 1.0, 0.3 * fadeIn))
      gc.fillOval(endX - 8, cy - 10, 16, 20)

      // Sparkle particles contracting
      for (i <- 0 until 6) {
        val angle = i * Math.PI / 3 + phase
        val dist = (1.0 - fadeIn) * 15
        val px = endX + Math.cos(angle) * dist
        val py = cy + Math.sin(angle) * dist
        gc.setFill(Color.color(0.7, 0.5, 1.0, 0.4 * fadeIn))
        gc.fillOval(px - 2, py - 2, 4, 4)
      }
    }
  }

  /** A figure behind a raised barrier, and enemy shots coming in from the right and stopping on
    * it, each with a ripple where it struck. The barrier is Barrier's own polyline, turned to face
    * right: bent back at the ends, as it is in the game. */
  private def renderBarrier(gc: GraphicsContext, animTick: Int, w: Double, h: Double): Unit = {
    val cy = h / 2.0
    val figX = 34.0
    // Barrier's frame on the canvas: px per cell across it, and where the ends and middle stand
    val perCell = (h / 2.0 - 5.0) / (Barrier.WIDTH / 2)
    def barrierX(across: Double): Double =
      figX + 13.0 + (Barrier.forwardAt(across.toFloat) - (Barrier.DISTANCE - Barrier.BEND)) / Barrier.BEND * 7.0
    def barrierY(across: Double): Double = cy + across * perCell

    val period = 54
    val flight = 36
    val shots = Array(-2.0, 0.4, 2.2)
    // How hard the barrier has just been struck: it flares with each ripple
    var flare = 0.0
    for (i <- shots.indices) {
      val t = (animTick + i * (period / shots.length)) % period
      if (t >= flight) flare = Math.max(flare, 1.0 - (t - flight).toDouble / (period - flight))
    }

    // The holder
    gc.setFill(Color.color(0.6, 0.8, 1.0, 0.45))
    gc.fillOval(figX - 5, cy - 7, 10, 14)

    // The barrier: a glow along it, the line of it, and a post at each end
    val xs = Array.tabulate(Barrier.POINTS)(i => barrierX(Barrier.localA(i)))
    val ys = Array.tabulate(Barrier.POINTS)(i => barrierY(Barrier.localA(i)))
    gc.setStroke(Color.color(0.35, 0.7, 1.0, 0.16 + 0.2 * flare))
    gc.setLineWidth(7)
    gc.strokePolyline(xs, ys, Barrier.POINTS)
    gc.setStroke(Color.color(0.7, 0.88, 1.0, 0.75 + 0.25 * flare))
    gc.setLineWidth(1.8)
    gc.strokePolyline(xs, ys, Barrier.POINTS)
    gc.setFill(Color.color(0.8, 0.92, 1.0, 0.9))
    gc.fillOval(xs(0) - 2, ys(0) - 2, 4, 4)
    gc.fillOval(xs(Barrier.POINTS - 1) - 2, ys(Barrier.POINTS - 1) - 2, 4, 4)

    // Shots flying in, and the ripple each leaves where the barrier stops it
    for (i <- shots.indices) {
      val t = (animTick + i * (period / shots.length)) % period
      val sy = barrierY(shots(i))
      val stopX = barrierX(shots(i)) + 3
      if (t < flight) {
        val x = (w - 8) - (t.toDouble / flight) * ((w - 8) - stopX)
        drawTrail(gc, x + 10, sy, Color.web("#ff7744"), 1.0, 22)
        gc.setFill(Color.color(1.0, 0.45, 0.25, 0.3))
        gc.fillOval(x - 6, sy - 6, 12, 12)
        gc.setFill(Color.color(1.0, 0.75, 0.5, 0.9))
        gc.fillOval(x - 3, sy - 3, 6, 6)
      } else {
        val r = (t - flight).toDouble / (period - flight)
        val fade = 1.0 - r
        gc.setFill(Color.color(0.8, 0.92, 1.0, 0.55 * fade * fade))
        gc.fillOval(stopX - 4, sy - 4, 8, 8)
        gc.setStroke(Color.color(0.6, 0.85, 1.0, 0.85 * fade))
        gc.setLineWidth(0.8 + 1.4 * fade)
        val rx = 2.0 + 5.0 * r
        val ry = 3.0 + 9.0 * r
        gc.strokeOval(stopX - 3 - rx, sy - ry, rx * 2, ry * 2)
      }
    }
  }

  // --- Shape drawing helpers ---

  private def drawTrail(gc: GraphicsContext, x: Double, cy: Double,
                         color: Color, pulse: Double, length: Int): Unit = {
    for (i <- 1 to 4) {
      val alpha = (0.08 - i * 0.015) * pulse
      if (alpha > 0) {
        gc.setFill(Color.color(
          color.getRed, color.getGreen, color.getBlue, Math.max(0.01, alpha)))
        gc.fillOval(x - 3 - i * (length / 4.0), cy - 2, 6, 4)
      }
    }
  }

  private def drawBeamShape(gc: GraphicsContext, x: Double, cy: Double,
                              primary: Color, secondary: Color, core: Color,
                              pulse: Double, phase: Double, scale: Double = 1.0): Unit = {
    val len = 18 * scale
    val width = 4 * scale * pulse

    // Outer glow
    gc.setStroke(Color.color(primary.getRed, primary.getGreen, primary.getBlue, 0.2 * pulse))
    gc.setLineWidth(width + 4 * scale)
    gc.strokeLine(x - len, cy, x, cy)

    // Main beam
    gc.setStroke(Color.color(secondary.getRed, secondary.getGreen, secondary.getBlue, 0.6 * pulse))
    gc.setLineWidth(width)
    gc.strokeLine(x - len * 0.8, cy, x, cy)

    // Core
    gc.setStroke(Color.color(core.getRed, core.getGreen, core.getBlue, 0.8))
    gc.setLineWidth(Math.max(1, width * 0.4))
    gc.strokeLine(x - len * 0.5, cy, x, cy)

    // Tip glow
    gc.setFill(Color.color(core.getRed, core.getGreen, core.getBlue, 0.5 * pulse))
    gc.fillOval(x - 3 * scale, cy - 3 * scale, 6 * scale, 6 * scale)
  }

  private def drawOrbShape(gc: GraphicsContext, x: Double, cy: Double,
                             primary: Color, secondary: Color, core: Color,
                             pulse: Double, phase: Double, scale: Double = 1.0): Unit = {
    val r = 7 * scale * pulse

    // Outer glow
    gc.setFill(Color.color(primary.getRed, primary.getGreen, primary.getBlue, 0.12 * pulse))
    gc.fillOval(x - r * 1.8, cy - r * 1.8, r * 3.6, r * 3.6)

    // Main orb
    gc.setFill(Color.color(secondary.getRed, secondary.getGreen, secondary.getBlue, 0.5 * pulse))
    gc.fillOval(x - r, cy - r, r * 2, r * 2)

    // Inner core
    gc.setFill(Color.color(core.getRed, core.getGreen, core.getBlue, 0.7))
    gc.fillOval(x - r * 0.4, cy - r * 0.4, r * 0.8, r * 0.8)
  }

  private def drawSpinnerShape(gc: GraphicsContext, x: Double, cy: Double,
                                 primary: Color, secondary: Color, core: Color,
                                 pulse: Double, phase: Double, scale: Double = 1.0): Unit = {
    val r = 6 * scale
    val spinAngle = phase * 4

    // Spinning blades
    for (i <- 0 until 4) {
      val angle = spinAngle + i * Math.PI / 2
      val bx = x + Math.cos(angle) * r
      val by = cy + Math.sin(angle) * r * 0.6
      gc.setFill(Color.color(secondary.getRed, secondary.getGreen, secondary.getBlue, 0.6 * pulse))
      gc.fillOval(bx - 2 * scale, by - 2 * scale, 4 * scale, 4 * scale)
    }

    // Center
    gc.setFill(Color.color(core.getRed, core.getGreen, core.getBlue, 0.7))
    gc.fillOval(x - 3 * scale, cy - 3 * scale, 6 * scale, 6 * scale)
  }

  private def drawTendrilShape(gc: GraphicsContext, x: Double, cy: Double,
                                 primary: Color, secondary: Color, core: Color,
                                 pulse: Double, phase: Double, scale: Double = 1.0): Unit = {
    val len = 14 * scale

    // Wavy tendrils
    gc.setStroke(Color.color(primary.getRed, primary.getGreen, primary.getBlue, 0.3 * pulse))
    gc.setLineWidth(2 * scale)
    for (i <- 0 until 3) {
      val yOff = (i - 1) * 4 * scale
      val waveOff = Math.sin(phase * 3 + i * 2) * 3 * scale
      gc.strokeLine(x - len, cy + yOff + waveOff, x, cy + yOff)
    }

    // Tip
    gc.setFill(Color.color(secondary.getRed, secondary.getGreen, secondary.getBlue, 0.6 * pulse))
    gc.fillOval(x - 3 * scale, cy - 3 * scale, 6 * scale, 6 * scale)

    // Core dot
    gc.setFill(Color.color(core.getRed, core.getGreen, core.getBlue, 0.7))
    gc.fillOval(x - 1.5 * scale, cy - 1.5 * scale, 3 * scale, 3 * scale)
  }

  private def drawCloudShape(gc: GraphicsContext, x: Double, cy: Double,
                               primary: Color, secondary: Color, core: Color,
                               pulse: Double, phase: Double, scale: Double = 1.0): Unit = {
    // Roiling cloud particles
    for (i <- 0 until 5) {
      val angle = phase * 2 + i * Math.PI * 2 / 5
      val dist = 5 * scale * pulse
      val px = x + Math.cos(angle) * dist
      val py = cy + Math.sin(angle) * dist * 0.5
      val alpha = 0.15 + 0.1 * Math.sin(phase + i)
      gc.setFill(Color.color(primary.getRed, primary.getGreen, primary.getBlue, alpha))
      gc.fillOval(px - 4 * scale, py - 4 * scale, 8 * scale, 8 * scale)
    }

    // Center mass
    gc.setFill(Color.color(secondary.getRed, secondary.getGreen, secondary.getBlue, 0.3 * pulse))
    gc.fillOval(x - 6 * scale, cy - 5 * scale, 12 * scale, 10 * scale)
  }

  private def drawDartShape(gc: GraphicsContext, x: Double, cy: Double,
                              primary: Color, secondary: Color, core: Color,
                              pulse: Double, phase: Double, scale: Double = 1.0): Unit = {
    // Arrow/dart shape pointing right
    val len = 10 * scale
    val halfW = 3 * scale

    // Shaft glow
    gc.setStroke(Color.color(primary.getRed, primary.getGreen, primary.getBlue, 0.3 * pulse))
    gc.setLineWidth(3 * scale)
    gc.strokeLine(x - len, cy, x, cy)

    // Arrow head
    gc.setFill(Color.color(secondary.getRed, secondary.getGreen, secondary.getBlue, 0.7 * pulse))
    gc.fillPolygon(
      Array(x + 4 * scale, x - 4 * scale, x - 4 * scale),
      Array(cy, cy - halfW, cy + halfW), 3)

    // Tip
    gc.setFill(Color.color(core.getRed, core.getGreen, core.getBlue, 0.8))
    gc.fillOval(x + 1 * scale, cy - 2 * scale, 4 * scale, 4 * scale)
  }

  private def drawChainShape(gc: GraphicsContext, x: Double, cy: Double,
                               primary: Color, secondary: Color, core: Color,
                               pulse: Double, phase: Double, scale: Double = 1.0): Unit = {
    // Chain links
    val linkCount = 4
    val linkSpacing = 5 * scale

    for (i <- 0 until linkCount) {
      val lx = x - i * linkSpacing
      val ly = cy + Math.sin(phase * 3 + i * 1.5) * 2 * scale
      gc.setStroke(Color.color(secondary.getRed, secondary.getGreen, secondary.getBlue, 0.5 * pulse))
      gc.setLineWidth(1.5 * scale)
      gc.strokeOval(lx - 3 * scale, ly - 2 * scale, 6 * scale, 4 * scale)
    }

    // Tip
    gc.setFill(Color.color(core.getRed, core.getGreen, core.getBlue, 0.7))
    gc.fillOval(x - 2 * scale, cy - 2 * scale, 4 * scale, 4 * scale)
  }

  private def drawMineShape(gc: GraphicsContext, x: Double, cy: Double,
                              primary: Color, secondary: Color, core: Color,
                              pulse: Double, phase: Double, scale: Double = 1.0): Unit = {
    val bounce = Math.abs(Math.sin(phase * 2)) * 3 * scale
    val my = cy - bounce
    val r = 6 * scale

    // Outer warning glow
    gc.setFill(Color.color(primary.getRed, primary.getGreen, primary.getBlue, 0.1 * pulse))
    gc.fillOval(x - r * 2, my - r * 2, r * 4, r * 4)

    // Mine body
    gc.setFill(Color.color(secondary.getRed, secondary.getGreen, secondary.getBlue, 0.6 * pulse))
    gc.fillOval(x - r, my - r, r * 2, r * 2)

    // Spikes
    for (i <- 0 until 6) {
      val angle = phase * 0.5 + i * Math.PI / 3
      val sx = x + Math.cos(angle) * (r + 2 * scale)
      val sy = my + Math.sin(angle) * (r + 2 * scale) * 0.7
      gc.setFill(Color.color(core.getRed, core.getGreen, core.getBlue, 0.5 * pulse))
      gc.fillOval(sx - 1.5 * scale, sy - 1.5 * scale, 3 * scale, 3 * scale)
    }

    // Blinking light
    val blinkAlpha = if (Math.sin(phase * 4) > 0) 0.8 else 0.2
    gc.setFill(Color.color(core.getRed, core.getGreen, core.getBlue, blinkAlpha))
    gc.fillOval(x - 2 * scale, my - 2 * scale, 4 * scale, 4 * scale)
  }
}
