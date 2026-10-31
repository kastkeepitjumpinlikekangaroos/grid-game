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
character can kite a melee one (walk away, shooting back) for as long as both only walk, and hits
harder, to make up for the health it hasn't got (*Damage makes up for health*, below). A melee
character closes the gap with its abilities or an item, not its feet.

| Role | Primary reach | Characters | Health | `moveSpeed` | Step |
|---|---|---|---|---|---|
| `Ranged` | 12 or more | 75 | 60-80 | 1.0 | 50ms, 20 cells/s |
| `Skirmisher` | 7-10 | 7 | 105-150 | 0.97 | 52ms, 19.2 cells/s |
| `Melee` | 6 cells or less | 30 | 105-150 | 0.94 | 53ms, 18.9 cells/s |

- **The role is named on each character, not worked out.** `CombatRole.forRange` is the rule
  (reach measured fully charged), and it has no exceptions now: the Monk was one, its 6-cell punch
  reaching 10 charged, until the melee primaries were halved (*Melee strikes short and hard*).
  `RosterBalanceTest` checks every character against the rule and its list of exceptions.
- **Skirmishers** are the boulder throwers (Earthshaker, Avalanche, Beetle, Gorilla, Golem,
  Cyclops) plus Ember: melee health, a pace between the two.
- **Health is set within its role's band.** It was first mapped from the old values (ranged
  `60 + (old - 65) * 20/60`, melee and skirmishers `105 + (old - 65) * 45/65`, rounded to 5), with
  the glass cannons (Wizard, Railgunner, Astronomer, Cleric, Pilot) at the ranged floor, where they
  still are. The balance pass of 2026-09-28 then moved it as one of its levers, by at most 15 for a
  fighter and 10 for a ranged character, and the third took 5 from the Griffin (*Balance in
  free-for-alls against bots*). The toughest
  ranged character (80) stays at least 20 below the frailest fighter (105). `RosterBalanceTest`
  pins the bands, that gap and the order of the paces.
- **Damage makes up for health.** Since the balance pass of 2026-10-12 a ranged character's attacks
  deal on average 1.78 times what they did (1.38 to 2.05 from one character to the next), and a
  fighter's what they did: in matches of bots, where nobody kites, that is what puts the two roles
  level (*Balance in free-for-alls against bots*, the third pass). Before it a ranged character won
  none of its mixed free-for-alls to a fighter's 22% (a fair share is 7%) and traded kills at 0.75
  to their 1.49, and in Teams every ranged character a team had more than the other cost it 18
  points of its chance to win. A player who kites, which bots don't, gets more out of a ranged
  character than that. Since 2026-10-15 a melee character's primary deals 1.75 times what it did,
  for half its range, as the melee characters were weak in players' hands (*Melee strikes short and
  hard*).
  - **No uncharged hit deals more than 55**, less than the frailest character's health (60), so
    nobody at full health dies to a single blow; a full charge, a player's skill shot, still may.
    Where the multiplier would have taken a hit past it (the Rocket, the Grenade, the Fireball,
    the Eruption, the Inferno Blast, the Cluster Bomb, the Gravity Well, the far end of the Spear,
    the Sniper Beam, the Star Bolt and the Gravity Lance, the Mine, and of the melee primaries the
    Talon and the Shovel) the hit stops at 55, and a blast's edge rose instead, to at most three
    quarters of its centre. That edge is most of what the blast does to whoever set it off: a shot
    that goes off on reaching someone does so a cell and a half or so from them, so the Grenade that
    went off on its target for about 29 now deals about 48. `RosterBalanceTest` holds every attack
    and trap to both rules.
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
  ranged bot only to get away from someone within 5 cells, and so with a blink, a dash and a haste
  (*No volleys of more than three*).
  - **Round walls** (`BotNavigation`, searching with `BotPaths`): a bot walks straight at its
    target while the straight way there is open, and otherwise along the shortest way round, found
    by an A* search of the whole map and kept until the target moves off its end; either way it
    steps diagonally only between open cells, as a player does (*A diagonal step goes between open
    cells only*, below). A ranged bot or a
    skirmisher with no clear shot (its primary would hit a wall) goes to the nearest cell it has
    one from, at its range, rather than circling behind the wall, and circling, it turns back where
    its shot would be blocked. Backing off with a wall behind it, it takes the way that gets it
    furthest without passing the target; cornered, it circles out. Someone it can't get to (dashing
    over a wall, where a fence went up on them, fenced in, or with other players on every side) it
    goes as near to as it can
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

Balance is measured in simulated matches of bots on the server's own code (`GameInstance`,
`BotController`), run headless at real speed: five minutes each, on the Cell, the Colony, the Meadow
and the Lagoon for the first two passes and on all seven maps for the third, every kill and every
point of damage booked to the attack that dealt it. A free-for-all was ranked on kills when these
passes were made, so what was steered is each character's finishing place (the third pass by way
of its kills a minute, which decided it), and with it the share of its matches it wins. Since
2026-10-14 a match is won on kills less deaths (design/match-flow.md, *Who wins*), and a
character's deaths count against it as much as its kills for it, which none of the passes weighed:
one that trades a death for every kill now finishes in the middle, however many it trades. The
next pass should steer the score.

