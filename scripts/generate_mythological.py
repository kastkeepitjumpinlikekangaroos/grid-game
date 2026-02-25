#!/usr/bin/env python3
"""Mythological character sprite generators (IDs 87-101).

15 characters with unique body shapes replacing the generic humanoid template.
"""

import math
import sys
import os
sys.path.insert(0, os.path.dirname(__file__))
from sprite_base import (
    generate_character, ellipse, pill, _darken, _brighten,
    draw_fur_texture, draw_scale_texture,
    OUTLINE, BLACK, DOWN, UP, LEFT, RIGHT,
)

# ---------------------------------------------------------------------------
# Shared constants
# ---------------------------------------------------------------------------

# Minotaur palette
MINO_BODY = (130, 80, 50)
MINO_LIGHT = (165, 110, 70)
MINO_DARK = (90, 55, 30)
MINO_BELLY = (170, 130, 90)
MINO_HORN = (180, 160, 120)
MINO_HORN_DARK = (140, 120, 80)
MINO_NOSE_RING = (180, 170, 50)
MINO_HOOF = (70, 50, 35)
MINO_EYE = (200, 60, 40)

# Medusa palette
MEDUSA_SKIN = (100, 150, 100)
MEDUSA_LIGHT = (130, 180, 120)
MEDUSA_DARK = (70, 110, 70)
MEDUSA_SCALE = (80, 130, 80)
MEDUSA_BELLY = (140, 180, 130)
MEDUSA_EYE = (200, 50, 50)
MEDUSA_SNAKE = (80, 140, 60)
MEDUSA_SNAKE2 = (60, 120, 80)
MEDUSA_GOLD = (200, 180, 60)

# Cerberus palette
CERB_BODY = (60, 30, 30)
CERB_LIGHT = (90, 50, 45)
CERB_DARK = (40, 20, 20)
CERB_BELLY = (100, 65, 55)
CERB_EYE = (255, 100, 50)
CERB_CHAIN = (160, 160, 170)
CERB_CHAIN_DARK = (120, 120, 130)
CERB_MOUTH = (120, 40, 30)

# Centaur palette
CENT_HORSE = (160, 120, 70)
CENT_HORSE_LIGHT = (190, 150, 95)
CENT_HORSE_DARK = (120, 85, 45)
CENT_SKIN = (210, 190, 160)
CENT_SKIN_DARK = (180, 155, 125)
CENT_HAIR = (100, 70, 40)
CENT_HOOF = (80, 60, 40)
CENT_BOW = (140, 100, 50)
CENT_TAIL = (90, 60, 35)
CENT_EYE = (50, 40, 30)

# Kraken palette
KRAK_BODY = (40, 60, 100)
KRAK_LIGHT = (60, 85, 130)
KRAK_DARK = (25, 40, 70)
KRAK_BELLY = (80, 100, 140)
KRAK_EYE = (200, 200, 50)
KRAK_SUCKER = (100, 80, 120)
KRAK_BEAK = (60, 50, 40)
KRAK_TENTACLE = (50, 70, 110)

# Sphinx palette
SPHINX_BODY = (200, 170, 100)
SPHINX_LIGHT = (230, 200, 130)
SPHINX_DARK = (160, 130, 70)
SPHINX_BELLY = (220, 200, 150)
SPHINX_SKIN = (200, 170, 130)
SPHINX_SKIN_DARK = (170, 140, 100)
SPHINX_HEADDRESS = (50, 80, 160)
SPHINX_HEADDRESS_GOLD = (220, 190, 60)
SPHINX_WING = (180, 150, 80)
SPHINX_EYE = (40, 100, 160)

# Cyclops palette
CYCLOPS_BODY = (150, 130, 100)
CYCLOPS_LIGHT = (180, 160, 125)
CYCLOPS_DARK = (110, 95, 70)
CYCLOPS_BELLY = (170, 155, 125)
CYCLOPS_EYE_WHITE = (230, 230, 220)
CYCLOPS_EYE_IRIS = (100, 160, 60)
CYCLOPS_FUR = (120, 100, 70)
CYCLOPS_CLUB = (110, 80, 45)
CYCLOPS_CLUB_DARK = (80, 55, 30)
CYCLOPS_TUSK = (210, 200, 180)

# Harpy palette
HARPY_BODY = (140, 100, 140)
HARPY_LIGHT = (170, 130, 170)
HARPY_DARK = (100, 70, 100)
HARPY_BELLY = (200, 180, 200)
HARPY_SKIN = (210, 185, 160)
HARPY_SKIN_DARK = (180, 155, 130)
HARPY_WING_TIP = (110, 70, 110)
HARPY_CREST = (160, 110, 160)
HARPY_TALON = (90, 70, 50)
HARPY_EYE = (200, 60, 60)


# ===================================================================
# MINOTAUR (ID 87) — bull head, large horns, massive muscular body
# ===================================================================

def draw_minotaur(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    sway = [-1, 0, 1, 0][frame]
    leg_spread = [-3, 0, 3, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32 + sway
    body_cy = base_y - 20
    head_cy = body_cy - 18

    body_shadow = _darken(MINO_BODY, 0.7)

    # --- Legs (hooved, thick) ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            lx = cx + side * (9 + abs(leg_spread)) + (leg_spread if side == -1 else -leg_spread)
            # Upper leg
            draw.rectangle([lx - 4, body_cy + 10, lx + 4, body_cy + 20],
                           fill=MINO_BODY, outline=OUTLINE)
            # Lower leg
            draw.rectangle([lx - 3, body_cy + 14, lx + 3, base_y - 2],
                           fill=MINO_DARK, outline=OUTLINE)
            # Hoof (triangular)
            draw.polygon([(lx - 5, base_y), (lx, base_y - 3), (lx + 5, base_y)],
                         fill=MINO_HOOF, outline=OUTLINE)
            draw.point((lx, base_y - 1), fill=_darken(MINO_HOOF, 0.7))
    elif direction == LEFT:
        for offset in [leg_spread, -leg_spread]:
            lx = cx - 2 + offset
            draw.rectangle([lx - 4, body_cy + 10, lx + 4, body_cy + 18],
                           fill=MINO_BODY, outline=OUTLINE)
            draw.rectangle([lx - 3, body_cy + 14, lx + 3, base_y - 2],
                           fill=MINO_DARK, outline=OUTLINE)
            draw.polygon([(lx - 5, base_y), (lx, base_y - 3), (lx + 5, base_y)],
                         fill=MINO_HOOF, outline=OUTLINE)
    else:  # RIGHT
        for offset in [leg_spread, -leg_spread]:
            lx = cx + 2 + offset
            draw.rectangle([lx - 4, body_cy + 10, lx + 4, body_cy + 18],
                           fill=MINO_BODY, outline=OUTLINE)
            draw.rectangle([lx - 3, body_cy + 14, lx + 3, base_y - 2],
                           fill=MINO_DARK, outline=OUTLINE)
            draw.polygon([(lx - 5, base_y), (lx, base_y - 3), (lx + 5, base_y)],
                         fill=MINO_HOOF, outline=OUTLINE)

    # --- Body (massive, wider than normal) ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy, 18, 14, MINO_BODY)
        ellipse(draw, cx + 4, body_cy + 3, 14, 10, body_shadow, outline=None)
        ellipse(draw, cx, body_cy, 15, 11, MINO_BODY, outline=None)
        ellipse(draw, cx - 3, body_cy - 3, 10, 7, MINO_LIGHT, outline=None)
        ellipse(draw, cx, body_cy + 4, 10, 6, MINO_BELLY, outline=None)
        draw_fur_texture(draw, cx, body_cy + 6, 20, 8, MINO_DARK, density=3)
        # Arms
        draw.rectangle([cx - 22, body_cy - 6, cx - 16, body_cy + 6],
                       fill=MINO_BODY, outline=OUTLINE)
        draw.rectangle([cx - 22, body_cy - 6, cx - 20, body_cy + 2],
                       fill=MINO_LIGHT, outline=None)
        ellipse(draw, cx - 19, body_cy + 8, 4, 3, MINO_DARK)
        draw.rectangle([cx + 16, body_cy - 6, cx + 22, body_cy + 6],
                       fill=MINO_BODY, outline=OUTLINE)
        ellipse(draw, cx + 19, body_cy + 8, 4, 3, MINO_DARK)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 18, 14, MINO_BODY)
        ellipse(draw, cx, body_cy, 15, 11, MINO_DARK, outline=None)
        draw_fur_texture(draw, cx, body_cy + 6, 20, 8, MINO_DARK, density=3)
        draw.rectangle([cx - 22, body_cy - 6, cx - 16, body_cy + 6],
                       fill=MINO_BODY, outline=OUTLINE)
        draw.rectangle([cx + 16, body_cy - 6, cx + 22, body_cy + 6],
                       fill=MINO_BODY, outline=OUTLINE)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy, 16, 14, MINO_BODY)
        ellipse(draw, cx + 2, body_cy + 3, 12, 10, body_shadow, outline=None)
        ellipse(draw, cx - 2, body_cy, 13, 11, MINO_BODY, outline=None)
        ellipse(draw, cx - 5, body_cy - 3, 8, 6, MINO_LIGHT, outline=None)
        draw_fur_texture(draw, cx, body_cy + 6, 18, 8, MINO_DARK, density=3)
        draw.rectangle([cx - 16, body_cy - 4, cx - 10, body_cy + 6],
                       fill=MINO_BODY, outline=OUTLINE)
        draw.rectangle([cx - 16, body_cy - 4, cx - 14, body_cy + 2],
                       fill=MINO_LIGHT, outline=None)
        ellipse(draw, cx - 13, body_cy + 8, 4, 3, MINO_DARK)
    else:  # RIGHT
        ellipse(draw, cx + 2, body_cy, 16, 14, MINO_BODY)
        ellipse(draw, cx + 6, body_cy + 3, 12, 10, body_shadow, outline=None)
        ellipse(draw, cx + 2, body_cy, 13, 11, MINO_BODY, outline=None)
        ellipse(draw, cx + 5, body_cy - 3, 8, 6, MINO_LIGHT, outline=None)
        draw_fur_texture(draw, cx + 2, body_cy + 6, 18, 8, MINO_DARK, density=3)
        draw.rectangle([cx + 10, body_cy - 4, cx + 16, body_cy + 6],
                       fill=MINO_BODY, outline=OUTLINE)
        ellipse(draw, cx + 13, body_cy + 8, 4, 3, MINO_DARK)

    # --- Head (bull-shaped) ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 12, 10, MINO_BODY)
        ellipse(draw, cx - 2, head_cy - 2, 8, 6, MINO_LIGHT, outline=None)
        # Muzzle
        ellipse(draw, cx, head_cy + 6, 8, 5, MINO_BELLY)
        # Nostrils
        draw.point((cx - 3, head_cy + 6), fill=BLACK)
        draw.point((cx + 3, head_cy + 6), fill=BLACK)
        # Nose ring
        draw.arc([cx - 3, head_cy + 7, cx + 3, head_cy + 11],
                 start=0, end=180, fill=MINO_NOSE_RING, width=2)
        # Eyes
        draw.rectangle([cx - 7, head_cy - 2, cx - 3, head_cy + 1], fill=MINO_EYE)
        draw.point((cx - 5, head_cy - 1), fill=BLACK)
        draw.rectangle([cx + 3, head_cy - 2, cx + 7, head_cy + 1], fill=MINO_EYE)
        draw.point((cx + 5, head_cy - 1), fill=BLACK)
        # Horns (large, curved outward and up)
        draw.polygon([(cx - 10, head_cy - 6), (cx - 20, head_cy - 20),
                      (cx - 16, head_cy - 18), (cx - 8, head_cy - 8)],
                     fill=MINO_HORN, outline=OUTLINE)
        draw.line([(cx - 12, head_cy - 10), (cx - 17, head_cy - 16)],
                  fill=MINO_HORN_DARK, width=1)
        draw.point((cx - 20, head_cy - 20), fill=_brighten(MINO_HORN, 1.3))
        draw.polygon([(cx + 10, head_cy - 6), (cx + 20, head_cy - 20),
                      (cx + 16, head_cy - 18), (cx + 8, head_cy - 8)],
                     fill=MINO_HORN, outline=OUTLINE)
        draw.line([(cx + 12, head_cy - 10), (cx + 17, head_cy - 16)],
                  fill=MINO_HORN_DARK, width=1)
        draw.point((cx + 20, head_cy - 20), fill=_brighten(MINO_HORN, 1.3))
    elif direction == UP:
        ellipse(draw, cx, head_cy, 12, 10, MINO_BODY)
        ellipse(draw, cx, head_cy, 9, 7, MINO_DARK, outline=None)
        # Horns
        draw.polygon([(cx - 10, head_cy - 6), (cx - 20, head_cy - 20),
                      (cx - 16, head_cy - 18), (cx - 8, head_cy - 8)],
                     fill=MINO_HORN, outline=OUTLINE)
        draw.polygon([(cx + 10, head_cy - 6), (cx + 20, head_cy - 20),
                      (cx + 16, head_cy - 18), (cx + 8, head_cy - 8)],
                     fill=MINO_HORN, outline=OUTLINE)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 11, 10, MINO_BODY)
        ellipse(draw, cx - 4, head_cy - 2, 7, 6, MINO_LIGHT, outline=None)
        # Muzzle
        ellipse(draw, cx - 10, head_cy + 4, 6, 4, MINO_BELLY)
        draw.point((cx - 13, head_cy + 4), fill=BLACK)
        # Nose ring
        draw.arc([cx - 14, head_cy + 5, cx - 10, head_cy + 9],
                 start=90, end=270, fill=MINO_NOSE_RING, width=2)
        # Eye
        draw.rectangle([cx - 7, head_cy - 2, cx - 3, head_cy + 1], fill=MINO_EYE)
        draw.point((cx - 5, head_cy - 1), fill=BLACK)
        # Horn (side view — one prominent)
        draw.polygon([(cx - 6, head_cy - 8), (cx - 16, head_cy - 22),
                      (cx - 12, head_cy - 20), (cx - 4, head_cy - 10)],
                     fill=MINO_HORN, outline=OUTLINE)
        draw.line([(cx - 8, head_cy - 12), (cx - 14, head_cy - 18)],
                  fill=MINO_HORN_DARK, width=1)
        draw.point((cx - 16, head_cy - 22), fill=_brighten(MINO_HORN, 1.3))
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 11, 10, MINO_BODY)
        ellipse(draw, cx + 4, head_cy - 2, 7, 6, MINO_LIGHT, outline=None)
        # Muzzle
        ellipse(draw, cx + 10, head_cy + 4, 6, 4, MINO_BELLY)
        draw.point((cx + 13, head_cy + 4), fill=BLACK)
        # Nose ring
        draw.arc([cx + 10, head_cy + 5, cx + 14, head_cy + 9],
                 start=270, end=90, fill=MINO_NOSE_RING, width=2)
        # Eye
        draw.rectangle([cx + 3, head_cy - 2, cx + 7, head_cy + 1], fill=MINO_EYE)
        draw.point((cx + 5, head_cy - 1), fill=BLACK)
        # Horn
        draw.polygon([(cx + 6, head_cy - 8), (cx + 16, head_cy - 22),
                      (cx + 12, head_cy - 20), (cx + 4, head_cy - 10)],
                     fill=MINO_HORN, outline=OUTLINE)
        draw.line([(cx + 8, head_cy - 12), (cx + 14, head_cy - 18)],
                  fill=MINO_HORN_DARK, width=1)
        draw.point((cx + 16, head_cy - 22), fill=_brighten(MINO_HORN, 1.3))


# ===================================================================
# MEDUSA (ID 88) — snake lower body, snake hair, green skin
# ===================================================================

def draw_medusa(draw, ox, oy, direction, frame):
    bob = [0, -1, 0, -1][frame]
    curve_shift = [-2, 0, 2, 0][frame]
    snake_phase = [-1, 1, -1, 1][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 16
    head_cy = body_cy - 20

    body_dark = _darken(MEDUSA_SKIN, 0.7)

    # --- Snake lower body (coiled S-curve, no legs) ---
    if direction == DOWN:
        # Lower coil
        ellipse(draw, cx + 4 + curve_shift, base_y - 4, 12, 5, MEDUSA_SKIN)
        draw_scale_texture(draw, cx + 4 + curve_shift, base_y - 4, 16, 6, MEDUSA_SCALE)
        # Mid coil
        ellipse(draw, cx - 4 - curve_shift, body_cy + 6, 10, 6, MEDUSA_SKIN)
        ellipse(draw, cx - 4 - curve_shift, body_cy + 6, 7, 4, MEDUSA_BELLY, outline=None)
        draw_scale_texture(draw, cx - 4 - curve_shift, body_cy + 6, 12, 8, MEDUSA_SCALE)
        # Upper coil / waist transition
        ellipse(draw, cx + curve_shift, body_cy - 2, 6, 7, MEDUSA_SKIN)
        ellipse(draw, cx + curve_shift, body_cy - 2, 4, 5, MEDUSA_BELLY, outline=None)
    elif direction == UP:
        ellipse(draw, cx - 4 + curve_shift, base_y - 4, 12, 5, MEDUSA_SKIN)
        draw_scale_texture(draw, cx - 4 + curve_shift, base_y - 4, 16, 6, MEDUSA_SCALE)
        ellipse(draw, cx + 4 - curve_shift, body_cy + 6, 10, 6, MEDUSA_SKIN)
        draw_scale_texture(draw, cx + 4 - curve_shift, body_cy + 6, 12, 8, MEDUSA_SCALE)
        ellipse(draw, cx - curve_shift, body_cy - 2, 6, 7, MEDUSA_SKIN)
    elif direction == LEFT:
        # Coiled tail trailing right
        ellipse(draw, cx + 10 - curve_shift, base_y - 6, 8, 6, MEDUSA_SKIN)
        draw_scale_texture(draw, cx + 10 - curve_shift, base_y - 6, 10, 8, MEDUSA_SCALE)
        ellipse(draw, cx + curve_shift, body_cy + 4, 8, 5, MEDUSA_SKIN)
        ellipse(draw, cx + curve_shift, body_cy + 4, 5, 3, MEDUSA_BELLY, outline=None)
        # Neck transition
        draw.polygon([(cx - 4, body_cy), (cx - 6, head_cy + 8),
                      (cx - 2, head_cy + 6), (cx, body_cy - 2)],
                     fill=MEDUSA_SKIN, outline=OUTLINE)
    else:  # RIGHT
        ellipse(draw, cx - 10 + curve_shift, base_y - 6, 8, 6, MEDUSA_SKIN)
        draw_scale_texture(draw, cx - 10 + curve_shift, base_y - 6, 10, 8, MEDUSA_SCALE)
        ellipse(draw, cx - curve_shift, body_cy + 4, 8, 5, MEDUSA_SKIN)
        ellipse(draw, cx - curve_shift, body_cy + 4, 5, 3, MEDUSA_BELLY, outline=None)
        draw.polygon([(cx + 4, body_cy), (cx + 6, head_cy + 8),
                      (cx + 2, head_cy + 6), (cx, body_cy - 2)],
                     fill=MEDUSA_SKIN, outline=OUTLINE)

    # --- Torso (humanoid upper body) ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy - 8, 10, 8, MEDUSA_SKIN)
        ellipse(draw, cx - 2, body_cy - 10, 7, 5, MEDUSA_LIGHT, outline=None)
        # Gold jewelry neckpiece
        draw.arc([cx - 8, body_cy - 14, cx + 8, body_cy - 6],
                 start=0, end=180, fill=MEDUSA_GOLD, width=2)
        # Arms
        draw.rectangle([cx - 16, body_cy - 12, cx - 10, body_cy - 2],
                       fill=MEDUSA_SKIN, outline=OUTLINE)
        draw.rectangle([cx + 10, body_cy - 12, cx + 16, body_cy - 2],
                       fill=MEDUSA_SKIN, outline=OUTLINE)
        # Gold bracelets
        draw.rectangle([cx - 16, body_cy - 4, cx - 10, body_cy - 2],
                       fill=MEDUSA_GOLD, outline=None)
        draw.rectangle([cx + 10, body_cy - 4, cx + 16, body_cy - 2],
                       fill=MEDUSA_GOLD, outline=None)
    elif direction == UP:
        ellipse(draw, cx, body_cy - 8, 10, 8, MEDUSA_SKIN)
        ellipse(draw, cx, body_cy - 8, 7, 5, body_dark, outline=None)
        draw.rectangle([cx - 16, body_cy - 12, cx - 10, body_cy - 2],
                       fill=MEDUSA_SKIN, outline=OUTLINE)
        draw.rectangle([cx + 10, body_cy - 12, cx + 16, body_cy - 2],
                       fill=MEDUSA_SKIN, outline=OUTLINE)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy - 8, 8, 8, MEDUSA_SKIN)
        ellipse(draw, cx - 4, body_cy - 10, 5, 5, MEDUSA_LIGHT, outline=None)
        draw.rectangle([cx - 12, body_cy - 10, cx - 6, body_cy - 2],
                       fill=MEDUSA_SKIN, outline=OUTLINE)
        draw.rectangle([cx - 12, body_cy - 4, cx - 6, body_cy - 2],
                       fill=MEDUSA_GOLD, outline=None)
    else:  # RIGHT
        ellipse(draw, cx + 2, body_cy - 8, 8, 8, MEDUSA_SKIN)
        ellipse(draw, cx + 4, body_cy - 10, 5, 5, MEDUSA_LIGHT, outline=None)
        draw.rectangle([cx + 6, body_cy - 10, cx + 12, body_cy - 2],
                       fill=MEDUSA_SKIN, outline=OUTLINE)
        draw.rectangle([cx + 6, body_cy - 4, cx + 12, body_cy - 2],
                       fill=MEDUSA_GOLD, outline=None)

    # --- Head ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 10, 9, MEDUSA_SKIN)
        ellipse(draw, cx - 2, head_cy - 2, 7, 5, MEDUSA_LIGHT, outline=None)
        # Reptilian slit eyes
        draw.rectangle([cx - 6, head_cy - 1, cx - 2, head_cy + 2], fill=MEDUSA_EYE)
        draw.line([(cx - 4, head_cy - 1), (cx - 4, head_cy + 2)], fill=BLACK, width=1)
        draw.rectangle([cx + 2, head_cy - 1, cx + 6, head_cy + 2], fill=MEDUSA_EYE)
        draw.line([(cx + 4, head_cy - 1), (cx + 4, head_cy + 2)], fill=BLACK, width=1)
        # Mouth
        draw.line([(cx - 2, head_cy + 5), (cx + 2, head_cy + 5)],
                  fill=MEDUSA_DARK, width=1)
        # Gold tiara
        draw.arc([cx - 8, head_cy - 8, cx + 8, head_cy - 2],
                 start=180, end=360, fill=MEDUSA_GOLD, width=2)
        draw.point((cx, head_cy - 8), fill=_brighten(MEDUSA_GOLD, 1.4))
        # Snake hair — wavy lines extending from head
        snakes = [(-8, -6), (-5, -8), (-2, -9), (2, -9), (5, -8), (8, -6)]
        for i, (sx, sy) in enumerate(snakes):
            scolor = MEDUSA_SNAKE if i % 2 == 0 else MEDUSA_SNAKE2
            sp = snake_phase if i % 2 == 0 else -snake_phase
            draw.line([(cx + sx, head_cy + sy),
                       (cx + sx + sp, head_cy + sy - 6),
                       (cx + sx - sp, head_cy + sy - 12)],
                      fill=scolor, width=2)
            # Snake head dot
            draw.point((cx + sx - sp, head_cy + sy - 12), fill=scolor)
            draw.point((cx + sx - sp, head_cy + sy - 13), fill=scolor)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 10, 9, MEDUSA_SKIN)
        ellipse(draw, cx, head_cy, 7, 6, body_dark, outline=None)
        # Snake hair (back view — more visible)
        snakes = [(-9, -4), (-6, -7), (-3, -8), (0, -9), (3, -8), (6, -7), (9, -4)]
        for i, (sx, sy) in enumerate(snakes):
            scolor = MEDUSA_SNAKE if i % 2 == 0 else MEDUSA_SNAKE2
            sp = snake_phase if i % 2 == 0 else -snake_phase
            draw.line([(cx + sx, head_cy + sy),
                       (cx + sx + sp, head_cy + sy - 7),
                       (cx + sx - sp, head_cy + sy - 14)],
                      fill=scolor, width=2)
            draw.point((cx + sx - sp, head_cy + sy - 14), fill=scolor)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 9, 9, MEDUSA_SKIN)
        ellipse(draw, cx - 4, head_cy - 2, 6, 5, MEDUSA_LIGHT, outline=None)
        # Eye
        draw.rectangle([cx - 8, head_cy - 1, cx - 4, head_cy + 2], fill=MEDUSA_EYE)
        draw.line([(cx - 6, head_cy - 1), (cx - 6, head_cy + 2)], fill=BLACK, width=1)
        # Snake hair (left side)
        snakes = [(-4, -8), (-1, -9), (2, -8), (5, -6), (7, -4)]
        for i, (sx, sy) in enumerate(snakes):
            scolor = MEDUSA_SNAKE if i % 2 == 0 else MEDUSA_SNAKE2
            sp = snake_phase if i % 2 == 0 else -snake_phase
            draw.line([(cx + sx, head_cy + sy),
                       (cx + sx + sp - 2, head_cy + sy - 7),
                       (cx + sx - sp - 2, head_cy + sy - 13)],
                      fill=scolor, width=2)
            draw.point((cx + sx - sp - 2, head_cy + sy - 13), fill=scolor)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 9, 9, MEDUSA_SKIN)
        ellipse(draw, cx + 4, head_cy - 2, 6, 5, MEDUSA_LIGHT, outline=None)
        # Eye
        draw.rectangle([cx + 4, head_cy - 1, cx + 8, head_cy + 2], fill=MEDUSA_EYE)
        draw.line([(cx + 6, head_cy - 1), (cx + 6, head_cy + 2)], fill=BLACK, width=1)
        # Snake hair (right side)
        snakes = [(4, -8), (1, -9), (-2, -8), (-5, -6), (-7, -4)]
        for i, (sx, sy) in enumerate(snakes):
            scolor = MEDUSA_SNAKE if i % 2 == 0 else MEDUSA_SNAKE2
            sp = snake_phase if i % 2 == 0 else -snake_phase
            draw.line([(cx + sx, head_cy + sy),
                       (cx + sx - sp + 2, head_cy + sy - 7),
                       (cx + sx + sp + 2, head_cy + sy - 13)],
                      fill=scolor, width=2)
            draw.point((cx + sx + sp + 2, head_cy + sy - 13), fill=scolor)


