package com.gridgame.common

object WorldRegistry {
  private val worlds: Array[String] = Array(
    "void_station.json",
    "the_necropolis.json",
    "reactor_core.json",
    "phantom_corridors.json",
    "toxic_wasteland.json",
    "the_abyss.json",
    "derelict_ship.json",
    "bone_pit.json",
    "neon_crypt.json",
    "dark_matter_lab.json",
    "the_hive.json",
    "shadow_forge.json",
    "cryo_chamber.json",
    "warp_gate.json",
    "plague_ward.json",
    "singularity.json"
  )

  def getFilename(index: Int): String = {
    if (index >= 0 && index < worlds.length) worlds(index) else worlds(0)
  }

  def getIndex(filename: String): Int = {
    val idx = worlds.indexOf(filename)
    if (idx >= 0) idx else 0
  }

  def getDisplayName(index: Int): String = {
    val filename = getFilename(index)
    filename.replace(".json", "").replace("_", " ").split(" ").map(_.capitalize).mkString(" ")
  }

  def displayNames: Array[String] = worlds.indices.map(getDisplayName).toArray

  def size: Int = worlds.length
}
