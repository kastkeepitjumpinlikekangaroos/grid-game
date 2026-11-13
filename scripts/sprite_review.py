#!/usr/bin/env python3
"""Close-up review sheets for character sprites, drawn from the generators as they are now.

sprite_gallery.py shows the roster side by side from the sheets in sprites/. This is the
other half of the loop: one character at a time, every frame, big enough to see a stray
line, and at the size and on the grounds the game shows it, before and after a change.
It draws from the draw functions themselves, so nothing has to be written to sprites/
to look at an edit.

    python3 scripts/sprite_review.py OUT --chars wolf,bear            # close-ups and in-game views
    python3 scripts/sprite_review.py OUT --chars wolf --against sprites   # and before/after pairs
    python3 scripts/sprite_review.py OUT --lint                       # the measurable faults, all 112
    python3 scripts/sprite_review.py OUT --lineup grass --against sprites   # the roster, before | after
    python3 scripts/sprite_review.py OUT --chars wolf --pages compare --against sprites   # one page only
    python3 scripts/sprite_review.py OUT --chars wolf --write sprites # write the sheets as well

For each character it writes

    <slug>_close_a.png   the DOWN and UP rows, four frames each, enlarged pixel for pixel
    <slug>_close_b.png   the LEFT and RIGHT rows
    <slug>_game.png      standing and striding in every facing, at the size a HiDPI screen
                         shows it (154px a frame) and an ordinary one (77px), on the five
                         grounds of the maps, in the engine's colours, with its shadow
    <slug>_compare_a.png, _b.png   with --against DIR: DIR's sheet beside the new one, standing
                         and striding, close up and in game (a: DOWN and UP, b: LEFT and RIGHT)

The close-ups are for faults (a line across a seam, a prop that jumps between frames, a
hand that is not holding what it holds); the in-game view is for whether a change reads
at all, and is the one to judge by. The red line across a close-up is the ground (y = 54
to a draw function): soles stand on it.

The engine does not show a sheet's colours as they are. Its grade and lighting lift the dark
end and brighten the middle (a sheet's 40 is shown as about 62, its 128 as 158) and compress the
light end (220 as 225, 255 as 240), on grounds brighter than the tiles' own colours. `ENGINE`
and `GROUNDS` here were measured from the render audit's pictures (2026-11-10): for every
character, the pixels well inside a flat area of its sheet against the same place in the frame
the engine drew, a quadratic through them for each channel (within 4 or 5 levels on average),
on the bright maps and on the space maps' deck, which are graded differently.
"""

import argparse
import importlib
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import sprite_base  # noqa: E402
from sprite_roster import ROSTER  # noqa: E402

FRAME = sprite_base.FRAME_SIZE
UNIT = FRAME / float(sprite_base.DRAW_SIZE)      # sheet pixels per authoring unit
DOWN, UP, LEFT, RIGHT = 0, 1, 2, 3
DIR_NAMES = ("DOWN", "UP", "LEFT", "RIGHT")
HEADROOM = sprite_base.HEADROOM                   # the frame shows y = -HEADROOM .. 64 - HEADROOM
FEET_Y = sprite_base.FOOT_LINE                    # the soles, in the frame's own units (60 of 64)
TILE_HALF_W = 20.0 * FRAME / 48.0                 # CharacterRosterTest: 20 of the 48 it is drawn at

