package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.model.Item
import com.gridgame.common.model.Player
import com.gridgame.common.model.Projectile
import com.gridgame.common.model.WorldData
import com.gridgame.common.protocol._
import com.gridgame.common.world.WorldLoader
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.Unpooled
import io.netty.channel._
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.DatagramPacket
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender

import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.CollectionConverters._

class GameServer(port: Int, val worldFile: String = "") {
  private val registry = new ClientRegistry()
  private val projectileManager = new ProjectileManager(registry)
  private val itemManager = new ItemManager()
  private val handler = new ClientHandler(registry, this, projectileManager, itemManager)
  private val cleanupExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
  private val projectileExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
  private val itemSpawnExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
  private val sequenceNumber = new AtomicInteger(0)
  @volatile private var running = false
  private var world: WorldData = _
  // Track tile modifications so reconnecting players get the current state
  private val modifiedTiles = new java.util.concurrent.ConcurrentHashMap[(Int, Int), Int]()

  // Netty components
  private var bossGroup: NioEventLoopGroup = _
  private var workerGroup: NioEventLoopGroup = _
  private var tcpServerChannel: Channel = _
  private var udpChannel: Channel = _

  def start(): Unit = {
    running = true

    println(s"Game server starting on port $port")
    println(s"World file configured: '${worldFile}' (empty=${worldFile.isEmpty})")

    // Load world for collision detection
    if (worldFile.nonEmpty) {
      try {
        world = WorldLoader.loadFromFile(worldFile)
        println(s"Loaded world '${world.name}' (${world.width}x${world.height})")
      } catch {
        case e: Exception =>
          println(s"Warning: Could not load world file: ${e.getMessage}")
          world = WorldData.createEmpty(200, 200)
      }
    } else {
      world = WorldData.createEmpty(200, 200)
    }

    // Start Netty
    bossGroup = new NioEventLoopGroup(1)
    workerGroup = new NioEventLoopGroup()

    val server = this

    // TCP Server
    val tcpBootstrap = new ServerBootstrap()
    tcpBootstrap.group(bossGroup, workerGroup)
      .channel(classOf[NioServerSocketChannel])
      .childHandler(new ChannelInitializer[SocketChannel] {
        override def initChannel(ch: SocketChannel): Unit = {
          ch.pipeline()
            .addLast(new LengthFieldBasedFrameDecoder(66, 0, 2, 0, 2))
            .addLast(new LengthFieldPrepender(2))
            .addLast(new GameServerTcpHandler(server, handler))
        }
      })
      .option[java.lang.Integer](ChannelOption.SO_BACKLOG, 128)
      .childOption[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, true)

    tcpServerChannel = tcpBootstrap.bind(port).sync().channel()
    println(s"TCP server listening on port $port")

    // UDP Server
    val udpBootstrap = new Bootstrap()
    udpBootstrap.group(workerGroup)
      .channel(classOf[NioDatagramChannel])
      .handler(new GameServerUdpHandler(server))

    udpChannel = udpBootstrap.bind(port).sync().channel()
    println(s"UDP server listening on port $port")

    // Schedule cleanup
    cleanupExecutor.scheduleAtFixedRate(
      new Runnable { def run(): Unit = cleanup() },
      5, 5, TimeUnit.SECONDS
    )

    // Schedule projectile tick
    projectileExecutor.scheduleAtFixedRate(
      new Runnable { def run(): Unit = tickProjectiles() },
      Constants.PROJECTILE_SPEED_MS.toLong,
      Constants.PROJECTILE_SPEED_MS.toLong,
      TimeUnit.MILLISECONDS
    )

    val spawnItems = () => {
      for (i <- 1 to 50) {
        spawnItem()
      }
    }

    spawnItems()

    // Schedule item spawning
    itemSpawnExecutor.scheduleAtFixedRate(
      new Runnable { def run(): Unit = spawnItems() },
      Constants.ITEM_SPAWN_INTERVAL_MS.toLong,
      Constants.ITEM_SPAWN_INTERVAL_MS.toLong,
      TimeUnit.MILLISECONDS
    )

    println(s"Game server started on port $port")

    // Block until TCP server channel closes
    tcpServerChannel.closeFuture().sync()
  }