- **Bots don't play the roles the same way.** A melee bot closes in; a ranged bot keeps to 60% of its
  reach and backs off, but strafes rather than kites, and lands 44% of its primaries to a melee bot's
  85%. So in a mixed free-for-all the fighters (melee and skirmishers, a third of the roster) take
  nearly every first place: 237 of 241 in the first measurement, and leading the bots' aim didn't
  change it. The first two passes left the role numbers as they were, set for a player who kites
  (*Roles, health and pace*); the third made up the difference in damage (*Damage makes up for
  health*). Each character is compared only with those it's fairly matched against:
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
- **The third pass (2026-10-12)** was for the ranged characters, still underpowered: it raised
  their damage until the two roles were level in bot matches, then balanced each role's characters
  among themselves again, on damage alone (health and cooldowns are as they were, but for the
  Griffin's: *What is left*). Everything was measured afresh, as the bots' walking had been rebuilt
  since the second pass (*Round walls*: a quarter to a third more kills a minute, and ranged bots
  that go looking for a clear shot), and so had where a shot strikes (never through a wall, and the
  nearest of those it reaches: design/combat.md).
  - *The roles.* Mixed free-for-alls with every ranged character's damage multiplied by 1.25 to 2
    put the roles level at about 1.78. The ranged characters' average was held there, and the
    fighters' at 1, while the numbers moved underneath.
  - *Within each role*, eight rounds of 360-480 matches in each pool (the ranged against each other,
    a fighter by its place among the fighters of mixed matches, as before), each moving every
    projectile's and trap's damage numbers by a least-squares fit of how far each moves its
    throwers' kills a minute. At the new damage, 10% more of it is about 13% more kills, for either
    role (it was 12% for a ranged character and 11% for a fighter, measured by giving every attack a
    random 20% more or less). The damage itself reordered the ranged characters: slows and holds
    count for less when a target dies in three hits instead of five, and reach and piercing for
    more, so standings measured at the old damage foretold the new ones poorly, and only matches
    played at the new damage were used. A pull of every attack toward the roles' multiplier kept the
    fit from loading a character's whole correction onto a small ability of its own (a 12-damage
    push was heading for 49), and shared projectiles move for all their throwers, which is what
    leaves the last few points (*What is left*).
  - Measured on the built code, over the same line-ups before and after:

    | | Before | After |
    |---|---|---|
    | Mixed free-for-alls (480): first places, ranged / fighters (a fair share is 7.1%) | 0.0% / 21.8% | 7.9% / 6.0% |
    | the same: kills per death, ranged / fighters | 0.75 / 1.49 | 1.01 / 0.97 |
    | the same: the ranged characters' share of the kills (they are 67% of the players) | 50% | 66% |
    | Teams (320): a team's chance to win, for each ranged character more than the other team | -18 points | +2 (within noise) |
    | Ranged against ranged (480): win share spread, sd (luck alone: 3.0) | 4.4 points | 3.2 points |
    | Fighters in mixed matches (480): win share spread, sd (luck alone: 5.3) | 9.0 points | 5.8 points |

  - *What moved.* The ranged characters who gained least (×1.38 to 1.52) are those whose biggest
    hits met the cap, who were also among the strongest: the Bombardier (Grenade blast 47-12 →
    55-41), the Astronomer (Star Bolt 7-41 → 17-55 by distance), the Railgunner (Sniper Beam 2-34 →
    3-50) and the Valkyrie (Spear 6-38 → 12-55). Those who gained most (×1.95 to 2.05) were the
    weakest: the four who throw the Death Bolt (13 → 28: the Necromancer, the Shadowfiend, the
    Warlock, Anubis), the Chameleon (Poison Dart 21 → 42), the Bard and the Musician (Sonic Wave 14
    → 27), the Inferno and the Harpy (Sonic Wave Med 18 → 37). Most primaries took about the 1.78:
    Bullet 16 → 29, Sand Shot 27 → 47, Frost Shard 17 → 30, Wind Blade 18 → 34, Charm 19 → 36. The
    six attacks both roles throw (the Gust, the Rope, the Tremor Slam, the Seismic Root, the Seismic
    Slam, the Shadow Haunt) took no part in the multiplier and moved only with the fit (the Gust 10
    → 7, the Shadow Haunt 25 → 31). The Mine went 45-15 → 55-27 and the Fire Rune's burn 40 → 71;
    the Bear Trap, which fighters lay too, is still 10. The fighters moved ×0.85 to 1.16: the
    Vampire most (Blood Fang 25 → 30, from 2% of its mixed matches won to 25-32%), the Rogue least
    (Shuriken 19 → 16), and the boulder throwers a little down (Boulder 30 → 27).
  - *What is left.* The Griffin, whose Talon the Raptor and the Hawk throw too, came out on top
    of the fighters, and its damage couldn't come down without theirs: its health did instead,
    120 → 115, which took it from 39% of its mixed matches won to 22% (a fair share). Otherwise a
    character or two in each pool is a few points of kills a minute from the middle, its attacks
    shared with others on the other side of it (the Magma Knight, whose Magma Ball the Cerberus
    throws); a variant of its own, as the second pass made four, would free it.