# ===================================================================
# CERBERUS (ID 89) — three-headed dog, dark fur, chain collar
# ===================================================================

def draw_cerberus(draw, ox, oy, direction, frame):
    bob = [0, -1, 0, -1][frame]
    leg_spread = [-3, 0, 3, 0][frame]
    pant = [0, 1, 0, -1][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 16
    head_cy = body_cy - 14

    body_shadow = _darken(CERB_BODY, 0.7)

    # --- Legs (4 dog legs, thick) ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            lx = cx + side * (8 + abs(leg_spread)) + (leg_spread if side == -1 else -leg_spread)
            draw.rectangle([lx - 3, body_cy + 8, lx + 3, base_y - 2],
                           fill=CERB_BODY, outline=OUTLINE)
            ellipse(draw, lx, base_y, 4, 3, CERB_DARK)
            draw.point((lx - 2, base_y + 1), fill=BLACK)
            draw.point((lx + 2, base_y + 1), fill=BLACK)
    elif direction == LEFT:
        for offset in [leg_spread, -leg_spread]:
            lx = cx - 2 + offset
            draw.rectangle([lx - 3, body_cy + 8, lx + 3, base_y - 2],
                           fill=CERB_BODY, outline=OUTLINE)
            ellipse(draw, lx, base_y, 4, 3, CERB_DARK)
            draw.point((lx - 2, base_y + 1), fill=BLACK)
    else:  # RIGHT
        for offset in [leg_spread, -leg_spread]:
            lx = cx + 2 + offset
            draw.rectangle([lx - 3, body_cy + 8, lx + 3, base_y - 2],
                           fill=CERB_BODY, outline=OUTLINE)
            ellipse(draw, lx, base_y, 4, 3, CERB_DARK)
            draw.point((lx + 2, base_y + 1), fill=BLACK)

    # --- Body (thick dog body) ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy, 16, 12, CERB_BODY)
        ellipse(draw, cx + 3, body_cy + 2, 12, 8, body_shadow, outline=None)
        ellipse(draw, cx, body_cy, 13, 9, CERB_BODY, outline=None)
        ellipse(draw, cx - 2, body_cy - 2, 8, 5, CERB_LIGHT, outline=None)
        ellipse(draw, cx, body_cy + 3, 8, 4, CERB_BELLY, outline=None)
        draw_fur_texture(draw, cx, body_cy, 22, 14, CERB_BODY, density=4)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 16, 12, CERB_BODY)
        ellipse(draw, cx, body_cy, 13, 9, CERB_DARK, outline=None)
        draw_fur_texture(draw, cx, body_cy, 22, 14, CERB_DARK, density=4)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy, 14, 12, CERB_BODY)
        ellipse(draw, cx + 2, body_cy + 2, 10, 8, body_shadow, outline=None)
        ellipse(draw, cx - 2, body_cy, 11, 9, CERB_BODY, outline=None)
        ellipse(draw, cx - 4, body_cy - 2, 7, 5, CERB_LIGHT, outline=None)
        draw_fur_texture(draw, cx, body_cy, 18, 14, CERB_BODY, density=4)
    else:  # RIGHT
        ellipse(draw, cx + 2, body_cy, 14, 12, CERB_BODY)
        ellipse(draw, cx + 6, body_cy + 2, 10, 8, body_shadow, outline=None)
        ellipse(draw, cx + 2, body_cy, 11, 9, CERB_BODY, outline=None)
        ellipse(draw, cx, body_cy - 2, 7, 5, CERB_LIGHT, outline=None)
        draw_fur_texture(draw, cx + 2, body_cy, 18, 14, CERB_BODY, density=4)

    # --- Chain collar (metallic grey links around neck area) ---
    if direction == DOWN:
        for i in range(-10, 12, 4):
            c = CERB_CHAIN if (i // 4) % 2 == 0 else CERB_CHAIN_DARK
            draw.rectangle([cx + i, body_cy - 10, cx + i + 3, body_cy - 8],
                           fill=c, outline=OUTLINE)
    elif direction == UP:
        for i in range(-10, 12, 4):
            c = CERB_CHAIN if (i // 4) % 2 == 0 else CERB_CHAIN_DARK
            draw.rectangle([cx + i, body_cy - 10, cx + i + 3, body_cy - 8],
                           fill=c, outline=OUTLINE)
    elif direction == LEFT:
        for i in range(-8, 8, 4):
            c = CERB_CHAIN if (i // 4) % 2 == 0 else CERB_CHAIN_DARK
            draw.rectangle([cx + i - 4, body_cy - 10, cx + i - 1, body_cy - 8],
                           fill=c, outline=OUTLINE)
    else:  # RIGHT
        for i in range(-8, 8, 4):
            c = CERB_CHAIN if (i // 4) % 2 == 0 else CERB_CHAIN_DARK
            draw.rectangle([cx + i, body_cy - 10, cx + i + 3, body_cy - 8],
                           fill=c, outline=OUTLINE)

    # --- Three heads ---
    def draw_dog_head(hx, hy, scale=1.0, is_center=True):
        """Draw a single dog head at (hx, hy)."""
        rx = int(8 * scale)
        ry = int(7 * scale)
        ellipse(draw, hx, hy, rx, ry, CERB_BODY)
        if is_center or direction in (DOWN, LEFT, RIGHT):
            ellipse(draw, hx - 1, hy - 1, int(5 * scale), int(4 * scale),
                    CERB_LIGHT, outline=None)
        # Flame eyes
        if direction == DOWN or (direction in (LEFT, RIGHT) and is_center):
            ex = int(4 * scale)
            draw.rectangle([hx - ex, hy - 2, hx - ex + 2, hy + 1], fill=CERB_EYE)
            draw.point((hx - ex + 1, hy - 1), fill=(255, 200, 50))
            draw.rectangle([hx + ex - 2, hy - 2, hx + ex, hy + 1], fill=CERB_EYE)
            draw.point((hx + ex - 1, hy - 1), fill=(255, 200, 50))
        elif direction == LEFT:
            draw.rectangle([hx - int(4 * scale), hy - 2, hx - int(4 * scale) + 2, hy + 1],
                           fill=CERB_EYE)
        elif direction == RIGHT:
            draw.rectangle([hx + int(2 * scale), hy - 2, hx + int(4 * scale), hy + 1],
                           fill=CERB_EYE)
        # Mouth / jaw
        if direction == DOWN:
            ellipse(draw, hx, hy + int(4 * scale), int(4 * scale), int(3 * scale), CERB_MOUTH)
            draw.line([(hx - int(3 * scale), hy + int(3 * scale) + pant),
                       (hx + int(3 * scale), hy + int(3 * scale) + pant)],
                      fill=BLACK, width=1)
        # Pointed ears
        if direction in (DOWN, UP):
            draw.polygon([(hx - rx + 2, hy - ry + 2),
                          (hx - rx - 2, hy - ry - 6),
                          (hx - rx + 5, hy - ry + 4)],
                         fill=CERB_BODY, outline=OUTLINE)
            draw.polygon([(hx + rx - 2, hy - ry + 2),
                          (hx + rx + 2, hy - ry - 6),
                          (hx + rx - 5, hy - ry + 4)],
                         fill=CERB_BODY, outline=OUTLINE)

    if direction == DOWN:
        # Center head (higher)
        draw_dog_head(cx, head_cy, scale=1.0, is_center=True)
        # Left head (lower, angled out)
        draw_dog_head(cx - 14, head_cy + 4, scale=0.85, is_center=False)
        # Right head (lower, angled out)
        draw_dog_head(cx + 14, head_cy + 4, scale=0.85, is_center=False)
    elif direction == UP:
        # Three heads from behind — center higher
        draw_dog_head(cx, head_cy, scale=1.0, is_center=True)
        draw_dog_head(cx - 12, head_cy + 3, scale=0.85, is_center=False)
        draw_dog_head(cx + 12, head_cy + 3, scale=0.85, is_center=False)
    elif direction == LEFT:
        # Three heads stacked/staggered — facing left
        draw_dog_head(cx - 6, head_cy, scale=1.0, is_center=True)
        draw_dog_head(cx - 2, head_cy - 8, scale=0.8, is_center=False)
        draw_dog_head(cx - 2, head_cy + 8, scale=0.8, is_center=False)
    else:  # RIGHT
        draw_dog_head(cx + 6, head_cy, scale=1.0, is_center=True)
        draw_dog_head(cx + 2, head_cy - 8, scale=0.8, is_center=False)
        draw_dog_head(cx + 2, head_cy + 8, scale=0.8, is_center=False)


# ===================================================================
# CENTAUR (ID 90) — horse body + human torso, 4 horse legs, bow
# ===================================================================

def draw_centaur(draw, ox, oy, direction, frame):
    bob = [0, -1, 0, -1][frame]
    leg_phase_f = [-3, 0, 3, 0][frame]  # front legs
    leg_phase_r = [3, 0, -3, 0][frame]  # rear legs
    tail_sway = [-3, 0, 3, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    # Horse body is longer/lower
    horse_cy = base_y - 12
    torso_cy = horse_cy - 16
    head_cy = torso_cy - 14

    horse_shadow = _darken(CENT_HORSE, 0.7)

    # --- Tail ---
    if direction == DOWN:
        draw.polygon([(cx + 6, horse_cy + 2), (cx + 10 + tail_sway, horse_cy + 14),
                      (cx + 6 + tail_sway, horse_cy + 16), (cx + 4, horse_cy + 4)],
                     fill=CENT_TAIL, outline=OUTLINE)
    elif direction == UP:
        draw.polygon([(cx - 2, horse_cy + 4), (cx + 2, horse_cy + 4),
                      (cx + 4 + tail_sway, base_y + 2),
                      (cx - 4 + tail_sway, base_y + 4)],
                     fill=CENT_TAIL, outline=OUTLINE)
        # Tail strands
        for i in range(-2, 4, 2):
            draw.line([(cx + i + tail_sway, base_y),
                       (cx + i + tail_sway * 2, base_y + 4)],
                      fill=CENT_HAIR, width=1)
    elif direction == LEFT:
        draw.polygon([(cx + 10, horse_cy), (cx + 18 + tail_sway, horse_cy - 4),
                      (cx + 20 + tail_sway, horse_cy), (cx + 12, horse_cy + 4)],
                     fill=CENT_TAIL, outline=OUTLINE)
    else:  # RIGHT
        draw.polygon([(cx - 10, horse_cy), (cx - 18 - tail_sway, horse_cy - 4),
                      (cx - 20 - tail_sway, horse_cy), (cx - 12, horse_cy + 4)],
                     fill=CENT_TAIL, outline=OUTLINE)

    # --- Horse legs (4 legs) ---
    if direction in (DOWN, UP):
        # Front pair
        for side in [-1, 1]:
            fx = cx + side * 6 + (leg_phase_f if side == -1 else -leg_phase_f)
            draw.rectangle([fx - 2, horse_cy + 4, fx + 2, base_y - 3],
                           fill=CENT_HORSE, outline=OUTLINE)
            # Hoof
            draw.rectangle([fx - 3, base_y - 3, fx + 3, base_y],
                           fill=CENT_HOOF, outline=OUTLINE)
        # Rear pair (wider stance)
        for side in [-1, 1]:
            rx = cx + side * 10 + (leg_phase_r if side == -1 else -leg_phase_r)
            draw.rectangle([rx - 2, horse_cy + 4, rx + 2, base_y - 3],
                           fill=CENT_HORSE_DARK, outline=OUTLINE)
            draw.rectangle([rx - 3, base_y - 3, rx + 3, base_y],
                           fill=CENT_HOOF, outline=OUTLINE)
    elif direction == LEFT:
        # Far rear leg
        draw.rectangle([cx + 6 - leg_phase_r, horse_cy + 4, cx + 10 - leg_phase_r, base_y - 3],
                       fill=CENT_HORSE_DARK, outline=OUTLINE)
        draw.rectangle([cx + 5 - leg_phase_r, base_y - 3, cx + 11 - leg_phase_r, base_y],
                       fill=CENT_HOOF, outline=OUTLINE)
        # Far front leg
        draw.rectangle([cx - 8 + leg_phase_f, horse_cy + 4, cx - 4 + leg_phase_f, base_y - 3],
                       fill=CENT_HORSE_DARK, outline=OUTLINE)
        draw.rectangle([cx - 9 + leg_phase_f, base_y - 3, cx - 3 + leg_phase_f, base_y],
                       fill=CENT_HOOF, outline=OUTLINE)
        # Near rear leg
        draw.rectangle([cx + 8 + leg_phase_r, horse_cy + 4, cx + 12 + leg_phase_r, base_y - 3],
                       fill=CENT_HORSE, outline=OUTLINE)
        draw.rectangle([cx + 7 + leg_phase_r, base_y - 3, cx + 13 + leg_phase_r, base_y],
                       fill=CENT_HOOF, outline=OUTLINE)
        # Near front leg
        draw.rectangle([cx - 6 - leg_phase_f, horse_cy + 4, cx - 2 - leg_phase_f, base_y - 3],
                       fill=CENT_HORSE, outline=OUTLINE)
        draw.rectangle([cx - 7 - leg_phase_f, base_y - 3, cx - 1 - leg_phase_f, base_y],
                       fill=CENT_HOOF, outline=OUTLINE)
    else:  # RIGHT
        # Far rear leg
        draw.rectangle([cx - 10 + leg_phase_r, horse_cy + 4, cx - 6 + leg_phase_r, base_y - 3],
                       fill=CENT_HORSE_DARK, outline=OUTLINE)
        draw.rectangle([cx - 11 + leg_phase_r, base_y - 3, cx - 5 + leg_phase_r, base_y],
                       fill=CENT_HOOF, outline=OUTLINE)
        # Far front leg
        draw.rectangle([cx + 4 - leg_phase_f, horse_cy + 4, cx + 8 - leg_phase_f, base_y - 3],
                       fill=CENT_HORSE_DARK, outline=OUTLINE)
        draw.rectangle([cx + 3 - leg_phase_f, base_y - 3, cx + 9 - leg_phase_f, base_y],
                       fill=CENT_HOOF, outline=OUTLINE)
        # Near rear leg
        draw.rectangle([cx - 12 - leg_phase_r, horse_cy + 4, cx - 8 - leg_phase_r, base_y - 3],
                       fill=CENT_HORSE, outline=OUTLINE)
        draw.rectangle([cx - 13 - leg_phase_r, base_y - 3, cx - 7 - leg_phase_r, base_y],
                       fill=CENT_HOOF, outline=OUTLINE)
        # Near front leg
        draw.rectangle([cx + 2 + leg_phase_f, horse_cy + 4, cx + 6 + leg_phase_f, base_y - 3],
                       fill=CENT_HORSE, outline=OUTLINE)
        draw.rectangle([cx + 1 + leg_phase_f, base_y - 3, cx + 7 + leg_phase_f, base_y],
                       fill=CENT_HOOF, outline=OUTLINE)

    # --- Horse body (long, horizontal) ---
    if direction == DOWN:
        ellipse(draw, cx, horse_cy, 16, 10, CENT_HORSE)
        ellipse(draw, cx + 3, horse_cy + 2, 12, 7, horse_shadow, outline=None)
        ellipse(draw, cx, horse_cy, 13, 8, CENT_HORSE, outline=None)
        ellipse(draw, cx - 2, horse_cy - 2, 8, 5, CENT_HORSE_LIGHT, outline=None)
    elif direction == UP:
        ellipse(draw, cx, horse_cy, 16, 10, CENT_HORSE)
        ellipse(draw, cx, horse_cy, 13, 8, CENT_HORSE_DARK, outline=None)
    elif direction == LEFT:
        # Elongated horizontal horse body
        ellipse(draw, cx + 2, horse_cy, 18, 10, CENT_HORSE)
        ellipse(draw, cx + 4, horse_cy + 2, 14, 7, horse_shadow, outline=None)
        ellipse(draw, cx + 2, horse_cy, 15, 8, CENT_HORSE, outline=None)
        ellipse(draw, cx, horse_cy - 2, 10, 5, CENT_HORSE_LIGHT, outline=None)
    else:  # RIGHT
        ellipse(draw, cx - 2, horse_cy, 18, 10, CENT_HORSE)
        ellipse(draw, cx, horse_cy + 2, 14, 7, horse_shadow, outline=None)
        ellipse(draw, cx - 2, horse_cy, 15, 8, CENT_HORSE, outline=None)
        ellipse(draw, cx - 4, horse_cy - 2, 10, 5, CENT_HORSE_LIGHT, outline=None)

    # --- Human torso (smaller, mounted on horse body) ---
    if direction == DOWN:
        ellipse(draw, cx, torso_cy, 8, 8, CENT_SKIN)
        ellipse(draw, cx - 1, torso_cy - 2, 5, 5, _brighten(CENT_SKIN, 1.1), outline=None)
        # Arms
        draw.rectangle([cx - 14, torso_cy - 4, cx - 8, torso_cy + 4],
                       fill=CENT_SKIN, outline=OUTLINE)
        draw.rectangle([cx + 8, torso_cy - 4, cx + 14, torso_cy + 4],
                       fill=CENT_SKIN, outline=OUTLINE)
    elif direction == UP:
        ellipse(draw, cx, torso_cy, 8, 8, CENT_SKIN)
        ellipse(draw, cx, torso_cy, 5, 5, CENT_SKIN_DARK, outline=None)
        # Bow on back (curved line)
        draw.arc([cx - 6, torso_cy - 10, cx + 6, torso_cy + 4],
                 start=250, end=110, fill=CENT_BOW, width=2)
        draw.line([(cx - 1, torso_cy - 10), (cx - 1, torso_cy + 4)],
                  fill=_darken(CENT_BOW, 0.8), width=1)
        draw.rectangle([cx - 14, torso_cy - 4, cx - 8, torso_cy + 4],
                       fill=CENT_SKIN, outline=OUTLINE)
        draw.rectangle([cx + 8, torso_cy - 4, cx + 14, torso_cy + 4],
                       fill=CENT_SKIN, outline=OUTLINE)
    elif direction == LEFT:
        ellipse(draw, cx - 4, torso_cy, 7, 8, CENT_SKIN)
        ellipse(draw, cx - 6, torso_cy - 2, 4, 5, _brighten(CENT_SKIN, 1.1), outline=None)
        draw.rectangle([cx - 12, torso_cy - 2, cx - 6, torso_cy + 4],
                       fill=CENT_SKIN, outline=OUTLINE)
        # Bow on back
        draw.arc([cx, torso_cy - 8, cx + 10, torso_cy + 4],
                 start=250, end=110, fill=CENT_BOW, width=2)
    else:  # RIGHT
        ellipse(draw, cx + 4, torso_cy, 7, 8, CENT_SKIN)
        ellipse(draw, cx + 6, torso_cy - 2, 4, 5, _brighten(CENT_SKIN, 1.1), outline=None)
        draw.rectangle([cx + 6, torso_cy - 2, cx + 12, torso_cy + 4],
                       fill=CENT_SKIN, outline=OUTLINE)
        # Bow on back
        draw.arc([cx - 10, torso_cy - 8, cx, torso_cy + 4],
                 start=70, end=290, fill=CENT_BOW, width=2)

    # --- Head (human, smaller) ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 8, 8, CENT_HAIR)
        ellipse(draw, cx - 1, head_cy - 1, 5, 5, _brighten(CENT_HAIR, 1.2), outline=None)
        # Face
        ellipse(draw, cx, head_cy + 3, 6, 5, CENT_SKIN)
        # Eyes
        draw.rectangle([cx - 4, head_cy + 2, cx - 2, head_cy + 4], fill=CENT_EYE)
        draw.rectangle([cx + 2, head_cy + 2, cx + 4, head_cy + 4], fill=CENT_EYE)
        draw.point((cx, head_cy + 5), fill=CENT_SKIN_DARK)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 8, 8, CENT_HAIR)
        ellipse(draw, cx, head_cy, 6, 6, _darken(CENT_HAIR, 0.8), outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 4, head_cy, 7, 7, CENT_HAIR)
        ellipse(draw, cx - 5, head_cy - 1, 4, 4, _brighten(CENT_HAIR, 1.2), outline=None)
        ellipse(draw, cx - 6, head_cy + 2, 5, 4, CENT_SKIN)
        draw.rectangle([cx - 9, head_cy + 1, cx - 7, head_cy + 3], fill=CENT_EYE)
        draw.point((cx - 8, head_cy + 4), fill=CENT_SKIN_DARK)
    else:  # RIGHT
        ellipse(draw, cx + 4, head_cy, 7, 7, CENT_HAIR)
        ellipse(draw, cx + 5, head_cy - 1, 4, 4, _brighten(CENT_HAIR, 1.2), outline=None)
        ellipse(draw, cx + 6, head_cy + 2, 5, 4, CENT_SKIN)
        draw.rectangle([cx + 7, head_cy + 1, cx + 9, head_cy + 3], fill=CENT_EYE)
        draw.point((cx + 8, head_cy + 4), fill=CENT_SKIN_DARK)


# ===================================================================
# KRAKEN (ID 91) — bulbous octopus head, 6 tentacles, large eyes
# ===================================================================

def draw_kraken(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    tentacle_phase = [-2, 0, 2, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    head_cy = base_y - 28
    tent_base = base_y - 14

    body_shadow = _darken(KRAK_BODY, 0.7)

    # --- Tentacles (6 curving downward) ---
    def draw_tentacles_front():
        """Draw tentacles for front/back view."""
        offsets = [(-14, 3), (-8, 0), (-2, -1), (2, -1), (8, 0), (14, 3)]
        for i, (tx, ty) in enumerate(offsets):
            phase = tentacle_phase if i % 2 == 0 else -tentacle_phase
            # Tentacle curve: three segments
            p1 = (cx + tx, tent_base + ty)
            p2 = (cx + tx + phase, tent_base + ty + 8)
            p3 = (cx + tx - phase, base_y + 2)
            draw.line([p1, p2], fill=KRAK_TENTACLE, width=3)
            draw.line([p2, p3], fill=KRAK_TENTACLE, width=2)
            # Suction cup dots
            draw.point((cx + tx + phase // 2, tent_base + ty + 4), fill=KRAK_SUCKER)
            draw.point((cx + tx, tent_base + ty + 8), fill=KRAK_SUCKER)

    def draw_tentacles_side(facing_left):
        """Draw tentacles for side view — fanning out."""
        d = -1 if facing_left else 1
        offsets = [(-4, -2), (0, 0), (4, 1), (8, 3), (12, 5), (16, 7)]
        for i, (tx, ty) in enumerate(offsets):
            phase = tentacle_phase if i % 2 == 0 else -tentacle_phase
            p1 = (cx + d * tx, tent_base + ty)
            p2 = (cx + d * (tx + 4) + phase, tent_base + ty + 7)
            p3 = (cx + d * (tx + 2) - phase, base_y + 2)
            w = 3 if i < 3 else 2
            draw.line([p1, p2], fill=KRAK_TENTACLE, width=w)
            draw.line([p2, p3], fill=KRAK_TENTACLE, width=max(1, w - 1))
            draw.point((cx + d * (tx + 2), tent_base + ty + 4), fill=KRAK_SUCKER)

    if direction == DOWN:
        draw_tentacles_front()
    elif direction == UP:
        draw_tentacles_front()
    elif direction == LEFT:
        draw_tentacles_side(facing_left=True)
    else:  # RIGHT
        draw_tentacles_side(facing_left=False)

    # --- Bulbous head (very large, dominates the character) ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 18, 16, KRAK_BODY)
        ellipse(draw, cx + 4, head_cy + 3, 14, 12, body_shadow, outline=None)
        ellipse(draw, cx, head_cy, 15, 13, KRAK_BODY, outline=None)
        ellipse(draw, cx - 3, head_cy - 4, 10, 8, KRAK_LIGHT, outline=None)
        # Large yellow eyes
        ellipse(draw, cx - 8, head_cy + 2, 5, 6, KRAK_EYE)
        ellipse(draw, cx - 8, head_cy + 2, 2, 3, BLACK, outline=None)
        ellipse(draw, cx + 8, head_cy + 2, 5, 6, KRAK_EYE)
        ellipse(draw, cx + 8, head_cy + 2, 2, 3, BLACK, outline=None)
        # Beak-like mouth
        draw.polygon([(cx - 3, head_cy + 10), (cx, head_cy + 14),
                      (cx + 3, head_cy + 10)], fill=KRAK_BEAK, outline=OUTLINE)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 18, 16, KRAK_BODY)
        ellipse(draw, cx, head_cy, 15, 13, _darken(KRAK_BODY, 0.85), outline=None)
        # Texture dots
        for dy in range(-10, 10, 5):
            for dx in range(-8, 10, 6):
                draw.point((cx + dx, head_cy + dy), fill=KRAK_LIGHT)
    elif direction == LEFT:
        ellipse(draw, cx - 4, head_cy, 16, 16, KRAK_BODY)
        ellipse(draw, cx - 2, head_cy + 3, 12, 12, body_shadow, outline=None)
        ellipse(draw, cx - 4, head_cy, 13, 13, KRAK_BODY, outline=None)
        ellipse(draw, cx - 7, head_cy - 4, 8, 8, KRAK_LIGHT, outline=None)
        # Eye (one visible, large)
        ellipse(draw, cx - 10, head_cy + 2, 5, 6, KRAK_EYE)
        ellipse(draw, cx - 10, head_cy + 2, 2, 3, BLACK, outline=None)
        # Beak
        draw.polygon([(cx - 12, head_cy + 8), (cx - 16, head_cy + 12),
                      (cx - 12, head_cy + 14)], fill=KRAK_BEAK, outline=OUTLINE)
    else:  # RIGHT
        ellipse(draw, cx + 4, head_cy, 16, 16, KRAK_BODY)
        ellipse(draw, cx + 6, head_cy + 3, 12, 12, body_shadow, outline=None)
        ellipse(draw, cx + 4, head_cy, 13, 13, KRAK_BODY, outline=None)
        ellipse(draw, cx + 1, head_cy - 4, 8, 8, KRAK_LIGHT, outline=None)
        # Eye
        ellipse(draw, cx + 10, head_cy + 2, 5, 6, KRAK_EYE)
        ellipse(draw, cx + 10, head_cy + 2, 2, 3, BLACK, outline=None)
        # Beak
        draw.polygon([(cx + 12, head_cy + 8), (cx + 16, head_cy + 12),
                      (cx + 12, head_cy + 14)], fill=KRAK_BEAK, outline=OUTLINE)


# ===================================================================
# SPHINX (ID 92) — lion body, human face, Egyptian headdress, wings
# ===================================================================

def draw_sphinx(draw, ox, oy, direction, frame):
    bob = [0, -1, 0, -1][frame]
    leg_phase = [-2, 0, 2, 0][frame]
    wing_fold = [0, 2, 0, -2][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    # Lion body sits lower and longer
    body_cy = base_y - 12
    head_cy = body_cy - 18

    body_shadow = _darken(SPHINX_BODY, 0.7)

    # --- Lion legs (4, quadruped stance) ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            # Front legs
            fx = cx + side * 8 + (leg_phase if side == -1 else -leg_phase)
            draw.rectangle([fx - 3, body_cy + 4, fx + 3, base_y - 2],
                           fill=SPHINX_BODY, outline=OUTLINE)
            if direction == DOWN:
                draw.rectangle([fx - 3, body_cy + 4, fx - 1, base_y - 6],
                               fill=SPHINX_LIGHT, outline=None)
            ellipse(draw, fx, base_y, 4, 3, SPHINX_DARK)
    elif direction == LEFT:
        for offset in [leg_phase, -leg_phase]:
            lx = cx - 2 + offset
            draw.rectangle([lx - 3, body_cy + 4, lx + 3, base_y - 2],
                           fill=SPHINX_BODY, outline=OUTLINE)
            ellipse(draw, lx, base_y, 4, 3, SPHINX_DARK)
    else:  # RIGHT
        for offset in [leg_phase, -leg_phase]:
            lx = cx + 2 + offset
            draw.rectangle([lx - 3, body_cy + 4, lx + 3, base_y - 2],
                           fill=SPHINX_BODY, outline=OUTLINE)
            ellipse(draw, lx, base_y, 4, 3, SPHINX_DARK)

    # --- Wings (folded on back, triangular) ---
    if direction == DOWN:
        # Wings peek out from sides
        draw.polygon([(cx - 14, body_cy - 6), (cx - 22, body_cy - 14 + wing_fold),
                      (cx - 16, body_cy + 2)],
                     fill=SPHINX_WING, outline=OUTLINE)
        draw.polygon([(cx + 14, body_cy - 6), (cx + 22, body_cy - 14 + wing_fold),
                      (cx + 16, body_cy + 2)],
                     fill=SPHINX_WING, outline=OUTLINE)
    elif direction == UP:
        # Wings more visible from back
        draw.polygon([(cx - 10, body_cy - 8), (cx - 24, body_cy - 18 + wing_fold),
                      (cx - 14, body_cy + 4)],
                     fill=SPHINX_WING, outline=OUTLINE)
        draw.line([(cx - 14, body_cy - 10), (cx - 22, body_cy - 16 + wing_fold)],
                  fill=_darken(SPHINX_WING, 0.8), width=1)
        draw.polygon([(cx + 10, body_cy - 8), (cx + 24, body_cy - 18 + wing_fold),
                      (cx + 14, body_cy + 4)],
                     fill=SPHINX_WING, outline=OUTLINE)
        draw.line([(cx + 14, body_cy - 10), (cx + 22, body_cy - 16 + wing_fold)],
                  fill=_darken(SPHINX_WING, 0.8), width=1)
    elif direction == LEFT:
        # Far wing behind body
        draw.polygon([(cx + 6, body_cy - 6), (cx + 16, body_cy - 18 + wing_fold),
                      (cx + 10, body_cy + 4)],
                     fill=SPHINX_WING, outline=OUTLINE)
    else:  # RIGHT
        draw.polygon([(cx - 6, body_cy - 6), (cx - 16, body_cy - 18 + wing_fold),
                      (cx - 10, body_cy + 4)],
                     fill=SPHINX_WING, outline=OUTLINE)

    # --- Lion body ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy, 16, 10, SPHINX_BODY)
        ellipse(draw, cx + 3, body_cy + 2, 12, 7, body_shadow, outline=None)
        ellipse(draw, cx, body_cy, 13, 8, SPHINX_BODY, outline=None)
        ellipse(draw, cx - 2, body_cy - 2, 8, 5, SPHINX_LIGHT, outline=None)
        ellipse(draw, cx, body_cy + 3, 8, 4, SPHINX_BELLY, outline=None)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 16, 10, SPHINX_BODY)
        ellipse(draw, cx, body_cy, 13, 8, SPHINX_DARK, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy, 16, 10, SPHINX_BODY)
        ellipse(draw, cx + 2, body_cy + 2, 12, 7, body_shadow, outline=None)
        ellipse(draw, cx - 2, body_cy, 13, 8, SPHINX_BODY, outline=None)
        ellipse(draw, cx - 4, body_cy - 2, 8, 5, SPHINX_LIGHT, outline=None)
    else:  # RIGHT
        ellipse(draw, cx + 2, body_cy, 16, 10, SPHINX_BODY)
        ellipse(draw, cx + 6, body_cy + 2, 12, 7, body_shadow, outline=None)
        ellipse(draw, cx + 2, body_cy, 13, 8, SPHINX_BODY, outline=None)
        ellipse(draw, cx, body_cy - 2, 8, 5, SPHINX_LIGHT, outline=None)

    # --- Head (human face with Egyptian headdress / nemes) ---
    if direction == DOWN:
        # Nemes headdress (wider at sides, striped)
        draw.polygon([(cx - 14, head_cy - 4), (cx - 10, head_cy - 14),
                      (cx + 10, head_cy - 14), (cx + 14, head_cy - 4),
                      (cx + 12, head_cy + 8), (cx - 12, head_cy + 8)],
                     fill=SPHINX_HEADDRESS, outline=OUTLINE)
        # Gold stripes on headdress
        for y in range(head_cy - 12, head_cy + 6, 4):
            draw.line([(cx - 12, y), (cx + 12, y)],
                      fill=SPHINX_HEADDRESS_GOLD, width=1)
        # Face
        ellipse(draw, cx, head_cy, 8, 8, SPHINX_SKIN)
        ellipse(draw, cx - 1, head_cy - 2, 5, 4, _brighten(SPHINX_SKIN, 1.1), outline=None)
        # Eyes (Egyptian-style, lined)
        draw.rectangle([cx - 5, head_cy - 1, cx - 2, head_cy + 2], fill=SPHINX_EYE)
        draw.point((cx - 4, head_cy), fill=BLACK)
        draw.line([(cx - 6, head_cy - 1), (cx - 1, head_cy - 1)],
                  fill=BLACK, width=1)
        draw.rectangle([cx + 2, head_cy - 1, cx + 5, head_cy + 2], fill=SPHINX_EYE)
        draw.point((cx + 3, head_cy), fill=BLACK)
        draw.line([(cx + 1, head_cy - 1), (cx + 6, head_cy - 1)],
                  fill=BLACK, width=1)
        # Nose
        draw.point((cx, head_cy + 3), fill=SPHINX_SKIN_DARK)
        # Mouth
        draw.line([(cx - 2, head_cy + 5), (cx + 2, head_cy + 5)],
                  fill=SPHINX_SKIN_DARK, width=1)
        # Uraeus (cobra ornament at forehead)
        draw.point((cx, head_cy - 8), fill=SPHINX_HEADDRESS_GOLD)
        draw.point((cx - 1, head_cy - 9), fill=SPHINX_HEADDRESS_GOLD)
        draw.point((cx + 1, head_cy - 9), fill=SPHINX_HEADDRESS_GOLD)
    elif direction == UP:
        # Headdress from behind
        draw.polygon([(cx - 12, head_cy - 4), (cx - 8, head_cy - 14),
                      (cx + 8, head_cy - 14), (cx + 12, head_cy - 4),
                      (cx + 10, head_cy + 8), (cx - 10, head_cy + 8)],
                     fill=SPHINX_HEADDRESS, outline=OUTLINE)
        for y in range(head_cy - 12, head_cy + 6, 4):
            draw.line([(cx - 10, y), (cx + 10, y)],
                      fill=SPHINX_HEADDRESS_GOLD, width=1)
        ellipse(draw, cx, head_cy, 7, 7, _darken(SPHINX_HEADDRESS, 0.8))
    elif direction == LEFT:
        # Headdress side view
        draw.polygon([(cx - 6, head_cy - 14), (cx + 4, head_cy - 14),
                      (cx + 8, head_cy - 4), (cx + 6, head_cy + 8),
                      (cx - 8, head_cy + 8), (cx - 10, head_cy - 4)],
                     fill=SPHINX_HEADDRESS, outline=OUTLINE)
        for y in range(head_cy - 12, head_cy + 6, 4):
            draw.line([(cx - 8, y), (cx + 6, y)],
                      fill=SPHINX_HEADDRESS_GOLD, width=1)
        # Face
        ellipse(draw, cx - 4, head_cy, 6, 7, SPHINX_SKIN)
        ellipse(draw, cx - 5, head_cy - 2, 4, 4, _brighten(SPHINX_SKIN, 1.1), outline=None)
        # Eye
        draw.rectangle([cx - 8, head_cy - 1, cx - 5, head_cy + 2], fill=SPHINX_EYE)
        draw.point((cx - 7, head_cy), fill=BLACK)
        draw.line([(cx - 9, head_cy - 1), (cx - 4, head_cy - 1)],
                  fill=BLACK, width=1)
        draw.point((cx - 7, head_cy + 3), fill=SPHINX_SKIN_DARK)
    else:  # RIGHT
        draw.polygon([(cx - 4, head_cy - 14), (cx + 6, head_cy - 14),
                      (cx + 10, head_cy - 4), (cx + 8, head_cy + 8),
                      (cx - 6, head_cy + 8), (cx - 8, head_cy - 4)],
                     fill=SPHINX_HEADDRESS, outline=OUTLINE)
        for y in range(head_cy - 12, head_cy + 6, 4):
            draw.line([(cx - 6, y), (cx + 8, y)],
                      fill=SPHINX_HEADDRESS_GOLD, width=1)
        ellipse(draw, cx + 4, head_cy, 6, 7, SPHINX_SKIN)
        ellipse(draw, cx + 5, head_cy - 2, 4, 4, _brighten(SPHINX_SKIN, 1.1), outline=None)
        draw.rectangle([cx + 5, head_cy - 1, cx + 8, head_cy + 2], fill=SPHINX_EYE)
        draw.point((cx + 6, head_cy), fill=BLACK)
        draw.line([(cx + 4, head_cy - 1), (cx + 9, head_cy - 1)],
                  fill=BLACK, width=1)
        draw.point((cx + 7, head_cy + 3), fill=SPHINX_SKIN_DARK)


# ===================================================================
# CYCLOPS (ID 93) — very bulky, single large eye, club, tusks
# ===================================================================

def draw_cyclops(draw, ox, oy, direction, frame):
    bob = [0, -1, 0, -1][frame]
    sway = [-1, 0, 1, 0][frame]
    leg_spread = [-3, 0, 3, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32 + sway
    body_cy = base_y - 20
    head_cy = body_cy - 16

    body_shadow = _darken(CYCLOPS_BODY, 0.7)

    # --- Legs (very thick) ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            lx = cx + side * (10 + abs(leg_spread)) + (leg_spread if side == -1 else -leg_spread)
            draw.rectangle([lx - 5, body_cy + 10, lx + 5, base_y - 2],
                           fill=CYCLOPS_BODY, outline=OUTLINE)
            if direction == DOWN:
                draw.rectangle([lx - 5, body_cy + 10, lx - 3, base_y - 6],
                               fill=CYCLOPS_LIGHT, outline=None)
            # Fur wrapping around shins
            draw_fur_texture(draw, lx, base_y - 8, 8, 6, CYCLOPS_FUR, density=3)
            ellipse(draw, lx, base_y, 6, 3, CYCLOPS_DARK)
    elif direction == LEFT:
        for offset in [leg_spread, -leg_spread]:
            lx = cx - 2 + offset
            draw.rectangle([lx - 5, body_cy + 10, lx + 5, base_y - 2],
                           fill=CYCLOPS_BODY, outline=OUTLINE)
            draw_fur_texture(draw, lx, base_y - 8, 8, 6, CYCLOPS_FUR, density=3)
            ellipse(draw, lx, base_y, 6, 3, CYCLOPS_DARK)
    else:  # RIGHT
        for offset in [leg_spread, -leg_spread]:
            lx = cx + 2 + offset
            draw.rectangle([lx - 5, body_cy + 10, lx + 5, base_y - 2],
                           fill=CYCLOPS_BODY, outline=OUTLINE)
            draw_fur_texture(draw, lx, base_y - 8, 8, 6, CYCLOPS_FUR, density=3)
            ellipse(draw, lx, base_y, 6, 3, CYCLOPS_DARK)

    # --- Body (very wide, bulky) ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy, 20, 14, CYCLOPS_BODY)
        ellipse(draw, cx + 4, body_cy + 3, 16, 10, body_shadow, outline=None)
        ellipse(draw, cx, body_cy, 17, 11, CYCLOPS_BODY, outline=None)
        ellipse(draw, cx - 3, body_cy - 3, 10, 7, CYCLOPS_LIGHT, outline=None)
        ellipse(draw, cx, body_cy + 4, 12, 6, CYCLOPS_BELLY, outline=None)
        # Fur clothing (shaggy texture around waist)
        draw_fur_texture(draw, cx, body_cy + 8, 28, 6, CYCLOPS_FUR, density=3)
        # Arms (massive)
        draw.rectangle([cx - 24, body_cy - 8, cx - 18, body_cy + 6],
                       fill=CYCLOPS_BODY, outline=OUTLINE)
        draw.rectangle([cx - 24, body_cy - 8, cx - 22, body_cy + 2],
                       fill=CYCLOPS_LIGHT, outline=None)
        ellipse(draw, cx - 21, body_cy + 8, 4, 3, CYCLOPS_DARK)
        # Club in right hand
        draw.rectangle([cx + 18, body_cy - 8, cx + 24, body_cy + 6],
                       fill=CYCLOPS_BODY, outline=OUTLINE)
        ellipse(draw, cx + 21, body_cy + 8, 4, 3, CYCLOPS_DARK)
        # Club
        draw.rectangle([cx + 19, body_cy - 20, cx + 23, body_cy - 6],
                       fill=CYCLOPS_CLUB, outline=OUTLINE)
        draw.rectangle([cx + 20, body_cy - 20, cx + 22, body_cy - 14],
                       fill=CYCLOPS_CLUB_DARK, outline=None)
        ellipse(draw, cx + 21, body_cy - 22, 4, 4, CYCLOPS_CLUB)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 20, 14, CYCLOPS_BODY)
        ellipse(draw, cx, body_cy, 17, 11, CYCLOPS_DARK, outline=None)
        draw_fur_texture(draw, cx, body_cy + 8, 28, 6, CYCLOPS_FUR, density=3)
        draw.rectangle([cx - 24, body_cy - 8, cx - 18, body_cy + 6],
                       fill=CYCLOPS_BODY, outline=OUTLINE)
        draw.rectangle([cx + 18, body_cy - 8, cx + 24, body_cy + 6],
                       fill=CYCLOPS_BODY, outline=OUTLINE)
        # Club on back
        draw.rectangle([cx + 19, body_cy - 20, cx + 23, body_cy - 6],
                       fill=CYCLOPS_CLUB, outline=OUTLINE)
        ellipse(draw, cx + 21, body_cy - 22, 4, 4, CYCLOPS_CLUB)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy, 18, 14, CYCLOPS_BODY)
        ellipse(draw, cx + 2, body_cy + 3, 14, 10, body_shadow, outline=None)
        ellipse(draw, cx - 2, body_cy, 15, 11, CYCLOPS_BODY, outline=None)
        ellipse(draw, cx - 5, body_cy - 3, 8, 6, CYCLOPS_LIGHT, outline=None)
        draw_fur_texture(draw, cx, body_cy + 8, 24, 6, CYCLOPS_FUR, density=3)
        # Arm with club
        draw.rectangle([cx - 18, body_cy - 6, cx - 12, body_cy + 6],
                       fill=CYCLOPS_BODY, outline=OUTLINE)
        draw.rectangle([cx - 18, body_cy - 6, cx - 16, body_cy + 2],
                       fill=CYCLOPS_LIGHT, outline=None)
        ellipse(draw, cx - 15, body_cy + 8, 4, 3, CYCLOPS_DARK)
        # Club
        draw.rectangle([cx - 17, body_cy - 20, cx - 13, body_cy - 4],
                       fill=CYCLOPS_CLUB, outline=OUTLINE)
        ellipse(draw, cx - 15, body_cy - 22, 4, 4, CYCLOPS_CLUB)
    else:  # RIGHT
        ellipse(draw, cx + 2, body_cy, 18, 14, CYCLOPS_BODY)
        ellipse(draw, cx + 6, body_cy + 3, 14, 10, body_shadow, outline=None)
        ellipse(draw, cx + 2, body_cy, 15, 11, CYCLOPS_BODY, outline=None)
        ellipse(draw, cx + 5, body_cy - 3, 8, 6, CYCLOPS_LIGHT, outline=None)
        draw_fur_texture(draw, cx + 2, body_cy + 8, 24, 6, CYCLOPS_FUR, density=3)
        # Arm with club
        draw.rectangle([cx + 12, body_cy - 6, cx + 18, body_cy + 6],
                       fill=CYCLOPS_BODY, outline=OUTLINE)
        ellipse(draw, cx + 15, body_cy + 8, 4, 3, CYCLOPS_DARK)
        # Club
        draw.rectangle([cx + 13, body_cy - 20, cx + 17, body_cy - 4],
                       fill=CYCLOPS_CLUB, outline=OUTLINE)
        ellipse(draw, cx + 15, body_cy - 22, 4, 4, CYCLOPS_CLUB)

    # --- Head (large, SINGLE EYE) ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 14, 12, CYCLOPS_BODY)
        ellipse(draw, cx - 2, head_cy - 2, 10, 8, CYCLOPS_LIGHT, outline=None)
        # SINGLE LARGE EYE (centered)
        ellipse(draw, cx, head_cy, 8, 7, CYCLOPS_EYE_WHITE)
        ellipse(draw, cx, head_cy, 4, 4, CYCLOPS_EYE_IRIS)
        ellipse(draw, cx, head_cy, 2, 2, BLACK, outline=None)
        draw.point((cx - 2, head_cy - 2), fill=(255, 255, 255))
        # Brow ridge
        draw.arc([cx - 10, head_cy - 8, cx + 10, head_cy + 2],
                 start=200, end=340, fill=CYCLOPS_DARK, width=2)
        # Tusks from lower jaw
        draw.polygon([(cx - 5, head_cy + 8), (cx - 4, head_cy + 14),
                      (cx - 3, head_cy + 8)], fill=CYCLOPS_TUSK, outline=OUTLINE)
        draw.polygon([(cx + 3, head_cy + 8), (cx + 4, head_cy + 14),
                      (cx + 5, head_cy + 8)], fill=CYCLOPS_TUSK, outline=OUTLINE)
        # Mouth
        draw.line([(cx - 6, head_cy + 8), (cx + 6, head_cy + 8)],
                  fill=CYCLOPS_DARK, width=1)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 14, 12, CYCLOPS_BODY)
        ellipse(draw, cx, head_cy, 10, 8, CYCLOPS_DARK, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 12, 11, CYCLOPS_BODY)
        ellipse(draw, cx - 4, head_cy - 2, 8, 7, CYCLOPS_LIGHT, outline=None)
        # Single eye (side view)
        ellipse(draw, cx - 6, head_cy, 6, 5, CYCLOPS_EYE_WHITE)
        ellipse(draw, cx - 7, head_cy, 3, 3, CYCLOPS_EYE_IRIS)
        ellipse(draw, cx - 7, head_cy, 1, 1, BLACK, outline=None)
        draw.point((cx - 9, head_cy - 2), fill=(255, 255, 255))
        # Brow ridge
        draw.arc([cx - 14, head_cy - 6, cx, head_cy + 4],
                 start=180, end=300, fill=CYCLOPS_DARK, width=2)
        # Tusk
        draw.polygon([(cx - 6, head_cy + 7), (cx - 8, head_cy + 13),
                      (cx - 4, head_cy + 7)], fill=CYCLOPS_TUSK, outline=OUTLINE)
        draw.line([(cx - 10, head_cy + 7), (cx - 2, head_cy + 7)],
                  fill=CYCLOPS_DARK, width=1)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 12, 11, CYCLOPS_BODY)
        ellipse(draw, cx + 4, head_cy - 2, 8, 7, CYCLOPS_LIGHT, outline=None)
        # Single eye
        ellipse(draw, cx + 6, head_cy, 6, 5, CYCLOPS_EYE_WHITE)
        ellipse(draw, cx + 7, head_cy, 3, 3, CYCLOPS_EYE_IRIS)
        ellipse(draw, cx + 7, head_cy, 1, 1, BLACK, outline=None)
        draw.point((cx + 5, head_cy - 2), fill=(255, 255, 255))
        # Brow ridge
        draw.arc([cx, head_cy - 6, cx + 14, head_cy + 4],
                 start=240, end=360, fill=CYCLOPS_DARK, width=2)
        # Tusk
        draw.polygon([(cx + 4, head_cy + 7), (cx + 8, head_cy + 13),
                      (cx + 6, head_cy + 7)], fill=CYCLOPS_TUSK, outline=OUTLINE)
        draw.line([(cx + 2, head_cy + 7), (cx + 10, head_cy + 7)],
                  fill=CYCLOPS_DARK, width=1)


# ===================================================================
# HARPY (ID 94) — bird body, humanoid face, feathered wing-arms
# ===================================================================

def draw_harpy(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    wing_flap = [-4, 2, -4, 0][frame]
    leg_step = [0, 2, 0, -2][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 16

    body_shadow = _darken(HARPY_BODY, 0.7)

    # --- Taloned bird feet ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            fx = cx + side * 7 + side * leg_step
            # Thin bird leg
            draw.line([(fx, body_cy + 10), (fx, base_y - 2)],
                      fill=HARPY_TALON, width=2)
            # Three talons
            for tx in [-4, 0, 4]:
                draw.line([(fx, base_y - 2), (fx + tx, base_y + 3)],
                          fill=HARPY_TALON, width=2)
                draw.point((fx + tx, base_y + 3), fill=OUTLINE)
    elif direction == LEFT:
        for off in [leg_step, -leg_step]:
            fx = cx - 3 + off
            draw.line([(fx, body_cy + 10), (fx, base_y - 2)],
                      fill=HARPY_TALON, width=2)
            for tx in [-4, 0, 3]:
                draw.line([(fx, base_y - 2), (fx + tx, base_y + 3)],
                          fill=HARPY_TALON, width=2)
                draw.point((fx + tx, base_y + 3), fill=OUTLINE)
    else:  # RIGHT
        for off in [leg_step, -leg_step]:
            fx = cx + 3 + off
            draw.line([(fx, body_cy + 10), (fx, base_y - 2)],
                      fill=HARPY_TALON, width=2)
            for tx in [-3, 0, 4]:
                draw.line([(fx, base_y - 2), (fx + tx, base_y + 3)],
                          fill=HARPY_TALON, width=2)
                draw.point((fx + tx, base_y + 3), fill=OUTLINE)

    # --- Wing-arms (feathered, spread out) ---
    if direction == DOWN:
        # Left wing
        draw.polygon([
            (cx - 12, body_cy - 6),
            (cx - 26, body_cy - 10 + wing_flap),
            (cx - 28, body_cy - 4 + wing_flap),
            (cx - 24, body_cy + 2 + wing_flap),
            (cx - 14, body_cy + 4),
        ], fill=HARPY_BODY, outline=OUTLINE)
        # Feather tips (darker)
        draw.line([(cx - 26, body_cy - 8 + wing_flap),
                   (cx - 24, body_cy + wing_flap)],
                  fill=HARPY_WING_TIP, width=2)
        # Right wing
        draw.polygon([
            (cx + 12, body_cy - 6),
            (cx + 26, body_cy - 10 + wing_flap),
            (cx + 28, body_cy - 4 + wing_flap),
            (cx + 24, body_cy + 2 + wing_flap),
            (cx + 14, body_cy + 4),
        ], fill=HARPY_BODY, outline=OUTLINE)
        draw.line([(cx + 26, body_cy - 8 + wing_flap),
                   (cx + 24, body_cy + wing_flap)],
                  fill=HARPY_WING_TIP, width=2)
    elif direction == UP:
        # Wings spread wider from back
        draw.polygon([
            (cx - 10, body_cy - 8),
            (cx - 28, body_cy - 14 + wing_flap),
            (cx - 30, body_cy - 6 + wing_flap),
            (cx - 26, body_cy + 2 + wing_flap),
            (cx - 12, body_cy + 4),
        ], fill=HARPY_BODY, outline=OUTLINE)
        draw.line([(cx - 20, body_cy - 10 + wing_flap),
                   (cx - 26, body_cy - 2 + wing_flap)],
                  fill=HARPY_DARK, width=1)
        draw.polygon([
            (cx + 10, body_cy - 8),
            (cx + 28, body_cy - 14 + wing_flap),
            (cx + 30, body_cy - 6 + wing_flap),
            (cx + 26, body_cy + 2 + wing_flap),
            (cx + 12, body_cy + 4),
        ], fill=HARPY_BODY, outline=OUTLINE)
        draw.line([(cx + 20, body_cy - 10 + wing_flap),
                   (cx + 26, body_cy - 2 + wing_flap)],
                  fill=HARPY_DARK, width=1)
    elif direction == LEFT:
        # Near wing (in front)
        draw.polygon([
            (cx - 8, body_cy - 6),
            (cx - 22, body_cy - 12 + wing_flap),
            (cx - 24, body_cy - 4 + wing_flap),
            (cx - 18, body_cy + 4 + wing_flap),
            (cx - 10, body_cy + 4),
        ], fill=HARPY_BODY, outline=OUTLINE)
        draw.line([(cx - 22, body_cy - 10 + wing_flap),
                   (cx - 20, body_cy + wing_flap)],
                  fill=HARPY_WING_TIP, width=2)
    else:  # RIGHT
        draw.polygon([
            (cx + 8, body_cy - 6),
            (cx + 22, body_cy - 12 + wing_flap),
            (cx + 24, body_cy - 4 + wing_flap),
            (cx + 18, body_cy + 4 + wing_flap),
            (cx + 10, body_cy + 4),
        ], fill=HARPY_BODY, outline=OUTLINE)
        draw.line([(cx + 22, body_cy - 10 + wing_flap),
                   (cx + 20, body_cy + wing_flap)],
                  fill=HARPY_WING_TIP, width=2)

    # --- Body (bird-like, rounder) ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy, 12, 12, HARPY_BODY)
        ellipse(draw, cx + 2, body_cy + 2, 8, 8, body_shadow, outline=None)
        ellipse(draw, cx, body_cy, 9, 9, HARPY_BODY, outline=None)
        ellipse(draw, cx - 2, body_cy - 2, 6, 5, HARPY_LIGHT, outline=None)
        # Light belly
        ellipse(draw, cx, body_cy + 4, 7, 5, HARPY_BELLY, outline=None)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 12, 12, HARPY_BODY)
        ellipse(draw, cx, body_cy, 9, 9, HARPY_DARK, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy, 10, 12, HARPY_BODY)
        ellipse(draw, cx, body_cy + 2, 7, 8, body_shadow, outline=None)
        ellipse(draw, cx - 2, body_cy, 8, 9, HARPY_BODY, outline=None)
        ellipse(draw, cx - 4, body_cy - 2, 5, 5, HARPY_LIGHT, outline=None)
        ellipse(draw, cx - 2, body_cy + 3, 5, 4, HARPY_BELLY, outline=None)
    else:  # RIGHT
        ellipse(draw, cx + 2, body_cy, 10, 12, HARPY_BODY)
        ellipse(draw, cx + 4, body_cy + 2, 7, 8, body_shadow, outline=None)
        ellipse(draw, cx + 2, body_cy, 8, 9, HARPY_BODY, outline=None)
        ellipse(draw, cx, body_cy - 2, 5, 5, HARPY_LIGHT, outline=None)
        ellipse(draw, cx + 2, body_cy + 3, 5, 4, HARPY_BELLY, outline=None)

    # --- Head (humanoid face with feathered crest) ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 10, 10, HARPY_BODY)
        ellipse(draw, cx - 1, head_cy - 2, 7, 6, HARPY_LIGHT, outline=None)
        # Face area
        ellipse(draw, cx, head_cy + 2, 7, 6, HARPY_SKIN)
        ellipse(draw, cx + 1, head_cy + 4, 4, 3, HARPY_SKIN_DARK, outline=None)
        # Eyes (fierce)
        draw.rectangle([cx - 5, head_cy, cx - 2, head_cy + 3], fill=HARPY_EYE)
        draw.point((cx - 4, head_cy + 1), fill=BLACK)
        draw.rectangle([cx + 2, head_cy, cx + 5, head_cy + 3], fill=HARPY_EYE)
        draw.point((cx + 3, head_cy + 1), fill=BLACK)
        # Angry eyebrows
        draw.line([(cx - 6, head_cy), (cx - 2, head_cy - 1)],
                  fill=HARPY_DARK, width=1)
        draw.line([(cx + 2, head_cy - 1), (cx + 6, head_cy)],
                  fill=HARPY_DARK, width=1)
        # Small beak/mouth
        draw.polygon([(cx - 2, head_cy + 5), (cx, head_cy + 8),
                      (cx + 2, head_cy + 5)], fill=HARPY_TALON, outline=OUTLINE)
        # Feathered crest (plume on top)
        draw.polygon([(cx - 3, head_cy - 8), (cx, head_cy - 18),
                      (cx + 3, head_cy - 8)], fill=HARPY_CREST, outline=OUTLINE)
        draw.polygon([(cx - 6, head_cy - 6), (cx - 4, head_cy - 14),
                      (cx - 1, head_cy - 7)], fill=HARPY_BODY, outline=OUTLINE)
        draw.polygon([(cx + 1, head_cy - 7), (cx + 4, head_cy - 14),
                      (cx + 6, head_cy - 6)], fill=HARPY_BODY, outline=OUTLINE)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 10, 10, HARPY_BODY)
        ellipse(draw, cx, head_cy, 7, 7, HARPY_DARK, outline=None)
        # Crest (back view)
        draw.polygon([(cx - 3, head_cy - 8), (cx, head_cy - 18),
                      (cx + 3, head_cy - 8)], fill=HARPY_CREST, outline=OUTLINE)
        draw.polygon([(cx - 6, head_cy - 6), (cx - 4, head_cy - 14),
                      (cx - 1, head_cy - 7)], fill=HARPY_BODY, outline=OUTLINE)
        draw.polygon([(cx + 1, head_cy - 7), (cx + 4, head_cy - 14),
                      (cx + 6, head_cy - 6)], fill=HARPY_BODY, outline=OUTLINE)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 9, 9, HARPY_BODY)
        ellipse(draw, cx - 3, head_cy - 2, 6, 5, HARPY_LIGHT, outline=None)
        # Face
        ellipse(draw, cx - 4, head_cy + 2, 6, 5, HARPY_SKIN)
        # Eye
        draw.rectangle([cx - 8, head_cy, cx - 5, head_cy + 3], fill=HARPY_EYE)
        draw.point((cx - 7, head_cy + 1), fill=BLACK)
        draw.line([(cx - 9, head_cy), (cx - 5, head_cy - 1)],
                  fill=HARPY_DARK, width=1)
        # Beak
        draw.polygon([(cx - 8, head_cy + 4), (cx - 12, head_cy + 5),
                      (cx - 8, head_cy + 6)], fill=HARPY_TALON, outline=OUTLINE)
        # Crest
        draw.polygon([(cx - 4, head_cy - 7), (cx - 2, head_cy - 16),
                      (cx + 1, head_cy - 7)], fill=HARPY_CREST, outline=OUTLINE)
        draw.polygon([(cx + 1, head_cy - 5), (cx + 3, head_cy - 12),
                      (cx + 5, head_cy - 5)], fill=HARPY_BODY, outline=OUTLINE)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 9, 9, HARPY_BODY)
        ellipse(draw, cx + 3, head_cy - 2, 6, 5, HARPY_LIGHT, outline=None)
        # Face
        ellipse(draw, cx + 4, head_cy + 2, 6, 5, HARPY_SKIN)
        # Eye
        draw.rectangle([cx + 5, head_cy, cx + 8, head_cy + 3], fill=HARPY_EYE)
        draw.point((cx + 6, head_cy + 1), fill=BLACK)
        draw.line([(cx + 5, head_cy - 1), (cx + 9, head_cy)],
                  fill=HARPY_DARK, width=1)
        # Beak
        draw.polygon([(cx + 8, head_cy + 4), (cx + 12, head_cy + 5),
                      (cx + 8, head_cy + 6)], fill=HARPY_TALON, outline=OUTLINE)
        # Crest
        draw.polygon([(cx - 1, head_cy - 7), (cx + 2, head_cy - 16),
                      (cx + 4, head_cy - 7)], fill=HARPY_CREST, outline=OUTLINE)
        draw.polygon([(cx - 5, head_cy - 5), (cx - 3, head_cy - 12),
                      (cx - 1, head_cy - 5)], fill=HARPY_BODY, outline=OUTLINE)


# ===================================================================
# GRIFFIN (ID 95) — eagle head + lion body, large spread wings, talons
# ===================================================================

# Griffin palette
GRIFFIN_BODY = (180, 150, 80)
GRIFFIN_LIGHT = (210, 185, 110)
GRIFFIN_DARK = (140, 115, 55)
GRIFFIN_BELLY = (200, 180, 130)
GRIFFIN_WING = (160, 130, 60)
GRIFFIN_WING_TIP = (110, 90, 40)
GRIFFIN_BEAK = (200, 160, 40)
GRIFFIN_EYE = (200, 140, 30)
GRIFFIN_TALON = (80, 70, 40)
GRIFFIN_FUR = (160, 130, 70)


def draw_griffin(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_phase = [-2, 0, 2, 0][frame]
    wing_flap = [-4, 0, 4, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 14
    head_cy = body_cy - 18

    body_shadow = _darken(GRIFFIN_BODY, 0.7)

    # --- Legs (front talons, rear paws) ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            # Front talons (eagle-like)
            fx = cx + side * 8 + (leg_phase if side == -1 else -leg_phase)
            draw.rectangle([fx - 3, body_cy + 4, fx + 3, base_y - 4],
                           fill=GRIFFIN_DARK, outline=OUTLINE)
            # Talon claws
            for t in [-2, 0, 2]:
                draw.line([(fx + t, base_y - 4), (fx + t, base_y)],
                          fill=GRIFFIN_TALON, width=1)
            # Rear paws (lion-like)
            rx = cx + side * 12
            draw.rectangle([rx - 3, body_cy + 6, rx + 3, base_y - 2],
                           fill=GRIFFIN_FUR, outline=OUTLINE)
            ellipse(draw, rx, base_y, 4, 3, GRIFFIN_DARK)
    elif direction == LEFT:
        for offset in [leg_phase, -leg_phase]:
            lx = cx - 2 + offset
            draw.rectangle([lx - 3, body_cy + 4, lx + 3, base_y - 4],
                           fill=GRIFFIN_DARK, outline=OUTLINE)
            for t in [-2, 0, 2]:
                draw.line([(lx + t, base_y - 4), (lx + t, base_y)],
                          fill=GRIFFIN_TALON, width=1)
    else:  # RIGHT
        for offset in [leg_phase, -leg_phase]:
            lx = cx + 2 + offset
            draw.rectangle([lx - 3, body_cy + 4, lx + 3, base_y - 4],
                           fill=GRIFFIN_DARK, outline=OUTLINE)
            for t in [-2, 0, 2]:
                draw.line([(lx + t, base_y - 4), (lx + t, base_y)],
                          fill=GRIFFIN_TALON, width=1)

    # --- Wings (large, spread) ---
    if direction == DOWN:
        for side in [-1, 1]:
            wx = cx + side * 14
            draw.polygon([
                (wx, body_cy - 8),
                (wx + side * 14, body_cy - 18 + wing_flap),
                (wx + side * 16, body_cy - 10 + wing_flap),
                (wx + side * 12, body_cy + 4 + wing_flap),
                (wx + side * 2, body_cy + 4),
            ], fill=GRIFFIN_WING, outline=OUTLINE)
            # Feather details
            draw.line([(wx + side * 6, body_cy - 12 + wing_flap),
                       (wx + side * 12, body_cy - 4 + wing_flap)],
                      fill=GRIFFIN_WING_TIP, width=2)
    elif direction == UP:
        for side in [-1, 1]:
            wx = cx + side * 12
            draw.polygon([
                (wx, body_cy - 8),
                (wx + side * 16, body_cy - 20 + wing_flap),
                (wx + side * 18, body_cy - 10 + wing_flap),
                (wx + side * 14, body_cy + 4 + wing_flap),
                (wx + side * 2, body_cy + 4),
            ], fill=GRIFFIN_WING, outline=OUTLINE)
            draw.line([(wx + side * 8, body_cy - 14 + wing_flap),
                       (wx + side * 14, body_cy - 4 + wing_flap)],
                      fill=GRIFFIN_DARK, width=1)
    elif direction == LEFT:
        draw.polygon([
            (cx - 8, body_cy - 6),
            (cx - 22, body_cy - 16 + wing_flap),
            (cx - 24, body_cy - 8 + wing_flap),
            (cx - 18, body_cy + 4 + wing_flap),
            (cx - 10, body_cy + 4),
        ], fill=GRIFFIN_WING, outline=OUTLINE)
        draw.line([(cx - 16, body_cy - 12 + wing_flap),
                   (cx - 20, body_cy - 2 + wing_flap)],
                  fill=GRIFFIN_WING_TIP, width=2)
    else:  # RIGHT
        draw.polygon([
            (cx + 8, body_cy - 6),
            (cx + 22, body_cy - 16 + wing_flap),
            (cx + 24, body_cy - 8 + wing_flap),
            (cx + 18, body_cy + 4 + wing_flap),
            (cx + 10, body_cy + 4),
        ], fill=GRIFFIN_WING, outline=OUTLINE)
        draw.line([(cx + 16, body_cy - 12 + wing_flap),
                   (cx + 20, body_cy - 2 + wing_flap)],
                  fill=GRIFFIN_WING_TIP, width=2)

    # --- Body (lion rear, feathered front) ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy, 16, 11, GRIFFIN_BODY)
        ellipse(draw, cx + 3, body_cy + 2, 12, 8, body_shadow, outline=None)
        ellipse(draw, cx, body_cy, 13, 9, GRIFFIN_BODY, outline=None)
        ellipse(draw, cx - 2, body_cy - 2, 8, 5, GRIFFIN_LIGHT, outline=None)
        ellipse(draw, cx, body_cy + 3, 8, 4, GRIFFIN_BELLY, outline=None)
        # Fur texture on rear half
        draw_fur_texture(draw, cx + 4, body_cy + 4, 14, 6, GRIFFIN_FUR, density=3)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 16, 11, GRIFFIN_BODY)
        ellipse(draw, cx, body_cy, 13, 9, GRIFFIN_DARK, outline=None)
        draw_fur_texture(draw, cx, body_cy + 4, 16, 6, GRIFFIN_FUR, density=3)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy, 16, 11, GRIFFIN_BODY)
        ellipse(draw, cx + 2, body_cy + 2, 12, 8, body_shadow, outline=None)
        ellipse(draw, cx - 2, body_cy, 13, 9, GRIFFIN_BODY, outline=None)
        ellipse(draw, cx - 4, body_cy - 2, 8, 5, GRIFFIN_LIGHT, outline=None)
        draw_fur_texture(draw, cx + 4, body_cy + 2, 12, 6, GRIFFIN_FUR, density=2)
    else:  # RIGHT
        ellipse(draw, cx + 2, body_cy, 16, 11, GRIFFIN_BODY)
        ellipse(draw, cx + 6, body_cy + 2, 12, 8, body_shadow, outline=None)
        ellipse(draw, cx + 2, body_cy, 13, 9, GRIFFIN_BODY, outline=None)
        ellipse(draw, cx, body_cy - 2, 8, 5, GRIFFIN_LIGHT, outline=None)
        draw_fur_texture(draw, cx - 4, body_cy + 2, 12, 6, GRIFFIN_FUR, density=2)

    # --- Head (eagle with sharp beak, fierce eyes) ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 10, 9, GRIFFIN_BODY)
        ellipse(draw, cx - 2, head_cy - 2, 7, 5, GRIFFIN_LIGHT, outline=None)
        # Fierce eyes
        draw.rectangle([cx - 6, head_cy - 1, cx - 2, head_cy + 2], fill=GRIFFIN_EYE)
        draw.point((cx - 4, head_cy), fill=BLACK)
        draw.rectangle([cx + 2, head_cy - 1, cx + 6, head_cy + 2], fill=GRIFFIN_EYE)
        draw.point((cx + 4, head_cy), fill=BLACK)
        # Angry brow ridges
        draw.line([(cx - 7, head_cy - 1), (cx - 2, head_cy - 2)],
                  fill=GRIFFIN_DARK, width=1)
        draw.line([(cx + 2, head_cy - 2), (cx + 7, head_cy - 1)],
                  fill=GRIFFIN_DARK, width=1)
        # Sharp beak
        draw.polygon([(cx - 3, head_cy + 4), (cx, head_cy + 10),
                      (cx + 3, head_cy + 4)], fill=GRIFFIN_BEAK, outline=OUTLINE)
        draw.line([(cx, head_cy + 6), (cx, head_cy + 10)], fill=GRIFFIN_DARK, width=1)
        # Small ear tufts
        draw.polygon([(cx - 6, head_cy - 6), (cx - 4, head_cy - 12),
                      (cx - 2, head_cy - 6)], fill=GRIFFIN_LIGHT, outline=OUTLINE)
        draw.polygon([(cx + 2, head_cy - 6), (cx + 4, head_cy - 12),
                      (cx + 6, head_cy - 6)], fill=GRIFFIN_LIGHT, outline=OUTLINE)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 10, 9, GRIFFIN_BODY)
        ellipse(draw, cx, head_cy, 7, 6, GRIFFIN_DARK, outline=None)
        # Ear tufts
        draw.polygon([(cx - 6, head_cy - 6), (cx - 4, head_cy - 12),
                      (cx - 2, head_cy - 6)], fill=GRIFFIN_LIGHT, outline=OUTLINE)
        draw.polygon([(cx + 2, head_cy - 6), (cx + 4, head_cy - 12),
                      (cx + 6, head_cy - 6)], fill=GRIFFIN_LIGHT, outline=OUTLINE)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 9, 9, GRIFFIN_BODY)
        ellipse(draw, cx - 4, head_cy - 2, 6, 5, GRIFFIN_LIGHT, outline=None)
        # Eye
        draw.rectangle([cx - 7, head_cy - 1, cx - 3, head_cy + 2], fill=GRIFFIN_EYE)
        draw.point((cx - 5, head_cy), fill=BLACK)
        draw.line([(cx - 8, head_cy - 1), (cx - 3, head_cy - 2)],
                  fill=GRIFFIN_DARK, width=1)
        # Beak (pointing left)
        draw.polygon([(cx - 8, head_cy + 3), (cx - 14, head_cy + 5),
                      (cx - 8, head_cy + 6)], fill=GRIFFIN_BEAK, outline=OUTLINE)
        # Ear tuft
        draw.polygon([(cx - 4, head_cy - 7), (cx - 2, head_cy - 14),
                      (cx + 1, head_cy - 7)], fill=GRIFFIN_LIGHT, outline=OUTLINE)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 9, 9, GRIFFIN_BODY)
        ellipse(draw, cx + 4, head_cy - 2, 6, 5, GRIFFIN_LIGHT, outline=None)
        # Eye
        draw.rectangle([cx + 3, head_cy - 1, cx + 7, head_cy + 2], fill=GRIFFIN_EYE)
        draw.point((cx + 5, head_cy), fill=BLACK)
        draw.line([(cx + 3, head_cy - 2), (cx + 8, head_cy - 1)],
                  fill=GRIFFIN_DARK, width=1)
        # Beak (pointing right)
        draw.polygon([(cx + 8, head_cy + 3), (cx + 14, head_cy + 5),
                      (cx + 8, head_cy + 6)], fill=GRIFFIN_BEAK, outline=OUTLINE)
        # Ear tuft
        draw.polygon([(cx - 1, head_cy - 7), (cx + 2, head_cy - 14),
                      (cx + 4, head_cy - 7)], fill=GRIFFIN_LIGHT, outline=OUTLINE)


