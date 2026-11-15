package com.gridgame.common.world

import com.gridgame.common.Constants
import com.gridgame.common.WorldRegistry
import com.gridgame.common.model.{Position, TeamDivider, Tile, WorldData}
import org.junit.Assert._
import org.junit.Test

import scala.collection.mutable

/**
 * The maps a lobby can pick. The server spawns players on a map's spawn points and the client
 * draws it from the same file, so a spawn point in a wall, or ground no one can walk to from
 * the rest, is a player who can't play.
 */
class WorldMapsTest {
  private val worlds: Seq[(String, WorldData)] =
    (0 until WorldRegistry.size).map { i =>
      val f = WorldRegistry.getFilename(i)
      f -> WorldLoader.load("worlds/" + f)
    }

  @Test def everyRegisteredMapLoads(): Unit = {
    assertTrue(worlds.nonEmpty)
    for ((f, w) <- worlds) {
      assertTrue(f, w.width > 0 && w.height > 0)
      assertTrue(s"$f fits the grid positions can address", w.width <= Constants.GRID_SIZE && w.height <= Constants.GRID_SIZE)
      assertTrue(f, w.name.nonEmpty)
    }
  }

  @Test def everySpawnPointIsOpenGround(): Unit = {
    for ((f, w) <- worlds) {
      assertTrue(s"$f has spawn points", w.spawnPoints.nonEmpty)
      for (sp <- w.spawnPoints) assertTrue(s"$f $sp", w.isWalkable(sp))
    }
  }

  @Test def allOpenGroundIsOneRegion(): Unit = {
    // A walkable pocket nobody can reach is somewhere a spawn, a respawn or an item can strand you
    for ((f, w) <- worlds) {
      val start = w.spawnPoints.head
      val seen = mutable.HashSet[(Int, Int)]((start.getX, start.getY))
      val queue = mutable.Queue((start.getX, start.getY))
      while (queue.nonEmpty) {
        val (x, y) = queue.dequeue()
        for ((nx, ny) <- Seq((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1))
             if w.isWalkable(nx, ny) && seen.add((nx, ny))) queue.enqueue((nx, ny))
      }
      val walkable = (for (y <- 0 until w.height; x <- 0 until w.width if w.isWalkable(x, y)) yield 1).sum
      assertEquals(s"$f: open cells reachable from the first spawn", walkable, seen.size)
    }
  }

  @Test def bothTeamsHaveSpawnPointsInTheirOwnHalf(): Unit = {
    // A Teams match spawns each team in its own half of the map (TeamDivider)
    for ((f, w) <- worlds) {
      val sides = w.spawnPoints.map(p => TeamDivider.sideOf(w, p.getX, p.getY))
      assertTrue(s"$f: team 1's half", sides.contains(-1))
      assertTrue(s"$f: team 2's half", sides.contains(1))
    }
  }

  @Test def theGeneratedMapsAreMirrorImagesAcrossTheDivider(): Unit = {
    // scripts/generate_maps.py builds its maps symmetric about the divider's line, so a Teams match
    // on one is the same match from either side. (The four space maps predate it: they are centred
    // half a cell off the line.)
    val older = Set("the_cell.json", "the_hive.json", "the_nexus.json", "the_colony.json")
    assertEquals("the generated maps", 15, worlds.count(w => !older(w._1)))
    for ((f, w) <- worlds if !older(f)) {
      val line = TeamDivider.line(w)
      for (y <- 0 until w.height; x <- 0 until w.width if 2 * line - x < w.width)
        assertSame(s"$f ($x,$y)", w.getTile(x, y), w.getTile(2 * line - x, y))
      val sides = w.spawnPoints.map(p => TeamDivider.sideOf(w, p.getX, p.getY))
      assertEquals(s"$f: as many spawn points each side", sides.count(_ < 0), sides.count(_ > 0))
      assertFalse(s"$f: none on the line", sides.contains(0))
    }
  }

  // -- room to walk and to fight ------------------------------------------------------------

