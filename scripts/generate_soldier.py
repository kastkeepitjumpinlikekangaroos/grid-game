#!/usr/bin/env python3
"""Generate sprites/soldier.png — 4-column x 4-row character spritesheet.

128x128 PNG, 32x32 per frame.
Row layout: Down=0, Up=1, Left=2, Right=3
4 walking animation frames per direction.

Style matches Spaceman/Gladiator: big round head, round body, small limbs, dark outlines.
Theme: Military soldier — olive green helmet, camo torso, brown combat boots, ammo belt.
"""

from PIL import Image, ImageDraw

FRAME_SIZE = 32
COLS = 4
ROWS = 4
IMG_W = FRAME_SIZE * COLS   # 128
IMG_H = FRAME_SIZE * ROWS   # 128

# Colors
OUTLINE = (40, 35, 35)
SKIN = (210, 175, 135)
SKIN_DARK = (180, 145, 110)
OLIVE = (85, 105, 55)
OLIVE_LIGHT = (110, 135, 70)
OLIVE_DARK = (60, 75, 40)
CAMO_SPOT = (70, 85, 45)
BROWN = (100, 70, 40)
BROWN_DARK = (70, 50, 30)
BROWN_LIGHT = (130, 95, 55)
BELT = (55, 50, 40)
BELT_BUCKLE = (200, 175, 60)
BLACK = (30, 30, 30)
METAL = (140, 140, 150)
METAL_DARK = (100, 100, 110)

DOWN, UP, LEFT, RIGHT = 0, 1, 2, 3


def ellipse(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=outline)


