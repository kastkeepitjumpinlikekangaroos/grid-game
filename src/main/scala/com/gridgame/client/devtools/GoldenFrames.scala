package com.gridgame.client.devtools

import com.gridgame.client.render.FrameClock
import com.gridgame.common.model._
import com.gridgame.common.protocol._
import com.gridgame.common.world.WorldLoader

import org.lwjgl.glfw.GLFW._

import java.io.File
import java.util.UUID
import javax.imageio.ImageIO
import com.gridgame.client.game.{ClientState, FadingProjectile, GameClient}
import com.gridgame.client.gl.{GLFWManager, GLWindow, RenderQuality}
import com.gridgame.client.render.GLGameRenderer
import com.gridgame.client.render.projectiles.GLProjectileRenderers

/**
 * Dev tool: fixed scenes drawn by the real GLGameRenderer on a stopped clock, so two builds of the
 * renderer can be compared pixel for pixel. Not shipped — run with
 *
 *   GRIDGAME_AUDIO=off bazel run //src/main/scala/com/gridgame/client:golden_frames -- out/dir
 *   GRIDGAME_AUDIO=off bazel run //src/main/scala/com/gridgame/client:golden_frames -- out/dir --quality=low
 *
 * then run it again on the other build and compare the directories (`cmp`, or any image diff).
 * A refactor of the renderer should leave every PNG byte for byte the same.
 *
 * Every scene is built from fixed ids, positions and characters, and everything in it that moves
 * with time is set against the frame's own clock (FrameClock), which is held still at [[T]] for
 * the captured frame. What the client works out against the system clock (its own status timers,
 * the match timer, the opening's countdown) is either set so it can't change while a scene is
 * drawn, or left out.
 */
object GoldenFrames {
  /** The time of the captured frame: in the future, so status timers set against it are running
    * on the system clock too, and about as big as a real time, so it rounds as one does. */
  val T: Long = 1900000000000L

  /** Frames drawn before the one captured, this far apart, so the match's fade-in is over. */
  private val WarmupFrames = 3
  private val FrameGapMs = 250L

  private val W = 1280
  private val H = 720

  final class HeldClock extends FrameClock {
    var ms: Long = T
    def nowMs(): Long = ms
    def nowNanos(): Long = ms * 1000000L
  }

  /** One picture: a map, where the local player stands, and what is going on round them.
    * `background` draws the map under another sky than its own. */
  final case class Scene(name: String, map: String, at: (Int, Int), build: Stage => Unit,
                         background: Option[String] = None)

  /** What a scene is built on. */
  final class Stage(val client: GameClient, val world: WorldData, val home: (Int, Int)) {
    private var seq = 1
    def nextSeq(): Int = { seq += 1; seq }
    val me: UUID = client.getLocalPlayerId

    /** Walkable cells round home, nearest first, in a fixed order. */
    lazy val open: IndexedSeq[(Int, Int)] = {
      val (hx, hy) = home
      val cells = for {
        dy <- -14 to 14; dx <- -18 to 18
        x = hx + dx; y = hy + dy
        if (dx != 0 || dy != 0) && x >= 0 && y >= 0 && x < world.width && y < world.height && world.isWalkable(x, y)
      } yield (x, y)
      cells.sortBy { case (x, y) => ((x - hx) * (x - hx) + (y - hy) * (y - hy), y, x) }
    }

    def cell(i: Int): (Int, Int) = open(i % open.size)

    def remote(i: Int, charIdx: Int, at: (Int, Int), team: Byte = 0): Player = {
      val id = new UUID(0L, 1000L + i)
      val p = new Player(id, s"P$i", new Position(at._1, at._2), Player.generateColorFromUUID(id), 100)
      p.setCharacterId(CharacterDef.all(charIdx % CharacterDef.all.size).id.id)
      p.setTeamId(team)
      p.setDirection(Direction.fromId(i % 4))
      client.getPlayers.put(id, p)
      p
    }

    def localUpdate(flags: Int = 0, flags2: Int = 0, health: Int = 100, slowPercent: Int = 0): Unit = {
      val pos = client.getLocalPosition
      client.processPacket(new PlayerUpdatePacket(nextSeq(), me, Packet.getCurrentTimestamp, pos, 0xFF00AA00,
        health, 0, flags, client.selectedCharacterId, client.localTeamId, 0, flags2, 0, slowPercent))
    }

