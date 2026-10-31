package com.gridgame.client.game

import com.gridgame.common.model.Position
import com.gridgame.common.protocol.Packet

/**
 * What the end-to-end harness (com.gridgame.e2e) needs of the client's insides, which are
 * package-private: where its packets go, the packets it hears, and the few things it knows that it
 * doesn't show.
 */
object E2EClient {
  /** Everything the client sends goes to `sink` instead of a network thread. */
  def sendTo(c: GameClient, sink: Packet => Unit): Unit = c.packetSink = sink

  /** A packet from the server, as the packet thread hands it on. */
  def hear(c: GameClient, p: Packet): Unit = c.processPacket(p)

  /** Where an aimed dash or blink would put us, as the renderer is shown. */
  def dashLanding(c: GameClient, maxDistance: Int): Position = c.dashLanding(maxDistance)
  def blinkLanding(c: GameClient, maxDistance: Int): Position = c.blinkLanding(maxDistance)
}
