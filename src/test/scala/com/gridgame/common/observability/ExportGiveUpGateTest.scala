package com.gridgame.common.observability

import io.opentelemetry.sdk.common.CompletableResultCode
import org.junit.Assert._
import org.junit.Test

/**
 * What an exporter does when the collector can't be reached: after three failures in a row it
 * stops sending, and tries the collector again once a minute until it answers. On a clock of the
 * test's own.
 */
class ExportGiveUpGateTest {
  private var now = 0L
  private val gate = new Telemetry.ExportGiveUpGate("metrics", 3, 60000L, () => now)

  private def failed(): Unit = gate.track(CompletableResultCode.ofFailure())
  private def succeeded(): Unit = gate.track(CompletableResultCode.ofSuccess())

  @Test def itStopsSendingAfterThreeFailuresInARow(): Unit = {
    for (_ <- 1 to 3) { assertTrue(gate.allow()); failed() }
    assertTrue(gate.tripped)
    assertFalse("dropped quietly", gate.allow())
  }

  @Test def aSuccessInBetweenStartsTheCountAgain(): Unit = {
    failed(); failed(); succeeded(); failed(); failed()
    assertFalse(gate.tripped)
    assertTrue(gate.allow())
  }

  @Test def itTriesTheCollectorAgainEveryMinuteAndResumesWhenItAnswers(): Unit = {
    // It used to stay tripped for the rest of the run: a collector restart of a few seconds ended a
    // long-running server's traces and logs for good
    for (_ <- 1 to 3) failed()
    now += 59999
    assertFalse(gate.allow())
    now += 1
    assertTrue("one export goes through to try it", gate.allow())
    assertFalse("one at a time", gate.allow())
    failed()
    now += 30000
    assertFalse("still down: another minute", gate.allow())
    now += 30000
    assertTrue(gate.allow())
    succeeded()
    assertFalse("it's back", gate.tripped)
    assertTrue(gate.allow())
    assertTrue(gate.allow())
  }
}
