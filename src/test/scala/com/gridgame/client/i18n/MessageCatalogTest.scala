package com.gridgame.client.i18n

import com.google.gson.JsonParser
import org.junit.Assert._
import org.junit.Test

import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters._

/**
 * The translation catalogs the whole UI reads from. Read from the classpath as the game reads
 * them, but without Messages.setLocale, which would save the language to the player's settings.
 */
class MessageCatalogTest {
  private def catalog(tag: String): Map[String, String] = {
    val in = getClass.getResourceAsStream(s"/i18n/messages_$tag.json")
    assertNotNull(s"catalog $tag", in)
    try JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject
      .entrySet().asScala.map(e => e.getKey -> e.getValue.getAsString).toMap
    finally in.close()
  }

  private val english = catalog("en")
  private val placeholder = "\\{\\d+\\}".r

  @Test def everySupportedLanguageHasACatalog(): Unit =
    Messages.supported.foreach(l => assertTrue(l.tag, catalog(l.tag).nonEmpty))

  @Test def translationsOnlyTranslateEnglishKeys(): Unit =
    for (l <- Messages.supported; key <- catalog(l.tag).keys) assertTrue(s"${l.tag}: '$key'", english.contains(key))

  @Test def translationsKeepEveryPlaceholder(): Unit = {
    // A translation that drops {0} shows the text with the name, count or score missing
    for (l <- Messages.supported; (key, value) <- catalog(l.tag)) {
      assertEquals(s"${l.tag}: '$key' -> '$value'",
        placeholder.findAllIn(key).toSet, placeholder.findAllIn(value).toSet)
    }
  }

  @Test def noFormattedTextHasAnApostrophe(): Unit = {
    // MessageFormat takes ' as a quote: "{0}'s" would print "{0}s" with no name in it
    for (l <- Messages.supported; (key, value) <- catalog(l.tag) if placeholder.findFirstIn(key).isDefined)
      assertFalse(s"${l.tag}: '$value'", value.contains("'"))
  }

  @Test def missingTextFallsBackToTheKeyAndArgumentsAreFilledIn(): Unit = {
    assertEquals("Some text with no translation", Messages.t("Some text with no translation"))
    assertEquals("Players: 3/8", Messages.t("Players: {0}/{1}", "3", "8"))
  }
}