  /** Send a packet to a specific player, using the correct transport based on packet type. */
  def sendPacketToPlayer(packet: Packet, player: Player): Unit = {
    if (packet.getType.tcp) {
      val raw = player.getTcpChannel
      if (raw != null) {
        val ch = raw.asInstanceOf[Channel]
        if (ch.isActive) {
          val data = packet.serialize()
          ch.writeAndFlush(Unpooled.wrappedBuffer(data))
        }
      }
    } else {
      val addr = player.getUdpAddress
      if (addr != null && udpChannel != null) {
        val data = packet.serialize()
        val dgram = new DatagramPacket(Unpooled.wrappedBuffer(data), addr)
        udpChannel.writeAndFlush(dgram)
      }
    }
  }

  /** Send a packet directly over a specific TCP channel (used for initial join responses). */
  def sendPacketViaChannel(packet: Packet, channel: Channel): Unit = {
    if (channel != null && channel.isActive) {
      val data = packet.serialize()
      channel.writeAndFlush(Unpooled.wrappedBuffer(data))
    }
  }

  /** Broadcast a packet to all players except the one with excludePlayerId. */
  private def broadcast(packet: Packet, excludePlayerId: UUID): Unit = {
    val data = packet.serialize()
    val players = registry.getAll

    players.asScala.foreach { player =>
      if (!player.getId.equals(excludePlayerId)) {
        sendRawToPlayer(data, packet.getType.tcp, player)
      }
    }
  }

  /** Broadcast a packet to all players (no exclusion). */
  def broadcastToAllPlayers(packet: Packet): Unit = {
    val data = packet.serialize()
    val players = registry.getAll

    players.asScala.foreach { player =>
      sendRawToPlayer(data, packet.getType.tcp, player)
    }
  }

  private def sendRawToPlayer(data: Array[Byte], isTcp: Boolean, player: Player): Unit = {
    if (isTcp) {
      val raw = player.getTcpChannel
      if (raw != null) {
        val ch = raw.asInstanceOf[Channel]
        if (ch.isActive) {
          ch.writeAndFlush(Unpooled.wrappedBuffer(data.clone()))
        }
      }
    } else {
      val addr = player.getUdpAddress
      if (addr != null && udpChannel != null) {
        val dgram = new DatagramPacket(Unpooled.wrappedBuffer(data.clone()), addr)
        udpChannel.writeAndFlush(dgram)
      }
    }
  }

  def getNextSequenceNumber: Int = sequenceNumber.getAndIncrement()

  private def tickProjectiles(): Unit = {
    if (!running || world == null) return

    val events = projectileManager.tick(world)
    events.foreach {
      case ProjectileMoved(projectile) =>
        val packet = new ProjectilePacket(
          sequenceNumber.getAndIncrement(),
          projectile.ownerId,
          projectile.getX, projectile.getY,
          projectile.colorRGB,
          projectile.id,
          projectile.dx, projectile.dy,
          ProjectileAction.MOVE
        )
        broadcastToAllPlayers(packet)

      case ProjectileHit(projectile, targetId) =>
        val hitPacket = new ProjectilePacket(
          sequenceNumber.getAndIncrement(),
          projectile.ownerId,
          projectile.getX, projectile.getY,
          projectile.colorRGB,
          projectile.id,
          projectile.dx, projectile.dy,
          ProjectileAction.HIT,
          targetId
        )
        broadcastToAllPlayers(hitPacket)

        val target = registry.get(targetId)
        if (target != null) {
          val updatePacket = new PlayerUpdatePacket(
            sequenceNumber.getAndIncrement(),
            targetId,
            target.getPosition,
            target.getColorRGB,
            target.getHealth
          )
          broadcastToAllPlayers(updatePacket)
        }

      case ProjectileDespawned(projectile) =>
        val packet = new ProjectilePacket(
          sequenceNumber.getAndIncrement(),
          projectile.ownerId,
          projectile.getX, projectile.getY,
          projectile.colorRGB,
          projectile.id,
          projectile.dx, projectile.dy,
          ProjectileAction.DESPAWN
        )
        broadcastToAllPlayers(packet)
    }
  }

