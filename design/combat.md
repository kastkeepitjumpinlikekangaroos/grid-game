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