# What the engine makes of a sheet's colour: for each channel, out = a + b x + c x^2 with x the
# sheet's value from 0 to 1. One set for the bright maps (sky, sea, snow) and one for the space maps.
# Not a straight line: a line fitted through every pixel of a frame against the engine's picture of
# it (the first measurement here) came out far flatter than the truth, 0.58 x + 77, because the two
# pictures never line up to the pixel and every stray pair pulls the slope toward nothing.
ENGINE = {
    "bright": ((8.74, 364.18, -131.10), (13.73, 341.23, -114.65), (17.06, 300.72, -87.06)),
    "deck": ((14.09, 324.25, -100.04), (19.29, 296.29, -80.07), (24.04, 241.81, -45.69)),
}
# The grounds a character stands on, as the engine shows them.
GROUNDS = (
    ("deck", (61, 115, 110)),      # the four space maps' circuit
    ("grass", (138, 197, 92)),     # the Meadow, the Lagoon's middle
    ("sand", (218, 210, 166)),     # the Lagoon (the Meadow's path is close: 212, 192, 140)
    ("planks", (197, 151, 103)),   # the Lagoon's boardwalks
    ("snow", (216, 215, 212)),     # the Snowglobe (its ice: 194, 211, 212)
)
CLOSE_BG = (112, 132, 112)

ORIGINALS = (
    ("character", "generate_spaceman", "draw_spaceman"),
    ("gladiator", "generate_gladiator", "draw_gladiator"),
    ("wraith", "generate_wraith", "draw_wraith"),
    ("wizard", "generate_wizard", "draw_wizard"),
    ("tidecaller", "generate_tidecaller", "draw_tidecaller"),
    ("soldier", "generate_soldier", "draw_soldier"),
    ("raptor", "generate_raptor", "draw_raptor"),
    ("assassin", "generate_assassin", "draw_assassin"),
    ("warden", "generate_warden", "draw_warden"),
    ("samurai", "generate_samurai", "draw_samurai"),
    ("plaguedoctor", "generate_plaguedoctor", "draw_plaguedoctor"),
    ("vampire", "generate_vampire", "draw_vampire"),
)
CATEGORIES = (
    ("generate_elementals", "ELEMENTAL_DRAW_FUNCTIONS"),
    ("generate_undead", "UNDEAD_DRAW_FUNCTIONS"),
    ("generate_medieval", "MEDIEVAL_DRAW_FUNCTIONS"),
    ("generate_scifi", "SCIFI_DRAW_FUNCTIONS"),
    ("generate_beasts", "BEAST_DRAW_FUNCTIONS"),
    ("generate_mythological", "MYTHOLOGICAL_DRAW_FUNCTIONS"),
    ("generate_specialists", "SPECIALIST_DRAW_FUNCTIONS"),
)


def draw_functions(only=None):
    """slug -> (draw function, the module it lives in), importing only what `only` needs."""
    out = {}
    for slug, module, fn in ORIGINALS:
        if only is None or slug in only:
            out[slug] = (getattr(importlib.import_module(module), fn), module)
    if only is None or any(s not in out for s in only):
        for module, table in CATEGORIES:
            for slug, fn in getattr(importlib.import_module(module), table).items():
                if only is None or slug in only:
                    out[slug] = (fn, module)
    return out


def resolve(names):
    """Slugs for a comma-separated list of slugs or display names (Spaceman is `character`)."""
    by_name = {n.lower(): s for n, s in ROSTER}
    slugs = {s for _, s in ROSTER}
    out = []
    for raw in names.split(","):
        key = raw.strip().lower().replace(" ", "")
        slug = key if key in slugs else by_name.get(key)
        if slug is None:
            sys.exit("no character called %r" % raw)
        out.append(slug)
    return out


def quantized(sheet):
    """The sheet as generate_character saves it: its palette is part of how it looks."""
    q = sheet.quantize(colors=255, method=Image.FASTOCTREE)
    entries = bytearray(q.palette.palette)
    for i in range(3, len(entries), 4):
        if entries[i] >= 250:
            entries[i] = 255
        elif entries[i] <= 5:
            entries[i] = 0
    q.putpalette(bytes(entries), "RGBA")
    return q


def render(fn):
    return quantized(sprite_base.render_sheet(fn)).convert("RGBA")


def frame(sheet, direction, idx):
    return sheet.crop((idx * FRAME, direction * FRAME, (idx + 1) * FRAME, (direction + 1) * FRAME))


