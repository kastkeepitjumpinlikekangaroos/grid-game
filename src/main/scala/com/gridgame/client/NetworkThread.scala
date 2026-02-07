package com.gridgame.client

import com.gridgame.common.Constants
import com.gridgame.common.protocol.Packet
import com.gridgame.common.protocol.PacketSerializer

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch

class NetworkThread(client: GameClient, serverAddress: InetAddress, serverPort: Int) extends Thread("NetworkThread") {
  private var socket: DatagramSocket = _
  private val heartbeatExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
  private val socketReady = new CountDownLatch(1)
  @volatile private var running = false

  setDaemon(true)

  def waitForReady(): Unit = {
    socketReady.await()
  }

  override def run(): Unit = {
    try {
      socket = new DatagramSocket()
      running = true
      socketReady.countDown()

      println(s"NetworkThread: Connected to server ${serverAddress.getHostAddress}:$serverPort")

      heartbeatExecutor.scheduleAtFixedRate(
        new Runnable { def run(): Unit = sendHeartbeat() },
        Constants.HEARTBEAT_INTERVAL_MS,
        Constants.HEARTBEAT_INTERVAL_MS,
        TimeUnit.MILLISECONDS
      )

      receiveLoop()
    } catch {
      case e: SocketException =>
        System.err.println(s"NetworkThread: Failed to create socket - ${e.getMessage}")
    } finally {
      shutdown()
    }
  }

  private def receiveLoop(): Unit = {
    val buffer = new Array[Byte](Constants.PACKET_SIZE)

    while (running && !isInterrupted) {
      try {
        val dgram = new DatagramPacket(buffer, buffer.length)
        socket.receive(dgram)

        val packet = PacketSerializer.deserialize(dgram.getData)
        client.enqueuePacket(packet)
      } catch {
        case e: IOException =>
          if (running) {
            System.err.println(s"NetworkThread: Error receiving packet - ${e.getMessage}")
          }
        case e: IllegalArgumentException =>
          System.err.println(s"NetworkThread: Invalid packet received - ${e.getMessage}")
      }
    }
  }

  def send(packet: Packet): Unit = {
    if (socket == null || socket.isClosed) {
      System.err.println("NetworkThread: Cannot send packet, socket is closed")
      return
    }

    try {
      val data = packet.serialize()
      val dgram = new DatagramPacket(data, data.length, serverAddress, serverPort)
      socket.send(dgram)
    } catch {
      case e: IOException =>
        System.err.println(s"NetworkThread: Error sending packet - ${e.getMessage}")
    }
  }

  private def sendHeartbeat(): Unit = {
    client.sendHeartbeat()
  }

  def shutdown(): Unit = {
    running = false

    heartbeatExecutor.shutdown()
    try {
      if (!heartbeatExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
        heartbeatExecutor.shutdownNow()
      }
    } catch {
      case _: InterruptedException =>
        heartbeatExecutor.shutdownNow()
        Thread.currentThread().interrupt()
    }

    if (socket != null && !socket.isClosed) {
      socket.close()
    }

    println("NetworkThread: Shutdown complete")
  }
}
