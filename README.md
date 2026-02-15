# Grid Game

A multiplayer 2D isometric arena game built with Scala and JavaFX.

Players connect to a server, join or create lobbies, pick from 11 unique characters, and battle in timed free-for-all matches across 19 maps. Features include ranked matchmaking with ELO, account-based authentication, bot players, and a built-in map editor.

## Quick Start

```bash
# Build
bazel build //...

# Run server (default port 25565)
bazel run //src/main/scala/com/gridgame/server:server

# Run client (login UI handles connection)
bazel run //src/main/scala/com/gridgame/client:client
```

## Features

- **11 Characters** - Each with unique primary attack, Q ability, and E ability (dashes, teleports, AoE, crowd control, etc.)
- **19 Maps** - From small arenas to large battlegrounds with varied terrain
- **Lobby System** - Create/join lobbies, configure map and game duration, add bots
- **Ranked Queue** - ELO-based matchmaking
- **Accounts** - Login/register with persistent stats, match history, and leaderboards
- **Isometric Rendering** - 2.5D tile-based world with parallax backgrounds
- **Map Editor** - Standalone tool for creating and editing world files

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

| Character | HP | Primary | Q Ability | E Ability |
|-----------|-----|---------|-----------|-----------|
| Spaceman | 100 | Normal | Tentacle (pull) | Ice Beam (freeze) |
| Gladiator | 100 | Axe | Spear Throw (scaling dmg) | Rope (pull) |
| Wraith | 100 | Soul Bolt (pierces walls) | Phase Shift (ethereal) | Haunt (teleport behind) |
| Wizard | 70 | Arcane Bolt (chargeable, pierces walls) | Fireball (high dmg) | Blink (teleport) |
| Tidecaller | 100 | Splash (chargeable AoE) | Tidal Wave (fan + push) | Geyser (AoE explosion) |
| Soldier | 100 | Bullet | Grenade (thrown explosive) | Rocket (impact explosive) |
| Raptor | 100 | Talon (melee) | Swoop (dash) | Gust (push) |
| Assassin | 85 | Shuriken (melee) | Poison Dart (slow) | Shadow Step (dash) |
| Warden | 110 | Chain Bolt (micro-freeze) | Lockdown (fan freeze) | Snare Mine (AoE freeze) |
| Samurai | 110 | Katana (melee) | Iaijutsu (dash) | Whirlwind (360 swords) |
| Plague Doctor | 100 | Plague Bolt (AoE) | Miasma (explosion) | Blight Bomb (explosion) |

## Tech Stack

- **Language**: Scala 2.13
- **UI**: JavaFX 21
- **Networking**: Netty (TCP + UDP, 64-byte fixed packets)
- **Database**: SQLite (accounts, match history, ELO)
- **Build**: Bazel with rules_scala
- **Assets**: Python (Pillow) sprite generators

## Project Structure

```
src/main/scala/com/gridgame/
  common/          # Shared models, protocol (15 packet types), world loader
  server/          # Game server, lobbies, auth, bots, projectiles, items, ranked queue
  client/          # JavaFX client, rendering, input handlers
  mapeditor/       # Standalone map editor
worlds/            # 19 JSON map definitions
sprites/           # Tile sheet + 11 character sprite sheets
scripts/           # Python sprite generators
```
