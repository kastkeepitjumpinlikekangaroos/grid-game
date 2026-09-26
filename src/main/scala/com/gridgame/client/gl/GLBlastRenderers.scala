package com.gridgame.client.gl

import com.gridgame.common.Constants
import com.gridgame.common.model.{CharacterId, ProjectileType, TrapType}

/**
 * The blasts: every explosion and splash a projectile, a slam or a mine sets off, drawn as the
 * thrower's own. A blast is picked by who threw it as well as by what they threw
 * ([[styleOf]]): six characters slam a TREMOR_SLAM and four throw a GRENADE, and the Banshee's
 * wail, the Golem's quake and the Nanoswarm's swarm have nothing in common but that. Every one
 * used to be one of two generic animations — a fireball, or a set of rings — tinted with the
 * player's random colour, and a slam was both at once: a Wolf's howl went off as a grenade.
 *
 * A blast is drawn in two layers. [[GROUND]] is what lies on the ground — the scorch, the cracks,
 * the web, the glyph, the ring racing out — and the back half of anything standing up out of it,
 * drawn with the ground so the players in the blast and the walls in front of it cover it.
 * [[AIR]] is everything over it: the flash, the fireball, the debris, the smoke, and the front
 * half of what stands up. Drawn in one layer over everything, as the old ones were, a scorch mark
 * lay over the players standing in it and a spike behind them stood in front of them.
 *
 * The footprint is the real one. A blast of radius r cells reaches r cells in every direction in
 * the world, which on screen is an ellipse √2 r cells of tile across ([[footprintW]]); the old
 * rings were drawn at r, 71% of the area they hit.
 *
 * Cost. Everything here is a function of the blast's progress and its seed: no state, no
 * allocation, the same pieces on the same arcs every frame. Fragments are what a weak GPU pays
 * for, so a blast lays at most one soft fill over its footprint (its scorch) and draws the rest
 * as strokes, small inked shapes and a few puffs; the old explosion's 800x600 full-screen flash
 * was more fragments than twelve of these. Counts of the numerous small things (debris, motes,
 * nanites) scale with `detail`.
 */
object GLBlastRenderers {
  final val GROUND = 0
  final val AIR = 1

  // ── Styles ─────────────────────────────────────────────────────────
  // What a blast looks like. Grouped by the engine that draws them; the engine is the family's
  // anatomy, the style its thrower's palette and motif.
  final val S_GENERIC = 0
  // Explosions
  final val S_FRAG = 1          // Soldier's grenade: a tight fireball, shrapnel, dirt, grey smoke
  final val S_ROCKET = 2        // Soldier's rocket: fireball and a smoke ring rolling out
  final val S_BOMB = 3          // Bombardier's grenade: a comic starburst and black cartoon smoke
  final val S_CLUSTER = 4       // Bombardier's cluster bomb: a burst, then bomblets popping round it
  final val S_BOMB_ROCKET = 5   // Bombardier's rocket: the starburst and a smoke ring
  final val S_PLASMA = 6        // Cyborg's arm rocket: a cyan plasma burst, hex fragments, arcs
  final val S_EMP = 7           // Railgunner's frag grenade: coil rings, steel shards, blue sparks
  final val S_CANNON = 8        // Pirate's cannonball: black powder smoke, splinters, the ball
  final val S_NAPALM = 9        // Pilot's napalm: a sheet of fire standing on the ground
  final val S_INFERNO = 10      // Inferno's blast: a towering fireball in a ring of flame
  final val S_ERUPTION = 11     // Magma Knight's eruption: lava bombs, ash, a pool cooling to crust
  final val S_MINE = 12         // Sentinel's mine: a sharp tech blast, plates and a blue spark
  // Fire and lava
  final val S_MAGMA = 13        // Magma Knight's primary: molten globs and a small cooling pool
  final val S_HELLFIRE = 14     // Cerberus: a crimson pool and three black-tipped flames
  final val S_FLAMBE = 15       // Chef: a whoosh of blue-footed flame and a pinch of spice
  final val S_PHOENIX = 16      // Phoenix: flame feathers fanning out, embers rising
  // Liquids
  final val S_PLAGUE = 17       // Plague Doctor's primary: green gas, a puddle, flies
  final val S_ALCHEMY = 18      // Alchemist's primary: a pop of bright potion and bubbles
  final val S_BLIGHT = 19       // Plague Doctor's blight bomb: glass, sludge, purple gas
  final val S_ACID_FLASK = 20   // Alchemist's acid flask: glass, lime acid fizzing and steaming
  final val S_ACID_SPRAY = 21   // Scorpion: a spray of venom sizzling into the ground
  final val S_MUD = 22          // Mudslinger: a big splat and globs of mud
  final val S_INK = 23          // Kraken: an ink splat with tentacles rising out of it
  // Gas
  final val S_MIASMA = 24       // Plague Doctor's miasma: a rolling cloud with a skull in it
  final val S_VENOM_CLOUD = 25  // Hydra: a low venom fog with plumes rising like heads
  // Water
  final val S_SPLASH = 26       // Tidecaller's primary: a crown of water
  final val S_GEYSER = 27       // Tidecaller's geyser: a column of water and its spray
  // Electricity
  final val S_STORM = 28        // Stormcaller's chain lightning: yellow arcs, a burnt branching scar
  final val S_THUNDER = 29      // Stormcaller's thunder strike: a bolt out of the sky
  final val S_TESLA = 30        // Tesla's chain lightning: cyan arcs inside coil rings
  final val S_TESLA_FORK = 31   // Tesla's fork: arcs that split as they reach
  final val S_OVERCLOCK = 32    // Cyborg's overclock: a hex pulse and speed lines
  // Earth
  final val S_SEISMIC = 33      // Earthshaker: rings of stone spikes, cracks, dust
  final val S_TREMOR = 34       // Beetle: a quake of dirt and bronze chitin
  final val S_GOLEM = 35        // Golem: stone blocks and a rune circle
  final val S_POUND = 36        // Gorilla: a crater, clods and turf
  final val S_MECH = 37         // Mech Pilot: a hex shock, sparks, bolts, steam
  final val S_SCALES = 38       // Anubis: a golden sun-disc, an ankh, sand spouts
  // Ice
  final val S_ICE_QUAKE = 39    // Avalanche's ice quake: frost spreading, ice spikes
  final val S_AVALANCHE = 40    // Avalanche's crush: a burst of snow and ice
  final val S_FROST = 41        // frost trap, snare mine: a snowflake and a few spikes
  // Nature
  final val S_THORNS = 42       // Thornweaver's primary: thorns splaying out
  final val S_LEAVES = 43       // Druid: leaves bursting and fluttering down
  final val S_SPLINTERS = 44    // Treant's primary: splinters and bark
  final val S_ENTANGLE = 45     // Thornweaver's entangle: vines whipping up everywhere
  final val S_ROOTS = 46        // Treant's root growth: great roots arching out of the ground
  final val S_WEB = 47          // Spider: a web spreading over the ground
  // Dark
  final val S_SOUL_HARVEST = 48 // Necromancer: souls rising and streaming in to him
  final val S_RAISE_DEAD = 49   // Necromancer: hands clawing up out of the ground
  final val S_BONES = 50        // Skeleton King: a burst of bones
  final val S_ECHO = 51         // Banshee's echo bolt: ghostly echoes
  final val S_WAIL = 52         // Banshee's wail: a screaming face and spectral rings
  final val S_SHADOW_BURST = 53 // Shadowfiend's burst: tendrils of shadow lashing out
  // Inward
  final val S_VOID_PULL = 54    // Shadowfiend's void pull: shadow sucked into a black hole
  final val S_GRAVITY = 55      // Graviton: a spiral falling in, lensing rings, a pop
  final val S_FIRE_VORTEX = 56  // Inferno's fire vortex: a whirl of flame
  final val S_TEMPORAL = 57     // Chronomancer: a clock face turning back, a rift
  // Holy
  final val S_SMITE = 58        // Cleric: a beam of light and a sunburst
  final val S_SHIELD_BASH = 59  // Crusader: a golden shield and a ring pushing out
  // Sound
  final val S_LUTE = 60         // Bard: notes riding rings of sound
  final val S_BASS = 61         // Musician: neon bass rings and a bouncing equaliser
  final val S_HOWL = 62         // Wolf: a moon, and silver waves rolling out
  final val S_ROAR = 63         // Shapeshifter: claw marks, jagged waves, fur
  final val S_SHRIEK = 64       // Harpy: feathers and sharp waves
  // Swarm and stone
  final val S_NANO_SPLASH = 65  // Nanoswarm's bolt: nanites scattering
  final val S_NANO_BURST = 66   // Nanoswarm's explosion: a swarm flung out over a hex grid
  final val S_PETRIFY = 67      // Medusa: an eye opening, and a wave of stone
  final val STYLE_COUNT = 68

  private val _name = new Array[String](STYLE_COUNT)
  private val _dur = new Array[Int](STYLE_COUNT)
  private val _lr = new Array[Float](STYLE_COUNT)
  private val _lg = new Array[Float](STYLE_COUNT)
  private val _lb = new Array[Float](STYLE_COUNT)
  private val _li = new Array[Float](STYLE_COUNT)
  private val _heavy = new Array[Float](STYLE_COUNT)

  /** A style's name, how long it lasts, the light it casts (colour and strength; 0 for none) and
    * how hard it shakes the air (the post-processor's radial warp; 0 for none). */
  private def style(id: Int, name: String, durMs: Int, lr: Float, lg: Float, lb: Float, li: Float,
                    heavy: Float = 0f): Unit = {
    _name(id) = name; _dur(id) = durMs
    _lr(id) = lr; _lg(id) = lg; _lb(id) = lb; _li(id) = li; _heavy(id) = heavy
  }

  style(S_GENERIC, "generic", 1000, 1f, 1f, 1f, 0.4f)
  style(S_FRAG, "frag", 1150, 1f, 0.66f, 0.3f, 0.6f, 1f)
  style(S_ROCKET, "rocket", 1150, 1f, 0.6f, 0.25f, 0.6f, 1f)
  style(S_BOMB, "bomb", 1150, 1f, 0.72f, 0.3f, 0.6f, 1f)
  style(S_CLUSTER, "cluster", 1300, 1f, 0.7f, 0.3f, 0.65f, 1f)
  style(S_BOMB_ROCKET, "bomb rocket", 1150, 1f, 0.66f, 0.28f, 0.6f, 1f)
  style(S_PLASMA, "plasma", 1050, 0.5f, 0.85f, 1f, 0.6f, 0.8f)
  style(S_EMP, "emp", 1100, 0.6f, 0.8f, 1f, 0.6f, 0.9f)
  style(S_CANNON, "cannon", 1300, 1f, 0.72f, 0.35f, 0.4f, 1f)
  style(S_NAPALM, "napalm", 1500, 1f, 0.52f, 0.18f, 0.6f, 0.8f)
  style(S_INFERNO, "inferno", 1300, 1f, 0.55f, 0.2f, 0.75f, 1f)
  style(S_ERUPTION, "eruption", 1450, 1f, 0.45f, 0.12f, 0.65f, 1f)
  style(S_MINE, "mine", 1100, 1f, 0.66f, 0.32f, 0.55f, 1f)
  style(S_MAGMA, "magma", 850, 1f, 0.5f, 0.15f, 0.4f)
  style(S_HELLFIRE, "hellfire", 900, 1f, 0.28f, 0.14f, 0.4f)
  style(S_FLAMBE, "flambe", 900, 0.6f, 0.68f, 1f, 0.45f)
  style(S_PHOENIX, "phoenix", 1050, 1f, 0.72f, 0.28f, 0.55f)
  style(S_PLAGUE, "plague", 850, 0.55f, 0.9f, 0.3f, 0.25f)
  style(S_ALCHEMY, "alchemy", 750, 0.75f, 1f, 0.55f, 0.3f)
  style(S_BLIGHT, "blight", 1250, 0.55f, 0.85f, 0.3f, 0.3f, 0.5f)
  style(S_ACID_FLASK, "acid flask", 1150, 0.8f, 1f, 0.3f, 0.3f, 0.5f)
  style(S_ACID_SPRAY, "acid spray", 950, 0.8f, 1f, 0.3f, 0.25f)
  style(S_MUD, "mud", 1150, 0f, 0f, 0f, 0f, 0.5f)
  style(S_INK, "ink", 1350, 0f, 0f, 0f, 0f)
  style(S_MIASMA, "miasma", 1450, 0.5f, 0.9f, 0.35f, 0.3f)
  style(S_VENOM_CLOUD, "venom cloud", 1250, 0.7f, 0.95f, 0.3f, 0.25f)
  style(S_SPLASH, "splash", 850, 0.5f, 0.8f, 1f, 0.2f)
  style(S_GEYSER, "geyser", 1250, 0.5f, 0.8f, 1f, 0.25f)
  style(S_STORM, "storm", 850, 1f, 0.92f, 0.45f, 0.65f)
  style(S_THUNDER, "thunder", 1050, 1f, 0.95f, 0.6f, 0.85f, 1f)
  style(S_TESLA, "tesla", 850, 0.45f, 0.85f, 1f, 0.6f)
  style(S_TESLA_FORK, "tesla fork", 750, 0.5f, 0.9f, 1f, 0.5f)
  style(S_OVERCLOCK, "overclock", 1000, 0.3f, 1f, 0.8f, 0.45f)
  style(S_SEISMIC, "seismic", 1500, 1f, 0.8f, 0.5f, 0.2f, 1f)
  style(S_TREMOR, "tremor", 1250, 1f, 0.8f, 0.5f, 0.15f, 1f)
  style(S_GOLEM, "golem", 1350, 1f, 0.7f, 0.3f, 0.3f, 1f)
  style(S_POUND, "pound", 1050, 1f, 0.8f, 0.5f, 0.15f, 1f)
  style(S_MECH, "mech", 1050, 1f, 0.6f, 0.25f, 0.35f, 1f)
  style(S_SCALES, "scales", 1450, 1f, 0.85f, 0.4f, 0.5f)
  style(S_ICE_QUAKE, "ice quake", 1450, 0.6f, 0.9f, 1f, 0.4f, 0.8f)
  style(S_AVALANCHE, "avalanche", 1150, 0.8f, 0.95f, 1f, 0.25f, 0.7f)
  style(S_FROST, "frost", 1050, 0.6f, 0.9f, 1f, 0.3f)
  style(S_THORNS, "thorns", 750, 0.5f, 0.9f, 0.4f, 0.12f)
  style(S_LEAVES, "leaves", 900, 0.6f, 1f, 0.5f, 0.25f)
  style(S_SPLINTERS, "splinters", 750, 1f, 0.8f, 0.5f, 0.08f)
  style(S_ENTANGLE, "entangle", 1450, 0.5f, 0.95f, 0.4f, 0.25f)
  style(S_ROOTS, "roots", 1550, 0.6f, 0.9f, 0.4f, 0.15f, 0.6f)
  style(S_WEB, "web", 1550, 0.9f, 0.9f, 1f, 0.15f)
  style(S_SOUL_HARVEST, "soul harvest", 1450, 0.4f, 1f, 0.6f, 0.55f)
  style(S_RAISE_DEAD, "raise dead", 1150, 0.45f, 1f, 0.5f, 0.4f)
  style(S_BONES, "bones", 800, 0.85f, 0.8f, 1f, 0.2f)
  style(S_ECHO, "echo", 850, 0.6f, 0.85f, 1f, 0.35f)
  style(S_WAIL, "wail", 1350, 0.6f, 0.9f, 1f, 0.5f, 1f)
  style(S_SHADOW_BURST, "shadow burst", 1250, 0.6f, 0.3f, 1f, 0.35f, 0.6f)
  style(S_VOID_PULL, "void pull", 1150, 0.55f, 0.3f, 1f, 0.4f, 0.7f)
  style(S_GRAVITY, "gravity", 1150, 0.6f, 0.45f, 1f, 0.5f, 0.9f)
  style(S_FIRE_VORTEX, "fire vortex", 1250, 1f, 0.55f, 0.2f, 0.6f, 0.6f)
  style(S_TEMPORAL, "temporal", 1250, 0.4f, 1f, 0.9f, 0.45f)
  style(S_SMITE, "smite", 1050, 1f, 0.92f, 0.6f, 0.85f)
  style(S_SHIELD_BASH, "shield bash", 1050, 1f, 0.85f, 0.45f, 0.5f, 1f)
  style(S_LUTE, "lute", 950, 0.9f, 0.7f, 1f, 0.4f)
  style(S_BASS, "bass", 950, 1f, 0.4f, 0.9f, 0.45f, 0.8f)
  style(S_HOWL, "howl", 1350, 0.7f, 0.8f, 1f, 0.35f, 0.9f)
  style(S_ROAR, "roar", 1150, 1f, 0.6f, 0.3f, 0.3f, 1f)
  style(S_SHRIEK, "shriek", 1150, 0.85f, 0.8f, 1f, 0.3f, 0.9f)
  style(S_NANO_SPLASH, "nano splash", 700, 0.3f, 1f, 0.8f, 0.3f)
  style(S_NANO_BURST, "nano burst", 1250, 0.3f, 1f, 0.8f, 0.45f, 0.6f)
  style(S_PETRIFY, "petrify", 1350, 0.7f, 1f, 0.4f, 0.45f, 0.6f)

  // ── Who throws what ────────────────────────────────────────────────
  // By projectile type, then by (character, projectile type) for a type thrown by characters
  // who have nothing in common: the same shape as AbilitySounds' two tables.
  private val _byType = new Array[Byte](256)
  private val _byCharType = new Array[Byte](256 * 256)

  private def byType(style: Int, types: Byte*): Unit = types.foreach(t => _byType(t & 0xFF) = style.toByte)
  private def only(style: Int, who: CharacterId, t: Byte): Unit =
    _byCharType(((who.id & 0xFF) << 8) | (t & 0xFF)) = style.toByte

  byType(S_FRAG, ProjectileType.GRENADE)
  byType(S_ROCKET, ProjectileType.ROCKET)
  byType(S_CLUSTER, ProjectileType.CLUSTER_BOMB)
  byType(S_NAPALM, ProjectileType.NAPALM_STRIKE)
  byType(S_INFERNO, ProjectileType.INFERNO_BLAST)
  byType(S_ERUPTION, ProjectileType.ERUPTION)
  byType(S_MAGMA, ProjectileType.MAGMA_BALL)
  byType(S_FLAMBE, ProjectileType.FLAMBE)
  byType(S_PHOENIX, ProjectileType.FLAME_TRAIL)
  byType(S_PLAGUE, ProjectileType.PLAGUE_BOLT)
  byType(S_MIASMA, ProjectileType.MIASMA)
  byType(S_BLIGHT, ProjectileType.BLIGHT_BOMB)
  byType(S_ACID_FLASK, ProjectileType.ACID_FLASK)
  byType(S_ACID_SPRAY, ProjectileType.ACID_SPRAY, ProjectileType.ACID_BOMB)
  byType(S_VENOM_CLOUD, ProjectileType.POISON_CLOUD)
  byType(S_MUD, ProjectileType.MUD_BOMB)
  byType(S_INK, ProjectileType.INK_SNARE)
  byType(S_SPLASH, ProjectileType.SPLASH)
  byType(S_GEYSER, ProjectileType.GEYSER)
  byType(S_STORM, ProjectileType.CHAIN_LIGHTNING)
  byType(S_THUNDER, ProjectileType.THUNDER_STRIKE)
  byType(S_TESLA, ProjectileType.TESLA_COIL)
  byType(S_TESLA_FORK, ProjectileType.CHAIN_LIGHTNING_FORK)
  byType(S_OVERCLOCK, ProjectileType.OVERCLOCK_BEAM)
  byType(S_SEISMIC, ProjectileType.SEISMIC_ROOT)
  byType(S_TREMOR, ProjectileType.TREMOR_SLAM)
  byType(S_POUND, ProjectileType.SEISMIC_SLAM)
  byType(S_AVALANCHE, ProjectileType.AVALANCHE_CRUSH)
  byType(S_ICE_QUAKE, ProjectileType.ICE_QUAKE)
  byType(S_FROST, ProjectileType.FROST_TRAP, ProjectileType.SNARE_MINE)
  byType(S_THORNS, ProjectileType.THORN)
  byType(S_SPLINTERS, ProjectileType.THORN_LIGHT)
  byType(S_ENTANGLE, ProjectileType.ENTANGLE)
  byType(S_ROOTS, ProjectileType.ROOT_GROWTH)
  byType(S_WEB, ProjectileType.WEB_TRAP)
  byType(S_SOUL_HARVEST, ProjectileType.SOUL_HARVEST)
  byType(S_RAISE_DEAD, ProjectileType.RAISE_DEAD)
  byType(S_BONES, ProjectileType.BONE_BOOMERANG)
  byType(S_ECHO, ProjectileType.ECHO_BOLT, ProjectileType.WAIL)
  byType(S_GRAVITY, ProjectileType.VORTEX_BOMB, ProjectileType.GRAVITY_LOCK)
  byType(S_TEMPORAL, ProjectileType.GRAVITY_WELL)
  byType(S_SMITE, ProjectileType.SMITE)
  byType(S_SHIELD_BASH, ProjectileType.SHOCKWAVE)
  byType(S_LUTE, ProjectileType.SONIC_BOOM)
  byType(S_HOWL, ProjectileType.HOWL)
  byType(S_ROAR, ProjectileType.FERAL_ROAR)
  byType(S_NANO_SPLASH, ProjectileType.NANO_BOLT)
  byType(S_PETRIFY, ProjectileType.STONE_GAZE)

  // One type, several fantasies
  only(S_BOMB, CharacterId.Bombardier, ProjectileType.GRENADE)
  only(S_EMP, CharacterId.Railgunner, ProjectileType.GRENADE)
  only(S_CANNON, CharacterId.Pirate, ProjectileType.GRENADE)
  only(S_BOMB_ROCKET, CharacterId.Bombardier, ProjectileType.ROCKET)
  only(S_PLASMA, CharacterId.Cyborg, ProjectileType.ROCKET)
  only(S_HELLFIRE, CharacterId.Cerberus, ProjectileType.MAGMA_BALL)
  only(S_ALCHEMY, CharacterId.Alchemist, ProjectileType.PLAGUE_BOLT)
  only(S_TESLA, CharacterId.Tesla, ProjectileType.CHAIN_LIGHTNING)
  only(S_SCALES, CharacterId.Anubis, ProjectileType.SEISMIC_ROOT)
  only(S_WAIL, CharacterId.Banshee, ProjectileType.TREMOR_SLAM)
  only(S_SHADOW_BURST, CharacterId.Shadowfiend, ProjectileType.TREMOR_SLAM)
  only(S_NANO_BURST, CharacterId.Nanoswarm, ProjectileType.TREMOR_SLAM)
  only(S_SHRIEK, CharacterId.Harpy, ProjectileType.TREMOR_SLAM)
  only(S_GOLEM, CharacterId.Golem, ProjectileType.TREMOR_SLAM)
  only(S_MECH, CharacterId.MechPilot, ProjectileType.SEISMIC_SLAM)
  only(S_LEAVES, CharacterId.Druid, ProjectileType.THORN)
  only(S_VOID_PULL, CharacterId.Shadowfiend, ProjectileType.VORTEX_BOMB)
  only(S_FIRE_VORTEX, CharacterId.Inferno, ProjectileType.VORTEX_BOMB)
  only(S_BASS, CharacterId.Musician, ProjectileType.SONIC_BOOM)

  /** The blast a projectile of `pType` thrown by character `charId` sets off (a CharacterId's id,
    * or -1 when the thrower isn't known: then the type's own). */
  def styleOf(charId: Byte, pType: Byte): Int = {
    val own = if (charId >= 0) _byCharType(((charId & 0xFF) << 8) | (pType & 0xFF)) else 0
    if (own != 0) own else _byType(pType & 0xFF)
  }

  /** The blast of a trap going off: only a mine has one. */
  def trapStyle(trapType: Byte): Int = if (trapType == TrapType.MINE) S_MINE else S_FRAG

  /** Whether `pType` has a blast of its own (a type without one is drawn as the generic). */
  def hasStyle(pType: Byte): Boolean = _byType(pType & 0xFF) != 0

  def name(style: Int): String = _name(style)
  def durationMs(style: Int): Int = _dur(style)
  def lightR(style: Int): Float = _lr(style)
  def lightG(style: Int): Float = _lg(style)
  def lightB(style: Int): Float = _lb(style)
  def lightStrength(style: Int): Float = _li(style)
  def heaviness(style: Int): Float = _heavy(style)

  private val SQRT2 = Math.sqrt(2).toFloat
  /** Half the width on screen, in world units, of the ground a blast of `radius` cells reaches. */
  def footprintW(radius: Float): Float = radius * Constants.ISO_HALF_W * SQRT2
  /** Half its height on screen. */
  def footprintH(radius: Float): Float = radius * Constants.ISO_HALF_H * SQRT2

  // ── The blast being drawn ──────────────────────────────────────────
  // Set by `draw` for the building blocks below, which all read them. Only the render thread
  // (or a dev tool's own) ever draws, so plain fields are safe, as they are in the projectile
  // renderers.
  private var sb: ShapeBatch = _
  private var layer = 0
  private var cx = 0f
  private var cy = 0f
  private var W = 1f
  private var H = 1f
  private var T = 0f      // progress through its life, 0-1
  private var MS = 0f     // milliseconds since it went off
  private var seed = 0
  private var det = 1f    // how many of the numerous small things to draw, 0.5-1
  private var k = 1f      // the size of its details, from the size of the blast
  private var pcR = 1f    // the thrower's colour, for the generic blast
  private var pcG = 1f
  private var pcB = 1f

  /**
   * Draw one layer of a blast. `(sx, sy)` is where it went off, `(w, h)` its footprint's
   * semi-axes on screen ([[footprintW]], [[footprintH]]), `t` its progress through its
   * [[durationMs]], `ms` the time since it went off, `seed` anything that tells it from the
   * blast beside it, `detail` how many of its small things to draw (1 at High), and
   * `(pr, pg, pb)` the thrower's colour, which only the generic blast uses.
   */
  def draw(batch: ShapeBatch, styleId: Int, lay: Int, sx: Float, sy: Float, w: Float, h: Float,
           t: Float, ms: Float, sd: Int, detail: Float, pr: Float, pg: Float, pb: Float): Unit = {
    sb = batch; layer = lay; cx = sx; cy = sy; W = w; H = h; T = t; MS = ms; seed = sd
    det = detail; pcR = pr; pcG = pg; pcB = pb
    k = Math.max(0.85f, Math.min(1.45f, Math.sqrt(w / 85f).toFloat))
    (styleId: @scala.annotation.switch) match {
      case S_FRAG | S_ROCKET | S_BOMB | S_CLUSTER | S_BOMB_ROCKET | S_PLASMA | S_EMP | S_CANNON |
           S_NAPALM | S_INFERNO | S_ERUPTION | S_MINE => explosion(styleId)
      case S_MAGMA | S_HELLFIRE => lava(styleId)
      case S_FLAMBE | S_PHOENIX => flame(styleId)
      case S_PLAGUE | S_ALCHEMY | S_BLIGHT | S_ACID_FLASK | S_ACID_SPRAY | S_MUD | S_INK => liquid(styleId)
      case S_MIASMA | S_VENOM_CLOUD => gas(styleId)
      case S_SPLASH | S_GEYSER => water(styleId)
      case S_STORM | S_THUNDER | S_TESLA | S_TESLA_FORK | S_OVERCLOCK => electric(styleId)
      case S_SEISMIC | S_TREMOR | S_GOLEM | S_POUND | S_MECH | S_SCALES => earth(styleId)
      case S_ICE_QUAKE | S_AVALANCHE | S_FROST => ice(styleId)
      case S_THORNS | S_LEAVES | S_SPLINTERS | S_ENTANGLE | S_ROOTS | S_WEB => nature(styleId)
      case S_SOUL_HARVEST | S_RAISE_DEAD | S_BONES | S_ECHO | S_WAIL | S_SHADOW_BURST => dark(styleId)
      case S_VOID_PULL | S_GRAVITY | S_FIRE_VORTEX | S_TEMPORAL => inward(styleId)
      case S_SMITE | S_SHIELD_BASH => holy(styleId)
      case S_LUTE | S_BASS | S_HOWL | S_ROAR | S_SHRIEK => sonic(styleId)
      case S_NANO_SPLASH | S_NANO_BURST => nano(styleId)
      case S_PETRIFY => petrify()
      case _ => generic()
    }
    sb.resetModifiers()
  }

  // ── Arithmetic ─────────────────────────────────────────────────────
  private val TWO_PI = (Math.PI * 2).toFloat
  private val PI = Math.PI.toFloat

