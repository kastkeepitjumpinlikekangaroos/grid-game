package com.gridgame.common

object WorldRegistry {
  private val worlds: Array[String] = Array(
    "arena.json",
    "badlands.json",
    "battlegrounds.json",
    "dueling_islands.json",
    "enchanted_ruins.json",
    "fortress.json",
    "frozen_colosseum.json",
    "kings_canyon.json",
    "lava_bridges.json",
    "maze.json",
    "neon_grid.json",
    "shipwreck_bay.json",
    "station_nexus.json",
    "the_crucible.json",
    "toxic_spire.json",
    "volcano.json"
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
