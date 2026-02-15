# Grid Game - Multiplayer 2D Arena Game

A multiplayer 2D isometric arena game built with Scala and JavaFX, using TCP+UDP networking via Netty.

## Build & Run

```bash
# Build everything
bazel build //...

# Run server (lobby-based, maps selected per-lobby)
bazel run //src/main/scala/com/gridgame/server:server

# Run server on custom port
bazel run //src/main/scala/com/gridgame/server:server -- 25566

# Run client (login UI prompts for host/port)
bazel run //src/main/scala/com/gridgame/client:client

# Run map editor
bazel run //src/main/scala/com/gridgame/mapeditor
```

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────────┐
│                            CLIENT                                │
│  ┌─────────────┐  ┌─────────────┐  ┌────────────────────────┐   │
│  │ ClientMain  │  │ GameClient  │  │ NetworkThread          │   │
│  │ (JavaFX App)│──│ (State)     │──│ (TCP+UDP via Netty)    │   │
│  └─────────────┘  └─────────────┘  └────────────────────────┘   │
│         │                │                                       │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────────────────┐  │
│  │ GameCanvas  │  │ KeyboardHandler│ │ MouseHandler           │  │
│  │ (Isometric) │  │ (WASD+QE)    │  │ (Aim/Click)            │  │
│  └─────────────┘  └──────────────┘  └────────────────────────┘  │
│         │                                                        │
│  ┌─────────────┐  ┌──────────────┐                              │
│  │TileRenderer │  │ Background   │                              │
│  │ (Tiles)     │  │ Renderer     │                              │
│  └─────────────┘  └──────────────┘                              │
└──────────────────────────────────────────────────────────────────┘
                           │ TCP + UDP
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│                            SERVER                                │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────────────────┐  │
│  │ ServerMain  │──│ GameServer   │──│ ClientHandler          │  │
│  │ (Entry)     │  │ (Netty TCP/  │  │ (Packet Processing)    │  │
│  │             │  │  UDP Loops)  │  │                        │  │
│  └─────────────┘  └──────────────┘  └────────────────────────┘  │
│                          │                                       │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────┐ │
│  │ LobbyManager │  │ GameInstance │  │ ClientRegistry         │ │
│  │ (Lobby CRUD) │  │ (Match State)│  │ (Player Map)           │ │
│  └──────────────┘  └──────────────┘  └────────────────────────┘ │
│                          │                                       │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────┐ │
│  │ AuthDatabase │  │ Projectile   │  │ ItemManager            │ │
│  │ (SQLite)     │  │ Manager      │  │ (Spawns/Pickups)       │ │
│  └──────────────┘  └──────────────┘  └────────────────────────┘ │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────┐ │
│  │ RankedQueue  │  │ BotManager   │  │ KillTracker            │ │
│  │ (Matchmaking)│  │ (AI Players) │  │ (Scoring)              │ │
│  └──────────────┘  └──────────────┘  └────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
```

## Project Structure

```
src/main/scala/com/gridgame/
├── common/                     # Shared code between client and server
│   ├── model/                  # Data models (Player, Tile, CharacterDef, Item, Projectile, etc.)
│   ├── protocol/               # Network packets (15 packet types)
│   └── world/                  # WorldLoader (JSON map parsing)
├── server/                     # Server (GameServer, Lobby, Auth, Bots, Projectiles, Items)
├── client/                     # Client (GameClient, UI, Input handlers)
│   ├── ui/                     # GameCanvas, TileRenderer, BackgroundRenderer, SpriteGenerator
│   └── input/                  # KeyboardHandler, MouseHandler
└── mapeditor/                  # Standalone map editor tool