# ===================================================================
# ANUBIS (ID 96) — jackal head, Egyptian gold armor, staff with ankh
# ===================================================================

# Anubis palette
ANUBIS_BODY = (40, 40, 60)
ANUBIS_LIGHT = (60, 60, 80)
ANUBIS_DARK = (25, 25, 40)
ANUBIS_GOLD = (200, 170, 50)
ANUBIS_GOLD_DARK = (160, 130, 30)
ANUBIS_EYE = (80, 200, 200)
ANUBIS_STAFF = (140, 120, 60)
ANUBIS_STAFF_DARK = (100, 85, 40)


def draw_anubis(draw, ox, oy, direction, frame):
    bob = [0, -1, 0, -1][frame]
    sway = [-1, 0, 1, 0][frame]
    leg_spread = [-2, 0, 2, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32 + sway
    body_cy = base_y - 18
    head_cy = body_cy - 18

    body_shadow = _darken(ANUBIS_BODY, 0.7)

    # --- Staff (behind body for DOWN/LEFT, in front for UP/RIGHT) ---
    def draw_staff(sx, top_y, bottom_y):
        """Draw the ankh-topped staff."""
        draw.line([(sx, top_y + 8), (sx, bottom_y)],
                  fill=ANUBIS_STAFF, width=2)
        # Ankh top — loop
        draw.arc([sx - 4, top_y, sx + 4, top_y + 8],
                 start=0, end=360, fill=ANUBIS_GOLD, width=2)
        # Ankh crossbar
        draw.line([(sx - 4, top_y + 8), (sx + 4, top_y + 8)],
                  fill=ANUBIS_GOLD, width=2)

    if direction in (DOWN, LEFT):
        draw_staff(cx + 14, head_cy - 12, base_y)

    # --- Legs ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            lx = cx + side * 6 + (leg_spread if side == -1 else -leg_spread)
            draw.rectangle([lx - 3, body_cy + 10, lx + 3, base_y - 2],
                           fill=ANUBIS_BODY, outline=OUTLINE)
            # Gold ankle wraps
            draw.rectangle([lx - 3, base_y - 6, lx + 3, base_y - 4],
                           fill=ANUBIS_GOLD, outline=None)
            ellipse(draw, lx, base_y, 4, 3, ANUBIS_DARK)
    elif direction == LEFT:
        for offset in [leg_spread, -leg_spread]:
            lx = cx - 2 + offset
            draw.rectangle([lx - 3, body_cy + 10, lx + 3, base_y - 2],
                           fill=ANUBIS_BODY, outline=OUTLINE)
            draw.rectangle([lx - 3, base_y - 6, lx + 3, base_y - 4],
                           fill=ANUBIS_GOLD, outline=None)
            ellipse(draw, lx, base_y, 4, 3, ANUBIS_DARK)
    else:  # RIGHT
        for offset in [leg_spread, -leg_spread]:
            lx = cx + 2 + offset
            draw.rectangle([lx - 3, body_cy + 10, lx + 3, base_y - 2],
                           fill=ANUBIS_BODY, outline=OUTLINE)
            draw.rectangle([lx - 3, base_y - 6, lx + 3, base_y - 4],
                           fill=ANUBIS_GOLD, outline=None)
            ellipse(draw, lx, base_y, 4, 3, ANUBIS_DARK)

    # --- Body (slim, with gold armor plates) ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy, 12, 12, ANUBIS_BODY)
        ellipse(draw, cx + 2, body_cy + 2, 9, 9, body_shadow, outline=None)
        ellipse(draw, cx, body_cy, 10, 10, ANUBIS_BODY, outline=None)
        ellipse(draw, cx - 2, body_cy - 2, 6, 5, ANUBIS_LIGHT, outline=None)
        # Gold chest plate
        draw.polygon([(cx - 6, body_cy - 6), (cx, body_cy - 10),
                      (cx + 6, body_cy - 6), (cx + 4, body_cy + 2),
                      (cx - 4, body_cy + 2)], fill=ANUBIS_GOLD, outline=OUTLINE)
        draw.line([(cx, body_cy - 10), (cx, body_cy + 2)],
                  fill=ANUBIS_GOLD_DARK, width=1)
        # Gold shoulder plates
        ellipse(draw, cx - 12, body_cy - 6, 5, 4, ANUBIS_GOLD)
        ellipse(draw, cx + 12, body_cy - 6, 5, 4, ANUBIS_GOLD)
        # Arms
        draw.rectangle([cx - 16, body_cy - 4, cx - 10, body_cy + 6],
                       fill=ANUBIS_BODY, outline=OUTLINE)
        draw.rectangle([cx + 10, body_cy - 4, cx + 16, body_cy + 6],
                       fill=ANUBIS_BODY, outline=OUTLINE)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 12, 12, ANUBIS_BODY)
        ellipse(draw, cx, body_cy, 10, 10, ANUBIS_DARK, outline=None)
        # Gold back plate hint
        draw.polygon([(cx - 5, body_cy - 6), (cx, body_cy - 9),
                      (cx + 5, body_cy - 6), (cx + 3, body_cy)],
                     fill=ANUBIS_GOLD_DARK, outline=OUTLINE)
        ellipse(draw, cx - 12, body_cy - 6, 5, 4, ANUBIS_GOLD)
        ellipse(draw, cx + 12, body_cy - 6, 5, 4, ANUBIS_GOLD)
        draw.rectangle([cx - 16, body_cy - 4, cx - 10, body_cy + 6],
                       fill=ANUBIS_BODY, outline=OUTLINE)
        draw.rectangle([cx + 10, body_cy - 4, cx + 16, body_cy + 6],
                       fill=ANUBIS_BODY, outline=OUTLINE)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy, 11, 12, ANUBIS_BODY)
        ellipse(draw, cx, body_cy + 2, 8, 9, body_shadow, outline=None)
        ellipse(draw, cx - 2, body_cy, 9, 10, ANUBIS_BODY, outline=None)
        # Side armor
        draw.polygon([(cx - 6, body_cy - 6), (cx - 2, body_cy - 10),
                      (cx + 2, body_cy - 6), (cx, body_cy + 2),
                      (cx - 4, body_cy + 2)], fill=ANUBIS_GOLD, outline=OUTLINE)
        ellipse(draw, cx - 10, body_cy - 6, 5, 4, ANUBIS_GOLD)
        draw.rectangle([cx - 14, body_cy - 4, cx - 8, body_cy + 6],
                       fill=ANUBIS_BODY, outline=OUTLINE)
    else:  # RIGHT
        ellipse(draw, cx + 2, body_cy, 11, 12, ANUBIS_BODY)
        ellipse(draw, cx + 4, body_cy + 2, 8, 9, body_shadow, outline=None)
        ellipse(draw, cx + 2, body_cy, 9, 10, ANUBIS_BODY, outline=None)
        draw.polygon([(cx - 2, body_cy - 6), (cx + 2, body_cy - 10),
                      (cx + 6, body_cy - 6), (cx + 4, body_cy + 2),
                      (cx, body_cy + 2)], fill=ANUBIS_GOLD, outline=OUTLINE)
        ellipse(draw, cx + 10, body_cy - 6, 5, 4, ANUBIS_GOLD)
        draw.rectangle([cx + 8, body_cy - 4, cx + 14, body_cy + 6],
                       fill=ANUBIS_BODY, outline=OUTLINE)

    # --- Head (jackal with pointed snout and tall ears) ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 10, 9, ANUBIS_BODY)
        ellipse(draw, cx - 2, head_cy - 2, 7, 5, ANUBIS_LIGHT, outline=None)
        # Pointed snout
        draw.polygon([(cx - 4, head_cy + 4), (cx, head_cy + 12),
                      (cx + 4, head_cy + 4)], fill=ANUBIS_BODY, outline=OUTLINE)
        draw.line([(cx - 2, head_cy + 6), (cx + 2, head_cy + 6)],
                  fill=ANUBIS_LIGHT, width=1)
        draw.point((cx, head_cy + 10), fill=BLACK)
        # Eyes (glowing cyan)
        draw.rectangle([cx - 6, head_cy - 1, cx - 2, head_cy + 2], fill=ANUBIS_EYE)
        draw.point((cx - 4, head_cy), fill=BLACK)
        draw.rectangle([cx + 2, head_cy - 1, cx + 6, head_cy + 2], fill=ANUBIS_EYE)
        draw.point((cx + 4, head_cy), fill=BLACK)
        # Tall pointed ears
        draw.polygon([(cx - 8, head_cy - 4), (cx - 6, head_cy - 18),
                      (cx - 3, head_cy - 4)], fill=ANUBIS_BODY, outline=OUTLINE)
        draw.polygon([(cx - 7, head_cy - 6), (cx - 6, head_cy - 14),
                      (cx - 4, head_cy - 6)], fill=ANUBIS_LIGHT, outline=None)
        draw.polygon([(cx + 3, head_cy - 4), (cx + 6, head_cy - 18),
                      (cx + 8, head_cy - 4)], fill=ANUBIS_BODY, outline=OUTLINE)
        draw.polygon([(cx + 4, head_cy - 6), (cx + 6, head_cy - 14),
                      (cx + 7, head_cy - 6)], fill=ANUBIS_LIGHT, outline=None)
        # Gold headband
        draw.line([(cx - 8, head_cy - 4), (cx + 8, head_cy - 4)],
                  fill=ANUBIS_GOLD, width=2)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 10, 9, ANUBIS_BODY)
        ellipse(draw, cx, head_cy, 7, 6, ANUBIS_DARK, outline=None)
        # Ears
        draw.polygon([(cx - 8, head_cy - 4), (cx - 6, head_cy - 18),
                      (cx - 3, head_cy - 4)], fill=ANUBIS_BODY, outline=OUTLINE)
        draw.polygon([(cx + 3, head_cy - 4), (cx + 6, head_cy - 18),
                      (cx + 8, head_cy - 4)], fill=ANUBIS_BODY, outline=OUTLINE)
        draw.line([(cx - 8, head_cy - 4), (cx + 8, head_cy - 4)],
                  fill=ANUBIS_GOLD, width=2)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 9, 9, ANUBIS_BODY)
        ellipse(draw, cx - 4, head_cy - 2, 6, 5, ANUBIS_LIGHT, outline=None)
        # Snout (pointing left)
        draw.polygon([(cx - 6, head_cy + 2), (cx - 16, head_cy + 6),
                      (cx - 6, head_cy + 6)], fill=ANUBIS_BODY, outline=OUTLINE)
        draw.point((cx - 14, head_cy + 5), fill=BLACK)
        # Eye
        draw.rectangle([cx - 6, head_cy - 1, cx - 2, head_cy + 2], fill=ANUBIS_EYE)
        draw.point((cx - 4, head_cy), fill=BLACK)
        # Ear
        draw.polygon([(cx - 4, head_cy - 6), (cx - 2, head_cy - 18),
                      (cx + 2, head_cy - 6)], fill=ANUBIS_BODY, outline=OUTLINE)
        draw.polygon([(cx - 3, head_cy - 8), (cx - 2, head_cy - 14),
                      (cx + 1, head_cy - 8)], fill=ANUBIS_LIGHT, outline=None)
        draw.line([(cx - 6, head_cy - 4), (cx + 4, head_cy - 4)],
                  fill=ANUBIS_GOLD, width=2)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 9, 9, ANUBIS_BODY)
        ellipse(draw, cx + 4, head_cy - 2, 6, 5, ANUBIS_LIGHT, outline=None)
        # Snout (pointing right)
        draw.polygon([(cx + 6, head_cy + 2), (cx + 16, head_cy + 6),
                      (cx + 6, head_cy + 6)], fill=ANUBIS_BODY, outline=OUTLINE)
        draw.point((cx + 14, head_cy + 5), fill=BLACK)
        # Eye
        draw.rectangle([cx + 2, head_cy - 1, cx + 6, head_cy + 2], fill=ANUBIS_EYE)
        draw.point((cx + 4, head_cy), fill=BLACK)
        # Ear
        draw.polygon([(cx - 2, head_cy - 6), (cx + 2, head_cy - 18),
                      (cx + 4, head_cy - 6)], fill=ANUBIS_BODY, outline=OUTLINE)
        draw.polygon([(cx - 1, head_cy - 8), (cx + 2, head_cy - 14),
                      (cx + 3, head_cy - 8)], fill=ANUBIS_LIGHT, outline=None)
        draw.line([(cx - 4, head_cy - 4), (cx + 6, head_cy - 4)],
                  fill=ANUBIS_GOLD, width=2)

    # Staff in front for UP/RIGHT
    if direction in (UP, RIGHT):
        draw_staff(cx - 14, head_cy - 12, base_y)


