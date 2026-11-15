package com.gridgame.client.render
package hud

import com.gridgame.client.gl.{GLFontRenderer, TextureRegion}
import com.gridgame.common.model.Direction

/**
 * The HUD's look: the menus' dark glass (ui/theme.css, ui/CLAUDE.md *The look*) in the batches'
 * terms, so a match reads as the same game as the screens round it. Panes of navy glass with a rim
 * of light, brightest along the top, standing off the map on a soft shadow; dark wells for what sits
 * in them; keycaps as the menus draw them; labels in small capitals; numbers in Exo 2 Black; one
 * accent, the gold, for what is ours and what is picked, and the scoreboard's green and red for
 * kills and deaths. Every shape is drawn with a smooth edge (ShapeBatch's `*Smooth`), as JavaFX
 * draws the menus'.
 *
 * Stateless but for the context it draws through, and allocates nothing.
 */
private[render] final class HudStyle(ctx: RenderContext) {
  import HudStyle._
  import ctx._

  /**
   * A pane of glass: `alpha` of the menus' card (navy, lighter toward its top), a soft shadow under it
   * so it stands off a bright map, and its rim of light.
   */
  def glass(x: Float, y: Float, w: Float, h: Float, radius: Float, alpha: Float = 1f, solid: Boolean = false): Unit = {
    if (alpha <= 0f) return
    beginShapes()
    shapeBatch.shadowRoundedRect(x, y + 2f, w, h, radius, 10f, 0f, 0f, 0.02f, 0.22f * alpha)
    // A pane over the middle of the screen, where the fight is, hides it: name plates showing through
    // read as part of it, even at 95%
    val topA = if (solid) 1f else GlassA; val footA = if (solid) 1f else GlassFootA
    shapeBatch.fillRoundedRectSmooth(x, y, w, h, radius,
      GlassTopR, GlassTopG, GlassTopB, topA * alpha,
      GlassR, GlassG, GlassB, footA * alpha)
    shapeBatch.strokeRoundedRectSmooth(x, y, w, h, radius, 1f, 1f, 1f, 1f, RimTopA * alpha, RimFootA * alpha)
  }

  /** A pane of glass tinted with an accent (a kill of ours, our death): the glass, then the accent
    * washed over it and round its rim. */
  def tintedGlass(x: Float, y: Float, w: Float, h: Float, radius: Float, r: Float, g: Float, b: Float,
                  alpha: Float = 1f, solid: Boolean = false): Unit = {
    if (alpha <= 0f) return
    glass(x, y, w, h, radius, alpha, solid)
    shapeBatch.fillRoundedRectSmooth(x, y, w, h, radius, r, g, b, 0.20f * alpha, r, g, b, 0.10f * alpha)
    shapeBatch.strokeRoundedRectSmooth(x, y, w, h, radius, 1.5f, r, g, b, 0.75f * alpha, 0.45f * alpha)
  }

  /** A well inside a pane: darker, with a hairline round it (a slot, a portrait's frame). */
  def well(x: Float, y: Float, w: Float, h: Float, radius: Float, alpha: Float = 1f): Unit = {
    beginShapes()
    shapeBatch.fillRoundedRectSmooth(x, y, w, h, radius, WellR, WellG, WellB, 0.82f * alpha, WellR, WellG, WellB, 0.9f * alpha)
    shapeBatch.strokeRoundedRectSmooth(x, y, w, h, radius, 1f, 1f, 1f, 1f, 0.07f * alpha, 0.11f * alpha)
  }

  /** A pill: a chip as the menus draw one, filled with a colour at `fillA`. */
  def pill(x: Float, y: Float, w: Float, h: Float, r: Float, g: Float, b: Float, fillA: Float): Unit = {
    beginShapes()
    shapeBatch.fillRoundedRectSmooth(x, y, w, h, h * 0.5f, r, g, b, fillA, r, g, b, fillA)
  }

  /**
   * A keycap with `label` on it, its top left at (x, y), as the menus' `.keycap`: a dark cap, a rim of
   * light round it, a lip below. Returns how wide it is.
   */
  def keycap(x: Float, y: Float, label: String, font: GLFontRenderer, alpha: Float = 1f): Float = {
    val tw = font.measureWidth(label)
    val h = KeycapH
    val w = Math.max(KeycapMinW, Math.round(tw + 12f).toFloat)
    beginShapes()
    shapeBatch.fillRoundedRectSmooth(x, y, w, h, 5f, 1f, 1f, 1f, 0.26f * alpha, 1f, 1f, 1f, 0.30f * alpha)
    shapeBatch.fillRoundedRectSmooth(x + 1f, y + 1f, w - 2f, h - 3f, 4f, CapR, CapG, CapB, alpha, CapR * 0.9f, CapG * 0.9f, CapB * 0.9f, alpha)
    beginSprites()
    font.drawText(spriteBatch, label, Math.round((x + (w - tw) / 2f) * 2f) / 2f, font.digitsCentredOn(y + (h - 2f) / 2f),
      TextR, TextG, TextB, alpha)
    w
  }

  /** How wide [[keycap]] draws `label`. */
  def keycapWidth(label: String, font: GLFontRenderer): Float =
    Math.max(KeycapMinW, Math.round(font.measureWidth(label) + 12f).toFloat)

  /** An eyebrow: small capitals in the muted grey over what they name (the menus' `Widgets.eyebrow`). */
  def eyebrow(text: String, x: Float, y: Float, alpha: Float = 1f): Float = {
    beginSprites()
    fontTiny.drawText(spriteBatch, text, x, y, MutedR, MutedG, MutedB, alpha)
  }

  // Each character's head and shoulders, cut from the first frame of its sheet facing us, made again
  // only when the atlas is (a grow makes every region anew)
  private val bustOf = new Array[TextureRegion](256)
  private val bustFrom = new Array[TextureRegion](256)

  /**
   * A character's head and shoulders, `size` across, its top left at (x, y): who killed whom in the
   * feed, whom a kill took, who killed us. Nothing for a character we know nothing of (-1).
   */
  def bust(characterId: Byte, x: Float, y: Float, size: Float, alpha: Float = 1f): Unit = {
    if (characterId < 0) return
    val frame = GLSpriteGenerator.getSpriteRegion(Direction.Down, 0, characterId)
    if (frame == null) return
    val i = characterId & 0xFF
    if (bustFrom(i) ne frame) {
      bustFrom(i) = frame
      // From just over the top of its art (a hat's, a squat character's head), down as far again as
      // it is wide, and no further than its soles
      val top = GLSpriteGenerator.artTop(characterId)
      val v0 = Math.min(BustV1Max - BustSpan, if (top < 0f) BustV0 else Math.max(0f, top - BustOver))
      val du = frame.u2 - frame.u; val dv = frame.v2 - frame.v
      bustOf(i) = TextureRegion(frame.texture, frame.u + du * BustU0, frame.v + dv * v0,
        frame.u + du * BustU1, frame.v + dv * (v0 + BustSpan))
    }
    beginSprites()
    spriteBatch.draw(bustOf(i), x, y, size, size, 1f, 1f, 1f, alpha)
  }
}

