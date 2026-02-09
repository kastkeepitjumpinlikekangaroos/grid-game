# Grid Game - Multiplayer 2D Game

A multiplayer 2D grid-based game built with Scala and JavaFX, using UDP networking.

## Build & Run

```bash
# Build everything
bazel build //...

# Run server with a world
bazel run //src/main/scala/com/gridgame/server:server -- --world=worlds/island.json

# Run client (connects to localhost:25565 by default)
bazel run //src/main/scala/com/gridgame/client:client

# Run client with custom host/port
bazel run //src/main/scala/com/gridgame/client:client -- --host=192.168.1.10 --port=25565
```

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                         CLIENT                               │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │ ClientMain  │  │ GameClient  │  │ NetworkThread       │  │
│  │ (JavaFX App)│──│ (State)     │──│ (UDP Send/Receive)  │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
│         │                │                                   │
│  ┌─────────────┐  ┌─────────────┐                           │
│  │ GameCanvas  │  │ KeyHandler  │                           │
│  │ (Rendering) │  │ (WASD)      │                           │
│  └─────────────┘  └─────────────┘                           │
└─────────────────────────────────────────────────────────────┘
                           │ UDP
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                         SERVER                               │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │ ServerMain  │──│ GameServer  │──│ ClientHandler       │  │
│  │ (Entry)     │  │ (UDP Loop)  │  │ (Packet Processing) │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
│                          │                                   │
│                   ┌─────────────┐                           │
│                   │ClientRegistry│                           │
│                   │ (Player Map) │                           │
│                   └─────────────┘                           │
└─────────────────────────────────────────────────────────────┘
```

## Project Structure

```
src/main/scala/com/gridgame/
├── common/                     # Shared code between client and server
├── server/
└── client/

worlds/                         # World definition files (JSON)
sprites/                        # Generated sprite assets (tiles.png, character.png)
scripts/                        # Asset generation scripts
```

## Asset Generation

Tile sprites are pre-rendered images loaded at runtime. Regenerate after changing tile colors, adding tiles, or adjusting elevations.

```bash
# Requires Pillow: pip install Pillow
python3 scripts/generate_tiles.py
```

- **Input**: Tile definitions hardcoded in the script (colors from `Tile.scala`, elevations per tile)
- **Output**: `sprites/tiles.png` (800x56 PNG, 20 columns x 1 row, 40x56px per cell)
- Flat (walkable) tiles: diamond at bottom 20px, upper area transparent
- Elevated (non-walkable) tiles: top diamond + left/right side faces, bottom-aligned
- If you add a new tile type to `Tile.scala`, also add its entry to the `TILES` list in this script and regenerate

## Network Protocol

Fixed 64-byte UDP packets


### Connection Flow

```
Client                              Server
   │                                   │
   │──── PLAYER_JOIN ─────────────────>│
   │                                   │
   │<─── WORLD_INFO (filename) ────────│
   │<─── PLAYER_JOIN (broadcast) ──────│
   │                                   │
   │──── PLAYER_UPDATE ───────────────>│
   │<─── PLAYER_UPDATE (broadcast) ────│
   │                                   │
   │──── HEARTBEAT (every 3s) ────────>│
   │                                   │
```

### Sprite Generation
Characters are procedurally generated based on:
- Player's UUID-derived color
- Current facing direction (Up/Down/Left/Right)
- Cached per (color, direction) pair

## Common Modifications

### Adding a New Tile Type
1. Add case object to `Tile.scala` with id, name, walkable, color
2. Add to `Tile.all` sequence
3. Add entry to `TILES` list in `scripts/generate_tiles.py` (with color and elevation)
4. Run `python3 scripts/generate_tiles.py` to regenerate `sprites/tiles.png`
5. Use in world JSON files

### Adding a New Packet Type
1. Add to `PacketType.scala` (new case object with unique ID)
2. Add to `PacketType.values` array
3. Create packet class extending `Packet`
4. Add deserialization case in `PacketSerializer.deserialize()`
5. Handle in `GameClient.processPacket()` or `ClientHandler.processPacket()`

### Adding New World Layer Type
1. Add case in `WorldLoader.parseLayer()` match statement
2. Implement tile placement logic

## Bazel Build Notes

- Uses `rules_scala` for Scala compilation
- JavaFX dependencies from Maven
- Gson for JSON parsing
- Build targets:
  - `//src/main/scala/com/gridgame/server:server`
  - `//src/main/scala/com/gridgame/client:client`
  - `//src/main/scala/com/gridgame/common:common`
