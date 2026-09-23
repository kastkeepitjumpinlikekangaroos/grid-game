package com.gridgame.client.gl

import com.gridgame.client.GameClient
import com.gridgame.common.{Constants, WorldRegistry}
import com.gridgame.common.model._
import com.gridgame.common.world.WorldLoader

import javafx.animation.AnimationTimer
import javafx.application.{Application, Platform}
import javafx.stage.Stage
import org.lwjgl.glfw.GLFW._

import java.io.{File, PrintWriter}
import java.util.UUID
import scala.jdk.CollectionConverters._

/**
 * Dev tool: can everything be seen on every map? Not shipped — run with
 *
 *   bazel run //src/main/scala/com/gridgame/client:render_audit -- --out=/tmp/audit
 *   bazel run //src/main/scala/com/gridgame/client:render_audit -- --out=/tmp/audit --maps=the_snowglobe --what=projectiles
 *   python3 scripts/render_audit.py /tmp/audit          # rank the worst, write contact sheets
 *
 * For every map it takes the ground a match is played on (every walkable tile covering at least
 * 2% of the arena, plus the pools a shot flies over), lays a field of each, and stands the whole
 * roster on it, then flies every projectile type over it, through the real GLGameRenderer: the
 * map's own background, grade, lighting and post-processing, at the size the player sees. Every
 * shot is taken twice, once as it is and once with what is being judged taken out — characters
 * keep their shadow, light and name plate and lose only their sprite — so the difference between
 * the two is exactly what that sprite or projectile adds to the frame. `scripts/render_audit.py`
 * turns those pairs into contrast figures and contact sheets of the worst cases.
 *
 * This is the loop for anything that could change how readable a character or an ability is:
 * sprite art, projectile art, tiles, backgrounds, the grade.
 */
object RenderAudit {
  def main(args: Array[String]): Unit = {
    val rest = RenderQuality.configure(if (args.exists(_.startsWith("--quality="))) args else args :+ "--quality=high")
    Application.launch(classOf[RenderAuditApp], rest: _*)
  }
}

class RenderAuditApp extends Application {
  private def argOf(args: Seq[String], name: String): Option[String] =
    args.find(_.startsWith(s"--$name=")).map(_.substring(name.length + 3))

  /** One picture to take: a field of `ground` on `map`, with these characters or projectiles on it. */
  private case class Shot(map: String, background: String, ground: Tile, kind: String, ids: Seq[Int], index: Int)

