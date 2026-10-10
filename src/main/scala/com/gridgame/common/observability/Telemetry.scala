package com.gridgame.common.observability

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.common.export.RetryPolicy
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.metrics.export.MetricExporter
import io.opentelemetry.sdk.trace.export.SpanExporter

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.function.BiFunction

/**
 * Singleton facade for the OpenTelemetry SDK.
 *
 * Behavior:
 *   - `init(...)` is idempotent. First call wins.
 *   - Honors `OTEL_SDK_DISABLED=true`: returns a no-op SDK without spinning up exporters.
 *   - If autoconfigure fails for any reason, falls back to no-op and logs to stderr —
 *     game must not crash because telemetry isn't reachable.
 *   - If the collector is unreachable, each exporter attempts to connect a bounded number
 *     of times (one export interval apart) and then drops its signals quietly, trying the
 *     collector again once a minute, instead of logging an exporter stack trace on every
 *     export forever. When the collector is back, exports resume.
 *   - `shutdown()` flushes pending batches and is safe to call during JVM shutdown.
 *
 * Service identity is set via `OTEL_SERVICE_NAME` / `OTEL_RESOURCE_ATTRIBUTES`, with
 * defaults supplied by `init`. Anything more elaborate (sampler config, exporter URL)
 * lives in env vars — see ops/observability/.env.example.
 */
object Telemetry {

  private val initialized = new AtomicBoolean(false)
  @volatile private var sdk: OpenTelemetry = OpenTelemetry.noop()
  @volatile private var sdkClosable: Option[AutoCloseable] = None
  @volatile private var sdkEnabled: Boolean = false

  /** True once the real SDK is wired in (vs. no-op fallback). */
  def isEnabled: Boolean = sdkEnabled

  /** Underlying SDK (always non-null — defaults to no-op). */
  def openTelemetry: OpenTelemetry = sdk