private[render] object HudStyle {
  // The palette (ui/theme.css's -gg-*)
  final val TextR = 0.933f; final val TextG = 0.949f; final val TextB = 1f          // -gg-text #eef2ff
  final val BodyR = 0.761f; final val BodyG = 0.792f; final val BodyB = 0.894f      // -gg-body #c2cae4
  final val MutedR = 0.545f; final val MutedG = 0.588f; final val MutedB = 0.733f   // -gg-muted #8b96bb
  final val FaintR = 0.365f; final val FaintG = 0.408f; final val FaintB = 0.553f   // -gg-faint #5d688d
  final val GoldR = 1f; final val GoldG = 0.788f; final val GoldB = 0.247f          // -gg-gold #ffc93f
  final val GoldTextR = 1f; final val GoldTextG = 0.831f; final val GoldTextB = 0.4f // -gg-gold-text #ffd466
  final val SkyR = 0.302f; final val SkyG = 0.722f; final val SkyB = 1f             // -gg-sky #4db8ff
  final val LeafR = 0.29f; final val LeafG = 0.847f; final val LeafB = 0.51f        // -gg-leaf #4ad882
  final val BerryR = 1f; final val BerryG = 0.365f; final val BerryB = 0.459f       // -gg-berry #ff5d75
  final val VioletR = 0.663f; final val VioletG = 0.545f; final val VioletB = 1f    // -gg-violet #a98bff

  // The glass (-gg-glass, rgba(14, 20, 42, 0.86), lit a little toward its top as a card is) and its rim
  // (a card's border: 0.13 along the top, 0.06 along the bottom)
  final val GlassR = 0.055f; final val GlassG = 0.078f; final val GlassB = 0.165f
  final val GlassTopR = 0.094f; final val GlassTopG = 0.118f; final val GlassTopB = 0.204f
  final val GlassA = 0.84f; final val GlassFootA = 0.88f
  final val RimTopA = 0.16f; final val RimFootA = 0.06f
  // A well (-gg-well #0b1129) and a keycap's face (#1c2650)
  final val WellR = 0.043f; final val WellG = 0.067f; final val WellB = 0.161f
  final val CapR = 0.11f; final val CapG = 0.149f; final val CapB = 0.314f

  final val KeycapH = 20f
  final val KeycapMinW = 22f

  // The tray along the bottom (HudTray): its slots, the gap between the abilities and the items, its
  // padding, and how far it stands off the bottom of the screen. The scoreboard Tab holds up is laid out
  // clear of it (ScoreboardPainter's BottomClear, 100 units from the foot, over its 86): a taller tray
  // moves that too
  final val SlotSize = 52f
  final val SlotGap = 8f
  final val SlotRadius = 11f
  final val GroupGap = 26f
  final val TrayPadX = 12f
  final val TrayPadY = 10f
  final val TrayBottom = 14f

  // A bust's square in a frame of a sheet (Constants: a frame shows the art's y = -6 to 58, the soles
  // on 54, a bare crown near 8 and the tallest hat at 0): 34 of the frame's 64 units, centred on the
  // figure, from a unit and a half over the top of its art (the tallest hat's top when that is not yet
  // known), and never past its soles
  final val BustSpan = 34f / 64f
  final val BustV0 = 6f / 64f
  final val BustOver = 1.5f / 64f
  final val BustV1Max = 60f / 64f
  final val BustU0 = 15f / 64f
  final val BustU1 = 49f / 64f
}

