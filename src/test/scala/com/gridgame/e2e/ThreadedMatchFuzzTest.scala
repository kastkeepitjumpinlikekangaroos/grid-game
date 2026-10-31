package com.gridgame.e2e

import org.junit.Test

/**
 * Honest players against matches running as the real server runs them: on executors of their own
 * (the ticks, respawns, item batches, the bots), racing the requests the players send rather than
 * taking turns with them (FuzzRun on a Loopback that isn't byHand). Nothing may throw on either end
 * or be printed to stderr, the server's rules hold, what came by TCP agrees once it has quietened,
 * and every match's standings reach its players as the server ranked them. Seeds as for
 * EndToEndFuzzTest.
 */
class ThreadedMatchFuzzTest {
  @Test def matchesOnTheirOwnClocksKeepTheirRules(): Unit =
    FuzzRun.seeds(Seq(31L)).foreach(seed => Loopback(seed, byHand = false)(loop => new FuzzRun(loop).play(rounds = 1, roundMs = 5500)))
}