- **The volleys' replacements (2026-10-14)** were fitted back to the scores their characters had,
  on kills less deaths, at the bots' pace and at a player's: *No volleys of more than three*, *What
  the volleys dealt was given back*.
- **The melee primaries (2026-10-15)** were halved in range and given 1.75 times the damage for
  players, not fitted to bots: *Melee strikes short and hard*. It took the skirmishers down about 3
  kills less deaths a match, which nothing has made up yet.
- **What bots can't tell:**
  - Ember and the Spaceman are left weaker than the middle against bots (46% → 6% of its mixed
    matches for Ember in the second pass; the third aimed them at 8% and 5% fewer kills a minute
    than the middle of their pools): Ember's 10-cell shots and phase, and the Spaceman's charged
    shot, are a player's strength, which bots don't use.
  - A player kites, walking away and shooting back; a bot strafes. The roles are level among
    bots, so in a player's hands a ranged character is the stronger of the two, which its pace
    was there for in the first place (*Roles, health and pace*).
  - A player fires a primary every 0.5s, a bot every 0.7-1.1s, so a primary counts for more in a
    player's hands than here, and an ability for less. Bots firing as often as a player (a
    simulation's setting, not the game's) put the ranged characters 1.2 kills less deaths a match
    above their score at the bots' pace and the fighters 2.4 below, on the kits before 2026-10-14
    (1.6 and 3.2 on the kits since).
  - Bots walk at half a player's pace, so a slow shot lands on them more often than on a player: the
    boulders and the slow ranged shots read stronger here than they will against people.
  - A bot raises a barrier (Gladiator, Paladin, Crusader, Beetle, Golem) only against ranged shots.
  - In a free-for-all of fighters alone, with no ranged characters in it, reach still wins: the
    skirmishers take most of them (the Cyclops 48%). It was balanced for mixed matches.

## No volleys of more than three
A volley was the easy ability to give a character, and too many had one: fifty abilities of 47
characters fired a fan, 33 of them five or eight at once (fourteen were rings of eight), and kits
ran together, the Pyromancer's fan of five flame bolts much the Glacier's of five frost shards and
the Photon's of five lasers. Since 2026-10-14 no volley is more than three
(`FanProjectile.count`), and one is left only where firing several at once is what the character
is; the rest became crowd control, hastes, blinks and the like, each out of the character's own
fantasy. `KitTest` holds the cap, the list of volleys left, and that no two kits are alike.

- **The volleys left**, each three at most: the Warden's chains flung wide, the Vampire's bats, Ember's
  scattered embers, the Ranger's multishot, the Monk's flurry of blows (three, gone once they land:
  the ring of eight came back like the fists it was made from), the Rogue's knives, the Jester's
  hand of cards, the Photon's beam split by a prism, the Pilot's strafing guns, the Hydra's three
  heads and Cerberus's three.
- **One wave or one cut where the fan stood for one.** The Tidecaller's Tidal Wave is one great wave
  now, 2.4 cells either side, rolling on through the first three it strikes and pushing each back
  once; the Samurai's Whirlwind is the Crescent Cut, one wide cut that goes on through everyone in
  its way and stuns each. Both are drawn as wide as they strike (their renderers scale with
  `hitRadius`). The Cyclops hurls one huge boulder.
- **The melee rings became slams** where they were a swing all round (the Berserker's Axe Spin, the
  Bear's Maul, Fenrir's Blood Frenzy), with the effects they had, and a lance where they reached
  far (the Paladin's Judgment, the Skeleton King's Bone Cage).
