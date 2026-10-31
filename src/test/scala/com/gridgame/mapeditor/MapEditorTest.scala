package com.gridgame.mapeditor

import com.gridgame.common.model.{Tile, WorldData}
import com.gridgame.common.world.WorldLoader
import org.junit.Assert._
import org.junit.Test

import java.nio.file.{Files, Path, Paths}
import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * The map editor's model, which writes the maps the game is played on: what it saves is the map it
 * has, tile for tile, read back by the game's own loader (every map in worlds/, and maps edited at
 * random with every tool); undo and redo walk its edits back and forth exactly; and its tools draw
 * what they say and nothing off the map.
 */
class MapEditorTest {
  private val rnd = new scala.util.Random(5)
  private val dir: Path = Files.createTempDirectory("gridgame-mapeditor")

  private def maps: Seq[Path] =
    Files.list(Paths.get("worlds")).iterator.asScala.filter(_.toString.endsWith(".json")).toSeq.sortBy(_.toString)

  private def ids(w: WorldData): Seq[Seq[Int]] = w.tiles.toSeq.map(_.toSeq.map(_.id))

  private def assertSameMap(what: String, a: WorldData, b: WorldData): Unit = {
    assertEquals(s"$what: its name", a.name, b.name)
    assertEquals(s"$what: its size", (a.width, a.height), (b.width, b.height))
    assertEquals(s"$what: its background", a.background, b.background)
    assertEquals(s"$what: its spawns", a.spawnPoints.map(p => (p.getX, p.getY)), b.spawnPoints.map(p => (p.getX, p.getY)))
    for (y <- 0 until a.height; x <- 0 until a.width)
      if (a.tiles(y)(x) != b.tiles(y)(x)) fail(s"$what: ($x, $y) was ${a.tiles(y)(x).name}, is ${b.tiles(y)(x).name}")
  }

  /** The map as the editor has it: its tiles, and the spawns it keeps apart from them (the spawn tool's). */
  private def asEdited(state: EditorState): WorldData =
    new WorldData(state.world.name, state.mapWidth, state.mapHeight, state.world.tiles,
      state.spawnPoints.toSeq.map { case (x, y) => new com.gridgame.common.model.Position(x, y) }, state.world.background)

  /** Saved by the editor, and read back by the game. */
  private def saveAndLoad(state: EditorState): WorldData = {
    val file = dir.resolve(s"saved${rnd.nextInt(1 << 30)}.json").toString
    WorldSaver.save(state, file)
    assertFalse("saved", state.dirty)
    assertEquals(Some(file), state.currentFile)
    WorldLoader.load(file)
  }

  private def opened(path: Path): EditorState = {
    val state = new EditorState
    state.loadWorld(WorldLoader.load(path.toString), path.toString)
    state
  }

  @Test def everyMapTheEditorSavesLoadsBackAsItWas(): Unit = {
    assertTrue("the maps are here", maps.nonEmpty)
    for (path <- maps) assertSameMap(path.getFileName.toString, WorldLoader.load(path.toString), saveAndLoad(opened(path)))
  }

  /** One random edit with one of the editor's tools, as its canvas makes them, sometimes off the map. */
  private def edit(state: EditorState): Unit = {
    val (w, h) = (state.mapWidth, state.mapHeight)
    def x = rnd.nextInt(w + 10) - 5
    def y = rnd.nextInt(h + 10) - 5
    val tile = Tile.all(rnd.nextInt(Tile.all.size))
    rnd.nextInt(5) match {
      case 0 => DrawingTools.pencil(state, x, y, 1 + rnd.nextInt(5), tile)
      case 1 => DrawingTools.drawRect(state, x, y, x, y, tile)
      case 2 => DrawingTools.drawCircle(state, x, y, x, y, tile)
      case 3 => DrawingTools.floodFill(state, x, y, tile)
      case _ =>
        // The spawn tool: one added on the map, or the last taken away (one is always left: a map
        // saved with none is given one at its middle as it is read, the game's choice and not the editor's)
        if (state.spawnPoints.size > 1 && rnd.nextBoolean()) state.spawnPoints.remove(state.spawnPoints.size - 1)
        else state.spawnPoints += ((rnd.nextInt(w), rnd.nextInt(h)))
    }
  }

  @Test def editsLoadBackAsTheyWereMade(): Unit =
    for (path <- maps.take(3); _ <- 0 until 3) {
      val state = opened(path)
      for (_ <- 0 until 40) edit(state)
      assertSameMap(s"${path.getFileName} edited", asEdited(state), saveAndLoad(state))
    }

  @Test def aNewMapSavesAsMade(): Unit = {
    val state = new EditorState
    state.createNewWorld("A new map", 37, 23, "space", Tile.Sand)
    for (_ <- 0 until 30) edit(state)
    assertSameMap("a new map", asEdited(state), saveAndLoad(state))
  }

