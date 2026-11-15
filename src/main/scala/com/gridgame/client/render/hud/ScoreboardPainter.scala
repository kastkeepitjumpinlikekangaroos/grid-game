package com.gridgame.client.render
package hud

import com.gridgame.client.i18n.{I18n, Messages}
import com.gridgame.common.Constants
import com.gridgame.common.model.{CharacterDef, Direction}

/**
 * The scoreboard Tab holds up in a match (a controller's Back), over most of the screen: everyone in
 * it with the character they play, their kills, deaths and score (kills less deaths, what the match is
 * won on), placed as the match's end will place them (LiveStandings), in a section a team in Teams;
 * who is dead just now, who has left; and along the top the mode, the map, the time left and our ping.
 * In practice, its tallies along the bottom. The match goes on under it, and the game can be played
 * while it is up.
 *
 * Drawn in the window's units, over the HUD, in the menus' dark glass. Allocates nothing as it draws:
 * the standings are rebuilt in place when a kill, a death, a join or a leave changes them (and once a
 * second for the rest), and every number and name it shows is made once and kept.
 */
private[render] final class ScoreboardPainter(ctx: RenderContext) {
  import ctx._

  private val standings = new LiveStandings()
  private var builtAtChange = -1
  private var builtWithPlayers = -1
  private var builtWithDeparted = -1
  private var builtAtMs = 0L
  private var shownLastFrame = false

  // Words made once (a match's language doesn't change while it is played)
  private var wordsMade = false
  private var sTitle, sRank, sPlayer, sKills, sDeaths, sScore, sYou, sLeft, sDead, sLeading, sTied, sFfa, sTeams,
    sPractice, sTeam1, sTeam2: String = _
  private def makeWords(): Unit = {
    if (wordsMade) return
    val upper = (s: String) => s.toUpperCase(Messages.currentLocale)
    sTitle = upper(Messages.t("Scoreboard"))
    sRank = Messages.t("RANK"); sPlayer = Messages.t("PLAYER"); sKills = Messages.t("KILLS")
    sDeaths = Messages.t("DEATHS"); sScore = Messages.t("SCORE")
    sYou = Messages.t("(you)"); sLeft = Messages.t("(left)"); sDead = Messages.t("Respawning")
    sLeading = upper(Messages.t("Leading")); sTied = upper(Messages.t("Tied"))
    sFfa = Messages.t("Free-for-all"); sTeams = Messages.t("Teams"); sPractice = Messages.t("Practice")
    sTeam1 = Messages.t("Team 1 (Blue)"); sTeam2 = Messages.t("Team 2 (Red)")
    wordsMade = true
  }

  // Names and numbers, each made the first time it is shown and kept
  private val characterNames = new Array[String](256)
  private def characterName(id: Byte): String = {
    val i = id & 0xFF
    var n = characterNames(i)
    if (n == null) {
      n = try I18n.characterName(CharacterDef.get(id)) catch { case _: Exception => "?" }
      characterNames(i) = n
    }
    n
  }
  private val numbers = new Array[String](2001)
  private val signedNumbers = new Array[String](2001)
  /** n as shown in a column: plain, or signed as a score is (+4, 0, −2, a minus as wide as the plus). */
  private def number(n: Int, signed: Boolean): String = {
    val i = Math.max(-1000, Math.min(1000, n)) + 1000
    val cache = if (signed) signedNumbers else numbers
    var s = cache(i)
    if (s == null) {
      val v = i - 1000
      s = if (!signed) v.toString else if (v > 0) s"+$v" else if (v < 0) s"−${-v}" else "0"
      cache(i) = s
    }
    s
  }
  private val teamNames = new Array[String](256)
  private def teamName(team: Byte): String = team match {
    case 1 => sTeam1
    case 2 => sTeam2
    case t =>
      val i = t & 0xFF
      if (teamNames(i) == null) teamNames(i) = Messages.t("Team {0}", t.toString)
      teamNames(i)
  }
  private val teamKillWords = new Array[String](1001)
  private def teamKillsText(k: Int): String = {
    val i = Math.max(0, Math.min(1000, k))
    if (teamKillWords(i) == null) teamKillWords(i) = Messages.t("{0} kills", i.toString)
    teamKillWords(i)
  }
  // The header's: the mode and the map, the clock, the ping, practice's tallies (made again as they change)
  private var headline = ""; private var headlineFor: String = null; private var headlineMode = -1
  private var clockText = ""; private var clockFor = -1
  private var pingText = ""; private var pingFor = Int.MinValue
  private var accuracyText = ""; private var accuracyFor = -1
  private var comboText = ""; private var comboFor = -1

  // The rows' places on the screen this frame, and the team sections' headings
  private val rowY = new Array[Float](standings.capacity)
  private var rowsShown = 0
  private val sectionY = new Array[Float](LiveStandings.MaxTeams)
  private val sectionTeam = new Array[Int](LiveStandings.MaxTeams)
  private var sectionCount = 0

  // How far down from the top, and up from the foot, the HUD's own rows reach, in the window's units
  private final val TopClear = 84f
  private final val BottomClear = 100f

  // The palette, the menus' own (ui/theme.css): text, body, muted, gold; the columns' green and red are
  // the end-of-match scoreboard's (text-leaf, text-berry)
  private final val InkR = 0.933f; private final val InkG = 0.949f; private final val InkB = 1.0f
  private final val BodyR = 0.761f; private final val BodyG = 0.792f; private final val BodyB = 0.894f
  private final val MutedR = 0.545f; private final val MutedG = 0.588f; private final val MutedB = 0.733f
  private final val GoldR = 1.0f; private final val GoldG = 0.788f; private final val GoldB = 0.247f

  /** Hold it up this frame, or not. Rebuilt the frame it comes up, so it never shows stale places. */
  def draw(shown: Boolean, screenW: Int, screenH: Int): Unit = {
    if (!shown) { shownLastFrame = false; return }
    makeWords()
    rebuildIfChanged()
    shownLastFrame = true

    val sw = screenW.toFloat; val sh = screenH.toFloat
    // Over most of the screen, but clear of the HUD's top row (the clock, our kills and deaths) and its
    // bar along the bottom (abilities, items), which half under its card read as clutter
    val px = Math.max(20f, Math.round(sw * 0.08f).toFloat)
    val py = Math.max(TopClear, Math.round(sh * 0.07f).toFloat)
    val pw = sw - px * 2
    val ph = sh - py - Math.max(BottomClear, Math.round(sh * 0.07f).toFloat)
    val tw = Math.min(pw - 64f, 1180f)
    val tx = Math.round(px + (pw - tw) / 2).toFloat
    val practice = client.isPracticeMode
    val footerH = if (practice) 44f else 0f
    val headTop = py + 22f
    val ruleY = py + 92f
    val columnsMidY = ruleY + 20f
    val rowsTop = ruleY + 36f
    val rowsBottom = py + ph - 18f - footerH

    // Where each row and each team's heading goes
    val count = standings.count
    val sections = if (standings.teamMode) standings.teamCount else 0
    val rowH = Math.max(30f, Math.min(50f, (rowsBottom - rowsTop) / Math.max(6f, count + sections * 0.9f)))
    val sectionH = Math.floor(rowH * 0.9f).toFloat
    sectionCount = 0
    rowsShown = 0
    var y = rowsTop
    var lastTeam = Int.MinValue
    var i = 0
    while (i < count && rowsShown == i) {
      if (standings.teamMode && standings.teams(i) != lastTeam && sectionCount < sectionY.length) {
        lastTeam = standings.teams(i)
        sectionY(sectionCount) = y; sectionTeam(sectionCount) = lastTeam; sectionCount += 1
        y += sectionH
      }
      if (y + rowH <= rowsBottom + 0.5f) { rowY(i) = y; rowsShown += 1; y += rowH }
      i += 1
    }

    // Columns, from the right: score, deaths, kills; the names from after the portrait
    val colW = Math.min(120f, tw * 0.11f)
    val scoreRight = tx + tw - 16f
    val deathsRight = scoreRight - colW
    val killsRight = deathsRight - colW
    val rankX = tx + 16f
    val portraitX = tx + 64f
    val namesX = portraitX + rowH + 10f

    // ── Shapes ──
    beginShapes()
    // The game round it, dimmed: only round it, and under its corners, since the card can't be seen
    // through. Shaded under it too, the shade was most of what the board cost the GPU (render/CLAUDE.md,
    // *The scoreboard Tab holds up*)
    val shadeA = 0.38f
    shapeBatch.fillRect(0f, 0f, sw, py, 0.01f, 0.015f, 0.04f, shadeA)
    shapeBatch.fillRect(0f, py + ph, sw, sh - py - ph, 0.01f, 0.015f, 0.04f, shadeA)
    shapeBatch.fillRect(0f, py, px, ph, 0.01f, 0.015f, 0.04f, shadeA)
    shapeBatch.fillRect(px + pw, py, sw - px - pw, ph, 0.01f, 0.015f, 0.04f, shadeA)
    val corner = 18f
    shapeBatch.fillRect(px, py, corner, corner, 0.01f, 0.015f, 0.04f, shadeA)
    shapeBatch.fillRect(px + pw - corner, py, corner, corner, 0.01f, 0.015f, 0.04f, shadeA)
    shapeBatch.fillRect(px, py + ph - corner, corner, corner, 0.01f, 0.015f, 0.04f, shadeA)
    shapeBatch.fillRect(px + pw - corner, py + ph - corner, corner, corner, 0.01f, 0.015f, 0.04f, shadeA)
    // Glass you can't see through: the name plates under it are white text too, and with no blur
    // behind it they read through it even at 97%
    shapeBatch.fillRoundedRectGradient(px, py, pw, ph, 18f,
      0.05f, 0.07f, 0.15f, 0.995f,
      0.03f, 0.04f, 0.09f, 1f)
    shapeBatch.strokeRoundedRect(px, py, pw, ph, 18f, 1f, 1f, 1f, 1f, 0.10f)
    shapeBatch.strokeLine(px + 20f, py + 0.5f, px + pw - 20f, py + 0.5f, 1f, 1f, 1f, 1f, 0.15f)
    shapeBatch.strokeLine(tx, ruleY, tx + tw, ruleY, 1f, 1f, 1f, 1f, 0.09f)

    var s = 0
    while (s < sectionCount) {
      val team = sectionTeam(s).toByte
      teamColour(team)
      val sy = sectionY(s)
      shapeBatch.fillRoundedRect(tx, sy + 4f, tw, sectionH - 8f, 8f, _rgb_r, _rgb_g, _rgb_b, 0.13f)
      shapeBatch.fillRoundedRect(tx, sy + 4f, 4f, sectionH - 8f, 2f, _rgb_r, _rgb_g, _rgb_b, 0.95f)
      shapeBatch.fillOvalSmooth(tx + 22f, sy + sectionH / 2, 6f, 6f, _rgb_r, _rgb_g, _rgb_b, 1f)
      val t = teamIndex(team)
      if (t >= 0 && standings.teamRanks(t) == 1) {
        val chip = leadChip
        shapeBatch.fillRoundedRect(chipX(tx, team, t), sy + sectionH / 2 - 11f, fontSmall.measureWidth(chip) + 18f, 22f, 11f,
          GoldR, GoldG, GoldB, if (standings.teamsLevel) 0.10f else 0.18f)
      }
      s += 1
    }

    i = 0
    while (i < rowsShown) {
      val ry = rowY(i)
      if (standings.isLocal(i)) {
        shapeBatch.fillRoundedRect(tx, ry + 2f, tw, rowH - 4f, 8f, GoldR, GoldG, GoldB, 0.12f)
        shapeBatch.fillRoundedRect(tx, ry + 2f, 3f, rowH - 4f, 1.5f, GoldR, GoldG, GoldB, 0.95f)
      } else if (i % 2 == 0) shapeBatch.fillRoundedRect(tx, ry + 2f, tw, rowH - 4f, 8f, 1f, 1f, 1f, 0.035f)
      // A medal behind the first three places of a free-for-all
      if (!standings.teamMode && standings.ranks(i) <= 3) {
        medalColour(standings.ranks(i))
        shapeBatch.fillOvalSmooth(rankX + 14f, ry + rowH / 2, 13f, 13f, _rgb_r, _rgb_g, _rgb_b, 0.95f)
      }
      if (isDead(i)) skull(namesX - 2f, ry, rowH, i)
      i += 1
    }

    // ── The portraits: everyone's character, standing in their row ──
    beginSprites()
    i = 0
    while (i < rowsShown) {
      val region = GLSpriteGenerator.getSpriteRegion(Direction.Down, 0, standings.characters(i))
      if (region != null) {
        val size = rowH * 1.08f
        val top = rowY(i) + rowH - 3f - size * Constants.SPRITE_FOOT_LINE.toFloat
        val a = if (standings.hasLeft(i)) 0.35f else if (isDead(i)) 0.5f else 1f
        spriteBatch.draw(region, portraitX + (rowH - size) / 2, top, size, size, 1f, 1f, 1f, a)
      }
      i += 1
    }

    // ── The words ──
    // The heading: the mode and the map; the clock and our ping
    fontSmall.drawText(spriteBatch, sTitle, tx, fontSmall.digitsCentredOn(headTop + 8f), MutedR, MutedG, MutedB, 1f)
    val titleScale = 0.6f
    fontLarge.drawText(spriteBatch, headlineText(practice), tx, headTop + 36f + titleScale * fontLarge.digitsCentredOn(0f),
      InkR, InkG, InkB, 1f, titleScale)
    if (!practice) {
      val clock = clockString()
      val cw = fontLarge.measureWidth(clock, titleScale)
      val low = clockFor > 0 && clockFor <= 30
      fontLarge.drawText(spriteBatch, clock, tx + tw - cw, headTop + 36f + titleScale * fontLarge.digitsCentredOn(0f),
        if (low) 1f else InkR, if (low) 0.42f else InkG, if (low) 0.42f else InkB, 1f, titleScale)
    }
    val ping = pingString()
    if (ping.nonEmpty)
      fontSmall.drawText(spriteBatch, ping, tx + tw - fontSmall.measureWidth(ping), fontSmall.digitsCentredOn(headTop + 8f),
        MutedR, MutedG, MutedB, 1f)

    // The columns' heads
    val headY = fontSmall.digitsCentredOn(columnsMidY)
    fontSmall.drawText(spriteBatch, sRank, rankX, headY, MutedR, MutedG, MutedB, 1f)
    fontSmall.drawText(spriteBatch, sPlayer, portraitX, headY, MutedR, MutedG, MutedB, 1f)
    rightAligned(fontSmall, sKills, killsRight, headY, MutedR, MutedG, MutedB, 1f)
    rightAligned(fontSmall, sDeaths, deathsRight, headY, MutedR, MutedG, MutedB, 1f)
    rightAligned(fontSmall, sScore, scoreRight, headY, MutedR, MutedG, MutedB, 1f)

    // Each team's heading: its name, its kills, whether it leads, and its score in the score column
    s = 0
    while (s < sectionCount) {
      val team = sectionTeam(s).toByte
      val my = sectionY(s) + sectionH / 2
      teamColour(team)
      val name = teamName(team)
      fontMedium.drawText(spriteBatch, name, tx + 38f, fontMedium.digitsCentredOn(my), _rgb_r, _rgb_g, _rgb_b, 1f)
      val t = teamIndex(team)
      if (t >= 0) {
        fontSmall.drawText(spriteBatch, teamKillsText(standings.teamKills(t)), tx + 38f + fontMedium.measureWidth(name) + 14f,
          fontSmall.digitsCentredOn(my), MutedR, MutedG, MutedB, 1f)
        if (standings.teamRanks(t) == 1)
          fontSmall.drawText(spriteBatch, leadChip, chipX(tx, team, t) + 9f, fontSmall.digitsCentredOn(my), GoldR, GoldG, GoldB, 1f)
        rightAligned(fontMedium, number(standings.teamScores(t), signed = true), scoreRight, fontMedium.digitsCentredOn(my),
          _rgb_r, _rgb_g, _rgb_b, 1f)
      }
      s += 1
    }

    // Each row: the place, who (the character, then the player), kills, deaths, score
    i = 0
    while (i < rowsShown) {
      val my = rowY(i) + rowH / 2
      val faded = standings.hasLeft(i)
      val dim = if (faded) 0.45f else if (isDead(i)) 0.7f else 1f
      val numY = fontMedium.digitsCentredOn(my)
      // The place: a medal's for a free-for-all's first three, on a team only its standing within it
      val place = if (standings.teamMode) placeInTeam(i) else standings.ranks(i)
      val placeText = number(place, signed = false)
      val onMedal = !standings.teamMode && place <= 3
      fontMedium.drawText(spriteBatch, placeText, rankX + 14f - fontMedium.measureWidth(placeText) / 2, numY,
        if (onMedal) 0.10f else MutedR, if (onMedal) 0.07f else MutedG, if (onMedal) 0.02f else MutedB, if (onMedal) 1f else dim)
      // Who: the character first, as the match shows everyone, then who plays it
      val nx = namesX + (if (isDead(i)) 22f else 0f)
      val character = characterName(standings.characters(i))
      val local = standings.isLocal(i)
      fontMedium.drawText(spriteBatch, character, nx, numY,
        if (local) GoldR else InkR, if (local) 0.88f else InkG, if (local) 0.55f else InkB, dim)
      var after = nx + fontMedium.measureWidth(character) + 10f
      val who = playerName(i)
      val whoLimit = killsRight - colW - after
      if (whoLimit > 40f) {
        val smallY = fontSmall.digitsCentredOn(my)
        fontSmall.drawText(spriteBatch, who, after, smallY, BodyR, BodyG, BodyB, dim)
        after += fontSmall.measureWidth(who) + 8f
        val tag = if (local) sYou else if (faded) sLeft else if (isDead(i)) sDead else null
        if (tag != null && after + fontSmall.measureWidth(tag) < killsRight - colW * 0.6f)
          fontSmall.drawText(spriteBatch, tag, after, smallY,
            if (local) GoldR else if (faded) MutedR else 1f, if (local) GoldG else if (faded) MutedG else 0.55f,
            if (local) GoldB else if (faded) MutedB else 0.55f, if (faded) 0.8f else 1f)
      }
      rightAligned(fontMedium, number(standings.kills(i), signed = false), killsRight, numY, 0.50f, 0.90f, 0.65f, dim)
      rightAligned(fontMedium, number(standings.deaths(i), signed = false), deathsRight, numY, 1f, 0.60f, 0.66f, dim)
      rightAligned(fontMedium, number(standings.score(i), signed = true), scoreRight, numY, InkR, InkG, InkB, dim)
      i += 1
    }
    if (rowsShown < count) {
      val more = moreText(count - rowsShown)
      fontSmall.drawText(spriteBatch, more, tx + (tw - fontSmall.measureWidth(more)) / 2,
        fontSmall.digitsCentredOn(rowsBottom + 6f), MutedR, MutedG, MutedB, 1f)
    }

    // Practice's tallies, along the bottom
    if (practice) {
      val acc = accuracyString(); val combo = comboString()
      val fy = fontMedium.digitsCentredOn(py + ph - 18f - footerH / 2)
      val gap = 40f
      val total = fontMedium.measureWidth(acc) + gap + fontMedium.measureWidth(combo)
      val fx = tx + (tw - total) / 2
      fontMedium.drawText(spriteBatch, acc, fx, fy, InkR, InkG, InkB, 1f)
      fontMedium.drawText(spriteBatch, combo, fx + fontMedium.measureWidth(acc) + gap, fy, GoldR, GoldG, GoldB, 1f)
    }
  }

  // ── The rows ───────────────────────────────────────────────────────────

  /** Rebuilt when a kill, a death, someone joining or leaving changes it, the frame it comes up, and
    * once a second for the rest (a character or a team heard late). */
  private def rebuildIfChanged(): Unit = {
    val changed = client.talliesChanged
    val players = client.getPlayers.size
    val departed = client.getDepartedPlayers.size
    if (!shownLastFrame || changed != builtAtChange || players != builtWithPlayers || departed != builtWithDeparted ||
        frameTimeMs - builtAtMs >= 1000L) {
      standings.gather(client)
      standings.place()
      builtAtChange = changed; builtWithPlayers = players; builtWithDeparted = departed; builtAtMs = frameTimeMs
    }
  }

  private def isDead(i: Int): Boolean =
    if (standings.isLocal(i)) client.getIsDead
    else { val p = standings.players(i); p != null && !standings.hasLeft(i) && p.isDead }

  private def playerName(i: Int): String =
    if (standings.isLocal(i)) client.playerName
    else { val p = standings.players(i); if (p != null) p.getName else "" }

  /** A row's place among its own team's rows: 1 for the first. */
  private def placeInTeam(i: Int): Int = {
    var n = 1
    var j = i - 1
    while (j >= 0 && standings.teams(j) == standings.teams(i)) { n += 1; j -= 1 }
    n
  }

  /** The leading team's chip: it leads, or every team is level. */
  private def leadChip: String = if (standings.teamsLevel) sTied else sLeading

  /** Where a team heading's chip starts: after its name and its kills. */
  private def chipX(tx: Float, team: Byte, t: Int): Float =
    tx + 38f + fontMedium.measureWidth(teamName(team)) + 14f + fontSmall.measureWidth(teamKillsText(standings.teamKills(t))) + 14f

  private def teamIndex(team: Byte): Int = {
    var t = 0
    while (t < standings.teamCount) { if (standings.teamIds(t) == team) return t; t += 1 }
    -1
  }

  // ── Pieces ─────────────────────────────────────────────────────────────

  private def rightAligned(font: com.gridgame.client.gl.GLFontRenderer, s: String, right: Float, y: Float,
                           r: Float, g: Float, b: Float, a: Float): Unit =
    font.drawText(spriteBatch, s, right - font.measureWidth(s), y, r, g, b, a)

  /** The team's colour, as the menus give it (Theme.Palette.team), into _rgb_r/g/b. */
  private def teamColour(team: Byte): Unit = team match {
    case 1 => _rgb_r = 0.31f; _rgb_g = 0.62f; _rgb_b = 1.0f
    case 2 => _rgb_r = 1.0f; _rgb_g = 0.36f; _rgb_b = 0.44f
    case 3 => _rgb_r = 0.29f; _rgb_g = 0.85f; _rgb_b = 0.51f
    case 4 => _rgb_r = 1.0f; _rgb_g = 0.79f; _rgb_b = 0.25f
    case _ => _rgb_r = MutedR; _rgb_g = MutedG; _rgb_b = MutedB
  }

  /** Gold, silver and bronze, into _rgb_r/g/b. */
  private def medalColour(rank: Int): Unit = rank match {
    case 1 => _rgb_r = 1.0f; _rgb_g = 0.80f; _rgb_b = 0.30f
    case 2 => _rgb_r = 0.80f; _rgb_g = 0.84f; _rgb_b = 0.90f
    case _ => _rgb_r = 0.85f; _rgb_g = 0.56f; _rgb_b = 0.33f
  }

  /** A small red skull before the name of someone dead just now. */
  private def skull(x: Float, rowTop: Float, rowH: Float, i: Int): Unit = {
    val cx = x + 8f; val cy = rowTop + rowH / 2 - 1f
    shapeBatch.fillOval(cx, cy - 1f, 6.5f, 6f, 0.92f, 0.30f, 0.32f, 0.95f, 14)
    shapeBatch.fillRect(cx - 4f, cy + 3f, 8f, 4f, 0.92f, 0.30f, 0.32f, 0.95f)
    shapeBatch.fillOval(cx - 2.5f, cy - 1.5f, 1.7f, 1.9f, 0.08f, 0.02f, 0.03f, 1f, 8)
    shapeBatch.fillOval(cx + 2.5f, cy - 1.5f, 1.7f, 1.9f, 0.08f, 0.02f, 0.03f, 1f, 8)
  }

  // ── Words made as they change ──────────────────────────────────────────

  private def headlineText(practice: Boolean): String = {
    val map = client.getWorld.name
    val mode = if (practice) 2 else if (standings.teamMode) 1 else 0
    if ((map ne headlineFor) || mode != headlineMode) {
      val modeName = if (mode == 2) sPractice else if (mode == 1) sTeams else sFfa
      headline = if (map == null || map.isEmpty) modeName else s"$modeName  ·  $map"
      headlineFor = map; headlineMode = mode
    }
    headline
  }

  private def clockString(): String = {
    val left = client.gameTimeRemaining
    if (left != clockFor) {
      clockText = f"${left / 60}%d:${left % 60}%02d"
      clockFor = left
    }
    clockText
  }

  private def pingString(): String = {
    val ms = client.lastRoundTripMs
    if (ms != pingFor) {
      pingText = if (ms < 0) "" else Messages.t("Ping {0} ms", ms.toString)
      pingFor = ms
    }
    pingText
  }

  private def accuracyString(): String = {
    val acc = if (client.practiceShots > 0) (client.practiceHits * 100L / client.practiceShots).toInt else 0
    if (acc != accuracyFor) { accuracyText = Messages.t("Accuracy: {0}%", acc.toString); accuracyFor = acc }
    accuracyText
  }

  private def comboString(): String = {
    val best = client.practiceBestCombo
    if (best != comboFor) { comboText = Messages.t("Best Combo: {0}", best.toString); comboFor = best }
    comboText
  }

  private var moreFor = -1; private var moreWords = ""
  private def moreText(n: Int): String = {
    if (n != moreFor) { moreWords = Messages.t("and {0} more", n.toString); moreFor = n }
    moreWords
  }
}