- **What the rest became:**

  | | Crowd control | A haste, a blink, a dash, a phase | Something else |
  |---|---|---|---|
  | Elementals | Frost Nova (Cryomancer), Frostbite, Hailstorm (Cloudrunner), Gale (Windwalker) | Combust (Pyromancer), Bog Hop (Mudslinger), Wind Form (Windwalker) | Ice Wall, a barrier (Glacier) |
  | Undead | Bone Cage (Skeleton King), Shadow Bind (Shade), Fling (Poltergeist) | | |
  | Medieval | Judgment (Paladin), Lullaby (Bard), Divine Judgment (Cleric), Pinning Spear (Valkyrie), Shackles (Inquisitor), Death Coil (Warlock) | Axe Spin (Berserker), Leap (Barbarian) | |
  | Sci-fi | Ion Shot (Android), Repulse (Graviton), Lag Spike (Glitcher) | Jump Jets (Mech Pilot), Disperse (Nanoswarm), Light Speed (Photon) | |
  | Beasts | Pin Down (Hawk), Electric Burst (Jellyfish), Maul (Bear) | Burrow (Spider), Ink Cloud (Kraken) | Web Trap, a snare (Spider) |
  | Myth | Riddle (Sphinx), Binding Talisman (Yokai), Blood Frenzy (Fenrir), Serpent Strike (Chimera), Whirlwind (Djinn) | Pounce (Sphinx) | |
  | Specialists | Marionette (Puppeteer), Wild Card (Gambler), Meteor (Astronomer) | Lucky Streak (Gambler) | Binding Rune, a trap (Runesmith) |

  The Warlock, the Astronomer, the Poltergeist and the Djinn had no volley, but had kits that were
  someone else's: three a fireball and a blink, three a charm and a blink. Each has an attack of its
  own now, and the Wizard and the Enchantress keep theirs. The Beetle's kit was the Golem's to the
  letter (a barrier and the same Tremor Slam), which `KitTest` found: its E is a Horn Flip, a slam
  that throws back and stuns. Every changed character's description says what its kit now does.
