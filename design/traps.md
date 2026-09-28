# Traps
`TrapCast` throws a trap onto the ground, where it waits for an enemy to walk into it. Five
abilities are one: the Warden's Snare Mine, the Blacksmith's Anvil Trap and the Gravedigger's Open
Grave (bear traps), the Sentinel's Deploy Mine (a mine) and the Runesmith's Rune Trap (a fire
rune).

- **One registry.** `common/model/Trap.scala` holds `TrapDef` — what a trap does — and `TrapKind`
  — what it looks like, kept apart so two traps that do different things can share a look. Five
  are defined: a bear trap (10 damage and a 2s stun), a mine (a 45/15 blast over 3 cells), a
  poison pod (48 over 6s, and a slow), a fire rune (a 40 burn over 5s) and a snare (a 2s root;
  Plan 5b gives it to the Spider). Unlike `ProjectileDef`, the registry is filled by its own
  initializer, so nothing can look a trap up before it is there.
- **Where it lands.** `TrapPlacement.target` walks from the caster toward the cursor, at most the
  ability's range (6 cells), and stops before the first cell a trap can't lie on: the client picks
  the cell with it and the server checks with `isValidTarget`, the same reason `Teleport` exists
  for blinks and stars. A throw can't be dropped past a wall, nor across the front of an enemy's
  raised barrier, which stops it as a wall does (design/barriers.md): it lands short. No cell at
  all means no cast and no cooldown spent.
- **Arming and lasting.** It arms 800ms after it lands and lies there 25s. A player keeps three;
  a fourth takes their oldest away. One trap to a cell.
- **What sets it off.** An enemy — not the owner, not a teammate — within `triggerRadius` (1.0
  cell: the trap's own cell and its four neighbours). Phased players walk over it, and so does
  anyone a Shield item has made invulnerable, exactly as a projectile can't touch them. A trigger
  consumes it whatever happens next: its stun obeys CC immunity like any other hold, and is spent
  either way.
- **What it then does** (`GameInstance.springTrap`): its damage and effects to the one who stepped
  on it, or its explosion with the same falloff and the same shelter behind a barrier a
  projectile's blast has. A burn or a poison is owned by whoever laid the trap, so the kill is
  theirs (`Attrs.CauseTrap`). The blast walks the registry rather than the projectile tick's
  spatial grid, because a trap goes off on whichever thread stepped on it and that grid belongs to
  the tick.
- **When it is checked.** `GameInstance.tickProjectiles` calls `trapManager.tick` every 30ms,
  which expires old traps and catches everyone standing on one. `ClientHandler.handlePlayerUpdate`
  also walks the cells an accepted step crossed: an update can carry a player several cells (a
  lost datagram), and a trap hopped clean over has still been stepped on. Every trap leaves
  through `TrapManager.claim`, which takes it with `traps.remove(id, trap)`, so however many
  threads find the same one, exactly one springs it.
- **Placing one** goes over TCP as a `TRAP_UPDATE` carrying its `AttackSlot`. `ClientHandler`
  checks the player can cast, that the attack really throws that trap, that the cell is in reach
  with a clear path and no enemy's barrier across it, that the cell is free, and the cooldown — through
  `PacketValidator.validateCast`, on the same per-slot clock a shot uses. Anything judged without
  the clock is judged first, so only a genuine race can spend a cast and still be refused. A
  refusal comes back as `REJECTED` and the client gives the cooldown back, ready again in 400ms
  rather than instantly: the reason a placement was refused often hasn't gone away, and a cooldown
  handed back whole turns a held key into a request every frame.
- **A new life starts with every attack ready.** `GameInstance.respawn` clears the server's attack
  clocks (`PacketValidator.resetAttackClocks`), which the client has always done for its own.
  Without it the server spent the rest of the last life's cooldown refusing abilities the player
  could see were ready — silently for a projectile, and as a refused placement for a trap.
- **Everyone is sent every trap**, joiners and rejoiners included. The owner and their allies see
  it plainly; everyone else sees it at 30% alpha — findable if you look, which is the point of
  looking. A player leaving takes their traps with them.
- **Drawn** by `TrapPainter.draw` in the ground pass, so a wall in front of one covers it
  in the depth pass that follows, with nothing asked of `EntityCollector`. Per kind: a bear trap's
  jaws, a mine with a blinking diode, a spore pod, a burning rune, a web. A ring closes on one
  that is still arming, and it fades over its last 800ms. A trap going off is a flash, a ring and
  something of its own (jaws snapping, spores rising, flame standing up, strands whipping back); a
  mine's blast goes into the client's blasts, keyed past `GameClient.TRAP_BLAST_KEYS` so a trap
  and a projectile can never collide, and is drawn as a mine's (`GLBlastRenderers.trapStyle`). The
  ability slot carries the count ("2/3").
- **Bots** lay one for a target within 8 cells that is closing in, and treat armed enemy traps as
  cells to walk round — in `canMoveTo` and in the BFS — unless their target is standing on one.
- **Sounds**: `trap_place`, `trap_snap`, `trap_poison`, `trap_ignite`; a mine reuses `explosion`.

`TrapTest`, `GameClientTrapTest`, `PacketRoundTripTest` and `CharacterRosterTest` pin all of this.

## Adding a New Trap
1. Add a `TrapType` id and a `TrapDef` to `common/model/Trap.scala`, and put it in `TrapDef.all`
   (the registry is built from that list by the object's own initializer). Pick an existing
   `TrapKind` or add one.
2. If it is a new kind, draw it: a `case` in `TrapPainter.drawTrap` and one in
   `drawTrapEffect` for it going off, plus `AbilityPreviewRenderer.drawTrapTop` for the character
   panel. Convex polygons only (`GRIDGAME_POLYCHECK=1`), and no per-frame allocation.
3. Give an ability `castBehavior = TrapCast(TrapType.X, range)` with `projectileType = -4` and
   `maxRange` equal to the cast's range (`CharacterRosterTest` checks both).
4. Update `i18n/messages_en.json` (`char.<id>.e.name`/`.desc` — `ContentCatalogTest` enforces it)
   and `docs/index.html`.
5. A new kind that should not sound like the others gets an entry in `AudioManager.playTrapSprung`
   and a generator in `scripts/generate_sounds.py` (see *Sound Effects & Music*).
