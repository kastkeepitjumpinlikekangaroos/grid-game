# Barriers
`BarrierCast` raises a shield wall carried in front of its caster and turned to face their aim:
the Crusader's Bulwark (3.5s, a 12s cooldown counted from the raise), the Gladiator's Scutum, the
Paladin's Aegis and the Beetle's Carapace (3s each) and the Golem's Stone Wall (3.5s, 14s). It
stays up for that whole time whatever its holder does, and only their death takes it down sooner.
It is called a barrier in code because the Shield *item* (key 4, five seconds of invulnerability, `Player.hasShield`,
flag 0x01) already has the name; the player-facing ability can still say shield.

- **One shape.** `common/model/Barrier.scala` is a polyline of three segments, 2.0 cells out
  along the aim, 6.0 wide, its ends bent 0.6 back. The server stops shots on it and the client
  draws it from it. It is one-sided: only a path that crosses its front (heading in, toward the
  holder) meets it, so the holder's own shots go out through it and an enemy who gets inside the
  arc is past it.
- **What it stops**: whatever an enemy sends at it that a wall would stop. Not the holder's own,
  not a teammate's (`GameInstance.isTeammate`; in FFA everyone else is an enemy), and never a
  `passesThroughWalls` type. A stopped projectile halts where it met the barrier and is sent as
  `ProjectileAction.BLOCKED`; an explosive goes off there instead, as against a wall, and pierce,
  ricochet and boomerang types simply stop. A thrown trap stops at it as at a wall, landing short
  of it (`TrapPlacement.target`'s `barrierBefore`, from `GameInstance.barrierAcross` on the server
  and the bots and `Barriers.enemyBarrierAcross` on the client), and the server refuses a placement
  across one. Every projectile type in the roster that doesn't fly over walls is fired at one in
  `BarrierTest.everythingThatDoesNotFlyOverWallsIsStopped`. What gets past it gets past it by
  geometry: from behind, round an end, or from an enemy who has walked inside the arc (walking is
  never stopped); and a shot already inside the arc when it goes up is past it too.
- **Who it shelters**: nobody can be hit through it, because the line from the projectile to
  them has to be clear. That is required, not a nicety: hit radii (1.8 to 3.5 cells) reach past
  a barrier standing 2 cells out, so without it a shot hit the holder, or whoever was beside
  them, before it got to the barrier. Blasts, splashes, slams and vortices centred in front of it
  don't reach anyone it stands between (`ProjectileManager.shelteredFromBlast`).
- **It moves.** `ProjectileManager` snapshots the raised barriers once a tick (`BarrierSnapshot`), with where each
  was on the tick before. A projectile the barrier was carried or swung onto, in front of it last
  tick and behind it now, is stopped too: without that, a holder walking into fire, which is what
  the Crusader is for, let the shots through. A shot's first tick also checks the one-cell hop
  from where it was fired to where `spawnProjectile` put it, so an enemy pressed up against the
  barrier can't start a shot on its far side.
- **Up for its whole time.** Nothing its holder does takes it down: the primary, the burst shot
  and their other ability go out through it, and a trap or an item leaves it up too. Only death
  drops it early (`Player.damage`), and a respawn resets it. It used to drop at the first attack
  or cast, on the client and again on the server, so a Gladiator who raised the Scutum and swung
  the axe or threw the rope had it for a moment rather than three seconds.
- **Up and down.** The client raises it by sending effect flags 2 bit 2 with the aim angle,
  keeps its facing streamed while it is up (`GameClient.streamBarrier`: every 100ms, every 50ms
  while it turns, from both input handlers) and says when its own timer, started at the cast, has
  run out: an update without the bit, sent twice, the only time a living holder's client sends
  one. The server (`ClientHandler.updateBarrier`) honours a raise at 80% of the cooldown since the
  last raise, turns the barrier with every update, and ends it on an update without the bit, so
  one it only took from a later update (the raise lost, or refused while the holder was held)
  ends with the barrier its holder sees rather than a whole barrier's time after it. Only the
  newest update's word on it counts (`PacketValidator.takeBarrierWord`): the fence that keeps old
  positions out moves only on a position the server accepted, so an update sent just before a
  raise and overtaken by it on the way used to be taken after it whenever the raise's own position
  was refused — and dropped the barrier, which the cooldown then kept down while its holder saw it
  up and took shots through it. Every client runs the barrier on its own timer: ours from the
  cast, as a phase does, and everyone else's from the time left that each update carrying it
  reports (bytes [53-54]), dropping it on an update that doesn't. An update older than the newest
  one it has taken a barrier from is ignored, since datagrams overtake each other. A remote
  barrier used to be a 600ms lease renewed by those updates, which its holder's client streams
  **from its render loop** — so a hitch there longer than the lease (a GC pause, a resize, an
  alt-tab on the weak machine this targets), a burst of lost datagrams, or a run of updates the
  server refused after a knockback took a barrier that was still up, and still stopping shots, off
  every other screen for the rest of its life.
- **Drawn** by `BarrierPainter.draw`, after the flying projectiles: a translucent sheet
  22px tall along the polyline and a glowing strip on the ground under it, blue for ours and our
  allies', red for everyone else's. It grows out of the ground as it goes up, sinks and fades as
  it drops, and ripples for 300ms where a shot struck it. A ripple is kept as how far along the
  barrier it struck (`GameClient.getBarrierImpact*`), so it moves with a barrier that is carried
  on. The strip is what shows a barrier aimed straight left or right, whose sheet this projection
  sees edge-on.
- **Bots** raise it against what it will stop: a target who shoots from further off than a blade
  (a ranged character or a skirmisher) with shots that don't fly over walls, from within reach and
  with nothing solid between them, or such a shot coming at its front and still out beyond it —
  whether or not they can strike back from where they stand, since it stays up while they do.
  While it is up they face the target and close in, and attack and cast through it as they
  otherwise would (`BotCombat`).

`BarrierTest` (in `common/model` for the shape and a throw's landing, and in `server`),
`GameClientBarrierTest`, `GameClientTrapTest` and `PacketRoundTripTest` pin all of this.
