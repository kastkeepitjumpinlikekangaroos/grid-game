package com.gridgame.server

import com.gridgame.common.GameClock
import com.gridgame.common.model._
import org.junit.Assert._
import org.junit.Test

import scala.collection.mutable

/**
 * The game has no north. A scene turned a quarter, a half or three quarters round, or mirrored —
 * the eight ways a square map can be laid over itself — is the same scene, and whatever a shot does
 * in one it must do in all of them: strike the same players on the same tick for the same damage,
 * push them to the cells the turn puts those cells on, stop where the turn puts where it stopped.
 * Nothing here says what a shot should do, only that it does it whichever way it flies; what it
 * should do one way round is the other suites'.
 *
 * It is the kind of fault this game has had most of: a shot from one side connecting a cell sooner
 * than from the other, a slam reaching a cell further south-east than north-west, a shot fired east
 * or south flying through the fence one fired west or north stopped at (design/combat.md, *Where a
 * hit lands*). Each was found by a player, one direction at a time.
 */
class SymmetryTest {
  import SymmetryTest._

  @Test def debugOne(): Unit = Option(System.getenv("GRIDGAME_SYM_DEBUG")).foreach(spec => spec.split(";").foreach(one => println(debug(one))))

  @Test def aBlinkAShoveAStarAndAThrowLandTheSameWhicheverWayTheSceneIsTurned(): Unit = {
    // Where a blink lands, where a knockback slides to, where a star jumps and where a thrown trap
    // falls (Teleport, TrapPlacement): from the middle and from cells about it, along the sixteen
    // points of the compass, as far as each goes
    var landings = 0
    for (seed <- 1 to 30) {
      val scene = Scene.random(seed, attacks.head)
      val world = worldOf(scene.tiles)
      val froms = (Centre, Centre) +: scene.others.map(_._1).take(3)
      for (turn <- Turn.all.tail) {
        val turnedWorld = worldOf(scene.turned(turn).tiles)
        for ((fx, fy) <- froms; (hx, hy) <- Scene.compass; reach <- Seq(1, 2, 3, 5, 8, 12)) {
          val (tfx, tfy) = turn.cell(fx, fy)
          val (thx, thy) = turn.heading(hx, hy)
          def same(what: String, here: Position, there: Position): Unit = {
            landings += 1
            assertEquals(s"scene $seed ${turn.name}: $what of $reach from ($fx,$fy) along ($hx,$hy) lands at $here as it is",
              turn.cell(here.getX, here.getY), (there.getX, there.getY))
          }
          same("a blink", Teleport.blinkTarget(world, fx, fy, hx, hy, reach), Teleport.blinkTarget(turnedWorld, tfx, tfy, thx, thy, reach))
          same("a shove", Teleport.slide(world, fx, fy, hx, hy, reach), Teleport.slide(turnedWorld, tfx, tfy, thx, thy, reach))
          val star = Teleport.starTarget(world, fx, fy, fx + hx * reach * 3.0, fy + hy * reach * 3.0)
          val turnedStar = Teleport.starTarget(turnedWorld, tfx, tfy, tfx + thx * reach * 3.0, tfy + thy * reach * 3.0)
          assertEquals(s"scene $seed ${turn.name}: a star from ($fx,$fy)", star.isDefined, turnedStar.isDefined)
          for (here <- star) same("a star", here, turnedStar.get)
          val trap = TrapPlacement.target(world, fx, fy, fx + hx * reach.toDouble, fy + hy * reach.toDouble, 6)
          val turnedTrap = TrapPlacement.target(turnedWorld, tfx, tfy, tfx + thx * reach.toDouble, tfy + thy * reach.toDouble, 6)
          assertEquals(s"scene $seed ${turn.name}: a throw from ($fx,$fy)", trap.isDefined, turnedTrap.isDefined)
          for (here <- trap) same("a thrown trap", here, turnedTrap.get)
        }
      }
    }
    assertTrue(landings > 100000)
  }