    def timer(remainingSeconds: Int): Unit =
      client.processPacket(new GameEventPacket(nextSeq(), new UUID(0L, 0L), Packet.getCurrentTimestamp,
        GameEvent.TIME_SYNC, 7.toShort, remainingSeconds, 0.toShort, 0.toShort, null, 0.toByte, 0.toShort,
        0.toShort, 0.toByte))

    def inventory(itemType: ItemType, n: Int): Unit =
      (0 until n).foreach { k =>
        client.processPacket(new ItemPacket(nextSeq(), me, 0, 0, itemType.id, 5000 + itemType.id * 10 + k,
          ItemAction.INVENTORY))
      }

    /** Run just before every frame: the match timer, which the client counts down on the system
      * clock, is told again, so it reads the same however long the frames took. */
    var beforeFrame: () => Unit = () => timer(300)
    /** Run between the first frame and the second: health changes, which is what damage numbers are. */
    var afterFirstFrame: () => Unit = () => ()
  }

  // ── What goes in the scenes ─────────────────────────────────────────────

  private lazy val projectileTypes: IndexedSeq[Byte] =
    (0 until 256).map(_.toByte).filter(t => GLProjectileRenderers.getRenderer(t) != null)

  /** Every (character, type, radius) that sets off a blast, as a match throws them. */
  private lazy val blastRoster: IndexedSeq[(Byte, Byte, Float)] = CharacterDef.all.flatMap { c =>
    Seq(c.primaryProjectileType, c.qAbility.projectileType, c.eAbility.projectileType).flatMap { t =>
      if (t >= -4 && t < 0) None
      else {
        val d = ProjectileDef.get(t)
        d.aoeOnHit.map(_.radius).orElse(d.explosionConfig.map(_.blastRadius)).orElse(d.aoeOnMaxRange.map(_.radius))
          .map(r => (c.id.id, t, r))
      }
    }
  }.distinct.toIndexedSeq

  private def projectiles(s: Stage, types: Seq[Byte], firstId: Int, spread: Int): Unit = {
    types.zipWithIndex.foreach { case (t, i) =>
      val (x, y) = s.cell(spread + i * 3)
      val ang = i * 0.61
      val owner = if (i % 5 == 0) s.me else new UUID(0L, 1000L + (i % 12))
      val p = new Projectile(firstId + i, owner, x + 0.25f, y - 0.2f, Math.cos(ang).toFloat, Math.sin(ang).toFloat,
        Player.generateColorFromUUID(owner), (i * 37) % 100, t)
      var k = 0
      while (k < 3 + i % 5) { p.moveStep(0.33f); k += 1 }
      s.client.getProjectiles.put(firstId + i, p)
    }
  }

  private def blasts(s: Stage, n: Int, from: Int): Unit = {
    var i = 0
    while (i < n) {
      val (who, t, r) = blastRoster((from + i * 7) % blastRoster.size)
      val (x, y) = s.cell(i * 11 + 5)
      val key = 900000L + from * 100 + i
      val age = 40L + (i * 97) % 900
      s.client.getBlasts.put(key, Array(T - age, ((x + 0.3f) * 1000).toLong, ((y - 0.2f) * 1000).toLong,
        0xFF8899FFL, (r * 1000).toLong, t.toLong, who.toLong, 0L,
        ((key ^ (key >>> 29)) * 0x9E3779B97F4A7C15L) >>> 40, 0L))
      i += 1
    }
  }

