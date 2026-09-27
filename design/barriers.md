# Barriers
`BarrierCast` raises a shield wall carried in front of its caster and turned to face their aim:
the Crusader's Bulwark (3.5s, a 12s cooldown counted from the raise), the Gladiator's Scutum, the
Paladin's Aegis and the Beetle's Carapace (3s each) and the Golem's Stone Wall (3.5s, 14s). It is called a barrier in code because the Shield *item* (key 4, five seconds of invulnerability, `Player.hasShield`,
flag 0x01) already has the name; the player-facing ability can still say shield.

- **One shape.** `common/model/Barrier.scala` is a polyline of three segments, 2.0 cells out
  along the aim, 6.0 wide, its ends bent 0.6 back. The server stops shots on it and the client
  draws it from it. It is one-sided: only a path that crosses its front (heading in, toward the
  holder) meets it, so the holder's own shots go out through it and an enemy who gets inside the
  arc is past it.
- **What it stops**: enemy projectile bodies. Not the holder's own, not a teammate's
  (`GameInstance.isTeammate`; in FFA everyone else is an enemy), and never a
  `passesThroughWalls` type. A stopped projectile halts where it met the barrier and is sent as
  `ProjectileAction.BLOCKED`; an explosive goes off there instead, as against a wall, and pierce,
  ricochet and boomerang types simply stop.
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
- **Up and down.** The client raises it by sending effect flags 2 bit 2 with the aim angle,
  keeps its facing streamed while it is up (`GameClient.streamBarrier`: every 100ms, every 50ms
  while it turns, from both input handlers) and says when it has run out. The server
  (`ClientHandler.updateBarrier`) honours a raise at 80% of the cooldown since the last raise,
  turns the barrier with every update, and drops it on an update without the bit and on any spawn
  it accepts from the holder. Firing the primary, the burst shot or any ability drops it; the
  client drops it first and says so before the shot is sent. Death drops it (`Player.damage`),
  and a respawn resets it. Every client runs the barrier on its own timer: ours from the cast,
  as a phase does, and everyone else's from the time left that each update carrying it reports
  (bytes [53-54]), dropping it on an update that doesn't. An update older than the newest one it
  has taken a barrier from is ignored, since datagrams overtake each other. A remote barrier used
  to be a 600ms lease renewed by those updates, which its holder's client streams **from its
  render loop** — so a hitch there longer than the lease (a GC pause, a resize, an alt-tab on the
  weak machine this targets), a burst of lost datagrams, or a run of updates the server refused
  after a knockback took a barrier that was still up, and still stopping shots, off every other
  screen for the rest of its life. Dropping one early is announced twice, as running out is,
  because nobody else's copy expires on its own any more.
- **Drawn** by `BarrierPainter.draw`, after the flying projectiles: a translucent sheet
  22px tall along the polyline and a glowing strip on the ground under it, blue for ours and our
  allies', red for everyone else's. It grows out of the ground as it goes up, sinks and fades as
  it drops, and ripples for 300ms where a shot struck it. A ripple is kept as how far along the
  barrier it struck (`GameClient.getBarrierImpact*`), so it moves with a barrier that is carried
  on. The strip is what shows a barrier aimed straight left or right, whose sheet this projection
  sees edge-on.
- **Bots** raise it against a target who shoots from further off than a blade (a ranged
  character or a skirmisher), who can reach them and whom they can't yet reach, or against a
  shot coming in; face the target and close in while it is up; and drop it to fire or
  cast.

`BarrierTest` (in `common/model` for the shape, and in `server`), `GameClientBarrierTest` and
`PacketRoundTripTest` pin all of this.
