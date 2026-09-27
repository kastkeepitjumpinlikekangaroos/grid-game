# Networking

What the server trusts, who owns a player's position, and what a session is. The wire format itself
(packet layouts and types) is in common/protocol/CLAUDE.md; the code that enforces all of this is
server/net (sessions, signatures, replay, rate limits), server/game (`PacketValidator`, the
`*Requests` traits of `ClientHandler`) and client/game.

## Network Security

9 layers of security protect the networking stack:

1. **TLS 1.3 for TCP** — All TCP traffic encrypted via Netty `SslHandler`. Server generates a self-signed certificate at startup using `keytool` with a random password and restrictive temp directory permissions (`rwx------`). Explicit cipher suites: `TLS_AES_256_GCM_SHA384`, `TLS_CHACHA20_POLY1305_SHA256`. Client trusts all certs (game server, not web).
2. **HMAC Packet Signing** — After auth, server issues a 32-byte session token. All subsequent packets (TCP and UDP) carry a 16-byte truncated HMAC-SHA256. Packets with invalid HMAC are dropped silently. UDP packets without a valid session token are dropped entirely (no unsigned UDP fallback). **A signature proves who sent a packet, not who it names**: a TCP packet naming anyone but the player its channel logged in as is dropped in `GameServerTcpHandler` before that player's rate budget or replay window is touched. Checked later, as it was, one logged-in client could spend another's budget, or send one packet numbered far ahead in their count and make every packet they sent afterwards look like a replay. The client's handlers take nothing unsigned once it holds a token (its UDP handler doesn't look at the sender, so the signature is all there is). `NetworkHandlerTest` and `ClientNetworkTest` pin both ends.
3. **Rate Limiting** — Per-client: 240 UDP/s, 40 TCP/s, 5 chat lines/s (`RateLimiter`'s companion object holds every limit). Per-IP: 5 connections/min, 5 auth failures before 30s cooldown. Per-channel: 5 auth requests/s before login, and the connection closed after 5 auth failures (`MAX_AUTH_FAILURES_PER_CHANNEL`). A datagram counts against a player's budget only once its signature checks out, since its source address is whatever its sender writes into it: charged first, a flood of forged datagrams in a player's name spent their budget and their real updates were dropped. Race-free auth tracking via `computeIfAbsent`. Stale entries cleaned up every 5s. `RateLimiterTest`.
4. **Server-Side Validation** — Movement validated against world bounds, walkability, and speed limits (`PacketValidator.maxCellsIn`: 2x the character's own rate, from `CharacterDef.moveSpeed` through `Movement`, + 2 cells tolerance, Long arithmetic to prevent overflow; updates in the same millisecond are checked too). A phased player walks through walls but is checked at their phased pace (twice their own, `Movement.phasedStepIntervalMs`), with a dash's reach on top: the check used to be skipped for them, and for as long as a phase lasted a client could put its player anywhere. Position updates older (by sequence number) than the newest position already applied are dropped, since positions are absolute, and so is every update from a player who is dead (see *Position authority*). Teleports go through `common/model/Teleport.scala` on both sides: the client picks a star's or blink's landing cell with it and the server checks with it, because a teleport the client shows and the server refuses snaps the player back. A star is applied by its TCP item packet, not by letting a UDP jump through; a refused one comes back as `ItemAction.USE_REJECTED`. Projectile spawn validated against player position (max 3 cells); the shooter (nobody dead, held or phased fires — a phase's last 250ms excepted, since the client's ends a trip across the wire before the server's); the heading, which is only a direction (NaN/Inf rejected, anything shorter than half a unit refused, the rest normalised, and a ground slam's zeroed so it lands on its caster — taken as a velocity, (1, 1) flew 41% faster and (0, 0) never moved, so it never reached its range and lay where it was fired for the rest of the match); the charge level (0-100, and for the primary no more than the time since that attack's last cast, or since the respawn, allows: `PacketValidator.chargeAllowed`; every other attack is uncharged. Taken as the client said, a client could fire full charges at the primary's fire rate); and the attack that fired it: a spawn request names its `AttackSlot` (primary, Q, E, or the burst shot — the primary along `AttackSlot.BurstDirections`, 8 a cast on `BURST_SHOT_COOLDOWN_MS` — carried in the unused projectile-ID field), its type must be that attack's, and each attack has its own clock — a new cast after 80% of that attack's cooldown (`SHOOT_COOLDOWN_MS` for the primary), at most its own projectile count per cast (3 for a gem-boosted primary, a fan's count). **Don't go back to inferring the attack from the projectile type**: many characters fire one type from two attacks (Bear's Maul is eight of its primary's claws), and judged as a primary burst the ring was cut to the three projectiles the fan sends first, straight behind the caster. `AbilityCastValidationTest` pins this across the roster. Health is the server's own: the health a client reports is never read.
5. **Auth Hardening** — Constant-time hash comparison (`MessageDigest.isEqual`), dummy hash on username-not-found (prevents timing enumeration), password minimum 6 characters.
6. **Replay Protection** — `PacketValidator` tracks sequence numbers per player with a sliding window bitmap (`SEQUENCE_WINDOW_SIZE = 1024`) for UDP out-of-order tolerance. TCP enforces strictly increasing sequence numbers. Duplicate/replayed packets are rejected. Issuing a session token resets the player's sequence tracking (`resetSequences`): the new session's client counts from zero, and when a re-login closed a channel the server still had open, the old session's numbers used to stay and every packet of the new one was dropped as a replay. `SessionTest` pins it.
7. **UDP Source Validation** — Server records each player's TCP connection IP (`playerTcpAddresses`). UDP packets are only accepted if the sender IP matches the player's TCP IP. It is a cheap first filter, not proof: a source address can be forged, which is why the rate limit waits for the signature (3).
8. **Session Token Expiration** — Tokens expire after `SESSION_TOKEN_LIFETIME_MS` (1 hour). The cleanup loop removes expired tokens and closes the player's TCP channel, forcing re-authentication.
9. **Client Disconnect Recovery** — `NetworkThread` uses Netty `IdleStateHandler` for read timeout detection (`CLIENT_TIMEOUT_MS`). On disconnect, a callback notifies `GameClient` which clears game state and can transition the UI back to the login screen. Incoming packet queue is bounded (`INCOMING_QUEUE_CAPACITY = 8192`) to prevent memory exhaustion. Sequence numbers reset on reconnect.

## Position authority

The client moves its own player and the server checks every step (item 4 above). The server
moves a player itself only through a **server move** — a pull, a knockback, a vortex, a
teleport-behind, a respawn, a freeze or root holding them where it has them, a refused star, or
a correction — made with `GameInstance.moveByServer` / `holdByServer`, which count it
(`Player.recordServerMove`) and send the player their state over TCP, so a move can't be lost.

- `PlayerUpdatePacket` bytes [45-48] carry the count: the server's updates about a player say how
  many server moves it has made them, and the client's updates echo the count it has seen.
- **The client takes a position from the server only from an update with a count it hasn't
  seen** (`GameClient.processPacket`). Every other update about it — regen and burn ticks, hits,
  lifesteal — carries wherever the server last heard it was, a step or two behind a player who
  is walking, and snapping to those rubber-banded players back all through a match.
- **The server drops a step whose count is behind the player's** (`ClientHandler`): it was sent
  before the client knew of the move and would drag the player back. Pushes used to be undone
  that way, and the old half-second "server teleported" window, which swallowed the owner's
  every step after a teleport-behind and then refused them all as too far, is gone.
- A lone refused step says nothing (it is usually a race the server catches up with, like a step
  overtaking its star), but refusals that go on for 250ms get the client corrected, at most every
  500ms: it no longer takes positions from ordinary updates, so nothing else would tell it.
- What the rest of the match is told about a player is the server's view (`GameInstance.
  stateUpdate`): position where the server holds it, all eight status flags, character, team.
  Relaying the client's claim with four flags showed rooted players walking and blinked a
  burning, rooted, slowed or sped-up player's effects off on every step.
- A match starts where the server placed each player: the client takes its spawn from its own
  `PLAYER_JOIN` echo and sends nothing from `setWorld`. Picking one itself used to put players
  on the same spawn and show everyone teleporting at the start.
- **The dead don't move.** A client goes on sending steps until it hears it has died; the server
  ignores every update from a dead player (`ClientHandler.handlePlayerUpdate`). Taken, the body
  walked on across everyone's screens and picked up whatever it passed, which the respawn then
  threw away. The respawn is a server move, so nothing sent from the last life counts after it.
- **A hold holds against every way of moving.** While a root or freeze lasts the server applies no
  step, blink or dash, and refuses a star; the client doesn't blink or dash while rooted, or use a
  star while held, and neither do bots. The client used to blink anyway: it showed the jump, the
  server kept the player where they were and said nothing, and the two disagreed until its later
  steps were refused.

`PositionAuthorityTest`, `OnHitEffectsTest`, `PhaseShiftTest`, `TeleportValidationTest` and
`GameClientPositionTest` pin all of this.

## Sessions, reconnects and names

- **A join from a player the match already has is a rejoin, and takes nothing from the join**
  (`ClientHandler.handlePlayerJoin`): the player's connection is rebound, and the client is sent
  the match — everyone in it with their teams, items, traps, its own state, the opening — but
  where the player is and how much health they have stay the server's. It used to put them
  wherever the join said and heal them to full: a teleport and a heal, a revival for the dead,
  whenever a client sent a join. **A join never puts anyone into a running match**
  (`GameServer.handleGlobalConnect` only routes one to a match that has the player): a client that
  sent `PLAYER_LEAVE` and then `PLAYER_JOIN` came back fresh, wherever it said, as any character.
- **One session an account.** A login while the server still holds another connection open for
  that account closes it and ends that session as a disconnect would (`GameServer.
  leaveEverything`): out of the ranked queue, its lobby, and its match. It used to close the
  channel alone, and the player stayed in the match — a target standing still — and in the lobby,
  so every lobby the new session tried was refused as "already in a lobby" until the match ended.
- **A player goes by the name they logged in with** (`Sessions.accountNames`), not by the name
  in their join, which let anyone appear in a lobby or in chat as anyone else.
- **Lobby 0 is no lobby** — on the wire and in the client — so `LobbyManager` never hands it out
  when its numbers wrap at 32768.
- **Entering a lobby leaves the ranked queue**, and the matchmaker leaves out anyone it finds in
  a lobby (it works from a snapshot): a player queued while in a lobby was taken into the ranked
  match and left behind in the other as a member who would never come back.
- The client ignores an update about a player who has left the match (`GameClient.
  departedPlayers`): updates come over UDP and the leave over TCP, and one arriving after the
  leave brought them back as a player called "Player" who never went away.

`ReconnectTest`, `AuthFlowTest`, `LobbyManagerTest`, `RankedQueueTest` and `GameClientMatchTest`
pin these.
