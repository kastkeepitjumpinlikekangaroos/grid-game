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
│   ├── Constants.scala         # Grid size, ports, timing constants
│   ├── model/
│   │   ├── Direction.scala     # Up/Down/Left/Right enum
│   │   ├── Player.scala        # Player entity (id, position, color, direction)
│   │   ├── Position.scala      # Grid coordinate wrapper with validation
│   │   ├── Tile.scala          # Terrain types (Grass, Water, Sand, etc.)
│   │   └── WorldData.scala     # World grid, spawn points, walkability
│   ├── protocol/
│   │   ├── Packet.scala        # Base packet class (64 bytes fixed)
│   │   ├── PacketType.scala    # PLAYER_JOIN, UPDATE, LEAVE, WORLD_INFO, HEARTBEAT
│   │   ├── PacketSerializer.scala  # Binary serialization/deserialization
│   │   ├── PlayerJoinPacket.scala
│   │   ├── PlayerUpdatePacket.scala
│   │   ├── PlayerLeavePacket.scala
│   │   └── WorldInfoPacket.scala
│   └── world/
│       └── WorldLoader.scala   # JSON world file parser
├── server/
│   ├── ServerMain.scala        # Entry point, arg parsing
│   ├── GameServer.scala        # UDP receive loop, broadcasting
│   ├── ClientHandler.scala     # Packet processing logic
│   └── ClientRegistry.scala    # Connected player storage
└── client/
    ├── ClientMain.scala        # JavaFX Application entry
    ├── GameClient.scala        # Client state, packet processing
    ├── NetworkThread.scala     # UDP socket, receive loop, heartbeat
    ├── input/
    │   └── KeyboardHandler.scala   # WASD input handling
    └── ui/
        ├── GameCanvas.scala    # Terrain and player rendering
        └── SpriteGenerator.scala   # Procedural character sprites

worlds/                         # World definition files (JSON)
├── meadow.json                # Peaceful meadow with river
├── island.json                # Tropical island
├── castle.json                # Castle with walls
├── winter.json                # Snowy landscape
└── volcano.json               # Volcanic island with lava
```

## Network Protocol

Fixed 64-byte UDP packets with this structure:

| Bytes | Field | Description |
|-------|-------|-------------|
| 0 | Type ID | Packet type (0x01-0x05) |
| 1-4 | Sequence | Incrementing packet number |
| 5-20 | Player ID | UUID (2 longs) |
| 21-24 | X Position | Grid X coordinate |
| 25-28 | Y Position | Grid Y coordinate |
| 29-32 | Color RGB | Player color (ARGB) |
| 33-36 | Timestamp | Unix timestamp (seconds) |
| 37-63 | Payload | Packet-specific data (27 bytes) |

### Packet Types

- **PLAYER_JOIN (0x01)**: Player connects. Payload = player name.
- **PLAYER_UPDATE (0x02)**: Position/color change. Broadcast to others.
- **PLAYER_LEAVE (0x03)**: Player disconnects.
- **WORLD_INFO (0x04)**: Server sends world filename to client on join.
- **HEARTBEAT (0x05)**: Keep-alive, sent every 3 seconds.

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

## World System

Worlds are JSON files with layers that build terrain:

```json
{
  "name": "World Name",
  "width": 200,
  "height": 200,
  "layers": [
    { "type": "fill", "tile": "grass" },
    { "type": "rect", "tile": "water", "x": 10, "y": 10, "width": 20, "height": 20 },
    { "type": "circle", "tile": "sand", "cx": 100, "cy": 100, "radius": 15 },
    { "type": "line", "tile": "path", "x1": 0, "y1": 100, "x2": 200, "y2": 100, "thickness": 3 },
    { "type": "border", "tile": "wall", "thickness": 2 },
    { "type": "scatter", "tile": "tree", "density": 0.03, "avoid": ["water", "path"] }
  ],
  "spawnPoints": [
    { "x": 100, "y": 100 }
  ]
}
```

### Layer Types

| Type | Description | Required Fields |
|------|-------------|-----------------|
| fill | Fill entire world | tile |
| rect | Rectangle area | tile, x, y, width, height |
| circle | Circular area | tile, cx, cy, radius |
| line | Line with thickness | tile, x1, y1, x2, y2, thickness |
| border | Edge border | tile, thickness |
| scatter | Random placement | tile, density, (optional: avoid, minX, maxX, minY, maxY) |
| points | Specific coordinates | tile, points: [{x, y}, ...] |

### Tile Types

| Tile | Walkable | Color |
|------|----------|-------|
| grass | Yes | Green |
| water | No | Blue |
| deep_water | No | Dark Blue |
| sand | Yes | Tan |
| stone | Yes | Gray |
| path | Yes | Light Brown |
| wall | No | Brown |
| tree | No | Dark Green |
| snow | Yes | White |
| ice | Yes | Light Blue |
| lava | No | Orange-Red |
| mountain | No | Dark Gray |

## Key Constants (Constants.scala)

```scala
GRID_SIZE = 1000          // Max world size
CELL_SIZE_PX = 20         // Pixels per cell
VIEWPORT_CELLS = 50       // Visible cells (50x50)
SERVER_PORT = 25565       // Default port
HEARTBEAT_INTERVAL_MS = 3000
CLIENT_TIMEOUT_MS = 10000
MOVE_RATE_LIMIT_MS = 100  // Max 10 moves/second
PACKET_SIZE = 64          // Fixed packet size
```

## Key Implementation Details

### Thread Model (Client)
- **Main Thread**: JavaFX UI, rendering via AnimationTimer
- **NetworkThread**: UDP receive loop + heartbeat scheduler
- **PacketProcessor**: Processes incoming packets from queue

### Thread Safety
- `AtomicReference` for mutable state (position, direction, world)
- `ConcurrentHashMap` for player registry
- `BlockingQueue` for packet processing
- `CountDownLatch` for socket ready synchronization

### Rendering Pipeline
1. Clear canvas
2. Draw terrain tiles (from WorldData)
3. Draw grid lines (subtle overlay)
4. Draw remote players (sprites)
5. Draw local player (sprite + yellow highlight)
6. Draw HUD (world name, position, player count)

### Sprite Generation
Characters are procedurally generated based on:
- Player's UUID-derived color
- Current facing direction (Up/Down/Left/Right)
- Cached per (color, direction) pair

## Common Modifications

### Adding a New Tile Type
1. Add case object to `Tile.scala` with id, name, walkable, color
2. Add to `Tile.all` sequence
3. Use in world JSON files

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