  private def everything(s: Stage, withEffects: Boolean): Unit = {
    val c = s.client
    // Players round us, every fourth an ally holding a barrier up
    val players = (0 until 14).map { i =>
      val p = s.remote(i, i * 8 + 3, s.cell(i * 6 + 2), team = (if (i % 2 == 0) 1 else 2).toByte)
      if (withEffects) (i % 9) match {
        case 0 => p.setFrozenUntil(T + 3600000L)
        case 1 => p.setFrozenUntil(T + 3600000L); p.setStunnedUntil(T + 3600000L)
        case 2 => p.applyPoison(1, 3600000, 1000, null)
        case 3 => p.applyBurn(1, 3600000, 1000, null)
        case 4 => p.setRootedUntil(T + 3600000L)
        case 5 => p.setSlowedUntil(T + 3600000L); p.setSlowMultiplier(0.4f)
        case 6 => p.setSpeedBoostUntil(T + 3600000L)
        case 7 => p.setShieldUntil(T + 3600000L); p.setGemBoostUntil(T + 3600000L)
        case _ => p.setPhasedUntil(T + 3600000L); p.setChargeLevel(70)
      }
      if (i % 4 == 1) p.raiseBarrier(T - 400L - i * 30, 3600000, (i * 0.9).toFloat)
      if (i % 3 == 0) p.setHealth(35 + i * 3)
      p
    }
    players.filter(_.hasBarrier).zipWithIndex.foreach { case (p, k) =>
      val a = p.getBarrierAngle
      val pos = p.getPosition
      c.recordBarrierImpact(p.getId, pos.getX + 2f * Math.cos(a).toFloat, pos.getY + 2f * Math.sin(a).toFloat, T - 60L - k * 70)
    }
    // Hit, just now and a moment ago
    Seq(1, 4, 7, 10).foreach { i =>
      val p = players(i)
      c.recordHit(p.getId, 0xFFFF7733, 0.8f, -0.6f, projectileTypes(i * 5), T - 40L * i)
    }
    // Items on the ground
    val kinds = Array[ItemType](ItemType.Gem, ItemType.Heart, ItemType.Star, ItemType.Shield, ItemType.Fence)
    (0 until 10).foreach { i =>
      val (x, y) = s.cell(i * 9 + 4)
      c.getItems.put(i, new Item(i, x, y, kinds(i % kinds.length)))
    }
    // Traps: every kind, ours and an enemy's, arming, armed, running out
    val trapTypes = TrapDef.all.map(_.id).toIndexedSeq
    (0 until 10).foreach { i =>
      val (x, y) = s.cell(i * 13 + 7)
      val tt = trapTypes(i % trapTypes.size)
      val d = TrapDef.get(tt)
      val placed = if (i % 3 == 0) T - 200L else T - 4000L
      val expires = if (i % 4 == 3) T + 300L else placed + d.lifetimeMs
      c.getTraps.put(i, new Trap(i, if (i % 2 == 0) s.me else new UUID(0L, 1001L), 0.toByte, x, y, tt,
        placed, placed + d.armDelayMs, expires))
    }
    (0 until 5).foreach { i =>
      val (x, y) = s.cell(i * 17 + 9)
      c.getTrapEffects.put(100 + i, Array(T - 80L * (i + 1), x * 1000L, y * 1000L, trapTypes(i % trapTypes.size).toLong))
    }
    // Deaths and teleports going off
    (0 until 3).foreach { i =>
      val (x, y) = s.cell(i * 19 + 3)
      c.getDeathAnimations.put(new UUID(1L, i), Array(T - 150L - i * 350, x.toLong, y.toLong, 0xFF44CCFFL + i * 0x1000, (i * 23 % 112).toLong))
    }
    (0 until 2).foreach { i =>
      val (x, y) = s.cell(i * 23 + 1)
      val (nx, ny) = s.cell(i * 23 + 40)
      c.getTeleportAnimations.put(new UUID(2L, i), Array(T - 120L - i * 300, x.toLong, y.toLong, nx.toLong, ny.toLong, 0xFFFFD700L))
    }
    // Shots stopped by the terrain and at the end of their range, fading
    (0 until 4).foreach { i =>
      val (x, y) = s.cell(i * 7 + 30)
      val p = new Projectile(7000 + i, new UUID(0L, 1003L), x.toFloat, y.toFloat, 1f, 0f, 0xFFAA66FF, 0,
        projectileTypes(i * 17))
      c.getFadingProjectiles.put(7000 + i, new FadingProjectile(p, T - 60L * (i + 1), i % 2 == 0, 0xFF888888))
    }
    // The kill feed and the chat
    c.killFeed.add(Array[AnyRef](java.lang.Long.valueOf(T - 800L), "You", "Wizard", "You killed Wizard"))
    c.killFeed.add(Array[AnyRef](java.lang.Long.valueOf(T - 400L), "Kraken", "Golem", "Kraken killed Golem"))
    c.killFeed.add(Array[AnyRef](java.lang.Long.valueOf(T - 100L), "Pirate", "You", "Pirate killed You"))
    c.chatMessages.add(Array[AnyRef](java.lang.Long.valueOf(T - 900L), "P1", "hello there", java.lang.Byte.valueOf(ChatScope.GAME)))
    c.chatMessages.add(Array[AnyRef](java.lang.Long.valueOf(T - 300L), "P2", "behind you!", java.lang.Byte.valueOf(ChatScope.TEAM)))
    // Something to carry
    s.inventory(ItemType.Heart, 2)
    s.inventory(ItemType.Star, 1)
    s.inventory(ItemType.Shield, 1)
    // Damage between the first frame and the second, for the numbers
    s.afterFirstFrame = () => {
      players(2).setHealth(players(2).getHealth - 23)
      players(5).setHealth(players(5).getHealth - 61)
    }
  }

