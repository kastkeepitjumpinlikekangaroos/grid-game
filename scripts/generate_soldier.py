#!/usr/bin/env python3
"""Generate sprites/soldier.png — 4-column x 4-row character spritesheet.

256x256 PNG, 64x64 per frame.
Row layout: Down=0, Up=1, Left=2, Right=3
4 walking animation frames per direction.

Style matches Spaceman/Gladiator: big round head, round body, small limbs, dark outlines.
Theme: Military soldier — olive green helmet with chin strap, camo torso with tactical vest,
brown combat boots with knee pads, ammo belt with pouches, rifle, dog tags.
Enhanced 64x64: detailed combat vest with pocket outlines, helmet with chin strap,
ammo belt across chest, boot treads.
"""

from PIL import Image, ImageDraw

FRAME_SIZE = 64
COLS = 4
ROWS = 4
IMG_W = FRAME_SIZE * COLS   # 256
IMG_H = FRAME_SIZE * ROWS   # 256

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
VEST_STITCH = (65, 85, 40)
KNEE_PAD = (90, 110, 60)
STRAP = (80, 65, 45)
DOG_TAG = (200, 205, 215)
BOOT_TREAD = (50, 40, 25)
AMMO_BELT = (90, 80, 50)
AMMO_ROUND = (160, 150, 60)

DOWN, UP, LEFT, RIGHT = 0, 1, 2, 3


def ellipse(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=outline)


def draw_vest_pocket(draw, x1, y1, x2, y2):
    """Draw a detailed vest pocket with stitch outline."""
    draw.rectangle([x1, y1, x2, y2], fill=VEST_POCKET, outline=OUTLINE)
    # Stitch detail
    draw.rectangle([x1 + 1, y1 + 1, x2 - 1, y1 + 2], fill=VEST_STITCH)
    # Flap line
    draw.line([(x1 + 1, y1 + 3), (x2 - 1, y1 + 3)], fill=OLIVE_DARK)


def draw_ammo_belt_detail(draw, x1, y, x2):
    """Draw ammo belt with individual round details."""
    draw.rectangle([x1, y, x2, y + 3], fill=AMMO_BELT, outline=OUTLINE)
    for x in range(x1 + 2, x2 - 1, 4):
        draw.rectangle([x, y + 1, x + 1, y + 2], fill=AMMO_ROUND)