  def broadcastPlayerUpdate(packet: PlayerUpdatePacket): Unit = {
    broadcastToAllPlayers(packet)
  }

  def getWorld: WorldData = world

  def broadcastTileUpdate(playerId: UUID, x: Int, y: Int, tileId: Int): Unit = {
    modifiedTiles.put((x, y), tileId)
    val packet = new TileUpdatePacket(
      sequenceNumber.getAndIncrement(),
      playerId,
      x, y,
      tileId
    )
    broadcastToAllPlayers(packet)
  }

  def sendModifiedTiles(player: Player): Unit = {
    val zeroUUID = new UUID(0L, 0L)
    modifiedTiles.forEach { (pos, tileId) =>
      val packet = new TileUpdatePacket(
        sequenceNumber.getAndIncrement(),
        zeroUUID,
        pos._1, pos._2,
        tileId
      )
      sendPacketToPlayer(packet, player)
    }
  }

  def broadcastProjectileSpawn(projectile: Projectile): Unit = {
    val packet = new ProjectilePacket(
      sequenceNumber.getAndIncrement(),
      projectile.ownerId,
      projectile.getX, projectile.getY,
      projectile.colorRGB,
      projectile.id,
      projectile.dx, projectile.dy,
      ProjectileAction.SPAWN
    )
    broadcastToAllPlayers(packet)
  }

  private def spawnItem(): Unit = {
    if (!running || world == null) return
    itemManager.spawnRandomItem(world).foreach { event =>
      broadcastItemSpawn(event.item)
    }
  }

  def broadcastItemSpawn(item: Item): Unit = {
    val zeroUUID = new UUID(0L, 0L)
    val packet = new ItemPacket(
      sequenceNumber.getAndIncrement(),
      zeroUUID,
      item.x, item.y,
      item.itemType.id,
      item.id,
      ItemAction.SPAWN
    )
    broadcastToAllPlayers(packet)
  }

  def broadcastItemPickup(item: Item, playerId: UUID): Unit = {
    val packet = new ItemPacket(
      sequenceNumber.getAndIncrement(),
      playerId,
      item.x, item.y,
      item.itemType.id,
      item.id,
      ItemAction.PICKUP
    )
    broadcastToAllPlayers(packet)
  }

  /** Handle an incoming packet from a TCP or UDP handler. */
  def handleIncomingPacket(packet: Packet, tcpCh: Channel, udpSender: InetSocketAddress): Unit = {
    try {
      val shouldBroadcast = handler.processPacket(packet, tcpCh, udpSender)

      if (shouldBroadcast) {
        // Inject the server's authoritative health for player packets
        val packetToBroadcast = packet.getType match {
          case PacketType.PLAYER_UPDATE =>
            val updatePacket = packet.asInstanceOf[PlayerUpdatePacket]
            val player = registry.get(updatePacket.getPlayerId)
            if (player != null) {
              new PlayerUpdatePacket(
                updatePacket.getSequenceNumber,
                updatePacket.getPlayerId,
                updatePacket.getTimestamp,
                updatePacket.getPosition,
                updatePacket.getColorRGB,
                player.getHealth
              )
            } else {
              packet
            }
          case PacketType.PLAYER_JOIN =>
            val joinPacket = packet.asInstanceOf[PlayerJoinPacket]
            val player = registry.get(joinPacket.getPlayerId)
            if (player != null) {
              new PlayerJoinPacket(
                joinPacket.getSequenceNumber,
                joinPacket.getPlayerId,
                joinPacket.getTimestamp,
                joinPacket.getPosition,
                joinPacket.getColorRGB,
                joinPacket.getPlayerName,
                player.getHealth
              )
            } else {
              packet
            }
          case _ => packet
        }
        broadcast(packetToBroadcast, packet.getPlayerId)
      }
    } catch {
      case e: IllegalArgumentException =>
        System.err.println(s"Invalid packet received: ${e.getMessage}")
    }
  }

