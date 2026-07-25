package com.gridgame.client.i18n

import com.google.gson.JsonParser

import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.text.MessageFormat
import java.util.Locale
import java.util.prefs.Preferences
import scala.collection.mutable

/**
 * Central message catalog for client-side internationalization.
 *
 * The whole game reads translated text through this singleton, which makes it
 * the global source of truth for the active language. Catalogs are flat
 * `key -> value` JSON files bundled as resources at `/i18n/messages_<tag>.json`.
 *
 * Lookup falls back active-locale -> English -> the key itself, so a missing
 * translation degrades to English (or a visible key) instead of crashing.
 *
 * The chosen language is persisted via [[java.util.prefs.Preferences]] so it is
 * remembered across runs. Parameterized strings use [[java.text.MessageFormat]]
 * ("{0}", "{1}") so translators can reorder words for their language.
 */
object Messages {

  /** A selectable language: its resource tag, its name in its own script, and a JVM Locale. */
  final case class Lang(tag: String, nativeName: String, locale: Locale)

  /** Supported languages in picker order. English is always the base/fallback. */
  val supported: Seq[Lang] = Seq(
    Lang("en", "English", Locale.ENGLISH),
    Lang("zh_CN", "简体中文", Locale.SIMPLIFIED_CHINESE),
    Lang("ko", "한국어", Locale.KOREAN)
  )

  val DefaultTag = "en"

  private val prefs = Preferences.userRoot().node("com/gridgame/client/i18n")
  private val PrefKey = "locale"

  @volatile private var active: Map[String, String] = Map.empty
  private var english: Map[String, String] = Map.empty
  @volatile private var activeTag: String = DefaultTag
  @volatile private var codepoints: Array[Int] = Array.emptyIntArray

  /** UI hook: set by the client so the current screen rebuilds when the language changes. */
  @volatile var onLocaleChanged: () => Unit = () => ()

  def currentTag: String = activeTag
  def currentLang: Lang = supported.find(_.tag == activeTag).getOrElse(supported.head)
  def currentLocale: Locale = currentLang.locale

  /** Distinct Unicode codepoints across the active + English catalogs — used to pre-warm the glyph atlas. */
  def currentCodepoints: Array[Int] = codepoints

  /** Load the English base + the persisted (or default) locale. Call once at startup. */
  def init(): Unit = {
    english = load("en").getOrElse(Map.empty)
    val saved = Option(prefs.get(PrefKey, null)).filter(t => supported.exists(_.tag == t)).getOrElse(DefaultTag)
    applyLocale(saved, persist = false, notify = false)
  }

  /** Switch language: swaps the catalog, persists the choice, and notifies the UI to rebuild. */
  def setLocale(tag: String): Unit = {
    if (tag == activeTag) return
    applyLocale(tag, persist = true, notify = true)
  }

  private def applyLocale(tag: String, persist: Boolean, notify: Boolean): Unit = {
    active = if (tag == "en") english else load(tag).getOrElse(Map.empty)
    activeTag = tag
    codepoints = computeCodepoints(active, english)
    if (persist) prefs.put(PrefKey, tag)
    if (notify) try onLocaleChanged() catch { case _: Throwable => () }
  }

  private def load(tag: String): Option[Map[String, String]] = {
    val path = s"/i18n/messages_$tag.json"
    val in = getClass.getResourceAsStream(path)
    if (in == null) {
      System.err.println(s"i18n: catalog not found on classpath: $path")
      return None
    }
    try {
      val reader = new InputStreamReader(in, StandardCharsets.UTF_8)
      val obj = JsonParser.parseReader(reader).getAsJsonObject
      val m = mutable.Map[String, String]()
      val it = obj.entrySet().iterator()
      while (it.hasNext) {
        val e = it.next()
        if (e.getValue.isJsonPrimitive) m(e.getKey) = e.getValue.getAsString
      }
      Some(m.toMap)
    } catch {
      case ex: Exception =>
        System.err.println(s"i18n: failed to parse $path: $ex")
        None
    } finally in.close()
  }

  private def computeCodepoints(cat: Map[String, String], fallback: Map[String, String]): Array[Int] = {
    val set = mutable.HashSet[Int]()
    def add(s: String): Unit = {
      var i = 0
      while (i < s.length) {
        val cp = s.codePointAt(i)
        set += cp
        i += Character.charCount(cp)
      }
    }
    cat.values.foreach(add)
    fallback.values.foreach(add)
    set.toArray
  }

  /** Translate a key. Missing keys fall back to English, then to the key text itself. */
  def t(key: String): String = active.getOrElse(key, english.getOrElse(key, key))

  /** Translate a key, falling back to an explicit default (used for generated game-content keys). */
  def tOr(key: String, default: String): String = active.getOrElse(key, english.getOrElse(key, default))

  /** Translate a parameterized key, substituting {0},{1},... via MessageFormat (locale-aware word order). */
  def t(key: String, args: Any*): String = {
    val pattern = t(key)
    if (args.isEmpty) pattern
    else
      try new MessageFormat(pattern, currentLocale).format(args.map(_.asInstanceOf[AnyRef]).toArray)
      catch { case _: Exception => pattern }
  }
}
