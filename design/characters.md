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
- **Health is set within its role's band.** It was first mapped from the old values (ranged
  `60 + (old - 65) * 20/60`, melee and skirmishers `105 + (old - 65) * 45/65`, rounded to 5), with
  the glass cannons (Wizard, Railgunner, Astronomer, Cleric, Pilot) at the ranged floor, where they
  still are. The balance pass of 2026-09-28 then moved it as one of its levers, by at most 15 for a
  fighter and 10 for a ranged character (*Balance in free-for-alls against bots*). The toughest
  ranged character (80) stays at least 20 below the frailest fighter (105). `RosterBalanceTest`
  pins the bands, that gap and the order of the paces.
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
  - **Round walls** (`BotNavigation`, searching with `BotPaths`): a bot walks straight at its
    target while the straight way there is open, and otherwise along the shortest way round, found
    by an A* search of the whole map and kept until the target moves off its end; either way it
    steps diagonally only between open cells, as a player does (*A diagonal step goes between open
    cells only*, below). A ranged bot or a
    skirmisher with no clear shot (its primary would hit a wall) goes to the nearest cell it has
    one from, at its range, rather than circling behind the wall, and circling, it turns back where
    its shot would be blocked. Backing off with a wall behind it, it takes the way that gets it
    furthest without passing the target; cornered, it circles out. Someone it can't get to (phased
    inside a wall, fenced in, or with other players on every side) it goes as near to as it can
    and waits there, looking for a way again only now and then: searched for on every step, eight
    bots after someone in the Colony's wall took 6ms a tick.
  - Bots used to walk straight at the target and search only when a step was blocked, 600 cells
    at most: the next straight step undid the way round, and they paced two cells at the wall for
    good, or stood there when the target was further off than the search reached. In matches of 12
    bots, three minutes on each map, that was 8.5% of their time stuck in free-for-alls (42% on the
    Colony, where targets are far apart) and 6.4% in Teams, and none now, with a quarter to a third
    more kills a minute. The
    balance passes of 2026-09-28 (*Balance in free-for-alls against bots*) were measured with the
    old walking. `BotPathingTest` walks bots round walls on every map.
- **Shown** under the name on the character select screen ("Melee · 145 HP · Slow",
  `CharacterSelectionPanel.roleLine`) and on the website's cards, which a tool writes from the
  roster (see *Website*). The grid's "Melee" and "Ranged" tabs are the older playstyle
  categories, not the role.
- A few roles read against their character's fantasy. The Magma Knight and the Valkyrie are
  ranged, and so kite. Ember, a tiny fire sprite, is a skirmisher with bruiser health. They follow
  the rule until their kits change.

## Balance in free-for-alls against bots
Until 2026-09-28 the match database couldn't show it: `match_results` kept only the humans' rows,
without their characters. It keeps every player's character now, and the bots' rows (`is_bot`), so
real matches can be read (see server/CLAUDE.md):

```sql
SELECT character_id, COUNT(*) AS played, SUM(rank = 1) AS won, SUM(kills) * 1.0 / SUM(deaths) AS kd
FROM match_results JOIN matches USING (match_id)
WHERE match_type = 0 AND character_id IS NOT NULL GROUP BY character_id ORDER BY kd DESC;
```

Before that, balance was measured in simulated free-for-alls of bots on the server's own code
(`GameInstance`, `BotController`), run headless at real speed: five minutes each on the Cell, the
Colony, the Meadow and the Lagoon, every kill and every point of damage booked to the attack that
dealt it. A free-for-all is ranked on kills, so what was steered is each character's mean finishing
place, and with it the share of its matches it wins.

- **Bots don't play the roles the same way.** A melee bot closes in; a ranged bot keeps to 60% of its
  reach and backs off, but strafes rather than kites, and lands 44% of its primaries to a melee bot's
  85%. So in a mixed free-for-all the fighters (melee and skirmishers, a third of the roster) take
  nearly every first place: 237 of 241 in the first measurement, and leading the bots' aim didn't
  change it. The role numbers are set for a player who kites (*Roles, health and pace*), and were
  left as they are. Each character is compared only with those it's fairly matched against:
  - a **ranged** character against the other ranged ones, in free-for-alls of 12-13 ranged bots;
  - a **fighter** by its place among the fighters of ordinary mixed free-for-alls of 14 (about five
    of them a match), since every fighter bot closes in the same way. A melee-only pool was tried
    first and dropped: its rankings didn't carry over to mixed matches (r = -0.03), where a fighter
    has to walk in through ranged fire and its health counts for more.
- **The first pass (2026-09-28)** tuned the outliers of the mixed free-for-all: the Barbarian (145 HP,
  Triple Axe 12s, Earthsplitter 25), the Berserker (145 HP, Axe Spin 33), Ember (Ember Fan 4s), the
  Cyclops (Thrown Boulder 38); and made the Grenade go off on the first enemy it reaches, not only at
  the end of its 12 cells. The same characters kept winning.
