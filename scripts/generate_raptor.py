#!/usr/bin/env python3
"""Generate sprites/raptor.png — 4-column x 4-row character spritesheet.

256x256 PNG, 64x64 per frame.
Row layout: Down=0, Up=1, Left=2, Right=3
4 walking animation frames per direction.

Style matches Spaceman/Gladiator/Wraith: big round head, round body, small limbs.
Theme: Bird of prey — sharp beak, feathered body, taloned feet, wing-arms.
Enhanced 64x64: individual feather V-marks on wings, scaled belly diamond grid,
sharper talons with highlight, plumage gradient dark back to light chest.
"""

from PIL import Image, ImageDraw

FRAME_SIZE = 64
COLS = 4
ROWS = 4
IMG_W = FRAME_SIZE * COLS   # 256
IMG_H = FRAME_SIZE * ROWS   # 256

# Colors
OUTLINE = (40, 30, 15)
BODY_DARK = (90, 60, 25)
BODY = (140, 95, 35)
BODY_LIGHT = (180, 130, 50)
BELLY = (210, 180, 120)
BELLY_TEX1 = (195, 165, 105)
BELLY_TEX2 = (220, 195, 140)
HEAD = (160, 110, 40)
HEAD_DARK = (120, 80, 30)
BEAK_DARK = (130, 90, 20)
BEAK = (210, 170, 50)
BEAK_NOSTRIL = (80, 55, 20)
EYE = (230, 190, 40)
EYE_CORE = (40, 30, 15)
EYE_GLINT = (255, 255, 230)
EYE_BROW = (50, 35, 15)
WING_DARK = (80, 55, 20)
WING = (120, 85, 30)
WING_LIGHT = (160, 120, 45)
WING_MID = (100, 70, 25)
WING_ACCENT = (190, 80, 25)
WING_FEATHER = (110, 78, 28)
TALON = (90, 70, 30)
TALON_TIP = (45, 30, 15)
TALON_HIGHLIGHT = (130, 100, 50)
CREST = (190, 130, 40)
CREST_ACCENT = (200, 80, 25)
CREST_BRIGHT = (220, 150, 45)
TAIL_DARK = (85, 58, 22)
TAIL = (130, 90, 32)
TAIL_LIGHT = (175, 125, 48)
TAIL_ACCENT = (185, 75, 22)
BELLY_SCALE = (200, 170, 110)

DOWN, UP, LEFT, RIGHT = 0, 1, 2, 3


def ellipse(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    """Draw an outlined ellipse centered at (cx, cy)."""
    draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=outline)


