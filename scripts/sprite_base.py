#!/usr/bin/env python3
"""Shared sprite generation framework for character sprite sheets.

Each character script provides a draw function that renders one frame and
calls generate_character() to produce the 512x512 sprite sheet.

Output: 512x512 PNG, 4 columns (frames) x 4 rows (directions).
Row layout: Down=0, Up=1, Left=2, Right=3. Frame 0 doubles as the idle pose
(the renderer shows it whenever a player stands still), so it is the standing
pose and the walk is stand / stride / stand / stride.

Frame size is 128x128. Draw functions author at 64x64 with the feet at y=54,
but never draw at that size: every primitive goes through ScaledDraw onto a
RENDER_SIZE (256px) canvas, which is then resampled down to 128. In game a
frame is shown at about 77px, so one authoring unit is ~1.2 screen pixels.

The art direction is MapleStory's (see "Style" below): a huge round head on a
small slim body, big glossy eyes, hair and hats as the silhouette, flat cel
shading, and every part outlined in a dark version of its own colour.
"""

import colorsys
import math

from PIL import Image, ImageChops, ImageDraw, ImageFilter

FRAME_SIZE = 128
DRAW_SIZE = 64     # Coordinate space the draw functions author in
COLS = 4
ROWS = 4
IMG_W = FRAME_SIZE * COLS   # 512
IMG_H = FRAME_SIZE * ROWS   # 512

# Supersampling: every frame is rendered at RENDER_SIZE and resampled to
# FRAME_SIZE, so curves, diagonals and thin strokes come out anti-aliased.
RENDER_SIZE = 256

DOWN, UP, LEFT, RIGHT = 0, 1, 2, 3

# The legacy "draw a dark outline" colour. ScaledDraw never paints it as
# given: any dark outline on a filled shape is re-inked from that shape's own
# fill (see `ink`), which is what gives every part a coloured line.
OUTLINE = (40, 35, 35)
BLACK = (30, 30, 30)
WHITE = (246, 246, 250)
CLEAR = (0, 0, 0, 0)


# ---------------------------------------------------------------------------
# Style
#
# MapleStory, concretely, and how each part of it is carried here:
#
# - Proportions. The head is about half the character: `rig` puts a skull of
#   12.4 x 11.2 units over a body 13 units wide and 25 tall, so the figure is
#   a little over two heads high. Do not "fix" this toward realism: a pass at
#   realistic thirds made the roster small, spindly and interchangeable.
# - Eyes are the face. `ms_eye` is a tall white with a big iris darkening
#   toward the top, a heavy upper lash and two catchlights. There is no nose.
# - Hair and hats are the silhouette. `hair` builds a whole hairstyle as one
#   mass -- crown, fringe and side locks -- with a gloss band across the crown.
# - Flat cel shading. `cel` fills a shape flat, lays one hard-edged shadow tone
#   on the side away from the light (upper left), and inks the outline. There
#   are no gradients and no noise anywhere.
# - Coloured outlines. `ink` is a dark, desaturated version of a fill's own
#   hue; ScaledDraw applies it to every outlined shape automatically.
# - Gear is oversized: hats, weapons, pauldrons and shields are drawn larger
#   than the body would suggest, because that is a lot of the charm.
# ---------------------------------------------------------------------------


# ---------------------------------------------------------------------------
# Colour
# ---------------------------------------------------------------------------

def _hsv(c):
    return colorsys.rgb_to_hsv(c[0] / 255.0, c[1] / 255.0, c[2] / 255.0)


def _from_hsv(h, s, v):
    r, g, b = colorsys.hsv_to_rgb(h % 1.0, min(1.0, max(0.0, s)), min(1.0, max(0.0, v)))
    return (int(round(r * 255)), int(round(g * 255)), int(round(b * 255)))


def _hue_toward(h, target, amount):
    delta = ((target - h + 0.5) % 1.0) - 0.5
    return h + delta * amount


def _luma(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def mix(a, b, t):
    """Blend two colours; t=0 is a, t=1 is b."""
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def ink(c):
    """The line a fill is outlined in: its own hue, dark and a little richer.

    Never black. A skin line is a warm brown, a white robe's is a slate blue,
    a gold helm's is a deep amber -- which is most of what makes a sprite read
    as painted rather than traced.
    """
    h, s, v = _hsv(c)
    if s < 0.12:
        return _from_hsv(_hue_toward(h, 0.68, 0.8), 0.28, max(0.15, v * 0.40))
    return _from_hsv(_hue_toward(h, 0.72, 0.10), min(1.0, s * 0.5 + 0.45), max(0.13, v * 0.44))


def shade(c, k=1.0):
    """One cel step darker: the flat tone a material turns on its shadow side.

    Hue-shifted toward violet rather than just darkened, so a yellow shades
    toward amber and a green toward teal instead of toward mud.
    """
    h, s, v = _hsv(c)
    if s < 0.08:
        return _from_hsv(0.66, min(0.25, 0.06 * k), v * (1.0 - 0.17 * k))
    return _from_hsv(_hue_toward(h, 0.72, 0.06 * k), s * (1.0 + 0.12 * k) + 0.03 * k,
                     v * (1.0 - 0.16 * k))


def lit(c, k=1.0):
    """One cel step lighter: a highlight, warmed toward yellow."""
    h, s, v = _hsv(c)
    return _from_hsv(_hue_toward(h, 0.14, 0.05 * k), s * (1.0 - 0.2 * k),
                     v * (1.0 + 0.1 * k) + 0.07 * k)


def _darken(color, factor=0.75):
    return tuple(max(0, int(c * factor)) for c in color[:3])


def _brighten(color, factor=1.25):
    return tuple(min(255, int(c * factor)) for c in color[:3])


class Fixed(tuple):
    """An outline colour used exactly as given, never re-inked from the fill."""

    def __new__(cls, *c):
        return tuple.__new__(cls, c[0] if len(c) == 1 else c)


# ---------------------------------------------------------------------------
# Drawing surface
# ---------------------------------------------------------------------------

class ScaledDraw:
    """ImageDraw proxy that scales a draw function's coordinates onto a larger canvas.

    Draw functions author in the 64px space; this multiplies every coordinate
    by `scale` so the same code renders onto the RENDER_SIZE supersampling
    canvas. Two further jobs:

    - Colour the lines. Any dark outline on a filled shape is replaced with
      `ink(fill)`, so every part is outlined in its own hue without each call
      site having to say so. Pass a `Fixed` colour to opt out.
    - Layers. `sub()` opens a transparent layer in the same coordinate space
      and `merge()` composites it back, optionally clipped to another layer.
      Drawing with fill=CLEAR on a layer erases, which is how `cel` cuts a
      hard-edged shadow band out of a shape.

    Box primitives keep the legacy inclusive-bounds mapping (x1 -> (x1+1)*s-1)
    so old call sites cover exactly the pixels they always did; the new kit
    draws everything as polygons, which map exactly.
    """

    def __init__(self, draw, scale, image=None):
        self._d = draw
        self._s = scale
        self._img = image if image is not None else getattr(draw, "_image", None)

    # -- coordinate mapping --------------------------------------------------

    def _pts(self, xy):
        s = self._s
        if len(xy) and isinstance(xy[0], (tuple, list)):
            return [(x * s, y * s) for (x, y) in xy]
        return [v * s for v in xy]

    def _box(self, xy):
        s = self._s
        if len(xy) and isinstance(xy[0], (tuple, list)):
            (x0, y0), (x1, y1) = xy[0], xy[1]
        else:
            x0, y0, x1, y1 = xy
        if x1 < x0:
            x0, x1 = x1, x0
        if y1 < y0:
            y0, y1 = y1, y0
        return [x0 * s, y0 * s, (x1 + 1) * s - 1, (y1 + 1) * s - 1]

    def _w(self, width):
        return max(1, int(round((width if width else 1) * self._s)))

    def _ow(self, outline, width):
        # Pillow ignores width when there is no outline; keep it untouched then.
        return self._w(width) if outline is not None else (width if width is not None else 0)

    @staticmethod
    def _auto(fill, outline):
        if outline is None or fill is None or isinstance(outline, Fixed):
            return outline
        if not isinstance(fill, (tuple, list)) or (len(fill) == 4 and fill[3] == 0):
            return outline
        if _luma(outline) < 80:
            return ink(fill)
        return outline

    # -- primitives ----------------------------------------------------------

    def rectangle(self, xy, fill=None, outline=None, width=1):
        outline = self._auto(fill, outline)
        self._d.rectangle(self._box(xy), fill=fill, outline=outline,
                          width=self._ow(outline, width))

    def rounded_rectangle(self, xy, radius=0, fill=None, outline=None, width=1, **kw):
        outline = self._auto(fill, outline)
        self._d.rounded_rectangle(self._box(xy), radius=radius * self._s, fill=fill,
                                  outline=outline, width=self._ow(outline, width), **kw)

    def ellipse(self, xy, fill=None, outline=None, width=1):
        outline = self._auto(fill, outline)
        self._d.ellipse(self._box(xy), fill=fill, outline=outline,
                        width=self._ow(outline, width))

    def polygon(self, xy, fill=None, outline=None, width=1):
        outline = self._auto(fill, outline)
        self._d.polygon(self._pts(xy), fill=fill, outline=outline,
                        width=self._ow(outline, width))

    def line(self, xy, fill=None, width=1, joint=None):
        # Lines are centred on their endpoints, so nudge to the middle of the
        # scaled block; otherwise a stroke along y=0 has half its width clipped.
        off = (self._s - 1) / 2.0
        pts = self._pts(xy)
        if pts and isinstance(pts[0], (tuple, list)):
            pts = [(x + off, y + off) for (x, y) in pts]
        else:
            pts = [v + off for v in pts]
        self._d.line(pts, fill=fill, width=self._w(width), joint=joint)

    def arc(self, xy, start, end, fill=None, width=1):
        self._d.arc(self._box(xy), start, end, fill=fill, width=self._w(width))

    def chord(self, xy, start, end, fill=None, outline=None, width=1):
        outline = self._auto(fill, outline)
        self._d.chord(self._box(xy), start, end, fill=fill, outline=outline,
                      width=self._ow(outline, width))

    def pieslice(self, xy, start, end, fill=None, outline=None, width=1):
        outline = self._auto(fill, outline)
        self._d.pieslice(self._box(xy), start, end, fill=fill, outline=outline,
                         width=self._ow(outline, width))

    def point(self, xy, fill=None):
        s = self._s
        if isinstance(xy[0], (tuple, list)):
            pts = xy
        elif len(xy) == 2:
            pts = [xy]
        else:
            pts = [(xy[i], xy[i + 1]) for i in range(0, len(xy), 2)]
        for (x, y) in pts:
            self._d.rectangle([x * s, y * s, (x + 1) * s - 1, (y + 1) * s - 1], fill=fill)

    # -- layers --------------------------------------------------------------

    def sub(self):
        """A fresh transparent layer in the same coordinate space."""
        img = Image.new("RGBA", self._img.size, CLEAR)
        return ScaledDraw(ImageDraw.Draw(img), self._s, img)

    def merge(self, layer, clip=None):
        """Composite `layer` onto this canvas, only where `clip` (a layer) is opaque."""
        src = layer._img
        if clip is not None:
            src = src.copy()
            src.putalpha(ImageChops.multiply(layer._img.getchannel("A"),
                                             clip._img.getchannel("A")))
        self._img.alpha_composite(src)

    def __getattr__(self, name):
        return getattr(self._d, name)


# ---------------------------------------------------------------------------
# Shapes
#
# The new kit draws everything as a polygon: ellipses, capsules and tapered
# limbs included. A polygon maps exactly through ScaledDraw, can be shifted
# (which is how `cel` finds a shape's shadow side), and has one continuous
# outline -- a limb with a separate round cap had a line drawn across it.
# ---------------------------------------------------------------------------

def arc_pts(cx, cy, rx, ry, a0, a1, n=None):
    """Points along an elliptical arc, degrees, y down (90 = straight down)."""
    if n is None:
        n = max(4, int(abs(a1 - a0) / 360.0 * (3.2 * (rx + ry) + 10)))
    return [(cx + rx * math.cos(math.radians(a0 + (a1 - a0) * i / float(n))),
             cy + ry * math.sin(math.radians(a0 + (a1 - a0) * i / float(n))))
            for i in range(n + 1)]


class Poly(object):
    __slots__ = ("pts",)

    def __init__(self, pts):
        self.pts = [(float(x), float(y)) for (x, y) in pts]

    def draw(self, d, fill=None, outline=None, width=1):
        d.polygon(self.pts, fill=fill, outline=outline, width=width)

    def shift(self, dx, dy):
        return Poly([(x + dx, y + dy) for (x, y) in self.pts])

    def scaled(self, k, ox, oy):
        return Poly([(ox + (x - ox) * k, oy + (y - oy) * k) for (x, y) in self.pts])


def Ell(cx, cy, rx, ry, n=None):
    return Poly(arc_pts(cx, cy, rx, ry, 0.0, 360.0, n)[:-1])


def RRect(x0, y0, x1, y1, rad):
    rad = max(0.01, min(rad, (x1 - x0) / 2.0, (y1 - y0) / 2.0))
    pts = []
    pts += arc_pts(x1 - rad, y0 + rad, rad, rad, 270, 360, 5)
    pts += arc_pts(x1 - rad, y1 - rad, rad, rad, 0, 90, 5)
    pts += arc_pts(x0 + rad, y1 - rad, rad, rad, 90, 180, 5)
    pts += arc_pts(x0 + rad, y0 + rad, rad, rad, 180, 270, 5)
    return Poly(pts)


def limb_pts(pts, widths, cap=True, cap0=False):
    """Outline of a tapered stroke along a polyline, with round ends."""
    n = len(pts)
    if isinstance(widths, (int, float)):
        widths = [widths] * n
    left, right, tang = [], [], []
    for i in range(n):
        if i == 0:
            dx, dy = pts[1][0] - pts[0][0], pts[1][1] - pts[0][1]
        elif i == n - 1:
            dx, dy = pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]
        else:
            dx, dy = pts[i + 1][0] - pts[i - 1][0], pts[i + 1][1] - pts[i - 1][1]
        ln = math.hypot(dx, dy) or 1.0
        tx, ty = dx / ln, dy / ln
        tang.append((tx, ty))
        nx, ny = -ty, tx
        w = widths[i]
        left.append((pts[i][0] + nx * w, pts[i][1] + ny * w))
        right.append((pts[i][0] - nx * w, pts[i][1] - ny * w))

    def cap_arc(c, w, a_start):
        # Half circle, clockwise on screen, between the two edges at one end.
        return arc_pts(c[0], c[1], w, w, a_start, a_start - 180.0, 8)[1:-1]

    out = list(left)
    if cap and widths[-1] > 0.3:
        tx, ty = tang[-1]
        out += cap_arc(pts[-1], widths[-1], math.degrees(math.atan2(tx, -ty)))
    out += right[::-1]
    if cap0 and widths[0] > 0.3:
        tx, ty = tang[0]
        out += cap_arc(pts[0], widths[0], math.degrees(math.atan2(tx, -ty)) + 180.0)
    return out