  override def start(stage: Stage): Unit = {
    val args = getParameters.getRaw.asScala.toSeq
    val out = new File(argOf(args, "out").getOrElse("render_audit"))
    out.mkdirs()
    val maps = argOf(args, "maps").map(_.split(",").map(m => if (m.endsWith(".json")) m else m + ".json").toSeq)
      .getOrElse(WorldRegistry.displayNames.indices.map(WorldRegistry.getFilename))
    val what = argOf(args, "what").map(_.split(",").toSet).getOrElse(Set("characters", "projectiles"))
    val w = argOf(args, "w").map(_.toInt).getOrElse(1280)
    val h = argOf(args, "h").map(_.toInt).getOrElse(720)

    CharacterDef.all // registers every ProjectileDef
    Platform.setImplicitExit(false)
    val window = new GLWindow("Render audit", w, h)
    window.create()
    window.show()

    val slotList = slots
    val characters = CharacterDef.all.map(_.id.id.toInt).toIndexedSeq
    val projectileTypes = (0 until 256).map(_.toByte)
      .filter(t => GLProjectileRenderers.getRenderer(t) != null).map(_ & 0xFF).toIndexedSeq

    // A field is a ground under a sky: maps that share both (the four space arenas are all circuit
    // under stars) are shot once, under the first of them
    val seen = scala.collection.mutable.Set[(String, Tile)]()
    val shots = maps.flatMap { map =>
      val world = WorldLoader.load("worlds/" + map)
      val (allGrounds, allPools) = groundsOf(world)
      val grounds = allGrounds.filter(g => seen.add((world.background, g)))
      val pools = allPools.filter(g => seen.add((world.background, g)))
      val c = if (what("characters")) grounds.flatMap { g =>
        characters.grouped(slotList.size).zipWithIndex.map { case (ids, i) => Shot(map, world.background, g, "char", ids, i) }
      } else Nil
      val p = if (what("projectiles")) (grounds ++ pools).flatMap { g =>
        projectileTypes.grouped(slotList.size).zipWithIndex.map { case (ids, i) => Shot(map, world.background, g, "proj", ids, i) }
      } else Nil
      println(s"$map (${world.background}): ground ${grounds.map(_.name).mkString(", ")}; pools ${pools.map(_.name).mkString(", ")}" +
        (if (grounds.size + pools.size < allGrounds.size + allPools.size) " (the rest shot under an earlier map)" else ""))
      c ++ p
    }
    println(s"${shots.size} shots, two renders each")

    val index = new PrintWriter(new File(out, "index.csv"))
    index.println("file,map,background,ground,kind,id,name,x0,y0,x1,y1")

    // One client per field: the camera sits on the local player, who stands in the middle of it
    var client: GameClient = null
    var renderer: GLGameRenderer = null
    var fieldKey = ""
    var shotIdx = 0
    var phase = 0 // 0 = set up a shot, 1..n = frames before the capture of A, then B
    var lastNs = 0L
    val SettleFrames = 45 // the match-start fade is 500ms, and the camera and lights must settle

    def setUp(s: Shot, hidden: Boolean): Unit = {
      val key = s"${s.map}/${s.ground.name}"
      if (key != fieldKey) {
        if (renderer != null) renderer.dispose()
        val field = fieldOf(s.ground, s.background)
        client = new GameClient("localhost", 0, field, "Audit")
        client.selectedCharacterId = CharacterId.DEFAULT.id
        renderer = new GLGameRenderer(client)
        fieldKey = key
      }
      client.getPlayers.clear()
      client.getProjectiles.clear()
      // A match holds a lobby's worth of characters; the whole roster overflows the atlas, so
      // start each shot with an empty one (safe here, between frames)
      if (!hidden) GLSpriteGenerator.clearCache()
      val home = client.getLocalPosition
      s.ids.zip(slotList).foreach { case (id, (a, b)) =>
        val cx = home.getX + a + b; val cy = home.getY + b - a
        if (s.kind == "char") {
          // Hidden: a stranger in the same colour (so the same light) with an id that has no
          // sprite sheet, so everything but the sprite is still drawn. A fresh id, not the same
          // player changed: a player whose health moved would get a damage number.
          val pid = new UUID(if (hidden) 0xB0D17L else 0xA0D17L, id.toLong)
          val color = Player.generateColorFromUUID(new UUID(0xA0D17L, id.toLong))
          val p = new Player(pid, s"C$id", new Position(cx, cy), color, 100)
          p.setCharacterId(if (hidden) 200.toByte else id.toByte)
          p.setHealth(p.getMaxHealth)
          client.getPlayers.put(pid, p)
        } else if (!hidden) {
          val t = id.toByte
          val dist = ProjectileDef.get(t).effectiveMaxRange(0).toFloat * 0.35f
          // Travelling screen right-and-down, a third of the way into its range, landing on the slot
          val proj = new Projectile(id + 1, new UUID(0xA0D17L, 999L), cx.toFloat, cy.toFloat, 1f, 0f, 0x88AAFF, 0, t)
          var guard = 0
          while (proj.getDistanceTraveled < dist && guard < 64) { proj.moveStep(1f); guard += 1 }
          val back = new Projectile(id + 1, proj.ownerId, cx - (proj.getX - cx), cy - (proj.getY - cy), 1f, 0f, 0x88AAFF, 0, t)
          guard = 0
          while (back.getDistanceTraveled < dist && guard < 64) { back.moveStep(1f); guard += 1 }
          client.getProjectiles.put(id + 1, back)
        }
      }
    }

    def rects(s: Shot): Seq[(Int, (Int, Int, Int, Int))] = {
      // Scene units to framebuffer pixels: the world is drawn at CAMERA_ZOOM in window units
      val k = Constants.CAMERA_ZOOM * window.fbWidth.toFloat / window.width
      val cxPx = window.fbWidth / 2f; val cyPx = window.fbHeight / 2f
      s.ids.zip(slotList).map { case (id, (a, b)) =>
        val sx = cxPx + a * 2 * Constants.ISO_HALF_W * k
        val sy = cyPx + b * 2 * Constants.ISO_HALF_H * k
        val r = if (s.kind == "char") {
          val sz = Constants.PLAYER_DISPLAY_SIZE_PX * 1.05f * k
          (sx - sz / 2, sy - sz, sx + sz / 2, sy + 2 * k)
        } else (sx - 58 * k, sy - 48 * k, sx + 58 * k, sy + 48 * k)
        (id, (r._1.toInt, r._2.toInt, r._3.toInt, r._4.toInt))
      }
    }

    val loop: AnimationTimer = new AnimationTimer {
      override def handle(now: Long): Unit = {
        glfwPollEvents()
        if (window.shouldClose || shotIdx >= shots.size) { finish(); return }
        val s = shots(shotIdx)
        val dt = if (lastNs == 0L) 1.0 / 60 else Math.min((now - lastNs) / 1e9, 0.05)
        lastNs = now
        if (phase == 0) setUp(s, hidden = false)
        if (phase == SettleFrames + 1) setUp(s, hidden = true)
        renderer.render(dt, window.fbWidth, window.fbHeight, window.width, window.height)
        val base = f"${s.map.stripSuffix(".json")}_${s.ground.name}_${s.kind}${s.index}%02d"
        if (phase == SettleFrames) saveFrame(new File(out, base + "_a.png"), window.fbWidth, window.fbHeight)
        if (phase == SettleFrames + 4) {
          saveFrame(new File(out, base + "_b.png"), window.fbWidth, window.fbHeight)
          rects(s).foreach { case (id, (x0, y0, x1, y1)) =>
            val name = if (s.kind == "char") CharacterDef.get(id.toByte).id.name
                       else ProjectileAudit.nameOf(id.toByte)
            index.println(s"$base,${s.map.stripSuffix(".json")},${s.background},${s.ground.name},${s.kind},$id,$name,$x0,$y0,$x1,$y1")
          }
          shotIdx += 1
          phase = 0
          if (shotIdx % 10 == 0) println(s"  $shotIdx / ${shots.size}")
        } else phase += 1
        window.swapBuffers()
      }

      private def finish(): Unit = {
        stop()
        index.close()
        println(s"wrote ${shots.size} shot pairs to ${out.getAbsolutePath}")
        if (renderer != null) renderer.dispose()
        window.destroy()
        GLFWManager.terminate()
        Platform.exit()
        System.exit(0)
      }
    }
    loop.start()
  }