def draw_soldier(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]

    base_y = oy + 54 + bob
    body_cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    if direction == DOWN:
        # Legs
        draw.rectangle([body_cx - 10 + leg_spread, body_cy + 10,
                        body_cx - 4 + leg_spread, base_y], fill=OLIVE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 4 - leg_spread, body_cy + 10,
                        body_cx + 10 - leg_spread, base_y], fill=OLIVE_DARK, outline=OUTLINE)
        # Knee pads
        draw.rectangle([body_cx - 10 + leg_spread, body_cy + 12,
                        body_cx - 4 + leg_spread, body_cy + 16], fill=KNEE_PAD)
        draw.rectangle([body_cx + 4 - leg_spread, body_cy + 12,
                        body_cx + 10 - leg_spread, body_cy + 16], fill=KNEE_PAD)
        # Boots with treads
        draw.rectangle([body_cx - 10 + leg_spread, base_y - 6,
                        body_cx - 4 + leg_spread, base_y], fill=BROWN_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 4 - leg_spread, base_y - 6,
                        body_cx + 10 - leg_spread, base_y], fill=BROWN_DARK, outline=OUTLINE)
        # Boot treads
        draw.line([(body_cx - 9 + leg_spread, base_y - 1), (body_cx - 5 + leg_spread, base_y - 1)], fill=BOOT_TREAD)
        draw.line([(body_cx + 5 - leg_spread, base_y - 1), (body_cx + 9 - leg_spread, base_y - 1)], fill=BOOT_TREAD)

        # Body
        ellipse(draw, body_cx, body_cy, 14, 12, OLIVE)
        # Camo pattern
        draw.rectangle([body_cx - 8, body_cy - 6, body_cx - 2, body_cy - 2], fill=CAMO_SPOT)
        draw.rectangle([body_cx + 2, body_cy, body_cx + 8, body_cy + 4], fill=OLIVE_DARK)
        draw.rectangle([body_cx - 4, body_cy + 2, body_cx + 2, body_cy + 6], fill=CAMO_BROWN)
        draw.rectangle([body_cx + 4, body_cy - 6, body_cx + 10, body_cy - 2], fill=CAMO_TAN)
        draw.rectangle([body_cx - 10, body_cy - 2, body_cx - 6, body_cy + 2], fill=CAMO_DARK)

        # Vest pockets with stitch detail
        draw_vest_pocket(draw, body_cx - 10, body_cy - 8, body_cx - 4, body_cy - 4)
        draw_vest_pocket(draw, body_cx + 4, body_cy - 8, body_cx + 10, body_cy - 4)

        # Dog tags
        draw.rectangle([body_cx, body_cy - 10, body_cx + 2, body_cy - 8], fill=DOG_TAG)
        draw.point((body_cx + 1, body_cy - 11), fill=METAL)

        # Ammo belt across chest
        draw_ammo_belt_detail(draw, body_cx - 12, body_cy - 2, body_cx + 12)

        # Belt
        draw.rectangle([body_cx - 14, body_cy + 6, body_cx + 14, body_cy + 10],
                       fill=BELT, outline=OUTLINE)
        draw.rectangle([body_cx - 2, body_cy + 6, body_cx + 2, body_cy + 10], fill=BELT_BUCKLE)
        # Ammo pouches on belt
        draw.rectangle([body_cx - 12, body_cy + 6, body_cx - 8, body_cy + 12],
                       fill=POUCH, outline=OUTLINE)
        draw.rectangle([body_cx + 8, body_cy + 6, body_cx + 12, body_cy + 12],
                       fill=POUCH, outline=OUTLINE)
        draw.rectangle([body_cx + 4, body_cy + 8, body_cx + 6, body_cy + 12],
                       fill=POUCH_DARK)

        # Arms
        draw.rectangle([body_cx - 18, body_cy - 6, body_cx - 12, body_cy + 6],
                       fill=OLIVE, outline=OUTLINE)
        draw.rectangle([body_cx + 12, body_cy - 6, body_cx + 18, body_cy + 6],
                       fill=OLIVE, outline=OUTLINE)
        # Gloves
        draw.rectangle([body_cx - 18, body_cy + 2, body_cx - 12, body_cy + 6],
                       fill=BROWN, outline=OUTLINE)
        draw.rectangle([body_cx + 12, body_cy + 2, body_cx + 18, body_cy + 6],
                       fill=BROWN, outline=OUTLINE)

        # Rifle
        rifle_sway = [0, 0, 2, 0][frame]
        draw.rectangle([body_cx + 18, body_cy - 2, body_cx + 22 + rifle_sway, body_cy + 14],
                       fill=GUN_BODY, outline=OUTLINE)
        draw.rectangle([body_cx + 18, body_cy + 10, body_cx + 20, body_cy + 20],
                       fill=GUN_BARREL)
        draw.rectangle([body_cx + 18, body_cy - 6, body_cx + 22, body_cy - 2],
                       fill=GUN_STOCK, outline=OUTLINE)

        # Head
        ellipse(draw, body_cx, head_cy, 16, 14, OLIVE)
        draw.rectangle([body_cx - 18, head_cy + 4, body_cx + 18, head_cy + 8],
                       fill=OLIVE_DARK, outline=OUTLINE)
        ellipse(draw, body_cx, head_cy + 4, 10, 8, SKIN)
        # Eyes
        draw.rectangle([body_cx - 6, head_cy + 2, body_cx - 2, head_cy + 6], fill=BLACK)
        draw.rectangle([body_cx + 2, head_cy + 2, body_cx + 6, head_cy + 6], fill=BLACK)
        # Helmet band
        draw.rectangle([body_cx - 14, head_cy - 2, body_cx + 14, head_cy + 2],
                       fill=OLIVE_DARK, outline=None)
        # Chin strap
        draw.line([body_cx - 10, head_cy + 8, body_cx - 10, head_cy + 12], fill=STRAP, width=2)
        draw.line([body_cx + 10, head_cy + 8, body_cx + 10, head_cy + 12], fill=STRAP, width=2)
        # Chin strap connector
        draw.line([body_cx - 10, head_cy + 12, body_cx - 6, head_cy + 14], fill=STRAP, width=1)
        draw.line([body_cx + 10, head_cy + 12, body_cx + 6, head_cy + 14], fill=STRAP, width=1)

    elif direction == UP:
        # Legs
        draw.rectangle([body_cx - 10 + leg_spread, body_cy + 10,
                        body_cx - 4 + leg_spread, base_y], fill=OLIVE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 4 - leg_spread, body_cy + 10,
                        body_cx + 10 - leg_spread, base_y], fill=OLIVE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 10 + leg_spread, body_cy + 12,
                        body_cx - 4 + leg_spread, body_cy + 16], fill=KNEE_PAD)
        draw.rectangle([body_cx + 4 - leg_spread, body_cy + 12,
                        body_cx + 10 - leg_spread, body_cy + 16], fill=KNEE_PAD)
        draw.rectangle([body_cx - 10 + leg_spread, base_y - 6,
                        body_cx - 4 + leg_spread, base_y], fill=BROWN_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 4 - leg_spread, base_y - 6,
                        body_cx + 10 - leg_spread, base_y], fill=BROWN_DARK, outline=OUTLINE)
        draw.line([(body_cx - 9 + leg_spread, base_y - 1), (body_cx - 5 + leg_spread, base_y - 1)], fill=BOOT_TREAD)
        draw.line([(body_cx + 5 - leg_spread, base_y - 1), (body_cx + 9 - leg_spread, base_y - 1)], fill=BOOT_TREAD)

        # Backpack
        pack_sway = [0, 2, 0, -2][frame]
        draw.rounded_rectangle([body_cx - 10 + pack_sway, body_cy - 8,
                                body_cx + 10 + pack_sway, body_cy + 10],
                               radius=4, fill=OLIVE_DARK, outline=OUTLINE)
        draw.rounded_rectangle([body_cx - 8 + pack_sway, body_cy - 6,
                                body_cx + 8 + pack_sway, body_cy + 8],
                               radius=4, fill=BROWN, outline=None)
        draw.line([body_cx - 6 + pack_sway, body_cy - 8, body_cx - 6, body_cy - 12],
                  fill=STRAP, width=2)
        draw.line([body_cx + 6 + pack_sway, body_cy - 8, body_cx + 6, body_cy - 12],
                  fill=STRAP, width=2)

        # Body
        ellipse(draw, body_cx, body_cy, 14, 12, OLIVE)
        ellipse(draw, body_cx, body_cy - 2, 10, 8, OLIVE_DARK)
        draw.rectangle([body_cx - 6, body_cy - 4, body_cx, body_cy], fill=CAMO_BROWN)
        draw.rectangle([body_cx + 2, body_cy + 2, body_cx + 8, body_cy + 6], fill=CAMO_DARK)

        # Belt
        draw.rectangle([body_cx - 14, body_cy + 6, body_cx + 14, body_cy + 10],
                       fill=BELT, outline=OUTLINE)
        draw.rectangle([body_cx - 10, body_cy + 6, body_cx - 6, body_cy + 12],
                       fill=POUCH, outline=OUTLINE)
        draw.rectangle([body_cx + 6, body_cy + 6, body_cx + 10, body_cy + 12],
                       fill=POUCH, outline=OUTLINE)

        # Arms
        draw.rectangle([body_cx - 18, body_cy - 6, body_cx - 12, body_cy + 6],
                       fill=OLIVE, outline=OUTLINE)
        draw.rectangle([body_cx + 12, body_cy - 6, body_cx + 18, body_cy + 6],
                       fill=OLIVE, outline=OUTLINE)

        # Head (back of helmet)
        ellipse(draw, body_cx, head_cy, 16, 14, OLIVE)
        ellipse(draw, body_cx, head_cy, 12, 10, OLIVE_DARK)
        draw.rectangle([body_cx - 14, head_cy - 2, body_cx + 14, head_cy + 2],
                       fill=OLIVE_DARK, outline=None)

    elif direction == LEFT:
        # Rifle behind body
        rifle_bob = [0, -2, 0, -2][frame]
        draw.rectangle([body_cx - 24, body_cy - 2 + rifle_bob,
                        body_cx - 6, body_cy + 2 + rifle_bob],
                       fill=GUN_BODY, outline=OUTLINE)
        draw.rectangle([body_cx - 30, body_cy - 2 + rifle_bob,
                        body_cx - 24, body_cy + rifle_bob],
                       fill=GUN_BARREL)
        draw.rectangle([body_cx - 6, body_cy - 4 + rifle_bob,
                        body_cx, body_cy + 4 + rifle_bob],
                       fill=GUN_STOCK, outline=OUTLINE)

        # Legs (side view)
        draw.rectangle([body_cx - 2 - leg_spread, body_cy + 10,
                        body_cx + 4 - leg_spread, base_y], fill=OLIVE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 2 - leg_spread, base_y - 6,
                        body_cx + 4 - leg_spread, base_y], fill=BROWN_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 8 + leg_spread, body_cy + 10,
                        body_cx - 2 + leg_spread, base_y], fill=OLIVE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 8 + leg_spread, base_y - 6,
                        body_cx - 2 + leg_spread, base_y], fill=BROWN_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 2 - leg_spread, body_cy + 12,
                        body_cx + 4 - leg_spread, body_cy + 16], fill=KNEE_PAD)
        draw.rectangle([body_cx - 8 + leg_spread, body_cy + 12,
                        body_cx - 2 + leg_spread, body_cy + 16], fill=KNEE_PAD)
        # Boot treads
        draw.line([(body_cx - 1 - leg_spread, base_y - 1), (body_cx + 3 - leg_spread, base_y - 1)], fill=BOOT_TREAD)
        draw.line([(body_cx - 7 + leg_spread, base_y - 1), (body_cx - 3 + leg_spread, base_y - 1)], fill=BOOT_TREAD)

        # Body
        ellipse(draw, body_cx - 2, body_cy, 12, 12, OLIVE)
        draw.rectangle([body_cx - 6, body_cy - 6, body_cx, body_cy - 2], fill=CAMO_SPOT)
        draw.rectangle([body_cx - 10, body_cy, body_cx - 4, body_cy + 4], fill=CAMO_BROWN)
        draw.rectangle([body_cx + 2, body_cy - 4, body_cx + 6, body_cy], fill=CAMO_TAN)
        draw_vest_pocket(draw, body_cx - 10, body_cy - 8, body_cx - 4, body_cy - 4)
        # Ammo belt
        draw_ammo_belt_detail(draw, body_cx - 10, body_cy - 2, body_cx + 8)
        # Belt
        draw.rectangle([body_cx - 14, body_cy + 6, body_cx + 10, body_cy + 10],
                       fill=BELT, outline=OUTLINE)
        draw.rectangle([body_cx - 12, body_cy + 6, body_cx - 8, body_cy + 12],
                       fill=POUCH, outline=OUTLINE)
        draw.rectangle([body_cx + 4, body_cy + 6, body_cx + 8, body_cy + 12],
                       fill=POUCH, outline=OUTLINE)

        # Arm (front)
        draw.rectangle([body_cx - 14, body_cy - 4, body_cx - 8, body_cy + 6],
                       fill=OLIVE, outline=OUTLINE)
        draw.rectangle([body_cx - 14, body_cy + 2, body_cx - 8, body_cy + 6],
                       fill=BROWN, outline=OUTLINE)

        # Head (side, facing left)
        ellipse(draw, body_cx - 2, head_cy, 14, 14, OLIVE)
        ellipse(draw, body_cx - 6, head_cy + 4, 8, 6, SKIN)
        draw.rectangle([body_cx - 18, head_cy + 4, body_cx + 8, head_cy + 8],
                       fill=OLIVE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 10, head_cy + 2, body_cx - 6, head_cy + 6], fill=BLACK)
        draw.rectangle([body_cx - 14, head_cy - 2, body_cx + 10, head_cy + 2],
                       fill=OLIVE_DARK, outline=None)
        # Chin strap
        draw.line([body_cx - 12, head_cy + 8, body_cx - 12, head_cy + 12], fill=STRAP, width=2)
        draw.line([body_cx - 12, head_cy + 12, body_cx - 8, head_cy + 14], fill=STRAP, width=1)

    elif direction == RIGHT:
        # Rifle behind body
        rifle_bob = [0, -2, 0, -2][frame]
        draw.rectangle([body_cx + 6, body_cy - 2 + rifle_bob,
                        body_cx + 24, body_cy + 2 + rifle_bob],
                       fill=GUN_BODY, outline=OUTLINE)
        draw.rectangle([body_cx + 24, body_cy - 2 + rifle_bob,
                        body_cx + 30, body_cy + rifle_bob],
                       fill=GUN_BARREL)
        draw.rectangle([body_cx, body_cy - 4 + rifle_bob,
                        body_cx + 6, body_cy + 4 + rifle_bob],
                       fill=GUN_STOCK, outline=OUTLINE)

        # Legs
        draw.rectangle([body_cx - 2 + leg_spread, body_cy + 10,
                        body_cx + 4 + leg_spread, base_y], fill=OLIVE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 2 + leg_spread, base_y - 6,
                        body_cx + 4 + leg_spread, base_y], fill=BROWN_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 4 - leg_spread, body_cy + 10,
                        body_cx + 10 - leg_spread, base_y], fill=OLIVE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 4 - leg_spread, base_y - 6,
                        body_cx + 10 - leg_spread, base_y], fill=BROWN_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 2 + leg_spread, body_cy + 12,
                        body_cx + 4 + leg_spread, body_cy + 16], fill=KNEE_PAD)
        draw.rectangle([body_cx + 4 - leg_spread, body_cy + 12,
                        body_cx + 10 - leg_spread, body_cy + 16], fill=KNEE_PAD)
        draw.line([(body_cx - 1 + leg_spread, base_y - 1), (body_cx + 3 + leg_spread, base_y - 1)], fill=BOOT_TREAD)
        draw.line([(body_cx + 5 - leg_spread, base_y - 1), (body_cx + 9 - leg_spread, base_y - 1)], fill=BOOT_TREAD)

        # Body
        ellipse(draw, body_cx + 2, body_cy, 12, 12, OLIVE)
        draw.rectangle([body_cx, body_cy - 6, body_cx + 6, body_cy - 2], fill=CAMO_SPOT)
        draw.rectangle([body_cx + 4, body_cy, body_cx + 10, body_cy + 4], fill=CAMO_BROWN)
        draw.rectangle([body_cx - 6, body_cy - 4, body_cx - 2, body_cy], fill=CAMO_TAN)
        draw_vest_pocket(draw, body_cx + 4, body_cy - 8, body_cx + 10, body_cy - 4)
        draw_ammo_belt_detail(draw, body_cx - 8, body_cy - 2, body_cx + 10)
        draw.rectangle([body_cx - 10, body_cy + 6, body_cx + 14, body_cy + 10],
                       fill=BELT, outline=OUTLINE)
        draw.rectangle([body_cx - 8, body_cy + 6, body_cx - 4, body_cy + 12],
                       fill=POUCH, outline=OUTLINE)
        draw.rectangle([body_cx + 8, body_cy + 6, body_cx + 12, body_cy + 12],
                       fill=POUCH, outline=OUTLINE)

        # Arm
        draw.rectangle([body_cx + 8, body_cy - 4, body_cx + 14, body_cy + 6],
                       fill=OLIVE, outline=OUTLINE)
        draw.rectangle([body_cx + 8, body_cy + 2, body_cx + 14, body_cy + 6],
                       fill=BROWN, outline=OUTLINE)

        # Head
        ellipse(draw, body_cx + 2, head_cy, 14, 14, OLIVE)
        ellipse(draw, body_cx + 6, head_cy + 4, 8, 6, SKIN)
        draw.rectangle([body_cx - 8, head_cy + 4, body_cx + 18, head_cy + 8],
                       fill=OLIVE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 6, head_cy + 2, body_cx + 10, head_cy + 6], fill=BLACK)
        draw.rectangle([body_cx - 10, head_cy - 2, body_cx + 14, head_cy + 2],
                       fill=OLIVE_DARK, outline=None)
        draw.line([body_cx + 12, head_cy + 8, body_cx + 12, head_cy + 12], fill=STRAP, width=2)
        draw.line([body_cx + 12, head_cy + 12, body_cx + 8, head_cy + 14], fill=STRAP, width=1)


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
