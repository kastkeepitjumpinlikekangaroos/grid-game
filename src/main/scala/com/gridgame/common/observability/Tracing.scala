package com.gridgame.common.observability

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Scope

/**
 * Lightweight span helpers. Designed so the no-op SDK path is essentially free
 * (the tracer returns a no-op SpanBuilder).
 *
 * Usage:
 *   Tracing.span("packet.process", Attrs.packet(pt)) {
 *     // work
 *   }
 *
 *   Tracing.spanT("db.auth", Attrs.dbOp("authenticate")) { span =>
 *     span.setAttribute("user", username)
 *     // work
 *   }
 */
object Tracing {

  private lazy val tracer: Tracer = Telemetry.tracer("com.gridgame")

  def span[T](name: String, attrs: Attributes = Attributes.empty(), kind: SpanKind = SpanKind.INTERNAL)(body: => T): T = {
    val s = tracer.spanBuilder(name)
      .setSpanKind(kind)
      .setAllAttributes(attrs)
      .startSpan()
    val scope: Scope = s.makeCurrent()
    try body
    catch {
      case t: Throwable =>
        s.recordException(t)
        s.setStatus(StatusCode.ERROR, t.getClass.getSimpleName)
        throw t
    } finally {
      scope.close()
      s.end()
    }
  }

  def spanT[T](name: String, attrs: Attributes = Attributes.empty(), kind: SpanKind = SpanKind.INTERNAL)(body: Span => T): T = {
    val s = tracer.spanBuilder(name)
      .setSpanKind(kind)
      .setAllAttributes(attrs)
      .startSpan()
    val scope: Scope = s.makeCurrent()
    try body(s)
    catch {
      case t: Throwable =>
        s.recordException(t)
        s.setStatus(StatusCode.ERROR, t.getClass.getSimpleName)
        throw t
    } finally {
      scope.close()
      s.end()
    }
  }

  /** Returns the current span (no-op if outside a span). */
  def current(): Span = Span.current()
}
