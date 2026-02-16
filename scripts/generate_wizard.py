#!/usr/bin/env python3
"""Generate sprites/wizard.png — 4-column x 4-row character spritesheet.

128x128 PNG, 32x32 per frame.
Row layout: Down=0, Up=1, Left=2, Right=3
4 walking animation frames per direction.

Style matches Spaceman/Gladiator/Wraith: big round head, round body, small limbs.
Theme: Wizard — curled pointed hat, flowing purple/indigo robes with star embroidery,
staff with pulsing glowing orb and magic sparkles, outlined beard, gold robe hem.
"""

from PIL import Image, ImageDraw

FRAME_SIZE = 32
COLS = 4
ROWS = 4
IMG_W = FRAME_SIZE * COLS   # 128
IMG_H = FRAME_SIZE * ROWS   # 128

# Colors
OUTLINE = (25, 20, 40)
ROBE_DARK = (40, 25, 70)
ROBE = (60, 35, 110)
ROBE_LIGHT = (80, 50, 140)
HAT = (50, 30, 90)
HAT_DARK = (35, 20, 65)
HAT_BAND = (200, 170, 50)      # Gold accent
HAT_BAND_DARK = (160, 130, 30)
FACE = (220, 190, 160)
FACE_SHADOW = (190, 160, 130)
BEARD = (200, 200, 210)
BEARD_DARK = (170, 170, 180)
BEARD_OUTLINE = (120, 120, 135)
EYE = (30, 25, 45)
STAFF_WOOD = (100, 65, 30)
STAFF_DARK = (70, 45, 20)
ORB_GLOW = (120, 80, 220)
ORB_BRIGHT = (180, 140, 255)
ORB_CORE = (220, 200, 255)
ORB_PULSE = (240, 220, 255)       # Extra bright for pulsing frames
ORB_GLOW_SOFT = (100, 65, 200)    # Dimmer glow for normal frames
SPARKLE_A = (255, 255, 200)       # Warm sparkle
SPARKLE_B = (200, 180, 255)       # Cool sparkle
SPARKLE_C = (180, 255, 255)       # Cyan sparkle
HAND_GLOW = (150, 110, 255)       # Magic glow near staff hand
ROBE_STAR = (210, 185, 60)        # Gold embroidered stars on robe
ROBE_HEM = (190, 160, 45)         # Gold trim at robe bottom

DOWN, UP, LEFT, RIGHT = 0, 1, 2, 3


def ellipse(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    """Draw an outlined ellipse centered at (cx, cy)."""
    draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=outline)


# Per-frame sparkle offsets relative to the orb center (dx, dy).
# Each frame gets 3 sparkle positions that shift around to animate.
SPARKLE_OFFSETS = [
    [(-4, -2), (3, -4), (5, 1)],    # frame 0
    [(-3, -5), (5, -1), (-1, 4)],    # frame 1
    [(-5, 0), (2, -5), (4, 3)],      # frame 2
    [(-2, -4), (5, -3), (-4, 3)],    # frame 3
]

# Per-frame robe star offsets (dx, dy) relative to body center.
ROBE_STARS = [
    [(-3, -1), (2, 2), (0, 5)],
    [(-4, 0), (3, 3), (-1, 5)],
    [(-3, 1), (2, 3), (1, 5)],
    [(-4, -1), (3, 2), (-1, 4)],
]


def draw_sparkles(draw, orb_x, orb_y, frame):
    """Draw 3 magic sparkle pixels around the staff orb."""
    colors = [SPARKLE_A, SPARKLE_B, SPARKLE_C]
    for i, (dx, dy) in enumerate(SPARKLE_OFFSETS[frame]):
        draw.point((orb_x + dx, orb_y + dy), fill=colors[i])


def draw_orb(draw, orb_x, orb_y, frame):
    """Draw the staff orb with pulsing brightness across frames."""
    is_pulse = frame in (1, 3)
    if is_pulse:
        # Brighter/slightly larger orb on pulse frames
        ellipse(draw, orb_x, orb_y, 3, 3, ORB_BRIGHT)
        draw.rectangle([orb_x - 1, orb_y - 1, orb_x + 1, orb_y + 1], fill=ORB_PULSE)
        draw.point((orb_x, orb_y), fill=(255, 250, 255))
        # Extra glow pixel above
        draw.point((orb_x, orb_y - 3), fill=ORB_CORE)
    else:
        # Normal orb
        ellipse(draw, orb_x, orb_y, 3, 3, ORB_GLOW)
        draw.rectangle([orb_x - 1, orb_y - 1, orb_x + 1, orb_y + 1], fill=ORB_BRIGHT)
        draw.point((orb_x, orb_y), fill=ORB_CORE)


