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

## Item Types
5 item types (defined in `ItemType.scala`): Gem, Heart, Star, Shield, Fence

What each does is `ItemType` and GameInstance (the server applies it); the client uses one
(GameClient's Items: a star's jump is picked with `Teleport` so the server agrees with it).
