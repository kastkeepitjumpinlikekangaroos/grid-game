package com.gridgame.client.game

import com.gridgame.client.net.NetworkThread
import com.gridgame.common.Constants
import com.gridgame.common.model._
import com.gridgame.common.observability.Metrics
import com.gridgame.common.protocol._

import java.util.UUID
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * The connection to the server: connecting and logging in, the session token, the heartbeat, and
 * the packet thread, which takes the server's packets off the network thread's queue and hands
 * each to [[GameClient.processPacket]]. Everything the client sends goes through [[send]], which
 * a test points somewhere else ([[packetSink]]).
 */
trait Connection { this: GameClient =>
  private var networkThread: NetworkThread = _
  private val incomingPackets: BlockingQueue[Packet] = new LinkedBlockingQueue(Constants.INCOMING_QUEUE_CAPACITY)
  private[game] val sequenceNumber: AtomicInteger = new AtomicInteger(0)

  // Where outgoing packets go: the network thread, or a test capturing them
  @volatile private[client] var packetSink: Packet => Unit = _

  private[game] def send(packet: Packet): Unit = {
    val sink = packetSink
    if (sink != null) sink(packet) else networkThread.send(packet)
  }

  @volatile private var running = false
  private val disconnected = new AtomicBoolean(false)
  private var packetProcessor: Thread = _

  @volatile var authResponseListener: (Boolean, UUID, String) => Unit = _
  // Disconnect listener (e.g. for UI to show reconnection message)
  @volatile var disconnectListener: () => Unit = _

  def connect(): Unit = {
    try {
      sequenceNumber.set(0)
      disconnected.set(false)
      networkThread = new NetworkThread(serverHost, serverPort, enqueuePacket, () => sendHeartbeat())
      running = true

      // Wire disconnect callback from network thread
      networkThread.disconnectCallback = new Runnable {
        def run(): Unit = handleDisconnect()
      }

      networkThread.start()
      networkThread.waitForReady() // Wait for TCP + UDP channels to be ready

      startPacketProcessor()

      println(s"GameClient: Connected to $serverHost:$serverPort (awaiting auth)")
    } catch {
      case e: Exception =>
        System.err.println(s"GameClient: Connection error - ${e.getMessage}")
        throw e
    }
  }

  /** The connection dropped: back to the login screen, with nothing of the session left over. */
  private def handleDisconnect(): Unit = {
    if (!disconnected.compareAndSet(false, true)) return

    Metrics.clientReconnects.add(1L, io.opentelemetry.api.common.Attributes.empty())
    running = false
    clientState = ClientState.CONNECTING
    networkThread.sessionToken = null
    forgetMatch()
    newLife()

    println("GameClient: Disconnected from server")

    val listener = disconnectListener
    if (listener != null) {
      javafx.application.Platform.runLater(new Runnable {
        def run(): Unit = listener()
      })
    }
  }

  def sendAuthRequest(username: String, password: String, isSignup: Boolean): Unit = {
    val action = if (isSignup) AuthAction.SIGNUP else AuthAction.LOGIN
    val packet = new AuthRequestPacket(
      sequenceNumber.getAndIncrement(),
      action,
      username,
      password
    )
    send(packet)
  }

  private[game] def handleAuthResponse(packet: AuthResponsePacket): Unit =
    if (authResponseListener != null) {
      authResponseListener(packet.getSuccess, packet.getAssignedUUID, packet.getMessage)
    }

  /** The token every packet is signed with from now on. */
  private[game] def takeSessionToken(packet: SessionTokenPacket): Unit = {
    networkThread.sessionToken = packet.getSessionToken
    println("GameClient: Session token received")
  }

  def completeAuthAndJoin(assignedUUID: UUID, username: String): Unit = {
    localPlayerId = assignedUUID
    localColorRGB = Player.generateColorFromUUID(assignedUUID)
    playerName = username

    sendJoinPacket()

    clientState = ClientState.LOBBY_BROWSER

    println(s"GameClient: Authenticated as '$username' (${assignedUUID.toString.substring(0, 8)})")
  }

  private[game] def sendJoinPacket(): Unit = {
    val pos = localPosition.get()
    val packet = new PlayerJoinPacket(
      sequenceNumber.getAndIncrement(),
      localPlayerId,
      pos,
      localColorRGB,
      playerName,
      getSelectedCharacterMaxHealth,
      selectedCharacterId
    )
    send(packet)
  }

  // Tracks the time the most recent heartbeat was sent — set in sendHeartbeat, read when the
  // server echoes it back. Used to derive `gridgame.client.latency`.
  private val heartbeatSentAtNs = new AtomicLong(0L)

  def sendHeartbeat(): Unit = {
    val packet = new HeartbeatPacket(
      sequenceNumber.getAndIncrement(),
      localPlayerId
    )
    heartbeatSentAtNs.set(System.nanoTime())
    send(packet)
  }

  /** The server echoes heartbeats back: the round trip is the latency. */
  private[game] def handleHeartbeatEcho(): Unit = {
    val sentNs = heartbeatSentAtNs.getAndSet(0L)
    if (sentNs > 0) {
      Metrics.clientLatency.record((System.nanoTime() - sentNs) / 1e6, io.opentelemetry.api.common.Attributes.empty())
    }
  }

  def enqueuePacket(packet: Packet): Unit = {
    if (!incomingPackets.offer(packet)) {
      System.err.println(s"GameClient: Packet queue full, dropping ${packet.getType}")
    }
  }

  private def startPacketProcessor(): Unit = {
    val processor = new Thread(new Runnable {
      private val drainBuffer = new java.util.ArrayList[Packet](64)
      def run(): Unit = {
        while (running) {
          try {
            // Block for first packet, then drain all available
            val first = incomingPackets.take()
            processPacket(first)
            drainBuffer.clear()
            incomingPackets.drainTo(drainBuffer)
            var i = 0
            while (i < drainBuffer.size()) {
              processPacket(drainBuffer.get(i))
              i += 1
            }
          } catch {
            case _: InterruptedException =>
              return
            case scala.util.control.NonFatal(e) =>
              // One bad packet or listener must not kill this thread; every screen after it
              // would stop responding to the server.
              System.err.println(s"GameClient: Error processing packet - $e")
              e.printStackTrace()
          }
        }
      }
    }, "PacketProcessor")
    processor.setDaemon(true)
    packetProcessor = processor
    processor.start()
  }

  def disconnect(): Unit = {
    if (!running && disconnected.get()) {
      return
    }

    running = false
    disconnected.set(true)

    // Interrupt packet processor thread so it doesn't block on take()
    if (packetProcessor != null) packetProcessor.interrupt()

    // Null if connect() was never reached
    if (networkThread == null) return

    val leavePacket = new PlayerLeavePacket(
      sequenceNumber.getAndIncrement(),
      localPlayerId
    )
    networkThread.send(leavePacket)
    networkThread.sessionToken = null

    networkThread.shutdown()

    println("GameClient: Disconnected")
  }
}
