package com.gridgame.mapeditor

import com.gridgame.common.model.{Tile, WorldData}

sealed trait Tool
object Tool {
  case object Pencil extends Tool
  case object Rect extends Tool
  case object Circle extends Tool
  case object Fill extends Tool
  case object Eraser extends Tool
  case object Spawn extends Tool
  case object Pan extends Tool
}

class EditorState {
  // World data
  var world: WorldData = WorldData.createEmpty(50, 50)

  // Current tool and tile
  var tool: Tool = Tool.Pencil
  var selectedTile: Tile = Tile.Grass
  var brushSize: Int = 1

  // Camera
  var cameraX: Double = 0.0
  var cameraY: Double = 0.0
  var zoom: Double = 1.0

  // Cursor position in world coordinates
  var cursorWorldX: Int = -1
  var cursorWorldY: Int = -1

  // View toggles
  var showGrid: Boolean = true
  var showSpawnPoints: Boolean = true

  // Dirty flag (unsaved changes)
  var dirty: Boolean = false

  // Current file path (None = never saved)
  var currentFile: Option[String] = None

  // Spawn points stored as (x, y) tuples (avoids Position validation issues)
  var spawnPoints: scala.collection.mutable.ArrayBuffer[(Int, Int)] =
    scala.collection.mutable.ArrayBuffer((25, 25))

  // Drag state for rect/circle tools
  var dragStartX: Int = -1
  var dragStartY: Int = -1
  var isDragging: Boolean = false

  def mapName: String = world.name
  def mapWidth: Int = world.width
  def mapHeight: Int = world.height

  def setTile(x: Int, y: Int, tile: Tile): Boolean = {
    if (x >= 0 && x < world.width && y >= 0 && y < world.height) {
      world.tiles(y)(x) = tile
      dirty = true
      true
    } else false
  }

  def getTile(x: Int, y: Int): Option[Tile] = {
    if (x >= 0 && x < world.width && y >= 0 && y < world.height)
      Some(world.tiles(y)(x))
    else None
  }

  def createNewWorld(name: String, width: Int, height: Int, background: String, fillTile: Tile): Unit = {
    val tiles: Array[Array[Tile]] = Array.fill(height, width)(fillTile)
    world = new WorldData(name, width, height, tiles, Seq.empty, background)
    spawnPoints.clear()
    spawnPoints += ((width / 2, height / 2))
    dirty = false
    currentFile = None
    cameraX = 0.0
    cameraY = 0.0
    zoom = 1.0
  }

  def loadWorld(worldData: WorldData, filePath: String): Unit = {
    world = worldData
    spawnPoints.clear()
    worldData.spawnPoints.foreach(p => spawnPoints += ((p.getX, p.getY)))
    dirty = false
    currentFile = Some(filePath)
    cameraX = 0.0
    cameraY = 0.0
    zoom = 1.0
  }
}
