package com.gridgame.tools

import com.gridgame.common.model.CharacterDef

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * The website's character cards (docs/index.html) each carry a pill with the character's role and
 * health, "Melee &middot; 145 HP". This writes them from the roster, so 112 cards follow
 * CharacterDef instead of being edited by hand: run [[GenDocsRoster]] after a roster change.
 */
object DocsRoster {
  /** A card's heading and pill, as the page lays them out: the name, then the pill's text. */
  private val Card = Pattern.compile(
    "(<div class=\"char-header\">\\s*<h3>)([^<]+)(</h3>\\s*<span class=\"char-hp\">)([^<]*)(</span>)")

  /** What a character's pill says. */
  def pill(d: CharacterDef): String = s"${d.role.name} &middot; ${d.maxHealth} HP"

  /** Every card's name and pill text, in page order. */
  def cards(html: String): Seq[(String, String)] = {
    val m = Card.matcher(html)
    val out = Seq.newBuilder[(String, String)]
    while (m.find()) out += (m.group(2) -> m.group(4))
    out.result()
  }

  /** The page with every card's pill written from the roster. Fails on a card that names no
    * character, and on a character without a card, rather than leave either behind. */
  def rewrite(html: String): String = {
    val byName = CharacterDef.all.map(d => d.displayName -> d).toMap
    val names = cards(html).map(_._1)
    val strangers = names.filterNot(byName.contains)
    require(strangers.isEmpty, s"cards for no character: ${strangers.mkString(", ")}")
    val missing = CharacterDef.all.map(_.displayName).filterNot(names.contains)
    require(missing.isEmpty, s"characters with no card: ${missing.mkString(", ")}")

    val m = Card.matcher(html)
    val sb = new StringBuffer()
    while (m.find()) {
      val card = m.group(1) + m.group(2) + m.group(3) + pill(byName(m.group(2))) + m.group(5)
      m.appendReplacement(sb, Matcher.quoteReplacement(card))
    }
    m.appendTail(sb)
    sb.toString
  }
}