# ===================================================================
# YOKAI (ID 97) — Oni demon with horns, wide robes, ghostly flames
# ===================================================================

# Yokai palette
YOKAI_SKIN = (180, 50, 50)
YOKAI_SKIN_LIGHT = (210, 80, 70)
YOKAI_SKIN_DARK = (130, 35, 35)
YOKAI_ROBE = (200, 200, 200)
YOKAI_ROBE_DARK = (160, 160, 170)
YOKAI_ROBE_ACCENT = (120, 30, 30)
YOKAI_HORN = (180, 170, 140)
YOKAI_FANG = (230, 230, 220)
YOKAI_EYE = (255, 220, 50)
YOKAI_FLAME1 = (255, 160, 40, 140)
YOKAI_FLAME2 = (255, 80, 30, 100)
YOKAI_FLAME3 = (200, 50, 200, 80)


def draw_yokai(draw, ox, oy, direction, frame):
    bob = [0, -1, 0, -1][frame]
    sway = [-1, 0, 1, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32 + sway
    body_cy = base_y - 16
    head_cy = body_cy - 18

    # --- Ghostly flame wisps (behind body) ---
    flame_colors = [YOKAI_FLAME1, YOKAI_FLAME2, YOKAI_FLAME3]
    flame_offsets = [
        (-12, -8 + frame * 2), (14, -4 - frame), (-8, 4 + frame),
        (10, 8 - frame * 2), (-16, 2 + frame), (16, -10 - frame),
    ]
    for i, (fdx, fdy) in enumerate(flame_offsets):
        fc = flame_colors[i % 3]
        fy = body_cy + fdy - (frame * 2 + i) % 6
        fx = cx + fdx + (frame + i) % 3 - 1
        ellipse(draw, fx, fy, 3, 3, fc, outline=None)

    # --- Wide robes (flowing, extends past arms) ---
    if direction == DOWN:
        # Robe body — wide trapezoid
        draw.polygon([(cx - 18, body_cy - 4), (cx - 8, body_cy - 12),
                      (cx + 8, body_cy - 12), (cx + 18, body_cy - 4),
                      (cx + 22, base_y), (cx - 22, base_y)],
                     fill=YOKAI_ROBE, outline=OUTLINE)
        # Robe shading
        draw.polygon([(cx + 4, body_cy - 8), (cx + 16, body_cy - 2),
                      (cx + 20, base_y - 4), (cx + 6, base_y)],
                     fill=YOKAI_ROBE_DARK, outline=None)
        # Red sash/belt
        draw.rectangle([cx - 16, body_cy, cx + 16, body_cy + 3],
                       fill=YOKAI_ROBE_ACCENT, outline=OUTLINE)
        # Wide sleeves (draping, extending past sides)
        draw.polygon([(cx - 18, body_cy - 6), (cx - 14, body_cy - 10),
                      (cx - 26, body_cy + 4), (cx - 22, body_cy + 8)],
                     fill=YOKAI_ROBE, outline=OUTLINE)
        draw.polygon([(cx + 14, body_cy - 10), (cx + 18, body_cy - 6),
                      (cx + 22, body_cy + 8), (cx + 26, body_cy + 4)],
                     fill=YOKAI_ROBE, outline=OUTLINE)
    elif direction == UP:
        draw.polygon([(cx - 18, body_cy - 4), (cx - 8, body_cy - 12),
                      (cx + 8, body_cy - 12), (cx + 18, body_cy - 4),
                      (cx + 22, base_y), (cx - 22, base_y)],
                     fill=YOKAI_ROBE, outline=OUTLINE)
        draw.polygon([(cx - 6, body_cy - 8), (cx + 6, body_cy - 8),
                      (cx + 4, base_y), (cx - 4, base_y)],
                     fill=YOKAI_ROBE_DARK, outline=None)
        draw.rectangle([cx - 16, body_cy, cx + 16, body_cy + 3],
                       fill=YOKAI_ROBE_ACCENT, outline=OUTLINE)
        draw.polygon([(cx - 18, body_cy - 6), (cx - 14, body_cy - 10),
                      (cx - 26, body_cy + 4), (cx - 22, body_cy + 8)],
                     fill=YOKAI_ROBE, outline=OUTLINE)
        draw.polygon([(cx + 14, body_cy - 10), (cx + 18, body_cy - 6),
                      (cx + 22, body_cy + 8), (cx + 26, body_cy + 4)],
                     fill=YOKAI_ROBE, outline=OUTLINE)
    elif direction == LEFT:
        draw.polygon([(cx - 14, body_cy - 4), (cx - 6, body_cy - 12),
                      (cx + 6, body_cy - 12), (cx + 12, body_cy - 4),
                      (cx + 16, base_y), (cx - 18, base_y)],
                     fill=YOKAI_ROBE, outline=OUTLINE)
        draw.polygon([(cx + 2, body_cy - 8), (cx + 10, body_cy - 2),
                      (cx + 14, base_y - 4), (cx + 2, base_y)],
                     fill=YOKAI_ROBE_DARK, outline=None)
        draw.rectangle([cx - 12, body_cy, cx + 10, body_cy + 3],
                       fill=YOKAI_ROBE_ACCENT, outline=OUTLINE)
        # Sleeve (front)
        draw.polygon([(cx - 14, body_cy - 6), (cx - 10, body_cy - 10),
                      (cx - 22, body_cy + 4), (cx - 18, body_cy + 8)],
                     fill=YOKAI_ROBE, outline=OUTLINE)
    else:  # RIGHT
        draw.polygon([(cx - 12, body_cy - 4), (cx - 6, body_cy - 12),
                      (cx + 6, body_cy - 12), (cx + 14, body_cy - 4),
                      (cx + 18, base_y), (cx - 16, base_y)],
                     fill=YOKAI_ROBE, outline=OUTLINE)
        draw.polygon([(cx - 10, body_cy - 2), (cx - 2, body_cy - 8),
                      (cx - 2, base_y), (cx - 14, base_y - 4)],
                     fill=YOKAI_ROBE_DARK, outline=None)
        draw.rectangle([cx - 10, body_cy, cx + 12, body_cy + 3],
                       fill=YOKAI_ROBE_ACCENT, outline=OUTLINE)
        # Sleeve (front)
        draw.polygon([(cx + 10, body_cy - 10), (cx + 14, body_cy - 6),
                      (cx + 18, body_cy + 8), (cx + 22, body_cy + 4)],
                     fill=YOKAI_ROBE, outline=OUTLINE)

    # --- Head (oni mask face) ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 12, 10, YOKAI_SKIN)
        ellipse(draw, cx - 2, head_cy - 2, 8, 6, YOKAI_SKIN_LIGHT, outline=None)
        # Angry eyes (wide, glowing)
        draw.rectangle([cx - 7, head_cy - 2, cx - 2, head_cy + 1], fill=YOKAI_EYE)
        draw.point((cx - 5, head_cy - 1), fill=BLACK)
        draw.rectangle([cx + 2, head_cy - 2, cx + 7, head_cy + 1], fill=YOKAI_EYE)
        draw.point((cx + 4, head_cy - 1), fill=BLACK)
        # Angry brows
        draw.line([(cx - 8, head_cy - 1), (cx - 2, head_cy - 3)],
                  fill=YOKAI_SKIN_DARK, width=2)
        draw.line([(cx + 2, head_cy - 3), (cx + 8, head_cy - 1)],
                  fill=YOKAI_SKIN_DARK, width=2)
        # Wide mouth with fangs
        draw.rectangle([cx - 6, head_cy + 4, cx + 6, head_cy + 8],
                       fill=YOKAI_SKIN_DARK, outline=OUTLINE)
        # Upper fangs
        draw.polygon([(cx - 4, head_cy + 4), (cx - 3, head_cy + 7),
                      (cx - 2, head_cy + 4)], fill=YOKAI_FANG, outline=None)
        draw.polygon([(cx + 2, head_cy + 4), (cx + 3, head_cy + 7),
                      (cx + 4, head_cy + 4)], fill=YOKAI_FANG, outline=None)
        # Lower fangs (sticking up from jaw)
        draw.polygon([(cx - 5, head_cy + 8), (cx - 4, head_cy + 5),
                      (cx - 3, head_cy + 8)], fill=YOKAI_FANG, outline=None)
        draw.polygon([(cx + 3, head_cy + 8), (cx + 4, head_cy + 5),
                      (cx + 5, head_cy + 8)], fill=YOKAI_FANG, outline=None)
        # Horns (two short)
        draw.polygon([(cx - 6, head_cy - 6), (cx - 4, head_cy - 16),
                      (cx - 2, head_cy - 6)], fill=YOKAI_HORN, outline=OUTLINE)
        draw.polygon([(cx + 2, head_cy - 6), (cx + 4, head_cy - 16),
                      (cx + 6, head_cy - 6)], fill=YOKAI_HORN, outline=OUTLINE)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 12, 10, YOKAI_SKIN)
        ellipse(draw, cx, head_cy, 9, 7, YOKAI_SKIN_DARK, outline=None)
        # Horns
        draw.polygon([(cx - 6, head_cy - 6), (cx - 4, head_cy - 16),
                      (cx - 2, head_cy - 6)], fill=YOKAI_HORN, outline=OUTLINE)
        draw.polygon([(cx + 2, head_cy - 6), (cx + 4, head_cy - 16),
                      (cx + 6, head_cy - 6)], fill=YOKAI_HORN, outline=OUTLINE)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 11, 10, YOKAI_SKIN)
        ellipse(draw, cx - 4, head_cy - 2, 7, 6, YOKAI_SKIN_LIGHT, outline=None)
        # Eye
        draw.rectangle([cx - 7, head_cy - 2, cx - 2, head_cy + 1], fill=YOKAI_EYE)
        draw.point((cx - 5, head_cy - 1), fill=BLACK)
        draw.line([(cx - 8, head_cy - 1), (cx - 2, head_cy - 3)],
                  fill=YOKAI_SKIN_DARK, width=2)
        # Mouth with fangs (side)
        draw.rectangle([cx - 10, head_cy + 4, cx - 2, head_cy + 7],
                       fill=YOKAI_SKIN_DARK, outline=OUTLINE)
        draw.polygon([(cx - 8, head_cy + 4), (cx - 7, head_cy + 6),
                      (cx - 6, head_cy + 4)], fill=YOKAI_FANG, outline=None)
        draw.polygon([(cx - 9, head_cy + 7), (cx - 8, head_cy + 5),
                      (cx - 7, head_cy + 7)], fill=YOKAI_FANG, outline=None)
        # Horn
        draw.polygon([(cx - 4, head_cy - 6), (cx - 2, head_cy - 16),
                      (cx + 1, head_cy - 6)], fill=YOKAI_HORN, outline=OUTLINE)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 11, 10, YOKAI_SKIN)
        ellipse(draw, cx + 4, head_cy - 2, 7, 6, YOKAI_SKIN_LIGHT, outline=None)
        # Eye
        draw.rectangle([cx + 2, head_cy - 2, cx + 7, head_cy + 1], fill=YOKAI_EYE)
        draw.point((cx + 4, head_cy - 1), fill=BLACK)
        draw.line([(cx + 2, head_cy - 3), (cx + 8, head_cy - 1)],
                  fill=YOKAI_SKIN_DARK, width=2)
        # Mouth with fangs (side)
        draw.rectangle([cx + 2, head_cy + 4, cx + 10, head_cy + 7],
                       fill=YOKAI_SKIN_DARK, outline=OUTLINE)
        draw.polygon([(cx + 6, head_cy + 4), (cx + 7, head_cy + 6),
                      (cx + 8, head_cy + 4)], fill=YOKAI_FANG, outline=None)
        draw.polygon([(cx + 7, head_cy + 7), (cx + 8, head_cy + 5),
                      (cx + 9, head_cy + 7)], fill=YOKAI_FANG, outline=None)
        # Horn
        draw.polygon([(cx - 1, head_cy - 6), (cx + 2, head_cy - 16),
                      (cx + 4, head_cy - 6)], fill=YOKAI_HORN, outline=OUTLINE)


