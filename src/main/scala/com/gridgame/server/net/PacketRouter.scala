package com.gridgame.server.net

import com.gridgame.common.protocol.Packet
import io.netty.channel.Channel

import java.net.InetSocketAddress

/** Where the network layer hands what it has received once it has checked it: the server above it. */
trait PacketRouter {
  /** A packet that passed every check: signed by its sender's session, numbered afresh, within its
    * rate, naming the player its TCP channel logged in as. `tcpCh` is set if it came over TCP,
    * `udpSender` if it came over UDP. */
  def handleIncomingPacket(packet: Packet, tcpCh: Channel, udpSender: InetSocketAddress): Unit

  /** A TCP channel closed. */
  def handleDisconnect(channel: Channel): Unit
}
