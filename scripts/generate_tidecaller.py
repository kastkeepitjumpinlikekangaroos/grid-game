#!/usr/bin/env python3
"""Generate sprites/tidecaller.png — 4-column x 4-row character spritesheet.

128x128 PNG, 32x32 per frame.
Row layout: Down=0, Up=1, Left=2, Right=3
4 walking animation frames per direction.

Style matches Spaceman/Gladiator: big round head, round body, small limbs, dark outlines.
Theme: Water mage — flowing blue robes, staff with water orb, blue/teal palette.
Enhanced: orbiting water droplets, dramatic staff orb with ripple ring, wavy robe hem,
hood gem, flared robe bottom, water splash at feet, teal hand glow.
"""

from PIL import Image, ImageDraw

FRAME_SIZE = 32
COLS = 4
ROWS = 4
IMG_W = FRAME_SIZE * COLS   # 128
IMG_H = FRAME_SIZE * ROWS   # 128

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

DOWN, UP, LEFT, RIGHT = 0, 1, 2, 3

# Per-frame orbiting water droplet positions (relative to body center).
# 3 droplets per frame, cycling around the character.
DROPLET_OFFSETS_DOWN = [
    [(-10, -5), (8, 2), (-3, -12)],
    [(-8, 2), (10, -5), (3, -14)],
    [(-10, 5), (7, -3), (-5, -11)],
    [(-7, -2), (10, 4), (2, -13)],
]
DROPLET_OFFSETS_UP = [
    [(10, -5), (-8, 2), (3, -12)],
    [(8, 2), (-10, -5), (-3, -14)],
    [(10, 5), (-7, -3), (5, -11)],
    [(7, -2), (-10, 4), (-2, -13)],
]
DROPLET_OFFSETS_LEFT = [
    [(-9, -7), (-5, 5), (-12, -1)],
    [(-7, -3), (-8, 6), (-13, 2)],
    [(-10, -5), (-4, 4), (-11, 1)],
    [(-8, -1), (-6, 7), (-14, -2)],
]
DROPLET_OFFSETS_RIGHT = [
    [(9, -7), (5, 5), (12, -1)],
    [(7, -3), (8, 6), (13, 2)],
    [(10, -5), (4, 4), (11, 1)],
    [(8, -1), (6, 7), (14, -2)],
]

