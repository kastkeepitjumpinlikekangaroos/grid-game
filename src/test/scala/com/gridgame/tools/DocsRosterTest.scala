package com.gridgame.tools

import com.gridgame.common.model.CharacterDef
import org.junit.Assert._
import org.junit.Test

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

/**
 * The website's 112 character cards say each character's role and health. They are written from
 * the roster by a tool, and this keeps them that way: a roster change that stops short of the
 * site fails here rather than advertising last season's numbers.
 *
 * Rewrite them with:
 *   bazel run //src/main/scala/com/gridgame/tools:gendocs
 */
class DocsRosterTest {
  // Bundled from docs/ (a data dependency of this test)
  private val html = new String(Files.readAllBytes(Paths.get("docs/index.html")), StandardCharsets.UTF_8)

  @Test def everyCharacterHasOneCard(): Unit = {
    val names = DocsRoster.cards(html).map(_._1)
    assertEquals(CharacterDef.all.map(_.displayName).sorted, names.sorted)
  }

  @Test def everyCardSaysWhatTheRosterSays(): Unit = {
    val byName = CharacterDef.all.map(d => d.displayName -> d).toMap
    for ((name, shown) <- DocsRoster.cards(html)) assertEquals(name, DocsRoster.pill(byName(name)), shown)
  }

  @Test def aPillSaysTheRoleAndTheHealth(): Unit = {
    assertEquals("Melee &middot; 145 HP", DocsRoster.pill(CharacterDef.BarbarianChar))
    assertEquals("Ranged &middot; 60 HP", DocsRoster.pill(CharacterDef.Wizard))
  }

  @Test def rewritingChangesOnlyThePills(): Unit = {
    val stale = html.replace(DocsRoster.pill(CharacterDef.Wizard), "70 HP")
    assertNotEquals(html, stale)
    assertEquals(html, DocsRoster.rewrite(stale))
  }
}
