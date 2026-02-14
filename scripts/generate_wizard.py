#!/usr/bin/env python3
"""Generate sprites/wizard.png — 4-column x 4-row character spritesheet.

128x128 PNG, 32x32 per frame.
Row layout: Down=0, Up=1, Left=2, Right=3
4 walking animation frames per direction.

Style matches Spaceman/Gladiator/Wraith: big round head, round body, small limbs.
Theme: Wizard — pointed hat, flowing purple/indigo robes, staff with glowing orb.
Color palette: deep purple/indigo robes, gold hat band, glowing blue-purple staff orb.
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
EYE = (30, 25, 45)
STAFF_WOOD = (100, 65, 30)
STAFF_DARK = (70, 45, 20)
ORB_GLOW = (120, 80, 220)
ORB_BRIGHT = (180, 140, 255)
ORB_CORE = (220, 200, 255)

DOWN, UP, LEFT, RIGHT = 0, 1, 2, 3


def ellipse(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    """Draw an outlined ellipse centered at (cx, cy)."""
    draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=outline)


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
        draw.line([(staff_x, head_cy - 2), (staff_x + sway, body_cy + 8)],
                  fill=STAFF_WOOD, width=2)
        draw.line([(staff_x + 1, head_cy - 2), (staff_x + 1 + sway, body_cy + 8)],
                  fill=STAFF_DARK, width=1)
        # Staff orb
        ellipse(draw, staff_x, head_cy - 4, 3, 3, ORB_GLOW)
        draw.rectangle([staff_x - 1, head_cy - 5, staff_x + 1, head_cy - 3], fill=ORB_BRIGHT)
        draw.point((staff_x, head_cy - 4), fill=ORB_CORE)

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

        # --- Head (face visible) ---
        ellipse(draw, body_cx, head_cy + 1, 7, 6, FACE)
        ellipse(draw, body_cx, head_cy + 2, 5, 4, FACE_SHADOW)

        # Eyes
        draw.rectangle([body_cx - 4, head_cy, body_cx - 2, head_cy + 2], fill=EYE)
        draw.rectangle([body_cx + 2, head_cy, body_cx + 4, head_cy + 2], fill=EYE)

        # Beard
        draw.polygon([
            (body_cx - 3, head_cy + 4),
            (body_cx + 3, head_cy + 4),
            (body_cx + 2, head_cy + 8),
            (body_cx, head_cy + 9),
            (body_cx - 2, head_cy + 8),
        ], fill=BEARD, outline=None)

        # --- Pointed hat ---
        # Hat brim
        ellipse(draw, body_cx, head_cy - 3, 9, 3, HAT)
        # Hat cone
        draw.polygon([
            (body_cx - 6, head_cy - 4),
            (body_cx + 6, head_cy - 4),
            (body_cx + 1, head_cy - 16),
            (body_cx - 1, head_cy - 16),
        ], fill=HAT, outline=OUTLINE)
        # Hat band (gold)
        draw.rectangle([body_cx - 6, head_cy - 5, body_cx + 6, head_cy - 3], fill=HAT_BAND)

    elif direction == UP:
        # --- Staff (behind body, right side) ---
        staff_x = body_cx + 7
        draw.line([(staff_x, head_cy - 2), (staff_x + sway, body_cy + 8)],
                  fill=STAFF_WOOD, width=2)
        # Staff orb
        ellipse(draw, staff_x, head_cy - 4, 3, 3, ORB_GLOW)
        draw.rectangle([staff_x - 1, head_cy - 5, staff_x + 1, head_cy - 3], fill=ORB_BRIGHT)

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

        # --- Head (back view, no face) ---
        ellipse(draw, body_cx, head_cy + 1, 7, 6, FACE_SHADOW)

        # --- Pointed hat (back) ---
        ellipse(draw, body_cx, head_cy - 3, 9, 3, HAT)
        draw.polygon([
            (body_cx - 6, head_cy - 4),
            (body_cx + 6, head_cy - 4),
            (body_cx + 1, head_cy - 16),
            (body_cx - 1, head_cy - 16),
        ], fill=HAT, outline=OUTLINE)
        draw.rectangle([body_cx - 6, head_cy - 5, body_cx + 6, head_cy - 3], fill=HAT_BAND)

    elif direction == LEFT:
        # --- Staff (behind body, to the right/behind) ---
        staff_x = body_cx + 5
        draw.line([(staff_x, head_cy - 1), (staff_x + sway + 1, body_cy + 8)],
                  fill=STAFF_WOOD, width=2)
        ellipse(draw, staff_x, head_cy - 3, 3, 3, ORB_GLOW)
        draw.point((staff_x, head_cy - 3), fill=ORB_CORE)

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

        # --- Head (side, facing left) ---
        ellipse(draw, body_cx - 1, head_cy + 1, 6, 6, FACE)
        ellipse(draw, body_cx - 2, head_cy + 2, 4, 4, FACE_SHADOW)

        # Eye (one visible)
        draw.rectangle([body_cx - 5, head_cy, body_cx - 3, head_cy + 2], fill=EYE)

        # Beard (side view)
        draw.polygon([
            (body_cx - 4, head_cy + 4),
            (body_cx + 1, head_cy + 4),
            (body_cx - 1, head_cy + 8),
            (body_cx - 4, head_cy + 7),
        ], fill=BEARD, outline=None)

        # --- Pointed hat (side, leaning left) ---
        ellipse(draw, body_cx - 1, head_cy - 3, 8, 3, HAT)
        draw.polygon([
            (body_cx - 7, head_cy - 4),
            (body_cx + 5, head_cy - 4),
            (body_cx - 2, head_cy - 16),
            (body_cx - 3, head_cy - 16),
        ], fill=HAT, outline=OUTLINE)
        draw.rectangle([body_cx - 7, head_cy - 5, body_cx + 5, head_cy - 3], fill=HAT_BAND)

    elif direction == RIGHT:
        # --- Staff (behind body, to the left/behind) ---
        staff_x = body_cx - 5
        draw.line([(staff_x, head_cy - 1), (staff_x + sway - 1, body_cy + 8)],
                  fill=STAFF_WOOD, width=2)
        ellipse(draw, staff_x, head_cy - 3, 3, 3, ORB_GLOW)
        draw.point((staff_x, head_cy - 3), fill=ORB_CORE)

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

        # --- Head (side, facing right) ---
        ellipse(draw, body_cx + 1, head_cy + 1, 6, 6, FACE)
        ellipse(draw, body_cx + 2, head_cy + 2, 4, 4, FACE_SHADOW)

        # Eye (one visible)
        draw.rectangle([body_cx + 3, head_cy, body_cx + 5, head_cy + 2], fill=EYE)

        # Beard (side view)
        draw.polygon([
            (body_cx - 1, head_cy + 4),
            (body_cx + 4, head_cy + 4),
            (body_cx + 4, head_cy + 7),
            (body_cx + 1, head_cy + 8),
        ], fill=BEARD, outline=None)

        # --- Pointed hat (side, leaning right) ---
        ellipse(draw, body_cx + 1, head_cy - 3, 8, 3, HAT)
        draw.polygon([
            (body_cx - 5, head_cy - 4),
            (body_cx + 7, head_cy - 4),
            (body_cx + 3, head_cy - 16),
            (body_cx + 2, head_cy - 16),
        ], fill=HAT, outline=OUTLINE)
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