  @Test def ofTwoAsNearAsEachOtherAShotStrikesTheSameOneWhicheverWayTheSceneIsTurned(): Unit = {
    // One either side of its line, as near it as each other at every step: which of them it
    // strikes went by which the tick's grid listed first, so by where on the map they stood
    val border = (for (x <- 0 until Size; y <- 0 until Size if x == 0 || y == 0 || x == Size - 1 || y == Size - 1) yield (x, y) -> (Tile.Wall: Tile)).toMap
    val plain = attacks.filter { a =>
      val d = ProjectileDef.get(a.projectileType)
      !a.slam && !d.passesThroughPlayers && d.pierceCount == 0 && !d.isExplosive && d.aoeOnHit.isEmpty && d.maxRange >= 6 &&
        d.hitCentreBehind == 0f && d.onHitEffects.forall { case _: Slow | _: Burn | _: Poison => true; case _ => false }
    }
    assertTrue(plain.size > 10)
    for (attack <- plain.take(12); (ahead, aside) <- Seq((3, 1), (4, 1), (5, 1), (2, 1))) {
      val scene = Scene(0L, attack, border, Seq(((Centre + ahead, Centre - aside), Direction.Down), ((Centre + ahead, Centre + aside), Direction.Down)), (1f, 0f))
      val struck = Turn.all.map { turn =>
        val last = play(scene.turned(turn)).raw.last._1
        // Who lost health: the shooter is first, then the two
        (1 to 2).filter(i => last(i)._3 < CharacterDef.get(CharacterId.Golem.id).maxHealth)
      }
      assertEquals(s"${attack.name}, two targets $ahead ahead and $aside either side: one of them is struck", 1, struck.head.size)
      for ((who, turn) <- struck.zip(Turn.all)) assertEquals(s"${attack.name}, ${turn.name}", struck.head, who)
    }
  }

  @Test def aStepOfSeveralCellsPassesOverTheSameCellsWhicheverWayItGoes(): Unit = {
    // An update can carry a player more than a cell (a datagram lost, a dash), and the traps on the
    // cells it passed over are sprung (TrapPlacement.walkCells): for every step up to five cells
    // either way, turned every way
    for (dx <- -5 to 5; dy <- -5 to 5; turn <- Turn.all.tail) {
      def passed(fx: Int, fy: Int, tx: Int, ty: Int): Seq[(Int, Int)] = {
        val cells = mutable.Buffer[(Int, Int)]()
        TrapPlacement.walkCells(fx, fy, tx, ty)((x, y) => cells += ((x, y)))
        cells.toSeq
      }
      val (tx, ty) = turn.cell(Centre + dx, Centre + dy)
      assertEquals(s"a step of ($dx,$dy), ${turn.name}",
        passed(Centre, Centre, Centre + dx, Centre + dy).map { case (x, y) => turn.cell(x, y) }, passed(Centre, Centre, tx, ty))
    }
  }

  @Test def aShotDoesTheSameWhicheverWayTheSceneIsTurned(): Unit = {
    val found = mutable.LinkedHashMap[String, mutable.Buffer[String]]()
    var scenes = 0
    for (attack <- attacks; seed <- 1 to 4) {
      val scene = Scene.random(seed * 1000L + attack.projectileType, attack)
      val straight = play(scene)
      scenes += 1
      for (turn <- Turn.all.tail) {
        val turned = play(scene.turned(turn))
        val differs = straight.differsFrom(turned, turn)
        if (differs != null) found.getOrElseUpdate(attack.name, mutable.Buffer()) += s"scene ${scene.seed} ${turn.name}: $differs"
      }
    }
    println(s"SymmetryTest: ${attacks.size} attacks, $scenes scenes, each turned ${Turn.all.size - 1} ways")
    if (found.nonEmpty)
      fail(s"${found.size} of ${attacks.size} attacks do something else when the scene is turned:\n" +
        found.map { case (name, all) => s"  $name (${all.size} turns): ${all.head}" }.mkString("\n"))
  }
}