  /**
   * Initialize the OpenTelemetry SDK. Safe to call multiple times; only the first
   * call has effect.
   *
   * @param serviceName    e.g. "grid-game-server" — used if OTEL_SERVICE_NAME is unset
   * @param serviceVersion stamped on every signal
   */
  def init(serviceName: String, serviceVersion: String = "dev"): Unit = {
    if (!initialized.compareAndSet(false, true)) return

    val disabled = sys.env.get("OTEL_SDK_DISABLED").exists(v => v.equalsIgnoreCase("true") || v == "1")
    if (disabled) {
      System.out.println(s"[telemetry] OTEL_SDK_DISABLED set — running with no-op telemetry")
      return
    }

    // Defaults that the user can override via env vars. We DO NOT override anything
    // already set by the environment.
    setDefault("OTEL_SERVICE_NAME", serviceName)
    setDefault("OTEL_METRICS_EXPORTER", "otlp")
    setDefault("OTEL_TRACES_EXPORTER", "otlp")
    setDefault("OTEL_LOGS_EXPORTER", "otlp")
    setDefault("OTEL_EXPORTER_OTLP_PROTOCOL", "grpc")
    setDefault("OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4317")
    // Also the reconnect cadence when the collector is down: the exporter wrappers below
    // collapse OTLP's own retry to a single attempt per export, so the periodic reader's
    // interval is the only thing pacing attempts — this is the "10s between attempts" the
    // give-up logic relies on. MaxExportFailures attempts, one interval apart, then quiet but
    // for one attempt every ExportRetryMs.
    setDefault("OTEL_METRIC_EXPORT_INTERVAL", "10000")
    setDefault("OTEL_BSP_SCHEDULE_DELAY", "1000")
    setDefault("OTEL_BLRP_SCHEDULE_DELAY", "1000")

    val existingAttrs = sys.props.get("otel.resource.attributes")
      .orElse(sys.env.get("OTEL_RESOURCE_ATTRIBUTES"))
      .getOrElse("")
    val extra = Seq(
      s"service.version=$serviceVersion",
      s"deployment.environment=${sys.env.getOrElse("DEPLOYMENT_ENV", "dev")}"
    ).filter(kv => !existingAttrs.contains(kv.takeWhile(_ != '='))).mkString(",")
    val merged = Seq(existingAttrs, extra).filter(_.nonEmpty).mkString(",")
    if (merged.nonEmpty) {
      System.setProperty("otel.resource.attributes", merged)
    }

    try {
      // Server tick and per-packet processing are usually sub-millisecond. The OTel
      // default histogram boundaries jump 0 → 5 → 10 → 25 ms, so >99% of samples land
      // in the (0, 5] bucket and histogram_quantile collapses to ~2.5–5 ms for every
      // percentile — outliers and regressions are invisible. Override with finer
      // boundaries on the histograms we actually care about.
      val meterProviderCustomizer: java.util.function.BiFunction[
        io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder,
        io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties,
        io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder
      ] = (mpBuilder, _) => {
        val fineMs = java.util.Arrays.asList[java.lang.Double](
          0.05, 0.1, 0.25, 0.5, 1.0, 2.0, 5.0, 10.0, 25.0, 50.0, 100.0, 250.0
        )
        val histAgg = io.opentelemetry.sdk.metrics.Aggregation.explicitBucketHistogram(fineMs)
        mpBuilder.registerView(
          io.opentelemetry.sdk.metrics.InstrumentSelector.builder().setName("gridgame.tick.duration").build(),
          io.opentelemetry.sdk.metrics.View.builder().setAggregation(histAgg).build()
        )
        mpBuilder.registerView(
          io.opentelemetry.sdk.metrics.InstrumentSelector.builder().setName("gridgame.packet.process.duration").build(),
          io.opentelemetry.sdk.metrics.View.builder().setAggregation(histAgg).build()
        )
        mpBuilder
      }
      // Wrap each OTLP exporter so a persistently-unreachable collector can't spam the log
      // with a SEVERE stack trace on every export. collapseRetry() reduces OTLP's built-in
      // multi-attempt retry (which the SDK floors at 2 attempts) to a single effective
      // attempt per export, leaving the periodic reader's export interval as the only thing
      // pacing reconnection. ExportGiveUpGate then trips after MaxExportFailures consecutive
      // failures and drops signals silently, letting one export through every ExportRetryMs to
      // see whether the collector is back. Net effect for metrics: try to connect
      // MaxExportFailures times, ~10s apart, then once a minute until it answers.
      val metricCustomizer = new BiFunction[MetricExporter, ConfigProperties, MetricExporter] {
        override def apply(exp: MetricExporter, cfg: ConfigProperties): MetricExporter =
          new GiveUpMetricExporter(collapseRetry(exp), new ExportGiveUpGate("metrics", MaxExportFailures, ExportRetryMs))
      }
      val spanCustomizer = new BiFunction[SpanExporter, ConfigProperties, SpanExporter] {
        override def apply(exp: SpanExporter, cfg: ConfigProperties): SpanExporter =
          new GiveUpSpanExporter(collapseRetry(exp), new ExportGiveUpGate("traces", MaxExportFailures, ExportRetryMs))
      }
      val logCustomizer = new BiFunction[LogRecordExporter, ConfigProperties, LogRecordExporter] {
        override def apply(exp: LogRecordExporter, cfg: ConfigProperties): LogRecordExporter =
          new GiveUpLogRecordExporter(collapseRetry(exp), new ExportGiveUpGate("logs", MaxExportFailures, ExportRetryMs))
      }
      val auto = AutoConfiguredOpenTelemetrySdk.builder()
        .setResultAsGlobal()
        .addMeterProviderCustomizer(meterProviderCustomizer)
        .addMetricExporterCustomizer(metricCustomizer)
        .addSpanExporterCustomizer(spanCustomizer)
        .addLogRecordExporterCustomizer(logCustomizer)
        .build()
      val configured = auto.getOpenTelemetrySdk
      sdk = configured
      sdkClosable = Some(new AutoCloseable {
        override def close(): Unit = {
          configured.close()
        }
      })
      sdkEnabled = true
      System.out.println(s"[telemetry] OpenTelemetry initialized — service=$serviceName endpoint=${sys.env.getOrElse("OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4317")}")

      try {
        // JVM runtime metrics (heap, GC, threads, classloader, CPU)
        io.opentelemetry.instrumentation.runtimemetrics.java17.RuntimeMetrics
          .create(configured)
      } catch {
        case e: Throwable =>
          System.err.println(s"[telemetry] JVM runtime metrics unavailable: ${e.getClass.getSimpleName}: ${e.getMessage}")
      }
    } catch {
      case e: Throwable =>
        System.err.println(s"[telemetry] init failed, falling back to no-op: ${e.getClass.getSimpleName}: ${e.getMessage}")
        sdk = OpenTelemetry.noop()
        sdkEnabled = false
    }
  }

