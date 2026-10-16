package com.gridgame.client.render
package world

import com.gridgame.common.model.{GroundEdges, Tile, WorldData}

import com.gridgame.client.gl.{GLTexture, ShaderProgram, SpriteBatch}

/**
 * Where two kinds of natural ground meet: one soft, inked edge over the tiles' staircase of diamonds
 * (GroundEdges in common/model says which ground spills over which, and how its edge looks). Every
 * cell an edge passes through is drawn again after the ground, as its diamond, by a shader: at each
 * pixel the cells round it, blended between their centres, decide what ground shows there, and the
 * pixel is left alone where that is the cell's own. The shader is GroundEdges.shade line for line,
 * which is how the menus' map pictures draw it.
 *
 * What it costs is the pixels it shades, so it shades as few as it can:
 *  - Everything a pixel reads is in its cell and the eight round it (the point a riser's height above
 *    it included), so each cell's quad carries their nine layers in its colour and the shader reads
 *    no texture. Nine fetches a pixel from a texture of the map's layers, over every cell an edge
 *    touched (a third of the screen round the Meadow's pond), cost the GPU 2 ms a frame.
 *  - A pixel with nothing but its cell's own ground round it, and round the point a riser above it,
 *    is let go at once, and a quarter of a cell all of whose pixels are like that isn't drawn at all.
 *  Measured on the render bench (the Meadow, a 2560x1600 frame at High, 16 players and 150
 *  projectiles, back to back with the build before it), a frame takes as long to build on the CPU and
 *  the GPU within the bench's noise, allocates nothing more, and the ground phase takes 0.65 ms more
 *  in the GPU profiler.
 *
 * The layers are read from the map once, and again round any cell whose tile has changed (a fence
 * put down or gone), looked for among the cells on screen every frame.
 */
