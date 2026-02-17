#!/usr/bin/env python3
"""Generate sprites/tidecaller.png — 4-column x 4-row character spritesheet.

256x256 PNG, 64x64 per frame.
Row layout: Down=0, Up=1, Left=2, Right=3
4 walking animation frames per direction.

Style matches Spaceman/Gladiator: big round head, round body, small limbs, dark outlines.
Theme: Water mage — flowing blue robes, staff with water orb, blue/teal palette.
Enhanced 64x64: scale armor pattern, trident with defined prongs, water droplet
particles, coral accent details.
"""

from PIL import Image, ImageDraw

FRAME_SIZE = 64
COLS = 4
ROWS = 4
IMG_W = FRAME_SIZE * COLS   # 256
IMG_H = FRAME_SIZE * ROWS   # 256

# Colors
OUTLINE = (40, 35, 35)
SKIN = (200, 185, 170)
SKIN_DARK = (170, 150, 135)
ROBE_DARK = (25, 55, 110)
ROBE = (35, 80, 150)
ROBE_LIGHT = (55, 110, 180)
TEAL = (40, 170, 170)
TEAL_LIGHT = (80, 210, 210)
TEAL_BRIGHT = (120, 240, 240)
TEAL_DARK = (25, 120, 120)
WATER_ORB = (100, 200, 240)
WATER_GLOW = (150, 230, 255)
WATER_CORE = (230, 250, 255)
WATER_RIPPLE = (80, 180, 220, 180)
WATER_DROP = (100, 230, 245)
WATER_DROP_BRIGHT = (170, 245, 255)
SPLASH_BRIGHT = (140, 235, 255)
SPLASH_DIM = (80, 190, 220)
STAFF_BROWN = (90, 65, 40)
STAFF_DARK = (60, 45, 30)
BLACK = (30, 30, 30)
WHITE_GLINT = (220, 240, 255)
GEM_CORE = (60, 240, 220)
GEM_SHINE = (180, 255, 245)
ROBE_HEM = (50, 150, 160)
SCALE_LIGHT = (45, 95, 170)
SCALE_DARK = (30, 70, 130)
CORAL = (200, 100, 80)
CORAL_LIGHT = (220, 130, 100)

DOWN, UP, LEFT, RIGHT = 0, 1, 2, 3

# Per-frame orbiting water droplet positions (relative to body center).
DROPLET_OFFSETS_DOWN = [
    [(-20, -10), (16, 4), (-6, -24)],
    [(-16, 4), (20, -10), (6, -28)],
    [(-20, 10), (14, -6), (-10, -22)],
    [(-14, -4), (20, 8), (4, -26)],
]
DROPLET_OFFSETS_UP = [
    [(20, -10), (-16, 4), (6, -24)],
    [(16, 4), (-20, -10), (-6, -28)],
    [(20, 10), (-14, -6), (10, -22)],
    [(14, -4), (-20, 8), (-4, -26)],
]
DROPLET_OFFSETS_LEFT = [
    [(-18, -14), (-10, 10), (-24, -2)],
    [(-14, -6), (-16, 12), (-26, 4)],
    [(-20, -10), (-8, 8), (-22, 2)],
    [(-16, -2), (-12, 14), (-28, -4)],
]
DROPLET_OFFSETS_RIGHT = [
    [(18, -14), (10, 10), (24, -2)],
    [(14, -6), (16, 12), (26, 4)],
    [(20, -10), (8, 8), (22, 2)],
    [(16, -2), (12, 14), (28, -4)],
]

# Per-frame foot splash pixel offsets
SPLASH_DOWN = [
    [(-10, 0), (8, -2), (12, 0)],
    [(-8, -2), (10, 0), (-12, 0)],
    [(-12, 0), (6, -2), (10, 0)],
    [(-6, 0), (12, -2), (-10, -2)],
]
SPLASH_UP = [
    [(-8, 0), (10, -2), (-12, 0)],
    [(-10, -2), (8, 0), (12, 0)],
    [(-6, 0), (12, -2), (-10, 0)],
    [(-12, -2), (6, 0), (10, -2)],
]
SPLASH_LEFT = [
    [(-6, 0), (-2, -2), (-10, 0)],
    [(-4, -2), (-8, 0), (0, 0)],
    [(-8, 0), (-2, 0), (-12, -2)],
    [(-6, -2), (-10, 0), (2, 0)],
]
SPLASH_RIGHT = [
    [(6, 0), (2, -2), (10, 0)],
    [(4, -2), (8, 0), (0, 0)],
    [(8, 0), (2, 0), (12, -2)],
    [(6, -2), (10, 0), (-2, 0)],
]