  /** Looking out over the edge of the map, with a player and a shot or two in front of it. */
  private def edgeScene(s: Stage): Unit = {
    val (hx, hy) = s.home
    s.remote(0, 40, (hx + 2, hy + 1))
    val p = new Projectile(1, s.me, hx + 1.5f, hy - 0.5f, 0.6f, -0.8f, 0xFF66CCFF, 30, projectileTypes(9))
    s.client.getProjectiles.put(1, p)
  }

  private val scenes: Seq[Scene] = Seq(
    Scene("hive_firefight", "the_hive.json", (0, 0), s => {
      everything(s, withEffects = true)
      projectiles(s, projectileTypes.take(90), 1, 0)
      blasts(s, 16, 0)
      s.client.setMouseWorldPosition(s.home._1 + 4.5, s.home._2 - 2.0)
    }),
    Scene("hive_rest", "the_hive.json", (0, 0), s => {
      everything(s, withEffects = false)
      projectiles(s, projectileTypes.drop(90), 101, 1)
      blasts(s, 16, 16)
      s.client.setMovementInputActive(true)
      s.localUpdate(flags = 0x10 | 0x20, flags2 = 0x02)
    }),
    Scene("meadow", "the_meadow.json", (60, 17), s => {
      everything(s, withEffects = true)
      projectiles(s, projectileTypes.filter(_ % 3 == 0), 1, 2)
      blasts(s, 12, 32)
      s.localUpdate(flags = 0x04, flags2 = 0x01)
    }),
    Scene("snowglobe", "the_snowglobe.json", (50, 14), s => {
      everything(s, withEffects = true)
      projectiles(s, projectileTypes.filter(_ % 3 == 1), 1, 3)
      blasts(s, 12, 44)
      s.localUpdate(flags = 0x40 | 0x80, slowPercent = 40)
    }),
    Scene("lagoon", "the_lagoon.json", (65, 18), s => {
      everything(s, withEffects = false)
      projectiles(s, projectileTypes.filter(_ % 3 == 2), 1, 4)
      blasts(s, 12, 56)
    }),
    Scene("cell_divider", "the_cell.json", (0, 0), s => {
      s.client.localTeamId = 1
      everything(s, withEffects = true)
      projectiles(s, projectileTypes.take(40), 1, 5)
      val (hx, hy) = s.home
      s.world.divider = new TeamDivider(true, hx + 5, T + 20000L)
      (0 until 4).foreach(i => s.client.recordDividerImpact((hx + 5).toFloat, (hy - 3 + i * 2).toFloat, T - 50L - i * 120))
    }),
    Scene("colony_ceasefire", "the_colony.json", (0, 0), s => {
      everything(s, withEffects = false)
      s.beforeFrame = () => {
        s.timer(240)
        s.client.processPacket(new GameEventPacket(s.nextSeq(), new UUID(0L, 0L), Packet.getCurrentTimestamp,
          GameEvent.MATCH_OPENING, 7.toShort, 0, 0.toShort, 0.toShort, null, 0.toByte, 0.toShort, 0.toShort,
          0.toByte, 3600000, MatchOpening.NO_ATTACKS))
      }
    }),
    Scene("nexus_dead", "the_nexus.json", (0, 0), s => {
      everything(s, withEffects = true)
      s.localUpdate(health = 0)
    }),
    Scene("nexus_game_over", "the_nexus.json", (0, 0), s => {
      s.localUpdate(health = 0)
      s.client.isRespawning = false
    }),
    // The edge of the world, where the background shows: each sky the renderer draws
    Scene("edge_sky", "the_meadow.json", (4, 30), s => edgeScene(s)),
    Scene("edge_sea", "the_lagoon.json", (4, 40), s => edgeScene(s)),
    Scene("edge_snow", "the_snowglobe.json", (4, 30), s => edgeScene(s)),
    Scene("edge_space", "the_hive.json", (4, 30), s => edgeScene(s)),
    Scene("edge_cityscape", "the_cell.json", (4, 30), s => edgeScene(s), background = Some("cityscape")),
    Scene("edge_desert", "the_cell.json", (4, 30), s => edgeScene(s), background = Some("desert")),
    Scene("edge_ocean", "the_cell.json", (4, 30), s => edgeScene(s), background = Some("ocean")),
    Scene("practice_chat", "the_meadow.json", (60, 30), s => {
      everything(s, withEffects = false)
      val c = s.client
      c.isPracticeMode = true
      c.practiceHits = 17; c.practiceShots = 40; c.practiceCombo = 3; c.practiceBestCombo = 6
      c.practiceLastHitTime = T - 500L
      c.isChatOpen = true
      c.chatInputText = "gg well played"
      c.leaveConfirmUntil = T + 2000L
    })
  )

