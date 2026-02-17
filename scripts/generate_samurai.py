#!/usr/bin/env python3
"""Generate sprites/samurai.png — 4-column x 4-row character spritesheet.

256x256 PNG, 64x64 per frame.
Row layout: Down=0, Up=1, Left=2, Right=3
4 walking animation frames per direction.

Style matches Spaceman/Gladiator: big round head, round body, small limbs, dark outlines.
Theme: Samurai — dark indigo hakama, gray kimono top, straw hat (kasa), katana on back/side.
Enhanced 64x64: layered armor plates (o-yoroi with horizontal bands), katana with visible
hamon line, mon crest on chest, wider shoulder guards.
"""

from PIL import Image, ImageDraw

FRAME_SIZE = 64
COLS = 4
ROWS = 4
IMG_W = FRAME_SIZE * COLS   # 256
IMG_H = FRAME_SIZE * ROWS   # 256

# Colors
OUTLINE = (40, 35, 35)
SKIN = (220, 185, 150)
SKIN_DARK = (190, 155, 120)
INDIGO = (45, 40, 90)
INDIGO_LIGHT = (65, 58, 120)
INDIGO_DARK = (30, 28, 65)
INDIGO_PLEAT = (22, 20, 50)
GRAY_KIMONO = (160, 155, 145)
GRAY_KIMONO_LIGHT = (185, 180, 170)
GRAY_KIMONO_DARK = (130, 125, 115)
STRAW = (210, 190, 130)
STRAW_LIGHT = (230, 215, 160)
STRAW_DARK = (175, 155, 100)
STRAW_STITCH = (150, 130, 80)
KATANA_BLADE = (200, 205, 215)
KATANA_GLINT = (255, 250, 240)
KATANA_HILT = (90, 50, 50)
KATANA_WRAP = (60, 40, 40)
KATANA_GUARD = (170, 150, 50)
WAKI_BLADE = (190, 195, 205)
WAKI_HILT = (80, 45, 45)
BLACK = (30, 30, 30)
HAIR = (35, 25, 20)
HAIR_LIGHT = (55, 40, 35)
WHITE_GLINT = (255, 250, 240)
GOLD_ACCENT = (200, 170, 60)
GOLD_DARK = (160, 130, 40)
TASSEL = (180, 150, 50)
SANDAL_STRAP = (195, 175, 115)
# New detail colors
HAMON_LINE = (220, 225, 235)
MON_CREST = (180, 160, 50)
MON_DARK = (140, 120, 30)
ARMOR_BAND = (55, 48, 100)
ARMOR_BAND_LIGHT = (75, 66, 130)
SHOULDER_GUARD = (50, 44, 95)
SHOULDER_EDGE = (80, 72, 140)
ARMOR_LACE = (120, 35, 35)

DOWN, UP, LEFT, RIGHT = 0, 1, 2, 3


def ellipse(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    """Draw an outlined ellipse centered at (cx, cy)."""
    draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=outline)


