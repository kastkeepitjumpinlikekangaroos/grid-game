# Combat

What a projectile is and does: its definition (`ProjectileDef`), the effects a hit applies, splashes
and explosions, and how the server measures a hit. The server flies every projectile
(server/game/ProjectileManager, a tick every 30ms) and applies what it does (GameInstance's
ProjectileEffects); the client only draws what it hears (client/game, client/render). Barriers and
traps have docs of their own (design/barriers.md, design/traps.md).

## Projectile System
Projectiles are defined in `ProjectileDef.scala` with extensive customization:
- **Charge scaling** — speed, damage, and range scale with charge level
- **Distance damage scaling** — damage increases over distance (e.g., spears)
- **Pierce** — passes through `pierceCount` players and is used up by the next
  (`ProjectileDef.piercesAfter`). It used to stop at hit number `pierceCount`, so the fourteen
  types with a pierce of 1 never pierced. The server tells clients about a hit it flies on from
  with `ProjectileAction.PIERCE`, so they keep drawing it
- **Boomerang** — returns to owner after max range
- **Ricochet** — bounces off walls (`ricochetCount`)
- **AoE splash** — area damage on hit or at max range, with an optional freeze, stun or root
- **Explosions** — center/edge damage with blast radius
- **Pass-through** — can ignore players or walls

## On-Hit Effects
12 effect types applied when projectiles hit players. A def carries one in `onHitEffect` and any
more in `alsoOnHit` (a dart that poisons and slows, a horn that knocks back and stuns): everything
reads `ProjectileDef.onHitEffects`, both in order, direct hits and blasts alike. The Vampire's Bat
Swarm freeze used to be a special case in `GameInstance`; it is an effect in its def now.

**An explosion deals its blast and nothing else.** A def that `explodesOnPlayerHit` goes straight to
its `explosionConfig` blast, which applies no on-hit effect, so the Inferno's Inferno Blast and the
Pilot's Napalm Strike have never burned anyone. A splash (`aoeOnHit`, `aoeOnMaxRange`) does apply
them to everyone it catches, which is why the Chef's Flambé is a splash.
- `Freeze(durationMs)`, `Stun(durationMs)`, `Root(durationMs)`, `Slow(durationMs, multiplier)`
- `Burn(totalDamage, durationMs, tickMs)`, `Poison(totalDamage, durationMs, tickMs)`
- `Push(distance)`, `PullToOwner`, `VortexPull(radius, pullStrength)`
- `LifeSteal(healPercent)`, `SpeedBoost(durationMs)`, `TeleportOwnerBehind(distance, freezeDurationMs)`

**A stun is a freeze wearing different clothes.** `Player.tryStun` sets the same `frozenUntil`
timer with the same rules (refused while frozen, CC-immune or phased) and the same CC immunity
after it, so every "is frozen" gate in the server, the client and the bots holds a stunned
player without knowing stuns exist. All `stunnedUntil` adds is which of the two the client
draws — stars round the head instead of frost (`StatusEffectPainter.drawStunnedEffect`). Splashes
carry one through `AoESplashConfig.stunDurationMs`, beside `freezeDurationMs`/`rootDurationMs`.

**A poison is a second damage-over-time slot, not a second kind of burn.** It has its own
ticks and owner on `Player`, so a poison and a burn run at once — sharing burn's slot,
whichever landed second wiped the first out. `GameInstance.tickPlayers` ticks both every 200ms
(`tickBurn`, `tickPoison`), and a kill is credited to whoever cast it (`Attrs.CausePoison`).

A poison counts its ticks rather than timing out (`Player.takePoisonTick`): each falls due a
whole `tickMs` after the one before was *due*, it lasts until the last one has landed, and the
damage left is spread over the ticks left, so it deals exactly its total. The update for its
last tick goes out without the flag, which is how clients hear it is over. Nobody regenerates
while poisoned, between its ticks as well as on them.

**A burn does not deal its listed total**, and is left that way here so burns play as they did.
It times out on the clock (`isBurning` is `now < burnUntil`) and schedules each tick from when
the last one actually landed, so on a 200ms server tick every bite is late, the last falls after
the deadline, and the per-tick split truncates: the roster's burns deal 60-80% of their totals
(`Burn(15, 3000, 750)` does 9, `Burn(12, 3000, 750)` 9, `Burn(20, 4000, 800)` 16). Tune burns
by what they deal, or move them onto the poison's rules and retune. Regen is off for as long as
a burn lasts, as for a poison. It used to be off only on the ticks the burn bit, and at a melee
character's 3 HP a second regen healed most of a burn back between bites.