  /** Flush + shutdown. Safe to call during shutdown hooks. */
  def shutdown(): Unit = {
    sdkClosable.foreach { c =>
      try c.close() catch { case _: Throwable => () }
    }
    sdkClosable = None
    sdkEnabled = false
  }

  // Convenience accessors. All return no-op instruments if the SDK isn't initialized.

  def meter(name: String): Meter = sdk.getMeter(name)
  def tracer(name: String): Tracer = sdk.getTracer(name)
  def logger(name: String): Logger = sdk.getLogsBridge.get(name)

  /** Build an Attributes instance from a flat list of key/value pairs. */
  def attrs(pairs: (String, String)*): Attributes = {
    if (pairs.isEmpty) return Attributes.empty()
    val b = Attributes.builder()
    pairs.foreach { case (k, v) => b.put(AttributeKey.stringKey(k), v) }
    b.build()
  }

  // --- Give up on an unreachable collector, and try it again now and then -------------
  //
  // Without this, OTLP exporters retry on every export interval forever, and each failed
  // export logs a full SEVERE stack trace via OTel's ThrottlingLogger. When the collector
  // simply isn't running (the common local-dev case) that's pure noise. We bound it: after
  // MaxExportFailures consecutive failures an exporter drops its signals silently, letting
  // one export through every ExportRetryMs to see whether the collector is back. Attempts
  // are spaced by the periodic reader's export interval (OTEL_METRIC_EXPORT_INTERVAL, 10s)
  // — see init().

  /** Consecutive failed exports before an exporter stops trying on every export. */
  private val MaxExportFailures = 3

  /** How often an exporter that stopped tries the collector again. It used to be never: traces
    * and logs export every second, so a collector restart of a few seconds ended them for the
    * rest of the server's run, and metrics after thirty. */
  private val ExportRetryMs = 60000L

  /**
   * OTLP's built-in retry can't be turned off (the SDK floors maxAttempts at 2 and uses
   * jittered backoff), so we collapse it: 2 attempts with ~0 backoff behave as a single
   * fail-fast attempt per export. That leaves the periodic reader's fixed interval as the
   * only thing pacing reconnection, so we get exactly MaxExportFailures attempts, one
   * interval apart, before [[ExportGiveUpGate]] trips — rather than a burst of jittered
   * internal retries muddying both the attempt count and the spacing.
   */
  private val fastFailRetry: RetryPolicy = RetryPolicy.builder()
    .setMaxAttempts(2)
    .setInitialBackoff(java.time.Duration.ofMillis(1))
    .setMaxBackoff(java.time.Duration.ofMillis(1))
    .setBackoffMultiplier(1.0)
    .build()

  // Rebuild the autoconfigured OTLP (grpc) exporter with the collapsed retry policy,
  // preserving every other setting via toBuilder(). Non-OTLP/non-grpc exporters (e.g. if
  // the protocol is overridden to http) pass through unchanged — the give-up gate still
  // bounds them, just with OTLP's default internal retry.
  private def collapseRetry(e: MetricExporter): MetricExporter = e match {
    case g: OtlpGrpcMetricExporter => g.toBuilder.setRetryPolicy(fastFailRetry).build()
    case other                     => other
  }
  private def collapseRetry(e: SpanExporter): SpanExporter = e match {
    case g: OtlpGrpcSpanExporter => g.toBuilder.setRetryPolicy(fastFailRetry).build()
    case other                   => other
  }
  private def collapseRetry(e: LogRecordExporter): LogRecordExporter = e match {
    case g: OtlpGrpcLogRecordExporter => g.toBuilder.setRetryPolicy(fastFailRetry).build()
    case other                        => other
  }