# ===================================================================
# GOLEM (ID 98) — stone body, NO face, cracks, glowing rune dots
# ===================================================================

# Golem palette
GOLEM_BODY = (140, 130, 120)
GOLEM_LIGHT = (170, 160, 150)
GOLEM_DARK = (100, 90, 80)
GOLEM_CRACK = (70, 60, 55)
GOLEM_RUNE1 = (50, 220, 200)
GOLEM_RUNE2 = (80, 255, 180)
GOLEM_RUNE3 = (50, 200, 255)


def draw_golem(draw, ox, oy, direction, frame):
    bob = [0, -1, 0, -1][frame]
    sway = [-1, 0, 1, 0][frame]
    leg_spread = [-2, 0, 2, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32 + sway
    body_cy = base_y - 18
    head_cy = body_cy - 16

    body_shadow = _darken(GOLEM_BODY, 0.7)

    # Rune pulse per frame (cycle through brightness)
    rune_colors = [GOLEM_RUNE1, GOLEM_RUNE2, GOLEM_RUNE3]
    rune_active = rune_colors[frame % 3]

    # --- Legs (blocky, rectangular) ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            lx = cx + side * 8 + (leg_spread if side == -1 else -leg_spread)
            draw.rectangle([lx - 5, body_cy + 10, lx + 5, base_y],
                           fill=GOLEM_BODY, outline=OUTLINE)
            # Crack on leg
            draw.line([(lx - 2, body_cy + 14), (lx + 1, base_y - 4)],
                      fill=GOLEM_CRACK, width=1)
            draw.rectangle([lx - 5, base_y - 3, lx + 5, base_y],
                           fill=GOLEM_DARK, outline=None)
    elif direction == LEFT:
        for offset in [leg_spread, -leg_spread]:
            lx = cx - 2 + offset
            draw.rectangle([lx - 5, body_cy + 10, lx + 5, base_y],
                           fill=GOLEM_BODY, outline=OUTLINE)
            draw.line([(lx - 2, body_cy + 14), (lx + 1, base_y - 4)],
                      fill=GOLEM_CRACK, width=1)
            draw.rectangle([lx - 5, base_y - 3, lx + 5, base_y],
                           fill=GOLEM_DARK, outline=None)
    else:  # RIGHT
        for offset in [leg_spread, -leg_spread]:
            lx = cx + 2 + offset
            draw.rectangle([lx - 5, body_cy + 10, lx + 5, base_y],
                           fill=GOLEM_BODY, outline=OUTLINE)
            draw.line([(lx - 2, body_cy + 14), (lx + 1, base_y - 4)],
                      fill=GOLEM_CRACK, width=1)
            draw.rectangle([lx - 5, base_y - 3, lx + 5, base_y],
                           fill=GOLEM_DARK, outline=None)

    # --- Body (massive, blocky) ---
    if direction == DOWN:
        draw.rectangle([cx - 16, body_cy - 10, cx + 16, body_cy + 12],
                       fill=GOLEM_BODY, outline=OUTLINE)
        # Shading
        draw.rectangle([cx + 4, body_cy - 8, cx + 14, body_cy + 10],
                       fill=body_shadow, outline=None)
        draw.rectangle([cx - 14, body_cy - 8, cx - 4, body_cy - 2],
                       fill=GOLEM_LIGHT, outline=None)
        # Cracks on body
        draw.line([(cx - 8, body_cy - 6), (cx - 4, body_cy + 2), (cx - 6, body_cy + 8)],
                  fill=GOLEM_CRACK, width=1)
        draw.line([(cx + 6, body_cy - 4), (cx + 2, body_cy + 4)],
                  fill=GOLEM_CRACK, width=1)
        # Glowing rune inscriptions (3-4 dots that pulse per frame)
        draw.rectangle([cx - 3, body_cy - 4, cx + 3, body_cy - 2], fill=rune_active)
        draw.point((cx - 6, body_cy + 2), fill=rune_active)
        draw.point((cx + 6, body_cy + 2), fill=rune_active)
        draw.point((cx, body_cy + 6), fill=rune_colors[(frame + 1) % 3])
        # Blocky arms
        draw.rectangle([cx - 24, body_cy - 8, cx - 16, body_cy + 6],
                       fill=GOLEM_BODY, outline=OUTLINE)
        draw.line([(cx - 22, body_cy - 4), (cx - 19, body_cy + 2)],
                  fill=GOLEM_CRACK, width=1)
        draw.rectangle([cx - 24, body_cy + 4, cx - 16, body_cy + 10],
                       fill=GOLEM_DARK, outline=OUTLINE)
        draw.rectangle([cx + 16, body_cy - 8, cx + 24, body_cy + 6],
                       fill=GOLEM_BODY, outline=OUTLINE)
        draw.rectangle([cx + 16, body_cy + 4, cx + 24, body_cy + 10],
                       fill=GOLEM_DARK, outline=OUTLINE)
    elif direction == UP:
        draw.rectangle([cx - 16, body_cy - 10, cx + 16, body_cy + 12],
                       fill=GOLEM_BODY, outline=OUTLINE)
        draw.rectangle([cx - 12, body_cy - 8, cx + 12, body_cy + 8],
                       fill=GOLEM_DARK, outline=None)
        draw.line([(cx - 6, body_cy - 6), (cx - 2, body_cy + 4)],
                  fill=GOLEM_CRACK, width=1)
        draw.line([(cx + 4, body_cy - 2), (cx + 8, body_cy + 6)],
                  fill=GOLEM_CRACK, width=1)
        draw.rectangle([cx - 24, body_cy - 8, cx - 16, body_cy + 6],
                       fill=GOLEM_BODY, outline=OUTLINE)
        draw.rectangle([cx - 24, body_cy + 4, cx - 16, body_cy + 10],
                       fill=GOLEM_DARK, outline=OUTLINE)
        draw.rectangle([cx + 16, body_cy - 8, cx + 24, body_cy + 6],
                       fill=GOLEM_BODY, outline=OUTLINE)
        draw.rectangle([cx + 16, body_cy + 4, cx + 24, body_cy + 10],
                       fill=GOLEM_DARK, outline=OUTLINE)
    elif direction == LEFT:
        draw.rectangle([cx - 14, body_cy - 10, cx + 12, body_cy + 12],
                       fill=GOLEM_BODY, outline=OUTLINE)
        draw.rectangle([cx + 2, body_cy - 8, cx + 10, body_cy + 8],
                       fill=body_shadow, outline=None)
        draw.rectangle([cx - 12, body_cy - 8, cx - 4, body_cy - 2],
                       fill=GOLEM_LIGHT, outline=None)
        draw.line([(cx - 8, body_cy - 4), (cx - 4, body_cy + 4), (cx - 6, body_cy + 8)],
                  fill=GOLEM_CRACK, width=1)
        # Rune dots
        draw.rectangle([cx - 5, body_cy - 2, cx - 1, body_cy], fill=rune_active)
        draw.point((cx - 8, body_cy + 4), fill=rune_active)
        draw.point((cx + 2, body_cy + 4), fill=rune_colors[(frame + 1) % 3])
        # Arm
        draw.rectangle([cx - 20, body_cy - 6, cx - 14, body_cy + 6],
                       fill=GOLEM_BODY, outline=OUTLINE)
        draw.rectangle([cx - 20, body_cy + 4, cx - 14, body_cy + 10],
                       fill=GOLEM_DARK, outline=OUTLINE)
    else:  # RIGHT
        draw.rectangle([cx - 12, body_cy - 10, cx + 14, body_cy + 12],
                       fill=GOLEM_BODY, outline=OUTLINE)
        draw.rectangle([cx + 4, body_cy - 8, cx + 12, body_cy + 8],
                       fill=body_shadow, outline=None)
        draw.rectangle([cx - 10, body_cy - 8, cx - 2, body_cy - 2],
                       fill=GOLEM_LIGHT, outline=None)
        draw.line([(cx + 6, body_cy - 4), (cx + 2, body_cy + 4), (cx + 4, body_cy + 8)],
                  fill=GOLEM_CRACK, width=1)
        # Rune dots
        draw.rectangle([cx + 1, body_cy - 2, cx + 5, body_cy], fill=rune_active)
        draw.point((cx + 8, body_cy + 4), fill=rune_active)
        draw.point((cx - 2, body_cy + 4), fill=rune_colors[(frame + 1) % 3])
        # Arm
        draw.rectangle([cx + 14, body_cy - 6, cx + 20, body_cy + 6],
                       fill=GOLEM_BODY, outline=OUTLINE)
        draw.rectangle([cx + 14, body_cy + 4, cx + 20, body_cy + 10],
                       fill=GOLEM_DARK, outline=OUTLINE)

    # --- Head (blank, featureless — NO eyes, NO mouth) ---
    if direction == DOWN:
        draw.rectangle([cx - 8, head_cy - 8, cx + 8, head_cy + 6],
                       fill=GOLEM_BODY, outline=OUTLINE)
        draw.rectangle([cx - 6, head_cy - 6, cx - 2, head_cy],
                       fill=GOLEM_LIGHT, outline=None)
        # Crack across head
        draw.line([(cx - 4, head_cy - 6), (cx + 2, head_cy + 2)],
                  fill=GOLEM_CRACK, width=1)
    elif direction == UP:
        draw.rectangle([cx - 8, head_cy - 8, cx + 8, head_cy + 6],
                       fill=GOLEM_BODY, outline=OUTLINE)
        draw.rectangle([cx - 6, head_cy - 6, cx + 6, head_cy + 2],
                       fill=GOLEM_DARK, outline=None)
        draw.line([(cx + 2, head_cy - 4), (cx - 2, head_cy + 2)],
                  fill=GOLEM_CRACK, width=1)
    elif direction == LEFT:
        draw.rectangle([cx - 10, head_cy - 8, cx + 6, head_cy + 6],
                       fill=GOLEM_BODY, outline=OUTLINE)
        draw.rectangle([cx - 8, head_cy - 6, cx - 4, head_cy],
                       fill=GOLEM_LIGHT, outline=None)
        draw.line([(cx - 6, head_cy - 4), (cx - 2, head_cy + 2)],
                  fill=GOLEM_CRACK, width=1)
    else:  # RIGHT
        draw.rectangle([cx - 6, head_cy - 8, cx + 10, head_cy + 6],
                       fill=GOLEM_BODY, outline=OUTLINE)
        draw.rectangle([cx + 4, head_cy - 6, cx + 8, head_cy],
                       fill=GOLEM_LIGHT, outline=None)
        draw.line([(cx + 6, head_cy - 4), (cx + 2, head_cy + 2)],
                  fill=GOLEM_CRACK, width=1)


# ===================================================================
# DJINN (ID 99) — floating, smoke trail, turban, crossed arms, lamp glow
# ===================================================================

# Djinn palette
DJINN_SKIN = (50, 80, 150)
DJINN_SKIN_LIGHT = (80, 110, 180)
DJINN_SKIN_DARK = (30, 55, 110)
DJINN_TURBAN = (200, 170, 50)
DJINN_TURBAN_DARK = (160, 130, 30)
DJINN_GOLD = (200, 170, 50)
DJINN_SMOKE1 = (120, 100, 160, 120)
DJINN_SMOKE2 = (100, 80, 140, 80)
DJINN_SMOKE3 = (80, 60, 120, 50)
DJINN_EYE = (255, 220, 100)
DJINN_LAMP_GLOW = (255, 200, 60, 140)


def draw_djinn(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    sway = [-1, 0, 1, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32 + sway
    body_cy = base_y - 22
    head_cy = body_cy - 18

    body_shadow = _darken(DJINN_SKIN, 0.7)

    # --- Smoke/vapor trail instead of legs (wispy, semi-transparent) ---
    smoke_colors = [DJINN_SMOKE1, DJINN_SMOKE2, DJINN_SMOKE3]
    # Multiple wispy ellipses tapering down
    for i in range(5):
        sy = body_cy + 14 + i * 5
        sx = cx + (frame + i) % 5 - 2 + sway
        size = max(2, 10 - i * 2)
        sc = smoke_colors[i % 3]
        ellipse(draw, sx, sy, size, 3, sc, outline=None)
    # Wispy tail tip
    ellipse(draw, cx + sway * 2, base_y - 2, 4, 3, DJINN_SMOKE3, outline=None)
    ellipse(draw, cx - sway, base_y + 2, 3, 2, DJINN_SMOKE2, outline=None)

    # --- Lamp glow particles (golden dots floating nearby) ---
    glow_offsets = [
        (-16, -6 - frame * 2), (18, -10 + frame), (-14, 4 + frame),
        (16, 6 - frame * 2), (-10, -14 + frame), (12, -2 - frame),
    ]
    for i, (gdx, gdy) in enumerate(glow_offsets):
        gy = body_cy + gdy + (i * 3 + frame * 2) % 8 - 4
        gx = cx + gdx + (i + frame) % 3 - 1
        ellipse(draw, gx, gy, 2, 2, DJINN_LAMP_GLOW, outline=None)

    # --- Body (muscular bare torso, crossed arms pose) ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy, 14, 14, DJINN_SKIN)
        ellipse(draw, cx + 3, body_cy + 3, 10, 10, body_shadow, outline=None)
        ellipse(draw, cx, body_cy, 12, 12, DJINN_SKIN, outline=None)
        ellipse(draw, cx - 2, body_cy - 3, 7, 6, DJINN_SKIN_LIGHT, outline=None)
        # Gold necklace
        draw.arc([cx - 8, body_cy - 8, cx + 8, body_cy],
                 start=0, end=180, fill=DJINN_GOLD, width=2)
        # Crossed arms
        draw.polygon([(cx - 14, body_cy - 4), (cx - 8, body_cy - 8),
                      (cx + 4, body_cy + 2), (cx - 2, body_cy + 6)],
                     fill=DJINN_SKIN, outline=OUTLINE)
        draw.polygon([(cx + 8, body_cy - 8), (cx + 14, body_cy - 4),
                      (cx + 2, body_cy + 6), (cx - 4, body_cy + 2)],
                     fill=DJINN_SKIN, outline=OUTLINE)
        # Gold wristbands
        ellipse(draw, cx - 10, body_cy, 3, 2, DJINN_GOLD)
        ellipse(draw, cx + 10, body_cy, 3, 2, DJINN_GOLD)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 14, 14, DJINN_SKIN)
        ellipse(draw, cx, body_cy, 12, 12, DJINN_SKIN_DARK, outline=None)
        # Crossed arms from behind
        draw.polygon([(cx - 14, body_cy - 4), (cx - 8, body_cy - 8),
                      (cx + 6, body_cy + 2), (cx, body_cy + 6)],
                     fill=DJINN_SKIN, outline=OUTLINE)
        draw.polygon([(cx + 8, body_cy - 8), (cx + 14, body_cy - 4),
                      (cx, body_cy + 6), (cx - 6, body_cy + 2)],
                     fill=DJINN_SKIN, outline=OUTLINE)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy, 12, 14, DJINN_SKIN)
        ellipse(draw, cx, body_cy + 3, 9, 10, body_shadow, outline=None)
        ellipse(draw, cx - 2, body_cy, 10, 12, DJINN_SKIN, outline=None)
        ellipse(draw, cx - 4, body_cy - 3, 6, 6, DJINN_SKIN_LIGHT, outline=None)
        # One arm crossing in front
        draw.polygon([(cx - 12, body_cy - 4), (cx - 8, body_cy - 8),
                      (cx + 4, body_cy + 2), (cx, body_cy + 6)],
                     fill=DJINN_SKIN, outline=OUTLINE)
        ellipse(draw, cx - 8, body_cy, 3, 2, DJINN_GOLD)
    else:  # RIGHT
        ellipse(draw, cx + 2, body_cy, 12, 14, DJINN_SKIN)
        ellipse(draw, cx + 4, body_cy + 3, 9, 10, body_shadow, outline=None)
        ellipse(draw, cx + 2, body_cy, 10, 12, DJINN_SKIN, outline=None)
        ellipse(draw, cx, body_cy - 3, 6, 6, DJINN_SKIN_LIGHT, outline=None)
        # One arm crossing in front
        draw.polygon([(cx + 8, body_cy - 8), (cx + 12, body_cy - 4),
                      (cx, body_cy + 6), (cx - 4, body_cy + 2)],
                     fill=DJINN_SKIN, outline=OUTLINE)
        ellipse(draw, cx + 8, body_cy, 3, 2, DJINN_GOLD)

    # --- Head (with turban) ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 10, 10, DJINN_SKIN)
        ellipse(draw, cx - 2, head_cy - 2, 7, 6, DJINN_SKIN_LIGHT, outline=None)
        # Eyes (glowing gold)
        draw.rectangle([cx - 5, head_cy, cx - 2, head_cy + 3], fill=DJINN_EYE)
        draw.point((cx - 4, head_cy + 1), fill=BLACK)
        draw.rectangle([cx + 2, head_cy, cx + 5, head_cy + 3], fill=DJINN_EYE)
        draw.point((cx + 3, head_cy + 1), fill=BLACK)
        # Goatee
        draw.polygon([(cx - 2, head_cy + 6), (cx, head_cy + 10),
                      (cx + 2, head_cy + 6)], fill=DJINN_SKIN_DARK, outline=None)
        # Turban (wrapped cloth, wider than head)
        draw.polygon([(cx - 12, head_cy - 4), (cx - 8, head_cy - 14),
                      (cx + 8, head_cy - 14), (cx + 12, head_cy - 4),
                      (cx + 10, head_cy - 2), (cx - 10, head_cy - 2)],
                     fill=DJINN_TURBAN, outline=OUTLINE)
        # Turban folds
        draw.line([(cx - 6, head_cy - 12), (cx - 4, head_cy - 4)],
                  fill=DJINN_TURBAN_DARK, width=1)
        draw.line([(cx + 2, head_cy - 12), (cx + 4, head_cy - 4)],
                  fill=DJINN_TURBAN_DARK, width=1)
        # Gem on turban
        ellipse(draw, cx, head_cy - 6, 3, 3, (200, 50, 50))
    elif direction == UP:
        ellipse(draw, cx, head_cy, 10, 10, DJINN_SKIN)
        ellipse(draw, cx, head_cy, 7, 7, DJINN_SKIN_DARK, outline=None)
        # Turban from behind
        draw.polygon([(cx - 12, head_cy - 4), (cx - 8, head_cy - 14),
                      (cx + 8, head_cy - 14), (cx + 12, head_cy - 4),
                      (cx + 10, head_cy - 2), (cx - 10, head_cy - 2)],
                     fill=DJINN_TURBAN, outline=OUTLINE)
        draw.line([(cx - 4, head_cy - 12), (cx - 2, head_cy - 4)],
                  fill=DJINN_TURBAN_DARK, width=1)
        draw.line([(cx + 4, head_cy - 12), (cx + 2, head_cy - 4)],
                  fill=DJINN_TURBAN_DARK, width=1)
        # Trailing cloth from turban
        draw.polygon([(cx - 4, head_cy - 2), (cx + 4, head_cy - 2),
                      (cx + 2, head_cy + 8), (cx - 2, head_cy + 8)],
                     fill=DJINN_TURBAN, outline=OUTLINE)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 9, 10, DJINN_SKIN)
        ellipse(draw, cx - 4, head_cy - 2, 6, 6, DJINN_SKIN_LIGHT, outline=None)
        # Eye
        draw.rectangle([cx - 6, head_cy, cx - 3, head_cy + 3], fill=DJINN_EYE)
        draw.point((cx - 5, head_cy + 1), fill=BLACK)
        # Goatee
        draw.polygon([(cx - 4, head_cy + 6), (cx - 3, head_cy + 9),
                      (cx - 2, head_cy + 6)], fill=DJINN_SKIN_DARK, outline=None)
        # Turban (side view)
        draw.polygon([(cx - 10, head_cy - 4), (cx - 6, head_cy - 14),
                      (cx + 6, head_cy - 14), (cx + 8, head_cy - 4),
                      (cx + 6, head_cy - 2), (cx - 8, head_cy - 2)],
                     fill=DJINN_TURBAN, outline=OUTLINE)
        draw.line([(cx - 2, head_cy - 12), (cx, head_cy - 4)],
                  fill=DJINN_TURBAN_DARK, width=1)
        ellipse(draw, cx - 2, head_cy - 6, 3, 3, (200, 50, 50))
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 9, 10, DJINN_SKIN)
        ellipse(draw, cx + 4, head_cy - 2, 6, 6, DJINN_SKIN_LIGHT, outline=None)
        # Eye
        draw.rectangle([cx + 3, head_cy, cx + 6, head_cy + 3], fill=DJINN_EYE)
        draw.point((cx + 4, head_cy + 1), fill=BLACK)
        # Goatee
        draw.polygon([(cx + 2, head_cy + 6), (cx + 3, head_cy + 9),
                      (cx + 4, head_cy + 6)], fill=DJINN_SKIN_DARK, outline=None)
        # Turban (side view)
        draw.polygon([(cx - 8, head_cy - 4), (cx - 6, head_cy - 14),
                      (cx + 6, head_cy - 14), (cx + 10, head_cy - 4),
                      (cx + 8, head_cy - 2), (cx - 6, head_cy - 2)],
                     fill=DJINN_TURBAN, outline=OUTLINE)
        draw.line([(cx, head_cy - 12), (cx + 2, head_cy - 4)],
                  fill=DJINN_TURBAN_DARK, width=1)
        ellipse(draw, cx + 2, head_cy - 6, 3, 3, (200, 50, 50))


