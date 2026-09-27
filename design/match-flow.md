# A match's opening and its end

How a match begins (the first thirty seconds: a Teams match's wall, a free-for-all's ceasefire) and
how it ends. The server runs both (GameInstance: `Opening.scala` and the match clock in
`GameInstance.scala`); the client keeps its own copy of the opening (GameClient's `Opening.scala`) and
draws it (`OpeningPainter`).

## The opening of a match

A match's first `Constants.MATCH_OPENING_MS` (30s) is not played quite the way the rest of it is,
and each mode spends it its own way: a Teams match walls the two halves off from each other, a
free-for-all holds everyone's fire. `common/model/MatchOpening.scala` names the two rules
(`DIVIDER`, `NO_ATTACKS`) and which mode gets which; the server sends the set, and each side acts
on the rules it recognises rather than working them out from the mode.

**Both sides run it on their own clocks.** The server announces it once when the match begins and
once when it is over — `GameEvent.MATCH_OPENING`, carrying the milliseconds left in
`GameEventPacket` bytes [48-51] and the rules in [52] — and tells anyone joining during it
(`GameInstance.sendOpeningTo`). A client's copy always outlives the server's by the trip down the
wire, so it never lets through something the server would refuse and rubber-band.
`GameInstance.syncOpening` drives both announcements from the projectile tick, and takes the wall
off the world as it announces the end, so the rest of the match pays nothing for it. Under the
match clock a countdown runs and a beat of FIGHT! marks the end
(`OpeningPainter.drawCountdown`) — the same words either way, since behind a wall or
holding your fire, the battle starts when the countdown does.

### Teams: a half of the map each, and a wall between them

A Teams match is played in two halves of the map, one per team, and opens with a wall between
them: nothing at all crosses the line, so each side can gather, pick its ground and say something
to each other before anyone can shoot anyone.
- **One line, worked out from the map.** `common/model/TeamDivider.scala` splits the world down
  the middle of its longer axis (x for a square map, which every map in `worlds/` is), so both
  sides derive the same geometry from the world they loaded and no shape is ever sent. Team 1
  takes the low half, team 2 the high one (`sideOfTeam`), and every spawn — the match's first and
  every respawn after it — comes out of that team's own half (`GameInstance.spawnFor`, through the
  cell filter on `WorldData.getValidSpawnPoint`). A half with nowhere to put anyone falls back to
  the whole map rather than to the middle of it.
- **The wall is a hole in the world's walkability.** A raised divider hangs off
  `WorldData.divider`, and `isWalkable` refuses the cells it stands on while it is up. That is what
  makes a step, a dash, a blink, a knockback, a trap throw, a bot's path and a spawn point all
  refuse it without any of them knowing it is there. It is not terrain — the tiles are untouched,
  and `isTileWalkable`, which is what a projectile is stopped by, ignores it.
- **What ignores walls is held by hand.** A star jumps over whatever lies between
  (`Teleport.starTarget` / `isValidStarTarget`), a phase walks through walls
  (`PacketValidator.validateMovement`), and a `passesThroughWalls` projectile flies over them.
  Each is turned back by the side rule — `allowsMove`: not into the wall, and not onto its far
  side — or by the crossing test, so nothing gets over what a walk cannot get through.
- **Nothing crosses it in flight either.** `ProjectileManager` reads the divider once a tick and
  stops any projectile whose sub-step crosses the line, a hair short of it, the way a barrier does:
  `ProjectileAction.BLOCKED` with **no** target, since this wall is nobody's. An explosive goes off
  there instead. A hit radius reaches a cell and a half past the line and a blast several, so a
  direct hit or a blast whose line to its victim crosses the divider is cut too (`dividerBetween`),
  exactly as a held barrier shelters whoever stands behind it.
- **It is the same wall for everyone**, allies included: for thirty seconds the two halves cannot
  touch each other at all.
