package com.gridgame.client.render
package hud

import com.gridgame.client.i18n.Messages

/** Practice's tallies: the combo, the best combo, accuracy, and a marker for every hit. */
private[render] final class PracticePainter(ctx: RenderContext) {
  import ctx._

  // Hit marker timestamps (small ring buffer for simultaneous kill markers)
  private val practiceHitMarkers = new Array[Long](8)
  private var practiceHitMarkerIdx = 0
  private var prevPracticeHits = 0

  def draw(screenW: Int, screenH: Int): Unit = {
    val now = frameTimeMs
    val cx = screenW / 2f
    val cy = screenH / 2f

    // Reset combo if 2 seconds since last hit
    if (client.practiceLastHitTime > 0 && now - client.practiceLastHitTime > 2000) {
      client.practiceCombo = 0
    }

    // Detect new kills for hit markers
    val currentHits = client.practiceHits
    if (currentHits > prevPracticeHits) {
      practiceHitMarkers(practiceHitMarkerIdx % practiceHitMarkers.length) = now
      practiceHitMarkerIdx += 1
      prevPracticeHits = currentHits
    }

    // --- Combo counter (center, below timer) ---
    val combo = client.practiceCombo
    if (combo > 0) {
      val comboText = Messages.t("COMBO x{0}", combo)
      val textW = fontMedium.measureWidth(comboText)

      // Color scales white -> yellow -> orange -> red with combo size
      val t = Math.min(1f, combo / 10f)
      val cr = 1f
      val cg = Math.max(0.2f, 1f - t * 0.8f)
      val cb = Math.max(0.1f, 1f - t)

      // Gentle pulse animation
      val pulse = 1f + 0.05f * Math.sin(now * 0.006).toFloat

      beginShapes()
      val comboW = textW + 24
      val comboH = 32f
      val comboX = cx - comboW / 2
      val comboY = 42f
      shapeBatch.fillRoundedRect(comboX, comboY, comboW * pulse, comboH, 6f, cr * 0.15f, cg * 0.15f, cb * 0.15f, 0.6f)

      beginSprites()
      fontMedium.drawText(spriteBatch, comboText, cx - textW / 2, comboY + 6, cr, cg, cb, 1f)
    }

    // --- Hit markers (screen center) ---
    beginShapes()
    var mi = 0
    while (mi < practiceHitMarkers.length) {
      val markerTime = practiceHitMarkers(mi)
      if (markerTime > 0) {
        val elapsed = now - markerTime
        if (elapsed < 400) {
          val alpha = (1f - elapsed / 400f) * 0.9f
          val size = 12f
          // Draw X marker (4 strokes)
          shapeBatch.strokeLine(cx - size, cy - size, cx + size, cy + size, 2f, 1f, 1f, 1f, alpha)
          shapeBatch.strokeLine(cx + size, cy - size, cx - size, cy + size, 2f, 1f, 1f, 1f, alpha)
        } else {
          practiceHitMarkers(mi) = 0
        }
      }
      mi += 1
    }

    // --- Accuracy display (top-left, below K/D) ---
    beginSprites()
    val accuracy = if (client.practiceShots > 0) (client.practiceHits * 100.0 / client.practiceShots).toInt else 0
    val accText = Messages.t("Accuracy: {0}%", accuracy)
    fontSmall.drawTextOutlined(spriteBatch, accText, 12, 126)
    val bestText = Messages.t("Best Combo: {0}", client.practiceBestCombo)
    fontSmall.drawTextOutlined(spriteBatch, bestText, 12, 144)
    // A session runs 30 minutes, so say how to end it sooner
    fontSmall.drawTextOutlined(spriteBatch, Messages.t("[Esc] End practice"), 12, 166, 0.6f, 0.65f, 0.7f, 0.8f)

    // --- "PRACTICE" label replacing timer ---
    val practiceText = Messages.t("PRACTICE")
    val ptw = fontMedium.measureWidth(practiceText)
    fontMedium.drawText(spriteBatch, practiceText, cx - ptw / 2, 8, 0.24f, 0.86f, 0.5f, 0.9f)
  }
}
