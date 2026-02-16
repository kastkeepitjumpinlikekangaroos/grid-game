#!/usr/bin/env python3
"""Generate sprites/soldier.png — 4-column x 4-row character spritesheet.

128x128 PNG, 32x32 per frame.
Row layout: Down=0, Up=1, Left=2, Right=3
4 walking animation frames per direction.

Style matches Spaceman/Gladiator: big round head, round body, small limbs, dark outlines.
Theme: Military soldier — olive green helmet with chin strap, camo torso with tactical vest,
brown combat boots with knee pads, ammo belt with pouches, rifle, dog tags.
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
CAMO_BROWN = (95, 80, 45)
CAMO_TAN = (120, 110, 65)
CAMO_DARK = (50, 60, 35)
BROWN = (100, 70, 40)
BROWN_DARK = (70, 50, 30)
BROWN_LIGHT = (130, 95, 55)
BELT = (55, 50, 40)
BELT_BUCKLE = (200, 175, 60)
POUCH = (75, 65, 45)
POUCH_DARK = (55, 48, 32)
BLACK = (30, 30, 30)
METAL = (140, 140, 150)
METAL_DARK = (100, 100, 110)
METAL_BRIGHT = (190, 195, 205)
GUN_BARREL = (65, 65, 70)
GUN_STOCK = (80, 55, 35)
GUN_BODY = (55, 55, 60)
VEST_POCKET = (75, 95, 50)
KNEE_PAD = (90, 110, 60)
STRAP = (80, 65, 45)
DOG_TAG = (200, 205, 215)

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
        # Legs — olive pants
        draw.rectangle([body_cx - 5 + leg_spread, body_cy + 5,
                        body_cx - 2 + leg_spread, base_y], fill=OLIVE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, body_cy + 5,
                        body_cx + 5 - leg_spread, base_y], fill=OLIVE_DARK, outline=OUTLINE)
        # Knee pads
        draw.rectangle([body_cx - 5 + leg_spread, body_cy + 6,
                        body_cx - 2 + leg_spread, body_cy + 8], fill=KNEE_PAD)
        draw.rectangle([body_cx + 2 - leg_spread, body_cy + 6,
                        body_cx + 5 - leg_spread, body_cy + 8], fill=KNEE_PAD)
        # Boots
        draw.rectangle([body_cx - 5 + leg_spread, base_y - 3,
                        body_cx - 2 + leg_spread, base_y], fill=BROWN_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, base_y - 3,
                        body_cx + 5 - leg_spread, base_y], fill=BROWN_DARK, outline=OUTLINE)

        # Body — camo torso
        ellipse(draw, body_cx, body_cy, 7, 6, OLIVE)
        # Enhanced camo pattern — multiple spots of varying shades
        draw.rectangle([body_cx - 4, body_cy - 3, body_cx - 1, body_cy - 1], fill=CAMO_SPOT)
        draw.rectangle([body_cx + 1, body_cy, body_cx + 4, body_cy + 2], fill=OLIVE_DARK)
        draw.rectangle([body_cx - 2, body_cy + 1, body_cx + 1, body_cy + 3], fill=CAMO_BROWN)
        draw.rectangle([body_cx + 2, body_cy - 3, body_cx + 5, body_cy - 1], fill=CAMO_TAN)
        draw.rectangle([body_cx - 5, body_cy - 1, body_cx - 3, body_cy + 1], fill=CAMO_DARK)

        # Tactical vest pockets — two small rectangles on chest
        draw.rectangle([body_cx - 5, body_cy - 4, body_cx - 2, body_cy - 2],
                       fill=VEST_POCKET, outline=OUTLINE)
        draw.rectangle([body_cx + 2, body_cy - 4, body_cx + 5, body_cy - 2],
                       fill=VEST_POCKET, outline=OUTLINE)

        # Dog tags — bright metallic pixel at neckline
        draw.rectangle([body_cx, body_cy - 5, body_cx + 1, body_cy - 4], fill=DOG_TAG)

        # Ammo belt
        draw.rectangle([body_cx - 7, body_cy + 3, body_cx + 7, body_cy + 5],
                       fill=BELT, outline=OUTLINE)
        # Belt buckle
        draw.rectangle([body_cx - 1, body_cy + 3, body_cx + 1, body_cy + 5], fill=BELT_BUCKLE)
        # Ammo pouches on belt
        draw.rectangle([body_cx - 6, body_cy + 3, body_cx - 4, body_cy + 6],
                       fill=POUCH, outline=OUTLINE)
        draw.rectangle([body_cx + 4, body_cy + 3, body_cx + 6, body_cy + 6],
                       fill=POUCH, outline=OUTLINE)
        draw.rectangle([body_cx + 2, body_cy + 4, body_cx + 3, body_cy + 6],
                       fill=POUCH_DARK)

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

        # Rifle — held in right hand, barrel pointing down-right
        rifle_sway = [0, 0, 1, 0][frame]
        draw.rectangle([body_cx + 9, body_cy - 1, body_cx + 11 + rifle_sway, body_cy + 7],
                       fill=GUN_BODY, outline=OUTLINE)
        # Barrel (extends down)
        draw.rectangle([body_cx + 9, body_cy + 5, body_cx + 10, body_cy + 10],
                       fill=GUN_BARREL)
        # Stock (top)
        draw.rectangle([body_cx + 9, body_cy - 3, body_cx + 11, body_cy - 1],
                       fill=GUN_STOCK, outline=OUTLINE)

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
        # Chin strap — thin line from helmet down to jaw
        draw.line([body_cx - 5, head_cy + 4, body_cx - 5, head_cy + 6], fill=STRAP, width=1)
        draw.line([body_cx + 5, head_cy + 4, body_cx + 5, head_cy + 6], fill=STRAP, width=1)

    elif direction == UP:
        # Legs
        draw.rectangle([body_cx - 5 + leg_spread, body_cy + 5,
                        body_cx - 2 + leg_spread, base_y], fill=OLIVE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, body_cy + 5,
                        body_cx + 5 - leg_spread, base_y], fill=OLIVE_DARK, outline=OUTLINE)
        # Knee pads
        draw.rectangle([body_cx - 5 + leg_spread, body_cy + 6,
                        body_cx - 2 + leg_spread, body_cy + 8], fill=KNEE_PAD)
        draw.rectangle([body_cx + 2 - leg_spread, body_cy + 6,
                        body_cx + 5 - leg_spread, body_cy + 8], fill=KNEE_PAD)
        # Boots
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
        # Backpack straps
        draw.line([body_cx - 3 + pack_sway, body_cy - 4, body_cx - 3, body_cy - 6],
                  fill=STRAP, width=1)
        draw.line([body_cx + 3 + pack_sway, body_cy - 4, body_cx + 3, body_cy - 6],
                  fill=STRAP, width=1)

        # Body
        ellipse(draw, body_cx, body_cy, 7, 6, OLIVE)
        ellipse(draw, body_cx, body_cy - 1, 5, 4, OLIVE_DARK)
        # Camo spots on back
        draw.rectangle([body_cx - 3, body_cy - 2, body_cx, body_cy], fill=CAMO_BROWN)
        draw.rectangle([body_cx + 1, body_cy + 1, body_cx + 4, body_cy + 3], fill=CAMO_DARK)

        # Belt
        draw.rectangle([body_cx - 7, body_cy + 3, body_cx + 7, body_cy + 5],
                       fill=BELT, outline=OUTLINE)
        # Rear pouches
        draw.rectangle([body_cx - 5, body_cy + 3, body_cx - 3, body_cy + 6],
                       fill=POUCH, outline=OUTLINE)
        draw.rectangle([body_cx + 3, body_cy + 3, body_cx + 5, body_cy + 6],
                       fill=POUCH, outline=OUTLINE)

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
        # Rifle behind body — drawn first so body overlaps it
        rifle_bob = [0, -1, 0, -1][frame]
        # Rifle barrel pointing left
        draw.rectangle([body_cx - 12, body_cy - 1 + rifle_bob,
                        body_cx - 3, body_cy + 1 + rifle_bob],
                       fill=GUN_BODY, outline=OUTLINE)
        # Barrel tip (extends further left)
        draw.rectangle([body_cx - 15, body_cy - 1 + rifle_bob,
                        body_cx - 12, body_cy + rifle_bob],
                       fill=GUN_BARREL)
        # Stock (right side)
        draw.rectangle([body_cx - 3, body_cy - 2 + rifle_bob,
                        body_cx, body_cy + 2 + rifle_bob],
                       fill=GUN_STOCK, outline=OUTLINE)

        # Legs (side view)
        draw.rectangle([body_cx - 1 - leg_spread, body_cy + 5,
                        body_cx + 2 - leg_spread, base_y], fill=OLIVE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 1 - leg_spread, base_y - 3,
                        body_cx + 2 - leg_spread, base_y], fill=BROWN_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 4 + leg_spread, body_cy + 5,
                        body_cx - 1 + leg_spread, base_y], fill=OLIVE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 4 + leg_spread, base_y - 3,
                        body_cx - 1 + leg_spread, base_y], fill=BROWN_DARK, outline=OUTLINE)
        # Knee pads (side)
        draw.rectangle([body_cx - 1 - leg_spread, body_cy + 6,
                        body_cx + 2 - leg_spread, body_cy + 8], fill=KNEE_PAD)
        draw.rectangle([body_cx - 4 + leg_spread, body_cy + 6,
                        body_cx - 1 + leg_spread, body_cy + 8], fill=KNEE_PAD)

        # Body
        ellipse(draw, body_cx - 1, body_cy, 6, 6, OLIVE)
        # Camo spots
        draw.rectangle([body_cx - 3, body_cy - 3, body_cx, body_cy - 1], fill=CAMO_SPOT)
        draw.rectangle([body_cx - 5, body_cy, body_cx - 2, body_cy + 2], fill=CAMO_BROWN)
        draw.rectangle([body_cx + 1, body_cy - 2, body_cx + 3, body_cy], fill=CAMO_TAN)
        # Tactical vest pocket (side view)
        draw.rectangle([body_cx - 5, body_cy - 4, body_cx - 2, body_cy - 2],
                       fill=VEST_POCKET, outline=OUTLINE)
        # Belt
        draw.rectangle([body_cx - 7, body_cy + 3, body_cx + 5, body_cy + 5],
                       fill=BELT, outline=OUTLINE)
        # Ammo pouch on belt
        draw.rectangle([body_cx - 6, body_cy + 3, body_cx - 4, body_cy + 6],
                       fill=POUCH, outline=OUTLINE)
        draw.rectangle([body_cx + 2, body_cy + 3, body_cx + 4, body_cy + 6],
                       fill=POUCH, outline=OUTLINE)

        # Arm (front) — holding rifle
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
        # Chin strap
        draw.line([body_cx - 6, head_cy + 4, body_cx - 6, head_cy + 6], fill=STRAP, width=1)

    elif direction == RIGHT:
        # Rifle behind body — drawn first so body overlaps it
        rifle_bob = [0, -1, 0, -1][frame]
        # Rifle barrel pointing right
        draw.rectangle([body_cx + 3, body_cy - 1 + rifle_bob,
                        body_cx + 12, body_cy + 1 + rifle_bob],
                       fill=GUN_BODY, outline=OUTLINE)
        # Barrel tip (extends further right)
        draw.rectangle([body_cx + 12, body_cy - 1 + rifle_bob,
                        body_cx + 15, body_cy + rifle_bob],
                       fill=GUN_BARREL)
        # Stock (left side)
        draw.rectangle([body_cx, body_cy - 2 + rifle_bob,
                        body_cx + 3, body_cy + 2 + rifle_bob],
                       fill=GUN_STOCK, outline=OUTLINE)

        # Legs
        draw.rectangle([body_cx - 1 + leg_spread, body_cy + 5,
                        body_cx + 2 + leg_spread, base_y], fill=OLIVE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 1 + leg_spread, base_y - 3,
                        body_cx + 2 + leg_spread, base_y], fill=BROWN_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, body_cy + 5,
                        body_cx + 5 - leg_spread, base_y], fill=OLIVE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, base_y - 3,
                        body_cx + 5 - leg_spread, base_y], fill=BROWN_DARK, outline=OUTLINE)
        # Knee pads (side)
        draw.rectangle([body_cx - 1 + leg_spread, body_cy + 6,
                        body_cx + 2 + leg_spread, body_cy + 8], fill=KNEE_PAD)
        draw.rectangle([body_cx + 2 - leg_spread, body_cy + 6,
                        body_cx + 5 - leg_spread, body_cy + 8], fill=KNEE_PAD)

        # Body
        ellipse(draw, body_cx + 1, body_cy, 6, 6, OLIVE)
        # Camo spots
        draw.rectangle([body_cx, body_cy - 3, body_cx + 3, body_cy - 1], fill=CAMO_SPOT)
        draw.rectangle([body_cx + 2, body_cy, body_cx + 5, body_cy + 2], fill=CAMO_BROWN)
        draw.rectangle([body_cx - 3, body_cy - 2, body_cx - 1, body_cy], fill=CAMO_TAN)
        # Tactical vest pocket (side view)
        draw.rectangle([body_cx + 2, body_cy - 4, body_cx + 5, body_cy - 2],
                       fill=VEST_POCKET, outline=OUTLINE)
        # Belt
        draw.rectangle([body_cx - 5, body_cy + 3, body_cx + 7, body_cy + 5],
                       fill=BELT, outline=OUTLINE)
        # Ammo pouches on belt
        draw.rectangle([body_cx - 4, body_cy + 3, body_cx - 2, body_cy + 6],
                       fill=POUCH, outline=OUTLINE)
        draw.rectangle([body_cx + 4, body_cy + 3, body_cx + 6, body_cy + 6],
                       fill=POUCH, outline=OUTLINE)

        # Arm — holding rifle
        draw.rectangle([body_cx + 4, body_cy - 2, body_cx + 7, body_cy + 3],
                       fill=OLIVE, outline=OUTLINE)
        # Glove
        draw.rectangle([body_cx + 4, body_cy + 1, body_cx + 7, body_cy + 3],
                       fill=BROWN, outline=OUTLINE)

        # Head
        ellipse(draw, body_cx + 1, head_cy, 7, 7, OLIVE)
        # Face
        ellipse(draw, body_cx + 3, head_cy + 2, 4, 3, SKIN)
        # Helmet brim
        draw.rectangle([body_cx - 4, head_cy + 2, body_cx + 9, head_cy + 4],
                       fill=OLIVE_DARK, outline=OUTLINE)
        # Eye
        draw.rectangle([body_cx + 3, head_cy + 1, body_cx + 5, head_cy + 3], fill=BLACK)
        # Helmet band
        draw.rectangle([body_cx - 5, head_cy - 1, body_cx + 7, head_cy + 1],
                       fill=OLIVE_DARK, outline=None)
        # Chin strap
        draw.line([body_cx + 6, head_cy + 4, body_cx + 6, head_cy + 6], fill=STRAP, width=1)


def main():
    img = Image.new("RGBA", (IMG_W, IMG_H), (0, 0, 0, 0))

    for direction in range(ROWS):
        for frame in range(COLS):
            frame_img = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE), (0, 0, 0, 0))
            frame_draw = ImageDraw.Draw(frame_img)
            draw_soldier(frame_draw, 0, 0, direction, frame)
            img.paste(frame_img, (frame * FRAME_SIZE, direction * FRAME_SIZE))

    img.save("sprites/soldier.png")
    print(f"Generated sprites/soldier.png ({IMG_W}x{IMG_H})")


if __name__ == "__main__":
    main()
