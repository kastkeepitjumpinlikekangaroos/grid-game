package com.gridgame.client.gl

import com.gridgame.client.GameClient
import com.gridgame.common.model._
import com.gridgame.common.world.WorldLoader

import javafx.animation.AnimationTimer
import javafx.application.{Application, Platform}
import javafx.stage.{Screen, Stage}
import org.lwjgl.glfw.GLFW._
import org.lwjgl.opengl.GL11.glFinish

import java.lang.management.ManagementFactory
import java.util.UUID
import scala.jdk.CollectionConverters._

/**
 * Dev tool: what a busy match costs the client. Not shipped — run with
 *
 *   bazel run //src/main/scala/com/gridgame/client:render_bench
 *   bazel run //src/main/scala/com/gridgame/client:render_bench -- --players=24 --projectiles=300 --quality=low
 *
 * Drives the real GLGameRenderer the way the client does — a GLFW window at the client's
 * size, frames driven by a JavaFX AnimationTimer on the FX thread — over a fabricated
 * firefight: players moving and taking damage, projectiles of every registered type in
 * flight, explosions, deaths and teleports going off. No server, no login, no match.
 *
 * Reports: CPU time to build a frame, time until the GPU has finished it (so a fill-rate
 * problem shows instead of hiding behind vsync), bytes allocated on the render thread, GC
 * activity over the run, and on macOS the process footprint as Activity Monitor shows it.
 */
object RenderBench {
  def main(args: Array[String]): Unit = {
    val rest = RenderQuality.configure(args)
    Application.launch(classOf[RenderBenchApp], rest: _*)
  }

  private[gl] def footprint(): String = {
    if (!System.getProperty("os.name", "").toLowerCase.contains("mac")) return ""
    try {
      val pid = ProcessHandle.current().pid()
      val p = new ProcessBuilder("footprint", pid.toString).redirectErrorStream(true).start()
      val out = new String(p.getInputStream.readAllBytes())
      p.waitFor()
      def line(tag: String): String =
        out.linesIterator.find(_.contains(tag)).map(_.trim.split("\\s+").take(2).mkString(" ")).getOrElse("?")
      val total = out.linesIterator.find(_.contains("phys_footprint:")).map(_.split(":")(1).trim).getOrElse("?")
      s"footprint:           $total (graphics ${line("IOAccelerator (graphics)")}, " +
        s"VM_ALLOCATE ${line("untagged (VM_ALLOCATE)")}, IOSurface ${line("IOSurface")}, malloc ${line("MALLOC_SMALL")})"
    } catch { case e: Exception => s"footprint unavailable: ${e.getMessage}" }
  }
}

class RenderBenchApp extends Application {
  /** Read back the default framebuffer and write it as a PNG. */
  private def saveFrame(path: String, w: Int, h: Int): Unit = {
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
    javax.imageio.ImageIO.write(img, "png", new java.io.File(path))
  }

  private def argOf(args: Seq[String], name: String): Option[String] =
    args.find(_.startsWith(s"--$name=")).map(_.substring(name.length + 3))

