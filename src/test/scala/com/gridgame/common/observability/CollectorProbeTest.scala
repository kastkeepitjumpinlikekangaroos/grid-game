package com.gridgame.common.observability

import org.junit.Assert._
import org.junit.Test

import java.net.InetAddress
import java.net.ServerSocket

/**
 * The one look telemetry takes for the collector, at startup (`Telemetry.collectorAnswers`):
 * does anything accept a connection at the endpoint the exporters would send to?
 */
class CollectorProbeTest {
  private val loopback = InetAddress.getByName("127.0.0.1")

  @Test def aListeningCollectorAnswers(): Unit = {
    val collector = new ServerSocket(0, 1, loopback)
    try assertTrue(Telemetry.collectorAnswers(s"http://127.0.0.1:${collector.getLocalPort}", 1000))
    finally collector.close()
  }

  @Test def nothingListeningIsNoCollector(): Unit = {
    val port = { val s = new ServerSocket(0, 1, loopback); try s.getLocalPort finally s.close() }
    assertFalse(Telemetry.collectorAnswers(s"http://127.0.0.1:$port", 1000))
  }

  @Test def everyAddressTheHostResolvesToIsTried(): Unit = {
    // localhost is 127.0.0.1 and ::1, and a collector may listen on only one of them: on the
    // last the name resolves to, the look has to get past the first
    val collector = new ServerSocket(0, 1, InetAddress.getAllByName("localhost").last)
    try assertTrue(Telemetry.collectorAnswers(s"http://localhost:${collector.getLocalPort}", 1000))
    finally collector.close()
  }

  @Test def anEndpointThatIsNotAUrlIsNoCollector(): Unit =
    assertFalse(Telemetry.collectorAnswers("not a url", 1000))
}