- **The second pass (2026-09-28) tuned every character**, in twelve rounds of simulation of 300-600
  matches each. Each round moved every character's damage (all its projectiles' numbers: the hit,
  burns and poisons, splashes, blasts, distance scaling), its health and its abilities' cooldowns
  toward the middle of its pool, by a least-squares fit of how far each moves its place (measured:
  10% more damage is 13% more kills a minute; health is worth 0.86 of damage). A projectile several
  characters throw has one number for all of them. Health moved at most 15 for a fighter and 10 for a
  ranged character, inside the band, so no character's toughness turned into another's. Four attacks
  that were shared by characters at opposite ends became types of their own, looking and sounding as
  their base does: the Bear's Maul (`MAUL`), the Gladiator's Arena Axe (`ARENA_AXE`, the Berserker
  and the Barbarian throw the `AXE`), the Assassin's Throwing Star (`THROWING_STAR`, the Rogue throws
  the `SHURIKEN`) and the Mantis' scythe (`MANTIS_SCYTHE`).

  Measured on the built code over the same line-ups before and after:

  | | Win share spread (sd) | Luck alone | Best record |
  |---|---|---|---|
  | Ranged, against each other (180 matches) | 15.4 → 4.7 points | 5.0 | 80% → 23% (Railgunner, now in the middle) |
  | Fighters in mixed matches (280) | 21.6 → 8.4 points | 7.0 | 80% → 43% (Berserker 80% → 23%) |

  A fair share is 8% of its matches for a ranged character against ranged ones, and about 22% for a
  fighter in a mixed match. Before: the Berserker won 80%, the Barbarian 66%, the Rogue 63%, the
  Minotaur 60%, Ember 46%, and four fighters none; the Railgunner won 80% of the ranged ones, the
  Photon 60%, the Valkyrie 53%, and 43 ranged characters none. What is left is mostly luck: a
  character's mean place now spreads 1.2 (fighters) and 1.4 (ranged) times as much as luck alone
  would spread it, where it was 4 and 6 times.
- **What moved, and why.** Reach decides a fight between ranged bots: whoever reaches further
  shoots first and backs off. The long reachers keep their reach and lose damage: the Railgunner's
  Sniper Beam 5-80 → 2-34 by distance, the Photon's Laser Heavy 22 → 11, the Pilot's Bullet Heavy
  24 → 14, the Ranger's Arrow Heavy 22 → 12, the Cleric's Holy Bolt Heavy 24 → 15, the Wizard's
  Arcane Bolt 18 → 11, the Valkyrie's Spear 10-60 → 6-38. Short reach and slow shots gain it: Sand
  Shot 16 → 27, Plague Bolt 14 → 22, Thorn 18 → 25, Magma Ball 15 → 22, the Spaceman's shot 10 → 17
  (a full charge still 100). Among the fighters the brawlers who won most lose damage and health (the
  Berserker 135 → 120 HP and Axe 33 → 24, the Barbarian's Axe 33 → 24, the Minotaur 150 → 135 HP
  and Horn 33 → 26, the Blacksmith 145 → 130 HP and Hammer 33 → 27), and those who never won gain
  them (the Shapeshifter 130 → 145 HP and Fist 22 → 28, the Death Knight, the Scorpion, the Chef,
  the Crusader, the Assassin, the Mantis). Every character's numbers are in its category file.
- **What bots can't tell:**
  - Ember and the Spaceman were left weaker than the middle against bots (46% → 6% of its mixed
    matches for Ember): Ember's 10-cell shots and phase, and the Spaceman's charged shot, are a
    player's strength, which bots don't use.
  - A player fires a primary every 0.5s, a bot every 0.7-1.1s, so a primary counts for more in a
    player's hands than here, and an ability for less.
  - Bots walk at half a player's pace, so a slow shot lands on them more often than on a player: the
    boulders and the slow ranged shots read stronger here than they will against people.
  - A bot raises a barrier (Gladiator, Paladin, Crusader, Beetle, Golem) only against ranged shots.
  - In a free-for-all of fighters alone, with no ranged characters in it, reach still wins: the
    skirmishers take most of them (the Cyclops 48%). It was balanced for mixed matches.

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

**A diagonal step goes between open cells only** (`WorldData.canStep`, `cornersOpen`). One of W, A,
S or D alone steps diagonally across the world, and a step used to need only its own cell open: past
a wall's corner the player's sprite stood in the wall for half the step, on every screen, and
between two blocks touching at a corner it went through a wall that looks whole. A diagonal step now
needs both cells beside it open too, and one that hasn't them slides along whichever side is open,
as a step into a wall always did. It is one rule wherever a player walks: the client's steps
(`LocalMovement.movePlayer`), the server's check of a single step (`PacketValidator`; of a few
bunched into one update it knows the ends, not the way), the bots' steps and every search for a
way (`BotNavigation`, `BotPaths`), and pushes and pulls (`Teleport.slide`), which are drawn sliding
over the ground. A blink is a jump and may still pass a corner, and a phase walks through walls,
corners and all. `WorldDataTest`, `GameClientPositionTest`, `PacketValidatorTest` and
`KnockbackTest` pin it, and `BotPathingTest` checks every step its bots take on every map.

**A step glides over the time until the next one** (`LocalMovement.movePlayer`): we are drawn
sliding from the cell we left to the one we stepped onto over the gap since the step before, which
is the pace while a key is held. A step after standing still has no such gap and glides over its
own interval (twice it for a pure left or right): it used to glide over the whole time stood still,
so a tap after a few seconds slid one cell over those seconds, the camera with it.
`GameClientPositionTest` pins it.

## Adding a new character

1. Add a `CharacterId` entry in `CharacterId.scala` (next available ID byte).
2. Define `ProjectileDef`s for the projectiles it is the first to throw in its category's file in
   `common/model/roster/`, in that file's `projectiles`. A variant of an existing type (another
   type's flight, reach and look, with effects, and if need be a damage, of its own:
   `MeleeKitTest`) goes in the file's `variants(base)`, copied
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