/**
 * Where the tray along the bottom of the screen lies this frame: one pane of glass, centred, holding
 * the ability slots (Q, E, then R) and, past a hairline, the item slots and how many we carry.
 * HudPainter lays it out each frame and draws the items; AbilityBarPainter draws the abilities in it.
 * It replaced a dark bar the width of the screen.
 */
private[render] final class HudTray {
  import HudStyle._

  var x = 0f
  var y = 0f
  var w = 0f
  var h = 0f
  /** The top of every slot. */
  var slotsY = 0f
  private var abilitiesX = 0f
  private var itemsX = 0f
  /** Where the hairline between the two groups is, and where the count of what we carry starts. */
  var dividerX = 0f
  var countX = 0f

  def abilityX(i: Int): Float = abilitiesX + i * (SlotSize + SlotGap)
  def itemX(i: Int): Float = itemsX + i * (SlotSize + SlotGap)

  /** Lay it out for a screen, with `countW` units at its right end for the count. */
  def layout(screenW: Int, screenH: Int, abilities: Int, items: Int, countW: Float): Unit = {
    val abilitiesW = abilities * SlotSize + (abilities - 1) * SlotGap
    val itemsW = items * SlotSize + (items - 1) * SlotGap
    w = Math.round(TrayPadX * 2 + abilitiesW + GroupGap + itemsW + 12f + countW).toFloat
    h = SlotSize + TrayPadY * 2
    x = Math.round((screenW - w) / 2f).toFloat
    y = Math.round(screenH - TrayBottom - h).toFloat
    slotsY = y + TrayPadY
    abilitiesX = x + TrayPadX
    dividerX = Math.round(abilitiesX + abilitiesW + GroupGap / 2f).toFloat
    itemsX = abilitiesX + abilitiesW + GroupGap
    countX = itemsX + itemsW + 12f
  }
}
