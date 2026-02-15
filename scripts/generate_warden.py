#!/usr/bin/env python3
"""Generate sprites/warden.png — 4-column x 4-row character spritesheet.

128x128 PNG, 32x32 per frame.
Row layout: Down=0, Up=1, Left=2, Right=3
4 walking animation frames per direction.

Style matches Spaceman/Gladiator: big round head, round body, small limbs, dark outlines.
Theme: Prison warden — dark iron armor, chain details, heavy key ring, iron mask/visor.
"""

from PIL import Image, ImageDraw

FRAME_SIZE = 32
COLS = 4
ROWS = 4
IMG_W = FRAME_SIZE * COLS   # 128
IMG_H = FRAME_SIZE * ROWS   # 128

# Colors
OUTLINE = (40, 35, 35)
IRON = (70, 70, 80)
IRON_LIGHT = (100, 100, 115)
IRON_DARK = (45, 45, 55)
CHAIN = (120, 120, 130)
CHAIN_DARK = (80, 80, 90)
VISOR_SLIT = (180, 200, 255)  # Icy blue glow from visor
VISOR_DIM = (100, 130, 180)
ARMOR_ACCENT = (55, 60, 70)
KEY_GOLD = (200, 170, 60)
KEY_DARK = (150, 125, 40)
BELT = (50, 45, 40)
BOOT = (40, 38, 35)
BLACK = (25, 25, 30)
FROST_BLUE = (140, 190, 255)

DOWN, UP, LEFT, RIGHT = 0, 1, 2, 3


def ellipse(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=outline)


