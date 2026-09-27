# ops/observability

The local telemetry stack, docker-composed: `docker compose up -d` here, Grafana at
http://localhost:3000. The server exports to it by default (OTLP gRPC on localhost:4317); the client
only when telemetry is opted in. How the code emits telemetry, and adding a metric:
src/main/scala/com/gridgame/common/observability/CLAUDE.md.

## Pipeline

```
Server  ──OTLP gRPC──►  OTel Collector  ──►  Prometheus  ──►  Grafana
(SDK 1.42)  :4317                       ──►  Tempo (traces)
                                        ──►  Loki (logs)
```

All four backends + Grafana are docker-composed in `ops/observability/`. Defaults target `localhost:4317`; falls back to a no-op SDK if init fails so the game runs identically with or without the stack up.

## Backend stack gotchas (resolved during integration — read before changing collector config)

- **Do not set `const_labels:` on the `prometheus` exporter when also using `resource_to_telemetry_conversion: enabled: true`.** The `resource` processor's attributes get promoted to labels, and if the same name appears in `const_labels` the Go Prometheus client throws "duplicate label names in constant and variable labels" and silently drops every metric (the OTel collector still reports them as "sent" — there's no error in `otelcol_*` metrics either). The collector logs show the error, but only there.
- **Grafana provisioned datasources need an explicit `uid:`** in `provisioning/datasources/datasources.yaml`. Without it, Grafana auto-generates a UID and dashboards that reference `uid: prometheus` (or any specific UID) silently fail to render with no error message in the UI.
- **The `OTLP receiver` and `prometheusexporter` are bound to different ports**: `:4317`/`:4318` for OTLP in, `:8889` for Prometheus scrape out. Confirm by curling `http://localhost:8889/metrics` to see what the exporter is actually serving.

Dashboards are the JSON in `grafana/dashboards/`; Grafana reloads them from the mounted volume every
10s, so no restart is needed. Configuration is the standard `OTEL_*` environment (see
`.env.example`).