def draw_hakama_pleats(draw, x1, y1, x2, y2, direction):
    """Draw vertical pleat lines on hakama pants."""
    w = x2 - x1
    if w < 6:
        return
    # Draw 3-4 thin darker lines depending on width
    for i in range(1, min(5, w // 2)):
        px = x1 + i * w // min(5, w // 2)
        if px < x2:
            draw.line([px, y1 + 2, px, y2 - 2], fill=INDIGO_PLEAT, width=1)


def draw_sandal(draw, x1, y1, x2, y2):
    """Draw a sandal with straw-colored horizontal straps."""
    draw.rectangle([x1, y1, x2, y2], fill=STRAW_DARK, outline=OUTLINE)
    # Horizontal straps
    h = y2 - y1
    if h >= 4:
        draw.line([x1 + 1, y1 + 2, x2 - 1, y1 + 2], fill=SANDAL_STRAP, width=1)
    if h >= 6:
        draw.line([x1 + 1, y2 - 2, x2 - 1, y2 - 2], fill=SANDAL_STRAP, width=1)


def draw_katana(draw, x_base, y_top, x_tip, y_tip, hilt_x, hilt_y):
    """Draw a katana with 4px wide blade, hamon line, and white glint."""
    # Blade — 4px wide
    draw.line([x_base, y_top, x_tip, y_tip], fill=KATANA_BLADE, width=4)
    # Hamon line along blade edge (wavy highlight offset by 1px)
    dx = x_tip - x_base
    dy = y_tip - y_top
    steps = max(abs(dx), abs(dy))
    if steps > 0:
        for i in range(0, steps, 2):
            t = i / steps
            gx = int(x_base + dx * t) + 1
            gy = int(y_top + dy * t)
            draw.point((gx, gy), fill=HAMON_LINE)
        # Glint highlight along blade edge
        for i in range(0, steps, 3):
            t = i / steps
            gx = int(x_base + dx * t) + 2
            gy = int(y_top + dy * t)
            draw.point((gx, gy), fill=KATANA_GLINT)
    # Guard (tsuba) — larger gold square at blade base
    draw.rectangle([x_base - 1, y_top - 1, x_base + 2, y_top + 1], fill=KATANA_GUARD)
    # Hilt (tsuka) — thicker
    draw.line([x_base, y_top, hilt_x, hilt_y], fill=KATANA_HILT, width=4)
    # Hilt wrap detail (diamond pattern)
    mid_hx = (x_base + hilt_x) // 2
    mid_hy = (y_top + hilt_y) // 2
    draw.point((mid_hx, mid_hy), fill=KATANA_WRAP)
    draw.point((mid_hx + 1, mid_hy - 1), fill=KATANA_WRAP)
    draw.point((hilt_x, hilt_y), fill=KATANA_WRAP)
    draw.point((hilt_x - 1, hilt_y + 1), fill=KATANA_WRAP)


def draw_wakizashi(draw, x1, y1, x2, y2, hx, hy):
    """Draw a short wakizashi sword tucked at the waist."""
    # Short blade — 2px wide
    draw.line([x1, y1, x2, y2], fill=WAKI_BLADE, width=2)
    # Glint pixel at midpoint
    mx = (x1 + x2) // 2
    my = (y1 + y2) // 2
    draw.point((mx, my), fill=KATANA_GLINT)
    draw.point((mx + 1, my), fill=KATANA_GLINT)
    # Hilt
    draw.line([x1, y1, hx, hy], fill=WAKI_HILT, width=2)


def draw_obi_knot(draw, cx, cy):
    """Draw a 4x4 gold obi knot with a tassel hanging from it."""
    # 4x4 knot
    draw.rectangle([cx - 2, cy, cx + 2, cy + 4], fill=GOLD_ACCENT, outline=None)
    draw.rectangle([cx - 1, cy, cx + 1, cy + 1], fill=GOLD_DARK)
    # Tassel hanging down (4-6 pixels)
    draw.line([cx, cy + 4, cx, cy + 8], fill=TASSEL, width=2)
    draw.rectangle([cx - 1, cy + 7, cx + 1, cy + 9], fill=GOLD_DARK)


def draw_mon_crest(draw, cx, cy):
    """Draw a mon (family crest) — a small circular emblem on the chest."""
    # Small circle with inner design
    draw.ellipse([cx - 4, cy - 4, cx + 4, cy + 4], fill=MON_CREST, outline=OUTLINE)
    # Inner cross pattern
    draw.line([(cx, cy - 3), (cx, cy + 3)], fill=MON_DARK, width=1)
    draw.line([(cx - 3, cy), (cx + 3, cy)], fill=MON_DARK, width=1)
    # Center dot
    draw.rectangle([cx - 1, cy - 1, cx + 1, cy + 1], fill=GOLD_ACCENT)


def draw_armor_bands(draw, cx, cy, w, h):
    """Draw horizontal layered armor bands (o-yoroi style)."""
    for i in range(0, h, 4):
        y = cy + i
        color = ARMOR_BAND if (i // 4) % 2 == 0 else ARMOR_BAND_LIGHT
        draw.rectangle([cx - w // 2, y, cx + w // 2, y + 2], fill=color, outline=None)
        # Lace detail at edge
        if (i // 4) % 3 == 0:
            draw.point((cx - w // 2, y + 1), fill=ARMOR_LACE)
            draw.point((cx + w // 2, y + 1), fill=ARMOR_LACE)


def draw_shoulder_guard(draw, cx, cy, facing_right=True):
    """Draw a wide shoulder guard (sode)."""
    if facing_right:
        draw.polygon([
            (cx - 2, cy - 4),
            (cx + 8, cy - 4),
            (cx + 10, cy + 4),
            (cx - 2, cy + 4),
        ], fill=SHOULDER_GUARD, outline=OUTLINE)
        # Horizontal bands on shoulder guard
        draw.line([(cx, cy - 2), (cx + 8, cy - 2)], fill=SHOULDER_EDGE, width=1)
        draw.line([(cx, cy + 2), (cx + 9, cy + 2)], fill=SHOULDER_EDGE, width=1)
    else:
        draw.polygon([
            (cx + 2, cy - 4),
            (cx - 8, cy - 4),
            (cx - 10, cy + 4),
            (cx + 2, cy + 4),
        ], fill=SHOULDER_GUARD, outline=OUTLINE)
        draw.line([(cx, cy - 2), (cx - 8, cy - 2)], fill=SHOULDER_EDGE, width=1)
        draw.line([(cx, cy + 2), (cx - 9, cy + 2)], fill=SHOULDER_EDGE, width=1)


def draw_kasa_hat(draw, cx, cy, direction):
    """Draw an ornate kasa hat with stitch lines and chin string."""
    if direction == DOWN:
        # Wide brim
        ellipse(draw, cx, cy - 4, 20, 8, STRAW)
        # Top dome
        ellipse(draw, cx, cy - 8, 12, 6, STRAW_LIGHT)
        # Brim line
        draw.line([cx - 20, cy - 2, cx + 20, cy - 2], fill=STRAW_DARK, width=2)
        # Radial stitch lines from center to edge
        draw.line([cx, cy - 10, cx - 14, cy - 2], fill=STRAW_STITCH, width=1)
        draw.line([cx, cy - 10, cx + 14, cy - 2], fill=STRAW_STITCH, width=1)
        draw.line([cx, cy - 10, cx, cy - 2], fill=STRAW_STITCH, width=1)
        draw.line([cx, cy - 10, cx - 8, cy - 2], fill=STRAW_STITCH, width=1)
        draw.line([cx, cy - 10, cx + 8, cy - 2], fill=STRAW_STITCH, width=1)
        # Chin string (two lines hanging from brim sides)
        draw.line([cx - 10, cy, cx - 8, cy + 8], fill=STRAW_DARK, width=2)
        draw.line([cx + 10, cy, cx + 8, cy + 8], fill=STRAW_DARK, width=2)

    elif direction == UP:
        # Brim
        ellipse(draw, cx, cy - 4, 20, 8, STRAW)
        # Top dome (darker from behind)
        ellipse(draw, cx, cy - 8, 12, 6, STRAW_DARK)
        # Stitch lines
        draw.line([cx, cy - 10, cx - 14, cy - 2], fill=STRAW_STITCH, width=1)
        draw.line([cx, cy - 10, cx + 14, cy - 2], fill=STRAW_STITCH, width=1)
        draw.line([cx, cy - 10, cx, cy - 2], fill=STRAW_STITCH, width=1)
        draw.line([cx, cy - 10, cx - 8, cy - 2], fill=STRAW_STITCH, width=1)
        draw.line([cx, cy - 10, cx + 8, cy - 2], fill=STRAW_STITCH, width=1)
        # Chin string visible from behind (hanging down at back)
        draw.line([cx - 6, cy + 4, cx - 4, cy + 10], fill=STRAW_DARK, width=2)
        draw.line([cx + 6, cy + 4, cx + 4, cy + 10], fill=STRAW_DARK, width=2)

    elif direction == LEFT:
        # Brim (extends left)
        ellipse(draw, cx - 2, cy - 4, 18, 8, STRAW)
        # Top dome
        ellipse(draw, cx - 2, cy - 8, 10, 6, STRAW_LIGHT)
        # Brim line
        draw.line([cx - 20, cy - 2, cx + 16, cy - 2], fill=STRAW_DARK, width=2)
        # Stitch lines
        draw.line([cx - 2, cy - 10, cx - 16, cy - 2], fill=STRAW_STITCH, width=1)
        draw.line([cx - 2, cy - 10, cx + 12, cy - 2], fill=STRAW_STITCH, width=1)
        draw.line([cx - 2, cy - 10, cx - 6, cy - 2], fill=STRAW_STITCH, width=1)
        # Chin string
        draw.line([cx + 4, cy, cx + 2, cy + 8], fill=STRAW_DARK, width=2)

    elif direction == RIGHT:
        # Brim (extends right)
        ellipse(draw, cx + 2, cy - 4, 18, 8, STRAW)
        # Top dome
        ellipse(draw, cx + 2, cy - 8, 10, 6, STRAW_LIGHT)
        # Brim line
        draw.line([cx - 16, cy - 2, cx + 20, cy - 2], fill=STRAW_DARK, width=2)
        # Stitch lines
        draw.line([cx + 2, cy - 10, cx + 16, cy - 2], fill=STRAW_STITCH, width=1)
        draw.line([cx + 2, cy - 10, cx - 12, cy - 2], fill=STRAW_STITCH, width=1)
        draw.line([cx + 2, cy - 10, cx + 6, cy - 2], fill=STRAW_STITCH, width=1)
        # Chin string
        draw.line([cx - 4, cy, cx - 2, cy + 8], fill=STRAW_DARK, width=2)


def draw_topknot(draw, cx, cy, direction):
    """Draw a small dark hair bun visible under the hat brim."""
    if direction == UP:
        # Hair bun visible on top of head (under hat brim, seen from behind)
        draw.rectangle([cx - 2, cy - 2, cx + 2, cy + 2], fill=HAIR, outline=None)
        draw.point((cx, cy - 2), fill=HAIR_LIGHT)
        draw.point((cx - 1, cy - 1), fill=HAIR_LIGHT)
    elif direction == LEFT:
        # Small bun peeking out on right side (back of head)
        draw.rectangle([cx + 6, cy - 4, cx + 10, cy], fill=HAIR, outline=None)
        draw.point((cx + 8, cy - 4), fill=HAIR_LIGHT)
        draw.point((cx + 7, cy - 3), fill=HAIR_LIGHT)
    elif direction == RIGHT:
        # Small bun peeking out on left side (back of head)
        draw.rectangle([cx - 10, cy - 4, cx - 6, cy], fill=HAIR, outline=None)
        draw.point((cx - 8, cy - 4), fill=HAIR_LIGHT)
        draw.point((cx - 7, cy - 3), fill=HAIR_LIGHT)


def draw_samurai(draw, ox, oy, direction, frame):
    """Draw a single samurai frame at offset (ox, oy)."""
    # Walking bob
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]

    base_y = oy + 54 + bob
    body_cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    if direction == DOWN:
        # --- Legs (hakama — wide pants) ---
        lx1 = body_cx - 12 + leg_spread
        lx2 = body_cx - 4 + leg_spread
        rx1 = body_cx + 4 - leg_spread
        rx2 = body_cx + 12 - leg_spread
        ly1 = body_cy + 8
        ly2 = base_y

        # Left leg
        draw.rectangle([lx1, ly1, lx2, ly2], fill=INDIGO, outline=OUTLINE)
        draw_hakama_pleats(draw, lx1, ly1, lx2, ly2, DOWN)
        # Right leg
        draw.rectangle([rx1, ly1, rx2, ly2], fill=INDIGO, outline=OUTLINE)
        draw_hakama_pleats(draw, rx1, ly1, rx2, ly2, DOWN)
        # Sandals
        draw_sandal(draw, lx1, base_y - 4, lx2, base_y)
        draw_sandal(draw, rx1, base_y - 4, rx2, base_y)

        # --- Katana on back (diagonal, behind body) — LONGER with hamon ---
        draw_katana(draw, body_cx + 10, body_cy - 8, body_cx + 18, body_cy + 14,
                    body_cx + 8, body_cy - 16)

        # --- Body (kimono top with armor bands) ---
        ellipse(draw, body_cx, body_cy, 14, 12, GRAY_KIMONO)
        # O-yoroi armor bands on torso
        draw_armor_bands(draw, body_cx, body_cy - 6, 20, 12)
        # Kimono V-neckline
        draw.line([body_cx, body_cy - 8, body_cx - 6, body_cy + 4], fill=GRAY_KIMONO_DARK, width=2)
        draw.line([body_cx, body_cy - 8, body_cx + 6, body_cy + 4], fill=GRAY_KIMONO_DARK, width=2)
        # Mon crest on chest
        draw_mon_crest(draw, body_cx, body_cy - 2)
        # Obi (belt)
        draw.rectangle([body_cx - 14, body_cy + 6, body_cx + 14, body_cy + 10],
                       fill=INDIGO_DARK, outline=OUTLINE)
        # Ornate obi knot
        draw_obi_knot(draw, body_cx, body_cy + 6)

        # --- Wakizashi tucked in obi (front, angled slightly) ---
        draw_wakizashi(draw, body_cx - 10, body_cy + 6, body_cx - 16, body_cy + 16,
                       body_cx - 8, body_cy + 4)

        # --- Arms with shoulder guards ---
        draw.rectangle([body_cx - 18, body_cy - 6, body_cx - 12, body_cy + 6],
                       fill=GRAY_KIMONO, outline=OUTLINE)
        draw.rectangle([body_cx + 12, body_cy - 6, body_cx + 18, body_cy + 6],
                       fill=GRAY_KIMONO, outline=OUTLINE)
        # Wider shoulder guards (sode)
        draw_shoulder_guard(draw, body_cx + 12, body_cy - 6, facing_right=True)
        draw_shoulder_guard(draw, body_cx - 12, body_cy - 6, facing_right=False)
        # Hands
        draw.rectangle([body_cx - 18, body_cy + 2, body_cx - 12, body_cy + 6],
                       fill=SKIN, outline=OUTLINE)
        draw.rectangle([body_cx + 12, body_cy + 2, body_cx + 18, body_cy + 6],
                       fill=SKIN, outline=OUTLINE)

        # --- Head (big round with kasa hat) ---
        # Face
        ellipse(draw, body_cx, head_cy + 2, 12, 10, SKIN)
        # Eyes
        draw.rectangle([body_cx - 6, head_cy, body_cx - 2, head_cy + 4], fill=BLACK)
        draw.rectangle([body_cx + 2, head_cy, body_cx + 6, head_cy + 4], fill=BLACK)
        # Eye glint
        draw.rectangle([body_cx - 5, head_cy, body_cx - 3, head_cy + 1], fill=WHITE_GLINT)
        draw.rectangle([body_cx + 3, head_cy, body_cx + 5, head_cy + 1], fill=WHITE_GLINT)
        # Kasa hat with stitch lines
        draw_kasa_hat(draw, body_cx, head_cy, DOWN)

    elif direction == UP:
        # --- Legs ---
        lx1 = body_cx - 12 + leg_spread
        lx2 = body_cx - 4 + leg_spread
        rx1 = body_cx + 4 - leg_spread
        rx2 = body_cx + 12 - leg_spread
        ly1 = body_cy + 8
        ly2 = base_y

        draw.rectangle([lx1, ly1, lx2, ly2], fill=INDIGO, outline=OUTLINE)
        draw_hakama_pleats(draw, lx1, ly1, lx2, ly2, UP)
        draw.rectangle([rx1, ly1, rx2, ly2], fill=INDIGO, outline=OUTLINE)
        draw_hakama_pleats(draw, rx1, ly1, rx2, ly2, UP)
        draw_sandal(draw, lx1, base_y - 4, lx2, base_y)
        draw_sandal(draw, rx1, base_y - 4, rx2, base_y)

        # --- Body (back view) ---
        ellipse(draw, body_cx, body_cy, 14, 12, GRAY_KIMONO_DARK)
        # Armor bands on back
        draw_armor_bands(draw, body_cx, body_cy - 6, 20, 12)
        # Obi
        draw.rectangle([body_cx - 14, body_cy + 6, body_cx + 14, body_cy + 10],
                       fill=INDIGO_DARK, outline=OUTLINE)

        # --- Katana on back (visible from behind) — LONGER with hamon ---
        draw_katana(draw, body_cx + 6, body_cy - 12, body_cx + 14, body_cy + 12,
                    body_cx + 4, body_cy - 20)

        # --- Arms with shoulder guards ---
        draw.rectangle([body_cx - 18, body_cy - 6, body_cx - 12, body_cy + 6],
                       fill=GRAY_KIMONO_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 12, body_cy - 6, body_cx + 18, body_cy + 6],
                       fill=GRAY_KIMONO_DARK, outline=OUTLINE)
        draw_shoulder_guard(draw, body_cx + 12, body_cy - 6, facing_right=True)
        draw_shoulder_guard(draw, body_cx - 12, body_cy - 6, facing_right=False)

        # --- Head (back of kasa hat) ---
        ellipse(draw, body_cx, head_cy + 2, 12, 10, SKIN_DARK)
        # Topknot hair bun (visible under hat brim from behind)
        draw_topknot(draw, body_cx, head_cy + 6, UP)
        # Kasa hat
        draw_kasa_hat(draw, body_cx, head_cy, UP)

    elif direction == LEFT:
        # --- Legs (side view) ---
        # Back leg
        blx1 = body_cx - 2 - leg_spread
        blx2 = body_cx + 4 - leg_spread
        # Front leg
        flx1 = body_cx - 8 + leg_spread
        flx2 = body_cx - 2 + leg_spread
        ly1 = body_cy + 8
        ly2 = base_y

        draw.rectangle([blx1, ly1, blx2, ly2], fill=INDIGO_DARK, outline=OUTLINE)
        draw_hakama_pleats(draw, blx1, ly1, blx2, ly2, LEFT)
        draw_sandal(draw, blx1, base_y - 4, blx2, base_y)
        draw.rectangle([flx1, ly1, flx2, ly2], fill=INDIGO, outline=OUTLINE)
        draw_hakama_pleats(draw, flx1, ly1, flx2, ly2, LEFT)
        draw_sandal(draw, flx1, base_y - 4, flx2, base_y)

        # --- Katana (on far side/back, angled up) — LONGER with hamon ---
        draw_katana(draw, body_cx + 8, body_cy - 10, body_cx + 14, body_cy + 12,
                    body_cx + 6, body_cy - 18)

        # --- Body ---
        ellipse(draw, body_cx - 2, body_cy, 12, 12, GRAY_KIMONO)
        ellipse(draw, body_cx - 2, body_cy - 2, 8, 8, GRAY_KIMONO_LIGHT)
        # Armor bands on side
        draw_armor_bands(draw, body_cx - 2, body_cy - 4, 14, 10)
        # Obi
        draw.rectangle([body_cx - 14, body_cy + 6, body_cx + 10, body_cy + 10],
                       fill=INDIGO_DARK, outline=OUTLINE)

        # --- Wakizashi tucked in obi (visible from side) ---
        draw_wakizashi(draw, body_cx - 6, body_cy + 8, body_cx - 12, body_cy + 18,
                       body_cx - 4, body_cy + 6)

        # Ornate obi knot (back side, smaller from side view)
        draw.rectangle([body_cx + 6, body_cy + 6, body_cx + 10, body_cy + 10],
                       fill=GOLD_ACCENT, outline=None)
        draw.line([body_cx + 8, body_cy + 10, body_cx + 8, body_cy + 14],
                  fill=TASSEL, width=2)

        # --- Arm (front) with shoulder guard ---
        draw.rectangle([body_cx - 14, body_cy - 4, body_cx - 8, body_cy + 6],
                       fill=GRAY_KIMONO, outline=OUTLINE)
        draw_shoulder_guard(draw, body_cx - 8, body_cy - 6, facing_right=False)
        draw.rectangle([body_cx - 14, body_cy + 2, body_cx - 8, body_cy + 6],
                       fill=SKIN, outline=OUTLINE)

        # --- Head (side view, facing left) ---
        ellipse(draw, body_cx - 2, head_cy + 2, 10, 10, SKIN)
        # Eye
        draw.rectangle([body_cx - 8, head_cy, body_cx - 4, head_cy + 4], fill=BLACK)
        draw.rectangle([body_cx - 7, head_cy, body_cx - 5, head_cy + 1], fill=WHITE_GLINT)
        # Topknot hair bun
        draw_topknot(draw, body_cx - 2, head_cy + 2, LEFT)
        # Kasa hat (side view)
        draw_kasa_hat(draw, body_cx - 2, head_cy, LEFT)

    elif direction == RIGHT:
        # --- Legs ---
        # Back leg
        brx1 = body_cx - 2 + leg_spread
        brx2 = body_cx + 4 + leg_spread
        # Front leg
        frx1 = body_cx + 4 - leg_spread
        frx2 = body_cx + 10 - leg_spread
        ly1 = body_cy + 8
        ly2 = base_y

        draw.rectangle([brx1, ly1, brx2, ly2], fill=INDIGO_DARK, outline=OUTLINE)
        draw_hakama_pleats(draw, brx1, ly1, brx2, ly2, RIGHT)
        draw_sandal(draw, brx1, base_y - 4, brx2, base_y)
        draw.rectangle([frx1, ly1, frx2, ly2], fill=INDIGO, outline=OUTLINE)
        draw_hakama_pleats(draw, frx1, ly1, frx2, ly2, RIGHT)
        draw_sandal(draw, frx1, base_y - 4, frx2, base_y)

        # --- Katana (on far side/back, angled up) — LONGER with hamon ---
        draw_katana(draw, body_cx - 8, body_cy - 10, body_cx - 14, body_cy + 12,
                    body_cx - 6, body_cy - 18)

        # --- Body ---
        ellipse(draw, body_cx + 2, body_cy, 12, 12, GRAY_KIMONO)
        ellipse(draw, body_cx + 2, body_cy - 2, 8, 8, GRAY_KIMONO_LIGHT)
        # Armor bands on side
        draw_armor_bands(draw, body_cx + 2, body_cy - 4, 14, 10)
        # Obi
        draw.rectangle([body_cx - 10, body_cy + 6, body_cx + 14, body_cy + 10],
                       fill=INDIGO_DARK, outline=OUTLINE)

        # --- Wakizashi tucked in obi (visible from side) ---
        draw_wakizashi(draw, body_cx + 6, body_cy + 8, body_cx + 12, body_cy + 18,
                       body_cx + 4, body_cy + 6)

        # Ornate obi knot (back side)
        draw.rectangle([body_cx - 10, body_cy + 6, body_cx - 6, body_cy + 10],
                       fill=GOLD_ACCENT, outline=None)
        draw.line([body_cx - 8, body_cy + 10, body_cx - 8, body_cy + 14],
                  fill=TASSEL, width=2)

        # --- Arm with shoulder guard ---
        draw.rectangle([body_cx + 8, body_cy - 4, body_cx + 14, body_cy + 6],
                       fill=GRAY_KIMONO, outline=OUTLINE)
        draw_shoulder_guard(draw, body_cx + 8, body_cy - 6, facing_right=True)
        draw.rectangle([body_cx + 8, body_cy + 2, body_cx + 14, body_cy + 6],
                       fill=SKIN, outline=OUTLINE)

        # --- Head ---
        ellipse(draw, body_cx + 2, head_cy + 2, 10, 10, SKIN)
        # Eye
        draw.rectangle([body_cx + 4, head_cy, body_cx + 8, head_cy + 4], fill=BLACK)
        draw.rectangle([body_cx + 5, head_cy, body_cx + 7, head_cy + 1], fill=WHITE_GLINT)
        # Topknot hair bun
        draw_topknot(draw, body_cx + 2, head_cy + 2, RIGHT)
        # Kasa hat
        draw_kasa_hat(draw, body_cx + 2, head_cy, RIGHT)


def main():
    img = Image.new("RGBA", (IMG_W, IMG_H), (0, 0, 0, 0))

    for direction in range(ROWS):
        for frame in range(COLS):
            frame_img = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE), (0, 0, 0, 0))
            frame_draw = ImageDraw.Draw(frame_img)
            draw_samurai(frame_draw, 0, 0, direction, frame)
            img.paste(frame_img, (frame * FRAME_SIZE, direction * FRAME_SIZE))

    img.save("sprites/samurai.png")
    print(f"Generated sprites/samurai.png ({IMG_W}x{IMG_H})")


if __name__ == "__main__":
    main()