  /**
   * Where things stand, as (a, b) steps from the local player: a steps of (1, -1) go right across
   * the screen (40 units each) and b steps of (1, 1) go down it (20 units each). Six columns and
   * four rows, leaving the middle column to the local player, far enough apart that a sprite, its
   * name plate and a projectile's trail never reach the next.
   */
  private def slots: Seq[(Int, Int)] =
    for (b <- Seq(-8, -3, 2, 7); a <- Seq(-9, -6, -3, 3, 6, 9)) yield (a, b)

  /** The ground a match on this map is played on: walkable tiles covering at least 2% of the open
    * ground, most common first, and the pools (water, lava) covering 2% of the map. */
  private def groundsOf(world: WorldData): (Seq[Tile], Seq[Tile]) = {
    val counts = new java.util.HashMap[Tile, Integer]()
    var open = 0; var cells = 0
    var y = 0
    while (y < world.height) {
      var x = 0
      while (x < world.width) {
        val t = world.getTile(x, y)
        cells += 1
        if (t.walkable) open += 1
        counts.merge(t, 1, (p, q) => p + q)
        x += 1
      }
      y += 1
    }
    val all = counts.asScala.toSeq.sortBy(-_._2.intValue())
    val grounds = all.collect { case (t, n) if t.walkable && n >= open * 0.02 => t }
    val pools = all.collect { case (t, n) if (t.form eq TileForm.Pool) && n >= cells * 0.02 => t }
    (grounds, pools)
  }

  /** A 64-cell square of one tile under a map's sky, with the local player in the middle of it. */
  private def fieldOf(ground: Tile, background: String): WorldData = {
    val n = 64
    val tiles = Array.fill(n, n)(ground)
    new WorldData(s"audit-${ground.name}", n, n, tiles, Seq(new Position(n / 2, n / 2)), background)
  }

  private def saveFrame(file: File, w: Int, h: Int): Unit = {
    val buf = org.lwjgl.BufferUtils.createByteBuffer(w * h * 4)
    org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, 0)
    org.lwjgl.opengl.GL11.glReadPixels(0, 0, w, h, org.lwjgl.opengl.GL11.GL_RGBA,
      org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, buf)
    val img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB)
    var y = 0
    while (y < h) {
      var x = 0
      while (x < w) {
        val o = ((h - 1 - y) * w + x) * 4
        img.setRGB(x, y, ((buf.get(o) & 0xFF) << 16) | ((buf.get(o + 1) & 0xFF) << 8) | (buf.get(o + 2) & 0xFF))
        x += 1
      }
      y += 1
    }
    javax.imageio.ImageIO.write(img, "png", file)
  }
}

/** Names of the projectile types, read off ProjectileType's constants. */
private[gl] object ProjectileAudit {
  private lazy val names: Map[Int, String] = ProjectileType.getClass.getDeclaredMethods
    .filter(m => m.getParameterCount == 0 && m.getReturnType == java.lang.Byte.TYPE)
    .map(m => (m.invoke(ProjectileType).asInstanceOf[java.lang.Byte].byteValue() & 0xFF) -> m.getName)
    .toMap
  def nameOf(t: Byte): String = names.getOrElse(t & 0xFF, s"type${t & 0xFF}")
}
