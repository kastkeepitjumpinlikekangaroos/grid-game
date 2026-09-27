# Characters

The roster: 112 characters in 8 categories. Each has a role that sets its health and its pace, a
primary attack, and two abilities (Q and E), each of which casts in one of a handful of ways. The
schema is `CharacterDef.scala` and `Ability.scala` in `common/model`; the data is the category files
in `common/model/roster/`; `CharacterDef` registers every projectile definition and serves the
characters by id. How an attack is drawn and how it sounds are the client's (see
client/render/projectiles, client/render/blasts, client/audio).

## Characters (112 total)

Characters are defined across 8 categories: the ids in `CharacterId.scala`, each category's characters and the projectiles they
are the first to throw in its file in `common/model/roster/` (`Originals.scala` … `Specialists.scala`):

| Category | IDs | Count | Characters |
|----------|-----|-------|------------|
| Original | 0-11 | 12 | Spaceman, Gladiator, Wraith, Wizard, Tidecaller, Soldier, Raptor, Assassin, Warden, Samurai, PlagueDoctor, Vampire |
| Elemental | 12-26 | 15 | Pyromancer, Cryomancer, Stormcaller, Earthshaker, Windwalker, MagmaKnight, Frostbite, Sandstorm, Thornweaver, Cloudrunner, Inferno, Glacier, Mudslinger, Ember, Avalanche |
| Undead/Dark | 27-41 | 15 | Necromancer, SkeletonKing, Banshee, Lich, Ghoul, Reaper, Shade, Revenant, Gravedigger, Dullahan, Phantom, Mummy, Deathknight, Shadowfiend, Poltergeist |
| Medieval/Fantasy | 42-56 | 15 | Paladin, Ranger, Berserker, Crusader, Druid, Bard, Monk, Cleric, Rogue, Barbarian, Enchantress, Jester, Valkyrie, Warlock, Inquisitor |
| Sci-Fi/Tech | 57-71 | 15 | Cyborg, Hacker, MechPilot, Android, Chronomancer, Graviton, Tesla, Nanoswarm, Voidwalker, Photon, Railgunner, Bombardier, Sentinel, Pilot, Glitcher |
| Nature/Beast | 72-86 | 15 | Wolf, Serpent, Spider, Bear, Scorpion, Hawk, Shark, Beetle, Treant, Phoenix, Hydra, Mantis, Jellyfish, Gorilla, Chameleon |
| Mythological | 87-101 | 15 | Minotaur, Medusa, Cerberus, Centaur, Kraken, Sphinx, Cyclops, Harpy, Griffin, Anubis, Yokai, Golem, Djinn, Fenrir, Chimera |
| Specialist | 102-111 | 10 | Alchemist, Puppeteer, Gambler, Blacksmith, Pirate, Chef, Musician, Astronomer, Runesmith, Shapeshifter |

## Roles, health and pace
Every character names a `role: CombatRole` (`common/model/CombatRole.scala`), and the role sets
the two numbers that trade range for staying power. Whoever has to get into range gets the
health to survive getting there. Whoever can hit from range walks a little quicker, so a ranged
character can kite a melee one (walk away, shooting back) for as long as both only walk. A melee
character closes the gap with its abilities or an item, not its feet.

| Role | Primary reach | Characters | Health | `moveSpeed` | Step |
|---|---|---|---|---|---|
| `Ranged` | 12 or more | 75 | 60-80 | 1.0 | 50ms, 20 cells/s |
| `Skirmisher` | 7-10 | 7 | 105-150 | 0.97 | 52ms, 19.2 cells/s |
| `Melee` | 6 cells or less | 30 | 105-150 | 0.94 | 53ms, 18.9 cells/s |

- **The role is named on each character, not worked out.** `CombatRole.forRange` is the rule
  (reach measured fully charged), with one exception: the Monk, whose 6-cell punch reaches 10
  charged, is melee. `RosterBalanceTest` checks every character against the rule and its list of
  exceptions.
- **Skirmishers** are the boulder throwers (Earthshaker, Avalanche, Beetle, Gorilla, Golem,
  Cyclops) plus Ember: melee health, a pace between the two.
- **Health keeps each character's place within its class.** It was mapped from the old values
  (ranged `60 + (old - 65) * 20/60`, melee and skirmishers `105 + (old - 65) * 45/65`, rounded to
  5), then the glass cannons (Wizard, Railgunner, Astronomer, Cleric, Pilot) were set at the
  ranged floor and the Minotaur at the melee ceiling. The toughest ranged character (80) stays at
  least 20 below the frailest fighter (105). `RosterBalanceTest` pins the bands, that gap and the
  order of the paces.