object SymmetryTest {
  /** GRIDGAME_SYM_DEBUG="<name substring>:<scene seed>:<turn index>": what a scene and one turn of it did, side by side. */
  def debug(spec: String): String = {
    val Array(name, seed, turnIndex) = spec.split(":")
    val attack = attacks.find(_.name.contains(name)).get
    val scene = Scene.random(seed.toLong, attack)
    val turn = Turn.all(turnIndex.toInt)
    val (a, b) = (play(scene), play(scene.turned(turn)))
    val d = ProjectileDef.get(attack.projectileType)
    val sb = new StringBuilder(s"${attack.name} type ${attack.projectileType} speed ${d.speedMultiplier} range ${d.maxRange} radius ${d.hitRadius} " +
      s"walls ${d.passesThroughWalls} players ${d.passesThroughPlayers} bounces ${d.ricochetCount} pierce ${d.pierceCount} explosive ${d.isExplosive} slam ${attack.slam}\n")
    sb.append(s"heading ${scene.heading}, turned (${turn.name}) ${scene.turned(turn).heading}\n")
    sb.append("as it is: " + told(scene)).append("turned: " + told(scene.turned(turn)))
    sb.append(s"others: ${scene.others.map(_._1).mkString(" ")}\n")
    val near = scene.tiles.filter { case ((x, y), _) => Math.abs(x - Centre) <= 12 && Math.abs(y - Centre) <= 12 }
    sb.append(s"tiles: ${near.toSeq.sortBy(_._1).map { case ((x, y), t) => s"($x,$y)${t.name}" }.mkString(" ")}\n")
    for (k <- 0 until Math.max(a.raw.size, b.raw.size)) {
      def show(p: Played, back: Boolean): String = if (k >= p.raw.size) "-" else {
        val (players, shots) = p.raw(k)
        // the turned run's cells and points, turned back
        val inv = Turn.all.find(t => t.cell(turn.cell(3, 7)._1, turn.cell(3, 7)._2) == ((3, 7))).get
        val ps = players.map { case (x, y, hp) => val (bx, by) = if (back) inv.cell(x, y) else (x, y); s"($bx,$by)$hp" }.mkString(" ")
        val ss = shots.map { case (x, y) => val (bx, by) = if (back) inv.point(x, y) else (x, y); f"[$bx%.2f,$by%.2f]" }.mkString(" ")
        s"$ps | $ss"
      }
      sb.append(f"${k + 1}%3d  ${show(a, back = false)}\n     ${show(b, back = true)}\n")
    }
    sb.toString
  }

  /** What everyone was told a scene's shot did, tick by tick (an onlooker in the corner hears it). */
  def told(scene: Scene): String = ServerTestKit.quietly(Sim() { clock =>
    val world = WorldData.createEmpty(Size, Size)
    for (((x, y), tile) <- scene.tiles) world.setTile(x, y, tile)
    world.setTile(1, 1, Tile.Grass)
    val m = new SimMatch(clock, world, respawns = false)
    val shooter = m.join(scene.attack.character.id, Centre, Centre, connected = false, id = new java.util.UUID(9L, 0L))
    scene.others.zipWithIndex.foreach { case (((x, y), dir), i) =>
      m.join(CharacterId.Golem, x, y, connected = false, id = new java.util.UUID(9L, 1L + i)).setDirection(dir) }
    val onlooker = m.join(CharacterId.Golem, 1, 1, id = new java.util.UUID(9L, 99L))
    val (dx, dy) = if (scene.attack.slam) (0f, 0f) else scene.heading
    val shot = m.launch(shooter, scene.attack.projectileType, dx, dy)
    val sb = new StringBuilder(f"spawned at (${shot.getX}%.2f,${shot.getY}%.2f) in cell (${shot.getCellX},${shot.getCellY}) ${world.getTile(shot.getCellX, shot.getCellY).name}\n")
    var n = 0
    while (m.instance.projectileManager.size > 0 && n < 40) {
      m.tick(); n += 1
      val heard = m.sent(onlooker).collect { case p: com.gridgame.common.protocol.ProjectilePacket =>
        val action = Seq("SPAWN", "MOVE", "HIT", "DESPAWN", "PIERCE", "BLOCKED")(p.getAction)
        f"$action (${p.getX}%.2f,${p.getY}%.2f)${if (p.getTargetId != null) " on " + p.getTargetId.getLeastSignificantBits else ""}" }
      sb.append(s"  tick $n: ${heard.mkString(", ")}\n")
    }
    sb.toString
  })

  val Size = 41
  val Centre = 20

