package com.gridgame.tools

import com.google.gson.{GsonBuilder, JsonObject}
import com.gridgame.common.model.{CharacterDef, ItemType}

/**
 * Dev tool: emits the English i18n catalog entries for shared game content
 * (character names/descriptions, ability names/descriptions, item names) as a
 * flat JSON object on stdout, keyed to match [[com.gridgame.client.i18n.I18n]].
 *
 * Run:  bazel run //src/main/scala/com/gridgame/tools:gencontent 2>/dev/null
 *
 * The output is the authoritative English source that the zh_CN / ko catalogs
 * are translated from; regenerate whenever CharacterDef changes.
 */
object GenContentCatalog {
  def main(args: Array[String]): Unit = {
    val obj = new JsonObject()
    CharacterDef.all.foreach { d =>
      val id = d.id.id
      obj.addProperty(s"char.$id.name", d.displayName)
      obj.addProperty(s"char.$id.desc", d.description)
      obj.addProperty(s"char.$id.q.name", d.qAbility.name)
      obj.addProperty(s"char.$id.q.desc", d.qAbility.description)
      obj.addProperty(s"char.$id.e.name", d.eAbility.name)
      obj.addProperty(s"char.$id.e.desc", d.eAbility.description)
    }
    ItemType.all.foreach(t => obj.addProperty(s"item.${t.id}", t.name))
    val gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    println(gson.toJson(obj))
  }
}
