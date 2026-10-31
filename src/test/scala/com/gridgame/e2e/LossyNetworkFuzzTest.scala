package com.gridgame.e2e

import org.junit.Test

/**
 * Honest players and the real server over Wi-Fi on a bad evening (NetConditions.Lossy: latency,
 * jitter that reorders datagrams, a tenth of them lost, a few twice). Nothing throws on either end,
 * nothing an honest player does is refused for what the network did to it (the validator says nothing
 * on stderr: design/networking.md, *Bad networks*), and once hands are off at the end of every round,
 * what came by TCP agrees — who is dead, the scores, the items, the traps, the standings — and so,
 * within six seconds, does where everyone is (FuzzRun). AwfulNetworkFuzzTest is the same on a phone
 * on a train. Seeds as for EndToEndFuzzTest.
 */
class LossyNetworkFuzzTest {
  @Test def honestPlayersAreTakenOverWiFiOnABadEvening(): Unit =
    FuzzRun.seeds(Seq(21L)).foreach(seed => Loopback(seed, NetConditions.Lossy)(loop => new FuzzRun(loop).play(rounds = 1, roundMs = 4000)))
}