def ellipse(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    """Draw an outlined ellipse centered at (cx, cy)."""
    draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=outline)


def pill(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    """Draw a rounded rectangle (pill shape)."""
    draw.rounded_rectangle([cx - rx, cy - ry, cx + rx, cy + ry],
                           radius=min(rx, ry), fill=fill, outline=outline)


def draw_water_droplets(draw, cx, cy, offsets):
    """Draw 3 orbiting water droplet particles."""
    for i, (dx, dy) in enumerate(offsets):
        px, py = cx + dx, cy + dy
        color = WATER_DROP_BRIGHT if i % 2 == 0 else WATER_DROP
        draw.point((px, py), fill=color)
        draw.point((px + 1, py), fill=color)
        if i == 0:
            draw.point((px, py - 2), fill=WATER_DROP)
            draw.point((px, py - 1), fill=WATER_DROP)
        elif i == 1:
            draw.point((px + 2, py), fill=WATER_DROP)
            draw.point((px + 1, py + 1), fill=WATER_DROP)


def draw_staff_orb(draw, cx, cy):
    """Draw a dramatic water orb with white core, outer ripple ring, and drip."""
    ellipse(draw, cx, cy, 10, 10, WATER_RIPPLE, outline=None)
    ellipse(draw, cx, cy, 6, 6, WATER_ORB, outline=OUTLINE)
    draw.rectangle([cx - 2, cy - 2, cx + 2, cy + 2], fill=WATER_CORE)
    draw.point((cx - 2, cy - 4), fill=WHITE_GLINT)
    draw.point((cx, cy - 4), fill=WHITE_GLINT)
    draw.point((cx - 1, cy - 3), fill=WHITE_GLINT)
    draw.point((cx, cy + 8), fill=WATER_GLOW)
    draw.point((cx, cy + 10), fill=SPLASH_DIM)
    draw.point((cx, cy + 9), fill=SPLASH_DIM)


def draw_foot_splash(draw, cx, base_y, offsets):
    """Draw small bright splash pixels at foot level."""
    for i, (dx, dy) in enumerate(offsets):
        color = SPLASH_BRIGHT if i == 0 else SPLASH_DIM
        draw.point((cx + dx, base_y + dy), fill=color)
        draw.point((cx + dx + 1, base_y + dy), fill=color)


def draw_robe_hem_wave(draw, left_x, right_x, y, frame):
    """Draw a wavy teal trim line at the bottom of the robe."""
    wave_offsets = [
        [0, -2, 0, 2, 0, -2, 0, 2, 0, -1, 0, 1, 0, -2, 0, 2, 0],
        [2, 0, -2, 0, 2, 0, -2, 0, 2, 0, -1, 0, 1, 0, -2, 0, 2],
        [0, 2, 0, -2, 0, 2, 0, -2, 0, 1, 0, -1, 0, 2, 0, -2, 0],
        [-2, 0, 2, 0, -2, 0, 2, 0, -2, 0, 1, 0, -1, 0, 2, 0, -2],
    ]
    wave = wave_offsets[frame]
    width = right_x - left_x
    for i in range(width + 1):
        px = left_x + i
        wave_idx = i % len(wave)
        py = y + wave[wave_idx]
        draw.point((px, py), fill=TEAL_BRIGHT)
        draw.point((px, py + 1), fill=TEAL)
        draw.point((px, py + 2), fill=TEAL_DARK)


def draw_hood_gem(draw, cx, cy):
    """Draw a bright teal gem/brooch at the forehead of the hood."""
    draw.rectangle([cx - 1, cy - 1, cx + 1, cy + 1], fill=GEM_CORE)
    draw.point((cx - 2, cy), fill=GEM_SHINE)
    draw.point((cx + 2, cy), fill=TEAL_DARK)
    draw.point((cx, cy - 2), fill=GEM_SHINE)
    draw.point((cx, cy + 2), fill=TEAL_DARK)


def draw_hand_glow(draw, hx, hy):
    """Draw a small teal glow near a hand position."""
    draw.point((hx, hy), fill=TEAL_BRIGHT)
    draw.point((hx - 2, hy), fill=TEAL_LIGHT)
    draw.point((hx + 2, hy), fill=TEAL_LIGHT)
    draw.point((hx, hy - 1), fill=TEAL_LIGHT)


def draw_scale_armor(draw, cx, cy, w, h):
    """Draw overlapping semicircle scale armor pattern."""
    for row in range(0, h, 4):
        offset = 3 if (row // 4) % 2 else 0
        for col in range(-w // 2 + offset, w // 2, 6):
            sx = cx + col
            sy = cy + row
            draw.arc([sx - 3, sy - 2, sx + 3, sy + 2], start=0, end=180, fill=SCALE_LIGHT)
            draw.point((sx, sy + 1), fill=SCALE_DARK)


def draw_coral_accents(draw, cx, cy):
    """Draw small coral accent details."""
    draw.point((cx - 8, cy + 8), fill=CORAL)
    draw.point((cx - 9, cy + 7), fill=CORAL_LIGHT)
    draw.point((cx + 8, cy + 8), fill=CORAL)
    draw.point((cx + 9, cy + 7), fill=CORAL_LIGHT)


def draw_tidecaller(draw, ox, oy, direction, frame):
    """Draw a single tidecaller frame at offset (ox, oy)."""
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]

    base_y = oy + 54 + bob
    body_cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    if direction == DOWN:
        # --- Legs (flowing robe bottom, flared wider) ---
        lx1 = body_cx - 10 + leg_spread
        lx2 = body_cx - 2 + leg_spread
        draw.polygon([(lx1 - 2, base_y), (lx1 + 2, body_cy + 12),
                       (lx2, body_cy + 12), (lx2 + 2, base_y)],
                     fill=ROBE_DARK, outline=OUTLINE)
        rx1 = body_cx + 2 - leg_spread
        rx2 = body_cx + 10 - leg_spread
        draw.polygon([(rx1 - 2, base_y), (rx1, body_cy + 12),
                       (rx2 - 2, body_cy + 12), (rx2 + 2, base_y)],
                     fill=ROBE_DARK, outline=OUTLINE)
        draw_robe_hem_wave(draw, body_cx - 12, body_cx + 12, base_y - 2, frame)

        # --- Body (flowing robe) ---
        ellipse(draw, body_cx, body_cy, 14, 12, ROBE)
        ellipse(draw, body_cx, body_cy - 2, 10, 8, ROBE_LIGHT)
        # Scale armor pattern
        draw_scale_armor(draw, body_cx, body_cy - 6, 16, 10)
        # Teal sash/belt
        draw.rectangle([body_cx - 14, body_cy + 6, body_cx + 14, body_cy + 10],
                       fill=TEAL, outline=OUTLINE)
        # Coral accents
        draw_coral_accents(draw, body_cx, body_cy)

        # --- Arms ---
        draw.rectangle([body_cx - 18, body_cy - 6, body_cx - 12, body_cy + 6],
                       fill=ROBE, outline=OUTLINE)
        draw.rectangle([body_cx + 12, body_cy - 6, body_cx + 18, body_cy + 6],
                       fill=ROBE, outline=OUTLINE)
        ellipse(draw, body_cx - 14, body_cy - 6, 6, 4, ROBE_LIGHT)
        ellipse(draw, body_cx + 14, body_cy - 6, 6, 4, ROBE_LIGHT)
        draw_hand_glow(draw, body_cx - 18, body_cy + 6)
        draw_hand_glow(draw, body_cx + 18, body_cy + 6)

        # --- Staff (left side) with trident prongs ---
        draw.rectangle([body_cx - 22, body_cy - 24, body_cx - 20, body_cy + 4],
                       fill=STAFF_BROWN, outline=OUTLINE)
        # Trident prongs
        draw.line([(body_cx - 21, body_cy - 24), (body_cx - 21, body_cy - 30)], fill=TEAL, width=2)
        draw.line([(body_cx - 25, body_cy - 24), (body_cx - 25, body_cy - 28)], fill=TEAL, width=2)
        draw.line([(body_cx - 17, body_cy - 24), (body_cx - 17, body_cy - 28)], fill=TEAL, width=2)
        # Prong tips
        draw.point((body_cx - 21, body_cy - 31), fill=TEAL_BRIGHT)
        draw.point((body_cx - 25, body_cy - 29), fill=TEAL_BRIGHT)
        draw.point((body_cx - 17, body_cy - 29), fill=TEAL_BRIGHT)
        # Water orb
        draw_staff_orb(draw, body_cx - 20, body_cy - 30)

        # --- Head (hooded) ---
        ellipse(draw, body_cx, head_cy, 16, 14, ROBE)
        ellipse(draw, body_cx, head_cy + 4, 10, 8, SKIN)
        draw.arc([body_cx - 16, head_cy - 14, body_cx + 16, head_cy + 2],
                 start=0, end=180, fill=ROBE_DARK)
        # Eyes -- glowing teal
        draw.rectangle([body_cx - 6, head_cy + 2, body_cx - 2, head_cy + 6], fill=TEAL_LIGHT)
        draw.rectangle([body_cx + 2, head_cy + 2, body_cx + 6, head_cy + 6], fill=TEAL_LIGHT)
        # Hood peak
        draw.polygon([(body_cx - 4, head_cy - 14), (body_cx, head_cy - 20),
                      (body_cx + 4, head_cy - 14)], fill=ROBE_DARK, outline=OUTLINE)
        draw_hood_gem(draw, body_cx, head_cy - 10)

        draw_water_droplets(draw, body_cx, body_cy, DROPLET_OFFSETS_DOWN[frame])
        draw_foot_splash(draw, body_cx, base_y, SPLASH_DOWN[frame])

    elif direction == UP:
        lx1 = body_cx - 10 + leg_spread
        lx2 = body_cx - 2 + leg_spread
        draw.polygon([(lx1 - 2, base_y), (lx1 + 2, body_cy + 12),
                       (lx2, body_cy + 12), (lx2 + 2, base_y)],
                     fill=ROBE_DARK, outline=OUTLINE)
        rx1 = body_cx + 2 - leg_spread
        rx2 = body_cx + 10 - leg_spread
        draw.polygon([(rx1 - 2, base_y), (rx1, body_cy + 12),
                       (rx2 - 2, body_cy + 12), (rx2 + 2, base_y)],
                     fill=ROBE_DARK, outline=OUTLINE)
        draw_robe_hem_wave(draw, body_cx - 12, body_cx + 12, base_y - 2, frame)

        robe_sway = [0, 2, 0, -2][frame]
        draw.rounded_rectangle([body_cx - 12 + robe_sway, body_cy - 4,
                                body_cx + 12 + robe_sway, body_cy + 14],
                               radius=6, fill=ROBE, outline=OUTLINE)
        draw.rounded_rectangle([body_cx - 10 + robe_sway, body_cy - 2,
                                body_cx + 10 + robe_sway, body_cy + 12],
                               radius=4, fill=ROBE_DARK, outline=None)

        ellipse(draw, body_cx, body_cy, 14, 12, ROBE)
        ellipse(draw, body_cx, body_cy - 2, 10, 8, ROBE_DARK)

        draw.rectangle([body_cx - 18, body_cy - 6, body_cx - 12, body_cy + 6],
                       fill=ROBE, outline=OUTLINE)
        draw.rectangle([body_cx + 12, body_cy - 6, body_cx + 18, body_cy + 6],
                       fill=ROBE, outline=OUTLINE)
        ellipse(draw, body_cx - 14, body_cy - 6, 6, 4, ROBE_LIGHT)
        ellipse(draw, body_cx + 14, body_cy - 6, 6, 4, ROBE_LIGHT)
        draw_hand_glow(draw, body_cx - 18, body_cy + 6)
        draw_hand_glow(draw, body_cx + 18, body_cy + 6)

        draw.rectangle([body_cx - 22, body_cy - 24, body_cx - 20, body_cy + 4],
                       fill=STAFF_BROWN, outline=OUTLINE)
        draw.line([(body_cx - 21, body_cy - 24), (body_cx - 21, body_cy - 30)], fill=TEAL, width=2)
        draw.line([(body_cx - 25, body_cy - 24), (body_cx - 25, body_cy - 28)], fill=TEAL, width=2)
        draw.line([(body_cx - 17, body_cy - 24), (body_cx - 17, body_cy - 28)], fill=TEAL, width=2)
        draw_staff_orb(draw, body_cx - 20, body_cy - 30)

        ellipse(draw, body_cx, head_cy, 16, 14, ROBE)
        ellipse(draw, body_cx, head_cy, 12, 10, ROBE_DARK)
        draw.polygon([(body_cx - 4, head_cy - 14), (body_cx, head_cy - 20),
                      (body_cx + 4, head_cy - 14)], fill=ROBE_DARK, outline=OUTLINE)
        draw.point((body_cx, head_cy - 10), fill=GEM_CORE)

        draw_water_droplets(draw, body_cx, body_cy, DROPLET_OFFSETS_UP[frame])
        draw_foot_splash(draw, body_cx, base_y, SPLASH_UP[frame])

    elif direction == LEFT:
        bx1 = body_cx - 2 - leg_spread
        bx2 = body_cx + 4 - leg_spread
        draw.polygon([(bx1 - 2, base_y), (bx1, body_cy + 12),
                       (bx2, body_cy + 12), (bx2 + 2, base_y)],
                     fill=ROBE_DARK, outline=OUTLINE)
        fx1 = body_cx - 8 + leg_spread
        fx2 = body_cx - 2 + leg_spread
        draw.polygon([(fx1 - 2, base_y), (fx1, body_cy + 12),
                       (fx2, body_cy + 12), (fx2 + 2, base_y)],
                     fill=ROBE, outline=OUTLINE)
        draw_robe_hem_wave(draw, body_cx - 12, body_cx + 6, base_y - 2, frame)

        robe_sway = [0, 2, 0, -2][frame]
        draw.rounded_rectangle([body_cx + 6, body_cy - 6,
                                body_cx + 16 + robe_sway, body_cy + 12],
                               radius=6, fill=ROBE, outline=OUTLINE)

        ellipse(draw, body_cx - 2, body_cy, 12, 12, ROBE)
        ellipse(draw, body_cx - 2, body_cy - 2, 8, 8, ROBE_LIGHT)
        draw_scale_armor(draw, body_cx - 2, body_cy - 6, 12, 8)
        draw.rectangle([body_cx - 14, body_cy + 6, body_cx + 10, body_cy + 10],
                       fill=TEAL, outline=OUTLINE)
        draw_coral_accents(draw, body_cx - 2, body_cy)

        draw.rectangle([body_cx - 14, body_cy - 4, body_cx - 8, body_cy + 6],
                       fill=ROBE, outline=OUTLINE)
        ellipse(draw, body_cx - 10, body_cy - 6, 6, 4, ROBE_LIGHT)
        draw_hand_glow(draw, body_cx - 14, body_cy + 6)

        draw.rectangle([body_cx - 16, body_cy - 28, body_cx - 14, body_cy + 4],
                       fill=STAFF_BROWN, outline=OUTLINE)
        draw.line([(body_cx - 15, body_cy - 28), (body_cx - 15, body_cy - 34)], fill=TEAL, width=2)
        draw.line([(body_cx - 19, body_cy - 28), (body_cx - 19, body_cy - 32)], fill=TEAL, width=2)
        draw.line([(body_cx - 11, body_cy - 28), (body_cx - 11, body_cy - 32)], fill=TEAL, width=2)
        draw_staff_orb(draw, body_cx - 14, body_cy - 34)

        ellipse(draw, body_cx - 2, head_cy, 14, 14, ROBE)
        ellipse(draw, body_cx - 6, head_cy + 4, 8, 6, SKIN)
        draw.arc([body_cx - 16, head_cy - 14, body_cx + 8, head_cy + 2],
                 start=0, end=180, fill=ROBE_DARK)
        draw.rectangle([body_cx - 10, head_cy + 2, body_cx - 6, head_cy + 6], fill=TEAL_LIGHT)
        draw.polygon([(body_cx - 6, head_cy - 14), (body_cx - 2, head_cy - 20),
                      (body_cx + 2, head_cy - 14)], fill=ROBE_DARK, outline=OUTLINE)
        draw_hood_gem(draw, body_cx - 4, head_cy - 10)

        draw_water_droplets(draw, body_cx, body_cy, DROPLET_OFFSETS_LEFT[frame])
        draw_foot_splash(draw, body_cx, base_y, SPLASH_LEFT[frame])

    elif direction == RIGHT:
        bx1 = body_cx - 2 + leg_spread
        bx2 = body_cx + 4 + leg_spread
        draw.polygon([(bx1 - 2, base_y), (bx1, body_cy + 12),
                       (bx2, body_cy + 12), (bx2 + 2, base_y)],
                     fill=ROBE_DARK, outline=OUTLINE)
        fx1 = body_cx + 4 - leg_spread
        fx2 = body_cx + 10 - leg_spread
        draw.polygon([(fx1 - 2, base_y), (fx1, body_cy + 12),
                       (fx2, body_cy + 12), (fx2 + 2, base_y)],
                     fill=ROBE, outline=OUTLINE)
        draw_robe_hem_wave(draw, body_cx - 6, body_cx + 12, base_y - 2, frame)

        robe_sway = [0, -2, 0, 2][frame]
        draw.rounded_rectangle([body_cx - 16 + robe_sway, body_cy - 6,
                                body_cx - 6, body_cy + 12],
                               radius=6, fill=ROBE, outline=OUTLINE)

        ellipse(draw, body_cx + 2, body_cy, 12, 12, ROBE)
        ellipse(draw, body_cx + 2, body_cy - 2, 8, 8, ROBE_LIGHT)
        draw_scale_armor(draw, body_cx + 2, body_cy - 6, 12, 8)
        draw.rectangle([body_cx - 10, body_cy + 6, body_cx + 14, body_cy + 10],
                       fill=TEAL, outline=OUTLINE)
        draw_coral_accents(draw, body_cx + 2, body_cy)

        draw.rectangle([body_cx + 8, body_cy - 4, body_cx + 14, body_cy + 6],
                       fill=ROBE, outline=OUTLINE)
        ellipse(draw, body_cx + 10, body_cy - 6, 6, 4, ROBE_LIGHT)
        draw_hand_glow(draw, body_cx + 14, body_cy + 6)

        draw.rectangle([body_cx + 14, body_cy - 28, body_cx + 16, body_cy + 4],
                       fill=STAFF_BROWN, outline=OUTLINE)
        draw.line([(body_cx + 15, body_cy - 28), (body_cx + 15, body_cy - 34)], fill=TEAL, width=2)
        draw.line([(body_cx + 11, body_cy - 28), (body_cx + 11, body_cy - 32)], fill=TEAL, width=2)
        draw.line([(body_cx + 19, body_cy - 28), (body_cx + 19, body_cy - 32)], fill=TEAL, width=2)
        draw_staff_orb(draw, body_cx + 14, body_cy - 34)

        ellipse(draw, body_cx + 2, head_cy, 14, 14, ROBE)
        ellipse(draw, body_cx + 6, head_cy + 4, 8, 6, SKIN)
        draw.arc([body_cx - 8, head_cy - 14, body_cx + 16, head_cy + 2],
                 start=0, end=180, fill=ROBE_DARK)
        draw.rectangle([body_cx + 6, head_cy + 2, body_cx + 10, head_cy + 6], fill=TEAL_LIGHT)
        draw.polygon([(body_cx - 2, head_cy - 14), (body_cx + 2, head_cy - 20),
                      (body_cx + 6, head_cy - 14)], fill=ROBE_DARK, outline=OUTLINE)
        draw_hood_gem(draw, body_cx + 4, head_cy - 10)

        draw_water_droplets(draw, body_cx, body_cy, DROPLET_OFFSETS_RIGHT[frame])
        draw_foot_splash(draw, body_cx, base_y, SPLASH_RIGHT[frame])


def main():
    img = Image.new("RGBA", (IMG_W, IMG_H), (0, 0, 0, 0))

    for direction in range(ROWS):
        for frame in range(COLS):
            frame_img = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE), (0, 0, 0, 0))
            frame_draw = ImageDraw.Draw(frame_img)
            draw_tidecaller(frame_draw, 0, 0, direction, frame)
            img.paste(frame_img, (frame * FRAME_SIZE, direction * FRAME_SIZE))

    img.save("sprites/tidecaller.png")
    print(f"Generated sprites/tidecaller.png ({IMG_W}x{IMG_H})")


if __name__ == "__main__":
    main()