private[render] final class GroundEdgePainter(ctx: RenderContext) {
  import ctx._

  private var batch: SpriteBatch = _
  // The batch binds a texture for every quad; the shader reads none
  private var blank: GLTexture = _
  private var world: WorldData = _
  private var w = 0
  private var h = 0
  // Each cell's layer, the tile it was read from, and whether an edge passes through it
  private var layers: Array[Byte] = _
  private var tiles: Array[Tile] = _
  private var edged: Array[Boolean] = _

  def draw(world: WorldData): Unit = {
    // Nothing of the other batches' may be queued while this one's program is bound
    if (batch == null) { endAll(); create() }
    if (world ne this.world) load(world)
    // A tile changed on screen (or just off it) since it was read: read the layers round it again
    var wy = Math.max(0, viewStartY - 2)
    val yEnd = Math.min(h - 1, viewEndY + 2)
    while (wy <= yEnd) {
      val row = world.tiles(wy)
      var wx = Math.max(0, viewStartX - 2)
      val xEnd = Math.min(w - 1, viewEndX + 2)
      while (wx <= xEnd) {
        if (row(wx) ne tiles(wy * w + wx)) changed(wx, wy)
        wx += 1
      }
      wy += 1
    }

    var begun = false
    wy = viewStartY
    while (wy <= viewEndY) {
      val loX = rowLo(wy)
      val hiX = rowHi(wy)
      var wx = loX
      while (wx <= hiX) {
        if (edged(wy * w + wx)) {
          if (!begun) {
            endAll()
            batch.begin(projection)
            batch.shader.setUniform1f("uPxPerUnit", shapeBatch.pixelsPerUnit)
            begun = true
          }
          val top = row3(wx, wy - 1); val mid = row3(wx, wy); val bot = row3(wx, wy + 1)
          // Which quarters of the cell anything can show in. A pixel reads the cells round the
          // corner of its quarter, and round the point a riser's height above it, which for all but
          // the top quarter reaches the patches above too; where all of those are the cell's own
          // ground the shader would draw nothing, so the quarter isn't drawn. Next to a cell an edge
          // only touches at a corner, that is three quarters of it
          val own = (mid >> 4) & 0xF
          val flatN = same(top, 0, own) && same(top, 1, own) && same(mid, 0, own)
          val flatE = same(top, 1, own) && same(top, 2, own) && same(mid, 2, own)
          val flatS = same(mid, 2, own) && same(bot, 1, own) && same(bot, 2, own)
          val flatW = same(mid, 0, own) && same(bot, 0, own) && same(bot, 1, own)
          val drawE = !(flatN && flatE); val drawW = !(flatN && flatW)
          val drawS = !(flatN && flatE && flatS && flatW)
          // The cell's diamond, its corners from the integer lattice as the ground's are, each at its
          // point of the cell (centred on 0); the nine layers round it in its colour
          val u = wx - wy; val v = wx + wy
          val xl = ((u - 1) * HW + camOffX).toFloat; val xc = (u * HW + camOffX).toFloat
          val xr = ((u + 1) * HW + camOffX).toFloat
          val yt = ((v - 1) * HH + camOffY).toFloat; val ym = (v * HH + camOffY).toFloat
          val yb = ((v + 1) * HH + camOffY).toFloat
          val r = top.toFloat; val g = mid.toFloat; val b = bot.toFloat
          if (!flatN && drawE && drawW && drawS) {
            batch.drawQuad(blank, xl, ym, -0.5f, 0.5f, xc, yt, -0.5f, -0.5f, xr, ym, 0.5f, -0.5f, xc, yb, 0.5f, 0.5f,
              r, g, b, 1f)
          } else {
            // the midpoints of its edges, each shared by the two quarters either side of it
            val xlc = (xl + xc) * 0.5f; val xcr = (xc + xr) * 0.5f
            val ytm = (yt + ym) * 0.5f; val ymb = (ym + yb) * 0.5f
            if (!flatN) batch.drawQuad(blank, xc, yt, -0.5f, -0.5f, xcr, ytm, 0f, -0.5f, xc, ym, 0f, 0f,
              xlc, ytm, -0.5f, 0f, r, g, b, 1f)
            if (drawE) batch.drawQuad(blank, xcr, ytm, 0f, -0.5f, xr, ym, 0.5f, -0.5f, xcr, ymb, 0.5f, 0f,
              xc, ym, 0f, 0f, r, g, b, 1f)
            if (drawS) batch.drawQuad(blank, xc, ym, 0f, 0f, xcr, ymb, 0.5f, 0f, xc, yb, 0.5f, 0.5f,
              xlc, ymb, 0f, 0.5f, r, g, b, 1f)
            if (drawW) batch.drawQuad(blank, xlc, ytm, -0.5f, 0f, xc, ym, 0f, 0f, xlc, ymb, 0f, 0.5f,
              xl, ym, -0.5f, 0.5f, r, g, b, 1f)
          }
        }
        wx += 1
      }
      wy += 1
    }
    if (begun) batch.end()
  }

  /** The layers of cells (x - 1, y), (x, y), (x + 1, y), off the map the nearest on it, four bits
    * apiece (as a float in the quad's colour, exact). */
  @inline private def row3(x: Int, y: Int): Int = {
    val base = (if (y < 0) 0 else if (y >= h) h - 1 else y) * w
    val x0 = if (x - 1 < 0) 0 else x - 1
    val x2 = if (x + 1 >= w) w - 1 else x + 1
    (layers(base + x0) & 0xF) | ((layers(base + x) & 0xF) << 4) | ((layers(base + x2) & 0xF) << 8)
  }

  /** Whether the i-th cell of a row of three reads as `own`, as the shader reads it: a cell that takes
    * no part is read as the cell's own ground. */
  @inline private def same(row: Int, i: Int, own: Int): Boolean = {
    val l = (row >> (4 * i)) & 0xF
    l == 0 || l == own
  }

  private def create(): Unit = {
    val shader = new ShaderProgram(GroundEdgePainter.VERT, GroundEdgePainter.FRAG)
    batch = new SpriteBatch(shader)
    blank = GLTexture.createWhitePixel()
    shader.use()
    shader.setUniform1f("uLiftA", GroundEdges.riserLift(1).toFloat)
    shader.setUniform1f("uLiftB", GroundEdges.riserLift(2).toFloat)
    shader.setUniform1f("uInkW", GroundEdges.InkWidth.toFloat)
    var k = 0
    while (k < GroundEdges.Count) {
      colour(shader, s"uBase[$k]", GroundEdges.base(k))
      colour(shader, s"uInk[$k]", GroundEdges.ink(k))
      colour(shader, s"uRiser[$k]", GroundEdges.riser(k))
      colour(shader, s"uRiserInk[$k]", GroundEdges.riserInk(k))
      val sh = GroundEdges.shore(k)
      shader.setUniform4f(s"uShore[$k]", ((sh >> 16) & 0xFF) / 255f, ((sh >> 8) & 0xFF) / 255f, (sh & 0xFF) / 255f,
        GroundEdges.shoreWidth(k).toFloat)
      shader.setUniform4f(s"uHas[$k]", if (GroundEdges.ink(k) != 0) 1f else 0f, GroundEdges.riserKind(k).toFloat,
        if (sh != 0) 1f else 0f, if (GroundEdges.shoreSoft(k)) 1f else 0f)
      k += 1
    }
  }

  private def colour(shader: ShaderProgram, name: String, argb: Int): Unit =
    shader.setUniform3f(name, ((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f)

  private def load(world: WorldData): Unit = {
    this.world = world
    w = world.width; h = world.height
    layers = GroundEdges.layers(world)
    tiles = new Array[Tile](w * h)
    edged = new Array[Boolean](w * h)
    var y = 0
    while (y < h) {
      var x = 0
      while (x < w) {
        tiles(y * w + x) = world.tiles(y)(x)
        edged(y * w + x) = GroundEdges.hasEdge(layers, w, h, x, y)
        x += 1
      }
      y += 1
    }
  }

  /** Cell (x, y) is another tile now: its layer and its neighbours' (a prop's ground is one of the
    * four round it) are read again, and whether an edge passes through each cell those can reach. */
  private def changed(x: Int, y: Int): Unit = {
    tiles(y * w + x) = world.tiles(y)(x)
    var cy = Math.max(0, y - 1)
    while (cy <= Math.min(h - 1, y + 1)) {
      var cx = Math.max(0, x - 1)
      while (cx <= Math.min(w - 1, x + 1)) { layers(cy * w + cx) = GroundEdges.layerAt(world, cx, cy).toByte; cx += 1 }
      cy += 1
    }
    cy = Math.max(0, y - 2)
    while (cy <= Math.min(h - 1, y + 2)) {
      var cx = Math.max(0, x - 2)
      while (cx <= Math.min(w - 1, x + 2)) {
        edged(cy * w + cx) = GroundEdges.hasEdge(layers, w, h, cx, cy)
        cx += 1
      }
      cy += 1
    }
  }

  def dispose(): Unit = {
    if (batch != null) { batch.dispose(); batch = null }
    if (blank != null) { blank.dispose(); blank = null }
    world = null
  }
}

private object GroundEdgePainter {
  /** The sprite batch's vertices: a corner on the screen, its point of the cell (the cell's centre
    * is 0, its corners 0.5 off it), and the nine layers round the cell, a row of three in each of the
    * colour's first three channels, four bits a layer (0 for one that takes no part) — the same at
    * every corner, and handed on whole. */
  val VERT: String =
    """#version 330 core
      |layout(location = 0) in vec2 aPos;
      |layout(location = 1) in vec2 aTexCoord;
      |layout(location = 2) in vec4 aColor;
      |uniform mat4 uProjection;
      |out vec2 vCell;
      |flat out vec4 vLayers;
      |void main() {
      |  gl_Position = uProjection * vec4(aPos, 0.0, 1.0);
      |  vCell = aTexCoord;
      |  vLayers = aColor;
      |}""".stripMargin

  /** GroundEdges.shade, per pixel: the point from the cell's centre, the layers from the nine cells
    * round it. */
  val FRAG: String =
    s"""#version 330 core
      |in vec2 vCell;
      |flat in vec4 vLayers;
      |uniform float uPxPerUnit;
      |uniform float uLiftA;
      |uniform float uLiftB;
      |uniform float uInkW;
      |uniform vec3 uBase[${GroundEdges.Count}];
      |uniform vec3 uInk[${GroundEdges.Count}];
      |uniform vec3 uRiser[${GroundEdges.Count}];
      |uniform vec3 uRiserInk[${GroundEdges.Count}];
      |uniform vec4 uShore[${GroundEdges.Count}];
      |uniform vec4 uHas[${GroundEdges.Count}];
      |out vec4 FragColor;
      |
      |const float HW = 20.0;
      |const float HH = 10.0;
      |ivec3 rows;
      |int own;
      |
      |// The cell (dx, dy) from this one, each -1 to 1: its layer, or this cell's where it takes no part
      |int layerAt(int dx, int dy) {
      |  int l = (rows[dy + 1] >> (4 * (dx + 1))) & 15;
      |  return l == 0 ? own : l;
      |}
      |
      |ivec4 corners(vec2 p, out vec2 f) {
      |  vec2 b = floor(p);
      |  f = p - b;
      |  int x = int(b.x); int y = int(b.y);
      |  return ivec4(layerAt(x, y), layerAt(x + 1, y), layerAt(x, y + 1), layerAt(x + 1, y + 1));
      |}
      |
      |// Signed distance in pixels to the edge of the ground of layer k or above, positive inside
      |float dist(ivec4 l, vec2 f, int k) {
      |  vec4 c = vec4(greaterThanEqual(l, ivec4(k)));
      |  float F = mix(mix(c.x, c.y, f.x), mix(c.z, c.w, f.x), f.y);
      |  float fs = mix(c.y - c.x, c.w - c.z, f.y);
      |  float ft = mix(c.z - c.x, c.w - c.y, f.x);
      |  vec2 g = vec2((fs - ft) / (2.0 * HW), (fs + ft) / (2.0 * HH)) / uPxPerUnit;
      |  float gl = length(g);
      |  if (gl < 1e-9) return F >= 0.5 ? 1e4 : -1e4;
      |  return (F - 0.5) / gl;
      |}
      |
      |vec4 over(vec4 dst, vec3 col, float a) { return vec4(col * a, a) + dst * (1.0 - a); }
      |
      |void main() {
      |  vec2 p = vCell;
      |  rows = ivec3(vLayers.xyz + 0.5);
      |  own = (rows.y >> 4) & 15;
      |  vec2 f; ivec4 l = corners(p, f);
      |  vec2 fa; ivec4 la = corners(p - vec2(uLiftA), fa);
      |  vec2 fb; ivec4 lb = corners(p - vec2(uLiftB), fb);
      |  // Nothing but the cell's own ground round the point, or round the points a riser above it: no
      |  // edge can reach it. Most of a cell an edge passes through is like that.
      |  ivec4 me = ivec4(own);
      |  if (all(equal(l, me)) && all(equal(la, me)) && all(equal(lb, me))) discard;
      |  ivec4 lo4 = min(min(l, la), lb);
      |  ivec4 hi4 = max(max(l, la), lb);
      |  int lo = min(min(lo4.x, lo4.y), min(lo4.z, lo4.w));
      |  int hi = max(max(hi4.x, hi4.y), max(hi4.z, hi4.w));
      |  float inkPx = uInkW * uPxPerUnit;
      |  vec4 res = vec4(0.0);
      |  int cur = 0;
      |  for (int k = lo; k <= hi; k++) {
      |    ivec4 kk = ivec4(k);
      |    if (!(any(equal(l, kk)) || any(equal(la, kk)) || any(equal(lb, kk)))) continue;
      |    float d = dist(l, f, k);
      |    int kind = int(uHas[k].y + 0.5);
      |    float da = kind == 1 ? dist(la, fa, k) : (kind == 2 ? dist(lb, fb, k) : d);
      |    float cover = clamp(d + 0.5, 0.0, 1.0);
      |    // what lies below carries round this layer and its riser
      |    if (cur > 0 && uHas[cur].z > 0.5) {
      |      float dd = kind != 0 ? max(d, da) : d;
      |      float wPx = uShore[cur].w * uPxPerUnit;
      |      float reach;
      |      if (uHas[cur].w > 0.5) { float g = clamp((dd + wPx) / wPx, 0.0, 1.0); reach = g * g; }
      |      else reach = clamp(dd + wPx + 0.5, 0.0, 1.0);
      |      res = over(res, uShore[cur].rgb, reach * (1.0 - clamp(dd + 0.5, 0.0, 1.0)));
      |    }
      |    if (k == own) res *= 1.0 - cover;
      |    else res = over(res, uBase[k], cover);
      |    if (cover > 0.5) cur = k;
      |    if (kind != 0) {
      |      float r = clamp(da + 0.5, 0.0, 1.0) * (1.0 - cover);
      |      float foot = clamp(inkPx - da + 0.5, 0.0, 1.0);
      |      res = over(res, uRiser[k], r * (1.0 - foot));
      |      res = over(res, uRiserInk[k], r * foot);
      |      if (r > 0.5) cur = k;
      |    }
      |    if (uHas[k].x > 0.5) res = over(res, uInk[k], cover * clamp(inkPx - d + 0.5, 0.0, 1.0));
      |  }
      |  if (res.a < 0.002) discard;
      |  FragColor = vec4(res.rgb / res.a, res.a);
      |}""".stripMargin
}
