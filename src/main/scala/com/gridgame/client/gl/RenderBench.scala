package com.gridgame.client.gl

import com.gridgame.client.GameClient
import com.gridgame.common.Constants
import com.gridgame.common.model._
import com.gridgame.common.protocol.{GameEvent, GameEventPacket, Packet}
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
 *   bazel run //src/main/scala/com/gridgame/client:render_bench -- --barriers   # some players hold barriers
 *   bazel run //src/main/scala/com/gridgame/client:render_bench -- --traps      # traps on the ground
 *   bazel run //src/main/scala/com/gridgame/client:render_bench -- --divider    # a Teams match's opening wall
 *   bazel run //src/main/scala/com/gridgame/client:render_bench -- --ceasefire  # a free-for-all's opening ceasefire
 *   bazel run //src/main/scala/com/gridgame/client:render_bench -- --map=the_meadow.json --at=60,16
 *                                                                  # another map, standing on one cell of it
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

    // --at=x,y stands the local player on that cell (it must be open ground), for looking at one
    // part of a map: the client puts us on one of the world's spawn points, so give it only that one
    val loaded = WorldLoader.load("worlds/" + mapFile)
    val world = argOf(args, "at").map(_.split(",").map(_.trim.toInt)) match {
      case Some(Array(x, y)) =>
        new WorldData(loaded.name, loaded.width, loaded.height, loaded.tiles, Seq(new Position(x, y)), loaded.background)
      case _ => loaded
    }
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
    // --effects puts a status effect on everyone, cycling through them, so the effect renderers
    // are in the frame too — they are some of the most expensive things drawn over a player.
    // Off by default, so the numbers stay comparable with the ones in CLAUDE.md.
    val withEffects = args.contains("--effects")
    // --barriers has every fourth player hold a barrier up, turning, and struck now and then. Off by
    // default for the same reason.
    val withBarriers = args.contains("--barriers")
    if (withBarriers) client.localTeamId = 1
    // --traps scatters 30 traps of every kind over the ground, half of them ours (drawn plainly)
    // and half an enemy's (drawn faint), with some still arming and some going off. Off by
    // default for the same reason as the two above.
    val withTraps = args.contains("--traps")
    val nTraps = if (withTraps) argOf(args, "traps").map(_.toInt).getOrElse(30) else 0
    // The opening of a match (MatchOpening), which is otherwise only the first thirty seconds of
    // a real one: --divider raises the wall a Teams match opens with, with shots striking it, and
    // --ceasefire holds everyone's fire the way a free-for-all's opening does. Both are told to
    // the client exactly as the server tells it, so the countdown under the clock runs; the wall
    // is then stood a few cells from the local player rather than down the middle of the map,
    // since what there is to look at is the wall itself. `=<seconds>` shortens the opening, which
    // is how to watch it end.
    val withDivider = args.exists(a => a == "--divider" || a.startsWith("--divider="))
    val withLock = args.exists(a => a == "--ceasefire" || a.startsWith("--ceasefire="))
    if (withDivider || withLock) {
      val flag = if (withDivider) "divider" else "ceasefire"
      val ms = argOf(args, flag).map(_.toDouble).map(secs => (secs * 1000).toInt)
        .getOrElse(Constants.MATCH_OPENING_MS)
      val rules = if (withDivider) MatchOpening.DIVIDER else MatchOpening.NO_ATTACKS
      client.processPacket(new GameEventPacket(1, new UUID(0L, 0L), Packet.getCurrentTimestamp,
        GameEvent.MATCH_OPENING, 0.toShort, 0, 0.toShort, 0.toShort, null, 0.toByte, 0.toShort,
        0.toShort, 0.toByte, ms, rules))
      if (withDivider) {
        val raised = client.divider
        world.divider = new TeamDivider(raised.axisX, home.getX + 5, raised.endsAt)
      }
      // The countdown under the match clock is part of what there is to look at, and the top of
      // the HUD is only drawn in a match
      client.clientState = com.gridgame.client.ClientState.PLAYING
    }
    val players = (0 until nPlayers).map { i =>
      val id = UUID.randomUUID()
      val p = new Player(id, s"Bot$i", walkableNear(home.getX, home.getY, 10), Player.generateColorFromUUID(id), 100)
      p.setCharacterId(chars((i * 7) % chars.length).id.id)
      if (withEffects) {
        val forever = System.currentTimeMillis() + 3600000L
        (i % 7) match {
          case 0 => p.setFrozenUntil(forever)
          case 1 => p.setFrozenUntil(forever); p.setStunnedUntil(forever) // a stun is a freeze wearing stars
          case 2 => p.applyPoison(1, 3600000, 1000, null)
          case 3 => p.applyBurn(1, 3600000, 1000, null)
          case 4 => p.setRootedUntil(forever)
          case 5 => p.setSlowedUntil(forever)
          case _ => p.setSpeedBoostUntil(forever)
        }
      }
      // Half of them on our side, so both tints are in the frame
      if (withBarriers && i % 4 == 0) {
        p.raiseBarrier(System.currentTimeMillis(), 3600000, (i * 0.7).toFloat)
        p.setTeamId(if (i % 8 == 0) 1 else 2)
      }
      client.getPlayers.put(id, p)
      p
    }.toArray

    // Items scattered nearby
    val itemTypes = Array[ItemType](ItemType.Gem, ItemType.Heart, ItemType.Star, ItemType.Shield, ItemType.Fence)
    (0 until 10).foreach { i =>
      val pos = walkableNear(home.getX, home.getY, 12)
      client.getItems.put(i, new Item(i, pos.getX, pos.getY, itemTypes(i % itemTypes.length)))
    }

    // Traps: every kind, ours and an enemy's, some of them still arming
    val trapTypes = TrapDef.all.map(_.id).toArray
    val trapOwner = UUID.randomUUID()
    (0 until nTraps).foreach { i =>
      val pos = walkableNear(home.getX, home.getY, 11)
      val now = System.currentTimeMillis()
      // Every third is still arming, and a couple are nearly out of time
      val placed = now - (if (i % 3 == 0) 0L else 4000L)
      val life = if (i % 7 == 0) 900L else TrapDef.get(trapTypes(i % trapTypes.length)).lifetimeMs.toLong
      client.getTraps.put(i, new Trap(i, if (i % 2 == 0) client.getLocalPlayerId else trapOwner,
        0.toByte, pos.getX, pos.getY, trapTypes(i % trapTypes.length), placed,
        placed + TrapDef.get(trapTypes(i % trapTypes.length)).armDelayMs, placed + life))
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
      // Barriers turning as their holders aim, and stopping a shot every half second or so
      if (withBarriers) {
        i = 0
        while (i < players.length) {
          val p = players(i)
          if (p.hasBarrier) {
            p.setBarrierAngle(p.getBarrierAngle + 0.02f)
            if ((frame + i) % 30 == 0) {
              val a = p.getBarrierAngle
              val pos = p.getPosition
              client.recordBarrierImpact(p.getId, pos.getX + 2f * Math.cos(a).toFloat,
                pos.getY + 2f * Math.sin(a).toFloat, now)
            }
          }
          i += 1
        }
      }
      // Traps going off and being laid again, so the sprung-trap effects are in the frame too
      if (withTraps && frame % 20 == 0) {
        val id = (frame / 20) % Math.max(1, nTraps)
        val trap = client.getTraps.remove(id)
        if (trap != null) {
          client.getTrapEffects.put(id, Array(now, (trap.x * 1000).toLong, (trap.y * 1000).toLong,
            trap.trapType.toLong))
        }
        val pos = walkableNear(home.getX, home.getY, 11)
        val tType = trapTypes(id % trapTypes.length)
        client.getTraps.put(id, new Trap(id, if (id % 2 == 0) client.getLocalPlayerId else trapOwner,
          0.toByte, pos.getX, pos.getY, tType, now, now + TrapDef.get(tType).armDelayMs,
          now + TrapDef.get(tType).lifetimeMs))
      }

      // Shots stopping on the divider, so its flash is in the frame too
      if (withDivider && frame % 25 == 0) {
        val d = world.divider
        val along = home.getY + rng.nextInt(11) - 5
        client.recordDividerImpact(d.at.toFloat, along.toFloat, now)
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
          f"quality ${RenderQuality.tierName}, $nPlayers players, ${client.getProjectiles.size} projectiles, " +
          f"${if (withEffects) "status effects, " else ""}${if (withBarriers) "barriers, " else ""}" +
          f"${if (withTraps) s"$nTraps traps, " else ""}$n frames")
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