- **A haste is a ground slam that speeds its caster up** (its projectile carries a `SpeedBoost`), cast
  as the Cyborg's Overclock always was: the server boosts the caster as it goes down, whoever is in it
  (`GameInstance.applyCastSelfBuff`), and goes off as its key goes down, as every slam does (*Casting
  mode*). Two are nothing else (the Overclock, the Gambler's Lucky Streak); two also burn or slow
  whoever is near (the Pyromancer's Combust, the Berserker's Axe Spin); the Windwalker's Gale hastes
  her when it lands.
  A boost is 40% quicker steps (`Movement`). It never used to last as long as it said for the player
  who had it: only the flag crossed the wire, their client took it as a second's boost, and a player
  at full health hears no update between a cast and the next hit, so the Cyborg's 4s Overclock was
  walked for one. Every update now carries how long the boost has left (`PlayerUpdatePacket` bytes
  [59-60]), the caster is told over TCP as well as by datagram, and a haste of our own is walked from
  its cast, as a phase is, while an update the server sent before it heard of the cast is ignored
  for a round trip (`LocalPlayer.hasteCast`). `GameClientStatusTest`, `KitReworkTest`.
- **Bots use them for what they are** (`BotCombat`): a fighter's dash or blink takes it in on a target
  out of reach, a ranged bot's blink or dash takes it away from one within 5 cells, as its phase
  already did (it used to blink a ranged bot into whoever it was fighting), and only if it would
  carry it 3 cells or more. A haste that is nothing else is cast by a ranged bot to get away and by a
  fighter to run a target down; one that also hurts, with its target in it. `BotMovementTest`.
- **Every new attack lands in a match** as it says, slams, splashes, shots that go on through or off
  the walls, the traps and the ice wall: `KitReworkTest`.
- **What the volleys dealt was given back.** A volley was up to a third of what its thrower dealt
  (the Spider's, the Nanoswarm's, the Gambler's, the Mudslinger's), so the reworked characters came
  out weaker: in mixed free-for-alls of bots (*Balance in free-for-alls against bots*), 960 on the
  line-ups the old kits played, they fell 2.3 kills less deaths a match on average (spread 3.2),
  twelve of them by 4 to 9.5, and everyone else rose 1.6. The bots' getaways (above) lifted the ranged
  characters with a blink or a dash 2 more, the Wizard 7. Rounds of 960 matches then fitted the
  damage of these characters' attacks, and the health of those whose main attack someone untouched
  throws too or had gone as far as it could, by least squares, as the balance passes did, aiming each
  back at its old score, or nearer the middle where the rework had moved it toward the middle. 10% more damage
  turned out to be 2.3 kills less deaths a match, not the 4 the fit first assumed. Measured on the
  built code over the same line-ups, the reworked characters are 0.4 below their old scores at the
  bots' pace and 0.1 above at a player's (spread 1.2 and 1.3; luck alone spreads 120 matches about
  0.9), the rest of the roster 0.3 above and 0.1 below.
  - *The bots' pace and a player's.* A bot fires its primary every 0.7-1.1s, a player every 0.5s
    (*What bots can't tell*), and this moved damage out of abilities into primaries, which a player's
    pace rewards. Fitted to the bots' pace alone, the reworked ranged characters came out 1.1 above
    their old scores with the bots firing as often as a player (the Gambler, the Spider and the Shade
    3-4), so the last step aimed at the mean of the two paces.
  - *Primaries, for whoever lost a volley*: the Spider's Web Shot 24 → 34, the Nanoswarm's Nano Bolt
    23 → 33, the Mudslinger's Mud Glob 33 → 46, the Runesmith's Rune Bolt 24 → 33, the Graviton's
    Gravity Ball 37 → 45, the Axe 23 → 28 (the Berserker and the Barbarian), Fenrir's claw 23 → 29,
    the Cleric's Holy Bolt Heavy 24 → 29, the Flame Bolt 18 → 23, the Laser 23 → 26, and a few points
    for a dozen more. The Gambler's card and the Puppeteer's chain bolt, which the Jester and the
    Warden throw too, are types of their own now, looking and sounding as their bases do
    (`CARD_HEAVY` 33, `CHAIN_BOLT_HEAVY` 40). The replacements themselves moved little: the Maul 26 →
    33, the Wild Card and the Marionette 24 and 20 → 31 and 26, the Frost Nova 18 → 23, the Combust
    14 → 18; the Axe Spin 22 → 17 and the Death Coil 30 → 23 came down.
  - *Health, where the main attack is shared or went as far as it could*: the Bard 60 → 70, the Puppeteer 65 → 75, the Sphinx 60
    → 70, the Bear 130 → 145, the Windwalker 60 → 65, the Cryomancer, the Nanoswarm and the Mech Pilot
    70 → 75, the Mudslinger 75 → 80; each within the 10 (15 for a fighter) a pass may move it.
  - *Untouched characters* moved only where the getaways took them past the middle, or where they
    throw an attack that moved: the Wizard's uncharged Arcane Bolt 15 → 12 (a full charge is still
    40), the Centaur's Arrow Light 30 → 28, the Voidwalker's Void Bolt 30 → 26, the Serpent 75 → 70
    HP; the Magma Ball 40 → 38 for the Magma Knight and Cerberus, whose Triple Blast is three of the
    Pyromancer's Flame Bolts; the Spaceman's Tentacle, the Kraken's primary, 28 → 33.
  - *What is left.* The Bard, whose Sonic Wave the Musician throws, stays 1.5 short with its health
    up its 10. The fighters as a whole come out 0.4 kills less deaths a match below where they were
    at the bots' pace and 1.2 at a player's: ranged bots now blink away from them.

## Melee strikes short and hard
The melee characters were weak in players' hands, and since 2026-10-15 each one's primary (the
thirty whose role is `Melee`, not the skirmishers') reaches half as far as it did and hits 1.75 times
as hard. Their abilities, health and pace are as they were.

- **Why.** A player fires their primary every half second, a bot every 0.7-1.1s (*What bots can't
  tell*), and in free-for-alls of bots firing as often as a player the melee characters came out 4.7
  kills less deaths a match below even and won 0.8% of them (a fair share is 7.1%), the ranged 1.9
  above. Part of that is the bots: a bot swings only at someone within the range its swing flies
  (`BotCombat.tryShoot`), and a swing strikes well past that (below), so a player, who swings at
  whoever it will strike, does better with one. With the melee bots swinging that way, the nearest
  the simulation comes to a player with a melee character, they still came out 1.6 below even, to the
  ranged 0.7 above.
- **Half the range**, a half rounded up and never under 2 cells: the claws, the talons, the katana,
  the Vampire's fang, the Shapeshifter's fist and the Gravedigger's shovel fly 2 cells (the fang flew
  3, the rest 4); the blades, the axes, the scythe, the hammer and the horn 3 (5); the knives, the
  throwing stars, the shuriken, the stinger and the Monk's punch 3 (6), and a charged punch 2 to 5 (4
  to 10). A swing starts a cell out from its thrower and strikes anyone within its hit radius of it
  (2.2 cells; the axes, the scythe, the hammer and the horn 2.5; the stinger 1.8), which is as it was,
  so it reaches a cell, its range and its radius: 5.2 cells for a claw (it was 7.2), 6.2 for a blade
  or a knife (8.2 and 9.2), 6.5 for an axe (8.5). On someone walking straight away it reaches about
  as far as it did, since it never gained much on them over its range (`MeleeKitTest.reachOnARunner`):
  3.5 cells for a claw where it was 3.8, 3.5 for an axe as before, 4.3 for a knife where it was 5.4.
  A claw is in the air 86ms, where it was 170.
- **1.75 times the damage**, a hit, a poison's total and a charged punch at both ends alike, each hit
  stopping at 55 (*No uncharged hit deals more than 55*), as the Talon's and the Shovel's 33 do: the
  Rogue's Shuriken 16 → 28, the Knife and the Stinger 21 → 37, the Cursed Blade 22 → 39, the Holy
  Blade 23 → 40, the Claw Swipe, the Scythe and the Drain Blade 24 → 42, the Ghoul's claw 26 → 46, the
  Vampire's fang, the Mantis's scythe and the Chill Blade 27 → 47, the Axe, the Bone Axe, the Hammer
  and the Horn 28 → 49, the Katana, the Fist, the Arena Axe, the Throwing Star and Fenrir's and the
  Shark's claws 29 → 51, the Talon and the Shovel 33 → 55; the Shark's poison 10 → 18 and the
  Stinger's 9 → 16; the Monk's punch 19 → 33, and charged 12-40 → 21-70 (held four seconds: a skill
  shot, which the cap leaves be). Every character's numbers are in its category file.