- **Bots** ignore anyone on the far side (`findNearestPlayer`) — they can neither reach nor hit
  them, and would spend the opening walking into the wall shooting it. Their paths already avoid
  the cells it stands on.
- **Drawn** by `OpeningPainter.drawWall`, after the flying projectiles and the held
  barriers: a sheet of amber light standing on the ground along the line — amber because blue and
  red are somebody's barrier — clipped to the stretch the screen can see, in the projection's own
  axes. A hard line along its top and a glow where it meets the ground, with ribs standing in it:
  two bright parallel edges with an even fill between them read as a road, not a wall. It beats
  through its last three seconds, flashes where a shot strikes it, and sinks into the ground as it
  drops.

### Free-for-all: a ceasefire

There are no sides to keep apart, so a free-for-all spends its opening holding everyone's fire:
for those thirty seconds nobody can attack at all — no shot, no charge, no burst, no ability — and
everyone gets the same half minute to find their feet and pick their ground.

- **Every attack is held**, whatever it is: a projectile of any kind
  (`ClientHandler.handleProjectileUpdate` refuses every spawn, before the fire-rate clock, so a
  refused attack costs no cooldown), a trap (`handleTrapUpdate` refuses it and says so, so the
  client gives the cooldown back), a phase or a dash (`activatePhase`), a barrier
  (`updateBarrier`), and a blink's jump — `PacketValidator.validateMovement` drops its
  ability-movement exception, so a jump no walk could have made is refused like any other.
- **What isn't held**: walking, and items. A star is an item, not an attack.
- **The client holds it first**, at the four doors an attack leaves by — `shootToward`,
  `shootAllDirections`, `shootAbility` and `startCharging`, the last because a charge bar that
  fills and then fires nothing is worse than a button that does nothing. Refusing here spends no
  cooldown, and no burst-shot root, on an attack the server was never going to allow.
- **The two ability slots are shuttered** with a padlock and the seconds left, the icon behind
  dimmed: a held slot is not a cooldown, and nothing it does will start one.
- **Bots hold their fire too** (`BotController.tickBot`): a bot spends the opening walking and
  looking for a target, and shooting at nothing.
- **Practice has no opening at all** (`GameInstance.begin` reads `isPractice`): it is where a
  player goes to try a character out, and holding its fire for thirty seconds is the one thing an
  opening must not do there. A ranked duel is a free-for-all of two, and holds fire like any other.

### Both

- **Look at either** with `bazel run //src/main/scala/com/gridgame/client:render_bench -- --divider`
  or `-- --ceasefire` (`=<seconds>` on either to watch it end), which is the loop for the HUD and
  the wall, since a real opening needs a match and lasts thirty seconds of it. The wall is stood
  beside the local player rather than down the middle of the map.
- `TeamDividerTest` (in `common/model` for the geometry and what the world refuses it, and in
  `server` for the match), `CeasefireTest`, `GameClientOpeningTest`, `LobbyFlowTest` and
  `PacketRoundTripTest` pin all of this. `TestMatch` opens with the opening only when a test asks
  for it (`opening = true`): a match driven by hand is one already under way.

## The end of a match

A match ends the moment its time is up: `GameInstance.start` schedules `endMatch` for its deadline.
The 10-second `TIME_SYNC`s only report the time left, and every client counts down from the last
one. The match clock (`getRemainingSeconds`, `isTimeUp`) is `System.nanoTime`, the clock that
executor counts on, never the wall clock.

It used to be the wall clock, and the syncs used to end the match, the first to find the time up.
The sync due at the deadline comes round only a few milliseconds after it, and macOS's `timed`
corrects the wall clock by 5-80 ms, backwards as often as forwards, about every 25 minutes. When the
clock went back further than that margin during a match, the sync found a second left, and the match
ran on for another ten seconds with every countdown at 0:00. That was about one match in ten on the
dev machine. `EndGameTest` pins both halves, and a match can be made shorter for a test with
`durationMs`.
