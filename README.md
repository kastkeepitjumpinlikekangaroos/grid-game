# Grid Game

A multiplayer 2D isometric arena game built with Scala and JavaFX.

Players connect to a server, join or create lobbies, pick from 112 unique characters across 8 themed categories, and battle in timed free-for-all matches across 16 maps. Features include ranked matchmaking with ELO, account-based authentication, bot players, gamepad support, and a built-in map editor.

## Quick Start

```bash
# Build
bazel build //...

# Run server (default port 25565)
bazel run //src/main/scala/com/gridgame/server:server

# Run server on custom port
bazel run //src/main/scala/com/gridgame/server:server -- 25566

# Run client (login UI handles connection)
bazel run //src/main/scala/com/gridgame/client:client

# Run map editor
bazel run //src/main/scala/com/gridgame/mapeditor
```

## Features

- **112 Characters** - Across 8 categories (Original, Elemental, Undead/Dark, Medieval/Fantasy, Sci-Fi/Tech, Nature/Beast, Mythological, Specialist), each with unique primary attack, Q ability, and E ability
- **16 Maps** - From small arenas to large battlegrounds with varied terrain (34 tile types)
- **Advanced Projectile System** - Pierce, boomerang, ricochet, AoE splash, explosions, charge scaling, distance-based damage, and 10 on-hit effects (freeze, burn, root, slow, pull, push, life steal, vortex, speed boost, teleport)
- **5 Item Types** - Heart (heal), Star (speed), Gem (score), Shield (defense), Fence (placeable barrier)
- **Lobby System** - Create/join lobbies, configure map and game duration, add bots
- **Ranked Queue** - ELO-based matchmaking
- **Accounts** - Login/register with persistent stats, match history, and leaderboards
- **Isometric Rendering** - 2.5D tile-based world with parallax backgrounds
- **Gamepad Support** - Controller input via LWJGL/GLFW
- **Character Selection** - Filterable, categorized grid with ability previews and animated sprite previews
- **Map Editor** - Standalone tool with tile palette, drawing tools, undo/redo, properties panel, and world file export

## Controls

| Key | Action |
|-----|--------|
| WASD | Move |
| Mouse | Aim direction |
| Left Click / Space | Shoot |
| Shift+Space | Burst shot (charged) |
| Q | Ability 1 |
| E | Ability 2 |
| Hold Space | Charge shot |
| F11 | Toggle fullscreen |

## Characters

112 characters organized into 8 categories:

| Category | Count | Examples |
|----------|-------|----------|
| Original | 12 | Spaceman, Gladiator, Wraith, Wizard, Tidecaller, Soldier, Raptor, Assassin, Warden, Samurai, Plague Doctor, Vampire |
| Elemental | 15 | Pyromancer, Cryomancer, Stormcaller, Earthshaker, Inferno, Glacier, Avalanche |
| Undead/Dark | 15 | Necromancer, Skeleton King, Banshee, Lich, Reaper, Deathknight, Shadowfiend |
| Medieval/Fantasy | 15 | Paladin, Ranger, Berserker, Druid, Bard, Monk, Valkyrie, Warlock |
| Sci-Fi/Tech | 15 | Cyborg, Hacker, MechPilot, Android, Chronomancer, Graviton, Railgunner |
| Nature/Beast | 15 | Wolf, Serpent, Bear, Hawk, Phoenix, Hydra, Gorilla, Chameleon |
| Mythological | 15 | Minotaur, Medusa, Cerberus, Kraken, Sphinx, Griffin, Fenrir, Chimera |
| Specialist | 10 | Alchemist, Puppeteer, Gambler, Pirate, Chef, Musician, Shapeshifter |

Each character has unique abilities using cast behaviors: StandardProjectile, PhaseShiftBuff, DashBuff, TeleportCast, FanProjectile, and GroundSlam.

## Tech Stack

- **Language**: Scala 2.13
- **UI**: JavaFX 21
- **Networking**: Netty (TCP + UDP, 64-byte fixed packets)
- **Database**: SQLite (accounts, match history, ELO)
- **Build**: Bazel with rules_scala
- **Input**: LWJGL 3.3.4 (gamepad support via GLFW)
- **Assets**: Python (Pillow) sprite generators

## Project Structure

```
src/main/scala/com/gridgame/
  common/            # Shared models, protocol (15 packet types), world loader
    model/           # Player, Tile (34), CharacterDef (112), ProjectileDef, Item, Projectile
    protocol/        # 15 packet types, serialization
    world/           # WorldLoader (JSON map parsing, 7 layer types)
  server/            # Game server, lobbies, auth, bots, projectiles, items, ranked queue
  client/            # JavaFX client, rendering, input handlers (keyboard, mouse, controller)
    ui/              # GameCanvas, TileRenderer, BackgroundRenderer, CharacterSelectionPanel
    input/           # KeyboardHandler, MouseHandler, ControllerHandler
  mapeditor/         # Standalone map editor (12 source files)
worlds/              # 16 JSON map definitions
sprites/             # Tile sheet + 112 character sprite sheets
scripts/             # Python sprite generators (14 scripts)
docs/                # GitHub Pages landing site
```