def draw_soldier(draw, ox, oy, direction, frame):
    bob = [0, -1, 0, -1][frame]
    leg_spread = [-2, 0, 2, 0][frame]

    base_y = oy + 27 + bob
    body_cx = ox + 16
    body_cy = base_y - 10
    head_cy = body_cy - 10

    if direction == DOWN:
        # Legs — brown combat boots
        draw.rectangle([body_cx - 5 + leg_spread, body_cy + 5,
                        body_cx - 2 + leg_spread, base_y], fill=OLIVE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, body_cy + 5,
                        body_cx + 5 - leg_spread, base_y], fill=OLIVE_DARK, outline=OUTLINE)
        # Boots
        draw.rectangle([body_cx - 5 + leg_spread, base_y - 3,
                        body_cx - 2 + leg_spread, base_y], fill=BROWN_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, base_y - 3,
                        body_cx + 5 - leg_spread, base_y], fill=BROWN_DARK, outline=OUTLINE)

        # Body — camo torso
        ellipse(draw, body_cx, body_cy, 7, 6, OLIVE)
        # Camo spots
        draw.rectangle([body_cx - 4, body_cy - 3, body_cx - 1, body_cy - 1], fill=CAMO_SPOT)
        draw.rectangle([body_cx + 1, body_cy, body_cx + 4, body_cy + 2], fill=OLIVE_DARK)
        # Ammo belt
        draw.rectangle([body_cx - 7, body_cy + 3, body_cx + 7, body_cy + 5],
                       fill=BELT, outline=OUTLINE)
        # Belt buckle
        draw.rectangle([body_cx - 1, body_cy + 3, body_cx + 1, body_cy + 5], fill=BELT_BUCKLE)

        # Arms
        draw.rectangle([body_cx - 9, body_cy - 3, body_cx - 6, body_cy + 3],
                       fill=OLIVE, outline=OUTLINE)
        draw.rectangle([body_cx + 6, body_cy - 3, body_cx + 9, body_cy + 3],
                       fill=OLIVE, outline=OUTLINE)
        # Gloves
        draw.rectangle([body_cx - 9, body_cy + 1, body_cx - 6, body_cy + 3],
                       fill=BROWN, outline=OUTLINE)
        draw.rectangle([body_cx + 6, body_cy + 1, body_cx + 9, body_cy + 3],
                       fill=BROWN, outline=OUTLINE)

        # Head — olive helmet
        ellipse(draw, body_cx, head_cy, 8, 7, OLIVE)
        # Helmet brim
        draw.rectangle([body_cx - 9, head_cy + 2, body_cx + 9, head_cy + 4],
                       fill=OLIVE_DARK, outline=OUTLINE)
        # Face
        ellipse(draw, body_cx, head_cy + 2, 5, 4, SKIN)
        # Eyes
        draw.rectangle([body_cx - 3, head_cy + 1, body_cx - 1, head_cy + 3], fill=BLACK)
        draw.rectangle([body_cx + 1, head_cy + 1, body_cx + 3, head_cy + 3], fill=BLACK)
        # Helmet band
        draw.rectangle([body_cx - 7, head_cy - 1, body_cx + 7, head_cy + 1],
                       fill=OLIVE_DARK, outline=None)

    elif direction == UP:
        # Legs
        draw.rectangle([body_cx - 5 + leg_spread, body_cy + 5,
                        body_cx - 2 + leg_spread, base_y], fill=OLIVE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, body_cy + 5,
                        body_cx + 5 - leg_spread, base_y], fill=OLIVE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 5 + leg_spread, base_y - 3,
                        body_cx - 2 + leg_spread, base_y], fill=BROWN_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, base_y - 3,
                        body_cx + 5 - leg_spread, base_y], fill=BROWN_DARK, outline=OUTLINE)

        # Backpack
        pack_sway = [0, 1, 0, -1][frame]
        draw.rounded_rectangle([body_cx - 5 + pack_sway, body_cy - 4,
                                body_cx + 5 + pack_sway, body_cy + 5],
                               radius=2, fill=OLIVE_DARK, outline=OUTLINE)
        draw.rounded_rectangle([body_cx - 4 + pack_sway, body_cy - 3,
                                body_cx + 4 + pack_sway, body_cy + 4],
                               radius=2, fill=BROWN, outline=None)

        # Body
        ellipse(draw, body_cx, body_cy, 7, 6, OLIVE)
        ellipse(draw, body_cx, body_cy - 1, 5, 4, OLIVE_DARK)

        # Arms
        draw.rectangle([body_cx - 9, body_cy - 3, body_cx - 6, body_cy + 3],
                       fill=OLIVE, outline=OUTLINE)
        draw.rectangle([body_cx + 6, body_cy - 3, body_cx + 9, body_cy + 3],
                       fill=OLIVE, outline=OUTLINE)

        # Head (back of helmet)
        ellipse(draw, body_cx, head_cy, 8, 7, OLIVE)
        ellipse(draw, body_cx, head_cy, 6, 5, OLIVE_DARK)
        # Helmet band
        draw.rectangle([body_cx - 7, head_cy - 1, body_cx + 7, head_cy + 1],
                       fill=OLIVE_DARK, outline=None)

    elif direction == LEFT:
        # Legs (side view)
        draw.rectangle([body_cx - 1 - leg_spread, body_cy + 5,
                        body_cx + 2 - leg_spread, base_y], fill=OLIVE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 1 - leg_spread, base_y - 3,
                        body_cx + 2 - leg_spread, base_y], fill=BROWN_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 4 + leg_spread, body_cy + 5,
                        body_cx - 1 + leg_spread, base_y], fill=OLIVE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 4 + leg_spread, base_y - 3,
                        body_cx - 1 + leg_spread, base_y], fill=BROWN_DARK, outline=OUTLINE)

        # Body
        ellipse(draw, body_cx - 1, body_cy, 6, 6, OLIVE)
        ellipse(draw, body_cx - 1, body_cy - 1, 4, 4, OLIVE_LIGHT)
        # Belt
        draw.rectangle([body_cx - 7, body_cy + 3, body_cx + 5, body_cy + 5],
                       fill=BELT, outline=OUTLINE)

        # Arm (front)
        draw.rectangle([body_cx - 7, body_cy - 2, body_cx - 4, body_cy + 3],
                       fill=OLIVE, outline=OUTLINE)
        # Glove
        draw.rectangle([body_cx - 7, body_cy + 1, body_cx - 4, body_cy + 3],
                       fill=BROWN, outline=OUTLINE)

        # Head (side, facing left)
        ellipse(draw, body_cx - 1, head_cy, 7, 7, OLIVE)
        # Face
        ellipse(draw, body_cx - 3, head_cy + 2, 4, 3, SKIN)
        # Helmet brim
        draw.rectangle([body_cx - 9, head_cy + 2, body_cx + 4, head_cy + 4],
                       fill=OLIVE_DARK, outline=OUTLINE)
        # Eye
        draw.rectangle([body_cx - 5, head_cy + 1, body_cx - 3, head_cy + 3], fill=BLACK)
        # Helmet band
        draw.rectangle([body_cx - 7, head_cy - 1, body_cx + 5, head_cy + 1],
                       fill=OLIVE_DARK, outline=None)

    elif direction == RIGHT:
        # Legs
        draw.rectangle([body_cx - 1 + leg_spread, body_cy + 5,
                        body_cx + 2 + leg_spread, base_y], fill=OLIVE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 1 + leg_spread, base_y - 3,
                        body_cx + 2 + leg_spread, base_y], fill=BROWN_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, body_cy + 5,
                        body_cx + 5 - leg_spread, base_y], fill=OLIVE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, base_y - 3,
                        body_cx + 5 - leg_spread, base_y], fill=BROWN_DARK, outline=OUTLINE)

        # Body
        ellipse(draw, body_cx + 1, body_cy, 6, 6, OLIVE)
        ellipse(draw, body_cx + 1, body_cy - 1, 4, 4, OLIVE_LIGHT)
        # Belt
        draw.rectangle([body_cx - 5, body_cy + 3, body_cx + 7, body_cy + 5],
                       fill=BELT, outline=OUTLINE)

        # Arm
        draw.rectangle([body_cx + 4, body_cy - 2, body_cx + 7, body_cy + 3],
                       fill=OLIVE, outline=OUTLINE)
        draw.rectangle([body_cx + 4, body_cy + 1, body_cx + 7, body_cy + 3],
                       fill=BROWN, outline=OUTLINE)

        # Head
        ellipse(draw, body_cx + 1, head_cy, 7, 7, OLIVE)
        ellipse(draw, body_cx + 3, head_cy + 2, 4, 3, SKIN)
        draw.rectangle([body_cx - 4, head_cy + 2, body_cx + 9, head_cy + 4],
                       fill=OLIVE_DARK, outline=OUTLINE)
        # Eye
        draw.rectangle([body_cx + 3, head_cy + 1, body_cx + 5, head_cy + 3], fill=BLACK)
        # Helmet band
        draw.rectangle([body_cx - 5, head_cy - 1, body_cx + 7, head_cy + 1],
                       fill=OLIVE_DARK, outline=None)


def main():
    img = Image.new("RGBA", (IMG_W, IMG_H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    for direction in range(ROWS):
        for frame in range(COLS):
            ox = frame * FRAME_SIZE
            oy = direction * FRAME_SIZE
            draw_soldier(draw, ox, oy, direction, frame)

    img.save("sprites/soldier.png")
    print(f"Generated sprites/soldier.png ({IMG_W}x{IMG_H})")


if __name__ == "__main__":
    main()