src/test/scala/com/gridgame/   # Tests
worlds/                         # World definition files (19 JSON maps)
sprites/                        # Sprite assets (tiles.png + 11 character PNGs)
scripts/                        # Asset generation scripts (tiles + character generators)
```

## Asset Generation

Sprites are pre-rendered images loaded at runtime.

### Tile Sprites
```bash
# Requires Pillow: pip install Pillow
python3 scripts/generate_tiles.py
```

- **Input**: Tile definitions hardcoded in the script (colors from `Tile.scala`, elevations per tile)
- **Output**: `sprites/tiles.png` (40x56px per cell, 34 tiles total)
- Flat (walkable) tiles: diamond at bottom 20px, upper area transparent
- Elevated (non-walkable) tiles: top diamond + left/right side faces, bottom-aligned
- If you add a new tile type to `Tile.scala`, also add its entry to the `TILES` list in this script and regenerate

### Character Sprites
Each character has a dedicated generator script (`scripts/generate_<name>.py`) that produces a sprite sheet with 4 directions x 4 animation frames.

```bash
python3 scripts/generate_gladiator.py   # -> sprites/gladiator.png
python3 scripts/generate_wizard.py      # -> sprites/wizard.png
# etc.
```

## Network Protocol

Fixed 64-byte packets over TCP (reliable) and UDP (fast updates), using Netty. Byte order: BIG_ENDIAN.

### Packet Types (15 total)

| ID   | Name              | Transport | Description                    |
|------|-------------------|-----------|--------------------------------|
| 0x01 | PLAYER_JOIN       | TCP       | Player enters game             |
| 0x02 | PLAYER_UPDATE     | UDP       | Position/health updates        |
| 0x03 | PLAYER_LEAVE      | TCP       | Player disconnects             |
| 0x04 | WORLD_INFO        | TCP       | World filename                 |
| 0x05 | HEARTBEAT         | UDP       | Keep-alive signal              |
| 0x06 | PROJECTILE_UPDATE | UDP       | Projectile movement            |
| 0x07 | ITEM_UPDATE       | TCP       | Item spawns/pickups            |
| 0x08 | TILE_UPDATE       | TCP       | Tile changes                   |
| 0x09 | LOBBY_ACTION      | TCP       | Lobby operations               |
| 0x0A | GAME_EVENT        | TCP       | Kill/death events              |
| 0x0B | AUTH_REQUEST      | TCP       | Login/register                 |
| 0x0C | AUTH_RESPONSE     | TCP       | Auth result                    |
| 0x0D | MATCH_HISTORY     | TCP       | Game statistics                |
| 0x0E | RANKED_QUEUE      | TCP       | Ranked matchmaking             |
| 0x0F | LEADERBOARD       | TCP       | Rankings                       |

### Connection Flow

```
Client                              Server
   │                                   │
   │──── AUTH_REQUEST ────────────────>│  (login/register)
   │<─── AUTH_RESPONSE ───────────────│
   │                                   │
   │──── LOBBY_ACTION (list) ────────>│  (browse/create/join lobbies)
   │<─── LOBBY_ACTION (lobby data) ───│
   │                                   │
   │──── LOBBY_ACTION (start) ───────>│  (host starts game)
   │<─── WORLD_INFO (filename) ───────│
   │<─── PLAYER_JOIN (broadcast) ─────│
   │                                   │
   │──── PLAYER_UPDATE ──────────────>│  (gameplay loop)
   │<─── PLAYER_UPDATE (broadcast) ───│
   │<─── PROJECTILE_UPDATE ───────────│
   │<─── ITEM_UPDATE ─────────────────│
   │<─── GAME_EVENT ──────────────────│
   │                                   │
   │──── HEARTBEAT (every 3s) ───────>│
   │                                   │
```

### Character Sprites
Each of the 11 characters has a pre-rendered sprite sheet with 4 directions and 4 animation frames per direction. Sprites are loaded from `sprites/<name>.png` at runtime.

## Common Modifications

### Adding a New Tile Type
1. Add case object to `Tile.scala` with id, name, walkable, color
2. Add to `Tile.all` sequence
3. Add entry to `TILES` list in `scripts/generate_tiles.py` (with color and elevation)
4. Run `python3 scripts/generate_tiles.py` to regenerate `sprites/tiles.png`
5. Use in world JSON files

### Adding a New Packet Type
1. Add to `PacketType.scala` (new case object with unique ID, specify `tcp = true/false`)
2. Add to `PacketType.values` array
3. Create packet class extending `Packet`
4. Add deserialization case in `PacketSerializer.deserialize()`
5. Handle in `GameClient.processPacket()` or `ClientHandler.processPacket()`

### Adding a New Character
1. Add `CharacterId` entry in `CharacterId.scala`
2. Define `ProjectileDef` entries for the character's projectiles in `CharacterDef.scala`
3. Register projectile defs in `ProjectileDef.register()`
4. Create `CharacterDef` val with abilities, stats, and sprite sheet path
5. Add to `byId` map and `all` sequence in `CharacterDef`
6. Create sprite generator script: `scripts/generate_<name>.py`
7. Run the script to produce `sprites/<name>.png`

### Adding New World Layer Type
1. Add case in `WorldLoader.parseLayer()` match statement
2. Implement tile placement logic
3. Supported layer types: `fill`, `rect`, `border`, `circle`, `line`

## Bazel Build Notes

- Uses `rules_scala` with Scala 2.13.16
- Dependencies: JavaFX 21.0.1, Netty 4.1.104, Gson 2.10.1, SQLite JDBC 3.44.0, Guava 32.1.3
- Build targets:
  - `//src/main/scala/com/gridgame/server:server`
  - `//src/main/scala/com/gridgame/client:client`
  - `//src/main/scala/com/gridgame/client:client_windows`
  - `//src/main/scala/com/gridgame/common:common`
  - `//src/main/scala/com/gridgame/mapeditor`