  @Test def everyMapHasRoomToWalkAndToFight(): Unit = {
    // One key walks a diagonal of the grid, a diagonal step goes between two open cells only, and a
    // blocked one slides along whichever side is open: a gap one cell wide is slid straight past,
    // and a bridge three wide leaves nowhere to step aside from a shot. The Archipelago's
    // boardwalks were three cells wide and two round its rings, the Graveyard's vaults had gates
    // of one cell, the space maps' spawn pods doors of one, and every wood was trees a cell apart
    // (scripts/map_audit.py measures the same and draws it; generate_maps.py builds to it).
    for ((f, w) <- worlds) assertEquals(s"$f: ${MapRoom.problems(w).mkString("; ")}", Nil, MapRoom.problems(w))
  }

  /** A walled room 24 cells square inside, with whatever `rows` put in it ('#' wall, anything else
    * floor), its top-left cell at the room's top-left. */
  private def room(rows: String*): WorldData = {
    val n = 26
    val tiles = Array.tabulate[Tile](n, n) { (y, x) =>
      val edge = x == 0 || y == 0 || x == n - 1 || y == n - 1
      val drawn = y - 1 >= 0 && y - 1 < rows.length && x - 1 >= 0 && x - 1 < rows(y - 1).length && rows(y - 1)(x - 1) == '#'
      if (edge || drawn) Tile.Wall else Tile.Grass
    }
    new WorldData("room", n, n, tiles, Seq(new Position(3, 3)))
  }

  /** A wall across the room on its tenth row, with a door of `door` cells in the middle of it. */
  private def walled(door: Int): WorldData = {
    val left = (24 - door) / 2
    room((Seq.fill(9)("") :+ ("#" * left + "." * door + "#" * (24 - left - door))): _*)
  }

  @Test def anOpenRoomHasRoom(): Unit = assertEquals(Nil, MapRoom.problems(room()))

  @Test def aDoorOfOneOrTwoCellsIsTight(): Unit = {
    for (door <- Seq(1, 2)) {
      val problems = MapRoom.problems(walled(door))
      assertTrue(s"a door of $door: $problems", problems.exists(_.contains("tight")))
    }
  }

  @Test def aDoorOfThreeOrFourCellsIntoHalfTheRoomIsAChoke(): Unit = {
    // Wide enough to walk through, too narrow to be the only way to somewhere of any size
    for (door <- Seq(3, 4)) {
      val problems = MapRoom.problems(walled(door))
      assertFalse(s"a door of $door is not tight: $problems", problems.exists(_.contains("tight")))
      assertTrue(s"a door of $door: $problems", problems.exists(_.contains("under 5")))
    }
  }

  @Test def aDoorOfFiveCellsIsAWayIn(): Unit = assertEquals(Nil, MapRoom.problems(walled(5)))

  @Test def aDoorOfThreeIntoAnAlcoveIsAllowed(): Unit = {
    // A spawn pod: five cells square inside, a door of three. Small enough to be an alcove
    val pod = room(
      "", "", "", "", "",
      "....#######",
      "....#.....#",
      "....#.....#",
      "....#.....#",
      "....#.....#",
      "....#.....#",
      "....##...##")
    assertEquals(Nil, MapRoom.problems(pod))
  }

  @Test def loneRocksFourCellsApartAreNotAGate(): Unit = {
    // A walk goes round either side of a rock: a row of them with three cells between is not a wall
    // with gates of three in it, and is not counted against the ways across a map
    val rocks = room((Seq.fill(9)("") :+ "...#...#...#...#...#...."): _*)
    assertEquals(Nil, MapRoom.problems(rocks))
    // ...but three in a row are the side of a way
    val walls = room((Seq.fill(9)("") :+ "###...###...###...###..."): _*)
    assertTrue(MapRoom.problems(walls).exists(_.contains("under 5")))
  }

  @Test def aSqueezeBetweenTwoCornersIsTight(): Unit = {
    // Two walls meeting corner to corner but for one cell: every cell either side has room round
    // it, and the way through is one diagonal step
    val rows = (0 until 24).map { y =>
      (0 until 24).map(x => if ((x + y == 23) && x != 11 && x != 12) '#' else '.').mkString
    }
    assertTrue(MapRoom.problems(room(rows: _*)).exists(_.contains("tight")))
  }

  @Test def displayNamesComeFromFileNames(): Unit = {
    assertEquals("The Cell", WorldRegistry.getDisplayName(0))
    assertEquals(WorldRegistry.getFilename(0), WorldRegistry.getFilename(-1))
    assertEquals(WorldRegistry.getFilename(0), WorldRegistry.getFilename(WorldRegistry.size))
    for (i <- 0 until WorldRegistry.size) assertEquals(i, WorldRegistry.getIndex(WorldRegistry.getFilename(i)))
  }
}

