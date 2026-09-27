# client/game

`GameClient`: everything the client knows of its session and everything the player does in it. The
server's packets arrive on the packet thread (`Connection`'s, which drains the network thread's
queue into `GameClient.processPacket`); the renderer and the input handlers read and drive it from
the render thread, and the menus from the FX thread. Hence the atomics, the concurrent maps and the
`@volatile`s: most fields are written by one thread and read by another.

`GameClient.scala` is the dispatch and what is forgotten when; each part of the client's state is a
trait in its own file, with the packets that update it and the actions that use it:

| Trait | What it holds and does |
|---|---|
| `Connection` | connecting, logging in, the session token, the heartbeat, the packet thread, `send` (a test points it at `packetSink`) |
| `Lobby` | the lobby browser's list, the lobby we are in (settings, members, their characters), lobby actions |
| `Chat` | lobby and match chat |
| `Ranked` | the ranked queue |
| `Records` | the leaderboard, and our profile's stats and match history |
| `MatchProgress` | the match as a whole: kills, the clock, the scoreboard, practice's tallies, leaving it |
| `Opening` | our copy of the match's opening: the Teams wall on our world, the ceasefire (design/match-flow.md) |
| `WorldState` | the map: the server names it, the UI loads it (`setWorld`) |
| `LocalPlayer` | us: character, position, health, death and respawn, the effects on us; `sendPositionUpdate`; the server's word on us (`handleOwnUpdate`) |
| `LocalMovement` | steps, dashes, blinks, stars, and where we are drawn between steps |
| `Attacks` | aiming, charging, firing, casting, cooldowns |
| `Barriers` | ours raised and streamed; everyone else's from their updates; ripples; what stops a shot in flight (design/barriers.md) |
| `OtherPlayers` | everyone else in the match |
| `Projectiles` | the projectiles in flight, flown between the server's ticks (below) |
| `Items`, `Traps` | items on the ground and our inventory; traps on the ground and going off |
| `EventAnimations` | what the renderer shows of an event for a moment: hits, deaths, teleports, blasts |

## What is forgotten when

`GameClient` decides it, in one place, from the traits' own `forget*` methods: `matchStarting`
(the server's GAME_STARTING: nothing of the last match carries over), `returnToLobbyBrowser`,
`forgetMatch` (everything about the match we were in), and `newLife` (what a death takes away: the
effects on us, our items, our barrier, our cooldowns — at a respawn, a rejoin, or the connection
dropping). New state in a trait belongs in that trait's `forget*`, so it goes when everything else
of its kind does.

The player's own position is theirs to move and the server's to check (design/networking.md,
*Position authority*): `handleOwnUpdate` takes a position only from a server move it hasn't seen.

## Flown between the server's ticks

The server moves every projectile once a tick (`PROJECTILE_SPEED_MS`, 30ms) and sends a MOVE for
each, and the client used to draw each one where the newest MOVE put it. At 60 fps that is a
projectile standing still in 45% of frames and moving nearly twice its step in the rest (72-78%
still at 120 and 144 fps), and on Wi-Fi a late packet bunched two ticks into one frame: jumps of
135px. The eye follows a moving object, so a stop-and-jump cadence reads as stutter however good
the art is. Now the client flies them (`client/game/NetProjectile.scala`):
- **Every projectile packet says which tick it is from** (`ProjectilePacket.getTick`, carried in
  the timestamp field; `GameInstance.projectileTick`). A MOVE is where the projectile is at the end
  of its tick; a SPAWN's position holds until the tick after the one it carries.
- **`ProjectileTimeline` says which tick it is now.** `arrival - tick × 30ms` is the same for
  every MOVE but for its time on the way, and the least of them over the last two seconds puts
  each tick where its quickest news arrives. A projectile is its newest position flown on at its
  speed for the ticks since, so a straight flight is drawn exactly as the server flies it, and a
  late packet only confirms what was already drawn. When the estimate moves, the renderer follows
  it at a tenth of real time instead of jumping.
- **What can't be foreseen is smoothed, not jumped to.** A bounce, a boomerang's turn, a gem
  doubling its speed (two moves in a straight line show it) arrive as a correction that dies away
  over ~40ms (`SMOOTH_MS`). A shot seen fired leaves from where it was fired and catches up.
- **It is never flown past where it will stop**: the face of the terrain ahead, found along the
  half-cell sub-steps the server will take (the face `TerrainImpact.resolve` puts its DESPAWN on),
  the end of its range (the end of the sub-step that reaches it), the divider, or a raised
  barrier that stops its owner's shots (`FlightBlockers`, gathered each frame). So it fades where
  it was last drawn. It does fly on toward a player it is about to hit, by up to a tick: a hit
  radius reaches well past the body, and the hit removes it.
- **No more than four ticks ahead of its news** (`MAX_AHEAD`), and nothing heard of it for ten
  ticks (`EXPIRE_NS`) means it has gone: it fades out where it is. Every projectile packet is a
  datagram, and one lost HIT or DESPAWN used to leave the projectile frozen in the air for the rest
  of the match. A MOVE older than the one it has is dropped, since datagrams overtake each other.
- **`getX`/`getY`, the heading, `getDistanceTraveled` and `isReturning` are what is drawn**,
  written once a frame by the render thread (`GameClient.flyProjectiles` in `Projectiles.scala`, called from
  `GLGameRenderer.render` before anything reads a projectile) under the projectile's lock; the
  packet thread only says what the server said (`heard`) or where it stopped (`stopAt`).
  `getDistanceTraveled` is the server's count, so the lifetime effects (the burn-out at the end of
  its range, trails that grow, the boomerang's way back) now run in a match as in the gallery.

Measured end to end — the real server flying shots, its packets delivered to the real client over
a simulated network, frames at the display's rate — motion went from 45-81% of frames still to
none, and past a shot's first few frames (its launch catching up) from half of all frames jumping
to none more than 1.5x its step: a mean error of 0.2-0.8% of a step on LAN, internet, Wi-Fi and an
80ms link losing 5%. Flying 150 projectiles costs about 1.3us a frame. `ProjectileFlightTest`
pins the client and `ProjectileTicksTest` the server's ticks.

## Design decision

- **Projectiles are flown between the server's ticks** — on the server's own tick timeline, at
  their own speed, never past where they will stop (see *Flown between the server's ticks*). Drawn
  where the newest packet put them, they stood still in half the frames at 60 fps and jumped in
  the rest.