# ===================================================================
# FENRIR (ID 100) — giant wolf, broken chains, ice crystals, red eyes
# ===================================================================

# Fenrir palette
FENRIR_BODY = (80, 80, 90)
FENRIR_LIGHT = (110, 110, 120)
FENRIR_DARK = (50, 50, 60)
FENRIR_BELLY = (100, 100, 110)
FENRIR_EYE = (200, 50, 50)
FENRIR_CHAIN = (150, 150, 160)
FENRIR_CHAIN_DARK = (110, 110, 120)
FENRIR_ICE = (180, 220, 255)
FENRIR_FANG = (220, 220, 220)
FENRIR_MOUTH = (120, 40, 40)


def draw_fenrir(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_phase = [-3, 0, 3, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    # Hunched, larger body
    body_cy = base_y - 14
    head_cy = body_cy - 14

    body_shadow = _darken(FENRIR_BODY, 0.7)

    # --- Legs (4, quadruped, hunched aggressive stance) ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            # Front legs (splayed wider)
            fx = cx + side * 10 + (leg_phase if side == -1 else -leg_phase)
            draw.rectangle([fx - 4, body_cy + 4, fx + 4, base_y - 2],
                           fill=FENRIR_BODY, outline=OUTLINE)
            if direction == DOWN:
                draw.rectangle([fx - 4, body_cy + 4, fx - 2, base_y - 6],
                               fill=FENRIR_LIGHT, outline=None)
            # Paw with claws
            ellipse(draw, fx, base_y, 5, 3, FENRIR_DARK)
            for c in [-2, 0, 2]:
                draw.point((fx + c, base_y + 2), fill=FENRIR_DARK)
    elif direction == LEFT:
        for offset in [leg_phase, -leg_phase]:
            lx = cx - 2 + offset
            draw.rectangle([lx - 4, body_cy + 4, lx + 4, base_y - 2],
                           fill=FENRIR_BODY, outline=OUTLINE)
            ellipse(draw, lx, base_y, 5, 3, FENRIR_DARK)
    else:  # RIGHT
        for offset in [leg_phase, -leg_phase]:
            lx = cx + 2 + offset
            draw.rectangle([lx - 4, body_cy + 4, lx + 4, base_y - 2],
                           fill=FENRIR_BODY, outline=OUTLINE)
            ellipse(draw, lx, base_y, 5, 3, FENRIR_DARK)

    # --- Body (large, hunched) ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy, 18, 12, FENRIR_BODY)
        ellipse(draw, cx + 4, body_cy + 2, 14, 9, body_shadow, outline=None)
        ellipse(draw, cx, body_cy, 15, 10, FENRIR_BODY, outline=None)
        ellipse(draw, cx - 3, body_cy - 2, 10, 6, FENRIR_LIGHT, outline=None)
        ellipse(draw, cx, body_cy + 4, 10, 5, FENRIR_BELLY, outline=None)
        draw_fur_texture(draw, cx, body_cy + 4, 22, 8, FENRIR_DARK, density=4)
        # Hunched back ridge
        draw.arc([cx - 12, body_cy - 10, cx + 12, body_cy - 2],
                 start=180, end=360, fill=FENRIR_LIGHT, width=2)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 18, 12, FENRIR_BODY)
        ellipse(draw, cx, body_cy, 15, 10, FENRIR_DARK, outline=None)
        draw_fur_texture(draw, cx, body_cy + 4, 22, 8, FENRIR_DARK, density=4)
        draw.arc([cx - 12, body_cy - 10, cx + 12, body_cy - 2],
                 start=180, end=360, fill=FENRIR_LIGHT, width=2)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy, 18, 12, FENRIR_BODY)
        ellipse(draw, cx + 2, body_cy + 2, 14, 9, body_shadow, outline=None)
        ellipse(draw, cx - 2, body_cy, 15, 10, FENRIR_BODY, outline=None)
        ellipse(draw, cx - 5, body_cy - 2, 10, 6, FENRIR_LIGHT, outline=None)
        draw_fur_texture(draw, cx - 2, body_cy + 4, 20, 8, FENRIR_DARK, density=4)
        # Tail (bushy, pointing right/back)
        draw.polygon([(cx + 14, body_cy - 2), (cx + 24, body_cy - 8 + bob),
                      (cx + 22, body_cy - 4 + bob), (cx + 16, body_cy + 2)],
                     fill=FENRIR_BODY, outline=OUTLINE)
        draw_fur_texture(draw, cx + 20, body_cy - 6 + bob, 6, 4, FENRIR_DARK, density=2)
    else:  # RIGHT
        ellipse(draw, cx + 2, body_cy, 18, 12, FENRIR_BODY)
        ellipse(draw, cx + 6, body_cy + 2, 14, 9, body_shadow, outline=None)
        ellipse(draw, cx + 2, body_cy, 15, 10, FENRIR_BODY, outline=None)
        ellipse(draw, cx - 1, body_cy - 2, 10, 6, FENRIR_LIGHT, outline=None)
        draw_fur_texture(draw, cx + 2, body_cy + 4, 20, 8, FENRIR_DARK, density=4)
        # Tail
        draw.polygon([(cx - 14, body_cy - 2), (cx - 24, body_cy - 8 + bob),
                      (cx - 22, body_cy - 4 + bob), (cx - 16, body_cy + 2)],
                     fill=FENRIR_BODY, outline=OUTLINE)
        draw_fur_texture(draw, cx - 20, body_cy - 6 + bob, 6, 4, FENRIR_DARK, density=2)

    # --- Broken chain links dangling from neck ---
    if direction == DOWN:
        for i, cdx in enumerate([-8, 0, 8]):
            cy = body_cy - 8 + (i + frame) % 3
            draw.rectangle([cx + cdx - 2, cy, cx + cdx + 2, cy + 4],
                           fill=FENRIR_CHAIN, outline=FENRIR_CHAIN_DARK)
    elif direction == LEFT:
        for i in range(3):
            cy = body_cy - 6 + i * 3 + (frame + i) % 2
            draw.rectangle([cx - 8 - i * 2, cy, cx - 4 - i * 2, cy + 4],
                           fill=FENRIR_CHAIN, outline=FENRIR_CHAIN_DARK)
    elif direction == RIGHT:
        for i in range(3):
            cy = body_cy - 6 + i * 3 + (frame + i) % 2
            draw.rectangle([cx + 4 + i * 2, cy, cx + 8 + i * 2, cy + 4],
                           fill=FENRIR_CHAIN, outline=FENRIR_CHAIN_DARK)

    # --- Ice crystals in fur (blue-white dots scattered on body) ---
    ice_positions = [(-10, -4), (8, -2), (-6, 4), (12, 2), (-2, -6), (6, 6)]
    for i, (idx, idy) in enumerate(ice_positions):
        if (frame + i) % 3 != 0:  # Some blink per frame
            draw.point((cx + idx, body_cy + idy), fill=FENRIR_ICE)

    # --- Head (large wolf head, snarling, glowing red eyes) ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 14, 12, FENRIR_BODY)
        ellipse(draw, cx - 2, head_cy - 2, 10, 8, FENRIR_LIGHT, outline=None)
        # Snarling muzzle
        ellipse(draw, cx, head_cy + 6, 10, 6, FENRIR_BODY)
        # Open mouth with teeth
        draw.rectangle([cx - 6, head_cy + 6, cx + 6, head_cy + 10],
                       fill=FENRIR_MOUTH, outline=OUTLINE)
        # Fangs
        draw.polygon([(cx - 4, head_cy + 6), (cx - 3, head_cy + 9),
                      (cx - 2, head_cy + 6)], fill=FENRIR_FANG, outline=None)
        draw.polygon([(cx + 2, head_cy + 6), (cx + 3, head_cy + 9),
                      (cx + 4, head_cy + 6)], fill=FENRIR_FANG, outline=None)
        # Lower fangs
        draw.polygon([(cx - 3, head_cy + 10), (cx - 2, head_cy + 7),
                      (cx - 1, head_cy + 10)], fill=FENRIR_FANG, outline=None)
        draw.polygon([(cx + 1, head_cy + 10), (cx + 2, head_cy + 7),
                      (cx + 3, head_cy + 10)], fill=FENRIR_FANG, outline=None)
        # Glowing red eyes
        draw.rectangle([cx - 8, head_cy - 2, cx - 3, head_cy + 2], fill=FENRIR_EYE)
        draw.point((cx - 6, head_cy), fill=(255, 200, 200))
        draw.rectangle([cx + 3, head_cy - 2, cx + 8, head_cy + 2], fill=FENRIR_EYE)
        draw.point((cx + 5, head_cy), fill=(255, 200, 200))
        # Nose
        draw.point((cx, head_cy + 5), fill=BLACK)
        # Ears (pointed, wolf-like)
        draw.polygon([(cx - 10, head_cy - 6), (cx - 8, head_cy - 16),
                      (cx - 4, head_cy - 6)], fill=FENRIR_BODY, outline=OUTLINE)
        draw.polygon([(cx - 9, head_cy - 8), (cx - 8, head_cy - 12),
                      (cx - 5, head_cy - 8)], fill=FENRIR_LIGHT, outline=None)
        draw.polygon([(cx + 4, head_cy - 6), (cx + 8, head_cy - 16),
                      (cx + 10, head_cy - 6)], fill=FENRIR_BODY, outline=OUTLINE)
        draw.polygon([(cx + 5, head_cy - 8), (cx + 8, head_cy - 12),
                      (cx + 9, head_cy - 8)], fill=FENRIR_LIGHT, outline=None)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 14, 12, FENRIR_BODY)
        ellipse(draw, cx, head_cy, 11, 9, FENRIR_DARK, outline=None)
        draw_fur_texture(draw, cx, head_cy, 16, 10, FENRIR_DARK, density=3)
        # Ears
        draw.polygon([(cx - 10, head_cy - 6), (cx - 8, head_cy - 16),
                      (cx - 4, head_cy - 6)], fill=FENRIR_BODY, outline=OUTLINE)
        draw.polygon([(cx + 4, head_cy - 6), (cx + 8, head_cy - 16),
                      (cx + 10, head_cy - 6)], fill=FENRIR_BODY, outline=OUTLINE)
    elif direction == LEFT:
        ellipse(draw, cx - 4, head_cy, 14, 12, FENRIR_BODY)
        ellipse(draw, cx - 6, head_cy - 2, 10, 8, FENRIR_LIGHT, outline=None)
        # Snarling muzzle (pointing left)
        draw.polygon([(cx - 10, head_cy + 2), (cx - 20, head_cy + 5),
                      (cx - 10, head_cy + 8)], fill=FENRIR_BODY, outline=OUTLINE)
        # Mouth
        draw.rectangle([cx - 18, head_cy + 4, cx - 10, head_cy + 8],
                       fill=FENRIR_MOUTH, outline=OUTLINE)
        # Fangs
        draw.polygon([(cx - 16, head_cy + 4), (cx - 15, head_cy + 7),
                      (cx - 14, head_cy + 4)], fill=FENRIR_FANG, outline=None)
        draw.polygon([(cx - 15, head_cy + 8), (cx - 14, head_cy + 5),
                      (cx - 13, head_cy + 8)], fill=FENRIR_FANG, outline=None)
        draw.point((cx - 18, head_cy + 3), fill=BLACK)
        # Eye
        draw.rectangle([cx - 10, head_cy - 2, cx - 5, head_cy + 2], fill=FENRIR_EYE)
        draw.point((cx - 8, head_cy), fill=(255, 200, 200))
        # Ear
        draw.polygon([(cx - 6, head_cy - 8), (cx - 4, head_cy - 16),
                      (cx, head_cy - 6)], fill=FENRIR_BODY, outline=OUTLINE)
        draw.polygon([(cx - 5, head_cy - 10), (cx - 4, head_cy - 14),
                      (cx - 1, head_cy - 8)], fill=FENRIR_LIGHT, outline=None)
    else:  # RIGHT
        ellipse(draw, cx + 4, head_cy, 14, 12, FENRIR_BODY)
        ellipse(draw, cx + 6, head_cy - 2, 10, 8, FENRIR_LIGHT, outline=None)
        # Snarling muzzle (pointing right)
        draw.polygon([(cx + 10, head_cy + 2), (cx + 20, head_cy + 5),
                      (cx + 10, head_cy + 8)], fill=FENRIR_BODY, outline=OUTLINE)
        # Mouth
        draw.rectangle([cx + 10, head_cy + 4, cx + 18, head_cy + 8],
                       fill=FENRIR_MOUTH, outline=OUTLINE)
        # Fangs
        draw.polygon([(cx + 14, head_cy + 4), (cx + 15, head_cy + 7),
                      (cx + 16, head_cy + 4)], fill=FENRIR_FANG, outline=None)
        draw.polygon([(cx + 13, head_cy + 8), (cx + 14, head_cy + 5),
                      (cx + 15, head_cy + 8)], fill=FENRIR_FANG, outline=None)
        draw.point((cx + 18, head_cy + 3), fill=BLACK)
        # Eye
        draw.rectangle([cx + 5, head_cy - 2, cx + 10, head_cy + 2], fill=FENRIR_EYE)
        draw.point((cx + 7, head_cy), fill=(255, 200, 200))
        # Ear
        draw.polygon([(cx, head_cy - 6), (cx + 4, head_cy - 16),
                      (cx + 6, head_cy - 8)], fill=FENRIR_BODY, outline=OUTLINE)
        draw.polygon([(cx + 1, head_cy - 8), (cx + 4, head_cy - 14),
                      (cx + 5, head_cy - 10)], fill=FENRIR_LIGHT, outline=None)


