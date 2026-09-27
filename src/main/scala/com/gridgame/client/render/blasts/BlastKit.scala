package com.gridgame.client.render.blasts

import com.gridgame.client.gl.ShapeBatch
import GLBlastRenderers._

/**
 * What every blast's drawing shares: the blast being drawn (where, how big, how far through its
 * life, its seed, its layer — set by GLBlastRenderers.draw, read by everything below it), the
 * arithmetic of its timing (seg, easeOut, tail, life), deterministic hashes, scratch arrays, and
 * the rule for which layer draws what stands on the ground (mine).
 */
private[blasts] object BlastKit {
  // ── The blast being drawn ──────────────────────────────────────────
  // Set by `draw` for the building blocks below, which all read them. Only the render thread
  // (or a dev tool's own) ever draws, so plain fields are safe, as they are in the projectile
  // renderers.
  private[blasts] var sb: ShapeBatch = _
  private[blasts] var layer = 0
  private[blasts] var cx = 0f
  private[blasts] var cy = 0f
  private[blasts] var W = 1f
  private[blasts] var H = 1f
  private[blasts] var T = 0f      // progress through its life, 0-1
  private[blasts] var MS = 0f     // milliseconds since it went off
  private[blasts] var seed = 0
  private[blasts] var det = 1f    // how many of the numerous small things to draw, 0.5-1
  private[blasts] var k = 1f      // the size of its details, from the size of the blast
  private[blasts] var pcR = 1f    // the thrower's colour, for the generic blast
  private[blasts] var pcG = 1f
  private[blasts] var pcB = 1f

  // ── Arithmetic ─────────────────────────────────────────────────────
  private[blasts] val TWO_PI = (Math.PI * 2).toFloat
  private[blasts] val PI = Math.PI.toFloat

  @inline private[blasts] def clamp01(v: Float): Float = if (v < 0f) 0f else if (v > 1f) 1f else v

  /** How far through [a, b] `t` is, clamped. */
  @inline private[blasts] def seg(t: Float, a: Float, b: Float): Float = clamp01((t - a) / (b - a))

  @inline private[blasts] def easeOut(t: Float): Float = { val u = 1f - t; 1f - u * u }

  @inline private[blasts] def easeOut3(t: Float): Float = { val u = 1f - t; 1f - u * u * u }

  @inline private[blasts] def smooth(t: Float): Float = t * t * (3f - 2f * t)

  /** 1 until `from`, then down to nothing at the end. */
  @inline private[blasts] def tail(t: Float, from: Float): Float = if (t <= from) 1f else clamp01((1f - t) / (1f - from))

  /** Up over [0, a], held, down over [b, 1]: the life of something that comes and goes. */
  @inline private[blasts] def life(t: Float, a: Float, b: Float): Float =
    if (t < a) t / a else if (t > b) clamp01((1f - t) / (1f - b)) else 1f

  @inline private[blasts] def mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

  @inline private[blasts] def cosf(a: Float): Float = Math.cos(a).toFloat

  @inline private[blasts] def sinf(a: Float): Float = Math.sin(a).toFloat

  /** A lighter tone, and the line colour: a dark version of the hue, never black. */
  @inline private[blasts] def lit(c: Float): Float = Math.min(1f, c * 0.45f + 0.55f)

  @inline private[blasts] def ink(c: Float): Float = c * 0.24f

  /** A deterministic hash of two ints to [-1, 1]: the same piece on the same arc every frame. */
  @inline private[blasts] def hash(a: Int, b: Int): Float = {
    var h = a * 0x27D4EB2D + b * 0x165667B1
    h = (h ^ (h >>> 15)) * 0x2C1B3C6D
    h = (h ^ (h >>> 12)) * 0x297A2D39
    h ^= h >>> 15
    (h & 0xFFFF) / 32767.5f - 1f
  }

  @inline private[blasts] def h01(a: Int, b: Int): Float = (hash(a, b) + 1f) * 0.5f

  // Scratch: one primitive at a time uses them, and the batch has read them when it returns
  private[blasts] val _xs = new Array[Float](32)
  private[blasts] val _ys = new Array[Float](32)
  private[blasts] val _px = new Array[Float](64)
  private[blasts] val _py = new Array[Float](64)
  private[blasts] val _pw = new Array[Float](64)
  private[blasts] val _pa = new Array[Float](64)

  // Per-puff state, computed once and read by each of a cluster's three passes
  private[blasts] val _ux = new Array[Float](64)
  private[blasts] val _uy = new Array[Float](64)
  private[blasts] val _us = new Array[Float](64)
  private[blasts] val _ua = new Array[Float](64)

  // Things standing on a ring, sorted far to near
  private[blasts] val _ord = new Array[Int](32)
  private[blasts] val _ordY = new Array[Float](32)

  /** Is something at screen y `y` on the ground behind the middle of the blast? Behind is drawn
    * with the ground, so whoever stands in the blast is in front of it. */
  @inline private[blasts] def behind(y: Float): Boolean = y < cy

  /** Whether this layer draws what stands at ground y `y`. */
  @inline private[blasts] def mine(y: Float): Boolean = (layer == GROUND) == behind(y)

  /** Sort the first `n` of `_ordY` far (small y) to near, into `_ord`. */
  private[blasts] def sortFarToNear(n: Int): Unit = {
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
}
