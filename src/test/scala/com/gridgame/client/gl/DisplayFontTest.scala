package com.gridgame.client.gl

import com.google.gson.JsonParser
import org.junit.Assert._
import org.junit.Test

import java.awt.Font
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters._

/**
 * The display font's letters (DisplayFont), as they are rasterized for the HUD's titles: the face the
 * menus letter theirs in, every letter whole in its picture (an italic capital leans past its advance,
 * and its ink further), the ink all round the face, and every letter of every title in every language
 * the game speaks drawn by one of its faces.
 */
class DisplayFontTest {
  // As a 60-unit title is rasterized on a HiDPI screen
  private val rasterSize = 120f
  private val outlinePx = 60f * 0.055f * 2f
  private val fonts: Array[Font] =
    DisplayFont.chain.zipWithIndex.map { case (f, i) => f.deriveFont(if (i == 0) Font.PLAIN else Font.BOLD, rasterSize) }

  private def catalog(tag: String): Map[String, String] = {
    val in = getClass.getResourceAsStream(s"/i18n/messages_$tag.json")
    try JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject
      .entrySet().asScala.map(e => e.getKey -> e.getValue.getAsString).toMap
    finally in.close()
  }

  // The words the HUD letters: the titles of a run of kills, FIRST BLOOD, FIGHT!, GAME OVER, a count
  private val Keys = Seq("ELIMINATED", "DOUBLE KILL", "TRIPLE KILL", "MEGA KILL", "RAMPAGE", "UNSTOPPABLE", "GODLIKE",
    "LEGENDARY", "FIRST BLOOD", "FIGHT!", "GAME OVER")
  private lazy val words: Seq[String] =
    Seq("en", "ko", "zh_CN").flatMap(tag => Keys.flatMap(catalog(tag).get)) :+ "×0123456789"
  private def codePoints(s: String): Seq[Int] = s.codePoints().toArray.toSeq

  @Test def itIsTheMenusTitleFace(): Unit = {
    val name = DisplayFont.chain.head.getFontName
    assertTrue(name, name.contains("Exo 2") && name.contains("Black") && name.contains("Italic"))
  }

  @Test def everyLetterOfEveryTitleIsDrawnByOneOfItsFaces(): Unit = {
    // A letter no face has would come out as an empty box, in the middle of a title
    for (w <- words; cp <- codePoints(w) if cp != ' ')
      assertTrue(s"U+${Integer.toHexString(cp)} of '$w'", DisplayFont.chain.exists(_.canDisplay(cp)))
  }

  @Test def nothingOfALetterIsCutOff(): Unit = {
    for (w <- words; cp <- codePoints(w).distinct if cp != ' ') {
      val l = DisplayFont.rasterize(fonts, cp, outlinePx, 0.5f)
      assertTrue(s"'${new String(Character.toChars(cp))}' has a picture", l.w > 0 && l.h > 0)
      def at(px: Array[Byte], x: Int, y: Int): Int = px(y * l.w + x) & 0xFF
      for (x <- 0 until l.w; y <- Seq(0, l.h - 1)) {
        assertEquals(s"'${new String(Character.toChars(cp))}' ink at ($x, $y)", 0, at(l.ink, x, y))
        assertEquals(0, at(l.face, x, y))
      }
      for (y <- 0 until l.h; x <- Seq(0, l.w - 1)) {
        assertEquals(s"'${new String(Character.toChars(cp))}' ink at ($x, $y)", 0, at(l.ink, x, y))
        assertEquals(0, at(l.face, x, y))
      }
    }
  }

  @Test def theInkGoesAllRoundTheFace(): Unit = {
    for (cp <- codePoints("ELIMNATDOUBKRPGSFH0123456789×")) {
      val l = DisplayFont.rasterize(fonts, cp, outlinePx, 0.5f)
      var faceX0, faceY0 = Int.MaxValue; var faceX1, faceY1 = Int.MinValue
      var inkX0, inkY0 = Int.MaxValue; var inkX1, inkY1 = Int.MinValue
      for (y <- 0 until l.h; x <- 0 until l.w) {
        val i = y * l.w + x
        val f = l.face(i) & 0xFF; val k = l.ink(i) & 0xFF
        // Wherever the face is, the ink under it is at least as solid: none of the face's edge shows
        // the ground through it
        assertTrue(s"'${cp.toChar}' at ($x, $y): face $f over ink $k", k + 2 >= f)
        if (f > 128) { faceX0 = faceX0 min x; faceX1 = faceX1 max x; faceY0 = faceY0 min y; faceY1 = faceY1 max y }
        if (k > 128) { inkX0 = inkX0 min x; inkX1 = inkX1 max x; inkY0 = inkY0 min y; inkY1 = inkY1 max y }
      }
      // and it stands out past the face by the outline on every side
      Seq(faceX0 - inkX0, inkX1 - faceX1, faceY0 - inkY0, inkY1 - faceY1).foreach { d =>
        assertEquals(s"'${cp.toChar}' ink beyond its face", outlinePx, d.toFloat, 1.6f)
      }
    }
  }

  @Test def aSpaceMovesThePenOnAndDrawsNothing(): Unit = {
    val l = DisplayFont.rasterize(fonts, ' ', outlinePx, 0.5f)
    assertEquals(0, l.w)
    assertTrue(l.advance > 0f)
  }
}