# ===================================================================
# CHIMERA (ID 101) — lion body, goat head on back, snake tail, bat wings
# ===================================================================

# Chimera palette
CHIMERA_LION = (150, 80, 40)
CHIMERA_LION_LIGHT = (180, 110, 60)
CHIMERA_LION_DARK = (110, 55, 25)
CHIMERA_LION_BELLY = (180, 140, 90)
CHIMERA_GOAT = (130, 130, 130)
CHIMERA_GOAT_DARK = (90, 90, 95)
CHIMERA_GOAT_HORN = (180, 170, 150)
CHIMERA_SNAKE = (60, 100, 50)
CHIMERA_SNAKE_DARK = (40, 70, 30)
CHIMERA_SNAKE_EYE = (200, 200, 50)
CHIMERA_WING = (100, 60, 80)
CHIMERA_WING_DARK = (70, 40, 55)
CHIMERA_EYE = (200, 120, 40)
CHIMERA_MANE = (120, 60, 30)


def draw_chimera(draw, ox, oy, direction, frame):
    bob = [0, -1, 0, -1][frame]
    leg_phase = [-2, 0, 2, 0][frame]
    wing_flap = [-3, 1, 3, -1][frame]
    tail_sway = [-2, 0, 2, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 12
    head_cy = body_cy - 16

    body_shadow = _darken(CHIMERA_LION, 0.7)

    # --- Legs (4, lion quadruped) ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            fx = cx + side * 8 + (leg_phase if side == -1 else -leg_phase)
            draw.rectangle([fx - 3, body_cy + 4, fx + 3, base_y - 2],
                           fill=CHIMERA_LION, outline=OUTLINE)
            if direction == DOWN:
                draw.rectangle([fx - 3, body_cy + 4, fx - 1, base_y - 6],
                               fill=CHIMERA_LION_LIGHT, outline=None)
            ellipse(draw, fx, base_y, 4, 3, CHIMERA_LION_DARK)
    elif direction == LEFT:
        for offset in [leg_phase, -leg_phase]:
            lx = cx - 2 + offset
            draw.rectangle([lx - 3, body_cy + 4, lx + 3, base_y - 2],
                           fill=CHIMERA_LION, outline=OUTLINE)
            ellipse(draw, lx, base_y, 4, 3, CHIMERA_LION_DARK)
    else:  # RIGHT
        for offset in [leg_phase, -leg_phase]:
            lx = cx + 2 + offset
            draw.rectangle([lx - 3, body_cy + 4, lx + 3, base_y - 2],
                           fill=CHIMERA_LION, outline=OUTLINE)
            ellipse(draw, lx, base_y, 4, 3, CHIMERA_LION_DARK)

    # --- Snake tail (ends in a small snake head) ---
    if direction == DOWN:
        # Tail behind, visible as a raised curve
        tx = cx + tail_sway
        draw.line([(tx, body_cy + 2), (tx + 6, body_cy - 4),
                   (tx + 10, body_cy - 8)], fill=CHIMERA_SNAKE, width=3)
        draw_scale_texture(draw, tx + 8, body_cy - 6, 6, 4, CHIMERA_SNAKE_DARK)
        # Snake head at tip
        ellipse(draw, tx + 12, body_cy - 10, 4, 3, CHIMERA_SNAKE)
        draw.point((tx + 14, body_cy - 11), fill=CHIMERA_SNAKE_EYE)
        # Forked tongue
        draw.line([(tx + 15, body_cy - 10), (tx + 18, body_cy - 11)],
                  fill=(200, 50, 50), width=1)
        draw.line([(tx + 15, body_cy - 10), (tx + 18, body_cy - 9)],
                  fill=(200, 50, 50), width=1)
    elif direction == UP:
        tx = cx - tail_sway
        draw.line([(tx, body_cy + 2), (tx - 6, body_cy - 4),
                   (tx - 10, body_cy - 8)], fill=CHIMERA_SNAKE, width=3)
        draw_scale_texture(draw, tx - 8, body_cy - 6, 6, 4, CHIMERA_SNAKE_DARK)
        ellipse(draw, tx - 12, body_cy - 10, 4, 3, CHIMERA_SNAKE)
        draw.point((tx - 14, body_cy - 11), fill=CHIMERA_SNAKE_EYE)
        draw.line([(tx - 15, body_cy - 10), (tx - 18, body_cy - 11)],
                  fill=(200, 50, 50), width=1)
    elif direction == LEFT:
        # Tail trailing behind (right side)
        tx = cx + 14
        draw.line([(cx + 10, body_cy), (tx, body_cy - 4 + tail_sway),
                   (tx + 6, body_cy - 8 + tail_sway)], fill=CHIMERA_SNAKE, width=3)
        draw_scale_texture(draw, tx + 4, body_cy - 6 + tail_sway, 6, 4, CHIMERA_SNAKE_DARK)
        ellipse(draw, tx + 8, body_cy - 10 + tail_sway, 4, 3, CHIMERA_SNAKE)
        draw.point((tx + 10, body_cy - 11 + tail_sway), fill=CHIMERA_SNAKE_EYE)
        draw.line([(tx + 11, body_cy - 10 + tail_sway), (tx + 14, body_cy - 11 + tail_sway)],
                  fill=(200, 50, 50), width=1)
    else:  # RIGHT
        tx = cx - 14
        draw.line([(cx - 10, body_cy), (tx, body_cy - 4 + tail_sway),
                   (tx - 6, body_cy - 8 + tail_sway)], fill=CHIMERA_SNAKE, width=3)
        draw_scale_texture(draw, tx - 4, body_cy - 6 + tail_sway, 6, 4, CHIMERA_SNAKE_DARK)
        ellipse(draw, tx - 8, body_cy - 10 + tail_sway, 4, 3, CHIMERA_SNAKE)
        draw.point((tx - 10, body_cy - 11 + tail_sway), fill=CHIMERA_SNAKE_EYE)
        draw.line([(tx - 11, body_cy - 10 + tail_sway), (tx - 14, body_cy - 11 + tail_sway)],
                  fill=(200, 50, 50), width=1)

    # --- Bat-like wings ---
    if direction == DOWN:
        for side in [-1, 1]:
            wx = cx + side * 12
            draw.polygon([
                (wx, body_cy - 6),
                (wx + side * 10, body_cy - 14 + wing_flap),
                (wx + side * 8, body_cy - 6 + wing_flap),
                (wx + side * 12, body_cy - 2 + wing_flap),
                (wx + side * 4, body_cy + 2),
            ], fill=CHIMERA_WING, outline=OUTLINE)
            # Wing membrane lines
            draw.line([(wx + side * 2, body_cy - 4),
                       (wx + side * 8, body_cy - 10 + wing_flap)],
                      fill=CHIMERA_WING_DARK, width=1)
            draw.line([(wx + side * 2, body_cy),
                       (wx + side * 10, body_cy - 4 + wing_flap)],
                      fill=CHIMERA_WING_DARK, width=1)
    elif direction == UP:
        for side in [-1, 1]:
            wx = cx + side * 10
            draw.polygon([
                (wx, body_cy - 8),
                (wx + side * 12, body_cy - 16 + wing_flap),
                (wx + side * 14, body_cy - 6 + wing_flap),
                (wx + side * 10, body_cy + 4 + wing_flap),
                (wx + side * 2, body_cy + 2),
            ], fill=CHIMERA_WING, outline=OUTLINE)
            draw.line([(wx + side * 4, body_cy - 6),
                       (wx + side * 10, body_cy - 12 + wing_flap)],
                      fill=CHIMERA_WING_DARK, width=1)
    elif direction == LEFT:
        draw.polygon([
            (cx - 6, body_cy - 6),
            (cx - 18, body_cy - 14 + wing_flap),
            (cx - 20, body_cy - 6 + wing_flap),
            (cx - 14, body_cy + 4 + wing_flap),
            (cx - 8, body_cy + 2),
        ], fill=CHIMERA_WING, outline=OUTLINE)
        draw.line([(cx - 10, body_cy - 8), (cx - 16, body_cy - 2 + wing_flap)],
                  fill=CHIMERA_WING_DARK, width=1)
    else:  # RIGHT
        draw.polygon([
            (cx + 6, body_cy - 6),
            (cx + 18, body_cy - 14 + wing_flap),
            (cx + 20, body_cy - 6 + wing_flap),
            (cx + 14, body_cy + 4 + wing_flap),
            (cx + 8, body_cy + 2),
        ], fill=CHIMERA_WING, outline=OUTLINE)
        draw.line([(cx + 10, body_cy - 8), (cx + 16, body_cy - 2 + wing_flap)],
                  fill=CHIMERA_WING_DARK, width=1)

    # --- Lion body ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy, 16, 10, CHIMERA_LION)
        ellipse(draw, cx + 3, body_cy + 2, 12, 7, body_shadow, outline=None)
        ellipse(draw, cx, body_cy, 13, 8, CHIMERA_LION, outline=None)
        ellipse(draw, cx - 2, body_cy - 2, 8, 5, CHIMERA_LION_LIGHT, outline=None)
        ellipse(draw, cx, body_cy + 3, 8, 4, CHIMERA_LION_BELLY, outline=None)
        draw_fur_texture(draw, cx, body_cy + 4, 16, 6, CHIMERA_LION_DARK, density=2)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 16, 10, CHIMERA_LION)
        ellipse(draw, cx, body_cy, 13, 8, CHIMERA_LION_DARK, outline=None)
        draw_fur_texture(draw, cx, body_cy + 4, 16, 6, CHIMERA_LION_DARK, density=2)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy, 16, 10, CHIMERA_LION)
        ellipse(draw, cx + 2, body_cy + 2, 12, 7, body_shadow, outline=None)
        ellipse(draw, cx - 2, body_cy, 13, 8, CHIMERA_LION, outline=None)
        ellipse(draw, cx - 4, body_cy - 2, 8, 5, CHIMERA_LION_LIGHT, outline=None)
    else:  # RIGHT
        ellipse(draw, cx + 2, body_cy, 16, 10, CHIMERA_LION)
        ellipse(draw, cx + 6, body_cy + 2, 12, 7, body_shadow, outline=None)
        ellipse(draw, cx + 2, body_cy, 13, 8, CHIMERA_LION, outline=None)
        ellipse(draw, cx, body_cy - 2, 8, 5, CHIMERA_LION_LIGHT, outline=None)

    # --- Goat head (small, on short neck behind main head) ---
    if direction == DOWN:
        # Small goat head behind and above
        gx = cx + 6
        gy = head_cy + 2
        # Neck
        draw.line([(gx, body_cy - 6), (gx, gy + 4)],
                  fill=CHIMERA_GOAT, width=3)
        ellipse(draw, gx, gy, 6, 5, CHIMERA_GOAT)
        ellipse(draw, gx, gy + 1, 4, 3, CHIMERA_GOAT_DARK, outline=None)
        # Goat eyes
        draw.point((gx - 3, gy), fill=BLACK)
        draw.point((gx + 3, gy), fill=BLACK)
        # Goat horns (small, curved)
        draw.polygon([(gx - 3, gy - 3), (gx - 5, gy - 9),
                      (gx - 1, gy - 4)], fill=CHIMERA_GOAT_HORN, outline=OUTLINE)
        draw.polygon([(gx + 1, gy - 4), (gx + 5, gy - 9),
                      (gx + 3, gy - 3)], fill=CHIMERA_GOAT_HORN, outline=OUTLINE)
    elif direction == UP:
        gx = cx - 6
        gy = head_cy + 2
        draw.line([(gx, body_cy - 6), (gx, gy + 4)],
                  fill=CHIMERA_GOAT, width=3)
        ellipse(draw, gx, gy, 6, 5, CHIMERA_GOAT)
        ellipse(draw, gx, gy, 4, 3, CHIMERA_GOAT_DARK, outline=None)
        draw.polygon([(gx - 3, gy - 3), (gx - 5, gy - 9),
                      (gx - 1, gy - 4)], fill=CHIMERA_GOAT_HORN, outline=OUTLINE)
        draw.polygon([(gx + 1, gy - 4), (gx + 5, gy - 9),
                      (gx + 3, gy - 3)], fill=CHIMERA_GOAT_HORN, outline=OUTLINE)
    elif direction == LEFT:
        # Goat head visible behind (on right side)
        gx = cx + 10
        gy = head_cy + 4
        draw.line([(gx - 2, body_cy - 4), (gx, gy + 4)],
                  fill=CHIMERA_GOAT, width=3)
        ellipse(draw, gx, gy, 5, 5, CHIMERA_GOAT)
        draw.point((gx + 2, gy), fill=BLACK)
        draw.polygon([(gx, gy - 3), (gx + 2, gy - 9),
                      (gx + 3, gy - 3)], fill=CHIMERA_GOAT_HORN, outline=OUTLINE)
    else:  # RIGHT
        gx = cx - 10
        gy = head_cy + 4
        draw.line([(gx + 2, body_cy - 4), (gx, gy + 4)],
                  fill=CHIMERA_GOAT, width=3)
        ellipse(draw, gx, gy, 5, 5, CHIMERA_GOAT)
        draw.point((gx - 2, gy), fill=BLACK)
        draw.polygon([(gx - 3, gy - 3), (gx - 2, gy - 9),
                      (gx, gy - 3)], fill=CHIMERA_GOAT_HORN, outline=OUTLINE)

    # --- Main lion head ---
    if direction == DOWN:
        # Mane (drawn behind head)
        ellipse(draw, cx, head_cy, 14, 13, CHIMERA_MANE)
        draw_fur_texture(draw, cx, head_cy, 20, 18, CHIMERA_LION_DARK, density=4)
        # Head
        ellipse(draw, cx, head_cy, 10, 9, CHIMERA_LION)
        ellipse(draw, cx - 2, head_cy - 2, 7, 5, CHIMERA_LION_LIGHT, outline=None)
        # Eyes
        draw.rectangle([cx - 5, head_cy - 1, cx - 2, head_cy + 2], fill=CHIMERA_EYE)
        draw.point((cx - 4, head_cy), fill=BLACK)
        draw.rectangle([cx + 2, head_cy - 1, cx + 5, head_cy + 2], fill=CHIMERA_EYE)
        draw.point((cx + 3, head_cy), fill=BLACK)
        # Nose and mouth
        draw.point((cx, head_cy + 4), fill=CHIMERA_LION_DARK)
        draw.line([(cx - 2, head_cy + 5), (cx + 2, head_cy + 5)],
                  fill=CHIMERA_LION_DARK, width=1)
        # Ears
        draw.polygon([(cx - 8, head_cy - 6), (cx - 6, head_cy - 12),
                      (cx - 4, head_cy - 6)], fill=CHIMERA_LION, outline=OUTLINE)
        draw.polygon([(cx + 4, head_cy - 6), (cx + 6, head_cy - 12),
                      (cx + 8, head_cy - 6)], fill=CHIMERA_LION, outline=OUTLINE)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 14, 13, CHIMERA_MANE)
        draw_fur_texture(draw, cx, head_cy, 20, 18, CHIMERA_LION_DARK, density=4)
        ellipse(draw, cx, head_cy, 10, 9, CHIMERA_LION)
        ellipse(draw, cx, head_cy, 7, 6, CHIMERA_LION_DARK, outline=None)
        draw.polygon([(cx - 8, head_cy - 6), (cx - 6, head_cy - 12),
                      (cx - 4, head_cy - 6)], fill=CHIMERA_LION, outline=OUTLINE)
        draw.polygon([(cx + 4, head_cy - 6), (cx + 6, head_cy - 12),
                      (cx + 8, head_cy - 6)], fill=CHIMERA_LION, outline=OUTLINE)
    elif direction == LEFT:
        ellipse(draw, cx - 4, head_cy, 13, 13, CHIMERA_MANE)
        draw_fur_texture(draw, cx - 4, head_cy, 18, 18, CHIMERA_LION_DARK, density=4)
        ellipse(draw, cx - 4, head_cy, 9, 9, CHIMERA_LION)
        ellipse(draw, cx - 6, head_cy - 2, 6, 5, CHIMERA_LION_LIGHT, outline=None)
        # Eye
        draw.rectangle([cx - 9, head_cy - 1, cx - 5, head_cy + 2], fill=CHIMERA_EYE)
        draw.point((cx - 7, head_cy), fill=BLACK)
        # Nose/mouth
        draw.point((cx - 10, head_cy + 3), fill=CHIMERA_LION_DARK)
        draw.line([(cx - 12, head_cy + 4), (cx - 8, head_cy + 4)],
                  fill=CHIMERA_LION_DARK, width=1)
        # Ear
        draw.polygon([(cx - 6, head_cy - 7), (cx - 4, head_cy - 14),
                      (cx - 1, head_cy - 7)], fill=CHIMERA_LION, outline=OUTLINE)
    else:  # RIGHT
        ellipse(draw, cx + 4, head_cy, 13, 13, CHIMERA_MANE)
        draw_fur_texture(draw, cx + 4, head_cy, 18, 18, CHIMERA_LION_DARK, density=4)
        ellipse(draw, cx + 4, head_cy, 9, 9, CHIMERA_LION)
        ellipse(draw, cx + 6, head_cy - 2, 6, 5, CHIMERA_LION_LIGHT, outline=None)
        # Eye
        draw.rectangle([cx + 5, head_cy - 1, cx + 9, head_cy + 2], fill=CHIMERA_EYE)
        draw.point((cx + 7, head_cy), fill=BLACK)
        # Nose/mouth
        draw.point((cx + 10, head_cy + 3), fill=CHIMERA_LION_DARK)
        draw.line([(cx + 8, head_cy + 4), (cx + 12, head_cy + 4)],
                  fill=CHIMERA_LION_DARK, width=1)
        # Ear
        draw.polygon([(cx + 1, head_cy - 7), (cx + 4, head_cy - 14),
                      (cx + 6, head_cy - 7)], fill=CHIMERA_LION, outline=OUTLINE)


# ===================================================================
# Registry and main
# ===================================================================

MYTHOLOGICAL_DRAW_FUNCTIONS = {
    'minotaur': draw_minotaur,
    'medusa': draw_medusa,
    'cerberus': draw_cerberus,
    'centaur': draw_centaur,
    'kraken': draw_kraken,
    'sphinx': draw_sphinx,
    'cyclops': draw_cyclops,
    'harpy': draw_harpy,
    'griffin': draw_griffin,
    'anubis': draw_anubis,
    'yokai': draw_yokai,
    'golem': draw_golem,
    'djinn': draw_djinn,
    'fenrir': draw_fenrir,
    'chimera': draw_chimera,
}


def main():
    for name, draw_func in MYTHOLOGICAL_DRAW_FUNCTIONS.items():
        generate_character(name, draw_func=draw_func)
    print(f"\nGenerated {len(MYTHOLOGICAL_DRAW_FUNCTIONS)} mythological character sprites.")


if __name__ == "__main__":
    main()
