package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.model.Player
import com.gridgame.common.protocol.Packet
import com.gridgame.common.protocol.PacketSerializer
import com.gridgame.common.protocol.PlayerLeavePacket

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.CollectionConverters._

class GameServer(port: Int) {
  private var socket: DatagramSocket = _
  private val registry = new ClientRegistry()
  private val handler = new ClientHandler(registry)
  private val broadcastExecutor: ExecutorService = Executors.newFixedThreadPool(4)
  private val cleanupExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
  private val sequenceNumber = new AtomicInteger(0)
  @volatile private var running = false

  def start(): Unit = {
    socket = new DatagramSocket(port)
    running = true

    println(s"Game server started on port $port")

    cleanupExecutor.scheduleAtFixedRate(
      new Runnable { def run(): Unit = cleanup() },
      5, 5, TimeUnit.SECONDS
    )

    receiveLoop()
  }

  private def receiveLoop(): Unit = {
    val buffer = new Array[Byte](Constants.PACKET_SIZE)

    while (running) {
      try {
        val dgram = new DatagramPacket(buffer, buffer.length)
        socket.receive(dgram)
        handlePacket(dgram)
      } catch {
        case e: IOException =>
          if (running) {
            System.err.println(s"Error receiving packet: ${e.getMessage}")
          }
      }
    }
  }

  private def handlePacket(dgram: DatagramPacket): Unit = {
    try {
      val data = dgram.getData
      val packet = PacketSerializer.deserialize(data)

      val address = dgram.getAddress
      val port = dgram.getPort

      val shouldBroadcast = handler.processPacket(packet, address, port)

      if (shouldBroadcast) {
        broadcast(packet, packet.getPlayerId)
      }
    } catch {
      case e: IllegalArgumentException =>
        System.err.println(s"Invalid packet received: ${e.getMessage}")
    }
  }

  private def broadcast(packet: Packet, excludePlayerId: UUID): Unit = {
    val data = packet.serialize()
    val players = registry.getAll

    broadcastExecutor.submit(new Runnable {
      def run(): Unit = {
        players.asScala.foreach { player =>
          if (!player.getId.equals(excludePlayerId)) {
            sendTo(data, player.getAddress, player.getPort)
          }
        }
      }
    })
  }

  private def sendTo(data: Array[Byte], address: InetAddress, port: Int): Unit = {
    try {
      val dgram = new DatagramPacket(data, data.length, address, port)
      socket.send(dgram)
    } catch {
      case e: IOException =>
        System.err.println(s"Error sending to ${address.getHostAddress}:$port - ${e.getMessage}")
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

    if (socket != null && !socket.isClosed) {
      socket.close()
    }

    broadcastExecutor.shutdown()
    cleanupExecutor.shutdown()

    try {
      if (!broadcastExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
        broadcastExecutor.shutdownNow()
      }
      if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
        cleanupExecutor.shutdownNow()
      }
    } catch {
      case _: InterruptedException =>
        broadcastExecutor.shutdownNow()
        cleanupExecutor.shutdownNow()
        Thread.currentThread().interrupt()
    }

    println("Server stopped.")
  }

  def getPlayerCount: Int = registry.size
}