- **The abilities are as they were**, ways in, holds and slams alike (*Every melee and skirmisher
  kit has a way in, or is an anchor*). Three fly as a melee primary does, being copies of one, and
  keep the reach they had, which their abilities say: the Hawk's Pin Down (the Talon's, 4 cells), the
  Monk's Flurry (the Fist's, 4) and the Rogue's Knife Spray (the Shuriken's, 6). `MeleeKitTest`.
- **The Monk is no exception to the role rule any more**: its punch reached 10 cells charged, a
  skirmisher's reach, and reaches 5 (*Roles, health and pace*). `RosterBalanceTest` holds every
  melee primary to 3 cells, 5 charged.
- **The bots** swing as they did, at whoever is within the range their swing flies, now 2 or 3
  cells; a melee bot casts its way in at anyone further off than that (it waited until 4 to 6), and
  presses R only with its target that close (*R resets the cooldowns*).
- **Measured** in free-for-alls of bots (*Balance in free-for-alls against bots*), 480 on the same
  line-ups before and after, kills less deaths a match:

  | Melee / ranged / skirmishers | Before | After |
  |---|---|---|
  | At the bots' pace | −1.3 / +0.4 / +0.7 | +0.4 / +0.1 / −2.8 |
  | At a player's pace | −4.7 / +1.9 / −0.1 | −5.2 / +2.4 / −3.2 |
  | At a player's pace, melee swinging at all it will strike | −1.6 / +0.7 / −0.5 | +2.8 / −0.7 / −4.2 |

  So played as a player plays it, melee goes from 2.3 below the ranged to 3.5 above, before the
  kiting a player does and a bot doesn't takes some of that back; the melee bots themselves, swinging
  as they do, come out about where they were. On the built code over another 480 line-ups the melee
  bots moved +1.5 at their pace and −0.4 at a player's, and the skirmishers −3.6 and −3.5. 1.5 times
  the damage put melee level with the ranged at full reach and left the melee bots weaker than they
  were (−7.2 at a player's pace); twice the damage gained little on 1.75 times, most hits meeting the
  cap.
- **What is left.**
  - The skirmishers lose about 3 a match whichever way it is measured: they stand their ground at
    half their throw, where a melee character now reaches them for 28 to 55 a blow.
  - The melee characters are further apart than they were. Played at full reach their scores spread
    4.9 a match (one standard deviation) where they spread 3.5, the Gladiator, the Crusader, the
    Vampire and the Paladin near +11 and the Raptor, the Rogue, the Griffin, the Bear and the Hawk
    between −3 and −5. A fit within the role, as the balance passes made, would close it.
  - Bots that swung at all their swing will strike would make melee far stronger among bots: +7.0
    at their own pace, where the ranged come out −2.5.

## Cast Behaviors
Each ability uses one of these cast behaviors (defined in `CharacterDef.scala`):
- `StandardProjectile` — fires a projectile toward the cursor
- `PhaseShiftBuff(durationMs)` — a phase: for `durationMs` untouchable (no shot, blast or trap
  touches us, and nothing freezes, roots or slows us), at twice the pace, and unable to attack;
  walls stop it as they stop a walk (*A phase stops at walls*)
- `DashBuff(maxDistance, durationMs, moveRateMs)` — a dash: toward the aim, as far as the cursor and
  `maxDistance` cells at most, over `durationMs`, phased while it lasts, and over whatever lies
  between: it ends on the farthest open cell along its line (`LocalMovement.dashLanding`)
- `TeleportCast(maxDistance)` — a blink: its whole range toward the aim, however near the cursor
  is, stopping short of the first cell it can't land on (`Teleport.blinkTarget`)
- `FanProjectile(count, fanAngle)` — fires up to three projectiles in a fan (*No volleys of more
  than three*)
- `GroundSlam(radius)` — AoE ground slam around the caster. What it reaches is its projectile's
  splash (`AbilityDef.slamRadius`): `radius` only tells bots when to cast it, and is a cell short of
  the splash for five of the roster's slams
- `BarrierCast(durationMs)` — raises a barrier in front of the caster (see *Barriers*); fires
  nothing, so its ability's projectile type is -3 (buffs and dashes carry -1, teleports -2)
- `TrapCast(trapType, maxRange)` — throws a trap onto the ground (see *Traps*); fires nothing
  either, so its ability's projectile type is -4

## Casting mode
Q and E are aimed, then cast with a click (`Attacks`). The key takes the ability up: where it will
go is laid on the ground (client/render/CLAUDE.md, *An aimed attack shows where it will go*) and its
slot on the HUD is ringed in the same gold, while we walk and aim as ever. The next press of the left
mouse button casts it toward the aim of that moment, and is nothing more: the primary doesn't go off
as the button comes up. The key coming up does nothing. So a quick click, Q, click, E, click fires
the primary and casts both, each on its own cooldown as before.
- **What goes off as its key goes down** is what has nothing to aim (`Attacks.goesOffOnPress`): every
  slam, the Earthshaker's Seismic Root as much as the Cyborg's Overclock, since it reaches all round
  us whichever way we aim; a phase; a barrier, which turns with the aim the whole time it is up; and
  a fan that goes all the way round (none in the roster now). It leaves an ability taken up as it was.
  Everything else goes somewhere (a shot, a fan, a dash, a blink, a trap thrown) and is aimed first.
- **On its cooldown the key takes nothing up**, so a click then is the primary's, which keeps its
  half second between shots (`SHOOT_COOLDOWN_MS`). Taken up anyway, every click until the cooldown
  ran would have done nothing at all.
- **A click that can't cast it now casts nothing, spends nothing, and leaves it taken up**: held,
  phased, in a free-for-all's ceasefire, a dash or a blink while rooted, a trap with nowhere to land.
  Its aim and its rim are grey until it could go, and the click after that casts it.
- **One ability is aimed at a time**: the other's key takes over from it, and the same key again
  leaves it as it is.
- **The right mouse button puts away** whatever is aimed or held, a charge included, without it going
  off or spending a cooldown, as the chat opening and the window losing the focus do (so the click
  that brings the window back casts nothing). A new life starts with nothing aimed.
- **The primary is held to aim**: it goes off as its button comes up, toward the aim of that moment,
  and while it is held a moment where it will go is laid on the ground. A primary that charges
  charges as it always did, and the half second between two shots runs to the moment one goes off,
  so a shot held through it and let go after it goes. A shot held as Q or E goes down still goes off
  as the button comes up, the ability waiting for the next click; a click that casts an ability
  starts no charge.
- **On a pad** A and B are Q and E, the right trigger is the left mouse button and the left bumper the
  right one.

It is all the client's: the server takes a cast when it comes, and never knew when a key went down.
`GameClientAimTest` pins it, through the real keyboard and mouse handlers too. It replaced holding Q
or E down to aim and letting go to cast, under which every slam was held to aim as well.

## R resets the cooldowns
R has both abilities ready again at once (`Attacks.pressReset`), and then waits a minute of its own
(`Constants.COOLDOWN_RESET_MS`) before it can be pressed again. Every match starts with R on that
minute, so nobody has it until the fighting has gone on a while: after the thirty seconds of a
free-for-all's ceasefire or of a Teams match's wall, the first R comes half a minute after the first
shot could be fired. Practice, where a player goes to try a character out, starts with R ready
instead, so it can be tried from the first moment (`GameInstance.firstResetFrom` on the server; the
client's `Attacks.startResetCooldown` reads `isPracticeMode`); once pressed it waits its minute as
anywhere. Since 2026-10-14; no character was retuned for it, on the assumption that it helps every
kit alike, which play will tell.
- **What it resets** is Q's and E's cooldowns, whatever they cast: a shot, a fan, a slam, a dash, a
  blink, a phase, a barrier, a trap. Not the primary's half second, nor the burst shot's second.
  Whatever is aimed stays aimed. A barrier already up stays up for the rest of its time, and can't be
  raised again over itself, which its cooldown always used to see to: it can be once it is down.
- **With nothing cooling down it does nothing**, and spends nothing, as Q or E on its cooldown takes
  nothing up.
- **Whoever couldn't cast can't press it**: the dead, the held, the phased, and everyone in a
  free-for-all's ceasefire (which R's first minute outlasts anyway).
- **A death doesn't give it back**, nor a rejoin: R's minute runs on through them, while a new life
  has Q and E ready as it always has. A new match starts it on its minute again (practice, ready).
- **Its slot is beside Q's and E's on the HUD** (`AbilityBarPainter`): a violet arrow going round, its
  minute sweeping round the slot as their cooldowns do, with the seconds still to wait written over
  it, since a minute is a slow sweep, and a flash as it comes ready. The sweep, all three slots', stops
  at the slot's own edge: it was a circle wide enough to reach the corners, so it spilled a third of a
  slot over the bar and onto the slot beside it, which a minute-long cooldown would have kept on screen.
  On a pad, R is Y.
- **The server judges it as it would a cast** (`CooldownResetRequests`; the packet is
  `COOLDOWN_RESET`, common/protocol/CLAUDE.md). The client has the abilities ready from the press and
  asks over TCP. The server takes it from someone who could cast now, once R's own minute has run (but
  for the tolerance every attack gets, a fifth of it: `PacketValidator.takeCooldownReset`), counted from the
  match's start for the first (`GameInstance.firstResetFrom`, which in practice has the first taken
  at once) and from the last after that, and clears every clock it holds Q and E to: the shots' and
  the traps' (`PacketValidator`), and a phase's and a barrier's, which are the player's own. A
  refusal is said (REJECTED, with how long R still has to wait when that was why), and the client
  puts Q and E back as they were before the press, but for a cast since that the server would have
  taken anyway; R comes back when the server says, or in a moment, as a refused trap does.