  def main(args: Array[String]): Unit = {
    val rest = RenderQuality.configure(if (args.exists(_.startsWith("--quality="))) args else args :+ "--quality=high")
    val outDir = new File(rest.headOption.getOrElse("golden"))
    val only = rest.find(_.startsWith("--scenes=")).map(_.substring("--scenes=".length).split(",").toSet)
    outDir.mkdirs()
    CharacterDef.all // registers every ProjectileDef

    val window = new GLWindow("Golden frames", W, H)
    window.create()

    scenes.filter(sc => only.forall(_.contains(sc.name))).foreach { scene =>
      val loaded = WorldLoader.load("worlds/" + scene.map)
      val home = if (scene.at != ((0, 0))) scene.at else (loaded.spawnPoints.head.getX, loaded.spawnPoints.head.getY)
      val world = new WorldData(loaded.name, loaded.width, loaded.height, loaded.tiles, Seq(new Position(home._1, home._2)),
        scene.background.getOrElse(loaded.background))
      val client = new GameClient("localhost", 0, world, "Golden")
      client.packetSink = _ => ()
      client.completeAuthAndJoin(new UUID(0L, 999L), "Golden")
      client.selectedCharacterId = CharacterId.Wizard.id
      client.clientState = ClientState.PLAYING
      client.setWorld(world)
      client.processPacket(new PlayerJoinPacket(1, client.getLocalPlayerId, Packet.getCurrentTimestamp,
        new Position(home._1, home._2), 0xFF00AA00, "Golden", CharacterDef.get(CharacterId.Wizard.id).maxHealth,
        CharacterId.Wizard.id, client.localTeamId))
      client.nanoClock = () => T * 1000000L
      val stage = new Stage(client, world, home)
      scene.build(stage)

      val clock = new HeldClock
      val renderer = new GLGameRenderer(client, clock)
      renderer.seedRandom(12345L)
      var f = 0
      while (f <= WarmupFrames) {
        glfwPollEvents()
        clock.ms = T - (WarmupFrames - f) * FrameGapMs
        stage.beforeFrame()
        renderer.render(1.0 / 60, window.fbWidth, window.fbHeight, window.width, window.height)
        if (f == 0) stage.afterFirstFrame()
        if (f == WarmupFrames) save(new File(outDir, scene.name + ".png"), window.fbWidth, window.fbHeight)
        window.swapBuffers()
        f += 1
      }
      renderer.dispose()
      println(s"golden: ${scene.name}")
    }
    window.destroy()
    GLFWManager.terminate()
    println(s"wrote golden frames (${RenderQuality.tierName}) to ${outDir.getAbsolutePath}")
    System.exit(0)
  }

  /** The default framebuffer, as it is about to be shown. */
  private def save(file: File, w: Int, h: Int): Unit = {
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
    ImageIO.write(img, "png", file)
  }
}
