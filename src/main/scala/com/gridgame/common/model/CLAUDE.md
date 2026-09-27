# common/model

The game's rules as data and plain logic, shared by the server (which runs them), the client (which
predicts and draws them) and the bots. Nothing here does I/O or knows about a match's threads.

What the rules are is written up by feature in `design/`: characters.md (the roster, roles, cast
behaviors, movement), combat.md (projectiles, on-hit effects, items), barriers.md, traps.md,
match-flow.md (the opening). This file is how the package is put together.

## The registries

- **Characters** — `CharacterDef` (its schema; `Ability.scala` for abilities and their
  `CastBehavior`s; `CombatRole`) serves every character by id from `roster/`, a file per category
  (`Originals` … `Specialists`, and `Retired` for definitions no character throws any more).
  `Roster` gathers them. Initializing `CharacterDef` registers every `ProjectileDef`: each category's
  definitions first, then the variants (`Roster.variants`), which copy their base's numbers from the
  registry.
- **Projectiles** — `ProjectileDef` (what a type does; `ProjectileType` names the ids,
  `OnHitEffect.scala` the effects) is a registry filled by `CharacterDef`; `ProjectileDef.get`
  makes sure `CharacterDef` has initialized, so the first lookup anywhere finds every type.
  `ProjectileDefRegistryTest` checks that, alone in its JVM.
- **Traps** — `TrapDef` in `Trap.scala` fills its own registry (`TrapDef.all`), so nothing can look a
  trap up before it is there; `TrapDef.register` is how a test adds one the roster lacks.
- **Tiles** — `Tile.all`: an id is its column in `sprites/tiles.png`, so ids have no gaps.

## The rest

- `Player` — a player's state as the server holds it (health, position, effect timers, a barrier,
  server moves) and as the client mirrors it for everyone else.
- `Projectile` — one in flight: its movement step, collision cells, ricochet, distance travelled.
- `WorldData`, `Tile` (with `TileForm`: ground, pool, block, prop), `TeamDivider`, `MatchOpening`.
- `Movement` (the one rule that turns a character's pace into a step interval), `Teleport` (the
  landing cell of a star or a blink, picked the same way on both sides), `TrapPlacement`,
  `Barrier` (the shape both sides stop shots on and draw), `TeamAssignment`, `MatchResults`.

## Adding a New Tile Type
1. Add case object to `Tile.scala` with the next id (an id is its atlas column, so no gaps),
   name, walkable, color. A non-walkable tile that isn't a block overrides `form` (`Pool` or
   `Prop`), and a prop names the `ground` it grows out of.
2. Add to `Tile.all` sequence, and bump the count in `WorldDataTest`
3. Write its draw function in `scripts/generate_tiles.py` and add it to `TILES` with its form. A
   block also needs `BLOCK_HEIGHTS` (and `ANIMATED_BLOCKS` if its frames move). A small prop can
   be enlarged with `PROP_SCALE`. A prop whose ground isn't grass goes in `tile_gallery.py`'s
   `PROP_GROUND`.
4. Run `python3 scripts/generate_tiles.py`, then look at it with `scripts/tile_gallery.py` and in
   the render bench (`TileTest` fails until the atlas has the new column)
5. Use in world JSON files (the map editor's palette lists every tile)

## Adding New World Layer Type
1. Add case in `WorldLoader.parseLayer()` match statement
2. Implement tile placement logic
3. Supported layer types (`common/world/WorldLoader`): `fill`, `rect`, `border`, `circle`, `line`, `points`, `grid`

Where the three generated maps come from, and the rules every map is held to, are in
scripts/CLAUDE.md (*Maps*).