  override def start(stage: Stage): Unit = {
    val args = getParameters.getRaw.asScala.toSeq
    val frames = argOf(args, "frames").map(_.toInt).getOrElse(900)
    val warmup = argOf(args, "warmup").map(_.toInt).getOrElse(300)
    val nPlayers = argOf(args, "players").map(_.toInt).getOrElse(16)
    val nProjectiles = argOf(args, "projectiles").map(_.toInt).getOrElse(150)
    val mapFile = argOf(args, "map").getOrElse("the_hive.json")
    val screenshot = argOf(args, "screenshot") // --screenshot=out.png saves the last frame

    CharacterDef.all // registers every ProjectileDef
    Platform.setImplicitExit(false)
    val bounds = Screen.getPrimary.getVisualBounds
    val w = argOf(args, "w").map(_.toInt).getOrElse(bounds.getWidth.toInt)
    val h = argOf(args, "h").map(_.toInt).getOrElse(bounds.getHeight.toInt)
    val window = new GLWindow("Render bench", w, h)
    window.create()
    if (args.contains("--novsync")) glfwSwapInterval(0)
    window.show()

    val world = WorldLoader.load("worlds/" + mapFile)
    val client = new GameClient("localhost", 0, world, "Bench")
    val renderer = new GLGameRenderer(client)
    val rng = new java.util.Random(42)
    val home = client.getLocalPosition

    def walkableNear(cx: Int, cy: Int, radius: Int): Position = {
      var tries = 0
      while (tries < 200) {
        val x = cx + rng.nextInt(radius * 2 + 1) - radius
        val y = cy + rng.nextInt(radius * 2 + 1) - radius
        if (world.isWalkable(x, y)) return new Position(x, y)
        tries += 1
      }
      new Position(cx, cy)
    }

    // Players around the local player, a spread of characters
    val chars = CharacterDef.all.toArray
    val players = (0 until nPlayers).map { i =>
      val id = UUID.randomUUID()
      val p = new Player(id, s"Bot$i", walkableNear(home.getX, home.getY, 10), Player.generateColorFromUUID(id), 100)
      p.setCharacterId(chars((i * 7) % chars.length).id.id)
      client.getPlayers.put(id, p)
      p
    }.toArray

    // Items scattered nearby
    val itemTypes = Array[ItemType](ItemType.Gem, ItemType.Heart, ItemType.Star, ItemType.Shield, ItemType.Fence)
    (0 until 10).foreach { i =>
      val pos = walkableNear(home.getX, home.getY, 12)
      client.getItems.put(i, new Item(i, pos.getX, pos.getY, itemTypes(i % itemTypes.length)))
    }

    // Projectiles: cycle through every registered type so every renderer gets exercised
    val types = (0 until 256).map(_.toByte).filter(t => GLProjectileRenderers.getRenderer(t) != null).toArray
    var nextProjId = 1
    var typeCursor = 0
    def spawnProjectile(): Unit = {
      val shooter = players(rng.nextInt(players.length))
      val sp = shooter.getPosition
      val ang = rng.nextDouble() * Math.PI * 2
      val t = types(typeCursor % types.length); typeCursor += 1
      val p = new Projectile(nextProjId, shooter.getId, sp.getX + 0.5f, sp.getY + 0.5f,
        Math.cos(ang).toFloat, Math.sin(ang).toFloat, shooter.getColorRGB, rng.nextInt(100), t)
      client.getProjectiles.put(nextProjId, p)
      nextProjId += 1
    }
    (0 until nProjectiles).foreach(_ => spawnProjectile())

    def stepWorld(frame: Int): Unit = {
      val now = System.currentTimeMillis()
      // Projectiles fly ~20 server ticks a second; retire and replace at max range
      val it = client.getProjectiles.values().iterator()
      var retired = 0
      while (it.hasNext) {
        val p = it.next()
        p.moveStep(0.33f)
        val pDef = ProjectileDef.get(p.projectileType)
        if (p.getDistanceTraveled > pDef.maxRange || p.isOutOfBounds(world)) {
          it.remove()
          retired += 1
          if (pDef.isExplosive)
            client.getExplosionAnimations.put(p.id, Array(now, (p.getX * 1000).toLong, (p.getY * 1000).toLong,
              p.colorRGB.toLong, 3000L))
          pDef.aoeOnMaxRange.foreach(aoe => client.getAoeSplashAnimations.put(p.id + 1000000, Array(now,
            (p.getX * 1000).toLong, (p.getY * 1000).toLong, p.colorRGB.toLong, (aoe.radius * 1000).toLong)))
        }
      }
      var i = 0
      while (i < retired) { spawnProjectile(); i += 1 }

      // Players step a cell every few frames and trade damage
      i = 0
      while (i < players.length) {
        val p = players(i)
        if ((frame + i) % 3 == 0) {
          val pos = p.getPosition
          val nx = pos.getX + rng.nextInt(3) - 1
          val ny = pos.getY + rng.nextInt(3) - 1
          if (world.isWalkable(nx, ny) && Math.abs(nx - home.getX) < 14 && Math.abs(ny - home.getY) < 14) {
            if (nx != pos.getX || ny != pos.getY) p.setDirection(Direction.fromMovement(nx - pos.getX, ny - pos.getY))
            p.setPosition(new Position(nx, ny))
          }
        }
        if ((frame + i * 5) % 45 == 0) {
          val hp = p.getHealth - (5 + rng.nextInt(26))
          p.setHealth(if (hp <= 0) 100 else hp)
        }
        i += 1
      }
      // A death and a teleport somewhere every couple of seconds
      if (frame % 120 == 0) {
        val p = players(rng.nextInt(players.length)); val pos = p.getPosition
        client.getDeathAnimations.put(UUID.randomUUID(), Array(now, pos.getX.toLong, pos.getY.toLong,
          p.getColorRGB.toLong, p.getCharacterId.toLong))
      }
      if (frame % 90 == 45) {
        val p = players(rng.nextInt(players.length)); val pos = p.getPosition
        val to = walkableNear(pos.getX, pos.getY, 5)
        client.getTeleportAnimations.put(p.getId, Array(now, pos.getX.toLong, pos.getY.toLong,
          to.getX.toLong, to.getY.toLong, p.getColorRGB.toLong))
      }
    }

    val threads = ManagementFactory.getThreadMXBean.asInstanceOf[com.sun.management.ThreadMXBean]
    val gcs = ManagementFactory.getGarbageCollectorMXBeans.asScala.toSeq
    def gcCount = gcs.map(_.getCollectionCount).sum
    def gcMs = gcs.map(_.getCollectionTime).sum

    val cpuNs = new Array[Long](frames)
    val gpuNs = new Array[Long](frames)
    val threadCpuNs = new Array[Long](frames)
    var alloc0 = 0L; var gc0 = 0L; var gcMs0 = 0L; var wall0 = 0L
    var frame = 0
    var lastNs = 0L

    // Same loop shape as ClientMain.showGameScene
    val loop: AnimationTimer = new AnimationTimer {
      override def handle(now: Long): Unit = {
        glfwPollEvents()
        if (window.shouldClose) { finish(frame - warmup); return }
        stepWorld(frame)
        val dt = if (lastNs == 0L) 1.0 / 60 else Math.min((now - lastNs) / 1e9, 0.05)
        lastNs = now
        if (frame == warmup) {
          alloc0 = threads.getCurrentThreadAllocatedBytes
          gc0 = gcCount; gcMs0 = gcMs; wall0 = System.nanoTime()
        }
        val c0 = threads.getCurrentThreadCpuTime
        val t0 = System.nanoTime()
        renderer.render(dt, window.fbWidth, window.fbHeight, window.width, window.height)
        val t1 = System.nanoTime()
        val c1 = threads.getCurrentThreadCpuTime
        glFinish()
        val t2 = System.nanoTime()
        val idx = frame - warmup
        if (idx >= 0) { cpuNs(idx) = t1 - t0; gpuNs(idx) = t2 - t0; threadCpuNs(idx) = c1 - c0 }
        if (idx == frames - 1) screenshot.foreach(path => saveFrame(path, window.fbWidth, window.fbHeight))
        window.swapBuffers()
        frame += 1
        if (frame - warmup >= frames) finish(frames)
      }

      private def finish(n0: Int): Unit = {
        stop()
        val n = Math.max(1, n0)
        val wallMs = (System.nanoTime() - wall0) / 1e6
        val allocPerFrame = (threads.getCurrentThreadAllocatedBytes - alloc0).toDouble / n
        def pct(a: Array[Long], p: Double): Double = {
          val s = a.take(n).sorted
          s(Math.min(n - 1, (n * p).toInt)) / 1e6
        }
        def mean(a: Array[Long]): Double = a.take(n).sum / 1e6 / n
        val gcN = gcCount - gc0; val gcT = gcMs - gcMs0
        val fp = RenderBench.footprint()
        System.gc(); System.gc()
        val heap = ManagementFactory.getMemoryMXBean.getHeapMemoryUsage
        println(f"render bench: ${window.fbWidth}x${window.fbHeight} fb (${window.width}x${window.height} window), " +
          f"quality ${RenderQuality.tierName}, $nPlayers players, ${client.getProjectiles.size} projectiles, $n frames")
        println(f"  frame build (wall):  mean ${mean(cpuNs)}%.2f ms, p50 ${pct(cpuNs, 0.5)}%.2f, p99 ${pct(cpuNs, 0.99)}%.2f, max ${pct(cpuNs, 1.0)}%.2f")
        println(f"  frame build (CPU):   mean ${mean(threadCpuNs)}%.2f ms, p50 ${pct(threadCpuNs, 0.5)}%.2f, p99 ${pct(threadCpuNs, 0.99)}%.2f  (render thread CPU time)")
        println(f"  frame done (GPU):    mean ${mean(gpuNs)}%.2f ms, p50 ${pct(gpuNs, 0.5)}%.2f, p99 ${pct(gpuNs, 0.99)}%.2f, max ${pct(gpuNs, 1.0)}%.2f")
        println(f"  wall:                ${wallMs / n}%.2f ms/frame (includes vsync)")
        println(f"  allocated:           ${allocPerFrame / 1024}%.1f KB/frame on the render thread (${allocPerFrame * 60 / 1048576}%.1f MB/s at 60 fps)")
        println(f"  GC:                  $gcN collections, $gcT ms total during the run")
        println(f"  heap:                ${heap.getUsed / 1048576.0}%.1f MB live after GC, ${heap.getCommitted / 1048576.0}%.1f MB committed")
        println(s"  $fp")
        renderer.dispose()
        window.destroy()
        GLFWManager.terminate()
        Platform.exit()
        System.exit(0)
      }
    }
    loop.start()
  }
}
