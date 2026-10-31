package com.gridgame.e2e

import org.junit.Test

/**
 * LossyNetworkFuzzTest on a phone on a train (NetConditions.Awful: 80ms each way and up to 250 more,
 * a fifth of all datagrams lost, some twice): nothing an honest player does is refused for what the
 * network did to it, and what came by TCP, and then where everyone is, agrees once hands are off.
 */
class AwfulNetworkFuzzTest {
  @Test def honestPlayersAreTakenOverAPhoneOnATrain(): Unit =
    FuzzRun.seeds(Seq(22L)).foreach(seed => Loopback(seed, NetConditions.Awful)(loop => new FuzzRun(loop).play(rounds = 1, roundMs = 4000)))
}
