package com.gridgame.common.observability

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.DoubleHistogram
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.LongHistogram
import io.opentelemetry.api.metrics.LongUpDownCounter
import io.opentelemetry.api.metrics.Meter

/**
 * Pre-built OTel instruments. Created once on first access, reused forever.
 *
 * All call sites use the same `incr(metric, attrs)` shape so the no-op case is free —
 * if `Telemetry.openTelemetry` is the no-op SDK, every instrument is a no-op too.
 */
object Metrics {

  private val meter: Meter = Telemetry.meter("com.gridgame")

  // ===== Connections / sessions =====

  val connectionsOpened: LongCounter = meter
    .counterBuilder("gridgame.connections.opened")
    .setDescription("New connections accepted by the server")
    .setUnit("{conn}")
    .build()

  val connectionsClosed: LongCounter = meter
    .counterBuilder("gridgame.connections.closed")
    .setDescription("Connections closed by the server")
    .setUnit("{conn}")
    .build()

  val sessionsExpired: LongCounter = meter
    .counterBuilder("gridgame.sessions.expired")
    .setDescription("Session tokens that expired and forced re-auth")
    .setUnit("{session}")
    .build()

  // ===== Packets =====

  val packetsReceived: LongCounter = meter
    .counterBuilder("gridgame.packets.received")
    .setDescription("Packets received by the server")
    .setUnit("{packet}")
    .build()

  val packetsSent: LongCounter = meter
    .counterBuilder("gridgame.packets.sent")
    .setDescription("Packets sent by the server")
    .setUnit("{packet}")
    .build()

  val packetsDropped: LongCounter = meter
    .counterBuilder("gridgame.packets.dropped")
    .setDescription("Packets dropped server-side (HMAC fail, replay, etc.)")
    .setUnit("{packet}")
    .build()

  val bandwidthBytes: LongCounter = meter
    .counterBuilder("gridgame.bandwidth.bytes")
    .setDescription("Bytes transferred over the network")
    .setUnit("By")
    .build()

  val packetProcessDuration: DoubleHistogram = meter
    .histogramBuilder("gridgame.packet.process.duration")
    .setDescription("Time spent dispatching a single packet")
    .setUnit("ms")
    .build()

  val packetDeserializeFailures: LongCounter = meter
    .counterBuilder("gridgame.packet.deserialize.failures")
    .setDescription("Packets that failed to deserialize")
    .setUnit("{packet}")
    .build()

  // ===== Rate limiting =====

  val rateLimitTriggered: LongCounter = meter
    .counterBuilder("gridgame.rate_limit.triggered")
    .setDescription("Rate-limit threshold hit")
    .setUnit("{event}")
    .build()

  // ===== Validation / anti-cheat =====

  val validationFailed: LongCounter = meter
    .counterBuilder("gridgame.validation.failed")
    .setDescription("Server-side validation rejected an action")
    .setUnit("{rejection}")
    .build()

  val replayRejected: LongCounter = meter
    .counterBuilder("gridgame.replay.rejected")
    .setDescription("Replay protection rejected an out-of-order/duplicate packet")
    .setUnit("{packet}")
    .build()

  val hmacFailures: LongCounter = meter
    .counterBuilder("gridgame.hmac.failures")
    .setDescription("HMAC signature verification failures")
    .setUnit("{packet}")
    .build()

  // ===== Auth =====

  val authAttempts: LongCounter = meter
    .counterBuilder("gridgame.auth.attempts")
    .setDescription("Auth attempts (login + signup)")
    .setUnit("{attempt}")
    .build()

  val authDuration: DoubleHistogram = meter
    .histogramBuilder("gridgame.auth.duration")
    .setDescription("Time taken to authenticate")
    .setUnit("ms")
    .build()

  // ===== Lobbies =====

  val lobbiesCreated: LongCounter = meter
    .counterBuilder("gridgame.lobbies.created")
    .setDescription("Lobbies created")
    .setUnit("{lobby}")
    .build()

  val lobbiesClosed: LongCounter = meter
    .counterBuilder("gridgame.lobbies.closed")
    .setDescription("Lobbies closed")
    .setUnit("{lobby}")
    .build()

  val lobbyAction: LongCounter = meter
    .counterBuilder("gridgame.lobby.action")
    .setDescription("Lobby actions performed by players")
    .setUnit("{action}")
    .build()

  val botsAdded: LongCounter = meter
    .counterBuilder("gridgame.bots.added")
    .setDescription("Bots added to a lobby/practice")
    .setUnit("{bot}")
    .build()

  val botsRemoved: LongCounter = meter
    .counterBuilder("gridgame.bots.removed")
    .setDescription("Bots removed from a lobby")
    .setUnit("{bot}")
    .build()

  // ===== Matches / instances =====

  val matchesStarted: LongCounter = meter
    .counterBuilder("gridgame.matches.started")
    .setDescription("Match instances started")
    .setUnit("{match}")
    .build()

  val matchesFinished: LongCounter = meter
    .counterBuilder("gridgame.matches.finished")
    .setDescription("Match instances finished")
    .setUnit("{match}")
    .build()

  val matchDuration: DoubleHistogram = meter
    .histogramBuilder("gridgame.match.duration")
    .setDescription("Wall-clock match duration")
    .setUnit("s")
    .build()

  // ===== Gameplay events =====

  val kills: LongCounter = meter
    .counterBuilder("gridgame.kills")
    .setDescription("Player kills")
    .setUnit("{kill}")
    .build()

