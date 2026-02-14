package com.gridgame.mapeditor

import com.gridgame.common.model.Tile
import scala.collection.mutable.ArrayBuffer

class UndoManager(maxStates: Int = 50) {
  // Store tile ID grids for undo/redo
  private val undoStack = new ArrayBuffer[Array[Array[Int]]]()
  private val redoStack = new ArrayBuffer[Array[Array[Int]]]()

  def saveSnapshot(state: EditorState): Unit = {
    val snapshot = state.world.tiles.map(_.map(_.id))
    undoStack += snapshot
    if (undoStack.length > maxStates) {
      undoStack.remove(0)
    }
    redoStack.clear()
  }

  def undo(state: EditorState): Unit = {
    if (undoStack.isEmpty) return

    // Save current state to redo
    val currentSnapshot = state.world.tiles.map(_.map(_.id))
    redoStack += currentSnapshot

    // Restore from undo
    val snapshot = undoStack.remove(undoStack.length - 1)
    applySnapshot(state, snapshot)
    state.dirty = true
  }

  def redo(state: EditorState): Unit = {
    if (redoStack.isEmpty) return

    // Save current to undo
    val currentSnapshot = state.world.tiles.map(_.map(_.id))
    undoStack += currentSnapshot

    // Restore from redo
    val snapshot = redoStack.remove(redoStack.length - 1)
    applySnapshot(state, snapshot)
    state.dirty = true
  }

  private def applySnapshot(state: EditorState, snapshot: Array[Array[Int]]): Unit = {
    for (y <- snapshot.indices; x <- snapshot(y).indices) {
      state.world.tiles(y)(x) = Tile.fromId(snapshot(y)(x))
    }
  }

  def canUndo: Boolean = undoStack.nonEmpty
  def canRedo: Boolean = redoStack.nonEmpty

  def clear(): Unit = {
    undoStack.clear()
    redoStack.clear()
  }
}
