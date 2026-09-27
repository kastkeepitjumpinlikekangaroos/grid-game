package com.gridgame.client.render.blasts

import com.gridgame.common.Constants
import com.gridgame.common.model.{CharacterId, ProjectileType, TrapType}
import com.gridgame.client.gl.ShapeBatch
import BlastKit._
import Dark._
import Earth._
import Electric._
import Explosions._
import Fire._
import Holy._
import Ice._
import Inward._
import Liquids._
import Nanites._
import Nature._
import Sonic._
import Stone._
import Water._

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
}
