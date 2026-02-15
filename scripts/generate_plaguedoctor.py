#!/usr/bin/env python3
"""Generate sprites/plaguedoctor.png — 4-column x 4-row character spritesheet.

128x128 PNG, 32x32 per frame.
Row layout: Down=0, Up=1, Left=2, Right=3
4 walking animation frames per direction.

Style matches Spaceman/Gladiator: big round head, round body, small limbs, dark outlines.
Theme: Plague Doctor — dark robes, beaked plague mask, carrying a censer/vial.
"""

from PIL import Image, ImageDraw

FRAME_SIZE = 32
COLS = 4
ROWS = 4
IMG_W = FRAME_SIZE * COLS   # 128
IMG_H = FRAME_SIZE * ROWS   # 128

# Colors
OUTLINE = (40, 35, 35)
ROBE_DARK = (35, 30, 40)
ROBE_MID = (50, 45, 55)
ROBE_LIGHT = (65, 58, 70)
MASK_WHITE = (210, 205, 195)
MASK_SHADOW = (170, 165, 155)
BEAK = (180, 170, 140)
BEAK_DARK = (140, 130, 105)
LENS_RED = (160, 40, 40)
LENS_GLOW = (200, 60, 50)
HAT_DARK = (30, 25, 35)
HAT_BAND = (90, 40, 40)
GLOVE_DARK = (45, 35, 30)
GLOVE = (60, 50, 40)
VIAL_GLASS = (60, 80, 60)
VIAL_LIQUID = (80, 180, 40)
BELT = (55, 45, 40)
BELT_BUCKLE = (130, 110, 50)
CENSER_METAL = (120, 110, 90)
CENSER_DARK = (80, 75, 60)
BLACK = (25, 25, 30)
SMOKE_GREEN = (70, 120, 40)

DOWN, UP, LEFT, RIGHT = 0, 1, 2, 3


def ellipse(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=outline)


