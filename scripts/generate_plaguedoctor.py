#!/usr/bin/env python3
"""Generate sprites/plaguedoctor.png — 4-column x 4-row character spritesheet.

256x256 PNG, 64x64 per frame.
Row layout: Down=0, Up=1, Left=2, Right=3
4 walking animation frames per direction.

Style matches Spaceman/Gladiator: big round head, round body, small limbs, dark outlines.
Theme: Plague Doctor — dark robes, tall hat, long beaked plague mask, censer with green fumes.
Enhanced 64x64: more detailed beak mask (curved with nostril dots), leather coat with
buckle details, potion bottles on belt (colored dots), longer coat tails.
"""

from PIL import Image, ImageDraw

FRAME_SIZE = 64
COLS = 4
ROWS = 4
IMG_W = FRAME_SIZE * COLS   # 256
IMG_H = FRAME_SIZE * ROWS   # 256

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
VIAL_BLUE = (45, 90, 160)
VIAL_GLASS = (100, 120, 110)
VIAL_CORK = (160, 130, 80)
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
# New detail colors
COAT_BUCKLE = (120, 100, 45)
COAT_STRAP = (65, 55, 50)
COAT_TAIL = (42, 36, 48)

DOWN, UP, LEFT, RIGHT = 0, 1, 2, 3


def ellipse(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=outline)


def draw_smoke(draw, sx, sy, frame):
    """Draw animated green smoke puffs rising from a point. Uses per-frame offsets."""
    # Four smoke particles that shift position each frame
    offsets = [
        # (dx, dy) per frame — 4 particles
        [(-2, -4), (2, -8), (0, -12), (-1, -16)],
        [(0, -6), (-2, -10), (2, -14), (1, -18)],
        [(2, -4), (0, -8), (-2, -12), (0, -16)],
        [(-2, -6), (2, -10), (0, -14), (-1, -18)],
    ]
    smokes = [(SMOKE_1, 4), (SMOKE_2, 3), (SMOKE_3, 2), (SMOKE_3, 2)]
    for i, (color, size) in enumerate(smokes):
        dx, dy = offsets[frame][i]
        px, py = sx + dx, sy + dy
        if size >= 4:
            draw.rectangle([px, py, px + 3, py + 3], fill=color)
        elif size >= 3:
            draw.rectangle([px, py, px + 2, py + 2], fill=color)
        else:
            draw.rectangle([px, py, px + 1, py + 1], fill=color)


def draw_potion_bottle(draw, cx, cy, color):
    """Draw a small potion bottle with cork and glass detail."""
    # Glass body
    draw.rectangle([cx - 2, cy, cx + 2, cy + 5], fill=VIAL_GLASS, outline=OUTLINE)
    # Liquid inside
    draw.rectangle([cx - 1, cy + 1, cx + 1, cy + 4], fill=color)
    # Cork
    draw.rectangle([cx - 1, cy - 2, cx + 1, cy], fill=VIAL_CORK)


def draw_coat_buckles(draw, body_cx, body_cy):
    """Draw leather coat buckle details on the front."""
    # Three buckle straps across the chest
    for i in range(3):
        y = body_cy - 4 + i * 5
        draw.line([(body_cx - 6, y), (body_cx + 6, y)], fill=COAT_STRAP, width=1)
        # Small buckle in center
        draw.rectangle([body_cx - 2, y - 1, body_cx + 2, y + 1], fill=COAT_BUCKLE)


def draw_coat_tails(draw, body_cx, body_cy, base_y, frame):
    """Draw longer coat tails that extend below the body."""
    sway = [-2, 0, 2, 0][frame]
    # Left coat tail
    draw.polygon([
        (body_cx - 8, body_cy + 8),
        (body_cx - 4, body_cy + 8),
        (body_cx - 3 + sway, base_y + 4),
        (body_cx - 9 + sway, base_y + 4),
    ], fill=COAT_TAIL, outline=OUTLINE)
    # Right coat tail
    draw.polygon([
        (body_cx + 4, body_cy + 8),
        (body_cx + 8, body_cy + 8),
        (body_cx + 9 - sway, base_y + 4),
        (body_cx + 3 - sway, base_y + 4),
    ], fill=COAT_TAIL, outline=OUTLINE)