def Limb(pts, widths, cap=True, cap0=False):
    return Poly(limb_pts(pts, widths, cap, cap0))


def cel(draw, shape, color, sh=(1.2, 1.2), hi=None, tone=None, hi_tone=None,
        regions=(), line=True, line_color=None, lw=1.0):
    """Fill a shape flat, cel-shade it, and ink its outline.

    `sh` is how far the lit face is pushed toward the light (upper left): the
    band of the shape the shifted copy no longer covers is the shadow side,
    filled with one flat tone. `hi` does the same the other way round for a
    rim of light along the upper-left edge. `regions` are extra (shape, colour)
    pairs clipped to the shape -- a collar's shadow, the dark half of a
    tabard. The line goes on last so it sits over the shading.
    """
    shape.draw(draw, fill=color)
    if sh:
        s = draw.sub()
        shape.draw(s, fill=tone or shade(color))
        shape.shift(-sh[0], -sh[1]).draw(s, fill=CLEAR)
        draw.merge(s)
    if regions:
        m = draw.sub()
        shape.draw(m, fill=(255, 255, 255))
        for reg, col in regions:
            layer = draw.sub()
            reg.draw(layer, fill=col)
            draw.merge(layer, clip=m)
    if hi:
        h = draw.sub()
        shape.draw(h, fill=hi_tone or lit(color))
        shape.shift(hi[0], hi[1]).draw(h, fill=CLEAR)
        draw.merge(h)
    if line:
        shape.draw(draw, fill=None, outline=line_color or ink(color), width=lw)


def clipped(draw, clip_shape, fn):
    """Run fn(layer) on a fresh layer and merge it clipped to `clip_shape`."""
    m = draw.sub()
    clip_shape.draw(m, fill=(255, 255, 255))
    layer = draw.sub()
    fn(layer)
    draw.merge(layer, clip=m)


def stroke(draw, pts, width, color):
    """A round-ended stroke of constant width, as a filled polygon."""
    Poly(limb_pts(pts, width / 2.0, True, True)).draw(draw, fill=color)


def blob(draw, shapes, color, sh=(1.2, 1.2), tone=None, line=True, line_color=None, lw=1.0,
         regions=()):
    """The union of several shapes as one cel-shaded mass with one outline.

    For anything fluffy -- beards, clouds, fur ruffs, foliage, smoke -- where
    drawing overlapping ellipses one by one would ink a line across every seam.
    The outline is found in image space: the mass's edge, eroded by the line
    width.
    """
    mass = draw.sub()
    for shp in shapes:
        shp.draw(mass, fill=color)
    if sh:
        s = draw.sub()
        for shp in shapes:
            shp.draw(s, fill=tone or shade(color))
        for shp in shapes:
            shp.shift(-sh[0], -sh[1]).draw(s, fill=CLEAR)
        mass.merge(s)
    for reg, col in regions:
        layer = draw.sub()
        reg.draw(layer, fill=col)
        mass.merge(layer, clip=mass)
    if line:
        a = mass._img.getchannel("A")
        k = max(1, int(round(lw * mass._s)))
        ring = ImageChops.subtract(a, a.filter(ImageFilter.MinFilter(2 * k + 1)))
        mass._img.paste(Image.new("RGBA", mass._img.size, tuple(line_color or ink(color)) + (255,)),
                        (0, 0), ring)
    draw.merge(mass)


def sparkle(draw, x, y, rad, color, core=(255, 255, 255)):
    """A four-point twinkle: magic, glints, stars."""
    k = rad * 0.28
    Poly([(x, y - rad), (x + k, y - k), (x + rad, y), (x + k, y + k), (x, y + rad),
          (x - k, y + k), (x - rad, y), (x - k, y - k)]).draw(draw, fill=color)
    if rad > 1.6:
        Ell(x, y, k * 1.1, k * 1.1).draw(draw, fill=core)


def star(draw, x, y, rad, color, points=5, inner=0.45, rot=-90.0, line=None):
    pts = []
    for i in range(points * 2):
        a = math.radians(rot + i * 180.0 / points)
        rr = rad if i % 2 == 0 else rad * inner
        pts.append((x + rr * math.cos(a), y + rr * math.sin(a)))
    Poly(pts).draw(draw, fill=color, outline=line, width=0.6 if line else 1)


# ---------------------------------------------------------------------------
# Legacy helpers (still used by the older draw functions)
# ---------------------------------------------------------------------------

def ellipse(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=outline)


