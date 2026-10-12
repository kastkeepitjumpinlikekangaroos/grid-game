package com.gridgame.common.observability

import io.opentelemetry.api.OpenTelemetry
import org.junit.Assert._
import org.junit.Test

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.InetAddress
import java.net.ServerSocket

/**
 * Telemetry started with no collector to send to, as the server usually is: it looks for the
 * collector once, says so, and stays off for the rest of the run. `init` takes effect once per
 * JVM, which is why this is a target of its own.
 */
class TelemetryWithoutCollectorTest {

  @Test def itLooksForTheCollectorOnceAndStaysOff(): Unit = {
    // It used to start every exporter anyway, and each tried the collector on every export: OTel
    // logged a SEVERE stack trace for every attempt, three to begin with and then one a minute for
    // each signal, for as long as the server ran
    val port = { val s = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")); try s.getLocalPort finally s.close() }
    System.setProperty("otel.exporter.otlp.endpoint", s"http://127.0.0.1:$port")
    val out = new ByteArrayOutputStream()
    val stdout = System.out
    System.setOut(new PrintStream(out, true))
    try Telemetry.init("telemetry-test") finally System.setOut(stdout)

    assertFalse(Telemetry.isEnabled)
    assertSame("no SDK, so no exporter left to try the collector", OpenTelemetry.noop(), Telemetry.openTelemetry)
    assertTrue(out.toString, out.toString.contains(s"No collector at http://127.0.0.1:$port"))
  }
}
