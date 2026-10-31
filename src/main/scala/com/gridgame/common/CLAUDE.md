# common

What the server, the client, the map editor and the tools share. Each package is a Bazel library and
depends only on the ones before it (see BUILD.bazel here):

```
core (Constants, WorldRegistry, BuildInfo)  <-  model  <-  protocol  <-  observability
  ^                                               ^
  +------  diagnostics                            +------  world
```

- **model/** — the game's rules as data and plain logic, with no I/O: characters and their
  projectiles (`roster/`), effects, players, projectiles in flight, tiles and the world, barriers,
  traps, movement, teleports, the match's opening. See model/CLAUDE.md, and the feature docs in
  `design/` for what the rules are.
- **protocol/** — the wire: every packet's layout, and signing (protocol/CLAUDE.md).
- **world/** — `WorldLoader`, which reads a map's JSON into a `WorldData` (model/CLAUDE.md has its
  layer types).
- **observability/** — the OpenTelemetry facade (observability/CLAUDE.md).
- **diagnostics/** — `Recording`: a match's recording as the client writes it for a problem report and
  the report tool reads it (segments of records, each record's layout; design/diagnostics.md).
- `BuildInfo` says which build this is (the commit, whether the tree had changes, when), from
  `build_info.properties`, which the `build_info` target stamps from `scripts/workspace_status.sh`:
  a runtime dependency of the binaries, so a new commit recompiles nothing.
- `Constants.scala` holds the numbers everything agrees on: the tick rates, the packet sizes, the
  display sizes, the opening's length. `WorldRegistry` lists the maps a lobby can pick.