  private def cleanup(): Unit = {
    val timedOut = registry.getTimedOutClients

    timedOut.asScala.foreach { playerId =>
      val leavePacket = handler.handleTimeout(playerId, sequenceNumber.getAndIncrement())
      if (leavePacket != null) {
        broadcast(leavePacket, playerId)
      }
    }

    if (!timedOut.isEmpty || registry.size > 0) {
      println(s"Server stats: ${registry.size} players connected")
    }
  }

  def stop(): Unit = {
    println("Stopping server...")
    running = false

    cleanupExecutor.shutdown()
    projectileExecutor.shutdown()
    itemSpawnExecutor.shutdown()

    try {
      if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
        cleanupExecutor.shutdownNow()
      }
      if (!projectileExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
        projectileExecutor.shutdownNow()
      }
      if (!itemSpawnExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
        itemSpawnExecutor.shutdownNow()
      }
    } catch {
      case _: InterruptedException =>
        cleanupExecutor.shutdownNow()
        projectileExecutor.shutdownNow()
        itemSpawnExecutor.shutdownNow()
        Thread.currentThread().interrupt()
    }

    if (tcpServerChannel != null) tcpServerChannel.close().sync()
    if (udpChannel != null) udpChannel.close().sync()
    if (bossGroup != null) bossGroup.shutdownGracefully()
    if (workerGroup != null) workerGroup.shutdownGracefully()

    println("Server stopped.")
  }

  def getPlayerCount: Int = registry.size
}

/** Netty handler for incoming TCP connections. */
class GameServerTcpHandler(server: GameServer, clientHandler: ClientHandler) extends SimpleChannelInboundHandler[io.netty.buffer.ByteBuf] {

  override def channelRead0(ctx: ChannelHandlerContext, msg: io.netty.buffer.ByteBuf): Unit = {
    if (msg.readableBytes() < Constants.PACKET_SIZE) return

    val data = new Array[Byte](Constants.PACKET_SIZE)
    msg.readBytes(data)

    try {
      val packet = PacketSerializer.deserialize(data)
      server.handleIncomingPacket(packet, ctx.channel(), null)
    } catch {
      case e: IllegalArgumentException =>
        System.err.println(s"TCP: Invalid packet received: ${e.getMessage}")
    }
  }

  override def channelInactive(ctx: ChannelHandlerContext): Unit = {
    clientHandler.handleDisconnect(ctx.channel())
    super.channelInactive(ctx)
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    System.err.println(s"TCP handler error: ${cause.getMessage}")
    ctx.close()
  }
}

/** Netty handler for incoming UDP packets. */
class GameServerUdpHandler(server: GameServer) extends SimpleChannelInboundHandler[DatagramPacket] {

  override def channelRead0(ctx: ChannelHandlerContext, msg: DatagramPacket): Unit = {
    val buf = msg.content()
    if (buf.readableBytes() < Constants.PACKET_SIZE) return

    val data = new Array[Byte](Constants.PACKET_SIZE)
    buf.readBytes(data)

    try {
      val packet = PacketSerializer.deserialize(data)
      server.handleIncomingPacket(packet, null, msg.sender())
    } catch {
      case e: IllegalArgumentException =>
        System.err.println(s"UDP: Invalid packet received: ${e.getMessage}")
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    System.err.println(s"UDP handler error: ${cause.getMessage}")
  }
}