def draw_belly_texture(draw, cx, cy, w, h):
    """Draw diamond grid scaled belly pattern."""
    for row in range(0, h, 4):
        offset = 2 if (row // 4) % 2 == 0 else 0
        for col in range(offset, w, 6):
            px = cx - w // 2 + col
            py = cy + row
            # Diamond shape
            draw.point((px, py - 1), fill=BELLY_TEX1)
            draw.point((px - 1, py), fill=BELLY_TEX2)
            draw.point((px + 1, py), fill=BELLY_TEX2)
            draw.point((px, py + 1), fill=BELLY_TEX1)
            draw.point((px, py), fill=BELLY_SCALE)


def draw_talons(draw, cx, base_y, direction, leg_step):
    """Draw wider, 3-toed talons with darker sharp tips and highlights."""
    if direction == DOWN:
        for side in [-1, 1]:
            foot_x = cx + side * 8 + side * leg_step
            draw.line([(foot_x, base_y), (foot_x - 6, base_y + 6)], fill=TALON, width=2)
            draw.line([(foot_x, base_y), (foot_x, base_y + 6)], fill=TALON, width=2)
            draw.line([(foot_x, base_y), (foot_x + 6, base_y + 6)], fill=TALON, width=2)
            draw.point((foot_x - 6, base_y + 6), fill=TALON_TIP)
            draw.point((foot_x, base_y + 6), fill=TALON_TIP)
            draw.point((foot_x + 6, base_y + 6), fill=TALON_TIP)
            # Highlight on talon tops
            draw.point((foot_x - 3, base_y + 2), fill=TALON_HIGHLIGHT)
            draw.point((foot_x, base_y + 2), fill=TALON_HIGHLIGHT)
            draw.point((foot_x + 3, base_y + 2), fill=TALON_HIGHLIGHT)

    elif direction == UP:
        for side in [-1, 1]:
            foot_x = cx + side * 8 + side * leg_step
            draw.line([(foot_x, base_y), (foot_x - 6, base_y + 6)], fill=TALON, width=2)
            draw.line([(foot_x, base_y), (foot_x, base_y + 6)], fill=TALON, width=2)
            draw.line([(foot_x, base_y), (foot_x + 6, base_y + 6)], fill=TALON, width=2)
            draw.point((foot_x - 6, base_y + 6), fill=TALON_TIP)
            draw.point((foot_x, base_y + 6), fill=TALON_TIP)
            draw.point((foot_x + 6, base_y + 6), fill=TALON_TIP)
            draw.point((foot_x - 3, base_y + 2), fill=TALON_HIGHLIGHT)
            draw.point((foot_x + 3, base_y + 2), fill=TALON_HIGHLIGHT)

    elif direction == LEFT:
        foot_x = cx - 4 + leg_step
        draw.line([(foot_x, base_y), (foot_x - 6, base_y + 6)], fill=TALON, width=2)
        draw.line([(foot_x, base_y), (foot_x - 2, base_y + 6)], fill=TALON, width=2)
        draw.line([(foot_x, base_y), (foot_x + 4, base_y + 6)], fill=TALON, width=2)
        draw.point((foot_x - 6, base_y + 6), fill=TALON_TIP)
        draw.point((foot_x - 2, base_y + 6), fill=TALON_TIP)
        draw.point((foot_x + 4, base_y + 6), fill=TALON_TIP)
        draw.point((foot_x - 4, base_y + 2), fill=TALON_HIGHLIGHT)
        foot_x2 = cx + 4 + leg_step
        draw.line([(foot_x2, base_y), (foot_x2 - 6, base_y + 6)], fill=TALON, width=2)
        draw.line([(foot_x2, base_y), (foot_x2 - 2, base_y + 6)], fill=TALON, width=2)
        draw.line([(foot_x2, base_y), (foot_x2 + 4, base_y + 6)], fill=TALON, width=2)
        draw.point((foot_x2 - 6, base_y + 6), fill=TALON_TIP)
        draw.point((foot_x2 - 2, base_y + 6), fill=TALON_TIP)
        draw.point((foot_x2 + 4, base_y + 6), fill=TALON_TIP)
        draw.point((foot_x2 - 4, base_y + 2), fill=TALON_HIGHLIGHT)

    elif direction == RIGHT:
        foot_x = cx + 4 - leg_step
        draw.line([(foot_x, base_y), (foot_x + 6, base_y + 6)], fill=TALON, width=2)
        draw.line([(foot_x, base_y), (foot_x + 2, base_y + 6)], fill=TALON, width=2)
        draw.line([(foot_x, base_y), (foot_x - 4, base_y + 6)], fill=TALON, width=2)
        draw.point((foot_x + 6, base_y + 6), fill=TALON_TIP)
        draw.point((foot_x + 2, base_y + 6), fill=TALON_TIP)
        draw.point((foot_x - 4, base_y + 6), fill=TALON_TIP)
        draw.point((foot_x + 4, base_y + 2), fill=TALON_HIGHLIGHT)
        foot_x2 = cx - 4 - leg_step
        draw.line([(foot_x2, base_y), (foot_x2 + 6, base_y + 6)], fill=TALON, width=2)
        draw.line([(foot_x2, base_y), (foot_x2 + 2, base_y + 6)], fill=TALON, width=2)
        draw.line([(foot_x2, base_y), (foot_x2 - 4, base_y + 6)], fill=TALON, width=2)
        draw.point((foot_x2 + 6, base_y + 6), fill=TALON_TIP)
        draw.point((foot_x2 + 2, base_y + 6), fill=TALON_TIP)
        draw.point((foot_x2 - 4, base_y + 6), fill=TALON_TIP)
        draw.point((foot_x2 + 4, base_y + 2), fill=TALON_HIGHLIGHT)


def draw_tail(draw, cx, body_cy, base_y, direction, frame):
    """Draw a fan of tail feathers that sway with walk animation."""
    tail_sway = [-2, 0, 2, 0][frame]

    if direction == UP:
        draw.polygon([
            (cx + tail_sway, body_cy + 8),
            (cx - 2 + tail_sway, base_y + 2),
            (cx + 2 + tail_sway, base_y + 2),
        ], fill=TAIL, outline=OUTLINE)
        draw.point((cx + tail_sway, base_y), fill=TAIL_ACCENT)
        draw.polygon([
            (cx - 4, body_cy + 8),
            (cx - 8 + tail_sway, base_y + 4),
            (cx - 4 + tail_sway, base_y + 4),
        ], fill=TAIL_DARK, outline=OUTLINE)
        draw.point((cx - 6 + tail_sway, base_y + 2), fill=TAIL_ACCENT)
        draw.polygon([
            (cx + 4, body_cy + 8),
            (cx + 4 + tail_sway, base_y + 4),
            (cx + 8 + tail_sway, base_y + 4),
        ], fill=TAIL_LIGHT, outline=OUTLINE)
        draw.point((cx + 6 + tail_sway, base_y + 2), fill=TAIL_ACCENT)

    elif direction == LEFT:
        tx = cx + 12
        ty = body_cy + 6
        draw.polygon([
            (cx + 8, ty - 2),
            (tx + 4 + tail_sway, ty - 4),
            (tx + 2 + tail_sway, ty),
        ], fill=TAIL_LIGHT, outline=OUTLINE)
        draw.point((tx + 4 + tail_sway, ty - 4), fill=TAIL_ACCENT)
        draw.polygon([
            (cx + 8, ty + 2),
            (tx + 6 + tail_sway, ty + 2),
            (tx + 4 + tail_sway, ty + 6),
        ], fill=TAIL, outline=OUTLINE)
        draw.point((tx + 6 + tail_sway, ty + 2), fill=TAIL_ACCENT)
        draw.polygon([
            (cx + 8, ty + 6),
            (tx + 2 + tail_sway, ty + 8),
            (tx + tail_sway, ty + 10),
        ], fill=TAIL_DARK, outline=OUTLINE)
        draw.point((tx + 2 + tail_sway, ty + 8), fill=TAIL_ACCENT)

    elif direction == RIGHT:
        tx = cx - 12
        ty = body_cy + 6
        draw.polygon([
            (cx - 8, ty - 2),
            (tx - 4 - tail_sway, ty - 4),
            (tx - 2 - tail_sway, ty),
        ], fill=TAIL_LIGHT, outline=OUTLINE)
        draw.point((tx - 4 - tail_sway, ty - 4), fill=TAIL_ACCENT)
        draw.polygon([
            (cx - 8, ty + 2),
            (tx - 6 - tail_sway, ty + 2),
            (tx - 4 - tail_sway, ty + 6),
        ], fill=TAIL, outline=OUTLINE)
        draw.point((tx - 6 - tail_sway, ty + 2), fill=TAIL_ACCENT)
        draw.polygon([
            (cx - 8, ty + 6),
            (tx - 2 - tail_sway, ty + 8),
            (tx - tail_sway, ty + 10),
        ], fill=TAIL_DARK, outline=OUTLINE)
        draw.point((tx - 2 - tail_sway, ty + 8), fill=TAIL_ACCENT)


def draw_crest(draw, cx, head_cy, direction):
    """Draw a multi-feather crest with accent tips."""
    if direction == DOWN or direction == UP:
        draw.polygon([(cx - 2, head_cy - 18), (cx + 2, head_cy - 18),
                      (cx + 2, head_cy - 8), (cx - 2, head_cy - 8)], fill=CREST, outline=OUTLINE)
        draw.point((cx, head_cy - 18), fill=CREST_ACCENT)
        draw.polygon([(cx - 8, head_cy - 14), (cx - 4, head_cy - 16),
                      (cx - 2, head_cy - 8), (cx - 6, head_cy - 8)], fill=CREST, outline=OUTLINE)
        draw.point((cx - 6, head_cy - 14), fill=CREST_ACCENT)
        draw.polygon([(cx + 8, head_cy - 14), (cx + 4, head_cy - 16),
                      (cx + 2, head_cy - 8), (cx + 6, head_cy - 8)], fill=CREST, outline=OUTLINE)
        draw.point((cx + 6, head_cy - 14), fill=CREST_ACCENT)
        draw.polygon([(cx - 12, head_cy - 10), (cx - 8, head_cy - 12),
                      (cx - 6, head_cy - 6)], fill=CREST_BRIGHT, outline=OUTLINE)
        draw.point((cx - 10, head_cy - 10), fill=CREST_ACCENT)
        draw.polygon([(cx + 12, head_cy - 10), (cx + 8, head_cy - 12),
                      (cx + 6, head_cy - 6)], fill=CREST_BRIGHT, outline=OUTLINE)
        draw.point((cx + 10, head_cy - 10), fill=CREST_ACCENT)
    elif direction == LEFT:
        draw.polygon([(cx, head_cy - 18), (cx + 4, head_cy - 16),
                      (cx + 4, head_cy - 8), (cx, head_cy - 8)], fill=CREST, outline=OUTLINE)
        draw.point((cx + 2, head_cy - 18), fill=CREST_ACCENT)
        draw.polygon([(cx + 6, head_cy - 14), (cx + 10, head_cy - 12),
                      (cx + 6, head_cy - 6)], fill=CREST, outline=OUTLINE)
        draw.point((cx + 8, head_cy - 14), fill=CREST_ACCENT)
        draw.polygon([(cx + 10, head_cy - 10), (cx + 14, head_cy - 8),
                      (cx + 10, head_cy - 4)], fill=CREST_BRIGHT, outline=OUTLINE)
        draw.point((cx + 12, head_cy - 10), fill=CREST_ACCENT)
    elif direction == RIGHT:
        draw.polygon([(cx, head_cy - 18), (cx - 4, head_cy - 16),
                      (cx - 4, head_cy - 8), (cx, head_cy - 8)], fill=CREST, outline=OUTLINE)
        draw.point((cx - 2, head_cy - 18), fill=CREST_ACCENT)
        draw.polygon([(cx - 6, head_cy - 14), (cx - 10, head_cy - 12),
                      (cx - 6, head_cy - 6)], fill=CREST, outline=OUTLINE)
        draw.point((cx - 8, head_cy - 14), fill=CREST_ACCENT)
        draw.polygon([(cx - 10, head_cy - 10), (cx - 14, head_cy - 8),
                      (cx - 10, head_cy - 4)], fill=CREST_BRIGHT, outline=OUTLINE)
        draw.point((cx - 12, head_cy - 10), fill=CREST_ACCENT)


def draw_eyes(draw, cx, head_cy, direction):
    """Draw fiercer, larger eyes with glint and brow line."""
    if direction == DOWN:
        draw.rectangle([cx - 12, head_cy - 4, cx - 4, head_cy + 2], fill=EYE)
        draw.point((cx - 8, head_cy - 2), fill=EYE_CORE)
        draw.point((cx - 6, head_cy), fill=EYE_CORE)
        draw.point((cx - 10, head_cy - 4), fill=EYE_GLINT)
        draw.line([(cx - 14, head_cy - 6), (cx - 4, head_cy - 6)], fill=EYE_BROW, width=2)
        draw.rectangle([cx + 4, head_cy - 4, cx + 12, head_cy + 2], fill=EYE)
        draw.point((cx + 8, head_cy - 2), fill=EYE_CORE)
        draw.point((cx + 6, head_cy), fill=EYE_CORE)
        draw.point((cx + 10, head_cy - 4), fill=EYE_GLINT)
        draw.line([(cx + 4, head_cy - 6), (cx + 14, head_cy - 6)], fill=EYE_BROW, width=2)
    elif direction == LEFT:
        draw.rectangle([cx - 12, head_cy - 4, cx - 4, head_cy + 2], fill=EYE)
        draw.point((cx - 8, head_cy - 2), fill=EYE_CORE)
        draw.point((cx - 6, head_cy), fill=EYE_CORE)
        draw.point((cx - 10, head_cy - 4), fill=EYE_GLINT)
        draw.line([(cx - 14, head_cy - 6), (cx - 4, head_cy - 6)], fill=EYE_BROW, width=2)
    elif direction == RIGHT:
        draw.rectangle([cx + 4, head_cy - 4, cx + 12, head_cy + 2], fill=EYE)
        draw.point((cx + 8, head_cy - 2), fill=EYE_CORE)
        draw.point((cx + 6, head_cy), fill=EYE_CORE)
        draw.point((cx + 10, head_cy - 4), fill=EYE_GLINT)
        draw.line([(cx + 4, head_cy - 6), (cx + 14, head_cy - 6)], fill=EYE_BROW, width=2)


def draw_beak(draw, cx, head_cy, direction):
    """Draw a sharper beak with darker tip and nostril dot."""
    if direction == DOWN:
        draw.polygon([(cx - 6, head_cy + 4), (cx + 6, head_cy + 4),
                      (cx, head_cy + 14)], fill=BEAK, outline=OUTLINE)
        draw.polygon([(cx - 2, head_cy + 10), (cx + 2, head_cy + 10),
                      (cx, head_cy + 14)], fill=BEAK_DARK, outline=None)
        draw.point((cx - 2, head_cy + 6), fill=BEAK_NOSTRIL)
        draw.point((cx + 2, head_cy + 6), fill=BEAK_NOSTRIL)
    elif direction == LEFT:
        draw.polygon([(cx - 20, head_cy), (cx - 10, head_cy - 4),
                      (cx - 10, head_cy + 4)], fill=BEAK, outline=OUTLINE)
        draw.polygon([(cx - 20, head_cy), (cx - 16, head_cy - 2),
                      (cx - 16, head_cy + 2)], fill=BEAK_DARK, outline=None)
        draw.point((cx - 14, head_cy - 2), fill=BEAK_NOSTRIL)
    elif direction == RIGHT:
        draw.polygon([(cx + 20, head_cy), (cx + 10, head_cy - 4),
                      (cx + 10, head_cy + 4)], fill=BEAK, outline=OUTLINE)
        draw.polygon([(cx + 20, head_cy), (cx + 16, head_cy - 2),
                      (cx + 16, head_cy + 2)], fill=BEAK_DARK, outline=None)
        draw.point((cx + 14, head_cy - 2), fill=BEAK_NOSTRIL)


def draw_wing_feather_marks(draw, cx, body_cy, side, wing_flap):
    """Draw V-shaped feather marks on wings."""
    wing_x = cx + side * 18
    wing_y = body_cy + wing_flap
    for i in range(3):
        fy = body_cy - 4 + i * 6
        fx = cx + side * (14 + i * 2)
        draw.line([(fx, fy), (fx + side * 2, fy + 2)], fill=WING_FEATHER, width=1)
        draw.line([(fx, fy), (fx + side * 2, fy - 2)], fill=WING_FEATHER, width=1)


def draw_wing_layered(draw, cx, body_cy, wing_flap, side, trailing=False):
    """Draw a wing with layered feathers and accent tips."""
    wing_x = cx + side * 18
    wing_y = body_cy + wing_flap
    base_color = WING_DARK if trailing else WING

    draw.polygon([
        (cx + side * 14, body_cy - 6),
        (wing_x + side * 4, wing_y - 2),
        (wing_x + side * 6, wing_y + 6),
        (wing_x + side * 2, wing_y + 10),
        (cx + side * 14, body_cy + 8),
    ], fill=base_color, outline=OUTLINE)

    if not trailing:
        draw.line([(cx + side * 14, body_cy - 2), (wing_x + side * 4, wing_y + 2)],
                  fill=WING_LIGHT, width=1)
        draw.line([(cx + side * 14, body_cy + 2), (wing_x + side * 4, wing_y + 6)],
                  fill=WING_MID, width=1)
        draw.line([(cx + side * 14, body_cy + 6), (wing_x + side * 2, wing_y + 8)],
                  fill=WING_DARK, width=1)
        draw.point((wing_x + side * 6, wing_y + 6), fill=WING_ACCENT)
        draw.point((wing_x + side * 4, wing_y + 8), fill=WING_ACCENT)
        draw.point((wing_x + side * 2, wing_y + 10), fill=WING_ACCENT)
        draw_wing_feather_marks(draw, cx, body_cy, side, wing_flap)
    else:
        draw.line([(cx + side * 12, body_cy), (wing_x + side * 2, wing_y + 4)],
                  fill=WING_MID, width=1)
        draw.point((wing_x + side * 4, wing_y + 8), fill=WING_ACCENT)


def draw_wing_side(draw, cx, body_cy, wing_flap, side, trailing=False):
    """Draw wing for side view."""
    wing_x = cx + side * 14
    wing_y = body_cy + wing_flap
    base_color = WING_DARK if trailing else WING

    draw.polygon([
        (cx + side * 10, body_cy - 6),
        (wing_x + side * 4, wing_y - 2),
        (wing_x + side * 6, wing_y + 6),
        (wing_x + side * 2, wing_y + 10),
        (cx + side * 10, body_cy + 8),
    ], fill=base_color, outline=OUTLINE)

    if not trailing:
        draw.line([(cx + side * 10, body_cy - 2), (wing_x + side * 4, wing_y + 2)],
                  fill=WING_LIGHT, width=1)
        draw.line([(cx + side * 10, body_cy + 2), (wing_x + side * 4, wing_y + 6)],
                  fill=WING_MID, width=1)
        draw.line([(cx + side * 10, body_cy + 6), (wing_x + side * 2, wing_y + 8)],
                  fill=WING_DARK, width=1)
        draw.point((wing_x + side * 6, wing_y + 6), fill=WING_ACCENT)
        draw.point((wing_x + side * 4, wing_y + 8), fill=WING_ACCENT)
        draw.point((wing_x + side * 2, wing_y + 10), fill=WING_ACCENT)
    else:
        draw.line([(cx + side * 10, body_cy), (wing_x + side * 2, wing_y + 4)],
                  fill=WING_MID, width=1)
        draw.point((wing_x + side * 4, wing_y + 8), fill=WING_ACCENT)


def draw_raptor(draw, ox, oy, direction, frame):
    """Draw a single raptor frame at offset (ox, oy)."""
    bob = [0, -2, 0, -1][frame]
    wing_flap = [-2, 2, -2, 0][frame]
    leg_step = [0, 2, 0, -2][frame]

    base_y = oy + 56 + bob
    body_cx = ox + 32
    body_cy = base_y - 18
    head_cy = body_cy - 20

    if direction == DOWN:
        draw_talons(draw, body_cx, base_y, DOWN, leg_step)
        draw.line([(body_cx - 8 - leg_step, base_y), (body_cx - 6, body_cy + 10)], fill=BODY_DARK, width=3)
        draw.line([(body_cx + 8 + leg_step, base_y), (body_cx + 6, body_cy + 10)], fill=BODY_DARK, width=3)

        for side in [-1, 1]:
            draw_wing_layered(draw, body_cx, body_cy, wing_flap, side)

        draw.polygon([(body_cx - 14, body_cy - 8), (body_cx + 14, body_cy - 8),
                      (body_cx + 16, body_cy + 10), (body_cx - 16, body_cy + 10)],
                     fill=BODY, outline=OUTLINE)
        # Plumage gradient: lighter chest
        draw.polygon([(body_cx - 8, body_cy - 2), (body_cx + 8, body_cy - 2),
                      (body_cx + 10, body_cy + 8), (body_cx - 10, body_cy + 8)],
                     fill=BELLY, outline=None)
        draw_belly_texture(draw, body_cx, body_cy, 16, 10)

        ellipse(draw, body_cx, head_cy, 16, 14, HEAD)
        draw_crest(draw, body_cx, head_cy, DOWN)
        draw_eyes(draw, body_cx, head_cy, DOWN)
        draw_beak(draw, body_cx, head_cy, DOWN)

    elif direction == UP:
        draw_tail(draw, body_cx, body_cy, base_y, UP, frame)
        draw_talons(draw, body_cx, base_y, UP, leg_step)
        draw.line([(body_cx - 8 - leg_step, base_y), (body_cx - 6, body_cy + 10)], fill=BODY_DARK, width=3)
        draw.line([(body_cx + 8 + leg_step, base_y), (body_cx + 6, body_cy + 10)], fill=BODY_DARK, width=3)

        for side in [-1, 1]:
            draw_wing_layered(draw, body_cx, body_cy, wing_flap, side)

        draw.polygon([(body_cx - 14, body_cy - 8), (body_cx + 14, body_cy - 8),
                      (body_cx + 16, body_cy + 10), (body_cx - 16, body_cy + 10)],
                     fill=BODY, outline=OUTLINE)
        draw.polygon([(body_cx - 10, body_cy - 4), (body_cx + 10, body_cy - 4),
                      (body_cx + 12, body_cy + 8), (body_cx - 12, body_cy + 8)],
                     fill=BODY_DARK, outline=None)

        ellipse(draw, body_cx, head_cy, 16, 14, HEAD)
        ellipse(draw, body_cx, head_cy, 12, 10, HEAD_DARK)
        draw_crest(draw, body_cx, head_cy, UP)

    elif direction == LEFT:
        draw_tail(draw, body_cx, body_cy, base_y, LEFT, frame)
        draw_talons(draw, body_cx, base_y, LEFT, leg_step)
        draw.line([(body_cx - 4 + leg_step, base_y), (body_cx - 2, body_cy + 10)], fill=BODY_DARK, width=3)
        draw.line([(body_cx + 4 + leg_step, base_y), (body_cx + 2, body_cy + 10)], fill=BODY_DARK, width=3)

        draw_wing_side(draw, body_cx, body_cy, wing_flap, +1, trailing=True)

        draw.polygon([(body_cx - 12, body_cy - 8), (body_cx + 12, body_cy - 8),
                      (body_cx + 14, body_cy + 10), (body_cx - 14, body_cy + 10)],
                     fill=BODY, outline=OUTLINE)
        draw.polygon([(body_cx - 8, body_cy - 2), (body_cx + 6, body_cy - 2),
                      (body_cx + 8, body_cy + 8), (body_cx - 6, body_cy + 8)],
                     fill=BELLY, outline=None)
        draw_belly_texture(draw, body_cx, body_cy, 12, 10)

        draw_wing_side(draw, body_cx, body_cy, wing_flap, -1, trailing=False)

        ellipse(draw, body_cx - 2, head_cy, 14, 14, HEAD)
        draw_eyes(draw, body_cx - 2, head_cy, LEFT)
        draw_beak(draw, body_cx - 2, head_cy, LEFT)
        draw_crest(draw, body_cx - 2, head_cy, LEFT)

    elif direction == RIGHT:
        draw_tail(draw, body_cx, body_cy, base_y, RIGHT, frame)
        draw_talons(draw, body_cx, base_y, RIGHT, leg_step)
        draw.line([(body_cx + 4 - leg_step, base_y), (body_cx + 2, body_cy + 10)], fill=BODY_DARK, width=3)
        draw.line([(body_cx - 4 - leg_step, base_y), (body_cx - 2, body_cy + 10)], fill=BODY_DARK, width=3)

        draw_wing_side(draw, body_cx, body_cy, wing_flap, -1, trailing=True)

        draw.polygon([(body_cx - 12, body_cy - 8), (body_cx + 12, body_cy - 8),
                      (body_cx + 14, body_cy + 10), (body_cx - 14, body_cy + 10)],
                     fill=BODY, outline=OUTLINE)
        draw.polygon([(body_cx - 6, body_cy - 2), (body_cx + 8, body_cy - 2),
                      (body_cx + 6, body_cy + 8), (body_cx - 8, body_cy + 8)],
                     fill=BELLY, outline=None)
        draw_belly_texture(draw, body_cx, body_cy, 12, 10)

        draw_wing_side(draw, body_cx, body_cy, wing_flap, +1, trailing=False)

        ellipse(draw, body_cx + 2, head_cy, 14, 14, HEAD)
        draw_eyes(draw, body_cx + 2, head_cy, RIGHT)
        draw_beak(draw, body_cx + 2, head_cy, RIGHT)
        draw_crest(draw, body_cx + 2, head_cy, RIGHT)


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