**A blast carries the same on-hit effect a direct hit would.** The `ProjectileAoEHit` case used
to apply only the holds and the burn and drop the rest, so no slam could push or pull, the
Necromancer's Soul Harvest ("healing from damage dealt") healed nothing, and the Cyborg's
Overclock boosted nobody. Life steal off a blast heals by the splash damage the event carries,
because a slam's own `effectiveDamage` is 0. `TeleportOwnerBehind` stays direct-hit only: it
needs one target to land behind, not a crowd. A slam whose on-hit effect is a `SpeedBoost` is a
self-buff, so it lands on the caster at cast time (`GameInstance.applyCastSelfBuff`) whether or
not anyone is standing in it.

**A push lands once a cast, and is drawn as a shove.** Everything one press of an attack fires is
one `Cast` (in `Projectile.scala`: a fan's five waves, which the client sends as five spawn
requests and `PacketValidator.castFor` groups, as bots' fans are grouped where they fire them). A
cast pushes a player once, however many of its projectiles strike them, straight away from where
it was fired (a blast's push, out from where it went off), and the rest of it then flies through
them. Each projectile used to push on its own, from wherever the last push had left the player and
from wherever its caster had walked to since: close in, where a fan's waves overlap, the Tidal Wave
threw a player 3, 6, 9 or 15 cells in a single tick and a fan of gusts up to 25, where now they
push 3 and 5 wherever the player stands (2.8 to 3.6 and 4.5 to 5.7 where the push runs across the
grid at an angle). The damage
is as it was: every projectile that strikes before the push deals its own. The server counts each
push as a knockback as well as a server move (`Player.recordKnockback`, carried in every update
about the player), and every client draws the move as a slide from where it drew them, off the
mark faster than anyone runs and slowing to a stop over ~200ms (`KnockbackSlide`; see
design/networking.md). Drawn where the server put them, a pushed player vanished and reappeared
cells away, turned to face the way they were thrown, and on other screens most pushes played a
teleport's flash (it plays for any jump of more than three cells along the grid's rows and
columns). `OnHitEffectsTest`, `GameClientKnockbackTest`, `RemoteMotionTest`.

## Where a hit lands

- **Projectile collision uses the drawn tile extents** — `Projectile.getCellX/Y` is
  `floor(x + 0.5)` because tiles are drawn centred on integer coordinates. Truncating put
  walls and map edges half a tile off their sprites, which is what made projectiles glitch
  into walls and off the map. A stopped projectile then sinks into the face it struck rather
  than blinking out, and wall-passers are drawn flying over the terrain.
- **Players are hit at the centre of their cell** — for the same reason: a player is drawn,
  and fires from, world `(x, y)`, so hits, splash, blasts and slams measure to there
  (`Projectile.withinPlayer` / `distanceToPlayer`). Measured from `(x + 0.5, y + 0.5)`, a shot
  from one side connected a cell sooner than from the other, and a ground slam reached a cell
  further south-east of its caster than north-west. `CombatTest` pins it.
- **A wave strikes where its water is.** The Tidal Wave and the Gust are drawn with their bulk
  trailing back from their leading edge (the wave's ridge of water, a gust's curls, cloud, whirlwind
  or eagle of wind, client/render/projectiles), which is where the projectile is, so a hit circle round
  that edge struck a player up to 1.8 cells ahead of the water, and pushed them away before it had
  visibly reached them. Their circle trails the edge (`ProjectileDef.hitCentreBehind`: 1.0 for the
  wave, 1.2 for the gust, whose sub-steps are shorter), though never back past the first place it
  is checked, a sub-step out from its spawn, where it would catch players beside its caster. So it
  reaches 0.6-0.8 of a cell ahead of the water and as wide as it did, and each flies a cell
  further than it did: over every cell round its caster, straight and diagonal, the Tidal Wave,
  the Wind Fan, the Wind Wall, the Gust Fan and the Gust strike exactly the players they struck
  before, only when the water reaches them. `ProjectileHitTest`, `OnHitEffectsTest`.
- **A wall shelters whoever stands behind it.** A hit radius (1.8 to 3.5 cells) reaches past a
  wall right in front of a player, so a shot drawn stopping at a fence hit whoever stood behind it:
  from a cell behind, 26 of the roster's 48 single-target shots did. A direct hit now needs the
  straight way from the projectile to the player clear of terrain that stops that projectile
  (`ProjectileManager.terrainBetween`, looked along in quarter cells: the rule a barrier's shelter
  keeps), so only a fence stands in the way of what flies over walls, and someone standing in a
  fence put down on them is still hit from outside it (neither end's own cell counts). Splashes,
  slams and blasts still reach through walls, as before. `CombatTest.nobodyIsHitThroughAWall`.
- **A shot is stopped by a wall right in front of its shooter.** A shot starts a cell out from where
  it was fired, and its sub-steps were judged only where each ended, so a step of exactly half a
  cell (a speed of 0.5 or 1.0 a tick) carried it from that first cell straight into the next: fired
  east or south, fifteen types flew through a fence or a one-cell wall their shooter stood against,
  for their whole range, while the aim lane and the bots' line of fire had them stop there. The
  first tick now judges the cell it was put in, as every sub-step's end is
  (`ProjectileManager.stopsWhereItIs`), and the client's flight does the same from a SPAWN
  (`NetProjectile`). A slam, which doesn't fly, still goes off where it lies.
  `CombatTest.aShotStopsAtAWallItsShooterStandsAgainst` fires every type in every direction at
  one, and `ProjectileFlightTest` holds the client to it.
- **A shot strikes the nearest of those it reaches.** A radius often takes in two players at once
  (point blank, one behind the other), and the shot struck whichever the projectile tick's grid
  listed last: fired east, the one in front was passed through untouched and the one behind hit.
  `CombatTest.aShotStrikesTheNearestOfThoseItReaches`. That grid (`PlayerGrid`, squares of 4 cells) only shows a shot the squares round it, which is as
  far as any shot may reach, its hit radius counted from the centre a wave trails
  (`hitCentreBehind`): `PlayerGridTest` holds it to the roster's furthest.
- **Water doesn't stop a shot.** Everything nobody can walk on stops a projectile — walls,
  cliffs, trees, rocks, lava — but water, shallow or deep, which it flies over as it does over
  the ground (`Tile.stopsProjectiles`, `WorldData.stopsProjectile`). A river or a pond used to be
  a wall to every shot but the wall-passers'. The server (`Projectile.hitsTerrain`, and
  `Projectile.ricochet` for which face of a wall standing in water a shot struck), the client
  (`TerrainImpact.blocks`, which `NetProjectile` flies up to and a DESPAWN walks back from) and
  the bots (`BotCombat.lineOfFire`, whether a shot would reach its target) ask the same question,
  cell for cell: were they to differ, the client would hold a shot at the water's edge that the
  server flies on from, and a bot would hold its fire across water. Nobody can stand on water all
  the same, so a push or a vortex stops at its edge (`Teleport.slide`), and so does a thrown
  trap, which is not a projectile. A pull to the thrower (a hook, a whip, a tongue) is the one
  thing that carries a player across: it lands its catch on the thrower's own cell whatever lies
  between, as Reap's always has through walls.

## Item Types
5 item types (defined in `ItemType.scala`): Gem, Heart, Star, Shield, Fence

What each does is `ItemType` and GameInstance (the server applies it); the client uses one
(GameClient's Items: a star's jump is picked with `Teleport` so the server agrees with it).

- **An item is picked up within reach, while there is room.** It is a player's once they are
  within two cells of it (`ITEM_PICKUP_RADIUS`, from their cell to its cell, as
  `ItemManager.checkPickup` measures) and carry fewer than ten (`MAX_INVENTORY_SIZE`, every kind
  together). The server checks on every step it accepts and on every projectile tick
  (`GameInstance.pickUpItemsInReach`). Only a step used to: an item that appeared beside someone
  standing still lay there until they moved, and so did one they had had no room for after they
  used an item to make some. Bots pick up on their own tick (`BotItems`), practice's passive ones
  never. A full inventory leaves the item where it lies, and the HUD says so (`HudPainter`): how
  many we carry beside the slots ("7/10", amber at ten) and, while an item is within reach,
  "Inventory full: use an item to make room". Nothing said it, and an item a full player stood on
  looked like an item that couldn't be picked up — the cap is reached in a minute or two of
  picking up without using.
- **A respawn is alive only at its spawn** (`Lives.respawn` heals last). Healed first, the player
  stood alive where they died until placed, and the tick could hand them an item lying there, into
  the inventory the respawn was clearing.
- **No client hears of an item's pickup before it hears of the item.** The server sends an item's
  SPAWN and its PICKUP from different threads, and the spawner used to put an item on the ground
  before sending its SPAWN. A pickup in between (a bot's tick, a step, the projectile tick) sent
  its PICKUP first, and a client that heard them in that order kept the item on its ground for
  good, where nobody, with room for it or not, could pick it up: a crowded free-for-all on the
  Cell, its bots standing by wherever items appear, showed one. It is less rare than the
  microseconds between the two make it sound, as the spawner's minute is a multiple of the bots'
  tick and of the projectile tick: one falls the same few milliseconds after every batch, all
  match. Now the spawner sends the SPAWN and only then puts the item down
  (`ItemManager.randomItem`, `GameInstance.spawnItemBatch`), and the client still drops a SPAWN
  for an item it has seen picked up (GameClient's Items), as a joiner's catch-up can be overtaken
  the same way. With the spawner held up between the two for 50ms on a crowded Cell, the old code
  left 12 such items on a client's ground in three minutes, and either fix alone none.

`ItemTest`, `RespawnTest` and `GameClientMatchTest` pin these, and the golden frame
`meadow_inventory_full` holds what the HUD shows of a full inventory.