  val deaths: LongCounter = meter
    .counterBuilder("gridgame.deaths")
    .setDescription("Player deaths (by cause)")
    .setUnit("{death}")
    .build()

  val respawns: LongCounter = meter
    .counterBuilder("gridgame.respawns")
    .setDescription("Player respawns")
    .setUnit("{respawn}")
    .build()

  val characterPlayed: LongCounter = meter
    .counterBuilder("gridgame.character.played")
    .setDescription("Character selections (incremented per match join)")
    .setUnit("{join}")
    .build()

  val projectilesSpawned: LongCounter = meter
    .counterBuilder("gridgame.projectiles.spawned")
    .setDescription("Projectiles spawned")
    .setUnit("{projectile}")
    .build()

  val projectilesHit: LongCounter = meter
    .counterBuilder("gridgame.projectiles.hit")
    .setDescription("Projectile hit events")
    .setUnit("{hit}")
    .build()

  val projectilesExpired: LongCounter = meter
    .counterBuilder("gridgame.projectiles.expired")
    .setDescription("Projectiles that despawned (range / wall / etc.)")
    .setUnit("{projectile}")
    .build()

  val itemsSpawned: LongCounter = meter
    .counterBuilder("gridgame.items.spawned")
    .setDescription("Items spawned in the world")
    .setUnit("{item}")
    .build()

  val itemsPickedUp: LongCounter = meter
    .counterBuilder("gridgame.items.picked_up")
    .setDescription("Items picked up by players")
    .setUnit("{item}")
    .build()

  val itemsUsed: LongCounter = meter
    .counterBuilder("gridgame.items.used")
    .setDescription("Items used (heart, shield, gem, star, fence)")
    .setUnit("{item}")
    .build()

  val tilesModified: LongCounter = meter
    .counterBuilder("gridgame.tiles.modified")
    .setDescription("Tiles changed at runtime (fence placement)")
    .setUnit("{tile}")
    .build()

  val chatMessages: LongCounter = meter
    .counterBuilder("gridgame.chat.messages")
    .setDescription("Chat messages relayed")
    .setUnit("{msg}")
    .build()

  // ===== Tick / performance =====

  val tickDuration: DoubleHistogram = meter
    .histogramBuilder("gridgame.tick.duration")
    .setDescription("Duration of a game-instance tick")
    .setUnit("ms")
    .build()

  val broadcastFanout: LongHistogram = meter
    .histogramBuilder("gridgame.broadcast.fanout")
    .ofLongs()
    .setDescription("Number of recipients in a single broadcast")
    .setUnit("{recipient}")
    .build()

  // ===== Ranked queue =====

  val queueMatchesMade: LongCounter = meter
    .counterBuilder("gridgame.queue.matches_made")
    .setDescription("Matches formed by the ranked matchmaker")
    .setUnit("{match}")
    .build()

  val queueWaitTime: DoubleHistogram = meter
    .histogramBuilder("gridgame.queue.wait_time")
    .setDescription("Time spent in queue")
    .setUnit("s")
    .build()

  val eloDelta: DoubleHistogram = meter
    .histogramBuilder("gridgame.elo.delta")
    .setDescription("Per-player ELO change after a ranked match")
    .setUnit("{elo}")
    .build()

  // ===== Database =====

  val dbDuration: DoubleHistogram = meter
    .histogramBuilder("gridgame.db.duration")
    .setDescription("Time spent in a SQLite operation")
    .setUnit("ms")
    .build()

  val dbErrors: LongCounter = meter
    .counterBuilder("gridgame.db.errors")
    .setDescription("Errors thrown by SQLite operations")
    .setUnit("{err}")
    .build()

  // ===== Client (only used when client telemetry is enabled) =====

  val clientFrameDuration: DoubleHistogram = meter
    .histogramBuilder("gridgame.client.frame.duration")
    .setDescription("Wall time to render a single frame on the client")
    .setUnit("ms")
    .build()

  val clientPacketsReceived: LongCounter = meter
    .counterBuilder("gridgame.client.packets.received")
    .setDescription("Packets received by the client")
    .setUnit("{packet}")
    .build()

  val clientPacketsSent: LongCounter = meter
    .counterBuilder("gridgame.client.packets.sent")
    .setDescription("Packets sent by the client")
    .setUnit("{packet}")
    .build()

  val clientLatency: DoubleHistogram = meter
    .histogramBuilder("gridgame.client.latency")
    .setDescription("Round-trip latency (heartbeat echo)")
    .setUnit("ms")
    .build()

  val clientReconnects: LongCounter = meter
    .counterBuilder("gridgame.client.reconnects")
    .setDescription("Client reconnect attempts")
    .setUnit("{reconnect}")
    .build()

  val clientErrors: LongCounter = meter
    .counterBuilder("gridgame.client.errors")
    .setDescription("Client-side error events")
    .setUnit("{err}")
    .build()

  // ===== Helpers =====

  /** Idempotent increment that handles both pre-attribute and raw paths. */
  @inline def incr(c: LongCounter, attrs: Attributes): Unit = c.add(1L, attrs)
  @inline def incr(c: LongCounter, n: Long, attrs: Attributes): Unit = c.add(n, attrs)
  @inline def record(h: DoubleHistogram, value: Double, attrs: Attributes): Unit = h.record(value, attrs)
  @inline def record(h: LongHistogram, value: Long, attrs: Attributes): Unit = h.record(value, attrs)
}