  @Test def undoWalksEveryEditBackAndRedoForwardAgain(): Unit = {
    val state = opened(maps.head)
    val undo = new UndoManager()
    val history = mutable.Buffer(ids(state.world))
    // As the canvas does it: a snapshot before each edit
    for (_ <- 0 until 30) {
      undo.saveSnapshot(state)
      edit(state)
      history += ids(state.world)
    }
    for (i <- history.indices.reverse.tail) {
      undo.undo(state)
      assertEquals(s"undone back to edit $i", history(i), ids(state.world))
    }
    assertFalse(undo.canUndo)
    for (i <- 1 until history.size) {
      undo.redo(state)
      assertEquals(s"redone to edit $i", history(i), ids(state.world))
    }
    assertFalse(undo.canRedo)
    // A new edit after an undo leaves nothing to redo
    undo.undo(state)
    undo.saveSnapshot(state)
    edit(state)
    assertFalse(undo.canRedo)
  }

  @Test def undoKeepsItsLastFiftyEdits(): Unit = {
    val state = new EditorState
    state.createNewWorld("undo", 10, 10, "sky", Tile.Grass)
    val undo = new UndoManager()
    val history = mutable.Buffer(ids(state.world))
    for (i <- 0 until 70) {
      undo.saveSnapshot(state)
      state.setTile(i % 10, i / 10, Tile.Wall)
      history += ids(state.world)
    }
    var undone = 0
    while (undo.canUndo) { undo.undo(state); undone += 1 }
    assertEquals(50, undone)
    assertEquals("as it was fifty edits ago", history(history.size - 1 - 50), ids(state.world))
  }

  @Test def aFloodFillTakesExactlyTheRegionItStartsIn(): Unit =
    for (_ <- 0 until 200) {
      val state = new EditorState
      val (w, h) = (1 + rnd.nextInt(20), 1 + rnd.nextInt(20))
      state.createNewWorld("fill", w, h, "sky", Tile.Grass)
      for (x <- 0 until w; y <- 0 until h if rnd.nextDouble() < 0.4) state.setTile(x, y, Tile.Wall)
      val (sx, sy) = (rnd.nextInt(w), rnd.nextInt(h))
      val target = state.world.tiles(sy)(sx)
      // The region, found here a different way: grown cell by cell until nothing joins it
      val region = mutable.Set((sx, sy))
      var grew = true
      while (grew) {
        val more = for ((x, y) <- region.toSeq; (dx, dy) <- Seq((1, 0), (-1, 0), (0, 1), (0, -1));
                        (nx, ny) = (x + dx, y + dy) if nx >= 0 && ny >= 0 && nx < w && ny < h &&
                          state.world.tiles(ny)(nx) == target && !region.contains((nx, ny))) yield (nx, ny)
        grew = more.nonEmpty
        region ++= more
      }
      val before = ids(state.world)
      val paint = if (target == Tile.Water) Tile.Sand else Tile.Water
      DrawingTools.floodFill(state, sx, sy, paint)
      for (y <- 0 until h; x <- 0 until w) {
        val want = if (region.contains((x, y))) paint.id else before(y)(x)
        assertEquals(s"($x, $y) of a $w x $h fill from ($sx, $sy)", want, state.world.tiles(y)(x).id)
      }
    }

  @Test def aRectangleAndABrushCoverTheirCellsAndNothingElse(): Unit =
    for (_ <- 0 until 200) {
      val state = new EditorState
      state.createNewWorld("rect", 15, 12, "sky", Tile.Grass)
      val (x1, y1, x2, y2) = (rnd.nextInt(25) - 5, rnd.nextInt(22) - 5, rnd.nextInt(25) - 5, rnd.nextInt(22) - 5)
      DrawingTools.drawRect(state, x1, y1, x2, y2, Tile.Wall)
      for (y <- 0 until 12; x <- 0 until 15) {
        val inside = x >= Math.min(x1, x2) && x <= Math.max(x1, x2) && y >= Math.min(y1, y2) && y <= Math.max(y1, y2)
        assertEquals(s"($x, $y) of the rectangle ($x1, $y1)-($x2, $y2)", inside, state.world.tiles(y)(x) == Tile.Wall)
      }
      val brush = new EditorState
      brush.createNewWorld("brush", 15, 12, "sky", Tile.Grass)
      val (bx, by, size) = (rnd.nextInt(25) - 5, rnd.nextInt(22) - 5, 1 + rnd.nextInt(6))
      DrawingTools.pencil(brush, bx, by, size, Tile.Wall)
      val painted = (for (y <- 0 until 12; x <- 0 until 15 if brush.world.tiles(y)(x) == Tile.Wall) yield (x, y)).toSet
      val want = (for (dy <- -size / 2 until -size / 2 + size; dx <- -size / 2 until -size / 2 + size
                       if bx + dx >= 0 && bx + dx < 15 && by + dy >= 0 && by + dy < 12) yield (bx + dx, by + dy)).toSet
      assertEquals(s"a brush of $size at ($bx, $by)", want, painted)
    }

  @Test def aCircleIsRoundAndWithinItsBox(): Unit =
    for (_ <- 0 until 100) {
      val state = new EditorState
      state.createNewWorld("circle", 40, 40, "sky", Tile.Grass)
      val (cx, cy, r) = (10 + rnd.nextInt(20), 10 + rnd.nextInt(20), rnd.nextInt(9))
      DrawingTools.drawCircle(state, cx - r, cy - r, cx + r, cy + r, Tile.Wall)
      for (y <- 0 until 40; x <- 0 until 40) {
        val inside = (x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r
        assertEquals(s"($x, $y) of a circle of $r at ($cx, $cy)", inside, state.world.tiles(y)(x) == Tile.Wall)
      }
    }
}
