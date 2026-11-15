package com.gridgame.client.render
package hud

import com.gridgame.client.i18n.Messages

import com.gridgame.client.game.{ClientState, KillFeedEntry, MatchProgress}
import com.gridgame.client.gl.DisplayFont.Face
import com.gridgame.common.Constants

/**
 * The match's information along the top, in the menus' dark glass (hud/HudStyle): the clock, our
 * score (kills, deaths, and kills less deaths, what the match is won on) with our run of kills in a
 * row beside it while there is one, the opening's countdown (and FIGHT! as it ends), and the kill
 * feed down the right, every
 * kill a row of who killed whom with their characters' faces. In practice its tallies take the score's
 * place, the very three its results show (PracticePainter has the rest of practice's HUD).
 */
private[render] final class MatchInfoPainter(ctx: RenderContext) {
  import ctx._
  import HudStyle._

  private val style = new HudStyle(ctx)

  // Words and numbers, each made once and kept: the HUD is drawn every frame
  private var sKills, sDeaths, sScore, sBest, sAccuracy, sInARow: String = _
  private def words(): Unit = if (sKills == null) {
    sKills = Messages.t("KILLS"); sDeaths = Messages.t("DEATHS"); sScore = Messages.t("SCORE")
    sBest = Messages.t("BEST COMBO"); sAccuracy = Messages.t("ACCURACY"); sInARow = Messages.t("IN A ROW")
  }
  private val plain = new Array[String](1000)
  private val signed = new Array[String](1999)
  private val percents = new Array[String](101)
  private def number(n: Int): String = {
    val i = Math.max(0, Math.min(999, n))
    if (plain(i) == null) plain(i) = i.toString
    plain(i)
  }
  /** A score as the scoreboard signs it: +4, 0, −2 (a minus as wide as the plus). */
  private def signedNumber(n: Int): String = {
    val i = Math.max(-999, Math.min(999, n)) + 999
    if (signed(i) == null) {
      val v = i - 999
      signed(i) = if (v > 0) "+" + v else if (v < 0) "−" + (-v) else "0"
    }
    signed(i)
  }
  private def percent(p: Int): String = {
    val i = Math.max(0, Math.min(100, p))
    if (percents(i) == null) percents(i) = i + "%"
    percents(i)
  }

  // The clock's text, made again only when a second goes
  private var _clockFor = -1
  private var _clockStr = ""

  // The score panel's columns this frame: label, value, colour
  private val colLabel = new Array[String](3)
  private val colValue = new Array[String](3)
  private val colR = new Array[Float](3); private val colG = new Array[Float](3); private val colB = new Array[Float](3)
  private val colW = new Array[Float](3)

  def draw(screenW: Int, screenH: Int): Unit = {
    if (client.clientState != ClientState.PLAYING) return
    words()
    if (!client.isPracticeMode) drawClock(screenW)
    drawCountdown(screenW, screenH)
    val right = drawScore()
    drawStreak(right + 10f, screenW)
    drawFeed(screenW)
  }

  // ── The clock ──────────────────────────────────────────────────────────

  private def drawClock(screenW: Int): Unit = {
    val remaining = client.gameTimeRemaining
    if (remaining != _clockFor) {
      _clockFor = remaining
      val m = remaining / 60; val s = remaining % 60
      _clockStr = m + (if (s < 10) ":0" else ":") + s
    }
    val low = remaining > 0 && remaining <= 30
    val beat = if (low) (Math.sin(frameTimeMs * 0.008).toFloat + 1f) * 0.5f else 0f
    val textW = fontNumber.measureWidth(_clockStr)
    val h = 42f
    val w = Math.round(textW + 62f).toFloat
    val x = Math.round(screenW / 2f - w / 2f).toFloat
    val y = 12f
    if (low) style.tintedGlass(x, y, w, h, h / 2f, BerryR, BerryG, BerryB, 0.75f + 0.25f * beat)
    else style.glass(x, y, w, h, h / 2f)
    // A clock face: a ring with its two hands, the minute hand going round with the seconds
    val ccx = x + 24f; val ccy = y + h / 2f
    val ir = if (low) BerryR else MutedR; val ig = if (low) BerryG else MutedG; val ib = if (low) BerryB else MutedB
    shapeBatch.strokeOvalSmooth(ccx, ccy, 8.5f, 8.5f, 2f, ir, ig, ib, 0.95f)
    val ang = (remaining % 60) * Math.PI / 30 - Math.PI / 2
    shapeBatch.strokeLine(ccx, ccy, ccx + (Math.cos(ang) * 5.6).toFloat, ccy + (Math.sin(ang) * 5.6).toFloat, 1.6f, ir, ig, ib, 0.95f)
    shapeBatch.strokeLine(ccx, ccy, ccx, ccy - 4f, 1.8f, ir, ig, ib, 0.95f)
    beginSprites()
    val tr = if (low) 1f else TextR; val tg = if (low) 0.55f + 0.2f * (1f - beat) else TextG; val tb = if (low) 0.6f else TextB
    fontNumber.drawText(spriteBatch, _clockStr, x + 40f, fontNumber.digitsCentredOn(ccy), tr, tg, tb, 1f)
  }

  // ── The opening (design/match-flow.md) ─────────────────────────────────

  // How long FIGHT! stays up once the opening is over
  private val GoMs = 1400f
  private var _openingSecs = -1
  private var _openingStr = ""
  private var sFight: String = _

  /**
   * Under the clock while the match opens: how long is left of it, on a pane rimmed in gold with a bar
   * emptying along its foot, beating in its last three seconds; and as it ends, FIGHT! lettered big
   * where a kill is announced. It says the same thing whichever opening it is: behind a wall or holding
   * your fire, the battle starts when the countdown does.
   */
  private def drawCountdown(screenW: Int, screenH: Int): Unit = {
    val rules = client.openingRulesNow
    if (rules == 0) return
    val now = frameTimeMs
    val left = client.openingMsLeft
    val since = (now - client.openingEndsAtMs).toFloat
    if (left > 0L) {
      val secs = ((left + 999L) / 1000L).toInt
      if (secs != _openingSecs) {
        _openingSecs = secs
        _openingStr = Messages.t("Battle begins in {0}", secs.toString)
      }
      val beat = if (left < 3000L) (Math.sin(now * 0.012).toFloat + 1f) * 0.5f else 0f
      val textW = fontBody.measureWidth(_openingStr)
      val h = 36f
      val w = Math.round(textW + 44f).toFloat
      val x = Math.round(screenW / 2f - w / 2f).toFloat
      val y = 62f
      style.tintedGlass(x, y, w, h, h / 2f, GoldR, GoldG, GoldB, 0.75f + 0.25f * beat)
      val frac = Math.max(0f, Math.min(1f, left.toFloat / Constants.MATCH_OPENING_MS))
      shapeBatch.fillRoundedRectSmooth(x + h / 2f, y + h - 5f, (w - h) * frac, 2.5f, 1.25f,
        GoldR, GoldG, GoldB, 0.95f, GoldR, GoldG, GoldB, 0.95f)
      beginSprites()
      fontBody.drawText(spriteBatch, _openingStr, x + 22f, fontBody.digitsCentredOn(y + h / 2f - 1f),
        GoldTextR, GoldTextG, GoldTextB, 1f)
    } else if (since >= 0f && since <= GoMs) {
      if (sFight == null) { sFight = Messages.t("FIGHT!"); display.prewarm(sFight) }
      val a = Math.min(1f, since / 60f) * Math.min(1f, (GoMs - since) / 350f)
      val s = KillBannerPainter.pop(since, 1.7f) * 1.15f
      val cy = Math.round(Math.max(112f, screenH * 0.2f)).toFloat
      beginShapes()
      shapeBatch.setAdditiveBlend(true)
      shapeBatch.fillOvalSoft(screenW / 2f, cy, 230f, 74f, GoldR, GoldG, GoldB, 0.3f * a * (1f - since / GoMs * 0.5f), 0f, 40)
      shapeBatch.setAdditiveBlend(false)
      beginSprites()
      display.drawCentred(spriteBatch, sFight, screenW / 2f, cy, Face.Gold, a, s)
    }
  }

  // ── Our score, or practice's tallies ───────────────────────────────────

  /** Draws the panel and returns its right edge. */
  private def drawScore(): Float = {
    if (client.isPracticeMode) {
      val shots = client.practiceShots
      val acc = if (shots > 0) (client.practiceHits * 100L / shots).toInt else 0
      column(0, sKills, number(client.practiceHits), LeafR, LeafG, LeafB)
      column(1, sBest, number(client.practiceBestCombo), GoldTextR, GoldTextG, GoldTextB)
      column(2, sAccuracy, percent(acc), SkyR, SkyG, SkyB)
    } else {
      val k = client.killCount; val d = client.deathCount
      column(0, sKills, number(k), LeafR, LeafG, LeafB)
      column(1, sDeaths, number(d), BerryR, BerryG, BerryB)
      column(2, sScore, signedNumber(k - d), TextR, TextG, TextB)
    }
    val padX = 16f
    // The scoreboard Tab holds up starts below this row (ScoreboardPainter's TopClear, 84 units from the
    // top, under its 74): a taller panel moves that too
    val x = 16f; val y = 12f; val h = 62f
    var w = padX * 2
    var i = 0
    while (i < 3) {
      colW(i) = Math.max(56f, Math.max(fontTiny.measureWidth(colLabel(i)), fontNumber.measureWidth(colValue(i))) + 4f)
      w += colW(i) + (if (i > 0) 20f else 0f)
      i += 1
    }
    w = Math.round(w).toFloat
    style.glass(x, y, w, h, 14f)
    // Hairlines between the columns
    var cx = x + padX
    i = 0
    while (i < 3) {
      if (i > 0) {
        shapeBatch.fillRect(Math.round(cx - 10f).toFloat, y + 14f, 1f, h - 28f, 1f, 1f, 1f, 0.08f)
      }
      cx += colW(i) + 20f
      i += 1
    }
    beginSprites()
    cx = x + padX
    i = 0
    while (i < 3) {
      fontTiny.drawText(spriteBatch, colLabel(i), cx, y + 9f, MutedR, MutedG, MutedB, 1f)
      fontNumber.drawText(spriteBatch, colValue(i), cx, fontNumber.digitsCentredOn(y + 40f), colR(i), colG(i), colB(i), 1f)
      cx += colW(i) + 20f
      i += 1
    }
    x + w
  }

  private def column(i: Int, label: String, value: String, r: Float, g: Float, b: Float): Unit = {
    colLabel(i) = label; colValue(i) = value; colR(i) = r; colG(i) = g; colB(i) = b
  }

  // ── Our run of kills in a row ──────────────────────────────────────────

  /**
   * While we have two or more kills in a row: a chip beside the score with the count, in the colour
   * the banner gave it, so the run stays on screen after its banner has gone (render/CLAUDE.md, *A kill
   * is announced*). In practice, where a run ends when the next kill is too long coming, a bar along its
   * foot runs out with the time left for one.
   */
  private def drawStreak(besideScore: Float, screenW: Int): Unit = {
    val now = frameTimeMs
    val run = client.streakAt(now)
    if (run < 2) return
    val k = client.lastKill
    val since = if (k != null) (now - k.atMs).toFloat else 1e9f
    // It comes in as the banner lands, and bumps with every kill
    val bump = if (since < 260f) 1f + 0.18f * (1f - since / 260f) else 1f
    val face = KillBannerPainter.faceFor(run)
    val count = countText(run)
    val textW = fontNumber.measureWidth(count)
    val labelW = fontTiny.measureWidth(sInARow)
    val h = 62f
    val badge = 17f
    val w = Math.round(14f + badge * 2 + 12f + Math.max(textW * 1.18f, labelW) + 16f).toFloat
    // Beside the score, unless that would run into the clock (or practice's pill) on a narrow window:
    // then under it, and under practice's key
    val roomy = besideScore + w <= screenW / 2f - 135f
    val x = if (roomy) besideScore else 16f
    val y = if (roomy) 12f else 12f + 62f + 8f + (if (client.isPracticeMode) 38f else 0f)
    style.glass(x, y, w, h, 14f)
    shapeBatch.strokeRoundedRectSmooth(x, y, w, h, 14f, 1.5f, face.midR, face.midG, face.midB, 0.85f, 0.45f)
    // A flame in a round badge of the run's colour, lit from above as the banner's letters are
    val bx = x + 14f + badge; val by = y + h / 2f
    shapeBatch.fillOvalSmooth(bx, by, (badge + 1.5f) * bump, (badge + 1.5f) * bump, face.inkR, face.inkG, face.inkB, 0.9f)
    shapeBatch.fillOvalSmooth(bx, by, badge * bump, badge * bump, face.midR, face.midG, face.midB, 1f)
    shapeBatch.fillOvalSmooth(bx, by - badge * 0.35f * bump, badge * 0.7f * bump, badge * 0.45f * bump,
      face.topR, face.topG, face.topB, 0.55f)
    flame(bx, by + 1f, 21f * bump, face.inkR, face.inkG, face.inkB, face.topR, face.topG, face.topB)
    if (client.isPracticeMode && k != null) {
      // What is left of the time for the next kill to keep the run going
      val left = Math.max(0f, 1f - since / MatchProgress.PracticeComboMs.toFloat)
      val bx0 = x + 14f + badge * 2 + 12f
      val bw = (w - (bx0 - x) - 16f) * left
      shapeBatch.fillRoundedRectSmooth(bx0, y + h - 11f, w - (bx0 - x) - 16f, 4f, 2f, WellR, WellG, WellB, 1f, WellR, WellG, WellB, 1f)
      if (bw > 0f) shapeBatch.fillRoundedRectSmooth(bx0, y + h - 11f, Math.max(4f, bw), 4f, 2f,
        face.topR, face.topG, face.topB, 1f, face.midR, face.midG, face.midB, 1f)
    }
    beginSprites()
    val tx = x + 14f + badge * 2 + 12f
    fontTiny.drawText(spriteBatch, sInARow, tx, y + 9f, MutedR, MutedG, MutedB, 1f)
    fontNumber.drawTextOutlined(spriteBatch, count, tx, fontNumber.digitsCentredOn(y + (if (client.isPracticeMode) 37f else 40f)),
      face.midR, face.midG, face.midB, 1f, bump)
  }

  // A flame's outline, clockwise from its tip, as fractions of its height round its middle: a tongue
  // leaning a little, a lick off its right side, and a round foot
  private val FlameX = Array(0.08f, 0.2f, 0.36f, 0.33f, 0.44f, 0.4f, 0.24f, 0f, -0.24f, -0.4f, -0.42f, -0.3f, -0.12f)
  private val FlameY = Array(-0.62f, -0.36f, -0.3f, -0.1f, 0.1f, 0.3f, 0.47f, 0.52f, 0.47f, 0.3f, 0.06f, -0.16f, -0.34f)
  private val _flameXs = new Array[Float](FlameX.length)
  private val _flameYs = new Array[Float](FlameX.length)

  /** A flame `s` tall on its middle (cx, cy), in `r`, `g`, `b`, with a smaller one inside it in the
    * pale of the run's colour, so it reads as fire and not a drop. */
  private def flame(cx: Float, cy: Float, s: Float, r: Float, g: Float, b: Float, inR: Float, inG: Float, inB: Float): Unit = {
    fan(cx, cy, s, r, g, b)
    fan(cx, cy + 0.2f * s, s * 0.5f, inR, inG, inB)
  }

  private def fan(cx: Float, cy: Float, s: Float, r: Float, g: Float, b: Float): Unit = {
    val n = FlameX.length
    var i = 0
    while (i < n) { _flameXs(i) = cx + FlameX(i) * s; _flameYs(i) = cy + FlameY(i) * s; i += 1 }
    shapeBatch.fillFan(cx, cy + 0.12f * s, _flameXs, _flameYs, n, r, g, b, 0.95f)
  }

  private val counts = new Array[String](1000)
  private def countText(n: Int): String = {
    val i = Math.max(0, Math.min(999, n))
    if (counts(i) == null) counts(i) = KillBannerPainter.Times + i
    counts(i)
  }

  // ── The kill feed ──────────────────────────────────────────────────────

  private val FeedShowMs = 7000L
  private val FeedOutMs = 600L
  private val FeedInMs = 220L

  /**
   * The last kills down the right, newest at the foot: each a pane with who killed whom, each by the
   * face and name of the character they play, ours on gold and our death on berry. A row slides in, and
   * when its time is up fades and closes up, so those under it move up rather than jump.
   */
  // The rows this frame, laid out as their panes are drawn and their names drawn after them all: the
  // faces and the names come from two textures, and drawn row by row each change was a draw
  private val MaxRows = 8
  private val rowEntry = new Array[KillFeedEntry](MaxRows)
  private val rowX = new Array[Float](MaxRows)
  private val rowY = new Array[Float](MaxRows)
  private val rowA = new Array[Float](MaxRows)
  private val rowKillerW = new Array[Float](MaxRows)

  private def drawFeed(screenW: Int): Unit = {
    val now = frameTimeMs
    val right = screenW - 16f
    var y = 12f
    val rowH = 38f
    val bust = 28f
    val teams = client.currentLobbyGameMode == 1
    var n = 0
    val it = client.killFeed.iterator()
    while (it.hasNext && n < MaxRows) {
      val e = it.next()
      val age = now - e.atMs
      if (age >= 0 && age < FeedShowMs) {
        val inP = Math.min(1f, age / FeedInMs.toFloat)
        val ease = 1f - (1f - inP) * (1f - inP) * (1f - inP)
        val out = if (age > FeedShowMs - FeedOutMs) (FeedShowMs - age) / FeedOutMs.toFloat else 1f
        val a = Math.min(1f, inP * 2f) * out
        val slide = (1f - ease) * 48f
        val killerW = fontBody.measureWidth(e.killerName)
        val victimW = fontBody.measureWidth(e.victimName)
        val w = Math.round(8f + bust + 8f + killerW + 14f + 18f + 14f + bust + 8f + victimW + 14f).toFloat
        val x = Math.round(right - w + slide).toFloat
        if (e.byUs) style.tintedGlass(x, y, w, rowH, 12f, GoldR, GoldG, GoldB, a)
        else if (e.onUs) style.tintedGlass(x, y, w, rowH, 12f, BerryR, BerryG, BerryB, a)
        else style.glass(x, y, w, rowH, 12f, a)
        // The newest flashes as it comes in
        if (age < 320) {
          val f = 1f - age / 320f
          shapeBatch.setAdditiveBlend(true)
          shapeBatch.fillRoundedRectSmooth(x, y, w, rowH, 12f, 1f, 1f, 1f, 0.16f * f * a, 1f, 1f, 1f, 0.06f * f * a)
          shapeBatch.setAdditiveBlend(false)
        }
        val by = y + (rowH - bust) / 2f
        val kx = x + 8f
        val vx = kx + bust + 8f + killerW + 14f + 18f + 14f
        style.well(kx, by, bust, bust, 8f, a)
        style.bust(e.killerCharacter, kx, by, bust, a)
        skull(kx + bust + 8f + killerW + 14f + 9f, y + rowH / 2f, a)
        style.well(vx, by, bust, bust, 8f, a)
        style.bust(e.victimCharacter, vx, by, bust, a)
        rowEntry(n) = e; rowX(n) = x; rowY(n) = y; rowA(n) = a; rowKillerW(n) = killerW
        n += 1
        // Closing up as it goes
        val keep = if (out < 1f) Math.max(0f, out * 1.6f - 0.6f) else 1f
        y += (rowH + 6f) * keep
      }
    }
    // Every name after every face
    beginSprites()
    var i = 0
    while (i < n) {
      val e = rowEntry(i)
      val ty = fontBody.digitsCentredOn(rowY(i) + rowH / 2f)
      val kx = rowX(i) + 8f + bust + 8f
      nameColour(e.byUs, e.killerTeam, teams)
      fontBody.drawText(spriteBatch, e.killerName, kx, ty, _nr, _ng, _nb, rowA(i))
      nameColour(e.onUs, e.victimTeam, teams)
      fontBody.drawText(spriteBatch, e.victimName, kx + rowKillerW(i) + 14f + 18f + 14f + bust + 8f, ty, _nr, _ng, _nb, rowA(i))
      rowEntry(i) = null
      i += 1
    }
  }

  private var _nr = 0f; private var _ng = 0f; private var _nb = 0f
  /** A name's colour in the feed: ours gold, a team's its colour in Teams, everyone else's white. */
  private def nameColour(us: Boolean, team: Byte, teams: Boolean): Unit = {
    if (us) { _nr = GoldTextR; _ng = GoldTextG; _nb = GoldTextB }
    else if (teams && team == 1) { _nr = 0.43f; _ng = 0.69f; _nb = 1f }
    else if (teams && team == 2) { _nr = 1f; _ng = 0.45f; _nb = 0.5f }
    else { _nr = TextR; _ng = TextG; _nb = TextB }
  }

  /** A skull: "killed", between the killer and whom they killed. */
  private def skull(cx: Float, cy: Float, a: Float): Unit = {
    beginShapes()
    // The cranium and the jaw under it, in the pale of the text
    shapeBatch.fillOvalSmooth(cx, cy - 1.8f, 8f, 7.4f, BodyR, BodyG, BodyB, a)
    shapeBatch.fillRoundedRectSmooth(cx - 4.6f, cy + 2.2f, 9.2f, 6f, 2f, BodyR, BodyG, BodyB, a, BodyR, BodyG, BodyB, a)
    // Its eyes and nose, and the teeth, in the glass's dark
    shapeBatch.fillOvalSmooth(cx - 3.1f, cy - 1.2f, 2.3f, 2.5f, WellR, WellG, WellB, a)
    shapeBatch.fillOvalSmooth(cx + 3.1f, cy - 1.2f, 2.3f, 2.5f, WellR, WellG, WellB, a)
    shapeBatch.fillOvalSmooth(cx, cy + 2.6f, 0.9f, 1.2f, WellR, WellG, WellB, a)
    shapeBatch.fillRect(cx - 1.75f, cy + 5.2f, 0.9f, 3f, WellR, WellG, WellB, a)
    shapeBatch.fillRect(cx + 0.85f, cy + 5.2f, 0.9f, 3f, WellR, WellG, WellB, a)
  }
}
