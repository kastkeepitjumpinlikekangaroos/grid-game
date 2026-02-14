#!/usr/bin/env python3
"""Generate sprites/tidecaller.png — 4-column x 4-row character spritesheet.

128x128 PNG, 32x32 per frame.
Row layout: Down=0, Up=1, Left=2, Right=3
4 walking animation frames per direction.

Style matches Spaceman/Gladiator: big round head, round body, small limbs, dark outlines.
Theme: Water mage — flowing blue robes, staff with water orb, blue/teal palette.
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
TEAL_DARK = (25, 120, 120)
WATER_ORB = (100, 200, 240)
WATER_GLOW = (150, 230, 255)
STAFF_BROWN = (90, 65, 40)
STAFF_DARK = (60, 45, 30)
BLACK = (30, 30, 30)
WHITE_GLINT = (220, 240, 255)

DOWN, UP, LEFT, RIGHT = 0, 1, 2, 3


def ellipse(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    """Draw an outlined ellipse centered at (cx, cy)."""
    draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=outline)


def pill(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    """Draw a rounded rectangle (pill shape)."""
    draw.rounded_rectangle([cx - rx, cy - ry, cx + rx, cy + ry],
                           radius=min(rx, ry), fill=fill, outline=outline)


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
        # --- Legs (behind robe) ---
        draw.rectangle([body_cx - 4 + leg_spread, body_cy + 6,
                        body_cx - 1 + leg_spread, base_y], fill=ROBE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 1 - leg_spread, body_cy + 6,
                        body_cx + 4 - leg_spread, base_y], fill=ROBE_DARK, outline=OUTLINE)

        # --- Body (flowing robe) ---
        # Robe body — wider at bottom for flowing effect
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

        # --- Staff (left side) ---
        draw.rectangle([body_cx - 11, body_cy - 12, body_cx - 10, body_cy + 2],
                       fill=STAFF_BROWN, outline=OUTLINE)
        # Water orb on top of staff
        ellipse(draw, body_cx - 10, body_cy - 14, 3, 3, WATER_ORB)
        draw.point((body_cx - 11, body_cy - 15), fill=WATER_GLOW)

        # --- Head (hooded) ---
        # Hood
        ellipse(draw, body_cx, head_cy, 8, 7, ROBE)
        # Face
        ellipse(draw, body_cx, head_cy + 2, 5, 4, SKIN)
        # Hood brim
        draw.arc([body_cx - 8, head_cy - 7, body_cx + 8, head_cy + 1],
                 start=0, end=180, fill=ROBE_DARK)
        # Eyes — glowing teal
        draw.rectangle([body_cx - 3, head_cy + 1, body_cx - 1, head_cy + 3], fill=TEAL_LIGHT)
        draw.rectangle([body_cx + 1, head_cy + 1, body_cx + 3, head_cy + 3], fill=TEAL_LIGHT)
        # Hood peak
        draw.polygon([(body_cx - 2, head_cy - 7), (body_cx, head_cy - 10),
                      (body_cx + 2, head_cy - 7)], fill=ROBE_DARK, outline=OUTLINE)

    elif direction == UP:
        # --- Legs ---
        draw.rectangle([body_cx - 4 + leg_spread, body_cy + 6,
                        body_cx - 1 + leg_spread, base_y], fill=ROBE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 1 - leg_spread, body_cy + 6,
                        body_cx + 4 - leg_spread, base_y], fill=ROBE_DARK, outline=OUTLINE)

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

        # --- Staff ---
        draw.rectangle([body_cx - 11, body_cy - 12, body_cx - 10, body_cy + 2],
                       fill=STAFF_BROWN, outline=OUTLINE)
        ellipse(draw, body_cx - 10, body_cy - 14, 3, 3, WATER_ORB)

        # --- Head (back of hood) ---
        ellipse(draw, body_cx, head_cy, 8, 7, ROBE)
        ellipse(draw, body_cx, head_cy, 6, 5, ROBE_DARK)
        # Hood peak
        draw.polygon([(body_cx - 2, head_cy - 7), (body_cx, head_cy - 10),
                      (body_cx + 2, head_cy - 7)], fill=ROBE_DARK, outline=OUTLINE)

    elif direction == LEFT:
        # --- Legs ---
        draw.rectangle([body_cx - 1 - leg_spread, body_cy + 6,
                        body_cx + 2 - leg_spread, base_y], fill=ROBE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 4 + leg_spread, body_cy + 6,
                        body_cx - 1 + leg_spread, base_y], fill=ROBE, outline=OUTLINE)

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

        # --- Staff (in front) ---
        draw.rectangle([body_cx - 8, body_cy - 14, body_cx - 7, body_cy + 2],
                       fill=STAFF_BROWN, outline=OUTLINE)
        ellipse(draw, body_cx - 7, body_cy - 16, 3, 3, WATER_ORB)
        draw.point((body_cx - 8, body_cy - 17), fill=WATER_GLOW)

        # --- Head (side, facing left) ---
        ellipse(draw, body_cx - 1, head_cy, 7, 7, ROBE)
        ellipse(draw, body_cx - 3, head_cy + 2, 4, 3, SKIN)
        draw.arc([body_cx - 8, head_cy - 7, body_cx + 4, head_cy + 1],
                 start=0, end=180, fill=ROBE_DARK)
        # Eye — glowing teal
        draw.rectangle([body_cx - 5, head_cy + 1, body_cx - 3, head_cy + 3], fill=TEAL_LIGHT)
        # Hood peak
        draw.polygon([(body_cx - 3, head_cy - 7), (body_cx - 1, head_cy - 10),
                      (body_cx + 1, head_cy - 7)], fill=ROBE_DARK, outline=OUTLINE)

    elif direction == RIGHT:
        # --- Legs ---
        draw.rectangle([body_cx - 1 + leg_spread, body_cy + 6,
                        body_cx + 2 + leg_spread, base_y], fill=ROBE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, body_cy + 6,
                        body_cx + 5 - leg_spread, base_y], fill=ROBE, outline=OUTLINE)

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

        # --- Staff (in front) ---
        draw.rectangle([body_cx + 7, body_cy - 14, body_cx + 8, body_cy + 2],
                       fill=STAFF_BROWN, outline=OUTLINE)
        ellipse(draw, body_cx + 7, body_cy - 16, 3, 3, WATER_ORB)
        draw.point((body_cx + 8, body_cy - 17), fill=WATER_GLOW)

        # --- Head (side, facing right) ---
        ellipse(draw, body_cx + 1, head_cy, 7, 7, ROBE)
        ellipse(draw, body_cx + 3, head_cy + 2, 4, 3, SKIN)
        draw.arc([body_cx - 4, head_cy - 7, body_cx + 8, head_cy + 1],
                 start=0, end=180, fill=ROBE_DARK)
        # Eye — glowing teal
        draw.rectangle([body_cx + 3, head_cy + 1, body_cx + 5, head_cy + 3], fill=TEAL_LIGHT)
        # Hood peak
        draw.polygon([(body_cx - 1, head_cy - 7), (body_cx + 1, head_cy - 10),
                      (body_cx + 3, head_cy - 7)], fill=ROBE_DARK, outline=OUTLINE)


def main():
    img = Image.new("RGBA", (IMG_W, IMG_H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    for direction in range(ROWS):
        for frame in range(COLS):
            ox = frame * FRAME_SIZE
            oy = direction * FRAME_SIZE
            draw_tidecaller(draw, ox, oy, direction, frame)

    img.save("sprites/tidecaller.png")
    print(f"Generated sprites/tidecaller.png ({IMG_W}x{IMG_H})")


if __name__ == "__main__":
    main()
