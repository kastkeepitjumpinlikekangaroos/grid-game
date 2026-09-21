package com.gridgame.client.i18n

import com.google.gson.JsonParser
import com.gridgame.common.model.CharacterDef
import org.junit.Assert._
import org.junit.Test

import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters._

/**
 * The roster's English text lives in two places: CharacterDef, which the server and the
 * gameplay code read, and the English message catalog, which is what the player actually sees
 * (I18n.tOr prefers the catalog and only falls back to CharacterDef when a key is missing).
 *
 * So renaming an ability in CharacterDef alone leaves the old name on every screen, silently.
 * This ties the two together: the catalog's char.* entries must be word for word what
 * CharacterDef says, and every character must have all six of them.
 *
 * Regenerate the entries with:
 *   bazel run //src/main/scala/com/gridgame/tools:gencontent 2>/dev/null
 */
class ContentCatalogTest {
  private val english: Map[String, String] = {
    val in = getClass.getResourceAsStream("/i18n/messages_en.json")
    assertNotNull("the English catalog", in)
    try JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject
      .entrySet().asScala.map(e => e.getKey -> e.getValue.getAsString).toMap
    finally in.close()
  }

  /** Every char.* key the roster should produce, and the English text it should carry. */
  private val expected: Map[String, String] = CharacterDef.all.flatMap { d =>
    val id = d.id.id
    Seq(
      s"char.$id.name" -> d.displayName,
      s"char.$id.desc" -> d.description,
      s"char.$id.q.name" -> d.qAbility.name,
      s"char.$id.q.desc" -> d.qAbility.description,
      s"char.$id.e.name" -> d.eAbility.name,
      s"char.$id.e.desc" -> d.eAbility.description
    )
  }.toMap

  @Test def everyCharacterAndAbilityHasItsEntries(): Unit = {
    assertEquals("six keys per character", CharacterDef.all.size * 6, expected.size)
    val missing = expected.keys.filterNot(english.contains).toSeq.sorted
    assertTrue(s"not in the catalog: ${missing.take(12).mkString(", ")}", missing.isEmpty)
  }

  @Test def theCatalogSaysWhatTheRosterSays(): Unit = {
    // A rename in CharacterDef that stops here shows the old text in game, since the catalog wins
    for ((key, text) <- expected.toSeq.sortBy(_._1)) {
      english.get(key).foreach(shown => assertEquals(key, text, shown))
    }
  }

  @Test def theCatalogHasNoCharacterEntriesTheRosterDoesNot(): Unit = {
    // A leftover from a character that was renumbered or removed: it would translate nothing
    val stale = english.keys.filter(_.startsWith("char.")).filterNot(expected.contains).toSeq.sorted
    assertTrue(s"no longer in the roster: ${stale.take(12).mkString(", ")}", stale.isEmpty)
  }
}