def draw_hand_glow(draw, hand_x, hand_y):
    """Draw 1-2 bright pixels near the hand holding the staff."""
    draw.point((hand_x, hand_y), fill=HAND_GLOW)
    draw.point((hand_x + 1, hand_y - 1), fill=(130, 95, 230))


def draw_robe_stars(draw, body_cx, body_cy, frame, direction):
    """Draw 2-3 tiny gold dots on the robe body to look like embroidered stars."""
    offsets = ROBE_STARS[frame]
    for dx, dy in offsets:
        sx = body_cx + dx
        sy = body_cy + dy
        draw.point((sx, sy), fill=ROBE_STAR)


def draw_robe_hem(draw, body_cx, body_cy):
    """Draw a gold trim line at the bottom edge of the robe."""
    hem_y = body_cy + 7
    draw.line([(body_cx - 8, hem_y), (body_cx + 8, hem_y)], fill=ROBE_HEM, width=1)


def draw_wizard(draw, ox, oy, direction, frame):
    """Draw a single wizard frame at offset (ox, oy).

    Proportions match other characters: big round head ~11px, body ~8px tall.
    Wizard floats slightly — bob animation like wraith but subtler.
    """
    bob = [0, -1, 0, -1][frame]
    sway = [-1, 0, 1, 0][frame]

    base_y = oy + 28 + bob
    body_cx = ox + 16
    body_cy = base_y - 9
    head_cy = body_cy - 10

    if direction == DOWN:
        # --- Staff (behind body, right side) ---
        staff_x = body_cx + 7
        staff_hand_y = body_cy + 1
        draw.line([(staff_x, head_cy - 2), (staff_x + sway, body_cy + 8)],
                  fill=STAFF_WOOD, width=2)
        draw.line([(staff_x + 1, head_cy - 2), (staff_x + 1 + sway, body_cy + 8)],
                  fill=STAFF_DARK, width=1)
        # Staff orb
        orb_x, orb_y = staff_x, head_cy - 4
        draw_orb(draw, orb_x, orb_y, frame)
        # Magic sparkles
        draw_sparkles(draw, orb_x, orb_y, frame)
        # Hand glow
        draw_hand_glow(draw, staff_x - 1, staff_hand_y)

        # --- Robe body ---
        draw.polygon([
            (body_cx - 7, body_cy - 4),
            (body_cx + 7, body_cy - 4),
            (body_cx + 9, body_cy + 7),
            (body_cx - 9, body_cy + 7),
        ], fill=ROBE, outline=OUTLINE)
        # Robe highlight
        draw.polygon([
            (body_cx - 4, body_cy - 3),
            (body_cx + 4, body_cy - 3),
            (body_cx + 5, body_cy + 5),
            (body_cx - 5, body_cy + 5),
        ], fill=ROBE_LIGHT, outline=None)
        # Robe stars
        draw_robe_stars(draw, body_cx, body_cy, frame, direction)
        # Robe hem
        draw_robe_hem(draw, body_cx, body_cy)

        # --- Head (face visible) ---
        ellipse(draw, body_cx, head_cy + 1, 7, 6, FACE)
        ellipse(draw, body_cx, head_cy + 2, 5, 4, FACE_SHADOW)

        # Eyes
        draw.rectangle([body_cx - 4, head_cy, body_cx - 2, head_cy + 2], fill=EYE)
        draw.rectangle([body_cx + 2, head_cy, body_cx + 4, head_cy + 2], fill=EYE)

        # Beard (longer, with outline)
        draw.polygon([
            (body_cx - 3, head_cy + 4),
            (body_cx + 3, head_cy + 4),
            (body_cx + 2, head_cy + 9),
            (body_cx, head_cy + 11),
            (body_cx - 2, head_cy + 9),
        ], fill=BEARD, outline=BEARD_OUTLINE)
        # Beard shadow line for depth
        draw.line([(body_cx - 1, head_cy + 6), (body_cx + 1, head_cy + 6)],
                  fill=BEARD_DARK, width=1)

        # --- Pointed hat (curled tip) ---
        # Hat brim
        ellipse(draw, body_cx, head_cy - 3, 9, 3, HAT)
        # Hat cone with curled tip (tip offset to the right)
        draw.polygon([
            (body_cx - 6, head_cy - 4),
            (body_cx + 6, head_cy - 4),
            (body_cx + 4, head_cy - 15),
            (body_cx + 3, head_cy - 16),
        ], fill=HAT, outline=OUTLINE)
        # Curl at tip (small extra curve to the right)
        draw.line([(body_cx + 3, head_cy - 16), (body_cx + 6, head_cy - 15)],
                  fill=HAT, width=2)
        draw.point((body_cx + 6, head_cy - 15), fill=OUTLINE)
        # Hat band (gold)
        draw.rectangle([body_cx - 6, head_cy - 5, body_cx + 6, head_cy - 3], fill=HAT_BAND)

    elif direction == UP:
        # --- Staff (behind body, right side) ---
        staff_x = body_cx + 7
        staff_hand_y = body_cy + 1
        draw.line([(staff_x, head_cy - 2), (staff_x + sway, body_cy + 8)],
                  fill=STAFF_WOOD, width=2)
        # Staff orb
        orb_x, orb_y = staff_x, head_cy - 4
        draw_orb(draw, orb_x, orb_y, frame)
        # Magic sparkles
        draw_sparkles(draw, orb_x, orb_y, frame)
        # Hand glow
        draw_hand_glow(draw, staff_x - 1, staff_hand_y)

        # --- Robe body ---
        draw.polygon([
            (body_cx - 7, body_cy - 4),
            (body_cx + 7, body_cy - 4),
            (body_cx + 9, body_cy + 7),
            (body_cx - 9, body_cy + 7),
        ], fill=ROBE, outline=OUTLINE)
        # Back of robe (darker)
        draw.polygon([
            (body_cx - 5, body_cy - 3),
            (body_cx + 5, body_cy - 3),
            (body_cx + 6, body_cy + 5),
            (body_cx - 6, body_cy + 5),
        ], fill=ROBE_DARK, outline=None)
        # Robe stars
        draw_robe_stars(draw, body_cx, body_cy, frame, direction)
        # Robe hem
        draw_robe_hem(draw, body_cx, body_cy)

        # --- Head (back view, no face) ---
        ellipse(draw, body_cx, head_cy + 1, 7, 6, FACE_SHADOW)

        # --- Pointed hat (back, curled tip) ---
        ellipse(draw, body_cx, head_cy - 3, 9, 3, HAT)
        draw.polygon([
            (body_cx - 6, head_cy - 4),
            (body_cx + 6, head_cy - 4),
            (body_cx + 4, head_cy - 15),
            (body_cx + 3, head_cy - 16),
        ], fill=HAT, outline=OUTLINE)
        # Curl at tip
        draw.line([(body_cx + 3, head_cy - 16), (body_cx + 6, head_cy - 15)],
                  fill=HAT, width=2)
        draw.point((body_cx + 6, head_cy - 15), fill=OUTLINE)
        draw.rectangle([body_cx - 6, head_cy - 5, body_cx + 6, head_cy - 3], fill=HAT_BAND)

    elif direction == LEFT:
        # --- Staff (behind body, to the right/behind) ---
        staff_x = body_cx + 5
        staff_hand_y = body_cy + 1
        draw.line([(staff_x, head_cy - 1), (staff_x + sway + 1, body_cy + 8)],
                  fill=STAFF_WOOD, width=2)
        orb_x, orb_y = staff_x, head_cy - 3
        draw_orb(draw, orb_x, orb_y, frame)
        # Magic sparkles
        draw_sparkles(draw, orb_x, orb_y, frame)
        # Hand glow
        draw_hand_glow(draw, staff_x - 1, staff_hand_y)

        # --- Robe body ---
        draw.polygon([
            (body_cx - 6, body_cy - 4),
            (body_cx + 6, body_cy - 4),
            (body_cx + 8, body_cy + 7),
            (body_cx - 8, body_cy + 7),
        ], fill=ROBE, outline=OUTLINE)
        draw.polygon([
            (body_cx - 4, body_cy - 3),
            (body_cx + 3, body_cy - 3),
            (body_cx + 4, body_cy + 5),
            (body_cx - 5, body_cy + 5),
        ], fill=ROBE_LIGHT, outline=None)
        # Robe stars
        draw_robe_stars(draw, body_cx, body_cy, frame, direction)
        # Robe hem
        draw_robe_hem(draw, body_cx, body_cy)

        # --- Head (side, facing left) ---
        ellipse(draw, body_cx - 1, head_cy + 1, 6, 6, FACE)
        ellipse(draw, body_cx - 2, head_cy + 2, 4, 4, FACE_SHADOW)

        # Eye (one visible)
        draw.rectangle([body_cx - 5, head_cy, body_cx - 3, head_cy + 2], fill=EYE)

        # Beard (side view, longer, with outline)
        draw.polygon([
            (body_cx - 4, head_cy + 4),
            (body_cx + 1, head_cy + 4),
            (body_cx - 1, head_cy + 9),
            (body_cx - 4, head_cy + 8),
        ], fill=BEARD, outline=BEARD_OUTLINE)
        draw.line([(body_cx - 3, head_cy + 6), (body_cx, head_cy + 6)],
                  fill=BEARD_DARK, width=1)

        # --- Pointed hat (side, leaning left, curled tip) ---
        ellipse(draw, body_cx - 1, head_cy - 3, 8, 3, HAT)
        draw.polygon([
            (body_cx - 7, head_cy - 4),
            (body_cx + 5, head_cy - 4),
            (body_cx - 4, head_cy - 15),
            (body_cx - 5, head_cy - 16),
        ], fill=HAT, outline=OUTLINE)
        # Curl at tip (curls to the left)
        draw.line([(body_cx - 5, head_cy - 16), (body_cx - 8, head_cy - 14)],
                  fill=HAT, width=2)
        draw.point((body_cx - 8, head_cy - 14), fill=OUTLINE)
        draw.rectangle([body_cx - 7, head_cy - 5, body_cx + 5, head_cy - 3], fill=HAT_BAND)

    elif direction == RIGHT:
        # --- Staff (behind body, to the left/behind) ---
        staff_x = body_cx - 5
        staff_hand_y = body_cy + 1
        draw.line([(staff_x, head_cy - 1), (staff_x + sway - 1, body_cy + 8)],
                  fill=STAFF_WOOD, width=2)
        orb_x, orb_y = staff_x, head_cy - 3
        draw_orb(draw, orb_x, orb_y, frame)
        # Magic sparkles
        draw_sparkles(draw, orb_x, orb_y, frame)
        # Hand glow
        draw_hand_glow(draw, staff_x + 1, staff_hand_y)

        # --- Robe body ---
        draw.polygon([
            (body_cx - 6, body_cy - 4),
            (body_cx + 6, body_cy - 4),
            (body_cx + 8, body_cy + 7),
            (body_cx - 8, body_cy + 7),
        ], fill=ROBE, outline=OUTLINE)
        draw.polygon([
            (body_cx - 3, body_cy - 3),
            (body_cx + 4, body_cy - 3),
            (body_cx + 5, body_cy + 5),
            (body_cx - 4, body_cy + 5),
        ], fill=ROBE_LIGHT, outline=None)
        # Robe stars
        draw_robe_stars(draw, body_cx, body_cy, frame, direction)
        # Robe hem
        draw_robe_hem(draw, body_cx, body_cy)

        # --- Head (side, facing right) ---
        ellipse(draw, body_cx + 1, head_cy + 1, 6, 6, FACE)
        ellipse(draw, body_cx + 2, head_cy + 2, 4, 4, FACE_SHADOW)

        # Eye (one visible)
        draw.rectangle([body_cx + 3, head_cy, body_cx + 5, head_cy + 2], fill=EYE)

        # Beard (side view, longer, with outline)
        draw.polygon([
            (body_cx - 1, head_cy + 4),
            (body_cx + 4, head_cy + 4),
            (body_cx + 4, head_cy + 8),
            (body_cx + 1, head_cy + 9),
        ], fill=BEARD, outline=BEARD_OUTLINE)
        draw.line([(body_cx, head_cy + 6), (body_cx + 3, head_cy + 6)],
                  fill=BEARD_DARK, width=1)

        # --- Pointed hat (side, leaning right, curled tip) ---
        ellipse(draw, body_cx + 1, head_cy - 3, 8, 3, HAT)
        draw.polygon([
            (body_cx - 5, head_cy - 4),
            (body_cx + 7, head_cy - 4),
            (body_cx + 5, head_cy - 15),
            (body_cx + 4, head_cy - 16),
        ], fill=HAT, outline=OUTLINE)
        # Curl at tip (curls to the right)
        draw.line([(body_cx + 4, head_cy - 16), (body_cx + 8, head_cy - 14)],
                  fill=HAT, width=2)
        draw.point((body_cx + 8, head_cy - 14), fill=OUTLINE)
        draw.rectangle([body_cx - 5, head_cy - 5, body_cx + 7, head_cy - 3], fill=HAT_BAND)


def main():
    img = Image.new("RGBA", (IMG_W, IMG_H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    for direction in range(ROWS):
        for frame in range(COLS):
            ox = frame * FRAME_SIZE
            oy = direction * FRAME_SIZE
            draw_wizard(draw, ox, oy, direction, frame)

    img.save("sprites/wizard.png")
    print(f"Generated sprites/wizard.png ({IMG_W}x{IMG_H})")


if __name__ == "__main__":
    main()
