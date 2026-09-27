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
  `UdpHandler`), `Sessions` (tokens, the channel and address each logged in on, auth failures),
  `ReplayGuard` (packet numbering), `RateLimiter`, `TlsProvider`, and `Outbox`, through which every
  packet the server sends goes: over the transport its type names, signed with the player's token.
- **account/** — `AuthService` (log in or sign up; a good login gets its token before the answer),
  `AccountQueries` (a profile, the leaderboard: ENTRY... END), `AuthDatabase`.
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
  `BotTargeting`, `BotNavigation` (paths, and a bot's role-driven spacing), `BotItems`, `BotCombat`.
  `BotManager` holds a lobby's bot slots (each a name and a random character) until its match starts.
- **lobby/** — `Lobby` (its seats are taken and resized under its lock: a join, a bot, a switch to
  Teams, the match starting), `LobbyManager` (their CRUD), `LobbyHandler` (lobby actions),
  `RankedQueue` (matchmaking for every ranked mode), `MatchLauncher` (starting a lobby's match the
  same way for a casual lobby, practice and ranked).
- **.** — `GameServer` (the parts put together, and routing), `ChatRelay`, `MatchEnd` (the end of a
  match: standings, saved results, ratings), `ServerMain`.

## Threads and ticks

A match runs on its own scheduled executors: the projectile tick every `PROJECTILE_SPEED_MS` (30ms,
`GameInstance.tickProjectiles`, which also ticks the traps), the player tick every 200ms
(`Vitals.tickPlayers`), the item spawner, a `TIME_SYNC` every 10 seconds, and the respawns; the match ends when its
deadline fires (`GameInstance.endMatch`, see design/match-flow.md). Packets arrive on Netty's threads
and are handled there (`ClientHandler`). `BarrierSnapshot` and `PlayerGrid` belong to the projectile
tick's thread: anything that reads them (`shelteredFromBlast`, `forEachNearbyPlayer`) must run on it.
A match started by a test is begun but never started (`GameInstance.begin`): the test ticks it by
hand (src/test/CLAUDE.md).

## What else to read

design/networking.md (security layers, position authority, sessions and reconnects),
design/combat.md, barriers.md, traps.md, match-flow.md, characters.md (the rules a match runs),
common/protocol/CLAUDE.md (the packets).
