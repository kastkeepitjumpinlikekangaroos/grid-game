package com.gridgame.common.observability

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Severity

/**
 * Thin logging facade. Writes structured logs to OTel + still prints to stdout/stderr
 * so the developer console keeps working unchanged.
 *
 * Use sparingly — counters/spans are the primary instrumentation. Logs are for state
 * transitions or rare errors that operators want to grep / alert on.
 */
object Log {

  private lazy val logger = Telemetry.logger("com.gridgame")

  def info(message: String, attrs: (String, String)*): Unit = {
    System.out.println(message)
    emit(Severity.INFO, message, attrs)
  }

  def warn(message: String, attrs: (String, String)*): Unit = {
    System.err.println(message)
    emit(Severity.WARN, message, attrs)
  }

  def error(message: String, attrs: (String, String)*): Unit = {
    System.err.println(message)
    emit(Severity.ERROR, message, attrs)
  }

  private def emit(sev: Severity, message: String, kv: Seq[(String, String)]): Unit = {
    if (!Telemetry.isEnabled) return
    try {
      val builder = Attributes.builder()
      kv.foreach { case (k, v) => builder.put(AttributeKey.stringKey(k), v) }
      logger.logRecordBuilder()
        .setSeverity(sev)
        .setSeverityText(sev.name())
        .setBody(message)
        .setAllAttributes(builder.build())
        .emit()
    } catch {
      case _: Throwable => // never let logging crash the caller
    }
  }
}