  /**
   * Consecutive-failure circuit breaker shared by the exporter wrappers. A success resets
   * the count, so a briefly-flaky-but-reachable collector never trips it; only a sustained
   * outage does. Tripped, it lets one export through every `retryAfterMs` ([[allow]]), and
   * the first that succeeds closes it again. Results complete on the exporter's own thread,
   * so its state is atomic. `nowMs` is the clock, a test's own in a test.
   */
  private[observability] final class ExportGiveUpGate(signal: String, maxFailures: Int, retryAfterMs: Long,
                                                      nowMs: () => Long = () => System.nanoTime() / 1000000L) {
    private val failures = new AtomicInteger(0)
    // When the gate tripped, or its last try at the collector failed; -1 while it hasn't tripped
    private val openedAt = new AtomicLong(-1L)
    // An export let through to try the collector, not yet back
    private val trying = new AtomicBoolean(false)

    def tripped: Boolean = openedAt.get() >= 0

    /** Should this export go to the collector? Every one until the gate trips; then one every
      * `retryAfterMs`, to see whether the collector is back. */
    def allow(): Boolean = {
      val opened = openedAt.get()
      opened < 0 || (nowMs() - opened >= retryAfterMs && trying.compareAndSet(false, true))
    }

    /** Record the delegate's result; returns it unchanged for the caller to hand back. */
    def track(result: CompletableResultCode): CompletableResultCode = {
      result.whenComplete(new Runnable {
        override def run(): Unit = {
          if (result.isSuccess) {
            failures.set(0)
            if (openedAt.getAndSet(-1L) >= 0) {
              System.err.println(s"[telemetry] OTLP $signal export reached the collector again — resuming")
            }
          } else if (openedAt.get() >= 0) {
            openedAt.set(nowMs()) // still unreachable: wait another interval
          } else if (failures.incrementAndGet() >= maxFailures && openedAt.compareAndSet(-1L, nowMs())) {
            System.err.println(
              s"[telemetry] OTLP $signal export failed $maxFailures times (collector unreachable) — " +
                s"dropping $signal quietly, and trying the collector again every ${retryAfterMs / 1000}s.")
          }
          trying.set(false)
        }
      })
      result
    }
  }

  // Transparent decorators: while the gate is tripped, export()/flush() short-circuit to
  // success without touching the network (no connection attempt, no ThrottlingLogger),
  // but for the export the gate lets through now and then to try the collector again.
  // Everything else — including metric temporality/aggregation/memory-mode selection and
  // shutdown — delegates unchanged so the exporter behaves identically while reachable.

  private final class GiveUpMetricExporter(delegate: MetricExporter, gate: ExportGiveUpGate)
      extends MetricExporter {
    def export(metrics: java.util.Collection[io.opentelemetry.sdk.metrics.data.MetricData]): CompletableResultCode =
      if (!gate.allow()) CompletableResultCode.ofSuccess() else gate.track(delegate.export(metrics))
    def flush(): CompletableResultCode =
      if (gate.tripped) CompletableResultCode.ofSuccess() else delegate.flush()
    def shutdown(): CompletableResultCode = delegate.shutdown()
    def getAggregationTemporality(instrumentType: io.opentelemetry.sdk.metrics.InstrumentType): io.opentelemetry.sdk.metrics.data.AggregationTemporality =
      delegate.getAggregationTemporality(instrumentType)
    override def getDefaultAggregation(instrumentType: io.opentelemetry.sdk.metrics.InstrumentType): io.opentelemetry.sdk.metrics.Aggregation =
      delegate.getDefaultAggregation(instrumentType)
    override def getMemoryMode(): io.opentelemetry.sdk.common.export.MemoryMode = delegate.getMemoryMode()
  }

  private final class GiveUpSpanExporter(delegate: SpanExporter, gate: ExportGiveUpGate)
      extends SpanExporter {
    def export(spans: java.util.Collection[io.opentelemetry.sdk.trace.data.SpanData]): CompletableResultCode =
      if (!gate.allow()) CompletableResultCode.ofSuccess() else gate.track(delegate.export(spans))
    def flush(): CompletableResultCode =
      if (gate.tripped) CompletableResultCode.ofSuccess() else delegate.flush()
    def shutdown(): CompletableResultCode = delegate.shutdown()
  }

  private final class GiveUpLogRecordExporter(delegate: LogRecordExporter, gate: ExportGiveUpGate)
      extends LogRecordExporter {
    def export(logs: java.util.Collection[io.opentelemetry.sdk.logs.data.LogRecordData]): CompletableResultCode =
      if (!gate.allow()) CompletableResultCode.ofSuccess() else gate.track(delegate.export(logs))
    def flush(): CompletableResultCode =
      if (gate.tripped) CompletableResultCode.ofSuccess() else delegate.flush()
    def shutdown(): CompletableResultCode = delegate.shutdown()
  }

  private def setDefault(name: String, value: String): Unit = {
    if (sys.env.get(name).isEmpty && sys.props.get(name.toLowerCase.replace('_', '.')).isEmpty) {
      System.setProperty(name.toLowerCase.replace('_', '.'), value)
    }
  }
}