- **What can still go wrong**: R goes over TCP and a shot over UDP, so a shot cast in the same moment
  as R, or while a lost R is being sent again (a round trip and a fifth of a second), can reach the
  server first and be refused, as a lost shot is, its cooldown spent. A phase or a barrier, said in
  every update while it lasts, is taken by the first after R arrives, and a trap goes over TCP
  behind it.
- **Bots press it too** (`BotCombat.tryResetCooldowns`), on the same minute: once both their abilities
  are spent, with three seconds or more still to go on each, and their target within reach of their
  primary, so the two go again at once.
- `CooldownResetTest` (the server, bots included) and `GameClientResetTest` (the client, through the
  real keyboard handler too) pin it, and `LobbyFlowTest` a real practice session's R ready at its
  start; the golden frame `meadow_reset_cooling` holds its slot.

## Movement speed
`CharacterDef.moveSpeed` multiplies the base walking rate of 20 cells a second
(`Constants.MOVE_RATE_LIMIT_MS`, 50ms a cell). Each character's is its role's pace
(`CombatRole.speed`, see *Roles, health and pace*): 1.0 ranged, 0.97 skirmisher, 0.94 melee.

`common/model/Movement.scala` holds the one rule that turns it into a step interval, and
everything that moves a player or checks a move reads it: `GLKeyboardHandler` and
`ControllerHandler` (through `GameClient.moveStepIntervalMs`; the isometric "pure left/right
takes twice the delay" rule stays in the handlers), `BotController` (bots step at twice a
player's interval for the same character and state), and `PacketValidator.creditRate`, the pace
a player's movement credit builds at: twice that character's own rate in the state they are in, and a
quarter more (design/networking.md, *Bad networks*). The two input handlers
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
over the ground. A phased step is a step like any other (*A phase stops at walls*). A blink is a
jump and may still pass a corner, unless all it reaches is the one cell past it: a jump of one cell
is a step to the server, which can't tell them apart, so that blink goes nowhere (it landed there
until 2026-10-30, and the server held its player back). A dash goes over walls, corners and all. `WorldDataTest`,
`GameClientPositionTest`, `PacketValidatorTest` and `KnockbackTest` pin it, and `BotPathingTest`
checks every step its bots take on every map.