def draw_plaguedoctor(draw, ox, oy, direction, frame):
    bob = [0, -1, 0, -1][frame]
    leg_spread = [-2, 0, 2, 0][frame]

    base_y = oy + 27 + bob
    body_cx = ox + 16
    body_cy = base_y - 10
    head_cy = body_cy - 10

    if direction == DOWN:
        # Legs — dark robe bottom
        draw.rectangle([body_cx - 5 + leg_spread, body_cy + 5,
                        body_cx - 2 + leg_spread, base_y], fill=ROBE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, body_cy + 5,
                        body_cx + 5 - leg_spread, base_y], fill=ROBE_DARK, outline=OUTLINE)
        # Boots
        draw.rectangle([body_cx - 5 + leg_spread, base_y - 3,
                        body_cx - 2 + leg_spread, base_y], fill=BLACK, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, base_y - 3,
                        body_cx + 5 - leg_spread, base_y], fill=BLACK, outline=OUTLINE)

        # Body — dark flowing robes
        ellipse(draw, body_cx, body_cy, 7, 6, ROBE_MID)
        # Robe folds
        draw.rectangle([body_cx - 4, body_cy - 3, body_cx - 2, body_cy + 3], fill=ROBE_DARK)
        draw.rectangle([body_cx + 2, body_cy - 2, body_cx + 4, body_cy + 2], fill=ROBE_LIGHT)
        # Belt with vials
        draw.rectangle([body_cx - 7, body_cy + 3, body_cx + 7, body_cy + 5],
                       fill=BELT, outline=OUTLINE)
        draw.rectangle([body_cx - 1, body_cy + 3, body_cx + 1, body_cy + 5], fill=BELT_BUCKLE)

        # Arms — dark robes with gloves
        draw.rectangle([body_cx - 9, body_cy - 3, body_cx - 6, body_cy + 3],
                       fill=ROBE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 6, body_cy - 3, body_cx + 9, body_cy + 3],
                       fill=ROBE_DARK, outline=OUTLINE)
        # Gloves
        draw.rectangle([body_cx - 9, body_cy + 1, body_cx - 6, body_cy + 3],
                       fill=GLOVE, outline=OUTLINE)
        draw.rectangle([body_cx + 6, body_cy + 1, body_cx + 9, body_cy + 3],
                       fill=GLOVE, outline=OUTLINE)

        # Censer in right hand (small swinging detail)
        censer_sway = [-1, 0, 1, 0][frame]
        draw.ellipse([body_cx + 7 + censer_sway, body_cy + 2,
                      body_cx + 11 + censer_sway, body_cy + 5],
                     fill=CENSER_METAL, outline=OUTLINE)

        # Head — plague mask (front view)
        # Wide-brim hat
        draw.rectangle([body_cx - 9, head_cy + 1, body_cx + 9, head_cy + 3],
                       fill=HAT_DARK, outline=OUTLINE)
        # Hat crown
        ellipse(draw, body_cx, head_cy - 2, 7, 5, HAT_DARK)
        # Hat band
        draw.rectangle([body_cx - 7, head_cy, body_cx + 7, head_cy + 2],
                       fill=HAT_BAND, outline=None)

        # Mask face
        ellipse(draw, body_cx, head_cy + 4, 5, 4, MASK_WHITE)
        # Beak — protruding forward
        draw.polygon([(body_cx, head_cy + 4), (body_cx - 2, head_cy + 6),
                      (body_cx, head_cy + 10), (body_cx + 2, head_cy + 6)],
                     fill=BEAK, outline=OUTLINE)
        # Red lens eyes
        ellipse(draw, body_cx - 3, head_cy + 3, 2, 2, LENS_RED)
        ellipse(draw, body_cx + 3, head_cy + 3, 2, 2, LENS_RED)
        # Lens glow dots
        draw.point((body_cx - 3, head_cy + 2), fill=LENS_GLOW)
        draw.point((body_cx + 3, head_cy + 2), fill=LENS_GLOW)

    elif direction == UP:
        # Legs
        draw.rectangle([body_cx - 5 + leg_spread, body_cy + 5,
                        body_cx - 2 + leg_spread, base_y], fill=ROBE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, body_cy + 5,
                        body_cx + 5 - leg_spread, base_y], fill=ROBE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 5 + leg_spread, base_y - 3,
                        body_cx - 2 + leg_spread, base_y], fill=BLACK, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, base_y - 3,
                        body_cx + 5 - leg_spread, base_y], fill=BLACK, outline=OUTLINE)

        # Body — back of robes
        ellipse(draw, body_cx, body_cy, 7, 6, ROBE_MID)
        ellipse(draw, body_cx, body_cy - 1, 5, 4, ROBE_DARK)

        # Arms
        draw.rectangle([body_cx - 9, body_cy - 3, body_cx - 6, body_cy + 3],
                       fill=ROBE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 6, body_cy - 3, body_cx + 9, body_cy + 3],
                       fill=ROBE_DARK, outline=OUTLINE)

        # Head (back of hat)
        ellipse(draw, body_cx, head_cy, 8, 7, HAT_DARK)
        ellipse(draw, body_cx, head_cy, 6, 5, ROBE_DARK)
        # Hat band
        draw.rectangle([body_cx - 7, head_cy, body_cx + 7, head_cy + 2],
                       fill=HAT_BAND, outline=None)

    elif direction == LEFT:
        # Legs (side view)
        draw.rectangle([body_cx - 1 - leg_spread, body_cy + 5,
                        body_cx + 2 - leg_spread, base_y], fill=ROBE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 1 - leg_spread, base_y - 3,
                        body_cx + 2 - leg_spread, base_y], fill=BLACK, outline=OUTLINE)
        draw.rectangle([body_cx - 4 + leg_spread, body_cy + 5,
                        body_cx - 1 + leg_spread, base_y], fill=ROBE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 4 + leg_spread, base_y - 3,
                        body_cx - 1 + leg_spread, base_y], fill=BLACK, outline=OUTLINE)

        # Body
        ellipse(draw, body_cx - 1, body_cy, 6, 6, ROBE_MID)
        ellipse(draw, body_cx - 1, body_cy - 1, 4, 4, ROBE_DARK)
        # Belt
        draw.rectangle([body_cx - 7, body_cy + 3, body_cx + 5, body_cy + 5],
                       fill=BELT, outline=OUTLINE)

        # Arm (front — holding censer)
        draw.rectangle([body_cx - 7, body_cy - 2, body_cx - 4, body_cy + 3],
                       fill=ROBE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 7, body_cy + 1, body_cx - 4, body_cy + 3],
                       fill=GLOVE, outline=OUTLINE)
        # Censer
        censer_sway = [-1, 0, 1, 0][frame]
        draw.ellipse([body_cx - 10, body_cy + 2 + censer_sway,
                      body_cx - 6, body_cy + 5 + censer_sway],
                     fill=CENSER_METAL, outline=OUTLINE)

        # Head (side, facing left) — beak visible
        # Hat
        ellipse(draw, body_cx - 1, head_cy, 7, 7, HAT_DARK)
        draw.rectangle([body_cx - 9, head_cy + 2, body_cx + 4, head_cy + 4],
                       fill=HAT_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 7, head_cy, body_cx + 5, head_cy + 2],
                       fill=HAT_BAND, outline=None)
        # Mask
        ellipse(draw, body_cx - 2, head_cy + 4, 4, 3, MASK_WHITE)
        # Beak pointing left
        draw.polygon([(body_cx - 6, head_cy + 4), (body_cx - 10, head_cy + 5),
                      (body_cx - 6, head_cy + 6)],
                     fill=BEAK, outline=OUTLINE)
        # Red lens eye
        ellipse(draw, body_cx - 4, head_cy + 3, 2, 2, LENS_RED)
        draw.point((body_cx - 4, head_cy + 2), fill=LENS_GLOW)

    elif direction == RIGHT:
        # Legs
        draw.rectangle([body_cx - 1 + leg_spread, body_cy + 5,
                        body_cx + 2 + leg_spread, base_y], fill=ROBE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 1 + leg_spread, base_y - 3,
                        body_cx + 2 + leg_spread, base_y], fill=BLACK, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, body_cy + 5,
                        body_cx + 5 - leg_spread, base_y], fill=ROBE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, base_y - 3,
                        body_cx + 5 - leg_spread, base_y], fill=BLACK, outline=OUTLINE)

        # Body
        ellipse(draw, body_cx + 1, body_cy, 6, 6, ROBE_MID)
        ellipse(draw, body_cx + 1, body_cy - 1, 4, 4, ROBE_DARK)
        # Belt
        draw.rectangle([body_cx - 5, body_cy + 3, body_cx + 7, body_cy + 5],
                       fill=BELT, outline=OUTLINE)

        # Arm (holding censer)
        draw.rectangle([body_cx + 4, body_cy - 2, body_cx + 7, body_cy + 3],
                       fill=ROBE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 4, body_cy + 1, body_cx + 7, body_cy + 3],
                       fill=GLOVE, outline=OUTLINE)
        # Censer
        censer_sway = [-1, 0, 1, 0][frame]
        draw.ellipse([body_cx + 6, body_cy + 2 + censer_sway,
                      body_cx + 10, body_cy + 5 + censer_sway],
                     fill=CENSER_METAL, outline=OUTLINE)

        # Head (side, facing right) — beak visible
        ellipse(draw, body_cx + 1, head_cy, 7, 7, HAT_DARK)
        draw.rectangle([body_cx - 4, head_cy + 2, body_cx + 9, head_cy + 4],
                       fill=HAT_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 5, head_cy, body_cx + 7, head_cy + 2],
                       fill=HAT_BAND, outline=None)
        # Mask
        ellipse(draw, body_cx + 2, head_cy + 4, 4, 3, MASK_WHITE)
        # Beak pointing right
        draw.polygon([(body_cx + 6, head_cy + 4), (body_cx + 10, head_cy + 5),
                      (body_cx + 6, head_cy + 6)],
                     fill=BEAK, outline=OUTLINE)
        # Red lens eye
        ellipse(draw, body_cx + 4, head_cy + 3, 2, 2, LENS_RED)
        draw.point((body_cx + 4, head_cy + 2), fill=LENS_GLOW)


def main():
    img = Image.new("RGBA", (IMG_W, IMG_H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    for direction in range(ROWS):
        for frame in range(COLS):
            ox = frame * FRAME_SIZE
            oy = direction * FRAME_SIZE
            draw_plaguedoctor(draw, ox, oy, direction, frame)

    img.save("sprites/plaguedoctor.png")
    print(f"Generated sprites/plaguedoctor.png ({IMG_W}x{IMG_H})")


if __name__ == "__main__":
    main()
