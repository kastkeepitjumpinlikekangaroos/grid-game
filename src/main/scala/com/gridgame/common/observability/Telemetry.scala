package com.gridgame.common.observability

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Singleton facade for the OpenTelemetry SDK.
 *
 * Behavior:
 *   - `init(...)` is idempotent. First call wins.
 *   - Honors `OTEL_SDK_DISABLED=true`: returns a no-op SDK without spinning up exporters.
 *   - If autoconfigure fails for any reason, falls back to no-op and logs to stderr —
 *     game must not crash because telemetry isn't reachable.
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
      val auto = AutoConfiguredOpenTelemetrySdk.builder()
        .setResultAsGlobal()
        .addMeterProviderCustomizer(meterProviderCustomizer)
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

  private def setDefault(name: String, value: String): Unit = {
    if (sys.env.get(name).isEmpty && sys.props.get(name.toLowerCase.replace('_', '.')).isEmpty) {
      System.setProperty(name.toLowerCase.replace('_', '.'), value)
    }
  }
}
