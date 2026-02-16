#!/usr/bin/env python3
"""Generate sprites/raptor.png — 4-column x 4-row character spritesheet.

128x128 PNG, 32x32 per frame.
Row layout: Down=0, Up=1, Left=2, Right=3
4 walking animation frames per direction.

Style matches Spaceman/Gladiator/Wraith: big round head, round body, small limbs.
Theme: Bird of prey — sharp beak, feathered body, taloned feet, wing-arms.
Color palette: gold/brown/amber with dark outlines, red/orange accent plumage.
"""

from PIL import Image, ImageDraw

FRAME_SIZE = 32
COLS = 4
ROWS = 4
IMG_W = FRAME_SIZE * COLS   # 128
IMG_H = FRAME_SIZE * ROWS   # 128

# Colors
OUTLINE = (40, 30, 15)
BODY_DARK = (90, 60, 25)
BODY = (140, 95, 35)
BODY_LIGHT = (180, 130, 50)
BELLY = (210, 180, 120)
BELLY_TEX1 = (195, 165, 105)     # belly feather texture dot color 1
BELLY_TEX2 = (220, 195, 140)     # belly feather texture dot color 2
HEAD = (160, 110, 40)
HEAD_DARK = (120, 80, 30)
BEAK_DARK = (130, 90, 20)        # darker beak tip
BEAK = (210, 170, 50)
BEAK_NOSTRIL = (80, 55, 20)      # nostril dot
EYE = (230, 190, 40)             # fiercer amber/gold eye
EYE_CORE = (40, 30, 15)
EYE_GLINT = (255, 255, 230)      # bright glint pixel
EYE_BROW = (50, 35, 15)          # dark brow line
WING_DARK = (80, 55, 20)
WING = (120, 85, 30)
WING_LIGHT = (160, 120, 45)
WING_MID = (100, 70, 25)         # mid-tone for layered feathers
WING_ACCENT = (190, 80, 25)      # red/orange feather tips on wings
TALON = (90, 70, 30)
TALON_TIP = (45, 30, 15)         # darker sharp tips
CREST = (190, 130, 40)
CREST_ACCENT = (200, 80, 25)     # red/orange accent on crest tips
CREST_BRIGHT = (220, 150, 45)    # brighter crest highlight
TAIL_DARK = (85, 58, 22)
TAIL = (130, 90, 32)
TAIL_LIGHT = (175, 125, 48)
TAIL_ACCENT = (185, 75, 22)      # red/orange tail feather tip

DOWN, UP, LEFT, RIGHT = 0, 1, 2, 3


def ellipse(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    """Draw an outlined ellipse centered at (cx, cy)."""
    draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=outline)


