package com.gridgame.common.observability

import io.opentelemetry.api.OpenTelemetry
import org.junit.Assert._
import org.junit.Test

import java.net.InetAddress
import java.net.ServerSocket
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Telemetry started with something listening where the exporters were told to send: it looks
 * there, finds it, and starts. `init` takes effect once per JVM, which is why this is a target of
 * its own.
 */
class TelemetryWithCollectorTest {
  // The listener isn't a real collector, so the export at shutdown fails, and OTel says so
  private val otelLog = Logger.getLogger("io.opentelemetry")

  @Test def itStartsWhenTheCollectorAnswers(): Unit = {
    val collector = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    System.setProperty("otel.exporter.otlp.endpoint", s"http://127.0.0.1:${collector.getLocalPort}")
    System.setProperty("otel.metric.export.interval", "3600000") // nothing exported during the test
    System.setProperty("otel.exporter.otlp.timeout", "200") // nor waited on at shutdown
    otelLog.setLevel(Level.OFF)
    try {
      Telemetry.init("telemetry-test")
      assertTrue(Telemetry.isEnabled)
      assertNotSame(OpenTelemetry.noop(), Telemetry.openTelemetry)
    } finally {
      Telemetry.shutdown()
      collector.close()
    }
  }
}