  @inline private def clamp01(v: Float): Float = if (v < 0f) 0f else if (v > 1f) 1f else v
  /** How far through [a, b] `t` is, clamped. */
  @inline private def seg(t: Float, a: Float, b: Float): Float = clamp01((t - a) / (b - a))
  @inline private def easeOut(t: Float): Float = { val u = 1f - t; 1f - u * u }
  @inline private def easeOut3(t: Float): Float = { val u = 1f - t; 1f - u * u * u }
  @inline private def smooth(t: Float): Float = t * t * (3f - 2f * t)
  /** 1 until `from`, then down to nothing at the end. */
  @inline private def tail(t: Float, from: Float): Float = if (t <= from) 1f else clamp01((1f - t) / (1f - from))
  /** Up over [0, a], held, down over [b, 1]: the life of something that comes and goes. */
  @inline private def life(t: Float, a: Float, b: Float): Float =
    if (t < a) t / a else if (t > b) clamp01((1f - t) / (1f - b)) else 1f
  @inline private def mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t
  @inline private def cosf(a: Float): Float = Math.cos(a).toFloat
  @inline private def sinf(a: Float): Float = Math.sin(a).toFloat
  /** A lighter tone, and the line colour: a dark version of the hue, never black. */
  @inline private def lit(c: Float): Float = Math.min(1f, c * 0.45f + 0.55f)
  @inline private def ink(c: Float): Float = c * 0.24f

  /** A deterministic hash of two ints to [-1, 1]: the same piece on the same arc every frame. */
  @inline private def hash(a: Int, b: Int): Float = {
    var h = a * 0x27D4EB2D + b * 0x165667B1
    h = (h ^ (h >>> 15)) * 0x2C1B3C6D
    h = (h ^ (h >>> 12)) * 0x297A2D39
    h ^= h >>> 15
    (h & 0xFFFF) / 32767.5f - 1f
  }
  @inline private def h01(a: Int, b: Int): Float = (hash(a, b) + 1f) * 0.5f

  // Scratch: one primitive at a time uses them, and the batch has read them when it returns
  private val _xs = new Array[Float](32)
  private val _ys = new Array[Float](32)
  private val _px = new Array[Float](64)
  private val _py = new Array[Float](64)
  private val _pw = new Array[Float](64)
  private val _pa = new Array[Float](64)
  // Per-puff state, computed once and read by each of a cluster's three passes
  private val _ux = new Array[Float](64)
  private val _uy = new Array[Float](64)
  private val _us = new Array[Float](64)
  private val _ua = new Array[Float](64)
  // Things standing on a ring, sorted far to near
  private val _ord = new Array[Int](32)
  private val _ordY = new Array[Float](32)

  /** Is something at screen y `y` on the ground behind the middle of the blast? Behind is drawn
    * with the ground, so whoever stands in the blast is in front of it. */
  @inline private def behind(y: Float): Boolean = y < cy
  /** Whether this layer draws what stands at ground y `y`. */
  @inline private def mine(y: Float): Boolean = (layer == GROUND) == behind(y)

