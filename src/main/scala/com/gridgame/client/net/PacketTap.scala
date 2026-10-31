package com.gridgame.client.net

/**
 * What passes through the connection, for whoever keeps a record of it: the app's problem reports
 * (client/diagnostics records a match's packets). Called on the network's threads with the 64-byte
 * payload as it arrived (its signature checked) or as it is sent; the array is only lent for the call.
 * Whatever implements it must be quick, never throw and never keep the array.
 */
trait PacketTap {
  /** A packet from the server, whose signature checked out. */
  def received(payload: Array[Byte], tcp: Boolean): Unit

  /** A packet to the server, as it goes out. */
  def sent(payload: Array[Byte], tcp: Boolean): Unit

  /** Something the connection threw away: a packet whose signature didn't check out, one that
    * wasn't a packet, one that couldn't be sent. */
  def dropped(what: String, typeId: Byte): Unit
}

object PacketTap {
  /** Nothing kept: every connection's, unless it is given one. */
  val None: PacketTap = new PacketTap {
    def received(payload: Array[Byte], tcp: Boolean): Unit = ()
    def sent(payload: Array[Byte], tcp: Boolean): Unit = ()
    def dropped(what: String, typeId: Byte): Unit = ()
  }
}
