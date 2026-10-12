# server

The game server: logins, lobbies, matchmaking, and the matches themselves, over TLS TCP and signed
UDP (Netty). Every package is a Bazel library and depends only on the ones above it (BUILD.bazel
here):

```
net      sockets, TLS, sessions, replay and rate limits, the Outbox everything is sent through
account  logins, profiles, the leaderboard (AuthDatabase: SQLite)          (net)
game     one running match                                                 (net)
bots     the bots playing in a match                                       (game)
lobby    lobbies, the ranked queue, starting a match                       (game, bots)
.        GameServer, ChatRelay, MatchEnd, ServerMain                       (all of them)
```

Lower layers reach the ones above them only through small traits they own: `PacketRouter` (what
net hands a checked packet to), `MatchHost` (what a match needs of the server), `MatchBots` (a
match's bots), `LobbyHost` (what the lobbies need). `GameServer` implements them.

## The parts

- **net/** — `ServerNetwork` (the sockets: TLS + length framing + `TcpHandler` per connection, one
  `UdpHandler`; a connection that hasn't logged in within 15s is closed), `Sessions` (tokens, renewed
  an hour in rather than ended — design/networking.md, layer 8 — the channel and address each logged
  in on, the refresh-token family its login belongs to and whether the exchange that started it is
  confirmed yet (`heardFrom`), telling a session the server ends why (SESSION_END), auth failures;
  its locks are striped, as a lock per player was kept after they left),
  `ReplayGuard` (packet numbering), `RateLimiter`, `TlsProvider` (the server's TLS identity, made
  once and kept beside the account database as `server_identity.p12`, so its players' clients can
  pin it: design/networking.md, layer 1), and `Outbox`, through which every
  packet the server sends goes: over the transport its type names, signed with the player's token.
- **account/** — `AuthService` (log in or sign up, log in again with a refresh token, log out; a
  good login gets its session's token, then its refresh token, before the answer), `RefreshTokens`
  (staying logged in: a month from the password, exchanged on every use, a copy that comes back
  ending its family; their table, of hashes only, is in `AuthDatabase` — design/networking.md,
  *Staying logged in*),
  `AccountQueries` (a profile, the leaderboard: ENTRY... END), `AuthDatabase` (every statement
  closed whether or not its query throws; `match_results`, which grows by a row for every player of
  every match, bots included, is only read through its indexes, by player and by match — its scans
  made every profile and leaderboard request slower the longer the server was played on). A finished match is
  saved as a `matches` row and a `match_results` row per player (`MatchRow`): standing, the
  character played (`ClientRegistry.characterOf`, which remembers a leaver's) and whether it was a
  bot. Bots' rows are saved for every match but practice; they belong to no account, so profiles
  and the leaderboard never count them. Rows from before 2026-09-28 have neither a character nor bots.
- **game/** — a match. `GameInstance` holds it — its players (`ClientRegistry`), projectiles
  (`ProjectileManager`, with the tick's `BarrierSnapshot` and `PlayerGrid`), items (`ItemManager`),
  traps (`TrapManager`), kills (`KillTracker`), world, teams, clock and ticks — and what happens in it
  is spread over the traits it is made of, a file each:

  | Trait | What it does |
  |---|---|
  | `Opening` | the first thirty seconds: the Teams wall, the free-for-all ceasefire (design/match-flow.md) |
  | `Broadcasts` | telling everyone in the match |
  | `PlayerSync` | what everyone is told about a player, and the server moving one (`moveByServer`) |
  | `ProjectileEffects` | what projectiles do: moves, hits, blasts and their effects |
  | `TrapEffects` | traps going off |
  | `Vitals` | burns, poisons and regeneration (every 200ms) |
  | `Lives` | deaths scored, and respawns |

  What its players send it is `ClientHandler`'s to judge, against `PacketValidator` (movement,
  casts, sequence numbers), also a trait per kind of request: `JoinRequests`, `StepRequests`
  (positions, phases, barriers), `ShotRequests`, `TrapRequests`, `ItemRequests`.
- **bots/** — `BotController` (one match's bots, on a 100ms tick of their own), in traits:
  `BotTargeting`, `BotNavigation` (where each step goes: a bot's role-driven spacing, the way round
  walls, a clear shot; design/characters.md), `BotPaths` (the searches it steps by: A* to a cell, a
  short breadth-first look round the bot, on scratch arrays the world's size, and who stands where
  this tick), `BotItems`, `BotCombat`. `BotManager` holds a lobby's bot slots (each a name and a
  random character) until its match starts.
- **lobby/** — `Lobby` (its seats are taken and resized under its lock: a join, a bot, a switch to
  Teams, the match starting), `LobbyManager` (their CRUD), `LobbyHandler` (lobby actions),
  `RankedQueue` (matchmaking for every ranked mode), `MatchLauncher` (starting a lobby's match the
  same way for a casual lobby, practice and ranked; one that fails to start leaves nothing running).
- **.** — `GameServer` (the parts put together, and routing, and the cleanup loop every 5s: players
  gone quiet, sessions to renew, stale rate-limit and replay state, and hourly the refresh tokens
  of logins that ran out), `ChatRelay`, `MatchEnd` (the end of a
  match: standings, saved results, ratings; the match comes down even if saving them fails —
  design/match-flow.md), `ServerMain`.

## Threads and ticks

A match runs on its own scheduled executors: the projectile tick every `PROJECTILE_SPEED_MS` (30ms,
`GameInstance.tickProjectiles`, which also ticks the traps and hands each player the items within
their reach: design/combat.md, *Item Types*), the player tick every 200ms
(`Vitals.tickPlayers`), the item spawner, a `TIME_SYNC` every 10 seconds, and the respawns; the match ends when its
deadline fires (`GameInstance.endMatch`, see design/match-flow.md). Packets arrive on Netty's threads
and are handled there (`ClientHandler`). The UDP socket has an event loop of its own: on one of the TCP
connections' loops, as it was, every player's steps and shots waited behind whatever that connection
was doing (a login's password hash, a database query). Every periodic task (the cleanup loop,
matchmaking, a match's ticks, the bots') catches `Throwable`, not just `Exception`: one `Error`
escaping a scheduled task cancels it for good, silently. `BarrierSnapshot` and `PlayerGrid` belong to the projectile
tick's thread: anything that reads them (`shelteredFromBlast`, `forEachNearbyPlayer`) must run on it.
A match started by a test is begun but never started (`GameInstance.begin`): the test ticks it by
hand (src/test/CLAUDE.md).

## What else to read

design/networking.md (security layers, position authority, sessions and reconnects),
design/combat.md, barriers.md, traps.md, match-flow.md, characters.md (the rules a match runs),
common/protocol/CLAUDE.md (the packets).
