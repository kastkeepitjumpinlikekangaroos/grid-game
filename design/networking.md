# Networking

What the server trusts, who owns a player's position, what a session is, and how a player stays
logged in. The wire format itself
(packet layouts and types) is in common/protocol/CLAUDE.md; the code that enforces all of this is
server/net (sessions, signatures, replay, rate limits), server/game (`PacketValidator`, the
`*Requests` traits of `ClientHandler`) and client/game.

## Network Security

10 layers of security protect the networking stack:

1. **TLS 1.3 for TCP, the server's key pinned** — All TCP traffic encrypted via Netty `SslHandler`. Explicit cipher suites: `TLS_AES_256_GCM_SHA384`, `TLS_CHACHA20_POLY1305_SHA256`. The server has one identity for good: an EC P-256 key and a self-signed certificate for it, made by `keytool` the first time it starts and kept beside its account database (`server_identity.p12`, owner-only, gitignored; `TlsProvider`), and it prints the key's fingerprint as it starts (`ServerIdentity.fingerprint`: SSH's form, `SHA256:` and the base64 SHA-256 of the key's SubjectPublicKeyInfo). A client holds every server to the key it first met at that host and port, as SSH does a host key (`KnownServers`: the player's in `known_servers.properties` in their settings folder, beside the remembered login; a dev tool's in memory): checked in the TLS handshake (`PinnedTrustManager`), and the connection isn't ready until the handshake is through, so a server showing another key gets nothing of the login, not the password, not a refresh token. The key, not the certificate: no chain, host name or dates are checked, and the handshake itself proves the server holds the key (TLS 1.3's CertificateVerify, which the JDK checks whatever the trust manager says). A server that shows another key is refused without a retry, and the player sees why, with both fingerprints: its owner may have set it up again (deleted the file, moved the server), and "Trust the new identity" pins the new key; otherwise someone is in the middle of the connection. The certificate used to be made anew at every start, and the client took any certificate at all, so whoever could intercept a connection could read every password sent over it. `ServerPinningTest` (real handshakes over loopback), `TlsProviderTest`, `KnownServersTest`.
2. **HMAC Packet Signing** — After auth, server issues a 32-byte session token. All subsequent packets (TCP and UDP) carry a 16-byte truncated HMAC-SHA256. Packets with invalid HMAC are dropped silently. UDP packets without a valid session token are dropped entirely (no unsigned UDP fallback). **A signature proves who sent a packet, not who it names**: a TCP packet naming anyone but the player its channel logged in as is dropped in `GameServerTcpHandler` before that player's rate budget or replay window is touched. Checked later, as it was, one logged-in client could spend another's budget, or send one packet numbered far ahead in their count and make every packet they sent afterwards look like a replay. The client's handlers take nothing unsigned once it holds a token (its UDP handler doesn't look at the sender, so the signature is all there is). `NetworkHandlerTest` and `ClientNetworkTest` pin both ends.
3. **Rate Limiting** — Per-client: 240 UDP/s, 40 TCP/s, 5 chat lines/s (`RateLimiter`'s companion object holds every limit). Per-IP: 5 connections/min, 5 auth failures before 30s cooldown. Per-channel: 5 auth requests/s before login, and the connection closed after 5 auth failures (`MAX_AUTH_FAILURES_PER_CHANNEL`). A datagram counts against a player's budget only once its signature checks out, since its source address is whatever its sender writes into it: charged first, a flood of forged datagrams in a player's name spent their budget and their real updates were dropped. Race-free auth tracking via `computeIfAbsent`. Stale entries cleaned up every 5s. `RateLimiterTest`. A connection has `LOGIN_DEADLINE_MS` (15s) to log in, or it is closed (`TcpHandler.channelActive`; the client logs in the moment it connects): nothing else closed a connection that finished its TLS handshake and said nothing, and it held its socket for as long as it liked. `NetworkHandlerTest`.
4. **Server-Side Validation** — Movement validated against world bounds, walkability (a single diagonal step only between open cells, as the client and the bots take one: `WorldData.cornersOpen`), and speed limits (`PacketValidator.maxCellsIn`: 2x the character's own rate, from `CharacterDef.moveSpeed` through `Movement`, + 2 cells tolerance, Long arithmetic to prevent overflow; updates in the same millisecond are checked too). A player's first update in a match is checked as well, from where the server put them as though no time had passed (a step, or two bunched by a lost datagram): with nothing to time it from it used to be let through unchecked, and a client's first update could put its player anywhere on the map (`PacketValidatorTest`). A phased player walks through walls but is checked at their phased pace (twice their own, `Movement.phasedStepIntervalMs`), with a dash's reach on top: the check used to be skipped for them, and for as long as a phase lasted a client could put its player anywhere. Position updates older (by sequence number) than the newest position already applied are dropped, since positions are absolute, and so is every update from a player who is dead (see *Position authority*). Teleports go through `common/model/Teleport.scala` on both sides: the client picks a star's or blink's landing cell with it and the server checks with it, because a teleport the client shows and the server refuses snaps the player back. A star is applied by its TCP item packet, not by letting a UDP jump through; a refused one comes back as `ItemAction.USE_REJECTED`. Projectile spawn validated against player position (max 3 cells, and a position that is no number refused: a NaN compares false with everything, so it passed that check, and the shot came from the corner of the map); the shooter (nobody dead, held or phased fires — a phase's last 250ms excepted, since the client's ends a trip across the wire before the server's); the heading, which is only a direction (NaN/Inf rejected, anything shorter than half a unit refused, the rest normalised, and a ground slam's zeroed so it lands on its caster — taken as a velocity, (1, 1) flew 41% faster and (0, 0) never moved, so it never reached its range and lay where it was fired for the rest of the match); the charge level (0-100, and for the primary no more than the time since that attack's last cast, or since the respawn, allows: `PacketValidator.chargeAllowed`; every other attack is uncharged. Taken as the client said, a client could fire full charges at the primary's fire rate); and the attack that fired it: a spawn request names its `AttackSlot` (primary, Q, E, or the burst shot — the primary along `AttackSlot.BurstDirections`, 8 a cast on `BURST_SHOT_COOLDOWN_MS` — carried in the unused projectile-ID field), its type must be that attack's, and each attack has its own clock — a new cast after 80% of that attack's cooldown (`SHOOT_COOLDOWN_MS` for the primary), at most its own projectile count per cast (3 for a gem-boosted primary, a fan's count). **Don't go back to inferring the attack from the projectile type**: many characters fire one type from two attacks (Bear's Maul is eight of its primary's claws), and judged as a primary burst the ring was cut to the three projectiles the fan sends first, straight behind the caster. `AbilityCastValidationTest` pins this across the roster. Health is the server's own: the health a client reports is never read. Not held yet: a blink's or a dash's jump is checked for its reach alone, not for its ability's cooldown nor (a blink's) for a clear way, so a modified client of a character with one can jump its reach every update, through walls.
5. **Auth Hardening** — Constant-time hash comparison (`MessageDigest.isEqual`), dummy hash on username-not-found (prevents timing enumeration), password minimum 6 characters.
6. **Replay Protection** — `PacketValidator` tracks sequence numbers per player with a sliding window bitmap (`SEQUENCE_WINDOW_SIZE = 1024`) for UDP out-of-order tolerance. TCP enforces strictly increasing sequence numbers. Duplicate/replayed packets are rejected. Issuing a session token resets the player's sequence tracking (`resetSequences`): the new session's client counts from zero, and when a re-login closed a channel the server still had open, the old session's numbers used to stay and every packet of the new one was dropped as a replay. `SessionTest` pins it.
7. **UDP Source Validation** — Server records each player's TCP connection IP (`playerTcpAddresses`). UDP packets are only accepted if the sender IP matches the player's TCP IP. It is a cheap first filter, not proof: a source address can be forged, which is why the rate limit waits for the signature (3).
8. **Session Token Renewal** — A token lasts `SESSION_TOKEN_LIFETIME_MS` (1 hour). Then the cleanup loop renews the session of a player who is still connected (`Sessions.renewTokens`): it sends a new token in a `SESSION_TOKEN` signed with the old one, and goes on signing with the old one until it first hears the client use the new one, which then becomes the session's (`Sessions.verify`, for TCP and UDP alike). The old token is taken for `RETIRED_TOKEN_GRACE_MS` (10s) after that, for what the client signed just before it switched. The client takes a new token up on its network thread as it reads it, before the packets behind it (`NetworkThread.takeSessionToken`), and takes the old one for `SESSION_RENEWAL_WINDOW_MS` while the server is still using it. A session that can't be renewed — its player not connected, or its client not switched within `SESSION_RENEWAL_WINDOW_MS` (60s) — ends: its token goes and every channel it logged in on is closed, and the client logs in again with its refresh token (*Staying logged in*). Sessions used to end an hour in whatever their player was doing: out to the login screen in the middle of a match. And only a joined player's channel was closed then, so a client that logged in and never joined kept a connection the server went on taking packets from. `SessionTest` pins all of this.
9. **Client Disconnect Recovery** — `NetworkThread` uses Netty `IdleStateHandler` for read timeout detection (`CLIENT_TIMEOUT_MS`). On disconnect, a callback notifies `GameClient`, which clears game state, and the app logs back in with the login's refresh token, unless the server said why it ended the session (*Staying logged in*). Incoming packet queue is bounded (`INCOMING_QUEUE_CAPACITY = 8192`) to prevent memory exhaustion. Sequence numbers reset on reconnect.
10. **Refresh Tokens** — what keeps a player logged in between connections, and between launches if they asked to be remembered: stored only as hashes, exchanged on every use, a copy that comes back ending its login (*Staying logged in*).

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
- **A knockback is counted apart as well** (`Player.recordKnockback`, bytes [55-58]), so every
  client can tell a push from a teleport, a pull or a correction and draw it as a shove: the
  player slides from where they were drawn to where the server put them (`KnockbackSlide`; ours
  in `LocalMovement.updateVisualPosition`, everyone else in `RemoteMotion`). Only the drawing: the
  player is where the server put them from the update on, and is hit and shoots from there. A
  count and not a flag, because a push is often followed in the same hit by a server move of
  another kind (the Horn Toss's stun holds its target where the push left them), and the update
  everyone hears after both still says a knockback happened.
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

`PositionAuthorityTest`, `OnHitEffectsTest`, `PhaseShiftTest`, `TeleportValidationTest`,
`GameClientPositionTest` and `GameClientKnockbackTest` pin all of this.

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
  leaveEverything`): out of the ranked queue, its lobby, and its match. It is told why first
  (SESSION_END, REPLACED), so its client doesn't log back in by itself (*Staying logged in*). It used to close the
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

## Staying logged in

A session lasts as long as its connection (its token renewed hourly, layer 8). What outlives a
connection is the login's **refresh token**, as OAuth has it: a password login (or a signup)
starts a *family* and gives the client its first token, and a token logs the client in again
without the password (`AUTH_REQUEST` with action REFRESH), once. So the player stays logged in:

- **For as long as the client is open.** It holds the token in memory (`ClientMain.savedLogin`),
  and when the connection drops it logs back in by itself (`ClientMain.connectionLost`: six tries
  over about half a minute, for a server restarting) and lands in the lobby browser. A match it
  was in is gone: the server took it out when the connection dropped.
- **Between launches, if the player ticked "Remember me".** The token is kept on disk as well
  (`client/net/LoginStore`: `login.properties` in the player's settings folder — Application
  Support on macOS, `%APPDATA%` on Windows, `~/.config` elsewhere — or wherever
  `GRIDGAME_LOGIN_FILE` says; readable by the player alone, `rw-------` in a `rwx------` folder,
  written beside it and moved into place), and the next launch logs in with it: the login screen
  is the saved login's card ("Continue as ..."), pressed for the player. Unticked, nothing is
  written, a login an earlier run kept is deleted, and closing the client ends the login on the
  server (LOGOUT).
- **For a month at most.** A family lasts `RefreshTokens.LifetimeMs` (30 days) from its password,
  however often it is used: exchanging a token doesn't put its end off. Then the password again.

The server (`server/account/RefreshTokens`, `AuthDatabase`):

- **Only a token's SHA-256 is stored** (`refresh_tokens`, a row a token: its family, the account,
  the name the family's password login used — the one a refresh login goes by — when it was
  issued, when the family runs out, its state). A copy of the database logs nobody in. A token is
  256 random bits, so a plain hash is as good as a slow one.
- **Every use exchanges it** for the next in its family: a login's reply carries a new token
  (REFRESH_TOKEN, after the session's token and before the answer, so it is signed and the client
  keeps it before it acts on the answer).
- **A token comes back only if it was copied**: the client never sends one it has exchanged. One
  whose exchange was confirmed is a reuse, and the whole family is revoked and the session it is in
  use on ended (SESSION_END, REVOKED), whichever of the two copies that was, the thief's or the
  player's. The player logs in with their password; the thief has nothing.
- **An exchange is confirmed when the client is first heard on the session it started**
  (`Sessions.heardFrom`: the first TCP packet signed with the new session's token, its join).
  Until then the old token is still taken, since the reply may have been lost with the connection
  and the client tries again with the token it has. Tried again, the token it was exchanged for is
  dead (a copy of the lost reply is no good to anyone); logging in with the new one instead
  confirms the old one was had. A strict exchange, with no allowance for a lost reply, would have
  logged a player out for good on the flaky connection that a reconnect usually is.
- **A refused token counts as a wrong password** (per address and per connection, the same
  cooldown), and a LOGOUT (any token of the family) says nothing back, so neither tells anyone
  whether a token was good. A database error refusing a refresh login says "Try again later", so
  the client keeps a token that may well be good.
- An account keeps at most `MaxFamiliesPerAccount` (10) families, the one used longest ago going
  first; a family keeps the hashes of its last 32 used tokens, to know one when it comes back; and
  the cleanup loop deletes families that ran out, hourly.

The client, told why the server ended a session (SESSION_END, read on the network thread as it
arrives, since the disconnect right behind it stops the packet thread):

- **REPLACED, the account logged in somewhere else.** The saved login's card, with why, and it waits
  for the player: two clients that logged back in by themselves would take the account from each
  other for ever. "Continue" takes it back.
- **REVOKED, the login is over** (logged out elsewhere, or a copy of its token turned up). The token
  is forgotten, here and on disk, and only the password will do.
- Anything else (no word: the connection dropped, the server restarted, the session couldn't be
  renewed) logs back in by itself. A refresh refused with "Login expired" forgets the token too.
- **Logging out** (the profile's Log out) ends the family on the server and deletes it here; "Use
  another account" on the card does the same, from a connection of its own.

Not covered: a client's first connection to a server is taken on trust (layer 1), as SSH's is, so
someone in the middle of that one connection could be pinned in the server's place (the pinned
identity is in `known_servers.properties`, to compare with the one the server prints as it
starts); and a token copied off the disk works until the player next logs in with theirs (which
ends both) or its month runs out. Two clients on one machine share the remembered login unless one
is given a `GRIDGAME_LOGIN_FILE` of its own.

`RefreshTokensTest` (the database), `StayLoggedInTest` (the server, through its TCP handler),
`ClientNetworkTest`, `GameClientLoginTest`, `LoginStoreTest` and `WelcomeScreenTest` pin this.
