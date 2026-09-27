package com.gridgame.client.render
package hud

import com.gridgame.client.i18n.Messages
import com.gridgame.common.model._

import com.gridgame.client.render.world.ItemPainter

/**
 * The HUD, drawn over everything in screen pixels: the bar along the bottom (inventory, abilities,
 * charge), the match's information, chat, practice's tallies, and the prompt to leave.
 */
private[render] final class HudPainter(ctx: RenderContext, abilityBar: AbilityBarPainter, chat: ChatPainter,
                                       matchInfo: MatchInfoPainter, practice: PracticePainter, items: ItemPainter) {
  import ctx._

  // Pre-allocated HUD data (avoids per-frame Seq allocations)
  private val inventorySlotTypes = Array((ItemType.Heart, "1"), (ItemType.Star, "2"), (ItemType.Gem, "3"), (ItemType.Shield, "4"), (ItemType.Fence, "5"))
  // Ability colors unpacked into parallel arrays (see _abilityR/G/B below)

  // Cached HUD strings — only re-allocated when values change
  private var _cachedChargeLevel = -1; private var _cachedChargeStr = ""

  // Cached inventory slot counts — avoids double getItemCount calls and per-frame toString
  private val _cachedSlotCounts = new Array[Int](5) // one per inventory slot
  private val _cachedSlotCountStrs = new Array[String](5)

  def draw(screenW: Int, screenH: Int): Unit = {
    // Dark backing panel behind the entire bottom HUD bar
    val hudBarH = 78f
    beginShapes()
    shapeBatch.fillRoundedRectGradient(0f, screenH - hudBarH, screenW.toFloat, hudBarH, 0f,
      0.02f, 0.02f, 0.06f, 0.50f,
      0.01f, 0.01f, 0.03f, 0.40f)
    // Top edge subtle highlight
    shapeBatch.strokeLine(0f, screenH - hudBarH, screenW.toFloat, screenH - hudBarH, 1f, 1f, 1f, 1f, 0.06f)

    renderInventory(screenW, screenH)
    abilityBar.draw(screenW, screenH)
    renderChargeBar(screenW, screenH)
    matchInfo.draw(screenW, screenH)
    chat.draw(screenW, screenH)
    if (client.isPracticeMode) practice.draw(screenW, screenH)
    renderLeavePrompt(screenW)
  }

  /** "Press Esc again", shown for the few seconds the first Esc arms leaving the match. */
  private def renderLeavePrompt(screenW: Int): Unit = {
    if (frameTimeMs >= client.leaveConfirmUntil) return
    val text =
      if (client.isPracticeMode) Messages.t("Press Esc again to end practice")
      else Messages.t("Press Esc again to leave the match")
    val textW = fontMedium.measureWidth(text)
    val boxW = textW + 36f
    val boxH = 36f
    val boxX = screenW / 2f - boxW / 2f
    val boxY = 84f
    beginShapes()
    shapeBatch.fillRoundedRect(boxX, boxY, boxW, boxH, 8f, 0.10f, 0.03f, 0.05f, 0.85f)
    shapeBatch.strokeRect(boxX, boxY, boxW, boxH, 1f, 0.9f, 0.3f, 0.35f, 0.6f)
    beginSprites()
    fontMedium.drawTextOutlined(spriteBatch, text, screenW / 2f - textW / 2f, boxY + (boxH - fontMedium.charHeight) / 2f, 1f, 0.85f, 0.85f)
  }

  private def renderInventory(screenW: Int, screenH: Int): Unit = {
    val slotSize = 54f; val slotGap = 8f; val numSlots = 5
    val totalW = numSlots * slotSize + (numSlots - 1) * slotGap
    val startX = (screenW - totalW) / 2f
    val startY = screenH - slotSize - 14f

    // Compute and cache slot counts once per frame
    var i = 0
    while (i < inventorySlotTypes.length) {
      val count = client.getItemCount(inventorySlotTypes(i)._1.id)
      if (count != _cachedSlotCounts(i)) {
        _cachedSlotCounts(i) = count
        _cachedSlotCountStrs(i) = if (count > 0) count.toString else null
      }
      i += 1
    }

    // Draw all slot backgrounds + icons (shapes)
    beginShapes()
    i = 0
    while (i < inventorySlotTypes.length) {
      val pair = inventorySlotTypes(i)
      val itemType = pair._1
      val count = _cachedSlotCounts(i)
      val slotX = startX + i * (slotSize + slotGap)
      intToRGB(itemType.colorRGB)
      val tr = _rgb_r; val tg = _rgb_g; val tb = _rgb_b

      if (count > 0) {
        shapeBatch.fillRoundedRect(slotX - 2, startY - 2, slotSize + 4, slotSize + 4, 6f, tr * 0.3f, tg * 0.3f, tb * 0.3f, 0.5f)
        shapeBatch.fillRoundedRect(slotX, startY, slotSize, slotSize, 5f, 0.08f, 0.08f, 0.14f, 0.80f)
        // Top edge highlight
        shapeBatch.strokeLine(slotX + 4, startY, slotX + slotSize - 4, startY, 1f, 1f, 1f, 1f, 0.08f)
      } else {
        shapeBatch.fillRoundedRect(slotX, startY, slotSize, slotSize, 5f, 0.06f, 0.06f, 0.1f, 0.50f)
      }

      val iconCX = slotX + slotSize / 2; val iconCY = startY + slotSize / 2 - 1; val iconSize = 17f
      if (count > 0) items.drawItemShape(itemType, iconCX, iconCY, iconSize, tr, tg, tb)
      else items.drawItemShape(itemType, iconCX, iconCY, iconSize, tr * 0.3f, tg * 0.3f, tb * 0.3f)

      if (count > 0) {
        val countStr = _cachedSlotCountStrs(i)
        val badgeW = Math.max(14f, 7f + countStr.length * 6f)
        val badgeX = slotX + slotSize - badgeW / 2 - 1
        val badgeY = startY - 5
        shapeBatch.fillRect(badgeX, badgeY, badgeW, 14f, 0.78f, 0.18f, 0.18f, 0.9f)
      }
      i += 1
    }

    // Draw all text labels (sprites) in one batch
    beginSprites()
    i = 0
    while (i < inventorySlotTypes.length) {
      val pair = inventorySlotTypes(i)
      val keyLabel = pair._2
      val count = _cachedSlotCounts(i)
      val slotX = startX + i * (slotSize + slotGap)

      if (count > 0) {
        fontSmall.drawText(spriteBatch, _cachedSlotCountStrs(i), slotX + slotSize - 8, startY - 3)
      }
      fontSmall.drawText(spriteBatch, keyLabel, slotX + slotSize / 2 - 4, startY + slotSize - 16,
        if (count > 0) 0.9f else 0.43f, if (count > 0) 0.9f else 0.43f, if (count > 0) 0.9f else 0.47f, 1f)
      i += 1
    }
  }

  private def renderChargeBar(screenW: Int, screenH: Int): Unit = {
    if (!client.isCharging) return
    val cpt = client.getSelectedCharacterDef.primaryProjectileType
    if (!ProjectileDef.get(cpt).chargeSpeedScaling.isDefined) return

    val chargeLevel = client.getChargeLevel
    val barW = 100f; val barH = 8f
    val barX = (screenW - barW) / 2f; val barY = screenH - 80f
    val tick = animationTick

    beginShapes()
    // Background with subtle pulse based on charge level
    val bgPulse = (0.95 + 0.05 * Math.sin(tick * 0.08 * (1f + chargeLevel / 100f))).toFloat
    shapeBatch.fillRoundedRect(barX - 2, barY - 2, barW + 4, barH + 4, 4f, 0.08f * bgPulse, 0.08f * bgPulse, 0.12f * bgPulse, 0.78f)
    shapeBatch.fillRoundedRect(barX, barY, barW, barH, 3f, 0.18f, 0.18f, 0.22f, 0.9f)

    val pct = chargeLevel / 100f
    // 3-stage color: GREEN (0-33%) -> YELLOW (34-66%) -> ORANGE-RED (67-100%)
    val cr = if (pct < 0.33f) 0.2f + pct * 2f
             else if (pct < 0.66f) 0.85f + (pct - 0.33f) * 0.45f
             else Math.min(1f, 1f)
    val cg = if (pct < 0.33f) 0.8f
             else if (pct < 0.66f) 0.8f - (pct - 0.33f) * 1.2f
             else Math.max(0f, 0.4f - (pct - 0.66f) * 1.2f)
    val cb = 0f

    // Fill bar
    shapeBatch.fillRoundedRect(barX, barY, barW * pct, barH, 3f, cr, cg, cb, 1f)
    // Top highlight (brighter)
    if (barW * pct > 2f) {
      shapeBatch.fillRect(barX + 1f, barY + 1f, barW * pct - 2f, 1.8f,
        Math.min(1f, cr + 0.3f), Math.min(1f, cg + 0.3f), Math.min(1f, cb + 0.3f), 0.4f)
    }

    // Threshold markers at 33% and 66%
    shapeBatch.fillRect(barX + barW * 0.33f - 0.5f, barY, 1f, barH, 0f, 0f, 0f, 0.3f)
    shapeBatch.fillRect(barX + barW * 0.66f - 0.5f, barY, 1f, barH, 0f, 0f, 0f, 0.3f)

    // Brief bright flash pulse at each 33% threshold crossing
    val threshFlash33 = Math.abs(pct - 0.33f)
    val threshFlash66 = Math.abs(pct - 0.66f)
    if (threshFlash33 < 0.03f) {
      val flashI = (1f - threshFlash33 / 0.03f) * 0.3f
      shapeBatch.fillRect(barX, barY - 1f, barW * 0.33f, barH + 2f, 1f, 1f, 0.5f, flashI)
    }
    if (threshFlash66 < 0.03f) {
      val flashI = (1f - threshFlash66 / 0.03f) * 0.3f
      shapeBatch.fillRect(barX, barY - 1f, barW * 0.66f, barH + 2f, 1f, 0.8f, 0.3f, flashI)
    }

    // Energy crackling along the bar (small lightning-like segments)
    { var seg = 0; while (seg < 5) {
      val segPhase = ((tick * 0.12f + seg * 20f) % barW)
      if (segPhase < barW * pct - 4f) {
        val segX = barX + segPhase
        val segY1 = barY + barH * 0.5f + Math.sin(tick * 0.3 + seg * 2.7).toFloat * 3f
        val segY2 = barY + barH * 0.5f - Math.sin(tick * 0.35 + seg * 1.9).toFloat * 3f
        val segX2 = segX + 4f + Math.sin(tick * 0.2 + seg).toFloat * 2f
        shapeBatch.strokeLine(segX, segY1, segX2, segY2, 1.2f,
          Math.min(1f, cr + 0.3f), Math.min(1f, cg + 0.3f), Math.min(1f, cb + 0.5f), 0.3f * pct)
      }
    ; seg += 1 } }

    shapeBatch.setAdditiveBlend(true)
    // Leading edge bright dot + soft glow trail
    val leadX = barX + barW * pct
    val leadY = barY + barH / 2
    shapeBatch.fillOvalSoft(leadX, leadY, 10f, 10f, cr, cg, 0.2f, 0.4f * pct, 0f, 12)
    shapeBatch.fillOval(leadX, leadY, 3f, 3f, 1f, 1f, 0.8f, 0.6f * pct, 8)
    // Glow trail behind leading edge
    shapeBatch.fillOvalSoft(leadX - 6f, leadY, 8f, 6f, cr, cg, 0.1f, 0.2f * pct, 0f, 8)

    // At 100%: dramatic pulsing glow border, 6+ spark particles, expanding ring effect
    if (chargeLevel >= 100) {
      val fullPulse = (0.5 + 0.5 * Math.sin(tick * 0.2)).toFloat
      // Pulsing glow border around entire bar
      shapeBatch.fillOvalSoft(barX + barW * 0.5f, barY + barH * 0.5f, barW * 0.58f, barH * 2.5f,
        cr, cg, 0.15f, 0.15f * fullPulse, 0f, 14)
      // Bright edge glow top and bottom
      shapeBatch.fillRect(barX - 1f, barY - 2f, barW + 2f, 1.5f, cr, cg, 0.2f, 0.2f * fullPulse)
      shapeBatch.fillRect(barX - 1f, barY + barH + 0.5f, barW + 2f, 1.5f, cr, cg, 0.2f, 0.2f * fullPulse)

      // 6+ spark particles emitting from bar
      { var s = 0; while (s < 6) {
        val sparkX = barX + ((tick * 0.6f + s * 17f) % barW)
        val sparkY = barY + barH * 0.5f + Math.sin(tick * 0.18 + s * 1.7).toFloat * 8f
        val sparkA = (0.4 + 0.3 * Math.sin(tick * 0.12 + s * 2.1)).toFloat * fullPulse
        shapeBatch.fillOval(sparkX, sparkY, 2.5f, 2.5f, 1f, 0.95f, 0.4f, sparkA, 4)
        // Spark trail
        val trailX = sparkX - 3f
        val trailY = sparkY + Math.sin(tick * 0.15 + s * 1.2).toFloat * 2f
        shapeBatch.strokeLineSoft(trailX, trailY, sparkX, sparkY, 1.5f, 1f, 0.8f, 0.3f, sparkA * 0.5f)
      ; s += 1 } }

      // Expanding ring effect at bar center
      val ringPhase = (tick * 0.04f) % 1f
      val ringR = 10f + ringPhase * 20f
      val ringAlpha = (1f - ringPhase) * 0.15f * fullPulse
      shapeBatch.strokeOval(barX + barW * 0.5f, barY + barH * 0.5f, ringR, ringR * 0.4f, 1.5f,
        cr, cg, 0.3f, ringAlpha, 14)
    }
    shapeBatch.setAdditiveBlend(false)

    beginSprites()
    if (chargeLevel != _cachedChargeLevel) { _cachedChargeLevel = chargeLevel; _cachedChargeStr = chargeLevel + "%" }
    fontSmall.drawText(spriteBatch, _cachedChargeStr, barX + barW / 2 - 12, barY - 16)
  }
}
