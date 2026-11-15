package com.gridgame.common

object WorldRegistry {
  private val worlds: Array[String] = Array(
    "the_cell.json",
    "the_hive.json",
    "the_nexus.json",
    "the_colony.json",
    "the_meadow.json",
    "the_lagoon.json",
    "the_snowglobe.json",
    // Added after: a map's index is kept with every match played on it, so new maps go on the end
    "the_crucible.json",
    "the_caldera.json",
    "the_cinderlands.json",
    "the_crypt.json",
    "the_graveyard.json",
    "the_necropolis.json",
    "the_tomb.json",
    "the_oasis.json",
    "the_dunes.json",
    "the_wildwood.json",
    "the_archipelago.json",
    "the_tundra.json"
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