def pill(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    draw.rounded_rectangle([cx - rx, cy - ry, cx + rx, cy + ry],
                           radius=min(rx, ry), fill=fill, outline=outline)


def draw_fur_texture(draw, cx, cy, w, h, color, density=3):
    """Scattered dot pattern for fur texture."""
    dark = _darken(color, 0.8)
    light = _brighten(color, 1.15)
    for row in range(0, h, density + 1):
        offset = (density // 2) if (row // (density + 1)) % 2 == 0 else 0
        for col in range(offset, w, density + 2):
            px = cx - w // 2 + col
            py = cy - h // 2 + row
            draw.point((px, py), fill=dark if (row + col) % 3 == 0 else light)


def draw_scale_texture(draw, cx, cy, w, h, color):
    """Diamond/scale pattern for reptile/fish skin."""
    dark = _darken(color, 0.85)
    light = _brighten(color, 1.1)
    for row in range(0, h, 4):
        offset = 2 if (row // 4) % 2 == 0 else 0
        for col in range(offset, w, 5):
            px = cx - w // 2 + col
            py = cy - h // 2 + row
            draw.point((px, py - 1), fill=dark)
            draw.point((px - 1, py), fill=light)
            draw.point((px + 1, py), fill=light)
            draw.point((px, py + 1), fill=dark)


def limb(draw, pts, widths, fill, outline=OUTLINE, cap=True):
    """A tapered limb following a polyline: arms, legs, necks, tails, tentacles.

    `pts` are the joints and `widths` the half-width at each (a scalar applies
    to all of them). One polygon with a round end, so the outline runs
    unbroken round the tip.
    """
    if len(pts) < 2:
        return
    draw.polygon(limb_pts(pts, widths, cap), fill=fill, outline=outline)


# ---------------------------------------------------------------------------
# Faces
# ---------------------------------------------------------------------------

SKIN = (255, 224, 198)
SKIN_TAN = (240, 192, 152)
SKIN_BROWN = (196, 136, 100)
SKIN_PALE = (238, 232, 236)
EYE_WHITE = (253, 253, 255)
LASH = (58, 34, 46)
IRIS = (112, 72, 54)
MOUTH = (178, 84, 84)


def ms_eye(draw, ex, ey, w=4.2, h=5.6, iris=IRIS, look=(0.0, 0.0), mood="bright",
           glow=None, skin=SKIN, side=1, lash=LASH):
    """A MapleStory eye.

    A tall white almost filled by the iris, which darkens toward the top; a
    heavy upper lash that flicks out at the outer corner; and two catchlights,
    a big one upper-left and a small one lower-right. At 77px this is the whole
    face -- it carries more expression than everything else on the head.

    `side` is which eye this is on screen (-1 left, +1 right), so the flick and
    any angled lid go the right way. Moods: "bright" (the default, round and
    open), "sharp" (lid angled down toward the nose -- determined, villainous),
    "calm" (a level half-lid), "closed" (a happy ^ arc), "wide" (no lid at all).
    `glow` replaces the eye with a lit shape for the undead and machines.
    """
    rx, ry = w / 2.0, h / 2.0
    if glow is not None:
        Ell(ex, ey + ry * 0.12, rx * 1.05, ry * 0.8).draw(draw, fill=glow)
        Ell(ex - rx * 0.15, ey - ry * 0.02, rx * 0.5, ry * 0.4).draw(
            draw, fill=lit(glow, 2.2))
        return
    if mood == "closed":
        stroke(draw, [(ex - rx, ey + 0.6), (ex - rx * 0.4, ey - 0.7), (ex + rx * 0.4, ey - 0.7),
                      (ex + rx, ey + 0.6)], 1.1, lash)
        return
    Ell(ex, ey, rx, ry).draw(draw, fill=EYE_WHITE)
    ix = ex + look[0] * rx * 0.16
    iy = ey + ry * 0.1 + look[1] * ry * 0.1
    irx, iry = rx * 0.84, ry * 0.82
    Ell(ix, iy, irx, iry).draw(draw, fill=iris)
    Ell(ix, iy - iry * 0.24, irx * 0.72, iry * 0.56).draw(draw, fill=shade(iris, 2.8))
    Ell(ix + irx * 0.08, iy + iry * 0.52, irx * 0.5, iry * 0.26).draw(draw, fill=lit(iris, 1.5))
    # Lids. `inner` is the corner toward the nose.
    lid_top = ey - ry - 0.6
    if mood == "sharp":
        inner_y, outer_y = ey - ry * 0.05, ey - ry * 0.62
    elif mood == "calm":
        inner_y = outer_y = ey - ry * 0.3
    else:
        inner_y = outer_y = None
    x_in, x_out = ex - side * rx, ex + side * rx
    if inner_y is not None:
        Poly([(x_in - side * 0.4, lid_top), (x_out + side * 0.5, lid_top),
              (x_out + side * 0.5, outer_y), (x_in - side * 0.4, inner_y)]).draw(draw, fill=skin)
        stroke(draw, [(x_in - side * 0.2, inner_y + 0.2), (x_out + side * 0.3, outer_y - 0.1),
                      (x_out + side * 1.2, outer_y - 0.9)], 1.2, lash)
    else:
        top = arc_pts(ex, ey, rx + 0.25, ry + 0.3, 196, 344, 12)
        under = arc_pts(ex, ey, rx - 0.45, ry - 0.8, 344, 196, 12)
        Poly(top + under).draw(draw, fill=lash)
        tip = top[-1] if side > 0 else top[0]
        stroke(draw, [tip, (tip[0] + side * 1.1, tip[1] - 0.9)], 0.9, lash)
    Ell(ix - irx * 0.36, iy - iry * 0.36, 0.95, 0.95).draw(draw, fill=(255, 255, 255))
    Ell(ix + irx * 0.4, iy + iry * 0.38, 0.5, 0.5).draw(draw, fill=(255, 255, 255))


def ms_brow(draw, bx, by, w, color, tilt=0.0, side=1, thick=0.85):
    """A thin, slightly arched brow. `tilt` > 0 drops the inner end (anger)."""
    x_in, x_out = bx - side * w / 2.0, bx + side * w / 2.0
    stroke(draw, [(x_in, by + tilt * 1.4), (bx, by - 0.45 + tilt * 0.4), (x_out, by + 0.2 - tilt * 0.3)],
           thick, color)


def ms_mouth(draw, mx, my, kind="smile", skin=SKIN, scale=1.0):
    """Tiny, as MapleStory mouths are. Kinds: smile, set, open, grin, frown,
    smirk, fangs, cat, o, None."""
    if kind is None:
        return
    s = scale
    line_col = mix(ink(skin), MOUTH, 0.35)
    if kind == "smile":
        stroke(draw, [(mx - 1.3 * s, my - 0.3), (mx, my + 0.45), (mx + 1.3 * s, my - 0.3)], 0.8, line_col)
    elif kind == "set":
        stroke(draw, [(mx - 1.0 * s, my), (mx + 1.0 * s, my)], 0.8, line_col)
    elif kind == "frown":
        stroke(draw, [(mx - 1.2 * s, my + 0.4), (mx, my - 0.3), (mx + 1.2 * s, my + 0.4)], 0.8, line_col)
    elif kind == "smirk":
        stroke(draw, [(mx - 1.1 * s, my + 0.1), (mx + 0.4 * s, my + 0.2), (mx + 1.4 * s, my - 0.6)], 0.8, line_col)
    elif kind in ("open", "o"):
        rx, ry = (1.2 * s, 1.35) if kind == "open" else (0.8 * s, 0.9)
        Ell(mx, my + 0.3, rx, ry).draw(draw, fill=(150, 44, 52))
        if kind == "open":
            Ell(mx, my + 0.3 + ry * 0.45, rx * 0.7, ry * 0.45).draw(draw, fill=(236, 116, 118))
    elif kind == "grin":
        pts = [(mx - 1.9 * s, my - 0.6), (mx + 1.9 * s, my - 0.6)] + \
            arc_pts(mx, my - 0.6, 1.9 * s, 1.9, 0, 180, 10)[1:-1]
        Poly(pts).draw(draw, fill=(150, 44, 52))
        Poly([(mx - 1.6 * s, my - 0.6), (mx + 1.6 * s, my - 0.6), (mx + 1.3 * s, my + 0.2),
              (mx - 1.3 * s, my + 0.2)]).draw(draw, fill=(255, 255, 255))
    elif kind == "fangs":
        stroke(draw, [(mx - 1.6 * s, my - 0.3), (mx, my + 0.3), (mx + 1.6 * s, my - 0.3)], 0.8, line_col)
        for k in (-1, 1):
            Poly([(mx + k * 1.0 * s - 0.45, my - 0.1), (mx + k * 1.0 * s + 0.45, my - 0.05),
                  (mx + k * 1.0 * s, my + 1.3)]).draw(draw, fill=(255, 255, 255))
    elif kind == "cat":
        stroke(draw, [(mx - 1.5 * s, my - 0.2), (mx - 0.75 * s, my + 0.5), (mx, my - 0.1),
                      (mx + 0.75 * s, my + 0.5), (mx + 1.5 * s, my - 0.2)], 0.75, line_col)


def eye(draw, cx, cy, rx, ry, sclera=WHITE, pupil=BLACK, look=(0.0, 0.0),
        glow=None, outline=None, shine=True):
    """Legacy small eye, restyled: a dark iris filling the eye with a catchlight."""
    if glow is not None:
        Ell(cx, cy, rx, ry).draw(draw, fill=glow)
        Ell(cx, cy, max(rx * 0.45, 0.6), max(ry * 0.45, 0.6)).draw(draw, fill=lit(glow, 2.0))
        return
    Ell(cx, cy, rx, ry).draw(draw, fill=sclera)
    px, py = cx + look[0] * rx * 0.3, cy + look[1] * ry * 0.3
    Ell(px, py, rx * 0.78, ry * 0.85).draw(draw, fill=pupil)
    if shine:
        Ell(px - rx * 0.3, py - ry * 0.35, max(0.5, rx * 0.3), max(0.5, rx * 0.3)).draw(
            draw, fill=(255, 255, 255))


def brow(draw, cx, cy, half_w, thickness, color, tilt=0.0):
    """Legacy brow ridge. `tilt` > 0 drops the inner end (angry)."""
    inner_y = cy + tilt * half_w
    outer_y = cy - tilt * half_w * 0.35
    draw.polygon([(cx - half_w, outer_y), (cx + half_w, inner_y),
                  (cx + half_w, inner_y + thickness), (cx - half_w, outer_y + thickness)],
                 fill=color, outline=None)


def muzzle(draw, cx, cy, rx, ry, color, nose_color=None, mouth_color=None,
           nostrils=True, outline=OUTLINE):
    """A snout projecting past the skull line, with a nose pad and a mouth."""
    if nose_color is None:
        nose_color = (58, 40, 44)
    cel(draw, Ell(cx, cy, rx, ry), color, sh=(0.6, 0.7))
    Ell(cx, cy - ry * 0.42, rx * 0.46, ry * 0.36).draw(draw, fill=nose_color)
    Ell(cx - rx * 0.12, cy - ry * 0.52, rx * 0.16, ry * 0.12).draw(draw, fill=lit(nose_color, 3.0))
    if mouth_color is not None:
        stroke(draw, [(cx - rx * 0.6, cy + ry * 0.35), (cx, cy + ry * 0.62),
                      (cx + rx * 0.6, cy + ry * 0.35)], 0.7, mouth_color)


def fang_row(draw, x0, x1, y, count, height, color=(252, 250, 244), down=True):
    """A row of teeth along a jaw line. `down` points them downward (upper jaw)."""
    if count < 1:
        return
    step = (x1 - x0) / float(count)
    for i in range(count):
        fx = x0 + step * (i + 0.5)
        tip = y + height if down else y - height
        draw.polygon([(fx - step * 0.38, y), (fx + step * 0.38, y), (fx, tip)],
                     fill=color, outline=None)


def claw(draw, x, y, dx, dy, length, width, color=(240, 238, 230)):
    """One curved claw or talon from (x, y) along (dx, dy)."""
    length_n = math.hypot(dx, dy) or 1.0
    ux, uy = dx / length_n, dy / length_n
    px, py = -uy, ux
    draw.polygon([(x + px * width, y + py * width),
                  (x - px * width, y - py * width),
                  (x + ux * length, y + uy * length)],
                 fill=color, outline=None)


def face(draw, cx, cy, skin, spread=4.0, eye_r=1.9, brow_color=None,
         eye_color=None, glow=None, tilt=0.35, mouth="set", look=(0.0, 0.0),
         nose=True):
    """Legacy face call, drawn with MapleStory eyes."""
    k = max(0.7, eye_r / 2.1)
    for side in (-1, 1):
        ms_eye(draw, cx + side * spread, cy, w=4.0 * k, h=5.2 * k, iris=eye_color or IRIS,
               look=look, mood="sharp" if tilt > 0.5 else "bright", glow=glow, skin=skin, side=side)
    ms_mouth(draw, cx + look[0] * 2.0, cy + eye_r + 4.0, mouth if mouth != "grim" else "frown", skin)


# ---------------------------------------------------------------------------
# Humanoid rig
#
# Most of the roster are people: a head, a torso, two arms, two legs and a
# costume. `rig` lays out one frame of that skeleton at MapleStory proportions
# and the rig_* functions draw it, so a proportion change here reaches every
# humanoid at once -- the same reason every sheet goes through
# `generate_character`. Characters call them in order and hang their costume
# off the anchor points in between:
#
#     hair(layer="back") -> cape(layer="under") -> legs -> arms(layer="far")
#       -> torso / robe -> arms(layer="near") -> head -> hair -> hat
#       -> cape(layer="over")
#
# The layers exist because draw order changes with the facing: the far arm is
# behind the torso in profile, and a cape or long hair is behind the body
# head-on but in front of it from behind.
# ---------------------------------------------------------------------------

HEAD_RX = 12.4
HEAD_RY = 11.2


class Rig(object):
    """Anchor points for one frame of a humanoid, in the 64px draw space."""

    __slots__ = ("cx", "base_y", "d", "back", "frame", "bob", "step", "ph",
                 "hip_y", "waist_y", "sh_y", "neck_y", "chin_y", "head_cy", "hx",
                 "head_rx", "head_ry", "sh_w", "hip_w", "build")

    @property
    def front(self):
        return not self.d and not self.back

    def near(self, side):
        """Is this limb on the camera side? Always head-on; in profile, one of them."""
        return (not self.d) or side * self.d > 0

    def shoulder(self, side):
        """Where an arm leaves the body. `side` is -1 (screen left) or +1."""
        if self.d:
            return (self.cx + self.d * (0.4 if self.near(side) else -0.8), self.sh_y + 0.2)
        return (self.cx + side * (self.sh_w - 1.3), self.sh_y + 0.4)

    def hand(self, side):
        """Where that arm ends: at the hip, a little outside the body."""
        if self.d:
            fwd = self.ph if not self.near(side) else -self.ph
            return (self.cx + self.d * (0.6 + fwd * 3.2), self.hip_y - 1.4 - abs(fwd) * 0.4)
        fwd = -self.ph * side
        return (self.cx + side * (self.sh_w + 2.1 - fwd * 0.3), self.hip_y - 1.6 - fwd * 0.6)

    def hip(self, side):
        if self.d:
            return (self.cx + self.d * (0.6 if self.near(side) else -0.8), self.hip_y - 1.5)
        return (self.cx + side * 2.9 * min(self.build, 1.2), self.hip_y - 1.5)

    def foot(self, side):
        """Where the sole meets the ground (the shoe is drawn around it)."""
        if self.d:
            fwd = self.ph if self.near(side) else -self.ph
            return (self.cx + self.d * (0.4 + fwd * 3.4), self.base_y - 1.3 - (1.1 if fwd < 0 else 0.0))
        fwd = self.ph * side
        return (self.cx + side * 3.0 * min(self.build, 1.2),
                self.base_y - 1.3 + (0.4 if fwd > 0 else (-1.4 if fwd < 0 else 0.0)))

    def eye_look(self):
        return (float(self.d), 0.0)


def rig(ox, oy, direction, frame, build=1.0, bob=None, step=None, head=1.0):
    """Lay out one frame. `build` scales the body's width -- 1.3 is a bruiser,
    0.85 a waif -- and `head` the skull, for the few characters whose hat or
    hair needs headroom (a smaller skull sits lower; the chin never moves).

    The walk is stand / stride / stand / stride, with a one-unit hop on the
    strides; frame 0 is the idle pose.
    """
    r = Rig()
    r.frame = frame
    r.ph = [0, 1, 0, -1][frame] if step is None else step / 3.0
    r.step = r.ph * 3.0
    r.bob = [0, -1, 0, -1][frame] if bob is None else bob
    r.build = build
    r.cx = ox + 32
    r.base_y = oy + 54 + r.bob
    r.d = {DOWN: 0, UP: 0, LEFT: -1, RIGHT: 1}[direction]
    r.back = direction == UP
    r.head_ry = HEAD_RY * head
    r.head_rx = HEAD_RX * head
    r.chin_y = r.base_y - 22.3
    r.head_cy = r.chin_y - r.head_ry
    r.hx = r.cx + r.d * 1.0
    r.sh_y = r.base_y - 20.0
    r.neck_y = r.sh_y - 1.5
    r.waist_y = r.base_y - 14.0
    r.hip_y = r.base_y - 10.0
    r.sh_w = 6.4 * build
    r.hip_w = 6.9 * build
    return r


def shoe(r, draw, fx, fy, color, near=True, big=1.0):
    """A small rounded shoe with its sole on (fx, fy + 1.2), toe toward the facing."""
    col = color if near else shade(color, 0.5)
    if r.d:
        x0, x1 = fx - r.d * 2.4 * big, fx + r.d * 3.7 * big
        shape = RRect(min(x0, x1), fy - 2.1, max(x0, x1), fy + 1.4, 1.6)
    else:
        shape = RRect(fx - 2.9 * big, fy - 2.0, fx + 2.9 * big, fy + 1.4, 1.6)
    cel(draw, shape, col, sh=(0.0, 0.7), hi=(0.0, 0.8) if near else None)


def rig_legs(r, draw, leg, boot=None, bare=False, sides=None, width=1.0):
    """Two short legs with a small shoe on each; the far leg first in profile."""
    if boot is None:
        boot = shade(leg, 2.2)
    order = (-r.d, r.d) if r.d else (-1, 1)
    for side in (sides or order):
        hx0, hy0 = r.hip(side)
        fx, fy = r.foot(side)
        near = r.near(side)
        col = leg if near else shade(leg, 0.6)
        w0 = 2.6 * width * min(r.build, 1.25)
        cel(draw, Limb([(hx0, hy0), (fx, fy - 1.2)], [w0, w0 * 0.86]), col, sh=(0.9, 0.0))
        if bare:
            cel(draw, Ell(fx + r.d * 0.8, fy, 2.4, 1.5), col, sh=(0.0, 0.6))
        else:
            shoe(r, draw, fx, fy, boot, near)


def torso_shape(r, top=None, bottom=None, w=None, hw=None):
    top = r.sh_y - 2.4 if top is None else top
    bot = r.hip_y + 0.5 if bottom is None else bottom
    cx, d = r.cx, r.d
    if d:
        return Poly([(cx - d * 3.6, top), (cx + d * 2.8, top), (cx + d * 4.5, top + 1.7),
                     (cx + d * 5.0, bot - 1.2), (cx + d * 4.5, bot), (cx - d * 4.3, bot),
                     (cx - d * 4.8, bot - 1.2), (cx - d * 4.7, top + 1.4)])
    w = r.sh_w if w is None else w
    hw = r.hip_w if hw is None else hw
    return Poly([(cx - w + 1.8, top), (cx + w - 1.8, top), (cx + w - 0.3, top + 1.1),
                 (cx + w, r.sh_y + 0.6), (cx + hw + 0.3, bot - 1.1), (cx + hw, bot),
                 (cx - hw, bot), (cx - hw - 0.3, bot - 1.1), (cx - w, r.sh_y + 0.6),
                 (cx - w + 0.3, top + 1.1)])


def rig_torso(r, draw, cloth, light=None, dark=None, belt=None, top=None, bottom=None):
    """A slim torso, cel-shaded, with the head's shadow under the chin."""
    shape = torso_shape(r, top, bottom)
    t = shape.pts[0][1]
    regions = [(Ell(r.hx, t + 0.4, r.head_rx * 0.62, 2.3), dark or shade(cloth))]
    cel(draw, shape, cloth, sh=(1.5, 0.9), tone=dark, regions=regions)
    if belt is not None:
        rig_belt(r, draw, belt)


def rig_belt(r, draw, belt, buckle=None, y=None):
    y = r.waist_y + 1.0 if y is None else y
    cx, d = r.cx, r.d
    if d:
        shape = RRect(cx - 4.5, y, cx + 4.6, y + 2.2, 0.6)
    else:
        shape = RRect(cx - r.hip_w - 0.6, y, cx + r.hip_w + 0.6, y + 2.2, 0.6)
    cel(draw, shape, belt, sh=(0.0, 0.6))
    if not r.back:
        bx = cx + d * 3.2
        cel(draw, RRect(bx - 1.4, y - 0.3, bx + 1.4, y + 2.5, 0.5), buckle or (236, 200, 96),
            sh=(0.5, 0.5))


def arm_pts(r, side, reach=0.0, lift=0.0, out=0.0):
    """Shoulder, elbow, wrist and hand centre for one arm. `out` holds the
    hand away from the body (something carried at arm's length)."""
    sx, sy = r.shoulder(side)
    hx, hy = r.hand(side)
    if r.d:
        hx += r.d * (reach * 4.0 + out)
    else:
        hx -= side * (reach * 2.6 - out)
    hy -= reach * 3.2 + lift
    out = side if not r.d else -r.d * 0.4
    ex, ey = (sx + hx) / 2.0 + out * 0.7, (sy + hy) / 2.0
    return (sx, sy), (ex, ey), (hx, hy - 1.0), (hx, hy)


def rig_arms(r, draw, sleeve, glove=None, light=None, reach=0.0, sides=(-1, 1),
             layer=None, cuff=None, hand_r=2.2, hands=True, out=0.0):
    """Thin arms ending in round hands.

    `reach` brings the hands in front of the body (a cast, a lunge). `layer`
    splits the pair for draw order: "far" draws only the arm behind the torso
    in profile, "near" everything else; None draws both, as before.
    """
    if glove is None:
        glove = shade(sleeve, 1.5)
    for side in sides:
        near = r.near(side)
        if layer == "far" and (not r.d or near):
            continue
        if layer == "near" and r.d and not near:
            continue
        s, e, w, h = arm_pts(r, side, reach, 0.0, out)
        col = sleeve if near else shade(sleeve, 0.6)
        aw = 2.2 * min(r.build, 1.3)
        cel(draw, Limb([s, e, w], [aw, aw * 0.9, aw * 0.82]), col, sh=(0.8, 0.4))
        if cuff is not None:
            cel(draw, Ell(w[0], w[1] + 0.2, aw * 0.95, 1.0), cuff, sh=None)
        if hands:
            cel(draw, Ell(h[0], h[1], hand_r, hand_r), glove if near else shade(glove, 0.5),
                sh=(0.6, 0.6))


def rig_hand(r, draw, side, glove, reach=0.0, lift=0.0, hand_r=2.2, out=0.0):
    """One hand on its own, for drawing a grip over whatever it holds."""
    h = hand_at(r, side, reach, lift, out)
    cel(draw, Ell(h[0], h[1], hand_r, hand_r), glove if r.near(side) else shade(glove, 0.5),
        sh=(0.6, 0.6))
    return h


def hand_at(r, side, reach=0.0, lift=0.0, out=0.0):
    """Centre of a hand, for things held in it."""
    return arm_pts(r, side, reach, lift, out)[3]


def rig_robe(r, draw, cloth, trim=None, flare=3.0, hem=None, top=None, dark=None, split=False):
    """A robe or dress from the shoulders to just above the feet."""
    cx, d = r.cx, r.d
    top = r.sh_y - 2.4 if top is None else top
    hem = r.base_y - 2.6 if hem is None else hem
    sway = r.ph * 0.8
    if d:
        shape = Poly([(cx - d * 3.4, top), (cx + d * 2.6, top), (cx + d * 4.3, top + 1.8),
                      (cx + d * (4.6 + flare * 0.5) + sway * d, hem),
                      (cx - d * (4.6 + flare * 0.8) - sway * d, hem), (cx - d * 4.5, top + 1.5)])
    else:
        w = r.sh_w
        shape = Poly([(cx - w + 1.8, top), (cx + w - 1.8, top), (cx + w - 0.3, top + 1.1),
                      (cx + w, r.sh_y + 0.6), (cx + w + flare + sway, hem),
                      (cx - w - flare + sway, hem), (cx - w, r.sh_y + 0.6), (cx - w + 0.3, top + 1.1)])
    t = shape.pts[0][1]
    regions = [(Ell(r.hx, t + 0.4, r.head_rx * 0.62, 2.3), dark or shade(cloth))]
    if split and not d and not r.back:
        regions.append((Poly([(cx - 0.5, r.waist_y), (cx + 0.5, r.waist_y), (cx + 2.5, hem + 1),
                              (cx - 2.5, hem + 1)]), shade(cloth, 1.8)))
    cel(draw, shape, cloth, sh=(1.8, 0.8), tone=dark, regions=regions)
    if trim is not None:
        ys = hem - 1.6
        if d:
            band = Poly([(cx + d * (4.6 + flare * 0.5) + sway * d, ys + 0.1),
                         (cx + d * (4.7 + flare * 0.5) + sway * d, hem),
                         (cx - d * (4.6 + flare * 0.8) - sway * d, hem),
                         (cx - d * (4.5 + flare * 0.8) - sway * d, ys + 0.1)])
        else:
            w = r.sh_w
            k = (ys - (r.sh_y + 0.6)) / max(1.0, hem - (r.sh_y + 0.6))
            band = Poly([(cx - w - flare * k + sway, ys), (cx + w + flare * k + sway, ys),
                         (cx + w + flare + sway, hem), (cx - w - flare + sway, hem)])
        cel(draw, band, trim, sh=(0.0, 0.5))
    return shape


def rig_head(r, draw, skin, hair=None, hair_back=None, expression="smile",
             eye_color=None, glow=None, brow_color=None, tilt=0.0, ears=True,
             neck=True, style=None, mood=None, blush=True, eyes=True, hat=False,
             brows=None, lash=LASH):
    """The skull, the face and (if given) the hair's front layer.

    `hair` is the hair colour and `style` a key of HAIR_STYLES. `mood` picks the
    eye shape (see ms_eye); `tilt` > 0.5 on an older call means "sharp". Long
    hair also needs its back layer drawn before the body: hair(layer="back").
    """
    if mood is None:
        mood = "sharp" if tilt >= 0.5 else "bright"
    head_skull(r, draw, skin, ears=ears)
    if not r.back and eyes:
        head_face(r, draw, skin, iris=eye_color or IRIS, mood=mood, glow=glow,
                  mouth=expression, blush=blush,
                  brows=brows if brows is not None else (hair is None or hat),
                  brow_color=brow_color, lash=lash, tilt=tilt)
    if hair is not None:
        rig_hair(r, draw, hair, style or "short", hat=hat, skin=skin)
    elif hair_back is not None and r.back:
        rig_hair(r, draw, hair_back, "crop", hat=hat)


def head_skull(r, draw, skin, ears=True, rx=None, ry=None):
    hx, hy = r.hx, r.head_cy
    rx = r.head_rx if rx is None else rx
    ry = r.head_ry if ry is None else ry
    if ears and not r.d:
        for side in (-1, 1):
            cel(draw, Ell(hx + side * (rx - 0.3), hy + 2.2, 1.9, 2.5), skin, sh=(0.5, 0.5))
    cel(draw, Ell(hx, hy, rx, ry), skin, sh=(0.9, 0.8) if not r.back else (0.8, 0.8))
    if ears and r.d:
        ex = hx - r.d * 3.0
        cel(draw, Ell(ex, hy + 2.6, 1.9, 2.5), skin, sh=(0.4, 0.5))
        Ell(ex + r.d * 0.2, hy + 2.8, 0.8, 1.3).draw(draw, fill=shade(skin, 1.2))


def face_anchor(r):
    """Where the eyes sit: (near/left eye x, far/right eye x, eye y, mouth x, mouth y)."""
    hx, hy = r.hx, r.head_cy
    k = r.head_rx / HEAD_RX
    if r.d:
        return (hx + r.d * 1.4 * k, hx + r.d * 7.6 * k, hy + 2.3 * k, hx + r.d * 6.4 * k, hy + 7.3 * k)
    return (hx - 4.7 * k, hx + 4.7 * k, hy + 2.3 * k, hx, hy + 7.4 * k)


def head_face(r, draw, skin, iris=IRIS, mood="bright", glow=None, mouth="smile",
              blush=True, brows=True, brow_color=None, lash=LASH, tilt=0.0, eye_scale=1.0):
    """Eyes, brows, blush and mouth, facing the rig's direction."""
    k = r.head_rx / HEAD_RX * eye_scale
    e1, e2, ey, mx, my = face_anchor(r)
    look = r.eye_look()
    if brow_color is None:
        brow_color = mix(lash, skin, 0.25)
    if r.d:
        near_x, far_x = e1, e2
        if blush:
            Ell(near_x - r.d * 1.6, ey + 3.4 * k, 1.8, 0.8).draw(draw, fill=mix(skin, (255, 128, 128), 0.32))
        ms_eye(draw, far_x, ey, 3.0 * k, 5.2 * k, iris, look, mood, glow, skin, r.d, lash)
        ms_eye(draw, near_x, ey, 4.2 * k, 5.6 * k, iris, look, mood, glow, skin, -r.d, lash)
        if brows:
            ms_brow(draw, near_x, ey - 4.3 * k, 3.4 * k, brow_color, tilt, -r.d)
            ms_brow(draw, far_x, ey - 4.2 * k, 2.4 * k, brow_color, tilt, r.d)
        ms_mouth(draw, mx, my, mouth, skin, 0.8)
        return
    if blush:
        for side in (-1, 1):
            Ell(r.hx + side * 7.4 * k, ey + 3.3 * k, 1.9, 0.85).draw(
                draw, fill=mix(skin, (255, 128, 128), 0.32))
    for side, ex in ((-1, e1), (1, e2)):
        ms_eye(draw, ex, ey, 4.2 * k, 5.6 * k, iris, look, mood, glow, skin, side, lash)
        if brows:
            ms_brow(draw, ex, ey - 4.4 * k, 3.4 * k, brow_color, tilt, side)
    ms_mouth(draw, mx, my, mouth, skin)


# ---------------------------------------------------------------------------
# Hair
#
# A hairstyle is one mass: crown, fringe and side locks in a single polygon,
# so its outline is continuous and it reads as a shape rather than as a cap
# with bits stuck on. Styles are parameters, not drawings, so the same code
# builds all four facings of every style:
#
#   vol     how far the mass stands off the skull
#   fringe  lock tips across the brow: (x from -1 to 1, how far below the
#           hairline the tip falls). Keep the longest tip about 5: any lower
#           and it sits on the eyes.
#   sweep   sideways lean of the fringe tips
#   side    how far the side locks fall, from the head's centre
#   back    how far the hair falls behind (> side: a separate back mass,
#           which needs hair(layer="back") before the body)
#   spikes  (count, height) of spikes across the crown
#   extra   "pony", "twin", "bun", "topknot", "ahoge"
# ---------------------------------------------------------------------------

HAIR_STYLES = {
    "short": dict(vol=1.5, fringe=((-0.74, 3.8), (-0.3, 5.0), (0.14, 5.2), (0.56, 4.4), (0.88, 2.8)),
                  sweep=0.9, side=3.2, back=6.5),
    "spiky": dict(vol=1.8, fringe=((-0.74, 4.2), (-0.3, 5.3), (0.12, 4.6), (0.54, 5.3), (0.88, 3.4)),
                  sweep=0.6, side=2.4, back=6.0, spikes=(7, 3.4)),
    "swept": dict(vol=1.7, fringe=((-0.8, 2.4), (-0.4, 3.6), (0.02, 4.6), (0.42, 5.2), (0.84, 4.4)),
                  sweep=2.0, side=3.4, back=6.5),
    "bob": dict(vol=2.0, fringe=((-0.78, 4.4), (-0.38, 5.0), (0.0, 5.2), (0.38, 5.0), (0.78, 4.4)),
                sweep=0.0, side=8.6, back=8.6, lock=3.6),
    "long": dict(vol=1.9, fringe=((-0.72, 4.0), (-0.28, 5.2), (0.18, 5.0), (0.62, 4.2)),
                 sweep=0.7, side=12.5, back=19.0, lock=3.4),
    "wild": dict(vol=2.3, fringe=((-0.76, 4.4), (-0.34, 5.4), (0.08, 4.4), (0.5, 5.4), (0.86, 3.8)),
                 sweep=0.4, side=5.5, back=9.0, spikes=(8, 3.8)),
    "crop": dict(vol=0.8, fringe=((-0.6, 1.6), (-0.2, 2.0), (0.2, 2.0), (0.6, 1.6)),
                 sweep=0.0, side=0.6, back=5.5),
    "curly": dict(vol=2.4, fringe=((-0.76, 3.8), (-0.36, 4.6), (0.04, 4.0), (0.44, 4.6), (0.82, 3.6)),
                  sweep=0.0, side=6.0, back=8.0, spikes=(9, 1.6), lock=3.8),
}


def _hair_style(style):
    if isinstance(style, dict):
        return style
    return HAIR_STYLES[style]


def _crown(ocx, ocy, orx, ory, a0, a1, spikes, lean=0.0):
    """Outer arc of the hair from angle a0 to a1, spiked between 196 and 344."""
    if not spikes or not spikes[0]:
        return arc_pts(ocx, ocy, orx, ory, a0, a1)
    count, height = spikes
    pts = []
    lo, hi = max(a0, 196.0), min(a1, 344.0)
    if lo > a0:
        pts += arc_pts(ocx, ocy, orx, ory, a0, lo)[:-1]
    n = count * 2
    for i in range(n + 1):
        a = lo + (hi - lo) * i / float(n)
        rad = math.radians(a)
        if i % 2:
            prof = 0.55 + 0.45 * math.sin(math.radians((a - lo) / (hi - lo) * 180.0))
            k = 1.0 + height * prof / ((orx + ory) / 2.0)
            a2 = math.radians(a + lean * 6.0)
            pts.append((ocx + orx * k * math.cos(a2), ocy + ory * k * math.sin(a2)))
        else:
            pts.append((ocx + orx * math.cos(rad), ocy + ory * math.sin(rad)))
    if hi < a1:
        pts += arc_pts(ocx, ocy, orx, ory, hi, a1)[1:]
    return pts


def _fringe(hx, rx, hl, fringe, sweep, xs=1.0):
    """Fringe points from the right temple to the left, alternating notch and tip."""
    tips = sorted(fringe, key=lambda t: -t[0])
    pts = []
    prev = 1.08
    for (x, drop) in tips:
        nx = (prev + x) / 2.0
        pts.append((hx + nx * (rx - 0.8) * xs, hl + 1.1))
        pts.append((hx + x * (rx - 0.8) * xs + sweep, hl + drop))
        prev = x
    pts.append((hx + (prev - 1.08) / 2.0 * (rx - 0.8) * xs, hl + 1.1))
    return pts


def _hair_front(hx, hy, rx, ry, st):
    v, side = st["vol"], st["side"]
    lw = st.get("lock", 3.2)
    ocx, ocy = hx, hy - v * 0.35
    orx, ory = rx + v, ry + v
    hl = hy - ry * 0.56
    sy = hy + side
    lim = ocy + ory * 0.9
    a = math.degrees(math.asin(max(-0.95, min(0.9, (min(sy, lim) - ocy) / ory))))
    outer = _crown(ocx, ocy, orx, ory, 180.0 - a, 360.0 + a, st.get("spikes"), st.get("sweep", 0.0) * 0.3)
    if sy > lim:
        flare = min(2.2, (sy - lim) * 0.15)
        outer = [(outer[0][0] - flare, sy)] + outer + [(outer[-1][0] + flare, sy)]
    lb, rb = outer[0], outer[-1]
    pts = list(outer)
    # right lock: pointed tip, then up the inner edge hugging the cheek
    pts += [(rb[0] - lw * 0.42, rb[1] + 1.5), (rb[0] - lw, rb[1] - 0.4)]
    if sy > hy + 2.5:
        pts.append((hx + rx - 1.6, hy + 1.6))
    pts.append((hx + rx - 1.0, hl + 2.8))
    pts += _fringe(hx, rx, hl, st["fringe"], st.get("sweep", 0.0))
    pts.append((hx - rx + 1.0, hl + 2.8))
    if sy > hy + 2.5:
        pts.append((hx - rx + 1.6, hy + 1.6))
    pts += [(lb[0] + lw, lb[1] - 0.4), (lb[0] + lw * 0.42, lb[1] + 1.5)]
    return pts, hl


def _hair_side(hx, hy, rx, ry, st, d):
    """Profile, facing d. Built facing right in local u (toward the face), mirrored."""
    v, side = st["vol"], st["side"]
    ocu, ocy = -0.9, hy - v * 0.35
    orx, ory = rx + v, ry + v
    hl = hy - ry * 0.56
    nape = max(5.5, min(side, 9.0)) if side < 8 else side
    pts = []
    # front of the fringe, then over the crown to the back of the head
    pts.append((rx + 0.1, hl + 3.2))
    pts.append((rx + v * 0.6, hl - 0.4))
    a_front = -38.0
    crown = _crown(ocu, ocy, orx, ory, 180.0, 360.0 + a_front, st.get("spikes"), 0.0)
    pts += crown[::-1]
    # down the back to the nape
    back_x = ocu - orx
    if nape > 8:
        flare = min(2.0, nape * 0.08)
        pts += [(back_x - 0.2, hy + nape * 0.4), (back_x - flare, hy + nape)]
        pts += [(back_x + 2.8, hy + nape + 1.2), (back_x + 4.6, hy + nape - 0.4),
                (0.4, hy + nape + 0.6), (1.6, hy + nape * 0.5)]
        pts.append((2.2, hl + 3.0))
    else:
        pts += [(back_x + 0.2, hy + nape * 0.55), (back_x + 1.4, hy + nape + 0.6),
                (-rx * 0.52, hy + nape - 0.8), (-rx * 0.36, hy + nape + 0.2),
                (-rx * 0.24, hy + 1.2), (-2.6, hy - 0.8), (-0.4, hy + 0.2),
                (0.3, hy + 3.8), (1.6, hy + 3.0), (2.4, hl + 3.0)]
    # the fringe from the temple forward
    tips = sorted([t for t in st["fringe"] if t[0] > -0.2], key=lambda t: t[0])
    u_prev = 2.4
    span = rx - 2.4
    for i, (x, drop) in enumerate(tips):
        u = 3.4 + span * (i + 0.8) / (len(tips) + 0.3)
        pts.append(((u_prev + u) / 2.0, hl + 1.2))
        pts.append((u + st.get("sweep", 0.0) * 0.5, hl + drop))
        u_prev = u
    return [(hx + d * u, y) for (u, y) in pts], hl


def _hair_back(hx, hy, rx, ry, st):
    v, side = st["vol"], st["side"]
    ocx, ocy = hx, hy - v * 0.35
    orx, ory = rx + v, ry + v
    nape = max(8.6, min(max(side, st.get("back", 6.5)), 10.0))
    long_ = st.get("back", 6.5) > 10
    lim = ocy + ory * 0.88
    a = math.degrees(math.asin(max(-0.9, min(0.88, (min(hy + nape, lim) - ocy) / ory))))
    outer = _crown(ocx, ocy, orx, ory, 180.0 - a, 360.0 + a, st.get("spikes"))
    bottom_y = hy + (st["back"] if long_ else nape)
    if bottom_y > outer[0][1] + 0.5:
        flare = 2.0 if long_ else 0.6
        outer = [(outer[0][0] - flare, bottom_y)] + outer + [(outer[-1][0] + flare, bottom_y)]
    lb, rb = outer[0], outer[-1]
    pts = list(outer)
    n = 6
    for i in range(1, 2 * n):
        t = i / float(2 * n)
        x = rb[0] + (lb[0] - rb[0]) * t
        pts.append((x, bottom_y + (1.5 if i % 2 else -1.0)))
    return pts


def hair_back_mass(hx, hy, rx, ry, st, d=0):
    """The long hair that falls behind the shoulders, for layer="back"."""
    v = st["vol"]
    b = st["back"]
    if d:
        u0 = -rx * 0.2
        pts = [(u0, hy - ry * 0.4), (-rx - v, hy - 2.0), (-rx - v - 1.6, hy + b * 0.6),
               (-rx - v - 0.6, hy + b), (-rx * 0.6, hy + b + 0.8), (-rx * 0.2, hy + b - 1.0),
               (u0 + 1.0, hy + 6.0)]
        return [(hx + d * u, y) for (u, y) in pts]
    w = rx + v
    pts = [(hx - w + 0.3, hy - 2.0), (hx - w - 1.8, hy + b * 0.7), (hx - w - 1.0, hy + b)]
    for i in range(1, 6):
        t = i / 6.0
        pts.append((hx - w - 1.0 + (2 * w + 2.0) * t, hy + b + (1.2 if i % 2 else -0.5)))
    pts += [(hx + w + 1.0, hy + b), (hx + w + 1.8, hy + b * 0.7), (hx + w - 0.3, hy - 2.0)]
    return pts


def _gloss(draw, pts_fn, color):
    for seg in pts_fn:
        Poly(seg).draw(draw, fill=color)


def _gloss_band(cx, cy, rx, ry, a0, a1, thick, pieces=3):
    """Short tapered strokes along an arc: the shine across a crown of hair."""
    segs = []
    span = (a1 - a0) / float(pieces)
    for i in range(pieces):
        s0 = a0 + span * i + span * 0.12
        s1 = a0 + span * (i + 1) - span * 0.12
        outer = arc_pts(cx, cy, rx + thick * 0.5, ry + thick * 0.5, s0, s1, 6)
        inner = arc_pts(cx, cy, rx - thick * 0.5, ry - thick * 0.5, s1, s0, 6)
        mid = (s0 + s1) / 2.0
        segs.append([arc_pts(cx, cy, rx, ry, s0, s0, 1)[0]] + outer[1:-1] +
                    [arc_pts(cx, cy, rx, ry, s1, s1, 1)[0]] + inner[1:-1])
    return segs


def rig_hair(r, draw, color, style="short", layer="front", hat=False, skin=None):
    """Draw a hairstyle. `layer="back"` is the long hair behind the body (call it
    before the body); the default front layer is everything else, drawn after
    the face. `hat=True` leaves the crown to whatever is worn on it."""
    st = _hair_style(style)
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    tone = shade(color)
    shine = lit(color, 1.6)
    if layer == "back":
        if st.get("back", 0) > 10 and not r.back:
            cel(draw, Poly(hair_back_mass(hx, hy, rx, ry, st, r.d)), color, sh=(1.2, 1.2), tone=tone)
        _hair_extra(r, draw, color, st, "back")
        return
    if r.back:
        _hair_extra(r, draw, color, st, "under")
        shape = Poly(_hair_back(hx, hy, rx, ry, st))
        cel(draw, shape, color, sh=(1.1, 1.3), tone=tone)
        if not hat:
            ocy = hy - st["vol"] * 0.35
            clipped(draw, shape, lambda l: _gloss(l, _gloss_band(
                hx, ocy, (rx + st["vol"]) * 0.7, (ry + st["vol"]) * 0.62, 212, 328, 1.4, 3), shine))
        _hair_extra(r, draw, color, st, "over")
        return
    if r.d:
        pts, hl = _hair_side(hx, hy, rx, ry, st, r.d)
    else:
        pts, hl = _hair_front(hx, hy, rx, ry, st)
    shape = Poly(pts)
    _hair_extra(r, draw, color, st, "under")
    # the fringe's shadow on the brow
    if skin is not None:
        clipped(draw, Ell(hx, hy, rx, ry), lambda l: shape.shift(0.3, 1.1).draw(l, fill=shade(skin)))
    cel(draw, shape, color, sh=(1.0, 1.3), tone=tone)
    if not hat:
        ocy = hy - st["vol"] * 0.35
        if r.d:
            cx0 = hx - r.d * 0.9
            a0, a1 = (205, 262) if r.d > 0 else (278, 335)
            band = _gloss_band(cx0, ocy, (rx + st["vol"]) * 0.72, (ry + st["vol"]) * 0.66, a0, a1, 1.4, 2)
        else:
            band = _gloss_band(hx, ocy, (rx + st["vol"]) * 0.72, (ry + st["vol"]) * 0.64, 206, 300, 1.4, 3)
        clipped(draw, shape, lambda l: _gloss(l, band, shine))
    _hair_extra(r, draw, color, st, "over")


def _hair_extra(r, draw, color, st, when):
    extra = st.get("extra")
    if not extra:
        return
    hx, hy, rx, ry = r.hx, r.head_cy, r.head_rx, r.head_ry
    d = r.d
    if extra == "pony":
        if (when == "back" and not r.back) or (when == "over" and r.back):
            if d:
                pts = [(hx - d * (rx * 0.7), hy - ry * 0.45), (hx - d * (rx + 4.2), hy + 1.0),
                       (hx - d * (rx + 3.0), hy + 11.0)]
            elif r.back:
                pts = [(hx, hy - ry * 0.3), (hx + 1.2, hy + 6.0), (hx + 0.4, hy + 15.0)]
            else:
                pts = [(hx + rx * 0.3, hy - ry * 0.7), (hx + rx + 3.4, hy - 1.0), (hx + rx + 2.4, hy + 9.0)]
            cel(draw, Limb(pts, [2.6, 3.2, 0.6]), color, sh=(0.9, 0.9))
    elif extra == "twin":
        if (when == "back" and not r.back) or (when == "over" and r.back):
            sides = (-1, 1) if not d else (-d,)
            for s in sides:
                base_x = hx + s * (rx - 1.0) if not d else hx - d * (rx * 0.5)
                pts = [(base_x, hy - ry * 0.55), (base_x + s * 4.4, hy + 1.0), (base_x + s * 3.0, hy + 12.0)]
                cel(draw, Limb(pts, [2.4, 3.0, 0.6]), color, sh=(0.9, 0.9))
    elif extra in ("bun", "topknot"):
        if when == "under":
            big = extra == "bun"
            bx = hx - d * (rx * 0.35)
            by = hy - ry - (1.4 if big else 2.2)
            cel(draw, Ell(bx, by, 4.2 if big else 2.8, 3.6 if big else 2.6), color, sh=(0.8, 0.9))
    elif extra == "ahoge":
        if when == "over" and not r.back:
            x0 = hx + (0.5 if not d else -d * 1.0)
            stroke_pts = [(x0, hy - ry - 1.0), (x0 + 1.4, hy - ry - 4.0), (x0 + 3.6, hy - ry - 4.6)]
            cel(draw, Limb(stroke_pts, [1.2, 0.9, 0.3]), color, sh=None)


def rig_cape(r, draw, cloth, lining=None, layer=None, length=None, width=1.0):
    """A cloak on the shoulders. Behind the body head-on and in profile
    (layer="under"), in front of it seen from behind (layer="over")."""
    if lining is None:
        lining = shade(cloth, 1.3)
    if layer == "under" and r.back:
        return
    if layer == "over" and not r.back:
        return
    sway = r.ph * 1.2
    bottom = r.base_y - 1.8 if length is None else length
    cx, d = r.cx, r.d
    w = r.sh_w * width
    if d:
        shape = Poly([(cx - d * 1.0, r.sh_y - 2.6), (cx - d * 4.6, r.sh_y - 1.0),
                      (cx - d * (7.6 + abs(sway)), bottom), (cx - d * 1.2, bottom - 1.0),
                      (cx + d * 1.0, r.hip_y - 2)])
        cel(draw, shape, lining, sh=(0.0, 0.0))
        cel(draw, Poly([(cx - d * 1.2, r.sh_y - 2.4), (cx - d * 4.6, r.sh_y - 0.8),
                        (cx - d * (7.6 + abs(sway)), bottom), (cx - d * 5.0, bottom - 0.4)]),
            cloth, sh=(0.8, 0.0))
        return
    if r.back:
        shape = Poly([(cx - w + 0.6, r.sh_y - 2.8), (cx + w - 0.6, r.sh_y - 2.8),
                      (cx + w + 2.2 + sway, bottom), (cx - w - 2.2 + sway, bottom)])
        cel(draw, shape, cloth, sh=(1.8, 0.0),
            regions=[(Poly([(cx - 0.8, r.sh_y), (cx + 0.8, r.sh_y), (cx + 1.4 + sway, bottom),
                            (cx - 1.4 + sway, bottom)]), shade(cloth, 0.8))])
        return
    shape = Poly([(cx - w + 0.4, r.sh_y - 2.8), (cx + w - 0.4, r.sh_y - 2.8),
                  (cx + w + 3.0 + sway, bottom), (cx - w - 3.0 + sway, bottom)])
    cel(draw, shape, lining, sh=(1.2, 0.0))


def hood_shape(r, peak=0.0, drape=True, snug=1.5):
    hx, hy, rx, ry, d = r.hx, r.head_cy, r.head_rx, r.head_ry, r.d
    ocx, ocy = hx - d * 0.5, hy - 0.4
    orx, ory = rx + snug, ry + snug + 0.1
    pts = arc_pts(ocx, ocy, orx, ory, 142, 398, 64)
    if peak:
        # a pointed tip at the crown, swept back: points near the top are
        # pulled outward, most at the top, so the tip stays one smooth shape
        top_a = 270.0 - d * 22.0
        out = []
        for (x, y) in pts:
            a = math.degrees(math.atan2((y - ocy) / ory, (x - ocx) / orx)) % 360.0
            t = max(0.0, 1.0 - abs(a - top_a) / 34.0) ** 1.6
            out.append((x + (x - ocx) / orx * peak * t * 0.3 - d * peak * t * 0.55,
                        y + (y - ocy) / ory * peak * t))
        pts = out
    if drape:
        sw = r.sh_w + 1.9
        if d:
            pts += [(hx + d * 5.4, r.sh_y + 0.6), (hx - d * 2.0, r.sh_y + 2.4),
                    (hx - d * 7.6, r.sh_y + 1.0)]
        else:
            pts += [(hx + sw, r.sh_y + 1.4), (hx + 3.0, r.sh_y + 2.6), (hx - 3.0, r.sh_y + 2.6),
                    (hx - sw, r.sh_y + 1.4)]
    return Poly(pts)


def hood_opening(r, scale=1.0):
    hx, hy, rx, ry, d = r.hx, r.head_cy, r.head_rx, r.head_ry, r.d
    if d:
        return Ell(hx + d * 2.8, hy + 2.0, rx * 0.74 * scale, ry * 0.84 * scale)
    return Ell(hx, hy + 2.0, rx * 0.9 * scale, ry * 0.84 * scale)


def rig_hood(r, draw, cloth, dark=None, glow=None, depth=0.75, peak=0.0, drape=True,
             lining=None, opening=1.0):
    """A hood worn up, framing the face (or swallowing it, with `glow` eyes).

    Snug -- a MapleStory hood sits a unit and a half off the skull -- and
    drawn after the face: it is laid on its own layer with the face opening
    cut out, so the eyes and fringe show through.
    """
    if dark is None:
        dark = shade(cloth, 2.6)
    if lining is None:
        lining = shade(cloth, 1.4)
    shape = hood_shape(r, peak, drape)
    layer = draw.sub()
    cel(layer, shape, cloth, sh=(1.5, 1.2), line=False)
    hole = hood_opening(r, opening)
    if not r.back:
        hole.scaled(1.12, hole.pts[0][0] * 0 + (r.hx + r.d * 2.8 if r.d else r.hx), r.head_cy + 2.0).draw(
            layer, fill=lining)
        hole.draw(layer, fill=CLEAR)
    shape.draw(layer, fill=None, outline=ink(cloth), width=1.0)
    if not r.back:
        hole.draw(layer, fill=None, outline=ink(lining), width=0.8)
    if glow is not None and not r.back:
        cel(draw, hole, dark, sh=None, line=False)
    draw.merge(layer)
    if glow is not None and not r.back:
        e1, e2, ey, _, _ = face_anchor(r)
        d = r.d
        for side, ex in (((-1, e1), (1, e2)) if not d else ((-d, e1), (d, e2))):
            ms_eye(draw, ex + d * 0.6, ey, 3.2 if not d else 2.6, 3.0, glow=glow)


# ---------------------------------------------------------------------------
# Gear
#
# Weapons and shields are authored once in a local frame -- +x along the
# object from where the hand grips it, +y across it -- and placed with
# `xform`, so the same sword can hang at a hip, point at the ground or be
# raised overhead. Oversized on purpose: in MapleStory the weapon is often as
# tall as its wielder, and that is a lot of the charm.
# ---------------------------------------------------------------------------

STEEL = (216, 222, 234)
LEATHER = (152, 98, 60)
GOLD = (248, 200, 80)
WOOD = (164, 108, 64)


def xform(pts, ox, oy, ang, k=1.0, flip=1.0):
    """Local points -> draw space: flip across the local x axis, scale by k,
    rotate by `ang` degrees (0 = +x, 90 = straight down), move to (ox, oy)."""
    a = math.radians(ang)
    c, s = math.cos(a), math.sin(a)
    return [(ox + (x * c - y * flip * s) * k, oy + (x * s + y * flip * c) * k) for (x, y) in pts]


def blade(draw, x, y, ang, length=13.0, width=1.7, color=STEEL, hilt=GOLD, grip=LEATHER,
          guard=3.0, curve=0.0, k=1.0, flip=1.0, pommel=True):
    """A sword gripped at (x, y), pointing along `ang`. `curve` bends it (a katana)."""
    cel(draw, Limb(xform([(-3.6, 0.0), (-0.2, 0.0)], x, y, ang, k, flip), [0.95 * k, 0.95 * k]), grip,
        sh=None)
    if pommel:
        px, py = xform([(-4.0, 0.0)], x, y, ang, k, flip)[0]
        cel(draw, Ell(px, py, 1.25 * k, 1.25 * k), hilt, sh=None)
    n = 8
    top, mid, bot = [], [], []
    for i in range(n + 1):
        t = i / float(n)
        u = 0.5 + (length - 0.5) * t
        v = curve * t * t * length * 0.12
        w = width * (1.0 - 0.18 * t)
        top.append((u, v - w))
        mid.append((u, v))
        bot.append((u, v + w))
    tip = (length + width * 1.3, curve * length * 0.12)
    body = Poly(xform(top + [tip] + bot[::-1], x, y, ang, k, flip))
    lower = Poly(xform(mid + [tip] + bot[::-1], x, y, ang, k, flip))
    cel(draw, body, color, sh=None, regions=[(lower, shade(color, 0.9))])
    edge = xform([(1.6, -width * 0.35), (length - 1.2, curve * length * 0.1 - width * 0.3)], x, y, ang, k, flip)
    stroke(draw, edge, 0.55 * k, lit(color, 1.4))
    gpts = [(-0.5, -guard), (0.7, -guard), (0.7, guard), (-0.5, guard)]
    cel(draw, Poly(xform(gpts, x, y, ang, k, flip)), hilt, sh=None)


def spear(draw, x0, y0, x1, y1, shaft=WOOD, head=STEEL, head_len=5.5, head_w=2.2, band=GOLD,
          width=1.0):
    """A shaft from (x0, y0) to a leaf-shaped head beyond (x1, y1)."""
    cel(draw, Limb([(x0, y0), (x1, y1)], [width, width]), shaft, sh=None)
    ang = math.degrees(math.atan2(y1 - y0, x1 - x0))
    leaf = [(0.0, -head_w * 0.55), (head_len * 0.45, -head_w), (head_len, 0.0),
            (head_len * 0.45, head_w), (0.0, head_w * 0.55)]
    cel(draw, Poly(xform(leaf, x1, y1, ang)), head, sh=None,
        regions=[(Poly(xform([(0.0, 0.0), (head_len, 0.0), (head_len * 0.45, head_w),
                              (0.0, head_w * 0.55)], x1, y1, ang)), shade(head, 0.9))])
    if band is not None:
        cel(draw, Poly(xform([(-1.4, -1.35 * width), (0.2, -1.35 * width), (0.2, 1.35 * width),
                              (-1.4, 1.35 * width)], x1, y1, ang)), band, sh=None)


def bow(draw, x, y, d, height=22.0, color=WOOD, string=(236, 236, 226), side=1):
    """A longbow held upright at (x, y), belly toward the facing."""
    k = side if not d else d
    top, bot = (x - k * 1.0, y - height * 0.55), (x - k * 1.0, y + height * 0.45)
    mid = (x + k * 3.6, y)
    pts = [top, (x + k * 2.6, y - height * 0.32), mid, (x + k * 2.6, y + height * 0.28), bot]
    cel(draw, Limb(pts, [0.5, 1.0, 1.2, 1.0, 0.5]), color, sh=None)
    stroke(draw, [top, (x - k * 0.6, y), bot], 0.4, string)


def round_shield(draw, x, y, rx, ry, face, rim=None, boss=None, emblem=None, edge_on=False):
    """A round shield seen face-on (or, with `edge_on`, as a narrow oval)."""
    rim = rim or shade(face, 1.4)
    if edge_on:
        cel(draw, Ell(x, y, rx, ry), rim, sh=(0.0, 0.8))
        cel(draw, Ell(x, y, rx * 0.55, ry * 0.86), face, sh=(0.3, 0.8))
        return
    cel(draw, Ell(x, y, rx, ry), rim, sh=(0.8, 0.8))
    cel(draw, Ell(x, y, rx - 1.3, ry - 1.3), face, sh=(1.1, 1.1), line=False)
    if emblem is not None:
        emblem(draw, x, y)
    if boss is not None:
        cel(draw, Ell(x, y, rx * 0.3, ry * 0.3), boss, sh=(0.5, 0.5), hi=(0.4, 0.4))


def gem(draw, x, y, rad, color):
    cel(draw, Poly([(x, y - rad), (x + rad * 0.85, y), (x, y + rad), (x - rad * 0.85, y)]), color,
        sh=None, regions=[(Poly([(x, y), (x + rad, y), (x, y + rad * 1.2)]), shade(color, 1.0))])
    Ell(x - rad * 0.25, y - rad * 0.35, rad * 0.22, rad * 0.22).draw(draw, fill=(255, 255, 255))


# ---------------------------------------------------------------------------
# Effects
#
# Fire, cloud, ice, lightning and leaves recur across the roster (a fireball,
# a living flame, a phoenix, a storm cloud, a crystal crown...), so each is one
# authored shape in flat cel tones rather than something every character
# improvises.
# ---------------------------------------------------------------------------

FIRE = ((236, 72, 44), (255, 150, 52), (255, 232, 120))
ICE = (178, 226, 250)

# Right half of a stylised flame, bottom (v=0) to tip (v=1), in half-widths.
_FLAME_R = ((0.0, 0.0), (0.6, 0.05), (0.95, 0.24), (0.98, 0.44), (0.74, 0.6), (0.84, 0.78),
            (0.44, 0.66), (0.3, 0.84), (0.04, 1.0))
_FLAME_L = ((-0.3, 0.78), (-0.58, 0.9), (-0.62, 0.62), (-0.96, 0.45), (-0.95, 0.24), (-0.6, 0.05))


def flame_pts(x, y, w, h, sway=0.0, flip=1.0):
    out = []
    for (u, v) in _FLAME_R + _FLAME_L:
        out.append((x + u * flip * w * 0.5 + sway * v * v, y - v * h))
    return out


def flame(draw, x, y, w, h, colors=FIRE, sway=0.0, flip=1.0, line=True):
    """A cel-shaded flame standing on (x, y): an outer tone, a hotter middle,
    and a pale core, each the same silhouette smaller and lower."""
    outer, mid, core = colors
    cel(draw, Poly(flame_pts(x, y, w, h, sway, flip)), outer, sh=None, line=line)
    Poly(flame_pts(x, y - h * 0.02, w * 0.64, h * 0.66, sway * 0.7, -flip)).draw(draw, fill=mid)
    Poly(flame_pts(x, y - h * 0.04, w * 0.34, h * 0.36, sway * 0.4, flip)).draw(draw, fill=core)


def cloud(draw, x, y, w, h, color, sh=(1.0, 1.2), tone=None, bumps=None):
    """A cumulus puff centred on (x, y): a flat-ish base under round bumps."""
    parts = [RRect(x - w * 0.5, y - h * 0.1, x + w * 0.5, y + h * 0.5, h * 0.3)]
    for (u, v, k) in (bumps or ((-0.3, 0.05, 0.3), (0.02, -0.12, 0.38), (0.32, 0.04, 0.28))):
        parts.append(Ell(x + u * w, y + v * h, k * w, k * w * 0.95))
    blob(draw, parts, color, sh=sh, tone=tone)


def crystal(draw, x, y, ang, length, width, color=ICE, line=True):
    """An ice shard or gem crystal: a long hexagon from (x, y) along `ang`,
    one face lit and one in shade."""
    w = width * 0.5
    local = [(0.0, -w * 0.7), (length * 0.72, -w), (length, 0.0), (length * 0.72, w), (0.0, w * 0.7)]
    body = Poly(xform(local, x, y, ang))
    dark = Poly(xform([(0.0, 0.0), (length, 0.0), (length * 0.72, w), (0.0, w * 0.7)], x, y, ang))
    cel(draw, body, color, sh=None, regions=[(dark, shade(color, 0.9))], line=line)
    stroke(draw, xform([(length * 0.1, -w * 0.35), (length * 0.66, -w * 0.55)], x, y, ang), 0.5, lit(color, 1.6))


def bolt(draw, pts, width, color=(255, 238, 120), line=True):
    """A lightning bolt through the given zigzag points, tapering to a point."""
    n = len(pts)
    widths = [width * (1.0 - 0.75 * i / max(1, n - 1)) for i in range(n)]
    cel(draw, Limb(pts, widths, cap=False), color, sh=None, line=line,
        line_color=shade(color, 2.4))


def leaf(draw, x, y, ang, length, width, color, vein=True):
    """A pointed leaf from its stem at (x, y) along `ang`."""
    w = width * 0.5
    local = arc_pts(length * 0.5, 0.0, length * 0.5, w, 180, 360, 8) + \
        arc_pts(length * 0.5, 0.0, length * 0.5, w, 0, 180, 8)[1:-1]
    pts = xform(local, x, y, ang)
    cel(draw, Poly(pts), color, sh=None,
        regions=[(Poly(xform([(0.0, 0.0), (length, 0.0)] + arc_pts(length * 0.5, 0.0, length * 0.5, w, 0, 180, 8)[1:-1],
                             x, y, ang)), shade(color, 0.8))])
    if vein:
        stroke(draw, xform([(length * 0.1, 0.0), (length * 0.8, 0.0)], x, y, ang), 0.4, shade(color, 1.6))


# ---------------------------------------------------------------------------
# Creatures
#
# The non-humanoids (beasts, monsters, mythological animals) have bodies of
# their own, but share a frame layout, a way of setting a pair of eyes, and
# the trot every quadruped walks with.
# ---------------------------------------------------------------------------

TOOTH = (252, 250, 240)


def creature_setup(ox, oy, direction, frame, bob=None):
    d = {DOWN: 0, UP: 0, LEFT: -1, RIGHT: 1}[direction]
    back = direction == UP
    b = [0, -1, 0, -1][frame] if bob is None else bob[frame]
    ph = [0, 1, 0, -1][frame]
    return d, back, oy + 54 + b, ox + 32, ph


def rot_pts(pts, cx, cy, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    return [(cx + (x - cx) * c - (y - cy) * s, cy + (x - cx) * s + (y - cy) * c) for (x, y) in pts]


def eye_pair(draw, x1, x2, y, d, iris, mood="sharp", skin=(240, 240, 240), w=4.0, h=5.0, lash=(40, 30, 40)):
    """A pair of MapleStory eyes for a creature: head-on both, in profile the
    far one narrowed and nearer the snout."""
    if d:
        ms_eye(draw, x2, y, w * 0.7, h * 0.94, iris, (float(d), 0.0), mood, skin=skin, side=d, lash=lash)
        ms_eye(draw, x1, y, w, h, iris, (float(d), 0.0), mood, skin=skin, side=-d, lash=lash)
    else:
        ms_eye(draw, x1, y, w, h, iris, (0.0, 0.0), mood, skin=skin, side=-1, lash=lash)
        ms_eye(draw, x2, y, w, h, iris, (0.0, 0.0), mood, skin=skin, side=1, lash=lash)


def draw_paw(draw, x, y, color, d=0, claws=True, r=2.4):
    cel(draw, Ell(x + d * 0.8, y, r, r * 0.7), color, sh=(0.0, 0.6))
    if claws:
        for k in ((-1, 0, 1) if not d else (0, 1)):
            cx = x + (k * 1.2 if not d else d * (r + 0.4 - k * 0.6))
            Poly([(cx - 0.45, y + r * 0.45), (cx + 0.45, y + r * 0.45), (cx + (d * 0.8 if d else 0), y + r * 0.45 + 1.4)]).draw(
                draw, fill=TOOTH)


def quad_legs(draw, cx, base, d, ph, color, front_x, back_x, top_y, far=True, paw=None, w=2.0):
    # `paw` here is the paw colour; the drawing function is draw_paw
    """Four legs in profile, trotting: diagonal pairs move together."""
    paw = paw or color
    legs = [(front_x, 1, False), (back_x, -1, False), (front_x, -1, True), (back_x, 1, True)]
    for (lx, phase, near) in ([l for l in legs if not l[2]] if far else []) + [l for l in legs if l[2]]:
        swing = ph * phase * 2.4
        lift = 1.0 if ph * phase < 0 else 0.0
        col = color if near else shade(color, 0.6)
        x0 = cx + d * lx
        x1 = x0 + d * swing
        cel(draw, Limb([(x0, top_y), (x0 + d * swing * 0.4, (top_y + base) / 2.0), (x1, base - 1.6 - lift)], [w, w * 0.9, w * 0.8]),
            col, sh=(0.6, 0.0))
        draw_paw(draw, x1, base - 1.0 - lift, paw if near else shade(paw, 0.6), d, claws=near, r=w * 1.1)


# ---------------------------------------------------------------------------
# Detail pass
#
# MapleStory shading lives in the art, not in a filter: `cel` authors every
# shadow as one flat tone. What is left for image space is the silhouette
# line. Sprites stand on terrain of every brightness, so the outermost edge
# gets one more ring, a shade darker than the ink it sits against -- still
# its own hue, never a black trace.
#
# The previous pass (a soft bevel, rim light, occlusion ramp and a fixed grain
# field) was painterly by design and fought flat colour everywhere: it put a
# gradient on every fill and noise on every flat area.
# ---------------------------------------------------------------------------

CONTOUR_DARKEN = 0.62
CONTOUR_ALPHA = 225


def add_contour(frame, darken=CONTOUR_DARKEN, alpha=CONTOUR_ALPHA, scale=1):
    """A one-pixel ring outside the silhouette, in a darker version of the edge
    it borders. Goes *behind* the frame so the anti-aliased edge survives."""
    a = frame.getchannel("A")
    solid = a.point(lambda v: 255 if v > 100 else 0)
    ring = ImageChops.subtract(solid.filter(ImageFilter.MaxFilter(2 * scale + 1)), solid)
    rgb = frame.convert("RGB")
    base = Image.composite(rgb, Image.new("RGB", frame.size, (255, 255, 255)), solid)
    edge = base.filter(ImageFilter.MinFilter(2 * scale + 1))
    edge = edge.point(lambda v: int(v * darken))
    backdrop = Image.new("RGBA", frame.size, CLEAR)
    backdrop.paste(edge.convert("RGBA"), (0, 0), ring.point(lambda v: v * alpha // 255))
    return Image.alpha_composite(backdrop, frame)


def finish_frame(frame, scale=1):
    """Apply the detail pass to one rendered frame.

    `scale` is the frame's size relative to FRAME_SIZE (1 for sprite sheets,
    larger for the app icon), so the ring stays proportional.
    """
    return add_contour(frame, scale=scale)


def render_frame(draw_func, direction, frame_idx):
    """One frame, supersampled, resampled to FRAME_SIZE and finished."""
    scale = RENDER_SIZE // DRAW_SIZE
    frame_img = Image.new("RGBA", (RENDER_SIZE, RENDER_SIZE), CLEAR)
    frame_draw = ScaledDraw(ImageDraw.Draw(frame_img), scale, frame_img)
    draw_func(frame_draw, 0, 0, direction, frame_idx)
    return finish_frame(frame_img.resize((FRAME_SIZE, FRAME_SIZE), Image.LANCZOS))


def render_sheet(draw_func):
    img = Image.new("RGBA", (IMG_W, IMG_H), CLEAR)
    for direction in range(ROWS):
        for frame_idx in range(COLS):
            img.paste(render_frame(draw_func, direction, frame_idx),
                      (frame_idx * FRAME_SIZE, direction * FRAME_SIZE))
    return img


def generate_character(name, draw_func):
    """Generate a character sprite sheet at sprites/<name>.png.

    draw_func(draw, ox, oy, direction, frame) authors in the DRAW_SIZE (64px)
    space. Each frame is drawn on its own canvas, so nothing bleeds between
    cells, rendered supersampled at RENDER_SIZE through ScaledDraw, resampled
    down to FRAME_SIZE, and finished.
    """
    img = render_sheet(draw_func)
    # Quantizing to a 255-colour palette (alpha carried per entry in tRNS)
    # keeps the sheets small and is indistinguishable at the size a sprite is
    # ever displayed. Both loaders expand it back to RGBA:
    # stbi_load_from_memory(..., 4) on the GL side, javafx.scene.image.Image on
    # the UI side.
    path = f"sprites/{name}.png"
    quantized = img.quantize(colors=255, method=Image.FASTOCTREE)
    # The octree splits on alpha as well as colour, so interior pixels come back
    # at 252-254 rather than solid. Snap the palette's near-extreme alphas so the
    # body stays fully opaque and cleared pixels stay fully clear; only the
    # genuinely anti-aliased edge keeps a partial value.
    entries = bytearray(quantized.palette.palette)
    for i in range(3, len(entries), 4):
        if entries[i] >= 250:
            entries[i] = 255
        elif entries[i] <= 5:
            entries[i] = 0
    quantized.putpalette(bytes(entries), "RGBA")
    quantized.save(path, optimize=True)
    print(f"Generated {path} ({IMG_W}x{IMG_H})")