def _font(size):
    for path in ("/System/Library/Fonts/Supplemental/Arial Bold.ttf",
                 "/System/Library/Fonts/Helvetica.ttc",
                 "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"):
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except OSError:
                pass
    return ImageFont.load_default()


def engine_graded(img, deck=False):
    """A frame in the colours the engine shows it in (on the space maps' deck, or anywhere else)."""
    r, g, b, a = img.split()
    out = []
    for ch, (c0, c1, c2) in zip((r, g, b), ENGINE["deck" if deck else "bright"]):
        lut = [int(min(255, max(0, round(c0 + c1 * (v / 255.0) + c2 * (v / 255.0) ** 2)))) for v in range(256)]
        out.append(ch.point(lut))
    return Image.merge("RGBA", out + [a])


def union_box(sheets, pad=3):
    """One box round everything any frame of these sheets draws, the same for every frame,
    so a bob or a prop that moves shows as movement and not as a different crop."""
    x0, y0, x1, y1 = FRAME, FRAME, 0, 0
    for sheet in sheets:
        for d in range(4):
            for i in range(4):
                box = frame(sheet, d, i).getchannel("A").point(lambda v: 255 if v > 16 else 0).getbbox()
                if box:
                    x0, y0, x1, y1 = min(x0, box[0]), min(y0, box[1]), max(x1, box[2]), max(y1, box[3])
    if x1 <= x0:
        return (0, 0, FRAME, FRAME)
    # keep the ground line and the frame's middle in the picture
    y1 = max(y1, int(FEET_Y * UNIT) + 2)
    return (max(0, x0 - pad), max(0, y0 - pad), min(FRAME, x1 + pad), min(FRAME, y1 + pad))