def draw_belly_texture(draw, cx, cy, w, h):
    """Draw subtle feather texture dots on the belly patch."""
    for row in range(0, h, 2):
        offset = 1 if (row // 2) % 2 == 0 else 0
        for col in range(offset, w, 3):
            px = cx - w // 2 + col
            py = cy + row
            color = BELLY_TEX1 if (row + col) % 4 < 2 else BELLY_TEX2
            draw.point((px, py), fill=color)


def draw_talons(draw, cx, base_y, direction, leg_step):
    """Draw wider, 3-toed talons with darker sharp tips."""
    if direction == DOWN:
        for side in [-1, 1]:
            foot_x = cx + side * 4 + side * leg_step
            # 3 distinct toes spread apart
            draw.line([(foot_x, base_y), (foot_x - 3, base_y + 3)], fill=TALON, width=1)
            draw.line([(foot_x, base_y), (foot_x, base_y + 3)], fill=TALON, width=1)
            draw.line([(foot_x, base_y), (foot_x + 3, base_y + 3)], fill=TALON, width=1)
            # Dark sharp tips
            draw.point((foot_x - 3, base_y + 3), fill=TALON_TIP)
            draw.point((foot_x, base_y + 3), fill=TALON_TIP)
            draw.point((foot_x + 3, base_y + 3), fill=TALON_TIP)

    elif direction == UP:
        for side in [-1, 1]:
            foot_x = cx + side * 4 + side * leg_step
            draw.line([(foot_x, base_y), (foot_x - 3, base_y + 3)], fill=TALON, width=1)
            draw.line([(foot_x, base_y), (foot_x, base_y + 3)], fill=TALON, width=1)
            draw.line([(foot_x, base_y), (foot_x + 3, base_y + 3)], fill=TALON, width=1)
            draw.point((foot_x - 3, base_y + 3), fill=TALON_TIP)
            draw.point((foot_x, base_y + 3), fill=TALON_TIP)
            draw.point((foot_x + 3, base_y + 3), fill=TALON_TIP)

    elif direction == LEFT:
        foot_x = cx - 2 + leg_step
        draw.line([(foot_x, base_y), (foot_x - 3, base_y + 3)], fill=TALON, width=1)
        draw.line([(foot_x, base_y), (foot_x - 1, base_y + 3)], fill=TALON, width=1)
        draw.line([(foot_x, base_y), (foot_x + 2, base_y + 3)], fill=TALON, width=1)
        draw.point((foot_x - 3, base_y + 3), fill=TALON_TIP)
        draw.point((foot_x - 1, base_y + 3), fill=TALON_TIP)
        draw.point((foot_x + 2, base_y + 3), fill=TALON_TIP)
        foot_x2 = cx + 2 + leg_step
        draw.line([(foot_x2, base_y), (foot_x2 - 3, base_y + 3)], fill=TALON, width=1)
        draw.line([(foot_x2, base_y), (foot_x2 - 1, base_y + 3)], fill=TALON, width=1)
        draw.line([(foot_x2, base_y), (foot_x2 + 2, base_y + 3)], fill=TALON, width=1)
        draw.point((foot_x2 - 3, base_y + 3), fill=TALON_TIP)
        draw.point((foot_x2 - 1, base_y + 3), fill=TALON_TIP)
        draw.point((foot_x2 + 2, base_y + 3), fill=TALON_TIP)

    elif direction == RIGHT:
        foot_x = cx + 2 - leg_step
        draw.line([(foot_x, base_y), (foot_x + 3, base_y + 3)], fill=TALON, width=1)
        draw.line([(foot_x, base_y), (foot_x + 1, base_y + 3)], fill=TALON, width=1)
        draw.line([(foot_x, base_y), (foot_x - 2, base_y + 3)], fill=TALON, width=1)
        draw.point((foot_x + 3, base_y + 3), fill=TALON_TIP)
        draw.point((foot_x + 1, base_y + 3), fill=TALON_TIP)
        draw.point((foot_x - 2, base_y + 3), fill=TALON_TIP)
        foot_x2 = cx - 2 - leg_step
        draw.line([(foot_x2, base_y), (foot_x2 + 3, base_y + 3)], fill=TALON, width=1)
        draw.line([(foot_x2, base_y), (foot_x2 + 1, base_y + 3)], fill=TALON, width=1)
        draw.line([(foot_x2, base_y), (foot_x2 - 2, base_y + 3)], fill=TALON, width=1)
        draw.point((foot_x2 + 3, base_y + 3), fill=TALON_TIP)
        draw.point((foot_x2 + 1, base_y + 3), fill=TALON_TIP)
        draw.point((foot_x2 - 2, base_y + 3), fill=TALON_TIP)


def draw_tail(draw, cx, body_cy, base_y, direction, frame):
    """Draw a fan of 2-3 tail feathers that sway with walk animation."""
    tail_sway = [-1, 0, 1, 0][frame]

    if direction == UP:
        # Tail fans out below and behind the body (visible from back)
        # Center feather
        draw.polygon([
            (cx + tail_sway, body_cy + 4),
            (cx - 1 + tail_sway, base_y + 1),
            (cx + 1 + tail_sway, base_y + 1),
        ], fill=TAIL, outline=OUTLINE)
        draw.point((cx + tail_sway, base_y), fill=TAIL_ACCENT)
        # Left feather
        draw.polygon([
            (cx - 2, body_cy + 4),
            (cx - 4 + tail_sway, base_y + 2),
            (cx - 2 + tail_sway, base_y + 2),
        ], fill=TAIL_DARK, outline=OUTLINE)
        draw.point((cx - 3 + tail_sway, base_y + 1), fill=TAIL_ACCENT)
        # Right feather
        draw.polygon([
            (cx + 2, body_cy + 4),
            (cx + 2 + tail_sway, base_y + 2),
            (cx + 4 + tail_sway, base_y + 2),
        ], fill=TAIL_LIGHT, outline=OUTLINE)
        draw.point((cx + 3 + tail_sway, base_y + 1), fill=TAIL_ACCENT)

    elif direction == LEFT:
        # Tail extends to the right behind the body
        tx = cx + 6
        ty = body_cy + 3
        # Top feather
        draw.polygon([
            (cx + 4, ty - 1),
            (tx + 2 + tail_sway, ty - 2),
            (tx + 1 + tail_sway, ty),
        ], fill=TAIL_LIGHT, outline=OUTLINE)
        draw.point((tx + 2 + tail_sway, ty - 2), fill=TAIL_ACCENT)
        # Middle feather
        draw.polygon([
            (cx + 4, ty + 1),
            (tx + 3 + tail_sway, ty + 1),
            (tx + 2 + tail_sway, ty + 3),
        ], fill=TAIL, outline=OUTLINE)
        draw.point((tx + 3 + tail_sway, ty + 1), fill=TAIL_ACCENT)
        # Bottom feather
        draw.polygon([
            (cx + 4, ty + 3),
            (tx + 1 + tail_sway, ty + 4),
            (tx + tail_sway, ty + 5),
        ], fill=TAIL_DARK, outline=OUTLINE)
        draw.point((tx + 1 + tail_sway, ty + 4), fill=TAIL_ACCENT)

    elif direction == RIGHT:
        # Tail extends to the left behind the body
        tx = cx - 6
        ty = body_cy + 3
        # Top feather
        draw.polygon([
            (cx - 4, ty - 1),
            (tx - 2 - tail_sway, ty - 2),
            (tx - 1 - tail_sway, ty),
        ], fill=TAIL_LIGHT, outline=OUTLINE)
        draw.point((tx - 2 - tail_sway, ty - 2), fill=TAIL_ACCENT)
        # Middle feather
        draw.polygon([
            (cx - 4, ty + 1),
            (tx - 3 - tail_sway, ty + 1),
            (tx - 2 - tail_sway, ty + 3),
        ], fill=TAIL, outline=OUTLINE)
        draw.point((tx - 3 - tail_sway, ty + 1), fill=TAIL_ACCENT)
        # Bottom feather
        draw.polygon([
            (cx - 4, ty + 3),
            (tx - 1 - tail_sway, ty + 4),
            (tx - tail_sway, ty + 5),
        ], fill=TAIL_DARK, outline=OUTLINE)
        draw.point((tx - 1 - tail_sway, ty + 4), fill=TAIL_ACCENT)


def draw_crest(draw, cx, head_cy, direction):
    """Draw a multi-feather crest with red/orange accent tips."""
    if direction == DOWN or direction == UP:
        # Center tall feather
        draw.polygon([
            (cx - 1, head_cy - 9),
            (cx + 1, head_cy - 9),
            (cx + 1, head_cy - 4),
            (cx - 1, head_cy - 4),
        ], fill=CREST, outline=OUTLINE)
        draw.point((cx, head_cy - 9), fill=CREST_ACCENT)
        # Left feather
        draw.polygon([
            (cx - 4, head_cy - 7),
            (cx - 2, head_cy - 8),
            (cx - 1, head_cy - 4),
            (cx - 3, head_cy - 4),
        ], fill=CREST, outline=OUTLINE)
        draw.point((cx - 3, head_cy - 7), fill=CREST_ACCENT)
        # Right feather
        draw.polygon([
            (cx + 4, head_cy - 7),
            (cx + 2, head_cy - 8),
            (cx + 1, head_cy - 4),
            (cx + 3, head_cy - 4),
        ], fill=CREST, outline=OUTLINE)
        draw.point((cx + 3, head_cy - 7), fill=CREST_ACCENT)
        # Far left small feather
        draw.polygon([
            (cx - 6, head_cy - 5),
            (cx - 4, head_cy - 6),
            (cx - 3, head_cy - 3),
        ], fill=CREST_BRIGHT, outline=OUTLINE)
        draw.point((cx - 5, head_cy - 5), fill=CREST_ACCENT)
        # Far right small feather
        draw.polygon([
            (cx + 6, head_cy - 5),
            (cx + 4, head_cy - 6),
            (cx + 3, head_cy - 3),
        ], fill=CREST_BRIGHT, outline=OUTLINE)
        draw.point((cx + 5, head_cy - 5), fill=CREST_ACCENT)

    elif direction == LEFT:
        # Crest swept to the right (trailing)
        draw.polygon([
            (cx, head_cy - 9),
            (cx + 2, head_cy - 8),
            (cx + 2, head_cy - 4),
            (cx, head_cy - 4),
        ], fill=CREST, outline=OUTLINE)
        draw.point((cx + 1, head_cy - 9), fill=CREST_ACCENT)
        draw.polygon([
            (cx + 3, head_cy - 7),
            (cx + 5, head_cy - 6),
            (cx + 3, head_cy - 3),
        ], fill=CREST, outline=OUTLINE)
        draw.point((cx + 4, head_cy - 7), fill=CREST_ACCENT)
        draw.polygon([
            (cx + 5, head_cy - 5),
            (cx + 7, head_cy - 4),
            (cx + 5, head_cy - 2),
        ], fill=CREST_BRIGHT, outline=OUTLINE)
        draw.point((cx + 6, head_cy - 5), fill=CREST_ACCENT)

    elif direction == RIGHT:
        # Crest swept to the left (trailing)
        draw.polygon([
            (cx, head_cy - 9),
            (cx - 2, head_cy - 8),
            (cx - 2, head_cy - 4),
            (cx, head_cy - 4),
        ], fill=CREST, outline=OUTLINE)
        draw.point((cx - 1, head_cy - 9), fill=CREST_ACCENT)
        draw.polygon([
            (cx - 3, head_cy - 7),
            (cx - 5, head_cy - 6),
            (cx - 3, head_cy - 3),
        ], fill=CREST, outline=OUTLINE)
        draw.point((cx - 4, head_cy - 7), fill=CREST_ACCENT)
        draw.polygon([
            (cx - 5, head_cy - 5),
            (cx - 7, head_cy - 4),
            (cx - 5, head_cy - 2),
        ], fill=CREST_BRIGHT, outline=OUTLINE)
        draw.point((cx - 6, head_cy - 5), fill=CREST_ACCENT)


def draw_eyes(draw, cx, head_cy, direction):
    """Draw fiercer, larger eyes with glint and brow line."""
    if direction == DOWN:
        # Left eye — larger
        draw.rectangle([cx - 6, head_cy - 2, cx - 2, head_cy + 1], fill=EYE)
        draw.point((cx - 4, head_cy - 1), fill=EYE_CORE)
        draw.point((cx - 3, head_cy), fill=EYE_CORE)
        draw.point((cx - 5, head_cy - 2), fill=EYE_GLINT)  # glint
        # Brow line
        draw.line([(cx - 7, head_cy - 3), (cx - 2, head_cy - 3)], fill=EYE_BROW, width=1)

        # Right eye — larger
        draw.rectangle([cx + 2, head_cy - 2, cx + 6, head_cy + 1], fill=EYE)
        draw.point((cx + 4, head_cy - 1), fill=EYE_CORE)
        draw.point((cx + 3, head_cy), fill=EYE_CORE)
        draw.point((cx + 5, head_cy - 2), fill=EYE_GLINT)  # glint
        # Brow line
        draw.line([(cx + 2, head_cy - 3), (cx + 7, head_cy - 3)], fill=EYE_BROW, width=1)

    elif direction == LEFT:
        # Single eye facing left — larger
        draw.rectangle([cx - 6, head_cy - 2, cx - 2, head_cy + 1], fill=EYE)
        draw.point((cx - 4, head_cy - 1), fill=EYE_CORE)
        draw.point((cx - 3, head_cy), fill=EYE_CORE)
        draw.point((cx - 5, head_cy - 2), fill=EYE_GLINT)
        # Brow line
        draw.line([(cx - 7, head_cy - 3), (cx - 2, head_cy - 3)], fill=EYE_BROW, width=1)

    elif direction == RIGHT:
        # Single eye facing right — larger
        draw.rectangle([cx + 2, head_cy - 2, cx + 6, head_cy + 1], fill=EYE)
        draw.point((cx + 4, head_cy - 1), fill=EYE_CORE)
        draw.point((cx + 3, head_cy), fill=EYE_CORE)
        draw.point((cx + 5, head_cy - 2), fill=EYE_GLINT)
        # Brow line
        draw.line([(cx + 2, head_cy - 3), (cx + 7, head_cy - 3)], fill=EYE_BROW, width=1)


def draw_beak(draw, cx, head_cy, direction):
    """Draw a sharper, more prominent beak with darker tip and nostril dot."""
    if direction == DOWN:
        # Beak pointing down — more prominent
        draw.polygon([
            (cx - 3, head_cy + 2),
            (cx + 3, head_cy + 2),
            (cx, head_cy + 7),
        ], fill=BEAK, outline=OUTLINE)
        # Darker tip
        draw.polygon([
            (cx - 1, head_cy + 5),
            (cx + 1, head_cy + 5),
            (cx, head_cy + 7),
        ], fill=BEAK_DARK, outline=None)
        # Nostril dot
        draw.point((cx - 1, head_cy + 3), fill=BEAK_NOSTRIL)

    elif direction == LEFT:
        # Beak pointing left — sharper
        draw.polygon([
            (cx - 10, head_cy),
            (cx - 5, head_cy - 2),
            (cx - 5, head_cy + 2),
        ], fill=BEAK, outline=OUTLINE)
        # Darker tip
        draw.polygon([
            (cx - 10, head_cy),
            (cx - 8, head_cy - 1),
            (cx - 8, head_cy + 1),
        ], fill=BEAK_DARK, outline=None)
        # Nostril
        draw.point((cx - 7, head_cy - 1), fill=BEAK_NOSTRIL)

    elif direction == RIGHT:
        # Beak pointing right — sharper
        draw.polygon([
            (cx + 10, head_cy),
            (cx + 5, head_cy - 2),
            (cx + 5, head_cy + 2),
        ], fill=BEAK, outline=OUTLINE)
        # Darker tip
        draw.polygon([
            (cx + 10, head_cy),
            (cx + 8, head_cy - 1),
            (cx + 8, head_cy + 1),
        ], fill=BEAK_DARK, outline=None)
        # Nostril
        draw.point((cx + 7, head_cy - 1), fill=BEAK_NOSTRIL)


def draw_wing_layered(draw, cx, body_cy, wing_flap, side, trailing=False):
    """Draw a wing with 2-3 layered feather shading lines and accent tips.

    side: -1 for left, +1 for right
    trailing: True for the wing behind the body (darker)
    """
    wing_x = cx + side * 9
    wing_y = body_cy + wing_flap
    base_color = WING_DARK if trailing else WING

    # Main wing shape
    draw.polygon([
        (cx + side * 7, body_cy - 3),
        (wing_x + side * 2, wing_y - 1),
        (wing_x + side * 3, wing_y + 3),
        (wing_x + side * 1, wing_y + 5),
        (cx + side * 7, body_cy + 4),
    ], fill=base_color, outline=OUTLINE)

    if not trailing:
        # Layered feather lines (3 shades)
        draw.line([(cx + side * 7, body_cy - 1),
                   (wing_x + side * 2, wing_y + 1)],
                  fill=WING_LIGHT, width=1)
        draw.line([(cx + side * 7, body_cy + 1),
                   (wing_x + side * 2, wing_y + 3)],
                  fill=WING_MID, width=1)
        draw.line([(cx + side * 7, body_cy + 3),
                   (wing_x + side * 1, wing_y + 4)],
                  fill=WING_DARK, width=1)
        # Red/orange accent at feather tips
        draw.point((wing_x + side * 3, wing_y + 3), fill=WING_ACCENT)
        draw.point((wing_x + side * 2, wing_y + 4), fill=WING_ACCENT)
        draw.point((wing_x + side * 1, wing_y + 5), fill=WING_ACCENT)
    else:
        # Trailing wing still gets some feather detail
        draw.line([(cx + side * 6, body_cy),
                   (wing_x + side * 1, wing_y + 2)],
                  fill=WING_MID, width=1)
        draw.point((wing_x + side * 2, wing_y + 4), fill=WING_ACCENT)


def draw_wing_side(draw, cx, body_cy, wing_flap, side, trailing=False):
    """Draw wing for side view (left or right facing) with layered feathers.

    side: -1 for left-extending wing, +1 for right-extending wing
    trailing: True for the wing behind the body (darker)
    """
    wing_x = cx + side * 7
    wing_y = body_cy + wing_flap
    base_color = WING_DARK if trailing else WING

    draw.polygon([
        (cx + side * 5, body_cy - 3),
        (wing_x + side * 2, wing_y - 1),
        (wing_x + side * 3, wing_y + 3),
        (wing_x + side * 1, wing_y + 5),
        (cx + side * 5, body_cy + 4),
    ], fill=base_color, outline=OUTLINE)

    if not trailing:
        # Layered feather lines
        draw.line([(cx + side * 5, body_cy - 1),
                   (wing_x + side * 2, wing_y + 1)],
                  fill=WING_LIGHT, width=1)
        draw.line([(cx + side * 5, body_cy + 1),
                   (wing_x + side * 2, wing_y + 3)],
                  fill=WING_MID, width=1)
        draw.line([(cx + side * 5, body_cy + 3),
                   (wing_x + side * 1, wing_y + 4)],
                  fill=WING_DARK, width=1)
        # Accent tips
        draw.point((wing_x + side * 3, wing_y + 3), fill=WING_ACCENT)
        draw.point((wing_x + side * 2, wing_y + 4), fill=WING_ACCENT)
        draw.point((wing_x + side * 1, wing_y + 5), fill=WING_ACCENT)
    else:
        draw.line([(cx + side * 5, body_cy),
                   (wing_x + side * 1, wing_y + 2)],
                  fill=WING_MID, width=1)
        draw.point((wing_x + side * 2, wing_y + 4), fill=WING_ACCENT)


def draw_raptor(draw, ox, oy, direction, frame):
    """Draw a single raptor frame at offset (ox, oy).

    Proportions match other characters: big round head ~11px, body ~8px tall.
    Bird of prey with sharp features and wing-like arms.
    """
    bob = [0, -1, 0, -1][frame]
    wing_flap = [-1, 1, -1, 0][frame]
    leg_step = [0, 1, 0, -1][frame]

    base_y = oy + 28 + bob
    body_cx = ox + 16
    body_cy = base_y - 9
    head_cy = body_cy - 10

    if direction == DOWN:
        # --- Taloned feet ---
        draw_talons(draw, body_cx, base_y, DOWN, leg_step)

        # --- Legs ---
        draw.line([(body_cx - 4 - leg_step, base_y), (body_cx - 3, body_cy + 5)], fill=BODY_DARK, width=2)
        draw.line([(body_cx + 4 + leg_step, base_y), (body_cx + 3, body_cy + 5)], fill=BODY_DARK, width=2)

        # --- Wings (arms) ---
        for side in [-1, 1]:
            draw_wing_layered(draw, body_cx, body_cy, wing_flap, side)

        # --- Body ---
        draw.polygon([
            (body_cx - 7, body_cy - 4),
            (body_cx + 7, body_cy - 4),
            (body_cx + 8, body_cy + 5),
            (body_cx - 8, body_cy + 5),
        ], fill=BODY, outline=OUTLINE)
        # Belly patch
        draw.polygon([
            (body_cx - 4, body_cy - 1),
            (body_cx + 4, body_cy - 1),
            (body_cx + 5, body_cy + 4),
            (body_cx - 5, body_cy + 4),
        ], fill=BELLY, outline=None)
        # Belly feather texture
        draw_belly_texture(draw, body_cx, body_cy, 8, 5)

        # --- Head ---
        ellipse(draw, body_cx, head_cy, 8, 7, HEAD)

        # --- Crest ---
        draw_crest(draw, body_cx, head_cy, DOWN)

        # --- Eyes ---
        draw_eyes(draw, body_cx, head_cy, DOWN)

        # --- Beak ---
        draw_beak(draw, body_cx, head_cy, DOWN)

    elif direction == UP:
        # --- Tail (visible from back) ---
        draw_tail(draw, body_cx, body_cy, base_y, UP, frame)

        # --- Taloned feet ---
        draw_talons(draw, body_cx, base_y, UP, leg_step)

        # --- Legs ---
        draw.line([(body_cx - 4 - leg_step, base_y), (body_cx - 3, body_cy + 5)], fill=BODY_DARK, width=2)
        draw.line([(body_cx + 4 + leg_step, base_y), (body_cx + 3, body_cy + 5)], fill=BODY_DARK, width=2)

        # --- Wings ---
        for side in [-1, 1]:
            draw_wing_layered(draw, body_cx, body_cy, wing_flap, side)

        # --- Body (back view — darker) ---
        draw.polygon([
            (body_cx - 7, body_cy - 4),
            (body_cx + 7, body_cy - 4),
            (body_cx + 8, body_cy + 5),
            (body_cx - 8, body_cy + 5),
        ], fill=BODY, outline=OUTLINE)
        draw.polygon([
            (body_cx - 5, body_cy - 2),
            (body_cx + 5, body_cy - 2),
            (body_cx + 6, body_cy + 4),
            (body_cx - 6, body_cy + 4),
        ], fill=BODY_DARK, outline=None)

        # --- Head (back view) ---
        ellipse(draw, body_cx, head_cy, 8, 7, HEAD)
        ellipse(draw, body_cx, head_cy, 6, 5, HEAD_DARK)

        # --- Crest ---
        draw_crest(draw, body_cx, head_cy, UP)

    elif direction == LEFT:
        # --- Tail (extending to right) ---
        draw_tail(draw, body_cx, body_cy, base_y, LEFT, frame)

        # --- Feet ---
        draw_talons(draw, body_cx, base_y, LEFT, leg_step)

        # --- Legs ---
        draw.line([(body_cx - 2 + leg_step, base_y), (body_cx - 1, body_cy + 5)], fill=BODY_DARK, width=2)
        draw.line([(body_cx + 2 + leg_step, base_y), (body_cx + 1, body_cy + 5)], fill=BODY_DARK, width=2)

        # --- Wing (trailing behind — right side) ---
        draw_wing_side(draw, body_cx, body_cy, wing_flap, +1, trailing=True)

        # --- Body ---
        draw.polygon([
            (body_cx - 6, body_cy - 4),
            (body_cx + 6, body_cy - 4),
            (body_cx + 7, body_cy + 5),
            (body_cx - 7, body_cy + 5),
        ], fill=BODY, outline=OUTLINE)
        draw.polygon([
            (body_cx - 4, body_cy - 1),
            (body_cx + 3, body_cy - 1),
            (body_cx + 4, body_cy + 4),
            (body_cx - 3, body_cy + 4),
        ], fill=BELLY, outline=None)
        # Belly feather texture
        draw_belly_texture(draw, body_cx, body_cy, 6, 5)

        # --- Wing (front — left side) ---
        draw_wing_side(draw, body_cx, body_cy, wing_flap, -1, trailing=False)

        # --- Head (facing left) ---
        ellipse(draw, body_cx - 1, head_cy, 7, 7, HEAD)

        # --- Eyes ---
        draw_eyes(draw, body_cx - 1, head_cy, LEFT)

        # --- Beak ---
        draw_beak(draw, body_cx - 1, head_cy, LEFT)

        # --- Crest ---
        draw_crest(draw, body_cx - 1, head_cy, LEFT)

    elif direction == RIGHT:
        # --- Tail (extending to left) ---
        draw_tail(draw, body_cx, body_cy, base_y, RIGHT, frame)

        # --- Feet ---
        draw_talons(draw, body_cx, base_y, RIGHT, leg_step)

        # --- Legs ---
        draw.line([(body_cx + 2 - leg_step, base_y), (body_cx + 1, body_cy + 5)], fill=BODY_DARK, width=2)
        draw.line([(body_cx - 2 - leg_step, base_y), (body_cx - 1, body_cy + 5)], fill=BODY_DARK, width=2)

        # --- Wing (trailing behind — left side) ---
        draw_wing_side(draw, body_cx, body_cy, wing_flap, -1, trailing=True)

        # --- Body ---
        draw.polygon([
            (body_cx - 6, body_cy - 4),
            (body_cx + 6, body_cy - 4),
            (body_cx + 7, body_cy + 5),
            (body_cx - 7, body_cy + 5),
        ], fill=BODY, outline=OUTLINE)
        draw.polygon([
            (body_cx - 3, body_cy - 1),
            (body_cx + 4, body_cy - 1),
            (body_cx + 3, body_cy + 4),
            (body_cx - 4, body_cy + 4),
        ], fill=BELLY, outline=None)
        # Belly feather texture
        draw_belly_texture(draw, body_cx, body_cy, 6, 5)

        # --- Wing (front — right side) ---
        draw_wing_side(draw, body_cx, body_cy, wing_flap, +1, trailing=False)

        # --- Head (facing right) ---
        ellipse(draw, body_cx + 1, head_cy, 7, 7, HEAD)

        # --- Eyes ---
        draw_eyes(draw, body_cx + 1, head_cy, RIGHT)

        # --- Beak ---
        draw_beak(draw, body_cx + 1, head_cy, RIGHT)

        # --- Crest ---
        draw_crest(draw, body_cx + 1, head_cy, RIGHT)


def main():
    img = Image.new("RGBA", (IMG_W, IMG_H), (0, 0, 0, 0))

    for direction in range(ROWS):
        for frame in range(COLS):
            frame_img = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE), (0, 0, 0, 0))
            frame_draw = ImageDraw.Draw(frame_img)
            draw_raptor(frame_draw, 0, 0, direction, frame)
            img.paste(frame_img, (frame * FRAME_SIZE, direction * FRAME_SIZE))

    img.save("sprites/raptor.png")
    print(f"Generated sprites/raptor.png ({IMG_W}x{IMG_H})")


if __name__ == "__main__":
    main()