# Per-frame foot splash pixel offsets (relative to base_y, body_cx)
SPLASH_DOWN = [
    [(-5, 0), (4, -1), (6, 0)],
    [(-4, -1), (5, 0), (-6, 0)],
    [(-6, 0), (3, -1), (5, 0)],
    [(-3, 0), (6, -1), (-5, -1)],
]
SPLASH_UP = [
    [(-4, 0), (5, -1), (-6, 0)],
    [(-5, -1), (4, 0), (6, 0)],
    [(-3, 0), (6, -1), (-5, 0)],
    [(-6, -1), (3, 0), (5, -1)],
]
SPLASH_LEFT = [
    [(-3, 0), (-1, -1), (-5, 0)],
    [(-2, -1), (-4, 0), (0, 0)],
    [(-4, 0), (-1, 0), (-6, -1)],
    [(-3, -1), (-5, 0), (1, 0)],
]
SPLASH_RIGHT = [
    [(3, 0), (1, -1), (5, 0)],
    [(2, -1), (4, 0), (0, 0)],
    [(4, 0), (1, 0), (6, -1)],
    [(3, -1), (5, 0), (-1, 0)],
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
        # Alternate brightness for variety
        color = WATER_DROP_BRIGHT if i % 2 == 0 else WATER_DROP
        draw.point((px, py), fill=color)
        # Add a second pixel to make droplets slightly more visible
        if i == 0:
            draw.point((px, py - 1), fill=WATER_DROP)
        elif i == 1:
            draw.point((px + 1, py), fill=WATER_DROP)


def draw_staff_orb(draw, cx, cy):
    """Draw a dramatic water orb with white core, outer ripple ring, and drip."""
    # Outer ripple ring (slightly larger, semi-transparent feel via lighter color)
    ellipse(draw, cx, cy, 5, 5, WATER_RIPPLE, outline=None)
    # Main orb
    ellipse(draw, cx, cy, 3, 3, WATER_ORB, outline=OUTLINE)
    # Inner bright core
    draw.rectangle([cx - 1, cy - 1, cx + 1, cy + 1], fill=WATER_CORE)
    # White glint
    draw.point((cx - 1, cy - 2), fill=WHITE_GLINT)
    draw.point((cx, cy - 2), fill=WHITE_GLINT)
    # Drip pixel below the orb
    draw.point((cx, cy + 4), fill=WATER_GLOW)
    draw.point((cx, cy + 5), fill=SPLASH_DIM)


def draw_foot_splash(draw, cx, base_y, offsets):
    """Draw small bright splash pixels at foot level."""
    for i, (dx, dy) in enumerate(offsets):
        color = SPLASH_BRIGHT if i == 0 else SPLASH_DIM
        draw.point((cx + dx, base_y + dy), fill=color)


def draw_robe_hem_wave(draw, left_x, right_x, y, frame):
    """Draw a wavy teal trim line at the bottom of the robe."""
    wave_offsets = [
        [0, -1, 0, 1, 0, -1, 0, 1, 0],
        [1, 0, -1, 0, 1, 0, -1, 0, 1],
        [0, 1, 0, -1, 0, 1, 0, -1, 0],
        [-1, 0, 1, 0, -1, 0, 1, 0, -1],
    ]
    wave = wave_offsets[frame]
    width = right_x - left_x
    for i in range(width + 1):
        px = left_x + i
        wave_idx = i % len(wave)
        py = y + wave[wave_idx]
        draw.point((px, py), fill=TEAL_BRIGHT)
        # Second row for thickness
        draw.point((px, py + 1), fill=TEAL)


def draw_hood_gem(draw, cx, cy):
    """Draw a bright teal gem/brooch at the forehead of the hood."""
    draw.point((cx, cy), fill=GEM_CORE)
    draw.point((cx - 1, cy), fill=GEM_SHINE)
    draw.point((cx + 1, cy), fill=TEAL_DARK)
    draw.point((cx, cy - 1), fill=GEM_SHINE)


def draw_hand_glow(draw, hx, hy):
    """Draw a small teal glow near a hand position."""
    draw.point((hx, hy), fill=TEAL_BRIGHT)
    draw.point((hx - 1, hy), fill=TEAL_LIGHT)
    draw.point((hx + 1, hy), fill=TEAL_LIGHT)


def draw_tidecaller(draw, ox, oy, direction, frame):
    """Draw a single tidecaller frame at offset (ox, oy).

    Proportions match Spaceman/Gladiator: big round head ~11px, body ~8px tall,
    small stick legs, centered in 32x32 frame.
    """
    # Walking bob
    bob = [0, -1, 0, -1][frame]
    leg_spread = [-2, 0, 2, 0][frame]

    # Anchor: bottom of feet at oy+28
    base_y = oy + 27 + bob
    body_cx = ox + 16
    body_cy = base_y - 10
    head_cy = body_cy - 10

    if direction == DOWN:
        # --- Legs (flowing robe bottom, flared wider) ---
        # Left leg/robe flare
        lx1 = body_cx - 5 + leg_spread
        lx2 = body_cx - 1 + leg_spread
        draw.polygon([(lx1 - 1, base_y), (lx1 + 1, body_cy + 6),
                       (lx2, body_cy + 6), (lx2 + 1, base_y)],
                     fill=ROBE_DARK, outline=OUTLINE)
        # Right leg/robe flare
        rx1 = body_cx + 1 - leg_spread
        rx2 = body_cx + 5 - leg_spread
        draw.polygon([(rx1 - 1, base_y), (rx1, body_cy + 6),
                       (rx2 - 1, body_cy + 6), (rx2 + 1, base_y)],
                     fill=ROBE_DARK, outline=OUTLINE)
        # Wavy hem at bottom of robe
        draw_robe_hem_wave(draw, body_cx - 6, body_cx + 6, base_y - 1, frame)

        # --- Body (flowing robe) ---
        ellipse(draw, body_cx, body_cy, 7, 6, ROBE)
        ellipse(draw, body_cx, body_cy - 1, 5, 4, ROBE_LIGHT)
        # Teal sash/belt
        draw.rectangle([body_cx - 7, body_cy + 3, body_cx + 7, body_cy + 5],
                       fill=TEAL, outline=OUTLINE)

        # --- Arms ---
        # Left arm (holding staff)
        draw.rectangle([body_cx - 9, body_cy - 3, body_cx - 6, body_cy + 3],
                       fill=ROBE, outline=OUTLINE)
        # Right arm
        draw.rectangle([body_cx + 6, body_cy - 3, body_cx + 9, body_cy + 3],
                       fill=ROBE, outline=OUTLINE)
        # Sleeve accents
        ellipse(draw, body_cx - 7, body_cy - 3, 3, 2, ROBE_LIGHT)
        ellipse(draw, body_cx + 7, body_cy - 3, 3, 2, ROBE_LIGHT)
        # Teal hand glow
        draw_hand_glow(draw, body_cx - 9, body_cy + 3)
        draw_hand_glow(draw, body_cx + 9, body_cy + 3)

        # --- Staff (left side) ---
        draw.rectangle([body_cx - 11, body_cy - 12, body_cx - 10, body_cy + 2],
                       fill=STAFF_BROWN, outline=OUTLINE)
        # Dramatic water orb on top of staff
        draw_staff_orb(draw, body_cx - 10, body_cy - 15)

        # --- Head (hooded) ---
        # Hood
        ellipse(draw, body_cx, head_cy, 8, 7, ROBE)
        # Face
        ellipse(draw, body_cx, head_cy + 2, 5, 4, SKIN)
        # Hood brim
        draw.arc([body_cx - 8, head_cy - 7, body_cx + 8, head_cy + 1],
                 start=0, end=180, fill=ROBE_DARK)
        # Eyes -- glowing teal
        draw.rectangle([body_cx - 3, head_cy + 1, body_cx - 1, head_cy + 3], fill=TEAL_LIGHT)
        draw.rectangle([body_cx + 1, head_cy + 1, body_cx + 3, head_cy + 3], fill=TEAL_LIGHT)
        # Hood peak
        draw.polygon([(body_cx - 2, head_cy - 7), (body_cx, head_cy - 10),
                      (body_cx + 2, head_cy - 7)], fill=ROBE_DARK, outline=OUTLINE)
        # Hood gem at forehead
        draw_hood_gem(draw, body_cx, head_cy - 5)

        # --- Water droplets ---
        draw_water_droplets(draw, body_cx, body_cy, DROPLET_OFFSETS_DOWN[frame])
        # --- Foot splash ---
        draw_foot_splash(draw, body_cx, base_y, SPLASH_DOWN[frame])

    elif direction == UP:
        # --- Legs (flared robe bottom) ---
        lx1 = body_cx - 5 + leg_spread
        lx2 = body_cx - 1 + leg_spread
        draw.polygon([(lx1 - 1, base_y), (lx1 + 1, body_cy + 6),
                       (lx2, body_cy + 6), (lx2 + 1, base_y)],
                     fill=ROBE_DARK, outline=OUTLINE)
        rx1 = body_cx + 1 - leg_spread
        rx2 = body_cx + 5 - leg_spread
        draw.polygon([(rx1 - 1, base_y), (rx1, body_cy + 6),
                       (rx2 - 1, body_cy + 6), (rx2 + 1, base_y)],
                     fill=ROBE_DARK, outline=OUTLINE)
        # Wavy hem
        draw_robe_hem_wave(draw, body_cx - 6, body_cx + 6, base_y - 1, frame)

        # --- Robe back (flowing) ---
        robe_sway = [0, 1, 0, -1][frame]
        draw.rounded_rectangle([body_cx - 6 + robe_sway, body_cy - 2,
                                body_cx + 6 + robe_sway, body_cy + 7],
                               radius=3, fill=ROBE, outline=OUTLINE)
        draw.rounded_rectangle([body_cx - 5 + robe_sway, body_cy - 1,
                                body_cx + 5 + robe_sway, body_cy + 6],
                               radius=2, fill=ROBE_DARK, outline=None)

        # --- Body ---
        ellipse(draw, body_cx, body_cy, 7, 6, ROBE)
        ellipse(draw, body_cx, body_cy - 1, 5, 4, ROBE_DARK)

        # --- Arms ---
        draw.rectangle([body_cx - 9, body_cy - 3, body_cx - 6, body_cy + 3],
                       fill=ROBE, outline=OUTLINE)
        draw.rectangle([body_cx + 6, body_cy - 3, body_cx + 9, body_cy + 3],
                       fill=ROBE, outline=OUTLINE)
        ellipse(draw, body_cx - 7, body_cy - 3, 3, 2, ROBE_LIGHT)
        ellipse(draw, body_cx + 7, body_cy - 3, 3, 2, ROBE_LIGHT)
        # Teal hand glow
        draw_hand_glow(draw, body_cx - 9, body_cy + 3)
        draw_hand_glow(draw, body_cx + 9, body_cy + 3)

        # --- Staff ---
        draw.rectangle([body_cx - 11, body_cy - 12, body_cx - 10, body_cy + 2],
                       fill=STAFF_BROWN, outline=OUTLINE)
        draw_staff_orb(draw, body_cx - 10, body_cy - 15)

        # --- Head (back of hood) ---
        ellipse(draw, body_cx, head_cy, 8, 7, ROBE)
        ellipse(draw, body_cx, head_cy, 6, 5, ROBE_DARK)
        # Hood peak
        draw.polygon([(body_cx - 2, head_cy - 7), (body_cx, head_cy - 10),
                      (body_cx + 2, head_cy - 7)], fill=ROBE_DARK, outline=OUTLINE)
        # Hood gem (visible from behind as a glow at top)
        draw.point((body_cx, head_cy - 5), fill=GEM_CORE)

        # --- Water droplets ---
        draw_water_droplets(draw, body_cx, body_cy, DROPLET_OFFSETS_UP[frame])
        # --- Foot splash ---
        draw_foot_splash(draw, body_cx, base_y, SPLASH_UP[frame])

    elif direction == LEFT:
        # --- Legs (flared robe bottom) ---
        # Back leg
        bx1 = body_cx - 1 - leg_spread
        bx2 = body_cx + 2 - leg_spread
        draw.polygon([(bx1 - 1, base_y), (bx1, body_cy + 6),
                       (bx2, body_cy + 6), (bx2 + 1, base_y)],
                     fill=ROBE_DARK, outline=OUTLINE)
        # Front leg
        fx1 = body_cx - 4 + leg_spread
        fx2 = body_cx - 1 + leg_spread
        draw.polygon([(fx1 - 1, base_y), (fx1, body_cy + 6),
                       (fx2, body_cy + 6), (fx2 + 1, base_y)],
                     fill=ROBE, outline=OUTLINE)
        # Wavy hem
        draw_robe_hem_wave(draw, body_cx - 6, body_cx + 3, base_y - 1, frame)

        # --- Robe back ---
        robe_sway = [0, 1, 0, -1][frame]
        draw.rounded_rectangle([body_cx + 3, body_cy - 3,
                                body_cx + 8 + robe_sway, body_cy + 6],
                               radius=3, fill=ROBE, outline=OUTLINE)

        # --- Body ---
        ellipse(draw, body_cx - 1, body_cy, 6, 6, ROBE)
        ellipse(draw, body_cx - 1, body_cy - 1, 4, 4, ROBE_LIGHT)
        # Teal sash
        draw.rectangle([body_cx - 7, body_cy + 3, body_cx + 5, body_cy + 5],
                       fill=TEAL, outline=OUTLINE)

        # --- Arm (front) ---
        draw.rectangle([body_cx - 7, body_cy - 2, body_cx - 4, body_cy + 3],
                       fill=ROBE, outline=OUTLINE)
        ellipse(draw, body_cx - 5, body_cy - 3, 3, 2, ROBE_LIGHT)
        # Teal hand glow
        draw_hand_glow(draw, body_cx - 7, body_cy + 3)

        # --- Staff (in front) ---
        draw.rectangle([body_cx - 8, body_cy - 14, body_cx - 7, body_cy + 2],
                       fill=STAFF_BROWN, outline=OUTLINE)
        draw_staff_orb(draw, body_cx - 7, body_cy - 17)

        # --- Head (side, facing left) ---
        ellipse(draw, body_cx - 1, head_cy, 7, 7, ROBE)
        ellipse(draw, body_cx - 3, head_cy + 2, 4, 3, SKIN)
        draw.arc([body_cx - 8, head_cy - 7, body_cx + 4, head_cy + 1],
                 start=0, end=180, fill=ROBE_DARK)
        # Eye -- glowing teal
        draw.rectangle([body_cx - 5, head_cy + 1, body_cx - 3, head_cy + 3], fill=TEAL_LIGHT)
        # Hood peak
        draw.polygon([(body_cx - 3, head_cy - 7), (body_cx - 1, head_cy - 10),
                      (body_cx + 1, head_cy - 7)], fill=ROBE_DARK, outline=OUTLINE)
        # Hood gem
        draw_hood_gem(draw, body_cx - 2, head_cy - 5)

        # --- Water droplets ---
        draw_water_droplets(draw, body_cx, body_cy, DROPLET_OFFSETS_LEFT[frame])
        # --- Foot splash ---
        draw_foot_splash(draw, body_cx, base_y, SPLASH_LEFT[frame])

    elif direction == RIGHT:
        # --- Legs (flared robe bottom) ---
        # Back leg
        bx1 = body_cx - 1 + leg_spread
        bx2 = body_cx + 2 + leg_spread
        draw.polygon([(bx1 - 1, base_y), (bx1, body_cy + 6),
                       (bx2, body_cy + 6), (bx2 + 1, base_y)],
                     fill=ROBE_DARK, outline=OUTLINE)
        # Front leg
        fx1 = body_cx + 2 - leg_spread
        fx2 = body_cx + 5 - leg_spread
        draw.polygon([(fx1 - 1, base_y), (fx1, body_cy + 6),
                       (fx2, body_cy + 6), (fx2 + 1, base_y)],
                     fill=ROBE, outline=OUTLINE)
        # Wavy hem
        draw_robe_hem_wave(draw, body_cx - 3, body_cx + 6, base_y - 1, frame)

        # --- Robe back ---
        robe_sway = [0, -1, 0, 1][frame]
        draw.rounded_rectangle([body_cx - 8 + robe_sway, body_cy - 3,
                                body_cx - 3, body_cy + 6],
                               radius=3, fill=ROBE, outline=OUTLINE)

        # --- Body ---
        ellipse(draw, body_cx + 1, body_cy, 6, 6, ROBE)
        ellipse(draw, body_cx + 1, body_cy - 1, 4, 4, ROBE_LIGHT)
        # Teal sash
        draw.rectangle([body_cx - 5, body_cy + 3, body_cx + 7, body_cy + 5],
                       fill=TEAL, outline=OUTLINE)

        # --- Arm ---
        draw.rectangle([body_cx + 4, body_cy - 2, body_cx + 7, body_cy + 3],
                       fill=ROBE, outline=OUTLINE)
        ellipse(draw, body_cx + 5, body_cy - 3, 3, 2, ROBE_LIGHT)
        # Teal hand glow
        draw_hand_glow(draw, body_cx + 7, body_cy + 3)

        # --- Staff (in front) ---
        draw.rectangle([body_cx + 7, body_cy - 14, body_cx + 8, body_cy + 2],
                       fill=STAFF_BROWN, outline=OUTLINE)
        draw_staff_orb(draw, body_cx + 7, body_cy - 17)

        # --- Head (side, facing right) ---
        ellipse(draw, body_cx + 1, head_cy, 7, 7, ROBE)
        ellipse(draw, body_cx + 3, head_cy + 2, 4, 3, SKIN)
        draw.arc([body_cx - 4, head_cy - 7, body_cx + 8, head_cy + 1],
                 start=0, end=180, fill=ROBE_DARK)
        # Eye -- glowing teal
        draw.rectangle([body_cx + 3, head_cy + 1, body_cx + 5, head_cy + 3], fill=TEAL_LIGHT)
        # Hood peak
        draw.polygon([(body_cx - 1, head_cy - 7), (body_cx + 1, head_cy - 10),
                      (body_cx + 3, head_cy - 7)], fill=ROBE_DARK, outline=OUTLINE)
        # Hood gem
        draw_hood_gem(draw, body_cx + 2, head_cy - 5)

        # --- Water droplets ---
        draw_water_droplets(draw, body_cx, body_cy, DROPLET_OFFSETS_RIGHT[frame])
        # --- Foot splash ---
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
