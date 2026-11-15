package com.gridgame.client.render
package hud

import com.gridgame.client.diagnostics.Diagnostics
import com.gridgame.client.i18n.Messages
import com.gridgame.common.Constants
import com.gridgame.common.model._

import com.gridgame.client.render.world.ItemPainter

/**
 * The HUD, drawn over everything in the window's units, in the menus' dark glass (hud/HudStyle): the
 * tray along the bottom (abilities, items and how many we carry, the charge), the match's information
 * along the top, a kill of ours announced, chat, practice's part, and the notices (an item we have no
 * room for, a moment F8 marked). The menu Esc brings up and the scoreboard Tab holds up are drawn over
 * it (MatchMenuPainter, ScoreboardPainter).
 */
private[render] final class HudPainter(ctx: RenderContext, abilityBar: AbilityBarPainter, chat: ChatPainter,
                                       matchInfo: MatchInfoPainter, practice: PracticePainter, items: ItemPainter,
                                       killBanner: KillBannerPainter) {
  import ctx._
  import HudStyle._

  private val style = new HudStyle(ctx)
  private val tray = new HudTray

  // The item slots, with the key each is used by
  private val itemTypes = Array(ItemType.Heart, ItemType.Star, ItemType.Gem, ItemType.Shield, ItemType.Fence)
  private val itemKeys = Array("1", "2", "3", "4", "5")

  // Made again only when they change: the HUD is drawn every frame
  private var _cachedChargeLevel = -1; private var _cachedChargeStr = ""
  private val _cachedSlotCounts = Array.fill(5)(-1)
  private val _cachedSlotCountStrs = new Array[String](5)
  private var _cachedTotal = -1; private var _cachedTotalStr = ""
  private var _cachedMark = -1; private var _cachedMarksFull = false; private var _cachedMarkStr = ""
  private var sInventoryFull: String = _
  // The count is laid out as wide as its widest, so the tray doesn't twitch as it changes
  private var countSlotW = 0f

  def draw(screenW: Int, screenH: Int): Unit = {
    if (sInventoryFull == null) {
      sInventoryFull = Messages.t("Inventory full: use an item to make room")
      countSlotW = fontBody.measureWidth(Constants.MAX_INVENTORY_SIZE + "/" + Constants.MAX_INVENTORY_SIZE)
    }
    tray.layout(screenW, screenH, AbilityBarPainter.Slots, itemTypes.length, countSlotW)
    style.glass(tray.x, tray.y, tray.w, tray.h, 18f)
    shapeBatch.fillRect(tray.dividerX, tray.slotsY + 8f, 1f, SlotSize - 16f, 1f, 1f, 1f, 0.10f)

    renderInventory()
    abilityBar.draw(tray)
    renderChargeBar()
    renderInventoryFull(screenW)
    matchInfo.draw(screenW, screenH)
    chat.draw(screenW, screenH)
    if (client.isPracticeMode) practice.draw(screenW, screenH)
    killBanner.draw(screenW, screenH)
    renderMarkNotice(screenW)
  }

  /**
   * A notice: a pill of glass with a dot of its accent before what it says, its top at `y`, centred
   * across the screen.
   */
  private def toast(screenW: Int, y: Float, text: String, r: Float, g: Float, b: Float): Unit = {
    val textW = fontBody.measureWidth(text)
    val h = 38f
    val w = Math.round(textW + 54f).toFloat
    val x = Math.round(screenW / 2f - w / 2f).toFloat
    style.tintedGlass(x, y, w, h, h / 2f, r, g, b, 0.85f)
    shapeBatch.fillOvalSmooth(x + 22f, y + h / 2f, 5f, 5f, r, g, b, 1f)
    beginSprites()
    fontBody.drawText(spriteBatch, text, x + 36f, fontBody.digitsCentredOn(y + h / 2f), TextR, TextG, TextB, 1f)
  }

  /** "Moment 2 marked": shown for a few seconds after F8 marks one for a problem report, so the player
    * knows it took and where to report it (design/diagnostics.md, *Marking a moment*). */
  private def renderMarkNotice(screenW: Int): Unit = {
    if (frameTimeMs >= Diagnostics.markNoticeUntilMs) return
    val n = Diagnostics.lastMarkNumber
    val full = Diagnostics.marksFull
    if (n != _cachedMark || full != _cachedMarksFull) {
      _cachedMark = n
      _cachedMarksFull = full
      _cachedMarkStr =
        if (full) Messages.t("A match keeps at most {0} marked moments", Diagnostics.MaxMarks.toString)
        else Messages.t("Moment {0} marked: report it when the match ends", n.toString)
    }
    toast(screenW, 84f, _cachedMarkStr, GoldR, GoldG, GoldB)
  }

  /** The item slots: each item's picture in a well, how many we have of it, the key that uses it; and
    * how many we carry of the most the server lets us, past which it leaves items on the ground. */
  private def renderInventory(): Unit = {
    var total = 0
    var i = 0
    while (i < itemTypes.length) {
      val count = client.getItemCount(itemTypes(i).id)
      if (count != _cachedSlotCounts(i)) {
        _cachedSlotCounts(i) = count
        _cachedSlotCountStrs(i) = if (count > 0) count.toString else null
      }
      total += count
      i += 1
    }
    if (total != _cachedTotal) {
      _cachedTotal = total
      _cachedTotalStr = total + "/" + Constants.MAX_INVENTORY_SIZE
    }

    val y = tray.slotsY
    i = 0
    while (i < itemTypes.length) {
      val count = _cachedSlotCounts(i)
      val x = tray.itemX(i)
      style.well(x, y, SlotSize, SlotSize, SlotRadius, if (count > 0) 1f else 0.7f)
      if (count > 0) {
        // Lit a little in the item's colour while we have one
        intToRGB(itemTypes(i).colorRGB)
        shapeBatch.fillRoundedRectSmooth(x, y, SlotSize, SlotSize, SlotRadius, _rgb_r, _rgb_g, _rgb_b, 0.0f,
          _rgb_r, _rgb_g, _rgb_b, 0.16f)
        shapeBatch.strokeRoundedRectSmooth(x, y, SlotSize, SlotSize, SlotRadius, 1f, _rgb_r, _rgb_g, _rgb_b, 0.30f, 0.55f)
      }
      // The item's own picture (ItemPainter.drawIcon): its art fills about seven tenths of this square
      items.drawIcon(itemTypes(i), x + SlotSize / 2, y + SlotSize / 2 - 2, 48f, count > 0)
      i += 1
    }

    // How many of each, on a badge over its top right corner, and its key under its bottom left
    i = 0
    while (i < itemTypes.length) {
      val count = _cachedSlotCounts(i)
      val x = tray.itemX(i)
      if (count > 0) {
        val s = _cachedSlotCountStrs(i)
        val bw = Math.max(20f, fontTiny.measureWidth(s) + 11f)
        val bx = x + SlotSize - bw + 5f; val by = y - 5f
        beginShapes()
        shapeBatch.fillRoundedRectSmooth(bx, by, bw, 20f, 10f, TextR, TextG, TextB, 1f, BodyR, BodyG, BodyB, 1f)
        shapeBatch.strokeRoundedRectSmooth(bx, by, bw, 20f, 10f, 1f, WellR, WellG, WellB, 0.9f, 0.9f)
        beginSprites()
        fontTiny.drawText(spriteBatch, s, Math.round((bx + (bw - fontTiny.measureWidth(s)) / 2f) * 2f) / 2f,
          fontTiny.digitsCentredOn(by + 10f), WellR, WellG, WellB, 1f)
      }
      style.keycap(x - 4f, y + SlotSize - KeycapH + 5f, itemKeys(i), fontTiny, if (count > 0) 1f else 0.6f)
      i += 1
    }

    // How many we carry, gold when it is all we may
    val full = total >= Constants.MAX_INVENTORY_SIZE
    beginSprites()
    fontBody.drawText(spriteBatch, _cachedTotalStr, tray.countX, fontBody.digitsCentredOn(y + SlotSize / 2f),
      if (full) GoldTextR else MutedR, if (full) GoldTextG else MutedG, if (full) GoldTextB else MutedB, 1f)
  }

  /** Said while an item lies within our reach that the server won't give us because we carry as
    * many as it allows (Items.isInventoryFull): it stays on the ground until we use one of ours.
    * Nothing said it, and a full inventory looked like an item that couldn't be picked up. */
  private def renderInventoryFull(screenW: Int): Unit = {
    if (_cachedTotal < Constants.MAX_INVENTORY_SIZE || client.getIsDead || !client.itemWithinReach) return
    val above = if (chargeShown) 46f else 0f
    toast(screenW, tray.y - 50f - above, sInventoryFull, GoldR, GoldG, GoldB)
  }

  private def chargeShown: Boolean =
    client.isCharging && ProjectileDef.get(client.getSelectedCharacterDef.primaryProjectileType).chargeSpeedScaling.isDefined

  /**
   * How far our charged shot is charged, over the middle of the tray: a slim gauge in the gold of its
   * lane on the ground (render/CLAUDE.md, *A charged shot's aim is a lane on the ground*), deep as it
   * starts and bright at full, where it breathes.
   */
  private def renderChargeBar(): Unit = {
    if (!chargeShown) return
    val level = client.getChargeLevel
    val pct = level / 100f
    val w = 180f; val h = 10f
    val textW = fontTiny.measureWidth("100%")
    val x = Math.round(tray.x + tray.w / 2f - (w + 10f + textW) / 2f).toFloat
    val y = tray.y - 30f
    val full = level >= 100
    val beat = if (full) (Math.sin(frameTimeMs * 0.012).toFloat + 1f) * 0.5f else 0f
    style.glass(x - 8f, y - 7f, w + 10f + textW + 18f, h + 14f, 12f)
    shapeBatch.fillRoundedRectSmooth(x, y, w, h, h / 2f, WellR, WellG, WellB, 1f, WellR, WellG, WellB, 1f)
    if (pct > 0f) {
      // Deep gold as it starts, the HUD's gold at full
      val fr = 0.86f + 0.14f * pct; val fg = 0.52f + 0.27f * pct; val fb = 0.12f + 0.13f * pct
      shapeBatch.fillRoundedRectSmooth(x, y, Math.max(h, w * pct), h, h / 2f,
        Math.min(1f, fr + 0.12f), Math.min(1f, fg + 0.16f), Math.min(1f, fb + 0.2f), 1f, fr * 0.9f, fg * 0.85f, fb, 1f)
    }
    if (full) {
      shapeBatch.setAdditiveBlend(true)
      shapeBatch.fillOvalSoft(x + w / 2f, y + h / 2f, w * 0.62f, 18f, GoldR, GoldG, GoldB, 0.25f + 0.2f * beat, 0f, 32)
      shapeBatch.setAdditiveBlend(false)
    }
    shapeBatch.strokeRoundedRectSmooth(x, y, w, h, h / 2f, 1f, 1f, 1f, 1f, 0.18f, 0.08f)
    if (level != _cachedChargeLevel) { _cachedChargeLevel = level; _cachedChargeStr = level + "%" }
    beginSprites()
    fontTiny.drawText(spriteBatch, _cachedChargeStr, x + w + 10f, fontTiny.digitsCentredOn(y + h / 2f),
      if (full) GoldTextR else BodyR, if (full) GoldTextG else BodyG, if (full) GoldTextB else BodyB, 1f)
  }
}