def draw_warden(draw, ox, oy, direction, frame):
    bob = [0, -1, 0, -1][frame]
    leg_spread = [-2, 0, 2, 0][frame]

    base_y = oy + 27 + bob
    body_cx = ox + 16
    body_cy = base_y - 10
    head_cy = body_cy - 10

    if direction == DOWN:
        # Legs — heavy iron boots
        draw.rectangle([body_cx - 5 + leg_spread, body_cy + 5,
                        body_cx - 2 + leg_spread, base_y], fill=IRON_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, body_cy + 5,
                        body_cx + 5 - leg_spread, base_y], fill=IRON_DARK, outline=OUTLINE)
        # Boots
        draw.rectangle([body_cx - 6 + leg_spread, base_y - 3,
                        body_cx - 1 + leg_spread, base_y], fill=BOOT, outline=OUTLINE)
        draw.rectangle([body_cx + 1 - leg_spread, base_y - 3,
                        body_cx + 6 - leg_spread, base_y], fill=BOOT, outline=OUTLINE)

        # Body — dark iron armor
        ellipse(draw, body_cx, body_cy, 7, 6, IRON)
        # Armor plate detail
        draw.rectangle([body_cx - 3, body_cy - 4, body_cx + 3, body_cy + 1],
                       fill=IRON_LIGHT, outline=OUTLINE)
        # Chain belt
        draw.rectangle([body_cx - 7, body_cy + 3, body_cx + 7, body_cy + 5],
                       fill=CHAIN_DARK, outline=OUTLINE)
        # Chain link details on belt
        for i in range(-5, 6, 3):
            draw.rectangle([body_cx + i, body_cy + 3, body_cx + i + 1, body_cy + 5],
                           fill=CHAIN)
        # Key ring hanging from belt
        draw.ellipse([body_cx + 4, body_cy + 4, body_cx + 8, body_cy + 8],
                     fill=KEY_GOLD, outline=OUTLINE)
        draw.rectangle([body_cx + 5, body_cy + 7, body_cx + 7, body_cy + 9],
                       fill=KEY_DARK, outline=OUTLINE)

        # Arms — armored gauntlets
        draw.rectangle([body_cx - 9, body_cy - 3, body_cx - 6, body_cy + 3],
                       fill=IRON, outline=OUTLINE)
        draw.rectangle([body_cx + 6, body_cy - 3, body_cx + 9, body_cy + 3],
                       fill=IRON, outline=OUTLINE)
        # Gauntlet details
        draw.rectangle([body_cx - 9, body_cy + 1, body_cx - 6, body_cy + 3],
                       fill=IRON_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 6, body_cy + 1, body_cx + 9, body_cy + 3],
                       fill=IRON_DARK, outline=OUTLINE)

        # Head — iron helm with visor
        ellipse(draw, body_cx, head_cy, 8, 7, IRON)
        # Visor plate
        draw.rectangle([body_cx - 6, head_cy, body_cx + 6, head_cy + 4],
                       fill=IRON_DARK, outline=OUTLINE)
        # Visor slit — glowing icy blue
        draw.rectangle([body_cx - 4, head_cy + 1, body_cx + 4, head_cy + 3],
                       fill=VISOR_SLIT)
        # Helm crest
        draw.rectangle([body_cx - 1, head_cy - 7, body_cx + 1, head_cy - 2],
                       fill=IRON_LIGHT, outline=OUTLINE)
        # Rivet details on helm
        draw.point((body_cx - 6, head_cy - 2), fill=CHAIN)
        draw.point((body_cx + 6, head_cy - 2), fill=CHAIN)

    elif direction == UP:
        # Legs
        draw.rectangle([body_cx - 5 + leg_spread, body_cy + 5,
                        body_cx - 2 + leg_spread, base_y], fill=IRON_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, body_cy + 5,
                        body_cx + 5 - leg_spread, base_y], fill=IRON_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 6 + leg_spread, base_y - 3,
                        body_cx - 1 + leg_spread, base_y], fill=BOOT, outline=OUTLINE)
        draw.rectangle([body_cx + 1 - leg_spread, base_y - 3,
                        body_cx + 6 - leg_spread, base_y], fill=BOOT, outline=OUTLINE)

        # Chain cape on back
        chain_sway = [0, 1, 0, -1][frame]
        for i in range(3):
            cy_off = body_cy - 2 + i * 3
            draw.rectangle([body_cx - 4 + chain_sway, cy_off,
                            body_cx + 4 + chain_sway, cy_off + 2],
                           fill=CHAIN_DARK, outline=None)
            for j in range(-3, 4, 2):
                draw.point((body_cx + j + chain_sway, cy_off + 1), fill=CHAIN)

        # Body
        ellipse(draw, body_cx, body_cy, 7, 6, IRON)
        # Back armor plate
        ellipse(draw, body_cx, body_cy - 1, 5, 4, IRON_DARK)

        # Arms
        draw.rectangle([body_cx - 9, body_cy - 3, body_cx - 6, body_cy + 3],
                       fill=IRON, outline=OUTLINE)
        draw.rectangle([body_cx + 6, body_cy - 3, body_cx + 9, body_cy + 3],
                       fill=IRON, outline=OUTLINE)

        # Head — back of helm
        ellipse(draw, body_cx, head_cy, 8, 7, IRON)
        ellipse(draw, body_cx, head_cy, 6, 5, IRON_DARK)
        # Crest
        draw.rectangle([body_cx - 1, head_cy - 7, body_cx + 1, head_cy - 2],
                       fill=IRON_LIGHT, outline=OUTLINE)

    elif direction == LEFT:
        # Legs (side view)
        draw.rectangle([body_cx - 1 - leg_spread, body_cy + 5,
                        body_cx + 2 - leg_spread, base_y], fill=IRON_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 1 - leg_spread, base_y - 3,
                        body_cx + 2 - leg_spread, base_y], fill=BOOT, outline=OUTLINE)
        draw.rectangle([body_cx - 4 + leg_spread, body_cy + 5,
                        body_cx - 1 + leg_spread, base_y], fill=IRON_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 4 + leg_spread, base_y - 3,
                        body_cx - 1 + leg_spread, base_y], fill=BOOT, outline=OUTLINE)

        # Body
        ellipse(draw, body_cx - 1, body_cy, 6, 6, IRON)
        ellipse(draw, body_cx - 1, body_cy - 1, 4, 4, IRON_LIGHT)
        # Belt
        draw.rectangle([body_cx - 7, body_cy + 3, body_cx + 5, body_cy + 5],
                       fill=CHAIN_DARK, outline=OUTLINE)
        # Key ring
        draw.ellipse([body_cx - 8, body_cy + 4, body_cx - 4, body_cy + 8],
                     fill=KEY_GOLD, outline=OUTLINE)

        # Arm (front, holding chains)
        draw.rectangle([body_cx - 7, body_cy - 2, body_cx - 4, body_cy + 3],
                       fill=IRON, outline=OUTLINE)
        draw.rectangle([body_cx - 7, body_cy + 1, body_cx - 4, body_cy + 3],
                       fill=IRON_DARK, outline=OUTLINE)

        # Head (side, facing left) — iron helm
        ellipse(draw, body_cx - 1, head_cy, 7, 7, IRON)
        # Visor
        draw.rectangle([body_cx - 8, head_cy, body_cx + 2, head_cy + 3],
                       fill=IRON_DARK, outline=OUTLINE)
        # Visor slit
        draw.rectangle([body_cx - 6, head_cy + 1, body_cx, head_cy + 2],
                       fill=VISOR_SLIT)
        # Crest
        draw.rectangle([body_cx - 2, head_cy - 7, body_cx, head_cy - 2],
                       fill=IRON_LIGHT, outline=OUTLINE)

    elif direction == RIGHT:
        # Legs
        draw.rectangle([body_cx - 1 + leg_spread, body_cy + 5,
                        body_cx + 2 + leg_spread, base_y], fill=IRON_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 1 + leg_spread, base_y - 3,
                        body_cx + 2 + leg_spread, base_y], fill=BOOT, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, body_cy + 5,
                        body_cx + 5 - leg_spread, base_y], fill=IRON_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, base_y - 3,
                        body_cx + 5 - leg_spread, base_y], fill=BOOT, outline=OUTLINE)

        # Body
        ellipse(draw, body_cx + 1, body_cy, 6, 6, IRON)
        ellipse(draw, body_cx + 1, body_cy - 1, 4, 4, IRON_LIGHT)
        # Belt
        draw.rectangle([body_cx - 5, body_cy + 3, body_cx + 7, body_cy + 5],
                       fill=CHAIN_DARK, outline=OUTLINE)
        # Key ring
        draw.ellipse([body_cx + 4, body_cy + 4, body_cx + 8, body_cy + 8],
                     fill=KEY_GOLD, outline=OUTLINE)

        # Arm
        draw.rectangle([body_cx + 4, body_cy - 2, body_cx + 7, body_cy + 3],
                       fill=IRON, outline=OUTLINE)
        draw.rectangle([body_cx + 4, body_cy + 1, body_cx + 7, body_cy + 3],
                       fill=IRON_DARK, outline=OUTLINE)

        # Head (side, facing right) — iron helm
        ellipse(draw, body_cx + 1, head_cy, 7, 7, IRON)
        # Visor
        draw.rectangle([body_cx - 2, head_cy, body_cx + 8, head_cy + 3],
                       fill=IRON_DARK, outline=OUTLINE)
        # Visor slit
        draw.rectangle([body_cx, head_cy + 1, body_cx + 6, head_cy + 2],
                       fill=VISOR_SLIT)
        # Crest
        draw.rectangle([body_cx, head_cy - 7, body_cx + 2, head_cy - 2],
                       fill=IRON_LIGHT, outline=OUTLINE)


def main():
    img = Image.new("RGBA", (IMG_W, IMG_H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    for direction in range(ROWS):
        for frame in range(COLS):
            ox = frame * FRAME_SIZE
            oy = direction * FRAME_SIZE
            draw_warden(draw, ox, oy, direction, frame)

    img.save("sprites/warden.png")
    print(f"Generated sprites/warden.png ({IMG_W}x{IMG_H})")


if __name__ == "__main__":
    main()