**A step glides over the time until the next one** (`LocalMovement.movePlayer`): we are drawn
sliding from the cell we left to the one we stepped onto over the gap since the step before, which
is the pace while a key is held. A step after standing still has no such gap and glides over its
own interval (twice it for a pure left or right): it used to glide over the whole time stood still,
so a tap after a few seconds slid one cell over those seconds, the camera with it.
`GameClientPositionTest` pins it.

## A phase stops at walls
A phase (`PhaseShiftBuff`: the Wraith's Phase Shift, the Vampire's Mist Form, the Kraken's Ink Cloud
and ten more) makes its caster untouchable and twice as quick, and until 2026-10-14 it walked
through walls as well, corners and all. Being untouchable and quick is what it is for, and walls
stop it now as they stop a walk.
- **A phased step is a step** (`WorldData.canStep`): onto open ground, diagonally only between open
  cells, and sliding along a wall it is walked into. The client takes no other
  (`LocalMovement.movePlayer`), and the server takes no update that puts someone phased on a cell
  they can't stand on, nor a single diagonal step past a wall's corner
  (`PacketValidator.validateMovement`; of a few steps bunched into one update it knows the ends and
  not the way, as for anyone walking). A bot's phase never went through walls (`BotNavigation`).
- **A dash still goes over walls.** It is phased while it lasts, as a phase is, and ends on the
  farthest open cell along its line, whatever lies between, carried there through anything in the
  way. The server tells the two apart by the kit, which has one phase or one dash at most
  (`KitTest`): a phased player whose kit has a dash is dashing, and may be over a wall or its corner
  (`PacketValidator.isDashing`).
- **What else gets past a wall** is as it was: the star item, which jumps over whatever lies between
  (`Teleport.starTarget`), and the shots that fly over walls (`passesThroughWalls`). A blink may pass
  a wall's corner but stops short of the wall itself (`Teleport.blinkTarget`), as it has since
  2026-09 — but not to land one diagonal cell past the corner, a step the server refuses.
- The descriptions that promised a way through walls (the Wraith's Phase Shift, the Windwalker's
  Wind Form, the Kraken's Ink Cloud, and the Shark and its Deep Dive) say what the phase does now.
- Bots and balance are untouched: a bot's phase always went round walls, and dashes are as they were.

`PhaseShiftTest`, `PacketValidatorTest`, `GameClientPositionTest` and `MatchFuzzTest` pin it.

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
