# common/observability

OpenTelemetry is wired in at server startup via `Telemetry.init("grid-game-server")` in `ServerMain.main`. The client opt-in path lives in `ClientMain.main` (`--telemetry` flag or `GRIDGAME_TELEMETRY=1`).

## Pipeline

```
Server  ──OTLP gRPC──►  OTel Collector  ──►  Prometheus  ──►  Grafana
(SDK 1.42)  :4317                       ──►  Tempo (traces)
                                        ──►  Loki (logs)
```

All four backends + Grafana are docker-composed in `ops/observability/`. Defaults target `localhost:4317`; falls back to a no-op SDK if init fails, or if no collector answers when it starts, so the game runs identically with or without the stack up.

## Code layout (`common/observability/`)

| File | Purpose |
|------|---------|
| `Telemetry.scala` | SDK init via `AutoConfiguredOpenTelemetrySdk`, if a collector answers; reads `OTEL_*` env vars. Idempotent `init()` + `shutdown()`. Registers JVM runtime metrics. |
| `Metrics.scala` | All ~40 instruments declared once as `val`s on a single `Meter("com.gridgame")`. Use `Metrics.foo.add(1L, attrs)` from call sites — no need to look up by name. |
| `Attrs.scala` | Cached `Attributes` instances keyed by packet type, character id, projectile type, drop reason, etc. Hot loops must NOT allocate via `Attributes.of(...)` per call. |
| `Tracing.scala` | `Tracing.span("name", attrs) { body }` helper. Used sparingly — counters/histograms are the primary instrumentation. |
| `Log.scala` | OTel logs bridge with `Log.info/warn/error(msg, kv...)`. Currently most code still uses `println` / `System.err.println`. |

## Key wiring decisions

- **`Metrics.characterPlayed` is incremented from `ClientRegistry.add`**, not from `ClientHandler.handlePlayerJoin`. Real human joins go through `LobbyHandler.handleStart` / `RankedQueue.start*Match` (which call `registry.add` directly), not through the `PLAYER_JOIN` packet path — only client *rejoins* during an active match hit `handlePlayerJoin`. Putting the counter at the registry chokepoint covers all join paths, including bots.
- **Async gauges live on the owning class**, registered in its constructor with `Meter.gaugeBuilder(...).buildWithCallback { obs => obs.record(state.size(), Attrs.Empty) }`. See `ProjectileManager`, `ItemManager`, `BotController`, `GameServer`, `RankedQueue` for examples.
- **`gridgame.kills` is labeled with `killer_character × projectile_type`**: a few hundred combinations, since each character kills with its own few attacks. **Keep every metric's label combinations under 2000**: that is the SDK's cap per metric, and past it every new combination is counted in one overflow series with no labels at all. A victim's character was a third label until 2026-10: tens of thousands of combinations, past the cap after a few hundred matches on a long-running server, and from then on the kills went unlabeled (and the top-killers panel undercounted).
- **The collector is looked for once, when telemetry starts, and never again** (the user's rule, 2026-10-12: the collector is often not running, and that is expected). `Telemetry.init` opens a connection to each endpoint the OTLP exporters would send to, trying every address its host resolves to (1s each); if nothing answers, it prints one line and stays on the no-op SDK for the rest of the run: no exporters, no export threads, nothing recorded. So start the stack before the server (or an opted-in client) to collect from it; started later, it needs the game restarted. A collector that answered and then goes away is given up on the same way (`Telemetry.ExportGiveUpGate`): after three failed exports in a row an exporter drops its signals quietly for the rest of the run. Until 2026-10-12 nothing looked at startup, and an exporter that had given up let one export through a minute to see whether the collector was back: OTel logged a SEVERE stack trace for every attempt that found nothing, three at startup and then one a minute for each signal, for as long as a server ran without the stack.
- **`gridgame.client.spawn_requests` is counted by `client/game/PendingSpawns`, not where the
  request is sent.** A request is answered by the first packet about a new projectile of ours
  (its SPAWN, or its first MOVE if that was lost), and unanswered after a second without one,
  whether lost or refused. What is fired as the player dies is dropped uncounted
  (client/game/CLAUDE.md, *Spawn requests the server never answers*).
- **Network metrics are wire-only.** Bot-fired projectiles bypass the network entirely, so `gridgame.packets.received{type="PROJECTILE_UPDATE"}` shows just human activity. For total game-event rates use the server-authoritative counters like `gridgame.projectiles.spawned`.

## Configuration

Standard `OTEL_*` env vars (see `ops/observability/.env.example`). Most useful:

- `OTEL_SDK_DISABLED=true` — turn off entirely
- `OTEL_EXPORTER_OTLP_ENDPOINT=http://remote:4317` — point at a non-local collector
- `OTEL_RESOURCE_ATTRIBUTES=service.version=...,deployment.environment=prod`
- `OTEL_METRIC_EXPORT_INTERVAL=10000` — milliseconds between metric flushes

## Adding a New Metric
1. Add the instrument to `common/observability/Metrics.scala` — counter / histogram / async gauge as appropriate. Set a descriptive `setDescription` and an OTel unit (`ms`, `s`, `By`, `{event}`, etc.).
2. If the metric carries labels that recur in hot paths, pre-build the `Attributes` in `common/observability/Attrs.scala` (cache by key — never allocate per call inside a tick loop).
3. Call `Metrics.<instrument>.add(...)` / `.record(...)` at the call site. For async gauges, register a callback in the file that owns the underlying state (see how `ProjectileManager`, `ItemManager`, `BotController` wire their gauges in their constructor).
4. Add a Grafana panel to the relevant dashboard JSON in `ops/observability/grafana/dashboards/`. The OTel Prometheus exporter normalizes names: dots → underscores, counters get `_total`, histograms get `_bucket`/`_sum`/`_count`, and the unit becomes a suffix (`ms` → `_milliseconds`, `s` → `_seconds`, `By` → `_bytes`). Grafana auto-reloads dashboards from the mounted volume every 10s — no restart needed.

The collector, Prometheus, Tempo, Loki and Grafana, and their gotchas: ops/observability/CLAUDE.md.