  /** Sort the first `n` of `_ordY` far (small y) to near, into `_ord`. */
  private def sortFarToNear(n: Int): Unit = {
    var i = 0
    while (i < n) { _ord(i) = i; i += 1 }
    i = 1
    while (i < n) {
      val v = _ord(i); val vy = _ordY(v)
      var j = i - 1
      while (j >= 0 && _ordY(_ord(j)) > vy) { _ord(j + 1) = _ord(j); j -= 1 }
      _ord(j + 1) = v
      i += 1
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  BUILDING BLOCKS
  // ═══════════════════════════════════════════════════════════════════

  /** The flash where it went off, over the first `len` of its life: a soft disc `size` of the
    * footprint across and a hot core, lying on the ground. */
  private def flash(len: Float, size: Float, r: Float, g: Float, b: Float, strength: Float = 1f, at: Float = 0f): Unit = {
    if (layer != AIR || T < at || T >= at + len) return
    val p = (T - at) / len
    val a = (1f - p) * (1f - p) * strength
    val rad = W * size * (0.55f + 0.45f * easeOut(p))
    sb.fillOvalSoft(cx, cy - H * 0.12f, rad, rad * 0.62f, r, g, b, 0.8f * a, 0f, 22)
    sb.fillOval(cx, cy - H * 0.12f, rad * 0.36f, rad * 0.26f, mix(r, 1f, 0.8f), mix(g, 1f, 0.8f), mix(b, 1f, 0.8f), a, 16)
  }

  /** A ring racing out along the ground over [a, b] of the blast's life, to `reach` of the
    * footprint, thinning as it goes, inked so it reads on snow and sand. */
  private def shockRing(a: Float, b: Float, w0: Float, r: Float, g: Float, bl: Float, alpha: Float,
                        reach: Float = 1f): Unit = {
    if (layer != GROUND) return
    val p = seg(T, a, b)
    if (p <= 0f || p >= 1f) return
    val q = easeOut3(p) * reach
    val lw = w0 * k * (1f - 0.6f * p)
    val al = alpha * (1f - p) * Math.min(1f, p * 10f)
    if (al <= 0.01f) return
    sb.strokeOval(cx, cy, W * q, H * q, lw + 3f, ink(r), ink(g), ink(bl), 0.55f * al, 24)
    sb.strokeOval(cx, cy, W * q, H * q, lw, r, g, bl, al, 24)
  }

  /** A thin ring standing at exactly the edge of the footprint: who was in it. */
  private def edgeRing(a: Float, b: Float, w: Float, r: Float, g: Float, bl: Float, alpha: Float): Unit = {
    if (layer != GROUND) return
    val al = alpha * life(seg(T, a, b), 0.15f, 0.6f)
    if (al <= 0.01f || T < a || T > b) return
    sb.strokeOval(cx, cy, W, H, w + 2.4f, ink(r), ink(g), ink(bl), 0.45f * al, 24)
    sb.strokeOval(cx, cy, W, H, w, r, g, bl, al, 24)
  }

  /** A soft patch on the ground, `size` of the footprint: in over the first tenth of the
    * blast's life, out from `out`. The only fill that covers the whole blast. */
  private def scorch(size: Float, r: Float, g: Float, b: Float, a: Float, out: Float = 0.6f): Unit = {
    if (layer != GROUND) return
    val al = a * Math.min(1f, T * 10f) * tail(T, out)
    if (al <= 0.01f) return
    sb.fillOvalSoft(cx, cy, W * size, H * size, r, g, b, al, 0f, 22)
  }

  /** A ground point: angle `th` round the footprint, `rho` of the way out. */
  @inline private def gx(c: Float, rho: Float): Float = cx + c * rho * W
  @inline private def gy(s: Float, rho: Float): Float = cy + s * rho * H

  /**
   * `n` jagged cracks out from the middle to `reach` of the footprint, growing over [g0, g1] and
   * fading from 60%, each with a fork. `glow` lights a thin line down their middles (lava, holy
   * light, runes), `ga` its strength.
   */
  private def cracks(n: Int, reach: Float, g0: Float, g1: Float, width: Float,
                     r: Float, g: Float, b: Float, a: Float,
                     glowR: Float = 0f, glowG: Float = 0f, glowB: Float = 0f, ga: Float = 0f, sd: Int = 0): Unit = {
    if (layer != GROUND) return
    val grow = easeOut(seg(T, g0, g1))
    val al = a * tail(T, 0.6f)
    if (grow <= 0.01f || al <= 0.01f) return
    val s0 = seed + sd * 131
    var i = 0
    while (i < n) {
      val th = (i + 0.5f * hash(s0, i * 7 + 1)) * TWO_PI / n
      val c = cosf(th); val s = sinf(th)
      val len = reach * (0.65f + 0.35f * h01(s0, i * 7 + 2)) * grow
      var j = 0
      while (j < 5) {
        val u = j / 4f
        val rho = 0.08f + (len - 0.08f) * u
        val off = if (j == 0) 0f else hash(s0 + i * 13, j) * 0.06f
        _px(j) = cx + (c * rho - s * off) * W
        _py(j) = cy + (s * rho + c * off) * H
        j += 1
      }
      if (len > 0.1f) {
        sb.strokePolylineTapered(_px, _py, 5, width * k, 0.7f, r, g, b, al, al * 0.6f)
        if (ga > 0f) sb.strokePolylineTapered(_px, _py, 5, width * 0.34f * k, 0.3f, glowR, glowG, glowB, ga * al, 0.2f * ga * al)
        // A fork off the third point, out to one side
        val side = if (hash(s0, i * 7 + 3) > 0f) 1f else -1f
        val fa = th + side * 0.6f
        val fl = len * 0.35f
        _xs(0) = _px(2); _ys(0) = _py(2)
        _xs(1) = _px(2) + (cosf(fa) * fl * 0.5f) * W + hash(s0, i * 7 + 4) * 2f
        _ys(1) = _py(2) + (sinf(fa) * fl * 0.5f) * H
        _xs(2) = _px(2) + cosf(fa + side * 0.2f) * fl * W
        _ys(2) = _py(2) + sinf(fa + side * 0.2f) * fl * H
        sb.strokePolylineTapered(_xs, _ys, 3, width * 0.6f * k, 0.5f, r, g, b, al * 0.9f, al * 0.4f)
      }
      i += 1
    }
  }

  // ── Debris ──
  final val D_CHIP = 0      // rock, dirt or metal: an inked triangle with a lit edge
  final val D_SHARD = 1     // glass or ice: a thin bright diamond
  final val D_SPLINTER = 2  // wood: a thin inked sliver
  final val D_BONE = 3      // a little bone with a knob at each end
  final val D_LEAF = 4      // a leaf with a midrib
  final val D_FEATHER = 5   // a feather with its quill
  final val D_DROP = 6      // a drop of liquid with a highlight
  final val D_GLOB = 7      // a fat inked blob with a gloss (lava, mud, sludge)
  final val D_NOTE = 8      // a music note
  final val D_SQUARE = 9    // a nanite, a spark of data
  final val D_SPARK = 10    // a hot streak along its flight, drawn additive
  final val D_HEX = 11      // a hexagonal fragment of something built
  final val D_BLOCK = 12    // a chunky block of stone
  final val D_FLECK = 13    // a tiny speck (spice, grit), no ink

  /**
   * `n` pieces flung out from the middle over [t0, t1] of the blast's life, each on its own
   * arc: out to `reach` of the footprint at most, `lift` units up at the top of the arc,
   * landing partway and fading by the end. `size` in units at the size of a 3-cell blast.
   * `shadow` lays a little shadow under the ones in the air, which is what shows their height.
   */
  private def debris(kind: Int, n0: Int, t0: Float, t1: Float, reach: Float, lift: Float, size: Float,
                     r: Float, g: Float, b: Float, sd: Int, shadow: Boolean = false, from: Float = 0.05f): Unit = {
    if (layer != AIR) return
    val p = seg(T, t0, t1)
    if (p <= 0f || p >= 1f) return
    val n = Math.max(1, (n0 * det + 0.5f).toInt)
    val s0 = seed * 7 + sd * 977
    val additive = kind == D_SPARK
    if (additive) sb.setAdditiveBlend(true)
    var i = 0
    while (i < n) {
      val hA = hash(s0, i * 5); val hB = h01(s0, i * 5 + 1); val hC = h01(s0, i * 5 + 2)
      val th = (i + 0.45f * hA) * TWO_PI / n
      val c = cosf(th); val s = sinf(th)
      val out = easeOut(Math.min(1f, p * 1.35f))
      val rho = from + (reach * (0.45f + 0.55f * hB) - from) * out
      val land = 0.5f + 0.35f * hC
      val tau = Math.min(1f, p / land)
      val z = lift * (0.5f + 0.5f * hB) * 4f * tau * (1f - tau)
      val x = gx(c, rho)
      val groundY = gy(s, rho)
      val y = groundY - z
      val a = if (p < land) 1f else 1f - (p - land) / (1f - land)
      val sz = size * k * (0.65f + 0.7f * hC)
      if (shadow && z > 3f && a > 0.05f) sb.fillOval(x, groundY, sz * 0.9f, sz * 0.36f, 0f, 0f, 0f, 0.2f * a, 8)
      if (additive) {
        // A streak back along where it has come from
        val q = Math.max(0f, p - 0.06f)
        val outQ = easeOut(Math.min(1f, q * 1.35f))
        val rq = from + (reach * (0.45f + 0.55f * hB) - from) * outQ
        val tq = Math.min(1f, q / land)
        val zq = lift * (0.5f + 0.5f * hB) * 4f * tq * (1f - tq)
        val bx = gx(c, rq); val by = gy(s, rq) - zq
        if (a > 0.03f) {
          sb.strokeLineSoft(x, y, bx, by, sz * 1.1f, r, g, b, 0.8f * a)
          sb.fillOval(x, y, sz * 0.55f, sz * 0.55f, mix(r, 1f, 0.6f), mix(g, 1f, 0.6f), mix(b, 1f, 0.6f), a, 6)
        }
      } else piece(kind, x, y, sz, hA * 3f + p * (5f + 7f * hB) * (if ((i & 1) == 0) 1f else -1f), r, g, b, a)
      i += 1
    }
    if (additive) sb.setAdditiveBlend(false)
  }

  /** Rotate the unit shape in (_xs, _ys) by (c, s), scale it by `sz` and put it at (x, y). */
  @inline private def place(n: Int, x: Float, y: Float, sz: Float, c: Float, s: Float): Unit = {
    var i = 0
    while (i < n) {
      val lx = _xs(i); val ly = _ys(i)
      _xs(i) = x + (lx * c - ly * s) * sz
      _ys(i) = y + (lx * s + ly * c) * sz
      i += 1
    }
  }

  /** One piece of debris, `sz` across, turned to `ang`. */
  private def piece(kind: Int, x: Float, y: Float, sz: Float, ang: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    if (a <= 0.02f) return
    val c = cosf(ang); val s = sinf(ang)
    (kind: @scala.annotation.switch) match {
      case D_CHIP | D_BLOCK =>
        val block = kind == D_BLOCK
        var pass = 0
        while (pass < 2) {
          val grow = if (pass == 0) 1.35f else 1f
          if (block) {
            _xs(0) = -0.9f; _ys(0) = -0.6f; _xs(1) = 0.8f; _ys(1) = -0.75f
            _xs(2) = 0.95f; _ys(2) = 0.6f; _xs(3) = -0.75f; _ys(3) = 0.7f
          } else {
            _xs(0) = 1f; _ys(0) = 0f; _xs(1) = -0.6f; _ys(1) = 0.78f; _xs(2) = -0.5f; _ys(2) = -0.72f
          }
          val m = if (block) 4 else 3
          place(m, x, y, sz * grow, c, s)
          if (pass == 0) sb.fillPolygon(_xs, _ys, m, ink(r), ink(g), ink(b), 0.9f * a)
          else {
            sb.fillPolygon(_xs, _ys, m, r, g, b, a)
            sb.strokeLine(_xs(0), _ys(0), _xs(1), _ys(1), 1.1f, lit(r), lit(g), lit(b), 0.85f * a)
          }
          pass += 1
        }
      case D_SHARD =>
        _xs(0) = 1.3f; _ys(0) = 0f; _xs(1) = 0f; _ys(1) = 0.36f; _xs(2) = -1f; _ys(2) = 0f; _xs(3) = 0f; _ys(3) = -0.36f
        place(4, x, y, sz, c, s)
        sb.strokePolygon(_xs, _ys, 4, 1.2f, ink(r), ink(g), ink(b), 0.7f * a)
        sb.fillPolygon(_xs, _ys, 4, r, g, b, 0.85f * a)
        sb.strokeLine(_xs(0), _ys(0), _xs(3), _ys(3), 0.9f, 1f, 1f, 1f, 0.9f * a)
      case D_SPLINTER =>
        _xs(0) = 1.4f; _ys(0) = 0f; _xs(1) = -1f; _ys(1) = 0.24f; _xs(2) = -1.2f; _ys(2) = -0.1f; _xs(3) = 0.1f; _ys(3) = -0.2f
        place(4, x, y, sz, c, s)
        sb.strokePolygon(_xs, _ys, 4, 1.3f, ink(r), ink(g), ink(b), 0.85f * a)
        sb.fillPolygon(_xs, _ys, 4, r, g, b, a)
      case D_BONE =>
        val hx = c * sz * 1.05f; val hy = s * sz * 1.05f
        sb.strokeLine(x - hx, y - hy, x + hx, y + hy, sz * 0.55f + 1.8f, 0.2f, 0.17f, 0.14f, 0.9f * a)
        sb.fillOval(x - hx, y - hy, sz * 0.42f + 0.9f, sz * 0.42f + 0.9f, 0.2f, 0.17f, 0.14f, 0.9f * a, 8)
        sb.fillOval(x + hx, y + hy, sz * 0.42f + 0.9f, sz * 0.42f + 0.9f, 0.2f, 0.17f, 0.14f, 0.9f * a, 8)
        sb.strokeLine(x - hx, y - hy, x + hx, y + hy, sz * 0.55f, r, g, b, a)
        sb.fillOval(x - hx, y - hy, sz * 0.42f, sz * 0.42f, r, g, b, a, 8)
        sb.fillOval(x + hx, y + hy, sz * 0.42f, sz * 0.42f, r, g, b, a, 8)
      case D_LEAF | D_FEATHER =>
        val len = if (kind == D_LEAF) 1.2f else 1.6f
        val wid = if (kind == D_LEAF) 0.52f else 0.36f
        // A lens: pointed at both ends, convex
        _xs(0) = len; _ys(0) = 0f; _xs(1) = len * 0.3f; _ys(1) = wid; _xs(2) = -len * 0.4f; _ys(2) = wid * 0.85f
        _xs(3) = -len; _ys(3) = 0f; _xs(4) = -len * 0.4f; _ys(4) = -wid * 0.85f; _xs(5) = len * 0.3f; _ys(5) = -wid
        place(6, x, y, sz, c, s)
        sb.strokePolygon(_xs, _ys, 6, 1.2f, ink(r), ink(g), ink(b), 0.85f * a)
        sb.fillPolygon(_xs, _ys, 6, r, g, b, a)
        val qx = c * sz * len * 1.25f; val qy = s * sz * len * 1.25f
        sb.strokeLine(x - qx, y - qy, x + qx * 0.8f, y + qy * 0.8f, 0.9f, ink(r) * 2f, ink(g) * 2f, ink(b) * 2f, 0.8f * a)
      case D_DROP =>
        sb.fillOval(x, y, sz * 0.62f + 1f, sz * 0.78f + 1f, ink(r), ink(g), ink(b), 0.75f * a, 8)
        sb.fillOval(x, y, sz * 0.62f, sz * 0.78f, r, g, b, a, 8)
        sb.fillOval(x - sz * 0.2f, y - sz * 0.28f, sz * 0.2f, sz * 0.24f, 1f, 1f, 1f, 0.8f * a, 6)
      case D_GLOB =>
        // A blob stretched a little along its spin, a tone lighter on its upper side, a small gloss
        val sx = sz * (1f + 0.18f * Math.abs(c)); val sy = sz * (0.84f + 0.14f * Math.abs(s))
        sb.fillOval(x, y, sx + 1.5f, sy + 1.5f, ink(r), ink(g), ink(b), 0.9f * a, 9)
        sb.fillOval(x, y, sx, sy, r, g, b, a, 9)
        sb.fillOval(x - sx * 0.14f, y - sy * 0.2f, sx * 0.66f, sy * 0.5f, mix(r, lit(r), 0.55f), mix(g, lit(g), 0.55f),
          mix(b, lit(b), 0.55f), a, 8)
        sb.fillOval(x - sx * 0.38f, y - sy * 0.4f, sx * 0.17f, sy * 0.13f, 1f, 1f, 0.92f, 0.75f * a, 6)
      case D_NOTE =>
        // A head, a stem and a flag
        val hx = x; val hy = y
        sb.fillOval(hx, hy, sz * 0.62f + 1.3f, sz * 0.46f + 1.3f, ink(r), ink(g), ink(b), 0.9f * a, 9)
        sb.strokeLine(hx + sz * 0.5f, hy, hx + sz * 0.5f, hy - sz * 2.1f, 2.6f, ink(r), ink(g), ink(b), 0.9f * a)
        sb.fillOval(hx, hy, sz * 0.62f, sz * 0.46f, r, g, b, a, 9)
        sb.strokeLine(hx + sz * 0.5f, hy, hx + sz * 0.5f, hy - sz * 2.1f, 1.3f, r, g, b, a)
        _xs(0) = hx + sz * 0.5f; _ys(0) = hy - sz * 2.1f
        _xs(1) = hx + sz * 1.25f; _ys(1) = hy - sz * 1.35f
        _xs(2) = hx + sz * 0.5f; _ys(2) = hy - sz * 1.45f
        sb.fillPolygon(_xs, _ys, 3, r, g, b, a)
      case D_SQUARE =>
        _xs(0) = -0.7f; _ys(0) = -0.7f; _xs(1) = 0.7f; _ys(1) = -0.7f; _xs(2) = 0.7f; _ys(2) = 0.7f; _xs(3) = -0.7f; _ys(3) = 0.7f
        place(4, x, y, sz, c, s)
        sb.fillPolygon(_xs, _ys, 4, r, g, b, a)
        sb.fillOval(x, y, sz * 0.3f, sz * 0.3f, lit(r), lit(g), lit(b), a, 6)
      case D_HEX =>
        sb.fillOval(x, y, sz + 1.2f, sz * 0.8f + 1.2f, ink(r), ink(g), ink(b), 0.85f * a, 6)
        sb.fillOval(x, y, sz, sz * 0.8f, r, g, b, a, 6)
        sb.fillOval(x - sz * 0.2f, y - sz * 0.2f, sz * 0.4f, sz * 0.3f, lit(r), lit(g), lit(b), a, 6)
      case _ => // D_FLECK
        sb.fillOval(x, y, sz * 0.5f, sz * 0.4f, r, g, b, a, 5)
    }
  }

  /**
   * A cluster of `n` puffs — smoke, dust, steam, gas — inked as one mass: each rises `rise`
   * units and swells from `s0` to `s1` units (at the size of a 3-cell blast) over its own part
   * of [t0, t1], thinning away. They start `spread` of the footprint out and drift `outward`
   * further; `flat` squashes them (dust along the ground), `lift` raises the whole cluster.
   * Drawn ink, body, light, so the line runs round the mass and not across every seam.
   */
  private def puffs(n0: Int, t0: Float, t1: Float, spread: Float, rise: Float, s0: Float, s1: Float,
                    r: Float, g: Float, b: Float, a0: Float, sd: Int,
                    outward: Float = 0f, flat: Float = 0.8f, lift: Float = 0f, inkK: Float = 1f): Unit = {
    if (layer != AIR) return
    if (T <= t0 || T >= t1) return
    val n = Math.min(64, Math.max(1, (n0 * (0.5f + 0.5f * det) + 0.5f).toInt))
    val s00 = seed * 3 + sd * 541
    var m = 0
    var i = 0
    while (i < n) {
      val hA = hash(s00, i * 3); val hB = h01(s00, i * 3 + 1); val hC = h01(s00, i * 3 + 2)
      val start = t0 + (t1 - t0) * 0.22f * hC
      val tau = seg(T, start, t1)
      if (tau > 0f && tau < 1f) {
        val th = (i + 0.5f * hA) * TWO_PI / n
        val c = cosf(th); val s = sinf(th)
        val e = easeOut(tau)
        val rho = spread * (0.25f + 0.75f * hB) + outward * e
        _ux(m) = gx(c, rho)
        _uy(m) = gy(s, rho) - lift * k - rise * k * e * (0.65f + 0.35f * hB)
        _us(m) = (s0 + (s1 - s0) * e) * k * (0.75f + 0.5f * hC)
        _ua(m) = a0 * Math.min(1f, tau * 7f) * (1f - tau) * (1f - 0.4f * tau)
        m += 1
      }
      i += 1
    }
    if (m == 0) return
    val ir = mix(r, ink(r), 0.8f); val ig = mix(g, ink(g), 0.8f); val ib = mix(b, ink(b), 0.8f)
    // The lit side is a tone up, not a highlight: lit hard, every puff was a glass ball
    val lr = mix(r, lit(r), 0.5f); val lg = mix(g, lit(g), 0.5f); val lb = mix(b, lit(b), 0.5f)
    var pass = 0
    while (pass < 3) {
      i = 0
      while (i < m) {
        val x = _ux(i); val y = _uy(i); val sz = _us(i); val a = _ua(i)
        (pass: @scala.annotation.switch) match {
          case 0 => sb.fillOval(x, y, sz + 1.8f, sz * flat + 1.8f, ir, ig, ib, 0.55f * a * inkK, 18)
          case 1 => sb.fillOval(x, y, sz, sz * flat, r, g, b, a, 18)
          case _ => sb.fillOval(x - sz * 0.16f, y - sz * 0.2f * flat, sz * 0.72f, sz * 0.62f * flat, lr, lg, lb, 0.9f * a, 14)
        }
        i += 1
      }
      pass += 1
    }
  }

  /** A tongue of flame standing at (bx, by), `h` tall and `w` half-wide at its foot, its tip
    * `lean` to the side: an inked teardrop, its colour, and a hotter heart. */
  private def flameTongue(bx: Float, by: Float, w: Float, h: Float, lean: Float, a: Float,
                          or: Float, og: Float, ob: Float, hr: Float, hg: Float, hb: Float,
                          tr: Float = -1f, tg: Float = 0f, tb: Float = 0f): Unit = {
    if (h < 1.5f || a <= 0.02f) return
    val l = Math.max(-w * 0.55f, Math.min(w * 0.55f, lean))
    tongue(bx, by, w + 1.4f, h + 2.2f, l, ink(or), ink(og), ink(ob), 0.85f * a)
    tongue(bx, by, w, h, l, or, og, ob, a)
    tongue(bx, by + w * 0.12f, w * 0.52f, h * 0.6f, l * 0.6f, hr, hg, hb, a)
    // A tip of another colour standing in its top half (the orange over a blue flame)
    if (tr >= 0f) tongue(bx + l * 0.3f, by - h * 0.34f, w * 0.7f, h * 0.66f, l * 0.7f, tr, tg, tb, a)
  }

  private def tongue(bx: Float, by: Float, w: Float, h: Float, lean: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    _xs(0) = bx - w; _ys(0) = by
    _xs(1) = bx - w * 0.8f + lean * 0.3f; _ys(1) = by - h * 0.4f
    _xs(2) = bx + lean; _ys(2) = by - h
    _xs(3) = bx + w * 0.8f + lean * 0.3f; _ys(3) = by - h * 0.4f
    _xs(4) = bx + w; _ys(4) = by
    _xs(5) = bx; _ys(5) = by + w * 0.34f
    sb.fillPolygon(_xs, _ys, 6, r, g, b, a)
  }

  /**
   * A spike standing up out of the ground at (bx, by): `h` tall, `w` half-wide at its foot, its
   * tip `lean` to the side. A lit face and a shaded face meeting along its ridge, inked round.
   * `glass` makes it translucent with a white edge (ice, crystal).
   */
  private def spike(bx: Float, by: Float, w: Float, h: Float, lean: Float, a: Float,
                    r: Float, g: Float, b: Float, glass: Boolean = false): Unit = {
    if (h < 1.5f || a <= 0.02f) return
    val tx = bx + lean; val ty = by - h
    _xs(0) = bx - w - 1.4f; _ys(0) = by + 0.6f
    _xs(1) = tx; _ys(1) = ty - 2.2f
    _xs(2) = bx + w + 1.4f; _ys(2) = by + 0.6f
    _xs(3) = bx + w * 0.1f; _ys(3) = by + w * 0.42f + 1.4f
    sb.fillPolygon(_xs, _ys, 4, ink(r), ink(g), ink(b), (if (glass) 0.7f else 0.9f) * a)
    val body = if (glass) 0.82f else 1f
    _xs(0) = bx - w; _ys(0) = by
    _xs(1) = tx; _ys(1) = ty
    _xs(2) = bx + w * 0.1f; _ys(2) = by + w * 0.42f
    sb.fillPolygon(_xs, _ys, 3, mix(r, lit(r), 0.6f), mix(g, lit(g), 0.6f), mix(b, lit(b), 0.6f), body * a)
    _xs(0) = bx + w * 0.1f; _ys(0) = by + w * 0.42f
    _xs(1) = tx; _ys(1) = ty
    _xs(2) = bx + w; _ys(2) = by
    sb.fillPolygon(_xs, _ys, 3, r * 0.72f, g * 0.72f, b * 0.78f, body * a)
    if (glass) sb.strokeLine(bx - w * 0.55f, by - h * 0.1f, tx - w * 0.05f, ty + h * 0.12f, 1.3f, 1f, 1f, 1f, 0.75f * a)
  }

  /**
   * A tendril growing out of the ground at (bx, by): heading off at screen angle `ang0`
   * (-π/2 is straight up), `len` long when `grow` is 1, curling by `curl` radians toward its tip.
   * Inked, then its colour, then a lit line along one side. Returns nothing; its points are
   * left in _px/_py for anything that hangs on it (leaves, suckers, thorns).
   */
  private def tendril(bx: Float, by: Float, ang0: Float, len: Float, grow: Float, curl: Float, w0: Float,
                      r: Float, g: Float, b: Float, a: Float, n: Int = 9, wisp: Boolean = false,
                      hr: Float = -1f, hg: Float = 0f, hb: Float = 0f): Unit = {
    if (grow <= 0.02f || a <= 0.02f) return
    var ang = ang0
    var x = bx; var y = by
    val step = len * grow / (n - 1)
    var i = 0
    while (i < n) {
      val u = i.toFloat / (n - 1)
      _px(i) = x; _py(i) = y
      _pw(i) = w0 * (1f - 0.88f * u) + 0.6f
      _pa(i) = if (wisp) a * (1f - u * u) else a
      ang += curl * (0.25f + 1.5f * u) / (n - 1)
      x += cosf(ang) * step
      y += sinf(ang) * step
      i += 1
    }
    var j = 0
    while (j < n) { _pw(j) += 2.6f; _pa(j) *= 0.85f; j += 1 }
    sb.strokePolylineVar(_px, _py, _pw, _pa, n, ink(r), ink(g), ink(b))
    j = 0
    while (j < n) { _pw(j) -= 2.6f; _pa(j) /= 0.85f; j += 1 }
    sb.strokePolylineVar(_px, _py, _pw, _pa, n, r, g, b)
    j = 0
    while (j < n) { _pw(j) *= 0.32f; _pa(j) *= 0.8f; j += 1 }
    if (hr < 0f) sb.strokePolylineVar(_px, _py, _pw, _pa, n, lit(r), lit(g), lit(b))
    else sb.strokePolylineVar(_px, _py, _pw, _pa, n, hr, hg, hb)
  }

  /** A zigzag of electricity from (x0, y0) to (x1, y1): `segs` kinks up to `amp` aside, the same
    * for the same seed. Ink, colour and a white core, thinning toward the far end. */
  private def bolt(x0: Float, y0: Float, x1: Float, y1: Float, segs: Int, amp: Float, sd: Int,
                   w: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    if (a <= 0.01f) return
    val dx = x1 - x0; val dy = y1 - y0
    val len = Math.sqrt(dx * dx + dy * dy).toFloat
    if (len < 1f) return
    val nx = -dy / len; val ny = dx / len
    val n = Math.min(segs, 14) + 1
    var i = 0
    while (i < n) {
      val t = i.toFloat / (n - 1)
      // Alternate sides by an uneven amount: the lightning-bolt zigzag, never a smooth worm
      val side = if ((i & 1) == 0) 1f else -1f
      val off = if (i == 0 || i == n - 1) 0f else side * (0.35f + 0.65f * h01(sd, i)) * amp
      _px(i) = x0 + dx * t + nx * off; _py(i) = y0 + dy * t + ny * off
      i += 1
    }
    sb.strokePolylineTapered(_px, _py, n, w + 3f, 2f, ink(r), ink(g), ink(b) + 0.05f, 0.75f * a, 0.3f * a)
    sb.strokePolylineTapered(_px, _py, n, w, w * 0.35f, r, g, b, 0.95f * a, 0.5f * a)
    sb.strokePolylineTapered(_px, _py, n, w * 0.42f, 0.5f, mix(r, 1f, 0.8f), mix(g, 1f, 0.8f), mix(b, 1f, 0.8f), a, 0.5f * a)
  }

  /**
   * A burnt branching scar on the ground, the mark lightning leaves (a Lichtenberg figure):
   * `n` branches out to `reach`, each forking twice. Dark, with a line of the bolt's colour down
   * it that fades well before the scar does.
   */
  private def scar(n: Int, reach: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    if (layer != GROUND) return
    val grow = easeOut(seg(T, 0f, 0.12f))
    val al = a * tail(T, 0.55f)
    val glow = a * (1f - seg(T, 0.05f, 0.45f))
    if (al <= 0.01f) return
    var i = 0
    while (i < n) {
      val s0 = seed * 11 + i * 71
      var ang = (i + 0.4f * hash(s0, 1)) * TWO_PI / n
      var x = cx; var y = cy
      val step = reach * grow / 5f
      var j = 0
      while (j < 6) {
        _px(j) = x; _py(j) = y
        ang += hash(s0, j + 2) * 0.55f
        x += cosf(ang) * step * W
        y += sinf(ang) * step * H
        j += 1
      }
      sb.strokePolylineTapered(_px, _py, 6, 3.4f * k, 0.6f, 0.08f, 0.07f, 0.06f, 0.55f * al, 0.25f * al)
      if (glow > 0.01f) sb.strokePolylineTapered(_px, _py, 6, 1.4f * k, 0.3f, r, g, b, glow, 0.2f * glow)
      // Two twigs off it
      var f = 0
      while (f < 2) {
        val at = 2 + f * 2
        val fa = ang + (if ((f & 1) == 0) 0.9f else -0.9f) + hash(s0, 20 + f) * 0.3f
        val fl = step * 1.3f
        val ex = _px(at) + cosf(fa) * fl * W; val ey = _py(at) + sinf(fa) * fl * H
        sb.strokeLine(_px(at), _py(at), ex, ey, 1.8f * k, 0.08f, 0.07f, 0.06f, 0.45f * al)
        if (glow > 0.01f) sb.strokeLine(_px(at), _py(at), ex, ey, 0.8f * k, r, g, b, 0.7f * glow)
        f += 1
      }
      i += 1
    }
  }

  /** Rings of sound: `n` rings, each a set of `arcs` arcs with gaps between, going out one after
    * another over [t0, t1] to `reach`, turning a little as they go. */
  private def soundRings(n: Int, arcs: Int, t0: Float, t1: Float, reach: Float, w0: Float,
                         r: Float, g: Float, b: Float, a0: Float, lift: Float = 0f): Unit = {
    if (layer != GROUND) return
    var i = 0
    while (i < n) {
      val start = t0 + (t1 - t0) * 0.5f * i / Math.max(1, n - 1)
      val p = seg(T, start, start + (t1 - t0) * 0.5f)
      if (p > 0f && p < 1f) {
        val q = easeOut(p) * reach
        val rx = W * q; val ry = H * q
        val a = a0 * (1f - p) * Math.min(1f, p * 8f)
        val lw = w0 * k * (1f - 0.5f * p)
        val rot = hash(seed, i) * PI + p * 0.6f
        val span = TWO_PI / arcs
        var j = 0
        while (j < arcs) {
          val st = rot + j * span
          sb.strokeArc(cx, cy - lift * k, rx, ry, st, span * 0.72f, lw + 2.6f, ink(r), ink(g), ink(b), 0.5f * a, 10)
          sb.strokeArc(cx, cy - lift * k, rx, ry, st, span * 0.72f, lw, r, g, b, a, 10)
          j += 1
        }
      }
      i += 1
    }
  }

  /** Motes: `n` little lights rising `rise` units and drifting over [t0, t1], additive. */
  private def motes(n0: Int, t0: Float, t1: Float, spread: Float, rise: Float, size: Float,
                    r: Float, g: Float, b: Float, a0: Float, sd: Int): Unit = {
    if (layer != AIR || T <= t0 || T >= t1) return
    val n = Math.max(1, (n0 * det + 0.5f).toInt)
    sb.setAdditiveBlend(true)
    var i = 0
    while (i < n) {
      val s0 = seed * 13 + sd * 97
      val start = t0 + (t1 - t0) * 0.4f * h01(s0, i * 3)
      val tau = seg(T, start, t1)
      if (tau > 0f && tau < 1f) {
        val th = (i + 0.5f * hash(s0, i * 3 + 1)) * TWO_PI / n
        val rho = spread * (0.2f + 0.8f * h01(s0, i * 3 + 2))
        val x = gx(cosf(th), rho) + sinf(tau * 5f + i) * 3f * k
        val y = gy(sinf(th), rho) - rise * k * easeOut(tau)
        val a = a0 * Math.min(1f, tau * 6f) * (1f - tau)
        val sz = size * k * (1f - 0.4f * tau)
        sb.fillOval(x, y, sz * 2.2f, sz * 2.2f, r, g, b, 0.3f * a, 8)
        sb.fillOval(x, y, sz, sz, mix(r, 1f, 0.5f), mix(g, 1f, 0.5f), mix(b, 1f, 0.5f), a, 6)
      }
      i += 1
    }
    sb.setAdditiveBlend(false)
  }

  /** `n` pieces whirled out and up round the middle over [t0, t1]: each turning `turns` times
    * round as it goes out to `reach`, rising to `lift` units and settling as it fades. Leaves,
    * feathers, nanites — what a gust or a swarm carries rather than what a blast throws. */
  private def swirl(kind: Int, n0: Int, t0: Float, t1: Float, reach: Float, lift: Float, turns: Float, size: Float,
                    r: Float, g: Float, b: Float, sd: Int): Unit = {
    if (layer != AIR) return
    val p = seg(T, t0, t1)
    if (p <= 0f || p >= 1f) return
    val n = Math.max(1, (n0 * det + 0.5f).toInt)
    val s0 = seed * 83 + sd * 29
    var i = 0
    while (i < n) {
      val hB = h01(s0, i * 3); val hC = h01(s0, i * 3 + 1)
      val tau = seg(p, 0.12f * hC, 1f)
      if (tau > 0f && tau < 1f) {
        val dir = if ((i & 1) == 0) 1f else 0.8f
        val th = (i + 0.5f * hash(s0, i * 3 + 2)) * TWO_PI / n + turns * TWO_PI * easeOut(tau) * dir
        val rho = reach * (0.35f + 0.65f * hB) * easeOut3(Math.min(1f, tau * 1.6f))
        val z = lift * (0.6f + 0.4f * hC) * sinf(Math.min(1f, tau * 1.25f) * PI * 0.9f + 0.1f)
        val a = Math.min(1f, tau * 8f) * (1f - smooth(seg(tau, 0.6f, 1f)))
        piece(kind, gx(cosf(th), rho), gy(sinf(th), rho) - z * k, size * k * (0.75f + 0.5f * hB),
          th * 2.2f + tau * 9f, r, g, b, a)
      }
      i += 1
    }
  }

  /** A star flare twinkling somewhere on the blast for a moment in [t0, t1]. */
  private def glints(n: Int, t0: Float, t1: Float, spread: Float, lift: Float, size: Float,
                     r: Float, g: Float, b: Float, a0: Float): Unit = {
    if (layer != AIR || T <= t0 || T >= t1) return
    var i = 0
    while (i < n) {
      val s0 = seed * 17 + i * 29
      val start = t0 + (t1 - t0) * 0.7f * h01(s0, 1)
      val p = seg(T, start, start + (t1 - t0) * 0.3f)
      if (p > 0f && p < 1f) {
        val th = h01(s0, 2) * TWO_PI
        val rho = spread * (0.2f + 0.8f * h01(s0, 3))
        val x = gx(cosf(th), rho); val y = gy(sinf(th), rho) - lift * k * h01(s0, 4)
        val a = a0 * (if (p < 0.3f) p / 0.3f else (1f - p) / 0.7f)
        sb.fillStarFlare(x, y, size * k * (0.6f + 0.4f * a), 2.2f, p * 1.5f, 0.5f, r, g, b, a)
      }
      i += 1
    }
  }

  /** A splat on the ground: a blob with a ragged edge (a fan, since its outline is a radius per
    * point), inked, with a gloss and drops round it. `size` of the footprint. */
  private def splat(size: Float, grow: Float, r: Float, g: Float, b: Float, a: Float, sd: Int,
                    glossA: Float = 0.6f, drops: Int = 7): Unit = {
    if (layer != GROUND || a <= 0.01f || grow <= 0.01f) return
    val s0 = seed * 5 + sd * 313
    val n = 28
    // Lobes: the sum of a few waves round it, each with its own phase, and a little grain
    val p3 = hash(s0, 1) * PI; val p5 = hash(s0, 2) * PI; val p7 = hash(s0, 3) * PI
    var pass = 0
    while (pass < 2) {
      val extra = if (pass == 0) 1.8f else 0f
      var i = 0
      while (i < n) {
        val th = i * TWO_PI / n
        val wob = 0.14f * sinf(3f * th + p3) + 0.09f * sinf(5f * th + p5) + 0.05f * sinf(7f * th + p7)
        val rr = size * grow * (0.9f + wob + 0.03f * hash(s0, 10 + i))
        _xs(i) = cx + cosf(th) * (rr * W + extra)
        _ys(i) = cy + sinf(th) * (rr * H + extra * 0.6f)
        i += 1
      }
      if (pass == 0) sb.fillFan(cx, cy, _xs, _ys, n, ink(r), ink(g), ink(b), 0.85f * a)
      else sb.fillFan(cx, cy, _xs, _ys, n, r, g, b, a)
      pass += 1
    }
    // Drops flung round it
    var d = 0
    while (d < drops) {
      val th = (d + 0.5f * hash(s0, 40 + d)) * TWO_PI / drops
      val rho = size * grow * (1.1f + 0.45f * h01(s0, 50 + d))
      val x = gx(cosf(th), rho); val y = gy(sinf(th), rho)
      val ds = (2.2f + 2.6f * h01(s0, 60 + d)) * k * grow
      sb.fillOval(x, y, ds + 1.2f, ds * 0.6f + 1.2f, ink(r), ink(g), ink(b), 0.8f * a, 8)
      sb.fillOval(x, y, ds, ds * 0.6f, r, g, b, a, 8)
      d += 1
    }
    if (glossA > 0f) {
      sb.fillOval(cx - size * W * 0.25f, cy - size * H * 0.28f, size * W * 0.3f * grow, size * H * 0.16f * grow,
        lit(r), lit(g), lit(b), glossA * a, 12)
      sb.fillOval(cx + size * W * 0.2f, cy + size * H * 0.1f, size * W * 0.12f * grow, size * H * 0.07f * grow,
        lit(r), lit(g), lit(b), glossA * 0.7f * a, 8)
    }
  }

  /** Bubbles popping on a surface: each swells for a moment and bursts into a ring. */
  private def bubbles(n: Int, t0: Float, t1: Float, spread: Float, size: Float,
                      r: Float, g: Float, b: Float, a0: Float, sd: Int): Unit = {
    if (layer != AIR || T <= t0 || T >= t1) return
    var i = 0
    while (i < n) {
      val s0 = seed * 19 + sd * 7 + i * 31
      val start = t0 + (t1 - t0) * 0.75f * h01(s0, 1)
      val p = seg(T, start, start + 0.18f)
      if (p > 0f && p < 1f) {
        val th = h01(s0, 2) * TWO_PI
        val rho = spread * Math.sqrt(h01(s0, 3)).toFloat
        val x = gx(cosf(th), rho); val y = gy(sinf(th), rho)
        val sz = size * k * (0.6f + 0.6f * h01(s0, 4))
        if (p < 0.7f) {
          val q = p / 0.7f
          val bs = sz * (0.4f + 0.6f * q)
          sb.strokeOval(x, y - bs * 0.6f, bs, bs * 0.9f, 1.3f, ink(r), ink(g), ink(b), 0.7f * a0, 10)
          sb.fillOval(x, y - bs * 0.6f, bs, bs * 0.9f, r, g, b, 0.55f * a0, 10)
          sb.fillOval(x - bs * 0.3f, y - bs * 0.95f, bs * 0.25f, bs * 0.2f, 1f, 1f, 1f, 0.8f * a0, 6)
        } else {
          val q = (p - 0.7f) / 0.3f
          sb.strokeOval(x, y - sz * 0.6f, sz * (1f + q * 0.8f), sz * (0.9f + q * 0.6f), 1.2f, lit(r), lit(g), lit(b), a0 * (1f - q), 10)
        }
      }
      i += 1
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  EXPLOSIONS
  // ═══════════════════════════════════════════════════════════════════

  // Heat ramps, hottest first: white-hot, yellow, orange, red, smoke. A fireball's lobes run down
  // one as they cool.
  private val PAL_FIRE = Array(1f, 0.97f, 0.82f, 1f, 0.82f, 0.3f, 0.98f, 0.46f, 0.12f, 0.72f, 0.16f, 0.07f, 0.27f, 0.24f, 0.23f)
  private val PAL_PLASMA = Array(0.92f, 1f, 1f, 0.45f, 0.92f, 1f, 0.2f, 0.55f, 1f, 0.16f, 0.22f, 0.6f, 0.36f, 0.42f, 0.52f)
  private val PAL_EMP = Array(0.95f, 0.98f, 1f, 0.55f, 0.8f, 1f, 0.3f, 0.48f, 0.95f, 0.3f, 0.34f, 0.52f, 0.34f, 0.36f, 0.42f)
  private val PAL_CANNON = Array(1f, 0.95f, 0.75f, 1f, 0.66f, 0.25f, 0.62f, 0.48f, 0.4f, 0.64f, 0.62f, 0.6f, 0.76f, 0.75f, 0.73f)
  private val PAL_NAPALM = Array(1f, 0.95f, 0.7f, 1f, 0.7f, 0.2f, 0.95f, 0.38f, 0.08f, 0.5f, 0.12f, 0.05f, 0.13f, 0.11f, 0.11f)
  private val PAL_INFERNO = Array(1f, 1f, 0.9f, 1f, 0.86f, 0.36f, 1f, 0.5f, 0.12f, 0.56f, 0.14f, 0.06f, 0.28f, 0.2f, 0.2f)
  private val PAL_ERUPTION = Array(1f, 0.9f, 0.6f, 1f, 0.6f, 0.15f, 0.85f, 0.25f, 0.05f, 0.36f, 0.1f, 0.05f, 0.22f, 0.2f, 0.2f)
  private var _hr = 0f
  private var _hg = 0f
  private var _hb = 0f

  /** The colour `heat` of the way up a heat ramp (0 smoke, 1 white-hot), into _hr/_hg/_hb. */
  private def heatColor(pal: Array[Float], heat: Float): Unit = {
    val x = (1f - clamp01(heat)) * 4f
    val i = Math.min(3, x.toInt)
    val f = x - i
    val o = i * 3
    _hr = mix(pal(o), pal(o + 3), f); _hg = mix(pal(o + 1), pal(o + 4), f); _hb = mix(pal(o + 2), pal(o + 5), f)
  }

  private val _uh = new Array[Float](16)

  /**
   * The fireball: `n` lobes swelling out of the middle and rising `rise` units, each white-hot at
   * first and cooling down its heat ramp to smoke by `end`, the outer ones first. `size` of the
   * footprint across. Drawn ink, body, heart, so the cartoon line runs round the whole mass.
   */
  private def fireball(n0: Int, size: Float, end: Float, rise: Float, pal: Array[Float], lift: Float = 0.12f): Unit = {
    if (layer != AIR || T >= end) return
    val n = Math.min(12, n0)
    val grow = easeOut3(seg(T, 0f, 0.16f))
    val p = T / end
    val fade = tail(p, 0.55f)
    // Cooling, it comes apart: the lobes drift away from each other and shrink as they go to smoke
    val apart = 1f + 0.7f * smooth(seg(p, 0.35f, 1f))
    val shrink = 1f - 0.35f * smooth(seg(p, 0.55f, 1f))
    val baseY = cy - H * lift - rise * k * easeOut(p)
    var i = 0
    while (i < n) {
      val d = if (i == 0) 0f else 0.55f + 0.35f * h01(seed, 200 + i)
      val th = if (i == 0) 0f else (i - 1 + 0.4f * hash(seed, 210 + i)) * TWO_PI / (n - 1)
      val rr = W * size * (if (i == 0) 0.62f else 0.4f + 0.14f * h01(seed, 220 + i)) * grow * (1f + 0.22f * p) * shrink
      _ux(i) = cx + cosf(th) * d * W * size * 0.5f * grow * apart
      // Lobes on top rise faster: the whole thing is a column of hot air
      _uy(i) = baseY + sinf(th) * d * W * size * 0.36f * grow * apart - (if (sinf(th) < 0f) rise * 0.45f * k * p else 0f)
      _us(i) = rr
      _uh(i) = 1f - seg(T, 0.015f + 0.05f * d, end * (0.8f + 0.2f * (1f - d)))
      _ua(i) = fade
      i += 1
    }
    var pass = 0
    while (pass < 3) {
      i = 0
      while (i < n) {
        val x = _ux(i); val y = _uy(i); val rr = _us(i); val a = _ua(i)
        (pass: @scala.annotation.switch) match {
          case 0 =>
            heatColor(pal, _uh(i) * 0.6f)
            sb.fillOval(x, y, rr + 2.4f, rr * 0.9f + 2.4f, _hr * 0.3f, _hg * 0.24f, _hb * 0.24f, 0.9f * a, 20)
          case 1 =>
            heatColor(pal, _uh(i))
            sb.fillOval(x, y, rr, rr * 0.9f, _hr, _hg, _hb, a, 20)
          case _ =>
            heatColor(pal, Math.min(1f, _uh(i) + 0.3f))
            sb.fillOval(x + rr * 0.08f, y + rr * 0.14f, rr * 0.56f, rr * 0.48f, _hr, _hg, _hb, a * Math.min(1f, _uh(i) * 2.5f), 16)
        }
        i += 1
      }
      pass += 1
    }
  }

  /**
   * A comic starburst — the KA-BOOM shape: a ten-point star popping out over the first tenth of
   * the blast, overshooting and settling, then shrinking away by `end`. Yellow round a red star
   * round a white heart, inked. Upright, facing the camera, at (x, y), `size` of the footprint.
   */
  private def starburst(x: Float, y: Float, size: Float, t0: Float, end: Float, points: Int, sd: Int): Unit = {
    if (layer != AIR) return
    val p = seg(T, t0, end)
    if (p <= 0f || p >= 1f) return
    val popIn = seg(p, 0f, 0.28f)
    // Overshoot and settle, about 0 → 1.1 → 1, then shrink as it goes
    val pop = if (popIn < 1f) easeOut3(popIn) + 0.16f * sinf(popIn * PI) else 1f - 0.35f * smooth(seg(p, 0.5f, 1f))
    val a = tail(p, 0.62f)
    val rOut = W * size * pop
    val rot = hash(seed, sd) * 0.5f + p * 0.3f
    val n = Math.min(14, points) * 2
    var layerI = 0
    while (layerI < 4) {
      val scale = if (layerI < 2) 1f else if (layerI == 2) 0.64f else 0.3f
      val extra = if (layerI == 0) 2.8f else 0f
      var i = 0
      while (i < n) {
        val th = rot + i * TWO_PI / n
        val out = if ((i & 1) == 0) 1f else 0.56f + 0.08f * hash(seed + sd, i)
        val rr = rOut * scale * out + extra
        _xs(i) = x + cosf(th) * rr
        _ys(i) = y + sinf(th) * rr * 0.86f
        i += 1
      }
      (layerI: @scala.annotation.switch) match {
        case 0 => sb.fillFan(x, y, _xs, _ys, n, 0.32f, 0.08f, 0.04f, 0.92f * a)
        case 1 => sb.fillFan(x, y, _xs, _ys, n, 1f, 0.84f, 0.2f, a)
        case 2 => sb.fillFan(x, y, _xs, _ys, n, 1f, 0.42f, 0.1f, a)
        case _ => sb.fillFan(x, y, _xs, _ys, n, 1f, 0.98f, 0.85f, a)
      }
      layerI += 1
    }
  }

  /** A bomblet going off at ground point (x, y) at `t0`: a small starburst, its own scorch and
    * ring, and a puff of black smoke. */
  private def bomblet(x: Float, y: Float, t0: Float, size: Float, sd: Int): Unit = {
    val p = seg(T, t0, t0 + 0.34f)
    if (p <= 0f || p >= 1f) return
    if (layer == GROUND) {
      val sa = 0.5f * Math.min(1f, p * 8f) * tail(p, 0.5f)
      sb.fillOvalSoft(x, y, W * size * 1.1f, H * size * 1.1f, 0.07f, 0.05f, 0.04f, sa, 0f, 14)
      val q = easeOut3(seg(p, 0f, 0.5f))
      val ra = (1f - seg(p, 0f, 0.5f)) * 0.8f
      if (ra > 0.01f) {
        sb.strokeOval(x, y, W * size * 1.6f * q, H * size * 1.6f * q, 5f, 0.3f, 0.2f, 0.05f, 0.5f * ra, 14)
        sb.strokeOval(x, y, W * size * 1.6f * q, H * size * 1.6f * q, 2.5f, 1f, 0.82f, 0.3f, ra, 14)
      }
    } else {
      val keepT = T; T = p
      starburst(x, y - H * size * 0.5f, size * 1.4f, 0f, 0.55f, 8, sd)
      T = keepT
      val sp = seg(p, 0.15f, 1f)
      if (sp > 0f && sp < 1f) {
        val sa = (1f - sp) * Math.min(1f, sp * 6f) * 0.85f
        val py = y - H * size * 0.4f - 18f * k * easeOut(sp)
        val ps = W * size * (0.35f + 0.5f * easeOut(sp))
        sb.fillOval(x, py, ps + 1.8f, ps * 0.8f + 1.8f, 0.05f, 0.05f, 0.06f, 0.7f * sa, 14)
        sb.fillOval(x, py, ps, ps * 0.8f, 0.2f, 0.19f, 0.22f, sa, 14)
        sb.fillOval(x - ps * 0.22f, py - ps * 0.22f, ps * 0.55f, ps * 0.42f, 0.36f, 0.35f, 0.38f, 0.85f * sa, 12)
      }
    }
  }

  /** The Pirate's cannonball, bowled out of its own blast: bouncing twice and rolling to a stop. */
  private def cannonball(): Unit = {
    if (layer != AIR) return
    val p = seg(T, 0.02f, 0.9f)
    if (p <= 0f || p >= 1f) return
    val th = hash(seed, 77) * PI
    val c = cosf(th); val s = sinf(th)
    val rho = 0.12f + 0.62f * easeOut(p)
    val bounce = Math.abs(sinf(p * PI * 2.2f)) * 14f * k * (1f - p) * (1f - p)
    val x = gx(c, rho); val groundY = gy(s, rho)
    val y = groundY - 4.5f * k - bounce
    val a = tail(p, 0.8f)
    val br = 4.6f * k
    sb.fillOval(x, groundY, br * 1.1f, br * 0.42f, 0f, 0f, 0f, 0.3f * a, 10)
    sb.fillOval(x, y, br + 1.6f, br + 1.6f, 0.03f, 0.03f, 0.04f, 0.95f * a, 14)
    sb.fillOval(x, y, br, br, 0.2f, 0.2f, 0.23f, a, 14)
    sb.fillOval(x - br * 0.32f, y - br * 0.34f, br * 0.34f, br * 0.28f, 0.6f, 0.6f, 0.65f, 0.9f * a, 8)
  }

  /** A pool of lava on the ground, bright when it lands and cooling to a dark crust, its cracks
    * still glowing. `hot` and `crust` are the two ends of its colour. */
  private def lavaPool(size: Float, hr: Float, hg: Float, hb: Float, cr: Float, cg: Float, cb: Float, sd: Int): Unit = {
    if (layer != GROUND) return
    val cool = smooth(seg(T, 0.2f, 0.85f))
    val grow = easeOut3(seg(T, 0f, 0.14f))
    val a = tail(T, 0.72f)
    splat(size, grow, mix(hr, cr, cool), mix(hg, cg, cool), mix(hb, cb, cool), a, sd, glossA = 0f, drops = 6)
    // The heart of it stays hot longest
    val ha = (1f - cool) * a
    if (ha > 0.02f) sb.fillOval(cx, cy, W * size * 0.45f * grow, H * size * 0.4f * grow, 1f, 0.9f, 0.45f, 0.9f * ha, 16)
    cracks(5, size * 0.9f, 0.25f, 0.5f, 2.4f, cr * 0.5f, cg * 0.5f, cb * 0.5f, cool * 0.9f,
      1f, mix(0.55f, 0.35f, cool), 0.12f, cool * 0.95f, sd)
  }

  /** Flames standing on `n` points: a ring at `rho` (`jitter` of it either way), or scattered
    * over the footprint when `scatter`. Each rises over [t0, t0 + 0.1] (later further out when it
    * spreads), licks, and dies back from `die`. Only the ones this layer owns are drawn, far to near. */
  private def flames(n0: Int, rho: Float, jitter: Float, scatter: Boolean, h0: Float, w0: Float, t0: Float, spreadT: Float,
                     die: Float, or: Float, og: Float, ob: Float, hr: Float, hg: Float, hb: Float, sd: Int,
                     tr: Float = -1f, tg: Float = 0f, tb: Float = 0f): Unit = {
    val n = Math.min(32, Math.max(1, (n0 * (0.6f + 0.4f * det) + 0.5f).toInt))
    val s0 = seed * 23 + sd * 61
    var i = 0
    while (i < n) {
      val th = (i + 0.5f * hash(s0, i)) * TWO_PI / n
      val rr = if (scatter) 0.12f + (rho - 0.12f) * Math.sqrt(h01(s0, 30 + i)).toFloat else rho + jitter * hash(s0, 60 + i)
      _ordY(i) = gy(sinf(th), rr)
      _ux(i) = gx(cosf(th), rr)
      _us(i) = rr
      i += 1
    }
    sortFarToNear(n)
    var j = 0
    while (j < n) {
      val i2 = _ord(j)
      val y = _ordY(i2)
      if (mine(y)) {
        val start = t0 + spreadT * _us(i2)
        val up = easeOut3(seg(T, start, start + 0.1f))
        val down = 1f - smooth(seg(T, die + 0.1f * h01(s0, 90 + i2), 1f))
        val lick = 0.82f + 0.18f * sinf(MS * 0.018f + i2 * 1.7f)
        val h = h0 * k * (0.7f + 0.5f * h01(s0, 120 + i2)) * up * down * lick
        val lean = sinf(MS * 0.011f + i2 * 2.3f) * 3f * k
        flameTongue(_ux(i2), y, w0 * k, h, lean, Math.min(1f, down * 1.4f), or, og, ob, hr, hg, hb, tr, tg, tb)
      }
      j += 1
    }
  }

  private def explosion(st: Int): Unit = {
    val pal = (st: @scala.annotation.switch) match {
      case S_PLASMA => PAL_PLASMA
      case S_EMP => PAL_EMP
      case S_CANNON => PAL_CANNON
      case S_NAPALM => PAL_NAPALM
      case S_INFERNO => PAL_INFERNO
      case S_ERUPTION => PAL_ERUPTION
      case _ => PAL_FIRE
    }
    // ── On the ground ──
    (st: @scala.annotation.switch) match {
      case S_ERUPTION =>
        lavaPool(0.46f, 1f, 0.48f, 0.08f, 0.24f, 0.08f, 0.05f, 1)
        shockRing(0f, 0.3f, 6f, 1f, 0.5f, 0.15f, 0.85f)
      case S_NAPALM =>
        scorch(0.95f, 0.1f, 0.06f, 0.03f, 0.55f, 0.72f)
        shockRing(0f, 0.28f, 6f, 1f, 0.6f, 0.2f, 0.8f)
      case S_PLASMA =>
        scorch(0.55f, 0.05f, 0.1f, 0.16f, 0.5f)
        cracks(5, 0.55f, 0.02f, 0.12f, 3f, 0.05f, 0.08f, 0.12f, 0.7f, 0.5f, 0.95f, 1f, 0.9f)
        shockRing(0f, 0.28f, 6f, 0.45f, 0.9f, 1f, 0.9f)
        shockRing(0.05f, 0.42f, 2.6f, 0.85f, 1f, 1f, 0.8f, reach = 0.8f)
      case S_EMP =>
        scorch(0.55f, 0.06f, 0.07f, 0.1f, 0.5f)
        cracks(5, 0.55f, 0.02f, 0.12f, 3f, 0.08f, 0.07f, 0.07f, 0.7f)
        // Coil rings, one after another, as off the Railgunner's slugs
        shockRing(0f, 0.24f, 3.4f, 0.6f, 0.82f, 1f, 1f)
        shockRing(0.05f, 0.3f, 3f, 0.75f, 0.9f, 1f, 0.9f)
        shockRing(0.1f, 0.36f, 2.6f, 0.9f, 0.95f, 1f, 0.8f)
      case S_CANNON =>
        scorch(0.5f, 0.07f, 0.05f, 0.04f, 0.6f, 0.7f)
        cracks(6, 0.6f, 0.02f, 0.1f, 3.4f, 0.1f, 0.08f, 0.06f, 0.75f)
        shockRing(0f, 0.3f, 6f, 0.9f, 0.85f, 0.75f, 0.85f)
      case S_INFERNO =>
        scorch(0.7f, 0.12f, 0.04f, 0.02f, 0.6f)
        cracks(6, 0.6f, 0.02f, 0.14f, 3.4f, 0.12f, 0.05f, 0.03f, 0.75f, 1f, 0.6f, 0.15f, 0.9f)
        shockRing(0f, 0.3f, 8f, 1f, 0.6f, 0.2f, 0.9f)
      case S_BOMB | S_CLUSTER | S_BOMB_ROCKET =>
        scorch(0.66f, 0.06f, 0.05f, 0.05f, 0.55f)
        cracks(5, 0.55f, 0.02f, 0.1f, 3.2f, 0.08f, 0.06f, 0.05f, 0.7f)
        shockRing(0f, 0.3f, 8f, 1f, 0.84f, 0.3f, 0.95f)
      case _ => // S_FRAG, S_ROCKET, S_MINE
        scorch(0.6f, 0.08f, 0.06f, 0.05f, 0.55f)
        cracks(6, 0.58f, 0.02f, 0.12f, 3.2f, 0.09f, 0.07f, 0.06f, 0.72f)
        shockRing(0f, 0.3f, 6.5f, 0.88f, 0.78f, 0.6f, 0.85f)
        if (st == S_MINE) shockRing(0.04f, 0.38f, 2.6f, 0.5f, 0.8f, 1f, 0.8f, reach = 0.85f)
    }

    // ── Standing: the flames of napalm and the inferno ──
    if (st == S_NAPALM) flames(14, 0.92f, 0f, scatter = true, 20f, 5f, 0.02f, 0.2f, 0.62f,
      0.98f, 0.42f, 0.08f, 1f, 0.86f, 0.4f, 1)
    if (st == S_INFERNO) flames(10, 0.78f, 0.06f, scatter = false, 28f, 6f, 0.03f, 0f, 0.55f,
      0.96f, 0.36f, 0.06f, 1f, 0.9f, 0.5f, 2)
    // The bomblets of a cluster, going off one after another round the first
    if (st == S_CLUSTER) {
      var i = 0
      while (i < 5) {
        val th = (i + 0.4f * hash(seed, 500 + i)) * TWO_PI / 5
        val rr = 0.5f + 0.35f * h01(seed, 510 + i)
        bomblet(gx(cosf(th), rr), gy(sinf(th), rr), 0.1f + 0.075f * i, 0.16f, 520 + i)
        i += 1
      }
    }

    if (layer != AIR) return

    // ── The blast itself. What it throws is drawn first, so it comes out of the fireball
    // instead of being painted across it ──
    (st: @scala.annotation.switch) match {
      case S_FRAG =>
        flash(0.08f, 0.6f, 1f, 0.92f, 0.7f)
        debris(D_SPARK, 10, 0f, 0.3f, 1f, 12f, 2.2f, 1f, 0.75f, 0.35f, 1)
        debris(D_CHIP, 9, 0.01f, 0.9f, 0.9f, 46f, 3.6f, 0.4f, 0.3f, 0.2f, 2, shadow = true)
        debris(D_CHIP, 5, 0.01f, 0.8f, 0.85f, 30f, 2.8f, 0.46f, 0.48f, 0.52f, 3)
        fireball(5, 0.42f, 0.5f, 14f, pal)
        puffs(5, 0.22f, 1f, 0.25f, 34f, 10f, 22f, 0.42f, 0.4f, 0.39f, 0.8f, 1, outward = 0.15f, lift = 10f)
      case S_ROCKET =>
        flash(0.08f, 0.65f, 1f, 0.9f, 0.6f)
        debris(D_SPARK, 12, 0f, 0.38f, 1f, 20f, 2.2f, 1f, 0.7f, 0.3f, 1)
        debris(D_CHIP, 6, 0.01f, 0.85f, 0.8f, 36f, 3f, 0.35f, 0.33f, 0.32f, 2, shadow = true)
        fireball(6, 0.46f, 0.5f, 18f, pal)
        // The smoke ring rolling out along the ground
        puffs(11, 0.1f, 0.95f, 0.3f, 6f, 12f, 22f, 0.52f, 0.5f, 0.48f, 0.62f, 2, outward = 0.6f, flat = 0.6f, inkK = 0.55f)
        puffs(3, 0.3f, 1f, 0.1f, 44f, 12f, 24f, 0.3f, 0.29f, 0.29f, 0.7f, 3, lift = 14f)
      case S_BOMB =>
        debris(D_CHIP, 8, 0.01f, 0.85f, 0.9f, 40f, 3.2f, 0.2f, 0.2f, 0.23f, 2, shadow = true)
        debris(D_SPARK, 8, 0f, 0.35f, 0.95f, 18f, 2.2f, 1f, 0.8f, 0.3f, 3)
        starburst(cx, cy - H * 0.3f, 0.52f, 0f, 0.32f, 10, 1)
        puffs(7, 0.14f, 1f, 0.3f, 30f, 12f, 24f, 0.2f, 0.19f, 0.22f, 0.9f, 1, outward = 0.25f, lift = 8f)
        glints(4, 0.05f, 0.6f, 0.8f, 30f, 10f, 1f, 0.92f, 0.45f, 0.95f)
      case S_CLUSTER =>
        debris(D_CHIP, 7, 0.01f, 0.8f, 0.85f, 36f, 3f, 0.2f, 0.2f, 0.23f, 2, shadow = true)
        starburst(cx, cy - H * 0.3f, 0.4f, 0f, 0.28f, 10, 1)
        puffs(5, 0.12f, 0.9f, 0.25f, 26f, 10f, 20f, 0.2f, 0.19f, 0.22f, 0.85f, 1, outward = 0.2f, lift = 8f)
      case S_BOMB_ROCKET =>
        debris(D_SPARK, 12, 0f, 0.38f, 1f, 20f, 2.2f, 1f, 0.72f, 0.3f, 1)
        starburst(cx, cy - H * 0.32f, 0.5f, 0f, 0.3f, 10, 1)
        puffs(11, 0.1f, 0.95f, 0.3f, 6f, 12f, 22f, 0.24f, 0.23f, 0.26f, 0.7f, 2, outward = 0.6f, flat = 0.6f, inkK = 0.55f)
        glints(3, 0.05f, 0.5f, 0.8f, 26f, 9f, 1f, 0.92f, 0.45f, 0.9f)
      case S_PLASMA =>
        flash(0.08f, 0.6f, 0.6f, 0.9f, 1f)
        debris(D_HEX, 8, 0.01f, 0.8f, 0.85f, 30f, 3f, 0.36f, 0.42f, 0.5f, 2, shadow = true)
        debris(D_SPARK, 10, 0f, 0.35f, 1f, 16f, 2f, 0.5f, 0.9f, 1f, 3)
        fireball(5, 0.38f, 0.42f, 10f, pal)
        if (T < 0.32f) {
          val epoch = (MS / 70f).toInt
          val a = 1f - T / 0.32f
          var i = 0
          while (i < 3) {
            val th = hash(seed, 300 + i + epoch * 3) * PI + i * 2.1f
            bolt(cx, cy - H * 0.2f, gx(cosf(th), 0.72f), gy(sinf(th), 0.72f), 6, 5f * k, seed + epoch * 7 + i,
              2.6f * k, 0.45f, 0.9f, 1f, a)
            i += 1
          }
        }
        puffs(3, 0.3f, 0.95f, 0.15f, 28f, 8f, 16f, 0.75f, 0.82f, 0.9f, 0.55f, 4, lift = 8f)
      case S_EMP =>
        flash(0.07f, 0.55f, 0.7f, 0.85f, 1f)
        debris(D_SHARD, 10, 0f, 0.6f, 1f, 22f, 3f, 0.78f, 0.8f, 0.86f, 1)
        debris(D_SPARK, 10, 0f, 0.4f, 0.95f, 14f, 2f, 0.5f, 0.75f, 1f, 2)
        fireball(5, 0.36f, 0.45f, 12f, pal)
        if (T > 0.06f && T < 0.5f) {
          // Static crawling round the rim
          val epoch = (MS / 80f).toInt
          val a = life(seg(T, 0.06f, 0.5f), 0.2f, 0.5f)
          var i = 0
          while (i < 4) {
            val th = (i + 0.5f * hash(seed + epoch, 400 + i)) * TWO_PI / 4
            bolt(gx(cosf(th), 0.8f), gy(sinf(th), 0.8f), gx(cosf(th + 0.4f), 0.85f), gy(sinf(th + 0.4f), 0.85f),
              4, 3f * k, seed * 3 + epoch * 5 + i, 1.8f * k, 0.55f, 0.8f, 1f, a)
            i += 1
          }
        }
        puffs(4, 0.3f, 1f, 0.2f, 30f, 9f, 20f, 0.4f, 0.42f, 0.46f, 0.7f, 3, lift = 8f)
      case S_CANNON =>
        flash(0.06f, 0.45f, 1f, 0.9f, 0.6f)
        debris(D_SPLINTER, 10, 0.01f, 0.85f, 0.95f, 40f, 3.4f, 0.58f, 0.4f, 0.22f, 2, shadow = true)
        debris(D_SPARK, 6, 0f, 0.3f, 0.9f, 12f, 2f, 1f, 0.8f, 0.4f, 3)
        fireball(4, 0.3f, 0.3f, 8f, pal)
        // Black powder: the smoke is the blast
        puffs(9, 0.04f, 1f, 0.28f, 26f, 12f, 28f, 0.8f, 0.79f, 0.77f, 0.88f, 1, outward = 0.45f, lift = 6f, inkK = 0.8f)
        cannonball()
      case S_NAPALM =>
        flash(0.07f, 0.7f, 1f, 0.85f, 0.5f)
        debris(D_GLOB, 8, 0f, 0.45f, 0.95f, 26f, 2.6f, 1f, 0.55f, 0.15f, 1)
        fireball(5, 0.4f, 0.35f, 10f, pal)
        puffs(6, 0.2f, 1f, 0.4f, 52f, 10f, 22f, 0.13f, 0.11f, 0.11f, 0.8f, 2, lift = 12f)
      case S_INFERNO =>
        flash(0.09f, 0.8f, 1f, 0.8f, 0.4f)
        fireball(7, 0.5f, 0.55f, 26f, pal)
        motes(12, 0.08f, 0.95f, 0.65f, 64f, 1.6f, 1f, 0.6f, 0.2f, 1f, 1)
        puffs(3, 0.4f, 1f, 0.1f, 52f, 12f, 24f, 0.28f, 0.2f, 0.19f, 0.6f, 2, lift = 24f)
      case S_ERUPTION =>
        flash(0.07f, 0.6f, 1f, 0.7f, 0.3f)
        debris(D_GLOB, 10, 0f, 0.8f, 1f, 72f, 3.6f, 1f, 0.5f, 0.1f, 1, shadow = true)
        debris(D_CHIP, 8, 0f, 0.75f, 0.85f, 52f, 3.4f, 0.2f, 0.17f, 0.16f, 2, shadow = true)
        fireball(5, 0.34f, 0.35f, 14f, pal)
        puffs(6, 0.12f, 1f, 0.12f, 80f, 10f, 24f, 0.24f, 0.22f, 0.22f, 0.8f, 3, lift = 14f)
        motes(10, 0.05f, 0.9f, 0.4f, 56f, 1.5f, 1f, 0.55f, 0.15f, 1f, 4)
      case _ => // S_MINE
        flash(0.07f, 0.6f, 1f, 0.9f, 0.7f)
        debris(D_BLOCK, 7, 0.01f, 0.8f, 0.9f, 36f, 3.2f, 0.42f, 0.45f, 0.5f, 1, shadow = true)
        debris(D_SPARK, 8, 0f, 0.35f, 1f, 16f, 2f, 0.5f, 0.8f, 1f, 2)
        fireball(5, 0.4f, 0.45f, 12f, pal)
        puffs(4, 0.28f, 1f, 0.2f, 32f, 9f, 20f, 0.4f, 0.4f, 0.42f, 0.75f, 3, lift = 8f)
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  LAVA AND FLAME
  // ═══════════════════════════════════════════════════════════════════

  private def lava(st: Int): Unit = {
    val hell = st == S_HELLFIRE
    if (hell) lavaPool(0.4f, 0.9f, 0.16f, 0.1f, 0.13f, 0.04f, 0.07f, 1)
    else lavaPool(0.38f, 1f, 0.48f, 0.08f, 0.28f, 0.09f, 0.05f, 1)
    // Cerberus: three flames out of the pool, one for each head
    if (hell) flames(3, 0.2f, 0f, scatter = false, 24f, 5.5f, 0.02f, 0f, 0.55f,
      0.82f, 0.1f, 0.12f, 1f, 0.55f, 0.2f, 1)
    if (layer != AIR) return
    if (hell) {
      flash(0.06f, 0.45f, 1f, 0.35f, 0.2f)
      debris(D_GLOB, 7, 0f, 0.55f, 0.85f, 24f, 2.6f, 0.9f, 0.16f, 0.12f, 1, shadow = true)
      motes(9, 0.05f, 0.9f, 0.45f, 46f, 1.4f, 0.85f, 0.95f, 0.3f, 1f, 3)
      puffs(2, 0.3f, 1f, 0.1f, 30f, 6f, 12f, 0.24f, 0.14f, 0.2f, 0.6f, 2, lift = 6f)
    } else {
      flash(0.06f, 0.45f, 1f, 0.7f, 0.3f)
      debris(D_GLOB, 8, 0f, 0.55f, 0.85f, 22f, 2.6f, 1f, 0.55f, 0.12f, 1, shadow = true)
      bubbles(3, 0.1f, 0.6f, 0.25f, 3f, 1f, 0.6f, 0.15f, 0.9f, 1)
      puffs(2, 0.3f, 1f, 0.1f, 30f, 6f, 12f, 0.3f, 0.26f, 0.25f, 0.55f, 2, lift = 6f)
    }
  }

  // Scratch for a flame feather's outline
  private val _fx = new Array[Float](10)
  private val _fy = new Array[Float](10)

  /** A feather of flame lying on the ground from `rho0` to `rho1` along `th`, `hw` half-wide at
    * its widest: a lens, so convex. Red-orange round a gold vane, a white quill down it. */
  private def flameFeather(th: Float, rho0: Float, rho1: Float, hw: Float, a: Float): Unit = {
    val c = cosf(th); val s = sinf(th)
    val bx = gx(c, rho0); val by = gy(s, rho0)
    val tx = gx(c, rho1); val ty = gy(s, rho1)
    val dx = tx - bx; val dy = ty - by
    val len = Math.sqrt(dx * dx + dy * dy).toFloat
    if (len < 2f || a <= 0.02f) return
    val nx = -dy / len; val ny = dx / len
    var pass = 0
    while (pass < 3) {
      val wk = (pass: @scala.annotation.switch) match { case 0 => 1f; case 1 => 0.84f; case _ => 0.44f }
      val grow = if (pass == 0) 1.6f else 0f
      // base, one side at 0.25/0.5/0.75, tip, the other side back
      _fx(0) = bx - dx * 0.02f; _fy(0) = by - dy * 0.02f
      var i = 1
      while (i <= 3) {
        val u = i * 0.25f
        val wu = hw * wk * sinf(u * PI * 0.92f + 0.12f) + grow
        _fx(i) = bx + dx * u + nx * wu; _fy(i) = by + dy * u + ny * wu
        _fx(8 - i) = bx + dx * u - nx * wu; _fy(8 - i) = by + dy * u - ny * wu
        i += 1
      }
      _fx(4) = tx + dx * (grow / len); _fy(4) = ty + dy * (grow / len)
      (pass: @scala.annotation.switch) match {
        case 0 => sb.fillPolygon(_fx, _fy, 8, 0.3f, 0.06f, 0.03f, 0.85f * a)
        case 1 => sb.fillPolygon(_fx, _fy, 8, 0.96f, 0.36f, 0.1f, a)
        case _ => sb.fillPolygon(_fx, _fy, 8, 1f, 0.78f, 0.28f, a)
      }
      pass += 1
    }
    sb.strokeLine(bx, by, bx + dx * 0.85f, by + dy * 0.85f, 1.3f, 1f, 0.97f, 0.8f, 0.9f * a)
  }

  private def flame(st: Int): Unit = {
    if (st == S_FLAMBE) {
      scorch(0.45f, 0.06f, 0.06f, 0.12f, 0.35f)
      // Blue-footed flames round the pan, orange at their tips
      flames(9, 0.45f, 0.05f, scatter = false, 24f, 5f, 0f, 0f, 0.3f,
        0.3f, 0.5f, 1f, 0.78f, 0.9f, 1f, 1, tr = 1f, tg = 0.6f, tb = 0.16f)
      if (layer != AIR) return
      flash(0.08f, 0.5f, 0.6f, 0.7f, 1f)
      // The whoosh up out of the middle
      val wp = seg(T, 0f, 0.42f)
      if (wp > 0f && wp < 1f) {
        val h = 44f * k * easeOut3(seg(wp, 0f, 0.3f)) * (1f - smooth(seg(wp, 0.45f, 1f)))
        flameTongue(cx, cy, 8f * k, h, sinf(MS * 0.015f) * 3f * k, 1f - seg(wp, 0.7f, 1f),
          0.3f, 0.5f, 1f, 0.8f, 0.9f, 1f, 1f, 0.62f, 0.18f)
      }
      // A pinch of spice
      debris(D_FLECK, 5, 0.02f, 0.6f, 0.8f, 30f, 2.4f, 0.85f, 0.2f, 0.1f, 1)
      debris(D_FLECK, 5, 0.02f, 0.6f, 0.8f, 34f, 2.4f, 0.3f, 0.62f, 0.2f, 2)
      debris(D_FLECK, 4, 0.02f, 0.6f, 0.75f, 26f, 2.4f, 0.95f, 0.78f, 0.3f, 3)
      glints(3, 0.05f, 0.5f, 0.6f, 24f, 8f, 1f, 0.9f, 0.6f, 0.9f)
    } else {
      // S_PHOENIX: a tail of flame fanned out over the ground
      scorch(0.55f, 0.12f, 0.05f, 0.02f, 0.4f)
      if (layer == GROUND) {
        var i = 0
        while (i < 9) {
          val th = (i + 0.3f * hash(seed, 700 + i)) * TWO_PI / 9
          val unfurl = easeOut3(seg(T, 0.01f + 0.015f * i, 0.2f + 0.015f * i))
          val a = tail(T, 0.5f)
          val flutter = 1f + 0.12f * sinf(MS * 0.02f + i * 1.3f)
          flameFeather(th, 0.08f, 0.1f + 0.78f * unfurl, W * 0.085f * flutter * unfurl, a)
          i += 1
        }
      }
      flames(3, 0.08f, 0f, scatter = false, 30f, 6f, 0.02f, 0f, 0.42f, 1f, 0.55f, 0.12f, 1f, 0.95f, 0.6f, 1)
      if (layer != AIR) return
      flash(0.08f, 0.7f, 1f, 0.8f, 0.4f)
      motes(14, 0.05f, 1f, 0.8f, 70f, 1.8f, 1f, 0.72f, 0.22f, 1f, 1)
      glints(3, 0.1f, 0.7f, 0.7f, 30f, 10f, 1f, 0.9f, 0.5f, 0.9f)
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  LIQUIDS AND GAS
  // ═══════════════════════════════════════════════════════════════════

  /** A few flies circling `height` units over the middle (the plague). */
  private def flies(n: Int, t0: Float, t1: Float, height: Float): Unit = {
    if (layer != AIR || T <= t0 || T >= t1) return
    val a = life(seg(T, t0, t1), 0.15f, 0.7f)
    var i = 0
    while (i < n) {
      val ph = MS * (0.009f + 0.004f * h01(seed, 600 + i)) + i * 1.9f
      val rr = (7f + 5f * h01(seed, 610 + i)) * k
      val x = cx + cosf(ph) * rr + sinf(ph * 2.3f) * 3f * k
      val y = cy - height * k + sinf(ph * 1.3f) * rr * 0.45f
      val flap = 0.5f + 0.5f * sinf(MS * 0.12f + i)
      sb.fillOval(x - 1.4f, y - 1.2f, 1.6f, 1.1f * flap + 0.3f, 0.85f, 0.9f, 0.85f, 0.55f * a, 6)
      sb.fillOval(x + 1.4f, y - 1.2f, 1.6f, 1.1f * flap + 0.3f, 0.85f, 0.9f, 0.85f, 0.55f * a, 6)
      sb.fillOval(x, y, 1.5f, 1.2f, 0.06f, 0.07f, 0.04f, 0.95f * a, 6)
      i += 1
    }
  }

  /** Bubbles floating up off it, rings with a glint. */
  private def risingBubbles(n: Int, t0: Float, t1: Float, spread: Float, rise: Float, size: Float,
                            r: Float, g: Float, b: Float, a0: Float, sd: Int): Unit = {
    if (layer != AIR || T <= t0 || T >= t1) return
    var i = 0
    while (i < n) {
      val s0 = seed * 29 + sd * 11 + i * 7
      val start = t0 + (t1 - t0) * 0.5f * h01(s0, 1)
      val tau = seg(T, start, t1)
      if (tau > 0f && tau < 1f) {
        val th = h01(s0, 2) * TWO_PI
        val rho = spread * h01(s0, 3)
        val x = gx(cosf(th), rho) + sinf(tau * 7f + i) * 2.5f * k
        val y = gy(sinf(th), rho) - rise * k * easeOut(tau)
        val bs = size * k * (0.6f + 0.6f * h01(s0, 4)) * (0.6f + 0.4f * tau)
        val a = a0 * Math.min(1f, tau * 6f) * (1f - tau)
        sb.strokeOval(x, y, bs, bs, 2.2f, ink(r), ink(g), ink(b), 0.6f * a, 10)
        sb.fillOval(x, y, bs, bs, r, g, b, 0.35f * a, 10)
        sb.strokeOval(x, y, bs, bs, 0.9f, lit(r), lit(g), lit(b), a, 10)
        sb.fillOval(x - bs * 0.35f, y - bs * 0.35f, bs * 0.25f, bs * 0.22f, 1f, 1f, 1f, 0.9f * a, 6)
      }
      i += 1
    }
  }

  /** Tentacles rising out of the ink, far to near: up, swaying, and slapping back down. */
  private def tentacles(n: Int): Unit = {
    val s0 = seed * 41
    var i = 0
    while (i < n) {
      val th = (i + 0.5f * hash(s0, i)) * TWO_PI / n
      val rr = 0.25f + 0.45f * h01(s0, 10 + i)
      _ordY(i) = gy(sinf(th), rr); _ux(i) = gx(cosf(th), rr); _us(i) = cosf(th)
      i += 1
    }
    sortFarToNear(n)
    var j = 0
    while (j < n) {
      val i2 = _ord(j)
      val x = _ux(i2); val y = _ordY(i2)
      if (mine(y)) {
        val start = 0.08f + 0.12f * h01(s0, 20 + i2)
        val rise = easeOut3(seg(T, start, start + 0.18f)) * (1f - smooth(seg(T, 0.66f, 0.94f)))
        if (rise > 0.02f) {
          val side = if (_us(i2) >= 0f) 1f else -1f
          sb.strokeOval(x, y, 9f * k, 3.4f * k, 1.8f, 0.45f, 0.3f, 0.55f, 0.6f * rise, 12)
          tendril(x, y, -PI * 0.5f + side * 0.25f, (44f + 18f * h01(s0, 30 + i2)) * k, rise,
            side * (1.5f + 0.5f * sinf(MS * 0.006f + i2)), 10f * k, 0.32f, 0.14f, 0.38f, 1f,
            hr = 0.72f, hg = 0.46f, hb = 0.82f)
          // Suckers down its inner side
          var p = 2
          while (p < 8) {
            sb.fillOval(_px(p) - side * 2.2f * k, _py(p), 1.7f * k, 1.3f * k, 0.9f, 0.72f, 0.88f, 0.9f * rise, 6)
            p += 2
          }
        }
      }
      j += 1
    }
  }

  private def liquid(st: Int): Unit = {
    (st: @scala.annotation.switch) match {
      case S_PLAGUE =>
        splat(0.3f, easeOut3(seg(T, 0f, 0.1f)), 0.35f, 0.55f, 0.12f, 0.85f * tail(T, 0.55f), 1, glossA = 0.5f, drops = 6)
        if (layer != AIR) return
        puffs(5, 0.02f, 0.95f, 0.2f, 18f, 8f, 18f, 0.45f, 0.72f, 0.2f, 0.75f, 1, outward = 0.3f, flat = 0.7f)
        bubbles(3, 0.1f, 0.7f, 0.25f, 3f, 0.5f, 0.8f, 0.25f, 0.9f, 1)
        debris(D_DROP, 6, 0f, 0.5f, 0.75f, 20f, 2.4f, 0.5f, 0.8f, 0.2f, 2)
        flies(4, 0.15f, 0.98f, 16f)
      case S_ALCHEMY =>
        splat(0.26f, easeOut3(seg(T, 0f, 0.1f)), 0.55f, 0.95f, 0.3f, 0.8f * tail(T, 0.5f), 1, drops = 5)
        if (layer != AIR) return
        flash(0.07f, 0.4f, 0.8f, 1f, 0.6f)
        debris(D_DROP, 5, 0f, 0.55f, 0.85f, 28f, 3f, 0.55f, 0.95f, 0.3f, 1)
        debris(D_DROP, 4, 0f, 0.55f, 0.8f, 32f, 3f, 0.95f, 0.3f, 0.8f, 2)
        debris(D_DROP, 4, 0f, 0.55f, 0.85f, 26f, 3f, 0.3f, 0.85f, 1f, 3)
        risingBubbles(8, 0.05f, 0.95f, 0.45f, 40f, 3.8f, 0.7f, 0.95f, 0.85f, 0.95f, 1)
        glints(4, 0.05f, 0.6f, 0.7f, 20f, 8f, 1f, 1f, 0.8f, 0.9f)
      case S_BLIGHT =>
        splat(0.42f, easeOut3(seg(T, 0.02f, 0.16f)), 0.3f, 0.42f, 0.12f, 0.9f * tail(T, 0.6f), 1, glossA = 0.45f, drops = 9)
        shockRing(0f, 0.25f, 5f, 0.55f, 0.75f, 0.3f, 0.8f)
        if (layer != AIR) return
        flash(0.06f, 0.45f, 0.7f, 0.95f, 0.5f)
        debris(D_SHARD, 9, 0f, 0.6f, 0.95f, 30f, 3.2f, 0.75f, 0.95f, 0.8f, 1)
        debris(D_GLOB, 8, 0f, 0.7f, 0.9f, 36f, 3.2f, 0.3f, 0.44f, 0.12f, 2, shadow = true)
        puffs(6, 0.1f, 1f, 0.3f, 40f, 10f, 24f, 0.5f, 0.35f, 0.6f, 0.7f, 1, outward = 0.35f, lift = 6f)
        puffs(3, 0.2f, 1f, 0.1f, 50f, 10f, 20f, 0.45f, 0.62f, 0.22f, 0.6f, 2, lift = 10f)
        bubbles(5, 0.15f, 0.85f, 0.35f, 3.4f, 0.45f, 0.6f, 0.2f, 0.9f, 3)
      case S_ACID_FLASK =>
        splat(0.38f, easeOut3(seg(T, 0f, 0.14f)), 0.72f, 0.92f, 0.2f, 0.9f * tail(T, 0.6f), 1, glossA = 0.6f, drops = 8)
        if (layer != AIR) return
        flash(0.06f, 0.45f, 0.85f, 1f, 0.5f)
        debris(D_SHARD, 9, 0f, 0.55f, 0.95f, 28f, 3f, 0.8f, 0.92f, 1f, 1)
        debris(D_DROP, 9, 0f, 0.6f, 0.9f, 30f, 2.6f, 0.72f, 0.92f, 0.2f, 2)
        // It fizzes: bubbles on it and steam off it
        bubbles(7, 0.1f, 0.9f, 0.35f, 2.6f, 0.85f, 1f, 0.5f, 0.9f, 3)
        puffs(5, 0.15f, 1f, 0.3f, 36f, 6f, 14f, 0.9f, 0.95f, 0.85f, 0.55f, 4, lift = 4f, inkK = 0.5f)
      case S_ACID_SPRAY =>
        splat(0.34f, easeOut3(seg(T, 0f, 0.14f)), 0.78f, 0.85f, 0.15f, 0.85f * tail(T, 0.55f), 1, drops = 10)
        if (layer == GROUND) {
          // Burning holes in what it landed on
          val ha = 0.8f * seg(T, 0.1f, 0.3f) * tail(T, 0.55f)
          var i = 0
          while (i < 4) {
            val th = h01(seed, 800 + i) * TWO_PI; val rr = 0.22f * h01(seed, 810 + i)
            sb.fillOval(gx(cosf(th), rr), gy(sinf(th), rr), 3.2f * k, 1.6f * k, 0.12f, 0.1f, 0.03f, ha, 8)
            i += 1
          }
          return
        }
        debris(D_DROP, 14, 0f, 0.45f, 1f, 14f, 2.2f, 0.78f, 0.85f, 0.15f, 1)
        puffs(5, 0.12f, 1f, 0.3f, 30f, 6f, 13f, 0.92f, 0.95f, 0.8f, 0.55f, 2, lift = 4f, inkK = 0.5f)
        bubbles(6, 0.1f, 0.8f, 0.3f, 2.4f, 0.9f, 1f, 0.4f, 0.9f, 3)
      case S_MUD =>
        splat(0.45f, easeOut3(seg(T, 0.02f, 0.3f)), 0.4f, 0.28f, 0.15f, 0.95f * tail(T, 0.62f), 1, glossA = 0.55f, drops = 10)
        shockRing(0f, 0.3f, 5f, 0.55f, 0.42f, 0.28f, 0.8f)
        crown(9, 0.2f, 20f, 3.4f, 0f, 0.42f, 0.42f, 0.3f, 0.16f, 1f, 1)
        if (layer != AIR) return
        debris(D_GLOB, 11, 0f, 0.7f, 1f, 48f, 3.4f, 0.42f, 0.3f, 0.16f, 1, shadow = true)
        debris(D_FLECK, 10, 0f, 0.5f, 1f, 30f, 2f, 0.28f, 0.2f, 0.12f, 2)
        bubbles(4, 0.2f, 0.9f, 0.3f, 3f, 0.55f, 0.42f, 0.26f, 0.9f, 3)
      case _ => // S_INK
        splat(0.5f, easeOut3(seg(T, 0f, 0.15f)), 0.1f, 0.06f, 0.16f, 0.92f * tail(T, 0.65f), 1, glossA = 0.5f, drops = 12)
        tentacles(6)
        if (layer != AIR) return
        debris(D_DROP, 10, 0f, 0.6f, 0.9f, 30f, 2.6f, 0.14f, 0.08f, 0.22f, 1)
        bubbles(4, 0.2f, 0.9f, 0.4f, 3f, 0.35f, 0.25f, 0.5f, 0.9f, 2)
    }
  }

  /** A skull made of gas: cranium and jaw inked as one, sockets and a nose in the dark. */
  private def skull(x: Float, y: Float, sz: Float, a: Float, r: Float, g: Float, b: Float): Unit = {
    if (a <= 0.02f) return
    sb.fillOval(x, y, sz + 2f, sz * 0.86f + 2f, ink(r), ink(g), ink(b), 0.8f * a, 18)
    sb.fillOval(x, y + sz * 0.62f, sz * 0.62f + 2f, sz * 0.36f + 2f, ink(r), ink(g), ink(b), 0.8f * a, 14)
    sb.fillOval(x, y, sz, sz * 0.86f, r, g, b, a, 18)
    sb.fillOval(x, y + sz * 0.62f, sz * 0.62f, sz * 0.36f, r, g, b, a, 14)
    sb.fillOval(x - sz * 0.25f, y - sz * 0.32f, sz * 0.45f, sz * 0.3f, lit(r), lit(g), lit(b), 0.8f * a, 12)
    val dr = ink(r) * 0.6f; val dg = ink(g) * 0.6f; val db = ink(b) * 0.6f
    sb.fillOval(x - sz * 0.36f, y + sz * 0.14f, sz * 0.25f, sz * 0.27f, dr, dg, db, 0.95f * a, 10)
    sb.fillOval(x + sz * 0.36f, y + sz * 0.14f, sz * 0.25f, sz * 0.27f, dr, dg, db, 0.95f * a, 10)
    _xs(0) = x; _ys(0) = y + sz * 0.34f
    _xs(1) = x + sz * 0.1f; _ys(1) = y + sz * 0.52f
    _xs(2) = x - sz * 0.1f; _ys(2) = y + sz * 0.52f
    sb.fillPolygon(_xs, _ys, 3, dr, dg, db, 0.95f * a)
    var t = 0
    while (t < 3) {
      val tx = x + (t - 1) * sz * 0.2f
      sb.strokeLine(tx, y + sz * 0.58f, tx, y + sz * 0.82f, 1.2f, dr, dg, db, 0.8f * a)
      t += 1
    }
  }

  private def gas(st: Int): Unit = {
    if (st == S_MIASMA) {
      scorch(0.75f, 0.22f, 0.35f, 0.12f, 0.35f)
      if (layer != AIR) return
      // The cloud rolls out along the ground, and a skull rises out of its middle
      puffs(12, 0.02f, 1f, 0.25f, 12f, 12f, 26f, 0.46f, 0.66f, 0.26f, 0.7f, 1, outward = 0.65f, flat = 0.65f)
      puffs(5, 0.05f, 1f, 0.12f, 50f, 14f, 30f, 0.52f, 0.42f, 0.6f, 0.65f, 2, lift = 8f)
      val sp = seg(T, 0.08f, 0.9f)
      if (sp > 0f && sp < 1f)
        skull(cx, cy - (20f + 50f * easeOut(sp)) * k, 18f * k * (0.8f + 0.3f * easeOut(sp)),
          0.85f * life(sp, 0.2f, 0.55f), 0.74f, 0.9f, 0.68f)
      motes(8, 0.1f, 0.9f, 0.6f, 40f, 1.4f, 0.55f, 1f, 0.4f, 0.9f, 3)
    } else {
      // S_VENOM_CLOUD: a low fog, and plumes rising out of it like the Hydra's heads
      scorch(0.7f, 0.3f, 0.4f, 0.08f, 0.3f)
      val s0 = seed * 43
      var i = 0
      while (i < 3) {
        val th = (i + 0.3f * hash(s0, i)) * TWO_PI / 3 + 0.5f
        _ordY(i) = gy(sinf(th), 0.3f); _ux(i) = gx(cosf(th), 0.3f); _us(i) = cosf(th)
        i += 1
      }
      sortFarToNear(3)
      var j = 0
      while (j < 3) {
        val i2 = _ord(j)
        val x = _ux(i2); val y = _ordY(i2)
        if (mine(y)) {
          val start = 0.05f + 0.07f * i2
          val grow = easeOut3(seg(T, start, start + 0.35f))
          val a = life(seg(T, start, 0.95f), 0.1f, 0.6f)
          if (grow > 0.02f && a > 0.02f) {
            val side = if (_us(i2) >= 0f) 1f else -1f
            tendril(x, y, -PI * 0.5f + side * 0.3f, 44f * k, grow, side * (1.6f + 0.4f * sinf(MS * 0.004f + i2)),
              11f * k, 0.62f, 0.8f, 0.22f, 0.85f * a)
            val hx = _px(8); val hy = _py(8)
            val hs = 7f * k * grow
            sb.fillOval(hx, hy, hs + 1.8f, hs * 0.78f + 1.8f, ink(0.62f), ink(0.8f), ink(0.22f), 0.8f * a, 14)
            sb.fillOval(hx, hy, hs, hs * 0.78f, 0.7f, 0.86f, 0.3f, 0.9f * a, 14)
            sb.fillOval(hx - hs * 0.4f, hy - hs * 0.1f, hs * 0.16f, hs * 0.2f, 0.1f, 0.14f, 0.03f, a, 6)
            sb.fillOval(hx + hs * 0.4f, hy - hs * 0.1f, hs * 0.16f, hs * 0.2f, 0.1f, 0.14f, 0.03f, a, 6)
          }
        }
        j += 1
      }
      if (layer != AIR) return
      puffs(10, 0.02f, 1f, 0.45f, 8f, 12f, 22f, 0.62f, 0.78f, 0.2f, 0.65f, 1, outward = 0.35f, flat = 0.55f)
      debris(D_DROP, 6, 0f, 0.6f, 0.7f, 24f, 2.2f, 0.7f, 0.9f, 0.2f, 2)
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  WATER
  // ═══════════════════════════════════════════════════════════════════

  /** A jet of liquid from (bx, by) to (tx, ty), `w` half-wide at its foot: four points, convex for
    * any lean a jet takes. */
  private def jet(bx: Float, by: Float, tx: Float, ty: Float, w: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    _xs(0) = bx - w; _ys(0) = by
    _xs(1) = tx; _ys(1) = ty
    _xs(2) = bx + w; _ys(2) = by
    _xs(3) = bx; _ys(3) = by + w * 0.32f
    sb.fillPolygon(_xs, _ys, 4, r, g, b, a)
  }

  /**
   * A crown thrown up round a ring at `rho`: `n` jets flaring out and up to `h0` units, falling
   * back over [t0, t1], each with a drop leaving its tip. Water, or mud.
   */
  private def crown(n0: Int, rho: Float, h0: Float, w0: Float, t0: Float, t1: Float,
                    r: Float, g: Float, b: Float, a0: Float, sd: Int): Unit = {
    val p = seg(T, t0, t1)
    if (p <= 0f || p >= 1f) return
    val n = Math.min(32, n0)
    val up = if (p < 0.35f) easeOut3(p / 0.35f) else 1f - smooth((p - 0.35f) / 0.65f)
    val a = a0 * tail(p, 0.6f)
    val s0 = seed + sd * 53
    var i = 0
    while (i < n) {
      val th = (i + 0.35f * hash(s0, i)) * TWO_PI / n
      _ux(i) = th
      _ordY(i) = gy(sinf(th), rho)
      i += 1
    }
    sortFarToNear(n)
    var j = 0
    while (j < n) {
      val i2 = _ord(j)
      val th = _ux(i2)
      val c = cosf(th); val s = sinf(th)
      val bx = gx(c, rho); val by = _ordY(i2)
      if (mine(by)) {
        val hh = h0 * k * (0.7f + 0.5f * h01(s0, 40 + i2)) * up
        if (hh > 2f) {
          val tx = bx + c * hh * 0.5f; val ty = by - hh + s * hh * 0.1f
          val w = w0 * k
          jet(bx, by + 0.8f, tx, ty - 1.8f, w + 1.4f, ink(r), ink(g), ink(b), 0.8f * a)
          jet(bx, by, tx, ty, w, r, g, b, a)
          sb.strokeLine(bx - w * 0.3f, by - 1f, tx - c * 0.5f, ty + 2f, 1.1f, lit(r), lit(g), lit(b), 0.85f * a)
          // A drop leaving the tip
          val dx = tx + c * hh * 0.2f * p; val dy = ty - 3f * k + p * p * 10f * k
          sb.fillOval(dx, dy, 2.4f * k + 1f, 2.8f * k + 1f, ink(r), ink(g), ink(b), 0.7f * a, 8)
          sb.fillOval(dx, dy, 2.4f * k, 2.8f * k, lit(r), lit(g), lit(b), a, 8)
        }
      }
      j += 1
    }
  }

  /** The Tidecaller's geyser: a column of water standing up out of the middle and falling back. */
  private def geyserColumn(): Unit = {
    if (layer != AIR) return
    val up = easeOut3(seg(T, 0f, 0.2f))
    val down = smooth(seg(T, 0.5f, 0.82f))
    val hgt = W * 1.05f * up * (1f - down)
    if (hgt < 3f) return
    val a = 1f - seg(T, 0.7f, 0.85f)
    val bw = W * 0.12f * (1f + 0.35f * down)
    val wob = sinf(MS * 0.02f) * 1.5f * k
    // Ink, body, a lighter stripe and a white core: a column is a trapezoid, so convex
    _xs(0) = cx - bw - 1.8f; _ys(0) = cy + 1f
    _xs(1) = cx - bw * 0.78f + wob - 1.8f; _ys(1) = cy - hgt - 1f
    _xs(2) = cx + bw * 0.78f + wob + 1.8f; _ys(2) = cy - hgt - 1f
    _xs(3) = cx + bw + 1.8f; _ys(3) = cy + 1f
    sb.fillPolygon(_xs, _ys, 4, 0.06f, 0.14f, 0.26f, 0.85f * a)
    _xs(0) = cx - bw; _ys(0) = cy
    _xs(1) = cx - bw * 0.78f + wob; _ys(1) = cy - hgt
    _xs(2) = cx + bw * 0.78f + wob; _ys(2) = cy - hgt
    _xs(3) = cx + bw; _ys(3) = cy
    sb.fillPolygon(_xs, _ys, 4, 0.3f, 0.6f, 0.95f, a)
    _xs(0) = cx - bw * 0.45f; _ys(0) = cy
    _xs(1) = cx - bw * 0.3f + wob; _ys(1) = cy - hgt
    _xs(2) = cx + bw * 0.2f + wob; _ys(2) = cy - hgt
    _xs(3) = cx + bw * 0.25f; _ys(3) = cy
    sb.fillPolygon(_xs, _ys, 4, 0.62f, 0.84f, 1f, 0.9f * a)
    sb.strokeLine(cx - bw * 0.1f, cy - 2f, cx - bw * 0.05f + wob, cy - hgt + 3f, 1.6f * k, 1f, 1f, 1f, 0.8f * a)
    // Foam boiling off the top, and round its foot
    var f = 0
    while (f < 3) {
      val fx = cx + wob + (f - 1) * bw * 0.7f
      val fy = cy - hgt - (if (f == 1) 3f * k else 0f)
      val fs = bw * (0.62f + (if (f == 1) 0.2f else 0f)) * (1f + 0.08f * sinf(MS * 0.03f + f))
      sb.fillOval(fx, fy, fs + 1.8f, fs * 0.8f + 1.8f, 0.1f, 0.2f, 0.32f, 0.8f * a, 14)
      f += 1
    }
    f = 0
    while (f < 3) {
      val fx = cx + wob + (f - 1) * bw * 0.7f
      val fy = cy - hgt - (if (f == 1) 3f * k else 0f)
      val fs = bw * (0.62f + (if (f == 1) 0.2f else 0f)) * (1f + 0.08f * sinf(MS * 0.03f + f))
      sb.fillOval(fx, fy, fs, fs * 0.8f, 0.9f, 0.96f, 1f, a, 14)
      f += 1
    }
    sb.fillOval(cx, cy, bw * 1.7f + 1.8f, bw * 0.55f + 1.8f, 0.1f, 0.2f, 0.32f, 0.7f * a, 16)
    sb.fillOval(cx, cy, bw * 1.7f, bw * 0.55f, 0.92f, 0.97f, 1f, 0.95f * a, 16)
  }

  private def water(st: Int): Unit = {
    if (st == S_SPLASH) {
      scorch(0.5f, 0.12f, 0.25f, 0.4f, 0.35f, 0.5f)
      shockRing(0.04f, 0.6f, 2.6f, 0.75f, 0.9f, 1f, 0.75f)
      shockRing(0.18f, 0.8f, 2f, 0.75f, 0.9f, 1f, 0.5f)
      if (layer == GROUND) {
        // Foam round where it struck
        val fp = seg(T, 0f, 0.55f)
        if (fp < 1f) {
          val q = 0.28f + 0.26f * easeOut(fp)
          sb.strokeOval(cx, cy, W * q, H * q, 5f * k * (1f - fp) + 1f, 0.95f, 0.98f, 1f, 0.85f * (1f - fp), 22)
        }
      }
      crown(10, 0.28f, 26f, 3.4f, 0f, 0.55f, 0.35f, 0.65f, 1f, 1f, 1)
      if (layer != AIR) return
      debris(D_DROP, 14, 0f, 0.7f, 0.9f, 34f, 2.4f, 0.55f, 0.8f, 1f, 1)
      glints(3, 0.05f, 0.5f, 0.6f, 20f, 8f, 0.85f, 0.95f, 1f, 0.85f)
    } else {
      // S_GEYSER
      scorch(0.6f, 0.12f, 0.25f, 0.4f, 0.35f, 0.6f)
      shockRing(0.02f, 0.45f, 3f, 0.75f, 0.9f, 1f, 0.8f)
      shockRing(0.15f, 0.65f, 2.4f, 0.75f, 0.9f, 1f, 0.6f)
      shockRing(0.3f, 0.85f, 2f, 0.75f, 0.9f, 1f, 0.45f)
      crown(8, 0.18f, 18f, 3f, 0f, 0.4f, 0.35f, 0.65f, 1f, 1f, 1)
      geyserColumn()
      if (layer != AIR) return
      debris(D_DROP, 16, 0.08f, 0.9f, 1f, 90f, 2.6f, 0.55f, 0.8f, 1f, 1)
      glints(3, 0.1f, 0.6f, 0.4f, 60f, 9f, 0.85f, 0.95f, 1f, 0.85f)
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  ELECTRICITY
  // ═══════════════════════════════════════════════════════════════════

  /** `n` bolts out of the middle to `reach` of the footprint, struck afresh every `every` ms
    * until `t1`: its shape jumps, as lightning's does, but its strength only ever falls. */
  private def arcBurst(n: Int, reach: Float, t1: Float, every: Float, w: Float,
                       r: Float, g: Float, b: Float, sd: Int, fork: Boolean = false): Unit = {
    if (layer != AIR || T >= t1) return
    val epoch = (MS / every).toInt
    val a = 1f - smooth(T / t1)
    val s0 = seed * 5 + sd * 17 + epoch * 131
    var i = 0
    while (i < n) {
      val th = (i + 0.6f * hash(s0, i)) * TWO_PI / n
      val c = cosf(th); val s = sinf(th)
      if (fork) {
        // To half way, then splitting in two
        val mx = gx(c, reach * 0.55f); val my = gy(s, reach * 0.55f)
        bolt(cx, cy - H * 0.15f, mx, my, 5, 5f * k, s0 + i, w * k, r, g, b, a)
        var f = 0
        while (f < 2) {
          val fa = th + (if (f == 0) -0.34f else 0.34f) + 0.12f * hash(s0, 40 + i * 2 + f)
          bolt(mx, my, gx(cosf(fa), reach), gy(sinf(fa), reach), 5, 4f * k, s0 + 50 + i * 2 + f, w * 0.7f * k, r, g, b, a)
          f += 1
        }
      } else {
        val rr = reach * (0.72f + 0.28f * h01(s0, 20 + i))
        bolt(cx, cy - H * 0.15f, gx(c, rr), gy(s, rr), 7, 6f * k, s0 + i, w * k, r, g, b, a)
      }
      i += 1
    }
  }

  /** Electricity crawling round the edge of the footprint over [t0, t1]. */
  private def rimArcs(n: Int, t0: Float, t1: Float, every: Float, r: Float, g: Float, b: Float): Unit = {
    if (layer != AIR || T <= t0 || T >= t1) return
    val epoch = (MS / every).toInt
    val a = life(seg(T, t0, t1), 0.15f, 0.5f)
    var i = 0
    while (i < n) {
      val th = (i + 0.5f * hash(seed + epoch * 7, 900 + i)) * TWO_PI / n
      bolt(gx(cosf(th), 0.92f), gy(sinf(th), 0.92f), gx(cosf(th + 0.38f), 0.96f), gy(sinf(th + 0.38f), 0.96f),
        4, 3f * k, seed * 3 + epoch * 5 + i, 1.9f * k, r, g, b, a)
      i += 1
    }
  }

  /** A ball of light crackling where it struck, over [0, t1]. */
  private def sparkBall(t1: Float, size: Float, r: Float, g: Float, b: Float): Unit = {
    if (layer != AIR || T >= t1) return
    val a = 1f - smooth(T / t1)
    val rr = size * k * (1f + 0.15f * sinf(MS * 0.05f))
    sb.fillOvalSoft(cx, cy - H * 0.15f, rr * 2.4f, rr * 2f, r, g, b, 0.4f * a, 0f, 16)
    sb.fillOval(cx, cy - H * 0.15f, rr, rr * 0.9f, mix(r, 1f, 0.7f), mix(g, 1f, 0.7f), mix(b, 1f, 0.7f), a, 14)
    sb.fillStarFlare(cx, cy - H * 0.15f, rr * 3.4f, 2.6f, (MS * 0.004f) % TWO_PI, 0.5f, 1f, 1f, mix(b, 1f, 0.6f), 0.9f * a)
  }

  /** A hexagon on the ground, `rho` of the footprint out, turned by `rot`. */
  private def hexRing(rho: Float, rot: Float, w: Float, r: Float, g: Float, b: Float, a: Float,
                      ox: Float = 0f, oy: Float = 0f): Unit = {
    if (a <= 0.01f) return
    var i = 0
    while (i < 6) {
      val th = rot + i * TWO_PI / 6
      _xs(i) = ox + gx(cosf(th), rho); _ys(i) = oy + gy(sinf(th), rho)
      i += 1
    }
    sb.strokePolygon(_xs, _ys, 6, w + 2.4f, ink(r), ink(g), ink(b), 0.5f * a)
    sb.strokePolygon(_xs, _ys, 6, w, r, g, b, a)
  }

  /** Streaks racing out along the ground from `from` to `to` of the footprint over [t0, t1],
    * each a stroke that is thin and clear at its tail and bright at its head. */
  private def streaks(n: Int, t0: Float, t1: Float, from: Float, to: Float, len: Float, w: Float,
                      r: Float, g: Float, b: Float, a0: Float, sd: Int): Unit = {
    if (layer != GROUND) return
    var i = 0
    while (i < n) {
      val s0 = seed * 47 + sd * 13
      val start = t0 + (t1 - t0) * 0.35f * h01(s0, i)
      val p = seg(T, start, t1)
      if (p > 0f && p < 1f) {
        val th = (i + 0.5f * hash(s0, 30 + i)) * TWO_PI / n
        val c = cosf(th); val s = sinf(th)
        val head = from + (to - from) * easeOut(p)
        val tl = Math.max(from, head - len)
        _xs(0) = gx(c, tl); _ys(0) = gy(s, tl)
        _xs(1) = gx(c, head); _ys(1) = gy(s, head)
        val a = a0 * (1f - p) * Math.min(1f, p * 6f)
        sb.strokePolylineTapered(_xs, _ys, 2, 0.6f, w * k + 2.2f, ink(r), ink(g), ink(b), 0f, 0.5f * a)
        sb.strokePolylineTapered(_xs, _ys, 2, 0.4f, w * k, r, g, b, 0f, a)
      }
      i += 1
    }
  }

  private def electric(st: Int): Unit = {
    (st: @scala.annotation.switch) match {
      case S_STORM =>
        scar(7, 0.8f, 1f, 0.9f, 0.35f, 0.9f)
        scorch(0.35f, 0.1f, 0.1f, 0.08f, 0.35f)
        if (layer != AIR) return
        flash(0.08f, 0.55f, 1f, 0.95f, 0.6f)
        sparkBall(0.35f, 6f, 1f, 0.9f, 0.35f)
        arcBurst(6, 0.95f, 0.5f, 70f, 4f, 1f, 0.9f, 0.35f, 1)
        rimArcs(7, 0.05f, 0.55f, 80f, 1f, 0.92f, 0.45f)
        debris(D_SPARK, 10, 0f, 0.35f, 1f, 18f, 2f, 1f, 0.9f, 0.4f, 1)
      case S_THUNDER =>
        scar(9, 0.9f, 1f, 0.95f, 0.6f, 1f)
        scorch(0.45f, 0.08f, 0.08f, 0.07f, 0.45f)
        shockRing(0f, 0.3f, 7f, 1f, 0.92f, 0.5f, 0.9f)
        if (layer != AIR) return
        flash(0.1f, 0.9f, 1f, 1f, 0.85f)
        // The bolt out of the sky, and a second strike down the same channel
        var s = 0
        while (s < 2) {
          val t0 = if (s == 0) 0f else 0.13f
          val sp = seg(T, t0, t0 + 0.13f)
          if (sp > 0f && sp < 1f) {
            val a = 1f - sp * sp
            val topX = cx + hash(seed, 950 + s) * 12f * k
            bolt(topX, cy - 230f * k, cx, cy - 2f, 10, 14f * k, seed * 7 + s, 15f * k, 1f, 0.95f, 0.55f, a)
          }
          s += 1
        }
        sparkBall(0.3f, 8f, 1f, 0.95f, 0.6f)
        if (T > 0.05f && T < 0.45f) {
          // Arcs crawling out over the ground from where it struck
          val epoch = (MS / 75f).toInt
          val a = life(seg(T, 0.05f, 0.45f), 0.1f, 0.4f)
          var i = 0
          while (i < 4) {
            val th = (i + 0.5f * hash(seed + epoch, 960 + i)) * TWO_PI / 4
            bolt(cx, cy, gx(cosf(th), 0.85f), gy(sinf(th), 0.85f), 7, 4f * k, seed + epoch * 9 + i, 2.2f * k, 1f, 0.95f, 0.55f, a)
            i += 1
          }
        }
        debris(D_SPARK, 12, 0f, 0.4f, 1f, 24f, 2.2f, 1f, 0.95f, 0.6f, 1)
        puffs(3, 0.3f, 1f, 0.1f, 30f, 8f, 16f, 0.45f, 0.45f, 0.48f, 0.5f, 2, lift = 4f)
      case S_TESLA =>
        scar(6, 0.75f, 0.45f, 0.85f, 1f, 0.9f)
        shockRing(0f, 0.25f, 3f, 0.55f, 0.9f, 1f, 1f)
        shockRing(0.06f, 0.32f, 2.6f, 0.7f, 0.95f, 1f, 0.9f)
        shockRing(0.12f, 0.4f, 2.2f, 0.85f, 1f, 1f, 0.8f)
        if (layer != AIR) return
        flash(0.08f, 0.55f, 0.5f, 0.85f, 1f)
        sparkBall(0.38f, 6f, 0.45f, 0.85f, 1f)
        arcBurst(5, 0.95f, 0.5f, 70f, 3.8f, 0.45f, 0.85f, 1f, 1)
        debris(D_SPARK, 10, 0f, 0.35f, 1f, 18f, 2f, 0.5f, 0.9f, 1f, 1)
      case S_TESLA_FORK =>
        scar(5, 0.7f, 0.55f, 0.9f, 1f, 0.85f)
        shockRing(0f, 0.3f, 3.4f, 0.6f, 0.92f, 1f, 0.95f)
        if (layer != AIR) return
        flash(0.07f, 0.45f, 0.55f, 0.9f, 1f)
        sparkBall(0.3f, 5f, 0.55f, 0.9f, 1f)
        arcBurst(3, 0.95f, 0.45f, 80f, 4.6f, 0.55f, 0.9f, 1f, 1, fork = true)
        debris(D_SPARK, 8, 0f, 0.35f, 1f, 16f, 2f, 0.6f, 0.92f, 1f, 1)
      case _ => // S_OVERCLOCK
        val ga = life(T, 0.08f, 0.5f) * 0.9f
        if (layer == GROUND) {
          val rot = T * 0.6f
          hexRing(0.92f, rot, 3f * k, 0.3f, 1f, 0.78f, ga)
          hexRing(0.56f, rot + PI / 6, 2.2f * k, 0.55f, 1f, 0.9f, ga * 0.9f)
          var i = 0
          while (i < 6) {
            val th = rot + i * TWO_PI / 6
            sb.strokeLine(gx(cosf(th), 0.56f), gy(sinf(th), 0.56f), gx(cosf(th), 0.92f), gy(sinf(th), 0.92f),
              1.6f * k, 0.3f, 1f, 0.78f, 0.7f * ga)
            i += 1
          }
        }
        shockRing(0f, 0.35f, 5f, 0.3f, 1f, 0.78f, 0.9f)
        streaks(14, 0.02f, 0.42f, 0.2f, 1.1f, 0.3f, 3f, 0.55f, 1f, 0.9f, 0.9f, 1)
        if (layer != AIR) return
        flash(0.08f, 0.5f, 0.4f, 1f, 0.8f)
        debris(D_SQUARE, 12, 0.05f, 0.9f, 0.6f, 50f, 2.2f, 0.35f, 1f, 0.8f, 1)
        motes(8, 0.05f, 0.8f, 0.7f, 50f, 1.3f, 0.35f, 1f, 0.85f, 1f, 2)
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  EARTH
  // ═══════════════════════════════════════════════════════════════════

  /**
   * Spikes standing up out of the ground on a ring at `rho` (± `jitter`): each rises over
   * [t0, t0 + 0.09], holds, and sinks back from `sink`. `tilt` leans them out from the middle.
   * Only this layer's, far to near.
   */
  private def spikeRing(n0: Int, rho: Float, jitter: Float, h0: Float, t0: Float, sink: Float,
                        r: Float, g: Float, b: Float, glass: Boolean, sd: Int, tilt: Float = 0.12f): Unit = {
    val n = Math.min(32, n0)
    val s0 = seed * 37 + sd * 101
    var i = 0
    while (i < n) {
      val th = (i + 0.4f * hash(s0, i)) * TWO_PI / n
      val rr = rho + jitter * hash(s0, 20 + i)
      _ux(i) = th
      _us(i) = rr
      _ordY(i) = gy(sinf(th), rr)
      i += 1
    }
    sortFarToNear(n)
    var j = 0
    while (j < n) {
      val i2 = _ord(j)
      val y = _ordY(i2)
      if (mine(y)) {
        val th = _ux(i2)
        val start = t0 + 0.05f * h01(s0, 40 + i2)
        val up = easeOut3(seg(T, start, start + 0.09f))
        val down = smooth(seg(T, sink + 0.08f * h01(s0, 50 + i2), 1f))
        val hh = h0 * k * (0.7f + 0.5f * h01(s0, 60 + i2)) * up * (1f - down)
        val w = h0 * k * 0.28f * (0.8f + 0.35f * h01(s0, 70 + i2))
        val lean = (cosf(th) * tilt + 0.15f * hash(s0, 80 + i2)) * hh
        spike(gx(cosf(th), _us(i2)), y, w, hh, lean, 1f - down * 0.5f, r, g, b, glass)
      }
      j += 1
    }
  }

  /** A circle of glyphs on the ground: a ring at `rho`, and `n` marks inside it, glowing. */
  private def runeCircle(rho: Float, n: Int, rot: Float, w: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    if (layer != GROUND || a <= 0.01f) return
    sb.strokeOval(cx, cy, W * rho, H * rho, w * k + 2.6f, ink(r), ink(g), ink(b), 0.5f * a, 24)
    sb.strokeOval(cx, cy, W * rho, H * rho, w * k, r, g, b, a, 24)
    sb.strokeOval(cx, cy, W * rho * 0.8f, H * rho * 0.8f, w * 0.5f * k, r, g, b, 0.7f * a, 24)
    var i = 0
    while (i < n) {
      val th = rot + i * TWO_PI / n
      val c = cosf(th); val s = sinf(th)
      // An angular mark: a stroke across the band with a hook, different on alternate marks
      val x0 = gx(c, rho * 0.84f); val y0 = gy(s, rho * 0.84f)
      val x1 = gx(c, rho * 0.96f); val y1 = gy(s, rho * 0.96f)
      val tx = -s * 3f * k; val ty = c * 1.5f * k
      _xs(0) = x0; _ys(0) = y0
      _xs(1) = x1; _ys(1) = y1
      _xs(2) = x1 + (if ((i & 1) == 0) tx else -tx); _ys(2) = y1 + (if ((i & 1) == 0) ty else -ty)
      sb.strokePolyline(_xs, _ys, 3, 1.4f * k + 1.6f, ink(r), ink(g), ink(b), 0.5f * a)
      sb.strokePolyline(_xs, _ys, 3, 1.4f * k, lit(r), lit(g), lit(b), a)
      i += 1
    }
  }

  /** An ankh of gold at (x, y), `s` its scale: a loop, a shaft and a crossbar, inked. */
  private def ankh(x: Float, y: Float, s: Float, a: Float): Unit = {
    if (a <= 0.02f) return
    var pass = 0
    while (pass < 3) {
      val grow = if (pass == 0) 2.2f else 0f
      val wk = if (pass == 2) 0.35f else 1f
      val cr = if (pass == 0) 0.3f else if (pass == 1) 1f else 1f
      val cg = if (pass == 0) 0.2f else if (pass == 1) 0.8f else 0.97f
      val cb = if (pass == 0) 0.05f else if (pass == 1) 0.28f else 0.75f
      val al = if (pass == 0) 0.9f * a else a
      sb.strokeOval(x, y - 7.5f * s, 3.8f * s, 4.8f * s, 2.8f * s * wk + grow, cr, cg, cb, al, 16)
      sb.strokeLine(x, y - 2.8f * s, x, y + 9f * s, 3f * s * wk + grow, cr, cg, cb, al)
      sb.strokeLine(x - 5.6f * s, y - 1.6f * s, x + 5.6f * s, y - 1.6f * s, 2.6f * s * wk + grow, cr, cg, cb, al)
      pass += 1
    }
  }

  /** Spouts of sand shooting up round a ring and falling back. */
  private def sandSpouts(n: Int, rho: Float, h0: Float, t0: Float): Unit = {
    val s0 = seed * 59
    var i = 0
    while (i < n) {
      val th = (i + 0.4f * hash(s0, i)) * TWO_PI / n
      _ux(i) = th; _ordY(i) = gy(sinf(th), rho)
      i += 1
    }
    sortFarToNear(n)
    var j = 0
    while (j < n) {
      val i2 = _ord(j)
      val y = _ordY(i2)
      if (mine(y)) {
        val th = _ux(i2)
        val start = t0 + 0.08f * h01(s0, 30 + i2)
        val p = seg(T, start, start + 0.55f)
        if (p > 0f && p < 1f) {
          val hh = h0 * k * (0.75f + 0.4f * h01(s0, 40 + i2)) * (if (p < 0.3f) easeOut3(p / 0.3f) else 1f - smooth((p - 0.3f) / 0.7f))
          val x = gx(cosf(th), rho)
          val a = tail(p, 0.7f)
          val tx = x + cosf(th) * hh * 0.15f; val ty = y - hh
          val bw = 2.6f * k; val tw = 6.6f * k * (0.6f + 0.4f * Math.min(1f, hh / (h0 * k)))
          var pass = 0
          while (pass < 2) {
            val gr = if (pass == 0) 1.5f else 0f
            _xs(0) = x - bw - gr; _ys(0) = y + gr
            _xs(1) = tx - tw - gr; _ys(1) = ty
            _xs(2) = tx + tw + gr; _ys(2) = ty
            _xs(3) = x + bw + gr; _ys(3) = y + gr
            if (pass == 0) sb.fillPolygon(_xs, _ys, 4, 0.3f, 0.22f, 0.1f, 0.8f * a)
            else sb.fillPolygon(_xs, _ys, 4, 0.86f, 0.72f, 0.45f, a)
            pass += 1
          }
          // The plume billowing off its top
          var f = -1
          while (f <= 1) {
            val fx = tx + f * tw * 0.7f; val fy = ty - (if (f == 0) 2.6f * k else 0f)
            sb.fillOval(fx, fy, tw * 0.62f + 1.5f, tw * 0.5f + 1.5f, 0.3f, 0.22f, 0.1f, 0.7f * a, 12)
            f += 1
          }
          f = -1
          while (f <= 1) {
            val fx = tx + f * tw * 0.7f; val fy = ty - (if (f == 0) 2.6f * k else 0f)
            sb.fillOval(fx, fy, tw * 0.62f, tw * 0.5f, 0.95f, 0.85f, 0.6f, a, 12)
            f += 1
          }
        }
      }
      j += 1
    }
  }

  private def earth(st: Int): Unit = {
    (st: @scala.annotation.switch) match {
      case S_SEISMIC =>
        cracks(10, 0.95f, 0.02f, 0.15f, 4.2f, 0.12f, 0.1f, 0.08f, 0.75f)
        scorch(0.55f, 0.3f, 0.24f, 0.16f, 0.3f)
        shockRing(0f, 0.35f, 8f, 0.8f, 0.68f, 0.5f, 0.9f)
        spikeRing(7, 0.45f, 0.06f, 18f, 0.02f, 0.72f, 0.56f, 0.49f, 0.4f, glass = false, 1)
        spikeRing(12, 0.8f, 0.06f, 26f, 0.07f, 0.74f, 0.52f, 0.45f, 0.37f, glass = false, 2)
        if (layer != AIR) return
        puffs(12, 0.08f, 0.9f, 0.82f, 10f, 14f, 26f, 0.72f, 0.62f, 0.48f, 0.5f, 1, outward = 0.18f, flat = 0.56f, inkK = 0.4f)
        debris(D_CHIP, 12, 0.02f, 0.8f, 1f, 40f, 3f, 0.5f, 0.42f, 0.34f, 1, shadow = true)
      case S_TREMOR =>
        cracks(8, 0.85f, 0.02f, 0.14f, 3.8f, 0.12f, 0.09f, 0.06f, 0.75f)
        scorch(0.5f, 0.3f, 0.22f, 0.12f, 0.3f)
        shockRing(0f, 0.35f, 8f, 0.75f, 0.62f, 0.4f, 0.9f)
        shockRing(0.08f, 0.45f, 4f, 0.5f, 0.72f, 0.36f, 0.8f)
        if (layer != AIR) return
        debris(D_CHIP, 14, 0.01f, 0.8f, 0.95f, 44f, 3.4f, 0.45f, 0.36f, 0.24f, 1, shadow = true)
        // Flecks of the beetle's bronze-green shell
        debris(D_SHARD, 6, 0f, 0.6f, 0.8f, 30f, 2.6f, 0.4f, 0.66f, 0.42f, 2)
        puffs(10, 0.05f, 0.9f, 0.78f, 12f, 14f, 26f, 0.68f, 0.56f, 0.4f, 0.5f, 3, outward = 0.2f, flat = 0.56f, inkK = 0.4f)
      case S_GOLEM =>
        runeCircle(0.62f, 8, T * 0.4f, 2.6f, 1f, 0.7f, 0.3f, 0.9f * life(T, 0.06f, 0.5f))
        cracks(8, 0.9f, 0.02f, 0.14f, 4.4f, 0.14f, 0.13f, 0.13f, 0.8f, 1f, 0.7f, 0.3f, 0.8f)
        shockRing(0f, 0.35f, 9f, 0.62f, 0.62f, 0.6f, 0.9f)
        if (layer != AIR) return
        debris(D_BLOCK, 10, 0.01f, 0.8f, 0.95f, 50f, 4.6f, 0.52f, 0.52f, 0.54f, 1, shadow = true)
        puffs(10, 0.05f, 0.9f, 0.78f, 12f, 14f, 26f, 0.62f, 0.6f, 0.58f, 0.5f, 2, outward = 0.2f, flat = 0.56f, inkK = 0.4f)
        motes(6, 0.05f, 0.7f, 0.6f, 30f, 1.4f, 1f, 0.72f, 0.3f, 1f, 3)
      case S_POUND =>
        scorch(0.42f, 0.2f, 0.14f, 0.08f, 0.6f)
        if (layer == GROUND) {
          // The crater's raised rim
          val ra = Math.min(1f, T * 10f) * tail(T, 0.6f)
          sb.strokeOval(cx, cy, W * 0.46f, H * 0.46f, 7f * k + 2.4f, 0.14f, 0.1f, 0.06f, 0.6f * ra, 22)
          sb.strokeOval(cx, cy, W * 0.46f, H * 0.46f, 7f * k, 0.55f, 0.42f, 0.26f, 0.85f * ra, 22)
          sb.strokeArc(cx, cy, W * 0.46f, H * 0.46f, PI * 1.05f, PI * 0.9f, 2f * k, 0.72f, 0.6f, 0.42f, 0.8f * ra, 14)
        }
        cracks(7, 0.75f, 0.02f, 0.12f, 3.6f, 0.13f, 0.09f, 0.05f, 0.75f)
        shockRing(0f, 0.32f, 9f, 0.78f, 0.66f, 0.48f, 0.9f)
        if (layer != AIR) return
        debris(D_CHIP, 10, 0.01f, 0.8f, 0.9f, 46f, 3.6f, 0.4f, 0.28f, 0.16f, 1, shadow = true)
        debris(D_LEAF, 6, 0.01f, 0.75f, 0.8f, 38f, 2.8f, 0.35f, 0.6f, 0.22f, 2)
        puffs(6, 0.05f, 0.9f, 0.5f, 18f, 13f, 24f, 0.7f, 0.58f, 0.42f, 0.5f, 3, outward = 0.3f, flat = 0.6f, inkK = 0.45f)
      case S_MECH =>
        scorch(0.45f, 0.12f, 0.12f, 0.14f, 0.45f)
        cracks(6, 0.7f, 0.02f, 0.12f, 3.4f, 0.1f, 0.1f, 0.12f, 0.7f)
        if (layer == GROUND) {
          // A hexagonal shock off the mech's foot
          val p = seg(T, 0f, 0.35f)
          if (p < 1f) hexRing(easeOut3(p), 0.2f, 6f * k * (1f - 0.5f * p), 0.55f, 0.66f, 0.82f, 0.95f * (1f - p))
        }
        if (layer != AIR) return
        flash(0.06f, 0.45f, 1f, 0.8f, 0.5f)
        debris(D_SPARK, 16, 0f, 0.35f, 1f, 12f, 2.2f, 1f, 0.65f, 0.25f, 1)
        debris(D_HEX, 7, 0.01f, 0.8f, 0.85f, 34f, 2.6f, 0.56f, 0.58f, 0.62f, 2, shadow = true)
        puffs(5, 0.1f, 0.95f, 0.35f, 34f, 8f, 18f, 0.9f, 0.92f, 0.95f, 0.6f, 3, lift = 4f, inkK = 0.5f)
      case _ => // S_SCALES: Anubis's judgment
        val ga = life(T, 0.1f, 0.65f)
        runeCircle(0.86f, 16, T * 0.3f, 3.4f, 1f, 0.8f, 0.3f, 0.95f * ga)
        scorch(0.6f, 0.75f, 0.62f, 0.35f, 0.25f)
        sandSpouts(10, 0.8f, 44f, 0.05f)
        if (layer != AIR) return
        val ap = seg(T, 0.03f, 0.88f)
        if (ap > 0f && ap < 1f) {
          val ay = cy - (16f + 36f * easeOut(ap)) * k
          val aa = life(ap, 0.12f, 0.6f)
          sb.fillOvalSoft(cx, ay - 6f * k, 34f * k, 34f * k, 1f, 0.85f, 0.4f, 0.45f * aa, 0f, 16)
          ankh(cx, ay, 2.2f * k, aa)
        }
        debris(D_FLECK, 14, 0.05f, 0.8f, 0.95f, 40f, 2.4f, 0.86f, 0.72f, 0.45f, 1)
        motes(10, 0.05f, 0.9f, 0.8f, 50f, 1.5f, 1f, 0.85f, 0.4f, 1f, 2)
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  ICE
  // ═══════════════════════════════════════════════════════════════════

  /** Frost spreading over the ground in a six-armed flake, each arm branching twice. */
  private def snowflake(size: Float, grow: Float, w: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    if (layer != GROUND || a <= 0.01f || grow <= 0.01f) return
    val rot = hash(seed, 1000) * 0.5f
    var i = 0
    while (i < 6) {
      val th = rot + i * PI / 3
      val c = cosf(th); val s = sinf(th)
      val len = size * grow
      _xs(0) = cx; _ys(0) = cy
      _xs(1) = gx(c, len); _ys(1) = gy(s, len)
      sb.strokePolylineTapered(_xs, _ys, 2, w * k + 2.6f, 1.6f, ink(r), ink(g), ink(b), 0.45f * a, 0.2f * a)
      sb.strokePolylineTapered(_xs, _ys, 2, w * k, 0.8f, r, g, b, a, 0.7f * a)
      var br = 0
      while (br < 2) {
        val at = len * (if (br == 0) 0.4f else 0.68f)
        val bl = len * (if (br == 0) 0.26f else 0.18f)
        val bx = gx(c, at); val by = gy(s, at)
        var side = -1
        while (side <= 1) {
          val ba = th + side * 0.62f
          sb.strokeLine(bx, by, bx + cosf(ba) * bl * W, by + sinf(ba) * bl * H, w * 0.55f * k, r, g, b, 0.85f * a)
          side += 2
        }
        br += 1
      }
      i += 1
    }
  }

  private def ice(st: Int): Unit = {
    (st: @scala.annotation.switch) match {
      case S_ICE_QUAKE =>
        scorch(0.8f, 0.7f, 0.88f, 1f, 0.3f)
        snowflake(0.92f, easeOut3(seg(T, 0f, 0.25f)), 3.2f, 0.78f, 0.93f, 1f, 0.9f * tail(T, 0.62f))
        shockRing(0f, 0.35f, 6f, 0.8f, 0.95f, 1f, 0.9f)
        spikeRing(5, 0.4f, 0.05f, 24f, 0.03f, 0.72f, 0.6f, 0.85f, 1f, glass = true, 1)
        spikeRing(11, 0.78f, 0.05f, 30f, 0.08f, 0.74f, 0.55f, 0.82f, 1f, glass = true, 2)
        if (layer != AIR) return
        // The spikes shatter
        debris(D_SHARD, 12, 0.72f, 1f, 1.05f, 22f, 2.8f, 0.7f, 0.9f, 1f, 1, from = 0.75f)
        puffs(8, 0.05f, 1f, 0.7f, 14f, 10f, 20f, 0.88f, 0.95f, 1f, 0.5f, 2, outward = 0.2f, flat = 0.6f, inkK = 0.4f)
        glints(6, 0.05f, 0.8f, 0.9f, 30f, 10f, 0.85f, 0.97f, 1f, 1f)
      case S_AVALANCHE =>
        scorch(0.6f, 0.92f, 0.96f, 1f, 0.45f, 0.55f)
        shockRing(0f, 0.32f, 7f, 0.85f, 0.95f, 1f, 0.9f)
        if (layer != AIR) return
        puffs(9, 0.02f, 1f, 0.3f, 26f, 12f, 26f, 0.93f, 0.96f, 1f, 0.9f, 1, outward = 0.5f, flat = 0.72f)
        debris(D_SHARD, 9, 0f, 0.75f, 0.95f, 40f, 3.4f, 0.62f, 0.85f, 1f, 2, shadow = true)
        motes(10, 0.05f, 0.9f, 0.9f, 16f, 1.2f, 0.95f, 0.98f, 1f, 0.9f, 3)
        glints(4, 0.05f, 0.7f, 0.8f, 24f, 9f, 0.9f, 0.97f, 1f, 0.9f)
      case _ => // S_FROST
        scorch(0.6f, 0.7f, 0.88f, 1f, 0.3f)
        snowflake(0.8f, easeOut3(seg(T, 0f, 0.22f)), 2.6f, 0.78f, 0.93f, 1f, 0.9f * tail(T, 0.6f))
        spikeRing(6, 0.55f, 0.06f, 18f, 0.04f, 0.7f, 0.6f, 0.85f, 1f, glass = true, 1)
        if (layer != AIR) return
        puffs(5, 0.05f, 1f, 0.5f, 12f, 8f, 16f, 0.88f, 0.95f, 1f, 0.5f, 2, outward = 0.2f, flat = 0.6f, inkK = 0.4f)
        glints(4, 0.05f, 0.7f, 0.8f, 24f, 9f, 0.85f, 0.97f, 1f, 1f)
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  NATURE
  // ═══════════════════════════════════════════════════════════════════

  /** Vines whipping up out of the ground all over the footprint, thorned and leafed, curling,
    * and drawing back into it. Only this layer's, far to near. */
  private def vines(n0: Int, len0: Float, w0: Float, r: Float, g: Float, b: Float, sd: Int): Unit = {
    val n = Math.min(24, (n0 * (0.6f + 0.4f * det) + 0.5f).toInt)
    val s0 = seed * 67 + sd * 7
    var i = 0
    while (i < n) {
      val th = (i + 0.5f * hash(s0, i)) * TWO_PI / n
      val rr = 0.25f + 0.67f * h01(s0, 10 + i)
      _ux(i) = gx(cosf(th), rr); _ordY(i) = gy(sinf(th), rr); _us(i) = rr; _uy(i) = cosf(th)
      i += 1
    }
    sortFarToNear(n)
    var j = 0
    while (j < n) {
      val i2 = _ord(j)
      val x = _ux(i2); val y = _ordY(i2)
      if (mine(y)) {
        val start = 0.02f + 0.1f * _us(i2)
        val grow = easeOut3(seg(T, start, start + 0.12f)) * (1f - smooth(seg(T, 0.72f + 0.06f * h01(s0, 20 + i2), 0.97f)))
        if (grow > 0.02f) {
          val side = if (_uy(i2) >= 0f) 1f else -1f
          val curl = side * (1.1f + 0.5f * h01(s0, 30 + i2)) + 0.2f * sinf(MS * 0.005f + i2)
          sb.fillOval(x, y, 5f * k, 2f * k, 0.18f, 0.12f, 0.06f, 0.8f * grow, 10)
          tendril(x, y, -PI * 0.5f + side * 0.22f + 0.15f * hash(s0, 40 + i2), len0 * k * (0.75f + 0.45f * h01(s0, 50 + i2)),
            grow, curl, w0 * k, r, g, b, 1f)
          // Thorns along it and a couple of leaves off it
          var p = 2
          while (p < 8) {
            val ax = _px(p); val ay = _py(p)
            val nx = _py(p + 1) - ay; val ny = -(_px(p + 1) - ax)
            val nl = Math.max(0.01f, Math.sqrt(nx * nx + ny * ny).toFloat)
            val ts = 2.6f * k * grow * (if ((p & 2) == 0) 1f else -1f)
            _xs(0) = ax - (_px(p + 1) - ax) * 0.25f; _ys(0) = ay - (_py(p + 1) - ay) * 0.25f
            _xs(1) = ax + nx / nl * ts; _ys(1) = ay + ny / nl * ts
            _xs(2) = ax + (_px(p + 1) - ax) * 0.25f; _ys(2) = ay + (_py(p + 1) - ay) * 0.25f
            sb.fillPolygon(_xs, _ys, 3, ink(r) * 1.4f, ink(g) * 1.4f, ink(b) * 1.4f, grow)
            p += 2
          }
          piece(D_LEAF, _px(4) + side * 3f * k, _py(4), 2.6f * k * grow, side * 0.6f, 0.35f, 0.72f, 0.28f, grow)
          piece(D_LEAF, _px(6) - side * 3f * k, _py(6), 2.2f * k * grow, -side * 0.8f, 0.4f, 0.78f, 0.3f, grow)
        }
      }
      j += 1
    }
  }

  /** Great roots arching up out of the ground along the ray `th`, from `r0` to `r1` of the
    * footprint, standing `hMax` high at the top of the arch when `grow` is 1. */
  private def rootArch(th: Float, r0: Float, r1: Float, hMax: Float, grow: Float, w0: Float, a: Float): Unit = {
    if (grow <= 0.02f || a <= 0.02f) return
    val c = cosf(th); val s = sinf(th)
    val n = 9
    var i = 0
    while (i < n) {
      val u = i.toFloat / (n - 1)
      val rho = r0 + (r1 - r0) * u
      _px(i) = gx(c, rho)
      _py(i) = gy(s, rho) - hMax * grow * sinf(u * PI)
      _pw(i) = w0 * (1f - 0.5f * u) + 2.8f
      _pa(i) = 0.9f * a
      i += 1
    }
    // Mounds of earth where it goes in and comes out
    sb.fillOval(gx(c, r0), gy(s, r0), w0 * 1.3f + 1.6f, w0 * 0.5f + 1.6f, 0.12f, 0.08f, 0.05f, 0.85f * a, 12)
    sb.fillOval(gx(c, r0), gy(s, r0), w0 * 1.3f, w0 * 0.5f, 0.42f, 0.3f, 0.18f, a, 12)
    sb.fillOval(gx(c, r1), gy(s, r1), w0 * 0.9f + 1.6f, w0 * 0.36f + 1.6f, 0.12f, 0.08f, 0.05f, 0.85f * a, 12)
    sb.fillOval(gx(c, r1), gy(s, r1), w0 * 0.9f, w0 * 0.36f, 0.42f, 0.3f, 0.18f, a, 12)
    sb.strokePolylineVar(_px, _py, _pw, _pa, n, 0.12f, 0.08f, 0.05f)
    i = 0
    while (i < n) { _pw(i) -= 2.8f; _pa(i) = a; i += 1 }
    sb.strokePolylineVar(_px, _py, _pw, _pa, n, 0.44f, 0.3f, 0.18f)
    i = 0
    while (i < n) { _py(i) -= _pw(i) * 0.22f; _pw(i) *= 0.3f; _pa(i) = 0.85f * a; i += 1 }
    sb.strokePolylineVar(_px, _py, _pw, _pa, n, 0.66f, 0.5f, 0.32f)
    // Bark rings across it, and moss on its back
    var m = 2
    while (m < n - 1) {
      val tx = _px(m + 1) - _px(m - 1); val ty = _py(m + 1) - _py(m - 1)
      val tl = Math.max(0.01f, Math.sqrt(tx * tx + ty * ty).toFloat)
      val half = w0 * (1f - 0.5f * m / (n - 1)) * 0.45f
      sb.strokeLine(_px(m) - ty / tl * half, _py(m) + tx / tl * half, _px(m) + ty / tl * half, _py(m) - tx / tl * half,
        1.3f, 0.2f, 0.13f, 0.07f, 0.8f * a)
      m += 2
    }
    sb.fillOval(_px(3), _py(3) - w0 * 0.2f, w0 * 0.55f, w0 * 0.3f, 0.36f, 0.6f, 0.24f, 0.9f * a, 10)
  }

  /** The Spider's web, cast out over the ground: threads racing out from the middle, then the
    * spiral strung between them, sagging a little between each pair. */
  private def web(): Unit = {
    if (layer != GROUND) return
    val a = tail(T, 0.72f)
    if (a <= 0.01f) return
    val n = 12
    val out = easeOut3(seg(T, 0f, 0.18f))
    val rot = hash(seed, 1100) * 0.3f
    var i = 0
    while (i < n) {
      val th = rot + (i + 0.2f * hash(seed, 1110 + i)) * TWO_PI / n
      val rr = 0.95f + 0.05f * hash(seed, 1120 + i)
      _ux(i) = cosf(th); _uy(i) = sinf(th); _us(i) = rr
      val ex = gx(_ux(i), rr * out); val ey = gy(_uy(i), rr * out)
      sb.strokeLine(cx, cy, ex, ey, 2.8f, 0.2f, 0.2f, 0.26f, 0.35f * a)
      sb.strokeLine(cx, cy, ex, ey, 1.3f * k, 0.95f, 0.95f, 1f, 0.95f * a)
      i += 1
    }
    var ring = 0
    while (ring < 5) {
      val rr = 0.2f + 0.17f * ring
      val on = seg(T, 0.06f + 0.035f * ring, 0.14f + 0.035f * ring)
      if (on > 0f) {
        var m = 0
        i = 0
        while (i <= n) {
          val i0 = i % n
          val pr = rr * _us(i0)
          _px(m) = gx(_ux(i0), pr); _py(m) = gy(_uy(i0), pr); m += 1
          if (i < n) {
            val i1 = (i + 1) % n
            // Sagging toward the middle between two spokes
            val mx = (_ux(i0) + _ux(i1)) * 0.5f; val my = (_uy(i0) + _uy(i1)) * 0.5f
            val ml = Math.sqrt(mx * mx + my * my).toFloat
            val sag = rr * 0.9f * (_us(i0) + _us(i1)) * 0.5f
            _px(m) = gx(mx / ml, sag * ml); _py(m) = gy(my / ml, sag * ml); m += 1
          }
          i += 1
        }
        val ra = a * on
        sb.strokePolyline(_px, _py, m, 2.6f, 0.2f, 0.2f, 0.26f, 0.3f * ra)
        sb.strokePolyline(_px, _py, m, 1.1f * k, 0.95f, 0.95f, 1f, 0.9f * ra)
      }
      ring += 1
    }
    // Silk bunched where it landed
    sb.fillOval(cx, cy, 6f * k + 1.4f, 3.2f * k + 1.4f, 0.25f, 0.25f, 0.3f, 0.6f * a, 12)
    sb.fillOval(cx, cy, 6f * k, 3.2f * k, 0.96f, 0.96f, 1f, a, 12)
  }

  private def nature(st: Int): Unit = {
    (st: @scala.annotation.switch) match {
      case S_THORNS =>
        scorch(0.42f, 0.15f, 0.2f, 0.08f, 0.35f)
        shockRing(0f, 0.3f, 4f, 0.5f, 0.8f, 0.3f, 0.85f)
        // A burst of thorns thrust out of where it struck, splaying out over the ground
        spikeRing(9, 0.36f, 0.1f, 24f, 0f, 0.5f, 0.34f, 0.54f, 0.18f, glass = false, 1, tilt = 1.1f)
        spikeRing(5, 0.13f, 0.05f, 18f, 0.02f, 0.5f, 0.3f, 0.48f, 0.16f, glass = false, 2, tilt = 0.6f)
        if (layer != AIR) return
        flash(0.06f, 0.3f, 0.7f, 1f, 0.5f, 0.6f)
        debris(D_LEAF, 5, 0.02f, 0.8f, 0.85f, 30f, 3f, 0.35f, 0.65f, 0.25f, 1)
        debris(D_DROP, 6, 0f, 0.5f, 0.75f, 20f, 2.2f, 0.55f, 0.75f, 0.2f, 2)
      case S_LEAVES =>
        scorch(0.4f, 0.25f, 0.45f, 0.15f, 0.25f)
        edgeRing(0.02f, 0.8f, 2f, 0.5f, 0.9f, 0.4f, 0.8f)
        if (layer != AIR) return
        flash(0.08f, 0.45f, 0.7f, 1f, 0.6f, 0.8f)
        // A whirl of leaves, a few of them gone gold
        swirl(D_LEAF, 11, 0f, 1f, 1f, 32f, 0.7f, 4.4f, 0.35f, 0.7f, 0.25f, 1)
        swirl(D_LEAF, 5, 0f, 1f, 0.9f, 38f, 0.6f, 4.2f, 0.88f, 0.75f, 0.25f, 2)
        motes(8, 0.05f, 0.9f, 0.6f, 34f, 1.5f, 0.6f, 1f, 0.5f, 1f, 3)
      case S_SPLINTERS =>
        scorch(0.3f, 0.2f, 0.14f, 0.08f, 0.3f)
        if (layer != AIR) return
        debris(D_SPLINTER, 12, 0f, 0.8f, 0.95f, 34f, 3.6f, 0.6f, 0.44f, 0.25f, 1, shadow = true)
        debris(D_CHIP, 6, 0f, 0.75f, 0.85f, 28f, 3f, 0.32f, 0.23f, 0.14f, 2)
        swirl(D_LEAF, 4, 0f, 0.95f, 0.8f, 30f, 0.5f, 3.2f, 0.4f, 0.64f, 0.26f, 3)
        debris(D_DROP, 4, 0f, 0.5f, 0.6f, 18f, 2.2f, 0.95f, 0.65f, 0.2f, 4)
        puffs(3, 0.02f, 0.8f, 0.2f, 16f, 7f, 14f, 0.72f, 0.6f, 0.44f, 0.6f, 5, outward = 0.3f, flat = 0.7f, inkK = 0.5f)
      case S_ENTANGLE =>
        scorch(0.5f, 0.18f, 0.3f, 0.1f, 0.3f)
        edgeRing(0.04f, 0.9f, 3f, 0.3f, 0.6f, 0.2f, 0.85f)
        vines(13, 56f, 8f, 0.26f, 0.55f, 0.18f, 1)
        if (layer != AIR) return
        debris(D_LEAF, 6, 0.05f, 0.95f, 0.9f, 40f, 3f, 0.35f, 0.66f, 0.26f, 1)
      case S_ROOTS =>
        cracks(8, 0.9f, 0.02f, 0.2f, 4.6f, 0.13f, 0.09f, 0.05f, 0.7f)
        scorch(0.45f, 0.22f, 0.16f, 0.1f, 0.3f)
        // The great roots, each belonging to the layer its middle stands in
        var i = 0
        while (i < 7) {
          val th = (i + 0.4f * hash(seed, 1200 + i)) * TWO_PI / 7
          val r0 = 0.2f + 0.15f * h01(seed, 1210 + i)
          val r1 = 0.62f + 0.28f * h01(seed, 1220 + i)
          if (mine(gy(sinf(th), (r0 + r1) * 0.5f))) {
            val start = 0.03f + 0.02f * i
            val grow = easeOut3(seg(T, start, start + 0.16f)) * (1f - smooth(seg(T, 0.74f, 0.97f)))
            rootArch(th, r0, r1, (16f + 10f * h01(seed, 1230 + i)) * k, grow, 7f * k, 1f)
          }
          i += 1
        }
        vines(5, 22f, 4f, 0.4f, 0.28f, 0.16f, 2)
        if (layer != AIR) return
        debris(D_CHIP, 12, 0.02f, 0.8f, 0.95f, 40f, 3.2f, 0.38f, 0.27f, 0.16f, 1, shadow = true)
        debris(D_LEAF, 4, 0.05f, 0.9f, 0.8f, 36f, 2.8f, 0.42f, 0.6f, 0.25f, 2)
        puffs(8, 0.05f, 0.85f, 0.7f, 12f, 14f, 26f, 0.6f, 0.5f, 0.38f, 0.45f, 3, flat = 0.56f, inkK = 0.4f)
      case _ => // S_WEB
        web()
        glints(5, 0.15f, 0.8f, 0.9f, 2f, 8f, 1f, 1f, 1f, 0.9f)
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  DARK
  // ═══════════════════════════════════════════════════════════════════

  /** A soul, or a spirit: a pale head (two dark eyes if `eyes`) and a straight tail of light
    * streaming away behind it, opposite (dx, dy). */
  private def soul(x: Float, y: Float, dx: Float, dy: Float, sz: Float, a: Float,
                   r: Float, g: Float, b: Float, eyes: Boolean): Unit = {
    if (a <= 0.02f) return
    val dl = Math.max(0.01f, Math.sqrt(dx * dx + dy * dy).toFloat)
    val ux = dx / dl; val uy = dy / dl
    var i = 0
    while (i < 4) {
      val t = i / 3f
      _px(i) = x - ux * sz * 3.4f * t; _py(i) = y - uy * sz * 3.4f * t
      _pw(i) = sz * 1.7f * (1f - t * 0.92f)
      _pa(i) = a * 0.75f * (1f - t)
      i += 1
    }
    sb.strokePolylineVar(_px, _py, _pw, _pa, 4, r, g, b)
    sb.fillOval(x, y, sz + 1.3f, sz * 1.05f + 1.3f, ink(r), ink(g), ink(b), 0.6f * a, 12)
    sb.fillOval(x, y, sz, sz * 1.05f, lit(r), lit(g), lit(b), a, 12)
    if (eyes) {
      sb.fillOval(x - sz * 0.34f, y - sz * 0.05f, sz * 0.17f, sz * 0.24f, 0.05f, 0.15f, 0.1f, a, 6)
      sb.fillOval(x + sz * 0.34f, y - sz * 0.05f, sz * 0.17f, sz * 0.24f, 0.05f, 0.15f, 0.1f, a, 6)
    }
  }

  /** A ring whose radius waves `waves` times round, `amp` of itself: a spectral shudder. */
  private def wavyRing(q: Float, amp: Float, waves: Int, phase: Float, w: Float,
                       r: Float, g: Float, b: Float, a: Float): Unit = {
    if (layer != GROUND || a <= 0.01f || q <= 0.01f) return
    val n = 40
    var i = 0
    while (i < n) {
      val th = i * TWO_PI / n
      val rr = q * (1f + amp * sinf(waves * th + phase))
      _px(i) = gx(cosf(th), rr); _py(i) = gy(sinf(th), rr)
      i += 1
    }
    sb.strokePolygon(_px, _py, n, w + 2.4f, ink(r), ink(g), ink(b), 0.45f * a)
    sb.strokePolygon(_px, _py, n, w, r, g, b, a)
  }

  /** The Banshee's face: a pale head rising out of the wail, hair streaming up off it, its mouth
    * opening wider as it screams (`open` 0-1). */
  private def wailFace(x: Float, y: Float, sz: Float, open: Float, a: Float): Unit = {
    if (a <= 0.02f) return
    var i = -1
    while (i <= 1) {
      tendril(x + i * sz * 0.45f, y - sz * 0.55f, -PI * 0.5f + i * 0.55f, sz * 1.7f, 1f, i * 0.9f + 0.2f,
        sz * 0.55f, 0.62f, 0.82f, 0.96f, 0.55f * a, n = 7, wisp = true)
      i += 1
    }
    sb.fillOvalSoft(x, y, sz * 1.9f, sz * 2f, 0.6f, 0.85f, 1f, 0.32f * a, 0f, 18)
    sb.fillOval(x, y, sz * 0.8f + 2f, sz + 2f, 0.12f, 0.22f, 0.32f, 0.7f * a, 18)
    sb.fillOval(x, y, sz * 0.8f, sz, 0.84f, 0.95f, 1f, 0.85f * a, 18)
    sb.fillOval(x - sz * 0.28f, y - sz * 0.12f, sz * 0.17f, sz * 0.25f, 0.06f, 0.1f, 0.2f, a, 10)
    sb.fillOval(x + sz * 0.28f, y - sz * 0.12f, sz * 0.17f, sz * 0.25f, 0.06f, 0.1f, 0.2f, a, 10)
    sb.fillOval(x, y + sz * 0.42f, sz * (0.16f + 0.08f * open), sz * (0.18f + 0.3f * open), 0.06f, 0.1f, 0.2f, a, 12)
  }

  private def dark(st: Int): Unit = {
    (st: @scala.annotation.switch) match {
      case S_SOUL_HARVEST =>
        scorch(0.7f, 0.05f, 0.12f, 0.08f, 0.45f)
        runeCircle(0.9f, 10, -T * 0.5f, 3f, 0.35f, 0.92f, 0.5f, 0.95f * life(T, 0.08f, 0.68f))
        if (layer != AIR) return
        // Souls rising out of the ground, then streaming in to whoever called them
        val n = Math.max(5, (9 * det + 0.5f).toInt)
        var i = 0
        while (i < n) {
          val th = (i + 0.5f * hash(seed, 1400 + i)) * TWO_PI / n
          val rr = 0.35f + 0.6f * h01(seed, 1410 + i)
          val bx = gx(cosf(th), rr); val by = gy(sinf(th), rr)
          val s1 = 0.03f + 0.1f * h01(seed, 1420 + i)
          val up = easeOut(seg(T, s1, s1 + 0.2f))
          val s2 = 0.36f + 0.1f * h01(seed, 1430 + i)
          val in = seg(T, s2, s2 + 0.34f)
          if (up > 0f && in < 1f) {
            val q = in * in
            val sx0 = bx; val sy0 = by - 16f * k * up
            val tx = cx; val ty = cy - 18f * k
            val swirl = sinf(in * PI) * 0.35f
            val x = mix(sx0, tx, q) + (ty - sy0) * swirl * 0.3f
            val y = mix(sy0, ty, q) - (tx - sx0) * swirl * 0.15f
            val dx = if (in > 0f) tx - sx0 else 0f
            val dy = if (in > 0f) ty - sy0 else -1f
            soul(x, y, dx, dy, 5.6f * k * (1f - 0.5f * q), Math.min(1f, up * 2f) * (1f - seg(in, 0.8f, 1f)),
              0.35f, 0.95f, 0.55f, eyes = true)
          }
          i += 1
        }
        flash(0.14f, 0.35f, 0.5f, 1f, 0.7f, 0.9f, at = 0.7f)
        motes(8, 0.05f, 0.95f, 0.8f, 36f, 1.4f, 0.45f, 1f, 0.6f, 0.9f, 1)
      case S_RAISE_DEAD =>
        scorch(0.6f, 0.3f, 0.85f, 0.45f, 0.22f, 0.6f)
        scorch(0.4f, 0.1f, 0.08f, 0.05f, 0.35f)
        // Hands clawing up out of the ground, closing on whatever is there, and sinking back
        val n = 5
        var i = 0
        while (i < n) {
          val th = (i + 0.4f * hash(seed, 1500 + i)) * TWO_PI / n
          val rr = 0.32f + 0.45f * h01(seed, 1510 + i)
          _ux(i) = gx(cosf(th), rr); _ordY(i) = gy(sinf(th), rr)
          i += 1
        }
        sortFarToNear(n)
        var j = 0
        while (j < n) {
          val i2 = _ord(j)
          val y = _ordY(i2)
          if (mine(y)) {
            val start = 0.03f + 0.08f * h01(seed, 1520 + i2)
            val rise = easeOut3(seg(T, start, start + 0.16f)) * (1f - smooth(seg(T, 0.7f, 0.95f)))
            val open = 1f - 0.8f * smooth(seg(T, 0.3f, 0.52f))
            GLProjectileRenderers.deadHand(sb, _ux(i2), y, rise, hash(seed, 1530 + i2) * 0.25f, open,
              Math.min(1f, rise * 3f), 1.15f * k)
          }
          j += 1
        }
        if (layer != AIR) return
        debris(D_BONE, 5, 0f, 0.7f, 0.8f, 26f, 2.4f, 0.92f, 0.9f, 0.8f, 1)
        debris(D_CHIP, 6, 0f, 0.6f, 0.75f, 24f, 2.6f, 0.32f, 0.24f, 0.15f, 2)
        motes(6, 0.1f, 0.9f, 0.6f, 40f, 1.4f, 0.45f, 1f, 0.5f, 0.9f, 3)
      case S_BONES =>
        scorch(0.4f, 0.15f, 0.12f, 0.18f, 0.35f)
        shockRing(0f, 0.3f, 5f, 0.75f, 0.6f, 0.95f, 0.85f)
        if (layer != AIR) return
        debris(D_BONE, 12, 0f, 0.8f, 0.95f, 38f, 3.2f, 0.93f, 0.9f, 0.8f, 1, shadow = true)
        flash(0.07f, 0.35f, 0.8f, 0.7f, 1f, 0.6f)
        puffs(4, 0.05f, 0.9f, 0.3f, 20f, 11f, 20f, 0.75f, 0.7f, 0.65f, 0.5f, 2, outward = 0.3f, flat = 0.66f, inkK = 0.45f)
        glints(2, 0.05f, 0.5f, 0.6f, 20f, 8f, 0.85f, 0.7f, 1f, 0.8f)
      case S_ECHO =>
        scorch(0.5f, 0.4f, 0.62f, 0.95f, 0.26f)
        soundRings(4, 3, 0f, 0.75f, 1f, 5.6f, 0.52f, 0.8f, 1f, 1f)
        if (layer != AIR) return
        flash(0.1f, 0.55f, 0.7f, 0.9f, 1f, 1f)
        // The ghost of the bolt, hanging where it struck for a moment
        val gp = seg(T, 0f, 0.5f)
        if (gp < 1f) sb.fillOvalSoft(cx, cy - 12f * k, 16f * k * (0.7f + 0.3f * gp), 14f * k * (0.7f + 0.3f * gp),
          0.6f, 0.85f, 1f, 0.55f * (1f - gp), 0f, 16)
        var i = 0
        while (i < 6) {
          val th = (i + 0.5f * hash(seed, 1600 + i)) * TWO_PI / 6
          val p = seg(T, 0.02f, 0.75f)
          if (p > 0f && p < 1f) {
            val rr = 0.15f + 0.8f * easeOut(p)
            val x = gx(cosf(th), rr); val y = gy(sinf(th), rr) - 10f * k * easeOut(p)
            soul(x, y, cosf(th) * W, sinf(th) * H - 10f, 5f * k, (1f - p) * Math.min(1f, p * 6f), 0.55f, 0.8f, 1f, eyes = false)
          }
          i += 1
        }
      case S_WAIL =>
        var i = 0
        while (i < 5) {
          val start = 0.03f * i + 0.14f * i
          val p = seg(T, start, start + 0.5f)
          if (p > 0f && p < 1f)
            wavyRing(easeOut(p), 0.04f, 9, MS * 0.012f + i, 3.4f * k * (1f - 0.5f * p), 0.65f, 0.9f, 1f, 0.85f * (1f - p))
          i += 1
        }
        if (layer != AIR) return
        flash(0.08f, 0.45f, 0.7f, 0.92f, 1f, 0.7f)
        val fp = seg(T, 0f, 0.8f)
        if (fp < 1f) wailFace(cx, cy - (14f + 26f * easeOut(fp)) * k, 16f * k * (1f + 0.25f * seg(fp, 0.1f, 0.6f)),
          seg(fp, 0.05f, 0.4f), 0.85f * life(fp, 0.1f, 0.55f))
        i = 0
        while (i < 8) {
          val th = (i + 0.5f * hash(seed, 1700 + i)) * TWO_PI / 8
          val p = seg(T, 0.05f + 0.03f * (i & 3), 0.85f)
          if (p > 0f && p < 1f) {
            val rr = 0.12f + 0.85f * easeOut(p)
            soul(gx(cosf(th), rr), gy(sinf(th), rr) - 16f * k * (1f - p), cosf(th) * W, sinf(th) * H, 3.4f * k,
              (1f - p) * Math.min(1f, p * 5f), 0.6f, 0.86f, 1f, eyes = false)
          }
          i += 1
        }
      case _ => // S_SHADOW_BURST
        scorch(0.72f, 0.06f, 0.02f, 0.1f, 0.6f)
        if (layer == GROUND) {
          // Tendrils of shadow lashing out over the ground, whipping as they go
          var i = 0
          while (i < 10) {
            val th = (i + 0.4f * hash(seed, 1800 + i)) * TWO_PI / 10
            val c = cosf(th); val s = sinf(th)
            val ang = Math.atan2(s * H, c * W).toFloat
            val len = Math.sqrt(c * W * c * W + s * H * s * H).toFloat * (0.72f + 0.22f * h01(seed, 1810 + i))
            val grow = easeOut3(seg(T, 0.02f, 0.16f)) * (1f - smooth(seg(T, 0.55f + 0.1f * h01(seed, 1820 + i), 0.92f)))
            tendril(cx, cy, ang, len, grow, sinf(MS * 0.012f + i * 1.7f) * 0.9f, 9f * k,
              0.12f, 0.04f, 0.18f, 1f, n = 10, wisp = true, hr = 0.72f, hg = 0.38f, hb = 1f)
            i += 1
          }
        }
        if (layer != AIR) return
        val cp = seg(T, 0f, 0.42f)
        if (cp < 1f) {
          val cr = W * 0.34f * easeOut3(seg(cp, 0f, 0.3f)) * (1f - smooth(seg(cp, 0.45f, 1f)))
          sb.fillOval(cx, cy - H * 0.1f, cr, cr * 0.6f, 0.05f, 0.02f, 0.08f, 0.92f, 22)
          sb.strokeOval(cx, cy - H * 0.1f, cr, cr * 0.6f, 3f * k, 0.72f, 0.38f, 1f, 0.9f * (1f - cp), 22)
        }
        debris(D_SPARK, 10, 0f, 0.4f, 1f, 18f, 2.2f, 0.7f, 0.35f, 1f, 1)
        puffs(5, 0.1f, 0.9f, 0.4f, 30f, 8f, 16f, 0.2f, 0.1f, 0.28f, 0.6f, 2, inkK = 0.6f)
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  INWARD: vortices and implosions
  // ═══════════════════════════════════════════════════════════════════

  /** Spiral arms turning in over the ground, `arms` of them, tightening as the blast goes on. */
  private def spiralArms(arms: Int, t0: Float, t1: Float, spin: Float, twist: Float, w: Float,
                         r: Float, g: Float, b: Float, a0: Float): Unit = {
    if (layer != GROUND) return
    val p = seg(T, t0, t1)
    if (p <= 0f || p >= 1f) return
    val a = a0 * life(p, 0.15f, 0.6f)
    val shrink = 1f - 0.45f * smooth(p)
    var j = 0
    while (j < arms) {
      val base = j * TWO_PI / arms + spin * MS * 0.001f + hash(seed, 1900) * PI
      var i = 0
      while (i < 12) {
        val u = i / 11f
        val rho = (0.95f - 0.82f * u) * shrink
        val th = base + twist * u
        _px(i) = gx(cosf(th), rho); _py(i) = gy(sinf(th), rho)
        _pw(i) = w * k * (0.3f + 0.9f * u) + 2.4f
        _pa(i) = a * (0.25f + 0.75f * u) * 0.6f
        i += 1
      }
      sb.strokePolylineVar(_px, _py, _pw, _pa, 12, ink(r), ink(g), ink(b))
      i = 0
      while (i < 12) { _pw(i) -= 2.4f; _pa(i) /= 0.6f; i += 1 }
      sb.strokePolylineVar(_px, _py, _pw, _pa, 12, r, g, b)
      i = 0
      while (i < 12) { _pw(i) *= 0.35f; i += 1 }
      sb.strokePolylineVar(_px, _py, _pw, _pa, 12, lit(r), lit(g), lit(b))
      j += 1
    }
  }

  /** Rings closing in on the middle, one after another. */
  private def closingRings(n: Int, t0: Float, t1: Float, w: Float, r: Float, g: Float, b: Float, a0: Float): Unit = {
    if (layer != GROUND) return
    var i = 0
    while (i < n) {
      val start = t0 + (t1 - t0) * 0.3f * i / Math.max(1, n - 1)
      val p = seg(T, start, start + (t1 - t0) * 0.7f)
      if (p > 0f && p < 1f) {
        val q = 1f - 0.9f * smooth(p)
        val a = a0 * life(p, 0.2f, 0.7f)
        sb.strokeOval(cx, cy, W * q, H * q, w * k * (1f + p) + 2.4f, ink(r), ink(g), ink(b), 0.45f * a, 24)
        sb.strokeOval(cx, cy, W * q, H * q, w * k * (1f + p), r, g, b, a, 24)
      }
      i += 1
    }
  }

  /** Motes pulled in on a spiral from the edge to the middle over [t0, t1], additive. */
  private def pullMotes(n0: Int, t0: Float, t1: Float, spiral: Float, size: Float,
                        r: Float, g: Float, b: Float, a0: Float, sd: Int): Unit = {
    if (layer != AIR || T <= t0 || T >= t1) return
    val n = Math.max(1, (n0 * det + 0.5f).toInt)
    sb.setAdditiveBlend(true)
    var i = 0
    while (i < n) {
      val s0 = seed * 71 + sd * 13
      val start = t0 + (t1 - t0) * 0.45f * h01(s0, i)
      val tau = seg(T, start, t1)
      if (tau > 0f && tau < 1f) {
        val th = (i + 0.5f * hash(s0, 20 + i)) * TWO_PI / n + spiral * tau
        val rho = 0.95f * (1f - tau * tau)
        val x = gx(cosf(th), rho); val y = gy(sinf(th), rho) - 6f * k * sinf(tau * PI)
        val a = a0 * Math.min(1f, tau * 5f) * (1f - seg(tau, 0.85f, 1f))
        sb.fillOval(x, y, size * k * 2f, size * k * 2f, r, g, b, 0.3f * a, 8)
        sb.fillOval(x, y, size * k, size * k, mix(r, 1f, 0.6f), mix(g, 1f, 0.6f), mix(b, 1f, 0.6f), a, 6)
      }
      i += 1
    }
    sb.setAdditiveBlend(false)
  }

  /** A hole in the middle: dark, rimmed with light, swelling over [0, 0.15] and collapsing at `pop`. */
  private def blackHole(pop: Float, size: Float, r: Float, g: Float, b: Float): Unit = {
    if (layer != GROUND) return
    val rr = size * easeOut3(seg(T, 0f, 0.15f)) * (1f - smooth(seg(T, pop - 0.08f, pop)))
    if (rr <= 0.005f) return
    sb.fillOval(cx, cy, W * rr, H * rr, 0.03f, 0.01f, 0.06f, 0.95f, 22)
    sb.strokeOval(cx, cy, W * rr, H * rr, 3f * k, r, g, b, 0.95f, 22)
    sb.strokeOval(cx, cy, W * rr * 1.25f, H * rr * 1.25f, 1.4f * k, r, g, b, 0.5f, 22)
  }

  /** A clock face on the ground, its hands running backwards, fast and then slowing. */
  private def clockFace(a: Float): Unit = {
    if (layer != GROUND || a <= 0.01f) return
    val tr = 0.35f; val tg = 0.95f; val tb = 0.85f
    val yr = 1f; val yg = 0.85f; val yb = 0.4f
    sb.strokeOval(cx, cy, W * 0.9f, H * 0.9f, 3.4f * k + 2.6f, ink(tr), ink(tg), ink(tb), 0.5f * a, 24)
    sb.strokeOval(cx, cy, W * 0.9f, H * 0.9f, 3.4f * k, tr, tg, tb, a, 24)
    sb.strokeOval(cx, cy, W * 0.78f, H * 0.78f, 1.4f * k, yr, yg, yb, 0.8f * a, 24)
    var i = 0
    while (i < 12) {
      val th = i * PI / 6 - PI * 0.5f
      val long = i % 3 == 0
      val inner = if (long) 0.64f else 0.71f
      sb.strokeLine(gx(cosf(th), inner), gy(sinf(th), inner), gx(cosf(th), 0.78f), gy(sinf(th), 0.78f),
        (if (long) 2.6f else 1.4f) * k, yr, yg, yb, a)
      i += 1
    }
    val sweep = -(MS * 0.011f) * (1f - 0.6f * T) + hash(seed, 2000) * PI
    var hnd = 0
    while (hnd < 2) {
      val ang = if (hnd == 0) sweep else sweep / 12f + 1.3f
      val len = if (hnd == 0) 0.62f else 0.4f
      _xs(0) = cx; _ys(0) = cy
      _xs(1) = gx(cosf(ang), len); _ys(1) = gy(sinf(ang), len)
      sb.strokePolylineTapered(_xs, _ys, 2, (if (hnd == 0) 3.2f else 4.2f) * k + 2.4f, 1.6f, ink(yr), ink(yg), ink(yb), 0.6f * a, 0.5f * a)
      sb.strokePolylineTapered(_xs, _ys, 2, (if (hnd == 0) 3.2f else 4.2f) * k, 0.6f, yr, yg, yb, a, a)
      hnd += 1
    }
    sb.fillOval(cx, cy, 3f * k, 2f * k, 1f, 0.95f, 0.7f, a, 10)
  }

  /** A tear of light standing in the middle: a lens, `h` tall, inked. */
  private def rift(x: Float, y: Float, w: Float, h: Float, a: Float, r: Float, g: Float, b: Float): Unit = {
    if (a <= 0.02f || h < 2f) return
    var pass = 0
    while (pass < 3) {
      val grow = if (pass == 0) 1.8f else 0f
      val wk = if (pass == 2) 0.4f else 1f
      _xs(0) = x; _ys(0) = y - h * 0.5f - grow
      _xs(1) = x + w * wk + grow; _ys(1) = y - h * 0.18f
      _xs(2) = x + w * wk + grow; _ys(2) = y + h * 0.18f
      _xs(3) = x; _ys(3) = y + h * 0.5f + grow
      _xs(4) = x - w * wk - grow; _ys(4) = y + h * 0.18f
      _xs(5) = x - w * wk - grow; _ys(5) = y - h * 0.18f
      (pass: @scala.annotation.switch) match {
        case 0 => sb.fillPolygon(_xs, _ys, 6, ink(r), ink(g), ink(b), 0.8f * a)
        case 1 => sb.fillPolygon(_xs, _ys, 6, r, g, b, a)
        case _ => sb.fillPolygon(_xs, _ys, 6, 1f, 1f, 1f, a)
      }
      pass += 1
    }
  }

  private def inward(st: Int): Unit = {
    (st: @scala.annotation.switch) match {
      case S_GRAVITY =>
        spiralArms(4, 0.02f, 0.72f, 2.2f, 2.4f, 4f, 0.55f, 0.42f, 1f, 0.9f)
        closingRings(3, 0.02f, 0.7f, 2.6f, 0.62f, 0.5f, 1f, 0.9f)
        blackHole(0.7f, 0.2f, 0.7f, 0.55f, 1f)
        shockRing(0.68f, 0.98f, 5f, 0.66f, 0.55f, 1f, 0.9f)
        if (layer != AIR) return
        pullMotes(12, 0.02f, 0.7f, 2.6f, 1.6f, 0.65f, 0.55f, 1f, 1f, 1)
        flash(0.12f, 0.5f, 0.7f, 0.6f, 1f, 1f, at = 0.68f)
        debris(D_SPARK, 10, 0.68f, 0.98f, 1f, 16f, 2f, 0.7f, 0.55f, 1f, 1)
      case S_VOID_PULL =>
        scorch(0.6f, 0.06f, 0.02f, 0.1f, 0.5f)
        closingRings(3, 0.02f, 0.68f, 3f, 0.55f, 0.3f, 0.9f, 0.9f)
        streaks(12, 0.02f, 0.62f, 1f, 0.12f, 0.3f, 3f, 0.4f, 0.2f, 0.7f, 0.85f, 1)
        blackHole(0.68f, 0.24f, 0.6f, 0.3f, 1f)
        shockRing(0.66f, 0.96f, 5f, 0.6f, 0.3f, 1f, 0.85f)
        if (layer != AIR) return
        puffs(8, 0.02f, 0.7f, 0.9f, 8f, 10f, 16f, 0.18f, 0.08f, 0.26f, 0.65f, 1, outward = -0.75f, flat = 0.7f, inkK = 0.6f)
        flash(0.12f, 0.45f, 0.6f, 0.3f, 1f, 0.9f, at = 0.66f)
        debris(D_SPARK, 12, 0.66f, 0.98f, 1f, 18f, 2.2f, 0.65f, 0.35f, 1f, 1)
      case S_FIRE_VORTEX =>
        scorch(0.6f, 0.14f, 0.05f, 0.02f, 0.5f)
        spiralArms(5, 0.02f, 0.75f, 3f, 3f, 11f, 1f, 0.5f, 0.1f, 0.95f)
        flames(5, 0.1f, 0.04f, scatter = false, 38f, 6f, 0.06f, 0f, 0.62f, 0.98f, 0.42f, 0.08f, 1f, 0.88f, 0.45f, 1)
        shockRing(0.7f, 0.97f, 6f, 1f, 0.55f, 0.15f, 0.85f)
        if (layer != AIR) return
        pullMotes(14, 0.02f, 0.72f, 3f, 1.6f, 1f, 0.6f, 0.2f, 1f, 1)
        flash(0.12f, 0.55f, 1f, 0.7f, 0.3f, 1f, at = 0.7f)
        puffs(3, 0.55f, 1f, 0.1f, 50f, 10f, 20f, 0.24f, 0.18f, 0.17f, 0.6f, 2, lift = 16f)
      case _ => // S_TEMPORAL
        clockFace(0.95f * life(T, 0.08f, 0.68f))
        closingRings(2, 0.1f, 0.8f, 2f, 0.4f, 0.95f, 0.88f, 0.7f)
        if (layer != AIR) return
        // Shards of time turning back round it, drifting in
        var i = 0
        while (i < 8) {
          val p = seg(T, 0.04f, 0.92f)
          if (p > 0f && p < 1f) {
            val th = (i + 0.3f * hash(seed, 2100 + i)) * TWO_PI / 8 - p * 2.6f
            val rr = 0.66f - 0.36f * smooth(p)
            val x = gx(cosf(th), rr); val y = gy(sinf(th), rr) - (8f + 4f * sinf(MS * 0.006f + i)) * k
            piece(D_SHARD, x, y, 2.6f * k, th * 2f, 1f, 0.9f, 0.55f, life(p, 0.1f, 0.75f))
          }
          i += 1
        }
        val rp = seg(T, 0.06f, 0.88f)
        if (rp > 0f && rp < 1f) {
          val h = 28f * k * easeOut3(seg(rp, 0f, 0.25f)) * (1f - smooth(seg(rp, 0.7f, 1f)))
          sb.fillOvalSoft(cx, cy - 16f * k, 12f * k, 20f * k, 0.4f, 1f, 0.9f, 0.35f * life(rp, 0.1f, 0.7f), 0f, 16)
          rift(cx, cy - 16f * k, 4.5f * k, h, life(rp, 0.1f, 0.75f), 0.45f, 1f, 0.9f)
        }
        glints(4, 0.05f, 0.8f, 0.7f, 20f, 8f, 1f, 0.92f, 0.6f, 0.9f)
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  HOLY
  // ═══════════════════════════════════════════════════════════════════

  /** A sunburst on the ground: a ring, rays out of it, and a cross in the middle. */
  private def sunburst(a: Float): Unit = {
    if (layer != GROUND || a <= 0.01f) return
    val r = 1f; val g = 0.86f; val b = 0.36f
    sb.strokeOval(cx, cy, W * 0.72f, H * 0.72f, 3f * k + 2.4f, ink(r), ink(g), ink(b), 0.45f * a, 24)
    sb.strokeOval(cx, cy, W * 0.72f, H * 0.72f, 3f * k, r, g, b, a, 24)
    var i = 0
    while (i < 12) {
      val th = i * TWO_PI / 12 + hash(seed, 2200) * 0.3f
      val out = if ((i & 1) == 0) 1f else 0.9f
      _xs(0) = gx(cosf(th), 0.78f); _ys(0) = gy(sinf(th), 0.78f)
      _xs(1) = gx(cosf(th), out); _ys(1) = gy(sinf(th), out)
      sb.strokePolylineTapered(_xs, _ys, 2, 4f * k, 0.6f, r, g, b, a, 0.3f * a)
      i += 1
    }
    // The cross, upright on the screen and lying on the ground. Along the ground's own axes it
    // was an X
    val l = W * 0.42f
    var pass = 0
    while (pass < 2) {
      val lw = 4.4f * k + (if (pass == 0) 2.4f else 0f)
      val cr = if (pass == 0) ink(r) else 1f; val cg = if (pass == 0) ink(g) else 0.95f; val cb = if (pass == 0) ink(b) else 0.7f
      val ca = if (pass == 0) 0.5f * a else a
      sb.strokeLine(cx - l * 0.55f, cy - l * 0.1f, cx + l * 0.55f, cy - l * 0.1f, lw, cr, cg, cb, ca)
      sb.strokeLine(cx, cy - l * 0.32f, cx, cy + l * 0.46f, lw, cr, cg, cb, ca)
      pass += 1
    }
  }

  /** A heater shield of gold at (x, y), `s` half its height, with a white cross on it. */
  private def shieldEmblem(x: Float, y: Float, s: Float, a: Float): Unit = {
    if (a <= 0.02f || s < 1f) return
    var pass = 0
    while (pass < 2) {
      val grow = if (pass == 0) 2.2f else 0f
      _xs(0) = x - 0.9f * s - grow; _ys(0) = y - s - grow
      _xs(1) = x + 0.9f * s + grow; _ys(1) = y - s - grow
      _xs(2) = x + 0.9f * s + grow; _ys(2) = y + 0.15f * s
      _xs(3) = x; _ys(3) = y + 1.3f * s + grow * 1.4f
      _xs(4) = x - 0.9f * s - grow; _ys(4) = y + 0.15f * s
      if (pass == 0) sb.fillPolygon(_xs, _ys, 5, 0.3f, 0.2f, 0.05f, 0.9f * a)
      else sb.fillPolygon(_xs, _ys, 5, 1f, 0.8f, 0.28f, a)
      pass += 1
    }
    _xs(0) = x - 0.78f * s; _ys(0) = y - 0.88f * s
    _xs(1) = x; _ys(1) = y - 0.88f * s
    _xs(2) = x; _ys(2) = y + 1.1f * s
    _xs(3) = x - 0.78f * s; _ys(3) = y + 0.1f * s
    sb.fillPolygon(_xs, _ys, 4, 1f, 0.92f, 0.55f, a)
    sb.strokeLine(x, y - 0.72f * s, x, y + 0.92f * s, 0.3f * s, 1f, 1f, 1f, a)
    sb.strokeLine(x - 0.6f * s, y - 0.18f * s, x + 0.6f * s, y - 0.18f * s, 0.3f * s, 1f, 1f, 1f, a)
  }

  private def holy(st: Int): Unit = {
    if (st == S_SMITE) {
      scorch(0.55f, 1f, 0.95f, 0.7f, 0.25f, 0.5f)
      sunburst(0.95f * life(T, 0.1f, 0.6f))
      if (layer != AIR) return
      // The beam of light driven down onto it
      val bp = seg(T, 0f, 0.45f)
      if (bp < 1f) {
        val a = 1f - smooth(bp)
        val bw = W * 0.13f * (1f - 0.55f * bp)
        val top = cy - 260f * k
        val ht = cy - top
        sb.fillRectGradient(cx - bw * 2f, top, bw * 4f, ht,
          1f, 0.9f, 0.55f, 0f, 1f, 0.9f, 0.55f, 0f, 1f, 0.9f, 0.55f, 0.35f * a, 1f, 0.9f, 0.55f, 0.35f * a)
        sb.fillRectGradient(cx - bw, top, bw * 2f, ht,
          1f, 0.92f, 0.6f, 0.2f * a, 1f, 0.92f, 0.6f, 0.2f * a, 1f, 0.92f, 0.6f, 0.95f * a, 1f, 0.92f, 0.6f, 0.95f * a)
        sb.fillRect(cx - bw * 0.36f, top, bw * 0.72f, ht, 1f, 1f, 0.96f, a)
        sb.strokeLine(cx - bw, top, cx - bw, cy, 1.4f, 0.8f, 0.55f, 0.15f, 0.7f * a)
        sb.strokeLine(cx + bw, top, cx + bw, cy, 1.4f, 0.8f, 0.55f, 0.15f, 0.7f * a)
      }
      flash(0.1f, 0.6f, 1f, 0.95f, 0.7f)
      debris(D_FEATHER, 5, 0.05f, 1f, 0.8f, 60f, 3.4f, 1f, 0.97f, 0.88f, 1)
      glints(6, 0.05f, 0.8f, 0.8f, 30f, 10f, 1f, 0.95f, 0.6f, 1f)
    } else {
      // S_SHIELD_BASH: a golden ring driving everyone back, and the Crusader's shield
      shockRing(0f, 0.45f, 12f, 1f, 0.82f, 0.35f, 0.95f)
      shockRing(0.06f, 0.5f, 5f, 1f, 0.95f, 0.75f, 0.8f)
      if (layer == GROUND) {
        val p = seg(T, 0f, 0.45f)
        if (p > 0f && p < 1f) {
          val q = easeOut3(p)
          val a = (1f - p) * Math.min(1f, p * 10f)
          var i = 0
          while (i < 10) {
            val th = (i + 0.5f) * TWO_PI / 10 + hash(seed, 2300) * 0.5f
            _xs(0) = gx(cosf(th - 0.09f), q - 0.05f); _ys(0) = gy(sinf(th - 0.09f), q - 0.05f)
            _xs(1) = gx(cosf(th), q + 0.03f); _ys(1) = gy(sinf(th), q + 0.03f)
            _xs(2) = gx(cosf(th + 0.09f), q - 0.05f); _ys(2) = gy(sinf(th + 0.09f), q - 0.05f)
            sb.strokePolyline(_xs, _ys, 3, 3.6f * k + 2.4f, 0.3f, 0.2f, 0.05f, 0.6f * a)
            sb.strokePolyline(_xs, _ys, 3, 3.6f * k, 1f, 0.9f, 0.55f, a)
            i += 1
          }
        }
      }
      if (layer != AIR) return
      flash(0.08f, 0.5f, 1f, 0.9f, 0.6f)
      val sp = seg(T, 0f, 0.5f)
      if (sp < 1f) {
        val pop = if (sp < 0.2f) { val q = easeOut3(sp / 0.2f); q * (1f + 0.5f * (1f - q)) } else 1f
        shieldEmblem(cx, cy - 28f * k - 10f * k * easeOut(sp), 17f * k * pop, 1f - smooth(seg(sp, 0.55f, 1f)))
      }
      puffs(8, 0.05f, 0.85f, 0.85f, 10f, 12f, 22f, 0.8f, 0.72f, 0.58f, 0.45f, 1, outward = 0.15f, flat = 0.56f, inkK = 0.4f)
      glints(3, 0.05f, 0.5f, 0.6f, 24f, 9f, 1f, 0.92f, 0.6f, 0.9f)
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  SOUND
  // ═══════════════════════════════════════════════════════════════════

  /** A ring of `n` teeth, the points `depth` of the radius out past the notches. */
  private def jaggedRing(q: Float, n: Int, depth: Float, w: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    if (layer != GROUND || a <= 0.01f) return
    val rot = hash(seed, 2400) * PI
    var i = 0
    while (i < n) {
      val th = rot + i * TWO_PI / n
      val rr = q * (if ((i & 1) == 0) 1f else 1f - depth)
      _px(i) = gx(cosf(th), rr); _py(i) = gy(sinf(th), rr)
      i += 1
    }
    sb.strokePolygon(_px, _py, n, w + 2.4f, ink(r), ink(g), ink(b), 0.5f * a)
    sb.strokePolygon(_px, _py, n, w, r, g, b, a)
  }

  /** Three claw slashes raked across the ground at (th, rho), drawn in over [t0, t0 + 0.08]. */
  private def clawMarks(th: Float, rho: Float, len: Float, t0: Float, a0: Float): Unit = {
    if (layer != GROUND) return
    val grow = easeOut3(seg(T, t0, t0 + 0.08f))
    val a = a0 * tail(T, 0.6f)
    if (grow <= 0.02f || a <= 0.01f) return
    val px = gx(cosf(th), rho); val py = gy(sinf(th), rho)
    val sa = th + 1.2f
    val dx = cosf(sa) * W; val dy = sinf(sa) * H
    val dl = Math.max(0.01f, Math.sqrt(dx * dx + dy * dy).toFloat)
    val ux = dx / dl; val uy = dy / dl
    val nx = -uy; val ny = ux
    var j = -1
    while (j <= 1) {
      val off = j * 5.5f * k
      val l = len * k * (if (j == 0) 1f else 0.85f)
      val sx0 = px + nx * off - ux * l * 0.5f; val sy0 = py + ny * off - uy * l * 0.5f
      var i = 0
      while (i < 5) {
        val u = i / 4f * grow
        _px(i) = sx0 + ux * l * u + nx * sinf(u * PI) * l * 0.1f
        _py(i) = sy0 + uy * l * u + ny * sinf(u * PI) * l * 0.1f
        _pw(i) = 4.2f * k * sinf((i / 4f) * PI * 0.9f + 0.15f) + 2.6f
        _pa(i) = 0.7f * a
        i += 1
      }
      sb.strokePolylineVar(_px, _py, _pw, _pa, 5, 0.25f, 0.06f, 0.04f)
      i = 0
      while (i < 5) { _pw(i) -= 2.6f; _pa(i) = a; i += 1 }
      sb.strokePolylineVar(_px, _py, _pw, _pa, 5, 0.95f, 0.42f, 0.18f)
      i = 0
      while (i < 5) { _pw(i) *= 0.4f; i += 1 }
      sb.strokePolylineVar(_px, _py, _pw, _pa, 5, 1f, 0.85f, 0.6f)
      j += 1
    }
  }

  /** The Wolf's moon: a crescent over its howl, glowing. */
  private def moon(x: Float, y: Float, rr: Float, a: Float): Unit = {
    if (a <= 0.02f) return
    sb.fillOvalSoft(x, y, rr * 2.2f, rr * 2.2f, 0.6f, 0.72f, 1f, 0.4f * a, 0f, 18)
    val m = 10
    var i = 0
    while (i < m) {
      val u = i.toFloat / (m - 1)
      val ang = PI * 0.5f + 0.2f + (PI - 0.4f) * u
      val ox = x + cosf(ang) * rr; val oy = y + sinf(ang) * rr
      val ix = x + 0.42f * rr + cosf(ang) * 0.82f * rr; val iy = y + sinf(ang) * 0.82f * rr
      val taper = sinf(u * PI)
      _px(i) = ox; _py(i) = oy
      _px(2 * m - 1 - i) = mix(ox, ix, taper); _py(2 * m - 1 - i) = mix(oy, iy, taper)
      i += 1
    }
    sb.fillRibbon(_px, _py, 2 * m, 0.93f, 0.95f, 1f, a)
    sb.strokePolyline(_px, _py, m, 2.2f, 0.25f, 0.3f, 0.5f, 0.7f * a)
  }

  private def sonic(st: Int): Unit = {
    (st: @scala.annotation.switch) match {
      case S_LUTE =>
        soundRings(3, 4, 0f, 0.7f, 1f, 4f, 0.72f, 0.5f, 0.95f, 0.9f)
        soundRings(2, 4, 0.1f, 0.8f, 0.8f, 2.5f, 1f, 0.82f, 0.4f, 0.8f)
        if (layer != AIR) return
        flash(0.07f, 0.35f, 0.9f, 0.7f, 1f, 0.7f)
        debris(D_NOTE, 4, 0f, 0.9f, 0.9f, 50f, 3.6f, 0.75f, 0.45f, 0.95f, 1)
        debris(D_NOTE, 3, 0f, 0.9f, 0.85f, 56f, 3.4f, 1f, 0.8f, 0.35f, 2)
        glints(4, 0.05f, 0.7f, 0.8f, 30f, 9f, 1f, 0.8f, 1f, 0.9f)
      case S_BASS =>
        var i = 0
        while (i < 3) {
          val t0 = i * 0.16f
          val even = (i & 1) == 0
          shockRing(t0, t0 + 0.34f, 10f, if (even) 1f else 0.3f, if (even) 0.3f else 0.9f, if (even) 0.82f else 1f, 0.9f)
          i += 1
        }
        // An equaliser standing round it, bouncing to the beat
        val ea = life(T, 0.08f, 0.62f)
        if (ea > 0.01f) {
          val n = 12
          i = 0
          while (i < n) {
            val th = i * TWO_PI / n + hash(seed, 2500) * 0.3f
            _ux(i) = gx(cosf(th), 0.62f); _ordY(i) = gy(sinf(th), 0.62f)
            i += 1
          }
          sortFarToNear(n)
          var j = 0
          val e = MS / 95f
          val e0 = e.toInt
          val f = smooth(e - e0)
          while (j < n) {
            val i2 = _ord(j)
            val x = _ux(i2); val y = _ordY(i2)
            if (mine(y)) {
              val lv = mix(h01(seed + i2, e0), h01(seed + i2, e0 + 1), f)
              val hgt = (6f + 22f * lv) * k * ea
              val bw = 3.2f * k
              sb.fillRect(x - bw - 1.3f, y - hgt - 1.3f, 2f * bw + 2.6f, hgt + 1.3f, 0.12f, 0.04f, 0.16f, 0.85f * ea)
              sb.fillRectGradient(x - bw, y - hgt, 2f * bw, hgt,
                1f, 0.3f, 0.85f, ea, 1f, 0.3f, 0.85f, ea, 0.3f, 0.9f, 1f, ea, 0.3f, 0.9f, 1f, ea)
              sb.fillRect(x - bw, y - hgt - 2.4f * k, 2f * bw, 1.6f * k, 1f, 1f, 1f, 0.9f * ea)
            }
            j += 1
          }
        }
        if (layer != AIR) return
        flash(0.06f, 0.4f, 1f, 0.5f, 0.9f, 0.8f)
        debris(D_NOTE, 3, 0f, 0.9f, 0.85f, 46f, 3.4f, 1f, 0.4f, 0.85f, 1)
        debris(D_NOTE, 2, 0f, 0.9f, 0.85f, 50f, 3.2f, 0.35f, 0.9f, 1f, 2)
      case S_HOWL =>
        scorch(0.5f, 0.6f, 0.7f, 0.95f, 0.18f)
        var i = 0
        while (i < 4) {
          val start = 0.16f * i
          val p = seg(T, start, start + 0.55f)
          if (p > 0f && p < 1f)
            wavyRing(easeOut(p), 0.03f, 11, MS * 0.008f + i * 1.3f, 3.6f * k * (1f - 0.4f * p), 0.74f, 0.84f, 1f, 0.85f * (1f - p))
          i += 1
        }
        if (layer != AIR) return
        val mp = seg(T, 0f, 0.85f)
        if (mp < 1f) moon(cx + 8f * k, cy - 92f * k - 10f * k * easeOut(mp), 20f * k, life(mp, 0.14f, 0.62f))
        motes(10, 0.05f, 0.9f, 0.6f, 40f, 1.4f, 0.7f, 0.82f, 1f, 0.9f, 1)
      case S_ROAR =>
        var i = 0
        while (i < 3) {
          val start = 0.14f * i
          val p = seg(T, start, start + 0.5f)
          if (p > 0f && p < 1f)
            jaggedRing(easeOut(p), 28, 0.07f, 3.4f * k * (1f - 0.4f * p), 1f, 0.6f, 0.25f, 0.9f * (1f - p))
          i += 1
        }
        i = 0
        while (i < 4) {
          clawMarks((i + 0.5f) * PI * 0.5f + hash(seed, 2600) * 0.4f, 0.55f, 34f, 0.04f + 0.04f * i, 0.95f)
          i += 1
        }
        if (layer != AIR) return
        flash(0.07f, 0.4f, 1f, 0.7f, 0.4f, 0.6f)
        debris(D_LEAF, 10, 0f, 0.85f, 0.9f, 36f, 2.6f, 0.72f, 0.45f, 0.22f, 1)
      case _ => // S_SHRIEK
        soundRings(4, 5, 0f, 0.75f, 1f, 4.6f, 0.82f, 0.74f, 1f, 0.95f)
        streaks(12, 0.02f, 0.5f, 0.2f, 1.05f, 0.3f, 3.2f, 0.9f, 0.88f, 1f, 0.9f, 1)
        if (layer != AIR) return
        flash(0.07f, 0.45f, 0.9f, 0.85f, 1f, 0.8f)
        swirl(D_FEATHER, 12, 0f, 1f, 1f, 44f, 0.45f, 4f, 0.62f, 0.46f, 0.4f, 1)
        swirl(D_FEATHER, 6, 0f, 1f, 0.9f, 50f, 0.4f, 3.8f, 0.78f, 0.7f, 0.95f, 2)
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  NANITES
  // ═══════════════════════════════════════════════════════════════════

  /** A swarm of nanites flung out to `reach` over [0, t1 * 0.3], circling there and scattering. */
  private def nanites(n0: Int, reach: Float, t1: Float, size: Float): Unit = {
    if (layer != AIR || T >= t1) return
    val n = Math.max(4, (n0 * det + 0.5f).toInt)
    val out = easeOut3(seg(T, 0f, t1 * 0.3f))
    val fade = 1f - smooth(seg(T, t1 * 0.62f, t1))
    var i = 0
    while (i < n) {
      val dir = if ((i & 1) == 0) 1f else -1f
      val th = (i + 0.5f * hash(seed, 2700 + i)) * TWO_PI / n + dir * T * 3.2f
      val rr = reach * (0.4f + 0.6f * h01(seed, 2710 + i)) * out
      val x = gx(cosf(th), rr); val y = gy(sinf(th), rr) - (6f + 5f * sinf(MS * 0.01f + i)) * k * out
      sb.fillOval(x, y, size * k * 1.9f, size * k * 1.9f, 0.3f, 1f, 0.8f, 0.22f * fade, 8)
      piece(D_SQUARE, x, y, size * k, th, 0.25f, 0.95f, 0.72f, fade)
      i += 1
    }
  }

  private def nano(st: Int): Unit = {
    if (st == S_NANO_SPLASH) {
      if (layer == GROUND) {
        val p = seg(T, 0f, 0.8f)
        if (p < 1f) {
          val q = 0.25f + 0.5f * easeOut3(seg(p, 0f, 0.25f))
          sb.fillOvalSoft(cx, cy, W * q, H * q, 0.3f, 1f, 0.78f, 0.3f * life(p, 0.1f, 0.4f), 0f, 18)
          hexRing(q, T * 0.8f, 2.8f * k, 0.3f, 1f, 0.78f, life(p, 0.1f, 0.4f))
        }
      }
      flash(0.07f, 0.35f, 0.4f, 1f, 0.8f, 0.7f)
      nanites(22, 0.9f, 1f, 2.3f)
    } else {
      // S_NANO_BURST: the swarm flung out over a flash of hex grid
      if (layer == GROUND) {
        val ga = life(T, 0.06f, 0.42f) * 0.9f
        val hr = 0.23f
        hexRing(hr, PI / 6, 2f * k, 0.3f, 1f, 0.78f, ga)
        var i = 0
        while (i < 6) {
          val th = i * TWO_PI / 6
          hexRing(hr, PI / 6, 2f * k, 0.3f, 1f, 0.78f, ga * (0.8f - 0.1f * (i & 1)),
            cosf(th) * W * 0.44f, sinf(th) * H * 0.44f)
          i += 1
        }
      }
      shockRing(0f, 0.35f, 5f, 0.3f, 1f, 0.78f, 0.9f)
      flash(0.08f, 0.45f, 0.4f, 1f, 0.8f, 0.8f)
      nanites(52, 0.92f, 1f, 2.4f)
      motes(8, 0.05f, 0.8f, 0.8f, 30f, 1.3f, 0.35f, 1f, 0.8f, 1f, 1)
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  STONE
  // ═══════════════════════════════════════════════════════════════════

  /** Medusa's eye opening over the blast: an almond, green-gold, with a slit pupil. */
  private def gorgonEye(x: Float, y: Float, w: Float, h: Float, a: Float): Unit = {
    if (a <= 0.02f || h < 0.8f) return
    sb.fillOvalSoft(x, y, w * 1.7f, w * 1.2f, 0.62f, 0.95f, 0.35f, 0.4f * a, 0f, 16)
    var pass = 0
    while (pass < 2) {
      val grow = if (pass == 0) 2.2f else 0f
      val n = 6
      var i = 0
      while (i < n) {
        val u = i.toFloat / (n - 1)
        _xs(i) = x - w - grow + 2f * (w + grow) * u; _ys(i) = y - (h + grow) * sinf(u * PI)
        i += 1
      }
      i = 1
      while (i < n - 1) {
        val u = 1f - i.toFloat / (n - 1)
        _xs(n + i - 1) = x - w - grow + 2f * (w + grow) * u; _ys(n + i - 1) = y + (h + grow) * sinf(u * PI)
        i += 1
      }
      if (pass == 0) sb.fillPolygon(_xs, _ys, 2 * n - 2, 0.08f, 0.1f, 0.04f, 0.9f * a)
      else sb.fillPolygon(_xs, _ys, 2 * n - 2, 0.74f, 0.95f, 0.32f, a)
      pass += 1
    }
    val ir = Math.min(h * 0.92f, w * 0.5f)
    sb.fillOval(x, y, ir, ir, 0.95f, 0.8f, 0.2f, a, 14)
    _xs(0) = x; _ys(0) = y - ir * 0.92f
    _xs(1) = x + ir * 0.2f; _ys(1) = y
    _xs(2) = x; _ys(2) = y + ir * 0.92f
    _xs(3) = x - ir * 0.2f; _ys(3) = y
    sb.fillPolygon(_xs, _ys, 4, 0.05f, 0.04f, 0.03f, 0.95f * a)
    sb.fillOval(x - ir * 0.36f, y - ir * 0.36f, ir * 0.2f, ir * 0.16f, 1f, 1f, 1f, 0.85f * a, 8)
  }

  private def petrify(): Unit = {
    // A wave of stone going out over the ground, and the ground it has turned grey and cracked
    val wp = seg(T, 0.12f, 0.5f)
    if (layer == GROUND) {
      val sa = Math.min(1f, wp * 1.4f) * tail(T, 0.62f)
      if (sa > 0.01f) sb.fillOvalSoft(cx, cy, W * 0.8f * easeOut3(wp), H * 0.8f * easeOut3(wp), 0.52f, 0.5f, 0.47f, 0.4f * sa, 0.1f * sa, 22)
    }
    shockRing(0.12f, 0.5f, 10f, 0.62f, 0.6f, 0.56f, 0.95f)
    cracks(10, 0.85f, 0.15f, 0.45f, 2.6f, 0.2f, 0.19f, 0.17f, 0.65f)
    if (layer != AIR) return
    val ep = seg(T, 0f, 0.62f)
    if (ep < 1f) {
      val open = easeOut(seg(ep, 0.03f, 0.25f)) * (1f - smooth(seg(ep, 0.72f, 1f)))
      gorgonEye(cx, cy - 34f * k, 24f * k, 13f * k * open, Math.min(1f, open * 2f))
    }
    debris(D_CHIP, 10, 0.12f, 0.9f, 0.9f, 30f, 3f, 0.6f, 0.58f, 0.54f, 1, shadow = true, from = 0.3f)
    glints(4, 0.05f, 0.6f, 0.5f, 30f, 9f, 0.7f, 1f, 0.4f, 0.9f)
  }

  /** A blast with no style of its own: a ring, a scorch and a flash in the thrower's colour. */
  private def generic(): Unit = {
    scorch(0.55f, pcR * 0.3f, pcG * 0.3f, pcB * 0.3f, 0.4f)
    shockRing(0f, 0.4f, 6f, pcR, pcG, pcB, 0.9f)
    if (layer != AIR) return
    flash(0.1f, 0.5f, lit(pcR), lit(pcG), lit(pcB))
    debris(D_SPARK, 8, 0f, 0.4f, 1f, 16f, 2f, lit(pcR), lit(pcG), lit(pcB), 1)
  }
}