- **Only just quicker:** walking straight away, a ranged character opens about a cell a second on
  a melee one. Played over the network: a Soldier walking away from a Berserker and shooting back
  went 20.1 cells a second to its 19.1, opened the gap from 8 cells to 12 and killed it in 3.9s
  without being swung at. From 8-9 cells, the Gladiator's rope pulled the Soldier in and the
  Hawk's Dive Bomb teleport landed beside it; each killed it inside 1.4s. `RosterBalanceTest` keeps
  the gap between half a cell and two cells a second.
- **Every melee and skirmisher kit has a way in, or is an anchor** (Plan 5a). A way in is a
  teleport; a phase, which walks at twice the pace; a pull, stun, root or slow on a projectile fast
  enough to land on someone walking away from 8 cells; or a dash that gains 6 cells over walking.
  The Crusader, Golem, Beetle and Avalanche are **anchors** instead: a barrier or a big hold, with
  a team to do the chasing. `MeleeKitTest` checks both, and that every kit has crowd control.
  - A player's `DashBuff` moves `maxDistance` cells over `durationMs` while the runner keeps
    walking, so it gains only `maxDistance - 18.9 * durationMs / 1000` cells over walking. Melee
    dashes were 300-500ms and gained 2-5; they are now 150ms (the Minotaur's 14 cells 200ms), and
    the 8-cell ones 10 cells: 7 to 10 cells each. A shorter dash is a shorter invulnerable phase
    too. Bots dash at once (`BotController` sets their position), so it never short-changed them.
    Ranged dashes are escapes and were left as they were.
  - A projectile flies `33 x speedMultiplier` cells a second, so on a runner walking straight
    away it lands from at most `maxRange x (1 - 0.6 / speedMultiplier) + hitRadius + 1` cells: a
    0.85 rope from 10, a 0.9 closer of 18 cells from 8, a 0.7 bolt from 5, and a 0.6 axe (a
    runner's own speed) only inside its hit radius. That formula is for a runner moving smoothly,
    and one moves in one-cell steps: a 16-cell closer at 0.9, which it put at 8.1, missed from 8
    at 3 phases of the step in 20, and the play test's Hammer Throw missed from 8 over the network.
    The 16-cell pulls and holds are 0.95. `MeleeKitsTest` walks a runner away from every
    projectile way in at 20 phases of its step and checks each lands from 8, and that the Death
    Bolt the Death Grip replaced never could.
  - Played over the network (Plan 5a), a Soldier walking away from 8 cells and shooting back was
    caught by the Berserker's Rage Charge, the Chef's Meat Hook, the Vampire's Mist Form, and the
    Blacksmith's, Death Knight's, Paladin's and Scorpion's throws, and was dead 1.2-3.2s after
    each. A dash stops at the cursor, so it is aimed past the runner: aimed at them, the Rage
    Charge covered 8 of its 10 cells and left the Berserker 4 cells back, out of its axe's reach.
  - A primary never holds (no stun, freeze or root): it fires twice a second, and each hold grants
    1.5s of CC immunity, which turns the kit's own holds away. A slow grants none.
- **Regen doesn't work against it:** 2% of the character's own max health a second
  (`Constants.REGEN_SHARE_PER_SEC`), so everyone takes 50s to come back from nothing, and none
  while a burn or a poison lasts. It used to be `3.0 - (max - 70) * 0.04` a second, which healed
  the frailest fastest and nobody at all from 145 HP.
- **Bots play their role** (`BotController.moveSmart`): a ranged bot keeps its distance and backs
  off, a melee bot closes in, and a skirmisher closes to half its throw and circles there without
  backing off. A ranged bot strafes at mid range rather than walking straight away, so a melee
  bot still catches one now and then. A phase doubles a bot's pace as it does a player's; a melee
  or skirmisher bot casts one to close on a target out of its reach (the Vampire's Mist Form), a
  ranged bot only to get away from someone within 5 cells.
- **Shown** under the name on the character select screen ("Melee · 145 HP · Slow",
  `CharacterSelectionPanel.roleLine`) and on the website's cards, which a tool writes from the
  roster (see *Website*). The grid's "Melee" and "Ranged" tabs are the older playstyle
  categories, not the role.
- A few roles read against their character's fantasy. The Magma Knight and the Valkyrie are
  ranged, and so kite. Ember, a tiny fire sprite, is a skirmisher with bruiser health. They follow
  the rule until their kits change.

## Cast Behaviors
Each ability uses one of these cast behaviors (defined in `CharacterDef.scala`):
- `StandardProjectile` — fires a projectile toward the cursor
- `PhaseShiftBuff(durationMs)` — grants a temporary buff (e.g., ethereal form)
- `DashBuff(maxDistance, durationMs, moveRateMs)` — dash movement ability
- `TeleportCast(maxDistance)` — instant teleport to cursor position
- `FanProjectile(count, fanAngle)` — fires multiple projectiles in a fan pattern
- `GroundSlam(radius)` — AoE ground slam around the caster
- `BarrierCast(durationMs)` — raises a barrier in front of the caster (see *Barriers*); fires
  nothing, so its ability's projectile type is -3 (buffs and dashes carry -1, teleports -2)
- `TrapCast(trapType, maxRange)` — throws a trap onto the ground (see *Traps*); fires nothing
  either, so its ability's projectile type is -4

## Movement speed
`CharacterDef.moveSpeed` multiplies the base walking rate of 20 cells a second
(`Constants.MOVE_RATE_LIMIT_MS`, 50ms a cell). Each character's is its role's pace
(`CombatRole.speed`, see *Roles, health and pace*): 1.0 ranged, 0.97 skirmisher, 0.94 melee.

`common/model/Movement.scala` holds the one rule that turns it into a step interval, and
everything that moves a player or checks a move reads it: `GLKeyboardHandler` and
`ControllerHandler` (through `GameClient.moveStepIntervalMs`; the isometric "pure left/right
takes twice the delay" rule stays in the handlers), `BotController` (bots step at twice a
player's interval for the same character and state), and `PacketValidator.maxCellsIn`, whose
tolerance is twice that character's own rate plus two cells of grace. The two input handlers
each used to carry their own copy of the numbers.

Everything acting on a player is a factor on their interval, not an absolute: charging drags a
step out to ten times its length at full, a phase halves it, a speed boost takes it to 60%, and
**a slow divides by its own multiplier** — a `Slow(_, 0.3f)` really is 30% of their pace. Only
the "slowed" bit used to cross the wire, so every slow was a flat half whatever its def said;
`PlayerUpdatePacket` byte [52] now carries the strength with it. `MovementTest` pins the
arithmetic, and that at a speed of 1.0 it is exactly what the handlers computed before.

**A step's wait counts from when the last step fell due, not from the frame that took it**
(`Movement.nextStepFrom`). The input handlers step on 60 fps frames and the bots on a 100ms tick,
and counting from the frame rounded every interval up to whole frames. That turned the roles'
few-percent gap into a quarter: a melee character's 53ms step waited for the fourth frame and
walked at 67ms, to a ranged character's 50ms. A 25ms phase walked at 33ms. A 50ms step lost a
whole frame whenever the third came a millisecond early, about one step in eight at ±3ms of frame
jitter. And a bot's 104ms or 106ms step waited for the second tick after its last, at half its
pace. Now the handlers carry the remainder (a step a whole interval late is a fresh start, so
nothing builds up while the keys are up), and a bot takes up to two steps a tick (a speed boost
brings a step under the tick). `MovementTest` and `BotMovementTest` pin it.

## Adding a new character

1. Add a `CharacterId` entry in `CharacterId.scala` (next available ID byte).
2. Define `ProjectileDef`s for the projectiles it is the first to throw in its category's file in
   `common/model/roster/`, in that file's `projectiles`. A variant of an existing type (another
   type's numbers and look, with effects of its own) goes in the file's `variants(base)`, copied
   from `base(type)`: `CharacterDef` registers every category's definitions first and then the
   variants (`Roster.projectiles`, `Roster.variants`), so a variant finds every type it copies.
   A definition that no character throws any more goes in `Retired.scala`.
3. Add its `CharacterDef` to the category file's `characters`, with abilities, stats and sprite
   sheet path. Give it the `role` its primary's reach reads as (`CombatRole.forRange`), a
   `maxHealth` in that role's band and a `moveSpeed` of the role's pace (`Melee.speed`,
   `Skirmisher.speed`; ranged keeps the default 1.0). `RosterBalanceTest` checks all three.
   `Roster.characters` gathers the categories in id order, which `CharacterDef.all` and the grid
   follow.
4. Generate its sprite sheet — either a dedicated script, `scripts/generate_<name>.py` (on
   `sprite_base.py`), or an entry in `scripts/generate_all_new_characters.py` (see scripts/SPRITES.md) —
   and run it to produce `sprites/<name>.png`.
5. Add its card to `docs/index.html` and run `bazel run //src/main/scala/com/gridgame/tools:gendocs`
   to write its role and health; add its ability names and descriptions to
   `i18n/messages_en.json` (`ContentCatalogTest` enforces it; `tools:gencontent` regenerates them).
6. If an attack of its sets off a blast (`aoeOnHit`, `aoeOnMaxRange`, `explosionConfig`), give it
   its own look in `GLBlastRenderers` (see client/render/blasts); `BlastStyleTest` fails until it
   has one. Its sounds: client/audio.