/**
 * Is there room to walk and to fight on a map? scripts/map_audit.py's rules, which generate_maps.py
 * builds every map to, measured here on the maps the game loads:
 *
 *  - no tight ground: every open cell is within a step of the map's spine, the cells with open
 *    ground all round them (3x3) joined to their neighbours on all eight sides. What is left
 *    out is a gap or a path one or two cells wide, and whatever lies behind one;
 *  - nothing of any size behind a choke: the same with 5x5 (a lane), counting no lone prop (cover
 *    of one or two cells: a rock to walk round), leaves no piece of ground bigger than an alcove.
 */
object MapRoom {
  val Room = 3
  val Lane = 5
  val Lone = 2
  val Alcove = 48

  def problems(w: WorldData): List[String] = {
    val walk = Array.tabulate(w.height, w.width)((y, x) => w.isTileWalkable(x, y))
    val out = List.newBuilder[String]

    val roomy = reach(walk, Room)
    val tight = for (y <- 0 until w.height; x <- 0 until w.width if walk(y)(x) && !roomy(y)(x)) yield (x, y)
    if (tight.nonEmpty) out += s"${tight.size} cells of tight ground (a gap or a path under $Room cells wide), e.g. ${tight.take(4).mkString(" ")}"

    // The lanes are measured with the lone props taken away
    val without = walk.map(_.clone())
    for (piece <- pieces(walk.map(_.map(!_)), diagonal = true) if piece.size <= Lone; (x, y) <- piece) without(y)(x) = true
    val lanes = reach(without, Lane)
    val off = Array.tabulate(w.height, w.width)((y, x) => walk(y)(x) && !lanes(y)(x))
    val cutOff = pieces(off, diagonal = false).filter(_.size > Alcove)
    if (cutOff.nonEmpty)
      out += s"${cutOff.map(_.size).sum} cells in ${cutOff.size} places are reached only by a way under $Lane cells wide, e.g. ${cutOff.take(4).map(_.head).mkString(" ")}"
    out.result()
  }

  /** What can be reached keeping n cells of open ground round oneself: every cell of the n x n
    * blocks of the biggest joined-up piece of the cells that have one. */
  private def reach(walk: Array[Array[Boolean]], n: Int): Array[Array[Boolean]] = {
    val h = walk.length
    val w = walk(0).length
    val k = n / 2
    val spine = Array.tabulate(h, w) { (y, x) =>
      y >= k && x >= k && y < h - k && x < w - k &&
        (-k to k).forall(oy => (-k to k).forall(ox => walk(y + oy)(x + ox)))
    }
    val out = Array.ofDim[Boolean](h, w)
    val all = pieces(spine, diagonal = true)
    if (all.nonEmpty) for ((x, y) <- all.maxBy(_.size); oy <- -k to k; ox <- -k to k) out(y + oy)(x + ox) = true
    out
  }

  /** The joined-up pieces of the cells that are set. */
  private def pieces(cells: Array[Array[Boolean]], diagonal: Boolean): Seq[Seq[(Int, Int)]] = {
    val h = cells.length
    val w = cells(0).length
    val seen = Array.ofDim[Boolean](h, w)
    val steps = Seq((1, 0), (-1, 0), (0, 1), (0, -1)) ++ (if (diagonal) Seq((1, 1), (1, -1), (-1, 1), (-1, -1)) else Nil)
    val out = Seq.newBuilder[Seq[(Int, Int)]]
    for (y0 <- 0 until h; x0 <- 0 until w if cells(y0)(x0) && !seen(y0)(x0)) {
      val piece = mutable.ArrayBuffer((x0, y0))
      seen(y0)(x0) = true
      var i = 0
      while (i < piece.size) {
        val (x, y) = piece(i)
        i += 1
        for ((ox, oy) <- steps) {
          val nx = x + ox
          val ny = y + oy
          if (nx >= 0 && ny >= 0 && nx < w && ny < h && cells(ny)(nx) && !seen(ny)(nx)) {
            seen(ny)(nx) = true
            piece += ((nx, ny))
          }
        }
      }
      out += piece.toSeq
    }
    out.result()
  }
}
