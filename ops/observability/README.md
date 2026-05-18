# Grid Game — Observability stack

Self-hosted OpenTelemetry pipeline that captures metrics, traces, and logs from the game
server (and optionally the client) and visualizes them in Grafana.

```
grid-game-server ──► OTel SDK ──► OTel Collector ──┬──► Prometheus  ──┐
                     (1.42.x)        :4317         ├──► Tempo         ├──► Grafana :3000
                                                   └──► Loki          ┘
```

## Quick start

```bash
cd ops/observability
docker compose up -d
# wait ~10s for everything to settle, then:
open http://localhost:3000
```

Grafana opens with anonymous admin access. Dashboards are pre-provisioned under the
**Grid Game** folder:

- **Grid Game — Overview** — server health, packets, latency, anti-cheat
- **Grid Game — Gameplay** — kills, characters, projectiles, ranked queue
- **Grid Game — Client** — frame duration, RTT (when client telemetry enabled)
- **Grid Game — JVM Runtime** — heap, GC, threads

Start the server in another terminal and the dashboards will populate within ~10 seconds.

```bash
bazel run //src/main/scala/com/gridgame/server:server
```

To run a client with telemetry enabled:

```bash
GRIDGAME_TELEMETRY=1 bazel run //src/main/scala/com/gridgame/client:client
# or
bazel run //src/main/scala/com/gridgame/client:client -- --telemetry
```

## Configuration

The OTel SDK is configured via standard environment variables. The defaults in
`Telemetry.scala` point at `localhost:4317` (this collector). Override any of:

| Variable | Default | Purpose |
|---|---|---|
| `OTEL_SDK_DISABLED` | `false` | Set `true` to disable telemetry entirely (no-op SDK) |
| `OTEL_SERVICE_NAME` | `grid-game-server` / `-client` | Service identity |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4317` | Collector endpoint |
| `OTEL_EXPORTER_OTLP_PROTOCOL` | `grpc` | `grpc` or `http/protobuf` |
| `OTEL_RESOURCE_ATTRIBUTES` | `service.version=dev,deployment.environment=dev` | Extra resource attrs |
| `OTEL_METRIC_EXPORT_INTERVAL` | `10000` ms | How often metrics flush |
| `DEPLOYMENT_ENV` | `dev` | Shorthand for `deployment.environment` |

## Inventory

### Metrics (what to grep for in Prometheus)

| Family | Sample metrics |
|---|---|
| Network | `gridgame_packets_received_total`, `gridgame_packets_sent_total`, `gridgame_packets_dropped_total`, `gridgame_bandwidth_bytes_total` |
| Latency | `gridgame_packet_process_duration_milliseconds`, `gridgame_tick_duration_milliseconds`, `gridgame_db_duration_milliseconds`, `gridgame_auth_duration_milliseconds` |
| Connections | `gridgame_connections_opened_total`, `gridgame_connections_closed_total`, `gridgame_connections_active`, `gridgame_sessions_active`, `gridgame_sessions_expired_total` |
| Anti-cheat | `gridgame_rate_limit_triggered_total`, `gridgame_validation_failed_total`, `gridgame_replay_rejected_total`, `gridgame_hmac_failures_total` |
| Auth | `gridgame_auth_attempts_total`, `gridgame_auth_duration_milliseconds` |
| Lobbies | `gridgame_lobbies_active`, `gridgame_lobbies_created_total`, `gridgame_lobbies_closed_total`, `gridgame_lobby_action_total`, `gridgame_bots_added_total`, `gridgame_bots_removed_total` |
| Matches | `gridgame_matches_started_total`, `gridgame_matches_finished_total`, `gridgame_match_duration_seconds`, `gridgame_instances_active` |
| Gameplay | `gridgame_kills_total`, `gridgame_deaths_total`, `gridgame_respawns_total`, `gridgame_character_played_total`, `gridgame_projectiles_spawned_total`, `gridgame_projectiles_hit_total`, `gridgame_projectiles_expired_total`, `gridgame_projectiles_active`, `gridgame_items_spawned_total`, `gridgame_items_picked_up_total`, `gridgame_items_used_total`, `gridgame_items_active`, `gridgame_tiles_modified_total`, `gridgame_chat_messages_total` |
| Ranked | `gridgame_queue_players`, `gridgame_queue_matches_made_total`, `gridgame_queue_wait_time_seconds`, `gridgame_elo_delta` |
| Bots | `gridgame_bots_active` |
| Client (opt-in) | `gridgame_client_frame_duration_milliseconds`, `gridgame_client_packets_received_total`, `gridgame_client_packets_sent_total`, `gridgame_client_latency_milliseconds`, `gridgame_client_reconnects_total`, `gridgame_client_errors_total` |
| JVM | `jvm_memory_used_bytes`, `jvm_gc_duration_seconds`, `jvm_thread_count`, `jvm_cpu_time_seconds_total` (etc.) |

Counter names use the Prometheus convention `_total` suffix (added by the OTel exporter).
Histograms expand to `_bucket`, `_sum`, `_count`.

### Traces

Spans emitted by the server (always enabled, sampled at 5% of normal flow + 100% of errors):

- `auth.request` — wraps a login/signup
- `match.lifecycle` *(future work — currently just point-in-time match start/end metrics)*
- `packet.process` *(future work — currently a counter + duration histogram, no per-call span)*

Traces are visible in Grafana's **Explore → Tempo** view. The Loki datasource has a derived
field that link-jumps from log line trace_id to the matching trace.

### Logs

Currently we still emit `println` / `System.err.println` calls to stdout. The OTel logs
bridge (`Log.scala`) is wired in but not yet routed at all call sites — that's the next
phase. Stdout-via-docker still works as the lowest-friction option.

## Operational notes

- **Bounded retention**: Prometheus keeps 7 days / 2 GB. Tempo keeps 24 h. Loki 7 days.
  Edit `docker-compose.yml` to extend.
- **Tail sampling**: configured in `collector/config.yaml` — 5% probability + always-keep
  errors. Tune `tail_sampling.policies` to your taste.
- **Resource use**: the full stack idle is ~250 MB RAM. Under load (server with a single
  match running) expect ~400 MB.
- **No HTTPS**: this stack is for local / private dev use. Don't expose any of these
  ports externally without TLS + auth.

## Disabling telemetry

```bash
# Server (still runs, just emits to no-op SDK):
OTEL_SDK_DISABLED=true bazel run //src/main/scala/com/gridgame/server:server

# Client: telemetry is off by default; only the --telemetry flag or
# GRIDGAME_TELEMETRY=1 turns it on.
```

## Tearing down

```bash
docker compose down       # stop containers, keep data
docker compose down -v    # also delete volumes
```
