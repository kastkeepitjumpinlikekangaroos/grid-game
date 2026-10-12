package com.gridgame.common.observability

import io.opentelemetry.sdk.common.CompletableResultCode
import org.junit.Assert._
import org.junit.Test

/**
 * What an exporter does when a collector that answered at startup stops answering: after three
 * failures in a row it stops sending, for the rest of the run.
 */
class ExportGiveUpGateTest {
  private val gate = new Telemetry.ExportGiveUpGate("metrics", 3)

  private def failed(): Unit = gate.track(CompletableResultCode.ofFailure())
  private def succeeded(): Unit = gate.track(CompletableResultCode.ofSuccess())

  @Test def itStopsSendingAfterThreeFailuresInARow(): Unit = {
    for (_ <- 1 to 3) { assertFalse("still sending", gate.tripped); failed() }
    assertTrue("dropped quietly", gate.tripped)
  }

  @Test def aSuccessInBetweenStartsTheCountAgain(): Unit = {
    failed(); failed(); succeeded(); failed(); failed()
    assertFalse(gate.tripped)
  }

  @Test def onceItHasGivenUpItNeverTriesTheCollectorAgain(): Unit = {
    // It used to let an export through once a minute to see whether the collector was back, and
    // OTel logged a stack trace for every one that found nothing: on a server run without the
    // stack, once a minute for each signal, forever
    for (_ <- 1 to 3) failed()
    succeeded() // an export that was still in flight when it gave up
    assertTrue(gate.tripped)
  }
}
