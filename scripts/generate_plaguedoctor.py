#!/usr/bin/env python3
"""Generate sprites/plaguedoctor.png — 4-column x 4-row character spritesheet.

128x128 PNG, 32x32 per frame.
Row layout: Down=0, Up=1, Left=2, Right=3
4 walking animation frames per direction.

Style matches Spaceman/Gladiator: big round head, round body, small limbs, dark outlines.
Theme: Plague Doctor — dark robes, tall hat, long beaked plague mask, censer with green fumes.
"""

from PIL import Image, ImageDraw

FRAME_SIZE = 32
COLS = 4
ROWS = 4
IMG_W = FRAME_SIZE * COLS   # 128
IMG_H = FRAME_SIZE * ROWS   # 128

# Colors
OUTLINE = (40, 35, 35)
ROBE_DARK = (38, 33, 45)
ROBE_MID = (55, 48, 62)
ROBE_LIGHT = (70, 62, 78)
MASK_WHITE = (215, 210, 200)
MASK_SHADOW = (180, 175, 165)
BEAK = (185, 175, 145)
BEAK_DARK = (145, 135, 110)
BEAK_TIP = (160, 150, 120)
BEAK_STITCH = (120, 110, 85)
MASK_STITCH = (170, 160, 145)
LENS_RED = (175, 35, 35)
LENS_GLOW = (220, 70, 55)
LENS_BRIGHT = (255, 120, 90)
LENS_REFLECT = (240, 200, 180)
HAT_DARK = (28, 22, 32)
HAT_MID = (38, 32, 42)
HAT_BAND = (95, 38, 38)
HAT_BUCKLE = (180, 155, 55)
GLOVE_DARK = (45, 35, 30)
GLOVE = (60, 50, 40)
VIAL_GREEN = (50, 140, 60)
VIAL_AMBER = (180, 130, 40)
VIAL_GLASS = (100, 120, 110)
BELT = (55, 45, 40)
BELT_BUCKLE = (140, 120, 50)
CENSER_METAL = (130, 118, 95)
CENSER_DARK = (85, 78, 62)
CENSER_LIGHT = (160, 148, 120)
BLACK = (25, 25, 30)
SMOKE_1 = (60, 140, 50, 160)
SMOKE_2 = (70, 160, 55, 120)
SMOKE_3 = (80, 180, 65, 80)
NOSTRIL = (100, 90, 70)

DOWN, UP, LEFT, RIGHT = 0, 1, 2, 3


def ellipse(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=outline)


def draw_smoke(draw, sx, sy, frame):
    """Draw animated green smoke puffs rising from a point. Uses per-frame offsets."""
    # Three smoke particles that shift position each frame
    offsets = [
        # (dx1, dy1, dx2, dy2, dx3, dy3) per frame
        [(-1, -2), (1, -4), (0, -6)],
        [(0, -3), (-1, -5), (1, -7)],
        [(1, -2), (0, -4), (-1, -6)],
        [(-1, -3), (1, -5), (0, -7)],
    ]
    smokes = [(SMOKE_1, 2), (SMOKE_2, 1), (SMOKE_3, 1)]
    for i, (color, size) in enumerate(smokes):
        dx, dy = offsets[frame][i]
        px, py = sx + dx, sy + dy
        if size == 2:
            draw.rectangle([px, py, px + 1, py + 1], fill=color)
        else:
            draw.point((px, py), fill=color)


