package com.gridgame.client.render
package hud

import com.gridgame.common.model._

/** The two ability slots: each one's icon, its cooldown sweeping round, a flash when it is ready
  * again, a trap ability's count, and a ceasefire's padlock. */
private[render] final class AbilityBarPainter(ctx: RenderContext) {
  import ctx._

  // Cooldown ready flash tracking (Q=0, E=1)
  private val prevCooldownReady = Array(true, true)
  private val cooldownReadyFlashTime = Array(0L, 0L)

  // Pre-allocated arrays for detailed HUD icons
  private val _iconXs = new Array[Float](12)
  private val _iconYs = new Array[Float](12)
  private val _iconXs2 = new Array[Float](8)
  private val _iconYs2 = new Array[Float](8)

  // The barrier icon's heater shield, in icon sizes from its centre: convex, clockwise from top left
  private val _shieldIconX = Array(-0.6f, 0.6f, 0.6f, 0.38f, 0f, -0.38f, -0.6f)
  private val _shieldIconY = Array(-0.65f, -0.65f, -0.05f, 0.48f, 0.8f, 0.48f, -0.05f)

  // Pre-unpacked ability colors (avoids Tuple3 allocation on each access)
  private val _abilityR = Array(0.2f, 0.4f)
  private val _abilityG = Array(0.8f, 0.7f)
  private val _abilityB = Array(0.3f, 1f)

  // Pre-allocated arrays for cooldown sweep polygon
  private val _sweepXs = new Array[Float](34)
  private val _sweepYs = new Array[Float](34)

  def draw(screenW: Int, screenH: Int): Unit = {
    val slotSize = 54f; val slotGap = 8f
    val invSlotSize = 54f; val invSlotGap = 8f; val invNumSlots = 5
    val totalInvW = invNumSlots * invSlotSize + (invNumSlots - 1) * invSlotGap
    val inventoryStartX = (screenW - totalInvW) / 2f
    val startY = screenH - slotSize - 14f

    val charDef = client.getSelectedCharacterDef
    val qAbility = charDef.qAbility; val eAbility = charDef.eAbility
    val qCooldown = client.getQCooldownFraction; val eCooldown = client.getECooldownFraction
    val numAbilities = 2

    val now = frameTimeMs
    val abilityGap = 12f
    // Held by a free-for-all's opening ceasefire (MatchOpening): the slots say so, and say for
    // how long, since nothing anyone presses attacks until it is over
    val locked = client.attacksLocked
    val lockSecs = if (locked) ((client.openingMsLeft + 999L) / 1000L).toInt else 0
    beginShapes()
    var i = 0
    while (i < numAbilities) {
      val aDef = if (i == 0) qAbility else eAbility
      val cooldownFrac = if (i == 0) qCooldown else eCooldown
      val ar = _abilityR(i); val ag = _abilityG(i); val ab = _abilityB(i)
      val slotX = inventoryStartX - (numAbilities - i) * (slotSize + slotGap) - abilityGap
      val onCooldown = cooldownFrac > 0.001f

      // Detect cooldown-to-ready transition for flash
      val isReady = !onCooldown
      if (isReady && !prevCooldownReady(i)) {
        cooldownReadyFlashTime(i) = now
      }
      prevCooldownReady(i) = isReady

      // Slot background (glassmorphism) with bright border
      shapeBatch.fillRoundedRect(slotX, startY, slotSize, slotSize, 5f, 0.08f, 0.08f, 0.14f, 0.75f)
      shapeBatch.strokeRect(slotX, startY, slotSize, slotSize, 1f, ar * 0.5f, ag * 0.5f, ab * 0.5f, 0.4f)
      shapeBatch.strokeLine(slotX + 4, startY, slotX + slotSize - 4, startY, 1f, 1f, 1f, 1f, 0.07f)

      // Ability icon inside the slot
      val cx = slotX + slotSize / 2; val cy = startY + slotSize / 2
      val iconAlpha = if (locked) 0.18f else if (onCooldown) 0.35f else 0.9f
      drawAbilityIcon(aDef.castBehavior, cx, cy, 17f, ar, ag, ab, iconAlpha)

      if (locked) {
        // Shuttered, with a padlock over it: this is not a cooldown, and nothing will start it
        shapeBatch.fillRoundedRect(slotX, startY, slotSize, slotSize, 5f, 0.02f, 0.02f, 0.05f, 0.62f)
        val lr = 1f; val lg = 0.78f; val lb = 0.3f
        val bodyW = 13f; val bodyH = 10f
        shapeBatch.strokeArc(cx, cy - 7f, 4.2f, 4.6f, Math.PI.toFloat, Math.PI.toFloat, 1.8f, lr, lg, lb, 0.9f, 10)
        shapeBatch.fillRoundedRect(cx - bodyW / 2, cy - 7f, bodyW, bodyH, 2f, lr, lg, lb, 0.85f)
        shapeBatch.fillOval(cx, cy - 2.4f, 1.5f, 1.6f, 0.1f, 0.06f, 0.02f, 0.9f, 6)
      }

      // Cooldown overlay — radial sweep (clock-wipe from 12 o'clock)
      if (locked) {
        // nothing: a holstered slot is shuttered, not counting down its own cooldown
      } else if (onCooldown) {
        val sweepAngle = cooldownFrac * Math.PI.toFloat * 2f
        val radius = slotSize * 0.72f
        val startAngle = -Math.PI.toFloat / 2f
        val steps = Math.max(4, (cooldownFrac * 24).toInt)
        _sweepXs(0) = cx; _sweepYs(0) = cy
        var s = 0
        while (s <= steps) {
          val angle = startAngle + (s.toFloat / steps) * sweepAngle
          _sweepXs(s + 1) = cx + Math.cos(angle).toFloat * radius
          _sweepYs(s + 1) = cy + Math.sin(angle).toFloat * radius
          s += 1
        }
        shapeBatch.fillPolygon(_sweepXs, _sweepYs, steps + 2, 0f, 0f, 0f, 0.55f)
        // Muted border when on cooldown
      } else {
        // No border stroke needed - glassmorphism panel is enough
        // Ready flash pulse (300ms glow when cooldown completes)
        val flashElapsed = now - cooldownReadyFlashTime(i)
        if (flashElapsed < 300 && cooldownReadyFlashTime(i) > 0) {
          val flashAlpha = (1f - flashElapsed / 300f) * 0.4f
          shapeBatch.setAdditiveBlend(true)
          shapeBatch.fillRoundedRect(slotX, startY, slotSize, slotSize, 5f, ar, ag, ab, flashAlpha)
          shapeBatch.setAdditiveBlend(false)
        }
      }
      i += 1
    }

    // Key labels, and for a trap the count of ours on the ground
    beginSprites()
    i = 0
    while (i < numAbilities) {
      val aDef = if (i == 0) qAbility else eAbility
      val slotX = inventoryStartX - (numAbilities - i) * (slotSize + slotGap) - abilityGap
      val ar = _abilityR(i); val ag = _abilityG(i); val ab = _abilityB(i)
      val onCooldown = (if (i == 0) qCooldown else eCooldown) > 0.001f
      val ka = if (locked) 0.35f else if (onCooldown) 0.5f else 1f
      fontSmall.drawTextOutlined(spriteBatch, aDef.keybind,
        slotX + slotSize - fontSmall.measureWidth(aDef.keybind) - 3, startY + slotSize - fontSmall.charHeight - 1,
        ar * ka, ag * ka, ab * ka, ka)
      if (locked) {
        // How long they stay holstered, under the padlock
        val secs = lockSecs.toString
        fontSmall.drawTextOutlined(spriteBatch, secs, slotX + slotSize / 2 - fontSmall.measureWidth(secs) / 2,
          startY + slotSize / 2 + 5f, 1f, 0.82f, 0.4f, 0.95f)
      }
      aDef.castBehavior match {
        case TrapCast(trapType, _) =>
          val tDef = TrapDef.get(trapType)
          val max = if (tDef != null) tDef.maxActive else 0
          val out = client.myTrapCount
          val text = trapCountText(out, max)
          // Full is worth noticing: the next one takes your oldest away
          val full = out >= max
          fontSmall.drawTextOutlined(spriteBatch, text, slotX + 3, startY + 2,
            if (full) 1f else ar, if (full) 0.75f else ag, if (full) 0.4f else ab, 0.95f)
        case _ =>
      }
      i += 1
    }
  }

  // "2/3" on a trap ability's slot, rebuilt only when it changes — the HUD is drawn every frame
  private var _trapCountText: String = ""
  private var _trapCountN = -1
  private var _trapCountMax = -1

  private def trapCountText(n: Int, max: Int): String = {
    if (n != _trapCountN || max != _trapCountMax) {
      _trapCountN = n; _trapCountMax = max
      _trapCountText = n + "/" + max
    }
    _trapCountText
  }

  /** Draw a detailed icon representing the cast behavior type. */
  private def drawAbilityIcon(behavior: CastBehavior, cx: Float, cy: Float, sz: Float,
                              r: Float, g: Float, b: Float, a: Float): Unit = {
    val t = animTickF
    behavior match {
      case StandardProjectile =>
        // Detailed arrow/bolt shape flying right with speed lines
        // Arrow shaft
        shapeBatch.fillRect(cx - sz * 0.6f, cy - sz * 0.12f, sz * 0.9f, sz * 0.24f, r, g, b, a * 0.85f)
        // Arrowhead triangle
        _iconXs(0) = cx + sz * 0.7f; _iconYs(0) = cy
        _iconXs(1) = cx + sz * 0.2f; _iconYs(1) = cy - sz * 0.4f
        _iconXs(2) = cx + sz * 0.2f; _iconYs(2) = cy + sz * 0.4f
        shapeBatch.fillPolygon(_iconXs, _iconYs, 3, r, g, b, a);
        // Bright core highlight on shaft
        shapeBatch.fillRect(cx - sz * 0.4f, cy - sz * 0.05f, sz * 0.6f, sz * 0.1f, clamp(r + 0.3f), clamp(g + 0.3f), clamp(b + 0.3f), a * 0.6f)
        // Fletching notches at tail
        shapeBatch.strokeLine(cx - sz * 0.55f, cy - sz * 0.12f, cx - sz * 0.7f, cy - sz * 0.3f, 1.5f, r, g, b, a * 0.7f)
        shapeBatch.strokeLine(cx - sz * 0.55f, cy + sz * 0.12f, cx - sz * 0.7f, cy + sz * 0.3f, 1.5f, r, g, b, a * 0.7f)
        // Speed lines trailing behind
        val sl = (Math.sin(t * 0.2) * 0.15 + 0.35).toFloat
        shapeBatch.strokeLine(cx - sz * 0.85f, cy - sz * 0.25f, cx - sz * (0.85f + sl), cy - sz * 0.25f, 1f, r, g, b, a * 0.35f)
        shapeBatch.strokeLine(cx - sz * 0.8f, cy, cx - sz * (0.8f + sl * 1.2f), cy, 1f, r, g, b, a * 0.4f)
        shapeBatch.strokeLine(cx - sz * 0.85f, cy + sz * 0.25f, cx - sz * (0.85f + sl), cy + sz * 0.25f, 1f, r, g, b, a * 0.35f)

      case FanProjectile(count, _) =>
        // Fan pattern with distinct diverging bolts
        val spread = Math.min(count, 7)
        // Origin circle at bottom center
        shapeBatch.fillOval(cx, cy + sz * 0.35f, sz * 0.18f, sz * 0.18f, r, g, b, a * 0.7f, 8);
        // Draw spread bolts diverging upward
        { var i = 0; while (i < spread) {
          val frac = if (spread > 1) (i.toFloat / (spread - 1)) - 0.5f else 0f
          val angle = -Math.PI / 2 + frac * Math.PI * 0.6
          val boltLen = sz * 0.75f
          val tipX = cx + (Math.cos(angle) * boltLen).toFloat
          val tipY = cy + sz * 0.35f + (Math.sin(angle) * boltLen).toFloat
          // Bolt line
          shapeBatch.strokeLine(cx, cy + sz * 0.35f, tipX, tipY, 1.8f, r, g, b, a * 0.8f)
          // Bolt tip dot
          shapeBatch.fillOval(tipX, tipY, sz * 0.12f, sz * 0.12f, r, g, b, a, 6)
        ; i += 1 } }
        // Arc at the spread width
        shapeBatch.strokeOval(cx, cy + sz * 0.35f, sz * 0.7f, sz * 0.7f, 1f, r, g, b, a * 0.2f, 16)

      case _: PhaseShiftBuff =>
        // Ghost/spirit with flowing wisps
        // Ghost body (rounded top, wavy bottom)
        shapeBatch.fillOval(cx, cy - sz * 0.15f, sz * 0.55f, sz * 0.6f, r, g, b, a * 0.45f, 14)
        // Inner brighter ghost core
        shapeBatch.fillOval(cx, cy - sz * 0.2f, sz * 0.38f, sz * 0.42f, r, g, b, a * 0.65f, 12)
        // Wavy bottom tendrils (3 wisps)
        val waveOff = (Math.sin(t * 0.15) * sz * 0.08).toFloat;
        { var i = 0; while (i < 3) {
          val wx = cx + (i - 1) * sz * 0.3f + waveOff * (if (i % 2 == 0) 1f else -1f)
          val wy = cy + sz * 0.35f
          shapeBatch.fillOvalSoft(wx, wy, sz * 0.15f, sz * 0.25f, r, g, b, a * 0.4f, 0f, 8)
        ; i += 1 } }
        // Eyes (bright, glowing)
        shapeBatch.fillOval(cx - sz * 0.18f, cy - sz * 0.22f, sz * 0.1f, sz * 0.12f, 1f, 1f, 1f, a * 0.9f, 6)
        shapeBatch.fillOval(cx + sz * 0.18f, cy - sz * 0.22f, sz * 0.1f, sz * 0.12f, 1f, 1f, 1f, a * 0.9f, 6)
        // Outer ethereal glow
        shapeBatch.fillOvalSoft(cx, cy - sz * 0.1f, sz * 0.8f, sz * 0.85f, r, g, b, a * 0.12f, 0f, 14)

      case _: DashBuff =>
        // Dynamic swoosh/dash trail with speed blur
        // Main swoosh curve — thick leading edge tapering to thin
        _iconXs(0) = cx + sz * 0.75f; _iconYs(0) = cy
        _iconXs(1) = cx + sz * 0.3f; _iconYs(1) = cy - sz * 0.35f
        _iconXs(2) = cx - sz * 0.5f; _iconYs(2) = cy - sz * 0.15f
        _iconXs(3) = cx - sz * 0.75f; _iconYs(3) = cy - sz * 0.05f
        _iconXs(4) = cx - sz * 0.5f; _iconYs(4) = cy + sz * 0.08f
        _iconXs(5) = cx + sz * 0.3f; _iconYs(5) = cy + sz * 0.35f
        shapeBatch.fillPolygon(_iconXs, _iconYs, 6, r, g, b, a * 0.8f)
        // Bright leading point
        shapeBatch.fillOval(cx + sz * 0.7f, cy, sz * 0.15f, sz * 0.15f, clamp(r + 0.3f), clamp(g + 0.3f), clamp(b + 0.3f), a, 8)
        // Speed motion lines behind the swoosh
        val dashPulse = (Math.sin(t * 0.2) * 0.15 + 0.5).toFloat;
        { var i = 0; while (i < 4) {
          val ly = cy + (i - 1.5f) * sz * 0.22f
          val lx0 = cx - sz * (0.5f + i * 0.08f)
          val lx1 = cx - sz * (0.85f + i * 0.05f)
          shapeBatch.strokeLine(lx0, ly, lx1, ly, 1.2f, r, g, b, a * dashPulse * (0.3f + i * 0.1f))
        ; i += 1 } }
        // Secondary lighter swoosh layer
        shapeBatch.strokeLine(cx - sz * 0.4f, cy - sz * 0.1f, cx + sz * 0.5f, cy, 1.5f, clamp(r + 0.2f), clamp(g + 0.2f), clamp(b + 0.2f), a * 0.4f)

      case _: TeleportCast =>
        // Portal ring with sparkle effect
        // Outer portal ring
        shapeBatch.strokeOval(cx, cy, sz * 0.7f, sz * 0.7f, 2.2f, r, g, b, a * 0.85f, 16)
        // Inner ring
        shapeBatch.strokeOval(cx, cy, sz * 0.45f, sz * 0.45f, 1.5f, r, g, b, a * 0.5f, 12)
        // Center glow
        shapeBatch.fillOvalSoft(cx, cy, sz * 0.3f, sz * 0.3f, clamp(r + 0.2f), clamp(g + 0.2f), clamp(b + 0.2f), a * 0.5f, 0f, 10);
        // Sparkle dots orbiting the ring
        { var i = 0; while (i < 4) {
          val sparkAngle = t * 0.12 + i * Math.PI / 2
          val sparkR = sz * 0.58f
          val sx = cx + (Math.cos(sparkAngle) * sparkR).toFloat
          val sy = cy + (Math.sin(sparkAngle) * sparkR).toFloat
          shapeBatch.fillOval(sx, sy, sz * 0.08f, sz * 0.08f, 1f, 1f, 1f, a * 0.8f, 6)
        ; i += 1 } }
        // Cross sparkle at center
        val sparkSz = sz * 0.15f * ((Math.sin(t * 0.18) * 0.3 + 0.7).toFloat)
        shapeBatch.strokeLine(cx - sparkSz, cy, cx + sparkSz, cy, 1.5f, 1f, 1f, 1f, a * 0.6f)
        shapeBatch.strokeLine(cx, cy - sparkSz, cx, cy + sparkSz, 1.5f, 1f, 1f, 1f, a * 0.6f)

      case _: GroundSlam =>
        // Impact crater with radiating cracks
        // Central impact point
        shapeBatch.fillOval(cx, cy, sz * 0.25f, sz * 0.16f, r, g, b, a, 10)
        // Inner bright flash
        shapeBatch.fillOvalSoft(cx, cy, sz * 0.18f, sz * 0.12f, clamp(r + 0.3f), clamp(g + 0.3f), clamp(b + 0.3f), a * 0.7f, 0f, 8)
        // Shockwave rings (isometric perspective)
        shapeBatch.strokeOval(cx, cy, sz * 0.5f, sz * 0.3f, 2f, r, g, b, a * 0.9f, 14)
        shapeBatch.strokeOval(cx, cy, sz * 0.85f, sz * 0.52f, 1.5f, r, g, b, a * 0.6f, 14)
        shapeBatch.strokeOval(cx, cy, sz * 1.15f, sz * 0.7f, 1f, r, g, b, a * 0.3f, 14);
        // Radiating crack lines from center
        { var i = 0; while (i < 6) {
          val crackAngle = i * Math.PI / 3 + Math.PI / 6
          val crackLen = sz * (0.55f + (i % 2) * 0.2f)
          val endX = cx + (Math.cos(crackAngle) * crackLen).toFloat
          val endY = cy + (Math.sin(crackAngle) * crackLen * 0.62f).toFloat // perspective squash
          shapeBatch.strokeLine(cx, cy, endX, endY, 1.2f, r, g, b, a * 0.5f)
        ; i += 1 } }
        // Debris particles floating upward
        { var i = 0; while (i < 3) {
          val dAngle = t * 0.1 + i * 2.1
          val dR = sz * 0.35f
          val dx = cx + (Math.cos(dAngle) * dR).toFloat
          val dy = cy - sz * 0.15f + (Math.sin(dAngle * 1.3) * sz * 0.12f).toFloat
          shapeBatch.fillOval(dx, dy, sz * 0.06f, sz * 0.06f, r, g, b, a * 0.5f, 4)
        ; i += 1 } }

      case TrapCast(trapType, _) =>
        val tDef = TrapDef.get(trapType)
        val glow = (Math.sin(t * 0.1) * 0.25 + 0.75).toFloat
        if (tDef != null && tDef.kind == TrapKind.MINE) {
          // A mine: a cased charge with a diode that blinks
          shapeBatch.fillOvalSoft(cx, cy, sz * 0.95f, sz * 0.8f, r, g, b, a * 0.16f * glow, 0f, 14)
          shapeBatch.fillOval(cx, cy + sz * 0.3f, sz * 0.62f, sz * 0.3f, r * 0.4f, g * 0.4f, b * 0.42f, a * 0.9f, 14)
          shapeBatch.fillOval(cx, cy, sz * 0.62f, sz * 0.34f, r * 0.62f, g * 0.58f, b * 0.56f, a, 14)
          shapeBatch.fillRect(cx - sz * 0.62f, cy + sz * 0.02f, sz * 1.24f, sz * 0.14f, r, g * 0.55f, b * 0.3f, a * 0.8f)
          shapeBatch.strokeOval(cx, cy, sz * 0.62f, sz * 0.34f, 1.2f, clamp(r + 0.2f), clamp(g + 0.2f), clamp(b + 0.2f), a * 0.8f, 14)
          val blink = if ((frameTimeMs % 900L) < 170L) 1f else 0.15f
          shapeBatch.fillOval(cx, cy - sz * 0.28f, sz * 0.13f, sz * 0.13f, 1f, 0.4f, 0.3f, a * (0.3f + 0.7f * blink), 8)
        } else {
          // Jaws standing open around a plate, seen from above
          shapeBatch.fillOvalSoft(cx, cy, sz * 0.95f, sz * 0.8f, r, g, b, a * 0.16f * glow, 0f, 14)
          shapeBatch.fillArcBand(cx, cy, sz * 0.5f, sz * 0.4f, sz * 0.8f, sz * 0.66f,
            Math.PI.toFloat, Math.PI.toFloat, 10, r, g, b, a * 0.95f, a * 0.95f)
          shapeBatch.fillArcBand(cx, cy, sz * 0.5f, sz * 0.4f, sz * 0.8f, sz * 0.66f,
            0f, Math.PI.toFloat, 10, r * 0.8f, g * 0.8f, b * 0.8f, a * 0.95f, a * 0.95f)
          var k = 0
          while (k < 8) {
            val ang = (Math.PI * (0.18 + 0.21 * (k % 4)) + (if (k < 4) Math.PI else 0.0)).toFloat
            val c = Math.cos(ang).toFloat
            val sn = Math.sin(ang).toFloat
            _iconXs(0) = cx + sz * 0.5f * c; _iconYs(0) = cy + sz * 0.4f * sn
            _iconXs(1) = cx + sz * 0.28f * c - sz * 0.1f * sn; _iconYs(1) = cy + sz * 0.22f * sn + sz * 0.08f * c
            _iconXs(2) = cx + sz * 0.28f * c + sz * 0.1f * sn; _iconYs(2) = cy + sz * 0.22f * sn - sz * 0.08f * c
            shapeBatch.fillPolygon(_iconXs, _iconYs, 3, 0.95f, 0.96f, 0.98f, a * 0.9f)
            k += 1
          }
          shapeBatch.fillOval(cx, cy, sz * 0.26f, sz * 0.2f, r * 0.8f, g * 0.75f, b * 0.6f, a, 10)
        }

      case _: BarrierCast =>
        // A heater shield: flat top, straight sides curving in to a point
        val glow = (Math.sin(t * 0.12) * 0.25 + 0.75).toFloat
        shapeBatch.fillOvalSoft(cx, cy, sz * 0.95f, sz * 0.95f, r, g, b, a * 0.18f * glow, 0f, 16)
        var i = 0
        while (i < 7) {
          val ox = _shieldIconX(i); val oy = _shieldIconY(i)
          _iconXs(i) = cx + ox * sz; _iconYs(i) = cy + oy * sz
          _iconXs2(i) = cx + ox * sz * 0.72f; _iconYs2(i) = cy - sz * 0.03f + oy * sz * 0.72f
          i += 1
        }
        shapeBatch.fillPolygon(_iconXs, _iconYs, 7, r, g, b, a * 0.85f)
        // Its face, a shade darker inside the rim
        shapeBatch.fillPolygon(_iconXs2, _iconYs2, 7, r * 0.45f, g * 0.45f, b * 0.45f, a * 0.9f)
        // A band of light across the face, and the boss at its centre
        shapeBatch.fillRect(cx - sz * 0.42f, cy - sz * 0.2f, sz * 0.84f, sz * 0.14f, r, g, b, a * 0.55f * glow)
        shapeBatch.fillOval(cx, cy - sz * 0.13f, sz * 0.14f, sz * 0.14f, clamp(r + 0.3f), clamp(g + 0.3f), clamp(b + 0.3f), a, 10)
        // Rim, with a highlight down the lit (left) side
        shapeBatch.strokePolygon(_iconXs, _iconYs, 7, 1.4f, clamp(r + 0.25f), clamp(g + 0.25f), clamp(b + 0.25f), a * 0.9f)
        shapeBatch.strokeLine(cx - sz * 0.48f, cy - sz * 0.52f, cx - sz * 0.48f, cy - sz * 0.08f, 1.5f, 1f, 1f, 1f, a * 0.35f)
    }
  }

  def reset(): Unit = {
    prevCooldownReady(0) = true; prevCooldownReady(1) = true
    cooldownReadyFlashTime(0) = 0L; cooldownReadyFlashTime(1) = 0L
  }
}
