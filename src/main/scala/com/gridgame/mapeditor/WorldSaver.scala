package com.gridgame.mapeditor

import com.google.gson.{GsonBuilder, JsonArray, JsonObject}
import java.io.{File, FileWriter}

object WorldSaver {

  def save(state: EditorState, filePath: String): Unit = {
    val root = new JsonObject()
    root.addProperty("name", state.world.name)
    root.addProperty("background", state.world.background)
    root.addProperty("width", state.mapWidth)
    root.addProperty("height", state.mapHeight)

    // Layers: single "grid" layer with all tile data
    val layers = new JsonArray()
    val gridLayer = new JsonObject()
    gridLayer.addProperty("type", "grid")

    val data = new JsonArray()
    for (y <- 0 until state.mapHeight) {
      val row = (0 until state.mapWidth).map(x => state.world.tiles(y)(x).id.toString).mkString(" ")
      data.add(row)
    }
    gridLayer.add("data", data)
    layers.add(gridLayer)
    root.add("layers", layers)

    // Spawn points
    val spawns = new JsonArray()
    state.spawnPoints.foreach { case (x, y) =>
      val sp = new JsonObject()
      sp.addProperty("x", x)
      sp.addProperty("y", y)
      spawns.add(sp)
    }
    root.add("spawnPoints", spawns)

    val gson = new GsonBuilder().setPrettyPrinting().create()
    val writer = new FileWriter(filePath)
    try {
      gson.toJson(root, writer)
    } finally {
      writer.close()
    }

    state.dirty = false
    state.currentFile = Some(filePath)
  }
}