def draw_plaguedoctor(draw, ox, oy, direction, frame):
    bob = [0, -1, 0, -1][frame]
    leg_spread = [-2, 0, 2, 0][frame]

    base_y = oy + 27 + bob
    body_cx = ox + 16
    body_cy = base_y - 10
    head_cy = body_cy - 10

    if direction == DOWN:
        # === Legs ===
        draw.rectangle([body_cx - 5 + leg_spread, body_cy + 5,
                        body_cx - 2 + leg_spread, base_y], fill=ROBE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, body_cy + 5,
                        body_cx + 5 - leg_spread, base_y], fill=ROBE_DARK, outline=OUTLINE)
        # Boots
        draw.rectangle([body_cx - 5 + leg_spread, base_y - 3,
                        body_cx - 2 + leg_spread, base_y], fill=BLACK, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, base_y - 3,
                        body_cx + 5 - leg_spread, base_y], fill=BLACK, outline=OUTLINE)

        # === Body - dark flowing robes ===
        ellipse(draw, body_cx, body_cy, 7, 6, ROBE_MID)
        # Robe fold details for depth
        draw.rectangle([body_cx - 4, body_cy - 3, body_cx - 2, body_cy + 3], fill=ROBE_DARK)
        draw.rectangle([body_cx + 2, body_cy - 2, body_cx + 4, body_cy + 2], fill=ROBE_LIGHT)
        # Robe hem highlight
        draw.line([body_cx - 5, body_cy + 5, body_cx + 5, body_cy + 5], fill=ROBE_LIGHT)

        # Belt with vials
        draw.rectangle([body_cx - 7, body_cy + 3, body_cx + 7, body_cy + 5],
                       fill=BELT, outline=OUTLINE)
        draw.rectangle([body_cx - 1, body_cy + 3, body_cx + 1, body_cy + 5], fill=BELT_BUCKLE)
        # Potion vials on belt
        draw.point((body_cx - 4, body_cy + 4), fill=VIAL_GREEN)
        draw.point((body_cx - 5, body_cy + 4), fill=VIAL_GLASS)
        draw.point((body_cx + 4, body_cy + 4), fill=VIAL_AMBER)
        draw.point((body_cx + 5, body_cy + 4), fill=VIAL_GLASS)

        # === Arms ===
        draw.rectangle([body_cx - 9, body_cy - 3, body_cx - 6, body_cy + 3],
                       fill=ROBE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 6, body_cy - 3, body_cx + 9, body_cy + 3],
                       fill=ROBE_DARK, outline=OUTLINE)
        # Gloves
        draw.rectangle([body_cx - 9, body_cy + 1, body_cx - 6, body_cy + 3],
                       fill=GLOVE, outline=OUTLINE)
        draw.rectangle([body_cx + 6, body_cy + 1, body_cx + 9, body_cy + 3],
                       fill=GLOVE, outline=OUTLINE)

        # === Censer in right hand ===
        censer_sway = [-1, 0, 1, 0][frame]
        cx_c = body_cx + 9 + censer_sway
        cy_c = body_cy + 3
        draw.ellipse([cx_c - 2, cy_c, cx_c + 2, cy_c + 3],
                     fill=CENSER_METAL, outline=OUTLINE)
        draw.point((cx_c, cy_c + 1), fill=CENSER_LIGHT)
        # Chain hint
        draw.point((cx_c, cy_c - 1), fill=CENSER_DARK)

        # Green smoke from censer
        draw_smoke(draw, cx_c, cy_c - 1, frame)

        # === Head - plague mask (front view) ===
        # Tall hat crown (drawn first, behind brim)
        # Main tall crown shape
        draw.rectangle([body_cx - 5, head_cy - 8, body_cx + 5, head_cy - 1],
                       fill=HAT_DARK, outline=OUTLINE)
        # Rounded top of crown
        ellipse(draw, body_cx, head_cy - 8, 5, 3, HAT_DARK)
        # Hat crown highlight
        draw.line([body_cx - 1, head_cy - 10, body_cx - 1, head_cy - 3], fill=HAT_MID)
        # Wide brim
        draw.rectangle([body_cx - 9, head_cy, body_cx + 9, head_cy + 2],
                       fill=HAT_DARK, outline=OUTLINE)
        # Hat band
        draw.rectangle([body_cx - 6, head_cy - 1, body_cx + 6, head_cy + 1],
                       fill=HAT_BAND, outline=None)
        # Buckle on hat band
        draw.rectangle([body_cx - 1, head_cy - 1, body_cx + 1, head_cy + 1],
                       fill=HAT_BUCKLE)

        # Mask face
        ellipse(draw, body_cx, head_cy + 4, 5, 4, MASK_WHITE)
        # Mask shadow for depth
        draw.arc([body_cx - 5, head_cy, body_cx + 5, head_cy + 8],
                 start=30, end=150, fill=MASK_SHADOW)

        # Stitching detail on mask
        draw.point((body_cx - 2, head_cy + 5), fill=MASK_STITCH)
        draw.point((body_cx + 2, head_cy + 5), fill=MASK_STITCH)

        # BIG BEAK - the signature feature, extends well below the face
        # Main beak shape - large diamond/kite pointing down
        draw.polygon([
            (body_cx, head_cy + 3),       # top of beak (at nose bridge)
            (body_cx - 3, head_cy + 6),   # left widest point
            (body_cx, head_cy + 11),      # bottom tip (long!)
            (body_cx + 3, head_cy + 6),   # right widest point
        ], fill=BEAK, outline=OUTLINE)
        # Beak center line (stitching ridge)
        draw.line([body_cx, head_cy + 4, body_cx, head_cy + 10], fill=BEAK_STITCH)
        # Curved tip shading
        draw.point((body_cx, head_cy + 10), fill=BEAK_TIP)
        draw.point((body_cx, head_cy + 11), fill=BEAK_DARK)
        # Nostril dots on beak
        draw.point((body_cx - 1, head_cy + 8), fill=NOSTRIL)
        draw.point((body_cx + 1, head_cy + 8), fill=NOSTRIL)
        # Beak cross-stitch marks
        draw.point((body_cx - 1, head_cy + 6), fill=BEAK_STITCH)
        draw.point((body_cx + 1, head_cy + 6), fill=BEAK_STITCH)

        # Red lens eyes - larger and more dramatic
        ellipse(draw, body_cx - 3, head_cy + 3, 2, 2, LENS_RED)
        ellipse(draw, body_cx + 3, head_cy + 3, 2, 2, LENS_RED)
        # Inner glow
        draw.point((body_cx - 3, head_cy + 3), fill=LENS_GLOW)
        draw.point((body_cx + 3, head_cy + 3), fill=LENS_GLOW)
        # Bright center dot
        draw.point((body_cx - 2, head_cy + 2), fill=LENS_BRIGHT)
        draw.point((body_cx + 2, head_cy + 2), fill=LENS_BRIGHT)
        # Reflection lines
        draw.point((body_cx - 4, head_cy + 2), fill=LENS_REFLECT)
        draw.point((body_cx + 4, head_cy + 2), fill=LENS_REFLECT)

    elif direction == UP:
        # === Legs ===
        draw.rectangle([body_cx - 5 + leg_spread, body_cy + 5,
                        body_cx - 2 + leg_spread, base_y], fill=ROBE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, body_cy + 5,
                        body_cx + 5 - leg_spread, base_y], fill=ROBE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 5 + leg_spread, base_y - 3,
                        body_cx - 2 + leg_spread, base_y], fill=BLACK, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, base_y - 3,
                        body_cx + 5 - leg_spread, base_y], fill=BLACK, outline=OUTLINE)

        # === Body - back of robes ===
        ellipse(draw, body_cx, body_cy, 7, 6, ROBE_MID)
        ellipse(draw, body_cx, body_cy - 1, 5, 4, ROBE_DARK)
        # Robe back fold detail
        draw.line([body_cx, body_cy - 4, body_cx, body_cy + 4], fill=ROBE_LIGHT)

        # Belt (visible from behind)
        draw.rectangle([body_cx - 7, body_cy + 3, body_cx + 7, body_cy + 5],
                       fill=BELT, outline=OUTLINE)
        # Vials visible from behind
        draw.point((body_cx - 4, body_cy + 4), fill=VIAL_GREEN)
        draw.point((body_cx + 4, body_cy + 4), fill=VIAL_AMBER)

        # === Arms ===
        draw.rectangle([body_cx - 9, body_cy - 3, body_cx - 6, body_cy + 3],
                       fill=ROBE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 6, body_cy - 3, body_cx + 9, body_cy + 3],
                       fill=ROBE_DARK, outline=OUTLINE)
        # Gloves
        draw.rectangle([body_cx - 9, body_cy + 1, body_cx - 6, body_cy + 3],
                       fill=GLOVE, outline=OUTLINE)
        draw.rectangle([body_cx + 6, body_cy + 1, body_cx + 9, body_cy + 3],
                       fill=GLOVE, outline=OUTLINE)

        # Censer in right hand (visible from behind)
        censer_sway = [-1, 0, 1, 0][frame]
        cx_c = body_cx + 9 + censer_sway
        cy_c = body_cy + 3
        draw.ellipse([cx_c - 2, cy_c, cx_c + 2, cy_c + 3],
                     fill=CENSER_METAL, outline=OUTLINE)
        draw_smoke(draw, cx_c, cy_c - 1, frame)

        # === Head (back of tall hat) ===
        # Tall crown from behind
        draw.rectangle([body_cx - 5, head_cy - 8, body_cx + 5, head_cy - 1],
                       fill=HAT_DARK, outline=OUTLINE)
        ellipse(draw, body_cx, head_cy - 8, 5, 3, HAT_DARK)
        # Crown back highlight
        draw.line([body_cx + 1, head_cy - 10, body_cx + 1, head_cy - 3], fill=HAT_MID)
        # Brim
        draw.rectangle([body_cx - 9, head_cy, body_cx + 9, head_cy + 2],
                       fill=HAT_DARK, outline=OUTLINE)
        # Back of head/mask visible under brim
        ellipse(draw, body_cx, head_cy + 3, 6, 4, ROBE_DARK)
        # Hat band (visible from behind)
        draw.rectangle([body_cx - 6, head_cy - 1, body_cx + 6, head_cy + 1],
                       fill=HAT_BAND, outline=None)

    elif direction == LEFT:
        # === Legs (side view) ===
        draw.rectangle([body_cx - 1 - leg_spread, body_cy + 5,
                        body_cx + 2 - leg_spread, base_y], fill=ROBE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 1 - leg_spread, base_y - 3,
                        body_cx + 2 - leg_spread, base_y], fill=BLACK, outline=OUTLINE)
        draw.rectangle([body_cx - 4 + leg_spread, body_cy + 5,
                        body_cx - 1 + leg_spread, base_y], fill=ROBE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 4 + leg_spread, base_y - 3,
                        body_cx - 1 + leg_spread, base_y], fill=BLACK, outline=OUTLINE)

        # === Body ===
        ellipse(draw, body_cx - 1, body_cy, 6, 6, ROBE_MID)
        ellipse(draw, body_cx - 1, body_cy - 1, 4, 4, ROBE_DARK)
        # Robe side fold
        draw.line([body_cx - 3, body_cy - 3, body_cx - 3, body_cy + 3], fill=ROBE_LIGHT)
        # Belt
        draw.rectangle([body_cx - 7, body_cy + 3, body_cx + 5, body_cy + 5],
                       fill=BELT, outline=OUTLINE)
        draw.rectangle([body_cx - 2, body_cy + 3, body_cx, body_cy + 5], fill=BELT_BUCKLE)
        # Vials on belt (side view - fewer visible)
        draw.point((body_cx + 2, body_cy + 4), fill=VIAL_GREEN)
        draw.point((body_cx + 3, body_cy + 4), fill=VIAL_AMBER)

        # === Arm (front - holding censer) ===
        draw.rectangle([body_cx - 7, body_cy - 2, body_cx - 4, body_cy + 3],
                       fill=ROBE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 7, body_cy + 1, body_cx - 4, body_cy + 3],
                       fill=GLOVE, outline=OUTLINE)
        # Censer
        censer_sway = [-1, 0, 1, 0][frame]
        cx_c = body_cx - 8
        cy_c = body_cy + 3 + censer_sway
        draw.ellipse([cx_c - 2, cy_c, cx_c + 2, cy_c + 3],
                     fill=CENSER_METAL, outline=OUTLINE)
        draw.point((cx_c, cy_c + 1), fill=CENSER_LIGHT)
        draw.point((cx_c, cy_c - 1), fill=CENSER_DARK)
        # Green smoke
        draw_smoke(draw, cx_c, cy_c - 1, frame)

        # === Head (side, facing left) - beak prominent ===
        # Tall hat (side view)
        draw.rectangle([body_cx - 5, head_cy - 8, body_cx + 3, head_cy - 1],
                       fill=HAT_DARK, outline=OUTLINE)
        ellipse(draw, body_cx - 1, head_cy - 8, 4, 3, HAT_DARK)
        # Hat side highlight
        draw.line([body_cx - 3, head_cy - 10, body_cx - 3, head_cy - 3], fill=HAT_MID)
        # Wide brim
        draw.rectangle([body_cx - 9, head_cy, body_cx + 4, head_cy + 2],
                       fill=HAT_DARK, outline=OUTLINE)
        # Hat band
        draw.rectangle([body_cx - 6, head_cy - 1, body_cx + 3, head_cy + 1],
                       fill=HAT_BAND, outline=None)
        # Buckle on hat band
        draw.rectangle([body_cx - 2, head_cy - 1, body_cx, head_cy + 1],
                       fill=HAT_BUCKLE)

        # Mask (side view)
        ellipse(draw, body_cx - 2, head_cy + 4, 4, 3, MASK_WHITE)
        # Mask shadow
        draw.point((body_cx, head_cy + 5), fill=MASK_SHADOW)
        # Mask stitching
        draw.point((body_cx - 1, head_cy + 5), fill=MASK_STITCH)

        # Beak pointing left - long and prominent (side view)
        draw.polygon([
            (body_cx - 5, head_cy + 3),    # top base
            (body_cx - 12, head_cy + 5),   # tip (far left, long!)
            (body_cx - 5, head_cy + 7),    # bottom base
        ], fill=BEAK, outline=OUTLINE)
        # Beak center stitching line
        draw.line([body_cx - 6, head_cy + 5, body_cx - 11, head_cy + 5], fill=BEAK_STITCH)
        # Curved tip
        draw.point((body_cx - 12, head_cy + 5), fill=BEAK_DARK)
        # Nostril
        draw.point((body_cx - 9, head_cy + 4), fill=NOSTRIL)

        # Red lens eye (side - one visible)
        ellipse(draw, body_cx - 4, head_cy + 3, 2, 2, LENS_RED)
        draw.point((body_cx - 4, head_cy + 3), fill=LENS_GLOW)
        draw.point((body_cx - 3, head_cy + 2), fill=LENS_BRIGHT)
        draw.point((body_cx - 5, head_cy + 2), fill=LENS_REFLECT)

    elif direction == RIGHT:
        # === Legs ===
        draw.rectangle([body_cx - 1 + leg_spread, body_cy + 5,
                        body_cx + 2 + leg_spread, base_y], fill=ROBE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 1 + leg_spread, base_y - 3,
                        body_cx + 2 + leg_spread, base_y], fill=BLACK, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, body_cy + 5,
                        body_cx + 5 - leg_spread, base_y], fill=ROBE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, base_y - 3,
                        body_cx + 5 - leg_spread, base_y], fill=BLACK, outline=OUTLINE)

        # === Body ===
        ellipse(draw, body_cx + 1, body_cy, 6, 6, ROBE_MID)
        ellipse(draw, body_cx + 1, body_cy - 1, 4, 4, ROBE_DARK)
        # Robe side fold
        draw.line([body_cx + 3, body_cy - 3, body_cx + 3, body_cy + 3], fill=ROBE_LIGHT)
        # Belt
        draw.rectangle([body_cx - 5, body_cy + 3, body_cx + 7, body_cy + 5],
                       fill=BELT, outline=OUTLINE)
        draw.rectangle([body_cx, body_cy + 3, body_cx + 2, body_cy + 5], fill=BELT_BUCKLE)
        # Vials on belt
        draw.point((body_cx - 3, body_cy + 4), fill=VIAL_GREEN)
        draw.point((body_cx - 2, body_cy + 4), fill=VIAL_AMBER)

        # === Arm (holding censer) ===
        draw.rectangle([body_cx + 4, body_cy - 2, body_cx + 7, body_cy + 3],
                       fill=ROBE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 4, body_cy + 1, body_cx + 7, body_cy + 3],
                       fill=GLOVE, outline=OUTLINE)
        # Censer
        censer_sway = [-1, 0, 1, 0][frame]
        cx_c = body_cx + 8
        cy_c = body_cy + 3 + censer_sway
        draw.ellipse([cx_c - 2, cy_c, cx_c + 2, cy_c + 3],
                     fill=CENSER_METAL, outline=OUTLINE)
        draw.point((cx_c, cy_c + 1), fill=CENSER_LIGHT)
        draw.point((cx_c, cy_c - 1), fill=CENSER_DARK)
        # Green smoke
        draw_smoke(draw, cx_c, cy_c - 1, frame)

        # === Head (side, facing right) - beak prominent ===
        # Tall hat (side view)
        draw.rectangle([body_cx - 3, head_cy - 8, body_cx + 5, head_cy - 1],
                       fill=HAT_DARK, outline=OUTLINE)
        ellipse(draw, body_cx + 1, head_cy - 8, 4, 3, HAT_DARK)
        # Hat side highlight
        draw.line([body_cx + 3, head_cy - 10, body_cx + 3, head_cy - 3], fill=HAT_MID)
        # Wide brim
        draw.rectangle([body_cx - 4, head_cy, body_cx + 9, head_cy + 2],
                       fill=HAT_DARK, outline=OUTLINE)
        # Hat band
        draw.rectangle([body_cx - 3, head_cy - 1, body_cx + 6, head_cy + 1],
                       fill=HAT_BAND, outline=None)
        # Buckle on hat band
        draw.rectangle([body_cx, head_cy - 1, body_cx + 2, head_cy + 1],
                       fill=HAT_BUCKLE)

        # Mask (side view)
        ellipse(draw, body_cx + 2, head_cy + 4, 4, 3, MASK_WHITE)
        # Mask shadow
        draw.point((body_cx, head_cy + 5), fill=MASK_SHADOW)
        # Mask stitching
        draw.point((body_cx + 1, head_cy + 5), fill=MASK_STITCH)

        # Beak pointing right - long and prominent (side view)
        draw.polygon([
            (body_cx + 5, head_cy + 3),    # top base
            (body_cx + 12, head_cy + 5),   # tip (far right, long!)
            (body_cx + 5, head_cy + 7),    # bottom base
        ], fill=BEAK, outline=OUTLINE)
        # Beak center stitching line
        draw.line([body_cx + 6, head_cy + 5, body_cx + 11, head_cy + 5], fill=BEAK_STITCH)
        # Curved tip
        draw.point((body_cx + 12, head_cy + 5), fill=BEAK_DARK)
        # Nostril
        draw.point((body_cx + 9, head_cy + 4), fill=NOSTRIL)

        # Red lens eye (side - one visible)
        ellipse(draw, body_cx + 4, head_cy + 3, 2, 2, LENS_RED)
        draw.point((body_cx + 4, head_cy + 3), fill=LENS_GLOW)
        draw.point((body_cx + 3, head_cy + 2), fill=LENS_BRIGHT)
        draw.point((body_cx + 5, head_cy + 2), fill=LENS_REFLECT)


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