def _zoom(box, cols, rows, limit=1500):
    w, h = box[2] - box[0], box[3] - box[1]
    return max(2, min(limit // (cols * w), limit // (rows * h), 6))


def _close_cell(fr, box, k, bg=CLOSE_BG):
    crop = fr.crop(box)
    cell = Image.new("RGBA", (crop.width * k, crop.height * k), bg + (255,))
    cell.alpha_composite(crop.resize(cell.size, Image.NEAREST))
    d = ImageDraw.Draw(cell)
    gy = int((FEET_Y * UNIT - box[1]) * k)
    d.line([(0, gy), (cell.width, gy)], fill=(210, 60, 60, 255), width=1)
    mx = int((FRAME / 2.0 - box[0]) * k)
    d.line([(mx, 0), (mx, 6)], fill=(210, 60, 60, 255), width=1)
    return cell


def close_pages(slug, sheet, out_dir):
    box = union_box([sheet])
    k = _zoom(box, 4, 2)
    paths = []
    for tag, dirs in (("a", (DOWN, UP)), ("b", (LEFT, RIGHT))):
        cw, ch = (box[2] - box[0]) * k, (box[3] - box[1]) * k
        page = Image.new("RGBA", (4 * cw + 5, 2 * (ch + 16) + 1), (20, 20, 24, 255))
        d = ImageDraw.Draw(page)
        for r, direction in enumerate(dirs):
            for i in range(4):
                page.alpha_composite(_close_cell(frame(sheet, direction, i), box, k),
                                     (1 + i * (cw + 1), 16 + r * (ch + 16)))
                d.text((4 + i * (cw + 1), 2 + r * (ch + 16)),
                       "%s %s  frame %d%s" % (slug, DIR_NAMES[direction], i, "  (idle)" if i == 0 else ""),
                       font=_font(11), fill=(230, 230, 235))
        path = os.path.join(out_dir, "%s_close_%s.png" % (slug, tag))
        page.convert("RGB").save(path)
        paths.append(path)
    return paths


GROUND_PAD = 6.0 / 48.0     # ground shown below the frame, so the shadow is not cut off


def _shadow(size):
    """The engine's shadow under a player (PlayerPainter.drawShadow): three soft ovals on the
    ground, the widest 56 units across, a little down and right of the feet."""
    k = size / 48.0
    h = int(round(size * (1 + GROUND_PAD)))
    img = Image.new("RGBA", (size, h), (0, 0, 0, 0))
    cx, cy = size / 2.0 + 2 * k, FEET_Y / 64.0 * size + 1 * k
    for rx, ry, a in ((28, 11, 0.18), (22, 9, 0.32), (14, 6, 0.45)):
        # fillOvalSoft: full strength only at the very middle, fading to nothing at the rim, so
        # each oval is drawn at half its size and blurred out to the rest, at 0.6 of its strength
        layer = Image.new("L", (size, h), 0)
        ImageDraw.Draw(layer).ellipse([cx - rx * k * 0.5, cy - ry * k * 0.5, cx + rx * k * 0.5,
                                       cy + ry * k * 0.5], fill=int(255 * a * 0.6))
        layer = layer.filter(ImageFilter.GaussianBlur(ry * k * 0.3))
        img.alpha_composite(Image.merge("RGBA", (Image.new("L", (size, h), 5), Image.new("L", (size, h), 3),
                                                 Image.new("L", (size, h), 12), layer)))
    return img


def in_game(fr, size, ground):
    """A frame as the game draws it: scaled as the GPU scales it, graded, over a ground, on its
    shadow. The cell is a little taller than the frame, for the ground under the feet."""
    cell = Image.new("RGBA", (size, int(round(size * (1 + GROUND_PAD)))), ground + (255,))
    cell.alpha_composite(_shadow(size))
    deck = tuple(ground) == dict(GROUNDS)["deck"]
    cell.alpha_composite(engine_graded(fr, deck).resize((size, size), Image.BILINEAR))
    return cell


GAME_VIEWS = ((DOWN, 0), (DOWN, 1), (RIGHT, 0), (RIGHT, 1), (UP, 0), (UP, 3), (LEFT, 0), (LEFT, 3))


def game_page(slug, sheet, out_dir):
    big, small = 154, 77
    bh, sh = in_game(frame(sheet, 0, 0), big, GROUNDS[0][1]).height, in_game(frame(sheet, 0, 0), small, GROUNDS[0][1]).height
    cols = len(GAME_VIEWS)
    page = Image.new("RGBA", (cols * big + 2, len(GROUNDS) * (bh + sh) + 22), (20, 20, 24, 255))
    d = ImageDraw.Draw(page)
    d.text((4, 3), "%s in game: 154px a frame (a HiDPI screen) on each ground, then 77px "
                   "(standing and striding: down, right, up, left)" % slug, font=_font(12), fill=(230, 230, 235))
    y = 20
    for size, step in ((big, bh), (small, sh)):
        for _, ground in GROUNDS:
            for c, (direction, idx) in enumerate(GAME_VIEWS):
                page.alpha_composite(in_game(frame(sheet, direction, idx), size, ground), (1 + c * size, y))
            y += step
    path = os.path.join(out_dir, "%s_game.png" % slug)
    page.convert("RGB").save(path)
    return path


def compare_pages(slug, before, after, out_dir):
    """Before | after, standing and striding, close up, with the same in game underneath:
    the DOWN and UP rows on one page, LEFT and RIGHT on the other."""
    box = union_box([before, after])
    k = _zoom(box, 4, 2, limit=1400)
    cw, ch = (box[2] - box[0]) * k, (box[3] - box[1]) * k
    game = 154
    gh = int(round(game * (1 + GROUND_PAD)))
    paths = []
    for tag, dirs in (("a", (DOWN, UP)), ("b", (LEFT, RIGHT))):
        page = Image.new("RGBA", (max(4 * cw + 5, 8 * game + 2), 2 * (ch + 1) + 22 + 2 * gh), (20, 20, 24, 255))
        d = ImageDraw.Draw(page)
        for c, label in enumerate(("before, standing", "AFTER, standing", "before, striding", "AFTER, striding")):
            d.text((4 + c * (cw + 1), 3), "%s  %s" % (slug, label), font=_font(12),
                   fill=(230, 230, 235) if c % 2 == 0 else (255, 226, 120))
        for r, direction in enumerate(dirs):
            for c, (sheet, idx) in enumerate(((before, 0), (after, 0), (before, 1), (after, 1))):
                page.alpha_composite(_close_cell(frame(sheet, direction, idx), box, k),
                                     (1 + c * (cw + 1), 18 + r * (ch + 1)))
        y = 2 * (ch + 1) + 22
        # in game: before then after, standing then striding, each facing of this page, on the
        # brightest ground and the darkest
        for _, ground in (GROUNDS[1], GROUNDS[0]):
            c = 0
            for direction in dirs:
                for idx in (0, 1):
                    page.alpha_composite(in_game(frame(before, direction, idx), game, ground), (1 + c * game, y))
                    page.alpha_composite(in_game(frame(after, direction, idx), game, ground), (1 + (c + 1) * game, y))
                    c += 2
            y += gh
        path = os.path.join(out_dir, "%s_compare_%s.png" % (slug, tag))
        page.convert("RGB").save(path)
        paths.append(path)
    return paths


def lineup_pages(entries, out_dir, before_dir=None, ground="grass", per_page=8, size=154, prefix="lineup"):
    """The roster a row a character, as the game shows it: standing in all four facings and one
    stride, and with `before_dir` the same from the sheets there on the left (two halves of five
    frames at 154px are as wide as a picture can be looked at whole). `entries` are (slug, sheet)."""
    colour = dict(GROUNDS)[ground]
    views = ((DOWN, 0), (RIGHT, 0), (UP, 0), (LEFT, 0), (RIGHT, 1))
    h = int(round(size * (1 + GROUND_PAD)))
    halves = 2 if before_dir else 1
    gap = 10
    w = halves * len(views) * size + (halves - 1) * gap
    paths = []
    for p in range(0, len(entries), per_page):
        chunk = entries[p:p + per_page]
        page = Image.new("RGBA", (w, len(chunk) * (h + 14)), (20, 20, 24, 255))
        d = ImageDraw.Draw(page)
        for r, (slug, sheet) in enumerate(chunk):
            y = r * (h + 14)
            sheets = [sheet]
            old = os.path.join(before_dir, slug + ".png") if before_dir else None
            if old and os.path.isfile(old):
                sheets = [Image.open(old).convert("RGBA"), sheet]
            elif before_dir:
                sheets = [None, sheet]
            for half, sh in enumerate(sheets):
                x0 = half * (len(views) * size + gap)
                label = slug if halves == 1 else ("%s  before" % slug if half == 0 else "%s  AFTER" % slug)
                d.text((x0 + 4, y + 1), label, font=_font(11),
                       fill=(255, 226, 120) if half == 1 else (230, 230, 235))
                if sh is None:
                    continue
                for c, (direction, idx) in enumerate(views):
                    page.alpha_composite(in_game(frame(sh, direction, idx), size, colour), (x0 + c * size, y + 14))
        path = os.path.join(out_dir, "%s_%s_%02d.png" % (prefix, ground, p // per_page))
        page.convert("RGB").save(path)
        paths.append(path)
    return paths


# ---------------------------------------------------------------------------
# Lint: what can be measured rather than looked at
# ---------------------------------------------------------------------------

def _lab(c):
    def f(t):
        return t ** (1 / 3.0) if t > 0.008856 else 7.787 * t + 16 / 116.0
    lin = [((v / 255.0 + 0.055) / 1.055) ** 2.4 if v / 255.0 > 0.04045 else v / 255.0 / 12.92 for v in c[:3]]
    x = (0.4124 * lin[0] + 0.3576 * lin[1] + 0.1805 * lin[2]) / 0.95047
    y = 0.2126 * lin[0] + 0.7152 * lin[1] + 0.0722 * lin[2]
    z = (0.0193 * lin[0] + 0.1192 * lin[1] + 0.9505 * lin[2]) / 1.08883
    return (116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z)))


def _delta_e(a, b):
    return sum((p - q) ** 2 for p, q in zip(a, b)) ** 0.5


def lint(slug, sheet):
    """Notes on one sheet. Each is (severity, text): "FAIL" breaks a test or a rule of the kit,
    "look" is worth a look and may be meant."""
    notes = []
    alpha = sheet.getchannel("A")
    widest = 0
    edges = set()
    feet = {}
    for d in range(4):
        for i in range(4):
            a = frame(alpha, d, i)
            box = a.point(lambda v: 255 if v > 32 else 0).getbbox()
            if not box:
                notes.append(("FAIL", "%s frame %d is empty" % (DIR_NAMES[d], i)))
                continue
            widest = max(widest, FRAME / 2 - box[0], box[2] - FRAME / 2)
            if box[1] <= 0:
                edges.add("top")
            if box[3] >= FRAME:
                edges.add("bottom")
            if box[0] <= 0 or box[2] >= FRAME:
                edges.add("side")
            if i in (0, 2):
                solid = a.point(lambda v: 255 if v > 128 else 0).getbbox()
                # in the draw functions' own y, where the soles are on 54
                feet[(d, i)] = solid[3] / UNIT - HEADROOM if solid else 0.0
    if widest > TILE_HALF_W:
        notes.append(("FAIL", "reaches %.1f units either side of its middle (26.6 is its tile: "
                              "CharacterRosterTest)" % (widest / UNIT)))
    elif widest > TILE_HALF_W - 2:
        notes.append(("look", "reaches %.1f units either side, close to the tile's 26.6" % (widest / UNIT)))
    limits = {"top": "y = %d" % -HEADROOM, "bottom": "y = %d" % (64 - HEADROOM), "side": "x = 0 or 64"}
    for e in sorted(edges):
        notes.append(("FAIL", "drawn up to the %s edge of its frame (%s): it is being cut off"
                      % (e, limits[e])))
    lows = sorted(set(round(v, 1) for v in feet.values()))
    ground = sprite_base.FEET_Y
    if lows and lows[-1] > ground + 1.6:
        notes.append(("look", "something reaches y = %.1f, below the soles at 54: it lies on the ground in "
                              "front of the feet (58 is the bottom of the frame)" % lows[-1]))
    if lows and lows[-1] < ground - 1.5:
        notes.append(("look", "nothing lower than y = %.1f: it hovers %.1f units over its shadow (fine "
                              "for a ghost)" % (lows[-1], ground - lows[-1])))
    idle = sorted(set(round(v, 1) for (d, i), v in feet.items() if i == 0))
    if len(idle) > 1 and idle[-1] - idle[0] > 1.5:
        notes.append(("look", "standing still, it is at different heights in different facings (lowest "
                              "point y = %.1f to %.1f)" % (idle[0], idle[-1])))
    for d in range(4):
        f0, f1, f2, f3 = (frame(sheet, d, i) for i in range(4))
        if f0.tobytes() == f1.tobytes():
            notes.append(("look", "%s does not move: frame 1 is frame 0" % DIR_NAMES[d]))
        elif f1.tobytes() == f3.tobytes():
            notes.append(("look", "%s strides the same way twice: frame 3 is frame 1" % DIR_NAMES[d]))
        if f0.tobytes() != f2.tobytes():
            notes.append(("look", "%s has two different standing frames (0 and 2)" % DIR_NAMES[d]))
    # How far the silhouette's rim is from each ground, in the engine's colours: the median
    # colour difference (CIE76) of the outermost pixels. Under ~22 it starts to sink in.
    a = frame(sheet, DOWN, 0).getchannel("A").point(lambda v: 255 if v > 128 else 0)
    inner = a.filter(ImageFilter.MinFilter(7))
    am, im = a.tobytes(), inner.tobytes()
    box = a.getbbox()
    for name, ground in GROUNDS if box else ():
        px = engine_graded(frame(sheet, DOWN, 0), name == "deck").convert("RGB").tobytes()
        rim = [(px[3 * i], px[3 * i + 1], px[3 * i + 2]) for i in range(len(am)) if am[i] and not im[i]]
        if rim:
            labs = [_lab(p) for p in rim[::2]]
            g = _lab(ground)
            ds = sorted(_delta_e(p, g) for p in labs)
            med = ds[len(ds) // 2]
            if med < 22:
                notes.append(("look", "its rim is close to the %s's colour (median difference %.0f)" % (name, med)))
    size = ((box[2] - box[0]) / UNIT, (box[3] - box[1]) / UNIT) if box else (0, 0)
    return notes, size


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("out_dir")
    ap.add_argument("--chars", default=None, help="comma-separated slugs or names (default: all, with --lint)")
    ap.add_argument("--against", default=None, help="a directory of sheets to compare with (sprites/)")
    ap.add_argument("--lint", action="store_true", help="print the measurable faults instead of drawing")
    ap.add_argument("--lineup", default=None, metavar="GROUND",
                    help="draw the named characters (default all) a row each as the game shows them, on "
                         "deck, grass, sand, planks or snow; with --against, before beside after")
    ap.add_argument("--size", type=int, default=154, help="--lineup's frame size (154 HiDPI, 77 ordinary)")
    ap.add_argument("--pages", default="close,game,compare",
                    help="which pages to draw: close, game, compare (default all; compare needs --against)")
    ap.add_argument("--write", default=None, metavar="DIR",
                    help="also write each sheet to DIR/<slug>.png as generate_character would")
    args = ap.parse_args()

    os.makedirs(args.out_dir, exist_ok=True)
    slugs = resolve(args.chars) if args.chars else [s for _, s in ROSTER]
    if not args.chars and not args.lint and not args.lineup:
        sys.exit("name the characters to draw with --chars (or --lint or --lineup for the whole roster)")
    fns = draw_functions(set(slugs))

    if args.lineup:
        entries = [(slug, render(fns[slug][0])) for slug in slugs]
        for p in lineup_pages(entries, args.out_dir, args.against, args.lineup, size=args.size,
                              per_page=8 if args.size > 100 else 16):
            print(p)
        return

    if args.lint:
        rows = []
        for slug in slugs:
            notes, size = lint(slug, render(fns[slug][0]))
            rows.append((slug, size, notes))
        for slug, size, notes in rows:
            tag = "FAIL" if any(s == "FAIL" for s, _ in notes) else ("look" if notes else "ok")
            print("%-14s %-4s  %4.1f x %4.1f units" % (slug, tag, size[0], size[1]))
            for sev, text in notes:
                print("      %s: %s" % (sev, text))
        return

    pages = {p.strip() for p in args.pages.split(",")}
    for slug in slugs:
        fn, module = fns[slug]
        sheet = render(fn)
        if "close" in pages:
            for p in close_pages(slug, sheet, args.out_dir):
                print(p)
        if "game" in pages:
            print(game_page(slug, sheet, args.out_dir))
        if args.against and "compare" in pages:
            old = os.path.join(args.against, slug + ".png")
            if os.path.isfile(old):
                for p in compare_pages(slug, Image.open(old).convert("RGBA"), sheet, args.out_dir):
                    print(p)
            else:
                print("(no %s to compare with)" % old)
        if args.write:
            os.makedirs(args.write, exist_ok=True)
            path = os.path.join(args.write, slug + ".png")
            quantized(sprite_base.render_sheet(fn)).save(path, optimize=True)
            print("wrote " + path)


if __name__ == "__main__":
    main()