  /** One of the eight ways a square lies over itself: where a cell goes, and where a heading does. */
  final case class Turn(name: String, a: Int, b: Int, c: Int, d: Int) {
    // (x, y) about the centre -> (a x + b y, c x + d y)
    def cell(x: Int, y: Int): (Int, Int) = {
      val (rx, ry) = (x - Centre, y - Centre)
      (Centre + a * rx + b * ry, Centre + c * rx + d * ry)
    }
    def point(x: Float, y: Float): (Float, Float) = {
      val (rx, ry) = (x - Centre, y - Centre)
      (Centre + a * rx + b * ry, Centre + c * rx + d * ry)
    }
    def heading(dx: Float, dy: Float): (Float, Float) = (a * dx + b * dy, c * dx + d * dy)
    def facing(dir: Direction): Direction = {
      val (dx, dy) = dir match {
        case Direction.Up => (0, -1)
        case Direction.Down => (0, 1)
        case Direction.Left => (-1, 0)
        case Direction.Right => (1, 0)
      }
      (a * dx + b * dy, c * dx + d * dy) match {
        case (0, -1) => Direction.Up
        case (0, 1) => Direction.Down
        case (-1, 0) => Direction.Left
        case _ => Direction.Right
      }
    }
  }

  object Turn {
    val all: Seq[Turn] = Seq(
      Turn("as it is", 1, 0, 0, 1), Turn("a quarter round", 0, -1, 1, 0), Turn("half round", -1, 0, 0, -1),
      Turn("three quarters round", 0, 1, -1, 0), Turn("mirrored east to west", -1, 0, 0, 1),
      Turn("mirrored north to south", 1, 0, 0, -1), Turn("mirrored about one diagonal", 0, 1, 1, 0),
      Turn("mirrored about the other", 0, -1, -1, 0))
  }

  type Attack = RosterAttack

  /** Every projectile type the roster throws, once, with the first character who throws it. */
  val attacks: Seq[RosterAttack] = RosterAttack.all

  /** A shooter in the middle of cluttered ground with others about, and the way the shot goes. */
  final case class Scene(seed: Long, attack: Attack, tiles: Map[(Int, Int), Tile], others: Seq[((Int, Int), Direction)],
                         heading: (Float, Float), barrier: Option[(Float, Float)] = None) {
    def turned(t: Turn): Scene = copy(
      tiles = tiles.map { case ((x, y), tile) => t.cell(x, y) -> tile },
      others = others.map { case ((x, y), dir) => (t.cell(x, y), t.facing(dir)) },
      heading = t.heading(heading._1, heading._2),
      barrier = barrier.map { case (fx, fy) => t.heading(fx, fy) })
  }

  object Scene {
    // Sixteen points of the compass: a turn of the scene puts each on another of them
    val compass: Seq[(Float, Float)] = {
      val d = 1.0 / Math.sqrt(2.0)
      val (c, s) = (Math.cos(Math.PI / 8), Math.sin(Math.PI / 8))
      val firstQuarter = Seq((1.0, 0.0), (c, s), (d, d), (s, c))
      // Each quarter is the one before turned a quarter round, exactly: (x, y) -> (-y, x)
      (0 until 4).flatMap(q => firstQuarter.map { case (x, y) => (0 until q).foldLeft((x, y)) { case ((px, py), _) => (-py, px) } })
        .map { case (x, y) => (x.toFloat, y.toFloat) }
    }

    def random(seed: Long, attack: Attack): Scene = {
      val rnd = new scala.util.Random(seed)
      val tiles = mutable.Map[(Int, Int), Tile]()
      for (x <- 0 until Size; y <- 0 until Size) {
        val edge = x == 0 || y == 0 || x == Size - 1 || y == Size - 1
        val r = rnd.nextDouble()
        val near = Math.abs(x - Centre) <= 12 && Math.abs(y - Centre) <= 12 && (x, y) != ((Centre, Centre))
        if (edge) tiles((x, y)) = Tile.Wall
        else if (near && r < 0.05) tiles((x, y)) = Tile.Wall
        else if (near && r < 0.08) tiles((x, y)) = Tile.Fence
        else if (near && r < 0.11) tiles((x, y)) = Tile.Water
      }
      val others = mutable.Buffer[((Int, Int), Direction)]()
      val facings = Seq(Direction.Up, Direction.Down, Direction.Left, Direction.Right)
      while (others.size < 6) {
        val cell = (Centre + rnd.nextInt(17) - 8, Centre + rnd.nextInt(17) - 8)
        if (cell != ((Centre, Centre)) && !tiles.contains(cell) && !others.exists(_._1 == cell)) others += ((cell, facings(rnd.nextInt(4))))
      }
      // A shot that bounces is never sent square at a wall: one that meets it head on turns north
      // or west, by a rule of its own (Projectile.ricochet, which KitReworkTest holds it to), and a
      // turn to one side has no mirror image. Off any other heading a bounce is as much the same
      // whichever way the scene is turned as anything else is
      val headings = if (ProjectileDef.get(attack.projectileType).ricochetCount > 0) compass.filter { case (x, y) => x != 0f && y != 0f } else compass
      // In every other scene the first of the others holds a barrier up, facing one of the sixteen
      val barrier = if (seed % 2 == 0) Some(compass(rnd.nextInt(compass.size))) else None
      Scene(seed, attack, tiles.toMap, others.toSeq, headings(rnd.nextInt(headings.size)), barrier)
    }
  }

