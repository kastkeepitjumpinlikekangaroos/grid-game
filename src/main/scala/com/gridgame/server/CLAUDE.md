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
.        GameServer, ChatRelay, MatchEnd, ProblemReports, ServerMain       (all of them)
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
  `UploadReceiver` is what a connection becomes once it is given over to a problem report's upload
  (`takeOver`, below): exactly the report's bytes, raw, into a file with a running CRC-32, a 30s read
  timeout and a 10-minute deadline; `TcpHandler.Upload` marks such a connection, so the login
  deadline leaves it be.
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
  (positions, phases, barriers), `ShotRequests`, `TrapRequests`, `ItemRequests`,
  `CooldownResetRequests` (R, on a minute of its own from the match's start, but ready at once in
  practice: design/characters.md).

  Every match has an id of its own (`GameInstance.matchId`, a UUID: lobby ids are used again), which
  its players are told as it begins (GAME_EVENT MATCH_INFO, after GAME_STARTING and before
  WORLD_INFO, from `MatchLauncher`) and again by any join (`JoinRequests`, first of all it is sent).
  A client names the match a problem report is about by it. And every match keeps a `MatchJournal`:
  what the server saw and decided in it, a line at a time, timed from its start, for a report about
  it to be kept with — the match and everyone in it as it begins, joins, rejoins, leaves and lost
  connections, every request refused with why and the numbers that say so (a step's cells and
  milliseconds, a shot's attack and how soon it came, R's time left, a trap's or a star's reason; the
  validator writes its own, through the journal ClientHandler hands it), the corrections sent after
  refused steps, deaths (by whom, with what, where) and respawns, errors caught in the match's ticks
  and the bots', and the end with its standings. A line is made only when something happens, never
  per tick; a refusal's text only when it is written. Bounded: its first 500 lines (the start) and
  its last 19,500, and a player's refusals 20 a second, the rest counted in a line (a client
  flooding the server can't push the start out). `GameServer` keeps a finished match's journal for
  half an hour, fifty at most (`MatchJournals`).
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
  gone quiet, sessions to renew, stale rate-limit and replay state, tickets no upload used, journals
  kept long enough, and hourly the refresh tokens of logins that ran out), `ChatRelay`, `MatchEnd`
  (the end of a match: standings, on kills less deaths, saved results, ratings; the match comes down
  even if saving them fails — design/match-flow.md), `ProblemReports` (below), `ServerMain`.

## Problem reports

`ProblemReports` takes in what players send when something went wrong (design/diagnostics.md) and
keeps it for whoever runs the server. A client says on its session that a report is ready
(PROBLEM_REPORT BEGIN: its id, size, CRC-32, kind and the match it is about); the server answers
READY with a ticket (16 random bytes, good for one upload of that report by that player within 60s),
REFUSED, or ACCEPTED with the code of one it has already (a resend after a lost answer: kept once).
The report then comes down a second connection of the client's that logs in with the ticket
(AUTH_REQUEST UPLOAD, routed here by `GameServer` and never to `AuthService`) and is given over to it
(`UploadReceiver.takeOver`): the frame decoder goes, the `TcpHandler` is replaced, and the go-ahead
(READY) is written only then, so no byte of the report is read as a frame. Whole and its CRC right,
it is put in its folder on a thread of its own and the connection told ACCEPTED with its code; any
other end is FAILED (CORRUPT, TRUNCATED, UNAVAILABLE) and nothing is kept. Why it is safe to let a
connection that never logs in carry megabytes: design/networking.md, *A problem report's upload*.

What it takes (`ProblemReports.Limits`): 64 MiB a report; 6 an hour and 20 a day from an account
(counted from the reports kept, which a restart reads back from the folder); 4 tickets and uploads
at once in all, one of them a player's (the same report asked for again gets a new ticket in place of
its last); the folder at 2 GiB. Refused, a client is told why and when trying again is worth it.

Kept beside the account database (`ProblemReports.defaultFolder`: the directory `bazel run` was run
from, or the working directory), in `problem_reports/<yyyy-MM-dd>/<code>/`, the code `GG-` and eight
letters and digits of Crockford's base32 (no I, L, O or U, so it reads back as written):

| File | What it holds |
|---|---|
| `report.zip` | the report as the client sent it (design/diagnostics.md says what is in it) |
| `server.json` | `code`, `reportId`, `receivedAt` (UTC), `account` (the name logged in with), `playerId`, `kind` (match, crash, general), `size`, `crc`, `matchId`, `matchJournal` (whether the next file is there), `server` (`BuildInfo.describe`); never the address it came from |
| `match-journal.txt` | the match's `MatchJournal`, while the server still has it (the match running, or ended within half an hour) |

A part not yet whole waits in `problem_reports/.incoming` and is deleted if the server stops first
(at its next start). The folder is gitignored. Each report is a line in the server's log
(`Problem report GG-... from <name> (<kind>, <size>, match <id>): <folder>`), each refusal or failure
a short one, and `gridgame.problem_reports` counts them by outcome. `ProblemReportsTest` (each
refusal, the upload through the real handlers and over real TLS, bad tickets, wrong bytes, cut-off
uploads, a restart) and `MatchJournalTest` (the lines, their bounds, a finished match's kept, and
MATCH_INFO) pin it.

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
