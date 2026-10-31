package com.gridgame.e2e

import org.junit.Test

/**
 * Honest players and the real server on a perfect network, strict (Loopback): every request taken,
 * and every client agreeing with the server about everyone in its match at every step, through rounds
 * of every mode at three tables at once (FuzzRun). A failure names its seed: `GRIDGAME_E2E_SEED` plays
 * it again, and `GRIDGAME_E2E_SOAK_SECONDS` keeps playing new seeds for that long:
 *
 * {{{
 * bazel test //src/test/scala/com/gridgame/e2e:end_to_end_fuzz_test --test_env=GRIDGAME_E2E_SOAK_SECONDS=600
 * }}}
 *
 * The rules run on the wall clock, so a seed plays the same hands but not always the same match.
 */
class EndToEndFuzzTest {
  @Test def honestPlayersAndTheServerAgree(): Unit =
    FuzzRun.seeds(Seq(11L)).foreach(seed => Loopback(seed)(loop => new FuzzRun(loop).play(rounds = 2)))
}