def draw_plaguedoctor(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]

    base_y = oy + 54 + bob
    body_cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    if direction == DOWN:
        # === Coat tails (behind legs) ===
        draw_coat_tails(draw, body_cx, body_cy, base_y, frame)

        # === Legs ===
        draw.rectangle([body_cx - 10 + leg_spread, body_cy + 10,
                        body_cx - 4 + leg_spread, base_y], fill=ROBE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 4 - leg_spread, body_cy + 10,
                        body_cx + 10 - leg_spread, base_y], fill=ROBE_DARK, outline=OUTLINE)
        # Boots
        draw.rectangle([body_cx - 10 + leg_spread, base_y - 6,
                        body_cx - 4 + leg_spread, base_y], fill=BLACK, outline=OUTLINE)
        draw.rectangle([body_cx + 4 - leg_spread, base_y - 6,
                        body_cx + 10 - leg_spread, base_y], fill=BLACK, outline=OUTLINE)

        # === Body - dark flowing robes ===
        ellipse(draw, body_cx, body_cy, 14, 12, ROBE_MID)
        # Robe fold details for depth
        draw.rectangle([body_cx - 8, body_cy - 6, body_cx - 4, body_cy + 6], fill=ROBE_DARK)
        draw.rectangle([body_cx + 4, body_cy - 4, body_cx + 8, body_cy + 4], fill=ROBE_LIGHT)
        # Robe hem highlight
        draw.line([body_cx - 10, body_cy + 10, body_cx + 10, body_cy + 10], fill=ROBE_LIGHT, width=2)

        # Leather coat buckle details
        draw_coat_buckles(draw, body_cx, body_cy)

        # Belt with potion vials
        draw.rectangle([body_cx - 14, body_cy + 6, body_cx + 14, body_cy + 10],
                       fill=BELT, outline=OUTLINE)
        draw.rectangle([body_cx - 2, body_cy + 6, body_cx + 2, body_cy + 10], fill=BELT_BUCKLE)
        # Potion bottles on belt
        draw_potion_bottle(draw, body_cx - 9, body_cy + 6, VIAL_GREEN)
        draw_potion_bottle(draw, body_cx + 9, body_cy + 6, VIAL_AMBER)
        draw_potion_bottle(draw, body_cx - 5, body_cy + 7, VIAL_BLUE)

        # === Arms ===
        draw.rectangle([body_cx - 18, body_cy - 6, body_cx - 12, body_cy + 6],
                       fill=ROBE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 12, body_cy - 6, body_cx + 18, body_cy + 6],
                       fill=ROBE_DARK, outline=OUTLINE)
        # Gloves
        draw.rectangle([body_cx - 18, body_cy + 2, body_cx - 12, body_cy + 6],
                       fill=GLOVE, outline=OUTLINE)
        draw.rectangle([body_cx + 12, body_cy + 2, body_cx + 18, body_cy + 6],
                       fill=GLOVE, outline=OUTLINE)

        # === Censer in right hand ===
        censer_sway = [-2, 0, 2, 0][frame]
        cx_c = body_cx + 18 + censer_sway
        cy_c = body_cy + 6
        draw.ellipse([cx_c - 4, cy_c, cx_c + 4, cy_c + 6],
                     fill=CENSER_METAL, outline=OUTLINE)
        draw.rectangle([cx_c - 1, cy_c + 1, cx_c + 1, cy_c + 3], fill=CENSER_LIGHT)
        # Chain hint
        draw.line([(cx_c, cy_c - 2), (cx_c, cy_c)], fill=CENSER_DARK, width=2)

        # Green smoke from censer
        draw_smoke(draw, cx_c, cy_c - 2, frame)

        # === Head - plague mask (front view) ===
        # Tall hat crown (drawn first, behind brim)
        draw.rectangle([body_cx - 10, head_cy - 16, body_cx + 10, head_cy - 2],
                       fill=HAT_DARK, outline=OUTLINE)
        # Rounded top of crown
        ellipse(draw, body_cx, head_cy - 16, 10, 6, HAT_DARK)
        # Hat crown highlight
        draw.line([body_cx - 2, head_cy - 20, body_cx - 2, head_cy - 6], fill=HAT_MID, width=2)
        # Wide brim
        draw.rectangle([body_cx - 18, head_cy, body_cx + 18, head_cy + 4],
                       fill=HAT_DARK, outline=OUTLINE)
        # Hat band
        draw.rectangle([body_cx - 12, head_cy - 2, body_cx + 12, head_cy + 2],
                       fill=HAT_BAND, outline=None)
        # Buckle on hat band
        draw.rectangle([body_cx - 2, head_cy - 2, body_cx + 2, head_cy + 2],
                       fill=HAT_BUCKLE)

        # Mask face
        ellipse(draw, body_cx, head_cy + 8, 10, 8, MASK_WHITE)
        # Mask shadow for depth
        draw.arc([body_cx - 10, head_cy, body_cx + 10, head_cy + 16],
                 start=30, end=150, fill=MASK_SHADOW, width=2)

        # Stitching detail on mask
        draw.point((body_cx - 4, head_cy + 10), fill=MASK_STITCH)
        draw.point((body_cx + 4, head_cy + 10), fill=MASK_STITCH)
        draw.point((body_cx - 6, head_cy + 8), fill=MASK_STITCH)
        draw.point((body_cx + 6, head_cy + 8), fill=MASK_STITCH)

        # BIG BEAK - the signature feature, extends well below the face
        # Main beak shape - large diamond/kite pointing down, curved
        draw.polygon([
            (body_cx, head_cy + 6),        # top of beak (at nose bridge)
            (body_cx - 6, head_cy + 12),   # left widest point
            (body_cx - 2, head_cy + 20),   # left narrowing
            (body_cx, head_cy + 22),       # bottom tip (long!)
            (body_cx + 2, head_cy + 20),   # right narrowing
            (body_cx + 6, head_cy + 12),   # right widest point
        ], fill=BEAK, outline=OUTLINE)
        # Beak center line (stitching ridge)
        draw.line([body_cx, head_cy + 8, body_cx, head_cy + 20], fill=BEAK_STITCH, width=2)
        # Curved tip shading
        draw.rectangle([body_cx - 1, head_cy + 20, body_cx + 1, head_cy + 22], fill=BEAK_TIP)
        draw.point((body_cx, head_cy + 22), fill=BEAK_DARK)
        # Nostril dots on beak (larger, more prominent)
        draw.ellipse([body_cx - 3, head_cy + 15, body_cx - 1, head_cy + 17], fill=NOSTRIL)
        draw.ellipse([body_cx + 1, head_cy + 15, body_cx + 3, head_cy + 17], fill=NOSTRIL)
        # Beak cross-stitch marks
        draw.point((body_cx - 3, head_cy + 12), fill=BEAK_STITCH)
        draw.point((body_cx + 3, head_cy + 12), fill=BEAK_STITCH)
        draw.point((body_cx - 2, head_cy + 14), fill=BEAK_STITCH)
        draw.point((body_cx + 2, head_cy + 14), fill=BEAK_STITCH)

        # Red lens eyes - larger and more dramatic
        ellipse(draw, body_cx - 6, head_cy + 6, 4, 4, LENS_RED)
        ellipse(draw, body_cx + 6, head_cy + 6, 4, 4, LENS_RED)
        # Inner glow
        draw.rectangle([body_cx - 7, head_cy + 5, body_cx - 5, head_cy + 7], fill=LENS_GLOW)
        draw.rectangle([body_cx + 5, head_cy + 5, body_cx + 7, head_cy + 7], fill=LENS_GLOW)
        # Bright center dot
        draw.point((body_cx - 4, head_cy + 4), fill=LENS_BRIGHT)
        draw.point((body_cx + 4, head_cy + 4), fill=LENS_BRIGHT)
        # Reflection lines
        draw.point((body_cx - 8, head_cy + 4), fill=LENS_REFLECT)
        draw.point((body_cx + 8, head_cy + 4), fill=LENS_REFLECT)

    elif direction == UP:
        # === Coat tails (behind legs) ===
        draw_coat_tails(draw, body_cx, body_cy, base_y, frame)

        # === Legs ===
        draw.rectangle([body_cx - 10 + leg_spread, body_cy + 10,
                        body_cx - 4 + leg_spread, base_y], fill=ROBE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 4 - leg_spread, body_cy + 10,
                        body_cx + 10 - leg_spread, base_y], fill=ROBE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 10 + leg_spread, base_y - 6,
                        body_cx - 4 + leg_spread, base_y], fill=BLACK, outline=OUTLINE)
        draw.rectangle([body_cx + 4 - leg_spread, base_y - 6,
                        body_cx + 10 - leg_spread, base_y], fill=BLACK, outline=OUTLINE)

        # === Body - back of robes ===
        ellipse(draw, body_cx, body_cy, 14, 12, ROBE_MID)
        ellipse(draw, body_cx, body_cy - 2, 10, 8, ROBE_DARK)
        # Robe back fold detail
        draw.line([body_cx, body_cy - 8, body_cx, body_cy + 8], fill=ROBE_LIGHT, width=2)

        # Belt (visible from behind)
        draw.rectangle([body_cx - 14, body_cy + 6, body_cx + 14, body_cy + 10],
                       fill=BELT, outline=OUTLINE)
        # Vials visible from behind
        draw_potion_bottle(draw, body_cx - 8, body_cy + 6, VIAL_GREEN)
        draw_potion_bottle(draw, body_cx + 8, body_cy + 6, VIAL_AMBER)

        # === Arms ===
        draw.rectangle([body_cx - 18, body_cy - 6, body_cx - 12, body_cy + 6],
                       fill=ROBE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 12, body_cy - 6, body_cx + 18, body_cy + 6],
                       fill=ROBE_DARK, outline=OUTLINE)
        # Gloves
        draw.rectangle([body_cx - 18, body_cy + 2, body_cx - 12, body_cy + 6],
                       fill=GLOVE, outline=OUTLINE)
        draw.rectangle([body_cx + 12, body_cy + 2, body_cx + 18, body_cy + 6],
                       fill=GLOVE, outline=OUTLINE)

        # Censer in right hand (visible from behind)
        censer_sway = [-2, 0, 2, 0][frame]
        cx_c = body_cx + 18 + censer_sway
        cy_c = body_cy + 6
        draw.ellipse([cx_c - 4, cy_c, cx_c + 4, cy_c + 6],
                     fill=CENSER_METAL, outline=OUTLINE)
        draw_smoke(draw, cx_c, cy_c - 2, frame)

        # === Head (back of tall hat) ===
        # Tall crown from behind
        draw.rectangle([body_cx - 10, head_cy - 16, body_cx + 10, head_cy - 2],
                       fill=HAT_DARK, outline=OUTLINE)
        ellipse(draw, body_cx, head_cy - 16, 10, 6, HAT_DARK)
        # Crown back highlight
        draw.line([body_cx + 2, head_cy - 20, body_cx + 2, head_cy - 6], fill=HAT_MID, width=2)
        # Brim
        draw.rectangle([body_cx - 18, head_cy, body_cx + 18, head_cy + 4],
                       fill=HAT_DARK, outline=OUTLINE)
        # Back of head/mask visible under brim
        ellipse(draw, body_cx, head_cy + 6, 12, 8, ROBE_DARK)
        # Hat band (visible from behind)
        draw.rectangle([body_cx - 12, head_cy - 2, body_cx + 12, head_cy + 2],
                       fill=HAT_BAND, outline=None)

    elif direction == LEFT:
        # === Coat tails (behind legs, side view) ===
        sway = [-2, 0, 2, 0][frame]
        draw.polygon([
            (body_cx + 2, body_cy + 8),
            (body_cx + 6, body_cy + 8),
            (body_cx + 7 - sway, base_y + 4),
            (body_cx + 1 - sway, base_y + 4),
        ], fill=COAT_TAIL, outline=OUTLINE)

        # === Legs (side view) ===
        draw.rectangle([body_cx - 2 - leg_spread, body_cy + 10,
                        body_cx + 4 - leg_spread, base_y], fill=ROBE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 2 - leg_spread, base_y - 6,
                        body_cx + 4 - leg_spread, base_y], fill=BLACK, outline=OUTLINE)
        draw.rectangle([body_cx - 8 + leg_spread, body_cy + 10,
                        body_cx - 2 + leg_spread, base_y], fill=ROBE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 8 + leg_spread, base_y - 6,
                        body_cx - 2 + leg_spread, base_y], fill=BLACK, outline=OUTLINE)

        # === Body ===
        ellipse(draw, body_cx - 2, body_cy, 12, 12, ROBE_MID)
        ellipse(draw, body_cx - 2, body_cy - 2, 8, 8, ROBE_DARK)
        # Robe side fold
        draw.line([body_cx - 6, body_cy - 6, body_cx - 6, body_cy + 6], fill=ROBE_LIGHT, width=2)
        # Coat buckle details (side view)
        for i in range(2):
            y = body_cy - 2 + i * 5
            draw.line([(body_cx - 6, y), (body_cx + 4, y)], fill=COAT_STRAP, width=1)
            draw.rectangle([body_cx - 2, y - 1, body_cx + 1, y + 1], fill=COAT_BUCKLE)
        # Belt
        draw.rectangle([body_cx - 14, body_cy + 6, body_cx + 10, body_cy + 10],
                       fill=BELT, outline=OUTLINE)
        draw.rectangle([body_cx - 4, body_cy + 6, body_cx, body_cy + 10], fill=BELT_BUCKLE)
        # Potion vials on belt (side view - fewer visible)
        draw_potion_bottle(draw, body_cx + 4, body_cy + 6, VIAL_GREEN)
        draw_potion_bottle(draw, body_cx + 8, body_cy + 7, VIAL_AMBER)

        # === Arm (front - holding censer) ===
        draw.rectangle([body_cx - 14, body_cy - 4, body_cx - 8, body_cy + 6],
                       fill=ROBE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 14, body_cy + 2, body_cx - 8, body_cy + 6],
                       fill=GLOVE, outline=OUTLINE)
        # Censer
        censer_sway = [-2, 0, 2, 0][frame]
        cx_c = body_cx - 16
        cy_c = body_cy + 6 + censer_sway
        draw.ellipse([cx_c - 4, cy_c, cx_c + 4, cy_c + 6],
                     fill=CENSER_METAL, outline=OUTLINE)
        draw.rectangle([cx_c - 1, cy_c + 1, cx_c + 1, cy_c + 3], fill=CENSER_LIGHT)
        draw.line([(cx_c, cy_c - 2), (cx_c, cy_c)], fill=CENSER_DARK, width=2)
        # Green smoke
        draw_smoke(draw, cx_c, cy_c - 2, frame)

        # === Head (side, facing left) - beak prominent ===
        # Tall hat (side view)
        draw.rectangle([body_cx - 10, head_cy - 16, body_cx + 6, head_cy - 2],
                       fill=HAT_DARK, outline=OUTLINE)
        ellipse(draw, body_cx - 2, head_cy - 16, 8, 6, HAT_DARK)
        # Hat side highlight
        draw.line([body_cx - 6, head_cy - 20, body_cx - 6, head_cy - 6], fill=HAT_MID, width=2)
        # Wide brim
        draw.rectangle([body_cx - 18, head_cy, body_cx + 8, head_cy + 4],
                       fill=HAT_DARK, outline=OUTLINE)
        # Hat band
        draw.rectangle([body_cx - 12, head_cy - 2, body_cx + 6, head_cy + 2],
                       fill=HAT_BAND, outline=None)
        # Buckle on hat band
        draw.rectangle([body_cx - 4, head_cy - 2, body_cx, head_cy + 2],
                       fill=HAT_BUCKLE)

        # Mask (side view)
        ellipse(draw, body_cx - 4, head_cy + 8, 8, 6, MASK_WHITE)
        # Mask shadow
        draw.rectangle([body_cx - 1, head_cy + 9, body_cx + 1, head_cy + 11], fill=MASK_SHADOW)
        # Mask stitching
        draw.point((body_cx - 2, head_cy + 10), fill=MASK_STITCH)
        draw.point((body_cx - 4, head_cy + 10), fill=MASK_STITCH)

        # Beak pointing left - long and prominent (side view, curved)
        draw.polygon([
            (body_cx - 10, head_cy + 6),    # top base
            (body_cx - 22, head_cy + 9),    # tip area
            (body_cx - 24, head_cy + 10),   # tip (far left, long!)
            (body_cx - 22, head_cy + 11),   # tip area
            (body_cx - 10, head_cy + 14),   # bottom base
        ], fill=BEAK, outline=OUTLINE)
        # Beak center stitching line
        draw.line([body_cx - 12, head_cy + 10, body_cx - 22, head_cy + 10], fill=BEAK_STITCH, width=2)
        # Curved tip
        draw.rectangle([body_cx - 25, head_cy + 9, body_cx - 23, head_cy + 11], fill=BEAK_DARK)
        # Nostril dots
        draw.ellipse([body_cx - 19, head_cy + 7, body_cx - 17, head_cy + 9], fill=NOSTRIL)
        # Cross-stitch marks
        draw.point((body_cx - 14, head_cy + 8), fill=BEAK_STITCH)
        draw.point((body_cx - 16, head_cy + 9), fill=BEAK_STITCH)
        draw.point((body_cx - 14, head_cy + 12), fill=BEAK_STITCH)

        # Red lens eye (side - one visible)
        ellipse(draw, body_cx - 8, head_cy + 6, 4, 4, LENS_RED)
        draw.rectangle([body_cx - 9, head_cy + 5, body_cx - 7, head_cy + 7], fill=LENS_GLOW)
        draw.point((body_cx - 6, head_cy + 4), fill=LENS_BRIGHT)
        draw.point((body_cx - 10, head_cy + 4), fill=LENS_REFLECT)

    elif direction == RIGHT:
        # === Coat tails (behind legs, side view) ===
        sway = [-2, 0, 2, 0][frame]
        draw.polygon([
            (body_cx - 6, body_cy + 8),
            (body_cx - 2, body_cy + 8),
            (body_cx - 1 + sway, base_y + 4),
            (body_cx - 7 + sway, base_y + 4),
        ], fill=COAT_TAIL, outline=OUTLINE)

        # === Legs ===
        draw.rectangle([body_cx - 2 + leg_spread, body_cy + 10,
                        body_cx + 4 + leg_spread, base_y], fill=ROBE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 2 + leg_spread, base_y - 6,
                        body_cx + 4 + leg_spread, base_y], fill=BLACK, outline=OUTLINE)
        draw.rectangle([body_cx + 4 - leg_spread, body_cy + 10,
                        body_cx + 10 - leg_spread, base_y], fill=ROBE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 4 - leg_spread, base_y - 6,
                        body_cx + 10 - leg_spread, base_y], fill=BLACK, outline=OUTLINE)

        # === Body ===
        ellipse(draw, body_cx + 2, body_cy, 12, 12, ROBE_MID)
        ellipse(draw, body_cx + 2, body_cy - 2, 8, 8, ROBE_DARK)
        # Robe side fold
        draw.line([body_cx + 6, body_cy - 6, body_cx + 6, body_cy + 6], fill=ROBE_LIGHT, width=2)
        # Coat buckle details (side view)
        for i in range(2):
            y = body_cy - 2 + i * 5
            draw.line([(body_cx - 4, y), (body_cx + 6, y)], fill=COAT_STRAP, width=1)
            draw.rectangle([body_cx - 1, y - 1, body_cx + 2, y + 1], fill=COAT_BUCKLE)
        # Belt
        draw.rectangle([body_cx - 10, body_cy + 6, body_cx + 14, body_cy + 10],
                       fill=BELT, outline=OUTLINE)
        draw.rectangle([body_cx, body_cy + 6, body_cx + 4, body_cy + 10], fill=BELT_BUCKLE)
        # Potion vials on belt
        draw_potion_bottle(draw, body_cx - 6, body_cy + 6, VIAL_GREEN)
        draw_potion_bottle(draw, body_cx - 10, body_cy + 7, VIAL_AMBER)

        # === Arm (holding censer) ===
        draw.rectangle([body_cx + 8, body_cy - 4, body_cx + 14, body_cy + 6],
                       fill=ROBE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 8, body_cy + 2, body_cx + 14, body_cy + 6],
                       fill=GLOVE, outline=OUTLINE)
        # Censer
        censer_sway = [-2, 0, 2, 0][frame]
        cx_c = body_cx + 16
        cy_c = body_cy + 6 + censer_sway
        draw.ellipse([cx_c - 4, cy_c, cx_c + 4, cy_c + 6],
                     fill=CENSER_METAL, outline=OUTLINE)
        draw.rectangle([cx_c - 1, cy_c + 1, cx_c + 1, cy_c + 3], fill=CENSER_LIGHT)
        draw.line([(cx_c, cy_c - 2), (cx_c, cy_c)], fill=CENSER_DARK, width=2)
        # Green smoke
        draw_smoke(draw, cx_c, cy_c - 2, frame)

        # === Head (side, facing right) - beak prominent ===
        # Tall hat (side view)
        draw.rectangle([body_cx - 6, head_cy - 16, body_cx + 10, head_cy - 2],
                       fill=HAT_DARK, outline=OUTLINE)
        ellipse(draw, body_cx + 2, head_cy - 16, 8, 6, HAT_DARK)
        # Hat side highlight
        draw.line([body_cx + 6, head_cy - 20, body_cx + 6, head_cy - 6], fill=HAT_MID, width=2)
        # Wide brim
        draw.rectangle([body_cx - 8, head_cy, body_cx + 18, head_cy + 4],
                       fill=HAT_DARK, outline=OUTLINE)
        # Hat band
        draw.rectangle([body_cx - 6, head_cy - 2, body_cx + 12, head_cy + 2],
                       fill=HAT_BAND, outline=None)
        # Buckle on hat band
        draw.rectangle([body_cx, head_cy - 2, body_cx + 4, head_cy + 2],
                       fill=HAT_BUCKLE)

        # Mask (side view)
        ellipse(draw, body_cx + 4, head_cy + 8, 8, 6, MASK_WHITE)
        # Mask shadow
        draw.rectangle([body_cx - 1, head_cy + 9, body_cx + 1, head_cy + 11], fill=MASK_SHADOW)
        # Mask stitching
        draw.point((body_cx + 2, head_cy + 10), fill=MASK_STITCH)
        draw.point((body_cx + 4, head_cy + 10), fill=MASK_STITCH)

        # Beak pointing right - long and prominent (side view, curved)
        draw.polygon([
            (body_cx + 10, head_cy + 6),    # top base
            (body_cx + 22, head_cy + 9),    # tip area
            (body_cx + 24, head_cy + 10),   # tip (far right, long!)
            (body_cx + 22, head_cy + 11),   # tip area
            (body_cx + 10, head_cy + 14),   # bottom base
        ], fill=BEAK, outline=OUTLINE)
        # Beak center stitching line
        draw.line([body_cx + 12, head_cy + 10, body_cx + 22, head_cy + 10], fill=BEAK_STITCH, width=2)
        # Curved tip
        draw.rectangle([body_cx + 23, head_cy + 9, body_cx + 25, head_cy + 11], fill=BEAK_DARK)
        # Nostril dots
        draw.ellipse([body_cx + 17, head_cy + 7, body_cx + 19, head_cy + 9], fill=NOSTRIL)
        # Cross-stitch marks
        draw.point((body_cx + 14, head_cy + 8), fill=BEAK_STITCH)
        draw.point((body_cx + 16, head_cy + 9), fill=BEAK_STITCH)
        draw.point((body_cx + 14, head_cy + 12), fill=BEAK_STITCH)

        # Red lens eye (side - one visible)
        ellipse(draw, body_cx + 8, head_cy + 6, 4, 4, LENS_RED)
        draw.rectangle([body_cx + 7, head_cy + 5, body_cx + 9, head_cy + 7], fill=LENS_GLOW)
        draw.point((body_cx + 6, head_cy + 4), fill=LENS_BRIGHT)
        draw.point((body_cx + 10, head_cy + 4), fill=LENS_REFLECT)


def main():
    img = Image.new("RGBA", (IMG_W, IMG_H), (0, 0, 0, 0))

    for direction in range(ROWS):
        for frame in range(COLS):
            frame_img = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE), (0, 0, 0, 0))
            frame_draw = ImageDraw.Draw(frame_img)
            draw_plaguedoctor(frame_draw, 0, 0, direction, frame)
            img.paste(frame_img, (frame * FRAME_SIZE, direction * FRAME_SIZE))

    img.save("sprites/plaguedoctor.png")
    print(f"Generated sprites/plaguedoctor.png ({IMG_W}x{IMG_H})")


if __name__ == "__main__":
    main()
