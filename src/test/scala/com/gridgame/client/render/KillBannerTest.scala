package com.gridgame.client.render

import com.gridgame.client.gl.DisplayFont.Face
import com.gridgame.client.render.hud.KillBannerPainter
import com.google.gson.JsonParser
import org.junit.Assert._
import org.junit.Test

import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters._

/**
 * A kill of ours announced (render/CLAUDE.md, *A kill is announced*): which title each kill in a row
 * gets, the colour a run heats up through, how a title lands, and that every word of it is in every
 * language the game speaks.
 */
class KillBannerTest {
  import KillBannerPainter._

  @Test def eachKillInARowHasATitleOfItsOwnAndTheLastHoldsForEveryRunPastIt(): Unit = {
    val titles = (1 to TitleKeys.length).map(run => TitleKeys(titleIndex(run)))
    assertEquals(Seq("ELIMINATED", "DOUBLE KILL", "TRIPLE KILL", "MEGA KILL", "RAMPAGE", "UNSTOPPABLE", "GODLIKE",
      "LEGENDARY"), titles)
    assertEquals(titles.distinct, titles)
    Seq(9, 12, 40, 999, 100000).foreach(run => assertEquals("LEGENDARY", TitleKeys(titleIndex(run))))
    // A count that can't come (no kill yet) is the first's, not an index out of the array
    assertEquals("ELIMINATED", TitleKeys(titleIndex(0)))
  }

  @Test def aRunHeatsUpFromGoldThroughEmberAndBerryToViolet(): Unit = {
    val faces = (1 to 9).map(faceFor)
    assertEquals(Seq(Face.Gold, Face.Gold, Face.Ember, Face.Ember, Face.Berry, Face.Berry, Face.Violet, Face.Violet,
      Face.Violet), faces)
  }

  @Test def aTitleLandsOnItsSizeAndStaysThere(): Unit = {
    assertEquals(1.5f, pop(0f, 1.5f), 0f)
    // Shrinking all the way in, to just under its size...
    var last = pop(0f, 1.5f)
    (1 to 110).foreach { t =>
      val s = pop(t.toFloat, 1.5f)
      assertTrue(s"at ${t}ms", s <= last + 1e-6f)
      last = s
    }
    assertEquals(0.93f, pop(110f, 1.5f), 1e-4f)
    // ...then settling on it, and staying there
    assertEquals(1f, pop(190f, 1.5f), 1e-4f)
    Seq(250f, ShowMs, 100000f).foreach(t => assertEquals(1f, pop(t, 1.5f), 0f))
    // A count landing after its title, not yet up, is at its first size
    assertEquals(2.1f, pop(-70f, 2.1f), 0f)
  }

  @Test def everyWordItSaysIsInEveryLanguage(): Unit = {
    def catalog(tag: String): Map[String, String] = {
      val in = getClass.getResourceAsStream(s"/i18n/messages_$tag.json")
      try JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject
        .entrySet().asScala.map(e => e.getKey -> e.getValue.getAsString).toMap
      finally in.close()
    }
    val words = TitleKeys.toSeq :+ "FIRST BLOOD"
    Seq("en", "ko", "zh_CN").foreach { tag =>
      val c = catalog(tag)
      words.foreach(w => assertTrue(s"$tag has '$w'", c.contains(w)))
    }
  }
}