  /** What happened, tick by tick. */
  final class Played(val ticks: Seq[String], val raw: Seq[(Seq[(Int, Int, Int)], Seq[(Float, Float)])]) {
    /** The first thing that isn't the same in `turned` once it is turned back, or null. */
    def differsFrom(turned: Played, turn: Turn): String = {
      if (raw.size != turned.raw.size) return s"it took ${raw.size} ticks as it is and ${turned.raw.size} turned"
      for (k <- raw.indices) {
        val (players, shots) = raw(k)
        val (tPlayers, tShots) = turned.raw(k)
        for (i <- players.indices) {
          val (x, y, hp) = players(i)
          val (tx, ty) = turn.cell(x, y)
          if ((tx, ty, hp) != tPlayers(i))
            return s"on tick ${k + 1}, player ${i + 1} is at ($x,$y) with $hp health as it is (which turns to ($tx,$ty)), and at " +
              s"(${tPlayers(i)._1},${tPlayers(i)._2}) with ${tPlayers(i)._3} turned"
        }
        if (shots.size != tShots.size) return s"on tick ${k + 1}, ${shots.size} in flight as it is and ${tShots.size} turned"
        for (i <- shots.indices) {
          val (sx, sy) = turn.point(shots(i)._1, shots(i)._2)
          if (Math.abs(sx - tShots(i)._1) > 0.01f || Math.abs(sy - tShots(i)._2) > 0.01f)
            return f"on tick ${k + 1}, the shot is at (${shots(i)._1}%.3f,${shots(i)._2}%.3f) as it is (which turns to ($sx%.3f,$sy%.3f)), and at (${tShots(i)._1}%.3f,${tShots(i)._2}%.3f) turned"
        }
      }
      null
    }
  }

  def worldOf(tiles: Map[(Int, Int), Tile]): WorldData = {
    val world = WorldData.createEmpty(Size, Size)
    for (((x, y), tile) <- tiles) world.setTile(x, y, tile)
    world
  }

  def play(scene: Scene): Played = ServerTestKit.quietly(Sim() { clock =>
    val world = worldOf(scene.tiles)
    val m = new SimMatch(clock, world, respawns = false)
    // The same ids every time: where two stand as near a shot as each other, which of them it
    // strikes goes by the order the match keeps its players in, which is by id
    val shooter = m.join(scene.attack.character.id, Centre, Centre, connected = false, id = new java.util.UUID(9L, 0L))
    val others = scene.others.zipWithIndex.map { case (((x, y), dir), i) =>
      val p = m.join(CharacterId.Golem, x, y, connected = false, id = new java.util.UUID(9L, 1L + i))
      p.setDirection(dir)
      p
    }
    for ((fx, fy) <- scene.barrier) others.head.raiseBarrier(GameClock.now(), 60000, Math.atan2(fy, fx).toFloat)
    val (dx, dy) = if (scene.attack.slam) (0f, 0f) else scene.heading
    m.launch(shooter, scene.attack.projectileType, dx, dy)
    val raw = mutable.Buffer[(Seq[(Int, Int, Int)], Seq[(Float, Float)])]()
    var n = 0
    while (m.instance.projectileManager.size > 0 && n < 400) {
      m.tick()
      n += 1
      raw += ((
        (shooter +: others).map(p => (p.getPosition.getX, p.getPosition.getY, p.getHealth)),
        m.instance.projectileManager.getAll.sortBy(_.id).map(s => (s.getX, s.getY))))
    }
    m.instance.stop()
    new Played(Nil, raw.toSeq)
  })
}
